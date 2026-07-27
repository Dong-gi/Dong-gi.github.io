#!/usr/bin/env bash
#
# setup-squid.sh — Oracle Linux 8 에 "TLS로 감싼 HTTP 포워드 프록시"를 구성한다.
#
#   [Edge] --TLS--> :443 (Squid https_port | stunnel) --> Squid --> Internet
#
# 특징
#   * Let's Encrypt 인증서 자동 발급 + 갱신 훅 설치
#   * Basic 인증 (Chrome/Edge 는 SOCKS5 인증을 지원하지 않으므로 HTTP 계열 필수)
#   * Squid 빌드에 OpenSSL 이 없으면 stunnel 로 자동 폴백
#   * SELinux / firewalld / iptables 를 모두 처리 (OCI 이미지는 배포판마다 다름)
#   * 멱등(idempotent): 여러 번 실행해도 안전
#
# 사용법
#   sudo ./setup-squid.sh --domain proxy.example.com --email me@example.com \
#                         --user myproxyuser [--password '...'] [--port 443] \
#                         [--tls auto|native|stunnel]
#
# 주의: OCI 콘솔의 VCN Security List / NSG 인그레스 규칙은 셸에서 열 수 없다.
#       README.md 의 1단계를 먼저 수행할 것.
#
set -euo pipefail

#=============================================================================
# 상수
#=============================================================================
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly CONF_DIR="${SCRIPT_DIR}/conf"

readonly SQUID_CONF="/etc/squid/squid.conf"
readonly SQUID_CONF_BACKUP="/etc/squid/squid.conf.orig"
readonly SQUID_PASSWD="/etc/squid/passwd"
readonly TLS_DIR="/etc/squid/tls"
readonly STUNNEL_CONF="/etc/stunnel/squid-tls.conf"
readonly DEPLOY_HOOK="/etc/letsencrypt/renewal-hooks/deploy/10-squid-tls.sh"
readonly STATE_FILE="/etc/squid/.setup-state"

# stunnel 폴백 시 Squid 가 로컬에서만 듣는 평문 포트
readonly SQUID_PLAIN_PORT=3128

readonly C_INFO=$'\033[1;34m'
readonly C_OK=$'\033[1;32m'
readonly C_WARN=$'\033[1;33m'
readonly C_ERR=$'\033[1;31m'
readonly C_OFF=$'\033[0m'

#=============================================================================
# 로깅
#=============================================================================
log()  { printf '%s[ .. ]%s %s\n' "$C_INFO" "$C_OFF" "$*"; }
ok()   { printf '%s[ OK ]%s %s\n' "$C_OK"   "$C_OFF" "$*"; }
warn() { printf '%s[WARN]%s %s\n' "$C_WARN" "$C_OFF" "$*" >&2; }
die()  { printf '%s[FAIL]%s %s\n' "$C_ERR"  "$C_OFF" "$*" >&2; exit 1; }

#=============================================================================
# 인자 파싱
#=============================================================================
DOMAIN=""
EMAIL=""
PROXY_USER=""
PROXY_PASSWORD=""
PROXY_PORT="443"
TLS_MODE="auto"

usage() {
    sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --domain)   DOMAIN="${2:?--domain 값 누락}";         shift 2 ;;
            --email)    EMAIL="${2:?--email 값 누락}";           shift 2 ;;
            --user)     PROXY_USER="${2:?--user 값 누락}";       shift 2 ;;
            --password) PROXY_PASSWORD="${2:?--password 값 누락}"; shift 2 ;;
            --port)     PROXY_PORT="${2:?--port 값 누락}";       shift 2 ;;
            --tls)      TLS_MODE="${2:?--tls 값 누락}";          shift 2 ;;
            -h|--help)  usage 0 ;;
            *)          die "알 수 없는 옵션: $1 (--help 참고)" ;;
        esac
    done

    [[ -n "$DOMAIN" ]]     || die "--domain 은 필수다 (인증서 발급에 필요)"
    [[ -n "$EMAIL" ]]      || die "--email 은 필수다 (Let's Encrypt 만료 알림)"
    [[ -n "$PROXY_USER" ]] || die "--user 는 필수다 (프록시 Basic 인증 계정)"

    [[ "$PROXY_PORT" =~ ^[0-9]+$ ]] && (( PROXY_PORT >= 1 && PROXY_PORT <= 65535 )) \
        || die "--port 는 1-65535 범위의 숫자여야 한다: $PROXY_PORT"

    case "$TLS_MODE" in
        auto|native|stunnel) ;;
        *) die "--tls 는 auto|native|stunnel 중 하나여야 한다: $TLS_MODE" ;;
    esac

    if [[ -z "$PROXY_PASSWORD" ]]; then
        PROXY_PASSWORD="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)"
        GENERATED_PASSWORD=1
    else
        GENERATED_PASSWORD=0
    fi
}

