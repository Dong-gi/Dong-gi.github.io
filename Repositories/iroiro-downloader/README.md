# iroiro-downloader

개인 사용 목적의 미디어 다운로더. Pixiv, YouTube, bilibili, hitomi.la, M3U8(HLS) 및 MPD(MPEG-DASH) 스트림을 지원하며 사이트를 추가할 수 있는 구조로 설계되어 있습니다.

## 요구 사항

- Python 3.12 이상
- Windows 10/11
- **Node.js 22.6 이상** — YouTube의 JS 챌린지(signature/n-param) 해결에 필요. [nodejs.org](https://nodejs.org/)에서 LTS 설치.

> ℹ Node가 없으면 YouTube 다운로드 시 `Requested format is not available` 오류가 발생합니다. yt-dlp 2025+ 부터 JavaScript runtime 필수.

## 설치

```powershell
pip install -r requirements.txt
```

Pixiv refresh token과 bilibili 쿠키는 **Windows Credential Manager**에 저장됩니다. YouTube 쿠키는 회전 주기가 짧아(30분 이내) 저장하지 않으며, 필요한 경우(연령 확인 등) 다이얼로그로 입력받아 이번 실행 동안만 보관합니다.

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
| Bilibili 인증 | (선택) 대회원·고화질 영상을 받을 때만 필요. **쿠키 입력** 버튼 |
| 최대 동시 다운로드 | 기본값 3 |

> YouTube는 사전 설정이 필요 없습니다. 인증이 필요한 영상은 다운로드 시도 시 자동으로 쿠키 입력 다이얼로그가 열립니다. bilibili도 마찬가지로, 필요한 순간에 자동으로 물어봅니다.

### Pixiv 로그인

**브라우저로 로그인** 버튼을 클릭하면 기본 브라우저가 열립니다. Pixiv 계정으로 로그인을 완료하면 자동으로 인증이 처리됩니다.

## 사용법

1. URL을 입력 창에 붙여넣고 **추가** 또는 **Enter**
2. YouTube · bilibili URL의 경우 다운로드 옵션(비디오/음성만, 품질) 선택 팝업이 표시됩니다
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

# bilibili
https://www.bilibili.com/video/BV1xx411c7mD     # 단일 영상
https://www.bilibili.com/video/BV1xx411c7mD?p=4 # 분P(다중 파트) 중 4편
https://www.bilibili.com/video/av170001         # 레거시 av 링크
https://b23.tv/AbCd12                           # 공유용 짧은 링크

# hitomi.la
https://hitomi.la/reader/12345.html             # 리더 페이지 (#1- 같은 프래그먼트 허용)
https://hitomi.la/galleries/12345.html          # 갤러리 페이지
https://hitomi.la/manga/title-with-id-12345.html  # 타입별 페이지 (manga, doujinshi, cg, imageset, anime)

# M3U8 (HLS 스트림)
https://example.com/path/playlist.m3u8
https://stream.example.com/live.m3u8?token=...    # 서명 토큰 포함도 가능

# MPD (MPEG-DASH 스트림)
https://example.com/path/manifest.mpd
https://dash.example.com/stream.mpd?sig=...
```

### YouTube 인증 (연령 확인 / 멤버십 영상)

해당 영상의 다운로드를 시도하면 자동으로 쿠키 입력 다이얼로그가 열립니다. 그때:

1. Chrome/Edge에서 YouTube에 로그인 후 임의의 YouTube 페이지를 열기
2. **F12** → **Network** 탭 → 목록에서 YouTube 요청 선택
3. **Request Headers** 에서 **`cookie:`** 값 전체 복사
4. 다이얼로그에 붙여넣고 확인

쿠키는 현재 실행 동안만 유지됩니다 — YouTube의 쿠키 회전 주기가 짧아(30분 이내) 영구 저장이 무의미하기 때문입니다. 프로그램을 재시작하면 다시 입력해야 합니다.

### bilibili

URL을 추가하면 **비디오 / 음성만** 과 품질을 고르는 팝업이 뜹니다. 음성만을 고르면 영상 스트림은 받지 않고 오디오(m4a)만 내려받으므로 훨씬 빠릅니다.

| 옵션 | 선택지 |
|---|---|
| 종류 | 비디오 / 음성만 |
| 품질 (비디오) | 4K / 1080p / 720p / 480p / 360p |
| 품질 (음성) | 최고 품질(약 192kbps) / 중간(약 132kbps) / 저용량(약 64kbps) |

처리 대상은 **단일 영상**입니다. 분P(다중 파트) 영상은 URL의 `?p=N`이 가리키는 한 편만 받으며, 지정이 없으면 1편을 받습니다. 番剧(`/bangumi/`)과 UP주 전체 목록(`space.bilibili.com`)은 지원하지 않습니다.

로그인 없이도 대부분의 공개 영상과 오디오는 받을 수 있습니다. 대회원 전용이나 고화질이 필요한 경우 쿠키 입력 팝업이 자동으로 열립니다.

1. 브라우저에서 bilibili에 로그인
2. **F12** → **Network** 탭 → bilibili.com 요청 선택
3. **Request Headers** 의 **`cookie:`** 값 전체 복사 (**SESSDATA** 포함 필수)
4. 팝업에 붙여넣고 확인

bilibili 쿠키는 수명이 길어 Credential Manager에 저장되며, 설정 창에서 삭제할 수 있습니다.

> 다운로드가 `412` 오류로 실패하면 서버가 일시적으로 요청을 차단한 것입니다. 잠시 후 재시도하거나 `pip install -U yt-dlp` 로 업데이트하세요.

> **속도가 수십 KB/s로 느리거나 전송이 중간에 끊긴다면** bilibili가 PCDN(개인 회선 기반 노드)으로 배정한 경우입니다. 자동으로 10회까지 재시도하며 받은 지점부터 이어받고, 10MB 단위로 잘라 받아 중단 위험을 줄입니다. 그래도 실패하면 **재시작** 버튼을 누르세요 — 이미 받은 부분은 다시 받지 않습니다. 배정 노드는 시도할 때마다 달라지므로 재시작만으로 해결되는 경우가 많습니다.

### 저장 경로 구조

```
저장 위치/
├── 작가명/                        # Pixiv
│   ├── 12345678_제목.jpg          # 단일 이미지
│   ├── 12345678_제목_p000.jpg     # 다중 이미지 (첫 번째)
│   ├── 12345678_제목_p001.jpg
│   └── 12345678_제목_ugoira.zip   # 우고이라 (애니메이션)
├── YouTube/업로더명/제목.mp4
├── Bilibili/UP주명/제목.m4a
├── Hitomi/작가명/12345_제목/001.avif
├── M3U8/playlist_1a2b3c4d.mp4
└── MPD/manifest_5e6f7a8b.mp4
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

> yt-dlp로 받는 사이트라면 `BaseExtractor` 대신 [`YtdlpExtractor`](src/extractors/_ytdlp.py)를 상속하세요. 다운로드 흐름·진행률·취소·오류 처리가 이미 구현돼 있고 `_format_spec`, `_dest_dir` 같은 훅만 채우면 됩니다.

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
│   │   ├── base.py             # BaseExtractor / CookieAuth / OptionsSchema
│   │   ├── _ytdlp.py           # yt-dlp 공통 베이스 + 쿠키 인증 믹스인
│   │   ├── pixiv.py            # Pixiv 구현체 (httpx)
│   │   ├── youtube.py          # YouTube 구현체 (yt-dlp)
│   │   ├── bilibili.py         # bilibili 구현체 (yt-dlp)
│   │   ├── hitomi.py           # hitomi.la 구현체 (httpx)
│   │   ├── _stream.py          # 스트리밍 매니페스트 공통 베이스
│   │   ├── m3u8.py             # M3U8 (HLS)
│   │   └── mpd.py              # MPD (MPEG-DASH)
│   ├── core/worker.py          # QThread 다운로드 워커
│   └── gui/
│       ├── main_window.py
│       ├── task_widget.py
│       ├── settings_dialog.py
│       ├── pixiv_login_dialog.py       # 브라우저 로그인 다이얼로그
│       ├── media_options_dialog.py     # 다운로드 옵션 선택 (사이트 무관)
│       └── cookies_dialog.py           # 쿠키 붙여넣기 (사이트 무관)
└── config.json                 # 자동 생성, 설정 저장
```
