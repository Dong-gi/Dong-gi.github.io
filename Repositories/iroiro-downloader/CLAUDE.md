# CLAUDE.md

## 프로젝트 개요

개인 사용 목적의 미디어 다운로더. Python 3.12+ / PySide6 GUI / httpx HTTP 클라이언트.
Pixiv, YouTube, bilibili, hitomi.la, M3U8(HLS) 스트림, MPD(MPEG-DASH) 스트림을 지원하며, 새 사이트를 쉽게 추가할 수 있는 익스트랙터 구조로 설계되어 있다.

## 실행

```powershell
pip install -r requirements.txt
python main.py
```

## 아키텍처

### 스레드 모델

- **메인 스레드**: Qt 이벤트 루프 전담. 위젯 수정은 반드시 메인 스레드에서만.
- **워커 스레드** (`DownloadWorker`): 사이트별 익스트랙터의 `download()`를 실행. 네트워크 I/O 전담.
- 스레드 간 통신은 Qt Signal/Slot으로만 수행. 직접 위젯 접근 금지.

### 협력적 취소 (Cooperative Cancellation)

`DownloadWorker`는 `threading.Event`인 `_stop_event`를 보유한다.
`worker.pause()`를 호출하면 event가 set되고, 익스트랙터는 청크 루프마다 이를 확인해 `InterruptedError`를 발생시킨다.
워커는 `InterruptedError`를 잡아 `task_paused` 시그널을 emit한다.
`terminate()`는 사용하지 않는다.

```
worker.pause()
  → stop_event.set()
  → 익스트랙터 청크 루프에서 InterruptedError
  → _save_image에서 불완전 파일 자동 삭제 (dest.unlink)
  → DownloadWorker.run()이 task_paused emit
  → MainWindow._on_paused() → 삭제 팝업 표시
```

### 다운로드 삭제 플로우

- `DOWNLOADING` 상태에서 삭제 요청 시: `pause()` → `PAUSED`로 전환 → 워커 정지 후 팝업
- `PAUSED` 상태에서 삭제 요청 시: 즉시 팝업
- `PENDING / DONE / FAILED`: 팝업 없이 즉시 제거
- 팝업 선택지: **파일도 삭제** (`shutil.rmtree(task.save_path)`) / **작업만 삭제** / **취소** (PENDING으로 복귀 후 재개)

### 작업 ID

`BaseExtractor.make_task_id(url)`로 URL에서 결정론적 ID를 생성한다. 같은 URL은 항상 같은 ID → 중복 감지에 사용.
- Pixiv artwork: `pixiv-artwork-{illust_id}`
- Pixiv user: `pixiv-user-{user_id}`
- 기본(미구현 사이트): UUID

중복 ID가 이미 큐에 있으면 `_show_duplicate_dialog(task)`를 호출한다.
- FAILED / PAUSED: **재시작** 버튼 (status → PENDING, progress 초기화)
- DONE: **폴더 열기** 버튼 (`os.startfile(task.save_path)`)
- 항상: **닫기** 버튼

### 세션 복원

`closeEvent`에서 실행 중인 워커를 `pause()` + `wait(5000)`으로 중단하고 `session.json`에 저장한다.
`__init__` 완료 후 `_load_session()`으로 복원한다.
- 저장 경로: `%APPDATA%\iroiro-downloader\session.json`
- DOWNLOADING 상태로 저장된 작업은 `Task.from_dict()`에서 PENDING으로 변환되어 재시작된다.

### 익스트랙터 레지스트리

`ExtractorRegistry` 클래스가 익스트랙터 인스턴스를 보관한다. `init_registry(config)`로 단일 레지스트리를 초기화하고, 모듈-레벨 헬퍼로 조회한다.
- `get_extractor(url)` — `can_handle()`이 True인 첫 번째 익스트랙터
- `extractor_for_site(site_id)` — site_id 기반 조회 (task ID에서 역추출한 값으로 인증 처리 시 사용)
- `all_extractors()` — 전체 목록 (설정 창이 인증 항목을 구성할 때 사용)

각 익스트랙터는 `site_id` 클래스 변수를 가진다 (예: `"pixiv"`, `"youtube"`, `"bilibili"`). `make_task_id()`가 이 값을 task ID prefix로 사용하며, `site_id_from_task_id(task_id)` 헬퍼로 역추출 가능.

### GUI ↔ 익스트랙터 계약 (사이트 하드코딩 금지)

GUI는 **사이트별 분기(isinstance/site 문자열 비교)를 하지 않는다.** 익스트랙터가 자신의 능력을 선언하고 GUI는 그것만 읽는다. 사이트를 추가해도 GUI 코드는 그대로다.

