"""ONLYOFFICE 편집기의 글꼴 목록(`sdkjs/common/AllFonts.js`)과 글꼴 목록 그림(`fonts_thumbnail*.png`)을 짓는다.

ONLYOFFICE 는 글자를 제 글꼴 엔진(FreeType 을 웹어셈블리로)으로 그리므로 기기의 글꼴을 쓰지 않는다 — 실은 글꼴과 그 목록이 전부다. 목록은 원래
문서 서버의 `allfontsgen`(네이티브)이 짓는데, 우리는 실을 글꼴을 바꾸므로(독점 글꼴을 빼고 한글 글꼴을 더한다) 같은 꼴을 여기서 짓는다.

AllFonts.js 의 꼴(판 2 — sdkjs 의 `checkAllFonts`·`CFontSelect.fromStream` 을 읽고 맞췄다. CryptPad 묶음의 것을 풀어 다시 지어 바이트가
같은 것을 확인한다 — `--selftest`):
- `__fonts_files`: 글꼴 파일의 자리(`/fonts/…`). 편집기는 `../../../../fonts/` + 이 자리로 받는다.
- `__fonts_infos`: [가족 이름, 보통 파일 번호, 면 번호, 기울임 …, 굵게 …, 굵은 기울임 …] — 없는 꼴은 -1. 목록 그림의 줄 번호가 이 차례다.
- `g_fonts_selection_bin`: 면마다 글꼴을 고르는 데 쓰는 값(OS/2 의 panose·유니코드 범위·코드 페이지·굵기·높이 …)을 base64 로.
  높이 값은 1000 단위로 환산해 0 쪽으로 버린다(allfontsgen 과 같다 — 실물로 맞췄다).

표준 라이브러리 + brotli(WOFF2 의 표를 푼다) + Pillow(목록 그림)만 쓴다.
"""

from __future__ import annotations

import base64
import io
import json
import re
import struct
import unicodedata
from dataclasses import dataclass, field

try:
    import brotli  # WOFF2
except ImportError:  # pragma: no cover - WOFF2 를 싣지 않는 빌드면 없어도 된다
    brotli = None


# ---------------------------------------------------------------------------------------------------------------------- 글꼴 읽기


def _sfnt_tables(data: bytes, offset: int = 0) -> dict[bytes, bytes]:
    """TTF·OTF(또는 TTC 의 면 하나)의 표를 꺼낸다."""
    num = struct.unpack_from(">H", data, offset + 4)[0]
    out = {}
    for i in range(num):
        tag, _cs, off, ln = struct.unpack_from(">4sIII", data, offset + 12 + 16 * i)
        out[tag] = data[off:off + ln]
    return out


_WOFF2_TAGS = [
    b"cmap", b"head", b"hhea", b"hmtx", b"maxp", b"name", b"OS/2", b"post", b"cvt ", b"fpgm", b"glyf", b"loca", b"prep", b"CFF ",
    b"VORG", b"EBDT", b"EBLC", b"gasp", b"hdmx", b"kern", b"LTSH", b"PCLT", b"VDMX", b"vhea", b"vmtx", b"BASE", b"GDEF", b"GPOS",
    b"GSUB", b"EBSC", b"JSTF", b"MATH", b"CBDT", b"CBLC", b"COLR", b"CPAL", b"SVG ", b"sbix", b"acnt", b"avar", b"bdat", b"bloc",
    b"bsln", b"cvar", b"fdsc", b"feat", b"fmtx", b"fvar", b"gvar", b"hsty", b"just", b"lcar", b"mort", b"morx", b"opbd", b"prop",
    b"trak", b"Zapf", b"Silf", b"Glat", b"Gloc", b"Feat", b"Sill",
]


def _base128(data: bytes, pos: int) -> tuple[int, int]:
    v = 0
    for i in range(5):
        b = data[pos]
        pos += 1
        if i == 0 and b == 0x80:
            raise ValueError("WOFF2: UIntBase128 의 앞 0")
        if v & 0xFE000000:
            raise ValueError("WOFF2: UIntBase128 넘침")
        v = (v << 7) | (b & 0x7F)
        if not b & 0x80:
            return v, pos
    raise ValueError("WOFF2: UIntBase128 가 길다")


