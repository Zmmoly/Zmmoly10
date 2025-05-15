@echo off
echo ===== اداة تثبيت متطلبات تطبيق زمولي للذكاء الاصطناعي =====
echo.

:: التحقق من وجود Python
echo الخطوة 1: التحقق من وجود Python...
python --version > nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Python غير مثبت على جهازك!
    echo.
    echo يجب تثبيت Python أولاً. قم بتنزيله من:
    echo https://www.python.org/downloads/
    echo.
    echo تأكد من تحديد خيار "Add Python to PATH" أثناء التثبيت.
    echo.
    echo بعد التثبيت، قم بتشغيل هذا السكريبت مرة أخرى.
    pause
    exit /b 1
) else (
    echo ✅ تم العثور على Python
)

:: تثبيت المكتبات المطلوبة
echo.
echo الخطوة 2: تثبيت المكتبات المطلوبة...
echo.

echo تثبيت pyyaml...
pip install pyyaml

echo تثبيت requests...
pip install requests

echo تثبيت tqdm...
pip install tqdm

echo.
echo ✅ تم تثبيت جميع المكتبات المطلوبة بنجاح!
echo.
echo ✨ اكتملت عملية التهيئة! يمكنك الآن بناء تطبيق زمولي من خلال Android Studio.
echo ✨ سيتم تنزيل نماذج الذكاء الاصطناعي تلقائياً أثناء عملية البناء.
echo.

pause