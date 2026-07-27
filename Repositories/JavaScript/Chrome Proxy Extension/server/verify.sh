#!/usr/bin/env bash
#
# verify.sh — 구성된 프록시를 서버에서 자체 점검한다.
#
#   sudo ./verify.sh [--password '...']
#
# 비밀번호를 주지 않으면 인증이 필요한 항목은 건너뛰고 도달성/인증서만 검사한다.
#
# Let's Encrypt 는 만료 알림 메일을 보내지 않는다(2025-06-04 종료). 이 스크립트를
# cron 에 걸어두면 FAIL 시 종료코드 1 이므로 cron 이 메일을 보낸다:
#   0 4 * * 1 root /opt/proxy/verify.sh
#
set -uo pipefail   # -e 는 쓰지 않는다: 개별 검사 실패를 집계해서 보고해야 하므로

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly STATE_FILE="/etc/squid/.setup-state"
readonly TLS_DIR="/etc/squid/tls"
readonly LETSENCRYPT_LIVE="/etc/letsencrypt/live"
readonly TRACE_URL="https://cloudflare.com/cdn-cgi/trace"
readonly EXPIRY_WARN_SECONDS=604800   # 7일

# shellcheck source=lib/cert.sh
source "${SCRIPT_DIR}/lib/cert.sh"

readonly C_OK=$'\033[1;32m' C_ERR=$'\033[1;31m' C_WARN=$'\033[1;33m' C_OFF=$'\033[0m'

PASS=0
FAIL=0
SKIP=0

pass() { printf '%s[PASS]%s %s\n' "$C_OK"   "$C_OFF" "$*"; PASS=$((PASS+1)); }
fail() { printf '%s[FAIL]%s %s\n' "$C_ERR"  "$C_OFF" "$*"; FAIL=$((FAIL+1)); }
skip() { printf '%s[SKIP]%s %s\n' "$C_WARN" "$C_OFF" "$*"; SKIP=$((SKIP+1)); }
head2(){ printf '\n--- %s ---\n' "$*"; }

PROXY_PASSWORD=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --password) PROXY_PASSWORD="${2:-}"; shift 2 ;;
        *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
    esac
done

[[ -r "$STATE_FILE" ]] || { echo "상태 파일이 없다: $STATE_FILE (setup-squid.sh 를 먼저 실행)" >&2; exit 1; }
# shellcheck disable=SC1090
source "$STATE_FILE"

echo "대상: ${DOMAIN}:${PROXY_PORT} (TLS 종단=${TLS_MODE}, 계정=${PROXY_USER}, 인증서=${CERT_NAME})"

#=============================================================================
head2 "1. 서비스 상태"
#=============================================================================
if systemctl is-active --quiet squid; then
    pass "squid 실행 중"
else
    fail "squid 가 멈춰 있다 → journalctl -u squid -n 50"
fi

if [[ "$TLS_MODE" == "stunnel" ]]; then
    if systemctl is-active --quiet stunnel; then
        pass "stunnel 실행 중"
    else
        fail "stunnel 이 멈춰 있다 → journalctl -u stunnel -n 50"
    fi
fi

#=============================================================================
head2 "2. 기존 서비스(nginx) 무영향 확인"
#=============================================================================
if systemctl list-unit-files 2>/dev/null | grep -q '^nginx\.service'; then
    if systemctl is-active --quiet nginx; then
        pass "nginx 실행 중"
    else
        fail "nginx 가 멈춰 있다 (블로그 중단) → journalctl -u nginx -n 50"
    fi

    if ss -tlnpH 2>/dev/null | awk '$4 ~ /:443$/ {print $6}' | grep -q nginx; then
        pass "443/tcp 는 여전히 nginx 가 점유"
    else
        fail "443/tcp 를 nginx 가 잡고 있지 않다 — 블로그 접속 확인 필요"
    fi
else
    skip "nginx 가 설치돼 있지 않다"
fi

#=============================================================================
head2 "3. 프록시 리스닝 소켓"
#=============================================================================
if ss -tlnH 2>/dev/null | grep -qE "[:.]${PROXY_PORT}\b"; then
    pass "${PROXY_PORT}/tcp 리스닝"
else
    fail "${PROXY_PORT}/tcp 를 아무도 듣지 않는다"
fi

if [[ "$TLS_MODE" == "stunnel" ]]; then
    if ss -tlnH 2>/dev/null | grep -qE "127\.0\.0\.1:${SQUID_PLAIN_PORT}\b"; then
        pass "squid 평문 포트 ${SQUID_PLAIN_PORT} 가 루프백에만 바인딩됨"
    else
        fail "squid 가 127.0.0.1:${SQUID_PLAIN_PORT} 를 듣지 않는다"
    fi
    if ss -tlnH 2>/dev/null | grep -qE "0\.0\.0\.0:${SQUID_PLAIN_PORT}\b"; then
        fail "squid 평문 포트가 0.0.0.0 에 노출됐다 — 즉시 수정 필요"
    fi
fi

