"""익스트랙터 공통 유틸리티."""
import re
import time
from pathlib import Path

_UNSAFE_CHARS = re.compile(r'[\\/*?:"<>|]')
_ANSI_RE = re.compile(r"\x1b\[[0-9;]*[mGKHF]")


def safe_filename(name: str) -> str:
    """Windows에서 안전한 파일/폴더명으로 정리.

    - `\\ / * ? : " < > |` 를 `_` 로 치환
    - 끝의 점/공백 제거 (Windows에서 trailing dot/space 불허)
    - 결과가 비면 `_` 반환
    """
    return _UNSAFE_CHARS.sub("_", name).rstrip(". ") or "_"


def clean_message(msg: str) -> str:
    """yt-dlp 등이 출력하는 ANSI 이스케이프 코드 및 앞뒤 공백 제거."""
    return _ANSI_RE.sub("", msg).strip()


def escape_outtmpl(literal: str) -> str:
    """yt-dlp outtmpl에 리터럴 문자열을 넣기 위한 이스케이프.

    outtmpl에서 `%`는 필드 치환의 시작이므로(`%(title)s`), 경로·파일명에 들어 있는
    `%`(예: URL에서 온 `%20`)는 `%%`로 escape해야 그대로 출력된다.
    """
    return literal.replace("%", "%%")


def write_netscape_cookies(cookie_str: str, path: Path, domain: str) -> None:
    """'name=value; ...' 형태의 cookie 헤더 문자열을 Netscape 포맷 파일로 저장.

    yt-dlp의 `cookiefile` 옵션이 이 파일을 `YoutubeDLCookieJar.load()`로 처리하며
    `__Secure-3PAPISID` → `SAPISID` 자동 파생, `_HTTPONLY_PREFIX` 처리 등
    공식 처리 경로를 거치게 된다. 인메모리 `cookiejar.set_cookie()` 주입은
    이 경로를 우회해 extractor가 로그인 상태를 인식하지 못한다.

    :param domain: 쿠키를 적용할 도메인. 하위 도메인 포함을 위해 앞에 점을 붙인 형태
        (예: ``".youtube.com"``, ``".bilibili.com"``).
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
        lines.append(f"{domain}\tTRUE\t/\t{secure}\t{expires}\t{name}\t{value.strip()}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


class CancelDownload(BaseException):
    """yt-dlp progress_hook 내부에서 stop_event 감지 시 발생.

    `BaseException` 상속이 핵심 — yt-dlp 내부의 `except Exception:` 블록을
    통과해 `download()` 레이어까지 전파되어야 한다. 거기서 잡아 `InterruptedError`로 재포장.
    """
