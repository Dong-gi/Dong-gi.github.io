"""yt-dlp 기반 익스트랙터 공통 베이스.

YouTube · bilibili · 스트리밍 매니페스트(M3U8/MPD)가 모두 공유하는 부분을 모았다.

- 2단계 흐름: 메타데이터 probe → 실제 다운로드 (동일 옵션으로 인스턴스 2개)
- 협력적 취소: progress_hook에서 `CancelDownload` → `InterruptedError` 재포장
- 진행률 계산 및 표시 문자열 생성
- `DownloadError` 메시지 정리(ANSI 제거) 및 사이트별 오류 변환

**`YoutubeDL.format_selector`는 `__init__`에서 한 번만 빌드된다.** 따라서 모든 옵션은
반드시 생성자 인자로 넘겨야 하며, 인스턴스를 만든 뒤 `ydl.params`를 고쳐도 반영되지 않는다.
probe 단계와 다운로드 단계가 별도 인스턴스인 이유다.

사이트별 차이는 아래 훅으로만 표현한다:
    PROBE / _extra_opts / _format_spec / _dest_dir / _filename_template /
    _title / _stream_labels / _resolve_url / _translate_error
"""
import atexit
import os
import re
import tempfile
import threading
from pathlib import Path
from typing import ClassVar

import imageio_ffmpeg
import yt_dlp

from src.config import Config
from src.extractors._util import (
    CancelDownload,
    clean_message,
    escape_outtmpl,
    write_netscape_cookies,
)
from src.extractors.base import BaseExtractor, CookieAuth, ProgressCallback
from src.models.task import Task

# 재시도 기본값은 **yt-dlp CLI 옵션 파서에만** 존재하고 `YoutubeDL` 클래스에는 없다.
# 지정하지 않으면 `RetryManager(self.params.get('retries'))` → `_retries or 0` 이 되어
# **재시도 0회**가 되고, 연결이 한 번만 끊겨도 작업이 그대로 실패한다.
# (증상: `[download] Got error: N bytes read, M more expected` — CDN이 전송 도중 연결 종료)
_RETRY_OPTS: dict = {
    "retries": 10,            # CLI 기본값과 동일
    "fragment_retries": 10,
    "file_access_retries": 3,
    # sleep_func는 `sleep_func(n=시도횟수-1)`로 호출된다. 1→2→4→…→최대 30초.
    "retry_sleep_functions": {
        "http": lambda n: min(2**n, 30),
        "fragment": lambda n: min(2**n, 30),
    },
    "socket_timeout": 20.0,   # 멈춘 연결을 오래 붙들지 않음
    "continuedl": True,       # .part 파일에서 이어받기 (기본값이지만 의존을 명시)
}

# 전송이 완료되지 않고 끊긴 경우 — 사용자에게는 "재시작하면 이어받는다"가 핵심 정보다.
_TRANSFER_INTERRUPTED_RE = re.compile(
    r"more expected|incomplete read|connection (?:reset|aborted|closed)|timed out",
    re.IGNORECASE,
)


def _translate_common_error(message: str) -> Exception | None:
    """사이트와 무관한 네트워크 오류를 안내 메시지로 변환."""
    if _TRANSFER_INTERRUPTED_RE.search(message):
        return RuntimeError(
            "전송이 도중에 끊겼습니다(서버가 연결을 종료했거나 응답이 없음). "
            "재시작하면 받은 지점부터 이어받습니다. "
            f"[원본: {message[:120]}]"
        )
    return None