#=============================================================================
# 사전 점검
#=============================================================================
require_root() {
    [[ "$(id -u)" -eq 0 ]] || die "root 권한이 필요하다: sudo $0 ..."
}

require_oracle_linux8() {
    local id version
    [[ -r /etc/os-release ]] || die "/etc/os-release 를 읽을 수 없다"
    # shellcheck disable=SC1091
    id="$(. /etc/os-release && echo "${ID:-}")"
    version="$(. /etc/os-release && echo "${VERSION_ID:-}")"

    if [[ "$id" != "ol" ]]; then
        warn "Oracle Linux 가 아니다 (ID=$id). RHEL 계열이면 대체로 동작하지만 검증되지 않았다."
    elif [[ "${version%%.*}" != "8" ]]; then
        warn "Oracle Linux 8 이 아니다 (VERSION_ID=$version). 패키지 경로가 다를 수 있다."
    else
        ok "Oracle Linux ${version} 확인"
    fi
}

# 도메인이 이 서버를 가리키는지 확인한다. 인증서 발급 실패를 미리 잡아낸다.
verify_dns() {
    local resolved public
    resolved="$(getent ahostsv4 "$DOMAIN" | awk 'NR==1{print $1}')" || true
    public="$(curl -fsS --max-time 10 https://api.ipify.org 2>/dev/null)" || true

    if [[ -z "$resolved" ]]; then
        die "$DOMAIN 의 A 레코드를 해석할 수 없다. DNS 를 먼저 설정하라."
    fi
    if [[ -z "$public" ]]; then
        warn "공인 IP 를 확인할 수 없어 DNS 일치 검사를 생략한다 (A=$resolved)."
        return
    fi
    if [[ "$resolved" != "$public" ]]; then
        warn "DNS 불일치: $DOMAIN → $resolved, 이 서버의 공인 IP → $public"
        warn "Let's Encrypt http-01 챌린지가 실패할 수 있다. 계속 진행한다."
    else
        ok "DNS 확인: $DOMAIN → $resolved"
    fi
}

#=============================================================================
# 패키지
#=============================================================================
install_packages() {
    log "저장소 및 패키지 설치"

    # certbot 은 EPEL 에 있다.
    if ! dnf -q repolist enabled 2>/dev/null | grep -q epel; then
        dnf install -y oracle-epel-release-el8 >/dev/null 2>&1 \
            || dnf install -y epel-release >/dev/null 2>&1 \
            || warn "EPEL 저장소 활성화에 실패했다. certbot 설치가 실패할 수 있다."
    fi

    local pkgs=(squid httpd-tools certbot policycoreutils-python-utils)
    dnf install -y "${pkgs[@]}" >/dev/null || die "패키지 설치 실패: ${pkgs[*]}"
    ok "설치 완료: ${pkgs[*]}"
}

# Squid 바이너리가 OpenSSL 지원으로 빌드됐는지 판별한다.
# OL8 의 squid 패키지 빌드 옵션은 마이너 버전에 따라 달라질 수 있으므로 런타임에 확인한다.
squid_has_openssl() {
    squid -v 2>/dev/null | grep -q -- '--with-openssl'
}

resolve_tls_mode() {
    case "$TLS_MODE" in
        native)
            squid_has_openssl \
                || die "squid 가 --with-openssl 없이 빌드되어 native TLS 를 쓸 수 없다. --tls stunnel 을 사용하라."
            ;;
        stunnel)
            ;;
        auto)
            if squid_has_openssl; then
                TLS_MODE="native"
            else
                TLS_MODE="stunnel"
                warn "squid 에 OpenSSL 지원이 없어 stunnel 폴백을 선택했다."
            fi
            ;;
    esac

    if [[ "$TLS_MODE" == "stunnel" ]]; then
        dnf install -y stunnel >/dev/null || die "stunnel 설치 실패"
    fi
    ok "TLS 종단 방식: ${TLS_MODE}"
}

