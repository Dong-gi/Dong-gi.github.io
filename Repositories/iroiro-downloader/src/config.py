import json
import os
from pathlib import Path

_APP_NAME = "iroiro-downloader"

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
        if not _CONFIG_PATH.exists():
            return
        try:
            with open(_CONFIG_PATH, encoding="utf-8") as f:
                loaded = json.load(f)
        except (OSError, json.JSONDecodeError):
            # 손상된 파일 / I/O 에러는 기본값으로 폴백
            return
        if not isinstance(loaded, dict):
            return
        # 아는 키만 받는다. 지운 기능이 남긴 키나 모르는 키는 무시하고,
        # 다음 save() 때 파일에서도 빠진다.
        for key in self._data:
            if key in loaded:
                self._data[key] = loaded[key]

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

    @property
    def max_concurrent(self) -> int:
        return self._data["max_concurrent"]

    @max_concurrent.setter
    def max_concurrent(self, value: int):
        self._data["max_concurrent"] = value
