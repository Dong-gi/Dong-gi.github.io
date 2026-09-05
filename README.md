# Dong-gi.github.io

개인 공부 기록을 담은 정적 사이트. **Pug 로 쓰고 빌드해서 순수 HTML 로 배포한다.**
`master` 에 push 하면 GitHub Actions 가 저장소를 통째로 GitHub Pages 에 올린다.
즉 **빌드 산출물(`posts/`, `index.html`, `figures/*/*.svg` …)도 커밋한다.**

```bash
npm ci
npm run build      # 모든 빌드 요소를 병렬로. 콘솔에는 요소별 성패만 나온다
npm run check      # 기초 과목 문서 검사
npm run verify     # 실제 브라우저로 페이지 확인 (준비가 더 필요하다 — scripts/ 절 참고)
```

빌드는 **다섯 개의 독립된 요소**로 나뉜다. 따로 돌릴 수 있고, 각자 자기 로그를 남기고,
각자 지난번 내용 해시와 견줘 **바뀐 것만** 다시 만든다.

| 명령 | 무엇을 하는가 | 로그·해시 |
|---|---|---|
| `npm run build-js` | `source/default.js` → `default.min.js` | `build-js.log` · `build-js-sha.json` |
| `npm run build-figure` | `figures/*.mjs` → `figures/<과목>/*.svg` | `build-figure.log` · `build-figure-sha.json` |
| `npm run build-d2` | `d2/**/*.d2` → 같은 자리의 `.svg` | `build-d2.log` · `build-d2-sha.json` |
| `npm run build-img` | `imgs/` → `imgs-generated/` 반응형 세트 | `build-img.log` · `build-img-sha.json` |
| `npm run build-pug` | `pugs/**/*.pug` → `posts/**/*.html` (수식 미리 렌더 포함) | `build-pug.log` · `build-pug-sha.json` |

**로그와 해시 기록은 모두 `source/build/` 안**, 요소 스크립트 바로 옆에 있다.
`npm run build` 는 이 다섯을 대신 불러 줄 뿐이고, 결과는 따로 돌린 것과 같다.
**어떤 요소를 통째로 다시 만들고 싶으면 `source/build/build-<요소>-sha.json` 을 지우면 된다.**

로그가 요소마다 따로 남으므로 `npm run build > build.log` 처럼 화면을 파일로 돌릴
필요가 없다. 요소가 실패하면 `source/build/build-<요소>.log` 를 본다.

작업 지침은 `CLAUDE.md`, 자습서 재작성 규격은 `docs/` 에 있다.

---

## 최상위(0레벨)

### 소스 — 사람이 손으로 쓰는 것

| 경로 | 무엇 | 누가·언제 쓰는가 |
|---|---|---|
| `pugs/` | **모든 글의 원본.** 카테고리별 `.pug` | 글을 쓰거나 고칠 때 유일하게 건드리는 곳 |
| `figures/` | 그림을 만드는 **JS 생성기**와 그 산출 SVG | 좌표계·곡선·벡터 그림이 필요할 때 |
| `d2/` | 상자·화살표 **도식의 D2 소스**와 산출 SVG | 흐름도·분류도·구조도가 필요할 때 |
| `source/` | 빌드 스크립트, 레이아웃(`skeleton.pug`), CSS/JS, 색인 데이터 | 사이트 자체의 동작을 바꿀 때 |
| `imgs/` | 손으로 찍은 **원본 이미지**(스크린샷·사진) | 글에 사진을 넣을 때 여기에 둔다 |
| `Repositories/` | 글에서 인용하는 **코드 파일 원본** | `+codeBtn('/Repositories/…')` 로 여는 실행 가능한 예제 |
| `files/` | 글에 딸린 첨부 파일과 사이트맵 | PDF 등을 걸 때 |
| `index.pug` | 사이트 첫 화면의 원본 | 첫 화면을 바꿀 때 |

### 산출물 — 빌드가 만들고 커밋되는 것 (손으로 고치지 마라)

