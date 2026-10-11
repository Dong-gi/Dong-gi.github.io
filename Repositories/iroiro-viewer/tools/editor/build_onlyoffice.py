"""ONLYOFFICE(docx·xlsx·pptx … 편집기)의 엔진 묶음을 짓는다 — `vendor/editor/assets/engines/onlyoffice/`(git 무시)로. APK 의 `assets/engines/onlyoffice/` 이 된다.

판을 고정한다(사용자 승인 2026-10-09 — 'onlyoffice로 하고 …'):
- 편집기: CryptPad 가 문서 서버 없이 쓰려고 고친 빌드 `cryptpad/onlyoffice-editor` 의 릴리스 v9.3.2+3(ONLYOFFICE·Euro-Office sdkjs/web-apps 9.3.0.140 바탕,
  AGPL-3.0). 받은 zip 의 SHA-512 를 CryptPad 의 설치 스크립트(install-office.sh)가 적은 값과 견준다.
- 변환기(x2t): `cryptpad/onlyoffice-x2t-wasm` 의 릴리스 v9.3.2+3(ONLYOFFICE core 를 웹어셈블리로, AGPL-3.0). 같은 방식으로 견준다.
- 라이선스 글: 같은 태그의 소스 저장소에서 받는다(zip 에 들어 있지 않다).

원래 묶음과 다른 것:
- 걷는다 — 도움말(언어마다 수백 MB)·맞춤법 사전·IE 판·한국어/영어가 아닌 말·매크로 편집기(monaco)·Visio·PDF 편집 엔진(이 빌드에는 PDF 편집 화면이
  없다)·embed/forms 화면·WOPI·미리 압축한 `.br` 사본·서비스 워커.
- 글꼴 — 묶음의 글꼴 가운데 라이선스가 분명하고 재배포가 되는 것만 싣는다(FONTS — OFL·Apache-2.0·Bitstream Vera/DejaVu·Symbola). **arial·calibri 는
  Monotype·Microsoft 의 독점 글꼴이라 싣지 않는다**(name 표의 라이선스 글로 확인). 그 자리는 메트릭이 같은 Arimo·Carlito 가 받는다.
  한글 글꼴은 rhwp 묶음의 것(OFL)을 빌려 쓴다(`fonts/rhwp/…` — 앱의 AssetServer 가 `engines/rhwp/fonts/` 로 잇는다. APK 에 두 벌 싣지 않는다).
  글꼴 목록(AllFonts.js)과 목록 그림은 `tools/editor/onlyoffice/allfonts.py` 가 짓는다(CryptPad 의 것을 바이트까지 다시 짓는 것으로 검증 — `--selftest`).
- HTML — 서비스 워커 등록을 걷고, 인라인 처리기 하나(`onload="this.media='all'"`)를 고정 값으로 바꾼다. 인라인 스크립트의 SHA-256 을 모아
  `csp.txt`(엔진의 CSP)를 짓는다 — 앱이 이 파일을 그 엔진의 정책으로 쓴다. 세 편집기 화면(main/index*.html)의 `</body>` 앞에 우리 손질
  (`/iroiro-oo-inner.css`·`.js` — 자판은 단추로만·붙이기·폰에 맞춤)을 넣는다.
- 우리 쪽(`feature/editor/src/main/assets/engines/onlyoffice/` — host.html·iroiro-onlyoffice.js·iroiro-oo-inner.*, 커밋한다)은 여기서 손대지 않는다.

    python -E -P tools/editor/build_onlyoffice.py            # 받기(처음만)·짓기 — rhwp 묶음을 먼저 지어 둔다(한글 글꼴)
    python -E -P tools/editor/build_onlyoffice.py --selftest # 글꼴 목록 생성기를 CryptPad 의 AllFonts.js 와 견준다

`-I` 대신 `-E -P` 로 돈다 — 글꼴 도구(Pillow·brotli)가 사용자 site-packages 에 있다. `-P` 가 스크립트·작업 폴더를 sys.path 에 넣지 않는다.
"""

