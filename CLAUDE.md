# Dong-gi.github.io 작업 지침

Pug → 정적 HTML 개인 블로그. `pugs/**/*.pug` 가 `posts/**/*.html` 로 렌더된다.

## 빌드

```bash
npm ci
npm run build        # 전체 렌더
npm run build-new    # 최근 10분 내 수정된 파일만
npm run figures      # figures/*.mjs → figures/<과목>/*.svg
```

Node 24 이상, 다이어그램 렌더에는 `d2` 바이너리가 필요하다 (없으면 해당 파일만 건너뛴다).

수식은 **빌드 시점에 미리 렌더**된다(MathJax 4 CHTML + assistive MathML). 브라우저에서
MathJax 를 돌리지 않으므로 `tex-chtml.js` 는 없고, 폰트는 `fonts/mathjax-newcm/` 에서
직접 서비스한다.

## 새 포스트를 추가할 때

1. `pugs/<카테고리>/<이름>.pug` 를 만든다.
2. **`source/posts.json` 에 항목을 등록한다.** 등록하지 않으면 렌더는 되지만
   사이드바 목록·사이트맵·갱신일 추적에서 빠진다.
   ```json
   { "category": ["기초 과목"], "file": "fundamental/physics.html", "title": "일반물리학" }
   ```
   `file` 은 `.html` 확장자다. `category` 는 배열이며 `/` 로 계층, 원소 여러 개로 다중 소속을 표현한다.
3. `pugs/` 아래에는 `.pug` 와 `.d2` 만 둔다. 빌드가 `pugs/` 의 모든 파일을 렌더 대상으로
   훑기 때문에 `.md` 같은 파일을 넣으면 쓸데없는 실패 로그가 남는다.

## Pug 규약

- 들여쓰기 스페이스 4칸. 탭 금지.
- 모든 포스트는 `include ../../source/skeleton.pug` 후 `+post({ title, useMath })` 로 시작한다.
- 수식을 쓰면 `useMath: true`. **인라인 구분자는 `식[` … `]식`** 이고 MathJax 는 `tags:'ams'` 설정이다.
- 본문에서 부등호는 LaTeX 명령(`\lt \gt \le \ge`)이나 HTML 엔티티(`&lt;` `&gt;`)로 쓴다.
- 산문은 반드시 태그(`p`, `li` …) 아래 두거나 `|` 로 시작한다. 평문으로 시작한 줄은 태그로 해석된다.
- 굵게는 `<b>...</b>`. `**굵게**` 는 pug 가 처리하지 않아 화면에 별표가 그대로 찍힌다.
- **`+ths` / `+tds` 는 인자를 이스케이프한다.** 표 칸에 `<b>` 나 `&#34;` 를 넣으면
  태그가 그대로 보인다. 표 칸에는 평문과 `식[...]식` 만 넣고, 그 안의 역슬래시는
  두 번 쓴다: `+tds('규칙', '식[\\log(xy) = \\log x + \\log y]식')`
- `+w3img` 는 `source/img-map.json` 에 등재된 이미지면 avif/webp 반응형 세트를 쓰고,
  등재되지 않았으면 평범한 `<img>` 로 폴백한다. **생성 SVG 는 등록할 필요가 없다.**
- 믹스인을 산문 안에서 `#[+example(...)]` 처럼 부르면 예제 카운터가 올라간다.
  링크(`+goto`, `+asA`)와 앵커(`+pos`) 외에는 인라인으로 부르지 마라.

## 주요 믹스인 (`source/skeleton.pug`)

| 믹스인 | 용도 |
|---|---|
| `+post(options)` | 문서 골격. `{ title, useMath }` |
| `+example(label?)` | 예제 블록. 번호 자동. `label` 을 주면 그 문구를 쓴다 |
| `+solution(title?, open?)` | 풀이. 기본 접힘. `+solution('증명', true)` 로 펼침 |
| `+table()` / `+ths(...)` / `+tds(...)` | 표 |
| `+asA(href, text?)` | 외부 링크 |
| `+asCode(lan, title?)` / `+asInlineCode(code, lan?)` | 코드 |
| `+codeBtn(options)` | 외부 코드 파일 열기 버튼 |
| `+pos(name)` / `+goto(name, href?)` | 앵커와 링크 |
| `+bookInfo(options)` | 참고 도서 카드 |
| `+legacy(version, note?)` | 구버전 서술 접기 |
| `+w3img(src, description?)` | 반응형 이미지 |

`+example` 은 문서마다 1부터 자동 번호를 붙이고 `id="example-N"` 앵커를 남긴다.
번호를 손으로 적지 말 것 — 예제를 중간에 끼워 넣어도 뒤 번호를 고칠 필요가 없게 한 장치다.

`+goto(name, href)` 는 `href#pos<해시>` 로 가는 링크를 만들고, 그 목적지에는
`+pos(name)` 이 있어야 한다. **헤딩만 있고 `+pos` 가 없으면 링크가 빈 곳으로 떨어진다.**
브라우저는 이때 아무 오류도 내지 않으므로 `npm run check-links` 로 확인한다.

링크의 두 종류를 구분해 쓴다.

- **문서 안의 절**을 가리키면 `+goto('절 이름', '/posts/…​.html')`. 목적지에 `+pos` 를 둔다.
- **문서 전체**를 가리키면 `+asA('/posts/…​.html', '문서 이름')`. 문서 제목으로는 앵커를 두지 않는다.

