#!/usr/bin/env bash
#
# uninstall.sh — setup-squid.sh 가 만든 구성을 되돌린다.
#
#   sudo ./uninstall.sh [--keep-certs]
#
# 되돌리는 항목: 서비스 중지/비활성, squid.conf 원복, TLS 복사본 삭제,
#                certbot deploy 훅 삭제, SELinux 포트 라벨 원복, 방화벽 포트 닫기
# 남기는 항목:   /etc/letsencrypt (--keep-certs 없으면 lineage 도 삭제), 설치된 패키지
#
set -uo pipefail

readonly STATE_FILE="/etc/squid/.setup-state"
readonly SQUID_CONF="/etc/squid/squid.conf"
readonly SQUID_CONF_BACKUP="/etc/squid/squid.conf.orig"
readonly SQUID_PASSWD="/etc/squid/passwd"
readonly TLS_DIR="/etc/squid/tls"
readonly STUNNEL_CONF="/etc/stunnel/squid-tls.conf"
readonly DEPLOY_HOOK="/etc/letsencrypt/renewal-hooks/deploy/10-squid-tls.sh"

log() { printf '[ .. ] %s\n' "$*"; }
ok()  { printf '[ OK ] %s\n' "$*"; }

KEEP_CERTS=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --keep-certs) KEEP_CERTS=1; shift ;;
        *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
    esac
done

[[ "$(id -u)" -eq 0 ]] || { echo "root 권한이 필요하다" >&2; exit 1; }

DOMAIN=""; PROXY_PORT=""; TLS_MODE=""
if [[ -r "$STATE_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$STATE_FILE"
else
    log "상태 파일이 없다. 포트/도메인 관련 원복은 생략한다."
fi

log "서비스 중지"
systemctl disable --now squid   >/dev/null 2>&1 || true
systemctl disable --now stunnel >/dev/null 2>&1 || true
ok "squid / stunnel 중지"

log "설정 파일 원복"
if [[ -f "$SQUID_CONF_BACKUP" ]]; then
    mv -f "$SQUID_CONF_BACKUP" "$SQUID_CONF"
    ok "squid.conf 원복"
else
    rm -f "$SQUID_CONF"
    ok "squid.conf 삭제 (백업 없음)"
fi
rm -f "$SQUID_PASSWD" "$STUNNEL_CONF" "$DEPLOY_HOOK"
rm -rf "$TLS_DIR"
ok "인증 파일 / TLS 복사본 / 훅 삭제"

if command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce)" != "Disabled" ]]; then
    log "SELinux 원복"
    semanage fcontext -d "${TLS_DIR}(/.*)?" 2>/dev/null || true
    setsebool -P squid_connect_any off 2>/dev/null || true
    if [[ -n "$PROXY_PORT" ]]; then
        # 443/80 등 기본 라벨이 있던 포트는 -d 로 지우면 시스템 기본값으로 돌아간다.
        semanage port -d -t squid_port_t -p tcp "$PROXY_PORT" 2>/dev/null || true
    fi
    ok "SELinux 원복"
fi

if [[ -n "$PROXY_PORT" ]]; then
    log "방화벽 포트 닫기: ${PROXY_PORT}/tcp"
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        firewall-cmd --permanent --remove-port="${PROXY_PORT}/tcp" >/dev/null 2>&1 || true
        firewall-cmd --reload >/dev/null 2>&1 || true
    fi
    if command -v iptables >/dev/null 2>&1; then
        while iptables -C INPUT -p tcp -m state --state NEW --dport "$PROXY_PORT" -j ACCEPT 2>/dev/null; do
            iptables -D INPUT -p tcp -m state --state NEW --dport "$PROXY_PORT" -j ACCEPT
        done
        [[ -f /etc/sysconfig/iptables ]] && iptables-save > /etc/sysconfig/iptables 2>/dev/null || true
    fi
    ok "방화벽 원복 (80/tcp 는 다른 용도가 있을 수 있어 남겨둔다)"
fi

if [[ "$KEEP_CERTS" -eq 0 && -n "$DOMAIN" ]]; then
    log "Let's Encrypt lineage 삭제: ${DOMAIN}"
    certbot delete --cert-name "$DOMAIN" --non-interactive >/dev/null 2>&1 || true
    ok "인증서 삭제"
else
    ok "인증서는 보존한다"
fi

rm -f "$STATE_FILE"

cat <<'EOF'

제거 완료.

남아 있는 것 (필요하면 직접 정리):
  - 패키지: squid, stunnel, certbot, httpd-tools, policycoreutils-python-utils
      sudo dnf remove squid stunnel
  - OCI 콘솔의 Security List / NSG 인그레스 규칙
  - 로그: /var/log/squid, /var/log/stunnel

EOF
