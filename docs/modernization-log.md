# 현대화 작업 로그

`docs/modernization-plan.md` 의 실행 기록. 커밋 1개 = 작업 1개 원칙으로 진행하며, 각 커밋마다 이 문서를 갱신한다.

## 현재 상태

| Phase | 상태 |
|---|---|
| Phase 0 — 결함 수정 | **완료** (커밋 2~6, 8, 11) |
| Phase 3-1~3-3 — SEO·CI·문서화 | **완료** (커밋 7, 9, 10, 13) |
| Phase 2 — Repositories | **재작성 완료, 빌드 미검증** (커밋 17~19, 26~31) |
| Phase 1 — 포스트 최신화 | **착수** (커밋 20~22). 우선순위 20건 중 1건 완료 |

Phase 2에서 손댄 프로젝트는 **72개**다.

| 분류 | 개수 | 내용 |
|---|---:|---|
| Spring | 40 | Jakarta EE 전환, Spring 7 / Boot 4.0, Java 25 |
| .NET | 17 | net6.0 → net10.0 |
| Node | 10 | `engines` 선언 |
| Android | 7 | compileSdk 36 / AGP 9 |
| Maven (Spring 외) | 8 | Java 25, `maven.compiler.release` |
| Gradle (Spring 외) | 6 | Gradle 9.3.1, Java 25, 제거된 API 정리 |
| 삭제 | 4 | 미참조 예제 |

