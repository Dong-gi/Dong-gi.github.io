# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

USB Tether shares Android cellular data with another device over a **Wi-Fi Direct hotspot** the phone creates itself. (The name is historical — an ADB-port-forwarding USB path existed early on and has been removed. The app name and package `com.example.usbtether` were left unchanged.) The Android app runs two proxy servers on `0.0.0.0`:
  - **SOCKS5** (RFC 1928) on port `1080` — **no fallback**. If the port is taken the server fails and surfaces the reason via `Socks5Server.lastError` → `TetherService.proxyError` → the UI. Silently moving to 1081 combined with the launcher's "accept whichever port answers a SOCKS5 greeting" probe was a hijack primitive: a malicious app squatting 1080 would be adopted by the launcher and see 100% of the PC's traffic
  - **HTTP** (CONNECT + absolute-URI forwarding) on port `8282` (falls back to `8283`…`8291` in order — e.g. when the NetShare app is already squatting on 8282)

The actually-bound ports are shown in the app UI and the foreground-service notification. **Always read those instead of assuming the base ports**, since a co-installed app (NetShare, another tether app) may force the fallback.

Plus an optional Wi-Fi Direct group owner (GO) with a user-chosen SSID/passphrase — clients land at `192.168.49.1` and can use either proxy.

Clients set the system per-network proxy to `192.168.49.1:8282` (HTTP), use a SOCKS5-aware app / VPN-based SOCKS client against port `1080`, or run `tun2proxy` on a PC to route all of its traffic through the SOCKS5 proxy.

The phone's native TCP/IP stack opens every outbound connection, making traffic appear phone-originated to the carrier in every mode.

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
1. `output/` already contains `tun2proxy.exe` and `wintun.dll` — no separate download needed. `adb.exe` is kept only for installing the APK and for diagnostics.
2. **Windows (Administrator):** run `output/windows-wifi.bat` — it auto-elevates, probes `192.168.49.1` ports 1080–1089, and launches tun2proxy with `--setup`. `--dns over-tcp` is not needed because UDP ASSOCIATE works over Wi-Fi Direct.

The `pc/` directory (legacy Rust bridge) is no longer used and can be ignored.

## Architecture

```
[Wi-Fi client @ 192.168.49.x] → [phone GO @ 192.168.49.1]
    ├─ HTTP system proxy   → :8282 (HttpProxyServer)
    └─ SOCKS5-aware client → :1080 (Socks5Server)
                                  ↓ java.net.Socket
                                  Android OS stack → Cellular
```

The carrier sees only normal phone-originated sockets — Android's OS TCP/IP stack handles every outbound connection.

### Android Side (`android/app/src/main/kotlin/com/example/usbtether/`)

| File | Role |
|------|------|
| `TetherService.kt` | Foreground service. Proxies always run while the service is up; hotspot is toggled independently via `ACTION_HOTSPOT_ON` / `ACTION_HOTSPOT_OFF` (battery-expensive). Exposes stats to `MainActivity` |
| `Socks5Server.kt` | RFC 1928 SOCKS5 server on `0.0.0.0:basePort` (peer-gated by `PeerFilter`), **no port fallback** — binds 1080 only and reports failure via `lastError`; binds synchronously inside `start()` so callers see the result immediately. Supports CONNECT (TCP relay) and UDP ASSOCIATE (§7 relay via `DatagramSocket`); uses `CachedThreadPool` |
| `PeerFilter.kt` | Which clients may connect (accept-time gate). Checks **both** that the connection arrived on the group owner's own address (`192.168.49.1` — an interface check, so nothing passes while the hotspot is off) and that the peer is `192.168.49.2`–`192.168.49.254`. The peer being `192.168.49.1` is rejected, which is what blocks other apps on the phone |
| `DestinationFilter.kt` | Which destinations may be reached — blocks loopback/private/link-local/CGNAT/ULA/multicast, resolves once and hands back the address object so callers cannot be DNS-rebound |
| `HttpProxyServer.kt` | HTTP proxy on `0.0.0.0:basePort` (peer-gated by `PeerFilter`) with the same 10-attempt fallback (same `start()` contract); supports `CONNECT host:port` (HTTPS tunnel) and absolute-URI forwarding (`GET http://...`); strips `Proxy-Connection`/`Proxy-Authorization`; no auth |
| `WifiHotspot.kt` | Wi-Fi Direct GO via `WifiP2pManager.createGroup(config)` with custom SSID/passphrase (API 29+); SSID auto-prefixed with `DIRECT-UT-` since P2P requires it |
| `MainActivity.kt` | UI: SSID + passphrase fields (persisted via `HotspotPreferences`; the passphrase is encrypted with `SecretStore`), main Start/Stop (proxies), separate Start hotspot / Stop hotspot button, hotspot status, proxy error line, byte counters. Launcher only — it no longer handles any privileged intent action |

