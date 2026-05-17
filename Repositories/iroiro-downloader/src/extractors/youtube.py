import atexit
import os
import re
import tempfile
import threading
import time
from pathlib import Path

import imageio_ffmpeg
import yt_dlp

from src.config import Config
from src.extractors._util import safe_filename
from src.extractors.base import AuthExpiredError, BaseExtractor, ProgressCallback
from src.models.task import Task

_VIDEO_RE = re.compile(
    r"(?:youtube\.com/(?:watch\?.*?v=|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})"
)
_ANSI_RE = re.compile(r"\x1b\[[0-9;]*[mGKHF]")

# 2026 YouTube SABR/PO Token 우회: PO Token 없이도 동작하는 player_client 우선
#   - default: yt-dlp가 알아서 최선 선택 (PO Token 있으면 사용)
#   - web_safari: HLS 반환, GVS PO Token 불필요
#   - android_vr: "made for kids" 검사 생략, PO Token 불필요
# 참고: yt-dlp/yt-dlp#12482, #13058, PO-Token-Guide wiki
_PLAYER_CLIENTS = ["default", "web_safari", "android_vr"]


def _clean(msg: str) -> str:
    """ANSI 이스케이프 코드 및 앞뒤 공백 제거."""
    return _ANSI_RE.sub("", msg).strip()


def _build_format_spec(mode: str, quality: str) -> str:
    """yt-dlp 정적 format 선택자. __init__ 시점에 빌드되므로 동적 변경 불가."""
    if mode == "audio":
        if quality == "best":
            return "bestaudio[ext=m4a]/bestaudio"
        return (
            f"bestaudio[abr<={quality}][ext=m4a]"
            f"/bestaudio[abr<={quality}]"
            f"/bestaudio[ext=m4a]/bestaudio"
        )
    h = quality
    return (
        f"bestvideo[height<={h}][ext=mp4]+bestaudio[ext=m4a]"
        f"/bestvideo[height<={h}]+bestaudio"
        f"/bestvideo+bestaudio"
        f"/best"
    )


_AUTH_KEYWORDS = (
    "sign in",
    "age-restricted",
    "age restricted",
    "members-only",
    "members only",
    "private video",
    "join this channel",
)


def _is_auth_error(msg: str) -> bool:
    lower = msg.lower()
    return any(kw in lower for kw in _AUTH_KEYWORDS)


