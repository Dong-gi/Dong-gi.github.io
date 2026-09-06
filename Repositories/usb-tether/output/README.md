# `output/` — 직접 받아야 하는 실행 파일

이 폴더에 커밋되어 있는 것은 시작 스크립트 둘(`windows-wifi.bat`, `macos-wifi.sh`)
뿐입니다. 실행 파일은 아래 절차로 받아 같은 폴더에 두세요.

## 왜 커밋하지 않는가

**라이선스가 막아서가 아닙니다.** tun2proxy 는 MIT 이고, Wintun 의 서명된 DLL 도 그것을
공식 API 로만 쓰는 소프트웨어와 함께라면 재배포가 허용됩니다(아래 라이선스 절). 걸리는
것은 `adb` 하나뿐이며 그것도 금지라기보다 판단이 갈리는 자리입니다.

빼기로 한 까닭은 크기와 배포입니다. 다섯 개가 39 MB 였고 그때 저장소 전체가 96 MB
였습니다. 이 저장소는 빌드 산출물까지 커밋해서 클론이 이미 무거운데 거기에 남이 빌드한
실행 파일을 얹을 이유가 없습니다. 게다가 저장소가 GitHub Pages 로 통째로 게시되는 탓에
커밋한 파일은 `https://dong-gi.github.io/Repositories/…` 로 누구나 내려받을 수 있게
됩니다. 직접 만들지 않은 실행 파일을 그 주소로 뿌리는 것은 이 저장소가 할 일이 아닙니다.

## 받는 절차

주소와 크기, 압축 안의 경로는 **2026-09-05 에 직접 확인한 것**입니다. 판올림되면 크기와
버전은 달라지지만 경로의 모양은 대체로 그대로입니다.

### tun2proxy — 반드시 필요합니다

