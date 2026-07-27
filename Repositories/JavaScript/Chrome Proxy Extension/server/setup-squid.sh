#!/usr/bin/env bash
#
# setup-squid.sh — Oracle Linux 8 에 "TLS로 감싼 HTTP 포워드 프록시"를 구성한다.
#
#   [Edge] --TLS--> :10443 (Squid https_port | stunnel) --> Squid --> Internet
#
# 전제
#   * nginx 가 이미 443/80 을 점유하고 있다 → 프록시는 **별도 포트**를 쓴다.
#   * certbot 이 이미 인증서를 자동 갱신하고 있다 → **발급하지 않고 재사용**한다.
#     (포워드 프록시는 URL 경로가 없어 nginx location 으로 분기할 수 없다.
#      CONNECT 는 authority-form 이라 경로 자체가 존재하지 않는다.)
#
# 하는 일
#   * 기존 Let's Encrypt lineage 를 찾아 도메인 커버리지·유효기간 검증
#   * Squid 설치 + Basic 인증 (Chrome/Edge 는 SOCKS5 인증을 지원하지 않음)
#   * Squid 빌드에 OpenSSL 이 없으면 stunnel 로 자동 폴백
#   * 갱신 시 인증서를 프록시용 디렉터리로 복사하는 deploy 훅 추가 (기존 훅에 영향 없음)
#   * SELinux / firewalld / iptables 처리
#   * 멱등(idempotent): 여러 번 실행해도 안전
#
# 사용법
#   sudo ./setup-squid.sh --domain 4joy.is-a.dev --user myproxyuser \
#                         [--password '...'] [--port 10443] \
#                         [--cert-name <lineage>] [--tls auto|native|stunnel]
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

# 인증서 검사 공통 함수: cert_san_list / cert_covers_domain / cert_not_after
# shellcheck source=lib/cert.sh
source "${SCRIPT_DIR}/lib/cert.sh"

readonly SQUID_CONF="/etc/squid/squid.conf"
readonly SQUID_CONF_BACKUP="/etc/squid/squid.conf.orig"
readonly SQUID_PASSWD="/etc/squid/passwd"
readonly TLS_DIR="/etc/squid/tls"
readonly STUNNEL_CONF="/etc/stunnel/squid-tls.conf"
readonly DEPLOY_HOOK="/etc/letsencrypt/renewal-hooks/deploy/90-proxy-tls.sh"
readonly STATE_FILE="/etc/squid/.setup-state"
readonly LETSENCRYPT_LIVE="/etc/letsencrypt/live"

# stunnel 폴백 시 Squid 가 로컬에서만 듣는 평문 포트
readonly SQUID_PLAIN_PORT=3128

# nginx 가 쓰는 포트. 여기에 프록시를 올리려 하면 막는다.
readonly RESERVED_PORTS=(80 443)

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
CERT_NAME=""
PROXY_USER=""
PROXY_PASSWORD=""
PROXY_PORT="10443"
TLS_MODE="auto"
GENERATED_PASSWORD=0

usage() {
    sed -n '2,33p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --domain)    DOMAIN="${2:?--domain 값 누락}";           shift 2 ;;
            --cert-name) CERT_NAME="${2:?--cert-name 값 누락}";     shift 2 ;;
            --user)      PROXY_USER="${2:?--user 값 누락}";         shift 2 ;;
            --password)  PROXY_PASSWORD="${2:?--password 값 누락}"; shift 2 ;;
            --port)      PROXY_PORT="${2:?--port 값 누락}";         shift 2 ;;
            --tls)       TLS_MODE="${2:?--tls 값 누락}";            shift 2 ;;
            -h|--help)   usage 0 ;;
            *)           die "알 수 없는 옵션: $1 (--help 참고)" ;;
        esac
    done

    [[ -n "$DOMAIN" ]]     || die "--domain 은 필수다 (Edge 가 접속할 호스트명 = 인증서의 이름)"
    [[ -n "$PROXY_USER" ]] || die "--user 는 필수다 (프록시 Basic 인증 계정)"

    # lineage 이름을 따로 주지 않으면 도메인과 같다고 본다 (certbot 기본 동작).
    [[ -n "$CERT_NAME" ]] || CERT_NAME="$DOMAIN"

    [[ "$PROXY_PORT" =~ ^[0-9]+$ ]] && (( PROXY_PORT >= 1 && PROXY_PORT <= 65535 )) \
        || die "--port 는 1-65535 범위의 숫자여야 한다: $PROXY_PORT"

    for reserved in "${RESERVED_PORTS[@]}"; do
        if [[ "$PROXY_PORT" == "$reserved" ]]; then
            die "포트 ${reserved} 는 nginx 가 쓰고 있다. 프록시는 별도 포트를 써야 한다 (기본 10443)."
        fi
    done

    case "$TLS_MODE" in
        auto|native|stunnel) ;;
        *) die "--tls 는 auto|native|stunnel 중 하나여야 한다: $TLS_MODE" ;;
    esac

    if [[ -z "$PROXY_PASSWORD" ]]; then
        PROXY_PASSWORD="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)"
        GENERATED_PASSWORD=1
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