def _write_netscape_cookies(cookie_str: str, path: Path) -> None:
    """'name=value; ...' 문자열을 Netscape 포맷 파일로 저장.

    yt-dlp의 `cookiefile` 옵션이 이 파일을 `YoutubeDLCookieJar.load()`로 처리하며
    `__Secure-3PAPISID` → `SAPISID` 자동 파생, `_HTTPONLY_PREFIX` 처리 등
    공식 처리 경로를 거치게 된다. 인메모리 `cookiejar.set_cookie()` 주입은
    이 경로를 우회해 YouTube extractor가 로그인 상태를 인식하지 못한다.
    """
    expires = int(time.time()) + 365 * 24 * 3600
    lines = ["# Netscape HTTP Cookie File", ""]
    for item in cookie_str.split(";"):
        item = item.strip()
        if "=" not in item:
            continue
        name, _, value = item.partition("=")
        name = name.strip()
        if not name:
            continue
        secure = "TRUE" if name.startswith("__Secure-") else "FALSE"
        # 형식: domain<TAB>include_sub<TAB>path<TAB>secure<TAB>expires<TAB>name<TAB>value
        lines.append(f".youtube.com\tTRUE\t/\t{secure}\t{expires}\t{name}\t{value.strip()}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


class _CancelDownload(BaseException):
    """progress_hook에서 stop_event 감지 시 발생. BaseException 상속으로 yt-dlp의 except Exception을 통과."""


class YoutubeExtractor(BaseExtractor):
    site_id = "youtube"

    def __init__(self, config: Config):
        self._config = config
        # YouTube 쿠키는 회전 주기가 짧아(30분 이내) 영속 저장이 무의미.
        # 인증 실패 시 사용자에게 입력받아 임시 Netscape 파일에만 기록 — 종료 시 삭제.
        self._cookies_file: Path | None = None
        atexit.register(self._clear_cookies_file)

    def set_cookies(self, cookie_str: str) -> None:
        """현재 세션 쿠키를 갱신. 빈 문자열이면 해제."""
        self._clear_cookies_file()
        if not cookie_str:
            return
        # NamedTemporaryFile 대신 mkstemp + 수동 닫기 — Windows에서 다른 프로세스가 읽도록
        fd, p = tempfile.mkstemp(prefix="iroiro_yt_cookies_", suffix=".txt")
        os.close(fd)
        path = Path(p)
        _write_netscape_cookies(cookie_str, path)
        self._cookies_file = path

    def has_cookies(self) -> bool:
        return self._cookies_file is not None

    def _clear_cookies_file(self) -> None:
        if self._cookies_file:
            self._cookies_file.unlink(missing_ok=True)
            self._cookies_file = None

    def can_handle(self, url: str) -> bool:
        # 단일 영상 URL만 처리 (플레이리스트/채널 URL 제외)
        return bool(_VIDEO_RE.search(url))

    def make_task_id(self, url: str) -> str:
        m = _VIDEO_RE.search(url)
        if m:
            return f"youtube-video-{m.group(1)}"
        return super().make_task_id(url)

    def canonical_url(self, url: str) -> str:
        """`list=`, `index=`, `pp=` 등 부수 파라미터 제거. `v=` 만 남김.

        `list=` 가 남아 있으면 yt-dlp가 재생목록 모드를 시도하는 등 부작용이 있어
        URL 추가 시점에서 정규화한다. (`noplaylist=True` 와 함께 belt-and-suspenders)
        """
        m = _VIDEO_RE.search(url)
        if not m:
            return url
        return f"https://www.youtube.com/watch?v={m.group(1)}"

    def download(
        self,
        task: Task,
        save_dir: str,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None = None,
    ) -> str:
        mode = task.options.get("mode", "video")
        quality = task.options.get("quality", "1080")

        on_progress(0.0, "정보 수집 중...")

        cookies_file = self._cookies_file
        has_cookies = cookies_file is not None

        # 비디오 모드는 bestvideo+bestaudio (2 streams) 우선, audio는 1 stream
        total_streams = 2 if mode == "video" else 1
        stream_labels = ["영상", "음성"] if mode == "video" else ["음성"]
        files_ordered: list[str] = []

        def progress_hook(d: dict) -> None:
            if stop_event and stop_event.is_set():
                raise _CancelDownload()
            if d["status"] != "downloading":
                return

            filename = d.get("filename") or ""
            if not filename:
                return
            if filename not in files_ordered:
                files_ordered.append(filename)
            stream_idx = files_ordered.index(filename)

            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            downloaded = d.get("downloaded_bytes") or 0
            stream_pct = downloaded / total if total else 0
            # combined stream fallback (1 file) 시 분모 보정
            effective_total = max(total_streams, len(files_ordered))
            overall = min((stream_idx + stream_pct) / effective_total, 0.99)

            label = stream_labels[min(stream_idx, len(stream_labels) - 1)]
            speed = d.get("speed") or 0
            speed_str = f" {speed / 1024:.0f}KB/s" if speed > 0 else ""
            on_progress(overall, f"{label} {stream_pct * 100:.0f}%{speed_str}")

        # format / extractor_args / js_runtimes 등은 두 단계 공통
        base_opts: dict = {
            "quiet": True,
            "no_warnings": True,
            "format": _build_format_spec(mode, quality),
            "ffmpeg_location": imageio_ffmpeg.get_ffmpeg_exe(),
            "extractor_args": {"youtube": {"player_client": _PLAYER_CLIENTS}},
            # YouTube n/sig 챌린지 해결용 JS runtime. Node는 자동 감지되지 않으므로 명시
            # (Deno는 자동 감지). yt-dlp-ejs 패키지가 솔버 스크립트 제공.
            "js_runtimes": {"node": {}},
            # URL에 list= 가 있어도 재생목록 다운로드 모드로 진입하지 않음 (단일 영상만 처리)
            "noplaylist": True,
        }
        if cookies_file:
            # cookiefile은 YoutubeDLCookieJar.load() 경로를 거쳐 SAPISID 자동 파생 등 처리됨
            base_opts["cookiefile"] = str(cookies_file)

        # Phase 1: 메타데이터 추출 (uploader/title)
        try:
            with yt_dlp.YoutubeDL(base_opts) as probe:
                info = probe.extract_info(task.url, download=False)
        except yt_dlp.utils.DownloadError as e:
            if _is_auth_error(str(e)):
                raise AuthExpiredError("youtube") if has_cookies else RuntimeError("AUTH_REQUIRED")
            raise RuntimeError(_clean(str(e)))

        if stop_event and stop_event.is_set():
            raise InterruptedError()

        uploader = safe_filename(info.get("uploader") or info.get("channel") or "YouTube")
        video_title = info.get("title", task.url)
        dest_dir = Path(save_dir) / "YouTube" / uploader
        dest_dir.mkdir(parents=True, exist_ok=True)
        task.save_path = str(dest_dir)

        # Phase 2: 실제 다운로드
        # outtmpl은 우리가 sanitize한 절대 경로 + 파일명 템플릿만 사용.
        # %(uploader)s 같은 경로 템플릿을 yt-dlp가 처리하게 두면 yt-dlp는 '/'를
        # ⧸(U+29F8)로 치환하지만 우리 safe_filename은 '_'로 치환해 폴더가 두 개 생성됨.
        dl_opts: dict = {
            **base_opts,
            "outtmpl": str(dest_dir / "%(title)s.%(ext)s"),
            "progress_hooks": [progress_hook],
        }
        if mode == "video":
            dl_opts["merge_output_format"] = "mp4"
        else:
            dl_opts["postprocessors"] = [
                {"key": "FFmpegExtractAudio", "preferredcodec": "m4a"}
            ]

        try:
            with yt_dlp.YoutubeDL(dl_opts) as dl:
                dl.download([task.url])
        except _CancelDownload:
            raise InterruptedError()
        except yt_dlp.utils.DownloadError as e:
            if _is_auth_error(str(e)):
                raise AuthExpiredError("youtube") if has_cookies else RuntimeError("AUTH_REQUIRED")
            raise RuntimeError(_clean(str(e)))

        return video_title
