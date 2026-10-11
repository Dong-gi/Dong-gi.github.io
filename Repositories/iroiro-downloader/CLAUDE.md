# CLAUDE.md

## 프로젝트 개요

개인 사용 목적의 YouTube 다운로더. Python 3.12+ / PySide6 GUI / yt-dlp.

**YouTube만 받는다.** pixiv·hitomi는 안드로이드 브라우저(`Repositories/iroiro-browser`)로 옮겼고 bilibili·M3U8·MPD는 지웠다(2026-10-11, 사용자 결정 — 지운 코드는 git 이력에 있다). 익스트랙터 구조는 그대로 두어 사이트를 다시 붙일 수 있다. 지운 기능이 사용자 기기에 남긴 것은 기동 때 치운다("옛 흔적 정리" 절).

## 실행

```powershell
pip install -r requirements.txt
python -m src.pot_provider   # YouTube PO Token 서버 빌드 (최초 1회, 플러그인 버전을 올릴 때마다)
python main.py
```

## 아키텍처

### 스레드 모델

- **메인 스레드**: Qt 이벤트 루프 전담. 위젯 수정은 반드시 메인 스레드에서만.
- **워커 스레드** (`DownloadWorker`): 사이트별 익스트랙터의 `download()`를 실행. 네트워크 I/O 전담.
- 스레드 간 통신은 Qt Signal/Slot으로만 수행. 직접 위젯 접근 금지.

### 협력적 취소 (Cooperative Cancellation)

`DownloadWorker`는 `threading.Event`인 `_stop_event`를 보유한다.
`worker.pause()`를 호출하면 event가 set되고, 익스트랙터는 진행 콜백마다 이를 확인해 `InterruptedError`로 멈춘다.
워커는 `InterruptedError`를 잡아 `task_paused` 시그널을 emit한다.
`terminate()`는 사용하지 않는다.

```
worker.pause()
  → stop_event.set()
  → yt-dlp progress_hook에서 CancelDownload → YtdlpExtractor.download()가 InterruptedError로 재포장
  → 불완전 파일은 yt-dlp에 맡긴다 (.part가 남으면 재개 때 continuedl로 이어받는다)
  → DownloadWorker.run()이 task_paused emit
  → MainWindow._on_paused() → (삭제 요청으로 멈춘 경우) 삭제 팝업 표시
```

### 다운로드 삭제 플로우

- `DOWNLOADING` 상태에서 삭제 요청 시: `pause()` → `PAUSED`로 전환 → 워커 정지 후 팝업
- `PAUSED` 상태에서 삭제 요청 시: 즉시 팝업
- `PENDING / DONE / FAILED / AUTH_FAILED`: 팝업 없이 즉시 제거
- 팝업 선택지: **파일도 삭제** (`shutil.rmtree(task.save_path)`) / **작업만 삭제** / **취소** (PENDING으로 복귀 후 재개)

### 작업 ID

`BaseExtractor.make_task_id(url)`로 URL에서 결정론적 ID를 생성한다. 같은 URL은 항상 같은 ID → 중복 감지에 사용.
- YouTube: `youtube-video-{11자리 video_id}`
- 기본(미구현 사이트): UUID

중복 ID가 이미 큐에 있으면 `_show_duplicate_dialog(task)`를 호출한다.
- FAILED / AUTH_FAILED / PAUSED: **재시작** 버튼 (status → PENDING, progress 초기화)
- DONE: **폴더 열기** 버튼 (`os.startfile(task.save_path)`)
- 항상: **닫기** 버튼

### 세션 복원

`closeEvent`에서 실행 중인 워커를 `pause()` + `wait(5000)`으로 중단하고 `session.json`에 저장한다.
`__init__` 마지막에 `_load_session()`으로 복원한다.
- 저장 경로: `%APPDATA%\iroiro-downloader\session.json`
- DOWNLOADING 상태로 저장된 작업은 `Task.from_dict()`에서 PENDING으로 변환되어 재시작된다.
- 필드가 빠졌거나 값이 맞지 않는 항목은 건너뛴다.
- **처리할 익스트랙터가 없는 작업**(지운 사이트가 남긴 것): `완료`는 그대로 두고, 나머지는 `실패`("지원하지 않는 URL입니다.")로 옮긴 뒤 상태 표시줄에 한 번 알린다. 지우지 않는 것은 주소를 보고 다른 곳에서 받을 수 있게 하려는 것이다. 이미 옮긴 작업은 다음 기동 때 세지 않으므로 알림이 되풀이되지 않는다.
- `_start()`도 익스트랙터가 없으면 워커를 띄우지 않고 같은 `실패`로 둔다(그 작업의 재시작 단추 대비).

