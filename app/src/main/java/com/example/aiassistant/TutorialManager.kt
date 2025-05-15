package com.example.aiassistant

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color
import android.view.ViewGroup
import android.view.LayoutInflater
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.core.content.ContextCompat

/**
 * مدير العرض التوضيحي التفاعلي
 * مسؤول عن عرض خطوات الاستخدام للمستخدمين الجدد وتوجيههم خلال استخدام التطبيق
 */
class TutorialManager(private val activity: Activity) {
    companion object {
        private const val PREFS_NAME = "tutorial_prefs"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        private const val KEY_CURRENT_STEP = "tutorial_current_step"
    }

    // المكونات الرئيسية للعرض التوضيحي
    private var overlayView: View? = null
    private var highlightView: View? = null
    private var tooltipView: View? = null
    
    // المعلومات المتعلقة بالحالة الحالية
    private var currentStep = 0
    private val tutorialSteps = ArrayList<TutorialStep>()
    private val preferences: SharedPreferences

    // حالة العرض التوضيحي
    private var isTutorialActive = false
    private var isDismissedByUser = false

    init {
        preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // استرجاع آخر خطوة تم الوصول إليها
        currentStep = preferences.getInt(KEY_CURRENT_STEP, 0)
    }

    /**
     * إعداد خطوات العرض التوضيحي
     */
    fun setupTutorialSteps() {
        tutorialSteps.clear()

        // 1. مقدمة للتطبيق
        tutorialSteps.add(
            TutorialStep(
                targetViewId = R.id.welcomeTextView,
                title = "مرحباً بك في تطبيق زمولي",
                message = "المساعد الذكي لتحليل اللهجات العربية والنصوص الطبية. سنوضح لك كيفية استخدام التطبيق خلال هذا العرض التوضيحي.",
                highlightType = HighlightType.RECTANGLE
            )
        )

        // 2. شرح زر الاستماع
        tutorialSteps.add(
            TutorialStep(
                targetViewId = R.id.listenButton,
                title = "زر الاستماع",
                message = "اضغط هنا لبدء الاستماع إلى صوتك. يمكن لزمولي التعرف على اللهجات العربية المختلفة وتحليل نبرة صوتك.",
                highlightType = HighlightType.CIRCLE
            )
        )

        // 3. شرح مساحة الرسائل
        tutorialSteps.add(
            TutorialStep(
                targetViewId = R.id.responseTextView,
                title = "مساحة المحادثة",
                message = "هنا ستظهر الردود على استفساراتك ونتائج التحليلات. يمكنك طلب تحليل اللهجة، تحليل نبرة الصوت، أو حتى تحليل صور التحاليل الطبية.",
                highlightType = HighlightType.RECTANGLE
            )
        )

        // 4. ميزات متقدمة
        tutorialSteps.add(
            TutorialStep(
                targetViewId = android.R.id.content,
                title = "ميزات متقدمة",
                message = "يمكن لزمولي التحكم في هاتفك (مثل إجراء المكالمات وإرسال الرسائل)، وتحليل التحاليل الطبية، والكشف عن اللهجات العربية المختلفة. جرب قول \"حلل نبرة صوتي\" أو \"ما هي لهجتي\".",
                highlightType = HighlightType.NONE
            )
        )
    }

    /**
     * التحقق مما إذا كان المستخدم قد أكمل العرض التوضيحي سابقاً
     * @return true إذا تم إكمال العرض التوضيحي سابقاً
     */
    fun isTutorialCompleted(): Boolean {
        return preferences.getBoolean(KEY_TUTORIAL_COMPLETED, false)
    }

    /**
     * تعيين حالة إكمال العرض التوضيحي
     * @param completed true لتعيين العرض التوضيحي كمكتمل
     */
    fun setTutorialCompleted(completed: Boolean) {
        preferences.edit().putBoolean(KEY_TUTORIAL_COMPLETED, completed).apply()
    }

    /**
     * بدء العرض التوضيحي من البداية
     */
    fun startTutorial() {
        if (tutorialSteps.isEmpty()) {
            setupTutorialSteps()
        }
        
        currentStep = 0
        preferences.edit().putInt(KEY_CURRENT_STEP, currentStep).apply()
        isDismissedByUser = false
        showCurrentStep()
    }

    /**
     * استئناف العرض التوضيحي من آخر خطوة
     */
    fun resumeTutorial() {
        if (tutorialSteps.isEmpty()) {
            setupTutorialSteps()
        }
        
        if (currentStep >= tutorialSteps.size) {
            currentStep = 0
        }
        
        isDismissedByUser = false
        showCurrentStep()
    }

