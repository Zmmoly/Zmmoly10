package com.example.aiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * خدمة الكشف عن الكلمات المفتاحية
 * تعمل في الخلفية للاستماع إلى الكلمات المفتاحية مثل "زمولي" أو "مساعد" لتفعيل التطبيق
 */
class HotwordDetectionService : Service() {

    companion object {
        private const val TAG = "HotwordDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "hotword_detection_channel"
        private const val CHANNEL_NAME = "Hotword Detection Service"
        private const val HOTWORD_MODEL_FILENAME = "hotword_model.tflite"
        private const val AUDIO_SAMPLE_RATE_HZ = 16000
        private const val RECORDING_BUFFER_SIZE = 2048
        private const val DETECTION_THRESHOLD = 0.7f
        private const val SENSITIVITY_PREF_KEY = "hotword_sensitivity"
        private const val DEFAULT_SENSITIVITY = 0.7f
        
        // الكلمات المفتاحية المدعومة
        val SUPPORTED_HOTWORDS = arrayOf(
            "زمولي", "مساعد", "مساعدي", "أوكي زمولي", "هيه زمولي", "يا زمولي"
        )
    }
    
    // واجهة للاستماع إلى الكلمات المفتاحية
    interface HotwordListener {
        fun onHotwordDetected(hotword: String)
    }
    
    // تخزين المستمعين للكلمات المفتاحية
    private var hotwordListeners = mutableListOf<HotwordListener>()
    
    // إضافة مستمع للكلمات المفتاحية
    fun addHotwordListener(listener: HotwordListener) {
        if (!hotwordListeners.contains(listener)) {
            hotwordListeners.add(listener)
        }
    }
    
    // إزالة مستمع للكلمات المفتاحية
    fun removeHotwordListener(listener: HotwordListener) {
        hotwordListeners.remove(listener)
    }
    
    // الإشعار بالكشف عن كلمة مفتاحية
    private fun notifyHotwordDetected(hotword: String) {
        hotwordListeners.forEach { it.onHotwordDetected(hotword) }
    }