## 그림

| 종류 | 어디에 | 산출 |
|---|---|---|
| 좌표계·곡선·벡터·기하 | `figures/<과목>-<블록>.mjs` | `figures/<과목>/<이름>.svg` |
| 상자와 화살표(흐름·분류·구조) | `d2/<과목>/<이름>.d2` | 같은 경로의 `.svg` |

`figures/lib.mjs` 가 축·곡선·화살표·라벨을 만드는 최소 라이브러리다. 의존성이 없고
다크 모드를 SVG 내부 미디어 쿼리로 처리한다. 자세한 함정은 `docs/REWRITE-WORKFLOW.md` §5.

## `pugs/fundamental` 집필 기준

기초 과목 문서의 목표는 **문서 하나로 기초부터 학사 과정 수준까지 자습이 가능**한 것이다.
읽는 사람에게 가정하는 것은 **중학교 수학까지**이고, 그 위의 도구는 필요한 자리에서
그때그때 도입한다. 전체 규칙은 **`docs/SELF-STUDY-SPEC.md`** 에 있다.

- 각 절(`h2`)마다 `+example` 을 최소 2개 두고 전부 `+solution` 을 붙인다.
- 풀이는 답만 쓰지 않는다. 무엇을 묻는지 → 무엇을 아는지 → 왜 그 식인지 →
  계산 → 답이 말이 되는지 순서로 쓴다.
- 문서 앞에 `이 문서를 읽는 방법`(선행 지식·학습 순서)과 `참고문서`, 뒤에 `더 공부할 것`을 둔다.
- 물리 상수 등 수치는 CODATA/SI 기준값을 쓴다. 확신할 수 없는 값은 기호로 남기고
  출처가 불분명한 주장은 넣지 않는다.

### 단위 정책

서술·예제·풀이의 모든 수치는 **SI 단위와 SI 허용 단위만** 쓴다.

- SI 허용 단위(BIPM SI 백서 9판 Table 8)는 `min h d ° ′ ″ ha L t Da eV au Np B dB` 가 전부다.
  `°C` 는 허용 단위가 아니라 SI 유도단위이므로 제한 없이 쓴다.
- **야드·파운드계 금지** — ft, lb, psi, °F, mile, inch, gallon, hp, Btu. 단위계 변환 문제도 만들지 않는다.
- 목록에 없는 비SI 단위는 계산에 쓰지 않고 다음으로 대체한다:
  atm/bar/mmHg/Torr → `Pa`·`kPa`, cal/kcal → `J`·`kJ`, Å → `pm`·`nm`,
  rpm → `rad/s`·`rev/min`, 몰농도 M → `mol/L`, 기체상수는 `8.314 J/(mol·K)`,
  표준압력은 `100 kPa`(IUPAC 표준상태).
- 각 문서 앞부분에 `h3` 로 "SI 허용 단위와, 다른 책에서 만나는 비SI 단위" 절을 한 번만 두고
  환산표를 정리한다. 그 표 바깥에서는 비SI 단위를 언급하지 않는다.
  형식은 `pugs/fundamental/physics.pug` 의 같은 절을 따른다.

## 자습서 재작성 작업

기초 과목 문서를 자습서로 다시 쓰는 작업의 규격과 절차는 `docs/` 에 있다.

| 파일 | 무엇 |
|---|---|
| `docs/SELF-STUDY-SPEC.md` | 재작성 규칙 7개 조항. 무엇을 지켜야 하는가 |
| `docs/REWRITE-WORKFLOW.md` | 작업 절차. 어떻게 하는가(문법 함정·그림·검사·보고) |
| `docs/REFERENCE-chapter.pug.txt` | 완성 기준 견본 한 장 |
| `docs/ledger/<과목>.md` | 장 사이 인계 목록. 독자가 그 장에서 무엇을 이미 아는가 |

여러 담당자가 동시에 장을 쓸 때는 **원장(ledger)이 유일한 계약**이다.
장을 끝낸 사람은 "이 장이 새로 도입한 것"을 보고하고, 그것이 다음 원장이 된다.

진행 상황(2026-08 기준): 물리·화학·생물·기초수학·선형대수·통계학 재작성 완료.
mcs·알고리즘은 아직 개조식 목록이고 `+example` 이 하나도 없다.
mcs 는 `h3.fake 문제 N` 으로 문제 24개를 수동 번호로 달고 있으며 풀이가 없다.

## 확인 명령

검사 스크립트는 `scripts/` 에 있다. 저장소 구조 전반은 `README.md` 를 보라.

```bash
node scripts/check-chapter.mjs <장 조각.pug>   # 장 조각 9가지 검사
npm run check                                   # 기초 과목 문서 11가지 검사
npm run check-links                             # +goto 앵커가 실재하는가 (빌드 뒤에)
npm run verify                                  # 실제 브라우저로 404·수식·그림·가로 넘침

# 단일 파일 렌더 확인
node -e "console.log(require('pug').renderFile('pugs/fundamental/physics.pug',{imgMap:{}}).length)"

# 수식 구분자 짝 확인 (두 수가 같아야 한다)
f=pugs/fundamental/physics.pug; grep -o '식\[' $f | wc -l; grep -o '\]식' $f | wc -l
```
