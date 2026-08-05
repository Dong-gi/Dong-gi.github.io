"""bilibili 다운로드 (yt-dlp 기반).

처리 범위는 **단일 영상 하나**로 한정한다.

- `bilibili.com/video/BV...` · `bilibili.com/video/av...`
- 분P(anthology)는 `?p=N`으로 지정한 한 파트만. 지정이 없으면 1편.
- `b23.tv/...` 짧은 링크는 다운로드 시점에 리다이렉트를 따라 실제 URL로 변환.

대상 외:
- 番剧 `/bangumi/play/...` — 지역 제한·유료 판정이 별도라 단일 Task 모델과 맞지 않음
- UP주 전체 `space.bilibili.com/...` — 재생목록(YouTube 채널 URL과 동일한 이유)

공통 다운로드 흐름은 :class:`YtdlpExtractor` 참조.
"""
import re
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import httpx

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

# BV ID는 대소문자를 구분한다 — 매칭만 IGNORECASE로 하고 캡처값은 원본 그대로 사용.
_VIDEO_RE = re.compile(r"bilibili\.com/video/(BV[0-9A-Za-z]{10}|av\d+)", re.IGNORECASE)
_SHORT_RE = re.compile(r"b23\.tv/([0-9A-Za-z]+)", re.IGNORECASE)

_HEADERS = {
    # CDN·웹 페이지 모두 Referer를 확인한다. yt-dlp의 bilibili extractor도 내부에서
    # 동일한 헤더를 붙이지만, 짧은 링크 해석은 우리가 직접 하므로 여기서도 지정.
    "Referer": "https://www.bilibili.com/",
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    ),
}

_SHORT_LINK_TIMEOUT = 10.0

# bilibili는 재생 URL을 PCDN(`*.mcdn.bilivideo.cn`, `*.szbdyd.com`) 노드로 배정하는 일이 잦고,
# 이 노드들은 속도가 낮고 전송 도중 연결을 끊는다. Range로 잘라 받으면 연결 하나가 오래
# 유지되지 않아 중단 위험이 줄고, 끊겨도 해당 조각만 다시 받으면 된다.
# 참고: yt-dlp/yt-dlp#12421, #14498
_CHUNK_SIZE = 10 * 1024 * 1024

_OPTIONS = OptionsSchema(
    title="bilibili 다운로드 옵션",
    video_qualities=(
        QualityChoice("4K (2160p)", "2160"),
        QualityChoice("1080p", "1080"),
        QualityChoice("720p", "720"),
        QualityChoice("480p", "480"),
        QualityChoice("360p", "360"),
    ),
    # bilibili의 오디오 트랙은 실측 64k / 132k / 192k 세 종류다.
    # value는 `abr<=` 임계값이므로 라벨의 비트레이트보다 약간 높게 잡는다.
    audio_qualities=(
        QualityChoice("최고 품질 (약 192kbps)", "best"),
        QualityChoice("중간 (약 132kbps)", "140"),
        QualityChoice("저용량 (약 64kbps)", "70"),
    ),
    default_video_quality="1080",
    default_audio_quality="best",
)

_COOKIE_PROMPT = CookiePrompt(
    title="bilibili 쿠키 설정",
    guide_html=(
        "이 영상은 로그인이 필요합니다.<br><br>"
        "브라우저에서 bilibili에 로그인 → 개발자 도구(F12) → Network 탭<br>"
        "→ bilibili.com 요청 선택 → Request Headers → <b>cookie:</b> 값을 붙여넣으세요.<br>"
        "<b>SESSDATA</b> 항목이 반드시 포함되어야 합니다."
    ),
    placeholder="SESSDATA=...; bili_jct=...; DedeUserID=...; ...",
    note=(
        "쿠키는 Windows 자격 증명 관리자에 저장됩니다. bilibili 쿠키는 수명이 길어 "
        "재입력 빈도가 낮습니다. 설정 창에서 삭제할 수 있습니다."
    ),
    retry_failed_message=(
        "쿠키를 적용했지만 인증에 실패했습니다. SESSDATA가 포함됐는지, "
        "해당 영상을 볼 수 있는 계정 권한(대회원 등)이 있는지 확인해주세요."
    ),
)

# 'Request is blocked by server (412)' / 'HTTP Error 412:' 양쪽을 잡되,
# 영상 ID 안의 숫자에는 걸리지 않도록 단어 경계로 제한
_HTTP_412_RE = re.compile(r"\b412\b")

# yt-dlp bilibili extractor가 로그인 요구 시 내보내는 메시지의 특징적 표현
_AUTH_KEYWORDS = (
    "premium member",
    "registered users",
    "login required",
    "requires login",
    "log in",
)


def _mode(task: Task) -> str:
    return task.options.get("mode", MODE_VIDEO)


def _part_number(url: str) -> int | None:
    """분P URL의 `?p=N`. 없거나 1이면 None.

    분P가 아닌 영상에서 `p=1`은 의미가 없고, 분P 영상에서도 `noplaylist` 동작상
    "p 없음"과 "p=1"이 같은 파트를 가리키므로 동일 작업으로 취급한다.
    """
    values = parse_qs(urlparse(url).query).get("p")
    if not values:
        return None
    try:
        part = int(values[-1])
    except ValueError:
        return None
    return part if part > 1 else None