from __future__ import annotations

import base64
import hashlib
import pathlib
import re
import shutil
import sys
import time
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "editor" / "onlyoffice"))
import allfonts  # noqa: E402

WORK = ROOT / "local" / "editor" / "onlyoffice"
DL = WORK / "dl"
OUT = ROOT / "vendor" / "editor" / "assets" / "engines" / "onlyoffice"
RHWP_FONTS = ROOT / "vendor" / "editor" / "assets" / "engines" / "rhwp" / "fonts"

TAG = "v9.3.2+3"
EDITOR_URL = "https://github.com/cryptpad/onlyoffice-editor/releases/download/v9.3.2%2B3/onlyoffice-editor.zip"
EDITOR_SHA512 = "181978176e443b9ff9cce127e6aa9668a4ee8e81473cb0c0c4cf0d83dea9b6636c730d40e704ebe8fdf313498a3106bafe5c4021ae937b269d05a07b7be6bbb4"
X2T_URL = "https://github.com/cryptpad/onlyoffice-x2t-wasm/releases/download/v9.3.2%2B3/x2t.zip"
X2T_SHA512 = "ed0579097a0269877d6622b28b1e2718e1f9f4e8ac99301f5f13f5f052b3db24db789deab8c654044db588be103c139be74f83a0ca066dee1789522241c1af6e"

# 라이선스 글 — 같은 태그의 소스(받은 것은 local/editor/onlyoffice/licenses 에 둔다).
LICENSES = {
    "AGPL-3.0-or-later.txt": "https://raw.githubusercontent.com/cryptpad/onlyoffice-editor/v9.3.2%2B3/LICENSES/AGPL-3.0-or-later.txt",
    "sdkjs-LICENSE.txt": "https://raw.githubusercontent.com/cryptpad/onlyoffice-editor/v9.3.2%2B3/sdkjs/LICENSE.txt",
    "sdkjs-3DPARTY.md": "https://raw.githubusercontent.com/cryptpad/onlyoffice-editor/v9.3.2%2B3/sdkjs/3DPARTY.md",
    "web-apps-LICENSE.txt": "https://raw.githubusercontent.com/cryptpad/onlyoffice-editor/v9.3.2%2B3/web-apps/LICENSE.txt",
    "x2t-core-LICENSE.txt": "https://raw.githubusercontent.com/cryptpad/onlyoffice-x2t-wasm/v9.3.2%2B3/core/LICENSE.txt",
    "x2t-core-3DPARTY.md": "https://raw.githubusercontent.com/cryptpad/onlyoffice-x2t-wasm/v9.3.2%2B3/core/3DPARTY.md",
    "Apache-2.0.txt": "https://www.apache.org/licenses/LICENSE-2.0.txt",
}

# 걷는 것(정규식, zip 안의 자리에).
PRUNE = [
    r"/resources/help/", r"^dictionaries/", r"/main/ie/", r"^web-apps/vendor/monaco/", r"^sdkjs/visio/", r"^sdkjs/pdf/",
    r"\.br$", r"\.map$", r"^web-apps/apps/[^/]+/embed/", r"^web-apps/apps/documenteditor/forms/", r"^web-apps/apps/api/wopi/",
    r"_ie\.js$", r"/sdk-all-ie\.js$", r"^sdkjs/common/serviceworker/", r"^web-apps/apps/presentationeditor/main/index\.reporter\.html$",
    # 글꼴과 그 목록은 우리가 짓는다.
    r"^fonts/", r"^sdkjs/common/AllFonts\.js$", r"^sdkjs/common/Images/fonts_thumbnail",
]
LOCALES = ("en", "ko")