#=============================================================================
# 인증서
#=============================================================================
# certbot standalone 은 80 번 포트를 쓴다. 발급 동안만 열고 다시 닫는다.
issue_certificate() {
    local live_dir="/etc/letsencrypt/live/${DOMAIN}"

    if [[ -s "${live_dir}/fullchain.pem" ]]; then
        ok "기존 인증서를 재사용한다: ${live_dir}"
        return
    fi

    log "Let's Encrypt 인증서 발급 (http-01, 80/tcp 필요)"
    open_port 80

    certbot certonly \
        --standalone \
        --non-interactive \
        --agree-tos \
        --email "$EMAIL" \
        --domain "$DOMAIN" \
        --key-type ecdsa \
        --preferred-challenges http \
        || die "인증서 발급 실패. 80/tcp 가 OCI Security List 에서도 열려 있는지 확인하라."

    ok "인증서 발급 완료"
}

# Squid/stunnel 는 /etc/letsencrypt 를 직접 읽기 어렵다(권한 + SELinux).
# 갱신 훅으로 전용 디렉터리에 복사하고 라벨을 맞춘다.
install_cert_deploy_hook() {
    log "인증서 배포 훅 설치: ${DEPLOY_HOOK}"

    install -d -m 0750 "$TLS_DIR"
    install -d -m 0755 "$(dirname "$DEPLOY_HOOK")"

    sed -e "s|@DOMAIN@|${DOMAIN}|g" \
        -e "s|@TLS_DIR@|${TLS_DIR}|g" \
        -e "s|@TLS_MODE@|${TLS_MODE}|g" \
        "${CONF_DIR}/certbot-deploy-hook.sh.tmpl" > "$DEPLOY_HOOK"
    chmod 0755 "$DEPLOY_HOOK"

    # 초기 1회는 직접 실행한다 (갱신 시점까지 기다릴 수 없으므로).
    RENEWED_LINEAGE="/etc/letsencrypt/live/${DOMAIN}" "$DEPLOY_HOOK" \
        || die "인증서 배포 훅 실행 실패"

    systemctl enable --now certbot-renew.timer >/dev/null 2>&1 \
        || systemctl enable --now certbot.timer >/dev/null 2>&1 \
        || warn "certbot 자동 갱신 타이머를 켤 수 없다. 'systemctl list-timers | grep certbot' 로 확인하라."

    ok "인증서 배포 및 자동 갱신 구성 완료"
}

#=============================================================================
# 인증 계정
#=============================================================================
create_proxy_user() {
    log "프록시 계정 생성: ${PROXY_USER}"
    if [[ -f "$SQUID_PASSWD" ]]; then
        htpasswd -b "$SQUID_PASSWD" "$PROXY_USER" "$PROXY_PASSWORD" >/dev/null 2>&1
    else
        htpasswd -c -b "$SQUID_PASSWD" "$PROXY_USER" "$PROXY_PASSWORD" >/dev/null 2>&1
    fi
    chown root:squid "$SQUID_PASSWD"
    chmod 0640 "$SQUID_PASSWD"
    ok "계정 등록 완료"
}

