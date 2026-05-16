# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

USB Tether shares Android cellular data with a PC over USB. The Android app runs a SOCKS5 proxy server (RFC 1928) on `127.0.0.1:1080`, exposed to the PC via ADB port forwarding. The PC uses `tun2proxy` to route all traffic through that proxy. The phone's native TCP/IP stack opens every outbound connection, making traffic appear phone-originated to the carrier.

## Build Commands

### Android App
```bash
cd android
./gradlew installDebug    # Build and install debug APK on connected device
./gradlew assemble        # Build APK only
./gradlew clean           # Clean build artifacts
```
Requires Android SDK API 34, Java 17. Uses AGP 8.7.0 + Kotlin 2.0.20.

### Running End-to-End
1. `output/` already contains `adb.exe`, `tun2proxy.exe`, and `wintun.dll` — no separate download needed.
2. **Windows (Administrator):** run `output/start-windows.bat` — auto-elevates, forwards port 1080, launches tun2proxy with `--setup`.

The `pc/` directory (legacy Rust bridge) is no longer used and can be ignored.

## Architecture

```
[PC] → [Virtual NIC (WinTun/TUN)] → [tun2proxy binary]
    ↓ SOCKS5 over TCP 127.0.0.1:1080
[ADB port forward over USB]
    ↓
[Socks5Server.kt] (Android SOCKS5 server)
    ↓ java.net.Socket (Android OS stack)
[Cellular modem] → Internet
```

The carrier sees only normal phone-originated sockets — Android's OS TCP/IP stack handles every outbound connection.

### Android Side (`android/app/src/main/kotlin/com/example/usbtether/`)

| File | Role |
|------|------|
| `TetherService.kt` | Foreground service that owns `Socks5Server`; exposes stats to `MainActivity` |
| `Socks5Server.kt` | RFC 1928 SOCKS5 server on `127.0.0.1:1080`; handles CONNECT (TCP relay) only — UDP ASSOCIATE rejected with `REP_CMD_UNSUPPORTED`; uses `CachedThreadPool` |
| `MainActivity.kt` | Simple UI: start/stop service toggle, display active TCP session count and bytes in/out |

### PC Side

The custom Rust binary (`pc/`) is **no longer used**. The scripts delegate to [tun2proxy](https://github.com/blechschmidt/tun2proxy/releases), a standalone binary that handles WinTun/TUN setup, IP assignment, routing, and SOCKS5 client protocol.

## Key Implementation Details

- **SOCKS5 address types:** IPv4 (ATYP=1), domain (ATYP=3), IPv6 (ATYP=4) all supported on inbound CONNECT requests.
- **TCP relay:** bidirectional `pipe()` with `shutdownOutput()` half-close so peers receive clean EOF.
- **UDP ASSOCIATE:** not supported — ADB only forwards TCP, so UDP ASSOCIATE requests are rejected. tun2proxy falls back gracefully with `--dns over-tcp`.
- **Relay buffer:** 8 192 bytes per pipe direction.
- **Android permissions required:** `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
- The service declares `android:foregroundServiceType="specialUse"` targeting API 34, minimum API 26.

## Dependencies

**Android (`app/build.gradle.kts`):** `androidx.core:core-ktx`, `androidx.appcompat`, `com.google.android.material`.

**External tools (pre-built, in `output/`):** `adb.exe` (Android platform-tools), `tun2proxy.exe` + `wintun.dll` (Windows TUN bridge).
