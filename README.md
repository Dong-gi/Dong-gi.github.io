# Dong-gi.github.io

개인 공부 기록을 담은 정적 사이트. **Pug 로 쓰고 빌드해서 순수 HTML 로 배포한다.**
`main` 에 push 하면 GitHub Actions 가 저장소를 통째로 GitHub Pages 에 올린다.
즉 **빌드 산출물(`posts/`, `index.html`, `figures/*/*.svg` …)도 커밋한다.**

```bash
npm ci
npm run build      # pug → posts/*.html, 수식 미리 렌더, 그림 생성, 색인 갱신
npm run check      # 기초 과목 문서 검사
npm run verify     # 실제 브라우저로 페이지 확인
```

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
| `posts/` | `pugs/` 를 렌더한 **최종 HTML**. 실제로 서비스되는 페이지 | `npm run build` |
| `imgs-generated/` | `imgs/` 를 반응형 avif·webp 로 변환한 것 | `npm run build` (원본이 바뀐 것만) |
| `index.html`, `404.html` | 첫 화면과 오류 페이지 | `npm run build` |
| `fonts/` | 자체 서비스하는 수식 폰트 | 고정. MathJax 판올림 때만 |

`figures/<과목>/*.svg` 와 `d2/**/*.svg` 도 산출물이지만 소스와 같은 폴더에 있다
(아래 1레벨 표 참고).

### 도구와 설정

| 경로 | 무엇 | 누가·언제 쓰는가 |
|---|---|---|
| `scripts/` | **품질 검사 스크립트 4종.** 문서·링크·페이지를 기계가 본다 | 문서를 고친 뒤, 커밋 전 |
| `docs/` | 자습서 재작성의 **규격·절차·인계 목록** | `pugs/fundamental/` 을 다시 쓸 때 |
| `CLAUDE.md` | 저장소 규약(pug 문법, 믹스인, 단위 정책, 확인 명령) | 어떤 작업이든 시작 전에 |
| `README.md` | 이 문서. 구조 안내 | 처음 왔을 때 |
| `package.json`, `package-lock.json` | 의존성과 명령 정의 | 빌드·검사 명령을 바꿀 때 |
| `tsconfig.json` | `source/build.ts` 의 타입 설정 | 빌드 스크립트를 고칠 때 |
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
| `_to_delete/` | **지워도 되는 것을 모아 두는 곳.** 확인 후 폴더째 삭제한다 |
| `node_modules/` | 의존성. git 무시 |

---

## 1레벨

### `pugs/` — 글의 원본

카테고리가 그대로 `posts/` 의 폴더 구조가 된다.
새 글을 넣으면 **`source/posts.json` 에도 등록해야** 사이드바·사이트맵·갱신일에 잡힌다.

| 경로 | 무엇 |
|---|---|
| `pugs/fundamental/` | **기초 과목 자습서.** 물리·화학·생물·기초수학·선형대수·통계학·이산수학·알고리즘 |
| `pugs/dev/` | 개발 관련 정리. 언어·프레임워크·DB·인프라별 하위 폴더 |
| `pugs/book/` | 읽은 책 기록 |
| `pugs/project/` | 프로젝트에서 겪은 문제와 해결(`error.pug`, `tip.pug`) |
| `pugs/daily-life/` | 생활 기록 |

`pugs/` 아래에는 `.pug` 만 둔다.

### `figures/` — 그림 생성기

의존성 없이 SVG 문자열을 만든다. 산출물이 `<img>` 로 삽입되므로 페이지 CSS 가 닿지 않고,
그래서 다크 모드까지 SVG 안에서 처리한다.

| 경로 | 무엇 | 누가·언제 |
|---|---|---|
| `figures/lib.mjs` | **공용 라이브러리.** 축·곡선·화살표·라벨·팔레트 | 새 그림을 만들 때 여기부터 읽는다 |
| `figures/build.mjs` | 모든 생성기를 돌려 SVG 를 쓴다 | `npm run figures`, 빌드가 자동 호출 |
| `figures/preview.mjs` | 만든 SVG 를 브라우저로 열어 대조용 PNG 로 뽑는다 | 라벨 겹침을 눈으로 확인할 때. `node figures/preview.mjs <과목> [이름조각]` |
| `figures/<과목>-<블록>.mjs` | 과목별 그림 정의. 한 과목을 여러 파일로 나눠 동시 작업 충돌을 막는다 | 그림을 추가·수정할 때 |
| `figures/<과목>/` | **생성된 SVG.** 손으로 고치지 마라 — 다시 빌드하면 덮인다 | 문서에서 `+w3img('/figures/<과목>/이름.svg', …)` 로 참조 |

