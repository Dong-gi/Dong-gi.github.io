"""rhwp 0.9 의 한컴 수식 글자(HYhwpEQ·HancomEQN 의 사용자 영역 코드)를 그리는 대체 글꼴을 짓는다 — build_rhwp.py 가 부른다.

왜 있는가. rhwp 0.9 부터 수식 글꼴이 HYhwpEQ(한컴 수식의 기본)나 HancomEQN 이면 숫자·기호·기울인 글자를 그 글꼴의 사용자 영역(PUA) 코드로
바꿔 캔버스에 그린다(`src/renderer/equation/hancom_glyphs.rs` 의 `fn glyph`). 두 글꼴은 한컴의 상용 글꼴이라 안드로이드에도 PC 에도 없고,
웹 빌드에는 그 코드를 되돌려 읽는 길이 없어(데스크톱 Skia 빌드의 `semantic_fallback_glyph` 만 있다) 네모로 나온다. 그래서 그 코드 자리에
자유 글꼴의 글리프를 담은 작은 글꼴을 짓고, 스튜디오가 그것을 'HyhwpEQ'·'HancomEQN' 이라는 CSS 가족으로 싣게 한다(vite.config.mjs).

무엇을 담는가 — rhwp 의 표를 빌드 때 그 소스에서 읽는다(손으로 옮겨 적지 않는다):
- `hancom_glyphs.rs` 의 `fn glyph(ch, role)` — 글자 → (그릴 코드, UPM 1024 의 전진 폭). 역할(Role)마다 무엇을 그리는지가 갈린다.
  기울인 소문자는 글리프 자체가 기울어 있고(이탤릭 글리프를 쓴다) 기울인 대문자는 rhwp 가 0.333 기울임 행렬을 건다(곧은 글리프를 쓴다).
- 같은 파일의 `measure` — HYhwpEQ 는 그리스·큰 연산자를 이 길로 보내지 않는다(그 둘은 HancomEQN 에만 담는다), 정체·ASCII 연산자는
  기준선을 옮겨 그린다(그만큼 글리프를 거꾸로 옮겨 둔다 — 아니면 그 글자만 숫자보다 높이 뜬다).
- `hancom_eqn_metrics.rs` 의 CODES·HMTX — HancomEQN 의 전진 폭.
표의 꼴이 바뀌면(역할이 늘었다, 갈래의 모양이 다르다, 배열 길이가 다르다) 짐작하지 않고 멈춘다 — 그때는 새 판의 소스를 읽고 이 도구를 고친다.

원 글꼴 — STIX Two Math 2.13(github.com/stipub/stixfonts 태그 v2.13 = 커밋 37308d44…, `fonts/static_ttf_woff2/STIXTwoMath-Regular.woff2`,
아래 SHA-256 으로 고정). SIL OFL 1.1 — 저작권 줄과 라이선스 글은 저장소의 OFL.txt 를 같은 커밋에서 받아 함께 싣는다. 그 OFL.txt 가 정한
Reserved Font Name 은 'TM Math'(MicroPress 부분)이고 'STIX Fonts'·'STIX Two' 는 IEEE 의 상표다 — 지은 글꼴의 이름표에는 셋 다 쓰지 않는다.
HYhwpEQ 처럼 Times 계열의 세리프이고, 곧은 라틴·숫자·연산자, 수학 이탤릭(U+1D434…), 그리스 수학 이탤릭, ∑∏△∴∵ 를 한 파일에 다 가졌다.
화면에서 쓰는 가족 이름(HyhwpEQ·HancomEQN)은 스튜디오가 FontFace 로 붙이는 이름이고, 글꼴 파일의 이름표는 'Iroiro EQ Substitute …' 다.

    local/editor/eqfont/venv/Scripts/python -I tools/editor/make_eq_font.py <rhwp 소스 폴더> <써 낼 폴더>

받은 원 글꼴은 local/editor/eqfont/downloads/(git 무시)에 둔다. 쓰는 것 — <써 낼 폴더>/HyhwpEQ-iroiro.woff2·HancomEQN-iroiro.woff2,
EQ-SUBSTITUTE-FONTS.txt(고지), STIXTwoMath-OFL.txt(원 글꼴의 OFL). fontTools·Brotli 는 가상 환경에만 둔다(eqfont-requirements.txt).
"""

