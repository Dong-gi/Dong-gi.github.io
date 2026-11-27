"""WebTether(Windows 테더 연결 도우미)를 짓는다.

    python -I build.py [--zip 받아둔.zip] [--deploy 복사할_곳.exe] [--no-api]
    python -I build.py --check                 최신 판·asset digest·보안 권고를 보여 주기만 한다
    python -I build.py --update [--deploy …]   고정 판을 최신 판으로 바꾸고(받은 zip 을 API digest 와 대조) 다시 짓는다

1. 판을 고정한 tun2proxy 릴리스 zip 을 받는다(이미 있으면 다시 받지 않는다). SHA-256 을 아래 값과 견주고,
   처음 받을 때는 GitHub API 의 asset digest 와도 견준다(닿지 못하면 경고만).
2. zip 에서 본체(tun2proxy-bin.exe)와 wintun.dll 만 꺼내 각각의 SHA-256 을 견준다.
   wintun.dll 의 Authenticode 서명이 유효하고 서명자가 WireGuard LLC 인지 본다.
3. 둘을 gzip 으로 줄여 exe 의 자원으로 싣고, 해시를 박은 Embedded.cs 를 지어 csc(C# 5, .NET Framework 4.8)로 짓는다.
   out/WebTether.exe       실제 판 — 매니페스트 asInvoker(트레이는 보통 권한, 서비스는 SCM 이 LocalSystem 으로, 설치·설정은 runas)
   out/WebTether-test.exe  시험 판 — asInvoker, 늘 dry-run(tun2proxy 를 띄우지 않는다)
   out/WebTether-tests.exe 시험(콘솔) · out/ctrlc-child.exe 시험이 띄우는 Ctrl+C 받이

받은 것과 지은 것은 모두 out/ 아래다(git 무시 — 공개 저장소에 남의 실행 파일·완성 exe 를 올리지 않는다).
아무것도 설치하지 않는다. 외부 파이썬 패키지를 쓰지 않는다.
"""

import argparse
import gzip
import hashlib
import io
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"

# ---- 고정한 판 (2026-11-21 확인) ----
T2P_TAG = "v0.8.7"
T2P_VERSION = "0.8.7"
T2P_ASSET = "tun2proxy-x86_64-pc-windows-msvc.zip"
T2P_URL = f"https://github.com/tun2proxy/tun2proxy/releases/download/{T2P_TAG}/{T2P_ASSET}"
T2P_API = f"https://api.github.com/repos/tun2proxy/tun2proxy/releases/tags/{T2P_TAG}"
T2P_ZIP_SHA256 = "801396ff46776aac4d9a6660a2972dbeb70b68c90a95a38289126c756298f863"
FILES = {
    "tun2proxy-bin.exe": "dee4f05420838f69b0c90d910bba8a2bf0416ef217d1032d292c12536fe351eb",
    "wintun.dll": "e5da8447dc2c320edc0fc52fa01885c103de8c118481f683643cacc3220dafce",
}
ZIP_MAX = 64 * 1024 * 1024
# 문제가 있어 건너뛰는 판 — --check·--update 가 새 판으로 치지 않고, 도우미도 알리지 않는다(Embedded.SkippedVersions).
# 꼴: "0.8.9": "까닭(날짜)"
T2P_SKIPPED: dict[str, str] = {
}
T2P_LATEST_API = "https://api.github.com/repos/tun2proxy/tun2proxy/releases/latest"
ADVISORY_APIS = [
    "https://api.github.com/advisories?ecosystem=rust&affects=tun2proxy&per_page=100",
    "https://api.github.com/repos/tun2proxy/tun2proxy/security-advisories?state=published&per_page=100",
]

WINDIR = Path(os.environ.get("SystemRoot", r"C:\Windows"))
CSC = WINDIR / "Microsoft.NET" / "Framework64" / "v4.0.30319" / "csc.exe"
POWERSHELL = WINDIR / "System32" / "WindowsPowerShell" / "v1.0" / "powershell.exe"
REFS = ["System.dll", "System.Core.dll", "System.Drawing.dll", "System.Windows.Forms.dll", "System.Xml.dll", "System.Management.dll",
        "System.Web.Extensions.dll", "System.ServiceProcess.dll"]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fail(msg: str) -> None:
    print("실패: " + msg, file=sys.stderr)
    sys.exit(1)


