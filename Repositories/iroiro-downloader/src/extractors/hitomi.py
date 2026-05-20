"""hitomi.la 갤러리 다운로드.

이미지 CDN URL은 ``ltn.gold-usergeneratedcontent.net``의 ``gg.js``에 정의된
``b`` / ``m`` / ``s`` 로직으로 계산한다.

- ``s(hash)``: 해시 끝 ``(..)(.)$`` 두 그룹을 ``last + prev`` 순으로 이어 16진수
  파싱 → 정수 ``g``.
- ``m(g)``: switch-case로 특정 ``g`` 값만 0(또는 다른 값)을 반환하고 기본은 1.
  결과가 서브도메인 인덱스로 쓰여 ``a{1 + m(g)}``(예: ``a1`` / ``a2``)가 된다.
- ``b``: 경로 prefix(예: ``1779015602/``).

이미지 포맷은 갤러리 메타데이터의 ``hasavif`` / ``hasjxl`` / ``haswebp`` 플래그를
따른다(우선순위 동순). CDN은 원본 jpg를 제공하지 않으며 메타데이터가 가리키는
포맷만 200을 반환한다.
"""
import json
import re
import threading
from pathlib import Path

import httpx

from src.config import Config
from src.extractors._util import safe_filename
from src.extractors.base import BaseExtractor, ProgressCallback
from src.models.task import Task

_GALLERY_RE = re.compile(r"hitomi\.la/[^?#]*?(\d+)\.html", re.IGNORECASE)

_LTN_BASE = "https://ltn.gold-usergeneratedcontent.net"
_GG_URL = f"{_LTN_BASE}/gg.js"
_REFERER = "https://hitomi.la/"
_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Safari/537.36"
)
_HEADERS = {"Referer": _REFERER, "User-Agent": _USER_AGENT}

_GALLERYINFO_PREFIX = "var galleryinfo = "

# gg.js 파싱 — 구조 변동에 강하도록 case 블록을 일반화해 추출
_GG_B_RE = re.compile(r"b\s*:\s*'([^']*)'")
_GG_DEFAULT_O_RE = re.compile(r"var\s+o\s*=\s*(\d+)\s*;")
_GG_CASE_BLOCK_RE = re.compile(
    r"((?:case\s+\d+\s*:\s*)+)o\s*=\s*(\d+)\s*;\s*break\s*;"
)
_GG_CASE_N_RE = re.compile(r"case\s+(\d+)\s*:")
_HASH_TAIL_RE = re.compile(r"(..)(.)$")

# 갤러리별 폴더명에 사용할 제목 최대 길이 — Windows MAX_PATH(260) 여유 확보
_MAX_TITLE_LEN = 80


class _Gg:
    """gg.js에서 추출한 ``b`` / ``m`` / ``s`` 로직."""

    def __init__(self, b: str, overrides: dict[int, int], default_o: int) -> None:
        self.b = b
        self._overrides = overrides
        self._default_o = default_o

    def m(self, g: int) -> int:
        return self._overrides.get(g, self._default_o)

    @staticmethod
    def s(hash_: str) -> int:
        match = _HASH_TAIL_RE.search(hash_)
        if not match:
            raise ValueError(f"Invalid hash: {hash_!r}")
        return int(match.group(2) + match.group(1), 16)