from __future__ import annotations

import hashlib
import pathlib
import re
import shutil
import sys
import unicodedata
import urllib.request

try:
    from fontTools.fontBuilder import FontBuilder
    from fontTools.pens.recordingPen import DecomposingRecordingPen
    from fontTools.pens.transformPen import TransformPen
    from fontTools.pens.ttGlyphPen import TTGlyphPen
    from fontTools.ttLib import TTFont
except ImportError:
    sys.exit("fontTools 가 없다 — local/editor/eqfont/venv 의 파이썬으로 돌릴 것(tools/editor/eqfont-requirements.txt)")

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOWNLOADS = ROOT / "local" / "editor" / "eqfont" / "downloads"

STIX_COMMIT = "37308d44583ef882dc8b544aaa2e80ac8c406ebf"  # 태그 v2.13
STIX_BASE = f"https://raw.githubusercontent.com/stipub/stixfonts/{STIX_COMMIT}/"
SOURCES = {
    # 받을 자리 → (저장소 안 경로, SHA-256). woff2 의 git blob 은 e9ef88c1…(태그의 트리와 같다).
    "STIXTwoMath-Regular.woff2": ("fonts/static_ttf_woff2/STIXTwoMath-Regular.woff2",
                                  "f2d7b22065da14ffa50eb4236b2bd6dfaa97ead592a02da5e20d85cf556f445b"),
    "OFL.txt": ("OFL.txt", "bec17cee6412788db45fd2644b301afbc99cc5d372ddd3345dc4a7bfdefb9f04"),
}
# 지은 글꼴의 이름표에 들어가면 안 되는 이름 — OFL 의 Reserved Font Name 과 상표.
FORBIDDEN_NAMES = ("TM Math", "STIX")

UPM = 1024  # rhwp 표의 단위와 같게 둔다 — 전진 폭을 반올림 없이 그대로 적는다.
ROLES = {"Variable", "Number", "Operator", "Greek", "BigOperator", "SmallOperator", "LegacyLiteral", "QuotedLiteral"}

# 그리스 수학 이탤릭 가운데 rhwp 가 다른 꼴(기호형)을 고른 것 — `layout/source_greek_symbol.rs` 의 표시 글자와 맞춘다.
GREEK_DISPLAY = {"ε": "MATHEMATICAL ITALIC EPSILON SYMBOL", "φ": "MATHEMATICAL ITALIC PHI SYMBOL"}
# 원 글꼴에서 어느 글리프를 쓰는가 — 표에 든 글자가 곧 그릴 글자가 아닌 것만.
SOURCE_OVERRIDES = {"-": "−", "․": "⋅"}


class TableChanged(SystemExit):
    pass


def changed(what: str) -> TableChanged:
    return TableChanged(f"rhwp 의 수식 글리프 표의 꼴이 바뀌었다 — {what}. 새 판의 hancom_glyphs.rs 를 읽고 make_eq_font.py 를 고칠 것")


# ---------------------------------------------------------------------------------------------------------------------- 표 읽기


def strip_comments(text: str) -> str:
    return re.sub(r"//[^\n]*", "", text)


def one(pattern: str, text: str, what: str, flags: int = 0) -> re.Match:
    found = list(re.finditer(pattern, text, flags))
    if len(found) != 1:
        raise changed(f"{what}({len(found)}곳)")
    return found[0]


def number_array(text: str, name: str, kind: str) -> list[int]:
    m = one(rf"const {name}: \[{kind}; (\d+)\] = \[(.*?)\];", text, f"배열 {name}", re.S)
    values = [int(v, 0) for v in re.findall(r"0x[0-9a-fA-F]+|\d+", m.group(2))]
    if len(values) != int(m.group(1)):
        raise changed(f"배열 {name} 의 길이")
    return values


def rust_char(literal: str) -> str:
    """'x'·'\\''·'\\u{..}' 의 안쪽."""
    if literal.startswith("\\u{"):
        return chr(int(literal[3:-1], 16))
    if literal.startswith("\\"):
        return {"\\'": "'", "\\\\": "\\", '\\"': '"'}[literal]
    if len(literal) != 1:
        raise changed(f"글자 상수 {literal!r}")
    return literal


CHAR = r"'(\\u\{[0-9a-fA-F]+\}|\\.|[^'\\])'"