# 싣는 묶음 글꼴(파일 이름) — 라이선스는 name 표에서 확인했다(2026-10-09).
FONTS = {
    "OFL-1.1": ["Arimo-Bold.ttf", "Arimo-BoldItalic.ttf", "Arimo-Italic.ttf", "Arimo-Regular.ttf", "Asana-Math.otf", "Carlito-Bold.ttf",
                "Carlito-BoldItalic.ttf", "Carlito-Italic.ttf", "Carlito-Regular.ttf", "Cousine-Bold.ttf", "Cousine-BoldItalic.ttf",
                "Cousine-Italic.ttf", "Cousine-Regular.ttf", "Hind-Bold.ttf", "Hind-Regular.ttf", "IBMPlexMono-Medium.ttf", "NotoMono-Regular.ttf",
                "NotoSans-Bold.ttf", "NotoSans-BoldItalic.ttf", "NotoSans-Italic.ttf", "NotoSans-Regular.ttf", "NotoSansLao-Regular.ttf",
                "NotoSansUI-Bold.ttf", "NotoSansUI-BoldItalic.ttf", "NotoSansUI-Italic.ttf", "NotoSansUI-Regular.ttf",
                "NotoSerif-Bold.ttf", "NotoSerif-BoldItalic.ttf", "NotoSerif-Italic.ttf", "NotoSerif-Regular.ttf", "OpenKhmerSchool-Bold.ttf",
                "OpenKhmerSchool-Light.ttf", "OpenKhmerSchool-Medium.ttf", "OpenKhmerSchool-SemiBold.ttf", "Tinos-Bold.ttf", "Tinos-BoldItalic.ttf",
                "Tinos-Italic.ttf", "Tinos-Regular.ttf"],
    "Apache-2.0": ["Caladea-Bold.ttf", "Caladea-BoldItalic.ttf", "Caladea-Italic.ttf", "Caladea-Regular.ttf", "DroidSans-Bold.ttf", "DroidSans.ttf",
                   "DroidSansMono.ttf", "DroidSerif-Bold.ttf", "DroidSerif-BoldItalic.ttf", "DroidSerif-Italic.ttf", "DroidSerif-Regular.ttf",
                   "OpenSans-Regular.ttf", "Roboto-Bold.ttf", "Roboto-BoldItalic.ttf", "Roboto-Italic.ttf",
                   "Roboto-Regular.ttf", "RobotoCondensed-Bold.ttf",
                   "RobotoCondensed-BoldItalic.ttf", "RobotoCondensed-Italic.ttf", "RobotoCondensed-Regular.ttf", "SymbolNeu.ttf"],
    "Bitstream-Vera+DejaVu (name 표에 전문)": ["DejaVuSans-Bold.ttf", "DejaVuSans.ttf", "DejaVuSansMono-Bold.ttf", "DejaVuSansMono.ttf",
                                              "DejaVuSerif-Bold.ttf", "DejaVuSerif.ttf"],
    "Symbola('free for any use' — name 표)": ["Symbola717.ttf"],
}
# 싣지 않는 묶음 글꼴과 까닭 — 새 판에 모르는 글꼴이 생기면 멈춘다.
FONTS_DENIED = {
    "arial.ttf": "Monotype 독점", "arialbd.ttf": "Monotype 독점", "arialbi.ttf": "Monotype 독점", "ariali.ttf": "Monotype 독점",
    "calibri.ttf": "Microsoft 독점", "calibrib.ttf": "Microsoft 독점", "calibrii.ttf": "Microsoft 독점", "calibriz.ttf": "Microsoft 독점",
    "opens___.ttf": "OpenSymbol — 라이선스 글이 글꼴에 없다",
    "WenQuan.ttf": "GPL+예외 — 크다(8.9 MB). 한자(KS X 1001 4,888자, 호환 한자 포함)·가나는 D2Coding 이 덮는다(2026-10-09 셈)",
    "komorebi-gothic.ttf": "크다(4.3 MB) — 가나·한자는 D2Coding 이 덮는다", "komorebi-gothic-P.ttf": "크다(4.3 MB)",
    "Ubuntu-B.ttf": "UFL — 라이선스 전문을 구할 길이 없다", "Ubuntu-BI.ttf": "UFL", "Ubuntu-C.ttf": "UFL", "Ubuntu-L.ttf": "UFL",
    "Ubuntu-LI.ttf": "UFL", "Ubuntu-M.ttf": "UFL", "Ubuntu-MI.ttf": "UFL", "Ubuntu-R.ttf": "UFL", "Ubuntu-RI.ttf": "UFL",
    "UbuntuMono-B.ttf": "UFL", "UbuntuMono-BI.ttf": "UFL", "UbuntuMono-R.ttf": "UFL", "UbuntuMono-RI.ttf": "UFL",
    # 같은 가족(Roboto)·같은 꼴로 겹친다 — CryptPad 의 AllFonts.js 도 싣지 않는다.
    "Roboto-Light.ttf": "겹침", "Roboto-LightItalic.ttf": "겹침", "Roboto-Thin.ttf": "겹침", "Roboto-ThinItalic.ttf": "겹침",
}
# 빌려 쓰는 한글 글꼴(rhwp 묶음, 모두 OFL-1.1) — rhwp 의 WOFF2 는 이름 표가 어긋난 것이 있어(굵은 Noto Sans KR 이 'Noto Sans KR Thin')
# 가족 이름을 여기서 정한다. 이름 목록의 첫째는 한중일 화면의 목록에 보이는 이름, 나머지는 **이 글꼴로 대신 그릴 문서의 글꼴 이름**이다 —
# 편집기의 글꼴 고르기가 다른 이름(m_names)까지 견주므로(GetFaceNamePenalty), 기기에 없는 맑은 고딕·바탕 따위가 비슷한 꼴로 그려진다.
# 맞추지 않으면 panose·범위만으로 골라 고정폭(D2Coding)으로 가거나(맑은 고딕), 한글이 없는 글꼴로 가 두부가 된다(함초롬바탕 — 2026-10-09 기기).
# 이름은 바꾸지 않는다 — 저장한 문서에는 원래 글꼴 이름이 그대로 남는다.
SANS_ALIASES = ["맑은 고딕", "Malgun Gothic", "맑은고딕", "함초롬돋움", "HCR Dotum", "한컴 고딕", "Hancom Gothic", "Apple SD Gothic Neo",
                "본고딕", "Source Han Sans KR", "나눔바른고딕", "NanumBarunGothic", "윤고딕", "HY중고딕", "HY헤드라인M"]