def _woff2_tables(data: bytes) -> tuple[dict[bytes, bytes], bytes]:
    """WOFF2 의 표 가운데 바뀌지 않은 것(name·OS/2·head·hhea·post …)을 꺼낸다 — glyf·loca 는 되살리지 않는다(쓰지 않는다)."""
    if brotli is None:
        raise RuntimeError("WOFF2 를 읽으려면 brotli 가 필요하다")
    flavor = data[4:8]
    num_tables = struct.unpack_from(">H", data, 12)[0]
    total_compressed = struct.unpack_from(">I", data, 20)[0]
    pos = 48
    entries = []
    for _ in range(num_tables):
        flags = data[pos]
        pos += 1
        idx = flags & 0x3F
        if idx == 63:
            tag = data[pos:pos + 4]
            pos += 4
        else:
            tag = _WOFF2_TAGS[idx]
        length, pos = _base128(data, pos)
        transform = (flags >> 6) & 3
        transformed = (tag in (b"glyf", b"loca") and transform == 0) or (tag not in (b"glyf", b"loca") and transform != 0)
        tlen = length
        if transformed:
            tlen, pos = _base128(data, pos)
        entries.append((tag, length, tlen, transformed))
    if flavor == b"ttcf":
        raise ValueError("WOFF2 글꼴 모음은 다루지 않는다")
    stream = brotli.decompress(data[pos:pos + total_compressed])
    out = {}
    off = 0
    for tag, _length, tlen, transformed in entries:
        if not transformed:
            out[tag] = stream[off:off + tlen]
        off += tlen
    return out, flavor


@dataclass
class Face:
    path: str                 # 편집기가 받는 자리 — `/fonts/…`
    family: str               # 가족 이름(name 1, 없으면 16)
    names: list[str] = field(default_factory=list)  # 다른 말의 가족 이름(목록이 한중일 화면에서 보인다)
    index: int = 0
    italic: bool = False
    bold: bool = False
    fixed: bool = False
    panose: bytes = b"\0" * 10
    ur: tuple[int, int, int, int] = (0, 0, 0, 0)
    cp: tuple[int, int] = (0, 0)
    weight: int = 400
    width: int = 5
    family_class: int = 0
    font_format: int = 1      # 1 TrueType(glyf), 2 CFF
    avg: int = 0
    asc: int = 0
    desc: int = 0
    gap: int = 0
    xh: int = 0
    ch: int = 0
    fs_type: int = 0
    raw: bytes = b""          # 목록 그림을 그릴 때 쓴다
    cmap: frozenset = frozenset()  # 그 글꼴이 그리는 글자(목록 그림 — 이름을 제 글꼴로 그릴 수 있는가)


def _names(table: bytes) -> dict[tuple[int, int, int, int], str]:
    fmt, count, so = struct.unpack_from(">HHH", table, 0)
    out = {}
    for i in range(count):
        pid, eid, lid, nid, ln, off = struct.unpack_from(">HHHHHH", table, 6 + 12 * i)
        raw = table[so + off:so + off + ln]
        try:
            if pid == 3 or pid == 0:
                s = raw.decode("utf-16-be")
            elif pid == 1 and eid == 0:
                s = raw.decode("mac_roman")
            else:
                continue
        except UnicodeDecodeError:
            continue
        out[(pid, eid, lid, nid)] = s
    return out


def _name(names: dict, nid: int) -> str | None:
    for key in ((3, 1, 0x409, nid), (3, 10, 0x409, nid), (1, 0, 0, nid)):
        if key in names:
            return names[key]
    for (pid, _eid, _lid, n), s in names.items():
        if n == nid and pid == 3:
            return s
    return None


def _cmap(table: bytes) -> frozenset:
    """글자 표의 유니코드 하위 표(3,10 → 3,1 → 0,*) 하나에서 그리는 글자를 모은다(형식 4·12)."""
    if not table:
        return frozenset()
    num = struct.unpack_from(">H", table, 2)[0]
    subs = {}
    for i in range(num):
        pid, eid, off = struct.unpack_from(">HHI", table, 4 + 8 * i)
        subs[(pid, eid)] = off
    off = next((subs[k] for k in ((3, 10), (3, 1), (0, 4), (0, 3), (0, 6), (0, 1), (0, 0)) if k in subs), None)
    if off is None:
        return frozenset()
    fmt = struct.unpack_from(">H", table, off)[0]
    out = set()
    if fmt == 4:
        seg2 = struct.unpack_from(">H", table, off + 6)[0]
        ends = struct.unpack_from(f">{seg2 // 2}H", table, off + 14)
        starts = struct.unpack_from(f">{seg2 // 2}H", table, off + 16 + seg2)
        for st, en in zip(starts, ends):
            if st != 0xFFFF:
                out.update(range(st, en + 1))
    elif fmt == 12:
        n = struct.unpack_from(">I", table, off + 12)[0]
        for i in range(n):
            st, en, _g = struct.unpack_from(">III", table, off + 16 + 12 * i)
            if en - st < 0x30000:
                out.update(range(st, en + 1))
    return frozenset(out)


