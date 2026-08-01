# 고아 HTML 리다이렉트 작업 리포트

작업일: 2026-08-01

## 배경

`posts/` 하위 HTML 617개 중 **355개**가 대응하는 `pugs/*.pug` 소스도 없고 `source/posts.json` 색인에도 없는 상태였다. 구 카테고리 체계(`posts/java`, `posts/language/java`, `posts/dev/JVM`, `daily_life` vs `daily-life` 등)에서 현행 체계로 이주하는 과정에서 생성물만 남은 것으로 보인다.

검색엔진에 이미 색인된 URL이므로 단순 삭제 시 인덱스 경고가 발생한다. 따라서 **후계 문서가 존재하는 건만 정적 리다이렉트로 치환하고, 나머지는 원본을 보존**하는 방침을 택했다.

## 결과

| 구분 | 건수 |
|---|---:|
| 전체 HTML | 617 |
| 현행 문서 (pug 소스 보유) | 262 |
| 고아 HTML | 355 |
| → 리다이렉트 스텁으로 치환 | **308** |
| → 원본 보존 (후계 없음) | 47 |
| 고유 리다이렉트 대상 문서 | 193 |

치환 308건의 내역은 자동 매칭 286건 + 수동 큐레이션 22건이다.

## 매칭 방법

`tools/audit-orphans.mjs` 가 세 가지 신호를 조합해 후계 문서를 찾는다.

1. **파일명 일치** — 확장자를 제외한 basename이 현행 문서와 유일하게 일치
2. **제목 일치** — `<title>` 정규화 후 유일하게 일치
3. **본문 유사도** — 제목·keywords·description·본문을 토큰화(한글 2자 이상 / 영숫자 2자 이상)해 TF-IDF 코사인 유사도 산출

신뢰도 판정 기준:

| 조건 | 판정 |
|---|---|
| 파일명 + 제목 모두 일치 | high |
| 신호 1개 + 코사인 ≥ 0.35 | high |
| 신호 1개 | medium |
| 신호 없음 + 코사인 ≥ 0.6 + 2위와의 격차 ≥ 0.1 | high |
| 신호 없음 + 코사인 ≥ 0.4 | medium |
| 그 외 | low |

high 판정만 자동 적용하고, medium/low는 수동 검토했다.

## 수동 큐레이션 (22건)

자동 매칭이 놓친 통합 사례. 각 건은 후계 문서의 실제 목차(h1/h2)에 해당 내용이 존재하는지 직접 확인했다.

| 구 문서 | 후계 | 근거 |
|---|---|---|
| `db/psql_{sql,admin,programming,tutorial}` <br> `dev/DB/psql-{sql,admin,programming,tutorial}` <br> `infra/db/psql_{sql,admin,programming,tutorial}` (12건) | `dev/DB/PostgreSQL.html` | `PostgreSQL.pug`(3,752줄)에 `h1 SQL`, `h1 서버 관리`, `h1 서버 프로그래밍`, `h1 참고 - psql` 존재 |
| `front/http`, `infra/web/http` (2건) | `dev/web/network.html` | `network.pug`에 `h1 HTTP`, `h1 프록시` 존재 |
| `dev/refactoring`, `single/refactoring`, `topic/refactoring` (3건) | `dev/software-design.html` | `software-design.pug`에 `h1 코드 디자인 패턴`, `h1 코드 리팩터링` 존재 |
| `javascript/basic` (1건) | `dev/JavaScript/JavaScript.html` | 코어 JavaScript 내용 이관 확인 |
| `db/redis`, `single/redis` (2건) | `dev/DB/redis.html` | 파일명 일치 |
| `java/gradle` (1건) | `dev/gradle.html` | 파일명 일치 |
| `topic/docker_mailserver` (1건) | `dev/docker-mailserver.html` | 파일명 일치 |

## 제외 (7건) — 원본 보존

