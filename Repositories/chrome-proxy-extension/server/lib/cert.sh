#!/usr/bin/env bash
#
# cert.sh — 인증서 검사 공통 함수. setup-squid.sh 와 verify.sh 가 source 한다.
#
# 단독 실행용이 아니다. `source "${SCRIPT_DIR}/lib/cert.sh"` 로 불러 쓴다.

# 인증서의 SAN(DNS 이름) 목록을 소문자로, 한 줄에 하나씩 출력한다.
#
# @param $1 PEM 파일 경로
cert_san_list() {
    local pem="$1"
    openssl x509 -in "$pem" -noout -ext subjectAltName 2>/dev/null \
        | tr ',' '\n' \
        | sed -n 's/.*DNS:\([^ ]*\).*/\1/p' \
        | tr -d ' ' \
        | tr '[:upper:]' '[:lower:]'
}

# SAN 중 하나가 주어진 도메인을 커버하는지 판정한다.
#
# 와일드카드(`*.example.com`)는 RFC 6125 대로 **라벨 한 개만** 매칭한다.
# 즉 `*.example.com` 은 `a.example.com` 을 커버하지만 `a.b.example.com` 은 아니다.
#
# @param $1 PEM 파일 경로
# @param $2 확인할 도메인
# @return 0 커버함 / 1 커버하지 않음
cert_covers_domain() {
    local pem="$1" domain="${2,,}" san suffix head

    while read -r san; do
        [[ -n "$san" ]] || continue

        if [[ "$san" == "$domain" ]]; then
            return 0
        fi

        if [[ "$san" == '*.'* ]]; then
            suffix="${san#\*}"              # "*.a.com" → ".a.com"
            if [[ "$domain" == *"$suffix" ]]; then
                head="${domain%"$suffix"}"
                if [[ -n "$head" && "$head" != *.* ]]; then
                    return 0
                fi
            fi
        fi
    done < <(cert_san_list "$pem")

    return 1
}

# 인증서 만료 시각을 사람이 읽는 형식으로 출력한다.
#
# @param $1 PEM 파일 경로
cert_not_after() {
    openssl x509 -in "$1" -noout -enddate 2>/dev/null | cut -d= -f2
}