#=============================================================================
# Squid 설정
#=============================================================================
write_squid_conf() {
    log "squid.conf 생성 (${TLS_MODE} 모드)"

    [[ -f "$SQUID_CONF_BACKUP" ]] || cp -a "$SQUID_CONF" "$SQUID_CONF_BACKUP" 2>/dev/null || true

    local tmpl listen_directive
    if [[ "$TLS_MODE" == "native" ]]; then
        tmpl="${CONF_DIR}/squid-native-tls.conf.tmpl"
        listen_directive="$PROXY_PORT"
    else
        tmpl="${CONF_DIR}/squid-plain.conf.tmpl"
        listen_directive="$SQUID_PLAIN_PORT"
    fi

    # basic_ncsa_auth 경로는 아키텍처에 따라 lib64/lib 로 갈린다.
    local ncsa_auth
    ncsa_auth="$(find /usr/lib64/squid /usr/lib/squid -maxdepth 1 -name basic_ncsa_auth -type f 2>/dev/null | head -n1)"
    [[ -n "$ncsa_auth" ]] || die "basic_ncsa_auth 헬퍼를 찾을 수 없다 (squid 패키지 확인)"

    sed -e "s|@PORT@|${listen_directive}|g" \
        -e "s|@TLS_DIR@|${TLS_DIR}|g" \
        -e "s|@PASSWD_FILE@|${SQUID_PASSWD}|g" \
        -e "s|@NCSA_AUTH@|${ncsa_auth}|g" \
        -e "s|@VISIBLE_HOSTNAME@|proxy|g" \
        "$tmpl" > "$SQUID_CONF"

    squid -k parse >/dev/null 2>&1 || {
        squid -k parse || true
        die "squid.conf 문법 검사 실패 (위 출력 참고)"
    }
    ok "squid.conf 검증 통과"
}

write_stunnel_conf() {
    [[ "$TLS_MODE" == "stunnel" ]] || return 0

    log "stunnel 설정 생성"
    install -d -m 0755 /etc/stunnel
    sed -e "s|@PORT@|${PROXY_PORT}|g" \
        -e "s|@BACKEND_PORT@|${SQUID_PLAIN_PORT}|g" \
        -e "s|@TLS_DIR@|${TLS_DIR}|g" \
        "${CONF_DIR}/stunnel-squid.conf.tmpl" > "$STUNNEL_CONF"
    chmod 0644 "$STUNNEL_CONF"
    ok "stunnel 설정 완료: ${STUNNEL_CONF}"
}

#=============================================================================
# SELinux
#=============================================================================
selinux_enabled() {
    command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce)" != "Disabled" ]]
}

# 포트 라벨을 지정한다. 이미 다른 타입으로 정의된 포트는 -m(수정), 미정의는 -a(추가).
selinux_label_port() {
    local port="$1" type="$2"
    if semanage port -l 2>/dev/null | awk '{for(i=3;i<=NF;i++) print $1, $2, $i}' \
        | grep -qE "^[a-z_]+_t tcp ${port}$"; then
        semanage port -m -t "$type" -p tcp "$port" 2>/dev/null \
            || semanage port -a -t "$type" -p tcp "$port" 2>/dev/null \
            || warn "SELinux 포트 라벨 지정 실패: ${port}/tcp → ${type}"
    else
        semanage port -a -t "$type" -p tcp "$port" 2>/dev/null \
            || warn "SELinux 포트 라벨 추가 실패: ${port}/tcp → ${type}"
    fi
}

configure_selinux() {
    if ! selinux_enabled; then
        warn "SELinux 가 비활성이라 관련 설정을 생략한다."
        return
    fi
    log "SELinux 설정"

    # 프록시는 임의의 목적지 포트로 나가야 한다.
    setsebool -P squid_connect_any on || warn "setsebool squid_connect_any 실패"

    # 인증서 디렉터리를 squid 가 읽을 수 있게 라벨링한다.
    semanage fcontext -a -t squid_conf_t "${TLS_DIR}(/.*)?" 2>/dev/null \
        || semanage fcontext -m -t squid_conf_t "${TLS_DIR}(/.*)?" 2>/dev/null || true
    restorecon -R "$TLS_DIR" 2>/dev/null || true

    if [[ "$TLS_MODE" == "native" ]]; then
        # squid 가 443 등 비표준(squid 기준) 포트를 bind 할 수 있게 한다.
        # 주의: 443 을 squid_port_t 로 바꾸면 이 호스트의 httpd 는 443 을 bind 할 수 없다.
        #       이 서버는 프록시 전용이라는 가정이다.
        selinux_label_port "$PROXY_PORT" squid_port_t
    else
        # stunnel 은 stunnel_t 도메인에서 동작한다. 전용 불리언이 있으면 켠다.
        setsebool -P stunnel_can_network_connect on 2>/dev/null || true
        selinux_label_port "$PROXY_PORT" http_port_t
    fi

    ok "SELinux 설정 완료"
}

