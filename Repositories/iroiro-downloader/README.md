# iroiro-downloader

개인 사용 목적의 미디어 다운로더. Pixiv와 YouTube를 지원하며 사이트를 추가할 수 있는 구조로 설계되어 있습니다.

## 요구 사항

- Python 3.12 이상
- Windows 10/11
- **Node.js 22.6 이상** — YouTube의 JS 챌린지(signature/n-param) 해결에 필요. [nodejs.org](https://nodejs.org/)에서 LTS 설치.

> ℹ Node가 없으면 YouTube 다운로드 시 `Requested format is not available` 오류가 발생합니다. yt-dlp 2025+ 부터 JavaScript runtime 필수.

## 설치

```powershell
pip install -r requirements.txt
```

Pixiv refresh token은 **Windows Credential Manager**에 저장됩니다. YouTube 쿠키는 회전 주기가 짧아(30분 이내) 저장하지 않으며, 필요한 경우(연령 확인 등) 다이얼로그로 입력받아 메모리에만 보관합니다.

## 실행

```powershell
python main.py
```

## 초기 설정

처음 실행 후 메뉴의 **설정**에서 아래 항목을 입력하세요.

| 항목 | 설명 |
|---|---|
| 저장 위치 | 다운로드 파일이 저장될 폴더 |
| Pixiv 인증 | **브라우저로 로그인** 버튼 클릭 |
| 최대 동시 다운로드 | 기본값 3 |

> YouTube는 사전 설정이 필요 없습니다. 인증이 필요한 영상은 다운로드 시도 시 자동으로 쿠키 입력 다이얼로그가 열립니다.

### Pixiv 로그인

**브라우저로 로그인** 버튼을 클릭하면 기본 브라우저가 열립니다. Pixiv 계정으로 로그인을 완료하면 자동으로 인증이 처리됩니다.

## 사용법

1. URL을 입력 창에 붙여넣고 **추가** 또는 **Enter**
2. YouTube URL의 경우 다운로드 옵션(비디오/음성, 화질) 선택 팝업이 표시됩니다
3. 다운로드가 자동으로 시작됩니다

### 지원 URL 형식

```
# Pixiv
https://www.pixiv.net/artworks/12345678        # 단일 작품
https://www.pixiv.net/en/artworks/12345678     # 언어 접두사 포함
https://www.pixiv.net/users/12345678           # 사용자 전체 작품

# YouTube
https://www.youtube.com/watch?v=XXXXXXXXXXX
https://youtu.be/XXXXXXXXXXX
https://www.youtube.com/shorts/XXXXXXXXXXX
```

### YouTube 인증 (연령 확인 / 멤버십 영상)

해당 영상의 다운로드를 시도하면 자동으로 쿠키 입력 다이얼로그가 열립니다. 그때:

1. Chrome/Edge에서 YouTube에 로그인 후 임의의 YouTube 페이지를 열기
2. **F12** → **Network** 탭 → 목록에서 YouTube 요청 선택
3. **Request Headers** 에서 **`cookie:`** 값 전체 복사
4. 다이얼로그에 붙여넣고 확인

쿠키는 현재 실행 동안만 메모리에 유지됩니다 — YouTube의 쿠키 회전 주기가 짧아(30분 이내) 영구 저장이 무의미하기 때문입니다. 프로그램을 재시작하면 다시 입력해야 합니다.

### 저장 경로 구조

```
저장 위치/
└── 작가명/
    ├── 12345678_제목.jpg          # 단일 이미지
    ├── 12345678_제목_p000.jpg     # 다중 이미지 (첫 번째)
    ├── 12345678_제목_p001.jpg
    └── 12345678_제목_ugoira.zip   # 우고이라 (애니메이션)
```

### 작업 삭제

다운로드 중인 작업을 삭제하면 팝업이 표시됩니다.

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

2. [`src/extractors/__init__.py`](src/extractors/__init__.py)의 `ExtractorRegistry.__init__`에 등록

```python
from src.extractors.example import ExampleExtractor

class ExtractorRegistry:
    def __init__(self, config: Config) -> None:
        self._extractors = [
            PixivExtractor(config),
            ExampleExtractor(config),  # 추가
        ]
```

## 프로젝트 구조

```
iroiro-downloader/
├── main.py
├── requirements.txt
├── src/
│   ├── config.py               # 설정 읽기/쓰기
│   ├── models/task.py          # Task 데이터 클래스
│   ├── auth/
│   │   └── pixiv_oauth.py      # PKCE OAuth 흐름 (레지스트리, 폴링)
│   ├── extractors/
│   │   ├── __init__.py         # 사이트 레지스트리
│   │   ├── base.py             # BaseExtractor 추상 클래스
│   │   ├── pixiv.py            # Pixiv 구현체
│   │   └── youtube.py          # YouTube 구현체 (yt-dlp)
│   ├── core/worker.py          # QThread 다운로드 워커
│   └── gui/
│       ├── main_window.py
│       ├── task_widget.py
│       ├── settings_dialog.py
│       ├── pixiv_login_dialog.py       # 브라우저 로그인 다이얼로그
│       ├── youtube_options_dialog.py   # YouTube 옵션 선택
│       └── youtube_cookies_dialog.py   # YouTube 쿠키 붙여넣기
└── config.json                 # 자동 생성, 설정 저장
```