| 경로 | 무엇 | 언제 갱신되는가 |
|---|---|---|
| `posts/` | `pugs/` 를 렌더한 **최종 HTML**. 실제로 서비스되는 페이지 | `npm run build-pug` |
| `imgs-generated/` | `imgs/` 를 반응형 avif·webp 로 변환한 것 | `npm run build-img` (원본이 바뀐 것만) |
| `index.html`, `404.html` | 첫 화면과 오류 페이지 | `npm run build-pug` |
| `fonts/` | 자체 서비스하는 수식 폰트와 일본어 폰트 | 수식 폰트는 고정(MathJax 판올림 때만). 일본어 폰트는 문서가 새 한자를 쓸 때 |

`figures/<과목>/*.svg` 와 `d2/**/*.svg` 도 산출물이지만 소스와 같은 폴더에 있다
(아래 1레벨 표 참고).

### 도구와 설정

| 경로 | 무엇 | 누가·언제 쓰는가 |
|---|---|---|
| `scripts/` | **품질 검사 4종**과 그것을 거드는 도구 3개. 문서·링크·페이지를 기계가 본다 | 문서를 고친 뒤, 커밋 전 |
| `docs/` | 자습서 재작성의 **규격·절차·인계 목록** | `pugs/fundamental/` 을 다시 쓸 때 |
| `CLAUDE.md` | 저장소 규약(pug 문법, 믹스인, 단위 정책, 확인 명령) | 어떤 작업이든 시작 전에 |
| `README.md` | 이 문서. 구조 안내 | 처음 왔을 때 |
| `package.json`, `package-lock.json` | 의존성과 명령 정의 | 빌드·검사 명령을 바꿀 때 |
| `tsconfig.json` | `source/build.ts` 와 `source/build/` 의 타입 설정 | 빌드 스크립트를 고칠 때 |
| `.github/` | GitHub Actions 배포 워크플로 | push 하면 자동으로 돈다 |
| `.vscode/`, `.claude/` | 편집기·에이전트 로컬 설정 (`.claude/` 는 git 무시) | 로컬 환경 설정 |
| `.gitignore`, `.gitattributes` | git 설정 | 드물게 |
| `LICENSE` | 라이선스 | — |

### 웹 표준 파일

| 경로 | 무엇 |
|---|---|
| `robots.txt` | 크롤러 지침. 사이트맵 위치를 가리킨다 |
| `offline-service-worker.js` | 오프라인 캐시. 방문한 페이지를 다시 볼 수 있게 한다 |

### 임시

| 경로 | 무엇 |
|---|---|
| `node_modules/` | 의존성. git 무시 |

---

## 1레벨

### `pugs/` — 글의 원본

카테고리가 그대로 `posts/` 의 폴더 구조가 된다.
새 글을 넣으면 **`source/posts.json` 에도 등록해야** 사이드바·사이트맵·갱신일에 잡힌다.

| 경로 | 무엇 |
|---|---|
| `pugs/fundamental/` | **기초 과목 자습서 12개.** 물리·화학·생물·기초수학·선형대수·확률·이산수학(mcs)·알고리즘·논리학·철학·심리학·일본어 |
| `pugs/dev/` | 개발 관련 정리. 언어·프레임워크·DB·인프라별 하위 폴더 |
| `pugs/book/` | 읽은 책 기록 |
| `pugs/project/` | 프로젝트에서 겪은 문제와 해결(`error.pug`, `tip.pug`) |
| `pugs/daily-life/` | 생활 기록 |

`pugs/` 아래에는 **`.pug` 만** 둔다(현재 257개 전부). 그림은 `figures/` 와 `d2/` 에 있고
문서는 경로로 참조할 뿐이다. 다른 확장자를 넣으면 렌더 대상에서 빠지고 `build-pug` 가
"`.pug` 가 아닌 파일이 N개 있다" 고 경고한다.

### `figures/` — 그림 생성기

의존성 없이 SVG 문자열을 만든다. 산출물이 `<img>` 로 삽입되므로 페이지 CSS 가 닿지 않고,
그래서 다크 모드까지 SVG 안에서 처리한다.