| 선언 | 위치 | GUI 동작 |
|------|------|----------|
| `options_schema() -> OptionsSchema \| None` | `BaseExtractor` | None이 아니면 URL 추가 시 `MediaOptionsDialog` 표시 |
| `CookieAuth` 상속 + `COOKIE_PROMPT` | `base.py` 능력 인터페이스 | 인증 실패 시 `CookiesDialog`를 해당 안내문으로 오픈 |
| `COOKIES_PERSISTENT` | `CookieAuth` | True인 사이트만 설정 창에 인증 관리 항목 노출 |

- `OptionsSchema`/`QualityChoice` — 비디오·음성 품질 선택지와 기본값. 선택 결과는 `task.options`의 `mode`(`MODE_VIDEO`/`MODE_AUDIO`) · `quality`로 저장된다.
- `CookiePrompt` — 다이얼로그 제목/안내 HTML/placeholder/저장 정책 안내/재시도 실패 메시지.
- Pixiv만 예외적으로 site 문자열 분기가 남아 있다 (쿠키가 아니라 OAuth 로그인 창으로 유도해야 하므로).
- 인증 실패 시 **세션 전용 쿠키만 즉시 폐기**한다. 영속 저장 쿠키는 지우지 않는다 — yt-dlp 메시지로는 "만료"와 "계정 권한 없음"을 구분할 수 없어, 멀쩡한 자격 증명을 삭제하는 부작용이 더 크기 때문. 사용자가 새로 입력하면 덮어쓰고, 취소하면 기존 값이 유지된다.

### URL 정규화

`BaseExtractor.canonical_url(url)`가 작업 추가 시점에 호출되어 추적·정렬·플레이리스트 등 부수 쿼리 파라미터를 제거한 표준 URL을 반환한다.
- **YouTube**: `?list=`, `&index=`, `&pp=` 등 → `?v=VIDEO_ID` 만 남김. (`noplaylist=True` 도 함께 설정해 yt-dlp 단계에서 belt-and-suspenders.)
- **Pixiv**: 언어 prefix(`/en/`, `/ja/`) 제거, query string 제거 → `https://www.pixiv.net/artworks/{id}` 또는 `users/{id}`.
- **hitomi**: 다양한 진입 경로(`/reader`, `/galleries`, `/manga`, `/doujinshi`, `/cg`, `/imageset`, `/anime`)와 `#page-` 프래그먼트를 ID만 보존한 `https://hitomi.la/reader/{id}.html` 로 통일.
- 기본 구현은 무수정. 사이트별 override.

이로써 같은 리소스의 다양한 URL이 동일 task로 처리되며, 작업 위젯 클릭 시 깔끔한 URL이 열린다.

## 파일 구조

```
src/
├── config.py               # Config 클래스. config.json 읽기/쓰기.
├── models/task.py          # Task 데이터클래스 + TaskStatus 열거형
├── auth/
│   └── pixiv_oauth.py      # PKCE 생성, 레지스트리 등록/해제, 코드 교환
├── extractors/
│   ├── __init__.py         # ExtractorRegistry / init_registry() / get_extractor() / extractor_for_site()
│   ├── _util.py            # safe_filename(), clean_message(), write_netscape_cookies(), CancelDownload
│   ├── _ytdlp.py           # YtdlpExtractor 베이스 + CookieFileAuthMixin
│   ├── _stream.py          # StreamExtractor 베이스 (HLS/DASH 등 스트리밍 매니페스트)
│   ├── base.py             # BaseExtractor ABC / CookieAuth / OptionsSchema / CookiePrompt
│   ├── pixiv.py            # PixivExtractor (httpx 직접 구현)
│   ├── youtube.py          # YoutubeExtractor (YtdlpExtractor)
│   ├── bilibili.py         # BilibiliExtractor (YtdlpExtractor)
│   ├── hitomi.py           # HitomiExtractor (ltn.gold-usergeneratedcontent.net)
│   ├── m3u8.py             # M3u8Extractor (HLS) — StreamExtractor 서브클래스
│   └── mpd.py              # MpdExtractor (MPEG-DASH) — StreamExtractor 서브클래스
├── core/worker.py          # DownloadWorker(QThread)
└── gui/
    ├── main_window.py              # 메인 윈도우. 큐 관리 담당.
    ├── task_widget.py              # 개별 작업 위젯 (진행률 표시)
    ├── settings_dialog.py          # 설정 다이얼로그
    ├── pixiv_login_dialog.py       # 브라우저 OAuth 로그인 다이얼로그
    ├── media_options_dialog.py     # 다운로드 옵션 선택 (OptionsSchema 기반, 사이트 무관)
    └── cookies_dialog.py           # 쿠키 붙여넣기 (CookiePrompt 기반, 사이트 무관)
```

