#!/bin/bash
# Start Wi-Fi Tether on macOS -- PC side of the Web app's tethering.
# See README.txt in the same zip for setup and downloads. Stop with Ctrl+C.
# Comments stay in English like windows-wifi.bat, which must be ASCII-only (cmd.exe reads it in the console code page).
# Prerequisites:
#   - Mac connected to the phone's Wi-Fi Direct hotspot (192.168.49.x, SSID DIRECT-UT-...)
#   - On the phone: turn on Tethering in Web (menu > Tethering, or the Tethering quick-settings tile)
#   - tun2proxy (macOS binary, arm64 or x86_64, renamed from tun2proxy-bin) in the same folder (see README.txt)
#   - Root (TUN interface creation requires it): run `sudo bash macos-wifi.sh`. Started without sudo,
#     it re-runs itself under sudo.

DIR="$(cd "$(dirname "$0")" && pwd)"

# --- Auto-elevate to root ---
# This used to only print "Run with sudo" and exit, which contradicted both the
# comment above it and the README. Worse, a bare `exit` returns 0, so the failure
# looked like success to anything calling this script.
# "$@" is forwarded so an explicit port survives the re-exec.
# /bin/bash runs the file explicitly: a script saved without the executable bit
# (downloaded, unzipped, copied from Windows) made "sudo $0" fail with
# "command not found" even though "bash macos-wifi.sh" had worked.
if [ "$(id -u)" -ne 0 ]; then
    echo "Re-running with sudo..."
    exec sudo /bin/bash "$0" "$@"
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
    # No DNS reset here. This script never touches the system resolver --
    # tun2proxy applies --dns-addr to the TUN interface only. The previous
    # `networksetup -setdnsservers Wi-Fi Empty` wiped whatever the user had
    # configured on their Wi-Fi service: state this script never created.
    route delete 192.168.49.1 2>/dev/null
}
# Clean up once, on exit: Ctrl+C or a kill becomes an exit (130/143), which runs the EXIT trap.
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$DIR/tun2proxy" --tun utun5 --dns-addr 1.1.1.1 --setup --proxy socks5://192.168.49.1:$PORT
