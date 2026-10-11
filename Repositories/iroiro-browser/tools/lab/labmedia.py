"""실험실의 실제 미디어 — 에뮬레이터의 MediaRecorder 가 만든 조각난 MP4(H.264·AAC)·WebM 을 나눠 DASH·HLS·MSE 쪽에 쓰고, 앱이 담은 MP4 를
읽어 확인한다. 표준 라이브러리만 쓴다.

시험 미디어는 ``/lab/maker`` 쪽이 기기에서 만든다(캔버스에 그린 숫자와 사인파 — 저작물이 아니다). 만든 것은 ``local/lab/media/`` (git 무시)에
둔다 — 저장소에 넣지 않는다.

TS 는 같은 표본으로 직접 짠다(:func:`to_ts` — H.264 는 Annex-B 로, AAC 는 ADTS 머리를 붙여 PES 로). 묶음 AAC(HLS 의 따로 된 소리) 조각은
ID3 의 ``com.apple.streaming.transportStreamTimestamp`` 를 앞에 둔다(:func:`packed_aac`).
"""

from __future__ import annotations

import functools
import pathlib
import struct

ROOT = pathlib.Path(__file__).resolve().parents[2]
MEDIA = ROOT / "local" / "lab" / "media"


# ── 상자 ─────────────────────────────────────────────────────────────────────────────────────────────────────────────


def boxes(data: bytes, start: int = 0, end: int | None = None) -> list[tuple[str, int, int, int]]:
    """맨 위(또는 [start, end) 안의) 상자들 — (형, 시작, 머리 길이, 전체 길이)."""
    end = len(data) if end is None else end
    out = []
    p = start
    while p + 8 <= end:
        size, kind = struct.unpack(">I4s", data[p:p + 8])
        head = 8
        if size == 1:
            size = struct.unpack(">Q", data[p + 8:p + 16])[0]
            head = 16
        elif size == 0:
            size = end - p
        if size < head:
            break
        out.append((kind.decode("latin-1"), p, head, size))
        p += size
    return out


def child(data: bytes, parent: tuple[str, int, int, int], path: list[str], skip: int = 0) -> list[tuple[str, int, int, int]]:
    """[parent] 아래에서 [path] 를 따라 내려간 상자들(마지막 형에 맞는 것 모두). [skip] 은 parent 의 내용 앞에 건너뛸 바이트(stsd 등)."""
    found = [parent]
    for i, name in enumerate(path):
        nxt = []
        for b in found:
            inner = b[1] + b[2] + (skip if i == 0 else 0)
            nxt += [c for c in boxes(data, inner, b[1] + b[3]) if c[0] == name]
        found = nxt
    return found


def full_box(data: bytes, b: tuple[str, int, int, int]) -> tuple[int, int, int]:
    """FullBox 의 (판, 깃발, 내용 시작)."""
    p = b[1] + b[2]
    return data[p], int.from_bytes(data[p + 1:p + 4], "big"), p + 4


def split_fmp4(data: bytes) -> tuple[bytes, list[bytes]]:
    """조각난 MP4 를 초기 조각(첫 moof 앞)과 조각들(moof 와 그 뒤의 상자들)로."""
    top = boxes(data)
    first = next((b[1] for b in top if b[0] == "moof"), len(data))
    init = data[:first]
    segments: list[bytes] = []
    current = None
    for kind, start, _, size in top:
        if start < first:
            continue
        if kind == "moof":
            if current is not None:
                segments.append(current)
            current = b""
        if current is not None and kind not in ("mfra",):
            current += data[start:start + size]
    if current:
        segments.append(current)
    return init, segments


def timescale(init: bytes) -> int:
    moov = next(b for b in boxes(init) if b[0] == "moov")
    mdhd = child(init, moov, ["trak", "mdia", "mdhd"])[0]
    version, _, p = full_box(init, mdhd)
    return struct.unpack(">I", init[p + 16:p + 20] if version == 1 else init[p + 8:p + 12])[0]


