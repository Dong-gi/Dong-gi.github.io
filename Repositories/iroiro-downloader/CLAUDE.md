# CLAUDE.md

## 프로젝트 개요

개인 사용 목적의 미디어 다운로더. Python 3.12+ / PySide6 GUI / httpx HTTP 클라이언트.
Pixiv, YouTube를 지원하며, 새 사이트를 쉽게 추가할 수 있는 익스트랙터 구조로 설계되어 있다.

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

`ExtractorRegistry` 클래스가 익스트랙터 인스턴스를 보관한다. `init_registry(config)`로 단일 레지스트리를 초기화하고, 모듈-레벨 헬퍼 `get_extractor(url)` / `find_extractor(cls)`로 조회한다.
- `get_extractor(url)` — `can_handle()`이 True인 첫 번째 익스트랙터
- `find_extractor(cls)` — 클래스 기반 조회 (URL 매칭 거치지 않음, AUTH_FAILED 처리 등에 사용)

각 익스트랙터는 `site_id` 클래스 변수를 가진다 (예: `"pixiv"`, `"youtube"`). `make_task_id()`가 이 값을 task ID prefix로 사용하며, `site_id_from_task_id(task_id)` 헬퍼로 역추출 가능.

### URL 정규화

`BaseExtractor.canonical_url(url)`가 작업 추가 시점에 호출되어 추적·정렬·플레이리스트 등 부수 쿼리 파라미터를 제거한 표준 URL을 반환한다.
- **YouTube**: `?list=`, `&index=`, `&pp=` 등 → `?v=VIDEO_ID` 만 남김. (`noplaylist=True` 도 함께 설정해 yt-dlp 단계에서 belt-and-suspenders.)
- **Pixiv**: 언어 prefix(`/en/`, `/ja/`) 제거, query string 제거 → `https://www.pixiv.net/artworks/{id}` 또는 `users/{id}`.
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
│   ├── __init__.py         # ExtractorRegistry / init_registry() / get_extractor() / find_extractor()
│   ├── _util.py            # safe_filename() 등 공통 유틸
│   ├── base.py             # BaseExtractor ABC + site_id_from_task_id()
│   ├── pixiv.py            # PixivExtractor
│   └── youtube.py          # YoutubeExtractor (yt-dlp 기반)
├── core/worker.py          # DownloadWorker(QThread)
└── gui/
    ├── main_window.py              # 메인 윈도우. 큐 관리 담당.
    ├── task_widget.py              # 개별 작업 위젯 (진행률 표시)
    ├── settings_dialog.py          # 설정 다이얼로그
    ├── pixiv_login_dialog.py       # 브라우저 OAuth 로그인 다이얼로그
    ├── youtube_options_dialog.py   # YouTube 다운로드 옵션 선택 다이얼로그
    └── youtube_cookies_dialog.py   # YouTube 쿠키 붙여넣기 다이얼로그
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

## Pixiv 인증

- Pixiv API는 OAuth refresh token 방식. 토큰은 `config.pixiv_refresh_token`에서 읽는다.
- `PixivExtractor._ensure_auth()`가 access token 만료 시 자동 갱신한다. threading.Lock으로 보호됨.
- 이미지 다운로드 시 `Referer: https://www.pixiv.net/` 헤더 필수. 없으면 403.
- API client_id/secret은 Pixiv Android 앱의 공개된 값을 사용한다 (`pixiv.py` 상단 상수 참조).

### 인증 정보 저장 방식

- `pixiv_refresh_token`은 Windows Credential Manager에 저장 (`keyring` 라이브러리 사용).
  - 서비스명: `iroiro-downloader`, 사용자명: `pixiv_refresh_token`
  - 평문 파일에 기록되지 않음
- YouTube 쿠키는 저장하지 않음 — `YoutubeExtractor._cookies_jar`에 메모리 전용 보관. 자세한 내용은 "YouTube 인증" 섹션 참고.
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

