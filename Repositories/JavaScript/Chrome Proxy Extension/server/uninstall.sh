#!/usr/bin/env bash
#
# uninstall.sh — setup-squid.sh 가 만든 구성을 되돌린다.
#
#   sudo ./uninstall.sh
#
# 되돌리는 항목
#   squid/stunnel 중지·비활성, squid.conf 원복, TLS 복사본 삭제,
#   프록시용 certbot deploy 훅 삭제, SELinux 원복, 프록시 포트 닫기
#
# ── 건드리지 않는 것 (중요) ──
#   * nginx 및 80/443 포트          — 블로그가 계속 돌아야 한다
#   * /etc/letsencrypt 의 인증서     — nginx 가 쓰는 것과 같은 lineage 다.
#                                      절대 certbot delete 를 실행하지 않는다.
#   * 기존 certbot 갱신 타이머/훅
#
set -uo pipefail

readonly STATE_FILE="/etc/squid/.setup-state"
readonly SQUID_CONF="/etc/squid/squid.conf"
readonly SQUID_CONF_BACKUP="/etc/squid/squid.conf.orig"
readonly SQUID_PASSWD="/etc/squid/passwd"
readonly TLS_DIR="/etc/squid/tls"
readonly STUNNEL_CONF="/etc/stunnel/squid-tls.conf"
readonly DEPLOY_HOOK="/etc/letsencrypt/renewal-hooks/deploy/90-proxy-tls.sh"

log() { printf '[ .. ] %s\n' "$*"; }
ok()  { printf '[ OK ] %s\n' "$*"; }

[[ $# -eq 0 ]] || { echo "이 스크립트는 인자를 받지 않는다" >&2; exit 2; }
[[ "$(id -u)" -eq 0 ]] || { echo "root 권한이 필요하다" >&2; exit 1; }

DOMAIN=""; CERT_NAME=""; PROXY_PORT=""; TLS_MODE=""
if [[ -r "$STATE_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$STATE_FILE"
else
    log "상태 파일이 없다. 포트 관련 원복은 생략한다."
fi

log "서비스 중지 (nginx 는 건드리지 않는다)"
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
rm -f "$SQUID_PASSWD" "$STUNNEL_CONF"
rm -rf "$TLS_DIR"
ok "인증 파일 / TLS 복사본 삭제"

# 프록시용 훅만 지운다. 기존 nginx 관련 훅은 그대로 둔다.
if [[ -f "$DEPLOY_HOOK" ]]; then
    rm -f "$DEPLOY_HOOK"
    ok "프록시 deploy 훅 삭제: $(basename "$DEPLOY_HOOK")"
fi

if command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce)" != "Disabled" ]]; then
    log "SELinux 원복"
    semanage fcontext -d "${TLS_DIR}(/.*)?" 2>/dev/null || true
    setsebool -P squid_connect_any off 2>/dev/null || true
    if [[ -n "$PROXY_PORT" ]]; then
        # 시스템 기본 라벨로 되돌린다. 80/443 은 애초에 건드리지 않았다.
        semanage port -d -t squid_port_t -p tcp "$PROXY_PORT" 2>/dev/null || true
        semanage port -d -t http_port_t  -p tcp "$PROXY_PORT" 2>/dev/null || true
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
    ok "방화벽 원복 (80/443 은 nginx 용이므로 유지)"
fi

# nginx 가 여전히 살아 있는지 확인해 준다.
if systemctl list-unit-files 2>/dev/null | grep -q '^nginx\.service'; then
    if systemctl is-active --quiet nginx; then
        ok "nginx 정상 동작 중"
    else
        printf '[WARN] nginx 가 멈춰 있다. 이 스크립트는 nginx 를 건드리지 않았다.\n' >&2
    fi
fi

rm -f "$STATE_FILE"

cat <<EOF

제거 완료.

인증서(${CERT_NAME:-lineage}) 는 nginx 가 계속 쓰므로 **삭제하지 않았다.**

남아 있는 것 (필요하면 직접 정리):
  - 패키지: squid, stunnel, httpd-tools, policycoreutils-python-utils
      sudo dnf remove squid stunnel
  - OCI 콘솔의 Security List / NSG 인그레스 규칙 (${PROXY_PORT:-프록시 포트}/tcp)
  - 로그: /var/log/squid, /var/log/stunnel

EOF