class YtdlpExtractor(BaseExtractor):
    """yt-dlp로 다운로드하는 익스트랙터의 공통 구현."""

    #: 다운로드 전 메타데이터 추출 단계를 수행할지 여부.
    #: 저장 경로/제목에 업로더·제목이 필요한 사이트는 True, URL만으로 충분하면 False.
    PROBE: ClassVar[bool] = True

    def __init__(self, config: Config):
        self._config = config

    # ------------------------------------------------------------ 서브클래스 훅

    def _extra_opts(self, task: Task) -> dict:
        """사이트별 yt-dlp 옵션. probe/다운로드 두 단계에 동일하게 적용된다."""
        return {}

    def _format_spec(self, task: Task) -> str:
        """yt-dlp format 선택자."""
        return "best"

    def _dest_dir(self, task: Task, info: dict | None, save_dir: str) -> Path:
        """저장 디렉토리. `info`는 PROBE=False면 None."""
        raise NotImplementedError

    def _filename_template(self, task: Task, info: dict | None) -> str:
        """`_dest_dir` 아래에 쓸 파일명 템플릿 (yt-dlp outtmpl 문법)."""
        return "%(title)s.%(ext)s"

    def _title(self, task: Task, info: dict | None) -> str:
        """작업 목록에 표시할 제목."""
        return (info or {}).get("title") or task.url

    def _stream_labels(self, task: Task) -> tuple[str, ...]:
        """순차 다운로드되는 스트림들의 표시 이름 (예: ``("영상", "음성")``).

        빈 튜플이면 전체 진행률 환산 없이 현재 파일 기준 퍼센트만 표시한다
        (세그먼트 스트림처럼 전체 크기를 알 수 없는 경우).
        """
        return ()

    def _resolve_url(self, task: Task) -> str:
        """yt-dlp에 넘길 실제 URL. 짧은 링크 해석 등이 필요하면 재정의."""
        return task.url

    def _translate_error(self, message: str) -> Exception | None:
        """yt-dlp 오류 메시지를 사이트별 예외로 변환. 해당 없으면 None."""
        return None

    # ------------------------------------------------------------ 공통 구현

    def download(
        self,
        task: Task,
        save_dir: str,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None = None,
    ) -> str:
        on_progress(0.0, "정보 수집 중...")

        url = self._resolve_url(task)
        self._check_stop(stop_event)

        base_opts = self._base_opts(task)

        info: dict | None = None
        if self.PROBE:
            info = self._extract_info(url, base_opts)
            self._check_stop(stop_event)

        dest_dir = self._dest_dir(task, info, save_dir)
        dest_dir.mkdir(parents=True, exist_ok=True)
        # save_path는 삭제 팝업의 "파일도 삭제"가 사용하므로 반드시 설정
        task.save_path = str(dest_dir)

        dl_opts: dict = {
            **base_opts,
            "outtmpl": self._outtmpl(task, info, dest_dir),
            "progress_hooks": [self._make_progress_hook(task, on_progress, stop_event)],
        }

        try:
            with yt_dlp.YoutubeDL(dl_opts) as ydl:
                ydl.download([url])
        except CancelDownload:
            raise InterruptedError()
        except yt_dlp.utils.DownloadError as e:
            raise self._as_error(e) from e

        return self._title(task, info)

    def _base_opts(self, task: Task) -> dict:
        opts: dict = {
            **_RETRY_OPTS,
            "quiet": True,
            "no_warnings": True,
            "format": self._format_spec(task),
            "ffmpeg_location": imageio_ffmpeg.get_ffmpeg_exe(),
        }
        # 사이트별 옵션이 마지막 — 필요하면 공통값도 덮어쓸 수 있게
        opts.update(self._extra_opts(task))
        return opts

    def _outtmpl(self, task: Task, info: dict | None, dest_dir: Path) -> str:
        # 디렉토리 부분은 우리가 이미 sanitize한 리터럴이므로 `%`만 escape한다.
        # 경로 템플릿(`%(uploader)s` 등)을 yt-dlp에 맡기면 우리와 치환 규칙이 달라
        # (yt-dlp는 `/`→`⧸`, safe_filename은 `_`) 폴더가 이중으로 생긴다.
        return os.path.join(
            escape_outtmpl(str(dest_dir)), self._filename_template(task, info)
        )

    def _extract_info(self, url: str, opts: dict) -> dict:
        try:
            with yt_dlp.YoutubeDL(opts) as probe:
                info = probe.extract_info(url, download=False)
        except yt_dlp.utils.DownloadError as e:
            raise self._as_error(e) from e
        if not info:
            raise RuntimeError("영상 정보를 가져오지 못했습니다.")
        return info

    def _as_error(self, e: Exception) -> Exception:
        message = clean_message(str(e))
        # 사이트별 변환이 우선, 없으면 공통 네트워크 오류 안내, 그것도 아니면 원문
        return (
            self._translate_error(message)
            or _translate_common_error(message)
            or RuntimeError(message)
        )

    def _make_progress_hook(
        self,
        task: Task,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None,
    ):
        labels = self._stream_labels(task)
        files_ordered: list[str] = []

        def hook(d: dict) -> None:
            if stop_event and stop_event.is_set():
                raise CancelDownload()
            if d.get("status") != "downloading":
                return
            filename = d.get("filename") or ""
            if not filename:
                return
            if filename not in files_ordered:
                files_ordered.append(filename)
            index = files_ordered.index(filename)

            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            downloaded = d.get("downloaded_bytes") or 0
            file_pct = (downloaded / total) if total else 0.0

            if labels:
                # 영상/음성을 순차 다운로드 — 전체 진행률로 환산.
                # 폴백으로 단일 파일만 받는 경우를 위해 분모를 실제 파일 수로 보정.
                expected = max(len(labels), len(files_ordered))
                overall = (index + file_pct) / expected
                prefix = f"{labels[min(index, len(labels) - 1)]} "
            else:
                # 세그먼트 스트림 등 — 전체 크기를 알 수 없어 현재 파일 기준으로 표시
                overall = file_pct
                prefix = ""

            speed = d.get("speed") or 0
            speed_str = f" {speed / 1024:.0f}KB/s" if speed > 0 else ""
            on_progress(min(overall, 0.99), f"{prefix}{file_pct * 100:.0f}%{speed_str}")

        return hook

    @staticmethod
    def _check_stop(stop_event: threading.Event | None) -> None:
        if stop_event and stop_event.is_set():
            raise InterruptedError()