def match_block(body: str, role: str) -> list[tuple[str, int, int]]:
    """`Role::X => match ch { 'c' => (0x…, n), … _ => return None, },` → [(글자, 코드, 폭)]. 갈래가 아닌 줄이 있으면 멈춘다."""
    m = one(rf"Role::{role} => match ch \{{(.*?)\n\s*_ => return None,\s*\}},", body, f"{role} 의 갈래", re.S)
    arms = []
    for line in m.group(1).splitlines():
        line = line.strip()
        if not line:
            continue
        a = re.fullmatch(rf"{CHAR} => \((0x[0-9a-fA-F]+), (\d+)\),", line)
        if not a:
            raise changed(f"{role} 의 갈래 {line!r}")
        arms.append((rust_char(a.group(1)), int(a.group(2), 16), int(a.group(3))))
    if not arms:
        raise changed(f"{role} 의 갈래가 비었다")
    return arms


class Entry:
    """그릴 코드 하나 — 어느 글자를 어떤 역할로 그리는지와 전진 폭(UPM 1024)."""

    def __init__(self, code: int, char: str, role: str, italic: bool, advance: int, shifted: bool):
        self.code, self.char, self.role, self.italic, self.advance, self.shifted = code, char, role, italic, advance, shifted

    def key(self) -> tuple:
        return self.char, self.role, self.italic


