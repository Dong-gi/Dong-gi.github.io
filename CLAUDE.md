# CLAUDE.md

`https://dong-gi.github.io` 개인 기술 블로그. 프레임워크 없이 pug + TypeScript 빌드 스크립트로 정적 HTML을 생성한다.

## 요구 환경

- **Node.js 24 이상** (`package.json` 의 `engines`). 빌드가 Node 네이티브 TS 타입 스트리핑으로 `source/build.ts` 를 직접 실행한다
- **`d2` 바이너리** — `d2/*.d2` 다이어그램 렌더에 필요. 없으면 해당 작업만 실패한다
- TypeScript는 **6.x 고정**. 프레임워크·라이브러리는 GA 후 6개월 이상 지난 것만 채택한다는 방침이며, TS 7은 연관 도구 지원이 아직 따라오지 않았다

## 명령

```bash
npm run build      # default.js 압축 -> pug/이미지/d2 렌더 -> 정합성 검사
npm run check      # 정합성 검사만
npm run typecheck  # tsc --noEmit
```

`npm run build` 는 마지막에 `npm run check` 를 돌린다. 검사 실패 시 종료 코드 1이지만 산출물은 이미 쓰인 뒤다. "빌드를 막는" 게 아니라 "문제를 알리는" 동작이다.

`node source/build.ts new` 로 실행하면 최근 10분 내 수정된 pug만 렌더한다.

## 디렉터리

| 경로 | 역할 |
|---|---|
| `pugs/**/*.pug` | **소스.** 여기만 편집한다 |
| `posts/**/*.html` | **생성물.** 직접 편집하지 않는다 (예외는 아래 참조) |
| `index.pug` → `index.html` | 홈. 최근 갱신 문서 목록을 클라이언트에서 렌더 |
| `source/skeleton.pug` | 공통 레이아웃과 mixin 모음. 모든 pug가 include |
| `source/build.ts` | 빌드 스크립트 |
| `source/posts.json` | **문서 색인.** 여기 없으면 목록·검색·사이트맵 어디에도 안 나온다 |
| `source/default.css` / `default.js` | 사이트 스타일·스크립트. `default.min.js` 는 빌드 산출물 |
| `imgs/` → `imgs-generated/` | 원본 이미지 → 반응형 변환본 |
| `d2/*.d2` → `d2/*.svg` | 다이어그램 소스 → SVG |
| `files/sitemap.txt` | 사이트맵. `robots.txt` 가 가리킨다 |
| `Repositories/` | 본문 코드 버튼이 참조하는 예제 프로젝트 |
| `tools/` | 정합성 검사·리다이렉트 도구 |
| `docs/` | 현대화 계획과 작업 로그 |

## 새 글 쓰기

1. `pugs/<카테고리>/<이름>.pug` 생성

   ```pug
   include ../../source/skeleton.pug

   +post({
       title: 'Gradle',
       description: 'Gradle 정리',
   })

       h1 Gradle
       ol
           li 내용
   ```

   `+post` 옵션은 `title`, `description`, `useMath`(MathJax 로드 여부) 세 가지다.

2. **`source/posts.json` 의 `list` 에 등록한다.** 이 단계를 빠뜨리면 문서가 어디에도 노출되지 않는다

   ```json
   { "category": "개발 자료", "file": "dev/gradle.html", "title": "Gradle" }
   ```

   `file` 은 `posts/` 를 뺀 상대 경로다. `mtimeMs` 는 빌드가 자동으로 채운다.
   `category` 는 `/` 로 계층을, `,` 로 다중 소속을 표현한다 (예: `개발 자료/JVM`, `기초 과목,책`).

3. `npm run build`

## skeleton.pug mixin

| mixin | 용도 |
|---|---|
| `+post(options)` | 문서 전체를 감싸는 레이아웃. 모든 문서의 루트 |
| `+codeBtn(path, lan)` | `Repositories/` 의 실제 파일을 여는 버튼. **경로가 실제로 있어야 한다** (검사 E6) |
| `+asCode(lan, title)` | 인라인 코드 블록 |
| `+asInlineCode(code, lan)` | 문장 중간의 짧은 코드 |
| `+asA(href, text)` | 링크 버튼. `text` 생략 시 URL 마지막 조각을 씀 |
| `+w3img(src, description)` | 이미지. `imgMap` 으로 크기를 미리 넣어 레이아웃 이동을 막는다 |
| `+table()` / `+tds(...)` / `+ths(...)` | 표 |
| `+bookInfo(options)` | 도서 리뷰용 메타 정보 |