# 프록시로 쓸 포트가 이미 점유돼 있으면 미리 알려준다.
check_port_available() {
    local holder
    if ! command -v ss >/dev/null 2>&1; then
        warn "ss 명령이 없어 포트 점유 검사를 생략한다."
        return
    fi
    holder="$(ss -tlnpH 2>/dev/null | awk -v p=":${PROXY_PORT}\$" '$4 ~ p {print $6; exit}')"

    if [[ -n "$holder" ]]; then
        # 재실행(멱등) 상황에서 squid 자신이 잡고 있는 것은 정상이다.
        if [[ "$holder" == *squid* || "$holder" == *stunnel* ]]; then
            ok "포트 ${PROXY_PORT} 는 이미 이 구성이 사용 중이다 (재구성으로 진행)"
        else
            die "포트 ${PROXY_PORT} 가 이미 사용 중이다: ${holder}. --port 로 다른 포트를 지정하라."
        fi
    else
        ok "포트 ${PROXY_PORT} 사용 가능"
    fi
}

#=============================================================================
# 기존 인증서 검증 (발급하지 않는다)
#=============================================================================
verify_existing_certificate() {
    local live_dir="${LETSENCRYPT_LIVE}/${CERT_NAME}"
    log "기존 Let's Encrypt 인증서 확인: ${live_dir}"

    if [[ ! -s "${live_dir}/fullchain.pem" || ! -s "${live_dir}/privkey.pem" ]]; then
        printf '%s[FAIL]%s %s\n' "$C_ERR" "$C_OFF" \
            "lineage 를 찾을 수 없다: ${live_dir}" >&2
        if [[ -d "$LETSENCRYPT_LIVE" ]]; then
            echo "  사용 가능한 lineage:" >&2
            find "$LETSENCRYPT_LIVE" -mindepth 1 -maxdepth 1 -type d -printf '    %f\n' >&2
            echo "  → --cert-name 으로 지정하라." >&2
        fi
        exit 1
    fi

    if ! cert_covers_domain "${live_dir}/fullchain.pem" "$DOMAIN"; then
        printf '%s[FAIL]%s %s\n' "$C_ERR" "$C_OFF" \
            "인증서가 ${DOMAIN} 를 커버하지 않는다." >&2
        echo "  인증서의 SAN:" >&2
        cert_san_list "${live_dir}/fullchain.pem" | sed 's/^/    /' >&2
        cat >&2 <<EOF
  해결 방법 중 하나를 택하라:
    1) 커버되는 이름을 --domain 으로 지정한다 (가장 간단).
    2) 기존 인증서에 이름을 추가한다:
         sudo certbot certonly --cert-name ${CERT_NAME} --nginx \\
              -d <기존 이름들 전부> -d ${DOMAIN}
       (--nginx 는 이미 동작 중인 인증 방식을 그대로 쓰라는 뜻이다.
        기존 이름을 빼면 그 이름이 인증서에서 사라진다.)
EOF
        exit 1
    fi

    if ! openssl x509 -in "${live_dir}/fullchain.pem" -noout -checkend 0 >/dev/null 2>&1; then
        die "인증서가 이미 만료됐다. 'sudo certbot renew' 로 먼저 갱신하라."
    fi

    ok "인증서 확인: ${DOMAIN} 커버, 만료 $(cert_not_after "${live_dir}/fullchain.pem")"

    # 갱신 자동화가 살아 있는지 확인만 한다 (건드리지 않는다).
    if systemctl list-timers --all 2>/dev/null | grep -q certbot; then
        ok "certbot 갱신 타이머 동작 중 (기존 설정 유지)"
    else
        warn "certbot 갱신 타이머를 찾을 수 없다. cron 등 다른 방식이라면 무시해도 된다."
        warn "Let's Encrypt 는 만료 알림 메일을 보내지 않는다(2025-06-04 종료). verify.sh 를 주기적으로 돌릴 것."
    fi
}

