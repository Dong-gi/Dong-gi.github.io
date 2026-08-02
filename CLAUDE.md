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
npm run dates      # 문서 갱신일 재계산 (아래 참조)
npm test           # 경로 처리 테스트 (Windows 호환성)
```

`npm run build` 는 마지막에 `npm run check` 를 돌린다. 검사 실패 시 종료 코드 1이지만 산출물은 이미 쓰인 뒤다. "빌드를 막는" 게 아니라 "문제를 알리는" 동작이다.

`node source/build.ts new` 로 실행하면 최근 10분 내 수정된 pug만 렌더한다. 단 `index.pug` 는 이 조건과 무관하게 항상 렌더된다. 이미지와 d2는 증분 모드와 관계없이 매번 검사한다(이미 생성된 것은 건너뜀).

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
| `source/doc-dates.json` | **문서 갱신일.** git 이력에서 계산한 값. `npm run dates` 로 생성 |
| `imgs/` → `imgs-generated/` | 원본 이미지 → 반응형 변환본 |
| `d2/*.d2` → `d2/*.svg` | 다이어그램 소스 → SVG |
| `files/sitemap.txt` | 사이트맵. `robots.txt` 가 가리킨다 |
| `Repositories/` | 본문 코드 버튼이 참조하는 예제 프로젝트 (아래 주의) |
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

   `file` 은 `posts/` 를 뺀 상대 경로다. `mtimeMs` 는 빌드가 `source/doc-dates.json` 을 보고 채운다.

   `category` 는 `/` 로 계층을 표현한다 (예: `개발 자료/JVM`). **다중 소속은 문자열 배열**이다.

   ```json
   { "category": ["개발 자료/책", "책"], "file": "book/0/010.html", "title": "..." }
   ```

   `source/default.js` 가 `Array.isArray(post.category)` 로 분기해 각 카테고리 노드에 문서를 복제해 넣는다. 쉼표로 이어 쓰면 `기초 과목,책` 이라는 단일 노드가 생기므로 쓰지 않는다.

3. 커밋한 뒤 `npm run dates` → `npm run build`

   `dates` 는 git 이력에서 문서 갱신일을 다시 계산한다. **커밋 이후에 돌려야** 새 문서가 반영된다. 빼먹으면 정합성 검사 W4가 알려준다.

## 문서 갱신일 (`dateModified`)

`source/doc-dates.json` 에 문서별 갱신일이 들어 있고, 빌드는 이 값을 JSON-LD 의 `dateModified` 와 홈의 "최근 갱신" 목록에 쓴다. **파일 mtime 은 쓰지 않는다** — 새로 클론하면 전부 체크아웃 시각이 되어 263개 페이지의 날짜가 한꺼번에 덮이기 때문이다.

`npm run dates` 가 git 이력에서 계산하며 두 가지를 걸러낸다.

1. **`tools/refactor-commits.json` 에 선언된 커밋** — 내용은 그대로 두고 표기만 바꾼 대량 커밋. mixin 교체, 후행 쉼표 추가 같은 것들이다. 이걸 안 거르면 문서 대부분의 갱신일이 리팩터링 날짜로 뭉개진다
2. **공백만 바뀐 변경** — 파일 단위로 판정하므로, 일부 파일만 실제 수정된 혼합 커밋에서도 정확하다. `+legacy` 로 본문을 접을 때가 여기 해당한다

경로가 바뀐 문서는 rename 을 추적해 이동 이전 이력까지 이어붙인다.

새 리팩터링 커밋을 만들었다면 `tools/refactor-commits.json` 에 SHA 를 추가하고 `npm run dates` 를 다시 돌린다.

## skeleton.pug mixin

전체 15개다.

| mixin | 용도 |
|---|---|
| `+post(options)` | 문서 전체를 감싸는 레이아웃. 모든 문서의 루트 |
| `+bookInfo(options)` | 도서 리뷰용 메타 정보 |
| `+legacy(version, note)` | 구버전 서술을 `details` 로 접는다. 아래 "문서 최신화" 참조 |
| `+codeBtn(path, lan)` | `Repositories/` 의 실제 **파일**을 여는 버튼. 경로가 없거나 디렉터리면 검사 E6가 잡는다 |
| `+asCode(lan, title)` | 블록 코드. 제목 줄과 함께 `<div class="as-code">` 로 감싼다 |
| `+asInlineCode(code, lan)` | 문장 중간의 짧은 코드 (`<span>`) |
| `+asA(href, text)` | 링크 버튼. `text` 생략 시 URL 마지막 조각을 씀 |
| `+w3img(src, description)` | 이미지. `imgMap` 으로 크기를 미리 넣어 레이아웃 이동을 막는다 |
| `+w3button(color)` | 범용 버튼. `+codeBtn` 이 내부적으로 쓴다 |
| `+table()` / `+tds(...)` / `+ths(...)` | 표 |
| `+pos(name)` | 문서 내 앵커 지점을 심는다 |
| `+goto(name, href)` | `+pos` 로 심은 앵커로 가는 링크. `href` 를 주면 다른 문서의 앵커로 새 탭 이동 |
| `+hoverTemplate()` | 마우스 오버 시 뜨는 팝오버 컨테이너 |

## 빌드 동작

`source/build.ts` 는 CPU 코어 수만큼 `worker_threads` 를 띄우고 세 종류 작업을 분배한다.

- **`render-pug`** — pug → HTML. 렌더 시 `siteOrigin`, `pageUrl`, `pageModified` 를 locals로 주입해 canonical·Open Graph·JSON-LD 를 만든다. 홈(`pageUrl` 이 오리진 루트)은 `WebSite`, 나머지는 `TechArticle` 로 나간다
- **`transform-img`** — `imgs/` 원본을 sharp로 500 / 1200 / 2000px × `jpeg`/`webp`/`avif` (gif는 `gif`/`webp`) 로 변환. 이미 있으면 건너뛴다. 크기 정보는 `source/img-map.json` 에 캐시
- **`render-d2`** — `d2` 로 SVG 생성 후 svgo 최적화 + 미사용 클래스 제거 + 반복 인라인 스타일의 클래스화

메인 스레드는 끝나고 `source/img-map.json`, `files/posts-compressed.json`, `files/sitemap.txt` 를 쓴다.

## `posts/` 를 직접 편집해도 되는 두 경우

`posts/` 는 원칙적으로 생성물이지만 pug 소스가 없는 파일이 두 종류 있다. 구 카테고리 체계에서 이주하며 남은 것들이다.

1. **리다이렉트 스텁 308개** — `<html data-redirect-stub>` 마커가 있다. `meta refresh` + `rel=canonical` + `location.replace` 3중 구성. `tools/apply-redirects.mjs` 로 생성·재생성한다
2. **보존 대상 47개** — `tools/preserved-orphans.json` 에 선언. 후계 문서가 없어 리다이렉트할 곳이 없고, 검색엔진에 색인되어 있어 삭제하지 않는다

새 고아 HTML이 생기면 정합성 검사 E4가 잡는다.

## 문서 최신화

낡은 문서를 갱신할 때는 **기존 내용을 지우지 않고 `+legacy` 로 접은 뒤 최신 내용을 그 위에 올린다.**

```pug
h1 Python
p 이 문서는 Python 3.14 기준입니다.
//- ... 최신 내용 ...

+legacy('Python 3.8', '2024-10-07 지원 종료')
    //- 기존 내용을 통째로 이 아래로 들여쓰기
```

기존 본문을 들여쓰기만 하므로 diff가 깨끗하고 되돌리기 쉽다. 구버전 환경을 쓰는 방문자에게도 정보가 남는다.

**제목에는 버전을 넣지 않는다.** URL이 바뀌면 리다이렉트가 또 필요해지므로, 기준 버전은 본문 상단에 적는다.

## `Repositories/` 정리 시 주의

대부분은 본문 `+codeBtn` 이 참조하는 예제지만, **참조가 없어도 지우면 안 되는 것들이 있다.**

| 경로 | 사유 |
|---|---|
| `JavaScript/Chrome Proxy Extension` | 실제로 사용 중인 도구. 블로그 예제가 아니라 독립 산출물 |
| `iroiro-downloader` | 자체 `CLAUDE.md`·`README.md` 를 가진 독립 프로젝트 |
| `usb-tether` | 위와 같음 |

"pug에서 참조하지 않음"은 삭제 근거로 충분하지 않다. 실제 용도를 확인하고 지운다.

## 정합성 검사

`tools/check-integrity.mjs`. 상세는 파일 상단 주석 참조.

- **E1~E3** `posts.json` 항목의 HTML·pug 존재, 중복 없음
- **E4** 보존 목록에 없는 새 고아 HTML
- **E5** 리다이렉트 대상 존재, 체인·자기참조 없음
- **E6** `/Repositories/...` 코드 참조 무결성
- **E7** 사이트맵 URL 형식과 대상 존재
- **W1** pug는 있으나 `posts.json` 미등록 (현재 11건, 경고)
- **W2** 보존 목록의 항목이 사라지거나 스텁이 됨
- **W4** `source/doc-dates.json` 에 갱신일이 없는 pug (`npm run dates` 필요)

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
- **경로는 항상 `/` 로 다룬다.** `build.ts` 는 경로를 문자열로 치환하는 곳이 많아(`/pugs/` → `/posts/`, doc-dates 조회, URL 생성) 구분자가 섞이면 전부 어긋난다. 파일시스템에서 경로를 받으면 즉시 `toPosix()` 를 통과시킨다. Windows 에서 이걸 빠뜨리면 **HTML 이 `posts/` 가 아니라 `pugs/` 안에 쓰인다.** `npm test` 가 `path.win32` 로 이 상황을 재현해 검증한다
- **셸 명령을 쓰지 않는다.** `chmod`·글로브 같은 POSIX 셸 기능은 Windows 에 없다. Node API(`fsp.chmod` 등)로 대체한다. 외부 바이너리 호출은 `d2` 하나뿐이다