| 경로 | 무엇 | 누가·언제 |
|---|---|---|
| `figures/lib.mjs` | **공용 라이브러리.** 축·곡선·화살표·라벨·팔레트 | 새 그림을 만들 때 여기부터 읽는다 |
| `figures/preview.mjs` | 만든 SVG 를 브라우저로 열어 대조용 PNG 로 뽑는다 | 라벨 겹침을 눈으로 확인할 때. `node figures/preview.mjs <과목> [이름조각]` |
| `figures/<과목>-<블록>.mjs` | 과목별 그림 정의. 한 과목을 여러 파일로 나눠 동시 작업 충돌을 막는다 | 그림을 추가·수정할 때 |
| `figures/<과목>/` | **생성된 SVG.** 손으로 고치지 마라 — 다시 빌드하면 덮인다 | 문서에서 `+w3img('/figures/<과목>/이름.svg', …)` 로 참조 |

과목 폴더 12개: `physics/` `chemistry/` `biology/` `mathematics/` `linear-algebra/`
`statistics/` `algorithm/` `mcs/` `logic/` `philosophy/` `psychology/` `japanese/`.
이름이 문서와 다른 것이 둘 있다 — `mathematics/` 는 `pugs/fundamental/elementary-mathematics.pug`,
`statistics/` 는 `probability.pug` 에 대응한다.

**과목 폴더 이름은 `source/build/figure.ts` 의 `SUBJECTS` 가 정한다.** 거기 없는
접두어의 모듈은 조용히 무시되므로, 과목을 새로 만들 때 그 배열에 먼저 넣어야 한다.

`japanese/` 는 제약이 다르다. 생성 SVG 는 `<img>` 로 실려 본문의 `lang` 을 물려받지
못하고, 한자는 한중일이 같은 코드포인트를 쓰면서 자형이 다르다. 그래서
**그림 안에는 가나·로마자·숫자·한글만 쓰고 한자는 본문 표로 옮긴다.**
장별 모듈 17개가 모두 `lib.mjs` 의 `FONT` 대신 `figures/japanese-font.mjs` 의
`jpGroup`(JP 폰트 스택)을 쓴다. 자세한 것은 `docs/ledger/japanese.md` §5.

### `d2/` — 상자와 화살표 도식

빌드가 `d2` 바이너리로 `.d2` 옆에 같은 이름의 `.svg` 를 만든다.
**문서에서는 `/figures/…` 가 아니라 `/d2/<과목>/이름.svg` 로 참조한다.**
(헷갈리기 쉬워 `scripts/check-doc.mjs` 가 이 실수를 검사한다.)

| 경로 | 무엇 |
|---|---|
| `d2/<과목>/` | 과목별 `.d2` 소스와 산출 `.svg` |
| `d2/1.d2` `2.d2` `3.d2` | 과목별로 나누기 전에 만든 옛 도식. `3.svg` 는 `pugs/dev/software-design.pug` 가 쓴다 |

### `source/` — 사이트의 뼈대

| 경로 | 무엇 | 누가·언제 |
|---|---|---|
| `source/build.ts` | **빌드 오케스트레이터.** `source/build/` 의 요소들을 자식 프로세스로 병렬 실행하고 성패만 보고한다 | 요소를 더하거나 뺄 때 |
| `source/build/` | **빌드 요소 다섯 개**와 공용 라이브러리. 아래 1레벨 표 참고 | 빌드 동작을 바꿀 때 |
| `source/skeleton.pug` | 모든 페이지의 레이아웃과 **믹스인 정의**(`+post` `+example` `+table` `+w3img` `+goto` …) | 새 믹스인이 필요할 때 |
| `source/default.css` | 사이트 전체 스타일 | 디자인을 바꿀 때 |
| `source/default.js` | 목차 생성·검색·다크 모드 등 페이지 동작 | 동작을 바꿀 때 |
| `source/default.min.js` | 위를 압축한 것. **산출물** | `npm run build-js` |
| `source/highlight.pack.js` | 코드 하이라이터(highlight.js 빌드본) | 지원 언어를 늘릴 때만 교체 |
| `source/posts.json` | **글 목록과 카테고리.** 새 글을 여기 등록해야 한다 | 글을 추가·이동할 때 |
| `source/posts-compressed.json` | 위를 검색용으로 압축한 것. **산출물** | 빌드 |

