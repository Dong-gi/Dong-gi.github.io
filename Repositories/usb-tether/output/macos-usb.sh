#!/bin/bash
# Start USB Tether on macOS
# Prerequisites:
#   - adb (macOS binary) in the same directory as this script
#       brew install android-platform-tools  OR  place adb from platform-tools here
#   - tun2proxy (macOS binary, arm64 or x86_64) in the same directory
#       https://github.com/tun2proxy/tun2proxy/releases
#   - Phone connected via USB with USB debugging authorized
#   - Android app installed and "Start" tapped
#   - Run with sudo (TUN interface creation requires root)

DIR="$(cd "$(dirname "$0")" && pwd)"

# Auto-elevate
if [ "$(id -u)" -ne 0 ]; then
    echo "Run with sudo"
    exit
fi

probe_socks5() {
    python3 - "$1" "$2" <<'EOF'
import socket, sys
try:
    s = socket.socket()
    s.settimeout(1.5)
    s.connect((sys.argv[1], int(sys.argv[2])))
    s.send(b'\x05\x01\x00')
    r = s.recv(2)
    s.close()
    sys.exit(0 if r == b'\x05\x00' else 1)
except Exception as e:
    sys.exit(1)
EOF
}

echo "[1/2] Probing SOCKS5 across ports 1080-1089..."
PORT=""
for ((p=1080; p<=1089; p++)); do
    if ! "$DIR/adb" forward tcp:$p tcp:$p >/dev/null 2>&1; then
        echo "  port $p: adb forward failed (device missing or port busy)"
        continue
    fi
    if probe_socks5 127.0.0.1 $p; then
        PORT=$p
        echo "  port $p: SOCKS5 OK"
        break
    else
        echo "  port $p: no SOCKS5 reply, removing forward"
        "$DIR/adb" forward --remove tcp:$p >/dev/null 2>&1
    fi
done

if [ -z "$PORT" ]; then
    echo ""
    echo "Failed to find a SOCKS5 server on any of ports 1080-1089."
    echo "Check that the phone is connected, ADB authorized, and the app's 'Start' was pressed."
    exit 1
fi

cleanup() {
    echo "Cleaning up ADB forward tcp:$PORT..."
    "$DIR/adb" forward --remove tcp:$PORT >/dev/null 2>&1
}
trap cleanup EXIT INT TERM

echo ""
echo "[2/2] Starting tun2proxy on socks5://127.0.0.1:$PORT ..."
# utun5: avoids utun0-utun3 commonly used by VPN software
# --dns over-tcp: ADB only forwards TCP, so UDP DNS must go over TCP
cleanup() {
    echo ""
    echo "Cleaning up..."
    networksetup -setdnsservers Wi-Fi Empty 2>/dev/null
    route delete 192.168.49.1 2>/dev/null
    ifconfig utun5 destroy 2>/dev/null
}
trap cleanup EXIT INT TERM

"$DIR/tun2proxy" --tun utun5 --dns over-tcp --dns-addr 1.1.1.1 --setup --proxy socks5://127.0.0.1:$PORT