    /**
     * عرض الخطوة الحالية من العرض التوضيحي
     */
    private fun showCurrentStep() {
        if (isDismissedByUser || currentStep >= tutorialSteps.size) {
            hideAllViews()
            isTutorialActive = false
            return
        }

        isTutorialActive = true
        val step = tutorialSteps[currentStep]
        
        // إيجاد العنصر المستهدف حسب المعرف
        val targetView = activity.findViewById<View>(step.targetViewId)
        
        if (targetView == null) {
            // تخطي هذه الخطوة إذا لم يتم العثور على العنصر المستهدف
            moveToNextStep()
            return
        }

        // عرض الطبقة الشفافة فوق الشاشة
        showOverlayView()
        
        // إبراز العنصر المستهدف
        highlightTargetView(targetView, step.highlightType)
        
        // عرض نافذة الشرح
        showTooltip(targetView, step)
    }

    /**
     * إنشاء وعرض طبقة شفافة لتغطية الشاشة
     */
    private fun showOverlayView() {
        if (overlayView == null) {
            overlayView = View(activity)
            overlayView?.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            overlayView?.setBackgroundColor(Color.parseColor("#99000000")) // أسود شفاف
            
            // إضافة مستمع للنقر على الطبقة الشفافة
            overlayView?.setOnClickListener {
                moveToNextStep()
            }
            
            val decorView = activity.window.decorView as ViewGroup
            decorView.addView(overlayView)
            
            // تأثير ظهور الطبقة الشفافة
            val fadeIn = AlphaAnimation(0f, 1f)
            fadeIn.duration = 300
            overlayView?.startAnimation(fadeIn)
        }
    }

    /**
     * إبراز العنصر المستهدف عن طريق إنشاء فتحة في الطبقة الشفافة
     */
    private fun highlightTargetView(targetView: View, highlightType: HighlightType) {
        // إزالة أي تأثير إبراز سابق
        val decorView = activity.window.decorView as ViewGroup
        if (highlightView != null) {
            decorView.removeView(highlightView)
            highlightView = null
        }

        // عند اختيار عدم إبراز أي عنصر
        if (highlightType == HighlightType.NONE) {
            return
        }

        // الحصول على إحداثيات وأبعاد العنصر المستهدف
        val location = IntArray(2)
        targetView.getLocationInWindow(location)
        
        val rect = Rect(
            location[0],
            location[1],
            location[0] + targetView.width,
            location[1] + targetView.height
        )
        
        // إضافة هامش للإبراز
        val padding = activity.resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin) / 2
        rect.inset(-padding, -padding)
        
