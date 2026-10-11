@echo off
setlocal
REM Start Wi-Fi Tether on Windows -- PC side of the Web app's tethering.
REM See README.txt in the same zip for setup and downloads. Stop with Ctrl+C in this window.
REM This file is kept ASCII-only on purpose: cmd.exe reads batch files in the console code page, not UTF-8.
REM Prerequisites:
REM   - PC connected to the phone's Wi-Fi Direct hotspot (192.168.49.x, SSID DIRECT-UT-...)
REM   - On the phone: turn on Tethering in Web (menu / Tethering, or the Tethering quick-settings tile)
REM   - tun2proxy.exe + wintun.dll in the same folder as this script (see README.txt)

REM --- Auto-elevate to Administrator if not already ---
REM fltmc succeeds only as Administrator. net session also needs the Server service,
REM and with that service off it failed even when elevated, so the UAC prompt looped.
fltmc >nul 2>&1
if %errorlevel% equ 0 goto :elevated

echo Requesting Administrator privileges...
REM -ArgumentList has to be passed explicitly. Without it the elevated copy starts
REM with no arguments, so an explicit port (windows-wifi.bat 1085) silently reverts
REM to 1080 -- and that is the normal path, since this script is nearly always
REM launched non-elevated. Reverting to 1080 either fails to connect or, worse,
REM connects to whatever else is sitting on 1080.
if "%~1"=="" (
    powershell -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
) else (
    powershell -Command "Start-Process -FilePath '%~f0' -Verb RunAs -ArgumentList '%*'"
)
exit /b

:elevated

REM --- SOCKS5 port is fixed at 1080 ---
REM The app no longer falls back to 1081-1089, and this script no longer probes.
REM Probing accepted whichever port answered a SOCKS5 greeting, which let a
REM malicious app on the phone squat 1080, answer 05 00, and become the proxy for
REM 100%% of the PC's traffic. Nothing in the greeting authenticates the peer.
REM If 1080 is taken the app now fails loudly and shows the reason in its UI.
set "PORT=1080"
if not "%~1"=="" set "PORT=%~1"

if not exist "%~dp0tun2proxy.exe" (
    echo tun2proxy.exe was not found next to this script. See README.txt.
    pause
    exit /b 1
)

echo [1/1] Starting tun2proxy on socks5://192.168.49.1:%PORT% (requires Administrator for TUN adapter + routing)...
REM UDP ASSOCIATE is supported, so --dns over-tcp is not needed.
REM --setup automatically adds a host route for 192.168.49.1 via the Wi-Fi interface,
REM preventing the proxy traffic itself from looping back into the TUN adapter.
"%~dp0tun2proxy.exe" --tun USBTether --dns-addr 1.1.1.1 --setup --proxy socks5://192.168.49.1:%PORT%
REM The elevated window closes when this script ends - stop on an error so the message can be read.
if errorlevel 1 pause

endlocal
