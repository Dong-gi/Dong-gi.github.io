"""YouTube PO Token 공급자(bgutil-ytdlp-pot-provider) 서버의 위치와 설치.

yt-dlp 쪽 플러그인은 pip 패키지(`bgutil-ytdlp-pot-provider`)로 들어오지만, 실제로 토큰을
만드는 것은 별도의 Node 프로젝트(`server/`)다. 플러그인의 **스크립트 모드**는 yt-dlp가
토큰이 필요할 때마다 `node build/generate_once.js`를 띄우므로 상주 서버가 필요 없다.

서버는 저장소 밖(`%LOCALAPPDATA%`)에 둔다. 이 저장소는 GitHub Pages로 통째로 공개되므로
`node_modules`(수백 MB, 서드파티)를 안에 둘 수 없다.

플러그인은 서버와 주 버전이 다르면 경고하므로, **설치된 플러그인과 같은 버전 태그**의
서버를 버전별 폴더에 빌드한다. 플러그인을 올리면 새 버전 폴더가 비어 있어 미설치로
판정되고, 다시 설치하라는 안내가 나간다.

설치: ``python -m src.pot_provider``
"""
import importlib.metadata
import io
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
from collections.abc import Callable
from pathlib import Path

import httpx

PLUGIN_DIST = "bgutil-ytdlp-pot-provider"
INSTALL_COMMAND = "python -m src.pot_provider"

_ROOT = (
    Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
    / "iroiro-downloader"
    / "pot-provider"
)
_ARCHIVE_URL = (
    "https://github.com/Brainicism/bgutil-ytdlp-pot-provider/archive/refs/tags/{version}.tar.gz"
)
# 플러그인의 Node 스크립트 모드가 찾는 경로 (`<server_home>/build/generate_once.js`)
_SCRIPT = Path("build") / "generate_once.js"
_DOWNLOAD_TIMEOUT = 60.0


class PotProviderSetupError(RuntimeError):
    """서버 설치 실패."""


def plugin_version() -> str | None:
    """설치된 yt-dlp 플러그인 버전. 플러그인이 없으면 None."""
    try:
        return importlib.metadata.version(PLUGIN_DIST)
    except importlib.metadata.PackageNotFoundError:
        return None


def server_home() -> Path | None:
    """플러그인 버전에 맞는 서버 폴더. 플러그인이 없으면 None (존재 여부는 따지지 않음)."""
    version = plugin_version()
    return _ROOT / version / "server" if version else None


def unavailable_reason() -> str | None:
    """준비되지 않은 이유. 준비됐으면 None.

    오류 메시지에 그대로 실어 보낸다 — 어느 경로를 확인했는지 보여야 사용자 환경에서
    원인을 가를 수 있다.
    """
    version = plugin_version()
    if version is None:
        return f"yt-dlp 플러그인 `{PLUGIN_DIST}` 없음 (실행 중인 파이썬: {sys.executable})"
    script = _ROOT / version / "server" / _SCRIPT
    if not script.is_file():
        return f"플러그인 {version}에 맞는 서버 없음: {script}"
    return None


def is_ready() -> bool:
    """플러그인과 같은 버전의 서버가 빌드되어 있는가."""
    return unavailable_reason() is None


def ytdlp_extractor_args() -> dict[str, dict[str, list[str]]]:
    """yt-dlp `extractor_args`에 병합할 항목. 준비되지 않았으면 빈 dict.

    빈 dict이어도 yt-dlp는 그대로 동작한다 — PO Token이 필요한 클라이언트의 포맷만 빠진다.
    """
    home = server_home()
    if home is None or not is_ready():
        return {}
    return {"youtubepot-bgutilscript": {"server_home": [str(home)]}}


def install(log: Callable[[str], None] = print) -> Path:
    """플러그인 버전과 같은 태그의 서버 소스를 받아 빌드한다. 이미 있으면 그대로 둔다.

    임시 폴더에서 빌드를 끝낸 뒤 옮기므로, 도중에 실패해도 반쯤 빌드된 폴더가
    '준비됨'으로 판정되지 않는다.
    """
    version = plugin_version()
    if version is None:
        raise PotProviderSetupError(
            f"yt-dlp 플러그인 `{PLUGIN_DIST}`가 설치되어 있지 않습니다. "
            "먼저 `pip install -r requirements.txt`를 실행하세요."
        )
    home = _ROOT / version / "server"
    if (home / _SCRIPT).is_file():
        log(f"이미 설치되어 있습니다: {home}")
        return home

    node = _require_exe("node")
    npm = _require_exe("npm")

    _ROOT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=_ROOT, prefix=".build-") as tmp:
        work = Path(tmp)
        log(f"{PLUGIN_DIST} {version} 소스 내려받는 중...")
        server = _extract_server(_download_archive(version), work)

        log("의존성 설치 중 (npm ci)...")
        _run([npm, "ci", "--no-audit", "--no-fund"], cwd=server)

        log("빌드 중 (tsc)...")
        _run([node, str(Path("node_modules") / "typescript" / "bin" / "tsc")], cwd=server)
        if not (server / _SCRIPT).is_file():
            raise PotProviderSetupError(f"빌드 결과에 {_SCRIPT.as_posix()}가 없습니다.")

        if home.exists():
            shutil.rmtree(home)  # 스크립트 없이 남은 이전 실패분
        home.parent.mkdir(parents=True, exist_ok=True)
        server.rename(home)

    log(f"설치 완료: {home}")
    return home


def _require_exe(name: str) -> str:
    path = shutil.which(name)
    if path is None:
        raise PotProviderSetupError(
            f"`{name}`을 찾을 수 없습니다. Node.js 22 이상을 설치하고 PATH에 추가하세요."
        )
    return path


def _download_archive(version: str) -> bytes:
    url = _ARCHIVE_URL.format(version=version)
    try:
        response = httpx.get(url, follow_redirects=True, timeout=_DOWNLOAD_TIMEOUT)
        response.raise_for_status()
    except httpx.HTTPError as e:
        raise PotProviderSetupError(f"소스를 내려받지 못했습니다: {url} ({e})") from e
    return response.content


def _extract_server(archive: bytes, dest: Path) -> Path:
    """tar.gz에서 `<최상위>/server/` 하위만 풀어 그 경로를 반환."""
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as tar:
        members = [m for m in tar.getmembers() if _is_server_member(m.name)]
        if not members:
            raise PotProviderSetupError("소스 묶음에 server/ 폴더가 없습니다.")
        tar.extractall(dest, members=members, filter="data")
    top = members[0].name.split("/", 1)[0]
    return dest / top / "server"


def _is_server_member(name: str) -> bool:
    parts = name.split("/")
    return len(parts) >= 2 and parts[1] == "server"


def _run(cmd: list[str], cwd: Path) -> None:
    try:
        subprocess.run(cmd, cwd=cwd, check=True)
    except subprocess.CalledProcessError as e:
        raise PotProviderSetupError(
            f"명령 실패 (종료 코드 {e.returncode}): {' '.join(cmd)}"
        ) from e


def main() -> int:
    try:
        install()
    except PotProviderSetupError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
