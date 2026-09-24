@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem  Outgo build script
rem
rem  Usage:
rem    build.bat            -> debug APK  (fast, unsigned, installable as-is)
rem    build.bat release     -> release APK (minified, signed with the keystore
rem                             from keystore.properties, which needs the keys
rem                             storeFile/storePassword/keyAlias/keyPassword.
rem                             Both it and the keystore are gitignored, so a
rem                             fresh clone has to create them - see the README.
rem                             Without them the APK comes out unsigned and
rem                             Android refuses to install it.)
rem    build.bat clean       -> clean + debug APK
rem ============================================================

cd /d "%~dp0"

set VARIANT=debug
set GRADLE_TASK=assembleDebug
if /I "%~1"=="release" (
    set VARIANT=release
    set GRADLE_TASK=assembleRelease
)
if /I "%~1"=="clean" (
    set GRADLE_TASK=clean assembleDebug
)

rem --- Make sure Gradle can find the Android SDK -------------------------
if not exist "local.properties" (
    if defined ANDROID_HOME (
        echo Writing local.properties with sdk.dir=%ANDROID_HOME%
        > local.properties echo sdk.dir=%ANDROID_HOME:\=/%
    ) else if defined ANDROID_SDK_ROOT (
        echo Writing local.properties with sdk.dir=%ANDROID_SDK_ROOT%
        > local.properties echo sdk.dir=%ANDROID_SDK_ROOT:\=/%
    ) else (
        echo.
        echo [WARN] No local.properties and no ANDROID_HOME / ANDROID_SDK_ROOT
        echo        environment variable found. If the build fails below,
        echo        install Android Studio ^(it installs the SDK^) or set
        echo        ANDROID_HOME to your SDK path and re-run this script.
        echo.
    )
)

echo.
echo === Building Outgo (%VARIANT%) ===
echo.

call gradlew.bat %GRADLE_TASK% --console=plain
set BUILD_RESULT=%ERRORLEVEL%

if not "%BUILD_RESULT%"=="0" (
    echo.
    echo === Build FAILED ^(exit code %BUILD_RESULT%^) ===
    exit /b %BUILD_RESULT%
)

echo.
echo === Build succeeded ===

set APK_DIR=app\build\outputs\apk\%VARIANT%
set FOUND_APK=
for %%F in ("%APK_DIR%\*.apk") do (
    echo APK: %%~fF
    set FOUND_APK=%%~fF
)

if defined FOUND_APK (
    echo.
    echo Install on a connected device/emulator with:
    echo   adb install -r "!FOUND_APK!"
    if /I "%VARIANT%"=="release" (
        if exist "keystore.properties" (
            echo.
            echo [NOTE] Signed with the keystore from keystore.properties.
            echo        Verify with: apksigner verify --verbose "!FOUND_APK!"
        ) else (
            echo.
            echo [WARN] No keystore.properties found, so this APK is UNSIGNED and
            echo        Android will reject it with "invalid package" or "app not
            echo        installed". Create a signing config first - see the
            echo        "Release signing" section of README.md.
        )
    )
    echo.
)

endlocal