## 새 익스트랙터 추가

1. `src/extractors/<사이트명>.py` 생성

```python
import threading
from src.config import Config
from src.extractors.base import BaseExtractor, ProgressCallback
from src.models.task import Task

class ExampleExtractor(BaseExtractor):
    site_id = "example"   # task ID prefix와 표시명에 사용

    def __init__(self, config: Config):
        self._config = config

    def can_handle(self, url: str) -> bool:
        return "example.com" in url

    def download(
        self,
        task: Task,
        save_dir: str,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None = None,
    ) -> str:
        task.save_path = ...   # 저장 디렉토리 경로를 반드시 설정할 것
        # stop_event.is_set() 체크는 루프마다 수행
        # 중단 시 InterruptedError raise, 불완전 파일은 직접 삭제
        return "제목"
```

2. `src/extractors/__init__.py`의 `ExtractorRegistry.__init__`에 한 줄 추가

```python
from src.extractors.example import ExampleExtractor

self._extractors = [
    PixivExtractor(config),
    ExampleExtractor(config),
]
```

## yt-dlp 공통 베이스 (`_ytdlp.py`)

yt-dlp로 받는 사이트는 `BaseExtractor`가 아니라 **`YtdlpExtractor`를 상속**한다. `download()` 전체 흐름(2단계 probe/다운로드, 협력적 취소, 진행률 계산, 오류 정리)이 베이스에 있고, 서브클래스는 훅만 채운다.

| 훅 | 기본값 | 용도 |
|----|--------|------|
| `PROBE` | `True` | 메타데이터 추출 단계 수행 여부. 업로더/제목이 필요 없으면 `False` |
| `_extra_opts(task)` | `{}` | 사이트별 yt-dlp 옵션 (probe·다운로드 양쪽에 적용) |
| `_format_spec(task)` | `"best"` | format 선택자 |
| `_dest_dir(task, info, save_dir)` | — | 저장 디렉토리 (필수 구현) |
| `_filename_template(task, info)` | `%(title)s.%(ext)s` | outtmpl의 파일명 부분 |
| `_title(task, info)` | `info["title"]` | 작업 목록 표시 제목 |
| `_stream_labels(task)` | `()` | 순차 스트림 표시명. 비어 있으면 현재 파일 기준 진행률만 표시 |
| `_resolve_url(task)` | `task.url` | 실제로 넘길 URL (짧은 링크 해석 등) |
| `_translate_error(msg)` | `None` | yt-dlp 오류 → 사이트별 예외 변환. 반환값이 있으면 그것을 raise |

`task.save_path` 설정, 디렉토리 생성, `%` escape(`escape_outtmpl`)는 베이스가 처리하므로 서브클래스에서 신경 쓸 필요 없다.

### format_selector 라이프사이클 (함정)

**`YoutubeDL.format_selector`는 `__init__`에서 한 번만 빌드된다.** `process_video_result`가 매번 새로 만드는 것처럼 보이지만 실제로는 init에서 만든 것을 사용한다. 따라서 **`ydl.params["format"] = ...`로 나중에 바꿔도 반영되지 않는다.** 모든 옵션(`format`, `outtmpl`, `extractor_args`, `progress_hooks`, `postprocessors`)은 생성자 인자로 전달해야 한다.

베이스가 probe와 다운로드에 **동일 옵션으로 인스턴스 2개**를 만드는 이유다. probe는 메타데이터만, 다운로드 인스턴스가 재추출하므로 URL 만료(YouTube n-param ~6시간)도 자동 해결된다.

참고: yt-dlp/yt-dlp#12482, #13058, #16350

### 네트워크 재시도 (함정 — 반드시 명시할 것)

**`retries` / `fragment_retries` / `file_access_retries`의 기본값은 yt-dlp CLI 옵션 파서(`options.py`)에만 있다.** `YoutubeDL` 클래스는 이 값을 모른다.

```python
# yt_dlp/downloader/http.py
for retry in RetryManager(self.params.get('retries'), self.report_retry):
# yt_dlp/utils/_utils.py — RetryManager.__init__
self.retries = _retries or 0        # None → 0회
```

Python API에서 지정하지 않으면 **재시도 0회**가 되어, CDN이 전송 도중 연결을 끊으면 (`[download] Got error: N bytes read, M more expected`) 그대로 실패한다. CLI로는 10번 재시도하며 `.part`에서 이어받으므로 성공하는 상황이 API에서는 실패하는, 재현 조건이 헷갈리는 버그가 된다.

베이스가 `_RETRY_OPTS`로 다음을 항상 주입한다: `retries=10`, `fragment_retries=10`, `file_access_retries=3`, 지수 백오프(1→2→4→…→30초), `socket_timeout=20`, `continuedl=True`.

