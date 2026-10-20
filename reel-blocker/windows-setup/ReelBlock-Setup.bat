@echo off
setlocal EnableDelayedExpansion
title ReelBlock one-time setup
cd /d "%~dp0"

set PKG=com.azeez.reelblock
set SVC=com.azeez.reelblock/com.azeez.reelblock.BlockerService
set APK_URL=https://github.com/mohammmed-azeez/ShapeAI_Bootcamp_BWD/releases/download/reelblock-latest/ReelBlock.apk
set TOOLS_URL=https://dl.google.com/android/repository/platform-tools-latest-windows.zip
set ADB=platform-tools\adb.exe
set WARN=

echo ============================================================
echo  ReelBlock one-time setup
echo.
echo  Before you start, on the phone:
echo   1. Settings - About device - Version - tap "Build number" 7 times
echo   2. Settings - Additional settings - Developer options:
echo        turn ON "USB debugging"
echo        turn ON "Disable permission monitoring"
echo   3. Connect the phone to this laptop with a USB cable
echo ============================================================
echo.
pause

echo.
echo [1/6] Getting the Android USB tool (adb) from Google...
if not exist "%ADB%" (
    powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest '%TOOLS_URL%' -OutFile 'platform-tools.zip'; Expand-Archive -Force 'platform-tools.zip' '.'"
)
if not exist "%ADB%" (
    echo FAILED: could not download adb. Check the internet and run this file again.
    goto :end
)
echo       OK

echo [2/6] Getting ReelBlock.apk...
if not exist "ReelBlock.apk" (
    powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest '%APK_URL%' -OutFile 'ReelBlock.apk'"
)
if not exist "ReelBlock.apk" (
    echo FAILED: could not download ReelBlock.apk. Check the internet and run this file again.
    goto :end
)
echo       OK

echo [3/6] Waiting for the phone...
echo       If the phone asks "Allow USB debugging?", tick "Always allow" and tap Allow.
%ADB% start-server >nul 2>&1
%ADB% wait-for-device
echo       Phone connected

echo [4/6] Installing ReelBlock... If the phone asks to install, tap Install.
%ADB% shell settings put global verifier_verify_adb_installs 0 >nul 2>&1
%ADB% install -r ReelBlock.apk
if errorlevel 1 (
    echo.
    echo FAILED to install. If it says UPDATE_INCOMPATIBLE, an old ReelBlock with a
    echo different key is on the phone. Uninstall it on the phone, then run this file again.
    %ADB% shell settings put global verifier_verify_adb_installs 1 >nul 2>&1
    goto :end
)
%ADB% shell settings put global verifier_verify_adb_installs 1 >nul 2>&1

echo [5/6] Turning everything on...
%ADB% shell pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS
if errorlevel 1 (
    set WARN=1
    echo       WARNING: self-repair permission was refused.
    echo       Turn ON "Disable permission monitoring" in Developer options and run this file again.
)

set CUR=
for /f "delims=" %%s in ('%ADB% shell settings get secure enabled_accessibility_services') do set CUR=%%s
if /i "!CUR!"=="null" set CUR=
set NEW=%SVC%
if defined CUR (
    echo !CUR! | find /i "%PKG%" >nul
    if errorlevel 1 (
        set NEW=!CUR!:%SVC%
    ) else (
        set NEW=!CUR!
    )
)
%ADB% shell settings put secure enabled_accessibility_services "!NEW!"
%ADB% shell settings put secure accessibility_enabled 1
%ADB% shell dumpsys deviceidle whitelist +%PKG% >nul 2>&1
%ADB% shell cmd appops set %PKG% RUN_IN_BACKGROUND allow >nul 2>&1
%ADB% shell cmd appops set %PKG% RUN_ANY_IN_BACKGROUND allow >nul 2>&1
%ADB% shell dpm set-active-admin %PKG%/.AdminReceiver >nul 2>&1

echo [6/6] Checking...
set ON=
for /f "delims=" %%s in ('%ADB% shell settings get secure enabled_accessibility_services') do set ON=%%s
echo !ON! | find /i "%PKG%" >nul
if errorlevel 1 (
    echo FAILED: the blocker is not on. Send a photo of this window.
    goto :end
)

echo.
echo ============================================================
if defined WARN (
    echo  ReelBlock is ON, but self-repair is missing. See the WARNING above.
) else (
    echo  DONE. ReelBlock is ON and repairs itself if it gets turned off.
)
echo  It locks itself 10 minutes from now. You can unplug the phone.
echo  You never need to open the app.
echo ============================================================

:end
echo.
pause
