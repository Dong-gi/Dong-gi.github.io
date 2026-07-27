#!/usr/bin/env bash
#
# verify.sh — 구성된 프록시를 서버에서 자체 점검한다.
#
#   sudo ./verify.sh [--password '...']
#
# 비밀번호를 주지 않으면 인증이 필요한 항목은 건너뛰고 도달성/인증서만 검사한다.
#
set -uo pipefail   # -e 는 쓰지 않는다: 개별 검사 실패를 집계해서 보고하려면 계속 진행해야 한다

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly STATE_FILE="/etc/squid/.setup-state"
readonly TLS_DIR="/etc/squid/tls"
readonly TRACE_URL="https://cloudflare.com/cdn-cgi/trace"

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

echo "대상: ${DOMAIN}:${PROXY_PORT} (TLS 종단=${TLS_MODE}, 계정=${PROXY_USER})"

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
head2 "2. 리스닝 소켓"
#=============================================================================
if ss -tlnp 2>/dev/null | grep -qE "[:.]${PROXY_PORT}\b"; then
    pass "${PROXY_PORT}/tcp 리스닝"
else
    fail "${PROXY_PORT}/tcp 를 아무도 듣지 않는다"
fi

if [[ "$TLS_MODE" == "stunnel" ]]; then
    if ss -tlnp 2>/dev/null | grep -qE "127\.0\.0\.1:${SQUID_PLAIN_PORT}\b"; then
        pass "squid 평문 포트 ${SQUID_PLAIN_PORT} 가 루프백에만 바인딩됨"
    else
        fail "squid 가 127.0.0.1:${SQUID_PLAIN_PORT} 를 듣지 않는다"
    fi
    if ss -tlnp 2>/dev/null | grep -qE "0\.0\.0\.0:${SQUID_PLAIN_PORT}\b"; then
        fail "squid 평문 포트가 0.0.0.0 에 노출됐다 — 즉시 수정 필요"
    fi
fi

#=============================================================================
head2 "3. 인증서"
#=============================================================================
if [[ -s "${TLS_DIR}/fullchain.pem" ]]; then
    not_after="$(openssl x509 -in "${TLS_DIR}/fullchain.pem" -noout -enddate 2>/dev/null | cut -d= -f2)"
    if openssl x509 -in "${TLS_DIR}/fullchain.pem" -noout -checkend 604800 >/dev/null 2>&1; then
        pass "인증서 유효 (만료: ${not_after})"
    else
        fail "인증서가 7일 내 만료된다 (만료: ${not_after}) → certbot renew"
    fi

    cert_cn="$(openssl x509 -in "${TLS_DIR}/fullchain.pem" -noout -subject 2>/dev/null | sed -n 's/.*CN *= *\([^,]*\).*/\1/p')"
    if [[ "$cert_cn" == "$DOMAIN" ]]; then
        pass "인증서 CN 일치: ${cert_cn}"
    else
        fail "인증서 CN(${cert_cn:-없음}) 이 도메인(${DOMAIN}) 과 다르다"
    fi
else
    fail "인증서 파일이 없다: ${TLS_DIR}/fullchain.pem"
fi

if systemctl list-timers --all 2>/dev/null | grep -q certbot; then
    pass "certbot 갱신 타이머 등록됨"
else
    fail "certbot 갱신 타이머가 없다 → systemctl enable --now certbot-renew.timer"
fi

#=============================================================================
head2 "4. TLS 핸드셰이크 (외부 주소로)"
#=============================================================================
if echo | timeout 15 openssl s_client -connect "${DOMAIN}:${PROXY_PORT}" \
        -servername "$DOMAIN" -verify_return_error >/dev/null 2>&1; then
    pass "TLS 핸드셰이크 및 체인 검증 성공"
else
    fail "TLS 핸드셰이크 실패 — OCI Security List / NSG 인그레스 규칙을 확인하라"
fi

#=============================================================================
head2 "5. 프록시 동작"
#=============================================================================
if [[ -z "$PROXY_PASSWORD" ]]; then
    skip "--password 미지정 → 인증 통과 검사 생략"
else
    # 5-1. 인증 없이 접근하면 거부돼야 한다.
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 \
            -x "https://${DOMAIN}:${PROXY_PORT}" "http://example.com" 2>/dev/null)"
    if [[ "$code" == "407" ]]; then
        pass "인증 없는 요청을 407 로 거부 (정상)"
    else
        fail "인증 없는 요청에 HTTP ${code} 응답 — 오픈 프록시 위험, http_access 확인"
    fi

    # 5-2. 인증 후 CONNECT 터널 (HTTPS 목적지)
    trace="$(curl -s --max-time 25 \
             -x "https://${DOMAIN}:${PROXY_PORT}" \
             --proxy-user "${PROXY_USER}:${PROXY_PASSWORD}" \
             "$TRACE_URL" 2>/dev/null)"
    if [[ -n "$trace" ]]; then
        egress_ip="$(sed -n 's/^ip=//p' <<<"$trace")"
        egress_loc="$(sed -n 's/^loc=//p' <<<"$trace")"
        pass "CONNECT 터널 성공 — 출구 IP=${egress_ip:-?} 국가=${egress_loc:-?}"
    else
        fail "인증 후에도 CONNECT 터널이 실패했다 → journalctl -u squid -n 50"
    fi

    # 5-3. 절대 URI 포워딩 (평문 HTTP 목적지)
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 \
            -x "https://${DOMAIN}:${PROXY_PORT}" \
            --proxy-user "${PROXY_USER}:${PROXY_PASSWORD}" \
            "http://example.com" 2>/dev/null)"
    if [[ "$code" == "200" ]]; then
        pass "평문 HTTP 포워딩 성공"
    else
        fail "평문 HTTP 포워딩 실패 (HTTP ${code})"
    fi
fi

#=============================================================================
head2 "6. SELinux"
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