GULIM_ALIASES = ["굴림", "Gulim", "굴림체", "GulimChe", "돋움", "Dotum", "돋움체", "DotumChe", "새굴림", "New Gulim"]
SERIF_ALIASES = ["바탕", "Batang", "바탕체", "BatangChe", "함초롬바탕", "HCR Batang", "한컴바탕", "본명조", "Source Han Serif KR", "신명조",
                 "HY신명조", "휴먼명조", "명조"]
GUNGSUH_ALIASES = ["궁서", "Gungsuh", "궁서체", "GungsuhChe", "휴먼옛체"]
# **Noto Sans KR 굵은꼴·Noto Serif KR 은 싣지 않는다** — 편집기의 글꼴 엔진(FreeType 웹어셈블리)이 이 셋의 WOFF2(가변 글꼴에서 떼어 낸 것 —
# 이름 표도 어긋났다)를 글리프 두 칸씩 밀어 읽어 엉뚱한 글자가 나왔다(2026-10-09 기기, 가족마다 한 줄씩 찍어 가렸다). 브라우저(rhwp)는 바르게
# 그린다. 그래서 고딕 별칭은 굵은꼴이 바른 나눔고딕, 명조 별칭은 나눔명조, 궁서는 고운바탕이 받는다. Noto Sans KR 은 보통꼴만(굵게는 엔진이 지어 그린다).
KOREAN = {
    "NotoSansKR-Regular.woff2": ("Noto Sans KR", ["본고딕"]),
    "NanumGothic-Regular.woff2": ("NanumGothic", ["나눔고딕", *SANS_ALIASES, *GULIM_ALIASES]),
    "NanumGothic-Bold.woff2": ("NanumGothic", ["나눔고딕", *SANS_ALIASES, *GULIM_ALIASES]),
    "NanumMyeongjo-Regular.woff2": ("NanumMyeongjo", ["나눔명조", *SERIF_ALIASES]),
    "NanumMyeongjo-Bold.woff2": ("NanumMyeongjo", ["나눔명조", *SERIF_ALIASES]),
    "GowunBatang-Regular.woff2": ("Gowun Batang", ["고운바탕", *GUNGSUH_ALIASES]),
    "GowunBatang-Bold.woff2": ("Gowun Batang", ["고운바탕", *GUNGSUH_ALIASES]),
    "GowunDodum-Regular.woff2": ("Gowun Dodum", ["고운돋움"]),
    "NanumGothicCoding-Regular.woff2": ("NanumGothicCoding", ["나눔고딕코딩"]), "NanumGothicCoding-Bold.woff2": ("NanumGothicCoding", ["나눔고딕코딩"]),
    "D2Coding-Regular.woff2": ("D2Coding", ["D2코딩"]), "D2Coding-Bold.woff2": ("D2Coding", ["D2코딩"]),
    "Pretendard-Regular.woff2": ("Pretendard", ["프리텐다드"]), "Pretendard-Bold.woff2": ("Pretendard", ["프리텐다드"]),
    "SpoqaHanSans-Regular.woff2": ("Spoqa Han Sans", ["스포카 한 산스"]),
}
# 글자별 대체의 차례(글꼴에 없는 글자를 무엇으로 그리는가) — 라틴·그리스·키릴은 Noto Sans, 한글은 Noto Sans KR, 한자·가나는 D2Coding(KS X 1001
# 한자 전부), 그다음 기호(DejaVu·Symbola)·수식·인도·라오·크메르 글자. 여기 없는 가족은 목록 차례로 뒤에 선다.
FALLBACK = ["Noto Sans", "Noto Sans KR", "D2Coding", "DejaVu Sans", "Symbola", "Asana Math", "Hind", "Noto Sans Lao", "Open Khmer School Medium"]


