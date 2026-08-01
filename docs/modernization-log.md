# 현대화 작업 로그

`docs/modernization-plan.md` 의 실행 기록. 커밋 1개 = 작업 1개 원칙으로 진행하며, 각 커밋마다 이 문서를 갱신한다.

리뷰 순서는 아래 표의 위에서 아래 방향이다.

| # | 커밋 | 종류 | 계획서 항목 | 리뷰 포인트 |
|---|---|---|---|---|
| 1 | 고아 HTML 308건을 리다이렉트 스텁으로 치환 | chore | 별건 (선행 작업) | 스텁 형식, 보존 47건의 타당성 |
| 2 | 사이트맵 URL에 누락된 `/posts/` 접두사 복원 | fix | Phase 0-1 | 250개 URL이 전부 깨져 있었음 |

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