| 경로 | 사유 |
|---|---|
| `infra/heroku.html` | 자동 매칭이 `project/error.html`(cos 0.617)을 제시했으나 주제가 무관한 오탐 |
| `db/psql_to_sqlite`, `dev/DB/psql-to-sqlite`, `infra/db/psql_to_sqlite` | `PostgreSQL.pug` 내 sqlite 언급 0회 — 후계 없음 |
| `dotnet/csharp_library`, `language/.net/csharp_library` | 구 문서는 표준 라이브러리 카탈로그(스트림/Collection/스레딩/ADO.NET)인데 `csharp.pug`는 언어·런타임 중심. 부분 커버만 됨 |
| `project/snippets.html` | `project/tip.html`과 코사인 0.45로 근거 부족 |

나머지 40건은 후계 문서가 없어 원본을 유지한다. 주로 `algorithm/koreatech/*`(문제 풀이), `javascript/*_example`(데모 페이지), `*/index.html`(구 카테고리 인덱스), 삭제된 도서 리뷰 등이다.

## 리다이렉트 스텁 구조

GitHub Pages는 서버 측 301을 설정할 수 없으므로 정적 파일로 동등한 신호를 준다.

```html
<!doctype html>
<html lang="ko" data-redirect-stub>
<head>
    <meta charset="UTF-8">
    <title>PostgreSQL SQL 언어</title>
    <link rel="canonical" href="https://dong-gi.github.io/posts/dev/DB/PostgreSQL.html">
    <meta http-equiv="refresh" content="0; url=/posts/dev/DB/PostgreSQL.html">
    <meta name="description" content="PostgreSQL SQL 언어 문서는 PostgreSQL 설명서(으)로 통합되었습니다">
    <script>location.replace("/posts/dev/DB/PostgreSQL.html" + location.hash);</script>
</head>
<body>
    <h1>PostgreSQL SQL 언어</h1>
    <p>이 문서는 <a href="/posts/dev/DB/PostgreSQL.html">PostgreSQL 설명서</a>(으)로 통합되었습니다.</p>
</body>
</html>
```

설계 근거:

- **지연 0초 meta refresh** — 검색엔진이 301에 준해 처리하는 방식
- **rel=canonical** — 색인 대상을 후계 문서로 지정
- **JS `location.replace`** — 실사용자 즉시 이동, `location.hash` 보존으로 앵커 링크 유지
- **`noindex`는 넣지 않음** — `rel=canonical`과 상충하는 신호이며, 병기 시 canonical 대상까지 색인에서 빠질 위험이 있다
- **`data-redirect-stub` 마커** — 재실행 시 이미 처리된 파일을 건너뛰기 위한 멱등성 표식

## 검증

`tools/verify-redirects.mjs` 로 아래 항목을 전수 검사했고 오류 0건이다.

1. 스텁 수가 계획과 일치 (308)
2. 각 스텁의 canonical / meta refresh / JS 대상이 서로 동일
3. 대상 파일이 실제로 존재
4. 리다이렉트 체인·자기참조 없음
5. 보존 대상 47건이 변경되지 않음
6. pug 소스를 가진 현행 문서가 덮어써지지 않음

유입이 많은 대상:

| 유입 | 대상 |
|---:|---|
| 12건 | `dev/DB/PostgreSQL.html` |
| 4건 | `dev/web/network.html` |
| 3건 | `dev/DB/redis.html`, `dev/software-design.html`, `dev/JVM/freemarker_*.html` |

## 재실행 방법

```bash
node tools/audit-orphans.mjs . docs/orphan-audit.json
node tools/apply-redirects.mjs . docs/orphan-audit.json --dry-run   # 계획만 확인
node tools/apply-redirects.mjs . docs/orphan-audit.json
node tools/verify-redirects.mjs . docs/redirect-plan.json
```

`apply-redirects.mjs` 상단의 `EXCLUDE` / `OVERRIDE` 상수로 수동 판단을 관리한다. 새 문서 통합이 생기면 `OVERRIDE`에 추가하면 된다.

## 후속 조치 권장

Google Search Console에서 리다이렉트된 URL의 재크롤링을 요청하면 인덱스 갱신이 빨라진다. 다만 308건을 개별 요청할 수는 없으므로, `files/sitemap.txt`가 정상화된 뒤 사이트맵을 재제출하는 편이 현실적이다.

> 사이트맵은 현재 URL이 깨져 있다. `docs/modernization-plan.md`의 Phase 0 참조.