def log(msg: str) -> None:
    print(msg, flush=True)


def fetch(url: str, dst: pathlib.Path, timeout: int = 600) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    part = dst.with_name(dst.name + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "iroiro-viewer-editor-build"})
    with urllib.request.urlopen(req, timeout=timeout) as r, part.open("wb") as f:
        shutil.copyfileobj(r, f, 1024 * 1024)
    part.replace(dst)


def ensure(url: str, name: str, sha512: str) -> pathlib.Path:
    path = DL / name
    if not path.is_file():
        log(f"받기: {url}")
        fetch(url, path)
    h = hashlib.sha512()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    if h.hexdigest() != sha512:
        path.unlink()
        sys.exit(f"{name} 의 SHA-512 가 다르다 — 지웠다. 받은 것: {h.hexdigest()}")
    log(f"{name}: SHA-512 맞음")
    return path


def safe_rel(name: str) -> str | None:
    if name.endswith("/") or name.startswith("/") or "\\" in name or "\0" in name:
        return None
    parts = name.split("/")
    if any(p in ("", ".", "..") for p in parts):
        return None
    return name


def keep(name: str, prune: list[re.Pattern]) -> bool:
    if any(p.search(name) for p in prune):
        return False
    if "/locale/" in name and not name.endswith("/") and "/locale/l10n/" not in name:
        base = name.rsplit("/", 1)[1]
        if not any(base == f"{lc}.json" or base.startswith(f"{lc}_") or base.startswith(f"{lc}.") for lc in LOCALES):
            return False
    if "/formula-lang/" in name:
        base = name.rsplit("/", 1)[1]
        if not any(base.startswith(f"{lc}_") or base.startswith(f"{lc}.") for lc in LOCALES):
            return False
    return True