### PC Side

The custom Rust binary (`pc/`) is **no longer used**. The scripts delegate to [tun2proxy](https://github.com/blechschmidt/tun2proxy/releases), a standalone binary that handles WinTun/TUN setup, IP assignment, routing, and SOCKS5 client protocol.

## Key Implementation Details

- **SOCKS5 address types:** IPv4 (ATYP=1), domain (ATYP=3), IPv6 (ATYP=4) all supported on inbound CONNECT requests.
- **HTTP proxy:** parses request line + headers byte-by-byte so body bytes stay in the InputStream and are forwarded verbatim by `relay()`. CONNECT replies `HTTP/1.1 200 Connection Established`. Forwarded requests rewrite absolute URI → origin-form path and inject `Host:` if absent.
- **Port policy:** SOCKS5 binds `1080` only and fails loudly otherwise (see above). HTTP still walks `8282`…`8291` because the user reads the chosen port off the UI and types it in manually — there is no automated probe to hijack. The chosen port is stored on the server (`actualPort`) and mirrored to `TetherService.socksPort` / `TetherService.httpPort`, which the notification and stats view read. The launcher scripts no longer probe; they connect to `192.168.49.1:1080` and take an optional port argument.
- **TCP relay:** bidirectional `pipe()` with `shutdownOutput()` half-close so peers receive clean EOF.
- **UDP ASSOCIATE:** supported via a **two-socket** relay (RFC 1928 §7). `clientFacing` binds only to `client.localAddress` (192.168.49.1) and accepts datagrams solely from `client.inetAddress` — the source address is pinned, only the port is learned from the first packet. `remoteFacing` is a separate socket used for the outbound leg, so "reply from remote" is structural rather than inferred from the source address. `BND.ADDR`/`BND.PORT` report `clientFacing`. Destinations go through `DestinationFilter` like the TCP path. Fragmented datagrams (FRAG ≠ 0) are silently dropped. Known gap: replies arriving on `remoteFacing` are not matched against outstanding requests (no NAT table).
- **Relay buffer:** 8 192 bytes per pipe direction.
- **Wi-Fi P2P GO IP:** Android assigns `192.168.49.1/24` to the GO and runs DHCP for clients. Custom SSID/passphrase requires API 29+; passphrase 8–63 chars per WPA2 spec; the GO's SSID is forced to start with `DIRECT-UT-` (Wi-Fi Direct requirement).
- **Android permissions required:** `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`. Plus `NEARBY_WIFI_DEVICES` (API 33+) or `ACCESS_FINE_LOCATION` (API 29–32) for `WifiP2pManager.createGroup`.
- The service declares `android:foregroundServiceType="specialUse"` targeting API 37, minimum API 26 (Wi-Fi hotspot path requires API 29+).

## Dependencies

**Android (`app/build.gradle.kts`):** `androidx.core:core-ktx`, `androidx.appcompat`, `com.google.android.material`.

**External tools (pre-built, in `output/`):** `tun2proxy.exe` + `wintun.dll` (Windows TUN bridge). `adb.exe` (Android platform-tools) is retained for APK installation and diagnostics only — it is not part of any data path.