전송 중단·타임아웃 계열 오류는 `_translate_common_error()`가 "재시작하면 이어받는다"는 안내로 바꾼다. 사이트별 `_translate_error()`가 우선한다.

### 협력적 취소

progress_hook에서 `stop_event` 확인 시 `CancelDownload(BaseException)`를 raise한다. `BaseException` 상속이므로 yt-dlp 내부의 `except Exception:`을 통과해 베이스의 `download()`까지 전파되고 거기서 `InterruptedError`로 재포장된다.

### 쿠키 인증 (`CookieFileAuthMixin`)

쿠키 문자열을 임시 Netscape 파일로 기록하고 yt-dlp `cookiefile`로 넘긴다.

- **인메모리 `ydl.cookiejar.set_cookie()` 주입은 금지.** `YoutubeDLCookieJar.load()` 경로를 우회하면 extractor의 로그인 판정(`__Secure-3PAPISID` → `SAPISID` 파생, `_HTTPONLY_PREFIX` 처리 등)이 동작하지 않는다.
- 영속 저장 여부는 `_load_saved_cookies()` / `_save_cookies()` 재정의로 표현. 기본은 세션 전용.
- `COOKIE_DOMAIN`(예: `".bilibili.com"`)이 Netscape 파일의 도메인 필드로 쓰인다.

```python
class ExampleExtractor(CookieFileAuthMixin, YtdlpExtractor):   # 믹스인을 앞에
    COOKIE_DOMAIN = ".example.com"
    COOKIES_PERSISTENT = True
    COOKIE_PROMPT = CookiePrompt(...)

    def _extra_opts(self, task):
        return {"noplaylist": True, **self._cookie_opts()}
```

## Pixiv 인증

- Pixiv API는 OAuth refresh token 방식. 토큰은 `config.pixiv_refresh_token`에서 읽는다.
- `PixivExtractor._ensure_auth()`가 access token 만료 시 자동 갱신한다. threading.Lock으로 보호됨.
- 이미지 다운로드 시 `Referer: https://www.pixiv.net/` 헤더 필수. 없으면 403.
- API client_id/secret은 Pixiv Android 앱의 공개된 값을 사용한다 (`pixiv.py` 상단 상수 참조).

### 인증 정보 저장 방식

- Windows Credential Manager에 저장 (`keyring` 라이브러리, 서비스명 `iroiro-downloader`). 평문 파일에 기록되지 않음.
  - `pixiv_refresh_token` — Pixiv OAuth refresh token
  - `bilibili_cookies` — bilibili cookie 헤더 문자열 (SESSDATA 등)
  - 접근은 `Config._get_secret()` / `Config._set_secret()` 한 쌍을 통해서만.
- YouTube 쿠키는 저장하지 않음 — 임시 파일에만 기록하고 종료 시 삭제. 자세한 내용은 "YouTube 인증" 섹션 참고.
- 나머지 설정(`save_dir`, `max_concurrent`)은 `%APPDATA%\iroiro-downloader\config.json`에 JSON으로 저장.
  - 프로젝트 디렉토리 바깥이므로 git 범위 외.

### 브라우저 로그인 흐름 (`src/auth/pixiv_oauth.py`)

설정 창의 "브라우저로 로그인" 버튼이 `PixivLoginDialog`를 열고 다음 흐름을 수행한다:

1. PKCE `code_verifier` / `code_challenge` 생성
2. HKCU 레지스트리에 `pixiv://` URI 스킴 핸들러 등록 (관리자 권한 불필요)
   - 핸들러: 콜백 URL을 `%TEMP%/iroiro_pixiv_callback.txt`에 기록하는 최소 Python 스크립트
   - HKCU가 HKLM보다 우선 적용되므로 Pixiv 앱 설치 여부와 무관하게 동작
3. 기본 브라우저로 Pixiv OAuth 로그인 페이지 오픈
4. 사용자 로그인 → Pixiv가 `pixiv://account/login?code=XXX` 로 리다이렉트
5. Windows가 핸들러 실행 → 임시 파일 기록
6. `QTimer` (500ms 간격)가 파일 존재를 폴링 → 감지 시 `exchange_code()` 호출
7. 레지스트리·임시 파일 정리 후 `accept()`
8. 타임아웃(3분) 또는 취소 시 `unregister_scheme()` 후 `reject()`

token exchange의 `redirect_uri`는 `https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback`. 실제 브라우저 콜백은 `pixiv://account/login`으로 오지만, 서버가 등록된 값으로 검증하는 것은 이 URL이다. `pixiv://account/login`이나 `https://app-api.pixiv.net/web/v1/meets`는 모두 1508을 반환한다.

