Web 테더링 — PC 연결 파일

폰의 Web 이 Wi-Fi Direct 핫스팟(SSID DIRECT-UT-…, 폰 주소 192.168.49.1)을 열면
PC 가 거기에 붙어 폰의 회선으로 인터넷을 씁니다.

들어 있는 것
  README.txt         이 글
  windows-wifi.bat   Windows — PC 의 모든 트래픽을 폰으로 보내는 시작 스크립트
  macos-wifi.sh      macOS — 같은 일
  tun2proxy(실행 파일)는 들어 있지 않습니다 — 아래에서 받습니다.

먼저 — 폰
  Web › 메뉴(⋮) › 테더링(또는 빠른 설정 타일 ‘테더링’)을 켭니다.
  SSID·패스프레이즈와 실제 포트는 그 화면에 보입니다.

Windows
  1. PC 를 폰의 Wi-Fi(DIRECT-UT-…)에 연결합니다.
  2. https://github.com/tun2proxy/tun2proxy/releases 에서
     tun2proxy-x86_64-pc-windows-msvc.zip 을 받아 풉니다.
  3. tun2proxy-bin.exe 의 이름을 tun2proxy.exe 로 바꿔, 같은 zip 의 wintun.dll 과
     함께 windows-wifi.bat 옆에 둡니다.
  4. windows-wifi.bat 을 실행하고 관리자 권한 요청을 허용합니다
     (Windows 가 막으면 ‘추가 정보 › 실행’).
  5. 창을 열어 둔 채 씁니다. 끝낼 때는 그 창에서 Ctrl+C(물으면 Y).
  오류가 나면 창이 멈춰 메시지를 보여 줍니다 — tun2proxy.exe·wintun.dll 의 이름과 자리부터 확인하세요.

macOS
  1. Mac 을 폰의 Wi-Fi(DIRECT-UT-…)에 연결합니다.
  2. 같은 곳에서 tun2proxy-aarch64-apple-darwin.zip(Apple 실리콘) 또는
     tun2proxy-x86_64-apple-darwin.zip(Intel)을 받아 풉니다.
  3. tun2proxy-bin 의 이름을 tun2proxy 로 바꿔 macos-wifi.sh 옆에 둡니다.
  4. 터미널에서 그 폴더로 가서: sudo bash macos-wifi.sh
  5. 끝낼 때는 Ctrl+C.
  실행이 막히면: chmod +x tun2proxy
                xattr -d com.apple.quarantine tun2proxy

PC 에 아직 인터넷이 없으면
  tun2proxy 를 받는 동안만 PC 의 시스템 프록시를 192.168.49.1:8282(HTTP)로 두고,
  받은 뒤 지웁니다. 실제 HTTP 포트는 Web 의 테더링 화면에 보입니다.
  tun2proxy 를 쓰는 동안에는 시스템·환경 변수의 프록시를 두지 마세요 —
  HTTP 포트는 8283… 으로 옮겨 설 수 있습니다.

스크립트 없이 — 프록시만
  앱·브라우저의 프록시를 HTTP 192.168.49.1:8282(실제 포트는 테더링 화면)
  또는 SOCKS5 192.168.49.1:1080 으로 둡니다. 그 앱의 트래픽만 폰으로 갑니다.

공유 폴더
  폰의 Web › 테더링 › 공유 폴더에서 폴더를 고른 뒤, PC 의 브라우저에서
  http://192.168.49.1:8080 을 엽니다(실제 포트는 테더링 화면).

남의 프로그램
  tun2proxy 는 MIT 라이선스, wintun.dll 은 Wintun 자체 라이선스를 따릅니다.
  둘 다 이 zip 에 들어 있지 않습니다.
