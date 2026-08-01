# 현대화 작업 로그

`docs/modernization-plan.md` 의 실행 기록. 커밋 1개 = 작업 1개 원칙으로 진행하며, 각 커밋마다 이 문서를 갱신한다.

리뷰 순서는 아래 표의 위에서 아래 방향이다.

| # | 커밋 | 종류 | 계획서 항목 | 리뷰 포인트 |
|---|---|---|---|---|
| 1 | 고아 HTML 308건을 리다이렉트 스텁으로 치환 | chore | 별건 (선행 작업) | 스텁 형식, 보존 47건의 타당성 |
| 2 | 사이트맵 URL에 누락된 `/posts/` 접두사 복원 | fix | Phase 0-1 | 250개 URL이 전부 깨져 있었음 |
| 3 | TypeScript 6 고정, `@types/node` 를 LTS 24로 하향 | chore | 채택 기준 | 6개월 룰 적용 결과 |
| 4 | `sharp` 네임스페이스 타입 오류 수정, `typecheck` 스크립트 추가 | fix | — | 기존 오류. 타입 검사가 이제 통과 |
| 5 | 수집이 중단된 Universal Analytics 태그 제거 | fix | Phase 0-2 | GA4 재도입 여부는 별도 판단 |
| 6 | 제거된 서브모듈을 가리키던 깨진 코드 참조 정리 | fix | Phase 0-3 | 깨진 참조 2건 → 0건 |
| 7 | canonical·Open Graph·JSON-LD 메타데이터 추가 | feat | Phase 3-1 | 생성 HTML 263개 전부 갱신 |
| 8 | 사이트 정합성 검사를 빌드에 통합 | feat | Phase 0-5 · 2-5 | 고아 355개가 쌓인 근본 원인 차단 |

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

TS 6.0.3 기준 `tsc --noEmit` **오류 0건**. 검사 대상은 저장소 내 유일한 `.ts` 파일인 `source/build.ts` 다.

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