## YouTube 다운로드

- **라이브러리**: `yt-dlp` (Python API) + `imageio-ffmpeg` (ffmpeg 바이너리 번들)
- **ffmpeg**: `imageio_ffmpeg.get_ffmpeg_exe()`로 번들 경로 취득. 사용자가 별도 설치 불필요.

### YouTube 인증 (세션 전용 / 반응형)

YouTube 쿠키는 실측상 **30분 이내**로 회전된다(SIDCC + 메인 세션 토큰 양쪽). 따라서 영속 저장은 무의미하며 **세션 전용 + 반응형(필요 시 입력 요청)** 방식을 채택.

- 메커니즘은 `CookieFileAuthMixin` 공통 구현 사용 (`%TEMP%\iroiro_youtube_cookies_*.txt`). `COOKIES_PERSISTENT = False`이므로 `_save_cookies`를 재정의하지 않아 디스크에 남지 않고, `atexit`로 종료 시 파일 삭제.
- `COOKIES_PERSISTENT = False`라서 **설정 창에 YouTube 항목이 나타나지 않는다** — 사전 입력 UI 미제공.
- 다운로드 흐름:
  1. 쿠키 없이 시도 → 인증 필요한 영상이면 `RuntimeError(AUTH_REQUIRED)`
  2. `MainWindow._on_failed` → `TaskStatus.AUTH_FAILED` + `_prompt_cookies(site)` 호출
  3. `CookiesDialog`가 해당 사이트의 `COOKIE_PROMPT` 문구로 오픈 → 사용자가 cookie 헤더 붙여넣기
  4. accept 시 `extractor.set_cookies(...)` → AUTH_FAILED 작업 자동 재시작
- 쿠키 만료 감지 시 `AuthExpiredError("youtube")` → `_on_auth_failed` → 쿠키 폐기 후 동일 다이얼로그 재오픈.
- 동시 실패는 `already_handled` 체크 + `_cookies_dialog is not None` 가드로 한 번만 처리.
- 방금 입력한 쿠키로 또 실패하면 무한 루프 방지를 위해 FAILED로 강등하고 `COOKIE_PROMPT.retry_failed_message`를 오류로 표시 (`_cookies_freshly_set` 집합으로 추적).

### 다운로드 옵션

URL 추가 시 `MediaOptionsDialog`가 `YoutubeExtractor.options_schema()`를 읽어 자동으로 표시된다. 선택 결과는 `task.options`에 저장되어 익스트랙터로 전달된다.

| 옵션 | 선택지 | 기본값 |
|------|--------|--------|
| 종류 | 비디오 / 음성만 | 비디오 |
| 품질 (비디오) | 4K / 1080p / 720p / 480p / 360p | 1080p |
| 품질 (음성) | 최고 품질 / 128kbps / 64kbps | 최고 품질 |

### 포맷 전략

- **비디오**: MP4 컨테이너. H.264+M4A 우선. ffmpeg로 병합.
- **음성만**: M4A(AAC) 추출. yt-dlp `FFmpegExtractAudio` 후처리기 사용.
- `_build_format_spec()`로 정적 선택자 + 풍부한 폴백 체인 생성.
  - 비디오: `bestvideo[height<=Q][ext=mp4]+bestaudio[ext=m4a]` → `bestvideo[height<=Q]+bestaudio` → `bestvideo+bestaudio` → `best`
  - 오디오: `bestaudio[abr<=Q][ext=m4a]` → `bestaudio[abr<=Q]` → `bestaudio[ext=m4a]` → `bestaudio`

### JavaScript Runtime 필수 (yt-dlp 2025+)

YouTube가 player script를 통한 `signature` / `n` 파라미터 챌린지를 적용하면서, yt-dlp 2025+ 부터는 외부 JS runtime이 필요해졌다.

- **필수 시스템**: Node.js 22.6+ (Deno/Bun/QuickJS도 가능)
- **필수 pip 패키지**: `yt-dlp[default]` — `yt-dlp-ejs`(JS 솔버 스크립트) 자동 설치
- **`js_runtimes={"node": {}}`** 옵션 명시 — Deno는 자동 감지되지만 Node는 명시 필요

설정 누락 시 증상:
```
WARNING: [youtube] BGlCa2jBNQc: Signature solving failed: Some formats may be missing
WARNING: [youtube] BGlCa2jBNQc: n challenge solving failed: Some formats may be missing
WARNING: Only images are available for download
ERROR: Requested format is not available
```
스토리보드 이미지만 남고 모든 미디어 포맷이 사라진다. "format not available"은 실제로는 "JS 챌린지 미해결로 포맷 0개"의 다른 표현.

