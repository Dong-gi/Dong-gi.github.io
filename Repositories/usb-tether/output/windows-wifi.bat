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

echo [1/2] Probing SOCKS5 on 192.168.49.1 ports 1080-1089...
REM The phone's SOCKS5 server falls back from 1080 upward if a port is already taken.
REM Probe each port with a real SOCKS5 NO_AUTH handshake and pick the first that responds.

set "PORT="
for /L %%P in (1080,1,1089) do (
    if not defined PORT (
        powershell -NoProfile -Command "try { $c=New-Object Net.Sockets.TcpClient; $c.ReceiveTimeout=1500; $c.SendTimeout=1500; $c.Connect('192.168.49.1',%%P); $s=$c.GetStream(); $s.Write([byte[]]@(5,1,0),0,3); $b=New-Object byte[] 2; $n=$s.Read($b,0,2); $c.Close(); if ($n -eq 2 -and $b[0] -eq 5 -and $b[1] -eq 0) { exit 0 } else { exit 1 } } catch { exit 1 }"
        if !errorlevel! equ 0 (
            set "PORT=%%P"
            echo   port %%P: SOCKS5 OK
        ) else (
            echo   port %%P: no SOCKS5 reply
        )
    )
)

if not defined PORT (
    echo.
    echo Failed to find a SOCKS5 server on 192.168.49.1 ports 1080-1089.
    echo Check that the PC is connected to the phone's hotspot and the app's "Start" was pressed.
    pause
    exit /b 1
)

echo.
echo [2/2] Starting tun2proxy on socks5://192.168.49.1:%PORT% (requires Administrator for TUN adapter + routing)...
REM UDP ASSOCIATE is supported in Wi-Fi mode, so --dns over-tcp is not needed.
REM --setup automatically adds a host route for 192.168.49.1 via the Wi-Fi interface,
REM preventing the proxy traffic itself from looping back into the TUN adapter.
"%~dp0tun2proxy.exe" --tun USBTether --dns-addr 1.1.1.1 --setup --proxy socks5://192.168.49.1:%PORT%

endlocal