class HitomiExtractor(BaseExtractor):
    site_id = "hitomi"

    def __init__(self, config: Config) -> None:
        self._config = config

    # ----------------------------------------------------------------- helpers

    @staticmethod
    def _extract_id(url: str) -> str | None:
        match = _GALLERY_RE.search(url)
        return match.group(1) if match else None

    @staticmethod
    def _parse_gg(js: str) -> _Gg:
        b_match = _GG_B_RE.search(js)
        if not b_match:
            raise RuntimeError("gg.js에서 b 값을 찾을 수 없습니다.")
        default_match = _GG_DEFAULT_O_RE.search(js)
        if not default_match:
            raise RuntimeError("gg.js에서 기본 o 값을 찾을 수 없습니다.")
        overrides: dict[int, int] = {}
        for block in _GG_CASE_BLOCK_RE.finditer(js):
            value = int(block.group(2))
            for case_match in _GG_CASE_N_RE.finditer(block.group(1)):
                overrides[int(case_match.group(1))] = value
        return _Gg(b_match.group(1), overrides, int(default_match.group(1)))

    @staticmethod
    def _choose_ext(file_entry: dict) -> str:
        """메타데이터 플래그에 따라 CDN에서 사용 가능한 확장자 선택."""
        if file_entry.get("hasavif"):
            return "avif"
        if file_entry.get("hasjxl"):
            return "jxl"
        if file_entry.get("haswebp"):
            return "webp"
        return Path(file_entry.get("name", "")).suffix.lstrip(".").lower() or "jpg"

    @staticmethod
    def _image_url(hash_: str, ext: str, gg: _Gg) -> str:
        g = gg.s(hash_)
        subdomain = f"a{1 + gg.m(g)}"
        return f"https://{subdomain}.gold-usergeneratedcontent.net/{gg.b}{g}/{hash_}.{ext}"

    @staticmethod
    def _gallery_label(info: dict) -> str:
        """저장 폴더에 쓸 작가/그룹 라벨. 둘 다 없으면 ``unknown``."""
        artists = info.get("artists") or []
        names = [a["artist"] for a in artists if a.get("artist")]
        if names:
            return ", ".join(names)
        groups = info.get("groups") or []
        names = [g["group"] for g in groups if g.get("group")]
        if names:
            return ", ".join(names)
        return "unknown"

    @staticmethod
    def _check_stop(stop_event: threading.Event | None) -> None:
        if stop_event and stop_event.is_set():
            raise InterruptedError()

    def _fetch_gallery_info(self, gallery_id: str) -> dict:
        url = f"{_LTN_BASE}/galleries/{gallery_id}.js"
        resp = httpx.get(url, headers=_HEADERS, timeout=15)
        resp.raise_for_status()
        text = resp.text.strip()
        if text.startswith(_GALLERYINFO_PREFIX):
            text = text[len(_GALLERYINFO_PREFIX):]
        text = text.rstrip(";").strip()
        return json.loads(text)

    def _fetch_gg(self) -> _Gg:
        resp = httpx.get(_GG_URL, headers=_HEADERS, timeout=15)
        resp.raise_for_status()
        return self._parse_gg(resp.text)

    def _save_image(
        self,
        url: str,
        dest: Path,
        stop_event: threading.Event | None,
    ) -> None:
        dest.parent.mkdir(parents=True, exist_ok=True)
        try:
            with httpx.stream(
                "GET",
                url,
                headers=_HEADERS,
                follow_redirects=True,
                timeout=60,
            ) as resp:
                resp.raise_for_status()
                with open(dest, "wb") as f:
                    for chunk in resp.iter_bytes(8192):
                        if stop_event and stop_event.is_set():
                            raise InterruptedError()
                        f.write(chunk)
        except BaseException:
            dest.unlink(missing_ok=True)
            raise

    # ----------------------------------------------------------- BaseExtractor

    def can_handle(self, url: str) -> bool:
        return "hitomi.la" in url.lower() and self._extract_id(url) is not None

    def make_task_id(self, url: str) -> str:
        gallery_id = self._extract_id(url)
        if gallery_id:
            return f"{self.site_id}-gallery-{gallery_id}"
        return super().make_task_id(url)

    def canonical_url(self, url: str) -> str:
        """ID만 보존한 reader URL로 정규화. 다양한 진입 경로(/manga, /doujinshi,
        /reader, /galleries, ...)와 ``#page-`` 프래그먼트를 동일 작업으로 합친다.
        """
        gallery_id = self._extract_id(url)
        if gallery_id:
            return f"https://hitomi.la/reader/{gallery_id}.html"
        return url

    def download(
        self,
        task: Task,
        save_dir: str,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None = None,
    ) -> str:
        gallery_id = self._extract_id(task.url)
        if not gallery_id:
            raise ValueError("hitomi.la URL에서 갤러리 ID를 찾을 수 없습니다.")

        on_progress(0.0, "메타데이터 수신 중...")
        self._check_stop(stop_event)
        info = self._fetch_gallery_info(gallery_id)

        self._check_stop(stop_event)
        gg = self._fetch_gg()

        files = info.get("files") or []
        total = len(files)
        if total == 0:
            raise RuntimeError("갤러리에 파일이 없습니다.")

        title = info.get("title") or gallery_id
        # 잘라낸 뒤 safe_filename — 절단 경계의 trailing space/dot까지 제거되도록 순서 주의
        folder_name = safe_filename(f"{gallery_id}_{title}"[:_MAX_TITLE_LEN])
        label = safe_filename(self._gallery_label(info))
        dest_dir = Path(save_dir) / "Hitomi" / label / folder_name
        task.save_path = str(dest_dir)

        on_progress(0.0, f"0/{total}")
        for i, file_entry in enumerate(files):
            self._check_stop(stop_event)
            hash_ = file_entry.get("hash")
            name = file_entry.get("name") or f"{i + 1:03d}"
            if not hash_:
                raise RuntimeError(f"파일 {name}의 해시가 없습니다.")
            ext = self._choose_ext(file_entry)
            stem = safe_filename(Path(name).stem) or f"{i + 1:03d}"
            dest = dest_dir / f"{stem}.{ext}"
            if not dest.exists():
                url = self._image_url(hash_, ext, gg)
                self._save_image(url, dest, stop_event)
            on_progress((i + 1) / total, f"{i + 1}/{total}")

        return title