def read_table(rhwp_src: pathlib.Path) -> tuple[str, list[Entry], dict[str, float], dict[int, int]]:
    """rhwp 소스에서 (HYhwpEQ 가족 이름, 그릴 코드 목록, 가족별 기준선 이동(em), HancomEQN 전진 폭) 을 읽는다."""
    path = rhwp_src / "src" / "renderer" / "equation" / "hancom_glyphs.rs"
    metrics_path = rhwp_src / "src" / "renderer" / "equation" / "hancom_eqn_metrics.rs"
    for p in (path, metrics_path):
        if not p.is_file():
            sys.exit(f"rhwp 소스에 {p.relative_to(rhwp_src)} 가 없다 — build_rhwp.py 의 SPARSE 를 볼 것")
    raw = path.read_text(encoding="utf-8")
    text = strip_comments(raw)

    family = one(r'pub const FONT_FAMILY: &str = "([^"]+)";', text, "FONT_FAMILY").group(1)

    enum = one(r"enum Role \{(.*?)\n\}", text, "Role 열거", re.S).group(1)
    variants = set(re.findall(r"^\s*([A-Z]\w*)", enum, re.M))
    if variants != ROLES:
        raise changed(f"Role 의 갈래 {sorted(variants ^ ROLES)}")

    italic_lower = number_array(text, "ITALIC_LOWER", "u16")
    italic_upper = number_array(text, "ITALIC_UPPER", "u16")
    roman_lower = number_array(text, "ROMAN_LOWER", "u16")
    roman_upper = number_array(text, "ROMAN_UPPER", "u16")
    numbers = number_array(text, "NUMBERS", "u16")
    for name, arr, n in (("ITALIC_LOWER", italic_lower, 26), ("ITALIC_UPPER", italic_upper, 26), ("ROMAN_LOWER", roman_lower, 26),
                         ("ROMAN_UPPER", roman_upper, 26), ("NUMBERS", numbers, 10)):
        if len(arr) != n:
            raise changed(f"{name} 의 길이 {len(arr)}")

    body = one(r"fn glyph\(ch: char, role: Role\) -> Option<\(char, u16\)> \{(.*?)\n\}\n", text, "fn glyph", re.S).group(1)
    used = set(re.findall(r"Role::(\w+)", body))
    if used != ROLES:
        raise changed(f"fn glyph 가 다루는 역할 {sorted(used ^ ROLES)}")

    # 라틴 변수 — 기울인 것은 PUA, 곧은 것은 그 글자 그대로.
    il = int(one(r"\((0x[0-9a-fA-F]+) \+ index as u32, ITALIC_LOWER\[index\]\)", body, "기울인 소문자").group(1), 16)
    iu = int(one(r"\((0x[0-9a-fA-F]+) \+ index as u32, ITALIC_UPPER\[index\]\)", body, "기울인 대문자").group(1), 16)
    one(r"\(ch as u32, ROMAN_LOWER\[index\]\)", body, "곧은 소문자")
    one(r"\(ch as u32, ROMAN_UPPER\[index\]\)", body, "곧은 대문자")
    num = one(r"let code = if index == 0 \{\s*(0x[0-9a-fA-F]+)\s*\} else \{\s*(0x[0-9a-fA-F]+) \+ index as u32\s*\};\s*\(code, NUMBERS\[index\]\)",
              body, "숫자", re.S)
    zero, digit_base = int(num.group(1), 16), int(num.group(2), 16)

    # 이 셋이 그대로여야 아래의 '어느 역할을 어느 가족에 담고, 무엇을 옮겨 그리는가' 가 맞다.
    measure = one(r"pub\(super\) fn measure\((.*?)\n\}\n", text, "fn measure", re.S).group(1)
    flat = re.sub(r"\s+", " ", measure)
    one(re.escape('if font_family != "HancomEQN" && matches!(role, Role::Greek | Role::BigOperator) { return None; }'), flat,
        "HYhwpEQ 가 그리스·큰 연산자를 넘기는 조건")
    one(re.escape("if matches!(role, Role::Variable { italic: true }) && ch.is_ascii_uppercase() { 0.333 } else { 0.0 }"), flat,
        "기울인 대문자의 기울임 행렬")
    one(re.escape('baseline_shift: if matches!(role, Role::Variable { italic: false }) || matches!(role, Role::LegacyLiteral) && text != "%" '
                  '|| matches!(role, Role::Operator) && text.is_ascii() {'), flat, "기준선을 옮기는 조건")
    shift = one(r'fs \* if font_family == "HancomEQN" \{ (-?[0-9.]+) \} else \{ ([0-9.]+) / ([0-9.]+) - ([0-9.]+) \}', flat, "기준선 이동")
    shifts = {"HancomEQN": float(shift.group(1)), family: float(shift.group(2)) / float(shift.group(3)) - float(shift.group(4))}

    entries: list[Entry] = []
    for i in range(26):
        lo, up = chr(ord("a") + i), chr(ord("A") + i)
        entries.append(Entry(il + i, lo, "Variable", True, italic_lower[i], False))
        entries.append(Entry(iu + i, up, "Variable", True, italic_upper[i], False))
        entries.append(Entry(ord(lo), lo, "Variable", False, roman_lower[i], True))
        entries.append(Entry(ord(up), up, "Variable", False, roman_upper[i], True))
    for i in range(10):
        entries.append(Entry(zero if i == 0 else digit_base + i, str(i), "Number", False, numbers[i], False))
    for ch, code, adv in match_block(body, "Operator"):
        entries.append(Entry(code, ch, "Operator", False, adv, ch.isascii()))
    for ch, code, adv in match_block(body, "LegacyLiteral"):
        entries.append(Entry(code, ch, "LegacyLiteral", False, adv, ch != "%"))
    for role in ("BigOperator", "SmallOperator", "QuotedLiteral"):
        g = one(rf"Role::{role} if ch == {CHAR} => \((0x[0-9a-fA-F]+), (\d+)\),", body, role)
        entries.append(Entry(int(g.group(2), 16), rust_char(g.group(1)), role, False, int(g.group(3)), False))
    lower = one(r'if let Some\(index\) = "([^"]+)"\.chars\(\)\.position\(\|c\| c == ch\)\s*\{\s*\((0x[0-9a-fA-F]+) \+ index as u32, (\d+)\)',
                body, "그리스 소문자", re.S)
    upper = one(r'let index = "([^"]+)"\.chars\(\)\.position\(\|c\| c == ch\)\?;\s*\((0x[0-9a-fA-F]+) \+ index as u32, (\d+)\)',
                body, "그리스 대문자", re.S)
    for g in (lower, upper):
        for i, ch in enumerate(g.group(1)):
            entries.append(Entry(int(g.group(2), 16) + i, ch, "Greek", False, int(g.group(3)), False))

    # 위 조각들만 찾으면 새 갈래(예: `Role::Number if ch == '.' => (0xe0.., n)`)가 생겨도 그냥 지나간다 — 그 글자는 말없이 네모가 된다.
    # 본문의 16진 코드 글자 수가 읽어 낸 것과 같아야 한다(라틴 변수 둘·숫자 둘·그리스 둘·연산자·옛 기호·가드 셋).
    consumed = 2 + 2 + 2 + len(match_block(body, "Operator")) + len(match_block(body, "LegacyLiteral")) + 3
    literals = len(re.findall(r"0x[0-9a-fA-F]+", body))
    if literals != consumed:
        raise changed(f"fn glyph 의 코드 글자 {literals}개 가운데 {consumed}개만 읽었다")
    # rhwp 는 기준선 이동을 런(text) 단위로 정하고 여기서는 글자 단위로 굳힌다 — 연산자·옛 기호가 한 글자씩 런이 되는 동안만 같다.

    codes = number_array(strip_comments(metrics_path.read_text(encoding="utf-8")), "CODES", "u32")
    hmtx = number_array(strip_comments(metrics_path.read_text(encoding="utf-8")), "HMTX", "u16")
    if len(codes) != len(hmtx):
        raise changed("hancom_eqn_metrics.rs 의 CODES 와 HMTX 길이")
    return family, entries, shifts, dict(zip(codes, hmtx))