**커밋 26 이후는 빌드로 검증하지 못했다.** 문서 맨 아래 [검증 체크리스트](#검증-체크리스트)를 참조.

**Phase 2의 프레임워크 재작성(Spring Boot 4.0 / .NET 10 / compileSdk 36)은 이 환경에서 검증할 수 없다.** 다만 그 이유는 처음에 적었던 것과 다르다.

| 항목 | 상태 |
|---|---|
| JDK 25.0.3 (`javac` 포함) | **설치 가능** — `apt-get download` + `dpkg -x`, root 불필요 |
| Maven Central | 차단 |
| Gradle 배포본 (`services.gradle.org`) | 차단 |
| GitHub raw / releases API | 차단 |
| NuGet, .NET SDK, Android SDK (`dl.google.com`) | 차단 |

즉 **컴파일러는 있지만 의존성을 하나도 내려받을 수 없다.** Spring·Android·.NET 프로젝트는 빌드 자체가 시작되지 않는다. 검증하지 못한 업그레이드를 커밋하는 대신, 확인 가능한 작업(미참조 프로젝트 삭제, 산출물 제거, Gradle 설정 이름 교체)만 처리했다.

반대로 **의존성이 없는 Java 코드는 실제로 컴파일·실행해 검증할 수 있다.** 커밋 22의 예제 7개를 이 방법으로 전수 검증했다.

```
$ npm run typecheck
✅ 오류 0건

$ npm run check
HTML 617개 (현행 262, 리다이렉트 308, 보존 47, 새 고아 0) / posts.json 251개 / 코드 참조 804개
✅ 오류 없음 (경고 11건)

렌더 드리프트: 263개 전부 커밋된 산출물과 일치
리다이렉트 스텁 무결성: 308건 통과
```

경고 11건은 `posts.json` 에 등록되지 않은 pug 문서다. 공개 여부 결정이 필요한 사안이라 오류로 올리지 않았다.

### 다음에 할 일

Phase 1의 남은 우선순위 문서다. 문서당 공식 자료 조사가 필요하므로 하나씩 커밋한다.

| # | 문서 | 줄 수 | 핵심 갱신 대상 |
|---|---|---:|---|
| 1 | `dev/JVM/spring_framework.pug` | 1,578 | Spring 5.x → 6/7, `javax.*` → `jakarta.*` |
| 2 | `dev/JVM/spring_servlet.pug` | 1,338 | 위와 동일 + JSP/XML 전제 |
| 3 | `dev/python/standard.pug` | 2,148 | `Since 3.8` 상한 → 3.14 |
| 4 | `dev/dotnet/csharp.pug` | 1,562 | `.NET 6` / C# 10 → .NET 10 / C# 14 |
| 5 | `dev/python/basic.pug` | 359 | 본문 내용을 3.14 기준으로 |
| — | `dev/JVM/java_ee.pug`, `jpa.pug` | | Jakarta EE 이관 |
| — | `dev/rpi.pug` | 395 | 2017년 Raspbian Stretch → 현행 |
| — | `project/error.pug` | 367 | 종료된 Heroku 무료 티어 절 정리 |

**커밋 11~15는 커밋 1~10에 대한 독립 리뷰에서 나온 지적을 반영한 것이다.** 리뷰는 별도 에이전트가 코드를 직접 실행·대조해 수행했고, CI를 막는 문제 1건과 잘못된 서술 여러 건을 찾아냈다.

## 커밋 목록

리뷰 순서는 아래 표의 위에서 아래 방향이다.

| # | 커밋 | 종류 | 계획서 항목 | 리뷰 포인트 |
|---|---|---|---|---|
| 1 | 고아 HTML 308건을 리다이렉트 스텁으로 치환 | chore | 별건 (선행 작업) | 스텁 형식, 보존 47건의 타당성 |
| 2 | 사이트맵 URL에 누락된 `/posts/` 접두사 복원 | fix | Phase 0-1 | 251개 URL이 전부 깨져 있었음 |
| 3 | TypeScript 6 고정, `@types/node` 를 LTS 24로 하향 | chore | 채택 기준 | 6개월 룰 적용 결과 |
| 4 | `sharp` 네임스페이스 타입 오류 수정, `typecheck` 스크립트 추가 | fix | — | 기존 오류. **커밋 11에서 검증 범위 정정** |
| 5 | 수집이 중단된 Universal Analytics 태그 제거 | fix | Phase 0-2 | GA4 재도입 여부는 별도 판단 |
| 6 | 제거된 서브모듈을 가리키던 깨진 코드 참조 정리 | fix | Phase 0-3 | 깨진 참조 2건 → 0건 |
| 7 | canonical·Open Graph·JSON-LD 메타데이터 추가 | feat | Phase 3-1 | 생성 HTML 263개 전부 갱신 |
| 8 | 사이트 정합성 검사를 빌드에 통합 | feat | Phase 0-5 · 2-5 | 고아 355개가 쌓인 근본 원인 차단 |
| 9 | GitHub Actions 검증 워크플로 추가 | ci | Phase 3-2 | 배포에는 관여하지 않음 |
| 10 | `CLAUDE.md` 작성 | docs | Phase 3-3 | 빌드 파이프라인·규칙 문서화 |
| 11 | 타입 검사 범위에서 `Repositories` 제외 | fix | 리뷰 지적 (치명적) | CI가 반드시 실패하던 문제 |
| 12 | 보존 대상 구 HTML 38건의 죽은 UA 태그 제거 | fix | 리뷰 지적 (중요) | 커밋 5의 미완 부분 |
| 13 | 홈페이지 구조화 데이터를 `WebSite` 로 분리 | fix | 리뷰 지적 (중요) | 홈이 `TechArticle` 로 나가던 문제 |
| 14 | 정합성 검사 보완 + 그로 인해 드러난 코드 버튼 오류 수정 | fix | 리뷰 지적 (사소) | 검사가 실제 버그를 잡아냄 |
| 15 | 부정확한 서술 정정, `Post.category` 타입 수정 | docs | 리뷰 지적 (중요·사소) | 문서를 코드와 일치시킴 |
| 16 | 최종 검증, `gradle.html` 날짜 재생성 | chore | — | mtime 취약성의 실물 사례 |
| 17 | 미참조 예제 프로젝트 4개 삭제 | chore | Phase 2-1 | 122 → 118개 |
| 18 | 커밋된 컴파일 산출물 25개 제거 | chore | Phase 2-4 | 계획서의 잘못된 서술도 정정 |
| 19 | Gradle 7에서 제거된 의존성 설정 158줄 교체 | fix | Phase 2-4 | 문서의 잘못된 예시도 갱신 |
| 20 | 구버전 접기 `+legacy` mixin 추가 | feat | Phase 1-1 | Phase 1의 전제 |
| 21 | 제목에 박제된 버전 13건 정리 | refactor | Phase 1-3 | URL은 그대로, 제목만 변경 |
| 22 | `Java 버전` 문서를 Java 26까지 갱신 | feat | Phase 1-2 (1순위) | 212 → 482줄 |
| 23 | 진행 상태 정리 | docs | — | 다음 작업 목록 포함 |
| 24 | 문서 Java 예제 7개를 JDK 25로 실행 검증 | docs | — | 전부 통과 |
| 25 | 예제 소스 132개의 Java 25 호환성 측정 | docs | Phase 2 사전 조사 | 비호환 0건 |
| 26 | Android 7개를 compileSdk 36 / AGP 9로 이관 | feat | Phase 2-3 | ⚠ **빌드 미검증** |
| 27 | .NET 17개를 net10.0으로 이관 | feat | Phase 2-3 | ⚠ 빌드 미검증 |
| 28 | Spring 미사용 Gradle 6개를 Gradle 9.3.1 / Java 25로 | feat | Phase 2-3 | ⚠ 빌드 미검증 |
| 29 | Spring 미사용 Maven 8개를 Java 25로 | feat | Phase 2-3 | ⚠ 빌드 미검증 |
| 30 | Spring 40개를 Jakarta EE / Boot 4.0으로 이관 | feat | Phase 2-3 | ⚠ **위험도 최상** |
| 31 | Node 10개에 `engines`, 홈 빌드 안내 정정 | feat | Phase 2-4 | `build-all` 은 없는 스크립트였음 |
| 32 | 검증 체크리스트 작성 | docs | — | 위험도 순 8개 항목 |

---

## 1. 고아 HTML 308건을 리다이렉트 스텁으로 치환

**변경 파일**: `posts/**` 308개, `tools/` 3개 신규, `docs/` 4개 신규

### 무엇을

`posts/` 하위 HTML 617개 중 355개가 pug 소스도 `posts.json` 색인도 없는 고아 상태였다. 이 중 후계 문서를 찾아낸 **308개**를 정적 리다이렉트 스텁으로 치환하고, 후계가 없는 **47개**는 원본을 보존했다.

### 왜

검색엔진에 이미 색인된 URL이라 삭제하면 인덱스 경고가 발생한다. 후계 문서가 있는데도 죽은 페이지를 방치하면 방문자가 옛 내용을 보게 된다.

### 어떻게

파일명 일치 / 제목 일치 / TF-IDF 코사인 유사도 세 신호를 조합해 후계 문서를 찾고, high 신뢰도만 자동 적용했다. 자동 매칭이 놓친 통합 사례 22건은 후계 문서의 실제 목차(h1/h2)를 직접 확인한 뒤 수동 등록했다. 오탐 1건(`infra/heroku` → `project/error`)은 제외했다.

스텁은 `meta refresh(0초)` + `rel=canonical` + `location.replace` 3중 구성이다. `noindex`는 canonical과 상충하는 신호이므로 넣지 않았다.

### 검증

`tools/verify-redirects.mjs` 로 6개 항목(개수 일치 / 3중 신호 동일성 / 대상 존재 / 체인·자기참조 / 보존 대상 무변경 / 현행 문서 무오염)을 전수 검사, 오류 0건.

### 리뷰 포인트

- 보존한 47건 중 실제로는 후계가 있는 것이 없는지 (`docs/redirect-plan.json` 의 `skipped` 배열)
- 수동 큐레이션 22건의 매핑 타당성 (`tools/apply-redirects.mjs` 의 `OVERRIDE` 상수)

상세 내역은 `docs/orphan-redirect-report.md` 참조.

---

## 2. 사이트맵 URL에 누락된 `/posts/` 접두사 복원

**변경 파일**: `source/build.ts`, `files/sitemap.txt`

### 무엇을

사이트맵에 등록된 **251개 URL이 전부 무효**였다. 정상 형태로 재생성했다.

```
https://dong-gi.github.iobook/0/001.html          ← 기존 (깨짐)
https://dong-gi.github.io/posts/book/0/001.html   ← 수정 후
```

### 왜

`build.ts` 가 URL을 이렇게 만들고 있었다.

```ts
posts.list.map((post) => `https://dong-gi.github.io${post.file}`)
```

`post.file` 은 `"book/0/001.html"` 형태로 선행 슬래시도 `posts/` 접두사도 없다. 문자열이 그대로 이어붙어 호스트명이 `dong-gi.github.iobook` 이 되어버렸다.

`robots.txt` 가 이 사이트맵을 가리키고 있어 색인에 직접 영향을 준다. 리다이렉트 스텁(커밋 1)의 효과를 검색엔진에 전달하려면 사이트맵 재제출이 필요한데, 그 전제가 되는 수정이다.

### 어떻게

오리진과 경로 접두사를 상수로 분리하고 `postUrl()` 헬퍼로 URL 생성 지점을 하나로 모았다. 같은 실수가 재발하지 않도록 문자열 템플릿을 코드에서 없앴다.

```ts
const SITE_ORIGIN = 'https://dong-gi.github.io';
const POSTS_URL_PREFIX = '/posts/';

function postUrl(post: Post): string {
    return SITE_ORIGIN + POSTS_URL_PREFIX + post.file;
}
```

### 검증

재생성한 251개 URL 전부에 대해 대응하는 파일이 실제로 존재하는지 확인했다 (누락 0건).

### 리뷰 포인트

- `files/sitemap.txt` 는 빌드 산출물인데 저장소에 커밋되어 있다. 이번엔 스크립트로 직접 재생성해 커밋에 포함했다
- 기존 사이트맵은 250줄, 새 사이트맵은 251줄이다. `posts.json` 항목 수(251)와 일치하므로 기존 것이 한 항목 뒤처진 상태였던 것으로 **추측**된다

---

## 3. TypeScript 6 고정, `@types/node` 를 LTS 24로 하향

**변경 파일**: `package.json`

| 패키지 | 기존 | 변경 | 사유 |
|---|---|---|---|
| `typescript` | `^7.0.2` | `~6.0.3` | TS 7.0은 2026-07-08 GA로 6개월 룰 미달. 연관 도구 미지원 |
| `@types/node` | `^25.6.0` | `^24.10.1` | Node 25는 **2026-06-01 EOL**. 24가 Active LTS |
| `engines.node` | 없음 | `>=24` | 빌드가 Node 네이티브 TS 타입 스트리핑에 의존 |

### 왜

TypeScript 7.0은 Go 네이티브로 재작성된 컴파일러라 변화 폭이 크다. **programmatic API를 제공하지 않아** typescript-eslint, webpack loader, 각종 템플릿 검사 도구가 아직 동작하지 않는다. API는 7.1에 나올 예정이다.

`^7.0.2` 캐럿 범위는 7.x 전체를 허용하므로 향후 마이너 업데이트가 자동 유입된다. `~6.0.3` 으로 패치 범위만 열어두었다.

`@types/node ^25` 는 더 심각한데, Node 25는 이미 EOL이라 존재하지 않는 런타임의 타입을 참조하고 있었다.

### 어떻게

TS 5.9.3 / 6.0.3 / 7.0.2 세 버전으로 실제 타입 검사를 돌려 비교했다. 세 버전 모두 동일하게 `source/build.ts(152,22)` 에서 `TS2503: Cannot find namespace 'sharp'` 하나만 보고했다. **이 오류는 버전 변경과 무관한 기존 문제**이며 커밋 4에서 따로 고친다.

즉 6.0.3으로의 하향은 새로운 타입 오류를 만들지 않는다.

### 리뷰 포인트

- TS 6.0은 2026-09-23에 6개월 룰을 충족한다. 그때까지는 엄밀히 말해 2개월 부족한 절충안이다
- `engines` 는 npm이 기본으로 강제하지 않는다. 강제하려면 `.npmrc` 에 `engine-strict=true` 가 필요하다

---

## 4. `sharp` 네임스페이스 타입 오류 수정, `typecheck` 스크립트 추가

**변경 파일**: `source/build.ts`, `package.json`

### 무엇을

`source/build.ts(152,22): error TS2503: Cannot find namespace 'sharp'` 를 고쳤다.

```diff
-import sharp from 'sharp';
+import sharp, { type Sharp } from 'sharp';
...
-            let img: sharp.Sharp | undefined;
+            let img: Sharp | undefined;
```

`npm run typecheck` 스크립트도 추가했다.

### 왜

`sharp` 의 타입 선언은 `declare namespace sharp` + `export = sharp` 형태다. `tsconfig.json` 에 `verbatimModuleSyntax: true` 가 걸려 있어 기본 임포트는 값 바인딩만 가져오고 네임스페이스는 가져오지 않는다. 그래서 `sharp.Sharp` 를 타입 위치에서 참조할 수 없었다.

TS 5.9 / 6.0 / 7.0 세 버전 모두에서 동일하게 나던 **기존 오류**다. 타입 검사가 원래부터 실패하고 있었기 때문에, 이 상태로는 CI에 검사 단계를 넣을 수 없었다.

### 검증

TS 6.0.3 기준 `source/build.ts` 의 타입 오류 0건.

> **정정 (커밋 11)** — 이 시점의 검증은 `source/build.ts` 만 대상으로 한 것이었다. 실제로는 `tsconfig.json` 의 `include: ["**/*"]` 가 `Repositories/` 하위 `.ts` 8개까지 끌어와 `npm run typecheck` 는 오류 4건으로 실패했다. 커밋 11에서 고쳤다.

### 리뷰 포인트

- `tsconfig.json` 의 `include` 가 `["**/*"]` 라 `Repositories/` 하위 JS까지 스캔 대상에 들어간다. `checkJs: false` 라 검사는 하지 않지만 스캔 비용은 발생한다. `exclude` 추가를 검토할 만하다

---

## 5. 수집이 중단된 Universal Analytics 태그 제거

**변경 파일**: `source/skeleton.pug`

### 무엇을

모든 페이지 `<head>` 에서 로드하던 `UA-143098403-1` 태그와 `gtag` 초기화 코드를 제거했다.

### 왜

Universal Analytics는 **2023-07-01 에 데이터 수집을 중단**했고 표준 속성은 2024년 7월 삭제됐다. 3년 넘게 아무 데이터도 수집되지 않으면서, 모든 페이지 방문마다 `googletagmanager.com` 외부 스크립트 요청만 발생하고 있었다.

### 어떻게

삭제하되 GA4로 되돌릴 때 필요한 형태를 pug 주석으로 남겼다. 측정 ID(`G-XXXXXXXXXX`)는 사용자가 직접 발급해야 하므로 임의 값을 넣지 않았다.

### 리뷰 포인트

- **방문자 통계를 계속 보고 싶다면 GA4 측정 ID를 발급해 주석의 형태로 되살려야 한다.** 지금 상태는 통계 수집이 완전히 없는 상태다
- 통계가 필요 없다면 이대로 두는 편이 페이지 로드가 빠르고 추적도 없다
- 이미 생성된 `posts/**` HTML에는 여전히 옛 태그가 남아 있다. 빌드를 다시 돌려야 반영된다

---

## 6. 제거된 서브모듈을 가리키던 깨진 코드 참조 정리

**변경 파일**: `pugs/dev/JVM/spring_framework.pug`, `posts/dev/JVM/spring_framework.html`, `.gitmodules` (삭제)

### 무엇을

`Spring Config Server` / `Spring Config Client` 절의 코드 버튼 2개가 존재하지 않는 파일을 가리키고 있었다. 누르면 실패한다.

- `/Repositories/Config/webapp.properties`
- `/Repositories/Config/config-client.properties`

### 왜 (원인 추적)

git 이력을 따라가 보니 `Repositories/Config` 는 파일이 아니라 **git 서브모듈**(`160000` 모드)이었고, 실체는 별도 저장소 `git@github.com:Dong-gi/Config.git` 다.

2023-03-10 커밋 `50451059` 에서 서브모듈 5개(`Config`, `Android/CCTV`, `JavaScript/SF`, `Node/Rendezvous`, `STS/Reminder`)를 한꺼번에 제거하면서 `.gitmodules` 를 비웠는데, **pug 쪽 참조는 함께 정리되지 않았다.** 나머지 4개는 참조가 없어 문제가 드러나지 않았고 `Config` 만 남았다.

`config-server/src/main/resources/bootstrap.properties` 가 아직 이 외부 저장소를 가리킨다.

```properties
spring.cloud.config.server.git.uri=https://github.com/Dong-gi/Config
```

### 어떻게

서브모듈 제거는 2023년의 의도적 결정으로 보이므로 되돌리지 않고, 참조 방식을 바꿨다.

- **`webapp.properties`** — 내용을 git 이력에서 복원했다(`c99c07db^` 시점, `message=Hello World`). 코드 버튼 대신 `+asCode` 로 인라인 표시
- **`config-client.properties`** — 이 저장소 이력 어디에도 존재한 적이 없어 내용을 복원할 수 없다. 임의로 지어내지 않고, 파일명 규칙(클라이언트의 `spring.application.name` 과 동일)과 `HelloController` 가 `@Value("${message}")` 로 읽으므로 `message` 속성이 필요하다는 사실만 서술했다
- 두 곳 모두 설정 저장소가 **별도 저장소**임을 명시하고 링크를 걸었다
- 이미 비어 있던 `.gitmodules` 를 삭제했다

### 검증

- pug 렌더 성공, 생성된 HTML 확인
- `pugs/`·`index.pug`·`source/` 전체에서 `/Repositories/...` 참조 **805건 중 깨진 것 0건** (수정 전 807건 중 2건 깨짐)

### 리뷰 포인트

- `config-client.properties` 의 실제 내용을 알고 계시면 인라인 코드로 넣는 편이 낫다
- 외부 저장소 `Dong-gi/Config` 가 아직 공개 상태인지 확인 필요. 비공개거나 삭제되었다면 링크도 함께 정리해야 한다

---

## 7. canonical·Open Graph·JSON-LD 메타데이터 추가

**변경 파일**: `source/skeleton.pug`, `source/build.ts`, `index.html`, `posts/**` 262개

### 무엇을

모든 페이지 `<head>` 에 아래를 추가했다.

```html
<link rel="canonical" href="https://dong-gi.github.io/posts/dev/aws.html">
<meta property="og:url" content="https://dong-gi.github.io/posts/dev/aws.html">
<meta property="og:type" content="article">
<meta property="og:site_name" content="dong-gi.github.io">
<meta property="og:locale" content="ko_KR">
<meta property="og:title" content="AWS">
<meta property="og:description" content="AWS 정리">
<meta property="og:image" content="https://dong-gi.github.io/imgs/favicon.png">
<meta name="twitter:card" content="summary">
<script type="application/ld+json">{"@context":"https://schema.org","@type":"TechArticle",...}</script>
```

### 왜

- **canonical** — 리다이렉트 스텁(커밋 1)에는 넣었는데 정작 현행 문서에는 없었다. 같은 문서가 여러 경로로 도달 가능할 때 색인 대상을 명확히 한다
- **Open Graph** — Slack·카카오톡·X 등에 링크를 붙여도 미리보기가 나오지 않았다
- **JSON-LD** — `dateModified` 를 구조화 데이터로 노출하면 검색 결과에 갱신일이 표시될 수 있다. `posts.json` 에 이미 `mtimeMs` 가 있었지만 HTML에는 어떤 날짜 정보도 없었다

### 어떻게

`skeleton.pug` 의 `post` mixin은 자기 출력 경로를 모른다. `build.ts` 가 렌더 시점에 세 값을 locals로 주입하도록 했다.

```ts
const { mtime } = await fsp.stat(o.path);
const html = await renderFile(o.path, {
    cache: true,
    imgMap: workerImgMap,
    siteOrigin: SITE_ORIGIN,
    pageUrl: pageUrlOf(htmlPath),
    pageModified: mtime.toISOString(),
});
```

`dateModified` 는 소스 pug의 mtime을 쓴다. `posts.json` 의 `mtimeMs` 는 `processPugs()` 가 렌더 작업과 동시에 갱신하므로 워커에서 참조하면 경합이 생긴다. 워커가 직접 stat 하는 편이 단순하고 정확하다.

`pageUrlOf()` 는 `/index.html` 을 `/` 로 정규화한다. 홈이 두 URL로 색인되는 것을 막는다.

주의한 것 두 가지:

- **JSON-LD의 `</script>` 조기 종료** — `<`, `>` 를 `<`, `>` 로 이스케이프했다. JSON 문자열 내부에서 유효한 이스케이프라 파싱 결과는 동일하다
- **빌드 밖 단독 렌더** — locals가 없으면 해당 태그를 건너뛴다. `typeof x === 'undefined'` 가드로 처리

### 검증

- 263개 pug 전부 렌더 성공, 실패 0건
- 제목에 `&`, `(`, `)` 가 든 문서 3종으로 JSON-LD를 `JSON.parse` 해 유효성 확인
- locals 없이 렌더해도 예외가 나지 않음을 확인
- 홈의 canonical이 `https://dong-gi.github.io/` 로 정규화되는지 확인
- 리다이렉트 스텁 308건이 훼손되지 않았는지 `tools/verify-redirects.mjs` 재실행 (오류 0건)

### 리뷰 포인트

- `og:image` 가 favicon이다. 문서별 대표 이미지를 쓰려면 `post` mixin에 옵션을 추가해야 한다
- `datePublished` 는 넣지 않았다. 최초 작성일을 알 방법이 마땅치 않아 지어내지 않았다. git 최초 커밋일을 쓸 수 있지만 대량 리팩터링 커밋 때문에 부정확하다
- ⚠ **`dateModified` 가 mtime 기반이라 머신 간 재현성이 없다.** 새로 클론하면 모든 파일의 mtime이 체크아웃 시각이 되므로, 다른 머신에서 빌드를 한 번만 돌려도 263개 페이지의 날짜가 그날로 덮이고 대량 diff가 생긴다. 커밋 15에서 `CLAUDE.md` 에 경고를 넣었고, git 커밋 날짜 기반으로 바꾸는 것은 후속 과제로 남긴다
- **이 커밋의 diff는 266개 파일이다.** 실질 변경은 `skeleton.pug` 와 `build.ts` 두 개뿐이고 나머지는 재생성된 산출물이다
- diff에 재렌더 대상이 아닌 파일(`posts/algorithm/koreatech/*.html`, `posts/db/example/*.sql` 등)도 섞여 있다. `.gitattributes` 의 `* text=auto` 에 따라 **CRLF가 LF로 정규화된 것**이며 내용은 동일하다. CR을 제거한 뒤 해시를 비교해 확인했다. 저장소에 CRLF로 커밋되어 있던 기존 불일치가 이번에 정리된 것이다

---

## 8. 사이트 정합성 검사를 빌드에 통합

**변경 파일**: `tools/check-integrity.mjs` (신규), `tools/preserved-orphans.json` (신규), `package.json`

### 무엇을

`npm run check` 로 실행하는 정합성 검사를 만들고 `npm run build` 마지막 단계에 붙였다.

| 코드 | 검사 |
|---|---|
| E1 | `posts.json` 항목의 HTML 파일이 실제로 존재하는가 |
| E2 | `posts.json` 항목에 대응하는 pug 소스가 존재하는가 |
| E3 | `posts.json` 에 중복 `file` 항목이 없는가 |
| E4 | pug 소스도 리다이렉트 스텁도 아니고 보존 목록에도 없는 **새 고아 HTML** 이 없는가 |
| E5 | 리다이렉트 스텁의 대상이 존재하고 체인·자기참조가 없는가 |
| E6 | pug 안의 `/Repositories/...` 코드 참조가 실제 파일을 가리키는가 |
| E7 | 사이트맵 URL의 형식이 맞고 실제 파일과 대응하며 스텁을 가리키지 않는가 |
| W1 | pug는 있으나 `posts.json` 에 등록되지 않은 문서 (경고) |
| W2 | 보존 목록에 있으나 파일이 사라졌거나 스텁이 된 항목 (경고) |

### 왜

빌드는 pug → html **단방향 생성**만 한다. pug를 지워도 html이 남고, `posts.json` 과의 어긋남도, 깨진 코드 참조도 드러나지 않는다. 고아 HTML이 355개까지 쌓이고 사이트맵 URL 251개가 전부 깨진 채 방치된 것이 이 때문이다. 이번에 고친 문제들이 다시 쌓이지 않게 하는 것이 목적이다.

E6는 계획서 Phase 2(Repositories 재작성)의 **선행 조건**이기도 하다. 프로젝트를 삭제·재작성하면 코드 버튼이 대량으로 깨질 수 있는데, 이 검사가 없으면 알 방법이 없다.

### 어떻게

의도적으로 보존한 고아 47건을 매번 오류로 잡으면 검사가 무용지물이 되므로, `tools/preserved-orphans.json` 에 사유와 함께 선언해두고 E4에서 제외한다. **목록에 없는 새 고아만 오류**가 되므로 회귀 감시 기능은 유지된다.

```json
{
    "$comment": "검색엔진에 색인되어 있어 삭제하지 않고 보존하는 구 HTML 목록. ...",
    "files": [
        { "file": "algorithm/book01.html", "reason": "후계 문서 없음" },
        ...
    ]
}
```

`posts.json` 미등록 문서 11건(`not-registered/` 9건 + `project/privacy` + `project/wpf_data_tool`)은 의도를 알 수 없어 **경고**로 두었다. 결론이 나면 `--strict` 로 승격하면 된다.

### 검증

- 현재 저장소: 오류 0건, 경고 11건 → 종료 코드 0
- `--strict`: 종료 코드 1
- **회귀 테스트** — 일부러 고아 HTML을 만들었을 때 E4로 검출, 사이트맵 URL을 망가뜨렸을 때 E7로 검출되는 것을 확인하고 원상 복구

### 리뷰 포인트

- 경고 11건을 어떻게 할지 결정이 필요하다. `not-registered/` 를 계속 미공개로 둘 것이라면 아예 빌드 대상에서 빼는 편이 낫다. 지금은 **HTML은 생성되지만 사이트맵·목록 어디에도 없어 아무도 찾을 수 없는 상태**다
- `npm run build` 가 검사 실패 시 종료 코드 1로 끝난다. 산출물은 이미 쓰인 뒤이므로 "빌드를 막는" 것이 아니라 "문제를 알리는" 동작이다

---

## 9. GitHub Actions 검증 워크플로 추가

**변경 파일**: `.github/workflows/verify.yml` (신규), `package-lock.json`

### 무엇을

`.github/` 디렉터리가 아예 없었다. push·PR 시 타입 검사와 정합성 검사를 돌리는 워크플로를 추가했다.

```
actions/checkout@v4
actions/setup-node@v4 (node 24, npm 캐시)
npm ci
npm run typecheck
npm run check
```

### 왜

커밋 8에서 만든 검사는 로컬에서 돌려야만 의미가 있다. CI가 없으면 규칙이 지켜지는지 알 수 없고, 계획서 Phase 1·2 처럼 큰 변경을 할 때 안전망이 없다.

### 어떻게 — 배포는 건드리지 않았다

이 저장소는 `posts/`, `index.html`, `files/` 를 **커밋된 산출물** 그대로 서빙하는 GitHub Pages "deploy from branch" 방식이다. Actions 배포 워크플로를 추가하면 배포 방식 자체가 바뀌므로 손대지 않았다. 검증 전용이다.

전체 빌드(`npm run build`)도 CI에서 돌리지 않는다. `d2` 바이너리 설치와 이미지 재생성이 필요해 느리고 깨지기 쉽다. 소스만으로 확인 가능한 두 검사만 돌린다.

### `package-lock.json` 재생성

`npm ci` 는 `package.json` 과 lock이 어긋나면 실패한다. 커밋 3에서 의존성을 바꿨으므로 `npm install --package-lock-only` 로 lock을 갱신했다.

| 패키지 | lock 해석 결과 |
|---|---|
| `typescript` | 6.0.3 |
| `@types/node` | 24.13.3 |

lock에서 **424줄이 삭제**됐는데, TypeScript 7이 플랫폼별 Go 네이티브 바이너리 패키지(`@typescript/native-preview-*`)를 여럿 끌어오던 것이 사라졌기 때문이다. 설치 용량이 줄어드는 부수 효과가 있다.

### 검증

`.github/workflows/verify.yml` 을 YAML 파서로 읽어 구조를 확인했다. 실제 실행 결과는 push 이후에 확인해야 한다.

### 리뷰 포인트

- **아직 CI에서 실행해보지 않았다.** push 후 첫 실행 결과 확인이 필요하다
- `npm ci` 가 `sharp` 프리빌트 바이너리를 받는다. 실패하면 `npm ci --ignore-scripts` 로 낮추거나 sharp를 optional로 분리하는 방법이 있다
- 배포 자동화, 링크 체커, Lighthouse CI는 계획서 Phase 3-2에 남아 있다

---

## 10. `CLAUDE.md` 작성

**변경 파일**: `CLAUDE.md` (신규)

### 무엇을

빌드 파이프라인, 디렉터리 구조, 새 글 작성 절차, mixin 목록, 정합성 검사 항목, 커밋 규칙을 한 파일에 정리했다.

### 왜

루트 `README.md` 가 링크 한 줄뿐이었다. 어디를 편집해야 하는지(`pugs/` 이고 `posts/` 가 아니라는 것), `posts.json` 등록이 필수라는 것, `posts/` 에 pug 소스 없는 파일이 왜 355개나 있는지 — 전부 코드를 읽어야만 알 수 있었다.

`Repositories/iroiro-downloader` 와 `usb-tether` 는 이미 각자 `CLAUDE.md` 를 갖고 있는데 정작 블로그 본체에는 없었다.

### 담은 내용

- **요구 환경** — Node 24+, `d2` 바이너리, TS 6 고정 방침
- **명령** — `build` / `check` / `typecheck`, `node source/build.ts new` 의 증분 렌더
- **디렉터리 표** — 소스와 생성물 구분
- **새 글 쓰기 3단계** — `posts.json` 등록을 빠뜨리면 어디에도 안 나온다는 점 명시
- **mixin 표** — `+post`, `+codeBtn`, `+asCode` 등 13개
- **빌드 동작** — 워커 3종 작업(pug/이미지/d2)과 이미지 변환 규격
- **`posts/` 직접 편집이 허용되는 두 경우** — 리다이렉트 스텁, 보존 대상
- **정합성 검사 E1~E7 / W1~W2 요약**
- **커밋 규칙** — 기존 이력의 Conventional Commits + 한국어 본문
- **주의** — `.gitattributes` CRLF 정규화, URL 상수 관리, 분석 도구 부재

### 리뷰 포인트

- 사실관계가 코드와 어긋나는 부분이 있는지 확인 필요. 특히 이미지 변환 규격과 `category` 표기 규칙은 코드·데이터에서 읽어낸 것이라 의도와 다를 수 있다
- `README.md` 는 그대로 두었다. 방문자용 소개와 개발자용 문서는 성격이 달라 분리해두는 편이 낫다고 판단했다

---

> **여기부터는 커밋 1~10에 대한 독립 리뷰에서 나온 지적을 반영한 것이다.**
> 리뷰는 별도 에이전트가 코드를 직접 실행·대조해 수행했다.

## 11. 타입 검사 범위에서 `Repositories` 제외

**변경 파일**: `tsconfig.json`

### 무엇을

```diff
-    "include": ["**/*"]
+    "include": ["**/*"],
+    "exclude": ["node_modules", "Repositories", "posts", "imgs-generated", "d2"]
```

### 왜 — CI가 첫 실행에서 반드시 실패하는 상태였다

`include: ["**/*"]` 에 `exclude` 가 없어 `Repositories/` 하위 `.ts` 8개가 검사 대상에 들어간다. 실제로 돌려보면 오류 4건이 난다.

```
Repositories/Node/test-231114/src/module/a.test.ts(2,20): TS2307 Cannot find module 'vitest'
Repositories/Node/test-231114/src/module/b.test.ts(2,20): TS2307 Cannot find module 'vitest'
Repositories/Node/test-231114/src/module/b.test.ts(4,10): TS1484 'C' is a type and must be imported using a type-only import
Repositories/Node/test-231114/src/module/b.ts(1,26): TS2307 Cannot find module 'lru-cache'
```

`vitest` 와 `lru-cache` 는 그 중첩 프로젝트의 의존성이라 루트 `npm ci` 로는 절대 설치되지 않는다. TS1484는 의존성과 무관하게 항상 난다.

**커밋 4의 "타입 검사가 이제 통과한다"는 서술은 틀렸다.** 당시 검증은 `source/build.ts` 하나만 대상으로 한 것이었고, 저장소 전체 설정으로 돌린 것이 아니었다. 커밋 9에서 추가한 CI의 `npm run typecheck` 단계는 그대로 두면 첫 실행에서 실패했을 것이다.

`Repositories/` 는 블로그 본문이 참조하는 예제 프로젝트 모음으로 각자 독립된 의존성과 빌드 설정을 가진다. 루트 tsconfig의 검사 대상이 아니다. `posts`, `imgs-generated`, `d2` 는 빌드 산출물이라 함께 뺐다.

### 검증

실제 `Repositories/**/*.ts` 8개를 격리 환경에 복사해 TS 6.0.3으로 대조했다.

| 설정 | 결과 |
|---|---|
| `exclude` 없음 (기존) | 오류 **4건** |
| `exclude` 적용 (수정 후) | 오류 **0건** |

---

## 12. 보존 대상 구 HTML 38건의 죽은 UA 태그 제거

**변경 파일**: `tools/strip-ua.mjs` (신규), `posts/**` 38개

### 무엇을

커밋 5는 `skeleton.pug` 에서 UA 태그를 뺐지만, **pug 소스가 없어 재렌더되지 않는 보존 대상 HTML 38건에는 그대로 남아 있었다.** 이번에 제거했다.

### 왜

하필 검색엔진에 색인되어 있어서 보존하기로 한 페이지들이다. 실제 방문 트래픽이 남아 있을 가능성이 가장 큰 쪽인데, 방문할 때마다 아무것도 수집하지 않는 `googletagmanager.com` 요청만 발생하고 있었다.

커밋 5의 메시지와 로그 5절이 "모든 페이지"라고 서술한 것은 부정확했다.

### 어떻게 — 안전장치

생성물이 아닌 파일을 스크립트로 일괄 편집하는 작업이라 안전장치를 여러 겹 두었다.

- `tools/preserved-orphans.json` 에 선언된 47건만 대상으로 한다
- UA 블록 정규식이 매칭되지 않으면 건너뛰고 보고한다
- 제거 후 남은 길이 차이가 정확히 블록 길이와 같은지 확인
- `before.replace(block, '') === after` 로 블록 외 변경이 없음을 확인
- 위 검사를 통과한 파일만 기록한다
- `--dry-run` 지원

### 검증

| 실행 | 결과 |
|---|---|
| dry-run | 제거 38 / 원래 없음 9 / 건너뜀 0 |
| 실행 | 제거 38 / 원래 없음 9 / 건너뜀 0 |
| 재실행 (멱등성) | 제거 0 / 원래 없음 47 / 건너뜀 0 |

- `posts/**` 전체와 `index.html` 에서 `UA-143098403-1` **잔존 0건**
- 변경된 38개 파일이 전부 보존 목록 안에 있음을 확인 (목록 밖 0건)
- diff: 38 files changed, 38 insertions(+), 175 deletions(-) — UA 블록만 사라졌다
- 정합성 검사 재실행: 오류 0건

---

## 13. 홈페이지 구조화 데이터를 `WebSite` 로 분리

**변경 파일**: `source/skeleton.pug`, `index.html`, `posts/**` 262개(재렌더)

### 무엇을

`index.pug` 도 `+post` mixin을 쓰기 때문에 홈페이지가 개별 기술 문서와 똑같은 타입으로 나가고 있었다.

```diff
-<meta property="og:type" content="article">
-{"@type":"TechArticle","headline":"Blog","dateModified":"..."}
+<meta property="og:type" content="website">
+{"@type":"WebSite","name":"Blog"}
```

### 왜

커밋 7에서 메타데이터를 넣을 때 홈을 따로 생각하지 않았다. 홈은 문서가 아니라 사이트 자체이므로 `og:type` 은 `website`, schema.org 타입은 `WebSite` 여야 한다. 잘못된 타입의 구조화 데이터는 넣지 않느니만 못하다.

### 어떻게

mixin API를 늘리지 않고 `pageUrl` 이 오리진 루트와 같은지로 자동 판별한다.

```pug
- const isHome = url !== '' && url === origin + '/'
```

`WebSite` 스키마는 `headline` 과 `dateModified` 를 쓰지 않으므로, 타입에 따라 필드 구성을 나눴다.

| | 홈 | 문서 |
|---|---|---|
| `og:type` | `website` | `article` |
| `@type` | `WebSite` | `TechArticle` |
| 제목 필드 | `name` | `headline` |
| `dateModified` | 없음 | 있음 |

### 검증

- 263개 렌더 성공, 실패 0
- 홈: `og:type=website`, `@type=WebSite`, `name` 필드 확인
- 문서: `og:type=article`, `@type=TechArticle`, `headline` + `dateModified` 확인
- `posts/**` 전체 분포 — `TechArticle` 262건, `WebSite` 0건

---

## 14. 정합성 검사 보완 + 그로 인해 드러난 코드 버튼 오류 수정

**변경 파일**: `tools/check-integrity.mjs`, `pugs/dev/JVM/lombok.pug`, `posts/dev/JVM/lombok.html`

### 검사 보완

| 항목 | 기존 | 수정 |
|---|---|---|
| **E5** | `meta refresh` 하나만 파싱해 검증. canonical·JS가 엇갈려도 통과 | **3중 신호를 모두 파싱해 서로 일치하는지 검사** |
| **E6** | `/Repositories/...` 문자열을 전부 긁어 오탐 발생. `existsSync` 라 디렉터리도 통과 | **`+codeBtn` 호출만 추출**, 디렉터리면 오류 |
| 요약 | `보존 47` 이 새 고아까지 포함해 집계 | `보존 47, 새 고아 0` 으로 분리 |
| E7 | 홈 URL(`origin/`)을 사이트맵에 넣으면 오류 | 홈 URL 허용 |

E6의 오탐은 이런 것이었다.

```pug
+asA('https://github.com/Dong-gi/Dong-gi.github.io/tree/master/Repositories/Node/test-231114')
```

GitHub 트리 링크인데 정규식이 `/Repositories/...` 부분을 코드 참조로 잡아 "디렉터리"라고 경고했다. `+codeBtn('...')` 과 `+codeBtn({ path: '...' })` 두 호출 형태만 추출하도록 바꿔 참조 수가 805 → 804로 정확해졌다.

### 검사가 잡아낸 실제 버그

보완한 E6가 `pugs/dev/JVM/lombok.pug` 에서 진짜 오류를 찾아냈다.

```pug
h1 @Getter, @Setter
ol
    +codeBtn('/Repositories/Eclipse', 'java')   ← 디렉터리를 가리킴
```

경로가 잘려 있었다. 버튼을 눌러도 아무것도 열리지 않는다. `LombokGetterAndSetterExample.java` 로 고쳤다.

같은 파일을 확인하다 두 가지를 더 찾았다.

- **`@CleanUp` 절이 `LombokNonNullExample.java` 를 열고 있었다.** 바로 위 `@NonNull` 절에서 복사한 흔적이다. `LombokCleanupExample.java` 로 고쳤다 — 파일은 있는데 아무 데서도 참조되지 않고 있었다
- **`@Log` 절에만 코드 버튼이 없었다.** 다른 12개 절은 전부 있다. `LombokLogExample.java` 를 연결했다

이 두 건은 경로가 존재하므로 검사로는 잡히지 않는다. 사람이 봐야 하는 종류다.

### 검증

- 회귀 테스트 — 스텁의 JS 대상만 다른 값으로 바꾸자 E5가 3중 신호 불일치로 검출
- 회귀 테스트 — codeBtn을 디렉터리로 바꾸자 E6가 검출
- 수정 후: 오류 0건, 경고 11건. 리다이렉트 스텁 무결성 통과

---

## 15. 부정확한 서술 정정, `Post.category` 타입 수정

**변경 파일**: `CLAUDE.md`, `source/build.ts`, `docs/modernization-log.md`

리뷰에서 지적된 사실관계 오류를 코드와 대조해 바로잡았다.

### 1. `category` 다중 소속 표기 — **틀린 서술이었다**

`CLAUDE.md` 가 "`,` 로 다중 소속을 표현한다 (예: `기초 과목,책`)" 라고 적었으나, 실제 데이터를 세어보니 **쉼표를 포함한 category 문자열은 0건이고 다중 소속은 문자열 배열 7건**이다.

```json
{ "category": ["개발 자료/책", "책"], "file": "book/0/010.html" }
```

`source/default.js:378` 이 `Array.isArray(post.category)` 로 분기해 각 카테고리 노드에 문서를 복제한다. 문서를 따라 쉼표로 쓰면 `기초 과목,책` 이라는 단일 노드가 생겨 목록이 깨진다.

같은 오해가 코드에도 있었다. `build.ts` 의 `interface Post` 가 `category: string` 으로 선언되어 있어 실제 데이터(`string | string[]`)와 불일치했다. `posts.json` 을 `createRequire` 로 읽어 tsc가 잡지 못하던 기존 문제다.

```diff
 interface Post {
-    category: string;
+    /** 단일 소속은 문자열, 다중 소속은 문자열 배열. 계층은 '/' 로 표현한다. */
+    category: string | string[];
```

### 2. mixin 표가 불완전했다

`skeleton.pug` 에 **14개**가 있는데 표에는 10개만 있었고 커밋 메시지는 "13개"라고 했다. 누락됐던 `+goto`, `+pos`, `+hoverTemplate`, `+w3button` 을 추가하고 개수를 명시했다.

`+asCode` 를 "인라인 코드 블록"이라 적은 것도 고쳤다. 블록 코드이고, 인라인은 `+asInlineCode` 다.

### 3. `node source/build.ts new` 설명 보완

10분(600000ms) 조건은 맞지만 `index.pug` 는 `build.ts:227` 에서 **무조건** 렌더된다. "수정된 pug만"은 부정확했다.

### 4. `dateModified` 의 머신 간 재현성 문제 명시

새로 클론한 저장소는 모든 파일 mtime이 체크아웃 시각이므로, 다른 머신에서 빌드를 한 번만 돌려도 263개 페이지의 날짜가 그날로 덮이고 대량 diff가 생긴다. 커밋 7의 로그에는 경합 회피 근거만 적고 이 부작용을 언급하지 않았다.

`CLAUDE.md` 주의 항목과 로그 7절에 경고를 넣었다. **git 커밋 날짜 기반으로 바꾸는 것은 후속 과제로 남긴다.**

### 5. 숫자 불일치

로그 표 2행이 "250개 URL"이라 적었으나 본문과 실제는 251개다.

### 남은 후속 과제

리뷰에서 나왔으나 이번에 고치지 않은 것들이다.

| 항목 | 사유 |
|---|---|
| `dateModified` 를 git 커밋 날짜 기반으로 | 대량 리팩터링 커밋 12건 때문에 단순 `git log -1` 로는 부정확. 별도 설계 필요 |
| 리다이렉트 스텁이 쿼리스트링을 버림 | `location.hash` 만 이어붙인다. 이 사이트는 쿼리스트링을 쓰지 않아 영향 없음 |
| 스텁 15개가 `not-registered/` 로 감 | `posts.json` 미등록 문서 처리 방침이 정해져야 결론이 남 (W1 경고 11건과 같은 사안) |
| `index.pug` 가 `nvm install 20` 과 없는 `npm run build-all` 을 안내 | 홈 본문 내용이라 Phase 1(포스트 최신화)에서 다룬다 |
| `posts/**/index.html` 형태 글이 생기면 canonical과 사이트맵이 갈림 | 현재 해당 문서 0건 |

---

## 16. 최종 검증, `gradle.html` 날짜 재생성

**변경 파일**: `posts/dev/gradle.html`, `docs/modernization-log.md`

### 검증 결과

| 검사 | 결과 |
|---|---|
| `tsc --noEmit` (TS 6.0.3) | 오류 0건 |
| `npm run check` | 오류 0건 / 경고 11건 |
| 리다이렉트 스텁 무결성 (308건) | 통과 |
| 렌더 드리프트 (263개) | 전부 일치 |
| 미커밋 변경 | 없음 |

### `dateModified` 취약성이 실제로 드러난 사례

커밋 14의 회귀 테스트에서 `pugs/dev/gradle.pug` 를 잠시 수정했다 되돌렸는데, 내용은 원상 복구됐지만 **mtime이 바뀌어 `dateModified` 만 다른 드리프트가 남았다.**

```
커밋본: "dateModified":"2026-05-16T04:19:37.807Z"
재렌더: "dateModified":"2026-08-01T10:35:21.479Z"
dateModified 제외 시 동일? true
```

mtime을 원래 값으로 되돌리려 했으나 이 환경의 파일시스템이 초 단위로 절삭해 밀리초(`.807`)를 복원할 수 없었다. 내용은 git과 동일하고 실제 수정 시각도 초 단위까지 정확하므로, `.000` 으로 재생성해 커밋했다.

**커밋 15에서 문서로만 경고했던 문제가 하루도 안 돼 실물로 나타난 셈이다.** `dateModified` 를 git 커밋 날짜 기반으로 바꾸는 후속 과제의 우선순위를 높일 근거가 된다.

---

## 17. 미참조 예제 프로젝트 4개 삭제

**변경 파일**: `Repositories/` 4개 디렉터리 삭제, `CLAUDE.md`

### 삭제한 것

`pugs/`·`index.pug`·`source/` 어디에서도 참조하지 않는 프로젝트다.

| 경로 | 파일 수 | 비고 |
|---|---:|---|
| `Eclipse/annotation-processing2` | 5 | 1·3은 참조되는데 2만 미참조 |
| `Node/redis-start` | 2 | |
| `STS/Skeleton-NonSpringBoot` | 13 | |
| `Single/teamcity` | 2 | `docker-compose.yml` 내용이 `not-registered/CI-CD.pug` 에 인라인으로 그대로 있어 정보 손실 없음 |

122개 → 118개.

### 삭제하지 않은 것 — 판단 착오 정정

처음에 `JavaScript/Chrome Proxy Extension`(34개 파일)도 미참조라 삭제 대상에 넣었으나, **실제로 사용 중인 도구라는 지적을 받고 되돌렸다.**

"pug에서 참조하지 않음"을 삭제 근거로 삼은 것이 잘못이었다. `Repositories/` 에는 블로그 예제가 아닌 독립 산출물도 섞여 있다. `iroiro-downloader` 와 `usb-tether` 도 같은 이유로 처음부터 제외했었는데, 그 판단 기준을 `Chrome Proxy Extension` 에는 적용하지 못했다. 자체 `CLAUDE.md` 유무로만 갈랐던 탓이다.

같은 실수가 반복되지 않도록 `CLAUDE.md` 에 "`Repositories/` 정리 시 주의" 절을 추가해 세 항목을 명시했다.

### 검증

- 삭제 후 정합성 검사: 오류 0건 (E6 코드 참조 804건 전부 유효)
- `Single/teamcity` 는 문서에 인라인 YAML이 있는지 대조 후 삭제

---

## 18. 커밋된 컴파일 산출물 25개 제거

**변경 파일**: `Repositories/STS/{rabbitmq-tutorial,TOTP-example}/bin/` 삭제, `.gitignore`, `docs/modernization-plan.md`

### 무엇을

Eclipse/STS가 만드는 `bin/` 출력 디렉터리가 통째로 커밋되어 있었다.

| 프로젝트 | 파일 |
|---|---|
| `STS/rabbitmq-tutorial` | `.class` 21개 + `application.properties` + `logback.xml` |
| `STS/TOTP-example` | `.class` 2개 |

`bin/main/application.properties` 와 `bin/main/logback.xml` 은 `src/main/resources/` 의 복사본이다. `diff` 로 동일함을 확인하고 지웠으므로 정보 손실은 없다.

`.gitignore` 에 재발 방지 규칙을 넣었다.

```gitignore
# Eclipse/STS 가 만드는 컴파일 산출물 디렉터리.
Repositories/**/bin/

# 머신 종속 파일 (Android SDK 절대 경로 등)
local.properties
```

### 계획서의 잘못된 서술 정정

`docs/modernization-plan.md` Phase 2-4에 "`local.properties` 와 `.idea/` 가 커밋되어 있다"고 적었는데 **사실이 아니었다.**

`git ls-files` 로 실제 추적 파일을 확인하니 두 경로 모두 0건이다. 작업 트리에 파일이 보인다는 이유로 커밋된 것이라 단정한 오류다. 계획서를 실측값 기준 표로 바꿨다.

> 디스크에 있는 것과 git이 추적하는 것은 다르다. `find` 가 아니라 `git ls-files` 로 확인해야 한다.

### 검증

- `git ls-files Repositories` 2,063건 중 산출물 패턴 매칭 → 수정 전 25건, 수정 후 0건
- `.gitignore` 규칙이 `Chrome Proxy Extension` 등 다른 프로젝트에 영향을 주지 않는지 확인 (해당 경로에 `bin/` 없음)

---

## 19. Gradle 7에서 제거된 의존성 설정 158줄 교체

**변경 파일**: `Repositories/**/build.gradle` 25개, `pugs/dev/gradle.pug`, `posts/dev/gradle.html`

### 무엇을

| 기존 | 변경 | 줄 수 | 파일 |
|---|---|---:|---:|
| `compile` | `implementation` | 134 | 25 |
| `testCompile` | `testImplementation` | 24 | 24 |

### 왜

`compile` 과 `testCompile` 은 Gradle 4.10에서 deprecated, **Gradle 7에서 제거**됐다. 현재 예제들이 Gradle 5/6에 묶여 있는 이유 중 하나다. Phase 2의 Gradle 9.3.1 이관을 하려면 어차피 먼저 걷어내야 한다.

`implementation` 은 Gradle 3.4부터 있으므로 **현재의 Gradle 5/6에서도 그대로 동작한다.** 즉 이 커밋만으로 깨지는 것은 없고, 앞으로의 이관 장벽만 낮아진다.

`api` 가 아니라 `implementation` 을 택한 이유는 이 프로젝트들이 라이브러리가 아니라 독립 실행 예제·웹앱이기 때문이다. 소비자에게 전이 노출할 대상이 없다.

### 건드리지 않은 것

`providedCompile`(76건)은 `war` 플러그인이 제공하는 설정으로 Gradle 7 이후에도 유효하다. `annotationProcessor`, `compileOnly` 도 마찬가지다. 치환 스크립트가 이들을 훼손하지 않았는지 개수로 확인했다.

### 문서의 잘못된 예시도 갱신

`pugs/dev/gradle.pug` 의 dependencies 예시가 제거된 설정을 그대로 가르치고 있었다. 현행 설정 전체로 다시 썼다.

```diff
-providedCompile '' // 빌드 시 사용 && 출력에 미포함
-compile ''         // 빌드 시 사용 && 출력에 포함
-testCompile 'junit:junit:4.11'     // 테스트에만 필요
+compileOnly ''            // 컴파일에만 사용, 런타임/출력에 미포함
+providedCompile ''        // war 플러그인. 컨테이너가 제공하므로 출력에 미포함
+implementation ''         // 빌드 시 사용 && 출력에 포함. 소비자에게 노출 안 됨
+api ''                    // java-library 플러그인. 소비자에게 전이 노출
+runtimeOnly ''            // 런타임에만 필요
+testImplementation 'junit:junit:4.13.2'   // 테스트에만 필요
+annotationProcessor ''    // 애너테이션 프로세서
```

### 검증

- 제거된 설정(`compile`/`testCompile`/`runtime`/`testRuntime`) 잔존 **0줄**
- `providedCompile` 76건 그대로 보존
- `implementation`/`testImplementation` 221줄
- gradle.html 재렌더 후 정합성 검사 오류 0건

> ⚠ **빌드로는 검증하지 못했다.** 이 샌드박스에 `gradle`·`javac` 가 없다. 설정 이름 치환은 Gradle 공식 마이그레이션 가이드의 1:1 대응이고 문법 변화가 없어 위험이 낮다고 판단했으나, 실제 빌드 확인은 로컬에서 한 번 필요하다.

---

## 20. 구버전 접기 `+legacy` mixin 추가

**변경 파일**: `source/skeleton.pug`, `source/default.css`, `CLAUDE.md`

### 무엇을

낡은 문서를 갱신할 때 쓸 표기를 먼저 정의했다. **Phase 1(포스트 최신화)의 전제**다.

```pug
mixin legacy(version, note)
    details.legacy
        summary
            = `구버전 기록 — ${version}`
            if note
                |  (
                = note
                |)
        block
```

렌더 결과.

```html
<details class="legacy">
  <summary>구버전 기록 — Python 3.8 (2024-10-07 지원 종료)</summary>
  ...기존 본문...
</details>
```

### 왜 이 방식인가

문서를 최신화하는 방법은 세 가지가 있다.

1. 옛 내용을 지우고 새로 쓴다 — 구버전 환경을 쓰는 방문자가 잃는다. diff도 커진다
2. 옛 내용 옆에 새 내용을 나란히 둔다 — 문서가 두 배로 길어지고 뭐가 현행인지 헷갈린다
3. **옛 내용을 접고 그 위에 새 내용을 올린다** ← 택함

3번은 **기존 본문을 들여쓰기만 하면 되므로 diff가 깨끗하고 되돌리기 쉽다.** 검색엔진은 `details` 안의 내용도 색인하므로 기존 유입도 유지된다.

### 스타일

`default.css` 에 4줄을 추가했다. 기존 `#contents details` 규칙을 물려받되 회색 계열로 낮춰 현행 내용과 시각적으로 구분한다.

```css
#contents details.legacy{border-left-color:rgba(0,0,0,.25);background:rgba(0,0,0,.02)}
#contents details.legacy>summary{color:#666;font-size:0.9em}
#contents details.legacy[open]>summary{margin-bottom:0.75rem}
```

### `CLAUDE.md` 에 문서 최신화 규칙 추가

`+legacy` 사용법과 함께 **"제목에는 버전을 넣지 않는다"** 는 규칙을 명시했다. 제목을 바꾸려다 URL까지 바꾸면 리다이렉트가 또 필요해진다. 기준 버전은 본문 상단에 적는다.

### 검증

- `note` 있는 경우와 없는 경우 두 가지로 렌더해 `summary` 문구 확인 (없을 때 빈 괄호가 남지 않음)
- **mixin 추가가 기존 263개 산출물에 아무 영향을 주지 않음을 드리프트 검사로 확인**

---

## 21. 제목에 박제된 버전 13건 정리

**변경 파일**: `pugs/**` 13개, `posts/**` 13개, `source/posts.json`, `source/default.css`, `tools/detitle-version.mjs` (신규)

### 무엇을

제목에서 버전을 빼고 본문 상단의 기준 버전 표기로 옮겼다.

| 기존 제목 | 새 제목 | 기준 버전 표기 |
|---|---|---|
| `Built-in 목록 2.3.28` | `FreeMarker Built-in 목록` | FreeMarker 2.3.28 |
| `프로그래밍 가이드 2.3.28` | `FreeMarker 프로그래밍 가이드` | 〃 |
| `템플릿 작성 가이드 2.3.28` | `FreeMarker 템플릿 작성 가이드` | 〃 |
| `XML 처리 가이드 2.3.28` | `FreeMarker XML 처리 가이드` | 〃 |
| `Python 3.8` | `Python` | Python 3.8 |
| `Python 3.8 데이터 모델` | `Python 데이터 모델` | 〃 |
| `JDK16 java.base 모듈` | `java.base 모듈` | JDK 16 |
| `JDK16 java.net.http 모듈` | `java.net.http 모듈` | 〃 |
| `Guava 30.1` | `Guava` | Guava 30.1 |
| `Apache Commons Lang 3.9` | `Apache Commons Lang` | 3.9 |
| `Apache Commons Collections 4.4` | `Apache Commons Collections` | 4.4 |
| `Apache Commons Math 3.6.1` | `Apache Commons Math` | 3.6.1 |
| `Apache Commons RNG 1.2` | `Apache Commons RNG` | 1.2 |

FreeMarker 4종은 제목만으로는 무슨 도구인지 알 수 없어 `FreeMarker` 를 앞에 붙였다.

```pug
+post({
    title: 'Python',
    description: 'Python 시작하기',
})

    p.version-note Python 3.8 기준입니다.

    h1 설치
```

### 왜

**버전을 갱신할 때마다 제목을 고치는 구조는 유지되지 않는다.** 실제로 FreeMarker 문서 4종은 2020년 이후 제목의 `2.3.28` 이 한 번도 갱신되지 않았다. 기준 버전을 본문으로 옮기면 내용 갱신과 같은 자리에서 함께 고치게 된다.

제목을 바꿔도 **URL(파일 경로)은 그대로**라 리다이렉트가 필요 없다. 다만 `source/posts.json` 의 `title` 도 같이 고쳐야 목록·검색에 반영된다.

### 어떻게

`tools/detitle-version.mjs` 로 pug 헤더 교체 + 본문 상단 삽입 + `posts.json` 갱신을 한 번에 처리했다. 멱등이며 `--dry-run` 을 지원한다.

**CRLF 처리에 주의했다.** 이 저장소의 작업 트리는 CRLF다. `posts.json` 을 LF로 다시 쓰면 251개 항목이 통째로 diff에 잡힌다. 원본의 줄바꿈 방식을 감지해 유지하도록 했고, 결과적으로 **`posts.json` diff는 제목 13줄뿐**이다.

`default.css` 에 `p.version-note` 스타일을 추가했다.

### 검증

- dry-run → 13건 처리 예정 / 건너뜀 0 확인 후 적용
- 재실행 시 13건 전부 "이미 처리됨" (멱등)
- `posts.json` diff가 제목 13줄로 한정됨을 확인
- 13개 재렌더 후 `<title>`, `version-note`, JSON-LD `headline` 반영 확인
- 사이트맵은 파일 경로 기반이라 변경 없음 (확인함)
- 정합성 검사 오류 0건

### 리뷰 포인트

- 새 제목이 목록에서 다른 문서와 헷갈리지 않는지. 특히 `Python` 은 `dev/python/` 아래 다른 문서들과 나란히 놓인다
- 기준 버전 문구를 "…기준입니다."로 통일했다. 다른 표현을 원하시면 스크립트의 `basis` 값만 고치면 된다

---

## 22. `Java 버전` 문서를 Java 26까지 갱신

**변경 파일**: `pugs/dev/JVM/version.pug` (212 → 482줄), `posts/dev/JVM/version.html`

### 갱신 전 상태

문서가 Java 17에서 끝나는데, 그 내용이 이게 전부였다.

```pug
h1 Java 17
ol
    li 21년 9월 출시 예정
```

**Java 17이 출시된 지 5년, Java 26까지 나온 시점**이었다. "버전별 추가사항"이라는 문서 목적상 최신성이 곧 존재 이유이므로 계획서에서 우선순위 7위에 올렸으나, 규모가 작고 조사만 하면 되는 작업이라 먼저 처리했다.

### 추가한 것

Java 17 ~ 26의 **정식화(final)된 기능만** 담았다. Preview / Incubator / Experimental 단계는 제외했다. 이 구분을 지키지 않으면 "Java 19에 가상 스레드가 있다" 같은 흔한 오해를 문서가 재생산하게 된다.

| 버전 | 다룬 내용 |
|---|---|
| 17 (LTS) | Sealed Classes, JDK 내부 강한 캡슐화, Enhanced PRNG, Always-Strict FP, macOS/AArch64 |
| 18 | UTF-8 by Default, Simple Web Server, `@snippet` |
| 19, 20 | **정식화 기능 없음** — 전부 preview/incubator였다는 사실 자체를 명시 |
| 21 (LTS) | Virtual Threads, Pattern Matching for switch, Record Patterns, Sequenced Collections, Generational ZGC |
| 22 | FFM API(JNI 대체), Unnamed Variables, 다중 파일 소스 실행 |
| 23 | Markdown Javadoc, ZGC 세대 모드 기본값 |
| 24 | **JEP 491 가상 스레드 피닝 해소**, AOT Class Loading, Stream Gatherers, Class-File API |
| 25 (LTS) | Scoped Values, Compact Source Files, Module Import, Flexible Constructor Bodies |
| 26 | HTTP/3, Applet API 제거 완료 |

버전마다 **제거·폐기 항목을 따로 표기**했다. Java 8/11에서 올라올 때 실제로 발목을 잡는 것은 새 기능이 아니라 이쪽이기 때문이다. Security Manager 영구 비활성화(24), `sun.misc.Unsafe` 메모리 접근 폐기(23·24), 32비트 x86 제거(24·25) 등.

### 문서 앞에 "어떤 버전을 쓸 것인가" 절 추가

버전 목록만 있으면 정작 독자의 질문("그래서 뭘 써야 하나")에 답하지 못한다. Oracle 지원 로드맵을 근거로 절을 하나 새로 넣었다.

- **2026년 9월부터 Oracle JDK 21 업데이트가 무상 NFTC에서 OTN 라이선스로 전환**된다 (한 달 남음)
- Java 17의 Oracle Premier Support도 **2026년 9월 종료**
- 가상 스레드를 본격적으로 쓸 거라면 21이 아니라 25여야 한다. 21~23에서는 `synchronized` 블록이 가상 스레드를 캐리어에 고정시켰고, 이게 JEP 491(Java 24)에서야 해소됐다

### 코드 예제 6개

record pattern + switch, virtual thread, sequenced collections, unnamed variables, stream gatherers, scoped values, compact source file.

### 부수 수정

- 기존 JEP 링크 18건이 `openjdk.java.net` 을 가리키고 있었다. 현재는 `openjdk.org` 로 리다이렉트되므로 전부 교체했다
- Java 11 절의 `+asCode('java') ... -> x.process(y);` 에서 `>` 가 이스케이프되지 않은 채로 출력되고 있었다. `&gt;` 로 고쳤다 (기존 오류)

### 검증

- 렌더 성공, `h1` 17개, JEP 링크 79개, 구 도메인 잔존 0
- **코드 블록 18개 전부 부등호 이스케이프 검사 통과** (이 과정에서 위의 기존 오류를 발견)
- 정합성 검사 오류 0건

### 출처

조사는 `openjdk.org` 의 릴리스 페이지(`/projects/jdk/17` ~ `/26`)와 개별 JEP 문서, Oracle Java SE 지원 로드맵, Adoptium 지원표를 대조해 수행했다.

> JEP 506·512·519 원문은 fetch가 빈 응답을 반환해 직접 읽지 못했다. 세 건 모두 JDK 25 릴리스 페이지에 preview 표기 없이 올라와 있는 것으로 정식화를 확인했고 세부 내용은 2차 자료로 교차 검증했다.

### 코드 예제 실행 검증 (커밋 23에서 추가)

샌드박스에 **JDK 25.0.3을 설치해 문서의 예제 7개를 전부 실제로 실행했다.** 전부 통과했다.

| 예제 | JEP | 실행 결과 |
|---|---|---|
| Compact Source File + Module Import | 512, 511 | `[1, 2, 3]` |
| Record Pattern + sealed switch | 440, 441 | `12.0` |
| Sequenced Collections | 431 | `[z, a, b, c] / c / [c, b, a, z]` |
| Stream Gatherers | 485 | `[[1, 2, 3], [4, 5, 6], [7]]` |
| Scoped Values | 506 | `alice` |
| Virtual Threads 1만 개 | 444 | 1,073ms에 완료 |
| Unnamed Variables | 456 | `*** x=7` |

**2차 자료 기준이라 재확인이 필요하다고 적었던 JEP 512의 `IO.println` 호출 형태가 정확했음이 확인됐다.** `import module java.base;` 와 클래스 선언 없는 `void main()` 조합도 그대로 동작한다.

---

## 25. 예제 소스 132개의 Java 25 호환성 측정

**변경 파일**: `docs/modernization-plan.md`

### 무엇을

`Repositories/` 의 `.java` 677개 중 **외부 의존성 없이 표준 라이브러리만 쓰는 132개를 JDK 25.0.3으로 전수 컴파일**했다. Phase 2를 시작하기 전에 위험이 어디에 있는지 재는 것이 목적이다.

| 검사 | 결과 |
|---|---|
| 디렉터리 단위 `javac 25` | 129개 통과, 3개 실패 |
| 실패 3건 재검증 | **전부 격리 컴파일 방식의 한계** |
| 실제 Java 25 비호환 | **0건** |

### 실패 3건의 정체

세 건 모두 "표준 라이브러리만 쓰는 파일"이라는 표본 추출 기준 때문에 생긴 것이었고, Java 25와 무관했다.

- `Eclipse/TestModule1` — 다른 프로젝트의 모듈 `second` 를 `requires` 한다. `--module-path` 를 주니 통과
- `Eclipse/TestModule2` — 표본이 `hello` 패키지의 일부만 담고 있었다. 프로젝트 통째로 컴파일하니 통과
- `STS/app2` 의 `Anno30DataEditor` — 형제 클래스 `Anno30Data` 가 표본에서 빠져 있었다 (`var d = new Anno30Data();` 에서 심볼 미해결)

### 이 측정이 말해주는 것

**Phase 2의 위험은 Java 언어·표준 라이브러리 쪽이 아니라 빌드 도구와 프레임워크 버전 쪽에 몰려 있다.** Java 8~15 시절에 쓴 코드가 Java 25에서 그대로 컴파일된다는 것은, 소스를 손대지 않고 툴체인만 올리는 것으로 상당수가 해결될 수 있다는 뜻이다.

계획서 Phase 2-3에 이 측정 결과를 넣었다.

### 한계

표준 라이브러리만 쓰는 132개 한정이다. 나머지 545개는 Spring·Lombok 등 외부 의존성이 있어 이 환경에서 확인할 수 없다. `javax.*` → `jakarta.*` 전환처럼 프레임워크 쪽 변경은 완전히 별개 문제이며, 그쪽이 실제 작업량의 대부분일 것으로 **추측**된다.

---

> ## ⚠ 여기부터는 빌드로 검증하지 못한 변경이다
>
> 커밋 26 이후는 의존성 다운로드가 차단된 환경에서 **지식에 기반해 작성**한 것이다. 문법 오류나 버전 오타가 남아 있을 수 있다.
> 각 항목 끝의 **검증 방법**을 로컬에서 한 번씩 돌려주시면 된다. 전체 체크리스트는 문서 맨 아래에 모아두었다.

---

## 26. Android 7개를 compileSdk 36 / AGP 9로 이관

**변경 파일**: `Repositories/Android/Project01~07` 의 `settings.gradle`·`build.gradle`·`app/build.gradle`·`AndroidManifest.xml`·`gradle-wrapper.properties` (35개), `tools/migrate-android.mjs` (신규)

### 왜 지금인가

**Google Play가 2026-08-31부터 targetSdk 36을 요구한다.** 남은 기간이 한 달이다. 예제 프로젝트라 스토어에 올리지는 않겠지만, 블로그가 가르치는 설정이 스토어 정책에 못 미치는 상태로 남는 것은 곤란하다.

기존 상태는 7개 전부 동일했다 — AGP 7.1.1, Gradle 7.2, compileSdk/targetSdk 31, minSdk 19(Project05만 21), `jcenter()`.

### 적용한 변경

| 대상 | 기존 | 변경 | 근거 |
|---|---|---|---|
| AGP | 7.1.1 | 9.0.0 | 6개월 룰 적격 (2026-01-15) |
| Gradle wrapper | 7.2 | 9.3.1 | AGP 9 요구 |
| `compileSdk` / `targetSdk` | 31 | 36 | Play 정책 |
| `minSdk` | 19 | 21 | androidx 1.7 계열이 21을 요구 |
| Java | 1.8 (일부만 지정) | 17 (전 프로젝트) | AGP 8+ 요구 |
| 저장소 | `jcenter()` | `mavenCentral()` | **JCenter 서비스 종료** |

구조적 변경 네 가지.

1. **`package` 속성 → `namespace`** — AGP 8부터 `AndroidManifest.xml` 의 `package` 속성이 제거되고 `build.gradle` 의 `namespace` 로 대체됐다
2. **`buildscript` + `classpath` → `plugins` DSL**
3. **저장소 선언을 `settings.gradle` 로 중앙화** — `pluginManagement` 와 `dependencyResolutionManagement`, `FAIL_ON_PROJECT_REPOS`
4. **`task clean(type: Delete)` → `tasks.register`**, `rootProject.buildDir` → `rootProject.layout.buildDirectory`

의존성 버전도 올렸다.

```
androidx.appcompat:appcompat               1.4.1   -> 1.7.0
androidx.constraintlayout:constraintlayout 2.1.3   -> 2.2.1
com.google.android.material:material       1.5.0   -> 1.12.0
androidx.test.ext:junit                    1.1.3   -> 1.2.1
androidx.test.espresso:espresso-core       3.4.0   -> 3.6.1
androidx.room:room-*                       2.4.1   -> 2.6.1
org.projectlombok:lombok                   1.18.22 -> 1.18.34
```

### 검증한 것

- `jcenter()` 잔존 **0건**
- `AndroidManifest.xml` 의 `package` 속성 잔존 **0건**
- 7개 × 5개 파일이 의도대로 바뀌었는지 육안 확인
- `BuildConfig` 사용처가 없어 AGP 8의 `buildFeatures.buildConfig` 기본값 변경에 영향받지 않음

### ⚠ 검증하지 못한 것

빌드를 돌려보지 못했다. `dl.google.com` 과 Maven Central이 차단돼 AGP·androidx를 내려받을 수 없다.

**AGP 9.0은 메이저 릴리스라 제가 모르는 파괴적 변경이 있을 수 있다.** 의심 지점 셋.

1. **AGP 9.0의 Groovy DSL 지원 범위** — Kotlin DSL(`build.gradle.kts`)을 강제하거나 Groovy DSL 일부 문법을 제거했을 가능성. 참고로 같은 저장소의 `Repositories/usb-tether/android` 는 이미 Kotlin DSL + compileSdk 37 이다
2. **`minifyEnabled` / `proguardFiles` 표기**가 AGP 9에서 바뀌었을 가능성
3. **AGP 9가 요구하는 최소 JDK** — 17로 잡았으나 21 이상일 수 있다

**검증 방법**

```bash
cd Repositories/Android/Project01
./gradlew assembleDebug --warning-mode all
```

한 개만 통과시켜 본 뒤 문제가 있으면 `tools/migrate-android.mjs` 의 상수를 고쳐 7개에 다시 적용하면 된다. 스크립트는 멱등이 아니므로 재적용 전에 `git checkout -- Repositories/Android` 로 되돌릴 것.

### 손대지 않은 것

`Repositories/usb-tether/android` 는 이미 `compileSdk = 37`, Kotlin DSL 로 현행 상태라 제외했다.

---

## 27. .NET 17개를 net10.0으로 이관

**변경 파일**: `Repositories/VisualStudio/**/*.csproj` 17개

### 왜

**.NET 6은 2024-11-12에 지원이 종료됐다.** 이미 2년 가까이 지난 상태다. .NET 10은 2025-11-11 GA된 LTS로 6개월 룰에 적격하며, 2028-11-14까지 지원된다.

.NET 8과 9는 둘 다 2026-11-10에 종료되므로 지금 올릴 대상으로는 의미가 없다.

### 적용한 변경

| 대상 | 기존 | 변경 | 개수 |
|---|---|---|---:|
| 콘솔 앱 TFM | `net6.0` | `net10.0` | 9 |
| WPF 앱 TFM | `net6.0-windows` | `net10.0-windows` | 8 |
| `System.Configuration.ConfigurationManager` | 6.0.0 | 10.0.0 | 1 |
| `Newtonsoft.Json` | 13.0.1 | 13.0.3 | 1 |

`System.Configuration.ConfigurationManager` 는 .NET 버전과 함께 번호가 올라가는 패키지라 TFM과 반드시 같이 움직여야 한다.

### ⚠ 검증하지 못한 것

`dotnet` SDK도 NuGet도 이 환경에서 접근할 수 없다.

**손대지 않은 패키지 8종** — 현행 버전을 확신할 수 없어 그대로 두었다. 대부분 `netstandard2.0` 을 타깃하므로 net10.0에서도 복원은 될 것으로 **추측**하지만, 오래된 것들이라 확인이 필요하다.

| 패키지 | 현재 버전 |
|---|---|
| `MySql.Data` | 8.0.28 |
| `System.Data.SQLite` (+ Core / EF6 / Linq) | 1.0.115.5 |
| `Dapper` | 2.0.123 |
| `SSH.NET` | 2020.0.2 |
| `Npgsql` | 8.0.3 |
| `morelinq` | 3.3.2 |
| `Hardcodet.NotifyIcon.Wpf` | 1.1.0 |

**솔루션 파일은 건드리지 않았다.** `VisualStudio.sln` 이 `Visual Studio Version 17`(VS 2022)로 되어 있는데, .NET 10을 빌드하려면 더 최신 VS가 필요할 수 있다. 솔루션 버전 문자열을 잘못 고치면 IDE가 열지 못하므로 실제 환경에서 판단하는 편이 낫다.

**검증 방법**

```bash
cd Repositories/VisualStudio
dotnet build VisualStudio.sln
# 또는 개별 확인
dotnet build ConsoleApp9/ConsoleApp9.csproj   # 패키지 참조가 가장 많은 프로젝트
dotnet build WpfApp8/WpfApp8.csproj           # WPF + 패키지
```

복원이 실패하는 패키지가 나오면 `dotnet list package --outdated` 로 한 번에 확인할 수 있다.

---

## 28. Spring을 쓰지 않는 Gradle 프로젝트 6개를 Gradle 9.3.1 / Java 25로

**변경 파일**: `Repositories/Eclipse/annotation-processing3`, `Gradle/JUnit-Example`, `Gradle/project-01`, `Gradle/project-02`, `STS/netty-tutorial`, `STS/TOTP-example`

Spring 의존이 있는 31개는 프레임워크 이관과 묶어야 해서 커밋 30으로 미뤘다. 여기서는 순수 Java 프로젝트만 다룬다.

### 적용한 변경

| 항목 | 기존 | 변경 |
|---|---|---|
| Gradle wrapper | 5.4 / 6.3 / 6.4 / 7.4 | 9.3.1 |
| Java 지정 | `sourceCompatibility = JavaVersion.VERSION_12` 등 | `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }` |
| `jcenter()` | 사용 중 | `mavenCentral()` |

### Gradle 7·8에서 제거된 API 세 가지를 찾아 고쳤다

이것이 이번 커밋의 핵심이다. 단순 버전 상향이 아니라 **그대로 두면 Gradle 9에서 빌드가 실패하는 것들**이다.

1. **`apply plugin: 'maven'`** → `'maven-publish'`
   `maven` 플러그인은 **Gradle 7에서 제거**됐다. `annotation-processing3` 이 쓰고 있었다
2. **`mainClassName`** → `mainClass`
   `mainClassName` 은 **Gradle 8에서 제거**됐다. `JUnit-Example` 이 쓰고 있었다
3. **`maven { url "..." }`** → `maven { url = uri("...") }`
   Gradle 8에서 deprecated된 표기

`sourceCompatibility` 대신 toolchain을 쓴 이유는, toolchain이 있으면 **Gradle이 해당 JDK를 자동으로 내려받아 쓰기 때문에** 빌드 머신의 JDK 버전과 무관해지기 때문이다.

### 의존성 상향

```
org.projectlombok:lombok        1.18.6 / 1.18.12 / 1.18.22 -> 1.18.36
junit:junit                     4.12                       -> 4.13.2
org.junit.jupiter:junit-jupiter 5.6.0 / 5.8.2              -> 5.11.4
com.google.guava:guava          28.2-jre / 30.1-jre        -> 33.4.0-jre
io.netty:netty-all              4.1.51.Final               -> 4.1.115.Final
org.slf4j:slf4j-api / -simple   1.7.30                     -> 2.0.16
commons-codec:commons-codec     1.15                       -> 1.17.1
```

### ⚠ 검증하지 못한 것

**Lombok의 Java 25 지원 여부가 가장 불확실하다.** Lombok은 컴파일러 내부 API에 의존하므로 JDK 메이저 버전이 오를 때마다 대응 릴리스가 필요하다. 1.18.36으로 올렸으나 **Java 25를 지원하는 최소 버전이 그보다 높을 수 있다.** 빌드가 `java.lang.NoSuchFieldError` 나 `IllegalAccessError` 로 실패하면 이 문제다.

`slf4j 1.7 → 2.0` 은 메이저 업그레이드다. API는 호환되지만 바인딩 방식이 바뀌었으므로(`ServiceLoader` 기반) 로깅이 조용히 죽을 수 있다.

**검증 방법**

```bash
cd Repositories/STS/TOTP-example && ./gradlew build          # Lombok + Java 25 조합 확인
cd Repositories/Eclipse/annotation-processing3 && ./gradlew build   # maven-publish + 애너테이션 프로세서
cd Repositories/Gradle/JUnit-Example && ./gradlew run        # mainClass 변경 확인
cd Repositories/STS/netty-tutorial && ./gradlew build        # slf4j 2.x 확인
```

---

## 30. Spring 프로젝트 40개를 Jakarta EE / Spring 7 / Boot 4.0으로 이관

**변경 파일**: `Repositories/STS/**`, `Repositories/Eclipse/spring-*` 의 소스·빌드 파일 234개, `pugs/dev/JVM/{spring_servlet,spring_framework,java_ee,jpa}.pug` + 생성 HTML, `tools/migrate-jakarta.mjs` (신규)

### 대상

| 분류 | 개수 | 기존 |
|---|---:|---|
| Spring Boot (Gradle) | 7 | Boot 2.2.1 ~ 2.6.0 |
| Spring Boot (Maven) | 9 | Boot 2.1.8 ~ 2.2.2 |
| 순수 Spring MVC (`mvc-javaN`, `mvc-xmlN`) | 24 | spring-webmvc 5.2.8.RELEASE |

`mvc-javaN` 18개는 이름과 달리 Java 버전별 예제가 아니라 **Java 기반 설정을 쓰는 Spring MVC 튜토리얼 시리즈**였다(`mvc-xmlN` 은 XML 설정판). 각 프로젝트가 인터셉터·파일 업로드·다중 디스패처 등 다른 주제를 다루므로, 일괄 상향이 적절하다고 판단했다. `spring_servlet.pug` 가 이들을 참조한다.

### 핵심 — `javax` 를 전부 바꾸면 안 된다

Spring 6 / Boot 3부터 `javax.*` 가 `jakarta.*` 로 바뀌었지만, **`javax` 로 시작한다고 다 Jakarta EE가 아니다.** 아래는 JDK 소속이라 바꾸면 컴파일이 깨진다.

| 패키지 | 소속 모듈 |
|---|---|
| `javax.annotation.processing` | `java.compiler` |
| `javax.lang.model`, `javax.tools` | `java.compiler` |
| `javax.sql` | `java.sql` (JDBC `DataSource`) |
| `javax.crypto` | `java.base` |
| `javax.naming` | `java.naming` |

그래서 전체 치환이 아니라 **허용 목록 방식**으로 처리했다. 변환 대상은 `servlet`, `persistence`, `validation`, `websocket`, `transaction`, `enterprise`, `inject`, `el`, `mail`, `ws.rs` 와 `javax.annotation` 중 `processing`·`Generated` 를 제외한 것.

결과적으로 남은 `javax` 는 전부 JDK 소속이다.

```
javax.annotation.processing / javax.lang.model / javax.tools
javax.sql / javax.crypto / javax.naming
```

### 적용한 변경

```
javax.persistence      -> jakarta.persistence     169곳
javax.servlet.http     -> jakarta.servlet.http     82곳
javax.servlet          -> jakarta.servlet          56곳
javax.servlet.jsp      -> jakarta.servlet.jsp      50곳
javax.annotation       -> jakarta.annotation       30곳
javax.validation       -> jakarta.validation       23곳
javax.websocket        -> jakarta.websocket        10곳
javax.transaction      -> jakarta.transaction       9곳
```

빌드 좌표.

```
javax.servlet:javax.servlet-api:4.0.1      -> jakarta.servlet:jakarta.servlet-api:6.1.0
javax.servlet.jsp:javax.servlet.jsp-api    -> jakarta.servlet.jsp:jakarta.servlet.jsp-api:4.0.0
javax.websocket:javax.websocket-api:1.1    -> jakarta.websocket:jakarta.websocket-api:2.2.0
javax.annotation:javax.annotation-api      -> jakarta.annotation:jakarta.annotation-api:3.0.0
spring-webmvc / -websocket  5.2.8.RELEASE  -> 7.0.0
spring-boot-starter-parent  2.x.x.RELEASE  -> 4.0.0
org.springframework.boot (plugin)   2.x    -> 4.0.0
io.spring.dependency-management            -> 1.1.7
Java  11 / 13 / 15                         -> 25
Gradle wrapper  5.6.4 / 6.3 / 6.4.1 / 7.x  -> 9.3.1
slf4j 1.7.30 -> 2.0.16, logback 1.2.3 -> 1.5.16
jackson-databind 2.11.2 -> 2.18.2, lombok -> 1.18.36, freemarker -> 2.3.34
```

### 작업 중 발견한 스크립트 버그 2건

정직하게 남긴다. 둘 다 **적용 후 검사에서 잡아 고쳤다.**

1. **좌표 변환 순서 오류** — 패키지 치환이 먼저 돌아 그룹 ID가 `jakarta.servlet` 로 바뀐 뒤에 `javax.servlet:javax.servlet-api` 패턴을 찾으니 매칭되지 않았다. 결과가 `jakarta.servlet:javax.servlet-api:4.0.1` 이라는 **존재하지 않는 좌표**였다
2. **`.RELEASE` 꼬리 잔존** — `5\.[\d.]+` 가 `5.2.8.` 까지 탐욕적으로 먹어 `RELEASE` 가 남았고 `7.0.0RELEASE` 가 됐다

전수 grep으로 두 패턴이 0건임을 확인했다.

### 문서와의 불일치 처리

`spring_servlet.pug`(1,338줄)와 `spring_framework.pug`(1,578줄)는 본문이 `javax.*` 와 XML 설정을 가르치는데, 이제 연결된 예제 코드는 `jakarta.*` 다. **본문 전면 개정은 Phase 1의 1·2순위 작업이라 이번 범위를 넘어선다.**

대신 네 문서(`spring_servlet`, `spring_framework`, `java_ee`, `jpa`) 상단에 배너를 넣어 불일치를 명시했다.

> 본문은 Spring 5.x / javax.* 기준입니다. 연결된 예제 코드는 Spring 7 / Jakarta EE(jakarta.*)로 이관되어 본문과 차이가 있습니다. 본문 최신화는 진행 중입니다.

### ⚠ 검증하지 못한 것 — 이번 커밋이 가장 위험하다

**1. Spring Boot 4.0 고유의 파괴적 변경을 반영하지 못했다.**
Boot 2 → 4는 메이저를 두 번 건너뛴다. 제가 아는 것은 Boot 3의 변경(jakarta 전환, Java 17+)까지이고, **Boot 4에서 무엇이 더 바뀌었는지는 확신할 수 없다.** 모듈 재구성, 프로퍼티 키 변경, 자동 설정 클래스 이동 등이 있을 수 있다. 공식 마이그레이션 가이드 대조가 필요하다.

**2. Spring Framework 7.0.0 이라는 버전이 실제로 존재하는지 확인하지 못했다.** 패치 번호가 다를 수 있다.

**3. `spring-boot-starter-parent` 를 쓰는 프로젝트는 개별 라이브러리 버전을 명시하면 안 된다.** parent가 관리하는 버전과 충돌할 수 있다. slf4j·logback·jackson 버전을 올린 것이 Boot 관리 버전과 어긋날 가능성이 있다.

**4. JSP / `web.xml` 기반 프로젝트** — `web.xml` 10개가 여전히 `web-app_2_3.dtd` DOCTYPE을 쓴다. Jakarta Servlet 6.1에서는 `jakarta.ee` 네임스페이스의 `web-app_6_0.xsd` 로 바꿔야 한다. **이번에 손대지 않았다.**

**5. Tomcat 버전** — Jakarta Servlet 6.1은 Tomcat 11 이상이 필요하다. WAR 배포 대상 서버도 함께 올려야 한다.

**검증 방법**

```bash
# 가장 단순한 것부터
cd Repositories/STS/mvc-java1     && ./gradlew build
cd Repositories/STS/app2          && ./gradlew build   # Spring Boot 4 Gradle
cd Repositories/Eclipse/spring-aop && mvn -q compile    # Spring Boot 4 Maven
cd Repositories/STS/jpa-example   && ./gradlew build   # jakarta.persistence
cd Repositories/STS/mvc-java16    && ./gradlew build   # websocket
cd Repositories/STS/mvc-xml2      && ./gradlew build   # web.xml + JSP
```

`mvc-java1` 하나만 통과시켜도 좌표·네임스페이스가 맞는지 대부분 판명된다. 되돌리려면 `git revert` 로 이 커밋만 떼어내면 된다.

---

## 31. Node 프로젝트 10개에 `engines` 선언 추가, 홈의 빌드 안내 정정

**변경 파일**: `Repositories/**/package.json` 10개, `index.pug`, `index.html`

### `engines` 추가

10개 `package.json` 전부에 아래를 넣었다.

```json
"engines": { "node": ">=24" }
```

Node 24가 Active LTS(2025-10-28 승격)다. Node 20은 2026-04-30, 22는 Maintenance, 25는 2026-06-01에 EOL됐다.

`engines` 는 npm이 기본으로 강제하지 않는다. 강제하려면 `.npmrc` 에 `engine-strict=true` 가 필요하다. 여기서는 **의도 표시**로만 넣었다.

### 홈페이지의 빌드 안내가 틀려 있었다

`index.pug` 의 "Oracle Cloud에 블로그 띄우는 절차" 절이 이렇게 되어 있었다.

```diff
-$ nvm install 20            # Node 20 은 2026-04-30 EOL
+$ nvm install 24            # package.json 의 engines 가 24 이상을 요구한다
-$ npm install
+$ npm ci                    # lock 을 그대로 재현
-$ npm run build-all         # 존재하지 않는 스크립트
+$ npm run build
```

**`npm run build-all` 은 존재하지 않는 스크립트다.** `package.json` 에 정의된 것은 `build`, `check`, `typecheck` 셋뿐이다. 이 안내를 그대로 따라 하면 실패한다. nvm 설치 스크립트 버전도 v0.39.7 → v0.40.1 로 올렸다.

### 손대지 않은 의존성

`express-start` 의 `mongodb ^3.5.6`, `jade 1.11.0`, `mocha 7.1.1`, `fastify-start` 의 `fastify ^3.27.1` 등은 그대로 두었다. 전부 메이저 업그레이드가 필요한데(`jade` 는 아예 `pug` 로 개명된 후 폐기), 예제 코드까지 함께 고쳐야 하므로 별도 작업이다.

---

# 검증 체크리스트

커밋 26~31은 **의존성 다운로드가 차단된 환경에서 지식에 기반해 작성**한 것이라 빌드로 확인하지 못했다. 아래를 순서대로 돌려주시면 된다. **위험도가 높은 것부터** 배치했다.

각 항목은 커밋 단위로 되돌릴 수 있다 — 문제가 크면 `git revert <sha>`.

## 1. Spring — 위험도 높음 (커밋 `470c55c6`)

Boot 2 → 4는 메이저를 두 번 건너뛴다. **여기가 깨질 확률이 가장 높다.**

```bash
cd Repositories/STS/mvc-java1      && ./gradlew build   # 가장 단순. 좌표·네임스페이스 판별용
cd Repositories/STS/app2           && ./gradlew build   # Spring Boot 4 (Gradle)
cd Repositories/Eclipse/spring-aop && mvn -q compile    # Spring Boot 4 (Maven)
cd Repositories/STS/jpa-example    && ./gradlew build   # jakarta.persistence
cd Repositories/STS/mvc-java16     && ./gradlew build   # jakarta.websocket
cd Repositories/STS/mvc-xml2       && ./gradlew build   # web.xml + JSP
```

확인할 것.

- [ ] `org.springframework:spring-webmvc:7.0.0` 이 실제로 존재하는 버전인가 (패치 번호 확인)
- [ ] Spring Boot 4.0의 마이그레이션 가이드에 반영 안 된 항목이 있는가 (모듈 재구성, 프로퍼티 키 변경, 자동 설정 이동)
- [ ] `spring-boot-starter-parent` 사용 프로젝트에서 slf4j·logback·jackson 버전을 명시한 것이 parent 관리 버전과 충돌하지 않는가 — **충돌하면 명시를 지우는 편이 맞다**
- [ ] `web.xml` 10개가 아직 `web-app_2_3.dtd` 다. Jakarta Servlet 6.1용 `web-app_6_0.xsd` 로 바꿔야 하는가
- [ ] 배포 대상 Tomcat이 11 이상인가 (Jakarta Servlet 6.1 요구)

## 2. Android — 마감 있음 (커밋 `c2fc1681`)

**Google Play targetSdk 36 강제가 2026-08-31이다.**

```bash
cd Repositories/Android/Project01 && ./gradlew assembleDebug --warning-mode all
```

- [ ] AGP 9.0이 Groovy DSL(`build.gradle`)을 계속 지원하는가 — Kotlin DSL 강제라면 7개 전부 `.kts` 로 다시 써야 한다
- [ ] `minifyEnabled` / `proguardFiles` 표기가 AGP 9에서 유효한가
- [ ] AGP 9의 최소 JDK 요구치 (17로 잡았음)
- [ ] `minSdk` 를 19 → 21로 올린 것이 의도에 맞는가 (Android 5.0 미만 제외)

하나만 통과시킨 뒤 문제가 있으면 `tools/migrate-android.mjs` 의 상수를 고쳐 재적용한다. **스크립트는 멱등이 아니므로 재적용 전 `git checkout -- Repositories/Android`.**

## 3. Lombok + Java 25 — 여러 커밋에 걸침 (`16e4959c`, `9520b9b8`, `470c55c6`)

Lombok은 컴파일러 내부 API에 의존해 JDK 메이저 버전마다 대응 릴리스가 필요하다. **1.18.36으로 올렸으나 Java 25 지원 최소 버전이 더 높을 수 있다.**

```bash
cd Repositories/STS/TOTP-example      && ./gradlew build
cd Repositories/Eclipse/lombok-example && mvn -q compile
```

- [ ] `NoSuchFieldError` / `IllegalAccessError` 가 나면 Lombok 버전 문제다. 최신으로 올릴 것

## 4. .NET (커밋 `9d8394cb`)

```bash
cd Repositories/VisualStudio
dotnet build VisualStudio.sln
dotnet list package --outdated
```

- [ ] 손대지 않은 패키지 8종이 net10.0에서 복원되는가 — `MySql.Data` 8.0.28, `System.Data.SQLite` 1.0.115.5, `Dapper` 2.0.123, `SSH.NET` 2020.0.2, `Npgsql` 8.0.3, `morelinq` 3.3.2, `Hardcodet.NotifyIcon.Wpf` 1.1.0
- [ ] `VisualStudio.sln` 이 `Visual Studio Version 17`(VS 2022)인데 .NET 10 빌드에 더 최신 VS가 필요한가

## 5. Gradle 9 이관 (커밋 `16e4959c`)

```bash
cd Repositories/Eclipse/annotation-processing3 && ./gradlew build   # maven-publish
cd Repositories/Gradle/JUnit-Example           && ./gradlew run     # mainClass
cd Repositories/STS/netty-tutorial             && ./gradlew build   # slf4j 2.x
```

- [ ] `slf4j 1.7 → 2.0` 은 바인딩 방식이 바뀐 메이저 업그레이드다. **로깅이 조용히 죽지 않는지** 실제 출력을 확인할 것

## 6. Maven (커밋 `9520b9b8`)

```bash
cd Repositories/Eclipse/JavaSE && mvn -q compile
```

- [ ] `maven.compiler.release 25` 로 컴파일되는가

## 7. 블로그 본문과의 정합성

코드는 올렸지만 **본문이 아직 옛 내용인 문서가 있다.**

- [ ] `spring_servlet.pug`(1,338줄), `spring_framework.pug`(1,578줄) — 본문이 `javax.*` / XML 설정 기준. 상단에 불일치 배너를 넣어두었다
- [ ] `java_ee.pug`, `jpa.pug` — 동일
- [ ] 코드 버튼이 여는 파일 내용이 본문 설명과 맞는지 (예: `mvc-javaN` 의 `jakarta.servlet` import)

본문 개정은 Phase 1의 1·2순위 작업이다.

## 8. 사이트 자체 (이 환경에서 검증 완료, 참고용)

```bash
npm ci && npm run typecheck && npm run build
```

빌드 후 `npm run check` 가 자동으로 돌며, 오류 0건이어야 한다. 경고 11건(`posts.json` 미등록 문서)은 알려진 상태다.

---

## 33. `JavaEE` 문서를 Jakarta EE 11 기준으로 갱신

**변경 파일**: `pugs/dev/JVM/java_ee.pug` (363 → 486줄), `posts/dev/JVM/java_ee.html`, `source/posts.json`

커밋 30에서 예제 코드를 `jakarta.*` 로 옮기면서 문서와 어긋나게 만든 부채를 갚는 첫 작업이다. 제목도 `JavaEE` → `Jakarta EE` 로 바꿨다(URL은 `java_ee.html` 그대로라 리다이렉트 불필요).

### 구성

`+legacy` mixin의 첫 실사용이다. **기존 본문 353줄을 그대로 접고 그 위에 새 내용을 올렸다.**

```pug
+post({ title: 'Jakarta EE', ... })

    p.version-note Jakarta EE 11(2025-06-26) 기준으로 정리했습니다. ...

    //- 새 내용 8개 절

    +legacy('Java EE 8 / javax.*', 'Tomcat 9 이하 기준. Jakarta EE 9 부터 네임스페이스가 바뀌었습니다')
        //- 기존 353줄을 들여쓰기만 해서 통째로
```

기존 본문은 **내용을 한 글자도 고치지 않고 4칸 들여쓰기만 했다.** Tomcat 8.0.52 경로, `javax.sql.DataSource`, JSP 스크립틀릿, MyBatis 설정 등이 그대로 남아 있어 구버전 환경을 쓰는 방문자에게는 여전히 유효하다.

### 새로 쓴 내용

| 절 | 요지 |
|---|---|
| Java EE → Jakarta EE | 2017 이관, 2018 이름 투표, **2019 상표권 협상 결렬**, 2020 big bang 전환 |
| 버전 이력 | EE 8 ~ 11 표. 필요 Java, 네임스페이스, 핵심 변경 |
| EE 11 사양 버전 | Servlet 6.1, Persistence 3.2, CDI 4.1, REST 4.0 등 |
| 체감 변화 | Servlet 6.0/6.1, Persistence 3.2, **CDI 4.0의 `beans.xml` 파괴적 변경** |
| `web.xml` 스키마 | EE 11 / EE 8 헤더 대조 |
| 구현체 | Tomcat·Jetty·GlassFish·WildFly·Payara 버전 대응표 |
| JSP의 현재 위치 | 유지보수 모드, Faces 4.0에서 뷰 기술로서 제거됨 |
| 제거·폐기 | EE 9 / 10 / 11 각각 |

특히 강조한 세 가지.

1. **`javax` 로 시작한다고 다 Jakarta EE가 아니다** — `javax.sql`, `javax.crypto`, `javax.naming`, `javax.annotation.processing`, `javax.lang.model`, `javax.tools` 는 JDK 소속이다. 커밋 30에서 실제로 부딪힌 함정이라 코드 블록으로 명시했다
2. **CDI 4.0에서 빈 `beans.xml` 의 의미가 바뀌었다** — explicit → implicit bean archive. "주입되던 것이 갑자기 안 되는" 전형적 원인
3. **Tomcat 9가 `javax` 를 지원하는 마지막 메이저다** — Jakarta로 올리면 10.1 이상, Servlet 6.1을 쓰려면 11이 필요

### 검증

- 렌더 성공, `h1` 12개, `legacy` 블록 1개
- **새로 쓴 코드 블록 11개 전부 부등호 이스케이프 검사 통과**
- `posts.json` diff는 제목 1줄
- 정합성 검사 오류 0건

### 알려진 잔여 문제

접어둔 구 본문의 코드 블록 10개가 `<` 는 `&lt;` 로 이스케이프하면서 `>` 는 그대로 두고 있다. **기존 문제이며 이번에 건드리지 않았다.** HTML 파서가 관대해 렌더는 정상이지만 일관성은 없다.

### 출처

`jakarta.ee` 릴리스 페이지(8/9/10/11/12), Servlet 6.0·6.1 XSD 원문, Eclipse Foundation 상표권 블로그, `tomcat.apache.org/whichversion.html`, 각 구현체 릴리스 노트.

---

## 34. Python 입문 문서를 3.14 기준으로 갱신

**변경 파일**: `pugs/dev/python/basic.pug` (359 → 504줄), `posts/dev/python/basic.html`

계획서 Phase 1 우선순위 5위. 입문 문서라 유입이 클 것으로 보이는데 **제목이 `Python 3.8` 이었고 본문의 설치 안내가 `C:\Program Files\Python36`, `python3.7` 을 예로 들고 있었다.** 제목은 커밋 21에서 이미 정리했고, 이번에 본문을 올렸다.

### 접근 — 전면 개정이 아니라 부분 교체

이 문서는 자료형·구문·함수·클래스처럼 **버전과 무관한 내용이 8할**이다. 통째로 `+legacy` 로 접으면 오히려 손해다. 두 군데만 손댔다.

1. **설치 절 교체** — 구 안내는 `+legacy('구 설치 안내', 'Python 3.8 시절 기준')` 으로 접고 현행 내용으로 대체
2. **버전별 절 추가** — 기존 `h1 Python 3.7`, `h1 Python 3.8` 뒤에 3.9 ~ 3.14 를 같은 형식으로 이어 붙임

### 새로 쓴 설치·환경 내용

| 절 | 요지 |
|---|---|
| 설치 | **3.14부터 Windows 배포가 Python Install Manager(PyManager) 중심**(PEP 773). `py install 3.14`. 기존 exe installer는 3.15까지 병행 |
| PEP 668 | 배포판 파이썬에 `pip install` 하면 `error: externally-managed-environment`. **입문자가 가장 먼저 부딪히는 벽**이라 절을 따로 뺐다 |
| 새 REPL | 3.13부터 기본. 여러 줄 편집, 색상, F1/F2/F3. 3.14에서 문법 하이라이팅 |

### 버전별 절 (3.9 ~ 3.14)

**언어 문법 변경만** 담았다. 표준 라이브러리 추가는 별도 문서(`dev/python/standard.pug`)의 몫이다.

- **3.9** — dict 병합 `|`, 내장 제네릭 `list[int]`, PEG 파서
- **3.10** — **구조적 패턴 매칭**, `X | Y` 유니온, 괄호 컨텍스트 매니저
- **3.11** — `except*` / ExceptionGroup, `^^^^` 트레이스백, 3.10 대비 1.25배
- **3.12** — **타입 파라미터 문법**과 `type` 문, f-string 정식 문법화, **`distutils` 제거**, **`venv` 가 setuptools를 넣지 않음**
- **3.13** — 타입 파라미터 기본값, 새 REPL, **PEP 594 "dead batteries" 19개 모듈 일괄 제거**
- **3.14** — **t-string**, 괄호 없는 다중 except, **어노테이션 지연 평가(전방 참조를 문자열로 감쌀 필요 없음)**, `types.UnionType` 통합

### free-threaded / JIT 절을 따로 뺀 이유

이 둘은 **"정식인가 실험인가"가 자주 잘못 알려진다.** 정확히 구분해 적었다.

- **free-threaded** — 3.13 실험적 → 3.14에서 PEP 779로 **공식 지원**. 단 **기본값이 아니며** 별도 실행 파일(`python3.14t`)이 필요하고 단일 스레드 성능이 5~10% 떨어진다. 기본 빌드로 만드는 단계는 미정
- **JIT** — 3.13 도입, **3.14에서도 여전히 실험적**. 다만 공식 macOS/Windows 바이너리에는 포함돼 있다
- 3.14의 tail-call 인터프리터는 JIT와 **별개**다
- **3.14.0~3.14.4는 증분 GC였으나 3.14.5에서 3.13의 세대별 GC로 되돌려졌다.** "3.14 = 증분 GC"라고 쓰면 틀린다

### 검증

- 렌더 성공, `h1` 25개, `legacy` 블록 1개
- **코드 블록 23개 전부 부등호 이스케이프 검사 통과** — 이 과정에서 기존 `pip freeze > requirements.txt` 의 미이스케이프도 함께 고쳤다
- 정합성 검사 오류 0건

### 남은 것

같은 카테고리에 **제목에 버전이 박힌 문서가 더 있다** — `Python Built-in Constants 3.8`, `Built-in Exceptions 3.8`, `Built-in Functions 3.8`, `Built-in Types 3.8`. 커밋 21에서 놓쳤다. `CLAUDE.md` 의 "제목에 버전을 넣지 않는다" 원칙에 어긋나므로 정리가 필요하다.

`dev/python/standard.pug`(2,148줄)는 여전히 `Since 3.8` 이 상한이다. Phase 1 우선순위 3위로 남아 있다.

### 출처

`docs.python.org` 의 What's New 3.9~3.14, `devguide.python.org/versions/`, PEP 584·585·604·634·654·695·696·750·758·649·765·779·744·773·668·761, `packaging.python.org` 도구 권장(2026-07-29 갱신).
