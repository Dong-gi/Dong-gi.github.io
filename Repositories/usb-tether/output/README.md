# `output/` — 직접 받아야 하는 실행 파일

이 폴더의 스크립트 둘(`windows-wifi.bat`, `macos-wifi.sh`)은 저장소에 들어 있다.
**나머지 실행 파일은 들어 있지 않다.** 아래 표대로 직접 받아 이 폴더에 두어야 한다.

## 왜 커밋하지 않는가

남이 빌드한 바이너리이고 재배포는 각자의 라이선스가 정한다. 게다가 이 저장소는
GitHub Pages 로 통째로 게시되므로, 여기 커밋한 것은 곧 웹에 공개하는 것이 된다
(`https://dong-gi.github.io/Repositories/usb-tether/output/…`).

`.gitignore` 는 원래 `pc/wintun.dll` 만 무시했고 WinTun 에 "license terms — don't
commit" 이라고 적어 두었는데, `output/` 은 그 규칙에 걸리지 않아 다섯 개가 그대로
커밋되어 한동안 공개되어 있었다. 지금은 이름으로 무시하므로 어느 폴더에 두든 걸린다.

## 무엇을 어디서

| 파일 | 무엇 | 어디서 | 비고 |
|---|---|---|---|
| `tun2proxy.exe` | TUN → SOCKS5 브릿지 (Windows) | [tun2proxy releases](https://github.com/tun2proxy/tun2proxy/releases) | `tun2proxy-x86_64-pc-windows-msvc` |
| `tun2proxy` | 같은 것 (macOS) | 같은 곳 | Apple Silicon 은 `aarch64-apple-darwin`, Intel 은 `x86_64-apple-darwin` |
| `wintun.dll` | WinTun 가상 NIC 드라이버 | [wintun.net](https://www.wintun.net/) | 배포 zip 의 `bin/amd64/wintun.dll`. Windows 전용 |
| `adb.exe` | Android Debug Bridge (Windows) | [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) | APK 설치·진단용. **선택** |
| `adb` | 같은 것 (macOS universal) | 같은 곳 | **선택** |

`adb` 는 없어도 된다. APK 를 `./gradlew installDebug` 로 넣거나 폰으로 직접
복사하면 된다. 데이터 경로에는 관여하지 않는다.

macOS 에서는 `wintun.dll` 이 필요 없다 — 커널에 TUN/utun 이 들어 있다.

## 받은 뒤

```bash
chmod +x output/tun2proxy output/adb output/macos-wifi.sh   # macOS
file output/tun2proxy                                        # arm64 인지 확인
```

## 마지막으로 확인한 것

저장소에서 뺄 때 들어 있던 파일의 SHA-256 이다. 같은 것을 받았는지 확인하는 데만
쓴다. **새로 받은 것이 이것과 달라도 정상이다** — 그 사이 판올림되었을 수 있다.

```
9fdf861259dc807937b13afdd5f053c7fda9f3b7726933fe0e0f45130ecb8dc7  adb            19993936
957e46b8615f7af5b7292a2ddabe98d2e61940c3fb2b0545756507f080613e71  adb.exe         8485016
de265e6030d195a478178eec8ba5f68736946425fa3430e61015e2631c8023b3  tun2proxy       5783024
5f090a505ba95edfd6c325a143c172ea6bf5cf3dcb35a2e592155a859671ff01  tun2proxy.exe   5814784
e5da8447dc2c320edc0fc52fa01885c103de8c118481f683643cacc3220dafce  wintun.dll       427552
```