#=============================================================================
# 패키지
#=============================================================================
install_packages() {
    log "패키지 설치"
    # certbot 은 설치하지 않는다 — 이미 운영 중인 것을 그대로 쓴다.
    local pkgs=(squid httpd-tools policycoreutils-python-utils)
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
# 인증서 배포 훅
#=============================================================================
# /etc/letsencrypt 를 서비스가 직접 읽게 하지 않는 이유
#   1) archive/ 퍼미션이 root 전용이라 squid/stunnel 이 읽을 수 없다
#   2) SELinux 라벨(cert_t)이 squid_conf_t 와 달라 접근이 거부된다
# 갱신 훅으로 전용 디렉터리에 복사하고 라벨을 맞춘다.
#
# 파일명을 90- 으로 두어 기존 훅(nginx reload 등)보다 나중에 실행되게 한다.
install_cert_deploy_hook() {
    log "인증서 배포 훅 설치: ${DEPLOY_HOOK}"

    install -d -m 0750 "$TLS_DIR"
    install -d -m 0755 "$(dirname "$DEPLOY_HOOK")"

    sed -e "s|@CERT_NAME@|${CERT_NAME}|g" \
        -e "s|@TLS_DIR@|${TLS_DIR}|g" \
        -e "s|@TLS_MODE@|${TLS_MODE}|g" \
        "${CONF_DIR}/certbot-deploy-hook.sh.tmpl" > "$DEPLOY_HOOK"
    chmod 0755 "$DEPLOY_HOOK"

    # 초기 1회는 직접 실행한다 (다음 갱신까지 기다릴 수 없으므로).
    RENEWED_LINEAGE="${LETSENCRYPT_LIVE}/${CERT_NAME}" "$DEPLOY_HOOK" \
        || die "인증서 배포 훅 실행 실패"

    [[ -s "${TLS_DIR}/fullchain.pem" ]] \
        || die "인증서 복사가 되지 않았다: ${TLS_DIR}/fullchain.pem"

    ok "인증서 배포 완료 (기존 갱신 자동화에 편승)"
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

    local tmpl listen_port
    if [[ "$TLS_MODE" == "native" ]]; then
        tmpl="${CONF_DIR}/squid-native-tls.conf.tmpl"
        listen_port="$PROXY_PORT"
    else
        tmpl="${CONF_DIR}/squid-plain.conf.tmpl"
        listen_port="$SQUID_PLAIN_PORT"
    fi

    # basic_ncsa_auth 경로는 아키텍처에 따라 lib64/lib 로 갈린다.
    local ncsa_auth
    ncsa_auth="$(find /usr/lib64/squid /usr/lib/squid -maxdepth 1 -name basic_ncsa_auth -type f 2>/dev/null | head -n1)"
    [[ -n "$ncsa_auth" ]] || die "basic_ncsa_auth 헬퍼를 찾을 수 없다 (squid 패키지 확인)"

    sed -e "s|@PORT@|${listen_port}|g" \
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
    install -d -m 0755 /etc/stunnel /var/log/stunnel
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
        # 10443 은 기본적으로 http_port_t 다. squid 가 bind 하려면 라벨을 바꿔야 한다.
        # nginx 는 80/443 만 쓰므로 이 변경이 nginx 에 영향을 주지 않는다.
        selinux_label_port "$PROXY_PORT" squid_port_t
    else
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
# 80/443 은 nginx 를 위해 이미 열려 있을 것이므로 건드리지 않는다.
configure_firewall() {
    log "방화벽 설정: ${PROXY_PORT}/tcp"

    if systemctl is-active --quiet firewalld 2>/dev/null; then
        firewall-cmd --permanent --add-port="${PROXY_PORT}/tcp" >/dev/null 2>&1 || true
        firewall-cmd --reload >/dev/null 2>&1 || true
        ok "firewalld: ${PROXY_PORT}/tcp 허용"
    fi

    if command -v iptables >/dev/null 2>&1; then
        if ! iptables -C INPUT -p tcp -m state --state NEW --dport "$PROXY_PORT" -j ACCEPT 2>/dev/null; then
            iptables -I INPUT 1 -p tcp -m state --state NEW --dport "$PROXY_PORT" -j ACCEPT 2>/dev/null || true
        fi
        # OL8 OCI 이미지는 /etc/sysconfig/iptables 에 규칙을 보존한다.
        if [[ -f /etc/sysconfig/iptables ]]; then
            iptables-save > /etc/sysconfig/iptables 2>/dev/null || true
        fi
        ok "iptables: ${PROXY_PORT}/tcp 허용"
    fi

    warn "OCI 콘솔 → VCN → Security List / NSG 에 ${PROXY_PORT}/tcp 인그레스 규칙을 직접 추가해야 한다."
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

# 프록시를 올리는 과정에서 nginx 를 건드리지 않았는지 확인한다.
verify_nginx_intact() {
    if ! systemctl list-unit-files 2>/dev/null | grep -q '^nginx\.service'; then
        return 0
    fi
    if systemctl is-active --quiet nginx; then
        ok "nginx 정상 동작 중 (기존 서비스 영향 없음)"
    else
        warn "nginx 가 멈춰 있다. 이 스크립트는 nginx 를 건드리지 않았지만 확인이 필요하다."
    fi
}

save_state() {
    cat > "$STATE_FILE" <<EOF
# setup-squid.sh 가 생성. uninstall.sh / verify.sh 가 참조한다.
DOMAIN=${DOMAIN}
CERT_NAME=${CERT_NAME}
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
  인증서        : ${LETSENCRYPT_LIVE}/${CERT_NAME} (기존 것 재사용)
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

  자체 점검:  sudo ${SCRIPT_DIR}/verify.sh --password '...'
  제거:       sudo ${SCRIPT_DIR}/uninstall.sh

EOF
}

#=============================================================================
main() {
    parse_args "$@"
    require_root
    require_oracle_linux8
    check_port_available
    verify_existing_certificate
    install_packages
    resolve_tls_mode
    install_cert_deploy_hook
    create_proxy_user
    write_squid_conf
    write_stunnel_conf
    configure_selinux
    configure_firewall
    start_services
    verify_nginx_intact
    save_state
    print_summary
}

main "$@"