INLINE_SCRIPT = re.compile(r"<script(?P<attrs>[^>]*)>(?P<body>.*?)</script>", re.S | re.I)
SW_SCRIPT = re.compile(r"<script>\s*\+?function registerServiceWorker\(\).*?</script>", re.S)
# 편집기 화면(틀 안)에 우리 손질을 넣는 자리 — 자판은 단추로만·붙이기·폰에 맞춤(feature/editor/src/main/assets/engines/onlyoffice/iroiro-oo-inner.*).
EDITOR_HTML = re.compile(r"^web-apps/apps/(documenteditor|spreadsheeteditor|presentationeditor)/main/index[^/]*\.html$")
INNER = '<link rel="stylesheet" href="/iroiro-oo-inner.css">\n<script src="/iroiro-oo-inner.js"></script>\n'


def fix_html(text: str, path: str) -> str:
    text, n = SW_SCRIPT.subn("", text)
    if n:
        log(f"  서비스 워커 등록을 걷었다: {path}")
    text = text.replace(' media="print" onload="this.media=\'all\'"', "")
    if re.search(r"\son[a-z]+\s*=\s*[\"']", text):
        hits = sorted(set(re.findall(r"\s(on[a-z]+)\s*=\s*[\"']", text)))
        sys.exit(f"{path} 에 인라인 처리기가 남았다: {hits} — CSP 가 막는다. 손질을 더할 것")
    if EDITOR_HTML.match(path):
        if text.count("</body>") != 1:
            sys.exit(f"{path} 에서 </body> 를 하나로 찾지 못했다 — 우리 손질을 넣을 자리")
        text = text.replace("</body>", INNER + "</body>")
    return text


def script_hashes(text: str) -> set[str]:
    out = set()
    for m in INLINE_SCRIPT.finditer(text):
        if "src=" in m.group("attrs"):
            continue
        # HTML 파서는 줄 끝(CRLF·CR)을 LF 로 바꾼 뒤 스크립트의 글을 잰다 — 같은 글로 잰다.
        body = m.group("body").replace("\r\n", "\n").replace("\r", "\n")
        out.add("'sha256-" + base64.b64encode(hashlib.sha256(body.encode("utf-8")).digest()).decode() + "'")
    return out


def unpack_editor(zpath: pathlib.Path) -> tuple[set[str], list[str]]:
    prune = [re.compile(p) for p in PRUNE]
    hashes: set[str] = set()
    html_files = []
    with zipfile.ZipFile(zpath) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            rel = safe_rel(info.filename)
            if rel is None:
                sys.exit(f"zip 에 이상한 자리가 있다: {info.filename!r}")
            if not keep(rel, prune):
                continue
            dst = OUT / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            data = z.read(info)
            if rel.endswith(".html"):
                text = fix_html(data.decode("utf-8"), rel)
                hashes |= script_hashes(text)
                html_files.append(rel)
                data = text.encode("utf-8")
            dst.write_bytes(data)
    return hashes, html_files


def unpack_x2t(zpath: pathlib.Path) -> None:
    dst = OUT / "x2t"
    dst.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zpath) as z:
        for name in ("x2t.js", "x2t.wasm"):
            (dst / name).write_bytes(z.read(name))


