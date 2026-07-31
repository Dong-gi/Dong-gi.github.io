# USB Tether (Wi-Fi Direct 핫스팟)

안드로이드 폰의 셀룰러 연결을 다른 기기로 공유합니다. 폰이 Wi-Fi Direct 그룹 오너(GO)로 동작해 클라이언트를 직접 받습니다.

- 클라이언트는 `192.168.49.1:8282`(HTTP 프록시) 또는 `192.168.49.1:1080`(SOCKS5)를 사용.
- 폰의 폴더 하나를 브라우저로 오르내릴 수도 있습니다 — `http://192.168.49.1:8080`.
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
폰의 자체 TCP/IP 스택이 새 소켓을 열어 페이로드만 옮기므로, **IP·TCP 헤더 수준에서는**
폰이 직접 만든 패킷과 같습니다 — TTL, TCP 옵션, MSS 처럼 테더링 탐지에 흔히 쓰이는
헤더 지표가 폰의 것이 됩니다.

다만 **페이로드는 손대지 않고 지나갑니다.** PC 브라우저의 TLS ClientHello 지문(JA3/JA4),
User-Agent, HTTP/2 SETTINGS, OS 별 트래픽 패턴은 그대로 남습니다. 정확한 서술은
"통신사가 구별할 수 없다"가 아니라 "헤더 기반 탐지에는 걸리지 않는다"입니다.
실제 탐지 수준은 검증하지 않았습니다.

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
│           │   ├── MainActivity.kt          # 시작/정지 UI, SSID/패스프레이즈 입력, 통계 표시
│           │   ├── TetherService.kt         # 포그라운드 서비스, 프록시 2개 + 핫스팟 수명 관리
│           │   ├── Socks5Server.kt          # RFC 1928 SOCKS5 서버 (0.0.0.0:1080)
│           │   ├── HttpProxyServer.kt       # HTTP/CONNECT 프록시 (0.0.0.0:8282~8291)
│           │   ├── FileServer.kt            # 브라우저용 파일 서버 (0.0.0.0:8080~8089)
│           │   ├── SharedFolder.kt           # SAF 공유 폴더 (목록·읽기·쓰기, 경로 탈출 차단)
│           │   ├── ConnectionRegistry.kt    # accept 된 소켓 추적 (Stop 이 실제로 끊게)
│           │   ├── PeerFilter.kt            # 접속 허용 판정 (도착 인터페이스 + 상대 주소)
│           │   ├── DestinationFilter.kt     # 목적지 허용 판정 (루프백·사설·CGNAT 차단)
│           │   ├── IpAddress.kt             # IPv4-mapped IPv6 정규화 (두 필터 공용)
│           │   ├── WifiHotspot.kt           # Wi-Fi Direct GO (커스텀 SSID/패스프레이즈)
│           │   ├── HotspotPreferences.kt    # SSID/패스프레이즈 저장, 난수 패스프레이즈 생성
│           │   ├── SecretStore.kt           # 패스프레이즈 AES/GCM 암복호화 (Android Keystore)
│           │   ├── HotspotTileService.kt    # 퀵 설정 타일 (핫스팟 토글)
│           │   └── HotspotStartActivity.kt  # 타일 전용 비공개 트램폴린 (exported=false)
│           ├── assets/
│           │   └── fileman.html          # 파일 서버 웹 UI (단일 파일, 외부 의존 없음)
│           └── res/
└── output/                    # 사전 빌드된 실행 파일 / 스크립트
    ├── adb.exe                # Android Debug Bridge, Windows (APK 설치·진단용)
    ├── adb                    # Android Debug Bridge, macOS universal
    ├── tun2proxy.exe          # TUN → SOCKS5 브릿지 (Windows)
    ├── tun2proxy              # TUN → SOCKS5 브릿지 (macOS, arm64 전용)
    ├── wintun.dll             # WinTun 가상 NIC 드라이버 (Windows 전용)
    ├── windows-wifi.bat       # Windows 시작 스크립트
    └── macos-wifi.sh          # macOS 시작 스크립트
