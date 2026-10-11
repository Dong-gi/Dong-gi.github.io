"""토렌트 엔진(:torrent)의 파서·암호를 엔진 밖에서 대조할 정답 벡터를 짓는다.

엔진과 같은 이해로 짠 시험은 같은 오해를 함께 통과한다 — 그래서 정답을 엔진의 코드를 보지 않고 PC 의 파이썬으로 따로 짓는다.
.torrent(BEP 3·12·19·47·52 — 한 파일·여러 파일·패드·하이브리드·순수 v2·private·정렬 안 된 info·name.utf-8·트래커 꼴),
마그넷(BEP 9), RC4·MSE(Message Stream Encryption), bencode 의 받을 것과 거부할 것, KRPC(BEP 5·43), UDP 트래커(BEP 15)의
바이트와 기대값을 코틀린 파일 하나(`torrent/src/test/kotlin/io/github/donggi/iroirobrowser/torrent/PythonVectors.kt`)로 쓴다.
토렌트가 담는 파일의 내용은 SHA-1 카운터 흐름이라(그 파일의 `FileSpec` 주석) 시험이 같은 바이트를 지어 조각 해시까지 견줄 수 있다.

쓰기 전에 스스로 대조한다 — 지은 토렌트를 따로 짠 엄격한 bencode 디코더로 다시 풀어 info 의 원래 바이트로 info-hash 를,
파일 내용으로 조각 해시·머클 뿌리·조각 층을 다시 셈, RC4 의 공개 벡터(위키백과·RFC 6229), MSE 의 두 쪽이 같은 S 를 얻고
상대의 바이트에서 동기화해 평문을 되찾는가, 마그넷·KRPC·UDP 를 다시 읽어 기대값과 같은가, 두 번 지어 같은가.

    python -I tools/torrent_vectors.py            # PythonVectors.kt 를 다시 쓴다
    python -I tools/torrent_vectors.py --check    # 쓰지 않고 지금 파일과 같은지만 본다(다르면 끝 코드 1)

표준 라이브러리만 쓴다. 네트워크도 무작위도 쓰지 않는다 — 몇 번을 돌려도 같은 파일이다.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import pathlib
import re
import struct
import sys
import textwrap
import urllib.parse
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT.joinpath("torrent", "src", "test", "kotlin", "io", "github", "donggi", "iroirobrowser", "torrent",
                    "PythonVectors.kt")
HEADER = "// tools/torrent_vectors.py 가 지었다(python -I tools/torrent_vectors.py) — 손으로 고치지 않는다."
PACKAGE = "io.github.donggi.iroirobrowser.torrent"

BLOCK = 16 * 1024          # BEP 52 머클 나무의 잎 하나가 덮는 바이트
LITERAL_LIMIT = 65_535     # 코틀린(JVM) 문자열 상수 하나의 바이트 상한
COMMENT_WIDTH = 100        # 지은 코틀린 주석의 한 줄 글자 수
MAX_DEPTH = 64             # 대조용 디코더의 깊이 상한(토렌트 검토가 권한 값)
LONG_MIN, LONG_MAX = -(1 << 63), (1 << 63) - 1


class VectorError(Exception):
    """스스로 대조하다 어긋났다 — 파일을 쓰지 않는다."""


def need(ok: bool, what: str) -> None:
    if not ok:
        raise VectorError(what)


def sha1(*parts: bytes) -> bytes:
    h = hashlib.sha1()
    for p in parts:
        h.update(p)
    return h.digest()


def sha256(*parts: bytes) -> bytes:
    h = hashlib.sha256()
    for p in parts:
        h.update(p)
    return h.digest()


def xor(a: bytes, b: bytes) -> bytes:
    need(len(a) == len(b), "xor 의 길이")
    return bytes(x ^ y for x, y in zip(a, b))


def ceil_div(a: int, b: int) -> int:
    return -(-a // b)


# ── 결정적 바이트 ──

def file_bytes(seed: int, length: int) -> bytes:
    """FileSpec 의 내용 — SHA1(("iroiro-" + seed + "-" + c) 의 UTF-8) 를 c = 0,1,2,… 차례로 이은 앞 length 바이트."""
    out = bytearray()
    c = 0
    while len(out) < length:
        out += sha1(f"iroiro-{seed}-{c}".encode("utf-8"))
        c += 1
    return bytes(out[:length])


def filler(label: str, length: int) -> bytes:
    """벡터 안의 '무작위' 바이트(패드·키·평문) — SHA-256(label + '#' + c) 를 이은 것. 늘 같다."""
    out = bytearray()
    c = 0
    while len(out) < length:
        out += sha256(f"{label}#{c}".encode("utf-8"))
        c += 1
    return bytes(out[:length])


# ── bencode ──

def benc(v: object) -> bytes:
    """bencode — 사전의 키는 원래 바이트의 차례로 정렬한다(BEP 3). str 은 UTF-8 로 쓴다."""
    if isinstance(v, bool):
        raise TypeError("bool 은 bencode 에 없다")
    if isinstance(v, int):
        return b"i%de" % v
    if isinstance(v, str):
        v = v.encode("utf-8")
    if isinstance(v, bytes):
        return b"%d:%s" % (len(v), v)
    if isinstance(v, list):
        return b"l" + b"".join(benc(x) for x in v) + b"e"
    if isinstance(v, dict):
        return benc_dict([(k, benc(x)) for k, x in v.items()])
    raise TypeError(type(v))


def benc_dict(items: list[tuple[str | bytes, bytes]]) -> bytes:
    """값이 이미 bencode 된 사전 — 키를 정렬해 잇는다. info 의 바이트를 고치지 않고 끼울 때 쓴다."""
    keyed = sorted(((k.encode("utf-8") if isinstance(k, str) else k), enc) for k, enc in items)
    keys = [k for k, _ in keyed]
    need(len(set(keys)) == len(keys), f"중복 키 {keys}")
    return b"d" + b"".join(benc(k) + enc for k, enc in keyed) + b"e"


class BencodeError(ValueError):
    """대조용 디코더가 거부한 까닭."""


_INT = re.compile(rb"0|-?[1-9][0-9]{0,18}")
_LEN = re.compile(rb"0|[1-9][0-9]{0,9}")


def bdecode(data: bytes) -> tuple[object, dict[bytes, tuple[int, int]]]:
    """엄격하게 푼다. 맨 위가 사전이면 값마다 원래 바이트의 범위도 돌려준다(info-hash 는 그 범위의 해시다).

    받는 것: 정렬되지 않은 사전 키(실제 토렌트에 있다). 거부하는 것: 정수·길이의 선행 0, -0, 빈 정수, 숫자 아닌 글자(+·공백·.),
    19자리 넘는 정수, Long 넘침, 남은 바이트보다 긴 문자열, 닫히지 않은 목록·사전·정수, 문자열 아닌 키, 중복 키, 값 없는 키,
    깊이 64 넘게, 끝에 남는 바이트, 빈 입력.
    """
    spans: dict[bytes, tuple[int, int]] = {}
    value, end = _decode(data, 0, 0, spans)
    if end != len(data):
        raise BencodeError("끝에 남는 바이트")
    return value, spans


def _decode(data: bytes, i: int, depth: int, spans: dict[bytes, tuple[int, int]] | None) -> tuple[object, int]:
    if i >= len(data):
        raise BencodeError("값이 없다")
    c = data[i:i + 1]
    if c == b"i":
        j = data.find(b"e", i + 1)
        if j < 0:
            raise BencodeError("닫히지 않은 정수")
        token = data[i + 1:j]
        if not _INT.fullmatch(token):
            raise BencodeError(f"정수의 꼴 {token!r}")
        n = int(token)
        if not LONG_MIN <= n <= LONG_MAX:
            raise BencodeError("Long 넘침")
        return n, j + 1
    if c.isdigit():
        j = data.find(b":", i)
        if j < 0:
            raise BencodeError("길이 뒤에 ':' 가 없다")
        token = data[i:j]
        if not _LEN.fullmatch(token):
            raise BencodeError(f"길이의 꼴 {token!r}")
        n = int(token)
        if n > len(data) - (j + 1):
            raise BencodeError("문자열이 남은 바이트보다 길다")
        return data[j + 1:j + 1 + n], j + 1 + n
    if c not in (b"l", b"d"):
        raise BencodeError(f"모르는 바이트 {c!r}")
    if depth >= MAX_DEPTH:
        raise BencodeError("너무 깊다")
    i += 1
    if c == b"l":
        items = []
        while True:
            if i >= len(data):
                raise BencodeError("닫히지 않은 목록")
            if data[i:i + 1] == b"e":
                return items, i + 1
            v, i = _decode(data, i, depth + 1, None)
            items.append(v)
    out: dict[bytes, object] = {}
    while True:
        if i >= len(data):
            raise BencodeError("닫히지 않은 사전")
        if data[i:i + 1] == b"e":
            return out, i + 1
        if not data[i:i + 1].isdigit():
            raise BencodeError("사전의 키가 문자열이 아니다")
        k, i = _decode(data, i, depth + 1, None)
        if k in out:
            raise BencodeError("중복 키")
        if i >= len(data) or data[i:i + 1] == b"e":
            raise BencodeError("값 없는 키")
        start = i
        v, i = _decode(data, i, depth + 1, None)
        out[k] = v
        if spans is not None:
            spans[k] = (start, i)


# ── 토렌트 ──

@dataclass
class FileSpec:
    path: list[str]
    length: int
    seed: int
    pad: bool = False
    attr: str = ""

    def data(self) -> bytes:
        return bytes(self.length) if self.pad else file_bytes(self.seed, self.length)


@dataclass
class TorrentCase:
    id: str
    doc: str
    raw: bytes
    expect: str
    ih1: str
    ih2: str | None
    name: str
    piece_length: int
    total_length: int
    pieces_hex: str
    files: list[FileSpec]
    trackers: list[list[str]]
    web_seeds: list[str]
    v2_files: list[FileSpec] | None = None   # 하이브리드·v2 의 file tree 차례(패드 없음) — 대조용


TRACKER_HTTP = "http://tracker.example.org:6969/announce"
TRACKER_UDP = "udp://tracker.example.org:6969/announce"
TRACKER_HTTPS = "https://backup.example.net/announce"


def v1_pieces(files: list[FileSpec], piece_length: int) -> bytes:
    stream = b"".join(f.data() for f in files)
    return b"".join(sha1(stream[i:i + piece_length]) for i in range(0, len(stream), piece_length))


def file_entry(f: FileSpec) -> dict[str, object]:
    entry: dict[str, object] = {"length": f.length, "path": f.path}
    if f.attr:
        entry["attr"] = f.attr
    return entry


def with_pads(files: list[FileSpec], piece_length: int) -> list[FileSpec]:
    """BEP 47 — 마지막이 아닌 파일이 조각 경계에서 끝나지 않으면 그 뒤에 패드(attr 'p', path ['.pad', '<길이>'])를 낀다."""
    out: list[FileSpec] = []
    offset = 0
    for i, f in enumerate(files):
        out.append(f)
        offset += f.length
        if i < len(files) - 1 and offset % piece_length:
            n = piece_length - offset % piece_length
            out.append(FileSpec([".pad", str(n)], n, 0, pad=True, attr="p"))
            offset += n
    return out


def path_key(f: FileSpec) -> tuple[bytes, ...]:
    """v2 file tree 를 걷는 차례 — 사전의 키는 원래 바이트로 정렬되므로 경로 성분의 바이트 차례와 같다."""
    return tuple(p.encode("utf-8") for p in f.path)


ZERO32 = bytes(32)


def next_pow2(n: int) -> int:
    p = 1
    while p < n:
        p *= 2
    return p


def fold(nodes: list[bytes]) -> list[list[bytes]]:
    """잎에서 뿌리까지의 층들(SHA-256(왼쪽 ‖ 오른쪽)). 잎 수는 2 의 거듭제곱이어야 한다."""
    need(len(nodes) == next_pow2(len(nodes)), "잎 수")
    layers = [nodes]
    while len(layers[-1]) > 1:
        prev = layers[-1]
        layers.append([sha256(prev[i], prev[i + 1]) for i in range(0, len(prev), 2)])
    return layers


def merkle_pad(blocks_per_piece: int) -> bytes:
    """잎 blocks_per_piece 개가 모두 0 인 부분 나무의 뿌리 — 조각 층에서 파일 끝 너머를 채우는 값."""
    h, n = ZERO32, 1
    while n < blocks_per_piece:
        h, n = sha256(h, h), n * 2
    return h


def merkle(data: bytes, piece_length: int) -> tuple[bytes, bytes | None]:
    """BEP 52 — 16 KiB 블록의 SHA-256 을 잎으로(마지막 블록은 짧은 채로), 잎 수를 2 의 거듭제곱까지 0 으로 채운 나무의 뿌리.
    파일이 조각보다 크면 한 마디가 조각 하나를 덮는 층도(파일 끝 너머만 덮는 마디는 뺀다)."""
    need(len(data) > 0, "빈 파일에는 pieces root 가 없다")
    leaves = [sha256(data[i:i + BLOCK]) for i in range(0, len(data), BLOCK)]
    blocks_per_piece = piece_length // BLOCK
    # 조각보다 작은 파일의 뿌리를 블록 수로 셀지 조각 크기로 셀지 해석이 갈릴 수 있다 — 그런 파일은 두지 않는다.
    need(len(data) > piece_length or next_pow2(len(leaves)) == blocks_per_piece, "조각보다 작은 파일의 블록 수")
    layers = fold(leaves + [ZERO32] * (next_pow2(len(leaves)) - len(leaves)))
    root = layers[-1][0]
    if len(data) <= piece_length:
        return root, None
    height = blocks_per_piece.bit_length() - 1
    return root, b"".join(layers[height][:ceil_div(len(data), piece_length)])


def root_from_layer(layer: bytes, piece_length: int) -> bytes:
    """조각 층에서 뿌리를 다시 — 받는 쪽이 piece layers 를 확인하는 길(빈 자리는 merkle_pad)."""
    nodes = [layer[i:i + 32] for i in range(0, len(layer), 32)]
    pad = merkle_pad(piece_length // BLOCK)
    return fold(nodes + [pad] * (next_pow2(len(nodes)) - len(nodes)))[-1][0]


def file_tree(files: list[FileSpec], piece_length: int) -> tuple[dict, dict]:
    """BEP 52 의 file tree(여러 파일이면 name 디렉터리 안쪽부터)와 맨 위 piece layers."""
    tree: dict = {}
    layers: dict = {}
    for f in files:
        node = tree
        for part in f.path[:-1]:
            node = node.setdefault(part, {})
        meta: dict[str, object] = {"length": f.length}
        if f.length:
            root, layer = merkle(f.data(), piece_length)
            meta["pieces root"] = root
            if layer is not None:
                need(root not in layers, "같은 뿌리")
                layers[root] = layer
        node[f.path[-1]] = {"": meta}
    return tree, layers


def finish(case_id: str, doc: str, info: dict | bytes, top: dict, expect: str, name: str, piece_length: int,
           files: list[FileSpec], trackers: list[list[str]], web_seeds: list[str],
           v2_files: list[FileSpec] | None = None) -> TorrentCase:
    need(piece_length >= BLOCK and piece_length == next_pow2(piece_length), "조각 길이")
    info_raw = info if isinstance(info, bytes) else benc(info)
    raw = benc_dict([(k, benc(v)) for k, v in top.items()] + [("info", info_raw)])
    pieces = b"" if expect == "v2-only" else v1_pieces(files, piece_length)
    total = sum(f.length for f in files)
    need(total <= 300 * 1024, f"{case_id} 가 300 KB 를 넘는다")
    return TorrentCase(
        id=case_id, doc=doc, raw=raw, expect=expect, ih1=sha1(info_raw).hex(),
        ih2=sha256(info_raw).hex() if v2_files is not None else None, name=name, piece_length=piece_length,
        total_length=total, pieces_hex=pieces.hex(), files=files, trackers=trackers, web_seeds=web_seeds,
        v2_files=v2_files)


def torrent_single() -> TorrentCase:
    name, plen = "single.bin", 16384
    f = FileSpec([name], 100_000, 11)
    info = {"length": f.length, "name": name, "piece length": plen, "pieces": v1_pieces([f], plen)}
    doc = "한 파일(BEP 3 의 length 꼴). 길이 100,000 은 조각 16,384 의 배수가 아니다 — 조각 일곱, 마지막은 1,696 바이트. announce 하나."
    return finish("single-file", doc, info, {"announce": TRACKER_HTTP}, "ok", name, plen, [f], [[TRACKER_HTTP]], [])


def torrent_multi() -> TorrentCase:
    name, plen = "multi", 32768
    files = [FileSpec(["a.txt"], 40_000, 21), FileSpec(["sub", "deep", "b.bin"], 70_000, 22),
             FileSpec(["empty.dat"], 0, 23), FileSpec(["c.bin"], 25_000, 24)]
    tiers = [[TRACKER_HTTP, TRACKER_UDP], [TRACKER_HTTPS]]
    seeds = ["https://seed.example.com/pub/", "http://mirror.example.net/files/"]
    info = {"files": [file_entry(f) for f in files], "name": name, "piece length": plen,
            "pieces": v1_pieces(files, plen)}
    top = {"announce": TRACKER_HTTP, "announce-list": tiers, "url-list": seeds,
           "created by": "iroiro tools/torrent_vectors.py", "creation date": 1_760_000_000}
    doc = ("여러 파일 넷 — 중첩 경로(sub/deep/b.bin)와 길이 0 인 파일(empty.dat)을 끼고, files 의 차례는 정렬하지 않은 그대로다. "
           "announce + announce-list(층 둘, announce 는 첫 층에도 있다) + url-list(둘), 읽지 않아도 되는 키(created by·creation date).")
    return finish("multi-file", doc, info, top, "ok", name, plen, files, tiers, seeds)


def torrent_pads() -> TorrentCase:
    name, plen = "padded", 16384
    run = FileSpec(["tools", "run.sh"], 5_000, 33, attr="xh")
    files = with_pads([FileSpec(["one.bin"], 20_000, 31), FileSpec(["two.bin"], 32_868, 32), run], plen)
    entries = [file_entry(f) for f in files]
    entries[-1]["sha1"] = sha1(run.data())   # BEP 47 의 파일 sha1 — 모르는 키처럼 지나가면 된다
    info = {"files": entries, "name": name, "piece length": plen, "pieces": v1_pieces(files, plen)}
    doc = ("BEP 47 패드 — one.bin 20,000 뒤에 .pad/12768, two.bin 32,868 뒤에 .pad/16284 (내용은 0, 조각 해시에 들어간다). "
           "마지막 tools/run.sh 는 attr 'xh'(실행·숨김)와 파일 sha1 을 달았고 뒤에 패드가 없다. announce 는 udp 하나.")
    return finish("pad-files", doc, info, {"announce": TRACKER_UDP}, "ok", name, plen, files, [[TRACKER_UDP]], [])


def torrent_hybrid() -> TorrentCase:
    name, plen = "hybrid", 32768
    v2_files = sorted([FileSpec(["delta.txt"], 20_000, 43), FileSpec(["alpha.bin"], 50_000, 41),
                       FileSpec(["beta", "gamma.bin"], 70_000, 42)], key=path_key)
    files = with_pads(v2_files, plen)
    tree, layers = file_tree(v2_files, plen)
    info = {"file tree": tree, "files": [file_entry(f) for f in files], "meta version": 2, "name": name,
            "piece length": plen, "pieces": v1_pieces(files, plen)}
    top = {"announce": TRACKER_UDP, "piece layers": layers}
    doc = ("하이브리드 v1+v2(BEP 52) — v1 으로 읽는다. info 에 meta version 2·file tree 와 v1 의 files·pieces 가 함께 있고 맨 위에 "
           "piece layers. v1 files 는 file tree 를 걷는 차례(키의 바이트 차례)이고 마지막이 아닌 파일마다 패드로 조각 경계에 맞췄다"
           "(마지막 파일 뒤에는 패드가 없다). 두 info-hash 는 같은 info 바이트의 SHA-1·SHA-256. 머클: 16 KiB 잎, 조각 32 KiB 라 "
           "조각 층은 잎 한 층 위 — gamma.bin(잎 5→8)의 셋째 마디는 H(다섯째 잎 ‖ 0), 넷째(파일 끝 너머)는 층에서 빠진다. "
           "delta.txt 는 조각 하나라 piece layers 에 없다.")
    return finish("hybrid-v1-v2", doc, info, top, "hybrid", name, plen, files, [[TRACKER_UDP]], [], v2_files)


def torrent_v2_only() -> TorrentCase:
    name, plen = "v2only", 65536
    v2_files = sorted([FileSpec(["y", "z.bin"], 60_000, 52), FileSpec(["x.bin"], 150_000, 51)], key=path_key)
    tree, layers = file_tree(v2_files, plen)
    info = {"file tree": tree, "meta version": 2, "name": name, "piece length": plen}
    doc = ("순수 v2(BEP 52 — pieces·files·length 없음) — 거부한다. infoHashV1Hex 는 정의대로 info 바이트의 SHA-1 을 적었다"
           "(v2 군집은 그 값이 아니라 SHA-256 의 앞 20 바이트를 쓴다). files 는 file tree 의 차례, 패드 없음. "
           "조각 64 KiB 라 조각 층은 잎 두 층 위 — x.bin(잎 10→16)의 조각 층은 마디 셋(넷째는 merkle_pad 라 빠진다).")
    return finish("v2-only", doc, info, {"piece layers": layers}, "v2-only", name, plen, v2_files, [], [], v2_files)


def torrent_private() -> TorrentCase:
    name, plen = "private.bin", 16384
    f = FileSpec([name], 30_000, 61)
    info = {"length": f.length, "name": name, "piece length": plen, "pieces": v1_pieces([f], plen), "private": 1,
            "source": "IROIRO-TEST"}
    tracker = "https://private.example.org/announce"
    doc = "info 의 private = 1 — 거부한다(초대제 트래커). 그런 토렌트에 흔한 source 키도 info 에 있다."
    return finish("private", doc, info, {"announce": tracker}, "private", name, plen, [f], [[tracker]], [])


def torrent_unsorted() -> TorrentCase:
    name, plen = "unsorted.bin", 16384
    f = FileSpec([name], 20_000, 71)
    pieces = v1_pieces([f], plen)
    # 손으로 짠 바이트 — name, piece length, pieces, length 차례(정렬하면 length 가 맨 앞이다).
    info_raw = (b"d" + benc("name") + benc(name) + benc("piece length") + benc(plen) + benc("pieces") + benc(pieces)
                + benc("length") + benc(f.length) + b"e")
    resorted = benc({"length": f.length, "name": name, "piece length": plen, "pieces": pieces})
    need(info_raw != resorted and len(info_raw) == len(resorted), "정렬 안 된 info")
    doc = ("info 사전의 키가 정렬되지 않았다(name, piece length, pieces, length 차례 — 손으로 짠 바이트). info-hash 는 원래 바이트의 "
           f"SHA-1 이다 — 정렬해 다시 쓴 info 의 SHA-1({sha1(resorted).hex()})이 아니다. 트래커 없음(빈 목록).")
    return finish("unsorted-info-keys", doc, info_raw, {"comment": "info keys are not sorted"}, "ok", name, plen,
                  [f], [], [])


def torrent_utf8_keys() -> TorrentCase:
    name, plen = "한국어 자료", 16384
    files = [FileSpec(["문서", "안내.txt"], 30_000, 81), FileSpec(["사진.bin"], 45_000, 82),
             FileSpec(["음악", "노래 01.bin"], 12_345, 83)]
    entries = [{"length": f.length, "path": [p.encode("cp949") for p in f.path], "path.utf-8": f.path} for f in files]
    for raw_name in [name.encode("cp949")] + [p.encode("cp949") for f in files for p in f.path if not p.isascii()]:
        try:
            raw_name.decode("utf-8")
        except UnicodeDecodeError:
            continue
        raise VectorError("CP949 바이트가 UTF-8 로도 읽힌다")
    info = {"files": entries, "name": name.encode("cp949"), "name.utf-8": name, "piece length": plen,
            "pieces": v1_pieces(files, plen)}
    doc = ("name·path 에는 CP949 바이트(UTF-8 로 읽히지 않는다), name.utf-8·path.utf-8 에는 UTF-8 — 기대값은 .utf-8 쪽이다. "
           "옛 한국어 클라이언트가 이렇게 썼다. 빈칸 든 이름(노래 01.bin)도 있다.")
    return finish("utf8-keys", doc, info, {"announce": TRACKER_HTTP}, "ok", name, plen, files, [[TRACKER_HTTP]], [])


def torrent_tracker_forms() -> TorrentCase:
    name, plen = "edge.bin", 16384
    f = FileSpec([name], 32_768, 91)
    tiers = [["udp://new.example.org:6969/announce", "http://new.example.org/announce"],
             ["https://new.example.net/announce"]]
    seed = "https://seed.example.org/edge.bin"
    info = {"length": f.length, "name": name, "piece length": plen, "pieces": v1_pieces([f], plen)}
    top = {"announce": "http://old.example.org/announce", "announce-list": tiers, "url-list": seed}
    doc = ("트래커의 꼴 — announce-list 가 있으면 announce 는 쓰지 않는다(BEP 12 — 여기의 announce 는 목록에 없는 주소다). "
           "url-list 가 목록이 아니라 문자열 하나다. 길이가 조각의 꼭 두 배라 마지막 조각도 꽉 찬다.")
    return finish("tracker-forms", doc, info, top, "ok", name, plen, [f], tiers, [seed])


def build_torrents() -> list[TorrentCase]:
    return [torrent_single(), torrent_multi(), torrent_pads(), torrent_hybrid(), torrent_v2_only(), torrent_private(),
            torrent_unsorted(), torrent_utf8_keys(), torrent_tracker_forms()]


def walk_file_tree(node: dict, prefix: list[str]) -> list[tuple[list[str], int, bytes | None]]:
    out = []
    for k in sorted(node):
        child = node[k]
        need(isinstance(child, dict) and k != b"", "file tree 의 마디")
        if b"" in child:
            meta = child[b""]
            out.append((prefix + [k.decode("utf-8")], meta[b"length"], meta.get(b"pieces root")))
        else:
            out += walk_file_tree(child, prefix + [k.decode("utf-8")])
    return out


def check_torrent(case: TorrentCase) -> None:
    """지은 바이트를 대조용 디코더로 다시 풀어 기대값을 하나하나 다시 얻는다."""
    top, spans = bdecode(case.raw)
    start, end = spans[b"info"]
    info_raw = case.raw[start:end]
    need(sha1(info_raw).hex() == case.ih1, f"{case.id}: v1 info-hash")
    need((sha256(info_raw).hex() if b"meta version" in top[b"info"] else None) == case.ih2, f"{case.id}: v2 info-hash")
    info = top[b"info"]
    name = (info.get(b"name.utf-8") or info[b"name"]).decode("utf-8")
    need(name == case.name and info[b"piece length"] == case.piece_length, f"{case.id}: 이름·조각 길이")
    v1 = b"pieces" in info
    v2 = info.get(b"meta version") == 2
    if b"files" in info:
        got = []
        for e in info[b"files"]:
            path = [p.decode("utf-8") for p in e.get(b"path.utf-8", e[b"path"])]
            attr = e.get(b"attr", b"").decode("ascii")
            got.append((path, e[b"length"], "p" in attr, attr))
    elif b"length" in info:
        got = [([name], info[b"length"], False, "")]
    else:
        got = [(path, length, False, "") for path, length, _ in walk_file_tree(info[b"file tree"], [])]
    need(got == [(f.path, f.length, f.pad, f.attr) for f in case.files], f"{case.id}: 파일 목록")
    need(sum(f.length for f in case.files) == case.total_length, f"{case.id}: 길이 합")
    for f in case.files:
        need(not f.pad or (f.path == [".pad", str(f.length)] and f.attr == "p"), f"{case.id}: 패드의 꼴")
    if v1:
        stream = b"".join(f.data() for f in case.files)
        pieces = info[b"pieces"]
        need(pieces.hex() == case.pieces_hex, f"{case.id}: pieces")
        need(len(pieces) == 20 * ceil_div(len(stream), case.piece_length), f"{case.id}: 조각 수")
        for i in range(len(pieces) // 20):
            chunk = stream[i * case.piece_length:(i + 1) * case.piece_length]
            need(sha1(chunk) == pieces[20 * i:20 * i + 20], f"{case.id}: 조각 {i}")
    else:
        need(case.pieces_hex == "", f"{case.id}: v2 의 pieces")
    if b"announce-list" in top:
        tiers = [[u.decode("utf-8") for u in tier] for tier in top[b"announce-list"]]
    elif b"announce" in top:
        tiers = [[top[b"announce"].decode("utf-8")]]
    else:
        tiers = []
    need(tiers == case.trackers, f"{case.id}: 트래커")
    seeds = top.get(b"url-list")
    seeds = [] if seeds is None else [seeds.decode("utf-8")] if isinstance(seeds, bytes) else [
        u.decode("utf-8") for u in seeds]
    need(seeds == case.web_seeds, f"{case.id}: 웹 시드")
    expect = "private" if info.get(b"private") == 1 else "hybrid" if v1 and v2 else "v2-only" if v2 else "ok"
    need(expect == case.expect, f"{case.id}: expect")
    if v2:
        check_v2(case, top, info)


def check_v2(case: TorrentCase, top: dict, info: dict) -> None:
    plen = case.piece_length
    layers = top.get(b"piece layers", {})
    walked = walk_file_tree(info[b"file tree"], [])
    need([p for p, _, _ in walked] == [f.path for f in case.v2_files], f"{case.id}: file tree 의 차례")
    used = set()
    for (path, length, root), f in zip(walked, case.v2_files):
        data = f.data()
        need(length == len(data) and (root is None) == (length == 0), f"{case.id}: {path} 의 길이")
        want_root, want_layer = merkle(data, plen)
        need(root == want_root, f"{case.id}: {path} 의 pieces root")
        if want_layer is None:
            need(root not in layers, f"{case.id}: {path} 는 조각 층이 없어야 한다")
            continue
        need(layers[root] == want_layer and root_from_layer(layers[root], plen) == root, f"{case.id}: {path} 의 조각 층")
        used.add(root)
    need(set(layers) == used, f"{case.id}: 남는 piece layers")
    if case.expect == "hybrid":
        offset, real = 0, []
        for f in case.files:
            if not f.pad:
                need(offset % plen == 0, f"{case.id}: {f.path} 가 조각 경계에서 시작하지 않는다")
                real.append(f.path)
            offset += f.length
        need(real == [f.path for f in case.v2_files], f"{case.id}: v1 과 v2 의 파일 차례")


# ── 마그넷 ──

@dataclass
class MagnetCase:
    comment: str
    uri: str
    expect: str
    info_hash: str | None
    name: str | None
    trackers: list[str]
    web_seeds: list[str]
    peers: list[str]


def pct(s: str, lower: bool = False) -> str:
    """퍼센트 인코딩(UTF-8, 예약하지 않은 글자만 그대로)."""
    out = urllib.parse.quote(s, safe="")
    return re.sub(r"%[0-9A-F]{2}", lambda m: m.group(0).lower(), out) if lower else out


def mixed_case(h: str) -> str:
    out, upper = [], True
    for ch in h:
        if ch.isalpha():
            out.append(ch.upper() if upper else ch.lower())
            upper = not upper
        else:
            out.append(ch)
    return "".join(out)


def build_magnets(t: dict[str, TorrentCase]) -> list[MagnetCase]:
    single, multi, hybrid, v2 = t["single-file"], t["multi-file"], t["hybrid-v1-v2"], t["v2-only"]
    utf8, forms = t["utf8-keys"], t["tracker-forms"]
    hexed = mixed_case(multi.ih1)
    need(hexed != hexed.lower() and hexed != hexed.upper(), "대소문자 섞기")
    b32 = base64.b32encode(bytes.fromhex(single.ih1)).decode("ascii")
    need(len(b32) == 32 and "=" not in b32, "base32 32 자")
    btmh_hybrid, btmh_v2 = "urn:btmh:1220" + hybrid.ih2, "urn:btmh:1220" + v2.ih2
    tr_https = "https://tracker.example.com/announce?passkey=abc&x=1"
    ws1, ws2 = "https://seed.example.org/single.bin", "http://mirror.example.net/files/single.bin"
    v6 = "[2001:db8::1]:51413"
    korean = "한국어 자료"
    m = MagnetCase
    return [
        m("btih 를 hex 40 자, 대소문자를 섞어 — dn 없음(name null)", f"magnet:?xt=urn:btih:{hexed}", "ok", multi.ih1,
          None, [], [], []),
        m("btih 를 base32 32 자(RFC 4648, 대문자) + dn", f"magnet:?xt=urn:btih:{b32}&dn=single.bin", "ok",
          single.ih1, "single.bin", [], [], []),
        m("dn 을 UTF-8 퍼센트 인코딩(한국어와 빈칸 %20 — '+' 는 쓰지 않았다)",
          f"magnet:?xt=urn:btih:{utf8.ih1}&dn={pct(korean)}", "ok", utf8.ih1, korean, [], [], []),
        m("tr 셋(udp·http·https) — 첫째·셋째는 인코딩, 둘째는 날것(질의에 ':'·'/' 는 그대로 써도 된다). 셋째는 안에 '?'·'&' 가 "
          "%3F·%26 으로 들어 있다 — '&' 로 먼저 가른 뒤 한 번만 푼다",
          f"magnet:?xt=urn:btih:{forms.ih1}&tr={pct(TRACKER_UDP)}&tr={TRACKER_HTTP}&tr={pct(tr_https)}", "ok",
          forms.ih1, None, [TRACKER_UDP, TRACKER_HTTP, tr_https], [], []),
        m("ws 둘(BEP 19 의 웹 시드)", f"magnet:?xt=urn:btih:{single.ih1}&ws={pct(ws1)}&ws={pct(ws2)}", "ok", single.ih1,
          None, [], [ws1, ws2], []),
        m("x.pe 둘 — IPv4:port(날것)와 [IPv6]:port(인코딩, 기대값은 대괄호 꼴)",
          f"magnet:?xt=urn:btih:{multi.ih1}&x.pe=203.0.113.5:6881&x.pe={pct(v6)}", "ok", multi.ih1, None, [], [],
          ["203.0.113.5:6881", v6]),
        m("btmh 만(순수 v2) — 받지 않는다", f"magnet:?xt={btmh_v2}&dn=v2only", "v2-only", None, None, [], [], []),
        m("btih 와 btmh 둘 다(하이브리드) — btih 를 쓴다", f"magnet:?xt=urn:btih:{hybrid.ih1}&xt={btmh_hybrid}&dn=hybrid",
          "ok", hybrid.ih1, "hybrid", [], [], []),
        m("btmh 가 앞, btih 가 뒤 — 그래도 btih 를 쓴다(첫 xt 만 보면 틀린다)",
          f"magnet:?xt={btmh_hybrid}&xt=urn:btih:{hybrid.ih1}&dn=hybrid", "ok", hybrid.ih1, "hybrid", [], [], []),
        m("xt 가 없다", f"magnet:?dn=no-xt&tr={pct(TRACKER_HTTP)}", "invalid", None, None, [], [], []),
        m("btih 의 hex 가 39 자", f"magnet:?xt=urn:btih:{multi.ih1[:39]}", "invalid", None, None, [], [], []),
        m("btih 가 40 자인데 hex 가 아닌 글자(g)", f"magnet:?xt=urn:btih:{multi.ih1[:39]}g", "invalid", None, None, [],
          [], []),
        m("모두 — 소문자 hex, dn 의 퍼센트 인코딩이 소문자 hex(%ed…), 모르는 키 xl(정확한 길이)은 지나간다",
          f"magnet:?xt=urn:btih:{utf8.ih1}&dn={pct(korean, lower=True)}&xl={utf8.total_length}&tr={pct(TRACKER_UDP)}"
          f"&ws={pct(ws1)}&x.pe=198.51.100.20:51413", "ok", utf8.ih1, korean, [TRACKER_UDP], [ws1],
          ["198.51.100.20:51413"]),
    ]


def check_magnet(case: MagnetCase) -> None:
    """작은 참조 파서로 다시 읽는다 — '&' 로 가르고, '=' 앞뒤를 나누고, 값을 한 번만 퍼센트 디코딩('+' 는 그대로)."""
    need(case.uri.startswith("magnet:?") and " " not in case.uri, f"마그넷의 꼴 {case.uri}")
    btih, btmh, dn, tr, ws, pe = [], [], None, [], [], []
    for part in case.uri[len("magnet:?"):].split("&"):
        key, _, value = part.partition("=")
        value = urllib.parse.unquote(value, encoding="utf-8", errors="strict")
        if key == "xt" and value.lower().startswith("urn:btih:"):
            btih.append(value[9:])
        elif key == "xt" and value.lower().startswith("urn:btmh:"):
            btmh.append(value[9:])
        elif key == "dn":
            dn = value
        elif key == "tr":
            tr.append(value)
        elif key == "ws":
            ws.append(value)
        elif key == "x.pe":
            pe.append(value)
    got: tuple = ("invalid", None, None, [], [], [])
    if btih:
        h = btih[0]
        if re.fullmatch(r"[0-9A-Fa-f]{40}", h):
            got = ("ok", h.lower(), dn, tr, ws, pe)
        elif re.fullmatch(r"[A-Z2-7]{32}", h):
            got = ("ok", base64.b32decode(h).hex(), dn, tr, ws, pe)
    elif btmh and re.fullmatch(r"1220[0-9a-f]{64}", btmh[0]):
        got = ("v2-only", None, None, [], [], [])
    want = (case.expect, case.info_hash, case.name, case.trackers, case.web_seeds, case.peers)
    need(got == want, f"마그넷 {case.uri}: {got} ≠ {want}")


# ── RC4 ──

class Rc4:
    def __init__(self, key: bytes, drop: int = 0) -> None:
        s = list(range(256))
        j = 0
        for i in range(256):
            j = (j + s[i] + key[i % len(key)]) & 0xFF
            s[i], s[j] = s[j], s[i]
        self._s, self._i, self._j = s, 0, 0
        self.keystream(drop)

    def keystream(self, n: int) -> bytes:
        s, i, j = self._s, self._i, self._j
        out = bytearray(n)
        for k in range(n):
            i = (i + 1) & 0xFF
            j = (j + s[i]) & 0xFF
            s[i], s[j] = s[j], s[i]
            out[k] = s[(s[i] + s[j]) & 0xFF]
        self._i, self._j = i, j
        return bytes(out)

    def crypt(self, data: bytes) -> bytes:
        return xor(data, self.keystream(len(data)))


@dataclass
class Rc4Case:
    comment: str
    key: bytes
    drop: int
    plaintext: bytes
    ciphertext: bytes


def build_rc4() -> list[Rc4Case]:
    cases = []
    # 위키백과 'RC4' 문서의 시험 벡터(키·평문·암호문)와 RFC 6229 의 40 비트 키 — 밖에서 온 값이라 구현을 거꾸로 확인한다.
    known = [(b"Key", b"Plaintext", "bbf316e8d940af0ad3"), (b"Wiki", b"pedia", "1021bf0420"),
             (b"Secret", b"Attack at dawn", "45a01f645fc35b383552544b9bf5")]
    for key, plain, cipher in known:
        need(Rc4(key).crypt(plain).hex() == cipher, f"RC4 공개 벡터 {key!r}")
        cases.append(Rc4Case(f"위키백과의 시험 벡터 — 키 '{key.decode()}', 평문 '{plain.decode()}'", key, 0, plain,
                             bytes.fromhex(cipher)))
    rfc_key = bytes.fromhex("0102030405")
    rfc = [(0, "b2396305f03dc027ccc3524a0a1118a86982944f18fc82d589c403a47a0d0919"),
           (1024, "30abbcc7c20b01609f23ee2d5f6bb7df")]
    for drop, stream in rfc:
        zeros = bytes(len(stream) // 2)
        need(Rc4(rfc_key, drop).crypt(zeros).hex() == stream, f"RC4 RFC 6229 오프셋 {drop}")
        cases.append(Rc4Case(f"RFC 6229 의 40 비트 키 0102030405 — 평문 0 이라 암호문이 곧 키 흐름(오프셋 {drop})", rfc_key, drop,
                             zeros, bytes.fromhex(stream)))
    for label, size in (("iroiro-rc4-key-1", 20), ("iroiro-rc4-key-2", 7)):
        key, plain = filler(label, size), filler(label + "-plain", 100)
        cases.append(Rc4Case(f"SHA 로 지은 {size} 바이트 키, MSE 처럼 앞 1024 바이트를 버린다", key, 1024, plain,
                             Rc4(key, 1024).crypt(plain)))
    return cases


# ── MSE ──

MSE_P = int("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22"
            "514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6"
            "F44C42E9A63A36210000000000090563", 16)
MSE_G = 2
VC = bytes(8)


def u16(n: int) -> bytes:
    return n.to_bytes(2, "big")


def u32(n: int) -> bytes:
    return n.to_bytes(4, "big")


def key96(n: int) -> bytes:
    return n.to_bytes(96, "big")


def handshake(info_hash: bytes, peer_id: bytes, reserved: bytes) -> bytes:
    need(len(info_hash) == 20 and len(peer_id) == 20 and len(reserved) == 8, "핸드셰이크의 길이")
    return b"\x13BitTorrent protocol" + reserved + info_hash + peer_id


def bt_message(msg_id: int, payload: bytes = b"") -> bytes:
    return u32(1 + len(payload)) + bytes([msg_id]) + payload


@dataclass
class MseCase:
    comment: str
    info_hash: bytes
    xa: bytes
    xb: bytes
    pad_a: bytes
    pad_b: bytes
    provide: int
    select: int
    pad_c: bytes
    pad_d: bytes
    ia: bytes
    ya: bytes
    yb: bytes
    s: bytes
    req1: bytes
    req23: bytes
    key_a: bytes
    key_b: bytes
    m1: bytes
    m2: bytes
    m3: bytes
    m4: bytes
    after_a: bytes
    after_a_wire: bytes
    after_b: bytes
    after_b_wire: bytes


def private_key(label: str, ok) -> int:
    """SHA-1(label + '-' + c) 를 160 비트 정수로 차례로 보며 ok 를 처음 만족하는 것 — 큰 정수의 바이트 꼴을 시험하려고 고른다."""
    for c in range(100_000):
        x = int.from_bytes(sha1(f"{label}-{c}".encode("utf-8")), "big")
        if ok(x):
            return x
    raise VectorError(f"{label}: 맞는 비밀 값을 찾지 못했다")


def build_mse_case(comment: str, label: str, skey: bytes, xa: int, xb: int, provide: int, select: int,
                   pad_a_len: int, pad_b_len: int, pad_c: bytes, pad_d: bytes, ia: bytes, after_a: bytes,
                   after_b: bytes) -> MseCase:
    ya, yb = pow(MSE_G, xa, MSE_P), pow(MSE_G, xb, MSE_P)
    s = pow(yb, xa, MSE_P)
    need(s == pow(ya, xb, MSE_P), f"{label}: 두 쪽의 S 가 다르다")
    need(all(1 < y < MSE_P - 1 for y in (ya, yb, s)), f"{label}: Y·S 의 범위")
    ya_b, yb_b, s_b = key96(ya), key96(yb), key96(s)
    pad_a, pad_b = filler(label + "-padA", pad_a_len), filler(label + "-padB", pad_b_len)
    req1 = sha1(b"req1", s_b)
    req23 = xor(sha1(b"req2", skey), sha1(b"req3", s_b))
    key_a, key_b = sha1(b"keyA", s_b, skey), sha1(b"keyB", s_b, skey)
    enc_a, enc_b = Rc4(key_a, 1024), Rc4(key_b, 1024)
    m3 = (req1 + req23 + enc_a.crypt(VC + u32(provide) + u16(len(pad_c)) + pad_c + u16(len(ia)))
          + enc_a.crypt(ia))
    m4 = enc_b.crypt(VC + u32(select) + u16(len(pad_d)) + pad_d)
    need(select in (1, 2) and select & provide, f"{label}: select 는 provide 가운데 하나")
    after_a_wire = enc_a.crypt(after_a) if select == 2 else after_a
    after_b_wire = enc_b.crypt(after_b) if select == 2 else after_b
    case = MseCase(comment, skey, xa.to_bytes(20, "big"), xb.to_bytes(20, "big"), pad_a, pad_b, provide, select,
                   pad_c, pad_d, ia, ya_b, yb_b, s_b, req1, req23, key_a, key_b, ya_b + pad_a, yb_b + pad_b, m3, m4,
                   after_a, after_a_wire, after_b, after_b_wire)
    check_mse_b(case)
    check_mse_a(case)
    return case


def check_mse_b(c: MseCase) -> None:
    """B 의 자리 — A 가 보낸 바이트 흐름만 보고 동기화해 VC·provide·PadC·IA 와 그 뒤의 평문을 되찾는다."""
    wire = c.m1 + c.m3 + c.after_a_wire
    ya = int.from_bytes(wire[:96], "big")
    s = key96(pow(ya, int.from_bytes(c.xb, "big"), MSE_P))
    need(s == c.s, "B 의 S")
    at = wire.find(sha1(b"req1", s), 96, 96 + 512 + 20)
    need(at == 96 + len(c.pad_a), "B 가 req1 으로 동기화한 자리")
    at += 20
    need(xor(wire[at:at + 20], sha1(b"req3", s)) == sha1(b"req2", c.info_hash), "B 가 되찾은 SKEY")
    at += 20
    dec = Rc4(sha1(b"keyA", s, c.info_hash), 1024)
    head = dec.crypt(wire[at:at + 14])
    need(head[:8] == VC and int.from_bytes(head[8:12], "big") == c.provide, "B 가 푼 VC·provide")
    n_pad_c = int.from_bytes(head[12:14], "big")
    at += 14
    need(dec.crypt(wire[at:at + n_pad_c]) == c.pad_c, "B 가 푼 PadC")
    at += n_pad_c
    n_ia = int.from_bytes(dec.crypt(wire[at:at + 2]), "big")
    at += 2
    need(dec.crypt(wire[at:at + n_ia]) == c.ia, "B 가 푼 IA")
    at += n_ia
    rest = wire[at:]
    need((dec.crypt(rest) if c.select == 2 else rest) == c.after_a, "B 가 푼 A 의 평문")


def check_mse_a(c: MseCase) -> None:
    """A 의 자리 — B 가 보낸 바이트 흐름에서 ENCRYPT(VC) 를 찾아 동기화해 select·PadD 와 그 뒤의 평문을 되찾는다."""
    wire = c.m2 + c.m4 + c.after_b_wire
    yb = int.from_bytes(wire[:96], "big")
    s = key96(pow(yb, int.from_bytes(c.xa, "big"), MSE_P))
    need(s == c.s, "A 의 S")
    key_b = sha1(b"keyB", s, c.info_hash)
    at = wire.find(Rc4(key_b, 1024).crypt(VC), 96, 96 + 512 + 8)
    need(at == 96 + len(c.pad_b), "A 가 VC 로 동기화한 자리")
    dec = Rc4(key_b, 1024)
    head = dec.crypt(wire[at:at + 14])
    need(head[:8] == VC and int.from_bytes(head[8:12], "big") == c.select, "A 가 푼 VC·select")
    n_pad_d = int.from_bytes(head[12:14], "big")
    at += 14
    need(dec.crypt(wire[at:at + n_pad_d]) == c.pad_d, "A 가 푼 PadD")
    at += n_pad_d
    rest = wire[at:]
    need((dec.crypt(rest) if c.select == 2 else rest) == c.after_b, "A 가 푼 B 의 평문")


def build_mse(t: dict[str, TorrentCase]) -> list[MseCase]:
    reserved_a = bytes([0, 0, 0, 0, 0, 0x10, 0, 0x04])
    reserved_b = bytes([0, 0, 0, 0, 0, 0x10, 0, 0x05])
    peer_a = b"-XX0000-" + sha1(b"iroiro-peer-a").hex()[:12].encode("ascii")
    peer_b = b"-ZZ0100-" + sha1(b"iroiro-peer-b").hex()[:12].encode("ascii")

    def lead(n: int) -> int:
        return key96(n)[0]

    # (a) Ya 의 맨 앞 바이트가 0, S 의 맨 앞 비트가 1 이 되게 Xa 를 고른다.
    skey_a = bytes.fromhex(t["multi-file"].ih1)
    xb_a = int.from_bytes(sha1(b"iroiro-mse-a-xb"), "big")
    yb_a = pow(MSE_G, xb_a, MSE_P)
    xa_a = private_key("iroiro-mse-a-xa",
                       lambda x: lead(pow(MSE_G, x, MSE_P)) == 0 and lead(pow(yb_a, x, MSE_P)) >= 0x80)
    request = bt_message(6, u32(0) + u32(0) + u32(16384))
    case_a = build_mse_case(
        "provide 0x02·select 0x02(RC4). PadA 37·PadB 0·PadC 0·PadD 0. IA 는 A 의 핸드셰이크 68 바이트"
        "(reserved[5] = 0x10, reserved[7] = 0x04, peer id '-XX0000-' + 12). "
        "그 뒤 A 는 have_none·interested·request(0, 0, 16384), "
        "B 는 제 핸드셰이크·have_all·unchoke 를 RC4 를 이어 보낸다. Ya 의 맨 앞 바이트가 0 이고(96 바이트를 채우지 않으면 95), "
        "S 의 맨 앞 비트가 1 이다(BigInteger.toByteArray 는 부호 바이트를 붙여 97 바이트를 낸다).",
        "iroiro-mse-a", skey_a, xa_a, xb_a, provide=0x02, select=0x02, pad_a_len=37, pad_b_len=0, pad_c=b"",
        pad_d=b"", ia=handshake(skey_a, peer_a, reserved_a),
        after_a=bt_message(0x0F) + bt_message(2) + request,
        after_b=handshake(skey_a, peer_b, reserved_b) + bt_message(0x0E) + bt_message(1))
    need(lead(int.from_bytes(case_a.ya, "big")) == 0 and case_a.s[0] >= 0x80, "(a) 의 바이트 꼴")

    # (b) S 의 맨 앞 바이트가 0, Yb 의 맨 앞 비트가 1 이 되게 Xb 를 고른다.
    single = t["single-file"]
    skey_b = bytes.fromhex(single.ih1)
    xa_b = int.from_bytes(sha1(b"iroiro-mse-b-xa"), "big")
    ya_b = pow(MSE_G, xa_b, MSE_P)
    xb_b = private_key("iroiro-mse-b-xb",
                       lambda x: lead(pow(ya_b, x, MSE_P)) == 0 and lead(pow(MSE_G, x, MSE_P)) >= 0x80)
    n_pieces = len(single.pieces_hex) // 40
    bitfield = bytes([0xFF] * (n_pieces // 8) + ([(0xFF << (8 - n_pieces % 8)) & 0xFF] if n_pieces % 8 else []))
    case_b = build_mse_case(
        "provide 0x03·select 0x01(평문). PadA 512·PadB 300·PadC 4(0)·PadD 0, IA 없음. 그 뒤의 바이트는 암호화하지 않는다 — "
        f"A 는 제 핸드셰이크, B 는 제 핸드셰이크·bitfield(조각 {n_pieces} 모두)·unchoke. S 의 맨 앞 바이트가 0 이다.",
        "iroiro-mse-b", skey_b, xa_b, xb_b, provide=0x03, select=0x01, pad_a_len=512, pad_b_len=300,
        pad_c=bytes(4), pad_d=b"", ia=b"",
        after_a=handshake(skey_b, peer_a, reserved_a),
        after_b=handshake(skey_b, peer_b, reserved_b) + bt_message(5, bitfield) + bt_message(1))
    need(case_b.s[0] == 0 and case_b.yb[0] >= 0x80, "(b) 의 바이트 꼴")
    return [case_a, case_b]


# ── bencode 사례 ──

@dataclass
class BencodeCase:
    data: bytes
    valid: bool
    note: str


def build_bencode() -> list[BencodeCase]:
    nested = benc({"dict": {"key": ["a", 1]}, "list": ["spam", 42, [], {}]})
    ok = [
        (b"i0e", "정수 0"),
        (b"i-5e", "음수"),
        (b"i9223372036854775807e", "Long.MAX_VALUE(19자리)"),
        (b"i-9223372036854775808e", "Long.MIN_VALUE"),
        (b"0:", "빈 문자열"),
        (b"le", "빈 목록"),
        (b"de", "빈 사전"),
        (nested, "중첩 — 사전 안의 사전·목록, 목록 안의 빈 목록·빈 사전"),
        (b"10:0123456789", "길이가 두 자리인 문자열"),
        (b"3:\x00\xff\x80", "이진 바이트 문자열(UTF-8 이 아니다)"),
        (b"d0:i1ee", "빈 키 — v2 의 file tree 가 쓴다"),
        (b"d1:Zi1e1:ai2ee", "키는 원래 바이트로 정렬된다 — 'Z'(0x5A) 가 'a'(0x61) 앞"),
        (b"d1:bi1e1:ai2ee", "정렬되지 않은 키 — 받아들인다(실제 토렌트에 있다, 토렌트 unsorted-info-keys)"),
        (b"l" * 32 + b"e" * 32, "목록 32 겹"),
    ]
    bad = [
        (b"i03e", "정수의 선행 0"),
        (b"03:abc", "길이의 선행 0"),
        (b"i-0e", "-0"),
        (b"ie", "빈 정수"),
        (b"i-e", "부호뿐인 정수"),
        (b"i10000000000000000000e", "20자리 정수(19자리를 넘는다)"),
        (b"i" + b"1" * 64 + b"e", "64자리 정수"),
        (b"i9223372036854775808e", "Long 넘침(MAX + 1)"),
        (b"i-9223372036854775809e", "Long 넘침(MIN - 1)"),
        (b"i+1e", "'+' 붙은 정수"),
        (b"i 1e", "앞에 공백 붙은 정수"),
        (b"i1 e", "뒤에 공백 붙은 정수"),
        (b"i1.5e", "소수"),
        (b"i42", "닫히지 않은 정수"),
        (b"d1:ai1e1:ai2ee", "중복 키(이웃)"),
        (b"d1:ai1e1:bi2e1:ai3ee", "중복 키(떨어져 있고 정렬도 안 됐다 — 바로 앞 키와만 견주면 놓친다)"),
        (b"i1ei2e", "끝에 남는 바이트(값 뒤에 값)"),
        (b"dex", "끝에 남는 바이트"),
        (b"5:abc", "길이가 남은 바이트보다 긴 문자열"),
        (b"9999999999:a", "길이가 Int 를 넘는 문자열"),
        (b"l1:a", "닫히지 않은 목록"),
        (b"d1:ai1e", "닫히지 않은 사전"),
        (b"di1ei2ee", "사전 키가 정수"),
        (b"dlei1ee", "사전 키가 목록"),
        (b"d1:ae", "값 없는 키"),
        (b"-1:a", "음수 길이"),
        (b"+1:a", "'+' 붙은 길이"),
        (b"3abc", "길이 뒤에 ':' 가 없다"),
        (b"", "빈 입력"),
        (b"x", "모르는 바이트"),
        (b"e", "짝 없는 'e'"),
        (b"l" * 1000 + b"e" * 1000, "목록 1000 겹 — 깊이 상한(검토가 권한 64)을 넘는다"),
    ]
    cases = [BencodeCase(d, True, n) for d, n in ok] + [BencodeCase(d, False, n) for d, n in bad]
    for c in cases:
        try:
            bdecode(c.data)
            accepted = True
        except BencodeError:
            accepted = False
        need(accepted == c.valid, f"bencode 사례 '{c.note}' 를 대조용 디코더가 다르게 본다")
    return cases


# ── KRPC(BEP 5·43) ──

@dataclass
class KrpcCase:
    id: str
    data: bytes
    note: str


@dataclass
class KrpcPeersCase:
    data: bytes
    node_ids: list[str]
    node_addrs: list[str]
    peers: list[str]


def compact(addr: str) -> bytes:
    ip, port = addr.rsplit(":", 1)
    return bytes(int(x) for x in ip.split(".")) + u16(int(port))


def build_krpc(t: dict[str, TorrentCase]) -> tuple[list[KrpcCase], list[KrpcPeersCase]]:
    me, target = sha1(b"iroiro-dht-self"), sha1(b"iroiro-dht-target")
    remote, remote2 = sha1(b"iroiro-dht-remote"), sha1(b"iroiro-dht-remote-2")
    info_hash = bytes.fromhex(t["multi-file"].ih1)
    token = sha1(b"iroiro-dht-token")[:8]
    nodes1 = [(sha1(b"iroiro-dht-node-1"), "192.0.2.17:6881"), (sha1(b"iroiro-dht-node-2"), "198.51.100.200:51413")]
    nodes2 = [(sha1(b"iroiro-dht-node-3"), "203.0.113.99:32768"), (sha1(b"iroiro-dht-node-4"), "192.0.2.1:1"),
              (sha1(b"iroiro-dht-node-5"), "198.51.100.255:65535")]
    values = ["203.0.113.5:6881", "203.0.113.250:65535"]

    get_peers = benc({"a": {"id": me, "info_hash": info_hash}, "q": "get_peers", "ro": 1, "t": "aa", "y": "q"})
    find_node = benc({"a": {"id": me, "target": target}, "q": "find_node", "ro": 1, "t": "bb", "y": "q"})
    peers_resp = benc({"ip": compact("198.51.100.7:6881"),
                       "r": {"id": remote, "nodes": b"".join(i + compact(a) for i, a in nodes1), "token": token,
                             "values": [compact(v) for v in values]},
                       "t": "aa", "y": "r"})
    nodes_resp = benc({"r": {"id": remote2, "nodes": b"".join(i + compact(a) for i, a in nodes2)}, "t": "bb",
                       "v": b"LT\x01\x02", "y": "r"})
    error = b"d1:eli201e23:A Generic Error Ocurrede1:t2:aa1:y1:ee"   # BEP 5 의 예 그대로

    def describe(nodes: list[tuple[bytes, str]]) -> str:
        return ", ".join(f"{i.hex()[:8]}… @ {a}" for i, a in nodes)

    cases = [
        KrpcCase("get_peers-query", get_peers,
                 f"get_peers 질의 — t 'aa', y 'q', q 'get_peers', a = {{id: SHA1('iroiro-dht-self') = {me.hex()}, "
                 f"info_hash: 토렌트 multi-file 의 v1 info-hash}}, 맨 위 ro = 1(BEP 43 — 읽기 전용). 키는 바이트 차례."),
        KrpcCase("find_node-query", find_node,
                 f"find_node 질의 — t 'bb', a = {{id: 위와 같다, target: SHA1('iroiro-dht-target') = "
                 f"{target.hex()}}}, ro = 1."),
        KrpcCase("get_peers-response", peers_resp,
                 f"get_peers 응답(t 'aa') — r.id {remote.hex()}, r.token {token.hex()}, r.nodes 둘({describe(nodes1)}), "
                 f"r.values 둘({', '.join(values)}). 맨 위 ip 는 BEP 42 의 '네 주소'(198.51.100.7:6881) — 읽지 않아도 된다."),
        KrpcCase("find_node-response", nodes_resp,
                 f"find_node 응답(t 'bb') — r.id {remote2.hex()}, r.nodes 셋({describe(nodes2)}), values 없음, "
                 "맨 위 v 'LT\\x01\\x02'(상대의 판) — 읽지 않아도 된다. 포트 32768·1·65535 와 바이트 255 는 부호 없는 셈을 본다."),
        KrpcCase("error-response", error,
                 "오류 응답 — e = [201, 'A Generic Error Ocurred'](BEP 5 의 예, 철자 그대로), t 'aa', y 'e'."),
    ]
    responses = [
        KrpcPeersCase(peers_resp, [i.hex() for i, _ in nodes1], [a for _, a in nodes1], values),
        KrpcPeersCase(nodes_resp, [i.hex() for i, _ in nodes2], [a for _, a in nodes2], []),
    ]
    for c in cases:
        msg, _ = bdecode(c.data)
        need(benc(msg) == c.data, f"KRPC {c.id} 는 정렬된 바이트여야 한다")
    for r in responses:
        msg, _ = bdecode(r.data)
        body = msg[b"r"]
        raw_nodes = body.get(b"nodes", b"")
        need(len(raw_nodes) % 26 == 0, "nodes 는 26 바이트씩")
        got_nodes = [(raw_nodes[k:k + 20].hex(), unpack_addr(raw_nodes[k + 20:k + 26]))
                     for k in range(0, len(raw_nodes), 26)]
        got_peers = [unpack_addr(v) for v in body.get(b"values", [])]
        need(got_nodes == list(zip(r.node_ids, r.node_addrs)) and got_peers == r.peers, "KRPC 응답의 기대값")
    return cases, responses


def unpack_addr(b: bytes) -> str:
    need(len(b) == 6, "압축 주소는 6 바이트")
    return f"{b[0]}.{b[1]}.{b[2]}.{b[3]}:{int.from_bytes(b[4:6], 'big')}"


# ── UDP 트래커(BEP 15) ──

@dataclass
class UdpCase:
    id: str
    data: bytes
    note: str


def build_udp(t: dict[str, TorrentCase], peer_id: bytes) -> list[UdpCase]:
    multi = t["multi-file"]
    magic, conn_id = 0x41727101980, 0x8877665544332211
    tid_connect, tid_announce = 0x01020304, 0x0A0B0C0D
    key = 0x9E3779B9
    peers = ["203.0.113.10:6881", "198.51.100.20:51413"]
    connect_req = struct.pack(">QII", magic, 0, tid_connect)
    connect_resp = struct.pack(">IIQ", 0, tid_connect, conn_id)
    announce_req = (struct.pack(">QII", conn_id, 1, tid_announce) + bytes.fromhex(multi.ih1) + peer_id
                    + struct.pack(">QQQIIIiH", 0, multi.total_length, 0, 2, 0, key, -1, 6881))
    announce_resp = struct.pack(">IIIII", 1, tid_announce, 1800, 3, 7) + b"".join(compact(p) for p in peers)
    error = struct.pack(">II", 3, tid_announce) + b"torrent not registered"
    need(len(connect_req) == 16 and len(connect_resp) == 16 and len(announce_req) == 98, "UDP 길이")
    need(announce_req[92:96] == b"\xff\xff\xff\xff", "num_want -1")
    return [
        UdpCase("connect-request", connect_req,
                "connect 요청(16 바이트) — protocol_id 0x41727101980, action 0, transaction_id 0x01020304."),
        UdpCase("connect-response", connect_resp,
                "connect 응답(16 바이트) — action 0, transaction_id 0x01020304, "
                "connection_id 0x8877665544332211(맨 앞 비트가 1)."),
        UdpCase("announce-request", announce_req,
                "announce 요청(98 바이트) — connection_id 0x8877665544332211, action 1, transaction_id 0x0A0B0C0D, "
                f"info_hash = 토렌트 multi-file 의 v1({multi.ih1}), peer_id '{peer_id.decode('ascii')}', downloaded 0, "
                f"left {multi.total_length}, uploaded 0, event 2(started), IP 0(기본), key 0x9E3779B9, "
                "num_want -1(0xFFFFFFFF), port 6881."),
        UdpCase("announce-response", announce_resp,
                f"announce 응답 — action 1, transaction_id 0x0A0B0C0D, interval 1800, leechers 3, seeders 7, "
                f"피어 둘({', '.join(peers)})."),
        UdpCase("error-response", error,
                "오류 응답 — action 3, transaction_id 0x0A0B0C0D, 메시지 'torrent not registered'(남은 바이트 전부, ASCII)."),
    ]


# ── 코틀린 쓰기 ──

def kstr(s: str) -> str:
    """코틀린 문자열 리터럴 — \\·\"·$ 와 제어 문자만 바꾸고 나머지(한국어 포함)는 그대로 둔다."""
    need(len(s.encode("utf-8")) <= LITERAL_LIMIT, "문자열 상수가 64 KB 를 넘는다")
    out = []
    for ch in s:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "$":
            out.append("${'$'}")
        elif ch == "\n":
            out.append("\\n")
        elif ord(ch) < 0x20 or 0x7F <= ord(ch) < 0xA0:
            out.append(f"\\u{ord(ch):04x}")
        else:
            out.append(ch)
    return '"' + "".join(out) + '"'