- 사용자 입력 쿠키 문자열은 임시 Netscape 포맷 파일(`%TEMP%\iroiro_yt_cookies_*.txt`)에 기록 → yt-dlp의 `cookiefile` 옵션으로 전달.
  - **인메모리 `ydl.cookiejar.set_cookie()` 주입은 사용 금지**. 이 경로는 yt-dlp의 `YoutubeDLCookieJar.load()`를 우회하므로 YouTube extractor의 `_has_auth_cookies` 검사(`__Secure-3PAPISID` → `SAPISID` 자동 파생, `_HTTPONLY_PREFIX` 처리 등)가 동작하지 않아 로그인 상태가 인식되지 않는다.
  - 파일은 `set_cookies()` 호출 시 새로 작성·이전 파일 삭제, `atexit`로 프로그램 종료 시 정리.
- 설정 창에는 YouTube 섹션 없음 — 사전 입력 UI 미제공.
- 다운로드 흐름:
  1. 쿠키 없이 시도 → 인증 필요한 영상이면 `RuntimeError("AUTH_REQUIRED")`
  2. `MainWindow._on_failed` → `TaskStatus.AUTH_FAILED` + `_prompt_youtube_cookies()` 호출
  3. `YoutubeCookiesDialog` 오픈 → 사용자가 cookie 헤더 붙여넣기
  4. accept 시 `extractor.set_cookies(...)` → AUTH_FAILED 작업 자동 재시작
- 쿠키 만료 감지 시 `AuthExpiredError("youtube")` → `_on_auth_failed` → 쿠키 파일 삭제 후 동일 다이얼로그 재오픈.
- 동시 실패는 `already_handled` 체크 + `_yt_cookies_dialog is not None` 가드로 한 번만 처리.

### 다운로드 옵션

URL 추가 시 `YoutubeOptionsDialog`가 자동으로 표시된다. 선택 결과는 `task.options`에 저장되어 익스트랙터로 전달된다.

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

### format_selector 라이프사이클 (함정)

**`YoutubeDL.format_selector`는 `__init__`에서 한 번만 빌드된다.** `process_video_result`는 매번 새 selector를 만드는 것처럼 보이지만 실제로는 init에서 만든 것을 사용한다. 따라서 **`ydl.params["format"] = "..."`로 나중에 바꿔도 반영되지 않는다**. 모든 옵션(`format`, `outtmpl`, `extractor_args`, `progress_hooks`, `postprocessors`)은 반드시 생성자 인자로 전달해야 한다.

이 때문에 두 단계 흐름(probe → dl)은 **동일한 `ydl_opts`로 두 개의 인스턴스**를 사용한다. probe는 메타데이터(uploader/title)만 가져오고, dl이 실제 다운로드 수행. URL 만료(YouTube n-param ~6시간)도 dl 단계 재추출로 자동 해결.

참고: yt-dlp/yt-dlp#12482, #13058, #16350, [PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide)

### 협력적 취소

progress_hook에서 `stop_event` 확인 시 `_CancelDownload(BaseException)`를 raise한다. `BaseException` 상속이므로 yt-dlp 내부의 `except Exception:` 블록을 통과하여 `download()` 레이어까지 전파되고, `InterruptedError`로 재포장된다.

### 저장 경로

`{save_dir}/YouTube/{업로더명}/{제목}.{ext}`. `task.save_path`는 업로더 폴더로 설정.

### 작업 ID

- 단일 영상: `youtube-video-{11자리 video_id}`

### URL 처리 범위

`can_handle()`은 `_VIDEO_RE`로 단일 영상 URL(`watch?v=`, `shorts/`, `youtu.be/`)만 매칭한다. 플레이리스트/채널 URL은 의도적으로 거부한다 — 코드는 단일 영상 메타데이터만 처리하므로.

## 주요 설계 결정

- Pixiv는 yt-dlp 지원이 불완전(이미지/만화 미지원)하므로 httpx로 직접 구현. YouTube는 yt-dlp 사용.
- 익스트랙터 인스턴스는 레지스트리에서 앱 전체에 하나만 존재(싱글턴). 여러 워커가 공유하므로 상태를 task 객체에 저장하고 익스트랙터 자체는 무상태에 가깝게 유지할 것.
- `task.save_path`는 익스트랙터가 dest_dir 확정 시 즉시 설정한다. 삭제 팝업의 "파일도 삭제"가 이 경로를 사용하므로 설정을 누락하면 파일이 삭제되지 않는다.
- `task.options`는 익스트랙터별 추가 파라미터 전달에 사용. Pixiv는 사용 안 함. YouTube는 `mode`, `quality`를 담는다.