## 빌드 동작

`source/build.ts` 는 CPU 코어 수만큼 `worker_threads` 를 띄우고 세 종류 작업을 분배한다.

- **`render-pug`** — pug → HTML. 렌더 시 `siteOrigin`, `pageUrl`, `pageModified` 를 locals로 주입해 canonical·Open Graph·JSON-LD 를 만든다
- **`transform-img`** — `imgs/` 원본을 sharp로 500 / 1200 / 2000px × `jpeg`/`webp`/`avif` (gif는 `gif`/`webp`) 로 변환. 이미 있으면 건너뛴다. 크기 정보는 `source/img-map.json` 에 캐시
- **`render-d2`** — `d2` 로 SVG 생성 후 svgo 최적화 + 미사용 클래스 제거 + 반복 인라인 스타일의 클래스화

메인 스레드는 끝나고 `source/img-map.json`, `files/posts-compressed.json`, `files/sitemap.txt` 를 쓴다.

## `posts/` 를 직접 편집해도 되는 두 경우

`posts/` 는 원칙적으로 생성물이지만 pug 소스가 없는 파일이 두 종류 있다. 구 카테고리 체계에서 이주하며 남은 것들이다.

1. **리다이렉트 스텁 308개** — `<html data-redirect-stub>` 마커가 있다. `meta refresh` + `rel=canonical` + `location.replace` 3중 구성. `tools/apply-redirects.mjs` 로 생성·재생성한다
2. **보존 대상 47개** — `tools/preserved-orphans.json` 에 선언. 후계 문서가 없어 리다이렉트할 곳이 없고, 검색엔진에 색인되어 있어 삭제하지 않는다

새 고아 HTML이 생기면 정합성 검사 E4가 잡는다.

## 정합성 검사

`tools/check-integrity.mjs`. 상세는 파일 상단 주석 참조.

- **E1~E3** `posts.json` 항목의 HTML·pug 존재, 중복 없음
- **E4** 보존 목록에 없는 새 고아 HTML
- **E5** 리다이렉트 대상 존재, 체인·자기참조 없음
- **E6** `/Repositories/...` 코드 참조 무결성
- **E7** 사이트맵 URL 형식과 대상 존재
- **W1** pug는 있으나 `posts.json` 미등록 (현재 11건, 경고)
- **W2** 보존 목록의 항목이 사라지거나 스텁이 됨

CI(`.github/workflows/verify.yml`)가 push·PR마다 `npm run typecheck` 와 `npm run check` 를 돌린다. 배포에는 관여하지 않는다 — GitHub Pages "deploy from branch" 방식이라 커밋된 산출물이 그대로 서빙된다.

## 커밋 규칙

Conventional Commits 접두사 + 한국어 본문. 기존 이력을 따른다.

```
fix: 사이트맵 URL에 누락된 /posts/ 접두사 복원

<무엇을 왜 고쳤는지. 근거가 되는 코드·수치를 함께.>
```

`feat`, `fix`, `chore`, `docs`, `refactor`, `perf`, `ci` 를 쓴다.

## 진행 중인 작업

- `docs/modernization-plan.md` — 현대화 마스터 플랜 (버전 채택 기준, Phase 0~3)
- `docs/modernization-log.md` — 커밋별 작업 로그

## 주의

- `.gitattributes` 가 `* text=auto` 라 커밋 시 CRLF가 LF로 정규화된다. 기존 파일을 `git add` 하면 줄바꿈만 바뀐 diff가 대량으로 생길 수 있다
- 사이트 오리진과 `/posts/` 접두사는 `build.ts` 의 `SITE_ORIGIN`, `POSTS_URL_PREFIX` 상수 하나로 관리한다. URL 문자열을 코드 안에 직접 쓰지 않는다
- 방문자 분석 도구는 현재 없다. 수집이 중단된 Universal Analytics 태그를 제거했고, GA4 재도입 형태는 `source/skeleton.pug` 주석에 남겨두었다
