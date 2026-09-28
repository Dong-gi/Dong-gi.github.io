# iroiro-viewer 작업 지침

안드로이드 **파일 관리자 + 만능 뷰어**. 개인용, APK 사이드로드. 스토어 배포 없음.
**이 파일이 이 프로젝트의 유일한 지침 문서다.** 규격·절차·인계 문서를 따로 두지 않는다.

---

## 물려받는 제약

상위 저장소 `Dong-gi.github.io` 는 **GitHub Pages 로 통째로 서비스된다.** 여기 커밋하는
모든 파일이 웹에서 내려받힌다. 규칙은 "커밋해도 되는가"가 아니라 **"공개해도 되는가"** 다.

- 서드파티 바이너리를 커밋하지 않는다. 예외는 `gradle-wrapper.jar`(48KB, `usb-tether` 의 전례) 하나다 — 실행
  파일이 아니고 출처가 명확하다. (고정폭 CJK 폰트도 예외로 적어 두었지만 7단계가 **넣지 않기로** 했다 — 아래
  '고정폭 글꼴 — 칸이 맞지 않는다'.)
- `local.properties`·keystore·실세계 문서 표본은 `.gitignore` 로 막혀 있다. **한 번 새면
  이력 재작성 말고는 되돌릴 방법이 없다.**
- 절대경로를 커밋하는 설정(`org.gradle.java.home` 등)을 쓰지 않는다.

---

## 확정 요구사항

사용자 인터뷰로 확정했다. **임의로 줄이지 마라.** 미루는 것은 되지만 미룬다고 적는다.

| | |
|---|---|
| 대상 | minSdk 31(Android 12). 실기기는 Android 12 폰과 Android 15 태블릿 |
| 저장소 접근 | `MANAGE_EXTERNAL_STORAGE` (모든 파일 접근) |
| 의존 정책 | AndroidX·Kotlin 공식 + **네이티브 코드 없는 성숙한 순수 JVM 오픈소스**까지 |
| 파일 관리 | 복사·이동·삭제·이름변경·새폴더·다중선택·정렬·숨김파일 + **휴지통** |
| 이미지 | 기본 뷰어 + **만화 모드(CBZ·CBR·폴더)** + 애니메이션(GIF·APNG·애니WebP) + EXIF·회전·공유 <br>→ APNG 은 **넣지 않기로 했다**(첫 장면과 고지). GIF·애니WebP 는 만화 뷰어와 이미지 뷰어(14단계)가 튼다 |
| 미디어 | 컨테이너는 넓게, 코덱은 기기가 주는 만큼(아래 "지원하지 않는 것") |
| 텍스트 | **읽기 전용.** 문법 강조, 인코딩 자동감지(UTF-8·CP949·EUC-KR·Shift_JIS), 대용량 |
| 문서 | PDF, EPUB, docx·xlsx·pptx(**직접 구현**), HWPX, HWP 5.0 |
| 압축 | ZIP·7z·RAR (junrar 의 UnRAR 라이선스를 수용하기로 했다. 고지 의무를 지킨다) |
| 재생 | 백그라운드·알림·이어폰 버튼·오디오 포커스 / 폴더 연속재생·이어보기 / 자막(SRT·ASS·내장) / PiP / 속도·트랙·A-B / **VLC 차용**(아래) |
| UI | 한국어. VLC·Solid Explorer 등 널리 쓰이는 앱의 관행을 따른다 |

**VLC 에서 차용하기로 한 것** — 현재 폴더(하위 폴더 제외)에 오디오를 가진 미디어가 있으면
플로팅 ▶ 버튼이 뜨고, 누르면 그것들로 임시 플레이리스트를 만들어 순차 재생. 셔플 가능.
화면을 꺼도 소리 계속. **블루투스 연결/해제 시 자동 resume/pause.**

**1단계에서 제외** — 네트워크 저장소(SMB·FTP·WebDAV), 앱 잠금, 홈 위젯, 전문검색 인덱스,
용량 분석, 탭·듀얼페인, 오피스 편집, 레거시 doc·xls·ppt, RAW 사진.
(네트워크 저장소는 `INTERNET` 미선언 결정과 맞물려 있어 도입하려면 그 결정부터 뒤집어야 한다.)

**이 프로젝트의 목적은 유휴 토큰을 의미 있게 쓰는 것이다**(사용자, 2026-09-23). 그래서 위
'제외' 와 아래 '지금 지원하지 않는 것' 은 **영구 결정이 아니다.** 편집(텍스트·문서)처럼
'하지 않는다' 로 적어 둔 것도 필요해지면 다시 연다 — 파일 관리자 기능이 이미 파일을
조작하므로 '이 앱은 파일을 바꾸지 않는다' 는 애초에 앱 전체의 불변식이 아니다. 다시 열 때
지키는 것은 **안전 규칙**이다: 원본을 파괴할 수 있는 경로는 목록(지금 다섯)에 더하고,
임시 파일 → fsync → 원자적 rename 으로 쓴다. **라이선스가 막는 것은 예외다** — RAR 만들기는
UnRAR 라이선스가 압축기 재구현을 금지한다.

---

## 툴체인 — 실측으로 확정한 값

**추측이 아니다. 아래는 이 기계에서 실제로 빌드가 된 조합이다.**

| | 값 | 왜 이 값인가 |
|---|---|---|
| AGP | 9.4.0 | 설치된 Android Studio 2026.1.4 가 `usb-tether` 를 이 버전으로 올렸다 |
| Gradle | 9.6.0 | 같은 업그레이드가 고른 값. 배포판이 이미 캐시에 있다 |
| 데몬 JDK | 21 (JBR) | `gradle/gradle-daemon-jvm.properties`. 시스템 java 는 25 지만 데몬은 21 로 통일한다 |
| Kotlin | 2.3.20 | KSP 2.3.12 의 POM 이 요구하는 짝. IDE 번들은 2.3.10(같은 2.3 라인) |
| compileSdk | 37 | Compose BOM 2026.09.00 이 `minCompileSdk=37` 을 요구한다 |
| targetSdk | 36 | **반드시 명시한다.** 안 적으면 AGP 9 가 조용히 compileSdk(37)로 채운다 |
| minSdk | 31 | |

### 함정 — 다시 밟지 마라

- **외부 `org.jetbrains.kotlin.android` 를 적용하면 설정이 죽는다.** AGP 9.4 의 새 DSL 과
  충돌해 `ApplicationExtensionImpl cannot be cast to BaseExtension` 이 난다.
  **`android.builtInKotlin=true`(기본값)로 내장 Kotlin 을 쓴다.** `usb-tether` 가 Kotlin
  플러그인 없이 `.kt` 를 컴파일하던 것이 이 때문이었다.
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
- **Room 마이그레이션의 `execSQL` 은 `androidx.sqlite.execSQL` 확장 함수다**(위 참고).
- **`InputStream.readNBytes` 는 안드로이드에 API 33부터 있다.** 자바 9 의 함수라 컴파일도
  되고 JVM 시험도 통과하는데, minSdk 31 기기에서 `NoSuchMethodError` 로 **앱이 즉사한다.**
  7단계에서 실제로 죽였다. `java.io`·`java.nio` 의 최신 함수를 쓰기 전에 API 수준을
  확인하고, 매 단계 `:app:lintRelease` 를 돌린다(`NewApi` 가 이것만 잡는다).
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
  형식(SRT·ASS)은 사라진 것이 아니라 `codecs` 로 옮겨 가 있다 — 바로 아래 줄. (처음에는 '알 길이 없다' 로 알고 형식을
  적지 않았다가, 그다음 검토가 `codecs` 를 찾아 `한국어 · SRT` 가 되살아났다.)
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
- **`WebSettings.blockNetworkLoads`·`blockNetworkImage` 를 켜면 우리가 내주는 그림도
  막힌다.** 겹겹 방어로 켰다가 기기에서 잡았다 — **같은 책의 CSS 는 붙는데 `<img>` 만
  깨진 아이콘으로** 떴다. 우리 URL 이 `https://` 라 WebView 가 그것을 네트워크 이미지로
  세고, 그 검사가 `shouldInterceptRequest` 보다 앞에 있다. 막는 일은 다른 두 겹이 한다 —
  `INTERNET` 미선언(OS 수준)과, 우리 호스트가 아닌 요청에 **빈 404** 를 돌려주는 가로채기.
  (`shouldInterceptRequest` 에서 `null` 을 돌려주면 안 된다 — 그것은 "WebView 가 알아서
  가져가라" 는 뜻이라 시도가 일어나고 그동안 쪽이 멎어 보인다.)
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
  으로 적는다. 스크래치패드의 `pinch.py` 가 그것이다.
- **Git Bash 의 here-document 는 역슬래시를 먹는다.** `python - <<'PY'` 로 코틀린 코드를 고치면
  문자열 안의 `\n` 이 진짜 줄바꿈이 되고 `'\u0000'` 이 NUL 문자가 되어 파일에 박힌다(둘 다
  실제로 밟았다 — `grep` 이 그 파일을 '바이너리' 로 보기 시작했다). 역슬래시가 든 편집은
  스크립트를 파일로 써서 돌리거나 `Edit` 도구로 한다.
- **코틀린의 블록 주석은 겹친다.** KDoc 안에 `_rels/` 뒤로 별표가 붙은 경로(`xl/` + `*.xml`
  꼴)를 적으면 거기서 **주석이 하나 더 열리고** 닫히지 않는다 — 컴파일러는 파일 끝에서
  `Unclosed comment` 만 말한다. 자바와 다르다. 12단계에서 밟았다. 주석에 경로 패턴을 적을
  때는 '`_rels` 폴더의 관계 파일' 처럼 말로 쓴다.
- **칸으로 나눈 화면(`Column`) 옆의 `SnackbarHost` 는 맨 위에 그려진다.** 부모가 `Box` 가 아니면 정렬을
  모른다 — 12단계의 첫 진입 고지가 제목 막대를 가렸고, 11단계의 EPUB 이어보기 안내도 같았다(아무도 눈치채지
  못했다). 알림을 띄우는 화면은 `Box` 로 감싸 `align(BottomCenter)` 한다.
- **WebView 의 `onPageFinished` 는 문서 전체의 배치가 끝나야 온다.** 칸 10만 개짜리 표는 3초에 보이고 45초에
  그것이 왔다 — 그동안 '여는 중' 동그라미가 다 그려진 표를 덮었다. '보이기 시작했다' 는 `onPageCommitVisible`
  (API 23)이다.
- **JVM 시험의 kxml2 2.3.0 은 요소 안에서 잘린 XML 을 조용히 `END_DOCUMENT` 로 끝낸다.** 안드로이드의 파서는
  던진다고 알려져 있으나 **기기에서 확인하지 않았다**. 그래서 '깨진 부분' 을 시험할 때 JVM 에서는 통과하고
  기기에서는 부분 실패가 되는(또는 그 반대) 갈림이 생길 수 있다 — pptx 는 스스로 던지게 맞췄다.
- **스크립트에서 `gradlew.bat` 에 `--tests "*"` 를 주면 배치 파일이 별표를 파일 이름으로 펼친다**
  (`Task .gradle not found`). `io.github.donggi.*` 처럼 꾸러미로 거른다.
- **msoffcrypto-tool 6.0 은 오라클로서 빈틈이 있다.** 4096바이트 이하의 `EncryptedPackage` 를 CFB 의 미니
  스트림 자리에 잘못 쓰고, SHA-1 Agile 의 검증값·HMAC 을 확인하지 못하며, AES-192 Agile 을 풀지 못한다(열쇠를
  자르지 않는다). 그 조합의 표본은 우리 파이썬 암호기와 우리 복호화기가 서로를 맞춘 것일 뿐이다. 실세계 말뭉치가 둘을 더
  찾았다 — AES-256 에 SHA-1 을 쓴 Agile 은 `Invalid key size (160)` 으로 풀지 못하고(EN05), 적힌 스트림 크기가 섹터 사슬보다
  길면 풀지 못한다(EN15). 그런 표본의 오라클은 명세대로 따로 쓴 파이썬 복호화기(HMAC 확인)다(`samples-local/corpus/tools/`).
- **점수가 낮은 까닭을 '설계' 나 '오라클의 빈틈' 으로 적기 전에 빠진 글을 눈으로 봐라.** 말뭉치 검증이 둘을 그렇게 넘겼다가
  검토에서 뒤집혔다 — XM01 의 재현율 0.903 을 '5만 칸 상한(설계)' 으로 설명했는데 상한 뒤에는 빈 칸뿐이었고(차이는 오라클이
  숨긴 행·열을 본 것이었다), 오히려 상한이 **잃은 것도 없이 '줄였다' 를 알리고** 있었다. DX12 의 정밀도 0 을 'python-docx 가
  수식을 못 읽는다' 로 넘겼는데 그 수식의 이항 계수를 우리가 **나누기**(`(n/k)`)로 적고 있었다.
- **OOXML 의 필드는 저장된 글을 믿지 마라.** 파워포인트의 슬라이드 번호(`a:fld type="slidenum"`)는 마스터에 `‹#›` 로, 슬라이드에는
  저장할 때의 옛 번호로 적혀 있다 — 글 그대로 그리면 모든 슬라이드에 `‹#›` 가 찍힌다(PP17). 워드의 옛 양식 확인란은 결과 글이
  **비어 있다**(상자는 워드가 그린다). 필드는 종류를 보고 우리가 채운다.
- **위생기(`Urls.rewrite`)는 URL 속성에서 공백을 지운다.** `java\nscript:` 로 스킴 검사를
  피해 가는 수법을 막으려는 것이라 옳은 동작인데, 그 탓에 부분 이름을 그대로 `src` 에 적으면
  `my image.png` 가 `myimage.png` 가 되어 **그림이 조용히 사라진다.** `#`·`?`·`%` 가 든 이름도
  조각·질의·인코딩으로 잘못 읽힌다. 본문에 적는 패키지 이름은 언제나 `FlowUrls.encode` 를
  지난다(OOXML 은 `OpcNames.toUrl` 이 그것을 부르고, `FlowDocumentBase` 의 리졸버가 한 번 더 그 모양으로 다시 적는다).
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
  '깨졌다' 로 나갔다**(검토가 잡은 회귀). 가리는 일은 **암호 없이 따로 연 파일**로, 항목마다 예외를
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
  (맥·리눅스의 표준)로 잠근 파일의 **맞는 암호가 '틀렸다'** 가 된다(검토가 잡았다).
- **확인 바이트 하나로 암호 후보를 고르지 마라.** 틀린 후보도 1/256 로 통과한다. 항목마다 첫 통과
  후보를 쓰면 한글 암호(반디집은 CP949)의 300쪽 만화에서 한 쪽 이상이 틀린 열쇠로 풀려 깨질 확률이
  69% 다. 후보는 아카이브마다 한 번, 항목 하나를 CRC·인증값까지 풀어 보고 정한다
  (`ZipArchiveReader.keys`).
- **ZIP 암호의 인코딩은 도구마다 다르다.** 명세(APPNOTE)가 정하지 않는다. **반디집은 한글 암호를
  CP949 로 넣는다** — 반디집으로 잠근 표본이 UTF-8 로는 풀리지 않았다(실측). 그래서
  `ZipDecryption.candidates` 가 UTF-8 · CP949 · ISO-8859-1 을 차례로 시도한다. 7z 는 명세가
  UTF-16LE 로 정해 후보가 하나다.
- **이미지 뷰어의 바닥층은 생각보다 크게 뜬다.** 표본이 2의 거듭제곱이고 목표가 '화면의 긴 변'
  이라, 4000×3000 사진을 2400 화면에 맞추면 표본 1 — **원본 그대로** 뜬다(48 MB). 그래서
  원본이 4000 인 사진에는 선명화 조각이 필요 없고 요청되지도 않는다. 조각을 시험하려면 원본이
  바닥층보다 커야 한다 — 8000×6000 을 썼다(바닥층 4000, 조각은 맞춤의 4.4배 넘어서부터).
- **HWPX 는 EPUB 처럼 생겼다.** `mimetype` 과 `META-INF/container.xml` 이 둘 다 있어서, 이름만 보는 EPUB
  판별기가 먼저 돌면 한글 문서를 전자책으로 연다(13단계 배선에서 잡았다). 판별기 차례는 `FormatRegistry.probes`
  한 곳이 정하고 **HWPX 가 EPUB 보다 먼저**다. 새 ZIP 기반 포맷을 더할 때는 기존 판별기가 그것을 가로채지
  않는지부터 본다.
- **한글의 문단 여백은 HWPUNIT 의 두 배로 적혀 있다.** 명세는 말하지 않는다. HWPX 의 `hp:switch` 가운데
  `hp:case`(HwpUnitChar 이름공간)만 진짜 HWPUNIT 이고 `hp:default`·옛 HWPX·HWP 5.0 의 값은 그 두 배다 — 그대로
  읽으면 내어쓰기가 두 배로 들어가 본문이 오른쪽으로 쏠린다. 오라클(바로보기)의 pt 와 짝 문서로 확인했다.
- **한글은 글자 모양의 넷에 하나꼴로 `hh:strikeout shape="3D"` 를 적는다**(표본 아홉 2,243개 중 560개, K26 은 86%,
  '바탕글' 포함). 있다는 것만으로 취소선을 켜면 본문 전체에 줄이 그어진다. **HWP 5.0 도 같은 글자 모양이 '여부 1 · 모양
  3D 단선' 으로 켜져 있다**(짝 표본에서 번호마다 대조) — 한글도 바로보기도 줄을 긋지 않으므로 두 변환기 모두 3D 모양을
  꺼짐으로 본다. 왜 긋지 않는지는 확인하지 못했다.
- **JVM 시험의 kxml2 는 BMP 밖의 문자 참조(`&#xF02B6;`)를 16비트로 잘라 읽는다.** 그 모양으로 표본을 만들면
  시험이 엉뚱한 글자를 본다. 한글은 사설 영역 글자를 UTF-8 그대로 적으므로(K27 실측) 표본도 글자 그대로 넣는다.
- **한컴의 사설 영역 글자(U+F02B1… 등)는 폰에서 빈 네모다.** 기기에 한컴 글꼴이 없다. 절 번호·글머리표가
  전부 그것이라 보이는 글자로 바꾼다(`format:html` 의 `HancomChars`·`BulletGlyphs` — **두 한글 변환기가 한 표를
  쓴다.** 한쪽에만 두었더니 같은 문서가 HWP 로는 보이고 HWPX 로는 두부였다). 표는 오라클 변환기가 보여 준 글자에서
  얻었다 — 한컴의 공개 표는 찾지 못했다.
- **두 한글 변환기는 같은 판단을 해야 한다.** 13단계 끝에 어긋남 열하나(양쪽 정렬·제목·한컴 글자 표·취소선·흰 글자·흰 칸·
  빈 칸·그림 설명·여백 상한·글꼴·칸 세로 정렬)가 한꺼번에 나왔고, 그것을 고친 뒤 **일관성만 보는 검토**가 열다섯을 더 찾았다
  (문단의 바탕 글자·줄 간격 단위·부분 나누기·도형 배지·캡션 자리·바탕 스타일·번호 표…) — 둘을 따로 만들고 따로 검토했기
  때문이다. **짝 문서가 있으면 글자만이 아니라 HTML 의 구조(태그·CSS 속성의 분포)로 견줘라** — 여덟은 글자 대조가 0.99 를 넘는
  짝에서 나왔다. 한쪽을 고치면 다른 쪽에 같은 자리가 있는지 찾고, 표·규칙은 `format:html` 에 하나로 둔다 — 지금 거기 있는
  것: 한컴 글자·글머리표·글꼴(`HancomGlyphs`), 바탕 스타일·부분 나누기·제목 다듬기(`HancomFlow`), 번호 모양과 셈·주석 표지
  (`HancomNumbers`), 그림 설명문의 자동 설명 가리기(`HancomAlt`, 14단계). 둘을 함께 여는 시험은 조립하는 `app` 에 있다(`HancomPairTest` — 짝의 부분 수·이름·첫 글).
- **한글 문서의 양쪽 정렬(`JUSTIFY`)을 `text-align:justify` 로 옮기지 마라**(배분·나눔만 옮긴다). 거의 모든 문단이 양쪽 정렬이고 우리 껍데기가
  `word-break:keep-all` 이라, 좁은 폰 화면에서 줄마다 낱말 사이가 크게 벌어진다(13단계 기기 확인). 두 변환기가
  같은 판단을 해야 한다 — 한쪽만 옮기면 같은 문서가 포맷에 따라 달리 보인다.
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
  `ZipException`, 중앙 디렉터리 자리가 틀리면 맨 `IOException` 을 던진다. 사용자는 디스크가 고장 난 줄 안다 — EPUB 과 OOXML 이
  리더를 여는 자리에서 '깨진 파일' 로 옮긴다(PDF 의 `PdfFailures`·CFB 의 `CorruptFormatException` 과 같은 까닭).
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
  DOCTYPE 이 제곱 시간을 만들었다(검토가 잰 값: 0.5 MB 에 22초, 32 MiB 상한이면 하루). **시간 시험은 옛 판이 확실히 넘는
  크기로 만든다** — 0.56 MB 로 짰더니 JIT 이 옛 판의 단순한 고리를 빠르게 돌려 3초 안에 들어와, 시험이 결함을 보지 못했다
  (고침을 되돌려 확인했다). 2.2 MB 에서 옛 판은 3초를 넘고 지금 판은 42 ms 다.
- **DataStore 에 `corruptionHandler` 를 비워 두지 마라. 설정 흐름을 `stateIn` 으로 드는 곳은 읽기 실패에 죽는다.** 처리기가
  없으면 깨진 설정 파일의 `CorruptionException`(`IOException`)이 읽는 흐름으로 그대로 나오고, 목록 ViewModel 이 설정을
  `stateIn(…, Eagerly)` 로 들고 있어 그 예외가 앱을 죽인다 — 파일이 그대로 남으므로 **켤 때마다** 죽고, 크래시 기록을 볼 설정
  화면에도 닿지 못한다(14단계 검토가 잡았다). `ReplaceFileCorruptionHandler { emptyPreferences() }` 를 붙이고 모든 흐름이
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
  (`BookEvents.discardPending`). 9단계 뒤 감사가 적은 '다음 문서의 화면에 배달한다' 와 같은 자리다.
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
- **Material 3 `Slider` 는 화면 낭독기에 범위의 백분율로 읽힌다.** '13 sp' 가 '30%' 로 들린다 — `contentDescription`(줄 제목)과
  `stateDescription`(실제 값)을 준다.
- **`PdfRenderer.Page.render` 에 행렬과 클립을 함께 주면 클립은 자르기만 한다.** 위 '`matrix = null` …' 의 짝이다 — 행렬 없이
  클립만 주면 쪽을 클립에 축소해 넣고, 행렬만 주면 **비트맵 전체가 클립**이라 자르기 상자 밖의 그림·재단선이 옆 쪽 자리에
  번진다(두 쪽 보기에서 드러났다 — 쪽 하나일 때는 비트맵이 곧 쪽이라 보이지 않는다).
- **`uiMode` 는 이 앱의 `configChanges` 에 없다.** 밤 모드를 바꾸면 액티비티가 다시 서고, ViewModel 의 상태는 남지만 컴포지션의
  상태(찾기 막대·뒤로 처리기)는 사라진다 — PDF 찾기가 칠은 남은 채 막대만 사라졌다. 화면이 켜 둔 것을 VM 에 물어 다시 세운다.
- **명세가 요구한 문장은 원문에서 글자로 옮겨라.** 한컴의 고지 문장은 '한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp)' 이고 첫 음절이 옛한글
  조합형 자모 셋(U+1112 U+119E U+11AB)이다. 13단계가 KDoc 으로 옮길 때 그 음절이 빠졌고, 14단계가 화면에 그 KDoc 을 바이트까지
  같게 옮겼다 — **두 곳을 견주는 시험은 둘이 함께 틀린 것을 보지 못한다.** 명세 원문(S01)을 우리 변환기로 열어 코드포인트로
  확인했고, 시험이 그 글자를 박는다(`NoticeCatalogTest`).
- **값을 지우는 소비자를 `collectAsStateWithLifecycle` 의 기본값(STARTED)으로 돌리지 마라.** 다른 액티비티가 뜨는 동안 아래 액티비티는
  PAUSED 이면서 아직 STARTED 이고, 스낵바의 시계는 `delay` 라 가려진 뒤에도 흐른다. 파일 목록이 그 틈에 온 재생 실패로 **보이지 않는
  스낵바**를 띄우고 4초 뒤 `consumeFailure()` 를 불러, 재생 화면의 실패 문구와 '다른 앱으로 열기' 가 저절로 사라졌다(에뮬레이터 실측).
  말하고 지우는 일은 `repeatOnLifecycle(RESUMED)` 안에서만 하고, 지우기 직전에 **말한 그 값이 아직 남아 있는지** 다시 본다
  (`PlaybackNotice`). 앞의 '액티비티의 설정을 컴포저블이 쥐게 하지 마라' 와 같은 뿌리다 — 컴포지션은 가려진 것을 모른다.
- **안드로이드의 MIME 대조는 대소문자를 가린다.** `IntentFilter.findMimeType` 이 글자 그대로 견준다. 플랫폼 `MimeTypeMap` 은 읽을 때 전부
  소문자로 바꾸므로(표 파일에는 `macroEnabled` 가 대문자로 적혀 있어도) 우리 표도 전부 소문자여야 받는 앱이 나온다. 시험이 박는다
  (`MimeResolverTableTest`).
- **플랫폼 MIME 표는 생각보다 많이 안다 — 그리고 그 답을 받는 앱은 없을 수 있다.** `.xyz` 는 `chemical/x-xyz` 다. 구체적인 MIME 의 고르는
  창은 그것을 선언한 앱이 없으면 'No apps can perform this action.' 뿐이다 — '모든 앱에서 고르기' 로 넓힌 까닭이다(14단계 뒤 절).
- **고르는 창(`ACTION_CHOOSER`)은 받을 앱이 없어도 뜬다.** `startActivity` 는 성공하고 우리에게는 '띄웠다' 로 보인다 — 받을 앱이 없다는
  것을 결과로 알 길이 없다. 받는 앱이 하나면 고르는 창이 그 앱을 곧바로 띄운다(API 31 옛 고르는 창, API 35 IntentResolver 모두 실측).
- **`queryIntentActivities` 는 API 30 부터 우리에게 보이는 앱만 답한다.** 매니페스트의 `<queries>` 가 없으면 '받을 앱이 없다' 는 답이
  거짓일 수 있다. 그래서 그 답은 **넓히는 데만** 쓴다(막는 데 쓰면 공개 범위가 어긋난 기기에서 아무것도 못 연다).
- **설치 관리자는 `REQUEST_INSTALL_PACKAGES` 를 선언하지 않은 앱이 보낸 APK 를 말없이 닫는다**(targetSdk 26 이상, API 31 설치 관리자의
  dex 로 확인). 우리 쪽에는 성공으로 보인다 — 매니페스트의 그 줄을 지우면 APK 가 아무 말 없이 안 열린다.
- **`startActivity` 는 받는 앱의 창이 뜨기 전에 돌아온다.** 그 사이(수백 ms)의 누름은 우리 화면에 떨어진다 — APK 를 두 번 누르면 설치
  관리자가 두 겹 쌓였고, 시트·메뉴의 항목을 두 번 누르면 그것이 닫힌 자리 아래의 줄이 열렸다. 띄운 직후의 누름을 흘려보낸다.
- **Material 3 1.4.0 의 `ModalBottomSheet` 는 별도 대화상자 창이다**(`ModalBottomSheetDialogWrapper`, aar 에서 확인). 화면 아래의
  `SnackbarHost` 를 덮는다 — 시트가 떠 있는 동안 알릴 것은 시트 안에 적는다.

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
./gradlew test                    # 순수 JVM 테스트(파서·방어·정렬)
./gradlew :app:lintRelease        # **매 단계 끝에 돌린다.** 아래 문단 참고

# 계측 테스트. 에뮬레이터가 떠 있어야 한다. **다섯 모듈 전부 돌린다** —
# `:core:io` 만 적어 두었더니 8·9단계가 박은 17건이 한 번도 돌지 않았다.
# **모듈을 새로 만들면 이 줄에 더하는 것이 그 단계의 일이다.**
./gradlew :core:io:connectedDebugAndroidTest \
          :feature:archive:connectedDebugAndroidTest \
          :feature:comic:connectedDebugAndroidTest \
          :feature:docview:connectedDebugAndroidTest \
          :core:webhost:connectedDebugAndroidTest
```

**lint 를 매 단계 돌리는 이유는 `NewApi` 하나다.** 컴파일러는 `java.io` 의 함수가
minSdk 에 있는지 모른다 — 7단계에서 `InputStream.readNBytes`(API 33)를 써서 minSdk 31
기기에서 `NoSuchMethodError` 로 앱이 죽었다. **빌드는 통과했고 테스트도 통과했다.**
lint 만 그것을 본다. **지금 이 저장소에서 lint 는 오류 0으로 통과한다** — 새로 뜨는
것만 보면 된다. 남은 경고 10건(2026-09-28)은 `MANAGE_EXTERNAL_STORAGE` 정책 안내
(`ScopedStorage`), `targetSdk 36`(`OldTargetApi`), 새 버전 알림 여덟이다 — 14단계가 옛 코드·리소스의 셋과 쓰이지 않는
리소스 넷을 정리했다(09-27 에는 17건). **`:app:lintRelease` 는 라이브러리 모듈의 문제를 보고하지 않는다**(함정 표) — 모듈을
새로 만들거나 크게 고친 단계는 `:<모듈>:lintRelease` 를 따로 돌린다. 14단계 끝에 손댄 열한 모듈이 모두 오류 0 이고, 남은 경고는
`feature:browser` 5·`feature:image` 3·`feature:docview` 2·`core:io` 1 로 전부 옛 줄이다(모듈 lint 가 처음 잡은 `DefaultLocale` 하나는
고쳤다 — 14단계 절). '다른 앱으로 열기'(14단계 뒤)가 손댄 열 모듈도 오류 0 이고, 새로 모듈 lint 를 돈 둘의 경고
(`core:playback` 경고 1 `ExportedService` — 재생 서비스, `feature:player` 경고 1·힌트 1)는 이번에 손대지 않은 줄이다.
**`NewApi` 는 0건이다** — PiP 의 API 33 짜리 setter 넷(`setTitle`·`setSubtitle`·
`setCloseAction`·`setExpandedAspectRatio`)을 아예 부르지 않기로 한 것이 여기서 확인된다.

**계측 테스트는 다섯 모듈에 있다** — `:core:io` 32건(파일 조작·휴지통·EXDEV 25 + 다른 곳에 복원 7),
`:feature:archive` 19건(풀기 10 + 암호 아카이브 4 + tar·폴더 시각 5), `:feature:comic` 12건(쪽 디코딩·이어보기 10 + 기본 방향·다음 권 2),
`:feature:docview` 19건(PDF 열기·렌더·타일·닫기 10 + 암호 PDF 8 + 실세계 PDF 말뭉치 1 — `samples-local/corpus/` 를
기기에 밀어 두었을 때만 돈다), `:core:webhost` 7건 (가짜 출처의 경로 계약). 합계 **89건**(2026-09-28, 두 에뮬레이터 모두 통과). 새 모듈 `feature:settings` 는 계측이 없다(JVM 13건). 암호 아카이브 표본(`format/archive/src/test/resources/
archivecrypt/`)도 JVM 시험과 한 벌을 나눠 쓴다 — `feature/archive/build.gradle.kts` 가 붙인다. 암호 PDF 표본은 JVM 시험과 **한 벌을 나눠 쓴다** —
`feature/docview/build.gradle.kts` 가 `src/test/resources` 를 계측 APK 의 자산으로 붙인다.
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

**R8 은 1단계부터 켜져 있다.** 12단계에 가서 처음 켜면 파서들의 리플렉션과 디코더 제거가
한꺼번에 터진다. 매 단계 끝마다 release 를 설치해 그 단계 기능을 훑는 것이 규칙이다.

### 에뮬레이터

AVD 둘이 있다. 실기기(Android 12 폰 / Android 15 태블릿)에 맞춰 만든 것이다.

| AVD | API | 화면 | 쓰임 |
|---|---|---|---|
| `Android_12_Phone` | 31 | 1080×2400 | 하한 검증. 일상 개발 |
| `Android_15_Tablet` | 35 | 2560×1600 | 가로·태블릿 레이아웃, 회전 |

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

**adb 로 검증할 때 실제로 밟은 함정 셋.**

- **`adb push` 는 경로가 틀려도 조용히 아무것도 안 한다.** MSYS 경로(`/c/...`)를 주면
  성공한 것처럼 보이고 기기 파일은 그대로다. 그 상태로 시험하면 앱이 옛 파일을 옳게
  보고하는데 앱을 의심하게 된다 — 실제로 mkv 코덱 판정을 그렇게 한 시간 헤맸다.
  **push 뒤에 기기 쪽 크기와 시각을 반드시 확인한다.**
- **`adb shell run-as cat` 은 바이너리를 깨뜨린다**(CRLF 변환). SQLite 파일을 그렇게
  꺼내면 "file is not a database" 가 난다. **`adb exec-out` 을 쓴다.**
  그리고 `run-as` 는 **디버그 빌드에서만** 된다 — 릴리스 앱의 내부 파일은 볼 수 없다.
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

---

## 실측표

**추정으로 적지 않는다.** 아래는 전부 이 기계·이 에뮬레이터에서 실제로 잰 값이고,
잰 방법은 앱 안의 진단 화면(2단계, `feature:diag`)이다. 값이 바뀌면 여기를 고친다.

### 문자셋 (2026-09-12, Android 12 에뮬레이터)

| 이름 | 안드로이드가 돌려주는 것 | 개발 PC 의 JVM |
|---|---|---|
| `x-windows-949` | **`EUC-KR`** | `x-windows-949` |
| `MS949` | **`EUC-KR`** | `x-windows-949` |
| `windows-949` | **`EUC-KR`** | `x-windows-949` |
| `EUC-KR` | `EUC-KR` | `EUC-KR` |
| `Shift_JIS`·`windows-31j` | `Shift_JIS` | — |
| `GB18030`·`Big5`·`UTF-16LE` | 그대로 | — |

**두 환경이 다르다.** 안드로이드는 CP949 별칭을 전부 `EUC-KR` 로 정규화한다. 그래서
`EntryNameDecoder` 는 이름을 하드코딩하지 않고 후보를 순서대로 찾는다. 단위 테스트가
PC 에서 통과했다고 기기에서 같을 것이라고 믿지 마라 — 진단 화면이 기기의 답이다.

### 코덱 (Android 12 x86_64 에뮬레이터, 디코더 36개)

| 있음 | 없음 |
|---|---|
| H.264, HEVC, VP9, **VP9 Profile2(10bit)**, AV1, FLAC, Opus, Vorbis | **HEVC Main10**, ALAC, AC-3, E-AC-3, DTS |

에뮬레이터 값이고 실기기는 다르다. 이 표의 쓸모는 "**코덱은 기기가 주는 만큼**" 이라는
요구사항 해석이 실제로 어떤 모양인지 보여주는 것이다. 10단계는 재생 실패를 코덱 이름과
함께 알려야 한다.

**5단계에서 실제로 재생해 확인한 것**(`dumpsys media_session` 의 `state=3`, 위치가 늘어남).

| 파일 | 결과 |
|---|---|
| `.flac` | `state=3, position=2994` |
| `.opus` | `state=3, position=2727` |
| `.m4a`(AAC) | `state=3, position=2735` |
| `.mp4`(H.264 + AAC) | `state=3, position=2911` |
| `.mkv`(H.264 baseline + FLAC) | `state=3, position=6115` · `c2.goldfish.h264.decoder` 생성 |
| 지원하지 않는 프로파일 | `NoSupport [codec.profileLevel, avc1.F4001E, video/avc]` 로 **코덱 이름과 함께** 실패 |

마지막 줄이 요구사항의 그 항목이다 — 실패가 "재생할 수 없습니다" 로 끝나지 않고 무엇이
없는지 말한다.

### SDK 확장과 PDF (Android 12 에뮬레이터)

S 확장 = 1, R 확장 = 1 → **레거시 `PdfRenderer` 경로**(암호 PDF 불가).

**계획에 있던 3분기(플랫폼 35+ / `PdfRendererPreV`(확장 13+) / 레거시)를 11단계가
넣지 않기로 했다.** 근거는 SDK 의 `api-versions.xml` 이다 — `android.graphics.pdf.PdfRenderer`
의 `openPage`·`Page.render` 는 전부 `since="21"` 이고 `sdks` 속성이 없다. 즉 **우리가
부르는 API 는 확장 수준과 무관하게 같은 것 하나**이고, 분기를 넣어 봐야 같은 호출을
세 번 적는 일이 된다. 확장 13 이상이 주는 것은 암호 PDF 열기(`loadParams`)와 폼·주석
편집이다.

**암호 PDF 를 열기로 하면서(11단계 뒤) 분기가 하나 생겼다** — 암호를 받는 생성자
`PdfRenderer(pfd, LoadParams)` 는 **API 35 프레임워크**에만 있다(그 생성자에는 `sdks` 가
없다). 35 이상은 플랫폼이 풀고, 그 아래는 우리가 풀어 메모리 파일로 넘긴다(아래
'암호가 걸린 파일'). 확장 13 의 `PdfRendererPreV` 는 여전히 쓰지 않는다. 두 갈래 모두
에뮬레이터에서 돈다 — 분기를 거절했던 이유('실행해 보지 못한 갈래') 가 여기서는 성립하지
않는다. `@RequiresApi(35)` + `SDK_INT` 검사라 `NewApi` 는 여전히 0건이다.

### 저장소

볼륨 둘이 따로 잡힌다 — `/storage/emulated/0` 와 `/storage/0000-0000`(FAT, 512MB).
`StorageManager.storageVolumes` 가 둘을 준다. **4단계의 EXDEV 경로를 실제로 태울 수 있다.**

### 목록 성능 (Android 12 에뮬레이터, 파일 10,000개 폴더)

| | |
|---|---|
| 나열(`Os.lstat` 항목당 1회) | **394 ms** |
| 정렬(한국어 대조 + 자연 정렬, 키 미리 생성) | **87 ms** |
| 합계 | **481 ms** (판정 1초) |
| 스크롤 잔카 | 340프레임 중 7 (2.06%), 90퍼센타일 21 ms |

정렬 키를 항목마다 미리 만드는 것이 이 수치의 핵심이다. 비교할 때마다 `Collator` 를
부르면 1만 개에서 비교가 14만 번 일어나 그것만으로 수백 ms 가 든다.

### 파일 조작 (4단계, 계측 테스트 + 실제 조작으로 확인)

| 확인한 것 | 결과 |
|---|---|
| 볼륨 간 `rename` | **EXDEV 로 실패한다** — 계측 테스트가 이것을 먼저 단언한다. 이 전제가 깨지면 아래 시험이 EXDEV 경로를 한 번도 안 타고 '통과' 한다 |
| 내부 → SD 로 비어있지 않은 폴더 이동 | 하위 폴더까지 구조 그대로. 원본은 사라진다 |
| 2GB 복사 | 완주. `.iroiro-part` 임시파일 0개 |
| 복사 취소 | 임시파일이 남지 않는다. 다만 **정리에 수 초가 걸린다**(스트림을 닫는 시간) — 취소 직후에 확인하면 아직 있다 |
| 600MB 휴지통 왕복 | 같은 볼륨 `rename` 이라 즉시. 원래 경로로 복원 |
| 앱 강제 종료 후 휴지통 | 목록이 그대로(Room 이 정본) |
| 사진 삭제 | MediaStore 항목도 사라진다(갤러리에 유령이 없다) |
| 사이드카 내용 | `{uuid, deletedAt, isDirectory, size}` 뿐. **경로·이름 없음** |
| 포그라운드 서비스 | 400ms 뒤 `isForeground=true`, 채널 '파일 작업'(무음), 취소 액션 1개 |

### 재생 (5단계, Android 12 에뮬레이터)

| 확인한 것 | 결과 |
|---|---|
| 홈으로 나가기 → 화면 끄기 → 돌아오기 | 세션 위치가 **끊김 없이 증가**. 이어서 트는 것이 아니라 끊긴 적이 없다 |
| 최근앱에서 밀어 없애기 | 재생이 멈추고 서비스가 내려간다(`onTaskRemoved`) |
| 이어보기 | 기록은 1분 넘는 것만, 10초 지난 뒤부터, 끝 15초 안쪽은 지운다. **10단계에서 '자동으로 이어 틀고 되돌리기' → '처음부터 틀고 3초간 물어보기' 로 뒤집혔다**(아래 사용자 지적 표) |
| 세션에 붙는 컨트롤러 | 우리 앱·미디어 알림·Auto·`android` 만. 나머지는 거절(파일 이름이 메타데이터로 나간다) |

**백그라운드 재생에 스위치가 없다.** 플레이어가 서비스에 살기 때문에 끊는 코드가
없을 뿐이다. 화면은 `MediaController` 로 **돌고 있는 세션에 다시 붙는다.**

### 세로 고정을 무시하는 가로 재생 (6단계에 함께 넣음, 실측)

자동회전을 끄고(`settings put system accelerometer_rotation 0`) 에뮬레이터를 가로로 돌린
상태에서 잰 것이다. `adb emu rotate` 가 센서 방향을 바꾼다.

| 화면 | 결과 |
|---|---|
| 런처(대조군) | `1080x2400` — **세로 그대로.** 자동회전이 꺼졌으니 당연하다 |
| 우리 재생 화면(영상) | **`2400x1080`** — 가로로 돈다 |
| 재생 화면을 나온 뒤 브라우저 | `1080x2400` — 세로로 복귀 |

`ActivityInfo.SCREEN_ORIENTATION_SENSOR` 가 문서대로 **사용자의 자동회전 끄기 설정을
무시한다**는 것이 이 대조군으로 확인됐다. `OrientationEventListener` 로 각도를 직접 재는
흔한 방법은 필요 없다 — 그것은 우리가 센서를 다시 구현하는 것이다.

**영상일 때만** 건다(`state.hasVideo`). 소리만 나는 파일까지 따라 돌면 사용자가 세로 고정을
켜 둔 뜻을 정면으로 거스른다. 화면을 떠날 때 `UNSPECIFIED` 로 돌려 놓는다.

**방향 잠금 단추는 10단계가 넣었다.** 값은 [ActivityInfo.SCREEN_ORIENTATION_LOCKED] —
**지금 방향 그대로** 묶으므로 어느 방향인지 우리가 계산하지 않아도 된다. 잠금은 영상이
아니게 되어도 **풀리지 않는다**(`hasVideo` 는 항목을 건너뛰는 동안 잠깐 거짓이 되므로
그것으로 풀면 영상 → 영상으로 넘길 때마다 사용자가 건 잠금이 조용히 사라진다). 그래서
**잠겨 있는 동안에는 소리 파일에서도 단추를 남긴다** — 그러지 않으면 푸는 방법이 없다.

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

폰 AVD 는 `false` 라 요청이 먹는다. 태블릿 AVD 는 `true` 일 것으로 보이나 아직 재지 않았다.
**이유를 틀리게 적어 두면 10단계에서 필요 없는 opt-out 속성
(`PROPERTY_COMPAT_IGNORE_REQUESTED_ORIENTATION`)을 넣게 되므로 여기 정확히 남긴다.**

### 썸네일 캐시 (6단계 선반영, 에뮬레이터에서 실제로 확인)

| 확인한 것 | 결과 |
|---|---|
| 사진 8장 훑기 | 캐시 파일 8개 생성(각 5KB 안팎, 320px JPEG) |
| 다시 열기 | **새로 만들지 않는다** — 파일 시각이 그대로다 |
| 원본 2장 삭제 후 다시 열기 | 캐시 8 → 6. 사라진 둘이 정확히 그 둘 |
| `.nomedia` 가 있는 폴더 | 목록에서 통째로 빠진다(9장 중 8장) |
| 1600×1200 원본 | **400×300** 으로 디코딩(sample=4). 원본의 1/16 픽셀만 메모리에 올라간다 |
| 640×480 원본 | 320×240(sample=2) |

축소가 **헤더 단계**에서 정해지는 것이 핵심이다(`ImageDecoder.onHeaderDecoded` 에서
`setTargetSampleSize`). 12MP(4000×3000)면 sample=8 이라 500×375, 원본의 1/64 만 올라간다 —
통째로 디코딩하면 그 한 장이 48MB다. `ImageDecoder` 를 쓴 또 다른 이유는 **EXIF 회전을
알아서 적용**하는 것이다. `BitmapFactory` 로 읽으면 세로로 찍은 사진이 격자에서 전부 눕는다.

캐시는 `filesDir/thumbs/` 다. **공유 저장소의 `.thumbnails` 가 아니다** — 거기 쌓으면
모든 파일 접근 권한을 가진 어떤 앱이든 '이 사람이 어떤 사진을 갖고 있는가' 를 읽고,
원본을 지워도 축소본이 남는다. `cacheDir` 도 아니다(시스템이 비우면 수천 장을 다시 디코딩한다).

### 이미지 뷰어 (6단계, Android 12 에뮬레이터)

제스처는 **화면 캡처가 아니라 배율 로그로** 판정했다(위 함정 참고).

| 순서 | 제목 | 배율 |
|---|---|---|
| 진입 | `큰그림.png` | 1.0 · `canPan=false` |
| 더블탭 | 그대로 | **2.0** · `canPan=true` |
| 확대 상태로 밀기 ×2 | **그대로** | 2.0 — 팬이지 페이지가 아니다 |
| 더블탭 | 그대로 | 1.0 |
| 배율 1 에서 밀기 | `사진8.png` | 넘어간다 |

**확대 상태에서 페이지가 넘어가지 않는 것**이 이 화면에서 가장 깨지기 쉬운 계약이다.
`transformable(canPan)` 이 팬을 소비하면 부모인 페이저가 못 받는다 — 형제 `pointerInput`
의 순서에 기대지 않고 **부모–자식 계층**으로 계약을 세운 이유다.

### EXIF 무손실 회전 (6단계, 실측)

| | |
|---|---|
| 파일 크기 | 3,775 → **3,883 바이트**(+108 = EXIF 블록). 재인코딩이 아니다 |
| EXIF | `Orientation = 6`(시계 90도) |
| 수정시각 | **바뀌지 않는다** — 날짜 정렬과 이어보기 키가 끊기지 않는다 |
| 임시 파일 | 남지 않는다. 폴더의 죽은 `.iroiro-*.part` 도 함께 걷힌다 |
| 결과 알림 | **뷰어 안에서** '1개 완료' 가 뜬다(결과 소비기를 Root 로 올린 결과) |

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

### 썸네일 (6단계 개편, 실측)

| 확인한 것 | 결과 |
|---|---|
| 파일 목록 격자·목록 | 사진·동영상이 그림으로 뜬다. 동영상은 ▶ 오버레이 |
| 캐시 구조 | `filesDir/thumbs/<폴더해시>/<키>.jpg` — **폴더마다 통이 나뉜다** |
| 갤러리↔브라우저 | 서로의 썸네일을 지우지 않는다 |

**폴더 통으로 나눈 이유**는 소비자가 둘이 되었기 때문이다. 예전의 평평한 캐시 + "살아
있는 키에 없으면 전부 지운다" 는 **갤러리가 유일한 소비자**일 때만 성립한다. 브라우저가
임의 폴더에서 썸네일을 만들기 시작하면 서로를 지운다. 이제 **폴더를 통째로 열거한 쪽만**
(`DirectoryLister.Ready`) 그 통을 청소하고, 갤러리는 청소하지 않는다 — 이미지만 골라
훑으므로 자격이 없다.

`.nomedia` 폴더와 휴지통에서는 디스크에 남기지 않는다(`persist=false`). `core:ui` 는
`core:io` 를 볼 수 없어 그 판정을 못 하므로 호출자가 넘긴다.

**옛 평평한 캐시는 한 번 통째로 버린다.** 캐시를 위해 캐시를 옮기는 코드를 유지하지 않는다.

### 텍스트·코드 뷰어 (7단계, Android 12 에뮬레이터)

**인코딩 판정을 기기에서 실제로 확인한 것.** 같은 글을 인코딩만 바꿔 만든 표본이다.

| 표본 | 판정 | 화면 |
|---|---|---|
| UTF-8 | `UTF-8` | 한글 정상 |
| UTF-8 + BOM | `UTF-8` | 첫 줄 앞에 보이지 않는 글자가 붙지 않는다(BOM 을 바이트로 건너뛴다) |
| CP949 | **`CP949`** | 한글 정상 — 판정 근거 '글자 분포 100점, 다음 후보 0점' |
| Shift_JIS | `Shift_JIS` | 일본어 정상 |
| UTF-16LE + BOM | `UTF-16LE` | 한글 정상(디코드하며 세는 길) |
| 무작위 바이트를 `.txt` 로 | **거절** | '글이 아닌 파일로 보입니다' + '인코딩을 골라 열어 보기' |

**줄 끝 세 종류**(`
`·`

`·단독 `
`)가 모두 옳게 갈렸다.

**색인 처리량**(디버그 빌드, `Iro.d` 기록).

| 파일 | 크기 | 행 | 앵커 | 색인 | 강조 |
|---|---|---|---|---|---|
| `big.log` | 16,066,780 B | 200,000 | 782 (12,512 B) | **219 ms** | 꺼짐(일반 텍스트) |
| `Big.kt` | 5,940,000 B | 216,000 | 844 (13,504 B) | 76 ms | **꺼짐**(상한 4 MiB 초과) |
| `min.js`(줄바꿈 없는 한 줄) | 105,795 B | 20 | 2 | 15 ms | 39 ms |
| `strings.xml` | 7,080 B | 210 | 1 | 4 ms | 3 ms |

16 MB 를 219 ms 에 훑는다 — **`/sdcard` 의 FUSE 를 통해 약 73 MB/s** 다. 2단계가 7단계로
미뤄 둔 'FUSE 창 단위 읽기 속도' 가 이 값이다. 색인 메모리는 1 GB 파일로 환산해도
**0.8 MB** 수준이라(앵커 하나 16 B) 파일 크기 상한을 두지 않는 근거가 성립한다.

**화면에서 확인한 것.**

| 확인한 것 | 결과 |
|---|---|
| 20만 줄 파일에서 `줄 번호로 가기` 123456 | 정확히 그 줄로 간다 |
| 20만 줄 파일에서 찾기(끝에서 두 번째 줄) | `1 / 1`, 그 자리로 이동 |
| 줄 접기 켜기 | 줄 번호는 왼쪽에 남고 본문만 접힌다 |
| 가로로 밀기(접기 끔) | **모든 행이 함께** 민다. 줄 번호 칸은 제자리 |
| 미니파이 JS 4,096자 조각 경계 | 문자열 색이 조각을 넘어 이어진다 |
| 인코딩 시트 | 후보마다 앞 두 줄을 그 인코딩으로 디코드해 보여 준다 — **읽히는 것을 고르면 된다** |
| 5.9 MB `.kt` | `Kotlin · 강조 꺼짐` 으로 **말한다**(숨기지 않는다) |

### 고정폭 글꼴 — 칸이 맞지 않는다 (7단계, 실측)

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

**ZIP 만 엔트리 수가 다른 것은 이 시험이 두 번 불안정했기 때문이다.** 7z 을 같은 크기로
못 키우는 것은 그쪽이 제곱이라 2000개면 분 단위가 되기 때문이다.

**시간 시험이 흔들린 두 번과, 그때마다 배운 것.**

1. **표본이 너무 작았다**(9단계). 60·120개로 재던 ZIP 은 이 기계에서 **5 ms** 라 배율이
   비용이 아니라 타이머 잡음을 쟀고, 같은 시험이 0.74배와 3.41배를 번갈아 냈다
   (`./gradlew test` 가 세 번에 한 번 실패했다). → **몇십 ms 대로 키우고, 같은 일을
   여러 번 재 가장 빠른 값을 쓴다**(잡음은 언제나 시간을 늘리는 쪽으로만 작용한다).
2. **두 측정을 따로 쟀다**(9단계 뒤 감사). 최소값을 써도 `./gradlew test` 가 모듈별
   테스트 워커를 **병렬로** 돌리는 동안 7z 의 배율이 3.8 에서 **2.02** 로 주저앉아 또
   실패했다 — 작은 쪽(0.8초)의 세 번이 전부 붐비는 구간에 떨어지고 큰 쪽(3초)은 그
   구간을 평균으로 흡수한 것이다. **짧은 측정일수록 같은 길이의 방해에 더 크게 부푼다.**
   → **두 크기를 번갈아 잰다**(`fastestPair`). 붐비는 구간이 두 측정에 같은 확률로
   걸리므로 부하가 있으면 두 값이 함께 커질 뿐 배율은 남는다.

고친 뒤 세 번 연속으로 7z 3.83~4.14배 · ZIP 1.63~1.89배가 나왔고, `./gradlew test`
전체를 `--rerun-tasks` 로 세 번 돌려 모두 통과했다.

**세 번째로 흔들렸다**(10단계 잔여 작업). JVM 시험이 268 → 309건으로 늘어 병렬 워커가
그만큼 오래 겹치자 같은 시험이 **양쪽 방향으로 번갈아** 깨졌다(7z 배율이 모자라는 쪽과
ZIP 배율이 넘치는 쪽). 원인은 앞의 둘과 같고 해법도 같은 방향이다 — **표본을 세 번에서
다섯 번으로 늘렸다.** 상한을 푸는 길은 쓰지 않았다. **배율을 단언하는 시험은 절대
시간이 아니라 두 측정의 관계를 지켜야 한다** — 그러려면 두 측정이 같은 환경을 봐야 한다.

7z 는 `open(N)` 마다 파일을 새로 열고 `nextEntry` 를 N번 돌린다. 1,000개짜리를 하나씩
풀면 몇 분이 걸린다. **그래서 푸는 길은 `extractSequentially` 하나뿐이고, `open` 은 한
항목을 보여 주는 용도로 좁혔다.** 같은 60개에서 702 ms → 34 ms, **20배**다.

**기기에서 확인한 것**(Android 12 에뮬레이터, 릴리스 R8 포함).

| 확인한 것 | 결과 |
|---|---|
| ZIP 폴더 트리 | `docs`·`src` 로 접히고 한글 이름 정상 |
| CP949 파일명(플래그 없음) | `한글이름.txt`·`자료` 로 복원 |
| 7z 40개 열기·풀기 | `solid=true` 로 잡히고 40개 전부. 내용이 **번호와 일치**(`ENTRY-39`) |
| **경로 탈출 아카이브** | `etc/passwd`·`Windows/evil.dll` 은 목적지 **안**에 풀리고, `../../../../sdcard/탈출.txt` 는 **만들어지지 않았다**. `/etc/passwd` 는 그대로 |
| 풀 수 없는 이름 | 목록에 **빨간 경고와 함께 보이고**, 대화상자가 '항목 2개는 풀 수 없습니다' |
| 압축폭탄(200 MB/199 KB) | 목록은 보이고, 풀기는 압축비 상한에서 끊긴다. `zero.bin` 없음 |
| 수정시각 | 아카이브의 `2026-09-12 10:00` 이 그대로(폴더는 '지금' — 아래 '미룬 것') |
| '여기에 풀기' 충돌 | '항목 2개가 흩어집니다' + '`docs` 가 이미 있습니다' + '파일 2개를 덮어쓰게 됩니다' + 이름 표본 |
| 둘 다 보관 | 원본 `README.txt` 그대로, 새것은 `README (2).txt` |
| 임시 파일 | 성공·실패·폭탄 어느 쪽에서도 `.iroiro-*.part` 가 **남지 않는다** |
| 릴리스(R8) 7z 풀기 | 40개 완주. **자가시험이 아니라 실제 경로로** LZMA2 관문을 통과했다 |

**임시 파일이 남던 것을 여기서 잡았다.** `abort()` 가 "대상이 없고 임시본이 온전하면
그것이 남은 유일한 사본일 수 있다" 는 이유로 지우지 않았는데, 그 조건은 **두 호출자 모두에서
성립하지 않는다**(복사는 원본이, 풀기는 아카이브가 그대로 있다). 폭탄을 막은 자리에
1,042,580바이트짜리 숨은 파일이 남았다. 지금은 언제나 지운다.

### 압축 (릴리스 R8 full mode APK 에서 확인)

| 항목 | 결과 |
|---|---|
| CP949 zip 파일명 | `한글이름.txt` · `EUC-KR` 로 복원 |
| UTF-8 무플래그 zip | `한글이름.txt` · 판정으로 복원 |
| **LZMA2 7z (R8 관문)** | 42,000바이트 복원 · 압축비 217:1 · CRC `d626e491` |
| 경로 탈출 이름 | 차단 |
| 압축 RAR5 | **미검증** — 표본을 만들 수 없다(junrar 에 쓰기 구현이 없다) |

R8 keep 규칙(`org.tukaani.xz.**`, `com.github.junrar.unpack.**`)이 실제로 듣는다는 뜻이다.
**이 확인은 매 단계 릴리스 스모크에서 다시 한다** — 규칙이 지워지면 빌드는 통과하고
사용자가 7z 를 여는 순간에 터진다.

### 만화 뷰어 (9단계, Android 12 에뮬레이터)

**쪽 판정을 눈으로 하지 않았다.** 표본을 쪽마다 다른 단색으로 만들고 화면 캡처의 색을
읽어 "지금 몇 쪽인가" 를 기계가 답하게 했다 — 6단계가 제스처를 캡처로 판정하려다 세 번
틀린 것과 같은 일을 피하려는 것이다.

**예산**(`ImageLimits.budgetOf` 의 첫 실사용. 힙 등급 192MB, 화면 1080×2400).

| | 값 |
|---|---|
| 한 장(`baseBytes`) | 10,368,000 B |
| 살아 있는 양(`liveCap`) | 41,472,000 B = **네 장** |
| 쪽 하나의 상한(`pageCap`) | 10,368,000 B |  ※ 11단계가 `comicPageCap` 에서 이름만 바꿨다(PDF 도 같은 값을 쓴다)
| 창(`ComicLimits.windowBytes`) | 20,736,000 B |
| 선명화층(`detailCap`) | 8,859,648 B |

**쪽 하나의 값**(1200×1800 PNG, 파일당 2.2 MB짜리 30쪽 아카이브).

| 경로 | 읽기 | 디코딩 |
|---|---|---|
| ZIP, 아무 쪽이나 | **200~700 ms** | 20~120 ms |
| solid 7z, **창 안** | **0 ms** | 20~120 ms |
| solid 7z, **창 밖** | **1,443~2,067 ms**(패스 한 번) | 20~120 ms |

**2단계가 여기까지 미뤄 둔 'solid 7z 페이지 전환 지연' 이 그 마지막 줄이다.** 값이 큰
이유는 solid 의 정의 그대로다 — 15쪽을 꺼내려면 앞의 14쪽을 전부 풀어야 한다(66 MB
아카이브에서 ≈26 MB/s). **창 안에서는 0 ms** 이므로 실제로 걸리는 것은 창을 벗어나는
순간뿐이고, **앞뒤로 22번 넘기는 동안 패스는 2번**만 돌았다.

| 그 밖 | 값 |
|---|---|
| 작은 7z(12쪽·124 KB) 첫 패스 | **114 ms** — 12쪽이 한 창에 다 들어온다 |
| 웹툰 띠 하나(800×12000 → 800×1777) | **8~43 ms** |
| 만화 표지 썸네일 | 300×450 JPEG **2.2 KB** |
| 메모리(30쪽 책을 앞뒤로 훑는 동안) | Java 힙 39~45 MB, 네이티브 힙 84~88 MB. OOM·FATAL 0 |

**화면에서 확인한 것**(전부 릴리스 R8 빌드에서 다시 확인했다).

| 확인한 것 | 결과 |
|---|---|
| `__MACOSX`·`ComicInfo.xml` 이 든 12쪽 CBZ | **12쪽**으로 잡힌다(14개가 아니다) |
| 왼→오 밀기 | 1→2→3→4쪽. 색이 차례대로 |
| **오른쪽에서 왼쪽** | 왼쪽으로 밀면 **뒤로**, 오른쪽으로 밀면 앞으로. 10단계가 한 번 뺐다가 되살리면서 **다시 쟀다**(아래 10단계 실측표) |
| 더블탭 | 레터박스(y 390~426)가 사라진다 = 확대 |
| **확대한 채 밀기** | 쪽이 넘어가지 않는다. 다시 더블탭하면 캡처가 **픽셀 단위로 동일** |
| 쪽으로 가기 9 | 9쪽의 색 |
| 앱 강제 종료 후 다시 열기 | `9쪽부터 이어서 봅니다` + `처음부터` |
| `처음부터` | 1쪽, 색도 1쪽 |
| 장별 폴더(`ch01/`·`ch02/`) | 12쪽으로 이어진다 |
| `page1`…`page12` | 자연 정렬(`page3` 이 셋째) |
| 잘린 PNG 한 장 | 그 쪽만 `이 쪽을 열 수 없습니다`. 나머지는 그대로 |
| 그림이 없는 CBZ | `그림이 한 장도 없습니다` |
| 폴더 → 더보기 → `이 폴더를 만화로 보기` | 12쪽 |
| **웹툰(800×12000) 세로 스크롤** | 띠로 그린다. 밀면 1→2→3쪽 |
| 웹툰을 **쪽 모드**로 보면 | 화면 가운데가 검다 — 통짜를 화면에 맞춘 결과라 세로 띠가 된다 |
| **움직이는 GIF 쪽** | 캡처 여섯 장이 **네 색을 돌며 바뀐다** = 실제로 돈다 |
| 일반 `.zip` 의 그림 항목 탭 | 만화 뷰어가 **그 쪽에서** 열린다(4장 중 3번째) |
| 일반 `.zip` 의 글 항목 탭 | `이 항목을 열 수 없습니다` |
| 목록의 만화 표지 | cbz·cb7 열 개에 표지가 생긴다. 그림 없는 책은 종류 배지 그대로 |
| 가로로 돌리기 | 쪽·색 그대로. 예산은 화소 수가 같아 다시 계산하지 않는다 |

### 9단계가 세운 것과, 실측이 잡은 것

**쪽은 디스크에 쓰지 않는다.** 설계 검토에서 가장 크게 갈린 것이 '쪽을 캐시 폴더에 뽑을
것인가' 였고, 뽑지 않기로 했다. 썸네일을 `filesDir` 에 둔 그 논리가 여기서는 **반대
방향으로** 작용하기 때문이다 — 썸네일은 320px 파생물이지만 만화 쪽은 **원본 그대로**라,
200쪽짜리 열 권을 보면 사용자의 만화가 통째로 앱 저장소에 평문 복제된다. 숨긴 폴더의
책도 예외가 아니고 프로세스가 죽으면 청소할 사람도 없다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 쪽을 **힙에만** 둔다 | 위 문단. `ImageIo.decodeFitted(bytes, …)` 가 이미 같은 판단을 코드에 적어 두었다 |
| solid 는 **창**으로 든다 | 300쪽을 전부 힙에 올리면 수백 MB다. 창은 **실제로 받은 바이트**로 닫는다 — 헤더의 선언 크기는 공격자가 적는 값이다 |
| 표본을 **바이트**로 자른다 | `sampleFor` 가 2의 거듭제곱이라 4000×3000 한 장이 45.8 MiB(= 예산의 4.6배)가 된다. 사진 한 장이면 살지만 **만화는 그런 장을 넷 든다.** `sampleForBudget` 이 흐려지더라도 바이트를 먼저 지킨다 |
| 웹툰은 **띠**로 그린다 | 800×12000 을 폭에 맞추면 한 장이 55 MiB다. 흐리게 만들어 풀 문제가 아니다 — 웹툰은 **글자를 읽는 그림**이다 |
| 방향은 `reverseLayout` 하나로 | 목록을 뒤집으면 쪽 번호가 자리와 어긋나 이어보기가 다른 쪽을 가리킨다. 방향은 **보이는 순서**의 문제다. 뒤집으면 아래 막대의 슬라이더도 함께 뒤집는다 |
| 표지는 **만화 확장자만** | `ARCHIVE` 전체로 넓히면 사용자가 **연 적도 없는** 압축 파일 속 개인 사진이 320px JPEG 으로 앱 저장소에 영속된다 |

**시험은 이렇게 나눴다.** `ComicSource` 는 `android.*` 를 하나도 쓰지 않아
(디코딩은 `ComicPageStore` 가 한다) **창·엔트리 번호·예산이 전부 JVM 시험 16건**으로
돈다. 플랫폼 디코더가 필요한 것만 계측 7건이다(예산이 표본을 실제로 키우는가, 띠가 제
자리를 뜨는가, 캐시가 도는가, 깨진 쪽만 실패하는가). **Room 을 왕복하는 이어보기·읽는
방향 3건이 감사 뒤에 더해져 `feature:comic` 의 계측은 10건이다.** 9단계 끝에 저장소
전체로 JVM 228건·계측 45건이었고, **10단계가 JVM 을 309건으로 늘렸다**(폴더 큐·제스처·
자막 이름·섞기 이력·날짜 묶기, 그리고 잔여 작업의 배속 눈금·A-B 구간·트랙 조각·PiP 계산).
계측은 45건 그대로였다 — 새로 더한 것이 전부 순수 계산이라 에뮬레이터가 답할 것이 없었다.
**11단계가 JVM 을 394건, 계측을 62건으로 늘렸다.** PDF 절반이 JVM 335·계측 55
(쪽 크기 계산 `PdfLimits` 11건, 확대 자리와 실패 매핑 `DocDetail`·`PdfFailures` 15건,
pdfium 이 있어야 답하는 것 10건), EPUB 절반이 JVM 을 394 로(위생 `HtmlSanitizer`·`Css`·
`HtmlShell` 30건, 컨테이너·주소·암호화 `EpubOpener`·`EpubHref`·`EpubEncryption` 29건),
계측을 62 로(`core:webhost` 7건) 늘렸다.

**EPUB 쪽이 계측을 거의 늘리지 않은 것이 모듈을 가른 값이다** — ZIP·XML·문자열은
에뮬레이터가 답할 것이 없다.

**11단계 뒤의 암호 PDF·원본 2배 확대가 JVM 을 427건, 계측을 70건으로 늘렸다.** 427 은
순수 JVM 모듈 271건과 안드로이드 모듈의 단위 시험(`testDebugUnitTest`) 156건의 합이다 —
앞의 394 와 같은 셈법이다. 늘어난 33건은 복호화기 19(`PdfDecryptorTest`), 래스터 선명화 9
(`RasterDetailTest`), 확대 계산 3(`ZoomMathTest`), PDF 상한 2(`PdfLimitsTest`)다.

**글꼴 난독화·아카이브 암호가 JVM 을 475건(306 + 169), 계측을 74건으로 늘렸다.** 순수 JVM 의
35건은 EPUB 17(`FontObfuscationTest` 7, 암호화 판정·여는이 10)과 아카이브 18(`ZipDecryptionTest` 6,
`ArchivePasswordTest` 12 — 그중 5건이 구현 뒤 검토의 회귀)이고, 안드로이드 모듈의 13건은 만화 6
(`ComicPasswordTest`)과 복호화기 검토 회귀 7(`PdfDecryptorTest` 19 → 26)이다.

**12단계가 JVM 을 776건(607 + 169)으로 늘렸다. 계측은 74건 그대로다.** 늘어난 301건이 전부 순수 JVM 이다 —
docx 57, xlsx 89, pptx 73, CFB 25, `format:opc` 57(패키지·바탕·`HtmlWriter` 상한 14, `OfficeCfb`·MS-OFFCRYPTO 43).
흐름 문서의 화면은 EPUB 과 같은 WebView 라
에뮬레이터가 따로 답할 것이 없었다 — 대신 두 기기에서 릴리스로 손수 훑었다(아래 12단계 실측).

**13단계가 JVM 을 945건(776 + 169)으로 늘렸다. 계측은 74건 그대로다.** 늘어난 169건이 전부 순수 JVM 이다 —
HWPX 87(구조·내용·암호·견고함·실물), HWP 5.0 82(레코드·스트림·여는이·내용·구조·실물·검토 회귀). 실물 시험은
`samples-local/hwp/` 가 있을 때만 돌고 없으면 건너뛴다.

**13단계 뒤의 말뭉치 확인·검토·짝 대조가 JVM 을 1,047건(873 + 174), 계측을 75건으로 늘렸다**(2026-09-27). 순수 JVM 의
873건 가운데 HWPX 116·HWP 5.0 100(짝 대조 셋째 검토의 회귀가 대부분), `format:html` 57(위생·껍데기 44 — 말뭉치·보안 검토의 회귀 포함 — 와 한글 공용 규칙 13),
OOXML·CFB 다섯 모듈 318, EPUB 52(말뭉치 2 포함)다. 안드로이드 모듈의 174건에는 `app` 의 `HancomPairTest` 1건(짝 표본이 있어야 돈다)과
docview 의 `PdfCorpusTest` 2건이 들었다. 계측의 1건은 기기에서 도는 PDF 말뭉치(`PdfCorpusDeviceTest`)다.

**14단계가 JVM 을 1,533건(1,054 + 479), 계측을 89건으로 늘렸다**(2026-09-28). 순수 JVM 의 1,054건 가운데 늘어난 것은 `format:archive`
150(tar·인코딩 회귀·진행률), `format:text` 93(마크다운 변환기와 적대 입력), `core:charset` 39, 변환기 일곱 모듈의 메모·SmartArt·
설명문·옛 표본 정리다. 안드로이드 모듈의 479건은 `core:ui` 91(끌어 닫기·애니메이션 계획·부정 캐시·방향), `feature:comic` 90(두 쪽·
쪽 목록·다음 권·`.cbt`), `feature:docview` 80(읽기 모양·장 안 위치·두 쪽·찾기), `core:playback` 73, `core:io` 27·`feature:browser` 26
(폴더 감시·휴지통 예약·배지), `core:data` 25(깨진 설정·크래시 기록), `feature:text` 21, `feature:settings` 13, `core:webhost` 12(**첫
JVM 시험** — 읽던 자리의 계산 `ReadingScroll`), `app` 11(화면 전이·고지), `feature:archive` 10 이다. `feature:text` 의 1건은 윈도에서
심볼릭 링크를 만들 권한이 없어 건너뛴다.

**14단계 뒤의 '다른 앱으로 열기' 가 JVM 을 1,672건(1,057 + 615)으로 늘렸다. 계측은 89건 그대로다**(2026-09-28). 순수 JVM 의 셋은
`core:model` 의 `PathNames`. 안드로이드 모듈의 136건은 `core:io` 36(MIME 차례·계열·표 점검·URI 루트·고르는 창 판단·여는 탐침),
`feature:browser` 23(누름의 갈래·시트·선택·가려진 목록), `feature:docview` 15, `feature:comic` 18(못 연 쪽·여는 사이에 없어진 책·
제목·다음 권의 탐침), `feature:player` 10·`feature:image` 7(두 모듈의 **첫 JVM 시험**), `feature:text` 7, `feature:archive` 12,
`core:playback` 8(실패가 항목에 매인다·컨테이너 실패의 두 갈래)이다.

**실측이 잡은 결함 넷.**

1. **책을 바꿔도 앞 책의 상태가 남았다.** `ComicViewModel` 은 액티비티에 묶여 있어 책을
   바꿔도 같은 객체다. 이어보기 기록이 없는 책을 열면 `_page` 와 `_direction` 이
   되돌려지지 않아, **세로 스크롤로 보던 웹툰을 닫고 연 만화가 세로 모드로 열렸다**
   (좌우로 밀어도 넘어가지 않는다). 화면 캡처의 세로 프로필이 레터박스 없이 폭을 꽉
   채우는 것으로 잡았다 — 목록의 글자로는 보이지 않는 결함이다.
2. **창이 요청한 쪽에서 시작해 패스가 두 번 돌았다.** 페이저가 앞뒤 한 장씩을 함께
   띄우므로, 창을 요청한 쪽에서 정확히 시작하면 **바로 다음 요청(이전 쪽)이 반드시
   창 밖**이 된다. 15쪽으로 뛰는 데 1,683 ms + 1,021 ms 가 들었다. 창을 한 쪽 앞에서
   시작하게 고쳤고(`LOOK_BEHIND`), 되돌아가는 중이면 반 창만큼 더 앞에서 시작한다.
3. **패스마다 예산을 새로 만들지 않으면 멀쩡한 만화가 거절된다.** `EntryBudget.entryCount`
   는 `resetOutput` 이 되돌리지 않는 **단조 증가** 값이고 리더 생성자가 엔트리마다
   `beginEntry()` 를 부른다. 300쪽짜리를 40번 다시 열면 12,000 > 10,000 이다. 화면에서는
   '갑자기 쪽이 안 열린다' 로 보이고 다시 열면 멀쩡해 재현이 어렵다 —
   **JVM 시험으로 박았고, 예산을 공유하도록 되돌려 시험이 실제로 실패하는 것을 확인했다.**

4. **이어보기가 화면의 초기값에 덮어써졌다.** 페이저와 VM 을 양방향으로 이으면
   — 페이저가 민 결과를 VM 에 보고하고, VM 의 쪽으로 페이저를 옮긴다 — 페이저가 0쪽으로
   서는 **첫 순간의 값이 곧바로 VM 으로 되돌아온다.** 그 사이에 DB 읽기가 끝나 있으면
   방금 되살린 쪽이 0으로 지워지고, 화면은 **"5쪽부터 이어서 봅니다" 를 띄워 놓고 1쪽을
   보여 준다.** 이기고 지는 것이 DB 읽기와 첫 컴포지션의 경주라 재현이 들쭉날쭉하다.
   고친 방법은 **되살린 뒤에 `Ready` 를 알리는 것**이다 — 페이저가 처음부터 옳은 쪽에
   서므로 경주 자체가 사라진다. 동기화 깃발을 세우는 길도 있었지만 그것은 경주를 남겨
   둔 채 한쪽을 늦추는 일이다.

**7z 리더의 드레인 루프에도 try 가 없었다.** 건너뛰는 엔트리가 폭탄이면
`maxSingleOutput` 이 거기서 터지는데, 그것은 그 항목 하나의 상한이지 아카이브를 무효로
만드는 상한이 아니다. 만화 뷰어는 창 밖의 쪽을 전부 그 길로 지나므로 **고르지도 않은
엔트리 하나가 패스 전체를 죽일** 수 있었다.

### 9단계가 끝난 뒤의 감사 — 문서가 코드와 어긋난 곳을 찾는다

단계가 끝난 다음 **코드가 아니라 이 문서를 대조하는** 검토를 한 번 돌렸다. 다섯 관점
(문서 대 코드·공개 위험·9단계 회귀·미룬 것 원장·10단계 준비도)으로 34건을 찾고 발견마다
세 렌즈(코드를 직접 읽어라·의도인지 결함인지 가려라·재현 경로를 구성해라)로 반박하게 해서
**15건이 살아남고 19건이 죽었다.** 반박이 죽인 것에는 'cbz 가 압축 화면에 못 간다',
'`SolidComicSource.close` 가 진행 중인 패스와 경쟁한다' 처럼 그럴듯한 것이 여럿 있었다.

**이 검토가 값을 한 이유는 대상이 코드가 아니라 문서였기 때문이다.** 시험도 lint 도
릴리스 스모크도 전부 통과하는 상태에서, 문서가 "이미 했다"고 적어 둔 것 두 가지가
사실이 아니었다.

| 무엇 | 어떻게 어긋나 있었나 |
|---|---|
| 6단계 '애니메이션' 행 | "만화 뷰어와 **이미지 뷰어**가 같은 페인터를 쓴다" — 이미지 뷰어에는 그 경로가 아예 없다. 취소선으로 지운 항목이라 아무도 다시 보지 않는다 |
| 계측 시험 명령 | `:core:io` 하나만 적혀 있어 **8·9단계가 박은 17건이 한 번도 돌지 않았다** |
| 의존 표 | `core:playback` 이 표가 금지한 셋을 5단계부터 쓰고 있었다 |
| `maxContainerDepth` | '재귀 폭탄이 이 한 줄로 끝난다' 고 적었는데 읽는 코드가 없다 |
| `core:charset` 공유 | 표는 고쳤지만 코드는 옮기지 않아 판정이 두 벌이다 |
| 예산표 `detailCap` | 같은 표의 입력으로 코드를 돌리면 8,859,648 인데 6,553,600 으로 적혀 있었다 |
| 7단계 '찾기' 행 | 진행률과 취소가 **서로 바뀌어** 적혀 있었다 |
| 빌드 절의 `JAVA_HOME` | 기계 사용자 이름이 박힌 절대경로. 같은 파일 17번째 줄이 스스로 금지한 것이고, **커밋하면 웹에 공개된다** |

**코드 결함 넷도 함께 나왔다.**

1. **일반 압축으로 묶은 만화가 읽는 방향을 기억하지 못했다.** 압축 목록에서 그림 항목을
   탭해 들어오는 길이 `key` 만 채우고 `restore` 를 건너뛰어, 방향이 기본값(LTR)으로
   열리고 나갈 때 그 기본값이 DB 에 덮어써졌다. cbz·cbr 은 목록에서 곧바로 뷰어로 가므로
   닿지 않지만, **일반 zip 으로 묶은 만화는 이 길이 유일한 입구**라 오른쪽에서 왼쪽을
   골라도 다음에 열면 매번 왼쪽에서 오른쪽이었다. 화면에는 아무 오류도 나지 않는다.
   `ComicViewModelTest` 로 박았고, 고침을 되돌려 시험이 실제로 실패하는 것을 확인했다.
2. **압축 안의 `.cbz` 가 '열 수 없음' 으로 끝났다.** 9단계가 만화 확장자를 `ARCHIVE` 에서
   `COMIC` 으로 옮기면서 중첩 안내의 검사가 그 넷을 놓쳤다. 권별 cbz 를 zip 하나에 모아
   둔 흔한 구성에서 사용자는 파일이 깨진 줄로 읽는다.
3. **취소가 열린 리더를 잃어버리는 창이 있었다.** `ComicOpen.open` 이 만든 리더는
   `withTimeout` → `withContext` 두 겹을 거쳐 돌아오는데, 그 사이에 취소되면 코루틴이
   값을 버린다 — 호출자는 받은 적이 없으니 닫을 손잡이가 없다. **값을 돌려주는 것과
   소유권을 넘기는 것을 갈랐다**(`onOpen` 콜백 + 호출자의 `finally`).
4. **재생 화면이 실패를 말하지 않았다.** 실패 문구 여덟 개가 `core:playback` 에 있는데
   읽는 코드는 `feature:browser` 에만 있었고, 거기에는 같은 문구가 `browser_playback_*`
   이라는 이름으로 **한 벌 더** 있었다. 그래서 재생 화면에 들어와 있는 동안 디코더가
   죽으면 검은 화면만 남았다. 문구를 `PlaybackText.kt` 한 곳으로 모으고 재생 화면이
   그것을 읽게 했다.

**고친 것을 다시 검토하게 했더니 넷이 더 나왔다.** 수정 자체가 결함을 남긴 것이 하나,
수정하면서 문서에 새로 적은 것이 틀린 것이 하나, 고침이 닿지 않은 옛 주석이 둘이다.

- `restore` 의 DB 읽기가 `runCatching` 이라 **취소를 삼켰다.** 그 아래로 정지 지점이
  없어 취소된 잡이 끝까지 달리고, `close()` 뒤에 이미 닫힌 소스를 안은 `Ready` 를 다시
  세운다. **엔트리 경로가 이제 이 DB 읽기를 지나므로 고침이 창을 넓혔다** — 같은 함수를
  고치면서 그 안의 옛 `runCatching` 을 보지 못한 것이다(위 코드 규칙에 넣었다).
- 감사가 "`core:charset` 을 부르는 것은 `format:text` 하나" 라고 적었는데 **거꾸로다** —
  판정을 부르는 것은 `feature:text` 이고 `format:text` 는 값 타입과 디코더만 쓴다.
  문서를 고치면서 확인하지 않은 사실을 새로 넣은 셈이다.
- `PlayerActivity` 의 KDoc 이 **CLAUDE.md 가 폐기한 이유**(targetSdk 36)를 그대로 들고
  있었다. 10단계에서 그 파일을 먼저 여는 사람이 바로 그 오해를 집는다.
- 모듈 지도와 `FormatId` 주석이 **없는 `app/FormatRegistry.kt`** 를 현재형으로 가리켰다.

- 고침을 검증하려고 `./gradlew test --rerun-tasks` 를 돌리자 `SequentialCostTest` 가
  또 깨졌다. 9단계가 표본을 키워 고쳤다고 적어 둔 그 시험이다 — 이번 원인은 다른
  것이었고(병렬 테스트 워커, 위 8단계 실측표) 두 측정을 **번갈아 재는** 것으로 고쳤다.

**교훈은 감사 자신에게도 적용된다** — 문서를 고치는 손도 근거를 코드에서 다시 읽어야
한다. 그리고 **결함을 고칠 때는 그 함수 전체를 읽어라**: 한 줄 아래에 같은 종류의
결함이 앉아 있었다.

### 10단계가 세운 것

**설계를 다섯 조각(모듈 이사·VLC 차용·자막·PiP·제스처)으로 짜고 각각을 두 렌즈
(데이터 손실·자기규칙)로 반증하게 했다. 반증 78건(치명 2).** 치명 하나가 9단계와 같은
형태였다 — '이어보기 기록을 **읽은 적 없는 값으로 덮어쓴다**'. 설계는 큐를 만들 때
`recent(500)` 으로 이어보기 지도를 뜨려 했는데, 기록이 500개를 넘으면 그 바깥의 항목이
조용히 0초로 되돌아간다.

**고친 방법은 기능을 좁히는 쪽이었다** — 큐가 저절로 넘어갈 때는 **이어보기를 아예 걸지
않는다.** 사용자가 직접 고른 시작 항목만 이어서 튼다(**설계 당시의 이야기다.** 그 뒤
사용자 지적으로 시작 항목까지 0초 고정이 되고 저장된 자리는 묻기만 한다 — 아래
'이어보기를 … 뒤집었다' 문단). 그러면 읽을 것도 쓸 것도 없어
그 결함이 성립할 자리가 사라지고, '중간부터 나오는데 무를 수 없다' 는 문제도 함께 없어진다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 큐 고르기를 **순수 함수로**(`FolderQueue`) | `android.*` 를 쓰지 않아 창·상한·시작 자리가 JVM 시험으로 박힌다. 9단계가 `ComicSource` 를 같은 이유로 갈랐다 |
| 상한을 넘으면 **누른 항목을 가운데 두고** 자른다 | 앞에서 자르면 폴더 뒤쪽 파일을 눌렀을 때 그것이 큐에서 빠지고 **다른 파일이 재생된다**. 8단계가 치명으로 고친 형태다 |
| ~~섞기를 **우리가 하지 않는다**~~ | **뒤집혔다 — 아래 '섞기를 우리가 뽑는다' 가 지금의 동작이다.** 처음 판단은 media3 의 `shuffleModeEnabled` 가 순서만 바꾸고 인덱스를 남기니 그것을 쓰자는 것이었는데, 그것은 '한 번 섞어 둔 차례' 라 다시 눌러도 같은 곡이 나왔다 |
| 자막 인코딩을 **바이트가 들어가는 길에서** 바꾼다 | 아래 문단 |
| 아이콘 아홉을 **직접 그린다**(`PlaybackIcons`) | 코어 세트 49개(aar 를 풀어 셌다)에 `SkipNext`·`Shuffle`·`Repeat`·재생목록·음표·캠코더가 하나도 없다. 5단계가 `Pause` 를 손으로 그린 그 이유다 |
| **재생목록을 화면 본체로 둔다**(`PlaylistPanel`) | 아래 문단 |
| 재생목록에 **길이를 적지 않는다** | 500개의 길이를 알려면 500번 열어야 한다. '목록을 그리려고 파일을 열지 않는다'(`FileKind` 주석)가 이 앱에서 가장 여러 번 값을 한 규칙이다. 길이는 그 곡을 틀면 아래 막대에 나온다 |
| 영상은 **비율 그대로** 그린다 | `fillMaxSize` 로 두면 표면이 화면을 채우느라 영상을 늘인다. 화소가 정사각형이 아닌 영상이 있으므로 `width/height` 가 아니라 `pixelWidthHeightRatio` 를 곱한 값을 쓴다 |
| 섞기를 **우리가 뽑는다**(`ShuffleHistory`) | media3 의 `shuffleModeEnabled` 는 '한 번 섞어 둔 차례' 라 같은 자리에서 다시 눌러도 늘 같은 곡이 나온다. 누를 때마다 새로 뽑되 이력으로 겹침을 막는다. 이력은 **메모리에만** 둔다 — 무엇을 들었는지는 사적인 값이다 |
| 제스처 계산을 **순수 함수로**(`GestureMath`) | 6단계가 제스처를 캡처로 판정하려다 세 번 틀렸다. 계산은 JVM 시험이 답하고, 기기에서는 `Iro.d` 로 연결만 본다 |
| 제스처 인식기를 **하나만** 둔다 | 형제 `pointerInput` 의 순서에 기대지 않는다(함정 표). `awaitEachGesture` 안에서 두드림인지 끌기인지, 끌기면 어느 축인지를 **한 번 정하고 바꾸지 않는다** — 비스듬히 끌 때 탐색과 소리가 번갈아 바뀌는 일이 이 규칙 하나로 사라진다 |
| 조작부를 **칸이 아니라 위에 겹친다** | 칸으로 두면 조작부가 나타날 때마다 위 칸이 좁아져 **영상이 줄어들고 자리가 움직인다.** 눈에는 "화면이 흔들린다" 로 보인다. 겹치면 바뀌는 것은 조작부의 유무 하나뿐이다 |
| 조작부 아래에 **그라데이션 판**을 깐다 | 영상 위에 흰 글자만 얹으면 밝은 장면에서 글자와 단추가 사라진다. 위로 갈수록 옅어져 영상을 통째로 가리지도 않는다 |
| 자막을 **영상 상자 안**에 넣는다 | 밖에 두면 세로 영상 아래의 빈 여백에 자막이 떠서 영상과 글이 멀어진다 |
| 첫 두드림의 토글을 **미룬다** | 바로 토글하면 두 번 두드려 30초를 건너뛸 때마다 조작부가 한 번 깜빡인다. 320ms 안에 두 번째가 오면 미뤄 둔 토글을 취소한다 |
| 두드림 안내를 **900ms 뒤 지운다** | 끌기는 손을 떼면 사라지지만 두드림에는 끝이 없어, 지우는 사람이 없으면 화면에 남는다 |
| **영상이 보일 때만** 어둡게 못 박는다 | `hasVideo && !playlistOpen`. 목록이 열려 있으면 영상은 가려져 있으므로 그때는 OS 테마를 따른다 — 시스템이 밝은 모드인데 이 화면만 검으면 그것이 오히려 튄다. **액티비티의 테마와 조작부의 색이 같은 식을 써야 한다** — 어긋나면 목록은 밝은데 조작부만 어두운 그림이 나온다 |
| 밝기는 **창 속성만** 바꾼다 | `Settings.System` 에 쓰면 앱을 나가도 기기 밝기가 바뀐 채로 남고 `WRITE_SETTINGS` 권한까지 필요하다. 소리는 반대로 **시스템 음량**을 바꾼다 — 재생 화면에서 줄인 것이 볼륨 키와 같은 자리여야 한다 |
| 날짜 묶기를 **순수 함수로**(`DayBucket`) | 시간대·서머타임·해 경계는 화면 캡처로 확인할 수 있는 것이 아니다. 시간대를 인자로 받으므로 서울의 9월도 상파울루의 11월(자정이 없는 날)도 같은 시험에서 답이 난다 |
| 날짜 구간을 **자리 번호로** 들고 있는다 | 항목을 날짜별 목록으로 다시 담으면 화면이 누른 칸의 **원래 자리**를 잃어 다른 사진이 열린다. 8단계가 아카이브 엔트리를 이름이 아니라 인덱스로 가리키기로 한 것과 같은 판단이다 |
| 구간을 **훑기 쪽에서** 만든다 | `GalleryScanner.scan` 은 `IroDispatchers.io` 위에서 돌고 화면은 주 스레드다. 1만 장의 시각을 주 스레드에서 날짜로 바꾸면 격자가 그려지기 전에 한 번 멎는다 |
| 기본 볼륨의 이름만 **우리가 붙인다** | `StorageVolume.getDescription` 이 주는 `Internal shared storage` 는 한국어 화면에서 그 한 줄만 영어로 길다. SD 카드는 그대로 둔다 — 그 이름은 기기가 아는 것이고 우리가 더 잘 지을 수 없다 |
| 이음매에 **인텐트를 주는 함수**를 더한다 | 알림은 '지금 열어라' 가 아니라 `PendingIntent` 를 미리 받아 둔다. 그래서 `PlayerLauncher` 가 `open` 하나짜리 `fun interface` 를 그만두고 `intent(context, showPlaylist)` 를 더 갖는다. 인텐트를 조립하는 일과 `launchMode="singleTask"` 선언은 여전히 한 몸이라 둘 다 `feature:player` 에 남는다 |
| 큐가 없으면 목록을 **'열린' 것으로 치지 않는다** | 한 곡짜리에서는 그릴 목록이 없어 `PlaylistPanel` 이 안 그려지는데, 그래도 '열림' 으로 치면 테마만 밝아져 **영상이 흰 바탕 위에 뜬다.** 알림이 '목록을 펼쳐라' 를 싣고 오면서 실제로 닿을 수 있는 자리가 됐다 |
| 목록의 여닫음은 **사용자의 뜻이 이긴다** | `hasVideo` 를 `remember` 의 키로 쓰면, 소리와 영상이 섞인 큐에서 곡이 넘어갈 때마다 닫아 둔 목록이 다시 열린다. '손대기 전에는 기본값, 한 번 손대면 그 뜻' 으로 갈랐다 |
| 줄의 **종류를 큐가 정해 실어 보낸다** | 화면이 제목의 확장자를 다시 보게 하면 판정이 두 곳으로 갈리고(`EntryNameDecoder` 로 이미 겪었다) 보이는 줄마다 되풀이된다. 큐는 항목이 실제로 바뀔 때만 다시 만들어진다 |
| 진행 바를 **직접 그린다** | Material 3 기본 트랙은 16dp 알약에 끝점 표시가 붙는데, 재생기에는 없는 기호라 '뭔가 더 있다' 로 읽힌다. 관행은 가는 트랙 + 둥근 점이다. 채움은 `Canvas` 가 아니라 `Box` + `fillMaxWidth(fraction)` 로 둔다 — 정렬이 RTL 을 대신 처리한다 |
| 조작을 **두 줄로 가른다** | 위는 '큐에 대한 조작'(목록·섞기·반복·이전/재생/다음), 아래는 '지금 이 재생에 대한 조작'(배속·A-B·트랙·방향 잠금·PiP). 한 줄에 몰면 작은 폰에서 아홉이 넘어 겹치거나 잘린다. 판 높이는 `onSizeChanged` 로 재고 있으므로 줄이 늘어도 위에 뜨는 안내들이 저절로 따라 올라간다 |
| 배속을 **눈금으로** 준다(`SpeedSteps`) | 배속은 되돌아올 수 있어야 하는 값인데 슬라이더로 두면 1.0 배로 정확히 돌아오는 일이 손가락에 달린다. 1.03 배에 걸린 사람은 무엇이 이상한지 모른 채 소리가 미묘하게 틀어진 것만 듣는다. 단추에 **지금 값을 늘 적어** 잊지 않게 한다 |
| 배속 눈금을 **조작부 안에서** 펼친다 | 떠 있는 층으로 두면 가로 영상에서 남는 높이가 절반뿐이라 아래 눈금이 잘린다. 안에 두면 판이 그만큼 높아지고 잰 높이가 나머지를 알아서 민다 |
| A-B 를 **클리핑이 아니라 되감기로** | `MediaItem.ClippingConfiguration` 은 `replaceMediaItem` 으로 걸리지 않고(함정 표), 큐를 다시 세워 걸면 `getCurrentPosition`·`getDuration` 이 **클립 기준**으로 바뀌어 진행 바·시간 표시·`PositionSaver` 가 전부 다른 시간축을 본다. 되감기는 시간축이 하나로 남는다 |
| 짧은 구간을 **거절하고 말한다** | 'A 를 그 자리로 옮겨 준다' 는 길은 옮김의 크기가 정의상 1초 미만이라 **진행 바에서도 시간 문구에서도 보이지 않는다.** 사용자는 자기가 찍은 자리가 왜 달라졌는지 모른다 |
| 구간 이탈을 **위치로** 판정한다 | 알림·잠금화면·블루투스의 탐색은 우리 함수를 지나지 않고 세션으로 바로 들어온다. 호출부에 표시를 다는 방식은 그 길을 통째로 놓쳐, 구간 밖으로 나간 사용자를 티커가 곧바로 끌어온다 |
| 트랙 목록을 **우리 id 로** 내보낸다 | 세션은 컨트롤러로 나가는 `TrackGroup` 마다 매번 새 id 를 매기고 돌아온 오버라이드를 그 표로 되돌린다. 예전 그룹으로 만든 오버라이드는 짝을 못 찾아 **조용히 무시된다** — 오버라이드는 언제나 방금 받은 `getCurrentTracks()` 의 그룹으로 만든다 |
| 트랙 이름을 **조각으로** 넘긴다(`TrackLabels`) | `Format` 은 media3 타입이라 JVM 시험에서 만들 수 없고(스텁), 문구를 계산 쪽에 박으면 5단계의 '문구 두 벌' 이 된다. 조각(`Descriptor`)만 만들고 한 줄로 붙이는 일은 문자열 자원이 한다 |
| 자막 시트를 **바텀시트로** | 조작부 안에 붙이면 가로 영상에서 줄이 넷만 넘어도 아래가 잘리고 스크롤할 자리도 없다. 시트를 `if (!showControls) return@Box` **앞**에 두는 것이 중요하다 — 뒤에 두면 조작부를 감춘 순간 시트가 컴포지션에서 빠져 **되돌릴 수 없는 화면**이 된다 |
| 트랙 선택을 **`mediaId` 가 바뀔 때만** 되돌린다 | 전환 이유로 가르면 한 곡 반복(`REASON_REPEAT`)과 자막 교체(`PLAYLIST_CHANGED`)에서도 돌아, **한 바퀴마다 그리고 자막을 고를 때마다** 사용자가 고른 소리 트랙이 지워진다 |
| PiP 는 **자동과 단추 둘 다** | 자동 진입은 '계속 보려고 나간다' 는 추측이라 `isPlaying` 을 조건에 넣고, 단추는 명시라 넣지 않는다. 목록이 펼쳐져 있으면 둘 다 들어가지 않는다 — 그 상태에서는 표면이 컴포지션에 없어 **영상 없는 창**이 뜬다 |
| PiP 에서 큐가 소리로 넘어가면 **창을 닫는다** | 판정을 `hasVideo` 로 하지 않는다. 그 값은 항목을 건너뛰는 동안 잠깐 거짓이 되므로 PiP 의 '다음' 을 눌러 영상 → 영상으로 넘기기만 해도 창이 사라진다. 큐가 실어 보내는 `QueueItem.kind` 는 확장자로 정해져 **항목이 실제로 바뀔 때만** 달라진다 |
| PiP 진입 **전에** 위층을 접는다 | `onPictureInPictureModeChanged` 는 애니메이션이 끝난 뒤 오고 시작 시점 콜백은 API 35 다. minSdk 31 에서 첫 프레임에 조작부가 찍히는 것을 막을 방법은 우리가 먼저 접는 것뿐이다. 진입이 거절된 길은 `onResume` 이 편다 — **되돌리는 코드가 없으면 조작부도 제스처도 없는 화면이 남는다** |

**재생목록이 보여야 한다 — 사용자가 VLC 를 들어 지적한 것이다.** 처음 만든 큐는 '만들어
놓고 차례로 트는 것' 뿐이었다. 그러면 다음/이전으로만 움직일 수 있고, 18곡짜리 폴더에서
16번째를 들으려면 열다섯 번을 눌러야 한다. VLC 를 비롯한 재생기들이 **재생목록을 화면
본체로 두는** 이유가 그것이다 — 목록이 곧 이동 수단이다.

그래서 재생 화면에 재생목록 층을 얹었다. 아무 줄이나 누르면 그 항목으로 건너뛰고
(`playAt`), 재생 중인 줄은 **막대 아이콘 + 굵은 글씨**로 갈린다(제목은 대개 끝이 잘리므로
굵게만으로는 구분이 안 된다). 목록을 열면 지금 곡이 보이는 자리로 스크롤한다.

**▶ 는 '이 폴더를 튼다' 가 아니라 '이 폴더를 재생목록으로 연다' 로 정의했다** — 누르면
재생 화면이 목록을 펼친 채로 열린다. 그 의도는 이음매(`PlayerLauncher.open(context,
showPlaylist)`)로 실어 보낸다. 파일 하나를 탭해 들어온 길은 false 다(그 사람이 고른 것은
목록이 아니라 그 파일이다). `launchMode="singleTask"` 라 두 번째부터는 `onNewIntent` 로
오므로 `intent` 필드를 그대로 읽으면 **처음 값이 영영 남는다** — 거기서 갱신한다.

**자막 인코딩 — media3 에 구멍이 있다.** `MediaItem.SubtitleConfiguration.Builder` 가 받는
것은 `setUri`·`setMimeType`·`setLanguage`·`setSelectionFlags`·`setRoleFlags`·`setLabel`·
`setId` 뿐이고(javap 로 확인), `SubripParser` 가 쓰는 `ParsableByteArray` 는
`readUtfCharsetFromBom()` — **BOM 이 없으면 UTF-8 로 단정한다.** 한국어 자막은 CP949,
일본어 자막은 Shift_JIS 가 흔하고 둘 다 BOM 이 없다. 7단계가 만들어 둔 `CharsetDetector`
가 답을 아는데 **먹일 자리가 없다.**

그래서 `DataSource` 를 한 겹 감쌌다(`SubtitleSource.kt`) — 자막 확장자면 통째로 읽어
판정하고 UTF-8 로 바꾼 바이트를 대신 내놓는다. 파서는 BOM 없는 UTF-8 을 보므로 아무것도
바꿀 필요가 없다. **통째로 메모리에 올리는 것이 요점이다**: 변환하면 길이가 달라지는데
(CP949 2바이트 → UTF-8 3바이트) media3 는 변환본 기준으로 구간을 물어본다.

### 10단계 실측 (Android 12 에뮬레이터, 릴리스 R8)

| 확인한 것 | 결과 |
|---|---|
| `feature:player` 이사 | 병합 매니페스트의 네 속성이 글자 그대로 유지. 기기에서 `.player.PlayerActivity` 로 뜬다 |
| 자동회전 끈 채 가로 재생 | 재생 화면 `cur=2400x1080`, 나오면 `1080x2400` — 이사 전과 같다 |
| ▶ FAB | '새 폴더' 위에 작은 FAB 으로 쌓인다. 미디어가 없는 폴더에서는 안 뜬다 |
| 폴더 큐 | 6개 폴더에서 `1 / 6`. 다음 두 번에 mkv → mp4 → flac |
| 섞기·반복 | 켜면 아이콘이 강조색으로 바뀐다. 큐가 하나면 조작이 아예 안 보인다 |
| **재생목록에서 임의 순번** | 4번째 줄을 눌러 `시험음.m4a`(`4 / 6`), 6번째를 눌러 `시험음.wav`(`6 / 6`) |
| 재생목록 토글 | 영상 위에서는 완전히 덮고(비치면 글씨가 사라진다) 상태 표시줄을 피한다. 목록이 열리면 자막을 그리지 않는다 |
| 조작부 여닫기 | 영상의 세로 위치가 **정확히 같다**(y 748 고정). 조작부만 나타나고 사라진다 |
| 테마 | 목록 화면의 여백이 `(246,250,255)`, 영상 화면이 `(0,0,0)` — 같은 밝은 모드에서 |
| 제스처 여섯 | `logcat` 으로 판정. `제스처 시작 SEEK/BRIGHTNESS/VOLUME`, `탐색 끌기 4215ms → 53923ms`(폭의 절반 이상을 끌어 +50초), `두드림 1회 — 조작부 토글`, `두드림 2·3회 side=RIGHT` |
| 영상 비율 | 가로 영상이 세로 화면에서 레터박스로 들어간다. 고치기 전에는 위아래로 잡아당겨져 있었다 |
| 영상 탭 | 재생 화면이 곧바로 열리고 큐가 그 항목에서 시작한다 |
| **CP949 자막** | `시험영상.srt`(CP949)가 이름으로 붙고 **`자막3 노랑` 이 깨지지 않고 뜬다** |
| 손으로 만든 mkv | ffmpeg 없이 쓴 Matroska(PCM + S_TEXT/UTF8)가 `state=3` 으로 재생된다 |
| 볼륨 이름 | 첫 화면이 `내부 저장소` 로 뜬다(`Internal shared storage` 가 아니다). SD 는 `SDCARD` 그대로 |
| ⋮ 메뉴 자리 | 메뉴가 ⋮ 아래에서 시작해 오른쪽 끝에 맞는다. 고치기 전에는 맨 왼쪽 아이콘 아래였다 |
| **갤러리 날짜 구분** | 사진 8장이 `오늘 2` · `어제 3` · `9월 10일 2` · `2025년 12월 31일 1` 로 갈린다. `touch -t` 로 수정시각을 흩어 만든 표본이다 |
| **날짜 구간의 자리** | 마지막 구간의 사진을 눌러 `사진4.png` · `8 / 8`, 첫 구간의 둘째를 눌러 `가로사진.jpg` · `2 / 9` — **구간 안의 자리가 원래 목록의 자리로 되돌려진다** |
| 사진 삭제 → 갤러리 복귀 | `갤러리 8장` → **`갤러리 7장`**. 구간이 통째로 사라지고 빈 칸이 남지 않는다 |
| 휴지통 되돌리기 → 갤러리 | `갤러리 9장`, 썸네일 전부. 구간도 되돌아온다 |
| 만화 방향(왼→오) | 왼쪽으로 두 번 밀어 4 → 5 → 6쪽 |
| **만화 방향(오른→왼)** | 읽기 설정 세 갈래가 모두 있고 **새 책의 기본은 왼쪽에서 오른쪽**이다. 오른쪽에서 왼쪽을 고르면 손가락을 **오른→왼으로 밀 때 6 → 5쪽(뒤로)**, 왼→오로 밀 때 5 → 6 → 7쪽(앞으로). 7쪽에서 슬라이더 손잡이가 왼쪽에서 46% 자리 — 뒤집히지 않았다면 55% 다 |
| 런처 아이콘 크기 | 앱 서랍에서 구글 Files 와 나란히 재어 **폭 60.5% · 높이 48.4% · 원 대비 면적 33.8%**(구글 58.6% · 49.7% · 33.9%) |
| ⋮ 메뉴 자리 | 메뉴가 ⋮ 아래에서 시작해 오른쪽 끝에 맞는다 |
| **이어보기 제안** | 0:32 를 기록해 둔 영상을 다시 틀면 **0:00 에서 시작**하고 `0:32 부터 이어서 재생할까요?` 가 뜬다. 3초 뒤 사라진다(1.2초 캡처에는 있고 4.2초 캡처에는 없다). 단추를 누르면 `position=30791` 로 뛴다 |
| 이어보기 안내의 자리 | 파일 관리자 목록에는 **뜨지 않는다**. 예전에는 거기서 무기한으로 남았다 |
| **알림 누르기** | 홈으로 나간 뒤 미디어 알림 본체를 누르면 `PlayerActivity` 가 **재생목록을 펼친 채** 앞으로 온다 |
| 재생목록 아이콘 | `시험영상.mp4` 에 캠코더, `시험음.*` 에 음표, 재생 중인 줄에 막대 |
| 진행 바 | 트랙 11px(=4dp), 손잡이 29px(≈11dp) 원. 끝점 표시가 없다 |
| **런처 재진입** | 태스크가 `[MainActivity, PlayerActivity]` 인 상태에서 런처 인텐트를 보내면 **`PlayerActivity` 가 그대로 앞으로 온다**. 고치기 전에는 `[MainActivity, PlayerActivity, MainActivity]` 가 되어 첫 화면이 떴다. 릴리스 빌드에서도 같다 |
| **알림 두 번째 누르기** | 폴더 ▶ 로 목록을 펼쳐 들어가 **목록을 닫은 뒤** 홈으로 나가 알림을 누르면 목록이 **다시 펼쳐진다**. 고치기 전에는 닫힌 채로 왔다(같은 값이 두 번 오면 `MutableStateFlow` 가 합쳤다) |
| **긴 재생목록의 끝** | 24곡 폴더에서 목록을 끝까지 내리면 `트랙24.flac` 이 조작부 판 **위에 온전히 보인다.** 6곡 표본으로는 목록이 화면을 못 채워 이 결함이 드러나지 않았다 |
| 조작부 빈 자리 탭 | 목록이 판 밑을 지나는 자리에서 조작부의 빈 곳을 눌러도 `1 / 24` 그대로 — 가려진 줄로 새지 않는다. 같은 상태에서 슬라이더를 끌면 `1:57 / 3:05` 로 정상 이동 |
| **이어보기 알약** | 조작부 판 위에 **온전한 둥근 알약**으로 뜬다. 고치기 전에는 아래 20dp 가 판에 덮여 직선으로 잘려 보였다(판 220dp 인데 상수 200dp 로 비켜세웠다) |
| **소리 파일 탭** | 재생 화면이 **재생목록을 펼친 채** 열린다(`mResumedActivity: .player.PlayerActivity`). 그래서 소리에도 이어보기 제안이 닿는다 — 고치기 전에는 미니 바만 떠서 제안을 볼 자리가 없었다 |
| **정지 중 방향 요청** | 영상을 가로로 보다 홈으로 나가 알림으로 소리 항목까지 넘긴 뒤 알림을 누르면 `cur=1080x2400`(세로). 고치기 전에는 `cur=2400x1080` — **소리 파일인데 화면이 가로로 돌았다** |
| 목록을 덮었다 열기 | 영상 위에서 목록을 열었다 닫으면 1초 안에 영상이 그대로 돌아온다(자막 포함). 표면을 뗐다 붙이는 값이 눈에 띄지 않는다 |
| **배속** | 눈금에서 1.5× 를 고르면 세션이 `speed=1.5` 로 바뀐다(`dumpsys media_session`). 단추 글자도 `1.5×` 로 강조된다. 릴리스 빌드에서도 같다 |
| **A-B 구간** | 43.5초에 A, 51초에 B 를 찍으면 `43490 → 47989 → 43490 …` 으로 되돌아온다(1.5배속). 알약이 `0:43 ~ 0:51 를 되풀이합니다`, 진행 바에 마커 둘 |
| **방향 잠금** | 자동회전을 끈 기기를 `adb emu rotate` 로 가로로 돌려도 잠금이 켜져 있으면 `cur=1080x2400` 그대로. 풀면 그 자리에서 `cur=2400x1080` |
| **트랙 시트** | mkv(소리 2·자막 2)에서 `한국어 더빙 · 모노 · AAC` / `English · 영어 · 모노 · AAC`, 자막에 `한국어`·`English · 영어`·`tracks.ko.srt`, 그 아래 같은 폴더의 자막 파일 둘 |
| **자막 파일 바꾸기** | 이름으로 붙은 `long.srt`(`이름이 맞지 않는…`)를 시트에서 `tracks.ko.srt` 로 바꾸면 **화면 글자가 `한국어 내장 자막입니다` 로 바뀐다.** `replaceMediaItem` 으로 짠 첫 판은 트랙 목록만 바뀌고 글자는 그대로였다 |
| **PiP 진입** | 단추로도 홈 버튼으로도 `mode=pinned`. 창은 `[533,1948][1038,2232]`(505×284 ≈ 16:9) |
| **PiP 비율** | 세로 영상(9:16)에서 창이 `284×504`(0.5635). 초광각 2.76:1 영상은 **예외 없이** 열린다(2.38 로 잘린다) |
| **PiP 조작** | 창을 누르면 이전·일시정지·다음 셋이 뜨고 눌러 동작한다(`state=2` 로 멈춤, `description=portrait.mp4` 로 넘어감). **일시정지하면 가운데 글리프가 삼각형(재생)으로 바뀐다** — 파라미터 갱신이 PiP 중에도 도는 증거다 |
| **PiP 중 항목 전환** | 영상 → 영상은 창이 그대로. **영상 → 소리로 넘어가면 창이 닫히고 재생은 계속된다**(`pinned: 0`, `state=3`) |
| **PiP 창 닫기** | 재생이 멈춘다(`state=2`). 전체화면으로 되돌리는 길과 화면을 껐다 켜는 길에서는 **멈추지 않는다**(각각 위치가 계속 는다) |
| **릴리스 PiP** | R8 빌드에서도 창과 조작 아이콘 셋이 그대로 그려진다(드로어블이 R 참조라 남는다) |
| **자막 형식 이름** | 트랙 줄이 `한국어 · SRT`·`English · 영어 · SRT` 로 뜬다. 고치기 전에는 `MEDIA3-CUES` 였다(원래 MIME 이 `codecs` 에 있다는 것을 몰랐다) |
| **잠근 채 멈췄다 서기** | 세로로 잠그고 화면을 끈 뒤 `adb emu rotate` 로 기기를 가로로 돌렸다가 켜도 `cur=1080x2400`. 잠금을 풀면 그 자리에서 `cur=2400x1080` 이 되어 **센서가 실제로 가로였음**이 확인된다 |

**표본을 ffmpeg 없이 만들었다.** SRT·ASS 는 텍스트라 바로 쓰면 되고, **내장 자막이 든
mkv 는 EBML 을 손으로 썼다** — PCM 오디오(`A_PCM/INT/LIT`)는 인코더가 필요 없고 자막
(`S_TEXT/UTF8`)은 글이라, 둘만 넣으면 인코더 없이 유효한 Matroska 가 된다. 스크래치패드의
`mkvmux.py` 가 그것이고, 저장소에는 넣지 않는다(도구다). 자막은 **조각마다 다른 글자**로
만들어 화면 캡처에서 글을 읽어 판정한다 — 9단계가 쪽을 단색으로 만든 것과 같은 장치다.

### 사용자가 화면을 보고 잡아 준 것 (10단계, 네 차례)

**기능이 도는지는 기계로 확인했는데 화면이 제대로 보이는지는 확인하지 않았다.** 아래 넷은
전부 내가 찍어 둔 캡처에 이미 보이던 것들이고, 캡처를 '동작 확인용' 으로만 읽어 지나쳤다.
**캡처는 동작을 묻는 도구이자 눈으로 검토하는 대상이다** — 둘을 함께 해야 한다.

| 무엇 | 원인 |
|---|---|
| 영상이 늘어나 보인다 | 표면을 `fillMaxSize` 로 둬 화면을 채우느라 비율이 깨졌다 |
| 재생 위치 손잡이가 너무 길다 | Material 3 의 기본 손잡이는 세로로 길고 좌우 여백까지 둔다. 검은 배경 위에서 '잘못 그려진 것' 으로 보인다 |
| 격자/목록 토글이 옆 아이콘보다 크고 모서리가 잘린다 | 글리프를 `fillMaxSize` 로 그려 48dp 터치 영역을 통째로 채웠다 |
| ▶ 와 새 폴더 FAB 크기가 다르다 | 위를 `SmallFloatingActionButton` 으로 뒀다. 둘은 같은 격의 동작이라 격을 나눌 이유가 없다 |
| 한 곡 반복이 전체 반복과 구별되지 않는다 | 24dp 안의 5px 짜리 '1' 은 나란히 놓고 봐도 안 보인다. 가운데를 비우고 숫자를 두 팔 사이에 꽉 채웠다 |
| 조작부가 나타날 때마다 영상이 줄었다 | 조작부를 Column 의 칸으로 두었다. 겹치는 층으로 바꿨다 |
| 조작부 글자가 밝은 장면에서 사라진다 | 판이 없었다. 그라데이션 판을 깔았다 |
| 자막이 세로 영상 **아래** 여백에 떴다 | 자막층이 화면 전체를 기준으로 삼았다. 영상 상자 안으로 넣었다 |
| 두 번 두드릴 때마다 조작부가 깜빡였다 | 첫 두드림이 그 자리에서 토글했다. 320ms 미루고 두 번째가 오면 취소한다 |
| '0:34 (+0:30)' 안내가 안 사라졌다 | 두드림에는 '손을 뗀다' 가 없어 지우는 사람이 없었다 |
| 재생목록·오디오 화면이 OS 설정을 안 따랐다 | 재생 화면 전체를 어둡게 못 박고 있었다. **영상이 실제로 보일 때만** 못 박는다 |
| 앱 이름과 아이콘 | `Files` 로 바꾸고 아이콘에서 재생 삼각형을 뺐다 — 아이콘은 설명이 아니라 찾는 표지라, 익숙한 모양 하나가 섞인 모양보다 낫다 |
| 아이콘이 어둡고 한쪽으로 쏠려 보인다 | 배경을 `#1F2933` → `#4A8BF0` 으로 올리고 폴더를 다시 그렸다. **어댑티브 아이콘은 108×108 화폭 가운데 72×72 만 보인다** — 첫 수정(60×44)은 그 원을 꽉 채워 런처에서 혼자 커 보였다. 지금은 36×28(보이는 원 지름의 49%)이고 중심이 (54.2, 54.0) 이다 |
| ⋮ 메뉴가 동떨어진 자리에 뜬다 | Popup 의 앵커가 아이콘 줄 전체였다(위 함정 표). 네 화면에서 단추와 메뉴를 `Box` 에 함께 담았다 |
| `Internal shared storage` 가 길다 | `StorageVolume.getDescription` 을 그대로 썼다. **기본 볼륨의 이름만** 우리가 붙인다(`내부 저장소`) — SD 카드의 이름은 기기가 아는 것이고 우리가 더 잘 지을 수 없다 |
| 갤러리에 날짜 구분이 없다 | 정렬은 처음부터 새것이 위였는데 경계를 그리지 않았다. 날짜 묶기를 순수 함수(`core:model` 의 `DayBucket`, JVM 시험 8건)로 빼고, 구간을 **훑기 쪽(IO 스레드)에서** 만들어 `Scan.Ready` 에 실었다 |
| ~~만화가 방향을 고를 수 있었다~~ | **내가 잘못 읽었다.** '항상 좌 → 우' 를 '오른쪽에서 왼쪽 선택지를 없애라' 로 읽고 뺐는데, 사용자가 "내가 오해했네요, 복구해주세요 — 처음 열 때 기본값은 LTR" 로 정정했다. 되살렸다. **기본값이 LTR 인 것은 원래부터 그랬다**(`open` 이 첫머리에서 되돌리고 `restore` 가 저장된 값만 얹는다) |
| 이어보기 안내가 파일 목록에 **영원히** 남는다 | 자리와 수명이 둘 다 틀렸다. 아래 문단 |
| 아이콘이 여전히 작다 | 앞 회차에 원 대비 면적 22.4% 로 키웠는데 구글 Files 는 33.9% 였다. 면적을 맞추는 배율 1.23 로 다시 키워 33.8% 가 됐다 — **두 아이콘을 같은 화면에서 찍어 픽셀로 쟀다** |
| 아이콘을 누르면 늘 첫 화면 | 런처 인텐트가 하던 화면 위에 `MainActivity` 를 하나 더 쌓고 있었다(위 함정 표) |
| 알림을 눌러도 아무 일이 없다 | 세션에 `setSessionActivity` 가 없었다. 이음매(`PlayerLauncher`)에 **인텐트를 주는 함수**를 더해 꽂았다 — 알림은 '지금 열어라' 가 아니라 `PendingIntent` 를 미리 받아 두기 때문이다 |
| 재생 막대가 익숙한 형태가 아니다 | 앞 회차의 얇은 알약은 시인성만 고친 것이었다. 재생기의 관행은 **가는 트랙 + 둥근 점**이고 끝점 표시가 없다. Material 3 기본 트랙은 16dp 알약에 끝점 표시가 붙는다 |
| 재생목록의 영상에도 음표가 붙는다 | 종류를 안 보고 그렸다. `QueueItem` 에 `kind` 를 실어 캠코더와 음표를 가른다 |

**이어보기를 '자동으로 이어 틀고 무르기' 에서 '처음부터 틀고 물어보기' 로 뒤집었다.**

사용자가 잡아 준 것은 안내가 **파일 관리자 목록에 제한시간 없이 남는다**는 것이었고,
원인은 둘이었다. **자리** — 파일 목록은 재생을 보는 화면이 아니라 그 안내를 소비할 사람이
없다(`consumeResumeNotice` 를 부르는 코드가 폴더 화면 하나뿐이었다). **수명** —
`showSnackbar` 에 `actionLabel` 을 주면 기간이 `Indefinite` 가 된다(위 함정 표).

고치면서 동작도 함께 뒤집었다. 예전 구조는 **저장된 위치로 먼저 뛴 다음** '처음부터' 를
띄우는 것이라, 되돌릴 것을 해 놓고 무르라고 하는 꼴이었다. 게다가 무를 수단이 파일
목록에만 있어서 **재생 화면으로 곧장 들어가는 영상은 무를 수단이 아예 없었다.**
지금은 언제나 0초부터 틀고 `resumeOfferMs` 로 **묻기만** 한다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 시작 위치의 예외를 없앴다 | 예전에는 '사용자가 직접 고른 시작 항목만 이어서 튼다' 는 예외가 있었고 그 근거가 '무를 방법이 없다' 였다. 기본이 0초가 되면서 그 위험이 사라져 규칙이 한 줄이 됐다 |
| 3초를 **화면이** 잰다 | 커넥션이 타이머를 들면 화면이 없는 동안에도 3초가 흘러, 미니 바로 듣다가 재생 화면을 열면 이미 지워져 있다 |
| **지나온 제안은 버린다** | 소리 파일은 재생 화면이 열리지 않아 제안이 아무에게도 안 보인 채 남는다. 한참 뒤 들어가면 그 단추는 **뒤로 가는** 단추가 된다. `push()` 가 위치를 지날 때 버린다 |
| 항목이 바뀌면 버린다 | 다음 곡으로 넘어간 뒤에도 남아 있으면 **다른 파일의 위치로 뛰는** 단추가 된다. `PLAYLIST_CHANGED` 만 빼는 것은 그것이 우리가 방금 큐를 갈아 끼운 것이기 때문이다 |
| 기록은 **막지 않는다** | 제안이 살아 있는 동안 저장을 막는 가드를 넣으려다 접었다. 소리 파일에는 제안을 지울 화면이 없어 **세션 내내 기록이 얼어붙고**, 그러면 이번에 실제로 본 20분이 어디에도 남지 않는다. 기록은 언제나 '지금 어디까지 봤는가' 여야 한다 |

**대가를 적어 둔다.** 3초 안에 답하지 않으면 옛 자리는 10초 뒤 새 진행으로 덮인다
(`PositionSaver` 의 티커). '언제나 처음부터 튼다' 를 고르면 따라오는 결과다.

**기능 결함 셋도 함께 나왔다.**

1. **갤러리에서 사진을 열고 뒤로 가면 첫 화면으로 떨어졌다.** `showGallery` 가
   `BrowserScreen` 안의 `rememberSaveable` 이었는데, 사진을 열면 `app` 이 화면을 `Viewer`
   로 바꾸면서 **`BrowserScreen` 이 컴포지션에서 통째로 빠진다.** `rememberSaveable` 은
   구성 변경과 프로세스 재생성은 견디지만 **컴포지션에서 빠지는 것은 견디지 못한다.**
   ViewModel + `SavedStateHandle` 로 옮겨 둘 다 지킨다.
2. **삭제 → 되돌리기 뒤 썸네일이 영영 안 생겼다.** 파일이 사라진 동안 뷰어가 한 번
   읽으려다 실패하고, 그 키가 **부정 캐시**에 영구 실패로 들어간다. 되돌려도 `key in
   failed` 라 다시는 만들지 않는다(앱을 껐다 켜야 한다). 이제 **파일이 있는데 못 만들었을
   때만** 영구 실패로 센다 — 9단계가 '일시적 실패와 영구 실패를 가르지 않는다' 로 미뤄
   두었던 바로 그 항목이다.
3. **사진을 지우고 갤러리로 돌아오면 지운 것이 빈 칸으로 남았다.** `rescanGallery()` 가
   **아무 데서도 불리지 않았다** — 작업이 끝나면 `refresh()` 만 돌았고 그것이 미는 것은
   `listing` 뿐이다. 갤러리 flow 는 `WhileSubscribed(5_000)` 이라 5초 안에 돌아오면
   **지우기 전의 목록을 그대로** 내놓는다. 사용자가 말한 '더미' 가 그것이다.
   `onOperationFinished` 가 갤러리도 다시 훑게 했다.

   **다시 훑는 동안 격자를 비우지 않는 것이 나머지 절반이다.** `GalleryScanner.scan` 은
   언제나 `Scanning(0)` 으로 시작하므로, 그대로 그리면 사진 한 장을 지울 때마다 격자가
   통째로 사라졌다가 되돌아온다. 마지막 `Ready` 를 **flow 바깥의 ViewModel 필드**로
   들고 있다가 그 사이를 메운다 — flow 안에 두면 `WhileSubscribed` 가 상류를 끊을 때
   함께 사라지고, 갤러리 → 사진 → 갤러리가 바로 그 길이다.

### 구현한 뒤 다시 적대적으로 검토했다 (10단계, 일곱 건)

**계획을 검토하는 것만으로는 모자랐다.** 일곱 건을 짜기 전에 항목마다 조사원 하나와
반증조 셋(놓친 호출부·회귀·자기규칙)을 돌렸는데, 그 사이에 내가 코드를 고치고 있어서
계획서의 줄 번호가 전부 어긋났다. 반증조가 **계획이 아니라 디스크의 코드**를 읽어
답한 덕에 값이 살았다 — 그때 나온 치명 하나가 이번에 가장 큰 것이었다.

| 무엇 | 어떻게 잡혔나 |
|---|---|
| **이어보기 기록이 10초 뒤 덮인다** | 처음부터 틀기로 바꾸면 `PositionSaver` 의 10초 티커가 곧바로 옛 자리를 지운다. 계획 단계의 반증조가 잡았다. 가드를 넣는 길은 소리 파일에서 **기록이 세션 내내 얼어붙는** 반대 결함을 만들어 접었다 — 위 이어보기 문단의 표 참고 |
| **목록을 펼쳐라 가 두 번째부터 안 듣는다** | 구현 뒤 검토에서 세 렌즈가 **각각 독립으로** 같은 것을 짚었다(`MutableStateFlow` 의 conflation). 기기에서 재현하고 고친 뒤 다시 쟀다 |
| **긴 재생목록의 끝이 조작부 뒤에 깔린다** | 6곡 표본으로는 드러나지 않는다. 24곡을 만들어 재현했다 — **표본이 작으면 없는 결함이 된다**(8단계의 zip 상계 결함과 같은 교훈) |
| **조작부의 빈 자리를 누르면 가려진 줄이 눌린다** | 위 함정 표. 눈으로는 '아무것도 안 눌렀는데 곡이 바뀐다' 로 보인다 |
| `stop()` 이 제안을 안 지운다 | 잠복이었다(지금은 그 상태에서 재생 화면이 떠 있지 않다). KDoc 이 적은 불변식과 코드를 맞췄다 |
| 아이콘 주석의 '면적 29.0%' | 분모가 원이 아니라 정사각형이었다. 좌표에서 신발끈 공식으로 다시 구해 33.7% 로 고쳤다 |
| 폐기된 근거를 적은 주석 둘 | `BrowserScreen` 의 '되돌리기를 준다', CLAUDE.md 의 '시작 항목만 이어서 튼다'. **9단계 감사가 결함으로 센 바로 그 형태다** |

**반증조가 되던진 것도 있다.** '알림이 두 번째 탭부터 깨진다' 는 과장이었고(목록을 한
번도 닫지 않았으면 두 번째 탭도 옳게 돈다), 'CLAUDE.md 를 위에서부터 읽는 사람이 옛
문장을 계약으로 읽는다' 도 앞선 자리에 이미 포인터가 있어 과장이었다. 심각도를 낮춰 적었다.

### 재현되지 않는 결함을 다루는 법 (10단계, '하단이 검게 보인다')

사용자가 "알림을 탭하여 앱으로 이동한 경우, **밑부분이 검게 보이는 경우가 있습니다**"
라고 캡처와 함께 알려 왔다. 에뮬레이터에서 **여섯 경로 36프레임**을 버스트 캡처했는데
검은 프레임이 **하나도 없었다**(오디오 중 알림 탭 ×3, 영상 중 목록을 닫고 알림 탭,
화면 껐다 켜고, 앱이 떠 있는 상태에서, 큐가 넘어간 직후).

**재현하지 못한 채로 캡처를 읽는 것이 여기서 값을 했다.**

1. **그 그림은 이 컴포지션이 만들 수 없다.** 목록이 그려졌다면 `playlistOpen` 이 참이고,
   그러면 `showControls` 도 반드시 참이며(`controlsVisible || !hasVideo || playlistOpen`)
   `showingVideo` 는 반드시 거짓이다. 셋이 **같은 값**을 읽는다. 그러므로 검정은 우리가
   칠한 것이 아니라 **우리가 지운 자리**다 — 창에 구멍을 뚫는 것은 `SurfaceView` 뿐이다.
2. **창을 다시 칠할 일이 왜 생기는가**를 찾으니 `requestedOrientation` 이 나왔다.
   정지 중에는 컴포지션이 다시 돌지 않아 `SENSOR` 가 그대로 남는다(위 함정 표).
3. **에뮬레이터가 그 길을 탈 수 없다는 것도 그때 알았다** — `adb emu rotate` 를 주지
   않는 한 센서 방향이 세로로 고정이다. '가끔' 이라는 말이 그 뜻이었다.

고친 뒤, **재현하지 못했던 그 길을 만들어** 확인했다(`adb emu rotate` 로 가로를 만들고,
알림의 '다음' 으로 정지 중에 영상 → 소리로 넘긴 뒤 알림 본체를 누른다). 고치기 전에는
소리 항목인데 `cur=2400x1080` 으로 가로가 됐고, 고친 뒤에는 `cur=1080x2400` 이다.

**교훈.** 재현되지 않는다고 원인이 없는 것이 아니다. 캡처 한 장이라도 **코드가 만들 수
있는 그림인지** 따져 보면 후보가 크게 줄어든다. 그리고 **에뮬레이터가 태울 수 없는 길이
무엇인지**를 먼저 물어라 — 이번에는 센서 방향이 그것이었다.

### 10단계 잔여를 짜며 — 조사를 먼저, 설계를 그 다음, 반증을 세 겹으로

PiP·배속·트랙 선택·A-B·방향 잠금을 한꺼번에 붙였다. 순서를 **조사 → 설계 → 반증**으로
둔 것이 이 회차에서 가장 값을 했다.

**조사가 설계를 두 번 갈아엎었다.** 조사원 둘에게 `javap` 와 `dexdump` 로 사실만 캐게 했다
(media3 1.11.1 의 `MediaController`, 기기의 `framework.jar`·`services.jar`·`framework-res`).
거기서 나온 것이 위 함정 표의 절반이다 — A-B 를 클리핑으로 짜려던 계획은 `canUpdateMediaItem`
바이트코드를 읽고 접었고, PiP 파라미터를 레이아웃마다 갱신하려던 계획은 종횡비 검사가
갱신 경로에도 있다는 것을 보고 접었다. **문서만 읽었으면 둘 다 기기에서 죽었을 것이다.**

**설계 넷을 각각 세 렌즈(자기규칙·데이터 손실·놓친 호출부)로 반증하게 했다. 치명 3, 중대 36.**
치명 셋이 전부 '설계가 스스로 선언한 불변식이 코드로는 성립하지 않는다' 는 형태였다.

| 무엇 | 어떻게 잡혔나 |
|---|---|
| **'목록이 펼쳐져 있으면 PiP 에 안 들어간다' 가 성립하지 않았다** | 그 사실을 PiP 쪽에 알려 주는 유일한 지점이 **목록이 열리면 컴포지션에서 사라지는 블록** 안에 있었다. 고친 방법은 목록 여닫음을 컴포지션에서 **액티비티로 올려** 화면과 PiP 판정이 같은 흐름을 읽게 한 것이다 |
| **트랙 시트를 한 번도 열 수 없다** | `LaunchedEffect(fileKey) { onDismiss() }` 는 항목이 바뀔 때가 아니라 **시트가 뜨는 첫 프레임에** 돈다 |
| **시트를 조작부 뒤에 두면 되돌릴 수 없는 화면이 된다** | 조작부를 감춘 순간 시트가 컴포지션에서 빠지는데 제스처까지 꺼 둔 상태였다 |
| 항목 전환 판정을 `hasVideo` 로 한 것 | 그 값은 건너뛰는 동안 잠깐 거짓이 된다 — PiP 창이 '다음' 한 번에 닫히고, 방향 잠금이 조용히 풀린다. **큐가 실어 보내는 종류**로 바꿨다 |
| 전환 이유로 트랙 선택을 되돌린 것 | `REASON_REPEAT` 와 `PLAYLIST_CHANGED` 에서도 돌아 한 바퀴마다, 자막을 고를 때마다 소리 트랙이 지워진다. **`mediaId` 비교**로 바꿨다 |
| `playWhenReady` 를 `isPlaying` 으로 잰 것 | 버퍼링·오디오 포커스 억제 중에는 거짓이라 그 순간 자막을 고르면 재생이 멈춘 채 돌아오지 않는다 |
| 진행 바 마커의 정렬 | `contentAlignment = Center` 인 상자에서 `fillMaxWidth(fraction)` 자식은 **가운데 정렬**된다. `BoxWithConstraints` + `offset(x)` 로 바꿨다(`offset` 은 RTL 을 따른다) |

**기기가 설계를 한 번 더 고쳤다.** 'PiP 창을 X 로 닫으면 멈춘다' 는 `isFinishing` 으로
짰는데 **한 번도 참이 되지 않았고**, 모드 콜백으로 옮겼더니 이번에는 **차례가 반대**였다
(위 함정 표). 콜백 차례는 문서에 없고 바이트코드로도 알 수 없다 — `Iro.d` 세 줄을 찍어
logcat 으로 읽고서야 맞췄다. **'언제 불리는가' 는 기기에만 있다.**

**한 가지는 우리가 만든 부하 때문에 깨졌다.** JVM 시험이 268 → 309건으로 늘자
`SequentialCostTest`(8단계의 시간 비율 시험)가 `./gradlew test` 에서 양쪽 방향으로 번갈아
깨졌다. 상한을 푸는 길은 쓰지 않았다 — 그러면 재려던 차이가 함께 흐려진다. **표본을
세 번에서 다섯 번으로 늘렸다**(잡음은 시간을 늘리는 쪽으로만 작용하므로 시도가 늘수록
최소값은 '방해받지 않았다면' 에 가까워지기만 한다). 전체 시험을 세 번 연속으로 돌려 확인했다.

### 구현한 뒤 다시 적대적으로 검토했다 (10단계 잔여, 열넷)

기기에서 전부 돌아가는 것을 보고 난 뒤에 세 렌즈(자기규칙·데이터 손실·놓친 호출부)로
**구현 자체**를 반증하게 했다. 발견 스물, 반증을 거쳐 **확정 열넷**(치명 0, 중대 2).
심각도는 반증조가 다시 매겼고 major 로 올라온 것 여럿이 minor 로 내려갔다 — 그 정정이
값을 했다. 아래 둘이 실제 결함이고 나머지는 흔들림·주석·군더더기다.

| 무엇 | 어떻게 잡혔나 |
|---|---|
| **그림 자막 거르기가 한 번도 듣지 않았다** | 위 함정 표. 단위 시험은 통과하는데 **실제 경로를 한 번도 타지 않는** 형태라, 시험을 '변환된 뒤의 값' 으로 다시 썼다. 덤으로 자막 줄에 `SRT`·`ASS` 가 되살아났다(기기에서 `한국어 · SRT` 확인) |
| **어긋난 짝 하나가 재생 화면을 끝낸다** | 위 함정 표. `queue` 와 `queueIndex` 를 갈라 받던 것을 `State.currentKind` 한 값으로 합쳤다 |
| 항목 전환 중 `hasVideo` 의 공백 | 한 프레임 거짓이 되는 그 값을 테마·표면·방향 요청·목록 기본값이 **모두** 읽고 있었다. 고친 자리는 하나다 — `push()` 에서 트랙을 아직 모를 때 **큐가 말하는 종류**로 메운다. 소비자 넷을 각각 고치는 것보다 뿌리가 하나인 쪽이 맞다 |
| 방향 잠금이 다른 방향으로 다시 걸린다 | 위 함정 표. `adb emu rotate` 를 **화면이 꺼진 동안** 넣어 재현하고 고친 뒤 다시 쟀다 |
| `pipActive` 가 '들어가려 한다' 와 '들어가 있다' 를 겸했다 | 진입이 거절된 순간에도 창 판정이 참이 된다. 그리기용 낙관값(`pipCollapsing`)과 확인된 상태를 갈랐다 |
| PiP 창에 제목과 자막이 찍힌다 | `!inPip` 가드를 영상 가지에만 달았다. 큐가 소리로 넘어간 짧은 창에서 드러난다 |
| 멈춘 채 B 근처에 서면 티커가 50ms 로 영원히 돈다 | 위치가 더 이상 늘지 않으니 조인 간격이 풀리지 않는다. **멈춰 있으면 조이지 않는다**로 고쳤다 |
| `candidatesFor` 가 호출부를 잃었다 | 자막 파일 목록을 짝 순서로 정렬하는 유일한 함수인데 아무도 부르지 않아, 시험만 통과하는 채로 남아 있었다. `refreshTracks` 가 거치게 했다 |
| `PlayerActivity` 의 클래스 KDoc | "지금은 최소한이다 — 표면, 재생/일시정지, 탐색" 이 그대로였다. **9단계 감사가 바로 이 파일의 KDoc 으로 결함을 셌고 같은 자리가 두 번째로 낡았다.** 지금 쥔 것과 지키는 불변식 넷으로 다시 썼다 |
| 드로어블 주석 둘이 엉뚱한 파일을 가리켰다 | 치환하며 문장이 깨졌고, 짝인 `PauseIcon` 쪽에는 표시가 아예 없었다. '양쪽 주석에 짝을 적는다' 는 규칙을 스스로 반만 지킨 셈이다 |
| `maxNumPictureInPictureActions` 를 흐름마다 읽었다 | 액티비티 수명 동안 바뀌지 않는 값이라 `by lazy` 로 한 번만 |

**반증조가 되던진 것도 있다.** '재생목록 기본값 때문에 ▶ 로 연 폴더에서 목록이 저절로
펼쳐진다' 는 시나리오가 성립하지 않았다(▶ 는 언제나 `showPlaylist=true` 로 열어 그 경로는
오히려 면역이다). 그런 정정이 뿌리를 `hasVideo` 의 공백으로 옮겨 놓았다 — **발견을 그대로
고치는 것보다 반증을 거친 뒤에 고치는 편이 고칠 자리를 줄인다.**

### 11단계가 세운 것 (PDF 절반)

**문서 뷰어는 만화 뷰어의 구조를 그대로 가져오되 셋이 다르다.**

| | 만화 뷰어(9단계) | 문서 뷰어(11단계) |
|---|---|---|
| 쪽의 출처 | 아카이브 안의 **바이트**(경로가 없다) | 파일 하나 안의 **쪽 번호** |
| 방향 | 왼→오 · 오→왼 · 세로 스크롤 | **왼→오 하나뿐** |
| 확대 | 흐려진 채로 둔다 | **그 자리를 다시 그린다**(타일) |

세 번째가 PDF 가 벡터라서 얻는 이득이다. 만화 쪽은 원본이 래스터라 확대하면 원본 화소를
다시 읽을 수밖에 없지만(`BitmapRegionDecoder`), PDF 는 **더 큰 배율로 다시 그리면** 된다 —
원본이라는 것이 애초에 없다. 방향을 고르게 하지 않은 것은 PDF 에 읽는 방향이 적혀 있지
않아서다. 세로쓰기 문서 몇 편을 위해 설정을 하나 더 만들지 않는다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 쪽 크기 계산을 **순수 함수로**(`PdfLimits`) | 값이 틀리면 결과가 `OutOfMemoryError` 이거나 `Canvas` 의 거부다. 그것을 에뮬레이터에서 재현해 고치는 일은 느리고 불확실하다 — `ImageLimits` 가 같은 이유로 순수 함수다 |
| 배율을 **연속값으로** | `ImageLimits.sampleForBudget` 은 2의 거듭제곱이라 최대 4배 흐려진다. PDF 는 그 손해를 볼 이유가 없다 |
| 폭·높이를 **내림(floor)** | `floor(w·s)·floor(h·s) ≤ w·h·s²` 가 언제나 성립해 반올림이 상한을 넘기는 경우가 구조적으로 없다. 올림으로 적으면 시험이 통과해도 실제로는 몇 바이트씩 넘는다 |
| 상한을 `min(예산, 100MiB)` 로 | 100MiB 비트맵을 `PdfRenderer` 는 군말 없이 채워 주는데 그 다음의 `Canvas` 가 API 31 에서 거부한다 |
| 쪽 크기 상한을 **두지 않는다** | 14400pt(200인치)짜리 쪽은 명세가 허락하는 정상 쪽이고, 그리는 문제는 배율이 이미 푼다. **확인하지 못한 명세 상한을 상수로 박지 않는다** — 8단계의 ZIP 엔트리 수 상계가 멀쩡한 파일을 거절한 그 형태다 |
| 선명화를 **띠가 아니라 타일로** | 만화는 원본을 `BitmapRegionDecoder` 로 다시 읽지만 PDF 에는 디코딩할 원본이 없다. 대신 더 큰 배율로 다시 그린다 |
| 타일에 **쪽 크기를 실어 보낸다**(`Tile.pageWidth`) | 화면이 타일을 얹으려면 쪽 안의 비율이 필요한데, 그러려면 `floor(폭pt × 배율)` 을 호출자가 한 번 더 적어야 한다. 내림을 반올림으로 다시 적는 순간 타일이 반 화소씩 어긋난다 — **계산을 두 벌로 두지 않는다** |
| 요청을 **눈금으로 끊는다**(`DocDetail`) | `snapshotFlow` 는 손가락이 1화소 움직일 때마다 다른 값을 낸다. pdfium 의 잠금이 **프로세스 전역**이라 그 요청들이 줄을 서고, 줄이 밀리는 만큼 화면이 늦게 따라온다. 배율 1/100 · 자리 1/200 이면 눈에 보이지 않으면서 요청 수가 수십 분의 일이 된다 |
| 층을 **`ZoomableImage` 안에서** 겹친다 | 확대·이동 변환을 `ZoomState` 한 곳에서만 읽어야 층이 교체되는 프레임에 그림이 튀지 않는다. 층마다 자기 변환을 들면 반드시 어긋난다(그 컴포저블의 주석이 6단계부터 적어 둔 것이고, 11단계가 그 자리에 실제로 층을 하나 더했다) |
| 창(window)과 미리 읽기를 **두지 않는다** | 9단계의 창은 solid 아카이브의 정의(앞 14쪽을 풀어야 15쪽이 나온다) 때문에 생긴 장치다. `PdfRenderer` 는 쪽 무작위 접근이 싸다(아래 실측). **없어도 되는 상태를 만들면 그 상태가 틀리는 날이 온다** |
| 닫기를 **렌더 디스패처에 얹는다** | `close()` 는 `AutoCloseable` 이라 `suspend` 가 아닌데 렌더는 코루틴 안에서 돈다. 병렬도 1 인 디스패처에 얹으면 진행 중인 렌더 뒤에 줄을 서고, 새 렌더는 닫힘 깃발이 막는다 |
| 축출해도 **`recycle()` 하지 않는다** | 만화에서는 '파괴된 비트맵을 만난 프레임이 앱을 죽인다' 였는데, PDF 는 recycle 된 비트맵에 `render` 하면 예외가 아니라 **`SIGABRT` 로 프로세스가 즉사한다**(조사 중 실제로 죽었다) |
| 배경은 **어두운 회색**, 쪽은 희다 | 만화는 검은 배경에 그림을 얹지만 문서는 흰 종이다. 검정 위의 흰 종이는 대비가 너무 세고, 어두운 모드라고 쪽을 반전하면 그림과 표의 색이 깨진다 |
| 슬라이더에 **눈금을 두지 않는다** | 만화는 쪽이 수십이지만 문서는 2,000쪽이 드물지 않고, 그만큼의 눈금 표시는 트랙을 회색 띠로 만든다 |

**`Documents.open` 의 소유권 계약을 여기서 고쳤다.** 2단계가 세운 그 함수는 `withTimeout`
안에서 문서를 만들어 반환하는데, 그 사이 취소되면 코루틴이 값을 버린다 — 호출자는 받은
적이 없으니 닫을 수 없고 pdfium 문서와 파일 서술자가 GC 를 기다린다. 9단계 뒤 감사가
`ComicOpen.open` 에서 잡은 것과 **같은 형태**이고, 고치기 좋은 때였던 이유는 그 함수에
**호출자가 하나도 없었기** 때문이다(11단계의 `DocViewModel` 이 첫 호출자다).
`onOpen` 콜백 + 호출자의 `finally` 로 갈랐다 — 12·13단계가 이 계약을 그대로 베낀다.

### 11단계 실측 (Android 12 에뮬레이터)

**쪽 판정을 눈으로 하지 않았다.** 표본을 쪽마다 다른 단색으로 만들고 흰 숫자를 찍어,
화면 캡처의 최빈색으로 '지금 몇 쪽인가' 를 기계가 답하게 했다(9단계와 같은 장치).
스크래치패드의 `mkpdf.py` 가 그것이고 저장소에는 넣지 않는다 — PDF 는 본문이 텍스트라
인코더 없이 손으로 쓸 수 있다(10단계가 mkv 를 EBML 로 쓴 것과 같다).

**예산**(힙 등급 192MB, 화면 1080×2400. 만화와 같은 표를 쓴다).

| | 값 |
|---|---|
| 한 장(`baseBytes`) | 10,368,000 B |
| 살아 있는 양(`liveCap`) | 41,472,000 B = 네 장 |
| 쪽 하나의 상한(`pageCap`) | 10,368,000 B |
| 선명화층(`detailCap`) | 8,859,648 B |

**렌더 시간.** 쪽마다 비트맵을 새로 만들고 흰색으로 지운 뒤 그리는 값 전부다.

| 쪽 | 비트맵 | 시간 |
|---|---|---|
| A4 세로(595×842pt) | 1080×1528 | **3~41 ms** |
| A4 가로(842×595pt) | 1080×763 | 3~10 ms |
| 200인치(14400×14400pt) | 1080×1080 | **4 ms** |
| 선명화 타일(배율 3.63) | 998×2218 | — |

**9단계의 solid 7z 이 1.4~2.1초였던 것과 견주면 세 자리 다른 값이다.** 창과 미리 읽기를
두지 않기로 한 근거가 이 표다.

**화면에서 확인한 것**(릴리스 R8 빌드에서 다시 확인했다).

| 확인한 것 | 결과 |
|---|---|
| 8쪽 PDF 열기 | `1 / 8`, 캡처의 최빈색이 1쪽 색과 **정확히 일치**(217,38,38 · 비율 1.00) |
| 왼쪽으로 밀기 ×3 | 2 → 3 → 4쪽. 색이 차례대로 |
| 오른쪽으로 밀기 | 3쪽으로 되돌아온다 |
| 배경 | 쪽 위아래 여백이 `(48,48,48)` = 우리가 정한 회색 |
| 비율 | 가로 쪽(842×595pt)이 1080×763 — 1.415 대 1.415로 **늘어나지 않는다** |
| `쪽으로 가기` 7 | 7쪽(242,115,26), 막대 `7 / 8` |
| **확대 선명화** | 글자 가장자리의 중간색 화소가 **맞춤 1px → 확대 직후 2~3px → 2.5초 뒤 다시 1px**. 흐려졌다가 타일이 와서 선명해지는 것을 화소로 잰 값이다 |
| 타일 로그 | `타일 쪽 2 x3.63 (202,357) 998x2218 / 쪽 2159x3056` — 배율이 맞춤(1.815)의 2배, 자리가 두드린 곳 |
| 회전 | 가로에서 `cur=2400x1080`, 같은 쪽이 763×1080 으로 다시 뜬다. 세로로 돌아오면 쪽 그대로 |
| 이어보기 | 앱을 강제 종료하고 다시 열면 `3쪽부터 이어서 봅니다` + `처음부터`, 실제로 3쪽 |
| PDF 아닌 파일(`.pdf` 확장자) | `이 문서를 열 수 없습니다`(Corrupt). 로그 `열지 못했다: Corrupt` |
| 압축 안의 `doc.pdf` | `압축 파일 안의 문서는 열지 않습니다. 풀어서 여세요` |
| 압축 안의 `inner.cbz` | `압축 파일 안의 압축 파일은…`(9단계 그대로. 회귀 없음) |
| 압축 안의 `readme.txt` | `이 항목을 열 수 없습니다`(그대로) |
| **DB 마이그레이션 2→3** | 기기의 `iroiro.db` 를 꺼내 보니 `user_version = 3`, `doc_progress` 의 CREATE 문이 선언 그대로, 그리고 **앞 단계 데이터가 그대로다** (`playback_position` 4행 · `comic_progress` 7행 · `bookmark` 1행). `fallbackToDestructiveMigration` 을 켜지 않은 것이 여기서 확인된다 |
| 이어보기 키 | `doc_progress` 의 행이 `sha256` 과 **파일 이름**뿐이다 — 절대경로가 없다 |

**함정 하나를 표본 쪽에서 배웠다 — `startxref` 를 망가뜨린 PDF 가 그냥 열린다.**
`broken.pdf` 는 xref 를 깨뜨려 만든 표본인데 pdfium 이 **스스로 객체를 훑어 복구**해
2쪽짜리로 정상 표시됐다. 깨진 파일을 시험하려면 **머리만 있고 몸이 없거나 아예 PDF 가
아닌** 표본이어야 한다(계측 시험은 그쪽을 쓴다). 표본이 의도한 경로를 실제로 타는지
확인하지 않으면, 시험은 통과하면서 아무것도 시험하지 않는다.

### 11단계가 세운 것 (EPUB 절반)

**EPUB 은 HTML 이다. 그래서 이 단계가 실제로 만든 것은 EPUB 뷰어가 아니라 '문서를
안전하게 그리는 기반' 이다** — 12·13단계의 docx·HWPX 가 쪽 재현을 포기하고 흐름 렌더로
가기로 한 이상(요구사항의 '지원하지 않는 것') 그 셋이 내놓는 것도 결국 HTML 이다.
모듈이 셋으로 갈린 것이 그 사실을 그대로 옮긴 것이다.

| 모듈 | 무엇 | 왜 여기인가 |
|---|---|---|
| `format:html` | 위생(HTML·CSS)과 읽기용 껍데기 | 세 단계가 공유한다. 위생 규칙이 두 벌이 되면 한쪽만 고쳐지는 날이 온다 |
| `format:epub` | 컨테이너·OPF·목차·암호화 판정 | ZIP + XML 이라 순수 JVM 이다. 그래서 깨진 표본을 초 단위로 시험한다 |
| `core:webhost` | 잠긴 WebView 와 가짜 출처 | **포맷을 모른다.** 알면 12·13단계가 같은 호스트를 못 쓴다 |

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 본문을 **가짜 출처**로 띄운다(`https://book.iroiro.invalid/…`) | `loadData` 는 출처가 null 이라 상대 주소가 풀리지 않고, `loadDataWithBaseURL` 은 장을 넘길 때마다 기준을 우리가 관리해야 한다. 우리 호스트의 URL 로 열면 **상대 주소를 WebView 가 푼다** — 그쪽이 우리보다 정확하고, 경로 계산이 한 벌로 끝난다 |
| `.invalid` 를 쓴다 | RFC 2606 이 **절대 등록되지 않도록 예약한** 최상위 도메인이다. 가로채기가 한 번 빗나가도 그 요청이 닿을 곳이 정의상 없다 |
| 위생기가 **DOM 을 만들지 않는다** | 저장소 규칙이기도 하고 안전 문제이기도 하다 — 임의의 사용자 파일은 수만 겹으로 중첩될 수 있고, 트리를 만들면 그 깊이가 그대로 스택이다. 잘못 닫힌 태그를 고쳐 주지도 않는다(그 일은 WebView 의 파서가 우리보다 잘한다) |
| 스킴 판정을 **한 곳**에 둔다(`Urls`) | 처음에는 리졸버에게 맡겼는데, 그러면 리졸버를 쓰는 쪽마다(11단계 EPUB, 12·13단계 docx·HWPX) 검사를 한 벌씩 다시 써야 한다. 한 곳이 `data:text/html` 을 빠뜨리는 날 그 문서는 창 안에서 새 문서를 연다 |
| `on…` 으로 시작하는 속성을 **예외 없이** 지운다 | 목록을 적어 두면 목록에 없는 새 이벤트가 그대로 지나간다 |
| 내용까지 버리는 것과 태그만 지우는 것을 **가른다** | `<script>`·`<iframe>`·`<object>` 는 내용까지 버린다. `<form>`·`<video>`·`<noscript>` 는 태그만 지운다 — 그 안의 글은 **읽는 사람이 보려던 글**이다 |
| CSS 를 **이해하려 들지 않는다** | 막을 것은 셋뿐이고(`@import`·바깥 `url(…)`·옛 스크립트 문법) 셋 다 글자로 찾을 수 있다. 글꼴·여백·색은 그대로 둔다 — 안전하게 만든다고 조판을 지우면 남는 것은 '지켜진 문서' 가 아니라 **망가진 문서**다 |
| 바깥 `url(…)` 을 **`none` 으로 바꾼다** | 선언을 지우면 중괄호 균형이 깨져 뒤의 규칙이 함께 무너지고, 빈 `url()` 로 두면 WebView 가 그 쪽을 다시 가져오려 든다 |
| **스타일시트 파일도 위생을 거친다**(`EpubBook.styleSheet`) | 장 안의 `<style>` 만 걸러서는 구멍이 남는다 — `<link rel="stylesheet">` 로 걸린 파일이 그 길로 들어온다. 위생의 구멍은 한 군데면 충분히 뚫린다 |
| 상대 주소를 **그대로 남긴다** | 우리가 절대 주소로 바꾸면 경로 계산이 두 벌(우리 것과 WebView 것)이 되고, 둘이 어긋나는 날 그림이 조용히 사라진다. 대신 위생기에게 **'이 주소가 책 안에 있는가' 를 묻는다** |
| 목차를 **평평하게** 만든다 | 트리를 들면 화면에 펼침 상태가 생기고, 그 상태는 쪽을 옮길 때마다 맞춰 줘야 하는 또 하나의 상태다(9단계의 '없어도 되는 상태를 만들면 그 상태가 틀리는 날이 온다'). 깊이는 들여쓰기로만 쓴다 |
| `linear="no"` 를 차례에서 뺀다 | 본문 차례 밖(광고·판권)이다. 넣으면 사용자가 읽는 순서가 책이 말한 순서와 달라진다 |
| `mimetype` 을 **거절의 근거로 쓰지 않는다** | 명세는 압축하지 말라고 하지만 실물에는 어긴 파일이 흔하다. 거절하면 멀쩡히 읽히는 책을 '깨졌다' 고 말하게 된다 — 8단계가 ZIP 엔트리 수 상계로 겪은 형태다. 경고만 남긴다 |
| `encryption.xml` 을 **읽어서** 가른다 | 같은 파일이 DRM 일 수도 **글꼴 난독화**일 수도 있다. 후자를 DRM 으로 보면 멀쩡한 상업 EPUB 을 통째로 거절한다. 본문이 걸렸거나 **무엇이 걸렸는지 모르면** 거절하고, 난독화면 푼다(11단계 뒤 — 처음에는 알리기만 했다) |
| 같은 이름의 엔트리를 **경고로 남긴다** | ZIP 명세가 금지하지 않으므로 깨진 파일이 아니라 **있을 수 있는 파일**이다. 조용히 고르면 사용자가 보는 그림이 왜 그것인지 아무도 모른다 |
| 본문을 **들고 있지 않는다** | 300쪽짜리 책의 XHTML 을 전부 펴 두면 그것만으로 수십 MB다. 보고 있는 장 하나만 그때그때 읽는다 |
| 자원을 **바이트로 만들어** 넘긴다 | `shouldInterceptRequest` 는 다른 스레드에서 온다. ZIP 리더를 그 스레드가 물고 있으면 책이 닫히는 순간과 겹친다. 읽는 동안만 잠그고 상한(32 MiB) 안에서 복사한다 |
| EPUB 은 **겹치지 않고 칸으로 나눈다** | 나머지 뷰어는 조작부를 그림 위에 겹치지만 글을 읽는 화면에서는 반대다 — 겹치면 **글자가 막대 밑으로 들어가 읽히지 않는다.** 그리고 WebView 는 탭을 스스로 쓰므로(글자 선택·링크) 두드림으로 조작부를 여닫을 길이 없다 |
| 세는 단위가 **쪽이 아니라 장** | 장 하나의 길이는 글의 길이가 정한다. 그것을 화면 크기로 잘라 쪽을 만들면 **글꼴 크기가 바뀔 때마다 쪽 번호가 달라지고**, 이어보기가 가리키는 자리가 그때마다 움직인다 |
| 버린 것을 **말한다**(`DocNoticeList`) | `UnsupportedFeatures` 가 2단계부터 적어 둔 계약이다 — "사용자는 원문에 무엇이 있었는지 모르므로, 빠진 것이 있다는 사실 자체를 앱이 말해 주어야 한다". 11단계가 그 계약의 첫 소비자다 |

**네 번째 이음매가 여기서 생겼다**(`DocumentSupport`). 문서 화면은 하나인데 12·13단계에
가면 여는 포맷이 일곱이 된다 — 화면이 직접 고르면 `feature:*` 이 포맷 모듈을 일곱 보게
되어 의존 표를 어긴다. 무엇을 무엇으로 여는지는 `app/FormatRegistry` 가 알고, 그것이
`FormatProbe`·`ProbeContext` 의 **첫 호출자**다(2단계가 세워 두고 11단계까지 비어 있었다).

### 11단계 실측 — EPUB (Android 12 에뮬레이터, 릴리스 R8)

**표본을 손으로 만들었다.** `mkevil.py` 가 **장마다 공격 하나씩** 넣은 책을 만든다 —
그래야 화면에서 무엇이 막혔는지 글로 읽을 수 있다. 한 화면에 여럿을 넣으면 판정이
모호해진다(9단계가 쪽을 단색으로 만든 것과 같은 장치의 글자판이다). 저장소에는 넣지 않는다.

| 장 | 시도한 것 | 결과 |
|---|---|---|
| 1 | `<script>` 가 문단 글자를 바꾼다 | **문단이 그대로다.** 스크립트가 돌지 않았다 |
| 2 | `https://tracker…/pixel.gif` · `//cdn…/x.png` | 대체 글자만 남는다. logcat 에 그 호스트가 **한 줄도** 없다 |
| 3 | `onclick`·`onmouseover`·`onerror` | 셋 다 아무 일도 없다. `onerror` 가 본문을 바꾸지 못했다 |
| 4 | `<iframe>`·`<object>` 안의 글 | **내용째 사라진다**('프레임 안의 글' 이 화면에 없다) |
| 5 | `@import url(https://…)` · `background:url(https://…)` · `expression(` | 셋 다 사라지고 **제목의 빨간 색과 밑줄은 남는다** — 조판까지 지우지 않는 것이 이 줄의 요점이다 |
| 6 | `file:///sdcard/…` · `javascript:` 링크 | 눌러도 아무 일이 없다 |
| 7 | 정상 장(그림·스타일·장 안 앵커·장 사이 링크) | 전부 보이고 전부 동작한다 |
| 알림 | 위 일곱 장을 지난 뒤 | `바깥을 가리키는 참조 15개 · 알 수 없는 요소 5개 · 이벤트 처리기 3개 · 스크립트 2개 · 프레임 1개 · 삽입 개체 1개` |

**정상 책에서 확인한 것.**

| 확인한 것 | 결과 |
|---|---|
| EPUB3(`nav.xhtml`) 6장 | 제목이 `dc:title` 로 뜨고 `1 / 6 장`. 그림과 CSS 가 붙는다 |
| EPUB2(`toc.ncx`) 6장 | 같다. 목차를 ncx 에서 읽는다 |
| 장 넘기기 | 1 → 2 → 3 → (뒤로) 2. 막대의 숫자와 본문이 함께 바뀐다 |
| 목차 | 여섯 줄이 모두 뜨고 `landmarks` 의 '표지' 는 **섞이지 않는다**. 고르면 그 장으로 간다 |
| 이어보기 | 앱을 강제 종료하고 다시 열면 `5장부터 이어서 봅니다`(PDF 는 `쪽`). 실제로 5장 |
| `mimetype` 을 압축해 넣은 책 | **열린다.** 경고만 남는다 |
| `META-INF/container.xml` 이 없는 책 | `이 문서를 열 수 없습니다` |
| `encryption.xml` 이 있는 책 | `암호가 걸린 문서는 열지 않습니다` — 그 표본의 `<EncryptedData/>` 가 비어 있어 '무엇이 걸렸는지 모른다' 로 끝난 경우다 |
| 같은 이름의 엔트리가 둘인 책 | 열리고, 알림에 `같은 이름을 씁니다` |
| `<link href="../../../../etc/passwd">` 인 책 | 열리고 그 스타일시트만 사라진다(책 밖을 가리키므로 리졸버가 null) |
| 안에 `inner.zip` 이 든 책 | 열린다. 그 항목을 가리키는 주소가 없으므로 아무 일도 없다 |
| 권한 | 병합 매니페스트와 기기 `dumpsys` 양쪽에 **`INTERNET` 이 없다** |
| 릴리스(R8) | 위 전부가 그대로다. `XmlPullParserFactory` 가 R8 에서도 파서를 찾는다 |
| FATAL | 0 |

### 11단계 뒤 — 암호 PDF · 원본 2배 확대 · 글꼴 난독화 (2026-09-23)

사용자 요청 셋: '편집이 영영 없다고 할 수 없다'(위 '이 프로젝트의 목적'), '읽는 방법이 공개된
암호화는 연다 — PDF 부터'(위 '암호가 걸린 파일'), '문서·사진의 확대를 원본의 2배까지'.

**암호 PDF 를 세운 방식.**

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 복호화기를 **직접 쓴다**(`docview/pdf/crypt/`, 순수 코틀린) | API 34 이하의 `PdfRenderer` 는 암호를 받지 않는다. PdfBox-Android 는 채택하지 않기로 한 목록에 있다 |
| 암호 PDF 를 **평문 PDF 로 다시 쓴다** | pdfium 은 우리 복호화 함수를 부를 수 없다. 문자열·스트림만 풀고 나머지는 글자 그대로 옮긴 뒤 `/Encrypt` 를 빼고 xref 를 새로 쓴다. 스트림은 **압축된 채로** 옮긴다(풀면 메모리 파일이 몇 배로 부푼다) |
| 쓰는 곳은 **memfd** | 평문을 저장소에 쓰지 않는다(위 '암호가 걸린 파일'). `Os.memfd_create` 는 API 30 부터이고 앱 샌드박스(untrusted_app)에서 돈다(계측 시험 `메모리_파일_경로가_이_기기에서_돈다`) |
| API 35 는 **플랫폼 먼저** | `PdfRenderer(pfd, LoadParams)` — 평문 사본이 없다. 거절하면 우리 것으로 한 번 더(후보가 더 많다) |
| `/Length` 는 **열 자리 0 으로 채워 쓰고 나중에 고친다** | AES 는 채움을 떼기 전까지 평문 길이를 모른다. 자리 채움은 **사전의 맨 끝**에 둔다 — 16진 문자열이 0 바이트 다섯을 품으면 같은 글자가 되어 엉뚱한 곳을 고친다 |
| xref 는 **쓴 번호만** 소구역으로 | 빽빽하게 쓰면 번호 하나를 크게 적은 파일이 표를 수백 MB 로 부풀린다(검토가 잡았다) |

**시험의 오라클이 우리가 아니다.** 표본 19개(합계 61,935 바이트)를 **pypdf 6.17**(순수 파이썬)과
**qpdf 12.3.2**(pikepdf 경유)가 잠갔다 — R2·R3·R4(RC4·AES)·R5·R6, 객체 스트림, 선형화,
메타데이터 평문, 소유자 암호만, 한글 암호. 두 도구가 서로의 표본을 평문과 같게 푸는 것을
먼저 확인했고(`fix_metadata_version` 함정은 그때 잡혔다), 우리 결과를 **qpdf 와 엄격한 pypdf 가
다시 읽어** 경고 0 을 확인했다(스크래치패드의 `mkcrypt.py`).

**실측**(에뮬레이터).

| 확인한 것 | 결과 |
|---|---|
| 17개 표본을 맞는 암호로 | **API 31·35 둘 다 평문과 화소가 같다**(`Bitmap.sameAs`) |
| API 35 의 플랫폼 길만 따로 | 17개 전부 — 한글 암호 포함 |
| 틀린 암호 · 소유자 암호 · 소유자 암호만 | 틀렸다 · 열린다 · 묻지 않고 열린다 |
| 인증서로 잠근 PDF | API 31 은 `SecurityException`, **API 35 는 `IOException`**(함정 표). 둘 다 '암호로는 열 수 없다' 로 끝난다 |
| 릴리스(R8) 화면 | 암호 창 → 틀린 암호에 빨간 '암호가 맞지 않습니다' → 취소하면 안내와 '암호 넣기' → 맞는 암호로 1/3 쪽 |

**원본 2배 확대.** 최대 배율 = `2 × 원본 폭 / 맞춤 폭`(`ZoomMath.maxScale`). 예전에는 화면에
맞춰 줄여 뜬 비트맵을 원본 자리에 넣어 12MP 사진도 맞춤의 2배에서 멈췄다. 원본의 뜻은 셋이다 —
사진은 **디코딩 전 원본의 화소**(`ImageIo.probe`), 만화는 쪽의 치수(`PageInfo.displayWidth`),
PDF 는 **종이의 실제 크기**(`폭pt × densityDpi / 72`, 데스크톱 뷰어의 '100%'). 작은 그림은
맞춤만으로 이미 원본의 2배를 넘으므로 **맞춤의 2배를 하한으로 남겼다**(두 번 두드려도 아무 일이
없으면 고장으로 읽힌다) — 사용자 요청을 그 그림들에서만 넘는 자리다.

키운 만큼 선명해야 뜻이 있으므로 **사진·만화에 영역 디코딩**을 붙였다(6단계가 미뤄 둔 것,
`RasterDetail`·`rememberRasterDetail`). PDF 는 타일의 상한을 '맞춤의 4배' 에서 **화면이 허락하는
최대 확대**로 바꿨다(`PdfLimits.tileFor(maxScale)`).

| 확인한 것 | 결과 |
|---|---|
| 8000×6000 사진, 폰에서 핀치 끝까지 | 배율 **14.81** = 2 × 8000 / 1080. 조각 750×1313, 원본 50화소 칸이 화면 100화소 |
| 같은 그림을 EXIF 6 으로 눕혀 저장한 것 | 바로 서서 보이고 조각도 바로 선다(`RegionMath`) |
| 4000×3000 사진 | 조각을 청하지 않는다 — 바닥층이 이미 원본 그대로 떠 있다(함정 표) |
| A4 PDF 를 끝까지 | 타일 배율 **11.66 px/pt** = 2 × 420 / 72. 쪽 전체 6937 화소 폭 |

핀치는 `adb emu event send` 로 넣었다(함정 표 — 게스트의 `sendevent` 는 막혀 있다).

**글꼴 난독화를 푼다**(`FontObfuscation`). IDPF 는 EPUB 3.3 명세 4.4 를 그대로 옮겼고, Adobe 는
명세가 아니라 Readium 구현을 읽어 확인했다. 오라클은 **Readium 이 만든 난독화 글꼴**이다 —
제3자의 글꼴이라 커밋하지 않고 `samples-local/epub-deobfuscation/` 에 둔다.

**적대적 검토(구현 뒤, 다섯 렌즈 + 렌즈마다 반증조 하나).** 발견 21, **확정 18**(중복 셋 포함),
반증 3. 고친 것:

| 무엇 | 어떻게 잡혔나 |
|---|---|
| **확대한 조각이 바닥층과 어긋나 튄다** | 자리를 `IntOffset` 으로 잘라 **확대 변환 안에서** 그렸다 — 1화소 미만의 오차가 배율만큼 커진다(12000화소 파노라마에서 18화소). 두 층 다 부동소수로 놓는다 |
| V4 에 `/Length` 가 없으면 맞는 암호가 '틀렸다' | 기본값 40비트. 명세는 V4 의 `/Length` 를 요구하지 않는다 — 128 이 기본이고 AESV2 는 언제나 128 |
| `stream` 뒤의 공백을 데이터로 읽었다 | AES 의 IV 가 통째로 어긋난다. pdfium 처럼 줄 끝까지 건너뛴다 |
| xref 가 밀린 파일에서 `/Encrypt` 를 엉뚱한 객체로 | 머리 번호를 대조해 어긋나면 다시 세운다(pdfium 과 같다) |
| 새 판이 지운 객체가 옛 객체 스트림에서 되살아났다 | xref 가 아는 번호는 건드리지 않는다 |
| 번호 하나로 xref 가 168 MB | 쓴 번호만 소구역으로 |
| 예측자의 폭으로 몇 바이트가 128 MB | 폭을 실제 데이터로 누른다 |
| 뿌리 없는 큰 파일을 두 번 통째로, 취소 없이 훑었다 | 다시 세우기는 한 번, 1 MiB 마다 취소 확인, PDF 머리가 없으면 훑지 않는다 |
| API 35 에서 오타 하나가 '너무 크다'·'열 수 없다' 로 | 플랫폼이 거절한 뒤의 실패는 '틀렸다' 다 |
| 메모리 파일 서술자가 샐 수 있었다 | 렌더 디스패처에 줄 선 채 취소되면 블록이 돌지 않는다 — 받지 않았으면 닫는다 |
| 닫는 중에 같은 문서를 다시 누르면 영원한 동그라미 | 닫기가 상태를 **그 자리에서** 끊고, 붙든 문서를 저장 뒤에 닫는다 |
| 판별(ZIP 중앙 디렉터리까지)이 주 스레드에서 | IO 로 옮겼다 |
| 정사각 정방향 사진은 조각이 안 왔다 | 정방향은 잴 것이 없다 — 정사각 검사보다 먼저 참이다 |
| ZIP 암호가 걸린 EPUB 을 '암호로는 열 수 없다' 로 | 공개된 방식이라 거짓이다. '다루지 않는다' 로 |

**반증조가 되던진 것**: '시험이 `/Length` 고침을 볼 수 없다'·'R2~R4 의 한글 후보에 표본이 없다'
는 결함이 아니라 시험의 빈틈이었고, '글꼴 수준의 메타데이터 스트림을 안 푼다' 는 화면에 닿는
길이 없었다. 수정마다 회귀 시험을 박았고, **V4·공백 둘은 고침을 되돌려 시험이 실제로 깨지는
것을 확인했다.**

### 11단계 뒤 — 아카이브 암호 (2026-09-23)

| 무엇 | 왜 그렇게 했나 |
|---|---|
| ZIP 복호화를 **직접 쓴다**(`ZipDecryption`) | commons-compress 는 암호 ZIP 을 읽지 못한다(`UnsupportedZipFeatureException`). 전통 암호(APPNOTE 6.1)와 WinZip AES(AE-1·AE-2) 둘 — 명세가 짧고 검증 벡터가 있다(PBKDF2 는 RFC 6070). 전통 암호는 끝의 CRC, AES 는 HMAC-SHA1 인증값으로 확인한다 |
| 7z·RAR 은 **라이브러리에 암호를 넘긴다** | commons-compress `SevenZFile.Builder.setPassword(char[])`, junrar `Archive(File, String)`. 7z 의 판정은 함정 표 |
| 암호 후보를 **셋** 시도한다 | 함정 표 — 반디집은 한글 암호를 CP949 로 넣는다 |
| 넣은 암호를 **확인하고 나서** 기억한다(`Archives.verifyPassword`) | 가장 작은 잠긴 항목 하나를 끝까지 읽어 본다. 확인 없이 기억하면 틀린 암호가 풀기·미리보기까지 흘러가 '깨진 파일' 로 끝난다 |
| 암호를 **세션 동안만** 기억한다(`SessionPasswords`) | 압축 목록·만화 뷰어·풀기 큐가 같은 파일을 따로 연다. feature 끼리 볼 수 없어 자리가 `core:io` 다(위 '암호를 다루는 규칙') |
| 풀기 요청은 **만들 때 사본을 붙든다** | 큐가 밀려 앱이 화면에서 사라진 뒤에 돌 수 있다. 쓰고 나면 푸는 쪽이, 돌지 않고 버려지면(대기 중 취소·작업 사이 취소·시작 전에 끊김) 큐가 지운다 |
| 만화는 **자기 사본**을 든다 | solid 7z 는 창을 벗어날 때마다 아카이브를 다시 연다 — 그때마다 암호가 필요하다 |
| 잠긴 쪽 판정을 **그림 이름**으로 | 예전 조건('폴더 항목이 하나도 없고 전부 잠겼다')은 쪽을 폴더에 담은 CBZ(흔하다)를 '그림이 한 장도 없습니다' 로 보냈다 — 암호를 물을 길 자체가 없었다. 고침을 되돌리면 `ComicPasswordTest` 가 깨지는 것을 확인했다 |

**오라클이 우리가 아니다.** ZIP 은 반디집(전통 암호, 영문·한글 암호)과 pyzipper(AES-128·256,
저장·deflate), 7z 는 py7zr(내용만·헤더까지·한글 암호)가 잠갔다. 만화 표본(`feature/comic/src/test/
resources/comiccrypt/`, 3개 1,315 바이트)도 두 도구가 잠그고 스스로 되읽어 확인했다(스크래치패드의
`mkcomiccrypt.py`). **RAR 은 미검증이다** — 암호 RAR 을 만들 도구가 없다.

**기기 실측**(릴리스 R8, 두 에뮬레이터 모두).

| 확인한 것 | 결과 |
|---|---|
| 일부 항목만 잠긴 zip | 목록 + 항목마다 자물쇠 + '암호가 걸린 항목이 있습니다 · 암호 넣기'. 잠긴 항목을 누르면 암호 창 |
| 틀린 암호 → 맞는 암호 | '암호가 맞지 않습니다'(입력칸이 빨갛다) → 배너와 자물쇠가 사라진다 |
| 헤더까지 잠긴 7z | 열자마자 암호 창. 취소하면 '암호를 넣어야 열 수 있는 압축 파일입니다' + '암호 넣기', 맞는 암호로 목록 |
| 그 7z 를 풀기 | '2개 완료'. 두 파일이 원본과 **바이트가 같고** 수정시각이 아카이브의 것, 임시 파일 0 |
| 잠긴 일반 zip 의 그림 항목 | 암호를 넣은 뒤 `03.png` 를 누르면 만화 뷰어가 `3 / 4` 로 열리고 캡처 색이 3쪽 색이다 — 세션 암호가 화면을 건너갔다 |
| 폴더에 쪽을 담은 잠긴 cbz | '암호가 걸린 만화' 창 → 틀린 암호 → 취소하면 '암호를 넣어야 열 수 있는 만화입니다' + '암호 넣기' → 맞는 암호로 1쪽 |
| 헤더까지 잠긴 cb7 | 맞는 암호로 1쪽, 밀면 2쪽(패스마다 다시 여는 길에 암호가 실린다) |
| 되돌아갔다 다시 열기 · 가로로 돌리기 | 다시 묻지 않는다 |
| 홈에 나갔다 돌아오기 | **다시 잠겨 있다** |
| FATAL | 0 |

**기기가 잡은 것 셋.**

1. **홈에 나갔다 돌아오면 목록이 '풀린' 채로 남았다.** 세션 암호는 지워졌는데 ViewModel 이 들고
   있던 목록은 그대로라, 풀기 대화상자는 '파일 2개' 를 말하고 실제 풀기는 둘 다 거절하는 — 서로
   다른 말을 하는 화면이었다. `SessionPasswords.clears` 로 지웠다는 사실을 알리고, 암호로 읽은
   목록(`Doc.unlocked`)은 그때 다시 읽는다.
2. **암호를 넣은 뒤에도 자물쇠가 남았다.** 배너는 사라지는데 항목마다 자물쇠가 그대로라 풀린 것을
   잠긴 것으로 읽는다. 자물쇠는 **지금 못 여는** 항목에만 단다.
3. **풀린 글 항목을 누르면 '이 앱이 풀지 않는 방식으로 잠긴 압축 파일입니다' 가 떴다.** 풀린
   항목은 종류별 안내('이 항목을 열 수 없습니다')로 간다.

**적대적 검토(구현 뒤, 네 렌즈 — 암호학·비밀 수명·상태 기계·EPUB — 와 렌즈마다 반증조 하나).**
발견 26, **확정 26, 반증 0**. 겹친 것을 합치면 결함 열다섯이다. 기기·시험이 전부 통과한 상태에서
나온 것들이고, 넷은 표본이 우리 도구로만 만들어져 드러나지 않았다.

| 무엇 | 어떻게 고쳤나 |
|---|---|
| **RAR 은 어떤 암호든 '맞다'** — 읽는 스트림이 실패를 삼킨다(함정 표). 틀린 암호가 세션에 기억되고 자물쇠가 사라졌다 | `RarArchiveReader.verifyPassword` 가 `extractFile` 로 확인한다. 읽는 스트림은 적힌 크기보다 먼저 끝나면 던진다 |
| **헤더까지 잠긴 RAR 이 암호를 묻지 않고, `.cbr` 이면 앱이 죽는다** — `RarException` 이 검사 예외다 | 리더가 `WrongPasswordException` → `ArchivePasswordException`, 나머지 → 메시지 없는 `IOException` 으로 옮긴다. RAR4 의 잠긴 헤더(틀린 열쇠로 '열리고' 항목이 0개)도 가른다 |
| **암호와 상관없는 Ultra·BCJ2 7z 의 목록이 '깨졌다'**(회귀), LZMA+AES 7z 는 막다른 화면 | 암호 없이 따로 연 파일로 항목마다 예외를 갈라 가린다(함정 표). 열쇠 유도도 하지 않는다 |
| **Info-ZIP 으로 잠근 ZIP 의 맞는 암호가 '틀렸다'** — 확인 바이트를 기기 시간대로 되만들었다 | 로컬 헤더의 날것 DOS 시각을 읽는다 |
| **한글 암호 ZIP 의 한 쪽이 틀린 열쇠로 풀린다**(1/256) | 후보를 아카이브마다 한 번 정한다 |
| 확인이 모든 `IOException` 을 '틀렸다' 로 읽었다 — 큰 항목·안쪽 압축 방식이 맞는 암호를 영원히 거절 | `PasswordCheck` 가 암호의 신호만 '틀렸다' 로 읽는다. solid 는 맨 앞 항목을 16 MiB 까지만 푼다 |
| 암호로도 못 여는 항목(강한 암호화·AES+LZMA)에 암호를 물었다 — 무엇을 넣어도 '잠김' 이 남는 고리 | `ArchiveEntry.lockedForGood`·`needsPassword`. 배너·만화의 '암호 넣기' 는 `needsPassword` 만 본다 |
| 확인하는 사이 홈으로 나가면 그 뒤의 `put` 이 앱이 뒤에 있는 내내 암호를 들었다 | `SessionPasswords.put` 이 앱이 화면에 없으면 받지 않는다 |
| 목록을 읽는 도중에 지워지면 '풀린' 목록이 섰다 | `load` 가 지운 횟수를 앞뒤로 견준다 |
| 앞 파일의 암호 창·풀기 계획이 다음 파일로 넘어갔다(액티비티에 묶인 ViewModel) | 파일이 바뀌면 둘 다 비운다. 다시 잠글 때도 계획을 비운다 |
| 암호 사본이 새는 길 넷 — 리더 생성 실패, 항목마다 만드는 `String`·후보 바이트, 맞지 않은 후보의 열쇠, PBKDF2 중간값 | 사본을 연 다음에 만들고, 모든 실패에서 덮는다. 후보는 `CharsetEncoder` 로 |
| 암호 deflate 항목마다 네이티브 zlib 상태가 GC 를 기다렸다 | 닫을 때 `Inflater.end()` |
| EPUB: 열쇠를 못 만드는 난독화 **본문**을 열었다 · `EncryptedData` 가 없는 파일을 거절했다 · 가리키는 곳 없는 블록 하나를 놓쳤다 · 식별자 끝의 NBSP 를 벗겨 열쇠가 틀렸다 | 본문이면 잠긴 책으로 · 없으면 연다 · 블록마다 센다 · XML 공백 넷만 벗긴다 |

회귀 시험 아홉(아카이브 5, EPUB 4)을 박았다. 아카이브 표본 다섯은 스크래치패드의 `mkreviewcrypt.py`
가 만들었다 — ZipCrypto 둘은 우리 암호기로 잠갔으므로 **파이썬 표준 `zipfile` 이 푸는 것**으로
확인했고(한글 표본은 UTF-8 로 풀면 'Bad CRC-32' 로 걸리는 것까지), 7z 둘은 py7zr, AES+LZMA 는
pyzipper 가 만들었다. **확인 바이트·후보 결정·7z 가리기 셋은 고침을 되돌려 해당 시험이 정확히 그것만
깨지는 것을 확인했다.** RAR 둘은 여전히 표본이 없어 **junrar 소스를 읽고 짠 코드**다.

### 12단계가 세운 것 — docx·xlsx·pptx 와 암호 OOXML (2026-09-24)

**쪽을 재현하지 않고 흐름으로 그린다**(아래 '지원하지 않는 것'). 그래서 세 포맷이 내놓는 것은 11단계의
EPUB 과 같이 **위생을 거친 HTML** 이고, 화면은 EPUB 의 칸 나눔 구조를 그대로 쓴다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 화면이 아는 것은 **`FlowDocument` 계약 하나**(`format:api`) | 문서 화면은 포맷 모듈을 하나만 볼 수 있고 그 하나가 이미 `format:epub` 이다(의존 표). docx·xlsx·pptx·HWPX 가 각자 화면을 요구하면 규칙이 무너진다. 13단계의 HWPX 는 이 화면을 고치지 않고 붙는다 |
| 세는 단위가 **부분**(part) | docx 는 본문(길면 조각), xlsx 는 시트, pptx 는 슬라이드. 이어보기가 저장하는 것도 이 번호다(`doc_progress.page`) — 스키마를 바꾸지 않았다 |
| 바탕 클래스 `OpcFlowDocument` 에 **지킬 것을 모았다** | 잠금(WebView 의 다른 스레드), 닫힌 뒤 null, 위생, 그릴 수 있는 그림만 내주기, 부분 하나의 실패는 부분 하나로, 캐시 셋, 버린 것은 부분마다 한 번만 세기. 세 변환기가 각자 지키면 한 곳이 빠뜨린다 — 실제로 검토가 '다시 그릴 때마다 배지가 는다' 를 셋 모두에서 찾았고 바탕 한 곳에서 고쳤다 |
| 변환기는 **`HtmlWriter` 로만 쓴다** | 문서의 글자를 HTML·CSS 에 이어 붙이는 길을 없앤다. 글자는 `text`, 속성 값은 인자, 스타일 값은 `CssValues` 가 만든다. 그래도 결과는 위생기를 한 번 더 지난다(겹친 방어) |
| 여는 일은 **한 여는이**(`OoxmlOpener`) | 판별기가 확장자로 짐작한 종류가 아니라 **패키지가 말하는 종류**로 연다. 암호가 걸린 파일은 풀기 전까지 속을 볼 수 없어 판별기가 확장자에 기댈 수밖에 없기 때문이다. `format:opc` 는 변환기를 모르고 `app/FormatRegistry` 가 셋을 이어 준다 |
| 암호 OOXML 은 **`format:cfb` 위의 `OfficeCfb`** | MS-OFFCRYPTO 의 Agile(4.4 — AES·SHA-1~512)과 Standard(AES-128/192/256 ECB). RC4·Extensible 은 '다루지 않는다', IRM·인증서는 '암호로는 열 수 없다'. 풀린 패키지는 **메모리에만** 두고 문서를 닫을 때 0 으로 덮는다(`OpcPackage.wipeOnClose`) |
| CFB 는 **HWP 도 쓸 모양**으로 | `CfbFile` 은 포맷을 모른다. 13단계의 HWP 5.0 이 그대로 선다. 형식 오류는 `CfbFormatException : CorruptFormatException` 이라 공용 `toOpenFailure()` 가 '입출력 실패' 가 아니라 '깨진 파일' 로 옮긴다 |
| `VelvetSweatshop` 을 **먼저** 시도한다 | 엑셀의 '읽기 전용 권장' 파일은 이 기본 암호로 잠겨 있다(명세에 있다). 그것을 사용자에게 물으면 있지도 않은 암호를 요구하는 것이다 — 11단계 PDF 의 '소유자 암호만' 과 같은 판단 |
| docx 는 **목록 번호를 우리가 센다** | 문단마다 `<ol>` 을 이어 붙이는 대신 (번호 정의, 수준)마다 셈을 들고 표지(`1.`·`가.`·`①`)를 글자로 적는다. 흐름 파서로 중첩 목록을 세우면 깨지고, 부분을 나눠도 번호가 이어져야 한다(조각 경계의 상태를 훑기가 찍어 둔다) |
| docx 의 표는 **훑을 때 병합을 잰다** | 세로 병합(`vMerge`)은 뒤 행을 봐야 `rowspan` 이 나온다. 표를 통째로 버퍼에 담지 않고, 여는 때의 훑기가 재 둔 값을 그리기가 읽는다 |
| xlsx 의 표시 형식은 **순수 클래스**(`NumberFormat`) | 날짜 일련번호(1900 윤년 버그·1904), 구역·조건·색·분수·지수·General 11자. 시험이 가장 많은 곳이다 — 틀리면 숫자가 조용히 다르게 보인다 |
| xlsx 는 **2,000행·5만 칸**에서 자른다 | 아래 실측. 값을 정한 것은 변환이 아니라 WebView 의 표 배치다. 자른 자리 뒤에 **보이는 값이 남았을 때만** 시트 이름과 함께 알린다(빈 칸·숨긴 행에 테두리만 있는 시트가 흔하다 — 말뭉치 XM01) |
| pptx 는 **길이를 슬라이드 단위로** 적는다 | `calc(var(--u)*N)` — `--u` 가 세로 화면에서는 `1vw`, 가로 화면에서는 `1vh × 폭/높이`. 슬라이드 한 장이 언제나 한 화면에 든다(폭에만 맞췄더니 가로 태블릿에서 4:3 의 아래쪽이 화면 밖이었다) |
| pptx 는 **자리 틀을 물려받는다** | 슬라이드의 자리 틀은 좌표를 레이아웃·마스터에서 받고, 글자 서식은 마스터의 `txStyles` 까지 올라간다. 이것 없이는 제목이 (0,0) 에 뜬다 |
| 첫 진입에 **한 번** 알린다 | 요구사항의 '첫 진입 고지'. 문서마다 처음 한 번(자리 기록이 없을 때) — 종류마다 한계가 달라서(글: 쪽 모양 / 시트: 수식 재계산·차트 / 슬라이드: 글꼴·줄바꿈) 문장도 셋이다 |
| 경고는 **코드로**, 문장은 화면이 | `FlowWarnings` 에 코드를 두고 `DocNotice` 가 문장을 고른다. 이름 없는 부분은 번호를 싣는다(`partName`) — 빈 문자열이면 서로 다른 부분의 경고가 하나로 합쳐졌다 |
| 보조 부분의 실패를 **말한다** | 스타일·번호 매기기·공유 문자열을 못 읽으면 본문은 기본 서식으로 그리되 `AUX_FAILED` 를 남긴다. 조용히 넘기면 사용자는 문서가 원래 그렇게 생긴 줄 안다 |

**원장(인계 계약)을 써서 넷이 나눠 만들었다** — docx·xlsx·pptx·CFB/암호를 각각 저장소의 **복사본**에서
만들고(같은 파일을 두 손이 만지지 않게), 모듈마다 구현 뒤에 따로 검토·수정하게 했다. 원장은 저장소에
남기지 않았다. 원장과 다르게 정한 것 가운데 남길 만한 것:

- docx 의 `mc:AlternateContent` 는 '대체본 먼저' 가 아니라 명세대로 **알아듣는 첫 `Choice`**, 없으면 `Fallback`.
- docx 의 글자 크기는 절대 pt 가 아니라 **문서 기본 크기에 대한 %** — WebView 의 기본 글꼴과 확대가 주도권을 갖는다.
- docx 의 쪽·단 나눔은 **아무것도 그리지 않는다**(`<p>` 안의 `<hr>` 는 문단을 쪼갠다).
- xlsx 의 열 폭은 파일 단위(기본 글꼴의 숫자 폭) × **Roboto 의 숫자 폭 0.56em** — 기기에 Calibri 가 없다. 기기에서 재지 않았다.
- xlsx 의 테마 색 번호는 **0=lt1, 1=dk1**(엑셀의 관례, 명세의 순서와 다르다). openpyxl 이 쓴 파일로 확인했다.
- pptx 의 반투명은 `CssValues.hexColor` 가 알파를 버려서 **숫자로 만든 `rgba()`** 를 쓴다.

**구현 뒤 모듈마다 적대적 검토를 돌렸다.** 발견 36(치명 1, 중대 9, 경미 26), 고친 것 27(치명·중대 전부).

| 무엇 | 어떻게 잡혔나 |
|---|---|
| **docx 를 여는 순간 메모리가 넘쳤다** | 조각 경계마다 목록 번호 상태를 통째로 복사했다 — 번호 정의가 많은 문서에서 조각 수 × 정의 수. 5.8 MB 짜리 '목록 폭탄' 표본으로 재현했다 |
| `HtmlWriter` 가 상한을 넘은 뒤에도 닫는 태그를 썼다 | 멈춘 뒤에 연 태그도 '열린 것' 으로 세어 닫는 태그만 쌓였다. 바탕에서 고쳤다(쓴 태그만 닫는다) |
| 그림의 대체 글(`descr`)에 상한이 없었다 | 본문이 위생기의 입력 상한을 넘어 `partHtml` 밖으로 예외가 나갔다 — 화면은 404 로 삼켜 **빈 화면에 아무 말도 없다**. 변환기에서 자르고, 바탕도 그 예외를 '너무 크다' 로 받는다 |
| 관계 찾기·자리 틀 맞추기가 제곱이었다(pptx) | 달린 줄마다 관계 목록을 처음부터 훑었다. 시간 시험으로 박았다 |
| 레이아웃·마스터 캐시가 개수로만 묶였다(pptx) | 항목 하나가 100 MB 일 수 있다. 무게로 묶었다 |
| xlsx 의 분수 표시(`# ?/10`)가 틀린 값을 보였다 | 분모에 0 이 든 고정 분모. 시험으로 박았다 |
| CFB: 열쇠 유도가 취소될 때 반쯤 계산한 해시를 덮지 않았다 | 경미. 나머지는 시험의 빈틈(미니 스트림 4096 경계, 풀린 것이 ZIP 이 아닐 때) — 14단계가 둘 다 메웠다(해시 덮기는 이미 고쳐져 있었다) |

**통합한 뒤 바탕·화면에서 더 고친 것**(검토가 '바탕은 내 몫이 아니다' 로 넘긴 것들).

| 무엇 | 왜 |
|---|---|
| 그리는 동안 깨진 관계 파일의 경고가 **화면에 닿지 않았다** | 문서가 만들 때 패키지의 경고를 한 번 베껴 두었다. 관계는 그림을 찾을 때 처음 읽힌다 — 이제 읽을 때마다 합친다 |
| 다른 부분의 각주·책갈피 링크가 **그 부분의 맨 위**에 떨어졌다 | `onNavigate` 가 경로만 받았다. 조각(`#`)을 함께 건넨다. EPUB 의 장 사이 각주도 같은 결함이었다 |
| **알림이 화면 맨 위에 떠서 제목 막대를 가렸다** | 칸으로 나눈 화면(`Column`) 옆에 `SnackbarHost` 를 두어 부모가 정렬을 몰랐다. 11단계의 EPUB 도 같았다 — 기기 캡처에서 잡았다 |
| 큰 시트가 **45초 동안 '여는 중'** | 아래 실측. `onPageFinished` 는 문서 전체의 배치가 끝나야 온다. 첫 그림(`onPageCommitVisible`)에 걷는다 |
| 풀린 평문 패키지를 **GC 에 맡겼다** | 닫을 때(여는 데 실패해도) 0 으로 덮는다 |
| 평문 상한이 `PdfLimits` 에 있었다 | 이름이 뜻을 속였다. `DecryptLimits` 로 옮기고 PDF·OOXML 이 같이 쓴다 |
| 엑셀 4 매크로 시트를 '알 수 없는 요소' 로 셌다 | 매크로다 |

**그다음 통합한 코드를 다시 적대적으로 검토했다**(네 렌즈 — 화면·바탕·변환기·문서 — 와 렌즈마다
반증조 하나, 읽기 전용). 발견 21, 반증 3, 겹친 것을 합치면 **결함 열다섯**(중대 셋)이고 전부 고쳤다.

| 무엇 | 어떻게 고쳤나 |
|---|---|
| **여는 일이 주 스레드에서 돌았다**(OOXML·EPUB) | 여는이 대부분이 디스패처를 고르지 않는 순수 JVM 이다. 큰 문서에서 화면이 멎고 시간 상한도 뒤로가기도 끼어들 틈이 없었다. `DocViewModel` 이 `Documents.open` 을 `IroDispatchers.parsing` 에서 부른다 — 11단계의 EPUB 도 같은 결함이었다 |
| **닫기가 주 스레드에서 그리기 잠금을 기다렸다** | 큰 시트를 그리는 중에 뒤로 가면 화면이 멎는다(데스크톱에서 0.7초, 폰은 몇 배). 앱 수명의 IO 스코프에서 닫고, 닫기가 청해지면 줄 선 부분 요청이 그리기 전에 돌아선다(`closeRequested`) |
| **매크로·서식 파일(`.docm`·`.xlsm`·`.pptm`·`.dotx`…)이 뷰어에 닿지 않았다** | 판별기는 여는데 목록이 '기타' 로 그렸다(`MimeResolver`). 종류에 더했다 |
| '2024' 라는 시트가 알림에서 '2024번째 부분' | 이름 없는 부분의 번호에 U+0001 앞머리를 붙인다(`FlowWarnings.PART_NUMBER_PREFIX`) — XML 글자에 들어올 수 없는 문자라 어떤 이름도 그것으로 시작하지 않는다 |
| 시트 이름을 다듬지 않고 화면에 냈다 | 방향 바꾸기 문자가 알림 문장을 거꾸로 보이게 할 수 있다. 제어 문자·겹친 공백·120자(`SheetNames.display`). 링크를 찾는 열쇠는 적힌 이름 그대로 |
| 링크가 남긴 자리가 목차·'처음부터' 뒤에도 남았다 | 고른 장의 처음이 아니라 옛 각주 자리로 갔다. 자리를 화면 위로 올려 목차·처음부터에서 비운다 |
| 첫 진입 고지가 되풀이될 수 있었다 | 기록을 닫을 때만 썼다. 처음 연 순간에 남긴다 |
| `.xlsm` 의 매크로 시트를 두 번 셌다 · 칸 상한에서 빈 행 머리 · 실패한 슬라이드의 ⚠ 가 어두운 바탕에 묻혔다 · pptx 가 레이아웃·테마를 못 읽어도 말하지 않았다 · 공유 문자열을 잃은 것을 '서식' 으로만 알렸다 | 각각 고쳤다(시험 넷) |
| `failureText` 의 KDoc(보안 규칙)이 새 상수에 밀려 떨어졌다 · `CfbLimits` 의 KDoc 이 사실과 달랐다 | 되돌렸다 |

### 12단계 실측 (두 에뮬레이터, 릴리스 R8)

표본은 python-docx·openpyxl·python-pptx 가 만들고 msoffcrypto-tool 이 잠갔다(스크래치패드의 `mkoffice.py`).
**그 도구가 스스로 푼 것이 원본과 같은지부터** 확인했다(11단계 pikepdf 교훈).

| 확인한 것 | 결과 |
|---|---|
| docx | 제목·굵게·기울임·빨간 글자·번호 목록·글머리표·가로 병합 칸·그림·가운데 정렬. 목차 여섯 줄이 들여쓰기로 뜨고, 고르면 **그 제목으로** 간다 |
| 긴 docx(2,920문단) | 두 부분으로 나뉘고 아래 막대가 `2 / 2 부분 · 목차` |
| xlsx | 머리 칸 채우기, `#,##0`·`0.0%`·`#,##0"원"`, 합친 칸(A6:D6) 가운데, 멀리 떨어진 H20. **숨긴 시트는 빠진다**(시트 넷 중 셋) |
| pptx | 제목 슬라이드·글머리표 세 수준(마스터의 수준 2 표지 '–')·그림·채운 도형·표. 가로 태블릿에서 **슬라이드 한 장이 한 화면에** 들고 좌우는 어두운 회색 |
| 40장짜리 pptx | 넘기기 1 → 4, 제목 `& <특수>` 가 한 번만 이스케이프되어 보인다 |
| 암호 docx(Agile) | 틀린 암호에 '암호가 맞지 않습니다', 맞는 암호로 본문 |
| 한글 암호 xlsx(Agile, `암호123`) · Standard AES-128 docx | 연다 |
| `VelvetSweatshop` 으로 잠긴 xlsx | **묻지 않고** 연다 |
| `.doc`·`.xls`(합성 CFB) | '이전 오피스 형식(.doc·.xls·.ppt)은 열지 않습니다…' |
| IRM 표본 · 인증서만 있는 Agile | '암호로는 열 수 없는 방식으로 잠긴 문서입니다'(암호를 묻지 않는다) |
| 첫 진입 고지 | 처음 연 문서에서만, 아래 막대 위에. 두 번째부터는 뜨지 않는다 |
| 이어보기 | 2부분에서 나갔다 다시 열면 `2번째 부분부터 이어서 봅니다` |
| `.docm`·`.xlsm` | 목록에서 눌러 곧바로 문서·시트로 열린다(고치기 전에는 '기타' 라 뷰어에 닿지 않았다) |
| 큰 시트를 그리는 중에 뒤로 | 0.27초 만에 목록이 돌아오고 멎지 않는다. ANR 0 |
| FATAL | 0(두 기기) |

**큰 시트의 배치 시간**(20열, 태블릿 에뮬레이터, 누른 때부터).

| 그리는 행 | 표가 보인 때 | 배치가 끝난 때(`onPageFinished`) |
|---|---|---|
| 1,000 | — | 4.6초 |
| 2,000 | — | 10.3초 |
| 5,000(칸 10만, 옛 상한) | **3.5초** | **44.6초**(폰 17.6초) |
| 상한을 2,000행으로 내리고 첫 그림에 걷은 뒤(3만 행 파일) | 2.4초 | 동그라미 2.4초(폰 4.4초) |

변환은 5,000행에서도 JVM 으로 0.6~1.2초였다 — **느린 것은 WebView 의 표 배치다.** 행 머리를 `sticky` 로
둔 것이 원인인지 빼고 재 보았는데 오히려 55초였다(원인이 아니다). 에뮬레이터 값이고 실기기는 다를 것이다.

### 12단계에서 미룬 것

| 미룬 것 | 왜 |
|---|---|
| **머리말·꼬리말·메모의 내용** | 센다(배지). 쪽 재현을 포기했으므로 머리말을 둘 자리가 없다. **docx 의 메모는 14단계가 그린다** — 메모가 달린 범위 끝의 작은 표지(`[머리글자+번호]`)에서 부분 끝의 메모 쪽으로 오간다. 보인 메모는 세지 않는다. xlsx·pptx 의 메모는 여전히 센다(XM01 은 139) |
| **차트** | 빈 상자(pptx)나 배지로 센다. 차트 데이터로 그림을 다시 그리는 것은 작은 차트 엔진이다 |
| **수식 재계산** | 저장된 값을 보인다. 값이 없는 수식은 '계산되지 않은 수식' 으로 센다 |
| xlsx 의 조건부 서식·틀 고정·셀 안 서식 조각 | 값은 가리지 않는다. 틀 고정은 A/1 머리만 붙어 있다 |
| pptx 의 애니메이션·전환·그라데이션(첫 색만)·효과·사용자 도형 | 센다 |
| docx 의 표 스타일·문단 간격·테마 글꼴과 색 | 기본 서식으로 그린다 |
| 레거시 doc·xls·ppt | 1단계에서 제외했다. '이전 형식' 으로 정확히 말한다 |
| RC4·Extensible 암호 OOXML | '다루지 않는다'. 오피스 2007 이후 기본값이 아니다 |
| ~~**실제 오피스가 만든 파일**~~ | **했다**(2026-09-25) — POI·Tika·LibreOffice 의 시험 표본 81건(워드·엑셀·파워포인트·LibreOffice 가 만든 것, 암호·이전 형식·깨진 것 포함)이 전부 기대대로 열린다. 아래 '실세계 말뭉치' 절 |
| **Android 의 XML 파서가 잘린 XML 을 던지는가** | JVM 시험의 kxml2 2.3.0 은 요소 안에서 잘린 XML 을 조용히 `END_DOCUMENT` 로 준다. 안드로이드의 것은 던진다고 알려져 있으나 **기기에서 확인하지 않았다** — 그 차이 때문에 시험과 기기의 '부분 실패' 판정이 갈릴 수 있다 |

### 13단계가 세운 것 — HWPX 와 HWP 5.0 (2026-09-24)

**선행조건(표본 20~30개)을 먼저 채웠다.** 조사원 넷(HWP 5.0 명세 요약·HWPX 구조·표본 후보·오라클 도구)과
반증조 넷이 읽기만 한 조사로 후보 73건을 모았고, 사용자가 30건을 승인했다. 받은 것은 `samples-local/hwp/`
(커밋하지 않는다)의 30건 108 MB 와 `SOURCES.md`(원래 이름·출처 URL·라이선스·SHA-256)다. **둘은 한글이
아니었다** — K35 는 `.hwpx` 이름의 HTML 쪽(음성 표본으로 둔다), S06 은 PDF 였다(폴더에는 두되 한글 표본에서 뺐다). 대부분이 정책브리핑
(korea.kr) 보도자료 첨부이고 공공누리 1유형(텍스트에 한함)이다.

**오라클이 우리가 아니다.** 정책브리핑이 첨부마다 내주는 '바로보기'(사이트의 변환기)가 같은 파일을 HTML 로
보여 주므로, 그 글을 읽어 와(업로드 없음) 우리 추출과 견줬다(`samples-local/hwp/oracle/`). 그리고 **같은 보도자료가
HWP 와 HWPX 로 함께 올라온 짝 셋**(K01/K25·K11/K33·K19/K27)으로 우리 두 변환기가 서로를 견줬다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 흐름 문서 바탕을 `format:html` 로 옮겼다(`FlowDocumentBase<P : FlowPackage>`) | 12단계의 `OpcFlowDocument` 는 OPC 꾸러미에 묶여 있었다. HWPX(ZIP + OWPML 매니페스트)와 HWP(CFB 의 `BinData`)도 같은 약속(잠금·위생·그림만 내주기·부분 실패·한 번만 세기)이 필요하다 — 꾸러미를 인터페이스(`FlowPackage`) 하나로 추렸다. `OpcFlowDocument` 는 얇은 자리로 남았다(docx·xlsx·pptx 는 고치지 않았다) |
| **HWPX 판별기가 EPUB 판별기보다 먼저 돈다** | HWPX 에도 `mimetype` 과 `META-INF/container.xml` 이 있어, 이름만 보는 EPUB 판별기가 한글 문서를 전자책으로 열 참이었다(배선하다 잡았다). HWPX 는 저장된 `mimetype` 의 내용(`application/hwp+zip`)이나 `Contents/content.hpf` 로 가른다 |
| HWP 판별은 **CFB + 한글 확장자**, 속은 여는이가 본다 | `.hwpx` 이름에 HWP 5.0 이 든 공공 문서가 실제로 있다(K36). 한글 97 이전(HWP 3.0)은 앞머리로 알아보고 '이전 형식' 으로 말한다 |
| 암호 HWPX 는 **연다** | 방식이 한컴이 공개한 OWPML 모델(Apache-2.0)에 있다 — SHA-256(암호) → PBKDF2-HMAC-SHA1 → AES-CBC → 해제 → 평문 앞 1 KiB 의 SHA-256 확인. 한컴의 쓰기 코드가 소금과 IV 를 같은 초의 `srand(time)` 로 만들어 **같을 수 있다** — 그것을 손상으로 보지 않는다 |
| HWP 배포용 문서는 **암호 없이 연다** | 열쇠가 파일 안에 있고 방식이 한컴의 별도 공개 문서에 있다(pyhwp·hwplib·rhwp 가 같은 방식). 256바이트 레코드 → MSVC 난수로 풀기 → SHA-1 16진 문자열의 앞 16바이트 → AES-128-ECB → 해제 |
| HWP 의 암호 문서는 **열지 않는다, 묻지도 않는다** | 한컴이 방식을 공개하지 않았다. 역공학한 구현(rhwp)이 있지만 '공개 명세' 가 아니다(위 '암호가 걸린 파일' 의 판단 기준). '암호로는 열 수 없는 방식' 으로 끝난다 |
| 문단 여백은 **HWPUNIT 의 두 배**로 적혀 있다 | 명세는 말하지 않는다. HWPX 의 `hp:switch` 가운데 `hp:case`(HwpUnitChar)가 진짜 HWPUNIT 이고 `hp:default`·옛 파일의 값·HWP 5.0 의 값이 그 두 배다 — 오라클의 pt 와 짝 문서로 확인했다(K01 의 문단 모양 327개 — 여백 조합 200가지 — 가 K25 의 `hp:default` 와 번호마다 전부 같고 `hp:case` 는 정확히 절반이다) |
| 한컴의 사용자 영역 문자(U+F02B1… 등)를 **보이는 글자로** 바꾼다 | 폰에 한컴 글꼴이 없어 절 번호가 빈 네모로 떴다(검토가 잡았다). 표는 오라클 변환기가 보여 준 글자에서 얻었다 — 한컴의 공개 표는 찾지 못했다. **두 변환기가 한 표를 쓴다**(`format:html` 의 `HancomChars`·`BulletGlyphs`) — 처음에는 HWP 쪽에만 있어 같은 보도자료의 HWPX 가 두부였다(아래 '기기가 잡은 것') |
| 표는 **칸마다 60자로 쳐서** 부분을 나눈다 | 글자 수만 세면 표로만 된 13 MB 절(K25)이 한 부분이 되어 WebView 배치가 멎는다(12단계 xlsx 실측과 같은 까닭) |
| 한컴 문서 명세의 **고지 문구**를 14단계에 넘긴다 | 한컴의 명세 사용 조건이 제품의 화면·설명서·소스에 '본 제품은 한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다.' 를 요구한다. `Hwp5Opener` 의 KDoc 에 적었고, **14단계가 고지 화면에 넣었다**(첫 음절의 옛한글을 그때 바로잡았다 — 함정 표) |

**구현 뒤 모듈마다 적대적 검토.** HWPX 발견 7(중대 1, 고침 6), HWP 발견 10(중대 2, 고침 6).

| 무엇 | 어떻게 잡혔나 |
|---|---|
| HWPX: 암호 항목을 쓸 데와 상관없이 **통째로 풀었다** | 비밀번호 확인 한 번에 29.7 MB 를 다 해제했다. 필요한 만큼만 푼다 |
| HWP: 사용자 영역 문자·소프트 하이픈이 **빈 네모** | 위 표 |
| HWP: 파일에 적힌 **행·열 수가 CPU 와 메모리를 몰았다** | 빈 표도 적힌 크기만큼 칸을 만들었다. 실제로 있는 칸으로 묶는다 |
| HWPX: PBKDF2 의 총량이 항목 수만큼 불었다 · 32 MiB 를 넘는 그림에 `<img>` 를 썼다 · 암호문 미리보기를 글로 보였다 · 쓰레기 `opf:title` 이 제목을 덮었다(뒤에 제목을 아예 쓰지 않기로 했다 — 아래) · 길이 0 인 암호 항목이 아무 암호나 받았다 | 각각 고쳤다 |
| HWP: 글꼴 수 음수로 DocInfo 전체를 버렸다 · 방향 바꾸기 문자가 목차에 남았다 · 각주 번호가 본문 순서를 바꿨다 | 각각 고쳤다 |
| 바탕: `UnsupportedFeatures` 가 **잠금 없이** 두 스레드에서 쓰였다 | WebView 스레드가 세고 주 스레드가 읽는다. 11단계의 EPUB 부터 그랬다 — 잠금을 걸었다 |

### 13단계 실측

**오라클 대조**(글자 3-gram 또는 낱말, 정규화).

| | 표본 | 결과 |
|---|---|---|
| HWP 5.0 ↔ 정책브리핑 바로보기 | K 12건 | 재현율 0.999~1.000, 정밀도 0.996~1.000 |
| HWPX ↔ 정책브리핑 바로보기 | K 6건 | 재현율 0.994~0.997(가장 낮은 K33), 정밀도 0.992~0.999(가장 낮은 K27). 모자란 것은 쪽마다의 머리말·꼬리말과 차례의 탭 채움 '···' 이다 |
| **우리 HWP ↔ 우리 HWPX**(같은 보도자료) | K01/K25 · K11/K33 · K19/K27 | 글자 3-gram(공백 제외) 1.0000 · 1.0000 · 0.9999. 표 243개의 행·칸·병합이 짝끼리 전부 같다 |

값은 13단계 끝의 것이다(아래 구조 대조의 수정을 다 들인 뒤). 처음 잰 값은 HWP 재현율이 0.991(K11)까지 내려갔는데, 한컴 글자
표·양쪽 정렬을 고치며 올라갔다 — 실물 시험의 문턱(재현율 0.995·정밀도 0.99)은 지금 값에 맞춘 것이다.

**기기**(폰 API 31 에서 15건, 태블릿 API 35 에서 6건, 릴리스 R8).

| 확인한 것 | 결과 |
|---|---|
| K01(HWP, 3.3 MB) | 6부분, 표·로고·배너 그림, 공공누리 표지 |
| K25(HWPX, 4.7 MB) | 4부분(13단계 끝) → **6부분**(짝 대조 셋째 검토가 부분 나누기를 HWP 와 맞춘 뒤, 2026-09-27) |
| S01(한컴 명세, **배포용**) | 암호 없이 풀려 9부분, 표지 그림과 제목 표 |
| K36(`.hwpx` 이름의 HWP 5.0) | 열린다 |
| O16(암호 HWPX) | 암호 창 → `123456` 으로 열린다 |
| O08(암호 HWP) | '암호로는 열 수 없는 방식으로 잠긴 문서입니다' — 묻지 않는다 |
| K35(`.hwpx` 이름의 HTML) | '이 앱이 다루지 않는 문서입니다' |
| K05(HWP 33 MB) · K28(HWPX 29.7 MB) | 열린다 |
| FATAL·ANR | 0 |

JVM 에서 K05 는 열기 127 ms·그리기 54 ms, K28 은 열기 237 ms·그리기 58 ms(데스크톱 값).

**기기가 잡은 것**(두 기기의 캡처를 눈으로 검토했다 — 10단계 교훈).

| 무엇 | 원인 |
|---|---|
| HWPX 문단이 폰에서 **낱말 사이가 크게 벌어졌다**(O16) | HWPX 변환기는 `JUSTIFY` 를 `text-align:justify` 로 옮기고 HWP 5.0 변환기는 일부러 적지 않았다 — **같은 문서가 포맷에 따라 달리 보였다.** 한글 문서는 거의 모든 문단이 양쪽 정렬이라 `word-break:keep-all` 과 만나 좁은 화면에서 줄마다 틈이 생긴다. 이제 두 변환기 모두 `JUSTIFY`·`LEFT` 를 적지 않는다(배분·나눔은 justify 로, 가운데·오른쪽은 그대로 옮긴다) |
| HWPX 제목 막대에 **'공표용 보도자료'**(K27) | 서식 파일에서 물려받은 `opf:title` 이다. 처음에는 글자 없는 제목(`3`·`1111`)과 사설 영역 글자만 걸렀는데 **그럴듯한 낡은 제목은 거를 수 없다.** HWP 5.0 처럼 제목을 비워 파일 이름이 보이게 했다 |
| HWPX 의 **절 번호가 두부**(K27·K33) | 한컴의 네모 숫자(`U+F02B1`~)를 옮기는 표가 HWP 5.0 변환기에만 있었다 — 같은 보도자료의 HWP(K11·K19)는 `１ 신규 플레이어 진입`·`１ 공공부문 일자리 규모 및 비율` 이었다. 위 정렬을 고친 뒤 **같은 종류의 어긋남이 더 있는가** 를 코드에서 찾다가 잡았다. 표를 `format:html` 로 옮겨 두 변환기가 하나를 쓰고, 실물 시험이 옮기지 않은 한컴 글자가 남지 않았는지 본다. 글머리표도 같았다(HWPX 는 기호 글꼴 글자를 전부 `•` 로, HWP 는 `▪` 등 뜻이 같은 글자로) |
| 아래 막대 글자에 `&#9;` | 앱이 아니라 **시험 스크립트의 것**이다(함정 표 — uiautomator 가 탭을 문자 참조로 적는다) |

**짝 문서의 HTML 을 구조로 견줬다.** 위 셋이 모두 '두 변환기가 같은 것을 달리 판단한' 형태라, 같은 보도자료의 HWP·HWPX 짝
셋(K01/K25 · K11/K33 · K19/K27)을 태그·CSS 속성의 분포와 글로 맞춘 블록마다의 스타일로 견줬다. **글자 대조가 0.99 를 넘는 짝에서**
어긋남 여섯이 더 나왔다.

| 무엇 | 어느 쪽이 옳았나 |
|---|---|
| 취소선 172곳(K19)이 HWP 에만 | **HWPX.** '보도자료'·'배포'·보도 날짜에 줄이 그어졌다. 글자 모양이 '여부 1 · 밑줄 자리 가운데 · 모양 3D 단선' 이고, 한컴의 HWPX 내보내기는 같은 모양을 `strikeout shape="3D"` 로 적는다(글자 모양 번호마다 대조했다). HWPX 쪽의 옛 설명('꺼진 취소선의 모양만 남았다')도 틀렸다 — 두 포맷 모두 '켜짐' 이다. 두 변환기 모두 3D 단선을 꺼짐으로 본다 |
| 흰 글자 보호가 HWP 에만 | **HWP.** HWPX 는 바탕 없는 칸의 흰 글자를 그대로 적어 흰 화면에서 사라졌다(K25). 알려진 바탕색(칸의 면 색·글상자의 채우기)을 들고 밝은 바탕 위의 흰 글자는 기본 색으로 둔다. HWPX 는 글상자의 채우기도 이제 칠한다 — 파란 표지 상자 위의 흰 글자가 살아난다 |
| 흰 칸 배경 3,031곳(K27)이 HWPX 에만 | **HWP.** 화면이 이미 희다 — 적지 않는다(바탕색으로는 든다) |
| 빈 칸 144개(K19)가 HWP 에서 줄 없이 납작했다 | **HWPX.** 빈 문단 세기(셋까지)를 표 전체로 하면 넷째 빈 칸부터 줄이 사라진다. 칸마다 새로 센다 |
| 그림 대체 글 20개(K19)가 HWP 에만 — `그림입니다. 원본 그림의 이름: CLP….bmp …` | **HWPX.** 한글이 저절로 넣는 설명문이라 화면 낭독기가 파일 이름을 읽고, 그림이 깨지면 그 글이 화면에 뜬다. 첫 줄이 개체 종류의 이름 + `입니다.` 이고 뒤 줄이 전부 `열쇠: 값` 인 것만 버린다 — 표본의 설명문 205개가 전부 그 틀이었고, 사진은 EXIF 줄(`사진 찍은 날짜`·`프로그램 이름`)이 더 붙는다. 사람이 쓴 설명은 남긴다(모양으로 짐작한 규칙) |
| 들여쓰기·간격·글자 크기 상한이 달랐다(HWPX 400pt·200pt·500%) | **HWP 쪽 값**(200pt·72pt·400%)으로 맞췄다. 폰 화면의 폭이 300pt 남짓이다 |

그다음 **이 수정들을 다시 적대적으로 검토했다**(렌즈 넷 — 정확성·일관성·보안·문서 — 와 반증조). 수정이 남긴 결함과 같은
종류의 어긋남이 더 나왔다.

| 무엇 | 어떻게 고쳤나 |
|---|---|
| **흰 글자 규칙이 글자 자신의 음영·형광펜을 보지 않았다** — 남색 음영·검은 형광펜 위의 흰 글자가 검게 사라졌다(수정이 만든 회귀) | 두 변환기 모두 글자의 음영(HWPX 는 형광펜도)을 바탕으로 본다 |
| **어두운 칸 안의 흰 칸**이 흰색을 잃어 투명해졌다(회귀) | 흰 면 색은 **둘러싼 바탕이 흰 화면일 때만** 뺀다 |
| HWPX 각주 본문이 부른 칸의 바탕을 물려받아 흰 글자를 흰 화면에 적었다 | 각주는 바탕 없이 걷는다(HWP 는 처음부터 새 걷개다) |
| 칸 안의 빈 줄 수가 표 밖까지 이어져 **표 뒤의 빈 문단**이 사라졌다(두 변환기) | 표가 끝나면 새로 센다 |
| 그림 설명 규칙이 EXIF 줄이 붙은 자동 설명(표본 32개)을 남기고, 사람이 쓴 짧은 '조직도입니다.' 를 버렸다 | 위 표의 규칙으로 좁혔다 |
| **글꼴** — HWP 는 한컴 글꼴 이름을 그대로(기기에 없어 전부 고딕), HWPX 는 명조 계열을 `serif` 로 | 두 변환기가 `format:html` 의 `HancomFonts` 로 명조·고딕만 옮긴다. K01 의 HTML 이 2.28 → 1.97 MB 로 줄었다 |
| **칸 세로 정렬** — HWPX 가 적지 않아 K27 의 4,578칸 중 46칸(위 41·아래 5)이 가운데로 | `hp:subList/@vertAlign` 이 칸을 여는 태그 뒤에 오므로 표 훑기(`TableScan`)가 적어 두고 그리기가 읽는다 |

고친 뒤 짝의 문단·줄바꿈·취소선·칸 배경·그림 설명·글꼴 분류가 같아졌다. 오라클 대조 값은 위 표 그대로다.

**그래도 '서식을 문단에 적는가 글자에 적는가' 를 '보이는 모양은 같다' 로 넘긴 것이 틀렸다.** 두 변환기의 **일관성만** 보는 검토를
한 번 더 돌렸더니(2026-09-27, 짝 셋의 HTML 을 블록마다 맞춰 CSS 가 물려지는 것까지 셌다) 열다섯이 나왔다. 가장 큰 것이 바로 그것이다.

| 무엇 | 어떻게 맞췄나 |
|---|---|
| **HWPX 는 문단에 바탕 글자 크기를 적지 않았다** — 빈 문단의 줄·줄 상자의 높이·번호 표지·각주 표지가 전부 문서 기본 크기였다. K25 의 빈 문단 874개가 전부 100%(K01 은 30~73%), 빈 줄의 높이 합이 1.6~1.9배, 글이 든 문단의 99% 가 글보다 37% 높은 줄 상자 | HWP 처럼 첫 `hp:run` 의 글자 모양을 문단의 바탕으로 적고 덩이는 바탕과 다른 것만 적는다. 빈 문단의 크기 분포가 세 짝 모두 **같아졌고**, 글보다 높은 줄 상자가 99% → 0%. 제목·번호 문단은 첫 덩이에서 연다(바탕을 알아야 하므로) |
| HWP 는 네 변이 '없음' 인 칸에도 테두리를 그렸다 — K19 의 '보도시점·배포' 머리 줄이 회색 상자였다(네 변 없는 칸: K01 382·K19 121, 짝의 `td.nb` 수와 같다) | 테두리/배경의 변 종류를 읽는다. 변 하나가 **종류·굵기·색 6바이트**다 — 명세의 표는 종류 넷을 먼저 적는 것처럼 읽히지만 짝의 칸들이 그 모양으로만 맞았다. HWPX 처럼 `td.nb` |
| HWP 의 줄 간격이 백분율(`130%`) — 문단에서 길이로 풀린 뒤 물려져 큰 글자가 좁은 줄에 눌렸다 | 두 변환기 모두 단위 없는 수(`1.3`, `CssValues.number`) |
| 부분 나누기 — HWP 는 칸 하나를 블록 하나로, HWPX 는 60자로 셌다(K01 6부분, 같은 보도자료 K25 4부분) | 한 저울(`HancomChunkMeter`, 칸 = 블록). 이제 부분 수·부분 이름·첫 글이 짝끼리 같다(`HancomPairTest`) |
| 도형 배지 — HWP 는 글상자를 그린 도형도 셌다(K01 8·K25 3, K19 6·K27 0). HWP 는 개체 연결선(`$col`)을 세지도 않았다 | 글상자가 없는 도형만 센다(HWPX·docx 의 판단). 연결선도 도형이다 |
| 캡션 자리 — HWPX 는 스키마의 차례대로 **모든 캡션을 개체 앞에** 적었다(K27 의 아래쪽 캡션). 수식·묶음·OLE·차트·글맵시의 캡션은 버렸다. HWP 는 수식 캡션만 늘 뒤에 | 두 변환기 모두 캡션의 자리를 따른다 — 아래·오른쪽은 따로 쓴 쓰개에 적었다가 개체 뒤에 붙인다(`HtmlWriter.append`) |
| 바탕 스타일이 일곱 군데 달랐다(캡션 .92em·표 여백·칸 여백·제목 줄 간격·칸의 기본 세로 정렬…) | 한 벌(`HancomCss.FLOW`) |
| 자원 상한(32 MiB)을 넘는 그림 — HWP 는 `img` 를 써서 깨진 그림만 떴다 | 풀린 크기를 **세기만 하는 풀기**로 재고 넘으면 '그릴 수 없는 그림' 으로(HWPX 와 같다) |
| 각주 — HWP 는 사용자 기호(`*`)를 읽지 않아 숫자로, HWPX 는 쪽마다 새로 세는 번호를 그대로(흐름에는 쪽이 없다). 각주·미주의 차례도 달랐다 | 사용자 기호를 읽는다. 쪽마다 새로 세는 번호는 흐름에서 이어 센다(두 변환기). 각주를 모두 쓴 뒤 미주. HWP 는 새 번호 지정(`nwno`)도 따른다 |
| 번호 표 — 동그라미 숫자 21~50·26 을 넘는 영문자·시작 번호 0·'네 글자 되풀이' 가 달랐다 | 한 표와 한 셈(`HancomNumbers`) |
| 제목 다듬기 — HWPX 는 방향 바꾸기 문자를 거르지 않았고 줄바꿈이 빠져 `A<br>B` 가 `AB`. HWP 는 스타일 이름 `개요 N` 도 제목으로 보았다 | 한 다듬기(`HancomTitles`), 줄바꿈은 공백. 제목은 문단 모양의 머리 모양으로만 가린다 |
| HWPX 는 밑줄의 자리(위·가운데)와 상대 크기(`relSz`)를 읽지 않았다 | HWP 와 같은 규칙(위 → 윗줄, 가운데 → 취소선, 상대 크기 10~250% 밖은 100%) |
| 글자 겹침의 남은 사설 영역 글자 — HWP 는 두부를 그대로 적었다 | 버리고 센다(`HancomChars.compose`) |
| HWP 가 음수 여백을 `margin-left:0pt` 로 적었다 · 미리보기가 잘려도 알리지 않았다 | 적지 않는다 · 알린다 |

고친 것마다 시험을 박고 고침을 되돌려 깨지는 것을 확인했다(HWP 24건, HWPX 33건 — 고친 쪽과 **다른** 검토자가 자기 경우를 더해
다시 돌렸다). 두 변환기는 서로 다른 사본에서 동시에 고쳤고, 공용 규칙은 그 전에 `format:html` 에 먼저 세웠다.

**기기**(두 에뮬레이터, 릴리스 R8): 세 짝의 첫 화면이 짝끼리 **화소까지 같다**(캡처 여섯 쌍 가운데 다섯, 나머지 한 쌍은 한 줄이
1화소 밀렸다). 부분 수도 같다(K01·K25 6, K19·K27 4). K19 의 '보도시점·배포' 줄이 HWP 에서도 테두리 없이 그려진다.

**지금 남은 짝 차이**(K01/K25 · K11/K33 · K19/K27, 태그·CSS 속성의 분포): HWPX 가 덩이를 탭·줄바꿈·묶음 빈칸에서 한 번 더
나눠 같은 모양의 `span`·`sub` 가 몇 개 더 있다(K25 187 대 K01 176), HWPX 가 주석 쪽을 `div.serif` 로 한 번 더 싼다. 보이는
모양은 같다. 버린 것 배지는 짝끼리 **같다**(K01·K25 모두 머리말 1·도형 3·그릴 수 없는 그림 1·수식 5).

### 13단계에서 미룬 것

| 미룬 것 | 왜 |
|---|---|
| HWP 5.0 의 **변경 추적** — 지운 글이 보인다 | 표본이 없고 레코드 모양이 문서화되지 않았다. 알리지도 못한다(고칠 때 경고부터) |
| 배포용 문서의 **복사·인쇄 제한** | 256바이트 레코드의 제한 비트 뜻을 확인하지 못했다(0x8003 · 0x8001 만 보았다). 모르는 비트로 막지 않는다 |
| 배포용 HWPX | 표본이 없다. 알아보면(`ha:docdistribute`) 읽히는 데까지 보이고, 하나도 못 읽으면 '암호로는 열 수 없다' |
| 수식 조판 | 한컴 수식 스크립트를 그대로 보인다(배지로 센다) |
| 머리말·꼬리말·쪽 모양·다단·탭 채움 | 흐름 렌더의 한계(12단계와 같다) |
| 한글 암호의 바이트 인코딩(HWPX) | UTF-8 로 가정했다. 실물은 ASCII 암호(O16) 하나로만 확인했다 |
| ~~HWPX 그림의 **설명문**(`hp:shapeComment`)~~ | **14단계에서 했다** | 훑기가 그림·묶음의 차례 번호로 적어 두고 그리기가 `img@alt` 로 쓴다. 자동 설명 가리기는 `format:html` 의 `HancomAlt` 한 벌 — 4,096자까지 읽고 **가린 뒤에** 300자로 자른다(예전 HWP 는 300자에서 잘라 읽어, EXIF 줄이 붙은 긴 자동 설명문이 끝 줄의 `:` 를 잃고 사람이 쓴 것으로 남았다). 읽을 수 있는 HWPX 의 설명문 111개가 전부 자동이라 실물의 대체 글은 그대로 비었다 |
| docx 의 `ListMarkers.glyph` 에 글머리표 표가 한 벌 더 | 한글의 두 변환기는 `format:html` 의 표 하나를 쓴다(번호 모양·셈까지). docx 쪽은 워드의 기호 글꼴이라 옮기지 않았다 — 고칠 때 함께 본다 |
| HWPX 의 **문서 안 링크**(`?#인스턴스` 하이퍼링크·상호 참조) | HWP 는 차례의 `?#645989673` 을 문단의 인스턴스 번호로 풀어 링크를 만든다(S01). HWPX 는 그런 명령을 가진 표본이 없어 모양을 확인하지 못했다 — 지금은 링크 없이 글만 보인다 |
| HWP 의 **글맵시 글** | 레코드 모양이 명세에 없다(hwplib 이 읽는 모양은 확인하지 않았다). HWPX 는 `@text` 를 글로 보인다 — 짝이 어긋나는 자리로 남긴다. **14단계가 표본을 훑었다** — 읽을 수 있는 HWP 17개(배포용 셋은 풀었다, O08 은 암호)에 `SHAPE_COMPONENT_TEXTART` 가 0개, HWPX 9개에 `hp:textart` 가 0개라 짝으로도 확인할 수 없다. 글 없이 '글자 효과' 로 센다 |
| ~~HWPX 의 새 번호 지정(`hp:newNum`)~~ | **14단계에서 했다** | 각주·미주의 `hp:newNum` 이 우리 셈을 옮긴다(`번호 − 구역 시작 번호`, HWP 의 `nwno` 와 같은 식). 갈리는 것은 쪽마다 새로 세는 문서와 저장 번호 없는 주석뿐이다 — 표본의 `hp:newNum` 은 전부 쪽 번호라 실물에서는 갈리지 않았다 |
| 아래쪽 캡션 안의 각주·빈 문단 | HWPX 는 캡션을 개체 뒤로 옮겨 쓰지만 그 안의 각주는 **만난 차례**로 번호를 받고, 캡션 끝의 빈 문단은 뒤의 빈 문단 수에 들지 않는다(HWP 는 든다). 표본에 없다 |

### 이미 구현한 포맷을 실세계 말뭉치로 확인했다 (2026-09-25)

사용자 요청: 이미 구현한 문서 포맷도 인터넷에서 표본을 찾아 제대로 열리는지 확인할 것. 그때까지의 표본은 거의 전부 우리가 만든
것이었다(파이썬 라이브러리의 출력, 손으로 쓴 PDF·EPUB) — 12단계가 '실제 오피스가 만든 파일' 을 미룬 것이 그것이다.

**말뭉치** — `samples-local/corpus/`(커밋하지 않는다) 122건: PDF 25, EPUB 16, OOXML 81(docx 무리 35·xlsx 무리 28·pptx 무리 18).
전부 공개 저장소의 시험 표본이다 — Apache POI·Tika(Apache-2.0), LibreOffice core(MPL-2.0), openpreserve format-corpus(CC0),
IDPF·W3C 의 EPUB 시험(CC BY-SA — 로컬에서만 쓴다), 미국 정부 저작물(퍼블릭 도메인), python-docx·msoffcrypto-tool(MIT). 파일마다
원래 이름·주소·라이선스·만든 도구·기대 동작을 `manifest.json` 에 적었다. 오라클을 만드는 스크립트는 말뭉치 곁
(`samples-local/corpus/tools/`)에 둔다 — 처음에는 세션의 스크래치패드에 있어 세션이 끝나면 오라클을 다시 만들 수 없었다.

**시험이 스스로 돈다** — `EpubCorpusTest`·`PdfCorpusTest`(JVM)·`PdfCorpusDeviceTest`(계측), `DocxCorpusTest`·`XlsxCorpusTest`·
`PptxCorpusTest`·`CfbCorpusTest`·`OoxmlCorpusCryptoTest`. 말뭉치가 없으면 건너뛴다. 오라클 파일만 없으면 판별·여는 결과·암호
흐름·구조 단언은 돌고 낱말 점수·복호화 해시 비교는 건너뛴다(확인했다).

**PDF·EPUB**(41건 — PDF 25, EPUB 16). 오라클은 pikepdf(qpdf 12.3.2)·pypdf 6.17, EPUB 은 zipfile + lxml 로 OPF·nav·NCX·본문을
따로 읽은 것, 그리고 말뭉치가 스스로 공개한 기대값(IDPF·W3C 저장소의 git blob SHA-1)이다.

| 확인한 것 | 결과 |
|---|---|
| EPUB 장 수·목차 줄 수 | 열리는 15권 전부 오라클과 **같다**(`linear="no"` 포함 — E03 16 중 15, E11 144 중 142) |
| EPUB 본문 낱말 | 재현율·정밀도 **1.0000**(E20 의 정밀도 0.90 은 풀지 않은 `&xxe;` 엔티티 — 옳은 동작) |
| 난독화 글꼴(E05 셋·E06) | 풀린 바이트가 IDPF·W3C 가 공개한 원본의 blob SHA-1 과 같다. sfnt 표 검사합도 맞다 |
| LCP(E21) | '암호로는 열 수 없는 방식' |
| 장 2,014개(E12) | 여는 데 74~276 ms, 모든 장을 3초 안에. 힙 최고 146 MB(E10) |
| 버린 것 일곱 종류의 수 | lxml 이 위생기 규칙대로 센 것과 같다(E18 의 727 은 easylaw.go.kr 에 걸린 진짜 그림) |
| 반쯤 자르거나 뒤섞은 책 | 멎지도, 예외가 새지도 않는다. 이제 '깨진 파일' 로 끝난다(예전에는 '입출력 실패') |
| 암호 PDF 7개 | 암호 없음·틀림은 '틀렸다', 사용자·소유자 암호는 연다. NFC·NFD 의 `hôtel`·`âge` 둘 다 |
| 우리가 푼 스트림 | qpdf 가 푼 것과 **바이트가 같다**(32/32 · 36/36 · 36/36 · 3/3 · 3/3 · 1/1 · 38/38). 우리 평문을 qpdf 가 경고 0, 엄격한 pypdf 가 경고 0 으로 읽고 쪽 글이 원본과 같다 |
| 쪽 수 | 암호 없는 17개 전부 qpdf 와 같다(PDF-06 은 6,696쪽) |
| **기기**(두 에뮬레이터) | 25개 전부 — 쪽 수가 qpdf 와 같고 첫·가운데·끝 쪽에 잉크가 있다(`PdfCorpusDeviceTest`, API 31 은 우리 복호화, API 35 는 플랫폼) |

**고친 것**(검증 5, 검토 6, 병합하며 2).

| 무엇 | 원인 |
|---|---|
| **XHTML 의 `<div/>` 가 뒤의 장 전체를 삼켰다**(E10 370곳, E14 8곳) | 장은 `text/html` 로 나간다. HTML 파서는 빈 요소가 아닌 것의 `/>` 를 무시해 그 요소가 닫히지 않는다. 위생기가 `<div></div>` 로 풀어 쓴다(`<style/>` 는 문서 끝까지 삼켰다) |
| **장이 쿼크 모드로 그려졌다** | 위생기가 DOCTYPE 을 지우고 껍데기가 되돌리지 않았다. 쿼크 모드의 표는 본문의 글자 크기를 물려받지 않는다(크롬: 본문 30px, 칸 16px). 글 대조로는 보이지 않는다 |
| `xml:lang` 만 적은 장의 언어가 사라졌다 | HTML 파서는 `xml:lang` 을 모른다 — 한자가 기기의 언어로 그려졌다(한자 통합). `lang` 을 함께 적는다 |
| **일본어·중국어의 줄 끝이 들쭉날쭉**(E03·E04·E17) | `word-break:keep-all` 은 띄어 쓰는 한국어의 규칙이다. 띄어 쓰지 않는 글에 걸면 공백·문장 부호에서만 줄이 나뉘어 양쪽 정렬과 만나 '都　說　沒　了' 처럼 벌어진다. `:lang(ja)·:lang(zh)` 는 되돌리고, 장에 언어가 없으면 책의 `dc:language` 를 단다(두 기기 캡처로 전후를 견줬다) |
| 차례에 곧바로 든 그림이 글로 읽혔다(E08 13쪽 전부 깨진 글자) | 만화형 EPUB 은 JPEG 를 차례에 넣는다. 그림 쪽으로 그린다. 형식을 모르거나 `octet-stream` 으로 적은 항목은 앞머리를 보고 가른다 |
| DOCTYPE 의 내부 선언이 `]>` 로 화면 맨 위에 찍혔다(E20) | |
| XHTML 의 `<br></br>` 가 줄바꿈 둘 | HTML 명세상 `</br>` 은 `<br>` 이다. 빈 요소의 닫는 태그를 쓰지 않는다 |
| 버린 것 알림이 보이지 않는 것을 셌다 | 장마다의 `<meta>` 가 '알 수 없는 요소' 로(E12 에서 2,014), 저작권 링크가 두 번씩 |
| 깨진 EPUB 이 '입출력이 실패했습니다' | ZIP 예외가 공용 매핑에서 `Io` 가 됐다. '깨진 파일' 로 |
| **형식이 `text/css; charset=utf-8` 인 스타일시트가 위생 없이 나갔다** | 화면이 매니페스트의 형식을 글자 그대로 견줬다. 매개변수·대문자를 떼고, 이름과 형식 가운데 하나라도 CSS 면 위생을 거친다. 차례 밖의 HTML 은 아예 내주지 않는다 |
| 기기 시험이 오라클 파일 없이도 통과했다 | 쪽 수·잉크를 견줄 수 없으면 실패로 |

**오라클이나 명세의 한계로 남긴 것**: PDF-28(첨부만 잠김)은 우리 복호화기로 가면 암호를 묻는다 — 기기에서는 pdfium 이 스스로 열어
닿지 않는다. XHTML 의 `<p><div>` 는 HTML 파서가 다르게 닫고(말뭉치에 없다), `<style>` 안의 CDATA 는 첫 규칙을 잃는다. 읽어 주기
(미디어 오버레이)는 알리지 않고 버린다. 고정 레이아웃은 흐름으로 그리되 알리지 않는다.

**OOXML**(81건). 오라클은 python-docx·openpyxl·python-pptx(글), msoffcrypto-tool(무결성까지 확인한 복호화 — 그 도구가 못 푸는
셋은 명세대로 따로 쓴 파이썬 Agile 복호화기, HMAC 확인), olefile(CFB 목록), 그리고 표본을 낸 프로젝트의 시험이 단언하는 글이다.

| 확인한 것 | 결과 |
|---|---|
| 여는 결과(열림·암호·이전 형식·깨짐·다루지 않음) | **81/81** 기대대로. 판별기가 맡는 것도 81/81 이 `FormatRegistry` 의 뜻과 같다 |
| 낱말 재현율 | docx 0.997~1.000 · xlsx 1.000 · pptx 1.000. DM01 의 0.997 은 윗첨자에서 낱말을 가르는 시험 쪽의 차이다(`1×10`+`7`) |
| 낱말 정밀도 | 낮은 것이 정상이다 — 오라클이 우리보다 덜 본다(python-docx 는 머리말·글상자·OMML·윗주를, openpyxl 은 글이 든 칸만, python-pptx 는 마스터의 글을 읽지 않는다). 그래서 하한(0.95)은 재현율에만 건다 |
| 암호 문서 15개 | 풀린 패키지가 오라클과 **바이트가 같다**. 표본을 낸 프로젝트가 적은 값(EN12 의 SHA-256, EN07 의 크기, EN15 의 ZIP 항목 크기)도 맞다 |
| '읽기 전용 권장'(EN07) | `VelvetSweatshop` 으로 묻지 않고 연다. POI 는 '일부러 망가뜨린 표본' 으로 적지만 명세대로 풀면 HMAC 까지 맞는다 |
| CFB 18개 | 스트림 목록이 olefile 과 같다 |
| 표시 형식 | POI 의 진리표(XL07) 325행 중 130행 일치(고치기 전 117), 나머지 195행은 알려진 분수 형식 두 갈래. 날짜표(XL08) 45/45(고치기 전 16) |
| **기기**(두 에뮬레이터, 릴리스 R8) | 24건을 화면으로 열었다 — 확인란 ☐·☒(DX09), 이항 계수 `(n¦k)`(DX12), 슬라이드 번호 `1`(PP17, `‹#›` 가 아니다), 암호 docx(EN01·EN03)와 기본 암호 docx·xlsx(EN07·EN12, 묻지 않는다), 큰 시트(BG01·XM01), 깨진 것(CR01·CR02 '열 수 없습니다'), 이전 형식(LG01) 전부 기대대로. xlsb(EN20)는 문서로 맡지 않아 파일 정보가 뜬다 |

**고친 것**(검증 11, 검토 2).

| 무엇 | 원인 |
|---|---|
| docx: 문단 표시만 지운 번호 문단이 **빈 표지**로 남고 뒤의 번호를 밀었다(DX05) | 문단 속성의 `w:rPr/w:del`·`w:moveFrom` 을 읽지 않았다 |
| docx: 옛 양식 확인란(FORMCHECKBOX)이 보이지 않았다(DX09) | 결과 글이 비어 있다(함정 표). `w:checked`(없으면 `w:default`)로 ☐·☒ 를 적는다 |
| docx: 가로줄 없는 분수(이항 계수)를 나누기로 적었다(DX12 의 `(n/k)`) | `m:type="noBar"` 를 보지 않았다. 워드의 선형 형식처럼 `(n¦k)` 로 |
| 깨진 docx·xlsx 가 '다루지 않는 문서' 또는 '입출력이 실패했다'(CR01·CR02) | 판별기가 중앙 디렉터리를 못 읽으면 손을 뗐고, ZIP 예외가 공용 매핑에서 `Io` 가 됐다. 오피스 확장자면 맡고 '깨진 파일' 로 |
| xlsb 가 여는이에 닿으면 XML 파서 오류로 '깨진 파일'(EN20) | 본문 형식 `…sheet.binary.macroEnabled.main` 의 `ms-excel` 을 보고 xlsx 변환기에 넘겼다. '다루지 않는다' 로 |
| xlsx 표시 형식 다섯 — 앞머리 쉼표·`?` 자리 사이의 천 단위 쉼표·정수 없는 분수 앞의 글자·분모 `0` 자리의 채움 방향·소문자 `am/pm` | 진리표가 잡았다. 분모를 반대쪽으로 채우면 **값이 바뀐다**(3/4 가 `03/400`) |
| xlsx: 5만 칸 상한에서 보이는 값을 잃지 않았는데 '줄였다' 를 알렸다(XM01) | 함정 표. 칸 상한도 멈춘 자리 뒤에 보이는 값이 남았을 때만 알린다(행·열 상한은 원래 그랬다) |
| pptx: 슬라이드 번호 필드가 `‹#›` 나 저장된 옛 번호로 보였다(PP17) | 함정 표. `presentation@firstSlideNum`(0~9,999 만 믿는다) + 슬라이드 차례로 |

**남긴 한계**.

- xlsx: 분자·분모와 빗금 사이에 글자나 폭 자리가 낀 분수(`#\:#=/=#`·`#_#/#`)는 분수로 알아보지 못한다(진리표 193행). `[$-F800]`
  (시스템 긴 날짜)은 기기 로캘이 아니라 적힌 코드로 그린다 — 순수 JVM 모듈은 로캘을 모른다.
- docx: 문단 표시만 지우고 글은 남은 문단은 워드의 최종본처럼 다음 문단에 합치지 않고 번호 없는 문단으로 선다. SmartArt 의 글은
  **14단계부터 그린다**(pptx 와 같은 그림 캐시에서, 도형 차례대로 — 자리 순이면 순환형 DX11 이 a·c·b 가 된다). 선형 수식의 `a_ncos(nπx)/L` 처럼
  첨자 뒤에 띄어쓰기가 없어 읽기 모호한 자리가 있다.
- pptx: 관계를 풀 수 없는 슬라이드는 빠지고 뒤의 슬라이드 번호가 당겨진다. 날짜 필드는 저장된 글 그대로다.
- ZIP 리더의 생성자가 던지는 맨 `IOException` 은 모두 '깨진 파일' 이다(상한·중단·사라진 파일은 빼고). commons-compress 가 깨진
  구조와 저장소의 읽기 오류(SAF 의 EIO)를 같은 형으로 던져 가를 수 없다.
- ~~옛 표본 시험 여섯은 `samples-local/<형식>/` 을 찾아 여전히 건너뛴다.~~ 14단계가 정리했다 — `CfbRealWorldTest` 는 `hwp`·`corpus`
  의 CFB 36개의 모든 스트림을 흘려 읽고(DIFAT 을 가진 K04·K05 포함), `XlsxOracleTest` 의 큰 표본 시험은 말뭉치의 큰 xlsx 넷을 연다.
  말뭉치 시험과 겹치는 것과 맞는 표본이 없는 것은 지웠다.

**말뭉치 수정을 다시 적대적으로 검토했다**(네 렌즈 — 정확성·일관성·보안·문서의 주장 — 와 반증조). 보안 렌즈가 여섯을 찾아 모두
고쳤다 — `<style>` 안의 `<`(책의 `</head>` 가 껍데기를 속였고 `<svg>` 안의 `<style>` 은 태그로 읽혔다), 껍데기가 태그를 글자로
찾은 것, 다른 SVG 를 가리키는 `<use>`, CSS 의 `url(x.svg#f)`, DOCTYPE 탐색의 제곱 시간, 형식 문자열을 믿고 날것으로 내준 자원
(함정 표의 세 줄). 고친 것마다 시험을 박고 되돌려 깨지는 것을 확인했다. 문서 렌즈는 13단계 절의 숫자 열하나가 코드·표본과 어긋난
것을 찾았다(시험 수·오라클 값·표본 수·통계 — 고쳤다). 일관성 렌즈는 위 '13단계 실측' 의 짝 대조(셋째 검토)다.

### 14단계 — 마감, 그리고 미룬 것 가운데 지금 할 수 있던 것 (2026-09-28)

사용자 요청: '실기기 검증은 빼고 14단계와, 미룬 것 가운데 지금 구현할 수 있는 것을 진행하라. 그리고 파일 관리자는 화면을 아래로
당겼다 놓으면 새로고침되게 하라.'

**여덟 꾸러미로 나눠 저장소의 복사본에서 동시에 만들었다** — 설정·파일 관리자·텍스트·압축·만화·이미지·문서 뷰어·변환기.
꾸러미마다 맡은 모듈이 겹치지 않고, 구현한 사본을 **다른 에이전트가** 적대적으로 검토해 고친 뒤 합쳤다. 꾸러미 사이의 계약은
먼저 실제 저장소에 세웠다 — `AppPreferences` 의 열쇠(텍스트 뷰어 기본값·만화의 기본 방향·읽기 모양·만화의 쪽 배치·PDF 두 쪽),
`ComicProgressDao`·`DocProgressDao` 의 `findAll`(`ProgressLookup.MAX_KEYS` = 500), 홈 화면의 설정 줄. 앱의 배선(풀기 알림의 '열기',
다음 권, 설정으로 남는 배치)은 꾸러미가 요청으로 넘기고 합칠 때 붙였다.

| 꾸러미 | 한 것 |
|---|---|
| 설정(`feature:settings`, 새 모듈) | 파일 목록·텍스트 뷰어 기본값·만화의 새 책 방향·문서 보기(글자 70~200%·여백·바탕 넷)·기록 지우기(확인 대화상자)·크래시 기록·정보(판·고지·진단). 쓰기는 `Deferred<Boolean>` 을 돌려주고 화면이 기다려 알린다 — 사건 채널로 두면 화면이 사라진 뒤 끝난 결과가 다음에 설정을 열 때 뜬다. 고지·진단에 다녀오면 설정 화면이 컴포지션에서 빠지므로 스크롤 자리를 액티비티에 묶인 VM 에 맡긴다 |
| 크래시 기록(`core:data` 의 `CrashLog`) | `attachBaseContext` 에서 걸고 앞의 처리기를 잇는다(두 번 불려도 한 겹, 어느 길로도 던지지 않는다). `filesDir/crash/` 에 최근 5건·한 건 64 KiB, 임시 파일 → fsync → rename. 절대경로와 `content:`·`file:` 주소는 `<경로>` 로 지운다. **화면에는 건수와 마지막 시각만** 보이고, 공유는 캐시의 사본으로 한다 |
| 고지(`app` 의 `NoticeScreen`·`NoticeCatalog`) | 릴리스 런타임 클래스패스의 좌표 147개. 라이선스는 Gradle 캐시의 POM·jar 에서만 읽었다(Apache-2.0 · 0BSD(XZ) · UnRAR · MIT(SLF4J API) · BSD 3-Clause(DataStore 의 protobuf), Commons 넷의 NOTICE). **한컴 문서 명세의 고지 문장**(13단계가 넘긴 의무)을 싣는다 — 함정 표의 '명세가 요구한 문장' |
| 릴리스 | 1.0.0(판 코드 2). 서명은 그대로 디버그 키다 — 바꾸면 설치된 앱을 지우고 깔아야 해 기록이 사라진다. 사용자가 정할 때 한 번 |
| 파일 관리자 | **당겨서 새로고침**(목록·격자·갤러리·휴지통. `PullToRefreshBox` 가 아니라 `Modifier.pullToRefresh` 다 — material3 1.4.0 의 상자에는 `enabled` 가 없어 고르는 중에 끌 수 없다. 짧은 목록에서도 당겨진다)과 ⋮ 메뉴의 '새로고침'(화면 낭독기로는 몸짓을 찾을 수 없다). 폴더 감시(`FolderWatch` — `FileObserver` + 디바운스, 우리 임시 파일은 무시)와, 앞으로 돌아올 때 폴더의 수정 시각이 바뀌었을 때만 다시 읽기. 휴지통 자동 비우기(JobScheduler). '다른 곳에 복원'. 풀기 뒤 '열기'. 목록의 읽던 쪽 배지(`12/30쪽`·`50%`·`다 읽음` — 이어보기 키로 찾고 경로를 쓰지 않는다. 자리가 하나뿐인 문서에는 달지 않는다) |
| 텍스트 | 기본값을 설정에서 시작하고 바꾸면 적는다. 찾기 진행률. 줄 끝 표시(LF `↓`·CRLF `⏎`·CR `←`, 찾기·강조 좌표 밖). **마크다운 미리보기** — `format:text` 의 순수 JVM 변환기(CommonMark 줄 알고리즘·구분자 쌓기 + GFM 표·취소선·할 일). 날것의 HTML 은 글자로 보이고(명세와 다르다), 누를 수 있는 것은 `#자리` 뿐이며, 그림은 그 폴더 아래의 상대 경로만 보인다. 문서와 같은 잠긴 WebView 에서 돌고, 글자 크기는 `textZoom` 으로 그 자리에서 건다(다시 만들면 읽던 자리가 맨 위로 간다) |
| 만화 | 새 책의 기본 방향(책의 기록이 이긴다). 두 쪽 보기(한 쪽·두 쪽·자동. 표지는 혼자 서고, 짝은 쪽 번호만으로 정하며, 오→왼이면 앞 쪽이 오른쪽. 반쪽 하나의 몫이 `pageCap/2` 라 펼침 넷이 한 장짜리 넷과 같은 바이트다). 쪽 목록(격자. 디스크에 쓰지 않고, solid 는 아카이브 차례로 한 번 훑는다). 다음 권(파일 관리자의 이름 차례에서 지금 책 바로 뒤. ⋮ 메뉴와, 끝에 닿았을 때의 알림) |
| 문서 뷰어 | 읽기 모양(글자는 `textZoom`, 여백·바탕은 위생을 거친 CSS, 슬라이드에는 바탕을 걸지 않는다)과 뷰어 안의 '보기'. 장·부분 안쪽 위치(`locator` = `장:비율`). 고정 레이아웃 EPUB. PDF 두 쪽 보기·쪽 목록·**API 35 에서만** 찾기. 암호 PDF 복호화기의 시험 빈틈 둘(V4 `/Length` 없음 · R2~R4 한글 후보)을 판별력 있는 시험으로 메웠다 |
| 압축 | **tar 계열**(`.tar`·`.tar.gz`/`.tgz`·`.tar.bz2`/`.tbz2`·`.tar.xz`/`.txz`, 그리고 `.cbt` 만화). 머리는 우리가 읽고 해제기만 라이브러리를 쓴다 — commons-compress 의 `TarArchiveInputStream` 은 메타 머리(긴 이름·PAX)를 적힌 크기만큼 모으고, 이름의 원본 바이트를 주지 않고, 링크의 크기 칸을 믿는다. 압축 안 한 tar 는 번호로 열고, 압축 tar 는 흐름이라 solid 7z 와 같은 길이다(목록·건너뛰기가 총량 상한에 묶인다 — 풀린 크기가 1 GiB 를 넘는 `.tar.gz` 는 '풀어야 할 양이 너무 커서 열지 않았습니다'). 링크·장치·희소 파일은 풀지 않고 링크로 센다. 풀기 뒤 **폴더 수정 시각**(이번 풀기가 만든 폴더 가운데 아카이브에 시각이 적힌 것만, 깊은 것부터 마지막에 — 7z 가 폴더 항목을 넘기지 않아 빈 폴더조차 안 생기던 것도 고쳤다). **진행률의 분모**는 아카이브에서 실제로 읽은 바이트다(RAR 은 선언값, 미검증). **인코딩 판정을 하나로** — `EntryNameDecoder` 가 `core:charset` 의 `CharsetDetector` 를 부른다. 합치기 전에 오늘의 답을 시험으로 박았다(`EntryNameRegressionTest`·`EntryNameConsistencyTest`) |
| 이미지 | 이미지 뷰어가 GIF·애니WebP 를 튼다 — 자리 잡은 장 하나만 돌고, 버퍼 셋이 한 화면 크기 비트맵에 들 때까지 표본을 줄이며, 그래도 안 들면 첫 장면과 까닭을 보인다. 확대도 된다(만화 뷰어는 그대로 막는다). APNG 은 첫 장면과 고지. **아래로 끌어 닫기**(배율 1 에서만, 인식기 하나가 축을 한 번 정한다). 썸네일 부정 캐시가 일시·영구·불확실을 가른다(`ThumbnailFailures`). 확대 조각이 EXIF 여덟 방향을 다 옮긴다 — 디코더가 방향을 적용했는지는 조각이 처음 필요할 때 두 디코더로 작게 떠 견준다 |
| 변환기 | docx 의 **메모**를 그린다(범위 끝의 `[머리글자+번호]` 표지 ↔ 부분 끝의 메모 쪽). docx 의 **SmartArt 글**을 캐시된 그림에서 도형 차례로 그린다(pptx 와 같은 그림). HWPX 그림 설명문과 `hp:newNum`, 자동 설명 가리기를 `format:html` 의 `HancomAlt` 한 벌로. 옛 표본 시험 여섯이 말뭉치를 보게 했다 — 일곱 모듈의 건너뜀이 0이다. HWP 글맵시는 표본 어디에도 레코드가 없어 미뤘다(13단계 표) |

**검토가 잡은 것**(구현한 사본을 다른 에이전트가 반증했다. 고친 것마다 되돌려 시험이 깨지는 것까지 다시 확인했다).

| 무엇 | 어떻게 고쳤나 |
|---|---|
| **설정 파일이 깨지면 켤 때마다 죽는다** | 함정 표. 크래시 기록을 볼 설정 화면에도 못 닿는 모양이었다 |
| 크래시 기록이 따옴표 뒤의 파일 이름을 남겼다 | 함정 표('경로의 끝을 글자 하나로 정하지 마라') |
| 공유 사본을 두 번 누르면 섞였다 | 잠근 채로 덮어쓴다. 만드는 동안 단추를 막는다 |
| PDF 배지가 뒤집혔다 | PDF 에도 `progress` 를 적어 배지가 '4/8쪽' 에서 '50%' 가 되고, 한 쪽짜리 PDF 는 열기만 해도 '다 읽음' 이었다. PDF 는 `locator`·`progress` 를 비운다 |
| 짧은 마지막 장이 '다 읽음' 에 못 닿았다 | 진행을 화면 **앞** 가장자리로 셈하면 한 화면에 드는 장은 늘 0이다. 화면 **끝** 가장자리까지 본 몫을 따로 잰다(`ReadingScroll.seenOf`) |
| 글자 크기·회전 뒤 되살리기가 옛 배치에서 멈췄다 | `onPageFinished` 가 다시 오지 않는다. 범위가 한 번 바뀐 뒤에만 센다 |
| 두 쪽 보기로 뛸 때마다 solid 패스가 둘 | 9단계가 한 쪽 보기에서 고친 것이 되살아났다 — 페이저가 앞 칸 두 쪽을 함께 띄운다. 뛰어든 자리에서 세 쪽 물러선다 |
| 두 쪽의 반쪽이 흐렸다 | 2의 거듭제곱 표본이라 1200×1800 쪽이 600×900 으로 떠서 1.2~1.8배 늘어났다. 자리에 맞춘 크기로 뜬다(`SpreadMath.halfSize`). 구현 보고는 '흐려지지 않는다' 였다 — 셈이 틀렸다 |
| 앞 책의 알림이 같은 책을 다시 열 때 배달됐다 | 함정 표('버퍼를 가진 `Channel`') |
| 쪽 목록이 화면 낭독기에 단추 하나로 읽혔다 | 함정 표('`clickable` 은 자손의 의미를 합친다') |
| 마크다운이 제곱이었다 | 함정 표('참조 구현을 옮기면') |
| 마크다운이 전각 공백을 벗겼다 | 코틀린의 `trim()` 은 전각 공백·NBSP 까지 벗긴다(일본어의 전각 들여쓰기가 사라졌다). 명세대로 공백·탭·줄 끝만 |
| 텍스트 뷰어의 옛 결함 둘(7단계부터) | `RowReader.read` 가 원하는 행을 다 모은 뒤에도 스트림 끝까지 풀어 찾기·강조가 파일 크기의 제곱이었다. 길이가 4,096의 배수인 줄이 빈 행을 하나 더 만들어 그 뒤 행 번호가 밀렸다 |
| 찾을 말을 지우고 찾기를 누르면 옛 결과가 떴다 · 자라는 로그에서 찾기가 끝나지 않았다 | 돌던 찾기를 멈추고 번호를 바꾼다 · 색인이 아는 행까지만 훑는다 |
| 막힌 폴더(`Android/data`)에서 돌아올 때마다 다시 읽었다 | 실패한 나열의 시각을 버리고 있었다 |
| '보기' 를 빨리 누르면 글자가 줄었다 커졌다 | 함정 표('DataStore 는 줄 선 쓰기 사이에') |
| 펼친 PDF 의 재단 여백이 옆 쪽에 번졌다 · 쪽 목록이 예산을 넘었다 · 밤 모드를 바꾸면 찾기 막대만 사라졌다 | 함정 표 둘. 격자가 열린 동안 쪽 층이 선명화 조각을 놓는다 |
| 설정의 슬라이더가 백분율로 읽혔다 | 함정 표 |
| **흔한 한글 이름의 CP949 zip 이 깨져 보였다**(인코딩 통합의 회귀) | `캡처`·`체크`·`치킨`·`호환` 이 라틴 찌꺼기(`ĸó`)로 읽혔다 — 라틴 낱말 판정이 비ASCII 글자끼리 붙은 것도 셌다. 옛 판정기를 시험 안에 복사해 한국어 이름 약 1만 2천 건에서 차등으로 견줬고, 고친 뒤 회귀 0건이다 |
| 기호만 든 CP949 이름이 Shift_JIS 로 읽혔다 | 반각 문장 부호(U+FF61~FF65)를 일본어 글자로 셌다 — `DCIM · Pictures` 가 Shift_JIS 로 이겼다. 고치자 Big5 이름도 옳게 읽힌다 |
| 작은 `.tar.gz` 하나가 힙을 다 썼다 | 이름 상한이 머리 하나에만 있었다. 하나 64 KiB, 한 번 훑는 동안의 합 16 MiB. 이어 붙인 PAX 머리의 희소 열쇠도 한없이 쌓였다 |
| PAX 시각이 넘치면 조용히 틀린 날짜, 백만 자리 값은 제곱 시간 | `BigDecimal.toLong` 은 넘쳐도 말하지 않는다. 64자·밀리초 범위를 넘으면 머리의 시각을 쓴다 |
| **작은 docx 하나가 SmartArt 로 앱을 죽일 수 있었다** | 상한이 글자 수(400만 자)뿐이라, 한 글자짜리 도형 수천 개를 가진 그림 수백 개가 상한 안에서 수백 MB 를 쥐었다. 그림 하나에 글자·문단·도형 상한, 문서 전체에 무게 8 MiB, 같은 데이터 부분은 한 번만 읽는다 |
| 같은 `w:id` 의 메모가 둘이면 상자도 둘 | 첫째만 그리고 센다 |
| 끌어 닫기가 도중에 끊기면 모든 장이 반쯤 내려간 채 남았다 | 되돌리는 애니메이션이 끌던 장의 인식기 스코프에서 돌아, 그 장이 컴포지션에서 빠지면(되돌리는 동안 두 장을 넘기면) 멈췄다. 닫히던 중이었으면 끌어 닫기가 영영 받지 않았다. 상태가 그 작업을 든다(`DismissState`) — 컴포즈 상태와 `Animatable` 이 안드로이드 라이브러리의 JVM 시험에서 그대로 도는 첫 사례다 |
| 움직이는 그림의 까닭 문구가 화면 모듈에 있었다 | 까닭을 만드는 타입(`AnimationPlan.StillReason`)이 `core:ui` 다 — 함정 표의 '문구는 그것을 만들어 내는 타입 곁에 둬라'. 만화 뷰어의 APNG 문구도 그 한 벌을 쓴다 |

**합치며 앱에서 붙인 것.** 풀기 결과 알림의 '열기'(동작 단추가 붙으므로 기간을 `Long` 으로 적는다 — 함정 표), 다음 권으로 넘어가면
앱의 화면 상태도 그 책을 가리키게(`AppScreen.Comic` — 프로세스가 죽었다 살아나도 보던 책으로 돌아온다), 만화의 쪽 배치와 PDF 두 쪽
보기를 설정으로 남기기(꾸러미는 공용 계약에 열쇠가 없어 세션 동안만 남겼다 — `AppPreferences.comicPageLayout`·`pdfSpread` 를 더했다.
늦게 끝난 설정 읽기가 사용자가 방금 고른 것을 덮지 않게 깃발을 둔다), `MimeResolver` 가 `.mdown`·`.mkd`·`.mkdn`·`.mdwn` 을 텍스트로
(미리보기는 알아보는데 목록이 '기타' 로 보내 뷰어에 닿지 않았다).
압축 쪽에서 둘을 더 붙였다 — `ComicOpen` 의 여는 문이 앞 16바이트의 매직(`probeContainer`)에서 `Archives.detect` 로 바뀌어
`.cbt` 와 tar 안의 그림이 만화 뷰어로 열린다(tar 의 표지는 257바이트 자리에 있다. `ComicSourceTest` 셋이 박고, 옛 문으로
되돌리면 둘이 깨진다). `MimeResolver` 가 `.tbz2`·`.tbz`·`.txz` 를 압축으로 본다.

**합친 뒤 고친 것.** 한컴 고지 문장의 옛한글 음절(함정 표 '명세가 요구한 문장'), 마크다운 미리보기가 글자 크기를 바꿀 때마다
다시 만들어져 읽던 자리가 맨 위로 가던 것(`textZoom` 으로 그 자리에서 건다), **암호 PDF 를 풀어 쓴 xref 가 기기의 로캘로 숫자를
적던 것**(`String.format` — 아랍어·페르시아어 로캘이면 풀린 PDF 가 깨진다. 모듈 lint 의 `DefaultLocale` 이 잡았다), 목록·격자 전환
단추에 이름이 없던 것(글리프가 손으로 그린 캔버스라 설명이 따로 없었다 — 문구 `browser_view_mode` 는 있는데 쓰이지 않았다),
설정의 '최근 위치 지우기'(그 표에 쓰는 코드가 한 줄도 없어 **지울 것이 언제나 없는 단추**였다 — 뺐다. 최근 위치를 기록하기 시작할
때 더한다), 줄 끝 표시 설정의 설명(`⏎` 하나만 적혀 있었다), 비어 남은 `mipmap-anydpi-v26` 폴더(lint `ObsoleteSdkInt`).

**화면 전이가 문자열이 됐다**(`AppScreen.kt` 의 `AppScreenCodec`). 진단·고지는 돌아갈 곳을 든다 — 설정에서 연 고지는 설정으로, 압축
목록에서 연 고지는 그 압축 목록의 그 폴더로. 옛 값·망가진 값은 첫 화면이다. 진단·고지 화면이 `SnackbarHost` 를 갖지 않아 Root 의
불변식('모든 화면이 알림 자리를 갖는다')을 어기고 있던 것도 여기서 고쳤다.

**텍스트 뷰어가 `rememberSaveable` 의 함정을 바라는 동작으로 쓴다** — 회전과 '새로 열기' 를 입장 표(저장 상태로 든 시각)로 가른다.
`MainActivity` 에 `SaveableStateHolder` 가 없어서 성립하는 장치라, 그것을 넣게 되면 이 장치를 다시 봐야 한다.

### 14단계 실측 (두 에뮬레이터, 릴리스 R8, 2026-09-28)

판정은 캡처의 글·화소와 uiautomator 덤프로 했다. 제스처는 `input swipe` 를 뒤에서 돌리며 그 가운데에 캡처를 떴다.

| 확인한 것 | 결과 |
|---|---|
| **당겨서 새로고침** | 항목 둘뿐이라 스크롤이 없는 폴더에서도 당겨지고, 표시가 손가락을 따라 내려오며, 목록을 비우지 않는다. 고르는 중에는 나타나지 않는다(선택도 그대로). 격자에서도 같고, 목록 가운데서 당기면 스크롤일 뿐이다 |
| 당기면 정말 다시 읽는가 | 앱이 뒤에 있는 동안 파일 하나의 **크기만** 바꾸면(폴더 시각은 그대로) 돌아와도 `2 B · 04:32` 가 남고, 당기면 `17 B · 04:34` 다. ⋮ 의 '새로고침' 도 같다 — 돌아올 때 폴더 시각만 보는 설계가 그대로 드러난다 |
| **폴더 감시** | `adb shell`(다른 프로세스, FUSE 를 지난다)이 만든 파일이 **2초 안에** 목록에 뜨고, 지우면 사라진다. 2단계가 미뤄 둔 'FileObserver 가 다른 앱의 변경을 통지하는가' 의 첫 답이다(API 31 에뮬레이터 — MediaProvider 가 바꾼 것은 재지 않았다) |
| 설정 | 모든 절이 보이고 판이 `1.0.0 (2)`. `am crash` 뒤 크래시 기록이 `1건 · 마지막 04:39` 이고 시스템의 처리도 그대로 돈다(앞 처리기를 잇는다). '공유' 는 `iroiro-crash.txt` 의 공유 시트이고 화면에는 내용이 없다 |
| 한컴 고지 | 옛한글 음절 ᄒᆞᆫ 이 시스템 글꼴에서 한 글자로 조합되어 그려진다 |
| 만화 | 세로에서 한 쪽. 가로로 돌리면 표지 혼자 → `2–3 / 12` 두 쪽. 오→왼이면 3쪽이 왼쪽이다. 쪽 목록에 열두 쪽의 색이 뜨고 지금 쪽에 테두리. 12쪽을 누르면 `끝까지 읽었습니다. 다음 권: 만화2` + '열기'. ⋮ 의 '다음 권: 만화2' 로 열면 1쪽이고 앞 책의 알림이 따라오지 않는다 |
| 목록 배지 | 끝까지 본 책 `다 읽음`, 2쪽에서 나온 책 `2/12쪽` + 가는 막대 |
| 이미지 뷰어 | GIF 가 돈다(0.4초 간격의 캡처가 다르다). **두 번 두드려 확대한 채로도 돈다** — 검토가 hwui 소스로 내린 결론을 기기에서 확인했다. 짧게 끌면 되돌아오고 길게 끌면 닫혀 폴더로 간다 |
| 마크다운 | 제목·강조·취소선·중첩 목록·할 일·인용·오른쪽 정렬 칸·코드 강조가 그려지고 `<script>` 는 글자로 보인다. 바깥 링크는 눌러도 화면이 화소까지 같고, `#목록` 은 그 제목으로 간다. 글자를 두 번 키워도 읽던 자리가 남는다 |
| 줄 끝 표시 | `↓`(LF)·`⏎`(CRLF)·`←`(CR)가 흐리게. 바꾼 글자 크기가 다음 파일에도 걸린다 |
| **세로쓰기의 첫 줄**(E03, 태블릿) | 옆으로 넘치는 장(3장)이 오른쪽 끝에서 시작하고 첫 줄이 온전하다 — 11단계부터 반쯤 걸리던 것. 어둡게·120% 로 바꿔 다시 흘러도 그 자리다 |
| 장 안쪽 위치 | 3장을 옆으로 두 번 밀고 닫았다 다시 열면 `3장부터 이어서 봅니다` 와 함께 **같은 줄들**이 보인다(세로쓰기·오→왼 책) |
| PDF | 태블릿(API 35): 두 쪽 `2–3 / 70` 이고 이음매에 번짐이 없다. 찾기 `Mach` 가 `1 / 68`, 강조가 그 낱말 위에 앉는다. 폰(API 31): 찾기 단추가 없고 세로에서는 두 쪽도 없다 |
| tar | `.cbt` 가 3쪽 만화로 열리고 쪽마다 색이 맞다. `.tar.gz` 목록이 `TAR.GZ · 항목 5개 · 1.0 KB · 연속 압축`. 전부 풀기가 새 폴더 `backup`(두 겹 확장자를 뗐다)에 `4개 완료` + '열기' — 누르면 압축 화면에서 그 폴더로 간다. `pages` 는 아카이브에 적힌 시각이고, 항목이 없던 `docs` 는 지금 시각이다. 임시 파일 0 |
| docx | 메모 표지 `[MK1]`·`[MK2]` 와 부분 끝의 메모 쪽(지은이·문단 둘). SmartArt 가 `a`·`b`·`c` 차례로 점선 상자에 |
| 계측 | 89건씩, 두 기기 모두 통과(폰의 docview 1건은 API 35 전용이라 `Assume` 으로 건너뛴다) |
| 권한 | `INTERNET` 없음. 새로 든 것은 휴지통 예약의 `RECEIVE_BOOT_COMPLETED` 하나다. 릴리스 APK 6,831,908 바이트 (그 뒤 '다른 앱으로 열기' 가 `REQUEST_INSTALL_PACKAGES` 와 `<queries>` 를 더했다 — 6,883,260 바이트) |

**확인하지 못한 것.** 실기기는 하지 않았다(요청). 그 밖에 재지 않은 것: `FileObserver` 가 MediaProvider 의 변경을 통지하는가(`adb shell` 의 변경은 통지한다 — 아래 실측),
FAT 폴더 시각의 실제 정밀도, JobScheduler 가 유휴 조건에서 하루 한 번 실제로 도는가, 안드로이드 정규식의 `\w` 가 유니코드인가(크래시
기록이 더 지우는 쪽으로만 갈린다), CommonMark 공식 시험 묶음(내려받지 않았다 — 손으로 옮긴 예제·탐침 44건·무작위 30만 건뿐이다),
새 쪽을 열 때 앞 쪽의 늦은 `onPageFinished` 가 새 쪽을 '다 읽힘' 으로 표시할 가능성(URL 로 거르지 않았다 — WebView 가 주는 URL 의
모양을 기기에서 확인하지 않았고, 잘못 거르면 위치 되살리기가 통째로 죽는다).
그리고 압축에서 RAR 의 진행률(표본이 없다), `.tar.bz2` 가 1 GiB 판정에 닿기까지 폰에서 걸리는 시간(수십 초로 짐작), 이름 폭탄의 기기
거동, 이미지에서 방향 판정이 실사진에서 답을 내는 비율(대칭에 가까운 사진은 흐린 채 둔다), 변환기에서 깨진 메모 부분에 대한 안드로이드
XML 파서의 실패 모양.

### 14단계 뒤 — 다른 앱으로 열기 (2026-09-28)

사용자 요청: '이 앱이 지원하지 않는 파일은 다른 지원하는 앱을 이용해 열 수 있게 구현해주세요. 예. APK 파일'.

**계약은 `core:io` 의 `ExternalOpen` 하나다** — `open(context, path, mode)` 과 `messageOf(result)`, MIME 은 `ExternalMime`. 문구
(`io_open_with`·`io_open_with_any` 와 실패 문장 셋)도 거기 한 벌이고 목록과 뷰어 여섯이 그것을 쓴다(함정 표의 '문구는 그것을 만들어 내는
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
| MIME 을 **차례로 좁힌다** — 우리 표 → 플랫폼 `MimeTypeMap` → 종류의 대표 → 모든 것 | 받는 앱은 인텐트의 MIME 과 **자기 필터**를 맞춰 본다. `octet-stream` 을 주면 그것을 선언한 드문 앱 말고는 아무도 나오지 않는다. 그래서 우리 표에는 이 앱이 열지 않는 형식(`.doc`·`.odt`·`.mobi`·글꼴·`.iso`·`.wma`…)도 적는다. 플랫폼 표의 `octet-stream`·모양이 이상한 값은 모르는 것으로 친다 |
| 그림·소리·영상·글은 **그 계열 안의 답만** 쓴다 | 계열 전체로 선언한 필터(`video` 뒤에 별표)는 `application/…` 을 받지 않는다(`IntentFilter.findMimeType`). `.rmvb` 의 등록값을 주면 영상 앱이 하나도 안 나온다. 글·코드는 `text/plain`(편집기가 선언하는 것) — 플랫폼 표는 `.py` 를 `text/x-python`, `.js` 를 `application/javascript` 로 준다 |
| **와일드카드면 언제나 고르는 창** | 시스템의 '다음으로 열기' 에서 '항상' 을 누르면 그 선택이 **인텐트의 MIME 그대로** 선호가 된다 — 계열 전체면 그 계열의 모든 파일이 그 앱으로 간다 |
| **이 형식을 아는 앱이 없으면 '모든 앱에서 고르기'** | 에뮬레이터에서 `.xyz`(플랫폼 표가 `chemical/x-xyz`)·`.iso`·`.mobi`·`.ttf` 의 고르는 창이 'No apps can perform this action.' 뿐이었다. 고르는 창을 띄우기 전에 그 MIME 을 받을 **다른 앱이 보이는지** 묻고(`queryIntentActivities`, `MATCH_DEFAULT_ONLY`. 우리 자신·내보내지 않은 것·우리가 못 가진 권한을 요구하는 것은 세지 않는다 — 옛 고르는 창의 `filterIneligibleActivities`), 없으면 같은 URI 를 모든 것으로 넘기는 고르는 창을 그 제목으로 띄운다(`ExternalOpen.chooserPlan` — 다만 두 에뮬레이터의 고르는 창은 VIEW 에 제목을 그리지 않았다, 아래 실측). 처음부터 모든 것인 파일(확장자 없음·어느 표도 모름)도 같은 창·같은 제목이다 — 같은 목록에 제목만 둘이면 무엇이 다른지 알 길이 없다 |
| 질의는 **넓힐지만 정한다. 막지 않는다** | 모든 것까지 받는 앱이 없다고 `NO_APP` 을 돌려주는 길은 버렸다. 공개 범위가 기기에서 다르게 돌면 모르는 파일을 하나도 못 열게 되고, 맞을 때 얻는 것은 시스템의 빈 화면 대신 우리 문장 하나다. 그래서 **고르는 창에서는 `NO_APP` 이 나오지 않는다.** `NO_APP`('이 형식을 아는 앱이 없습니다')은 곧바로 여는 VIEW 에서만 나고, 목록은 그때 정보 시트를 띄운다 — 그 시트의 단추가 모든 앱의 창으로 간다 |
| `core:io` 의 매니페스트에 `<queries>`(VIEW · `content` · 모든 형식) | API 30 부터 질의는 **우리에게 보이는 앱만** 답한다. 묻는 코드가 그 모듈에 있어 선언도 거기 두고 병합이 앱에 싣는다. **사생활** — 이 앱은 이제 content URI 를 VIEW 로 받는 설치된 앱을 볼 수 있다. 답은 넓힐지 정하는 데 한 번 쓰고 버린다(보여 주지도 저장하지도 않는다). `QUERY_ALL_PACKAGES` 는 쓰지 않고 `INTERNET` 이 없다 |
| `app` 의 매니페스트에 `REQUEST_INSTALL_PACKAGES` | API 31 설치 관리자의 dex 로 확인했다 — targetSdk 26 이상인 앱이 이것을 선언하지 않고 APK 를 보내면 `InstallStart` 가 로그 한 줄만 남기고 **조용히 닫힌다**(우리에게는 `STARTED`). 선언이 설치를 허락하지는 않는다 — 사용자가 '출처를 알 수 없는 앱' 에서 이 앱을 허락해야 하고, 그 뒤에도 설치할 때마다 설치 관리자가 묻는다. 이 앱은 스스로 무엇도 설치하지 않는다(`PackageInstaller` 세션을 열지 않는다) |
| 권한은 **그 인텐트에만, 읽기만** | URI 는 `ShareHelper.uriOf` 한 곳(`/storage/` 와 캐시의 공유 폴더만). 휴지통은 그 앞에서 `ExternalOpen.refuses` 가 거절한다. 고르는 창은 안쪽 인텐트의 데이터·깃발을 들고 고른 앱을 **우리 이름으로** 띄운다(`startAsCaller`) — 권한이 우리 FileProvider 에서 곧장 그 앱으로 간다 |
| **두 번 누르기를 막는다** | `startActivity` 는 받는 앱의 창이 뜨기 전에 돌아온다(함정 표). 같은 파일·같은 모양은 500 ms 안에 다시 띄우지 않고(`ExternalOpen.isRepeat`), 목록은 띄운 뒤 700 ms 동안 누름을 흘려보낸다(`OpenWithRules.SETTLE_MS` — 시트·메뉴가 닫힌 자리 아래의 줄이 눌리는 것까지 막는다) |
| 판단은 **모듈마다 순수 객체 하나** | `DocOpenWith`·`TextOpenWith`·`ArchiveOpenWith`·`ImageOpenWith`·`ComicOpenWith`·`PlayerOpenWith`, 목록은 `TapRoute`. 문서·텍스트·압축·재생·목록은 `else` 없는 `when` 이라 갈래가 새로 생기면 정하기 전까지 컴파일되지 않는다(함정 표의 '종류를 옮기면 …'). 만화는 집합(`FAILURES_TO_HAND_OFF`)이라 컴파일러가 막지 않고 **시험이** `ComicOpen.Kind.entries` 를 센다. 이미지는 두 값짜리 enum 의 비교다. 시험이 `FileKind.entries`·`OpenFailure` 의 하위형도 전부 센다 |
| '깨졌다' 와 '읽을 수 없다' 를 **여는 탐침**으로 가른다(`core:io` 의 `FileProbe`) | 안드로이드는 권한 거부도 `FileNotFoundException` 으로 알려, 예외만 보면 열리지 않는 파일이 '깨진 파일' 이 되고 단추가 선다. `canRead()` 가 아니라 **실제로 연다**(FUSE 에서 둘이 같다는 것을 확인하지 않았다). 여는 **사이에** 없어진 파일도 실패한 길에서 한 번 더 열어 본다(문서 `failedAfter`·압축 `failureKindOf(t, reachable)`·만화 `failedKind`). 처음에는 네 뷰어가 이 몇 줄을 모듈마다 한 벌씩 들었고 만화만 다시 열어 보지 않았다 — 한 벌로 합쳤고 모듈 쪽 함수는 한 줄 위임이다 |
| 실패 화면의 제목에 **파일 이름**(`core:model` 의 `PathNames.lastSegment`) | 텍스트·압축·문서·만화의 제목이 Ready 에서만 와서, 실패 화면에 단추만 있고 무엇을 넘기는지 보이지 않았다. 전체 경로는 적지 않는다. 만화는 열린 뒤의 이름(`Book.name`)처럼 만화·압축 확장자를 뗀다(`ComicTitle.bookNameOf`) — 다르면 열리는 순간 제목 글자가 바뀐다 |
| ⋮ 는 **파일에 닿은 뒤**에만 켜진다 | 문서·텍스트·압축의 `Loading(reached)`. 없는 파일은 `NOT_SHAREABLE` 로 걸러지지만 **있는데 열리지 않는 파일**은 걸러지지 않고 받는 앱에 넘어간다. 읽을 수 없는 파일에는 서지 않는다(문서·이미지·만화·재생은 줄이 없고, 텍스트·압축은 흐리게 남는다). 암호를 묻는 중에는 있다 — 잠긴 PDF·docx·HWPX·zip·cbz 를 다른 앱의 암호 입력으로 넘길 수 있다 |
| 재생 실패는 **그 항목에 매인다** | `PlaybackConnection.State.failure` 가 항목이 바뀌어도 남아, 앞 곡의 실패 곁에 다음 곡을 넘기는 단추가 설 수 있었고 다음 곡이 같은 값으로 실패하면 `MutableStateFlow` 가 합쳐 새 실패가 흐르지 않았다. 새 상태는 순수 함수 `State.advancedTo(frame)` 가 만들고 실패는 같은 항목일 때만 들고 간다. 재생 화면이 대신 지우던 장치 셋은 지웠다. media3 1.11.1 바이트코드 — 한 묶음의 알림은 타임라인 → 불연속 → 항목 전환 → 오류 → 트랙 차례이고 알리기 전에 상태를 갈아 둔다(새 항목의 오류를 지우지 않는다) |
| 컨테이너 실패를 **우리 말로** | `BadContainer` 가 media3 의 오류 코드 이름(`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`)을 화면에 적었다. '이 앱이 재생하지 못하는 형식입니다'(추출기가 없다 — 다른 앱이 열 수 있다)와 '파일이 깨졌거나 읽을 수 없는 모양입니다' 로 가른다 |
| 넘기면 **우리 재생은 멈춘다**(끝내지 않는다) | 같은 파일을 저쪽에서 보려는 것이라 둘이 함께 소리를 내면 안 된다. 고르는 창은 늘 `STARTED` 라 물러나도 멈춘 채다 — 재생 단추 한 번으로 돌아온다. PiP 에서는 단추도 ⋮ 도 없다 |

**화면마다 단추를 두는 실패.**

| 화면 | 둔다 | 두지 않는다 |
|---|---|---|
| 파일 관리자 | 누름(R1), 정보 시트, 하나만 고른 선택의 ⋮('다른 앱으로 열기'·'정보') | 폴더·휴지통 |
| 문서 | 다루지 않는 형식·이전 형식·풀 수 없는 잠금(DRM·인증서·HWP 암호)·깨짐·너무 큼·시간 초과 | 암호를 묻는 중(⋮ 는 있다)·권한 없음·입출력 |
| 텍스트 | 글이 아닌 파일·읽지 못하는 인코딩(UTF-32) | 읽을 수 없는 파일 |
| 압축 | 다루지 않는 형식(iso·tar 아닌 gz·bz2·xz·분할)·깨짐·너무 큼·풀 수 없는 암호 — **압축 파일 자신**을 넘긴다 | 읽을 수 없는 파일, 안의 항목 |
| 이미지 | 못 푼 그림(SVG·TIFF·못 푸는 HEIC·AVIF·깨짐) | 없거나 열리지 않는 파일 |
| 만화 | 다루지 않음·깨짐·풀 수 없는 암호·너무 큼(책 파일), 폴더 만화의 못 연 쪽(그 쪽의 파일) | 폴더 책 자체, 압축 안의 쪽, 암호를 묻는 중 |
| 재생 | 디코더 없음·실패, 추출기 없음·깨진 컨테이너, 못 트는 트랙, 파싱·디코딩·DRM 코드 | 읽을 수 없음(입출력 코드), PiP |

**두 차례, 꾸러미마다 구현 → 다른 에이전트의 적대적 검토 → 일관성만 보는 검토로 만들었다.** 검토가 잡은 것 가운데 남길 것:

- **안드로이드의 MIME 대조는 대소문자를 가린다**(함정 표) — 우리 표의 `macroEnabled` 일곱이 대문자였다.
- **종류 표가 두 벌이었다.** 시험용 표와 `kindOf` 의 `when` 이 같은 차례를 따로 들어, `when` 에만 더한 갈래가 표 시험을 비켜 갔다. 이제
  `kindOf` 가 표 하나(`extensionTable`)로 답한다. `ts` 가 영상·코드 두 표에 있던 것도 그때 보였다(`when` 이 조용히 영상을 골랐다).
- `ShareHelper.uriOf` 의 `canonicalFile` 이 `runCatching` 밖에서 `IOException` 을 내 누름 처리기를 죽일 수 있었다.
- **가려진 목록이 재생 실패를 지웠다**(에뮬레이터 실측, 함정 표) — `song.wma` 를 누르면 재생 화면의 실패 문구와 단추가 3~4초 뒤 사라졌다.
  목록이 맨 앞(RESUMED)일 때만 말하고, 말한 그 실패가 아직 남아 있을 때만 지운다(`feature:browser` 의 `PlaybackNotice`).
- 재생 상태의 뿌리 고침을 시험이 **부르지 않았다** — 시험이 `push` 의 논리를 제 안에 옮겨 적어, 고친 한 줄을 지워도 아무 시험도 깨지지
  않았다. `push()` 를 컨트롤러 읽기(`Frame`)와 순수 함수로 갈라 시험이 제품 코드를 부른다.
- 재생 화면의 ⋮ 메뉴가 **저 혼자 펼쳐졌다** — 펼침 상태가 화면 쪽에 있어, 단추가 사라진 사이(읽을 수 없는 항목·PiP·조작부 감춤)에도
  남았다가 단추가 다시 설 때 펼쳐졌다. 단추·메뉴·상태를 `PlayerMoreMenu` 하나에 담았다.
- `ModalBottomSheet` 는 별도 창이라 화면 아래의 알림을 덮는다(함정 표) — 목록은 못 띄웠을 때 시트 안에도 같은 문장을 빨갛게 적는다.

**확인하지 못한 것.** `AppsFilter`(system_server)가 `<queries>` 대로 공개하는지는 소스가 없다 — 기기의 결과(아래)로만 본다. 제3자
앱(압축·만화·전자책·HWP·글꼴 뷰어)이 실제로 거르는 MIME. FUSE 위에서 `canRead` 와 여는 답이 어긋나는 파일(에뮬레이터에서 만들지 못했다).
목록이 같은 값의 실패 둘을 한 프레임 안에 받으면 뒤의 것을 말하지 않고 지울 수 있다(값으로 견준다 — 일관성 검토가 짚었고, 보이는
문장이 같아 두었다). 폴더를 이어 틀다 난 실패가 기기에서도 위 차례로 오는지.

**실측**(두 에뮬레이터, 릴리스 R8, 2026-09-28). 표본은 `/sdcard/ow/` 에 둔 열둘이다(EasterEgg 의 APK, 플랫폼의 Roboto 글꼴, 손으로 만든 나머지).

| 확인한 것 | 결과 |
|---|---|
| APK 누르기 | API 31·35 모두 설치 관리자 → '출처를 알 수 없는 앱' 물음(35 는 `InstallStart` → `InstallStaging`). 31 에서 허락한 뒤 다시 누르면 '이 앱을 업데이트할까요?' — 취소했다 |
| APK 를 선택 ⋮ 로(CHOOSE) | API 35 의 IntentResolver 가 받는 앱 하나를 곧바로 띄우고(`realCallingUid` 가 우리다) 설치 관리자가 받아들인다. 선택이 끝나고, 메뉴가 ⋮ 바로 아래에 뜬다 |
| `data.xyz`·`Roboto-Regular.ttf` 누르기 | 정보 시트에 빨간 '이 형식을 아는 앱이 없습니다' + '다른 앱으로 열기' → 모든 것(`*/*`)의 고르는 창에 앱 열일곱(폰). HTML Viewer 를 고르면 `typ=*/*` 로 받고 권한 거부가 없다. 고치기 전에는 'No apps can perform this action.' 뿐이었다. 태블릿도 같다 |
| `disk2.iso`(32 KiB 뒤 `CD001`)·`book.mobi`·`broken.cbz` 의 실패 단추 | 셋 다 모든 것의 고르는 창. 실패 화면의 제목에 파일 이름이 뜬다(고치기 전에는 비었다) |
| 확장자 없는 `README` 누르기 | 곧바로 모든 것의 고르는 창 |
| **넓히지 않아야 할 것** | 이진 `.txt` → `text/plain` 의 고르는 창에 Chrome·HTML Viewer 둘뿐(API 31·35). SVG → 포토가 곧바로 뜬다(API 31). `<queries>` 의 공개가 두 기기에서 듣는다는 뜻이다 — 듣지 않았다면 이 둘도 모든 것으로 넓혀졌다 |
| 고르는 창의 제목 | **두 기기 모두 VIEW 의 고르는 창에 제목을 그리지 않는다.** '모든 앱에서 고르기' 와 '다른 앱으로 열기' 는 화면에서 갈리지 않고 목록만 다르다. 구분을 말로 하는 것은 **곧바로 연 길이 `NO_APP` 으로 끝났을 때**(구체적인 MIME 에 받는 앱이 없다)의 정보 시트 문장('이 형식을 아는 앱이 없습니다')뿐이다 — 처음부터 모든 것인 파일(`README`)과 ⋮·실패 단추에서 넓힌 길은 말 없이 모든 앱의 목록이 뜬다 |
| `song.wma` | '이 앱이 재생하지 못하는 형식입니다' + '다른 앱으로 열기' 가 19초 넘게 남는다(고치기 전 3~4초에 사라졌다). 이전 곡(`song2.wma`)으로 넘기면 앞 실패가 지워진다 — 오류 뒤의 플레이어는 멈춰 있어 **재생을 눌러야** 그 곡이 시도되고, 그때 선 단추는 `song2.wma` 를 넘긴다(YouTube Music 이 받았다). 목록으로 돌아오면 알림이 한 번, 폴더를 다시 들어와도 없다. 태블릿에서 12초 뒤에도 남는다 |
| 재생 화면 ⋮ | 화면 낭독 이름이 '더보기'(태블릿) — 다른 뷰어와 같다 |
| 0 으로 찬 64 KiB `.iso` | '빈 압축 파일입니다'(TAR · 항목 0개). **결함이 아니다** — 0 으로 찬 파일은 올바른 빈 tar 다(끝 표시 두 칸). 처음 만든 표본이 그것이라 진짜 모양(`CD001`)으로 다시 만들었다 |
| FATAL·ANR | 0(두 기기) |

### 에뮬레이터로 검증되지 않는 것 — 문서에 그대로 남긴다

실기기 adb 연결은 하지 않기로 했다. 아래는 **코드는 쓰되 미검증으로 남는다.**

- 블루투스 연결/해제 자동 resume/pause
- SD·USB OTG 의 실제 마운트 경로와 동작
- Doze 상태의 장시간 재생
- 기기별 하드웨어 코덱(AC-3·DTS·HEVC 10bit), AVIF·HEIC 지원 여부

---

## 모듈 구조

```
app/                      조립만. 화면 전이와 이음매 꽂기(ExtractSupport·CoverSupport·
                          PlayerSupport·DocumentSupport)
                          ※ `FormatRegistry` 는 **11단계가 만들었다** — 무엇을 무엇으로
                            여는지 아는 유일한 곳. 판별기(`FormatProbe`)를 여기서 돌린다
core/
  model/   [순수 JVM]     FileEntry 등 값 타입 + IroDispatchers + DayBucket(날짜 묶기)
  safety/  [순수 JVM] 2   상한·스트림 가드·SafeXml·ImageLimits·ComicLimits — 방어 상한의 유일한 지점
  charset/ [순수 JVM] 7   인코딩 감지. 판정을 부르는 곳은 `feature:text`·`core:playback`(자막)·`format:archive`(파일명)
  io/      [Android]      볼륨·권한·나열·파일조작·휴지통
  data/    [Android]  2   Room + DataStore
  ui/      [Android]      테마·공용 컴포저블·제스처·이미지 디코딩·ThumbnailStore·애니메이션 페인터
  playback/[Android]  5   MediaSessionService·세션·커넥션·이어보기·미니 바·문구. **화면은 없다**(10)
                          ※ `core:model` 을 **api** 로 낸다 — `FileEntry`·`FileKind` 가 공개 API 에 있다
  webhost/ [Android] 11   잠금 WebView 호스트(`LockedWebView`·`WebHost`). 포맷을 모른다
format/    ★ 전부 java-library(순수 JVM). android.* 가 컴파일 단계에서 막힌다
  api/     2   DocumentSource·DocumentOpener·OpenFailure·ProgressSink
  archive/ 8·14(tar)   cfb/ 12(암호 OOXML)·13(HWP)   text/ 7·14(마크다운)   html/ 11(위생)·13(흐름 문서 바탕·한글 공용 규칙)   epub/ 11
  opc/ 12  OPC 패키지·관계·`OpcFlowDocument`(바탕의 얇은 자리)·`OoxmlOpener`·`OfficeCfb`(MS-OFFCRYPTO)
           ※ 흐름 문서 바탕(`FlowDocumentBase`·`FlowPackage`)은 13단계가 `format:html` 로 옮겼다
  docx/ xlsx/ pptx/ 12   변환기 셋. `OpcFlowDocument` 를 상속해 **부분 하나의 본문**만 쓴다
  hwpx/ hwp5/  13   변환기 둘. `FlowDocumentBase` 를 상속한다(HWPX 는 ZIP + OWPML, HWP 는 CFB 위)
feature/   browser(3·4·즐겨찾기·갤러리) image(6) text(7) archive(8) comic(9)
           player(10) docview(11) diag(2) settings(14)
           ※ `feature:docview` 가 보는 포맷 모듈은 **`format:epub` 하나뿐이다** — PDF 는
             `android.graphics.pdf` 라 순수 JVM 인 `format/` 에 둘 수 없고, 계약
             (`DocumentOpener`)만 `format:api` 에서 가져온다. **12단계의 docx·xlsx·pptx 도
             보지 않는다** — 화면이 아는 것은 `format:api` 의 `FlowDocument` 계약 하나이고,
             13단계의 HWPX·HWP 도 같은 계약으로 붙었다(규칙이 허락하는 '자기 포맷 하나' 가 이미
             `format:epub` 이다)
samples-local/            .gitignore 대상. 실세계 문서는 절대 커밋하지 않는다
```

**`feature:player` 는 10단계가 만들었다.** 5단계는 재생 화면을 `core:playback` 안에
두었는데(서비스와 컨트롤러가 거기 있고 화면이 그 둘에만 기댔다), 10단계가 자막·PiP·
제스처·속도/트랙/A-B 를 더하면 화면이 모듈의 대부분이 된다. 그래서 **화면만 옮겼다** —
`PlayerActivity` 와 10단계의 화면 코드는 `feature:player`, 서비스·세션·커넥션·이어보기·
미니 바·문구는 `core:playback` 이다.

**미니 바를 함께 옮기지 않은 것이 요점이다.** `feature:browser` 가 그것을 그리므로
`feature:player` 로 옮기면 feature 끼리 참조가 된다. 대신 미니 바가 화면을 열던 자리
(`Intent(context, PlayerActivity::class.java)`)를 **세 번째 이음매**로 끊었다 —
`PlayerSupport` + `interface PlayerLauncher`, 구현은 `feature:player` 의 `PlayerEntry`,
꽂는 것은 `IroiroApp.onCreate`. `ExtractSupport`(8)·`CoverSupport`(9)와 같은 모양이다.

**이 이음매만 함수가 둘이다** — `open(context, showPlaylist)` 과
`intent(context, showPlaylist)`. 재생 알림이 누를 곳을 지정하려면 `PendingIntent` 가
필요하고, 그것은 '실행' 이 아니라 **인텐트** 를 요구한다. 세션은 `core:playback` 이
만드는데 화면은 `feature:player` 에 있으므로 그 인텐트도 이음매를 건너와야 한다.

**옮기면서 얻은 것**: `IroiroTheme` 을 쓰는 것이 `PlayerActivity` 하나였으므로
`core:playback` 의 `core:ui`·`activity-compose` 의존이 사라졌다. 아래 의존 표의
`core:playback` 예외가 셋에서 **둘**로 줄었다.

**이사는 컴파일로 확인하지 않았다.** 기능이 하나도 바뀌지 않아야 하는 커밋이라
병합 매니페스트에서 `PlayerActivity` 의 네 속성(`exported=false`·`singleTask`·
`configChanges` 넷·`supportsPictureInPicture`)이 글자 그대로 남았는지 보고, 기기에서
`dumpsys activity` 로 `.player.PlayerActivity` 가 뜨는 것과 자동회전을 끈 채 가로
재생(`cur=2400x1080`)이 그대로인 것을 확인했다.

**의존 방향을 어기는 것은 회귀다.**

| 모듈 | 참조해도 되는 것 |
|---|---|
| `app` | 전부 |
| `feature:*` | `core:*`, `format:api`, 자기 포맷 모듈 하나. **feature 끼리 참조 금지** |
| `format:*` | `core:model`, `core:safety`, **`core:charset`**, `format:api`, coroutines-core. 그 안에서 기반으로 삼아도 되는 것은 `api`·`opc`·`cfb`·`archive`·**`html`** 다섯뿐 |
| `core:io/data/ui/webhost` | `core:model`, `core:safety`, `core:charset` |
| `app` | 위의 전부에 더해 **`format:archive`·`format:epub`·`format:opc`·`format:docx`·`format:xlsx`·`format:pptx`·`format:hwpx`·`format:hwp5` 도 직접 본다** — `FormatRegistry` 가 판별에 ZIP 엔트리 이름을 보고(docx·xlsx·pptx·hwpx 가 전부 ZIP 이라 이름 없이는 갈리지 않는다) 여는이를 골라야 하고, `OoxmlOpener` 에 세 변환기를 이어 주는 것도 여기다(`format:opc` 는 변환기를 모른다 — 변환기가 그 위에 선다). 조립이 `app` 의 일이다 |
| `core:io` | **예외로 `core:data` 도** — 휴지통은 '파일을 옮기는 것' 과 '어디서 왔는지 적는 것' 이 한 몸이라, 갈라 놓으면 정합성이 깨지는 자리에 인터페이스 한 겹이 더 생길 뿐이다 |
| `core:playback` | **예외로 `core:io`·`core:data` 도** — `io` 는 `Iro`·`FileKey`·`MimeResolver`, `data` 는 이어보기 테이블 때문이다. **이 행이 없어서 5단계부터 표가 코드와 어긋나 있었다**(9단계 뒤 감사에서 찾았다) — 규칙을 계약으로 읽는 사람이 `core:playback` 을 잎 모듈로 착각한다. 10단계가 화면을 `feature:player` 로 옮기면서 `core:ui` 가 빠져 셋이 둘이 됐다 |

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
`app` 이 꽂는다. **관문(`Semaphore(1)`)을 이음매 쪽에 두는 것**이 이번에 더해진 것이다:
7z 표지 셋이 동시에 열리면 사전 메모리가 세 배가 되는데, 그 약속이 구현이 바뀌어도
유지되어야 한다.

**`format:html` 이 기반 목록에 더해진 경위**(11단계). 모듈 지도는 처음부터 `html` 과
`epub` 을 나란히 적어 두었는데, 의존 표는 포맷 모듈이 기반으로 삼을 수 있는 것을
`api`·`opc`·`cfb`·`archive` 넷으로 못 박고 있었다 — **지도가 나눠 두라고 적은 것을 표가
금지하고 있었다.** `core:charset` 때와 같은 자기모순이고, 해법도 같다.

더해도 되는 근거는 `archive` 와 같다: `format:html` 은 `core:safety`·`format:api` 만 보는
**잎 모듈**이라 의존 방향이 뒤집히지 않는다. 그리고 그 자리는 11단계만의 것이 아니다 —
12·13단계의 docx·HWPX 도 쪽 재현을 포기하고 흐름 렌더로 가므로 **내놓는 것이 결국 HTML**
이고, 위생 규칙이 두 벌이 되면 한쪽만 고쳐지는 날이 온다(5단계의 '문구 두 벌').

**`core:charset` 이 표에 더해진 경위**(7단계). 모듈 지도는 처음부터 "인코딩 감지. 텍스트·
자막·zip 파일명이 공유한다" 고 적었는데 zip 파일명 판정은 `format:archive` 에 살아서,
**지도가 공유하라고 적은 것을 표가 금지하고 있었다.** 자기모순인 규칙은 "어기지 않는다" 가
성립하지 않는다. `core:charset` 은 순수 JVM 이고 `core:model` 만 보는 잎 모듈이라 의존
방향이 뒤집히지 않는다.

**zip 파일명도 14단계부터 `core:charset` 을 쓴다.** 그 전에는 표만 고치고 코드는 옮기지 않아
`format/archive/EntryNameDecoder.kt` 가 CP949 후보 탐색과 점수 판정을 **따로 한 벌 더** 갖고 있었다. 지금은 UTF-8 표시만
먼저 보고 `CharsetDetector.detect` 를 부른다(유니코드 경로 추가필드는 `ZipArchiveReader` 가 판정 앞에서 따른다).

판정(`CharsetDetector.detect`·`BinarySniffer.sniff`·`Bom.detect`)을 부르는 곳은 셋이다 — `feature:text`(화면이 판정 근거까지
그린다), `core:playback`(자막 `SubtitleSource`), `format:archive`(`EntryNameDecoder`). `format:text` 는 값 타입 `TextEncoding` 과
`WindowedDecoder` 만 쓴다. 안드로이드의 별칭(`x-windows-949` → `EUC-KR`)을 찾는 것은 `TextEncoding.CP949` 한 곳이다.

**판정 규칙을 손볼 때는 세 소비자의 시험을 함께 돌려라** — 규칙은 한 곳이지만 답이 가장 크게 흔들리는 것은 파일명이다(짧고, 사용자가
이미 그 이름을 보고 있다). `EntryNameRegressionTest`(오늘의 답)와 `EntryNameConsistencyTest`(텍스트 판정과 같은 답)가 지킨다.

**파서를 순수 JVM 에 두는 이유는 빌드 시간이 아니라 테스트 루프다.** dex·AAPT·에뮬레이터
없이 초 단위로 돌릴 수 있어야 파서 개발이 성립한다. 그래서 **이미지 디코딩은 파서가 하지
않는다** — `format:*` 은 `ImageRef`(바이트 스트림 제공자)까지만 만들고 `BitmapFactory` 는
`feature`·`core:ui` 가 부른다.

---

## 지켜야 할 규칙

### 안전

- **선언한 `foregroundServiceType` 마다 대응 권한이 있어야 한다.** `dataSync` 서비스에
  `FOREGROUND_SERVICE_DATA_SYNC` 가 없으면 `startForeground` 가 `SecurityException` 으로 즉사한다.
- **`INTERNET` 을 선언하지 않는다.** 매니페스트에 `tools:node="remove"` 를 못 박고 병합된
  매니페스트를 매 단계 grep 한다. 이 한 줄이 EPUB 의 추적 픽셀, 문서의 외부 참조, 모든
  서드파티의 소켓을 OS 수준에서 동시에 막는다.
- **`FileProvider` 는 `/storage/` 와 캐시 하위 폴더만 노출한다.** `root-path "/"` 로 두면
  앱 내부 DB·크래시 로그까지 넘어간다. `getUriForFile` 호출은 한 함수에 가두고 그 안에서
  `canonicalFile` 이 허용 루트 아래인지 검사한다.
- **다른 앱에 넘기는 파일도 그 URI 로만, 읽기 권한만 준다**(`ExternalOpen` — 14단계 뒤 절). 휴지통은 볼륨 안이어도 넘기지 않는다.
  이 일 때문에 권한 하나(`REQUEST_INSTALL_PACKAGES` — APK 를 설치 관리자에 넘길 때 선언이 없으면 말없이 닫힌다)와 `<queries>`
  (content URI 를 VIEW 로 받는 앱이 보인다)가 늘었다. 이 앱은 스스로 무엇도 설치하지 않고, 본 앱 목록을 보여 주거나 저장하지 않는다.
- **파서 방어 상한을 한곳(`core:safety`)에 못 박는다.** 엔트리 10,000 / 단일 해제 256MB /
  총 해제 1GB / 압축비 100:1 / XML 중첩 256 / 파일당 30초 / **재생 큐 500항목**.
  **선언된 크기를 믿지 않고 실제 읽은 바이트를 센다.** 오프셋은 `Long` 으로 읽고
  `Math.toIntExact` 로 변환한다.
  **단서 — 포맷의 구조 상한은 그 포맷 곁에 둔다**(12단계에서 정했다). 시트의 행·칸, docx 의 표 깊이,
  슬라이드 수, 관계 파일 하나의 항목 수처럼 **그 포맷만 아는** 상한은 모듈의 한 파일(`XlsxLimits`)이나
  그 클래스의 동반 객체에 모아 둔다. `core:safety` 에 두면 거기가 모든 포맷의 구조를 알아야 한다.
  **여러 포맷이 같이 쓰는 예산**(위의 목록, 암호를 푼 평문 한 벌의 `DecryptLimits`)만 `core:safety` 다.
- **컨테이너 깊이 상한은 없다. `maxContainerDepth` 는 11단계가 지웠다.**
  2단계가 선언해 두고 9단계 뒤 감사까지 읽는 코드가 한 줄도 없었다. 11단계가 그 미결을
  **세는 코드를 넣는 쪽이 아니라 상수를 지우는 쪽으로** 끝냈다 — 깊이를 세려면 '지금 몇
  겹인가' 를 아는 주체가 있어야 하는데, **뷰어에 넘기는 것이 언제나 파일 경로**라
  (`DocumentSource.asFile`) 아카이브 엔트리는 애초에 뷰어에 닿지 않는다. 컨테이너 안의
  컨테이너는 **열리지 않아서** 막히는 것이지 세어져서 막히는 것이 아니다. 실제로 막는
  자리 셋 — 압축 목록의 중첩 아카이브와 문서(둘 다 '풀어서 여세요'), 그리고
  `PdfOpener` 의 `asFile() ?: Unsupported`.
  **12·13단계가 OOXML·HWPX 를 붙일 때 이 문장을 '이미 막혀 있다' 로 읽지 마라** —
  정말 깊이를 세야 하는 자리가 생기면 **그때 호출부에 직접 넣는다.** 아무것도 막지
  못하면서 막는다고 적혀 있는 값이 가장 나쁘다는 것이 이 항목이 남긴 교훈이다.
- **CFB(OLE2)에는 전용 불변식이 따로 필요하다** — 압축·XML 상한이 걸리지 않는 구조다.
  `sectorShift` 는 9 또는 12 만, 섹터 번호는 범위 검사 후에만 seek, 모든 체인 순회에
  방문 집합과 길이 상한, 스트림 길이는 `min(선언값, 체인길이×섹터크기)`.
- **원본 파일을 파괴할 수 있는 경로가 다섯이다** — EXIF 저장, 덮어쓰기 복사, EXDEV 이동,
  휴지통 복원 충돌, 아카이브 "여기에 풀기". 전부 **임시 파일 → fsync → 원자적 rename**,
  삭제는 성공 확인 뒤에만.
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
  넣어 **다음 문서의 화면에 배달한다**(9단계 뒤 감사에서 `ComicViewModel.restore` 가
  이 모양이었다). 정리(`finally` 안의 `close`)에만 쓰고, **suspend 호출을 감쌀 때는
  쓰지 마라** — 취소를 되던지는 try/catch 를 손으로 쓴다.
- **화면에 나가는 글자는 예외 없이 `strings.xml` 에 둔다.** 코드에 문자열 리터럴을 적지
  않는다 — 나중에 용어를 통일하려 할 때 코드를 뒤져야 하는 상태가 되면 통일이 안 된다.
  **예외는 진단 화면(`feature:diag`) 하나다.** 개발자만 보는 화면이고 용어를 통일할
  대상이 아니다. 대신 **순수 JVM 모듈은 화면 문구를 만들지 않는다** — `ArchiveSelfTest`
  가 문장이 아니라 `Check` 와 값을 돌려주는 것이 그 규칙이다. 모듈 경계가 뜻을 잃으면
  나중에 번역·용어 통일이 `format/` 까지 들어가야 한다.
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
- 문서 표본은 **공개 문서를 수집해서 쓴다**(공공기관 공개 HWPX·HWP, 오픈소스의 OOXML).
  미디어 표본은 ffmpeg 으로 만든다(도구로만 쓴다. 저장소에 넣지 않는다).

---

## 단계 로드맵

각 단계는 **설치해서 실제로 쓸 수 있는 APK** 로 끝난다. 완료 판정은 에뮬레이터에서 눈으로 본다.

| | 단계 | 상태 |
|---|---|---|
| 0 | 검증 환경 — 에뮬레이터·표본 조달 | **완료** — 에뮬레이터 둘, 문서 표본은 13단계(한글 30건)와 실세계 말뭉치(122건, `samples-local/corpus/`). 아직 없는 표본(RAR5·암호 RAR·애니 WebP·배포용 HWPX 등)은 각 단계의 '미룬 것' 에 적었다 |
| 1 | 뼈대 — 빌드·모듈·권한·R8 점화 | **완료** |
| 2 | 공통 계약 5종 + 방어 계층 + 관통 스파이크 | **완료**(아래 단서) + 적대적 검토 반영 |
| 3 | 파일 브라우저(읽기 전용) | **완료** |
| 4 | 파일 조작 + 휴지통 ← 여기서 실사용이 시작된다 | **완료** |
| 5 | 재생 기본 — 백그라운드·알림·이어폰 버튼 | **완료** + 3·4단계 적대적 검토 반영 |
| 6 | 이미지 뷰어·썸네일·EXIF·회전 | **완료**(애니메이션·APNG 는 아래 '미룬 것') |
| 7 | 텍스트·코드 뷰어 + 인코딩 감지기 | **완료**(아래 '미룬 것') |
| 8 | 압축 제품화 (ZIP·7z·RAR) | **완료**(아래 '미룬 것') |
| 9 | 만화 뷰어 | **완료** + 종료 뒤 문서·코드 대조 감사 반영(아래 '미룬 것') |
| 10 | 재생 심화 — VLC 차용·자막·PiP·제스처 | **완료** — 모듈 이사·폴더 큐·재생목록·자막·제스처에 더해 PiP·배속·트랙 선택·A-B 구간·방향 잠금까지 |
| 11 | PDF + EPUB + 공용 문서 렌더 기반 | **완료** — `feature:docview`·`PdfLimits`·`doc_progress`·`core:webhost`·`format:html`·`format:epub`·`app/FormatRegistry.kt`, 그리고 `maxContainerDepth` 결론 |
| 11+ | 암호 PDF(표준 보안 처리기) · 원본 2배 확대 · 사진·만화 영역 디코딩 · EPUB 글꼴 난독화 · 아카이브 암호(ZIP·7z, RAR 은 미검증) | **완료**(2026-09-23, 아래 '11단계 뒤' 절). 공개된 암호화의 나머지(OOXML·HWP)는 12·13단계와 함께 |
| 12a·b·c | docx → xlsx → pptx (읽히게) + 암호 OOXML(MS-OFFCRYPTO) | **완료**(2026-09-24) — `format:opc`·`format:cfb`·`format:docx`·`format:xlsx`·`format:pptx`, 흐름 문서 화면(`FlowReader`), 첫 진입 고지. 아래 '12단계가 세운 것' |
| 13a~d | HWPX → CFB·PrvText → HWP 본문 → 표·그림 | **완료**(2026-09-24) — `format:hwpx`·`format:hwp5`, 흐름 문서 바탕을 `format:html` 로(`FlowDocumentBase`), 암호 HWPX·배포용 HWP. 아래 '13단계가 세운 것' |
| 14 | 마감 — 설정·고지·릴리스 점검 | **완료**(2026-09-28) — `feature:settings`(설정·크래시 기록), 고지 화면(한컴 문서 명세의 고지 문구 포함), 1.0.0. 미룬 것 가운데 할 수 있던 것과 **당겨서 새로고침**을 함께 했다(아래 '14단계' 절). 실기기 검증은 하지 않았다(사용자 요청) |

**위험을 앞으로, 쓸 수 있는 것을 일찍, 크고 불확실한 것을 뒤로.** 12·13단계가 전체 분량의
절반이고 불확실성도 가장 크다. 뒤에 둔 것은 미완으로 끝나도 앞의 앱이 온전하도록 하는 보험이다.

### 적대적 검토에서 나온 것 (2단계 종료 후)

에이전트 12개로 여섯 관점(방어·압축·계약·안드로이드·자기규칙·완결성)을 검토하고 각
발견을 다시 반증하게 했다. **확정 84건(치명 1, 중대 24), 반증 6건.** 반증한 쪽이 실제로
코드를 돌려(에뮬레이터에 dex 를 올리고, zip 을 손으로 고쳐) 근거를 댄 것이 여섯이다.

고친 것 가운데 구조를 바꾼 것:

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
| RAR 링크 판정을 `getRedirection()` 으로 | 내가 "이 API 는 없다(실측)" 고 적은 주석이 **틀렸다** — javap 정규식이 타입명의 숫자를 걸렀다 |
| CP949 자가시험을 **고정 바이트**로 | 우리가 고른 charset 으로 쓰고 같은 것으로 읽으면 왕복이 깨질 수가 없어 아무것도 증명하지 못했다 |
| 유니코드 경로 추가필드(0x7075) 존중 | 라이브러리가 이미 푼 정답을 버리고 다시 추측해 일본어 이름을 망가뜨렸다 |

**남은 경미 59건은 목록으로만 둔다.** 대부분 주석과 코드의 어긋남, 쓰이지 않는 상수,
문구 다듬기다. 4단계에서 같은 파일을 건드릴 때 함께 정리한다.

### 적대적 검토에서 나온 것 (3·4단계 종료 후)

같은 방식으로 다섯 관점(UI·데이터손실·동시성·자기규칙·빈틈)을 검토하고 반증하게 했다.
**확정 85건(치명 4, 중대 35), 반증 4건, 검토가 놓쳤다가 반증조 쪽이 새로 찾은 것 24건.**

치명 넷이 전부 **되돌릴 수 없는 손실**이었다. 기능이 모자란 것이 아니라, 이미 있는
기능이 사용자 파일을 지우는 자리였다.

| 무엇 | 왜 |
|---|---|
| 영구 삭제에 **확인 대화상자** | '완전히 삭제'·'휴지통 비우기' 가 누르는 즉시 실행됐다. 되돌릴 수 있는 '휴지통으로 보내기' 에만 확인이 있어 **보호가 정확히 거꾸로** 걸려 있었다 |
| `nextAvailable` 이 **있는 이름을 돌려줬다** | 255바이트 이름에서 ` (2)` 가 통째로 잘려 후보가 원본과 같아졌다. 그것을 '둘 다 보관' 의 대상으로 쓴 복사가 남의 파일을 지웠다 |
| 휴지통 이동 세 쓰기를 `NonCancellable` 로 | `rename` 과 기록 쓰기 사이에서 끊기면 앱 안에서 되살릴 수 없는 uuid 덩어리가 남고, 30일 뒤 자동으로 지워졌다 |
| 고아를 **지우지 않고 되살린다** | 사이드카 시각을 못 읽으면 그 자리에서 영구 삭제했다. **판단 실패를 삭제로 해석**하는 코드였다 |
| 덮어쓰기에서 `delete()` 를 없앴다 | `rename(2)` 가 이미 원자적 대체다. 먼저 지우는 것은 원본도 복사본도 없는 창만 만들었고, 대상이 빈 폴더면 사용자의 폴더가 파일로 바뀌었다 |
| 항목 실패가 **배치를 끝내지 않는다** | 1,000개 중 3번째가 실패하면 나머지 997개를 시도조차 않고 진행 수까지 버렸다. 멈추는 것은 저장공간 부족 하나뿐 |
| 결과를 상태가 아니라 **사건(Channel)으로** | `StateFlow` 라 다음 작업의 `Running` 이 앞 결과를 덮었고, 화면이 없는 동안 끝난 '3개 실패' 는 아무에게도 전해지지 않았다 |
| 결과 소비를 **BrowserScreen 한 곳으로** | 폴더 화면만 소비해서 휴지통에서 누른 영구 삭제의 성패가 어디에도 안 보였다 |
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

**계측 회귀 6건을 새로 박았다**(255바이트 이름·종류 다른 충돌·항목 실패 계속·수정시각
보존·고아 되살리기·볼륨 없을 때 기록 보존). 계측 테스트는 13 → **19개**다.

**반증된 것도 기록한다.** '취소 한 번으로 파일이 영구 삭제된다' 는 시나리오는 과장이었다 —
사이드카를 `insert` **앞**에 쓰므로 그 항목은 30일간 보존된다. 파일 자체가 아니라
'앱 안에서의 복원 경로' 를 잃는 것이었다. 고친 것은 같지만 심각도 판단이 달라진다.

### 2단계에서 미룬 것과 그 이유

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| ~~HWP 관통(CFB + PrvText)~~ | **13에서 했다** | 선행조건(표본 20~30개)을 먼저 채웠다 — 공공 문서 29건(`samples-local/hwp/`). PrvText 를 거치지 않고 본문 레코드를 곧바로 읽는다 |
| 압축 RAR5 회귀 시험 | 표본이 생기는 대로 | junrar 에 쓰기 구현이 없어 표본을 만들 수 없다. 진단 화면이 이 사실을 '미검증' 으로 표시한다 |
| ~~solid 7z 페이지 전환 지연 실측~~ | **9에서 쟀다** | 창 안 0 ms / 창 밖 1.4~2.1초(66 MB·30쪽). 실측표 참고 |
| ~~`FileObserver` 가 다른 앱의 변경을 통지하는가~~ | **14단계에서 붙였다**(재지는 않았다) | 폴더 감시(`FolderWatch`)가 섰다. 다른 앱·MediaProvider 의 변경을 통지하는지는 **아직 재지 않았다** — 그래서 앞으로 돌아올 때 폴더의 수정 시각을 한 번 더 본다(아래 14단계 절) |
| ~~FUSE 창 단위 읽기 속도~~ | **7에서 쟀다** | 16 MB 로그 색인 219 ms(≈73 MB/s). 실측표 참고 |
| ~~크래시 기록기~~ | **14단계에서 했다** | `core:data` 의 `CrashLog`(아래 14단계 절) |

### 3·4단계 검토에서 나왔지만 미루는 것

| 미룬 것 | 왜 |
|---|---|
| ~~**휴지통 자동 비우기가 앱을 켤 때만 돈다**~~ | **14단계에서 했다** — 새 의존 없이 JobScheduler 로(`TrashPurgeJob`, 하루 한 번·유휴 조건). 앱 시작 때의 정리도 그대로 돈다. 예약된 정리는 조용한 작업이라 포그라운드 서비스를 띄우지 않는다(백그라운드에서 띄우지 못하는 Android 12 제한). **유휴 조건에서 실제로 도는지는 재지 않았다** |
| **`MediaIndex.scanAll` 이 정말 필요한가** | 이 에뮬레이터(API 31)에서 재보니 삭제·이름변경·이동이 **스캔 없이** MediaStore 에 반영된다(내부·FAT SD 모두). 실측표의 '사진 삭제 → MediaStore 항목도 사라진다' 는 `scanAll` 이 한 일을 증명하지 않는다. 1만 개 배치마다 도는 바인더 호출이 값 없이 남아 있을 수 있다 — **실기기에서 한 번 확인한 뒤에 빼거나 남긴다** |
| ~~출처를 모르는 휴지통 항목을 **사용자가 고른 폴더로** 복원~~ | **14단계에서 했다** — '다른 곳에 복원' 이 붙여넣기 막대 같은 고르기 상태로 들어가고, 고른 폴더에서 '여기에 복원'. 옮기는 길은 다른 조작과 같다(원자적 rename·EXDEV 복사·이름 충돌 규칙) |

### 6단계에서 미룬 것

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| ~~**애니메이션 재생(GIF·애니WebP)**~~ | **만화 뷰어는 9단계, 이미지 뷰어는 14단계가 했다** | 6단계가 적어 둔 함정이 그대로 맞았다 — `Drawable.setCallback` 은 **약한 참조**라, 익명 객체로 넘기면 GC 뒤에 무효화가 조용히 멎고 첫 장면에서 멈춘다. 그래서 콜백을 `AnimatedImagePainter` 의 **필드**로 들고 수명을 `RememberObserver` 로 컴포지션에 묶었다. **13단계까지 그 페인터를 쓰는 것은 `feature:comic` 뿐이었다** — 목록에서 `.gif`·애니WebP 를 탭해 여는 `feature:image` 의 `ImageViewerScreen` 은 `ImageIo.decodeFitted` 로 정지 한 장만 떠서 **첫 장면에서 멈춘 채 아무 말도 하지 않았다**(9단계 뒤 감사에서 찾았다. 문서가 '두 뷰어가 같은 페인터를 쓴다' 고 잘못 적고 있었다). 페인터는 `core:ui` 에 있어 옮길 것은 없고, 이미지 뷰어가 확대(`ZoomableImage`)와 애니메이션을 어떻게 가를지만 정하면 된다 — 만화 뷰어는 '움직이는 쪽은 확대하지 않는다' 로 갈랐다. **14단계는 이미지 뷰어에서 확대를 허락했다**(아래 14단계 절). 트는 것은 자리 잡은 장 하나뿐이고, 표본을 버퍼 셋이 한 화면 크기 비트맵에 들 때까지 줄이며, 그래도 안 들면 첫 장면과 까닭을 보인다 |
| **APNG** | **넣지 않기로 했다** | 9단계에서 다시 보고 결론을 내렸다(아래 '지원하지 않는 것'). 플랫폼이 `isAnimated=false` 로 주므로 직접 청크를 분해해 합성해야 하는데, 그것은 우리가 CRC 를 다시 계산해 skia 에 먹이는 일이다. 대신 **판별기(`ImageFormats.isApng`)로 화면이 "움직이지만 첫 장면만 보여 줍니다" 라고 말한다** |
| ~~**영역 디코딩(확대했을 때 선명해지기)**~~ | **11단계 뒤에 했다**(사진·만화) | 최대 배율을 원본의 2배로 올리면서 필요해졌다(아래 '11단계 뒤' 절). 90°·270° 는 `RegionMath` 가 좌표를 옮기고 조각을 돌린다. ~~180°·거울상은 여전히 흐린 채로 확대된다~~ — **14단계가 여덟 방향을 다 옮긴다.** 디코더가 방향을 적용했는지는 여전히 미리 알 수 없어(치수가 안 바뀐다), 조각이 처음 필요할 때 한 번 원본을 두 디코더로 작게 떠 견준다(`OrientationMatch`). 한쪽이 뚜렷할 때만 답하고, 모르면 조각 없이 흐리게 둔다 — 대칭에 가까운 사진이 그렇다. 만화는 정방향 쪽만 조각을 얹는다 |
| ~~아래로 끌어 닫기~~ | **14단계에서 했다**(이미지 뷰어) | 배율 1 에서만. 인식기 하나가 터치 슬롭에서 축을 한 번 정한다(아래가 우세하면 닫기, 가로·위·두 손가락·아래층이 소비한 것은 남의 것). 뷰포트의 15% 를 넘기거나 1000 dp/s 로 튕기면 닫는다. 만화 뷰어에는 넣지 않았다 — 세로 스크롤 모드와 중재가 한 겹 더 겹친다 |
| 진입 확대 애니메이션 | 필요해지면(14단계도 하지 않았다 — 모듈을 건넌다) | **옛 이유("파일 목록에 출발 썸네일이 없다")는 틀렸다** — 6단계 썸네일 개편으로 목록·격자 둘 다 `Thumb` 을 그린다(위 실측표). 남은 진짜 이유는 값이다: 출발 경계를 뷰어까지 옮기려면 공유 요소 전이를 세우거나 좌표를 손으로 넘겨야 하고, 그림·만화·재생 셋에 각각 붙는다 |
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

**실측이 잡은 결함 — CRLF 파일의 앵커가 하나뿐이었다.**

`
` 자리에서는 줄 시작을 아직 모른다(다음 바이트가 `
` 이면 한 칸 더 밀린다). 그래서
"캐리지리턴 뒤에는 앵커를 찍지 않는다" 고 썼는데, **CRLF 파일은 모든 줄 끝이 `
`** 이라
그 규칙이 건너뛰기가 아니라 **영구 억제**가 됐다. 20만 줄 로그의 앵커가 1개가 되어 끝으로
뛸 때마다 16 MB 를 처음부터 다시 읽었다.

**이 결함을 JVM 시험 26건이 전부 놓쳤다.** 행 수도 글자도 옳았기 때문이다 — 틀린 것은
성능뿐이었고, 등가성 시험은 성능을 보지 않는다. 기기에서 `앵커 1` 이라는 숫자를 눈으로
보고서야 잡혔다. **그래서 `AnchorReproTest` 는 성능 불변식을 직접 단언한다**(줄 끝 세
종류가 같은 수의 앵커를 내는가, 앵커 간격이 상한 안에 있는가). 고친 방법은 앵커를 버리지
않고 **한 바이트 미루는** 것이다.

**교훈 둘.** ① 실측 기록에 쓰는 숫자를 늘려라 — 재지 않았으면 못 잡았다.
② **성능 불변식은 정확성 시험이 대신 지켜 주지 않는다.**

### 7단계에서 미룬 것

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| ~~**설정이 화면을 벗어나면 사라진다**(줄 접기·줄 번호·글자 크기)~~ | **14단계에서 했다** | 뷰어가 설정의 기본값(`AppPreferences.textViewer`)에서 시작하고 바꾸면 곧바로 적는다. 설정 화면에서도 고친다 |
| 찾기가 **파일 전체를 훑는다** | 필요해지면 | 16 MB 에서 몇 초 걸린다. 취소가 있고 **진행률은 14단계가 붙였다**('찾는 중… N%' 와 아래 막대 위에 겹친 가는 막대). 찾기는 색인이 아는 행까지만 훑는다 — 보고 있는 로그에 줄이 덧붙어도 끝난다. 색인에 낱말을 넣는 것은 '전문검색 인덱스' 라 1단계에서 제외한 항목이다 |
| **자동 새로고침 없음** | 필요해지면 | 보고 있는 파일이 밖에서 바뀌면 색인이 낡는다. `RowIndex.sourceBytes` 가 그것을 감지할 자리를 이미 들고 있다. 14단계가 **폴더** 감시를 붙였지만 텍스트 뷰어는 쓰지 않는다 — 붙일 때는 같은 경로의 `FileObserver` 둘이 서로를 끊는다는 함정을 먼저 본다 |
| 문법 강조 언어 **23종** | 늘어나는 대로 | 훑개는 둘(중괄호 계열·마크업)이고 나머지는 자료다. 새 언어는 `TextLanguage` 에 낱말 목록만 더하면 된다 |
| ~~**마크다운·줄바꿈 문자 보기**(`⏎` 표시)~~ | **14단계에서 했다** | 줄 끝 표시(LF `↓`·CRLF `⏎`·CR `←`)와 마크다운 미리보기(아래 14단계 절) |
| 조각 경계에서 낱말이 잘리면 색이 틀린다 | 고치지 않는다 | 4,096자에서 자른 자리의 `var` 가 `v` + `ar` 로 갈리면 뒤쪽은 낱말이 아니다. 미니파이 파일에서만 보이고, 고치려면 조각 경계를 낱말 경계로 옮겨야 하는데 그러면 행 길이가 들쭉날쭉해진다 |

### 8단계가 세운 것과, 검토가 잡은 것

**설계를 다섯 관점(탐색·풀기·미리보기·solid·안전)으로 짜고 각각을 두 렌즈(데이터 손실·
자기규칙)로 반증하게 했다. 설계 5건, 반증 121건(치명 21).** 그 가운데 **치명 하나가
2단계부터 있던 결함**이었다 — 460 KB 를 넘는 ZIP 을 하나도 못 열고 있었다(위 함정 표).
자가시험 표본이 전부 수백 바이트라 여섯 단계 동안 아무도 몰랐다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 푸는 길을 **순차 하나로** | `open()` 반복은 7z 에서 제곱이다(실측 3.85배). 1,000개짜리가 몇 분 걸린다 |
| 순차 API 를 **미는(push) 모양**으로 | junrar 의 `getInputStream` 이 새 스레드를 띄워 취소가 닿지 않는다. 우리가 `OutputStream` 을 쥐어야 해제 루프 안에서 인터럽트를 본다 |
| 트리를 **순수 JVM 에서** | 디렉터리 엔트리 없는 zip, 같은 이름 둘, 파일이자 폴더인 이름 — 경계가 많고 전부 초 단위 시험으로 돈다. 9단계 만화 뷰어가 같은 그룹핑을 쓴다 |
| 풀기를 **파일 작업 큐로** | 같은 폴더를 두 작업이 동시에 만지지 않고, 포그라운드 서비스·진행률·취소·결과 보고를 그대로 얻는다 |
| 이름 계산을 **한 함수로**(`ExtractNames`) | 화면의 충돌 선검사와 엔진이 다르게 계산하면 사용자가 본 것과 덮어써지는 것이 달라진다. 4단계가 치명으로 고친 그 형태다 |
| 기본 목적지를 **'새 폴더'** 로 | 최상위가 여럿이면 흩어지고, 하나여도 그 이름이 이미 있으면 섞인다. **개수만 보지 않는다** |
| 부분 실패를 **되돌리지 않는다** | 되돌리기는 '실패했으니 사용자 파일을 지운다' 이고, 4단계 치명 넷이 전부 그 형태였다. 대신 어디까지 했는지를 싣는다 |
| 상한을 **둘로 가른다** | 엔트리 상한(크기·압축비)은 그 항목만 실패로 세고, 아카이브 상한(총량·개수)은 그 자리에서 끝낸다 — 한 번 넘으면 그 뒤 전부가 반드시 넘는다 |
| 풀기에 **별도 상한**(`ParseLimits.forExtract`) | 기본 총량 1 GiB 는 '파서가 힙에 올리는 양' 이다. 64 KiB 버퍼로 디스크에 흘려보내는 풀기에 그대로 쓰면 정상 500 MB 백업이 거절된다. 총량은 **대상 볼륨의 여유 공간**으로 바꾸고 엔트리별 방어는 그대로 둔다 |

**7z 리더에 있던 잠재 결함도 함께 고쳤다** — 이름이 없는 엔트리가 하나라도 있으면 목록의
번호와 실제 위치가 어긋나 **다른 파일의 바이트**가 열렸다(ZIP 에서 이름을 신원으로 쓰다
밟은 것과 같은 형태다). 이제 물리적 위치를 따로 센다.

**반증이 틀린 것도 있었다.** 'solid RAR 에서 `extractFile` 이 조용히 다른 바이트를 준다'
는 주장은 바이트코드를 읽어 반박했다 — junrar 는 `lastProcessedFileIndex` 로 사전
연속성을 제대로 관리한다. 실측·코드 확인 없이 받아들였으면 필요 없는 방어를 넣을 뻔했다.

### 8단계에서 미룬 것

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| ~~**아카이브 안의 파일 미리보기**~~ | **9단계에서 했다(그림만)** | 그림 항목을 탭하면 만화 뷰어가 **그 쪽에서** 열린다. 미룬 이유였던 '캐시의 자리·수명·프라이버시' 는 만화 뷰어가 **캐시를 아예 두지 않기로** 하면서 통째로 사라졌다. **글·문서·영상은 그대로 못 연다** — 그 뷰어들은 경로를 요구하고, 경로를 주려면 뽑아야 한다 |
| ~~**목록 썸네일**~~ | **9단계에서 했다** | 만화 확장자(cbz·cbr·cb7·cbt)에만 건다. 걱정했던 '12번 되풀이' 는 둘로 눌렀다 — 표지 만들기를 `Semaphore(1)` 로 한 줄로 세우고, 창이 차면 패스를 그 자리에서 끝낸다(`StopPass`) |
| **압축 만들기(zip 으로 묶기)** | 넣지 않는다 | 확정 요구사항 표의 '압축' 행은 읽기 모양이고(junrar 의 UnRAR 라이선스는 애초에 압축기 재구현을 금지한다), 만들기는 **원본을 파괴할 수 있는 여섯 번째 경로**가 된다. 필요해지면 그때 요구사항부터 고친다 |
| ~~**tar·tar.gz·tar.xz**~~ | **14단계에서 했다** | 걱정했던 '컨테이너의 뜻' 은 판별을 둘로 갈라 풀었다 — 앞머리 매직(`probeKind`)과, 압축 스트림은 안의 첫 머리까지만 풀어 보는 `Archives.detect`(목록은 세우지 않는다). `FormatId` 에는 tar 자리가 없어 형식 이름은 `ArchiveKind` 가 적는다. `.bz2`·`.xz`·`.gz` 로 싼 파일 하나(tar 가 아닌 것)는 여전히 '다루지 않는 형식' 이다 |
| ~~**폴더 수정시각 복원**~~ | **14단계에서 했다** | 적어 둔 그대로 — 모든 쓰기가 끝난 뒤 `finally` 에서 깊은 것부터. 이번 풀기가 **만든** 폴더만 건다(원래 있던 폴더의 시각은 사용자의 것이다) |
| ~~진행률의 분모~~ | **14단계에서 했다** | 포맷마다 실제로 읽은 압축 바이트다(ZIP 은 항목 스트림, 7z 는 `compressedCount`, 압축 tar 는 파일에서 읽은 바이트). RAR 은 끝난 항목의 선언값이고 **표본이 없어 확인하지 못했다**. 못 세면 선언 크기 합으로 물러난다 |
| ~~풀린 폴더로 바로 가기~~ | **14단계에서 했다** | 결과 알림의 '열기'. 하나도 못 풀고 실패했으면 단추가 없다(`FileOpManager.Finished.folderToOpen`) |


---

### 9단계에서 미룬 것

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| **움직이는 쪽은 확대되지 않는다**(만화 뷰어) | 걸리면 그때 | 처음 까닭은 '확대 변환을 얹으면 프레임마다 다시 합성해 재생이 끊긴다' 였는데 **14단계가 hwui 소스를 읽고 그 길에서는 성립하지 않는다고 보았다** — `drawAnimatedImage` 는 드로어블을 표시 목록에 한 번 기록하고 프레임은 RenderThread 가 넘기며, 확대 행렬은 그 기록을 감쌀 뿐이다(기기에서 확인하지 않았다). 이미지 뷰어는 그래서 확대를 허락했다(`ZoomablePainter`). 만화 뷰어는 그대로 둔다 — **만화책 안의 GIF 는 드물다** |
| ~~**읽기 설정이 책마다 저장된다. 기본값은 없다**~~ | **14단계에서 했다** | 새 책은 설정의 기본 방향으로 연다. **책의 기록이 이긴다** — 기본값을 바꿨다고 읽던 책이 뒤집히면 안 된다(`ReadingStart.decide`) |
| ~~**두 쪽 보기(펼침)**~~ | **14단계에서 했다** | 한 쪽 / 두 쪽 / 자동(가로 화면이면 두 쪽). 표지는 혼자 서고, 반쪽 하나의 몫이 `pageCap/2` 다(아래 14단계 절) |
| ~~**쪽 목록(썸네일 격자)**~~ | **14단계에서 했다** | 디스크에 쓰지 않는다. solid 는 아카이브 차례로 한 번 훑고, 원하는 쪽을 다 지나면 멈춘다 |
| **세로 모드에서 확대** | 필요해지면 | 띠가 `LazyColumn` 의 항목이라 확대하려면 스크롤과 변환을 함께 중재해야 한다. 웹툰은 폭 맞춤으로 읽는 것이 보통이다 |
| ~~**책 사이 이동(다음 권)**~~ | **14단계에서 했다** | 권 번호를 이름에서 읽지 **않는다** — 파일 관리자의 이름 차례(자연 정렬)에서 지금 책 바로 뒤의 권이다. 그 추측이 가장 작다 |
| ~~**읽음 표시**~~ | **14단계에서 했다** | 목록의 배지(`12/30쪽`·`다 읽음`). **스키마를 바꾸지 않았다** — 쪽·쪽 수에서 끌어낸다(`ReadingBadges`). 스키마는 여전히 3(11단계의 `doc_progress`)이다 |
| ~~부정 캐시가 **일시적 실패와 영구 실패를 가르지 않는다**~~ | **14단계에서 했다** | `ThumbnailFailures` 가 셋으로 가른다 — 영구는 디코더가 바이트를 거절한 것뿐이고, OOM·입출력은 2초부터 두 배씩 10분까지 물러나며 영구가 되지 않는다. 동영상 프레임·표지 없음은 세 번 거듭되면 영구다. 요청 병합(`SingleFlight`)도 고쳤다 — 일하던 칸이 화면을 벗어나 취소되면 기다리던 칸이 이미 디스크에 써진 썸네일 대신 배지를 그렸다 |

### 9단계 뒤 감사에서 나왔지만 미루는 것

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| ~~**인코딩 판정이 두 벌이다**~~ | **14단계에서 했다** | 적어 둔 순서대로 — 표본을 먼저 늘려 오늘의 답을 박고(`EntryNameRegressionTest`), 그다음 합쳤다. 바뀐 답은 개선으로 적었다(Shift_JIS·UTF-8 무표시 `café`·Big5). 검토가 옛 판정기를 시험 안에 복사해 한국어 이름 약 1만 2천 건으로 차등 대조했고, 합친 첫 판의 회귀 둘(`캡처` 류·기호 이름)을 고친 뒤 0건이다. **여전히 틀리는 것** — 짧은 간체 중국어 이름, ASCII 낱말 끝에 붙은 UTF-8 로도 읽히는 한 글자(`Excel처` → `Exceló`, `café` 를 살리는 대가) |
| ~~**이미지 뷰어의 애니메이션·APNG 고지**~~ | **14단계에서 했다** | 위 6단계 '미룬 것' 참고. APNG 문구는 `core:ui` 에 한 벌이고 만화 뷰어도 그것을 쓴다 |
| ~~**찾기 진행률**~~ | **14단계에서 했다** | 7단계 표 참고 |
| ~~`maxContainerDepth` 를 **지울 것인가 쓸 것인가**~~ | **11단계에서 지웠다** | 위 방어 절 참고. 세는 코드를 넣는 쪽은 '막을 수 없는 것을 막는 척하는 일' 이라 고르지 않았다 |

### 11단계에서 미룬 것 (PDF 쪽)

| 미룬 것 | 어디로 | 왜 |
|---|---|---|
| **글자 선택·복사·문서 안 찾기** | 찾기만 **API 35 에서 했다**(14단계) | 레거시 `PdfRenderer` 는 글자를 주지 않는다(확장 13 이상의 `PdfRendererPreV.selectPageText` 가 그것이고, 이 기기는 확장 1이다). API 35 의 `Page.searchText` 로 찾기와 강조 상자를 붙였고 그 아래에서는 단추가 아예 없다(`@RequiresApi` + `SDK_INT`, `NewApi` 0건). 선택·복사와 API 34 이하의 찾기는 그대로다 — 우리가 PDF 의 글자 배치 엔진을 다시 만드는 것은 12·13단계보다 큰 일이다 |
| **목차(outline)와 문서 안 링크** | 필요해지면 | 같은 이유로 레거시 API 가 주지 않는다. 쪽 이동은 슬라이더와 `쪽으로 가기` 로 한다 |
| ~~**두 쪽 보기(펼침)**~~ | **14단계에서 했다** | 짝은 `[1][2 3]…`(표지 혼자). 쪽마다 `destClip` 을 그 쪽 자리로 준다 — 행렬만 주면 재단 여백이 옆 쪽에 번진다(함정 표). 설정으로 남는다 |
| ~~**쪽 목록(썸네일 격자)**~~ | **14단계에서 했다** | 격자가 열려 있는 동안 쪽 층이 선명화 조각을 놓는다 — 캐시만 비우면 보이는 쪽이 쥔 조각 때문에 예산을 넘는다(검토가 잡았다) |
| **주석·폼을 그리는가** | 확인하지 않았다 | `RENDER_MODE_FOR_DISPLAY` 로 그리므로 플랫폼이 정하는 대로 나온다. 표본을 만들어 확인하기 전에는 '그린다' 도 '안 그린다' 도 적지 않는다 |
| **인쇄** | 넣지 않는다 | 1단계 요구사항에 없다 |
| **세로쓰기 책의 첫 줄이 화면 오른쪽 끝에 반쯤 걸린다**(E03·E04) | 필요해지면 | **원인을 확인하지 못했다.** 실세계 말뭉치 확인에서 보았다. 11단계부터 그랬다(고치기 전 빌드로 찍은 캡처에도 있다). 데스크톱 크롬은 뷰포트 메타가 없는 문서를 980px 로 배치해 WebView 와 조건이 달라 재현하지 못했다 |
| ~~**EPUB 의 장 안쪽 위치**~~ | **14단계에서 했다** | `locator` 에 `장:비율`(경로·글을 적지 않는다)을 적고, 배치가 끝난 뒤 코틀린에서 스크롤한다(스크립트는 꺼져 있다). **비율이라 글꼴 크기를 바꾸면 조금 어긋난다** — CFI 는 만들지 않았다. 흐름 문서(docx·HWP)도 같은 길이다 |
| ~~**글자 크기·여백·테마**~~ | **14단계에서 했다** | 글자는 `WebSettings.textZoom`(책의 CSS 와 싸우지 않고 다시 읽지도 않는다), 여백·바탕은 위생을 거친 CSS. 뷰어 안의 '보기' 와 설정 화면이 같은 값을 쓴다. 슬라이드에는 바탕을 걸지 않는다 |
| ~~**고정 레이아웃 EPUB**~~ | **14단계에서 했다** | 쪽의 뷰포트(없으면 OPF 의 `rendition:viewport`)로 화폭을 정하고 흐르지 않게 화면에 맞춘다. 한 번 알린다 |
| **미디어 오버레이(읽어 주기)** | 넣지 않는다 | 소리와 글을 맞춰 재생하는 기능이고, 그것은 이 앱의 재생기와 뷰어를 하나로 묶는 일이다 |
| ~~**글꼴 난독화 풀기**~~ | **했다**(2026-09-23, 방침이 바뀌었다) | 예전 판단은 'XOR 로 풀 수는 있지만 DRM 우회와 경계가 모호하다' 였다. 사용자가 '읽는 방법이 공개된 암호화는 연다' 로 정했다. `EpubBook.openResource` 가 풀린 바이트를 준다 — 화면은 난독화를 모른다. 오라클은 Readium 이 만든 난독화 글꼴이다(`FontObfuscationTest`, 표본은 `samples-local/`) |
| ~~**읽던 쪽 배지·읽음 표시**~~ | **14단계에서 했다** | 목록의 배지. EPUB·흐름 문서는 화면 **끝** 가장자리까지 본 몫(`progress`), PDF 는 쪽 — PDF 에 `progress` 를 적으면 한 쪽짜리가 열기만 해도 '다 읽음' 이 된다 |

### 10단계가 착수하며 정한 것

9단계 뒤 감사의 준비도 조사가 미결 다섯을 뽑았고, 10단계가 착수하면서 이렇게 정했다.

| 미결 | 정한 것 | 근거 |
|---|---|---|
| 재생 UI 의 자리 | **`feature:player` 를 만든다.** 화면만 옮기고 미니 바·서비스·커넥션은 `core:playback` 에 남긴다 | 10단계가 화면을 세 배로 키운다. `IroiroTheme` 을 쓰는 것이 `PlayerActivity` 하나라 옮기면 `core:playback` 의 `core:ui` 의존이 사라져 의존 표 예외가 셋에서 둘로 준다 |
| 자막 렌더 | **의존을 늘리지 않고 직접 그린다** | `media3-exoplayer` 안에 SRT·ASS·WebVTT·TTML 파서가 이미 있다(`extractor/text/` 아래 열 갈래). `Player.Listener.onCues` 로 받아 Compose 로 그린다. `media3-ui` 는 View 기반이라 Compose 화면에 맞지도 않는다 |
| 영상 탭 동작 | **영상은 곧바로 재생 화면으로**, 소리는 미니 바 그대로 | PiP·자막·제스처가 전부 재생 화면 안의 일이다. 영상을 탭해 놓고 목록에 남아 소리만 나는 것은 아무도 기대하지 않는다 |
| 재생 설정 저장 | **저장하지 않는다**(세션 안에서만). DB 마이그레이션 없음 | 7·9단계가 똑같이 14단계로 미뤘다. 외부 자막을 이름으로 자동 짝짓기 하면 다시 열 때 알아서 붙으므로 저장할 값이 작다 |
| '오디오를 가진 미디어' | **확장자 근사**(AUDIO·VIDEO 전부) | '목록을 그릴 때 파일을 열지 않는다'(`FileKind` 주석)가 더 단단한 규칙이다. 1만 개 폴더에서 ▶ 를 띄울지 정하려고 1만 번 열 수는 없다. **무음 영상이 섞이는 것이 이 근사가 틀리는 유일한 방향**이고, 틀려도 재생이 안 될 뿐 파일은 다치지 않는다 |

**착수 시점에 이미 서 있던 것** — PiP 는 매니페스트(`supportsPictureInPicture`·
`configChanges`)가 5단계에서 끝나 있고 코드만 없었다. 자막 인코딩 판정은 `core:charset` 을
의존 한 줄로 그대로 쓸 수 있었다(판정 창 64 KiB 라 자막 파일은 통째로 들어간다).

**착수 시점에 없던 것** — 플레이리스트·다음/이전·셔플·반복이 하나도 없었고(재생은 언제나
한 항목이었다) 자막·PiP·속도·트랙·A-B·제스처 코드가 0건, 재생·자막 표본이 0개였다.
`proguard-rules.pro` 에 media3 규칙이 없어 **자막 부품을 들이면 릴리스 스모크를 반드시
함께 돈다**고 적어 두었고, 실제로 매 회차 릴리스로 확인했다(PiP 아이콘까지).

## 암호가 걸린 파일 — 잠근 방식이 공개돼 있으면 연다

**사용자가 2026-09-23 에 정했다**: "암호화된 문서를 읽는 방법이 공개되지 않은 이상, 암호화된
문서도 열 수 있게 해주세요." 그 전까지의 방침('암호를 입력받아 들고 있는 것은 이 앱이 하지
않기로 한 일') 은 뒤집혔다. 판단 기준은 하나다 — **푸는 방법이 공개 명세에 있는가.** 암호를
몰라도 풀리는 방식(배포용 문서처럼 열쇠가 파일 안에 있는 것)도 공개돼 있으면 연다.

| 형식 | 잠근 방식 | 공개인가 | 지금 |
|---|---|---|---|
| PDF 표준 보안 처리기(R2~R6) | RC4·AES-128·AES-256 | ISO 32000-1/2 | **연다**(11단계 뒤) |
| PDF 공개 키 처리기(`Adobe.PubSec`) | 받는 사람의 인증서 | 명세는 공개지만 열쇠가 암호가 아니다 | 열지 않는다(묻지도 않는다) |
| PDF 전용 처리기(상업 DRM) | 업체마다 | 공개되지 않았다 | 열지 않는다 |
| EPUB 글꼴 난독화(IDPF·Adobe) | XOR | IDPF 는 EPUB 3.3 명세 4.4, Adobe 는 명세가 아니라 오픈소스 구현(Readium)이 따르는 방식 | **푼다**(`FontObfuscation`) |
| EPUB DRM(Adobe ADEPT) | 업체 방식 | 공개되지 않았다 | 열지 않는다 |
| EPUB Readium LCP | AES | 명세는 공개. 다만 실사용 프로필의 키 변환은 인증 아래 배포된다고 알려져 있다 — **확인하지 않았다** | 확인 전까지 열지 않는다 |
| ZIP 전통 암호 · WinZip AES(AE-1·AE-2) | ZipCrypto · AES-128/192/256 | APPNOTE 6.1 · WinZip AES 명세 | **연다**(`ZipDecryption`) |
| ZIP PKWARE 강한 암호화(SES) | 인증서·전용 | 명세 일부만 공개 | 열지 않는다('이 앱이 풀지 않는 방식') |
| 암호 안쪽이 LZMA·PPMd 등인 ZIP 항목 | 암호는 공개, 압축을 우리가 안 푼다 | | 열지 않는다. **암호를 묻지 않는다**(`ArchiveEntry.lockedForGood`) |
| 7z | AES-256(내용만·헤더까지) | 7-Zip 문서 | **연다**(commons-compress 에 암호를 넘긴다) |
| RAR | AES | RAR 기술 문서 | 암호를 넘긴다(junrar) — **미검증**, 표본을 만들 도구가 없다 |
| OOXML(암호 문서) — Agile · Standard | AES(SHA-1~512) · AES ECB | MS-OFFCRYPTO(Microsoft 공개 명세) | **연다**(12단계, `OfficeCfb`). `VelvetSweatshop`(엑셀 '읽기 전용 권장')은 묻지 않고 연다 |
| OOXML — RC4 CryptoAPI · Extensible | | 공개 명세지만 오피스 2007 뒤로 기본값이 아니다 | 열지 않는다('다루지 않는 문서') |
| OOXML — IRM(권한 관리) · 인증서만 | 서버·인증서의 열쇠 | 열쇠가 암호가 아니다 | 열지 않는다. **암호를 묻지 않는다** |
| HWPX 암호 문서 | SHA-256 → PBKDF2-HMAC-SHA1 → AES-CBC | 한컴이 공개한 OWPML 모델(Apache-2.0) | **연다**(13단계, `HwpxCrypto`). 한글 암호의 바이트 인코딩은 UTF-8 로 가정했다(실물은 ASCII 암호 하나로만 확인) |
| HWP 배포용 문서 | 파일 안의 열쇠 → AES-128-ECB | 한컴의 별도 공개 문서(pyhwp·hwplib·rhwp 가 같은 방식) | **암호 없이 연다**(13단계). 복사·인쇄 제한 비트는 뜻을 확인하지 못해 따르지 않는다 |
| HWP 암호 문서 | | **공개되지 않았다**(역공학 구현만 있다) | 열지 않는다. **암호를 묻지 않는다** — '암호로는 열 수 없는 방식' |
| 배포용 HWPX | | 표본이 없다 | 알아보면 읽히는 데까지 보이고, 하나도 못 읽으면 '암호로는 열 수 없다' |

**암호를 다루는 규칙.**

- **`CharArray` 로 받아 여는 일이 끝나면 0 으로 덮는다.** 주인은 VM 이다(`DocViewModel`·
  `ArchiveViewModel`·`ComicViewModel`) — 여는이(`PdfOpener`·`Archives.open`)는 쓰기만 하고 지우지
  않는다. 주인이 모르는 사이에 값이 사라지면 안 된다. **오래 들어야 하는 쪽은 자기 사본을 든다**
  (리더·`SolidComicSource`·풀기 요청) — 닫거나 끝날 때 그 사본을 지운다.
- **아카이브 암호만 세션 동안 기억한다**(`SessionPasswords`, 메모리에만). 압축 목록·만화 뷰어·풀기
  큐가 같은 파일을 따로 열기 때문이다. 넣은 암호는 **확인한 뒤에** 기억하고, 앱이 화면에서
  사라지면(시작된 액티비티가 0) 전부 지운다 — 회전은 예외다. 지우면 알리고(`clears`), 암호로
  풀어 둔 목록은 그때 다시 읽어 잠근다. **앱이 화면에 없는 동안에는 `put` 을 받지 않는다** — 확인이
  몇 초 걸리는 사이 홈으로 나가면 지우는 일은 이미 지나갔기 때문이다. 목록을 읽는 도중에 지워졌으면
  그 결과를 '풀렸다' 로 내지 않고 암호 없이 다시 읽는다. **이미 열린 것은 닫지 않는다** — 읽고 있는 만화와 PDF 는
  자기 사본(또는 평문 메모리 파일)으로 계속 읽힌다. PDF 암호는 기억하지 않는다(열려 있는 동안
  다시 물을 일이 없다).
- **저장하지 않는다** — `SavedStateHandle` 에도, DB 에도, 로그에도. 프로세스가 죽었다 살아나면
  다시 묻는다. 입력칸은 저장되지 않는 상태로 든다(함정 표의 `rememberTextFieldState`).
- **완전히 지우지는 못한다.** Compose 입력칸의 버퍼와, API 35 에서 플랫폼에 넘기는 `String`
  (`LoadParams.Builder.setPassword` 가 그것만 받는다), JCE 의 `SecretKeySpec`·`Mac` 이 안에 드는 열쇠
  사본(ZIP AES), junrar 가 열쇠를 유도할 때마다 만드는 `String` 은 우리가 덮을 수 없다. 우리 코드가
  만드는 사본(ZIP 암호의 바이트 후보·유도한 열쇠·PBKDF2 중간값)은 `String` 을 거치지 않고 만들어 덮는다.
  줄일 수 있는 데까지 줄인 것이다 — 그렇게 적어 두고 약속하지 않는다.
- **평문을 저장소에 쓰지 않는다.** 플랫폼이 암호를 받지 않는 API 34 이하에서는 우리가 풀어
  **memfd**(경로 없는 tmpfs 파일)에 평문 PDF 한 벌을 만들고 그 서술자를 pdfium 에 준다
  (`MemfdSink`). 문서 크기만큼 RAM 을 쓰므로 256 MiB 상한이 있다(`PdfLimits.MAX_DECRYPTED_BYTES`).
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
  **연다** — 아래 '암호가 걸린 파일' 절. 암호 OOXML 은 12단계부터, 암호 HWPX 와 배포용 HWP 는 13단계부터 연다.
  **HWP 5.0 의 암호 문서는 열지 않는다** — 한컴이 방식을 공개하지 않았다. 암호를 묻지도 않는다.
- **docx·HWP 의 페이지 재현(쪽 나눔·머리글·바닥글·단)을 포기한다.** 같은 폰트도 조판
  엔진도 없어 줄바꿈이 어차피 달라지고, 세로 화면에 A4 를 그대로 그리면 읽을 수 없다.
  흐름 렌더 + 첫 진입 고지 + 버린 항목 배지로 대신한다.
- **오피스 문서의 머리말·꼬리말·차트와 시트·슬라이드의 메모를 그리지 않는다**(12단계). 버린 것 배지가 센다. 쪽을
  재현하지 않으므로 머리말을 둘 자리가 없고, 차트를 그리려면 차트 엔진이 필요하다. docx 의 메모는 14단계부터 그린다.
- **시트의 수식을 다시 계산하지 않는다.** 저장된 값을 보인다. 값이 없는 수식은 '계산되지 않은 수식' 으로 센다.
- **시트 하나에서 2,000행·5만 칸까지만 그린다.** 그 너머에 보이는 값이 있으면 시트 이름과 함께 '줄였다' 를 알린다(빈 칸만
  남았으면 알리지 않는다) — 값을 정한 것은 WebView 의 표 배치 시간이다(12단계 실측).
- **한글 97 이전(HWP 3.0)을 열지 않는다.** 앞머리로 알아보고 '이전 형식' 으로 정확히 말한다(.doc·.xls·.ppt 와 같은 문장).
- **HWP 5.0 의 변경 추적을 알지 못한다.** 지운 글이 보일 수 있다 — 표본이 없고 레코드 모양이 문서화되지 않아
  알리지도 못한다. 한글 수식은 조판하지 않고 스크립트를 그대로 보인다(배지로 센다).
- **이전 오피스 형식(.doc·.xls·.ppt)을 열지 않는다.** '이전 형식입니다. 새 형식으로 저장하면 열립니다' 로 정확히
  말한다. 1단계에서 제외한 항목이다.
- **분할 아카이브를 열지 않는다**(`.z01`·`.part1.rar`·`7z.001`). 조각 하나만으로는
  통짜로 열 수 없고, 열려고 하면 라이브러리 예외로 끝나 사용자에게는 '깨진 파일' 로 보인다.
  지원하지 않는다는 사실을 정확히 말하는 편이 낫다. 압축 확장자를 가진 조각(마지막 `.zip` 등)은 그 문장 곁에 '다른 앱으로
  열기' 가 선다. `.z01`·`.001` 조각은 종류가 '기타' 라 압축 화면에 오지 않고, 누르면 곧바로 다른 앱으로 간다.
- **RAR 의 암호는 넘기기만 하고 확인하지 못했다.** junrar 에 암호를 건네는 코드는 있지만 암호
  RAR 표본을 만들 도구가 없어 한 번도 실제 표본을 타지 않았다. ZIP(전통·WinZip AES)과 7z 는
  **연다**(위 '암호가 걸린 파일'). PKWARE 의 강한 암호화(SES)는 '이 앱이 풀지 않는 방식으로 잠긴
  압축 파일입니다' 로 끝난다.
- **`iso` 를 열지 않는다.** 목록에서 종류는 '압축' 으로 보이지만(확장자로 정하므로) 열면 '이 앱이 다루지 않는 압축 형식입니다'
  와 '다른 앱으로 열기' 로 끝난다. `.gz`·`.bz2`·`.xz` 로 싼 파일 하나(안이 tar 가 아닌 것)도 같다. tar 계열은 14단계부터 연다 — 다만 **풀린 크기가
  1 GiB 를 넘는 압축 tar 는 목록을 보이지 않는다**(흐름이라 목록을 세우려면 끝까지 풀어야 한다. '풀어야 할 양이 너무 커서
  열지 않았습니다'). tar 의 심볼릭 링크·하드 링크·장치 파일은 풀지 않고 링크로 센다(RAR 링크와 같다).
- **지금은 압축 파일을 만들지 않는다.** 만들기는 원본을 파괴할 수 있는 경로를 하나 더 만든다.
  **RAR 만들기는 앞으로도 없다** — UnRAR 라이선스가 압축기 재구현 자체를 금지한다.
- **아카이브 안의 아카이브를 열지 않는다.** 목록에는 보이고, 풀어서 실제 파일이 된 뒤에
  열면 된다. 막는 것은 `maxContainerDepth` 라는 상수가 아니라 **구조**다(위 방어 절 참고) —
  엔트리에는 경로가 없어 뷰어에 넘길 수 없다. 탭하면 '압축 파일 안의 압축 파일은 열지
  않습니다. 풀어서 여세요' 로 끝난다. **만화 확장자(cbz·cbr·cb7·cbt)도 여기 든다** —
  9단계가 그 넷을 `ARCHIVE` 에서 `COMIC` 으로 옮기면서 이 안내가 한동안 '이 항목을 열 수
  없습니다' 로 잘못 나갔다(9단계 뒤 감사에서 고쳤다).
- **아카이브 안의 그림만 미리본다**(9단계). 그림 항목을 탭하면 만화 뷰어가 그 쪽에서 열리고,
  글·문서·영상 항목은 "이 항목을 열 수 없습니다" 로 끝난다 — 그 뷰어들은 경로를 요구하는데
  경로를 주려면 엔트리를 디스크에 뽑아야 하고, 그것이 만화 뷰어가 하지 않기로 한 일이다.
- **압축 파일 안의 문서를 열지 않는다**(11단계). PDF·EPUB·오피스·HWP 항목을 탭하면
  **'압축 파일 안의 문서는 열지 않습니다. 풀어서 여세요'** 로 끝난다(기기에서 확인했다).
  pdfium 이 **seekable 한 파일 서술자**를 요구해 기술적으로도 막히지만, 여는 유일한 길인
  '문서를 통째로 임시 파일에 뽑기' 는 쪽 한 장이 아니라 **문서 전체의 평문 사본**이라
  만화 뷰어가 하지 않기로 한 일보다 나쁘다. 12·13단계의 직접 구현 파서는 바이트 스트림을
  받으므로 기술적으로는 열 수 있게 되지만 **그때도 열지 않는다** — 한 번 뚫으면
  '엔트리를 디스크로 뽑지 않는다' 는 압축 화면의 불변식이 깨진다.
- **APNG 을 움직이게 틀지 않는다.** 플랫폼이 `isAnimated=false` 로 주므로 우리가 청크를
  분해해 합성해야 하는데, 그것은 CRC 를 다시 계산해 skia 에 먹이는 일이라 방어 상한과
  악성 표본이 먼저 필요하다. 만화 뷰어와 이미지 뷰어가 **"움직이는 PNG 입니다. 첫 장면만 보여
  줍니다"** 로 말한다.
- **너무 큰 움직이는 그림은 첫 장면만 보인다**(이미지 뷰어). 표본을 두 번 더 줄여도 프레임 버퍼 셋이 한 화면 크기
  비트맵에 들지 않으면 첫 장면과 그 까닭을 보인다.
- **만화 뷰어의 움직이는 쪽(GIF·애니WebP)은 확대되지 않는다.** 탭으로 막대를 여닫는 것은 된다. 이미지 뷰어는
  확대된다(14단계, 위 '9단계에서 미룬 것').
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
  `http:`·`file:`·`content:` 로 시작하면 지운다. `INTERNET` 미선언이 1차 방어이고 위생이
  2차다 — **방어는 겹쳐 두어야 한 겹이 무너져도 남는다.**
- **EPUB 안에서 글자를 찾지 못한다.** 장 하나 안에서는 WebView 의 기능이 있지만 책 전체를
  훑는 찾기는 없다. 7단계가 텍스트 뷰어에서 '파일 전체를 훑는다' 로 남겨 둔 것과 같은
  성질의 일이고, 여기서는 장마다 압축을 풀어야 해서 값이 더 크다.
- **DRM 이 걸린 EPUB 을 열지 않는다.** `encryption.xml` 이 본문을 가리키거나 무엇을
  가리키는지 알 수 없으면 '암호로는 열 수 없는 방식으로 잠긴 문서입니다' 로 끝낸다. 글꼴만
  난독화된 책은 **연다**.
- **`.mobi`·`.azw`·`.fb2` 를 열지 않는다.** 목록에서 종류는 '전자책' 으로 보이지만
  (확장자로 정하므로) 열면 '이 앱이 다루지 않는 문서입니다' 와 '다른 앱으로 열기' 로 끝난다. `.odt`·`.rtf` 도 같다. 이전 오피스
  형식은 '이전 형식' 문장과 함께 같은 단추가 선다.
- **PDF 의 글자를 선택·복사하지 못하고, API 34 이하에서는 문서 안에서 찾지도 못한다.** 레거시
  `PdfRenderer` 가 글자를 주지 않는다(위 '11단계에서 미룬 것'). 찾기는 API 35 에서만 있다. 목차와 문서 안
  링크도 없다 — 쪽 이동은 슬라이더·`쪽으로 가기`·쪽 목록이다.
- **인증서로 잠긴 PDF 와 전용 처리기로 잠긴 PDF 를 열지 않는다.** 앞의 것(공개 키 처리기)은
  암호가 아니라 받는 사람의 인증서를 요구하고, 뒤의 것은 방식이 공개되지 않았다. 둘 다
  '암호로는 열 수 없는 방식으로 잠긴 문서입니다' 로 끝난다 — **암호를 묻지 않는다**(물어도
  소용이 없다). 표준 보안 처리기로 잠긴 PDF 는 암호를 물어 연다.
- **UTF-32 텍스트를 열지 않는다.** BOM 은 알아보지만 읽지는 못한다 — UTF-16 인 척 열어
  널이 가득한 화면을 보여 주는 것이 가장 나쁘다. "이 앱이 읽지 못하는 인코딩입니다" 와 '다른 앱으로 열기' 로 끝낸다.
- **인코딩이 섞인 텍스트 파일을 다루지 않는다.** 판정은 파일 하나에 하나다. 앞 1 MiB 가
  전부 ASCII 이고 그 뒤에 CP949 한글이 나오는 파일은 UTF-8 로 판정되어 뒷부분만 깨진다 —
  그때 사용자가 아래 막대에서 인코딩을 손으로 바꾼다.
- **지금은 텍스트를 편집하지 않는다.** 요구사항 표가 '읽기 전용' 이다 — 영구 결정은 아니다
  (위 '이 프로젝트의 목적'). 넣을 때는 원본을 파괴할 수 있는 경로가 여섯 번째로 늘어나므로
  그 목록과 규칙(임시 파일 → fsync → 원자적 rename)에 먼저 더한다.
- **4 MiB 를 넘는 파일에는 문법 강조를 켜지 않는다.** 색을 반쯤 맞히는 대신 끄고 화면이
  그렇게 말한다. 위 실측표의 '강조 상태를 앵커마다 기록' 참고.
- **SVG·TIFF 는 디코딩하지 않는다.** 확장자가 IMAGE 로 잡히지만 플랫폼 디코더가 못 읽는다.
  격자에서는 한 번만 시도하고 실패를 기억해(부정 캐시) 종류 배지로 남긴다. 이미지 뷰어는 '이 이미지를 열 수 없습니다' 곁에
  '다른 앱으로 열기' 를 둔다.
- **APK·글꼴과 이 앱이 모르는 파일에는 뷰어가 없다.** 누르면 곧바로 다른 앱으로 연다 — APK 는 시스템 설치 관리자(설치를 묻는 것은
  설치 관리자다). 그 형식을 아는 앱이 없으면 정보 시트('이 형식을 아는 앱이 없습니다')가 뜨고 그 '다른 앱으로 열기' 가 모든 앱의
  고르는 창으로 간다. 확장자가 없거나 어느 표도 모르는 파일은 곧바로 모든 앱의 고르는 창이다(14단계 뒤 절).
- **회전을 파일에 저장하지 못하는 형식이 있다**(PNG·HEIC·AVIF·RAW·GIF·BMP·애니메이션 WebP).
  화면은 돌지만 "이 형식은 회전을 파일에 저장할 수 없습니다" 로 정확히 끝낸다. 위 실측표 참고.
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
  백그라운드 자리는 5단계가 알림과 미니 바로 이미 정해 두었다. 그래서 PiP 로 보는 중에
  큐가 소리로 넘어가면 **창을 닫고 재생만 잇는다.**
- **배속·트랙 선택·A-B 구간·방향 잠금을 저장하지 않는다**(세션 안에서만). 10단계가
  착수하며 정한 '재생 설정 저장 없음' 그대로다. 배속은 `stop()` 에서 1.0 으로 되돌린다 —
  재생 화면을 열지 않고 미니 바로만 듣는 길에는 배속이 적혀 있지 않아, 남겨 두면 무엇이
  이상한지 모른 채 소리가 틀어진 것만 듣게 된다.
- **ASS 의 서식을 조판하지 않는다.** 위/아래 자리와 좌우 정렬만 따르고 글자색·크기·
  회전·카라오케는 버린다. 전부 따르면 ASS 조판기를 다시 만드는 일이 된다.
- 채택하지 않는 것: Apache POI, PdfBox-Android, androidx.pdf(beta), juniversalchardet,
  ICU4J, media3 의 네이티브 decoder 확장, zstd-jni.