    // متغيرات لتسجيل الصوت والكشف عن الكلمات المفتاحية
    private var audioRecord: AudioRecord? = null
    private var isRecording = AtomicBoolean(false)
    private var interpreter: Interpreter? = null
    private var modelBuffer: ByteBuffer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var detectionJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferences: SharedPreferences
    private var sensitivity: Float = DEFAULT_SENSITIVITY

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "إنشاء خدمة الكشف عن الكلمات المفتاحية")
        preferences = getSharedPreferences("hotword_prefs", Context.MODE_PRIVATE)
        sensitivity = preferences.getFloat(SENSITIVITY_PREF_KEY, DEFAULT_SENSITIVITY)
        
        // تحميل نموذج الكشف عن الكلمات المفتاحية
        loadHotwordModel()
        
        // الحصول على قفل استيقاظ للحفاظ على الخدمة نشطة
        acquireWakeLock()
    }

    /**
     * تحميل نموذج الكشف عن الكلمات المفتاحية
     */
    private fun loadHotwordModel() {
        try {
            // تحقق من وجود ملف النموذج في تخزين التطبيق
            val modelFile = File(filesDir, "ml/$HOTWORD_MODEL_FILENAME")
            
            if (modelFile.exists()) {
                // تحميل النموذج من التخزين المحلي
                modelBuffer = FileUtil.loadMappedFile(modelFile.absolutePath)
                interpreter = Interpreter(modelBuffer!!)
                Log.d(TAG, "تم تحميل نموذج الكلمات المفتاحية من التخزين المحلي")
            } else {
                // تحميل النموذج من موارد التطبيق
                try {
                    modelBuffer = FileUtil.loadMappedFile(this, HOTWORD_MODEL_FILENAME)
                    interpreter = Interpreter(modelBuffer!!)
                    Log.d(TAG, "تم تحميل نموذج الكلمات المفتاحية من موارد التطبيق")
                } catch (e: Exception) {
                    Log.e(TAG, "فشل تحميل نموذج الكلمات المفتاحية من موارد التطبيق: ${e.message}")
                    
                    // محاولة استخدام النموذج الافتراضي من موارد التطبيق
                    try {
                        modelBuffer = FileUtil.loadMappedFile(this, "default_hotword_model.tflite")
                        interpreter = Interpreter(modelBuffer!!)
                        Log.d(TAG, "تم تحميل نموذج الكلمات المفتاحية الافتراضي")
                    } catch (e: Exception) {
                        Log.e(TAG, "فشل تحميل نموذج الكلمات المفتاحية الافتراضي: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تحميل نموذج الكلمات المفتاحية", e)
        }
    }

    /**
     * الحصول على قفل استيقاظ للحفاظ على الخدمة نشطة
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HotwordDetectionService:WakeLock"
            )
            wakeLock?.acquire(10*60*1000L) // 10 دقائق كحد أقصى
        } catch (e: Exception) {
            Log.e(TAG, "فشل الحصول على قفل الاستيقاظ", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "بدء خدمة الكشف عن الكلمات المفتاحية")
        
        // بدء الخدمة كخدمة أمامية لمنع إيقافها من قبل النظام
        startForeground(NOTIFICATION_ID, createNotification())
        
        // بدء الاستماع إلى الكلمات المفتاحية
        startHotwordDetection()
        
        // إعادة تشغيل الخدمة إذا تم إيقافها
        return START_STICKY
    }

    /**
     * إنشاء إشعار لتشغيل الخدمة في المقدمة
     */
    private fun createNotification(): Notification {
        // إنشاء قناة إشعارات للأجهزة التي تعمل بنظام Android Oreo وما فوق
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة الكشف عن الكلمات المفتاحية"
                setShowBadge(false)
                lightColor = Color.BLUE
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        
        // إنشاء قصد لفتح التطبيق عند النقر على الإشعار
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            PendingIntent.getActivity(this, 0, notificationIntent, flags)
        }
        
        // بناء الإشعار
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("زمولي نشط")
            .setContentText("يستمع للكلمات المفتاحية...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * بدء الكشف عن الكلمات المفتاحية
     */
    private fun startHotwordDetection() {
        // التحقق من وجود مترجم لنموذج الكلمات المفتاحية
        if (interpreter == null) {
            Log.e(TAG, "لا يمكن بدء الكشف عن الكلمات المفتاحية: نموذج الكلمات المفتاحية غير محمل")
            return
        }
        
        // التحقق من أن عملية الكشف عن الكلمات المفتاحية ليست قيد التشغيل بالفعل
        if (isRecording.get()) {
            Log.d(TAG, "الكشف عن الكلمات المفتاحية قيد التشغيل بالفعل")
            return
        }
        
        // تهيئة عملية التسجيل الصوتي
        initializeAudioRecord()
        
        // بدء عملية الكشف عن الكلمات المفتاحية في خلفية تشغيل
        detectionJob = serviceScope.launch {
            isRecording.set(true)
            
            try {
                while (isRecording.get()) {
                    val detected = detectHotword()
                    if (detected != null) {
                        // إبلاغ المستمعين بالكشف عن كلمة مفتاحية
                        Log.d(TAG, "تم الكشف عن الكلمة المفتاحية: $detected")
                        
                        // إبلاغ المستمعين في الخيط الرئيسي
                        withContext(Dispatchers.Main) {
                            notifyHotwordDetected(detected)
                        }
                        
                        // إيقاف الكشف مؤقتاً لتجنب الكشف المتكرر
                        delay(2000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في عملية الكشف عن الكلمات المفتاحية", e)
            } finally {
                // تحرير موارد التسجيل
                releaseAudioRecord()
            }
        }
    }

    /**
     * تهيئة التسجيل الصوتي
     */
    private fun initializeAudioRecord() {
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            // تهيئة مسجل الصوت
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AUDIO_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )
            
            // بدء التسجيل
            audioRecord?.startRecording()
            Log.d(TAG, "تم بدء التسجيل الصوتي")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تهيئة التسجيل الصوتي", e)
        }
    }

    /**
     * تحرير موارد التسجيل الصوتي
     */
    private fun releaseAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "تم تحرير موارد التسجيل الصوتي")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تحرير موارد التسجيل الصوتي", e)
        }
    }

    /**
     * الكشف عن الكلمات المفتاحية في بيانات الصوت
     * @return الكلمة المفتاحية المكتشفة أو null إذا لم يتم الكشف عن أي كلمة مفتاحية
     */
    private suspend fun detectHotword(): String? = withContext(Dispatchers.IO) {
        try {
            // التحقق من وجود مسجل الصوت
            val record = audioRecord ?: return@withContext null
            
            // قراءة بيانات الصوت
            val audioBuffer = ShortArray(RECORDING_BUFFER_SIZE)
            val readResult = record.read(audioBuffer, 0, RECORDING_BUFFER_SIZE)
            
            if (readResult <= 0) {
                return@withContext null
            }
            
            // تحويل بيانات الصوت إلى تنسيق مناسب للنموذج
            val inputBuffer = convertAudioToModelInput(audioBuffer)
            
            // إجراء التنبؤ باستخدام النموذج
            val outputBuffer = Array(1) { FloatArray(SUPPORTED_HOTWORDS.size) }
            interpreter?.run(inputBuffer, outputBuffer)
            
            // تحديد الكلمة المفتاحية ذات أعلى احتمال
            val probabilities = outputBuffer[0]
            var maxProbIndex = -1
            var maxProb = 0.0f
            
            for (i in probabilities.indices) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxProbIndex = i
                }
            }
            
            // إذا كان الاحتمال أعلى من العتبة، فقد تم الكشف عن كلمة مفتاحية
            if (maxProbIndex >= 0 && maxProb > sensitivity) {
                return@withContext SUPPORTED_HOTWORDS[maxProbIndex]
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في الكشف عن الكلمات المفتاحية", e)
            return@withContext null
        }
    }

    /**
     * تحويل بيانات الصوت إلى تنسيق مناسب للنموذج
     */
    private fun convertAudioToModelInput(audioBuffer: ShortArray): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(audioBuffer.size * 4) // 4 بايت لكل قيمة float
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // تطبيع قيم الصوت إلى نطاق [-1, 1]
        for (i in audioBuffer.indices) {
            val normalizedValue = audioBuffer[i] / 32767.0f // 32767 هي القيمة القصوى للـ Short
            inputBuffer.putFloat(normalizedValue)
        }
        
        inputBuffer.rewind() // إعادة تعيين المؤشر إلى بداية المخزن المؤقت
        return inputBuffer
    }

    /**
     * إيقاف الكشف عن الكلمات المفتاحية
     */
    private fun stopHotwordDetection() {
        isRecording.set(false)
        detectionJob?.cancel()
        detectionJob = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        // هذه الخدمة لا تدعم الارتباط
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "تدمير خدمة الكشف عن الكلمات المفتاحية")
        
        // إيقاف الكشف عن الكلمات المفتاحية
        stopHotwordDetection()
        
        // تحرير قفل الاستيقاظ
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        // تحرير موارد نموذج الكلمات المفتاحية
        interpreter?.close()
        interpreter = null
        modelBuffer = null
    }

    /**
     * تعيين حساسية الكشف عن الكلمات المفتاحية
     * @param value قيمة الحساسية (0.0 إلى 1.0)
     */
    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.0f, 1.0f)
        preferences.edit().putFloat(SENSITIVITY_PREF_KEY, sensitivity).apply()
        Log.d(TAG, "تم تعيين حساسية الكشف على: $sensitivity")
    }
    
    // تأخير لمدة محددة بالمللي ثانية
    private suspend fun delay(timeMillis: Long) {
        kotlinx.coroutines.delay(timeMillis)
    }
}