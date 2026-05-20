#!/bin/bash
# Start Wi-Fi Tether on macOS
# Prerequisites:
#   - tun2proxy (macOS binary, arm64 or x86_64) in the same directory
#       https://github.com/tun2proxy/tun2proxy/releases
#   - Mac connected to the phone's Wi-Fi hotspot (192.168.49.x)
#   - Android app installed and "Start" tapped (hotspot must be running on the phone)
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

echo "[1/2] Probing SOCKS5 on 192.168.49.1 ports 1080-1089..."
PORT=""
for ((p=1080; p<=1089; p++)); do
    if probe_socks5 192.168.49.1 $p; then
        PORT=$p
        echo "  port $p: SOCKS5 OK"
        break
    else
        echo "  port $p: no SOCKS5 reply"
    fi
done

if [ -z "$PORT" ]; then
    echo ""
    echo "Failed to find a SOCKS5 server on 192.168.49.1 ports 1080-1089."
    echo "Check that the Mac is connected to the phone's hotspot and the app's 'Start' was pressed."
    exit 1
fi

echo ""
echo "[2/2] Starting tun2proxy on socks5://192.168.49.1:$PORT ..."
# UDP ASSOCIATE is supported in Wi-Fi mode, so --dns over-tcp is not needed.
# --setup automatically adds a host route for 192.168.49.1 via the Wi-Fi interface,
# preventing the proxy traffic itself from looping back into the TUN adapter.
# utun5: avoids utun0-utun3 commonly used by VPN software
cleanup() {
    echo ""
    echo "Cleaning up..."
    networksetup -setdnsservers Wi-Fi Empty 2>/dev/null
    route delete 192.168.49.1 2>/dev/null
    ifconfig utun5 destroy 2>/dev/null
}
trap cleanup EXIT INT TERM

"$DIR/tun2proxy" --tun utun5 --dns-addr 1.1.1.1 --setup --proxy socks5://192.168.49.1:$PORT
