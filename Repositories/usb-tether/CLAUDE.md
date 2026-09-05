# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

USB Tether shares Android cellular data with another device over a **Wi-Fi Direct hotspot** the phone creates itself. (The name is historical — an ADB-port-forwarding USB path existed early on and has been removed. The app name and package `com.example.usbtether` were left unchanged.) The Android app runs two proxy servers on `0.0.0.0`:
  - **SOCKS5** (RFC 1928) on port `1080` — **no fallback**. If the port is taken the server fails and surfaces the reason via `Socks5Server.lastError` → `TetherService.proxyError` → the UI. Silently moving to 1081 combined with the launcher's "accept whichever port answers a SOCKS5 greeting" probe was a hijack primitive: a malicious app squatting 1080 would be adopted by the launcher and see 100% of the PC's traffic
  - **HTTP** (CONNECT + absolute-URI forwarding) on port `8282` (falls back to `8283`…`8291` in order — e.g. when the NetShare app is already squatting on 8282)

The actually-bound ports are shown in the app UI and the foreground-service notification. **Always read those instead of assuming the base ports**, since a co-installed app (NetShare, another tether app) may force the fallback.

Plus an optional Wi-Fi Direct group owner (GO) with a user-chosen SSID/passphrase — clients land at `192.168.49.1` and can use either proxy.

Clients set the system per-network proxy to `192.168.49.1:8282` (HTTP), use a SOCKS5-aware app / VPN-based SOCKS client against port `1080`, or run `tun2proxy` on a PC to route all of its traffic through the SOCKS5 proxy.

The phone's native TCP/IP stack opens every outbound connection, so **at the IP/TCP header level** the traffic is phone-originated in every mode (TTL, TCP options, MSS). Payloads pass through untouched, so client-side fingerprints — TLS ClientHello (JA3/JA4), `User-Agent`, HTTP/2 SETTINGS, OS-specific traffic shape — still say "desktop". This defeats header-based tethering detection, not detection in general, and the boundary has not been measured. Do not restate this as "indistinguishable from the phone's own traffic".

## Build Commands

### Android App
```bash
cd android
./gradlew installDebug    # Build and install debug APK on connected device
./gradlew assemble        # Build APK only
./gradlew clean           # Clean build artifacts
```
Requires Android SDK API 37 (`compileSdk` = `targetSdk` = 37, `minSdk` = 26). AGP **9.2.1**, Gradle **9.4.1**. The Gradle daemon runs on JDK **21** (pinned in `gradle/gradle-daemon-jvm.properties`) while the app itself compiles to Java **17** bytecode (`compileOptions`).

**No Kotlin plugin is applied anywhere, on purpose.** AGP 9 has built-in Kotlin support and carries its own KGP, so `org.jetbrains.kotlin.android` must not be added back — it collides with the new DSL. The trade-off is that the Kotlin version is not pinned in this repo; it follows whatever AGP 9.2.1 bundles. Pin it explicitly if a build ever needs to be reproduced exactly.

### Running End-to-End
1. `output/` ships **only the launchers** (`windows-wifi.bat`, `macos-wifi.sh`) and `output/README.md`. Fetch the rest yourself:
   - Windows: `tun2proxy.exe`, `wintun.dll`, and optionally `adb.exe`
   - macOS: `tun2proxy` (match your arch — arm64 or x86_64) and optionally `adb` (universal)
   - `output/README.md` lists where each comes from, plus the SHA-256 of the copies that used to be committed.
   - **Nothing third-party is committed and nothing should be.** These are other people's builds, their licenses govern redistribution, and this repository is published wholesale as a GitHub Pages site — committing one publishes it. `.gitignore` now matches them by name.
   - There is **no committed APK.** Build it (`./gradlew installDebug`) or install a locally built one. `adb`/`adb.exe` exist only for installing the APK and for diagnostics — they are not part of any data path.
2. **Windows (Administrator):** run `output/windows-wifi.bat` — it auto-elevates and launches tun2proxy against `socks5://192.168.49.1:1080` with `--setup`. It does **not** probe for a port: accepting whichever port answered a SOCKS5 greeting was the hijack primitive described above, so the port is now fixed and only overridable by an explicit argument (`windows-wifi.bat 1085`). `--dns over-tcp` is not needed because UDP ASSOCIATE works over Wi-Fi Direct.
3. **macOS:** run `output/macos-wifi.sh` — same behaviour; it re-execs itself under `sudo` (forwarding arguments) and uses TUN interface `utun5` to avoid the `utun0`–`utun3` range VPN clients tend to occupy.

## Architecture

```
[Wi-Fi client @ 192.168.49.x] → [phone GO @ 192.168.49.1]
    ├─ HTTP system proxy   → :8282 (HttpProxyServer)
    └─ SOCKS5-aware client → :1080 (Socks5Server)
                                  ↓ java.net.Socket
                                  Android OS stack → Cellular
```

Every outbound connection is a normal socket opened by Android's own TCP/IP stack. See the caveat in Project Overview for what payload fingerprints still reveal.

### Android Side (`android/app/src/main/kotlin/com/example/usbtether/`)

