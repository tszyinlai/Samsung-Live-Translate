@echo off
setlocal
cd /d "%~dp0"
echo Live Translate Captions Android - Debug APK builder
where gradle >nul 2>nul
if errorlevel 1 (
    echo.
    echo Gradle is not available in PATH.
    echo Please use the included GitHub Actions workflow or install Android Studio,
    echo Android SDK 35, Java 17 and Gradle 8.10.2 first.
    pause
    exit /b 1
)
call gradle --no-daemon testDebugUnitTest assembleDebug
if errorlevel 1 (
    echo.
    echo Build failed. Please share the console output to diagnose the issue.
    pause
    exit /b 1
)
echo.
echo APK created: app\build\outputs\apk\debug\app-debug.apk
pause
