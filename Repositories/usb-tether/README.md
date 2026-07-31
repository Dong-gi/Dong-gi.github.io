# USB Tether (ADB 채널 + Wi-Fi 핫스팟)

안드로이드 폰의 셀룰러 연결을 다른 기기로 공유. 두 가지 경로를 동시에 제공합니다.

1. **USB**: 폰의 SOCKS5(기본 1080)를 ADB 포트 포워딩으로 PC에 노출 → PC에서 `tun2proxy`로 모든 트래픽 라우팅.
2. **Wi-Fi 핫스팟**: 폰이 Wi-Fi Direct 그룹 오너(GO)로 동작해 다른 기기를 직접 받음.
   - 클라이언트는 `192.168.49.1:8282`(HTTP 프록시) 또는 `192.168.49.1:1080`(SOCKS5)를 사용.
   - 안드로이드/PC의 시스템 Wi-Fi 수동 프록시 설정은 HTTP만 지원하므로 8282 사용. SOCKS5는 SOCKS 지원 앱(Firefox 등) 또는 VPN 기반 SOCKS 클라이언트로 활용.

> **포트 충돌 시 자동으로 다음 포트로 이동합니다.** 두 프록시 모두 `기본 포트` ~ `기본 포트 + 9` 범위(최대 10번)를 순서대로 시도합니다. 예를 들어 NetShare가 8282를 점유하면 HTTP 프록시는 8283을, 그것도 막혀 있으면 8284…8291까지 시도합니다. 앱 화면과 알림에 **실제 사용 중인 포트**가 표시되니 클라이언트 설정에는 거기 적힌 숫자를 그대로 쓰세요. USB 모드의 `start-windows.bat`도 동일하게 1080~1089를 스캔합니다.

## 아키텍처

```
USB 경로:
[PC] → WinTun "USBTether" → tun2proxy → adb forward → 폰 127.0.0.1:1080 (Socks5Server)
                                                            ↓ java.net.Socket
                                                            Android OS 스택 → Cellular

Wi-Fi 경로:
[Wi-Fi 클라이언트] ↔ 폰 GO @ 192.168.49.1
   ├─ HTTP 시스템 프록시   → :8282 (HttpProxyServer)
   └─ SOCKS5 지원 클라이언트 → :1080 (Socks5Server)
                                ↓ java.net.Socket
                                Android OS 스택 → Cellular
```

핵심 통찰은 폰이 PC의 IP 패킷을 그대로 포워딩하지 않는다는 것입니다.
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
│           │   ├── PeerFilter.kt        # 접속 허용 대역 판정 (루프백 + 192.168.49.0/24)
│           │   └── WifiHotspot.kt       # Wi-Fi Direct GO (커스텀 SSID/패스프레이즈)
│           └── res/
└── output/                    # 사전 빌드된 실행 파일 / 스크립트
    ├── adb.exe                # Android Debug Bridge (Windows)
    ├── tun2proxy.exe          # TUN → SOCKS5 브릿지 (Windows)
    ├── wintun.dll             # WinTun 가상 NIC 드라이버 (Windows 전용)
    ├── usb-tether.apk         # 컴파일된 안드로이드 앱
    ├── windows-usb.bat        # Windows 시작 스크립트 (USB/ADB 모드)
    ├── windows-wifi.bat       # Windows 시작 스크립트 (Wi-Fi 핫스팟 모드)
    ├── macos-usb.sh           # macOS 시작 스크립트 (USB/ADB 모드)
    └── macos-wifi.sh          # macOS 시작 스크립트 (Wi-Fi 핫스팟 모드)