이 폴더에 **더 이상 없는 것** — 예전 문서가 가리키던 자리다.
`img-map.json` 은 없어졌고 그림 크기는 `build/lib/image-size.ts` 가 원본 헤더에서
직접 읽는다(`+w3img` 가 부르는 `imgSize`). `doc-dates.json` 도 없어졌고 갱신일은
빌드 날짜와 git 이력으로 대신한다.

### `source/build/` — 빌드 요소

빌드는 요소 다섯 개로 나뉘고, **요소 하나가 프로세스 하나이고 프로세스 하나가 로그
파일 하나**다. 예전에는 워커 스레드 풀 하나에 pug·d2·img 작업을 번갈아 밀어 넣어서,
로그 한 줄이 어디서 나온 것인지 알 수 없었다.

병렬은 `source/build.ts` 한 곳에만 있다. **요소 안은 순차 실행이다.** 해시 기록 덕분에
평소 빌드의 대상은 방금 고친 문서 한두 개라, 요소 안에서 더 쪼개도 얻을 것이 거의 없다.

| 경로 | 무엇 | 명령 |
|---|---|---|
| `source/build/js.ts` | terser 로 `default.js` 를 압축한다 | `npm run build-js` |
| `source/build/figure.ts` | `figures/*.mjs` 를 불러 SVG 를 쓴다. **내용이 달라진 것만** 쓴다. 과목 목록(`SUBJECTS`)이 여기 있다 | `npm run build-figure` |
| `source/build/d2.ts` | `d2` 바이너리로 렌더하고 SVG 를 줄인다. 바이너리가 없으면 조용히 건너뛴다 | `npm run build-d2` |
| `source/build/img.ts` | sharp 로 반응형 avif·webp 세트를 만든다 | `npm run build-img` |
| `source/build/pug.ts` | pug 렌더 + 수식 미리 렌더 + `posts-compressed.json`·`sitemap.txt` | `npm run build-pug` |
| `source/build/lib/paths.ts` | 저장소 기준 정규화 경로. 매니페스트 키의 형식을 정한다 | — |
| `source/build/lib/log.ts` | 요소별 로그(콘솔 + `build-<요소>.log` 동시) | — |
| `source/build/lib/manifest.ts` | 해시 매니페스트와 증분 실행 | — |
| `source/build/lib/math.ts` | MathJax 미리 렌더. Windows 로더 우회를 포함한다 | — |
| `source/build/lib/image-size.ts` | 이미지 원본 헤더에서 크기를 읽는다. `+w3img` 의 `imgSize` 가 이것이다 | — |
| `source/build/build-<요소>.log` | **요소별 로그.** 매 실행마다 덮어쓴다. git 무시 | 실패했을 때 여기부터 본다 |
| `source/build/build-<요소>-sha.json` | 요소별 **내용 해시 기록.** 빌드 가속의 근거. 지우면 그 요소를 전부 다시 만든다 | 커밋한다 |

산출물을 소스와 같은 폴더에 두는 것은 이 저장소에서 여기뿐이다. 저장소 루트에
`build-pug.log` 같은 파일 다섯 개가 흩어져 있으면 루트를 열 때마다 사이트의 소스보다
빌드 부산물이 먼저 눈에 들어오기 때문이다. 이름의 `build-` 접두사가 정렬에서
요소 스크립트(`pug.ts` …)와 갈라 주고, `npm run build-pug` ↔ `build-pug.log` ↔
`build-pug-sha.json` 의 대응도 한눈에 보인다.

**요소 사이에 의존은 없다.** 다섯이 모두 동시에 돌고 서로의 산출물을 읽지 않는다.
예전에는 둘 있었다 — `img → pug`(`img-map.json`)와 `dates → pug`(`doc-dates.json`).
그림 크기를 `lib/image-size.ts` 가 원본에서 직접 읽게 하고 갱신일을 빌드 날짜와
git 이력으로 대신하면서 둘 다 없앴다. 그래서 순서를 정하는 장치도 두지 않는다.

### `scripts/` — 품질 검사

기계가 볼 수 있는 것은 기계에 맡긴다. 사람 눈으로는 놓치는 것들이다.

