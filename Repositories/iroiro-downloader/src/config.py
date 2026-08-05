import json
import os
from pathlib import Path

import keyring

_APP_NAME = "iroiro-downloader"
_KEYRING_SERVICE = _APP_NAME
_KEYRING_USERNAME_PIXIV = "pixiv_refresh_token"
_KEYRING_USERNAME_BILIBILI = "bilibili_cookies"

_CONFIG_DIR = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming")) / _APP_NAME
_CONFIG_PATH = _CONFIG_DIR / "config.json"
_SESSION_PATH = _CONFIG_DIR / "session.json"
_DEFAULT_SAVE_DIR = str(Path.home() / "Downloads" / "iroiro")


class Config:
    SESSION_PATH: Path = _SESSION_PATH  # 세션 저장 경로 (불변)

    def __init__(self):
        self._data: dict = {
            "save_dir": _DEFAULT_SAVE_DIR,
            "max_concurrent": 3,
        }
        self._load()

    def _load(self):
        if _CONFIG_PATH.exists():
            try:
                with open(_CONFIG_PATH, encoding="utf-8") as f:
                    self._data.update(json.load(f))
            except (OSError, json.JSONDecodeError):
                # 손상된 파일 / I/O 에러는 기본값으로 폴백
                pass

    def save(self):
        _CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with open(_CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(self._data, f, ensure_ascii=False, indent=2)

    @property
    def save_dir(self) -> str:
        return self._data["save_dir"]

    @save_dir.setter
    def save_dir(self, value: str):
        self._data["save_dir"] = value

    # ── 자격 증명 (Windows Credential Manager) ───────────────────────────────
    # 평문 파일(config.json)에는 기록하지 않는다.

    @staticmethod
    def _get_secret(username: str) -> str:
        return keyring.get_password(_KEYRING_SERVICE, username) or ""

    @staticmethod
    def _set_secret(username: str, value: str) -> None:
        if value:
            keyring.set_password(_KEYRING_SERVICE, username, value)
            return
        try:
            keyring.delete_password(_KEYRING_SERVICE, username)
        except keyring.errors.PasswordDeleteError:
            # 저장된 항목이 없으면 삭제 실패 — 이미 원하는 상태이므로 무시
            pass

    @property
    def pixiv_refresh_token(self) -> str:
        return self._get_secret(_KEYRING_USERNAME_PIXIV)

    @pixiv_refresh_token.setter
    def pixiv_refresh_token(self, value: str):
        self._set_secret(_KEYRING_USERNAME_PIXIV, value)

    @property
    def bilibili_cookies(self) -> str:
        """bilibili cookie 헤더 문자열. YouTube와 달리 수명이 길어 영속 저장한다."""
        return self._get_secret(_KEYRING_USERNAME_BILIBILI)

    @bilibili_cookies.setter
    def bilibili_cookies(self, value: str):
        self._set_secret(_KEYRING_USERNAME_BILIBILI, value)

    @property
    def max_concurrent(self) -> int:
        return self._data["max_concurrent"]

    @max_concurrent.setter
    def max_concurrent(self, value: int):
        self._data["max_concurrent"] = value