def _scale(v: int, upem: int) -> int:
    return int(v * 1000 / upem)  # 0 쪽으로 버린다(allfontsgen 과 같다)


def read_face(data: bytes, path: str, *, family_override: str | None = None) -> Face:
    """글꼴 파일 하나(TTF·OTF·WOFF2)의 면 0 을 읽는다."""
    sig = data[:4]
    if sig == b"wOF2":
        tables, flavor = _woff2_tables(data)
        cff = flavor == b"OTTO"
    elif sig in (b"\0\1\0\0", b"true", b"OTTO"):
        tables = _sfnt_tables(data)
        cff = sig == b"OTTO"
    else:
        raise ValueError(f"모르는 글꼴 꼴: {path}")
    names = _names(tables[b"name"])
    family = family_override or _name(names, 1) or _name(names, 16)
    if not family:
        raise ValueError(f"가족 이름이 없다: {path}")
    # 다른 말의 가족 이름(이름 1, 영어가 아닌 것의 첫째 하나) — 한중일 화면의 목록이 보인다(CheckNamesForInterface).
    # allfontsgen 도 하나만 싣는다(번체·간체가 둘 다 적힌 WenQuanYi 에서 앞의 것 — 실물로 맞췄다).
    alt = []
    for (pid, _eid, lid, nid), s in sorted(names.items()):
        if pid == 3 and nid == 1 and lid != 0x409 and s != family:
            alt.append(s)
            break
    head = tables[b"head"]
    upem = struct.unpack_from(">H", head, 18)[0]
    mac_style = struct.unpack_from(">H", head, 44)[0]
    post = tables.get(b"post", b"")
    fixed = bool(post and struct.unpack_from(">I", post, 12)[0])
    os2 = tables[b"OS/2"]
    version = struct.unpack_from(">H", os2, 0)[0]
    avg, weight, width, fs_type = struct.unpack_from(">hHHH", os2, 2)
    family_class = struct.unpack_from(">h", os2, 30)[0]
    panose = os2[32:42]
    ur = struct.unpack_from(">IIII", os2, 42)
    fs_sel = struct.unpack_from(">H", os2, 62)[0]
    asc, desc, gap = struct.unpack_from(">hhh", os2, 68)
    cp = struct.unpack_from(">II", os2, 78) if version >= 1 and len(os2) >= 86 else (0, 0)
    xh, ch = struct.unpack_from(">hh", os2, 86) if version >= 2 and len(os2) >= 90 else (0, 0)
    sub = (_name(names, 2) or "").lower()
    italic = bool(mac_style & 2) or bool(fs_sel & 1) or "italic" in sub or "oblique" in sub
    bold = bool(mac_style & 1) or bool(fs_sel & 0x20) or sub in ("bold", "bold italic")
    return Face(
        path=path, family=family, names=alt, italic=italic, bold=bold, fixed=fixed, panose=bytes(panose), ur=tuple(ur), cp=tuple(cp),
        weight=weight, width=width, family_class=family_class, font_format=2 if cff else 1, avg=_scale(avg, upem),
        asc=_scale(asc, upem), desc=_scale(desc, upem), gap=_scale(gap, upem), xh=_scale(xh, upem), ch=_scale(ch, upem),
        fs_type=fs_type, raw=data, cmap=_cmap(tables.get(b"cmap", b"")),
    )


# ---------------------------------------------------------------------------------------------------------------------- AllFonts.js