| File | Role |
|------|------|
| `TetherService.kt` | Foreground service. Proxies always run while the service is up; hotspot is toggled independently via `ACTION_HOTSPOT_ON` / `ACTION_HOTSPOT_OFF` (battery-expensive). Exposes stats to `MainActivity` |
| `Socks5Server.kt` | RFC 1928 SOCKS5 server on `0.0.0.0:basePort` (peer-gated by `PeerFilter`), **no port fallback** — binds 1080 only and reports failure via `lastError`; binds synchronously inside `start()` so callers see the result immediately. Supports CONNECT (TCP relay) and UDP ASSOCIATE (§7 relay via `DatagramSocket`); uses `CachedThreadPool` |
| `PeerFilter.kt` | Which clients may connect (accept-time gate). Checks **both** that the connection arrived on the group owner's own address (`192.168.49.1` — an interface check, so nothing passes while the hotspot is off) and that the peer is `192.168.49.2`–`192.168.49.254`. The peer being `192.168.49.1` is rejected, which is what blocks other apps on the phone |
| `DestinationFilter.kt` | Which destinations may be reached — blocks loopback/private/link-local/CGNAT/ULA/multicast, `240.0.0.0/4` (incl. `255.255.255.255`), and the IPv6-carrying-IPv4 bypasses (`::/96`, `64:ff9b::/32`, `2002::/16`); resolves once and hands back the address object so callers cannot be DNS-rebound |
| `HttpProxyServer.kt` | HTTP proxy on `0.0.0.0:basePort` (peer-gated by `PeerFilter`). Unlike SOCKS5 it **does** walk 10 ports (`8282`…`8291`) — see Port policy below — but shares the same `start()` contract: binds synchronously and reports failure via `lastError`. Supports `CONNECT host:port` (HTTPS tunnel) and absolute-URI forwarding (`GET http://...`); rejects CL+TE and duplicate-`Host` smuggling combos, strips all hop-by-hop headers, caps header line/count; no auth |
| `IpAddress.kt` | `InetAddress.asIpv4()` — normalizes IPv4-mapped IPv6 (`::ffff:a.b.c.d`). Shared by both filters. Deliberately narrow: `DestinationFilter` blocks the other IPv6-carrying-IPv4 forms by prefix instead, because widening this helper would also widen what `PeerFilter` accepts |
| `FileServer.kt` | Browser-facing file server on `0.0.0.0:8080`…`8089` (peer-gated by `PeerFilter`, same gate as the proxies — so the hotspot being off blocks everything, and other apps on the phone are blocked too). `GET /` serves `assets/fileman.html`; `GET /api/list?path=` returns JSON; `GET /api/file?path=` streams with `Range` support and RFC 6266 `filename*`; `PUT /api/file?path=` streams the body to the file. Upload is `PUT` on purpose — HTML forms can only send GET/POST, so form-based CSRF cannot reach it. No auth inside the allowed range; `authorize()` is the single hook to add it |
| `SharedFolder.kt` | The one folder the file server exposes, over SAF (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`) — no storage permission is declared. Relative paths are split and validated (`splitPath` rejects absolute, `..`, empty segments, backslash, NUL, >32 segments) then walked child-by-child, so escaping the tree is structurally impossible as well |
| `ConnectionRegistry.kt` | Tracks accepted sockets so `stop()` can actually cut live relays (`shutdownNow()` alone does not interrupt a blocked `read()`), and supplies the count used for the 256-session cap. `register()` is called at accept time, not inside `handleClient` |
| `WifiHotspot.kt` | Wi-Fi Direct GO via `WifiP2pManager.createGroup(config)` with custom SSID/passphrase (API 29+); SSID must match `DIRECT-` + 2 alphanumerics, otherwise `DIRECT-UT-` is prepended. On `BUSY` it reuses an existing group **only if SSID and passphrase both match**. Single-use: `stop()` closes the `Channel` |
| `MainActivity.kt` | UI: SSID + passphrase fields (persisted via `HotspotPreferences`; the passphrase is encrypted with `SecretStore`), main Start/Stop (proxies), separate Start hotspot / Stop hotspot button, hotspot status, proxy error line, byte counters. Launcher only — it no longer handles any privileged intent action |
| `HotspotTileService.kt` | Quick Settings tile that toggles the hotspot. OFF goes straight to the service; ON cannot, because `createGroup` keeps failing with `BUSY` unless one of this app's activities is foreground — so it launches the trampoline below |
| `HotspotStartActivity.kt` | Invisible `exported="false"` trampoline for the tile's ON path. Exists so the privileged "start hotspot with the stored credentials" action is not reachable from `MainActivity`, which must stay `exported="true"` for the launcher filter |
| `HotspotPreferences.kt` | Persists SSID + passphrase. Generates a per-device 20-char random passphrase on first use (57-char alphabet, ~116 bits) instead of a constant default; migrates and deletes any plaintext value left by older builds, and treats weak stored values as absent |
| `SecretStore.kt` | AES/GCM encrypt/decrypt of the stored passphrase with a non-exportable Android Keystore key. Platform APIs only — no `androidx.security:security-crypto` dependency |

### PC Side

There is no custom PC binary. An early Rust bridge (`pc/`) was removed from the tree; if older commits or notes mention it, it is gone. The scripts delegate to [tun2proxy](https://github.com/blechschmidt/tun2proxy/releases), a standalone binary that handles WinTun/TUN setup, IP assignment, routing, and SOCKS5 client protocol.

## Key Implementation Details

- **SOCKS5 address types:** IPv4 (ATYP=1), domain (ATYP=3), IPv6 (ATYP=4) all supported on inbound CONNECT requests.
- **HTTP proxy:** parses request line + headers byte-by-byte so body bytes stay in the stream and are forwarded verbatim by `relay()`. The stream is a `BufferedInputStream` and **the same instance is handed to `relay()`** — re-fetching `client.getInputStream()` there would drop whatever the buffer already holds. CONNECT replies `HTTP/1.1 200 Connection Established`. Forwarded requests rewrite absolute URI → origin-form path and inject `Host:` if absent.
- **Port policy:** SOCKS5 binds `1080` only and fails loudly otherwise (see above). HTTP still walks `8282`…`8291` because the user reads the chosen port off the UI and types it in manually — there is no automated probe to hijack. The chosen port is stored on the server (`actualPort`) and mirrored to `TetherService.socksPort` / `TetherService.httpPort`, which the notification and stats view read. The launcher scripts no longer probe; they connect to `192.168.49.1:1080` and take an optional port argument.
- **TCP relay:** bidirectional `pipe()` with `shutdownOutput()` half-close so peers receive clean EOF.
- **UDP ASSOCIATE:** supported via a **two-socket** relay (RFC 1928 §7). `clientFacing` binds only to `client.localAddress` (192.168.49.1) and accepts datagrams solely from `client.inetAddress` — the source address is pinned, only the port is learned from the first packet. `remoteFacing` is a separate socket used for the outbound leg, so "reply from remote" is structural rather than inferred from the source address. `BND.ADDR`/`BND.PORT` report `clientFacing`. Destinations go through `DestinationFilter` like the TCP path. Fragmented datagrams (FRAG ≠ 0) are silently dropped. Domain-form destinations (ATYP=3) are **never resolved on the relay thread** — `resolvedOrWarmUp()` answers from a bounded 60 s cache and otherwise drops the datagram while warming the name up in the background, so one slow lookup cannot stall the association. Clients that resolve names themselves (tun2proxy included) send literals and never hit this path. Known gaps: (a) replies arriving on `remoteFacing` are not matched against outstanding requests (no NAT table); (b) the request's own DST.ADDR/DST.PORT is parsed and then discarded — pinning the client port from it would require honouring it only when non-zero, since RFC 1928 §7 lets clients send zeros.
- **Relay buffer:** 8 192 bytes per pipe direction. The file server uses 64 KiB instead — file transfers move large contiguous chunks.
- **File server:** one request per connection (`Connection: close`), so there is no keep-alive body-framing to get wrong. `HEAD` is deliberately unsupported. Uploads require `Content-Length` (no chunked decoder) and are streamed straight to `ContentResolver.openOutputStream(uri, "wt")` — `"wt"` truncates, plain `"w"` can leave trailing bytes from a longer previous file. A partially written upload is deleted rather than left behind.
- **Wi-Fi P2P GO IP:** Android assigns `192.168.49.1/24` to the GO and runs DHCP for clients. Custom SSID/passphrase requires API 29+; passphrase 8–63 chars per WPA2 spec and obviously-guessable values are rejected; the SSID must match `DIRECT-` + two alphanumerics (Wi-Fi Direct requirement), so anything else gets `DIRECT-UT-` prepended, and the result must fit in 32 bytes.
- **Android permissions declared:** `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`. Plus `NEARBY_WIFI_DEVICES` (API 33+) or `ACCESS_FINE_LOCATION` (`maxSdkVersion="32"`) for `WifiP2pManager.createGroup`. Every one of these is actually used — `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE` and `WAKE_LOCK` were declared but never touched by any code and have been removed. If relaying turns out to stall with the screen off, bring `WAKE_LOCK` back **together with** code that actually acquires a `PARTIAL_WAKE_LOCK`; declaring it alone does nothing.
- **API levels:** `minSdk` 26, so the app installs on Android 8.0+, but the Wi-Fi Direct hotspot path needs **API 29+** for a custom SSID/passphrase and fails with a clear message below that. `targetSdk` 37; the service declares `android:foregroundServiceType="specialUse"`.

## Dependencies

**Android (`app/build.gradle.kts`):** `androidx.core:core-ktx`, `androidx.appcompat`, `com.google.android.material`. No Kotlin plugin — see Build Commands.

**External tools (fetched into `output/`, never committed):** `tun2proxy.exe` + `wintun.dll` (Windows) and `tun2proxy` (macOS). `adb.exe` / `adb` (Android platform-tools) are optional — APK installation and diagnostics only, not part of any data path. No APK is committed either. See `output/README.md`.