def segment_samples(seg: bytes) -> list[dict]:
    """조각 하나의 표본(길이·시간 길이·키 여부·cts) — tfhd·trun 의 기본값을 따른다."""
    out = []
    for moof in [b for b in boxes(seg) if b[0] == "moof"]:
        for traf in child(seg, moof, ["traf"]):
            tfhd = child(seg, traf, ["tfhd"])[0]
            _, flags, p = full_box(seg, tfhd)
            p += 4  # track_ID
            if flags & 0x01:
                p += 8
            if flags & 0x02:
                p += 4
            d_dur = d_size = d_flags = 0
            if flags & 0x08:
                d_dur = struct.unpack(">I", seg[p:p + 4])[0]
                p += 4
            if flags & 0x10:
                d_size = struct.unpack(">I", seg[p:p + 4])[0]
                p += 4
            if flags & 0x20:
                d_flags = struct.unpack(">I", seg[p:p + 4])[0]
            tfdt = child(seg, traf, ["tfdt"])
            base = 0
            if tfdt:
                v, _, q = full_box(seg, tfdt[0])
                base = struct.unpack(">Q", seg[q:q + 8])[0] if v == 1 else struct.unpack(">I", seg[q:q + 4])[0]
            for trun in child(seg, traf, ["trun"]):
                version, tflags, q = full_box(seg, trun)
                count = struct.unpack(">I", seg[q:q + 4])[0]
                q += 4
                data_offset = None
                if tflags & 0x01:
                    data_offset = struct.unpack(">i", seg[q:q + 4])[0]
                    q += 4
                first_flags = None
                if tflags & 0x04:
                    first_flags = struct.unpack(">I", seg[q:q + 4])[0]
                    q += 4
                pos = moof[1] + (data_offset or 0)
                t = base
                for i in range(count):
                    dur, size, sflags, cts = d_dur, d_size, d_flags, 0
                    if tflags & 0x100:
                        dur = struct.unpack(">I", seg[q:q + 4])[0]
                        q += 4
                    if tflags & 0x200:
                        size = struct.unpack(">I", seg[q:q + 4])[0]
                        q += 4
                    if tflags & 0x400:
                        sflags = struct.unpack(">I", seg[q:q + 4])[0]
                        q += 4
                    if tflags & 0x800:
                        cts = struct.unpack(">i" if version else ">I", seg[q:q + 4])[0]
                        q += 4
                    if i == 0 and first_flags is not None:
                        sflags = first_flags
                    key = not (sflags & 0x00010000)
                    out.append({"pos": pos, "size": size, "dur": dur, "dts": t, "cts": cts, "key": key})
                    pos += size
                    t += dur
    return out


def segment_seconds(seg: bytes, ts: int) -> float:
    return sum(s["dur"] for s in segment_samples(seg)) / ts if ts else 1.0


# ── 시험 미디어 → 쪽 ─────────────────────────────────────────────────────────────────────────────────────────────────


def have(*names: str) -> bool:
    return all((MEDIA / n).is_file() and (MEDIA / n).stat().st_size > 0 for n in names)


def parts(name: str) -> tuple[bytes, list[bytes], int]:
    return _parts(name, (MEDIA / name).stat().st_mtime_ns)


@functools.lru_cache(maxsize=16)
def _parts(name: str, _mtime: int) -> tuple[bytes, list[bytes], int]:
    data = (MEDIA / name).read_bytes()
    init, segs = split_fmp4(data)
    return init, segs, timescale(init)