| 경로 | 무엇을 보는가 | 언제 |
|---|---|---|
| `scripts/check-chapter.mjs` | 장 조각 하나: 렌더 성공, 수식 구분자 짝, 예제=풀이, 헤딩 레벨, 금지 단위, 그림 존재 등 9가지 | 장을 하나 쓸 때마다 |
| `scripts/check-doc.mjs` | 완성된 문서 하나: 위 항목 + 장 사이 연속성, 표 칸 이스케이프, d2 경로, 환산표 중복, 일본어 폰트 서브셋 등 13가지 | 문서를 합친 뒤 (`npm run check`) |
| `scripts/check-links.mjs` | 사이트 전체의 `+goto` 앵커가 실제로 존재하는가 | **빌드 뒤** (`npm run check-links`) |
| `scripts/verify-pages.mjs` | 실제 브라우저로 열어 404·콘솔 오류·수식 렌더·그림 로드·가로 넘침 | 마지막 확인 (`npm run verify`) |

검사를 거드는 것 셋.

| 경로 | 무엇 |
|---|---|
| `scripts/lib-markers.mjs` | 브라우저가 열릴 때 만드는 앵커를 빌드 시점에 미리 계산한다. `default.js` 와 **같은 결과를 내야 한다** |
| `scripts/prune-pos.mjs` | 헤딩이 이미 만드는 앵커와 이름이 겹치는 `+pos()` 를 찾아 지운다. `--apply` 로 실제 삭제 |
| `scripts/subset-japanese-font.py` | `+jp` 가 쓰는 글자만 담아 `fonts/japanese/` 의 woff2 와 `COVERAGE.txt` 를 다시 만든다 |

`+goto` 는 목적지에 `+pos` 앵커가 없어도 브라우저가 오류를 내지 않는다.
눌러 보기 전까지 드러나지 않으므로 `check-links.mjs` 가 필요하다. 다만
**본문이 링크를 설명하는 글이면 오탐이 난다** — `pugs/dev/web/html.pug` 가 예시로
적은 `href="#ref"` 를 진짜 링크로 읽는다. 검사기가 속성과 본문 글자를 가리지 못한다.

**`npm run verify` 는 지금 이 저장소에서 바로 돌지 않는다.** `playwright` 가
`devDependencies` 에 없고 `verify-pages.mjs` 가 크로미움 경로를
`/opt/pw-browsers/chromium` 으로 박아 두었다(리눅스 경로). 쓰려면
`npm i -D playwright` 후 `npx playwright install chromium` 하고 그 줄의
`executablePath` 를 지워 기본 탐색에 맡긴다.

### `docs/` — 자습서 재작성 규격

`pugs/fundamental/` 문서를 **중학교 수준에서 출발해 학사 수준까지 혼자 읽을 수 있게**
다시 쓰는 작업의 규격이다. 여러 사람(또는 여러 에이전트)이 장을 나눠 쓸 때의 계약이기도 하다.

| 경로 | 무엇 |
|---|---|
| `docs/SELF-STUDY-SPEC.md` | 재작성 규칙 7개 조항. **무엇을** 지켜야 하는가 |
| `docs/REWRITE-WORKFLOW.md` | 작업 절차. **어떻게** 하는가 — pug 함정, 그림 만드는 법, 검사, 보고 형식 |
| `docs/REFERENCE-chapter.pug.txt` | 완성 기준 견본 한 장. 밀도와 문체를 여기에 맞춘다 |
| `docs/ledger/` | 과목별 **장 사이 인계 목록.** 독자가 그 장에 도착했을 때 무엇을 이미 아는가 |

`docs/ledger/` 에는 `chemistry.md` `biology.md` `mathematics.md` `linear-algebra.md`
`statistics.md` `algorithm.md` `mcs.md` `logic.md` `philosophy.md` `psychology.md`
`japanese.md` 열하나가 있다. **물리는 원장 없이 쓴 문서라 없다.**

`japanese.md` 는 다른 것들과 성격이 하나 더 있다. 어학 문서라 규격 본문에 없는 제약
(소리·필순을 다루지 않는 이유, 그림 안 한자 금지, 폰트 서브셋 관리)을 §0·§5·§7 이
담고 있다. **어학 문서를 더 쓴다면 규격보다 그것을 먼저 읽어라.**