def kopt(s: str | None) -> str:
    return "null" if s is None else kstr(s)


def kstrs(xs: list[str]) -> str:
    return "listOf(" + ", ".join(kstr(x) for x in xs) + ")" if xs else "emptyList()"


def wrap(text: str) -> list[str]:
    need("/*" not in text and "*/" not in text, f"주석 안의 주석 표: {text}")   # 코틀린의 블록 주석은 겹친다
    return textwrap.wrap(text, width=COMMENT_WIDTH, break_long_words=False, break_on_hyphens=False)


def kdoc(text: str, indent: str) -> list[str]:
    """선언 앞의 KDoc — 길면 여러 줄로."""
    lines = wrap(text)
    if len(lines) == 1:
        return [f"{indent}/** {lines[0]} */"]
    return [f"{indent}/**"] + [f"{indent} * {line}" for line in lines] + [f"{indent} */"]


def kline(text: str, indent: str) -> list[str]:
    """목록 안의 한 항목 앞에 다는 줄 주석."""
    return [f"{indent}// {line}" for line in wrap(text)]


def render(torrents: list[TorrentCase], magnets: list[MagnetCase], rc4: list[Rc4Case], mse: list[MseCase],
           bencode: list[BencodeCase], krpc: list[KrpcCase], responses: list[KrpcPeersCase],
           udp: list[UdpCase]) -> str:
    out: list[str] = []
    w = out.append
    w(HEADER)
    w(f"package {PACKAGE}")
    w("")
    w("/**")
    w(" * 토렌트 엔진의 파서·암호를 바깥에서 대조할 정답 벡터 — PC 의 파이썬(표준 라이브러리만)이 엔진의 코드를 보지 않고 지었다.")
    w(" * 무작위를 쓰지 않아 다시 지어도 같고, 지은 쪽이 스스로 다시 풀어 대조한 뒤에 쓴다(tools/torrent_vectors.py 의 머리말).")
    w(" * 사례마다 무엇을 보이는지는 그 앞의 주석에 있다. 인터넷 주소는 문서용(example.org·192.0.2.0/24·198.51.100.0/24·")
    w(" * 203.0.113.0/24·2001:db8::/32)이다.")
    w(" */")
    w("internal object PythonVectors {")
    w("    /** 결정적 바이트: block(c) = SHA1((\"iroiro-\" + seed + \"-\" + c) 의 UTF-8), c = 0,1,2,… 를 이어 앞 length 바이트. "
      "패드 파일은 0. */")
    w("    class FileSpec(val path: List<String>, val length: Long, val seed: Int, val pad: Boolean, val attr: String)")
    w("")
    w("    class TorrentCase(")
    w("        val id: String,")
    w("        val torrentBase64: String,       // .torrent 전체")
    w("        val expect: String,              // \"ok\" | \"hybrid\"(v1 로 읽어야 한다) | "
      "\"v2-only\"(거부) | \"private\"(거부)")
    w("        val infoHashV1Hex: String,       // SHA-1(info 사전의 **원래 바이트**), 소문자")
    w("        val infoHashV2Hex: String?,      // v2·하이브리드면 SHA-256(info 원래 바이트)")
    w("        val name: String,")
    w("        val pieceLength: Int,")
    w("        val totalLength: Long,           // v1 꼴 파일 길이 합(패드 포함), 순수 v2 는 파일 길이 합")
    w("        val pieceHashesHex: String,      // 20바이트씩 이은 hex(순수 v2 는 \"\")")
    w("        val files: List<FileSpec>,       // v1 info 의 차례(패드 포함). 한 파일 꼴이면 path = listOf(name)")
    w("        val trackers: List<List<String>>,// announce-list 의 층(없으면 announce 하나를 한 층으로, 그것도 없으면 빈 목록)")
    w("        val webSeeds: List<String>,      // url-list")
    w("    )")
    w("")
    out += kdoc("토렌트 " + str(len(torrents)) + " — " + ", ".join(c.id for c in torrents)
                + ". 무엇을 보이는지는 각 함수의 주석에 있다. 파일 내용은 [FileSpec] 의 규칙으로 지으면 조각 해시까지 견줄 수 있다.", "    ")
    calls = ", ".join(f"torrent{i + 1}()" for i in range(len(torrents)))
    w(f"    val torrents: List<TorrentCase> = listOf({calls})")
    w("")
    w("    /** expect 가 \"ok\" 가 아니면 infoHashHex·name 은 null, 목록은 빈 것이다. peers 의 IPv6 는 \"[주소]:포트\" 꼴. */")
    w("    class MagnetCase(val uri: String, val expect: String /* \"ok\" | \"v2-only\" | \"invalid\" */, "
      "val infoHashHex: String?, val name: String?,")
    w("        val trackers: List<String>, val webSeeds: List<String>, val peers: List<String>)")
    w("")
    out += kdoc(f"마그넷 {len(magnets)} — 사례마다의 뜻은 그 줄 위의 주석.", "    ")
    w("    val magnets: List<MagnetCase> = magnetCases()")
    w("")
    w("    /** 키 흐름의 앞 drop 바이트를 버린 뒤 평문과 xor 한 것이 암호문이다. */")
    w("    class Rc4Case(val keyHex: String, val drop: Int, val plaintextHex: String, val ciphertextHex: String)")
    w("")
    out += kdoc(f"RC4 {len(rc4)} — 위키백과의 시험 벡터 셋, RFC 6229 의 키 0102030405 둘(오프셋 0·1024), SHA 로 지은 키 둘(drop 1024).",
                "    ")
    w("    val rc4: List<Rc4Case> = rc4Cases()")
    w("")
    w("    class MseCase(")
    w("        val infoHashHex: String, val xaHex: String, val xbHex: String, "
      "val padAHex: String, val padBHex: String,")
    w("        val cryptoProvide: Int, val cryptoSelect: Int, "
      "val padCHex: String, val padDHex: String, val iaHex: String,")
    w("        val yaHex: String, val ybHex: String, val sHex: String,            // 셋 다 96바이트 big-endian(앞을 0 으로 채움)")
    w("        val req1Hex: String, val req2xor3Hex: String, val keyAHex: String, val keyBHex: String,")
    w("        val message1Hex: String,     // Ya ‖ PadA")
    w("        val message2Hex: String,     // Yb ‖ PadB")
    w("        val message3Hex: String,     // SHA1('req1'‖S) ‖ (SHA1('req2'‖SKEY) xor SHA1('req3'‖S)) ‖ "
      "ENC_A(VC ‖ crypto_provide ‖ len(PadC) ‖ PadC ‖ len(IA)) ‖ ENC_A(IA)")
    w("        val message4Hex: String,     // ENC_B(VC ‖ crypto_select ‖ len(PadD) ‖ PadD)")
    w("        val afterAPlainHex: String, val afterACipherHex: String,   // 그 뒤 A 가 보내는 바이트와 실제로 선 위에 나가는 바이트"
      "(select 가 RC4 면 RC4_A 를 이어서, 평문이면 그대로)")
    w("        val afterBPlainHex: String, val afterBCipherHex: String,   // B 쪽도 같게")
    w("    )")
    w("")
    out += kdoc("MSE 둘. P 는 명세의 768 비트 소수, G = 2, SKEY = info-hash, keyA·keyB 의 RC4 는 키 흐름 앞 1024 바이트를 버리고 "
                "한 쪽의 ENCRYPT 는 한 흐름으로 이어진다. Xa·Xb 는 SHA-1 로 지은 160 비트 값 가운데 큰 정수의 바이트 꼴을 시험하도록 "
                "골랐다(각 사례의 주석). B 의 자리에서 req1 으로, A 의 자리에서 ENCRYPT(VC) 로 동기화해 풀어 보는 것까지 대조했다.", "    ")
    w("    val mse: List<MseCase> = listOf(" + ", ".join(f"mse{i + 1}()" for i in range(len(mse))) + ")")
    w("")
    w("    class BencodeCase(val inputHex: String, val valid: Boolean, val note: String)")
    w("")
    out += kdoc(f"bencode {len(bencode)} — 받을 것 {sum(c.valid for c in bencode)}, 거부할 것 "
                f"{sum(not c.valid for c in bencode)}. 정렬되지 않은 키는 받는다(토렌트 unsorted-info-keys 와 같은 까닭).", "    ")
    w("    val bencode: List<BencodeCase> = bencodeCases()")
    w("")
    w("    class KrpcCase(val id: String, val bytesHex: String, val note: String)")
    w("    // 풀어 본 결과를 시험이 대조할 수 있게, 응답 사례는 note 에 사람이 읽는 기대값을 적고, 따로 아래 둘을 둔다:")
    w("    class KrpcPeersCase(val bytesHex: String, val nodeIdsHex: List<String>, "
      "val nodeAddrs: List<String> /* \"ip:port\" */, "
      "val peers: List<String> /* \"ip:port\" */)")
    w("")
    out += kdoc(f"KRPC {len(krpc)} — 질의 둘(키는 바이트 차례, 맨 위 ro = 1), 응답 둘, 오류 하나.", "    ")
    w("    val krpc: List<KrpcCase> = krpcCases()")
    w("")
    out += kdoc("위 응답 둘(get_peers·find_node)을 풀어 낸 기대값 — nodes 는 26 바이트씩(id 20 + IPv4 4 + 포트 2), values 는 6 바이트씩.",
                "    ")
    w("    val krpcResponses: List<KrpcPeersCase> = krpcResponseCases()")
    w("")
    w("    class UdpCase(val id: String, val bytesHex: String, val note: String)")
    w("")
    out += kdoc(f"UDP 트래커 {len(udp)} — 모든 수는 big-endian.", "    ")
    w("    val udp: List<UdpCase> = udpCases()")

    for i, c in enumerate(torrents):
        w("")
        out += kdoc(f"{c.id}: {c.doc}", "    ")
        w(f"    private fun torrent{i + 1}() = TorrentCase(")
        w(f"        id = {kstr(c.id)},")
        w(f"        torrentBase64 = {kstr(base64.b64encode(c.raw).decode('ascii'))},")
        w(f"        expect = {kstr(c.expect)},")
        w(f"        infoHashV1Hex = {kstr(c.ih1)},")
        w(f"        infoHashV2Hex = {kopt(c.ih2)},")
        w(f"        name = {kstr(c.name)},")
        w(f"        pieceLength = {c.piece_length},")
        w(f"        totalLength = {c.total_length}L,")
        w(f"        pieceHashesHex = {kstr(c.pieces_hex)},")
        w("        files = listOf(")
        for f in c.files:
            w(f"            FileSpec(path = {kstrs(f.path)}, length = {f.length}L, seed = {f.seed}, "
              f"pad = {'true' if f.pad else 'false'}, attr = {kstr(f.attr)}),")
        w("        ),")
        tiers = "listOf(" + ", ".join(kstrs(t) for t in c.trackers) + ")" if c.trackers else "emptyList()"
        w(f"        trackers = {tiers},")
        w(f"        webSeeds = {kstrs(c.web_seeds)},")
        w("    )")

    w("")
    w("    private fun magnetCases() = listOf(")
    for c in magnets:
        out += kline(c.comment, "        ")
        w(f"        MagnetCase({kstr(c.uri)}, {kstr(c.expect)}, {kopt(c.info_hash)}, {kopt(c.name)}, "
          f"{kstrs(c.trackers)}, "
          f"{kstrs(c.web_seeds)}, {kstrs(c.peers)}),")
    w("    )")

    w("")
    w("    private fun rc4Cases() = listOf(")
    for c in rc4:
        out += kline(c.comment, "        ")
        w(f"        Rc4Case({kstr(c.key.hex())}, {c.drop}, {kstr(c.plaintext.hex())}, {kstr(c.ciphertext.hex())}),")
    w("    )")

    for i, c in enumerate(mse):
        w("")
        out += kdoc(c.comment, "    ")
        w(f"    private fun mse{i + 1}() = MseCase(")
        fields = [("infoHashHex", c.info_hash), ("xaHex", c.xa), ("xbHex", c.xb), ("padAHex", c.pad_a),
                  ("padBHex", c.pad_b)]
        for k, v in fields:
            w(f"        {k} = {kstr(v.hex())},")
        w(f"        cryptoProvide = {c.provide},")
        w(f"        cryptoSelect = {c.select},")
        fields = [("padCHex", c.pad_c), ("padDHex", c.pad_d), ("iaHex", c.ia), ("yaHex", c.ya), ("ybHex", c.yb),
                  ("sHex", c.s), ("req1Hex", c.req1), ("req2xor3Hex", c.req23), ("keyAHex", c.key_a),
                  ("keyBHex", c.key_b), ("message1Hex", c.m1), ("message2Hex", c.m2), ("message3Hex", c.m3),
                  ("message4Hex", c.m4), ("afterAPlainHex", c.after_a), ("afterACipherHex", c.after_a_wire),
                  ("afterBPlainHex", c.after_b), ("afterBCipherHex", c.after_b_wire)]
        for k, v in fields:
            w(f"        {k} = {kstr(v.hex())},")
        w("    )")

    w("")
    w("    private fun bencodeCases() = listOf(")
    for c in bencode:
        w(f"        BencodeCase({kstr(c.data.hex())}, {'true' if c.valid else 'false'}, {kstr(c.note)}),")
    w("    )")

    w("")
    w("    private fun krpcCases() = listOf(")
    for c in krpc:
        w(f"        KrpcCase({kstr(c.id)}, {kstr(c.data.hex())}, {kstr(c.note)}),")
    w("    )")

    w("")
    w("    private fun krpcResponseCases() = listOf(")
    for r in responses:
        w(f"        KrpcPeersCase({kstr(r.data.hex())}, {kstrs(r.node_ids)}, {kstrs(r.node_addrs)}, {kstrs(r.peers)}),")
    w("    )")

    w("")
    w("    private fun udpCases() = listOf(")
    for c in udp:
        w(f"        UdpCase({kstr(c.id)}, {kstr(c.data.hex())}, {kstr(c.note)}),")
    w("    )")
    w("}")
    return "\n".join(out) + "\n"