def build_fonts(zpath: pathlib.Path) -> tuple[list[str], list[str]]:
    listed = {n for names in FONTS.values() for n in names}
    faces = []
    with zipfile.ZipFile(zpath) as z:
        present = sorted(i.filename[len("fonts/fonts/"):] for i in z.infolist() if i.filename.startswith("fonts/fonts/") and not i.is_dir())
        unknown = [n for n in present if n not in listed and n not in FONTS_DENIED]
        if unknown:
            sys.exit("모르는 묶음 글꼴 — 라이선스를 보고 FONTS·FONTS_DENIED 에 더할 것: " + ", ".join(unknown))
        missing = sorted(listed - set(present))
        if missing:
            sys.exit("FONTS 에 적었는데 묶음에 없다: " + ", ".join(missing))
        fdir = OUT / "fonts" / "fonts"
        fdir.mkdir(parents=True, exist_ok=True)
        for name in sorted(listed):
            data = z.read("fonts/fonts/" + name)
            (fdir / name).write_bytes(data)
            faces.append(allfonts.read_face(data, "fonts/" + name))
    if not RHWP_FONTS.is_dir():
        sys.exit(f"한글 글꼴을 빌릴 rhwp 묶음이 없다({RHWP_FONTS.relative_to(ROOT)}) — tools/editor/build_rhwp.py 를 먼저 돌릴 것")
    for name, (family, names) in KOREAN.items():
        p = RHWP_FONTS / name
        if not p.is_file():
            sys.exit(f"rhwp 묶음에 {name} 가 없다")
        f = allfonts.read_face(p.read_bytes(), "rhwp/" + name, family_override=family)
        f.names = list(names)
        faces.append(f)
    js, infos = allfonts.build_allfonts(faces, FALLBACK)
    (OUT / "sdkjs" / "common").mkdir(parents=True, exist_ok=True)
    (OUT / "sdkjs" / "common" / "AllFonts.js").write_text(js, encoding="utf-8")
    files = sorted({f.path for f in faces})
    by_path = {f.path: f for f in faces}
    images = OUT / "sdkjs" / "common" / "Images"
    images.mkdir(parents=True, exist_ok=True)
    fallback_latin = by_path["fonts/DejaVuSans.ttf"]
    fallback_ea = by_path["rhwp/NotoSansKR-Regular.woff2"]
    for ea, prefix in ((False, "fonts_thumbnail"), (True, "fonts_thumbnail_ea")):
        for suffix, png in allfonts.build_thumbnails(infos, by_path, files, ea, fallback_latin, fallback_ea).items():
            (images / f"{prefix}{suffix}.png").write_bytes(png)
    return [i[0] for i in infos], sorted(KOREAN)


def licenses(families: list[str]) -> None:
    lic = OUT / "licenses"
    lic.mkdir(parents=True, exist_ok=True)
    src = WORK / "licenses"
    for name, url in LICENSES.items():
        p = src / name
        if not p.is_file():
            log(f"받기: {url}")
            fetch(url, p, timeout=120)
        shutil.copy2(p, lic / name)
    ofl = ROOT / "vendor" / "editor" / "assets" / "engines" / "rhwp" / "licenses" / "OFL-1.1.txt"
    if not ofl.is_file():
        sys.exit("OFL 전문을 rhwp 묶음에서 찾지 못했다")
    shutil.copy2(ofl, lic / "OFL-1.1.txt")
    notes = ["ONLYOFFICE 엔진에 실은 글꼴과 라이선스(묶음 글꼴은 name 표의 글, 한글 글꼴은 rhwp 묶음의 licenses/FONTS.txt).", ""]
    for license_name, names in FONTS.items():
        notes.append(f"[{license_name}]")
        notes.extend(f"  fonts/fonts/{n}" for n in names)
        notes.append("")
    notes.append("[OFL-1.1 — rhwp 묶음에서 빌려 쓴다]")
    notes.extend(f"  ../rhwp/fonts/{n}" for n in sorted(KOREAN))
    notes.append("")
    notes.append("싣지 않은 묶음 글꼴: " + ", ".join(f"{k}({v})" for k, v in FONTS_DENIED.items()))
    notes.append("")
    notes.append("목록에 보이는 가족: " + ", ".join(families))
    (lic / "FONTS.txt").write_text("\n".join(notes) + "\n", encoding="utf-8")