과목 폴더: `physics/` `chemistry/` `biology/` `mathematics/` `linear-algebra/` `statistics/`
(`mathematics/` 는 `pugs/fundamental/elementary-mathematics.pug`,
`statistics/` 는 `probability.pug` 에 대응한다.)

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
| `source/build.ts` | **빌드 전체.** pug 렌더, MathJax 미리 렌더, d2 렌더, 이미지 변환, 색인·사이트맵 생성을 워커로 돌린다 | 빌드 동작을 바꿀 때 |
| `source/skeleton.pug` | 모든 페이지의 레이아웃과 **믹스인 정의**(`+post` `+example` `+table` `+w3img` `+goto` …) | 새 믹스인이 필요할 때 |
| `source/default.css` | 사이트 전체 스타일 | 디자인을 바꿀 때 |
| `source/default.js` | 목차 생성·검색·다크 모드 등 페이지 동작 | 동작을 바꿀 때 |
| `source/default.min.js` | 위를 압축한 것. **산출물** | `npm run compress-js` |
| `source/highlight.pack.js` | 코드 하이라이터(highlight.js 빌드본) | 지원 언어를 늘릴 때만 교체 |
| `source/qrcode.min.js` | QR 코드 생성. 일부 글에서만 쓴다 | — |
| `source/posts.json` | **글 목록과 카테고리.** 새 글을 여기 등록해야 한다 | 글을 추가·이동할 때 |
| `source/posts-compressed.json` | 위를 검색용으로 압축한 것. **산출물** | 빌드 |
| `source/img-map.json` | 이미지 크기 표. 반응형 `<picture>` 를 만들 때 쓴다. **산출물** | 빌드 |
| `source/doc-dates.json` | 글별 최종 갱신일. git 이력에서 계산한다. **산출물** | 빌드 |

### `scripts/` — 품질 검사

기계가 볼 수 있는 것은 기계에 맡긴다. 사람 눈으로는 놓치는 것들이다.

| 경로 | 무엇을 보는가 | 언제 |
|---|---|---|
| `scripts/check-chapter.mjs` | 장 조각 하나: 렌더 성공, 수식 구분자 짝, 예제=풀이, 헤딩 레벨, 금지 단위, 그림 존재 등 9가지 | 장을 하나 쓸 때마다 |
| `scripts/check-doc.mjs` | 완성된 문서 하나: 위 항목 + 장 사이 연속성, 표 칸 이스케이프, d2 경로, 환산표 중복 등 11가지 | 문서를 합친 뒤 (`npm run check`) |
| `scripts/check-links.mjs` | 사이트 전체의 `+goto` 앵커가 실제로 존재하는가 | **빌드 뒤** (`npm run check-links`) |
| `scripts/verify-pages.mjs` | 실제 브라우저로 열어 404·콘솔 오류·수식 렌더·그림 로드·가로 넘침 | 마지막 확인 (`npm run verify`) |

`+goto` 는 목적지에 `+pos` 앵커가 없어도 브라우저가 오류를 내지 않는다.
눌러 보기 전까지 드러나지 않으므로 `check-links.mjs` 가 필요하다.

### `docs/` — 자습서 재작성 규격

`pugs/fundamental/` 문서를 **중학교 수준에서 출발해 학사 수준까지 혼자 읽을 수 있게**
다시 쓰는 작업의 규격이다. 여러 사람(또는 여러 에이전트)이 장을 나눠 쓸 때의 계약이기도 하다.

| 경로 | 무엇 |
|---|---|
| `docs/SELF-STUDY-SPEC.md` | 재작성 규칙 7개 조항. **무엇을** 지켜야 하는가 |
| `docs/REWRITE-WORKFLOW.md` | 작업 절차. **어떻게** 하는가 — pug 함정, 그림 만드는 법, 검사, 보고 형식 |
| `docs/REFERENCE-chapter.pug.txt` | 완성 기준 견본 한 장. 밀도와 문체를 여기에 맞춘다 |
| `docs/ledger/` | 과목별 **장 사이 인계 목록.** 독자가 그 장에 도착했을 때 무엇을 이미 아는가 |

`docs/ledger/` 에는 `chemistry.md` `biology.md` `mathematics.md`
`linear-algebra.md` `statistics.md` 가 있다.

### `imgs/` 와 `imgs-generated/`

둘 다 `YYYYMM` 폴더로 나뉜다. `imgs/` 에 원본을 넣으면 빌드가
`imgs-generated/` 에 500·1200·2000 폭의 avif·webp·jpeg 를 만들고,
`+w3img` 가 `<picture>` 로 골라 쓴다. **`imgs-generated/` 는 손대지 마라.**

### `Repositories/` — 인용하는 코드

글에서 `+codeBtn('/Repositories/…')` 로 여는 실제 코드 파일이다.
플랫폼·프로젝트별로 나뉜다(`Android/` `Gradle/` `JavaScript/` `Node/` `Python/`
`STS/` `Single/` `VisualStudio/` `Eclipse/` 등). `Single/` 은 프로젝트에 속하지 않는
단일 파일 조각들이다.

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

---

## 글 하나가 화면에 나오기까지

```
pugs/fundamental/physics.pug          글의 원본
  │  ├── include source/skeleton.pug  레이아웃과 믹스인
  │  ├── +w3img('/figures/physics/…') figures/physics-*.mjs 가 만든 SVG
  │  ├── +w3img('/d2/physics/…')      d2/physics/*.d2 를 렌더한 SVG
  │  └── +codeBtn('/Repositories/…')  인용 코드
  ↓  source/build.ts
posts/fundamental/physics.html        수식까지 미리 렌더된 최종 HTML
  ↓  git push → .github/workflows/static.yml
GitHub Pages
```
