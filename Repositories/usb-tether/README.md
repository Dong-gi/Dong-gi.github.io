# USB Tether (Wi-Fi Direct 핫스팟)

안드로이드 폰의 셀룰러 연결을 다른 기기로 공유합니다. 폰이 Wi-Fi Direct 그룹 오너(GO)로 동작해 클라이언트를 직접 받습니다.

- 클라이언트는 `192.168.49.1:8282`(HTTP 프록시) 또는 `192.168.49.1:1080`(SOCKS5)를 사용.
- 안드로이드/PC의 시스템 Wi-Fi 수동 프록시 설정은 HTTP만 지원하므로 8282 사용. SOCKS5는 SOCKS 지원 앱(Firefox 등), VPN 기반 SOCKS 클라이언트, 또는 PC의 `tun2proxy`로 활용.

> **이름은 과거 흔적입니다.** 초기에는 ADB 포트 포워딩을 쓰는 USB 경로도 제공했지만 제거했습니다. 앱 이름·패키지명(`com.example.usbtether`)은 그대로 두었습니다.

> **SOCKS5(1080)는 포트를 옮기지 않습니다.** 점유돼 있으면 조용히 다른 포트로 가지 않고 실패하며, 앱 화면에 이유가 표시됩니다. 예전에는 1081–1089로 폴백하고 PC 스크립트가 그 범위를 훑어 SOCKS5 응답이 오는 첫 포트를 채택했는데, 폰의 악성 앱이 1080을 먼저 잡고 응답만 흉내내면 PC 트래픽 전량을 가로챌 수 있었습니다.
>
> **HTTP(8282)는 여전히 8283…8291까지 폴백합니다.** 이쪽은 사용자가 앱 화면에 표시된 포트를 보고 직접 입력하므로 자동 탐색으로 가로챌 여지가 없습니다. 예를 들어 NetShare가 8282를 점유하면 8283이 쓰입니다. 클라이언트 설정에는 **앱 화면·알림에 적힌 숫자**를 그대로 쓰세요.

## 아키텍처

```
[Wi-Fi 클라이언트 192.168.49.x] ↔ 폰 GO @ 192.168.49.1
   ├─ HTTP 시스템 프록시     → :8282 (HttpProxyServer)
   └─ SOCKS5 지원 클라이언트 → :1080 (Socks5Server)
                                ↓ java.net.Socket
                                Android OS 스택 → Cellular
```

핵심 통찰은 폰이 클라이언트의 IP 패킷을 그대로 포워딩하지 않는다는 것입니다.
폰의 자체 TCP/IP 스택이 새 소켓을 열어 페이로드만 옮기므로, 통신사가 보는 트래픽은
완전히 폰이 직접 만든 패킷처럼 보입니다 (TTL, TCP 옵션, MSS 등 모두 정상).

## 디렉터리 구조

```
usb-tether/
├── README.md                  # 이 파일
├── CLAUDE.md                  # Claude Code 가이드 (영문)
├── android/                   # 안드로이드 앱 (Kotlin)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── kotlin/com/example/usbtether/
│           │   ├── MainActivity.kt      # 시작/정지 UI, SSID/패스프레이즈 입력, 통계 표시
│           │   ├── TetherService.kt     # 포그라운드 서비스, 세 컴포넌트 수명 관리
│           │   ├── Socks5Server.kt      # RFC 1928 SOCKS5 서버 (0.0.0.0:1080)
│           │   ├── HttpProxyServer.kt   # HTTP/CONNECT 프록시 (0.0.0.0:8282)
│           │   ├── PeerFilter.kt        # 접속 허용 대역 판정 (192.168.49.2–254)
│           │   ├── DestinationFilter.kt # 목적지 허용 판정 (루프백·사설·CGNAT 차단)
│           │   ├── IpAddress.kt         # IPv4-mapped IPv6 정규화 (두 필터 공용)
│           │   └── WifiHotspot.kt       # Wi-Fi Direct GO (커스텀 SSID/패스프레이즈)
│           └── res/
└── output/                    # 사전 빌드된 실행 파일 / 스크립트
    ├── adb.exe                # Android Debug Bridge (APK 설치·진단용)
    ├── tun2proxy.exe          # TUN → SOCKS5 브릿지 (Windows)
    ├── wintun.dll             # WinTun 가상 NIC 드라이버 (Windows 전용)
    ├── usb-tether.apk         # 컴파일된 안드로이드 앱
    ├── windows-wifi.bat       # Windows 시작 스크립트
    └── macos-wifi.sh          # macOS 시작 스크립트
```

## 셋업 순서

### 사전 준비

1. **안드로이드 폰**
   - APK를 `adb`로 설치할 경우에만: 설정 → 개발자 옵션 → USB 디버깅 활성화, 처음 PC에 연결 시 RSA 키 승인
   - APK를 폰으로 직접 옮겨 설치한다면 개발자 옵션은 필요 없습니다

2. **PC**
   - `output/` 폴더에 `tun2proxy.exe`, `wintun.dll`이 포함되어 있음 — 별도 설치 불필요
   - 관리자 권한 필요 (WinTun 드라이버 로드)

### Android 앱 설치

사전 빌드된 APK 직접 설치:

```bash
output\adb.exe install output\usb-tether.apk
```

또는 소스에서 빌드:

```bash
cd android
./gradlew installDebug
```

설치 후 폰에서 앱을 열고:

