# 블로그 현대화 마스터 플랜

작성일: 2026-08-01

## 채택 기준

**GA 후 6개월 이상 경과한 버전만 채택한다.** 기준일 2026-08-01 기준으로 **2026-02-01 이전 GA**가 적격이다. 도구 생태계(린터, 빌드 플러그인, IDE)가 따라오지 못하는 사례를 피하기 위한 규칙이다.

| 기술 | 예제 프로젝트 현재 | 목표 (적격) | GA | 비고 |
|---|---|---|---|---|
| Java | **13** (25개), 11, 15, 1.8 | **JDK 25 LTS** | 2025-09-16 | 13·15는 지원 완전 종료. 17도 Premier 2026-09 종료 |
| Gradle | **6.3** (26개), 5.4, 5.6.4, 7.x | **9.3.1** | 2026-01-29 | Gradle 5/6은 JDK 17+ 에서 실행 불가, 7.x는 JDK 21+ 불가 |
| Spring Boot | 2.2.1 ~ 2.6.0 | **4.0.x** | 2025-11-20 | 3.x 전 브랜치 OSS 지원 종료(2026-06-30). 4.0도 2026-12-31 종료 → 12월에 4.1로 재이관 필요 |
| .NET | **6.0** (17개) | **.NET 10 LTS** | 2025-11-11 | .NET 6은 2024-11-12 종료 |
| Android | compileSdk **31** (7개) | **compileSdk 36 + AGP 9.0.0** | AGP 2026-01-15 | **Play targetSdk 36 강제가 2026-08-31 — 30일 남음** |
| Node.js | `@types/node ^25` | **`^24`** | 2025-10-28 | Node 25는 2026-06-01 EOL |
| Python | 문서가 3.8 기준 | **3.14** | 2025-10-07 | 3.8은 2024-10-07 EOL |
| PostgreSQL | 문서에 `PostgreSQL10Dialect` | **18** | 2025-09-25 | |
| Redis | 문서는 8.8 반영 (기준 초과) | **8.4.x** | 2025-11-18 | 8.0부터 AGPLv3. Valkey 9.0(BSD, 2025-10-21)도 적격 |
| Kubernetes | — | **1.35** | 2025-12-17 | 1.33 이하 EOL |
| TypeScript | `^7.0.2` | **결정 필요** ↓ | — | 6.0=2026-03-23, 7.0=2026-07-08 — **둘 다 6개월 미달** |

### TypeScript 결정 사항

현재 `package.json`이 `typescript: ^7.0.2`인데, TS 7.0은 GA 후 3주밖에 지나지 않았다. 6개월 룰을 엄격히 적용하면 적격한 것은 **5.9.x**뿐이다.

TS 7은 Go 네이티브로 재작성된 컴파일러라 변화 폭이 크다. 확인된 제약:

- **programmatic API 미제공** — typescript-eslint, webpack loader, Vue/Svelte/Astro 템플릿 검사가 아직 동작하지 않는다. API는 7.1(2026-10경 예상, **추측**)에 나올 예정
- `target: es5`, `baseUrl`, `moduleResolution: node`, `module: amd/umd/systemjs`, `downlevelIteration`이 전부 하드 에러

다만 이 블로그는 `node source/build.ts`로 Node 네이티브 타입 스트리핑을 쓰고 tsc는 타입 검사 용도로만 쓰므로 노출 면적이 작다. 세 가지 선택지:

| 안 | 내용 | 6개월 룰 |
|---|---|---|
| A (엄격) | `^5.9` 로 내림 | 충족 |
| B (절충, 권장) | `~6.0.3` 으로 내림. TS 6.0은 2026-09-23에 6개월 도달 | 약 2개월 부족 |
| C (현상 유지) | `^7.0.2` 유지 | 미충족 |

`@types/node`는 어느 안이든 `^25` → **`^24`** 로 내려야 한다.

---

## Phase 0 — 확인된 결함 수정 (즉시)

전부 코드에서 직접 확인한 사실이다.

### 0-1. `files/sitemap.txt` URL이 전부 깨져 있음 ⚠️ 최우선

`source/build.ts:250`:

```ts
.map((post) => `https://dong-gi.github.io${post.file}`)
```

`post.file`은 `"dev/aws.html"` 형태로 선행 슬래시도 `posts/` 접두사도 없다. 결과:

```
https://dong-gi.github.iobook/0/001.html      ← 실제 생성값
https://dong-gi.github.io/posts/book/0/001.html  ← 있어야 할 값
```

**사이트맵에 등록된 250개 URL이 전부 무효**다. `robots.txt`가 이 사이트맵을 가리키고 있어 색인에 직접 영향을 준다. 한 줄 수정으로 해결된다.

### 0-2. Universal Analytics 태그가 죽어 있음

`source/skeleton.pug:164`가 `UA-143098403-1`을 로드한다. Universal Analytics는 2023-07-01에 데이터 수집을 중단했고 표준 속성은 2024년 7월 삭제됐다. **3년 넘게 아무 데이터도 수집되지 않고 있다.** GA4로 이전하거나, 방문자 추적이 불필요하면 스크립트를 제거해 로드 비용을 줄인다.

### 0-3. 깨진 코드 참조 2건

`Repositories/Config/` 디렉터리가 존재하지 않는데 pug에서 참조한다.

- `Repositories/Config/config-client.properties`
- `Repositories/Config/webapp.properties`

해당 코드 버튼을 누르면 실패한다. 파일을 복원하거나 참조를 제거한다.

### 0-4. `posts.json` 미색인 문서 11개

pug 소스가 있고 HTML도 생성되지만 `source/posts.json`에 없어 사이트맵·검색·목록 어디에도 노출되지 않는 문서들이다. 대부분 `pugs/not-registered/` 소속이다(AI, K8S, ELK, CI/CD, JavaFX, Ruby, tomcat, Unity, 양자컴퓨터).

의도적 미공개라면 `not-registered/`를 빌드 대상에서 제외해 HTML 자체를 만들지 않는 편이 낫다. 공개할 것이라면 `posts.json`에 등록한다. **현재는 "생성은 되지만 아무도 못 찾는" 어중간한 상태**이며, 이번 리다이렉트 작업으로 15건이 이쪽을 가리키게 됐으므로 정리가 필요하다.

### 0-5. 빌드가 고아 파일을 감지하지 못함

`source/build.ts`는 pug → html 단방향 생성만 한다. pug를 지워도 html이 남고, `posts.json`과의 정합성도 검사하지 않는다. 이번에 355개가 쌓인 근본 원인이다.

빌드에 검증 단계를 추가한다.

- `posts/` 에 pug 소스도 리다이렉트 스텁도 아닌 html이 있으면 경고
- `posts.json` 항목 중 실제 파일이 없으면 실패
- pug는 있는데 `posts.json`에 없는 문서 목록 출력
- 사이트맵 URL 형식 검사

`tools/audit-orphans.mjs`의 인벤토리 로직을 재사용할 수 있다.

---

## Phase 1 — 포스트 콘텐츠 최신화

`pugs/` 262개 문서 / 51,354줄. 도서 요약(`book/**` 162개)과 `daily-life`는 시의성이 없어 대상에서 제외하면 **실질 대상은 약 90개 문서 / 46,000줄**이다.

### 1-1. 구버전 접기 패턴 정의

먼저 `source/skeleton.pug`에 mixin을 추가해 표기를 통일한다.

```pug
//-
    구버전 서술을 접어두는 블록.
    version : 접히는 내용의 기준 버전 (예: 'Python 3.8')
    note    : EOL 날짜 등 부가 설명 (선택)
mixin legacy(version, note)
    details.legacy.w3-leftbar.w3-border-gray
        summary.w3-small= `구버전 기록 — ${version}`
        if note
            p.w3-small.w3-text-gray= note
        block
```

적용 형태:

```pug
h1 Python
p 이 문서는 Python 3.14 기준입니다.
//- ... 최신 내용 ...

+legacy('Python 3.8', '2024-10-07 지원 종료')
    //- 기존 내용을 통째로 이 아래로 들여쓰기
```

기존 본문을 삭제하지 않고 들여쓰기만 하면 되므로 diff가 깨끗하고, 되돌리기도 쉽다.

### 1-2. 갱신 우선순위

전수 조사 결과 상위 20개다. 점수 = (마지막 실질 내용 변경 이후 경과 연수) × log10(줄 수).

> 이 저장소에는 pug 40개 이상을 한 번에 건드린 기계적 대량 커밋이 12건 있다(mixin 교체, 디렉터리 이동 등). 단순 `git log -1`은 실제보다 최신으로 보이므로, rename 추적 + 대량 커밋 제외로 "마지막 실질 변경일"을 따로 계산했다.

| # | 문서 | 줄 수 | 실질 변경 | 사유 |
|---|---|---:|---|---|
| 1 | `dev/JVM/spring_framework.pug` | 1,578 | 2021-09 | Spring 5.x + XML. `spring-beans-3.0.xsd`, 5.3.0-SNAPSHOT 링크. `jakarta.*` 0건 |
| 2 | `dev/JVM/spring_servlet.pug` | 1,338 | 2021-05 | Spring 5.x + `javax.*` + JSP/web.xml 전제. Boot 3/4 전환 미반영 |
| 3 | `dev/python/standard.pug` | 2,148 | 2021-05 | 최대 Python 문서인데 `Since 3.8`이 상한. 3.9~3.14 변화 전무 |
| 4 | `dev/dotnet/csharp.pug` | 1,562 | 2022-04 | `.NET 6.0` 명시, C# 10이 마지막 절. C# 11~14 / .NET 8~10 누락 |
| 5 | `dev/python/basic.pug` | 359 | 2021-05 | **제목이 "Python 3.8"**. 입문 문서라 유입 큼 |
| 6 | `dev/python/data_model.pug` | 452 | 2020-08 | 제목 "Python 3.8 데이터 모델". 6년 미수정 |
| 7 | `dev/JVM/version.pug` | 212 | 2021-06 | "Java 버전별 추가사항"인데 17에서 멈춤. **목적상 최신성이 생명** |
| 8 | `dev/JVM/freemarker_built_in.pug` | 256 | 2020-08 | 제목에 2.3.28 박제. FreeMarker 4종 중 최대 |
| 9 | `dev/python/built_in_type.pug` | 398 | 2020-08 | 기본형 문서, 참조 빈도 높음 |
| 10 | `dev/python/built_in_function.pug` | 278 | 2020-08 | `Since 3.9` 상한 |
| 11 | `dev/JVM/guava.pug` | 771 | 2022-03 | 제목 "Guava 30.1" |
| 12 | `dev/JVM/apache.commons.lang.pug` | 415 | 2021-05 | 제목 "3.9" 박제 |
| 13 | `dev/JVM/groovy.pug` | 426 | 2021-05 | `Since 3.0.0` 상한. Groovy 4/5 누락 |
| 14 | `dev/JavaScript/jQuery.pug` | 142 | 2020-08 | 주제 자체가 레거시 + CDN 2.2.0. **존치/아카이브 판단 필요** |
| 15 | `dev/JVM/spring_reactive.pug` | 251 | 2021-04 | 본문에 `작업 기준 버전 : 5.3.5` 명시 |
| 16 | `dev/JVM/basic.pug` | 373 | 2021-10 | Java 입문인데 `jdk-10.0.2` 예시, javase/12 링크 |
| 17 | `project/error.pug` | 367 | 2026-03 | L111~193이 종료된 Heroku 무료 티어 로그. **부분 정리만** 필요 |
| 18 | `dev/rpi.pug` | 395 | 2025-03 | 내용은 2017년 Raspbian Stretch + OpenCV 3.3. Pi 5 / Bookworm / picamera2와 단절 |
| 19 | `not-registered/CI-CD.pug` | 129 | 2021-01 | CI/CD 문서에 GitHub Actions가 없음 |
| 20 | `dev/JVM/java_ee.pug` | 361 | 2024-02 | `javax.*` / JSP / web.xml 중심. Jakarta EE 이관 반영 필요 |

**최신성 양호 (작업 불필요)**: `dev/DB/redis.pug`(Redis 8.8·Valkey 9.1 반영), `dev/JavaScript/node.pug`(v24.2.0), `dev/JavaScript/JavaScript.pug`(ES2025), `dev/DB/PostgreSQL.pug`, `dev/web/html.pug`, `dev/web/css.pug`.

### 1-3. 제목에 박제된 버전 (일괄 처리)

제목 자체에 버전이 들어간 문서는 URL을 바꾸지 않고 제목만 갱신하는 편이 안전하다(리다이렉트가 또 필요해지지 않도록).

`freemarker_*` 4종("2.3.28"), `python/basic`("Python 3.8"), `python/data_model`("Python 3.8 데이터 모델"), `java.base`·`java.net.http`("JDK16"), `guava`("30.1"), `apache.commons.{lang,collections,math,rng}`("3.9"/"4.4"/"3.6.1"/"1.2").

버전을 제목에서 빼고 본문 상단에 "기준 버전" 표기로 옮기는 방식을 권한다.

### 1-4. EOL 기술 문서 처리 방침

| 유형 | 문서 | 방침 |
|---|---|---|
| 주제 자체가 레거시 | `jQuery.pug`, `not-registered/JavaFX.pug`(Swing/RIA), `not-registered/unity.pug`(폐기된 Standard Assets) | 상단에 아카이브 배너 + 현행 대안 링크 |
| 부분 서술만 낡음 | `project/error.pug`(Heroku 무료 티어), `dev/aws.pug`(Amazon Linux 2, 2026-06 EOL), `dev/linux.pug`(CentOS 6/7) | 해당 절만 `+legacy`로 접고 최신 절 추가 |
| 네임스페이스 전환 필요 | `java_ee.pug`, `spring_servlet.pug`, `spring_framework.pug`, `jpa.pug` (`javax.*` → `jakarta.*`) | 전면 개정 대상 |
| 제거된 API 언급 | `dev/web/html.pug`(`<applet>`), `dev/gradle.pug`(`testCompile`), `dev/JVM/lombok.pug`(`@Log4j`) | 해당 줄만 수정 |
| 역사 서술 맥락 | `JavaScript.pug`의 Flash/IE 언급 | **수정 불필요** |

### 1-5. 진행 방식

문서 1건당 컨텍스트가 크므로(최대 2,148줄), 문서 단위로 서브에이전트를 병렬 투입하는 편이 효율적이다. 문서당 절차:

1. 공식 문서에서 현행 버전의 변경사항 확인 (검색 필수, 추측 금지)
2. 기존 본문을 `+legacy(...)`로 감싸기
3. 그 위에 현행 버전 기준 내용 작성
4. 누락된 중요 주제 보강
5. 깨진 외부 링크 확인

---

## Phase 2 — Repositories 정리 및 재작성

`Repositories/` 는 11개 그룹 / 122개 프로젝트. 블로그 본문의 코드 버튼(`+codeBtn`)이 `/Repositories/...` 경로로 파일을 직접 참조한다. **pug 소스에서 807개 경로가 참조**되고 있다.

### 2-1. 삭제 후보 (참조 0건)

pug·index·source 어디에서도 참조하지 않는 프로젝트다.

| 경로 | 판단 |
|---|---|
| `Eclipse/annotation-processing2` | 1·3은 참조되는데 2만 미참조 → 삭제 |
| `JavaScript/Chrome Proxy Extension` | 삭제 |
| `Node/redis-start` | 삭제 |
| `STS/Skeleton-NonSpringBoot` | 삭제 |
| `Single/teamcity` | `not-registered/CI-CD.pug`가 TeamCity를 다루지만 코드 참조는 없음. CI/CD 문서를 GitHub Actions로 개정하면 함께 삭제 |

### 2-2. 삭제하면 안 되는 것 (참조 0건이지만)

| 경로 | 사유 |
|---|---|
| `iroiro-downloader` | 자체 `CLAUDE.md`·`README.md`·`requirements.txt`를 가진 독립 프로젝트. 블로그 예제가 아님 |
| `usb-tether` | 동일. 자체 `CLAUDE.md` + Android 프로젝트 보유 |

두 프로젝트는 블로그와 성격이 다르다. **별도 저장소로 분리하는 편이 맞다고 보이지만, 판단은 필요하다.**

### 2-3. 재작성 대상

| 그룹 | 프로젝트 수 | 현재 | 목표 | 규모 |
|---|---:|---|---|---|
| `STS/mvc-java1` ~ `mvc-java18` | 18 | Spring Boot 2.x, Java 13, Gradle 6.3 | Spring Boot 4.0, JDK 25, Gradle 9.3.1 | **최대**. `javax.*`→`jakarta.*` 전환 포함 |
| `VisualStudio/ConsoleApp1~9`, `WpfApp1~8` | 17 | .NET 6.0 / net6.0-windows | .NET 10 | 지원 종료됨 |
| `Android/Project01~07` | 7 | compileSdkVersion 31, Gradle 6.x | compileSdk 36, AGP 9.0.0 | **Play 강제 시한 8/31** |
| `Eclipse/*` (spring-aop, spring-mvc, spring-transaction 등) | ~18 | Java 8~13, Gradle 5.x | JDK 25, Gradle 9.3.1 | Eclipse 프로젝트 파일(`.classpath`/`.project`) 정리 병행 |
| `STS/mvc-xml1~2`, `logback-maven` 등 | ~5 | Maven, java.version 11~12 | JDK 25 | |
| `Node/*` | ~10 | 버전 미고정 | Node 24 LTS 명시 | `engines` 필드 추가 |
| `Gradle/*`, `Python/*`, `Single/*` | ~20 | 혼재 | 개별 판단 | |

**권장 접근**: `STS/mvc-javaN` 18개는 이름대로라면 Java 버전별 예제일 가능성이 높다. 전부 최신으로 올리면 시리즈의 의도가 사라지므로, **먼저 각 프로젝트가 무엇을 보여주는 예제인지 파악**한 뒤 통합/삭제/유지를 결정해야 한다. 무작정 일괄 업그레이드는 위험하다.

### 2-4. 표준 위반 정리

`git ls-files` 로 **실제 추적 중인 파일**을 확인한 결과다.

| 항목 | 상태 |
|---|---|
| Gradle `testCompile`(Gradle 7에서 제거) → `testImplementation` | 처리 필요 |
| Eclipse/STS `bin/` 컴파일 산출물 25개 커밋됨 | **커밋 18에서 제거** |
| `gradle-wrapper.jar` 커밋 | 정상 관행, 유지 |
| Eclipse `.classpath`/`.project` 커밋됨 | 프로젝트를 그대로 import 하려면 필요. 유지 판단 |

> **정정** — 초안에서 "`local.properties` 와 `.idea/` 가 커밋되어 있다"고 적었으나 사실이 아니다. 두 경로 모두 작업 트리에는 있지만 **git 추적 대상이 아니다**(`git ls-files` 결과 0건). 디스크의 파일만 보고 단정한 오류였다.

### 2-5. 참조 무결성 자동 검사

Phase 0-5의 빌드 검증에 **`+codeBtn`이 가리키는 `/Repositories/...` 경로가 실제 존재하는지** 검사를 추가한다. 현재 2건이 깨져 있고, 프로젝트를 삭제·재작성하면 대량으로 깨질 수 있다. 이 검사가 없으면 Phase 2를 안전하게 진행할 수 없다. **Phase 2의 선행 조건이다.**

---

## Phase 3 — 그 외 현대화 제안

요청하신 두 항목 외에 조사 중 발견한 것들이다.

### 3-1. SEO / 메타데이터 (효과 대비 비용 최상)

현재 `<head>`에 없는 것: `rel=canonical`, Open Graph, Twitter Card, JSON-LD 구조화 데이터, `lastmod`가 포함된 XML 사이트맵.

- **canonical** — 리다이렉트 스텁에는 넣었지만 정작 현행 문서에는 없다. `skeleton.pug`에 추가
- **OG 태그** — Slack·카카오톡 공유 시 미리보기가 나오지 않는다. `og:title`, `og:description`, `og:image`, `og:url`
- **JSON-LD** — `TechArticle` 스키마로 `datePublished`/`dateModified` 노출. `posts.json`에 이미 `mtimeMs`가 있어 바로 쓸 수 있다
- **XML 사이트맵** — 현재는 URL 나열 텍스트다. `<lastmod>`를 넣으면 크롤러가 갱신된 문서를 우선 재방문한다. Phase 0-1 수정과 함께 처리

### 3-2. CI/CD 부재

`.github/` 디렉터리가 아예 없다. 빌드가 수동인 것으로 **추측**된다. GitHub Actions로:

- push 시 `npm run build` → GitHub Pages 배포
- Phase 0-5의 정합성 검사를 CI에서 실행 (고아 파일, 깨진 코드 참조, 사이트맵 형식)
- 링크 체커로 외부 죽은 링크 주기적 검출
- Lighthouse CI로 성능·접근성 회귀 감시

**이게 없으면 Phase 1·2에서 만든 규칙이 지켜지는지 알 수 없다.**

### 3-3. 프로젝트 문서화 (`CLAUDE.md` / `README.md`)

루트 `README.md`가 링크 한 줄뿐이다. 빌드 파이프라인(pug→html, terser, sharp, svgo, d2), 카테고리 규칙, `posts.json` 갱신 절차, 새 글 작성 방법이 어디에도 문서화되어 있지 않다. `Repositories/iroiro-downloader`와 `usb-tether`는 이미 `CLAUDE.md`를 갖고 있는데 정작 블로그 본체에는 없다.

### 3-4. 새 글 작성 워크플로 자동화

현재 새 글을 쓰려면 pug 작성 → `posts.json` 수동 등록 → 이미지 배치 → 빌드를 직접 해야 한다. 스캐폴딩 스크립트(또는 Cowork skill)로 묶으면 실수가 줄고 반복 비용이 사라진다.

### 3-5. 접근성 / 성능

- `default.css`가 26KB, `tex-chtml.js`가 **884KB**다. MathJax는 수식이 있는 문서에서만 조건부 로드하는지 확인 필요
- `offline-service-worker.js`가 있는데 캐시 전략이 현행 파일 목록과 맞는지 점검 필요
- 이미지 파이프라인(sharp로 500/1200/2000px × jpeg/webp/avif)은 이미 잘 되어 있다. `<picture>`/`srcset`으로 실제 서빙되는지만 확인
- `lang="ko-KR"` 문서에 영문 코드가 많은데 `<code>` 요소의 언어 표기 검토

### 3-6. Disqus 의존성

구 HTML에 Disqus 카운트 스크립트가 있었다. 현행 `skeleton.pug`에는 없어 보이므로 이미 제거된 듯하나, 댓글 기능을 다시 붙일 계획이라면 GitHub Discussions 기반 giscus 쪽이 광고·추적이 없어 낫다.

### 3-7. 카테고리 체계 재정비

이번 정리로 드러난 것인데, `posts/`에 `java` / `language/java` / `dev/JVM` 처럼 같은 주제가 3개 체계로 존재했다. 현행 `pugs/`는 `dev`, `fundamental`, `book`, `project`, `daily-life`, `not-registered` 6개로 정리되어 있으나 `dev/JVM`에 30개 문서 / 13,269줄이 몰려 있다. 추가 분할을 검토할 시점이다.

---

## 실행 순서

의존 관계를 반영한 순서다.

```
Phase 0  (반나절)   결함 수정 — 사이트맵, GA, 깨진 참조, posts.json, 빌드 검증
   ↓
Phase 3-1 (반나절)  SEO 메타데이터 — Phase 0의 사이트맵 수정과 함께
Phase 3-2 (반나절)  GitHub Actions — 이후 모든 작업의 안전망
Phase 3-3 (반나절)  CLAUDE.md — 이후 모든 세션의 탐색 비용 제거
   ↓
Phase 2-5 (선행)    코드 참조 무결성 검사 ← Phase 2의 전제 조건
   ↓
Phase 1 (병렬)      포스트 최신화        Phase 2 (순차)  Repositories 재작성
  · legacy mixin 정의 먼저                 · 삭제 5건 먼저
  · 문서 단위 서브에이전트 병렬             · Android가 8/31 시한으로 최우선
  · 우선순위 20건부터                       · STS/mvc-javaN은 의도 파악 후 결정
```

**시한이 있는 항목**

| 기한 | 항목 |
|---|---|
| 2026-08-31 | Android targetSdk 36 (Play 강제) — 30일 남음 |
| 2026-09 | Java 17 Premier Support 종료 |
| 2026-09-23 | TypeScript 6.0이 6개월 룰 충족 |
| 2026-11-10 | .NET 8·9 동시 종료 (.NET 10만 남음) |
| 2026-12-31 | Spring Boot 4.0 종료 → 4.1로 재이관 |

---

## 출처

- [Oracle Java SE 지원 로드맵](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [JDK 25 GA 공지](https://mail.openjdk.org/pipermail/announce/2025-September/000360.html)
- [Spring Boot 4.0.0 GA](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/) · [4.1.0 GA](https://spring.io/blog/2026/06/10/spring-boot-4) · [지원 버전](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)
- [Gradle 릴리스](https://gradle.org/releases/) · [호환성 매트릭스](https://docs.gradle.org/current/userguide/compatibility.html)
- [Announcing .NET 10](https://devblogs.microsoft.com/dotnet/announcing-dotnet-10/) · [.NET 지원 정책](https://dotnet.microsoft.com/platform/support/policy/dotnet-core)
- [Play targetSdk 요구사항](https://developer.android.com/google/play/requirements/target-sdk) · [AGP 9.0 릴리스 노트](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Node.js 릴리스 일정](https://nodejs.org/en/about/previous-releases)
- [Python 3.14.0 릴리스](https://pythoninsider.blogspot.com/2025/10/python-3140-final-is-here.html) · [Python 버전 현황](https://devguide.python.org/versions/)
- [PostgreSQL 18 릴리스](https://www.postgresql.org/about/news/postgresql-18-released-3142/)
- [Redis EOL](https://endoflife.date/redis) · [Valkey 릴리스](https://valkey.io/download/releases/)
- [Kubernetes v1.35](https://kubernetes.io/blog/2025/12/17/kubernetes-v1-35-release/)
- [Announcing TypeScript 6.0](https://devblogs.microsoft.com/typescript/announcing-typescript-6-0/) · [Announcing TypeScript 7.0](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/)