# ---------------------------------------------------------------------------------------------------------------------- 원 글꼴


def fetch_sources() -> dict[str, pathlib.Path]:
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    got = {}
    for name, (rel, sha) in SOURCES.items():
        path = DOWNLOADS / name
        if not path.is_file():
            print(f"원 글꼴: {STIX_BASE}{rel} 를 받는다", flush=True)
            req = urllib.request.Request(STIX_BASE + rel, headers={"User-Agent": "iroiro-viewer-editor-build"})
            partial = DOWNLOADS / (name + ".partial")
            with urllib.request.urlopen(req, timeout=120) as r, partial.open("wb") as f:
                shutil.copyfileobj(r, f)
            partial.replace(path)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != sha:
            path.unlink()
            sys.exit(f"{name} 의 SHA-256 이 다르다 — 지웠다. 받은 것: {digest}")
        got[name] = path
    return got


def source_char(e: Entry) -> str:
    """원 글꼴에서 가져올 글자 — 기울인 소문자·그리스는 수학 이탤릭, 기울인 대문자는 곧은 글자(rhwp 가 기울인다)."""
    if e.role == "Variable" and e.italic and e.char.islower():
        try:
            return unicodedata.lookup(f"MATHEMATICAL ITALIC SMALL {e.char.upper()}")
        except KeyError:
            return "ℎ"  # 수학 이탤릭 h 의 자리는 비어 있고 플랑크 상수가 그 글자다
    if e.role == "Greek":
        if e.char in GREEK_DISPLAY:
            return unicodedata.lookup(GREEK_DISPLAY[e.char])
        name = unicodedata.name(e.char)  # GREEK SMALL LETTER ALPHA
        case = "SMALL" if "SMALL" in name else "CAPITAL"
        return unicodedata.lookup(f"MATHEMATICAL ITALIC {case} {name.split('LETTER ')[1]}")
    return SOURCE_OVERRIDES.get(e.char, e.char)


# ---------------------------------------------------------------------------------------------------------------------- 짓기