[릴리스](https://github.com/tun2proxy/tun2proxy/releases)에서 기계에 맞는 것을 받습니다.
주소는 `https://github.com/tun2proxy/tun2proxy/releases/latest/download/<파일명>` 꼴이고,
확인한 시점의 최신은 v0.8.3 이며 자산이 16 개 올라와 있었습니다.

| 대상 | 파일명 | 크기 |
|---|---|---|
| Windows x64 | `tun2proxy-x86_64-pc-windows-msvc.zip` | 5.2 MB |
| macOS Apple Silicon | `tun2proxy-aarch64-apple-darwin.zip` | 5.2 MB |
| macOS Intel | `tun2proxy-x86_64-apple-darwin.zip` | 5.4 MB |

압축을 풀면 폴더 없이 파일이 그대로 나옵니다. 본체는 `tun2proxy-bin.exe`(macOS 는
`tun2proxy-bin`)이고 나머지는 `udpgw-server`, `README.md`, `tun2proxy.h`, 그리고
라이브러리 형태의 `tun2proxy.dll`(macOS 는 `libtun2proxy.dylib`)입니다. **Windows zip
에는 `wintun.dll` 도 들어 있어서** 아래 wintun 절을 건너뛰어도 됩니다.

**이름을 바꾸지 않으면 동작하지 않습니다.** 스크립트가 부르는 이름은 `tun2proxy.exe` 와
`tun2proxy` 인데 릴리스는 `tun2proxy-bin` 으로 냅니다. 어긋나면 스크립트가 오류 한 줄
없이 그냥 아무 일도 하지 않으므로 원인을 찾기 어렵습니다.

```powershell
Move-Item tun2proxy-bin.exe output\tun2proxy.exe
```

```bash
mv tun2proxy-bin output/tun2proxy && chmod +x output/tun2proxy
file output/tun2proxy          # arm64 인지 x86_64 인지 확인
```

### wintun.dll — Windows 전용이고 대개는 받을 일이 없습니다

위 Windows zip 에 이미 들어 있으므로 그것으로 충분합니다. 따로 받아야 하는 경우는
32비트나 ARM Windows 를 쓸 때뿐입니다. macOS 는 커널에 TUN/utun 이 들어 있어 아예
필요 없습니다.

```
https://www.wintun.net/builds/wintun-0.14.1.zip      (0.7 MB)
```

압축 안은 아키텍처별로 나뉘어 있습니다. 64비트가 쓸 것은 418 KB 짜리
`wintun/bin/amd64/wintun.dll` 이고, 그 밖에 `bin/arm64/`, `bin/x86/`, `bin/arm/` 과
`wintun/include/wintun.h`, `wintun/LICENSE.txt` 가 들어 있습니다. 자기 것 하나를 꺼내
`output/` 에 두면 됩니다.

### adb — 선택입니다

APK 는 `./gradlew installDebug` 로 넣거나 폰으로 직접 옮겨도 되므로 adb 가 없어도
됩니다. 있으면 APK 설치와 진단이 편할 뿐이고 데이터 경로에는 관여하지 않습니다.

```
https://dl.google.com/android/repository/platform-tools-latest-windows.zip   (7.7 MB)
https://dl.google.com/android/repository/platform-tools-latest-darwin.zip   (15.4 MB)
https://dl.google.com/android/repository/platform-tools-latest-linux.zip     (8.6 MB)
```

압축 안에는 `platform-tools/adb.exe`(또는 `platform-tools/adb`)와 Apache 2.0 고지인
`platform-tools/NOTICE.txt` 가 있습니다. 실행 파일 하나만 꺼내 `output/` 에 두면 되고,
macOS 는 `chmod +x output/adb` 를 잊지 마세요.

### 받은 것이 공식 배포본인가

Windows 파일은 서명으로 가릴 수 있습니다. Wintun 은 WireGuard LLC 가, adb 는 Google
LLC 가 서명해서 내놓으므로 아래와 다른 결과가 나오면 공식 배포본이 아닙니다. tun2proxy
는 서명하지 않으므로 이 방법이 통하지 않고, 대신 릴리스 페이지의 build provenance 로
확인합니다.

```powershell
Get-AuthenticodeSignature .\output\wintun.dll   # Valid, CN=WireGuard LLC
Get-AuthenticodeSignature .\output\adb.exe      # Valid, CN=Google LLC
```

## 라이선스

받아서 자기 기계에서 쓰는 데에는 셋 다 제약이 없습니다. 아래는 이것들을 **다시 배포할
때** 걸리는 조건이고, 이 저장소가 셋을 빼기로 한 판단의 근거이기도 합니다.

| | 라이선스 | 재배포 |
|---|---|---|
| tun2proxy | MIT | 됩니다. 저작권 표시와 라이선스 전문을 함께 실어야 합니다 |
| wintun.dll | Wintun Prebuilt Binaries License | 조건이 맞으면 됩니다 |
| adb | AOSP 소스는 Apache 2.0, Google 빌드는 Android SDK 약관 | 판단이 갈립니다 |

**Wintun 은 GPL 이 아닙니다.** 소스는 GPLv2지만 wintun.net 이 배포하는 서명된 DLL 에는
별도의 더 관대한 라이선스가 붙고(zip 안의 `LICENSE.txt`), 그쪽 문서는 서명된 DLL 이
Wintun 을 배포하는 유일하게 지원되는 방법이라고 적어 두었습니다. 재배포를 막는 조항에도
예외가 하나 있는데 `wintun.h` 가 정한 API 로만 Wintun 을 쓰는 소프트웨어와 함께
배포하는 경우입니다. tun2proxy 의 Windows 릴리스가 `wintun.dll` 을 같이 담는 것이 바로
그 형태이고, 이 저장소의 `output/` 도 같은 배치였습니다.

**adb 만 갈립니다.** Android SDK 약관은 §3.4 에서 재배포를 금지하면서 §3.5 에서는
오픈소스 라이선스가 걸린 구성요소가 그 라이선스만 따른다고 합니다. adb 는 AOSP 에서
Apache 2.0 이므로 어느 조항을 앞세우느냐에 따라 답이 달라집니다. scrcpy 처럼 Windows
릴리스에 `adb.exe` 를 넣어 내놓는 유명한 프로젝트도 있습니다. Apache 2.0 으로 본다면
재배포할 때 라이선스 사본과 `NOTICE` 를 함께 실어야 합니다. 이 저장소는 어느 쪽이
맞는지 가리지 않고 넣지 않는 쪽을 골랐습니다.