참고: [yt-dlp wiki/EJS](https://github.com/yt-dlp/yt-dlp/wiki/EJS)

### SABR / PO Token 대응 (2026 필수)

YouTube의 `web`/`ios` player_client는 PO Token 없이는 **SABR-only formats**만 반환한다. yt-dlp는 이런 포맷을 format 선택자 단계에서 필터링하므로, 결과적으로 **"Requested format is not available"** 에러가 발생한다 (포맷이 listed에는 보여도 다운로드 불가).

해결: `extractor_args.player_client`로 PO Token 불필요한 client들을 명시한다.

```python
"extractor_args": {"youtube": {"player_client": ["default", "web_safari", "android_vr"]}}
```

- `default`: yt-dlp가 자동 선택 (PO Token 있으면 사용)
- `web_safari`: HLS 반환, GVS PO Token 불필요
- `android_vr`: "made for kids" 검사 생략, PO Token 불필요

여러 client를 나열하면 yt-dlp가 각 client에서 포맷 목록을 받아 합친다. 이로써 다운로드 가능한 포맷 발견 확률이 크게 올라간다.

참고: [PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide). format_selector 라이프사이클·협력적 취소는 "yt-dlp 공통 베이스" 섹션 참고.

### 저장 경로

`{save_dir}/YouTube/{업로더명}/{제목}.{ext}`. `task.save_path`는 업로더 폴더로 설정.

### 작업 ID

- 단일 영상: `youtube-video-{11자리 video_id}`

### URL 처리 범위

`can_handle()`은 `_VIDEO_RE`로 단일 영상 URL(`watch?v=`, `shorts/`, `youtu.be/`)만 매칭한다. 플레이리스트/채널 URL은 의도적으로 거부한다 — 코드는 단일 영상 메타데이터만 처리하므로.

## bilibili 다운로드

yt-dlp의 `BiliBiliIE` 사용. 비디오/음성만 선택은 YouTube와 동일하게 `task.options`의 `mode`·`quality`로 전달된다.

### URL 처리 범위

`can_handle()`이 받는 것은 **단일 영상 하나**뿐이다.

| 형태 | 예 | task ID |
|------|-----|---------|
| BV | `bilibili.com/video/BV1xx411c7mD` | `bilibili-video-BV1xx411c7mD` |
| av (레거시) | `bilibili.com/video/av170001` | `bilibili-video-av170001` |
| 분P | `.../BV1xx411c7mD?p=4` | `bilibili-video-BV1xx411c7mD-p4` |
| 짧은 링크 | `b23.tv/AbCd12` | `bilibili-short-AbCd12` |

의도적으로 제외: 番剧(`/bangumi/play/...`), UP주 전체(`space.bilibili.com/...`), 라이브. 재생목록·유료/지역 판정이 단일 Task 모델과 맞지 않는다.

- **BV ID는 대소문자를 구분한다.** 정규식은 `IGNORECASE`로 매칭하되 캡처한 문자열은 원본 그대로 사용할 것.
- av ↔ BV 상호 변환은 하지 않는다. 같은 영상을 av 링크와 BV 링크로 각각 추가하면 별개 작업이 된다 (알려진 한계).

### canonical_url

`spm_id_from`, `vd_source`, `t`, `share_*` 등 추적 파라미터를 제거한다. 단 **`?p=N`(분P)은 보존**한다 — YouTube의 `list=`와 달리 어느 파트를 받을지 결정하는 의미 있는 값이다.

`p=1`은 제거한다. `noplaylist=True` 상태에서 "p 없음"과 "p=1"은 yt-dlp가 같은 파트로 처리하므로(`part_id = part_id or 1`) 동일 작업으로 취급해야 중복 감지가 맞는다.

### 분P (anthology)

한 BV가 여러 편을 담는 구조. yt-dlp는 이를 재생목록으로 확장하려 하므로 `noplaylist=True`로 막고 URL의 `?p=N`(없으면 1편)만 받는다. 1 Task = 1 파일 모델을 유지하기 위한 선택.

### b23.tv 짧은 링크

실제 ID는 리다이렉트를 따라가야만 알 수 있는데 URL 추가 시점(메인 스레드)에서 네트워크를 쓸 수 없다. 따라서 **task ID는 짧은 코드 기준**으로 만들고, 실제 해석은 워커 스레드의 `_resolve_url()`에서 httpx로 수행한다(본문은 읽지 않고 최종 URL만 확인). 해석 결과가 영상 URL이 아니면 명시적으로 실패시킨다.

### 포맷

bilibili는 DASH라 영상/음성이 항상 분리 제공된다.

- **비디오**: `bestvideo[height<=Q][vcodec^=avc1]+bestaudio[ext=m4a]` 우선(플레이어 호환성) → mp4 → 무조건 폴백 순으로 체인. ffmpeg 병합.
- **음성만**: `bestaudio[abr<=Q]` 계열 + `FFmpegExtractAudio(m4a)`. 원본이 이미 m4a(AAC)라 사실상 재인코딩 없이 통과한다.
- 실측 오디오 트랙은 **64k(30216) / 132k(30232) / 192k(30280)** 세 종류. 옵션의 `value`는 `abr<=` 임계값이라 라벨의 비트레이트보다 약간 높게 잡혀 있다(`140`, `70`). YouTube의 128/64를 그대로 쓰면 132k를 건너뛰고 64k가 잡힌다.

### PCDN 노드 대응

bilibili는 재생 URL을 `*.mcdn.bilivideo.cn` · `*.szbdyd.com` 같은 **PCDN(개인 회선 P2P CDN)** 노드로 배정하는 일이 잦다. 이 노드는 수십 KB/s로 느리고 전송 도중 연결을 끊는다 — 증상은 `N bytes read, M more expected`.

대응으로 `http_chunk_size = 10MB`를 적용한다. Range로 잘라 받으므로 연결 하나가 오래 유지되지 않고, 끊겨도 그 조각만 다시 받는다. 여기에 베이스의 재시도(10회 + 이어받기)가 겹쳐 대부분 복구된다.

호스트를 `upos-*.bilivideo.com`으로 치환하면 더 빠르지만, bilibili의 CDN 배정을 우회하는 방식이라 채택하지 않았다 (yt-dlp도 코어 반영을 거부하고 플러그인 영역으로 분류). 참고: yt-dlp/yt-dlp#12421, #14498

### 인증

`SESSDATA` 쿠키. YouTube와 달리 수명이 길어 **`COOKIES_PERSISTENT = True`** — Credential Manager(`bilibili_cookies`)에 저장하고 기동 시 복원한다. 따라서 설정 창에 "Bilibili 인증" 항목(쿠키 입력/삭제)이 노출된다.

로그인 없이도 오디오 트랙은 대체로 받히지만, 720p 이상·대회원 전용 영상은 쿠키가 필요하다.

### 오류 변환 (`_translate_error`)

| 조건 | 처리 |
|------|------|
| `premium member` / `registered users` / `log in` 등 | 쿠키 있으면 `AuthExpiredError`, 없으면 `AUTH_REQUIRED` |
| `blocked by server` 또는 `\b412\b` | "일시적 차단(412) — 잠시 후 재시도 또는 yt-dlp 업데이트" 안내 |
| geo restricted | 지역 제한 안내 |

412는 2026년 들어 자주 보고되는 증상이라 별도 안내를 붙였다. 영상 ID 안의 숫자에 오탐하지 않도록 단어 경계(`\b412\b`)로 검사한다.

### 저장 경로

`{save_dir}/Bilibili/{UP주명}/{제목}.{ext}`. `task.save_path`는 UP주 폴더.

## 스트리밍 매니페스트 (HLS / DASH) 다운로드

직접적인 매니페스트 URL을 yt-dlp의 generic extractor + 번들 ffmpeg로 처리한다. (다른 사이트의 비디오 페이지가 아닌 raw playlist/manifest URL만 매칭.)

`StreamExtractor`는 `YtdlpExtractor`의 서브클래스이며 `PROBE = False`(매니페스트에는 업로더/제목 메타데이터가 없어 probe 불필요)로 동작한다. 각 포맷은 다시 얇은 서브클래스:
- `M3u8Extractor` — `.m3u8` URL (HLS), folder `M3U8/`
- `MpdExtractor` — `.mpd` URL (MPEG-DASH), folder `MPD/`

공통 동작:
- **매칭 규칙**: URL에 해당 확장자(쿼리/프래그먼트 직전)가 포함되면 매칭. 대소문자 무시.
- **task ID**: URL의 SHA-256 16자리 hex (URL이 서명 토큰 포함이라 그대로 해싱).
- **canonical_url**: 무수정 — 쿼리 파라미터가 보통 서명 토큰이라 임의 제거 불가.
- **저장 경로**: `{save_dir}/{FOLDER}/{URL stem}_{8자리 hash}.mp4` — 같은 URL은 같은 파일명.
- **format**: master playlist/manifest에서 best variant 선택 (yt-dlp가 자동).
- **downloader**: HLS는 `hls_prefer_native=False`로 ffmpeg 사용. DASH는 yt-dlp의 `dashsegments` 다운로더(기본). 둘 다 ffmpeg로 muxing.
- **progress**: 세그먼트 단위라 전체 진행률 추정이 정확하지 않다. 현재 다운로드 중인 파일 기준 표시.

## hitomi.la 다운로드

httpx 기반 직접 구현 — yt-dlp의 hitomi extractor는 현행 CDN/포맷 전환을 따라가지 못해 사용하지 않는다.

### 메타데이터 / CDN 엔드포인트

- **갤러리 메타데이터**: `https://ltn.gold-usergeneratedcontent.net/galleries/{id}.js`
  - 응답은 `var galleryinfo = {...};` 형태의 JS 한 줄. `var galleryinfo = ` prefix를 떼고 끝의 `;`/공백을 정리한 뒤 `json.loads`.
  - 핵심 필드: `id`, `title`, `artists` / `groups`, `files`[{`name`, `hash`(sha256), `hasavif` / `hasjxl` / `haswebp`, `width`, `height`}].
- **URL 생성 로직**: `https://ltn.gold-usergeneratedcontent.net/gg.js`
  - `gg.b`(문자열, 예: `'1779015602/'`) · `gg.m(g)`(switch-case, 기본 1) · `gg.s(hash)`(정수) 정의.
  - 다운로드 시점마다 다시 받아 캐싱하지 않는다 — 운영 측에서 수시로 바뀜.

### 이미지 URL 공식

```
g = int(hash[-1] + hash[-3:-1], 16)            # s 함수: /(..)(.)$/ 마지막 1자 + 그 앞 2자
subdomain = f"a{1 + gg.m(g)}"                  # a1 또는 a2
url = f"https://{subdomain}.gold-usergeneratedcontent.net/{gg.b}{g}/{hash}.{ext}"
```

- 확장자 선택 우선순위: `hasavif` → `hasjxl` → `haswebp` → `name`의 원본 확장자. CDN은 원본 jpg를 그대로 서빙하지 않으며 메타데이터가 가리키는 포맷만 200 응답.
- 모든 요청에 `Referer: https://hitomi.la/` 필수. 누락 시 CDN이 4xx 반환.

### gg.js 파싱 — 변동성 대응

`var o = N;` 으로 시작하는 기본값과 `case A: case B: ... o = X; break;` 형태의 case 묶음을 일반화 정규식으로 추출(`_GG_CASE_BLOCK_RE` + `_GG_CASE_N_RE`). 현재 구조는 “기본 1, 한 블록만 0으로 강등”이지만 다중 블록·다른 값으로 바뀌어도 동작하도록 작성.

### 저장 경로

`{save_dir}/Hitomi/{작가 또는 그룹명}/{id}_{title}/001.{ext}` 형태. 작가/그룹이 모두 없으면 `unknown`. 제목은 80자에서 자른 뒤 `safe_filename`으로 정리(절단 경계의 trailing space/dot 제거를 위해 자르기 → sanitize 순서).

### 작업 ID / canonical URL

- `make_task_id`: `hitomi-gallery-{id}`.
- `canonical_url`: 진입 경로(/reader, /galleries, /manga, /doujinshi 등)와 `#page-` 프래그먼트를 무시하고 `https://hitomi.la/reader/{id}.html` 로 통일.

### URL 매칭

`hitomi\.la/[^?#]*?(\d+)\.html` — 경로의 마지막 `.html` 직전에 위치한 숫자 시퀀스를 ID로 간주. 쿼리·프래그먼트 안의 숫자는 무시.

## 주요 설계 결정

- Pixiv는 yt-dlp 지원이 불완전(이미지/만화 미지원)하므로 httpx로 직접 구현. YouTube·bilibili는 yt-dlp 사용. hitomi.la 역시 yt-dlp가 현행 CDN/AVIF 전환을 따라가지 못해 httpx 직접 구현.
- yt-dlp 기반 사이트가 셋 이상이 되면서 공통 흐름을 `YtdlpExtractor`로 올렸다. 사이트별 코드는 훅 구현만 남기고, 중복된 진행률·오류·취소 처리를 다시 만들지 말 것.
- GUI는 익스트랙터의 선언(`options_schema()`, `CookieAuth`)만 보고 동작한다. 새 사이트를 추가할 때 GUI에 `isinstance`/site 문자열 분기를 넣는 것은 회귀로 간주한다.
- 익스트랙터 인스턴스는 레지스트리에서 앱 전체에 하나만 존재(싱글턴). 여러 워커가 공유하므로 상태를 task 객체에 저장하고 익스트랙터 자체는 무상태에 가깝게 유지할 것.
- `task.save_path`는 익스트랙터가 dest_dir 확정 시 즉시 설정한다. 삭제 팝업의 "파일도 삭제"가 이 경로를 사용하므로 설정을 누락하면 파일이 삭제되지 않는다.
- `task.options`는 익스트랙터별 추가 파라미터 전달에 사용. Pixiv는 사용 안 함. YouTube는 `mode`, `quality`를 담는다.