#=============================================================================
# 방화벽
#=============================================================================
# OCI 이미지는 firewalld 를 쓰는 경우와 iptables 규칙이 직접 박힌 경우가 섞여 있다.
# 둘 다 처리한다. (OCI 콘솔의 Security List/NSG 는 별도로 열어야 한다.)
open_port() {
    local port="$1"

    if systemctl is-active --quiet firewalld 2>/dev/null; then
        firewall-cmd --permanent --add-port="${port}/tcp" >/dev/null 2>&1 || true
        firewall-cmd --reload >/dev/null 2>&1 || true
        ok "firewalld: ${port}/tcp 허용"
    fi

    if command -v iptables >/dev/null 2>&1; then
        if ! iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
            iptables -I INPUT 1 -p tcp -m state --state NEW --dport "$port" -j ACCEPT 2>/dev/null || true
        fi
        # OL8 OCI 이미지는 /etc/sysconfig/iptables 에 규칙을 보존한다.
        if [[ -f /etc/sysconfig/iptables ]]; then
            iptables-save > /etc/sysconfig/iptables 2>/dev/null || true
        fi
        ok "iptables: ${port}/tcp 허용"
    fi
}

configure_firewall() {
    log "방화벽 설정"
    open_port "$PROXY_PORT"
    open_port 80   # 인증서 갱신(http-01)용
    warn "OCI 콘솔 → VCN → Security List / NSG 에 ${PROXY_PORT}/tcp, 80/tcp 인그레스 규칙을 직접 추가해야 한다."
}

#=============================================================================
# 서비스 기동
#=============================================================================
start_services() {
    log "서비스 기동"

    systemctl enable squid >/dev/null 2>&1 || true
    systemctl restart squid || {
        journalctl -u squid -n 30 --no-pager || true
        die "squid 기동 실패 (위 로그 참고)"
    }
    ok "squid 기동"

    if [[ "$TLS_MODE" == "stunnel" ]]; then
        systemctl enable stunnel >/dev/null 2>&1 || true
        systemctl restart stunnel || {
            journalctl -u stunnel -n 30 --no-pager || true
            die "stunnel 기동 실패 (위 로그 참고)"
        }
        ok "stunnel 기동"
    fi
}

save_state() {
    cat > "$STATE_FILE" <<EOF
# setup-squid.sh 가 생성. uninstall.sh / verify.sh 가 참조한다.
DOMAIN=${DOMAIN}
PROXY_PORT=${PROXY_PORT}
PROXY_USER=${PROXY_USER}
TLS_MODE=${TLS_MODE}
SQUID_PLAIN_PORT=${SQUID_PLAIN_PORT}
INSTALLED_AT=$(date -Iseconds)
EOF
    chmod 0600 "$STATE_FILE"
}

print_summary() {
    cat <<EOF

${C_OK}================ 구성 완료 ================${C_OFF}

  프록시 주소   : ${DOMAIN}:${PROXY_PORT}
  프로토콜      : HTTPS (TLS 로 감싼 HTTP 포워드 프록시)
  TLS 종단      : ${TLS_MODE}
  계정          : ${PROXY_USER}
EOF
    if [[ "$GENERATED_PASSWORD" -eq 1 ]]; then
        cat <<EOF
  비밀번호      : ${PROXY_PASSWORD}
                  ${C_WARN}↑ 지금 저장하라. 다시 표시되지 않는다.${C_OFF}
EOF
    else
        echo "  비밀번호      : (입력한 값)"
    fi
    cat <<EOF

  Edge 확장 옵션 페이지에 위 값을 그대로 입력한다.
  스킴은 반드시 ${C_OK}HTTPS${C_OFF} 를 선택할 것 (평문 HTTP 는 CONNECT 대상 호스트가 노출된다).

  자체 점검:  sudo ${SCRIPT_DIR}/verify.sh
  제거:       sudo ${SCRIPT_DIR}/uninstall.sh

EOF
}

#=============================================================================
main() {
    parse_args "$@"
    require_root
    require_oracle_linux8
    install_packages
    resolve_tls_mode
    verify_dns
    issue_certificate
    install_cert_deploy_hook
    create_proxy_user
    write_squid_conf
    write_stunnel_conf
    configure_selinux
    configure_firewall
    start_services
    save_state
    print_summary
}

main "$@"
