# iroiro-viewer 작업 지침

안드로이드 **파일 관리자 + 만능 뷰어**(폰에 보이는 이름 `Files`). 개인용, APK 사이드로드. 스토어 배포 없음.
**이 파일이 이 프로젝트의 유일한 지침 문서다.** 규격·절차·인계 문서를 따로 두지 않는다.

**걷은 기능의 기록은 이 문서에 없다.** 직접 구현했던 오피스·한글 변환기(12·13단계), 앱 안 오피스 편집 화면, 네트워크 저장소(SFTP)와 그것이
남긴 것을 지우던 `LegacyCleanup`, 따로 된 앱 Editor 시절의 연동, 인앱 브라우저 검토, HTML 미리보기(위생을 거친 쪽·스크립트 실행), 그리고
단계·회차별 진행 기록·실측 일지·검토의 경위는 git 이력(마지막으로 그것들이 있던 커밋 `ffe8e8b` — 2026-11-06)과 내려받기 폴더의 백업
`iroiro-backup-20261009`(커밋되지 않은 10-05~10-09 의 것 — SFTP·오피스 편집 회차·Editor)에 있다. 다시 지을 때는 거기서 그때의 설계·실측·함정을 읽는다.

---

## 물려받는 제약

상위 저장소 `Dong-gi.github.io` 는 **GitHub Pages 로 통째로 서비스된다.** 여기 커밋하는
모든 파일이 웹에서 내려받힌다. 규칙은 "커밋해도 되는가"가 아니라 **"공개해도 되는가"** 다.

- 서드파티 바이너리를 커밋하지 않는다. 예외는 `gradle-wrapper.jar`(48KB) 하나다 — 실행
  파일이 아니고 출처가 명확하다. 고정폭 CJK 폰트는 넣지 않기로 했다(아래 '고정폭 글꼴 — 칸이 맞지 않는다'). 편집기 엔진 묶음(`vendor/`)도
  서드파티 바이너리·글꼴이라 git 이 무시하고 `tools/editor/` 가 다시 짓는다.
- `local.properties`·keystore·실세계 문서 표본은 `.gitignore` 로 막혀 있다. **한 번 새면
  이력 재작성 말고는 되돌릴 방법이 없다.**
- 절대경로를 커밋하는 설정(`org.gradle.java.home` 등)을 쓰지 않는다.

---

## 확정 요구사항

사용자 인터뷰로 확정했다. **임의로 줄이지 마라.** 미루는 것은 되지만 미룬다고 적는다.

| | |
|---|---|
| 대상 | minSdk 31(Android 12). 실기기는 Android 12 폰(SM-A908N) 하나 — 확인은 폰 에뮬레이터로만 한다(아래 '에뮬레이터') |
| 저장소 접근 | `MANAGE_EXTERNAL_STORAGE` (모든 파일 접근) |
| 의존 정책 | AndroidX·Kotlin 공식 + **네이티브 코드 없는 성숙한 순수 JVM 오픈소스**까지 |
| 파일 관리 | 복사·이동·삭제·이름변경·새폴더·다중선택·정렬·숨김파일 + **휴지통** |
| 이미지 | 기본 뷰어 + **만화 모드(CBZ·CBR·폴더)** + 애니메이션(GIF·APNG·애니WebP) + EXIF·회전·공유 <br>→ APNG 은 **넣지 않기로 했다**(첫 장면과 고지). GIF·애니WebP 는 만화 뷰어와 이미지 뷰어가 튼다 |
| 미디어 | 컨테이너는 넓게, 코덱은 기기가 주는 만큼(아래 "지원하지 않는 것") |
| 텍스트 | 문법 강조, 인코딩 자동감지(UTF-8·CP949·EUC-KR·Shift_JIS), 대용량 <br>→ 256 KiB 까지 고친다(인코딩·줄 끝·BOM 그대로 — 아래 '14단계 뒤 — 글 편집·새 글 파일·그리기와 메모') |
| 문서 | PDF, EPUB, docx·xlsx·pptx, HWPX, HWP 5.0 <br>→ PDF·EPUB 은 이 앱의 뷰어다. **오피스·한글·ODF·옛 오피스 문서는 편집기(rhwp·ONLYOFFICE — `feature:editor`)의 보기 모드가 보인다**(사용자 — 'PC 에서 보는 것에 가까운 뷰어로'. 아래 '14단계 뒤 — 네트워크를 걷고 편집기를 안으로, 뷰어 교체') |
| 압축 | ZIP·7z·RAR (junrar 의 UnRAR 라이선스를 수용하기로 했다. 고지 의무를 지킨다) |
| 재생 | 백그라운드·알림·이어폰 버튼·오디오 포커스 / 폴더 연속재생·이어보기 / 자막(SRT·ASS·내장) / PiP / 속도·트랙·A-B / **VLC 차용**(아래) |
| UI | 한국어. VLC·Solid Explorer 등 널리 쓰이는 앱의 관행을 따른다 |

**VLC 에서 차용하기로 한 것** — 현재 폴더(하위 폴더 제외)에 오디오를 가진 미디어가 있으면
플로팅 ▶ 버튼이 뜨고, 누르면 그것들로 임시 플레이리스트를 만들어 순차 재생. 셔플 가능.
화면을 꺼도 소리 계속. **블루투스 연결/해제 시 자동 resume/pause.**

**1단계에서 제외** — 네트워크 저장소(SMB·FTP·WebDAV), 앱 잠금, 홈 위젯, 전문검색 인덱스,
용량 분석, 탭·듀얼페인, RAW 사진. (오피스 편집과 레거시 doc·xls·ppt 는 편집기가 한다 — 레거시는 보기 모드로 열고, 고쳐 저장하면
곁에 새 OOXML 이 생긴다. 네트워크 저장소는 하나도 없다.)

**이 프로젝트의 목적은 유휴 토큰을 의미 있게 쓰는 것이다**(사용자). 그래서 위
'제외' 와 아래 '지금 지원하지 않는 것' 은 **영구 결정이 아니다.** 편집(텍스트·문서)처럼
'하지 않는다' 로 적어 둔 것도 필요해지면 다시 연다 — 파일 관리자 기능이 이미 파일을
조작하므로 '이 앱은 파일을 바꾸지 않는다' 는 애초에 앱 전체의 불변식이 아니다. 다시 열 때
지키는 것은 **안전 규칙**이다: 원본을 파괴할 수 있는 경로는 목록(아래 '안전' — 지금 열)에 더하고,
임시 파일 → fsync → 원자적 rename 으로 쓴다. **라이선스가 막는 것은 예외다** — RAR 만들기는
UnRAR 라이선스가 압축기 재구현을 금지한다.

**다음 방향**(사용자): 뷰어 기능에 더할 것이 없으면 **편집 기능을 하나씩** 더한다. 기능 확장에 낫다면 **인터넷 연결도 쓴다** —
아래 '`INTERNET` 을 선언하지 않는다' 도 영구 결정이 아니다(지금은 선언하지 않는다). 다시 열 때 잃는 것은 세 겹 방어 가운데 OS 의 겹(문서·
WebView·서드파티의 소켓을 한꺼번에 막던 것)이다. 그때는 WebView 의 가로채기(우리 호스트가 아니면 빈 404)가 문서를 지키는 마지막 겹이 되므로,
네트워크는 그것을 쓰는 기능의 코드 안에서만 열고 문서를 그리는 길에는 닿지 않게 둔다. 그리고 **문서를 그리는 WebView 에서 스크립트를 켜지
않는다** — 웹소켓·WebRTC 는 가로채기를 지나지 않고, DNS 미리 찾기·WebRTC 는 CSP 로도 막지 못한다(`LockedWebView` 머리말). Web(iroiro-browser)과
합칠 길을 열어 두려는 사용자 결정이 이 규칙 위에 있다. HTML 은 텍스트 뷰어가 원문(문법 강조)으로 보이고, 그린 모양은 '브라우저로 열기' 로
다른 브라우저에 넘긴다.

**폴더 화면의 + 단추**(사용자): 누르면 **무엇을 만들지 고르는** 작은 메뉴가 뜬다 — 새 폴더 / 새 글 파일 / 오피스 문서 넷
(Word·Excel·PowerPoint·한글). 오피스 문서는 **앱에 든 빈 서식을 옮겨 적고 곧바로 편집기가 연다**(`core:io` 의 `BlankDocuments`·`NewDocuments` —
편집기가 그 형식을 다룰 때만 줄이 있다. 아래 '14단계 뒤 — 글 편집·새 글 파일·그리기와 메모', '…뷰어 교체').

**첫 편집 기능 둘**(사용자): 사진의 직사각형 영역을 새 파일로, 소리·영상의 구간을 새 파일로, 그리고 **소리만 저장**(영상의
소리 트랙만 새 파일로) — 아래 '14단계 뒤 — 사진 잘라 저장·구간 저장' 절. 셋 다 원본 곁에 새 파일을 만든다.

**인앱 웹 브라우저**는 이 앱에 넣지 않는다 — 따로 된 앱 `Repositories/iroiro-browser`(폰에 보이는 이름 'Web')다.

---

## 툴체인 — 실측으로 확정한 값

**추측이 아니다. 아래는 이 기계에서 실제로 빌드가 된 조합이다.**

| | 값 | 왜 이 값인가 |
|---|---|---|
| AGP | 9.4.1 | 설치된 Android Studio 2026.1.4 의 업그레이드가 고른 값 |
| Gradle | 9.6.0 | 같은 업그레이드가 고른 값. 배포판이 이미 캐시에 있다 |
| 데몬 JDK | 21 (JBR) | `gradle/gradle-daemon-jvm.properties`. 시스템 java 는 25 지만 데몬은 21 로 통일한다 |
| Kotlin | 2.3.20 | KSP 2.3.12 의 POM 이 요구하는 짝. IDE 번들은 2.3.10(같은 2.3 라인) |
| compileSdk | 37 | Compose BOM 2026.09.00 이 `minCompileSdk=37` 을 요구한다 |
| targetSdk | 36 | **반드시 명시한다.** 안 적으면 AGP 9 가 조용히 compileSdk(37)로 채운다 |
| minSdk | 31 | |

### 함정 — 다시 밟지 마라

- **외부 `org.jetbrains.kotlin.android` 를 적용하면 설정이 죽는다.** AGP 9.4 의 새 DSL 과
  충돌해 `ApplicationExtensionImpl cannot be cast to BaseExtension` 이 난다.
  **`android.builtInKotlin=true`(기본값)로 내장 Kotlin 을 쓴다.**
  `org.jetbrains.kotlin.plugin.compose` 는 내장 Kotlin 과 함께 잘 돈다(실측).
- **순수 JVM 모듈에 `jvmToolchain(17)` 을 쓰지 마라.** 이 기계에 JDK 17 이 없어 내려받기
  해석기(foojay)를 물어온다. 데몬 JDK 21 로 컴파일하고 `jvmTarget`·`targetCompatibility`
  만 17 로 맞춘다 — D8 이 받아들이는 상한이 17 이다.
- **`local.properties` 의 경로는 슬래시로 적고, 드라이브 문자 뒤의 콜론은 이스케이프한다**
  (`sdk.dir=C\:/Users/...`). 역슬래시 구분자는 `.properties` 의 이스케이프 문자와 충돌해
  `IOException: Invalid file path` 로 죽고, 콜론을 이스케이프하지 않으면 lint 가
  `PropertyEscape` **오류**를 내서 커밋되지도 않는 파일 하나 때문에 lint 가 통째로 실패한다.
- `android:Theme.DeviceDefault.DayNight.NoActionBar` 는 **없는 리소스다.** `DayNight` 를
  상속하고 `windowActionBar`·`windowNoTitle` 을 직접 끈다.
- `gradle wrapper` 태스크는 **settings 파일이 있어야** 돈다.
- `SevenZFile.Builder.setMaxMemoryLimitKb(int)` 는 **이름과 달리 바이트를 받는다**(1024로
  나눈다). 65536을 넘기면 64MiB 가 아니라 64KiB 가 되어 정상 7z 도 `MemoryLimitException`
  으로 죽는다. 1.28.0 이 그래서 `setMaxMemoryLimitKiB` 를 새로 냈다 — 그쪽을 쓴다.
- **`javap` 출력을 정규식으로 거를 때 타입 이름의 숫자를 빠뜨리지 마라.**
  `[a-zA-Z.<>]+` 로 반환형을 잡으면 `Rar5Redirection` 같은 타입이 통째로 사라져
  "이 API 는 없다" 는 틀린 결론이 나온다. 실제로 한 번 밟았다.
- **`SavedStateHandle` 에 `subList` 를 넣으면 앱이 죽는다.**
  `Can't put value with type class java.util.ArrayList$SubList into saved state`.
  저장 상태에 들어가는 목록은 언제나 `ArrayList(...)` 로 새로 만들어 넣는다.
- **즉시 끝나는 작업에 포그라운드 서비스를 띄우면 앱이 죽는다.**
  `startForegroundService` 를 부른 쪽은 5초 안에 `startForeground` 까지 가야 하는데,
  그 전에 서비스를 멈추면 `ForegroundServiceDidNotStartInTimeException` 이 난다.
  휴지통 이동·이름 바꾸기는 `rename` 한 번이라 수십 ms 다 — **400ms 지나고도 도는
  작업에만** 서비스를 붙인다(`FileOpManager`).
- `LazyColumn` 을 `verticalScroll` 안에 넣으면 측정 단계에서 죽는다. 스크롤 컨테이너를 겹치지 마라.
- **ExoPlayer 는 자기를 만든 스레드에서만 만질 수 있다.** 다른 스레드에서 `currentPosition`
  하나만 읽어도 `IllegalStateException: Player is accessed on the wrong thread` 로 앱이 죽는다.
  위치 저장기의 스코프를 `Dispatchers.IO` 로 두었다가 FATAL 두 번을 봤다. **재생 스코프는
  `Dispatchers.Main.immediate`, 그 안에서 DB 쓰기만 IO 로 넘긴다.**
- **Material 아이콘 코어 세트에 `StarBorder` 가 없다.** 있는 것은 채워진 `Star` 뿐이고,
  `Icons.Outlined.Star` 는 이름과 달리 **같은 채워진 모양**이다(Material 에서 빈 별의
  이름은 `star_outline` 이라 따로 있다). 확장 세트를 들이지 않기로 했으므로 직접 그린다
  (`StarOutline.kt`). 코어에 있는 것은 **49개뿐이다**(`material-icons-core` 의 aar 를
  풀어 `Icons.Filled` 를 셌다) — 쓰기 전에 jar 를 열어 확인하라.
- **Room 마이그레이션의 `execSQL` 은 `androidx.sqlite.execSQL` 확장 함수다.** Room 2.8 의
  `Migration.migrate` 가 받는 것은 `SQLiteConnection` 이고 거기에는 멤버 `execSQL` 이 없다.
  import 를 빠뜨리면 "Unresolved reference" 만 뜬다.
- **`runBlocking` 으로 쓴 계측 테스트는 마지막 식이 `Unit` 이어야 한다.** 아니면
  `Method ... should be void` 로 **클래스 전체가** 초기화에 실패해 한 개도 돌지 않는다.
- **`adb shell "input tap X Y; input tap X Y"` 는 더블탭이 되지 않는다.** 두 탭 사이가
  **23ms**(실측)인데 Compose 의 `ViewConfiguration.doubleTapMinTimeMillis` 가 40ms 라
  그보다 빠른 쌍을 더블탭으로 세지 않는다. 결과는 **단일 탭 두 번**이다.
  `input tap X Y; sleep 0.1; input tap X Y` 로 띄워야 한다.
- **제스처를 화면 캡처로 판정하려 들지 마라.** 6단계에서 이것으로 세 번 틀린 결론을 냈다
  — 단일 탭이 상단 막대를 숨겨 판독에 쓰던 파일 이름이 사라졌고, 확대된 줄 알았던 캡처는
  사실 다른 사진이었다. **배율 같은 내부 값은 `Iro.d` 로 찍어 logcat 으로 읽어라.**
  그 로그는 릴리스에서 호출 지점째 사라진다.
- **`InputStream.readNBytes` 는 안드로이드에 API 33부터 있다.** 자바 9 의 함수라 컴파일도
  되고 JVM 시험도 통과하는데, minSdk 31 기기에서 `NoSuchMethodError` 로 **앱이 즉사한다.**
  7단계에서 실제로 죽였다. `java.io`·`java.nio` 의 최신 함수를 쓰기 전에 API 수준을
  확인하고, 매 단계 `:app:lintRelease` 를 돌린다(`NewApi` 가 이것만 잡는다).
- **순수 JVM 모듈은 lint 의 `NewApi` 가 보지 않는다.** 자바 10 의 `ByteArrayOutputStream.toString(Charset)`(API 33)이 빌드·JVM 시험·모듈 lint 를
  모두 지나 기기에서 앱을 죽인 적이 있다. 순수 JVM 모듈을 고치면 `tools/check_api_level.py` 로 그 모듈의 클래스를 SDK 의 `api-versions.xml` 과
  견준다(문제가 있으면 끝 코드 1). 바이트를 글로 바꿀 때는 `String(bytes, Charsets.UTF_8)` 이다.
- **`adb push` 에 비ASCII 이름을 주면 멈춘다.** `/sdcard/텍스트시험/` 로 밀면 오류도
  없이 300초를 넘겼고 기기에는 아무것도 생기지 않았다. 시험 파일은 **ASCII 이름으로**
  밀고, 한글 이름이 필요한 시험은 기기에서 `mv` 로 바꾼다.
- **상계를 개수 상한과 견주지 마라.** ZIP 리더가 `파일크기 / 46`(중앙 디렉터리 레코드의
  최소 길이)을 엔트리 수의 **상계**로 구해 상한(10,000)과 견줬다. 상계는 "이만큼까지 있을
  수 있다" 는 뜻이라, 파일이 460,000바이트만 넘으면 **엔트리가 3개인 zip 도 거절**됐다
  (실측: 419,911바이트는 열리고 461,869바이트는 `엔트리 10040개` 로 거절). **2단계부터
  8단계 시작까지 실사용 크기의 zip 을 거의 다 못 열고 있었고 표본이 전부 수백 바이트라
  자가시험도 통과했다.** 지금은 EOCD 에 **적힌 수**를 읽는다(`ZipEntryCount`).
- **`Thread.interrupted()` 는 플래그를 지운다.** 정적 함수이면서 상태를 소비하므로,
  한 번 검사하고 나면 그 뒤의 모든 검사가 아무것도 못 본다. 취소는 한 번만 오기 때문에
  뒤늦게 도는 루프가 멈추지 않는다. **`Thread.currentThread().isInterrupted` 를 쓴다.**
- **junrar 의 `Archive.getInputStream` 은 `PipedInputStream` 과 새 스레드를 만든다**
  (바이트코드로 확인했다). 우리 스레드를 인터럽트해도 해제가 그 스레드에서 계속 돈다.
  대량 소비는 **`extractFile(header, out)`** 을 쓴다 — 부르는 스레드에서 돌아 우리가 쥔
  `OutputStream` 의 `write` 에서 취소를 볼 수 있고, `lastProcessedFileIndex` 덕에
  **인덱스 오름차순이면 solid RAR 도 전체 한 번**에 끝난다(되감으면 사전을 초기화한다).
  (경로 탈출 CVE 가 있던 것은 `Junrar.extract`·`LocalFolderExtractor` 이고, 그것들은
  **스스로 경로를 만들어** 쓴다. `extractFile` 은 우리가 준 스트림에 쓸 뿐 경로를 모른다.)
- **commons-compress 는 암호 걸린 엔트리를 *쓰지* 못한다.** `ZipArchiveOutputStream.write`
  가 `UnsupportedZipFeatureException` 으로 거부하므로, 암호 표본은 정상으로 쓴 뒤
  general purpose bit 0 을 **로컬 헤더와 중앙 디렉터리 양쪽에서** 켜야 한다.
- **commons-compress 의 EOCD 탐색은 주석 안의 가짜 서명에 속는다.** 주석에
  `50 4B 05 06` 바이트가 들어 있으면 `Too many disks for zip archive` 로 열지 못한다
  (우리 `ZipEntryCount` 는 주석 길이 칸을 함께 검증해 속지 않는다). 고칠 자리가 라이브러리
  안쪽이라 **개수는 맞히지만 열리지 않는** 아카이브가 생긴다. 시험으로 남겨 두었다.
- **`FilterOutputStream` 을 상속했으면 `write(ByteArray, Int, Int)` 를 반드시 재정의하라.**
  기본 구현이 그것을 바이트 하나씩 `write(Int)` 로 풀어 쓴다.
- **액티비티에 묶인 ViewModel 은 문서를 바꿔도 상태가 남는다.** 만화 뷰어에서 책을 바꿀 때
  이어보기 기록이 없으면 `_page`·`_direction` 이 되돌려지지 않아 **앞 책의 읽던 쪽과 읽는
  방향이 새 책에 옮겨 갔다** — 세로 스크롤로 보던 웹툰을 닫고 연 만화가 세로 모드로 열려
  좌우로 밀리지 않았다. 여는 함수의 첫머리에서 **기본값으로 되돌리고** 나서 저장된 값을 얹어라.
- **컴포지션은 화면이 정지하면 다시 그려지지 않는다. 액티비티의 설정을 컴포저블이 쥐게
  하지 마라.** 재생 화면이 `requestedOrientation` 을 `DisposableEffect` 로 걸어 두었더니,
  화면이 정지한 동안 큐가 영상 → 소리로 넘어가도 효과가 다시 돌지 않아 **보이지도 않는
  화면이 `SCREEN_ORIENTATION_SENSOR` 를 그대로 쥐고 있었다.** 알림에서 돌아오는 순간 창이
  가로로 잡혔다가 세로로 되돌아오고, 그 사이 새로 넓어진 창의 아래쪽이 아무도 칠하지 않은
  채 남는다 — 사용자가 본 '하단이 검게 보인다' 다. `collectAsStateWithLifecycle` 의
  `minActiveState` 를 낮춰도 **소용없다**(재구성 자체가 멈춘다). 액티비티의 `onStop` 에서
  놓고 `repeatOnLifecycle(STARTED)` 로 다시 잡아라.
- **`SurfaceView` 는 창에 구멍을 뚫는다. 가려진 동안에도 붙여 두지 마라.**
  `setZOrderOnTop` 을 부르지 않은 `SurfaceView` 의 자리는 컴포지터가 따로 합성하므로,
  그 위를 Compose 로 덮어 놓아도 **창을 다시 칠할 일이 생기면 아무도 칠하지 않은 자리가
  검게 남는다.** 재생목록이 영상을 덮는 동안에는 표면을 컴포지션에서 아예 뺀다 —
  안 보이는 것이라 잃는 것이 없고, 다시 붙는 데 드는 시간도 눈에 띄지 않는다(실측).
- **화면의 층이 겹치는 자리를 상수 dp 로 맞히지 마라.** 조작부 판의 높이는 시스템 바
  인셋과 글꼴 배율에 따라 달라진다(실측 220dp 중 72dp 가 인셋이다). 상수 200dp 로
  비켜세운 이어보기 알약이 **아래 20dp 가 판에 덮여 잘린 것처럼 보였다** — 게다가
  다이나믹 배색에서 `surface` 와 `background` 가 같은 색이라 덮은 판이 보이지도 않았다.
  **높이를 아는 방법은 `onSizeChanged` 로 재는 것 하나뿐이다.** 잰 값이 없는 동안에는
  추측한 자리에 그리지 말고 한 프레임 늦게 그려라.
- **Compose 의 형제 히트 테스트는 포인터 노드가 없는 형제를 지나쳐 아래로 내려간다.**
  겹쳐 놓은 층 가운데 위층이 `background` 만 갖고 있으면(그리기 수정자는 포인터를 잡지
  않는다) 그 빈 자리를 누른 탭이 **아래층까지 내려가 거기 있는 `clickable` 을 누른다.**
  재생 조작부의 빈 자리를 누르면 그 뒤에 깔린 재생목록 줄이 눌려 곡이 바뀌었다.
  **덮는 층에는 탭을 삼킬 수정자를 함께 달아라** — `clickable(indication = null) {}` 이면
  충분하고, 자식(슬라이더·단추)은 먼저 받으므로 다치지 않는다(기기에서 확인했다).
  `pointerInput` 으로 모든 변화를 소비하는 길은 쓰지 마라 — 슬라이더가 터치 슬롭을
  넘기기 전의 눌림을 빼앗아 드래그가 죽는다.
- **`MutableStateFlow` 는 같은 값을 합친다. '요청' 을 값으로 표현하지 마라.**
  `showPlaylist.value = true` 를 두 번 넣으면 두 번째는 **방출되지 않는다**(equality 기반
  conflation). 알림이 싣고 오는 값은 언제나 `true` 라, 목록을 펼쳐 들어왔다가 사용자가
  닫은 뒤 알림을 다시 눌러도 아무 일이 없었다. `remember` 의 키로 쓰면 더 나쁘다 —
  키가 그대로라 초기화도 안 된다. **일련번호를 붙여 '도착했다' 를 표현하라.**
- **`showSnackbar` 에 동작 단추를 붙이면 기간이 무기한이 된다.** Material 3 의 기본값이
  `if (actionLabel == null) Short else Indefinite` 라(바이트코드로 확인), `duration` 을
  생략한 채 `actionLabel` 만 주면 **누르거나 밀기 전에는 사라지지 않는다.** 재생의
  이어보기 안내가 파일 목록에 영원히 남아 있던 것이 이것이고(사용자가 지적했다), 만화
  뷰어에도 같은 모양이 하나 더 있었다. **동작 단추를 붙일 때는 기간을 손으로 적어라.**
- **런처 인텐트는 태스크의 base 인텐트와 같을 때만 '앞으로 가져오기' 가 된다.**
  `ACTION_MAIN` + `CATEGORY_LAUNCHER` 로 시작한 태스크가 아니면(재생 화면을 여는
  `FLAG_ACTIVITY_NEW_TASK` 인텐트, `am start -n`, 일부 바로가기) `filterEquals` 가 어긋나
  **하던 화면 위에 루트 액티비티가 하나 더 쌓인다** — 사용자에게는 '아이콘을 누르면 늘
  첫 화면' 으로 보인다. 에뮬레이터에서 재현했다: `[MainActivity, PlayerActivity]` →
  런처 인텐트 → `[MainActivity, PlayerActivity, MainActivity]`.
  **`launchMode` 로 고치지 마라** — `singleTask` 는 태스크를 뿌리까지 되감으면서 그 위의
  재생 화면을 지우고, `singleTop` 은 맨 위일 때만 듣는데 문제는 맨 위가 아닐 때다.
  `onCreate` 에서 `!isTaskRoot && 런처 인텐트` 면 `finish()` 하는 검사 하나로 끝난다.
- **Material 3 의 `Slider` 는 트랙을 `constraints - 손잡이 너비` 로 잰다.** 그래서
  손잡이의 **측정 크기**를 애니메이션하면 막대의 좌우 여백이 잡을 때마다 함께 흔들린다.
  커지는 것은 그림이어야 한다 — 재는 상자는 고정하고 그 안의 원만 키워라.
- **XML 주석에 하이픈을 둘 잇달아 쓸 수 없다.** 마크다운 표의 구분선(`|---|---|`)을 그대로
  옮겼다가 `mergeDebugResources` 가 파싱에서 죽었다. 표가 필요하면 `*` 목록으로 바꿔라.
- **Compose 의 `Popup` 은 자기를 감싼 부모 레이아웃 노드를 앵커로 삼는다.**
  `DropdownMenu` 를 `TopAppBar` 의 `actions` 에 ⋮ 단추와 **형제로** 두면 앵커가
  아이콘 줄 전체가 되어, 메뉴가 맨 왼쪽 아이콘 아래에서 시작한다(사용자가 '꽤 동떨어진
  자리' 로 지적했다). `actions` 가 자식을 `Constraints.copy(minWidth = 0)` 으로 재고
  `DropdownMenuPositionProvider` 가 `startToAnchorStart` 를 먼저 시도하기 때문이다.
  **단추와 메뉴를 `Box` 하나에 함께 담는다.** `offset` 으로 보정하지 마라 — 화면 폭과
  글꼴 배율이 바뀌면 다시 어긋난다.
- **`rememberSaveable` 은 컴포지션에서 빠지는 것을 견디지 못한다.** 구성 변경과 프로세스
  재생성은 견디지만, 그 컴포저블이 화면 전환으로 **통째로 빠지면** 값이 사라진다.
  갤러리에서 사진을 열었다가 뒤로 오면 첫 화면으로 떨어진 것이 이것이었다. 화면 전환을
  건너 살아야 하는 값은 ViewModel + `SavedStateHandle` 에 둔다.
- **부정 캐시에 '지금 없다' 를 넣지 마라.** 파일이 사라진 동안의 실패를 영구 실패로
  기억하면, 휴지통에서 되돌아와도 **다시는** 썸네일이 만들어지지 않는다. 영구 실패는
  '파일이 있는데 못 읽는다'(SVG·TIFF) 뿐이다.
- **화면 상태와 ViewModel 을 양방향으로 잇지 마라.** `HorizontalPager`·`LazyColumn` 은
  컴포지션 첫 순간에 **자기 초기값**을 흘리고, 그것을 그대로 받아 적으면 되살린 값이
  지워진다. 되살린 뒤에 화면을 **만들고**(상태를 `Ready` 로 늦춰서), 화면은 초기 위치를
  그 값으로 잡아라. 9단계에서 이어보기가 이것으로 한 번 지워졌다.
- **`Drawable.setCallback` 은 콜백을 약한 참조로 든다.** 익명 객체를 그 자리에서 만들어
  넘기면 GC 뒤에 `invalidateDrawable` 이 오지 않아 **애니메이션이 첫 장면에서 조용히 멈춘다.**
  오류도 로그도 없다. 콜백은 페인터의 **필드**여야 한다(`AnimatedImagePainter`).
- **`BitmapRegionDecoder` 는 반드시 `recycle()` 한다.** 네이티브 쪽에 디코더 상태가 통째로
  살아 있어서, 띠마다 하나씩 새면 웹툰 한 회를 내리는 동안 수십 개가 쌓인다.
  `newInstance(byte[], int, int)` 는 **API 31부터**이고 불리언을 받는 옛 오버로드는 31에서
  deprecated 다.
- **`LruCache` 에서 밀려난 비트맵을 즉시 `recycle()` 하지 마라.** 아직 그리고 있는 프레임이
  파괴된 비트맵을 만나 앱이 죽는다. 참조만 놓으면 GC 가 마지막 그리기 뒤에 걷어 간다.
- **코루틴 경계 너머로 `Closeable` 을 돌려주지 마라.** `withContext` 안에서 만든 자원을
  반환값으로 넘기면, 돌아오는 사이에 취소된 코루틴은 **값을 버리고 예외를 던진다** —
  호출자는 받은 적이 없으니 닫을 수 없고 자원은 GC 를 기다린다. 시험으로 잡히지 않는다
  (창이 좁아 재현이 안 된다). **값을 돌려주는 것과 소유권을 넘기는 것을 갈라라**:
  만든 자리에서 콜백으로 건네고, 호출자는 `finally` 에서 닫되 성공 경로에서 주인을 바꾼다.
- **종류(`FileKind`)를 옮기면 그것을 검사하던 `when` 을 전부 찾아라.** 9단계가 cbz·cbr·
  cb7·cbt 를 `ARCHIVE` 에서 `COMIC` 으로 옮기자 **다른 모듈의 안내문 분기가 조용히
  틀렸다**(압축 안의 cbz 가 '중첩' 이 아니라 '열 수 없음' 이 됐다). 컴파일러는 아무 말도
  하지 않는다 — `when` 이 `else` 로 끝나기 때문이다. 종류를 옮길 때는 그 상수를 쓰는
  **모든 모듈**을 grep 한다.
- **문구는 그것을 만들어 내는 타입 곁에 둬라.** 5단계가 실패 문구를 `core:playback` 에
  넣고 화면을 `feature:browser` 에 만들면서 같은 문구를 `browser_*` 이름으로 한 벌 더
  두었다. 결과는 **양쪽 다 조용한 실패**다 — 원본 여덟 개는 한 번도 안 쓰였고(라이브러리
  모듈이라 lint 의 `UnusedResources` 도 안 잡는다), 재생 화면은 디코더가 죽어도 아무 말을
  못 했다. 문구가 둘로 갈리면 화면이 늘어날 때마다 한쪽만 고쳐진다.
- **PiP 의 종횡비 예외는 '들어갈 때' 만이 아니라 '파라미터를 갱신할 때' 도 난다.**
  시스템이 받는 비율은 `[0.41841, 2.39]` 이고(플랫폼 리소스 `config_pictureInPictureMin/
  MaxAspectRatio`, 에뮬레이터의 framework-res 에서 직접 읽었다) 벗어나면
  `IllegalArgumentException: Aspect ratio is too extreme` 이다. 그 검사를 하는
  `ensureValidPictureInPictureActivityParams` 가 `enterPictureInPictureMode` 와
  `setPictureInPictureParams` **양쪽 경로**에서 불리므로, 공식 Compose 예제처럼 레이아웃마다
  파라미터를 갱신하는 구조를 그대로 베끼면 **시네마스코프(2.76:1) 영상을 열어 두기만 해도
  재생 화면이 죽는다.** 자르는 자리는 '진입 직전' 이 아니라 **비율을 만드는 한 곳**이어야
  한다(`PipMath.aspectOf`, JVM 시험이 지킨다). 비율을 모르면(`videoAspect==0`) **넘기지
  마라** — `Rational(w,0)` 은 무한·NaN 이고 NaN 은 한계 밖으로 판정된다. 안 넘기면 시스템이
  16:9 기본값을 쓴다.
- **`enterPictureInPictureMode` 는 `onStop` 뒤에 부르면 `IllegalStateException` 이다.**
  API 31 의 `mCanEnterPictureInPicture` 는 `performCreate`·`performNewIntent`·
  `performRestart` 에서 서고 `performStop` 에서 꺼진다(기기의 framework.jar 를 dexdump 로
  확인). 단추의 `onClick` 은 리줌 상태지만 그 사이에 코루틴·디바운스를 끼우면 창이 열린다.
  **부르는 자리는 리줌 상태의 콜백 안이어야 하고** `lifecycle.currentState.isAtLeast(RESUMED)`
  를 앞세운다. try/catch 로 덮는 것은 원인을 감출 뿐이다.
- **PiP 창의 X 는 액티비티를 끝내지 않는다. 그리고 `onStop` 이 모드 콜백보다 먼저 온다.**
  기기에서 두 번 틀렸다 — `isFinishing` 검사는 **한 번도 참이 되지 않았고**(X 는 고정
  태스크만 걷어 내고 액티비티는 전체화면 크기로 뒤에 남는다), 모드 콜백에서 표시를 세우는
  방식은 **이미 늦었다**(차례가 `onStop` → `onPictureInPictureModeChanged(false)` 다).
  '창을 닫았다' 를 알려면 **`onStop` 에서 '그때 PiP 였는가' 를 적어 두고 그 뒤에 모드가
  꺼지는지** 본다. 화면이 꺼져 멈춘 길에서는 모드 콜백이 오지 않아 갈린다 — 그 구분을
  못 하면 **화면을 끌 때마다 재생이 멎어** 5단계부터 지켜 온 약속이 깨진다.
- **`RemoteAction` 은 `android.graphics.drawable.Icon` 만 받는다.** Compose 의
  `ImageVector` 에서 오는 변환 경로가 없으므로 PiP 조작 아이콘은 `res/drawable/` 에
  벡터 드로어블로 **한 벌 더** 그려야 한다(`PlaybackIcons` 와 좌표를 글자 그대로 맞추고
  양쪽 주석에 짝을 적어 둔다 — 5단계의 '문구 두 벌' 과 같은 빚이다). 문구도 마찬가지로
  컴포지션 밖에서 필요하므로 `Context.getString` 을 쓰는 비-컴포저블 접근자를
  **같은 파일에** 더한다(`PlaybackStrings`).
- **`PendingIntent` 의 동일성은 extras 를 보지 않는다.** `Intent.filterEquals`
  (action·data·type·component·categories)만 본다. PiP 의 재생/일시정지/다음을 같은 액션에
  extras 만 달리해 만들면 `FLAG_UPDATE_CURRENT` 가 앞의 것을 덮어써 **세 단추가 모두 같은
  일을 한다.** 액션 문자열과 요청 코드를 서로 다르게 줘라.
- **`MediaController` 에는 `createMessage` 가 없다**(그것은 `ExoPlayer` 인터페이스 전용이라
  서비스 안에서만 닿는다). 그래서 A-B 구간 되감기는 **우리 티커**밖에 없다. 기본 500ms 로
  두면 구간 끝이 반 박자 넘쳐 들리고, 전부 50ms 로 올리면 `push()` 가 `State` 를 통째로
  복사하며 재구성을 열 배로 늘린다 — **B 근처에서만** 조인다(`AbRepeat.tickDelayMs`).
  남은 시간은 **벽시계 기준**이라 배속으로 나눠야 한다.
- **`replaceMediaItem` 으로는 자막을 바꿀 수 없다.** media3 는 새 항목을 받으면
  `MediaSource.canUpdateMediaItem` 에 묻고, 로컬 파일의 `ProgressiveMediaSource` 는
  `uri`·`imageDurationMs`·`customCacheKey` **셋만** 비교해 참을 준다(바이트코드 확인).
  소스를 다시 만들지 않으므로 새 자막 구성이 **조용히 무시된다**. `prepare()` 도 같은 소스를
  다시 준비할 뿐이다. 바꾸려면 **`setMediaItems(items, index, position)` 로 큐를 다시 세운다**
  — 위치와 자리를 우리가 들고 다시 넣는다. 돌려받는 항목에 `localConfiguration`(uri·자막
  구성)이 살아 있다는 것은 기기에서 확인했다.
- **media3 의 자막 트랙은 파서를 지나면 MIME 이 `application/x-media3-cues` 가 된다.**
  그대로 사람에게 보여 주면 화면에 `MEDIA3-CUES` 가 뜬다(기기에서 실제로 봤다). **그 MIME 을 적지 마라.** 원래
  형식(SRT·ASS)은 사라진 것이 아니라 `codecs` 로 옮겨 가 있다 — 바로 아래 줄.
- **media3 의 자막 트랙은 원래 MIME 을 `codecs` 에 숨겨 둔다. `sampleMimeType` 으로
  거르는 조건은 절대 참이 되지 않는다.** `SubtitleTranscodingTrackOutput.format()` 이
  `sampleMimeType` 을 `application/x-media3-cues` 로 갈아 끼우면서 원래 값을
  `setCodecs(...)` 로 옮긴다(바이트코드로 확인). **그림 자막(PGS·VobSub·DVB)도 그 길을
  지난다** — `DefaultSubtitleParserFactory.supportsFormat` 에 셋이 다 들어 있다. 그래서
  '그림 자막은 목록에서 뺀다' 를 `sampleMimeType` 으로 짜면 **한 번도 걸러지지 않고**,
  고른 사람은 표시만 옮겨 가고 글자는 안 뜨는 화면을 본다. 단위 시험이
  `isPictureSubtitle("application/pgs")` 만 확인하면 그 시험은 **실제 경로를 한 번도
  타지 않은 채 통과한다** — 변환된 뒤의 값으로도 시험하라.
- **서로 다른 흐름에서 받은 값을 짝지어 판단하지 마라.** `queue`(목록)와
  `State.queueIndex`(자리)를 각각 다른 `StateFlow` 에서 받아 `queue[queueIndex]` 로 읽으면,
  둘이 한 프레임 어긋난 순간에 **엉뚱한 줄**을 읽는다. 그 값으로 PiP 창을 닫기로 해 두면
  어긋난 짝 하나가 재생 화면을 `finish()` 한다. **함께 쓸 값은 한 흐름에 실어 보내라** —
  큐 항목의 종류를 `State.currentKind` 로 옮긴 것이 그것이고, `QueueItem.kind` 를 커넥션이
  정해 보내기로 한 판단과 같은 이유다.
- **`SCREEN_ORIENTATION_LOCKED` 는 '거는 그 순간의 방향' 에 묶인다.** 이 앱은 `onStop` 에서
  방향 요청을 놓았다가 다시 설 때 새로 걸므로(그것이 '하단이 검게 보인다' 를 고친 장치다),
  그 상수를 그대로 들고 있으면 **그 사이에 기기가 놓인 방향**으로 잠긴다 — 가로로 눕혀
  잠그고 화면을 껐다 켜면 세로로 잠긴다. 누른 순간의 방향을 구체값(`LANDSCAPE`·`PORTRAIT`)
  으로 적어 두어라.
- **`Locale.getDefault()` 는 기기의 언어이지 화면의 언어가 아니다.** 이 앱의 문자열 자원은
  `values/` 하나뿐이라 화면은 언제나 한국어인데, 기기가 영어로 맞춰져 있으면 언어 이름만
  `Korean`·`English` 로 나온다(에뮬레이터에서 그렇게 나왔다). **읽는 사람이 보는 언어**를
  따라야 한다.
- **`kotlin.math.round` 는 짝수 쪽으로 반올림한다**(`Math.rint`). 9:16 을 1000배 한 562.5 가
  563 이 아니라 **562** 가 된다. 눈에 띄는 차이는 아니지만 시험이 읽는 사람의 상식과 어긋나고,
  그 어긋남은 다음 사람이 시험을 고치게 만든다. 반올림 방향이 뜻을 가지면 `floor(x + 0.5)` 로 적어라.
- **안드로이드 라이브러리 모듈의 JVM 시험은 `kotlin("test-junit")` 이 필요하다.**
  `kotlin("test")` 만으로는 러너가 붙지 않아 `Unresolved reference 'Test'` 가 난다
  (순수 JVM 모듈은 `kotlin("test")` 로 충분하다).
- **`PdfRenderer.Page.render` 에 `matrix = null` 을 주면 쪽을 비트맵에 늘려 맞춘다.**
  레터박스가 아니다 — 595×842pt 쪽을 800×800 비트맵에 그렸더니 네 귀퉁이가 쪽의
  귀퉁이였다(실측). 한 문서 안에서도 쪽 크기가 다르므로(`595×842` 와 `792×612` 가 한
  파일에 있다) 첫 쪽 크기로 비트맵을 만들어 돌려 쓰면 가로 쪽에서 곧바로 찌그러진다.
  **`destClip` 만 주는 것도 타일이 아니다** — 클립 안에 쪽 **전체**를 축소해 넣는다.
  잘라 보여 주는 일은 `Matrix` 의 `postTranslate` 가 한다.
- **`render` 는 비트맵을 지우지 않는다.** 새 비트맵에 그냥 그리면 쪽이 칠하지 않은
  자리가 **투명**으로 남고, 캐시의 것을 재사용하면 **앞 쪽의 그림이 비쳐 보인다.**
  종이의 흰색은 PDF 가 아니라 우리가 칠하는 것이다(`eraseColor(WHITE)`).
  `Bitmap.Config.RGB_565` 는 `Unsupported pixel format` 이라 고를 수 있는 것도 아니다.
- **recycle 된 비트맵에 `render` 하면 예외가 아니라 `SIGABRT` 로 프로세스가 즉사한다**
  (조사 중 실제로 죽었다). 만화에서 `LruCache` 축출 뒤 `recycle()` 을 하지 않기로 한
  이유가 PDF 에서는 한 단계 더 무겁다 — 로그 한 줄 남기고 앱이 사라진다.
- **닫힌 문서에 그리면 문서가 약속한 `IllegalStateException` 이 아니라 NPE 가 온다**
  (실측). 그리고 `Current page not closed` 에 기대지 마라 — 35+ 재구현에는 그 검사가
  아예 없고, 레거시에서도 가드 필드가 volatile 이 아니라 스레드가 둘이면 나지 않는다.
  **그 예외는 방어가 아니라 증상이다.** 닫힘은 부르는 쪽이 깃발로 들고 있어야 한다.
- **렌더 도중에는 취소가 닿지 않는다.** `Thread.interrupt()` 를 걸어도 끝까지 돌고
  예외도 나지 않는다(실측). `withTimeout` 도 기다리기를 그만두는 것일 뿐이고 그동안
  pdfium 의 전역 잠금(`sPdfiumLock` 은 static 이다)은 물려 있다. 확인할 수 있는 자리는
  **`render` 를 부르기 직전** 하나뿐이다.
- **pdfium 은 `startxref` 가 망가진 PDF 를 스스로 복구해 연다.** xref 를 깨뜨려 만든
  표본이 2쪽짜리로 정상 표시됐다(11단계 실측). 깨진 파일을 시험하려면 머리만 있고 몸이
  없거나 아예 PDF 가 아닌 표본이어야 한다 — **표본이 의도한 경로를 타는지 확인하지
  않으면 시험은 통과하면서 아무것도 시험하지 않는다.**
- **`WebSettings.blockNetworkImage` 를 켜면 우리가 내주는 그림도 막힌다.** 겹겹 방어로 `blockNetworkLoads` 와 함께 켰다가 기기에서
  잡았다 — **같은 책의 CSS 는 붙는데 `<img>` 만 깨진 아이콘으로** 떴다. 우리 URL 이 `https://` 라 WebView 가 그것을 네트워크 그림으로
  센다. **`blockNetworkLoads` 는 `INTERNET` 이 없으면 기본값이 참이고, 선언하면 거짓으로 뒤집힌다**(문서의 규칙) — 그래서
  `LockedWebView` 가 참을 **명시**한다(그래도 가로챈 응답 — 책의 그림·CSS — 은 그대로 뜬다, 기기에서 확인). `shouldInterceptRequest` 에서
  `null` 을 돌려주지 마라 — 그것은 "WebView 가 알아서 가져가라" 는 뜻이라 시도가 일어나고 그동안 쪽이 멎어 보인다.
- **EPUB3 의 `nav.xhtml` 에는 목차 말고도 `landmarks`·`page-list` 가 함께 들어 있다.**
  `<nav>` 를 전부 읽으면 목차에 '표지·판권·1쪽·2쪽…' 이 줄줄이 뜬다.
  **`epub:type="toc"` 인 것만** 읽어야 하고, 그 속성은 네임스페이스가 붙어 있어
  `getAttributeValue("http://www.idpf.org/2007/ops", "type")` 로 찾는다.
- **`META-INF/encryption.xml` 이 있다고 다 DRM 이 아니다.** 같은 파일이 **글꼴 난독화**
  (IDPF·Adobe)에도 쓰이고 그쪽은 본문이 멀쩡하다. 전부 거절하면 **멀쩡히 읽히는 상업
  EPUB 을 통째로 막는다.** 항목마다 `EncryptionMethod/@Algorithm` 과 `CipherReference/@URI`
  를 읽어, 난독화면 **풀고**(`FontObfuscation`), 풀 수 없는 방식이면 글꼴일 때만 알리고 연다.
  **아무것도 적혀 있지 않거나 주소가 책 안의 이름으로 풀리지 않으면 거절한다** — '아마
  글꼴이겠지' 로 넘기면 암호화된 본문을 깨진 글자로 보여 주게 된다. 뒤의 것(풀리지 않는
  주소)은 오래 구멍이었다: 주소를 조용히 버려, 글꼴 하나와 책 밖을 가리키는 본문 하나가
  적힌 책이 '글꼴만 걸렸다' 로 통과했다(`EpubEncryptionTest` 가 박는다).
- **방식(`EncryptionMethod`)은 `EncryptedData` 마다 따로다.** 읽은 값을 다음 항목으로 넘기면
  방식을 적지 않은 잠긴 본문에 앞 항목의 '난독화' 가 붙어 풀린다(틀린 바이트로).
- **`URLDecoder` 로 EPUB 의 주소를 풀지 마라.** 그것은 폼 인코딩이라 `+` 를 공백으로
  바꾼다 — `a+b.png` 가 `a b.png` 가 되어 파일을 못 찾는다. 퍼센트만 푼다.
- **`UnsupportedFeatures` 를 화면이 직접 보면 배지가 뜨지 않는다.** 그냥 가변 객체라 값이
  늘어도 재구성이 일어나지 않고, EPUB 에서 장을 읽는 일은 **WebView 의 다른 스레드**에서
  일어난다(기기에서 그 증상을 봤다 — 스크립트를 버린 장을 보고 있는데 알림 단추가 없었다).
  흐름(`StateFlow`)으로 내보내는 쪽이 유일하게 도는 길이다.
- **PDF 의 실패를 공용 `toOpenFailure()` 에 맡기면 뜻이 정확히 뒤바뀐다.** 레거시
  `PdfRenderer` 가 실패를 알리는 방법은 둘뿐인데, 암호 PDF 는 `SecurityException` 이라
  `NoPermission`('읽을 권한이 없습니다')이 되어 **사용자가 권한 설정을 뒤지고**,
  깨진 PDF·PDF 아닌 파일·0바이트는 전부 `IOException` 이라 `Io`('입출력이 실패했다')가
  되어 **디스크가 고장 난 것처럼** 읽힌다. `PdfFailures` 가 그 앞에서 가린다.
  **메시지 문자열로 갈라 보지 마라** — 플랫폼 판마다 바뀌는 값이고, 분기의 근거로
  삼는 순간 그 문자열이 화면 쪽으로 새는 길이 열린다.
- **`Cipher.getInstance(...).apply { init(..., IvParameterSpec(iv)) }` 에서 `iv` 는 우리 필드가
  아니다.** `apply` 안에서는 수신 객체의 멤버가 바깥 클래스의 필드보다 먼저 풀리므로 `iv` 가
  `Cipher.getIV()` 가 되고, 초기화 전의 그 값은 null 이다 — `IvParameterSpec` 이 NPE 를 낸다.
  컴파일러는 아무 말도 하지 않는다. AES 복호화기를 처음 돌린 JVM 시험이 전부 이것으로 죽었다.
  수신 객체 람다 안에서 쓸 필드는 **수신 객체의 멤버와 겹치지 않는 이름**(`ivBytes`)으로 둔다
  (지역 변수·매개변수는 멤버보다 먼저 풀리므로 괜찮다).
- **pikepdf(qpdf)는 저장할 때 XMP 메타데이터를 다시 쓴다**(`fix_metadata_version` 기본값 켜짐 —
  307 → 338 바이트). 그렇게 잠근 표본을 풀어 평문 원본과 바이트로 견주면 **우리 복호화기가
  옳은데도** 시험이 깨진다. 표본을 만들 때 `fix_metadata_version=False` 를 준다. 판별력 있는
  시험을 만들려고 독립 도구로 표본을 잠갔는데, 그 도구가 내용까지 바꾸면 오라클이 흔들린다 —
  **두 도구가 서로의 표본을 풀어 평문과 같은지부터 확인하라.**
- **AGP 9 에서 `android { sourceSets.getByName("androidTest").assets.srcDir(...) }` 는 설정
  단계에서 죽는다** — `DefaultAndroidLibrarySourceSet_Decorated cannot be cast to
  AndroidLibrarySourceSet`. 옛 타입으로 캐스팅하는 접근자다. 변형 API 로 붙인다:
  `androidComponents { onVariants { it.androidTest?.sources?.assets?.addStaticSourceDirectory(...) } }`.
- **API 35 의 `PdfRenderer` 재구현은 인증서(공개 키)로 잠긴 PDF 를 `IOException` 으로
  알린다.** 레거시(API 31)는 잠긴 문서를 전부 `SecurityException` 으로 알린다(태블릿·폰
  에뮬레이터에서 실측). 예외 종류만 보면 35 이상에서 그 문서가 '깨진 파일' 로 나간다 —
  `PdfOpener` 는 두 예외 모두에서 `/Encrypt` 를 직접 읽어 가른다.
- **`rememberTextFieldState()` 는 저장되는 상태다.** 암호 입력칸에 쓰면 회전할 때 암호가
  **저장 상태 번들**로 들어가고, 그 번들은 최근 앱 화면을 위해 시스템이 디스크에 쓸 수 있다.
  암호칸은 `remember { TextFieldState() }` 로 든다(회전하면 입력이 지워지는 것이 대가다).
- **Play Store 에뮬레이터 이미지에서 `sendevent` 는 SELinux 에 막힌다**(shell 이 `input` 그룹인데도
  `Permission denied`). `input` 명령은 손가락 하나뿐이라 **핀치를 넣을 길이 에뮬레이터 콘솔
  하나다**: `adb emu event send EV_ABS:ABS_MT_SLOT:0 EV_ABS:ABS_MT_TRACKING_ID:100
  EV_ABS:ABS_MT_POSITION_X:… … EV_SYN:0:0`. 좌표는 `0..32767` 로 화면에 비례하고,
  **`EV_SYN:SYN_REPORT:0` 은 `KO: invalid event code`** 다(콘솔에 그 별칭이 없다) — `EV_SYN:0:0`
  으로 적는다.
- **Git Bash 의 here-document 는 역슬래시를 먹는다.** `python - <<'PY'` 로 코틀린 코드를 고치면
  문자열 안의 `\n` 이 진짜 줄바꿈이 되고 `'\u0000'` 이 NUL 문자가 되어 파일에 박힌다(둘 다
  실제로 밟았다 — `grep` 이 그 파일을 '바이너리' 로 보기 시작했다). 역슬래시가 든 편집은
  스크립트를 파일로 써서 돌리거나 `Edit` 도구로 한다.
- **코틀린의 블록 주석은 겹친다.** KDoc 안에 `_rels/` 뒤로 별표가 붙은 경로(`xl/` + `*.xml`
  꼴)를 적으면 거기서 **주석이 하나 더 열리고** 닫히지 않는다 — 컴파일러는 파일 끝에서
  `Unclosed comment` 만 말한다. 자바와 다르다. 주석에 경로 패턴을 적을
  때는 '`_rels` 폴더의 관계 파일' 처럼 말로 쓴다.
- **칸으로 나눈 화면(`Column`) 옆의 `SnackbarHost` 는 맨 위에 그려진다.** 부모가 `Box` 가 아니면 정렬을
  모른다 — EPUB 이어보기 안내가 제목 막대를 가리고 있었다(아무도 눈치채지
  못했다). 알림을 띄우는 화면은 `Box` 로 감싸 `align(BottomCenter)` 한다.
- **WebView 의 `onPageFinished` 는 문서 전체의 배치가 끝나야 온다.** 칸 10만 개짜리 표는 3초에 보이고 45초에
  그것이 왔다 — 그동안 '여는 중' 동그라미가 다 그려진 표를 덮었다. '보이기 시작했다' 는 `onPageCommitVisible`
  (API 23)이다.
- **JVM 시험의 kxml2 2.3.0 은 요소 안에서 잘린 XML 을 조용히 `END_DOCUMENT` 로 끝낸다.** 안드로이드의 파서는
  던진다고 알려져 있으나 **기기에서 확인하지 않았다**. 그래서 '깨진 부분' 을 시험할 때 JVM 에서는 통과하고
  기기에서는 부분 실패가 되는(또는 그 반대) 갈림이 생길 수 있다.
- **스크립트에서 `gradlew.bat` 에 `--tests "*"` 를 주면 배치 파일이 별표를 파일 이름으로 펼친다**
  (`Task .gradle not found`). `io.github.donggi.*` 처럼 꾸러미로 거른다.
- **점수가 낮은 까닭을 '설계' 나 '오라클의 빈틈' 으로 적기 전에 빠진 글을 눈으로 봐라.** 오라클과 견주는 말뭉치 시험(`PdfTextCorpusTest` 등)에서
  '상한 때문'·'오라클이 못 읽는다' 로 넘긴 차이가 우리 결함(숨긴 행·열을 센 것, 이항 계수를 나누기로 적은 것)으로 뒤집힌 적이 있다.
- **위생기(`Urls.rewrite`)는 URL 속성에서 공백을 지운다.** `java\nscript:` 로 스킴 검사를
  피해 가는 수법을 막으려는 것이라 옳은 동작인데, 그 탓에 부분 이름을 그대로 `src` 에 적으면
  `my image.png` 가 `myimage.png` 가 되어 **그림이 조용히 사라진다.** `#`·`?`·`%` 가 든 이름도
  조각·질의·인코딩으로 잘못 읽힌다. 사용자 파일 이름을 주소로 쓰는 곳은 마크다운 미리보기이고, 그쪽은 이름을 쪽에 적지 않고
  번호(`img/N`)로 바꾼다(`MarkdownPreview` — 위생기의 리졸버는 번호만 통과시킨다).
- **commons-compress 의 7z 는 암호 여부를 목록에서 알려 주지 않는다.** `SevenZArchiveEntry` 에
  암호 표시가 없어 `contentMethods` 에 `AES256SHA256` 이 있는지로 가르고, 그 값은 `nextEntry` 를
  돌아야 채워진다. 암호 없이 읽으면 `PasswordRequiredException` 인데 **틀린 암호에는 따로 예외가
  없다** — LZMA 가 쓰레기를 받아 읽는 도중 `CorruptedInputException` 이 난다. 헤더까지 잠긴 것은
  암호 없이 열면 `PasswordRequiredException`, 틀린 암호로 열면
  `IOException("Broken or unsupported archive: no Header")` 다(전부 1.28.0 실측). 그래서 '틀렸다'
  는 예외 종류가 아니라 **맞는 암호로 한 항목을 끝까지 읽어 보는 것**(`Archives.verifyPassword`)
  으로 판정한다. 그리고 `PasswordRequiredException` 의 메시지에는 **파일의 절대경로**가 들어 있다.
  **`nextEntry` 는 그 폴더의 해제기 사슬을 실제로 만든다** — LZMA(LZMA2 가 아니다)·BZip2 는 만들면서
  바로 읽고(암호를 준 파일이면 폴더마다 AES 열쇠 유도), BCJ2·PPMd·64 MiB 사전은 만드는 것 자체가
  실패한다. 암호를 가리려고 모든 폴더에서 `nextEntry` 를 돌렸더니 **암호와 상관없는 Ultra 7z 의 목록이
  '깨졌다' 로 나갔다**. 가리는 일은 **암호 없이 따로 연 파일**로, 항목마다 예외를
  갈라서 한다(`SevenZArchiveReader.lockedPositions`).
- **junrar 의 `getInputStream` 은 실패를 삼킨다.** 해제가 다른 스레드에서 돌다 `RarException`(틀린
  암호·CRC)을 만나면 잡아 버리고 파이프만 닫는다 — 읽는 쪽에는 **멀쩡한 끝(EOF)** 으로 보인다(소스로
  확인). 그 스트림으로 암호를 확인하면 **어떤 암호든 맞다**. 확인은 부르는 스레드에서 도는
  `extractFile` 로 하고, 읽는 스트림은 적힌 크기보다 먼저 끝나면 실패로 돌린다. 그리고 `RarException`
  은 **`Exception` 을 바로 잇는 검사 예외**라 `IOException`·`RuntimeException` 을 잡는 호출부를 모두
  지나 코루틴 밖으로 나간다 — 헤더까지 잠긴 `.cbr` 하나가 앱을 죽인다. 리더가 우리 예외로 옮긴다.
- **ZIP 전통 암호의 확인 바이트(데이터 기술자 항목)는 로컬 헤더의 날것 DOS 시각에서 읽는다.**
  commons-compress 의 `getTime()` 은 UT(0x5455)·NTFS 추가 필드가 있으면 그 **UTC 시각**을 준다.
  그것을 기기의 시간대로 DOS 시각에 되돌리면 만든 사람의 시간대와 시가 어긋나, Info-ZIP `zip -e`
  (맥·리눅스의 표준)로 잠근 파일의 **맞는 암호가 '틀렸다'** 가 된다.
- **확인 바이트 하나로 암호 후보를 고르지 마라.** 틀린 후보도 1/256 로 통과한다. 항목마다 첫 통과
  후보를 쓰면 한글 암호(반디집은 CP949)의 300쪽 만화에서 한 쪽 이상이 틀린 열쇠로 풀려 깨질 확률이
  69% 다. 후보는 아카이브마다 한 번, 항목 하나를 CRC·인증값까지 풀어 보고 정한다
  (`ZipArchiveReader.keys`).
- **ZIP 암호의 인코딩은 도구마다 다르다.** 명세(APPNOTE)가 정하지 않는다. **반디집은 한글 암호를
  CP949 로 넣는다** — 반디집으로 잠근 표본이 UTF-8 로는 풀리지 않았다(실측). 그래서
  `ZipDecryption.candidates` 가 UTF-8 · CP949 · ISO-8859-1 을 차례로 시도한다. 7z 는 명세가
  UTF-16LE 로 정해 후보가 하나다. **AES 도 같다** — 반디집 7.45 는 AES ZIP 의 한글 암호도 CP949 로 넣는다(실측). 이 앱이 만드는 암호 ZIP 은
  UTF-8 이라(7-Zip 쪽) 한글이 든 암호면 반디집이 열지 못한다 — 암호 칸이 경고한다('14단계 뒤 — AES 암호 ZIP').
- **이미지 뷰어의 바닥층은 생각보다 크게 뜬다.** 표본이 2의 거듭제곱이고 목표가 '화면의 긴 변'
  이라, 4000×3000 사진을 2400 화면에 맞추면 표본 1 — **원본 그대로** 뜬다(48 MB). 그래서
  원본이 4000 인 사진에는 선명화 조각이 필요 없고 요청되지도 않는다. 조각을 시험하려면 원본이
  바닥층보다 커야 한다 — 8000×6000 을 썼다(바닥층 4000, 조각은 맞춤의 4.4배 넘어서부터).
- **HWPX 는 EPUB 처럼 생겼다.** `mimetype` 과 `META-INF/container.xml` 이 둘 다 있어서, 이름만 보는 EPUB
  판별기는 한글 문서를 전자책으로 연다. HWPX 판별기가 없으므로(편집기가 연다) **EPUB 판별기가 스스로 가린다** —
  저장된 `mimetype`(압축하지 않은 첫 항목, 추가 필드 없음, 이름 길이 8)이 `application/epub+zip` 으로 시작하면 맡고, `application/hwp+zip`·
  `application/vnd.oasis.` 로 시작하면(한글·ODF) 손을 떼고, 그 밖(압축한 `mimetype`·BOM 등)은 이름으로 본다(`EpubProbe`, `EpubProbeTest`).
  **`mimetype` 을 거절의 근거로 쓰지 않는다**(11단계의 규칙). 새 ZIP 기반 포맷을 더할 때는 기존 판별기가 그것을 가로채지
  않는지부터 본다.
- **JVM 시험의 kxml2 는 BMP 밖의 문자 참조(`&#xF02B6;`)를 16비트로 잘라 읽는다.** 그 모양으로 표본을 만들면
  시험이 엉뚱한 글자를 본다. 표본에는 글자를 그대로(UTF-8) 넣는다.
- **uiautomator 덤프는 탭을 `&#9;` 로 적는다.** 덤프를 정규식으로 읽는 스크립트가 그 글자를 그대로 내보내면
  화면에 `&#9;` 가 떠 있는 것처럼 보인다. 앱을 의심하기 전에 캡처를 본다.
- **XHTML 을 `text/html` 로 내주면 뜻이 바뀐다.** EPUB 의 장은 XML 인데 WebView 에는 HTML 로 나간다. 셋이 실제로 밟혔다 —
  ① 빈 요소가 아닌 것의 자기 닫힘(`<div/>`·`<span/>`·`<style/>`)을 HTML 파서는 **여는 태그**로 읽어 뒤의 장 전체가 그 안에
  들어간다(E10 370곳). ② DOCTYPE 이 없으면 **쿼크 모드**라 표가 본문의 글자 크기를 물려받지 않는다. ③ `xml:lang` 을 모른다 —
  한자가 기기의 언어로 그려진다. 위생기가 `<x></x>` 로 풀어 쓰고, `</br>` 같은 빈 요소의 닫는 태그를 버리고(HTML 에서는
  `<br>` 이다), `lang` 을 함께 적고, 껍데기가 `<!DOCTYPE html>` 을 붙인다. **글 대조로는 하나도 보이지 않는다** — 셋 다 실세계
  말뭉치의 구조 대조·브라우저 실측이 잡았다.
- **`word-break:keep-all` 은 한국어의 규칙이다.** 낱말 사이를 띄어 쓰는 글에서만 뜻이 있다. 띄어 쓰지 않는 일본어·중국어에
  걸면 공백·문장 부호에서만 줄이 나뉘어, 양쪽 정렬과 만나 '都　說　沒　了' 처럼 벌어진다. 껍데기가 `:lang(ja)·:lang(zh)` 에서
  되돌린다 — 그러려면 **문서의 언어를 알아야** 한다(EPUB 은 `dc:language` 를 기본으로 단다).
- **매니페스트의 MIME 은 책이 적은 문자열이다.** `text/css; charset=utf-8`·대문자·틀린 형식이 온다. 글자 그대로 견주면 그런
  스타일시트가 위생을 건너뛰고, 매개변수가 붙은 것은 WebView 가 CSS 로 적용한다. 견주기 전에 매개변수를 떼고 소문자로 바꾸고,
  이름과 형식 가운데 **하나라도** CSS 면 위생을 거친다.
- **깨진 ZIP 의 예외는 `IOException` 이라 공용 매핑에서 '입출력 실패' 가 된다.** commons-compress 는 끝 레코드가 없으면
  `ZipException`, 중앙 디렉터리 자리가 틀리면 맨 `IOException` 을 던진다. 사용자는 디스크가 고장 난 줄 안다 — EPUB 은
  리더를 여는 자리에서 '깨진 파일' 로 옮긴다(PDF 의 `PdfFailures` 와 같은 까닭).
- **CSS 는 그 안의 `<` 를 막지 않는다. `<style>` 에 넣는 CSS 의 `<` 는 `\3c ` 로 바꾼다**(`HtmlSanitizer.styleText`). 구멍
  둘이 크롬에서 재현됐다 — 책의 `<style>` 안의 `</head>` 를 껍데기가 진짜로 알고 거기 스타일을 끼우자 우리 `</style>` 이 책의
  것을 먼저 닫아 **뒤의 CSS 글자가 위생을 건너뛴 마크업**(`<img onerror>`·`<iframe>`)이 됐고, `<svg>`·`<math>` 안의 `<style>`
  은 HTML 파서에게 보통 요소라 안의 글이 태그로 읽혔다. 그리고 태그의 자리를 **글자 검색으로 찾지 마라** — 껍데기는 주석과
  날것의 글(`style`·`title`·`xmp`…) 안을 건너뛰며 찾는다(`HtmlShell.indexOfTag`).
- **브라우저가 가져다 쓰는 것은 태그의 주소만이 아니다.** `<use href="x.svg#g">` 는 다른 SVG 문서를 가져와 장 안에
  **복제**하고 `filter:url(x.svg#f)` 도 그 문서를 읽는다 — 날것으로 내준 SVG 의 바깥 참조가 장 안에서 산다. `<use>` 와 CSS 는
  같은 문서 안(`#id`)만 가리키게 한다. 형식 문자열도 믿지 않는다: 크롬은 표준 모드에서도 형식이 비었거나
  `application/x-unknown-content-type`·`text/css,x` 인 파일을 스타일시트로 적용한다 — 날것으로 내주는 자원의 형식은 **우리
  표(그림·글꼴)에서만** 나온다(`rawResourceType`).
- **되풀이될 수 있는 것의 탐색 범위를 묶어라.** DOCTYPE 을 건너뛰며 내부 부분집합의 끝을 입력 끝까지 찾았더니 되풀이된
  DOCTYPE 이 제곱 시간을 만들었다(0.5 MB 에 22초, 32 MiB 상한이면 하루). **시간 시험은 옛 판이 확실히 넘는
  크기로 만든다** — 0.56 MB 로 짰더니 JIT 이 옛 판의 단순한 고리를 빠르게 돌려 3초 안에 들어와, 시험이 결함을 보지 못했다
  (고침을 되돌려 확인했다). 2.2 MB 에서 옛 판은 3초를 넘고 지금 판은 42 ms 다.
- **DataStore 에 `corruptionHandler` 를 비워 두지 마라. 설정 흐름을 `stateIn` 으로 드는 곳은 읽기 실패에 죽는다.** 처리기가
  없으면 깨진 설정 파일의 `CorruptionException`(`IOException`)이 읽는 흐름으로 그대로 나오고, 목록 ViewModel 이 설정을
  `stateIn(…, Eagerly)` 로 들고 있어 그 예외가 앱을 죽인다 — 파일이 그대로 남으므로 **켤 때마다** 죽고, 크래시 기록을 볼 설정
  화면에도 닿지 못한다. `ReplaceFileCorruptionHandler { emptyPreferences() }` 를 붙이고 모든 흐름이
  `IOException` 을 기본값으로 넘긴다(`AppPreferences.data`). 잃는 것은 정렬·숨김·뷰어 기본값뿐이다.
- **DataStore 는 줄 선 쓰기 사이에 옛 값을 돌려준다.** 문서 뷰어의 '보기' 에서 110 → 120 을 빠르게 누르면 110 이 되돌아와
  글이 한 번 더 흐르고 그 사이 누른 한 번이 사라졌다. 쓰는 중에 온 값은 받지 않는다(`LocalFirst`). 슬라이더는 끄는 동안
  화면 값만 움직이고 손을 뗄 때 한 번 쓴다(설정 화면) — 같은 까닭이다.
- **`clickable` 은 자손의 의미를 합친다.** 위 '덮는 층에는 탭을 삼킬 수정자를 함께 달아라' 의 단서다 — compose-foundation 1.12.1
  의 `AbstractClickableNode.shouldMergeDescendantSemantics` 가 참이라(바이트코드로 확인), 글·스크롤을 가진 층의 **부모**에 달면
  화면 낭독기가 층 전체를 '눌러도 아무 일 없는 단추' 하나로 읽는다(만화의 쪽 목록). 내용 **아래의 형제**에
  `clearAndSetSemantics {}` 와 함께 단다 — 형제에 포인터 노드가 있으므로 히트 테스트는 여전히 층에서 멈춘다.
- **버퍼를 가진 `Channel` 은 그것을 받는 화면보다 오래 산다.** 알림을 받는 수집기는 화면과 함께 사라지지만 ViewModel 의
  채널은 남아, 앞 알림을 띄우던 10초 동안 줄 선 것이 **같은 책을 다시 열 때** 배달됐다 — 1쪽을 보는데 '끝까지 읽었습니다.
  다음 권'. 경로로 걸러도 같은 책을 닫았다 연 것은 가려지지 않는다. 문서를 열고 닫는 자리에서 줄 선 것을 버린다
  (`BookEvents.discardPending`). 아래 '코드' 의 `runCatching` 항목이 말하는 '다음 문서의 화면에 배달한다' 와 같은 자리다.
- **참조 구현을 옮기면 그 구현의 제곱까지 옮긴다.** 마크다운의 강조 처리를 commonmark.js 짜임 그대로 옮겼더니, 바닥이
  쌓기의 맨 위일 때 멈출 자리를 지나쳐 쌓기 전체를 훑는 것까지 따라왔다 — 짝 없는 구분자 뒤에 링크가 되풀이되면 1.6M 글자에
  75초(고친 뒤 0.67초). 적대 시험은 규칙마다 '짝 없는 것이 쌓인 뒤 닫는 것이 되풀이되는' 모양을 만든다.
- **파일 이름에는 공백·괄호·따옴표·콜론이 다 들어간다. 경로의 끝을 글자 하나로 정하지 마라.** 크래시 기록의 경로 지우기가
  따옴표에서 멈추자 `Guns N' Roses - Don't Cry.mp3` 의 뒷조각이 공유 사본에 남았다(`Uri.encode` 도 `'` 를 바꾸지 않는다).
  덜 지우는 것보다 더 지운다.
- **같은 경로의 `FileObserver` 둘은 서로를 끊는다.** 같은 경로의 관찰자끼리 inotify 감시 번호를 나눠 써서, 뒤에 건 것이 앞의
  것을 덮고 하나를 멈추면 둘 다 멎는다. 지금은 관찰자가 폴더 화면 하나뿐이고 `flatMapLatest` 가 앞 감시를 멈춘 뒤에 새 감시를
  건다 — 다른 화면에 감시를 더할 때 먼저 본다.
- **JVM 시험이 컴파일되지 않는 파일(`strings.xml`·`res/raw`·다른 모듈의 소스)을 읽으면, 그 파일만 바꾼 빌드에서 Gradle 이
  시험을 건너뛴다**(UP-TO-DATE). 시험의 입력으로 적는다(`app/build.gradle.kts` 의 `inputs.files`).
- **`:app:lintRelease` 는 라이브러리 모듈의 문제를 보고하지 않는다.** 모듈을 새로 만들면 `:<모듈>:lintRelease` 를 따로 돌린다.
- **commons-compress 의 `CpioArchiveOutputStream` 은 안드로이드에서 이름을 쓰다 죽는다** — `NioZipEncoding.encode` 가 쓰던 `CharsetEncoder`
  에 `canEncode` 를 불러 `IllegalStateException: Current state = CODING` 이다(두 에뮬레이터 실측 — JVM 의 인코더는 받아 준다). 기본 인코딩이
  US-ASCII 라 한글 이름도 그대로 실리지 않는다. 앱은 cpio 를 읽기만 하므로 계측 시험의 표본은 손으로 짓는다(`ExtractEngineTest.newc`).
  **'컴파일만 되고 돌리지 않은' 계측 시험은 이렇게 기기에서 처음 깨진다** — 사본에서 만든 계측 시험은 합친 뒤 두 기기에서 돌려 본다.
- **Gradle 빌드 캐시(`org.gradle.caching=true`)는 사본에서 돈 시험 결과를 되살린다.** 시험이 읽는 `samples-local/` 은 시험의 입력으로
  선언돼 있지 않아 캐시 열쇠에 들지 않는다 — 표본이 없는 작업 사본에서 같은 코드로 돈 시험의 **'건너뜀'** 이 실제 저장소의 결과로
  왔다. 사본에서 만든 것을 합친 뒤에는 말뭉치 시험이 있는 모듈을 `--rerun-tasks` 로
  돌리고 건너뜀 수를 견준다.
- **Material 3 `Slider` 는 화면 낭독기에 범위의 백분율로 읽힌다.** '13 sp' 가 '30%' 로 들린다 — `contentDescription`(줄 제목)과
  `stateDescription`(실제 값)을 준다.
- **`PdfRenderer.Page.render` 에 행렬과 클립을 함께 주면 클립은 자르기만 한다.** 위 '`matrix = null` …' 의 짝이다 — 행렬 없이
  클립만 주면 쪽을 클립에 축소해 넣고, 행렬만 주면 **비트맵 전체가 클립**이라 자르기 상자 밖의 그림·재단선이 옆 쪽 자리에
  번진다(두 쪽 보기에서 드러났다 — 쪽 하나일 때는 비트맵이 곧 쪽이라 보이지 않는다).
- **`uiMode` 는 이 앱의 `configChanges` 에 없다.** 밤 모드를 바꾸면 액티비티가 다시 서고, ViewModel 의 상태는 남지만 컴포지션의
  상태(찾기 막대·뒤로 처리기)는 사라진다 — PDF 찾기가 칠은 남은 채 막대만 사라졌다. 화면이 켜 둔 것을 VM 에 물어 다시 세운다.
- **명세가 요구한 문장은 원문에서 글자로 옮겨라.** 한컴의 고지 문장은 '한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp)' 이고 첫 음절이 옛한글
  조합형 자모 셋(U+1112 U+119E U+11AB)이다. 13단계가 KDoc 으로 옮길 때 그 음절이 빠졌고, 14단계가 화면에 그 KDoc 을 바이트까지
  같게 옮겼다 — **두 곳을 견주는 시험은 둘이 함께 틀린 것을 보지 못한다.** 명세 원문(S01)의 코드포인트로
  확인했고, 시험이 그 글자를 박는다(`NoticeCatalogTest`).
- **값을 지우는 소비자를 `collectAsStateWithLifecycle` 의 기본값(STARTED)으로 돌리지 마라.** 다른 액티비티가 뜨는 동안 아래 액티비티는
  PAUSED 이면서 아직 STARTED 이고, 스낵바의 시계는 `delay` 라 가려진 뒤에도 흐른다. 파일 목록이 그 틈에 온 재생 실패로 **보이지 않는
  스낵바**를 띄우고 4초 뒤 `consumeFailure()` 를 불러, 재생 화면의 실패 문구와 '다른 앱으로 열기' 가 저절로 사라졌다(에뮬레이터 실측).
  말하고 지우는 일은 `repeatOnLifecycle(RESUMED)` 안에서만 하고, 지우기 직전에 **말한 그 값이 아직 남아 있는지** 다시 본다
  (`PlaybackNotice`). 앞의 '액티비티의 설정을 컴포저블이 쥐게 하지 마라' 와 같은 뿌리다 — 컴포지션은 가려진 것을 모른다.
- **안드로이드의 MIME 대조는 대소문자를 가린다.** `IntentFilter.findMimeType` 이 글자 그대로 견준다. 플랫폼 `MimeTypeMap` 은 읽을 때 전부
  소문자로 바꾸므로(표 파일에는 `macroEnabled` 가 대문자로 적혀 있어도) 우리 표도 전부 소문자여야 받는 앱이 나온다. 시험이 박는다
  (`MimeResolverTableTest`).
- **AndroidX 의 ViewModel 은 인텐트 extras 를 저장 상태의 기본값으로 받는다.** `ComponentActivity.defaultViewModelCreationExtras` 가
  `getIntent().getExtras()` 를 `DEFAULT_ARGS_KEY` 로 싣고, 새로 만드는 `SavedStateHandle` 이 그것으로 채워진다(내비게이션이 인자를 넘기는
  길이다). **내보낸 액티비티에서는 구멍이다** — 남의 앱이 extras 만으로 우리의 '되살린 상태' 를 지어내고(받기 화면이 받기 검사를 건너뛰고
  앱의 DB 를 뷰어에 띄웠을 것이다, 파일 목록이 앱 내부 폴더에서 시작했을 것이다), 우리가 모르는 클래스를 실으면 그 상태를 만드는 순간
  꺼내다 던져 **프로세스가 통째로 죽는다.** 내보낸 액티비티는
  `defaultViewModelCreationExtras` 를 재정의해 기본값을 비운다(`withoutIntentArgs`).
- **`taskAffinity=""` 여도 새 태스크 요청은 옛 태스크를 다시 쓴다.** `FLAG_ACTIVITY_NEW_TASK` 로 띄우면 시스템은 그 액티비티를 뿌리로 가진
  태스크를 찾는다(친화성이 아니라 뿌리의 구성 요소로도 맞춘다). standard 면 그 맨 위의 옛 화면 위에 새 화면이 쌓여, 새 파일을 닫을 때 옛
  파일이 다시 보인다. 받기 화면은 `singleTop` + `onNewIntent` 로 맨 위의 화면이 새 요청을 받는다(기기에서 `Activity not started, its current
  task has been brought to the front` 뒤 한 화면으로 확인했다).
- **매니페스트의 인텐트 필터에도 MIME 은 소문자로만 적는다.** 대조가 대소문자를 가리므로 대문자 변형을 함께 적으면 받을 것 같지만,
  lint 가 오류(`AppLinkUrlError`)로 멈춘다. 오피스의 `macroEnabled` 처럼 등록된 이름이 대문자여도 소문자만 적는다 — 플랫폼 표가 그렇게
  준다.
- **`tools:node="remove"` 는 그 요소 이름에만 듣는다.** `<uses-permission … INTERNET … tools:node="remove">` 는 라이브러리가
  `<uses-permission-sdk-23>`(API 23 부터만 청하는 모양)으로 들여온 INTERNET 을 지우지 못해 병합본에 남는다(라이브러리 매니페스트에 심어
  확인했다). 막을 모양마다 한 줄을 둔다. 그리고 병합 매니페스트를 `verify<변형>NoInternet` 이 보고(주석을 지운 뒤
  `uses-permission`·`uses-permission-sdk-23`·옛 이름 `uses-permission-sdk-m` 을 찾는다) `assemble<변형>` 이 그것을 기다린다 — 다시 새면 빌드가 멈춘다.
- **`adb shell am start` 의 SEND 는 `EXTRA_STREAM` 에 권한을 싣지 못한다.** 앱이 보낸 공유는 `Instrumentation` 이 URI 를 `ClipData` 로
  옮기며 읽기 권한을 함께 싣는데(`Intent.migrateExtraStreamToClipData`), 셸의 `am` 은 그 길을 지나지 않는다 — `--grant-read-uri-permission`
  을 줘도 권한이 없어 '받은 파일을 읽지 못했습니다' 가 뜬다. VIEW 는 데이터 URI 라 그 깃발이 듣는다. 공유를 셸로 시험할 때는 모든 파일
  접근 권한을 켜 두고(그러면 미디어 저장소의 경로로 제자리를 연다) 해석만 본다.
- **`FileOpManager.results` 는 한 줄을 나눠 받는다.** `Channel.receiveAsFlow` 라 모으는 쪽이 둘이면 사건 하나는 그중 하나에게만 간다.
  컴포지션은 가려진 액티비티에서도 살아 있으므로, 뒤에 깔린 `MainActivity` 가 앞의 화면(다른 앱에서 연 `OpenActivity`)이 시킨 풀기의
  결과를 보이지 않는 스낵바로 삼킨다. `Root` 는 `repeatOnLifecycle(RESUMED)` 안에서만 모은다 — 위 '값을 지우는 소비자를 …' 과 같은 뿌리다.
- **플랫폼 MIME 표는 생각보다 많이 안다 — 그리고 그 답을 받는 앱은 없을 수 있다.** `.xyz` 는 `chemical/x-xyz` 다. 구체적인 MIME 의 고르는
  창은 그것을 선언한 앱이 없으면 'No apps can perform this action.' 뿐이다 — '모든 앱에서 고르기' 로 넓힌 까닭이다('다른 앱으로 열기' 절).
- **고르는 창(`ACTION_CHOOSER`)은 받을 앱이 없어도 뜬다.** `startActivity` 는 성공하고 우리에게는 '띄웠다' 로 보인다 — 받을 앱이 없다는
  것을 결과로 알 길이 없다. 받는 앱이 하나면 고르는 창이 그 앱을 곧바로 띄운다(API 31 옛 고르는 창, API 35 IntentResolver 모두 실측).
- **`queryIntentActivities` 는 API 30 부터 우리에게 보이는 앱만 답한다.** 매니페스트의 `<queries>` 가 없으면 '받을 앱이 없다' 는 답이
  거짓일 수 있다. 그래서 그 답은 **넓히는 데만** 쓴다(막는 데 쓰면 공개 범위가 어긋난 기기에서 아무것도 못 연다).
- **설치 관리자는 `REQUEST_INSTALL_PACKAGES` 를 선언하지 않은 앱이 보낸 APK 를 말없이 닫는다**(targetSdk 26 이상, API 31 설치 관리자의
  dex 로 확인). 우리 쪽에는 성공으로 보인다 — 매니페스트의 그 줄을 지우면 APK 가 아무 말 없이 안 열린다.
- **Play 프로텍트가 이 앱을 '유해한 앱'(적대적 다운로더)으로 막는다**(사용자 폰). `REQUEST_INSTALL_PACKAGES` 를 걷은 판도 똑같이 막혔다 —
  판정이 이 앱(꾸러미 이름·서명·거의 같은 코드)에 붙어 남는 것으로 본다(추정). 설치는 Play 프로텍트 검사를 잠시 끄고 한다('14단계 뒤 — Play 프로텍트의 설치 차단').
- **`startActivity` 는 받는 앱의 창이 뜨기 전에 돌아온다.** 그 사이(수백 ms)의 누름은 우리 화면에 떨어진다 — APK 를 두 번 누르면 설치
  관리자가 두 겹 쌓였고, 시트·메뉴의 항목을 두 번 누르면 그것이 닫힌 자리 아래의 줄이 열렸다. 띄운 직후의 누름을 흘려보낸다.
- **Material 3 1.4.0 의 `ModalBottomSheet` 는 별도 대화상자 창이다**(`ModalBottomSheetDialogWrapper`, aar 에서 확인). 화면 아래의
  `SnackbarHost` 를 덮는다 — 시트가 떠 있는 동안 알릴 것은 시트 안에 적는다.
- **페이저 안의 WebView(`AndroidView`)는 핀치를 페이저에게 빼앗긴다.** 부모가 손가락 하나의 가로 움직임을 넘기기로 읽고 WebView 에
  취소를 보낸다 — SVG 쪽에서 가로 핀치는 거의 확대되지 않았고 세로 핀치만 됐다(폰 에뮬레이터). 두 손가락이 닿았거나 확대된 동안
  `requestDisallowInterceptTouchEvent(true)` 를 부르면 `AndroidView` 가 움직임을 부모보다 먼저 그 뷰에 준다(`ReaderWebView.holdsGestures`).
  화면에 맞춘 채 한 손가락으로 미는 것(넘기기·끌어 닫기)은 부모에게 남긴다. **'확대됐는가' 를 재는 기준에 크기 없는 뷰의 배율을 넣지
  마라** — 페이저는 옆 장을 크기 0 으로 먼저 세우고, 그동안 WebView 는 맞춤의 1/4 배율로 갔다고 알렸다가 자리를 잡은 뒤 맞춤으로
  돌아온다(0×0 에서 2.625 → 0.656, 1080×2400 에서 0.656 → 2.625). 그 0.656 을 기준으로 두었더니 맞춤 상태가 확대로 읽혀 가로로 긴
  SVG 장에서 다음 장으로 넘어가지 않았다(곧바로 연 장은 멀쩡해서, 앞 장에서 밀어 들어와야만 드러났다).
- **이 앱의 WebView 는 두 번 두드려도 확대하지 않는다**(두 에뮬레이터 실측 — `useWideViewPort` 를 끈 탓으로 보이나 확인하지 않았다).
  확대를 켠 쪽이라도 그렇다. 두 번 두드림이 필요한 쪽은 `GestureDetector` + `WebView.zoomBy` 로 직접 한다(`ReaderWebView.doubleTapZoom`).
  `zoomBy` 는 **뷰 가운데**를 축으로 키우고 0.01~100 밖의 값에는 던진다. 그 직후의 `scrollBy` 는 새 범위로 듣는다 — 축과 두드린 점의
  거리에 (배율 − 1)을 곱해 밀면 두드린 점이 손가락 밑에 남는다(두 기기에서 화소로 확인).
- **TTML 자막을 우리 변환(UTF-8 로 다시 쓰기)에 태우지 마라.** SRT·ASS·VTT 는 BOM 이 없으면 media3 가 UTF-8 로 단정해서 우리가 판정해
  바꿔 넣지만(10단계), TTML 은 XML 이라 인코딩을 선언문이 말한다. 바이트만 UTF-8 로 바꾸면 `encoding="UTF-16"` 같은 선언과 어긋난다.
  그대로 넘긴다(`SubtitleNames.needsTranscoding`).
- **WebView 의 렌더러가 죽으면 앱도 죽는다 — `onRenderProcessGone` 을 처리하라.** 이 앱의 WebView 는 렌더러 프로세스 하나를 나눠
  쓰고, 그것이 죽으면(문서가 무너뜨렸거나 시스템이 메모리를 찾아 죽였다) 떠 있는 WebView 가 모두 그 콜백을 받는다. 기본 구현이
  false 를 돌려주면 WebView 가 앱을 끝낸다(SVG 를 그림 뷰어가 미리 세우면서 노출이 커졌다).
  true 를 돌려주는 대가로 그 뷰를 떼어 내 부숴야 한다(`LockedWebView` 의 '렌더러가 죽으면'). 에뮬레이터에서는 루트 없이 렌더러를
  죽일 수 없어 `chrome://crash` 를 임시로 불러 확인했다.
- **media3 1.11.1 의 `TsExtractor` 는 188바이트 패킷만 안다.** 첫 188바이트 안의 한 자리에서 다섯 패킷의 동기 바이트를 찾는다(바이트코드).
  192바이트 M2TS(AVCHD `.mts`·블루레이 `.m2ts`)는 받는 추출기가 없다 — 재생 화면이 '재생하지 못하는 형식' 과 '다른 앱으로 열기' 를
  보인다(가짜 M2TS 로 기기에서 확인). 그래서 `.mts` 를 영상 표에 넣지 않는다(기타 — 누르면 곧바로 다른 앱). 그리고 **0번 자리만 보는
  판별은 잘라 낸 녹화를 놓친다** — media3 는 그것을 TS 로 알아본다.
- **에뮬레이터의 화면 꺼짐 시간은 '끄지 않음'(`screen_off_timeout` = 2147483647)이다.** 그래서 '영상을 보는데 화면이 꺼진다' 는 결함이
  오래 보이지 않았다. 화면이 켜져 있어야 하는 기능은 `adb shell settings put system screen_off_timeout 15000` 으로
  짧게 두고 `dumpsys power | grep mWakefulness` 로 잰다(끝나면 되돌린다).
- **`Modifier.fillMaxWidth().aspectRatio(r)` 는 높이에 맞추는 길을 잃는다.** 폭을 먼저 못 박으면 `aspectRatio` 가 높이로 물러설 수 없어
  상자가 부모보다 커지고 가운데 놓여 위아래가 잘린다 — 20:9 폰을 가로로 눕히면 16:9 영상이 2400×1350 이 되었다. 화면에 맞추려면
  `aspectRatio` 만 둔다(부모 `Box` 는 최소 크기를 0 으로 넘기므로 폭에 맞춰 보고 넘치면 높이에 맞춘다). 세로 화면만 재면 보이지 않는다.
- **Compose 의 `SelectionContainer` 는 여러 `Text` 의 조각을 줄바꿈 하나로 잇는다**(compose-foundation 1.12.1 `getSelectedText` 의
  바이트코드). 행마다 `Text` 하나인 목록에서 그대로 복사하면 4,096자로 자른 긴 줄의 조각 사이에 없던 줄바꿈이 들어가고, 화면에만 있는
  기호(줄 끝 표시)가 함께 간다. 복사는 `LocalClipboard` 를 가로채 `SelectionState.selectedTexts`(조각마다 문자열 주석이 살아 있다)로
  다시 잇는다(`RowCopy` — 글 고르기 절).
- **`SelectionContainer` 는 고른 줄을 게으른 목록 밖에서도 붙든다 — 그러나 그 줄의 내용은 우리가 준다.** VM 이 창(400행)만 들고 있으면
  붙들린 줄이 창 밖이 되는 순간 빈 칸이 되어 선택에서 조용히 빠진다. 칸이 마지막으로 그린 행을 든다(`HeldRow`).
- **`ZipOutputStream.setLevel` 을 항목 사이에 바꾸지 마라.** 안드로이드 12 에뮬레이터에서 수준을 0 으로 바꾼 다음 항목이
  `invalid stored block lengths` 로 풀리지 않았다(계측 시험이 잡았다 — JVM 에서는 확인하지 않았다). 압축하지 않을 항목은 STORED 로 쓴다
  (머리에 적을 크기·CRC 를 먼저 잰다). 원인(`deflateParams` 와 이 판의 zlib)은 확인하지 않았다.
- **`Box(contentAlignment = Alignment.Center)` 안에 `offset` 으로 자리를 잡는 아이를 넣지 마라.** 아이가 먼저 가운데 놓인 뒤 옮겨진다 —
  PDF 쪽 상자 안의 고르기 손잡이가 화면 가운데에서 옮겨진 자리(화면 밖)에 그려져 보이지 않았다. 덮는 층은 자기 `Box(fillMaxSize)`
  (왼쪽 위 정렬) 안에 둔다.
- **클립보드에 큰 글을 넣으면 앱이 죽는다.** 바인더 한 번에 1 MB 를 넘으면 `TransactionTooLargeException` 이고, Compose 의 복사는 그것을
  잡지 않는다(넣는 코루틴이 그대로 던진다). 넣는 쪽이 길이를 끊는다(20만 자 — 글 고르기 절). 그리고 **안드로이드 13 부터는 시스템이
  '복사했다' 를 보여 준다** — 앱은 12 이하에서만 띄운다(공식 안내가 겹쳐 알리지 말라고 한다).
- **WebView 에서 링크 위를 길게 누르면 고르기가 시작되지 않는다** — 브라우저의 관례다. 기기에서 고르기를 확인할 때 링크가 아닌 글을 누른다
  (EPUB 의 목차 장은 전부 링크라 처음에 '안 된다' 로 보였다).
- **`rememberSelectionState()` 는 저장되는 상태다**(`rememberSaveable` + `SelectionState.Saver`, 바이트코드로 확인). 고른 글을 강조의 색까지
  저장 상태 번들에 싣는다 — 수백 줄을 고른 채 홈으로 나가면 번들이 바인더 한도를 넘어 앱이 죽는다. 큰 글을 고르는 화면은
  `remember { SelectionState() }` 로 든다(`rememberTextFieldState` 와 같은 함정).
- **코틀린의 `getOrPut` 은 null 값을 기억하지 않는다.** 값이 null 이면 '없다' 로 보고 매번 다시 만든다 — '못 읽었다' 를 캐시하려던
  글꼴·객체 스트림이 참조마다 다시 풀렸다. 실패를 기억하려면 `containsKey` 로 갈라 넣는다.
- **`FileOpManager.results` 를 받는 것은 파일 목록이 있는 액티비티(`Root`)다. 다른 액티비티에서 시킨 작업의 결과는 그 화면에 가지
  않는다.** 재생 화면(`PlayerActivity`)에서 시킨 구간 저장의 성패를 거기서 말하려고 요청에 받는 자리(`Request.sink` —
  `FileOpManager.ResultSink`)를 달았다. 받는 자리는 화면을 쥐지 않는 프로세스 객체(`ClipJobs`)이고 화면이 앞에 있을 때만 받는다 — 아니면
  여느 결과처럼 목록이 말한다(어느 길로든 한 번).
- **제공자 호출에서 큐를 기다리다 '시간이 넘었다' 고 답하면 그 요청을 반드시 거둬라.** '못 했다' 고 말한 요청이 큐에 남으면 앞 작업이 끝난 뒤
  돌아 **말없이 원본을 바꾼다**(편집기의 저장이 300초 뒤 '바쁘다' 고 답하고 요청을 그대로 두던 판이 그랬다). 줄 선 것만 거두는 것은
  `FileOpManager.withdraw(id)`(곧바로 '취소' 를 내고 도는 것은 끊지 않는다), `cancel(id)` 는 도는 것까지 끊는다 — 넘겨받은 저장을 끊으면 사용자가 누른
  저장을 버린다. 그리고 바이트는 그 반영만의 파일로 옮긴 뒤 넣는다 — 같은 자리를 다음 반영이 덮으면 앞 요청이 남의 바이트를 쓴다.
- **MediaMuxer 의 Ogg 쓰기는 패킷마다 페이지 하나를 쓴다.** Opus 구간이 원본보다 5% 컸다(페이지 188개 → 8,903개). 표준대로의
  Ogg 이고 우리 재생기는 튼다 — 크기를 원본에 견주어 '깨졌다' 로 읽지 마라.
- **플랫폼의 MKV 추출기는 자막 트랙을 내주지 않는다.** `MediaExtractor` 가 영상·소리만 보여 주므로 구간 저장이 자막을 뺐는지 셀 수도 없다 —
  구간 칸이 '자막 트랙은 담지 않습니다' 를 늘 말한다(셀 수 있는 것은 담지 못한 소리 트랙뿐이다).
- **`ExifInterface.saveAttributes` 는 없던 기본 태그를 함께 쓴다**(방향 0 = 정의 안 됨, 화소 폭·높이 등 — 호환을 위한 기본값). 잘라 저장한
  JPEG 에 방향 태그가 0 으로 붙어 있어도 우리가 옮긴 것이 아니다.
- **화면 크기는 `LocalWindowInfo.current.containerSize` 로 읽는다.** `Configuration.screenWidthDp` 를 쓰면 lint 가 `ConfigurationScreenWidthHeight`
  를 낸다(이미지 뷰어의 옛 줄 둘은 그대로다 — 자르는 화면은 새 길로 썼다).
- **`pointerInput(key)` 안의 인식기는 세워질 때의 콜백을 쥔다.** `ZoomableImage` 가 `onTap`·`onLongPress` 를 그대로 넘겨, 바뀐 람다가
  키가 그대로인 동안 닿지 않았다. 바뀌는 콜백은 `rememberUpdatedState` 로 읽는다.
- **플랫폼 MKV 추출기의 되감기는 '앞의 키프레임' 이 아니다.** `MediaExtractor.seekTo(t, SEEK_TO_PREVIOUS_SYNC)` 를 MKV 에 주면 **소리는 t 뒤의
  첫 블록**으로, 목차(Cues)가 없는 **영상은 t 뒤의 첫 키프레임**으로 간다 — 방식(mode)을 보지 않는다(AOSP `MatroskaExtractor` 의
  `BlockIterator::seek`). MP4 는 앞으로 간다. 기기 시험이 잡았다: 1.0초를 고른 MKV 의 FLAC 소리가 1.02초부터 뽑혔다. 그 시각 이하의 동기
  표본에 서려면 앞에서 다시 찾아 걸어야 한다(`MediaRemux.seekAtOrBefore`).
- **이어 붙인 두 FLAC 프레임은 CRC-16 이 그대로 맞는다.** 비반사·초기값 0 인 CRC 는 '자기 CRC 를 품은 데이터' 에서 0 이 되므로, 앞 프레임
  (CRC 포함) 뒤에 이어 센 CRC 는 뒤 프레임 홀로의 CRC 와 같다. 그래서 '그릇의 표본 하나 = 프레임 하나' 를 끝의 CRC 로 확인할 수 없다(처음
  판이 그렇게 했다가 JVM 시험에 걸렸다). 경계는 다음 머리의 CRC-8·같은 흐름·그 앞까지의 CRC-16 세 겹으로만 안다(`FlacCut.frameEnd`).
- **안드로이드 문자열 자원은 감싸지 않은 앞뒤 공백을 지운다.** `<string> · %1$d개는 …</string>` 를 뒤에 이어 붙이면 `…)· 1개는` 이 된다 —
  구간 저장·풀기의 덧붙이는 문구가 그렇게 나가고 있었다. 공백이 뜻을 가지면 문자열을
  큰따옴표로 감싼다(`">" · …"<`).
- **`withContext` 는 블록이 끝났어도 그사이 취소됐으면 결과를 버린다.** 같은 디스패처로 들어가도 그 코루틴이 취소된 잡의 자식이라 끝나는
  자리에서 `CancellationException` 이 된다 — `NonCancellable` 안에서 파일을 놓고 결과를 돌려줘도 부른 쪽은 '취소' 를 받는다. 놓인 것을 알리는
  일은 반환값이 아니라 **놓는 그 자리의 콜백**으로 한다(`NewFiles.place` 의 `onPlaced` → `FileOpManager.execute` 의 `placed`).
- **플랫폼 MPEG-TS·PS 추출기의 되감기를 믿지 마라.** TS 추출기는 **소리 트랙만 고르면 되감지 않고**(AOSP 소스로 짚었고 기기 시험이
  확인했다 — 고침을 되돌리면 영상이 있는 TS 의 소리가 0:00 부터 뽑힌다), 영상과 함께 되감으면 **시간축을 그 자리 뒤에 처음 읽은 PES 로 다시
  세운다** — 그 PES 를 되감은 시각으로 놓고, 그보다 앞선 표본은 그 시각으로 눌린다(AOSP `ATSParser` 의 `mAbsoluteTimeAnchorUs`·`mFirstPTS`).
  방송처럼 영상을 소리보다 0.5초 앞서 실은 TS 에서 2.5~4.5초를 고르면 소리는 1.5초부터 담기고 영상은 열여섯 장뿐이었다(계측 시험이 손으로 지은
  TS 로 잰 값 — PTS 차례로만 섞은 TS 에서는 22 ms 어긋나 눈에 띄지 않았다). PS 추출기는 아예 되감지 못한다. 그런 그릇은 **되감지 않고 처음부터
  읽는다**(`MediaRemux.seeksUnreliably` — 키프레임은 따로 연 추출기로 걸어 찾는다). 그릇의 이름은 `MediaExtractor.getMetrics()` 의
  `FORMAT`(`MPEG2TSExtractor`·`MPEG4Extractor`·`MatroskaExtractor` — 이 기기 실측)이 말한다.
- **플랫폼 TS 추출기는 AAC 를 ADTS 머리째 준다**(형식에 `is-adts=1`). `MediaMuxer` 는 그것을 떼지 않고 MP4 에 담아, 표본마다 7바이트가 붙은
  m4a·mp4 가 나온다 — 그릇의 머리(esds)를 믿고 날것으로 푸는 재생기가 풀지 못한다. TS 의 소리를 뽑거나 TS 영상을 구간 저장하면 소리가 깨진
  파일이었다(계측 시험이 잡았다: 186바이트 프레임이 193바이트로 담겼다). 머리를 떼고 담는다(`Adts`). `.aac` 파일의 추출기는 떼어 준다.
- **NaN 은 `coerceIn` 을 그대로 지나고, Compose 의 `ProgressBarRangeInfo` 는 NaN 을 받으면 던진다**(`current must not be NaN`). 진행 막대와
  슬라이더가 화면 낭독기용으로 그것을 만든다. 그리고 **그리는 자리에서 값을 읽는 람다는 바깥의 `if` 를 믿지 못한다** — 재생 상태를 위임
  속성(`val state by …collectAsStateWithLifecycle()`)으로 들면 람다는 부를 때마다 **지금** 값을 읽는다. 재생 중에 소리 파일을 다시 누르면
  새 항목의 길이가 0 이 된 상태를 의미 갱신(스냅숏이 적용될 때 돈다)이 재구성보다 먼저 읽어 미니 바의 진행이 0/0 = NaN 이 됐고, 앱이
  죽었다(릴리스 크래시를 R8 retrace 로 풀어 `IroProgress` 의 `semantics` 를 찾았다). 막대의 값은
  `progressFraction`(NaN → 0)을 지나고, 미니 바의 람다는 상태를 한 번 읽어 그 값에서 위치와 길이를 함께 꺼낸다. `IroSlider` 도 NaN 을
  범위의 시작으로 바꾼다. 시험은 `core:ui` 의 `ProgressFractionTest`.
- **media3 의 TS 추출기는 파일 끝 112,800 바이트(188바이트 패킷 600개)에서 마지막 PCR 을 찾고, 못 찾으면 그 파일을 '되감을 수 없는' 것으로
  본다**(1.11.1 의 `TsDurationReader`). 재생 화면에 길이가 0:00 으로 뜨고 막대를 끌어도 그 자리로 가지 않는다 — 브라우저가 HLS 조각을 이어 붙인
  실물(3.38 GB)은 끝의 142,128 바이트에 PCR 이 없었다(PCR 은 영상 패킷의 일부에만 실리는데 끝은 소리 패킷이 대부분이다). 범위를 넓힌다 —
  `DefaultExtractorsFactory().setTsExtractorTimestampSearchBytes(…)`(`PlaybackService` 의 `TS_TIMESTAMP_SEARCH_BYTES`, 1 MiB 남짓). **같은 값이
  되감기의 한 걸음이 훑는 양이기도 하다**(`TsBinarySearchSeeker`) — 마구 키우지 마라.
- **PiP 에 들어가면 그 액티비티가 제 태스크로 옮겨 가고, API 31 은 전체 화면으로 되돌릴 때 원래 태스크로 돌려놓는다**(실측 — `[MainActivity,
  PlayerActivity]` 가 PiP 동안 `[MainActivity]` 와 고정 태스크 `[PlayerActivity]` 로 갈렸다가 되돌리면 다시 하나). 혼자인 태스크는 태스크째 고정되고,
  그 사이 런처를 누르면 그 태스크가 전체 화면으로 돌아온다. 그래서 `isTaskRoot` 는 PiP 를 오가며 바뀐다 — 다시 설 때마다 본다.
- **API 31 의 자동 PiP(영상을 트는 중에 홈)는 `onUserLeaveHint` 를 부르지 않는다** — `onPictureInPictureModeChanged(true)` 만 온다(로그로
  실측). 홈을 '사용자가 떠났다' 로 읽는 일은 모드 콜백에서도 해야 한다(재생 화면의 '보낸 앱으로' 표시가 그것을 놓쳐 홈으로 튕겼다). 같은 까닭으로
  `onUserLeaveHint` 에 둔 'PiP 첫 프레임 미리 접기'(10단계)는 그 길에서 돌지 않는다 — 첫 프레임에 조작부가 찍히는지는 확인하지 않았다.
- **재생 화면이 태스크의 뿌리일 수 있다.** 다른 앱이 넘긴 파일로 이 앱이 처음 뜨면 파일 목록이 없고, 그 태스크로 런처가 와도 `MainActivity` 는
  군더더기로 끝나(위 '런처 인텐트는 …') 재생 화면이 그대로 보인다. 거기서 그냥 끝내면 홈으로 떨어진다 — 뒤로 가기가 파일 목록을 연다
  (`PlayerSupport.openFileList`, 아래 '`.ts` 되감기·재생 화면의 뒤로 가기' 절). 그 파일 목록은 **이름만 가리킨 인텐트**로 연다 — 런처 인텐트로 열면
  재생 화면이 아직 뿌리라 `finishIfDuplicateLaunch` 가 끝낸다.
- **`transformable` 의 `canPan` 은 이번 프레임의 움직임만 받고, 처음 불리는 때가 터치 슬롭을 넘는 순간이다.** 끌기의 방향(가장자리 쪽으로 미는가)을
  그것으로 정하면 슬롭 안의 움직임을 놓친다. 방향은 `Initial` 단계에서 손가락을 따라 따로 잰다(`ZoomState.trackZoomDrags`).
- **AndroidX `ComponentActivity.dispatchKeyEvent` 를 덮어쓰면 lint 가 `RestrictedApi` 오류를 낸다.** 볼륨 키로 쪽을 넘기는 일은 `onKeyDown`·`onKeyUp`
  으로 한다 — 누름·뗌·되풀이를 모두 삼켜야 소리 막대가 뜨지 않는다.
- **`LazyList` 는 `contentPadding` 을 측정 단계에서 읽는다.** 상태를 읽는 `PaddingValues` 를 넘기면 다시 구성하지 않고 다시 재기만 한다 — 떠 있는
  단추 묶음의 높이를 목록 끝 여백으로 줄 때 그것을 쓴다.
- **`apply {}` 안의 `x = x` 는 바깥의 것이 아니라 수신 객체나 함수 인자에 대입하려 든다**('val cannot be reassigned'). 위 `Cipher…apply` 의 함정과 같은
  뿌리다 — 수신 객체 람다 안에서는 이름이 겹치지 않게 한다.
- **`enableEdgeToEdge()` 의 기본 내비게이션 바 스타일은 시스템 모드를 따르는 대비 막이다.** 밝은 모드 + 3버튼 내비게이션이면 강제로 어둡게 그린
  화면(영상) 아래에 흰 띠가 남는다. 영상이 보이는 동안 `SystemBarStyle.dark(TRANSPARENT)` 로 다시 건다(`PlayerActivity.holdSystemBars`).
- **`WindowInsets.systemBarsIgnoringVisibility` 는 `@ExperimentalLayoutApi` 다.** 시스템 바를 숨겼다 보였다 하는 화면에서 막대가 출렁이지 않게 하는
  인셋이라 쓰되(재생 화면·뷰어 넷), 판이 오르면 바뀔 수 있다는 것을 안다.
- **`getString(id, args)` 로 채우는 문구의 `<b>` 는 사라진다**(서식 문자열은 스타일을 지키지 않는다). 굵게 할 곳은 문장을 갈라 `AnnotatedString` 으로 잇는다.
- **플랫폼 TS 추출기는 PES 하나에서 시작하는 ADTS 프레임을 모두 한 표본으로 준다**(에뮬레이터: 964바이트 = 다섯 프레임). 표본 하나를 프레임 하나로
  보면 방송·HLS 의 소리를 '저장할 수 없다' 로 거절한다. 길이 칸으로 나누고 프레임마다 1024 표본씩 시각을 민다(`Adts.split`). 플랫폼 TS 추출기는 소리
  트랙을 하나만 내준다고 보는데(AOSP `MPEG2TSExtractor::init` 으로 추론) 기기로 확인하지 않았다.
- **ExifInterface 1.4.2 의 `saveAttributes` 는 파일을 말없이 자를 수 있다.** 마지막 내보내기가 `closeQuietly` 안이라 디스크가 차면 예외 없이 잘린 JPEG 이
  남는다(바이트코드). 쓰기 전의 길이와 꼬리 4 KiB 를 적어 두고 견준다 — JPEG 저장은 SOI · 새 EXIF · 나머지를 글자 그대로 옮기므로 꼬리가 같아야 한다.
  상했으면 날짜 없이 다시 쓴다(`ImageSaver` 의 `JpegTail`).
- **목차 없는 MKV 에서 마지막 블록 너머로 되감으면 `MediaExtractor.seekTo` 가 -1 로 끝난다.** 앞으로 간 것처럼 물러서서 다시 찾는다
  (`MediaRemux.seekAtOrBefore`).
- **commons-compress 1.28.0 의 `FramedLZ4CompressorInputStream(in, true)` 는 빈 프레임마다 재귀한다** — 빈 프레임 수만 개짜리 파일(`x.cbt`, 220 KB)이
  표지를 만들다 `StackOverflowError` 로 앱을 죽였다(그 폴더를 열 때마다). 프레임마다 `false` 로 열고 우리 고리로 넘는다(`Lz4FramesInputStream`).
  bzip2 도 한 스트림 안의 빈 블록에서 같은 꼴로 재귀한다(소스로 확인, 실물로 넘치게 하지는 않았다) — 해제기를 `StackGuardInputStream` 으로 감싸
  넘침을 `IOException` 으로 옮긴다. `Throwable` 을 잡는 압축 화면·풀기는 안전했고, 죽은 길은 표지와 만화였다.
- **Compose 의 글 고르기 막대는 '번역' 같은 `PROCESS_TEXT` 항목에 고른 글을 길이 상한 없이 인텐트로 싣는다** — 큰 선택이 바인더 한도를 넘으면 앱이
  죽는다(1.12.1 은 `EXTRA_PROCESS_TEXT_READONLY` 도 거꾸로 적는다). 그 항목(`ProcessTextKey`)을 거르고 같은 앱 목록으로 우리 항목을 단다(`RowSend` —
  10만 자, 서로게이트 쌍을 가르지 않게 자른다).
- **`BasicTextField` 는 글자 하나를 칠 때마다 글 전체를 다시 배치한다**(compose-foundation 1.12.1 의 `TextFieldLayoutStateCache.computeLayout` →
  `TextMeasurer.measure` — `StaticLayout` 하나). 큰 글을 편집기에 그대로 넣으면 타자가 굼떠진다 — 에뮬레이터에서 64 KiB 18 ms, 128 KiB 32 ms, 256 KiB
  65~85 ms(한 글자의 프레임, `dumpsys gfxinfo`). 그래서 편집은 256 KiB 까지다(`TextLimits.EDIT_MAX_BYTES`).
- **`TextFieldState.undoState`·`TextFieldBuffer.changes` 는 1.12.1 에서도 실험 API 다**(OptIn). 변경이 여럿인 고침(모두 바꾸기)은 되돌리기 기록에
  글 전체 두 벌을 남긴다.
- **초점이 없는 글 칸은 커서를 따라 스스로 스크롤하지 않는다.** 찾기는 배치 결과(`onTextLayout`)로 직접 민다 — 그 배치는 칸 하나의 것이라, 줄 접기를
  바꿔 칸이 새로 서면 다시 받아야 한다.
- **JDK 와 ICU(기기)의 문자 변환기는 왕복 결과가 다를 수 있다**(JDK 의 Big5 `A1 5A` 는 다시 쓰면 `A1 C4` 가 된다). 편집기는 엄격하게 읽히고 다시 쓰면
  같은 바이트인 파일만 연다 — 판정은 기기의 변환기로 한다(JVM 시험이 통과해도 기기에서 갈릴 수 있다).
- **`StaticLayout` 은 붓(`TextPaint`)을 참조로 들고 그릴 때 읽는다.** 메모마다 붓을 따로 둔다(그리기·메모 — 붓 하나를 돌려 쓰면 앞 메모의 빛깔·크기가
  바뀐다).
- **시스템이 거둔 몸짓은 이미 소비된 '뗌' 으로 온다.** 그리던 획은 그때 버린다(그리기·메모 — 끝까지 그은 것으로 치면 엉뚱한 획이 남는다).
- **편집기 화면(`EditorActivity`)은 설정이 바뀌어도 다시 서면 안 된다.** 다시 서면 WebView 가 새로 서고 쪽 안의 고친 것이 사라진다 — 글꼴 크기를 바꾸면
  고친 것을 잃었다. `configChanges` 에 fontScale·locale·layoutDirection·fontWeightAdjustment·mcc·mnc·touchscreen·colorMode 등을 더하고,
  `onConfigurationChanged` 는 밤 모드·글꼴·밀도가 바뀌었을 때 바탕·덮개·단추만 다시 짓는다(이미 떠 있는 물음 창은 다시 칠하지 않는다).
- **org.json 의 `optString` 은 JSON null 을 글자 `"null"` 로 준다.** 쪽이 보낸 `position: null`(읽던 자리를 모른다)을 자리 `"null"` 로 읽는다 — `isNull` 로 먼저 가른다(`HostBridge`).
- **합성한 `KeyboardEvent` 의 `keyCode` 는 만들 때 줄 수 없다(늘 0).** ONLYOFFICE(web-apps·sdkjs)는 keyCode 를 보므로 어댑터가 합성한 Escape 를 무시한다 —
  `Object.defineProperty` 로 붙인다(뒤로 가기로 메뉴·창을 닫는 길).
- **앱이 거절하며 까닭을 이미 말한 실패를 쪽이 다시 실패로 알리게 두지 마라.** 편집기의 저장 통로에서 앱이 받기를 거절하면(`save-failed`) 앱은 그 자리에서
  제 까닭(세션 없음·바쁘다·공간)을 띄우는데, ONLYOFFICE 어댑터가 그 거절을 `save-error` 로 되알려 앱이 일반 '저장하지 못했습니다' 창을 그 위에 덮었다 —
  까닭이 보이지 않았다. 거절에서 난 오류는 표시를 달아(`refused`) 되알리지 않는다. rhwp 는 AbortError 로 가른다(`saveFailure`).
- **태그 사이의 글을 정규식으로 볼 때 `[^<]*\S` 의 `\S` 는 `<` 도 먹는다.** 빈칸만 든 글 요소를 내용으로 셌다(ONLYOFFICE 의 빈 결과 검사) — `[^<\s]`.
- **자바의 `AES/CTR/NoPadding` 은 WinZip AES 의 CTR 이 아니다.** 자바는 계수기를 블록 전체의 빅 엔디언 정수로 올리고, WinZip(Brian Gladman 의
  구현)은 1 부터의 리틀 엔디언이다. IV 를 리틀 엔디언 1 로 주면 **첫 블록만 같아** 16바이트 이하의 항목은 맞게 풀린다 — 작은 표본만 보면 통과하고
  그보다 긴 항목부터 다른 도구에서 깨진다. 계수기 블록을 늘어놓아 `AES/ECB` 로 암호화한다(`AesZipCrypto` — lint 의 `GetInstance` 는 까닭을 적고
  껐다). `AesZipWriterTest` 가 둘이 다름을 박는다.
- **해제기는 제 흐름의 끝에서 멈추고 남은 입력을 읽지 않는다 — 인증값이 데이터 뒤에 있는 형식은 남은 것을 끝까지 읽어야 한다.** WinZip AES 의
  HMAC 은 암호문 뒤에 있고 CTR 은 비트를 골라 뒤집을 수 있어서, deflate 첫 블록의 '마지막 블록' 비트를 켠 변조가 **잘린 평문을 정상의 끝으로**
  통과시켰다(지금은 `ZipDecryption.authenticated` 가 남은 암호문을 읽고 인증값을 본다). 암호문이 해제기의 한 번
  채우기(64 KiB)보다 커야 드러난다 — 작은 표본만 보면 통과한다.
- **가운데 목록으로 읽는 리더는 항목 뒤의 빈틈을 모른다.** 인증값을 적힌 것보다 2바이트 길게 쓰는 결함(되돌려 넣어 본 것)이 우리 리더
  (commons-compress)와의 왕복 시험을 그대로 지났다 — 적힌 압축 크기 안에 맞는 인증값이 다 들어 있기 때문이다. 쓰개의 시험은 항목의 끝이 다음
  머리(마지막은 가운데 목록)와 맞는지를 따로 본다(`AesZipWriterTest` 의 `MiniZip`).
- **반디집 콘솔(`bz.exe`)은 암호가 틀리면 다시 묻고 기다린다** — 표준 입력을 닫아도 끝나지 않는다(콘솔에서 직접 읽는 것으로 보인다). 스크립트로
  돌릴 때는 시간 제한을 건다.
- **commons-compress 1.28.0 의 7z AES 열쇠 유도에는 상한이 없다.** `AES256SHA256Decoder.sha256Password` 가 코더 속성 첫 바이트의 아래 6비트만큼
  `1L shl n` 번 SHA-256 을 돌고 인터럽트를 보지 않는다(소스·바이트코드) — 62 면 끝나지 않는다. 라이브러리에 **암호를 넘기기 전에** 머리를 우리가
  읽어 반복을 본다(`SevenZKeyGuard` — '14단계 뒤 — 보안 점검 고침'). 암호가 없으면 유도하지 않는다(유도 전에 `PasswordRequiredException`).
- **예외를 이름(`simpleName`)으로 가르지 마라 — 릴리스(R8)에서는 이름이 줄어든다.** 압축·만화 화면이 'Encrypt' 가 든 이름으로 '풀지 않는 방식' 을
  갈랐는데 매핑 파일에서 `ZipDecryption$UnsupportedEncryptionException -> mp5` — 릴리스의 PKWARE 강한 암호화 ZIP 은 '깨진 파일' 로 나갔다. 공개된
  바탕 타입(`ArchiveEncryptionUnsupportedException`)으로 가른다. `MemoryLimit` 이름 보기(commons-compress 의 것)는 아직 이름이다(남은 일).
- **Git Bash 의 `grep` 은 CR 을 보지 못할 수 있다** — `grep -q $'\r'` 로 줄 끝을 쟀더니 CRLF 인 `PathRules.kt` 가 LF 로 나왔다. 줄 끝은
  파이썬으로 바이트를 세어 본다. 이 저장소의 `.kt` 는 대부분 LF 지만 CRLF 파일이 섞여 있다.

### 버전을 고정하는 곳

`gradle/libs.versions.toml` 하나다. **동적 버전(`+`, `latest.release`, 범위)을 쓰지 마라.**
빌드가 날마다 달라지면 "어제는 되던 것"을 추적할 수 없다.

---

## 빌드와 검증

```bash
# 데몬 JDK 는 21(JetBrains Runtime)이다. 아래는 이 기계의 경로이고 **기계마다 다르다** —
# 끝의 `.2` 는 Gradle 이 같은 툴체인을 여러 번 받을 때 붙이는 카운터다.
# 자기 기계 것은 `ls ~/.gradle/jdks/` 로 확인한다. 툴체인 자체는
# `gradle/gradle-daemon-jvm.properties`(vendor=JETBRAINS, version=21)가 정한다.
export JAVA_HOME="$HOME/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2"
./gradlew :app:assembleDebug      # 일상 개발용. 빠르고 스택트레이스가 읽힌다
./gradlew :app:assembleRelease    # R8 켜짐. 매 단계 끝에 이것으로 스모크를 돈다
                                  # assemble 은 병합 매니페스트에 INTERNET 이 없는지(verify<변형>NoInternet)도 본다 — 새면 멈춘다
./gradlew test                    # 순수 JVM 테스트(파서·방어·정렬)
./gradlew :app:lintRelease        # **매 단계 끝에 돌린다.** 아래 문단 참고

# 계측 테스트. 에뮬레이터가 떠 있어야 한다. **여섯 모듈 전부 돌린다.**
# **모듈을 새로 만들면 이 줄에 더한다** — 적지 않은 모듈의 계측은 한 번도 돌지 않는다.
./gradlew :core:io:connectedDebugAndroidTest \
          :feature:archive:connectedDebugAndroidTest \
          :feature:comic:connectedDebugAndroidTest \
          :feature:docview:connectedDebugAndroidTest \
          :core:webhost:connectedDebugAndroidTest \
          :feature:image:connectedDebugAndroidTest
```

**lint 를 매 단계 돌리는 이유는 `NewApi` 하나다.** 컴파일러는 `java.io` 의 함수가 minSdk 에 있는지 모른다 — `InputStream.readNBytes`(API 33)가
빌드와 시험을 통과하고 minSdk 31 기기에서 `NoSuchMethodError` 로 앱을 죽였다. lint 만 그것을 본다. **지금 이 저장소에서 lint 는 오류 0으로
통과한다** — 새로 뜨는 것만 보면 된다. **`:app:lintRelease` 는 라이브러리 모듈의 문제를 보고하지 않는다**(함정 표) — 모듈을 새로 만들거나 크게
고치면 `:<모듈>:lintRelease` 를 따로 돌린다. 남은 경고는 전부 옛 줄이다 — `app` 2(`MANAGE_EXTERNAL_STORAGE` 정책 안내 `ScopedStorage`, `targetSdk 36`
의 `OldTargetApi` — 새 판 알림이 더 뜰 때가 있다), `feature:browser` 5(`ModifierParameter` 1·`FrequentlyChangingValue` 4), `feature:image` 3
(`ConfigurationScreenWidthHeight` 2·`LocalContextResourcesRead` 1), `core:playback` 1(`ExportedService` — 재생 서비스). 나머지 모듈은 'No issues'
(2026-11 리팩터링이 `core:io`·`feature:docview`·`feature:player` 의 쉬운 것 — 쓸모없어진 `SDK_INT` 검사, 빈 `super.onCleared`, KTX
`createBitmap`, `mutableLongStateOf`, 매개변수 차례 — 을 걷었다). `feature:browser`·`feature:image` 의 것은 고치면 동작이 바뀔 수 있어
두었다(재구성 빈도·화면 크기를 읽는 자리. 화면 함수의 매개변수 차례는 부르는 쪽까지 옮겨야 한다).
까닭을 적고 끈 것 — `IncomingFiles` 의 `UsableSpace` 둘과 `ZipMaker` 의 하나(남길 공간을 재는 자리라 일부러 보수적인 값을 쓴다), `AesZipCrypto` 의
`GetInstance`(ECB — 계수기 블록만 암호화하는 CTR 이다).
**순수 JVM 모듈(`format:*`·`core:model`·`core:safety`·`core:charset`)은 lint 가 보지 않는다** — `tools/check_api_level.py` 가 그 몫이고 지금 0건이다
(함정 표. 자바 9 의 공변 반환 `ByteBuffer.limit(Int)` 처럼 표에 없는 것도 잡는다 — `Buffer` 로 불러 피한다). **`NewApi` 는 0건이다** — PiP 의
API 33 짜리 setter 넷(`setTitle`·`setSubtitle`·`setCloseAction`·`setExpandedAspectRatio`)을 부르지 않는다.

**JVM 시험은 1,750건이다**(순수 JVM 625 + 안드로이드 모듈 1,125), 실패 0. 건너뜀은 표본(`samples-local/`)이 있는 저장소에서 3(윈도에서 심볼릭
링크를 만들 권한이 없다 — `feature:text` 1, 그리고 pyzipper 와 견주는 `feature:archive` 의 `AesZipOracleTest` 2 — 환경 변수 `IROIRO_AESZIP_ORACLE` 이
있을 때만 돈다, '14단계 뒤 — AES 암호 ZIP'), 표본이 없는 작업 사본에서는 10(`format:epub` 4·`feature:docview` 3·`feature:text` 1·`feature:archive` 2).

| 모듈 | 시험 | 모듈 | 시험 | 모듈 | 시험 |
|---|---|---|---|---|---|
| `core:model` | 20 | `format:archive` | 251 | `feature:archive` | 39 |
| `core:safety` | 68 | `format:epub` | 78 | `feature:browser` | 67 |
| `core:charset` | 39 | `format:html` | 75 | `feature:comic` | 120 |
| `core:io` | 187 | `format:text` | 94 | `feature:docview` | 138 |
| `core:data` | 29 | `app` | 62 | `feature:editor` | 70 |
| `core:ui` | 106 | | | `feature:image` | 61 |
| `core:playback` | 96 | | | `feature:player` | 29 |
| `core:webhost` | 18 | | | `feature:settings` | 18 |
| | | | | `feature:text` | 85 |

**계측 테스트는 여섯 모듈에 167건이다** — `:core:io` 90(파일 조작·휴지통·EXDEV 25, 다른 곳에 복원 7, ZIP 만들기 9(암호 ZIP 3), 구간 저장·소리만
저장 20, 잘라 저장 5, 큐의 취소 4·줄 선 요청 거두기 1, 이름 겹침과 여럿 되돌리기 2, 글 저장 10(`TextSaverTest`), 그린 것 얹어 저장 1, 새 문서 서식 4
(`NewDocumentsTest`), 편집기 저장 2(`EditorSaverTest`)), `:feature:image` 6(그리기·메모 — `MarkupRenderTest`), `:feature:archive` 29(풀기 10, 암호
아카이브 4, tar·폴더 시각 5, 단일 압축 스트림·cpio·deb·자기 덮어쓰기 7, PATH_MAX 1, 우리가 잠근 ZIP 을 우리 풀기로 2 — `AesZipExtractTest`),
`:feature:comic` 12(쪽 디코딩·이어보기 10, 기본 방향·다음 권 2), `:feature:docview` 23(PDF 열기·렌더·타일·닫기 10, 암호 PDF 8, 이어 보기의 쪽 저장 4,
실세계 PDF 말뭉치 1 — `samples-local/corpus/` 를 기기에 밀어 두었을 때만 돈다), `:core:webhost` 7(가짜 출처의 경로 계약).
**암호 ZIP 의 계측 다섯**(`ZipMakerTest` 셋 — 머리 칸·빈 암호·취소, `AesZipExtractTest` 둘)은 **컴파일만 했고 기기에서 아직 돌지 않았다.**
`feature:settings` 는 계측이 없다. 암호 아카이브 표본(`format/archive/src/test/resources/archivecrypt/`)과 암호 PDF 표본은 JVM 시험과
**한 벌을 나눠 쓴다** — `feature/archive/build.gradle.kts`·`feature/docview/build.gradle.kts` 가 `src/test/resources` 를 계측 APK 의 자산으로 붙인다.
`플랫폼이_암호를_직접_받는다` 는 API 35 전용이라 폰 AVD 에서는 `Assume` 로 건너뛰는데,
AGP 의 XML 보고서는 그것을 `<failure>` 요소(`AssumptionViolatedException`)로 적는다 —
빌드는 통과한다. 실패로 읽지 마라.

**EPUB 은 계측이 0건이다.** ZIP·XML·문자열뿐이라 전부 순수 JVM 에서 돈다 — 그것이
`format:epub`·`format:html` 을 `format/` 에 둔 값이다. 깨진 표본을 마음껏 만들어
초 단위로 돌릴 수 있고, 에뮬레이터가 답할 것이 없다.

**파일시스템 경계는 계측 테스트로만 확인된다.** 볼륨을 넘는 `rename` 이 정말 `EXDEV` 를
내는가, 취소가 임시 파일을 남기지 않는가, 휴지통 왕복이 정합한가 — 이런 것은 JVM 테스트로
대신할 수 없다. 그 테스트는 **앱 전용 외부 디렉터리**(`getExternalFilesDirs`)에서 돈다:
권한이 필요 없으면서도 볼륨이 둘이라 EXDEV 가 그대로 성립한다.

**R8 은 1단계부터 켜져 있다.** 늦게 켜면 파서들의 리플렉션과 디코더 제거가
한꺼번에 터진다. 매 단계 끝마다 release 를 설치해 그 단계 기능을 훑는 것이 규칙이다.

### 에뮬레이터

AVD 둘이 있다. **폰 하나만 쓴다** — 사용자가 태블릿을 양도해 태블릿 에뮬레이터 시험이 필요 없어졌다(사용자). 계측 시험·기기 확인·릴리스
스모크는 `Android_12_Phone` 에서만 돈다.

| AVD | API | 화면 | 쓰임 |
|---|---|---|---|
| `Android_12_Phone` | 31 | 1080×2400 | 하한 검증. 일상 개발. **유일한 확인 기기** |
| `Android_15_Tablet` | 35 | 2560×1600 | 쓰지 않는다. AVD 는 지우지 않았다 |

**그래서 기기에서 확인되지 않는 것** — API 33 이상에서만 도는 갈래다. 플랫폼이 암호 PDF 를 받는 길(`PdfRenderer` 의
`LoadParams`, API 35)과 그 위의 PDF 찾기(`Page.searchText`), 계측 시험 `플랫폼이_암호를_직접_받는다`(폰에서는 `Assume` 으로 건너뛴다),
앱 언어를 따로 두는 시험(`cmd locale set-app-locales`, API 33+). 사용자의 기기(Android 12)에서는 그 갈래들이 돌지 않는다
(넓은 화면의 배치는 폰을 가로로 눕혀 본다) — 그대로 두되, 고칠 때 JVM 시험 말고는 지켜 줄 것이 없다는 것을 안다.

```bash
"$ANDROID_HOME/emulator/emulator.exe" -avd Android_12_Phone -no-snapshot-load -gpu auto &
adb install -r C:/.../app/build/outputs/apk/release/app-release.apk
adb shell appops set io.github.donggi.iroiroviewer MANAGE_EXTERNAL_STORAGE allow
adb shell am start -n io.github.donggi.iroiroviewer/.MainActivity
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png C:/.../s.png
```

adb 에 주는 **PC 쪽 경로는 윈도우 형식**(`C:/...`)이어야 하고, 기기 쪽 경로를 줄 때는
Git Bash 의 경로 변환을 `MSYS_NO_PATHCONV=1` 로 꺼야 한다. 에뮬레이터 둘을 함께 띄우면
`adb` 가 기기를 고르라고 한다 — `-s emulator-5554` 나 환경변수 `ANDROID_SERIAL` 로 고른다.

**사용자의 실기기에 설치하지 마라. 해야 하면 먼저 묻는다.** 사용자 폰(SM-A908N)에는 사용자가
쓰는 앱이 깔려 있는데, 우리 빌드와 서명이 다르면 `install -r` 이 거절되고 지우고 깔아야
한다 — 그 순간 휴지통 기록·이어보기·즐겨찾기가 사라진다. 에뮬레이터는 지우고 깔아도 된다.

**adb 로 검증할 때 실제로 밟은 함정.**

- **`adb push` 는 경로가 틀려도 조용히 아무것도 안 한다.** MSYS 경로(`/c/...`)를 주면
  성공한 것처럼 보이고 기기 파일은 그대로다. 그 상태로 시험하면 앱이 옛 파일을 옳게
  보고하는데 앱을 의심하게 된다 — 실제로 mkv 코덱 판정을 그렇게 한 시간 헤맸다.
  **push 뒤에 기기 쪽 크기와 시각을 반드시 확인한다.**
- **`adb shell run-as cat` 은 바이너리를 깨뜨린다**(CRLF 변환). SQLite 파일을 그렇게
  꺼내면 "file is not a database" 가 난다. **`adb exec-out` 을 쓴다.**
  그리고 `run-as` 는 **디버그 빌드에서만** 된다 — 릴리스 앱의 내부 파일은 볼 수 없다.
- **`$(adb shell pidof …)` 에는 `\r` 이 붙는다**(Git Bash) — 그대로 넘기면 `kill: unknown pid` 다. `tr -d '\r'` 로 뗀다. 그리고 디버그 앱이라도
  `run-as … kill` 은 앱의 프로세스를 보지 못했고, 재생 서비스가 도는 프로세스는 `am kill` 로 죽지 않는다. 액티비티를 다시 세우는 시험(저장 상태)은
  밤 모드를 잠깐 바꿔 했다(`cmd uimode night yes` — **시험 전 값(`cmd uimode night` 으로 읽는다, 에뮬레이터는 `auto`)으로 되돌린다**).
- **프로세스가 죽은 뒤 되살아나는 화면은 `am crash` 한 번으로 시험한다**(재생 화면의 되살리기). 재생 서비스 때문에 `am kill` 이 듣지
  않는다. 재생 화면 위에 `MainActivity` 를 같은 태스크로 띄운 채 `adb shell am crash <패키지>` 를 보내면 재생 화면이 저장 상태로 다시 선다.
  **60초 안에 두 번 하면** 'keeps stopping' 이 태스크를 비워 되살릴 것이 없어진다.
- **`uiautomator dump` 는 화면에 애니메이션이 있으면 간헐적으로 실패한다.** 미니 재생
  바의 진행 표시가 계속 움직여 창이 idle 이 되지 않기 때문이다. 앱 문제가 아니다
  (죽는 PID 도 uiautomator 자신이다). 좌표를 한 번 얻어 두고 `input tap` 으로 진행하라.
- ffmpeg 으로 시험 영상을 만들 때 **`-preset ultrafast` 는 `testsrc` 를 High 4:4:4
  Predictive(`avc1.F4001E`)로 인코딩한다.** 어떤 기기도 그것을 못 푼다. 표본은
  `-profile:v baseline -pix_fmt yuv420p` 로 만든다.

**실측으로 확인한 것.**

- `adb root` 는 막혀 있다(Play Store 이미지). 그러나 **`appops set` 은 루트 없이 된다** —
  권한 부여 자동화가 살아 있다.
- **SD 카드가 `/storage/0000-0000` 에 별도 FAT 볼륨으로 마운트된다.** `StorageManager` 도
  두 볼륨을 따로 준다. 4단계의 EXDEV(볼륨 간 이동) 경로를 이 에뮬레이터로 실제로 태울 수 있다.
- `screencap`·`input` 동작.

### 일하는 법 — 지난 실수에서 남은 것

- **문서를 고치는 손도 근거를 코드에서 다시 읽어야 한다.** 시험·lint·릴리스 스모크가 다 통과해도 문서가 '이미 했다' 고 적은 것이 사실이
  아니었던 적이 있다(계측 명령에 모듈 하나만 적혀 다른 모듈의 시험이 한 번도 돌지 않았다, 의존 표가 코드와 어긋났다, 읽는 코드가 없는 상한을
  '막는다' 고 적었다).
- **결함을 고칠 때는 그 함수 전체를 읽어라.** 한 줄 아래에 같은 종류의 결함(취소를 삼키는 `runCatching`)이 앉아 있곤 하다.
- **폐기한 근거를 주석에 남기지 마라.** 파일을 먼저 여는 사람이 바로 그 오해를 집는다.
- **조사 → 설계 → 반증 차례로.** `javap`·`dexdump` 로 라이브러리·플랫폼의 사실을 먼저 캐면 설계가 기기에서 죽기 전에 뒤집힌다(A-B 를 클리핑으로,
  PiP 파라미터를 레이아웃마다 갱신하려던 계획이 그렇게 뒤집혔다). 반증이 잡는 치명은 대개 '설계가 선언한 불변식이 코드로는 성립하지 않는다' 는
  꼴이다. **'언제 불리는가'(콜백 차례)는 문서에도 바이트코드에도 없다** — 기기에서 `Iro.d` 로 찍어 맞춘다.
- **재현되지 않는 결함은 캡처가 '코드가 만들 수 있는 그림인가' 부터 따진다.** 그리고 에뮬레이터가 태울 수 없는 길(센서 방향·블루투스 등)이
  무엇인지 먼저 묻는다('하단이 검게 보인다' 는 `adb emu rotate` 를 주지 않으면 탈 수 없는 길이었다).
- **캡처는 동작 확인이자 눈으로 하는 검토다.** 사용자가 잡은 것(늘어난 영상, 동떨어진 ⋮ 메뉴, 영원히 남는 안내 …)은 대개 찍어 둔 캡처에 이미
  보였다. **표본이 작으면 없는 결함이 된다**(긴 재생목록의 끝은 6곡이 아니라 24곡에서 드러났다).
- **성능 불변식은 정확성 시험이 대신 지켜 주지 않는다** — 시험이 성능 불변식을 직접 단언하게 한다(`AnchorReproTest`). 실측 기록에 쓰는 숫자를
  늘려라 — 재지 않았으면 못 잡는다.
- **고친 것은 고침을 되돌려 시험이 정확히 그것만 깨지는지 확인한다.** 표본이 의도한 경로를 타지 않으면 시험은 통과하면서 아무것도 시험하지 않는다.
- **사본에서 나눠 고치고 합칠 때** — 줄 끝이 다른 파일은 3-way 가 통째로 충돌로 본다(바탕에 맞추고 다시 합친다). 두 쪽 살리기(union)는 경계에서
  괄호·KDoc 을 깨뜨린다 — 빌드의 끝 코드만 보고 '통과' 로 읽지 말고 기록의 `e:` 를 본다. 따로 쓴 두 갈래는 같은 규칙을 한쪽만 따르기 쉽다 — 합친 뒤
  '한쪽에만 들어간 규칙' 을 찾는다. 사본에서 쓰고 돌리지 않은 계측 시험은 합친 트리에서 돌린다.
- 형식 있는 `combine` 은 흐름 다섯까지다 — 여섯째는 `.combine(...)` 으로 잇는다(`SettingsViewModel.values`).

---

## 실측표

**추정으로 적지 않는다.** 아래는 전부 이 기계·이 에뮬레이터에서 실제로 잰 값이고,
잰 방법은 앱 안의 진단 화면(2단계, `feature:diag`)이다. 값이 바뀌면 여기를 고친다.

### 문자셋 (Android 12 에뮬레이터)

| 이름 | 안드로이드가 돌려주는 것 | 개발 PC 의 JVM |
|---|---|---|
| `x-windows-949` | **`EUC-KR`** | `x-windows-949` |
| `MS949` | **`EUC-KR`** | `x-windows-949` |
| `windows-949` | **`EUC-KR`** | `x-windows-949` |
| `EUC-KR` | `EUC-KR` | `EUC-KR` |
| `Shift_JIS`·`windows-31j` | `Shift_JIS` | — |
| `GB18030`·`Big5`·`UTF-16LE` | 그대로 | — |

**두 환경이 다르다.** 안드로이드는 CP949 별칭을 전부 `EUC-KR` 로 정규화한다. 그래서
이름을 하드코딩하지 않고 후보를 순서대로 찾는다(`TextEncoding.CP949` 한 곳). 단위 테스트가
PC 에서 통과했다고 기기에서 같을 것이라고 믿지 마라 — 진단 화면이 기기의 답이다.

### 코덱 (Android 12 x86_64 에뮬레이터, 디코더 36개)

| 있음 | 없음 |
|---|---|
| H.264, HEVC, VP9, **VP9 Profile2(10bit)**, AV1, FLAC, Opus, Vorbis | **HEVC Main10**, ALAC, AC-3, E-AC-3, DTS |

에뮬레이터 값이고 실기기는 다르다. 이 표의 쓸모는 "**코덱은 기기가 주는 만큼**" 이라는
요구사항 해석이 실제로 어떤 모양인지 보여주는 것이다. 재생 실패는 코덱 이름과 함께 알린다.

### SDK 확장과 PDF (Android 12 에뮬레이터)

S 확장 = 1, R 확장 = 1 → **레거시 `PdfRenderer` 경로**(암호 PDF 불가).

**3분기(플랫폼 35+ / `PdfRendererPreV`(확장 13+) / 레거시)를 넣지 않는다**(11단계). 근거는 SDK 의
`api-versions.xml` 이다 — `android.graphics.pdf.PdfRenderer` 의 `openPage`·`Page.render` 는 전부 `since="21"` 이고
`sdks` 속성이 없다. 즉 **우리가 부르는 API 는 확장 수준과 무관하게 같은 것 하나**이고, 분기를 넣어 봐야 같은 호출을
세 번 적는 일이 된다. 확장 13 이상이 주는 것은 암호 PDF 열기(`loadParams`)와 폼·주석 편집이다.

**암호 PDF 때문에 분기가 하나 있다** — 암호를 받는 생성자 `PdfRenderer(pfd, LoadParams)` 는 **API 35 프레임워크**에만 있다(그 생성자에는
`sdks` 가 없다). 35 이상은 플랫폼이 풀고, 그 아래는 우리가 풀어 메모리 파일로 넘긴다(아래 '암호가 걸린 파일'). 확장 13 의
`PdfRendererPreV` 는 쓰지 않는다. 두 갈래 모두 에뮬레이터에서 돌려 확인했다(35 갈래는 태블릿 AVD — 지금은 기기에서 돌지 않는다, 위
'에뮬레이터'). `@RequiresApi(35)` + `SDK_INT` 검사라 `NewApi` 는 0건이다.

### 목록 성능 (Android 12 에뮬레이터, 파일 10,000개 폴더)

| | |
|---|---|
| 나열(`Os.lstat` 항목당 1회) | **394 ms** |
| 정렬(한국어 대조 + 자연 정렬, 키 미리 생성) | **87 ms** |
| 합계 | **481 ms** (판정 1초) |
| 스크롤 잔카 | 340프레임 중 7 (2.06%), 90퍼센타일 21 ms |

정렬 키를 항목마다 미리 만드는 것이 이 수치의 핵심이다. 비교할 때마다 `Collator` 를
부르면 1만 개에서 비교가 14만 번 일어나 그것만으로 수백 ms 가 든다.

---

## 기능별 설계 — 정한 것과 그 까닭

단계·회차의 진행 기록과 실측 일지는 걷고(맨 앞 '걷은 기능의 기록' 참고) 지금의 결정·규칙·한계만 남겼다. 절 이름의 'N단계'·'14단계 뒤' 는
그 기능이 들어온 자리를 가리킨다(아래 '단계 번호').

### 파일 조작 — 실측이 정한 전제 (4단계)

- **볼륨 간 `rename` 은 EXDEV 로 실패한다** — 계측 테스트가 이것을 먼저 단언한다. 이 전제가 깨지면 EXDEV 경로의 시험이 그 길을 한 번도 안 타고
  '통과' 한다.
- 복사를 취소하면 임시 파일(`.iroiro-part`)이 남지 않지만 **정리에 수 초가 걸린다**(스트림을 닫는 시간) — 취소 직후에 확인하면 아직 있다.
- 휴지통의 정본은 Room 이다. 사이드카 내용은 `{uuid, deletedAt, isDirectory, size}` 뿐 — **경로·이름을 적지 않는다.** 사진을 지우면 MediaStore
  항목도 사라진다(갤러리에 유령이 없다).
- 포그라운드 서비스는 400ms 뒤에 붙는다(채널 '파일 작업', 무음, 취소 액션 하나 — 함정 표의 '즉시 끝나는 작업').

### 재생 — 세션과 백그라운드 (5단계)

**백그라운드 재생에 스위치가 없다.** 플레이어가 서비스에 살기 때문에 끊는 코드가 없을 뿐이다. 화면은 `MediaController` 로 **돌고 있는 세션에
다시 붙는다.** 최근 앱에서 밀어 없애면 재생이 멈추고 서비스가 내려간다(`onTaskRemoved`). **세션에 붙는 컨트롤러는 우리 앱·미디어 알림·Auto·
`android` 만**이고 나머지는 거절한다 — 파일 이름이 메타데이터로 나간다. 이어보기 기록은 1분 넘는 것만, 10초 지난 뒤부터 적고 끝 15초 안쪽은
지운다(아래 '이어보기').

### 세로 고정을 무시하는 가로 재생

자동회전을 끄고(`settings put system accelerometer_rotation 0`) 에뮬레이터를 가로로 돌린
상태에서 잰 것이다. `adb emu rotate` 가 센서 방향을 바꾼다.
`ActivityInfo.SCREEN_ORIENTATION_SENSOR` 가 문서대로 **사용자의 자동회전 끄기 설정을
무시한다**는 것이 이 대조군으로 확인됐다. `OrientationEventListener` 로 각도를 직접 재는
흔한 방법은 필요 없다 — 그것은 우리가 센서를 다시 구현하는 것이다.

**영상일 때만** 건다(`state.hasVideo`). 소리만 나는 파일까지 따라 돌면 사용자가 세로 고정을
켜 둔 뜻을 정면으로 거스른다. 화면을 떠날 때 `UNSPECIFIED` 로 돌려 놓는다.

**방향 잠금 단추**(10단계)는 **누른 순간의 방향을 구체값**(`LANDSCAPE`·`PORTRAIT`)으로 적어 건다 — `SCREEN_ORIENTATION_LOCKED` 는
'거는 그 순간의 방향' 에 묶이는데 이 앱은 `onStop` 에서 방향 요청을 놓았다가 다시 설 때 새로 걸기 때문이다(함정 표). 잠금은 영상이
아니게 되어도 **풀리지 않는다**(`hasVideo` 는 항목을 건너뛰는 동안 잠깐 거짓이 되므로 그것으로 풀면 영상 → 영상으로 넘길 때마다
사용자가 건 잠금이 조용히 사라진다). 그래서 **잠겨 있는 동안에는 소리 파일에서도 단추를 남긴다** — 그러지 않으면 푸는 방법이 없다.

```bash
# 실측: 자동회전이 꺼진 기기를 `adb emu rotate` 로 가로로 돌린다
# 잠금 전 1080x2400 → 잠그고 회전 1080x2400(그대로) → 풀면 2400x1080
adb shell dumpsys window displays | grep -oE "cur=[0-9]+x[0-9]+"
```

**태블릿에서 안 들을 수 있다. 다만 이유가 targetSdk 36 이 아니다.** Android 16 이 무시하는
것은 *고정* 방향(`portrait`·`landscape` 등)이고 `SCREEN_ORIENTATION_SENSOR` 는
`ActivityInfo.isFixedOrientation()` 에 들어 있지 않아 그 정책의 대상이 아니다. 진짜 조건은
**그 디스플레이의 `ignoreOrientationRequest`** 다.

```bash
adb shell dumpsys window | grep ignoreOrientationRequest   # 폰 AVD: false (실측)
```

폰 AVD 는 `false` 라 요청이 먹는다. 태블릿 AVD 는 `true` 일 것으로 보이나 재지 않았다.
**이유를 틀리게 적어 두면 필요 없는 opt-out 속성(`PROPERTY_COMPAT_IGNORE_REQUESTED_ORIENTATION`)을 넣게 되므로 여기 정확히 남긴다.**

### 썸네일 캐시

축소가 **헤더 단계**에서 정해지는 것이 핵심이다(`ImageDecoder.onHeaderDecoded` 에서
`setTargetSampleSize`). 12MP(4000×3000)면 sample=8 이라 500×375, 원본의 1/64 만 올라간다 —
통째로 디코딩하면 그 한 장이 48MB다. `ImageDecoder` 를 쓴 또 다른 이유는 **EXIF 회전을
알아서 적용**하는 것이다. `BitmapFactory` 로 읽으면 세로로 찍은 사진이 격자에서 전부 눕는다.

캐시는 `filesDir/thumbs/` 다. **공유 저장소의 `.thumbnails` 가 아니다** — 거기 쌓으면
모든 파일 접근 권한을 가진 어떤 앱이든 '이 사람이 어떤 사진을 갖고 있는가' 를 읽고,
원본을 지워도 축소본이 남는다. `cacheDir` 도 아니다(시스템이 비우면 수천 장을 다시 디코딩한다).

**폴더마다 통이 나뉜다**(`filesDir/thumbs/<폴더해시>/<키>.jpg`). 소비자가 둘(갤러리·브라우저)이라, 평평한 캐시 + "살아 있는 키에
없으면 전부 지운다" 는 서로를 지운다. **폴더를 통째로 열거한 쪽만**(`DirectoryLister.Ready`) 그 통을 청소하고, 갤러리는 청소하지
않는다 — 이미지만 골라 훑으므로 자격이 없다. 목록·격자 모두 사진·동영상을 그림으로 그리고 동영상에는 ▶ 를 얹는다.
`.nomedia` 폴더와 휴지통에서는 디스크에 남기지 않는다(`persist=false`). `core:ui` 는 `core:io` 를 볼 수 없어 그 판정을 못 하므로
호출자가 넘긴다.

### 이미지 뷰어 (6단계)

**확대 상태에서 페이지가 넘어가지 않는 것**이 이 화면에서 가장 깨지기 쉬운 계약이다.
`transformable(canPan)` 이 팬을 소비하면 부모인 페이저가 못 받는다 — 형제 `pointerInput`
의 순서에 기대지 않고 **부모–자식 계층**으로 계약을 세운 이유다. 다만 확대한 채 가장자리에 닿은 뒤 **새로 시작한 끌기**는
넘긴다(그러지 않으면 확대하면 다음 사진으로 갈 길이 없다). 끌기마다 손가락이 닿은 뒤 움직인 방향으로 한 번 정한다
(`ZoomState.claimsDrag` + `trackZoomDrags`) — `transformable` 의 `canPan` 은 이번 프레임의 움직임만 받고 처음 불리는 때가 터치 슬롭을
넘는 순간이라, 방향은 `Initial` 단계에서 따로 잰다(함정 표).

### EXIF 무손실 회전 (6단계)

EXIF 블록만 다시 쓴다 — 재인코딩이 아니고, **수정 시각을 바꾸지 않는다**(날짜 정렬과 이어보기 키가 끊기지 않는다). 결과는 뷰어 안에서 알린다.

**저장할 수 있는 형식을 좁혔다.** 기준은 "라이브러리가 쓸 수 있는가" 가 아니라
**"쓰면 실제로 돌아 보이는가"** 다.

| 형식 | 저장 | 왜 |
|---|---|---|
| JPEG | ○ | 쓸 수 있고 디코더가 적용한다 |
| WebP(정지) | ○ | 같다 |
| WebP(애니메이션) | ✗ | `saveAttributes` 가 컨테이너를 다시 쓰는데 **프레임 보존을 확인하지 못했다.** 표본을 만들 도구(ffmpeg)가 없었다 — 확인 못 한 것으로 사용자 파일을 걸지 않는다 |
| PNG | ✗ | 쓸 수는 있는데 **안드로이드 PNG 디코더가 EXIF 를 읽지 않는다.** 파일은 바뀌는데 아무 일도 안 일어난다 |
| HEIC·AVIF·RAW·GIF·BMP | ✗ | `saveAttributes` 자체가 던진다. 재인코딩은 사용자의 HEIC 를 JPEG 으로 바꾸는 일이다 |

판정은 **두 곳에 있다** — 화면은 메뉴를 끄는 데, 엔진은 실제 쓰기를 막는 데 쓴다.
애니메이션 WebP 판별은 RIFF 청크 머리만 훑는다(`ImageFormats`, JVM 시험 8건).

### 텍스트·코드 뷰어 (7단계)

색인은 16 MB 로그를 219 ms 에 훑는다(`/sdcard` 의 FUSE 를 지나 약 73 MB/s). 색인 메모리는 앵커 하나 16 B 라 1 GB 파일이라도 0.8 MB 수준이다 —
**텍스트 파일 크기에 상한을 두지 않는** 근거다(설계는 아래 '7단계가 세운 것과, 실측이 잡은 것'). 4 MiB 를 넘는 파일은 강조를 끄고 화면이 그렇게
말한다.

### 고정폭 글꼴 — 칸이 맞지 않는다 (7단계)

저장소에 글꼴을 넣지 않기로 하고(공개 저장소다) `Typeface.MONOSPACE` + 시스템 폴백을
쓴다. **글자는 빠지지 않는다.** 대신 칸이 안 맞는다.

| `textSize=100` 기준 | 폭 | 라틴 대비 |
|---|---|---|
| 라틴(`M`) | 60.0 | 1.000 |
| 숫자 | 60.0 | **1.000** — ASCII 끼리는 진짜 고정폭이다 |
| 한글 | 92.0 | **1.533** |
| 한자 | 100.0 | **1.667** |
| 가나 | 100.0 | **1.667** |

두 배가 아니고, **한글과 한자가 서로도 다르다.** 그래서 한글이 섞인 줄은 세로 정렬이
어긋나고, 한글과 한자를 같이 쓴 줄은 더 어긋난다. 코드에서 CJK 는 대개 주석과 문자열에
있어 실제로 걸리는 자리는 드물다.

**고치려면 Noto Sans Mono CJK 를 커밋해야 하는데 그것이 이 저장소의 규칙과 정면으로
부딪친다**(GitHub Pages 가 통째로 서비스한다). 글자폭을 코드로 맞추는 길(글자마다
`letterSpacing` 을 넣어 칸에 밀어 넣기)은 선택과 찾기 좌표를 깨뜨린다. **맞추지 않고
적어 둔다.**

### 압축 제품화 (8단계)

**엔트리 하나씩 여는 비용**(개발 PC, JVM 21. 같은 아카이브를 엔트리 수만 두 배로).

| 방법 | 60개 | 120개 | 배율 |
|---|---|---|---|
| 7z, `open()` 반복 | 702 ms | 2,705 ms | **3.85배 = 제곱** |
| 7z, `extractSequentially` | 29~34 ms | 54~56 ms | **1.60~1.89배 = 선형** |
| ZIP, `open()` 반복 | 19~23 ms (1000개) | 37~39 ms (2000개) | **1.63~1.89배**(선형) |

7z 는 `open(N)` 마다 파일을 새로 열고 `nextEntry` 를 N번 돌린다. 1,000개짜리를 하나씩
풀면 몇 분이 걸린다. **그래서 푸는 길은 `extractSequentially` 하나뿐이고, `open` 은 한
항목을 보여 주는 용도로 좁혔다.** 같은 60개에서 702 ms → 34 ms, **20배**다.

**배율을 단언하는 시간 시험(`SequentialCostTest`)은 세 번 흔들렸고, 그때마다 배운 것이 지금의 짜임이다.**
ZIP 만 엔트리 수가 다른 것은 60·120개면 이 기계에서 **5 ms** 라 배율이 비용이 아니라 타이머 잡음을 쟀기 때문이다 →
**몇십 ms 대로 키우고, 같은 일을 여러 번 재 가장 빠른 값을 쓴다**(잡음은 언제나 시간을 늘리는 쪽으로만 작용한다). `./gradlew test` 가
모듈별 테스트 워커를 **병렬로** 돌리는 동안 짧은 측정일수록 같은 길이의 방해에 더 크게 부푼다 → **두 크기를 번갈아 잰다**
(`fastestPair` — 붐비는 구간이 두 측정에 같은 확률로 걸리므로 부하가 있으면 두 값이 함께 커질 뿐 배율은 남는다). 시험이 늘어 병렬 워커가
오래 겹치자 양쪽 방향으로 번갈아 깨졌다 → **표본을 다섯 번으로**. 상한을 푸는 길은 쓰지 않았다. **배율을 단언하는 시험은 절대
시간이 아니라 두 측정의 관계를 지켜야 한다** — 그러려면 두 측정이 같은 환경을 봐야 한다.

**`abort()` 는 임시본을 언제나 지운다.** "대상이 없고 임시본이 온전하면 그것이 남은 유일한 사본일 수 있다" 는 예외는 두 호출자
모두에서 성립하지 않는다(복사는 원본이, 풀기는 아카이브가 그대로 있다) — 그 예외 때문에 폭탄을 막은 자리에 1,042,580바이트짜리 숨은
파일이 남았었다.

### 압축 — R8 keep 규칙

R8 keep 규칙(`org.tukaani.xz.**`, `com.github.junrar.unpack.**`)이 LZMA2 7z 와 RAR 해제를 살린다. **릴리스 스모크에서 7z 풀기를 다시 본다** —
규칙이 지워지면 빌드는 통과하고 사용자가 7z 를 여는 순간에 터진다. 압축 RAR5 는 미검증이다(junrar 에 쓰기 구현이 없어 표본을 만들 수 없다).

### 만화 뷰어 (9단계)

**쪽 판정을 눈으로 하지 않는다.** 표본을 쪽마다 다른 단색으로 만들고 화면 캡처의 색을 읽어 "지금 몇 쪽인가" 를 기계가 답하게 한다(PDF 도
같다 — 쪽마다 단색 + 흰 숫자). 예산은 `ImageLimits.budgetOf`(PDF 도 같은 표). solid 7z 의 창 밖 쪽은 앞 쪽을 다 풀어야 해서 1~2초가 걸리고
창 안은 0 ms 다.

### 9단계가 세운 것과, 실측이 잡은 것

**만화 쪽은 디스크에 쓰지 않는다.** 설계 검토에서 가장 크게 갈린 것이 '쪽을 캐시 폴더에 뽑을
것인가' 였고, 뽑지 않기로 했다. 썸네일을 `filesDir` 에 둔 그 논리가 여기서는 **반대
방향으로** 작용하기 때문이다 — 썸네일은 320px 파생물이지만 만화 쪽은 **원본 그대로**라,
200쪽짜리 열 권을 보면 사용자의 만화가 통째로 앱 저장소에 평문 복제된다. 숨긴 폴더의
책도 예외가 아니고 프로세스가 죽으면 청소할 사람도 없다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 쪽을 **힙에만** 둔다 | 위 문단. `ImageIo.decodeFitted(bytes, …)` 가 이미 같은 판단을 코드에 적어 두었다 |
| solid 는 **창**으로 든다 | 300쪽을 전부 힙에 올리면 수백 MB다. 창은 **실제로 받은 바이트**로 닫는다 — 헤더의 선언 크기는 공격자가 적는 값이다 |
| 창은 요청한 쪽 **한 쪽 앞에서** 시작한다(`LOOK_BEHIND`) | 페이저가 앞뒤 한 장씩을 함께 띄우므로, 요청한 쪽에서 정확히 시작하면 **바로 다음 요청(이전 쪽)이 반드시 창 밖**이 된다(15쪽으로 뛰는 데 패스가 두 번 돌았다). 되돌아가는 중이면 반 창만큼 더 앞에서 시작한다 |
| 패스마다 **예산을 새로** 만든다 | `EntryBudget.entryCount` 는 `resetOutput` 이 되돌리지 않는 **단조 증가** 값이고 리더 생성자가 엔트리마다 `beginEntry()` 를 부른다. 공유하면 300쪽짜리를 40번 다시 열 때 12,000 > 10,000 으로 멀쩡한 만화가 거절된다(재현이 어렵다 — JVM 시험이 박는다) |
| 7z 의 건너뛰는 엔트리 실패는 **그 항목만의** 실패 | 드레인 루프에서 `maxSingleOutput` 이 터지는 것은 그 항목 하나의 상한이지 아카이브를 무효로 만드는 상한이 아니다. 만화 뷰어는 창 밖의 쪽을 전부 그 길로 지나므로, 아니면 **고르지도 않은 엔트리 하나가 패스 전체를 죽인다** |
| 표본을 **바이트**로 자른다 | `sampleFor` 가 2의 거듭제곱이라 4000×3000 한 장이 45.8 MiB(= 예산의 4.6배)가 된다. 사진 한 장이면 살지만 **만화는 그런 장을 넷 든다.** `sampleForBudget` 이 흐려지더라도 바이트를 먼저 지킨다 |
| 웹툰은 **띠**로 그린다 | 800×12000 을 폭에 맞추면 한 장이 55 MiB다. 흐리게 만들어 풀 문제가 아니다 — 웹툰은 **글자를 읽는 그림**이다 |
| 방향은 `reverseLayout` 하나로 | 목록을 뒤집으면 쪽 번호가 자리와 어긋나 이어보기가 다른 쪽을 가리킨다. 방향은 **보이는 순서**의 문제다. 뒤집으면 아래 막대의 슬라이더도 함께 뒤집는다 |
| 표지는 **만화 확장자만** | `ARCHIVE` 전체로 넓히면 사용자가 **연 적도 없는** 압축 파일 속 개인 사진이 320px JPEG 으로 앱 저장소에 영속된다 |
| 되살린 뒤에 `Ready` 를 알린다 | 페이저와 VM 을 양방향으로 이으면 페이저가 0쪽으로 서는 첫 순간의 값이 VM 으로 되돌아와 방금 되살린 쪽을 지운다(DB 읽기와 첫 컴포지션의 경주 — 함정 표 '화면 상태와 ViewModel 을 양방향으로 잇지 마라'). 페이저가 처음부터 옳은 쪽에 서면 경주 자체가 사라진다 |
| 여는 함수의 첫머리에서 **기본값으로 되돌린다** | 액티비티에 묶인 VM 이라 책을 바꿔도 같은 객체다 — 앞 책의 쪽·방향이 새 책에 옮겨 간다(함정 표) |

**시험은 이렇게 나눴다.** `ComicSource` 는 `android.*` 를 하나도 쓰지 않아(디코딩은 `ComicPageStore` 가 한다) **창·엔트리 번호·예산이 전부
JVM 시험**으로 돈다. 플랫폼 디코더가 필요한 것(예산이 표본을 실제로 키우는가, 띠가 제자리를 뜨는가, 캐시가 도는가, 깨진 쪽만 실패하는가)과
Room 을 왕복하는 이어보기·읽는 방향만 계측이다.

### 재생 화면 (10단계)

**큐가 저절로 넘어갈 때는 이어보기를 걸지 않는다**(시작 항목도 0초에서 틀고 저장된 자리는 묻기만 한다 — 아래 '이어보기'). 큐를 만들 때
`recent(500)` 으로 이어보기 지도를 뜨면 기록이 500개를 넘을 때 그 바깥 항목이 **읽은 적 없는 값으로 덮인다** — 읽을 것도 쓸 것도 없게 해 그
결함이 설 자리를 없앴다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 큐 고르기를 **순수 함수로**(`FolderQueue`) | `android.*` 를 쓰지 않아 창·상한·시작 자리가 JVM 시험으로 박힌다. 9단계가 `ComicSource` 를 같은 이유로 갈랐다 |
| 상한을 넘으면 **누른 항목을 가운데 두고** 자른다 | 앞에서 자르면 폴더 뒤쪽 파일을 눌렀을 때 그것이 큐에서 빠지고 **다른 파일이 재생된다**. 8단계가 치명으로 고친 형태다 |
| 자막 인코딩을 **바이트가 들어가는 길에서** 바꾼다 | 아래 문단 |
| 아이콘 아홉을 **직접 그린다**(`PlaybackIcons`) | 코어 세트 49개(aar 를 풀어 셌다)에 `SkipNext`·`Shuffle`·`Repeat`·재생목록·음표·캠코더가 하나도 없다. 5단계가 `Pause` 를 손으로 그린 그 이유다 |
| **재생목록을 화면 본체로 둔다**(`PlaylistPanel`) | 아래 문단 |
| 재생목록에 **길이를 적지 않는다** | 500개의 길이를 알려면 500번 열어야 한다. '목록을 그리려고 파일을 열지 않는다'(`FileKind` 주석)가 이 앱에서 가장 여러 번 값을 한 규칙이다. 길이는 그 곡을 틀면 아래 막대에 나온다 |
| 영상은 **비율 그대로** 그린다 | `fillMaxSize` 로 두면 표면이 화면을 채우느라 영상을 늘인다. 화소가 정사각형이 아닌 영상이 있으므로 `width/height` 가 아니라 `pixelWidthHeightRatio` 를 곱한 값을 쓴다 |
| 섞기를 **우리가 뽑는다**(`ShuffleHistory`) | media3 의 `shuffleModeEnabled` 는 '한 번 섞어 둔 차례' 라 같은 자리에서 다시 눌러도 늘 같은 곡이 나온다. 누를 때마다 새로 뽑되 이력으로 겹침을 막는다. 이력은 **메모리에만** 둔다 — 무엇을 들었는지는 사적인 값이다 |
| 제스처 계산을 **순수 함수로**(`GestureMath`) | 제스처를 캡처로 판정하려다 세 번 틀렸다(함정 표). 계산은 JVM 시험이 답하고, 기기에서는 `Iro.d` 로 연결만 본다 |
| 제스처 인식기를 **하나만** 둔다 | 형제 `pointerInput` 의 순서에 기대지 않는다(함정 표). `awaitEachGesture` 안에서 두드림인지 끌기인지, 끌기면 어느 축인지를 **한 번 정하고 바꾸지 않는다** — 비스듬히 끌 때 탐색과 소리가 번갈아 바뀌는 일이 이 규칙 하나로 사라진다 |
| 조작부를 **칸이 아니라 위에 겹친다** | 칸으로 두면 조작부가 나타날 때마다 위 칸이 좁아져 **영상이 줄어들고 자리가 움직인다.** 겹치면 바뀌는 것은 조작부의 유무 하나뿐이다 |
| 조작부 아래에 **그라데이션 판**을 깐다 | 영상 위에 흰 글자만 얹으면 밝은 장면에서 글자와 단추가 사라진다. 위로 갈수록 옅어져 영상을 통째로 가리지도 않는다 |
| 자막을 **영상 상자 안**에 넣는다 | 밖에 두면 세로 영상 아래의 빈 여백에 자막이 떠서 영상과 글이 멀어진다 |
| 첫 두드림의 토글을 **미룬다** | 바로 토글하면 두 번 두드려 30초를 건너뛸 때마다 조작부가 한 번 깜빡인다. 320ms 안에 두 번째가 오면 미뤄 둔 토글을 취소한다 |
| 두드림 안내를 **900ms 뒤 지운다** | 끌기는 손을 떼면 사라지지만 두드림에는 끝이 없어, 지우는 사람이 없으면 화면에 남는다 |
| **영상이 보일 때만** 어둡게 못 박는다 | `hasVideo && !playlistOpen`. 목록이 열려 있으면 영상은 가려져 있으므로 그때는 OS 테마를 따른다. **액티비티의 테마와 조작부의 색이 같은 식을 써야 한다** — 어긋나면 목록은 밝은데 조작부만 어두운 그림이 나온다 |
| 밝기는 **창 속성만** 바꾼다 | `Settings.System` 에 쓰면 앱을 나가도 기기 밝기가 바뀐 채로 남고 `WRITE_SETTINGS` 권한까지 필요하다. 소리는 반대로 **시스템 음량**을 바꾼다 — 재생 화면에서 줄인 것이 볼륨 키와 같은 자리여야 한다 |
| 날짜 묶기를 **순수 함수로**(`DayBucket`) | 시간대·서머타임·해 경계는 화면 캡처로 확인할 수 있는 것이 아니다. 시간대를 인자로 받으므로 서울의 9월도 상파울루의 11월(자정이 없는 날)도 같은 시험에서 답이 난다 |
| 날짜 구간을 **자리 번호로** 들고 있는다 | 항목을 날짜별 목록으로 다시 담으면 화면이 누른 칸의 **원래 자리**를 잃어 다른 사진이 열린다. 아카이브 엔트리를 이름이 아니라 인덱스로 가리키는 것과 같은 판단이다 |
| 구간을 **훑기 쪽에서** 만든다 | `GalleryScanner.scan` 은 `IroDispatchers.io` 위에서 돌고 화면은 주 스레드다. 1만 장의 시각을 주 스레드에서 날짜로 바꾸면 격자가 그려지기 전에 한 번 멎는다 |
| 기본 볼륨의 이름만 **우리가 붙인다**(`내부 저장소`) | `StorageVolume.getDescription` 이 주는 `Internal shared storage` 는 한국어 화면에서 그 한 줄만 영어로 길다. SD 카드는 그대로 둔다 — 그 이름은 기기가 아는 것이고 우리가 더 잘 지을 수 없다 |
| 이음매에 **인텐트를 주는 함수**를 더한다 | 알림은 '지금 열어라' 가 아니라 `PendingIntent` 를 미리 받아 둔다. 그래서 `PlayerLauncher` 가 `open` 에 더해 `intent(context, showPlaylist)` 를 갖는다(세션의 `setSessionActivity`). 인텐트를 조립하는 일과 `launchMode="singleTask"` 선언은 한 몸이라 둘 다 `feature:player` 에 남는다 |
| 큐가 없으면 목록을 **'열린' 것으로 치지 않는다** | 한 곡짜리에서는 그릴 목록이 없어 `PlaylistPanel` 이 안 그려지는데, 그래도 '열림' 으로 치면 테마만 밝아져 **영상이 흰 바탕 위에 뜬다** |
| 목록의 여닫음은 **사용자의 뜻이 이긴다** | `hasVideo` 를 `remember` 의 키로 쓰면, 소리와 영상이 섞인 큐에서 곡이 넘어갈 때마다 닫아 둔 목록이 다시 열린다. '손대기 전에는 기본값, 한 번 손대면 그 뜻' 으로 갈랐다 |
| 줄의 **종류를 큐가 정해 실어 보낸다**(`QueueItem.kind`) | 화면이 제목의 확장자를 다시 보게 하면 판정이 두 곳으로 갈리고 보이는 줄마다 되풀이된다. 큐는 항목이 실제로 바뀔 때만 다시 만들어진다. 재생목록의 캠코더·음표도 이것으로 가른다 |
| 진행 바를 **직접 그린다** | Material 3 기본 트랙은 16dp 알약에 끝점 표시가 붙는데, 재생기에는 없는 기호라 '뭔가 더 있다' 로 읽힌다. 관행은 가는 트랙 + 둥근 점이다. 채움은 `Canvas` 가 아니라 `Box` + `fillMaxWidth(fraction)` 로 둔다 — 정렬이 RTL 을 대신 처리한다. 앱의 모든 슬라이더·진행 표시가 이 모양이다(`core:ui` 의 `IroSlider`·`IroProgress` — 14단계 뒤 절) |
| 조작을 **두 줄로 가른다** | 위는 '큐에 대한 조작'(목록·섞기·반복·이전/재생/다음), 아래는 '지금 이 재생에 대한 조작'(배속·A-B·트랙·방향 잠금·PiP). 한 줄에 몰면 작은 폰에서 아홉이 넘어 겹치거나 잘린다. 판 높이는 `onSizeChanged` 로 재고 있으므로 줄이 늘어도 위에 뜨는 안내들이 저절로 따라 올라간다 |
| 배속을 **눈금으로** 준다(`SpeedSteps`) | 배속은 되돌아올 수 있어야 하는 값인데 슬라이더로 두면 1.0 배로 정확히 돌아오는 일이 손가락에 달린다. 단추에 **지금 값을 늘 적어** 잊지 않게 한다 |
| 배속 눈금을 **조작부 안에서** 펼친다 | 떠 있는 층으로 두면 가로 영상에서 남는 높이가 절반뿐이라 아래 눈금이 잘린다. 안에 두면 판이 그만큼 높아지고 잰 높이가 나머지를 알아서 민다 |
| A-B 를 **클리핑이 아니라 되감기로** | `MediaItem.ClippingConfiguration` 은 `replaceMediaItem` 으로 걸리지 않고(함정 표), 큐를 다시 세워 걸면 `getCurrentPosition`·`getDuration` 이 **클립 기준**으로 바뀌어 진행 바·시간 표시·`PositionSaver` 가 전부 다른 시간축을 본다. 되감기는 시간축이 하나로 남는다 |
| 짧은 구간을 **거절하고 말한다** | 'A 를 그 자리로 옮겨 준다' 는 길은 옮김의 크기가 정의상 1초 미만이라 **진행 바에서도 시간 문구에서도 보이지 않는다.** 사용자는 자기가 찍은 자리가 왜 달라졌는지 모른다 |
| 구간 이탈을 **위치로** 판정한다 | 알림·잠금화면·블루투스의 탐색은 우리 함수를 지나지 않고 세션으로 바로 들어온다. 호출부에 표시를 다는 방식은 그 길을 통째로 놓친다 |
| 멈춰 있으면 A-B 티커를 **조이지 않는다** | 멈춘 채 B 근처에 서면 위치가 늘지 않아 50ms 간격이 영원히 돈다 |
| 트랙 목록을 **우리 id 로** 내보낸다 | 세션은 컨트롤러로 나가는 `TrackGroup` 마다 매번 새 id 를 매기고 돌아온 오버라이드를 그 표로 되돌린다. 예전 그룹으로 만든 오버라이드는 짝을 못 찾아 **조용히 무시된다** — 오버라이드는 언제나 방금 받은 `getCurrentTracks()` 의 그룹으로 만든다 |
| 트랙 이름을 **조각으로** 넘긴다(`TrackLabels`) | `Format` 은 media3 타입이라 JVM 시험에서 만들 수 없고(스텁), 문구를 계산 쪽에 박으면 '문구 두 벌' 이 된다. 조각(`Descriptor`)만 만들고 한 줄로 붙이는 일은 문자열 자원이 한다 |
| 자막 시트를 **바텀시트로** | 조작부 안에 붙이면 가로 영상에서 줄이 넷만 넘어도 아래가 잘린다. 시트를 `if (!showControls) return@Box` **앞**에 두는 것이 중요하다 — 뒤에 두면 조작부를 감춘 순간 시트가 컴포지션에서 빠져 **되돌릴 수 없는 화면**이 된다 |
| 트랙 선택을 **`mediaId` 가 바뀔 때만** 되돌린다 | 전환 이유로 가르면 한 곡 반복(`REASON_REPEAT`)과 자막 교체(`PLAYLIST_CHANGED`)에서도 돌아, **한 바퀴마다 그리고 자막을 고를 때마다** 사용자가 고른 소리 트랙이 지워진다 |
| 큐를 다시 세울 때 재생 여부는 `playWhenReady` 로 잰다 | `isPlaying` 은 버퍼링·오디오 포커스 억제 중에는 거짓이라 그 순간 자막을 고르면 재생이 멈춘 채 돌아오지 않는다 |
| 항목 전환 중의 공백을 **큐가 말하는 종류**로 메운다 | `hasVideo` 는 항목을 건너뛰는 동안 한 프레임 거짓이 되는데 테마·표면·방향 요청·목록 기본값이 **모두** 그것을 읽는다. `push()` 에서 트랙을 아직 모를 때 큐의 종류로 메우는 한 자리가 소비자 넷을 각각 고치는 것보다 맞다 |
| PiP 는 **자동과 단추 둘 다** | 자동 진입은 '계속 보려고 나간다' 는 추측이라 `isPlaying` 을 조건에 넣고, 단추는 명시라 넣지 않는다. 목록이 펼쳐져 있으면 둘 다 들어가지 않는다 — 그 상태에서는 표면이 컴포지션에 없어 **영상 없는 창**이 뜬다. 그 판정이 컴포지션 안에 있으면 목록이 열릴 때 함께 사라지므로 목록 여닫음은 **액티비티가 든다** |
| PiP 에서 큐가 소리로 넘어가면 **창을 닫는다** | 판정을 `hasVideo` 로 하지 않는다 — PiP 의 '다음' 을 눌러 영상 → 영상으로 넘기기만 해도 창이 사라진다. 큐가 실어 보내는 종류(`State.currentKind`)는 **항목이 실제로 바뀔 때만** 달라진다 |
| '들어가려 한다' 와 '들어가 있다' 를 가른다 | 그리기용 낙관값(`pipCollapsing`)과 확인된 상태를 한 값이 겸하면 진입이 거절된 순간에도 창 판정이 참이 된다 |
| PiP 진입 **전에** 위층을 접는다 | `onPictureInPictureModeChanged` 는 애니메이션이 끝난 뒤 오고 시작 시점 콜백은 API 35 다. minSdk 31 에서 첫 프레임에 조작부가 찍히는 것을 막을 방법은 우리가 먼저 접는 것뿐이다. 진입이 거절된 길은 `onResume` 이 편다 — **되돌리는 코드가 없으면 조작부도 제스처도 없는 화면이 남는다** |

**재생목록이 보여야 한다 — 사용자가 VLC 를 들어 지적한 것이다.** '만들어 놓고 차례로 트는 것' 뿐이면 다음/이전으로만 움직일 수 있고,
18곡짜리 폴더에서 16번째를 들으려면 열다섯 번을 눌러야 한다. VLC 를 비롯한 재생기들이 **재생목록을 화면 본체로 두는** 이유가 그것이다 —
목록이 곧 이동 수단이다. 아무 줄이나 누르면 그 항목으로 건너뛰고(`playAt`), 재생 중인 줄은 **막대 아이콘 + 굵은 글씨**로 갈린다(제목은
대개 끝이 잘리므로 굵게만으로는 구분이 안 된다). 목록을 열면 지금 곡이 보이는 자리로 스크롤한다.

**▶ 는 '이 폴더를 튼다' 가 아니라 '이 폴더를 재생목록으로 연다' 로 정의했다** — 누르면 재생 화면이 목록을 펼친 채로 열린다. 그 의도는
이음매(`PlayerLauncher.open(context, showPlaylist)`)로 실어 보낸다. 파일 하나를 탭해 들어온 길은 false 다(그 사람이 고른 것은 목록이 아니라
그 파일이다 — 다만 소리 파일은 재생 화면이 목록을 펼친 채 열린다). `launchMode="singleTask"` 라 두 번째부터는 `onNewIntent` 로 오므로
`intent` 필드를 그대로 읽으면 **처음 값이 영영 남는다** — 거기서 갱신한다.

**자막 인코딩 — media3 에 구멍이 있다.** `MediaItem.SubtitleConfiguration.Builder` 가 받는
것은 `setUri`·`setMimeType`·`setLanguage`·`setSelectionFlags`·`setRoleFlags`·`setLabel`·
`setId` 뿐이고(javap 로 확인), `SubripParser` 가 쓰는 `ParsableByteArray` 는
`readUtfCharsetFromBom()` — **BOM 이 없으면 UTF-8 로 단정한다.** 한국어 자막은 CP949,
일본어 자막은 Shift_JIS 가 흔하고 둘 다 BOM 이 없다. 그래서 `DataSource` 를 한 겹 감쌌다(`SubtitleSource.kt`) — 자막 확장자면 통째로 읽어
판정하고 UTF-8 로 바꾼 바이트를 대신 내놓는다. 파서는 BOM 없는 UTF-8 을 보므로 아무것도
바꿀 필요가 없다. **통째로 메모리에 올리는 것이 요점이다**: 변환하면 길이가 달라지는데
(CP949 2바이트 → UTF-8 3바이트) media3 는 변환본 기준으로 구간을 물어본다. TTML 은 태우지 않는다(함정 표).

**이어보기 — '처음부터 틀고 물어보기'.** 언제나 0초부터 틀고 `resumeOfferMs` 로 **묻기만** 한다(3초). 저장된 위치로 먼저 뛰고 '처음부터' 로
무르게 하면 재생 화면으로 곧장 들어가는 영상에는 무를 수단이 없다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 시작 위치의 예외가 없다 | 기본이 0초라 '무를 방법이 없다' 는 위험이 사라져 규칙이 한 줄이다 |
| 3초를 **화면이** 잰다 | 커넥션이 타이머를 들면 화면이 없는 동안에도 3초가 흘러, 미니 바로 듣다가 재생 화면을 열면 이미 지워져 있다 |
| **지나온 제안은 버린다** | 소리 파일은 제안이 아무에게도 안 보인 채 남을 수 있다. 한참 뒤 들어가면 그 단추는 **뒤로 가는** 단추가 된다. `push()` 가 위치를 지날 때 버린다 |
| 항목이 바뀌면 버린다 | 다음 곡으로 넘어간 뒤에도 남아 있으면 **다른 파일의 위치로 뛰는** 단추가 된다. `PLAYLIST_CHANGED` 만 빼는 것은 그것이 우리가 방금 큐를 갈아 끼운 것이기 때문이다. `stop()` 도 지운다 |
| 기록은 **막지 않는다** | 제안이 살아 있는 동안 저장을 막으면 소리 파일에는 제안을 지울 화면이 없어 **세션 내내 기록이 얼어붙는다.** 기록은 언제나 '지금 어디까지 봤는가' 여야 한다 |
| 안내는 파일 목록에 **띄우지 않는다** | 파일 목록은 재생을 보는 화면이 아니라 그 안내를 소비할 사람이 없다. 동작 단추를 붙인 스낵바는 기간이 무기한이 된다(함정 표) |

**대가를 적어 둔다.** 3초 안에 답하지 않으면 옛 자리는 10초 뒤 새 진행으로 덮인다
(`PositionSaver` 의 티커). '언제나 처음부터 튼다' 를 고르면 따라오는 결과다.

**갤러리.** 사진을 열고 뒤로 오면 갤러리로 돌아온다 — `showGallery` 는 ViewModel + `SavedStateHandle` 에 둔다(`rememberSaveable` 은 화면 전환으로
컴포지션에서 빠지는 것을 견디지 못한다 — 함정 표). 파일 작업이 끝나면 갤러리도 다시 훑는다(`onOperationFinished` — `WhileSubscribed(5_000)`
라 5초 안에 돌아오면 지우기 전의 목록을 그대로 내놓았다). **다시 훑는 동안 격자를 비우지 않는다** — `GalleryScanner.scan` 은 언제나
`Scanning(0)` 으로 시작하므로, 마지막 `Ready` 를 **flow 바깥의 ViewModel 필드**로 들고 그 사이를 메운다(flow 안에 두면 `WhileSubscribed` 가
상류를 끊을 때 함께 사라진다).

**앱 이름과 아이콘**(사용자). 이름은 `Files`, 아이콘에서 재생 삼각형을 뺐다 — 아이콘은 설명이 아니라 찾는 표지라, 익숙한 모양 하나가 섞인
모양보다 낫다. 배경 `#4A8BF0`. **어댑티브 아이콘은 108×108 화폭 가운데 72×72 만 보인다** — 그 원을 꽉 채우면 런처에서 혼자 커 보인다.
크기는 앱 서랍에서 구글 Files 와 나란히 찍어 픽셀로 쟀다: 폭 60.5% · 높이 48.4% · 원 대비 면적 33.8%(구글 58.6% · 49.7% · 33.9%).

**만화의 읽는 방향**(사용자) — 왼→오 · 오→왼 · 세로 스크롤 셋을 다 고를 수 있고, **새 책의 기본은 왼쪽에서 오른쪽**이다.

**자막·영상 표본을 ffmpeg 없이 만든다** — 내장 자막이 든 mkv 는 EBML 을 손으로 쓴다(PCM 오디오 `A_PCM/INT/LIT` + 자막 `S_TEXT/UTF8` 이면 인코더
없이 유효한 Matroska 다). 자막은 조각마다 다른 글자로 만들어 캡처에서 글을 읽어 판정한다. 그런 도구는 저장소에 넣지 않는다.

### PDF 뷰어 (11단계)

**문서 뷰어는 만화 뷰어의 구조를 그대로 가져오되 셋이 다르다.**

| | 만화 뷰어(9단계) | 문서 뷰어(11단계) |
|---|---|---|
| 쪽의 출처 | 아카이브 안의 **바이트**(경로가 없다) | 파일 하나 안의 **쪽 번호** |
| 방향 | 왼→오 · 오→왼 · 세로 스크롤 | **왼→오 하나뿐** |
| 확대 | 원본 화소를 다시 읽는다(`BitmapRegionDecoder`) | **그 자리를 다시 그린다**(타일) |

세 번째가 PDF 가 벡터라서 얻는 이득이다 — PDF 는 **더 큰 배율로 다시 그리면** 된다. 방향을 고르게 하지 않은 것은 PDF 에 읽는 방향이
적혀 있지 않아서다. 세로쓰기 문서 몇 편을 위해 설정을 하나 더 만들지 않는다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 쪽 크기 계산을 **순수 함수로**(`PdfLimits`) | 값이 틀리면 결과가 `OutOfMemoryError` 이거나 `Canvas` 의 거부다. 그것을 에뮬레이터에서 재현해 고치는 일은 느리고 불확실하다 — `ImageLimits` 가 같은 이유로 순수 함수다 |
| 배율을 **연속값으로** | `ImageLimits.sampleForBudget` 은 2의 거듭제곱이라 최대 4배 흐려진다. PDF 는 그 손해를 볼 이유가 없다 |
| 폭·높이를 **내림(floor)** | `floor(w·s)·floor(h·s) ≤ w·h·s²` 가 언제나 성립해 반올림이 상한을 넘기는 경우가 구조적으로 없다. 올림으로 적으면 시험이 통과해도 실제로는 몇 바이트씩 넘는다 |
| 상한을 `min(예산, 100MiB)` 로 | 100MiB 비트맵을 `PdfRenderer` 는 군말 없이 채워 주는데 그 다음의 `Canvas` 가 API 31 에서 거부한다 |
| 쪽 크기 상한을 **두지 않는다** | 14400pt(200인치)짜리 쪽은 명세가 허락하는 정상 쪽이고, 그리는 문제는 배율이 이미 푼다. **확인하지 못한 명세 상한을 상수로 박지 않는다** — ZIP 엔트리 수 상계가 멀쩡한 파일을 거절한 그 형태다 |
| 선명화를 **띠가 아니라 타일로** | PDF 에는 디코딩할 원본이 없다. 대신 더 큰 배율로 다시 그린다 |
| 타일에 **쪽 크기를 실어 보낸다**(`Tile.pageWidth`) | 화면이 타일을 얹으려면 쪽 안의 비율이 필요한데, 그러려면 `floor(폭pt × 배율)` 을 호출자가 한 번 더 적어야 한다. 내림을 반올림으로 다시 적는 순간 타일이 반 화소씩 어긋난다 — **계산을 두 벌로 두지 않는다** |
| 요청을 **눈금으로 끊는다**(`DocDetail`) | `snapshotFlow` 는 손가락이 1화소 움직일 때마다 다른 값을 낸다. pdfium 의 잠금이 **프로세스 전역**이라 그 요청들이 줄을 서고, 줄이 밀리는 만큼 화면이 늦게 따라온다. 배율 1/100 · 자리 1/200 이면 눈에 보이지 않으면서 요청 수가 수십 분의 일이 된다 |
| 층을 **`ZoomableImage` 안에서** 겹친다 | 확대·이동 변환을 `ZoomState` 한 곳에서만 읽어야 층이 교체되는 프레임에 그림이 튀지 않는다. 층마다 자기 변환을 들면 반드시 어긋난다 |
| 창(window)과 미리 읽기를 **두지 않는다** | 9단계의 창은 solid 아카이브의 정의 때문에 생긴 장치다. `PdfRenderer` 는 쪽 무작위 접근이 싸다(아래 렌더 시간). **없어도 되는 상태를 만들면 그 상태가 틀리는 날이 온다** |
| 닫기를 **렌더 디스패처에 얹는다** | `close()` 는 `AutoCloseable` 이라 `suspend` 가 아닌데 렌더는 코루틴 안에서 돈다. 병렬도 1 인 디스패처에 얹으면 진행 중인 렌더 뒤에 줄을 서고, 새 렌더는 닫힘 깃발이 막는다 |
| 축출해도 **`recycle()` 하지 않는다** | recycle 된 비트맵에 `render` 하면 예외가 아니라 **`SIGABRT` 로 프로세스가 즉사한다**(함정 표) |
| 배경은 **어두운 회색**, 쪽은 희다 | 만화는 검은 배경에 그림을 얹지만 문서는 흰 종이다. 검정 위의 흰 종이는 대비가 너무 세고, 어두운 모드라고 쪽을 반전하면 그림과 표의 색이 깨진다 |
| 슬라이더에 **눈금을 두지 않는다** | 만화는 쪽이 수십이지만 문서는 2,000쪽이 드물지 않고, 그만큼의 눈금 표시는 트랙을 회색 띠로 만든다 |
| `Documents.open` 은 **소유권을 콜백으로** 넘긴다 | `withTimeout` 안에서 만든 문서를 반환하면 그 사이 취소될 때 코루틴이 값을 버려 pdfium 문서와 파일 서술자가 GC 를 기다린다. `onOpen` 콜백 + 호출자의 `finally` 로 갈랐다(함정 표 '코루틴 경계 너머로 `Closeable` 을 돌려주지 마라') |

**렌더 시간.** 쪽마다 비트맵을 새로 만들고 흰색으로 지운 뒤 그리는 값 전부다.

| 쪽 | 비트맵 | 시간 |
|---|---|---|
| A4 세로(595×842pt) | 1080×1528 | **3~41 ms** |
| A4 가로(842×595pt) | 1080×763 | 3~10 ms |
| 200인치(14400×14400pt) | 1080×1080 | **4 ms** |
| 선명화 타일(배율 3.63) | 998×2218 | — |

**solid 7z 의 1.4~2.1초와 견주면 세 자리 다른 값이다.** 창과 미리 읽기를 두지 않기로 한 근거가 이 표다.

### EPUB 과 문서를 안전하게 그리는 기반 (11단계)

**EPUB 은 HTML 이다. 그래서 이 단계가 실제로 만든 것은 EPUB 뷰어가 아니라 '문서를 안전하게 그리는 기반' 이다** — 그 기반을 EPUB 과
글 뷰어의 마크다운 미리보기(14단계)가 함께 쓴다. 모듈이 셋으로 갈린 것이 그 사실을 그대로 옮긴 것이다.

| 모듈 | 무엇 | 왜 여기인가 |
|---|---|---|
| `format:html` | 위생(HTML·CSS)과 읽기용 껍데기(`HtmlShell`) | 여럿이 공유한다(`format:epub`·`format:text`). 위생 규칙이 두 벌이 되면 한쪽만 고쳐지는 날이 온다 |
| `format:epub` | 컨테이너·OPF·목차·암호화 판정 | ZIP + XML 이라 순수 JVM 이다. 그래서 깨진 표본을 초 단위로 시험한다 |
| `core:webhost` | 잠긴 WebView 와 가짜 출처 | **포맷을 모른다.** 그래야 EPUB·글의 미리보기·SVG(`feature:docview`·`feature:text`·`feature:image`)가 같은 호스트를 쓴다 |

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 본문을 **가짜 출처**로 띄운다(`https://book.iroiro.invalid/…`) | `loadData` 는 출처가 null 이라 상대 주소가 풀리지 않고, `loadDataWithBaseURL` 은 장을 넘길 때마다 기준을 우리가 관리해야 한다. 우리 호스트의 URL 로 열면 **상대 주소를 WebView 가 푼다** — 그쪽이 우리보다 정확하고, 경로 계산이 한 벌로 끝난다 |
| `.invalid` 를 쓴다 | RFC 2606 이 **절대 등록되지 않도록 예약한** 최상위 도메인이다. 가로채기가 한 번 빗나가도 그 요청이 닿을 곳이 정의상 없다 |
| 위생기가 **DOM 을 만들지 않는다** | 저장소 규칙이기도 하고 안전 문제이기도 하다 — 임의의 사용자 파일은 수만 겹으로 중첩될 수 있고, 트리를 만들면 그 깊이가 그대로 스택이다. 잘못 닫힌 태그를 고쳐 주지도 않는다(그 일은 WebView 의 파서가 우리보다 잘한다) |
| 스킴 판정을 **한 곳**에 둔다(`Urls`) | 리졸버에게 맡기면 리졸버를 쓰는 쪽마다 검사를 한 벌씩 다시 써야 한다. 한 곳이 `data:text/html` 을 빠뜨리는 날 그 문서는 창 안에서 새 문서를 연다 |
| `on…` 으로 시작하는 속성을 **예외 없이** 지운다 | 목록을 적어 두면 목록에 없는 새 이벤트가 그대로 지나간다 |
| 내용까지 버리는 것과 태그만 지우는 것을 **가른다** | `<script>`·`<iframe>`·`<object>` 는 내용까지 버린다. `<form>`·`<video>`·`<noscript>` 는 태그만 지운다 — 그 안의 글은 **읽는 사람이 보려던 글**이다 |
| CSS 를 **이해하려 들지 않는다** | 막을 것은 셋뿐이고(`@import`·바깥 `url(…)`·옛 스크립트 문법) 셋 다 글자로 찾을 수 있다. 글꼴·여백·색은 그대로 둔다 — 안전하게 만든다고 조판을 지우면 남는 것은 '지켜진 문서' 가 아니라 **망가진 문서**다 |
| 바깥 `url(…)` 을 **`none` 으로 바꾼다** | 선언을 지우면 중괄호 균형이 깨져 뒤의 규칙이 함께 무너지고, 빈 `url()` 로 두면 WebView 가 그 쪽을 다시 가져오려 든다 |
| **스타일시트 파일도 위생을 거친다**(`EpubBook.styleSheet`) | 장 안의 `<style>` 만 걸러서는 구멍이 남는다 — `<link rel="stylesheet">` 로 걸린 파일이 그 길로 들어온다. 위생의 구멍은 한 군데면 충분히 뚫린다 |
| 상대 주소를 **그대로 남긴다** | 우리가 절대 주소로 바꾸면 경로 계산이 두 벌(우리 것과 WebView 것)이 되고, 둘이 어긋나는 날 그림이 조용히 사라진다. 대신 위생기에게 **'이 주소가 책 안에 있는가' 를 묻는다** |
| 목차를 **평평하게** 만든다 | 트리를 들면 화면에 펼침 상태가 생기고, 그 상태는 쪽을 옮길 때마다 맞춰 줘야 하는 또 하나의 상태다. 깊이는 들여쓰기로만 쓴다 |
| `linear="no"` 를 차례에서 뺀다 | 본문 차례 밖(광고·판권)이다. 넣으면 사용자가 읽는 순서가 책이 말한 순서와 달라진다 |
| `mimetype` 을 **거절의 근거로 쓰지 않는다** | 명세는 압축하지 말라고 하지만 실물에는 어긴 파일이 흔하다. 거절하면 멀쩡히 읽히는 책을 '깨졌다' 고 말하게 된다 — ZIP 엔트리 수 상계로 겪은 형태다. 경고만 남긴다(판별기의 손떼기는 함정 표 'HWPX 는 EPUB 처럼 생겼다') |
| `encryption.xml` 을 **읽어서** 가른다 | 같은 파일이 DRM 일 수도 **글꼴 난독화**일 수도 있다. 후자를 DRM 으로 보면 멀쩡한 상업 EPUB 을 통째로 거절한다. 본문이 걸렸거나 **무엇이 걸렸는지 모르면** 거절하고, 난독화면 푼다 |
| 같은 이름의 엔트리를 **경고로 남긴다** | ZIP 명세가 금지하지 않으므로 깨진 파일이 아니라 **있을 수 있는 파일**이다. 조용히 고르면 사용자가 보는 그림이 왜 그것인지 아무도 모른다 |
| 본문을 **들고 있지 않는다** | 300쪽짜리 책의 XHTML 을 전부 펴 두면 그것만으로 수십 MB다. 보고 있는 장 하나만 그때그때 읽는다 |
| 자원을 **바이트로 만들어** 넘긴다 | `shouldInterceptRequest` 는 다른 스레드에서 온다. ZIP 리더를 그 스레드가 물고 있으면 책이 닫히는 순간과 겹친다. 읽는 동안만 잠그고 상한(32 MiB) 안에서 복사한다 |
| EPUB 은 **겹치지 않고 칸으로 나눈다** | 나머지 뷰어는 조작부를 그림 위에 겹치지만 글을 읽는 화면에서는 반대다 — 겹치면 **글자가 막대 밑으로 들어가 읽히지 않는다.** 그리고 WebView 는 탭을 스스로 쓰므로(글자 선택·링크) 두드림으로 조작부를 여닫을 길이 없다 |
| 세는 단위가 **쪽이 아니라 장** | 장 하나의 길이는 글의 길이가 정한다. 그것을 화면 크기로 잘라 쪽을 만들면 **글꼴 크기가 바뀔 때마다 쪽 번호가 달라지고**, 이어보기가 가리키는 자리가 그때마다 움직인다 |
| 버린 것을 **말한다**(`DocNoticeList`) | `UnsupportedFeatures` 가 2단계부터 적어 둔 계약이다 — "사용자는 원문에 무엇이 있었는지 모르므로, 빠진 것이 있다는 사실 자체를 앱이 말해 주어야 한다" |

**네 번째 이음매**(`DocumentSupport`). 문서 화면이 여는 포맷을 직접 고르면 `feature:*` 이 포맷 모듈을 여럿 보게 되어 의존 표를 어긴다.
무엇을 무엇으로 여는지는 `app/FormatRegistry` 가 알고(지금은 EPUB·PDF — 판별기 둘·여는이 둘), 그것이 `FormatProbe`·`ProbeContext` 를 부른다.

### 암호 PDF · 원본 2배 확대 · 글꼴 난독화

**암호 PDF 를 세운 방식.**

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 복호화기를 **직접 쓴다**(`docview/pdf/crypt/`, 순수 코틀린) | API 34 이하의 `PdfRenderer` 는 암호를 받지 않는다. PdfBox-Android 는 채택하지 않기로 한 목록에 있다 |
| 암호 PDF 를 **평문 PDF 로 다시 쓴다** | pdfium 은 우리 복호화 함수를 부를 수 없다. 문자열·스트림만 풀고 나머지는 글자 그대로 옮긴 뒤 `/Encrypt` 를 빼고 xref 를 새로 쓴다. 스트림은 **압축된 채로** 옮긴다(풀면 메모리 파일이 몇 배로 부푼다) |
| 쓰는 곳은 **memfd** | 평문을 저장소에 쓰지 않는다(아래 '암호가 걸린 파일'). `Os.memfd_create` 는 API 30 부터이고 앱 샌드박스(untrusted_app)에서 돈다(계측 시험 `메모리_파일_경로가_이_기기에서_돈다`). 렌더 디스패처에 줄 선 채 취소되면 블록이 돌지 않으므로, 받지 않은 서술자는 닫는다 |
| API 35 는 **플랫폼 먼저** | `PdfRenderer(pfd, LoadParams)` — 평문 사본이 없다. 거절하면 우리 것으로 한 번 더(후보가 더 많다). 그 뒤의 실패는 '틀렸다' 다 |
| `/Length` 는 **열 자리 0 으로 채워 쓰고 나중에 고친다** | AES 는 채움을 떼기 전까지 평문 길이를 모른다. 자리 채움은 **사전의 맨 끝**에 둔다 — 16진 문자열이 0 바이트 다섯을 품으면 같은 글자가 되어 엉뚱한 곳을 고친다 |
| xref 는 **쓴 번호만** 소구역으로 | 빽빽하게 쓰면 번호 하나를 크게 적은 파일이 표를 수백 MB 로 부풀린다(168 MB 를 본 표본이 있다) |
| 명세대로 읽는다 — pdfium 처럼 | V4 에 `/Length` 가 없으면 128(기본 40비트로 읽으면 맞는 암호가 '틀렸다'), `stream` 뒤의 공백은 줄 끝까지 건너뛴다(데이터로 읽으면 AES 의 IV 가 통째로 어긋난다), xref 가 밀린 파일은 머리 번호를 대조해 어긋나면 다시 세운다, xref 가 아는 번호는 옛 객체 스트림에서 되살리지 않는다 |
| 적대적 입력에 상한 | 예측자의 폭은 실제 데이터로 누른다(몇 바이트가 128 MB 가 됐다). 다시 세우기는 한 번, 1 MiB 마다 취소 확인, PDF 머리가 없으면 훑지 않는다 |

**시험의 오라클이 우리가 아니다.** 표본(합계 61,935 바이트)을 **pypdf 6.17**(순수 파이썬)과
**qpdf 12.3.2**(pikepdf 경유)가 잠갔다 — R2·R3·R4(RC4·AES)·R5·R6, 객체 스트림, 선형화,
메타데이터 평문, 소유자 암호만, 한글 암호. 두 도구가 서로의 표본을 평문과 같게 푸는 것을
먼저 확인했고(`fix_metadata_version` 함정은 그때 잡혔다), 우리 결과를 **qpdf 와 엄격한 pypdf 가
다시 읽어** 경고 0 을 확인했다.

**원본 2배 확대.** 최대 배율 = `2 × 원본 폭 / 맞춤 폭`(`ZoomMath.maxScale`). 원본의 뜻은 셋이다 —
사진은 **디코딩 전 원본의 화소**(`ImageIo.probe`), 만화는 쪽의 치수(`PageInfo.displayWidth`),
PDF 는 **종이의 실제 크기**(`폭pt × densityDpi / 72`, 데스크톱 뷰어의 '100%'). 작은 그림은
맞춤만으로 이미 원본의 2배를 넘으므로 **맞춤의 2배를 하한으로 남겼다**(두 번 두드려도 아무 일이
없으면 고장으로 읽힌다) — 사용자 요청을 그 그림들에서만 넘는 자리다.

키운 만큼 선명해야 뜻이 있으므로 **사진·만화에 영역 디코딩**을 붙였다(`RasterDetail`·`rememberRasterDetail`). 조각은 바닥층과 함께
**부동소수로** 놓는다 — `IntOffset` 으로 잘라 확대 변환 안에서 그리면 1화소 미만의 오차가 배율만큼 커진다(12000화소 파노라마에서 18화소).
정방향은 잴 것이 없으므로 정사각 검사보다 먼저 참이다. PDF 는 타일의 상한이 **화면이 허락하는 최대 확대**다(`PdfLimits.tileFor(maxScale)`).

**글꼴 난독화를 푼다**(`FontObfuscation`). IDPF 는 EPUB 3.3 명세 4.4 를 그대로 옮겼고, Adobe 는
명세가 아니라 Readium 구현을 읽어 확인했다. 오라클은 **Readium 이 만든 난독화 글꼴**이다 —
제3자의 글꼴이라 커밋하지 않고 `samples-local/epub-deobfuscation/` 에 둔다. 열쇠를 못 만드는 난독화 **본문**은 잠긴 책으로 보고,
`EncryptedData` 가 없는 파일은 연다. 식별자 끝은 XML 공백 넷만 벗긴다(NBSP 를 벗기면 열쇠가 틀린다). ZIP 암호가 걸린 EPUB 은
'다루지 않는다' 로 말한다(공개된 방식이라 '암호로는 열 수 없다' 는 거짓이다).

### 아카이브 암호

| 무엇 | 왜 그렇게 했나 |
|---|---|
| ZIP 복호화를 **직접 쓴다**(`ZipDecryption`) | commons-compress 는 암호 ZIP 을 읽지 못한다(`UnsupportedZipFeatureException`). 전통 암호(APPNOTE 6.1)와 WinZip AES(AE-1·AE-2) 둘 — 명세가 짧고 검증 벡터가 있다(PBKDF2 는 RFC 6070). 전통 암호는 끝의 CRC, AES 는 HMAC-SHA1 인증값으로 확인한다. 확인 바이트는 로컬 헤더의 날것 DOS 시각에서 읽고, 후보는 아카이브마다 한 번 정한다(함정 표) |
| 7z·RAR 은 **라이브러리에 암호를 넘긴다** | commons-compress `SevenZFile.Builder.setPassword(char[])`, junrar `Archive(File, String)`. 7z 의 판정과 가리기는 함정 표 |
| 암호 후보를 **셋** 시도한다 | 함정 표 — 반디집은 한글 암호를 CP949 로 넣는다 |
| 넣은 암호를 **확인하고 나서** 기억한다(`Archives.verifyPassword`) | 가장 작은 잠긴 항목 하나를 끝까지 읽어 본다. 확인 없이 기억하면 틀린 암호가 풀기·미리보기까지 흘러가 '깨진 파일' 로 끝난다. **RAR 은 `extractFile` 로 확인한다** — 읽는 스트림은 실패를 삼켜 어떤 암호든 '맞다' 가 된다(함정 표). 확인은 암호의 신호만 '틀렸다' 로 읽는다(`PasswordCheck` — 큰 항목·안쪽 압축 방식의 `IOException` 을 '틀렸다' 로 읽으면 맞는 암호를 영원히 거절한다). solid 는 맨 앞 항목을 16 MiB 까지만 푼다 |
| 암호로도 못 여는 항목을 가른다(`ArchiveEntry.lockedForGood`·`needsPassword`) | 강한 암호화·AES+LZMA 처럼 암호를 넣어도 못 여는 항목에 암호를 물으면 무엇을 넣어도 '잠김' 이 남는 고리가 된다. 배너·만화의 '암호 넣기' 는 `needsPassword` 만 본다 |
| 헤더까지 잠긴 RAR 의 예외를 **우리 예외로 옮긴다** | `RarException` 은 검사 예외라 호출부를 모두 지나 앱을 죽인다(함정 표). 리더가 `WrongPasswordException` → `ArchivePasswordException`, 나머지 → 메시지 없는 `IOException` 으로 옮긴다. RAR4 의 잠긴 헤더(틀린 열쇠로 '열리고' 항목이 0개)도 가른다 |
| 암호를 **세션 동안만** 기억한다(`SessionPasswords`) | 압축 목록·만화 뷰어·풀기 큐가 같은 파일을 따로 연다. feature 끼리 볼 수 없어 자리가 `core:io` 다(아래 '암호가 걸린 파일'). 앱이 화면에 없으면 `put` 을 받지 않는다(확인하는 사이 홈으로 나가면 앱이 뒤에 있는 내내 암호를 들었다) |
| 지워지면 **알린다**(`SessionPasswords.clears`) | 홈에 나갔다 오면 세션 암호는 지워졌는데 ViewModel 의 목록은 '풀린' 채라, 풀기 대화상자와 실제 풀기가 서로 다른 말을 했다. 암호로 읽은 목록(`Doc.unlocked`)은 그때 다시 읽고, `load` 는 지운 횟수를 앞뒤로 견준다 |
| 자물쇠는 **지금 못 여는** 항목에만 | 암호를 넣은 뒤에도 자물쇠가 남으면 풀린 것을 잠긴 것으로 읽는다. 풀린 글 항목은 종류별 안내('이 항목을 열 수 없습니다')로 간다 |
| 풀기 요청은 **만들 때 사본을 붙든다** | 큐가 밀려 앱이 화면에서 사라진 뒤에 돌 수 있다. 쓰고 나면 푸는 쪽이, 돌지 않고 버려지면(대기 중 취소·작업 사이 취소·시작 전에 끊김) 큐가 지운다 |
| 만화는 **자기 사본**을 든다 | solid 7z 는 창을 벗어날 때마다 아카이브를 다시 연다 — 그때마다 암호가 필요하다 |
| 파일이 바뀌면 암호 창·풀기 계획을 비운다 | 액티비티에 묶인 ViewModel 이라 앞 파일의 것이 다음 파일로 넘어간다(함정 표). 다시 잠글 때도 계획을 비운다 |
| 잠긴 쪽 판정을 **그림 이름**으로 | '폴더 항목이 하나도 없고 전부 잠겼다' 는 조건은 쪽을 폴더에 담은 CBZ(흔하다)를 '그림이 한 장도 없습니다' 로 보냈다 — 암호를 물을 길 자체가 없었다(`ComicPasswordTest`) |
| 암호 사본을 **새지 않게** 덮는다 | 리더 생성 실패, 항목마다 만드는 `String`·후보 바이트, 맞지 않은 후보의 열쇠, PBKDF2 중간값 — 사본을 연 다음에 만들고 모든 실패에서 덮는다. 후보는 `CharsetEncoder` 로. 암호 deflate 항목은 닫을 때 `Inflater.end()`(네이티브 zlib 상태가 GC 를 기다렸다) |

**오라클이 우리가 아니다.** ZIP 은 반디집(전통 암호, 영문·한글 암호)과 pyzipper(AES-128·256,
저장·deflate), 7z 는 py7zr(내용만·헤더까지·한글 암호)가 잠갔다. 만화 표본(`feature/comic/src/test/
resources/comiccrypt/`, 3개 1,315 바이트)도 두 도구가 잠그고 스스로 되읽어 확인했다. 우리 암호기로 잠근 ZipCrypto 표본은
**파이썬 표준 `zipfile` 이 푸는 것**으로 확인했다. **RAR 은 미검증이다** — 암호 RAR 을 만들 도구가 없어 junrar 소스를 읽고 짠 코드다.
확인 바이트·후보 결정·7z 가리기 셋은 고침을 되돌려 해당 시험이 정확히 그것만 깨지는 것을 확인했다.

### 실세계 말뭉치

**말뭉치** — `samples-local/corpus/`(커밋하지 않는다) 122건: PDF 25, EPUB 16, OOXML 81(OOXML 은 걷은 오피스 변환기의 시험이 쓰던 것이다 —
지금 그것을 읽는 시험은 없다). 전부 공개 저장소의 시험 표본이다 — Apache POI·Tika(Apache-2.0), LibreOffice core(MPL-2.0), openpreserve
format-corpus(CC0), IDPF·W3C 의 EPUB 시험(CC BY-SA — 로컬에서만 쓴다), 미국 정부 저작물(퍼블릭 도메인), python-docx·msoffcrypto-tool(MIT).
파일마다 원래 이름·주소·라이선스·만든 도구·기대 동작을 `manifest.json` 에 적었다. 오라클을 만드는 스크립트는 말뭉치 곁
(`samples-local/corpus/tools/`)에 둔다 — 세션의 스크래치패드에 두면 세션이 끝난 뒤 오라클을 다시 만들 수 없다.

**시험이 스스로 돈다** — `EpubCorpusTest`·`PdfCorpusTest`·`PdfTextCorpusTest`(JVM)·`PdfCorpusDeviceTest`(계측). 말뭉치가 없으면 건너뛴다.
오라클 파일만 없으면 판별·여는 결과·암호 흐름·구조 단언은 돌고 낱말 점수·복호화 해시 비교는 건너뛴다(확인했다).

오라클은 pikepdf(qpdf 12.3.2)·pypdf 6.17, EPUB 은 zipfile + lxml 로 OPF·nav·NCX·본문을 따로 읽은 것, 그리고 말뭉치가 스스로 공개한
기대값(IDPF·W3C 저장소의 git blob SHA-1)이다.

**남긴 한계** — PDF-28(첨부만 잠김)은 우리 복호화기로 가면 암호를 묻는다(기기에서는 pdfium 이 스스로 열어 닿지 않는다). XHTML 의
`<p><div>` 는 HTML 파서가 다르게 닫고(말뭉치에 없다), `<style>` 안의 CDATA 는 첫 규칙을 잃는다. 읽어 주기(미디어 오버레이)는 알리지 않고
버린다. ZIP 리더의 생성자가 던지는 맨 `IOException` 은 모두 '깨진 파일' 이다(상한·중단·사라진 파일은 빼고) — commons-compress 가 깨진
구조와 저장소의 읽기 오류(SAF 의 EIO)를 같은 형으로 던져 가를 수 없다.

### 14단계 — 설정·크래시 기록·고지와 뷰어 다듬기

| 꾸러미 | 한 것 |
|---|---|
| 설정(`feature:settings`, 새 모듈) | 파일 목록·텍스트 뷰어 기본값·만화의 새 책 방향·문서 보기(글자 70~200%·여백·바탕 넷)·기록 지우기(확인 대화상자)·크래시 기록·정보(판·고지·진단). 쓰기는 `Deferred<Boolean>` 을 돌려주고 화면이 기다려 알린다 — 사건 채널로 두면 화면이 사라진 뒤 끝난 결과가 다음에 설정을 열 때 뜬다. 고지·진단에 다녀오면 설정 화면이 컴포지션에서 빠지므로 스크롤 자리를 액티비티에 묶인 VM 에 맡긴다 |
| 크래시 기록(`core:data` 의 `CrashLog`) | `attachBaseContext` 에서 걸고 앞의 처리기를 잇는다(두 번 불려도 한 겹, 어느 길로도 던지지 않는다). `filesDir/crash/` 에 최근 5건·한 건 64 KiB, 임시 파일 → fsync → rename. 절대경로와 `content:`·`file:` 주소는 `<경로>` 로 지운다. **화면에는 건수와 마지막 시각만** 보이고, 공유는 캐시의 사본으로 한다(잠근 채로 덮어쓰고, 만드는 동안 단추를 막는다) |
| 고지(`app` 의 `NoticeScreen`·`NoticeCatalog`) | 릴리스 런타임 클래스패스의 좌표. 라이선스는 Gradle 캐시의 POM·jar 에서만 읽었다(Apache-2.0 · 0BSD(XZ) · UnRAR · MIT(SLF4J API) · BSD 3-Clause(DataStore 의 protobuf), Commons 넷의 NOTICE). **한컴 문서 명세의 고지 문장**을 싣는다 — 함정 표의 '명세가 요구한 문장'. 편집기 엔진의 고지는 아래 '…뷰어 교체' |
| 릴리스 | 1.0.0(판 코드 2). 서명은 그대로 디버그 키다 — 바꾸면 설치된 앱을 지우고 깔아야 해 기록이 사라진다. 사용자가 정할 때 한 번 |
| 파일 관리자 | **당겨서 새로고침**(목록·격자·갤러리·휴지통. `PullToRefreshBox` 가 아니라 `Modifier.pullToRefresh` 다 — material3 1.4.0 의 상자에는 `enabled` 가 없어 고르는 중에 끌 수 없다. 짧은 목록에서도 당겨진다)과 ⋮ 메뉴의 '새로고침'(화면 낭독기로는 몸짓을 찾을 수 없다). 폴더 감시(`FolderWatch` — `FileObserver` + 디바운스, 우리 임시 파일은 무시)와, 앞으로 돌아올 때 폴더의 수정 시각이 바뀌었을 때만 다시 읽기(실패한 나열의 시각도 들어 막힌 폴더를 매번 다시 읽지 않는다). 휴지통 자동 비우기(JobScheduler — `TrashPurgeJob`, 하루 한 번·유휴 조건, 조용한 작업이라 포그라운드 서비스를 띄우지 않는다). '다른 곳에 복원'. 풀기 뒤 '열기'. 목록의 읽던 쪽 배지(`12/30쪽`·`50%`·`다 읽음` — 이어보기 키로 찾고 경로를 쓰지 않는다. 자리가 하나뿐인 문서에는 달지 않는다) |
| 텍스트 | 기본값을 설정에서 시작하고 바꾸면 적는다. 찾기 진행률(찾을 말을 바꾸면 돌던 찾기를 멈추고 번호를 바꾼다, 색인이 아는 행까지만 훑는다 — 자라는 로그에서도 끝난다). 줄 끝 표시(LF `↓`·CRLF `⏎`·CR `←`, 찾기·강조 좌표 밖). **마크다운 미리보기** — `format:text` 의 순수 JVM 변환기(CommonMark 줄 알고리즘·구분자 쌓기 + GFM 표·취소선·할 일). 날것의 HTML 은 글자로 보이고(명세와 다르다), 누를 수 있는 것은 `#자리` 뿐이며, 그림은 그 폴더 아래의 상대 경로만 보인다. 문서와 같은 잠긴 WebView 에서 돌고, 글자 크기는 `textZoom` 으로 그 자리에서 건다(다시 만들면 읽던 자리가 맨 위로 간다). 공백은 명세대로 공백·탭·줄 끝만 벗긴다(코틀린의 `trim()` 은 전각 공백·NBSP 까지 벗겨 일본어의 전각 들여쓰기가 사라졌다) |
| 만화 | 새 책의 기본 방향(책의 기록이 이긴다 — `ReadingStart.decide`). 두 쪽 보기(한 쪽·두 쪽·자동. 표지는 혼자 서고, 짝은 쪽 번호만으로 정하며, 오→왼이면 앞 쪽이 오른쪽. 반쪽 하나의 몫이 `pageCap/2` 라 펼침 넷이 한 장짜리 넷과 같은 바이트다. 반쪽은 자리에 맞춘 크기로 뜬다 — `SpreadMath.halfSize`, 2의 거듭제곱 표본이면 1.2~1.8배 늘어나 흐리다. 두 쪽으로 뛰면 뛰어든 자리에서 세 쪽 물러서 창을 연다 — 페이저가 앞 칸 두 쪽을 함께 띄운다). 쪽 목록(격자. 디스크에 쓰지 않고, solid 는 아카이브 차례로 한 번 훑는다). 다음 권(파일 관리자의 이름 차례에서 지금 책 바로 뒤 — 권 번호를 이름에서 읽지 않는다. ⋮ 메뉴와, 끝에 닿았을 때의 알림) |
| 문서 뷰어 | 읽기 모양(글자는 `textZoom` — 책의 CSS 와 싸우지 않고 다시 읽지도 않는다, 여백·바탕은 위생을 거친 CSS)과 뷰어 안의 '보기'. 장 안쪽 위치(`locator` = `장:비율`, 경로·글을 적지 않는다 — 비율이라 글꼴 크기를 바꾸면 조금 어긋난다. CFI 는 만들지 않았다). 고정 레이아웃 EPUB(쪽의 뷰포트 — 없으면 OPF 의 `rendition:viewport` — 로 화폭을 정하고 화면에 맞춘다. 한 번 알린다). PDF 두 쪽 보기·쪽 목록·**API 35 에서만** 찾기 |
| 압축 | **tar 계열**(`.tar`·`.tar.gz`/`.tgz`·`.tar.bz2`/`.tbz2`·`.tar.xz`/`.txz`, 그리고 `.cbt` 만화). 머리는 우리가 읽고 해제기만 라이브러리를 쓴다 — commons-compress 의 `TarArchiveInputStream` 은 메타 머리(긴 이름·PAX)를 적힌 크기만큼 모으고, 이름의 원본 바이트를 주지 않고, 링크의 크기 칸을 믿는다. 이름 상한은 머리 하나에 64 KiB, 한 번 훑는 동안의 합 16 MiB(이어 붙인 PAX 의 희소 열쇠까지). PAX 시각은 64자·밀리초 범위를 넘으면 머리의 시각을 쓴다(`BigDecimal.toLong` 은 넘쳐도 말하지 않는다). 압축 안 한 tar 는 번호로 열고, 압축 tar 는 흐름이라 solid 7z 와 같은 길이다(목록·건너뛰기가 총량 상한에 묶인다 — 풀린 크기가 1 GiB 를 넘는 `.tar.gz` 는 '풀어야 할 양이 너무 커서 열지 않았습니다'). 링크·장치·희소 파일은 풀지 않고 링크로 센다. 풀기 뒤 **폴더 수정 시각**(이번 풀기가 만든 폴더 가운데 아카이브에 시각이 적힌 것만, 깊은 것부터 마지막에 — 원래 있던 폴더의 시각은 사용자의 것이다). **진행률의 분모**는 아카이브에서 실제로 읽은 바이트다(RAR 은 선언값, 미검증). **인코딩 판정을 하나로** — `EntryNameDecoder` 가 `core:charset` 의 `CharsetDetector` 를 부른다(아래 '모듈 구조') |
| 이미지 | 이미지 뷰어가 GIF·애니WebP 를 튼다 — 자리 잡은 장 하나만 돌고, 버퍼 셋이 한 화면 크기 비트맵에 들 때까지 표본을 줄이며, 그래도 안 들면 첫 장면과 까닭을 보인다(까닭 문구는 그것을 만드는 `AnimationPlan.StillReason` 곁 `core:ui` 에 한 벌 — 만화 뷰어의 APNG 문구도 그것을 쓴다). 확대도 된다(`ZoomablePainter` — hwui 는 드로어블을 표시 목록에 한 번 기록하고 프레임은 RenderThread 가 넘긴다. 만화 뷰어는 그대로 막는다). APNG 은 첫 장면과 고지. **아래로 끌어 닫기**(배율 1 에서만, 인식기 하나가 터치 슬롭에서 축을 한 번 정한다. 뷰포트의 15% 를 넘기거나 1000 dp/s 로 튕기면 닫는다. 되돌리는 애니메이션은 장이 아니라 상태가 든다 — `DismissState`. 만화 뷰어에는 넣지 않았다). 썸네일 부정 캐시가 일시·영구·불확실을 가른다(`ThumbnailFailures` — 영구는 디코더가 바이트를 거절한 것뿐, OOM·입출력은 2초부터 두 배씩 10분까지 물러난다). 확대 조각이 EXIF 여덟 방향을 다 옮긴다 — 디코더가 방향을 적용했는지는 조각이 처음 필요할 때 두 디코더로 작게 떠 견준다(`OrientationMatch` — 한쪽이 뚜렷할 때만 답하고, 모르면 조각 없이 흐리게 둔다) |

**검토가 잡아 지금의 규칙이 된 것**(함정 표에 든 것은 빼고) — PDF 는 `locator`·`progress` 를 비운다(적으면 배지가 '4/8쪽' 에서 '50%' 가 되고
한 쪽짜리 PDF 는 열기만 해도 '다 읽음' 이었다). EPUB 의 진행은 화면 **끝** 가장자리까지 본 몫이다(`ReadingScroll.seenOf` — 앞 가장자리로 세면
한 화면에 드는 짧은 장은 늘 0이다). 글자 크기·회전 뒤의 되살리기는 범위가 한 번 바뀐 뒤에만 센다(`onPageFinished` 가 다시 오지 않는다).
`RowReader.read` 는 원하는 행을 다 모으면 멈추고(끝까지 풀면 찾기·강조가 파일 크기의 제곱이었다), 길이가 4,096의 배수인 줄이 빈 행을
하나 더 만들지 않는다. **CP949 이름의 판정** — 라틴 낱말 판정은 ASCII 끼리만 세고(`캡처`·`체크`·`치킨`·`호환` 이 `ĸó` 로 읽혔다), 반각 문장
부호(U+FF61~FF65)를 일본어 글자로 세지 않는다(`DCIM · Pictures` 가 Shift_JIS 로 이겼다). 옛 판정기를 시험 안에 복사해 한국어 이름 약 1만
2천 건에서 차등으로 견줬고 회귀 0건이다.

**합치며 앱에서 붙인 것.** 풀기 결과 알림의 '열기'(동작 단추가 붙으므로 기간을 `Long` 으로 적는다 — 함정 표), 다음 권으로 넘어가면
앱의 화면 상태도 그 책을 가리키게(`AppScreen.Comic` — 프로세스가 죽었다 살아나도 보던 책으로 돌아온다), 만화의 쪽 배치와 PDF 두 쪽
보기를 설정으로 남기기(`AppPreferences.comicPageLayout`·`pdfSpread` — 늦게 끝난 설정 읽기가 사용자가 방금 고른 것을 덮지 않게 깃발을
둔다), `MimeResolver` 가 `.mdown`·`.mkd`·`.mkdn`·`.mdwn` 을 텍스트로, `.tbz2`·`.tbz`·`.txz` 를 압축으로 본다. `ComicOpen` 의 여는 문이
`Archives.detect` 라 `.cbt` 와 tar 안의 그림이 만화 뷰어로 열린다(tar 의 표지는 257바이트 자리에 있다 — `ComicSourceTest`). 암호 PDF 를 풀어 쓴
xref 는 **기기의 로캘로 숫자를 적지 않는다**(`String.format` — 아랍어·페르시아어 로캘이면 풀린 PDF 가 깨진다. 모듈 lint 의 `DefaultLocale` 이
잡았다). 목록·격자 전환 단추에 이름이 있다(글리프가 손으로 그린 캔버스다). 설정의 '최근 위치 지우기' 는 뺐다 — 그 표에 쓰는 코드가 한 줄도
없어 지울 것이 언제나 없는 단추였다. 최근 위치를 기록하기 시작할 때 더한다.

**화면 전이가 문자열이다**(`AppScreen.kt` 의 `AppScreenCodec`). 진단·고지는 돌아갈 곳을 든다 — 설정에서 연 고지는 설정으로, 압축
목록에서 연 고지는 그 압축 목록의 그 폴더로. 옛 값·망가진 값은 첫 화면이다. 모든 화면이 알림 자리(`SnackbarHost`)를 갖는다(Root 의 불변식).

**텍스트 뷰어가 `rememberSaveable` 의 함정을 바라는 동작으로 쓴다** — 회전과 '새로 열기' 를 입장 표(저장 상태로 든 시각)로 가른다.
`MainActivity` 에 `SaveableStateHolder` 가 없어서 성립하는 장치라, 그것을 넣게 되면 이 장치를 다시 봐야 한다.

### 14단계 뒤 — 다른 앱으로 열기

**계약은 `core:io` 의 `ExternalOpen` 하나다** — `open(context, path, mode)` 과 `messageOf(result)`, MIME 은 `ExternalMime`. 문구
(`io_open_with`·`io_open_with_any` 와 실패 문장 셋)도 거기 한 벌이고 목록과 뷰어들이 그것을 쓴다(함정 표의 '문구는 그것을 만들어 내는
타입 곁에').

| 규칙 | |
|---|---|
| R1 | 이 앱에 뷰어가 없는 파일(APK·FONT·OTHER)을 누르면 `Mode.VIEW` — 받는 앱이 하나거나 기본 앱이 있으면 곧바로(APK 는 설치 관리자), 여럿이면 시스템의 '다음으로 열기' |
| R2 | '다른 앱으로 열기' 라고 적힌 모든 자리는 `Mode.CHOOSE` — 언제나 고르는 창(기본 앱을 쓰지도, 정하지도 않는다) |
| R3 | 뷰어가 '이 앱이 보여 줄 수 없다' 로 끝나면 그 문장 곁에 단추(아래 표). 이 앱이 묻는 암호, 없거나 열리지 않는 파일, 압축 안의 항목, 휴지통에는 두지 않는다 |
| R4 | `STARTED` 가 아닌 결과는 `messageOf` 의 우리 문장으로 그 화면의 알림에. 예외 문구는 나가지 않는다 |
| R5 | 이름은 언제나 `io_open_with` 다. feature 모듈에 사본을 두지 않는다 |
| R6 | 주 스레드에서, 컴포지션의 액티비티 컨텍스트로 부른다(동기이고 가볍다) |

| 무엇 | 왜 그렇게 했나 |
|---|---|
| MIME 을 **차례로 좁힌다** — 우리 표 → 플랫폼 `MimeTypeMap` → 종류의 대표 → 모든 것 | 받는 앱은 인텐트의 MIME 과 **자기 필터**를 맞춰 본다. `octet-stream` 을 주면 그것을 선언한 드문 앱 말고는 아무도 나오지 않는다. 그래서 우리 표에는 이 앱이 열지 않는 형식(`.mobi`·글꼴·`.iso`·`.wma`…)도 적는다. 플랫폼 표의 `octet-stream`·모양이 이상한 값은 모르는 것으로 친다. 표는 전부 소문자다(함정 표) |
| 그림·소리·영상·글은 **그 계열 안의 답만** 쓴다 | 계열 전체로 선언한 필터(`video` 뒤에 별표)는 `application/…` 을 받지 않는다(`IntentFilter.findMimeType`). `.rmvb` 의 등록값을 주면 영상 앱이 하나도 안 나온다. 글·코드는 `text/plain`(편집기가 선언하는 것) — 플랫폼 표는 `.py` 를 `text/x-python`, `.js` 를 `application/javascript` 로 준다 |
| **와일드카드면 언제나 고르는 창** | 시스템의 '다음으로 열기' 에서 '항상' 을 누르면 그 선택이 **인텐트의 MIME 그대로** 선호가 된다 — 계열 전체면 그 계열의 모든 파일이 그 앱으로 간다 |
| **이 형식을 아는 앱이 없으면 '모든 앱에서 고르기'** | 에뮬레이터에서 `.xyz`(플랫폼 표가 `chemical/x-xyz`)·`.iso`·`.mobi`·`.ttf` 의 고르는 창이 'No apps can perform this action.' 뿐이었다. 고르는 창을 띄우기 전에 그 MIME 을 받을 **다른 앱이 보이는지** 묻고(`queryIntentActivities`, `MATCH_DEFAULT_ONLY`. 우리 자신·내보내지 않은 것·우리가 못 가진 권한을 요구하는 것은 세지 않는다 — 옛 고르는 창의 `filterIneligibleActivities`), 없으면 같은 URI 를 모든 것으로 넘기는 고르는 창을 그 제목으로 띄운다(`ExternalOpen.chooserPlan` — 다만 두 에뮬레이터의 고르는 창은 VIEW 에 제목을 그리지 않았다). 처음부터 모든 것인 파일(확장자 없음·어느 표도 모름)도 같은 창·같은 제목이다 |
| 질의는 **넓힐지만 정한다. 막지 않는다** | 모든 것까지 받는 앱이 없다고 `NO_APP` 을 돌려주는 길은 버렸다. 공개 범위가 기기에서 다르게 돌면 모르는 파일을 하나도 못 열게 되고, 맞을 때 얻는 것은 시스템의 빈 화면 대신 우리 문장 하나다. 그래서 **고르는 창에서는 `NO_APP` 이 나오지 않는다.** `NO_APP`('이 형식을 아는 앱이 없습니다')은 곧바로 여는 VIEW 에서만 나고, 목록은 그때 정보 시트를 띄운다 — 그 시트의 단추가 모든 앱의 창으로 간다(시트는 별도 창이라 못 띄웠을 때의 문장을 시트 안에도 빨갛게 적는다 — 함정 표) |
| `core:io` 의 매니페스트에 `<queries>`(VIEW · `content` · 모든 형식) | API 30 부터 질의는 **우리에게 보이는 앱만** 답한다. 묻는 코드가 그 모듈에 있어 선언도 거기 두고 병합이 앱에 싣는다. **사생활** — 이 앱은 content URI 를 VIEW 로 받는 설치된 앱을 볼 수 있다. 답은 넓힐지 정하는 데 한 번 쓰고 버린다(보여 주지도 저장하지도 않는다). `QUERY_ALL_PACKAGES` 는 쓰지 않고 `INTERNET` 이 없다 |
| `app` 의 매니페스트에 `REQUEST_INSTALL_PACKAGES` | API 31 설치 관리자의 dex 로 확인했다 — targetSdk 26 이상인 앱이 이것을 선언하지 않고 APK 를 보내면 `InstallStart` 가 로그 한 줄만 남기고 **조용히 닫힌다**(우리에게는 `STARTED`). 선언이 설치를 허락하지는 않는다 — 사용자가 '출처를 알 수 없는 앱' 에서 이 앱을 허락해야 하고, 그 뒤에도 설치할 때마다 설치 관리자가 묻는다. 이 앱은 스스로 무엇도 설치하지 않는다(`PackageInstaller` 세션을 열지 않는다) |
| 권한은 **그 인텐트에만, 읽기만** | URI 는 `ShareHelper.uriOf` 한 곳(`/storage/` 와 캐시의 공유 폴더만, `canonicalFile` 의 `IOException` 까지 그 안에서 잡는다). 휴지통은 그 앞에서 `ExternalOpen.refuses` 가 거절한다. 고르는 창은 안쪽 인텐트의 데이터·깃발을 들고 고른 앱을 **우리 이름으로** 띄운다(`startAsCaller`) — 권한이 우리 FileProvider 에서 곧장 그 앱으로 간다 |
| **두 번 누르기를 막는다** | `startActivity` 는 받는 앱의 창이 뜨기 전에 돌아온다(함정 표). 같은 파일·같은 모양은 500 ms 안에 다시 띄우지 않고(`ExternalOpen.isRepeat`), 목록은 띄운 뒤 700 ms 동안 누름을 흘려보낸다(`OpenWithRules.SETTLE_MS` — 시트·메뉴가 닫힌 자리 아래의 줄이 눌리는 것까지 막는다) |
| 판단은 **모듈마다 순수 객체 하나** | `DocOpenWith`·`TextOpenWith`·`ArchiveOpenWith`·`ImageOpenWith`·`ComicOpenWith`·`PlayerOpenWith`, 목록은 `TapRoute`. 문서·텍스트·압축·재생·목록은 `else` 없는 `when` 이라 갈래가 새로 생기면 정하기 전까지 컴파일되지 않는다(함정 표의 '종류를 옮기면 …'). 만화는 집합(`FAILURES_TO_HAND_OFF`)이라 컴파일러가 막지 않고 **시험이** `ComicOpen.Kind.entries` 를 센다. 이미지는 두 값짜리 enum 의 비교다. 시험이 `FileKind.entries`·`OpenFailure` 의 하위형도 전부 센다 |
| 종류 표는 **한 벌**(`extensionTable`) | 시험용 표와 `kindOf` 의 `when` 이 같은 차례를 따로 들면 `when` 에만 더한 갈래가 표 시험을 비켜 간다(`ts` 가 영상·코드 두 표에 있던 것도 그렇게 숨었다) |
| '깨졌다' 와 '읽을 수 없다' 를 **여는 탐침**으로 가른다(`core:io` 의 `FileProbe`) | 안드로이드는 권한 거부도 `FileNotFoundException` 으로 알려, 예외만 보면 열리지 않는 파일이 '깨진 파일' 이 되고 단추가 선다. `canRead()` 가 아니라 **실제로 연다**(FUSE 에서 둘이 같다는 것을 확인하지 않았다). 여는 **사이에** 없어진 파일도 실패한 길에서 한 번 더 열어 본다(문서 `failedAfter`·압축 `failureKindOf(t, reachable)`·만화 `failedKind`). 한 벌이고 모듈 쪽 함수는 한 줄 위임이다 |
| 실패 화면의 제목에 **파일 이름**(`core:model` 의 `PathNames.lastSegment`) | 실패 화면에 단추만 있으면 무엇을 넘기는지 보이지 않는다. 전체 경로는 적지 않는다. 만화는 열린 뒤의 이름(`Book.name`)처럼 만화·압축 확장자를 뗀다(`ComicTitle.bookNameOf`) — 다르면 열리는 순간 제목 글자가 바뀐다 |
| ⋮ 는 **파일에 닿은 뒤**에만 켜진다 | 문서·텍스트·압축의 `Loading(reached)`. 없는 파일은 `NOT_SHAREABLE` 로 걸러지지만 **있는데 열리지 않는 파일**은 걸러지지 않고 받는 앱에 넘어간다. 읽을 수 없는 파일에는 서지 않는다(문서·이미지·만화·재생은 줄이 없고, 텍스트·압축은 흐리게 남는다). 암호를 묻는 중에는 있다 — 잠긴 PDF·zip·cbz 를 다른 앱의 암호 입력으로 넘길 수 있다 |
| 재생 실패는 **그 항목에 매인다** | 새 상태는 순수 함수 `State.advancedTo(frame)` 가 만들고 실패는 같은 항목일 때만 들고 간다(앞 곡의 실패 곁에 다음 곡을 넘기는 단추가 설 수 있었고, 다음 곡이 같은 값으로 실패하면 `MutableStateFlow` 가 합쳤다). `push()` 를 컨트롤러 읽기(`Frame`)와 순수 함수로 갈라 시험이 제품 코드를 부른다. media3 1.11.1 바이트코드 — 한 묶음의 알림은 타임라인 → 불연속 → 항목 전환 → 오류 → 트랙 차례이고 알리기 전에 상태를 갈아 둔다(새 항목의 오류를 지우지 않는다) |
| 컨테이너 실패를 **우리 말로** | '이 앱이 재생하지 못하는 형식입니다'(추출기가 없다 — 다른 앱이 열 수 있다)와 '파일이 깨졌거나 읽을 수 없는 모양입니다' 로 가른다(media3 의 오류 코드 이름을 화면에 적지 않는다) |
| 가려진 목록은 재생 실패를 **지우지 않는다**(`feature:browser` 의 `PlaybackNotice`) | 목록이 맨 앞(RESUMED)일 때만 말하고, 말한 그 실패가 아직 남아 있을 때만 지운다(함정 표 '값을 지우는 소비자를 …') |
| 재생 화면의 ⋮ 는 **한 덩이**(`PlayerMoreMenu`) | 펼침 상태가 화면 쪽에 있으면 단추가 사라진 사이(읽을 수 없는 항목·PiP·조작부 감춤)에도 남았다가 단추가 다시 설 때 저 혼자 펼쳐진다 |
| 넘기면 **우리 재생은 멈춘다**(끝내지 않는다) | 같은 파일을 저쪽에서 보려는 것이라 둘이 함께 소리를 내면 안 된다. 고르는 창은 늘 `STARTED` 라 물러나도 멈춘 채다 — 재생 단추 한 번으로 돌아온다. PiP 에서는 단추도 ⋮ 도 없다 |

**화면마다 단추를 두는 실패.**

| 화면 | 둔다 | 두지 않는다 |
|---|---|---|
| 파일 관리자 | 누름(R1), 정보 시트, 하나만 고른 선택의 ⋮('다른 앱으로 열기'·'정보') | 폴더·휴지통 |
| 문서 | 다루지 않는 형식·풀 수 없는 잠금(DRM·인증서)·깨짐·너무 큼·시간 초과. 오피스·한글이 여기 닿으면(편집기를 띄우지 못했을 때) 편집기가 첫 단추다(아래 '…뷰어 교체') | 암호를 묻는 중(⋮ 는 있다)·권한 없음·입출력 |
| 텍스트 | 글이 아닌 파일·읽지 못하는 인코딩(UTF-32) | 읽을 수 없는 파일 |
| 압축 | 다루지 않는 형식(iso·zstd·lzip·분할 등)·깨짐·너무 큼·풀 수 없는 암호 — **압축 파일 자신**을 넘긴다 | 읽을 수 없는 파일, 안의 항목 |
| 이미지 | 못 푼 그림(TIFF·못 푸는 HEIC·AVIF·SVG 도 비트맵도 아닌 `.svg`·깨짐), 렌더러가 죽은 SVG 장(SVG 는 WebView 가 그린다) | 없거나 열리지 않는 파일 |
| 만화 | 다루지 않음·깨짐·풀 수 없는 암호·너무 큼(책 파일), 폴더 만화의 못 연 쪽(그 쪽의 파일) | 폴더 책 자체, 압축 안의 쪽, 암호를 묻는 중 |
| 재생 | 디코더 없음·실패, 추출기 없음·깨진 컨테이너, 못 트는 트랙, 파싱·디코딩·DRM 코드 | 읽을 수 없음(입출력 코드), PiP |

### 14단계 뒤 — SVG·`.ts`·확장자 넓히기

**SVG 를 세운 방식.**

| 무엇 | 왜 그렇게 했나 |
|---|---|
| SVG 를 **`<img>` 로 싣는다**(우리가 쓴 쪽 + 잠긴 WebView, `SvgSupport`·`SvgPage`) | 플랫폼 디코더에 SVG 가 없다. 문서로 열면 스크립트·바깥 자원·링크를 우리가 막아야 하지만, `<img>` 로 실린 SVG 는 브라우저가 고정 모드로 그린다 — 스크립트도 바깥 요청도 누를 곳도 없다. 쪽(HTML)에는 파일의 글이 한 글자도 들어가지 않는다 |
| 흰 상자는 **그림 뒤에만** | SVG 는 대개 투명 바탕에 검은 선을 그린다. 검은 뷰어 바탕에 그대로 두면 선이 사라진다. 뿌리의 `width`·`height`(없으면 `viewBox`)로 비율을 재 그 상자에만 흰색을 깐다. 비율을 모르면 화면 전체다 |
| 뿌리 요소를 **해석기 없이, 한 번 훑어** 읽는다 | 시작 태그 하나의 속성 셋이면 된다. 해석기를 세우면 외부 실체·DOCTYPE 을 따로 막아야 한다. 주석·처리 지시·DOCTYPE(내부 부분집합, `] >`)을 건너뛰고 따옴표 안의 `>` 에 속지 않는다. **정규식을 쓰지 않는다** — 속성을 정규식으로 찾으면 이름 글자만 이어진 64 KiB 시작 태그 하나에 53초가 걸렸다. 값은 길이를 묶고 읽는다 |
| 뿌리가 svg 가 아니면 **비트맵 디코더에** 맡긴다 | 내용이 PNG·JPEG 인 `.svg` 를 디코더는 내용으로 연다('내용이 이긴다'). 그것도 아니면 '이 이미지를 열 수 없습니다' 와 '다른 앱으로 열기' |
| svgz 는 **앞머리로** 가른다. 풀린 크기 32 MiB. **풀린 바이트를 들지 않는다** | 확장자가 `.svg` 인데 내용이 gzip 인 파일이 있다. 압축 폭탄은 풀면서 센다. 풀린 바이트를 장마다 쥐면 페이저가 미리 세우는 서너 장이 저마다 32 MiB 까지 든다 — 앞머리만 남기고 WebView 가 청할 때 다시 연다(그때도 상한을 건다) |
| 회전 메뉴를 **끈다** | WebView 가 그려 화면에서도 돌지 않는다. 켜 두면 '저장할 수 없다' 만 떠서 돈 것처럼 읽힌다 |
| 렌더러가 죽으면 **그 장은 못 연 장** | 함정 표('WebView 의 렌더러가 죽으면 앱도 죽는다'). 파일은 멀쩡하니 '다른 앱으로 열기' 를 둔다 |
| **뒤쪽이 깨진 SVG 는 그려진 만큼만 보인다**(고치지 않았다) | 첫 그림 신호에 동그라미를 걷으므로 실패 문구가 없고, 빈 흰 상자일 수 있다(⋮ 의 '다른 앱으로 열기' 는 있다). 미리 XML 해석기로 확인하려면 Illustrator 가 뿌리의 `xmlns` 를 DOCTYPE 의 내부 실체(`&ns_svg;`)로 적는 것까지 받아야 해서 — 실체를 막은 해석기로는 멀쩡한 그림을 '깨졌다' 로 보낸다 — 하지 않았다 |
| 확대는 WebView 가, 넘기기·끌어 닫기는 페이저가 | 벡터라 키울수록 다시 그려 선명하다. 대신 손가락을 나눠야 한다 — 핀치를 지키는 `holdsGestures`, 두 번 두드림의 `doubleTapZoom`(둘 다 함정 표), 두드림으로 막대를 여닫는 **엿보는** 인식기(WebView 가 눌림을 소비해 부모의 두드림 인식기가 못 본다) |
| 두 번 두드림은 **사진과 같은 2.5배** | 같은 뷰어 안에서 그림마다 몸짓이 다르면 고장으로 읽힌다(`ZoomMath.DOUBLE_TAP_SCALE`). 확대돼 있으면 맞춤으로 돌아간다 |
| 썸네일은 **여전히 종류 배지** | 그림 한 칸을 그리려고 WebView 를 띄우지 않는다 |

**`.ts`·`.mts` 를 내용으로 가른 방식.**

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 목록은 **이름대로 적고**(`.ts` 영상, `.mts` 기타), 목록이 선 뒤 가른다(`BrowserViewModel.streams`·`AmbiguousStreams`) | 목록은 파일을 열지 않는다. 목록이 선 뒤 VM 이 입출력 디스패처에서 앞머리를 읽고(폴더당 새로 1,000개까지), 누름·▶ 큐·▶ 단추가 그 답을 쓴다. 아직 모르는 것은 누른 자리에서 읽는다. **크기·수정 시각이 같으면 다시 읽지 않는다** — 조각을 받는 HLS 폴더는 조각마다 목록이 다시 선다 |
| `.mts` 는 **기타**다 | 캠코더(AVCHD)의 `.mts` 는 192바이트 패킷이라 media3 가 틀지 못한다(함정 표). 영상으로 적으면 누를 때 재생 화면에서 실패한 뒤에야 넘어가고 폴더의 ▶ 가 전부 실패할 큐를 만든다. TypeScript 의 `.mts` 만 누를 때 글로 보낸다 |
| 글로 보내는 것은 **텍스트 뷰어가 받는 것뿐**(`ContentSniff`) | TypeScript 는 언제나 글이다. 글인지는 텍스트 뷰어와 **같은 판정기·같은 앞머리**(`BinarySniffer`, 8 KiB)가 정하고 나머지는 이름대로 영상 쪽이다 — 재생하지 못하면 재생 화면이 그렇게 말한다(거꾸로 '패킷이 0번 자리부터 이어져야 영상' 으로 짜면 잘라 낸 녹화가 텍스트 뷰어로 가서 '글이 아닌 파일' 로 끝난다) |
| 패킷 무늬를 **판정기 앞에서** 본다 | 첫 패킷 안 어느 자리에서든 다섯 패킷(188·192바이트) — media3 와 같은 기준이다. 널 패킷만 이어진 앞머리에는 널 바이트가 없어 판정기가 글로 본다 |
| ▶ 단추는 **영상으로 판정된 것만** 센다 | 모르는 것까지 세면 TypeScript 만 있는 폴더에서 단추가 떴다 사라진다. 갈리지 않는 미디어가 있으면 판정을 기다리지 않는다 |
| 다른 앱으로 넘길 때도, 공유할 때도 같은 판정(`ExternalOpen.mimeFor`·`ShareHelper.mimeOf`) | TypeScript 를 `video/mp2t` 로 넘기면 고르는 창에 영상 앱만 뜨고, 받는 앱이 영상으로 다룬다. 공유는 하나를 실을 때만 앞머리를 읽는다 — 여럿 가운데 갈리는 이름이 있으면 모든 것(`*/*`)으로 둔다(수백 개를 고른 공유가 주 스레드에서 수백 번 읽게 할 수 없다) |

**확장자 표를 넓혔다**(`MimeResolver`). 라이브러리·플랫폼이 **이미 읽는 것만** 더했다 — media3 의 추출기(AMR·AVI·FLV·MP4 계열·
MPEG-PS·TS·WebM), 플랫폼 디코더(JFIF·WBMP), 글·코드 확장자와 **확장자 없는 코드 이름**(Makefile·Dockerfile·`.gitignore`…),
TTML 자막. RAW 사진(1단계 제외)·xapk 류·aif·dts 는 더하지 않았다. 텍스트 뷰어가 강조할 줄 아는 이름은 전부 글·코드로 잡힌다
(`TextNamesConsistencyTest` — 예외는 영상인 ts, 기타인 mts, 그림인 svg). '뷰어는 아는데 목록이 기타' 인 어긋남을 시험이 막는다.
`.jfif`·`.jpe`(JPEG 의 다른 이름)는 회전 저장·메타데이터 제거 공유도 JPEG 과 같다 — 그 판정이 확장자로 하는 것이라(화면 `ExifReader`,
엔진 `FileOpEngine`) 두 곳에 함께 더했다.

**위생기(`format:html`)의 규칙 — EPUB·마크다운 미리보기에 걸린다.**

| 규칙 | 왜 |
|---|---|
| CSS 는 한 번 훑는다(`CssRefs`), 위생기와 CSS 정리에 취소 확인점 | `@import;` 를 되풀이하면 제곱이었다(8 MiB 원문에 135초). 취소도 닿지 않아 파싱 디스패처 하나를 몇 분 쥐었다 |
| 결과 상한은 **쓰는 도중에** 잰다 | 다 만든 뒤에 재면 `"` 하나가 `&quot;` 로 부풀어 상한의 몇 배를 이미 쥔 뒤다(8 Mi 자면 힙 192 MB 를 넘었다). `HtmlSanitizerReviewTest` 가 확인점 수로 지킨다 |
| 짝 없는 `</script>`·`</iframe>`·`</object>` 는 그 태그만 버린다 | 하나가 **뒤 본문을 전부 지웠다**. `frame` 은 짝 없는 요소다. HTML 의 스스로 닫기는 여는 태그다(빈 요소는 XHTML 만) |
| 이스케이프를 푼 뒤 함수·규칙을 가른다 | `\75 rl(`·`image-set("…")`·`@\69mport`·`style="url&lpar;…&rpar;"` 가 CSS 위생을 비켜 갔다. `style` 은 문자 참조를 푼 글을 거르고, 모르는 이름 참조가 남으면 버린다 |
| SMIL(`<set attributeName="href" to=…>`)의 값을 버린다 | '같은 문서만 가리킨다' 규칙을 비켜 갔다(이름을 참조로 적어도) |

CSS 문자열 안의 `url(` 은 `none` 으로 바꾸지 않고, 깨진 `url(` 은 `none`, `style=""` 은 풀고 거른 뒤 다시 이스케이프한다(`&` 가 `&amp;` 로 나간다).

**압축 — 지금 라이브러리로 읽을 수 있는 것**(`format:archive`·`feature:archive`).

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 파일 하나를 싼 gz·bz2·xz·lz4·lzma·Z 는 **항목 하나짜리**로 연다(`SingleStreamReader`) | 목록은 앞 512바이트만 풀어 안이 tar 인지 본다. 이름은 gzip FNAME(마지막 조각만, 제어 문자 버림, 풀기 관문, 압축 파일 자신과 같은 이름 거절)이나 파일 이름(7-Zip 규칙). 크기는 정직한 것만 — xz 인덱스·LZMA 머리, 나머지는 '크기 모름' |
| tar.lz4·tar.lzma(`.tlz`)·tar.Z(`.taz`), cpio 네 형식, ar·deb | deb 은 이름이 아니라 내용(첫 항목 `debian-binary`)으로 이름표를 단다 |
| **gzip 의 틀은 우리가 읽는다**(`GzipInputStream`, 해제는 `Inflater`) | commons-compress 는 FNAME·FCOMMENT 를 상한 없이 모은다. `.tar.gz` 도 이것으로 읽는다 — JDK 와 무작위 멤버 400건을 견줘 불일치 0 이었고, 옛 해제기는 하위 필드가 아닌 FEXTRA 를 깨졌다고 던져 그중 132건을 못 열었다 |
| tar·cpio·ar 은 한 벌(`ScannedArchive`·`EntryScannerBase`) | commons-compress 의 cpio·ar 읽기는 이름을 적힌 길이만큼 모으고, ar 이름을 ASCII 로만 풀고, 건너뛸 때 자료를 전부 읽는다 |
| 판별 차례: ZIP·7z·RAR → **tar 검사합** → 압축 매직 → ar → cpio → 빈 tar | tar 검사합이 압축 매직 앞이다 — 첫 이름이 `BZh9…` 인 tar 가 bzip2 로 보였다 |
| LZMA 는 매직이 없어 이름(`.lzma`·`.tlz`)·엄격한 머리·실제로 풀림 셋을 함께 요구한다 | 무작위 `.lzma` 200건이 하나도 아카이브로 보이지 않는다 |
| 판별에 5초 상한, xz 는 스트림마다 `BasicArrayCache` | 빈 블록이 이어진 xz 는 블록마다 사전(최대 72 MiB)을 새로 잡았다 — 36 KB 파일 하나에 5.6초(고친 뒤 12 ms) |
| 자기 덮어쓰기는 **파일의 정체**로 막는다(`SelfOverwriteGuard` — `Files.isSameFile`) | '여기에 풀기' 가 아카이브와 같은 이름의 항목을 만나면 원본이 그 항목으로 바뀐다. canonical 경로는 대소문자를 적힌 그대로 두는데 FAT·exFAT SD 는 대소문자를 가리지 않는다(`a_b.gz` 와 `A_B.GZ` 가 한 파일). 정체를 읽지 못하면 덮어쓰지 않는다 |
| 남은 크기보다 긴 선언 길이는 '깨짐' | '풀어야 할 양이 너무 커서' 로 보였다 |
| 0 으로만 된 본문은 안의 이름이 `.tar` 일 때만 빈 tar | `zero.img.gz` 가 '빈 압축 파일' 로 열려 풀 수 없었다 |

남은 것: 한 아카이브 안의 대소문자만 다른 두 이름은 대소문자를 가리지 않는 볼륨에서 충돌 규칙을 따르고, bzip2·LZ4 의 작은 스트림이 잔뜩
이어진 파일은 크기에 비례해 느리다(25,000개 925 KB 에 1.2초 — 취소는 닿는다).

### 14단계 뒤 — 슬라이더 통일·블루투스 이어 재생·다른 앱에서 열기

**슬라이더·진행 표시를 한 벌로**(`core:ui` 의 `IroSlider`·`IroProgress`). 재생 화면의 모양 — 4dp 둥근 트랙, 채움은 강조색·
나머지는 28% 투명, 끝점 표시와 눈금이 없고, 11dp 점이 잡으면 16dp — 을 모든 자리가 쓴다. 재생·만화·문서의 쪽 슬라이더, 설정의 슬라이더,
미니 바·파일 목록의 읽던 쪽 막대·저장소 막대·파일 작업 막대·텍스트 뷰어의 색인과 찾기 진행이다. 끝을 모르는 진행(`progress = null`)은
3분의 1 길이의 조각이 흐른다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 점은 **그림만** 키운다(잡는 상자는 16dp 고정) | 함정 표의 'Material 3 의 `Slider` 는 트랙을 `constraints - 손잡이 너비` 로 잰다' — 상자를 키우면 잡을 때마다 막대가 흔들린다 |
| 진행 값은 그리기 단계에서 읽는다(`drawBehind` + 값을 주는 람다) | 색인·찾기·재생의 진행은 초에 여러 번 바뀐다. 값마다 재구성하지 않는다. NaN 은 0 으로 바꿔 읽는다(함정 표 'NaN 은 `coerceIn` 을 그대로 지나고') |
| 진행 막대는 화면 낭독기에 범위로 읽힌다(`progressBarRangeInfo`) | Material 의 막대가 주던 것을 잃지 않는다. 슬라이더는 Material 의 것을 그대로 쓰므로 설정의 줄이 주던 `stateDescription` 도 남는다 |
| 영상 위의 슬라이더는 남은 몫을 흰색으로 | 검은 영상·만화·문서 바탕에서 테마의 글자색은 안 보일 수 있다 — 부르는 쪽이 `inactiveColor` 를 준다 |

**블루투스가 연결되면 이어서 재생**(설정 '재생', 기본 켜짐). 판단은 `core:playback` 의 `BluetoothResume`(순수 클래스, JVM 시험)이 하고, 소리
장치 알림을 받아 넘기고 플레이어를 부르는 것은 `PlaybackService` 다. 설정은 `AppPreferences.resumeOnBluetooth` 이고 서비스가 흐름으로 들어
바꾸면 곧바로 듣는다. 끊길 때 멈추는 것은 media3 의 `setHandleAudioBecomingNoisy(true)` 다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| **끊겨서 멈춘 재생만** 기억한다(`PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY`) | 사용자가 멈춘 것을 장치가 붙었다고 틀면 안 된다. 그 사이에 사용자가 틀거나 멈췄거나 다른 항목으로 넘어갔으면 버린다 |
| 소리를 내는 블루투스만 센다(A2DP·LE 헤드셋·LE 스피커·보청기) | 통화용(SCO)만 붙은 장치로 음악을 이어 틀 까닭이 없다. 헤드셋은 둘을 함께 알리므로 섞여 오면 이어 튼다 |
| 1초 기다렸다 튼다 | 장치가 붙었다는 알림이 소리 경로가 그리로 넘어가기 전에 올 수 있다 — 첫 소리가 기기 스피커로 샌다. 그 사이 사용자가 무엇이든 했으면(틀기·멈춤) 틀지 않고, 틀기 직전에 소리 내는 블루투스 장치가 아직 붙어 있는지 본다(1초 안에 다시 떨어지면 스피커로 틀 뻔했다) |
| 끊긴 지 오래되어도 버리지 않는다 | 차에서 내렸다 다시 타는 것이 흔한 쓰임이다 |
| 처음 등록할 때 한꺼번에 오는 장치 목록 | 기억한 것이 없으므로 아무 일도 없다 |

**다른 앱에서 이 앱으로 열기**(`app` 의 `OpenActivity`, `core:io` 의 `IncomingFiles`). 메일·메신저의 첨부, 브라우저가 받은 것, 다른 파일
관리자의 '다음으로 열기'(VIEW)와 문서·압축·만화의 공유(SEND)가 온다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| **따로 된 액티비티**(`taskAffinity=""`, `autoRemoveFromRecents`) | 보낸 앱의 태스크에 쌓이고 닫으면 보낸 앱으로 돌아간다. `MainActivity` 에 필터를 달면 보낸 쪽이 새 태스크로 띄울 때 우리 파일 목록 위에 얹혀 뒤로 가기가 목록으로 간다 |
| 새 태스크로 다시 받으면 **맨 위의 화면이 받는다**(`singleTop` + `onNewIntent`) | standard 면 옛 화면 위에 쌓인다(함정 표 '`taskAffinity=""` 여도'). 맨 위의 화면이 앞 사본을 지우고 새로 받고, 요청 차례(`generation`)를 열쇠로 처음부터 다시 그린다 |
| 화면은 **같은 `Root`** 를 그 뷰어에서 시작한다(`Opened`) | 뷰어를 따로 이으면 `Root` 의 배선(스낵바 불변식·작업 결과·고지·압축에서 만화로)이 두 벌이 된다. 닫으면 끝난다 — 받은 압축에서 연 만화만 그 압축 목록으로 돌아간다. 파일 목록에 속한 일(권한 화면·알림 권한·휴지통 정리·푼 폴더로 가기)은 하지 않는다 |
| **제자리 먼저, 아니면 사본** | 뷰어가 전부 경로를 받는다(pdfium 은 되감는 서술자, 압축·만화는 임의 접근). 저장소 문서·내려받기·미디어 저장소는 경로를 정해진 모양으로 적어 제자리로 연다(모든 파일 접근 권한이 있을 때). 남의 제공자는 URI 안에 `/storage/…` 가 통째로 있고 이름·크기가 같을 때만 믿는다. 그 밖은 `cacheDir/share/inbox/<uuid>/` 에 복사한다. 이름이 종류를 말하지 않고 형식은 알면 **그 자리에서** 복사해 확장자를 붙인다(`file:`·content 둘 다) |
| 사본은 **읽기 전용**이고 화면이 끝나면 지운다 | 지우기·회전·풀기를 내지 않는다 — 보낸 쪽의 원본이 아니고, 캐시는 사용자가 찾아갈 수 없는 자리다. 공유·다른 앱으로 열기는 된다(`share/` 아래라 FileProvider 가 내준다). 메일의 명세서 같은 첨부가 캐시에 쌓이지 않게 지운다. 다 만드는 그 순간 경로를 넘기고(`onCopied`) 취소를 한 번 더 본다 — 받은 쪽이 끝났으면 거기서 지운다. 재생기에 넘긴 사본만 남기고 하루 뒤(그리고 프로세스가 설 때) 치운다 — 재생기가 지금 든 파일(`FileKey` 로 견준다)은 남긴다 |
| 받는 형식은 그림·소리·영상·글의 계열, 이 앱(과 편집기)이 여는 문서·압축·만화, `octet-stream`. **APK·글꼴은 받지 않는다** — 그래도 **설치 파일이 오면 열지 않는다**(이름이든 내용이든 — `refusalOf`, '14단계 뒤 — 보안 점검 고침') | 받으면 우리 '다른 앱으로 열기' 가 우리를 다시 고른다. 필터는 이름을 가리킨 인텐트에 듣지 않고(안드로이드 12) 이름을 바꾼 APK 는 다른 형식으로 온다 — 받은 APK 를 설치 관리자에 넘기면 보낸 앱이 이 앱의 설치 권한을 빌려 쓴다. 공유(SEND)는 문서·압축·만화와 `octet-stream` 만 받는다 — 사진·영상의 공유 목록은 메신저로 보내는 자리라 뷰어가 끼면 거슬리고, `text/plain` 을 받으면 브라우저의 주소 공유마다 이 앱이 선다. 옛 오피스·ODF 여섯(doc·xls·ppt·odt·ods·odp)도 VIEW·SEND 로 받는다 — 편집기가 보기 모드로 연다(`ReceiveFiltersTest` 가 편집기 형식 표와 견준다) |
| 우리를 **빼는** 자리 | `ExternalOpen` 이 곧바로 여는 길(시스템의 '다음으로 열기' 에서는 우리를 뺄 수 없어, 우리가 그 MIME 을 받으면 우리를 뺀 고르는 창으로 간다 — `ownTakes`, `.mts`·`.psd` 처럼 '기타' 인데 영상·그림 형식인 것)과 공유 창(`ShareHelper`) |
| 내보낸 두 액티비티는 **인텐트 extras 를 저장 상태로 넘기지 않는다**(`withoutIntentArgs`) | 함정 표 'AndroidX 의 ViewModel 은 인텐트 extras 를 …'. 되살린 경로는 입출력 스레드에서 한 번 더 본다(`IncomingFiles.stillUsable` — 사본이면 우리 사본 폴더 안, 제자리면 지금도 제자리). 권한자 앞의 사용자 번호(`0@`)는 떼고 견준다(`IncomingFiles.authorityOf` — 아니면 '우리 제공자 거절' 을 비켜 간다) |
| 작업 결과를 **앞에 있을 때만** 받는다 | 함정 표의 '`FileOpManager.results` 는 한 줄을 나눠 받는다'. 앞에 아무도 없는 동안 끝난 결과는 줄에 남았다가 돌아오면 뜬다. 지울 썸네일 키는 프로세스에 하나로 둔다(`BrowserViewModel` 의 companion — 다른 액티비티가 결과를 받아도 무효화를 놓치지 않는다) |
| 밖에서 넘어온 재생의 뒤로 가기는 **한 번** 태스크를 뒤로 보낸다(`PlayerSupport.open(…, fromOutside = true)` → `moveTaskToBack`) | 재생기에 넘긴 뒤 뒤로 가면 우리 파일 목록이 드러나지 않게 — 재생은 이어진다. 표시가 화면이 사는 내내 남으면 런처로 돌아온 사람도 뒤로 가기가 홈으로 튕긴다(아래 '`.ts` 되감기·재생 화면의 뒤로 가기' 절). 넘기기는 ViewModel 이 하고 끝내기는 액티비티가 기다린다(멈춘 화면의 컴포지션은 그 값을 보지 못한다) |
| 받는 일의 실패는 화면에 우리 문장으로 | 읽지 못함('받은 파일을 읽지 못했습니다')·공간 없음·넘긴 것이 없음('열 파일이 없습니다'). 남이 실은 extras 를 꺼내다 던져도, 제공자가 무엇으로 실패해도 앱이 죽지 않는다 |

**고치지 않은 것.** 받은 화면에서 제자리의 그림 하나를 지우면 목록이 비어 화면이 끝나고, 그때 뜬 '되돌리기' 알림도 함께 사라진다 —
파일은 휴지통에 있어 파일 목록의 휴지통에서 되살릴 수 있다. 남의 제공자가 답하지 않으면 입출력 스레드가 묶이고, 그런 받기를 여럿 띄우면
앱의 입출력이 멎을 수 있다(제공자 호출에 시간 상한이 없다 — 값이 작아 미뤘다).

### 14단계 뒤 — 글 고르기·ZIP 만들기

**글 고르기 — 뷰어마다 길이 다르다.**

| 뷰어 | 어떻게 | 왜 그렇게 했나 |
|---|---|---|
| 텍스트 뷰어 | 행 목록을 `SelectionContainer` 로 감싼다. 줄 번호는 `DisableSelection`. 길게 누르면 낱말, 손잡이로 넓힌다. 선택 상태는 `remember { SelectionState() }`(함정 표 — 저장되면 바인더 한도를 넘는다) | 안드로이드의 글 고르기 그대로다(손잡이·돋보기·떠 있는 막대·스마트 선택). compose-foundation 1.12 는 고른 행을 게으른 목록 밖에서도 **붙들고**(PinnableContainer) 손잡이를 끄는 동안 목록을 민다 — 바이트코드로 확인했다 |
| ″ 복사 | 받은 글을 쓰지 않고 **조각 목록으로 다시 잇는다**(`RowCopy`, `RowClipboard` 가 `LocalClipboard` 를 가로챈다) | Compose 는 `Text` 마다의 조각을 줄바꿈 하나로 잇는다(함정 표). 행의 글에 문자열 주석(표시의 자리·잇는 행)을 달고 `SelectionState.selectedTexts`(조각마다 주석이 살아 있다)로 잇는다. 빈 조각에도 주석이 남는 것은 JVM 시험이 박는다 |
| ″ '모두 선택' | 막대에서 **뺐다**(`filterTextContextMenuComponents`) | 이 목록은 창(400행)만 읽어 두어 그것이 고르는 것은 지금 그려진 행뿐이다. '모두' 라고 적힌 단추가 파일의 일부만 고르면 거짓말이다 |
| ″ 창이 옮겨 간 행 | 칸마다 마지막으로 그린 행을 든다(`HeldRow`) | 붙들린 행이 VM 의 창 밖이 되면 `rowAt` 이 null 이 되어 고른 글이 조용히 빠진다 |
| PDF | **우리가 쪽의 글자와 자리를 꺼낸다**(`docview/pdf/text` — 순수 코틀린, 11단계의 해석기 위). 길게 누른 자리의 낱말 → 손잡이 둘 → 시스템의 떠 있는 막대(`LocalTextToolbar` — 복사·모두 선택) | 레거시 `PdfRenderer`(API 34 이하 — 사용자의 기기)는 글자를 주지 않는다. 내용 스트림에서 글자의 자리를 정하는 연산자(`BT`·`Tf`·`Td`·`Tm`·`TJ`·`cm`·`q`/`Q`·`Do`)만 돌고 색·선·그림은 건너뛴다. 글꼴은 `/ToUnicode` → `/Differences`(글리프 이름) → 기본 인코딩 → 미리 정의된 CMap 의 문자 인코딩(`KSCms-UHC-H` 는 CP949 로 읽는다) 차례로 글자를 찾고, 폭은 `/Widths`·`/W` 로 잰다. 폼은 `Do` 를 부른 자리의 상태를 복사해 행렬만 바꿔 시작한다(명세 8.10.1). 누른 자리 찾기는 띄어쓰기를 건너뛴다 |
| ″ 좌표 | 보이는 쪽의 포인트(자르기 상자의 왼쪽 위, `/Rotate` 반영) → 가상 쪽의 비율 → 화면 | pdfium 이 쪽을 그리는 상자·방향과 같아야 상자가 글자에 앉는다. 화면 변환은 `ZoomableImage`·`SearchHighlights` 와 같은 식이다(`PdfSelectionMath`, JVM 시험). 손잡이 층은 제 `Box(fillMaxSize)` 안에 둔다(함정 표) |
| ″ 범위 | 쪽 하나 안에서만. 쪽을 넘기면 풀린다. 두드리면 풀린다(막대를 여닫지 않는다). 뒤로 가기가 먼저 푼다. 꺼낸 뒤 그 칸이 아직 자리 잡은 칸일 때만 세운다 | 쪽을 넘는 선택은 손잡이를 끌어 페이저를 넘기는 일과 겹친다(구글의 PDF 뷰어도 쪽마다다) |
| ″ 원본 | 평문 PDF 는 그 파일, 우리가 푼 암호 PDF 는 **메모리 파일의 복제 서술자**(문서가 닫을 때 닫는다 — 주인이 바뀌기 전까지 `finally` 가 닫는다), 소유자 암호만 걸린 파일은 글 꺼내기가 빈 암호로 푼다 | API 35 에서 **플랫폼이 직접 푼** 암호 문서는 평문이 우리 손에 없어 고를 수 없다 — '이 문서에서는 글을 고를 수 없습니다' |
| ″ 쪽 수가 다르면 | 고르기를 끈다 | 망가진 쪽 나무에서 pdfium 과 우리가 쪽을 다르게 세면 3쪽을 눌렀는데 4쪽의 글이 골라진다 |
| ″ 적대적 PDF | 푸는 중인 번호를 기억해 되돌아오면 '없다'(제 안을 가리키는 객체 스트림의 `/Length` — 경계는 `StackOverflowError` 도 잡고 글 꺼내기를 끈다), 못 푼 객체 스트림·글꼴도 기억한다. CMap 범위는 (길이, 시작) 차례로 늘어놓고 이분 탐색, 코드 4천 개마다 취소, 쪽 하나에 **30초**(`ParseLimits`). 쪽마다 푼 폼을 기억하고 해석한 바이트(256 MiB)와 푼 폼(96 MiB)을 묶는다. `/W` 값의 수를 표 전체로 묶는다(26만). CMap 은 스트림마다 한 번, 글꼴·CMap 캐시를 묶고 메모리가 넘치면 엔진을 놓는다. 한 번 실패하면 그 문서에서는 끈다(취소는 빼고) | 남이 지은 PDF 하나로 멎거나 메모리를 다 쓰는 길이었다(20만 범위 × 수백만 코드가 몇 시간, 같은 간접 배열을 수천 번 가리키는 `/W` 가 몇 KB 로 수십 GB). 셋은 고침을 되돌려 시험이 정확히 그것만 깨지는 것을 확인했다 |
| EPUB | **고칠 것이 없었다** — WebView 가 처음부터 길게 눌러 고르고 복사한다 | 기기에서 확인했다(가로쓰기·세로쓰기 모두). 링크 위를 길게 누르면 고르기가 시작되지 않는다 — 브라우저의 관례다 |

복사는 두 뷰어 모두 **20만 자**에서 끊는다(넘으면 넣지 않고 말한다) — 클립보드는 바인더로 건너가 1 MB 를 넘으면
`TransactionTooLargeException` 이고, Compose 의 복사는 그것을 잡지 않아 앱이 죽는다. '복사했습니다' 는 **안드로이드 12 이하에서만**
띄운다 — 13 부터는 시스템이 클립보드에 넣는 순간 스스로 보여 준다(공식 안내가 앱이 겹쳐 알리지 말라고 한다).

**ZIP 만들기**(`core:io` 의 `ZipMaker`, 큐의 `Request.Compress`). 파일 목록에서 고른 것들 → ⋮ → 'ZIP으로 압축' → 이름(하나면 그 이름, 여럿이면
지금 폴더의 이름) → 큐가 돈다(진행·취소·포그라운드 서비스는 복사와 같다) → '압축했습니다: 이름.zip'.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 플랫폼의 `java.util.zip` 을 쓴다 | 이름을 UTF-8 로 쓰고 그 표시(비트 11)를 켜고, 4 GiB·65,535개를 넘으면 ZIP64 로 쓰고, 확장 시각(0x5455, UTC)을 적는다. commons-compress 는 `format:archive` 에 있어 `core:io` 가 볼 수 없고 볼 필요도 없다(이음매를 만들지 않았다) |
| **이름은 끝에 맡는다**(`O_EXCL` — `NewFiles.place`) — 임시본에 다 쓰고 fsync 한 뒤 | 대화상자의 이름은 제안일 뿐이다. 묶는 동안 같은 이름이 생겼으면 `(2)` 가 붙는다(결과가 실제 이름을 싣는다). 처음에 맡으면 묶는 내내 0바이트 `.zip` 이 목록에 뜨고 누르면 '깨진 압축 파일' 이다. '이미 있다'(EEXIST)만 다음 이름으로, 그 밖의 오류는 그 까닭으로 말한다 |
| 링크·장치·소켓은 담지 않는다. 빈 폴더는 담는다 | 복사의 훑기와 같은 세 겹(링크를 따라가지 않는다·같은 inode 를 두 번 들어가지 않는다·깊이 64) |
| 열리지 않는 파일은 담지 않고 센다. **읽는 도중에 실패하면 전부 버린다**(끝낸 수 0 으로 말한다) | 쓰다 만 항목은 ZIP 에서 지울 수 없고, 그런 ZIP 은 푸는 쪽에서 'CRC 오류' 로 드러난다 — 백업을 믿은 뒤에야 안다 |
| 이미 압축된 형식(사진·영상·음악·압축·OOXML·PDF)은 **'저장'**(STORED) — 크기·CRC 를 먼저 재느라 두 번 읽는다 | 다시 압축해도 줄지 않으면서 폰에서 몇 배 느리다. 수준만 0 으로 바꾸는 길은 **계측 시험이 잡았다**(함정 표 '`ZipOutputStream.setLevel`') |
| 남은 공간이 64 MiB 밑이면 멈춘다(16 MiB 마다 본다) | 결과의 크기는 다 쓰기 전에 모른다(글은 줄고 영상은 그대로다). 미리 거절하지 않고 디스크를 0 까지 채우지도 않는다 |
| 담을 것이 없으면(링크만 골랐다) 만들지 않는다 · 항목(폴더 포함)이 **이 앱이 다시 열 수 있는 수**(`ParseLimits` 의 1만)를 넘으면 거절한다 | 빈 ZIP 은 쓸모가 없다. 같은 앱이 '너무 크다' 로 열지 못하는 ZIP 을 만들지 않는다 |
| 압축기의 네이티브 메모리를 닫는다 | 아래 파일을 열어 두는 감싸개(`KeepOpen`)로 ZIP 흐름을 닫는다 |

**암호를 걸 수 있다** — 이름 칸 아래의 '암호 (선택)'. WinZip AES-256 으로 잠근다(아래 '14단계 뒤 — AES 암호 ZIP').

**실물 말뭉치로 잰 글 꺼내기**(`PdfTextCorpusTest`, 오라클은 pypdf 6.17 — `samples-local/corpus/tools/oracle_pdf_text.py`). 읽을 수 있는 18
문서(스캔 + 보이지 않는 OCR 글·한컴 PDF·TeX·6,696쪽 법령집·Google Docs·LibreOffice·Word·소유자 암호 포함)에서 공백을 뺀 글자 세 개짜리
조각의 재현율·정밀도가 **1.0000**, 스캔(PDF-05)만 조각 하나가 달랐다(0.9999). 둘이 같은 차례(내용 스트림)를 따르므로 글자가 같다 —
띄어쓰기의 짐작은 이 대조가 재지 않고, 기기의 복사가 보였다. TeX 문서는 두 쪽 다 CFF 글꼴의 내장 인코딩을 읽지 않는다(같이 틀릴 수 있는 자리).

### 14단계 뒤 — 사진 잘라 저장·구간 저장

둘 다 **원본을 고치지 않고 곁에 새 파일**을 만든다(`이름_crop.jpg`·`이름_clip.mp4`).

**공통 — 파일 작업 큐를 지난다**(ZIP 만들기와 같은 까닭: 같은 폴더를 두 작업이 동시에 만지지 않게, 진행·취소·포그라운드 서비스·결과 보고).
임시본에 다 쓰고 fsync 한 뒤 **끝에** 없는 이름을 `O_EXCL` 로 맡는다 — `NewFiles.place` 한 곳이 차례를 정하고 셋(ZIP·잘라 저장·구간 저장)이
쓴다(먼저 맡으면 fsync 하는 동안 0바이트 파일이 보인다). 임시본은 `AtomicFileWriter.Staged`(내리기와 놓기를 가른 모양). 놓기는 `NonCancellable`
이고 놓인 것을 콜백으로 큐에 알린다(함정 표의 `withContext`). 결과 문구와 실패 까닭은 그것을 만든 `core:io` 의 `FileOpText` 에 한 벌 — 재생
화면도 같은 문장을 쓴다. 그릇을 **세우는** 실패만 형식 탓(`UNSUPPORTED`)이고, 쓰는 도중의 `IllegalStateException`·`Bitmap.compress` 의 false 는
남은 공간을 보고 '공간이 모자랍니다'·'입출력' 으로(`NewFiles.reasonWhenSilent`), 폴더가 없으면(SD 카드를 뺐다 — 사라진 폴더의 `usableSpace` 도
0 이다) '찾을 수 없다' 로 말한다.

**사진 잘라 저장.**

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 뷰어 ⋮ → '잘라서 저장' → 그림을 덮는 고르는 화면(`CropScreen`) | 관행(구글 포토·삼성 갤러리)을 따른다 — 그림 전체가 골라진 채 시작, 바깥은 어둡게, 삼등분 선, ㄱ자 모서리, 비율(자유·원본·1:1·4:3·3:4·16:9·9:16)과 '처음처럼', 고른 크기(원본 화소). 아래 깔린 뷰어에 손가락이 닿지 않게 빈 인식기를 단다(의미를 합치는 `clickable` 은 쓰지 않는다 — 함정 표). 고르는 동안 뒤의 페이저와 막대는 `clearAndSetSemantics`(화면 낭독기가 회전·지우기에 닿았다) |
| 셈은 순수 함수(`CropMath`) | 셈의 입구·출구에서 사각형을 그림 안에 둔다(`fit` — 실수 셈은 비율을 거치며 그림을 0.0001 넘긴다. 2560×1600 을 4:3 으로 옆 변을 끝까지 끌면 `coerceIn` 의 범위가 비어 앱이 죽었다 — 범위가 비면 던지지 않는 `clampTo`). 모서리가 변보다 먼저, 사각형이 손가락보다 좁으면 더 가까운 변, 손가락이 그 축의 범위 안이면 변의 손잡이를 변 길이의 넷째 몫까지만(`CropMath.grip` — 작은 사각형의 가운데를 끌면 옮기기다), 비율을 고정한 모서리는 맞은편을 붙박고 변은 가로지르는 축의 가운데를 지킨다(가장자리에서는 밀린다), 비율을 바꾸면 넓이를 지킨다(작게 고른 자리가 갑자기 그림 전체가 되지 않게 — 최소보다 작아진 사각형은 그 크기까지만 지킨다), 반올림은 짝수 쪽이 아니다. 무작위 2만 번 끌기 시험 |
| 뜨는 일은 `feature:image`(`ImageCropRenderer`), 쓰는 일은 `core:io`(`ImageSaver`) — **다섯 번째 이음매** `CropSupport` | 화면이 본 그대로 뜨는 셈(디코더가 EXIF 방향을 적용했는가 `ImageIo.probe`·`RegionOrientation`, 화면 방향 사각형을 저장 방향으로 `RegionMath.toStored`)은 `core:ui` 에 있고 `core:io` 는 그것을 볼 수 없다(의존 표). 사용자 파일을 쓰는 코드는 `core:io` 에만 둔다 |
| 원본 해상도로 — 확대 조각과 **같은 길**(영역 디코딩 + 방향) | 방향을 가를 수 없는 사진(180°·거울상·정사각에서 화소 판정도 못 한 것)과 영역 디코더가 없는 형식(GIF·BMP)은 **화면과 같은 디코더로 통째로 떠** 자른다(오른쪽·아래는 올림) — 화면이 본 것과 같다는 것이 먼저다. `setCrop` 으로 영역만 푸는 길은 쓰지 않는다 — 디코더가 안에서 통째로 푸는지 확인하지 못했다 |
| 결과 한 장의 상한 — 힙의 셋째 몫과 캔버스 100 MiB 가운데 작은 것 | 힙 등급 192 MB 에서 1,677만 화소(12MP 사진의 전체를 골라도 줄이지 않는다). 넘으면 2의 거듭제곱으로 줄이고 **결과가 말한다**('원본이 커서 W×H 로 줄였습니다') |
| 형식은 원본을 따른다(`ImageSaver` 머리말의 표) | JPEG 은 품질 95 로 다시 인코딩(무손실 MCU 자르기는 플랫폼에 없다), PNG·무손실 WebP(`VP8L` 청크로 안다)는 그대로, GIF·BMP 는 PNG, HEIC·AVIF 는 JPEG(새 의존 없이는 HEIF 를 쓰지 못한다), 투명한 것은 PNG. 아이콘의 MIME 은 `image/x-ico`(디코더가 그렇게 말한다) |
| EXIF 는 찍은 날·기기만 옮긴다 | 갤러리가 찍은 날로 묶는다. **위치는 옮기지 않는다**(잘라 낸 그림은 대개 남에게 보내려는 것이다), 방향도 옮기지 않는다(화소를 이미 세웠다) |
| 고를 때 본 치수를 함께 보내고, 큐가 돌 때 다르면 거절(`CHANGED`) | 회전 저장이 큐에 밀려 있으면 고른 자리가 저장될 때의 파일과 어긋난다. 같은 까닭으로 화면에서 돌려 둔 동안(아직 저장하지 않은 회전 값이 0 이 아닐 때) 메뉴가 꺼진다. 저장이 막 끝나 화면이 새 치수를 읽기 전에 연 자르기는 옛 치수를 보내고, 큐가 `CHANGED` 로 거절한다(결과는 안전하다) |
| 정지 그림만 | 움직이는 그림(첫 장면만 남는 것을 잘라 낸 그림이라 할 수 없다)·SVG·못 연 장, 그리고 다른 앱이 넘긴 읽기 전용 사본은 메뉴가 없다 |

**구간 저장.**

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 재생 화면 ⋮ → '구간 저장' → 조작부 안에 한 칸(`ClipControls`) | 시트로 띄우면 진행 막대가 가려진다 — 막대를 끌어 자리를 찾고 듣다가 '시작을 여기로'·'끝을 여기로' 를 누르는 것이 이 일의 전부다(위치는 티커가 미는 상태가 아니라 컨트롤러에서 곧바로 읽는다 — `PlaybackConnection.positionNow`, 아니면 500 ms 늦은 자리를 잡는다). 고른 구간은 막대에 띠로(A-B 표시와 같은 자리). 처음 구간은 찍어 둔 A-B, 없으면 지금 자리부터 끝까지. '시작부터 재생' 으로 들어 본다. 짧으면 '끝이 시작보다 뒤여야 합니다(0.5초 이상)' |
| **누르기 전에 말한다**(`MediaClipper.preview`) | 영상이면 실제로 담기는 키프레임의 시각을, 담을 수 없는 형식이면 까닭을 말하고 저장 단추를 끈다 — 기기에서 고른 시작 0:30.1 이 0:20.8 부터 담기는 것(키프레임 간격 약 10초)과, 소리가 FLAC 인 MKV 가 누른 뒤에야 거절당하는 것을 보고 더했다 |
| **다시 인코딩하지 않는다** | 화질·음질이 그대로이고 폰에서도 빠르다. 대가는 자를 수 있는 자리가 형식마다 정해져 있다는 것이다(`MediaClipper` 머리말의 표) |
| WAV·MP3·FLAC 은 우리가 자른다(`core:io` 의 `clip/`, 순수 코틀린) | 플랫폼 `MediaMuxer` 가 셋 다 쓰지 못한다. WAV 는 표본 경계(`fmt `·`LIST` 를 옮기고 `data` 를 자른다, PCM 만), MP3 는 프레임 경계(ID3v2·ID3v1 은 옮기고 Xing·Info·VBRI 는 자른 것의 프레임 수·바이트 수·**목차**로 다시 쓴다 — 그대로 두면 길이가 원본 길이로 보이고, 목차가 없으면 media3 의 `XingSeeker` 가 탐색하지 못한다. `TLEN` 도 자른 길이로), FLAC 은 프레임 경계(머리의 번호를 0 부터 다시 매기고 CRC-8·CRC-16 을 다시 계산한다 — 그대로 두면 재생기가 위치를 1:23 부터로 그린다, STREAMINFO 는 표본 수·프레임 크기를 새로·MD5 는 0, 주석·그림은 옮기고 목차는 버린다). MP3 는 고른 끝까지만 훑고 고른 구간의 프레임만 기억하며 4,096 프레임마다 취소를 본다. FLAC 의 프레임 목록은 자리·길이 두 수의 원시 배열이다(프레임마다 객체면 긴 파일에서 수백 MB) |
| FLAC 프레임의 끝은 **세 겹으로** 확인한다 | 길이가 적혀 있지 않다. 다음 동기 부호의 머리가 CRC-8 로 맞고, 같은 흐름이며, 그 앞까지의 CRC-16 이 맞아야 끝이다(소리 데이터 속의 우연한 `FF F8` 에 속지 않는다 — 시험이 일부러 심는다. 함정 표 '이어 붙인 두 FLAC 프레임'). 목차가 있으면 고른 곳 앞으로 건너뛰고, 없으면 처음부터 훑는다. 잘린 메타 머리는 '저장할 수 없다', 끝의 태그 꼬리(ID3v1·APE·ID3v2) 앞을 마지막 프레임의 끝으로 본다 |
| 나머지는 `MediaExtractor` + `MediaMuxer`(`clip/MediaRemux`), 그릇은 **코덱이 정한다**(`RemuxPlan`) | MP4·3GPP(H.264·H.265·MPEG-4·H.263 + AAC·AMR), WebM(VP8·VP9 + Opus·Vorbis, 영상 하나·소리 하나), Ogg(Opus). MKV 의 H.264+AAC 는 MP4 로, Ogg Vorbis 는 WebM 으로, TS 의 H.264+AAC 도 MP4 로 담긴다(확장자가 바뀐다). 영상이 없는 그릇은 다시 담지 못하면 소리 뽑기로 저장한다(MKA 의 FLAC·AC-3, 소리만 든 TS 의 MP2) |
| **MPEG-TS·PS 는 처음부터 읽는다**(`MediaRemux.seeksUnreliably`) | 플랫폼 추출기의 되감기를 믿을 수 없다(함정 표 — 소리만 고르면 되감지 않고, 영상과 함께 되감으면 시간축을 다시 세운다). 키프레임은 따로 연 추출기로 처음부터 걸어 찾고, 고른 시작 앞의 표본은 쓰는 고리가 버린다. 대가는 시간이다 — 긴 녹화의 뒤쪽을 고르면 그 앞을 다 읽는다(미리 보기의 키프레임도 걸어서 잰다). TS 의 AAC 는 ADTS 머리를 떼고 담는다(`Adts.split` — 표본 하나에 프레임이 여럿이다, 함정 표). 동기 부호·층이 틀리거나, 길이가 머리보다 짧거나 표본을 넘거나, 끝에 바이트가 남거나, 날것 블록이 여럿이면 '저장할 수 없다' |
| 영상은 고른 시작 바로 앞의 키프레임부터, 소리 트랙은 그 시각부터 | 키프레임이 아닌 프레임은 앞 프레임을 참조한다(첫 표본이 키프레임인지 확인한다). 끝은 B 프레임 때문에 다음 키프레임(또는 2초)까지 보며 표시 시각이 끝보다 앞인 것만 담는다. 끝이 길이에 닿으면 '끝까지'(null)로 보낸다 — 재생 화면의 길이는 트랙의 끝보다 몇 ms 짧을 수 있다 |
| 소리가 있는 영상에서 담을 소리가 하나도 없으면 거절 | 말없이 소리 없는 영상을 만들지 않는다. 담지 못하는 **나머지** 트랙(두 번째 소리의 AC-3 등)은 빼고 그 수를 결과가 말한다 |
| 회전(`rotation-degrees`)을 옮긴다 | 휴대폰 세로 영상이 누워서 나오지 않게(계측 시험이 90 을 확인한다) |
| 결과는 재생 화면이 받는다(`ResultSink` + `ClipJobs`, 함정 표) | 앱의 결과 소비자는 다른 액티비티에 있다. 결과는 줄로 받아 차례로 말하고(값 하나면 잇달아 끝난 둘이 앞의 것을 덮는다), 앞에 있을 때만 받으며 물러나면 받았지만 말하지 못한 것을 앱의 결과 줄로 돌려보낸다(파일 목록이 말한다). 저장의 결과는 맨 뒤 차례이고, 지우는 쪽은 그사이 새 안내가 왔으면 지우지 않는다. 칸은 **자기가 넣은 요청의 결과에만** 닫힌다. 넣은 요청의 신원을 들어 앞 작업에 밀려 줄 선 동안 '앞의 파일 작업이 끝나면 저장합니다' 를 보이고 저장 단추를 끈다. 진행은 큐의 상태를 칸 아래의 진행 줄이 스스로 읽는다(재생 화면 전체가 다시 그려지지 않게) |
| 칸의 '취소' 는 **그 요청 하나만**(`FileOpManager.cancel(id)`) | 큐가 줄 선 요청을 신원으로 들고(`waiting`) 그 자리에서 '취소' 를 낸다. '지금 작업 취소' 는 화면에 보이는 그 작업(`State.Running.id`)만 끊는다. '전부 취소' 는 세대(`epoch`)를 올리고 줄을 비우며 받는 자리가 있는 요청에 '취소' 를 준다(워커가 막 꺼낸 칸도 세대로 거른다). 도는 잡·신원·먼저 온 취소는 잠금 하나 아래에서만 읽고 쓴다. 시작하기 전에 취소된 작업도 큐가 '취소' 를 낸다(블록이 돌지 않으면 아무도 결과를 내지 않는다) |
| 다른 앱이 넘긴 사본(앱 캐시)은 저장하지 않는다 | 곁에 써도 사용자가 찾아갈 수 없는 자리다 |

**소리만 저장**(같은 날 사용자 요청 '비디오 편집 기능을 하나 더 — 오디오만 추출하여 별도로 저장').

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 재생 화면 ⋮ → '소리만 저장'(영상일 때만) — **소리 트랙 전체**. 구간 칸에도 '소리만 저장'(고른 구간의 소리) | 가장 흔한 쓰임은 '이 영상의 소리를 파일로' 다 — 구간부터 고르게 하면 처음과 끝을 손으로 맞춰야 한다. 끝까지는 끝을 **null** 로 보낸다(재생 화면의 길이는 트랙의 끝보다 몇 ms 짧을 수 있어 그 값을 넣으면 끝 프레임을 잃는다) |
| **다시 인코딩하지 않는다** — 소리의 그릇만 바꾼다(`core:io` 의 `clip/AudioExtract`, 그릇은 `RemuxPlan.AudioOut`) | AAC → `.m4a`, AMR → `.3ga`, Opus → `.opus`, Vorbis → `.weba`(여기까지 플랫폼 `MediaMuxer` — 다시 담기와 같은 길), MP3 → `.mp3`, MP2 → `.mp2`, AC-3·E-AC-3 → `.ac3`·`.eac3`(프레임이 머리를 품어 이어 붙이면 되는 흐름이다), FLAC → `.flac`(그릇의 머리 csd-0 에서 `fLaC`·STREAMINFO 를 세우고 번호를 0 부터 다시 매긴다), PCM → `.wav`(머리를 앞에 두고 크기는 끝에서 고친다). 그래서 **영상째로는 담을 수 없는 MKV 의 FLAC·PCM·AC-3 소리도 뽑힌다** — 구간 칸이 '영상째로는 저장할 수 없는 형식입니다 · 소리만 저장할 수 있습니다' 를 말한다 |
| MP3 는 Xing·Info 머리 프레임을 **지어** 앞에 둔다(프레임 수·바이트 수·목차) | 가변 비트율 MP3 에 그것이 없으면 재생기가 첫 프레임의 비트율로 길이를 어림해(무음으로 시작하는 곡은 첫 프레임이 작다) 길이와 탐색이 크게 틀린다. 같은 판·표본율·채널로 태그가 들어갈 만큼 큰 비트율의 무음 프레임을 짓는다(LAME 의 Info 와 같다 — 자르기의 `XingInfo` 와 한 벌) |
| FLAC 표본 하나에 프레임이 여럿이면 나눠 쓴다 | 이어 붙인 두 프레임은 CRC-16 이 그대로 맞는다(함정 표). 경계를 못 찾으면 거절한다 — 짐작으로 이어 붙인 FLAC 은 깨진 파일이다 |
| 고른 시각을 **품은** 프레임에서 시작한다(`MediaRemux.seekAtOrBefore`, 그리고 쓰는 고리의 들고 가기) | MKV 추출기의 되감기는 뒤로 간다(함정 표). 영상 구간 저장도 같은 도우미를 지난다. 되감기는 짐작으로만 쓰고 시작은 쓰는 고리가 정한다 — 고른 시각 이하의 마지막 표본을 들고 가다 넘는 표본이 오면 그것부터 쓴다(되감기가 들었든 아니든 같은 결과다 — 영상이 있는 TS 의 소리가 0:00 부터 뽑히던 것도 이것으로 막혔다). TS·PS 는 아예 되감지 않는다 |
| 소리가 여럿이면 **쓸 수 있는 첫 트랙** 하나 | 나머지 수를 결과가 말한다('· 다른 소리 트랙 N개는 담지 않았습니다'). 쓸 수 없는 갈래(머리 없는 FLAC·빅 엔디언 PCM)는 고르기에서 빠지되 수에는 든다 |
| 소리 트랙이 없으면 그렇게 말한다(`Reason.NO_AUDIO` — '소리 트랙이 없는 영상입니다') | '이 형식은 저장할 수 없다' 로 말하면 형식 탓으로 읽힌다. 구간 칸은 미리 보고 단추를 끄며 까닭을 덧붙인다 |
| 진행은 **시각으로** 잰다 | 영상 파일의 크기로 어림하면 소리만 뽑을 때 진행이 몇 %에서 끝난다. 다시 담기(영상)도 같은 셈이다 |
| 이름은 `이름_audio.확장자`, 결과는 구간 저장과 같은 길(큐 → `ClipJobs`) | 구간을 저장하면 칸을 닫고, 소리만 저장했으면 열어 둔다 — 같은 구간의 영상도 저장할 수 있다. 확장자 표에 `.mp2` 가 있다(방송 녹화의 소리). 고르는 줄과 저장하는 줄을 갈랐다(한 줄이면 '소리만 저장' 이 화면 밖에 걸린다) |

### 14단계 뒤 — `.ts` 되감기·재생 화면의 뒤로 가기

**`.ts` 되감기** — 브라우저가 HLS 조각을 이어 붙인 TS 는 끝에 PCR 이 없어 media3 가 '되감을 수 없는' 것으로 보았다(함정 표 'media3 의 TS
추출기는 …'). `PlaybackService` 가 PCR 을 찾는 범위를 188 × 5,600 바이트로 넓힌다 — 추출기의 나머지 설정은 기본값 그대로다
(`DefaultMediaSourceFactory` 가 스스로 만드는 `DefaultExtractorsFactory()` 와 같다). 같은 값이 되감기의 한 걸음이 훑는 양이다(3.38 GB 실물에서
새 자리까지 0.4~0.7초).

**재생 화면의 뒤로 가기** — 다른 앱이 넘긴 파일로 연 재생 화면의 표시(`fromOutside` — 뒤로 가면 태스크를 뒤로 보내 보낸 앱으로)는 한 번만
듣고, 재생 화면이 태스크의 뿌리일 수 있다(함정 표). 런처로 돌아온 사람의 뒤로 가기가 홈으로 튕기지 않게 아래처럼 한다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 표시는 **한 번뿐**(`PlayerActivity.forgetSender`) | 뒤로 가기로 보낸 앱에 돌아갔거나, 홈·최근 앱으로 떠났거나(`onUserLeaveHint`), PiP 에 들어갔으면(모드 콜백 — 자동 PiP 는 `onUserLeaveHint` 를 부르지 않는다) 거둔다. 그 뒤에 돌아온 사람은 보낸 앱에서 오지 않았다. 우리가 띄운 앱('다른 앱으로 열기' — 우리 태스크에 얹혀 뒤로 가면 이 화면이다)은 떠난 것으로 치지 않는다(`handingOff`). 화면이 꺼진 것은 콜백이 오지 않는다 |
| 다시 선 화면은 **저장 상태**를 따른다 | 밤 모드를 바꾸거나 프로세스가 죽었다 살아나면 처음 인텐트(표시가 켜진 것)를 그대로 받는다 — 거둔 표시가 되살아나지 않게 |
| 뒤로 가기는 **한 곳**(`goBack`) | 표시가 있으면 보낸 앱으로, 밑에 파일 목록이 있으면 끝내기, **뿌리면 지금 트는 파일의 폴더로 파일 목록을 열고 끝내기**. 시스템의 뒤로 가기는 할 일이 '끝내기' 뿐이면 꺼 둔다(여느 뒤로 가기가 돈다). 폴더는 `/storage/` 아래의 파일일 때만 — 앱 캐시의 사본(다른 앱이 넘긴 것)은 첫 화면이다 |
| 파일 목록을 여는 일은 **세 번째 이음매에 함수 하나**(`PlayerSupport.FileListOpener`) | 파일 목록은 `app` 의 것이다. `app` 의 `FileListLauncher` 가 꽂는다 — 이 앱의 **다른 태스크에** 파일 목록이 살아 있으면 그 태스크를 앞으로 가져오고(최근 앱에 이 앱이 둘이 되지 않게 — PiP 는 원래 태스크로 돌아오므로 그 길을 만들지 못했다, 코드만), 없으면 재생 화면의 태스크에 연다. 그 파일 목록은 이름만 가리킨 인텐트로 연다(함정 표 '재생 화면이 태스크의 뿌리일 수 있다') |
| 폴더는 **프로세스로 건넨다**(`FileListRequests`) | 내보낸 `MainActivity` 의 extras 로 싣지 않는다 — 남의 앱이 같은 extras 로 파일 목록을 아무 폴더에서나 열게 할 수 있다(`withoutIntentArgs` 와 같은 판단). 파일 목록(`Root`)이 앞에 섰을 때 꺼낸다. 같은 폴더를 두 번 청해도 두 번 오게 요청은 인스턴스다(함정 표 '`MutableStateFlow` 는 같은 값을 합친다') |

### 14단계 뒤 — 재생 화면·파일 목록·읽는 화면 다듬기

| 갈래 | 지금의 동작 |
|---|---|
| 재생 화면 | **시스템 바** — 영상이 보이고 조작부가 숨으면 두 바도 숨는다(가장자리를 밀면 잠깐). `PlayerActivity.holdSystemBars` 가 `repeatOnLifecycle(STARTED)` 로 쥐고 `onStop` 에서 놓는다(함정 표 '액티비티의 설정을 컴포저블이 쥐게 하지 마라'). 그래서 '조작부가 떠 있는가' 를 액티비티가 든다(`controlsShown`, 판정 `PlayerChrome.barsHidden`). 영상이 보이는 동안 `enableEdgeToEdge(SystemBarStyle.dark)`(함정 표), 판·재생목록의 인셋은 `systemBarsIgnoringVisibility ∪ displayCutout`. **조작부가 3.5초 뒤 저절로 숨는다** — 손가락이 닿은 동안(Initial 단계에서 보기만)·막대를 끄는 중·⋮·트랙 시트·배속 눈금·구간 칸·저장 중·PiP·TalkBack·멈춤은 빼고, 조건이 풀리면 처음부터 잰다. 판은 `only(Bottom + Horizontal)` 이고 자막을 판 높이만큼 올린다(`SubtitleLayer(bottomLift)`). 막대를 끄는 동안 갈 자리의 시간을 강조색으로, 화면 낭독기에 '재생 위치, 1분 23초 / 45분'(`SpokenTime`), 재생목록 줄에 '재생 중·선택됨'. 판 왼쪽 위의 뒤로 단추. 큰 재생기의 관행 넷 — 두 번 두드림 기본 **10초**(5·10·15·30, `tapSeekSeconds`), 터치 잠금(자물쇠 — 방향 잠금은 '도는 폰' 아이콘, 잠금 중에는 몸짓이 꺼지고 두드리거나 뒤로 가면 '터치 잠금 풀기' 알약이 2.5초), 가운데 큰 재생 단추, 알림이 `showPlaylist=false` 를 싣는다(아래 '결정'). **프로세스가 죽은 뒤** — `PlayerRestore` 가 저장 상태에 큐의 경로(지금 항목을 앞 1/4 에 두고 최대 100)·자리·위치를 적고, 다시 서면 **멈춘 채** 그 위치로 세운다(자막 후보는 그 폴더에서 다시 고른다). 못 세우면 토스트와 그 폴더의 목록. media3 의 재개 장치와 이어보기 기록은 디스크에 경로 목록을 남겨야 해서 쓰지 않았다. 구간 저장은 공유 폴더(`ShareHelper.shareDir`) 아래를 모두 거절한다(`ClipSource.isAppCopy`) |
| 파일 목록 | 이름 바꾸기·새 폴더가 있는 이름이면 칸을 닫지 않고 칸 안에 '같은 이름이 이미 있습니다'(`FileOpEngine.Reason.EXISTS` — `reserveName` 이 까닭을 돌려준다). 휴지통의 여럿 고르기(길게 누르기·전체 선택 토글)와 되돌리기 한 요청(`Request.Restore(uuids)`·`TrashStore.restoreAll` — 스낵바 '되돌리기' 도 결과 한 줄), 휴지통 썸네일(그림·영상만, `persist=false`). 압축 화면의 '전부 풀기' 는 아래 막대의 단추. 검색 키가 자판을 내린다(목록·압축). 목록·격자의 끝 여백을 떠 있는 단추 묶음의 높이만큼(함정 표의 `contentPadding`). 전체 선택은 다시 누르면 모두 푸는 토글(`SelectAllToggle`). 사진 뷰어의 정제 공유 실패를 그 자리에서 말한다. 비밀번호 칸(`PasswordDialog` — 압축·만화·PDF)에 눈 단추(`ui/icon/Visibility.kt`) |
| 읽는 화면 | **PDF 를 세로로 이어 본다(기본)** — 줄 하나 = 쪽 하나(두 쪽 보기면 펼침), 배율은 문서 전체에 걸린다(`PdfScroll.kt`·`ScrollDocument`). 바닥층은 폭 맞춤 한 장이고 그 상한은 `ImageLimits.scrollPageCap`(살아 있는 몫의 1/3 — 긴 줄 셋이 든다), 확대는 목록 전체의 `graphicsLayer` 이고 바닥층 배율을 넘은 만큼 **보이는 자리만** 다시 그린다(`PdfLimits.regionTile`, 선명화 몫은 보이는 넓이대로 나눈다). 렌더 스레드는 하나, 손가락이 멎은 뒤 180ms. ⋮ 의 '한 쪽씩 넘겨 보기 / 세로로 이어 보기'(`pdf_continuous`). 쪽 번호·슬라이더·쪽 목록·쪽으로 가기·이어보기·찾기·글 고르기가 두 보기에서 같다. **뷰어 넷(그림·만화·PDF·문서)의 시스템 바** — 막대를 감추면 바도 감춘다. 화면은 `core:ui` 의 `ViewerWindow` 에 바람만 적고 `app` 의 `ViewerWindowHost` 가 `repeatOnLifecycle(STARTED)` 로 걸고 `onStop` 에서 놓는다(`MainActivity`·`OpenActivity` 둘 다). 두 액티비티는 `IroiroTheme(systemBarIcons = false)` — 테마가 회전마다 아이콘 색을 덮었다. **읽는 동안 화면 켜 두기** — 만화·문서·글이면 마지막 조작(`onUserInteraction`) 뒤 정한 시간까지 `FLAG_KEEP_SCREEN_ON`(0·2·5·10·30분, 기본 10, 상한 60). 화면의 **종류**(`ViewerScreens.reading`)로 정한다. **만화** — 가장자리 30% 두드림이 넘기기(`EdgeTaps` — 오→왼이면 좌우가 뒤집히고, 확대 중에는 어디를 두드려도 막대, 웹툰은 화면 80% 씩), 첫·끝 쪽에서 더 넘기면 '첫 쪽입니다'·'마지막 쪽입니다', 볼륨 키(`comic_volume_keys`, 기본 끔 — `onKeyDown`·`onKeyUp`, 함정 표). **EPUB** — 흐르는 장의 끝에서 **새로** 72dp 넘게 당겨 놓으면 다음 장, 처음에서 거꾸로 당기면 앞 장의 **끝**(`ReadingTurn` + `ReaderWebView`, `vm.openAtEnd`). 고정 레이아웃 쪽은 좌우 밀기(48dp)와 가장자리 30% 두드림(링크를 누른 것·확대 중은 빼고). 모든 장이 고정 레이아웃인 EPUB 은 '쪽' 으로 센다('N / M 쪽'·'N쪽부터'). 쪽 슬라이더의 화면 낭독 '쪽, N쪽 / M쪽'. **확대한 채 가장자리에서 새로 밀면 넘긴다**(`ZoomState.claimsDrag` + `trackZoomDrags`) |
| 구간 저장·잘라 저장 | TS 의 PES 하나에 든 AAC 여럿(`Adts.split` — 함정 표). 목차 없는 MKV 의 끝 너머 되감기(-1). 훑는 사이 줄어든 파일에서 `WindowReader` 가 멎지 않게. 고른 구간의 빈 소리 트랙 — 첫 소리가 비면 거절(`NO_AUDIO_IN_RANGE` '고른 구간에는 소리가 없습니다 — 소리 없는 영상은 만들지 않습니다'), 둘째 뒤의 빈 트랙은 빼고 센다. 긴 MP3·FLAC 의 메모리 — MP3 는 이어진 구간 + 성긴 목차(`SparseOffsets`, 4,096칸, 차면 반으로), FLAC 은 프레임 길이만(4바이트)이고 메타 블록은 쓸 때 원본에서 다시 읽는다. 프레임 상한·계획 중 메모리 부족은 `RANGE_TOO_LONG`('고른 구간이 너무 길어 저장할 수 없습니다 — 더 짧게 고르세요'). 잘라 저장의 EXIF 날짜 쓰기(함정 표 — 꼬리 검사) |
| 압축·HTML·텍스트 | LZ4 빈 프레임의 재귀(함정 표 — 빈 프레임은 빈 내용이다, `lz4 -d` 와 같다). tar 크기의 넘침(뺄셈·`saturatingAdd`, 2^62 를 넘으면 깨진 머리), ar 의 긴 이름 표(이름 하나 64 KiB, 항목이 아닌 멤버 16), gzip 목록은 이름·주석을 4,096바이트까지만. **판별을 시간(5초)이 아니라 소비한 입력으로 끊는다** — 앞 512바이트 전 4 MiB, 빈 tar 의 0 꼬리 16 KiB, LZ4 프레임 하나에 4 KiB(`ProbeLimitException`) — 같은 파일이면 어디서 열어도 같은 답이다. PATH_MAX 를 넘는 이름은 그 항목만 거절(`ExtractNames.inside`), `ArchiveTree` 의 재귀를 고리로(3만 층이 256 KiB 스택에서 0.08초). 글 고르기 막대의 보내기(함정 표 — `RowSend`). 위생기 — `<svg>`·`<math>` 안의 `<style>` 을 본문 규칙으로 풀어(`CharRefs.decodeText`) 위생한 뒤 `&` 를 `\26 ` 으로, SVG 표현 속성 열(`fill`·`stroke`·`filter`·`mask`·`clip-path`·`marker*`·`cursor`)을 CSS 선언처럼, CSS 조각 참조는 퍼센트를 푼 경로가 SVG 면 `none`. `.xhtml` 은 XML 의 인코딩 차례(BOM > 고른 것 > XML 선언 > 판정, meta 는 보지 않는다). **텍스트 뷰어의 기본값을 글 파일·코드 파일로 따로**(`text_prose_*` 넷 — 글은 처음에 줄을 접고 줄 번호를 숨긴다. 갈래는 목록의 표 `MimeResolver.kindOf`) — 옛 값은 글자 크기·줄 끝 표시를 물려받고, 어느 갈래든 처음 쓸 때 글의 값을 먼저 적어 둔다. 두 손가락으로 글자 크기(9–22sp, 갈래마다 저장) |

**결정.**

- 알림을 누르면 화면의 기본값이 정한다(`showPlaylist=false`) — 영상 큐는 영상, 소리만 도는 큐는 목록(다음 곡을 보려는 사람이 대부분이다).
- 두 번 두드림의 기본값이 10초이고 설정에서 고른다(5·10·15·30).
- 확대한 채 가장자리에서 **새로 시작한 끌기**는 넘긴다(한 끌기 안에서는 넘기지 않는다).
- PDF 는 세로 이어 보기가 기본이다.
- 되살리기에 실패하면 토스트를 띄운다 — 앱의 첫 토스트다(화면이 곧 사라져 스낵바를 띄울 자리가 없다).
- 새 설정 — `tap_seek_seconds`(10), `reader_keep_screen_minutes`(10), `comic_volume_keys`(끔), `pdf_continuous`(참), `text_prose_*` 넷.
- 수치 — 조작부 숨김 3.5초, 가장자리 30%, 당겨 넘기기 72dp, 밀기 48dp, 화면 켜 두기 기본 10분.
- **문구 규칙** — 조사는 붙여 쓴다(라틴어·숫자 뒤도: `PDF는`·`1~65535입니다`). 변수 뒤에는 받침과 무관한 조사(에·의·부터·까지)만 붙이고,
  그 밖은 이름을 따옴표로 앞에 두고 문장과 가른다(`‘%1$s’ · 목록에서 지웁니다`). 압축 용어는 '연속 압축'. 글자 크기에 단위(sp)를 적지 않는다.

**사용자가 정할 것.** `PROCESS_TEXT` 를 매니페스트의 `<queries>` 에 적을지 — 적으면 글 고르기 막대의 보내기('번역' 등) 목록이 넓어지지만, 이 앱이
글을 받는 앱으로 무엇이 깔려 있는지 알게 된다(사생활과 맞바꾸는 일). 지금은 적지 않았다 — 보이는 앱은 Compose 가 예전에 달던 것과 같다(`RowSend`).

### 14단계 뒤 — 글 편집·새 글 파일·그리기와 메모

**글 편집**(`feature:text` 의 `TextEditScreen`·`TextEditViewModel`, 쓰기는 `core:io` 의 `TextSaver`).

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 뷰어 위 막대의 연필(`Icons.Filled.Edit` — 코어 세트에 있다) | 고칠 수 없는 파일(`/storage` 밖·받은 사본·휴지통·보통 파일 아님·쓸 수 없음·다시 쓸 수 없는 인코딩)에서는 숨긴다. 크기 때문이면 단추는 두고 누를 때 '이 파일은 커서 이 앱에서 고칠 수 없습니다 — 256 KB까지' 를 말한다(판단은 `TextEditGate`, 저장할 때 다시 보는 마지막 관문은 `TextSaver.placeOf`). 256 KiB 인 까닭은 함정 표('`BasicTextField` 는 글자 하나를 칠 때마다') |
| 편집기는 **뷰어의 모드가 아니라 따로 된 화면**(`AppScreen.Edit` — 코덱 `e:<t\|b>:<인코딩>:<경로>`) | 닫으면 뷰어로 돌아간다('새 글 파일' 로 열었으면 목록으로). 저장 뒤에는 뷰어가 다시 읽는다(`TextSaves`) |
| 인코딩·BOM·줄 끝·마지막 줄바꿈을 지킨다 | 편집기 안의 줄 끝은 `\n` 하나(붙여 넣은 CR 은 입력 변환이 걷는다), 쓸 때 원래 것으로. 섞여 있으면 저장 전에 묻고 가장 많은 것으로 맞춘다(같으면 먼저 나온 것, 없으면 LF). 쓸 수 없는 글자가 있으면 그 글자를 골라 보이고 'UTF-8로 바꿔 저장 / 취소'. 다시 쓰면 같은 바이트인 파일만 연다(함정 표 'JDK 와 ICU') |
| **세션의 첫 저장 때 원본 사본을 휴지통에**(`TrashStore.copyToTrash`) | 기록까지 남겨야 성공으로 친다. 못 넣으면 '그래도 저장 / 취소'. 빈 파일은 넣지 않는다. 되살리면 지금 파일과 겹치지 않게 `메모 (2).txt` |
| 연 뒤 (크기·수정 시각)이 바뀌었거나 지워졌으면 '덮어쓰기 / 취소' | 밖에서 고친 것을 모르고 덮지 않게(`FileStamp`, 까닭 `CHANGED_ELSEWHERE`·`PREVIOUS_NOT_KEPT`) |
| 쓰기는 파일 작업 큐(`Request.SaveText`, `Kind.SAVE_TEXT`) → `AtomicFileWriter` | 같은 폴더를 두 작업이 동시에 만지지 않게. 결과는 편집기가 곧바로 받는다(`TextSaveJobs`), 문장은 `FileOpText.textMessage` |
| 초안 `filesDir/text-drafts/<경로의 SHA-256>.draft` | 글이 바뀐 뒤 2초 쉬면·`ON_STOP`·화면을 떠날 때 적는다. 같은 편집 세션으로 다시 열리면(프로세스가 죽었다 살아난 것) 묻지 않고 잇고, 다른 때 남은 초안이면 '이어서 고치기 / 버리기'. 30일 넘은 초안은 치운다 |
| 되돌리기·다시 하기(Ctrl+Z·Y), 찾기·바꾸기(대소문자, 모두 바꾸기는 되돌리기 한 번), Ctrl+S | 글자 크기·줄 접기·줄 번호는 뷰어의 그 갈래 설정을 따른다(함정 표 — 실험 API·초점 없는 칸의 스크롤) |
| **+ 단추가 메뉴다** — 새 폴더 / 새 글 파일 / 오피스 문서 넷 | 새 글 파일의 처음 이름 '새 글.txt'(있으면 '새 글 (2).txt'), 만들기는 큐를 지난다(`Request.CreateText`, `Kind.NEW_TEXT`, `O_EXCL`), 만들면 곧바로 편집기가 자판과 함께 열린다. 새 폴더는 곧바로 만든다. 오피스 문서는 서식을 옮겨 적고 편집기가 연다(아래 '…뷰어 교체') |

**그리기·메모**(`feature:image` 의 `Markup*` — 뷰어 ⋮ 의 '잘라서 저장' 아래).

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 도구 다섯 — 펜(2·4·8dp)·형광펜(12·20·32dp, 40%, 한 획을 한 경로로)·직선(가로·세로 6° 안이면 붙는다)·글 메모(대화상자, 16·24·36sp, 바탕 상자)·지우개(획·메모째, 지나간 **선분**으로 맞힌다) | 빛깔 여덟. 되돌리기 200걸음, '모두 지우기' 는 묻고 되돌리기로 산다. 두 손가락은 확대·이동(뷰어의 `ZoomState`·확대 조각 그대로) — 두 번째 손가락이 닿으면 그리던 획을 버린다(시스템이 거둔 몸짓도 — 함정 표). 확대한 채 그은 굵기·글자 크기는 **보이는 크기**를 그림 좌표로 옮겨 든다 |
| 새 요청을 만들지 않고 **`SaveCrop.overlay` 를 넓혔다**(`Kind.MARKUP`, 결과 `이름_markup.확장자`) | 쓰는 길이 잘라 저장과 같다(`ImageSaver` — 형식·EXIF·화소 상한·이름 맡기). 이음매는 `CropSupport.Overlay`(`draw(canvas, w, h)`) 하나 — `core:io` 는 그릴 것의 내용을 모른다 |
| **화면과 저장이 같은 그리기 코드**(`MarkupPainter`) | 그림 폭을 1000 으로 보는 기준 단위로 그리고 캔버스 배율만 바꾼다. 메모의 줄 바꿈도 그 단위에서 한 번만 짠다 — 저장한 것이 화면과 어긋나지 않게. 메모마다 붓을 따로 둔다(함정 표 '`StaticLayout`') |
| 그린 것은 ViewModel 이 든다, 저장 상태에는 경로 하나 | 회전·밤 모드를 견딘다. 프로세스가 죽으면 `ON_STOP` 에 적은 초안(`noBackupFilesDir/markup/draft.bin`)으로 — 원본의 경로·크기·시각·치수가 같을 때만 되살린다(되돌리기 기록은 없다) |
| 켜지는 조건은 잘라 저장과 같은 판단 하나(`editableProbe`) | 정지 그림, 원본 치수를 쟀고, 돌려 둔 채 아직 저장하지 않은 것이 아닐 때. 읽기 전용 사본에는 메뉴가 없다 |
| 접근성 | 도구·빛깔·굵기는 이름이 붙은 라디오 단추, 그리는 자리는 '획 N개 · 메모 M개', 낭독기 동작으로 '가운데에 글 메모 달기'·메모 고치기 |

**작은 것 하나(고치지 않았다).** 편집 중에 밖에서 바뀐 파일을 '저장하지 않음' 으로 닫으면 뷰어는 옛 글을 그대로 보인다 — 텍스트 뷰어는 디스크에서
바뀐 파일을 다시 읽지 않는다(7단계의 결정 — '자동 새로고침 없음'). 자판을 띄운 채 뒤로 가기는 두 번 — 자판 내리기, 그다음 '닫기 전에 저장할까요?' — 로
보통의 동작이다.

### 14단계 뒤 — 네트워크를 걷고 편집기를 안으로, 뷰어 교체

편집기(rhwp·ONLYOFFICE)는 이 앱 안의 모듈이다 — 목록에서 곧바로 편집기의 보기 모드로 넘기려면 편집기가 이 앱 안에 있어야 한다. 이 앱에는
네트워크 기능이 없다(사용자 — 'Files 의 네트워크 기능을 제거').

#### INTERNET 을 다시 걷었다

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 매니페스트가 다시 `INTERNET` 을 `tools:node="remove"` 로 막는다(`uses-permission-sdk-23` 모양도 — 함정 표. `assemble` 이 병합 매니페스트를 보는 `verify<변형>NoInternet` 을 기다린다) | OS 의 겹(앱 UID 의 소켓 차단)이 돌아온다 — 문서의 WebView·편집기·서드파티를 한꺼번에 막는다 |
| 네트워크 저장소(SFTP)가 없다 | 서버 목록 표는 DB 4 → 5 가 지운다(`DROP TABLE remote_server`). 크래시 기록의 경로 지우기는 `sftp://` 를 그대로 안다 — 옛 판의 상태가 그 주소를 실을 수 있다고 보아 남겼다(추정) |
| **'브라우저로 열기'** — HTML 의 그린 모양을 보는 유일한 길 | 같은 파일을 브라우저에 넘긴다(`ExternalOpen.Mode.WEB_PAGE` — `text/html`, XHTML 은 `application/xhtml+xml`, 언제나 고르는 창). 텍스트 뷰어 ⋮ 에 있다 |
| 그동안 세운 겹은 남긴다 | 잠긴 WebView 의 `blockNetworkLoads` 명시, 재생기의 `LocalOnlyDataSource`(file·content 만 연다 — media3 의 기본 소스는 http·https·udp·rtmp 도 연다), 매니페스트의 WebView 사용 통계(`MetricsOptOut`)·안전 브라우징 끄기. 방어는 겹쳐 두어야 한 겹이 무너져도 남는다 |

#### 편집기를 안으로(`feature:editor`)

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 편집기는 이 앱의 모듈이다 — 같은 APK·같은 UID. 화면(`EditorActivity`)은 **따로 된 프로세스 `:editor`** 에서 돈다 | 큰 웹 편집기의 WebView·렌더러 메모리(수백 MB)를 파일 쪽과 가르고, WebView 자료 폴더도 따로 쓴다(`WebView.setDataDirectorySuffix("editor")` — 한 앱의 두 프로세스가 같은 폴더를 쓰면 WebView 가 던진다). 그 프로세스는 `IroiroApp` 의 조립·정리를 건너뛴다(받은 사본 치우기가 주 프로세스 재생기의 파일을 모르고 지울 수 있다) |
| 길 — 인텐트(`EditorApp.intent`)·저장 자리(`EditorSlotProvider`, 주 프로세스)·`EditorSaver` | 두 프로세스를 잇는 것이 저장 자리 제공자다. 편집기는 부른 쪽이 이 앱인지 본다(`callingActivity` — 화면을 내보내지 않는다). 아래 '편집기와의 길' |
| 엔진 묶음은 `vendor/editor/assets`(git 무시) — `tools/editor/` 가 짓는다 | rhwp(MIT)·ONLYOFFICE 편집기와 x2t(AGPL-3.0)·글꼴(OFL·Apache·GUST). 묶음이 없으면 시험 엔진(probe)만 든 빌드가 되고 형식 목록이 비어 아무것도 넘기지 않는다. 고지 화면의 '편집기 엔진' 이 묶음의 licenses 를 펼친다(엔진 명세 문장은 묶음이 든 빌드에만, 상표 문장은 따로 늘 — 그 출처는 확인하지 못했다). 묶음이 든 릴리스 APK 는 약 76 MB 다(거의 다 엔진이다 — 엔진 없이는 약 8 MB) |
| 엔진 판 — rhwp **0.8.7**, ONLYOFFICE 는 CryptPad 빌드 **9.3.2+3**(편집기·x2t). 새 판은 `python -I tools/editor/check_updates.py` 가 알린다 | rhwp 0.9.0 은 지어 보고 되돌렸다(2026-11-14) — 글꼴이 HYhwpEQ 인 수식(한컴 수식의 기본)의 숫자·기호를 그 글꼴의 사용자 영역 글자로 그려, 그 글꼴이 없는 기기에서 네모로 나왔다(`build_rhwp.py` 머리말). 판을 올리면 수식 문서·옛한글·큰 HWP 를 에뮬레이터에서 먼저 본다. ONLYOFFICE 는 CryptPad 가 새 빌드의 해시를 내야 올린다(`build_onlyoffice.py` 는 그 해시와 견준다) — 본가의 9.4·10.0 보안 고침은 그때 들어온다 |
| 의존 하나 — `androidx.webkit` 1.17.1(사용자 승인, 브라우저와 같은 판) | 편집기와 바이트를 ArrayBuffer 로 주고받는 WebMessageListener 가 여기 있다. `core:webhost` 는 쓰지 않는다 — 지금은 편집기만 쓴다 |
| 편집기 화면은 **설정이 바뀌어도 다시 서지 않는다** | 다시 서면 WebView 가 새로 서고 쪽 안의 고친 것이 사라진다(함정 표) |

#### 편집기와의 길 — 넘기기와 저장

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 넘길지는 **편집기가 알리는 형식**으로(`core:io` 의 `EditorApp.kindFor`·`viewKindFor`) | 편집기 화면의 `<meta-data io.github.donggi.iroiro.editor.KINDS>`(빌드에 든 엔진이 채운다). 형식 글자 = 확장자, 넘길 때의 MIME 은 소문자. 한글(rhwp), OOXML 과 그 판(docm·dotx·dotm·xlsm·xltx·xltm·pptm·ppsx·ppsm·potx·potm), 이전 형식(doc·dot·xls·xlt·ppt·pot·pps), ODF(odt·ods·odp), TXT·CSV, PDF(ONLYOFFICE). RTF 는 넘기지 않는다(그 변환기가 읽을 때 한글을 버린다). 한글 서식(`.hwt`·`.hwtx`·`.owpml`)은 형식의 **별칭**으로 연다(`hwt`→hwp, `hwtx`·`owpml`→hwpx — 바이트 모양이 같다) |
| 넘기는 곳 — 목록·다른 앱이 넘긴 문서(보기 모드, 아래 표), 문서 뷰어 ⋮ 의 '편집'(PDF — 편집기는 고칠 수 있는 docx 로 **바꿔** 연다), 실패 화면의 '편집기로 열기'(`DocViewScreen`·`rememberEditorKind`), 폴더 + 의 새 문서, 텍스트 뷰어 ⋮ 의 '표 편집기로 열기'(CSV 만) | 읽기 전용 화면(받은 사본)에서는 편집으로 넘기지 않는다. CSV 는 표 편집기로 고치고 저장하면 그 자리(원래 인코딩·줄 끝·구분자)에 쓰인다 — 돌아오면 뷰어가 인코딩부터 다시 판정한다(`TextViewer.changedElsewhere` + `reopen` — 같은 경로라 옛 색인이 남는다). 글 파일(.txt)은 넘기지 않는다 — 이 앱의 편집기가 바이트까지 지킨다 |
| 저장 자리 — `EditorSlots`(세션)·`EditorSlotProvider`(내보내지 않은 제공자, 주 프로세스, `grantUriPermissions="false"` — 같은 UID 라 줄 일이 없다)·`EditorSaver`(쓰기 — 파일 작업 큐의 `Request.SaveFromEditor`) | 편집기는 그 자리(URI 하나)에 바이트를 쓰고 `commit` 을 부른다. 파일 쪽은 큐가 원본을 바꾼 **뒤에** 답한다 — 편집기는 그 답을 받아야 '저장됨' 이다. `commit` 은 받은 바이트를 그 반영만의 파일로 옮긴 뒤(`stage` — 같은 자리를 다음 반영이 덮으면 앞 요청이 남의 바이트를 쓴다) 큐에 넣고, 줄 서서 15초(`QUEUE_WAIT_MS`)를 넘기면 줄에서 거두고(`FileOpManager.withdraw` — 도는 것은 끊지 않는다) '바쁘다', 돌기 시작한 것은 끝을 기다려 진짜 결과로 답한다. 세션이 닫히면 줄 선 반영을 거둔다(함정 표 '제공자 호출에서 큐를 기다리다') |
| 세션의 첫 저장 때 고치기 전 판을 휴지통에 | 글 편집(`TextSaver`)과 같은 판단. 같은 세션의 다음 저장은 남기지 않는다. 새 문서의 빈 원본은 남기지 않는다(`fresh`). 못 남기면 쓰지 않는다 |
| 연 뒤 밖에서 바뀌었으면 그 판도 휴지통에 남기고 쓴다 | 세션이 들고 있는 (크기·시각)과 지금이 다르면. 편집기에게 물을 길이 없다(동기 호출 안이다) — 대신 잃지 않게 한다. 넣을 때 한 번, 큐 안에서 한 번 더 견준다(`SaveFromEditor.expected` → `EditorSaver.keepsPrevious` — 다르거나 모르면 남긴다) |
| 형식·이름을 바꾼 저장은 곁에 새 파일 | 'HWPX 형식으로 저장'·'다른 이름으로 저장', 그리고 이전 형식·PDF(편집기가 doc → docx, xls → xlsx, ppt → pptx, pdf → docx 로 저장한다 — `EditorSlots.plan`). 별칭(서식)은 저장할 확장자 목록에 넣지 않아, 서식을 고쳐 저장하면 서식은 그대로 두고 곁에 새 `.hwp`·`.hwpx` 가 생긴다(`HancomTemplateSaveTest` — rhwp 어댑터는 스튜디오에 형식의 확장자로 바꾼 이름을 넘긴다, 안 바꾸면 `신청서.hwt.hwp`). 있는 이름이면 `(2)`(`NewFiles.place`). 같은 세션이 그 이름으로 다시 저장하면 그 새 파일을 바꾼다. 이름은 편집기가 댄 것을 다듬는다(`cleanName` — 마지막 조각, FAT·exFAT·NTFS 가 받지 않는 글자 빼기, 확장자는 형식이 정한다 — `EditorApp.extensions`). **길이는 바이트로** — 255 − 확장자 바이트, 글자로는 120(UTF-16, 서로게이트 쌍을 가르지 않는다). 편집기 쪽 `OutputNames` 도 같은 자리에서 자른다(편집기는 그 이름으로 앱 캐시에 먼저 쓴다 — 교차 시험 `theEditorsNamingAndOursAgree`). **같은 이름인가**는 원본 이름에도 같은 다듬기를 하고 대소문자를 가리지 않고, 확장자가 형식의 것일 때만 본다(`sameName` — `공문.HWP` 는 제자리, 서식·이전 형식은 곁에 새 파일). 제자리 저장의 경로는 언제나 원본이다 |
| 앞머리로 형식을 확인한다(`EditorSaver.looksLike`), 512 MiB 까지 | HWP 는 CFB, HWPX 는 ZIP, OOXML 은 ZIP **또는 CFB**(암호 문서를 고쳐 저장하면 같은 암호가 걸려 CFB 로 온다), ODF 는 암호가 걸려도 ZIP, PDF 는 `%PDF-`, 글(txt·csv)은 다른 그릇(ZIP·CFB·PDF)으로 시작하지만 않으면. 편집기가 같은 형식으로 쓰지 않는 이전 형식은 받지 않는다. 엔진이 망가진 바이트를 내도 원본에 닿지 않는다 |
| 세션은 디스크에(`no_backup/editor-slots.json`, AtomicFile), 받은 바이트는 캐시에(`cache/editor-slot/<표>/`) | 편집기가 앞에 있는 동안 주 프로세스가 거둬질 수 있다 — 제공자가 다시 띄운 프로세스가 세션을 읽어 받는다. 7일 동안 쓰이지 않았거나 32개를 넘으면 가장 오래 쓰이지 않은 것부터 지운다(`usedAt` — 반영할 때 갱신) — **편집기가 쥔 세션(`hold`)은 지우지 않는다**. 시계가 뒤로 간 것은 지우는 까닭이 아니다. 세션이 없으면 제공자가 `/dev/null` 을 주어 반영이 `session` 으로 답하고, 편집기가 '원본 파일과의 연결이 끊겨… 내보내기로 남긴 뒤 다시 여세요' 를 보인다. 닫힌 세션은 늦은 반영이 되살리지 않는다. open·close 는 주 스레드 밖에서 |
| 돌아오면 다시 연다 | 문서 뷰어에서 넘겼으면 **원래 문서**를 다시 연다(`docReopen` — 같은 경로라도 `DocViewScreen` 의 `reopen`), 폴더에서 넘겼으면 목록을 다시 읽는다. **한 번 알린다**(`noticeOf`) — 새 파일·고치기 전 판을 휴지통에 남긴 것·둘 다(곧바로 끝나는 받은 화면이면 토스트). '새 파일로 저장했습니다' 는 이름마다 한 번 |

#### 뷰어 교체 — 오피스·한글은 편집기의 보기 모드로

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 목록에서 누른 한글·OOXML(과 그 판)·ODF·옛 오피스(doc·xls·ppt …) → 편집기의 **보기 모드**(`EditorApp.viewKindFor` → `MainActivity` 의 `openDocument`) | 편집기는 쪽을 PC 처럼 그린다(rhwp 의 조판, ONLYOFFICE). 걷은 흐름 변환기는 쪽을 버리고 글을 다시 흘렸다 — 사용자의 말로 '확연히 나쁜' 쪽이다. 묶음 없이 지은 빌드면 문서 뷰어로 간다('다루지 않는 문서' 와 '다른 앱으로 열기') |
| 빼는 것 — PDF·글·CSV(`EditorApp.OWN_VIEWERS`), EPUB | PDF 는 이 앱의 pdfium 뷰어가 이미 쪽 그대로 그리고(편집기는 PDF 를 고칠 수 있는 docx 로 **바꿔** 연다 — 쪽 모양이 달라진다), 글·CSV 는 텍스트 뷰어의 것이다. EPUB 은 편집기가 다루지 않는다 |
| 보기 모드의 **떠 있는 단추 묶음**(`EditorActivity.refreshFloatingBar` — 무엇을 띄울지는 `EditorRules.floating`) | 파일 쪽이 보기 모드에도 저장 자리를 준다. 오른쪽 아래(엔진의 아래 막대를 가리지 않게 72dp 위)에 ⋮ 와 주 단추 — 문서 앱들의 보기 화면이 연필을 두는 자리다. 주 단추는 보기 모드에서 문서를 열었고 저장 자리가 있으면 '편집', 암호 물음을 닫아 열지 못했으면 '다시 열기', 그 밖의 실패면 '다른 앱으로 열기'. ⋮ 는 보기 모드에서 문서를 연 뒤나 열지 못했을 때 있고(고치는 동안과 **여는 동안에는 없다** — 여는 동안 엔진이 띄우는 암호 칸의 오른쪽 끝을 가렸다), 머리에 파일 이름(누를 수 없다), 줄에 '다른 앱으로 열기'(`core:io` 의 문구 — 받은 사본도 된다, 두 번 누름은 700ms 막는다). 자판이 뜨면 숨긴다. **'편집' 을 누르면** 원본을 다시 읽어 떠 둔 사본과 바이트로 견주고(`DocumentStore.sameAs` — FileProvider URI 로는 수정 시각을 알 수 없다) 바뀌었으면 지금 파일로 열고 '보던 사이 파일이 바뀌어 지금 파일로 엽니다' 를 말한다(읽지 못하면 보기 그대로). 쪽에 읽던 자리를 물어(`where`, 800ms) 편집 쪽 첫 쪽 주소의 `at` 에 싣는다 — rhwp 는 `s:위,왼쪽`(스크롤 비율 — 스튜디오가 쪽 번호를 밖에 내지 않는다), ONLYOFFICE 는 `p:쪽`·`c:시트,행,열`. WebView 는 새로 세운다(엔진들이 처음 열 때 모드를 정한다 — rhwp 어댑터의 입력 막기, ONLYOFFICE 의 `editorConfig.mode`). 받은 사본(읽기 전용)에는 '편집' 이 없다. 암호 문서는 엔진이 다시 묻는다(암호는 어디에도 두지 않는다). 플랫폼 뷰로 그린다(Material 구성 요소를 들이지 않는다) |
| **열지 못한 문서**(`open-failed`) | 엔진이 문서를 열지 못하면 어댑터가 인사(`ready`) 대신 `open-failed` 를 보내고 까닭을 쪽에 적는다. 앱은 '편집' 단추를 띄우지 않고 '다른 앱으로 열기'(받은 content URI 를 읽기 권한과 함께 고르는 창으로 — 이 앱의 `OpenActivity` 는 뺀다)를 단다. **rhwp 는 열지 못하면 빈 새 문서를 대신 연다**(`openBlankDocumentIfIdle`) — 그것을 넘겨받은 문서로 알면 '편집' 단추가 뜨고, 고쳐 저장하면 빈 문서가 원본 자리에 쓰일 뻔했다(풀 수 없는 방식으로 잠긴 HWP). 어댑터가 스튜디오의 오류 기록('[main] 파일 로드 실패')으로 알아채 저장을 막고 그 빈 문서를 덮는다 — 스튜디오가 그 글을 바꾸면 알아채지 못한다. `open-failed` 는 갈래(`reason`)를 싣는다 — `cancelled`(암호 물음을 닫았다 → 앱이 '다시 열기' — WebView 를 새로 세우고 원본을 다시 떠 온다)·`locked`(풀 수 없는 잠금·DRM)·`corrupt`·`other`. rhwp 는 암호 창을 닫아도 실패 기록 없이 빈 문서를 열므로 '[WasmBridge] 새 문서 생성' 기록과 암호 칸(`#hwp-password-input`)이 나타났는지를 함께 본다(rhwp v0.8.7 의 기록 글에 기댄다). ONLYOFFICE 는 x2t 가 빈 결과를 낸 것을, 편집기의 문서가 비었을 때만 원본(OOXML·ODF)을 다시 받아 견줘 잡는다(원본에 내용이 있었으면 `corrupt`). 오류 화면의 단추는 `EditorRules.actions` — 엔진 없음·WebView·너무 큼은 '다른 앱으로 열기', 읽지 못함·렌더러 죽음은 '다시 열기' + '다른 앱으로 열기', 부른 앱·인텐트·저장 자리를 믿을 수 없으면 '닫기' 뿐 |
| 보기 모드도 읽는 화면 — **화면 켜 두기** | 파일 쪽 설정('읽는 동안 › 화면 켜 두기')의 분을 인텐트에 실어 보내고(`EditorApp.EXTRA_KEEP_SCREEN_MINUTES` — 편집기의 프로세스는 이 앱의 DataStore 를 열지 않는다, 한 프로세스의 것이다) 편집기가 마지막 조작(`onUserInteraction`) 뒤 그 시간까지 창에 `FLAG_KEEP_SCREEN_ON` 을 건다. 편집으로 바꾸면 푼다(글 편집기와 같은 판단 — 치는 동안은 그 조작이 화면을 깨워 둔다). 띄울 때 설정을 읽어 보낸다(`first()`, 2초 상한 — 넘으면 기본값. 앱이 막 섰을 때 아직 읽지 못한 값을 보내지 않게) |
| 편집기를 띄우는 동안 누름을 흘려보낸다(`editorPending`) | 띄우기 시작부터 결과가 올 때까지 문서 누름·편집기 열기를 흘려보낸다 — 오피스 문서를 빠르게 두 번 누르면 편집기가 두 겹 쌓였다. 시간이 아니라 결과까지인 것은 `:editor` 프로세스가 차갑게 서는 시간이 `SETTLE_MS` 보다 길 수 있어서다. 실패·취소 길에서도 풀린다. 문서 뷰어에서 편집기를 띄우지 못하면 문서를 다시 연다(`docReopen` — '여는 중' 동그라미가 남았다) |
| 다른 앱이 넘긴 문서(`OpenActivity`) | 편집기가 그 형식을 보이면 `AppScreen.Editor`(코덱 `r:<형식>:<경로>` — 옛 판의 `o:` 는 첫 화면으로 읽는다)로 편집기를 한 번만 띄우고(저장 상태의 표시 — 회전·프로세스 재생성 때 두 번 띄우지 않게), 편집기가 닫히면 받은 화면도 끝난다(`home`). 못 띄우면 문서 뷰어로 물러난다. VIEW·SEND 필터에 doc·xls·ppt·odt·ods·odp 의 MIME 도 있다 — 편집기가 보이는 형식을 모두 받는다(`ReceiveFiltersTest` 가 편집기 형식 표와 견준다) |
| 문서 뷰어 실패 화면의 단추 | PDF 는 **깨진 파일에서만** '편집기로 열기' 를 두고 그때도 '다른 앱으로 열기' 가 첫 단추다. 오피스·한글이 문서 뷰어에 닿는 것은 편집기를 띄우지 못하고 물러났을 때뿐이라('다루지 않는 문서') 그때는 편집기가 첫 단추다(`DocOpenWith.editorOnFailure`·`editorFirst`) |
| `FormatRegistry` 는 EPUB·PDF 둘 | 판별기 둘·여는이 둘. EPUB 판별기가 스스로 한글 문서를 가린다(함정 표 'HWPX 는 EPUB 처럼 생겼다') |
| 새 문서 넷은 **서식을 옮겨 적는다**(`core:io` 의 `BlankDocuments`·`NewDocuments`) | 앱에 든 서식(`assets/blank/blank.{docx,xlsx,pptx,hwpx}` — 걷은 쓰개가 마지막으로 지은 것, 3~12 KB)을 ZIP 항목의 차례·이름·압축 방식·시각 그대로 옮기고, 만든 시각 자리의 표지(`1970-01-01T00:00:00Z` — `docProps/core.xml`·`Contents/content.hpf`)만 지금(UTC, 초)으로 바꾼다. 데이터 기술자 없이 쓴다 — 압축할 항목은 같은 압축기로 길이를 미리 재고, `mimetype` 같은 STORED 항목은 크기·CRC 를 먼저 적는다. 다 쓴 뒤 처음부터 다시 읽어 CRC 로 확인하고 그 이름 그대로 놓는다(`NewFiles.place`, `O_EXCL`). 메뉴의 줄은 서식이 있고 편집기가 그 형식을 고칠 수 있을 때만 있다(`NewDocuments.creatable`) |
| 읽던 쪽 배지 | 오피스·한글 문서에는 달지 않는다 — 편집기는 읽던 자리를 적지 않아, 걷은 흐름 뷰어가 남긴 옛 기록의 배지('50%')가 영영 그 자리에 멈췄을 것이다 |
| 한컴 고지 | 그대로 둔다 — 편집기에 실린 rhwp 가 같은 한컴 공개 문서를 참고했다(그 README 와 정보 창에 같은 문장이 있다). 고지 화면의 설명은 '한글 문서는 편집기에 함께 실린 rhwp 가 엽니다' |
| 설정의 문서 보기 안내 | 'EPUB에만 걸립니다(PDF·오피스·한글 문서는 쪽 그대로).' |

**적어 둘 것.** 앱을 오간 뒤(최근 앱) rhwp 편집 모드에서 첫 뒤로 가기가 아무 일도 하지 않은 것처럼 보일 때가 있다 — 쪽이 펼친 도구 상자를 닫으려다
'처리했다' 고 답한 것으로 보이고, 두 번째에 저장 물음이 뜬다('닫으려던 것이 닫히지 않으면 다음엔 없다' 의 동작). 원인은 가리지 않았다.

### 14단계 뒤 — Play 프로텍트의 설치 차단

사용자 폰에서 Play 프로텍트가 이 앱을 **'유해한 앱 차단됨'**(적대적 다운로더 — 다른 앱을 내려받아 까는 통로로 본다는 분류)으로 막는다. 판정
기준은 공개돼 있지 않다.

- `REQUEST_INSTALL_PACKAGES` 를 걷고 APK 를 넘기지 않게 한 판도 **똑같이 막혔다** — 같은 꾸러미 이름·같은 디버그 키·거의 같은 코드라 앞의 판정이
  붙어 남는 것으로 본다(추정). 사용자가 APK 설치 기능을 되살리라고 했다 — **이 권한을 다시 걷지 마라.**
- 설치는 Play 프로텍트 검사를 잠시 끄고 하고 다시 켠다(사용자가 폰에서). 다시 켜면 같은 APK 가 다시 막힌다 — 업데이트마다 껐다 켠다.
- 같은 PC 의 디버그 키로 서명한 Web(이 권한이 없다)은 설치된다.

**남은 길**(사용자가 정할 것). 원인 좁히기 — 에뮬레이터(Play 스토어 이미지)에서 시험용 꾸러미 이름으로 판을 나눠(서명 키만 바꾼 판, 편집기 엔진을
뺀 판) 막히는지 본다(그 판들의 앱 정보가 Google 로 간다 — 허락이 필요하다). 앱 정체가 까닭이면 새 서명 키로 옮긴다(폰의 옛 판을 지우고 깔아야 해서
휴지통 기록·이어보기·즐겨찾기가 사라진다). Google 에 오탐 이의제기(Play 프로텍트 이의 양식 — APK 를 낸다).

### 14단계 뒤 — AES 암호 ZIP

파일 목록 → 고르기 →
⋮ → 'ZIP으로 압축' 의 이름 칸 아래에 **암호 (선택)** 칸이 붙었다. 비워 두면 예전과 같은 길(플랫폼의 `ZipOutputStream`)이고, 치면 확인 칸이 나와
두 번 치게 하고 **WinZip AES-256(AE-2)** 으로 잠근다 — 7-Zip·WinZip·반디집이 아는 형식이다. 결과는 '암호를 걸어 압축했습니다: 이름.zip'. 칸의
말은 둘뿐이다 — '파일 이름은 가려지지 않습니다', 영문·숫자·기호 밖의 글자가 들면 '… 반디집 등 다른 프로그램에서 열리지 않을 수 있습니다'.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 쓰개를 직접 쓴다(`core:io` 의 `AesZipWriter`·`AesZipCrypto`). 새 의존 없음 — `javax.crypto`(AES/ECB·HmacSHA1)와 `java.util.zip.Deflater` 만 | 플랫폼의 `ZipOutputStream` 은 방식 99 를 받지 않고, commons-compress 는 암호 항목을 쓰지 못하며(함정 표) `core:io` 가 볼 수도 없다. zip4j 는 새 의존이라 들이지 않았다 |
| 형식 — 방식 99, 일반 목적 비트 0·11, 푸는 데 필요한 판 51, 추가 필드 `0x9901`(판 2·`AE`·세기 3·실제 방식 8 또는 0)을 두 머리에. 데이터는 솔트 16 ‖ 확인값 2 ‖ AES-256-CTR(압축한 바이트) ‖ HMAC-SHA1 의 앞 10 | WinZip 의 공개 문서 'AES Encryption Information' 그대로. 열쇠는 PBKDF2-HMAC-SHA1 1000회 → AES 32·HMAC 32·확인값 2. 실제 방식은 ZIP 만들기가 파일마다 고른 것(이미 압축된 형식은 저장) |
| **항목마다 새 솔트**(`SecureRandom` 16바이트) | 열쇠가 항목마다 달라진다. 같은 열쇠로 계수기를 1 부터 다시 쓰면 두 항목의 평문이 XOR 로 드러난다 |
| **AE-2**(CRC 칸 0) | 평문의 CRC 를 적지 않는다 — 작은 파일은 CRC 만으로 내용을 맞혀 볼 수 있다(네 자리 숫자를 적은 메모면 1만 번). 무결성은 HMAC 이 지킨다. pyzipper 의 기본값과 같다 |
| CTR 계수기는 **1 부터의 리틀 엔디언**(앞 8바이트) — 계수기 블록을 늘어놓아 `AES/ECB` 로 64 KiB 씩 한꺼번에 | 자바의 `AES/CTR` 은 계수기가 빅 엔디언이다(함정 표). 한 블록씩 부르면 4 GiB 에 약 2억 7천만 번이다 |
| 데이터 기술자를 쓰지 않는다 — **머리를 먼저 쓰고 항목이 끝나면 그 머리의 크기 칸을 고쳐 쓴다**(`FileChannel` 의 위치 지정 쓰기 — 우리 임시본 안의 일) | 흐름으로 읽는 쪽도 '저장' 안쪽의 끝을 알 수 있게. 그래서 '저장' 항목도 **한 번만 읽는다**(CRC 를 적지 않고 크기는 끝에 안다 — 암호 없는 길은 재느라 두 번 읽는다) |
| ZIP64 — 넘친 칸만 가운데 목록의 ZIP64 칸에(명세의 차례), 가운데 목록의 자리·크기가 넘치면 ZIP64 끝 레코드와 위치표. 로컬 머리의 ZIP64 칸은 훑을 때 잰 크기에 deflate 가 부풀 수 있는 몫을 얹어 **미리** 잡는다 | 로컬 머리의 칸은 나중에 늘릴 수 없다. 잡지 않은 항목이 넘치면(읽는 사이 파일이 4 GiB 를 넘게 자랐다) 깨진 ZIP 을 쓰지 않고 전부 버린다('입출력이 실패했습니다' — 암호 없는 길의 '잰 뒤에 파일이 바뀌었다' 와 같은 판단) |
| 폴더는 잠그지 않는다. 파일 이름도 잠기지 않는다 | 형식의 성질이다(가운데 목록이 평문). 칸이 말한다 |
| 암호의 바이트는 **UTF-8** | 7-Zip 이 그렇게 넣는다고 알려져 있다(이 기계에 7-Zip 이 없어 확인하지 않았다). 반디집은 한글을 CP949 로 넣어 우리의 한글 암호 ZIP 을 열지 못한다(실측 — 함정 표). 이 앱의 리더는 UTF-8·CP949·ISO-8859-1 을 대 보므로 어느 쪽이든 연다 |
| 암호가 머무는 곳 | 칸은 `core:ui` 의 `NewPasswordFields`(`PasswordDialog` 곁 — 같은 규칙: `remember` 로 든다, 보안 입력칸, 눈 단추). '압축' 을 누르는 순간 `CharArray` 로 꺼내 칸을 비우고 큐의 요청(`Request.Compress.password`)에만 싣는다 — 인텐트·저장 상태·디스크·로그에 가지 않는다(`toString` 이 싣지 않는다). `ZipMaker.make` 가 맨 먼저 열쇠의 바탕(`AesZipKey` — 암호로 열쇠를 건 HMAC 하나, 항목마다 솔트만 바꿔 PBKDF2 를 돈다)으로 옮기고 배열과 UTF-8 사본을 덮는다. 돌지 않고 버려진 요청은 큐가 덮는다(`settle` — 풀기와 같다) |
| 확인 단추 | 두 칸이 같고 UTF-8 로 옮길 수 있어야 켜진다(짝 없는 서로게이트는 막는다 — 엔진 `AesZipKey.of` 와 같은 판정 `AesZipCrypto.isEncodable`) |
| 전통 암호(ZipCrypto)로는 만들지 않는다 | 약하다(알려진 평문으로 열쇠를 되찾는 공격이 있다). 읽기는 그대로 한다 |

**리더의 구멍을 함께 막았다**(`format:archive` 의 `ZipDecryption.authenticated`) — 시험이 찾았다. deflate 항목의 암호문 첫 비트('마지막 블록'
표시)를 뒤집으면 해제기가 첫 블록에서 끝나는데, 리더가 남은 암호문을 읽지 않아 **인증값을 보지 않은 채 잘린 평문을 정상의 끝으로** 내주었다
(AE-2 에는 CRC 도 없다 — 풀기라면 잘린 파일을 '완료' 로 놓았다). 암호문이 해제기의 한 번 채우기(64 KiB)보다 큰 항목에서만 드러났고, pyzipper 가
만든 것에도 같았다(오라클의 변조 표본). 이제 해제기가 끝을 말하면 남은 암호문을 읽어 인증값을 본다(함정 표).

**오라클이 우리가 아니다.**

| 확인한 것 | 결과 |
|---|---|
| 독립 구현이 읽는다(`core:io` 의 `AesZipWriterTest` 15 — JCE 의 PBKDF2·`BigInteger` 계수기·`Inflater`, 머리는 명세의 바이트 자리) | 머리 칸, 두 머리의 일치, 빈틈 없음, ZIP64(32비트의 끝을 1·600 바이트로 낮춰서), DOS 시각·확장 시각, 내용(저장·압축·빈 파일·한글 이름·3 MB·한 바이트씩 넣기), 틀린 암호·바뀐 암호문, RFC 6070, 넘치면 던지기 |
| 우리 리더가 읽는다(`feature:archive` 의 `AesZipRoundTripTest` 7 — 압축 목록·풀기의 길 `Archives.open`·`extractSequentially`) | 이름·폴더·시각·크기·내용, 암호 없이 자물쇠·틀린 암호, 바뀐 암호문, 마지막 블록 비트, ZIP64, 한글 암호 |
| **pyzipper 0.4.0 이 우리 것을 푼다** | 표본 7개·파일 30개가 바이트까지 같다(9 MB·한글 암호·126자 암호·ZIP64 억지·섞임·3바이트씩 넣은 것). 머리 칸(판 51·CRC 0·AE-2·실제 방식·저장의 압축 크기 = 평문 + 28)과 두 머리의 일치를 구조체로 따로 읽어 확인, `testzip()`, 틀린 암호 거절 |
| **우리 리더가 pyzipper 것을 푼다** | 9개 — AES-128·192·256, AE-1(CRC 를 견준다), 저장·압축, 한글 암호, ZIP64 억지, 7 MB, 한글·일본어·이모지 이름, 잠긴 폴더 항목. 틀린 암호는 '틀렸다'. 한 비트를 바꾼 둘(마지막 블록·저장의 한가운데)은 읽다가 실패 |
| 반디집 7.45 콘솔(`bz t`·`bz x`) | 영문 암호의 표본 넷(ZIP64 둘 포함) — 시험 통과, 풀어서 바이트가 같다. 한글이 든 암호의 셋은 열지 못했다(CP949) |
| Info-ZIP `zipinfo -v` | 모든 머리를 경고 없이 읽는다(방식 99 는 'unknown') |

오라클의 차례 — `python oracle.py make-py <폴더>`(pyzipper 가 잠근 것·평문·변조 둘) → 환경 변수 `IROIRO_AESZIP_ORACLE=<폴더>` 를 두고
`:feature:archive:testDebugUnitTest --tests io.github.donggi.iroiroviewer.archive.AesZipOracleTest --rerun`(우리 것을 쓰고 pyzipper 것을 읽는다 —
환경 변수는 시험의 입력이 아니라 `--rerun` 이 있어야 돈다) → `python oracle.py check-ours <폴더>`. 파이썬은 `-I` 없이(pyzipper 가 사용자
site-packages 에 있다) 저장소 밖에서 돌린다. 스크립트(`oracle.py`, 되돌리기 `mutate.py`)는 그 회차 세션의 스크래치패드에 두었고 저장소에 넣지
않았다 — 다시 하려면 이 차례대로 새로 짓는다. 표본은 전부 그 자리에서 지은 합성 데이터다.

**확인하지 못한 것** — 실기기, 한글이 든 암호의 경고(adb 로 한글을 칠 수 없다), 진행·취소, 폰에서의 속도(항목마다 PBKDF2 가 HMAC 을 4천 번
돈다 — 작은 파일이 수천 개면 그만큼 느리다), 계측 시험 다섯(위 '빌드와 검증' — 컴파일만), 7-Zip·WinZip·윈도 탐색기가 실제로 여는지, 4 GiB 를
넘는 실제 항목(한계를 낮춘 시험으로만). PBKDF2 1000회는 요즘 기준으로 약하다 — 형식이 정한 값이라 바꾸면 다른 도구가 못 연다. 긴 암호가 지킨다.

### 14단계 뒤 — 보안 점검 고침

보안 점검(독립 검토)이 낸 것 가운데 고칠 가치가 있는 것만 고쳤다(사용자). 나머지는 아래 '알고 둔 것'.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| **7z AES 열쇠 유도 반복을 유도하기 전에 본다**(`format:archive` 의 `SevenZKeyGuard`). 지수 0~24 와 63(반복 없음)만 받는다 — 7-Zip(`k_NumCyclesPower_Supported_MAX`)·py7zr 와 같다. 넘으면 `SevenZUnsafeKeyException` — '이 앱이 풀지 않는 방식으로 잠긴 압축 파일(만화)입니다' | commons-compress 1.28.0 은 그 값만큼 `1L shl n` 번 SHA-256 을 돌고 인터럽트를 보지 않는다(함정 표). 62 를 적은 7z 에 암호를 넣으면 그 스레드가 끝나지 않고 해석 디스패처 둘을 그런 파일 둘이 붙든다. 라이브러리는 그 값을 유도 전에 내주지 않으므로 머리를 **우리가** 읽는다 — 서명 머리 → 다음 머리 → (잠긴·압축된 머리면 그 StreamsInfo, 아니면 MainStreamsInfo) → 폴더의 코더 속성(AES id `06 F1 07 01`). 읽는 차례는 라이브러리의 `SevenZFile` 그대로이고, 라이브러리가 유도 전에 스스로 실패하는 갈래(서명·판·머리 없음·추가 스트림·외부 폴더·속성 없는 AES)는 따라가지 않는다. 머리를 읽지 못하면 넘기지 않는다(`IOException`) |
| **잠긴 머리 안의 내용 폴더까지 본다** | 머리를 잠근 열쇠의 반복은 평문이지만 내용 폴더의 속성은 잠긴 머리 안이다 — 머리가 정상이고 내용이 62 면 라이브러리는 머리를 연 뒤 첫 항목에서 멎는다. 암호를 받았으면 머리를 우리가 푼다(같은 유도 — 반복을 덩이로 묶어 해시해 라이브러리보다 빠르다, 덩이마다 취소를 본다). 압축만 된 머리(7-Zip 의 LZMA·py7zr 의 LZMA2)는 암호 없이도 푼다. 머리를 푸는 사슬은 복사·LZMA·LZMA2·AES 만 안다 — 그 밖이고 암호를 받았으면 열지 않는다 |
| 암호를 넘기는 **모든 열기**(목록·확인·항목·풀기·만화의 패스 — `openFile`) 앞에서 다시 본다 · 암호 없는 목록에 받을 수 없는 반복이 보이면 잠긴 항목은 `lockedForGood` · 머리가 잠겼고 그 열쇠가 상한 밖이면 묻지 않고 '풀지 않는 방식' | 그 사이 파일이 바뀔 수 있다. 물어도 소용없는 암호를 묻지 않는다('암호가 걸린 파일' 의 규칙). 어느 폴더의 것인지 가르지 않고 잠긴 항목 전부다 — 7-Zip 이 만든 파일에는 그런 값이 없다 |
| **'풀지 않는 방식' 을 타입으로 가른다**(`ArchiveEncryptionUnsupportedException` — 7z 의 거절과 ZIP 의 PKWARE 강한 암호화가 잇는다) | 압축·만화 화면이 이름으로 갈랐는데 R8 이 이름을 줄여 릴리스에서는 '깨진 파일' 이었다(함정 표) |
| **다른 앱이 넘긴 설치 파일(APK)은 열지 않는다**(`app` 의 `refusalOf`·`core:io` 의 `ApkFiles`). 이름(`.apk`)이나 **내용**으로 보고, 받을 때·되살릴 때 둘 다, 사본은 곧바로 지운다. 받은 화면은 '다른 앱이 보낸 설치 파일은 열지 않습니다' 와 '닫기' 뿐 | 넘긴 APK 를 '다른 앱으로 열기' 로 설치 관리자에 넘기면 설치 관리자는 **이 앱이 보낸 것**으로 받는다 — 권한 없는 앱이 이 앱의 설치 권한과 '출처를 알 수 없는 앱' 허용을 빌려 쓴다. 안드로이드 12 는 이름을 가리킨 인텐트에 필터를 보지 않는다. 이름을 바꾼 APK 는 모르는 형식이면 '모든 앱에서 고르기'(모든 형식 — 형식을 하나라도 적은 필터에 다 맞는다)로 가고 거기 설치 관리자도 선다 — 그래서 받는 자리에서 어느 화면으로도 보내지 않는다(뷰어의 실패 화면·'보여 줄 화면이 없다'·재생기·편집기의 '다른 앱으로 열기' 가 함께 막힌다). 내용 판별은 설치 관리자(libziparchive)의 차례 — 끝에서 끝 레코드(주석 64 KiB, 끝에 가까운 것), ZIP64 위치표, 가운데 목록의 이름을 바이트로(뿌리의 `AndroidManifest.xml` 만) — 가운데 목록이 상한(64 MiB·100만 항목)을 넘으면 APK 로 친다. 겹으로 `ExternalOpen.open`·`ShareHelper.share` 가 받은 사본의 APK 를 넘기지 않는다. 파일 목록에서 고른 APK 는 그대로다(사용자가 되살린 기능). 매니페스트의 `octet-stream` 은 그대로다. 안드로이드 라이브러리 묶음(`.aar`)도 APK 로 보인다 |
| **이름의 방향 제어 글자**(U+061C·200E·200F·202A~202E·2066~2069 — `core:model` 의 `PathNames.isBidiControl`) — 짓거나 받는 이름에서는 `_`(`PathRules.sanitize` — 풀기·받은 사본·되살리기, 편집기가 댄 이름 `EditorSlots.cleanName`·`OutputNames` — 둘이 같은 자리에서), 화면에서는 이미 있는 이름의 그 글자를 U+FFFD 로(`PathNames.forDisplay`) | `invoice` + U+202E + `fdp.apk` 가 'invoicekpa.pdf' 로 보인다. 실제 이름은 바꾸지 않는다(찾고 열고 넘기는 데는 원래 이름 — 그런 이름의 문서를 편집기로 고쳐도 제자리에 저장된다). 화면은 목록·격자·정보 시트·받은 화면·뷰어 넷과 그림 뷰어·EXIF 시트·글 편집기의 제목·압축 안의 항목·재생 화면·재생목록·미니 바·편집기의 ⋮ 머리·내보내기 알림이다. 사용자가 적는 이름(`validate`)은 고쳐 주지 않는다 — 화면이 드러낸다 |
| **편집기의 그림 넣기·내보내기는 돌려받은 주소를 거른다**(`feature:editor` 의 `EditorRules.acceptsReturnedUri`) — `content` 이고 권한자가 이 앱의 것이 아닐 때만(사용자 번호 `0@` 를 떼고, 꾸러미 이름·`꾸러미.` 로 시작·제공자의 주인이 이 앱이면 거절, 대소문자 무관). 거르면 '이 앱의 파일은 넣을 수 없습니다'·'그 자리에는 내보낼 수 없습니다' | 고르개 자리에 선 제3자 앱이 `file:///data/user/0/<이 앱>/…` 나 이 앱의 FileProvider·저장 자리 주소를 돌려주면 이 앱이 제 신원으로 읽거나(내부 파일이 문서에 실려 나간다) 쓴다(내부 파일이나 — 모든 파일 접근 권한으로 — 사용자 파일을 덮는다) |

**시험 표본.** 7z 는 **합성**뿐이다 — commons-compress 가 잠근 것(반복 19)의 속성 바이트를 고쳐 두 CRC 를 다시 맞춘 것(맞추지 않으면 라이브러리가
유도 전에 'CRC 가 다르다' 로 끝나 시험이 아무것도 보지 않는다), py7zr 표본의 평문 StreamsInfo 를 고친 것, 시험이 손으로 지은 머리(`Mini7z` — 손으로
지은 잠긴 머리를 **라이브러리가** 여는 것이 우리 머리 풀기의 오라클이다). 우리 열쇠 유도는 라이브러리의 것(반사로 부른다)·참조 구현과 같다. 받을 수
없는 반복(25·30·62)은 2초 제한의 데몬 스레드에서 '풀지 않는 방식' 으로 끝나야 한다.

**기기에서 아직 보지 않은 것.** ① 반복 62 의 7z 에 암호 — 곧바로 '풀지 않는 방식'(앱이 멎지 않는다), 정상 7z(반복 19, 머리까지 잠근 것 포함)는
그대로 열린다. ② 이름에 U+202E 가 든 파일의 받은 화면·뷰어 제목(목록은 봤다). ③ 편집기의 그림 넣기·내보내기가 평소대로 되는지(시스템 고르개).
반복 24 의 7z 를 라이브러리가 폰에서 몇 초에 유도하는지(2^24 번 — 끝은 있다). 7-Zip 이 실제로 만든 머리 잠금 7z(우리 표본은 py7zr·합성 — 우리
머리 풀기의 LZMA 갈래를 실물로 탄 적이 없다).

**알고 둔 것**(점검이 냈지만 고치지 않는다 — 리드·사용자). 편집기의 붙이기 확인. 제자리로 연 받은 파일을 읽기 전용으로 다루기(지금은 사본만 읽기 전용이다).
디버그 키 서명(사용자 결정). `:editor` 프로세스는 권한 경계가 아니다(같은 앱·같은 UID — 위 '편집기를 안으로'). 폰의 보안 패치 수준(이 앱이 고칠 수 없다).

**남은 일.** 기기 확인(위). 방향 제어 글자를 아직 날것으로 보이는 자리 — 스낵바·대화상자의 문장에 넣는 이름(`‘%1$s’` 꼴), 재생 알림(시스템이 그린다 —
제목이 메타데이터라 자막 짝짓기에 쓰여 바꾸지 않았다). 압축 화면의 `MemoryLimit` 이름 보기(commons-compress 의 것 — 릴리스에서 이름이 줄어 '깨진 파일'
이 된다, 그 타입은 `feature:archive` 에서 보이지 않는다).

## 더할 만한 기능 후보 (검토만, 코드 없음 — 사용자가 고르기 전)

비슷한 앱들(MiXplorer·Solid Explorer·Material Files·Files by Google·VLC·MX Player·Moon+ Reader·Perfect Viewer·Mihon·Xodo 등)의 공식 자료로 고른
후보다. 모두 **INTERNET 없이** 되고 상위 묶음에는 위험 권한이 없다(Play 프로텍트 판정에 새 재료를 주지 않는다 — 추론). 코드로 확인한 빈자리 —
`recent_location` 표에 쓰는 코드가 없고(`visit` 호출 0건), `folder_pref`(폴더별 정렬·보기)도 DAO 만 있다, 미니 재생 바에 이전·다음 단추가 없다,
폰(API 31)에는 PDF 찾기가 없다(`Page.searchText` 는 API 35).

| 순위 | 후보 | 규모 | 메모 |
|---|---|---|---|
| 1 | **PDF 찾기(API 31 에서도)·목차·문서 안 링크** | M | 검증한 글 꺼내기(18문서 재현율·정밀도 1.0) 위에 쪽마다 찾기(진행·취소, 쪽당 상한 그대로), `/Outlines` 사슬(방문 집합·개수 상한)과 `/Link` 주석. 읽기만. 11단계 '미룬 것' |
| 2 | **재생 작은 묶음** — 수면 타이머, 미니 바 이전·다음, 자막 크기·위치, 외부 자막 싱크(±0.1초 — `SubtitleSource` 가 바이트를 이미 가로챈다, 내장 자막은 밖), `.smi`(SAMI → SRT), 화면 비율·확대 | 대부분 S, .smi M | VLC·MX Player 의 관행. ASS 서식을 조판하지 않는다는 결정은 그대로 |
| 3 | **저장소 훑기 엔진** 하나로 셋 — 용량 분석(폴더 크기 나무·큰 파일·종류별), 저장소 전체 찾기와 최근 받은 것(MediaStore 로 빠르게 + 훑어 채우기), 중복 찾기(크기 → 앞뒤 64 KiB → SHA-256, 남길 것을 고르고 나머지는 휴지통) | M×3 | 결과는 메모리에만(색인을 디스크에 두지 않는다 — '전문검색 인덱스' 와 다르다). `Os.lstat` 훑기(1만 개 394 ms)와 ZIP 만들기의 세 겹(링크·inode·깊이 64)을 다시 쓴다. `/Android/data` 는 셀 수 없어 '보이지 않는 몫' 으로. '용량 분석' 은 1단계 제외 항목(영구 아님). Web 의 큰 받기·` (1)` 사본에 쓸모 |
| 4 | **기본기 묶음** — 표 둘 살리기(폴더별 정렬·최근 위치), 폴더 정보(크기·항목 수), 경로 복사, 파일 즐겨찾기·지우기 전에 묻기(지금은 길게 누르면 곧바로 지운다), 폴더 공유 → 'ZIP 으로 묶어 보내기'(지금은 폴더가 말없이 빠진다), 홈 화면 바로가기(`requestPinShortcut` — 내보낸 화면으로 들어오는 경로를 `/storage` 아래·휴지통 밖으로 검증), 앱 테마 스위치, 체크섬(MD5·SHA-1·SHA-256·CRC32 와 곁의 `*.sha256` 대조), 썸네일 넓히기(APK 아이콘·앨범 아트·PDF 첫 쪽 — PDF 는 메모리에만, 암호 PDF 는 빼고) | 각 S, 합쳐 M | — |
| 5 | **여러 개 이름 바꾸기** — 찾아 바꾸기·앞뒤 붙이기·번호·날짜(수정일·EXIF)·대소문자, 미리보기에서 겹침과 못 쓰는 글자 | M | Web 의 갤러리 받기가 `{id}_{제목}_p000` 꼴로 쌓는다. 화면과 엔진이 같은 `sanitize`(3·4단계 치명 결함의 교훈), 파일 작업 큐·O_EXCL, a↔b 는 임시 이름 두 단계, 되돌리기 기록. **이어보기·읽던 쪽의 키에 이름이 든다** — 바꾸며 키도 옮긴다 |
| 6 | **골라서 풀기 + AES-256 암호 ZIP 만들기** | M | 압축 목록에서 여럿 골라 기존 풀기 큐로(항목 신원은 인덱스). 암호 ZIP 은 WinZip AES(AE-2)를 직접 쓰거나(데이터 기술자 없는 ZIP 쓰기가 이미 있다 — 읽기 쪽 `ZipDecryption`·7-Zip 이 오라클) zip4j(Apache-2.0 — 새 의존, 승인). commons-compress 는 ZIP 암호를 쓰지 못한다. ZipCrypto 는 만들지 않는다. PC 의 7-Zip 으로 열리는 '금고' 역할. **AES 암호 ZIP 만들기는 했다**('14단계 뒤 — AES 암호 ZIP'). 골라서 풀기는 아직이다 |
| 7 | **갤러리 다듬기** — 찍은 날(EXIF `DateTimeOriginal` — Room 캐시)로 묶기, 영상 넣기, 앨범(폴더) 보기, 여럿 골라 위치 지우고 공유·ZIP·삭제·이동, `Android/media`(메신저 사진)도 훑기 | M | 지금은 수정일로 묶는다(`GalleryScanner`). Aves 의 지도·역지오코딩은 인터넷이라 가져오지 않는다 |
| 8 | **다른 저장소(SAF)에서 열기·보내기 + 'Files 에 저장' 공유 대상** | S~M + M | `ACTION_OPEN_DOCUMENT`·`CREATE_DOCUMENT` — 네트워킹은 제공자 앱(드라이브 등)이 하므로 INTERNET 없이 클라우드에 닿는다(폴더 통째 고르기는 확인 못 함). 공유 대상은 따로 된 액티비티 별칭으로(사진·영상 공유 목록에 이 앱이 끼면 거슬린다던 판단), 이름은 `NewFiles.place`(O_EXCL) |

그다음 — **읽어 주기(TTS)**(플랫폼 `TextToSpeech`, 매니페스트 `<queries>` 에 TTS_SERVICE. 화면이 꺼진 채 읽으려면 재생 서비스·이어폰 단추와
다툰다 — 첫 판은 화면이 켜진 동안만. 'EPUB 미디어 오버레이는 넣지 않는다' 와 같은 묶임이라 **사용자 결정**. 읽는 글이 TTS 엔진 앱으로 넘어간다),
만화의 여백 자르기·넓은 쪽 나누기·밝기(Mihon·Perfect Viewer — 나누면 쪽 번호와 칸 번호가 갈려 이어보기 셈을 고친다), 이미지 도구(왼쪽 회전·뒤집기 —
EXIF 2·4·5·7·8 은 무손실, 슬라이드쇼, 배경화면 `getCropAndSetWallpaperIntent` — 권한 없이, 크기·형식 바꾸기, 사진 여러 장 → PDF `PdfDocument`),
영상 도구(화면 캡처 `PixelCopy`, 프레임 넘기기, 무손실 회전 저장 `MediaMuxer.setOrientationHint`), 소리 효과(`Equalizer`·`LoudnessEnhancer`·
`DynamicsProcessing` — 권한 없음, 삼성의 음향 효과와 겹칠 수 있다), 오디오(지금 곡의 태그·앨범 아트, `.m3u`·`.m3u8` 저장·열기 — `#EXT-X-` 면 HLS,
큐 편집. 태그 고치기는 뒤로 — jaudiotagger 는 LGPL·옛 API 문제), Web 과 함께 우고이라 움직이게(Web 이 `ugoira_meta` 의 프레임 시간을 곁 파일로),
16진수 보기, 색인 없는 내용 찾기, 폴더 한쪽 맞추기(SD·USB — 미리보기, 지울 것은 휴지통), PDF 쪽 뽑기·합치기·돌리기(L — 암호 PDF 를 다시 쓰는
쓰개 위에), 그림 자막(PGS·VobSub — 지금 '그리지 않는다'), 이동·이름 바꾸기 되돌리기.

**하지 말 것**(조사의 판단 — 바꿀 수 있다): 네트워크 저장소·캐스팅·자막 내려받기·지도·클라우드 동기화(INTERNET — 사용자가 걷었다, 다시
열면 OS 의 소켓 차단 겹을 잃는다 — 클라우드는 위 8번의 SAF 로), 앱 안 파일·FTP 서버(Web 의 테더링 파일 서버가 한다), 보안 삭제(플래시는 덮어써도
남는다 — FAST '11), 앱 잠금·독자 형식 금고(6번의 AES ZIP 으로 — 꼭 필요하면 `BiometricPrompt`), 탭·듀얼 페인(폰 화면에 L — 붙여넣기 바가 이미
아무 폴더로나 옮긴다), 홈 위젯·빠른 설정 타일(바로가기·미디어 알림이 한다, Glance 는 새 의존), 전문검색 색인(문서 내용이 디스크에 남는다),
오디오 라이브러리 DB(폴더 중심과 '목록을 그리려고 파일을 열지 않는다' 에 거꾸로), FFmpeg·libraw(네이티브 금지), 압축 안의 문서·영상 바로 열기(평문
사본 불변식 — 6번의 골라서 풀기가 출구), USB OTG 전용 구현(OS 가 마운트하고 모든 파일 권한이 닿는다 — 기기에서 볼 일). 이미 정한 것(APNG·텍스트
자동 새로고침·RAR 만들기·PDF 형광펜)은 그대로다.

모듈이 거의 겹치지 않아(docview · player/playback · core:io/browser · archive) 사본 넷에서 함께 지을 수 있다(Gradle 은 빌드 잠금으로 하나씩).

## 에뮬레이터로 검증되지 않는 것 — 문서에 그대로 남긴다

실기기 adb 연결은 하지 않기로 했다. 아래는 **코드는 쓰되 미검증으로 남는다.**

- 블루투스 연결·해제 — 해제 때 멈춤은 media3 의 `setHandleAudioBecomingNoisy(true)` 가 하고(`PlaybackService`), 연결 때 이어 틀기는 `BluetoothResume`
  이 한다(설정 '블루투스가 연결되면 이어서 재생', 기본 켜짐 — 위 '14단계 뒤 — 슬라이더 통일·…' 절). 차례는 JVM 시험(`BluetoothResumeTest`)이 박지만
  실제 장치가 붙고 떨어지는 알림은 에뮬레이터로 낼 수 없다 — 재생 서비스가 장치 알림을 등록하고도 죽지 않는 것까지만 봤다
- SD·USB OTG 의 실제 마운트 경로와 동작
- Doze 상태의 장시간 재생
- 기기별 하드웨어 코덱(AC-3·DTS·HEVC 10bit), AVIF·HEIC 지원 여부
- **편집기(rhwp·ONLYOFFICE)의 실기기 메모리** — 큰 문서(86쪽 한글·수십 MB 시트)를 에뮬레이터에서는 열었지만 사용자 폰의 메모리에서 렌더러가 버티는지는
  모른다. 렌더러가 죽으면 편집기는 '편집기가 멈췄습니다' 를 보이고 앱은 살아남는다(`onRenderProcessGone`)

---

## 모듈 구조

```
app/                      조립만. 화면 전이와 이음매 꽂기(ExtractSupport·CoverSupport·
                          PlayerSupport(재생 화면 열기·뿌리인 재생 화면 밑의 파일 목록 열기 — FileListLauncher)·
                          DocumentSupport·CropSupport). 편집기가 돌려준 문서의 저장 자리(EditorSlots·EditorSlotProvider)
                          ※ `FormatRegistry` — 무엇을 무엇으로 여는지 아는 유일한 곳(지금은 EPUB·PDF).
                            판별기(`FormatProbe`)를 여기서 돌린다
core/
  model/   [순수 JVM]     FileEntry 등 값 타입 + IroDispatchers + DayBucket(날짜 묶기) + PathNames
  safety/  [순수 JVM] 2   상한·스트림 가드·SafeXml·ImageLimits·ComicLimits·PdfLimits·DecryptLimits — 방어 상한의 유일한 지점
  charset/ [순수 JVM] 7   인코딩 감지. 판정을 부르는 곳은 아래 문단
  io/      [Android]      볼륨·권한·나열·파일조작·휴지통 · ZIP 만들기(암호 ZIP 의 쓰개 `AesZipWriter` 포함)·잘라 저장의 쓰기·구간 저장과 소리만 뽑기(`clip/`) · 글 저장·새 글 파일(`TextSaver`)
                          · 다른 앱으로 열기(`ExternalOpen`)·다른 앱에서 받기(`IncomingFiles`)
                          · 편집기와의 약속(`EditorApp`)·편집기가 돌려준 문서의 저장(`EditorSaver`)·새 문서의 서식(`BlankDocuments`·`NewDocuments`)
  data/    [Android]  2   Room + DataStore + 크래시 기록(`CrashLog`)
  ui/      [Android]      테마·공용 컴포저블·제스처·이미지 디코딩·ThumbnailStore·애니메이션 페인터·`IroSlider`·`IroProgress`
  playback/[Android]  5   MediaSessionService·세션·커넥션·이어보기·미니 바·문구. **화면은 없다**(10)
                          ※ `core:model` 을 **api** 로 낸다 — `FileEntry`·`FileKind` 가 공개 API 에 있다
  webhost/ [Android] 11   잠금 WebView 호스트(`LockedWebView`·`WebHost`). 포맷을 모른다. 스크립트를 켜는 갈래는 없다
format/    ★ 전부 java-library(순수 JVM). android.* 가 컴파일 단계에서 막힌다
  api/     2   DocumentSource·DocumentOpener·OpenFailure·ProgressSink
  archive/ 8·14(tar)·14 뒤(단일 압축 스트림·cpio·ar)   text/ 7·14(마크다운)
  html/ 11(위생·껍데기·`HtmlWriter`)   epub/ 11
feature/   browser(3·4·즐겨찾기·갤러리) image(6 — 잘라 저장의 고르는 화면·뜨는 쪽 · 그리기·메모) text(7 · 글 편집기) archive(8) comic(9)
           player(10 — 구간 저장 칸) docview(11 — `pdf/text` 는 PDF 글 꺼내기) diag(2) settings(14)
           editor(편집기 — rhwp·ONLYOFFICE 를 WebView 에 띄운다, 따로 된 프로세스 `:editor`. `core:io` 만 본다)
           ※ `feature:docview` 가 보는 포맷 모듈은 **`format:epub` 하나뿐이다** — PDF 는
             `android.graphics.pdf` 라 순수 JVM 인 `format/` 에 둘 수 없고, 계약
             (`DocumentOpener`)만 `format:api` 에서 가져온다
tools/     check_api_level.py(순수 JVM 모듈의 API 수준 — 아래 '빌드와 검증'), editor/(편집기 엔진 묶음을 짓는다 — 판·해시를 박았다)
vendor/                   .gitignore 대상. tools/editor 가 지은 편집기 엔진 묶음(서드파티 바이너리·글꼴)
samples-local/            .gitignore 대상. 실세계 문서는 절대 커밋하지 않는다
```

**`feature:player` 는 화면만 가진다**(10단계). 서비스·세션·커넥션·이어보기·미니 바·문구는 `core:playback` 이다 — 서비스와 컨트롤러가 거기 있고,
10단계가 자막·PiP·제스처·속도/트랙/A-B 를 더하며 화면이 모듈의 대부분이 되어 화면만 옮겼다.

**미니 바를 함께 옮기지 않은 것이 요점이다.** `feature:browser` 가 그것을 그리므로
`feature:player` 로 옮기면 feature 끼리 참조가 된다. 대신 미니 바가 화면을 열던 자리
(`Intent(context, PlayerActivity::class.java)`)를 **세 번째 이음매**로 끊었다 —
`PlayerSupport` + `interface PlayerLauncher`, 구현은 `feature:player` 의 `PlayerEntry`,
꽂는 것은 `IroiroApp.onCreate`. `ExtractSupport`(8)·`CoverSupport`(9)와 같은 모양이다.

**이 이음매만 함수가 둘이다** — `open(context, showPlaylist)` 과
`intent(context, showPlaylist)`. 재생 알림이 누를 곳을 지정하려면 `PendingIntent` 가
필요하고, 그것은 '실행' 이 아니라 **인텐트** 를 요구한다. 세션은 `core:playback` 이
만드는데 화면은 `feature:player` 에 있으므로 그 인텐트도 이음매를 건너와야 한다. 뿌리인 재생 화면의 뒤로 가기가 파일 목록을 여는
함수(`PlayerSupport.FileListOpener`)도 같은 이음매에 있다.

**의존 방향을 어기는 것은 회귀다.**

| 모듈 | 참조해도 되는 것 |
|---|---|
| `app` | 전부 |
| `feature:*` | `core:*`, `format:api`, 자기 포맷 모듈 하나. **feature 끼리 참조 금지** |
| `format:*` | `core:model`, `core:safety`, **`core:charset`**, `format:api`, coroutines-core. 그 안에서 기반으로 삼아도 되는 것은 `api`·`archive`·**`html`** 셋뿐 |
| `core:io/data/ui/webhost` | `core:model`, `core:safety`, `core:charset` |
| `feature:editor` | **`core:io` 하나**(편집기와의 약속 `EditorApp` 의 글자열) — 파일 쪽과는 인텐트와 저장 자리 제공자로만 잇는다. 따로 된 프로세스에서 돌므로 파일 쪽의 상태(DB·DataStore·작업 큐)를 보지 않는다 |
| `app` | 위의 전부에 더해 **`format:archive`·`format:epub` 도 직접 본다** — `FormatRegistry` 가 판별에 ZIP 엔트리 이름을 보고 여는이를 골라야 한다. 조립이 `app` 의 일이다 |
| `core:io` | **예외로 `core:data` 도** — 휴지통은 '파일을 옮기는 것' 과 '어디서 왔는지 적는 것' 이 한 몸이라, 갈라 놓으면 정합성이 깨지는 자리에 인터페이스 한 겹이 더 생길 뿐이다 |
| `core:playback` | **예외로 `core:io`·`core:data`·`core:ui` 도** — `io` 는 `Iro`·`FileKey`·`MimeResolver`, `data` 는 이어보기 테이블, `ui` 는 미니 바의 진행 막대(`IroProgress`) 때문이다. 이 행이 빠지면 규칙을 계약으로 읽는 사람이 `core:playback` 을 잎 모듈로 착각한다(표가 코드와 어긋나 있던 적이 있다) |

**`core:io` 가 `format:archive` 를 보지 않는 이유와, 그래서 생긴 이음매**(8단계).
아카이브 풀기는 사용자 저장소에 쓰는 작업이라 복사·이동과 **같은 큐**(`FileOpManager`)를
지나야 한다. 그런데 큐는 `core:io` 에 있고 아카이브를 읽는 코드는 `format:archive` 에 있다.
`core:io` 가 `format:*` 을 보게 하면 **계층이 통째로 뒤집힌다**(format 이 core 를 보는 구조다).
`core:charset` 때처럼 표를 고치는 길도 있었지만 그때는 '잎 모듈이라 방향이 뒤집히지 않는다'
는 근거가 있었고 `format:archive` 에는 그 근거가 없다.

그래서 `core:io` 에 **포맷을 전혀 모르는 함수 하나짜리 인터페이스**(`ExtractSupport.Runner`)
를 두고, 구현은 `feature:archive` 가 만들며, `app`(`IroiroApp.onCreate`)이 꽂는다.
조립을 `app` 이 한다는 것은 모듈 지도가 이미 적어 둔 규칙이다. **인터페이스를 늘리는 것을
꺼리는 원칙은 그대로다** — 여기는 정합성이 아니라 계층 방향의 문제라 사정이 다르다.

**같은 이음매가 9단계에 하나 더 생겼다**(`ui.CoverSupport`). 목록의 만화 표지는
`core:ui` 의 `ThumbnailStore` 가 그리는데 아카이브를 읽는 코드는 `format:archive` 에 있다.
계층 방향이 `ExtractSupport` 와 같은 문제라 해법도 같다 — 포맷을 전혀 모르는 함수 하나
(`CoverBytes`)를 `core:ui` 에 두고, 구현(`ComicCover`)은 `feature:comic` 이 만들며,
`app` 이 꽂는다. **관문(`Semaphore(1)`)을 이음매 쪽에 두는 것**이 그때 더해진 것이다:
7z 표지 셋이 동시에 열리면 사전 메모리가 세 배가 되는데, 그 약속이 구현이 바뀌어도
유지되어야 한다. 이음매는 그 뒤 둘이 더 있다 — 문서(`DocumentSupport`, 11단계)와 잘라 저장(`CropSupport`, 14단계 뒤).

**`format:html` 이 기반 목록에 든 까닭**(11단계). `format:html` 은 `core:safety`·`format:api` 만 보는 **잎 모듈**이라 의존 방향이 뒤집히지 않고,
위생 규칙이 두 벌이 되면 한쪽만 고쳐지는 날이 온다 — EPUB(`format:epub`)과 마크다운 미리보기(`format:text`)가 같은 위생기·껍데기를 쓴다.
(모듈 지도가 나눠 두라고 적은 것을 의존 표가 금지하고 있던 자기모순을 고친 것이다 — 자기모순인 규칙은 "어기지 않는다" 가 성립하지 않는다.)

**`core:charset` 이 표에 든 경위**(7단계). 모듈 지도는 처음부터 "인코딩 감지. 텍스트·
자막·zip 파일명이 공유한다" 고 적었는데 zip 파일명 판정은 `format:archive` 에 살아서,
**지도가 공유하라고 적은 것을 표가 금지하고 있었다.** `core:charset` 은 순수 JVM 이고 다른 모듈을 보지 않는 잎 모듈이라 의존
방향이 뒤집히지 않는다. **zip 파일명도 `core:charset` 을 쓴다**(14단계 — 그 전에는 `format/archive/EntryNameDecoder.kt` 가 CP949 후보 탐색과 점수
판정을 따로 한 벌 더 갖고 있었다). 지금은 UTF-8 표시만 먼저 보고 `CharsetDetector.detect` 를 부른다(유니코드 경로 추가필드는 `ZipArchiveReader`
가 판정 앞에서 따른다). 합칠 때 바뀐 답은 개선으로 적었다(Shift_JIS·UTF-8 무표시 `café`·Big5). **여전히 틀리는 것** — 짧은 간체 중국어 이름,
ASCII 낱말 끝에 붙은 UTF-8 로도 읽히는 한 글자(`Excel처` → `Exceló`, `café` 를 살리는 대가).

판정(`CharsetDetector.detect`·`BinarySniffer.sniff`·`Bom.detect`)을 부르는 곳은 넷이다 — `feature:text`(화면이 판정 근거까지
그린다), `core:playback`(자막 `SubtitleSource`), `format:archive`(`EntryNameDecoder`), `core:io`(`ContentSniff` — `.ts`·`.mts` 를
글로 보낼지 `BinarySniffer` 로 정한다. 텍스트 뷰어가 받는 것만 글로 보내려는 것이라 **같은 판정기·같은 앞머리 길이**여야 한다).
`format:text` 는 값 타입 `TextEncoding` 과 `WindowedDecoder` 만 쓴다. 안드로이드의 별칭(`x-windows-949` → `EUC-KR`)을 찾는 것은
`TextEncoding.CP949` 한 곳이다.

**판정 규칙을 손볼 때는 소비자의 시험을 함께 돌려라** — 규칙은 한 곳이지만 답이 가장 크게 흔들리는 것은 파일명이다(짧고, 사용자가
이미 그 이름을 보고 있다). `EntryNameRegressionTest`(오늘의 답)와 `EntryNameConsistencyTest`(텍스트 판정과 같은 답)가 지킨다.
`BinarySniffer` 를 고치면 `ContentSniffTest` 도 본다.

**파서를 순수 JVM 에 두는 이유는 빌드 시간이 아니라 테스트 루프다.** dex·AAPT·에뮬레이터
없이 초 단위로 돌릴 수 있어야 파서 개발이 성립한다. 그래서 **이미지 디코딩은 파서가 하지
않는다** — `format:*` 은 바이트를 내주는 데까지만 하고(압축 항목의 스트림, EPUB 의 자원) 그림을 푸는 것(`BitmapFactory`·
`ImageDecoder`)은 `feature`·`core:ui` 가 한다.

---

## 지켜야 할 규칙

### 안전

- **선언한 `foregroundServiceType` 마다 대응 권한이 있어야 한다.** `dataSync` 서비스에
  `FOREGROUND_SERVICE_DATA_SYNC` 가 없으면 `startForeground` 가 `SecurityException` 으로 즉사한다.
- **`INTERNET` 을 선언하지 않는다**(`tools:node="remove"` — 나중에 붙는 라이브러리가 제 매니페스트에서 병합해 들여오는 것까지 막는다). 이 한 줄이
  EPUB 의 추적 픽셀·문서의 외부 참조·편집기·모든 서드파티의 소켓을 OS 수준에서 한꺼번에 막는다. 라이브러리가 `uses-permission-sdk-23` 으로
  들여오는 모양도 따로 막고(remove 는 그 요소 이름에만 듣는다 — 함정 표), `assemble` 이 병합 매니페스트를 보고 INTERNET 이 있으면 멈춘다
  (`verify<변형>NoInternet`). 그 밖의 겹 — 문서를 그리는 WebView 의 `blockNetworkLoads` 명시와 가로채기의 빈 404, 재생기의 `LocalOnlyDataSource`
  (file·content 만) — 도 그대로 둔다. **다시 열게 되면** 위 '다음 방향' 과 아래 '문서를 그리는 WebView 는 스크립트를 켜지 않는다' 를 먼저 본다.
- **`FileProvider` 는 `/storage/` 와 캐시 하위 폴더만 노출한다.** `root-path "/"` 로 두면
  앱 내부 DB·크래시 로그까지 넘어간다. `getUriForFile` 호출은 한 함수에 가두고 그 안에서
  `canonicalFile` 이 허용 루트 아래인지 검사한다.
- **다른 앱이 이 앱에 넘긴 설치 파일(APK)은 설치 관리자에 넘기지 않는다** — 받는 화면이 이름과 내용(ZIP 의 가운데 목록에 `AndroidManifest.xml`)으로
  보고 어느 화면으로도 보내지 않고(`refusalOf`·`ApkFiles`), `ExternalOpen.open`·`ShareHelper.share` 가 받은 사본을 한 번 더 거른다. 넘기면 설치
  관리자는 이 앱이 보낸 것으로 받는다 — 보낸 앱이 이 앱의 설치 권한을 빌려 쓴다. 파일 목록에서 고른 APK 는 그대로 간다.
- **이 앱이 짓거나 받는 이름에서 글의 방향을 바꾸는 글자는 `_` 로**(`PathRules.sanitize`·편집기의 `cleanName`·`OutputNames` — `PathNames.isBidiControl`),
  이미 있는 이름은 **화면에서** 보이는 표시로(`PathNames.forDisplay` — 실제 이름은 그대로). U+202E 하나로 `.apk` 가 `.pdf` 처럼 보인다.
- **다른 앱에 넘기는 파일도 그 URI 로만, 읽기 권한만 준다**(`ExternalOpen` — '다른 앱으로 열기' 절). 휴지통은 볼륨 안이어도 넘기지 않는다.
  이 일 때문에 권한 하나(`REQUEST_INSTALL_PACKAGES` — APK 를 설치 관리자에 넘길 때 선언이 없으면 말없이 닫힌다)와 `<queries>`
  (content URI 를 VIEW 로 받는 앱이 보인다)가 늘었다. 이 앱은 스스로 무엇도 설치하지 않고, 본 앱 목록을 보여 주거나 저장하지 않는다.
- **다른 앱이 넘긴 파일은 `IncomingFiles` 를 지나야만 연다**(내보낸 `OpenActivity` — '다른 앱에서 이 앱으로 열기'). 스킴은 `content`·`file` 둘이고,
  `file:` 과 제자리는 **정규화한 경로가 `/storage/` 아래**일 때만(휴지통 제외), 우리 자신의 제공자를 가리키는 URI 는 받지 않는다 — 우리는
  우리 제공자를 허락 없이 열 수 있어서, 남이 지어 보낸 URI 로 보낸 쪽이 못 읽는 파일을 우리가 대신 열게 된다. 이름은 보낸 쪽이 적은 글이라
  마지막 조각만 다듬어 쓰고, 사본은 `share/inbox/<무작위>/` 에 두어 읽기 전용으로 다루며 화면이 끝나면 지운다. **APK·글꼴 형식은 필터에
  적지 않는다** — 적으면 우리 '다른 앱으로 열기' 가 우리를 다시 고르는 고리가 된다. 공유(`ShareHelper`)와 '다른 앱으로 열기'
  (`ExternalOpen`)의 고르는 창은 우리를 뺀다.
- **내보낸 액티비티(`MainActivity`·`OpenActivity`)는 인텐트 extras 를 ViewModel 의 저장 상태로 넘기지 않는다**(`withoutIntentArgs` — 함정
  표). 새로 액티비티를 내보내면 같은 재정의를 단다. 남이 실은 extras 를 꺼내는 자리(공유의 스트림)는 꺼내다 던지는 것을 잡는다.
- **파서 방어 상한을 한곳(`core:safety`)에 못 박는다.** 엔트리 10,000 / 단일 해제 256MB /
  총 해제 1GB / 압축비 100:1 / XML 입력 32 MiB / 파일당 30초 / **재생 큐 500항목**.
  **선언된 크기를 믿지 않고 실제 읽은 바이트를 센다.** 오프셋은 `Long` 으로 읽고
  `Math.toIntExact` 로 변환한다.
  **XML 중첩 깊이 상한은 없다**(2026-11-14 사용자 결정으로 지웠다 — `SafeXml` 의 `nextGuarded`·`nextTagGuarded` 와 `maxXmlDepth`). 이 앱의
  XML 파서(`EpubPackage`·`EpubToc`·`EpubEncryption`)는 재귀 없이 `parser.next()` 를 고리로 돌아 겹이 깊어도 스택이 자라지 않고, 파서가 드는
  요소 이름 목록은 입력 32 MiB 상한에 묶인다. **XML 을 재귀로 읽는 파서를 새로 짓는다면** 그 파서에 깊이 상한을 다시 둔다.
  **단서 — 포맷의 구조 상한은 그 포맷 곁에 둔다.** 그 포맷만 아는 상한(tar 의 PAX 머리, 이름 머리의 합, 해제기 사전의 메모리처럼)은
  모듈의 한 파일(`format:archive` 의 `TarLimits`·`EntryNameLimits`·`StreamLimits`)이나 그 클래스의 동반 객체에 모아 둔다. `core:safety` 에
  두면 거기가 모든 포맷의 구조를 알아야 한다. **포맷과 상관없는 예산**(위의 목록, 암호를 푼 평문 한 벌의 `DecryptLimits`)만 `core:safety` 다.
- **컨테이너 깊이 상한은 없다. `maxContainerDepth` 는 11단계가 지웠다.**
  읽는 코드가 한 줄도 없는 상수였고, **세는 코드를 넣는 쪽이 아니라 상수를 지우는 쪽으로** 끝냈다 — 깊이를 세려면 '지금 몇
  겹인가' 를 아는 주체가 있어야 하는데, **뷰어에 넘기는 것이 언제나 파일 경로**라
  (`DocumentSource.asFile`) 아카이브 엔트리는 애초에 뷰어에 닿지 않는다. 컨테이너 안의
  컨테이너는 **열리지 않아서** 막히는 것이지 세어져서 막히는 것이 아니다. 실제로 막는
  자리 셋 — 압축 목록의 중첩 아카이브와 문서(둘 다 '풀어서 여세요'), 그리고
  `PdfOpener` 의 `asFile() ?: Unsupported`.
  **새 포맷을 붙일 때 이 문장을 '이미 막혀 있다' 로 읽지 마라** —
  정말 깊이를 세야 하는 자리가 생기면 **그때 호출부에 직접 넣는다.** 아무것도 막지
  못하면서 막는다고 적혀 있는 값이 가장 나쁘다는 것이 이 항목이 남긴 교훈이다.
- **CFB(OLE2)를 다시 해석하게 되면 전용 불변식이 따로 필요하다** — 압축·XML 상한이 걸리지 않는 구조다(걷은 `format:cfb` 가 지키던 것).
  `sectorShift` 는 9 또는 12 만, 섹터 번호는 범위 검사 후에만 seek, 모든 체인 순회에
  방문 집합과 길이 상한, 스트림 길이는 `min(선언값, 체인길이×섹터크기)`. (지금은 편집기가 돌려준 바이트의 앞머리만 본다 — `EditorSaver.looksLike`.)
- **원본 파일을 파괴할 수 있는 경로가 열이다** — EXIF 저장, 덮어쓰기 복사,
  EXDEV 이동, 휴지통 복원 충돌, 아카이브 "여기에 풀기", 그리고 곁에 새 파일을 만드는 셋 — **ZIP 만들기·사진 잘라 저장(그리기·메모 포함)·구간 저장**
  (새 파일이지만 이름을 잘못 고르면 남의 파일을 덮는다. 다 쓰고 내린 뒤 끝에 `O_EXCL` 로 없는 이름을 맡는다 — `NewFiles.place`. 소리만 저장은
  구간 저장의 한 갈래다. 그리기·메모는 잘라 저장의 요청에 그릴 것을 싣는 한 갈래다 — `SaveCrop.overlay`. 암호 ZIP 은 ZIP 만들기의 한 갈래다 —
  같은 임시본·같은 놓기이고, 머리의 크기 칸을 고쳐 쓰는 위치 지정 쓰기는 그 임시본 안의 일이다 — `AesZipWriter`),
  그리고 **글 편집의 저장**(`TextSaver.save`: 원본 자리에 임시본 → fsync → rename, 세션의 첫 저장이면 먼저 원본의 사본을 휴지통에
  넣고(`TrashStore.copyToTrash`) 못 넣으면 묻는다, 연 뒤 밖에서 바뀌었으면 묻는다). 같이 생긴 쓰기 길 둘 — 휴지통에 사본 넣기(사이드카 → 임시본 →
  rename → 기록), 새 글 파일과 새 문서(`O_EXCL` 로 맡는다 — 남의 파일을 덮지 않는다. 새 문서는 서식을 다 쓰고 다시 읽어 확인한 뒤 놓는다 —
  `NewDocuments`). 전부 **임시 파일 → fsync → 원자적 rename**, 삭제는 성공 확인 뒤에만.
  열째는 **편집기가 돌려준 문서의 저장**(`EditorSaver`: 앞머리로 형식을 확인하고(OOXML 은 ZIP 또는 암호의 CFB, 글은 다른 그릇이
  아닐 것) 512 MiB 까지, 원본 자리에 임시본 → fsync → rename, 세션의 첫 저장이면 먼저 원본의 사본을 휴지통에 넣고 못 넣으면 쓰지 않는다, 형식·이름을
  바꿨으면(이전 오피스 형식·PDF 는 언제나 OOXML 로 — 바뀐다) 원본은 두고 곁에 `NewFiles.place`). 반영마다 따로 된 바이트(`commit-<id>`)로 옮긴 뒤 넣고,
  기대한 (크기·시각)을 실어 큐 안에서 다시 견준다.
- **편집기(`feature:editor`, 따로 된 프로세스 `:editor`)의 바이트가 원본에 닿는 길은 하나다** — 저장 자리(`EditorSlotProvider` — 내보내지 않은
  제공자, 주 프로세스) → 파일 작업 큐 → `EditorSaver`. 편집기는 사용자 파일에 직접 쓰지 않는다. 같은 앱(UID)이라 저장 자리에 따로 권한을 주지
  않고, 편집기는 부른 쪽이 이 앱인지 본다(화면을 내보내지 않는다). 편집기는 남이 만든 문서를 큰 자바스크립트 편집기로 다룬다 — 그 WebView 는 자기
  엔진의 가짜 출처(`https://<엔진>.iroiro-editor.invalid`)만 내주고(가로채기·CSP), 문서의 링크는 묻고 브라우저 앱으로 넘기며, 쪽의 탐색을 모두 막는다.
  그림 넣기·내보내기에서 시스템 고르개가 돌려준 주소는 **남의 `content` 주소만** 받는다(`EditorRules.acceptsReturnedUri`). 이 앱의 파일·제공자를
  가리키면 이 앱의 신원으로 읽거나 쓰게 된다.
- **문서를 그리는 WebView(`core:webhost` 의 `LockedWebView`)는 스크립트를 켜지 않는다** — 켜는 길이 코드에 없다(
  그 머리말 '스크립트는 켜지 않는다'). 사용자 파일을 읽을 수 있는 쪽에서 스크립트가 돌면 가로채기·CSP 가 막지 못하는 길(DNS 미리 찾기·WebRTC·클립보드)로
  내용이 나갈 수 있다. HTML 의 그린 모양은 '브라우저로 열기' 로 다른 앱에 넘긴다. 편집기의 WebView 는 따로다(자기 엔진의 출처만 — 위 항목).
- **7z 의 AES 열쇠는 반복 지수 0~24·63 일 때만 유도한다**(`SevenZKeyGuard` — 라이브러리에 암호를 넘기기 전에 머리를 읽어 본다). 넘으면 '풀지 않는 방식'
  이다.
- 진짜 메모리 안전성 위반은 우리 Kotlin 파서가 아니라 플랫폼 네이티브 디코더(libwebp·
  skia·pdfium)에서 난다. 그것을 **모든 파일 접근 권한을 가진 프로세스**에서 부른다는 것을
  잊지 마라. 렌더 인터페이스는 처음부터 프로세스를 넘길 수 있는 모양(FD + 파라미터 →
  Bitmap)으로 짠다.

### 코드

- 오류는 예외가 아니라 `sealed OpenFailure` 로 돌려주고 부분 성공을 1급으로 다룬다.
  경계 catch 의 첫 줄은 언제나 `if (e is CancellationException) throw e`.
- **`runCatching` 은 그 규칙을 어기는 가장 쉬운 길이다.** `Throwable` 을 잡으므로
  취소까지 삼킨다. 삼킨 자리 아래에 정지 지점이 없으면 **취소된 코루틴이 끝까지
  달려** 이미 닫힌 자원을 안은 상태를 다시 세우고, 버퍼를 가진 `Channel` 에 이벤트를
  넣어 **다음 문서의 화면에 배달한다**(`ComicViewModel.restore` 가 이 모양이었다).
  정리(`finally` 안의 `close`)에만 쓰고, **suspend 호출을 감쌀 때는
  쓰지 마라** — 취소를 되던지는 try/catch 를 손으로 쓴다.
- **화면에 나가는 글자는 예외 없이 `strings.xml` 에 둔다.** 코드에 문자열 리터럴을 적지
  않는다 — 나중에 용어를 통일하려 할 때 코드를 뒤져야 하는 상태가 되면 통일이 안 된다.
  **예외는 진단 화면(`feature:diag`) 하나다.** 개발자만 보는 화면이고 용어를 통일할
  대상이 아니다. 대신 **순수 JVM 모듈은 화면 문구를 만들지 않는다** — `ArchiveSelfTest`
  가 문장이 아니라 `Check` 와 값을 돌려주는 것이 그 규칙이다. 모듈 경계가 뜻을 잃으면
  나중에 번역·용어 통일이 `format/` 까지 들어가야 한다.
- **화면의 설명은 짧게 둔다** — 한두 문장(무슨 일인지·무엇을 할지)과 꼭 필요한 경고(지움·덮어씀·암호·안전)만. 까닭·경위·규격·수치는
  이 문서에 둔다(사용자 — Web 과 같은 기준).
  고지(오픈소스·라이선스·한컴 문장)는 줄이지 않는다.
- **예외 메시지를 화면에 그대로 보내지 않는다.** `OpenFailure.detail` 은 사용자가 읽는
  값이고, 예외 메시지에는 절대경로와 공격자가 심은 문자열이 들어 있다. 종류별로 우리가
  쓴 문장만 쓴다(`toOpenFailure` 참고).
- **아카이브 엔트리의 신원은 이름이 아니라 인덱스다.** ZIP 명세는 같은 이름을 금지하지
  않고, 이름은 인코딩 판정을 거친 손실 가능한 문자열이다. 이름으로 찾으면 사용자가
  고른 것과 **다른 파일의 바이트**가 열린다(회귀 시험이 있다).
- **생성자에서 자원을 열었으면 초기화 실패 시 스스로 닫아라.** 생성자가 던진 객체는
  호출자가 `close()` 할 방법이 없다.
- 주석은 한국어로. **무엇을 하는지가 아니라 왜 그렇게 했는지**를 적는다.
- 디스패처: 디스크 IO `Dispatchers.IO`, 파싱·디코딩 `Default.limitedParallelism(2)`,
  썸네일 `(3)`, PDF 렌더 `(1)`. **DOM 파싱 금지, `File.readBytes()` 금지.**
- 이어보기·읽던쪽 키에 절대경로를 쓰지 않는다: `sha256(size + 파일명 + mtime)`.

### 표본

- 저장소에 커밋하는 표본은 **이 테스트를 위해 새로 만든 20KB 이하** 파일뿐이다.
- 실세계 문서는 `samples-local/`(gitignore)에 두고 없으면 `assumeTrue` 로 건너뛴다.
- 골든 테스트는 정규화 텍스트(`.golden.txt`)로만 커밋한다.
- 문서 표본은 **공개 문서를 수집해서 쓴다**(공공기관 공개 문서, 오픈소스 프로젝트의 시험 표본 — 위 '실세계 말뭉치' 절).
  미디어 표본은 ffmpeg 으로 만든다(도구로만 쓴다. 저장소에 넣지 않는다).

---

## 단계 번호

이 문서와 코드 주석이 'N단계' 로 기능이 들어온 자리를 가리킨다. 0 검증 환경 · 1 뼈대(빌드·모듈·권한·R8) · 2 공통 계약·방어 계층 ·
3 파일 브라우저 · 4 파일 조작·휴지통 · 5 재생 기본 · 6 이미지 뷰어·썸네일·EXIF · 7 텍스트 뷰어·인코딩 감지 · 8 압축(ZIP·7z·RAR) · 9 만화 뷰어 ·
10 재생 심화(VLC 차용·자막·PiP·제스처) · 11 PDF·EPUB·문서 렌더 기반(뒤에 암호 PDF·아카이브 암호) · 12·13 오피스·한글 변환기(걷었다 — 편집기가
대신한다) · 14 마감(설정·고지·1.0.0) · '14단계 뒤' 는 그 뒤의 기능들이다(위 '기능별 설계').

각 단계는 **설치해서 실제로 쓸 수 있는 APK** 로 끝난다. **위험을 앞으로, 쓸 수 있는 것을 일찍, 크고 불확실한 것을 뒤로.**

### 적대적 검토가 세운 규칙 (2단계)

| 무엇 | 왜 |
|---|---|
| 엔트리 신원을 이름 → **인덱스** | 같은 이름 두 엔트리가 하나로 접혀 **다른 파일의 바이트**가 열렸다 |
| `skip()` 을 위임 **전에** 자른다 | 먼저 위임하면 막겠다던 해제 비용을 이미 치른 뒤에 예외가 났다 |
| 총 출력 상한에 `resetOutput()` | 상한이 '한 번에 풀 양' 이 아니라 '평생 읽을 양' 이 되어, 300쪽 만화책이 1GB 에서 죽을 참이었다 |
| 7z: 메모리 상한·**건너뛰기 비용 선검사**·인터럽트 | solid 7z 는 `read()` 한 번이 앞 엔트리 전체를 우리 계측 **밖에서** 푼다 |
| 압축비 분모를 **실제 소비 입력**으로 | 헤더의 선언 압축크기는 공격자가 적는 값이다 |
| 생성자 실패 시 **핸들 자가 반납** | 생성자가 던진 객체는 호출자가 닫을 수 없다 |
| `OpenFailure` 에 `Timeout`, `detail` 위생 | 시간 상한이 조용히 사라졌고, 예외 메시지에 경로가 실려 화면까지 갔다 |
| `Documents.open()` 한 곳에서 `withTimeout` | 상한이 선언만 되고 아무 데서도 걸리지 않았다 |
| RAR 링크 판정을 `getRedirection()` 으로 | "이 API 는 없다(실측)" 고 적은 주석이 **틀렸다** — javap 정규식이 타입명의 숫자를 걸렀다(함정 표) |
| CP949 자가시험을 **고정 바이트**로 | 우리가 고른 charset 으로 쓰고 같은 것으로 읽으면 왕복이 깨질 수가 없어 아무것도 증명하지 못했다 |
| 유니코드 경로 추가필드(0x7075) 존중 | 라이브러리가 이미 푼 정답을 버리고 다시 추측해 일본어 이름을 망가뜨렸다 |

### 적대적 검토가 세운 규칙 (3·4단계)

치명은 전부 **이미 있는 기능이 사용자 파일을 지우는 자리**였다 — 새 기능보다 먼저 의심한다.

| 무엇 | 왜 |
|---|---|
| 영구 삭제에 **확인 대화상자** | '완전히 삭제'·'휴지통 비우기' 가 누르는 즉시 실행됐다. 되돌릴 수 있는 '휴지통으로 보내기' 에만 확인이 있어 **보호가 정확히 거꾸로** 걸려 있었다 |
| `nextAvailable` 이 **있는 이름을 돌려주지 않게** | 255바이트 이름에서 ` (2)` 가 통째로 잘려 후보가 원본과 같아졌다. 그것을 '둘 다 보관' 의 대상으로 쓴 복사가 남의 파일을 지웠다 |
| 휴지통 이동 세 쓰기를 `NonCancellable` 로 | `rename` 과 기록 쓰기 사이에서 끊기면 앱 안에서 되살릴 수 없는 uuid 덩어리가 남고, 30일 뒤 자동으로 지워졌다 |
| 고아를 **지우지 않고 되살린다** | 사이드카 시각을 못 읽으면 그 자리에서 영구 삭제했다. **판단 실패를 삭제로 해석**하는 코드였다 |
| 덮어쓰기에서 `delete()` 를 없앴다 | `rename(2)` 가 이미 원자적 대체다. 먼저 지우는 것은 원본도 복사본도 없는 창만 만들었고, 대상이 빈 폴더면 사용자의 폴더가 파일로 바뀌었다 |
| 항목 실패가 **배치를 끝내지 않는다** | 1,000개 중 3번째가 실패하면 나머지 997개를 시도조차 않고 진행 수까지 버렸다. 멈추는 것은 저장공간 부족 하나뿐 |
| 결과를 상태가 아니라 **사건(Channel)으로** | `StateFlow` 라 다음 작업의 `Running` 이 앞 결과를 덮었고, 화면이 없는 동안 끝난 '3개 실패' 는 아무에게도 전해지지 않았다 |
| 결과 소비를 **한 곳으로**(지금은 `Root`) | 폴더 화면만 소비해서 휴지통에서 누른 영구 삭제의 성패가 어디에도 안 보였다 |
| 복사가 **수정시각을 보존한다** | SD↔내부 이동 한 번에 사진 수천 장이 '지금' 이 되어 날짜 정렬이 무너지고, 이어보기 키(`sha256(크기+이름+수정시각)`)도 함께 끊겼다 |
| `FileUtils.copy` 에 **executor 를 넘긴다** | `null` 이면 진행 리스너가 한 번도 불리지 않아 큰 파일 하나를 복사하는 내내 멈춰 보였다 |
| 정합성 검사를 **파일 작업 큐 안으로** | 밖에서 돌아 휴지통으로 옮기는 중인 파일을 고아로 보았다. 회전마다 다시 돌기도 했다 |
| 이름 바꾸기가 **이름을 먼저 맡는다**(`O_EXCL`) | `exists()` 와 `rename` 사이에 다른 앱이 그 이름을 만들면 확인도 휴지통도 없이 덮어썼다 |
| `stopService` 를 **`startForeground` 확인 뒤에** | 400~450ms 에 끝나는 작업이 `ForegroundServiceDidNotStartInTimeException` 을 여는 창이었다 — 400ms 지연은 그것을 막으려던 장치인데 거울상을 만들었다 |
| 충돌 검사를 **엔진과 같은 이름**(`sanitize`)으로 | 화면은 원래 이름을, 엔진은 다듬은 이름을 썼다. `a?.txt` 는 리눅스에서 합법이라 경고 없이 `a_.txt` 가 덮어써졌다 |
| 폴더를 옮기면 **선택을 지운다** | 화면에 없는 파일이, 심지어 다른 볼륨의 파일이 삭제·이동 대상이 됐다 |
| `DirectoryIteratorException` 을 잡는다 | `DirectoryStream` 반복자의 RuntimeException 이 flow 밖으로 나가 `stateIn` 공유 코루틴을 죽이고 앱을 내렸다 |
| 스낵바를 **보여 준 뒤에** 소비한다 | 먼저 소비하면 키가 바뀌어 `showSnackbar` 가 취소된다 — 완료·실패·취소가 한 번도 안 떴다 |
| 충돌 대화상자에 **취소**를 넣고 한 열로 | `dismissButton` 이 왼쪽이라 '덮어쓰기' 가 평소 '취소' 자리에 있었고, 취소는 아예 없었다 |

계측 회귀를 함께 박았다(255바이트 이름·종류 다른 충돌·항목 실패 계속·수정시각 보존·고아 되살리기·볼륨 없을 때 기록 보존).

### 아직 미룬 것 — 2·3·4·6단계

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| 압축 RAR5 회귀 시험 | 표본이 생기는 대로 | junrar 에 쓰기 구현이 없어 표본을 만들 수 없다. 진단 화면이 이 사실을 '미검증' 으로 표시한다 |
| `FileObserver` 가 MediaProvider 의 변경을 통지하는가 | 재지 않았다 | 폴더 감시(`FolderWatch`)는 다른 프로세스(`adb shell`)의 변경을 2초 안에 통지했다(14단계 실측). 그래서 앞으로 돌아올 때 폴더의 수정 시각을 한 번 더 본다 |
| **`MediaIndex.scanAll` 이 정말 필요한가** | 실기기에서 한 번 확인한 뒤에 빼거나 남긴다 | 이 에뮬레이터(API 31)에서 재보니 삭제·이름변경·이동이 **스캔 없이** MediaStore 에 반영된다(내부·FAT SD 모두). '사진을 지우면 MediaStore 항목도 사라진다'(위 '파일 조작') 는 `scanAll` 이 한 일을 증명하지 않는다. 1만 개 배치마다 도는 바인더 호출이 값 없이 남아 있을 수 있다 |
| 휴지통 자동 비우기(`TrashPurgeJob`)가 유휴 조건에서 실제로 도는가 | 재지 않았다 | JobScheduler 로 하루 한 번·유휴 조건. 앱 시작 때의 정리도 그대로 돈다 |
| **APNG** | **넣지 않기로 했다** | 아래 '지원하지 않는 것'. 대신 **판별기(`ImageFormats.isApng`)로 화면이 "움직이지만 첫 장면만 보여 줍니다" 라고 말한다** |
| 진입 확대 애니메이션 | 필요해지면 | 출발 경계를 뷰어까지 옮기려면 공유 요소 전이를 세우거나 좌표를 손으로 넘겨야 하고, 그림·만화·재생 셋에 각각 붙는다(목록·격자 둘 다 `Thumb` 을 그리므로 출발 썸네일은 있다) |
| **애니메이션 WebP 회전 왕복 게이트** | ffmpeg 표본이 생기면 | 지금은 **거절**한다. 확인 못 한 것을 통과시키지 않는다 |

### 7단계가 세운 것과, 실측이 잡은 것

**성긴 색인이 이 단계의 뼈대다.** 줄마다 바이트 오프셋을 들면 1 GB·1천만 줄에서 76 MB 라
파일 크기 상한을 둘 수밖에 없어진다. 앵커를 64 KiB 또는 256행마다 하나만 두면 같은 파일이
0.9 MB 이고, 대가인 '앵커에서 목표 행까지 다시 읽기' 가 **64 KiB + 한 행**으로 묶인다.
그래서 이 앱은 **텍스트 파일 크기에 상한을 두지 않는다.**

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 줄이 아니라 **행**이 좌표다 | 줄바꿈 없는 480 KB 미니파이 JS 를 한 줄로 그리면 화면이 멎는다. 4,096자마다 자르고 줄 번호는 논리 줄을 가리킨다(`less -N` 과 같다) |
| 앵커는 **반드시 행 시작** | CP949·Shift_JIS 는 자기동기화가 아니다. 문자 한가운데에서 읽기 시작하면 `U+FFFD` 하나 없이 **그럴듯한 다른 글자**가 나온다 |
| 색인을 **두 갈래**로 나눔 | ASCII 호환 인코딩은 바이트에서 `0x0A` 를 세면 되고(전수 조사 0건), UTF-16 은 `0x0A` 가 글자의 일부라(`U+AC0A '갊'` = `AC 0A`) 디코드하며 세야 한다 |
| 찾기를 **문자로** | 바이트로 찾으면 CP949 후행 바이트가 ASCII 와 겹쳐 **가짜 일치**가 난다 |
| 강조에 정규식을 쓰지 않음 | 임의의 사용자 파일에 4,096자 한 행이다. 역추적 폭발이 화면을 멈추고, 여러 줄 주석은 정규식으로 표현할 수도 없다 |
| 강조 상태를 **앵커마다** 기록 | 파일 한가운데로 뛰어들었을 때 그 행이 블록 주석 안인지 알아야 한다. 앵커 하나에 `Int` 하나(1 GB 에 220 KB). 만드는 패스가 비싸서 4 MiB 를 넘으면 **강조를 끄고 화면이 그렇게 말한다** — 반쯤 맞는 색보다 색이 없는 편이 정직하다 |
| CR 뒤의 앵커는 버리지 않고 **한 바이트 미룬다** | CR 자리에서는 줄 시작을 아직 모른다(다음 바이트가 LF 면 한 칸 더 밀린다). '캐리지리턴 뒤에는 앵커를 찍지 않는다' 로 짜면 CRLF 파일은 모든 줄 끝이 CR+LF 라 그 규칙이 **영구 억제**가 된다 — 20만 줄 로그의 앵커가 1개가 되어 끝으로 뛸 때마다 16 MB 를 처음부터 다시 읽었다 |

### 7단계에서 미룬 것

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| 찾기가 **파일 전체를 훑는다** | 필요해지면 | 16 MB 에서 몇 초 걸린다. 취소와 진행률('찾는 중… N%' 와 아래 막대 위에 겹친 가는 막대)이 있다. 찾기는 색인이 아는 행까지만 훑는다 — 보고 있는 로그에 줄이 덧붙어도 끝난다. 색인에 낱말을 넣는 것은 '전문검색 인덱스' 라 1단계에서 제외한 항목이다 |
| 자동 새로고침 | **하지 않기로 했다**(사용자) | 텍스트 뷰어는 디스크에서 바뀐 파일을 다시 읽지 않는다 — 같은 경로를 다시 열어도 옛 색인 그대로다(`TextViewModel.open` 의 `openedPath == path`). 다시 열 때를 위한 메모 — `RowIndex.sourceBytes` 가 그것을 감지할 자리를 이미 들고 있다. 폴더 감시(`FolderWatch`)는 텍스트 뷰어가 쓰지 않는다 — 붙일 때는 같은 경로의 `FileObserver` 둘이 서로를 끊는다는 함정을 먼저 본다 |
| 문법 강조 언어 **23종** | 늘어나는 대로 | 훑개는 둘(중괄호 계열·마크업)이고 나머지는 자료다. 새 언어는 `TextLanguage` 에 낱말 목록만 더하면 된다 |
| 조각 경계에서 낱말이 잘리면 색이 틀린다 | 고치지 않는다 | 4,096자에서 자른 자리의 `var` 가 `v` + `ar` 로 갈리면 뒤쪽은 낱말이 아니다. 미니파이 파일에서만 보이고, 고치려면 조각 경계를 낱말 경계로 옮겨야 하는데 그러면 행 길이가 들쭉날쭉해진다 |

### 8단계가 세운 것

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 푸는 길을 **순차 하나로** | `open()` 반복은 7z 에서 제곱이다(실측 3.85배). 1,000개짜리가 몇 분 걸린다 |
| 순차 API 를 **미는(push) 모양**으로 | junrar 의 `getInputStream` 이 새 스레드를 띄워 취소가 닿지 않는다. 우리가 `OutputStream` 을 쥐어야 해제 루프 안에서 인터럽트를 본다 |
| 트리를 **순수 JVM 에서** | 디렉터리 엔트리 없는 zip, 같은 이름 둘, 파일이자 폴더인 이름 — 경계가 많고 전부 초 단위 시험으로 돈다. 만화 뷰어가 같은 그룹핑을 쓴다 |
| 풀기를 **파일 작업 큐로** | 같은 폴더를 두 작업이 동시에 만지지 않고, 포그라운드 서비스·진행률·취소·결과 보고를 그대로 얻는다 |
| 이름 계산을 **한 함수로**(`ExtractNames`) | 화면의 충돌 선검사와 엔진이 다르게 계산하면 사용자가 본 것과 덮어써지는 것이 달라진다. 4단계가 치명으로 고친 그 형태다 |
| 기본 목적지를 **'새 폴더'** 로 | 최상위가 여럿이면 흩어지고, 하나여도 그 이름이 이미 있으면 섞인다. **개수만 보지 않는다** |
| 부분 실패를 **되돌리지 않는다** | 되돌리기는 '실패했으니 사용자 파일을 지운다' 이고, 4단계 치명 넷이 전부 그 형태였다. 대신 어디까지 했는지를 싣는다 |
| 상한을 **둘로 가른다** | 엔트리 상한(크기·압축비)은 그 항목만 실패로 세고, 아카이브 상한(총량·개수)은 그 자리에서 끝낸다 — 한 번 넘으면 그 뒤 전부가 반드시 넘는다 |
| 풀기에 **별도 상한**(`ParseLimits.forExtract`) | 기본 총량 1 GiB 는 '파서가 힙에 올리는 양' 이다. 64 KiB 버퍼로 디스크에 흘려보내는 풀기에 그대로 쓰면 정상 500 MB 백업이 거절된다. 총량은 **대상 볼륨의 여유 공간**으로 바꾸고 엔트리별 방어는 그대로 둔다 |
| 7z 의 위치를 **물리적으로** 센다 | 이름이 없는 엔트리가 하나라도 있으면 목록의 번호와 실제 위치가 어긋나 **다른 파일의 바이트**가 열렸다(ZIP 에서 이름을 신원으로 쓰다 밟은 것과 같은 형태다) |

### 9단계에서 미룬 것

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| **움직이는 쪽은 확대되지 않는다**(만화 뷰어) | 걸리면 그때 | 이미지 뷰어는 확대를 허락했다(`ZoomablePainter` — hwui 는 드로어블을 표시 목록에 한 번 기록하고 프레임은 RenderThread 가 넘기며, 확대 행렬은 그 기록을 감쌀 뿐이다. 소스를 읽은 판단이고 기기에서 확대한 채 도는 것을 봤다). 만화 뷰어는 그대로 둔다 — **만화책 안의 GIF 는 드물다** |
| **세로 모드에서 확대** | 필요해지면 | 띠가 `LazyColumn` 의 항목이라 확대하려면 스크롤과 변환을 함께 중재해야 한다. 웹툰은 폭 맞춤으로 읽는 것이 보통이다 |

### 11단계에서 미룬 것 (PDF 쪽)

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| **글자 선택·복사·문서 안 찾기** | 찾기는 **API 35 에서**, **고르기·복사는 모든 판에서 했다**(우리 글 꺼내기) | 레거시 `PdfRenderer` 는 글자를 주지 않는다(확장 13 이상의 `PdfRendererPreV.selectPageText` 가 그것이고, 이 기기는 확장 1이다). API 35 의 `Page.searchText` 로 찾기와 강조 상자를 붙였고 그 아래에서는 단추가 아예 없다(`@RequiresApi` + `SDK_INT`, `NewApi` 0건). API 34 이하의 찾기는 글 꺼내기(`docview/pdf/text`) 위에 지을 수 있다 |
| **목차(outline)와 문서 안 링크** | 필요해지면 | 같은 이유로 레거시 API 가 주지 않는다. 쪽 이동은 슬라이더와 `쪽으로 가기` 로 한다 |
| **주석·폼을 그리는가** | 확인하지 않았다 | `RENDER_MODE_FOR_DISPLAY` 로 그리므로 플랫폼이 정하는 대로 나온다. 표본을 만들어 확인하기 전에는 '그린다' 도 '안 그린다' 도 적지 않는다 |

### 10단계가 착수하며 정한 것

| 미결 | 정한 것 | 근거 |
|---|---|---|
| 재생 UI 의 자리 | **`feature:player` 를 만든다.** 화면만 옮기고 미니 바·서비스·커넥션은 `core:playback` 에 남긴다 | 10단계가 화면을 세 배로 키운다(위 '모듈 구조') |
| 자막 렌더 | **의존을 늘리지 않고 직접 그린다** | `media3-exoplayer` 안에 SRT·ASS·WebVTT·TTML 파서가 이미 있다(`extractor/text/` 아래 열 갈래). `Player.Listener.onCues` 로 받아 Compose 로 그린다. `media3-ui` 는 View 기반이라 Compose 화면에 맞지도 않는다 |
| 영상 탭 동작 | **영상은 곧바로 재생 화면으로** | PiP·자막·제스처가 전부 재생 화면 안의 일이다. 영상을 탭해 놓고 목록에 남아 소리만 나는 것은 아무도 기대하지 않는다(소리도 재생 화면이 목록을 펼친 채 열린다) |
| 재생 설정 저장 | **저장하지 않는다**(세션 안에서만). DB 마이그레이션 없음 | 외부 자막을 이름으로 자동 짝짓기 하면 다시 열 때 알아서 붙으므로 저장할 값이 작다 |
| '오디오를 가진 미디어' | **확장자 근사**(AUDIO·VIDEO 전부) | '목록을 그릴 때 파일을 열지 않는다'(`FileKind` 주석)가 더 단단한 규칙이다. 1만 개 폴더에서 ▶ 를 띄울지 정하려고 1만 번 열 수는 없다. **무음 영상이 섞이는 것이 이 근사가 틀리는 유일한 방향**이고, 틀려도 재생이 안 될 뿐 파일은 다치지 않는다(`.ts` 는 내용으로 가른다 — 'SVG·`.ts`·확장자' 절) |

`proguard-rules.pro` 에 media3 규칙이 없어 **자막 부품을 들이면 릴리스 스모크를 반드시 함께 돈다**.

## 암호가 걸린 파일 — 잠근 방식이 공개돼 있으면 연다

**사용자가 정했다**: "암호화된 문서를 읽는 방법이 공개되지 않은 이상, 암호화된
문서도 열 수 있게 해주세요." 판단 기준은 하나다 — **푸는 방법이 공개 명세에 있는가.** 암호를
몰라도 풀리는 방식(배포용 문서처럼 열쇠가 파일 안에 있는 것)도 공개돼 있으면 연다.
오피스·한글 문서는 편집기(rhwp·ONLYOFFICE)가 열므로 그 암호도 편집기가 다룬다 — 아래 표의 그 줄은 기기에서 본 편집기의 거동이다.

| 형식 | 잠근 방식 | 공개인가 | 지금 |
|---|---|---|---|
| PDF 표준 보안 처리기(R2~R6) | RC4·AES-128·AES-256 | ISO 32000-1/2 | **연다** |
| PDF 공개 키 처리기(`Adobe.PubSec`) | 받는 사람의 인증서 | 명세는 공개지만 열쇠가 암호가 아니다 | 열지 않는다(묻지도 않는다) |
| PDF 전용 처리기(상업 DRM) | 업체마다 | 공개되지 않았다 | 열지 않는다 |
| EPUB 글꼴 난독화(IDPF·Adobe) | XOR | IDPF 는 EPUB 3.3 명세 4.4, Adobe 는 명세가 아니라 오픈소스 구현(Readium)이 따르는 방식 | **푼다**(`FontObfuscation`) |
| EPUB DRM(Adobe ADEPT) | 업체 방식 | 공개되지 않았다 | 열지 않는다 |
| EPUB Readium LCP | AES | 명세는 공개. 다만 실사용 프로필의 키 변환은 인증 아래 배포된다고 알려져 있다 — **확인하지 않았다** | 확인 전까지 열지 않는다 |
| ZIP 전통 암호 · WinZip AES(AE-1·AE-2) | ZipCrypto · AES-128/192/256 | APPNOTE 6.1 · WinZip AES 명세 | **연다**(`ZipDecryption`). WinZip AES-256(AE-2)으로 **만든다**(`AesZipWriter`. 전통 암호로는 만들지 않는다) |
| ZIP PKWARE 강한 암호화(SES) | 인증서·전용 | 명세 일부만 공개 | 열지 않는다('이 앱이 풀지 않는 방식') |
| 암호 안쪽이 LZMA·PPMd 등인 ZIP 항목 | 암호는 공개, 압축을 우리가 안 푼다 | | 열지 않는다. **암호를 묻지 않는다**(`ArchiveEntry.lockedForGood`) |
| 7z | AES-256(내용만·헤더까지) | 7-Zip 문서 | **연다**(commons-compress 에 암호를 넘긴다). 열쇠 유도 반복 지수가 0~24·63 일 때만 — 넘으면 암호를 묻지 않고 '이 앱이 풀지 않는 방식' 이다(`SevenZKeyGuard`) |
| RAR | AES | RAR 기술 문서 | 암호를 넘긴다(junrar) — **미검증**, 표본을 만들 도구가 없다 |
| OOXML(암호 문서) — Agile · Standard | AES(SHA-1~512) · AES ECB | MS-OFFCRYPTO(Microsoft 공개 명세) | 편집기(ONLYOFFICE)가 암호를 묻는다(물음을 닫으면 '암호를 넣어야 열 수 있는 문서입니다' 와 '다른 앱으로 열기'). 맞는 암호로 여는 것·`VelvetSweatshop`(읽기 전용 권장 파일의 기본 암호)을 묻지 않는지는 편집기로 확인하지 않았다 |
| OOXML — RC4 CryptoAPI · Extensible · IRM(권한 관리) · 인증서만 | | 앞의 둘은 공개 명세지만 오피스 2007 뒤로 기본값이 아니다. 뒤의 둘은 열쇠가 암호가 아니다 | 편집기의 거동은 확인하지 않았다 |
| HWPX 암호 문서 | SHA-256 → PBKDF2-HMAC-SHA1 → AES-CBC | 한컴이 공개한 OWPML 모델(Apache-2.0) | 편집기(rhwp)가 암호를 묻는다(O16 — 맞는 암호로 여는 것은 확인하지 않았다) |
| HWP 배포용 문서 | 파일 안의 열쇠 → AES-128-ECB | 한컴의 별도 공개 문서(pyhwp·hwplib·rhwp 가 같은 방식) | 편집기(rhwp)가 **암호 없이 연다**(S01 — 70쪽). 복사·인쇄 제한 비트를 rhwp 가 따르는지는 확인하지 않았다 |
| HWP 암호 문서 | | **공개되지 않았다**(역공학 구현만 있다) | 편집기(rhwp)는 잠금 판 4만 푼다(제 오류 문장) — O08(판 0)은 열지 못해, 어댑터가 '문서를 열지 못했습니다 — 이 편집기가 풀지 못하는 방식으로 잠긴 문서입니다' 와 '다른 앱으로 열기' 를 보인다. 판 4 를 여는지는 표본이 없어 확인하지 않았다 |
| 배포용 HWPX | | 표본이 없다 | 확인하지 않았다 |

**암호를 다루는 규칙.**

- **`CharArray` 로 받아 여는 일이 끝나면 0 으로 덮는다.** 주인은 VM 이다(`DocViewModel`·
  `ArchiveViewModel`·`ComicViewModel`) — 여는이(`PdfOpener`·`Archives.open`)는 쓰기만 하고 지우지
  않는다. 주인이 모르는 사이에 값이 사라지면 안 된다. **오래 들어야 하는 쪽은 자기 사본을 든다**
  (리더·`SolidComicSource`·풀기 요청) — 닫거나 끝날 때 그 사본을 지운다.
- **잠그는 암호(ZIP 만들기)는 큐의 요청이 든다**. 칸(`NewPasswordFields` — `remember`·보안 입력칸)에서 '압축' 을 누르는 순간
  꺼내 칸을 비우고 `Request.Compress.password` 에만 싣는다. 묶는 쪽(`ZipMaker.make`)이 맨 먼저 열쇠의 바탕(`AesZipKey` — 암호로 열쇠를 건
  HMAC)으로 옮기고 배열과 UTF-8 사본을 덮는다. 돌지 않고 버려지면 큐가 덮는다(`settle`). 세션에 기억하지 않는다.
- **아카이브 암호만 세션 동안 기억한다**(`SessionPasswords`, 메모리에만). 압축 목록·만화 뷰어·풀기
  큐가 같은 파일을 따로 열기 때문이다. 넣은 암호는 **확인한 뒤에** 기억하고, 앱이 화면에서
  사라지면(시작된 액티비티가 0) 전부 지운다 — 회전은 예외다. 지우면 알리고(`clears`), 암호로
  풀어 둔 목록은 그때 다시 읽어 잠근다. **앱이 화면에 없는 동안에는 `put` 을 받지 않는다** — 확인이
  몇 초 걸리는 사이 홈으로 나가면 지우는 일은 이미 지나갔기 때문이다. 목록을 읽는 도중에 지워졌으면
  그 결과를 '풀렸다' 로 내지 않고 암호 없이 다시 읽는다. **이미 열린 것은 닫지 않는다** — 읽고 있는 만화와 PDF 는
  자기 사본(또는 평문 메모리 파일)으로 계속 읽힌다. PDF 암호는 기억하지 않는다(열려 있는 동안
  다시 물을 일이 없다).
- **저장하지 않는다** — `SavedStateHandle` 에도, DB 에도, 로그에도. 프로세스가 죽었다 살아나면
  다시 묻는다. 입력칸은 저장되지 않는 상태로 든다(함정 표의 `rememberTextFieldState`). 편집기의 암호도 엔진이 물을 뿐 어디에도 두지 않는다.
- **완전히 지우지는 못한다.** Compose 입력칸의 버퍼와, API 35 에서 플랫폼에 넘기는 `String`
  (`LoadParams.Builder.setPassword` 가 그것만 받는다), JCE 의 `SecretKeySpec`·`Mac` 이 안에 드는 열쇠
  사본(ZIP AES — 풀기와 묶기 모두. 묶기는 작업 내내 암호로 열쇠를 건 HMAC 하나를 든다), junrar 가 열쇠를 유도할 때마다 만드는 `String` 은 우리가 덮을 수 없다. 우리 코드가
  만드는 사본(ZIP 암호의 바이트 후보·유도한 열쇠·PBKDF2 중간값)은 `String` 을 거치지 않고 만들어 덮는다.
  줄일 수 있는 데까지 줄인 것이다 — 그렇게 적어 두고 약속하지 않는다.
- **평문을 저장소에 쓰지 않는다.** 플랫폼이 암호를 받지 않는 API 34 이하에서는 우리가 풀어
  **memfd**(경로 없는 tmpfs 파일)에 평문 PDF 한 벌을 만들고 그 서술자를 pdfium 에 준다
  (`MemfdSink`). 문서 크기만큼 RAM 을 쓰므로 256 MiB 상한이 있다(`PdfLimits.MAX_DECRYPTED_BYTES` = `DecryptLimits.MAX_BYTES`).
- **물어도 소용없는 암호를 묻지 않는다.** pdfium 은 '암호가 필요하다' 와 '모르는 처리기다' 를
  같은 예외로 말한다. `/Encrypt` 를 우리가 읽어(`PdfDecryptor.inspect`) 가른다 — 인증서·전용
  처리기면 `OpenFailure.Encrypted`, 표준 처리기면 `OpenFailure.PasswordRequired`.
- **소유자 암호만 걸린 문서(빈 사용자 암호)는 묻지 않고 연다.** 거기서 암호를 물으면 사용자는
  있지도 않은 암호를 요구받는다. pdfium 이 스스로 열고, 못 열면 우리가 빈 암호로 푼다.

## 지금 지원하지 않는 것 — 정직하게 적는다

**영구 결정이 아니다**(위 '이 프로젝트의 목적'). 다시 열 때는 그 항목을 여기서 지우고
안전 규칙을 먼저 세운다. 라이선스가 막는 것(RAR 만들기)만 예외다.

- **FFmpeg 확장을 쓰지 않는다.** Maven 에 미리 빌드된 배포가 없고, 공식 README 가 Windows
  빌드를 지원하지 않으며, 비디오 렌더러는 소스에 "아직 동작하지 않음"으로 박혀 있다.
  `.so` 를 커밋해도 영상 코덱은 하나도 늘지 않는데 의존 정책과 공개 저장소 규칙만 깨진다.
- 따라서 **APE·TTA·WavPack·DSD·RealMedia·WMA/WMV/ASF 는 열지 못한다**(extractor 자체가 없다).
  AC-3·E-AC-3·DTS·TrueHD·ALAC 은 "기기가 지원하면 재생"이다. 목록이 소리·영상으로 보는 것(APE·WMA·WMV·RMVB)은 재생 화면이
  '이 앱이 재생하지 못하는 형식입니다' 곁에 **'다른 앱으로 열기'** 를 둔다(VLC 같은 앱이 있으면 거기서 열린다). 종류 표에 없는
  것(TTA·WavPack·DSD·ASF·`.rm`)은 '기타' 라 누르면 곧바로 다른 앱으로 간다.
- **`/Android/data` 와 `/Android/obb` 는 볼 수 없다.** 모든 파일 접근 권한으로도, SAF
  우회로도 막혔다. 숨기지 말고 자물쇠와 안내로 남긴다. shizuku·adb 류 우회는 하지 않는다.
- **잠근 방식이 공개되지 않은 문서는 열지 않는다** — 상업 DRM(EPUB 의 Adobe ADEPT, PDF 의
  전용 보안 처리기)과 인증서로 잠근 PDF(공개 키 처리기, 열쇠가 암호가 아니다). 공개된 방식은
  **연다** — 위 '암호가 걸린 파일' 절. 오피스·한글 문서의 암호는 편집기가 다룬다(같은 절의 표).
- **오피스·한글 문서를 무엇까지 그리는지는 편집기(rhwp·ONLYOFFICE)의 일이다** — 이 앱이 확인한 범위는 위 '…뷰어 교체' 절의 기기 확인이다.
  **편집기가 없는 빌드(엔진 묶음 없이 지은 것)는 오피스·한글 문서를 열지 않는다** — 문서 뷰어가 '이 앱이 다루지 않는 문서입니다' 와 '다른 앱으로
  열기' 로 끝낸다. 사용자의 빌드에는 묶음이 든다.
- **한글 97 이전(HWP 3.0)** — 편집기(rhwp)가 그 파일에 무엇을 보이는지는 확인하지 않았다.
- **RTF 는 열지 않는다** — 편집기의 변환기가 읽을 때 한글을 버린다. '이 앱이 다루지 않는 문서입니다' 와 '다른 앱으로 열기' 로 끝난다(이전 오피스
  형식과 ODF 는 편집기가 연다 — 고쳐 저장하면 이전 형식은 곁에 OOXML 로).
- **분할 아카이브를 열지 않는다**(`.z01`·`.part1.rar`·`7z.001`). 조각 하나만으로는
  통짜로 열 수 없고, 열려고 하면 라이브러리 예외로 끝나 사용자에게는 '깨진 파일' 로 보인다.
  지원하지 않는다는 사실을 정확히 말하는 편이 낫다. 압축 확장자를 가진 조각(마지막 `.zip` 등)은 그 문장 곁에 '다른 앱으로
  열기' 가 선다. `.z01`·`.001` 조각은 종류가 '기타' 라 압축 화면에 오지 않고, 누르면 곧바로 다른 앱으로 간다.
- **RAR 의 암호는 넘기기만 하고 확인하지 못했다.** junrar 에 암호를 건네는 코드는 있지만 암호
  RAR 표본을 만들 도구가 없어 한 번도 실제 표본을 타지 않았다. ZIP(전통·WinZip AES)과 7z 는
  **연다**(위 '암호가 걸린 파일'). PKWARE 의 강한 암호화(SES)는 '이 앱이 풀지 않는 방식으로 잠긴
  압축 파일입니다' 로 끝난다.
- **`iso` 를 열지 않는다.** 목록에서 종류는 '압축' 으로 보이지만(확장자로 정하므로) 열면 '이 앱이 다루지 않는 압축 형식입니다'
  와 '다른 앱으로 열기' 로 끝난다. zstd·brotli·lzip·옛 LZ4(`lz4 -l`)·ARJ·RPM·분할도 같다. `.gz`·`.bz2`·`.xz`·`.lz4`·`.lzma`·`.Z` 로
  싼 파일 하나는 **항목 하나짜리로 연다**(위 'SVG·`.ts`·확장자' 절). tar 계열(`.tar.lz4`·`.tlz`·`.tar.Z` 포함)과 cpio·ar·deb
  도 연다 — 다만 **풀린 크기가 1 GiB 를 넘는 압축 tar 는 목록을 보이지 않는다**(흐름이라 목록을 세우려면 끝까지 풀어야 한다. '풀어야
  할 양이 너무 커서 열지 않았습니다'). tar·cpio 의 심볼릭 링크·하드 링크·장치 파일은 풀지 않고 링크로 센다(RAR 링크와 같다).
- **사진 잘라 저장은 다시 인코딩한다** — JPEG 은 품질 95 로 다시 쓴다(무손실 MCU 자르기는 플랫폼에 없다), HEIC·AVIF 는 JPEG 로,
  투명한 그림은 PNG 로 나온다. 움직이는 그림·SVG 는 자르지 않는다. 자르는 화면에서 확대하지 않는다 — 큰 그림의 아주 작은 자리는 고르기 어렵다.
  위치(GPS)는 옮기지 않는다. 원본을 고치는 자르기(덮어쓰기)는 없다.
- **그리기·메모도 다시 인코딩한다**(잘라 저장과 같은 정책, 결과는 `이름_markup`). 그린 것은 레이어로 남지 않아 나중에 고칠 수 없다.
  원본을 덮어쓰는 그리기는 없다. 움직이는 그림·SVG 에는 그리지 않는다. 화소 지우개·도형·글꼴 고르기·두 번 두드려 확대는 없고, 도구 설정은 앱이
  사는 동안만 남는다. 획 하나는 8,192점, 메모 하나는 2,000자에서 멈춘다.
- **구간 저장은 다시 인코딩하지 않는다** — 그래서 영상은 고른 시작 바로 앞의 키프레임부터 담긴다(칸이 미리 말한다). 담을 수 없는
  것: FLAC·AC-3·DTS 소리만 있는 영상, AV1·MPEG-2 영상, MP3 를 품은 MP4, Ogg FLAC, 잠긴(DRM) 미디어, ADPCM·RF64 WAV, AVI·FLV(플랫폼이 읽지
  못한다). 자막 트랙은 담지 않는다. 제목·날짜·위치는 MP4·WebM·Ogg 에 옮기지 않는다(MP3 의 ID3·FLAC 의 주석·WAV 의 LIST 는 옮긴다).
  다른 앱이 넘긴 사본은 저장하지 않는다.
- **소리만 저장도 다시 인코딩하지 않는다** — 그래서 소리의 코덱이 결과의 형식을 정한다(MP3 로 바꿔 주지 않는다). 뽑지 못하는 것:
  DTS·TrueHD·ALAC 소리, 빅 엔디언 PCM, 머리(csd-0) 없는 FLAC, 잠긴(DRM) 미디어, 4 GiB 를 넘는 PCM(WAV 의 한계 — RF64 는 쓰지 않는다), AVI·FLV
  (플랫폼이 읽지 못한다). 소리 트랙이 여럿이면 뽑을 수 있는 첫 트랙 하나만 뽑는다(고르게 하지 않는다).
- **압축 파일은 ZIP 만 만든다.** 7z·tar 는 만들지 않는다 — 요청이 ZIP 이었고, ZIP 은 받는 쪽 어디서나 풀린다. 암호 ZIP 은
  **WinZip AES-256(AE-2)으로만** 만든다 — 전통 암호(ZipCrypto)는 약해서 만들지 않고, **파일 이름은 가려지지 않는다**(형식의 성질).
  **RAR 만들기는 앞으로도 없다** — UnRAR 라이선스가 압축기 재구현 자체를 금지한다.
- **아카이브 안의 아카이브를 열지 않는다.** 목록에는 보이고, 풀어서 실제 파일이 된 뒤에
  열면 된다. 막는 것은 `maxContainerDepth` 라는 상수가 아니라 **구조**다(위 '안전' 참고) —
  엔트리에는 경로가 없어 뷰어에 넘길 수 없다. 탭하면 '압축 파일 안의 압축 파일은 열지
  않습니다. 풀어서 여세요' 로 끝난다. **만화 확장자(cbz·cbr·cb7·cbt)도 여기 든다**(함정 표 '종류를 옮기면 …').
- **아카이브 안의 그림만 미리본다.** 그림 항목을 탭하면 만화 뷰어가 그 쪽에서 열리고,
  글·문서·영상 항목은 "이 항목을 열 수 없습니다" 로 끝난다 — 그 뷰어들은 경로를 요구하는데
  경로를 주려면 엔트리를 디스크에 뽑아야 하고, 그것이 만화 뷰어가 하지 않기로 한 일이다.
- **압축 파일 안의 문서를 열지 않는다.** PDF·EPUB·오피스·HWP 항목을 탭하면
  **'압축 파일 안의 문서는 열지 않습니다. 풀어서 여세요'** 로 끝난다(기기에서 확인했다).
  pdfium 이 **seekable 한 파일 서술자**를 요구해 기술적으로도 막히지만, 여는 유일한 길인
  '문서를 통째로 임시 파일에 뽑기' 는 쪽 한 장이 아니라 **문서 전체의 평문 사본**이라
  만화 뷰어가 하지 않기로 한 일보다 나쁘다. 바이트 스트림을 받는 파서라도 **열지 않는다** — 한 번 뚫으면
  '엔트리를 디스크로 뽑지 않는다' 는 압축 화면의 불변식이 깨진다. 편집기도 넘겨받은 문서를 앱 캐시에
  통째로 떠 두므로 같은 까닭으로 압축 안의 문서를 넘기지 않는다.
- **APNG 을 움직이게 틀지 않는다.** 플랫폼이 `isAnimated=false` 로 주므로 우리가 청크를
  분해해 합성해야 하는데, 그것은 CRC 를 다시 계산해 skia 에 먹이는 일이라 방어 상한과
  악성 표본이 먼저 필요하다. 만화 뷰어와 이미지 뷰어가 **"움직이는 PNG 입니다. 첫 장면만 보여
  줍니다"** 로 말한다(판별기 `ImageFormats.isApng`).
- **너무 큰 움직이는 그림은 첫 장면만 보인다**(이미지 뷰어). 표본을 두 번 더 줄여도 프레임 버퍼 셋이 한 화면 크기
  비트맵에 들지 않으면 첫 장면과 그 까닭을 보인다.
- **만화 뷰어의 움직이는 쪽(GIF·애니WebP)은 확대되지 않는다.** 탭으로 막대를 여닫는 것은 된다. 이미지 뷰어는
  확대된다(위 '9단계에서 미룬 것').
- **만화 표지는 확장자가 만화인 것에만 만든다**(cbz·cbr·cb7·cbt). 일반 압축 파일까지
  넓히면 사용자가 **연 적도 없는** 파일 속 개인 사진이 320px JPEG 으로 앱 저장소에 남는다.
- **만화 쪽을 디스크에 쓰지 않는다.** 쪽은 원본 그대로라, 캐시에 뽑으면 사용자의 만화가
  통째로 앱 저장소에 평문 복제된다. 대가는 solid 아카이브에서 창을 벗어날 때의 1~2초다.
- **주석에 EOCD 서명 바이트가 들어 있는 ZIP 을 열지 못한다.** 우리 개수 판정은 속지
  않지만 commons-compress 의 탐색이 걸려 넘어진다(위 함정 표). 라이브러리 안쪽 문제다.
- **EPUB 의 스크립트를 돌리지 않는다.** EPUB3 은 문서 안의 JavaScript 를 허용하지만
  이 앱은 WebView 에서 끄고(`javaScriptEnabled = false`) 위생기에서 지운다. 스크립트로
  움직이는 퀴즈·애니메이션은 정지한 모양으로 보인다 — **버린 것 알림이 그 사실을 센다.**
- **문서가 바깥을 가리키는 것을 따라가지 않는다.** 그림·스타일시트·글꼴·링크 어느 것이든
  `http:`·`file:`·`content:` 로 시작하면 지운다. 위생이 한 겹이고 잠긴 WebView 의 가로채기(우리 호스트가 아니면 빈 404)와
  `blockNetworkLoads` 가 또 한 겹이다 — **방어는 겹쳐 두어야 한 겹이 무너져도 남는다.** 맨 앞의 겹은 `INTERNET` 미선언이다(위 'INTERNET 을
  다시 걷었다').
- **EPUB 안에서 글자를 찾지 못한다.** 장 하나 안에서는 WebView 의 기능이 있지만 책 전체를
  훑는 찾기는 없다. 텍스트 뷰어의 '파일 전체를 훑는다' 와 같은
  성질의 일이고, 여기서는 장마다 압축을 풀어야 해서 값이 더 크다.
- **DRM 이 걸린 EPUB 을 열지 않는다.** `encryption.xml` 이 본문을 가리키거나 무엇을
  가리키는지 알 수 없으면 '암호로는 열 수 없는 방식으로 잠긴 문서입니다' 로 끝낸다. 글꼴만
  난독화된 책은 **연다**.
- **`.mobi`·`.azw`·`.fb2` 를 열지 않는다.** 목록에서 종류는 '전자책' 으로 보이지만
  (확장자로 정하므로) 열면 '이 앱이 다루지 않는 문서입니다' 와 '다른 앱으로 열기' 로 끝난다. `.rtf` 도 같다.
- **PDF 의 글은 쪽 하나 안에서만 고른다**(위 '글 고르기' 절). API 35 에서 플랫폼이 직접 푼 암호 문서는 고르지 못한다.
  **API 34 이하에서는 문서 안에서 찾지 못한다** — 찾기는 API 35 에서만 있다(글 꺼내기 위에 지을 수 있다). 목차와 문서 안 링크도 없다 —
  쪽 이동은 슬라이더·`쪽으로 가기`·쪽 목록이다. 주석·폼을 그리는지는 확인하지 않았다(`RENDER_MODE_FOR_DISPLAY` — 플랫폼이 정하는 대로다).
- **인증서로 잠긴 PDF 와 전용 처리기로 잠긴 PDF 를 열지 않는다.** 앞의 것(공개 키 처리기)은
  암호가 아니라 받는 사람의 인증서를 요구하고, 뒤의 것은 방식이 공개되지 않았다. 둘 다
  '암호로는 열 수 없는 방식으로 잠긴 문서입니다' 로 끝난다 — **암호를 묻지 않는다**(물어도
  소용이 없다). 표준 보안 처리기로 잠긴 PDF 는 암호를 물어 연다.
- **UTF-32 텍스트를 열지 않는다.** BOM 은 알아보지만 읽지는 못한다 — UTF-16 인 척 열어
  널이 가득한 화면을 보여 주는 것이 가장 나쁘다. "이 앱이 읽지 못하는 인코딩입니다" 와 '다른 앱으로 열기' 로 끝낸다.
- **인코딩이 섞인 텍스트 파일을 다루지 않는다.** 판정은 파일 하나에 하나다. 앞 1 MiB 가
  전부 ASCII 이고 그 뒤에 CP949 한글이 나오는 파일은 UTF-8 로 판정되어 뒷부분만 깨진다 —
  그때 사용자가 아래 막대에서 인코딩을 손으로 바꾼다.
- **글 편집은 256 KiB 까지다.** 편집기는 칠 때마다 글 전체를 다시 배치한다(함정 표) — 에뮬레이터에서
  256 KiB 의 한 글자가 65~85 ms 였다. 편집기에는 문법 강조·두 손가락 글자 크기·정규식 찾기·자동 저장이 없다. 줄 끝이 섞인 파일은 줄마다
  지키지 못하고 가장 많은 것으로 맞춘다(저장 전에 묻는다). 엄격하게 읽히고 다시 쓰면 같은 바이트인 파일만 연다 — 아니면 까닭을 말하고 열지 않는다.
  받은 사본·휴지통·`/storage` 밖은 고치지 않는다(연필 단추가 없다). 텍스트 뷰어는 밖에서 바뀐 파일을 다시 읽지 않는다(자동 새로고침 없음 — 사용자).
- **4 MiB 를 넘는 파일에는 문법 강조를 켜지 않는다.** 색을 반쯤 맞히는 대신 끄고 화면이
  그렇게 말한다. 위 '7단계가 세운 것과, 실측이 잡은 것' 의 '강조 상태를 앵커마다 기록' 참고.
- **TIFF 는 디코딩하지 않는다. SVG 는 그림 뷰어에서만 그린다**(잠긴 WebView — 'SVG·`.ts`·확장자' 절). 둘 다 확장자가
  IMAGE 로 잡히지만 플랫폼 디코더가 못 읽어, 격자에서는 한 번만 시도하고 실패를 기억해(부정 캐시) 종류 배지로 남긴다 — 썸네일 하나를
  그리려고 WebView 를 띄우지 않는다. 이미지 뷰어는 TIFF 와 SVG 가 아닌 `.svg`(첫 요소가 `svg` 가 아닌 파일)에 '이 이미지를 열 수
  없습니다' 곁에 '다른 앱으로 열기' 를 둔다.
- **M2TS(AVCHD `.mts`·블루레이 `.m2ts`)를 재생하지 못한다.** media3 의 TS 추출기가 188바이트 패킷만 안다(함정 표). `.m2ts` 는 재생
  화면이 '이 앱이 재생하지 못하는 형식입니다' 곁에 '다른 앱으로 열기' 를 두고, `.mts` 는 목록이 기타로 보아 누르면 곧바로 다른 앱으로
  간다(TypeScript 면 텍스트 뷰어).
- **APK·글꼴과 이 앱이 모르는 파일에는 뷰어가 없다.** 누르면 곧바로 다른 앱으로 연다 — APK 는 시스템 설치 관리자(설치를 묻는 것은
  설치 관리자다). 그 형식을 아는 앱이 없으면 정보 시트('이 형식을 아는 앱이 없습니다')가 뜨고 그 '다른 앱으로 열기' 가 모든 앱의
  고르는 창으로 간다. 확장자가 없거나 어느 표도 모르는 파일은 곧바로 모든 앱의 고르는 창이다('다른 앱으로 열기' 절).
- **회전을 파일에 저장하지 못하는 형식이 있다**(PNG·HEIC·AVIF·RAW·GIF·BMP·애니메이션 WebP).
  화면은 돌지만 "이 형식은 회전을 파일에 저장할 수 없습니다" 로 정확히 끝낸다. 위 'EXIF 무손실 회전' 참고.
- **메타데이터를 제거하고 공유할 수 있는 형식도 JPEG·PNG·WebP 뿐이다.** 나머지는 재인코딩
  말고는 길이 없는데, 그것은 사용자의 HEIC 를 JPEG 으로 바꾸는 일이라 하지 않는다.
- **`.smi`(SAMI) 자막을 읽지 않는다.** media3 의 자막 파서 열 갈래(cea·dvb·pgs·ssa·
  subrip·ttml·tx3g·vobsub·webvtt)에 SAMI 가 없다. 한국어 자막에 흔한 형식이지만,
  목록에 올려 놓고 붙이면 빈 자막이 되는 것보다 다루지 않는다고 말하는 편이 낫다.
- **그림 자막(PGS·VobSub)을 그리지 않는다.** `Cue.bitmap` 을 그리려면 좌표계를 영상
  화면에 정확히 맞춰야 하고, 그것은 자막 층이 하려는 일(글 몇 줄)과 종류가 다르다.
  **트랙 목록에도 올리지 않는다** — 고를 수 있게 두면 표시만 옮겨 가고 화면에는 아무
  글자도 뜨지 않는다.
- **PiP 창에는 영상만 둔다.** 자막도 조작부도 재생목록도 그리지 않는다. 공식 문서가
  "사용자는 PiP 중에 앱의 UI 를 만질 수 없다" 고 적고, 작은 창에 자막을 얹으면 글자가
  뭉개져 읽히지도 않는다. 조작은 창의 `RemoteAction` 셋(이전·재생/일시정지·다음)뿐이다.
- **소리 파일에서는 PiP 에 들어가지 않는다.** 창에 보일 것이 검은 사각형뿐이다. 소리의
  백그라운드 자리는 알림과 미니 바다. 그래서 PiP 로 보는 중에
  큐가 소리로 넘어가면 **창을 닫고 재생만 잇는다.**
- **배속·트랙 선택·A-B 구간·방향 잠금을 저장하지 않는다**(세션 안에서만 — 위 '10단계가 착수하며 정한 것'). 배속은 `stop()` 에서 1.0 으로 되돌린다 —
  재생 화면을 열지 않고 미니 바로만 듣는 길에는 배속이 적혀 있지 않아, 남겨 두면 무엇이
  이상한지 모른 채 소리가 틀어진 것만 듣게 된다.
- **ASS 의 서식을 조판하지 않는다.** 위/아래 자리와 좌우 정렬만 따르고 글자색·크기·
  회전·카라오케는 버린다. 전부 따르면 ASS 조판기를 다시 만드는 일이 된다.
- **네트워크 저장소를 다루지 않는다**(SMB·FTP·WebDAV·SFTP) — 이 앱은 기기 안의 파일만 본다(사용자).
- **HTML 을 그려 보이지 않는다.** 텍스트 뷰어가 원문(문법 강조)으로 보이고, 그린 모양은 ⋮ 의 '브라우저로 열기' 로 다른 브라우저에 넘긴다 —
  넘긴 것은 그 파일 하나라 같은 폴더의 CSS·그림은 실리지 않는다. 폴더째 읽기 권한을 넘기면 `INTERNET` 을 가진 브라우저가 쪽의 스크립트로
  그 폴더를 읽게 되므로 하지 않는다.
- **고른 글을 다른 앱(번역 등)에 보낼 때는 앞 10만 자만 보낸다**(넘으면 말한다). 복사는 따로 20만 자다(글 고르기 절).
- **인쇄**와 EPUB 의 **미디어 오버레이(읽어 주기)** 는 넣지 않는다 — 인쇄는 요구사항에 없고, 읽어 주기는 이 앱의 재생기와 뷰어를 하나로 묶는 일이다.
- 채택하지 않는 것: Apache POI, PdfBox-Android, androidx.pdf(beta), juniversalchardet,
  ICU4J, media3 의 네이티브 decoder 확장, zstd-jni.