### 익스트랙터 레지스트리

`ExtractorRegistry` 클래스가 익스트랙터 인스턴스를 보관한다. `init_registry(config)`로 단일 레지스트리를 초기화하고, 모듈-레벨 헬퍼로 조회한다.
- `get_extractor(url)` — `can_handle()`이 True인 첫 번째 익스트랙터. 없으면 None → GUI가 "지원하지 않는 URL입니다."
- `extractor_for_site(site_id)` — site_id 기반 조회 (task ID에서 역추출한 값으로 인증 처리 시 사용)

각 익스트랙터는 `site_id` 클래스 변수를 가진다 (지금은 `"youtube"` 하나). `make_task_id()`가 이 값을 task ID prefix로 사용하며, `site_id_from_task_id(task_id)` 헬퍼로 역추출 가능.

### GUI ↔ 익스트랙터 계약 (사이트 하드코딩 금지)

GUI는 **사이트별 분기(isinstance/site 문자열 비교)를 하지 않는다.** 익스트랙터가 자신의 능력을 선언하고 GUI는 그것만 읽는다. 사이트가 하나뿐이어도 이 구조를 지킨다 — 사이트를 다시 붙여도 GUI 코드는 그대로다.

| 선언 | 위치 | GUI 동작 |
|------|------|----------|
| `options_schema() -> OptionsSchema \| None` | `BaseExtractor` | None이 아니면 URL 추가 시 `MediaOptionsDialog` 표시 |
| `CookieAuth` 상속 + `COOKIE_PROMPT` | `base.py` 능력 인터페이스 | 인증 실패 시 `CookiesDialog`를 해당 안내문으로 오픈 |

- `OptionsSchema`/`QualityChoice` — 비디오·음성 품질 선택지와 기본값. 선택 결과는 `task.options`의 `mode`(`MODE_VIDEO`/`MODE_AUDIO`) · `quality`로 저장된다.
- `CookiePrompt` — 다이얼로그 제목/안내 HTML/placeholder/저장 정책 안내/재시도 실패 메시지.
- 쿠키는 **세션 전용**뿐이다. 인증 실패 시 GUI가 쿠키를 즉시 폐기하고 다시 묻는다.
- 쿠키를 영속 저장하던 능력(`COOKIES_PERSISTENT`, `CookieFileAuthMixin`의 `_load_saved_cookies`/`_save_cookies` 훅, 설정 창 인증 항목)은 그것을 쓰던 bilibili와 함께 지웠다. 다시 붙일 때는 git 이력에서 함께 되살리고 "인증 실패 시 영속 쿠키는 지우지 않는다"는 규칙도 되살린다 — yt-dlp 메시지로는 "만료"와 "계정 권한 없음"을 구분할 수 없어, 멀쩡한 자격 증명을 지우는 부작용이 더 크다.

### URL 정규화

`BaseExtractor.canonical_url(url)`가 작업 추가 시점에 호출되어 추적·정렬·플레이리스트 등 부수 쿼리 파라미터를 제거한 표준 URL을 반환한다.
- **YouTube**: `?list=`, `&index=`, `&pp=` 등 → `?v=VIDEO_ID` 만 남김. (`noplaylist=True` 도 함께 설정해 yt-dlp 단계에서 belt-and-suspenders.)
- 기본 구현은 무수정. 사이트별 override.

이로써 같은 리소스의 다양한 URL이 동일 task로 처리되며, 작업 위젯 클릭 시 깔끔한 URL이 열린다.

## 파일 구조

