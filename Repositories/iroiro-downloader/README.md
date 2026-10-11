# iroiro-downloader

개인 사용 목적의 YouTube 다운로더. 사이트를 추가할 수 있는 구조로 설계되어 있습니다.

> **YouTube만 받습니다.** pixiv·hitomi는 안드로이드 브라우저(`Repositories/iroiro-browser`)로 옮겼고, bilibili·M3U8·MPD는 지웠습니다(2026-10-11 — 지운 코드는 git 이력에 있습니다).

## 요구 사항

- Python 3.12 이상
- Windows 10/11
- **Node.js 22.6 이상** — YouTube의 JS 챌린지(signature/n-param) 해결에 필요. [nodejs.org](https://nodejs.org/)에서 LTS 설치.

> ℹ Node가 없으면 YouTube 다운로드 시 `Requested format is not available` 오류가 발생합니다. yt-dlp 2025+ 부터 JavaScript runtime 필수.

## 설치

```powershell
pip install -r requirements.txt
python -m src.pot_provider
```

두 번째 줄은 YouTube **PO Token 공급자**를 빌드합니다(Node.js·npm 필요, 1~2분). 로그인이 필요한 YouTube 영상(연령 확인 등)은 이것이 있어야 받아집니다. 저장소 밖 `%LOCALAPPDATA%\iroiro-downloader\pot-provider\`에 설치되며, `requirements.txt`의 `bgutil-ytdlp-pot-provider` 버전을 올린 뒤에는 다시 실행해야 합니다.

YouTube 쿠키는 회전 주기가 짧아(30분 이내) 저장하지 않으며, 필요한 경우(연령 확인 등) 다이얼로그로 입력받아 이번 실행 동안만 보관합니다.

## 실행

```powershell
python main.py
```

기동할 때마다 예전 판이 남긴 것을 정리합니다 — 이 앱이 만든 `pixiv://` 핸들러 등록, `%TEMP%\iroiro_pixiv_*`, 자격 증명 관리자의 Pixiv refresh token·bilibili 쿠키. 없으면 아무 일도 하지 않습니다.

## 초기 설정

메뉴의 **설정**에서 바꿀 수 있습니다.

| 항목 | 설명 |
|---|---|
| 저장 위치 | 다운로드 파일이 저장될 폴더 |
| 최대 동시 다운로드 | 기본값 3 |

> 로그인 설정은 없습니다. 인증이 필요한 영상은 다운로드 시도 시 자동으로 쿠키 입력 다이얼로그가 열립니다.

## 사용법

1. URL을 입력 창에 붙여넣고 **추가** 또는 **Enter**
2. 다운로드 옵션(비디오/음성만, 품질) 선택 팝업이 표시됩니다
3. 다운로드가 자동으로 시작됩니다

다른 사이트의 주소는 "지원하지 않는 URL입니다."로 끝납니다. 예전 판에서 넘어온 다른 사이트의 작업은 목록에 '실패'로 남습니다(완료된 것은 그대로) — 제목을 누르면 주소가 열리니, 필요 없으면 삭제하세요.

### 지원 URL 형식

```
https://www.youtube.com/watch?v=XXXXXXXXXXX
https://youtu.be/XXXXXXXXXXX
https://www.youtube.com/shorts/XXXXXXXXXXX
```

재생목록·채널 주소는 받지 않습니다. `list=`가 붙은 영상 주소는 그 영상 하나만 받습니다.

### YouTube 인증 (연령 확인 / 멤버십 영상)

해당 영상의 다운로드를 시도하면 자동으로 쿠키 입력 다이얼로그가 열립니다. 그때:

1. Chrome/Edge에서 YouTube에 로그인 후 임의의 YouTube 페이지를 열기
2. **F12** → **Network** 탭 → 목록에서 YouTube 요청 선택
3. **Request Headers** 에서 **`cookie:`** 값 전체 복사
4. 다이얼로그에 붙여넣고 확인

쿠키는 현재 실행 동안만 유지됩니다 — YouTube의 쿠키 회전 주기가 짧아(30분 이내) 영구 저장이 무의미하기 때문입니다. 프로그램을 재시작하면 다시 입력해야 합니다.

> ℹ 쿠키를 넣은 뒤 `Requested format is not available`이 나면 쿠키 문제가 아닙니다. 로그인 상태에서는 PO Token이 있어야 받을 수 있는 경로만 남으므로, `python -m src.pot_provider`를 실행한 뒤 다시 시도하세요.

### 저장 경로 구조

```
저장 위치/
└── YouTube/업로더명/제목.mp4      # 음성만: 제목.m4a
```

### 작업 삭제

다운로드 중이거나 일시 정지된 작업을 삭제하면 팝업이 표시됩니다.

- **파일도 삭제** — 저장된 파일까지 모두 제거합니다
- **작업만 삭제** — 목록에서만 제거하고 파일은 유지합니다
- **취소** — 삭제를 취소하고 다운로드를 재개합니다

## 새 사이트 추가 방법

1. [`src/extractors/`](src/extractors/)에 새 파일 작성 (`BaseExtractor` 상속)

```python
# src/extractors/example.py
import threading
from src.config import Config
from src.extractors.base import BaseExtractor, ProgressCallback
from src.models.task import Task

class ExampleExtractor(BaseExtractor):
    site_id = "example"   # task ID prefix · 표시명

    def __init__(self, config: Config):
        self._config = config

    def can_handle(self, url: str) -> bool:
        return "example.com" in url

    def download(self, task, save_dir, on_progress, stop_event=None) -> str:
        # 다운로드 구현
        return "제목"
```

> yt-dlp로 받는 사이트라면 `BaseExtractor` 대신 [`YtdlpExtractor`](src/extractors/_ytdlp.py)를 상속하세요. 다운로드 흐름·진행률·취소·오류 처리가 이미 구현돼 있고 `_format_spec`, `_dest_dir` 같은 훅만 채우면 됩니다.

2. [`src/extractors/__init__.py`](src/extractors/__init__.py)의 `ExtractorRegistry.__init__`에 등록

```python
from src.extractors.example import ExampleExtractor

class ExtractorRegistry:
    def __init__(self, config: Config) -> None:
        self._extractors = [
            YoutubeExtractor(config),
            ExampleExtractor(config),  # 추가
        ]
```

## 프로젝트 구조

```
iroiro-downloader/
├── main.py
├── requirements.txt
└── src/
    ├── config.py               # 설정 읽기/쓰기
    ├── legacy_cleanup.py       # 옛 흔적 정리 (지운 기능이 남긴 등록·임시 파일·비밀)
    ├── pot_provider.py         # YouTube PO Token 서버 설치·위치 (`python -m src.pot_provider`)
    ├── models/task.py          # Task 데이터 클래스
    ├── extractors/
    │   ├── __init__.py         # 사이트 레지스트리
    │   ├── base.py             # BaseExtractor / CookieAuth / OptionsSchema
    │   ├── _util.py            # 파일명 정리 · 쿠키 변환 등 공통 함수
    │   ├── _ytdlp.py           # yt-dlp 공통 베이스 + 쿠키 인증 믹스인
    │   └── youtube.py          # YouTube 구현체 (yt-dlp)
    ├── core/worker.py          # QThread 다운로드 워커
    └── gui/
        ├── main_window.py
        ├── task_widget.py
        ├── settings_dialog.py
        ├── media_options_dialog.py     # 다운로드 옵션 선택 (사이트 무관)
        └── cookies_dialog.py           # 쿠키 붙여넣기 (사이트 무관)
```

설정(`config.json`)과 세션(`session.json`)은 저장소가 아니라 `%APPDATA%\iroiro-downloader\`에 저장됩니다.
