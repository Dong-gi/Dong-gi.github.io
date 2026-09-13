"""
Pixiv OAuth PKCE 흐름 구현.

동작 원리:
1. PKCE code_verifier / code_challenge 생성
2. HKCU 레지스트리에 pixiv:// URI 스킴 핸들러 임시 등록
   - 핸들러는 `python -c` 인라인 한 줄. 콜백 URL을 임시 파일에 기록만 한다.
     **핸들러 스크립트 파일을 만들지 않는다** — 예측 가능한 경로에 `.py`를 두면
     거기에 쓸 수 있는 것이 다음 pixiv:// 이동에서 그대로 실행된다.
   - HKCU이므로 관리자 권한 불필요, HKLM(Pixiv 앱 설치 시)보다 우선 적용
3. 기본 브라우저로 Pixiv 로그인 페이지 오픈
4. 사용자 로그인 완료 → Pixiv가 pixiv://account/login?code=XXX 로 리다이렉트
5. Windows가 등록된 핸들러 실행 → 임시 파일에 URL 기록
6. 앱이 파일 존재를 감지 → code 추출 → refresh_token 교환
   - 토큰 교환의 redirect_uri는 실제 브라우저 콜백 URI(pixiv://account/login)가 아닌
     OAuth 서버에 등록된 값(https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback)을 사용해야 한다.
     두 값이 다른 것은 의도적이며, 잘못 변경하면 HTTP 400(code 1508)이 발생한다.
7. 레지스트리 및 임시 파일 정리
   - 정리는 `PixivLoginDialog.done()` 한 곳에서 한다(Esc·창 닫기·성공·실패 모두 경유).
   - 크래시로 남은 것은 앱 기동 시 `cleanup_stale_scheme()`이 치운다.

상수 노출:
    AUTH_URL, CLIENT_ID, CLIENT_SECRET, APP_HEADERS
        → extractors/pixiv.py 가 access token 갱신 시 import해서 사용.
"""

import hashlib
import secrets
import shutil
import sys
import tempfile
import webbrowser
import winreg
from base64 import urlsafe_b64encode
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import httpx

# ── Pixiv API endpoint / credentials ────────────────────────────────────────

AUTH_URL = "https://oauth.secure.pixiv.net/auth/token"
_LOGIN_URL = "https://app-api.pixiv.net/web/v1/login"
_REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"

# Pixiv 공식 Android 앱(PixivAndroidApp/5.0.234)의 OAuth 자격증명.
#
# - 출처: Pixiv 공식 Android APK 디컴파일을 통해 2017년경 공개된 값.
# - 성격: "비밀"이 아님 — 누구나 APK에서 추출 가능. pixivpy, gallery-dl, PixivUtil2 등
#   거의 모든 서드파티 Pixiv 클라이언트가 동일 값을 재사용.
# - 보안: 이 값만으로는 사용자 계정 접근 불가. 사용자별 refresh_token이 별도 필요.
# - TOS: 공식 API 프로그램이 없어 사실상 표준이지만 명시적 허용은 아님(회색 지대).
#        개인 사용 목적 도구에서는 관례적으로 무방.
# - 무효화: 2017년 이래 무효화된 적 없음. 변경 시 새 값도 곧 공개됨.
CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"

# Pixiv API 호출 시 공통 헤더. 공식 Android 앱의 User-Agent를 그대로 사용.
APP_HEADERS = {
    "User-Agent": "PixivAndroidApp/5.0.234 (Android 11; Pixel 5)",
    "App-OS": "android",
    "App-OS-Version": "11.0",
    "App-Version": "5.0.234",
}

# ── 콜백 핸들러용 임시 파일/레지스트리 경로 ──────────────────────────────────

_REG_KEY = r"Software\Classes\pixiv"
_REG_CMD_KEY = rf"{_REG_KEY}\shell\open\command"

#: 등록할 때마다 새로 만드는 임시 디렉터리 접두어. 고정 경로를 쓰지 않는 것은
#: 콜백 파일 경로를 미리 알 수 없게 하기 위한 것이다.
_TMP_PREFIX = "iroiro_pixiv_"

#: 핸들러가 실행할 코드. **리터럴 고정** — 경로도 URL도 argv로 받으므로
#: 이 문자열에 외부 값이 끼어들 자리가 없다.
_HANDLER_CODE = (
    "import sys,pathlib;"
    "pathlib.Path(sys.argv[1]).write_text(sys.argv[2],encoding='utf-8')"
)

_callback_dir: Path | None = None
_callback_file: Path | None = None


def _b64url(data: bytes) -> str:
    return urlsafe_b64encode(data).rstrip(b"=").decode()


def generate_pkce() -> tuple[str, str]:
    """(code_verifier, code_challenge) 반환."""
    verifier = _b64url(secrets.token_bytes(32))
    challenge = _b64url(hashlib.sha256(verifier.encode()).digest())
    return verifier, challenge


