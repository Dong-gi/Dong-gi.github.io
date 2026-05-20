"""익스트랙터 공통 유틸리티."""
import re

_UNSAFE_CHARS = re.compile(r'[\\/*?:"<>|]')


def safe_filename(name: str) -> str:
    """Windows에서 안전한 파일/폴더명으로 정리.

    - `\\ / * ? : " < > |` 를 `_` 로 치환
    - 끝의 점/공백 제거 (Windows에서 trailing dot/space 불허)
    - 결과가 비면 `_` 반환
    """
    return _UNSAFE_CHARS.sub("_", name).rstrip(". ") or "_"


class CancelDownload(BaseException):
    """yt-dlp progress_hook 내부에서 stop_event 감지 시 발생.

    `BaseException` 상속이 핵심 — yt-dlp 내부의 `except Exception:` 블록을
    통과해 `download()` 레이어까지 전파되어야 한다. 거기서 잡아 `InterruptedError`로 재포장.
    """