```

> APK 는 저장소에 포함되어 있지 않습니다. 아래처럼 직접 빌드하세요.

## 셋업 순서

### 사전 준비

1. **안드로이드 폰**
   - APK를 `adb`로 설치할 경우에만: 설정 → 개발자 옵션 → USB 디버깅 활성화, 처음 PC에 연결 시 RSA 키 승인
   - APK를 폰으로 직접 옮겨 설치한다면 개발자 옵션은 필요 없습니다

2. **PC**
   - `output/` 폴더에 `tun2proxy.exe`, `wintun.dll`이 포함되어 있음 — 별도 설치 불필요
   - 관리자 권한 필요 (WinTun 드라이버 로드)

### Android 앱 설치

소스에서 빌드해 바로 설치:

```bash
cd android
./gradlew installDebug
```

APK 파일만 만들어 폰으로 옮기고 싶다면:

```bash
cd android
./gradlew assembleDebug
# 결과물: android/app/build/outputs/apk/debug/app-debug.apk
../output/adb.exe install android/app/build/outputs/apk/debug/app-debug.apk   # 또는 폰으로 직접 복사
```

설치 후 폰에서 앱을 열고:

1. **Start** 탭 — `0.0.0.0:1080`(SOCKS5)와 `0.0.0.0:8282`(HTTP) 프록시 서버가 기동됩니다.
2. **SSID**와 **패스프레이즈**를 확인하고 **Start hotspot** 탭. 핫스팟은 배터리 소모가 크므로 기본은 꺼져 있고, 필요할 때만 켰다 끕니다.
   - 패스프레이즈는 **첫 실행 시 기기마다 다른 20자 난수**가 생성되어 입력란에 채워집니다(약 116비트). 그대로 쓰시고, 접속할 기기에 그 값을 옮겨 적으세요.
   - 직접 바꿀 수도 있지만 WPA2 규격상 8–63자여야 하고, `12345678` 처럼 명백히 추측하기 쉬운 값은 거부됩니다.
3. 핫스팟 첫 실행 시 **NEARBY_WIFI_DEVICES**(Android 13+) 또는 **위치** 권한(Android 10–12)을 허용해야 GO 생성이 가능합니다.

> SSID는 Wi-Fi Direct 사양상 `DIRECT-` + 영숫자 두 글자로 시작해야 해서, 입력값 앞에 `DIRECT-UT-`가 자동으로 붙습니다.
> (예: `MyPhone` → `DIRECT-UT-MyPhone`) 입력값이 이미 그 형태라면(`DIRECT-ab...`) 그대로 씁니다.
> 접두사를 포함한 전체 길이가 32바이트를 넘으면 거부됩니다.

프록시 포트가 화면에 `—` 로 나오면 그 포트를 다른 앱이 쓰고 있다는 뜻입니다. 바로 아래 줄에 이유가 표시되고, 점유한 앱을 끄면 **Start** 를 다시 눌러 재시도할 수 있습니다.

### 파일 주고받기 (브라우저)

앱에서 **Choose shared folder** 를 눌러 공개할 폴더 하나를 고른 뒤, 클라이언트 브라우저에서 `http://192.168.49.1:8080` 을 엽니다. 포트는 앱 화면의 `File port` 에 표시된 값을 쓰세요.

- **다운로드**: 파일 이름을 누릅니다. `Range` 를 지원하므로 중단 재개와 동영상 탐색이 됩니다. 한글 파일명도 그대로 저장됩니다(RFC 6266 `filename*`).
- **업로드**: 파일을 페이지에 끌어다 놓거나 **파일 선택**. 진행률이 표시되고, 같은 이름이 있으면 덮어씁니다.
- **폴더 탐색**: 폴더 이름을 눌러 내려가고, 상단 경로에서 올라옵니다.

구현상 유의점:

- **폴더 권한은 SAF 로만 받습니다.** `ACTION_OPEN_DOCUMENT_TREE` 로 고른 폴더 하나에 대한 권한만 가지며, 매니페스트에 스토리지 권한을 선언하지 않습니다. 전체 파일 접근(`MANAGE_EXTERNAL_STORAGE`)은 쓰지 않습니다.
- **접근 통제는 프록시와 동일**합니다(`PeerFilter`). 핫스팟이 꺼져 있으면 도달할 수 없고, 폰 내부의 다른 앱도 도달할 수 없습니다. 그 대역 안에서는 **인증이 없습니다** — 핫스팟에 붙인 기기는 폴더를 읽고 쓸 수 있습니다.
- **폴더 생성·삭제·이름 변경은 없습니다.** 목록·다운로드·업로드만 합니다.
- **업로드는 `PUT`** 입니다. HTML `<form>` 은 GET/POST 만 보낼 수 있어, 악성 웹페이지가 폼 전송으로 업로드를 유발하는 경로가 원천 차단됩니다.
- **`http://` 라 secure context 가 아닙니다.** 그래서 다운로드는 `<a download>` 내비게이션으로 처리합니다 — `fetch` 로 받아 Blob 을 만들면 파일 전체가 브라우저 메모리에 올라갑니다.

### 퀵 설정 타일로 핫스팟 켜고 끄기

앱을 열지 않고 핫스팟만 토글하려면 **퀵 설정 패널 편집 → `Hotspot` 타일 추가**. 저장된 SSID/패스프레이즈로 동작하므로 앱에서 한 번은 값을 확인해 두어야 합니다.

- 타일을 켤 때 화면이 잠깐 깜빡일 수 있습니다. Android 는 이 앱의 액티비티가 포그라운드에 있지 않으면 Wi-Fi Direct 그룹 생성을 계속 거부하므로(`BUSY`), 보이지 않는 트램폴린 액티비티를 띄웠다가 바로 닫습니다.
- 그 액티비티는 `exported="false"` 여서 다른 앱이 호출할 수 없습니다. 핫스팟을 켜는 동작이 외부에서 트리거되면 사용자 조작 없이 무선 공격면이 열리기 때문입니다.

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

- **tun2proxy**: `output/tun2proxy` 가 이미 포함되어 있습니다. 단 **Apple Silicon(arm64) 전용**입니다.
  - Intel Mac 이라면 [github.com/tun2proxy/tun2proxy/releases](https://github.com/tun2proxy/tun2proxy/releases) 에서 `tun2proxy-x86_64-apple-darwin.tar.gz` 를 받아 압축 해제 후 `output/tun2proxy` 를 덮어쓰세요.
  - 확인 방법: `file output/tun2proxy` → `Mach-O 64-bit arm64 executable`
- **adb**: `output/adb` 가 universal 바이너리로 포함되어 있습니다(APK 설치·진단용, 선택).
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
- **프록시는 인증 없음**: `PeerFilter`가 (1) 연결이 도착한 로컬 주소가 그룹 오너 주소(`192.168.49.1`)인지, (2) 상대가 `192.168.49.2`–`192.168.49.254`인지를 함께 봅니다. 따라서 다른 네트워크, 폰 내부 앱, 그리고 **핫스팟이 꺼져 있는 동안의 모든 연결**이 거부됩니다. 다만 그 대역 안에서는 인증이 없어, Wi-Fi 패스프레이즈가 사실상 유일한 접근 통제선입니다.
- **프록시 서비스는 상시 실행**: 앱을 최근 목록에서 밀어 없애도 계속 실행됩니다. 핫스팟이 꺼져 있으면 `PeerFilter`가 모든 연결을 거부하므로 리스너가 열려 있어도 도달 가능한 클라이언트가 없습니다.
- **파일 서버는 chunked 업로드를 받지 않음**: `Content-Length` 가 필요합니다. 브라우저의 `XMLHttpRequest.send(File)` 은 항상 길이를 붙이므로 웹 UI 에서는 문제가 없지만, `curl -T` 같은 도구로 chunked 를 보내면 501 입니다.
- **폴더 목록이 느릴 수 있음**: SAF 는 항목 수만큼 ContentProvider 를 왕복합니다. 항목이 수천 개인 폴더는 목록 응답이 눈에 띄게 느립니다.
- **목적지 포트는 제한하지 않음**: 클라이언트 트래픽 전량을 통과시키는 용도라 포트 허용 목록을 두면 메일·SSH·게임 등이 깨집니다. 위험의 본질은 포트가 아니라 도달 범위라고 판단해 주소로만 막습니다.
- **약관**: 한국 통신사 약관 위반 소지 — 사용자 책임

## 라이선스

학습·실험 목적. 상업적 사용 금지.