def fetch(url: str, limit: int, accept: str | None = None) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "webtether-build"})
    if accept:
        req.add_header("Accept", accept)
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read(limit + 1)
    if len(data) > limit:
        fail(f"{url} 가 {limit} 바이트를 넘습니다")
    return data


def api_digest() -> str | None:
    try:
        rel = json.loads(fetch(T2P_API, 4 * 1024 * 1024, "application/vnd.github+json"))
    except Exception as e:  # noqa: BLE001 — 닿지 못하면 경고만
        print(f"경고: GitHub API 에 닿지 못했습니다({e}) — 고정한 해시로만 확인합니다")
        return None
    for a in rel.get("assets", []):
        if a.get("name") == T2P_ASSET:
            d = a.get("digest") or ""
            return d.split(":", 1)[1] if d.startswith("sha256:") else None
    return None


def get_zip(local: str | None, use_api: bool) -> bytes:
    dl = OUT / "dl" / T2P_ASSET
    if local:
        data = Path(local).read_bytes()
    elif dl.exists():
        data = dl.read_bytes()
    else:
        print(f"받는 중: {T2P_URL}")
        data = fetch(T2P_URL, ZIP_MAX)
        if use_api:
            d = api_digest()
            if d is not None and d != T2P_ZIP_SHA256:
                fail(f"GitHub API 의 digest({d})가 고정한 값과 다릅니다")
            if d is not None:
                print("GitHub API 의 asset digest 와 같습니다")
    got = sha256(data)
    if got != T2P_ZIP_SHA256:
        fail(f"{T2P_ASSET} 의 SHA-256 이 다릅니다: {got}")
    dl.parent.mkdir(parents=True, exist_ok=True)
    if not dl.exists():
        dl.write_bytes(data)
    print(f"{T2P_ASSET} SHA-256 확인: {got}")
    return data


def extract(data: bytes) -> dict[str, bytes]:
    out = {}
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name, want in FILES.items():
            info = z.getinfo(name)  # 폴더 없이 그 이름만
            if info.file_size > ZIP_MAX:
                fail(f"{name} 이 너무 큽니다")
            b = z.read(info)
            if sha256(b) != want:
                fail(f"{name} 의 SHA-256 이 다릅니다: {sha256(b)}")
            out[name] = b
    bindir = OUT / "bin"
    bindir.mkdir(parents=True, exist_ok=True)
    for name, b in out.items():
        (bindir / name).write_bytes(b)
    return out


def check_signature(path: Path, expect_org: str | None) -> str:
    """Get-AuthenticodeSignature — 경로는 환경 변수로 넘긴다(명령 글에 붙이지 않는다)."""
    script = (
        "$s = Get-AuthenticodeSignature -LiteralPath $env:WT_SIGFILE; "
        "$sub = ''; if ($s.SignerCertificate) { $sub = $s.SignerCertificate.Subject }; "
        "Write-Output ($s.Status.ToString() + '|' + $sub)"
    )
    env = dict(os.environ, WT_SIGFILE=str(path))
    r = subprocess.run([str(POWERSHELL), "-NoProfile", "-NonInteractive", "-Command", script],
                       capture_output=True, text=True, env=env, timeout=120)
    line = (r.stdout or "").strip().splitlines()[-1] if r.stdout.strip() else ""
    status, _, subject = line.partition("|")
    if expect_org is not None:
        if status != "Valid" or signer_org(subject) != expect_org:
            fail(f"{path.name} 의 서명이 맞지 않습니다: {line or r.stderr}")
    return line