def _build_format_spec(mode: str, quality: str) -> str:
    """yt-dlp 정적 format 선택자.

    bilibili는 DASH로 영상/음성이 분리 제공되므로 비디오는 항상 병합이 필요하다.
    호환성을 위해 avc1(H.264)을 우선하되, 없으면 hev1/av01로 폴백한다.
    """
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
        f"bestvideo[height<={h}][vcodec^=avc1]+bestaudio[ext=m4a]"
        f"/bestvideo[height<={h}][ext=mp4]+bestaudio[ext=m4a]"
        f"/bestvideo[height<={h}]+bestaudio"
        f"/bestvideo+bestaudio"
        f"/best"
    )


class BilibiliExtractor(CookieFileAuthMixin, YtdlpExtractor):
    site_id = "bilibili"

    # bilibili 쿠키(SESSDATA)는 YouTube와 달리 수명이 길어 영속 저장한다.
    COOKIE_DOMAIN = ".bilibili.com"
    COOKIES_PERSISTENT = True
    COOKIE_PROMPT = _COOKIE_PROMPT

    def can_handle(self, url: str) -> bool:
        return bool(_VIDEO_RE.search(url) or _SHORT_RE.search(url))

    def make_task_id(self, url: str) -> str:
        short = _SHORT_RE.search(url)
        if short:
            # 짧은 링크는 네트워크 없이 실제 ID를 알 수 없다 — 코드 자체를 ID로 사용.
            # (같은 영상의 전체 URL과는 다른 작업으로 잡히는 한계가 있다)
            return f"{self.site_id}-short-{short.group(1)}"
        m = _VIDEO_RE.search(url)
        if not m:
            return super().make_task_id(url)
        part = _part_number(url)
        suffix = f"-p{part}" if part else ""
        return f"{self.site_id}-video-{m.group(1)}{suffix}"

    def canonical_url(self, url: str) -> str:
        """`spm_id_from`, `vd_source`, `t`, `share_*` 등 추적 파라미터 제거.

        `?p=N`(분P)은 어느 파트인지를 정하는 의미 있는 값이므로 보존한다
        (YouTube의 `list=`와 다른 점).
        """
        short = _SHORT_RE.search(url)
        if short:
            return f"https://b23.tv/{short.group(1)}"
        m = _VIDEO_RE.search(url)
        if not m:
            return url
        base = f"https://www.bilibili.com/video/{m.group(1)}"
        part = _part_number(url)
        return f"{base}?p={part}" if part else base

    def options_schema(self) -> OptionsSchema:
        return _OPTIONS

    # ------------------------------------------------------------ 쿠키 영속 저장

    def _load_saved_cookies(self) -> str:
        return self._config.bilibili_cookies

    def _save_cookies(self, cookie_str: str) -> None:
        self._config.bilibili_cookies = cookie_str

    # ------------------------------------------------------------ yt-dlp 훅

    def _resolve_url(self, task: Task) -> str:
        """`b23.tv` 짧은 링크를 실제 영상 URL로 변환. 그 외는 그대로."""
        if not _SHORT_RE.search(task.url):
            return task.url
        try:
            with httpx.Client(
                follow_redirects=True, timeout=_SHORT_LINK_TIMEOUT, headers=_HEADERS
            ) as client:
                # 본문은 필요 없다 — 최종 URL만 확인하고 즉시 연결을 닫는다
                with client.stream("GET", task.url) as resp:
                    resolved = str(resp.url)
        except httpx.HTTPError as e:
            raise RuntimeError(f"짧은 링크를 해석하지 못했습니다: {e}") from e

        if not _VIDEO_RE.search(resolved):
            raise RuntimeError(f"지원하지 않는 링크로 연결됩니다: {resolved}")
        return self.canonical_url(resolved)

    def _extra_opts(self, task: Task) -> dict:
        opts: dict = {
            # 분P 전체를 재생목록으로 받지 않고 지정 파트(기본 1편)만 처리
            "noplaylist": True,
            # PCDN 노드 대응 — 청크 단위 Range 다운로드
            "http_chunk_size": _CHUNK_SIZE,
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
        return ("음성",) if _mode(task) == MODE_AUDIO else ("영상", "음성")

    def _dest_dir(self, task: Task, info: dict | None, save_dir: str) -> Path:
        uploader = safe_filename((info or {}).get("uploader") or "bilibili")
        return Path(save_dir) / "Bilibili" / uploader

    def _translate_error(self, message: str) -> Exception | None:
        lowered = message.lower()
        if any(kw in lowered for kw in _AUTH_KEYWORDS):
            return (
                AuthExpiredError(self.site_id)
                if self.has_cookies()
                else RuntimeError(AUTH_REQUIRED)
            )
        if "blocked by server" in lowered or _HTTP_412_RE.search(lowered):
            return RuntimeError(
                "bilibili가 요청을 일시적으로 차단했습니다(412). "
                "잠시 후 다시 시도하거나, yt-dlp를 최신 버전으로 업데이트해주세요."
            )
        if "geo" in lowered and "restrict" in lowered:
            return RuntimeError(
                "지역 제한된 영상입니다. 중국 외 지역에서는 받을 수 없습니다."
            )
        return None