### `imgs/` 와 `imgs-generated/`

둘 다 `YYYYMM` 폴더로 나뉜다. `imgs/` 에 원본을 넣으면 빌드가
`imgs-generated/` 에 500·1200·2000 폭의 avif·webp 를 만들고(gif 는 webp 만),
`+w3img` 가 `<picture>` 로 골라 쓴다. **`imgs-generated/` 는 손대지 마라.**

### `Repositories/` — 인용하는 코드

글에서 `+codeBtn('/Repositories/…')` 로 여는 실제 코드 파일이다. `codeBtn` 은 눌렀을 때
그 경로를 `fetch` 하므로 **참조되는 파일은 배포에 남아 있어야 한다.**
플랫폼·프로젝트별로 나뉜다(`Android/` `Gradle/` `JavaScript/` `Node/` `Python/`
`STS/` `Single/` `VisualStudio/` `Eclipse/` 등). `Single/` 은 프로젝트에 속하지 않는
단일 파일 조각들이다.

추적 파일 2,041개 · 132개 프로젝트이고, 그중 **문서가 실제로 가리키는 것은 800개**다.
나머지는 대부분 그 예제를 돌리기 위한 빌드 파일(gradle 래퍼 53벌, `.classpath` 49개,
`.project` 58개)이다. 문서 46개가 이곳을 가리키고, `spring_framework.pug` 하나가 254개로
가장 많다.

**여기 성격이 다른 것 둘이 섞여 있다.** `iroiro-downloader/` 와 `usb-tether/` 는
인용되는 코드 조각이 아니라 자체 `README.md`·`CLAUDE.md` 를 가진 **독립 애플리케이션**이고,
어느 문서도 가리키지 않는다. `usb-tether/output/` 에는 서드파티 실행 파일이 들어 있어
**`Repositories/` 전체 45.4 MB 중 39 MB 가 이 한 폴더다.** 배포 워크플로가 저장소를
통째로 올리므로 그 파일들은 지금 공개 주소로 내려받아진다.

### `posts/` — 서비스되는 HTML

`pugs/` 와 같은 구조다: `book/` `daily-life/` `dev/` `fundamental/` `project/`.
`db/` 와 `front/` 는 대응하는 `.pug` 가 더 이상 없는 **옛 산출물**이다.
지우면 그 주소로 들어오던 링크가 끊기므로 그대로 두었다.

### `files/`

| 경로 | 무엇 |
|---|---|
| `files/sitemap.txt` | 사이트맵. 빌드가 만든다 |
| `files/mcs.pdf` | MIT 6.042 *Mathematics for Computer Science* 교재. `pugs/fundamental/mcs.pug` 의 바탕 |

### `fonts/`

`fonts/mathjax-newcm/` — 수식용 New Computer Modern woff2 105개(GUST Font License).
빌드 시점에 수식을 미리 렌더하므로 브라우저는 MathJax 를 돌리지 않고 이 폰트만 받는다.

`fonts/japanese/` — 일본어 문서용 Noto Sans CJK JP 서브셋 woff2 2개(SIL OFL 1.1,
400·700 합계 약 165 KB). **문서가 쓰는 한자만** 담고 있어 문서에 종속된다. 새 한자를
쓰면 폰트에 없어 폴백하는데 폴백은 아무 경고도 내지 않으므로, `npm run check` 가
`COVERAGE.txt` 와 견줘 잡는다. 다시 만드는 절차는 `fonts/japanese/README.md`.

---

## 글 하나가 화면에 나오기까지

```
pugs/fundamental/physics.pug          글의 원본
  │  ├── include source/skeleton.pug  레이아웃과 믹스인
  │  ├── +w3img('/figures/physics/…') figures/physics-*.mjs 가 만든 SVG
  │  ├── +w3img('/d2/physics/…')      d2/physics/*.d2 를 렌더한 SVG
  │  └── +codeBtn('/Repositories/…')  인용 코드
  ↓  npm run build-pug
posts/fundamental/physics.html        수식까지 미리 렌더된 최종 HTML
  ↓  git push → .github/workflows/static.yml
GitHub Pages
```
