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

# --- SOCKS5 port is fixed at 1080 ---
# The app no longer falls back to 1081-1089, and this script no longer probes.
# Probing accepted whichever port answered a SOCKS5 greeting, which let a malicious
# app on the phone squat 1080, answer 05 00, and become the proxy for 100% of the
# Mac's traffic. Nothing in the greeting authenticates the peer.
# If 1080 is taken the app now fails loudly and shows the reason in its UI.
PORT="${1:-1080}"

echo ""
echo "[1/1] Starting tun2proxy on socks5://192.168.49.1:$PORT ..."
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