```
src/
├── config.py               # Config 클래스. config.json 읽기/쓰기 (아는 키만 읽는다)
├── legacy_cleanup.py       # 옛 흔적 정리 — 지운 기능이 기기에 남긴 등록·임시 파일·비밀 (기동 때)
├── pot_provider.py         # YouTube PO Token 서버 위치 판정·설치 (`python -m src.pot_provider`)
├── models/task.py          # Task 데이터클래스 + TaskStatus 열거형
├── extractors/
│   ├── __init__.py         # ExtractorRegistry / init_registry() / get_extractor() / extractor_for_site()
│   ├── _util.py            # safe_filename(), clean_message(), escape_outtmpl(), build_netscape_cookies(), CancelDownload
│   ├── _ytdlp.py           # YtdlpExtractor 베이스 + CookieFileAuthMixin
│   ├── base.py             # BaseExtractor ABC / CookieAuth / OptionsSchema / CookiePrompt
│   └── youtube.py          # YoutubeExtractor (YtdlpExtractor)
├── core/worker.py          # DownloadWorker(QThread)
└── gui/
    ├── main_window.py              # 메인 윈도우. 큐 관리 담당.
    ├── task_widget.py              # 개별 작업 위젯 (진행률 표시)
    ├── settings_dialog.py          # 설정 다이얼로그 (저장 위치·최대 동시 다운로드)
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
    YoutubeExtractor(config),
    ExampleExtractor(config),
]
```

지운 사이트(pixiv·hitomi·bilibili·M3U8·MPD)의 구현은 git 이력에 있다. pixiv를 되살리면 `legacy_cleanup.py`가 그 `pixiv://` 등록·keyring 비밀을 지우게 되므로 정리 쪽도 함께 손볼 것.

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

쿠키 문자열을 **인메모리 Netscape 스트림**으로 만들어 yt-dlp `cookiefile`로 넘긴다.
디스크에 파일을 만들지 않는다.

yt-dlp는 `cookiefile`이 경로가 아니면(`is_path_like`가 거짓) 그 객체를 `YoutubeDLCookieJar`에
그대로 넘긴다(`yt_dlp/cookies.py`의 `open()`). 이 성질을 이용해 파일 없이 공식 처리 경로를 탄다.

**`_RewindingCookieStream`이 필요한 이유 (함정 둘, 둘 다 실측).** 맨 `StringIO`로는 깨진다.

- **두 번 읽힌다.** `download()`가 probe와 다운로드에 같은 옵션 dict를 넘기므로 같은 스트림이
  두 번 적재되고, 두 번째는 EOF에서 시작해
  `LoadError: does not look like a Netscape format cookies file` 가 난다. → `__iter__`에서 되감는다.
- **yt-dlp가 되쓴다.** `YoutubeDL.__exit__` → `save_cookies()` 가 `cookiefile`이 None이 아니면
  무조건 `jar.save()` 를 부르고, 그 경로의 `open(write=True)` 는 `truncate(0)` 만 하고 위치는
  되돌리지 않아 내용이 NUL로 채워진다. → `truncate`에서 위치까지 되돌린다.

`_cookie_opts()`는 호출마다 새 스트림을 만든다. 익스트랙터는 레지스트리 싱글턴이라 동시
다운로드가 인스턴스를 공유하므로, 스트림을 재사용하면 워커끼리 같은 객체의 위치를 건드린다.

이 방식은 yt-dlp 내부 동작(file-like `cookiefile`)에 기대고 있고 `requirements.txt`의
`yt-dlp`는 상한이 없다. yt-dlp를 올린 뒤 인증이 조용히 깨지면 여기를 먼저 볼 것.

- **인메모리 `ydl.cookiejar.set_cookie()` 주입은 금지.** `YoutubeDLCookieJar.load()` 경로를 우회하면 extractor의 로그인 판정(`__Secure-3PAPISID` → `SAPISID` 파생, `_HTTPONLY_PREFIX` 처리 등)이 동작하지 않는다.
- 쿠키는 세션 전용이다 — 어디에도 저장하지 않는다 (영속 저장은 "GUI ↔ 익스트랙터 계약" 참고).
- `COOKIE_DOMAIN`(예: `".youtube.com"`)이 Netscape 파일의 도메인 필드로 쓰인다.

```python
class ExampleExtractor(CookieFileAuthMixin, YtdlpExtractor):   # 믹스인을 앞에
    COOKIE_DOMAIN = ".example.com"
    COOKIE_PROMPT = CookiePrompt(...)

    def _extra_opts(self, task):
        return {"noplaylist": True, **self._cookie_opts()}
```