class CookieFileAuthMixin(CookieAuth):
    """쿠키 문자열을 임시 Netscape 파일로 기록해 yt-dlp `cookiefile`로 전달하는 믹스인.

    인메모리 `ydl.cookiejar.set_cookie()` 주입은 사용하지 않는다 — 그 경로는
    `YoutubeDLCookieJar.load()`를 우회하므로 extractor의 로그인 판정이 동작하지 않는다.

    영속 저장은 사이트마다 다르다(YouTube는 쿠키 회전이 잦아 세션 전용, bilibili는
    수명이 길어 자격 증명 관리자에 저장). `_load_saved_cookies` / `_save_cookies`
    를 재정의해 표현한다. 기본은 저장하지 않음.

    `YtdlpExtractor` 앞에 두고 다중 상속한다 (``class X(CookieFileAuthMixin, YtdlpExtractor)``).
    """

    #: 쿠키를 적용할 도메인 (하위 도메인 포함을 위해 앞에 점).
    COOKIE_DOMAIN: ClassVar[str]

    def __init__(self, config: Config):
        super().__init__(config)  # type: ignore[call-arg]  # 협력적 다중 상속 — YtdlpExtractor로 전달
        self._cookies_file: Path | None = None
        atexit.register(self._clear_cookies_file)
        saved = self._load_saved_cookies()
        if saved:
            self._write_cookies_file(saved)

    # ------------------------------------------------------------ 영속 저장 훅

    def _load_saved_cookies(self) -> str:
        """저장된 쿠키 복원. 기본은 세션 전용(저장 안 함)."""
        return ""

    def _save_cookies(self, cookie_str: str) -> None:
        """쿠키 영속 저장/삭제. 기본은 아무것도 하지 않음."""

    # ------------------------------------------------------------ CookieAuth

    def set_cookies(self, cookie_str: str) -> None:
        self._clear_cookies_file()
        self._save_cookies(cookie_str)
        if cookie_str:
            self._write_cookies_file(cookie_str)

    def has_cookies(self) -> bool:
        return self._cookies_file is not None

    # ------------------------------------------------------------ 내부

    def _cookie_opts(self) -> dict:
        """`_extra_opts`에 병합할 yt-dlp 쿠키 옵션."""
        return {"cookiefile": str(self._cookies_file)} if self._cookies_file else {}

    def _write_cookies_file(self, cookie_str: str) -> None:
        # NamedTemporaryFile 대신 mkstemp + 즉시 닫기 — Windows에서 yt-dlp가 읽을 수 있도록
        fd, name = tempfile.mkstemp(prefix=f"iroiro_{self.site_id}_cookies_", suffix=".txt")
        os.close(fd)
        path = Path(name)
        write_netscape_cookies(cookie_str, path, self.COOKIE_DOMAIN)
        self._cookies_file = path

    def _clear_cookies_file(self) -> None:
        if self._cookies_file:
            self._cookies_file.unlink(missing_ok=True)
            self._cookies_file = None
