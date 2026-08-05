"""
Pixiv OAuth PKCE 흐름 구현.

동작 원리:
1. PKCE code_verifier / code_challenge 생성
2. HKCU 레지스트리에 pixiv:// URI 스킴 핸들러 임시 등록
   - 핸들러는 콜백 URL을 임시 파일에 기록하는 최소 Python 스크립트
   - HKCU이므로 관리자 권한 불필요, HKLM(Pixiv 앱 설치 시)보다 우선 적용
3. 기본 브라우저로 Pixiv 로그인 페이지 오픈
4. 사용자 로그인 완료 → Pixiv가 pixiv://account/login?code=XXX 로 리다이렉트
5. Windows가 등록된 핸들러 실행 → 임시 파일에 URL 기록
6. 앱이 파일 존재를 감지 → code 추출 → refresh_token 교환
   - 토큰 교환의 redirect_uri는 실제 브라우저 콜백 URI(pixiv://account/login)가 아닌
     OAuth 서버에 등록된 값(https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback)을 사용해야 한다.
     두 값이 다른 것은 의도적이며, 잘못 변경하면 HTTP 400(code 1508)이 발생한다.
7. 레지스트리 및 임시 파일 정리

상수 노출:
    AUTH_URL, CLIENT_ID, CLIENT_SECRET, APP_HEADERS
        → extractors/pixiv.py 가 access token 갱신 시 import해서 사용.
"""

import hashlib
import secrets
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

_CALLBACK_FILE = Path(tempfile.gettempdir()) / "iroiro_pixiv_callback.txt"
_HANDLER_SCRIPT = Path(tempfile.gettempdir()) / "iroiro_pixiv_handler.py"
_REG_KEY = r"Software\Classes\pixiv"
_REG_CMD_KEY = rf"{_REG_KEY}\shell\open\command"


def _b64url(data: bytes) -> str:
    return urlsafe_b64encode(data).rstrip(b"=").decode()


def generate_pkce() -> tuple[str, str]:
    """(code_verifier, code_challenge) 반환."""
    verifier = _b64url(secrets.token_bytes(32))
    challenge = _b64url(hashlib.sha256(verifier.encode()).digest())
    return verifier, challenge


def register_scheme() -> None:
    """pixiv:// URI 스킴 핸들러를 HKCU 레지스트리에 등록."""
    _CALLBACK_FILE.unlink(missing_ok=True)

    _HANDLER_SCRIPT.write_text(
        "import sys, pathlib\n"
        f'pathlib.Path(r"{_CALLBACK_FILE}").write_text(sys.argv[1], encoding="utf-8")\n',
        encoding="utf-8",
    )

    cmd = f'"{sys.executable}" "{_HANDLER_SCRIPT}" "%1"'

    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, _REG_KEY) as k:
        winreg.SetValue(k, "", winreg.REG_SZ, "URL:pixiv Protocol")
        winreg.SetValueEx(k, "URL Protocol", 0, winreg.REG_SZ, "")
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, _REG_CMD_KEY) as k:
        winreg.SetValue(k, "", winreg.REG_SZ, cmd)


def unregister_scheme() -> None:
    """등록한 pixiv:// 핸들러 및 임시 파일 정리."""
    for sub in [r"\shell\open\command", r"\shell\open", r"\shell", ""]:
        try:
            winreg.DeleteKey(winreg.HKEY_CURRENT_USER, _REG_KEY + sub)
        except OSError:
            pass
    _CALLBACK_FILE.unlink(missing_ok=True)
    _HANDLER_SCRIPT.unlink(missing_ok=True)


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
    if _CALLBACK_FILE.exists():
        return _CALLBACK_FILE.read_text(encoding="utf-8").strip()
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