# ── 짓기 ──

def build() -> tuple[str, dict[str, int]]:
    torrents = build_torrents()
    for case in torrents:
        check_torrent(case)
    by_id = {c.id: c for c in torrents}
    need(len(by_id) == len(torrents), "토렌트 id 가 겹친다")
    seeds = [f.seed for c in torrents for f in (c.v2_files or c.files) if not f.pad]
    need(len(set(seeds)) == len(seeds), "파일의 seed 가 겹친다")
    magnets = build_magnets(by_id)
    for m in magnets:
        check_magnet(m)
    rc4 = build_rc4()
    mse = build_mse(by_id)
    bencode = build_bencode()
    krpc, responses = build_krpc(by_id)
    peer_id = mse[0].ia[48:68]
    udp = build_udp(by_id, peer_id)
    text = render(torrents, magnets, rc4, mse, bencode, krpc, responses, udp)
    counts = {"토렌트": len(torrents), "마그넷": len(magnets), "RC4": len(rc4), "MSE": len(mse), "bencode": len(bencode),
              "KRPC": len(krpc), "KRPC 풀이": len(responses), "UDP": len(udp)}
    return text, counts


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="토렌트 엔진의 정답 벡터(PythonVectors.kt)를 짓는다.")
    parser.add_argument("--check", action="store_true", help="쓰지 않고 지금 파일과 같은지만 본다")
    args = parser.parse_args()
    try:
        text, counts = build()
        need(build()[0] == text, "두 번 지은 결과가 다르다")
    except (VectorError, BencodeError) as e:
        sys.exit(f"대조 실패 — 쓰지 않았다: {e}")
    data = text.encode("utf-8")
    summary = " · ".join(f"{k} {v}" for k, v in counts.items())
    digest = hashlib.sha256(data).hexdigest()
    rel = OUT.relative_to(ROOT).as_posix()
    if args.check:
        same = OUT.is_file() and OUT.read_bytes() == data
        print(f"{rel}: {'같다' if same else '다르다 — python -I tools/torrent_vectors.py 로 다시 지어라'} ({summary})")
        sys.exit(0 if same else 1)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", newline="\n", encoding="utf-8") as fp:
        fp.write(text)
    need(OUT.read_bytes() == data, "쓴 파일이 지은 것과 다르다")
    print(f"{rel} — {summary} — {len(data):,} 바이트, sha256 {digest}")


if __name__ == "__main__":
    main()
