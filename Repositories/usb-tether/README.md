# USB Tether (ADB 채널 기반)

안드로이드 폰의 셀룰러 연결을 PC로 공유.
폰에서 SOCKS5 프록시를 실행하고 ADB 포트 포워딩으로 PC에 노출한 뒤, PC에서 tun2proxy로 모든 트래픽을 라우팅하는 방식.

## 아키텍처

```
[PC: 모든 IP 트래픽]
  ↓
[PC: WinTun 가상 NIC "USBTether"]
  ↓ SOCKS5 over TCP 127.0.0.1:1080
[tun2proxy binary]
  ↓
[ADB 포트 포워딩 (over USB)]
  ↓
[Android: Socks5Server.kt (RFC 1928)]
  ↓ java.net.Socket (Android OS TCP/IP 스택)
[Cellular modem] → Internet
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
│           │   ├── MainActivity.kt      # 시작/정지 UI, 통계 표시
│           │   ├── TetherService.kt     # 포그라운드 서비스, Socks5Server 수명 관리
│           │   └── Socks5Server.kt      # RFC 1928 SOCKS5 서버 구현
│           └── res/
└── output/                    # 사전 빌드된 실행 파일
    ├── adb.exe                # Android Debug Bridge
    ├── tun2proxy.exe          # TUN → SOCKS5 브릿지 (Windows)
    ├── wintun.dll             # WinTun 가상 NIC 드라이버
    ├── usb-tether.apk         # 컴파일된 안드로이드 앱
    └── start-windows.bat      # Windows 시작 스크립트
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

설치 후 폰에서 앱을 열고 "Start" 탭 — `127.0.0.1:1080`에서 SOCKS5 서버로 대기.

### 연결 시작 (Windows)

```bat
output\start-windows.bat
```

스크립트가 하는 일:
1. 관리자 권한으로 자동 재실행 (WinTun 드라이버 로드에 필요)
2. `adb forward tcp:1080 tcp:1080` — PC `localhost:1080` → 폰 `localhost:1080` 포워딩
3. `tun2proxy.exe --tun USBTether --dns over-tcp --dns-addr 1.1.1.1 --setup --proxy socks5://127.0.0.1:1080` 실행
   - 가상 NIC "USBTether" (10.42.0.1/24) 생성 및 디폴트 라우팅 자동 설정
   - DNS는 TCP를 통해 1.1.1.1로 라우팅 (ADB가 UDP를 지원하지 않으므로)
4. 종료 시 ADB 포워드 정리

## 한계

- **UDP 미지원**: ADB가 TCP만 포워딩하므로 UDP ASSOCIATE는 거부됨 — UDP 의존 앱은 동작하지 않을 수 있음
- **성능**: 사용자 공간 처리이므로 폰 CPU가 수백 Mbps 처리에 한계
- **MTU 조정**: 캡슐화 오버헤드로 인한 fragmentation 문제 가능
- **약관**: 한국 통신사 약관 위반 소지 — 사용자 책임

## 라이선스

학습·실험 목적. 상업적 사용 금지.
