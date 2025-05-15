package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URL
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * مدير النماذج: فئة مسؤولة عن إدارة نماذج التعلم الآلي في التطبيق
 * 
 * الوظائف الرئيسية:
 * - تحميل وتفريغ نماذج TensorFlow Lite
 * - التحقق من وجود النماذج وتنزيلها عند الحاجة
 * - إدارة دورة حياة النماذج وذاكرتها
 * - التعامل مع الوصول المتزامن
 * 
 * ملاحظات:
 * - الحد الأقصى الافتراضي للنماذج المحملة هو 5 نماذج
 * - يمكن تعديل هذا الحد باستخدام setMaxLoadedModels()
 */
class ModelManager(private val context: Context) {
    companion object {
        private const val TAG = "ModelManager"
        private const val DEFAULT_MAX_MODELS = 5
        private const val CONFIG_FILENAME = "assets-config.yml"
        private const val PREFERENCES_NAME = "model_manager_prefs"
        private const val PREF_MAX_MODELS = "max_loaded_models"
    }

    // خرائط لتخزين النماذج والمترجمين
    private val loadedModels = ConcurrentHashMap<String, Interpreter>()
    private val modelUsageCounter = ConcurrentHashMap<String, Int>()
    private val modelLastUsed = ConcurrentHashMap<String, Long>()
    
    // قائمة بالنماذج المتاحة ومساراتها
    private val availableModels = ConcurrentHashMap<String, String>()
    
    // ملف التكوين
    private var configMap: Map<String, Any>? = null
    
    // مجمع الخيوط للمهام الخلفية
    private val executor = Executors.newCachedThreadPool()
    
