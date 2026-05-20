@echo off
setlocal enabledelayedexpansion
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

echo [1/2] Probing SOCKS5 across ports 1080-1089...
REM Phone's SOCKS5 server falls back from 1080 to 1081 if 1080 is taken,
REM and the PC's local port may also be occupied. Try up to 10 PC:phone pairs
REM (always N:N) and pick the first one where we get a valid SOCKS5 NO_AUTH reply.

set "PORT="
for /L %%P in (1080,1,1089) do (
    if not defined PORT (
        "%~dp0adb.exe" forward tcp:%%P tcp:%%P >nul 2>&1
        if not errorlevel 1 (
            powershell -NoProfile -Command "try { $c=New-Object Net.Sockets.TcpClient; $c.ReceiveTimeout=1500; $c.SendTimeout=1500; $c.Connect('127.0.0.1',%%P); $s=$c.GetStream(); $s.Write([byte[]]@(5,1,0),0,3); $b=New-Object byte[] 2; $n=$s.Read($b,0,2); $c.Close(); if ($n -eq 2 -and $b[0] -eq 5 -and $b[1] -eq 0) { exit 0 } else { exit 1 } } catch { exit 1 }"
            if !errorlevel! equ 0 (
                set "PORT=%%P"
                echo   port %%P: SOCKS5 OK
            ) else (
                echo   port %%P: no SOCKS5 reply, removing forward
                "%~dp0adb.exe" forward --remove tcp:%%P >nul 2>&1
            )
        ) else (
            echo   port %%P: adb forward failed ^(PC port likely busy or device missing^)
        )
    )
)

if not defined PORT (
    echo.
    echo Failed to find a SOCKS5 server on any of ports 1080-1089.
    echo Check that the phone is connected, ADB authorized, and the app's "Start" was pressed.
    pause
    exit /b 1
)

echo.
echo [2/2] Starting tun2proxy on socks5://127.0.0.1:%PORT% (requires Administrator for TUN adapter + routing)...
REM --setup: tun2proxy creates the WinTun adapter, assigns 10.42.0.1/24, and adds default route
"%~dp0tun2proxy.exe" --tun USBTether --dns over-tcp --dns-addr 1.1.1.1 --setup --proxy socks5://127.0.0.1:%PORT%

echo Cleaning up ADB forward tcp:%PORT%...
"%~dp0adb.exe" forward --remove tcp:%PORT%

endlocal
