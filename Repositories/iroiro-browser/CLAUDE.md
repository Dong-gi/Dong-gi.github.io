# iroiro-browser 작업 지침 (폰에 보이는 이름: **Web**)

**이 파일이 이 프로젝트의 유일한 지침 문서다.** 규격·절차·인계 문서를 따로 두지 않는다.

## 무엇인가

사용자의 **WebView 브라우저**. 웹 요청은 모두 사용자의 프록시(TLS 로 감싼 HTTP 포워드 프록시, Squid, Basic 인증 — 서버는
앱이 내어 주는 '서버 구성 파일' 로 세운다)로 보내고, 프록시에 닿지 못하면 **직접 연결로 새지 않는다**(fail-closed). 사용자가 둔 **예외 규칙**(사설망·`*.local`·
점 없는 이름 …)에 든 주소만 WebView 와 앱의 HTTP 가 같은 규칙으로 바로 연다. 출구 확인은 예외를 보지 않고 늘 프록시로 간다.

**프록시 밖으로 바로 나가는 것은 테더링 하나다**(사용자 결정): Wi-Fi Direct 핫스팟에 붙은 **다른 기기의** 트래픽 — 이 폰의 회선으로 바로, 따로 된
프로세스 `:tether`(아래 '테더링'). 브라우저 자신의 요청(WebView·내려받기·출구 확인·광고 목록)은 그것과 섞이지 않는다.

- **iroiro-viewer 와 따로 된 앱이다**(사용자 결정). `INTERNET` 은 앱(UID) 단위라 같은 앱이면 모든 파일 권한을 가진 뷰어도 네트워크에 닿는다.
- 이름 **Web**, 아이콘은 지구본(사용자 지정 — `drawable/ic_launcher_foreground.xml`, 크기는 viewer 아이콘과 같은 기준). 판은 **1.0.0**(versionCode 10). **Android 16(API 36) 부터**
  깔린다(2026-11-21 — 사용자의 메인 폰이 16 이 됐다. 옛 12 폰(SM-A908N)은 마지막 0.9.0 릴리스를 그대로 쓴다 — 아래 'Android 16 으로 올리며').
- 기능 — 브라우저(아래 '브라우저 — 무엇을 왜 그렇게 하나': 길·광고 차단·요소 숨기기·내려받기·찾은 미디어·재생 중인 것 받기·유튜브 광고 가리기와
  건너뛰기·뒤에서 재생 잇기 …), pixiv·hitomi 갤러리 받기('갤러리 받기' — 데스크톱 다운로더에서 옮겼다, 다운로더에는 YouTube 만 남았다), 테더링('테더링' —
  따로 된 앱이던 usb-tether 를 합쳤다), 설정 파일 zip('설정 파일 zip' — 프록시 서버 구성과 PC 연결 파일. 옛 PC 용 크롬 확장과 usb-tether 저장소를 대신한다).

## 걷은 것

사용자 요청으로 **토렌트**(받기가 느렸다)·**닫은 탭 되살리기**·**설정·북마크 백업/복원**·**검색 엔진 고르기**(Google 로 고정)를 걷었다. 코드와 서술은 git
이력에 있다(걷기 직전의 커밋 `ffe8e8b`). 남은 것 — 주소창의 `magnet:` 은 여전히 검색어로 돌리지 않고 다른 앱에 넘길지 묻는다(아래 '주소창과 제안'),
`.torrent` 는 보통 파일로 받는다, 알림 번호 3 이 빈다. 토렌트가 앱 안에 남긴 파일(`noBackupFilesDir/torrent`·`torrent-app`)과 옛 설정 키를 지우던 정리 코드도
걷었다 — 남은 것은 지우지 않는다(읽는 코드도 없다). 사용자가 내보낸 백업 JSON 은 이제 읽지 않는다. 권한은 그대로다 — `WAKE_LOCK` 은 뒤에서 재생 잇기가,
`FOREGROUND_SERVICE_DATA_SYNC`·`ACCESS_NETWORK_STATE`·`POST_NOTIFICATIONS` 는 내려받기·녹화·광고 목록이 쓴다. 11-14 정리에서 부르는 곳이 없던
작은 것(`Remux.sniff`·`CaptureSession.totals`·`AdBlocker.skippedCount`·`RequestType.ALL`·`Item.canResume`, 실험실의 `describe`)을 지우고, 여러 곳에
복사돼 있던 것을 한 곳으로 모았다 — 주소창 고치기 끝내기(`BrowserActivity.endAddressEditing`), 받기의 멈춤 깃발(`DownloadCenter.Item.stopRequested`),
'링크 복사'(`Ui.copyLink`). 이어서 기기 키로 잠그기(`crypto/DeviceKeyCipher` — SecretBox·TetherSecret 은 별칭과 실패를 잡는 약속만 다르다),
상한까지 읽기(`Streams.readAll`·`readHead`), 내려받기와 찾은 미디어 미리 읽기의 요청 머리(`download/HopHeaders`)를 모았다.

## Android 16 으로 올리며 (1.0.0 — 2026-11-21)

사용자의 메인 폰이 Android 16 이 되어 **minSdk 31 → 36**, 판 **0.9.0(9) → 1.0.0(10)**. targetSdk 36·compileSdk 37 은 그대로다. 옛 12 폰은 마지막
0.9.0 릴리스(`Downloads/Web-0.9.0-release.apk` — 지우지 않는다)를 그대로 쓴다. 이 판은 12~15 에 깔리지 않는다.

| 버전 때문에 갈랐던 것 | 1.0.0 |
|---|---|
| 뒤로 — 13 이상 `OnBackInvokedCallback`, 12 `onBackPressed`(+ lint `GestureBackNavigation` 막기) | 콜백 하나(브라우저·설정 화면). 테더 화면은 콜백을 걷었다 — 하는 일이 시스템의 기본(끝내기)과 같아 걸지 않아야 16 의 뒤로 미리 보기 애니메이션이 보인다. 매니페스트의 `tools:targetApi` 도 걷었다 |
| 알림 권한 — 13 이상만 묻기 | 늘 묻는다(받기·녹화·테더를 켤 때 — 그 자리 그대로) |
| 테더의 연결 변경 수신기 — 13 이상 `RECEIVER_NOT_EXPORTED` | 그 하나 |
| 테더의 포그라운드 — 14 이상만 `startForeground(…, SPECIAL_USE)` | 그 하나 |
| 방송의 덧붙임 — 14 이상 형식 있는 `getParcelableExtra` | 그 하나(도우미 함수를 걷었다) |
| 타일의 트램폴린 — 14 이상 `startActivityAndCollapse(PendingIntent)` | 그 하나 |
| 테더 권한 — 12 위치(FINE·COARSE, maxSdkVersion 32)·'대략적인 위치' 안내, 13 이상 근처 기기 | **근처 기기 하나**. 위치 권한·'위치 서비스 꺼짐' 검사(`HotspotProblem.LOCATION_OFF`)·'위치 설정 열기' 단추와 그 글을 걷었다 |
| '링크 복사' 의 토스트 — 12 만(13 부터 시스템이 알린다) | 걷었다(글 `copied` 도) |
| 막대 아래까지 그리기 — `setDecorFitsSystemWindows(false)`·투명한 `statusBarColor`·`navigationBarColor`(12~14 를 15 와 같게) | 걷었다 — targetSdk 35 부터 시스템이 늘 그렇게 그리고 그 셋은 듣지 않는다(폐기) |
| `Remux` 의 MP4 — 14 이상만 AV1 | AV1 을 늘 넣는다(`MP4_MIMES`). **가로채기의 AV1 거절은 그대로** — 16 의 `MediaMuxer` 도 MP4 에 Opus 를, WebM 에 AV1 을 담지 못한다(아래 '재생 중인 것 받기' 의 '유튜브') |
| 순수 JVM 모듈이 피하던 API 33 의 것 | `URLEncoder.encode`·`URLDecoder.decode` 의 `Charset` 판, `InputStream.readNBytes`(`Streams.readHead`·`SharedOps.readUpTo`), `removeLast`(API 35 — `UriRefs`). `Streams.readAll` 은 상한을 넘는 순간 끊는 꼴이라 그대로 |
| `tools/check_api_level.py` — `MIN_SDK = 31` | `app/build.gradle.kts` 의 minSdk 를 읽는다(36). 36 까지 SDK 에서 빠진 것(`removed`)도 문제로 친다 |
| 실험실 실행기 — `Android_12_Phone` 만 | `Android_16_Phone` 만(`run_lab.py` 의 `AVD`) |

**13~16 의 동작 변경 가운데 이 앱에 닿는 것**(developer.android.com 의 behavior-changes 13·14·15·16·로컬 망 권한·Wi-Fi 권한·FGS 시한
문서로 확인):
- **고쳤다** — 위 표(뒤로·알림·수신기 깃발·PendingIntent 타일·위치 권한·복사 토스트·막대 그리기).
- **이미 맞다(targetSdk 36 이라 0.9.0 도 그랬다)** — 포그라운드 서비스의 종류(14)와 `dataSync` 의 하루 6시간(15 — `DownloadService`·`CaptureService` 의
  `onTimeout`), mediaPlayback·specialUse 는 시한 없음, 내보낸 것과 명시적 인텐트(14 'safer intents' — 내부 서비스·화면은 모두 명시적이고 내보내지 않는다),
  예측 뒤로 가기(16 — targetSdk 36 이면 `onBackPressed`·KEYCODE_BACK 이 오지 않는다. 이 앱은 처음부터 콜백이다), 엣지 투 엣지 강제(15)와 16 의
  `windowOptOutEdgeToEdgeEnforcement` 무효(이 앱은 쓰지 않았다).
- **보고만** — ① **로컬 망 보호**: targetSdk 36 에서는 강제되지 않는다(문서 — 17(API 37) 부터, `ACCESS_LOCAL_NETWORK`). targetSdk 를 37 로 올리면 테더
  서버가 **들어오는 연결을 받는 것**과 앱의 HTTP·WebView 가 사설망에 닿는 것(예외 규칙의 사설망)에 그 권한이 든다 — 올릴 때 함께 정한다. ② **노치 모드**:
  15 부터(targetSdk 35+) 창이 아닌 화면의 `SHORT_EDGES`·`DEFAULT` 는 `ALWAYS` 로 읽힌다 — 전체 화면 영상 동안 노치 모드를 기본값으로 돌리는 것
  (아래 '전체 화면' 표)은 듣지 않는다(영상이 노치 자리까지 그려질 수 있다 — 이 AVD 에는 노치가 없어 보지 못했다, 0.9.0 도 15 이상 폰에서는 같았다).
  ③ **Wi-Fi 잠금**: 14 부터 `WIFI_MODE_FULL_HIGH_PERF` 는 `LOW_LATENCY` 로 바뀌고 앞에 있고 화면이 켜진 동안만 듣는다 — 뒤에서 재생 잇기의 Wi-Fi 잠금은
  사실상 듣지 않는다(해가 없어 뒀다). ④ **16 의 일감 몫**: 포그라운드 서비스와 함께 도는 일감도 실행 몫을 따른다 — 광고 목록 갱신(`AdBlockUpdateJob`,
  하루 한 번·짧다)에는 문제가 없을 것으로 본다(추론). ⑤ **16 의 인텐트 넘겨주기 보호**(모든 앱): 이 앱은 받은 인텐트의 덧붙임에 든 인텐트를 띄우지
  않는다(`intent:` 고리는 `parseUri` 로 지어 컴포넌트·셀렉터를 지운다) — 닿지 않는다고 본다. ⑥ WebView·미디어 세션·타일에 16 의 변경은 문서에 없다.

**에뮬레이터 `Android_16_Phone`**(2026-11-21 만듦 — `system-images;android-36;google_apis;x86_64` r7, `avdmanager` 가 없어(SDK 에 cmdline-tools 가
없다) 이미지를 저장소 목록(`sys-img2-4.xml`)의 주소에서 받아 SHA-1 을 견주고 풀어 `package.xml` 을 짓고, AVD 파일(`config.ini` — 픽셀 8 꼴 1080×2400·420
dpi·RAM 4 GB, 노치 없음)을 손으로 지었다. 그 이미지의 WebView 는 **133** 이다 — 사용자 폰(늘 최신)보다 낡았다. Play 판이 아니라 올릴 수 없다).
띄우기: `env -u HTTP_PROXY … emulator.exe -avd Android_16_Phone -gpu host -feature -Vulkan`. 본 것(디버그·릴리스 판, 프록시 끔·광고 차단 끔) — 쪽 열기
(example.com·고리 따라가기), 전체 화면의 위 가장자리 밀기, 뒤로(쪽 뒤로 → 할 일이 없으면 홈으로 — 화면은 남는다), 내려받기(묻기 → 알림 권한 묻기 →
'내려받기' 에 107 B), 설정·테더 화면의 뒤로, 테더링(근처 기기 권한 하나 → **위치 서비스를 끈 채** 그룹 `DIRECT-UT-USBTether`·`p2p-wlan0-0` 이 서고
서버 셋이 섬, 연결 변경 방송이 위치를 끈 채 닿음(`dumpsys activity broadcasts`), Wi-Fi 를 끄면 '핫스팟이 내려갔습니다' 로 통째로 꺼짐, `adb forward` 로
들어온 연결은 '핫스팟 밖에서 온 연결' 로 거절), 퀵 설정 타일(`cmd statusbar click-tile` — PendingIntent 트램폴린으로 켜고 끄기), 공유 폴더(SAF 고르기 →
디버그 탐침으로 저장 공간·만들기·이름 바꾸기·복사·옮기기·자기 안으로 거절·휴지통·`.nomedia`·되살리기·비우기). 시험 파일과 폴더는 지웠다.

## 사용자 결정

| 무엇 | 결정 |
|---|---|
| 앱 | 뷰어와 **따로 된 앱**, `Repositories/iroiro-browser`, 이름 Web, 웹 브라우저 느낌의 아이콘 |
| 의존·권한 | 의존은 `androidx.webkit` 1.17.1·`androidx.media3:media3-extractor` 1.11.1 둘(**승인**). 폰의 Android System WebView 는 늘 최신. 권한 — `INTERNET`·알림·포그라운드 서비스(`dataSync`·`mediaPlayback`·테더의 `specialUse`)·`ACCESS_NETWORK_STATE`(보통 권한 — JobScheduler)·`WAKE_LOCK`(보통 권한 — 뒤에서 재생 잇기), 테더링의 Wi-Fi 상태 읽기·바꾸기·근처 기기(`neverForLocation`). **위치 권한은 없다**(1.0.0 에서 걷었다) — 아래 '테더링' 의 '권한' |
| 길 | **중계 하나로** — WebView·내려받기·출구 확인이 모두 앱 안 중계(`LocalRelay`)를 거친다(예외 규칙에 든 주소만 바로). 원격 계정은 중계만 안다 |
| DNS 미리 찾기 누출 | **감수한다**(앱 전용 VPN 으로만 막을 수 있다 — 하지 않는다). 연결 상태 대화가 그 사실과 '개인 DNS' 를 알린다 |
| 세이프 브라우징 | **쓰지 않는다**(Play 서비스가 프록시 밖에서 묻는다) — 설정과 매니페스트 meta-data 둘 다 끈다 |
| 릴리스의 평문 http | **허용** — `res/xml/nsc_release.xml`. 프록시까지는 TLS 다 |
| 켜기·끄기 | **막대 왼쪽의 스위치 하나.** **묻지 않는다.** 메뉴·상태 창·알림 띠·설정 화면에는 켜고 끄는 단추를 두지 않는다. 주소·계정은 그대로 둔다. 기본값은 켜짐(정하기 전에는 아무 데도 나가지 않는다). 스위치는 전역이다 — 끄면 받던 보통 파일·HLS 도 직접 연결로 잇는다 |
| 익명 탭 | **만들지 않는다** — 설정의 '사이트별 데이터' 로 갈음한다 |
| 재생 중인 것 받기 | **MSE 가로채기와 실시간 녹화 둘 다, 사용자가 고른다** — 쪽의 세계를 건드리는 것은 고른 탭·고른 동안만 |
| `blob:` 내려받기 | **지원** — 쪽에서 보이지 않는 실행 세계로. 넘겨받지 못하면 사용자가 고를 때만 그 탭에 갈고리 |
| 따로 된 소리 합치기 | **media3 로** — `media3-extractor`(읽기) + 플랫폼 `MediaMuxer`(쓰기) |
| AES-128 HLS | **풀어 받는다** |
| DRM 재생 | **지원** — 보호된 미디어 권한만 준다. DRM 영상은 받지 않는다 |
| 다른 앱의 링크 받기 | **보류** — VIEW 인텐트를 받지 않는다('사용자가 정할 것') |
| UA·클라이언트 힌트 | **일반화** — 크롬의 줄인 꼴, 힌트에서 WebView 상표·기기 모델을 뺀다 |
| 내려받기 위치 | **공용 '내려받기' 폴더** |
| 광고 목록 | **EasyList·EasyPrivacy·YousList·uBlock filters 넷 다 기본으로 켠다**(꾸준히 갱신되는 목록) |
| 이어받기 | **자동으로 세 번 다시 해 보고, 그래도 안 되면 멈춰 사용자가 이어받기** |
| 검색 | **Google 하나**(고르는 설정 없음). 검색어 제안은 하지 않는다 |
| 전체 화면 | **설정 '전체 화면', 기본 켬** — 상태 표시줄과 막대를 숨기고 **화면 위 20% 의 띠**에서 아래로 밀 때만 부른다. 내비게이션 바(뒤로·홈)는 남긴다, 프록시 문제의 알림 띠는 늘 보인다, 노치 자리까지 그린다 |
| 요소 숨기기 | **구현** — 쪽에서 거슬리는 부분을 골라 그 사이트에서 숨긴다. 규칙은 `사이트##선택자`, 범위는 등록 도메인, 광고 목록과 따로 두고(`files/hidden-elements.txt`) **광고 차단을 끄거나 그 사이트에서 끈 때도 건다** |
| 재생 중 화면 켜 두기 | 지금 탭에서 **소리가 나는** 미디어(영상·소리 모두)가 재생되는 동안과 **전체 화면 영상**이 재생되는 동안(음소거여도). 페이지 안의 음소거 영상은 켜 두지 않는다. 설정은 두지 않는다(전원 단추로 언제든 끈다) |
| 항상 확대 허용 | **설정 '화면 › 항상 확대 허용', 기본 켬** — 크롬의 '확대/축소 강제 사용' 처럼 **뷰포트의 잠금(`user-scalable`·5배 밑의 `maximum-scale`)만** 푼다(`touch-action`·쪽이 스스로 받는 두 손가락 몸짓은 그대로). 깨지는 사이트가 있으면 끌 길로 둔다 |
| 주소창·탭의 작은 것 | **'복사한 링크 열기'**(주소창을 빈 글로 열면 맨 위) · 글 고르기 메뉴의 **'검색'**('복사' 바로 뒤 — Google 로 새 탭에서) · 고리·그림의 **'새 탭에서 열기' 는 곧바로 그 탭으로**(그 탭에서 뒤로 갈 곳이 없으면 연 탭으로) · **살아 있는 탭 수**는 설정 › 화면(1·2·3·5(기본)·8·10·15·20) |
| 보관 기간 | 설정 › 인터넷 사용 기록에 '방문 기록 보관'·'캐시 비우기'(기본은 '기간 없음'·'하지 않음'). **쿠키는 기간으로 지우지 않는다**(로그인이 풀린다 — '사용자가 정할 것') |
| 정체 감추기 | WebView Media Integrity API 를 끄고 파비콘을 받지 않는다. `Sec-GPC: 1` 은 넣지 않았다('사용자가 정할 것') |
| 앱의 HTTP 와 예외 규칙 | **따른다** — 내려받기·HLS·DASH·찾은 미디어 미리 읽기·광고 목록이 WebView 와 같은 예외 규칙으로 바로 간다(크로미움처럼 읽고, 다르게 읽힐 수 있는 꼴은 프록시로). 출구 확인은 늘 중계 |
| 테더링 | usb-tether 를 **합친다**(목적은 '앱 하나 줄기'). **인증은 더하지 않는다**(Wi-Fi Direct 로 붙은 기기만 닿는다) — 대신 그 전제를 코드로 단단히 한다(손님 거르기·파일 서버의 `Host` 검사·스레드 예산). 손님의 트래픽은 프록시를 거치지 않는다 — '바로 나감' 예외. 포트·SSID 앞머리·192.168.49.x 는 그대로(PC 의 스크립트·Wi-Fi 프로필이 쓴다). **켜기·끄기 하나**(서버 셋과 핫스팟 — 핫스팟이 서지 못하거나 내려가면 통째로 끄고 까닭을 남긴다, 서버 하나가 포트를 못 얻은 것은 끄지 않는다) · **HTTP·파일 포트는 차 있으면 +1 씩**(이 폰의 다른 앱이 8282·8080 을 먼저 쥐면 PC·손님이 그 앱에 붙는 위험을 감수했다. SOCKS5 는 1080 고정) · **타일을 길게 누르면 테더 화면**(앱 바로가기는 두지 않는다) · **재부팅 뒤 저절로 켜지 않는다** |
| 테더 파일 서버(NAS 꼴) | **시놀로지·QNAP File Station·Nextcloud 꼴**(11-21 승인) — 바깥 JS·CSS·CDN·새 Gradle 의존 없음, 화면은 한국어·설명은 짧게. 읽기 쪽(나무·목록/격자·정렬·거르기·보이는 줄만·섬네일·보기·여러 개 고르기·zip 흘려 받기·올리기 대기열·저장 공간·단축키)은 늘, **관리 기능**(폴더 만들기·이름 바꾸기·옮기기·복사·지우기 = 숨은 휴지통으로·휴지통·폴더째 올리기)은 테더 화면의 **'관리 허용'(기본 끔)** 이 켜졌을 때만(서버가 요청마다 본다). 바꾸는 요청은 PUT·JSON POST 뿐 — 아래 '테더링' 의 '서버의 세부'. 11-21 더 정한 것: **올리기의 덮어쓰기는 관리 허용과 상관없이 그대로 두되 같은 이름이면 한 번 묻는다**(서버는 `overwrite=1` 이 없으면 409) · **관리 허용은 테더링 켜기·끄기와 별개**(끌 때 저절로 끄지 않는다) · **휴지통 보관 기간을 고른다 — 7·30(기본)·90일·비우지 않음**(테더 화면, 지난 것은 `:tether` 가 저절로 비운다) · **휴지통에 `.nomedia`**. 11-21 그 뒤 더 정한 것: **'모든 파일에 적용'**(윈도의 덮어쓰기 창처럼 — 겹치는 파일이 여럿이면 첫 물음에서 한 번, 겹치는 수를 알면 '(겹치는 파일 N개)', 그 묶음 안에서만) · **폴더와 같은 이름이면 '덮어쓰기' 를 고를 수 없다**(흐리게 + '같은 이름의 폴더가 있습니다', 서버는 `overwrite=1` 이어도 409 `folder`, '모든 파일에 적용' 으로 덮어쓰기를 골랐으면 그런 것은 새 이름으로) · **새 이름의 제어·방향·폭 없는 글자는 거절하지 않고 뺀다**(빼는 것은 11-21 의 집합 그대로 — 소프트 하이픈·태그 글자·이체 선택자는 둔다, 휴지통 판정만 Default_Ignorable 전부를 떼고 본다: 11-27 재검토, 아래 '서버의 세부' · 올리기·폴더 만들기·이름 바꾸기·겹쳐 짓는 새 이름·폴더째 올리기의 새 성분, 같은 이름 검사도 뺀 뒤로, 답에 실제 이름) · **zip·받기의 쓰기 시한은 두지 않는다** · **덮어쓴 원래 파일은 휴지통으로**(관리 허용과 상관없이 — 옮기지 못하면 덮지 않는다. 휴지통 보기·되살리기는 관리 허용이 필요하다) · **덮어쓸 때 원래 파일이 0바이트면 휴지통에 넣지 않는다**(11-21 — 그냥 새 것으로 바꾼다. 크기는 서버가 실제 항목에서, 판정 뒤 원래 것이 커지면 휴지통으로, 덮어쓰기만 — 관리의 지우기는 그대로) · 관리가 꺼진 손님의 반복 덮어쓰기로 **휴지통이 커지는 것은 지금은 다루지 않는다**(11-21) |
| 설정 파일 zip | 설정 › 프록시의 **'서버 구성 파일 받기'**(`web-proxy-server.zip`), 테더링 › 다른 기기에서의 **'PC 연결 파일 받기'**(`web-tether-pc.zip`) — 둘 다 공용 '내려받기'. 남의 실행 파일(tun2proxy·wintun)은 담지 않고 README 가 받는 곳을 적는다 |
| 갤러리 받기 | **pixiv·hitomi**(사이트는 사용자가 골랐고 나머지는 검토의 권고대로) — pixiv 는 **이 브라우저의 로그인 쿠키 + 웹 AJAX**(데스크톱의 OAuth·앱 API·비밀 보관을 옮기지 않았다, Claude 는 로그인하지 않는다), 모든 요청은 내려받기의 길(세 번째 '바로 나감' 없음), 자리 `Download/Pixiv/<작가>/`·`Download/Hitomi/<작가>/<번호_제목>/`, 우고이라는 zip 그대로, 들어가는 길은 ⋮ 메뉴와 고리 길게 누르기('주소로 받기' 없음), wake lock 없음, 시험 자료는 합성만 |
| 유튜브 광고 | **B1(가리고 소리 끄기)과 B2(건너뛸 수 있게 되면 앱이 진짜 터치로 누르기 — 앞·뒤·화면 꺼짐·다른 탭 무관).** B3(끝으로 감기)는 넣지 않는다 — 건너뛸 수 없는 광고는 가리고 끈 채 끝까지 간다. 켜는 조건은 광고 차단(그 사이트에서 끄면 꺼진다 — 탈출구), 새 설정 스위치는 없다 |
| 뒤에서 재생 잇기 | **유튜브(https)에서 소리가 나는 재생만**(Claude 가 정한 꼴 — 음소거로 저절로 도는 것은 아니다), 잇는 탭은 하나, 앱이 뒤에 있거나 그 탭을 보지 않는 동안만 알림(제목·재생/일시 정지·닫기 — 잠금 화면·이어폰 단추도), 멈춘 뒤 10분은 다시 틀 수 있게 남긴다, 새 설정 스위치는 없다(알림의 '닫기') |
| 유튜브의 보임 속이기 | **넣는다** — 유튜브의 주 틀에 **쪽의 세계** 문서 시작 스크립트(`BackgroundPlay.VISIBILITY_SCRIPT`). 이 앱이 쪽의 세계에 상시 두는 둘째 스크립트다(첫째는 WebRTC 가드). `_lact` 갱신('계속 시청하시겠습니까?' 미루기)은 넣지 않았다 |
| 뒤에서 재생 — 그리기 줄이기 | 화면 밖 그리기는 **WebView 가 다시 그리기를 청할 때만**, 뒤에서 재생하는 동안은 **그 탭의 영상 요소를 숨긴다** |
| m3u8 열쇠 | 열쇠를 **쪽의 재생기가 실제로 보낸 머리로** 청하고(메모리에만 — 쥘 머리와 상한은 Claude 가 정했다), 재생목록·MPD 를 **넘겨주기 뒤의 주소로** 풀고, **너그럽게 읽되 첫 조각으로 확인**하고, 실패 글에 까닭의 갈래와 '가로채기' 안내. 다음 길(D·F)은 실패 글이 가리킬 때('사용자가 정할 것') |
| 보안 점검 고침 | 광고 목록의 정규식을 되짚지 않는 엔진으로(`ListRegex`)·무늬·낱말·호스트의 제곱 시간, 내려받기의 Referer 를 홉마다, 중계의 홀로 선 CR·LF 를 400, 테더 HTTP 프록시의 겹친 CL·TE 를 400, WebRTC 가드를 쪽이 바꿀 수 있는 내장 함수에서 떼기(사용자: '수정할 가치가 있는 것들만'). 고치지 않은 것과 2차 점검(11-20)은 '보안 점검 — 알고 둔 것' |
| PC 도우미의 설정 바꾸기 | **서비스 확인 창으로**(11-27 — 관리자 .NET 프로세스를 두지 않으려고). 설정 창은 트레이(보통 권한)의 것이고, 저장하면 서비스에 제안하고 서비스가 콘솔 사용자에게 바뀌는 칸을 보이며 묻는다 — **권한 모델이 바뀌었다: 콘솔에 로그온한 보통 사용자가 '예' 하나로 SYSTEM 서비스의 설정(폰 프록시 주소·포트·우회 목록·tun2proxy 인자 등)을 바꿀 수 있다**(예전에는 UAC 관리자 승인). 서비스는 **설치본의 트레이(`Program Files\WebTether\WebTether.exe`)가 보낸 제안만** 받고(손님의 이미지와 설치본이 같은 파일인가 — 볼륨 일련번호 + 파일 ID, 검토 11-27), 확인 창에 요청한 프로그램의 경로·PID 를 보인다. 완전하지 않다 — 같은 사용자의 프로그램은 돌고 있는 트레이에 코드를 넣어(같은 무결성·같은 사용자라 막는 것이 없다) 그 이름으로 제안을 보낼 수 있다. 그때 막는 것은 사용자가 보는 확인 창뿐이다. 아래 'PC 도우미' 의 '설정' |

## 지켜야 할 것

| 규칙 | 내용 |
|---|---|
| 서드파티 | 보안에 취약한 서드파티 라이브러리를 쓰지 않는다(사용자: '개인용이라고 보안에 취약한 3자 라이브러리를 이용해선 안 됩니다'). 의존은 **`androidx.webkit` 1.17.1 과 `androidx.media3:media3-extractor` 1.11.1 둘**(둘 다 사용자 승인 — 끌려오는 것은 아래 '재생 중인 것 받기' 의 '의존'). **화면은 플랫폼 뷰로 짓는다** — Compose 등은 새 의존이라 승인이 필요하다. 새 의존·내려받기는 승인을 받는다 |
| 공개 저장소 | 부모 저장소는 GitHub Pages 로 **통째 공개**된다. 개인 문서·keystore·서드파티 바이너리·**프록시의 주소와 계정**을 커밋하지 않는다. 실험실의 인증서·시험 계정·결과는 `local/`(git 무시), 디버그 빌드가 믿는 시험 CA 와 NSC(`app/src/debug/res/raw/lab_ca.pem`·`xml/nsc_lab.xml`)도 git 무시다 |
| 커밋 | 사용자가 청할 때만 |
| 기기 | **실기기에 깔지 않는다**(사용자의 Android 16 폰도, 옛 SM-A908N 도). 에뮬레이터는 **`Android_16_Phone`(API 36, google_apis x86_64 — 픽셀 8 꼴)만**. `Android_12_Phone`(API 31)은 쓰지 않는다 — minSdk 36 이라 깔리지 않고, 그 안의 옛 앱에 사용자의 실제 프록시가 있다. 새 AVD 의 앱에는 실제 프록시 주소·계정을 넣지 않는다(프록시는 꺼 둔다). 태블릿 시험은 하지 않는다(사용자가 양도) |
| 계정 | 실제 프록시 비밀번호는 **사용자가 넣는다**(Claude 가 넣지 않는다). 로그인하지 않는다. 시험 계정은 `make_certs.py` 가 만든 무작위 값이고 대화에 적지 않는다 |
| 다른 설정 | 다른 앱·시스템의 보안 설정을 바꾸지 않는다 — 에뮬레이터에 인증서를 깔지 않고, 디버그 빌드의 네트워크 보안 설정이 시험 CA 를 `lab.test` 아래 이름에만 믿는다 |
| 테더의 프로세스 | 테더의 구성 요소 다섯(화면·트램폴린·서비스·타일·타일을 길게 누를 때의 작은 화면)은 **모두 `android:process=":tether"`** 이고, 그 코드(`tether/`·`app/.../tether/`)는 **브라우저의 것(`WebApp.of` — 설정·길·내려받기, WebView)을 부르지 않는다** — 그 프로세스에서는 `WebApp.of` 가 던지고 WebView 가 꺼져 있다. 내보내는 것은 타일과 그 작은 화면(`TilePreferencesActivity` — 테더 화면을 열기만 하고 받은 인텐트를 읽지 않는다)뿐이다. 앱의 시험 `TetherIsolationTest` 가 매니페스트와 소스를 본다(테더 소스가 가져올 수 있는 앱의 것은 R·BuildConfig·`ui.Ui`·`crypto.DeviceKeyCipher` 와 설정 파일 zip 의 `setup.*` 뿐 — 테더 화면이 'PC 연결 파일 받기' 로 `setup/` 을 부른다. 그 도우미들과 `setup/` 도 같은 규칙을 지키는지 시험이 따로 본다). 아래 '테더링' |
| 테더의 문 | 인증 없는 LAN 서버다(사용자 결정) — **손님 거르기(`PeerFilter` — 그룹 오너 주소·손님 대역·떠 있는 우리 그룹의 인터페이스)·파일 서버의 `Host` 검사·목적지 거르기(`DestinationFilter`)·스레드 예산을 약하게 하지 않는다.** 바깥 소켓은 `Outbound`(`Socket(Proxy.NO_PROXY)`)로만. 목적지·손님의 주소를 기록에 남기지 않는다 |
| PC 쪽 실행 파일 | PC 연결 파일은 앱의 자산(`app/src/main/assets/setup/tether-pc/` — 시작 스크립트 둘과 README.txt)이고 테더 화면이 zip 으로 내어 준다. tun2proxy·wintun·adb 는 zip 에도 저장소에도 넣지 않고 **이름으로** git 무시다(`.gitignore` — 공개 저장소에 남의 실행 파일을 올리지 않는다). `windows-wifi.bat` 은 ASCII·CRLF(cmd.exe 는 배치 파일을 콘솔의 코드 페이지로 읽는다), 나머지 자산 글은 LF(`SetupAssetsTest`). 서버 구성 파일(`assets/setup/proxy-server/`)에는 프록시의 실제 주소·계정을 적지 않는다 — 예시는 `proxy.example.com`·`myproxyuser` |
| 사이트 자료 | 갤러리 받기의 시험 자료는 **합성만** — 실제 작품·작가·제목·갤러리 번호를 저장소에 넣지 않는다(공개 저장소). 사이트의 실제 답은 스크래치패드에서 읽기만 하고, hitomi 는 그림을 받지 않는다(`Range: bytes=0-0` 까지 — 에뮬레이터에서도 계획·확인 창까지만, 그 쪽을 열지 않는다) |
| 보고 | 한국어 |

## 툴체인과 확인 명령 — iroiro-viewer 와 같다

Gradle 9.6.0(viewer 의 래퍼를 복사), AGP 9.4.1, **내장 Kotlin** 2.3.20(`android.builtInKotlin=true` — 외부 `org.jetbrains.kotlin.android` 를
적용하면 AGP 9.4 의 새 DSL 과 충돌해 설정이 죽는다), JBR 21, compileSdk 37, **targetSdk 36 을 반드시 적는다**(없으면 AGP 9 가 조용히 37 로
채운다), **minSdk 36**(1.0.0 부터 — 앞은 31), Java 17(순수 JVM 모듈은 `jvmToolchain` 을 쓰지 않는다 — JDK 17 을 따로 내려받게 된다. 바이트코드만 17). **configuration
cache 를 껐다** — 디버그 빌드가 시험 CA 파일이 있는지로 네트워크 보안 설정을 고른다(`app/build.gradle.kts` 의 manifestPlaceholders). 캐시가
켜져 있으면 인증서를 만든 뒤에도 옛 판단이 남는다.

```bash
export JAVA_HOME="$HOME/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2"
./gradlew :core:test :net:test :app:testDebugUnitTest --offline
./gradlew :tether:test --offline                                       # 테더의 서버·거르개(약 20초, 루프백만)
./gradlew :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --offline   # lint 오류 0
./gradlew :core:classes :net:classes :tether:classes --offline && python tools/check_api_level.py   # 문제 0건
```

빌드 캐시가 켜져 있다(`org.gradle.caching`) — 시험을 다시 돌려야 하면 시험 과제마다 `--rerun` 을 붙인다. 앱의 시험 가운데 **소스·자원·자산의 글을
읽는 것**(`TetherIsolationTest`·`TetherWiringTest`·`SetupAssetsTest`)을 위해 `app/build.gradle.kts` 가 시험 과제의 입력에 매니페스트·`src/main/kotlin`·
`res/values`·`src/main/assets` 를 적는다 — 그 파일만 바꿔도 시험이 다시 돈다.

**`tools/check_api_level.py` 를 매번 돌린다.** `:core`·`:net`·`:tether` 는 순수 JVM 이라 JDK 의 API 로 컴파일된다 — minSdk 에
없는 함수를 써도 컴파일러도 JVM 시험도 모른다(minSdk 31 때 `URLEncoder.encode(String, Charset)`(API 33)로 **검색하면 앱이 죽었다**). 기준은
`app/build.gradle.kts` 의 `minSdk` 를 읽는다(지금 36 — 분기 판 '36.1' 에 들어온 것과 36 까지 SDK 에서 빠진 것(`removed`)도 문제로 친다). lint 의 `NewApi` 는 앱
모듈만 보고 `checkDependencies` 를 켜도 JVM 모듈은 보지 않았다(되돌려 확인했다). 이 도구는 컴파일된 클래스의 상수 풀에서 `java/…`·`javax/…`
참조를 모아 SDK 의 `api-versions.xml`(lint 가 쓰는 표)과 견준다 — minSdk 31 때 같은 결함 둘(`URLEncoder`·`readNBytes`)을 되돌려 넣으면 둘 다 잡았다(36 에서는 둘 다 있는 API 다).

lint 경고 셋(targetSdk 37·Gradle 9.8·Kotlin 2.4 가 나왔다 — `--offline` 이면 Gradle·Kotlin 의 둘은 뜨지 않을 수 있다)은 viewer 와 같은 선택이라 그대로 둔다(올리려면 내려받기 승인이 필요하다). `MissingOnRenderProcessGone`(androidx.webkit 1.17 의 lint)은
**오탐**이라 막았다 — Kotlin 의 상위 생성자 호출 `WebViewClient()` 을 맨 WebViewClient 를 만드는 것으로 읽는다(세 클래스 모두
`onRenderProcessGone` 을 구현하고 true 를 돌려준다). 뒤로는 플랫폼의 `OnBackInvokedCallback` 하나다(1.0.0 — 12 의 `onBackPressed` 갈래와 그
`GestureBackNavigation` 막기를 걷었다. AndroidX 의 디스패처는 새 의존이다). AAPT2 데몬이 한 번 'Universal C Runtime' 을 탓하며 못 뜬 적이 있다 — 다시 돌리면 된다.

## 모듈

```
core/   JVM. Omnibox(주소창) · Suggester(주소창 제안 — 방문 기록·북마크에서) · SearchEngine(Google 고정) · ProxySettings/Bypass(설정·예외 규칙) ·
        BypassMatcher(앱의 HTTP 가 바로 갈 주소 — 예외 규칙을 크로미움처럼, 모르면 프록시) ·
        ExitTrace(출구) · ContentDisposition · DownloadNames · SafeFileName · DataUrl · Referrer · IpLiterals · PercentCoding ·
        Streams(Long 셈의 범위 읽기·상한까지 읽기 readAll·앞머리만 readHead) · LinkRenewal(만료된 받기를 새 주소로 잇기) · UserAgents(일반화된 UA·데스크톱 사이트) ·
        ClipText(복사한 글의 첫 링크 — '복사한 링크 열기') · LiveTabs(살아 있는 탭 수의 고를 값) · Retention(방문 기록·캐시 보관 기간) ·
        UriRefs(상대 주소를 RFC 3986 대로 풀기·출처 — 재생목록·MPD·쪽의 요청)
        adblock/ FilterParser · UrlPattern · Request · RequestType · Domains · AdBlocker · CosmeticEngine(광고 차단 엔진) ·
                 ListRegex(목록의 정규식 — 되짚지 않는 작은 엔진, 걸음 예산) ·
                 HideSelectors(요소 숨기기의 선택자 짓기·무작위 이름 가리기·이스케이프) · UserHides(사용자 규칙)
        media/   MediaSniffer(미디어 가리기) · HlsPlaylist(재생목록) · HlsCrypto(AES-128) · DashManifest(MPD) · Id3Timestamp(묶음 AAC 의 시각) ·
                 MseSegments(가로챈 덩이의 초기 조각) · FoundMediaLog(찾은 미디어의 페이지 경계) · MediaCodecs(코덱 이름) ·
                 HlsKeys(열쇠 응답 읽기 — 너그러운 후보·첫 조각으로 확인·실패의 갈래) · PageRequests(쪽이 낸 요청의 머리 — 탭마다 메모리에만)
        sites/   갤러리 받기의 사이트 로직 — SiteUrls(주소 알아보기 — UrlRecognizer) · GalleryResolver(계획·풀기) · GalleryCodec(계획의 글 — PlanJson) ·
                 Pixiv(웹 AJAX) · Hitomi(갤러리 정보·gg.js·그림 주소) · SiteNames(이름·화면 글) · SiteResponses(실패 가르기) · Json(상한을 둔 읽개).
                 망은 앱이 주는 SiteFetch 로만 — 아래 '갤러리 받기'.
net/    JVM. LocalRelay(앱 안 중계) · UpstreamProxy · HttpHead · HttpFetcher(앱의 HTTP — 넘겨주기의 홉마다 길을 고른다) · RelayAuthenticator
tether/ JVM, 새 의존 없음. 테더링의 서버와 거르개(패키지 tether.core) — 아래 '테더링'. 앱의 tether/ 가 잇는다
        TcpServer(받기·거르기·상한의 몸통 — TetherEnvironment·StartError) · Socks5Server · HttpProxyServer · FileServer(규약 — 폴더는 SharedTree 로 주입)
        PeerFilter(손님 — 그룹 오너 주소·손님 대역·우리 그룹의 인터페이스, 조회 주입) · DestinationFilter(목적지 — 사설·로컬 대역) · IpAddress
        Outbound(바깥 문 — Socket(Proxy.NO_PROXY)) · ThreadBudget(서버 셋의 스레드 예산) · ConnectionRegistry · DeadlineInputStream · ByteRange · Json ·
        SharedPaths(경로 검사·이름 규칙·휴지통 이름·MIME·filename* 부호) · SharedOps(파일 서버의 관리 기능·휴지통·zip 의 차례 — SharedTree 위) ·
        JsonReader(관리 기능의 JSON 몸 — 엄격) · Thumbnails·ThumbnailCache(섬네일의 형식 보기·디스크 캐시) ·
        Passphrases·PassphraseVault(SSID·패스프레이즈 규칙과 보관) · GroupWatch · ChangeCoalescer · LogThrottle · TetherLog. 시험은 루프백만(
        시험 소스의 DevFileServer 는 PC 에서 화면을 보는 개발용 실행기)
app/    WebApp(설정·길·내려받기·연 사이트 목록을 처음 쓸 때 세운다 — 주 프로세스에서만, 테더 프로세스에서는 WebView 를 끄고 of() 가 던진다) · AppUserAgent(앱이 스스로 내는 요청의 UA)
        proxy/   NetworkPath(길을 정하는 유일한 곳) · AppRoute(앱의 HTTP 가 홉마다 고르는 길 — 순수) · SettingsStore · SecretBox(기기 키) · NetErrors
        crypto/  DeviceKeyCipher(기기 키 AES-256-GCM 잠그기 — 별칭을 받는다, 실패를 잡지 않는다. SecretBox 와 :tether 의 TetherSecret 이 함께)
        browser/ BrowserActivity · Tab · SessionStore · PageClient/PageChrome · WebViews · PageScripts(WebRTC 가드·확대 풀기) · ExternalLinks
        browser/ PageBridge(쪽↔앱 통로 — blob·녹화·가로채기) · BridgeScripts(실행 세계·쪽의 갈고리) · CaptureSessions(가로채기·녹화의 파일)
        browser/ StartPage(새 탭의 시작 화면) · ToolbarGestureLayout(막대의 몸짓) · AddressSuggestions(주소창 제안·인라인 완성)
        browser/ MediaChooser(찾은 미디어의 창들) · MediaProbe(크기·재생목록 미리 읽기 — 내려받기와 같은 길) · CaptureService(녹화·가로채기의 포그라운드 서비스) ·
                 CaptureRegistry(받는 중인 것·되살리기) · CountBadge
        browser/ ProxyStatus(막대의 상태 글) · PageErrors · ErrorCover('열지 못함' 덮개) · MessageBar · PullToRefresh
        browser/ SelectionSearch(글 고르기 메뉴의 '검색' — 검색어 다듬기·고른 글을 읽는 실행 세계 스크립트, 메뉴의 줄은 PageWebView 의 SearchItemCallback)
        browser/ ElementPicker·PickOverlay(요소 고르기 — 덮개와 아래 막대) · PickProtocol(고르는 스크립트와 주고받는 꼴)
        browser/ Fullscreen(전체 화면 — EdgeSwipe·BarTimer·FullscreenLayout 판단, TopStrips·BrowserFrame 배치)
        browser/ ScreenWake(재생 중 화면 켜 두기 — 틀마다의 재생 PlayingMedia 와 켜 둘지의 판단)
        browser/ YtGuard(유튜브 광고 가리기·건너뛰기 — 출처 규칙·켜질 조건·누를 자리와 실행 세계 스크립트, PageBridge 가 건다 — 누르기는
                 PageWebView.tap)
        browser/ BackgroundPlay(뒤에서 재생 잇기의 판단 — 켜질 조건·알림의 글·10분, 유튜브의 보임 속이기 VISIBILITY_SCRIPT) · BackgroundPlayService(mediaPlayback 서비스 — 알림 5·미디어 세션·
                 잠금, 화면과는 BackgroundPlayback 으로) — WebView 를 보이는 채로 두는 것은 PageWebView(keepWindowVisible·backstaged), 그동안
                 화면 밖에 그리는 것은 OffscreenDraw(PageWebView.drawOffscreen)
        library/ Library(북마크·방문 기록 — SQLite) · LibraryActivity
        sites/   SiteList(연 사이트) · SiteDataActivity(사이트별 데이터) · PopupSites(팝업 허용)   settings/SettingsActivity
        adblock/ AdBlockService(목록·엔진·허용·갱신) · AdBlockActivity(설정 — '숨긴 요소' 절 포함) · AdBlockUpdateJob(하루 한 번 — JobScheduler) ·
                 HiddenElements(사용자가 숨긴 요소 — hidden-elements.txt)
        download/ DownloadCenter(받기·이어받기) · DownloadStore(목록 — downloads.json) · Remux(그릇 바꿔 담기 — media3 추출기 + MediaMuxer) ·
                  DownloadService · DownloadsActivity · DownloadTexts · HlsKeyTexts(열쇠를 받지 못한 글의 갈래) · MediaStoreWriter   ui/Ui(화면들의 작은 도구 — '링크 복사' 포함)
        download/ TextParts(글 조각 꼴 Part·Sizes, mimeOf — 갤러리·열쇠 글) · DetailsDialog(목록의 '자세히' 창) ·
                  HopHeaders(내려받기·MediaProbe 가 함께 쓰는 홉마다의 머리 — UA·쿠키·Referer)
        download/ GalleryRules(갤러리 받기의 판단 — 자리·글·이어받을 자리·건너뛰기와 멈춤, 순수) · GalleryPrompt(묻기) · DownloadSets(곁의 계획·기록 —
                  noBackupFilesDir/download-sets) — 받기 자체는 DownloadCenter 의 SET(`runSet`)
        tether/  (모두 :tether 프로세스) TetherService(specialUse — 떠 있는 것이 곧 테더링: 서버 셋·핫스팟·알림 4) · TetherActivity(화면 — 켜기·끄기 하나) ·
                 HotspotStartActivity(타일의 트램폴린) · HotspotTileService(퀵 설정 타일 '테더링') · TilePreferencesActivity(타일을 길게 누를 때 — 내보냄, 테더
                 화면만 연다) · WifiHotspot(Wi-Fi Direct 그룹) · TetherState(그 프로세스의 상태·듣개) · TetherSettings·TetherSecret(설정
                 `tether`·기기 키·관리 허용) · SharedFolder(SAF 트리 — DocumentsContract, 섬네일) · TetherPermissions · TetherTexts(기계 말 → 글)
                 assets/tether/fileman.html·fileman.js·fileman.css(파일 서버의 화면)
        setup/   (주 프로세스와 :tether 가 함께) SetupZip(묶음 목록·zip 짓기 — 순수) · SetupFiles(자산 → zip → '내려받기')   assets/setup/proxy-server·tether-pc
app/src/test/   판단을 떼어 낸 순수 함수의 시험(상태 글·오류 까닭·당김·NetErrors·앱의 HTTP 길(AppRoute)·DownloadCenter·PickProtocol·전체 화면·재생 중 화면 켜 두기·테더의 글과 프로세스 경계·설정 파일 zip·유튜브 광고 가리기와 건너뛰기·갤러리 받기의 판단과 곁의 파일과 배선·뒤에서 재생 잇기·주소창과 탭의 작은 것들·m3u8 열쇠 받기의 글과 배선·내려받기의 홉마다 Referer·WebRTC 가드의 꼴·파일 서버의 화면 자산과 관리 허용 배선·올리기의 덮어쓰기 묻기·휴지통 보관 기간 배선·모든 파일에 적용과 폴더와 같은 이름·화면과 서버가 빼는 글자·이름을 열쇠로 쓰는 맵) — `kotlin("test-junit")`
app/src/debug/  lab/LabActivity·LabParams·LabScripts(실험) · lab/LabSetupActivity(시험 도구가 설정을 넣고 사이트별 데이터 화면을 연다) ·
                lab/TetherFolderProbeActivity(:tether — 파일 서버의 SAF 쪽·관리 기능을 기기에서 돌려 logcat `WebProbe` 에, `do=screen` 이면 테더 화면) — 모두 DUMP 권한
tools/  check_api_level.py · lab/make_certs.py · lab/labserver.py · lab/labmedia.py(시험 미디어 — 나누기·TS 짜기·담긴 MP4 읽기) ·
        lab/run_lab.py(WebView 의 성질) · lab/run_browser.py(이 앱 — 기대가 낡았다, 아래 '실험실')
```

## 브라우저 — 무엇을 왜 그렇게 하나

### 길 — 중계 하나·fail-closed

- **`NetworkPath` 가 길을 정하는 유일한 곳이다.** 프록시가 켜져 있으면 중계를 세우고 WebView(`ProxyController` — 프로세스 전체)와 앱의 HTTP 를
  그리로 보낸다. 프록시를 정하지 않았으면 중계가 모든 요청에 502 `not-configured` — **직접 연결로 가는 길이 없다**(`addDirect` 를 넣지 않는다).
  끄면(사용자가 고른 것) override 를 걷고 직접 간다. 설정을 바꾸거나 켜고 끄면(`update`) 중계를 새로 세우고(새 세션 계정·realm·포트) 판
  (`revision`)이 오른다 — '길이 걸렸다' 는 화면의 깃발이 아니라 이 판으로 본다(화면 안에서 길을 바꾸면 깃발은 그대로 참이라 새 길이 걸리기 전에
  탭을 다시 불렀을 것이다).
- **쪽은 `whenReady` 로만 연다** — 지금 판의 길이 WebView 에 걸리고 데워진 뒤에(그 사이에 연 쪽은 옛 길, 켜던 중이면 직접으로 나간다). 판이
  바뀌면 열린 탭을 모두 다시 불러 옛 길의 연결(웹소켓 등)을 끊는다. 처음 띄울 때 켜져 있는데 정하지 않았으면 설정을 먼저 연다. 설정을 바꾸고
  저장하지 않은 채 나가면 묻는다. 길이 그대로면(`ProxySettings.sameRoute`) '저장' 이 중계를 다시 세우지 않는다('바뀐 것이 없습니다' — 받던
  HLS·DASH 가 끊기지 않게). 설정은 `commit` 으로 곧바로 쓴다(`apply` 는 나중에 써서, 바꾼 직후 앱을 죽이면 옛 길로 떴다).
- **끔 → 켬** — 켜는 순간 녹화·가로채기 중이 아닌 탭을 모두 접는다(WebView 를 없애 연결을 그 자리에서 끊고, 뒤로·앞으로 기록은 메모리에
  남긴다). 지금 탭은 데운 뒤, 뒤의 탭은 고를 때 되살린다. 녹화·가로채기 탭은 `NetworkPath.whenApplied`(override 가 걸리는 즉시)에 다시
  부른다. 모든 탭을 override 시점에 다시 부르지 않는 것은 데우기 전이면 서비스 워커의 요청이 407 을 못 넘기기 때문이다(추론).
- **데우기.** 서비스 워커의 요청은 407 에 답하지 못하므로(실험 sw-cold) 첫 쪽 전에 WebView 의 인증 캐시를 채운다. 중계가 **스스로 204 로
  답하는 주소** `http://warmup.iroiro.invalid/?<표>` 를 보이지 않는 WebView 로 연다 — 계정을 확인한 뒤 답하므로 캐시가 차고, **바깥으로 나가는
  요청도 이름 풀이도 없다**(실험실에서 프록시·DNS 기록 0건). 최대 3초. 표는 중계가 돌려 알린다(`Listener.onWarmedUp(tag)`) — 옛 중계의 늦은
  소식과 섞이지 않게.
- **상태와 출구.** 중계가 원격의 일(닿음·계정 거절·불통)을 알리고(`LocalRelay.Listener` — 원격이 그 목적지만 거절한 것은 '닿음'), 출구 확인
  (`https://cloudflare.com/cdn-cgi/trace` — 프록시와 상관없는 제3자, 설정에서 바꾼다)이 앱의 HTTP 로 5분마다·설정을 바꿀 때 IP 와 나라를
  읽는다. 막대의 스위치 옆 **따로 된 글**이 상태다(누르면 '연결 상태' — 스위치 자신의 글이면 뜻을 보려고 누를 때 스위치가 꺼진다): 나라(초록) ·
  `···`(확인 중) · `KR!`(한국 — 프록시를 거치지 않는 것 같다) · `끊김` · `계정 거절` · `출구 모름` · `미지원`. 꺼져 있거나 정하지 않았으면 비운다.
  스위치의 `stateDescription` 은 '켜짐 · 출구 미국'. 문제가 있으면 알림 띠('설정' 단추 하나). '연결 상태' 는 프록시·출구 IP·DNS 미리 찾기 안내와
  예외(테더링)를 늘 보인다.
- **인증.** WebView 의 인증 콜백에는 **중계의 407**(무작위 realm 으로 가린다)만 중계 계정으로 답하고, 그 밖은 사이트의 401 이라 사용자에게
  묻는다. 원격 계정은 WebView 에 없다(실험 ambiguity — 직접 모드에서는 같은 이름의 사이트가 프록시 계정을 받았다). 앱의 HTTP 는
  `RelayAuthenticator`(중계의 포트·루프백에만 답한다)가 답한다. **비밀번호는 기기 키(AndroidKeyStore AES-256-GCM)로 잠가** 설정 파일에 둔다
  (백업·이전에서 빠진다).
- **중계(`LocalRelay`).** 손님·원격 양쪽에 `TCP_NODELAY` — 터널은 작은 조각(TLS 레코드·HTTP/2 프레임)을 잇달아 쓰는데 켜 두면 주고받기마다
  왕복 하나가 더 들 수 있다(크로미움도 프록시 연결에서 끈다. 효과는 따로 재지 않았다). 원격 이름이 주소 여럿으로 풀리면 차례로 해 본다 —
  마지막에 붙은 주소를 먼저, 뒤에 해 볼 주소가 남았으면 3초까지만(셀룰러는 IPv6 를 먼저 주므로 IPv4 로만 받는 프록시면 모든 연결이
  실패했을 것이다 — 시험이 루프백의 IPv4·IPv6 로 본다). 자리 256 은 계정을 확인한 뒤에야 잡고, 인증 전 손님은 따로 64 자리(차면 가장 오래
  기다린 것부터 내보낸다)이며 받은 때부터 5초 안에 계정이 실린 머리를 보내야 한다(한 바이트씩 흘려도 끊긴다). 인증 뒤 자리가 없으면 503
  (수치는 추론). `close()` 가 손님·원격 연결을 실제로 끊는다(끊는 일은 따로 된 스레드 — 함정 '중계를 닫는 스레드'). 중계가 만든 오류 응답에는 짧은 한국어 페이지(`errorPage` — 문구는 앱이 넘기고,
  CSP `default-src 'none'`). 평문 http 도 요청마다 원격 TLS 를 새로 연다(아래 '남은 일'). **요청 줄·머리 줄 안의 홀로 선 CR·LF 는 400**(머리는 CRLF 로만 가르므로 그런 글자가 줄 안에 살아남는데, 중계는 요청 줄(CONNECT 의 대상)과 값을 원격 Squid 에 바이트 그대로 다시
  쓴다 — 홀로 선 CR·LF 를 줄 끝으로 읽는 서버에는 머리 주입·스머글링의 재료다. 이 앱의 WebView·HttpURLConnection 은 그런 글자를 넣지 못해 지금은
  악용할 길이 없다 — 테더의 HTTP 프록시와 같게 맞춘 심층 방어, `HttpHead.hasBareLineBreak`). 원격이 준 응답 머리는 그대로 읽는다.

### 앱의 HTTP 와 예외 규칙

앱의 HTTP(내려받기·HLS·DASH·찾은 미디어 미리 읽기·광고 목록)도 WebView 와 같은 예외 규칙(override 의 bypass)으로 사설망을 바로 연다 — 그래야
공유기·NAS 의 파일을 받는다(사용자 결정). **기기에서는 아직 확인하지 않았다**(아래 '확인하지 못한 것').

**판정 — `core/BypassMatcher`.** WebView 에 넘기는 규칙 그대로(`Bypass.rules()`, 점 없는 이름이면 `<local>`)를 크로미움의
`net::ProxyBypassRules` 처럼 읽는다 — 호스트 무늬(`example.com` 은 그 이름 하나, `*.example.com` 은 그 아래 모든 깊이이고 자신은 아니다, ASCII
소문자로 견준다 — 점 네 개의 IPv4 글자에도 견준다), 무늬 없는 IPv4 는 그 주소 하나, IP 대역은 주소가 IP 글자일 때만(이름을 풀지 않는다) 앞 N
비트, `<local>` 은 점이 없고 IP 가 아닌 이름, 그리고 크로미움이 늘 거는 암묵 규칙(`localhost`·127.0.0.0/8·`::1`·169.254.0.0/16·`fe80::/10` — 이
앱은 `<-loopback>` 을 넣지 않는다). **약속은 하나 — 크로미움이 프록시로 보낼 주소를 바로 보내지 않는다.** 거꾸로(크로미움은 바로 여는데 여기는
프록시)는 된다 — 그 받기가 예전처럼 실패할 뿐이다(fail-closed). 그래서 확실하지 않은 꼴은 모두 프록시다. 견주는 호스트는 자바가 실제로 붙을
호스트(`URL.getHost`)다 — 바로 붙는 곳이 언제나 예외에 든 곳이게.

| 크로미움과 다른 것 — 모두 '여기는 프록시' | 까닭 |
|---|---|
| `*.localhost` | 크로미움은 스스로 루프백으로 풀지만 앱의 HTTP 는 시스템 DNS 로 푼다 — 망의 DNS 가 공인 주소를 주면 거기 바로 붙는다 |
| 끝에 점이 있는 이름(`nas.local.`·`localhost.`·`10.0.0.1.`) | 크로미움은 `localhost.` 를 암묵 규칙으로 보고 IPv4 의 끝 점을 떼어 바로 연다 |
| 점 네 개의 십진 꼴이 아닌 IPv4(`127.1`·`0x7f.0.0.1`·`010.0.0.1`·`2130706433`·`foo.123`) | 크로미움은 IPv4 로 고쳐 읽는다(`010` 은 8진수 8, `foo.123` 은 깨진 주소). 자바·OkHttp 가 같은 주소에 붙는다고 보일 수 없다 — 느슨하게 십진수로 읽으면 `010.0.0.1` 이 10/8 이라 바로가 되는데 크로미움은 8.0.0.1(프록시)이다 |
| 사용자 정보가 든 주소, 1~65535 밖의 포트, ASCII 밖의 글자(유니코드 호스트 포함)·`%`·`\`·빈칸이 든 호스트, 빈 호스트 | 자바(`URL`)·OkHttp·크로미움이 같은 호스트로 읽는다고 보일 수 없다(`evil%2ecom` 은 자바에게 점 없는 이름, 크로미움에게 `evil.com`). WebView 가 넘기는 주소는 이미 퓨니코드다 |
| IPv4 를 실은 IPv6(`[::ffff:10.0.0.1]`) 대 IPv4 대역, IPv4 대 IPv6 대역(`::ffff:0:0/96`) | 크로미움은 견준다. 여기는 같은 꼴끼리만. IPv4 를 실은 루프백·링크 로컬도 암묵 규칙으로 보지 않는다 |
| 크로미움이 다르게 읽을 수 있는 규칙(앞의 0·짧은 꼴·16진의 IPv4, 스킴·포트가 붙은 것, 가운데의 별표, 별표 하나, 괄호 붙은 IPv6 대역, ASCII 밖의 글자) | 없는 셈 친다. `<-loopback>` 이 하나라도 있으면 아무것도 바로 보내지 않는다(크로미움에서는 차례에 따라 암묵 규칙이 빠진다) |

**규칙을 넣는 칸도 단단히 했다**(`Bypass.normalizeCidr`·`normalizeHost`). IPv4 칸에 앞의 0 을 받지 않는다 — 크로미움은 `010.0.0.0/8` 을
**8.0.0.0/8(공인 대역)** 으로 읽어 WebView 가 거기를 바로 열고(사설망이라 믿는 사이 실제 IP 가 드러난다 — 설정의 공인 대역 경고도 `010` 을 10 으로
읽어 비켜 갔다), `08.…` 은 받지 않아 WebView 에 길을 거는 순간 던졌을 것이다 — androidx 의 `addBypassRule` 은 거르지 않고(바이트코드로 확인 —
그래서 `applyToWebView` 가 `buildConfig` 를 감싼 try 는 그 실패를 잡지 못한다), 거르는 것은 WebView 쪽 `setProxyOverride` 이며 그 호출은 try
밖이다(크로미움 소스로 기억하는 것 — 기기에서 재지 않았다, 아래 '남은 일'). 끝 칸이 숫자인 무늬 없는 호스트(`10.1`·`010.0.0.1`·`foo.123`)는
점 네 개의 십진 꼴만 받는다. 이미 저장된 옛 규칙은 다시 거르지 않는다 — 설정에서 다시 저장하면 걸리고, 그동안 앱의 HTTP 는 그 규칙을 없는 셈
친다.

**홉마다 — `net/HttpFetcher`.** 길이 하나의 `Proxy` 가 아니라 `HttpFetcher.Route`(`(URL) -> Proxy?`)이고, 넘겨주기를 따를 때마다 그 홉의 주소로
다시 묻는다 — 사설망의 페이지가 인터넷으로(둘째 홉은 프록시), 인터넷이 사설망으로(둘째 홉은 바로) 넘겨줄 수 있다. null 이면 연결을 열지 않고
`HttpFetcher.NoRoute`('프록시가 준비되지 않았습니다' — `NetErrors`). 바로 붙는 것은 중계가 아니라 앱의 HTTP 자신이다(그 홉만
`Proxy.NO_PROXY` — 중계는 그대로 원격 프록시로만 간다).

**길 — `proxy/AppRoute`(`NetworkPath.httpRoute()`).** 설정·중계가 바뀔 때 `NetworkPath` 가 하나로 지어 바꿔 끼우고(volatile 하나 —
`publishRoute`), 받기 하나(시도 하나)는 얻은 것을 끝까지 쓴다 — 한 받기의 홉들이 서로 다른 기준으로 고르지 않는다(옛 중계가 닫혔으면 연결 오류로
끝나고 다음 시도가 새 길을 얻는다).

| 상태 | 앱의 HTTP |
|---|---|
| 프록시를 껐다 | 모두 바로 |
| 켰는데 중계가 없다 | **모두 받지 않는다 — 예외 주소도.** WebView 의 예외는 중계와 함께 거는 override 에만 있다 |
| 중계가 있다(프록시를 정하지 않았어도) | 예외에 들면 바로, 아니면 중계(127.0.0.1). 정하지 않은 동안 WebView 도 같은 규칙으로 사설망을 바로 연다 |
| 출구 확인 | **늘 중계**(넘겨주기의 홉까지 — `AppRoute.relayOnly`). 프록시의 출구를 보려는 것이라 확인 주소나 그 넘겨주기가 예외에 들어도 바로 가면 확인이 거짓말을 한다 |

**계정.** `RelayAuthenticator` 는 그대로다(프록시·중계의 포트·루프백). 바로 붙은 연결의 407 에 JDK 는 프록시 인증을 묻지 않는다 — 시험이 중계와
**같은 포트의 다른 루프백 주소**(127.0.0.2)와 다른 포트에 401·407 을 내는 서버를 세워(중계의 realm 을 흉내 낸다) 계정이 실리지 않는 것을 본다.
안드로이드의 OkHttp 는 프록시 없는 연결의 407 을 `ProtocolException` 으로 끝낸다(소스로 읽은 것 — 기기에서 재지 않았다). 바로 간 받기는 그
사설망 기기에 실제 IP 로 닿고 그 이름을 기기의 DNS 에 묻는다 — WebView 와 같다(사용자가 예외로 둔 결과다. 남의 Wi-Fi 에서 사설망을 예외로 두면 그
망의 기기에도 바로 붙는다).

**시험.** `:core` `BypassMatcherTest`·`ProxySettingsTest`·`IpLiteralsTest`, `:net` `HttpFetcherTest`(바로→프록시·프록시→바로·길이 없음·바로
붙은 루프백의 401/407), `:app` `AppRouteTest`·`NetErrorsTest` 의 `NoRoute` — 길을 첫 홉에서 한 번만 고르기·주소·규칙의 IPv4 를 느슨하게 읽기·사용자 정보 검사 빼기·
`<-loopback>` 모른 척하기를 각각 잡는다.

**확인하지 못한 것(기기).** 실험실 판으로 사설망 쪽(`10.0.2.2`)의 받기·HLS·찾은 미디어 크기가 바로 가는 것, 사설망 ↔ 인터넷 넘겨주기, 바로 간
받기의 쿠키·Referer, 광고 목록을 사설망 주소로 더한 것, OkHttp 가 프록시 없는 연결의 407 을 정말 `ProtocolException` 으로 끝내는지, 시스템 DNS 가
`*.local`·점 없는 이름을 어떻게 푸는지.

### 쪽의 결정·UA·DRM

- **WebRTC 가드 2판**(문서 시작 스크립트 — 아래 '실험 결과' 의 15길을 모두 막는다). 가드는 쪽의 세계에서 쪽보다 먼저 돌지만 틀 나무를 훑는 일은 쪽이
  틀을 만든 뒤에 한다 — 그때 부르는 내장 함수(`Object.getOwnPropertyNames`·`RegExp.prototype.test`·`window.length` …)를 쪽이 바꿔 두면 새 빈 틀의
  `RTCPeerConnection` 이 남는다(실제 IP 가 새는 길 — 함정 '쪽의 세계 스크립트가 나중에 부르는 것'). 그래서 쓸 것은 처음에 붙잡고(`Reflect.apply` 로
  부른다), 속성 설명은 원형 없는 객체로, 틀 나무는 연결 목록과 처음에 붙잡은 `window.length` getter 로 훑는다. `Function.prototype.apply`·`call` 은 부르지
  않는다(쪽이 바꿔 원래 함수를 빼내지 못하게). 꼴은 JVM 시험 `WebRtcGuardTest`, 시험 쪽은 스크래치패드에만. `X-Requested-With` 빈 값(1.17 의
  `Profile.addCustomHeader` — 쪽이 스스로 단 값은 그대로라 AJAX 판정이 깨지지 않는다), 세이프 브라우징 끔, 파일·content 접근 끔(서비스 워커도 — 따로
  설정이고 기본값은 켜짐), 혼합 콘텐츠 막음, 서드파티 쿠키 막음(기본), 카메라·마이크·위치 거절. **웹 페이지에는 위치를 주지 않는다** — WebView 의 위치
  API 를 끄고(`setGeolocationEnabled(false)`) 위치 요청을 늘 거절한다(`PageChrome.onGeolocationPermissionsShowPrompt`). 앱은 위치 권한도 갖지 않는다
  (1.0.0 — 12 의 테더링이 요구하던 위치 권한을 걷어 OS 권한이 다시 바깥 겹이다).
- **정체 감추기.** **WebView Media Integrity API 를 끈다** — 기본값(켜짐)이면 쪽의 스크립트가
  `window.android.webview.getExperimentalMediaIntegrityTokenProvider` 로 Play 의 무결성 토큰을 받고 그 토큰에 이 앱의 꾸러미 이름이 실린다(androidx 의
  `WEBVIEW_MEDIA_INTEGRITY_API_ENABLED` 문서) — `X-Requested-With` 를 비운 까닭과 같다. **파비콘을 받지 않는다**(`setDownloadFaviconsEnabled(false)` — 이
  앱은 파비콘을 쓰지 않는데 쪽마다 요청 하나씩 받았다). 둘 다 `WebViews.hideIdentity`, 그 기능을 모르는 WebView 면 그 줄만 건너뛴다(에뮬레이터에서 둘 다
  듣는 것을 봤다).
- 팝업 — 사용자가 누르지 않은 새 창은 앱이 막고 알린다(아래 '사이트별 데이터·팝업'). 누른 것은 새 탭.
- **SSL 오류는 넘기지 않는다.** 주 문서의 인증서 오류만 덮개로 알린다(그림 하나의 인증서로 쪽 전체가 막힌 것처럼 보이지 않게).
- WebView 가 다루지 않는 스킴(`intent:` 등)은 주 프레임·누름(또는 그 넘겨주기)일 때만 묻고 연다 — `intent:` 의 컴포넌트·셀렉터·URI 권한은
  지우고, 받을 앱이 없으면 `browser_fallback_url`. 묻는 창이 다른 앱은 프록시를 거치지 않는다고 말한다.
- 렌더러가 죽으면 그 탭만 비우고 앱은 산다(덮개로 까닭과 [다시 시도], 뒤의 탭은 고를 때 다시 연다).
- **UA·클라이언트 힌트**(사용자 결정 — 일반화). 글은 **크롬의 줄인 꼴**(`UserAgents` — `Mozilla/5.0 (Linux; Android 10; K) … Chrome/<판>.0.0.0
  Mobile Safari/537.36`, 크롬 판은 기기의 WebView 를 따른다), 힌트는 WebView 의 값에서 **`Android WebView` 상표와 기기 모델을 뺀다**
  (`setUserAgentMetadata` — 글만 바꾸면 WebView 는 낮은 엔트로피의 힌트만 보내는데 그 상표에 WebView 가 남는다고 문서가 적었다). 안드로이드 판
  (`platformVersion`)은 크롬처럼 실제 값. 앱의 내려받기도 WebView 의 UA 를 쓰고, **앱이 스스로 내는 요청**(출구 확인·광고 목록)도 같은 꼴이다
  (`AppUserAgent`). **서비스 워커가 내는 요청은 덮지 못한다** — 아래 '사용자가 정할 것'.
- **데스크톱 사이트** — 탭마다(메뉴). `UserAgents.desktop()`(`X11; Linux x86_64`, 힌트 Linux·x86·64비트·데스크톱 — 크로미움의 데스크톱 사이트
  처리를 기억으로 옮긴 꼴, 소스로 확인하지 않았다). 세션에 남고 그 탭에서 연 탭이 물려받는다.
- **DRM 재생**(사용자 결정). `onPermissionRequest` 가 **보호된 미디어(`RESOURCE_PROTECTED_MEDIA_ID`)만** 준다 — 쪽이 EME(Widevine)로
  라이선스를 청하려면 이것이 있어야 한다(Widevine 은 출처마다 기기 식별자를 쓴다). 카메라·마이크와 함께 청하면 모두 거절. DRM 영상은 재생만 되고
  받기·가로채기·녹화는 하지 않는다. 에뮬레이터는 MediaCrypto 가 없어 키를 만들지 못한다(`requestMediaKeySystemAccess` 와 권한까지만 확인했다).

### 주소창과 제안

- **`Omnibox`.** 스킴 없는 도메인은 **https**, `localhost`·IP 는 http, 낱말 하나·빈칸 든 글·`@` 든 글은 검색어, `javascript:` 는 검색어(주소창에서
  스크립트를 돌리지 않는다), 국제화 도메인은 퓨니코드, **`magnet:` 은 다른 앱에 넘길지 묻는다**(빈칸이 들었거나 깨졌어도 검색어로 돌리지
  않는다 — info-hash 가 검색 엔진에 가지 않게 — `Omnibox.Target.Magnet`; Enter 와 페이지의 마그넷 고리는 다른 스킴과 같은 '다른 앱으로 열까요?' 창).
  검색은 **Google 하나**(`core/SearchEngine` — `searchUrl`·`LABEL`) — **검색어 제안은 하지 않는다**(친 글을 검색 엔진에 묻지 않는다).
- **제안(`Suggester` — `:core`, 화면은 `AddressSuggestions`)** — Chromium·Firefox for Android 의 소스로 그 관행을 따랐다. **방문 기록·북마크·열린
  탭에서만** 고른다. 자판에는 늘 URI 꼴 + 제안 없음 + 개인 학습 없음을 준다.
  - **인라인 완성**: 친 글이 **ASCII 한 낱말**이고 아는 주소(`https://`·`www.` 를 뗀 것)의 앞머리이면 그 뒤를 골라 둔 채 채운다 — 호스트까지
    치는 동안은 호스트까지만(`na` → `naver.com`), `/`·`?`·`:` 를 치면 주소 전체. 한글이 섞이거나 조합 중인 글이 있으면 채우지 않는다. **문턱** —
    주소창에서 친 적이 있거나(`typed`) 북마크했거나 두 번 넘게 간 곳(크롬은 친 곳만, 파이어폭스는 간 곳 모두 — 그 사이를 골랐다). 같은 호스트의
    쪽들은 점수를 모아 견준다. Enter 는 채운 글이 가리키는 기록의 주소로 간다. **지우기 한 번은 채운 것만 지우고**, 친 글이 늘지 않은 동안은 다시
    채우지 않는다 — '지웠는가' 는 **친 글의 길이가 늘었는가**로 가른다. 채운 글은 진짜 선택으로 그린다(두 엔진은 배경색 + 숨긴 커서 + 자판
    주고받기 감싸기) — 에뮬레이터의 키 입력으로는 맞게 돌았고 삼성 키보드에서는 확인하지 않았다(어긋나면 크롬의
    `SpannableAutocompleteEditTextModel` 방식으로 바꾼다).
  - **목록**: **맨 위는 언제나 Enter 가 갈 곳** — 인라인이 있으면 그 주소, 없으면 '입력한 그대로'(주소면 '이동', 아니면 'Google 검색', `magnet:`
    이면 '다른 앱으로 열기(검색 엔진에 보내지 않습니다)'). 그다음 친 글에 맞는 열린 탭('탭으로 전환' — 지금 탭은 빼고 둘까지), 기록·북마크 여섯
    줄까지. 빈 글로 초점을 얻으면 자주·최근 간 곳 여섯(새 탭의 시작 화면은 덮지 않는다). 낱말마다(빈칸으로 가른다) 제목이나 주소에 있어야
    하고, 무게는 주소의 앞머리 8 · 낱말의 앞머리 3 · 가운데 1(로마자 한 글자는 앞머리만 — 한글은 붙여 쓴 덩어리가 한 낱말이라 가운데 맞추기가
    필요하다)에 간 횟수·친 횟수의 로그 합을 4·14·31·90·365일 마디로 줄인 값(사이는 곧게)을 곱한다(방문마다의 시각이 없어 모든 방문을 마지막
    때로 친다). 스킴·`www.`·조각·끝의 `/` 만 다른 주소는 한 줄. 기록 줄의 ↖ 는 그 주소를 주소창에 채워 고쳐 쓰게, **길게 누르면 기록에서
    지운다**(묻고 — 북마크는 그대로).
  - 계산은 일꾼 스레드에서(기록 1만 줄에 PC 의 JVM 으로 4~5 ms — 폰은 몇 배), 답이 왔을 때 **친 글**(채운 것은 뺀 것)이 바뀌었으면 버린다. 기록은
    주소창이 초점을 얻을 때 메모리에 한 벌 지어 두고(`Library.suggester`) 쓰기가 있으면 다시 짓는다.
  - DB 2판(`browser.db`) — `history.typed`(주소창에서 쳐서·제안을 골라 간 횟수). 탭의 '주소창에서 연 쪽' 표시(`Tab.typedNavigation`)로 다 열었을
    때 센다. 검색은 세지 않는다.
- 새 탭은 주소창 초점과 자판을 함께 띄운다(새 탭은 주소를 치려고 여는 일이 많다).
- **복사한 링크 열기.** 주소창을 빈 글로 열면, 클립보드에 링크가 있어 보일 때 맨 위에 '복사한 링크 열기'(→)를 둔다 — 새 탭의 시작 화면 위에서는 그 줄
  하나만(시작 화면을 덮지 않는다). **클립보드의 내용은 누를 때만 읽는다**(시스템은 남의 클립을 읽을 때마다 알린다 — 12 부터) — 그 전에는 설명만 본다
  (`AddressSuggestions.clipHasLink`): 10분 안에 복사한 것(`ClipDescription.getTimestamp` — `System.currentTimeMillis` 의 시계)이고, 링크 목록
  (`text/uri-list`)이거나 이름표가 http(s) 주소 하나(이 앱의 '링크 복사' 는 이름표에 주소를 둔다 — 브라우저 화면과 북마크·방문 기록 화면이 함께 쓰는 `Ui.copyLink` 한 곳, 파일 이름을 이름표로 다는 앱이 있어 스킴을 적은
  것만)거나, 글이면 시스템의 글 분류(`getConfidenceScore(TYPE_URL)` 0.9 이상) — **분류가 아직이거나 하지 않았으면 줄을 둔다**(함정 '클립보드의 설명').
  누르면 그 글의 첫 http(s) 링크로 간다(`core/ClipText` — 글 전체가 주소면 그것, 아니면 글 안의 첫 `http(s)://`, 끝의 문장 부호·짝 없는 닫는 괄호·전각
  괄호와 따옴표는 뗀다, `data:`·`about:`·`javascript:`·`magnet:` 은 고르지 않는다. 글이 없는 항목은 http(s) 주소만 — `coerceToText` 는 `content:` 주소면
  남의 제공자를 열어 읽는다). 링크가 없으면 '복사한 글에서 링크를 찾지 못했습니다'. 한 번 연(또는 링크가 없던) 클립은 다시 두지 않는다(복사한 시각으로
  가린다).

### 탭·세션·막대의 몸짓

- **세션(`SessionStore`).** 열린 탭의 주소·제목·고른 탭·데스크톱 사이트를 `session.json`(`AtomicFile`)에 1초 늦춰 적고, 화면이 멈출 때(`onStop`)
  바로 적는다. 다시 뜨면 탭을 모두 만들되 WebView 는 고를 때 만든다(고른 탭만 바로 — 길이 걸린 뒤에). **뒤로·앞으로 기록은 디스크에 두지
  않는다** — WebView 의 상태 묶음은 `Parcel` 이라 플랫폼 판이 바뀌면 못 읽는다. `data:`·8 KB 넘는 주소는 빈 탭으로 둔다.
- **살아 있는 탭**(기본 5 — 설정 › 화면에서 1·2·3·5·8·10·15·20). WebView 는 탭마다 수십 MB 다. 넘으면 가장 오래 안 본 탭을
  **접는다** — `saveState` 를 메모리에 두고 WebView 를 없앤다. 고르면 새 WebView 에 `restoreState` 로 쪽과 뒤로·앞으로 기록이 돌아온다. 접힌 쪽은
  멈춘다(소리·영상도). 지금 탭·뒤에서 재생을 잇는 탭·녹화·가로채기 중인 탭은 접지 않는다. 설정에서 줄이면 브라우저 화면으로 돌아갈 때(`onResume`)
  넘는 탭을 접는다. 수는 `core/LiveTabs`(저장값은 그 범위로 맞춘다).
- **길게 누르기.** 고리: 새 탭에서 열기(**곧바로 그 탭으로** — 그 탭에서 뒤로 갈 곳이 없으면 연 탭으로 돌아온다)·링크 복사·링크 공유·링크 내려받기·이 요소 숨기기. 그림: 이미지 받기(내려받기와 같은 길 — 중계·쿠키·Referer, `data:` 그림도)·새 탭에서
  열기(곧바로). 글을 길게 누르면 WebView 의 글 고르기 — 그 메뉴에 '검색'(아래).
- **글 고르기 메뉴의 '검색'.** 크로미움은 고르기 메뉴를 WebView 의 `startActionMode(…, TYPE_FLOATING)` 로 띄운다 — `PageWebView` 가 그 콜백을 감싸
  (`SearchItemCallback`) '복사' 와 같은 차례·같은 '늘 보임'(`SHOW_AS_ACTION_ALWAYS`)으로 '검색' 을 더해 '복사' 바로 뒤에 둔다(함정 '떠 있는 고르기 메뉴의
  차례'). '복사' 가 없는 메뉴(비밀번호 칸·고른 글이 없는 입력칸)에는 두지 않고, 크로미움이 메뉴를 다시 지으면 다시 본다. 메뉴의 자리 셈
  (`onGetContentRect`)은 크로미움의 것을 그대로 쓴다. 누르면 통로가 틀마다의 실행 세계에서 고른 글을 읽는다(`PageBridge.selectedText` —
  `JavaScriptReplyProxy.executeJavaScript`, 초점이 있는 틀의 글을 먼저, 1.5초까지 — 입력칸 안의 고른 글도, 비밀번호 칸은 읽지 않는다) → 메뉴를 닫고
  (먼저 닫으면 고르기가 풀려 글이 빈다) → 다듬어(제어·방향 제어 문자를 빼고 500 글자 — `SelectionSearch`) Google 로 새 탭에서(곧바로 그 탭으로).
  **글을 고른 채 뒤로 가기는 고르기만 푼다**(`PageWebView.finishSelection` — 함정 '떠 있는 메뉴와 뒤로 가기'). 통로를 모르는 WebView 면 메뉴를 감싸지
  않는다.
- **닫은 탭은 되살리지 않는다**(걷었다 — 위 '걷은 것'). 탭을 닫는 길은 `closeTabs()` 하나이고 닫은 탭의 기록은 곧바로 버린다. 탭 단추의 설명은 '탭 N개', 녹화·가로채기 탭이 있으면 빨간 테두리.
- **쪽을 화면에서 떼는 곳은 모두 `detach` 를 지난다** — 초점을 먼저 거두고(`clearFocus`) 뗀다(탭 고르기·접기·닫기·모두 닫기·이웃 탭 빌리기와
  돌려주기·전체 화면 영상·렌더러). `select()` 도 초점을 먼저 거둔다(새 탭을 치려고 연 길은 고른 뒤에 초점을 다시 준다). 아래 함정 '초점을 쥔
  뷰를 떼면 주소창이 초점을 받는다'.
- **막대의 몸짓(`ToolbarGestureLayout`)**(위 사용자 요청 — 크롬의 판정 + 파이어폭스의 마무리). 막대를 감싼 레이아웃이 **방향이 정해지는
  순간에만** 몸짓을 가져온다(아이에게는 취소가 가 주소창이 초점을 받지 않고 단추가 눌리지 않는다). 한 번 정하면 바꾸지 않는다. 좌표는 화면
  좌표(`rawX`). 주소창을 고치는 중·찾기 줄·전체 화면 영상·앞 몸짓의 마무리 중·프록시 스위치 위에서 시작한 것은 받지 않는다.
  - **좌우로 밀기 — 앞·뒤 탭.** 슬롭을 넘고 가로축 ±25° 안이면 시작. 왼쪽이면 다음 탭(탭 목록의 차례 — 끝에서 처음으로 돌지 않는다). 지금
    쪽이 손가락을 따라가고 30 dp 틈을 두고 이웃이 들어온다 — 이웃의 WebView 가 살아 있으면 잠시 빌려 실제 쪽을(빌린 동안 `onResume`), 접혔거나
    아직 열지 않은 탭이면 제목·호스트를. 손을 떼면 최소 튕김 속도를 넘었으면 민 방향일 때만 넘기고 반대면 되돌리고, 아니면 이웃이 폭의 25% 넘게
    보였을 때 넘긴다. 넘기기 250 ms(`linear_out_slow_in`)·되돌리기 200 ms·반대 튕김 150 ms. 끝에서는 폭의 20% 까지 줄어드는 비율로만(`tanh`).
    새 탭을 열지 않는다.
  - **아래로 당기기 — 탭 목록.** 2×슬롭(약 16 dp)을 넘고 세로축 ±25° 안이면 **곧바로** 탭 목록(지금 탭이 보이게 내려 둔다). 막대는 상태 표시줄
    아래라 알림 창을 내리는 시스템 몸짓과 겹치지 않는다.
  - 조사가 권했지만 하지 않은 것(사용자가 정할 것): 두 몸짓을 끄는 설정, 처음 쓸 때의 안내, 탭 단추를 길게 눌러 '탭 닫기·새 탭', 탭을 떠날 때
    떠 두는 그림(탭마다 수 MB), 채운 글을 배경색 + 숨긴 커서로 그리기, 방문마다의 시각 표. 막대를 아래로 옮기지 않았다(몸짓이 '위로 밀기' 가
    되어 시스템의 홈 몸짓과 부딪친다).
  - 확인하지 못한 것: 실기기, 가장자리에서 시작한 밀기와 시스템의 뒤로 몸짓이 겹치는 자리, 탭이 수십 개일 때의 탭 목록.

### 북마크·방문 기록·시작 화면·쪽에서 찾기

- **저장(`Library`).** 플랫폼 SQLite `browser.db`(새 의존 없이)의 표 둘 — `bookmarks(url, title, created)`, `history(url, title, last_visit, visits,
  typed)`(주소마다 한 줄, 최근 10,000곳 — 100번 적을 때마다 넘친 것을 지운다). 디스크 일은 일꾼 스레드 하나가 차례로 한다(쪽의 제목이 다 열기
  전에 와도 차례가 지켜진다). 백업·이전에서 빠진다(`data_extraction_rules`) — 앱 안에서 옮기는 길도 없다(걷었다).
- **방문 기록.** 주 문서를 다 열면(http·https) 적는다. **중계가 막은 쪽(`X-Iroiro-Relay` 머리가 달린 502 — 정하지 않음·불통·계정 거절)과 연결
  오류 쪽은 적지 않는다**(`Tab.failed`). 새 쪽이 시작하면 앞 쪽의 제목을 지운다. **SPA 이동도 적는다**(`NavigationListener` →
  `PageHost.onSameDocumentNavigation` — 새로 고침·조각만·다 열기 전의 이동은 빼고). 내려받기로 끝난 탐색은 커밋되지 않는다 — 탭이 '커밋된
  주소' 를 따로 들고 Referer·주소창을 그것으로 한다.
- **보관 기간.** 설정 › 인터넷 사용 기록 — **방문 기록 보관**(기간 없음(기본 — 최근 1만 곳)·1년·90일·30일·7일·1일 — 마지막으로 간 때가 그보다 오래된
  주소를 지운다, 북마크는 그대로)과 **캐시 비우기**(하지 않음(기본 — WebView 가 용량으로만 다룬다)·30일마다·7일마다·매일 — WebView 의 HTTP 캐시를 통째로
  비운다, 쿠키·사이트 저장소·로그인은 그대로). 판단은 `core/Retention`, 거는 것은 브라우저 화면으로 돌아올 때마다(`BrowserActivity.applyRetention` —
  SQL 하나·시각 견주기 하나, 지난 기록은 `Library.expireHistory`). 캐시는 주기를 켜는 순간부터 센다(켜자마자 비우지 않는다 — `SettingsStore.cacheDays`
  가 비운 때를 적는다), '쿠키·사이트 데이터·캐시 지우기' 도 그때를 적는다. **쿠키·사이트 저장소는 기간으로 지우지 않는다** — 로그인이 풀린다('사용자가
  정할 것').
- **화면(`LibraryActivity`).** 메뉴의 '북마크에 추가'/'북마크에서 빼기'(지금 쪽이 북마크인지는 메모리의 집합으로 바로 안다), '북마크'·'방문 기록'(한 화면 —
  위의 두 단추로 바꾸고 '선택됨' 상태를 둔다). 제목·주소로 찾기, 누르면 지금 탭에서 열기, 길게 누르면 새 탭에서 열기·링크 복사·이름 바꾸기·지우기,
  기록은 '모두 지우기'. **고른 주소는 결과(`setResult`)로만 돌려준다** — 브라우저 화면은 런처로 열리는(내보낸) 화면이라 인텐트의 덧붙임으로
  주소를 받으면 다른 앱이 이 브라우저에 아무 주소나 열게 할 수 있다.
- **시작 화면(`StartPage`).** 주소가 없는 탭에는 북마크(최근 30)와 자주 간 곳(8)을 뷰로 보인다 — 앱 안의 쪽 파일을 WebView 로 열지 않는다(파일
  접근은 꺼 두었다).
- **쪽에서 찾기.** 막대 아래 찾기 줄(`findAllAsync`·`findNext`·`setFindListener`). 탭을 바꾸거나 뒤로 가면 닫고 표시를 지운다. 설정의 '인터넷 사용
  기록' 에 '방문 기록 지우기'(북마크·쿠키는 그대로).

### 사이트별 데이터·팝업

- **사이트별 데이터**(익명 탭 대신 — 사용자). 설정 → '사이트별 데이터' 에서 사이트를 누르면 그 사이트의 쿠키·저장소·캐시를 지운다
  (`WebStorageCompat.deleteBrowsingDataForSite`). WebView 는 **등록 도메인 단위**로 지우고(`www.example.com` → `example.com` 전체) 지운 도메인을
  돌려준다 — 그 아래 이름을 목록에서 함께 뺀다. 목록은 이 브라우저로 연 사이트(`SiteList` — 주 문서의 호스트, 최근 1000곳, 설정 파일 `sites`)와
  저장소를 쓴 출처(`WebStorage.getOrigins`)를 합친 것이다 — WebView 에는 쿠키가 있는 곳을 늘어놓는 API 가 없어 연 사이트를 따로 적는다(서드파티
  쿠키는 막혀 있어 쿠키는 연 사이트에만 생긴다). 찾기 칸에 목록에 없는 이름을 넣어도 지운다. '쿠키·사이트 데이터·캐시 지우기'(모두)는 연 사이트
  목록도 함께 지운다.
- **익명 탭을 버리며 알게 된 것**(다시 검토할 때). WebView 의 프로필은 디스크에 있고(메모리 전용 프로필이 없다) **한 번 메모리에 올린 프로필은
  같은 프로세스에서 지울 수 없다**(`ProfileStore.deleteProfile` → IllegalStateException — 1.17.1 의 문서). 익명 탭을 만든다면 닫을 때 데이터를
  비우고 빈 프로필은 다음 실행 때 지우는 길밖에 없다. 인증 캐시도 프로필마다라 그 프로필도 데워야 한다.
- **팝업.** `javaScriptCanOpenWindowsAutomatically` 는 참으로 두고 앱이 `onCreateWindow` 의 `isUserGesture` 로 막는다(거짓이면 크로미움이 **소리
  없이** 막아 앱이 알 수 없다). 막으면 '팝업을 막았습니다 — 사이트 · 이 사이트 허용'. 허용은 등록 도메인 단위(`PopupSites`, 설정의 '팝업' 목록,
  메뉴 '이 사이트의 팝업 다시 막기').

### 광고 차단

**엔진(`:core` 의 `adblock` — 순수 JVM).** 서드파티 엔진은 쓰지 않는다(성숙한 JVM 라이브러리가 없고 GPL 코드는 이 저장소(MIT)와 맞지 않는다).
ABP·uBO·AdGuard 의 공통 문법과 hosts 꼴을 읽는다.

- **찾기**: `||호스트^` 꼴은 **호스트 표**(요청 호스트와 그 상위 이름만 본다 — 목록 규칙의 95%), 나머지는 **낱말 색인**(무늬 안에서 앞뒤가 확실히
  끊긴 가장 긴 글자·숫자 묶음), 낱말이 없는 것과 정규식은 늘 본다. 막는 규칙은 `$important` 를 먼저, 예외(`@@`)가 맞으면 막지 않는다. `$badfilter`
  가 가리킨 규칙은 지을 때 뺀다. 쪽 단위 예외(`$document`·`$elemhide`·`$generichide`)는 주 문서 때 한 번 정한다.
- **무늬**(`UrlPattern`): 정규식으로 바꾸지 않고 `||`·`|`·`^`·`*` 를 직접 맞춘다. **`||host/path` 는 포트가 붙은 주소에 맞지 않는다**(ABP 그대로).
- **선택지**: 종류(`~` 부정 포함)·`$third-party`·`$1p`·`$domain=`·`$important`·`$match-case`·`$all`·`$redirect`(막기만)·예외의 쪽 단위 셋. **쓰지
  못하는 규칙은 건너뛴다** — 응답 고치기(`$csp`·`$replace`·`$removeparam`·`$redirect-rule` 만), 주 문서만 막기(막은 쪽을 보일 화면이 없다),
  팝업, 스크립트 끼우기(`##+js`·`#%#`·`#$#`), HTML 거르기(`##^`), 절차 선택자(`:has-text` 등).
- **요청의 종류**: WebView 가 알려 주지 않는다 — `Accept` 로 문서(틀)·CSS·그림만 확실히 갈리고, 나머지는 확장자로, 그래도 모르면 스크립트·XHR·
  글꼴·미디어·그 밖을 함께 친다(핑은 언제나 POST — GET 은 핑이 아니고 POST 는 XHR 이거나 핑, `guess` 가 메서드를 받는다). **막는 규칙은 어림한
  종류가 모두 자기 종류일 때만 걸리고**(확실할 때만 막는다) 예외는 하나만 겹쳐도 걸린다. 하나만 맞아도 막으면 EasyPrivacy 의
  `*$ping,third-party` 가 확장자 없는 모든 제3자 요청(속도 측정 서버, 유튜브의 `googlevideo.com/videoplayback`, 남의 도메인의 API)을 막고 빈 200 을
  돌려준다. 그래서 종류를 좁힌 막는 규칙은 종류를 모르는 요청을 막지 않는다(덜 막는 쪽 — 쪽이 깨지는 것보다 낫다). `RealListsBenchmark` 가 그 요청들을 GET·POST 로 통과시키는지 본다.
- **제1자/제3자**: 공개 접미사 목록 없이 흔한 두 단계 접미사(`co.kr` 등)와 호스팅 접미사(`github.io` 등)만 아는 어림(`Domains` — 팝업 허용과
  가로채기의 사이트 판정에도 쓴다).
- **요소 숨김**: 일반 규칙을 다 넣지 않는다(EasyList 만으로 1만 4천 줄·560 KB). 선택자에 클래스·id 가 있으면 **그 이름이 쪽에 있을 때만**
  넣고(다 연 뒤와 2.5초·7초 뒤에 쪽의 클래스·id 를 모은다 — `evaluateJavascript` 의 즉시 함수라 쪽에 전역 이름을 남기지 않는다), 이름 없는
  것(속성 선택자 등)과 그 사이트의 규칙만 쪽을 시작할 때 넣는다. 선택자마다 한 줄(잘못된 선택자 하나가 나머지를 망치지 않게). 주 문서에만.
- **실제 목록으로 잰 것**(`RealListsBenchmark` — `local/adblock-lists` 에 손으로 받아 둔 넷, PC 의 JVM): 네트워크 규칙 110,557개(호스트 표 95%), 숨김
  38,076개, 건너뜀 7,442개(대부분 uBO 의 스크립트 끼우기와 절차 선택자), 읽기 0.32초·짓기 0.08초, 요청 하나 7.5 µs.
- **시간 상한** — 규칙은 하위 요청마다 WebView 의 요청 스레드에서 돈다. 요청 하나를 맞추는 시간은 주소 길이에 비례해야 한다 — 쪽이 지은 아주 긴
  주소(WebView 는 2 MB 까지 넘긴다)나 사용자가 더한 목록의 한 줄이 그 스레드를 세우지 못하게.
  - **정규식 규칙은 `ListRegex`**(:core — 새 의존 없음). java.util.regex 는 되짚는 엔진이라 `(a+)+$` 같은 한 줄과 긴 주소가 만나면 지수 시간이다.
    '읽은 글자를 세는 `CharSequence` 감싸개' 는 **안드로이드에서 듣지 않고**(함정 '안드로이드의 java.util.regex 는 ICU 다'), JVM 에서도 글자를 읽지
    않고 도는 되짚기는 세지 못한다. 그래서 목록이 쓰는 문법만 받는 작은 엔진이다 — 톰슨 NFA 를 한 글자씩 굴려 되짚지 않는다(걸음 ≤ (주소 길이 + 1) ×
    명령 수 × 2). 받는 것은 글자·`.`·모둠·묶음·`|`·되풀이(`{n,m}` 은 펼친다, 게으른 꼴은 같게)·`^ $ \b \B`, 뜻은 java.util.regex 와 같다(무작위 무늬로
    견주는 시험). 받지 않는 꼴(앞뒤 보기·`\1`·소유 되풀이·`(?i)`·겹친 모둠·`\p{…}`·자바스크립트와 뜻이 다른 `[]…]`·`\v`, 빈 글에 맞을 수 있는 것의
    `{2,}`)은 건너뛴다 — 기본 목록 넷의 정규식은 모두 받고 같은 답이다. 무늬 글 1,024 자·명령 4,096 개·`{n,m}` 의 수 1,000 까지.
  - **걸음 예산** — 16,384 + 주소 한 글자에 128 걸음. 넘으면 그 요청에는 맞지 않은 것으로 치고(막는 규칙이면 막지 않고, 예외면 풀어 주지 않는다)
    **그 규칙을 끈다** — 엔진을 다시 지을 때(목록 갱신·켜고 끄기)까지. 디버그 기록에 `adblock regex off <규칙의 앞 48 자>`(주소는 싣지 않는다).
    목록 넷의 규칙은 일부러 지은 긴 주소에서도 한 글자에 65 걸음이 가장 많다 — 그 두 배를 주어 쪽이 긴 주소로 목록의 규칙을 끄지 못하게 했다.
  - 쪽이 지은 긴 주소로 제곱 시간이 되지 않게 — 무늬(`UrlPattern`)는 첫 조각이 맞는 **가장 앞의 자리 하나로 족하다**(뒤의 조각은 앞에서 맞출수록 남은
    자리가 넓다 — 조각 하나에 끝의 `|` 만 예외), 낱말 색인은 **같은 낱말을 한 번만** 담고, 호스트 표는 **253 자(DNS 이름의 상한)보다 긴 상위 이름은
    늘어놓지 않는다**. 1 MB 주소 하나를 세 종류로 맞추는 시간은 `RealListsBenchmark` 가 잰다.

**목록(`AdBlockService`).** **실행 중에 내려받아 이 기기에서 짓는다** — 저장소에도 APK 에도 넣지 않는다. 받기는 앱의 HTTP 로(프록시를 켰으면
프록시를 거친다). **앱을 켤 때 받지 않는다** — 하루 한 번 깨어(`AdBlockUpdateJob`, JobScheduler) 만료(목록의 `! Expires:` — 하루~일주일로 묶는다)가
지난 것만 `If-None-Match`·`If-Modified-Since` 로 묻는다. 처음 켠 목록·'지금 갱신'·아직 못 받은 목록(길이 정상이 되면 — 처음 깔았을 때는 프록시를
정하기 전이라 실패한다)만 바로 받는다. 받은 것이 HTML 이면 목록으로 쓰지 않는다. 넷 모두 기본으로 켠다(사용자 결정 — 앞서 깔린 앱은 `defaults_v2` 로 한 번 켠다). List-KR·filterslists-KO 는 주소를 확인하지 못해 넣지 않았다(설정에서 주소로 더한다).
설정 화면 — 목록마다 규칙 수·받은 때·실패, 지금 갱신, 'Wi-Fi 에서만 자동 갱신'(기본 꺼짐), 목록 더하기·빼기, 광고 차단을 끈 사이트, 목록의 출처와
라이선스, 숨긴 요소. **사이트별 끄기** — 메뉴 '이 사이트에서 광고 차단 끄기(이 페이지 N개 막음)', 등록 도메인 단위로 끄고 쪽을 다시 연다.

**막지 못하는 것.** 넘겨주기 뒤의 주소(가로채기가 불리지 않는 경우), 웹소켓·WebRTC, 응답을 고치는 규칙, 본편과 같은 곳에서 오는 영상 광고의 바이트
(유튜브의 googlevideo.com — 모바일 웹이 광고를 따로 된 물음으로 받으면 목록이 그것을 막지만 늘 그렇지는 않다, 아래 '유튜브 광고 검토'), 틀(iframe) 안의 요소 숨김, 늦게 붙는 광고의 일반 숨김(7초 뒤까지만 모은다). 목록을 비켜 온
유튜브 광고는 '유튜브 광고 가리기(B1)·건너뛰기(B2)' 가 가리고 소리를 끄고, 건너뛸 수 있게 되면 누른다.

### 요소 숨기기

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 들어가는 길 둘 — 메뉴 '요소 숨기기'(광고 차단을 꺼도 보인다), 고리·그림을 길게 눌러 '이 요소 숨기기'(손가락이 닿은 자리에서 시작 — `PageWebView.lastDownX/Y`) | 글·빈 곳을 길게 누르면 WebView 의 글 고르기가 된다 — 그 자리는 메뉴로 들어간다 |
| 고르는 동안 플랫폼 뷰 덮개(`PickOverlay`)가 손가락을 모두 받는다 | 페이지에 누름·고리가 가지 않는다. 끌거나 튕기면 그 자리의 스크롤 상자(없으면 문서)를 스크립트의 `scrollBy` 로 민다 |
| 고르는 코드는 BRIDGE 안에 잠들어 있다가 `pick-start` 로만 깬다 | 지을 때는 열린 페이지의 실행 세계에 그때그때 스크립트를 넣는 길이 없다고 보았다(문서 시작·끝뿐) — 지금은 1.16 부터 `JavaScriptReplyProxy.executeJavaScript`(그 프록시를 보낸 틀·세계에서 돈다)가 있다(아래 '더할 만한 기능 검토'). 고르는 코드는 아직 그 길로 옮기지 않았다. 페이지의 DOM 은 바꾸지 않고 테두리는 앱이 그린다. 좌표는 보이는 영역(visual viewport)에 대한 비율 |
| 아래 막대 — 요소 설명(`div#top.ad-slot · 320×250`)·넓게(body 바로 아래까지)·좁게·취소·숨기기 | 끝나는 때: 뒤로·탭 바꾸기·막대 몸짓·새로 고침·주소창·찾기·전체 화면·메뉴·길 바뀜, 페이지가 스스로 바뀌어도(그때는 알린다) |
| 선택자 짓기(`core` 의 `HideSelectors`, 순수 함수) — id → 시험용 속성(`data-testid` …) → 클래스 → 이름 있는 조상 아래의 그 이름 → 이름 있는 가까운 조상에서의 경로(`>`·`:nth-of-type`) → `body` 에서의 경로 | 버리는 이름: 해시처럼 보이는 무작위 이름(CSS-in-JS·원자 CSS·세션 번호), 상태 클래스(`active`·`is-open`), 꼴 클래스(`row`·`mt-3`·`card` …). 스크립트가 지금 페이지에서 후보마다 맞은 수를 재고 **고른 요소 하나만** 맞는 첫 후보를 쓴다 |
| 이스케이프 — `CSS.escape` 규칙에 `{` `}` `;`·공백·제어 글자는 16진(`\7b `) | 페이지가 정한 이름(`x{}body{display:none}`)이 다른 것을 숨기는 규칙이 되지 못하게. 저장 파일을 읽을 때도 같은 검사(`isSafeSelector`) |
| `html`·`body` 는 고를 수 없고, 보이는 영역의 70% 이상을 덮으면 한 번 더 묻는다 | 쿠키 창처럼 화면을 덮는 것은 사람들이 실제로 숨기고 싶어 해 막지 않았다 |
| 숨기면 지금 페이지에 `styleScript` 로 곧바로 넣고 '숨겼습니다 — 사이트 · 되돌리기' | 되돌리기는 그 규칙의 `<style>` 만 걷는다. 페이지를 시작할 때(`adStart`) 목록 CSS 와 사용자 CSS 를 한 `<style>` 로 |
| 관리 — 광고 차단 설정의 '숨긴 요소'(사이트마다 '모두 지우기', 규칙을 누르면 지울지 묻는다), 메뉴의 '이 사이트에서 숨긴 요소 되돌리기(N개)'(규칙이 있을 때만 — 지우고 다시 열고 '다시 숨기기') | 사용자 규칙이 페이지를 깨뜨려도 길을 찾을 수 있게 |
| 광고 차단과의 관계 — **늘 건다**(광고 차단 전체를 꺼도, 그 사이트에서만 꺼도) | 사이트별 끄기는 목록이 사이트를 깨뜨릴 때의 탈출구이고, 사용자 규칙은 직접 고른 것이라 성격이 다르다 |
| 접근성 — 막대 단추마다 설명, 요소 설명 글과 숨긴 뒤의 알림은 live region, 덮개를 낭독기로 활성화하면 가운데 요소 | |

시험: JVM(`HideSelectors`·`UserHides`·`PickProtocol`), 고르는 스크립트는 PC 의 node 24 로 따로(시험대는 스크래치패드에만). **한계** — 틀(iframe) 안의
요소는 고르지 못하고(틀째는 된다), shadow DOM 안은 그 호스트가 골라지며, 같은 칸 여럿을 한 번에 숨기지 않고, 고르는 중에는 두 손가락 확대가 안 된다.
**확인하지 못한 것** — 확대한 페이지·데스크톱 꼴에서 테두리가 요소에 꼭 맞는가(좌표의 전제), 같은 사이트의 다른 탭·`www.`·`m.` 이름, 광고 차단을 끈 채,
끝나는 때들, TalkBack.

### 유튜브 광고 가리기(B1)·건너뛰기(B2)

기본 광고 목록이 모바일 웹의 광고 물음(`ad_break`·`get_midroll_info`)을 막아도 플레이어 응답에 실린 광고는 온다(아래 '유튜브 광고 검토'). 그런 광고를
B1 이 가리고 소리를 끄고, 건너뛸 수 있게 되면 B2 가 누른다. 끝으로 감기(B3)는 넣지 않았다 — 건너뛸 수 없는 광고는 가리고 끈 채 끝까지 간다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 유튜브 출처(youtube.com·youtube-nocookie.com 과 그 아래 이름, https·기본 포트)에만, 통로와 **같은 실행 세계**에 문서 시작 스크립트(`YtGuard.SCRIPT`)를 통로 뒤에 건다 | 쪽의 세계를 건드리지 않는다(쪽의 세계는 사용자가 고른 탭·고른 동안만). 같은 세계라 통로의 주입 객체로 앱에 묻는다. androidx.webkit 의 별표 규칙은 그 아래 이름만 덮어 이름 자체도 함께 적는다. 실행 세계를 모르는 WebView 면 걸지 않는다(쪽의 세계로 물러서지 않는다 — B1 없음) |
| 켤지는 앱이 답한다 — 틀이 `yt-hello` 로 묻고 앱이 `yt-guard`(`on`·`label`)로 답한다. 조건은 광고 차단이 켜져 있고 **주 문서의 사이트**에서 끄지 않았을 때(`YtGuard.wanted`). 광고 목록은 보지 않는다(받기 전·모두 끈 때도 켠다). 앱은 출처를 같은 규칙으로 한 번 더 본다 | 문서 시작 스크립트는 상태를 모른다. 퍼가기 틀도 주 문서를 따른다 — 요청 막기와 메뉴의 사이트별 끄기가 주소창의 사이트를 따르듯이. 사이트별 끄기는 쪽을 다시 열어 새로 묻게 하고, 광고 차단 설정에서 돌아오면 이미 연 유튜브 틀에 다시 알린다(`refreshYtGuard` — 쪽을 다시 열지 않는다) |
| 알아채기 — 재생기(`.html5-video-player`)의 `ad-showing` 을 MutationObserver(그 재생기의 class 만)로, 미디어 이벤트(포착 단계)로 재생기를 찾고, 1초마다 한 번 더(늦게 생긴 재생기·떨어진 재생기·쪽이 걷어 낸 style) | 광고는 본편과 **같은 `<video>`** 로 재생되고 광고 동안만 그 클래스가 붙는다. 문서 전체의 class 변화는 보지 않는다(유튜브는 클래스를 자주 바꾼다) |
| 가리기 — 광고가 있는 동안만 `<style>` 하나. 광고 중인 재생기 안의 `video`·`.video-ads`·`.ytp-ad-module`·`.ytp-ad-player-overlay`(`-layout`·`-instream-info`)·`.ytp-ad-pod-index--mweb`·`.ytp-ad-duration-remaining--mweb`·`.ytp-ad-visit-advertiser-button`·`.ytp-ad-text`·`.ytp-ad-image-overlay` 를 `visibility:hidden`, 건너뛰기 칸(`.ytp-ad-skip-ad-slot`·`.ytp-ad-skip-button-slot`·`.ytp-ad-skip-button-container`·`.ytp-skip-ad`)은 `visibility:visible`. 가운데에 '광고 가리는 중'(재생기의 `::after`, `pointer-events:none`) | **opacity 를 쓰지 않는다** — 덮개와 영상에 걸자 쌓임 순서가 바뀌어 건너뛰기 단추 자리의 맨 위가 `<video>` 가 되었다(에뮬레이터, `elementFromPoint` — 진짜 탭이 단추에 닿지 않는다). visibility 는 쌓임을 바꾸지 않고, 가린 것은 누름을 받지 않으며, 가린 것 안의 칸만 되살릴 수 있다. 칸의 단추 자체는 고르지 않는다(유튜브가 5초 전에 단추를 숨기는 방식을 그대로 둔다). 표시 글은 앱의 자원(`strings_ytguard.xml`)이고, CSS 글자열을 깨는 글자(제어·따옴표·역슬래시·꺾쇠·중괄호·쌍반점)는 스크립트가 빼고 40자로 줄인다 |
| 소리 — 광고 미디어마다 한 번 정한다: 켜져 있으면 끄고 '우리 것' 으로 적는다. 광고 중에 소리가 다시 켜지면, **그 광고 미디어가 시작된 뒤** 1.5초 안에 사람이 만졌으면(신뢰된 `pointerdown`·`touchstart`·`mousedown`·`keydown`) 그 뜻을 따르고, 아니면 쪽이 한 것이라 다시 끈다(광고 미디어마다 여덟 번까지 — 그 뒤는 쪽의 뜻). 광고가 끝나면 **우리가 끈 것만** 되돌린다 | 광고 전에 이미 음소거였던 것은 손대지 않는다. 건너뛰기를 누른 직후 묶음의 다음 광고에서 쪽이 제 소리 상태를 다시 거는 것을 사람의 뜻으로 읽지 않게 '그 광고 미디어가 시작된 뒤' 의 누름만 센다(새 미디어 = `loadstart`·`emptied`). 쪽과 끝없이 다투지 않게 횟수를 묶었다 |
| 원래대로 — 꺼짐 알림, 문서가 떠날 때(`pagehide` — 뒤로 가기 캐시에서 돌아오면 다시 묻는다), 재생기·영상이 문서에서 떨어질 때. SPA 이동은 클래스·연결 상태를 따른다 | 흔적을 남기지 않는다 — 광고가 없으면 `<style>` 도 없다 |
| **건너뛰기(B2)** — 광고 중인 재생기의 건너뛰기 단추(`.ytp-ad-skip-button-modern`, 옛 이름 `.ytp-skip-ad-button`·`.ytp-ad-skip-button` 도)가 크기를 얻고 그 가운데의 맨 위(`elementFromPoint`)가 단추(나 그 안)이면 그 자리를 보이는 영역에 대한 비율로 앱에 알린다(`yt-skip`). 2초에 한 번, 광고 미디어마다(새 미디어·새 광고 시간이면 처음부터) 다섯 번까지. 보이는 영역 밖이면 알리지 않는다 | 건너뛸 수 있게 되기 전에는 단추의 칸이 `display:none` 이라 크기가 0 이다 — 광고 5초 뒤 크기가 생긴다. 숨은 것은 맨 위가 될 수 없다. 누름이 듣지 않을 때를 위해 다시 청하되 끝없이 누르지 않는다 |
| 앱이 그 자리를 **진짜 터치**로 누른다 — 손가락 하나의 누름·뗌(`PageWebView.tap` — 뗌의 시각을 60 ms 뒤로 적어 곧바로 잇달아 넣는다), 비율을 그 뷰의 픽셀로(`YtGuard.tapPoint`), 전체 화면 영상이면 그 화면에. 주 틀의 것만·켜져 있을 때만(`ytGuardWanted`)·1초에 한 번까지. 진짜 손가락이 WebView 에 닿아 있으면 넣지 않는다(스크립트가 다시 청한다) | 스크립트의 `click()` 은 신뢰되지 않은 사건이라 쪽이 가려낼 수 있다 — 앱이 WebView 에 넣은 누름은 크로미움에 사람의 누름과 같다(isTrusted). 앱이 뒤에 있어도·화면이 꺼져도·쪽의 자리에 투명하게 남은 탭([backstaged] — 막는 `dispatchTouchEvent` 를 비킨다)에도 넣는다. 자리가 주 틀의 보이는 영역에 대한 비율이라 퍼가기 틀은 받지 않는다 |
| 알린 뒤 **1.5초** 동안 단추 밖에 떨어진 신뢰된 `click` 이 재생기 안이나 고리(`a[href]`)면 막는다(`preventDefault`·`stopImmediatePropagation` — 문서 시작에 건 창의 포착 듣개라 쪽의 것보다 먼저 돈다). 단추를 누른 것은 그대로 두고 막기를 푼다 | 그 사이 쪽이 바뀌어 다른 것(광고주 방문)이 눌리지 않게. 광고 덮개는 B1 이 가려(visibility) 애초에 누를 수 없다. 그 사이 사람이 재생기·고리를 누르면 한 번 먹힌다(1.5초 — 감수) |
| 광고가 나오고 끝나는 것을 앱에 알린다(`yt-ad` — 주 틀의 것만, 쪽이 바뀌면 잊는다) | 뒤에서 재생 잇기는 '소리가 나는 재생' 만 잇는데 B1 이 광고를 음소거한다 — 광고 중에 앱이 뒤로 가도 잇게(`BackgroundPlay.wanted` 의 `adShowing`). 광고는 사람이 소리와 함께 튼 재생에만 붙는다(검토 — 소리를 끈 저절로 재생은 광고를 부르지 않는다) |
| 하지 않는 것 — 배속·감기(B3), 스크립트의 누르기(`click()`·사건 만들기), 쪽의 함수·전역 감싸기, 응답에서 광고 지우기(A), 네트워크. 스크립트에 역슬래시·달러 기호를 쓰지 않는다 | 사용자 결정(B1, 그날 밤 B2). 시험이 스크립트의 글에 그런 것이 없는지 본다. 역슬래시·달러는 코틀린의 날 글자열(달러는 글자열 틀)·셸이 먹는다 — 걸러 낼 글자는 번호로 가린다 |

**화면 켜 두기와 얽힘.** 광고를 음소거한 동안은 '소리 나는 재생' 이 아니어서 `ScreenWake` 가 화면 켜 두기를 놓는다(전체 화면 영상이면 그대로
켜 둔다). 화면 꺼짐 시간보다 긴 광고면 광고 중에 화면이 꺼질 수 있다 — 광고는 짧아 그대로 두었다. 광고가 끝나 소리가 돌아오면 다시 켜 둔다.

**시험.** JVM `YtGuardTest`(출처 규칙이 androidx.webkit 의 규칙을 옮긴 대조와 같은가·켜질 조건·약속 이름·visibility 만·넣지 않기로 한 것·역슬래시와
달러·표시 글·같은 세계와 유튜브 규칙과 통로 뒤·화면의 연결, 그리고 B2 — 건너뛸 수 있게 된 단추만 알리기·누를 자리의 셈·주 틀의 것만 켜져 있을 때
진짜 터치로). 스크립트는 PC 의 node 24 로 작은 DOM 흉내 위에서 따로 시험한다(시험대는 스크래치패드에만). 에뮬레이터에서 실제 광고로 앱이 앞·뒤(홈)·
화면 꺼짐·다른 탭을 볼 때 모두 건너뛰었다(유튜브는 사용자의 프록시(미국 출구)에서 봇 확인을 내서 프록시를 끄고 PC 회선으로 봤다).
**확인하지 못한 것** — 전체 화면 영상의 광고(그 화면에 누름을 넣는다), 건너뛸 수 없는 광고 뒤의 묶음, 유튜브가 단추의 클래스를 바꾸면(알리지 못해
B1 만 남는다), 사용자의 프록시(미국 출구)에서의 실제 광고, 데스크톱 사이트(www)의 클래스, 표시가 재생기 가운데에 오는지(재생기가 위치 지정이라고
보았다), 퍼가기·music.youtube.com·쇼츠(따로 된 광고 칸이라 걸리지 않을 것 — 추측), 묶음 사이에 `ad-showing` 이 잠깐 빠지는지, 클래스보다 소리가
먼저 나는지, 유튜브가 가리기·음소거를 알아채는지, 1.5초 창이 실제 음소거 해제에 넉넉한지, TalkBack 이 표시 글을 읽는지.

### 뒤에서 재생 잇기

안드로이드의 크로미움은 숨은 쪽의 영상을 멈춘다(함정 '숨은 쪽의 영상') — 앱이 뒤로 갈 때 지금 탭에 `WebView.onPause()` 를 부르거나 탭을 바꿀 때 떠나는
탭의 WebView 를 떼면 쪽이 '숨김' 이 되고, 유튜브는 멈춘 채 둔다. 아래가 그것을 피하는 방법이다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 잇는 것 — 주 문서가 **유튜브**(https·기본 포트 — 광고 가리기와 같은 출처 규칙 `YtGuard.isYouTubeOrigin`, music.youtube.com 포함)이고 **소리가 나는** 미디어가 재생 중일 때(`BackgroundPlay.wanted` — 재생 상태는 화면 켜 두기와 같은 통로의 알림 `PlayingMedia`). 광고가 나오는 중이면(B1 이 음소거했다 — `yt-ad`) 영상이 재생 중인 것으로 족하다 | 사용자의 말 그대로 유튜브다. 음소거로 저절로 도는 영상은 듣는 것이 아니다. 다른 사이트로 넓히려면 그 함수 하나 |
| **회**(session) — 잇는 탭은 하나. 앱이 뒤로 가는 순간(`onPause`)의 지금 탭, 또는 다른 탭을 고르는 순간 떠나는 탭이 위 조건이면 연다. 새 회가 열리면 앞 회의 탭은 멈추고 여느 탭으로 돌아간다 | 소리 둘이 겹치지 않게. 회에 든 탭은 소리가 없어도(B1 이 음소거한 광고) 재생 중이면 잇는다 |
| 앱이 뒤로 갈 때 — 그 탭에 `onPause` 를 부르지 않고 **창이 보인다고 알린다**(`PageWebView.keepWindowVisible` — `onWindowVisibilityChanged` 에 VISIBLE 을 넘긴다). 실제 창이 VISIBLE 을 알리면 저절로 풀린다 | 쪽의 보임은 WebView 가 창에 붙었는가·뷰가 보이는가·창이 보이는가·`onPause` 가 아닌가로 정해진다(크로미움 `AwContents` — 소스로 읽은 것). 아래 함정 표 |
| 다른 탭으로 갈 때 — 떠나는 탭을 **떼지 않는다**. 쪽의 자리에 투명하게(alpha 0) 남기고 누름·초점·접근성을 막고(`PageWebView.backstaged`), 새 지금 탭은 그 위에 놓는다(`pageIndex`). 돌아오면 그 자리에서 다시 보인다(새로 그리기를 청한다 — 투명도만 바꾸면 그려 둔 것을 다시 쓸 뿐이다). 회의 탭은 이웃 탭 보기에 빌려 가지 않고 접지 않는다 | 떼었다 붙이면 쪽이 숨어 재생이 멈췄다(에뮬레이터). `INVISIBLE`·`GONE` 도 보임을 바꾼다 |
| **그려지지 않는 동안 화면 밖에 그린다**(`OffscreenDraw`, `PageWebView.drawOffscreen`). 회의 탭이 재생 중이고 앱이 뒤에 있거나(`onPause` 부터 — `onStop` 은 0.7초 늦어 그 사이 디코더가 막혔다) 다른 탭을 보는 동안, 하드웨어 캔버스(`Surface.lockHardwareCanvas` — `ImageReader` 의 표면, 그림은 곧바로 버린다)에 `View.draw` 로 그린다 — **WebView 가 다시 그리기를 청할 때만**: `PageWebView` 가 크로미움(`AwContents`)이 청하는 두 길(`invalidate`·`postInvalidateOnAnimation`)을 받아 알리고(`OffscreenDraw.invalidated`), 그리기는 그다음 vsync(`Choreographer`)에. 청이 0.5초(`OffscreenDraw.FALLBACK_MS`) 넘게 끊기면 vsync 마다 그린다(청을 받는 길을 놓쳤을 때의 물러서기). 멈추거나 돌아오거나 떼거나 없애면 그친다. 잇달아 120번 실패하면 그만둔다 | 위 둘은 크로미움이 쪽을 '보임' 으로 두게 할 뿐 **아무도 그리지 않는다** — 그리지 않은 영상 프레임은 돌아오지 않아 디코더의 출력이 막히고, 영상 파이프라인이 서면 소리도 함께 선다(`DECODER_UNDERFLOW` → 파이프라인 `HAVE_NOTHING` — 사용자 기기에서는 소리가 끊긴 뒤 유튜브가 144p 로 다시 세웠다). 화면 밖에 그리면 WebView 의 그리기(draw functor)가 렌더 스레드에서 돌아 프레임이 소비된다. 화면이 꺼져도 Choreographer 는 돈다. 청할 때만 그리면 화면에 보일 때와 같은 횟수다(30 fps 영상이면 초당 30번) — vsync 마다면 그 두 배라 뒤의 CPU 가 앞보다 크다. 고정된 낮은 빈도로 그리지 않는 것은 '보임' 인 동안 못 그린 영상 프레임이 버린 프레임으로 세어지고 유튜브가 그것으로 화질을 낮추기 때문이다(아래 '브레이브와 견줌' 의 상한) |
| **뒤에서는 영상 요소를 숨긴다**(`PageBridge.hideVideos`·`BackgroundPlay.HIDE_VIDEO_SCRIPT`). 화면 밖에 그리기 시작할 때 그 탭의 틀마다 **실행 세계**에서(`JavaScriptReplyProxy.executeJavaScript` — 글 고르기의 '검색' 과 같은 길) `<style>`(프로세스마다 무작위 id — `video{visibility:hidden!important}`)을 넣고, 화면 밖 그리기가 그치면(앞으로 돌아옴·그 탭을 고름·멈춤) 걷는다. 영상이 있는 틀에서 숨긴 것이 확인되고 아직 화면 밖에 그리는 중이면 청이 있을 때만 그린다(`PageWebView.offscreenRequestsOnly` — 물러서지 않는다) | 숨은 영상은 크로미움이 화면에 내지 않고(영상 층의 제출이 서고 프레임을 스스로 흘려보낸다 — 버린 프레임으로 세지 않는다) WebView 가 다시 그리기를 청하지 않는다. 화면 밖 그리기가 거의 서고 해독·소리만 남는다. 물러서기를 그대로 두면 청이 없다는 까닭으로 vsync 마다 그린다(함정 '숨은 영상과 물러서기'). 숨기기가 듣지 않으면(새 문서·통로 없음) 영상이 보여 청이 오므로 청할 때 그린다. 영상만 숨긴다 — 건너뛰기 단추(B2) 같은 것은 그대로. 쪽은 그 스타일 요소를 DOM 에서 볼 수 있다 |
| 재생 중인 쪽은 **떼었다 붙이지 않는다** — 같은 탭을 다시 고르면(탭 목록에서 지금 탭) 멈추거나 떼지 않고, 밤 모드가 바뀌어 화면을 다시 지어야 하면 회가 있거나 지금 탭에서 소리 나는 재생 중인 동안 미룬다(`holdsRebuild` — 재생이 바뀌거나 회가 닫히면 `maybeRebuild`) | 떼었다 붙이면 쪽이 잠깐 숨는데, 유튜브 모바일 웹은 `timeupdate` 마다 `visibilityState` 를 보고 숨었으면 재생기를 세운다(`stopVideo` — 다시 보이면 멈춘 채 다시 붙는다, 아래 '브레이브와 견줌'). 밤 모드가 '자동' 이면 시스템은 화면이 꺼진 동안 바꿔 두고 켜는 순간 알린다 — 그 순간 멈췄다 |
| **유튜브는 늘 '보임' 을 읽는다**(사용자 결정 — `BackgroundPlay.VISIBILITY_SCRIPT`). 유튜브 출처(`YtGuard.ORIGIN_RULES`)의 **주 틀**에만, **쪽의 세계**에 문서 시작 스크립트(`addDocumentStartJavaScript` — `WebViews.create`). 문서 객체의 `visibilityState`·`hidden`(크로미움에 있으면 접두어 붙은 둘도)을 '보임' 으로 덮고(열거되지 않는 접근자), `visibilitychange` 를 창의 포착 단계에서 멈춘다(쪽의 스크립트보다 먼저 건 듣개 — 창·문서의 쪽 듣개에 닿지 않는다). 쪽의 함수는 감싸지 않는다(브레이브의 탭 도우미는 `document.addEventListener` 를 바꿔 끼운다) | 위의 둘은 우리가 아는 숨김을 없앨 뿐이다 — 쪽이 잠깐이라도 숨으면(우리가 모르는 시스템의 창 전환·앞으로 생길 길) 유튜브가 재생기를 세운다. 이것이 있으면 크로미움은 숨은 동안 영상을 멈췄다가 보이면 저절로 다시 틀고(`paused_when_hidden`) 유튜브는 모른다. 앱의 실행 세계(통로·B1·B2)는 문서 객체가 따로라 실제 값을 읽고, 그 세계의 스크립트는 이 사건을 듣지 않는다. 숨은 채 60초 쉬면 360p 로 내리는 상한도 걸리지 않는다 |
| **알림·잠금 화면·이어폰 단추** — 포그라운드 서비스(`BackgroundPlayService`, `mediaPlayback`, 알림 5, 채널 '재생' — 중요도 낮음)와 플랫폼 `MediaSession`(꼬리표 `Web`). `MediaStyle` 알림에 제목·'유튜브'·재생/일시 정지·닫기, 누르면 브라우저. 알림은 회가 있고 **앱이 뒤에 있거나 그 탭을 보지 않을 때만**(`serviceWanted`) — 앞에서 그 탭을 보면 걷는다 | 서비스 없이 화면을 끄자 재생이 멈췄다(에뮬레이터). 서비스는 앱이 막 뒤로 가는 순간에 세운다 — 뒤에서는 포그라운드 서비스를 세우지 못한다(그 실패는 삼킨다). 내보내지 않는다 |
| 제목 — 실행 세계(BRIDGE)가 유튜브에서만 **화면의 제목 칸**(`.ytVideoMetadataViewModelTitle`)을 읽어 미디어 알림에 싣고, 앱이 다듬어 든다(`cleanText` — 제어 문자는 빈칸, 방향 제어 문자는 빼고, 200 코드 포인트). 없으면 쪽 제목에서 ' - YouTube' 를 뗀 것 | 유튜브 모바일 웹은 쪽 제목이 'YouTube' 뿐이고(본문에 둔 두 번째 `<title>`·og:title 은 처음 연 영상의 것으로 남는다), **WebView 에는 미디어 세션 API 가 없다**(함정 표). 재생 중인 틀의 것만 받고 바뀌면(다음 영상) 알림을 고친다. 유튜브가 그 클래스를 바꾸면 쪽 제목('YouTube')으로 물러선다 |
| 재생 중인 동안 부분 wake lock(`Web:background-play`, 10분 제한, 1분마다 다시 건다)과 Wi-Fi 잠금(`WIFI_MODE_FULL_HIGH_PERF`). 멈추면 놓는다 | 화면을 끈 채 망이 끊기지 않게. 단 14 부터 플랫폼이 HIGH_PERF 를 LOW_LATENCY 로 바꿔 끼고 그 잠금은 앱이 앞에 있고 화면이 켜진 동안만 듣는다(문서) — 뒤에서는 사실상 듣지 않는다(해가 없어 둔다) |
| 단추는 통로로 — 앱이 그 탭의 모든 틀에 `media-control` 을 보내고 실행 세계가 재생 중인 요소를 멈추며 기억했다가 다시 튼다(기억이 없으면 마지막으로 재생한 요소) | 쪽의 세계를 건드리지 않는다 |
| 멈춘 뒤 **10분**(`PAUSE_GRACE_MS`)은 회를 남긴다 — 알림·잠금 화면·이어폰으로 다시 튼다. 지나면 닫는다(그 탭은 여느 뒤의 탭 — `onPause`·떼기) | 멈춘 채 쪽을 '보임' 으로 두면 자원을 쓴다. Handler 의 시계는 기기가 잠든 동안 서므로 회가 조금 길어질 수 있다 |
| 회가 닫히는 때 — 알림의 '닫기'·잠금 화면·이어폰의 정지(미디어도 멈춘다), 그 탭이 유튜브를 떠남, 탭을 닫음, 렌더러가 죽음, 길이 바뀌어 탭을 접음(끔 → 켬), 앞에서 그 탭으로 돌아옴(알림만 걷힌다 — 재생은 그대로), 화면이 사라짐, 최근 앱에서 밀어 없앰(`stopWithTask`) | |
| 재생 중 화면 켜 두기와 따로다 | 화면 켜 두기(`ScreenWake`)는 앞의 창에만 — 뒤에서 잇는 동안은 화면을 켜 두지 않는다 |

**대가와 한계.** 쪽이 '보임' 이라 화면이 꺼진 동안에도 **영상까지 받고 풀고 화면 밖에 그린다** — 앞에서 보는 것과 거의 같은 일이다(영상을 숨겨 그리기를 거의 세운 뒤로는 앞보다 적다 — 아래 'CPU — 크롬·브레이브와 견줌'). 소리만 받는 것보다 데이터·배터리가 든다(브레이브는 쪽을 실제로 숨겨 크로미움이 10초 뒤 영상
트랙을 끈다 — 아래 '브레이브와 견줌'). 그리는 동안 뷰 크기의 표면 둘(1080×2400 이면 약 20 MB)을 쥐고, 그릴 때마다 로그에
`E FrameEvents: updateAcquireFence: Did not find frame.` 이 찍힌다(앱 안의 표면에 HWUI 로 그리면 늘 나온다 — 해는 없다). **광고와 얽힌다** — 뒤에서 광고가 나오면 B1 이 음소거하고, 건너뛸 수 있게 되면(대개 5초 뒤) B2 가 누른다 —
그 몇 초와 **건너뛸 수 없는 광고는 그 길이만큼 무음**이다(B3 는 넣지 않았다).

**시험.** `BackgroundPlayTest` — 켜질 조건·출처, 알림의 제목(꼬리 떼기·다듬기), 알림이 설 때, 10분, 매니페스트(mediaPlayback·내보내지 않음·
stopWithTask), 통로의 이름, 제목을 읽고 고치는 배선, 화면의 배선(뒤로 갈 때·탭을 떠날 때), 화면 밖에 그리는 배선(언제·떼거나 없앨 때 그치기·다시
보이면 새로 그리기), 같은 탭을 다시 고를 때와 밤 모드에서 떼지 않기, 보임 속이기(주 틀에서만·덮는 둘·포착 단계에서 멈추기·쪽의 함수를 감싸지
않기·역슬래시와 달러 없음·쪽의 세계에 유튜브 규칙으로), 화면 밖에는 청할 때만 그리기(두 길을 받는가·그리기 전에 청을 내리는가·처음 한 번·상위
생성자 안에서는 널)와 청이 끊기면 물러서기(`OffscreenDraw.due`), 뒤에서 영상 숨기기(화면 밖에 그리는 동안·그치면 되돌리기·숨긴 것이 확인되고 아직
그리는 중일 때만 물러서지 않기·실행 세계에서 무작위 이름으로). 보임 속이기 스크립트는 PC 의 node 24 로 작은 흉내 위에서 따로(시험대는 스크래치패드에만).
WebView 의 보임·그리기와 알림은 기기에서 본다 — 에뮬레이터에서는 홈·화면 끔·다른 탭·알림의 단추·미디어 키·정지(wake lock 을 놓는다)·다음 영상의 제목·
음소거로 도는 영상은 잇지 않음을 봤다(유튜브는 사용자의 프록시(미국 출구)에서 봇 확인·'Video unavailable' 을 내서 프록시를 끄고 PC 회선으로 본다. 재생
위치는 DevTools 로 `<video>` 의 `currentTime` 을 읽는다).

**브레이브와 견줌**(브레이브·크로미움의 소스와 유튜브 재생기 `base.js` 를 읽은 것 — '추론' 이라 적은 것 밖은 소스로 확인했다). 브레이브(안드로이드)는
두 겹이다 — 크로미움에 `--disable-background-media-suspend` 를 주어 숨은 탭의 미디어를 멈추지 않게 하고, 유튜브의 주 세계에 스크립트를 넣어
`visibilitychange` 듣개 등록을 버리고 `visibilityState` 를 늘 'visible' 로 읽게 한다(기본 목록의 스크립틀릿도 같은 일과 `_lact` 갱신). 그 탭은 크로미움에
**실제로 숨어** 그리기가 서고, 크로미움은 숨은 지 10초 뒤 영상 트랙을 끈다(키프레임 간격이 5.5초 밑일 때) — 소리만 남아 끊김이 없다. **WebView 에서는
첫 겹이 안 된다** — 모든 WebView 기기에서 숨은 쪽의 미디어를 멈추고(`aw_field_trials.cc` 가 큰 화면 예외까지 끈다) 앱이 줄 수 있는 스위치가 없다. 그래서
'보임' 으로 두는 길뿐이고, 남은 차이(그려지지 않음)를 화면 밖 그리기로 메운다. **유튜브 모바일 재생기는 `visibilityState` 만 본다** — `visibilitychange`
듣개와 `loadedmetadata`·`playing`·**`timeupdate` 마다**, 'visible' 이 아니고 그 영상이 `backgroundable`(프리미엄으로 보인다 — 추론)이 아니면 `stopVideo()`.
화질 상한도 넷 있다 — 버린 프레임(5초마다 견주어 세 번 잇달아 넘으면 한 단계씩, `yt-player-performance-cap` 으로 30일 남기고 깃발에 따라 사람이 고른 화질도
누른다), 다시 받기(60초에 네 번), 매끄러움(`mediaCapabilities.decodingInfo` — 크로미움은 버린 프레임이 5% 넘은 기록이 있는 꼴을 매끄럽지 않다고 답하고 그
기록은 쪽이 숨었을 때만 쉰다), 숨은 채 60초 쉬면 360p. 사용자 기기에 이 상한이 남아 있을 수 있다(그 localStorage 와 크로미움의 디코드 기록 — 추론).

**CPU — 크롬·브레이브와 견줌.** 에뮬레이터에서 같은 유튜브 영상(480p VP9·30 fps, 프록시 끔)을 견준 결론이다.
- **앞**: 크롬보다 10% 안팎 더 쓴다. 앱의 기능(중계·광고 차단·스크립트) 탓이 아니다 — 맨 WebView(WebView Shell)도 같거나 더 썼다. 까닭은 WebView 의
  구조다(크로미움·AOSP 소스): 크롬은 영상을 SurfaceControl 층으로 디스플레이 하드웨어(HWC)에 넘기는데(`kAndroidSurfaceControl`), 안드로이드 12 의 WebView
  는 그 길이 꺼져 있어(`kWebViewSurfaceControl` 기본 꺼짐, HWUI 의 `debug.hwui.webview_overlays_enabled` 기본 false — 13 부터 true) 영상 프레임마다 앱이
  WebView 전체를 다시 합성한다. 에뮬레이터에서는 크롬도 오버레이를 못 쓰므로 폰에서는 차이가 더 클 것이다(추측). **이 견줌은 12 에서 잰 것이다** — 13 부터는
  HWUI 의 WebView 오버레이가 켜져 있어 WebView 도 영상을 오버레이로 넘길 수 있다(크로미움 쪽 기능이 그 기기·판에서 켜지는지는 확인하지 않았다). 그러면 앞의
  차이는 줄고, 오버레이로 넘긴 영상은 화면 밖 그리기(`OffscreenDraw` — 뷰의 `draw`)에 실리지 않으므로 뒤의 그리기·디코더 막힘도 달라질 수 있다 — 16 에서 다시
  재지 않았다(아래 '확인하지 못한 것').
- **뒤**: 화면 밖 그리기를 vsync 마다(화면이 꺼져도 55–60 Hz) 하면 30 fps 영상에서 앞의 두 배를 그려 앞보다 더 쓴다 → 청할 때만 그리고, 영상 요소를
  숨기면 그리기가 거의 0 이 되어 뒤(홈)·화면 끔이 앞의 절반쯤이다(버린 프레임 0, 화질 그대로). 그래도 쪽을 실제로 숨기는 브레이브가 뒤에서 훨씬 적게
  쓸 것이다(재지 못했다). 크롬은 뒤에서 재생하지 않는다.
- **고정 30·15 fps 로 그리지 마라** — 못 그린 프레임이 버린 프레임으로 세어져 5% 를 넘으면 크로미움의 디코드 기록과 유튜브의 30일 상한이 걸려 앞에서도
  화질이 내려간다.
- 더 줄일 길(하지 않았다): 화면 밖 표면을 작게(영상을 숨긴 뒤로는 거의 그리지 않아 얻을 것이 적다), Wi-Fi `HIGH_PERF` 잠금 빼기(약한 Wi-Fi 에서 버퍼링할
  수 있다). 남은 몫은 해독(코덱 서비스)과 쪽의 스크립트다 — 영상 트랙을 끄는 길은 WebView 에 없다.
- **재는 법** — 에뮬레이터(`-gpu host -feature -Vulkan` — 실험실의 함정 '크롬 영상에서 죽는 에뮬레이터')에서 화질을 고정하고 미디어 음량 0, 40초씩 `/proc`
  의 utime+stime 을 창의 앞뒤에서 읽어 브라우저 프로세스·렌더러·화면 합성·코덱·소리 서비스를 합친다(한 코어 = 100, 한 번 잰 값이 ±3 쯤 흔들린다). 디버그
  기록 `offscreen drawn=… fallback=…`(10초마다)·`bgvideo hide|show` 로 그리기 수와 숨기기를 본다. **폰에서는**(adb 없이): 같은 긴 영상을 1080p 로 고정, 밝기
  고정·절전 끔·충전기 뺌·배터리 30–90%, 앞에서 30분씩(Web·크롬·브레이브 — 차례를 바꿔 두 번), 화면을 끈 채 뒤에서 30분씩(Web·브레이브 — 백그라운드 재생
  켬), 시작과 끝의 배터리 % 를 본다(앱별 숫자에는 하드웨어 해독·HWC·GPU 가 고르게 잡히지 않는 것으로 본다 — 추정).

**확인하지 못한 것.** **Android 16 에서 다시 잰 적이 없다**(CPU·끊김·화질 — 13 부터 HWUI 의 WebView 오버레이가 켜져 '보이되 그려지지 않는 WebView'
와 화면 밖 그리기의 전제가 달라질 수 있다, 위 'CPU' 의 '앞'). 사용자 기기에서 지금 판(화면 밖 그리기가 그 GPU 에서 되는지 — 실패가 이어지면 그만두고 예전처럼 끊긴다, 화면이 꺼진 동안의
vsync, 전력, 남은 화질 상한), 실기기(삼성 One UI 의 배터리 관리가 뒤의 앱을 정리하는지, 잠금 화면의 모양, 진짜 블루투스·유선 이어폰 단추 — 에뮬레이터에는
셸이 넣은 키뿐이다), 10분 뒤 닫힘, 알림의 '닫기' 단추 자체(같은 길인 정지 키로 봤다), 유튜브가 오래 만지지 않은 재생을 스스로 멈추는지('계속
시청하시겠습니까?' — 다루지 않는다), 데스크톱 사이트(www)·music.youtube.com 의 제목, 회의 탭을 남긴 채 다른 탭을 오래 쓸 때의 메모리, 1080p·60 fps 영상,
영상 트랙이 없는 재생(청이 없어 물러서기로 vsync 마다 그린다), 뒤에서 영상을 숨긴 채의 실제 광고(숨기는 것은 영상뿐이라 건너뛰기 단추는 보이고 눌릴
것이다), 돌아올 때 영상 자리가 잠깐 비는지, 알림 권한을 거절한 때(서비스는 서지만 알림이 보이지 않는다), TalkBack.

### 내려받기

- **WebView 가 아니라 앱의 HTTP 로 다시 받는다**(중계로 — 예외 규칙에 든 주소는 홉마다 바로): WebView 의 User-Agent, 그 주소의 쿠키(홉마다),
  Referer(지금 쪽에서 — 크롬의 기본 정책 `strict-origin-when-cross-origin`, **홉마다 다시 짓는다** — 아래 'Referer 를 홉마다'. 잠긴 HLS 의 열쇠만은
  쪽의 재생기가 실제로 보낸 머리로 — 아래 '찾은 미디어·HLS·DASH' 의 '열쇠 받기'), 넘겨주기는 손으로 따른다(http↔https, 받은 쿠키는 WebView 의 저장소로).
  이름은 `Content-Disposition` 의 `filename*`(안드로이드 12 의 `guessFileName` 은 모른다)·깨진 UTF-8 되돌리기·방향 제어 문자 지우기, 형식에 맞는
  확장자(`DownloadNames.named` — 이름은 지키고 확장자만 맞춘다). 공용 '내려받기' 폴더(MediaStore — 저장소 권한이 필요 없다)에 `IS_PENDING` 으로
  받고 실패하면 지운다. `data:` 는 그 자리에서 푼다. POST 로 받은 것은 GET 으로 다시 받는다. 확인 창에 이름 칸과 크기(`Range: bytes=0-0`).
- **Referer 를 홉마다.** 홉마다 그 홉의 주소로 `strictOriginWhenCrossOrigin(쪽 주소, 홉 주소)` 를 짓는다 — 한 번 지은 값을 모든 홉에 실으면 처음 주소가
  쪽과 같은 출처일 때 **쪽의 전체 주소(질의 포함)** 가 다른 출처로 넘겨준 곳·다른 출처의 조각·열쇠 서버에 간다(브라우저는 다른 출처에 출처만 보낸다).
  보통 파일·조각·재생목록(HLS·DASH 의 살피기 포함)·쪽의 요청을 찾지 못한 열쇠·찾은 미디어의 크기와 재생목록 미리 읽기(`MediaProbe`)가 모두 그렇다.
  쪽 주소는 받기 항목의 `pageUrl`(downloads.json). 쪽 주소를 적지 않던 옛 항목은 적어 둔 `referer`(쪽 주소이거나 그 출처)를 쪽으로 본다
  (`refererSourceOf` — 옛 판보다 더 내보내지 않는다). 받기를 넣는 함수들은 미리 지은 Referer 를 받지 않는다(쪽 주소만 — 새 항목의 `referer` 는 비운다).
  **그대로 둔 둘**: 열쇠의 '쪽이 보낸 머리 따라 하기'(쪽이 실제로 보낸 값 — 재생목록 요청의 Referer 로 물러선 갈래도 그 값을 넘겨주기 뒤의 홉까지 그대로
  싣는다), 갤러리 받기의 Referer(사이트의 규약이 정한 값 — 처음 홉에만 그대로, 넘겨주기 뒤의 홉은 같은 규칙으로 `Referrer.pinnedFirstHop` — hitomi 의 그림
  서버처럼 다른 출처의 처음 홉에도 정한 값이 가는 것은 데스크톱 다운로더와 같은 규약이다).
- **다시 해 보기·이어받기**(사용자 결정). 보통 파일은 끊기면 **받은 데서부터** `Range: bytes=N-` 와 `If-Range`(강한 ETag, 없으면 Last-Modified —
  그 사이 바뀌었으면 서버가 통째로 주고 처음부터 쓴다)로 스스로 잇고, **나아감 없이 세 번** 실패하면(1.5초·4초 쉬며) **멈춤**으로 둔다 — 받은 것은
  감춘 항목(`IS_PENDING`)에 남는다. HLS·DASH 는 조각마다 세 번 해 보고 그래도 안 되면 다 받은 조각까지 남긴 채 멈춘다. 서버가 4xx(408·429 밖)로
  거절하면 바로 멈춘다. 멈춘 것은 목록에서 누르면 '이어받기·지우기', 알림에 '이어받기' 단추. 시스템은 오래된(이레) 감춘 항목을 지운다 — 그때는
  처음부터. 동시 연결(한 파일을 여러 연결로)은 하지 않는다.
- **만료와 링크 갱신.** 401·403·404·410(프록시가 만든 답은 빼고)이면 멈추고 까닭을 적고, 목록 창과 알림에 '원래 페이지 열기'(`pageUrl`·`pageTitle`
  을 `downloads.json` 에 적는다 — 옛 파일도 읽힌다). 같은 페이지·같은 종류·주소의 경로가 같으면(질의만 다르면) 멈춘 항목을 새 주소로 잇는다
  (`LinkRenewal` — DASH 는 조각 수도 같아야).
- **목록**(`DownloadStore` — `downloads.json`, 200개까지)은 디스크에 둔다 — 앱이 다시 떠도 남고, 받던 중에 앱이 죽었으면 '멈춤(앱이 멈춰
  중단됐습니다)' 으로 돌아와 이어받는다. HLS 를 이어받을 때는 재생목록을 다시 읽는다(서명된 주소가 바뀌었을 수 있다 — 조각 수가 같으면 받은 조각
  뒤부터). 상태별 길게 누르기 메뉴(열기·공유·원래 페이지 열기·목록에서 빼기·파일까지 지우기 …, 받은 것을 버리는 일은 한 번 더 묻는다), 받는 중
  단추 '받기 취소(지움)'·'계속 받기'. 다 받은 것은 **기본 앱으로** 연다.
- **고아 정리**(`DownloadCenter.sweepOrphans`) — `downloads.json` 에 기록이 없고, 이 프로세스가 뜨기 1초 전보다 먼저 생겼고, 우리가 만든 감춘
  항목만 지운다. 목록이 깨졌으면 아무것도 지우지 않는다. **주 프로세스에서만** 돈다(테더 프로세스가 뜰 때 돌면 받는 중인 파일을 지웠을 것이다 —
  아래 '테더링' 의 '구조').
- **포그라운드 서비스**(`DownloadService`, `dataSync`, 알림 1). 하나면 진행과 '일시 정지'·'받기 취소(지움)', 여럿이면 줄마다 진행과 '모두 일시
  정지', 끝나면 누르면 여는 알림. 마무리 글은 '영상과 소리'·'영상만'·'소리만'. 오류 글은 한국어로만(`NetErrors` — 플랫폼의 영어 글을 그대로
  보이지 않는다, 순수 함수로 두고 시험).
- 살피기·마무리·취소 뒷정리는 받기 일꾼과 따로 된 일꾼에서 한다(살피기 둘, 연결 8초·읽기 10초 — `inspectHls`·`inspectDash` 가 `Inspection` 을
  돌려주고 `cancel()`). 조각은 받으면서 파일에 쓰고(잠긴 것은 흘려 보내며 푼다), 범위 읽기는 `Streams.slice` 의 Long 셈. 시도마다 연결 객체를 새로
  얻는다.
- 프록시를 끄면 받던 보통 파일·HLS 가 직접 연결로 이어 받는다 — 스위치는 전역이라는 결정 그대로다(검토가 짚었지만 바꾸지 않았다).

### 찾은 미디어·HLS·DASH

- **찾기.** 쪽이 낸 요청(가로채기 — 광고 차단과 따로, 차단을 꺼도 찾는다)을 **주소의 확장자**로 가린다(`MediaSniffer` — WebView 는 응답의 형식을
  알려 주지 않는다): `.m3u8` HLS, `.mpd` DASH, 영상·소리 확장자. 재생목록의 조각(`.ts`·`.m4s`)은 치지 않는다. 하나라도 있으면 막대에 '찾은 미디어'
  단추(수 배지·`stateDescription` '이 페이지에서 N개'·live region, 앞 페이지 것만 남으면 흐리게).
- **어느 페이지의 것인가**(`core/media/FoundMediaLog`). 탐색이 **커밋될 때** 경계를 옮긴다(androidx.webkit `NavigationListener` 의 `didCommit`·
  `isSameDocument` — 커밋되지 않은 탐색, 곧 첨부 응답·204 는 목록을 건드리지 않는다). 커밋 알림이 새 문서의 첫 요청보다 늦게 오므로 다른 문서로
  커밋하면 **그 탐색이 시작된 뒤 처음 찾은 것만** 새 페이지로 옮긴다. 조각(#)만 바뀐 이동은 경계가 아니고, 다시 요청된 것은 지금 페이지로 올리며,
  앞 페이지는 3개·떠난 지 10분까지(목록 창의 아래에 '앞 페이지 — 제목 · n분 전' 으로 흐리게). 찾은 영상의 기본 이름은 찾은 페이지의 제목.
- **HLS.** 찾은 m3u8 을 뒤에서 한 번 읽어(`MediaProbe` — 중계로, 페이지마다 6개) 마스터의 하위(화질·소리·자막·각도·I-frame)를 숨기고, 하위를
  고르면 그 마스터의 화질 창으로. 번호만 다른 같은 경로의 파일은 3개부터 한 줄(둘은 다른 영상일 때가 많다), 저지연 HLS 의 `_HLS_*` 질의는 열쇠에서
  뺀다. 받을 때는 재생목록을 앱의 HTTP 로 읽어(상대 주소는 **넘겨주기 뒤의 주소**에 대어 RFC 3986 대로 — 아래 '열쇠 받기') 마스터면 **화질을
  고르게**(`1280x720 · 2.5 Mbps`, 어림 크기·코덱·'소리만' — 마스터면 가장 높은
  화질의 재생목록을 한 번 더 읽어 길이를 안다), 미디어면 조각 수와 길이를 보이고 묻는다. 조각을 차례로 받아 **한 파일로 잇는다** — TS 는 `.ts`
  (이어 붙여도 된다), fMP4 는 초기 조각 + 조각들을 `.mp4`(소리만이면 `.m4a`), 묶음 AAC 는 `.aac`. 바이트 범위는 `Range` 로(서버가 통째로 주면
  잘라 쓴다), 진행은 '조각 a / b'. 고른 화질의 소리가 따로면(`#EXT-X-MEDIA`) 따로 받아 한 파일로 담는다(아래 '재생 중인 것 받기' 의 '합치기').
  잠긴 초기 조각은 그 MAP 줄이 나온 자리의 잠금을 갖는다(RFC 8216 4.3.2.4 — ffmpeg 처럼 MAP 이 KEY 보다 앞이면 평문), IV 가 없으면 0(hls.js 와
  같다). **AES-128 은 풀어 받는다**(`HlsCrypto` — 플랫폼 `javax.crypto`, `update`·`doFinal` 을 직접 부른다 — 함정 '`CipherInputStream`', 열쇠는
  아래 '열쇠 받기'). 읽는 동안
  취소할 수 있는 '재생목록을 읽는 중…' 창(거듭 누름은 무시, 취소하면 `Inspection.cancel()`).
- **DASH(`DashManifest` — `:core`).** 플랫폼의 XML 읽기(`javax.xml.parsers`)로 MPD 를 읽되 **DOCTYPE 이 든 문서는 읽지 않는다**(외부 엔티티·
  엔티티 폭탄 — MPD 에는 DOCTYPE 이 없다). **첫 기간 하나만** 본다. 화질마다 조각 주소를 정한다 — `SegmentTemplate`(`$Number$`·`$Time$`·
  `$RepresentationID$`·`$Bandwidth$`, `%03d` 꼴, `SegmentTimeline` 의 `S@t·d·r`(`r=-1` 은 기간 끝까지)), `SegmentList`(`SegmentURL@mediaRange`·
  `Initialization@range`), `SegmentBase` 나 `BaseURL` 만이면 파일 하나 통째로. 위(`AdaptationSet`·`Period`)의 것을 물려받고 `BaseURL` 은 목록
  주소(넘겨주기 뒤의 것)부터 차례로 대어 RFC 3986 대로 푼다(`UriRefs` — HLS 와 같다). 영상 화질을 고르면 영상과
  가장 좋은 소리를 받아 한 파일로 담는다(조각 잇기는 HLS 와 같은 `DownloadCenter.fetchParts`).
- **받지 않는 것.** 실시간 방송(HLS 에 `#EXT-X-ENDLIST` 가 없다·DASH 의 `type="dynamic"` — 그 자리에서 알리고 '재생 중인 것 받기' 로 잇는 창), DRM
  (`SAMPLE-AES`·키 형식이 identity 가 아닌 것(Widevine·FairPlay)·DASH 의 `ContentProtection` — DASH 는 붙은 화질만 빼고 다 붙었으면 받지 않는다).
  DASH 의 둘째 기간부터(광고를 끼운 기간 나눔 등), 소리의 말 고르기(대역폭이 가장 큰 것), 자막(`text/vtt` 등)은 보지 않는다.

**열쇠 받기.** 잠긴 HLS 의 열쇠 응답이 정확히 16바이트가 아니면(빈 응답·HTML·JSON·hex·base64 글·덧붙은 바이트, 64 KB 넘음) '잠금을 풀 열쇠를 받지
못했습니다' 가 된다. 그렇게 되는 길 — 넘겨준 재생목록의 상대 열쇠를 처음 주소에 대어 풀어 엉뚱한 곳(200 의 웹 페이지)에 청함, `URI="?k=1"` 을 브라우저와
다르게 풂, Referer 를 보는 열쇠 서버가 앱이 지은 Referer 에는 JSON 을 주고 틀의 Referer 에는 열쇠를 줌, 16바이트가 아닌 꼴. 아래가 그 대책이다(E·A·B·C 는
조사의 권고 이름 — '사용자가 정할 것' 의 D·F 와 같은 목록).

| 무엇 | 왜 그렇게 했나 |
|---|---|
| **쪽이 보낸 요청을 따라 한다(E)** — `intercept` 가 광고 차단 뒤에(나가는 요청만) `request.requestHeaders` 를 그 탭의 `PageRequests`(`Tab.requests`)에 적고, 열쇠를 받을 때(`DownloadCenter.loadKey`) 그 열쇠의 요청을 찾아 쪽이 보낸 Referer·스크립트가 단 머리·Origin 을 싣는다. 받기 항목은 고른 탭의 것을 쥔다(`MediaChooser` → `enqueueHls`) | 재생기가 다른 사이트의 틀 안에 있으면 그 틀의 출처가 Referer 인데 앱은 맨 위 쪽으로 지었다. hls.js 의 `xhrSetup` 따위가 단 토큰 머리는 앱이 알 길이 없었다. WebView 는 그 요청의 머리에 Referer 를 담아 준다(크로미움 `aw_proxying_url_loader_factory.cc` — 소스로 읽었다, **기기의 판에서는 재지 않았다**). 쿠키·Origin·Sec-Fetch 는 망이 그 뒤에 붙여 머리에 없다 — 쿠키는 다른 요청처럼 홉마다 WebView 의 저장소에서 |
| 찾는 차례(`replayFor`) — 열쇠 주소 그대로 → 쪽이 같은 길에 질의를 더한 주소(그런 주소가 하나일 때만 — 그 주소로 청한다) → 같은 길의 다른 질의(우리 주소에 머리만) → 그 재생목록의 요청(Referer·Origin 만). 없으면 다른 요청처럼(홉마다 맨 위 쪽에서 지은 Referer — '내려받기' 의 'Referer 를 홉마다') | 열쇠 요청은 재생목록을 읽기 전에 지나가므로 무엇이 열쇠인지 모르는 채 쥔다. 견주기는 출처(소문자·기본 포트 없이)와 길·질의 — 조각(#)은 뗀다 |
| 쥐는 요청 — GET·http(s)·8 KB 이하의 주소이고, `Accept` 가 그림·스타일·문서(틀)·미디어 요소가 아니고 확장자가 그림·스타일·스크립트·글꼴·조각(`ts`·`m4s` …)이 아닌 것. 탭마다 128개·30분·52만 글자(`MAX_CHARS`)까지, 넘으면 오래된 것부터 버린다. 머리는 24개·이름은 토큰 글자 64자·값은 출력할 수 있는 ASCII 와 탭 4 KB 까지 | 쪽마다 수백인 조각·그림이 열쇠를 밀어내지 않게. 글자 수는 쪽이 머리를 부풀려(24개 × 4 KB) 앱의 메모리를 쓰지 못하게 — 접은 탭도 쥔 채 있다. CR·LF 가 든 값(머리 끼워 넣기)과 ASCII 밖의 값은 쥐지 않는다 |
| 쥐지 않는 머리 — 아래 표. 그 밖(`Accept`·`Authorization`·`X-…`·클라이언트 힌트 …)은 그대로 싣는다 | |
| 스크립트가 단 머리는 쪽이 보낸 요청과 **같은 출처**의 홉에만 싣는다(넘겨주기로 다른 출처에 가면 뗀다). Origin 은 Referer 의 출처가 열쇠 주소의 출처와 다를 때만, 홉마다(브라우저가 다른 출처로의 XHR 에 다는 것). 쪽이 Referer 를 보내지 않았으면 보내지 않는다 — 그때는 Origin 도 싣지 않는다 | 인증 토큰이 넘겨준 곳에 새지 않게 — 브라우저도 다른 출처로 넘겨줄 때 `Authorization` 을 뗀다. Referer 를 빼 버린 쪽(`no-referrer`)의 Origin 은 지을 수 없다 |
| **메모리에만** — 기록·디스크(`downloads.json`)에 쓰지 않는다(`toJson` 에 없다, 디버그 기록은 꼴과 수만 — `hls key kind=… match=…`). 탭을 닫으면(`closeTabs`)·화면이 사라지면 버리고, 주 틀에 다른 사이트(등록 도메인)의 문서가 커밋되면 그 탐색이 시작되기 전에 적은 것을 버린다(`committed` — 찾은 미디어의 경계와 같은 까닭). 앱이 다시 뜨면 없다 — 그때 이어받는 열쇠는 쪽의 머리 없이 받는다 | 값이 토큰일 수 있다(`Authorization`·주소의 질의) |
| **넘겨주기 뒤의 주소로 푼다(A)** — `readPlaylist`·`inspectDash` 가 넘겨주기를 다 따른 주소(`HttpFetcher.Response.url`)를 바탕으로 준다. 상대 주소는 `UriRefs`(RFC 3986 5.2 — 질의만 있는 참조는 재생목록의 이름을 지키고, URI 가 받지 않는 글자(세로 막대·중괄호)가 든 참조도 푼다) | RFC 8216 4.1 은 '그것을 담은 재생목록의 주소', hls.js·ExoPlayer 도 응답의 주소로 푼다. 찾은 미디어의 미리 읽기(`MediaProbe`)는 처음부터 그랬다. 함정 '`java.net.URI.resolve`' |
| **너그럽게 읽되 확인한다(B)** — 16바이트면 그대로. 아니면 후보(`HlsKeys.candidates`) — 32자 hex(앞의 `0x` 는 떼고)·24자 base64(끝이 `==`, URL 판도)를 푼 것과, 16바이트보다 길면 앞 16바이트. 후보는 **첫 조각의 앞머리 16 KB 를 풀어** HLS 조각의 꼴일 때만 쓴다(`HlsKeys.pick`) — TS(188 바이트마다 0x47, 셋 이상)·fMP4(아는 상자 이름과 말이 되는 크기)·ID3(판 2~4)·ADTS(이어지는 프레임 둘). 조각 전체를 읽었으면 PKCS#7 채움까지. 읽은 앞머리는 이어 쓴다(다시 받지 않는다) | hls.js 는 긴 응답의 앞 네 낱말로 재생한다(WebCrypto 가 거절하면 소프트웨어 AES — `aes-decryptor.ts`, 소스로 읽었다) — '재생은 되는데 받기만 안 된다' 의 한 길. 확인 없이 쓰면 틀린 열쇠로 쓰레기 파일을 만든다 — 무작위 바이트 10만 덩이·무작위 열쇠 1만 개가 하나도 맞지 않았다(시험). JSON 에 싼 열쇠와 사이트만의 풀이는 하지 않는다 |
| **까닭의 갈래를 글에(C)** — '잠금을 풀 열쇠를 받지 못했습니다 — <꼴> · 넘겨주기 n번 · 열쇠 k번째(n개 중) · 쪽과 다른 사이트 · <쪽의 요청>'(있는 것만). 꼴은 빈 응답·웹 페이지(HTML, 크기)·JSON(크기)·글(크기)·hex 글·base64 글이 맞지 않음·n바이트(길면 '앞 16바이트로도 풀리지 않습니다')·64 KB 넘음, 쪽의 요청은 '쪽이 보낸 머리를 실음'·'재생목록 요청의 Referer 를 실음'·'쪽의 요청 기록 없음'. 실패 창은 그 글 뒤에 '재생은 되면 ‘재생 중인 것 받기’의 ‘가로채기’로 받을 수 있습니다.' 를 붙인다(옛 글로 실패한 항목에도) | 다음 실패가 어느 까닭인지 말하게. 글은 `downloads.json` 에 남으므로 주소·쿠키·토큰·몸은 싣지 않는다(`HlsKeyTexts` — 자원 번호와 인자로만 골라 JVM 에서 시험, 글은 `strings_hls_key.xml`). 가로채기는 재생기가 푼 바이트를 담아 열쇠가 필요 없다 |

| 쥐지 않는 머리 | 까닭 |
|---|---|
| `Cookie`·`Cookie2` | 앱이 홉마다 WebView 의 저장소에서 싣는다(넘겨준 곳의 쿠키도) |
| `Host`·`Connection`·`Keep-Alive`·`TE`·`Trailer`·`Transfer-Encoding`·`Upgrade`·`Expect`·`Content-Length`·`Proxy-Connection` | 연결의 머리 — 플랫폼 HTTP 가 정한다 |
| `Proxy-Authorization` | 프록시 계정은 중계의 것이다 |
| `Range`·`If-Range`·`If-Match`·`If-None-Match`·`If-Modified-Since`·`If-Unmodified-Since` | 열쇠에 206·304 를 부른다 |
| `Accept-Encoding` | 플랫폼 HTTP 가 스스로 청하고 푼다 — 옮기면 br 따위가 와서 풀지 못한다 |
| `User-Agent` | 앱이 같은 값(WebView 의 UA)을 싣는다 |
| `X-Requested-With` | WebView 의 표지다(이 앱은 비운다 — '쪽의 결정·UA·DRM') |
| `Referer`·`Origin` | Referer 는 따로 쥐어 그대로 싣고 Origin 은 그것에서 짓는다 |

**시험.** `:core` `UriRefsTest`(RFC 3986 5.4 의 보기 전부·질의만·빈 길·URI 가 받지 않는 글자·출처)·`HlsKeysTest`·`PageRequestsTest`·`HlsTest`, `:net`
`HttpFetcherTest` 의 넘겨주기 수, 앱 `HlsKeyTextsTest`·`HlsKeyWiringTest`(재생목록·MPD 를 넘겨주기 뒤의 주소로·열쇠를 쪽의 머리로 받고 첫 조각으로 확인·쪽의
머리가 `toJson`·`fromJson`·기록에 없음·탭이 쥐고 닫거나 떠나면 버림·글의 길이와 실패 창의 안내 — 소스·자원으로). 시험 자료는 합성(`*.test`)뿐.
에뮬레이터에서 프록시를 끄고 PC 의 작은 서버(스크래치패드 — 합성 TS 를 openssl 로 실제로 잠갔다, 두 포트로 출처 둘)로 토큰 머리를 보는 열쇠 서버·다른
출처의 틀 안 재생기·받을 수 없는 열쇠의 실패 글·32바이트 열쇠·302 로 넘겨준 재생목록의 상대 열쇠를 봤다 — **WebView 153 의 `requestHeaders` 에 스크립트가
단 머리와 Referer 가 담긴다**. 받은 시험 파일은 앱의 '파일까지 지우기' 로 지운다.

**확인하지 못한 것.** 사용자가 겪은 사이트(이 고침으로 받히는지 — 아니면 새 글의 갈래로 다음을 정한다), 너그러운 열쇠를 주는 실제 사이트, 분할 쿠키,
서비스 워커가 낸 열쇠 요청(그 요청은 `intercept` 로 오지 않는다), hex·base64 열쇠와 질의만 있는 열쇠 주소의 기기 확인(JVM 시험만). 쪽을 옮긴 직후의 받기에서
파일의 이름과 Referer 가 **앞 쪽의 것**이 된 적이 한 번 있다 — 찾은 미디어의 페이지 경계(`FoundMediaLog`)의 드문 경합으로 보인다(같은 차례로 다시 해 보니
맞았다 — 확인하지 못했다).

### 재생 중인 것 받기

직접 받기 어려운 스트리밍을 재생하는 그대로 받는다 — 가로채기와 녹화 둘 다, 사용자가 고른다(위 '사용자 결정').

- **쪽↔앱 통로(`PageBridge`·`BridgeScripts`).** androidx.webkit 1.17 의 **실행 세계**(`JS_INJECTION_IN_FRAME_AND_WORLD` — 크롬 확장의 고립된
  세계와 같다)에 주입 객체(`addWebMessageListener(…, world, …)`)와 문서 시작 스크립트(`addJavaScriptOnEvent`)를 **모든 탭·모든 틀에** 둔다. 쪽의
  스크립트는 그 세계의 변수·함수·주입 객체를 볼 수 없고 DOM 만 함께 쓴다 — 늘 걸어 두어도 쪽이 알아챌 것이 없다. 세계·객체의 이름은 프로세스마다
  무작위. 앱으로 가는 것은 JSON 글과 이진 덩이(머리 1 바이트 종류 + 4 바이트 번호 — `WEB_MESSAGE_ARRAY_BUFFER`). 그 기능을 모르는 WebView 면
  통로를 걸지 않는다(blob 은 '받지 못합니다'). **쪽의 세계를 건드리는 것은 사용자가 고른 탭·고른 동안만**이다 — 가로채기의 갈고리와 blob 갈고리.
- **`blob:` 내려받기**(사용자 결정). WebView 는 blob 주소만 알려 주고 바이트는 쪽 안에만 있다. 쪽은 흔히 고리를 누른 **바로 다음 줄에서
  주소를 거둔다** — 그래서 통로의 스크립트가 **누름을 포착 단계에서 보고 그 자리에서 `fetch(blob)` 로 Blob 을 쥔다**(부른 때의 blob 을 쥐므로 곧이어
  거둬도 남는다, 2분). 앱은 내려받기 알림이 오면 틀마다 묻고(`PageBridge.findBlob`) 가진 틀이 512 KB 덩이로 넘긴다 — 한 덩이를 '내려받기' 폴더에
  쓴 뒤 다음 덩이를 청한다(메모리를 묶는다). 이름은 고리의 `download` 속성(확장자만 형식에 맞춘다). **넘겨받지 못하는 둘** — 쪽의 CSP 가 `blob:`
  읽기를 막는다(실행 세계에도 쪽의 CSP 가 걸린다), 본문 밖의 고리를 누르고 곧바로 거둔다 — 에서는 '준비하고 다시 열기' 를 묻고, 고르면
  **그 탭에만, 그 사이트에 있는 동안만** 쪽의 세계에 `URL.createObjectURL` 갈고리(`BLOB_HOOK` — 만든 Blob 을 쥐었다가 Blob 자체를 건넨다,
  네트워크 읽기가 아니라 CSP 가 걸리지 않는다)를 걸고 쪽을 다시 연다. 본문 밖의 고리는 이름이 'download' 다(누름이 창까지 오지 않아 속성을 볼 수
  없고, WebView 도 알려 주지 않는다). blob 붙잡기 상한 8개·256 MB·120초.
- **실시간 녹화.** 쪽을 다시 열지 않는다. 틀마다 재생 중인 `<video>`·`<audio>` 를 물어 가장 알맞은 것(재생 중·영상·큰 것)을 고르고, 그 틀의 실행
  세계가 `captureStream()` → `MediaRecorder`(H.264+AAC 의 MP4 를 먼저, 안 되면 VP9/VP8+Opus 의 WebM — 소리만이면 AAC 의 M4A·Opus 의 WebM, 5
  Mbps·160 kbps)로 1초마다 넘긴다. 요소가 멈추면 녹화도 멈추고('녹화 멈춤' — 막대의 시간은 실제로 담긴 시간), 끝나면 저장한다. 쪽이 바뀌면 받은
  데까지 저장한다(마지막 1초는 잃는다). 저장은 MediaRecorder 의 조각난 그릇을 **길이와 찾기 표가 있는 그릇으로 다시 담는다**(아래 합치기 — 담지
  못하면 받은 그대로). **다시 인코딩**이라 화질이 떨어지고 재생 시간만큼 걸린다. 다른 출처의 미디어(CORS 없이)는 `captureStream` 이 거절될 수
  있다(확인하지 않았다 — 그런 것은 직접 받기로 된다). DRM 요소(`mediaKeys`)는 녹화하지 않는다. 시작하지 못한 까닭은 읽는 말로(쪽이 준 예외 이름을
  그대로 보이지 않는다).
- **MSE 가로채기.** '가로채기' 를 고르면 그 탭의 WebView 에 쪽의 세계 갈고리(`MSE_HOOK` — 문서 시작)를 걸고 **쪽을 다시 연다**(재생기가 처음부터
  다시 받는 것을 담으려고 — 초기 조각을 놓치면 읽을 수 없다). 갈고리는 `MediaSource.addSourceBuffer`·`SourceBuffer.appendBuffer`(그 호출 안에서
  복사)·`changeType`·`HTMLMediaElement.setMediaKeys`(DRM)를 감싼다. 통로는 실행 세계가 `window.postMessage(무작위 표, [MessagePort])` 로 건네고
  갈고리가 제일 먼저 건 포착 듣개로 받아 멈춘다(쪽은 그 알림을 보지 못한다). 앱은 버퍼마다 파일에 잇고(`CaptureSession` — 초기 조각·
  `timestampOffset` 이 바뀌면 새 덩이), 막대 아래 줄이 '가로채는 중 · 영상 N · 소리 N'·저장하면 담길 크기·저장·취소를 보인다. 저장할 때 '영상과
  소리' 또는 '소리만', 영상이 여럿이면 고른다(그 영상이 처음 잡힌 때의 제목이 이름). 바이트가 가장 많은 MediaSource 의 버퍼마다 **같은 초기 조각의
  덩이들**(같은 화질 — 바이트가 가장 많은 무리)만 첫 시각 차례로, 같은 시각의 표본은 한 번만 담는다. **다시 인코딩하지 않는다** — 재생기가 받은
  만큼만 담긴다. **가로채기는 사이트(등록 도메인)에 묶는다** — 다른 사이트로 가는 탐색이 시작되면 갈고리를 걷고(커밋되지 않으면 다시 건다), 다른
  사이트 문서가 인사하면 끝내고 저장을 묻는다(주 틀의 인사가 오는 출처로 가리므로 페이지가 꾸밀 수 없다). 통로는 그 사이트 문서에만, 20초 안에 오지
  않으면 쌓던 것을 버린다. 쓰기 대기 48 MB(초기 조각·첫 덩이는 버리지 않는다, '버림 N MB'). DRM 을 쓰는 쪽이면 '받을 수 없다' 고 보이고 저장
  단추가 없다.
- **탭을 닫을 때.** 탭을 닫는 네 길(뒤로·X·모두 닫기·`window.close`)이 모두 `settleCaptures` 를 지난다 — 사용자가 닫으면 '저장하고 닫기 / 버리고
  닫기 / 취소', `window.close` 는 묻지 않고 받은 데까지 저장(페이지 하나가 사용자의 녹화를 지울 수 없게), `dispose()` 의 기본도 저장. 탭 목록의
  '● 녹화 중 · 1:23', 막대 아래 '다른 탭에서 녹화 중 · 제목 [그 탭으로]'.
- **`CaptureService`**(dataSync 포그라운드 서비스, 알림 2 — '녹화 중 · 1:23 · 12 MB' 의 저장·취소). 받는 동안 서고 받는 것이 없으면 스스로
  내려간다. 앱이 뜰 때 남은 것을 지우지 않고 '지난번에 받던 것이 남아 있습니다'(`CaptureRecovery` — `meta.json`)로 저장을 묻는다.
- **합치기(`Remux` — media3, 사용자 결정).** 읽기는 **media3 의 추출기**(`media3-extractor` 1.11.1 — fMP4·MP4·TS·ADTS·MP3·
  WebM), 쓰기는 플랫폼 `MediaMuxer`. **다시 인코딩하지 않는다.** 플랫폼 `MediaExtractor` 를 쓰지 않는 것은 이어 붙인 fMP4 의 `tfdt` 를 읽지 않고
  TS 의 시각을 누르기 때문이다. 줄기(영상·소리)마다 덩이들을 차례로 읽어 트랙 하나를 뽑고 **시각 차례로 엇갈려** 담는다(한 줄기를 다 담고 다음을
  담으면 `MediaMuxer` 가 메모리에 쌓는다). 시작은 맞춘다 — 줄기들의 첫 시각이 2초 안에서 갈리면 같은 시계(TS 의 PTS·fMP4 의 tfdt·묶음 AAC 의 ID3
  시각 `Id3Timestamp`)로 보고 가장 이른 것을 0 으로, 더 갈리면 줄기마다 제 첫 시각을 0 으로. 그릇은 코덱이 정한다 — 모두 VP8·VP9·Opus·Vorbis 면
  WebM, 모두 H.264·H.265·MPEG-4·AV1·AAC 면 MP4(`Remux.MP4_MIMES` — minSdk 36 이라 AV1 을 판으로 가르지 않는다), 그 밖은 담지 못한다(그때는 **따로 된 파일**로 남기고 목록·알림에 까닭을 적는다). DRM 으로 잠긴
  표본이 보이면 담지 않는다. HLS·DASH 의 따로 된 소리는 줄기마다 앱 안의 임시 파일(`noBackupFilesDir/downloads/<번호>`)로 받은 뒤 한 파일로
  담는다. TS 는 **조각마다 따로** 읽는다(함정 '따로 만든 TS 조각의 연속성 카운터'). 줄기가 하나면 '내려받기' 폴더에 곧바로 잇는다(TS 는 `.ts` 그대로).
- **의존**(사용자 승인). `androidx.media3:media3-extractor:1.11.1` 이 끌어오는 것 — media3-common·container·decoder, **Guava 33.3.1-android**
  (+ failureaccess·listenablefuture 빈 것), androidx.annotation·annotation-experimental, 컴파일 때만 쓰는 주석들(jsr305·checker-qual·
  error_prone_annotations·j2objc). 모두 Apache-2.0(checker-qual 은 MIT). iroiro-viewer 와 같은 판이라 새로 받은 것은 `annotation-experimental`
  1.3.1 의 AAR 하나였다. 의존을 바꾸면 설정의 '정보'(오픈소스 목록 — `settings_about_text`)도 고친다.
- **실험실에서 확인한 것.** 합치기 — DASH·HLS fMP4·HLS TS+묶음 AAC 모두 한 MP4(avc1 + mp4a — 표본 수와 길이가 원본과 같다), DASH WebM 은 한 WebM
  (V_VP9 + A_OPUS). 녹화 — MP4(H.264 + AAC), 소리만 M4A. 가로채기는 원본과 같은 표본 수. 가로채기 중 쪽이 MediaKeys 를 붙이면(ClearKey) 'DRM' 을 보이고
  저장 단추가 없다.
- **확인하지 못한 것.** 실기기의 Widevine·HDR·B 프레임이 많은 실제 스트림의 합치기(에뮬레이터는 MediaCrypto 가 없고 시험 미디어는 B 프레임이
  없다), 워커 안의 MSE(크롬 108+ `MediaSourceHandle` — 문서 시작 스크립트가 닿지 않아 놓친다, 녹화를 쓴다), 녹화의 멈춤·알림의 저장·취소·가로채기의
  사이트 묶기·되살리기(`am kill` 뒤의 물음)는 기기에서 보지 않았다.
- **유튜브.** m.youtube.com 에서 **가로채기가 된다** — 재생기가 PO 토큰·SABR 을 다 풀고 넘기는 바이트라 yt-dlp 의 어려움이 여기에는 없다(에뮬레이터·
  프록시 끔, 짧은 영상이 처음부터 끝까지 담겼다). 지킬 것:
  - 유튜브 WebM 은 Cues 되감기를 끈 추출기로 읽어야 저장이 끝난다(함정 'MSE 로 받은 WebM 과 Cues' — `Remux.newExtractor`). JVM 시험은 없다(media3
    추출기가 JVM 의 android.jar 에서 `Build.FINGERPRINT`·`TextUtils` 로 깨진다 — 기본값을 켜도 트랙을 읽지 못했다).
  - **가로채는 동안 AV1 은 '틀 수 없다' 고 답한다** — `MSE_HOOK` 이 `MediaSource.isTypeSupported`·`canPlayType`·`mediaCapabilities.decodingInfo` 에서
    `av01` 만 거절한다(가로채기를 고른 탭·그동안만). 이 WebView 에 유튜브는 AV1(+ Opus)을 고르는데, AV1 + Opus 는 `Remux` 가 한 그릇에 담지 못해(WebM 의
    코덱 목록에 AV1 이, MP4 의 목록에 Opus 가 없다) 영상·소리 두 파일이 됐다. 거절하면 유튜브가 VP9 + Opus 를 골라 WebM 한 파일이 된다. **minSdk 36 에서도
    거절한다** — 플랫폼 `MediaMuxer` 가 MP4 에 Opus 를, WebM 에 AV1 을 담지 못한다(Android 16 — `android-16.0.0_r1` 의 `MPEG4Writer::getFourCCForMime` 은
    소리가 AMR·AAC 뿐, `WebmWriter` 는 VP8·VP9·Opus·Vorbis 뿐, `MediaMuxer` 문서의 표도 Opus 는 WebM·OGG 만, AV1 은 MP4 만(34 부터)). 2026-11-21 에 소스로
    확인했다 — 담지 못하므로 에뮬레이터 확인은 하지 않았다. 풀려면 다시 인코딩하거나 소리를 따로 두는 것을 받아들여야 한다('사용자가 정할 것').
  - 이름이 `youtube.com` 이 될 수 있다 — 그 영상이 처음 잡힌 때의 제목이 비어 있었다(되살린 저장이라 그런지는 보지 않았다).
  - 보지 않은 것 — 화질 전환, 광고 섞임(광고 차단이 켜져 있었다), 긴 영상·배속, 프록시로 볼 때의 봇 확인, 워커 MSE.

### 화면 — 오류 덮개·전체 화면·화면 켜 두기·확대 허용·당겨서 새로 고침

**'열지 못함' 덮개(`ErrorCover`·`PageErrors`).** 까닭을 오류 이름(`net::ERR_…`)·중계의 표(`X-Iroiro-Relay`)·Squid 의 표(`X-Squid-Error`)·그때의
길의 상태로 고르고(오류 뒤 5초 안의 길 고장도 그 까닭으로), 443 이 아닌 https 포트를 짚고(사용자의 Squid 는 CONNECT 를 443·10443 에만 허락한다),
인증서 오류는 주 문서일 때만, 렌더러가 죽으면 지금 탭에. 사용자가 멈췄거나 다른 탐색이 앞선 것은 덮지 않는다. 전제 하나는 확인하지 않았다(함정
'오류 덮개의 전제'). 크로미움이 `ERR_UNSAFE_PORT` 로 막는 주소는 일반 까닭으로 떨어지고(경미 — 그대로 뒀다), 오류 페이지의 탭 이름은 WebView 의
'Webpage not available' 로 남는다.

**당겨서 새로 고침(`PullToRefresh`).** 페이지 맨 위에서 페이지를 아래로 당길 때만(`onOverScrolled` 가 맨 위 밀림을 알리는지가 전제 — 기기에서
확인하지 않았다).

**전체 화면**(사용자 결정 — 기본 켬).

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 설정 '화면 › 전체 화면'(`SettingsStore.fullscreen` — `commit`) | 끄면 예전 화면 그대로. 켜도 **TalkBack(터치 탐색)이 켜져 있거나 분할 화면·자유형 창이면** 예전 화면으로 그린다 — 가장자리 밀기를 낭독기로 찾을 수 없고, AOSP 12 는 그때 상태 표시줄을 늘 보인다 |
| 상태 표시줄만 숨기고(`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) 내비게이션 바는 남긴다, 노치 모드 `SHORT_EDGES` | 기본 행동이면 시스템이 상태 표시줄을 스스로 보이고 손가락을 알림 창으로 넘긴다(`DisplayPolicy.requestTransientBars` → `transferTouch`). 기본 노치 모드면 상태 표시줄을 숨겨도 노치 띠가 검게 남는다 |
| 막대(와 진행 막대)를 **페이지 위에 겹친다**(`BrowserFrame`) — 위 여백은 4dp + max(상태 표시줄, 노치) | 상태 표시줄의 높이는 숨어 있어도 `getInsetsIgnoringVisibility` 로 읽는다 — 보이고 숨을 때 페이지가 다시 배치되지 않고 스크롤 자리도 그대로다 |
| **화면 위 20% 의 띠에서 시작해 아래로**(터치 슬롭 넘고 세로가 우세)만 막대와 상태 표시줄을 함께 부른다(`EdgeSwipe` — 띠는 `FullscreenLayout.edgeBand`: 뿌리 높이의 20% 와 상태 표시줄·노치의 띠(모르면 24dp) 가운데 큰 것) | 띠가 상태 표시줄 높이만큼이면 실기기에서 그 띠의 밀기를 시스템이 먼저 가져가 상태 표시줄만 내려오는 일이 잦다(사용자). 대가: 막대가 숨은 동안 위 20% 에서 시작한 아래 끌기는 스크롤·당겨서 새로 고침이 아니라 막대다. 가져오는 순간 페이지에는 취소를 보내고 손을 뗄 때까지 아무것도 보내지 않는다. 띠 안의 두드림·옆·위로 밀기·두 손가락은 페이지의 것. `dispatchTouchEvent` 에서 본다(WebView 가 부모의 가로채기를 막는다). 상태 표시줄은 우리가 `show`/`hide(statusBars())` 로 함께 다룬다 — 시스템이 잠깐 보이는 막대는 앱에 '보이지 않음' 으로 알려지고(`InsetsPolicy.adjustVisibilityForTransientTypes`), 앱이 보이기를 청하면 그 '잠깐' 이 풀린다(`checkAbortTransient`) |
| 다시 숨는 때 — 막대 밖을 누르면 곧바로(그 누름은 페이지로 간다), 막대를 만지지 않은 채 **4초**(접근성의 '조작할 시간' 이 길면 그만큼) | 시스템의 잠깐 보이는 막대(2.25초)보다 길게 — 출구 나라를 읽고 엄지를 메뉴·주소창으로 옮길 틈 |
| 숨지 않는 때 — 주소창 초점, 찾기 줄, 창이 초점을 잃었을 때(메뉴·탭 목록·대화상자·길게 누르기 메뉴), 막대 몸짓 중, **새 탭의 시작 화면** | 붙잡힌 동안 4초마다 다시 본다(풀렸다는 알림을 놓쳐도 결국 숨는다) |
| 찾기 줄·받기 줄·알림 띠는 `TopStrips` 로 — 막대가 숨으면 화면 맨 위(노치만큼 띄운다), 나타나면 그 아래로 | 프록시 문제의 알림 띠는 늘 보인다 |
| 막대가 숨은 동안 3dp 진행 막대가 화면 맨 위에 | 페이지를 여는 중인지 보인다 |
| 앱을 떠났다 돌아오면 숨긴 상태(주소창을 고치던 중·찾기·시작 화면이면 보이는 채), 전체 화면 영상 동안만 노치 모드를 기본값으로(15 부터 targetSdk 35+ 는 `DEFAULT`·`SHORT_EDGES` 가 `ALWAYS` 로 읽혀 이 되돌림은 듣지 않는다 — 'Android 16 으로 올리며') | 회전은 이 화면이 스스로 받는다(`configChanges`) — 인셋을 다시 맞춘다. 전체 화면 영상 동안은 `SCREEN_ORIENTATION_SENSOR`(회전 잠금을 켠 폰에서도 영상을 가로로 — 영상의 방향은 스크립트 없이 알 수 없어 회전 잠금만 무시한다) |

**확인하지 못한 것** — **노치**(이 에뮬레이터에서는 노치 흉내를 켜지 않는다 — 함정 표. 사용자 폰(위쪽 물방울 노치)과 One UI 의 앱별 '카메라 컷아웃'
설정에서의 모습은 사용자가 볼 것), 전체 화면 영상 뒤, 알림 띠가 뜬 채, TalkBack, 분할 화면, 삼성 키보드. **알림 창을 내리려면 두 번 밀어야 한다**(첫
번째는 막대만 부른다). 스위치 제어처럼 터치 탐색이 아닌 접근성 도구는 가려내지 않는다.

**재생 중 화면 켜 두기**(사용자 결정). WebView 는 스스로 **소리가 나는 영상**만 붙잡는다(WebView 153: 소리 나는 영상은 켜 둠,
`<audio>`·음소거·음량 0·음소거 자동 반복 영상은 켜 두지 않음). 그 밖을 메운다.

| 무엇 | 왜 그렇게 했나 |
|---|---|
| 지금 탭에서 **소리가 나는**(음소거·음량 0 이 아닌) 미디어가 재생되는 동안 창에 `FLAG_KEEP_SCREEN_ON`(`ScreenWake`) — 영상이든 소리만이든 | 사용자의 말 그대로 '미디어'. 소리가 나는 재생은 사용자가 고른 것이다 — WebView 의 자동 재생은 사용자가 누르지 않으면 음소거여야 한다 |
| **전체 화면 영상**은 음소거여도 | 전체 화면은 보고 있다는 뜻이다 |
| 페이지 안의 음소거 영상은 켜 두지 않는다 | 저절로 도는 음소거 배경·미리 보기 영상이 화면을 붙잡지 않게(WebView 도 그렇다) |
| 지금 탭의 것만 | 뒤의 탭에서 나는 소리는 화면을 붙잡지 않는다(크롬도 보이는 탭만 — 기억으로 적었다, 확인하지 않았다) |
| 앱을 떠나면 저절로 | 창이 보일 때만 듣는 깃발이다. 걷을 때 시스템이 꺼짐 시간을 처음부터 센다(`ON_AFTER_RELEASE`) |

알아내는 길 — 통로의 실행 세계(`BridgeScripts.BRIDGE` — 모든 틀)가 미디어 이벤트(`play`·`playing`·`pause`·`ended`·`volumechange`·`emptied`·
`abort`·`error`)를 포착 단계로 창에서 받아, 그 틀에 재생 중인 요소가 있는지(소리가 나는가·영상인가)를 바뀔 때 알린다. 재생 중이면 **10초 고정
간격으로** 다시 본다(재생 중인 요소가 문서에서 떨어지면 멈춤 이벤트가 창까지 오지 않는다 — 이벤트마다 `setTimeout` 을 새로 걸면 반복 소리에서
다시 알림이 한 번도 나가지 않아 30초 만에 풀렸다). 문서가 떠나면(`pagehide`) 끝났다고 알리고, 뒤로 가기 캐시에서 돌아오면 다시 본다. 앱
(`PageBridge` → `PlayingMedia`)은 틀마다의 마지막 상태를 모으고 **30초 넘게 소식이 없는 틀은 잊는다**. 주 틀에 새 문서가 인사할 때·렌더러가 죽을
때·WebView 를 새로 만들 때·탭을 닫을 때 모두 잊고, 탭을 고를 때·앱으로 돌아올 때 다시 묻는다(`media-ask`). **페이지의 세계는 건드리지 않는다** —
그래서 문서에 붙지 않은 `new Audio()`, shadow DOM 안의 요소, Web Audio 는 모른다(그런 페이지에서는 WebView 의 기본만). 통로를 모르는 WebView 도
WebView 의 기본만. 확인하지 못한 것 — 실기기에서 꺼짐 시간이 실제로 지켜지는지, 유튜브 같은 실제 사이트, 앱을 5분 넘게 뒤에 둔 뒤, 다른
프로세스의 틀이 죽은 때, TalkBack. 유튜브 광고 가리기가 광고를 음소거한 동안은 소리 나는 재생이 아니다 — 화면 켜 두기가 풀린다(위 '유튜브 광고
가리기(B1)').

**항상 확대 허용**(사용자 결정 — 기본 켬). 사이트가 확대를 막는 길은 거의 뷰포트(`<meta name="viewport" content="… maximum-scale=1,
user-scalable=no">`)이고 **WebView 에는 크롬의 '확대/축소 강제 사용' 같은 설정이 없다** — 그래서 쪽의 meta 를 고쳐 쓴다(`PageScripts.FORCE_ZOOM`).

| 무엇 | 왜 그렇게 했나 |
|---|---|
| `user-scalable`(잠그는 값일 때)과 5배 밑의 `maximum-scale` 만 걷고 나머지는 그대로 다시 적는다 | 폭·처음 배율·최소 배율을 두면 페이지의 배치가 바뀌지 않는다. 상한 5배는 크롬의 기본값이고, 페이지가 더 크게 허락했으면 둔다 |
| 내용을 **Blink 의 `HTMLMetaElement::ParseContentAttribute` 와 같은 차례**로 쌍으로 나눠 읽는다 | 공백·쌍반점으로 가른 꼴, `=` 앞뒤의 공백, `width device-width` 를 Blink 가 읽는 그대로 남기려고. 잠그는 값의 판정도 Blink 를 옮겼다 — `user-scalable` 은 `yes`·`device-width`·`device-height`·절댓값 1 이상이 아니면 잠근다, `maximum-scale` 의 `no`·모르는 값은 0 |
| 주 틀에만, 쪽이 나중에 넣은 meta·바꾼 content 도 MutationObserver 로 | SPA 가 뷰포트를 다시 넣거나 바꾼다. 같은 요소를 1초 안에 열 번 넘게 고치게 되면 그 요소를 그대로 둔다(되잠그는 고리) |
| 쪽에서 보이지 않는 **실행 세계**에서(`addJavaScriptOnEvent` — 모르는 WebView 면 쪽의 세계에 `addDocumentStartJavaScript`) | 쪽의 스크립트가 고치는 코드를 보거나 바꾸지 못한다. DOM 은 함께 쓰므로 고친 meta 는 쪽에 듣는다 |
| `touch-action` 은 건드리지 않는다 | 지도·그림판처럼 쪽이 두 손가락 몸짓을 스스로 받는 곳이 그 CSS 로 브라우저의 확대를 막는다 — 풀면 쪽과 브라우저가 한 몸짓을 함께 먹는다 |
| 설정 '화면 › 항상 확대 허용'(`SettingsStore.forceZoom`, `commit`) | 끄면 다음에 여는 페이지부터 사이트의 설정을 따른다. **켜면 이미 연 페이지에도 곧바로**(돌아올 때 지금 페이지에 같은 스크립트를 한 번 돌린다) |
| 역슬래시를 쓰지 않는다 | 제어 문자는 `String.fromCharCode` 로 — 코틀린의 날 글자열·Git Bash 의 here-document 가 먹을 일이 없다 |

스크립트는 PC 의 node 24 로 따로 시험한다(뷰포트 문자열 — 시험대는 스크래치패드에만). 확인하지 못한 것 — 실제 사이트, 실기기, `touch-action`·두 손가락
`touchmove` 를 막는 사이트(풀지 않는다), 두 번 두드려 확대, TalkBack.

**접근성 메모.** 탭 단추 '탭 N개', 설정 칸의 이름(`labelFor`), 알림 띠·덮개·아래 알림 줄의 live region, 북마크·방문 기록 단추의 '선택됨'. TalkBack
으로는 아직 아무것도 확인하지 않았다.

## 함정 — 다시 밟지 마라

| 함정 | 내용 |
|---|---|
| MSE 로 받은 WebM 과 Cues | 유튜브의 WebM 머리(SeekHead)는 원본 파일의 Cues 자리를 가리키지만 재생기는 머리와 Cluster 만 SourceBuffer 에 넘긴다 — 가로챈 파일의 그 자리에는 Cluster 가 있다. `MatroskaExtractor` 의 기본(Cues 를 찾아 되감기)은 그 자리에서 Cluster 를 만나 다시 Cues 를 찾으러 가기를 끝없이 되풀이한다(예외도 없이 CPU 만 탄다). 차례로만 읽는 곳은 `FLAG_DISABLE_SEEK_FOR_CUES` 로 만든다(`Remux.newExtractor`) |
| `setContentView` 전의 `insetsController` | 안드로이드 12 는 장식 뷰가 없으면 NPE 로 앱이 뜨자마자 죽는다 — `window.decorView` 를 먼저 만든다(`Ui.edgeToEdge`) |
| 익명 객체 안의 같은 이름 | 중계의 소식을 받는 익명 객체 안에서 `onUpstream(event)` 를 부르면 바깥의 것이 아니라 **그 객체 제 자신**이 불린다 — 메인 스레드에 제 자신을 끝없이 다시 올렸다(ANR). 이름을 가른다(`handleUpstream`) |
| ListView 줄 안의 단추 | 줄 안의 `ImageButton` 이 초점을 받을 수 있으면 `ListView` 가 줄 누르기를 넘기지 않는다 — 줄에 `FOCUS_BLOCK_DESCENDANTS`(단추는 초점 없이도 눌린다) |
| lint `RequiresFeature` | 함수 앞의 `if (!isFeatureSupported) return` 을 lint 가 알아보지 못한다 — 부르는 곳을 `if (isFeatureSupported) { … }` 안에 둔다 |
| 코틀린의 겹치는 주석 | KDoc 안에 경로 무늬(슬래시·별표가 붙은 것)를 적으면 그 별표가 **안쪽 주석을 열어** 파일 끝까지 주석이 된다(코틀린의 블록 주석은 겹친다). 주석에 슬래시-별표·별표-슬래시를 쓰지 않는다 |
| `ACCESS_NETWORK_STATE` | 망 조건이 붙은 JobScheduler 일감은 targetSdk 34 부터 이 권한이 있어야 걸린다(lint 가 `MissingPermission` 으로 빌드를 멈췄다) |
| 다른 모듈의 속성은 스마트 캐스트가 안 된다 | `:core` 의 `Long?` 속성을 앱 모듈에서 `if (p.offset != null) … p.offset` 로 쓰면 컴파일러가 막는다 — 지역 변수에 받아 쓴다 |
| 실행 세계에도 쪽의 CSP 가 걸린다 | 쪽이 `default-src 'self'` 면 보이지 않는 세계의 `fetch(blob:)` 도 막힌다 — 그 쪽에서는 사용자가 고를 때만 쪽의 세계에 `createObjectURL` 갈고리를 건다 |
| 따로 만든 TS 조각의 연속성 카운터 | 조각마다 0 부터 다시 세는 TS 를 이어 붙여 읽으면 media3 의 TS 추출기가 그 끊김에서 읽던 PES 를 버려 **조각마다 끝 프레임을 잃었다**(168 중 158). 조각마다 따로 읽는다(받을 때 조각 크기를 적어 둔다) |
| 늘 바쁜 화면 | 같은 글이라도 1초마다 다시 넣으면 접근성 이벤트가 끊이지 않아 `uiautomator dump` 가 'could not get idle state' 로 빈손이다 — 글이 바뀔 때만 넣는다(받기 줄은 2초마다·바뀔 때만, 테더 화면도 같다) |
| 저장으로 넘긴 세션의 일꾼 | 저장으로 넘기며 세션의 일꾼을 먼저 닫아 마무리 쓰기가 거절됐다(`RejectedExecutionException`) — 닫기는 저장 일이 남은 쓰기를 기다린 뒤에 |
| 다시 불러온 쪽의 제목 | 다시 불러도 제목이 같으면 WebView 가 제목을 다시 알리지 않는다 — 다 열었을 때 WebView 에 묻는다 |
| 서비스 워커의 UA | `setUserAgentString`·`setUserAgentMetadata` 는 WebView 마다의 설정이라 **서비스 워커가 내는 요청에는 닿지 않는다**(서비스 워커 설정에는 UA 가 없다). 전용 워커는 쪽의 것을 물려받는다 |
| **포그라운드 서비스의 차례** | 요청마다 `startForegroundService` 를 부르는데 서비스가 **처음 한 번만** `startForeground` 를 부르고, 끝났다는 알림이 첫 요청이 닿기 전에 와서 서비스가 스스로 멈추면 `ForegroundServiceDidNotStartInTimeException` 으로 **앱이 죽는다**(몇 ms 에 끝나는 마무리). 서비스는 요청마다 곧바로 포그라운드에 서고, 첫 요청 전에는 멈추지 않고, **마지막 요청의 번호로** 멈춘다(`stopSelf(startId)`·`stopSelfResult`). 내려받기·녹화·뒤에서 재생·테더 서비스가 모두 이 차례다. 이 크래시는 시스템 dropbox(`dumpsys dropbox --print data_app_crash`)가 답했다(`logcat -c` 는 crash 버퍼도 지운다) |
| 닫히는 중계의 일꾼 | 설정을 바꿔 중계를 닫는 순간 열리던 터널이 내려간 일꾼 풀에 일을 넣다 `RejectedExecutionException` 이 일꾼 스레드 밖으로 나가 **앱이 죽었다**. 받는 고리·터널 잇기·절대 주소의 몸 보내기 세 자리에서 잡아 손님을 닫는다(시험이 재현한다) |
| 중계를 닫는 스레드 | `LocalRelay.close()` 가 열린 TLS 소켓을 그 자리에서 닫으면 close_notify(네트워크 쓰기)가 나가, 부른 쪽이 메인 스레드면 `NetworkOnMainThreadException` — **프록시 스위치를 끄면 앱이 죽었다**(`NetworkPath.restartRelay` 에서). 듣는 소켓만 그 자리에서 닫고 연결 끊기는 따로 된 스레드(`closer`)가 한다. 그러면 원격의 답이 끊기기 전에 올 수 있어 **닫힌 뒤 온 답(CONNECT 의 200·절대 주소의 응답)은 넘기지 않는다.** 시험 `닫기는_소켓을_부른_스레드에서_닫지_않는다`·`닫힌_뒤에_온_원격의_답은_넘기지_않는다` — 고침을 되돌리면 각각 깨진다. 같은 꼴(TLS 소켓을 메인 스레드에서 닫기)이 다른 곳에는 없다 — 받기·살피기의 취소는 깃발만 세운다 |
| 멈춘 WebView 는 다시 붙여도 그리지 않는다 | 화면에 없는 탭은 `onPause` 상태라 새 부모에 붙어도 그리지 않는다 — 빌려 온 동안 `onResume`, 돌려줄 때 `onPause` |
| **초점을 쥔 뷰를 떼면 주소창이 초점을 받는다** | 쪽을 누르면 WebView 가 초점을 쥔다. 그 WebView 를 떼면 안드로이드가 초점을 처음 만나는 '누르면 초점을 받는' 뷰에 넘기는데(`ViewGroup.removeViewInternal` 의 `rootViewRequestFocus` — 터치 모드에서도), 이 화면에서 그것이 주소창이다 — 주소창이 고치는 상태가 되고 새 탭의 주소도 그리지 못했다. 쪽을 떼는 곳은 모두 `detach`(초점을 먼저 거둔다)를 지난다. `clearFocus` 는 터치 모드에서 초점을 어디에도 넘기지 않는다(API 28 부터) |
| `CipherInputStream` | 끝의 채움 오류를 삼키는 구현이 있다 — 잠긴 조각이 틀린 열쇠로도 '풀렸다' 가 된다. `update`·`doFinal` 을 직접 부른다 |
| 이름의 번호 찾기 | `\d+(?=\D*$)` 가 `.mp4` 의 `4` 를 잡았다 — 이름에서 확장자를 뺀 부분에서 찾는다 |
| `stateDescription` | 두면 화면 낭독기가 기본 '켜짐/꺼짐' 대신 그것만 읽는다 — 켜짐·꺼짐을 앞에 함께 적는다 |
| 오류 덮개의 전제 | WebView 는 오류를 커밋보다 먼저 알린다고 보고, 오류 뒤 3초 안에 같은 주소가 커밋되면 WebView 의 오류 페이지로 친다 — 중계의 502 에서 본 순서이고 연결 오류·인증서 오류에서는 확인하지 않았다 |
| app 모듈의 JVM 시험 | `kotlin("test-junit")` 이 있어야 돈다(`kotlin("test")` 만으로는 러너가 붙지 않는다). org.json 은 그 시험에서 빈 껍데기(stub)뿐이라 JSON 을 맵·목록 나무로 바꿔 읽는다(`PickProtocol`) |
| 시험 이름 | 코틀린의 역따옴표 이름에 `·` 를 쓸 수 없다 |
| node 의 `vm` 과 견주기 | 쪽 스크립트를 node 24 로 따로 시험할 때 `vm` 에서 만든 객체는 원형(realm)이 달라 `assert.deepStrictEqual` 이 실패한다 — JSON 으로 견준다 |
| 도구의 줄 끝 | 이 저장소에는 CRLF 와 LF 파일이 섞여 있다. Edit 도구는 CRLF 를 지키지만 Write 는 LF 로 쓰고, 파이썬의 `write_text` 는 윈도에서 CRLF 로 쓴다 — 고침 스크립트는 바이트로 읽고 쓴다 |
| Git Bash 의 here-document | 파이썬 고침 스크립트를 here-document 로 넘기면 문자열의 `\r\n`·`\\r` 이 진짜 줄바꿈·CR 이 되어 파일이 깨진다 — 스크립트는 파일로 쓰고 돌린다 |
| 도구가 푸는 `\u` 이스케이프 | 편집 도구에 코틀린 글자열의 `\u202E` 를 그대로 적으면 도구의 JSON 이 그것을 풀어 **진짜 방향 제어 문자**가 소스에 들어간다(보이지 않는 글자). 이스케이프가 든 소스는 파이썬으로 고치고 역슬래시는 `chr(92)` 로 짓는다. 넣은 뒤 소스에 U+202A–202E·U+2066–2069·U+200E/200F 가 날것으로 있는지 훑는다. 저장소의 `.kt`·`.kts` 에는 보이지 않는 글자(유니코드 Cf — 방향 제어 문자·U+FEFF·소프트 하이픈)를 날것으로 두지 않고 이스케이프로 적는다(눈에 보이는 BMP 밖 글자 — 시험의 이모지 — 는 그대로) |
| Git Bash 의 grep 은 CR 을 숨긴다 | `grep -q $'\r'` 로 줄 끝을 쟀더니 CRLF 파일(`PageBridge.kt`)이 LF 로 나왔다 — MSYS 의 grep 이 줄 끝의 CR 을 떼고 본다. 줄 끝은 파이썬으로 바이트를 센다(`b.count(b'\r\n')`). 소스를 읽는 JVM 시험은 `\r\n` 을 `\n` 으로 맞춰 읽는다 |
| 노치 흉내 | 에뮬레이터에서 `cmd overlay enable com.android.internal.display.cutout.emulation.tall` 을 켜자 이 이미지의 SystemUI 가 `ScreenDecorations` 에서 되풀이해 죽었다 — **이 에뮬레이터에서 노치 흉내를 켜지 마라** |
| 숨은 쪽의 영상 | 안드로이드의 크로미움은 쪽이 숨으면 영상을 멈춘다 — 소리가 있어도, WebView 의 `onPause`, 창에서 떼기, 창이 보이지 않음(앱이 뒤로 감) 모두. 앱이 끌 길이 없다(브레이브는 명령줄 스위치로 끈다 — '브레이브와 견줌'). 뒤에서 재생을 잇는 탭은 셋을 다 피한다(아래 둘, '뒤에서 재생 잇기') |
| 보이되 그려지지 않는 WebView | 크로미움에 '보임' 인 채 창이 숨었거나 투명하면(alpha 0) 영상 프레임이 그려지지 않아 돌아오지 않고, 디코더가 막혀 파이프라인째(소리도) 선다. 사용자 기기에서는 끊긴 뒤 유튜브가 144p 로 다시 세웠다. 그동안 화면 밖에 그린다(`OffscreenDraw`) |
| 쪽을 잠깐이라도 숨기기 | 유튜브 모바일 웹은 `timeupdate` 마다 `visibilityState` 를 보고 숨었으면 재생기를 세운다 — 떼었다 곧바로 붙여도(보임 사건 둘, 90 ms 사이) 멈췄다. 같은 탭을 다시 고르기·밤 모드로 화면 다시 짓기가 그랬다(지금은 떼지 않는다). 유튜브는 보임 속이기로 그 틈을 모른다 — 다른 사이트는 여전히 안다 |
| 자동 밤 모드 | 시스템은 '자동' 밤 모드를 화면이 꺼진 동안 바꿔 두고 켜는 순간 알린다(`onConfigurationChanged`) — 뒤에서 재생하는 중에 화면을 켜면 그때 화면을 다시 지었다 |
| 창 보임을 `onResume` 에서 되돌리기 | 뒤에서 잇던 탭의 '창이 보인다' 를 `onResume` 에서 풀면 그때는 실제 창의 VISIBLE 이 아직 오지 않아 쪽이 한 번 숨고 재생이 멈췄다(에뮬레이터). 실제 창이 VISIBLE 을 알릴 때 푼다(`PageWebView.keepWindowVisible`) |
| 떼었다 붙인 WebView | 같은 WebView 를 떼었다 다시 붙이면 쪽이 숨었다 보여 재생이 멈춘다 — 뒤에서 잇는 탭은 떼지 않고 투명하게 둔다(`backstaged`) |
| WebView 에는 미디어 세션 API 가 없다 | `navigator.mediaSession` 이 undefined 다(WebView 153, 에뮬레이터) — 쪽이 건 제목을 받을 길이 없다. 유튜브 모바일 웹은 쪽 제목도 'YouTube' 뿐이라 화면의 제목 칸을 읽는다('뒤에서 재생 잇기') |
| 잠든 기기에 셸로 넣은 미디어 키 | 화면이 꺼진 채 `input keyevent KEYCODE_MEDIA_*` 를 넣자 앱이 ANR 로 잡혔다 — 잠든 동안 셸이 넣은 키(isInjected)도 앱에 넘어간다(PhoneWindowManager — 소스로 읽은 것). 진짜 이어폰 키는 미디어 세션으로 간다. 미디어 키는 화면을 켠 채 넣는다. 안드로이드 12 의 `cmd media_session dispatch` 는 'packageName may not be empty' 로 실패한다 |
| 떠 있는 고르기 메뉴의 차례 | 떠 있는 메뉴(FloatingToolbar)는 '늘 보임' 줄을 먼저, '넘침' 줄을 나중에 놓고 같은 갈래 안에서만 차례를 견준다 — `menu.add` 의 기본(넘침)으로 더한 '검색' 은 '복사' 와 같은 차례여도 맨 끝으로 갔고, 메뉴가 붐비면 ⋮ 안으로 숨는다. 크로미움의 줄처럼 `SHOW_AS_ACTION_ALWAYS` 로 둔다(lint `AlwaysShowAction` 은 그 자리에서 막았다) |
| 떠 있는 메뉴와 뒤로 가기 | 창은 기본 액션 모드만 뒤로 가기로 닫는다 — 떠 있는 고르기 메뉴는 그대로 두고 뒤로 가기가 화면으로 간다(쪽이 뒤로 넘어가거나, 돌아갈 쪽이 없으면 앱이 뒤로 갔다). 화면이 먼저 고르기를 푼다(`PageWebView.finishSelection`) |
| 숨은 영상과 물러서기 | 화면 밖 그리기는 청이 0.5초 끊기면 vsync 마다로 물러서는데, 쪽의 영상을 숨기면 청이 아예 없어(크로미움이 영상을 화면에 내지 않는다) 물러서기가 오히려 vsync 마다 그린다. 숨긴 것이 확인된 동안은 물러서지 않는다(`OffscreenDraw.requestsOnly`) |
| 쪽의 세계 스크립트가 나중에 부르는 것 | 문서 시작 스크립트가 쪽보다 먼저 돌아도, 그 안에서 **나중에**(쪽의 사건·갈고리에서) 부르는 내장 함수·원형 메서드(`Object.*`·`Array.prototype.*`·`RegExp.prototype.test`·`WeakSet.prototype.has`·`Function.prototype.apply`·`window.length` …)는 쪽이 그 사이 바꿔 둘 수 있다 — WebRTC 가드가 그렇게 뚫렸다. 쓸 것은 처음에 붙잡아 `Reflect.apply` 로 부르고, 설명 객체는 원형 없이 짓는다(`WebRtcGuardTest`) |
| 덮은 메서드와 상위 생성자 | 코틀린 하위 클래스가 덮은 메서드(`PageWebView.invalidate`)는 상위 생성자(WebView) 안에서도 불릴 수 있다 — 그때는 하위 클래스의 필드가 아직 초기화되기 전이라 널이 아닌 형의 필드도 실제로는 null 이다. 그런 필드는 널이 될 수 있는 형으로 두고 쓸 때 만든다(화면 밖 그리기) |
| 클립보드의 설명 | `getPrimaryClipDescription` 은 읽었다는 알림을 띄우지 않지만 이름표는 앱마다 제멋대로고(파일 이름·'url'·없음 — 에뮬레이터는 'host clipboard'), 글 분류는 끝나지 않을 수 있다(에뮬레이터 — 늘 '아직'). 분류만 믿으면 줄이 뜨지 않는다 — 모르면 줄을 두고 누를 때 본다 |
| 넘겨준 재생목록의 바탕 주소 | 재생목록·MPD 의 상대 주소(화질·조각·열쇠)는 **넘겨주기 뒤의 주소**에 대어 푼다 — 처음 주소에 대어 풀면 넘겨준 재생목록의 열쇠를 엉뚱한 곳에 청한다(그곳이 200 의 웹 페이지를 주면 '잠금을 풀 열쇠를 받지 못했습니다') |
| `java.net.URI.resolve` | RFC 2396 이다 — 질의만 있는 참조 `?k=1` 을 `…/x7/?k=1` 로 풀고(브라우저는 `…/x7/index.m3u8?k=1`) URI 가 받지 않는 글자(세로 막대·중괄호)가 든 참조에는 던진다. 재생목록·MPD 의 주소는 `UriRefs`(RFC 3986)로 푼다 |
| `shouldInterceptRequest` 의 머리 | 넘겨주기에는 불리지 않는다(처음 요청만). 머리에 쿠키·Origin·Sec-Fetch 는 없다 — 망이 그 뒤에 붙인다(크로미움 소스로 읽은 것). Referer 는 담긴다고 보았다 — 기기에서 재지 않았다('찾은 미디어·HLS·DASH' 의 '열쇠 받기') |
| 안드로이드의 java.util.regex 는 ICU 다 | `Matcher.reset` 이 입력을 `toString()` 으로 바꿔 ICU(네이티브)에 넘긴다 — `CharSequence` 감싸개로 읽기를 세는 시간 상한은 **JVM 시험에서만 듣고 기기에서는 듣지 않는다**(SDK 소스 `java/util/regex/Matcher.java`). 뜻도 조금 다르다(ICU 의 `\w` 는 유니코드). 쪽이 고를 수 있는 입력에 정규식을 쓰려면 되짚지 않는 엔진으로 — 목록의 정규식은 `ListRegex`(위 '광고 차단' 의 '시간 상한') |
| 빈 글에 맞는 것의 되풀이 | java.util.regex 는 되풀이 한 번이 빈 글로 끝나면 남은 최소 횟수를 채우지 않고 되풀이를 끝낸다(`(\b|.){2,}b` 가 `cB` 에 맞지 않는다) — 톰슨 NFA 와 뜻이 갈린다. 무작위 견주기가 찾았다 — `ListRegex` 는 그 꼴(빈 글에 맞을 수 있는 것의 `{2,}`)을 받지 않는다 |
| java.util.regex 를 기준으로 한 무작위 시험 | 기준 자신이 되짚다 멈춘다(무작위 무늬 하나에 10분 넘게). 기준에는 읽기를 세어 넘으면 던지는 입력(`ListRegexTest.Bounded` — 이 JVM 의 java.util.regex 는 `charAt` 으로 읽는다)을 주고 터진 것은 견주지 않는다 |
| 제곱 시간 시험과 벡터 `indexOf` | `String.indexOf(char)` 는 JVM 의 벡터 명령이라 이 PC 에서 10¹¹ 글자를 3초 안에 훑는다 — 머리 글자가 드문 조각으로 지은 제곱 시간 시험은 되돌려도 깨지지 않았다. 뒤 조각의 머리 글자가 자주 나오게(자리마다 견주게) 지어야 깨진다(폰은 훨씬 느려 드문 꼴도 드러난다) |
| 16바이트가 아닌 열쇠 | RFC 8216 은 16바이트지만 hls.js 는 긴 응답의 앞 16바이트로 재생한다 — '재생은 되는데 받기만 안 된다' 의 한 길. 너그럽게 읽은 후보는 첫 조각을 풀어 꼴이 맞을 때만 쓴다(`HlsKeys.pick`) — 확인 없이 쓰면 틀린 열쇠로 쓰레기 파일이 생긴다 |

## 갤러리 받기 — pixiv·hitomi

사용자 결정은 위 표의 '갤러리 받기'. 데스크톱 다운로더(`Repositories/iroiro-downloader` 의 옛 `extractors/pixiv.py`·`hitomi.py` — 지운 코드는 git
이력에 있다)의 동작을 옮기되 브라우저에 맞게 바꿨다. **새 의존 없음.** 사이트 로직은 `:core` 의 `sites/`(순수 JVM — 소켓을 열지 않고 앱이 주는
`SiteFetch` 로만 묻는다), 받기·목록·알림·묻기는 앱(`DownloadCenter` 의 `Kind.SET`·`GalleryRules`·`GalleryPrompt`·`DownloadSets`).

### 길과 들어가는 길

- **사이트에 묻는 것(JSON·JS)도 파일을 받는 것도 내려받기의 같은 길**이다 — `fetcher()`(= `NetworkPath.httpRoute()` — 중계, 예외 규칙의 주소는
  홉마다 바로), `headers()`(WebView 의 UA·그 주소의 쿠키·Referer), 넘겨주기의 쿠키는 WebView 로. 해석기에는 `siteFetch()` 를 준다(`getBytes` — 살피기·
  받기의 끈기, 2xx 밖은 `SiteHttpException` + 프록시가 만든 답인지). **세 번째 '바로 나감' 은 없다** — `GalleryWiringTest` 가 소스로 박는다
  (`HttpFetcher(` 는 `fetcher()` 하나, `GalleryResolver(` 는 `siteFetch(` 로만, 갤러리의 다른 파일은 망에 손대지 않는다).
- **pixiv 는 이 브라우저의 로그인 쿠키로** 묻는다(WebView 의 `CookieManager` — 우리가 대신 로그인하지 않고 비밀을 두지 않는다). 로그인하지 않으면
  전체 연령 작품만 받힌다.
- 들어가는 길 — ⋮ 메뉴(지금 탭의 **커밋된 주소**를 `SiteUrls.recognize` 가 알아볼 때만 한 줄: pixiv 작품 '이 작품 받기', pixiv 작가 '이 작가의 작품
  모두 받기', hitomi '이 갤러리 받기' — pixiv 는 SPA 라 `pushState` 이동도 커밋된 주소를 고친다), 고리·그림 고리를 길게 눌러 같은 줄(그 쪽을 열지
  않고 곧바로 묻는다). 알아보기는 http(s)·8 KB 이하만 묻고, 던지면 줄을 두지 않는다(메뉴를 여는 일이 앱을 내리지 않게).
- 묻는 차례(`GalleryPrompt`) — 같은 대상(`SiteTarget.key`)이 받는 중·멈춤이면 '이미 내려받기 목록에 있습니다 · 목록 보기', 다
  받았거나 받은 파일이 있는 채 그만둔 것이면 '이미 받은 적이 있습니다. 다시 받을까요?'(다시 받으면 같은 폴더에 ` (1)` 사본) → 취소할 수 있는 '목록을
  읽는 중…'(`DownloadCenter.planGallery` — `Inspection`) → 받을 수 없으면 '받을 수 없습니다' 와 까닭 한 줄 → 확인 창(제목, '작가: …', '파일 N개'
  또는 '작품 N개', '저장: Download/…/') → '받기 시작했습니다 · 목록 보기'. 받기를 누르는 사이 다른 탭에서 같은 것이 들어갔으면 넣지 않는다.
  해석기가 무엇을 던지든(앱 안의 결함까지) 살피기 일꾼을 죽이지 않고 '알 수 없는 까닭' 한 줄로 끝낸다.
- 일꾼 — 계획 읽기는 살피기 일꾼(둘·연결 8초·읽기 10초), 받기는 **갤러리 전용 일꾼 하나**(`web-download-gallery`) — 작가 받기는 몇 시간이 걸릴 수
  있어 보통 받기(둘)를 막지 않게, 같은 로그인으로 사이트에 몰아 묻지 않게(둘째 갤러리는 '기다리는 중').

### 사이트 로직(`:core` 의 `sites/`)

| 파일 | 무엇 |
|---|---|
| `Sites.kt`·`Gallery.kt` | 계약 — `Site`·`SiteTarget`(작품·작가·갤러리, 열쇠와 정규 주소)·`SiteUrls`·`SiteFetch`·`SiteHttpException`·`SiteException`(까닭 여섯)·`GalleryFile`·`GalleryUnit`(Direct·HitomiImage·PixivWork)·`GalleryPlan`·`GalleryResolver`(plan·expand·invalidate)·`GalleryCodec` |
| `UrlRecognizer` | 주소 알아보기 — 손으로 가른다(`java.net.URI` 에 맡기지 않는다) |
| `Json` | 상한을 둔 JSON 읽개·쓰개(`internal`) — 나무는 Map·List·String·Long·Double·Boolean·null |
| `SiteResponses` | 사이트의 답 가르기(HTTP 코드·HTML·JSON 실패 → `SiteException` 까닭) |
| `Pixiv` · `Hitomi` | 응답 해석·주소 셈·이름 |
| `SiteNames` | 저장 이름(서로게이트·NFC·`SafeFileName`)·화면 글·겹침 꼬리·'다시 다듬어도 그대로인가' |
| `PlanJson` | `GalleryCodec` 의 몸 — 판 번호가 붙은 JSON |

**pixiv — OAuth 가 아니라 웹 AJAX + 로그인 쿠키.** 봉투 `{"error":bool,"message":str,"body":…}`. 엔드포인트 다섯 — `ajax/illust/{id}`(작품)·
`/pages`(쪽들)·`/ugoira_meta`(우고이라 zip)·`ajax/user/{id}`(이름)·`ajax/user/{id}/profile/all`(작품 번호들). AJAX 의 Referer 는 그 쪽의 정규 주소
(`/artworks/{id}`·`/users/{id}`), 그림·zip(i.pximg.net)의 Referer 는 `https://www.pixiv.net/`.

- 작품: 우고이라(`illustType` 2)면 `ugoira_meta` 의 `originalSrc`(원본 크기 zip — 데스크톱의 600px 보다 낫다, 없으면 `src`), `pageCount` 1 이면
  `urls.original`, 여럿이면 `pages`. 단위는 `Direct`. 우고이라의 프레임 시간은 싣지 않는다(데스크톱도 그랬다 — zip 그대로).
- 작가: 이름 + `profile/all` 의 `illusts` **와 `manga`**(데스크톱은 `type=illust` 만 받아 만화가 빠졌다), 번호가 큰 것(새 것)부터, 같은 번호는 한 번.
  단위는 `PixivWork(id)` 이고 계획 때는 작품을 묻지 않는다(`fileCount` null — `expand` 가 그때 묻는다). 번호가 아닌 키가 섞이면 UNEXPECTED.
- 그림 주소는 받기 전에 본다 — https, 호스트가 `pximg.net` 이나 그 아래, 1,024자까지, 공백·제어 문자 없음(앱이 그 주소에 쿠키를 실을 수 있으므로
  남의 호스트를 들이지 않는다).
- 이름은 데스크톱과 같은 꼴 — 한 쪽 `{id}_{제목}{ext}`, 여러 쪽 `{id}_{제목}_p{000}{ext}`(0 부터 세 자리), 우고이라 `{id}_{제목}_ugoira.zip`.
  `ext` 는 주소 경로의 확장자(소문자, 없으면 UNEXPECTED). **제목을 먼저 120 바이트로 줄인다** — 다 붙인 뒤 `SafeFileName` 이 200 바이트로 자르면
  `_p000` 이 잘려 쪽끼리 이름이 겹친다. 폴더 `["Pixiv", <작가 이름 정리>]` — 다듬으면 비는 이름(점·빈칸뿐)이면 작가 번호.

**hitomi — 확장자와 서브도메인은 사이트의 스크립트와 실제 답을 따른다**(데스크톱 다운로더와 다르다).

- 확장자: **원래 gif 면 webp, `hasavif` 면 avif, 그 밖은 webp.** 갤러리 정보에는 `haswebp`·`hasjxl` 키가 **아예 없고**(파일마다
  `hasavif:1` 만), 사이트의 읽개(`reader.js` 의 `make_image_element`)는 `hasavif` 면 `<source type=image/avif>` 를, 그리고 **늘** `<img src=…webp>` 를
  싣는다. jxl 은 사이트가 싣지 않고 안드로이드 12 가 못 연다, 원래 확장자(jpg)는 CDN 이 주지 않는다. gif 를 webp 로 하는 것은 안드로이드 12 가
  움직이는 WebP 는 틀지만 움직이는 AVIF 는 확인하지 못해서다. 안드로이드 12 의 MIME 표(`framework.jar` 의 `res/android.mime.types`)에
  `image/avif`·`image/webp` 가 있다(에뮬레이터에서 확인) — 둘 다 그림으로 저장된다.
- 서브도메인: **확장자의 머리 글자 + (1 + m(g))** — avif `a1`·`a2`, webp `w1`·`w2`. 데스크톱은 늘 `a` 였다(avif 만 받아서 드러나지 않았다). 사이트의
  `common.js`(`subdomain_from_url`)와 gallery-dl 1.32 도 같다. 주소 `https://<서브도메인>.gold-usergeneratedcontent.net/{b}{g}/{hash}.{ext}`,
  `g = int(hash 끝 1자 + 그 앞 2자, 16)`(0..4095).
- gg.js: 작은 토큰 읽개로 **`m: function(g){…}` 의 몸만 문법대로** 읽는다 — `var o = N; switch (g) { (case N:)+ o = X; break; … [default: o = X;
  break;] } return o;`. 이름·공백·주석·속성의 차례·`}` 앞 `;` 생략은 상관없다. 같은 case 는 앞의 것(JS 의 switch 와 같다). 조건문·다른 변수·break
  없이 이어지는 case·4095 넘는 case·두 자리 넘는 값이면 UNEXPECTED(틀린 주소를 조용히 짓지 않는다). `s` 함수도 토큰으로 견준다, `b` 는 숫자 1~20자 +
  `/`(실측한 `b` 는 그 파일을 지은 유닉스 시각 — 매시 바뀌는 것으로 보인다, 한 번만 봤다). gg.js 는 `GG_TTL_MS`(30분) 동안 해석기 인스턴스에
  기억한다(받기는 하나씩, `invalidate` 는 받는 중에도 막히지 않고 그 사이 받은 것을 기억하지 않게 — 세대 번호). 계획 때는 gg.js 를 받지 않는다.
- 갤러리 정보: `var galleryinfo = {…}` — 머리를 공백에 너그럽게, 값 하나를 JSON 으로 읽고 끝에는 공백과 `;` 하나만(실측은 `;` 없음). 답의 `id` 가
  다르거나 해시가 64자 16진이 아니면 UNEXPECTED.
- 폴더 `["Hitomi", <작가들 ", " / 그룹들 / "unknown">, "{id}_{제목}"]` — 마지막은 80 코드 포인트로 **자른 뒤** 다듬는다. 파일 `{name 의 마지막 성분에서
  확장자를 뗀 것}.{ext}`, 이름이 없거나 다듬으면 비면 `001` 꼴 순번. **같은 갤러리 안에서 겹치면** ` (2)`·` (3)` — 대소문자와 NFC 를 무시하고
  견준다(공용 저장소는 대소문자를 가리지 않는다).
- Referer: 갤러리 정보와 그림은 `https://hitomi.la/reader/{id}.html`, gg.js 는 `https://hitomi.la/`.

**실패 가르기**(`SiteResponses`) — 404·410 → NOT_FOUND(gg.js 는 사이트의 부품이라 UNEXPECTED), 401 → LOGIN_REQUIRED, 403·JSON 대신 HTML → BLOCKED,
꼴이 다름 → UNEXPECTED, 상한을 넘음 → TOO_LARGE. **프록시가 만든 답과 429·5xx·망 오류는 같은 예외 객체 그대로** 던진다(사이트의 '없음' 이 아니고,
앱이 다시 해 볼지 정한다). pixiv 의 2xx 봉투가 `error:true` 면 메시지에 로그인·연령 제한 낱말(`ログイン`·`login`·`로그인`·`登录`·`R-18`·`年齢` …)이
보일 때 LOGIN_REQUIRED, 아니면 NOT_FOUND. `urls.original` 이 null 이면 LOGIN_REQUIRED, 연령 제한·로그인 전용 작품의 `pages`·`ugoira_meta` 가 '없음'
이어도 LOGIN_REQUIRED. 취소는 요청마다 그 앞에서 `InterruptedIOException`.

**주소 알아보기**(`SiteUrls.recognize`) — 스킴 http·https, 권한부에 `@`·백슬래시·`%`·대괄호·빈칸·비ASCII 가 있으면 null, 호스트는 정확히 `pixiv.net`·
`www.pixiv.net`/`hitomi.la`(대소문자 무시, `pixiv.net.evil.com`·끝의 점은 null), 포트는 없거나 기본값, 질의·조각은 버린다, 번호는 1~12자리(앞의 0 은
null). pixiv — `/artworks/{id}`, `/{언어}/artworks/{id}`, `/users/{id}`(+`/illustrations`·`/manga`·`/artworks`), `member_illust.php?illust_id=`,
`member.php?id=`, `member_illust.php?id=`(옛 작품 목록 → 작가). 북마크·소설·태그로 거른 목록은 null. hitomi — 첫 성분이 `galleries`·`reader`·
`doujinshi`·`manga`·`cg`·`gamecg`·`imageset`·`anime` 이고 둘째가 `….html`, `.html` 바로 앞의 숫자(앞이 `/` 나 `-` 일 때만).

**JSON·코덱·화면 글.** JSON 은 RFC 8259 를 엄격하게(같은 키는 뒤의 것, 정수는 Long — 넘치면 Double, 쓰개는 홀로 선 서로게이트를 `\uXXXX` 로).
계획의 글 `{"format":"iroiro-gallery-plan","v":1,"target":{…},"title","creator","folder","fileCount","units":[…]}` — 다시 읽을 때 모두 다시 본다(모르는
꼴·판·단위, 해시, 확장자, 주소, **폴더 성분과 파일 이름은 `SafeFileName` 이 다시 다듬어도 그대로인 것만** — `..`·구분자·제어 문자가 든 것은 받지
않는다, 앱이 그대로 경로로 쓴다). 화면 글(제목·작가)은 제어 문자를 빈칸으로, **방향 제어 문자(U+202E 등)를 빼고**(남의 제목이 확인 창의 뒤 글을
뒤집지 못하게), NFC, 300 코드 포인트.

| 상한 | 값 |
|---|---|
| 한 덩이(`FETCH_LIMIT`) | 8 MiB — 넘으면 TOO_LARGE |
| JSON(사이트) | 글자 8 Mi, 깊이 64, 글 하나 1 Mi 자, 값 50만(키도 센다), 숫자 64자 |
| JSON(계획) | 글자 32 Mi, 깊이 8, 글 하나 8 Ki 자 |
| 단위(`MAX_UNITS`) | 2만 — 작가의 작품·작품의 쪽·갤러리의 파일 |
| gg.js | 1 Mi 자·토큰 20만, case 0..4095, 값 두 자리 |
| 이름 | 파일·폴더 200 바이트(`SafeFileName`) + 겹침 꼬리, pixiv 제목 120 바이트, hitomi 폴더 제목 80 코드 포인트 |

### 받는 차례(`DownloadCenter.runSet`)

1. 계획 — 메모리(넣을 때 쥔 것), 없으면(앱이 다시 떴다) 곁의 `<번호>.json` 을 `GalleryCodec.decode`. 없거나 깨졌거나 다른 대상의 것이면 **실패**
   ('받을 목록을 읽지 못했습니다 — 원래 페이지에서 다시 받으세요').
2. 이어받을 자리 — 목록의 (단위, 파일 차례)와 곁의 기록의 마지막 일 가운데 앞선 것의 다음(`GalleryRules.resumeAt`). 받던 파일(감춘 항목)은 버리고
   처음부터. 수(받은 파일·건너뜀)도 기록과 견줘 큰 쪽.
3. **받기 한 번마다 해석기를 새로**(hitomi 의 gg.js 도 새로 읽는다).
4. 단위마다 `expand` → 파일을 하나씩: 응답이 2xx 면 그때 감춘 항목(`IS_PENDING`)을 '내려받기/<폴더>/' 에 만들고(`MediaStoreWriter.create(…, folder)` —
   성분마다 `SafeFileName.clean` 을 한 번 더, MediaStore 가 경로를 거절하면 `Download/` 바로 아래로), 그 URI 를 `pendingUri` 로 목록에 적고(고아
   정리가 지우지 않게), 흘려 쓰고, `Content-Length` 와 견주고, 드러내고, 곁의 기록에 `F` 줄. 형식은 확장자로(모르면 octet-stream — 이름은 그대로).
5. 파일마다 끊기면 **처음부터 세 번**(1.5초·4초 쉬며), 4xx(408·429 밖)는 한 번.
6. pixiv 작가 받기는 **작품과 작품 사이에 `PIXIV_GAP_MS`(0.6초)** 쉰다(데스크톱은 0.3초 — 같은 로그인으로 몰아 물으면 계정이 제한될 수 있다).
7. 다 받으면 '다 받음', 건너뛴 것이 있으면 '건너뜀 N개(로그인 필요 M개)'. 끝난(다 받음·그만둠·실패) 항목의 계획 파일은 버린다(기록은 남긴다).

| 실패 | 결정(`GalleryRules.onExpandFailure`·`onFileFailure` — 순수, 시험) |
|---|---|
| 사용자가 멈췄거나 그만뒀다(그때의 예외는 무엇이든) | 멈춤·그만둠(깃발이 정한다) |
| pixiv 작가 받기의 작품: NOT_FOUND·LOGIN_REQUIRED·EMPTY, 사이트의 404·410, 푼 파일이 0개 | **그 작품을 건너뛰고 센다**(로그인이면 따로 센다) |
| hitomi 그림이 404·410(사이트의 답) | `invalidate()` 뒤 그 단위를 한 번 더 풀어 **같은 파일을 다시** — 그래도 없으면 건너뛰고 센다 |
| 그 밖의 파일이 404·410(pixiv 의 쪽 등) | 건너뛰고 센다 |
| 프록시(중계·Squid)가 만든 404 등 | 사이트의 '없음' 이 아니다 — 멈춤 |
| BLOCKED·UNEXPECTED·TOO_LARGE, 403 등 거절, gg.js 를 읽지 못함, 세 번 실패한 망·5xx | **멈춤**(그 까닭으로 — 다음 단위도 같을 것) |
| '내려받기' 에 쓰지 못함 | 멈춤('공간을 확인한 뒤 이어받으세요') |
| 앱이 죽음 | 다음에 뜨면 '멈춤(앱이 멈춰 중단됐습니다)' — 보통 파일·미디어와 같다 |

까닭의 글(`GalleryRules.reason`)은 사이트의 까닭마다 다르고(로그인은 pixiv 면 '이 브라우저에서 pixiv 에 로그인한 뒤 다시 해 보세요'), 망은 `NetErrors`,
사이트가 준 기계 말(`detail`)은 싣지 않는다.

### 그만두기·지우기·저장 꼴·목록과 알림

- **'그만 받기'**(받는 중·기다리는 중) — 받던 파일(감춘 것)만 지우고 **드러낸 파일은 남긴다**(그래서 보통 파일의 '받기 취소(지움)' 와 이름이 다르다 —
  목록·알림 모두, 묻지 않는다). **'받은 파일까지 지우기(N개)'**(받는 중이 아닐 때 — 받은 것이 있을 때만) — 한 번 묻고 곁의 기록의 URI 를 모두 지우고 목록에서
  뺀다(이 앱이 만든 항목만 지워진다 — 사용자가 옮겼거나 다른 앱이 바꾼 것은 못 지운다, 지운 수를 알린다). 비게 된 폴더는 남는다(앱이 지울 수 없다).
  '목록에서 빼기(파일은 그대로)' 는 곁의 계획·기록을 버린다. 실패·그만둔 갤러리는 '다시 받기' 로 잇지 않는다(받은 파일이 겹친다 — 원래 페이지에서 다시).
- `downloads.json` 의 항목: `kind: "SET"`, `url`·`pageUrl` = 대상의 정규 주소('원래 페이지 열기'), 제목, `userAgent`, `pendingUri`, `savedUri`·
  `savedMime` = 첫 파일('첫 파일 열기'), `received`, `note` = 건너뛴 수, 그리고 `set: {site, key, title, creator, folder[], units, fileCount(-1 = 모름),
  unitsDone, fileIndex, filesSaved, skipped, skippedLogin}`. 모르는 사이트 이름이면 그 항목을 버린다.
- `noBackupFilesDir/download-sets/<번호>.json` — 계획 전체(뒷정리 일꾼이 임시 파일 → fsync → 이름 바꾸기). `<번호>.saved` — **덧붙이기만 하는 기록**:
  `F\t단위\t차례\tURI`(드러낸 파일)·`S\t단위\t차례\t0|1`(건너뜀 — 차례 -1 은 작품 통째, 끝은 로그인 때문인가). 이어받을 자리·수와 '받은 파일까지
  지우기' 의 원본. 줄마다 fsync 하지 않고 끝이 잘린 줄은 버린다. 앱이 뜰 때 목록을 제대로 읽었으면 목록에 없는 번호의 곁의 파일을 치우고, 새
  갤러리를 넣을 때 그 번호에 남은 것을 먼저 비운다(목록이 깨져 번호가 다시 셌을 때 남의 기록을 읽지 않게).
- 목록(`DownloadsActivity`) — 보통 내려받기 사이에 한 줄: 제목 / 'n / m개'(작가 받기는 '작품 a / b · 파일 n') · 크기, 멈춤이면 '멈춤 — 까닭 · 진행', 다
  받으면 '다 받음 · 파일 n개 · 크기 · 건너뜀 …' / 저장 자리. 누르면 자세히와 할 수 있는 일(`GalleryRules.actions` — 일시 정지·그만 받기 / 이어받기 /
  첫 파일 열기 / 원래 페이지 열기 / 목록에서 빼기·받은 파일까지 지우기(N개)), 길게 누르면 그 메뉴.
- 알림(`DownloadService` — 번호를 새로 두지 않았다: 1 과 1000 + 번호) — 받는 중이면 제목·단위로 센 막대·'n / m개'·'일시 정지'·**'그만 받기'**, 다 받으면
  '다 받았습니다 — 파일 n개 · 건너뜀 …'(누르면 첫 파일), 멈추면 까닭과 '이어받기'·'원래 페이지 열기'(로그인·봇 확인을 그 쪽에서 푼다).

### 시험

`:core` — `SiteUrlsTest`(적대적인 호스트·권한부)·`JsonTest`(퍼저)·`PixivTest`·`HitomiTest`(주소 대조값 — 파이썬으로 따로 셈)·`SiteNamesTest`·
`GalleryCodecTest`(깨졌거나 모르는 꼴)·`GalleryResolverTest`(가짜 `SiteFetch` — LOGIN_REQUIRED 의 길들, 프록시의 답·429·5xx·IOException 은 같은 객체로,
취소, gg.js 의 기억·TTL·invalidate·거꾸로 간 시계, 여러 일꾼이 함께 풀어도 gg.js 는 한두 번, 상한)·`SiteFuzzTest`. 앱 — `GalleryRulesTest`·
`DownloadSetsTest`·`GalleryWiringTest`. 시험 자료는 모두 합성이다. `runSet` 의 차례는 MediaStore·WebView 쿠키에 묶여 JVM 시험이 없다 — 판단은 모두
`GalleryRules` 로 떼어 시험한다.

### 확인한 것·확인하지 못한 것

사이트의 실제 답은 PC 의 파이썬으로 읽기만 했다(로그인 없이 — 받은 제목·이름·태그·번호는 시험에도 저장소에도 넣지 않는다). 아는 것 — pixiv 의 없는
작품과 우고이라가 아닌 작품의 `ugoira_meta` 는 **404 + `error:true` 봉투**(메시지는 Accept-Language 의 언어), `ajax/illust/{id}` 의 `urls.original` 은
`https://i.pximg.net/img-original/img/…/{id}_p0.png` 꼴, `profile/all` 의 `illusts`·`manga` 는 번호 → null 맵. hitomi 의 `galleries/{id}.js` 는 머리가 정확히
`var galleryinfo = ` 이고 **끝에 `;` 가 없다**, 파일마다 `hash`·`width`·`height`·`name`·`hasavif` 뿐. `gg.js` 는 `max-age=3600`. 그림은 `Range: bytes=0-0`
으로 avif@`a(1+m)`·webp@`w(1+m)` 만 206 이었다(webp@`a(1+m)` 은 404 — 내용은 받지 않았다). 에뮬레이터에서 pixiv 작품 받기(원본과 바이트까지 같다)·시간
초과로 멈춘 뒤 이어받기·다시 받기 물음·길게 누르기(작품·작가)·받은 파일까지 지우기를, hitomi 는 확인 창까지(그림 호스트에 닿지 않게 취소) 봤다.

**확인하지 못한 것.** 받는 중의 사용자 일시 정지·이어받기, 앱을 죽인 뒤의 '멈춤' 과 이어받을 자리, 그만 받기, 로그인한 pixiv(연령 제한·로그인 전용
작품의 실제 답과 낱말, 우고이라의 `ugoira_meta` 정상 답, 요청이 잦을 때 — `error:true` 봉투면 지금은 NOT_FOUND 로 읽힌다, AJAX 에 Cloudflare 확인이
끼는지), 작가 받기를 실제로 돌리기·건너뛰기, hitomi 의 실제 받기(규칙상 하지 않는다 — `hasavif` 0 인 파일이 webp 로 나오는지, gif 의 webp 가
움직이는지, gg.js 가 바뀐 뒤 옛 주소가 얼마나 받히는지와 다시 셈하기), 실기기, 다크 모드, TalkBack.

### 함정·위험 — 갤러리의 것

| 함정 | 내용 |
|---|---|
| 두 칸의 차례 | 받는 일꾼은 파일 차례를 먼저 쓰고 단위를 나중에 쓴다, 목록을 적는 일꾼은 단위를 먼저 읽는다 → 적힌 자리가 실제보다 앞서지 않는다(앞서면 이어받을 때 파일을 건너뛴다. 뒤처진 것은 곁의 기록이 바로잡는다) |
| hitomi 의 서브도메인 | 확장자를 따른다(webp → `w`, avif → `a`) — 늘 `a` 면 webp 가 하나도 안 받힌다 |
| pixiv 의 긴 제목 | 다 붙인 뒤 자르면 `_p000` 이 잘려 쪽끼리 겹친다 — 제목을 먼저 줄인다 |
| 남의 제목 | 방향 제어 문자를 빼지 않으면 확인 창의 뒤 글이 뒤집힌다 |

- **위험** — hitomi 모듈이 공개 저장소에 든다(2026-03 FAKKU 의 DMCA 통지가 gallery-dl 의 hitomi 추출기와 안드로이드 앱까지 겨눴다 — 아래 '데스크톱
  다운로더에서 옮기지 않은 것', 사용자가 알고 정했다). pixiv 는 같은 로그인으로 자동 요청을 한다(계정 제한 가능성 — 작품 사이 0.6초). 사이트가 바뀌면 빌드해 다시 깔아야
  한다.

## 테더링

따로 된 앱이던 `Repositories/usb-tether`(앱 이름 'Hotspot', 패키지 `com.example.usbtether`)를 Web 에 합쳤다. 목적은 **'앱 하나 줄기'**(사용자), 인증
없는 LAN 서버는 'Wi-Fi Direct 로 붙은 기기만 닿으니 문제 없다'(사용자) — **인증은 더하지 않고 그 전제를 코드로 단단히 한다**(따로 된 프로세스·주
프로세스에서만 브라우저를 세움·알림 번호·새 의존 없음·`Socket(Proxy.NO_PROXY)`·손님 인터페이스 검사·파일 서버의 `Host` 검사 — 아래).

### 무엇을 하나

폰이 **Wi-Fi Direct 그룹 오너**(GO — 폰은 `192.168.49.1`)가 되고, 붙은 기기(손님 — 사용자의 PC, `192.168.49.2`–`254`)가 폰의 회선을 쓴다.
켜기·끄기는 **하나**다 — 서버 셋과 핫스팟을 함께 켜고 함께 끈다(사용자 결정).

| 서버 | 포트 | 무엇 |
|---|---|---|
| SOCKS5(RFC 1928) | **1080 고정** | CONNECT·**UDP ASSOCIATE**. PC 는 tun2proxy 로 트래픽을 통째로 보낸다(아래 'PC 쪽') |
| HTTP 프록시 | **8282**, 차 있으면 8283…8291 | `CONNECT`(터널)와 절대 URI 전달. 시스템·앱의 프록시 설정(이 PC 의 `HTTP_PROXY=192.168.49.1:8282`) |
| 파일 서버 | **8080**, 차 있으면 8081…8089 | 폰의 폴더 하나(SAF)를 손님의 브라우저로 — NAS(File Station) 꼴의 화면: 폴더 나무·목록/격자·섬네일·보기·zip 받기·올리기 대기열, '관리 허용' 이면 폴더 만들기·이름 바꾸기·옮기기·복사·휴지통(아래 '서버의 세부') |

셋 다 `0.0.0.0`(실기기에서는 듀얼스택 `::`)에 묶고 accept 직후 손님을 거른다. 화면·알림은 **실제로 묶인 포트**를 보인다.

- **SOCKS5 는 다른 포트로 옮기지 않는다** — 차 있으면 SOCKS5 는 서지 않고 화면이 그 포트를 이 폰의 다른 앱이 쥐었다는 것과 '그 앱을 멈추고
  테더링을 껐다 켜라' 를 보인다(어느 앱인지는 안드로이드가 알려 주지 않는다 — 테더링은 켜진 채다). 옮기고 PC 의 런처가 SOCKS5 인사에 답하는 첫
  포트를 고르게 하면, 폰의 악성 앱이 1080 을 먼저 잡고 `05 00` 만 답해 **PC 트래픽 전부**를 지나게 할 수 있다 — 보이는 것은 평문과 TLS 의 SNI 이고,
  PC 가 믿는 인증서가 없으니 TLS 를 벗기지는 못하지만 끊거나 엉뚱한 곳으로 잇거나 평문을 바꿀 수 있다. 그래서 런처도 1080 만 쓴다(인자로만 바꾼다).
- **HTTP·파일은 기본 포트가 차 있으면 다음 포트로 옮겨 선다**(열 개까지 — 사용자 결정, 위 '사용자 결정'). 열이 모두 차 있을 때만 '포트 8282–8291번을
  모두 다른 앱이 쓰고 있습니다'. **남는 위험(사용자가 감수했다)**: PC 는 `HTTP_PROXY=…:8282` 를 고정으로 쓰므로 이 폰의 다른 앱이 8282 를 먼저 쥐면
  (0.0.0.0 에 묶으면 핫스팟의 손님도 그 앱에 닿는다 — 손님이 핫스팟으로만 붙는다고 막히는 틈이 아니다) PC 가 그 앱을 프록시로 써 손님 거르기도 없이
  평문과 CONNECT 의 호스트를 보이고, 8080 을 쥔 앱은 익숙한 주소로 가짜 화면을 띄워 올린 파일을 가져갈 수 있다. 막는 것은 화면·알림의 실제 포트와
  '기본 포트가 아니면 PC·손님의 설정을 그 포트로 고치라' 는 설명뿐이다.
- **바깥 연결은 모두 이 폰이 새로 연다**(`Outbound` — `Socket(Proxy.NO_PROXY)`, UDP 는 `DatagramSocket`). 그래서 **IP·TCP 머리 수준에서는** 폰이
  만든 패킷이다(TTL·TCP 옵션·MSS). 내용은 손대지 않고 지나가므로 PC 쪽 지문(TLS ClientHello 의 JA3/JA4·User-Agent·HTTP/2 SETTINGS·OS 별 트래픽
  꼴)은 그대로다 — 맞는 말은 '통신사가 구별할 수 없다' 가 아니라 '머리로 보는 테더링 탐지에는 걸리지 않는다' 이고, 그 경계는 재지 않았다(**이
  문장을 '폰 자신의 트래픽과 구별되지 않는다' 로 바꿔 적지 마라**). 통신사 약관에 걸릴 수 있다 — 사용자 책임.
- **프록시 밖이다.** 손님의 트래픽은 브라우저의 프록시를 거치지 않고 시스템의 기본 망(모바일 데이터, 또는 폰이 Wi-Fi 에 붙어 있으면 그 Wi-Fi —
  코드는 ConnectivityManager 를 쓰지 않는다)으로 바로 나간다. 이 앱의 **'바로 나감' 예외 둘째**다. 브라우저 자신의 요청과는 섞이지 않는다 — 다른
  프로세스이고, 손님은 루프백(브라우저의 중계 `127.0.0.1`)에 닿지 못한다(아래 '목적지 거르기'). 연결 상태 창이 두 예외를 늘 적는다
  (`status_tether_note`).

### 구조 — 모듈과 프로세스

| 어디 | 무엇 |
|---|---|
| `:tether`(순수 JVM, 패키지 `tether.core`) | 안드로이드에 기대지 않는 것 — 서버 셋의 규약(`TcpServer` 몸통 + `Socks5Server`·`HttpProxyServer`·`FileServer`), 거르개(`PeerFilter`·`DestinationFilter`·`IpAddress`), `Outbound`·`ThreadBudget`·`ConnectionRegistry`·`DeadlineInputStream`, 파일 서버의 조각(`ByteRange`·`Json`·`SharedPaths`), `Passphrases`·`PassphraseVault`, 그룹 판정 `GroupWatch`, 알림·기록 묶기(`ChangeCoalescer`·`LogThrottle`), 파일 서버의 관리 기능·휴지통·zip 의 차례(`SharedOps`)·JSON 읽개(`JsonReader`)·섬네일 캐시(`ThumbnailCache`). 로그는 주입한 `TetherLog`(앱이 `android.util.Log` 로 잇는다). **기계 말만 낸다**(`StartError`·`PeerRejection`·`Passphrases.Problem`) — 글은 앱이 고른다 |
| 앱 `tether/`(모두 `:tether` 프로세스) | `TetherService`(specialUse 포그라운드 서비스 — 떠 있는 것이 곧 테더링: 서버 셋·핫스팟·알림) · `TetherActivity`(화면 — 켜기·끄기 하나) · `HotspotStartActivity`(타일의 트램폴린) · `HotspotTileService`(퀵 설정 타일 '테더링') · `TilePreferencesActivity`(타일을 길게 누를 때 시스템 UI 가 여는 작은 화면 — 내보냄, 테더 화면만 연다) · `WifiHotspot` · `TetherState`(그 프로세스의 정적 상태·듣개) · `TetherSettings`·`TetherSecret` · `SharedFolder`(SAF — `DocumentsContract`, 섬네일) · `TetherPermissions` · `TetherTexts` · `assets/tether/fileman.html`·`.js`·`.css` · `res/values/strings_tether.xml` |

- 구성 요소 다섯(화면·트램폴린·서비스·타일·타일을 길게 누를 때의 작은 화면)은 **모두 `android:process=":tether"`** — 테더의 상태(`TetherState`)가 그
  프로세스의 정적 필드라 하나라도 다른 프로세스면 늘 처음 값을 본다. 서로는 브로드캐스트가 아니라 **같은 프로세스의 듣개**로 알린다(내보낸 수신기의
  틈이 없다). 상태가 바뀌었다는 알림(`TetherState.changed`)은 250 ms 안의 것을 한 번으로 합친다(`ChangeCoalescer` — 첫 알림은 곧바로, 마지막 상태를
  놓치지 않는다). 연결마다 바뀌는 수는 알리지 않고 화면이 1초마다 읽는다(거절이 몰릴 때 메인 스레드를 밀지 않게).
- **`WebApp.onCreate` 는 주 프로세스에서만 브라우저를 세운다**(`Application.getProcessName() == packageName`). 테더 프로세스에서는
  `WebView.disableWebView()`만 하고 돌아간다 — 특히 `DownloadCenter.sweepOrphans` 를 돌리지 않는다: 그것은 '목록에 없고 이 프로세스가 뜨기 전에 생긴'
  감춘 '내려받기' 항목을 지우므로, 브라우저가 합치기·blob 받기를 하는 중에 테더 프로세스가 뜨면 **받는 중인 파일을 지웠을 것이다**.
- **`WebApp.of()` 가 테더 프로세스에서 불리면 던진다**(`check(isMainProcess)`) — 설정·프록시 계정·중계(그 안의 `Authenticator.setDefault`·WebView 의
  `ProxyController`)·내려받기·라이브러리·광고 차단이 모두 그 하나의 길로만 선다. 테더의 코드는 그것을 부르지 않는다(`TetherIsolationTest` 가
  소스의 import 와 매니페스트의 프로세스·내보냄을 본다). 매니페스트에 ContentProvider·BroadcastReceiver 가 없다 — 테더 프로세스가 뜰 때 도는 브라우저의
  코드는 `WebApp` 의 생성과 위의 가르기뿐이다.
- **경계는 UID 다**(프로세스를 나눠도). 같은 UID 라 테더 프로세스도 앱의 파일(`shared_prefs/web.xml`·쿠키·`browser.db`)과 기기 키(프록시 비밀번호의
  `web-proxy-password`)를 **쓸 수 있다**. 권한·SAF 권한·망 정책(데이터 사용량·배터리 예외)·알림 번호 공간도 함께다. 나눠서 줄어든 것은 결함·메모리
  고갈이 서로 옮는 것, 프로세스 전체 설정이 서로 닿는 것, 테더 프로세스의 메모리에 프록시 계정·중계·WebView 가 올라오는 것이다. **테더의 코드에 '앱
  자료 폴더의 파일 읽기·쓰기' 류 결함이 생기면 곧 프록시 계정이 위험하다 — 파일 서버를 `java.io.File` 기반으로 바꾸지 마라.**

### 손님 거르기·목적지 거르기·Host

**`PeerFilter` — 넷을 본다**(accept 직후, 손님의 주소는 기록하지 않는다. 넷째는 모듈에만 있고 앱에서는 꺼져 있다):

1. 연결이 향한 로컬 주소가 **그룹 오너 주소 `192.168.49.1`** 인가 — 핫스팟이 꺼져 있으면 그 주소가 어느 인터페이스에도 없으므로 어떤 연결도
   통과하지 못한다.
2. 상대가 **`192.168.49.2`–`254`** 인가 — `.1`(그룹 오너 = 이 폰)은 폰 안의 다른 앱이다(커널이 출발 주소로 같은 인터페이스 주소를 고른다 —
   에뮬레이터에서 그랬다, 실기기에서는 확인하지 않았다). 다른 앱이 이 프록시로 제 `INTERNET` 권한을 세탁해 VPN 방화벽·UID 별 데이터 제한을 비켜 가지
   못하게.
3. **이 앱의 그룹이 떠 있고, 그룹 오너 주소가 붙은 인터페이스가 정확히 그 그룹의 인터페이스인가.** 그룹의 인터페이스 이름은 그룹이 선 것을 확인할
   때 `WifiP2pGroup.getInterface()` 로 받아 `TetherState.groupInterface` 에 둔다. 그룹이 없으면 `NO_GROUP` 으로 거절한다 — 다른 앱(옛 테더 앱 등)의
   Wi-Fi Direct 그룹도 같은 대역을 쓴다. 조회는 `NetworkInterface.getByInetAddress`(매핑을 벗긴 IPv4 로, 시험은 조회를 주입한다)이고 답을 **2초**
   기억한다(그룹의 인터페이스가 바뀌면 곧바로 다시 — tun2proxy 는 페이지 하나에 연결 수십 개를 한꺼번에 연다). 1·2 만으로는 폰이 붙은 **다른 Wi-Fi 의
   DHCP 가 192.168.49.0/24 를 쓰고 폰에 `.1` 을 주면** 그 LAN 의 기기가 통과했다. 조회가 실패하거나 이름이 다르면 거절하고 화면의 '거절한 연결' 줄이
   두 이름을 보인다. 에뮬레이터(API 31)의 그룹은 `p2p-wlan0-0` 이었다 — 사용자 폰의 이름은 상태 줄('핫스팟 켜짐 · … · 이 폰은 192.168.49.1(이름)')에
   보인다.
4. **(모듈에만 — 앱에서는 꺼져 있다) 연결이 들어온 망** — 받은 소켓의 SO_MARK 아래 16비트(netd 의 netId)가 로컬 망 99 인가. 1·3 은 연결이 **향한**
   주소와 그 주소가 붙은 인터페이스를 볼 뿐 **들어온** 인터페이스를 보지 않는다. 그래서 폰이 남의 Wi-Fi(STA)에 붙은 채 핫스팟을 켜면, 그 Wi-Fi 쪽
   기기가 `192.168.49.1` 로 가는 경로를 폰의 Wi-Fi 주소로 넣고 출발 `192.168.49.77` 로 SYN 을 보내 1–3 을 다 지난다(커널의 rp_filter 가 꺼져 있으면 —
   에뮬레이터는 모두 0. 답은 `tcp_fwmark_accept=1` 로 그 Wi-Fi 로 나가므로 그 Wi-Fi 의 공유기를 쥔 공격자에게 닿는다). 막으려면 SO_MARK 를 읽어야
   하는데 **일반 앱이 읽을 공개 API 가 없다**: `Os.getsockoptInt` 는 `@SystemApi(client = MODULE_LIBRARIES)` 라 SDK 에 없어 컴파일되지 않고, 반사로
   부르면 숨은 API 목록에서 `MAX_TARGET_O` 라 targetSdk 36 에서 막힌다(AGP lint 의 `private-apis.txt` 로 확인). `Os.getsockoptTimeval`(API 29)은 timeval
   이 아닌 옵션에 엉뚱한 값을 준다고 문서에 있고, 손님의 IP 목록(`WifiP2pDevice.getIpAddress()`)은 API 35 다. 그래서 판정(netId 99 — AOSP 의
   `INetd.LOCAL_NET_ID`, 기기에서 확인하지 못했다)은 `PeerFilter` 에 읽는 함수를 주입하면 켜지게 두고 시험했으며 앱은 넣지 않는다. 켜려면 NDK(새 빌드
   의존)가 든다 — '사용자가 정할 것'.

**파일 서버의 `Host` 검사.** `Host` 머리가 `192.168.49.1`(포트가 붙든 말든 — 옮겨 선 포트도)이 아니면 **421** 과 까닭을 적은 본문으로 거절한다.
머리가 없거나 둘이어도 거절. 손님이 192.168.49.1 에 프록시 없이 바로 닿는 구성(tun2proxy 의 `--setup`)에서는 손님 PC 의 브라우저가 연 **남의 웹
페이지**가 DNS 재바인딩으로 이 서버를 같은 출처처럼 불러 공유 폴더를 읽고 쓸 수 있다고 보았다(추론 — 브라우저가 사설망 요청을 막으면 막힌다).
재바인딩한 요청에는 공격자의 이름이 `Host` 로 실린다. 폼 전송(GET/POST 만)과 다른 출처의 PUT(미리 묻기 — 이 서버는 OPTIONS 에 404)은 원래 막혀 있다.

**`DestinationFilter` — 프록시가 잇지 않는 곳**: 고정 대역(IPv4 `0/8`·루프백·링크 로컬·RFC 1918·CGNAT `100.64/10`·멀티캐스트·`240/4`(제한
브로드캐스트 포함)·문서용·IETF 프로토콜용 `192.0.0.0/24`(464XLAT 의 CLAT 이 `192.0.0.4` 에 있다)·벤치마크 `198.18.0.0/15`, IPv6 `::`·`::1`·
`fe80::/10`·ULA `fc00::/7`·멀티캐스트·문서용 `2001:db8::/32`, IPv4 를 실은 꼴(`::ffff:` 은 벗겨 다시 보고, `::/96`·NAT64 `64:ff9b::/32`·6to4
`2002::/16` 은 앞머리째)), **폰 자신의 모든 인터페이스 주소**(셀룰러가 준 공인 IPv4·IPv6 로 붙으면 폰의 서비스에 닿았다), **LAN 쪽 인터페이스의
on-link 접두**(`wlan`·`eth`·`p2p`·`swlan`·`ap`·`softap`·`rndis`·`usb`·`ncm`·`bt-pan`·`aware` 로 시작하는 이름 — 집의 공인 IPv6 /64 의 NAS·프린터가
고정 대역에 걸리지 않았다. IPv6 은 /48, IPv4 는 /8 보다 넓은 접두는 버린다). 셀룰러(`rmnet`·`ccmni` 등)의 접두는 막지 않는다 — 그 /64 는 이 기기
하나의 것이고 막으면 인터넷이 깨질 수 있다(정확한 자기 주소만 막는다). 인터페이스 목록은 시험이 주입하고 5초 기억한다(읽지 못하면 앞의 것 —
처음이면 고정 대역만). 막히면 SOCKS5 는 '규칙이 허락하지 않음', HTTP 는 **403 과 까닭·할 일을 적은 본문**('192.168.49.* 를 프록시 예외에'). **이름을
풀지 못한 것은 막은 것이 아니다**: HTTP 는 502 와 까닭, SOCKS5 는 '호스트에 닿지 못함'(4). 이름은 **한 번 풀어 걸러진 주소 객체로** 연결한다(DNS
재바인딩 — UDP 도 TCP 처럼 막히지 않는 첫 주소). **포트는 막지 않는다** — PC 의 트래픽을 통째로 지나게 하는 쓰임이라 메일·SSH·게임이 깨진다. 손님은 폰 자신(브라우저의 중계 포함)과 폰이
붙은 망의 on-link 범위에 닿지 못한다 — **그 너머의 LAN**(공유기가 나눠 준 다른 서브넷, 공인 IPv4 로 된 회사망의 다른 대역)은 모른다.

### 상한 — 자원 고갈

| 무엇 | 값 | 까닭 |
|---|---|---|
| **스레드 예산**(`ThreadBudget` — 서버 셋이 함께) | **768** | 연결을 받기 전에 그 연결이 쥘 스레드(프록시 TCP 2 · SOCKS5 UDP 묶음 3 · 파일 1)를 쥐고, 모자라면 받지 않는다(닫거나 SOCKS5 일반 실패). usb-tether 의 최악은 256×3 + 256×2 + 32 = **1,312** 였다. 지금 최악은 768 + accept 셋 + 이름 풀이 8 ≈ **780**. SOCKS5 혼자 상한까지 차도(TCP 128×2 + UDP 128×3 = 640) 들어가고 그때도 HTTP 터널 64 개가 남는다 |
| SOCKS5 동시 연결 | 256 | tun2proxy 는 PC 의 TCP 연결 하나가 연결 하나다 |
| 그 가운데 UDP 묶음 | **128** | 묶음 하나가 스레드 셋과 64 KiB 버퍼 둘(힙) — 128 이면 16 MiB. tun2proxy 는 DNS 질의마다 묶음을 연다고 보아 넉넉히(확인하지 않았다) |
| HTTP 프록시 동시 연결 | 256 | 앱들이 통째로 HTTP 프록시를 지나는 쓰임을 줄이지 않으려고 |
| 파일 서버 동시 연결 | 32 | zip·섬네일·복사도 그 연결의 스레드에서 돈다(스레드를 더 띄우지 않는다 — 예산 1) |
| 파일 서버 — zip | 동시에 흘리기 **2** · 기다리는 표 4(60초, 한 번 쓴다) · 담는 항목 **1만**(폴더 안까지)·깊이 32 | 표를 줄 때 미리 펴서 넘으면 받기 전에 413. 연결 하나를 끝까지 쥐므로 동시 수를 묶는다. 표 수 상한은 **펴 보기 전에** 본다(뒤에서 보면 표가 찬 동안 몰아 보낸 요청마다 폴더를 펴 SAF 질의를 쏟는다 — 11-21, 넣기 전에 한 번 더 본다) |
| 파일 서버 — 섬네일 | 동시에 짓기 2(차례 20초) · 하나 512 KiB · 캐시 3,000개·48 MiB(cacheDir, 오래 안 쓴 것부터) | 동영상 장면 풀기는 무겁다. 캐시 파일 이름은 열쇠의 SHA-256 — 손님의 글이 경로에 들지 않는다 |
| 파일 서버 — 목록·관리 | 목록 2만 항목(넘으면 자르고 `truncated`) · 한 번에 고른 것 1,000 · JSON 몸 512 KiB · 손으로 짓는 복사 1만 항목 · 관리 동작은 하나씩(차례 30초 — 폴더째 올리기의 폴더 만들기도) | 화면은 보이는 줄만 그린다(3,000개 폴더에서 줄 35개). **JSON 몸은 관리 잠금 밖에서 다 읽은 뒤에 잠근다** — 잠금 안에서 읽으면 몸을 한 바이트씩 흘리는 손님 하나가 다른 모든 관리 동작을 503 으로, 폴더째 올리기를 끝없는 기다림으로 묶었다(11-21 검토에서 고침) |
| 머리를 다 받기까지 | SOCKS5·HTTP 10초, 파일 30초 — **머리 전체의 마감**(`DeadlineInputStream` — 읽기마다 남은 시간을 soTimeout 으로 건다) | 읽기마다 다시 거는 soTimeout 이면 한 바이트씩 흘리는 손님이 일꾼을 끝없이 쥔다. 중계가 시작되면 0(무한 — 조용한 WebSocket·SSH 를 끊지 않는다. 쌓이는 것은 위 상한이 막는다). 파일 서버의 올리기 본문은 읽기 하나에 30초(큰 파일은 오래 걸리므로 전체 마감이 없다) |
| 머리 줄 | HTTP 한 줄 8 KiB · 100 줄, 파일 64 줄 — **`:` 없는 줄·겹친 이름도 센다** | 넘으면 431(파일 서버는 400) |
| UDP 이름 풀이 | 캐시 256 · 60초 · 동시 8 | 중계 스레드에서 풀지 않는다(첫 패킷은 버리고 뒤에서 푼다) |

수치는 어림이다 — 스레드 하나의 스택 예약 1 MiB 안팎, 실제 메모리는 기기에서 재지 않았다. 테더는 따로 된 프로세스라 메모리가 모자라도 탭·내려받기는
함께 죽지 않는다. 상한으로 닫은 연결은 손님 거르기와 따로 세어 화면에 보이고(`Refusal`), 거절·상한의 기록은 같은 까닭이면 초당 한 줄과 묶인 수로
적는다(`LogThrottle`).

### 서버의 세부

- **SOCKS5**: 무엇을 내밀든 NO_AUTH. 주소 꼴 셋(IPv4·이름·IPv6). CONNECT 의 답은 BND `0.0.0.0:0`(폰의 셀룰러 주소를 실어 손님에게 통신사 IP 를 알리지
  않게). 중계는 두 방향(몸통 `TcpServer.relay` — HTTP 프록시와 함께), 한쪽이 끝나면 반대편 쓰기를 닫아(`shutdownOutput`) 상대가 정상 FIN 을 받고
  **반대 방향은 시한 없이 끝까지** 넘긴다(시한을 두면 요청을 보내고 쓰기를 닫은 손님에게 늦게 오는 답이 잘린다. 기다리는 동안 1초마다 멈춤을 보아
  stop() 이면 원격까지 닫는다). 버퍼 한 방향 8 KiB. 인사·요청을 읽은 버퍼를 중계까지 넘긴다(답을 기다리지 않고 요청 뒤에 데이터를 붙여 보내는 손님).
- **UDP ASSOCIATE**: 소켓 **둘** — `clientFacing` 은 `192.168.49.1` 에만 묶고 출발 주소가 그 손님이 아니면 버린다(포트만 첫 패킷에서 배운다),
  `remoteFacing` 으로 온 것은 뜻으로 답이다(소켓 하나·출발지 어림이면 탈취·열린 UDP 프록시·반사 증폭·답 꽂기가 다 된다 — 런처가 `--dns over-tcp` 를
  빼서 PC 의 DNS 가 모두 여기를 지난다). 조각(FRAG≠0)은 버린다. 한계: 원격의 답을 보낸 요청과 견주지 않는다(NAT 표 없음), 요청의 DST 를 쓰지 않는다.
- **HTTP 프록시**: 머리를 바이트 단위로 읽고 **같은 버퍼 스트림을 중계까지 넘긴다**(다시 얻으면 버퍼에 든 본문을 잃는다 — 시험이 본다). CONNECT 는
  `HTTP/1.1 200 Connection Established`. 전달은 절대 URI → origin-form, `Host` 가 없으면 채우고, hop-by-hop 머리(`Proxy-*`·`Connection`·
  `Keep-Alive`·`TE`·`Trailer`·`Upgrade` — `Locale.ROOT` 로 견준다)를 걷고 `Connection: close` 를 적는다(요청마다 원격 연결을 새로 연다). 스머글링의
  재료 — CL 과 TE 를 함께·겹친 `Host`·홀로 선 CR — 는 400. RFC 7230 3.3.2·3.3.3 도 따른다(`framingChecked`): `Content-Length`
  가 여럿이거나 한 줄에 쉼표로 여럿이면 모두 같은 십진수일 때만 하나로 줄여 넘기고(앞의 0 은 같은 값) 다르거나 수(`1*DIGIT`)가 아니면(빈 값·부호·
  빈칸·`0x`) 400, `Transfer-Encoding` 이 둘 이상이거나 마지막 코딩이 `chunked` 가 아니거나 `chunked` 가 두 번이거나 빈 코딩이 있으면 400
  (`xchunked`·`chunked, identity` 처럼 서버마다 다르게 읽는 꼴). 이 프록시는 본문을 읽지 않고 흘리므로 경계를 읽는 것은 원격과 그 앞의 CDN 이다 —
  그 사이에서 갈리는 꼴을 세탁해 주지 않으려는 것이다. 인증 없음.
- **파일 서버**(2026-11-21 NAS 꼴로 다듬음 — 본보기는 시놀로지·QNAP File Station·Nextcloud. 바깥 JS·CSS·CDN·새 Gradle 의존 없음). API:

  | 요청 | 하는 일 |
  |---|---|
  | `GET /` · `/fileman.js` · `/fileman.css` | 화면(앱의 자산 셋 — `FileServer.UI_FILES` 밖의 이름은 묻지 않는다) |
  | `GET /api/info` | 폴더 이름·`manage`(관리 허용)·저장 공간(`free`·`total` — 얻지 못하면 null)·상한 |
  | `GET /api/list?path=[&dirs=1]` | 목록 JSON(폴더 먼저·이름 차례, 휴지통은 감춘다, 2만에서 자르고 `truncated`). `dirs=1` 은 폴더만(나무·옮길 곳) |
  | `GET /api/file?path=[&inline=1]` | 받기 — Range 하나(`bytes=a-b`·`a-`·`-n`, 여러 구간은 416), RFC 6266 `filename*`. 늘 `attachment` — `inline=1` 은 **PDF 만** |
  | `GET /api/thumb?path=&size=` | 섬네일(160·320). 머리 바이트로 JPEG·PNG·WebP·GIF 만(`Thumbnails.sniff`), 없으면 404 |
  | `PUT /api/file?path=[&overwrite=1][&parents=1]` | 올리기(아래). **덮어쓰기는 `overwrite=1` 일 때만** — 없거나 `0` 이면 같은 이름(대소문자 무시, 폴더도)에 409 `exists`, 다른 값은 400. 같은 이름이 **폴더면 `overwrite=1` 이어도 409**(`folder: true`). 덮을 때 원래 파일은 휴지통으로(답의 `trashed`) — 0바이트면 휴지통 없이 바꾼다. 답의 `name` 은 실제로 둔 이름. `parents=1`(없는 폴더를 만들며 — 폴더째 올리기)은 관리 |
  | `POST /api/zip` `{base, names}` → `GET /api/zip?id=` | 여러 개·폴더를 zip 으로 **흘려** 받기 — 표를 줄 때 차례를 미리 펴(항목 1만·깊이 32 넘으면 413), 표는 60초·한 번. 이미 눌린 형식(그림·영상·소리·압축·PDF)은 누르지 않고 나머지는 수준 1, 파일마다 내보낸다. 길이 없이 보내고 연결을 닫아 끝을 알린다. 중간에 못 읽으면 연결을 끊는다(브라우저는 실패로 — 반쯤 된 zip 을 '완료' 로 보이지 않게) |
  | `PUT /api/folder?path=` | 폴더 만들기(관리) — 같은 이름(대소문자 무시) 409 |
  | `POST /api/rename` `{path, name}` | 이름 바꾸기(관리) — 맨 위(공유 폴더 자체)는 400, 대소문자만 바꾸기는 겹침이 아니다 |
  | `POST /api/move`·`/api/copy` `{base, names, to, conflict}` | 옮기기·복사(관리). **폴더를 자기 안으로**(대상이 그 폴더이거나 그 아래)는 아무것도 하지 않고 400. `conflict` 는 `ask`(기본 — 하나라도 겹치면 409 `conflicts` 로 이름들, 화면이 묻는다)·`skip`·`rename`(`이름 (1).확장자`). 같은 폴더로 옮기기는 건너뜀, 같은 폴더로 복사는 사본 |
  | `POST /api/delete` `{base, names}` | **휴지통으로 옮기기**(관리) |
  | `GET /api/trash` · `POST /api/trash/restore` `{ids}` · `POST /api/trash/purge` `{ids}`·`{all:true}` | 휴지통 보기(`retentionDays` — 보관 기간, 0 은 비우지 않음)·되살리기·영구 삭제·비우기(관리 — 비우기는 휴지통 폴더와 `.nomedia` 를 남긴다) |

  - **바꾸는 요청은 PUT 과 `Content-Type: application/json` 몸의 POST 뿐**(다른 사이트의 쪽이 사전 확인 없이 보낼 수 없는 꼴 — `OPTIONS` 는 404 그대로).
    JSON 몸은 매개변수가 붙은 `application/json` 만(아니면 415 — 폼·`text/plain`·multipart), TE 501, 길이 없음 411, 512 KiB 넘음 413, 엄격한 읽개
    (`JsonReader` — 겹친 이름·찌꺼기·깊이 8·홀로 선 서로게이트 거절). 덧붙여 바꾸는 요청에 `Origin` 이 있으면 `http://192.168.49.1[:포트]` 여야 하고
    `Sec-Fetch-Site` 가 있으면 `same-origin`·`none` 이어야 한다(아니면 403). `Host`·`Origin`·`Content-Type`·`Content-Length`·`Transfer-Encoding`·
    `Sec-Fetch-Site`·`Range` 가 둘이면 400. **GET 은 아무것도 바꾸지 않는다.**
  - **관리 허용**(`tether` 설정의 `manage_allowed`, 기본 끔 — 테더 화면의 '공유 폴더' 절 스위치): 서버가 **요청마다** 람다로 읽는다(끄면 다음 요청부터
    403 과 `manageOff` 와 까닭). 관리 동작은 하나씩(잠금 — 두 손님이 같은 것을 함께 옮기거나 지우는 틈을 두지 않는다). 파일 올리기(같은 이름 덮어쓰기
    포함)는 예전부터의 기능이라 스위치와 상관없다(사용자 결정 11-21) — 덮인 원래 파일은 스위치가 꺼져 있어도 휴지통으로 간다(아래 '올리기'). 관리 허용은 테더링 켜기·끄기와 별개다 — 끌 때 저절로 끄는 코드는 없다(사용자 결정).
  - **휴지통**: 공유 폴더 맨 위의 숨은 폴더 `.web-trash` 에, 지운 것 하나마다 칸 폴더 `t<ms>-<8 hex>`(그 안에 지운 항목이 이름 그대로)와 기록
    `<id>.json`(원래 이름·원래 폴더 경로·지운 때·폴더인가·크기 — 칸 밖이라 지운 항목의 이름과 겹치지 않는다). 되살리기는 기록의 자리로(그 폴더가 사라졌으면
    다시 만들고, 같은 이름이 있으면 칸 안에서 먼저 `이름 (1)` 로 바꾼 뒤 옮긴다). 비우기·영구 삭제만 정말 지운다(`deleteDocument` — 폴더는 안의 것까지).
    **보통의 길은 휴지통에 닿지 않는다** — 맨 위 성분이 그 이름(대소문자 무시, **Unicode Default_Ignorable 전부와 끝의 점·공백을 떼고** — `SharedPaths.isTrashName`·`trashKey`·`isIgnorableCodePoint`, 보이지 않는 글자는 11-27 점검. 새 이름에서 빼는 글자(`cleanName`)보다 넓다 — 일반 이름은 그 글자를 지키되 휴지통 판정만 넓게)이면 목록·받기·섬네일·옮기기는 404, 올리기는 400, 새 이름으로도 받지
    않는다(`SharedPaths.isHidden`) — 옮기기·복사로 하위 폴더의 `.web-trash` 를 맨 위로 가져와 휴지통 자리에 앉히는 것도 그 항목의
    실패로 거절한다(휴지통이 아직 없으면 겹침이 없어 그대로 휴지통이 됐다 — 11-21 2차 검토에서 고침). 목록·나무·zip 에서 감춘다(하위 폴더의 같은 이름은 감추지 않는다 — 맨 위의 것만 휴지통이다). 끝의 점을 떼는 까닭:
    윈도(이 PC 의 시험 — `File(".web-trash.")` 가 `.web-trash` 를 만든다)와 리눅스의 vfat·exfat(SD 카드 — 코드로만 보았다)는 끝의 점을 떼고 만들어,
    스위치가 꺼져 있어도 되는 올리기로 `.web-trash.` 파일을 휴지통 자리에 앉히면 지우기가 모두 409 가 되고 API 로는 치울 수 없었다(11-21 검토에서 고침).
    대소문자 밖의 겹침 가운데 유니코드 접기(ext4 casefold 의 NFKC 류)는 따로 막지 않았다. 휴지통을 새로 만들었는데 제공자가 다른 이름으로 지었으면(겹쳐
    `이름 (1)`·글자 바꾸기) 그 폴더를 지우고 500 '휴지통 폴더(.web-trash)를 만들지 못했습니다' — 숨지 않은 다른 이름의 폴더에 지운 것을 쌓지 않는다(11-27).
    휴지통 안에는 빈 파일 **`.nomedia`**(`SharedOps.NO_MEDIA`)를 둔다 — 폰의 갤러리(MediaStore)가 지운 사진·영상을 보이지 않게(사용자 결정 11-21). 휴지통을
    만들 때·지우기(휴지통으로)마다·휴지통 손보기마다 없으면 더한다(옛 판이 만든 휴지통도). 칸도 기록도 아니라 목록·되살리기·영구 삭제에 나오지 않고,
    비우기(`{all:true}`)는 그것과 휴지통 폴더를 남기고 나머지를 지운다(전에는 휴지통 폴더째 지웠다). 더하기의 실패는 삼킨다.
    **보관 기간**(사용자 결정 11-21 — 테더 화면의 '휴지통 보관': 7·30(기본)·90일·비우지 않음, `tether` 설정의 `trash_days`): `FileServer` 가 선 뒤
    15초에 한 번(테더링을 켤 때마다 — 서버는 테더링과 함께 서고 내린다)과 그 뒤 하루마다, 그리고 화면에서 기간을 바꾸면 곧바로 한 번
    (`TetherService` 가 설정을 듣고 `requestTrashSweep`) `SharedOps.maintainTrash` 를 돌린다 — `:tether` 프로세스의 스레드 하나(`file-server-trash`,
    연결이 아니라 스레드 예산 밖), 관리 동작과 같은 잠금(30초 안에 차례가 오지 않으면 이번은 건너뛴다), **관리 허용과 상관없다**. 지우는 것은
    **기록의 '지운 때' 와 칸 이름의 시각이 둘 다** 기간보다 오래된 칸뿐이다(딱 기간인 것은 아직) — 기록이 없거나 읽지 못하거나 '지운 때' 가 없으면
    지우지 않는다(데이터를 잃지 않게). 칸 이름의 시각이 기간 안이면 기록을 읽지도 않는다(휴지통이 커도 SAF 질의는 지난 것만큼). 칸을 지우지 못하면
    기록을 남겨 다음에 다시 하고, 실패는 기록(로그)만 한다. 휴지통 API 를 부를 때 하지 않은 까닭: 휴지통을 열지 않으면 영영 비우지 않는다(기간의 뜻은
    공간을 돌려받는 것), GET 은 아무것도 바꾸지 않는다는 규칙도 있다. 더 자주 하지 않은 까닭: 손볼 때마다 휴지통 목록과 지난 칸의 기록을 SAF 로 읽는다.
  - **경로**는 공유 폴더를 맨 위로 하는 상대 경로 — 절대·`.`·`..`·빈 성분·역슬래시·NUL·32 성분 넘음은 거절하고(`SharedPaths.split`), **성분마다 자식만
    찾아 내려간다**(`SharedTree` 의 계약 — SAF 의 문서 ID 로만). 새로 짓는 이름(만들기·바꾸기·올리기)은 **먼저 보이지 않는 글자를 빼고**
    (`SharedPaths.cleanName` — 사용자 결정 11-21: C0·DEL·C1 `U+0000–001F·007F–009F`, 방향 제어 `U+061C·200E·200F·202A–202E·2066–2069`, 폭 없는 글자
    `U+180E·200B–200D·2060–2064·FEFF`, 줄·문단 나눔 `U+2028·2029` — 소프트 하이픈·태그 글자(국기 이모지)·이체 선택자(`U+FE0F` — 이모지 꼴)·몽골 이체
    선택자·한글 채움·`U+034F` 는 두고, ZWJ 를 빼므로 이어 붙인 이모지는 낱자로 갈린다. 11-27 점검이 Default_Ignorable 전부로 넓혔다가 재검토가 되돌렸다 —
    넓은 집합(`SharedPaths.isIgnorableCodePoint`)은 휴지통 판정에만 쓴다) 그 이름을 `SharedPaths.isValidName`(공백만·`.`·`..`·`/`·역슬래시·255 바이트 넘음 거절)과 맨 위의 휴지통 이름으로 본다
    (`SharedOps.newName` — 같은 이름 검사도 뺀 뒤의 이름으로, 답에 실제 이름). 빼는 곳은 올리기의 파일 이름·폴더 만들기·이름 바꾸기·옮기기/복사에서
    겹쳐 새로 짓는 이름(`이름 (1)` — 겹치지 않으면 이름 그대로 옮긴다)·폴더째 올리기의 **없는** 성분(그 이름 그대로의 폴더가 있으면 그것 — 다른 앱이
    지은 이름은 다듬지 않는다). 이미 있는 폴더를 가리키는 경로 성분은 다듬지 않는다. 경로의 NUL 은 지금처럼 경로 검사(`split`)에서 400 — 브라우저의
    파일 이름에는 없고 화면은 미리 뺀다. 화면(`fileman.js`)도 둘로 나눈다 — `STRIP_RE`(새 이름에서 빼는 것 — 미리 빼 보이기만)와 `IGNORABLE_RE`(휴지통 판정 — `u` 깃발, 보충 평면은 `\u{…}`), `FileManTest` 가 각각을 서버의 `isStrippedCodePoint`·`isIgnorableCodePoint` 와 모든 코드 포인트 0–10FFFF 로 견준다. 여러 항목은 늘 `base` 폴더 + 한 성분 이름들(1,000 까지, 겹치면 400). `SharedOps` 가 이 규칙과 겹침·자기 안으로·
    상한·휴지통을 맡고 `SharedTree` 는 바탕 동작(만들기·이름 바꾸기·옮기기·복사·지우기)만 한다 — 앱의 SAF 와 시험의 임시 폴더가 같은 규칙을 탄다.
    SAF 의 옮기기(`moveDocument`)·복사(`copyDocument`)는 제공자가 그 깃발을 세운 것만, 아니면 손으로(복사 — 반쯤 지은 사본은 지운다, 옮기기 — 복사가
    다 된 뒤에만 원래 것을 지운다 — 지우기가 실패하면 원래 것이 반쯤 지워졌을 수 있으므로 **사본을 지우지 않는다**: 휴지통으로 옮기기는 그때 칸과 기록을
    남기고 실패로 알린다(전에는 칸을 치워 온전한 것이 하나도 남지 않을 수 있었다 — 11-21 검토에서 고침). 옮기기는 이름을 고르지 못하므로 새 이름이 필요하면 원래 폴더에서 숨은 임시 이름으로 바꾸고 옮긴 뒤 새 이름으로 바꾼다. 그 마지막 이름 바꾸기가
    실패하면 숨은 임시 이름으로 남기지 않는다(11-27): 제공자의 옮기기로 제자리에 되돌려 원래 이름으로(손으로 하는 복사 뒤 지우기는 반쯤 지운 것을 남길 수
    있어 쓰지 않는다), 못 하면 옮긴 곳에서 원래 이름으로 바꾸고, 실패 글에 실제로 남은 자리·이름('옮기지 않고 되돌렸습니다'·'제자리에 ‘…’ 로 남았습니다'·
    '옮긴 곳에 ‘…’ 로 남았습니다' — 되돌리지도 못하면 숨은 임시 이름 그대로를 적는다, `SharedOps.undoMove`).
  - **올리기**: 본문이 그대로 내용 — **숨은 임시 이름 `.이름.part` 로 다 받은 뒤에** 그 이름으로 바꾼다. 끊기거나 길이가 다르거나
    쓰기를 열지 못하면 **임시만** 지운다(원래 파일은 그대로, 빈 파일도 남기지 않는다). TE 가 있으면 501, CL 이 없으면 411, 흘려서 쓴다. SAF 쪽은 임시를
    `renameDocument` 로 바꾸므로 **이름 바꾸기를 하는 제공자**여야 한다(`FLAG_SUPPORTS_RENAME` — 못 하면 받기 전에 500). 이름 바꾸기가 실패하면 받은
    내용이 임시에 남는다(지우지 않는다). **같은 이름은 `overwrite=1` 일 때만 덮는다**(사용자 결정 11-21 — 묻지 않은 요청이
    조용히 덮지 않게): 없거나 `0` 이면 받기 전에 409 `exists`, 받는 동안 다른 손님이 그 이름을 만들었으면 두기 직전에 다시 보고 받은 것(임시)을 버리고
    409. **두기(`SharedTree.commitUpload`)는 같은 이름(대소문자 무시)을 지우지 않는다** — 있으면 임시를 지우고 실패(예전에는 지우고 바꿨다 — 확인과
    두기 사이의 틈에 생긴 것을 덮을 수 있었고, 덮어쓰기가 휴지통을 비켜 갔다). 기기 저장소의 제공자는 그 사이에 생긴 이름에 겹치지 않는 이름을
    고른다(FileSystemProvider — 답의 `name` 이 그것). **같은 이름이 폴더면 `overwrite=1` 이어도 받기 전에 409 `folder`**(한 폴더에 같은 이름의 파일과
    폴더는 둘 수 없다 — 파일 시스템의 제약, 대소문자를 가리지 않는 곳에서는 `Photos` 폴더와 `photos` 파일도). **덮어쓰기(사용자 결정 11-21)**: 다
    받은 뒤 관리 동작과 같은 잠금(30초 — 안 오면 503, 임시를 지우고 원래 것 그대로)을 쥐고 **원래 파일을 휴지통으로 옮긴 뒤에**(`SharedOps.trashReplaced`
    — 지우기와 같은 칸·기록이라 되살리기·보관 기간·`.nomedia` 가 같다, **관리 허용과 상관없다**) 둔다. 휴지통으로 옮기지 못하면(휴지통 자리에 파일·
    제공자가 거절) **덮지 않고** 받은 임시를 지운 뒤 500 '원래 파일을 휴지통으로 옮기지 못해 덮어쓰지 않았습니다 — …'. 뒤 글은 원래 것을 다시 보고 가른다(11-27 — `FileServer.stillInPlace`): 같은 항목·
    이름·크기로 제자리에 있을 때만 '원래 파일은 그대로입니다', 없으면 '원래 파일이 제자리에 없습니다 — 휴지통을 확인하세요', 읽지 못하면 '원래 파일이
    그대로인지 확인하지 못했습니다 — 휴지통을 확인하세요'(옮기기가 된 뒤 실패를 알린 꼴이 있다 — 아래 `trashOne`). 옮긴 뒤
    두기가 실패하면 원래 것을 휴지통에서 되살려 본다(그 틈에 같은 이름이 생겨 `이름 (1)` 로 되살렸으면 답이 그 이름을 적는다 — 전에는 '그대로' 라 했다,
    11-21 3차 검토). 휴지통으로 옮기기(`trashOne` — 지우기와 함께)가 실패를 알려도 **칸에 그 항목이 있고 제자리에 없으면 옮겨진 것으로 친다**(SAF 의
    `moveDocument` 가 된 뒤 다시 읽기가 실패한 꼴 — 전에는 칸을 지워 하나뿐인 원래 것을 지웠다, 11-21 3차 검토. 알 수 없으면 칸과 기록을 남긴다). 답의 `trashed`. 관리가 꺼진 손님이 덮어쓴 것은 휴지통에 남지만 **보기·되살리기는 관리 허용이
    필요하다**(휴지통 API 는 관리 — 지금 규칙 그대로). 그래서 휴지통이 커질 수 있다 — 지금은 다루지 않는다(사용자 결정 11-21).
    **원래 파일이 0바이트면 휴지통에 넣지 않는다**(사용자 결정 11-21 — 덮어쓰기만, 관리의 지우기·폴더째 지우기는 그대로 휴지통): 잠금 안에서 방금
    읽은 실제 항목으로 판정한다(`SharedOps.isEmptyFile` — 크기 0 **이고 열어 읽은 바이트가 없음**. 앱의 SAF 는 `COLUMN_SIZE` 가 null 이면 크기를
    0 으로 적으므로 크기만 보면 내용 있는 파일을 지울 수 있다 — 열지 못하면 비지 않은 것으로). 두기는 같은 이름을 지우지 않으므로 원래 것을 같은
    폴더의 숨은 이름 `.이름.replacing-<8 hex>` 로 비켜 두고(`setAside` — 지우지 않는다) 둔 뒤에 치운다(`discardAside` — **지우기 직전에 다시 보고**
    그새 커졌거나 지우지 못하면 원래 이름으로 휴지통에, 답의 `trashed` 가 true). 두기가 실패하면 비켜 둔 것을 원래 이름으로 돌린다(그새 같은 이름이
    생겼으면 `이름 (1)` — 답이 그 이름을 적는다). 비켜 두지 못하면(이름이 너무 길어짐·제공자 거절) 휴지통 길로 간다. 남는 틈: 다시 본 뒤 지우기까지
    사이에 다른 앱이 쓰는 것은 막지 못한다(지우기가 조건부가 아니다), 비켜 둔 뒤 프로세스가 죽으면 숨은 0바이트 파일이 남는다(`.part` 임시처럼).
  - **답의 머리**: 모두 `X-Content-Type-Options: nosniff`·`Referrer-Policy: no-referrer`·`Cross-Origin-Resource-Policy: same-origin`·`Connection: close`.
    화면은 CSP `default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; media-src 'self'; connect-src 'self'; base-uri 'none';
    form-action 'none'; frame-ancestors 'none'`(인라인 스크립트·스타일 없음 — 화면은 HTML·JS·CSS 세 파일, 스크립트는 CSSOM 으로 크기·위치만) +
    `X-Frame-Options: DENY`. 그 밖(JSON·파일·섬네일·zip)은 `default-src 'none'; sandbox; frame-ancestors 'none'`. 브라우저로 여는 PDF 만 sandbox 없이
    (`frame-ancestors 'none'` — 크로미움의 PDF 보기가 sandbox 문서에서 막힌다). **HTML·SVG 를 이 출처의 문서로 여는 길이 없다** — 받기는 늘 attachment,
    미리 보기는 그림·영상·소리를 요소로(요소는 attachment 를 보지 않는다), 글은 앞 256 KiB 를 Range 로 받아 `<pre>` 의 글로(UTF-8, 아니면 EUC-KR).
  - **섬네일은 남이 올린 그림을 `:tether` 프로세스에서 풀지 않는다**(그 프로세스는 프록시 계정에 닿는 UID 다 — 그림 풀개의 결함이 곧 그 위험).
    `SharedFolder.thumbnail`: 그림은 제공자의 섬네일 바이트를 그대로(`openTypedAssetFileDescriptor(…, "image/*", EXTRA_SIZE)` — 기기 저장소의 제공자는
    **EXIF 섬네일**을, 없으면 원본 전체를 준다 — 512 KiB 를 넘으면 버린다), 동영상은 `MediaMetadataRetriever.getScaledFrameAtTime`(풀기는 시스템의
    미디어 프로세스 — 받은 화소를 JPEG 로 담기만), 소리는 파일에 든 앨범 그림 바이트 그대로. `BitmapFactory`·`ImageDecoder`·`loadThumbnail`·
    `getDocumentThumbnail` 을 부르지 않는다(`FileManTest`). 서버의 섬네일이 없는 그림은 화면이 3 MB 이하일 때 원본을 `<img>` 로 대신 그린다(손님의
    브라우저가 푼다). 캐시는 `cacheDir/tether-thumbs`.
  - **화면**(`assets/tether/fileman.*` — 바닐라 JS 하나): 머리(폴더 이름·남은 공간 막대·'관리 켜짐/꺼짐') · 도구 막대(위로·새로 고침·올리기(파일·폴더째)·
    새 폴더·받기·이름 바꾸기·옮기기·복사·지우기·정보·휴지통·거르기·정렬·목록/격자·단축키) · 길(빵 부스러기) · 왼쪽 폴더 나무(펼칠 때 읽는다) + 오른쪽 목록
    (보이는 줄만 그린다) · 아래 줄(항목 수·고른 것의 수와 크기). 760 px 아래(폰)는 한 칸 — 나무는 ☰ 의 서랍, 크기·때는 이름 아래 줄. 정렬은 이름(숫자는
    수로, 라틴 다음 한글)·크기·때·종류, 폴더는 늘 먼저. 고르기 — 누르기·Ctrl·Shift·Ctrl+A·네모, 터치에서는 누르면 열고 고른 것이 있으면 누르기가 고르기.
    보기 — 그림(앞·뒤·휠·두 손가락 확대·끌기·밀어 넘기기)·동영상·소리·글, PDF 는 새 탭. 올리기 대기열(파일마다 진행·속도·그만두기·다시, 동시에 둘, 끌어다
    놓기는 폴더까지). **같은 이름이면 '…에 이미 있습니다. 덮어쓸까요?' 를 한 번 묻는다**('건너뛰기·새 이름으로·덮어쓰기', 기본 단추(Enter)는 '새 이름으로',
    창을 닫으면 건너뛰기) — 그 묶음(한 번 고르거나 끌어다 놓은 것)에 겹치는 것이 더 있으면 체크 상자 **'모든 파일에 적용 (겹치는 파일 N개)'**(윈도의
    덮어쓰기 창처럼 — 처음 물을 때 묶음이 올릴 폴더를 다 읽어 이 파일까지 센다, 폴더가 16개를 넘으면 세지 않고 정하지 않은 것이 남았으면 수 없이),
    그 답은 **그 묶음 안에서만** 산다(다음 올리기·'다시' 는 새 묶음이라 다시 묻는다 — '다시' 는 고른 덮어쓰기·새 이름도 지운다. 새 묶음은 올릴 폴더의
    이름을 새로 읽는다). **같은 이름이 폴더면** '덮어쓸까요?' 없이 '같은 이름의 폴더가 있습니다' 한 줄, '덮어쓰기' 는 흐리게(title 에 같은 까닭 — 기본
    단추가 되지 않는다). '모든 파일에 적용' 으로 덮어쓰기를 고른 묶음에서 폴더와 겹친 것은 **새 이름으로** 올린다(건너뛰면 그 파일이 올라가지 않는다 —
    내용을 버리지 않는 쪽, 줄에 '새 이름(같은 이름의 폴더)'). 올릴 이름(과 폴더째 올리기의 성분)은 보이지 않는 글자를 뺀 이름으로 살피고 보내며(빼면
    틀린 이름이면 그 줄은 곧바로 실패), 서버가 준 실제 이름을 쓴다. 끝난 줄에 '이름 다듬음'·'원래 것은 휴지통에'. 새 폴더·이름 바꾸기의 창은 입력
    아래에 '보이지 않는 글자를 빼고 ‘…’ 로 씁니다' 를 미리 보인다. 올리기는 늘 `overwrite` 를 밝히고 `1` 은 그 파일에
    '덮어쓰기' 를 고른 뒤에만, 받은 409 는 다시 묻는다. 묻기는 한 번에 하나다(동시에 살피는 둘이 함께 물으면 뒤의 창이 앞의 창을 덮어 앞의 것이 영영
    '살피는 중' 에 남았다 — 11-21 헤드리스에서 보고 고침). **창(대화)도 한 번에 하나** — 열린 창이 있으면 닫힌 뒤에 연다(`dialog` 가
    줄을 세운다 — 묻기가 떠 있는 동안 옮기기의 '같은 이름' 창이 뜨면 묻기의 약속이 풀리지 않아 대기열이 영영 멈췄다, 11-21 2차 검토에서 헤드리스로 재현하고 고침). 휴지통 보기에는 '보관 기간 N일 — 지난 것은 저절로 비워집니다'(0 이면 '보관 기간 없음 —
    저절로 비우지 않습니다') 한 줄. 관리가 꺼져 있으면 관리 단추를 흐리게 하고 title 에 까닭.
    오류 글은 서버가 준 한국어. 단축키는 화면의 '?'. 파일 이름은 늘 `textContent` 로 — 글을 HTML 로 끼워 넣는 길이 스크립트에 없다(`FileManTest`).
    받기·zip 은 `<a download>`(secure context 가 아니다 — 파일 전체를 메모리에 올리지 않는다).
  - **PC 에서 화면 보기**: 시험 소스의 `DevFileServer`(개발용 실행기 — 손님 거르기를 거기서만 루프백 허용으로, `Host`·`Origin` 은 `127.0.0.1`, 관리
    허용은 스위치 파일이 있는 동안)로 임시 폴더를 띄우고 Edge 헤드리스(`--remote-debugging-port` + CDP)로 넓은 창·폰 폭(390)을 떠 본다.
- **멈추면 실제로 끊긴다**: 듣는 소켓 → 적어 둔 연결(`ConnectionRegistry` — `read()` 에 막힌 일꾼은 인터럽트를 무시한다) → 일꾼 풀. 멈춘 서버는 다시
  세우지 않는다(`StartError.TERMINATED`). 실패한 서버는 들고 있지 않아 다음 시작 요청이 다시 해 본다. **받기 고리가 죽으면**(accept 가 던짐 — stop()
  이 닫은 것이 아니면 — 이나 고리 밖으로 나간 Error) 서버를 그 자리에서 내린다 — 듣는 소켓을 닫아 포트를 놓고 연결을 끊고 `StartError.DIED` 로
  알린다. 서비스는 그 서버를 비워 다음 시작 요청에서 새로 세우고 화면은 그 서버의 줄에 까닭을 보인다.
- 서버가 띄우는 스레드는 모두 `background()`(Throwable 까지 잡아 그 중계만 끝낸다) — 안드로이드는 잡히지 않은 예외로 **프로세스를 죽인다**(PC 의
  인터넷이 끊긴다). usb-tether 의 중계는 반대 방향 스레드가 try 밖에서 `client.getOutputStream()` 을 불러 닫힌 소켓에서 죽을 수 있었다. 시험이 서버
  스레드에서 잡히지 않고 나간 예외가 0 인지 본다(`Uncaught`).

### 핫스팟(Wi-Fi Direct)

- `WifiP2pManager.createGroup` 에 사용자가 정한 SSID·패스프레이즈(`WifiP2pConfig.Builder`, 대역 자동, 저장하지 않는 그룹). 자격 증명은 서비스가
  설정에서 읽는다(인텐트에 싣지 않는다). **핫스팟이 서지 못하거나(아래의 어떤 까닭이든) 켜진 뒤 내려가면 서비스가 테더링을 통째로 끈다**(서버까지
  — `TetherService.endTethering`, 까닭은 상태에 남는다). 핫스팟 없는 서버는 손님 거르기가 모두 `NO_GROUP` 으로 막으므로 남겨 둘 까닭이 없다. 서버
  하나가 포트를 얻지 못한 것은 끄지 않는다(그 서버의 줄에 까닭).
- SSID 는 `DIRECT-` + 영숫자 둘로 시작해야 해서(규격) 아니면 **`DIRECT-UT-`** 를 붙이고(바꾸지 마라 — PC 의 Wi-Fi 프로필), 앞머리까지 **32바이트**
  안(한글은 3바이트). 칸을 비우면 `USBTether`(→ `DIRECT-UT-USBTether`). 패스프레이즈는 **출력 가능한 ASCII**(0x20–0x7E — WPA2 의 규격, ASCII 를
  길이보다 먼저 본다) 8–63자이고 **뻔한 값**(`12345678`·`password`·서로 다른 글자 둘 이하·오름·내림 차례)은 거절한다 — 그룹에 들어온 기기는 인증 없이
  프록시와 공유 폴더에 닿으므로. 처음에는 기기마다 다른 **20자 난수**(헷갈리는 0/O·1/l/I 를 뺀 57자 — 약 116비트)를 만들어 채운다. 저장한 값이
  약하거나 규칙에 맞지 않으면 없는 것으로 친다.
- 만들기 전에 Wi-Fi 꺼짐·Wi-Fi Direct 꺼짐(모바일 핫스팟 등)을 할 수 있는 말로 알린다. **BUSY** 면 떠 있는 그룹이 **SSID 와
  패스프레이즈가 모두 같을 때만** 다시 쓴다(그룹 오너이기만 하면 받아들이면 남의·약한 그룹을 제 것으로 보인다). Channel 은 인스턴스마다 새로 만들고
  버릴 때 닫는다. 만들지 못한 인스턴스는 `removeGroup` 없이 Channel 만 닫는다(남의 그룹을 내리지 않게). 늦게 온 결과는 거른다.
- **'켜짐' 은 우리 그룹이 선 것을 확인한 뒤**: `createGroup` 의 성공은 '요청을 받았다' 일 뿐이라, 그 뒤 그룹 조회를 0.5초마다 되풀이해 우리 그룹(이
  폰이 오너이고 이름이 우리 SSID — `GroupWatch`)이 인터페이스 이름과 함께 보이면 켜짐이고 그 이름을 `TetherState.groupInterface` 에 둔다. 15초 안에
  서지 않으면 그 그룹을 내리고 실패('핫스팟이 서지 않았습니다' — 테더링을 끈다). 그동안 화면·타일·알림은 '켜는 중'(화면의 단추는 이미 '테더링
  끄기').
- **밖에서 내려가는 것**: 서비스가 `WIFI_P2P_CONNECTION_CHANGED_ACTION` 을 **동적으로** 받고(`RECEIVER_NOT_EXPORTED` — 시스템만 보낼 수 있는 보호된
  방송이기도 하다, 매니페스트에는 수신기를 두지 않는다) Channel 을 잃는 것(`ChannelListener` — 우리가 닫은 것은 빼고)도
  본다. 우리 그룹이 없다는 방송은 늦게 온 옛 방송일 수 있어 **조회로 확인한 뒤** 테더링을 통째로 끄고 '핫스팟이 내려갔습니다' 를 남긴다. 방송은 근처 기기 권한으로
  온다 — **위치 서비스가 꺼져 있어도**(16 에뮬레이터: 시스템이 `NEARBY_WIFI_DEVICES`+`ACCESS_WIFI_STATE` 를 요구하는 판과 위치를 요구하는 판을 따로 보내고,
  위치를 끈 채 우리 수신기에 앞의 것이 닿았다).
- **끄기**(화면의 단추·알림의 단추·타일 — `TetherService.stop`)는 켜져 있을 때만 부른다(`TetherState.tetheringOn` — 서비스가 떠 있고 핫스팟이
  켜졌거나 켜는 중, 판정은 `TetherTexts.isOn` 하나). 그때는 서비스가 이미 포그라운드로 섰으므로 `stopService` 가 막 청한 시작을 끊어 앱을 죽이는 일이
  없다. 사용자가 끄면 지난 까닭을 지우고(`TetherState.clearProblem`), 실패로 꺼진 것만 까닭이 남는다. 서비스가 내려가며 그룹을 내린다. `removeGroup` 이
  실패해도 그룹이 이미 없을 수 있어 **조회해서 우리 그룹이 정말 남았을 때만** '핫스팟을 내리지 못했습니다' 를 남긴다(서비스가 내려간 뒤라 그 뒤
  방송으로 걷지 못한다 — 다음 켜기가 지운다).
- **시작 요청은 모두 '켜기' 다**(액션 없이) — 화면·트램폴린의 켜기, 최근 앱에서 밀어 없앤 뒤의 다시 세우기(`onTaskRemoved`), 프로세스가 죽은 뒤
  시스템의 sticky 재시작(인텐트 null). 이미 켜져 있으면 아무것도 바꾸지 않는다(받기 고리가 죽어 내려간 서버만 다시 선다). 그래서 sticky 재시작도
  핫스팟을 다시 켜 본다 — 화면이 앞에 없어 `createGroup` 이 BUSY 면 떠 있는 우리 그룹을 다시 쓰고, 없으면 테더링을 끄고 까닭을 남긴다('사용자가 정할
  것'). 스스로 멈출 때는 `stopSelfResult(마지막 시작 번호)` — 그 사이 새 켜기가 와 있으면 멈추지 않는다(함정 '포그라운드 서비스의 차례'). 그 자리에서
  실패해 끄기로 했으면 `START_NOT_STICKY`. 시스템이 뒤에서 포그라운드 서비스를 막으면(`ForegroundServiceStartNotAllowedException`) 앱을 죽이지 않고
  화면이 '다시 켜세요' 를 보인다(sticky 재시작과 배터리 최적화에서 뺀 앱은 그 제한의 예외다).
- `createGroup` 은 **이 앱의 화면이 앞에 있지 않으면 BUSY 로 실패한다**(usb-tether 때 실기기) — 그래서 타일은 켤 때 내보내지 않은 투명 트램폴린을
  띄운다(`startActivityAndCollapse(PendingIntent)` — Intent 판은 targetSdk 34 부터 던진다). 끌 때는 서비스를 곧바로 멈춘다(`stopService` —
  뒤에서도 막히지 않는다). 런처 화면(내보냄)이 인텐트의 액션만 보고 핫스팟을 켜면 **아무 앱이나** 사용자 모르게 켤 수 있다 — 켜는 일은 내보내지 않은
  트램폴린과 테더 화면의 단추만 한다.
- 재부팅 뒤 저절로 켜지 않는다(사용자: 필요 없다). START_STICKY·`stopWithTask=false` — 최근 앱에서 밀어 없애도 테더링은 그대로다.

### 권한

| 권한 | 왜 | 언제 |
|---|---|---|
| `FOREGROUND_SERVICE_SPECIAL_USE` | 테더 서비스의 종류(specialUse — 서브타입 property `userspace_socks5_and_http_proxy_for_wifi_direct_tethering`: 손님의 IP 패킷을 넘기지 않고 프록시가 내용만 옮긴다, VpnService 를 쓰지 않는다). dataSync 의 하루 6시간 한도와 상관없다 | 설치 때 |
| `ACCESS_WIFI_STATE`·`CHANGE_WIFI_STATE` | Wi-Fi 가 켜졌는지·Wi-Fi Direct 그룹 만들기 | 설치 때 |
| `NEARBY_WIFI_DEVICES`(`neverForLocation`) | `createGroup` 과 연결 변경 방송 — 위치를 얻는 데 쓰지 않는다고 밝혀 위치 권한도 위치 서비스도 필요 없다(16 에뮬레이터에서 위치를 끈 채 그룹이 서고 방송이 왔다) | **테더 화면에서 '테더링 켜기' 를 누를 때만**(알림 권한도 함께 — 거절해도 켠다. 타일은 권한이 없으면 켜지 않고 테더 화면을 연다) |

**위치 권한은 걷었다**(1.0.0 — 12 의 `createGroup` 이 요구하던 `ACCESS_FINE/COARSE_LOCATION`(maxSdkVersion 32)과 '대략적인 위치' 안내, '위치 서비스
꺼짐' 검사와 '위치 설정 열기' 단추). 이 앱에는 위치·카메라·마이크 권한이 없다 — 웹 페이지의 위치 막기(위 '쪽의 결정')에 OS 겹이 다시 있다.

### 알림 번호 — 앱 전체

알림 번호는 앱(UID)마다 하나의 공간이다 — 프로세스를 나눠도 서로 덮는다(usb-tether 의 테더 알림 1 을 그대로 두면 내려받기 진행과 덮는다).

| 번호 | 무엇 | 어디 |
|---|---|---|
| 1 | 내려받기 진행(갤러리 받기도) | `DownloadService.ONGOING_ID` |
| 2 | 녹화·가로채기 | `CaptureService.NOTICE_ID` |
| 3 | (비어 있다 — 걷은 토렌트의 것이었다) | |
| **4** | **테더링**(채널 `tether` '테더링', 중요도 낮음) | `TetherService.NOTICE_ID` — `:tether` 프로세스 |
| 5 | 뒤에서 재생 잇기(채널 `playback` '재생', 중요도 낮음) | `BackgroundPlayService.NOTICE_ID` |
| 1000 + 내려받기 번호(1 부터 늘어난다) | 끝난·멈춘 내려받기(갤러리 받기도) | `DownloadService.FINISHED_BASE` |

겹치지 않는다. 채널: `downloads`·`downloads-done`·`capture`·`tether`·`playback`. 테더 알림(4)은
**테더링이 켜진 동안만** 있다 — 제목 '테더링 켜짐 · DIRECT-UT-…'(켜는 중이면 '테더링 켜는 중 — 핫스팟을 기다립니다'), 글은 실제로 묶인 포트(못
세웠으면 줄표), **단추 하나 '테더링 끄기'**(내보내지 않은 서비스로 가는 불변 PendingIntent, 액션 `…action.TETHER_STOP` — 화면을 열지 않고 끌 수 있는
자리가 타일 말고도 하나 있는 편이 낫다고 보았다. 잘못 누르면 PC 의 인터넷이 끊긴다 — '사용자가 정할 것'), 누르면 테더 화면.

### 들어가는 길과 화면

**런처 아이콘을 하나 더 두지 않는다**(목적이 '앱 하나 줄기'). ① 브라우저의 **⋮ 메뉴 '테더링'**(연결 상태 아래) ② **퀵 설정 타일 '테더링'** — 누르면
테더링을 통째로 켜고 끄고(부제에 SSID·'켜는 중'·'꺼짐'), **길게 누르면 테더 화면** ③ 테더 알림(켜진 동안 — 누르면 테더 화면). 앱 바로가기는 두지
않는다(사용자가 타일 길게 누르기를 골랐다 — `TetherIsolationTest` 가 없는지 본다). 타일의 이름은 들을 때마다 '테더링' 으로 다시 적는다(시스템 UI 가 옛
이름을 들고 있을 수 있다). 클래스 이름(`HotspotTileService`·`HotspotStartActivity`)은 바꾸지 마라 — 바꾸면 사용자가 더해 둔 타일이 사라진다.

타일을 길게 누르면 시스템 UI 가 `android.service.quicksettings.action.QS_TILE_PREFERENCES`(API 24)를 이 꾸러미로 좁혀 찾고 **자기 uid 로** 띄운다 —
받는 화면은 내보내야 한다(없으면 앱 정보 화면이 열린다). 테더 화면은 내보내지 않은 채 두고, `:tether` 프로세스의 작은 화면 `TilePreferencesActivity`
(내보냄 — `Theme.NoDisplay`·`noHistory`·최근 앱에 남지 않음, 필터는 그 액션 하나)가 테더 화면을 열고 곧바로 끝난다. **받은 인텐트를 읽지 않는다**
(액션·덧붙임·데이터 — 시스템 UI 가 싣는 타일 이름과 상태도) — 누가 띄우든 테더 화면이 열릴 뿐 핫스팟·서버는 건드리지 않는다. 화면의 작업
(taskAffinity)은 따로다 — 알림과 그 작은 화면은 새 작업으로 열고(브라우저와 같은 작업이면 브라우저의 작업을 앞으로 가져와 그 위에 열어 뒤로 가면
브라우저가 선다), 메뉴로 열면 브라우저의 작업 위에 열린다.

화면(`TetherActivity` — 플랫폼 뷰): 상태 줄(**라이브 영역** — 켜져 있으면 서버의 실제 포트 셋과 세우지 못한 서버의 까닭·핫스팟과 그 그룹 인터페이스,
꺼져 있으면 '테더링 꺼짐' 한 줄과 — 실패로 꺼졌으면 — '핫스팟 — 까닭'. 글이 실제로 바뀔 때만 다시 쓴다) · 오가는 양의 줄(라이브 영역 밖 — 주고받은
양·거절한 연결 수와 마지막 까닭·상한으로 닫은 연결 수, 1초마다 — 같은 글이면 손대지 않는다, 함정 '늘 바쁜 화면') · **'테더링 켜기' / '테더링 끄기'
하나**(켜기는 넣은 값 검사 → 권한 → 서비스, 끄기는 서비스를 멈춘다. 켜는 중에도 '끄기') · (그 문제일 때만) 앱 설정 · 짧은 설명(켜기·끄기·
포트·권한) · 핫스팟 — SSID·패스프레이즈(보이기 — 화면 상태 저장과 **자동 완성에서 빠진다**)·실제 SSID(켜진 동안(켜는 중 포함) 둘 다 잠긴다) · 공유
폴더 고르기/그만두기와 **'관리 허용' 스위치**(기본 끔 — 설명 한 문장 '핫스팟에 붙은 기기가 공유 폴더를 바꾸고 지울 수 있습니다.', 파일 서버가 요청마다 본다)·
**'휴지통 보관: 30일 (기본)' 단추**(누르면 7일·30일 (기본)·90일·비우지 않음을 고르는 창 — 브라우저 설정의 보관 기간과 같은 꼴, 설명 한 문장 '지운 지 이 기간이 지난 것은 휴지통에서 저절로 비웁니다.') ·
다른 기기에서 쓰는 법 · 알아 둘 것(배터리 최적화의 지금 상태와 설정 열기). 서비스가 설 때 주고받은 양·거절·상한의 수를 0
으로(`TetherState.resetCounters`) — 꺼진 뒤에는 다음에 켤 때까지 남는다. 설명은 짧게 둔다(사용자 — 포트
범위·권한의 까닭 같은 세부는 이 문서에만).

### 저장

SharedPreferences **`tether`**(테더 프로세스만 연다 — 브라우저의 `web`·`adblock`·`sites`·`popups` 와 겹치지 않는다. SharedPreferences 는 여러 프로세스가
함께 쓰는 것을 지키지 않는다): `ssid` · `passphrase_sealed`(기기 키 **`tether-hotspot-passphrase`** — AndroidKeyStore AES-256-GCM, 사용자 인증 없음 —
타일로 켤 때마다 묻게 된다, 브라우저의 `web-proxy-password` 와 따로) · `tree_uri`(공유 폴더 — 영속 SAF 권한 읽기·쓰기) · `manage_allowed`(파일 서버의 관리 허용 — 기본 끔) · `trash_days`(휴지통 보관 기간 — 7·30·90·0, 기본 30, 고를 값 밖이면 30). 곧바로 쓴다(commit). 백업·기기
이전에서 빠진다(`allowBackup=false`, `data_extraction_rules` 가 sharedpref 를 통째로 뺀다). 패스프레이즈는 화면과 서비스가 **같은 프로세스의 보관소
하나**(`PassphraseVault` — 마지막으로 정하거나 연 값을 메모리에 든다)를 쓴다: 기기 키로 잠그지 못해도 이 프로세스에서는 화면에 넣은 값으로 그룹을
만들고('저장하지 못했다' 를 알린다 — 앞의 저장값은 지우지 않는다), 잠근 값이 있는데 열지 못하면 새 난수로 **덮어쓰지 않고** 화면(칸을 비우고 까닭)·
타일(켜지 않고 까닭 — 서비스가 테더링을 끄고 '저장한 패스프레이즈를 열지 못했습니다' 를 남긴다)에 알린다. 키 저장소는 `GeneralSecurityException` 밖의
것도 던진다 — 잠그기의 실패를 모두 잡는다.

### PC 쪽 — PC 연결 파일(zip)

테더 화면의 '다른 기기에서' → **'PC 연결 파일 받기'** 가 `web-tether-pc.zip`(README.txt · windows-wifi.bat · macos-wifi.sh)을 공용 '내려받기' 에
쓴다(아래 '설정 파일 zip'). 원본은 `app/src/main/assets/setup/tether-pc/`. `windows-wifi.bat` 은 관리자 권한으로 저절로 다시 뜨고(`fltmc` 로 판정 —
`fltmc`·`powershell` 은 `%SystemRoot%\System32\…` 의 온전한 경로로 부른다: 올리기 전의 현재 폴더는 스크립트의 폴더이고 cmd.exe 는 PATH 보다 그곳을 먼저 찾는다)
`tun2proxy.exe --tun USBTether --dns-addr 1.0.0.1 --setup --proxy socks5://192.168.49.1:1080` 을 돌린다(DNS 는 1.0.0.1 — 1.1.1.1 과 같은 Cloudflare 해석기다. LG U+ 모바일망은 1.1.1.1 로 가는 길이 없어 PC 의 이름 풀이가 통째로 실패했다) — Wi-Fi 가 붙어 있는 동안은 TUN 고리가 생기지 않는다(`--setup` 은 프록시 주소가 사설이면 호스트 경로를 더하지 않는다 —
tproxy-config 7.0.9 의 `windows.rs` 를 읽은 것. 192.168.49.0/24 가 Wi-Fi 쪽 on-link 경로라 기본 경로보다 좁아 이긴다. **Wi-Fi 링크가 내려가면 그 경로를 쓸 수 없어 고리가
생긴다** — 배치 파일에는 막는 것이 없다, 도우미의 막기는 아래 'PC 도우미' 의 'Wi-Fi 가 끊길 때 — 경로 고리'), UDP ASSOCIATE 가 되므로 `--dns over-tcp` 가 필요 없고, 포트는 훑지 않고 인자로만(`windows-wifi.bat 1085`).
tun2proxy.exe 가 없거나 오류로 끝나면 창이 멈춘다. `macos-wifi.sh` 는 같은 일 — `exec sudo /bin/bash "$0" "$@"` 로 다시 뜨며 인자를 넘기고(실행
비트가 없어도 된다), TUN 은 VPN 이 흔히 쓰는 `utun0`–`3` 을 피해 `utun5`, 끝날 때 한 번 경로를 걷고 시스템의 DNS 는 건드리지 않는다. 주석은 영어이고
배치 파일은 **ASCII 로만**(cmd.exe 는 배치 파일을 콘솔의 코드 페이지로 읽는다)·CRLF, 셸 스크립트는 LF. WinTun 어댑터 이름은 예전 그대로
`USBTether`. 두 스크립트는 SOCKS5(1080 고정)만 쓴다 — HTTP·파일 포트가 옮겨 서도 상관없다.

**HTTP 프록시를 쓰는 쪽**(PC 의 `HTTP_PROXY=192.168.49.1:8282`·시스템의 Wi-Fi 별 프록시)은 HTTP 가 8282 에 섰을 때만 맞다. 이 폰의 다른 앱이
8282 를 쥐고 있으면 HTTP 는 8283… 로 옮겨 서고 8282 로 가는 요청은 **그 다른 앱으로 간다**(위 '무엇을 하나' 의 남는 위험). tun2proxy 를 쓰는 동안에는
그런 프록시 설정이 필요 없다 — README.txt 가 tun2proxy 를 받는 동안만 두라고 적는다.

**실행 파일(tun2proxy·wintun·adb)은 zip 에도 저장소에도 없다** — README.txt 가 받는 곳을 적고, `.gitignore` 가 어느 폴더에 있든 **이름으로** 막는다.
받는 곳(2026-09-05 확인): tun2proxy `https://github.com/tun2proxy/tun2proxy/releases/latest/download/<파일>` — 윈도 x64 `tun2proxy-x86_64-pc-windows-msvc.zip`
(`wintun.dll` 이 들어 있다), 맥 `tun2proxy-aarch64-apple-darwin.zip`·`tun2proxy-x86_64-apple-darwin.zip`. 압축은 폴더 없이 풀리고, 본체 `tun2proxy-bin(.exe)`
의 이름을 `tun2proxy(.exe)` 로 바꿔야 스크립트가 찾는다. wintun 은 32비트·ARM 윈도일 때만 `https://www.wintun.net/builds/wintun-0.14.1.zip`. 공식
배포본인지 — `Get-AuthenticodeSignature`(wintun.dll: WireGuard LLC), tun2proxy 는 릴리스의 build provenance. 라이선스 — tun2proxy MIT, Wintun 의 서명된
DLL 은 'Wintun Prebuilt Binaries License'(법률 자문 아님).

### PC 도우미 — WebTether.exe(Windows 트레이)

`windows-wifi.bat` 을 대신하는 **트레이 상주 exe 하나**(2026-11-21, 사용자 승인). 소스는 `pc-windows/`(C# 5 + WinForms, .NET Framework 4.8 —
윈도에 든 `csc.exe`, NuGet·외부 라이브러리 없음, 아무것도 설치하지 않는다). zip 의 배치 파일은 그대로 둔다(맥·도우미 없이 쓰는 길).

```bash
python -I pc-windows/build.py [--deploy C:/…/WebTether.exe]   # 받고(한 번) 확인하고 짓는다 → pc-windows/out/
pc-windows/out/WebTether-tests.exe --scratch <폴더> > out.txt     # 루프백·가짜·스크래치만(SSID·Wi-Fi·서비스·작업은 읽기만) — 출력을 파일로(아래 함정). 창을 띄우는 시험 하나는 --with-dialogs 일 때만
pc-windows/out/WebTether-tests.exe --shots <폴더>                  # 창을 PNG 로(화면 밖에 투명하게 잠깐)
python -I pc-windows/build.py --check                            # 최신 tun2proxy·asset digest·보안 권고(보여 주기만)
python -I pc-windows/build.py --update                           # 고정 판을 최신으로 바꾸고 다시 짓는다
```

| 무엇 | 어떻게·왜 |
|---|---|
| 산출 | `out/WebTether.exe`(실제 판 — 매니페스트 `asInvoker`. 인자로 갈린다: 없으면 **트레이**(보통 권한), `--service`(SCM 이 띄운다 — LocalSystem), `--install`·`--uninstall`(관리자 — 트레이가 UAC 로 띄운다, 아래 '서비스'), `WebTether-test.exe`(시험 판 — `asInvoker`, **늘 dry-run**: 서비스 없이 한 프로세스에서 Controller 를 돌리고 tun2proxy 를 띄우지 않고 명령줄만 적고, 경로·Wi-Fi 는 기록만. `--home <폴더>`(없으면 `%LOCALAPPDATA%\WebTether-test` — '이 사용자 전용' 권한)·`--exit-after <초>` — 실제 판처럼 뜨면 저절로 연결한다(dry-run)), `WebTether-tests.exe`·`ctrlc-child.exe`. `out/` 은 **git 무시**(완성 exe 가 남의 실행 파일을 싣는다 — `.gitignore` 의 `pc-windows/out/`·`pc-windows/**/*.exe`. exe 무시는 `pc-windows/` 아래로만 — 저장소의 다른 곳에 커밋된 exe 는 없다) |
| 상태 폴더(`SecureFs`) | 서비스가 쓰고 읽는 것(설정·연결 상태·로그·풀어 둔 실행 파일)은 모두 **`%ProgramData%\WebTether\`**(`SpecialFolder.CommonApplicationData` — 환경 변수가 아니다). 서비스가 시작할 때 `SecureDir.Prepare` 로 준비하고 쥔다. 트레이(보통 권한)는 거기에 쓰지 않는다 — 제 것(업데이트 기억 `update`)은 `%LOCALAPPDATA%\WebTether\`('이 사용자 전용' 정책)에. 예전 판은 `%APPDATA%`·`%LOCALAPPDATA%` 에 썼다 — 같은 사용자의 프로그램이 접합점·심볼릭 링크로 관리자의 지우기·쓰기를 돌리거나 설정·상태 파일을 고쳐 관리자 동작(프록시 주소·넣고 지울 경로)을 정할 수 있었다. 만들 때 상속을 끊고 주인 Administrators, 권한은 Administrators·SYSTEM 모두 권한뿐(사용자는 읽기도 없다 — 로그는 앱의 '로그' 창이 관리자로 읽는다). 이미 있으면 링크를 따라가지 않는 핸들로 열어 재분석 지점·주인·정책 밖 SID 의 쓰기 같은 권한(상속만 하는 줄도)을 보고, 어긋나면(ProgramData 는 사용자도 하위 폴더를 만든다) 쓰지 않고 `WebTether.untrusted-<때>` 로 비킨 뒤(링크면 링크 자체가 옮겨진다) 새로 만든다 — 못 비키면 오류 창을 띄우고 시작하지 않는다. 쥔 핸들(`FILE_LIST_DIRECTORY`, `FILE_SHARE_DELETE` 없음)로 도는 동안 그 폴더의 이름 바꾸기·지우기를 막는다. 그 아래에 쓰기 전에(로그 돌려쓰기 포함) 뿌리부터 그 파일까지 재분석 지점이 없는지 보고, 설정·상태는 임시 파일에 다 쓴 뒤 바꿔 끼운다(`SafeFs`). 다시 풀 때 bin 지우기는 링크를 따라 들어가지 않는다(`SecureDir.DeleteTree`). **옛 자리(`%APPDATA%\WebTether`)는 읽지 않는다** — 처음 한 번 가져오던 `Migration` 은 걷었다(사용자 결정 11-21 — 코드는 git 이력에). 옛 파일도, 걷은 사용량 통계가 남긴 `usage` 파일도 지우지 않는다(읽지 않을 뿐) |
| 고정 판 | **tun2proxy 0.8.7**(2026-10-31) `tun2proxy-x86_64-pc-windows-msvc.zip` SHA-256 `801396ff…f863` — GitHub API 의 asset digest 와 같다. 본체 `tun2proxy-bin.exe` 와 `wintun.dll`(서명 WireGuard LLC 를 빌드가 본다)만 꺼내 각 SHA-256 도 `build.py` 에 박는다. `tun2proxy.dll`·`udpgw-server.exe` 는 넣지 않는다. 문제 있는 판은 `T2P_SKIPPED`(새 판으로 치지 않고 도우미도 알리지 않는다) |
| 내장과 확인 | 둘을 gzip 자원으로 싣고, 연결할 때마다 `%ProgramData%\WebTether\bin\<묶음 id>\` 에 (없거나 다르면) 풀어 **SHA-256 을 확인한 핸들을 tun2proxy 가 끝날 때까지 쥔다**(`FileShare.Read` — 그동안 바꾸기·지우기·그 폴더와 위 폴더의 이름 바꾸기가 막힌다, 시험). 다르면 다시 풀고 그래도 다르면 띄우지 않는다. 폴더에 낯선 파일이 있으면 지우고 다시 푼다 — 관리자 tun2proxy 는 제 폴더에서 DLL 과 `route.exe`·`netsh` 를 먼저 찾는다. 관리자로 돌면 그 폴더의 주인을 Administrators 로, 다른 이의 쓰기를 막는다(`IsLockedDown` 이 확인). 확인·띄우기는 그 폴더를 지우기·이름 바꾸기를 막은 채 열어 얻은 **실제 경로**로 한다 — 사용자 경로의 `bin` 을 접합점으로 두고 확인 뒤 대상을 바꾸면 글로 된 경로가 끼워 넣은 exe 를 가리켰다(시험 — 지금은 관리자 전용 폴더라 끼울 수 없지만 둔다. `bin` 이 재분석 지점이면 풀지 않고 오류) |
| 명령줄 | 셸 없이, 인자는 `CommandLineToArgvW` 규칙으로 싼다(시험이 API 와 실제 자식으로 되돌려 본다). 현재 폴더는 System32 — tun2proxy 는 시작할 때 현재 폴더와 그 위에서 `.env` 를 읽는다(dotenvy). 자식 환경에서 `RUST_*` 를 뺀다. 기본값은 배치 파일과 같은 뜻(`--dns direct --verbosity info` 를 밝혀 적을 뿐) |
| 끊기 | 자식의 (보이지 않는) 콘솔에 잠깐 붙어 Ctrl+C(`AttachConsole`→`GenerateConsoleCtrlEvent`→`FreeConsole`) — tun2proxy 0.8.7 은 ctrlc2 4.0.0 으로 Ctrl+C·Ctrl+Break 를 듣고 `--setup` 을 스스로 걷는다. 5초 안에 안 끝나면 강제로 끝내고 도우미가 걷는다. **도우미가 죽으면**: tun2proxy 를 띄운 곧바로 작업 개체(`JobObject` — `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`, 이름 없음, 핸들은 상속되지 않음)에 넣어 도우미가 어떻게 끝나든 커널이 핸들을 닫으며 tun2proxy 도 끝난다(시험 — 도우미 대역을 강제로 끝내면 손자가 끝난다) — 남은 경로는 다음 실행이 상태 파일로 걷고, '다른 tun2proxy' 로 막히지 않는다. 멈춘 채 띄우지 못해(Process.Start) 띄운 직후 넣는다 — 그 몇 ms 에 tun2proxy 가 띄운 자식은 작업 밖에 남을 수 있다(추론 — `--setup` 의 route·netsh 는 TUN 을 세운 뒤라 늦다고 보았다). 넣지 못하면 로그에 적고 띄운 채 둔다 |
| 걷기(`Routes.CleanupPlan`) | 0.8.7 의 `--setup`(tproxy-config 7.0.9): `route add 0.0.0.0 mask 0.0.0.0 10.0.0.1 metric 6`, `--bypass` 마다 원래 게이트웨이로 metric 1, TUN 의 DNS. 정상 종료는 **기본 경로를 모두 지우고** 원래 게이트웨이로 metric 200 을 다시 넣는다 — 그 사이에 죽으면 기본 경로가 없다. 그래서 연결 전에 기본 경로와 **이미 있던 같은 우회 경로**(`kept=` — 사용자가 미리 둔 것)를 상태 파일(`%ProgramData%\WebTether\connection-state`)에 적고, 끝난 뒤·다음 실행 때 ① **TUN 어댑터에 걸린** 10.0.0.1 기본 경로(같은 10.0.0.1 이라도 다른 어댑터면 그대로 — 지우기에 `if <어댑터>` 를 붙인다, 없으면 route.exe 가 모든 어댑터에서 지운다) ② 우리가 넘긴 우회 경로(다음 홉이 그때의 게이트웨이인 것만, 연결 전부터 있던 것은 빼고 — tun2proxy 자신의 Ctrl+C 걷기가 그것을 지우는지는 확인하지 않았다) ③ TUN 밖 기본 경로가 하나도 없으면 적어 둔 것을 metric 200 으로(그 어댑터가 있을 때만) — `System32\route.exe` 로. **걷지 않는 때**(11-27 점검 — 이름이 아니라 우리가 띄운 것으로): 우리가 띄운 tun2proxy(그 PID·핸들 — 작업 개체 안)가 아직
살아 있을 때(끝내기가 늦다), 그리고 이름이 tun2proxy 로 시작하는 **다른** 프로세스가 있으면서 TUN 어댑터가 떠 있을 때(배치 파일로 띄운 진짜 tun2proxy — 같은
`USBTether` 이름을 쓴다). 이름만 빌린 프로세스는 TUN 이 없으면 걷기를 막지 못한다(로그에 적고 걷는다 — 보통 권한의 프로그램이 관리자의 걷기를 영영 막던
틈). ③ 의 되살리기는 연결 전에 적은 어댑터 **번호와 GUID** 를 함께 견준다(`default=… <GUID>` — 그 번호가 다른 어댑터에 돌아갔으면 넣지 않는다, GUID 가 없는
옛 상태 파일은 번호만). 걷기는 **일꾼 스레드에서** 돌고(화면이 멈추지 않게), 도는 동안은 Tick 이 쉬고 새 연결이 그 끝을 기다린다(걷는 사이 새 tun2proxy 가 경로를 넣어 섞이지 않게). 시작할 때의 흔적 걷기도 같다 |
| 연결 | 다른 tun2proxy(이름 — 우리 자식의 PID 는 뺀다)가 떠 있으면 띄우지 않는다(배치 파일의 것과 TUN 이 부딪친다 — 이름만 빌린 프로그램도 막을 수 있지만 보이는 오류일 뿐이다) → 192.168.49.2–254 를 가진 어댑터(없고 Wi-Fi 프로필을 적었으면 `netsh wlan connect name="…"` 뒤 15초) → **그 어댑터의 Wi-Fi 이름이 접두어로 시작하는가**(아래 'Wi-Fi 이름') → 192.168.49.x 어댑터가 **둘 이상**이면 `GetBestInterface(192.168.49.1)` 이 그 이름을 본
어댑터인가(11-27 점검 — 아니면 트래픽이 이름을 검사하지 않은 어댑터로 간다. 다르거나 모르면 손으로 누른 '연결' 도 **묻지 않고** 띄우지 않는다, 연결된 동안 그렇게
되면 끊는다 — `NetState.RouteCheck`·`HotspotInfo.RouteMismatch`) → 폰 프록시 확인(SOCKS5 `05 01 00`→`05 00`, http 는 `CONNECT 127.0.0.1:9` 에 HTTP/1.x 답 — 폰은 403) → 풀기·확인 → 띄움 → 2초 살고 TUN 어댑터가 보이면 '연결됨'. **30초 안에 TUN 어댑터가 나타나지 않으면** 끄고 걷고 오류(다시 하지 않는다 — `ConnectTimeoutSeconds`). 기다리는 사이(Wi-Fi·폰 프록시 확인·풀기) 끊으면 거기서 그만둔다. 끊는 일은 자식마다 하나 — 핫스팟이 사라져 끊는 사이 '끊기' 를 눌러도 Ctrl+C·걷기를 두 번 하지 않고, 사용자가 끊었으면 그 뒤 '오류·대기' 로 덮지 않는다. 핫스팟이 사라지거나 자식이 끝나면 풍선을 띄우고 간격마다 다시(핫스팟이 없으면 횟수를 쓰지 않고 기다린다, 1분 넘게 붙어 있었을 때만 횟수를 0 으로). 걷지 못했으면(tun2proxy 가 남았다) 상태 파일을 남긴다 |
| 자동 연결(사용자 결정 11-21, 11-27 서비스) | **서비스가 뜨면 늘 저절로 연결한다** — 부팅 때(자동 시작 — 11-28 부터 지연 없이), **로그온 전에도**, 로그오프 뒤에도 그대로(사용자 결정 11-27). 사용자의 '끊기'(트레이·상태 창 → 파이프의 `disconnect`)는 **서비스의 메모리에만** 남는다(`userWantsOn` — 사용자 결정 11-21 둘째): 서비스가 도는 동안은 Tick 도 다시 연결하지 않고 '연결' 로만 다시 붙으며, **서비스가 다시 시작하면(재부팅 포함) 저절로 연결한다**. 트레이를 끝내도 끊기지 않는다. 파일에 남기지 않는다 — 예전 판의 `%ProgramData%\WebTether\user-disconnected` 는 읽지도 지우지도 않는다(그 코드를 걷었다 — `AppPaths.UserOffFile`·`MarkUserOff`·`UserDisconnected`). 오류·핫스팟 사라짐·다시 연결 포기·트레이의 '끝내기'·로그오프도 끊음이 아니다. 서비스가 시작할 때(`AutoConnectAsync`) 앞 연결의 흔적 걷기가 끝난 뒤 연결하고, **처음 180초**(`StartupWaitSeconds`)는 핫스팟·폰 프록시가 없거나 Wi-Fi 이름이 접두어 밖이어도 횟수를 쓰지 않고 '다시 연결' 설정과 상관없이 5초(`StartupRetrySeconds` — '다시 연결 간격' 이 더 짧으면 그것)마다 다시 본다('다시 연결 대기 · … — 기다리는 중') — 부팅 직후에는 Wi-Fi 가 늦게 붙는다(로그온 전에 붙는 것은 '모든 사용자' 프로필뿐 — 아래 '서비스'). 그 뒤는 늘 쓰는 규칙: '다시 연결' 이 켜져 있으면 핫스팟이 없거나 이름이 접두어 밖인 동안 횟수 없이 계속 기다리고(까닭을 고쳐 보인다, Wi-Fi 프로필이 있으면 핫스팟이 없을 때의 netsh 시도마다 횟수), 꺼져 있으면 오류와 풍선('저절로 연결하지 못했습니다'). tun2proxy 를 띄운 뒤의 실패(자식이 끝남 등)는 처음부터 늘 쓰는 규칙이다. **꺼짐·오류로 멈춘 뒤에도 저절로 다시 시작한다**(9차 판정 11-28 — `Controller.RearmAsync`: 부팅 때 한 번만 시작하면 180초 + 다시 연결 20회×10초 뒤 '다시 연결을 멈췄습니다' 로 끝나, 폰의 테더링을 나중에 켜거나 로그온이 늦으면 사용자가 '연결' 을 눌러야 했다): 사용자가 '끊기' 를 누른 것이 아니고(`userStopped` — 다음 '연결' 이나 서비스 재시작까지) '다시 연결' 이 켜져 있으면 ① **망이 바뀌어**(서비스가 `NetworkChange.NetworkAddressChanged`·`NetworkAvailabilityChanged` 를 받아 UI 스레드로 — `Controller.NetworkChanged`) 접두어에 맞는 Wi-Fi 가 보일 때 — **마지막으로 멈춘 까닭이 tun2proxy 를 띄우기 전의 되풀이할 수 있는 실패(핫스팟·폰 프록시 없음·길 어긋남·접두어 밖)일 때만**(10차 판정 11-28 — `rearmOnNetwork`). 띄운 뒤의 실패(자식이 끝남)나 되풀이하지 않는 오류(TUN 이 나타나지 않음·다른 tun2proxy·설정)로 멈췄으면 망 바뀜만으로는 다시 시작하지 않는다 — 뜨자마자 죽는 tun2proxy 는 시도마다 TUN 을 만들고 걷어 망 바뀜 알림을 스스로 일으켜, 남은 알림으로 다시 시작하고 횟수를 0 으로 돌리기를 끝없이 되풀이했다(SYSTEM 이 10초마다 tun2proxy·`--setup` 경로 바꾸기·알림). 알림은 꺼짐·오류로 들어선 뒤 **10초**(`NetworkQuietSeconds` — 우리가 걷은 TUN 의 알림)가 지나 온 것만 세고, 시도 중·연결 중의 알림은 버리며 시작할 때 남은 깃발을 지운다 ② 알림을 놓쳤을 때의 물러서기로 **30초마다**(`RearmPollSeconds`) 핫스팟을 보아 접두어에 맞는 Wi-Fi 가 **새로 나타났을 때**(멈춘 뒤 처음 본 것은 바탕만 — 이미 있던 망으로 되풀이하지 않는다) ③ **콘솔 세션에 로그온·연결할 때**(`ServiceCore.SessionChanged` — SCM 의 `SessionLogon`·`ConsoleConnect`, 다른 세션은 보지 않는다) 서비스가 뜰 때와 같이 시작한다(180초의 너그러운 기다림부터). 접두어 밖 Wi-Fi 는 지금처럼 저절로 붙지 않는다. 로그의 '저절로 연결을 다시 시작합니다 — 까닭' 은 같은 까닭이면 10분에 한 줄(`RearmLogQuietMinutes`). **다시 시작에는 예산이 있다**(10차 판정): 한 시간에 **3번**(`RearmPerHour`), 잇달아 하면 간격을 **60초**(`RearmBaseGapSeconds`)에서 두 배씩(한 시간까지) — 1분 넘게 붙어 있었거나 사용자가 '연결' 하거나 콘솔에 로그온하면(`RearmAsync(…, renew)`) 새로. 예산이 막으면 '저절로 다시 시작을 미룹니다 — 까닭 (…)' 을 적고(같은 까닭은 10분에 한 줄), 그 Wi-Fi 가 내내 보이면 예산이 열린 뒤의 점검이 잇는다(사라지면 버린다 — 다시 나타남이 새로 청한다). 트레이 알림 '연결이 끊겼습니다'·'다시 연결을 멈췄습니다' 는 1분 넘게 붙어 있거나 사용자가 '연결' 할 때까지 한 번씩만(되풀이는 로그에만). 다른 tun2proxy·틀린 설정은 지금처럼 곧바로 오류 — 다른 tun2proxy 는 그 PID 와 '이전 판 도우미라면 그 알림 영역 아이콘의 ‘끝내기’ 로 끝낸 뒤 다시 연결하십시오' 를 상태(트레이)와 로그에(11-28 — 옛 판 도우미가 떠 있으면 그 `tun2proxy-bin` 이 여기에 걸린다. 사용자 기기에서 그랬다). 옛 판이 남긴 TUN 어댑터 `USBTether` 만 있고 tun2proxy 가 없을 때 새 tun2proxy 가 서는지는 확인하지 못했다(실패하면 'tun2proxy 가 끝났습니다(코드 n)' 와 그 출력이 로그에) |
| Wi-Fi 가 끊길 때 — 경로 고리(11-28) | **Wi-Fi 링크가 내려가면 폰(192.168.49.1)으로 가는 길이 TUN 이 된다** — Wi-Fi 의 on-link `192.168.49.0/24` 를 쓸 수 없고 남은 길은 `--setup` 이 넣은 TUN 의 기본 경로(`0.0.0.0/0 → 10.0.0.1`)뿐이다. tun2proxy 0.8.7 은 바깥 연결을 인터페이스에 묶지 않으므로(`TcpStream::connect` 그대로 — 그런 옵션도 없다, `--bypass` 는 경로만 더하고 그 경로도 링크와 함께 못 쓰게 된다) 제 폰 연결이 제 TUN 으로 돌아와 새 세션이 되고 그 세션이 또 폰으로 연결을 열어 불어난다(사용자 기기 11-28 — 12:15:16·12:16:25 의 Wi-Fi 끊김(WLAN-AutoConfig 11004·8003 'temporary-disconnect')과 시각이 맞았다. 바탕 원인은 잦은 Wi-Fi 끊김이다 — 폰 쪽인지 PC 드라이버인지는 모른다). 1초 Tick 의 '핫스팟 사라짐' 은 1~2초 늦다. 두 겹으로 줄인다: ① **경로 고리 감지**(`LoopGuard` — `LinkWatch.cs`) — tun2proxy 의 출력에서 'Beginning #n TCP|UDP … -> <프록시 주소>:<포트>'(0.8.7 의 `log::info!("Beginning {session_info}")`, SessionInfo 꼴 `#{id} {protocol} {src} -> {dst}`)를 보면 — 줄 꼴은 0.8.7 그대로만 받는다(12차): 줄 첫머리가 env_logger 0.11 의 기본 머리 `[<UTC 초까지 'YYYY-MM-DDThh:mm:ssZ'> INFO  tun2proxy] `(수준은 5칸으로 채움, 대상은 lib.rs 의 모듈 경로)이고 바로 뒤가 'Beginning #' 이며 목적지 뒤에 아무것도 없는 줄, 주소는 점 넷의 십진 IPv4(칸마다 0–255, 앞의 0 없음 — `IPAddress.TryParse` 가 받는 `3232248065`·`0xC0.…`·`192.168.049.1` 은 거절)와 대괄호 IPv6(범위 `%` 없음)만, 포트는 1–65535(앞의 0 없음). 출력이 색을 입으면(ANSI — 파이프라 env_logger 의 auto-color 가 끈다) 줄을 알아보지 못한다. 이렇게 보면 **곧바로 강제로 끝낸다**(Ctrl+C 를 기다리지 않는다 — `StopChildAsync(…, hard)`, tun2proxy 의 걷기는 돌지 않고 우리 정리(`Routes.CleanupPlan`)가 맡는다 — 강제 종료와 같은 길). 포트는 보지 않는다(TUN 으로 폰 주소에 가는 세션은 어느 포트든 on-link 경로가 빠졌다는 뜻 — 1080 의 고리, UDP 묶음의 포트, 시스템 프록시의 8282). 로그는 자식마다 한 줄 '경로 고리를 막으려고 tun2proxy 를 멈췄습니다 — …'. 이미 끊는 중(핫스팟 사라짐의 Ctrl+C)이면 끝내기만 앞당기고 상태는 그 끊기가 정한다. **분류는 '띄운 뒤의 실패'**(`Fail` 되풀이 가능·띄우기 전 아님 — 횟수를 0 으로 돌리지 않는다): 핫스팟이 그대로인데 길만 틀린 고장이면 핫스팟 사라짐(횟수를 0 으로)처럼 다루면 끝없이 띄우고 끊는다. Wi-Fi 가 정말 사라졌으면 대기가 횟수를 쓰지 않고 핫스팟을 기다리고, 1분 넘게 붙어 있으면 횟수가 새로 찬다. 상한에서 멈추면 10차의 '망 바뀜만으로는 다시 시작하지 않음' 을 탄다. 감지가 info 의 줄을 읽으므로 **tun2proxy 의 로그 수준을 info 밑으로 내리지 않는다**(`CommandLine.EffectiveVerbosity` — 설정 '로그 수준' 의 off·error·warn 은 tun2proxy 에 info 로 넘긴다. 그 셋을 골라도 로그가 줄지 않는다 — 설정 칸의 뜻과 어긋나므로 설정 창이 그 셋을 고르면(또는 그 값을 읽어 오면) 아래 알림 줄에 'tun2proxy 기록은 경로 고리 감지 때문에 info 이상으로 남깁니다.' 를, 칸의 풍선 도움말에 같은 뜻을 보인다(12차 — 값의 뜻을 '도우미 자신의 기록' 으로 바꾸는 것보다 덜 헷갈린다고 보았다: 도우미 자신의 기록에는 수준이 없고, 옛 설정 파일의 값도 그대로 읽힌다. 기본값을 돌린 알림(`LoadNotice`)이 있으면 그쪽이 먼저다)). ② **앞서 내리기** — 연결 중(자식이 있음)의 망 바뀜 알림(`NetworkChange`)과 Wi-Fi 끊김 알림(`WlanEvents` — `WlanRegisterNotification` 의 ACM disconnecting·disconnected·interface_removal, MSM disassociating·disconnected·adapter_removal, 값은 SDK 10.0.26100 의 헤더로 확인 — 새 의존 없는 P/Invoke, 읽기만 하는 알림)을 받으면 Tick 을 기다리지 않고 곧바로 핫스팟을 보고 사라졌으면 '핫스팟 사라짐' 으로 내린다(`Controller.CheckLinkSoon`). TUN 자신의 알림이면 핫스팟이 그대로라 아무것도 않는다. Wi-Fi 알림은 연결한 핫스팟 어댑터(GUID — `HotspotInfo.AdapterId`)의 것만 보고, 주소가 빠지기 전에 오므로 그 뒤 3초(`FastWindowMs`) 동안 0.1초(`FastCheckMs`)마다 다시 본다. 이 알림은 10차의 '다시 시작 깃발' 에 쓰지 않는다(연결 중에는 내리기에만). 알림을 받지 못하면(Wi-Fi API 없음) 로그에 한 줄 적고 Tick 만으로 간다. **남는 것** — 둘 다 고리가 시작된 뒤(①)나 경로가 빠지는 그 순간(②)에 반응하므로 몇 ms 의 고리는 남는다. 근본은 TUN 쪽으로 폰에 가는 연결을 막는 것(방화벽 규칙 — 사용자에게 따로 묻는다)이나 tun2proxy 의 바깥 소켓을 Wi-Fi 에 묶는 것(상류 기능 — 없다) |
| Wi-Fi 이름(사용자 결정 11-21) | 안드로이드의 Wi-Fi Direct 그룹 오너는 **모두 192.168.49.1** 이라 주소·폰 프록시 응답만 보면 저장된 프로필로 **남의 DIRECT 망**(프린터 등)에 붙었을 때 PC 트래픽 전체를 그쪽으로 보낸다. 그래서 192.168.49.x 어댑터의 Wi-Fi 이름이 **접두어**(설정 '자동 연결 Wi-Fi', 기본 `DIRECT-UT-`, '기본값' 단추 — 비울 수 없고 출력 가능한 ASCII 32자까지(검토 11-27 — 비슷한 글자로 확인 창을 속이지 않게, 옛 파일의 비ASCII 값은 버리고 기본값), 대소문자를 가린다)로 시작할 때만 '핫스팟 있음' 으로 본다(`StatusText.Refusal`). **저절로 하는 시도**(시작·다시 연결)는 아니면 띄우지 않고 묻지도 않으며 상태에 까닭('Wi-Fi 이름이 DIRECT-UT- 로 시작하지 않음(…)'·'Wi-Fi 이름을 알 수 없음(까닭)'). **손으로 누른 '연결'** 은 사용자의 뜻이라 한 번 묻는다(`Controller.AskConnect` → "Wi-Fi '…' 에 연결할까요?", 기본 단추 '아니요' — 이름을 모르는 것도 묻는다. **실제 판은 서비스가 직접 묻는다**(사용자 결정 11-27 — `ServiceCore.AskUser` → `WtsPrompt`: `WTSSendMessage` 로 **콘솔 세션**에 `MB_YESNO|MB_ICONWARNING|MB_DEFBUTTON2|MB_TOPMOST|MB_SETFOREGROUND`, 60초가 지나면 '아니요'. 트레이가 묻지 않으므로 보통 권한 프로그램이 트레이 대신 '예' 를 보낼 길이 파이프에 없다. **콘솔에 로그온한 사용자가 없으면 묻지 않고 연결하지 않는다**(fail-safe — 로그에 '물을 사람이 없어…', 로그온 전·로그오프 뒤에는 접두어에 맞는 망만 붙는다). 묻는 동안 상태 `asking`, 그 창을 다시 앞으로 부르는 길은 없다(아래 `ReshowAsk` 는 시험 판의 것). 창을 띄우지 못하면 연결하지 않는다). 시험 판의 창은 **맨 앞에**(`TopAsk` — user32 `MessageBoxW` 에 `MB_TOPMOST`·`MB_SETFOREGROUND`, 주인 창 없음: 늘 위 무리에 그려져 보통 창 뒤에 숨지 않고, 시스템이 전면 권한을 주지 않으면 초점은 그대로이고 작업 표시줄 단추가 깜박인다. WinForms 의 MessageBox 는 `MB_TOPMOST` 를 받지 않는다. 묻는 동안 상태는 '연결 중 · 묻는 중 — Wi-Fi 이름', 그사이 '연결' 을 다시 누르면 — 끊기 뒤에 — 새 시도 없이 그 창을 다시 맨 앞으로(`ReshowAsk`). 한계: 다른 '늘 위' 창과는 그 무리 안의 차례를 따르고, 전체 화면 독점 게임·UAC 보안 데스크톱 위에는 뜨지 않는다): '아니요' 면 '꺼짐 · 연결하지 않음', '예' 면 연결하고 **그 이름은 서비스가 도는 동안 다시 연결도 저절로**(메모리에만 — 서비스가 다시 시작하면 잊는다, 이름을 모르는 것의 '예' 는 그 한 번만). 연결된 동안 이름이 **다른 이름으로 바뀌면** 핫스팟이 사라진 것으로 친다(끊고 늘 쓰는 규칙 — 이름을 잠깐 모르게 된 것은 그대로 둔다). 묻는 사이 '끊기' 를 누르면 '예' 여도 띄우지 않는다. **저절로 하는 시도가 도는 사이 누른 '연결'**(끊기 → 연결 — 단추는 시도 도중에도 '꺼짐' 이면 '연결' 이다)은 새 시도를 띄우지 않고(tun2proxy 가 둘 서지 않게) 도는 시도를 손 시도로 올린다(`manualRequested`) — 그 시도가 이름을 접두어 밖(또는 모름)으로 보면 거절하지 않고 묻는다. 그 뜻은 그 시도까지만(다음 저절로 하는 시도는 묻지 않는다), 그 뒤 '끊기' 면 사라진다(검토 11-21 — 전에는 묻혀 묻는 창 없이 거절로 끝났다). **이름을 모른 채 '예' 로 붙은 망**은 이름이 알려지면 다시 본다(`LostWhy`): 접두어 밖이고 이 실행에서 허락한 이름도 아니면 끊고 상태·풍선에 '이름을 모른 채 연결한 Wi-Fi 가 '…' 로 확인됨 — DIRECT-UT- 로 시작하지 않음', 맞으면 그 이름을 기억해 그 뒤 바뀌는 것을 잡는다. 여전히 모르는 동안은 끊지 않는다(검토 11-21). 이름 얻기(`WifiName`, 읽기만): 먼저 **Native Wifi API**(`WlanQueryInterface` 의 current_connection — 어댑터 GUID 로, SSID 바이트를 UTF-8 로), 그것이 **위치 권한으로 거절되면**(ERROR_ACCESS_DENIED — Windows 11 24H2 부터, 문서에 관리자 예외가 없다. 이 PC 는 위치가 꺼져 있어 거절됐고 `netsh wlan show interfaces` 도 같은 까닭으로 SSID 를 주지 않았다) **네트워크 목록 이름**(NLM — WMI `root\StandardCimv2` 의 `MSFT_NetConnectionProfile.Name`, 위치와 상관없다 — Windows 가 Wi-Fi 에 처음 붙을 때 SSID 로 짓는 이름이라 앞머리는 같다, 같은 이름이 또 있으면 뒤에 ' 2')으로 물러선다. 유선·Wi-Fi 가 아닌 어댑터·서비스 꺼짐은 물러서지 않는다(이름 모름) — **24H2 의 위치 검사는 어댑터를 보기 전에 해서 유선·TUN·없는 GUID 도 거절(5)이 온다**(이 PC 에서 확인 — 그대로 물러서면 유선 어댑터에도 네트워크 목록 이름(유선은 DNS 접미사로 짓는다)을 썼다, 독립 검토 11-21에서 고침): 그래서 거절이면 위치와 상관없는 `interface_state`(opcode 6)로 **연결된 Wi-Fi 어댑터인지** 따로 보고 그때만 물러선다(`WifiSsid.DeniedOnConnectedWlan`). WMI 질의는 연결·열거 5초 시한(`WifiName.NlmSearcher` — 멈추면 이름 모름. 시한이 없으면 WMI 가 멈출 때 1초 점검이 영영 섰다). 어댑터마다 **10초 기억** — 1초마다 Wi-Fi API 를 부르면 위치가 켜진 PC 에서 '위치 사용 중' 표시가 늘 뜬다(문서). **연결을 정하는 자리(`Attempt`)는 기억을 버리고 새로 본다**(`Controller.ForgetWifiNames` — 기억한 옛 이름으로, 핫스팟이 사라진 직후 같은 어댑터가 붙은 남의 DIRECT 망에 저절로 연결했다, 검토 11-21). 1초 점검·상태 창은 기억을 쓴다 — 연결된 동안 다른 망으로 옮겨 간 것을 알아채는 데 10초쯤 늦을 수 있다. **묻는 창에 '예' 라고 하면 핫스팟을 다시 보고** 허락한 이름(또는 접두어에 맞는 망)일 때만, 이름을 모르던 것은 같은 어댑터·주소일 때만 잇는다(창이 떠 있는 사이 망이 바뀌면 '꺼짐 · 연결하지 않음 — 묻는 사이 Wi-Fi 가 바뀜', 검토 11-21). **한계 — 이름은 인증이 아니다**: 누구나 AP 이름을 `DIRECT-UT-…` 로 지을 수 있고(가짜 AP), 이름을 모르던 망을 '예' 로 허락하고 붙은 뒤에는 이름을 끝내 모르는 동안 망이 바뀌어도 알아채지 못한다. 막는 것은 저장된 프로필·자동 연결로 **남의 DIRECT 망(프린터 등)에 실수로 붙은 때**뿐이다. 네트워크 목록 이름은 관리자가 바꿀 수 있다(보통 권한의 프로그램이 바꿀 수 있는지는 확인하지 못했다). 로그에는 SSID(제어·방향 제어 문자를 뺀 것)와 어디서 얻었는지만, 상태 창의 핫스팟 줄에도 이름(네트워크 목록 이름이면 그렇다고) |
| 막는 호출은 일꾼 스레드에서(사용자 결정 11-21) | **1초 점검**(`TickAsync`)의 살피기 — 핫스팟·SSID(`HotspotFinder`)·TUN 어댑터(`TunPresent`) — 는 `Task.Run` 으로, 결과만 UI 스레드로 돌아온다. 살피는 동안(`tickRunning`)의 Tick 은 건너뛰어 겹치지 않고, 돌아왔을 때 그사이 상태가 바뀌었거나(`version` — `Set` 마다 오른다) 자식이 바뀌었거나 끊는·걷는·시도하는 중이면 그 결과를 버린다(다음 Tick 이 다시 본다 — 살피는 사이 '끊기' 를 눌렀는데 늦게 온 '핫스팟 없음' 이 '대기' 로 덮던 틈). 살피기가 던지면 그 Tick 은 아무것도 하지 않는다(실패를 '핫스팟 없음' 으로 읽어 끊지 않는다). **연결 시도**의 다른 tun2proxy 목록(`Tun2proxyLister`)·핫스팟 살피기도 일꾼 스레드에서. **상태 창**의 핫스팟 줄은 2초마다 일꾼 스레드에서 살피고 창이 없어진 뒤 돌아온 결과는 버린다. **설정 저장**(시험 판은 설정 파일 쓰기, 실제 판은 서비스에 제안하고 확인 창의 답 기다리기)도 일꾼 스레드에서 — 그동안 칸·단추를 막고 '저장 중…', 창을 닫지 않는다(닫기·Esc 가 듣지 않는다). 돌아왔을 때 창이 없어졌어도 저장한 설정은 Controller 에 건다(파일과 메모리가 어긋나지 않게). '끝내기' 는 도는 저장을 기다린 뒤 끝낸다 |
| 단추 '정리 중' | 트레이·상태 창의 '연결/끊기' 는 `Controller.Toggle` 하나로 정한다 — 자식이 돌거나 연결 중·대기면 '끊기', **자식은 끝났는데 경로 걷기나 끊기의 마무리가 아직이면 '정리 중'(누를 수 없음)**, 그 밖은 '연결'. 예전에는 끊은 직후 걷는 몇 ms~몇 초 동안 상태가 '연결됨' 인 채 자식이 없어 '연결' 로 보였다. 그때 눌러도 새 시도는 끝내기·걷기가 끝나길 기다렸지만(`Attempt` — 끝내는 중이면 그것부터, 이어 `pendingCleanup`) 끊는 쪽의 마무리(핫스팟 사라짐의 대기, 끊기의 '꺼짐')가 새 시도를 덮을 수 있었다 — 지금은 새 시도가 돌면 덮지 않는다. 걷기가 끝나면 `Changed` 로 알려 트레이 메뉴도 돌아오고, 메뉴를 열 때 다시 정한다. 누른 때 다시 정하므로 낡은 글을 눌러도 '정리 중' 이면 아무것도 하지 않는다. **자식이 끝났는데 그 알림이 아직 UI 스레드에 닿지 않았으면**(`child` 가 남았다) '끊기' 이고 '연결' 은 아무것도 하지 않는다(11-21 3차 검토 — 그 틈에 '연결' 로 보여 새 tun2proxy 가 앞 자식의 걷기보다 먼저 서고, 앞 자식은 걷히지 않은 채 잊혔다) |
| 설정 | `%ProgramData%\WebTether\settings`(한 줄 한 키, 아는 키만, 값마다 검사 — 틀린 키만 버린다. **파일 읽기(`Parse`)와 제안(`FromProposal`)이 같은 값 규칙 `Settings.CheckValue` 를 쓴다**(검토 11-27 — 둘이 달라 옛 파일 값이면 트레이가 지금 설정을 읽지도 못했다): 걸린 키는 파일에서는 버리고 기본값(로그에 '값을 버림: 키 (까닭)'), 제안에서는 통째로 거절. 단 파일의 **우회 목록은 틀리거나 넓은 칸만 빼고**(로그에 '우회 목록에서 뺌') 나머지를 `, ` 로 다시 잇는다. **우회 목록의 저장 꼴은 하나**(`Settings.NormalizeBypass` — 칸을 갈라 `, ` 로 잇기, 7차 판정): `Apply`·제안의 길이 검사(날 글과 저장 꼴 둘 다)·파일 읽기가 이 꼴을 써서, 확인 창에 보이는 것이 저장하는 것이고 저장한 것을 다시 읽으면 글이 바뀌지 않는다 — 전에는 빈칸으로 이은 1000자 가까운 목록이 제안을 지나 저장된 뒤 다시 읽을 때 `, ` 로 길어져 통째로 버려졌다. 파일 읽기가 키를 기본값으로 돌렸으면(우회 목록은 칸을 뺐으면) 서비스가 `settings` 답의 `defaulted` 로 싣고 설정 창이 열 때 '설정 일부를 기본값으로 돌렸습니다: <이름>' 한 줄을 보인다(제안이 적용되면 지운다)). 칸마다 '기본값'. **설정 창은 트레이의 것**(사용자 결정 11-27 '서비스 확인 창으로' — 관리자 판 `--settings`·`reload` 명령은 걷었다): 열 때 파이프의 `settings` 로 지금 설정을 읽고(`ServiceSettingsHost`), 저장하면 `propose` 로 새 설정 **전체**(`set_<키>`)를 보낸다. 서비스(`ServiceCore.Propose`)는 ⓪ **설치본의 트레이가 보낸 것만** 받는다(손님의 이미지(`QueryFullProcessImageName`)와 `ServicePlan.InstalledExe` 를 **열어서** 볼륨 일련번호 + 파일 ID 로 견준다 — `FileIdentity.Same`, 경로 글을 견주면 8.3 이름·접합점에 휘둘린다(검토 11-27). 공유 모드는 넉넉히·링크는 따라간다·64비트 파일 ID(ReFS 의 128비트는 보지 않는다). 아니면 '설치된 트레이에서만 바꿀 수 있습니다', 위 '사용자 결정' 의 한계) ① `Settings.FromProposal` 로 거른다 — 아는 키가 모두 한 번씩, 값마다 `CheckValue`(길이 — **우회 목록 1000자**(여러 /16 을 적는 정상 사용이 200자를 넘는다)·나머지 200자(`MaxValueLength`), 제어·서식(방향 제어 포함)·줄 나눔 글자·**U+0020 밖의 빈칸**(NBSP·전각 빈칸)·**겹친 빈칸·앞뒤 빈칸**, **확인 창의 기호 `→ ⟨ ⟩ ‘ ’`**(값이 줄의 꼴을 흉내 내지 못하게), **Wi-Fi 프로필의 한 낱말 안에 라틴과 키릴·그리스·전각(FF00–FFEF)이 섞인 것**(비ASCII 자체는 받는다 — `MixedScript`))를 지나고, **SSID 앞머리는 출력 가능한 ASCII 만**(규격이 `DIRECT-` + 영숫자 — 비슷한 글자로 속이지 못하게), 검사기(`Validate`)를 모두 지나야 한다(틀리면 묻지도 않고 '설정 값이 틀렸습니다'. 파일 읽기와 달리 틀린 키만 버리지 않는다). 검사기 자체도 좁혔다(검토 11-27): **프록시 주소는 폰의 주소 — 점 네 개의 십진 IPv4 로 192.168.49.1–254 만**(`IsPhoneAddress` — 긴 이름의 꼬리가 확인 창에서 잘려 숨고 `192.168.49.l` 이 옛 값처럼 보였다), **우회 목록의 한 칸은 /8 이상이고 `0.` 으로 시작하지 않게**(`IsNarrowBypass` — `0.0.0.0/1 128.0.0.0/1` 로 터널을 통째로 비켜 갔다). 예전 설정 파일에 이 밖의 값이 있으면 읽을 때 그 키를 버리고 기본값을 쓴다(로그에 '값을 버림' — 사용자 기기의 파일은 관리자 전용이라 읽어 보지 못했다, 기본값 `192.168.49.1`·빈 우회 목록이면 영향이 없다) ② 바뀐 것이 없으면 묻지 않는다 ③ 손님의 세션이 콘솔 세션이어야 한다 ④ 묻는 창은 한 번에 하나(Wi-Fi 물음과 함께 — 떠 있으면 '다른 확인 창이 떠 있습니다'), **어떤 결과든 끝난 뒤 10초**(`ProposalAfterMs` — 사용자가 저장한 직후 남의 제안이 끼어들지 않게), 거절·시간 초과 뒤 90초(`ProposalQuietMs`), 앞의 거절에서 10분 안에 거절이 되풀이되면 300초(`ProposalQuietLongMs` — 적용되거나 10분(`ProposalRejectWindowMs`)이 지나면 다시 처음부터)는 '잠시 뒤 다시 하십시오'(피로 공격 — 쉼은 손님을 가리지 않는다, 검토의 의견대로 받아들였다) ⑤ **거른 객체로 확인 창의 글을 짓는다**(`SettingsConfirm.Text` — 머리에 '요청: <경로> (PID n)'(`QueryFullProcessImageName`, 못 얻으면 '알 수 없음'), 바뀐 칸마다 '이름: ‘옛값’ → ‘새값’'(글 값과 요청 경로는 ‘ ’ 로 감싼다 — 빈 값은 '(없음)'), 켬·끔·매일 꼴. **자르지 않는다** — 60자마다 줄을 바꿔 다 보인다. 옛 값(파일)의 보이지 않는 글자·U+0020 밖의 빈칸·겹친·앞뒤 빈칸은 `⟨U+XXXX⟩` 로 드러낸다. 값이 다른데 보이는 글이 같거나 글 전체가 3000자를 넘으면 묻지 않고 거절 — '그 밖 N개' 로 줄이지 않는다) WTSSendMessage 로 콘솔 세션에 묻는다(제목 'Web 테더 — 설정 확인', 기본 '아니요', 60초 뒤 '아니요') ⑥ '예' 면 **같은 객체**를 저장하고 건다(예전 `reload` 와 같은 길 — `Controller.ApplySettings`). 파이프는 곧바로 답하고(`asking`·번호) 결과는 상태의 `proposal_id`·`proposal_result` 와 **최근 결과 여덟**(`proposal_recent` — 번호:결과)으로 알린다 — 트레이는 최근 결과에서 제 번호를 찾아(`ServiceSettingsHost.Judge`) '저장했습니다' 또는 '저장하지 못했습니다: 확인 창에서 거절했습니다(또는 시간이 지났습니다)'·'다른 확인 창이 떠 있습니다'·'잠시 뒤 다시 하십시오'·'이 화면에서는 확인할 수 없습니다'·'설치된 트레이에서만 바꿀 수 있습니다', 제 결과를 모르는 채 **다른 번호**가 보이면 '다른 요청이 처리됐거나 서비스가 다시 시작됐습니다. 다시 저장하세요.' 로 기다림을 끝낸다(번호는 **서비스가 뜰 때마다 무작위**(1…2⁴⁸)로 시작해 하나씩 오른다 — 다시 뜬 서비스의 번호가 트레이가 기다리는 옛 번호와 겹치지 않게, 검토 11-27. 그래서 크기가 아니라 '다른가' 로 본다). 기다리는 동안 설정 창은 '확인 창을 기다리는 중…'. 설정에는 비밀이 없다(읽는 `settings` 명령은 콘솔 사용자에게 지금 값을 보인다 — 상태 폴더는 사용자가 읽지 못하는 관리자 전용 폴더다). 'Windows 시작 시 실행' 칸과 그 작업 스케줄러 등록·시작 스크립트 `launch.cmd`·'옛 사본 바꾸기' 는 걷었다(서비스가 대신한다 — 코드와 서술은 git 이력 `d95c1fcb`). 설정 파일의 옛 `autostart` 는 문제로 적지 않고 버린다(`Settings.RemovedKeys`) |
| 설치(`--install`, 11-27) | 트레이가 뜰 때 서비스가 없거나 서비스의 exe 가 이 판과 다르면(SHA-256 — `ServiceCheck`) 맨 앞 창으로 한 번 묻고('서비스가 설치되어 있지 않습니다. 설치할까요? (관리자 승인)'·'서비스가 이 판과 다릅니다. 이 판으로 바꿀까요? (관리자 승인 — 잠깐 끊깁니다)', 기본 '아니요'), 트레이 메뉴의 '서비스 설치·고치기' 도 같다 — UAC(`runas`)로 이 exe 를 `--install` 로 띄워 끝 코드를 본다. **묻기 전과 설치기가 시작할 때 이전 판 도우미를 본다**(`OtherHelpers` — 11-28: 사용자가 옛 판 도우미(관리자)를 끝내지 않은 채 설치해, 새 서비스는 '다른 tun2proxy' 로 연결하지 못하고 사용자는 옛 판의 연결을 새 판의 것으로 봤다): 이름이 `WebTether.exe` 인 다른 프로세스가 떠 있으면(**읽기만** — 끝내지 않는다. 빼는 것: 자기·서비스(세션 0 이고 PID 가 SCM 의 서비스 PID — 시작·멈추는 중에도, `ScmManager.ServicePidReadOnly`)·이 exe 와 같은 파일로 뜬 것(설치기를 띄운 트레이 — `FileIdentity`). 이미지 경로를 읽지 못하는 것은 '떠 있음') 묻지 않고 설치하지 않으며 '이전 판 도우미가 실행 중입니다 — 알림 영역 아이콘의 ‘끝내기’ 로 끝낸 뒤 다시 하세요. (PID n)'(트레이는 풍선, 설치기는 실패 창). 떠 있는 것이 **모두 다른 사용자 세션**(1 이상이고 이 세션이 아님)에 있으면 '다른 사용자 세션에서 도우미가 실행 중입니다 — 그 세션에서 끝내야 합니다. (PID n)'(9차 판정 — 그 아이콘은 이 화면에 없다, `OtherHelpers.Describe`. 이 세션·모르는 세션·세션 0 이 하나라도 있으면 앞의 글). 차례(`ServiceInstaller.Install`): 다른 도우미 확인(`OtherRunning`) → 서비스가 돌면 멈춤 → exe 를 `Program Files\WebTether\WebTether.exe` 로 복사(아래) → 옛 작업 걷기(아래 '옛 작업') → 서비스 만들기·고치기 → HKLM Run → 시작. 설치본이 아닌 exe(내려받기)로 설치했으면 그 트레이를 끝내고 설치본의 트레이를 띄운다 — **못 띄우면 삼키지 않고 창으로 알린다**(`TrayRelaunch` — '설치는 됐지만 트레이를 다시 띄우지 못했습니다: … <설치본> 를 실행하거나 다시 로그온하십시오.', 11-28: 예전에는 실패를 삼켜 트레이가 조용히 사라졌다). **설치 폴더의 권한(11-28)**: `DirPolicy.InstallDir` — 주인 Administrators, **Administrators·SYSTEM 모두 + BUILTIN\Users 읽기·실행**(`ReadAndExecute`, 폴더와 그 아래에 상속), 상속 끊음. 11-27 판은 관리자 전용(`AdminOnly`)이라 **보통 권한의 트레이가 설치본 exe 를 실행하지 못했다** — HKLM Run 의 로그온 실행과 설치 뒤 다시 띄우기가 둘 다 조용히 실패해 사용자 기기에서 트레이 아이콘이 뜨지 않았다(커밋 `4e6a7d0b`). Users 를 고른 것은 Program Files 의 관례(Windows 가 거기에 주는 것이 BUILTIN\Users 의 읽기·실행 — Authenticated Users 가 아니다)를 따른 것이고, 대화형으로 로그온한 사용자는 모두 Users 의 구성원이다. 검사(`SecureDir.CheckDescriptor` — 폴더·파일 모두)는 정책 밖 SID 의 **읽기·실행 줄은 정책 안으로**, 쓰기·지우기·권한 바꾸기 계열 비트(WriteData·AppendData·WriteEA·DeleteChild·WriteAttributes·Delete·WriteDAC·WriteOwner·GENERIC_ALL·GENERIC_WRITE)는 여전히 정책 밖으로 보고, 정책의 Readers 가 그 대상 자신에 듣는 허락 줄로 읽기·실행(FILE_READ_DATA·FILE_EXECUTE — 일반 권한도 풀어 본다)을 갖고 그것을 막는 거부 줄이 없어야 맞다(`MissingReader`). **옛 판의 폴더**(주인·쓰기 권한은 정책대로이고 읽기·실행 줄만 없다)는 비키지 않고 쥔 채 그 줄만 더한다(정책 밖의 누구도 그 안에 심을 수 없었으므로 — 비키면 Program Files 에 옛 폴더가 남는다). 설치본 파일은 폴더에서 물려받고, 새로 쓸 때 **주인을 정책의 주인(Administrators)으로 정한다**(만든 사람이 주인이면 그 사용자가 보통 권한으로도 권한을 바꿀 수 있다). 설치기는 끝에 **설치본 파일의 권한을 정책으로 다시 본다**(`Installer.FileCheck` — 보통 사용자가 실행할 수 있는가의 스스로 확인, 안 맞으면 '설치본의 권한이 맞지 않습니다 — …' 로 실패). 같은 바이트여도 권한이 맞지 않으면 다시 쓴다. `%ProgramData%\WebTether` 는 그대로 관리자 전용(`ServiceInstaller.DataPolicy` — 서비스만 읽고 쓴다. 트레이는 그 안을 읽지 않는다 — 로그·설정은 파이프로, 트레이 제 상태는 `%LOCALAPPDATA%\WebTether`). 트레이가 설치 폴더에서 읽는 것은 제 exe(`SelfImage` — 판 해시)뿐이다. **복사는 경로로 하지 않는다**(`SelfImage`·`Installer`): 돌고 있는 exe 도 이름은 바뀌므로(시험) 같은 사용자의 프로그램이 내려받기 폴더의 exe 를 바꿔치기하면 경로 복사가 그것을 Program Files 로 옮겼다. 시작할 때 `GetMappedFileName` 으로 **지금 돌고 있는 이미지 파일**을 공유 모드 Read 만으로 열어 쥐고(연 핸들의 실제 경로가 그 뒤 다시 얻은 이미지 이름과 같아야 한다 — 쥔 동안 이름 바꾸기가 막혀 같으면 같은 파일), 그 핸들의 바이트를 설치 폴더의 임시 이름에 써 SHA-256 을 견준 뒤 옮기고(옛 파일은 지우고 옮긴다 — `File.Replace` 는 옛 권한을 남긴다) 한 번 더 견준다. 같은 바이트면 쓰지 않는다. 설치 폴더도 정책의 ACL 이어야 한다(위 — 관리자만 쓰기·Users 읽기·실행) — 주인이 Administrators·SYSTEM 이고 링크가 아니면(그 위 Program Files 는 사용자가 쓰지 못한다) **권한을 고치거나 안을 지우지 않고 폴더째 `WebTether.untrusted-<때>` 로 비킨 뒤 새로 만든다**(11-27 재검토 — `SecureDir.Prepare`, ProgramData 와 같은 길. 처음 점검의 고침은 권한을 고친 뒤 안의 것을 경로로 확인하고 경로로 지웠는데, 그 틈에 접합점을 끼우면 관리자가 설치 폴더 밖을 지웠다. 권한이 넓던 동안 남이 넣은 exe 옆 DLL·제 권한을 따로 가진 파일도 새 폴더에는 없다). **비킨 폴더는 지우지 않는다** — 치우기는 사용자 몫(`C:\Program Files\WebTether.untrusted-*`). 비키려면 Program Files 에서 이름을 바꿀 수 있어야 한다 — 이 PC 의 Program Files 는 Administrators 가 (M)·하위에 (F)(읽기만 해서 봤다), 관리자 판(`--install`)은 된다. 비키지 못하면(그 안의 파일이 열려 있음 — 옛 사본이 돌고 있음 등) 설치를 멈추고 '옆으로 비키지도 못했습니다 … 그 폴더를 지운 뒤 다시 실행하십시오' |
| 서비스(11-27 — 관리자 부분을 떼다) | 이름 `WebTether`, 표시 'Web 테더 도우미', **LocalSystem**, **자동 시작(지연 없음 — 11-28)**(11-27 판은 지연된 자동 시작이라 부팅 뒤 1~2분 늦게 떠 사용자가 '저절로 돌지 않는다' 로 봤고, 로그온 전 연결에도 늦다. 고치기(`CreateOrUpdate`)는 지연 깃발을 늘 적어 옛 판의 지연을 끈다 — `ServiceSpec.DelayedStart`), 시작할 때 로그 첫 줄 '[시작] 서비스 Web 테더 <판> · tun2proxy … · 상태 …'(늘 남긴다 — 상태 폴더를 마련하지 못하면 로그를 쓸 곳이 없어 SCM 의 시스템 이벤트 로그만 남는다), 실패 동작 5초·30초·60초 뒤 다시 시작(하루에 0 으로), ImagePath `"C:\Program Files\WebTether\WebTether.exe" --service`(따옴표 — `ServicePlan.ImagePath`, 경로에 따옴표·`%`·제어 글자·UNC·상대·`.exe` 가 아님이면 만들지 않는다). 서비스 DACL(`ServicePlan.Sddl`) — SYSTEM·Administrators 모두, 대화형(`IU`)·인증된 사용자(`AU`)는 **조회만**(`CCLCSWLORC` — 시작·멈추기·바꾸기·지우기 없음). **고친 것**: SCM 은 사용자의 환경 블록·HKCU 를 읽지 않으므로 '시작 시 실행' 의 T1574.012(HKCU\Environment 의 `COR_PROFILER` 등을 CLR 이 관리자로 올림)와 `HKCU\Software\Microsoft\.NETFramework` 의 CLR 설정이 서비스에는 닿지 않는다 — 시작 스크립트(`launch.cmd`)로 비우던 것을 걷었다. 서비스는 Controller 를 창 없는 메시지 고리 스레드에서 돌리고(`ServiceCore` — STA, `WindowsFormsSynchronizationContext`), 로그는 상태 폴더의 파일과 메모리 고리(3000 줄 — 트레이가 `log` 명령으로 받아 간다). 시작할 때 이 프로세스의 환경에 `COR_`·`COMPlus_` 류 변수가 있으면 로그에 경고만(`WarnClrVariables` — SCM 의 환경은 시스템 것이다). 멈출 때 끊고 걷기를 15초까지 기다린다(`RequestAdditionalTime`). 연결 시도의 Wi-Fi 묻기는 위 'Wi-Fi 이름' — WTSSendMessage. `netsh wlan connect`(Wi-Fi 프로필)는 SYSTEM 이 부르므로 **'모든 사용자' 프로필이어야 한다**고 본다(문서에 '이 사용자만' 프로필을 SYSTEM 이 쓸 수 있는지는 없다 — 실패하면 로그에 그 안내, 설정 칸의 도움말에도 한 줄) |
| 파이프(11-27) | 트레이 ↔ 서비스는 이름 있는 파이프 하나(`PipeNames.Service` — GUID 가 붙은 고정 이름). 서버는 `FILE_FLAG_FIRST_PIPE_INSTANCE`(남이 먼저 그 이름을 쥐면 서비스는 듣지 못하고 로그에 적는다 — 연결 일은 그대로, **5초마다 다시 해 본다**(`PipeRetryMs` — 고리가 끝났을 때도, 실패는 1분에 한 줄) — 검토 11-27: 지연 자동 시작의 서비스보다 먼저 이름을 잡은 프로그램이 놓으면 곧 서비스가 잡는다)·`PIPE_REJECT_REMOTE_CLIENTS`, DACL 은 NETWORK 거부·SYSTEM·Administrators 모두·INTERACTIVE 읽기쓰기(`PipeAcl`). 손님마다 PID·세션(`GetNamedPipeClientProcessId`·`SessionId`)과 흉내 낸 토큰(SID·그룹·무결성)을 보아 **콘솔 세션(`WTSGetActiveConsoleSessionId`)의 대화형 사용자, 중간 무결성 이상만** 받는다 — 원격 데스크톱·NETWORK·낮은 무결성·비대화형은 거절, SYSTEM 은 받음(사용자 결정 11-27, `PipeClientCheck.Verdict`). 손님(트레이)은 서버가 **세션 0 이고 그 PID 가 SCM 이 아는 서비스 `WebTether` 의 PID**(`QueryServiceStatusEx` — 서비스 DACL 의 조회 권한으로 보통 사용자도 읽는다, `ScmManager.ProcessIdReadOnly`)일 때만 믿는다(`PipeClient.ServerVerdict` — 아니면 '서비스가 아닌 프로그램이 통로를 쥐었습니다', SCM 이 서비스를 RUNNING 으로 보지 않으면 '서비스가 시작·멈추는 중이거나 돌고 있지 않습니다' — 어느 쪽이든 믿지 않는다). 세션 0 만 보던 것은 검토 11-27 이 짚었다: 보통 사용자도 S4U 예약 작업 등으로 세션 0 에 프로세스를 띄울 수 있어, 지연 자동 시작과 HKLM Run 트레이 사이에 이름을 먼저 잡은 가짜 서버를 믿었다. 11-28 에 일반 자동 시작으로 당겼다(위 '서비스' — 그 틈도 더 준다). 흉내 수준은 Identification. 말은 길이(4바이트 LE)를 앞에 붙인 `key=value` 줄(이스케이프, 64 KiB·키 64개·값 16K 자 — `PipeMessage`), 명령은 `hello`·`status`·`connect`·`disconnect`·`log(after)`·`settings`(지금 설정 읽기)·`propose`(`set_<아는 키>` 만 — 위 '설정') 뿐이고 모르는 명령·판·덧붙은 인자는 거절(`PipeCommands.Validate`). 연결 하나에 5초 시한(타이머가 스트림을 닫는다). 상태는 트레이가 1초마다 `status` 로 읽는다(`ServiceClient` — 받은 글은 `SafeText.Clean`, 호스트·접두어 검사, 알림 주소는 GitHub 만) |
| 옛 작업(11-27) | 설치가 예전의 '시작 시 실행' 작업을 걷는다 — `schtasks /Query /TN WebTether /XML`(DTD 거절)로 Exec 를 읽어 **우리 것일 때만**(옛 꼴 — 설치본 exe 를 인자 없이, 11-27 꼴 — conhost + `.\launch.cmd` 에 작업 폴더가 설치 폴더) `/Delete /F`, 남의 작업이면 두고 알린다(`LegacyTask.IsOurs`). 설치 폴더의 `launch.cmd`·`launch-env.txt` 도 지운다. 남은 작업이 로그온 때 옛 판을 관리자로 띄우지 않게 |
| 트레이(11-27) | 보통 권한(`asInvoker`), 세션마다 하나(`Local\WebTether-tray` 뮤텍스 — 둘째는 첫째의 상태 창을 부른다), **HKLM Run** 의 `WebTether`(`"<설치본>"` — 모든 사용자의 로그온에 뜬다)로 뜬다. 상태·연결/끊기는 파이프로, 로그 창은 서비스의 로그를 받아 메모리에(파일은 서비스가 쓴다), 업데이트 확인은 트레이가(위 '업데이트 알림'). 메뉴에 '설정'(트레이의 창 — 저장은 서비스의 확인 창으로, 위 '설정'), '서비스 설치·고치기', '서비스 제거'(묻고 UAC — `--uninstall`). 관리자 판을 `runas` 로 띄우기 전에 ① **HKCU 를 읽기만 하고**(`Elevation.Precheck`) `HKCU\Environment` 와 `HKCU\Volatile Environment`(와 그 세션 번호 하위 키 — 로그온 때 환경 블록에 든다)에 .NET Framework 4.8 의 CLR·셔림이 먼저 읽는 변수(`ClrEnv.FrameworkPrefixes` — 앞머리 `COR_`·`COMPlus_`·`APPDOMAIN_MANAGER_`·`__COMPAT_LAYER`, 대소문자 무시. `DOTNET_`·`CORECLR_` 는 Framework 에 뜻이 없어 막지 않는다 — `DOTNET_CLI_TELEMETRY_OPTOUT` 을 둔 개발자 PC 에서 설치가 막혔다, 검토 11-27)가 있거나 `HKCU\Software\Microsoft\.NETFramework` 에 값이 하나라도 있으면(CLR 이 같은 이름으로 읽는 설정 — 무엇이 위험한지 다 알지 못해 이름을 가리지 않는다, 보통 비어 있다) **올리지 않고** 풍선으로 그 이름을 알린다('관리자 권한을 쓰지 않았습니다 — 사용자 설정에 .NET 을 바꾸는 값이 있습니다: …' — 사용자가 지운 뒤 다시) ② 트레이 제 프로세스의 환경에서 CLR 변수를 지운다(`ClrEnv.Scrub` — 이쪽은 넓게 `CORECLR_`·`DOTNET_` 까지: 트레이 자신의 환경이라 지워도 해가 없다). **① 은 완전하지 않다 — 검사와 UAC 창 사이에 같은 사용자의 프로그램이 값을 넣을 수 있다(경합).** **UAC 로 올린 프로세스가 부모의 환경 블록을 물려받는지, 사용자 프로필로 새로 짓는지는 확인하지 못했다**(검토 11-27) — 그래서 ① 을 둔다. 서비스에 닿지 못하면 SCM 의 상태를 읽어(읽기만 — `ScmManager.RawStateReadOnly`) 짧게 보인다(`ServiceClient.UnreachableText` — '서비스가 시작되는 중입니다'·'서비스가 멈춰 있습니다'·'서비스가 멈추는 중입니다'·'서비스가 설치되어 있지 않습니다', 돌고 있는데 닿지 못하거나 모르면 '서비스에 닿지 못함 — 까닭', 11-28). 트레이를 끝내도 연결은 그대로다 |
| 제거(`--uninstall`, 11-27) | 서비스 멈춤 → 지우기 → HKLM Run 값 지우기 → 설치본 지우기(쓰는 중이면 다시 시작할 때 지우게 `MoveFileEx` 로 미룬다) → 빈 설치 폴더 지우기. `%ProgramData%\WebTether`(설정·로그)는 **묻고 기본은 남긴다**(사용자 결정 11-27 — '설정·로그(…)도 지울까요?' 기본 '아니요'). 지우기로 골라도 그 폴더의 주인·권한이 관리자 전용 정책에 맞을 때만 지우고(`SecureDir.DeleteIfTrusted`), 아니면 지우지 않고 `WebTether.untrusted-<때>` 로 옮긴다(검토 11-27 — 남이 미리 만든 폴더 안의 접합점으로 관리자의 지우기가 밖으로 가지 않게) |
| DLL 찾기(11-27 점검) | `[assembly: DefaultDllImportSearchPaths(System32)]` — 이 어셈블리의 P/Invoke 는 System32 에서만, `Main` 첫머리에 `SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32)`(`DllSafety.Harden` — 그 뒤의 이름만 적은 LoadLibrary 가 exe 의 폴더·현재 폴더·PATH 를 보지 않는다. 내려받기에서 관리자로 띄우면 그 폴더는 같은 사용자가 쓴다). 시험 exe 도 같게 걸고 모든 시험(창 그리기·WMI·HTTP)이 돈다. **막지 못하는 것**: 그 전에 올라오는 정적 가져오기 — .NET exe 의 `mscoree.dll` 은 KnownDLLs 에 없어(이 PC 에서 읽음) 내려받기 폴더에 같은 이름을 두면 `Main` 전에 올라간다. csc 로는 `/DEPENDENTLOADFLAG` 를 줄 수 없다 — 내려받기의 exe 가 관리자로 도는 것은 처음 설치(트레이가 UAC 로 띄우는 `--install`) 한 번뿐이고, 그 뒤 관리자로 도는 것(서비스·다시 설치·제거)은 관리자 전용 폴더의 설치본이다(설치본의 트레이가 자기를 `runas` 로 띄운다 — 단 내려받기의 exe 로 다시 띄운 트레이의 '설치' 는 그 exe 를 관리자로 띄운다. 설치를 마치면 내려받기의 트레이는 끝나고 설치본의 트레이가 뜬다). 내려받기의 exe 로 띄운 트레이는 보통 권한이라 같은 폴더의 DLL 이 올라가도 권한이 오르지 않는다 |
| 그 밖 | 하나만 실행 — 서비스는 SCM 이 하나만 띄운다, 트레이는 세션마다 `Local\WebTether-tray`(위 '트레이'). 트레이와 시험 판(`Local\…-test`)은 `InstanceLock`(뮤텍스·이벤트 — 둘째는 첫째의 상태 창을 부른다, 이미 있는데 주인이 정책 밖이거나 다른 종류 객체면 알리고 뜬다). 11-27 까지의 관리자 도우미가 쓰던 `Global\` 표지와 상태 폴더의 잠금 파일(`InstanceFile`)은 걷었다(git 이력 `d95c1fcb` — 둘이 함께 저절로 연결하던 틈을 막던 것인데 이제 연결은 서비스 하나다), 로그(고리 3000 줄 + `%ProgramData%\WebTether\logs\webtether.log` 1 MB 돌려쓰기), `Process` 객체는 다 쓰면 닫는다(자식·explorer·자기 자신), 아이콘은 코드로(모양도 다르다 — 빈 고리·반 고리·체크·느낌표), 정보 창에 두 라이선스 글(`pc-windows/licenses/`). **속도·트래픽 통계와 데이터 한도 알림은 걷었다**(사용자 결정 11-21 — 상태 창의 '지금 속도'·'오늘'·'이번 달', `Usage.cs`, 설정의 한도 칸). 남은 `usage` 파일은 읽지도 지우지도 않고, 설정 파일의 `limit_on`·`limit_day_gb`·`limit_month_gb` 는 문제로 적지 않고 버린다(`Settings.RemovedKeys` — 다음 저장에서 사라진다) |

**업데이트 알림 — 알리기만 한다**(사용자 승인). 연결된 동안 설정한 간격(매일·**매주**(기본)·매월)마다 한 번, GitHub API 의 tun2proxy 최신 릴리스와 보안
권고(GHSA 전역 `advisories?ecosystem=rust&affects=tun2proxy` + 저장소의 `security-advisories` — RustSec 은 GHSA 가 들여와 따로 보지 않는다, wintun 은 zip 을
따라간다)를 묻는다. 이 판에 걸리는 권고가 있으면 경고 풍선과 트레이 메뉴 맨 위 빨간 줄, 새 판이면 풍선과 '새 판 x.y.z — 변경 내역 보기'·'이 판은 알리지
않기'. 실패는 로그에만 남기고 다음 간격에. 묻는 것은 **트레이**(보통 권한 — 서비스는 바깥에 묻지 않는다, `Controller.UpdatesEnabled = false`), 상태는 `%LOCALAPPDATA%\WebTether\update`(예전 `%ProgramData%\WebTether\update` 는 읽지 않는다). 남의 서버가 준 글이라 답 하나 1 MB, **확인 한 번(요청 셋을 합쳐) 전체 15초**(`TotalTimeoutMs` — 마감이 지나면 요청을 끊고(Abort) 남은 요청을 하지 않는다. 읽기 한 번마다의 시한이면 한 바이트씩 흘리는 서버가 끝없이 붙잡는다 — 시험), TLS 1.2 이상, 넘겨주기 따르지 않음,
JSON 은 쓰는 칸만, 화면 글은 제어·방향 제어 문자를 걸러 자르고, 여는 주소는 `https://github.com/` 만(관리자 권한을 물려주지 않게 `explorer.exe` 에 넘긴다).
**왜 알림만인가** — exe 의 안전은 '빌드 때 고정해 확인한 SHA-256 의 바이트만 관리자로 돈다' 에 걸려 있다. 도우미가 스스로 받아 바꾸면 그 확인이 'GitHub 이
그때 준 것' 으로 바뀌어 릴리스나 계정이 털렸을 때 곧바로 모든 PC 의 관리자 권한이 된다(공급망). 새 판은 사람이 `build.py --update`(받은 zip 이 API digest 와
같아야 고친다, 태그 이름이 판 꼴 `v1.2.3[-pre]` 가 아니면 멈춘다 — build.py 소스에 그대로 쓰이므로, wintun 서명자는 주체의 `O=` 가 정확히 WireGuard LLC 인지 본다)로 짓고 시험을 다시 돌린다.

**시험**(`WebTether-tests.exe` — 실제 망·경로·Wi-Fi·서비스(SCM)·Run 키·작업 스케줄러·ProgramData·Program Files·전역 이름에 손대지 않는다(서비스와 예약 작업은 읽기만 한 번). 화면에 창을 띄우는 시험(이름이 '묻는 창' 으로 시작 — 하나)은 `--with-dialogs` 를 줄 때만 돈다(SKIP 으로 적힌다). 스크래치 폴더는 역슬래시 경로로 준다 — 슬래시로 주면 경로 글을 견주는 시험 둘이 틀린다):
**11-27 서비스로 옮기며** — 파이프 글(짓고 읽기·틀린 꼴 거절)·명령(고정된 일곱 — 걷은 `reload` 는 거절, `set_` 는 `propose` 의 아는 키만, 그 밖의 인자는 log 의 after 하나)·손님 확인(망·원격 세션·낮은 무결성·비대화형 거절, SYSTEM 받음)·파이프 ACL, 서비스 몸통을 dry-run 으로 실제 파이프에 띄워 hello·status·connect·disconnect·log, 콘솔 세션이 아닌 손님 거절(세션 판정을 바꿔 넣어)과 **이름을 남이 먼저 쥔 파이프**(서비스는 듣지 못하고 연결 일은 한다), 접두어 밖 Wi-Fi 의 '연결' — 콘솔에 사용자가 없으면 묻지도 연결하지도 않고, 있으면 그 세션에 묻고(가짜 `IUserPrompt` — WTSSendMessage 를 부르지 않는다) '아니요' 면 꺼짐·'예' 면 연결, 서비스 상태 글(범위·주소·방향 제어 문자 거르기), 트레이의 `ServiceClient`(닿지 못함·알림 번호), 서비스 계획(따옴표 경로·`--service`·거절할 경로·DACL 의 사용자 권한), 옛 예약 작업 가르기(우리 것 두 꼴만 — 남의 것·Exec 둘·DTD 는 아님), 설치기·제거기(가짜 SCM·Run 키·schtasks — 차례, 해시를 견준 복사, 옛 작업은 우리 것만, 상태 폴더는 고른 때만), 트레이의 첫 물음, 이 PC 의 서비스·예약 작업을 읽기만. **검토 11-27 의 고침** — 트레이의 서버 확인(세션 0 + SCM 의 서비스 PID, 기본 확인이 SCM 을 본다), 이름을 남이 먼저 잡은 파이프를 놓이면 다시 잡기(선점한 서버가 답하다가 놓이면 서비스), 관리자 판 띄우기 직전의 환경 지우기(뜻 없는 이름의 변수만 넣고 가짜 runas 가 본 환경에 없음 — 실제 runas 는 하지 않는다), 제거기의 상태 폴더(권한이 넓은 폴더는 지우지 않고 옮김), 설정 확인 창의 글(방향 제어·줄바꿈·긴 값·전체 상한·켬/끔), 설정 제안(검사기에 걸린 것·빠진 키·보이지 않는 글자·줄바꿈·긴 값·모르는 키는 묻지 않음, 바뀐 것이 없으면 묻지 않음, 세션이 다르거나 콘솔에 사용자가 없으면 거절, '예' 면 보인 값 그대로 저장·적용 — 트레이의 `ServiceSettingsHost` 로 끝까지, 물음은 하나·거절 뒤 쉬는 동안 '잠시 뒤', 쉬고 나면 다시) — 모두 가짜 WTSSendMessage. 고친 것(서버 PID·다시 잡기·환경 지우기·제거기의 정책 검사·제안 검사기·보이지 않는 글자·세션·하나만·쉬는 시간·글 거르기)을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다. **5차 판정(11-27)의 고침** — 폰 주소만·넓은 우회 거절·옛 파일의 그런 값은 버림, U+0020 밖의 빈칸·겹친·앞뒤 빈칸 거절, 확인 창의 글(자르지 않고 줄 바꿈·우회 목록의 꼬리까지·NBSP·겹친 빈칸 드러내기·긴 값 거절·요청한 프로그램), 설치본이 아닌 프로그램의 제안 거절과 확인 창의 경로·PID, 끝난 뒤의 쉼·되풀이된 거절의 긴 쉼·최근 결과, 트레이의 결과 가리기(`Judge` — 최근 목록·더 큰 번호), 올리기 전 HKCU 읽기(가짜 레지스트리 — 실제 HKCU 에 쓰지 않는다, 이 PC 는 읽기만 한 번). 고친 것(폰 주소·넓은 우회·드러내기·긴 값·이상한 빈칸·겹친 빈칸·설치본만·끝난 뒤 쉼·긴 쉼·최근 결과·더 큰 번호·HKCU 확인·요청한 프로그램)을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다. '값이 다른데 보이는 글이 같음' 거절은 드러내기가 있는 한 닿지 않는 겹이라 따로 되돌려 보지 않았다. **6차 판정(11-27)의 고침** — 파일 읽기와 제안의 같은 값 규칙(걸린 키는 파일에서 기본값·우회 목록은 틀리거나 넓은 칸만 빼기·우회 목록 1000자와 나머지 200자·`→ ⟨ ⟩ ‘ ’`·한 낱말의 섞인 글자·SSID 앞머리 ASCII), 확인 창의 ‘ ’ 와 600자 넘는 우회 목록이 다 보임, Precheck 의 좁힌 목록(`DOTNET_`·`CORECLR_` 는 통과)과 `Volatile Environment`·세션 하위 키(가짜 레지스트리), 파일 ID 대조(접합점·대소문자는 같은 파일, 사본·없는 파일은 다름, 접합점 경로로 적은 설치본의 제안을 받음), 무작위로 시작하는 제안 번호와 '다른 번호' 판정, 10분 창이 지난 거절은 첫 거절로. 고친 것(파일의 값 규칙·우회 칸 빼기·1000자·기호·섞인 글자·SSID ASCII·‘ ’·좁힌 목록·Volatile·세션 하위 키·파일 ID·무작위 번호·다른 번호·거절 창)을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다. **7차 판정(11-28)의 고침** — 우회 목록의 저장 꼴(빈칸·쉼표·쌍반점이 섞인 1000자 가까운 목록을 제안 → 확인 창 → 저장 → 서비스를 다시 띄워 읽어도 그대로, 날 글은 짧아도 저장 꼴이 1000자를 넘으면 거절), 기본값으로 돌린 키(Parse 가 모으고 — 뒤 줄이 바로 적은 키는 빼고 — 서비스가 `defaulted` 로 싣고 설정 창 바탕이 한 줄로, 제안이 적용되면 지움). 고친 것(Apply 의 정규화·저장 꼴의 길이·키 모으기·`defaulted` 싣기·적용 뒤 지우기)을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다(되돌려 보기는 경우마다 900초 시한 — 넘으면 그 시험 exe 만 끝내고 다음으로). 설정 창이 그 줄을 그리는 것은 화면 시험이 없어 보지 않았다. **11-28 사용자 설치에서 난 것의 고침** — 설치 폴더 정책(Users 읽기·실행 줄은 정책 안·쓰기 계열 비트 열 가지는 정책 밖·읽기·실행이 없거나 상속만 하거나 거부 줄이면 맞지 않음, SDDL 로 지은 보안 설명자), 새 폴더의 ACL, 옛 판 폴더에 그 줄만 제자리에서 더하기, 설치본 파일의 주인·Users 읽기·실행·마지막 확인(옛 권한의 같은 바이트는 다시 쓰고, Users 쓰기를 더하면 확인이 잡음), 자동 시작에 지연 없음(가짜 SCM 의 `ServiceSpec`), 트레이 다시 띄우기의 실패 글, 닿지 못할 때의 SCM 상태 글(가짜 SCM 상태 — Poll 까지), 이전 판 도우미 가르기(자기·서비스·같은 파일은 빼고 못 읽은 것은 막음 — 가짜 목록·가짜 서비스 PID, 실제 프로세스·SCM 은 보지 않는다)와 그때 설치기가 아무것도 바꾸지 않음, 다른 tun2proxy 의 PID·안내가 상태와 로그에, 서비스의 시작 줄, 제거기의 '설정·로그도 지울까요' 기본 '아니요'(소스로). 고친 것을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다(경우마다 900초 시한). 설치본을 새로 쓸 때 주인을 정하는 줄은 시험에서 주인이 늘 이 사용자라 되돌려도 깨지지 않는다(관리자 판에서만 뜻이 있다), 마지막 확인은 물려받은 권한이 늘 맞아 닿지 않는 겹이라 되돌려 보지 않았다. **9차 판정(11-28)** — 저절로 다시 연결(꺼짐·오류에서 접두어에 맞는 Wi-Fi 가 나타나면·망 바뀜 알림이면 다시 시작, 처음 본 것은 바탕만, 접두어 밖은 아님, 사용자가 끊었으면 망·로그온에도 하지 않고 '연결' 이 풂, '다시 연결' 끔이면 하지 않음, 같은 까닭은 한 줄 — 가짜 핫스팟·가짜 망 알림), 서비스의 세션 변경(콘솔 세션의 로그온만 — 다른 세션·로그온이 아닌 변경은 아님), 이전 판 도우미의 글(모두 다른 사용자 세션이면 '그 세션에서 끝내야 합니다'). 고친 것을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다(바탕 없이 늘 다시 시작하게 되돌리면 옛 시험 'TUN 어댑터가 끝내 나타나지 않으면' 도 깨진다 — 오류 뒤 곧바로 되풀이한다). 서비스가 `NetworkChange` 알림을 받는 배선은 실제 망을 바꾸지 않으므로 시험하지 않았다(그 알림이 부르는 `Controller.NetworkChanged` 는 시험한다). **10차 판정(11-28)** — 뜨자마자 죽는 가짜 자식에 시도마다 망 바뀜 알림을 넣어도 띄운 수가 다시 연결 상한(1 + 1)에서 멎고 Wi-Fi 가 사라졌다 나타나면 다시 띄우며 '연결이 끊겼습니다'·'다시 연결을 멈췄습니다' 는 한 번씩, 되풀이하지 않는 오류(다른 tun2proxy) + 망 바뀜이면 다시 시작하지 않음, 띄우기 전의 실패로 멈췄으면 망 바뀜으로 다시 시작하되 시도 중·멈춘 직후의 알림과 시작 전에 센 알림은 세지 않음, 예산(한 시간에 세 번·간격 두 배·미룬 것은 예산이 열리면 잇기·로그온과 '연결' 이 새로 — 서비스의 콘솔 로그온도). 고친 것(시도 중 알림 버리기·시작할 때 깃발 지우기·멈춘 직후 조용함·망 바뀜만의 조건·멈춘 까닭 가르기·시간당 상한·두 배 간격·미룬 것 잇기·로그온 새로(Controller·Service 둘)·'연결' 새로·두 알림 묶기)을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다(경우마다 900초 시한). 1분 넘게 붙어 있으면 예산·알림을 새로 하는 줄은 시험이 60초를 기다려야 해 되돌려 보지 않았다. **경로 고리·앞서 내리기(11-28)** — 0.8.7 의 실제 줄 꼴(`[… INFO  tun2proxy] Beginning #33 TCP 10.0.0.33:52298 -> 192.168.49.1:1080`)과 UDP·다른 포트·IPv6, 고리가 아닌 줄(DNS 의 바뀐 목적지·다른 주소·끝 줄·깨진 꼴), 로그 수준을 info 로 올리기, Wi-Fi 알림 코드, 출력을 흘려 넣는 가짜 자식으로 곧바로 강제로 끝내기·띄운 뒤의 실패로 횟수를 써 상한에서 멎음·자식마다 한 줄·알림 한 번·끊는 중(Ctrl+C 를 기다리는 가짜 자식)의 끝내기 앞당김, 연결 중의 주소 바뀜·Wi-Fi 알림으로 Tick 없이 내리기(TUN 자신의 알림·다른 어댑터는 그대로, 알림 뒤에 주소가 빠지는 것을 빠른 점검 창이 잡음), 서비스가 Wi-Fi 알림을 걸고(가짜 — Native Wifi 에 등록하지 않는다) 받아 넘기고 멈출 때 걷음. 고친 것(줄 찾기·자식마다 한 번·강제로 끝내기·끊는 중 앞당김·횟수를 0 으로 돌리지 않음·UDP·'Beginning' 머리·로그 수준·연결 중 주소 알림·어댑터 거르기·빠른 점검 창·서비스의 배선·걷기)를 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다(경우마다 900초 시한). `HotspotLost` 첫머리의 '이미 끊는 중이면 그만' 은 다른 길이 모두 같은 깃발을 먼저 보므로 닿지 않는 겹이라 되돌려 보지 않았다. 실제 Wi-Fi 알림의 등록·코드·도착 시각은 기기에서 보지 않았다. **12차 판정(11-28)** — 고리 줄 읽기를 0.8.7 의 꼴로 좁힘(머리가 없거나 줄 가운데에 있거나 여는 대괄호가 아니거나 수준·대상·때의 꼴이 다르면 아님, 목적지 뒤에 무엇이 붙으면 아님, `IPAddress.TryParse` 가 받는 하나의 수·16진·앞의 0·칸 셋·끝의 점·256·포트의 앞 0·포트 0·65536·포트 없음·대괄호 없는 IPv6·범위가 붙은 IPv6 은 아님, 출발 주소도 엄격), 설정 창의 로그 수준 알림 줄(off·error·warn 이면 보이고 info 이상이면 없음 — 창을 띄우지 않고 `LoadFrom` 으로). 고친 것(여는 대괄호·때의 꼴·목적지 뒤·점 넷 십진·앞의 0·포트·IPv6 글자·알림 줄)을 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다(경우마다 900초 시한). 기본값을 돌린 알림이 로그 수준 알림보다 먼저인 것은 시험이 그 알림을 만들 길이 없어 되돌려 보지 않았다. 걷은 '시작 시 실행' 의 시험(작업 XML·시작 스크립트를 conhost 로 돌리기·옛 사본·가짜 schtasks 의 시간 초과)과 잠금 파일 시험도 함께 걷었다(git 이력 `d95c1fcb`). **11-27 점검의 것 가운데 남은 것** — 권한이 틀린 설치 폴더는 통째로 비키고(안의 심어 둔 것은 비킨 폴더에 그대로) 비키지 못하면 실패, 되살리기의 GUID, 이름이 아니라 우리 자식으로 정하는 걷기(이름만 같은 다른 것은 TUN 이 있을 때만 막음·끝나지 않은 우리 자식), 192.168.49.x 어댑터가 여럿일 때의 길 견주기(손으로 눌러도 묻지 않음), DLL 찾기 좁히기 — 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다.
 설정 읽기·쓰기·검사(걷은 한도 키는 문제로 적지 않음), 명령줄 싸기(공백·따옴표·`&`·끝의
역슬래시·한글), 내장 실행 파일(풀기·바뀐 것·끼워 넣은 DLL·깨진 내장본·위 폴더의 접합점 바꾸기·실제 0.8.7 을 쥔 채 `--version`), Ctrl+C(듣는 자식은 정리하고 0 으로, 듣지 않는 자식은
강제로), 작업 개체(띄운 자식이 들었는가·도우미 대역 `--job-parent` 를 강제로 끝내면 손자가 끝나는가), 가짜 SOCKS5·HTTP(루프백), 걷기 계획(합성 경로 표 — 지우기는 어댑터로 좁힌다)·이 PC 의 경로 표 읽기, 상태 기계(dry-run — 연결·핫스팟 사라짐·자식 끝남·횟수
상한(연결됨 직후 죽기를 되풀이해도)·연결 도중 끊기(트레이·상태 창)·걷지 못하면 상태 파일 남기기·다른 tun2proxy·Wi-Fi 프로필·지난번 흔적·TUN 시간 제한·핫스팟 사라짐과 끊기가 겹침·걷기는 일꾼 스레드에서·이미 있던 우회 경로·bin 접합점·끊은 뒤 걷는 동안과 시작 걷기 동안의 '정리 중'과 그 사이의 연결), 자동 연결(표시가 없으면 연결·'끊기' 만 표시를 남기고 '연결' 이 지움·끝내기·핫스팟 사라짐·다른 tun2proxy·포기는 끊음이 아님·시작 직후의 너그러운 기다림과 그 상한), 업데이트(판 견주기·범위 표기·글 거르기·답 읽기·간격·건너뛴 판·한 번만 묻기·실패·한 바이트씩 흘리는 루프백 서버의 전체 마감·요청 셋의 전체 상한),
'끊기' 는 메모리에만(그 실행 동안 Tick 도 다시 연결하지 않음·다음 실행은 연결·예전 판의 `user-disconnected` 를 공유 없이 잠가 두어도 연결하고 내용·시각 그대로), Wi-Fi 이름(합성 `WLAN_CONNECTION_ATTRIBUTES` 읽기·이름 얻는 차례(거절일 때만 NLM)·저절로는 접두어 밖이면 띄우지도 묻지도 않음·이름 모름·기다리는 동안 남의 망이면 횟수를 쓰지 않고 까닭을 고침·손으로는 묻고 '아니요'·'예' 와 그 이름의 다시 연결·다음 실행은 잊음·묻는 사이 끊기·묻는 창이 떠 있을 때의 '연결' 은 그 창을 다시 앞으로·자동 시도 중 누른 '연결' 은 묻게(끊기 → 연결, 하나만 띄움, 그 시도까지만 — 다음 저절로 하는 시도가 그 안의 새로 보기에서 남의 망을 만나도 묻지 않음: 1초 점검이 남의 망을 보면 시도를 띄우지 않아 그것만으로는 새는 것을 못 잡았다, 검토 r5 가 더하고 되돌려 깨지는 것을 봤다)·이름을 모르고 허락한 망이 남의 이름이면 끊고 우리 이름이면 기억·묻는 창이 `MB_TOPMOST` 로 맨 위로 올린 보통 창보다 위에 뜨고 다시 부를 수 있음(시험 판이 화면 밖으로 곧바로 비키고 닫는다 — 초점은 빼앗지 않게 `MB_SETFOREGROUND` 는 뺀다)·연결 중 이름 바뀜·접두어 설정과 대소문자·화면 글의 방향 제어 문자·이 PC 에서 읽기만 한 번(이름은 적지 않는다 — 두 번째는 10초 기억)), 막는 호출(1초 점검의 핫스팟·TUN 살피기가 다른 스레드·겹치지 않음·살피는 사이 끊으면 늦은 결과를 버림·살피기가 던지면 아무것도 안 함·시도의 목록·핫스팟도 다른 스레드·상태 창의 핫스팟과 창이 없어진 뒤·설정 저장이 다른 스레드에서 '저장 중' 이고 칸·단추를 막음·창이 없어져도 설정을 건다), 상태 폴더(**스크래치를 뿌리로 '이 사용자 전용' 정책** — 새로 만들기의 ACL·쥔 동안 이름 바꾸기 막힘·남이 쓸 수 있는 폴더·접합점을 비키기·그 아래 접합점으로 쓰지 않기, 관리자 전용 정책은 거절하는 길만), 설치본(이 시험 exe 를 쥐면 이름 바꾸기가 막히고 놓으면 바뀐다·쥔 핸들에서 복사·권한이 틀린 폴더는 비키기), 하나만 실행(먼저 잡은 보통 권한의 표지·다른 종류 객체를 알아챔), 띄우자마자 끝난 자식의 알림을 늦게 구독해도 받기, 끝남 알림 전의 '연결', 설정 파일을 못 쓰면 '저장하지 못했습니다' 이고 설정은 옛 값(접합점 너머에 쓰지 않음). 걷은 '시작 시 실행'·옛 사본 쪽의 시험과 그 되돌려 보기 기록(r6)은 git 이력 `d95c1fcb` 에 있다. 고친 것은 하나씩 되돌려 겨눈 시험만 깨지는 것을 봤다(11-21 자동 연결·'정리 중'·걷은 한도 키와 3차 검토의 것도). 11-21 넷째 결정(끊기는 메모리에만·Wi-Fi 이름·작업의 시간 초과·일꾼 스레드)의 고침도 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다. 그 뒤 독립 검토의 넷(유선 어댑터의 거절을 Wi-Fi 로 보지 않기·연결을 정할 때 이름 기억 버리기·묻는 사이 망이 바뀌면 잇지 않기·WMI 시한)도 하나씩 되돌려 그 시험만 깨지는 것을 봤다. 그 뒤 고침 셋(묻는 창 맨 앞·다시 앞으로, 자동 시도 중의 '연결', 이름을 모르고 허락한 망)도 하나씩 되돌려 겨눈 시험이 깨지는 것을 봤다. `--update` 는 고정 판을
0.8.6 으로 바꾼 사본에서 끝까지 돌려 0.8.7 로 되돌아오는 것을 봤다.

**확인하지 못한 것**(이 PC 의 인터넷이 그 길이라 실제로 띄우지 않았다): **SYSTEM 으로 도는 서비스의 Wi-Fi API**(위치 동의는 사용자 문맥의 프로세스에만 묻는다고 문서에 있고 서비스 예외는 적혀 있지 않다 — 거절되면 네트워크 목록 이름으로 물러선다, 그 WMI 질의가 SYSTEM 에서 같은 이름을 주는지도), 실제 남의 DIRECT 망, 서비스로 실제 tun2proxy 를 띄우고 끊기(세션 0 의 서비스가 숨은 콘솔에 Ctrl+C 를 보내 `--setup` 을 걷는지),
강제 종료 뒤의 걷기, 폴더 잠그기(관리자 판만), `netsh wlan connect` 를 SYSTEM 이 부를 때(‘이 사용자만’ 프로필이면 실패하는지), 부팅 직후·로그온 전의 실제 자동 연결(Wi-Fi 가 180초 안에 붙는지), 연결된 동안의 업데이트
확인(트레이 쪽 — 망 길은 `--live-update` 로 한 번 봤다), 종료 때(서비스의 `OnShutdown` 이 끊고 걷는 데 시간이 넉넉한지), 높은 DPI.
**서비스로 옮기며 확인하지 못한 것**(11-27 — 실제 서비스 만들기·지우기·시작·멈춤, schtasks `/Create`·`/Delete`, `runas`, Run 키 쓰기, WTSSendMessage 는 하지 않았다 — 모두 인터페이스 뒤의 가짜로 시험했다): 설치·제거의 실제 차례(`CreateServiceW`·`ChangeServiceConfig2W` 의 구조체 배치 — 64비트로 적었다, `SetServiceObjectSecurity` 의 SDDL, 쓰는 중인 설치본의 미룬 지우기), 실제 서비스에서의 파이프(ACL·`PIPE_REJECT_REMOTE_CLIENTS`·흉내 낸 토큰 읽기 — 시험은 보통 권한의 dry-run 서버로 봤다), **WTSSendMessage 의 창에 같은 세션의 보통 권한 프로그램이 입력을 넣지 못하는지**(UIPI — 문서로 추론. 설정 확인 창은 이것에 기댄다 — 그 프로그램이 '예' 를 누를 수 있으면 콘솔 사용자 몰래 설정을 바꾼다), 그 창이 맨 앞에 뜨는지·시한 60초·긴 글(1500자)이 다 보이는지, 실제 서비스의 PID 를 보통 사용자가 `QueryServiceStatusEx` 로 읽는지(서비스 DACL 의 `LC` — 시험은 SCM 대신 가짜 PID), SYSTEM 서비스가 손님의 이미지 경로를 `QueryFullProcessImageName` 으로 열 수 있는 꼴로 얻는지(경로 글의 꼴은 이제 상관없다 — 두 파일을 열어 파일 ID 로 견준다. 열지 못하면 설치본의 트레이도 '설치된 트레이에서만…' 으로 거절된다 — 시험은 이 시험 exe 와 접합점 경로로 봤다), runas 로 띄운 관리자 판이 정말 트레이의 환경 블록을 물려받는지(지우기가 듣는 전제 — 문서로 확인하지 못했다), 로그오프 뒤 서비스가 연결을 쥐고 있는지, HKLM Run 으로 모든 사용자에게 트레이가 뜨는지(11-28 — 설치 폴더에 Users 읽기·실행을 준 뒤로는 떠야 한다. 관리자 판으로 실제 ACL 을 지어 보지는 않았다 — 설치본 파일의 주인을 Administrators 로 정하는 `File.SetAccessControl` 이 관리자 판에서 되는지도), 이전 판 도우미 가르기를 실제 프로세스로(보통 권한의 트레이가 관리자 프로세스의 이름·세션을 읽는지 — 경로는 못 읽어도 '떠 있음' 으로 친다), 옛 판의 TUN 어댑터가 남은 채 새 서비스가 연결되는지, 내려받기의 exe 로 설치한 뒤 설치본의 트레이로 다시 뜨는 흐름, 옛 예약 작업을 실제로 지우기(이 PC 의 작업은 읽기만 해서 '우리 것' 으로 읽혔다).
**관리자 판에서만 되는 것**(2026-11-21 — 관리자 권한으로 아무것도 돌리지 않았다): `%ProgramData%\WebTether` 를 주인 Administrators 로 만들기와 그 ACL,
Program Files 설치본 만들기(권한이 틀린 폴더 비키기 포함)와 해시 대조, 실제 tun2proxy 를
작업 개체에 넣기(서비스의 자식), 서비스가 죽은 뒤 wintun 어댑터가 함께 사라지는지(SwDevice — 추론).

**사용자가 설치 뒤 할 일과 볼 것**(11-27 서비스 판):
1. 떠 있는 옛 도우미(관리자로 뜬 것)를 **트레이의 '끝내기' 로 끝낸다**(자동 실행만 끄면 지금 떠 있는 것은 그대로다 — 11-28 사용자 기기에서 그 연결을 새 판의 것으로 봤다). 새 판은 옛 도우미가 떠 있으면 묻지도 설치하지도 않고 '이전 판 도우미가 실행 중입니다 — …' 를 보인다.
2. 새 `WebTether.exe` 를 (내려받기에서) 실행 → '서비스가 설치되어 있지 않습니다. 설치할까요?' 에 '예' → UAC 승인. 그 트레이가 끝나고 설치본의 트레이가 뜬다(**알림 영역에 아이콘이 다시 보여야 한다** — 못 띄우면 창으로 알린다).
3. 볼 것: `icacls "C:\Program Files\WebTether"` 와 `icacls "C:\Program Files\WebTether\WebTether.exe"`(Administrators·SYSTEM `(F)`, `BUILTIN\Users:(RX)` — 폴더는 `(OI)(CI)`, 그 밖은 없음, 파일 주인 `icacls … /q` 나 속성 창으로 Administrators), `sc qc WebTether`(BINARY_PATH_NAME 이 따옴표로 싼 `"C:\Program Files\WebTether\WebTether.exe" --service`, START_TYPE `AUTO_START`(DELAYED 가 붙지 않음),
   SERVICE_START_NAME `LocalSystem`)·`sc sdshow WebTether`(위 SDDL)·관리자 아닌 창에서 `sc stop WebTether` 가 '액세스 거부'·`schtasks /Query /TN WebTether` 가
   '없음'·`C:\Program Files\WebTether\launch.cmd` 가 없음·작업 관리자의 트레이 `WebTether.exe` 가 '관리자 권한 아님'·`icacls "%ProgramData%\WebTether"`
   (Administrators·SYSTEM 만, 상속 없음). 트레이로 연결·끊기, 다시 부팅해 **로그온 전에** 연결되는지(로그온 화면에서 폰의 손님 목록 등)와 로그온 뒤
   트레이 아이콘이 뜨는지(HKLM Run). 서비스가 떴는지는 트레이의 '로그' 첫 줄 '[시작] 서비스 …' 로 본다 — 트레이가 '서비스가 시작되는 중입니다'·'서비스가
   멈춰 있습니다' 를 보이면 SCM 이 그렇게 본 것이다(`sc query WebTether`). 서비스가 시작하지 못했으면 이벤트 뷰어 › Windows 로그 › 시스템의 Service Control Manager 줄.
4. Wi-Fi 프로필로 저절로 붙게 해 두었다면 그 프로필이 **'모든 사용자'** 것이어야 한다(SYSTEM 이 `netsh wlan connect` 를 부른다 — 안 되면 로그에 안내).
5. 접두어 밖 Wi-Fi 에 손으로 '연결' 하면 묻는 창이 서비스에서 뜬다(제목 'Web 테더') — 로그온한 사용자가 없으면 묻지 않고 연결하지 않는다.
6. 트레이의 '설정' 에서 값을 바꿔 저장하면 UAC 대신 서비스의 확인 창('Web 테더 — 설정 확인', 바뀐 칸마다 '이름: 옛값 → 새값')이 뜬다 — '예' 일 때만
   적용된다. 창의 '요청:' 줄이 `C:\Program Files\WebTether\WebTether.exe` 인지 본다. **스스로 바꾸지 않았는데 이 창이 뜨면 '아니요'**(같은 세션의 다른
   프로그램이 트레이를 빌려 보낸 것이다). 설치본이 아닌 트레이(내려받기의 exe)에서는 설정을 바꿀 수 없다.
7. 설치·제거가 '관리자 권한을 쓰지 않았습니다 — 사용자 설정에 .NET 을 바꾸는 값이 있습니다: …' 로 멈추면, 그 이름의 값을 `HKCU\Environment` 나
   `HKCU\Software\Microsoft\.NETFramework` 에서 확인하고(모르는 값이면 지운 뒤) 다시 한다.
8. 예전 설정의 프록시 주소가 192.168.49.x 가 아니었거나 우회 목록에 /8 보다 넓은 대역이 있었다면 그 칸은 기본값으로 돌아간다(서비스 로그의 '값을 버림').

| 함정 | 내용 |
|---|---|
| 스마트 앱 컨트롤 | 켜져 있으면 csc 로 지은 서명 없는 exe 도, 평판이 아직 없는 tun2proxy 0.8.7 도 막힌다("An Application Control policy has blocked this file" — CodeIntegrity 3077). 2026-11-21 사용자가 껐다 |
| Ctrl+C 무시 깃발은 물려 간다 | `SetConsoleCtrlHandler(NULL, TRUE)` 를 켠 채 띄운 자식은 Ctrl+C 를 받지 못했다(ctrlc2 는 그 깃발을 끄지 않는다). 삼키는 처리기만 두면 보내는 쪽이 `STATUS_CONTROL_C_EXIT` 로 죽었다. 깃발을 켜 두고 **자식을 띄우는 동안만** 끈다(`ConsoleCtrl.WithCtrlCInheritable` — 보내기와 같은 자물쇠) |
| 시험 exe 의 콘솔 | Ctrl+C 시험이 시험 exe 를 제 콘솔에서 뗀다 — 출력은 파일로 돌려 받는다(결과는 `<scratch>\results.txt` 에도) |
| 확인 하나뿐인 MessageBox 닫기 | 시험이 `WM_COMMAND IDOK` 만 보내자 창이 닫히지 않아 시험이 멈췄다(11-21 — 그 시험 프로세스를 끝냈다). `IDCANCEL`(2)도 함께 보낸다(`TopAsk.CloseForTest`, 마지막 길 `Win.AnswerAllDialogs` 도) |
| 보이지 않은 창의 DrawToBitmap | 자식 컨트롤을 그리지 않는다 — 화면 밖(-20000)에 `Opacity 0` 으로 잠깐 띄운다 |
| `python -I` | `PYTHONIOENCODING` 을 보지 않는다 — `build.py` 가 출력을 UTF-8 로 다시 건다(파이프가 cp949 면 '—' 에서 죽었다) |
| 옛 csc | C# 5 까지(`$""`·`?.`·`nameof` 없음), 소스가 BOM 없는 UTF-8 이라 `/codepage:65001` 이 없으면 한글이 깨진다 |
| 속성만 청한 핸들 | `FILE_READ_ATTRIBUTES`·`READ_CONTROL` 만 청한 핸들은 공유 모드 검사에 끼지 않는다 — `FILE_SHARE_DELETE` 를 빼도 그 폴더의 이름 바꾸기를 막지 못했다(시험). 막으려면 `FILE_LIST_DIRECTORY` 같은 데이터 권한을 함께 청한다 |
| `BY_HANDLE_FILE_INFORMATION` | FILETIME 은 DWORD 둘(4 바이트 정렬)이다 — `long` 으로 적으면 틈이 생겨 링크 수가 917504 로 읽혔다(시험) |
| 돌고 있는 exe 의 이름 | 실행 중인 exe 도 이름은 바꿀 수 있다(이미지 섹션은 지우기·쓰기만 막는다) — 경로로 자기를 복사하면 바꿔치기한 파일을 옮긴다 |
| schtasks 가 읽는 파일 | (걷은 '시작 시 실행' 이 밟은 것 — 다시 작업을 등록할 일이 생기면) schtasks `/XML` 은 파일을 공유 Read 로 연다(남의 쓰기를 막는다) — 쓰기 권한이 있는 핸들이 열려 있으면 '다른 프로세스가 파일을 사용 중' 으로 실패한다. 스크래치의 틀린 XML 로 재현했다(작업은 만들어지지 않는다) |
| 띄우자마자 끝난 자식 | `Process.Exited` 는 `Start` 가 돌아오기 전에도 온다 — 그 뒤에 구독하면 알림을 잃어 '연결 중' 에 멈췄다. `ChildProcess.Exited` 는 이미 끝났으면 구독하는 자리에서 부른다 |
| 뮤텍스를 열 때 | 이미 있는 이름이면 `new Mutex(…, MutexSecurity)` 의 보안 설명자는 무시되고 남의 것이 열린다 — 주인을 따로 본다(`InstanceLock`) |
| Wi-Fi 이름과 위치 권한 | Windows 11 24H2 부터 위치 권한이 없으면 `WlanQueryInterface`(current_connection)가 ERROR_ACCESS_DENIED, `netsh wlan show interfaces` 도 'need location permission' 으로 SSID 를 주지 않는다(이 PC — 위치가 꺼져 있다). 위치를 켜라고 하지 않고 네트워크 목록 이름으로 물러선다(`WifiName`). **그 거절은 어댑터를 보기 전에 온다** — 유선·TUN·없는 GUID 도 5 다. `interface_state`(6)는 위치와 상관없이 Wi-Fi 어댑터면 상태를, 아니면 1168 을 준다(이 PC) |
| 비동기 저장과 시험 스레드 | 창을 만들면 그 스레드에 WinForms 문맥이 깔린다 — 그 스레드에서 `SaveAsync` 를 `.Wait()` 하면 이어지는 일이 돌지 않아 멈춘다. 시험은 `Application.DoEvents` 로 돌리며 기다리고(`Pump`), 끝나면 문맥을 되돌린다 |
| CLR 이 읽는 시험 변수 | `COMPLUS_Version`·`COMPLUS_InstallRoot` 같은 값이 .NET exe 에 닿으면 셔림(mscoree)이 관리 코드 전에 '이 판의 .NET 을 설치할까요?' 창을 사용자 화면에 띄운다(11-27 — 시작 스크립트 시험을 되돌려 본 판에서). 환경을 넘기는 시험의 값은 뜻 없는 이름만 쓴다 |

### 사용자가 폰에서 할 일 — 옮겨 오지 않는 것과 차례

패키지·UID 가 달라 옛 usb-tether 의 자료는 넘어오지 않는다(기기 키는 앱 밖으로 나오지 않는다). 사용자는 합친 판을 폰에서 써 보았다. 새 판을 깔 때
볼 것(이미 지난 차례는 건너뛴다):

1. 새 Web 은 사용자가 깐다(Claude 는 실기기에 깔지 않는다).
2. 테더 화면의 **SSID 와 패스프레이즈를 옛 앱과 같게** 둔다(같아야 PC 가 그대로 다시 붙는다 — 패스프레이즈는 사용자가 손으로 넣는다. 비밀번호이므로
   Claude 가 다루지 않는다). 새 값을 쓰려면 PC 의 Wi-Fi 프로필도 고친다.
3. **옛 앱이 떠 있으면 먼저 멈춘다**(설정 › 앱 › Hotspot › 강제 중지 — 그 서비스는 최근 앱에서 밀어도 다시 뜬다). **이 순간 PC 의 인터넷이 끊긴다.** 옛
   앱이 떠 있으면 새 앱의 SOCKS5(1080)는 서지 않고 HTTP·파일은 8283·8081 로 옮겨 서며(이 PC 의 8282 는 옛 앱으로 간다), 같은 자격 증명의 그룹이면 새 앱이
   옛 앱의 그룹을 다시 쓰게 된다.
4. **테더링 켜기** → 권한(근처 기기 — 위치는 묻지 않는다) → '서버 켜짐 · SOCKS5 1080 · HTTP 8282 · 파일 8080' 과 '핫스팟 켜짐 · DIRECT-UT-… · 이 폰은 192.168.49.1(**이름**)' 인지 본다. HTTP·파일이 기본
   포트가 아니면 이 폰의 다른 앱이 그 포트를 쥔 것이다 — 그 앱을 멈추고 껐다 켜거나, PC·손님의 설정을 화면의 포트로 고친다. SOCKS5 에 줄표가 보이면 그
   앱을 멈추고 껐다 켠다. PC 가 안 되면 '거절한 연결' 줄의 까닭을 받는다(PeerFilter 를 고친다).
5. **공유 폴더를 다시 고른다**(SAF 권한은 앱마다다). **퀵 설정 타일 '테더링' 을 다시 더한다**(옛 'Hotspot' 타일은 옛 앱과 함께 사라진다. 삼성 One UI 의
   길게 누르기가 테더 화면을 여는지 폰에서 본다).
6. **배터리 최적화에서 Web 을 뺀다**(화면의 '배터리 최적화 설정 열기') — 화면이 꺼진 채 오래 쓰고, 뒤에서 서비스를 다시 세우는 길이 막히지 않게. 옛 앱에
   준 앱별 설정(데이터·배터리)도 Web 에 다시 준다 — 이제 브라우저와 테더가 그 설정을 함께 쓴다(UID 하나).
7. 사용자가 확인하면 폰의 옛 usb-tether 앱을 지운다(저장소의 것은 이미 저장소 밖 백업 폴더로 옮겼다). PC 의 시작 스크립트는 'PC 연결 파일 받기' 로 새
   판을 받는다.

### 시험

`:tether:test`(모두 루프백 — 바깥 망에 아무것도 나가지 않는다): `Socks5ServerTest`(인사·CONNECT 의 세 주소 꼴·이름은 한 번 풀고 그 주소로·
막힌 목적지·풀지 못한 이름은 4·요청 뒤에 붙여 보낸 바이트·전체 마감·반쯤 닫은 뒤의 늦은 답·UDP ASSOCIATE 의 감싸기·출발 포트 고정·UDP 묶음 상한·
포트를 옮기지 않음·스레드 예산·멈추면 끊김) · `HttpProxyServerTest`(CONNECT·머리와 함께 온 바이트·origin-form 과 hop-by-hop·스머글링 400(값이 다른 CL·TE 여럿·끝이 chunked 가 아닌 TE 포함)·같은 값의 CL 은 하나로·431·`:` 없는
줄도 셈·전체 마감·403 본문·풀지 못한 이름 502·차 있으면 다음 빈 포트, 모두 차면 실패·반쯤 닫은 뒤의 늦은 답) · `FileServerTest`(Host 421·목록·한글·
경로 벗어나기 404·Range·덮어쓰기가 끊겨도 원래 파일 그대로·TE 만이어도 501·머리 줄·전체 마감·옮겨 선 포트의 `Host`) · `FileServerManageTest`(관리
스위치가 꺼지면 관리 길 아홉이 모두 403 이고 요청마다 본다·단순 POST(text/plain·폼·multipart·Content-Type 없음)는 415·다른 출처의 Origin·
Sec-Fetch-Site 는 403·GET·OPTIONS·DELETE 로는 관리 길이 없다·폴더 만들기와 이름 바꾸기(겹침·틀린 이름·맨 위·휴지통 이름)·옮기기와 복사·**폴더를
자기 안으로**는 거절하고 아무것도 바꾸지 않음·같은 이름은 묻고(409)·건너뛰고·새 이름으로·제공자가 옮기기를 못 하면 복사 뒤 지우기·손으로 짓는 복사의
상한과 반쯤 지은 사본 지우기·지우기는 휴지통으로 — 되살리면 사라진 폴더도 다시 만들고 겹치면 새 이름·비우기와 하나씩·**휴지통은 보통의 길로 닿지
않는다**·폴더째 올리기는 관리일 때만·**덮어쓰기는 `overwrite=1` 로 밝힌 요청만**(없거나 0 이면 409, 폴더와 같은 이름도)·**받는 동안 같은 이름이 생기면
덮지 않고 409**·JSON 몸의 꼴과 상한·**몸을 흘리는 손님이 다른 관리 동작을 막지 않음**·끝에 점·공백을 붙인
휴지통 이름·하위 폴더의 `.web-trash` 를 맨 위로 옮기거나 복사해도 휴지통이 되지 않음(11-21 2차 검토)·손으로 옮겨 휴지통에 넣다가 원래 것을 다 지우지 못하면 사본을 남김 — 뒤의 셋은 11-21 검토의 고침, 하나씩 되돌려 그 시험만 깨지는 것을 보았다·
**휴지통의 `.nomedia`**(만들 때·옛 휴지통에도, 목록·되살리기·영구 삭제·비우기에 나오지 않고 남는다)·**보관 기간**(지난 것만 — 딱 기간은 아직, 관리 허용과
상관없음·기록이 없거나 깨졌거나 지운 때가 없거나 칸 이름과 기록 가운데 하나라도 기간 안이면 지우지 않음·'비우지 않음'·칸을 지우지 못하면 기록을 남겨
다음에·서버가 서면 손보고 설정을 바꾸면 곧바로)·**11-21 그 뒤 결정**(폴더와 같은 이름은 `overwrite=1` 이어도 409 `folder`·덮어쓴 원래 파일은
관리가 꺼져 있어도 휴지통으로 — `.nomedia`·되살리기·대소문자만 다른 원래 것·휴지통으로 옮기지 못하면 덮지 않고 임시도 남기지 않음·덮어쓰기는 관리
잠금을 쥔다·새 이름의 제어·방향·폭 없는 글자는 빼고 쓰고 답에 실제 이름(올리기·폴더 만들기·이름 바꾸기, 빼면 비거나 `..`·휴지통 이름이면 400 —
맨 위 휴지통 자리에 아무것도 앉지 않음)·폴더째 올리기는 이름 그대로의 폴더를 찾고 없는 성분만 빼고 만듦·옮기기/복사의 겹쳐 짓는 새 이름 — 휴지통
옮기기·폴더 409·글자 빼기·잠금 넷을 하나씩 되돌려 겨눈 시험만 깨지는 것을 보았다)·**11-21 3차 검토**(옮기기가 된 뒤 실패를 알려도 휴지통 칸을 지우지
않음·덮어쓰기의 두기가 실패하면 되살린 자리를 알림 — 둘을 하나씩 되돌려 겨눈 시험만 깨지는 것을 보았다)·**11-21 빈 원래 파일**(0바이트 원래 것은
휴지통 없이 바꿈 — 대소문자만 다른 것·1바이트는 휴지통·빈 것을 빈 것으로·관리의 지우기는 그대로 휴지통·크기를 0 으로 적는 제공자의 파일은 열어 보고
휴지통으로·비켜 둔 뒤 커지면 원래 이름으로 휴지통에·두기가 실패하면 원래 자리로(그새 같은 이름이 생기면 `이름 (1)`)·비켜 두지 못하면 휴지통 길 —
열어 보기·다시 보기·비켜 두기 셋을 되돌려 겨눈 시험이 깨지는 것을 보았다. 4차 검토가 더한 셋 — 열지 못하거나 읽다 실패하면 휴지통·비켜 둔 빈 것을
지우지 못하면 원래 이름으로 휴지통·커졌는데 휴지통에 넣지 못하면 지우지 않고 숨은 이름으로 남김, 넷을 되돌려 각각 겨눈 시험만 깨졌다)) · `RulesTest` 의 빼는 글자(모든 코드 포인트가 11-21 의 목록과 같다 — 이모지의 `U+FE0F`·태그 순서 국기·`U+180B–180D`·`U+3164`·`U+034F`·소프트 하이픈은 일반 이름에 남는다)와 휴지통 판정(Default_Ignorable 전부·그런 글자를 끼운 별칭은 숨은 자리·맨 위만) ·
`FileServerManageTest` 의 11-27 점검 셋(덮어쓰기에서 칸을 읽지 못하면 '그대로' 라 하지 않음·휴지통은 정확히 그 이름일 때만 씀·옮긴 뒤 새 이름 바꾸기가 실패하면 되돌림 — 하나씩 되돌려 각각 그 시험만 깨졌다) · `FileServerReadTest`(화면 세 파일과 CSP·nosniff·받기는 늘 attachment
이고 sandbox, PDF 만 inline·정보(저장 공간·스위치)·목록은 휴지통을 감추고 폴더만도·zip 의 내용과 한 번 쓰는 표·항목 수 상한은 받기 전에 413·**흘려
보낸다**(둘째 파일에서 막혀도 첫 파일이 이미 왔다)·동시에 둘·섬네일은 머리로 형식을 보고(이름만 그림인 HTML 은 404) 캐시하고 캐시 이름은 해시뿐·캐시
상한·JSON 읽개의 엄격함·**zip 표가 다 찼으면 폴더를 펴 보기 전에 503**) · `PeerFilterTest`(우리 그룹의
인터페이스·STA 로 들어오면 netId 로 거절(모듈의 판정)·그룹이 없으면 거절·답 2초 기억) · `DestinationFilterTest`(대역·폰 자신의 주소·LAN 의 on-link
접두와 셀룰러·넓은 접두 버림·풀지 못함은 거절이 아님·재바인딩) · `TcpServerTest`(받기 고리가 죽으면 포트를 놓고 알림·stop 의 멱등·상한·기록 묶기) ·
`NoticeRateTest` · `PassphraseVaultTest` · `RulesTest` · `LoopbackIntegrationTest`(셋을 한 환경으로 — 기록에 목적지가 없는지, 바깥 문은 JVM 의
`ProxySelector` 를 묻지 않는다(맨 `Socket()` 은 묻고 죽은 SOCKS 로 간다 — 대조)). 시험마다 서버 스레드에서 잡히지 않고 나간 예외가 0 인지 본다.

**포트를 쓰는 시험은 시험이 포트를 쥔다**(11-21 — `HttpProxyServerTest` 가 가끔 실패한다는 보고, 재현은 못 함). 포트 옮기기 시험(HTTP·파일·SOCKS5)은
`PortPair` — 임시 포트 범위 밖(20000–29999)의 이어진 두 포트를 둘 다 쥐고 있다가 다음 포트를 서버를 세우기 바로 앞에서 놓는다(`handOver`). 예전
`squatWithFreeNext` 는 `ServerSocket(0)` 자리의 다음 포트를 열어 보고 곧 닫았는데, 이 PC(윈도)는 임시 포트를 차례로 주어 그 포트가 곧 다음
`ServerSocket(0)` 의 것이었다 — 그 틈에 `ServerSocket(0)` 하나를 끼우면 옛 꼴은 20번에 20번 졌고 새 꼴은 0번이었다(Gradle 이 다른 모듈의 시험을 함께
돌린다 — `org.gradle.parallel`). 바깥 연결의 임시 포트는 윈도에서 듣는 소켓의 묶기를 막지 않았다. '닿지 못하는 원격'(HTTP 502·SOCKS5 의 거부·죽은
SOCKS)은 `refusingPort` — 듣지 않는 소켓으로 묶어 쥔 루프백 포트(쥔 동안 남이 묶지 못하고 임시 포트로도 나가지 않는다, 잇기는 거절 — 바깥 망으로
나가지 않는다). 예전 `closedPort` 는 묶었다 곧 놓은 번호였다. `:net:test :core:test :tether:test` 를 함께 `--rerun` 으로 다섯 번 — 모두 통과.

앱의 테더 시험(`:app:testDebugUnitTest` 의 일부): `TetherTextsTest`(포트 범위·DIED·라이브 영역의 상태 줄에 1초마다 바뀌는 수가 없음·꺼진 테더링은 한
줄과 까닭·켜짐 판정·상한으로 닫은 연결) · `TetherIsolationTest`(프로세스 다섯·내보냄·길게 누르기 화면은 받은 인텐트를 읽지 않음·specialUse·위치
권한·테더 소스의 import·앱 바로가기 없음) · `TetherWiringTest`(안드로이드에 기대어 JVM 에서 돌릴 수 없는 이음새를 소스·자원으로 — 그룹 인터페이스를
거르개에·숨은 API 없음·수신기 내보내지 않음과 ChannelListener·바뀜 합치기·보관소 하나·자동 완성·라이브 영역·단추 하나·통째로 끄기·끄기의 세 길·
타일 이름·포트 설명·macOS 스크립트) · `FileManTest`(서버가 묻는 화면 자산 셋이 다 있고 그뿐·HTML 에 인라인 스크립트·스타일·이벤트 속성 없음·바깥
주소 없음·이름을 HTML 로 그리는 길 없음·바꾸는 요청은 PUT 과 JSON 의 POST 뿐·관리 허용은 기본 끔이고 서버는 요청마다 읽음·섬네일은 그림 풀개를
부르지 않고 `java.io.File` 을 쓰지 않음·올리기는 늘 `overwrite` 를 밝히고 `1` 은 '덮어쓰기' 를 고른 뒤에만·'나머지에도 적용' 은 묶음 안에서만·'다시' 는
새 묶음·묻는 창의 기본 단추는 덮어쓰기가 아님·묻기는 하나씩·휴지통 보관 기간은 기본 30일이고 서버가 손볼 때마다 읽고 바꾸면 곧바로·'관리 허용' 곁의
단추·설명 한 문장·화면의 한 줄·창은 한 번에 하나(11-21 2차 검토)·'모든 파일에 적용 (겹치는 파일 N개)'·폴더와 겹치면 '덮어쓰기' 흐리게와 그 글이 서버와
같음·흐린 단추는 기본 단추가 아님·적용으로 덮어쓰기면 폴더와 겹친 것은 새 이름·서버가 준 이름을 씀·**화면의 `STRIP_RE`·`IGNORABLE_RE` 와 서버의 빼는 글자·떼는 글자가 각각 모든
코드 포인트에서 같음**·**이름·경로를 열쇠로 쓰는 맵은 원형이 없다**(11-27 점검 — `S.selected` 등을 `{}` 로 두면 `constructor`·`toString`·`__proto__`
이름의 파일이 늘 '고른 것' 이 되고 확장자 표가 `x.constructor` 를 그림으로 읽었다 — 화면은 `dict()`(`Object.create(null)`) 로, 남은 `{}` 는 열쇠가 정해진
셋뿐. node 24 로 옛 판과 새 판을 스크래치에서 견줬다)). 고친 것마다 그 고침을 잠깐 되돌려 겨눈 시험만 깨지는지 보았다(파일 서버의 NAS 꼴은 여섯 — 다른 출처·단순 POST·휴지통 감추기·관리 스위치·자기
안으로·zip 의 파일마다 내보내기 — 를 한꺼번에 되돌려 겨눈 시험이 모두 깨지는 것을 보았다. 11-21 사용자 결정의 고침 여섯 — 서버의 덮어쓰기 기본·두기
직전의 다시 보기·`.nomedia`·기록 없는 칸 남기기·zip 표 상한의 차례·서버가 설 때 손보기 — 도 하나씩 되돌려 겨눈 시험만 깨지는 것을 보았다).

### 확인한 것·확인하지 못한 것

에뮬레이터(API 31)에도 Wi-Fi Direct 가 있어 그룹이 실제로 선다(그룹 인터페이스 `p2p-wlan0-0`). 에뮬레이터에서 본 것 — 테더 프로세스가 따로 서고 그
맵에 WebView 본체·relro·쿠키가 없다, 단추 하나와 끄기 셋(화면·알림·타일 — 서비스·듣는 포트·그룹·알림이 모두 사라진다), Wi-Fi 를 끄면 통째로 꺼지기,
포트 +1(`nc` 로 8282·8080 을 쥔 채), 거르기(`adb forward` 는 '핫스팟 밖에서 온 연결', 폰 안의 `nc` 는 '이 폰 안에서 온 연결' — 기록에 주소 없음), 타일과
길게 누르기(서비스는 서지 않는다), 앱 바로가기 없음, 최근 앱에서 지워도 그대로, 릴리스(R8). `am crash` 로 프로세스를 죽이면 시스템이 서비스를 다시
세우지만 그룹은 이미 내려갔고 화면이 앞에 없어 `createGroup` 이 실패해 **테더링이 통째로 꺼진다**(서버만 남지 않는다 — '사용자가 정할 것').

**파일 서버의 NAS 꼴(2026-11-21)에서 본 것**: PC — `DevFileServer` 와 Edge 헤드리스(CDP)로 넓은 창(1400)·폰 폭(390, 터치 흉내)에서 목록·격자·
섬네일(원본 대신 그리기 포함)·나무와 서랍·보기(앞뒤·확대·밀어 넘기기·글·EUC-KR·HTML 은 글로 — 쪽 제목이 바뀌지 않음)·고르기(누르기·Ctrl·Shift·네모)·
3,000개 폴더에서 DOM 의 줄 35개·거르기·정보·단축키·새 폴더·이름 바꾸기·같은 이름 묻기 → '새 이름으로'·느린 망(2 MB/s)에서 대기열의 진행·속도·그만두기·
다시·옮길 곳 고르기·여러 개 zip(1.1 MB 를 받아 풀림)·휴지통으로·휴지통 보기·되살리기·**쓰는 중에 관리 스위치를 끄면 다음 요청이 403 이고 화면이 '관리
꺼짐' 과 까닭**·PDF 는 Edge 의 PDF 보기로·HTML 에 `inline=1` 을 붙여도 받기. 콘솔 오류(CSP 위반 포함) 0, 폰 폭에서 가로 넘침 없음. 에뮬레이터(API 31) —
'관리 허용' 스위치가 보이고 누르면 `manage_allowed` 가 곧바로 바뀐다(true → false 로 되돌려 둠), 디버그 탐침(`TetherFolderProbeActivity`)으로 실제 SAF
(`Download/web-share-test`)에서: 저장 공간(남은 4.6 GB / 10.4 GB), 섬네일 — EXIF 섬네일이 든 JPEG 는 제공자가 **EXIF 섬네일(3.3 KB)** 을, 작은 PNG 는
원본을, 512 KiB 넘는 PNG 는 없음, 동영상은 미디어 서버의 장면(1.6 KB JPEG, 0.1–0.3초), 앨범 그림 없는 WAV 는 없음 — 만들기·이름 바꾸기·복사(제공자의
`copyDocument`)·같은 폴더로 복사(사본)·새 이름으로 옮기기·자기 안으로 거절·휴지통·되살리기·영구 삭제. 테더링 켜기는 위치 권한을 주지 않아 보지 않았다
(테더 프로세스는 죽지 않았다).

**11-21 사용자 결정(덮어쓰기 묻기·보관 기간·`.nomedia`)에서 본 것**: PC — `DevFileServer` 와 Edge 헤드리스로 셋을 한꺼번에 끌어다 놓으면(둘이 겹침)
첫 물음에 '이 올리기의 나머지에도 적용' 이 있고 기본 초점이 '새 이름으로', 건너뛰기 + 적용이면 둘 다 그대로·새 것만 올라감, 다음 올리기는 다시 묻고
하나뿐이면 '나머지에도' 가 없음, 덮어쓰기를 고르고 그만둔 뒤 '다시' 는 다시 묻고 원래 파일 그대로, Enter 는 새 이름(`new (1).txt`), 휴지통 보기의
'보관 기간 30일 — 지난 것은 저절로 비워집니다'(넓은 창·폰 폭, 가로 넘침 없음·나가면 사라짐), 콘솔 오류 0. 처음 돌렸을 때 동시에 살피던 둘이 함께 물어
앞의 것이 영영 '살피는 중' 에 남는 것을 보고 묻기를 하나씩으로 고쳤다. 에뮬레이터(API 31) — 테더 화면의 '휴지통 보관: 30일 (기본)' 과 고르는 창,
7일을 고르면 `trash_days` 가 곧바로 7(30 으로 되돌려 둠), 디버그 탐침으로 실제 SAF(`Download/web-share-test`)에서 휴지통에 `.nomedia` 가 **그 이름
그대로** 생기고(제공자가 확장자를 붙이지 않았다) 손보기(30일)가 새 칸을 지우지 않고, 비우기 뒤 휴지통에 `.nomedia` 만 남는다. 보지 못한 것: 갤러리가
`.nomedia` 를 보고 휴지통의 사진을 실제로 감추는지(MediaStore 의 다시 훑기), 하루 차례가 실제로 하루 뒤에 도는지(시험은 간격을 짧게 주입), 테더링을
켠 채 설정을 바꿨을 때 곧바로 도는지(위치 권한을 주지 않아 테더링을 켜지 않았다 — JVM 시험만).

**11-21 그 뒤 결정(모든 파일에 적용·폴더와 같은 이름·글자 빼기·덮어쓴 것은 휴지통)에서 본 것**: PC — `DevFileServer` 와 Edge 헤드리스로 다섯을
한꺼번에 끌어다 놓으면(파일 셋 + 폴더 `Photos` 와 같은 이름) 첫 물음에 '모든 파일에 적용 (겹치는 파일 4개)', 기본 초점 '새 이름으로', 적용 + 덮어쓰기면
파일 셋은 덮이고 원래 것 셋은 휴지통 칸으로(`.nomedia` 함께, 관리 꺼짐), `Photos` 는 `Photos (1)` 로(줄에 '새 이름(같은 이름의 폴더)'), 새 파일은 그대로.
폴더와 같은 이름 하나(`photos`)는 '같은 이름의 폴더가 있습니다' 와 흐린 '덮어쓰기'(title 같은 글), Enter 는 새 이름. `사진<RLO>gpj.exe`·폭 없는 글자 든
이름은 뺀 이름으로 올라가고 줄에 '이름 다듬음', 새 폴더 창의 미리 보기 한 줄, 관리를 켜면 휴지통에 덮인 셋이 원래 자리와 함께 보인다. 폰 폭(390)의 묻는 창 —
가로 넘침 없음. 콘솔 오류 0. 보지 못한 것: 폴더째 올리기에서 성분을 뺀 경로로 겹침을 세는 것(헤드리스에서 끌어다 놓은 폴더를 만들지 못한다 — JVM
시험과 소스만), 실제 SAF 제공자에서 덮어쓰기의 휴지통 옮기기(`moveDocument`)와 두기가 같은 이름을 지우지 않는 것(에뮬레이터는 돌리지 않았다), SAF
제공자가 방향·폭 없는 글자가 든 이름을 어떻게 다루는지(이제 서버가 빼므로 새로 생기지는 않는다).

**사용자 폰(이제 Android 16 폰)에서만 될 것**: 실제 Wi-Fi Direct 칩의 그룹 만들기·BUSY, **그룹 인터페이스의 이름**(상태 줄에 보인다)과 손님이 그 검사를
지나는지, 연결 변경 방송이 오는지, 사용자 폰의 `rp_filter`(`adb shell cat /proc/sys/net/ipv4/conf/{all,default,wlan0}/rp_filter` — 1(엄격)이면 폰이 붙은
남의 Wi-Fi 에서 경로를 넣는 길(손님 거르기 4)을 커널이 막는다)·`tcp_fwmark_accept`·`ip rule`(SO_MARK 길을 켤지 정할 때), 공유 폴더의 제공자가 이름
바꾸기를 하는지, PC 의 tun2proxy·HTTP 프록시·UDP(DNS) 중계, 파일 서버(`http://192.168.49.1:8080`), 화면을 오래 끈 채(Doze)·셀룰러, 옛 앱에서 넘어갈 때의
끊김. **손님이 붙는 길은 에뮬레이터 하나로는 만들 수 없다** — 그룹에 들 다른 기기가 없고, 폰 안에서 붙으면 출발 주소가 `.1` 이라 거르개가 막는다.

**확인하지 못한 것**: 사용자가 '잘 작동합니다' 라고 했지만 무엇을 어디까지 보았는지(손님의 종류·그룹 인터페이스 이름·거절 줄)는 받지 못했다. 특히 사용자
폰의 프레임워크가 그룹 인터페이스 이름을 주는지(비면 15초 뒤 '핫스팟이 서지 않았습니다' — 테더링이 꺼진다), 연결마다의
인터페이스 조회의 비용, 스레드 예산 768 이 무거운 쓰임에 모자라지 않은지(넘으면 새 연결을 닫는다 — 화면에 수가 보인다), 삼성 One UI 의 퀵 설정이 길게
누르기에 `QS_TILE_PREFERENCES` 를 쓰는지, 한글 패스프레이즈의 안내(adb 로 한글을 칠 수 없어 JVM 시험만), 자동 완성의 '저장할까요?', TalkBack, 받는 중에 테더 프로세스가 처음 뜰 때 받던 파일이 지워지지 않는지(코드로만 — `WebApp.onCreate` 가 정리를
돌리기 **전에** 돌아간다), 파일 서버의 실제 주고받기·올리기의 임시 파일(JVM 시험만), **파일 서버의 새 화면을 실제 PC↔폰(Wi-Fi Direct)으로 쓰기**
— PC 브라우저(크롬·엣지·파이어폭스)의 `<a download>` 로 받는 zip 이 4 GB·6만 5천 항목을 넘을 때(Zip64 — JDK·안드로이드의 `ZipOutputStream` 이 저절로
쓴다고 보았다, 재지 않았다), 폴더째 올리기의 끌어다 놓기(크로미움 `webkitGetAsEntry` — 헤드리스에서는 지은 File 로만 보았다), 사용자 폰의 저장소 제공자가
`FLAG_SUPPORTS_MOVE`·`COPY`·`THUMBNAIL` 을 세우는지(삼성), SD 카드 공유 폴더의 저장 공간(볼륨의 자리를 statvfs — 에뮬레이터는 기기 저장소만), 실제
카메라 사진의 EXIF 섬네일, 큰 폴더(수만 항목)에서 SAF 목록 질의의 시간. tun2proxy 의 UDP 묶음 수(DNS 질의마다인지)와 동시 세션 200 은 기억으로
적었다.

**남은 위험**:
- **폰이 남의 Wi-Fi(STA)에 붙은 채 핫스팟을 켜면, 그 Wi-Fi 의 공유기를 쥔 사람이 손님 거르기를 지날 수 있다**(rp_filter 가 꺼져 있으면 — 위 '손님
  거르기' 4). 줄이려면 테더를 쓰는 동안 폰을 남의 Wi-Fi 에 붙이지 않는다(셀룰러로만) — 또는 NDK 로 읽는다('사용자가 정할 것'). 폰 안의 다른 앱·폰이
  붙은 망의 주소로 온 연결·다른 앱의 그룹은 막힌다(1–3).
- HTTP·파일 포트를 옮긴다(사용자가 감수 — 위 '무엇을 하나'). SOCKS5(PC 트래픽 전부)는 옮기지 않으므로 그 틈이 없다.
- 경계는 UID 다 — 테더 코드의 파일 읽기·쓰기 결함은 곧 브라우저의 비밀로 이어진다. 인증 없는 LAN 서버라 그룹의 손님은 폴더를 읽고 덮어쓸 수 있다
  (사용자 결정 — 패스프레이즈가 유일한 문 — 덮인 원래 것은 휴지통으로 가고 보관 기간이 지나면 정말 지워진다). '관리 허용' 을 켜 두면 옮기고 지울 수도 있다(지우기는 휴지통으로 — 비우기와 보관 기간(기본 30일)이 지난 것은 정말 지운다).
- UID 단위 망 정책이 둘을 묶는다 — 데이터 사용량·앱별 VPN·백그라운드 데이터 제한·배터리 예외가 브라우저와 테더에 함께 걸린다.
- 브라우저는 실기기에서 검증된 적이 없다 — 테더를 쓰려고 브라우저 전체를 실기기에 처음 깔게 된다.
- usb-tether 의 '알려진 한계' 는 그대로다 — UDP 의 NAT 표 없음·요청 DST 무시, HTTP 프록시는 TCP 만, 사용자 공간 처리라 폰 CPU 가 수백 Mbps 에서 한계,
  캡슐화로 MTU 문제가 날 수 있음, chunked 올리기 없음, 서버의 시스템 호출(소켓) 수준 시험은 루프백뿐.

### 함정 — 테더의 것

| 함정 | 내용 |
|---|---|
| `setSingleLine` 과 비밀번호 칸 | 입력 종류(비밀번호)를 건 **뒤에** `isSingleLine = true` 를 부르면 글자 바꾸기가 한 줄 꼴로 갈아 끼워져 비밀번호 가리기가 풀린다 — 패스프레이즈가 '보이기' 를 끈 채 그대로 보였다. 한 줄을 먼저 걸고 입력 종류를 나중에 건다 |
| SO_MARK 를 읽을 길 | `Os.getsockoptInt` 는 SDK 에 없다(컴파일 오류), 반사는 `MAX_TARGET_O` 로 막힌다 — 숨은 API 를 부르지 않는다(`TetherWiringTest` 가 본다) |
| `createGroup` 과 앞 화면 | 이 앱의 화면이 앞에 없으면 BUSY — 타일은 트램폴린을 띄운다(`startActivityAndCollapse(PendingIntent)`) |
| 길게 누르기의 화면은 내보내야 한다 | 시스템 UI 가 자기 uid 로 띄운다 — 그래서 그 화면은 받은 인텐트를 읽지 않고 테더 화면만 연다 |
| 타일의 클래스 이름 | 바꾸면 사용자가 더해 둔 타일이 사라진다 |
| MSYS 의 `cp` | 받아 둔 `adb`·`tun2proxy`(확장자 없는 맥·리눅스 판)를 Git Bash 의 `cp` 로 옮기면 같은 폴더의 `.exe` 를 덮어쓴다 — PowerShell 의 `Copy-Item` 으로 |
| 셸 스크립트의 CR | `macos-wifi.sh`·서버 스크립트에 CR 이 있으면 bash 가 줄 끝의 `\r` 을 명령의 일부로 읽는다 — LF 로만 |

## 설정 파일 zip

사용자 결정은 위 표의 '설정 파일 zip'. 단추 둘이 앱에 든 파일 묶음을 zip 하나로 공용 '내려받기' 폴더에 쓴다.

| 단추 | 자리 | zip | 안 |
|---|---|---|---|
| 서버 구성 파일 받기 | 설정 › 프록시(주 프로세스) | `web-proxy-server.zip` | `README.md`(서버 구성 절차) · `server/setup-squid.sh`·`verify.sh`·`uninstall.sh`·`lib/cert.sh`·`conf/*.tmpl` 넷 |
| PC 연결 파일 받기 | 테더링 › 다른 기기에서(`:tether`) | `web-tether-pc.zip` | `README.txt` · `windows-wifi.bat` · `macos-wifi.sh` |

- **어떻게.** `app/.../setup/` — `SetupZip`(순수: 묶음 목록 `SetupBundle`·zip 짓기·경로 거르기 — ASCII 조각만, 절대 경로·`..`·빈 조각·`\`·`:` 거절, 폴더
  항목 없음, 바이트 그대로)와 `SetupFiles`(자산 → zip → `MediaStore.Downloads`: zip 을 다 지은 뒤 IS_PENDING 으로 넣고 드러냄, 실패하면 그 항목을 지움,
  따로 된 스레드, 결과는 토스트 한 줄, 도는 동안 다시 누르면 무시). 자산은 `app/src/main/assets/setup/{proxy-server,tether-pc}/`, 글은
  `res/values/strings_setup_files.xml`. 테더 화면이 부르므로 `setup/` 은 브라우저의 것을 부르지 않는다(`TetherIsolationTest`).
- **남의 실행 파일은 담지 않는다** — 자산은 공개 저장소에 커밋된다. README.txt 가 받을 파일과 이름 바꾸기를 적는다(아래 '테더링' 의 'PC 쪽').
- **실행 비트.** `java.util.zip` 은 유닉스 실행 비트를 싣지 못한다 — README 와 스크립트의 안내가 `sudo bash …` 로 적는다. 서버 스크립트끼리는 경로로
  실행하지 않는다.
- **서버 파일**은 옛 확장의 `server/` 를 옮긴 것이다. 바꾼 것: 사용법의 실제 도메인 → `proxy.example.com`(실제 주소·계정은 자산 어디에도 없다), 화면에
  찍히는 실행 안내를 `bash` 로, 확장(Edge·PAC) 이야기 → 'Web 앱'(끝 요약은 'Web 앱의 설정 › 프록시에 위 값을 넣는다'). README.md 는 새로 썼다(전제
  조건·OCI 인그레스·구성과 옵션·verify.sh·uninstall.sh·'Web › 설정 › 프록시' 칸·검증·트러블슈팅·한계·법적 고려·출처).
- **PC 스크립트**(동작은 위 '테더링' 의 'PC 쪽'). 윈도의 관리자 판정은 `fltmc`(`net session` 은 Server 서비스가 꺼진 PC 에서 관리자여도 실패해 권한
  요청이 끝없이 되풀이된다), tun2proxy.exe 가 없거나 오류로 끝나면 창이 멈춘다(관리자 창은 끝나면 곧바로 닫혀 오류가 보이지 않는다),
  `enabledelayedexpansion` 은 쓰지 않는다(경로의 `!` 를 지운다), macOS 는 Ctrl+C 때 정리를 한 번만 한다. `--bypass 192.168.49.0/24` 는 더하지 않는다 —
  윈도에서 TUN 주소에 묶는 앱은 그래도 터널로 가서 돕지 않는다.
- 이 zip 이 옛 `Repositories/chrome-proxy-extension`·`Repositories/usb-tether`·이 앱의 `pc/` 를 대신한다(저장소에서 걷어 저장소 밖 백업 폴더로 옮겼다 —
  지우지 않았다). PC 가 지금 쓰는 tun2proxy 는 그 폴더들이 아니라 사용자의 바탕 화면에 있다.
- **시험.** `SetupZipTest` · `SetupAssetsTest`(묶음 목록 = 자산 폴더, `.bat` 의 ASCII·CRLF, 나머지의 LF·UTF-8, 실제 자산으로 지은 zip, 서버 파일의 예시
  도메인·`bash` 안내·확장 이야기 없음, PC 스크립트의 머리말·`fltmc`·멈춤·한 번의 정리) · `TetherIsolationTest`(`setup/` 도 브라우저의 것을 부르지 않는다).
  에뮬레이터에서 두 zip 이 `Download/` 에 생기고 항목이 자산과 바이트까지 같았다. **확인하지 못한 것**: 실제 서버에서 `sudo bash setup-squid.sh`, 윈도·맥에서
  zip 을 풀어 tun2proxy 와 함께 돌리기(이 PC 의 인터넷이 그 길을 지난다 — 끊지 않는다).

## 실험실 — 돌리는 법

```bash
python tools/lab/make_certs.py      # 한 번: 시험 CA·서버 인증서(*.lab.test, 10.0.2.2), 시험 계정, 디버그 NSC → 그 뒤 assembleDebug
env -u HTTP_PROXY -u HTTPS_PROXY -u NO_PROXY -u http_proxy -u https_proxy -u no_proxy \
    "$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Android_16_Phone -no-snapshot-load -gpu host -feature -Vulkan -dns-server 127.0.0.1
python tools/lab/run_browser.py                 # 이 앱 — 기대가 낡았다(아래). 시나리오 이름을 주면 그것만(ready 를 함께)
python tools/lab/run_lab.py                     # WebView 의 성질(약 15분) — 묶음·실험 이름을 주면 그것만, --repeat N
```

**에뮬레이터는 두 꼴로 띄운다.** 실험실 꼴(위 — `-dns-server 127.0.0.1`)은 실험실 서버가 꺼져 있으면 **어떤 이름도 찾지 못한다** — 사용자가 손으로 써
볼 때는 `-dns-server` 없이 띄운다(프록시 변수는 똑같이 뺀다 — 이 PC 는 프록시 없이 바로 나갈 수 있다). 사용자에게 넘길 때는 손으로 쓰는 꼴로 다시 띄워
둔다.

PC 의 루프백에 서는 것(에뮬레이터 안에서 `10.0.2.2` 가 PC 의 `127.0.0.1`): **시험 프록시** 18443(TLS — Squid 와 같은 꼴: Basic realm `proxy`, 407 뒤
연결을 이어 쓴다)·18080(평문 대조군), **목적지** 8080·8443(탐침 쪽들 — 내려받기·쿠키·출구 확인 `/cdn-cgi/trace` 포함), **웹소켓** 8081, **DNS**
53/udp(`*.lab.test`·`labhost` → `10.0.2.2`, **나머지는 NXDOMAIN** — 누가 무엇을 찾았는지 다 적는다), **STUN** 3478–3492/udp(WebRTC 의 길마다 포트
하나). 시험 프록시는 `*.lab.test`·`labhost` 만 잇고 나머지는 403 으로 거절하며 적는다. 실험 하나는 앱 프로세스 하나다(`am start -S`, 프록시 설정이
프로세스 전체라서). 두 실행기는 `Android_16_Phone` 에뮬레이터인지 확인하고서야 깐다(1.0.0 부터 — 앞은 `Android_12_Phone`. 그 AVD 의 옛 앱에는 사용자의 실제 프록시가 있다). **시험 미디어**는 `maker` 시나리오가 처음 한 번 기기의
MediaRecorder 로 만든다(캔버스의 숫자·사인파 — 저작물이 아니다). 실험실이 받아 `local/lab/media/`(git 무시)에 두고, `labmedia.py` 가 나눠 DASH·HLS
fMP4 를 짓고 같은 표본으로 TS(PAT·PMT·PES)와 묶음 AAC(ID3 시각 + ADTS)를 짠다(따로 만든 조각처럼 연속성 카운터가 조각마다 처음부터다). 잠긴 HLS 의
조각은 openssl 로 실제로 잠근다.

`run_browser.py` 는 설정을 디버그 전용 `LabSetupActivity` 로 넣고(adb 의 셸만 열 수 있다 — `android.permission.DUMP`), 화면의 일은 디버그 빌드가 logcat
`WebUI` 에 적는 줄로 읽고, 주소창·메뉴·스위치·물음 창은 실제로 두드린다. 판정은 실험실 서버의 사건으로 한다. **시작할 때 디버그 APK 를 깔고 앱의
데이터를 지운다**(`pm clear`) — **사용자가 에뮬레이터의 앱에 넣은 실제 프록시 설정도 사라진다. 사용자의 실제 프록시가 든 에뮬레이터 앱에는 돌리지
않는다**(그 앱에는 `install -r` 로만 깐다). **기대가 낡아 지금은 돌리지 않는다**: 전체 화면이 기본으로 켜져 막대가 4초 뒤 숨으므로
`uiautomator dump` 에 주소창·스위치·메뉴·탭 단추가 없고(고칠 길: `LabSetupActivity` 에 `fullscreen=0` 을 두거나, 누르기 전에 위에서 밀어 막대를
부른다), 검토의 고침으로 찾은 영상의 저장 이름(페이지 제목)·실시간 창을 닫는 단계·HLS 마스터의 요청 수·탭 단추의 설명('탭 N개')이 바뀌었다.

**실험실의 함정**

| 함정 | 내용 |
|---|---|
| **에뮬레이터가 PC 의 `http_proxy` 를 쓴다** | 이 PC 에는 `HTTP_PROXY`·`HTTPS_PROXY` 가 걸려 있다(폰 테더링의 프록시). 에뮬레이터는 그것이 있으면 손님의 TCP 를 전부 그 프록시로 보내고, 그 프록시는 사설·루프백 주소를 거절한다 — `10.0.2.2` 에 닿지 못해 **모든 실험이 `ERR_PROXY_CONNECTION_FAILED`** 였다. 지우고 띄운다(위 명령). PC 의 `curl` 도 같은 변수를 쓴다 |
| `-dns-server 127.0.0.1` | 에뮬레이터의 DNS(10.0.2.3)를 실험실의 DNS 로 받는다. 띄우기 전에 서버가 없어도 된다. 실행기가 먼저 `ping dnscheck-….lab.test` 로 확인한다. 실험실 서버가 꺼지면 이름을 하나도 못 찾는다(위 '두 꼴') |
| **adb 의 오류에 명령의 인자를 싣지 않는다** | extras 에 시험 계정이 있다(한 번 출력에 찍혀 바꿨다) |
| **`uiautomator dump` 와 비밀번호** | **비밀번호 칸의 글도 그대로 나온다**(에뮬레이터의 '비밀번호 보이기' 기본값 탓으로 보인다 — 확인하지 않았다). 비밀번호 칸이 있는 화면은 dump 하지 않는다. 설정 화면은 프록시의 사용자 이름도 글자 그대로 보인다 — dump 하지 말고 화면 캡처로 본다 |
| **실제 프록시가 든 앱으로 실험실의 받기** | 앱의 HTTP 는 예외 규칙을 따른다 — 예외 목록이 `10.0.2.2` 를 덮으면(사설망 켬 — 기본) 받기가 바로 가지만, 덮지 않으면 **사용자의 실제 프록시로** `10.0.2.2:…` 를 청한다(실제로 그랬다). 그 앱의 예외 목록이 덮는 줄 알 때가 아니면 실험실의 받기를 시험하지 않는다 — 실험실 판(시험 프록시)으로 한다. 출구 확인은 예외와 상관없이 늘 프록시로 간다 |
| **`adb emu network speed` 는 Wi-Fi 경로에 듣지 않는다** | 512 kbps 로 묶었다고 믿고 큰 받기를 돌렸다가 몇 초 만에 약 210 MB 를 받았다. **셀룰러로 가면 듣는다**(`adb shell svc wifi disable` 뒤). 이 PC 의 인터넷은 사용자 폰의 회선을 지난다 — 큰 받기를 고르기 전에 경로부터 묶는다 |
| `network speed edge` 와 프록시의 TLS | 셀룰러로 돌리고 EDGE(473.6 kbps)로 묶자 **사용자 프록시를 지나는 터널 안의 TLS 가 응답 없이 멈췄다** — CONNECT 의 200 은 왔는데 30초 시간 초과가 세 번. 같은 셀룰러에서 `full` 로 풀자 6초에 받았다. 까닭은 모른다. 시험이 끝나면 `network speed full`·`svc wifi enable` 로 되돌린다 |
| **글을 치기 전에 앞 화면을 본다** | 앱이 뒤로 빠진 줄 모르고 `input text` + ENTER 를 보냈더니 **홈 화면의 Google 검색창**에 시험 문자열이 들어가 에뮬레이터의 계정으로 검색됐다. 치기 전에 `dumpsys activity activities` 의 `mResumedActivity` 와 `dumpsys input_method` 의 `mServedView` 를 확인한다 |
| 전체 화면에서 위 가장자리 밀기 | 막대가 이미 보일 때 같은 몸짓을 하면 **시스템의 알림 창**이 내려온다 — 그 창에는 에뮬레이터 계정의 개인 알림이 있다. 알림 창을 dump 할 때는 우리 줄만 거른다 |
| 사용자가 띄워 둔 탭 | 새 빌드를 깔 때마다 세션이 되살아나 그 쪽이 다시 열린다 — fast.com 같은 측정 쪽이면 사용자 프록시의 데이터를 쓴다. 시험 동안은 가벼운 쪽으로 바꿔 두고 끝나면 되돌린다 |
| **에뮬레이터의 클립보드 공유** | 에뮬레이터 안에서 복사하면 **PC 의 클립보드를 덮는다**(에뮬레이터의 클립보드 공유) — 그 글은 'host clipboard' 라는 이름표로 다시 들어온다. 복사가 드는 시험 앞에 사용자에게 알린다 |
| 크롬 영상에서 죽는 에뮬레이터 | 크롬으로 유튜브를 틀자 qemu 가 abort 했다(호스트 Vulkan 의 'device lost'). `-gpu host -feature -Vulkan` 으로 띄운 뒤로는 크롬·Web·WebView Shell 로 여러 번 틀어도 버텼다. 죽은 뒤 다시 띄우면 **충돌 보고를 구글에 보낼지 묻는 창**이 떠 부팅이 멈춘다 — 보내지 않는다(에뮬레이터를 끄고 `%LOCALAPPDATA%/Temp/AndroidEmulator/emu-crash-<판>.db/reports/` 의 덤프를 옮기면 묻지 않는다) |
| 막 부팅한 에뮬레이터 | 기본 네트워크가 모바일에서 Wi-Fi 로 넘어가며 첫 연결을 끊는다 — 실행기가 Wi-Fi 가 자리 잡을 때까지 기다린다(`wait_network`). 첫 쪽이 20초를 넘길 때가 있다 — 미디어 쪽은 40초까지 기다린다 |
| 에뮬레이터의 시스템 서버가 죽는다 | 실험을 수십 번 잇달아 돌리다 `system_server` 가 감시견에 죽었다 살아났다. 실행기는 그 실험을 '확인 불가' 로 두고 부팅이 끝나기를 기다리며, 실험 사이에 1.5초 쉰다 |
| UDP 가 가끔 빠진다 | A 질의 하나가 실험실에 오지 않아 리졸버의 5초 재시도에 걸린 적이 있다 — 결함이 아니다 |
| 백그라운드로 띄운 에뮬레이터 | 도구의 백그라운드 명령은 30분(기본)에 멈춘다 — 에뮬레이터 프로세스는 남아 다시 띄우면 '같은 AVD 를 둘' 로 실패한다. 띄울 때 시간 제한을 2시간으로 준다 |
| 내보내지 않은 화면 | `am start -n …/.settings.SettingsActivity` 는 말없이 실패한다(셸은 내보내지 않은 화면을 열지 못한다) — 메뉴로 들어간다 |
| Git Bash | `adb shell` 의 `/sdcard/…` 는 `MSYS_NO_PATHCONV=1` 없이는 바뀐다 |
| `run_browser.py` 를 고칠 때 | logcat 의 옛 줄(주소창에 칠 때마다 logcat 을 비운다 — 그 앞의 줄은 먼저 읽는다), 뒤늦은 파비콘(켜기·끄기 시험은 쪽이 다 열리고 잠시 뒤 스위치를 누르는 때부터 센다), 물음 창의 단추(`android:id/button1` 로), 되살린 탭의 같은 제목(주소의 고유한 표로 기다린다 — `wait_page`), `am start -W` 와 바로 끝나는 화면(`-W` 없이), `dumpsys window` 의 첫 `mCurrentFocus` 는 마지막 ANR 의 기록일 수 있다, 기기의 DNS 로는 구글을 가릴 수 없다(프록시가 받은 것으로만 본다), 주소창으로 연 내려받기의 첫 GET 은 WebView 의 것이다(앱의 요청 `Accept: */*` 만 센다), 실험실 서버의 길 차례(좁은 길을 위에), 순수 파이썬 TS 는 한 번 짜서 둔다, EME 는 보안 맥락에서만(https), 화질 이름(HLS `0.7 Mbps`·DASH `600 kbps`), 같은 출처의 맨 iframe 순번(새 틀은 본문 끝에), 바쁜 기기의 주소창(친 글이 같아진 뒤 Enter — `type_address`), 키 이벤트 뒤의 초점 테두리(손으로 쓸 때는 보이지 않는다) |

## 실험 결과 — WebView 의 성질

WebView 153.0.8010.36·Android 12 에뮬레이터·실험실 프록시에서 `run_lab.py` 로 잰 것(원본은 `local/lab/results-*.md`). 이 앱의 길·인증·가드가 이 결과 위에
서 있다. 남는 누출은 DNS 미리 찾기(감수)다 — WebRTC 는 2판 가드가, 같은 이름의 401 은 중계가 막는다.

| 묶음 | 결과 | 브라우저 설계에 준 것 |
|---|---|---|
| **HTTPS 프록시 인증** | **된다.** TLS 로 감싼 프록시의 407 이 `onReceivedHttpAuthRequest(host="프록시 이름"(포트 없음), realm="proxy")` 로 오고, 답하면 http·https 쪽 모두 열린다. 걱정했던 `ERR_PROXY_AUTH_UNSUPPORTED`(2023 보고)는 재현되지 않았다. 한 번 답하면 그 프로세스(그 프로필)의 새 연결은 계정을 먼저 싣는다. 평문 프록시·중계도 같다 | 직접 모드도 가능하지만 아래 '같은 이름의 401' 때문에 중계로 갔다 |
| 틀린 비밀번호 | 직접: 콜백이 `useHttpAuthUsernamePassword()=false` 로 거듭 온다 → 끊으면 407 쪽. 중계: http 쪽은 502 + `X-Iroiro-Relay: upstream-auth`, **https 쪽은 `ERR_TUNNEL_CONNECTION_FAILED` 뿐** | 중계가 원격의 거절을 앱에 따로 알린다(`Listener`) |
| **같은 이름의 401** | 프록시와 **같은 호스트 이름의 사이트**가 같은 realm(`proxy`)으로 401 을 내면 콜백이 프록시의 407 과 똑같다. 직접 모드에서 이름으로 답하면 **사이트가 프록시 계정을 받았다**. 중계 모드는 realm 이 무작위라 가린다 | 사용자의 프록시 호스트에는 웹 사이트도 있다. **중계 하나로**(사용자 결정) |
| 앱의 HTTP(`HttpURLConnection`) | 플랫폼 HTTP 는 TLS 프록시를 모른다 → 중계를 평문 프록시로 쓴다. `Authenticator` 로 주면 http·https 모두 된다. **`requestingHost` 가 `localhost` 다**(127.0.0.1 을 거꾸로 찾은 이름) — 루프백 주소와 포트로 가린다 | `RelayAuthenticator` |
| **직접 연결로 새지 않는가** | 프록시 포트가 죽음·중계 뒤 원격이 죽음·프록시 이름을 못 찾음·**프록시 인증서를 믿을 수 없음**·프록시가 CONNECT 를 403 으로 거절·계정을 거절 — 모두 목적지에 닿지 않았고 그 이름을 기기가 찾지도 않았다(`addDirect` 를 넣지 않으면 된다) | 그대로 |
| 예외 규칙 | `10.0.0.0/8`(IP 대역)·`*.direct.lab.test`(무늬)·`<simple>`(점 없는 이름) 모두 직접, 예외가 아닌 이름은 프록시로 갔고 기기가 그 이름을 찾지 않았다 | 확장의 예외 목록을 옮겼다(기본값은 확장의 것에서 PC 의 테더링 대역을 뺀 것). 앱의 HTTP 도 같은 규칙을 크로미움처럼 읽는다(위 '앱의 HTTP 와 예외 규칙') |
| **DNS 미리 찾기** | 프록시를 걸어도 **기기가 이름을 찾는다**: `<link rel=dns-prefetch>`·`Link:` 머리의 dns-prefetch·스크립트가 넣은 것·`innerHTML` 로 넣은 것. 찾지 않는 것: preconnect·`<a>`·실제 요청·speculation rules prefetch·`<link rel=prefetch>`. **쪽 자신의 이름은 찾지 않는다.** 투기적 로딩을 꺼도 같고, 스크립트 가드는 스크립트가 `rel` 을 적는 한 길만 막는다 | **앱으로는 못 막는다**(WebView 에 그 설정이 없다 — 소스로도 확인). 사용자가 감수 — 연결 상태 대화가 알린다('개인 DNS' 를 켜면 통신사에는 보이지 않는다) |
| **WebRTC** | 프록시를 비켜 UDP 로 STUN 에 닿고, 후보에 **기기의 실제 사설 IP 가 그대로**(mDNS 가림 없음) 나온다. **1판 가드**(문서 시작 스크립트로 지우기)는 **같은 출처의 빈 틀 열 가지 길로 비켜 간다**(contentWindow·`frames[i]`·동기 onload·innerHTML·onload 속성·파서·document.write·자식 창의 appendChild·contentDocument.defaultView·src=about:blank). **2판 가드**(틀이 생길 수 있는 모든 길의 바로 뒤에서 틀 나무를 훑어 지우고 그 창에도 갈고리를 건다)는 **15가지 길을 모두 막았다**(1.17 의 `addJavaScriptOnEvent` 로 넣어도 같다) | 2판을 쓴다(`PageScripts`). 남는 위험: 15길 밖의 새 길, 쪽이 갈고리를 알아챌 수 있다(`toString`). 화상 통화는 되지 않는다 |
| 웹소켓 | `ws://` 도 프록시의 CONNECT 로 갔고 기기는 이름을 찾지 않았다 | **사용자의 Squid 는 CONNECT 를 443·10443 에만 허락한다**(`SSL_ports`) — `ws://`(80)·443 아닌 https 는 실제 프록시에서 막힌다 |
| 머리 | 모든 요청에 **`X-Requested-With: io.github.donggi.iroirobrowser`** — 개인 앱의 이름이 곧 사람을 가리킨다. `REQUESTED_WITH_HEADER_ALLOW_LIST` 는 WebView 153 이 지원하지 않는다. **1.17 의 `Profile.addCustomHeader("X-Requested-With", "")` 가 WebView 의 값을 빈 값으로 바꾼다**(쪽이 단 `XMLHttpRequest` 는 그대로). UA 에는 `wv` 와 기기 모델이, 클라이언트 힌트에는 `Android WebView` 가 실린다 | 빈 값으로 덮는다(`WebViews`). 출구 IP 자체가 사용자 하나의 것이라 머리를 지워도 사람은 가려진다 |
| **서비스 워커** | 새 프로세스의 첫 탐색이 서비스 워커가 맡은 쪽이면 **직접 모드에서 늘 실패**(`ERR_HTTP_RESPONSE_CODE_FAILURE`, 5/5) — 워커의 요청에는 WebView 가 없어 407 에 답할 길이 없다. 중계 모드는 4/4 통과(시간 운이다). **다른 쪽을 먼저 열면 둘 다 된다 — 화면에 붙이지 않은 WebView 로 열어도 된다** | 데우기(중계가 스스로 답하는 주소 — 바깥으로 아무것도 나가지 않는다) |
| 프로필 | 프록시 설정(프로세스 전체)은 **다른 프로필에도 걸린다**. **인증 캐시는 프로필마다 따로**다 | 프로필을 늘리면 그 프로필도 데운다 |
| 올리기 | 4 MiB POST 가 http·https 쪽, 직접·중계 모두 온전히 갔다 | — |
| 속도 | 실험실에서 직접 5.5~5.9 MB/s, 중계 4.6~4.9 MB/s. 둘 다 에뮬레이터의 SLIRP 와 파이썬 TLS 프록시가 병목이라 **절댓값은 뜻이 없다** | 셀룰러 해외 프록시에서는 중계의 몫이 묻힐 것으로 본다(추측) |
| WebView 기능 | PROXY_OVERRIDE·REVERSE_BYPASS·DOCUMENT_START_SCRIPT·JS_INJECTION_IN_FRAME_AND_WORLD·CUSTOM_REQUEST_HEADERS·MULTI_PROFILE·SERVICE_WORKER_*·SPECULATIVE_LOADING·PRECONNECT·PROFILE_URL_PREFETCH·NAVIGATION_LISTENER·DELETE_BROWSING_DATA 등이 모두 true, REQUESTED_WITH_HEADER_ALLOW_LIST 만 false | 폰의 WebView 는 늘 최신(사용자) — 같거나 새 판 |

## 탭별 프록시 검토 (검토만, 코드 없음)

**결론 — 쿠키를 함께 쓰는 '진짜 탭별' 은 WebView 로 할 수 없다. '세계 둘'(프록시 세계·직접 세계 — 탭마다 어느 세계인지 고른다, 세계마다 쿠키·로그인·
저장소가 따로)로는 할 수 있다.** WebView 의 프록시는 프로세스에 하나다(`ProxyController` — `Profile`·`WebViewBuilder` 에 프록시 API 가 없다). 프록시 설정은
모든 프로필에 걸리고 인증 캐시는 프로필마다 따로다(실험 profile). 같은 프로필 안의 탭은 가를 수 없다(중계가 받는 요청에는 어느 탭인지가 없다). 프로필은
WebView 마다 처음에 한 번만 정한다(`WebViewCompat.setProfile` — 탭의 세계를 바꾸려면 WebView 를 새로 만든다).

**짜는 길.** 기본 프로필 = 프록시 세계(지금의 쿠키 그대로), 새 프로필 `direct` = 직접 세계. 중계 하나에 세계마다 계정을 따로 내고(407 의 답은 WebView 마다
오므로 그 탭의 세계 계정을, 데우기도 세계마다) **계정으로 길을 가른다** — 프록시 계정 → 원격, 직접 계정 → 목적지에 바로, 그 밖 → 407. 지금 중계가 지키는
'목적지에 직접 붙는 길이 이 클래스에 없다' 를 깨는 일이라 직접 길은 따로 된 클래스로 두고 '프록시 계정의 요청은 직접으로 새지 않는다' 를 JVM 시험으로
박는다. 지금 기본 프로필에만 걸리는 것을 프로필마다로(`X-Requested-With` 덮기·서비스 워커 설정·서드파티 쿠키 막기·**내려받기의 쿠키**(직접 세계의 탭에서
프록시 세계의 쿠키를 실으면 두 신원이 이어진다)·사이트별 데이터·기록 지우기). **세계가 둘인 것은 대가이면서 보호다** — 같은 쿠키가 두 길로 나가면 사이트가
실제 IP 와 프록시 쪽 신원을 잇는다(지금 스위치를 끌 때가 그렇다). 착수 전 실험실에서 — 두 세계가 같은 목적지를 동시에 열 때 CONNECT 의 계정이 섞이지
않는가, 세계마다 데운 뒤의 서비스 워커 요청, 다른 프로필로의 `saveState` → `restoreState`, 직접 세계에도 `X-Requested-With` 덮기·WebRTC 가드가 듣는가.
**에뮬레이터의 이 앱에는 사용자의 실제 프록시·쿠키가 있다** — 따로 된 패키지 이름의 실험판으로 한다(프로필은 한 번 만들면 그 프로세스에서 지울 수 없다 —
위 '익명 탭을 버리며 알게 된 것').

**더 가벼운 길 — 사이트별 직접.** 예외 규칙(`ProxyConfig` 의 bypass)에 메뉴의 '이 사이트는 직접 연결' 을 더한다. 탭이 아니라 **목적지 호스트**로
갈리고 모든 탭에 걸린다. 그 쪽이 다른 호스트에서 받는 것(CDN·API)은 여전히 프록시로 가서 일부만 직접이 될 수 있다. 쿠키는 한 세계라 그 사이트에서는
실제 IP 와 쿠키가 이어진다. 고칠 곳은 메뉴·설정과, 규칙만 바꿀 때 중계를 다시 세우지 않고 길만 다시 거는 것.

## 데스크톱 다운로더에서 옮기지 않은 것

데스크톱 다운로더(`Repositories/iroiro-downloader`)의 일반 기능(HLS·DASH·직접 파일·AES-128·소리 합치기·이어받기)은 이 앱에 이미 있고 페이지의 쿠키·
Referer·UA 가 그대로 붙는다. 옮긴 것은 다운로더가 사이트 로직을 직접 지은 pixiv·hitomi 뿐이다('갤러리 받기'). 옮기지 않은 것과 까닭(바깥 사실은 2026-10
조회 — 법률 자문 아님):

| 사이트 | 까닭 |
|---|---|
| YouTube | **넣지 않는다**(데스크톱에 둔다 — 브라우저에서는 '재생 중인 것 받기'). yt-dlp 는 JS 런타임·PO Token·SABR 을 요구해 가고, 안드로이드에서 도는 성숙한 길(youtubedl-android·NewPipeExtractor·bgutil)은 모두 GPL-3.0 이라 이 저장소(MIT)에 맞지 않는다. MIT 로만 가려면 Chaquopy·yt-dlp·JS 엔진·WebView 의 PO Token 을 직접 엮어야 하고, yt-dlp 가 바뀔 때마다 APK 를 다시 깔아야 한다. 약관이 다운로드·자동화를 금한다 |
| bilibili | 되지만 위험이 크다 — '재생 중인 것 받기' 를 먼저. 사용자 Squid 는 CONNECT 를 443·10443 에만(PCDN 포트), 출구가 미국(지역·CDN), WBI 서명·캡차, 폰의 `MediaMuxer` 가 못 담는 조합(FLAC·E-AC-3·AV1+Opus) |
| 일반 m3u8·mpd | 이미 된다 — 빠진 것은 '주소로 받기'(붙여 넣기) 진입뿐 |

hitomi 는 공개 저장소에 두면 DMCA 통지를 받을 수 있다(2026-03 FAKKU 의 통지가 gallery-dl 의 hitomi 추출기와 안드로이드 앱까지 겨눴고 gallery-dl 은 그
추출기를 지웠다 — 사용자가 알고 정했다). 일본은 침해 만화의 사적 내려받기도 위법이다(문화청).

## 유튜브 광고 검토

'유튜브 광고 가리기(B1)·건너뛰기(B2)' 의 뒷받침(에뮬레이터, m.youtube.com, 로그인 없음 — 법률 자문 아님).

- **모바일 웹은 광고를 따로 된 물음으로 받는다** — 플레이어 응답에는 자리만 있고(`adPlacements`·`adBreakServiceRenderer`) 재생할 때
  `m.youtube.com/youtubei/v1/player/ad_break`(와 `get_midroll_info`)로 묻는다. EasyList 가 그 물음을 막아 광고가 나오지 않는다. 그러나 광고가 플레이어
  응답에 바로 실리거나(`instreamVideoAdRenderer`·`adSlots`) 본편 스트림에 이어 붙으면(SSAI) 막을 물음이 없다 — 목록을 켠 채 앞 광고 묶음이 나온 적이
  있다. 그래서 B1·B2 다.
- 광고는 본편과 **같은 `<video>`** 로 재생되고 광고 동안만 재생기(`#movie_player`)에 `ad-showing ad-interrupting` 이 붙는다(덮개 `.ytp-ad-player-overlay`·
  `.ytp-ad-pod-index--mweb`·`.ytp-ad-duration-remaining--mweb`·`.ytp-ad-skip-ad-slot`). 소리를 끈 채 저절로 도는 재생은 광고를 부르지 않는다. 건너뛰기
  단추(`.ytp-ad-skip-button-modern`)는 광고 5초 뒤에 크기를 얻는다.
- 가리기에 opacity 를 쓰지 않는다(쌓임 순서가 바뀌어 진짜 탭이 단추에 닿지 않는다 — B1 의 표). 스크립트의 `.click()` 은 유튜브가 무시한다는 보고가
  여럿이다 — 그래서 진짜 터치다.
- **하지 않는 것** — B3(`currentTime = duration` — 서버가 광고 길이만큼 본편을 늦출 수 있다는 보고가 있다), A(쪽의 세계에서 `JSON.parse`·fetch 를 감싸
  `adPlacements`·`playerAds`·`adSlots` 를 지우기 — 쪽의 세계 상시라 이 앱의 규칙과 어긋나고, uBO·AdGuard 코드는 GPL-3.0 이며, 유튜브가 거의 매일 바꾼다).
- 유튜브 약관(제한 사항 — 우회·자동화 금지)과 도움말('광고 차단은 약관 위반')이 있고 공식 길은 Premium 이다. 데스크톱 사이트(www.youtube.com)에는 uBO 의
  무거운 규칙이 따로 걸린다 — 이 앱은 모바일 웹만 봤다.

## 더할 만한 기능 검토 (검토만 — 고르지 않은 후보)

조사(WebView 로 지은 브라우저들, 크롬·파이어폭스·브레이브·삼성 인터넷·Cromite 의 공식 문서·저장소, androidx.webkit 의 릴리스 노트·소스)가 낸 것
가운데 아직 하지 않은 것.

- **열린 쪽의 실행 세계에 그때 스크립트를 돌리는 길이 있다** — `JavaScriptReplyProxy.executeJavaScript(script, receiver)`(그 프록시를 보낸 틀·실행
  세계에서 돈다, 1.16 부터). 글 고르기의 '검색'·뒤에서 영상 숨기기가 쓴다. 가끔 쓰는 스크립트는 BRIDGE 에 늘 싣지 않고 이 길로 돌린다.
- 1.17.1 에 있는데 쓰지 않는 것 — `setAudioMuted`·`setAlgorithmicDarkeningAllowed`·`getBackForwardCacheSettings`(실험)·`setWebViewRenderProcessClient`·
  `setWebAuthenticationSupport`·`prerenderUrlAsync`. 1.18.0-rc01 에는 이 앱에 쓸 것이 없다(1.17.1 에 머물러도 된다).
- 아마 이미 되는 것(폰에서 볼 것) — 비밀번호 관리자의 자동 완성(WebView 의 안드로이드 자동 완성 — 앱이 끄지 않았다), 고른 글의 번역(글 고르기 메뉴의
  번역 앱 — 그 앱들은 프록시를 거치지 않는다, 추론).

| 후보 | 규모 | 메모 |
|---|---|---|
| PDF 로 저장·인쇄 | S | `PrintManager` + `createPrintDocumentAdapter` — 시스템 인쇄 화면의 'PDF 로 저장'. 다 연 뒤에만·WebView 하나에 한 작업씩. Files 가 PDF 를 연다. MHTML(`saveWebArchive`)은 다시 열려면 꺼 둔 파일 접근을 켜야 해 권하지 않는다 |
| 사이트별 직접 연결 — 메뉴 '이 사이트는 프록시 없이' + 유튜브 묶음(youtube.com·youtu.be·googlevideo.com·ytimg.com·ggpht.com) + 막대의 '직접' 표시 | S–M | '탭별 프록시 검토' 의 가벼운 길. 예외 호스트에 `*.youtube.com` 을 넣으면 지금도 된다 — 없는 것은 메뉴·묶음·표시. 미국 출구에서 유튜브가 봇 확인을 낸다. 대가 — 그 사이트·CDN 에 실제 IP, 기기 DNS 에 이름, 같은 구글 쿠키가 두 IP 로. **사용자 결정** |
| 미디어 도구 — 탭 음소거(`setAudioMuted`)·아무 쪽 영상의 재생 속도(실행 세계에서 `playbackRate`)·잠자기 타이머 | S×3 | 탭 음소거로 B1 의 광고 음소거를 바꾸면 쪽이 소리를 다시 켜는 것과 다투지 않는다(그때는 뒤에서 재생 잇기의 '소리 나는 재생' 판단도 함께) |
| 웹 페이지도 어둡게 — 알고리즘 어둡게 하기 + 사이트별 끄기 | S | targetSdk 33 부터 WebView 는 `prefers-color-scheme` 을 앱 테마로 정하고 알고리즘 어둡게는 꺼 둔다 — 밤 모드에서도 다크 CSS 가 없는 사이트는 희다 |
| 화면 속 화면(PiP) | M | `supportsPictureInPicture`·Android 12 의 `setAutoEnterEnabled`, 작은 창의 단추는 `RemoteAction` → `media-control`. PiP 동안은 창이 실제로 보이므로 화면 밖 그리기·`keepWindowVisible` 이 걸리지 않게. '뒤에서 재생' 의 상태 기계에 갈래가 하나 는다 |

그다음 — **SponsorBlock**(유튜브 영상 안의 협찬 구간 건너뛰기 — API·자료는 CC BY-NC-SA 4.0, 영상 ID 의 SHA-256 앞 4글자로만 묻는다, 확장의 코드는 LGPL
이라 새로 쓴다, 앱이 저절로 감는 것이라 **사용자 결정**), **읽기 모드 + 소리 내어 읽기**(Readability.js — Apache-2.0, 새 서드파티라 승인. 주 틀의
복제본에서 돌리고 결과는 JS 를 끈 따로 된 WebView 에 CSP `default-src 'none'` 로 그린다. 네트워크 목소리의 TTS 는 글을 프록시 밖으로 보낸다), **사이트별
설정**(데스크톱 사이트 기억·글자 크기·JS 끄기·그림 끄기 — JS 를 끈 사이트에서는 통로·B1·B2 도 꺼진다), **데이터 사용량**(한 UID 라 시스템이 가르지 못한다 —
호스트별 기록은 또 하나의 방문 기록이라 '방문 기록 지우기' 에 함께), **뒤로 가기 캐시**(실험 API — 기본값을 기기에서 먼저 잰다), **홈 화면
바로가기**(`requestPinShortcut` — 인텐트는 내보내지 않은 작은 화면으로), **번역**(쪽 전체는 사용자의 API 열쇠나 기기 안 모델 — 새 서드파티). 낮은 순위 —
응답 없는 쪽 끝내기(렌더러를 탭이 함께 써 끝내면 모든 탭이 덮개로 간다), 쿠키 알림 처리, 큰 파일을 여러 연결로 받기, 탭 그룹·탭 목록 찾기, 사이트
정보(인증서), '주소로 받기'.

**하지 말 것**(조사의 판단 — 바꿀 수 있다): 크롬 확장·남의 사용자 스크립트(WebView 에 확장 API 가 없고, 사용자 스크립트는 쪽의 세계에 상시 걸린다),
패스키(브라우저 모드는 Google 승인의 특권 앱 목록이 필요하고 `origin` 은 Android 14 부터, 앱 모드는 자산 링크로 묶인 사이트만), 자체 비밀번호
관리자·동기화(안드로이드 자동 완성이 한다), 익명 탭·검색어 제안·세이프 브라우징(거절한 뒤 새 까닭이 없다), 내장 QR 스캐너(CAMERA 권한 — 삼성 카메라가
읽고 '복사한 링크 열기' 로 잇는다), 지문 위장(쪽의 세계에 상시 갈고리 — 덧붙여 출구는 미국인데 시간대(Asia/Seoul)·언어(ko)는 한국을 알린다, WebView 에
시간대를 바꿀 API 는 없어 보인다 — 추론), DNS 누출 막기(앱 전용 VPN — 감수로 정했다), WebRTC 허용(실험에서 실제 IP 가 샜다), 프리렌더·프리페치(누르지
않은 쪽을 셀룰러·프록시로 미리 받는다), 구글 번역 위젯(남의 스크립트를 쪽의 세계에), 유튜브 A·B3.

**보류한 결정을 다시 볼 까닭** — 다른 앱의 링크(VIEW): 삼성 카메라의 QR·메신저 링크를 바로 열려면 필요해진다. 문을 덜 여는 차례는 '복사한 링크
열기'(있다) → 확인 창을 거치는 '공유 → Web'(`ACTION_SEND`) → VIEW. `_lact` 는 잠자기 타이머와 짝으로.

## 보안 점검 — 알고 둔 것

점검의 발견 가운데 고치지 않은 것(사용자: '수정할 가치가 있는 것들만'). 고친 것은 위 '사용자 결정' 의 '보안 점검 고침'.

- **유튜브 출처의 주 문서가 B2 의 진짜 터치를 부를 수 있다** — 건너뛰기 단추를 찾는 것은 실행 세계의 스크립트지만 DOM 은 쪽과 함께 쓰므로, 유튜브
  출처에서 코드가 도는 쪽(유튜브 자신이나 그 출처의 XSS)은 광고 표지와 단추처럼 보이는 것을 꾸며 앱이 그 자리를 진짜 터치로 누르게 할 수 있다(그
  쪽의 보이는 영역 안·1초에 한 번). 유튜브 출처의 코드 실행이 전제라 고치지 않았다.
- **디버그 키 서명** — 릴리스도 이 PC 의 디버그 키로 서명한다(사용자 결정 — 키를 바꾸는 것은 사용자가 정할 때 한 번). 앱 자료는 백업·기기 이전에서 빠지고
  앱 안에서 옮기는 길(백업)은 걷었으므로, 키를 바꾸거나 다시 깔면 북마크·기록·숨긴 요소·팝업 허용·예외 규칙·목록 설정이 모두 사라진다.
- **DNS 미리 찾기 누출·테더의 무인증** — 이미 감수로 정한 것(위 '사용자 결정').
- 열쇠의 '쪽이 보낸 머리 따라 하기' 가 재생목록 요청의 Referer 로 물러선 갈래는 그 값을 다른 출처의 열쇠에도 그대로 싣는다(쪽이 실제로 보낸 값이라
  그대로 두었다 — 위 '내려받기' 의 'Referer 를 홉마다').

**2차 점검(2026-11-20 — 10-16 뒤에 바뀐 것) 고친 것**: 찾은 재생목록을 뒤에서 저절로 읽는 길(쪽 하나면 된다)에서 — `UriRefs` 의 점 마디 걷기가 마디마다
남은 길을 베껴 제곱 시간(MB 단위 한 줄로 일꾼이 몇 분), `HlsPlaylist.parse` 가 지은 주소에 상한이 없어(주소마다 바탕 주소가 통째로 붙는다) 메모리
부족으로 앱이 죽을 수 있었다 — 자리로 세어 걷고, 한 재생목록에 주소 10만 개·글자 3,200만(넘으면 재생목록이 아닌 것으로). '복사한 링크 열기' 의
`ClipText` 가 `lowercase()`(길이가 바뀐다 — `İ`)의 자리를 원래 글에 써서 던졌다(누르면 앱이 죽었다) — 글자마다 소문자로. `windows-wifi.bat` 의 시스템
도구를 온전한 경로로. 그 권한 올리기가 스크립트의 경로·인자를 PowerShell 의 홑따옴표 글에 붙여 넣어 경로에 `'` 가 있으면(사용자 이름 등)
실패했다 — 환경 변수(`WEB_TETHER_SELF`·`WEB_TETHER_ARGS`)로 넘긴다(11-21, 사용자). 시험 `UriRefsTest`·`HlsTest`·`ClipTextTest`·`SetupAssetsTest` — 고침을 되돌리면 각각 그것만 깨진다.

2차 점검이 고치지 않은 것(사용자 2026-11-21 — 아래 셋은 그대로 둔다):
- **열쇠의 머리 따라 하기와 CSRF** — 쪽이 고른 재생목록의 열쇠 주소에, 쪽이 그 주소로 보낸 스크립트 머리(미리 묻기가 거절됐을 요청의 것이어도 —
  `intercept` 는 CORS 앞이다)와 그 주소의 쿠키(SameSite 와 상관없이 — 내려받기 전체가 그렇다)를 실은 GET 이 나간다. 사용자가 그 쪽의 HLS 를 받기로
  골라야 하고 응답은 쪽에 돌아가지 않는다. 막으려면 다른 사이트의 열쇠에는 스크립트 머리를 싣지 않는 길이 있다(사용자가 정할 것).
- **DASH 의 상한** — 화질마다 조각 5만 개까지지만 화질 수·주소 글자 합에는 상한이 없다. 사용자가 그 MPD 를 받기로 골라야 한다(일꾼은 Throwable 을 잡지만
  메모리 부족은 다른 스레드에도 닿는다). HLS 처럼 합의 상한을 둘 수 있다.

**3차 점검(2026-11-27 — 독립 점검의 중간 둘·낮음 넷) 고친 것**: ① PC 도우미의 '시작 시 실행' 이 관리자 권한으로 .NET exe 를 곧바로 띄워 같은 사용자가 쓸 수
있는 HKCU\Environment 의 프로파일러 변수로 DLL 이 관리자로 올라가던 것(T1574.012) — 처음에는 conhost --headless + cmd /d 의 시작 스크립트가 비운 뒤 띄우게 고쳤고(`d95c1fcb`), 같은 날
사용자 결정으로 **관리자 부분을 서비스로 뗐다**(위 'PC 도우미' 의 '서비스' — 시작 스크립트와 작업은 걷었다) ② 파일 서버 화면의 이름을 열쇠로 쓰는 맵이 원형을 가져 `constructor`·`__proto__` 같은 이름이 늘 '고른 것' 이 되던 것 ③ 휴지통 이름의
보이지 않는 글자 별칭(판정만 Default_Ignorable 전부 — 일반 이름에서 빼는 글자는 11-21 그대로, 재검토)·제공자가 다른 이름으로 지은 휴지통 ④ 덮어쓰기의 휴지통 실패 글 ⑤ 옮긴 뒤 이름 바꾸기 실패의 숨은 임시 이름 ⑥ PC 도우미의 DLL 찾기·이름으로
정하던 tun2proxy·되살리기의 어댑터 GUID·192.168.49.x 어댑터가 여럿일 때의 길·설치 폴더의 낯선 파일. **고치지 않은 것(적기만)**:
- **ProgramData 선점의 서비스 거부** — 보통 권한의 프로그램이 도우미보다 먼저 `%ProgramData%\WebTether` 를 만들면 도우미는 그것을 `.untrusted-<때>` 로 비키고 새로
  만든다. 비키지도 못하게(그 폴더를 지우기 공유 없이 열어 쥐거나 안의 파일을 잠가) 두면 도우미는 '상태 폴더를 쓸 수 없어 시작하지 않습니다' 로 뜨지 않는다 —
  권한을 얻지는 못하고 테더 연결만 막는다. 서비스로 옮긴 뒤에도 같다 — 서비스가 시작할 때 같은 길로 준비한다(설치기가 미리 만들지 않는다).
- ~~관리자 부분을 서비스로 떼기~~ — 11-27 사용자 결정으로 했다(위 'PC 도우미' 의 '서비스'). 서비스(SCM 이 띄운다)에는 사용자의 환경 블록과
  `HKCU\Software\Microsoft\.NETFramework` 가 닿지 않는다. 관리자 판 설정 창(`--settings`)도 걷었다(사용자 결정 '서비스 확인 창으로'). 사용자가 UAC 로 승인해
  띄우는 관리자 판은 `--install`·`--uninstall` 둘이고, 트레이가 `runas` 전에 HKCU 의 `Environment`·`Volatile Environment`(와 세션 하위 키)의 Framework CLR 변수(`COR_`·`COMPlus_`·
  `APPDOMAIN_MANAGER_`·`__COMPAT_LAYER`)·`Software\Microsoft\.NETFramework`(값 아무거나)를 읽기만 해 있으면 올리지 않고(`Elevation.Precheck`), 제 환경에서
  CLR 변수를 넓게 지운다(`ClrEnv.Scrub` — 검토 11-27).
  **남는 것**: 내려받기 폴더에서 처음 띄우는 exe 의 `mscoree.dll` 정적 가져오기(KnownDLLs 밖 — 'DLL 찾기'), runas 로 올린 프로세스가 환경을 어디서 짓는지
  (부모의 블록인지 사용자 프로필인지 — 확인하지 못했다. 어느 쪽이든 그 바탕인 HKCU 는 올리기 전에 본다), 확인하고 올리는 사이에 HKCU 를 바꾸는 틈(같은
  사용자의 프로그램이 UAC 창이 떠 있는 동안 써 넣을 수 있다). 설정 바꾸기의 새 권한 모델(콘솔의 보통 사용자가 확인 창의 '예' 로 SYSTEM 서비스의 설정을
  바꾼다 — 설치본 트레이에서 온 것만, 트레이에 주입하면 비켜 간다)은 위 '사용자 결정'.
- **미확인 위험 — `HKCU\Software\Microsoft\.NETFramework` 로 들어오는 CLR 설정**: 서비스에는 닿지 않게 됐다. 관리자 판 둘에는 그 키에 값이 하나라도 있으면
  올리지 않게 했다(검토 11-27) — CLR 이 실제로 무엇을 읽는지·무엇이 코드를 올릴 수 있는지는 여전히 확인하지 않았다.
- 연결 전 '다른 tun2proxy' 는 여전히 이름으로 본다(배치 파일의 것과 부딪치지 않으려는 것) — 이름만 빌린 프로그램이 연결을 막을 수 있지만 오류 글로 보인다.

## 사용자가 정할 것

- **다른 앱의 링크를 받을지 — 보류**(사용자). 받으면(VIEW 인텐트) 메신저·메일의 링크가 이 브라우저로 열리고 그 링크도 프록시를 탄다. 지금은 받지
  않는다(런처로만 연다). 받기로 하면 '기본 브라우저' 후보가 된다
- Squid 의 `SSL_ports`(CONNECT 를 443·10443 에만) — `ws://`·443 아닌 https 를 쓰려면 서버를 고친다
- **쿠키·사이트 데이터의 보관 기간** — 지금은 사이트가 정한 만료까지 둔다(방문 기록·캐시만 기간을 고른다). 기간으로 지우면 로그인이 풀린다. 넣는다면
  '오래 안 간 사이트의 데이터 지우기'(방문 기록의 마지막 때로 사이트를 가려 `deleteBrowsingDataForSite`)가 덜 거칠다
- **`Sec-GPC: 1`**(정체 감추기에서 넣지 않은 것). 모든 요청에 '팔지 말라' 는 머리를 싣는다(출구가 미국이라 뜻이 있다 — 추론, `Profile.addCustomHeader`
  로 머리만). 대가는 머리 하나만큼 남과 달라 보이는 것
- 광고 목록의 **'Wi-Fi 에서만 자동 갱신'**(지금 꺼짐). List-KR·filterslists-KO 는 주소를 확인하지 못했다(설정에서 주소로 더한다)
- **실시간 방송 받기**('멈출 때까지 받기') — 지금은 실시간 방송이면 받지 않는다(재생하며 녹화·가로채기로는 담긴다)
- 가로채기에서 **화질이 바뀐 구간** — 지금은 바이트가 가장 많은 화질만 담는다. 여러 화질을 한 파일로 하려면 다시 인코딩이 필요하다
- **가로채기의 AV1 거절을 풀지** — 지금은 늘 거절한다. 16 의 `MediaMuxer` 도 MP4 에 Opus 를, WebM 에 AV1 을 담지 못해(소스로 확인 — 위 '재생 중인
  것 받기' 의 '유튜브') AV1 + Opus 는 한 파일이 되지 않는다. 풀려면 소리를 다시 인코딩(Opus → AAC)하거나 두 파일을 받아들여야 한다
- 녹화의 화질(지금 5 Mbps·160 kbps)과, 재생 중인 요소가 여럿일 때 고르는 화면(지금은 재생 중·영상·큰 것을 저절로)
- **서비스 워커가 내는 요청의 UA**(기기 모델·`wv` 가 그대로 간다 — 실측). 고를 길: ① 감수(권고 — 출구 IP 가 이미 사용자 하나의 것이다), ② 서비스
  워커의 네트워크 요청을 막는다(`ServiceWorkerWebSettings.setBlockNetworkLoads` — 오프라인·푸시를 쓰는 사이트가 깨질 수 있다), ③ 서비스 워커의 GET 을
  앱의 HTTP 로 대신 받는다(본문이 있는 요청은 못 한다 — 호환 위험)
- **탭별 프록시**(위 '탭별 프록시 검토'): ① 세계 둘(탭마다 고른다, 쿠키·로그인이 세계마다 따로) ② 사이트별 직접(쿠키 하나, 호스트로 갈린다) ③ 둘 다
  ④ 하지 않음. ①이면 함께 정할 것 — 막대 스위치가 '지금 탭을 다른 세계로 옮기기' 가 되는가(그러면 지금의 전역 끄기를 없앨지), 새 탭의 세계(연 탭을
  물려받기가 컨테이너의 관례), 방문 기록·북마크를 두 세계가 함께 쓸지
- 막대의 몸짓에서 조사가 권했지만 하지 않은 것(위 '탭·세션·막대의 몸짓')
- **갤러리 받기**(사용자는 사이트를 정했고 나머지는 검토의 권고대로 지었다. 바꿀 수 있는 것): pixiv 인증(지금 WebView 로그인 / OAuth), 폴더(지금
  `Download/<사이트>/<작가>/…`), 우고이라(지금 zip — MP4 로 하려면 MediaCodec 다시 인코딩, 약 500줄), 긴 받기의 wake lock(지금 없음 — 화면을 끈 채 작가
  받기를 몇 시간 돌리면 멈출 수 있다), '주소로 받기'(붙여 넣기 — 지금 없음), 갤러리 받기는 동시에 하나(사이트에 몰아 묻지 않게), 로그인이 필요해 건너뛴
  작품만 다시 받는 길(지금은 다시 받으면 모두 다시 — ` (1)` 사본)
- **유튜브 광고** — B3(건너뛸 수 없는 광고를 끝으로 감기)는 넣지 않았다 — 건너뛸 수 없는 광고는 가리고 끈 채 끝까지 간다(뒤에서는 그동안 무음). 서버가
  광고 길이만큼 본편을 늦출 수 있다는 보고가 있다
- **뒤에서 재생 잇기**(Claude 의 판단으로 지었다): 유튜브만(다른 사이트의 소리도 이을지 — `BackgroundPlay.wanted`), 멈춘 뒤 남기는 10분
  (`PAUSE_GRACE_MS`), 끄는 설정 스위치(지금 없다 — 알림의 '닫기'). 뒤에서 나온 광고는 건너뛸 수 있게 될 때까지의 몇 초가 무음이다
  - '계속 시청하시겠습니까?'(유튜브가 오래 만지지 않은 재생을 스스로 멈춤) — 다루지 않는다. 브레이브는 목록 스크립틀릿이 5분마다 `window._lact`(유튜브의
    마지막 활동 시각)를 새로 쓴다. 넣으려면 보임 속이기 옆의 한 줄(쪽의 세계)이다
  - 화면 밖에 그리는 크기 — 지금은 뷰 크기 그대로(줄이면 GPU·메모리가 줄지만 쪽이 타일을 그 배율로 다시 그릴 수 있다 — 재지 않았다)
- **테더링**(Claude 의 판단으로 지었다):
  - 상한 — 스레드 예산 768·UDP 묶음 128(나머지는 usb-tether 그대로). 넘으면 새 연결을 닫는다
  - 손님 거르기의 넷째 검사(받은 소켓의 SO_MARK 로 연결이 **들어온** 망 보기)를 켤지 — 앱이 읽을 공개 API 가 없어 NDK(네이티브 코드·CMake — 새 빌드
    의존, APK 에 ABI 마다 작은 `.so`)가 든다. 켜지 않는 동안 폰이 붙은 남의 Wi-Fi 의 공유기를 쥔 사람이 손님 거르기를 지날 수 있다(rp_filter 가 꺼져
    있으면). 다른 길: 핫스팟이 켜진 동안 폰이 Wi-Fi(STA)에 붙어 있으면 화면에 경고하기
  - 프로세스가 죽어 시스템이 서비스를 다시 세울 때(sticky) 핫스팟도 다시 켜 볼지 — 지금은 켜 본다. 에뮬레이터에서는 그룹이 이미 내려갔고 화면이 앞에
    없어 `createGroup` 이 실패해 통째로 꺼졌다. 다른 길: 다시 세울 때는 켜 보지 않고 끄고 까닭만 남기기(PC 가 저절로 다시 붙을 기회를 버린다)
  - 알림의 '테더링 끄기' 단추(지금 있다 — 잘못 누르면 PC 의 인터넷이 끊긴다) — 원치 않으면 걷는다
  - **파일 서버(NAS 꼴)의 판단 거리**(11-21, Claude 가 정한 것 — ①–④ 는 사용자가 정했다: 덮어쓰기는 그대로 두되 묻기, 관리 허용은 테더링과 별개,
    휴지통 보관 기간(기본 30일), `.nomedia` — 위 '사용자 결정'·'서버의 세부'. 거기서 Claude 가 정한 것: 비우는 때는 서버가 설 때·하루마다·설정을
    바꿀 때(휴지통 API 를 부를 때는 아니다), 딱 기간인 것은 아직 지우지 않음, 비우기가 휴지통 폴더와 `.nomedia` 를 남김, 폴더와 같은 이름으로 올리면
    묻고 '덮어쓰기' 를 골라도 400(폴더를 파일로 덮지 않는다), 묻는 창의 기본 단추 '새 이름으로') ⑤ 섬네일을 이 프로세스에서 풀지 않기로 해서 **EXIF 섬네일이 없는 큰 그림**(스크린샷 PNG 등 512 KiB
    넘음)은 서버 섬네일이 없다 — 화면이 3 MB 까지는 원본으로 대신 그리고 그 위는 아이콘. 다음 길은 `isolatedProcess` 서비스(따로 된 샌드박스 UID — 앱
    자료에 닿지 못한다)에서 풀기다(새 구성 요소·프로세스) ⑥ 바꾸는 요청에 `Origin` 이 없으면 받는다(curl 등 — 브라우저는 늘 싣는다)
- **m3u8 열쇠**(위 '찾은 미디어·HLS·DASH' 의 '열쇠 받기'): 실패 글이 '쪽이 보낸 머리를 실음' 인데도 열쇠 대신 HTML·JSON·빈 응답을 말하면 조사의 F — 그
  틀의 실행 세계에서 열쇠를 `fetch`(Origin·쿠키·Sec-Fetch·지문까지 쪽과 같다. 탭이 그 쪽에 있어야 하고 사이트가 보는 요청이 하나 더 나간다 — 실험실에서
  실행 세계의 Origin·CORS·CSP 부터 잰다). 실패가 한참 받은 뒤에 나면 조사의 D — 모든 줄기의 첫 열쇠를 조각보다 먼저 받고, 늦은 열쇠가 실패하면
  재생목록을 다시 읽어 한 번 더, 그래도 안 되면 받은 것을 남긴 채 '멈춤'(지금은 '실패' 로 받은 것을 지운다)

## 남은 일

- **실기기** — 브라우저는 실기기에서 검증된 적이 없다(사용자가 깐다). 실제 폰(이제 Android 16 폰)의 WebView 판·셀룰러에서의 속도, 삼성 One UI·삼성 키보드에서의 꼴
  (글 고르기 메뉴의 '검색' 자리, '복사한 링크 열기' 가 삼성의 글 분류에서 뜨는지, 인라인 완성)
- **테더링** — 지금 판을 사용자 폰에 다시 깐다(위 '사용자가 폰에서 할 일'), 특히 삼성 One UI 에서 타일을 길게 누르면 테더 화면이 열리는지(AOSP 는 그
  액션을 찾고 없으면 앱 정보를 연다). 손님이 붙는 길은 사용자 폰에서만. 사용자가 확인한 뒤 폰의 옛 usb-tether 앱을 지운다
- **설정 파일 zip** — 실제 서버에서 zip 을 풀어 `sudo bash setup-squid.sh` 로 돌리기, 윈도·맥에서 PC 연결 파일과 tun2proxy 를 함께 돌리기
- **m3u8 열쇠** — 사용자 기기에서 그 사이트로 다시 받아 보기(안 되면 새 실패 글의 갈래를 받는다)
- **보안 점검 고침** — 실제 쪽에서 내려받기·HLS 의 홉마다 Referer, 광고 차단의 막는 수를 새 정규식 엔진 전후로 같은 쪽에서 견주기
- **갤러리 받기** — 위 '갤러리 받기' 의 '확인하지 못한 것'과 실기기
- **유튜브 광고 가리기·건너뛰기** — 사용자의 프록시(미국 출구)에서의 실제 광고, 전체 화면 영상의 광고 건너뛰기, 데스크톱 사이트(www)의 클래스, 퍼가기,
  실기기
- **뒤에서 재생 잇기** — 위 '뒤에서 재생 잇기' 의 '확인하지 못한 것'(특히 사용자 기기에서 끊김·화질과 배터리 견주기)
- **앱의 HTTP 와 예외 규칙** — 기기 확인(실험실 판으로 사설망 받기·HLS·찾은 미디어 크기·사설망↔인터넷 넘겨주기 — 사용자의 실제 프록시가 든 앱으로는
  실험실 받기를 하지 않는다)
- 길: `applyToWebView` 의 try 는 `buildConfig` 만 감싼다 — WebView 가 예외 규칙을 받지 않으면 `setProxyOverride` 가 메인 스레드에서 던질 수 있다
  (칸을 단단히 하기 전에 저장된 `08.…` 같은 규칙). 감싸고 예외 없이 다시 걸려면 앱의 HTTP 도 그 판에서 예외를 따르지 않게 맞춘다
- 중계: 평문 http 의 원격 연결 재사용(지금은 요청마다 원격 TLS). 터널마다 원격 TCP·TLS 를 새로 연다(크롬이 HTTPS 프록시에 하는 것과 같다) — 미리 열어 둔
  연결을 쓰면 새 연결마다 왕복 둘을 줄일 수 있다(재지 않았다. 셀룰러의 NAT 가 쉬는 연결을 끊는 것과 데이터 비용을 함께 봐야 한다)
- 서비스 워커가 내는 요청의 UA(위 '사용자가 정할 것')
- 가로채기: 워커 안의 MSE(크롬 108+ `MediaSourceHandle`)는 문서 시작 스크립트가 닿지 않아 놓친다 — 녹화를 쓴다
- blob: 본문 밖의 고리는 이름(`download` 속성)을 모른다 — '준비하고 다시 열기' 뒤에도 이름은 'download'
- 실기기의 Widevine(라이선스·프로비저닝)·HDR·B 프레임이 많은 실제 스트림의 합치기 — 에뮬레이터는 MediaCrypto 가 없고 시험 미디어는 B 프레임이 없다
- 광고 차단: 일반 숨김을 늦게 붙는 광고까지(지금은 7초 뒤까지), 틀 안의 숨김, 공개 접미사 목록 없이 어림하는 제1자/제3자
- `tools/lab/run_browser.py` 의 기대를 지금 동작에 맞추기(위 '실험실')
- 기기에서 보지 않은 것(브라우저): 끔 → 켬의 탭 접기·되살리기, HLS·DASH 를 받는 중의 스위치·'저장', 만료와 링크 갱신, 고아 정리, 중계의 인증 전
  자리(JVM 시험만), 알림의 단추, SPA 의 방문 기록(유튜브 모바일), 전체 화면 회전, 당겨서 새로 고침, 데스크톱 사이트, TalkBack