def write_csp(hashes: set[str]) -> str:
    # 'unsafe-eval' — sdkjs 가 읽어 들일 때 매크로 상자를 new Function 으로 만든다(돌리지는 않는다 — 매크로는 끈다). 웹어셈블리(x2t·글꼴 엔진).
    csp = ("default-src 'self'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval' " + " ".join(sorted(hashes)) + "; "
           "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data: blob:; connect-src 'self' data: blob:; "
           "worker-src 'self' blob:; frame-src 'self' blob:; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'self'")
    (OUT / "csp.txt").write_text(csp + "\n", encoding="utf-8")
    return csp


def selftest() -> None:
    zpath = ensure(EDITOR_URL, "onlyoffice-editor.zip", EDITOR_SHA512)
    with zipfile.ZipFile(zpath) as z:
        files, infos, sel = allfonts.parse_allfonts(z.read("sdkjs/common/AllFonts.js").decode("utf-8-sig"))
        faces = []
        for path in files:
            faces.append(allfonts.read_face(z.read("fonts" + path), path))
    js, my_infos = allfonts.build_allfonts(faces)
    f2, i2, s2 = allfonts.parse_allfonts(js)
    ok = f2 == files and i2 == infos and s2 == sel
    log(f"글꼴 목록 생성기: CryptPad 의 AllFonts.js 와 {'같다' if ok else '다르다'} (파일 {len(files)}, 가족 {len(infos)}, 선택 표 {len(sel)} 바이트)")
    if not ok:
        sys.exit(1)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    if "--selftest" in sys.argv[1:]:
        selftest()
        return
    editor = ensure(EDITOR_URL, "onlyoffice-editor.zip", EDITOR_SHA512)
    x2t = ensure(X2T_URL, "x2t.zip", X2T_SHA512)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    log("풀기: 편집기(걷을 것을 걷고)")
    hashes, html_files = unpack_editor(editor)
    injected = [p for p in html_files if EDITOR_HTML.match(p)]
    if len(injected) < 3:
        sys.exit(f"우리 손질을 넣은 편집기 화면이 셋이 안 된다: {injected}")
    # 편집기는 서버가 주는 사용자 테마 목록(출처 맨 위의 themes.json)을 찾는다 — 없으면 경고만 찍는다. 빈 목록을 둔다.
    (OUT / "themes.json").write_text('{"themes": []}\n', encoding="utf-8")
    log("풀기: x2t")
    unpack_x2t(x2t)
    log("글꼴: 고르고 목록·그림 짓기")
    families, korean = build_fonts(editor)
    licenses(families)
    csp = write_csp(hashes)
    total = 0
    files = []
    for p in sorted(OUT.rglob("*")):
        if p.is_file():
            total += p.stat().st_size
            files.append(p)
    info = [
        "ONLYOFFICE 엔진 묶음 — tools/editor/build_onlyoffice.py 가 지었다. 손으로 고치지 않는다.",
        f"지은 때: {time.strftime('%Y-%m-%d %H:%M:%S %z')}",
        f"편집기: {EDITOR_URL} (SHA-512 {EDITOR_SHA512[:16]}…)",
        f"x2t: {X2T_URL} (SHA-512 {X2T_SHA512[:16]}…)",
        f"HTML {len(html_files)}개, 인라인 스크립트 해시 {len(hashes)}개 → csp.txt",
        f"글꼴 가족 {len(families)}개(한글 {len(set(v[0] for v in KOREAN.values()))}개는 rhwp 묶음에서 빌림)",
        f"파일 {len(files)}개, {total:,} 바이트",
    ]
    (OUT / "BUILD-INFO.txt").write_text("\n".join(info) + "\n", encoding="utf-8")
    log("\n".join(info[2:]))
    biggest = sorted(files, key=lambda p: p.stat().st_size, reverse=True)[:8]
    log("큰 파일: " + ", ".join(f"{p.relative_to(OUT).as_posix()} {p.stat().st_size // 1024:,} KiB" for p in biggest))
    log(f"끝: {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