```

## 셋업 순서

### 사전 준비

1. **안드로이드 폰**
   - 설정 → 개발자 옵션 → USB 디버깅 활성화
   - 처음 PC에 연결 시 RSA 키 승인 다이얼로그를 한 번 허용

2. **PC**
   - `output/` 폴더에 `adb.exe`, `tun2proxy.exe`, `wintun.dll`이 포함되어 있음 — 별도 설치 불필요
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

1. **Start** 탭 — `0.0.0.0:1080`(SOCKS5)와 `0.0.0.0:8282`(HTTP) 프록시 서버가 기동됩니다. USB 모드만 쓴다면 여기까지면 끝.
2. Wi-Fi 핫스팟이 필요할 때만 **SSID**와 **패스프레이즈**를 확인하고 **Start hotspot** 탭. 핫스팟은 배터리 소모가 크므로 기본은 꺼져 있고, 필요할 때만 켰다 끕니다.
   - 패스프레이즈는 **첫 실행 시 기기마다 다른 20자 난수**가 생성되어 입력란에 채워집니다(약 116비트). 그대로 쓰시고, 접속할 기기에 그 값을 옮겨 적으세요.
   - 직접 바꿀 수도 있지만 WPA2 규격상 8–63자여야 하고, `12345678` 처럼 명백히 추측하기 쉬운 값은 거부됩니다.
3. 핫스팟 첫 실행 시 **NEARBY_WIFI_DEVICES**(Android 13+) 또는 **위치** 권한(Android 10–12)을 허용해야 GO 생성이 가능합니다.

> SSID는 Wi-Fi Direct 사양상 `DIRECT-` 접두사가 필요해 입력값 앞에 `DIRECT-UT-`가 자동으로 붙습니다.
> (예: `MyPhone` → `DIRECT-UT-MyPhone`)

### Wi-Fi 클라이언트에서 사용

폰의 핫스팟에 접속한 뒤 IP는 자동으로 `192.168.49.x`가 할당됩니다 (게이트웨이 `192.168.49.1`).

- **시스템 전역(가장 단순)**: 해당 Wi-Fi 네트워크의 프록시 설정을 **수동**으로 바꿔 `192.168.49.1:8282` 입력. 모든 시스템 프록시 인식 앱이 자동 라우팅.
- **SOCKS5**: Firefox(`about:config`의 `network.proxy.socks`), 또는 SocksDroid/Every Proxy 같은 VPN 기반 SOCKS 클라이언트로 `192.168.49.1:1080` 사용.

### 연결 시작 (Windows)

#### USB 모드

```bat
output\windows-usb.bat
```

스크립트가 하는 일:
1. 관리자 권한으로 자동 재실행 (WinTun 드라이버 로드에 필요)
2. `1080`부터 `1089`까지 순서대로 시도하며 각 포트에 대해:
   - `adb forward tcp:N tcp:N` 등록
   - PowerShell로 `127.0.0.1:N`에 SOCKS5 NO_AUTH 그리팅(`0x05 0x01 0x00`)을 보내고 `0x05 0x00` 응답을 1.5초 안에 받으면 성공으로 판정
   - 성공한 포트 `N`을 채택, 실패한 포트는 forward를 즉시 해제
3. 채택된 포트로 `tun2proxy.exe --tun USBTether --dns over-tcp --dns-addr 1.1.1.1 --setup --proxy socks5://127.0.0.1:N` 실행
   - 가상 NIC "USBTether" (10.42.0.1/24) 생성 및 디폴트 라우팅 자동 설정
   - DNS는 TCP를 통해 1.1.1.1로 라우팅 (ADB가 UDP를 지원하지 않으므로)
4. 종료 시 채택된 포트의 ADB 포워드만 정리

#### Wi-Fi 핫스팟 모드

```bat
output\windows-wifi.bat
```

스크립트가 하는 일:
1. 관리자 권한으로 자동 재실행
2. `192.168.49.1`의 `1080`~`1089` 포트를 순서대로 SOCKS5 그리팅으로 탐색, 응답하는 첫 포트 채택
3. 채택된 포트로 `tun2proxy.exe --tun USBTether --dns-addr 1.1.1.1 --setup --proxy socks5://192.168.49.1:N` 실행
   - `--setup`이 `192.168.49.1`에 대한 host route를 Wi-Fi 인터페이스로 유지 → TUN 루프 방지
   - UDP ASSOCIATE를 지원하므로 `--dns over-tcp` 불필요 (DNS·게임·WebRTC 등 UDP 트래픽도 릴레이)
