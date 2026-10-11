"""rhwp(HWP·HWPX 편집기)의 엔진 묶음을 짓는다 — `vendor/editor/assets/engines/rhwp/`(git 무시)로. APK 의 `assets/engines/rhwp/` 이 된다.

판을 고정한다(사용자 승인 2026-10-09):
- 소스: github.com/edwardkim/rhwp 의 태그 v0.8.7 = 커밋 1a76570e…. 알려진 보안 권고 10건이 모두 이 판에서 고쳐졌다(2026-10-09 확인).
  저장소가 3.7 GB 라 필요한 자리만 받는다(깊이 1·blob 은 필요할 때·성긴 체크아웃 — 스튜디오·글꼴·라이선스).
- wasm: npm 의 `@rhwp/core@0.8.7`. 같은 커밋에서 올렸다(레지스트리 메타데이터의 gitHead, SLSA 출처 증명). 받은 것을 아래 sha512 와 견준다.
- 빌드 도구: 스튜디오의 package-lock.json 그대로 `npm ci --ignore-scripts`(설치 스크립트를 돌리지 않는다). npm 캐시까지 `local/editor/rhwp/` 안에 둔다.

원래 빌드와 다른 것(자세한 것은 `tools/editor/rhwp/vite.config.mjs`):
- PWA(서비스 워커·매니페스트)·외부 웹폰트·hwpctrl 플러그인·CanvasKit 을 뺀다. 표본 문서(public/samples)도 뺀다.
- 글꼴은 라이선스가 분명한 것만 싣는다(OFL·GUST — FONTS_ALLOWED). 카페24·행복고딕은 '무료 배포' 라고만 적혀 재배포 조건을 모른다 — 뺀다.
  새 판에 모르는 글꼴이 생기면 멈춘다(보고 정한다).
- 어댑터(`feature/editor/src/main/assets/engines/rhwp/iroiro-rhwp.js`, 우리 코드라 커밋한다 — APK 에서 이 묶음과 합쳐진다)를 부르는 줄을
  index.html 의 `</body>` 앞에 넣는다. 고전 스크립트라 문서를 다 읽은 뒤·스튜디오의 모듈이 돌기 전에 돈다.
- 라이선스를 `licenses/` 에 모은다 — rhwp(MIT)·번들에 든 npm 꾸러미·글꼴(name 표에서 꺼낸 저작권 줄과 OFL 전문).

    python -I tools/editor/build_rhwp.py          # 받기(처음만)·짓기
    python -I tools/editor/build_rhwp.py --clean  # 작업 폴더(local/editor/rhwp)를 지우고 처음부터

표준 라이브러리만 쓴다. git·node(24 이상, npm 포함)가 PATH 에 있어야 한다. 받기는 환경의 프록시 설정을 따른다.
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools" / "editor" / "rhwp"
WORK = ROOT / "local" / "editor" / "rhwp"
SRC = WORK / "src"
STUDIO = SRC / "rhwp-studio"
DIST = WORK / "dist"
LOGS = WORK / "logs"
OUT = ROOT / "vendor" / "editor" / "assets" / "engines" / "rhwp"
ADAPTER = ROOT / "feature" / "editor" / "src" / "main" / "assets" / "engines" / "rhwp" / "iroiro-rhwp.js"

REPO = "https://github.com/edwardkim/rhwp.git"
TAG = "v0.8.7"
COMMIT = "1a76570e833917d15817415a53c09ad61ab3203f"
CORE_URL = "https://registry.npmjs.org/@rhwp/core/-/core-0.8.7.tgz"
CORE_INTEGRITY = "sha512-qgNpY9aclCvy9D7cPnpuplu42kUFZCCPhdjAS5HBso4Z4XeHG3uchYdUleQBoas/zEuwfJ7H4/ulepqdL/LXDA=="

# 성긴 체크아웃의 자리(gitignore 꼴). e2e 시험·표본 문서는 받지 않는다.
SPARSE = [
    "/LICENSE",
    "/THIRD_PARTY_LICENSES.md",
    "/assets/fonts/",
    "/rhwp-studio/",
    "!/rhwp-studio/e2e/",
    "!/rhwp-studio/public/samples/",
]

# 싣는 글꼴(파일 이름의 앞) — 모두 SIL OFL 1.1, LatinModernMath 만 GUST Font License(LPPL 계열, 재배포 허용).
FONTS_ALLOWED = (
    "D2Coding-", "GowunBatang-", "GowunDodum-", "LatinModernMath-", "NanumGothic-", "NanumGothicCoding-", "NanumMyeongjo-",
    "NotoSansKR-", "NotoSerifKR-", "Pretendard-", "SourceHanSerifK-", "SpoqaHanSans-",
)
# 싣지 않는 글꼴 — 라이선스가 '무료 배포' 뿐이다(rhwp 의 assets/fonts/FONTS.md).
FONTS_DENIED = ("Cafe24", "Happiness")


def log(msg: str) -> None:
    print(msg, flush=True)


def run(step: str, args: list[str], cwd: pathlib.Path, env: dict[str, str] | None = None, check: bool = True) -> int:
    """명령 하나 — 출력은 local/editor/rhwp/logs/<step>.log 에, 실패하면 끝 40줄을 보이고 멈춘다."""
    LOGS.mkdir(parents=True, exist_ok=True)
    path = LOGS / f"{step}.log"
    with path.open("w", encoding="utf-8", errors="replace") as f:
        p = subprocess.run(args, cwd=cwd, env=env, stdout=f, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    if p.returncode != 0 and check:
        tail = path.read_text(encoding="utf-8", errors="replace").splitlines()[-40:]
        log("\n".join(tail))
        sys.exit(f"{step} 실패(끝 코드 {p.returncode}) — {path}")
    return p.returncode


def rmtree(path: pathlib.Path) -> None:
    """지운다 — 윈도에서 git 의 객체 파일은 읽기 전용이라 그대로는 지워지지 않는다."""
    def writable_then_retry(func, p, _exc):
        os.chmod(p, 0o700)
        func(p)
    shutil.rmtree(path, onexc=writable_then_retry)


def git(*args: str, step: str = "git", check: bool = True) -> int:
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0")
    return run(step, ["git", "-C", str(SRC), *args], cwd=ROOT, env=env, check=check)


def git_out(*args: str) -> str:
    return subprocess.run(["git", "-C", str(SRC), *args], capture_output=True, text=True, check=True).stdout.strip()


def node_tools() -> tuple[str, str]:
    node = shutil.which("node")
    if not node:
        sys.exit("node 가 PATH 에 없다")
    npm_cli = pathlib.Path(node).resolve().parent / "node_modules" / "npm" / "bin" / "npm-cli.js"
    if not npm_cli.is_file():
        sys.exit(f"npm 을 찾지 못했다: {npm_cli}")
    major = int(subprocess.run([node, "--version"], capture_output=True, text=True, check=True).stdout.strip().lstrip("v").split(".")[0])
    if major < 24:
        sys.exit(f"node 24 이상이 필요하다(지금 {major})")
    return node, str(npm_cli)


# ---------------------------------------------------------------------------------------------------------------------- 받기


def checkout() -> None:
    if (SRC / ".git").is_dir() and git_out("rev-parse", "HEAD") == COMMIT:
        log(f"소스: 이미 {TAG}({COMMIT[:10]})")
        return
    if SRC.exists():
        rmtree(SRC)
    SRC.mkdir(parents=True)
    log(f"소스: {REPO} 의 {TAG} 를 받는다(필요한 자리만)")
    git("init", "-q")
    # 윈도에서 심볼릭 링크를 만들지 않는다 — public/fonts 가 링크인데, 그 자리는 prepare() 가 고른 글꼴로 채운다.
    git("config", "core.symlinks", "false")
    git("config", "core.autocrlf", "false")
    git("remote", "add", "origin", REPO)
    git("sparse-checkout", "set", "--no-cone", *SPARSE, step="git-sparse")
    git("fetch", "--depth", "1", "--filter=blob:none", "origin", f"+refs/tags/{TAG}:refs/tags/{TAG}", step="git-fetch")
    got = git_out("rev-parse", f"refs/tags/{TAG}^{{commit}}")
    if got != COMMIT:
        sys.exit(f"태그 {TAG} 가 다른 커밋을 가리킨다: {got} (고정한 것 {COMMIT}) — 판을 다시 확인할 것")
    git("-c", "advice.detachedHead=false", "checkout", "-q", COMMIT, step="git-checkout")
    if git_out("rev-parse", "HEAD") != COMMIT:
        sys.exit("체크아웃한 커밋이 다르다")


def fetch_core() -> pathlib.Path:
    """@rhwp/core 의 묶음을 받아(이미 있으면 그것을) sha512 를 견준다."""
    dl = WORK / "downloads"
    dl.mkdir(parents=True, exist_ok=True)
    tgz = dl / "rhwp-core-0.8.7.tgz"
    if not tgz.is_file():
        log(f"wasm: {CORE_URL} 를 받는다")
        req = urllib.request.Request(CORE_URL, headers={"User-Agent": "iroiro-viewer-editor-build"})
        with urllib.request.urlopen(req, timeout=120) as r, (dl / "partial").open("wb") as f:
            shutil.copyfileobj(r, f)
        (dl / "partial").replace(tgz)
    algo, want = CORE_INTEGRITY.split("-", 1)
    got = base64.b64encode(hashlib.new(algo, tgz.read_bytes()).digest()).decode()
    if got != want:
        tgz.unlink()
        sys.exit(f"@rhwp/core 묶음의 {algo} 가 다르다 — 지웠다. 받은 것: {got}")
    log("wasm: @rhwp/core 0.8.7 sha512 맞음")
    return tgz


def unpack_core(tgz: pathlib.Path) -> None:
    pkg = SRC / "pkg"
    if pkg.exists():
        shutil.rmtree(pkg)
    pkg.mkdir()
    with tarfile.open(tgz, "r:gz") as t:
        for m in t.getmembers():
            if not m.isfile() or not m.name.startswith("package/"):
                continue
            rel = m.name[len("package/"):]
            if not rel or "/" in rel or rel.startswith("."):
                continue  # 이 꾸러미는 맨 위에 파일 일곱뿐이다
            src = t.extractfile(m)
            (pkg / rel).write_bytes(src.read())
    for need in ("rhwp.js", "rhwp_bg.wasm", "LICENSE"):
        if not (pkg / need).is_file():
            sys.exit(f"@rhwp/core 묶음에 {need} 가 없다")


# ---------------------------------------------------------------------------------------------------------------------- 짓기


def prepare() -> list[str]:
    """작업 폴더를 우리 빌드에 맞춘다 — 글꼴 고르기, 표본 빼기, 설정·대역 복사. 실은 글꼴 이름을 돌려준다."""
    fonts_src = SRC / "assets" / "fonts"
    kept, unknown = [], []
    for f in sorted(fonts_src.glob("*.woff2")):
        if f.name.startswith(FONTS_ALLOWED):
            kept.append(f.name)
        elif not f.name.startswith(FONTS_DENIED):
            unknown.append(f.name)
    if unknown:
        sys.exit("모르는 글꼴이 있다 — 라이선스를 보고 FONTS_ALLOWED·FONTS_DENIED 에 더할 것: " + ", ".join(unknown))
    fonts_dst = STUDIO / "public" / "fonts"
    if fonts_dst.is_dir() and not fonts_dst.is_symlink():
        shutil.rmtree(fonts_dst)
    elif fonts_dst.exists() or fonts_dst.is_symlink():
        fonts_dst.unlink()
    fonts_dst.mkdir()
    for name in kept:
        shutil.copy2(fonts_src / name, fonts_dst / name)

    samples = STUDIO / "public" / "samples"
    if samples.exists():
        shutil.rmtree(samples)

    shutil.copy2(TOOLS / "vite.config.mjs", STUDIO / "vite.iroiro.config.mjs")
    stubs = STUDIO / "iroiro-stubs"
    if stubs.exists():
        shutil.rmtree(stubs)
    shutil.copytree(TOOLS / "stubs", stubs)
    (stubs / "canvaskit.wasm").write_bytes(b"")
    return kept


def npm_install(node: str, npm_cli: str) -> None:
    lock = STUDIO / "package-lock.json"
    stamp = STUDIO / "node_modules" / ".iroiro-lock-sha256"
    digest = hashlib.sha256(lock.read_bytes()).hexdigest()
    if stamp.is_file() and stamp.read_text(encoding="utf-8").strip() == digest:
        log("빌드 도구: 이미 받았다(package-lock.json 같음)")
        return
    log("빌드 도구: npm ci --ignore-scripts (package-lock.json 그대로)")
    args = [node, npm_cli, "ci", "--ignore-scripts", "--no-audit", "--no-fund", "--loglevel=warn", "--cache", str(WORK / "npm-cache"),
            # 프록시 너머로 받다 보면 연결이 끊긴다(2026-10-09 ECONNRESET) — 동시 연결을 줄이고 더 기다려 다시 받는다.
            "--maxsockets=6", "--fetch-retries=5", "--fetch-retry-mintimeout=2000", "--fetch-retry-maxtimeout=60000"]
    for attempt in range(1, 4):
        if run("npm-ci", args, cwd=STUDIO, check=attempt == 3) == 0:
            break
        log(f"npm ci 실패 — 다시({attempt}/3). 받은 것은 캐시에 남는다")
    stamp.write_text(digest, encoding="utf-8")


def npm_audit(node: str, npm_cli: str) -> dict:
    """알려진 취약점 — 빌드를 멈추지는 않는다(빌드 도구의 것이 대부분이다). 번들에 든 꾸러미의 것은 assemble() 이 따로 본다."""
    path = LOGS / "npm-audit.json"
    LOGS.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        subprocess.run([node, npm_cli, "audit", "--json", "--cache", str(WORK / "npm-cache")], cwd=STUDIO, stdout=f,
                       stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def vite_build(node: str) -> None:
    if DIST.exists():
        shutil.rmtree(DIST)
    log("짓기: vite build")
    env = dict(os.environ, IROIRO_RHWP_OUT=str(DIST), NODE_ENV="production")
    vite = STUDIO / "node_modules" / "vite" / "bin" / "vite.js"
    run("vite-build", [node, str(vite), "build", "--config", "vite.iroiro.config.mjs"], cwd=STUDIO, env=env)


# ---------------------------------------------------------------------------------------------------------------------- 싣기


def package_license(pkg_dir: pathlib.Path) -> pathlib.Path | None:
    for name in ("LICENSE", "LICENSE.md", "LICENSE.txt", "license", "LICENCE", "LICENSE-MIT"):
        if (pkg_dir / name).is_file():
            return pkg_dir / name
    return None


def assemble(node: str, kept_fonts: list[str], audit: dict) -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    shutil.copytree(DIST, OUT)

    # public/ 에서 그대로 온 것 가운데 쓰지 않는 것 — rhwp.js 따위는 옛 웹 쪽이 쓰던 사본이다(스튜디오는 번들의 wasm 을 쓴다).
    # print.html 은 남긴다 — 'PDF로 저장' 이 숨은 틀에 그것을 열고 쪽을 그려 넣는다(어댑터가 그 틀의 print() 를 앱의 인쇄로 돌린다).
    for name in ("rhwp.js", "rhwp.d.ts", "rhwp_bg.wasm.d.ts"):
        p = OUT / name
        if p.exists():
            p.unlink()

    bundled = json.loads((OUT / "bundled-packages.json").read_text(encoding="utf-8"))
    (OUT / "bundled-packages.json").unlink()

    # 어댑터를 부르는 줄.
    index = OUT / "index.html"
    html = index.read_text(encoding="utf-8")
    if html.count("</body>") != 1:
        sys.exit("index.html 의 </body> 가 하나가 아니다")
    if "iroiro-rhwp.js" not in html:
        html = html.replace("</body>", '  <script src="/iroiro-rhwp.js"></script>\n</body>')
    # 외부로 가는 주소가 남았는지(CSP 가 어차피 막지만, 남았다면 무언가 빠뜨린 것이다).
    external = sorted(set(re.findall(r"""(?:src|href)=["'](https?://[^"']+)""", html)))
    if external:
        sys.exit("index.html 이 바깥 주소를 부른다: " + ", ".join(external))
    index.write_text(html, encoding="utf-8")
    if not ADAPTER.is_file():
        log(f"주의: 어댑터가 아직 없다({ADAPTER.relative_to(ROOT)}) — 앱을 지으면 쪽이 문서를 열지 못한다")

    # 라이선스.
    lic = OUT / "licenses"
    lic.mkdir()
    shutil.copy2(SRC / "LICENSE", lic / "rhwp-LICENSE.txt")
    shutil.copy2(SRC / "THIRD_PARTY_LICENSES.md", lic / "rhwp-THIRD_PARTY_LICENSES.md")
    shutil.copy2(SRC / "pkg" / "LICENSE", lic / "rhwp-core-LICENSE.txt")
    missing = []
    for name in bundled:
        p = package_license(STUDIO / "node_modules" / name)
        if p:
            shutil.copy2(p, lic / f"npm-{name.replace('/', '__')}-LICENSE.txt")
        else:
            missing.append(name)
    if missing:
        sys.exit("라이선스 글을 찾지 못한 꾸러미: " + ", ".join(missing))
    ofl = (SRC / "assets" / "fonts" / "SourceHanSerifK-OFL.txt").read_text(encoding="utf-8")
    start = ofl.find("This Font Software is licensed under the SIL Open Font License")
    if start < 0:
        sys.exit("SourceHanSerifK-OFL.txt 에서 OFL 전문을 찾지 못했다")
    (lic / "OFL-1.1.txt").write_text(ofl[start:], encoding="utf-8")
    shutil.copy2(SRC / "assets" / "fonts" / "SourceHanSerifK-OFL.txt", lic / "SourceHanSerifK-OFL.txt")
    run("font-notices", [node, str(TOOLS / "font_notices.mjs"), str(OUT / "fonts"), str(lic / "FONTS.txt")], cwd=ROOT)

    # 번들에 든 꾸러미의 알려진 취약점 — 있으면 멈춘다(빌드 도구의 것은 앱에 실리지 않는다).
    vulns = audit.get("vulnerabilities", {}) if isinstance(audit, dict) else {}
    shipped = sorted(n for n in vulns if n in bundled)
    if shipped:
        sys.exit("번들에 든 꾸러미에 알려진 취약점이 있다: " + ", ".join(shipped) + f" — {LOGS / 'npm-audit.json'}")
    tool_vulns = {n: v.get("severity") for n, v in vulns.items()}

    total = 0
    files = []
    for p in sorted(OUT.rglob("*")):
        if p.is_file():
            total += p.stat().st_size
            files.append(p)
    info = [
        "rhwp 엔진 묶음 — tools/editor/build_rhwp.py 가 지었다. 손으로 고치지 않는다.",
        f"지은 때: {time.strftime('%Y-%m-%d %H:%M:%S %z')}",
        f"소스: {REPO} 태그 {TAG} = 커밋 {COMMIT}",
        f"wasm: @rhwp/core 0.8.7 ({CORE_INTEGRITY})",
        f"node: {subprocess.run([node, '--version'], capture_output=True, text=True).stdout.strip()}",
        "번들에 든 npm 꾸러미: " + (", ".join(bundled) if bundled else "없음"),
        "실은 글꼴: " + ", ".join(kept_fonts),
        "빌드 도구의 알려진 취약점(앱에 실리지 않는다): " + (json.dumps(tool_vulns, ensure_ascii=False) if tool_vulns else "없음"),
        f"파일 {len(files)}개, {total:,} 바이트",
    ]
    (OUT / "BUILD-INFO.txt").write_text("\n".join(info) + "\n", encoding="utf-8")
    log("\n".join(info[2:]))
    biggest = sorted(files, key=lambda p: p.stat().st_size, reverse=True)[:8]
    log("큰 파일: " + ", ".join(f"{p.relative_to(OUT).as_posix()} {p.stat().st_size // 1024:,} KiB" for p in biggest))


def main() -> None:
    # -I(격리)로 돌면 PYTHONIOENCODING 이 먹지 않는다 — 윈도 콘솔의 cp949 로 한국어가 깨지지 않게.
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    if "--clean" in sys.argv[1:] and WORK.exists():
        log(f"작업 폴더를 지운다: {WORK}")
        rmtree(WORK)
    node, npm_cli = node_tools()
    checkout()
    unpack_core(fetch_core())
    kept = prepare()
    npm_install(node, npm_cli)
    audit = npm_audit(node, npm_cli)
    vite_build(node)
    assemble(node, kept, audit)
    log(f"끝: {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
