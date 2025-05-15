#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
سكريبت لتنزيل نماذج التعلم الآلي لتطبيق زمولي من GitHub Releases
ووضعها في المجلدات المناسبة داخل مشروع Android Studio

يتم استدعاء هذا السكريبت تلقائياً أثناء عملية البناء في Android Studio
من خلال مهمة Gradle المخصصة "downloadMLModels"

يمكن أيضاً تشغيله يدوياً من سطر الأوامر:
  python download_ml_models.py [project_root_path]
"""

import os
import sys
import yaml
import requests
from pathlib import Path
from tqdm import tqdm

# المسار الجذر للمشروع (يمكن تغييره حسب موقع مشروعك)
# إذا تم تمرير المسار كمعامل في سطر الأوامر، استخدمه
if len(sys.argv) > 1:
    PROJECT_ROOT = Path(sys.argv[1])
else:
    PROJECT_ROOT = Path('.')  # استخدم المجلد الحالي كمسار افتراضي

def download_file(url, destination_path):
    """تنزيل ملف من URL إلى المسار المحدد مع إظهار شريط التقدم"""
    # التأكد من وجود المجلد
    os.makedirs(os.path.dirname(destination_path), exist_ok=True)
    
    # إجراء الطلب مع استخدام stream=True للملفات الكبيرة
    response = requests.get(url, stream=True)
    response.raise_for_status()  # التحقق من نجاح الطلب
    
    # الحصول على حجم الملف إذا كان متاحاً
    total_size = int(response.headers.get('content-length', 0))
    
    # إنشاء شريط التقدم
    progress_bar = tqdm(
        total=total_size,
        unit='B',
        unit_scale=True,
        desc=f"تنزيل {os.path.basename(destination_path)}"
    )
    
    # كتابة الملف تدريجياً مع تحديث شريط التقدم
    with open(destination_path, 'wb') as file:
        for chunk in response.iter_content(chunk_size=8192):
            if chunk:
                file.write(chunk)
                progress_bar.update(len(chunk))
    
    progress_bar.close()
    return destination_path

def main():
    """الدالة الرئيسية للسكريبت"""
    # قراءة ملف التكوين
    config_path = PROJECT_ROOT / 'assets-config.yml'
    
    print(f"⏳ قراءة ملف التكوين من {config_path}...")
    try:
        with open(config_path, 'r', encoding='utf-8') as config_file:
            config = yaml.safe_load(config_file)
    except FileNotFoundError:
        print(f"❌ خطأ: ملف التكوين غير موجود في {config_path}")
        print("   تأكد من تشغيل السكريبت من المجلد الصحيح.")
        return 1  # رمز خطأ لعملية الخروج
    except yaml.YAMLError as e:
        print(f"❌ خطأ في تنسيق ملف YAML: {e}")
        return 1  # رمز خطأ لعملية الخروج
    
    # استخراج معلومات الإصدار والروابط
    release_tag = config.get('releaseTag')
    print(f"ℹ️ تم العثور على إصدار: {release_tag}")
    
    # تنزيل الملفات
    print("🔄 جارٍ تنزيل النماذج...")
    models_count = 0
    errors_count = 0
    
    # تجاوز المفاتيح الأخرى في الملف
    for path, url in config.items():
        # تجاهل المفاتيح غير المتعلقة بالملفات
        if not isinstance(url, str) or not url.startswith('http'):
            continue
            
        # تجاهل المفاتيح المعلوماتية
        if path in ['releaseTag', 'releaseUrl', 'تاريخ_الإنشاء', 'تاريخ_التحديث']:
            continue
        
        # تحويل المسار النسبي إلى مسار كامل
        full_path = PROJECT_ROOT / path
        
        # التحقق مما إذا كان الملف موجودًا بالفعل (لتجنب التنزيل المتكرر)
        if os.path.exists(full_path):
            print(f"✓ الملف موجود بالفعل: {path}")
            models_count += 1
            continue
        
        # إنشاء المجلدات اللازمة
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        
        try:
            download_file(url, full_path)
            models_count += 1
            print(f"✅ تم تنزيل {path}")
        except Exception as e:
            errors_count += 1
            print(f"❌ فشل تنزيل {path}: {e}")
    
    # طباعة ملخص العمليات
    print(f"\n==== ملخص تنزيل النماذج ====")
    print(f"✓ عدد النماذج المتوفرة/المنزلة: {models_count}")
    if errors_count > 0:
        print(f"❌ عدد الأخطاء: {errors_count}")
        if errors_count == len(config) - 3:  # طرح المفاتيح المعلوماتية الثلاثة
            print("⚠️ فشل تنزيل جميع النماذج! قد لا يعمل التطبيق بشكل صحيح.")
            return 1  # رمز خطأ
    
    print("\n✨ اكتمل التنزيل! التطبيق جاهز للبناء والتشغيل.")
    
    return 0  # نجاح العملية

if __name__ == "__main__":
    print("🤖 أداة تنزيل النماذج لتطبيق زمولي للذكاء الاصطناعي")
    print("=" * 60)
    exit_code = main()
    sys.exit(exit_code)  # إرجاع رمز الخروج المناسب للنظام