def make_ico(path: Path) -> None:
    """exe 아이콘 — 초록 원과 흰 체크(트레이의 '연결됨' 과 같은 꼴). 외부 라이브러리 없이 32bpp BMP 로."""
    def image(n: int) -> bytes:
        px = bytearray()
        c = (n - 1) / 2
        r = n / 2 - 0.5
        for y in range(n - 1, -1, -1):  # 아래에서 위로
            for x in range(n):
                d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
                a = max(0.0, min(1.0, r - d + 0.5))
                # 체크: (0.28,0.52)-(0.44,0.68)-(0.72,0.36)
                fx, fy = x / n, y / n
                def seg(x1, y1, x2, y2):
                    dx, dy = x2 - x1, y2 - y1
                    t = max(0.0, min(1.0, ((fx - x1) * dx + (fy - y1) * dy) / (dx * dx + dy * dy)))
                    return ((fx - x1 - t * dx) ** 2 + (fy - y1 - t * dy) ** 2) ** 0.5
                white = min(seg(0.28, 0.52, 0.44, 0.68), seg(0.44, 0.68, 0.72, 0.36)) < 0.065
                b, g, rr = (255, 255, 255) if white else (0x3E, 0x8E, 0x1E)
                px += bytes((b, g, rr, int(a * 255)))
        mask = bytes(((n + 31) // 32) * 4 * n)
        header = struct.pack("<IiiHHIIiiII", 40, n, n * 2, 1, 32, 0, len(px) + len(mask), 0, 0, 0, 0)
        return header + bytes(px) + mask

    sizes = [16, 32, 48]
    imgs = [image(n) for n in sizes]
    out = struct.pack("<HHH", 0, 1, len(sizes))
    offset = 6 + 16 * len(sizes)
    for n, im in zip(sizes, imgs):
        out += struct.pack("<BBBBHHII", n % 256, n % 256, 0, 0, 1, 32, len(im), offset)
        offset += len(im)
    path.write_bytes(out + b"".join(imgs))


def csc(out: Path, sources: list[Path], *, target: str, manifest: Path | None, defines: list[str],
        resources: list[tuple[Path, str]], icon: Path | None = None, main: str | None = None) -> None:
    args = [str(CSC), "/nologo", "/codepage:65001", "/utf8output", f"/target:{target}", "/platform:x64",
            "/optimize+", "/warn:4", "/nowarn:1591", f"/out:{out}"]
    args += [f"/reference:{r}" for r in REFS]
    if manifest:
        args.append(f"/win32manifest:{manifest}")
    if icon:
        args.append(f"/win32icon:{icon}")
    if defines:
        args.append("/define:" + ";".join(defines))
    if main:
        args.append(f"/main:{main}")
    args += [f"/resource:{p},{name}" for p, name in resources]
    args += [str(s) for s in sources]
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    text = (r.stdout + r.stderr).strip()
    if text:
        print(text)
    if r.returncode != 0:
        fail(f"csc 가 {out.name} 를 짓지 못했습니다")
    print(f"지음: {out}  ({out.stat().st_size:,} 바이트)")


TAG_RE = re.compile(r"v?(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})(-[0-9A-Za-z.-]{1,40})?")


def safe_tag(tag: object) -> str:
    """릴리스의 태그 이름 — build.py(파이썬 글자열)와 Embedded.cs(C# 글자열)에 그대로 쓰이므로 판 꼴만 받는다.
    git 의 태그 이름에는 따옴표·쌍반점·괄호가 들어갈 수 있다 — 그대로 쓰면 다시 짓는 build.py 가 그 코드를 돌린다."""
    if not isinstance(tag, str) or not TAG_RE.fullmatch(tag):
        fail(f"릴리스 태그가 판 꼴이 아닙니다: {tag!r}")
    return tag


def signer_org(subject: str) -> str | None:
    """인증서 주체의 O= 값(정확히). 'O=WireGuard LLC' 가 글 안에 있는지만 보면 'O=WireGuard LLCX' 도 지난다."""
    for part in re.split(r",\s*(?=[A-Za-z0-9.]+=)", subject or ""):
        k, _, v = part.partition("=")
        if k.strip() == "O":
            v = v.strip()
            return v[1:-1] if len(v) >= 2 and v[0] == v[-1] == '"' else v
    return None


def version_key(v: str) -> tuple:
    v = v.lstrip("vV").split("+", 1)[0]
    core, _, pre = v.partition("-")
    nums = [int(x) for x in core.split(".")] + [0, 0, 0]
    return (nums[0], nums[1], nums[2], 1 if not pre else 0, pre)


def in_range(version: str, rng: str) -> bool | None:
    """GHSA 의 vulnerable_version_range 해석(도우미의 Versions.InRange 와 같은 규칙 — 시험판 비교는 거칠다)."""
    if not rng:
        return None
    v = version_key(version)
    for c in rng.split(","):
        c = c.strip()
        for op in ("<=", ">=", "<", ">", "="):
            if c.startswith(op):
                c = c[len(op):].strip()
                break
        else:
            op = "="
        try:
            w = version_key(c)
        except ValueError:
            return None
        ok = {"<": v < w, "<=": v <= w, ">": v > w, ">=": v >= w, "=": v == w}[op]
        if not ok:
            return False
    return True


def latest_release() -> dict:
    rel = json.loads(fetch(T2P_LATEST_API, 4 * 1024 * 1024, "application/vnd.github+json"))
    asset = next((a for a in rel.get("assets", []) if a.get("name") == T2P_ASSET), None)
    digest = (asset or {}).get("digest") or ""
    return {
        "tag": safe_tag(rel.get("tag_name")), "url": rel.get("html_url", ""), "published": rel.get("published_at", ""),
        "asset_url": (asset or {}).get("browser_download_url", ""),
        "digest": digest.split(":", 1)[1] if digest.startswith("sha256:") else None,
    }


def advisories(version: str) -> list[str]:
    out, seen = [], set()
    for url in ADVISORY_APIS:
        for a in json.loads(fetch(url, 4 * 1024 * 1024, "application/vnd.github+json")):
            if a.get("withdrawn_at") or a.get("ghsa_id") in seen:
                continue
            for v in a.get("vulnerabilities") or []:
                if ((v.get("package") or {}).get("name") or "").lower() != "tun2proxy":
                    continue
                hit = in_range(version, v.get("vulnerable_version_range") or "")
                if hit is False:
                    continue
                seen.add(a.get("ghsa_id"))
                out.append(f"{a.get('ghsa_id')} {a.get('severity')} {v.get('vulnerable_version_range')} "
                           f"{a.get('html_url')}" + (" (범위를 읽지 못함)" if hit is None else ""))
                break
    return out


def check() -> dict:
    rel = latest_release()
    latest = rel["tag"].lstrip("v")
    skipped = T2P_SKIPPED.get(latest)
    newer = version_key(latest) > version_key(T2P_VERSION) and skipped is None
    print(f"tun2proxy  고정 {T2P_VERSION} · 최신 {latest} ({rel['published'][:10]})" + (" ← 새 판" if newer else "")
          + (f" (건너뛰는 판 — {skipped})" if skipped else ""))
    print(f"           {rel['url']}")
    print(f"           {T2P_ASSET} digest {rel['digest'] or '없음'}")
    adv = advisories(T2P_VERSION)
    print(f"보안 권고(고정 판 {T2P_VERSION} 에 걸리는 것): " + (f"{len(adv)}건" if adv else "없음"))
    for line in adv:
        print("  " + line)
    rel["newer"] = newer
    return rel


def update(deploy: str | None) -> None:
    rel = check()
    if not rel["newer"]:
        print("바꿀 새 판이 없습니다")
        return
    if not rel["digest"]:
        fail("API 가 asset digest 를 주지 않아 대조할 수 없습니다 — 손으로 확인하십시오")
    url = rel["asset_url"]
    if not url.startswith("https://github.com/tun2proxy/tun2proxy/releases/download/"):
        fail(f"받는 곳이 낯섭니다: {url}")
    print(f"받는 중: {url}")
    data = fetch(url, ZIP_MAX)
    got = sha256(data)
    if got != rel["digest"]:
        fail(f"받은 zip 의 SHA-256({got})이 API digest 와 다릅니다")
    new_files = {}
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in FILES:
            new_files[name] = sha256(z.read(name))
    # wintun.dll 의 서명은 다시 짓는 길(extract → check_signature)이 본다 — 맞지 않으면 짓지 않는다.
    me = Path(__file__)
    text = me.read_text(encoding="utf-8")
    tag = safe_tag(rel["tag"])  # 소스에 쓰기 직전에 한 번 더
    ver = tag.lstrip("v")
    repl = [
        (f'T2P_TAG = "{T2P_TAG}"', f'T2P_TAG = "{tag}"'),
        (f'T2P_VERSION = "{T2P_VERSION}"', f'T2P_VERSION = "{ver}"'),
        (f'T2P_ZIP_SHA256 = "{T2P_ZIP_SHA256}"', f'T2P_ZIP_SHA256 = "{got}"'),
    ] + [(f'"{n}": "{h}"', f'"{n}": "{new_files[n]}"') for n, h in FILES.items()]
    for old, new in repl:
        if text.count(old) != 1:
            fail(f"build.py 에서 고칠 자리를 찾지 못했습니다: {old}")
        text = text.replace(old, new)
    dl = OUT / "dl" / T2P_ASSET
    dl.parent.mkdir(parents=True, exist_ok=True)
    dl.write_bytes(data)
    me.write_text(text, encoding="utf-8", newline="\n")
    print(f"build.py 를 {ver} 로 고쳤습니다 — 다시 짓습니다(도우미 시험도 다시 돌리십시오)")
    args = [sys.executable, "-I", str(me), "--no-api"] + (["--deploy", deploy] if deploy else [])
    sys.exit(subprocess.run(args).returncode)


def main() -> None:
    # python -I 는 PYTHONIOENCODING 을 보지 않는다 — 콘솔·파이프가 cp949 여도 깨지지 않게.
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", help="이미 받아 둔 릴리스 zip(해시로 확인한다)")
    ap.add_argument("--deploy", help="실제 판을 복사할 곳")
    ap.add_argument("--no-api", action="store_true", help="GitHub API 와 견주지 않는다")
    ap.add_argument("--check", action="store_true", help="최신 판·digest·보안 권고를 보여 주기만")
    ap.add_argument("--update", action="store_true", help="최신 판으로 바꾸고 다시 짓는다")
    a = ap.parse_args()
    if a.check:
        check()
        return
    if a.update:
        update(a.deploy)
        return

    if not CSC.exists():
        fail(f"csc 가 없습니다: {CSC}")
    data = get_zip(a.zip, not a.no_api)
    files = extract(data)
    print("wintun.dll 서명: " + check_signature(OUT / "bin" / "wintun.dll", "WireGuard LLC"))
    print("tun2proxy-bin.exe 서명: " + check_signature(OUT / "bin" / "tun2proxy-bin.exe", None))

    gen = OUT / "gen"
    res = OUT / "res"
    gen.mkdir(parents=True, exist_ok=True)
    res.mkdir(parents=True, exist_ok=True)
    resources = []
    for name, b in files.items():
        p = res / (name + ".gz")
        p.write_bytes(gzip.compress(b, compresslevel=9, mtime=0))
        resources.append((p, f"WebTether.{name}.gz"))
    resources.append((HERE / "licenses" / "tun2proxy-LICENSE.txt", "WebTether.licenses.tun2proxy.txt"))
    resources.append((HERE / "licenses" / "wintun-LICENSE.txt", "WebTether.licenses.wintun.txt"))

    t2p, wt = FILES["tun2proxy-bin.exe"], FILES["wintun.dll"]
    bundle = hashlib.sha256((t2p + wt).encode("ascii")).hexdigest()[:16]
    (gen / "Embedded.cs").write_text(
        "// build.py 가 짓는다 — 고치지 마라.\n"
        "namespace WebTether\n{\n    public static class Embedded\n    {\n"
        f'        public const string Tun2proxyVersion = "{T2P_VERSION}";\n'
        f'        public const string Tun2proxyZipSha256 = "{T2P_ZIP_SHA256}";\n'
        f'        public const string Tun2proxySha256 = "{t2p}";\n'
        f'        public const string WintunSha256 = "{wt}";\n'
        f'        public const string BundleId = "{bundle}";\n'
        f'        public const string SkippedVersions = "{",".join(sorted(T2P_SKIPPED))}";\n'
        "    }\n}\n", encoding="utf-8", newline="\n")

    template = (HERE / "app.manifest").read_text(encoding="utf-8")
    (gen / "app.manifest").write_text(template.replace("@LEVEL@", "asInvoker"), encoding="utf-8")
    (gen / "test.manifest").write_text(template.replace("@LEVEL@", "asInvoker"), encoding="utf-8")
    make_ico(gen / "app.ico")

    src = sorted((HERE / "src").glob("*.cs")) + [gen / "Embedded.cs"]
    csc(OUT / "WebTether.exe", src, target="winexe", manifest=gen / "app.manifest", defines=[],
        resources=resources, icon=gen / "app.ico")
    csc(OUT / "WebTether-test.exe", src, target="winexe", manifest=gen / "test.manifest", defines=["TEST_BUILD"],
        resources=resources, icon=gen / "app.ico")
    csc(OUT / "WebTether-tests.exe", src + sorted((HERE / "tests").glob("Test*.cs")), target="exe",
        manifest=gen / "test.manifest", defines=["TEST_BUILD"], resources=resources,
        main="WebTether.Tests.TestMain")
    csc(OUT / "ctrlc-child.exe", [HERE / "tests" / "CtrlChild.cs"], target="exe", manifest=gen / "test.manifest",
        defines=[], resources=[])

    if a.deploy:
        shutil.copyfile(OUT / "WebTether.exe", a.deploy)
        print(f"복사함: {a.deploy}")
    print("완성 exe SHA-256: " + sha256((OUT / "WebTether.exe").read_bytes()))


if __name__ == "__main__":
    main()