## 설정·인증 정보 저장

- 설정(`save_dir`, `max_concurrent`)은 `%APPDATA%\iroiro-downloader\config.json`에 JSON으로 저장. 프로젝트 디렉토리 바깥이므로 git 범위 외.
  - 읽을 때 아는 키만 받는다. 지운 기능이 남긴 키나 모르는 키는 무시되고 다음 저장 때 파일에서도 빠진다.
- YouTube 쿠키는 저장하지 않음 — 프로세스 메모리에만 두고 디스크에 쓰지 않는다. 자세한 내용은 "YouTube 인증" 섹션 참고.
- 자격 증명 관리자(keyring)에는 아무것도 쓰지 않는다. 예전 판이 쓴 Pixiv refresh token·bilibili 쿠키는 "옛 흔적 정리"가 지운다.

## 옛 흔적 정리 (`src/legacy_cleanup.py`)

`main()`이 맨 먼저 `cleanup_legacy_traces()`를 부른다. 지운 기능이 사용자 기기에 남겼을 수 있는 것을 기동 때마다 지운다. 모두 "있으면 지우고 없으면 조용히"이고, 무엇이 실패해도 기동을 막지 않는다.

| 대상 | 왜 남았나 | 지우는 조건 |
|------|-----------|-------------|
| HKCU `Software\Classes\pixiv` (`pixiv://` 핸들러) | Pixiv 로그인 창이 크래시·강제 종료로 닫히면 남았다. 남아 있으면 아무 웹 페이지나 `pixiv://`로 이동시켜 파이썬을 띄울 수 있다 | `shell\open\command` 값에 `iroiro_pixiv_`가 있을 때만. 없으면 남의 등록이라 두고, 남이 덧붙인 하위 키가 있으면 그 키도 남긴다 |
| `%TEMP%\iroiro_pixiv_*` | 같은 이유 — 콜백 폴더와 옛 고정 이름 핸들러·콜백 파일 | 이름이 이 접두어로 시작하면 |
| 자격 증명 관리자 `iroiro-downloader`의 `pixiv_refresh_token`·`bilibili_cookies` | 지운 기능의 비밀 | 언제나 (`keyring.delete_password`). 없으면 조용히, 그 밖의 오류는 stderr에 한 줄 남기고 다음 기동 때 다시 시도 |

- **이 앱의 등록인지 가르는 표지가 `iroiro_pixiv_`다.** 옛 두 판의 핸들러 명령이 모두 담는다 — 2026-08 판 `"<python>" "%TEMP%\iroiro_pixiv_handler.py" "%1"`, 2026-09 판 `"<python>" -c "…" "%TEMP%\iroiro_pixiv_XXX\callback.txt" "%1"`. 옛 `cleanup_stale_scheme()`은 확인 없이 지웠지만, 이제는 이 앱이 그 키를 다시 만들 일이 없으므로 남의 등록을 지우지 않게 표지를 본다.
- **keyring은 이 정리에만 남겼다.** 비밀을 쓴 것이 keyring이므로 지우는 것도 keyring으로 한다 — Windows 백엔드는 같은 서비스에 사용자가 둘이면 하나를 `{username}@{service}`로 옮겨 적는데, `delete_password`가 두 자리를 모두 지운다. 그 규칙을 다시 구현하지 않으려는 것이다. keyring이 없으면 이 정리만 건너뛰고 앱은 뜬다.
- **빼도 되는 때:** 옛 판을 쓴 기기에서 새 판이 한 번 떠서 지운 뒤 — `cmdkey /list | findstr iroiro`가 비고 `HKCU\Software\Classes\pixiv`가 없으면 끝난 것이다. 이 모듈과 `requirements.txt`의 keyring을 함께 뺀다.
- **시험할 때는** 실제 레지스트리·자격 증명 관리자·`%TEMP%`를 건드리지 않게 이 모듈의 `winreg`·`keyring`을 가짜로 바꾸고 `APPDATA`·`LOCALAPPDATA`·`TEMP`를 스크래치로 돌린다.

## YouTube 다운로드

