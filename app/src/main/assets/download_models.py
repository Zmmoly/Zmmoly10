#!/usr/bin/env python3
"""
سكريبت لتنزيل نماذج التعلم الآلي لتطبيق زمولي من GitHub Releases
يمكن تشغيله مباشرة من Android Studio أو من خلال Gradle
"""

import os
import sys
import requests
from pathlib import Path
from tqdm import tqdm

def download_file(url, destination_path):
    """تنزيل ملف من URL إلى المسار المحدد مع إظهار شريط التقدم"""
    try:
        response = requests.get(url, stream=True)
        response.raise_for_status()
        
        total_size = int(response.headers.get('content-length', 0))
        block_size = 8192
        progress_bar = tqdm(total=total_size, unit='B', unit_scale=True, desc=f"تنزيل {os.path.basename(destination_path)}")
        
        with open(destination_path, 'wb') as file:
            for chunk in response.iter_content(chunk_size=block_size):
                if chunk:
                    file.write(chunk)
                    progress_bar.update(len(chunk))
        
        progress_bar.close()
        print(f"تم تنزيل: {destination_path}")
        return True
    except Exception as e:
        print(f"خطأ في تنزيل {url}: {str(e)}")
        return False

def main(project_root):
    """الدالة الرئيسية للسكريبت"""
    # تحديد مسارات الملفات
    config_path = os.path.join(project_root, "assets-config.yml")
    
    if not os.path.exists(config_path):
        print(f"خطأ: ملف التكوين غير موجود في {config_path}")
        return False
    
    # قراءة ملف التكوين
    with open(config_path, 'r', encoding='utf-8') as config_file:
        config_text = config_file.read()
    
    # تحليل روابط الملفات
    models_to_download = {}
    for line in config_text.splitlines():
        line = line.strip()
        if line and not line.startswith('#'):
            parts = line.split(':', 1)
            if len(parts) == 2:
                file_path = parts[0].strip()
                file_url = parts[1].strip()
                models_to_download[file_path] = file_url
    
    # إنشاء المجلدات اللازمة
    assets_dir = os.path.join(project_root, "app", "src", "main", "assets")
    ml_dir = os.path.join(project_root, "app", "src", "main", "ml")
    
    os.makedirs(assets_dir, exist_ok=True)
    os.makedirs(ml_dir, exist_ok=True)
    
    # تنزيل النماذج
    success_count = 0
    for file_path, url in models_to_download.items():
        # تحديد مسار الوجهة
        if file_path.startswith("app/ml/"):
            rel_path = file_path[len("app/ml/"):]
            dest_dir = ml_dir
        elif file_path.startswith("app/src/main/assets/"):
            rel_path = file_path[len("app/src/main/assets/"):]
            dest_dir = assets_dir
        else:
            # مسار غير معروف، تخطي
            print(f"تم تخطي المسار غير المعروف: {file_path}")
            continue
        
        # إنشاء المجلدات الفرعية إذا لزم الأمر
        if "/" in rel_path:
            subdir = os.path.join(dest_dir, os.path.dirname(rel_path))
            os.makedirs(subdir, exist_ok=True)
        
        # مسار الملف الكامل
        full_dest_path = os.path.join(dest_dir, rel_path)
        
        # تحقق مما إذا كان الملف موجودًا بالفعل
        if os.path.exists(full_dest_path):
            file_size = os.path.getsize(full_dest_path)
            if file_size > 0:
                print(f"الملف موجود بالفعل: {full_dest_path} ({file_size} بايت)")
                success_count += 1
                continue
        
        # تنزيل الملف
        print(f"جاري تنزيل {rel_path} من {url}")
        if download_file(url, full_dest_path):
            success_count += 1
    
    # إظهار ملخص
    print(f"\nتم تنزيل {success_count} من أصل {len(models_to_download)} ملفات.")
    return success_count == len(models_to_download)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        project_root = sys.argv[1]
    else:
        # افتراض أن المجلد الحالي هو جذر المشروع
        project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
    
    print(f"جذر المشروع: {project_root}")
    success = main(project_root)
    sys.exit(0 if success else 1)