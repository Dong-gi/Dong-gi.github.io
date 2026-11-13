"""편집기 엔진(rhwp·ONLYOFFICE)에 새 판이 나왔는지 본다 — 받지도 짓지도 않는다(묻는 것은 작은 JSON·글 넷).

지금 고정한 판은 두 빌드 스크립트(`build_rhwp.py`·`build_onlyoffice.py`)에서 읽는다(import 하지 않고 글로 — 스크립트가 바뀌면 여기를 고칠 필요가
없게). 견주는 곳:
- rhwp — npm 의 `@rhwp/core` 최신 판(dist-tags.latest). 소스 태그는 같은 판 이름(v<판>)이다.
- ONLYOFFICE — CryptPad 의 설치 스크립트(cryptpad/cryptpad main 의 install-office.sh)가 적은 편집기(v9 줄)·x2t 의 태그와 SHA-512. 우리 빌드는
  그 해시와 견주므로, CryptPad 가 새 해시를 적기 전에는 올리지 않는다. 두 릴리스 저장소의 최신 태그도 함께 보인다(해시가 아직 없는 판).

    python -I tools/editor/check_updates.py

끝 코드: 0 = 새 판 없음, 10 = 새 판 있음, 1 = 묻지 못했다. 올리는 절차는 iroiro-viewer 의 CLAUDE.md('엔진 판').
"""

from __future__ import annotations

import json
import pathlib
import re
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
RHWP_SCRIPT = ROOT / "tools" / "editor" / "build_rhwp.py"
OO_SCRIPT = ROOT / "tools" / "editor" / "build_onlyoffice.py"

NPM_URL = "https://registry.npmjs.org/@rhwp/core"
INSTALL_SCRIPT_URL = "https://raw.githubusercontent.com/cryptpad/cryptpad/main/install-office.sh"
RELEASE_URL = "https://api.github.com/repos/{repo}/releases/latest"
# 지어 보고 되돌린 rhwp 판 — 새 판으로 세지 않고 까닭만 보인다(까닭의 자세한 것은 build_rhwp.py 머리말).
RHWP_SKIPPED = {
    "0.9.0": "HYhwpEQ 수식의 숫자·기호가 네모로 나온다(2026-11-14 되돌림)",
}
TIMEOUT_S = 20
MAX_BYTES = 4 * 1024 * 1024


def fetch(url: str, accept: str | None = None) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "iroiro-check-updates"})
    if accept:
        request.add_header("Accept", accept)
    with urllib.request.urlopen(request, timeout=TIMEOUT_S) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError(f"응답이 너무 크다: {url}")
    return data


def pinned(path: pathlib.Path, name: str) -> str:
    match = re.search(rf'^{name} = "([^"]+)"', path.read_text(encoding="utf-8"), re.M)
    if not match:
        sys.exit(f"{path.name} 에서 {name} 을 찾지 못했다 — 이 도구를 고칠 것")
    return match.group(1)


def version_key(text: str) -> tuple[int, ...]:
    """'v9.3.2+3'·'0.9.0' → 견줄 수 있는 수의 줄. 숫자가 아닌 꼬리(-rc 등)는 버린다."""
    return tuple(int(part) for part in re.findall(r"\d+", text.lstrip("v")))


def check_rhwp() -> bool:
    ours = pinned(RHWP_SCRIPT, "VERSION")
    meta = json.loads(fetch(NPM_URL, accept="application/vnd.npm.install-v1+json"))
    latest = meta["dist-tags"]["latest"]
    skipped = RHWP_SKIPPED.get(latest)
    newer = version_key(latest) > version_key(ours) and skipped is None
    print(f"rhwp        고정 {ours} · npm 최신 {latest}" + (" ← 새 판" if newer else "")
          + (f" (되돌린 판 — {skipped})" if skipped else ""))
    if newer:
        integrity = meta["versions"].get(latest, {}).get("dist", {}).get("integrity", "?")
        print(f"            CORE_INTEGRITY 후보 {integrity}")
        print("            소스 커밋은 태그 v" + latest + " 에서 확인하고(레지스트리의 gitHead 와 같은지), 수식·옛한글·큰 HWP 를 먼저 본다")
    return newer


def check_onlyoffice() -> bool:
    ours = pinned(OO_SCRIPT, "TAG")
    our_editor = pinned(OO_SCRIPT, "EDITOR_SHA512")
    our_x2t = pinned(OO_SCRIPT, "X2T_SHA512")
    script = fetch(INSTALL_SCRIPT_URL).decode("utf-8", "replace")
    editor = re.findall(r"install_version\s+v\d+\s+(\S+)\s+([0-9a-f]{128})", script)
    x2t = re.findall(r"install_x2t\s+(\S+)\s+([0-9a-f]{128})", script)
    if not editor or not x2t:
        raise ValueError("CryptPad 설치 스크립트의 꼴이 바뀌었다 — install_version·install_x2t 줄을 찾지 못했다")
    editor_tag, editor_sha = max(editor, key=lambda item: version_key(item[0]))
    x2t_tag, x2t_sha = max(x2t, key=lambda item: version_key(item[0]))
    newer = False
    for label, tag, sha, our_sha in (("편집기", editor_tag, editor_sha, our_editor), ("x2t", x2t_tag, x2t_sha, our_x2t)):
        if tag == ours:
            state = "같다" if sha == our_sha else "같은 태그인데 해시가 다르다 — 확인할 것"
        elif version_key(tag) > version_key(ours):
            state = "← 새 판"
            newer = True
        else:
            state = "CryptPad 가 더 옛 판을 적었다"
        print(f"ONLYOFFICE  {label:<5} 고정 {ours} · CryptPad 설치 스크립트 {tag} {state}")
        if state == "← 새 판":
            print(f"            SHA-512 {sha}")
    for repo in ("cryptpad/onlyoffice-editor", "cryptpad/onlyoffice-x2t-wasm"):
        tag = json.loads(fetch(RELEASE_URL.format(repo=repo), accept="application/vnd.github+json")).get("tag_name", "?")
        note = " (CryptPad 가 아직 해시를 적지 않았다 — 기다린다)" if version_key(tag) > version_key(ours) and tag not in (editor_tag, x2t_tag) else ""
        print(f"            {repo} 의 최신 릴리스 {tag}{note}")
    return newer


def main() -> int:
    found = False
    try:
        found |= check_rhwp()
        found |= check_onlyoffice()
    except (OSError, ValueError, KeyError) as error:
        print(f"묻지 못했다: {error}", file=sys.stderr)
        return 1
    return 10 if found else 0


if __name__ == "__main__":
    sys.exit(main())