def dash_mpd() -> str:
    """영상(v.mp4)과 소리(a.mp4)를 따로 된 화질로 — 조각 목록(SegmentList)."""
    vi, vs, vts = parts("v.mp4")
    ai, as_, ats = parts("a.mp4")
    total = max(sum(segment_seconds(s, vts) for s in vs), sum(segment_seconds(s, ats) for s in as_))

    def seglist(prefix: str, n: int) -> str:
        return "".join(f'<SegmentURL media="{prefix}/seg{i + 1}.m4s"/>' for i in range(n))
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT{total:.3f}S" minBufferTime="PT2S">\n'
        "  <Period>\n"
        '    <AdaptationSet mimeType="video/mp4" contentType="video">\n'
        '      <Representation id="v" bandwidth="600000" width="320" height="240" codecs="avc1.42E01E">\n'
        f'        <SegmentList duration="1" timescale="1"><Initialization sourceURL="v/init.mp4"/>{seglist("v", len(vs))}</SegmentList>\n'
        "      </Representation>\n"
        "    </AdaptationSet>\n"
        '    <AdaptationSet mimeType="audio/mp4" lang="und">\n'
        '      <Representation id="a" bandwidth="96000" codecs="mp4a.40.2">\n'
        f'        <SegmentList duration="1" timescale="1"><Initialization sourceURL="a/init.mp4"/>{seglist("a", len(as_))}</SegmentList>\n'
        "      </Representation>\n"
        "    </AdaptationSet>\n"
        "  </Period>\n"
        "</MPD>\n"
    )


def dash_webm_mpd() -> str:
    """VP9 영상(v.webm)과 Opus 소리(a.webm)를 파일 하나씩(BaseURL 만 — 통째로 받는다)."""
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT6.5S" minBufferTime="PT2S">\n'
        "  <Period>\n"
        '    <AdaptationSet mimeType="video/webm" contentType="video">\n'
        '      <Representation id="vw" bandwidth="600000" width="320" height="240" codecs="vp9"><BaseURL>v.webm</BaseURL></Representation>\n'
        "    </AdaptationSet>\n"
        '    <AdaptationSet mimeType="audio/webm" lang="und">\n'
        '      <Representation id="aw" bandwidth="96000" codecs="opus"><BaseURL>a.webm</BaseURL></Representation>\n'
        "    </AdaptationSet>\n"
        "  </Period>\n"
        "</MPD>\n"
    )


def webm_codecs(data: bytes) -> list[str]:
    """WebM 의 CodecID(86 — 그 뒤 길이와 글) — 담긴 코덱들."""
    out = []
    i = data.find(b"\x86")
    while i >= 0 and i + 2 < len(data):
        n = data[i + 1] & 0x7F if data[i + 1] & 0x80 else 0
        if 0 < n < 32:
            word = data[i + 2:i + 2 + n]
            if word[:2] in (b"V_", b"A_") and all(32 < c < 127 for c in word):
                out.append(word.decode("ascii"))
        i = data.find(b"\x86", i + 1)
    return out


def hls_master(kind: str) -> str:
    """[kind] = fmp4(영상 fMP4 + 소리 fMP4) · ts(영상 TS + 묶음 AAC)."""
    return (
        "#EXTM3U\n#EXT-X-VERSION:7\n"
        f'#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="sound",DEFAULT=YES,AUTOSELECT=YES,URI="{kind}-a.m3u8"\n'
        '#EXT-X-STREAM-INF:BANDWIDTH=700000,RESOLUTION=320x240,AUDIO="aud"\n'
        f"{kind}-v.m3u8\n"
    )


def hls_media(kind: str, track: str) -> str:
    name = "v.mp4" if track == "v" else "a.mp4"
    init, segs, ts = parts(name)
    lines = ["#EXTM3U", "#EXT-X-VERSION:7", "#EXT-X-TARGETDURATION:3", "#EXT-X-MEDIA-SEQUENCE:0", "#EXT-X-PLAYLIST-TYPE:VOD"]
    if kind == "fmp4":
        lines.append(f'#EXT-X-MAP:URI="{track}/init.mp4"')
    for i, s in enumerate(segs):
        ext = "m4s" if kind == "fmp4" else ("ts" if track == "v" else "aac")
        folder = track if kind == "fmp4" else f"ts{track}"
        lines += [f"#EXTINF:{max(segment_seconds(s, ts), 0.1):.3f},", f"{folder}/seg{i + 1}.{ext}"]
    lines.append("#EXT-X-ENDLIST")
    return "\n".join(lines) + "\n"


# ── TS 짜기(시험용) ─────────────────────────────────────────────────────────────────────────────────────────────────


