# تنزيل نماذج الذكاء الاصطناعي لتطبيق زمولي

هذا الدليل يشرح كيفية تنزيل نماذج الذكاء الاصطناعي الكبيرة اللازمة لتشغيل تطبيق زمولي للذكاء الاصطناعي.

## لماذا نحتاج هذه الخطوات؟

تطبيق زمولي يستخدم عدة نماذج للذكاء الاصطناعي، بعضها كبير الحجم (مثل `logical_reasoning_model.tflite` بحجم 128 ميجابايت). هذه النماذج غير مضمنة في مستودع GitHub بسبب قيود الحجم، وبدلاً من ذلك تُخزن في GitHub Releases.

عند استنساخ أو استيراد المشروع في Android Studio، لن يتم تنزيل هذه النماذج تلقائياً، مما سيؤدي إلى أخطاء عند محاولة تشغيل التطبيق.

## الطريقة 1: استخدام السكريبت التلقائي

قمنا بإنشاء سكريبت Python لتنزيل جميع النماذج ووضعها في المجلدات المناسبة:

1. تأكد من وجود Python على جهازك (الإصدار 3.6 أو أحدث)

2. قم بتثبيت المكتبات المطلوبة:
   ```
   pip install pyyaml requests tqdm
   ```

3. من مجلد المشروع الرئيسي، قم بتشغيل السكريبت:
   ```
   python download_ml_models.py
   ```

4. انتظر حتى يكتمل تنزيل جميع النماذج

5. الآن يمكنك بناء وتشغيل المشروع في Android Studio

## الطريقة 2: التنزيل اليدوي

إذا كنت تفضل تنزيل النماذج يدوياً:

1. افتح ملف `assets-config.yml` الموجود في المشروع

2. قم بتنزيل كل ملف من الروابط المذكورة:
   - [logical_reasoning_model.tflite](https://github.com/Zmmoly/zamouli-ai-assistant-520319/releases/download/assets-522032/logical_reasoning_model.tflite) (128 ميجابايت)
   - [arabic_dialect_model.tflite](https://github.com/Zmmoly/zamouli-ai-assistant-520319/releases/download/assets-522032/arabic_dialect_model.tflite) (1 ميجابايت)
   - [medical_analyzer_model.tflite](https://github.com/Zmmoly/zamouli-ai-assistant-520319/releases/download/assets-522032/medical_analyzer_model.tflite) (1 ميجابايت)
   - [voice_emotion_model.tflite](https://github.com/Zmmoly/zamouli-ai-assistant-520319/releases/download/assets-522032/voice_emotion_model.tflite) (5.3 ميجابايت)
   - [model.tflite](https://github.com/Zmmoly/zamouli-ai-assistant-520319/releases/download/assets-522032/model.tflite) (0.5 كيلوبايت)
   - [labels.txt](https://github.com/Zmmoly/zamouli-ai-assistant-520319/releases/download/assets-522032/labels.txt) (0.1 كيلوبايت)

3. أنشئ المجلدات التالية (إذا لم تكن موجودة):
   - `app/src/main/assets/`
   - `app/src/main/assets/temp_models/`
   - `app/ml/`

4. ضع كل ملف في المسار المناسب حسب ملف `assets-config.yml`:
   - `app/src/main/assets/logical_reasoning_model.tflite`
   - `app/src/main/assets/arabic_dialect_model.tflite`
   - `app/src/main/assets/medical_analyzer_model.tflite`
   - `app/src/main/assets/voice_emotion_model.tflite`
   - `app/src/main/assets/labels.txt`
   - `app/ml/model.tflite`

5. الآن يمكنك بناء وتشغيل المشروع في Android Studio

## ملاحظات

- تأكد من أن لديك مساحة كافية على القرص (حوالي 150 ميجابايت مطلوبة للنماذج)
- إذا واجهت مشاكل في الوصول إلى الروابط، تأكد من تسجيل الدخول إلى GitHub
- بعض النماذج قد تكون كبيرة وتستغرق وقتاً للتنزيل اعتماداً على سرعة الإنترنت لديك

## المساعدة

إذا واجهت أي مشاكل، يرجى مراجعة [صفحة المشكلات](https://github.com/Zmmoly/zamouli-ai-assistant-520319/issues) أو فتح مشكلة جديدة.