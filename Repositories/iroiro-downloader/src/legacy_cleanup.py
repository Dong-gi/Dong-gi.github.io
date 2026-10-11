"""지운 기능이 사용자 기기에 남긴 흔적을 기동 때마다 치운다 (옛 흔적 정리).

2026-10-11 에 YouTube 만 남기고 pixiv·hitomi·bilibili·M3U8·MPD 를 지웠다.
코드는 지웠어도 예전 실행이 기기에 남긴 것은 그대로 있을 수 있다.

- ``pixiv://`` 핸들러 등록(HKCU)과 ``%TEMP%/iroiro_pixiv_*`` — Pixiv 로그인 창이
  크래시·강제 종료로 닫히면 남았다. 등록이 남아 있으면 아무 웹 페이지나
  ``pixiv://`` 로 이동시켜 파이썬을 띄울 수 있다.
- 자격 증명 관리자의 Pixiv refresh token 과 bilibili 쿠키 — 더 쓰지 않는 비밀.

모두 '있으면 지우고 없으면 조용히' 다. 무엇이 실패해도 앱 기동을 막지 않는다.
"""
import shutil
import sys
import tempfile
import winreg
from pathlib import Path

try:
    import keyring
    from keyring.errors import PasswordDeleteError
except ImportError:  # requirements.txt 에 있지만, 없어도 앱은 떠야 한다
    keyring = None

# ── pixiv:// 핸들러 (옛 src/auth/pixiv_oauth.py) ────────────────────────────
_SCHEME_KEY = r"Software\Classes\pixiv"
#: 지우는 순서. 하위 키가 남은 키는 DeleteKey 가 지우지 못하므로 안쪽부터.
_SCHEME_SUBKEYS = (r"\shell\open\command", r"\shell\open", r"\shell", "")

#: 옛 판이 %TEMP% 에 만든 파일·폴더의 접두어. 핸들러 명령에도 늘 들어 있었다.
#:   2026-08 판: "<python>" "%TEMP%\iroiro_pixiv_handler.py" "%1"
#:   2026-09 판: "<python>" -c "..." "%TEMP%\iroiro_pixiv_XXX\callback.txt" "%1"
#: 그래서 명령에 이것이 있으면 이 앱의 등록이고, 없으면 남의 것이다.
_OWN_MARKER = "iroiro_pixiv_"

# ── 자격 증명 (옛 src/config.py) ────────────────────────────────────────────
_KEYRING_SERVICE = "iroiro-downloader"
_KEYRING_USERNAMES = ("pixiv_refresh_token", "bilibili_cookies")


def cleanup_legacy_traces() -> None:
    """옛 흔적을 모두 치운다. 앱 기동 때 한 번 부른다."""
    for step in (_remove_pixiv_scheme, _remove_temp_files, _remove_secrets):
        try:
            step()
        except Exception as e:  # 정리가 실패해도 기동은 막지 않는다
            _warn(f"옛 흔적 정리 중 오류({step.__name__}): {e}")


def _remove_pixiv_scheme() -> None:
    """HKCU 의 ``pixiv://`` 등록을 지운다 — 이 앱이 만든 것일 때만."""
    try:
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER, _SCHEME_KEY + _SCHEME_SUBKEYS[0]
        ) as key:
            command, _ = winreg.QueryValueEx(key, "")
    except OSError:
        return  # 등록이 없다 — 거의 언제나 이 경우
    if not isinstance(command, str) or _OWN_MARKER not in command.lower():
        return  # 남의 등록이다
    for sub in _SCHEME_SUBKEYS:
        try:
            winreg.DeleteKey(winreg.HKEY_CURRENT_USER, _SCHEME_KEY + sub)
        except OSError:
            # 남이 덧붙인 하위 키가 있으면 지워지지 않는다. 그대로 둔다
            # (명령이 없는 키는 아무것도 띄우지 못한다).
            pass


def _remove_temp_files() -> None:
    """옛 판이 %TEMP% 에 남긴 콜백 폴더와 핸들러·콜백 파일을 지운다."""
    # 지우면서 훑지 않도록 목록을 먼저 다 읽는다
    for path in list(Path(tempfile.gettempdir()).glob(_OWN_MARKER + "*")):
        try:
            if path.is_dir():
                shutil.rmtree(path, ignore_errors=True)
            else:
                path.unlink(missing_ok=True)
        except OSError:
            pass  # 쓰는 중이거나 권한이 없다 — 다음 기동 때 다시 시도한다


def _remove_secrets() -> None:
    """자격 증명 관리자에 남은 Pixiv refresh token·bilibili 쿠키를 지운다.

    저장한 것이 keyring 이므로 지우는 것도 keyring 으로 한다. Windows 백엔드는
    같은 서비스에 사용자가 둘이면 하나를 ``{username}@{service}`` 로 옮겨 적는데,
    ``delete_password`` 가 두 자리를 모두 지운다 — 그 규칙을 따로 구현하지 않는다.
    """
    if keyring is None:
        return
    for username in _KEYRING_USERNAMES:
        try:
            keyring.delete_password(_KEYRING_SERVICE, username)
        except PasswordDeleteError:
            pass  # 없다 — 이미 바라는 상태
        except Exception as e:  # 백엔드 오류. 다른 이름은 계속 시도한다
            _warn(f"옛 비밀({username})을 지우지 못했습니다. "
                  f"다음 기동 때 다시 시도합니다: {e}")


def _warn(message: str) -> None:
    # pythonw 로 띄우면 sys.stderr 가 None 이다 — print 는 그때 아무것도 안 한다
    print(message, file=sys.stderr)
