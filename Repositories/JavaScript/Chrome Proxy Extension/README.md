# Remote Proxy Router

Windows Edge 의 모든 트래픽을 해외 클라우드 인스턴스(Oracle Linux 8)의 **TLS 로 감싼 HTTP 포워드 프록시**로 라우팅하는 MV3 확장 + 서버 구성 도구.

```
[Edge] ──PAC "HTTPS proxy.example.com:443"──┐
                                            │
        (이 TCP 연결 자체가 아래 경로로 나간다)
                                            ▼
   WinTun "USBTether" → tun2proxy → 폰 SOCKS5 192.168.49.1:1080
                                  → Android TCP/IP 스택 → 셀룰러
                                  → 해외 인스턴스 :443 → 목적지
```

`tun2proxy` 는 IP 계층(기본 라우트)에서, 확장은 L7 에서 동작하므로 서로 간섭하지 않는다. usb-tether 구성에서 시스템 VPN(WireGuard 등)을 쓰지 않는 이유가 이것이다 — 기본 라우트를 두고 충돌한다.

---

## 목차

1. [디렉터리 구조](#디렉터리-구조)
2. [1단계 — 서버 준비 (OCI 콘솔)](#1단계--서버-준비-oci-콘솔)
3. [2단계 — DNS](#2단계--dns)
4. [3단계 — 서버 구성](#3단계--서버-구성)
5. [4단계 — Edge 확장 설치](#4단계--edge-확장-설치)
6. [5단계 — 검증](#5단계--검증)
7. [설계 결정과 근거](#설계-결정과-근거)
8. [트러블슈팅](#트러블슈팅)
9. [알려진 한계](#알려진-한계)

---

## 디렉터리 구조

```
Chrome Proxy Extension/
├── README.md
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
└── server/                           # Oracle Linux 8 구성 도구
    ├── setup-squid.sh                # 메인 셋업 (멱등)
    ├── verify.sh                     # 서버 자체 점검
    ├── uninstall.sh                  # 원복
    └── conf/
        ├── squid-native-tls.conf.tmpl # Squid 가 TLS 종단
        ├── squid-plain.conf.tmpl      # stunnel 폴백용 (루프백 전용)
        ├── stunnel-squid.conf.tmpl
        └── certbot-deploy-hook.sh.tmpl
```

---

## 1단계 — 서버 준비 (OCI 콘솔)

셸에서 열 수 없는 것들이다. **먼저 해야 한다.**

1. Oracle Linux 8 인스턴스를 원하는 리전에 생성한다. 공인 IP(퍼블릭 서브넷) 필요.
2. OCI 콘솔 → **Networking → VCN → Subnet → Security List**(또는 인스턴스의 **NSG**) → **Ingress Rules 추가**:

   | Source CIDR | Protocol | Destination Port |
   |---|---|---|
   | `0.0.0.0/0` | TCP | `443` |
   | `0.0.0.0/0` | TCP | `80` |

   `80` 은 Let's Encrypt `http-01` 챌린지용이다. 인증서 갱신은 자동이므로 계속 열어둔다.

> **왜 소스를 `0.0.0.0/0` 으로 두는가**: 셀룰러 IP 는 유동이고 통신사 CGNAT 뒤에 있어 고정할 수 없다. 대신 접근 통제는 Squid 의 Basic 인증이 담당한다. IP 를 고정할 수 있다면 좁히는 편이 낫다.

---

## 2단계 — DNS

`proxy.example.com` A 레코드를 인스턴스의 공인 IP 로 지정한다.

**IP 가 아니라 도메인이 반드시 필요하다.** Edge 가 프록시의 TLS 인증서를 검증하고, Let's Encrypt 는 IP 에 인증서를 발급하지 않기 때문이다.

전파 확인:

```bash
nslookup proxy.example.com
```

---

## 3단계 — 서버 구성

```bash
# 인스턴스에 접속한 뒤
git clone <이 저장소>            # 또는 server/ 디렉터리만 scp
cd "Chrome Proxy Extension/server"
chmod +x setup-squid.sh verify.sh uninstall.sh

sudo ./setup-squid.sh \
  --domain proxy.example.com \
  --email  you@example.com \
  --user   edgeproxy
```

`--password` 를 생략하면 24자 난수를 생성해 마지막에 한 번 출력한다. **그 자리에서 저장할 것.**

스크립트가 수행하는 일:

| 단계 | 내용 |
|---|---|
| 패키지 | EPEL 활성화 → `squid`, `httpd-tools`, `certbot`, `policycoreutils-python-utils` |
| TLS 방식 판별 | `squid -v \| grep -- --with-openssl` 로 확인. 있으면 Squid 가 직접 TLS 종단, 없으면 `stunnel` 자동 폴백 |
| DNS 사전 검사 | A 레코드가 이 서버를 가리키는지 확인 (인증서 발급 실패를 미리 잡는다) |
| 인증서 | `certbot certonly --standalone`, ECDSA 키 |
| 배포 훅 | `/etc/letsencrypt/renewal-hooks/deploy/10-squid-tls.sh` — 갱신 시 `/etc/squid/tls` 로 복사 + SELinux 라벨 + `squid -k reconfigure` |
| 인증 | `htpasswd` 로 `/etc/squid/passwd` 생성 (`basic_ncsa_auth`) |
| squid.conf | 템플릿 치환 후 `squid -k parse` 로 문법 검증 |
| SELinux | `squid_connect_any=on`, `/etc/squid/tls` → `squid_conf_t`, 리스닝 포트 라벨링 |
| 방화벽 | `firewalld` 와 `iptables` 양쪽 처리 (OCI 이미지가 섞여 있다) |

멱등이므로 설정을 바꿔 다시 실행해도 안전하다.

### 자체 점검

```bash
sudo ./verify.sh --password '발급받은비밀번호'
```

서비스 상태, 리스닝 소켓, 인증서 만료/CN, TLS 핸드셰이크, **인증 없는 요청이 407로 거부되는지**(오픈 프록시 방지), CONNECT 터널, 평문 HTTP 포워딩, SELinux 거부 로그를 검사하고 PASS/FAIL 을 집계한다.

---

## 4단계 — Edge 확장 설치

1. `edge://extensions` 를 연다.
2. 좌측 하단 **개발자 모드**를 켠다.
3. **압축 해제된 항목 로드** → 이 저장소의 `extension/` 폴더를 선택한다.
4. 툴바에서 확장 아이콘 → **설정 열기**.
5. 입력:

   | 항목 | 값 |
   |---|---|
   | 스킴 | **HTTPS** |
   | 호스트 | `proxy.example.com` |
   | 포트 | `443` |
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

## 5단계 — 검증

| 확인 항목 | 방법 | 기대 결과 |
|---|---|---|
| 출구 IP | 확장 팝업 | 인스턴스 공인 IP, 국가 = 인스턴스 리전 |
| 출구 IP (독립 확인) | `https://cloudflare.com/cdn-cgi/trace` 접속 | 위와 동일 |
| WebRTC 누출 | `https://browserleaks.com/webrtc` | 로컬/통신사 IP 가 노출되지 않음 |
| DNS 누출 | `https://browserleaks.com/dns` | 해석 서버가 해외(1.1.1.1 계열) |
| fail-closed | 서버에서 `sudo systemctl stop squid` 후 브라우징 | `ERR_PROXY_CONNECTION_FAILED` — **셀룰러로 새지 않는 것이 정상** |
| 예외 동작 | `http://192.168.49.1:8282` 접속 | 폰 프록시에 직접 도달 (프록시 미경유) |

팝업에 **출구 국가가 `KR` 로 표시되면 경고 문구가 뜬다.** 프록시를 실제로 경유하지 않고 있다는 신호다.

---

## 설계 결정과 근거

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

Squid 의 `https_port` 는 바이너리가 `--with-openssl` 로 빌드돼야 동작한다. **Oracle Linux 8 의 squid 패키지 빌드 옵션은 마이너 버전에 따라 달라질 수 있어 확인 없이 단정할 수 없다.** 스크립트가 런타임에 `squid -v` 로 판별하고, 없으면 Squid 를 루프백 평문으로 돌리고 stunnel 이 443의 TLS 를 종단한다. stunnel 은 순수 TCP 래퍼이므로 `Proxy-Authorization` 헤더가 그대로 통과해 Basic 인증이 정상 동작한다.

### MV3 서비스 워커 대응

워커는 유휴 시 종료된다. 따라서

- 모든 리스너를 `background.js` **최상위에서 동기 등록**한다 (`onAuthRequired` 포함).
- 상태는 메모리 변수가 아니라 `storage.local`(설정) / `storage.session`(런타임)에 둔다.
- `onStartup` / `onInstalled` 에서 설정과 실제 프록시 상태를 재동기화한다.

### 자격증명 유출 방어

`onAuthRequired` 핸들러는 `details.isProxy === true` **이면서** `details.challenger` 의 host:port 가 설정된 프록시와 일치할 때만 자격증명을 넘긴다. origin 서버의 401 에는 절대 개입하지 않는다. 또 같은 요청이 2회 넘게 407 을 받으면 취소해 무한 재시도 루프를 끊는다.

---

## 트러블슈팅

| 증상 | 원인 / 조치 |
|---|---|
| `ERR_PROXY_CONNECTION_FAILED` | ① `windows-wifi.bat`(tun2proxy)가 죽었는지 확인 ② OCI Security List 인그레스 규칙 ③ 서버에서 `sudo ./verify.sh` |
| 프록시 인증 대화상자가 반복 | 비밀번호 불일치. 옵션에서 재입력. 서버에서 `sudo htpasswd -b /etc/squid/passwd <user> <pw>` 후 `sudo squid -k reconfigure` |
| 인증서 경고 | 확장에 IP 를 넣었거나 도메인 불일치. 인증서 CN 과 동일한 도메인을 입력해야 한다 |
| 팝업에 "다른 확장이 점유" | 프록시를 제어하는 다른 확장을 끈다. 프록시 설정은 프로필 전역이며 한 확장만 점유 가능 |
| 출구 국가가 `KR` | 프록시를 경유하지 않고 있다. 토글 상태와 `edge://net-export` 로 확인 |
| 특정 사이트만 차단/캡차 | 데이터센터 IP(ASN) 기반 차단. 프록시가 아니라 사이트 정책 문제다 |
| 체감 속도가 매우 느림 | PC→폰(Wi-Fi Direct)→셀룰러→해외 3홉 + 폰의 유저스페이스 릴레이 병목. 구조적 한계 |
| 서버에서 `SELinux AVC` 거부 | `sudo ausearch -m AVC -ts recent \| audit2why` |
| 인증서 갱신 실패 | 80/tcp 가 OCI 와 OS 방화벽 양쪽에서 열려 있어야 한다. `sudo certbot renew --dry-run` |

로그 확인:

```bash
sudo journalctl -u squid -n 100 --no-pager
sudo journalctl -u stunnel -n 100 --no-pager   # 폴백 모드일 때
sudo tail -f /var/log/squid/cache.log
```

Edge 쪽은 `edge://extensions` → 확장 카드의 **서비스 워커** 링크로 콘솔을 연다.

---

## 알려진 한계

- **Edge 프로필 안에서만 동작한다.** OS 의 다른 앱, 다른 브라우저는 그대로 셀룰러로 나간다.
- **자격증명이 평문 저장된다.** `chrome.storage.local` 은 암호화되지 않는다. 이 프록시 전용 계정을 쓰고 재사용하지 말 것.
- **UDP/QUIC 미지원.** HTTP 프록시는 TCP 전용이다. 프록시가 설정되면 Edge 는 QUIC 대신 TCP 를 쓰므로 실사용에는 문제가 없지만, WebRTC 등 UDP 기반 기능은 동작하지 않는다(이건 누출 방어 측면에서는 의도된 결과다).
- **프록시 도메인 자체의 DNS 조회는 로컬에서 일어난다.** 그 한 번의 조회는 셀룰러 경로로 나가므로, 통신사가 그 도메인을 차단하지 않는 이름을 써야 한다.
- **데이터 요금.** 모든 Edge 트래픽이 USIM 데이터로 계량된다.
- **Secure DNS(DoH)** 는 확장이 끌 수 없다. 프록시가 설정되면 목적지 이름은 서버가 해석하므로 실질적 영향은 없다.

### 추측으로 표시해야 할 부분

- OL8 squid 패키지의 OpenSSL 빌드 여부 → 런타임 판별로 처리했다.
- stunnel 폴백 모드에서 `stunnel_t` 도메인이 443(`http_port_t`)을 bind 할 수 있는지는 정책 버전에 따라 다를 수 있다. `verify.sh` 가 최근 AVC 거부를 검사하므로, 걸리면 `audit2allow` 로 모듈을 만들거나 `--tls native` 로 전환할 것.
- 셀룰러 망의 443 아웃바운드는 열려 있다고 보는 것이 합리적이지만, 통신사·APN 별 정책 차이는 실측이 필요하다.
- tun2proxy 가 TCP 를 유저스페이스에서 종단·재생성하므로 IP 캡슐화가 없어 MTU 파편화 문제는 크지 않을 것으로 **추측**한다. 대용량 전송에서 이상이 있으면 TUN MTU 조정을 검토할 것.

---

## 법적 고려

지역 차단 우회는 관할 지역 법률과 대상 사이트 약관에 저촉될 수 있다. 테더링 자체도 통신사 약관 문제가 될 수 있다(usb-tether README 참고). 이 도구는 학습·실험 목적이며, 저자는 변호사가 아니다.

---

## 출처

- [Squid Web Cache wiki — Feature: HTTPS](https://wiki.squid-cache.org/Features/HTTPS) — `https_port` 포워드 프록시 지원, 브라우저가 PAC 로만 HTTPS 프록시를 지정할 수 있다는 점
- [Oracle Linux 8: Configuring the Firewall](https://docs.oracle.com/en/operating-systems/oracle-linux/8/firewall/OL8-FIREWALL.pdf) — firewalld 기본 활성 및 포트 개방
- [Opening up port 80 and 443 for Oracle Cloud servers](https://dev.to/armiedema/opening-up-port-80-and-443-for-oracle-cloud-servers-j35) — OCI 는 Security List/NSG 와 OS 방화벽이 이중으로 존재
