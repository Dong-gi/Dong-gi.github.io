@echo off
REM Start USB Tether on Windows
REM Prerequisites:
REM   - adb.exe in PATH or this directory
REM   - Phone connected via USB with USB debugging authorized
REM   - Android app installed and "Start" tapped
REM   - tun2proxy.exe + wintun.dll (amd64) in PATH or this directory
REM     Download or Build: https://github.com/tun2proxy/tun2proxy

REM --- Auto-elevate to Administrator if not already ---
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting Administrator privileges...
    powershell -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo [1/2] Setting up ADB port forward...
%~dp0adb.exe forward tcp:1080 tcp:1080
if errorlevel 1 (
    echo Failed — is the phone connected and ADB authorized?
    pause
    exit /b 1
)

echo [2/2] Starting tun2proxy (requires Administrator for TUN adapter + routing)...
REM --setup: tun2proxy creates the WinTun adapter, assigns 10.42.0.1/24, and adds default route
%~dp0tun2proxy.exe --tun USBTether --dns over-tcp --dns-addr 1.1.1.1 --setup --proxy socks5://127.0.0.1:1080

echo Cleaning up ADB forward...
%~dp0adb.exe forward --remove tcp:1080
