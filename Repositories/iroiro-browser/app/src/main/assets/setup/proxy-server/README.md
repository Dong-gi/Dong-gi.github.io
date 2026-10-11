# Web 프록시 서버 구성

Web 이 쓰는 프록시 — **TLS 로 감싼 HTTP 포워드 프록시**(Squid, Basic 인증) — 를 Oracle Linux 8 서버에 세운다.
nginx·certbot 이 이미 도는 서버에 **별도 포트(기본 10443)** 로 얹고, nginx 와 인증서 갱신은 건드리지 않는다.

예시의 `proxy.example.com`·`myproxyuser` 는 자기 값으로 바꾼다.

```
server/setup-squid.sh   구성 — 여러 번 돌려도 된다
server/verify.sh        자체 점검
server/uninstall.sh     되돌리기
server/lib/·conf/       함수·설정 템플릿
```

## 전제 조건

- certbot 이 인증서를 자동 갱신하고 있고, 프록시로 쓸 이름이 그 인증서에 있다(`sudo certbot certificates` 의 `Domains:`).
  **이 도구는 인증서를 발급하지 않는다** — 기존 것을 재사용하고, 갱신 때 복사하는 훅만 더한다.
- 새 이름을 쓰려면 인증서에 더한다. 기존 이름을 **모두** 다시 적는다(빠진 이름은 인증서에서 사라진다):
  `sudo certbot certonly --cert-name <lineage> --nginx -d <기존 이름들> -d proxy.example.com`
- nginx 가 443·80 을 쓰고 있어도 된다 — 그대로 둔다.

## 1단계 — OCI 인그레스 규칙

셸에서 열 수 없으니 먼저 한다. OCI 콘솔 → Networking → VCN → Subnet → Security List(또는 인스턴스의 NSG) → Ingress Rules 추가:

| Source CIDR | Protocol | Destination Port |
|---|---|---|
| `0.0.0.0/0` | TCP | `10443` |

폰의 IP 는 바뀌므로 소스를 좁히지 않는다 — 접근은 Basic 인증이 막는다.

## 2단계 — 서버 구성

zip 을 서버에 올려(scp 등) 푼다. **zip 에는 실행 권한이 담기지 않으므로 스크립트는 `bash` 로 돌린다.**

```bash
unzip web-proxy-server.zip            # unzip 이 없으면: sudo dnf install -y unzip
cd web-proxy-server/server
sudo bash setup-squid.sh --domain proxy.example.com --user myproxyuser
```

`--password` 를 빼면 24자 난수를 만들어 **끝에 한 번만** 보인다 — 그 자리에서 적어 둔다.

| 옵션 | 기본값 | |
|---|---|---|
| `--domain` | (필수) | Web 이 붙을 이름 = 인증서에 든 이름 |
| `--user` | (필수) | Basic 인증 계정 |
| `--password` | 자동 생성 | |
| `--port` | `10443` | 80·443 은 거부한다(nginx) |
| `--cert-name` | `--domain` 값 | certbot lineage 이름이 다를 때 |
| `--tls` | `auto` | `native`(Squid 가 TLS) · `stunnel`(폴백) 강제 |

하는 일: 포트·인증서(SAN·만료) 검사 → 패키지(squid·httpd-tools) → TLS 방식(Squid 에 OpenSSL 이 없으면 stunnel) → 갱신 훅
(`/etc/letsencrypt/renewal-hooks/deploy/90-proxy-tls.sh`) → 계정 → squid.conf → SELinux·방화벽(firewalld·iptables 에 10443/tcp) → 기동 → nginx 확인.

### 자체 점검 — verify.sh

```bash
sudo bash verify.sh --password '<비밀번호>'
```

서비스·nginx·리스닝 소켓·인증서(복사본과 원본)·TLS 핸드셰이크·프록시 동작(인증 없으면 407·CONNECT·평문 HTTP)·SELinux 를 본다.
FAIL 이 있으면 종료 코드 1. Let's Encrypt 는 만료 알림 메일을 보내지 않으니(2025-06-04 종료) 주기적으로 돌린다. 예:

```bash
sudo cp -r . /opt/proxy
echo '0 4 * * 1 root bash /opt/proxy/verify.sh' | sudo tee /etc/cron.d/proxy-verify
```

### 되돌리기 — uninstall.sh

```bash
sudo bash uninstall.sh
```

squid·stunnel·설정·계정·TLS 복사본·훅·SELinux·방화벽 포트를 되돌린다. nginx 와 `/etc/letsencrypt` 의 인증서는 건드리지 않는다.
패키지·OCI 인그레스 규칙·로그는 남는다.

## 3단계 — Web 에 넣기

Web › 설정 › 프록시:

| 칸 | 값 |
|---|---|
| 호스트 | `--domain` 값 — IP 가 아니라 인증서의 이름 |
| 포트 | `10443`(또는 `--port`) |
| 사용자·비밀번호 | 서버에서 만든 계정 |