def avc_config(init: bytes) -> tuple[int, list[bytes], list[bytes]]:
    """avcC — (NAL 길이 바이트 수, SPS 들, PPS 들)."""
    moov = next(b for b in boxes(init) if b[0] == "moov")
    stsd = child(init, moov, ["trak", "mdia", "minf", "stbl", "stsd"])[0]
    _, _, p = full_box(init, stsd)
    entry = boxes(init, p + 4, stsd[1] + stsd[3])[0]
    # VisualSampleEntry: 8(SampleEntry) + 70 바이트 뒤에 자식 상자들.
    avcc = next(b for b in boxes(init, entry[1] + entry[2] + 78, entry[1] + entry[3]) if b[0] == "avcC")
    q = avcc[1] + avcc[2]
    length_size = (init[q + 4] & 3) + 1
    n_sps = init[q + 5] & 0x1F
    q += 6
    sps, pps = [], []
    for _ in range(n_sps):
        n = struct.unpack(">H", init[q:q + 2])[0]
        sps.append(init[q + 2:q + 2 + n])
        q += 2 + n
    n_pps = init[q]
    q += 1
    for _ in range(n_pps):
        n = struct.unpack(">H", init[q:q + 2])[0]
        pps.append(init[q + 2:q + 2 + n])
        q += 2 + n
    return length_size, sps, pps


def aac_config(init: bytes) -> tuple[int, int, int]:
    """esds 의 AudioSpecificConfig — (object type, 표본률 번호, 채널 수)."""
    i = init.find(b"esds")
    data = init[i:]
    k = data.find(b"\x05")  # DecoderSpecificInfo 태그
    while k >= 0:
        # 길이(가변) 뒤 2 바이트가 ASC 이다 — 길이가 2 인 것을 찾는다.
        p = k + 1
        n = 0
        while True:
            c = data[p]
            p += 1
            n = (n << 7) | (c & 0x7F)
            if not c & 0x80:
                break
        if n == 2:
            asc = data[p:p + 2]
            obj = asc[0] >> 3
            freq = ((asc[0] & 7) << 1) | (asc[1] >> 7)
            ch = (asc[1] >> 3) & 0xF
            return obj, freq, ch
        k = data.find(b"\x05", k + 1)
    raise ValueError("no AudioSpecificConfig")


def adts(frame: bytes, obj: int, freq: int, ch: int) -> bytes:
    n = len(frame) + 7
    return bytes([0xFF, 0xF1, ((obj - 1) << 6) | (freq << 2) | (ch >> 2), ((ch & 3) << 6) | (n >> 11), (n >> 3) & 0xFF,
                  ((n & 7) << 5) | 0x1F, 0xFC]) + frame


def _pts(marker: int, v: int) -> bytes:
    return bytes([(marker << 4) | (((v >> 30) & 7) << 1) | 1, (v >> 22) & 0xFF, (((v >> 15) & 0x7F) << 1) | 1, (v >> 7) & 0xFF,
                  ((v & 0x7F) << 1) | 1])