def build_font(css_family: str, entries: list[Entry], shift_em: float, advances: dict[int, int], src: TTFont,
               notice: str, out: pathlib.Path) -> tuple[int, list[str]]:
    """한 가족의 글꼴 — 코드마다 원 글리프를 전진 폭 가운데에 두고, rhwp 가 옮겨 그리는 글자는 그만큼 거꾸로 옮긴다."""
    cmap_src = src.getBestCmap()
    glyph_set = src.getGlyphSet()
    scale = UPM / src["head"].unitsPerEm
    order, cmap, glyphs, metrics, notes = [".notdef"], {}, {}, {}, []

    pen = TTGlyphPen(None)  # .notdef — 빈 상자(바깥 윤곽은 시계 방향, 안쪽은 반대)
    w = UPM // 2
    for points in (((50, 0), (50, 700), (w - 50, 700), (w - 50, 0)), ((100, 50), (w - 100, 50), (w - 100, 650), (100, 650))):
        pen.moveTo(points[0])
        for p in points[1:]:
            pen.lineTo(p)
        pen.closePath()
    glyphs[".notdef"] = pen.glyph()
    metrics[".notdef"] = (w, 50)

    for e in sorted(entries, key=lambda e: e.code):
        ch = source_char(e)
        name_src = cmap_src.get(ord(ch))
        if name_src is None:
            sys.exit(f"원 글꼴에 U+{ord(ch):04X}({ch}) 가 없다 — {e.role} {e.char!r}")
        advance = advances.get(e.code, e.advance)
        if advance <= 0:
            sys.exit(f"U+{e.code:04X}({e.char}) 의 전진 폭을 모른다")
        src_adv = glyph_set[name_src].width * scale
        dx = (advance - src_adv) / 2  # 원 글리프의 좌우 여백을 그대로 두고 새 폭의 가운데로
        dy = shift_em * UPM if e.shifted else 0.0  # rhwp 가 fs·shift(음수 — 위로)만큼 옮겨 그린다 — 글리프는 그만큼 아래로
        rec = DecomposingRecordingPen(glyph_set)
        glyph_set[name_src].draw(rec)
        tt = TTGlyphPen(None)
        rec.replay(TransformPen(tt, (scale, 0, 0, scale, dx, dy)))
        name = f"u{e.code:04X}"
        order.append(name)
        glyphs[name] = tt.glyph()
        cmap[e.code] = name
        metrics[name] = (advance, 0)
        notes.append(f"U+{e.code:04X} ← {e.role}{'(기울임)' if e.italic else ''} {e.char!r} = 원 글꼴 U+{ord(ch):04X} {name_src}, "
                     f"폭 {advance}/1024" + (f", 아래로 {-dy:.1f}" if e.shifted else ""))

    fb = FontBuilder(UPM, isTTF=True)
    fb.font.recalcTimestamp = False
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    glyf = fb.font["glyf"]
    for name in order:
        g = glyf[name]
        g.recalcBounds(glyf)
        metrics[name] = (metrics[name][0], getattr(g, "xMin", 0) if g.numberOfContours else 0)
    fb.setupHorizontalMetrics(metrics)
    y_max = max((getattr(glyf[n], "yMax", 0) for n in order), default=0)
    y_min = min((getattr(glyf[n], "yMin", 0) for n in order), default=0)
    ascent, descent = round(src["hhea"].ascent * scale), round(src["hhea"].descent * scale)
    fb.setupHorizontalHeader(ascent=ascent, descent=descent)
    label = "Iroiro EQ Substitute " + ("N" if css_family == "HancomEQN" else "H")
    copyright_line = (src["name"].getDebugName(0) or "").strip()
    names = {
        "copyright": f"{copyright_line}. Portions copyright 1998-2003 by MicroPress, Inc. Modified version (glyph subset re-encoded) "
                     "made by the iroiro-viewer build tool tools/editor/make_eq_font.py.",
        "familyName": label,
        "styleName": "Regular",
        "uniqueFontIdentifier": f"{label.replace(' ', '')}-Regular;1.0",
        "fullName": f"{label} Regular",
        "psName": f"{label.replace(' ', '')}-Regular",
        "version": "Version 1.0",
        "description": notice,
        "licenseDescription": "This Font Software is licensed under the SIL Open Font License, Version 1.1. "
                              "This license is available with a FAQ at: https://openfontlicense.org",
        "licenseInfoURL": "https://openfontlicense.org",
    }
    for v in names.values():
        for bad in FORBIDDEN_NAMES:
            if bad in v and v is not names["copyright"]:
                sys.exit(f"이름표에 '{bad}' 가 들어갔다 — OFL 의 Reserved Font Name·상표를 쓰지 않는다")
    fb.setupNameTable(names)
    fb.setupOS2(sTypoAscender=ascent, sTypoDescender=descent, sTypoLineGap=0, usWinAscent=max(ascent, y_max),
                usWinDescent=max(-descent, -y_min), achVendID="NONE", fsType=0, fsSelection=0x40)  # REGULAR
    fb.setupPost()
    fb.font["head"].created = fb.font["head"].modified = src["head"].modified
    fb.font.flavor = "woff2"
    fb.save(str(out))
    return len(order) - 1, notes


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    if len(sys.argv) != 3:
        sys.exit("쓰는 법: make_eq_font.py <rhwp 소스 폴더> <써 낼 폴더>")
    rhwp_src, out_dir = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    family, entries, shifts, eqn_hmtx = read_table(rhwp_src)

    seen: dict[int, Entry] = {}
    for e in entries:
        prev = seen.get(e.code)
        if prev and (source_char(prev), prev.shifted, prev.advance) != (source_char(e), e.shifted, e.advance):
            raise changed(f"U+{e.code:04X} 를 두 글자({prev.char!r}·{e.char!r})가 쓴다")
        seen.setdefault(e.code, e)

    sources = fetch_sources()
    src = TTFont(str(sources["STIXTwoMath-Regular.woff2"]))
    out_dir.mkdir(parents=True, exist_ok=True)
    notice = ("Substitute for the equation glyph codes that the rhwp HWP editor draws in the HYhwpEQ/HancomEQN equation fonts. "
              "Glyphs are taken from an SIL OFL 1.1 font and placed at those code points; this is not the Hancom font and is not "
              "affiliated with Hancom Inc.")

    report = []
    for css_family, keep, advances in (
        # HYhwpEQ 는 그리스·큰 연산자를 이 길로 보내지 않는다(fn measure).
        (family, lambda e: e.role not in ("Greek", "BigOperator"), {}),
        ("HancomEQN", lambda e: True, eqn_hmtx),
    ):
        chosen = {}
        for e in entries:
            if keep(e):
                chosen.setdefault(e.code, e)
        path = out_dir / f"{css_family}-iroiro.woff2"
        count, notes = build_font(css_family, list(chosen.values()), shifts[css_family], advances, src, notice, path)
        report.append((css_family, path, count, notes))
        print(f"{css_family}: 글자 {count}개 → {path.name} {path.stat().st_size:,} 바이트 (정체·ASCII 연산자 {shifts[css_family]:+.4f}em 보정)")

    shutil.copy2(sources["OFL.txt"], out_dir / "STIXTwoMath-OFL.txt")
    lines = [
        "한컴 수식 글자의 대체 글꼴 — tools/editor/make_eq_font.py 가 지었다. 손으로 고치지 않는다.",
        "",
        "rhwp(0.9 부터)는 수식 글꼴이 HYhwpEQ·HancomEQN 이면 숫자·기호·기울인 글자를 그 글꼴의 사용자 영역 코드로 그린다.",
        "두 글꼴은 이 앱에 없으므로(한컴의 상용 글꼴이다), 자유 글꼴의 글리프를 그 코드 자리에 옮겨 담은 글꼴을 같은 이름의 가족으로 싣는다.",
        "한컴의 글꼴이 아니며 한컴과 관계가 없다.",
        "",
        f"원 글꼴: STIX Two Math 2.13 — https://github.com/stipub/stixfonts (커밋 {STIX_COMMIT})",
        f"  파일 {SOURCES['STIXTwoMath-Regular.woff2'][0]} SHA-256 {SOURCES['STIXTwoMath-Regular.woff2'][1]}",
        "  저작권: Copyright 2001-2021 The STIX Fonts Project Authors (https://github.com/stipub/stixfonts);",
        "          Portions copyright 1998-2003 by MicroPress, Inc., with Reserved Font Name TM Math.",
        "  라이선스: SIL Open Font License 1.1 — 전문은 STIXTwoMath-OFL.txt(원 저장소의 OFL.txt 그대로)와 OFL-1.1.txt.",
        "  STIX Fonts 는 IEEE 의 상표다. 지은 글꼴의 이름(Iroiro EQ Substitute H·N)은 원 글꼴의 이름·Reserved Font Name 을 쓰지 않는다.",
        "바꾼 것: 글리프 일부를 골라 다른 코드 자리에 두고, 전진 폭을 rhwp 의 표(UPM 1024)에 맞춰 가운데로 옮기고, rhwp 가 기준선을 옮겨",
        "         그리는 글자(정체 라틴·ASCII 연산자·일부 옛 기호)는 그만큼 반대로 옮겼다. 윤곽 자체는 바꾸지 않았다(배율만 1000 → 1024).",
        f"표의 출처: rhwp 소스의 src/renderer/equation/hancom_glyphs.rs(fn glyph)·hancom_eqn_metrics.rs(CODES·HMTX)",
        "",
    ]
    for css_family, path, count, notes in report:
        lines.append(f"== {path.name} — CSS 가족 '{css_family}', 글자 {count}개")
        lines.extend("  " + n for n in notes)
        lines.append("")
    (out_dir / "EQ-SUBSTITUTE-FONTS.txt").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