저장하고 브라우저 화면 왼쪽 위 스위치를 켠다. 연결 상태에 서버 쪽 출구 IP·나라가 보이면 된다(‘출구가 한국입니다’ 면 프록시를 거치지 않는 것이다).

## 4단계 — 검증

| 무엇 | 방법 | 기대 |
|---|---|---|
| 출구 | Web 의 연결 상태 | 서버의 공인 IP·리전의 나라 |
| fail-closed | 서버에서 `sudo systemctl stop squid` 뒤 페이지 열기 | ‘프록시에 닿지 못합니다’ — 직접 연결로 새지 않는다. 끝나면 `sudo systemctl start squid` |
| 기존 사이트 | nginx 의 사이트 열기 | 평소와 같다 |

## 트러블슈팅

| 증상 | 조치 |
|---|---|
| `set: pipefail: invalid option name`, `$'\r'`, `bash\r` 오류 | 파일이 CRLF 로 바뀌었다(Windows 에서 열어 저장). server 폴더에서 `sed -i 's/\r$//' *.sh lib/*.sh conf/*.tmpl` |
| `sudo: ./setup-squid.sh: command not found` | 실행 권한이 없다 — `sudo bash setup-squid.sh …` |
| 메시지 없이 종료 코드 141 | SIGPIPE — ERR 트랩이 줄 번호를 찍는다. `bash -x` 로 다시 돌려 본다 |
| ‘인증서가 … 커버하지 않는다’ | 보인 SAN 에 맞게 `--domain` 을 고치거나, 안내된 `certbot certonly` 로 이름을 더한다 |
| ‘포트 … 가 이미 사용 중이다’ | `sudo ss -tlnp \| grep 10443` 로 확인하고 `--port` 를 바꾼다 |
| Web: ‘프록시에 닿지 못합니다’ | OCI 인그레스(1단계) → 서버에서 `sudo bash verify.sh` |
| Web: ‘프록시가 계정을 거절했습니다’ | 비밀번호 확인. 서버에서 `sudo htpasswd -b /etc/squid/passwd <계정> <비밀번호>` 뒤 `sudo squid -k reconfigure` |
| 인증서 오류 | 호스트에 IP 나 SAN 에 없는 이름을 넣었다 |
| 갱신 뒤 프록시만 옛 인증서 | 훅은 nginx 의 갱신을 지키려고 실패해도 0 으로 끝난다. `sudo RENEWED_LINEAGE=/etc/letsencrypt/live/<lineage> /etc/letsencrypt/renewal-hooks/deploy/90-proxy-tls.sh` 로 다시 돌리고 로그를 본다 |
| 특정 사이트만 차단·캡차 | 데이터센터 IP 차단 — 사이트의 정책이다 |
| SELinux AVC 거부 | `sudo ausearch -m AVC -ts recent \| audit2why` |

로그:

```bash
sudo journalctl -u squid -n 100 --no-pager
sudo journalctl -u stunnel -n 100 --no-pager      # stunnel 폴백일 때
sudo tail -f /var/log/stunnel/squid-tls.log        # stunnel 폴백일 때
sudo tail -f /var/log/squid/cache.log
sudo grep proxy-tls-hook /var/log/letsencrypt/letsencrypt.log
```

## 알려진 한계

- 프록시 이름의 DNS 조회는 폰의 망(통신사)에서 일어난다 — 통신사가 막지 않는 이름을 쓴다.
- 비표준 포트(10443)는 443 보다 눈에 띈다. 443 을 nginx 와 함께 쓰려면 SNI 분기(`ssl_preread`)가 필요하다 — 이 도구는 하지 않는다.
- HTTP 프록시는 TCP 만 지난다(UDP·QUIC 은 안 된다).
- 확인하지 않은 것: stunnel 폴백에서 SELinux 가 10443 bind 를 막을 수 있다(verify.sh 가 AVC 를 본다 — 걸리면 `--tls native` 나 `audit2allow`).
  셀룰러의 10443 아웃바운드는 통신사·APN 마다 다를 수 있다.

## 법적 고려

지역 차단 우회는 관할 지역의 법률과 사이트 약관에 저촉될 수 있다. 학습·실험용이며, 이 글은 법률 자문이 아니다.

## 출처

- [Squid wiki — Feature: HTTPS](https://wiki.squid-cache.org/Features/HTTPS) — `https_port` 포워드 프록시
- [Let's Encrypt — Expiration Notification Service Has Ended](https://letsencrypt.org/2025/06/26/expiration-notification-service-has-ended)
- [Oracle Linux 8: Configuring the Firewall](https://docs.oracle.com/en/operating-systems/oracle-linux/8/firewall/OL8-FIREWALL.pdf)
- [Opening up port 80 and 443 for Oracle Cloud servers](https://dev.to/armiedema/opening-up-port-80-and-443-for-oracle-cloud-servers-j35) — OCI 는 Security List/NSG 와 OS 방화벽이 따로 있다
- [nginx — ngx_stream_ssl_preread_module](https://nginx.org/en/docs/stream/ngx_stream_ssl_preread_module.html) — SNI 분기