1. **Start** 탭 — `0.0.0.0:1080`(SOCKS5)와 `0.0.0.0:8282`(HTTP) 프록시 서버가 기동됩니다.
2. **SSID**와 **패스프레이즈**를 확인하고 **Start hotspot** 탭. 핫스팟은 배터리 소모가 크므로 기본은 꺼져 있고, 필요할 때만 켰다 끕니다.
   - 패스프레이즈는 **첫 실행 시 기기마다 다른 20자 난수**가 생성되어 입력란에 채워집니다(약 116비트). 그대로 쓰시고, 접속할 기기에 그 값을 옮겨 적으세요.
   - 직접 바꿀 수도 있지만 WPA2 규격상 8–63자여야 하고, `12345678` 처럼 명백히 추측하기 쉬운 값은 거부됩니다.
3. 핫스팟 첫 실행 시 **NEARBY_WIFI_DEVICES**(Android 13+) 또는 **위치** 권한(Android 10–12)을 허용해야 GO 생성이 가능합니다.

> SSID는 Wi-Fi Direct 사양상 `DIRECT-` 접두사가 필요해 입력값 앞에 `DIRECT-UT-`가 자동으로 붙습니다.
> (예: `MyPhone` → `DIRECT-UT-MyPhone`)

### Wi-Fi 클라이언트에서 사용

폰의 핫스팟에 접속한 뒤 IP는 자동으로 `192.168.49.x`가 할당됩니다 (게이트웨이 `192.168.49.1`).

- **시스템 전역(가장 단순)**: 해당 Wi-Fi 네트워크의 프록시 설정을 **수동**으로 바꿔 `192.168.49.1:8282` 입력. 모든 시스템 프록시 인식 앱이 자동 라우팅.
- **SOCKS5**: Firefox(`about:config`의 `network.proxy.socks`), 또는 SocksDroid/Every Proxy 같은 VPN 기반 SOCKS 클라이언트로 `192.168.49.1:1080` 사용.
- **PC 전체 트래픽**: 아래 시작 스크립트로 `tun2proxy` 실행.

### 연결 시작 (Windows)

```bat
output\windows-wifi.bat
```

스크립트가 하는 일:
1. 관리자 권한으로 자동 재실행 (WinTun 드라이버 로드에 필요)
2. `tun2proxy.exe --tun USBTether --dns-addr 1.1.1.1 --setup --proxy socks5://192.168.49.1:1080` 실행
   - `--setup`이 `192.168.49.1`에 대한 host route를 Wi-Fi 인터페이스로 유지 → TUN 루프 방지
   - UDP ASSOCIATE를 지원하므로 `--dns over-tcp` 불필요 (DNS·게임·WebRTC 등 UDP 트래픽도 릴레이)

> 포트를 탐색하지 않습니다. 응답하는 아무 포트나 채택하는 동작이 스쿼팅 탈취 경로였습니다. 포트를 바꿔야 한다면 인자로 넘기세요: `output\windows-wifi.bat 1085`

### 연결 시작 (macOS)

#### 사전 준비

macOS용 바이너리는 `output/` 에 포함되어 있지 않으므로 직접 준비해야 합니다.

- **tun2proxy**: [github.com/tun2proxy/tun2proxy/releases](https://github.com/tun2proxy/tun2proxy/releases) 에서 아키텍처에 맞는 파일 다운로드
  - Apple Silicon: `tun2proxy-aarch64-apple-darwin.tar.gz`
  - Intel: `tun2proxy-x86_64-apple-darwin.tar.gz`
  - 압축 해제 후 바이너리를 `output/tun2proxy` 로 배치
- **adb** (APK 설치용, 선택): `brew install android-platform-tools`
- **wintun.dll 불필요**: macOS는 커널에 TUN/utun 인터페이스가 내장되어 있습니다.

스크립트를 처음 실행하기 전에 실행 권한을 부여합니다:

```bash
chmod +x output/macos-wifi.sh
```

#### 실행

```bash
output/macos-wifi.sh
```

`sudo` 로 자동 재실행하며 동작은 Windows 스크립트와 동일합니다. 포트를 바꿔야 하면 인자로 넘기세요: `output/macos-wifi.sh 1085`. TUN 인터페이스명은 `utun5` 를 사용합니다(VPN 소프트웨어가 주로 점유하는 `utun0–utun3` 를 피하기 위함).

## 한계

- **UDP**: SOCKS5 UDP ASSOCIATE를 지원합니다(tun2proxy가 192.168.49.1 UDP 릴레이 포트에 직접 도달 가능). HTTP 프록시는 TCP 전용이므로 UDP 불가.
- **성능**: 사용자 공간 처리이므로 폰 CPU가 수백 Mbps 처리에 한계
- **MTU 조정**: 캡슐화 오버헤드로 인한 fragmentation 문제 가능
- **Android 10(API 29) 이상 필요**: 커스텀 SSID/패스프레이즈가 그 이상에서만 동작하며, Wi-Fi Direct 경로가 유일한 경로입니다.
- **프록시는 인증 없음**: `PeerFilter`가 접속 대역을 `192.168.49.2`–`192.168.49.254`로 제한하므로 다른 네트워크와 폰 내부 앱은 도달할 수 없습니다. 다만 그 대역 안에서는 인증이 없어, Wi-Fi 패스프레이즈가 사실상 유일한 접근 통제선입니다.
- **목적지 포트는 제한하지 않음**: 클라이언트 트래픽 전량을 통과시키는 용도라 포트 허용 목록을 두면 메일·SSH·게임 등이 깨집니다. 위험의 본질은 포트가 아니라 도달 범위라고 판단해 주소로만 막습니다.
- **약관**: 한국 통신사 약관 위반 소지 — 사용자 책임

## 라이선스

학습·실험 목적. 상업적 사용 금지.
