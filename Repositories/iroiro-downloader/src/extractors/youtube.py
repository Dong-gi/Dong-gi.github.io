"""YouTube 다운로드 (yt-dlp 기반).

단일 영상만 처리한다 — 플레이리스트/채널 URL은 의도적으로 거부.
공통 다운로드 흐름은 :class:`YtdlpExtractor` 참조.
"""
import re
from pathlib import Path

from src.extractors._util import safe_filename
from src.extractors._ytdlp import CookieFileAuthMixin, YtdlpExtractor
from src.extractors.base import (
    AUTH_REQUIRED,
    MODE_AUDIO,
    MODE_VIDEO,
    AuthExpiredError,
    CookiePrompt,
    OptionsSchema,
    QualityChoice,
)
from src.models.task import Task

_VIDEO_RE = re.compile(
    r"(?:youtube\.com/(?:watch\?.*?v=|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})"
)

# 2026 YouTube SABR/PO Token 우회: PO Token 없이도 동작하는 player_client 우선
#   - default: yt-dlp가 알아서 최선 선택 (PO Token 있으면 사용)
#   - web_safari: HLS 반환, GVS PO Token 불필요
#   - android_vr: "made for kids" 검사 생략, PO Token 불필요
# 참고: yt-dlp/yt-dlp#12482, #13058, PO-Token-Guide wiki
_PLAYER_CLIENTS = ["default", "web_safari", "android_vr"]

_AUTH_KEYWORDS = (
    "sign in",
    "age-restricted",
    "age restricted",
    "members-only",
    "members only",
    "private video",
    "join this channel",
)

_OPTIONS = OptionsSchema(
    title="YouTube 다운로드 옵션",
    video_qualities=(
        QualityChoice("4K (2160p)", "2160"),
        QualityChoice("1080p", "1080"),
        QualityChoice("720p", "720"),
        QualityChoice("480p", "480"),
        QualityChoice("360p", "360"),
    ),
    audio_qualities=(
        QualityChoice("최고 품질", "best"),
        QualityChoice("중간 (128kbps)", "128"),
        QualityChoice("저용량 (64kbps)", "64"),
    ),
    default_video_quality="1080",
    default_audio_quality="best",
)

_COOKIE_PROMPT = CookiePrompt(
    title="YouTube 쿠키 설정",
    guide_html=(
        "이 영상은 로그인이 필요합니다.<br><br>"
        "브라우저 개발자 도구(F12) → Network 탭 → YouTube 요청 선택<br>"
        "→ Request Headers → <b>cookie:</b> 값을 아래에 붙여넣으세요."
    ),
    placeholder="CONSENT=YES+...; SID=...; HSID=...; ...",
    note=(
        "쿠키는 이번 실행 동안만 메모리에 보관되며 디스크에 저장되지 않습니다 "
        "(YouTube의 쿠키 회전 주기가 짧아 영구 저장이 무의미)."
    ),
    retry_failed_message=(
        "쿠키를 적용했지만 인증에 실패했습니다. "
        "쿠키가 회전됐거나(보통 30분 이내), 본 영상에 대해 계정 권한이 없을 수 있습니다 "
        "(예: YouTube 본인 인증 미완료)."
    ),
)


def _build_format_spec(mode: str, quality: str) -> str:
    """yt-dlp 정적 format 선택자. 인스턴스 생성 시점에 확정되므로 동적 변경 불가."""
    if mode == MODE_AUDIO:
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


class YoutubeExtractor(CookieFileAuthMixin, YtdlpExtractor):
    site_id = "youtube"

    # YouTube 쿠키는 회전 주기가 짧아(실측 30분 이내) 영속 저장이 무의미.
    # 인증 실패 시 사용자에게 입력받아 임시 파일에만 기록하고 종료 시 삭제한다.
    COOKIE_DOMAIN = ".youtube.com"
    COOKIES_PERSISTENT = False
    COOKIE_PROMPT = _COOKIE_PROMPT

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

    def options_schema(self) -> OptionsSchema:
        return _OPTIONS

    # ------------------------------------------------------------ yt-dlp 훅

    def _extra_opts(self, task: Task) -> dict:
        opts: dict = {
            "extractor_args": {"youtube": {"player_client": _PLAYER_CLIENTS}},
            # YouTube n/sig 챌린지 해결용 JS runtime. Node는 자동 감지되지 않으므로 명시
            # (Deno는 자동 감지). yt-dlp-ejs 패키지가 솔버 스크립트 제공.
            "js_runtimes": {"node": {}},
            # URL에 list= 가 있어도 재생목록 모드로 진입하지 않음 (단일 영상만 처리)
            "noplaylist": True,
            **self._cookie_opts(),
        }
        if _mode(task) == MODE_AUDIO:
            opts["postprocessors"] = [
                {"key": "FFmpegExtractAudio", "preferredcodec": "m4a"}
            ]
        else:
            opts["merge_output_format"] = "mp4"
        return opts

    def _format_spec(self, task: Task) -> str:
        return _build_format_spec(_mode(task), task.options.get("quality", "1080"))

    def _stream_labels(self, task: Task) -> tuple[str, ...]:
        # 비디오는 bestvideo+bestaudio 2개 스트림을 순차 다운로드
        return ("음성",) if _mode(task) == MODE_AUDIO else ("영상", "음성")

    def _dest_dir(self, task: Task, info: dict | None, save_dir: str) -> Path:
        data = info or {}
        uploader = safe_filename(data.get("uploader") or data.get("channel") or "YouTube")
        return Path(save_dir) / "YouTube" / uploader

    def _translate_error(self, message: str) -> Exception | None:
        lowered = message.lower()
        if any(kw in lowered for kw in _AUTH_KEYWORDS):
            # 쿠키가 이미 있는데 실패 → 만료. 없으면 최초 입력 요청.
            return AuthExpiredError(self.site_id) if self.has_cookies() else RuntimeError(AUTH_REQUIRED)
        return None


def _mode(task: Task) -> str:
    return task.options.get("mode", MODE_VIDEO)