#=============================================================================
head2 "4. 인증서"
#=============================================================================
# 프록시가 실제로 읽는 복사본과, certbot 이 갱신하는 원본 두 곳을 모두 본다.
for label_path in "프록시 복사본:${TLS_DIR}/fullchain.pem" \
                  "certbot 원본:${LETSENCRYPT_LIVE}/${CERT_NAME}/fullchain.pem"; do
    label="${label_path%%:*}"
    pem="${label_path#*:}"

    if [[ ! -s "$pem" ]]; then
        fail "${label} 인증서가 없다: ${pem}"
        continue
    fi

    if openssl x509 -in "$pem" -noout -checkend "$EXPIRY_WARN_SECONDS" >/dev/null 2>&1; then
        pass "${label} 유효 (만료: $(cert_not_after "$pem"))"
    else
        fail "${label} 이 7일 내 만료된다 (만료: $(cert_not_after "$pem")) → sudo certbot renew"
    fi

    # CN 이 아니라 SAN 커버리지를 본다. 블로그와 인증서를 공유하므로 CN 은 다를 수 있다.
    if cert_covers_domain "$pem" "$DOMAIN"; then
        pass "${label} 이 ${DOMAIN} 를 커버"
    else
        fail "${label} 이 ${DOMAIN} 를 커버하지 않는다 (SAN: $(cert_san_list "$pem" | paste -sd, -))"
    fi
done

# 원본과 복사본이 같은지 — 갱신 훅이 제대로 돌았는지 확인하는 지표다.
src="${LETSENCRYPT_LIVE}/${CERT_NAME}/fullchain.pem"
dst="${TLS_DIR}/fullchain.pem"
if [[ -s "$src" && -s "$dst" ]]; then
    if cmp -s "$src" "$dst"; then
        pass "프록시 복사본이 최신 (deploy 훅 정상)"
    else
        fail "복사본이 원본과 다르다 → sudo RENEWED_LINEAGE=${LETSENCRYPT_LIVE}/${CERT_NAME} /etc/letsencrypt/renewal-hooks/deploy/90-proxy-tls.sh"
    fi
fi

if systemctl list-timers --all 2>/dev/null | grep -q certbot; then
    pass "certbot 갱신 타이머 동작 중"
else
    skip "certbot systemd 타이머 없음 (cron 방식일 수 있다 — 직접 확인)"
fi

#=============================================================================
head2 "5. TLS 핸드셰이크 (외부 주소로)"
#=============================================================================
if echo | timeout 15 openssl s_client -connect "${DOMAIN}:${PROXY_PORT}" \
        -servername "$DOMAIN" -verify_return_error >/dev/null 2>&1; then
    pass "TLS 핸드셰이크 및 체인 검증 성공"
else
    fail "TLS 핸드셰이크 실패 — OCI Security List / NSG 에 ${PROXY_PORT}/tcp 인그레스가 있는지 확인"
fi

#=============================================================================
head2 "6. 프록시 동작"
#=============================================================================
readonly PROXY_URL="https://${DOMAIN}:${PROXY_PORT}"

if [[ -z "$PROXY_PASSWORD" ]]; then
    skip "--password 미지정 → 인증 통과 검사 생략"
else
    # 6-1. 인증 없이 접근하면 거부돼야 한다 (오픈 프록시 방지).
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 \
            -x "$PROXY_URL" "http://example.com" 2>/dev/null)"
    if [[ "$code" == "407" ]]; then
        pass "인증 없는 요청을 407 로 거부 (정상)"
    else
        fail "인증 없는 요청에 HTTP ${code} 응답 — 오픈 프록시 위험, http_access 확인"
    fi

    # 6-2. 인증 후 CONNECT 터널 (HTTPS 목적지)
    trace="$(curl -s --max-time 25 -x "$PROXY_URL" \
             --proxy-user "${PROXY_USER}:${PROXY_PASSWORD}" \
             "$TRACE_URL" 2>/dev/null)"
    if [[ -n "$trace" ]]; then
        egress_ip="$(sed -n 's/^ip=//p' <<<"$trace")"
        egress_loc="$(sed -n 's/^loc=//p' <<<"$trace")"
        pass "CONNECT 터널 성공 — 출구 IP=${egress_ip:-?} 국가=${egress_loc:-?}"
    else
        fail "인증 후에도 CONNECT 터널이 실패했다 → journalctl -u squid -n 50"
    fi

    # 6-3. 절대 URI 포워딩 (평문 HTTP 목적지)
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 -x "$PROXY_URL" \
            --proxy-user "${PROXY_USER}:${PROXY_PASSWORD}" \
            "http://example.com" 2>/dev/null)"
    if [[ "$code" == "200" ]]; then
        pass "평문 HTTP 포워딩 성공"
    else
        fail "평문 HTTP 포워딩 실패 (HTTP ${code})"
    fi
fi

#=============================================================================
head2 "7. SELinux"
#=============================================================================
if command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce)" != "Disabled" ]]; then
    if getsebool squid_connect_any 2>/dev/null | grep -q ' on$'; then
        pass "squid_connect_any = on"
    else
        fail "squid_connect_any 가 off 다 → setsebool -P squid_connect_any on"
    fi

    denials="$(ausearch -m AVC -ts recent 2>/dev/null | grep -cE 'squid|stunnel' || true)"
    if [[ "${denials:-0}" -gt 0 ]]; then
        fail "최근 SELinux AVC 거부 ${denials}건 → ausearch -m AVC -ts recent | audit2why"
    else
        pass "최근 SELinux 거부 없음"
    fi
else
    skip "SELinux 비활성"
fi

#=============================================================================
printf '\n=========================================\n'
printf '  PASS %d / FAIL %d / SKIP %d\n' "$PASS" "$FAIL" "$SKIP"
printf '=========================================\n'
[[ "$FAIL" -eq 0 ]] || exit 1
