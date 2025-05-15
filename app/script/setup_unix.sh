#!/bin/bash

echo "===== اداة تثبيت متطلبات تطبيق زمولي للذكاء الاصطناعي ====="
echo

# التحقق من وجود Python
echo "الخطوة 1: التحقق من وجود Python..."
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 غير مثبت على جهازك!"
    echo
    
    # التحقق من نوع نظام التشغيل وتقديم تعليمات مناسبة
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        echo "لتثبيت Python 3 على macOS، يمكنك استخدام Homebrew:"
        echo "1. قم بتثبيت Homebrew إذا لم يكن مثبتاً:"
        echo "   /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        echo "2. قم بتثبيت Python 3:"
        echo "   brew install python3"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        echo "لتثبيت Python 3 على Linux، استخدم مدير الحزم الخاص بالتوزيعة:"
        echo "- Ubuntu/Debian: sudo apt-get update && sudo apt-get install python3 python3-pip"
        echo "- Fedora: sudo dnf install python3 python3-pip"
        echo "- Arch Linux: sudo pacman -S python python-pip"
    fi
    
    echo
    echo "بعد التثبيت، قم بتشغيل هذا السكريبت مرة أخرى."
    exit 1
else
    echo "✅ تم العثور على Python 3"
    echo "   $(python3 --version)"
fi

# التحقق من وجود pip
echo
echo "الخطوة 2: التحقق من وجود pip..."
if ! command -v pip3 &> /dev/null; then
    echo "❌ pip3 غير مثبت على جهازك!"
    echo
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        echo "لتثبيت pip على macOS:"
        echo "   brew install python3"  # يأتي مع Python عادة
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        echo "لتثبيت pip على Linux:"
        echo "- Ubuntu/Debian: sudo apt-get install python3-pip"
        echo "- Fedora: sudo dnf install python3-pip"
        echo "- Arch Linux: sudo pacman -S python-pip"
    fi
    
    echo
    echo "بعد التثبيت، قم بتشغيل هذا السكريبت مرة أخرى."
    exit 1
else
    echo "✅ تم العثور على pip"
fi

# تثبيت المكتبات المطلوبة
echo
echo "الخطوة 3: تثبيت المكتبات المطلوبة..."
echo

echo "تثبيت pyyaml..."
pip3 install pyyaml

echo "تثبيت requests..."
pip3 install requests

echo "تثبيت tqdm..."
pip3 install tqdm

echo
echo "✅ تم تثبيت جميع المكتبات المطلوبة بنجاح!"
echo
echo "✨ اكتملت عملية التهيئة! يمكنك الآن بناء تطبيق زمولي من خلال Android Studio."
echo "✨ سيتم تنزيل نماذج الذكاء الاصطناعي تلقائياً أثناء عملية البناء."
echo

# جعل السكريبت قابل للتنفيذ
chmod +x $(dirname "$0")/setup_and_download.py