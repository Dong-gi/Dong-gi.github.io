@echo off
setlocal enabledelayedexpansion
REM Start Wi-Fi Tether on Windows
REM Prerequisites:
REM   - PC connected to the phone's Wi-Fi hotspot (192.168.49.x)
REM   - Android app installed and "Start" tapped (hotspot must be running on the phone)
REM   - tun2proxy.exe + wintun.dll in the same directory as this script
REM     Download or Build: https://github.com/tun2proxy/tun2proxy

REM --- Auto-elevate to Administrator if not already ---
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting Administrator privileges...
    powershell -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

REM --- SOCKS5 port is fixed at 1080 ---
REM The app no longer falls back to 1081-1089, and this script no longer probes.
REM Probing accepted whichever port answered a SOCKS5 greeting, which let a
REM malicious app on the phone squat 1080, answer 05 00, and become the proxy for
REM 100%% of the PC's traffic. Nothing in the greeting authenticates the peer.
REM If 1080 is taken the app now fails loudly and shows the reason in its UI.
set "PORT=1080"
if not "%~1"=="" set "PORT=%~1"

echo [1/1] Starting tun2proxy on socks5://192.168.49.1:%PORT% (requires Administrator for TUN adapter + routing)...
REM UDP ASSOCIATE is supported, so --dns over-tcp is not needed.
REM --setup automatically adds a host route for 192.168.49.1 via the Wi-Fi interface,
REM preventing the proxy traffic itself from looping back into the TUN adapter.
"%~dp0tun2proxy.exe" --tun USBTether --dns-addr 1.1.1.1 --setup --proxy socks5://192.168.49.1:%PORT%

endlocal