class TsWriter:
    """MPEG-TS 를 짠다 — PAT·PMT 와 PES 하나 또는 둘(영상 0x100, 소리 0x101). PCR 은 영상 PES 에 싣는다."""

    def __init__(self, video: bool, audio: bool) -> None:
        self.video, self.audio = video, audio
        self.cc: dict[int, int] = {}
        self.out = bytearray()

    def _packets(self, pid: int, payload: bytes, start: bool, pcr: int | None = None) -> None:
        first = True
        while first or payload:
            cc = self.cc.get(pid, 0)
            self.cc[pid] = (cc + 1) & 0xF
            body = b""
            if first and pcr is not None:
                body = bytes([0x10, (pcr >> 25) & 0xFF, (pcr >> 17) & 0xFF, (pcr >> 9) & 0xFF, (pcr >> 1) & 0xFF, ((pcr & 1) << 7) | 0x7E, 0])
            room = 184 - ((1 + len(body)) if body else 0)
            chunk = payload[:room]
            payload = payload[len(chunk):]
            if len(chunk) < room:
                # 남는 자리는 adaptation field 의 채움(0xFF)으로 — 그 필드 전체가 184 - 내용 길이가 되게.
                need = 184 - len(chunk)
                if need == 1:
                    field = bytes([0])
                else:
                    filled = (body if body else bytes([0x00]))
                    filled += bytes([0xFF]) * (need - 1 - len(filled))
                    field = bytes([len(filled)]) + filled
            elif body:
                field = bytes([len(body)]) + body
            else:
                field = b""
            control = 0x30 if field else 0x10
            self.out += bytes([0x47, (0x40 if (start and first) else 0) | (pid >> 8), pid & 0xFF, control | cc]) + field + chunk
            first = False

    def tables(self) -> None:
        pat = bytes([0x00, 0x00, 0xB0, 0x0D, 0x00, 0x01, 0xC1, 0x00, 0x00, 0x00, 0x01, 0xF0, 0x00])
        self._packets(0, pat + struct.pack(">I", crc32_mpeg(pat[1:])), True)
        streams = b""
        if self.video:
            streams += bytes([0x1B, 0xE1, 0x00, 0xF0, 0x00])
        if self.audio:
            streams += bytes([0x0F, 0xE1, 0x01, 0xF0, 0x00])
        length = 9 + len(streams) + 4
        pmt = bytes([0x00, 0x02, 0xB0 | (length >> 8), length & 0xFF, 0x00, 0x01, 0xC1, 0x00, 0x00,
                     0xE1, 0x00 if self.video else 0x01, 0xF0, 0x00]) + streams
        self._packets(0x1000, pmt + struct.pack(">I", crc32_mpeg(pmt[1:])), True)

    def pes(self, pid: int, stream_id: int, data: bytes, pts: int, dts: int | None) -> None:
        if dts is not None and dts != pts:
            header = bytes([0x80, 0xC0, 10]) + _pts(3, pts) + _pts(1, dts)
        else:
            header = bytes([0x80, 0x80, 5]) + _pts(2, pts)
        length = 0 if stream_id == 0xE0 else len(header) + len(data)
        packet = bytes([0, 0, 1, stream_id]) + struct.pack(">H", length if length < 65536 else 0) + header + data
        self._packets(pid, packet, True, pcr=(dts if dts is not None else pts) if pid == 0x100 else None)


def crc32_mpeg(data: bytes) -> int:
    crc = 0xFFFFFFFF
    for b in data:
        crc ^= b << 24
        for _ in range(8):
            crc = ((crc << 1) ^ 0x04C11DB7) if crc & 0x80000000 else (crc << 1)
            crc &= 0xFFFFFFFF
    return crc


PTS_BASE = 900_000  # 10초 — 실제 HLS 처럼 0 이 아닌 데서 시작한다


def to_ts_segments() -> list[bytes]:
    """v.mp4 의 조각마다 TS 조각 하나(영상만 — 소리는 묶음 AAC 로 따로). 짜는 데 오래 걸려(순수 파이썬) 한 번만 짠다 — 요청마다 짜면 바쁠 때
    실험실 프록시의 10초 제한을 넘겨 앱이 '끊김' 으로 멈췄다."""
    return _ts_segments((MEDIA / "v.mp4").stat().st_mtime_ns)


@functools.lru_cache(maxsize=2)
def _ts_segments(_mtime: int) -> list[bytes]:
    init, segs, ts = parts("v.mp4")
    length_size, sps, pps = avc_config(init)
    out = []
    for seg in segs:
        w = TsWriter(video=True, audio=False)
        w.tables()
        for s in segment_samples(seg):
            raw = seg[s["pos"]:s["pos"] + s["size"]]
            nals = []
            p = 0
            while p + length_size <= len(raw):
                n = int.from_bytes(raw[p:p + length_size], "big")
                nals.append(raw[p + length_size:p + length_size + n])
                p += length_size + n
            au = b"\x00\x00\x00\x01\x09\xF0"  # AUD
            if s["key"]:
                for x in sps + pps:
                    au += b"\x00\x00\x00\x01" + x
            for x in nals:
                if x and (x[0] & 0x1F) in (7, 8, 9):
                    continue
                au += b"\x00\x00\x00\x01" + x
            dts = PTS_BASE + s["dts"] * 90000 // ts
            pts = PTS_BASE + (s["dts"] + s["cts"]) * 90000 // ts
            w.pes(0x100, 0xE0, au, pts, dts)
        out.append(bytes(w.out))
    return out