4. ADB 포워드 없음 — 종료 시 별도 정리 작업 없음

> 두 스크립트 모두 폰의 SOCKS5 서버가 폴백되거나 PC 포트가 점유돼도 자동으로 다음 포트로 넘어갑니다. 10개 포트 모두 실패하면 에러 메시지와 함께 종료합니다.

### 연결 시작 (macOS)

#### 사전 준비

macOS용 바이너리는 `output/` 에 포함되어 있지 않으므로 직접 준비해야 합니다.

- **adb** (USB 모드만 필요): `brew install android-platform-tools` 또는 [platform-tools](https://developer.android.com/tools/releases/platform-tools) zip에서 추출해 `output/` 에 배치
- **tun2proxy**: [github.com/tun2proxy/tun2proxy/releases](https://github.com/tun2proxy/tun2proxy/releases) 에서 아키텍처에 맞는 파일 다운로드
  - Apple Silicon: `tun2proxy-aarch64-apple-darwin.tar.gz`
  - Intel: `tun2proxy-x86_64-apple-darwin.tar.gz`
  - 압축 해제 후 바이너리를 `output/tun2proxy` 로 배치
- **wintun.dll 불필요**: macOS는 커널에 TUN/utun 인터페이스가 내장되어 있습니다.

스크립트를 처음 실행하기 전에 실행 권한을 부여합니다:

```bash
chmod +x output/macos-usb.sh output/macos-wifi.sh
```

#### USB 모드

```bash
output/macos-usb.sh
```

#### Wi-Fi 핫스팟 모드

```bash
output/macos-wifi.sh
```

두 스크립트 모두 `sudo` 로 자동 재실행하고 포트 탐색·tun2proxy 실행 과정은 Windows 스크립트와 동일합니다. TUN 인터페이스명은 `utun5` 를 사용합니다(VPN 소프트웨어가 주로 점유하는 `utun0–utun3` 를 피하기 위함).

## 한계

- **UDP**: Wi-Fi 핫스팟 모드에서는 SOCKS5 UDP ASSOCIATE를 지원합니다(tun2proxy가 192.168.49.1 UDP 릴레이 포트에 직접 도달 가능). USB 모드는 ADB가 TCP만 포워딩하므로 UDP 릴레이 소켓에 도달할 수 없어 여전히 미지원 — `--dns over-tcp`로 DNS만 우회. HTTP 프록시는 TCP 전용이므로 양쪽 모두 UDP 불가.
- **성능**: 사용자 공간 처리이므로 폰 CPU가 수백 Mbps 처리에 한계
- **MTU 조정**: 캡슐화 오버헤드로 인한 fragmentation 문제 가능
- **Wi-Fi 핫스팟 API 레벨**: 커스텀 SSID/패스프레이즈는 Android 10(API 29) 이상에서만 동작. 그 미만은 USB 경로만 사용 가능.
- **프록시는 인증 없음**: `PeerFilter`가 접속 대역을 루프백과 `192.168.49.0/24`로 제한하므로 다른 네트워크에서는 도달할 수 없습니다. 다만 그 대역 안에서는 인증이 없어, Wi-Fi 패스프레이즈가 사실상 유일한 접근 통제선입니다.
- **폰의 다른 앱은 루프백으로 프록시를 쓸 수 있음**: USB 모드(`adb forward`) 때문에 루프백을 허용해야 하므로 막을 수 없습니다. 그 앱들은 VpnService 기반 방화벽과 UID별 데이터 제한을 우회하며, 트래픽이 이 앱에 귀속됩니다.
- **약관**: 한국 통신사 약관 위반 소지 — 사용자 책임

## 라이선스

학습·실험 목적. 상업적 사용 금지.
