import re
import threading
import time
from pathlib import Path

import httpx

from src.auth.pixiv_oauth import APP_HEADERS, AUTH_URL, CLIENT_ID, CLIENT_SECRET
from src.config import Config
from src.extractors._util import safe_filename
from src.extractors.base import AuthExpiredError, BaseExtractor, ProgressCallback
from src.models.task import Task

_ARTWORK_RE = re.compile(r"pixiv\.net/(?:[a-z]{2}/)?artworks/(\d+)")
_USER_RE = re.compile(r"pixiv\.net/(?:[a-z]{2}/)?users/(\d+)")

_API_BASE = "https://app-api.pixiv.net"


class PixivExtractor(BaseExtractor):
    site_id = "pixiv"

    def __init__(self, config: Config):
        self._config = config
        self._access_token = ""
        self._token_expires_at = 0.0
        self._auth_lock = threading.Lock()

    # ------------------------------------------------------------------ auth

    def _ensure_auth(self):
        with self._auth_lock:
            if time.time() < self._token_expires_at - 60:
                return
            token = self._config.pixiv_refresh_token
            if not token:
                raise RuntimeError("설정에서 Pixiv 리프레시 토큰을 입력해주세요.")
            try:
                resp = httpx.post(
                    AUTH_URL,
                    data={
                        "client_id": CLIENT_ID,
                        "client_secret": CLIENT_SECRET,
                        "grant_type": "refresh_token",
                        "refresh_token": token,
                        "include_policy": "true",
                    },
                    headers={"User-Agent": APP_HEADERS["User-Agent"]},
                    timeout=15,
                )
                resp.raise_for_status()
            except httpx.HTTPStatusError:
                raise AuthExpiredError("pixiv")
            data = resp.json()
            self._access_token = data["access_token"]
            self._token_expires_at = time.time() + data["expires_in"]

    def _auth_headers(self) -> dict:
        return {**APP_HEADERS, "Authorization": f"Bearer {self._access_token}"}

    # ----------------------------------------------------------------- helpers

    def _check_stop(self, stop_event: threading.Event | None) -> None:
        if stop_event and stop_event.is_set():
            raise InterruptedError()

    def _api_get(self, path: str, params: dict | None = None) -> dict:
        url = f"{_API_BASE}{path}"
        for attempt in (0, 1):
            resp = httpx.get(url, params=params or {}, headers=self._auth_headers(), timeout=15)
            if resp.status_code == 401 and attempt == 0:
                # access token 만료 — 강제 갱신 후 1회 재시도
                self._token_expires_at = 0.0
                self._ensure_auth()
                continue
            resp.raise_for_status()
            return resp.json()
        raise RuntimeError("unreachable")

    def _image_pairs(self, illust: dict) -> list[tuple[str, str]]:
        """(download_url, filename) 목록 반환."""
        iid = illust["id"]
        title = safe_filename(illust["title"])

        if illust["type"] == "ugoira":
            meta = self._api_get("/v1/ugoira/metadata", {"illust_id": iid})
            url = meta["ugoira_metadata"]["zip_urls"]["medium"]
            return [(url, f"{iid}_{title}_ugoira.zip")]

        if illust["page_count"] == 1:
            url = illust["meta_single_page"]["original_image_url"]
            ext = Path(url).suffix
            return [(url, f"{iid}_{title}{ext}")]

        pairs = []
        for i, page in enumerate(illust["meta_pages"]):
            url = page["image_urls"]["original"]
            ext = Path(url).suffix
            pairs.append((url, f"{iid}_{title}_p{i:03d}{ext}"))
        return pairs

    def _save_image(
        self,
        url: str,
        dest: Path,
        stop_event: threading.Event | None = None,
    ):
        dest.parent.mkdir(parents=True, exist_ok=True)
        try:
            with httpx.stream(
                "GET", url,
                headers={"Referer": "https://www.pixiv.net/"},
                follow_redirects=True,
                timeout=60,
            ) as resp:
                resp.raise_for_status()
                with open(dest, "wb") as f:
                    for chunk in resp.iter_bytes(8192):
                        # 청크마다 중단 신호 확인 — 빠른 반응을 위해
                        if stop_event and stop_event.is_set():
                            raise InterruptedError()
                        f.write(chunk)
        except BaseException:
            # 중단 또는 오류 시 불완전한 파일 제거
            dest.unlink(missing_ok=True)
            raise

    def _download_illust(
        self,
        illust: dict,
        save_dir: Path,
        on_progress: ProgressCallback,
        done: int,
        total: int,
        stop_event: threading.Event | None = None,
    ):
        pairs = self._image_pairs(illust)
        n = len(pairs)
        for i, (url, filename) in enumerate(pairs):
            self._check_stop(stop_event)
            dest = save_dir / filename
            if not dest.exists():
                self._save_image(url, dest, stop_event)
            progress = (done + (i + 1) / n) / total
            # 갤러리: 작품 번호 / 전체 작품 수 / 단일 작품: 파일 번호 / 전체 파일 수
            text = f"{done+1}/{total}" if total > 1 else f"{i+1}/{n}"
            on_progress(progress, text)

    # ----------------------------------------------------------- BaseExtractor

    def can_handle(self, url: str) -> bool:
        return bool(_ARTWORK_RE.search(url) or _USER_RE.search(url))

    def make_task_id(self, url: str) -> str:
        m = _ARTWORK_RE.search(url)
        if m:
            return f"pixiv-artwork-{m.group(1)}"
        m = _USER_RE.search(url)
        if m:
            return f"pixiv-user-{m.group(1)}"
        return super().make_task_id(url)

    def canonical_url(self, url: str) -> str:
        """언어 prefix(`/en/`, `/ja/` 등) 및 쿼리 파라미터를 제거한 표준 URL."""
        m = _ARTWORK_RE.search(url)
        if m:
            return f"https://www.pixiv.net/artworks/{m.group(1)}"
        m = _USER_RE.search(url)
        if m:
            return f"https://www.pixiv.net/users/{m.group(1)}"
        return url

    def download(
        self,
        task: Task,
        save_dir: str,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None = None,
    ) -> str:
        self._ensure_auth()
        base = Path(save_dir)

        artwork_m = _ARTWORK_RE.search(task.url)
        if artwork_m:
            illust_id = artwork_m.group(1)
            on_progress(0.0, "메타데이터 수신 중...")
            self._check_stop(stop_event)
            data = self._api_get("/v1/illust/detail", {"illust_id": illust_id})
            illust = data["illust"]
            artist = safe_filename(illust["user"]["name"])
            dest_dir = base / artist
            task.save_path = str(dest_dir)
            self._download_illust(illust, dest_dir, on_progress, 0, 1, stop_event)
            return illust["title"]

        user_m = _USER_RE.search(task.url)
        if user_m:
            user_id = user_m.group(1)
            on_progress(0.0, "사용자 정보 수신 중...")
            self._check_stop(stop_event)
            user_data = self._api_get("/v1/user/detail", {"user_id": user_id})
            user_name = safe_filename(user_data["user"]["name"])
            dest_dir = base / user_name
            task.save_path = str(dest_dir)

            illusts: list[dict] = []
            next_url: str | None = f"{_API_BASE}/v1/user/illusts"
            params: dict | None = {"user_id": user_id, "type": "illust"}
            while next_url:
                self._check_stop(stop_event)
                resp = httpx.get(
                    next_url,
                    params=params or {},
                    headers=self._auth_headers(),
                    timeout=15,
                )
                resp.raise_for_status()
                page = resp.json()
                illusts.extend(page["illusts"])
                next_url = page.get("next_url")
                params = None

            total = len(illusts)
            on_progress(0.0, f"0/{total}")
            for i, illust in enumerate(illusts):
                self._download_illust(illust, dest_dir, on_progress, i, total, stop_event)
                time.sleep(0.3)

            return user_data["user"]["name"]

        raise ValueError("처리할 수 없는 URL입니다.")