def id3_timestamp(pts: int) -> bytes:
    owner = b"com.apple.streaming.transportStreamTimestamp\x00"
    body = owner + struct.pack(">Q", pts & 0x1FFFFFFFF)
    frame = b"PRIV" + _syncsafe(len(body)) + b"\x00\x00" + body
    return b"ID3\x04\x00\x00" + _syncsafe(len(frame)) + frame


def _syncsafe(n: int) -> bytes:
    return bytes([(n >> 21) & 0x7F, (n >> 14) & 0x7F, (n >> 7) & 0x7F, n & 0x7F])


def packed_aac_segments() -> list[bytes]:
    """a.mp4 의 조각마다 묶음 AAC 조각 하나 — ID3 시각(영상 TS 와 같은 시계) + ADTS 프레임들."""
    return _packed_aac((MEDIA / "a.mp4").stat().st_mtime_ns)


@functools.lru_cache(maxsize=2)
def _packed_aac(_mtime: int) -> list[bytes]:
    init, segs, ts = parts("a.mp4")
    obj, freq, ch = aac_config(init)
    out = []
    for seg in segs:
        samples = segment_samples(seg)
        if not samples:
            continue
        pts = PTS_BASE + samples[0]["dts"] * 90000 // ts
        data = id3_timestamp(pts)
        for s in samples:
            data += adts(seg[s["pos"]:s["pos"] + s["size"]], obj, freq, ch)
        out.append(data)
    return out


# ── 앱이 담은 MP4 확인 ───────────────────────────────────────────────────────────────────────────────────────────────


def mp4_info(data: bytes) -> dict:
    """담긴 MP4 의 트랙들 — 처리기(vide/soun), 표본 형(avc1/mp4a…), 표본 수, 길이(초). 그리고 전체 길이."""
    top = boxes(data)
    moov = next((b for b in top if b[0] == "moov"), None)
    if moov is None:
        return {"tracks": [], "duration": 0.0, "fragmented": any(b[0] == "moof" for b in top)}
    mvhd = child(data, moov, ["mvhd"])[0]
    v, _, p = full_box(data, mvhd)
    if v == 1:
        scale, dur = struct.unpack(">IQ", data[p + 16:p + 28])
    else:
        scale, dur = struct.unpack(">II", data[p + 8:p + 16])
    tracks = []
    for trak in child(data, moov, ["trak"]):
        hdlr = child(data, trak, ["mdia", "hdlr"])[0]
        _, _, q = full_box(data, hdlr)
        handler = data[q + 4:q + 8].decode("latin-1")
        mdhd = child(data, trak, ["mdia", "mdhd"])[0]
        mv, _, q = full_box(data, mdhd)
        tscale, tdur = struct.unpack(">IQ", data[q + 16:q + 28]) if mv == 1 else struct.unpack(">II", data[q + 8:q + 16])
        stsd = child(data, trak, ["mdia", "minf", "stbl", "stsd"])[0]
        _, _, q = full_box(data, stsd)
        entry = boxes(data, q + 4, stsd[1] + stsd[3])
        stsz = child(data, trak, ["mdia", "minf", "stbl", "stsz"])
        count = 0
        if stsz:
            _, _, q = full_box(data, stsz[0])
            count = struct.unpack(">I", data[q + 4:q + 8])[0]
        tracks.append({"handler": handler, "codec": entry[0][0] if entry else "?", "samples": count,
                       "seconds": round(tdur / tscale, 2) if tscale else 0.0})
    return {"tracks": tracks, "duration": round(dur / scale, 2) if scale else 0.0, "fragmented": False}