- **라이브러리**: `yt-dlp` (Python API) + `imageio-ffmpeg` (ffmpeg 바이너리 번들)
- **ffmpeg**: `imageio_ffmpeg.get_ffmpeg_exe()`로 번들 경로 취득. 사용자가 별도 설치 불필요.

### YouTube 인증 (세션 전용 / 반응형)

YouTube 쿠키는 실측상 **30분 이내**로 회전된다(SIDCC + 메인 세션 토큰 양쪽). 따라서 영속 저장은 무의미하며 **세션 전용 + 반응형(필요 시 입력 요청)** 방식을 채택.

- 메커니즘은 `CookieFileAuthMixin` 공통 구현 사용. 쿠키는 어디에도 저장되지 않고 프로세스 메모리에만 존재한다. 사용자에게 안내하는 문구(`COOKIE_PROMPT.note`)와 실제 동작이 여기서 일치한다.
  - 예전에는 `%TEMP%\iroiro_youtube_cookies_*.txt`에 평문으로 쓰고 `atexit`으로 지웠다. `atexit`은 강제 종료·크래시에서 돌지 않아 세션 쿠키가 그대로 남았고, 안내 문구와도 어긋났다. 되돌리지 말 것.
- **설정 창에 인증 항목이 없다** — 사전 입력 UI 미제공.
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

- **비디오**: MP4 컨테이너. mp4 영상+m4a 음성 우선. ffmpeg로 병합.
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

YouTube는 client마다 포맷을 내주는 조건이 다르다. 실측(2026-10, yt-dlp 2026.08.19):

| client | PO Token 없이 | 쿠키 지원 | 비고 |
|--------|---------------|-----------|------|
| `web`, `web_safari` | SABR 전용 — URL 없는 포맷만 | O | **PO Token을 줘도 마찬가지**(강제 SABR) |
| `mweb` | 포맷을 건너뜀 (`require a GVS PO Token`) | O | **PO Token이 있으면 정상** |
| `android_vr` | 받아짐 | **X** | yt-dlp 2026.07.04 이하는 **앞 1 MiB 뒤 403** |
| `web_embedded` | 받아짐 | O | 연령 제한 영상은 퍼가기가 막혀 UNPLAYABLE |
| `tv_downgraded` | `The page needs to be reloaded.` | O | |

결정적인 점: **쿠키를 넣으면 yt-dlp가 `android_vr`을 뺀다**(`SUPPORTS_COOKIES` 거짓). 그러면
PO Token 없이 받아지는 client가 남지 않아 로그인이 필요한 영상(연령 제한 등)은 언제나
`Requested format is not available` 로 끝난다. 쿠키를 넣었는데 이 오류가 나면 쿠키 문제가 아니다.

```python
_PLAYER_CLIENTS = ["default", "mweb", "web_safari", "android_vr"]
```

여러 client를 나열하면 yt-dlp가 포맷 목록을 합친다. 비로그인 상태에서는 `default` 묶음
(`visionos`, `web`)과 `android_vr`이 받고, 로그인 상태에서는 `mweb`이 받는다.

#### PO Token 공급자 (`src/pot_provider.py`)

`bgutil-ytdlp-pot-provider`를 쓴다. 두 조각이다.

- **yt-dlp 플러그인** — pip 패키지. `requirements.txt`에서 **버전을 고정**한다. 플러그인은
  서버와 주 버전이 다르면 경고하므로 둘을 함께 올려야 한다.
- **토큰 생성 서버** — Node 프로젝트(`server/`). **스크립트 모드**로 쓴다 — yt-dlp가 토큰이
  필요할 때 `node build/generate_once.js`를 띄우므로 상주 서버 관리가 없다.
  - 위치: `%LOCALAPPDATA%\iroiro-downloader\pot-provider\<플러그인 버전>\server`.
    **저장소 밖에 둔다** — 이 저장소는 GitHub Pages로 통째로 공개되고 `node_modules`는
    수백 MB의 서드파티 코드다(라이선스 GPL-3.0).
  - 설치: `python -m src.pot_provider`. 플러그인과 같은 태그의 소스 tar.gz를 httpx로 받아
    `server/`만 풀고 `npm ci` → `tsc`. git 불필요. 임시 폴더에서 빌드를 끝낸 뒤 옮기므로
    실패해도 반쯤 된 폴더가 "준비됨"으로 판정되지 않는다. 이미 있으면 아무것도 안 한다.
  - 플러그인 버전을 올리면 새 버전 폴더가 비어 미설치로 판정된다 → 다시 설치.