def _utf8(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack("<i", len(b)) + b


def _record(f: Face) -> bytes:
    body = bytearray()
    body += _utf8(f.family)
    body += struct.pack("<i", len(f.names))
    for n in f.names:
        body += _utf8(n)
    body += _utf8(f.path)
    body += struct.pack("<iiii", f.index, 1 if f.italic else 0, 1 if f.bold else 0, 1 if f.fixed else 0)
    body += struct.pack("<i", 10) + f.panose
    body += struct.pack("<IIII", *f.ur) + struct.pack("<II", *f.cp)
    body += struct.pack("<HH", f.weight, f.width)
    body += struct.pack("<hhhhhhhh", f.family_class, f.font_format, f.avg, f.asc, f.desc, f.gap, f.xh, f.ch)
    body += struct.pack("<H", f.fs_type)
    return struct.pack("<i", len(body) + 4) + bytes(body)


def family_key(name: str) -> str:
    return name.casefold()


def build_ranges(infos: list[list], files: list[str], faces_by_path: dict[str, Face], preference: list[str]) -> list[int]:
    """글자별 대체 표(`__fonts_ranges` — [처음,끝, __fonts_infos 의 번호] 의 잇단 셋). 편집기는 지금 글꼴에 없는 글자를 이 표의 글꼴로
    그린다(sdkjs 의 FontPickerByCharacter — 표가 없으면 두부가 된다. CryptPad 의 AllFonts.js 에는 없었다). 글자마다 [preference] 의 차례로
    그 글자를 가진 첫 가족을, 거기 없는 가족은 목록 차례로 고른다."""
    fam_index = {row[0]: i for i, row in enumerate(infos)}
    missing = [n for n in preference if n not in fam_index]
    if missing:
        raise ValueError("대체 차례에 없는 가족: " + ", ".join(missing))
    order = [fam_index[n] for n in preference] + [i for i, row in enumerate(infos) if row[0] not in preference]
    cover = []
    for i in order:
        row = infos[i]
        cps: set[int] = set()
        for k in (1, 3, 5, 7):
            if row[k] >= 0:
                cps |= faces_by_path[files[row[k]]].cmap
        cover.append((i, cps))
    every = sorted(set().union(*(c for _, c in cover)))
    ranges: list[list[int]] = []
    for cp in every:
        if cp < 0x20 or 0xD800 <= cp <= 0xDFFF:
            continue
        owner = next(i for i, c in cover if cp in c)
        if ranges and ranges[-1][1] == cp - 1 and ranges[-1][2] == owner:
            ranges[-1][1] = cp
        else:
            ranges.append([cp, cp, owner])
    return [x for r in ranges for x in r]


def build_allfonts(faces: list[Face], fallback: list[str] | None = None) -> tuple[str, list[list]]:
    """AllFonts.js 의 글과 [가족 이름, R, r, I, i, B, b, BI, bi] 목록(목록 그림의 줄 차례)을 돌려준다. [fallback] 을 주면 글자별 대체 표도
    싣는다(그 차례가 앞선다 — [build_ranges])."""
    files = sorted({f.path for f in faces})
    index_of = {p: i for i, p in enumerate(files)}
    families: dict[str, dict[str, Face]] = {}
    for f in faces:
        style = ("BI" if f.italic else "B") if f.bold else ("I" if f.italic else "R")
        slot = families.setdefault(f.family, {})
        if style in slot:
            raise ValueError(f"같은 가족·같은 꼴이 둘이다: {f.family} {style} — {slot[style].path}, {f.path}")
        slot[style] = f
    infos = []
    for fam in sorted(families, key=family_key):
        slots = families[fam]
        row: list = [fam]
        for style in ("R", "I", "B", "BI"):
            f = slots.get(style)
            row += [index_of[f.path], f.index] if f else [-1, -1]
        infos.append(row)
    sel = bytearray(struct.pack("<i", len(faces)))
    for f in sorted(faces, key=lambda f: f.path):
        sel += _record(f)
    lines = ['﻿window["__all_fonts_js_version__"] = 2;', "", 'window["__fonts_files"] = [']
    lines.append(",\n".join(json.dumps(p, ensure_ascii=False) for p in files))
    lines.append("];")
    lines.append("")
    lines.append('window["__fonts_infos"] = [')
    lines.append(",\n".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) for r in infos))
    lines.append("];")
    lines.append("")
    lines.append('window["g_fonts_selection_bin"] = "' + base64.b64encode(bytes(sel)).decode("ascii") + '";')
    if fallback is not None:
        flat = build_ranges(infos, files, {f.path: f for f in faces}, fallback)
        lines.append("")
        lines.append('window["__fonts_ranges"] = [' + ",".join(str(x) for x in flat) + "];")
    return "\n".join(lines) + "\n", infos