        // إنشاء عنصر مخصص للإبراز
        val customView = HighlightView(activity, rect, highlightType)
        highlightView = customView
        
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        decorView.addView(highlightView, params)
    }

    /**
     * عرض نافذة الشرح بجانب العنصر المستهدف
     */
    private fun showTooltip(targetView: View, step: TutorialStep) {
        // إزالة أي نافذة شرح سابقة
        val decorView = activity.window.decorView as ViewGroup
        if (tooltipView != null) {
            decorView.removeView(tooltipView)
            tooltipView = null
        }
        
        // إنشاء نافذة الشرح
        val tooltipLayout = LayoutInflater.from(activity).inflate(R.layout.tutorial_tooltip, null)
        
        // تعيين العنوان والرسالة
        val titleTextView = tooltipLayout.findViewById<TextView>(R.id.tooltipTitle)
        val messageTextView = tooltipLayout.findViewById<TextView>(R.id.tooltipMessage)
        val nextButton = tooltipLayout.findViewById<Button>(R.id.tooltipNextButton)
        val skipButton = tooltipLayout.findViewById<Button>(R.id.tooltipSkipButton)
        
        titleTextView.text = step.title
        messageTextView.text = step.message
        
        // تحديث أزرار التنقل
        if (currentStep == tutorialSteps.size - 1) {
            nextButton.text = "إنهاء"
        } else {
            nextButton.text = "التالي"
        }
        
        nextButton.setOnClickListener {
            moveToNextStep()
        }
        
        skipButton.setOnClickListener {
            dismissTutorial()
        }
        
        // إضافة نافذة الشرح للواجهة
        tooltipView = tooltipLayout
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        decorView.addView(tooltipView, params)
        
        // تحديد موضع نافذة الشرح
        positionTooltip(targetView)
    }

    /**
     * تحديد موضع نافذة الشرح بالنسبة للعنصر المستهدف
     */
    private fun positionTooltip(targetView: View) {
        tooltipView?.let { tooltip ->
            // الحصول على إحداثيات وأبعاد العنصر المستهدف
            val location = IntArray(2)
            targetView.getLocationInWindow(location)
            
            val targetRect = Rect(
                location[0],
                location[1],
                location[0] + targetView.width,
                location[1] + targetView.height
            )
            
            // قياس نافذة الشرح
            tooltip.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            
            val tooltipWidth = tooltip.measuredWidth
            val tooltipHeight = tooltip.measuredHeight
            
            // تحديد ما إذا كانت نافذة الشرح ستظهر فوق أو تحت العنصر المستهدف
            val screenHeight = activity.resources.displayMetrics.heightPixels
            val spaceAbove = targetRect.top
            val spaceBelow = screenHeight - targetRect.bottom
            
            val params = tooltip.layoutParams as ViewGroup.LayoutParams
            
            // تعديل موضع نافذة الشرح
            if (spaceBelow > tooltipHeight || spaceBelow > spaceAbove) {
                // وضع نافذة الشرح تحت العنصر المستهدف
                tooltip.y = (targetRect.bottom + 20).toFloat()
            } else {
                // وضع نافذة الشرح فوق العنصر المستهدف
                tooltip.y = (targetRect.top - tooltipHeight - 20).toFloat()
            }
            
            // تحديد التوسيط الأفقي
            tooltip.x = ((activity.window.decorView.width - tooltipWidth) / 2).toFloat()
            
            // تطبيق تأثير الظهور
            val fadeIn = AlphaAnimation(0f, 1f)
            fadeIn.duration = 300
            tooltip.startAnimation(fadeIn)
        }
    }

    /**
     * الانتقال إلى الخطوة التالية
     */
    private fun moveToNextStep() {
        currentStep++
        
        // حفظ الخطوة الحالية
        preferences.edit().putInt(KEY_CURRENT_STEP, currentStep).apply()
        
        if (currentStep < tutorialSteps.size) {
            // عرض الخطوة التالية
            showCurrentStep()
        } else {
            // إنهاء العرض التوضيحي
            completeAndDismissTutorial()
        }
    }

    /**
     * إنهاء العرض التوضيحي وإخفاء جميع العناصر
     */
    private fun completeAndDismissTutorial() {
        setTutorialCompleted(true)
        hideAllViews()
        isTutorialActive = false
    }

    /**
     * تخطي العرض التوضيحي بناءً على طلب المستخدم
     */
    private fun dismissTutorial() {
        isDismissedByUser = true
        setTutorialCompleted(true)
        hideAllViews()
        isTutorialActive = false
    }

    /**
     * إخفاء جميع العناصر المرئية للعرض التوضيحي
     */
    private fun hideAllViews() {
        val decorView = activity.window.decorView as ViewGroup
        
        // إزالة الطبقة الشفافة
        if (overlayView != null) {
            val fadeOut = AlphaAnimation(1f, 0f)
            fadeOut.duration = 300
            overlayView?.startAnimation(fadeOut)
            
            Handler(Looper.getMainLooper()).postDelayed({
                decorView.removeView(overlayView)
                overlayView = null
            }, 300)
        }
        
        // إزالة عنصر الإبراز
        if (highlightView != null) {
            decorView.removeView(highlightView)
            highlightView = null
        }
        
        // إزالة نافذة الشرح
        if (tooltipView != null) {
            val fadeOut = AlphaAnimation(1f, 0f)
            fadeOut.duration = 300
            tooltipView?.startAnimation(fadeOut)
            
            Handler(Looper.getMainLooper()).postDelayed({
                decorView.removeView(tooltipView)
                tooltipView = null
            }, 300)
        }
    }

    /**
     * التحقق مما إذا كان العرض التوضيحي نشطاً حالياً
     */
    fun isActive(): Boolean {
        return isTutorialActive
    }
}

/**
 * أنواع الإبراز المختلفة للعناصر
 */
enum class HighlightType {
    CIRCLE,     // دائرة
    RECTANGLE,  // مستطيل
    OVAL,       // بيضاوي
    NONE        // بدون إبراز
}

/**
 * خطوة في العرض التوضيحي
 */
data class TutorialStep(
    val targetViewId: Int,          // معرف العنصر المستهدف
    val title: String,              // عنوان الخطوة
    val message: String,            // شرح الخطوة
    val highlightType: HighlightType // نوع الإبراز
)

/**
 * عنصر مخصص لإبراز العناصر المستهدفة
 */
class HighlightView(
    context: Context,
    private val highlightRect: Rect,
    private val highlightType: HighlightType
) : View(context) {

    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // رسم طبقة شفافة ثم إزالة المنطقة المستهدفة
        canvas.drawColor(Color.TRANSPARENT)
        
        when (highlightType) {
            HighlightType.CIRCLE -> {
                val centerX = highlightRect.exactCenterX()
                val centerY = highlightRect.exactCenterY()
                val radius = Math.max(highlightRect.width(), highlightRect.height()) / 2f
                canvas.drawCircle(centerX, centerY, radius, paint)
            }
            HighlightType.RECTANGLE -> {
                val rectF = RectF(
                    highlightRect.left.toFloat(),
                    highlightRect.top.toFloat(),
                    highlightRect.right.toFloat(),
                    highlightRect.bottom.toFloat()
                )
                canvas.drawRoundRect(rectF, 20f, 20f, paint)
            }
            HighlightType.OVAL -> {
                val rectF = RectF(
                    highlightRect.left.toFloat(),
                    highlightRect.top.toFloat(),
                    highlightRect.right.toFloat(),
                    highlightRect.bottom.toFloat()
                )
                canvas.drawOval(rectF, paint)
            }
            HighlightType.NONE -> {
                // عدم رسم أي شيء
            }
        }
    }
}