    // تفضيلات التخزين
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )
    
    // الحد الأقصى لعدد النماذج المحملة في الذاكرة
    var maxLoadedModels: Int = preferences.getInt(PREF_MAX_MODELS, DEFAULT_MAX_MODELS)
        set(value) {
            field = value
            preferences.edit().putInt(PREF_MAX_MODELS, value).apply()
            // إعادة تقييم النماذج المحملة عند تغيير الحد الأقصى
            enforceModelLimit()
        }

    init {
        Log.d(TAG, "تهيئة ModelManager")
        // قراءة ملف التكوين وتحديد النماذج المتاحة
        loadConfiguration()
        // فحص النماذج المتاحة وتحديثها
        scanAvailableModels()
    }

    /**
     * قراءة ملف تكوين النماذج
     */
    private fun loadConfiguration() {
        try {
            val configFile = File(context.filesDir, CONFIG_FILENAME)
            
            if (configFile.exists()) {
                FileInputStream(configFile).use { inputStream ->
                    val yaml = Yaml()
                    val reader = InputStreamReader(inputStream)
                    configMap = yaml.load(reader) as? Map<String, Any>
                    
                    Log.d(TAG, "تم تحميل ملف التكوين: ${configMap?.size ?: 0} عناصر")
                }
            } else {
                // نسخ ملف التكوين من الموارد إلى مجلد التطبيق إذا لم يكن موجوداً
                context.assets.open(CONFIG_FILENAME).use { input ->
                    FileOutputStream(configFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // إعادة تحميل الملف بعد نسخه
                loadConfiguration()
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تحميل ملف التكوين", e)
        }
    }

    /**
     * فحص النماذج المتاحة في مجلدات التطبيق
     */
    private fun scanAvailableModels() {
        try {
            // بحث في مجلد ml
            val mlDir = File(context.filesDir, "ml")
            if (mlDir.exists() && mlDir.isDirectory) {
                mlDir.listFiles { file -> file.extension == "tflite" }?.forEach { file ->
                    availableModels[file.nameWithoutExtension] = file.absolutePath
                    Log.d(TAG, "تم العثور على نموذج: ${file.name}")
                }
            }

            // بحث في مجلد الموارد
            val assetsDir = File(context.filesDir, "assets")
            if (assetsDir.exists() && assetsDir.isDirectory) {
                assetsDir.listFiles { file -> file.extension == "tflite" }?.forEach { file ->
                    availableModels[file.nameWithoutExtension] = file.absolutePath
                    Log.d(TAG, "تم العثور على نموذج: ${file.name}")
                }
            }
            
            Log.i(TAG, "تم العثور على ${availableModels.size} نموذج")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في فحص النماذج المتاحة", e)
        }
    }

    /**
     * الحصول على مترجم لنموذج معين
     * سيتم تحميل النموذج تلقائياً إذا لم يكن محملاً بالفعل
     */
    @Synchronized
    fun getInterpreter(modelName: String): Interpreter? {
        try {
            // تحديث عداد الاستخدام وآخر وقت استخدام
            modelUsageCounter[modelName] = (modelUsageCounter[modelName] ?: 0) + 1
            modelLastUsed[modelName] = System.currentTimeMillis()
            
            // إذا كان النموذج محملاً بالفعل، أعد المترجم
            if (loadedModels.containsKey(modelName)) {
                Log.d(TAG, "استخدام نموذج محمل مسبقاً: $modelName")
                return loadedModels[modelName]
            }
            
            // تحقق من وجود النموذج محلياً
            if (!isModelAvailable(modelName)) {
                // حاول تنزيل النموذج
                Log.w(TAG, "النموذج $modelName غير متوفر، جاري محاولة تنزيله...")
                if (!downloadModel(modelName)) {
                    Log.e(TAG, "فشل تنزيل النموذج: $modelName")
                    return null
                }
            }
            
            // تنفيذ حد النماذج قبل تحميل نموذج جديد
            enforceModelLimit()
            
            // تحميل النموذج
            val modelPath = availableModels[modelName] ?: throw IllegalStateException("مسار النموذج $modelName غير متوفر")
            val modelFile = File(modelPath)
            
            if (!modelFile.exists()) {
                throw IllegalStateException("ملف النموذج غير موجود: $modelPath")
            }
            
            // تحميل النموذج باستخدام مخزن مؤقت للبايت
            val mappedByteBuffer = loadModelFile(modelFile)
            val interpreter = Interpreter(mappedByteBuffer)
            
            // تخزين المترجم
            loadedModels[modelName] = interpreter
            Log.i(TAG, "تم تحميل النموذج: $modelName")
            
            return interpreter
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في الحصول على مترجم للنموذج $modelName", e)
            return null
        }
    }

    /**
     * التحقق من توفر نموذج محلياً
     */
    fun isModelAvailable(modelName: String): Boolean {
        // التحقق من وجود النموذج في قائمة النماذج المتاحة
        return availableModels.containsKey(modelName)
    }

    /**
     * الحصول على قائمة بالنماذج المحملة حالياً
     */
    fun getLoadedModels(): List<String> {
        return loadedModels.keys().toList()
    }

    /**
     * الحصول على قائمة بجميع النماذج المتاحة (المحملة وغير المحملة)
     */
    fun getAllAvailableModels(): List<String> {
        return availableModels.keys.toList()
    }

    /**
     * تحميل ملف النموذج إلى مخزن مؤقت للبايت
     */
    @Throws(Exception::class)
    private fun loadModelFile(modelFile: File): MappedByteBuffer {
        FileInputStream(modelFile).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = 0L
            val declaredLength = modelFile.length()
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    /**
     * تنزيل نموذج من URL المحدد في ملف التكوين
     */
    private fun downloadModel(modelName: String): Boolean {
        try {
            val config = configMap ?: return false
            
            // البحث عن رابط التنزيل في ملف التكوين
            val modelUrl = findModelUrlInConfig(modelName)
            if (modelUrl.isNullOrEmpty()) {
                Log.e(TAG, "لم يتم العثور على رابط تنزيل للنموذج: $modelName")
                return false
            }
            
            // تحديد مسار التنزيل
            val downloadPath = determineModelPath(modelName)
            
            // إنشاء المجلدات اللازمة
            val downloadFile = File(downloadPath)
            downloadFile.parentFile?.mkdirs()
            
            // تنزيل ملف النموذج
            Log.i(TAG, "جاري تنزيل النموذج $modelName من $modelUrl")
            URL(modelUrl).openStream().use { input ->
                FileOutputStream(downloadFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // التحقق من نجاح التنزيل
            if (downloadFile.exists() && downloadFile.length() > 0) {
                // إضافة النموذج إلى قائمة النماذج المتاحة
                availableModels[modelName] = downloadPath
                Log.i(TAG, "تم تنزيل النموذج بنجاح: $modelName")
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تنزيل النموذج $modelName", e)
            return false
        }
    }

    /**
     * البحث عن رابط التنزيل للنموذج في ملف التكوين
     */
    private fun findModelUrlInConfig(modelName: String): String? {
        val config = configMap ?: return null
        
        // البحث عن مسارات تحتوي على اسم النموذج
        for ((path, url) in config) {
            if (url is String && url.startsWith("http") && 
                path is String && path.contains("$modelName.tflite")) {
                return url
            }
        }
        
        return null
    }

    /**
     * تحديد مسار حفظ النموذج
     */
    private fun determineModelPath(modelName: String): String {
        // البحث عن المسار في ملف التكوين
        val config = configMap ?: return "${context.filesDir}/ml/$modelName.tflite"
        
        for ((path, url) in config) {
            if (url is String && url.startsWith("http") && 
                path is String && path.contains("$modelName.tflite")) {
                
                // استخدام المسار من ملف التكوين مع مجلد ملفات التطبيق
                val relativePath = path.replace("app/", "")
                return "${context.filesDir}/$relativePath"
            }
        }
        
        // المسار الافتراضي
        return "${context.filesDir}/ml/$modelName.tflite"
    }

    /**
     * تطبيق حد النماذج المحملة
     * سيتم تفريغ أقل النماذج استخداماً إذا تم تجاوز الحد الأقصى
     */
    private fun enforceModelLimit() {
        if (loadedModels.size <= maxLoadedModels) {
            return
        }
        
        Log.d(TAG, "تطبيق حد النماذج. المحملة حالياً: ${loadedModels.size}, الحد الأقصى: $maxLoadedModels")
        
        // حساب الأولوية لكل نموذج (استناداً إلى عدد الاستخدامات وآخر وقت استخدام)
        val modelScores = loadedModels.keys.associate { modelName ->
            val usageCount = modelUsageCounter[modelName] ?: 0
            val lastUsed = modelLastUsed[modelName] ?: 0L
            val currentTime = System.currentTimeMillis()
            val timeFactor = (currentTime - lastUsed) / 60000.0 // بالدقائق
            
            // صيغة الأولوية: عدد الاستخدامات / (الوقت منذ آخر استخدام + 1)
            val score = usageCount / (timeFactor + 1)
            modelName to score
        }
        
        // ترتيب النماذج حسب الأولوية (الأقل أولوية أولاً)
        val modelsToUnload = modelScores.entries
            .sortedBy { it.value }
            .take(loadedModels.size - maxLoadedModels)
            .map { it.key }
        
        // تفريغ النماذج منخفضة الأولوية
        for (modelName in modelsToUnload) {
            unloadModel(modelName)
        }
    }

    /**
     * تفريغ نموذج محدد من الذاكرة
     */
    fun unloadModel(modelName: String) {
        try {
            val interpreter = loadedModels.remove(modelName)
            interpreter?.close()
            Log.i(TAG, "تم تفريغ النموذج: $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تفريغ النموذج $modelName", e)
        }
    }

    /**
     * تفريغ جميع النماذج من الذاكرة
     */
    fun unloadAllModels() {
        for (modelName in loadedModels.keys.toList()) {
            unloadModel(modelName)
        }
        Log.i(TAG, "تم تفريغ جميع النماذج")
    }
    
    /**
     * تحميل مسبق لمجموعة من النماذج
     */
    fun preloadModels(modelNames: List<String>) {
        executor.execute {
            for (modelName in modelNames) {
                if (!isModelAvailable(modelName)) {
                    downloadModel(modelName)
                }
            }
        }
    }
    
    /**
     * تحميل نموذج بشكل متزامن (معلق)
     */
    suspend fun loadModelAsync(modelName: String): Interpreter = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val interpreter = getInterpreter(modelName)
                if (interpreter != null) {
                    continuation.resume(interpreter)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException("فشل تحميل النموذج: $modelName")
                    )
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * تعيين الحد الأقصى للنماذج المحملة
     */
    fun setMaxLoadedModels(maxModels: Int) {
        maxLoadedModels = maxModels.coerceAtLeast(1)
    }
    
    /**
     * إعادة فحص النماذج المتاحة
     */
    fun refreshAvailableModels() {
        scanAvailableModels()
    }
    
    /**
     * التنظيف عند تدمير الكائن
     */
    fun cleanup() {
        unloadAllModels()
        executor.shutdown()
        try {
            if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
}