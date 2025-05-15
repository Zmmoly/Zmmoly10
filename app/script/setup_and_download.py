#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
سكريبت متقدم لتنزيل نماذج الذكاء الاصطناعي لتطبيق زمولي
- يتحقق من وجود Python
- يتحقق من وجود المكتبات المطلوبة ويقوم بتثبيتها إذا لم تكن موجودة
- يقوم بتنزيل النماذج ووضعها في المجلدات المناسبة
"""

import os
import sys
import platform
import subprocess
import importlib.util
from pathlib import Path

# قائمة بالمكتبات المطلوبة
REQUIRED_PACKAGES = ["pyyaml", "requests", "tqdm"]

def is_package_installed(package_name):
    """التحقق مما إذا كانت حزمة Python مثبتة"""
    return importlib.util.find_spec(package_name) is not None

def install_package(package_name):
    """تثبيت حزمة Python باستخدام pip"""
    print(f"⏳ جارٍ تثبيت حزمة {package_name}...")
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "install", package_name])
        print(f"✅ تم تثبيت {package_name} بنجاح")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ فشل تثبيت {package_name}: {e}")
        return False

def ensure_packages():
    """التأكد من تثبيت جميع الحزم المطلوبة"""
    print("🔍 التحقق من وجود المكتبات المطلوبة...")
    missing_packages = []
    
    for package in REQUIRED_PACKAGES:
        if not is_package_installed(package):
            missing_packages.append(package)
    
    if missing_packages:
        print(f"ℹ️ المكتبات التالية غير مثبتة: {', '.join(missing_packages)}")
        
        for package in missing_packages:
            if not install_package(package):
                print(f"⚠️ تحذير: فشل تثبيت {package}. قد لا يعمل السكريبت بشكل صحيح.")
    else:
        print("✅ جميع المكتبات المطلوبة متوفرة")

def download_models(project_root):
    """تنزيل نماذج الذكاء الاصطناعي"""
    # استيراد المكتبات المطلوبة (بعد التأكد من تثبيتها)
    import yaml
    import requests
    from tqdm import tqdm
    
    # قراءة ملف التكوين
    config_path = os.path.join(project_root, 'assets-config.yml')
    
    print(f"⏳ قراءة ملف التكوين من {config_path}...")
    try:
        with open(config_path, 'r', encoding='utf-8') as config_file:
            config = yaml.safe_load(config_file)
    except FileNotFoundError:
        print(f"❌ خطأ: ملف التكوين غير موجود في {config_path}")
        return 1
    except yaml.YAMLError as e:
        print(f"❌ خطأ في تنسيق ملف YAML: {e}")
        return 1
    except Exception as e:
        print(f"❌ خطأ غير متوقع أثناء قراءة ملف التكوين: {e}")
        return 1
    
    # استخراج معلومات الإصدار والروابط
    release_tag = config.get('releaseTag')
    print(f"ℹ️ تم العثور على إصدار: {release_tag}")
    
    # تنزيل الملفات
    print("🔄 جارٍ تنزيل النماذج...")
    models_count = 0
    error_count = 0
    
    # تجاوز المفاتيح الأخرى في الملف
    for path, url in config.items():
        # تجاهل المفاتيح غير المتعلقة بالملفات
        if not isinstance(url, str) or not url.startswith('http'):
            continue
            
        # تجاهل المفاتيح المعلوماتية
        if path in ['releaseTag', 'releaseUrl', 'تاريخ_الإنشاء']:
            continue
        
        # تحويل المسار النسبي إلى مسار كامل
        full_path = os.path.join(project_root, path)
        
        # التحقق مما إذا كان الملف موجودًا بالفعل ونفس الحجم
        if os.path.exists(full_path):
            try:
                # التحقق من حجم الملف عبر الإنترنت
                response = requests.head(url)
                online_size = int(response.headers.get('content-length', 0))
                local_size = os.path.getsize(full_path)
                
                # إذا كان الحجم نفسه، تخطي التنزيل
                if online_size > 0 and local_size == online_size:
                    print(f"✅ الملف موجود بالفعل: {path}")
                    models_count += 1
                    continue
            except Exception as e:
                print(f"ℹ️ لا يمكن التحقق من حجم الملف عبر الإنترنت لـ {path}: {e}")
                # في حالة حدوث خطأ، محاولة التنزيل على أي حال
        
        try:
            # التأكد من وجود المجلد
            os.makedirs(os.path.dirname(full_path), exist_ok=True)
            
            # إجراء الطلب مع استخدام stream=True للملفات الكبيرة
            print(f"⏳ تنزيل {os.path.basename(full_path)}...")
            response = requests.get(url, stream=True)
            response.raise_for_status()  # التحقق من نجاح الطلب
            
            # الحصول على حجم الملف إذا كان متاحاً
            total_size = int(response.headers.get('content-length', 0))
            
            # إنشاء شريط التقدم
            progress_bar = tqdm(
                total=total_size,
                unit='B',
                unit_scale=True,
                desc=f"تنزيل {os.path.basename(full_path)}"
            )
            
            # كتابة الملف تدريجياً مع تحديث شريط التقدم
            with open(full_path, 'wb') as file:
                for chunk in response.iter_content(chunk_size=8192):
                    if chunk:
                        file.write(chunk)
                        progress_bar.update(len(chunk))
            
            progress_bar.close()
            models_count += 1
            print(f"✅ تم تنزيل {path}")
        except Exception as e:
            print(f"❌ فشل تنزيل {path}: {e}")
            error_count += 1
    
    print(f"\n✨ اكتمل التنزيل! تم تنزيل/التحقق من {models_count} من النماذج.")
    
    if error_count > 0:
        print(f"⚠️ تم تخطي {error_count} من الملفات بسبب أخطاء.")
        return 1
    
    return 0

def main():
    """الدالة الرئيسية للسكريبت"""
    # إظهار معلومات عن بيئة Python
    print(f"ℹ️ نسخة بايثون: {platform.python_version()}")
    print(f"ℹ️ نظام التشغيل: {platform.system()} {platform.release()}")
    
    # التأكد من تثبيت المكتبات المطلوبة
    ensure_packages()
    
    # استخدام المسار الذي تم تمريره من Gradle أو استخدام الدليل الحالي
    project_root = sys.argv[1] if len(sys.argv) > 1 else '.'
    
    # تنزيل النماذج
    return download_models(project_root)

if __name__ == "__main__":
    print("🤖 أداة تنزيل النماذج المتقدمة لتطبيق زمولي للذكاء الاصطناعي")
    print("=" * 60)
    
    # تشغيل البرنامج الرئيسي وإرجاع رمز الخروج
    sys.exit(main())