- `ytdlp_extractor_args()`가 준비됐을 때만 `youtubepot-bgutilscript:server_home`을 넘긴다.
  다운로드마다 판정하므로 설치 후 앱 재시작은 필요 없다. 미설치여도 yt-dlp는 동작한다 —
  `mweb` 포맷만 빠진다.
- 미설치 상태에서 `Requested format is not available`이 나면 `_translate_error`가 설치 명령을
  안내한다(쿠키 문제로 오해하지 않도록). `unavailable_reason()`이 **무엇을 확인했는지**
  (플러그인 버전·실행 중인 파이썬·찾은 스크립트 경로)를 메시지에 함께 싣는다 — 개발 셸에서는
  준비됨인데 앱에서만 미설치로 판정된 일이 있어, 사용자 환경에서 원인을 가르려고 넣었다.
- **설치는 사용자 자신의 터미널에서 할 것.** Claude 데스크톱(MSIX 패키지) 안에서 돈
  `python -m src.pot_provider`는 `%LOCALAPPDATA%` 쓰기가 `…\Packages\Claude_*\LocalCache\Local\`로
  가상화되어, Claude 쪽에서는 설치된 것으로 보이지만 앱에서는 보이지 않는다(실제로 겪었다).
- 토큰 캐시(yt-dlp `memory`)는 프로세스 전역이라 probe·다운로드 두 인스턴스가 같은 토큰을
  쓴다. 그래도 `mweb`을 목록에 넣은 탓에 **매 다운로드마다 토큰 생성(Node 실행) 비용**이
  붙는다 — 비로그인 영상에서는 쓰이지 않는 토큰이다. 알고 감수한 비용이다.
- 플러그인은 `bgutil:http`(127.0.0.1:4416) 공급자도 등록한다. 서버를 띄우지 않으므로 ping이
  즉시 실패하고 스크립트 공급자로 넘어간다.

확인: `yt-dlp -v` 출력에 `Generating a gvs PO Token for mweb client via bgutil script`.

참고: [PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide),
[bgutil-ytdlp-pot-provider](https://github.com/Brainicism/bgutil-ytdlp-pot-provider),
yt-dlp/yt-dlp#12482. format_selector 라이프사이클·협력적 취소는 "yt-dlp 공통 베이스" 섹션 참고.

### 저장 경로

`{save_dir}/YouTube/{업로더명}/{제목}.{ext}`. `task.save_path`는 업로더 폴더로 설정.

### 작업 ID

- 단일 영상: `youtube-video-{11자리 video_id}`

### URL 처리 범위

`can_handle()`은 `_VIDEO_RE`로 단일 영상 URL(`watch?v=`, `shorts/`, `youtu.be/`)만 매칭한다. 플레이리스트/채널 URL은 의도적으로 거부한다 — 코드는 단일 영상 메타데이터만 처리하므로.

## 주요 설계 결정

- YouTube는 yt-dlp로 받는다. yt-dlp 기반 사이트의 공통 흐름은 `YtdlpExtractor`에 있다. 사이트별 코드는 훅만 구현하고, 진행률·오류·취소 처리를 다시 만들지 말 것.
- GUI는 익스트랙터의 선언(`options_schema()`, `CookieAuth`)만 보고 동작한다. 사이트를 붙일 때 GUI에 `isinstance`/site 문자열 분기를 넣는 것은 회귀로 간주한다.
- 익스트랙터 인스턴스는 레지스트리에서 앱 전체에 하나만 존재(싱글턴). 여러 워커가 공유하므로 상태를 task 객체에 저장하고 익스트랙터 자체는 무상태에 가깝게 유지할 것.
- `task.save_path`는 익스트랙터가 dest_dir 확정 시 즉시 설정한다. 삭제 팝업의 "파일도 삭제"가 이 경로를 사용하므로 설정을 누락하면 파일이 삭제되지 않는다.
- `task.options`는 익스트랙터별 추가 파라미터 전달에 사용. YouTube는 `mode`, `quality`를 담는다.