def register_scheme() -> None:
    """pixiv:// URI 스킴 핸들러를 HKCU 레지스트리에 등록.

    핸들러는 `python -c` 인라인이며 콜백 경로와 URL을 모두 argv로 받는다.
    디스크에 스크립트를 두지 않으므로 "그 파일에 쓸 수 있으면 실행된다"는
    문제가 생기지 않는다.
    """
    global _callback_dir, _callback_file

    if getattr(sys, "frozen", False):
        # 프리즈 빌드에서는 sys.executable 이 앱 자신이라 `-c` 가 통하지 않는다.
        # 조용히 망가진 핸들러를 심느니 여기서 멈춘다.
        raise RuntimeError(
            "프리즈된 빌드에서는 pixiv:// 핸들러를 등록할 수 없습니다. "
            "소스에서 실행하거나, 앱 자신을 콜백 인자로 받도록 고쳐야 합니다."
        )

    _cleanup_callback_dir()
    _callback_dir = Path(tempfile.mkdtemp(prefix=_TMP_PREFIX))
    _callback_file = _callback_dir / "callback.txt"

    # 경로에 큰따옴표가 들어갈 수 없으므로(Windows 경로 규칙) 이 인용으로 충분하다.
    cmd = f'"{sys.executable}" -c "{_HANDLER_CODE}" "{_callback_file}" "%1"'

    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, _REG_KEY) as k:
        winreg.SetValue(k, "", winreg.REG_SZ, "URL:pixiv Protocol")
        winreg.SetValueEx(k, "URL Protocol", 0, winreg.REG_SZ, "")
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, _REG_CMD_KEY) as k:
        winreg.SetValue(k, "", winreg.REG_SZ, cmd)


def unregister_scheme() -> None:
    """등록한 pixiv:// 핸들러 및 임시 파일 정리. 여러 번 불러도 안전하다."""
    _delete_reg_keys()
    _cleanup_callback_dir()


def cleanup_stale_scheme() -> None:
    """앱 기동 시, 지난 실행이 크래시로 남긴 등록·임시 디렉터리를 치운다.

    `done()`이 정상 종료를 모두 덮으므로 여기 걸리는 것은 강제 종료뿐이다.
    등록이 남아 있으면 아무 웹 페이지나 pixiv:// 로 이동시켜 파이썬을 띄울 수
    있으므로, 로그인 창을 다시 열지 않더라도 지워야 한다.
    """
    _delete_reg_keys()
    for path in Path(tempfile.gettempdir()).glob(f"{_TMP_PREFIX}*"):
        _remove_path(path)


def _delete_reg_keys() -> None:
    for sub in [r"\shell\open\command", r"\shell\open", r"\shell", ""]:
        try:
            winreg.DeleteKey(winreg.HKEY_CURRENT_USER, _REG_KEY + sub)
        except OSError:
            pass


def _cleanup_callback_dir() -> None:
    global _callback_dir, _callback_file
    if _callback_dir is not None:
        _remove_path(_callback_dir)
    _callback_dir = None
    _callback_file = None


def _remove_path(path: Path) -> None:
    if path.is_dir():
        shutil.rmtree(path, ignore_errors=True)
    else:
        try:
            path.unlink(missing_ok=True)
        except OSError:
            pass


def open_login_browser(code_challenge: str) -> None:
    url = (
        f"{_LOGIN_URL}"
        f"?code_challenge={code_challenge}"
        f"&code_challenge_method=S256"
        f"&client=pixiv-android"
    )
    webbrowser.open(url)


def poll_callback() -> str | None:
    """콜백 파일이 존재하면 pixiv:// URL 반환, 없으면 None."""
    if _callback_file is not None and _callback_file.exists():
        return _callback_file.read_text(encoding="utf-8").strip()
    return None


def extract_code(callback_url: str) -> str:
    params = parse_qs(urlparse(callback_url).query)
    codes = params.get("code", [])
    if not codes:
        raise ValueError(f"OAuth 코드를 찾을 수 없습니다: {callback_url}")
    return codes[0]


def _post_token(data: dict) -> httpx.Response:
    return httpx.post(AUTH_URL, data=data, headers=APP_HEADERS, timeout=15)


def exchange_code(code: str, code_verifier: str) -> str:
    """authorization code → refresh_token 교환."""
    resp = _post_token({
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "code": code,
        "code_verifier": code_verifier,
        "grant_type": "authorization_code",
        "include_policy": "true",
        "redirect_uri": _REDIRECT_URI,
    })

    if not resp.is_success:
        raise RuntimeError(
            f"HTTP {resp.status_code} — {AUTH_URL}\n\n{resp.text}"
        )
    return resp.json()["refresh_token"]