def parse_allfonts(text: str) -> tuple[list[str], list[list], bytes]:
    """시험용 — 이미 있는 AllFonts.js 를 푼다."""
    files = json.loads(re.search(r'window\["__fonts_files"\] = (\[.*?\]);', text, re.S).group(1))
    infos = json.loads(re.search(r'window\["__fonts_infos"\] = (\[.*?\]);', text, re.S).group(1))
    sel = base64.b64decode(re.search(r'window\["g_fonts_selection_bin"\] = "([^"]*)"', text).group(1))
    return files, infos, sel


def split_records(sel: bytes) -> list[bytes]:
    count = struct.unpack_from("<i", sel, 0)[0]
    pos = 4
    out = []
    for _ in range(count):
        ln = struct.unpack_from("<i", sel, pos)[0]
        out.append(sel[pos:pos + ln])
        pos += ln
    if pos != len(sel):
        raise ValueError("선택 표의 끝이 맞지 않는다")
    return out


# ---------------------------------------------------------------------------------------------------------------------- 목록 그림


THUMB_RATIOS = (1, 1.25, 1.5, 1.75, 2)
THUMB_W = 300
THUMB_H = 28


def _is_east_asian(s: str) -> bool:
    for ch in s:
        o = ord(ch)
        if 0x1100 <= o <= 0x11FF or 0x2E80 <= o <= 0x9FFF or 0xA960 <= o <= 0xA97F or 0xAC00 <= o <= 0xD7AF or 0xF900 <= o <= 0xFAFF \
                or 0xFF00 <= o <= 0xFFEF or 0x20000 <= o <= 0x2FFFF:
            return True
    return False


def display_name(family: str, alt: list[str], east_asian_ui: bool) -> str:
    """목록에 보이는 이름 — sdkjs 의 CheckNamesForInterface 와 같은 판단(한중일 화면이면 한중일 이름을)."""
    if not alt:
        return family
    a = alt[0]
    if _is_east_asian(family) == _is_east_asian(a):
        return family
    if east_asian_ui:
        return a if _is_east_asian(a) else family
    return family if not _is_east_asian(family) else a


def build_thumbnails(infos: list[list], faces_by_path: dict[str, Face], files: list[str], east_asian_ui: bool,
                     fallback_latin: Face, fallback_ea: Face) -> dict[str, bytes]:
    """[비율 접미사 → PNG 바이트]. 줄마다 그 가족의 보통 꼴(없으면 첫 꼴)로 이름을 검게, 바탕은 투명으로. 이름의 글자를 그 글꼴이 못
    그리면(라오·크메르·기호 글꼴) 대체 글꼴로 — 원래 그림(allfontsgen)도 그렇다. 기준선은 줄마다 같은 자리(줄 높이 28 의 20)."""
    from PIL import Image, ImageDraw, ImageFont

    out = {}
    for ratio in THUMB_RATIOS:
        w, h = int(THUMB_W * ratio), int(round(THUMB_H * ratio))
        img = Image.new("RGBA", (w, h * len(infos)), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        for row, info in enumerate(infos):
            idx = next(info[i] for i in (1, 3, 5, 7) if info[i] >= 0)
            face = faces_by_path[files[idx]]
            text = display_name(info[0], face.names, east_asian_ui)
            if any(ord(c) not in face.cmap for c in text if not c.isspace()):
                face = fallback_ea if _is_east_asian(text) else fallback_latin
            size = int(round(18 * ratio))
            font = ImageFont.truetype(io.BytesIO(face.raw), size)
            # 글이 칸을 넘으면 줄인다(긴 이름) — 편집기가 폭 300 으로 잘라 보인다.
            while font.getlength(text) > w - 4 * ratio and size > 8:
                size -= 1
                font = ImageFont.truetype(io.BytesIO(face.raw), size)
            draw.text((int(2 * ratio), row * h + int(round(20 * ratio))), text, font=font, fill=(0, 0, 0, 255), anchor="ls")
        buf = io.BytesIO()
        img.save(buf, format="PNG", optimize=True)
        suffix = "" if ratio == 1 else f"@{ratio:g}x"
        out[suffix] = buf.getvalue()
    return out


def normalize(s: str) -> str:
    return unicodedata.normalize("NFC", s)
