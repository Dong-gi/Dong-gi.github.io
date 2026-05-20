"""스트리밍 매니페스트 다운로드 공통 베이스 (HLS, DASH 등).

yt-dlp의 generic extractor + 번들 ffmpeg로 세그먼트를 받아 MP4로 muxing.
각 포맷별 차이는 클래스 변수(`URL_PATTERN`, `FOLDER_NAME`)만으로 충분하다.
"""
import hashlib
import re
import threading
from pathlib import Path
from typing import ClassVar
from urllib.parse import urlparse

import imageio_ffmpeg
import yt_dlp

from src.config import Config
from src.extractors._util import CancelDownload, safe_filename
from src.extractors.base import BaseExtractor, ProgressCallback
from src.models.task import Task

_ANSI_RE = re.compile(r"\x1b\[[0-9;]*[mGKHF]")


def _clean(msg: str) -> str:
    return _ANSI_RE.sub("", msg).strip()


def _name_from_url(url: str) -> str:
    """URL 경로의 stem + URL 해시 8자리. 같은 URL → 같은 파일명 (덮어쓰기 방지)."""
    parsed = urlparse(url)
    stem = Path(parsed.path).stem or "stream"
    digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:8]
    return safe_filename(f"{stem}_{digest}")


class StreamExtractor(BaseExtractor):
    """yt-dlp generic + ffmpeg 기반 스트리밍 매니페스트 다운로드.

    하위 클래스가 정의:
    - `site_id` (BaseExtractor 상속, e.g. ``"m3u8"``, ``"mpd"``)
    - :attr:`URL_PATTERN`: 매칭용 정규식
    - :attr:`FOLDER_NAME`: ``{save_dir}/{FOLDER_NAME}/`` 아래에 저장
    """

    URL_PATTERN: ClassVar[re.Pattern[str]]
    FOLDER_NAME: ClassVar[str]

    def __init__(self, config: Config):
        self._config = config

    def can_handle(self, url: str) -> bool:
        return bool(self.URL_PATTERN.search(url))

    def make_task_id(self, url: str) -> str:
        # URL의 해시 — 보통 쿼리에 서명 토큰이 있어 그대로 해싱. 동일 URL → 동일 ID.
        digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:16]
        return f"{self.site_id}-{digest}"

    def canonical_url(self, url: str) -> str:
        # 스트리밍 매니페스트 쿼리는 서명 토큰일 때가 많아 임의 제거 불가
        return url

    def download(
        self,
        task: Task,
        save_dir: str,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None = None,
    ) -> str:
        on_progress(0.0, "정보 수집 중...")

        name = _name_from_url(task.url)
        dest_dir = Path(save_dir) / self.FOLDER_NAME
        dest_dir.mkdir(parents=True, exist_ok=True)
        task.save_path = str(dest_dir)

        files_ordered: list[str] = []

        def progress_hook(d: dict) -> None:
            if stop_event and stop_event.is_set():
                raise CancelDownload()
            if d["status"] != "downloading":
                return
            filename = d.get("filename") or ""
            if not filename:
                return
            if filename not in files_ordered:
                files_ordered.append(filename)

            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            downloaded = d.get("downloaded_bytes") or 0
            pct = (downloaded / total) if total else 0
            speed = d.get("speed") or 0
            speed_str = f" {speed / 1024:.0f}KB/s" if speed > 0 else ""
            # 세그먼트 단위라 전체 진행률 정확치 않음 — 현재 파일 기준 표시
            on_progress(min(pct, 0.99), f"{pct * 100:.0f}%{speed_str}")

        ydl_opts: dict = {
            "quiet": True,
            "no_warnings": True,
            "outtmpl": str(dest_dir / f"{name}.%(ext)s"),
            "ffmpeg_location": imageio_ffmpeg.get_ffmpeg_exe(),
            "progress_hooks": [progress_hook],
            "merge_output_format": "mp4",
            "format": "best",
            # HLS에서 ffmpeg downloader 강제 (네이티브보다 안정적). DASH에는 영향 없음.
            "hls_prefer_native": False,
        }

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([task.url])
        except CancelDownload:
            raise InterruptedError()
        except yt_dlp.utils.DownloadError as e:
            raise RuntimeError(_clean(str(e)))

        return name
