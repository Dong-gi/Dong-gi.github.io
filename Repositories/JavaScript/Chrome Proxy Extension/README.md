# Remote Proxy Router

Windows Edge 의 모든 트래픽을 해외 클라우드 인스턴스(Oracle Linux 8)의 **TLS 로 감싼 HTTP 포워드 프록시**로 라우팅하는 MV3 확장 + 서버 구성 도구.

이미 nginx(443/80)와 certbot 자동 갱신이 돌고 있는 서버에 **별도 포트(기본 10443)로 얹는** 것을 전제로 한다. 기존 서비스와 인증서 갱신에는 손대지 않는다.

```
[Edge] ──PAC "HTTPS 4joy.is-a.dev:10443"──┐
                                         │
        (이 TCP 연결 자체가 아래 경로로 나간다)
                                         ▼
   WinTun "USBTether" → tun2proxy → 폰 SOCKS5 192.168.49.1:1080
                                  → Android TCP/IP 스택 → 셀룰러
                                  → 해외 인스턴스 :10443 → 목적지

   같은 서버의 :443 은 nginx 가 계속 블로그를 서비스한다 (건드리지 않음)
```

`tun2proxy` 는 IP 계층(기본 라우트)에서, 확장은 L7 에서 동작하므로 서로 간섭하지 않는다. usb-tether 구성에서 시스템 VPN(WireGuard 등)을 쓰지 않는 이유가 이것이다 — 기본 라우트를 두고 충돌한다.

---

## 목차

