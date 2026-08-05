"""스트리밍 매니페스트 다운로드 공통 베이스 (HLS, DASH 등).

yt-dlp의 generic extractor + 번들 ffmpeg로 세그먼트를 받아 MP4로 muxing.
각 포맷별 차이는 클래스 변수(`URL_PATTERN`, `FOLDER_NAME`)만으로 충분하다.
공통 다운로드 흐름은 :class:`YtdlpExtractor` 참조.
"""
import hashlib
import re
from pathlib import Path
from typing import ClassVar
from urllib.parse import urlparse

from src.extractors._util import escape_outtmpl, safe_filename
from src.extractors._ytdlp import YtdlpExtractor
from src.models.task import Task


def _name_from_url(url: str) -> str:
    """URL 경로의 stem + URL 해시 8자리. 같은 URL → 같은 파일명 (덮어쓰기 방지)."""
    parsed = urlparse(url)
    stem = Path(parsed.path).stem or "stream"
    digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:8]
    return safe_filename(f"{stem}_{digest}")


class StreamExtractor(YtdlpExtractor):
    """raw 매니페스트 URL(HLS/DASH) 다운로드.

    하위 클래스가 정의:
    - `site_id` (BaseExtractor 상속, e.g. ``"m3u8"``, ``"mpd"``)
    - :attr:`URL_PATTERN`: 매칭용 정규식
    - :attr:`FOLDER_NAME`: ``{save_dir}/{FOLDER_NAME}/`` 아래에 저장
    """

    URL_PATTERN: ClassVar[re.Pattern[str]]
    FOLDER_NAME: ClassVar[str]

    # 매니페스트에는 업로더/제목 메타데이터가 없다 — probe 없이 바로 다운로드
    PROBE = False

    def can_handle(self, url: str) -> bool:
        return bool(self.URL_PATTERN.search(url))

    def make_task_id(self, url: str) -> str:
        # URL의 해시 — 보통 쿼리에 서명 토큰이 있어 그대로 해싱. 동일 URL → 동일 ID.
        digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:16]
        return f"{self.site_id}-{digest}"

    def canonical_url(self, url: str) -> str:
        # 스트리밍 매니페스트 쿼리는 서명 토큰일 때가 많아 임의 제거 불가
        return url

    # ------------------------------------------------------------ yt-dlp 훅

    def _extra_opts(self, task: Task) -> dict:
        return {
            "merge_output_format": "mp4",
            # HLS에서 ffmpeg downloader 강제 (네이티브보다 안정적). DASH에는 영향 없음.
            "hls_prefer_native": False,
        }

    def _format_spec(self, task: Task) -> str:
        # master playlist/manifest의 best variant는 yt-dlp가 자동 선택
        return "best"

    def _dest_dir(self, task: Task, info: dict | None, save_dir: str) -> Path:
        return Path(save_dir) / self.FOLDER_NAME

    def _filename_template(self, task: Task, info: dict | None) -> str:
        return f"{escape_outtmpl(_name_from_url(task.url))}.%(ext)s"

    def _title(self, task: Task, info: dict | None) -> str:
        return _name_from_url(task.url)