1. [디렉터리 구조](#디렉터리-구조)
2. [전제 조건](#전제-조건)
3. [1단계 — OCI 인그레스 규칙](#1단계--oci-인그레스-규칙)
4. [2단계 — 서버 구성](#2단계--서버-구성)
5. [3단계 — Edge 확장 설치](#3단계--edge-확장-설치)
6. [4단계 — 검증](#4단계--검증)
7. [설계 결정과 근거](#설계-결정과-근거)
8. [트러블슈팅](#트러블슈팅)
9. [알려진 한계](#알려진-한계)

---

## 디렉터리 구조

```
Chrome Proxy Extension/
├── README.md
├── .gitattributes                    # *.sh 를 LF 로 강제 (CRLF 로 저장되면 Linux 에서 깨진다)
├── package.json                      # type=module (Node 테스트용). 확장 동작과 무관
├── extension/                        # Edge/Chrome MV3 확장 (unpacked 로 로드)
│   ├── manifest.json
│   ├── background.js                 # 서비스 워커: 오케스트레이션, 상태 전이
│   ├── lib/
│   │   ├── constants.js              # 기본 설정 · 상수의 단일 출처
│   │   ├── settings.js               # 영속화 · 검증 · 마이그레이션
│   │   ├── pac.js                    # PAC 스크립트 생성기 (fail-closed)
│   │   ├── proxy.js                  # chrome.proxy 적용/해제, 점유 충돌 판정
│   │   ├── privacy.js                # WebRTC · 네트워크 예측 누출 방어
│   │   ├── auth.js                   # 프록시 407 자동 응답 (onAuthRequired)
│   │   ├── health.js                 # 도달성 · 출구 IP/국가 확인
│   │   └── state.js                  # 런타임 상태 (storage.session)
│   ├── ui/
│   │   ├── popup.html / .css / .js   # ON/OFF 토글, 출구 IP 표시
│   │   ├── options.html / .css / .js # 서버 · 인증 · 예외 · 진단
│   │   └── common.css
│   └── icons/
├── test/
│   └── pac.test.mjs                  # PAC 라우팅 판정 · 설정 검증 단위 테스트
└── server/                           # Oracle Linux 8 구성 도구
    ├── setup-squid.sh                # 메인 셋업 (멱등)
    ├── verify.sh                     # 자체 점검 (cron 에 걸 수 있다)
    ├── uninstall.sh                  # 원복 (nginx·인증서는 건드리지 않음)
    ├── lib/
    │   └── cert.sh                   # 인증서 SAN 커버리지 검사 공통 함수
    └── conf/
        ├── squid-native-tls.conf.tmpl # Squid 가 TLS 종단
        ├── squid-plain.conf.tmpl      # stunnel 폴백용 (루프백 전용)
        ├── stunnel-squid.conf.tmpl
        └── certbot-deploy-hook.sh.tmpl
```

---

## 전제 조건

| | 확인 방법 |
|---|---|
| nginx 가 443/80 을 서비스 중 | `systemctl is-active nginx` |
| certbot 이 인증서를 자동 갱신 중 | `sudo certbot certificates`, `systemctl list-timers \| grep certbot` |
| Edge 가 접속할 호스트명이 그 인증서에 포함 | `sudo certbot certificates` 의 `Domains:` 확인 |
| PC 는 `output/windows-wifi.bat`(tun2proxy) 실행 중 | usb-tether 저장소 참고 |

**이 도구는 인증서를 발급하지 않는다.** 기존 lineage 를 찾아 재사용하고, 갱신 시 복사만 하는 훅을 추가한다.

기존 인증서가 `4joy.is-a.dev` 를 커버하고 있다면 그 이름을 그대로 쓰면 된다. `proxy.4joy.is-a.dev` 처럼 새 이름을 쓰고 싶다면 인증서에 이름을 추가해야 한다 (스크립트가 커버리지 미달을 감지하면 정확한 명령을 출력한다):

```bash
# 주의: 기존 이름을 전부 다시 나열해야 한다. 빠진 이름은 인증서에서 사라진다.
sudo certbot certonly --cert-name 4joy.is-a.dev --nginx \
     -d 4joy.is-a.dev -d proxy.4joy.is-a.dev
```

---

## 1단계 — OCI 인그레스 규칙

셸에서 열 수 없다. **먼저 해야 한다.**

OCI 콘솔 → **Networking → VCN → Subnet → Security List**(또는 인스턴스의 **NSG**) → **Ingress Rules 추가**:

| Source CIDR | Protocol | Destination Port |
|---|---|---|
| `0.0.0.0/0` | TCP | `10443` |

80/443 은 이미 열려 있을 것이므로 건드리지 않는다.

> **왜 소스를 `0.0.0.0/0` 으로 두는가**: 셀룰러 IP 는 유동이고 통신사 CGNAT 뒤에 있어 고정할 수 없다. 접근 통제는 Squid 의 Basic 인증이 담당한다. IP 를 고정할 수 있다면 좁히는 편이 낫다.

---

## 2단계 — 서버 구성

```bash
# 인스턴스에 접속한 뒤 server/ 디렉터리를 올린다 (git clone 또는 scp)
cd server
chmod +x setup-squid.sh verify.sh uninstall.sh

sudo ./setup-squid.sh \
  --domain 4joy.is-a.dev \
  --user   edgeproxy
```

`--password` 를 생략하면 24자 난수를 생성해 마지막에 한 번 출력한다. **그 자리에서 저장할 것.**

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `--domain` | (필수) | Edge 가 접속할 호스트명. 기존 인증서가 이 이름을 커버해야 한다 |
| `--user` | (필수) | 프록시 Basic 인증 계정 |
| `--password` | 자동 생성 | |
| `--port` | `10443` | 80/443 을 지정하면 거부한다 (nginx 보호) |
| `--cert-name` | `--domain` 값 | certbot lineage 이름이 도메인과 다를 때 지정 |
| `--tls` | `auto` | `native`(Squid 가 TLS 종단) / `stunnel`(폴백) 강제 지정 |

스크립트가 수행하는 일:

| 단계 | 내용 |
|---|---|
| 포트 점유 검사 | `ss` 로 확인. 다른 프로세스가 쓰고 있으면 중단 (squid/stunnel 자신이면 재구성으로 진행) |
| **인증서 검증** | lineage 존재 · **SAN 커버리지**(와일드카드 포함) · 만료 여부. 없거나 안 맞으면 해결 명령을 출력하고 중단 |
| 패키지 | `squid`, `httpd-tools`, `policycoreutils-python-utils` (certbot 은 설치하지 않는다) |
| TLS 방식 판별 | `squid -v \| grep -- --with-openssl` 로 확인. 있으면 Squid 가 직접 TLS 종단, 없으면 `stunnel` 자동 폴백 |
| 배포 훅 | `renewal-hooks/deploy/90-proxy-tls.sh` — 갱신 시 `/etc/squid/tls` 로 복사 + SELinux 라벨 + `squid -k reconfigure`. **항상 exit 0** (아래 근거 참고) |
| 인증 | `htpasswd` 로 `/etc/squid/passwd` 생성 (`basic_ncsa_auth`) |
| squid.conf | 템플릿 치환 후 `squid -k parse` 로 문법 검증 |
| SELinux | `squid_connect_any=on`, `/etc/squid/tls` → `squid_conf_t`, `10443` → `squid_port_t` |
| 방화벽 | `firewalld` 와 `iptables` 양쪽에 `10443/tcp` 만 추가 (80/443 은 미변경) |
| 사후 확인 | nginx 가 여전히 살아 있는지 검사 |

멱등이므로 설정을 바꿔 다시 실행해도 안전하다.

### 자체 점검

```bash
sudo ./verify.sh --password '발급받은비밀번호'
```

7개 그룹을 검사한다: 서비스 상태, **nginx 무영향**(443 을 여전히 nginx 가 점유하는지), 프록시 리스닝 소켓, 인증서(프록시 복사본 + certbot 원본 양쪽의 만료·SAN 커버리지·동일성), TLS 핸드셰이크, 프록시 동작(**인증 없는 요청이 407로 거부되는지** = 오픈 프록시 방지, CONNECT 터널, 평문 HTTP 포워딩), SELinux.

> **만료 감시는 스스로 해야 한다.** Let's Encrypt 는 [만료 알림 메일 서비스를 2025-06-04 자로 종료](https://letsencrypt.org/2025/06/26/expiration-notification-service-has-ended)했다. FAIL 이 있으면 종료코드 1 이므로 cron 이 메일을 보낸다:
>
> ```bash
> echo '0 4 * * 1 root /opt/proxy/verify.sh' | sudo tee /etc/cron.d/proxy-verify
> ```

---

## 3단계 — Edge 확장 설치

1. `edge://extensions` 를 연다.
2. 좌측 하단 **개발자 모드**를 켠다.
3. **압축 해제된 항목 로드** → 이 저장소의 `extension/` 폴더를 선택한다.
4. 툴바에서 확장 아이콘 → **설정 열기**.
5. 입력:

   | 항목 | 값 |
   |---|---|
   | 스킴 | **HTTPS** |
   | 호스트 | `4joy.is-a.dev` |
   | 포트 | `10443` |
   | 사용자명 / 비밀번호 | 서버에서 만든 계정 |

6. **예외 목록은 그대로 둔다.** 기본값에 다음이 들어 있다:

   ```
   10.42.0.0/24      # tun2proxy 가상 NIC (USBTether)
   192.168.49.0/24   # 폰 Wi-Fi Direct GO — SOCKS5 1080 / HTTP 8282
   127.0.0.0/8
   169.254.0.0/16
   ```

   이걸 지우면 **테더링 경로 자체가 프록시로 향해 즉시 인터넷이 끊긴다.**

7. **저장** → 팝업에서 토글을 켠다.

> `output/windows-wifi.bat` 가 실행 중이어야 한다. 확장은 그 위에 얹히는 것이므로 tun2proxy 가 죽으면 프록시에도 도달할 수 없다.

---

## 4단계 — 검증

| 확인 항목 | 방법 | 기대 결과 |
|---|---|---|
| 출구 IP | 확장 팝업 | 인스턴스 공인 IP, 국가 = 인스턴스 리전 |
| 출구 IP (독립 확인) | `https://cloudflare.com/cdn-cgi/trace` 접속 | 위와 동일 |
| **블로그 정상** | `https://4joy.is-a.dev` 접속 | 평소와 동일 (프록시 경유로 들어가지만 정상 응답) |
| WebRTC 누출 | `https://browserleaks.com/webrtc` | 로컬/통신사 IP 가 노출되지 않음 |
| DNS 누출 | `https://browserleaks.com/dns` | 해석 서버가 해외(1.1.1.1 계열) |
| fail-closed | 서버에서 `sudo systemctl stop squid` 후 브라우징 | `ERR_PROXY_CONNECTION_FAILED` — **셀룰러로 새지 않는 것이 정상** |
| 예외 동작 | `http://192.168.49.1:8282` 접속 | 폰 프록시에 직접 도달 (프록시 미경유) |

팝업에 **출구 국가가 `KR` 로 표시되면 경고 문구가 뜬다.** 프록시를 실제로 경유하지 않고 있다는 신호다.

---

## 설계 결정과 근거

### 왜 경로(path)가 아니라 별도 포트인가

포워드 프록시는 URL 경로를 쓰지 않는다. 클라이언트가 보내는 것은 두 형태뿐이다:

```
CONNECT example.com:443 HTTP/1.1      ← authority-form. 경로가 아예 없다
GET http://example.com/foo HTTP/1.1   ← absolute-form. 경로는 '목적지'의 것
```

nginx 의 `location` 은 origin-form(`GET /foo`)의 경로에만 매칭되고, **stock nginx 는 `CONNECT` 메서드를 지원하지 않는다**(서드파티 `ngx_http_proxy_connect_module` 이 필요). 따라서 `/proxy/` 같은 위치에 걸 수 없고, 분기 기준은 **포트** 또는 **SNI** 뿐이다. 별도 포트가 nginx 재구성 없이 가장 낮은 위험으로 끝난다.

(443 을 공유하려면 nginx `stream` + `ssl_preread` 로 SNI 분기가 가능하지만, http 서버를 루프백으로 옮겨야 하고 블로그 접속 로그의 클라이언트 IP 가 `127.0.0.1` 이 되며, `ssl_preread` 모듈이 빌드에 포함돼 있는지 확인이 필요하다.)

### 배포 훅이 항상 exit 0 인 이유

이 서버의 certbot 은 **블로그의 인증서도 같이** 갱신한다. `renewal-hooks/deploy/` 의 훅이 0 이 아닌 코드로 끝나면 certbot 이 갱신 자체를 실패로 처리하므로, 프록시 쪽 문제가 블로그의 인증서 배포를 망가뜨릴 수 있다. 그래서 훅은 오류를 로그로만 남기고 항상 성공으로 끝난다. 실제 상태 판정은 `verify.sh` 가 맡고, 복사본과 원본을 `cmp` 로 비교해 훅이 조용히 실패했는지 잡아낸다.

파일명이 `90-` 인 것도 의도적이다. 기존 훅(nginx reload 등)보다 나중에 실행된다.

### CN 이 아니라 SAN 커버리지를 보는 이유

블로그와 인증서를 공유하므로 CN 은 `4joy.is-a.dev` 인데 프록시용 이름은 SAN 에만 있을 수 있다. `server/lib/cert.sh` 의 `cert_covers_domain()` 이 SAN 목록을 파싱하고, 와일드카드는 RFC 6125 대로 **라벨 한 개만** 매칭한다(`*.example.com` 은 `a.example.com` 을 커버하지만 `a.b.example.com` 은 아니다).

### PAC 스크립트를 쓰는 이유

Chrome/Edge 는 **GUI 나 `fixed_servers` 로 HTTPS 프록시를 지정할 수 없다.** PAC 스크립트의 `HTTPS host:port` 토큰으로만 가능하다 ([Squid wiki: Feature/HTTPS](https://wiki.squid-cache.org/Features/HTTPS)). 그래서 `chrome.proxy` 를 `pac_script` 모드로 쓴다.

### fail-closed — `; DIRECT` 폴백을 붙이지 않는다

PAC 의 반환값에 `DIRECT` 폴백을 넣으면 프록시 장애 시 **조용히** 셀룰러 IP 로 나간다. 우회 목적이 무너지고 사용자는 알아채지 못한다. 그래서:

- PAC 은 프록시 토큰만 반환한다.
- `pacScript.mandatory = true` 로 PAC 오류 시에도 direct 폴백을 금지한다.
- 프록시 도달 실패 시 알림만 띄우고 **라우팅을 자동으로 끄지 않는다.**

### PAC 안에서 DNS 조회를 하지 않는다

`dnsResolve()` / `isInNet(호스트명, ...)` 은 로컬 DNS 조회를 유발한다. 셀룰러 경유라 지연이 크고, 무엇보다 **통신사 DNS 에 목적지 호스트명이 노출**된다. 따라서 IP 판정은 리터럴 IPv4/IPv6 에 대해서만 직접 파싱해 수행한다.

### HTTPS 스킴이 필요한 이유 (SOCKS5 가 아닌)

| | HTTPS 프록시 | SOCKS5 |
|---|---|---|
| 사용자 인증 | 지원 (Basic over TLS) | **Chrome/Edge 미지원** |
| CONNECT 대상 호스트 | TLS 로 암호화 → 통신사 SNI/DPI 차단 우회 | 평문 노출 |
| 접근 통제 | 계정 기반 | 방화벽 IP 제한뿐 → 유동 셀룰러 IP 에 부적합 |

### WebRTC 방어가 이 환경에서 특히 중요한 이유

usb-tether 의 Wi-Fi 핫스팟 모드는 SOCKS5 UDP ASSOCIATE 를 지원한다. 즉 **UDP 가 실제로 셀룰러로 빠져나간다.** WebRTC 의 STUN 요청이 프록시를 우회해 폰의 통신사 IP 를 노출시킬 수 있다. `chrome.privacy.network.webRTCIPHandlingPolicy = 'disable_non_proxied_udp'` 로 막는다.

### stunnel 폴백을 둔 이유

Squid 의 `https_port` 는 바이너리가 `--with-openssl` 로 빌드돼야 동작한다. **Oracle Linux 8 의 squid 패키지 빌드 옵션은 마이너 버전에 따라 달라질 수 있어 확인 없이 단정할 수 없다.** 스크립트가 런타임에 `squid -v` 로 판별하고, 없으면 Squid 를 루프백 평문(3128)으로 돌리고 stunnel 이 10443 의 TLS 를 종단한다. stunnel 은 순수 TCP 래퍼이므로 `Proxy-Authorization` 헤더가 그대로 통과해 Basic 인증이 정상 동작한다.

### MV3 서비스 워커 대응

워커는 유휴 시 종료된다. 따라서

- 모든 리스너를 `background.js` **최상위에서 동기 등록**한다 (`onAuthRequired` 포함).
- 상태는 메모리 변수가 아니라 `storage.local`(설정) / `storage.session`(런타임)에 둔다.
- `onStartup` / `onInstalled` 에서 설정과 실제 프록시 상태를 재동기화한다.

### 자격증명 유출 방어

`onAuthRequired` 핸들러는 `details.isProxy === true` **이면서** `details.challenger` 의 host:port 가 설정된 프록시와 일치할 때만 자격증명을 넘긴다. origin 서버의 401 에는 절대 개입하지 않는다. 또 같은 요청이 2회 넘게 407 을 받으면 취소해 무한 재시도 루프를 끊는다.

---

## 테스트

```bash
npm test        # 또는 node --test
```

PAC 라우팅 판정 25건(테더링 대역이 `DIRECT` 로 빠지는지 포함), fail-closed 보장, DNS 조회 함수 미사용, 스킴 토큰 매핑, 설정 검증 로직을 검사한다.

---

## 트러블슈팅

| 증상 | 원인 / 조치 |
|---|---|
| 스크립트가 **메시지 없이 종료 코드 141** | `141 = 128 + SIGPIPE`. `set -o pipefail` 상태에서 파이프 오른쪽이 먼저 끝나면(`head -c`, `grep -q`, `awk '{exit}'`) 왼쪽이 SIGPIPE 로 죽고 `set -e` 가 스크립트를 조용히 죽인다. 현재 스크립트에서는 제거했고, 재발 시 ERR 트랩이 라인 번호와 원인을 출력한다 |
| `set: pipefail: invalid option name` 또는 `syntax error near unexpected token $'{\r'` | 파일이 **CRLF** 로 저장됐다. Windows 편집기에서 저장하면 발생한다. `sed -i 's/\r$//' server/*.sh server/lib/*.sh` 로 변환. 재발 방지는 저장소의 `.gitattributes`(`*.sh text eol=lf`)가 담당한다 |
| `/usr/bin/env: 'bash\r': No such file or directory` | 위와 같은 CRLF 문제 |
| `ERR_PROXY_CONNECTION_FAILED` | ① `windows-wifi.bat`(tun2proxy)가 죽었는지 확인 ② OCI Security List 에 `10443/tcp` 인그레스 ③ 서버에서 `sudo ./verify.sh` |
| 셋업 스크립트가 "인증서가 커버하지 않는다" 로 중단 | 출력된 SAN 목록을 보고 `--domain` 을 맞추거나, 안내된 `certbot certonly --cert-name ... --nginx` 로 이름을 추가 |
| 셋업 스크립트가 "포트가 이미 사용 중" 으로 중단 | `sudo ss -tlnp \| grep 10443` 으로 점유자 확인 후 `--port` 로 변경 |
| 프록시 인증 대화상자가 반복 | 비밀번호 불일치. 옵션에서 재입력. 서버에서 `sudo htpasswd -b /etc/squid/passwd <user> <pw>` 후 `sudo squid -k reconfigure` |
| 인증서 경고 | 확장에 IP 를 넣었거나 SAN 에 없는 이름을 넣었다. `sudo certbot certificates` 의 `Domains:` 와 일치시킬 것 |
| 갱신 후 프록시만 옛 인증서 | 훅이 조용히 실패했다. `sudo RENEWED_LINEAGE=/etc/letsencrypt/live/<name> /etc/letsencrypt/renewal-hooks/deploy/90-proxy-tls.sh` 로 수동 실행 후 로그 확인 |
| 블로그가 안 열린다 | 이 도구는 nginx 를 건드리지 않는다. `systemctl status nginx`, `nginx -t` 로 확인. `verify.sh` 의 2번 그룹도 이를 검사한다 |
| 팝업에 "다른 확장이 점유" | 프록시를 제어하는 다른 확장을 끈다. 프록시 설정은 프로필 전역이며 한 확장만 점유 가능 |
| 출구 국가가 `KR` | 프록시를 경유하지 않고 있다. 토글 상태와 `edge://net-export` 로 확인 |
| 특정 사이트만 차단/캡차 | 데이터센터 IP(ASN) 기반 차단. 프록시가 아니라 사이트 정책 문제다 |
| 체감 속도가 매우 느림 | PC→폰(Wi-Fi Direct)→셀룰러→해외 3홉 + 폰의 유저스페이스 릴레이 병목. 구조적 한계 |
| 서버에서 `SELinux AVC` 거부 | `sudo ausearch -m AVC -ts recent \| audit2why` |

로그 확인:

```bash
sudo journalctl -u squid -n 100 --no-pager
sudo journalctl -u stunnel -n 100 --no-pager   # 폴백 모드일 때
sudo tail -f /var/log/squid/cache.log
sudo grep proxy-tls-hook /var/log/letsencrypt/letsencrypt.log
```

Edge 쪽은 `edge://extensions` → 확장 카드의 **서비스 워커** 링크로 콘솔을 연다.

---

## 알려진 한계

- **Edge 프로필 안에서만 동작한다.** OS 의 다른 앱, 다른 브라우저는 그대로 셀룰러로 나간다.
- **자격증명이 평문 저장된다.** `chrome.storage.local` 은 암호화되지 않는다. 이 프록시 전용 계정을 쓰고 재사용하지 말 것.
- **UDP/QUIC 미지원.** HTTP 프록시는 TCP 전용이다. 프록시가 설정되면 Edge 는 QUIC 대신 TCP 를 쓰므로 실사용에는 문제가 없지만, WebRTC 등 UDP 기반 기능은 동작하지 않는다(누출 방어 측면에서는 의도된 결과다).
- **프록시 도메인 자체의 DNS 조회는 로컬에서 일어난다.** 그 한 번의 조회는 셀룰러 경로로 나가므로, 통신사가 그 도메인을 차단하지 않는 이름을 써야 한다.
- **비표준 포트(10443)** 는 443 보다 눈에 띈다. 통신사 DPI 가 포트 기반으로 뭔가 한다면 443 공유(SNI 분기) 방식을 검토해야 한다.
- **데이터 요금.** 모든 Edge 트래픽이 USIM 데이터로 계량된다.
- **Secure DNS(DoH)** 는 확장이 끌 수 없다. 프록시가 설정되면 목적지 이름은 서버가 해석하므로 실질적 영향은 없다.
- **블로그 트래픽도 프록시를 경유한다.** 자기 서버에 해외를 한 바퀴 돌아 접속하는 셈이라 느리다. 신경 쓰이면 예외 목록에 `4joy.is-a.dev` 를 추가하면 되지만, 그러면 그 도메인 접속만 통신사에 노출된다.

### 추측으로 표시해야 할 부분

- OL8 squid 패키지의 OpenSSL 빌드 여부 → 런타임 판별로 처리했다.
- stunnel 폴백 모드에서 `stunnel_t` 도메인이 10443(`http_port_t`)을 bind 할 수 있는지는 정책 버전에 따라 다를 수 있다. `verify.sh` 가 최근 AVC 거부를 검사하므로, 걸리면 `audit2allow` 로 모듈을 만들거나 `--tls native` 로 전환할 것.
- 셀룰러 망의 10443 아웃바운드는 열려 있다고 보는 것이 합리적이지만, 통신사·APN 별 정책 차이는 실측이 필요하다.
- tun2proxy 가 TCP 를 유저스페이스에서 종단·재생성하므로 IP 캡슐화가 없어 MTU 파편화 문제는 크지 않을 것으로 **추측**한다. 대용량 전송에서 이상이 있으면 TUN MTU 조정을 검토할 것.

---

## 법적 고려

지역 차단 우회는 관할 지역 법률과 대상 사이트 약관에 저촉될 수 있다. 테더링 자체도 통신사 약관 문제가 될 수 있다(usb-tether README 참고). 이 도구는 학습·실험 목적이며, 저자는 변호사가 아니다.

---

## 출처

- [Squid Web Cache wiki — Feature: HTTPS](https://wiki.squid-cache.org/Features/HTTPS) — `https_port` 포워드 프록시 지원, 브라우저가 PAC 로만 HTTPS 프록시를 지정할 수 있다는 점
- [Let's Encrypt — Expiration Notification Service Has Ended](https://letsencrypt.org/2025/06/26/expiration-notification-service-has-ended) — 만료 알림 메일 종료(2025-06-04)
- [Oracle Linux 8: Configuring the Firewall](https://docs.oracle.com/en/operating-systems/oracle-linux/8/firewall/OL8-FIREWALL.pdf) — firewalld 기본 활성 및 포트 개방
- [Opening up port 80 and 443 for Oracle Cloud servers](https://dev.to/armiedema/opening-up-port-80-and-443-for-oracle-cloud-servers-j35) — OCI 는 Security List/NSG 와 OS 방화벽이 이중으로 존재
- [nginx — ngx_stream_ssl_preread_module](https://nginx.org/en/docs/stream/ngx_stream_ssl_preread_module.html) — SNI 분기 대안 (기본 빌드에 미포함)
