"""실험실 서버 — 에뮬레이터의 Web 실험판이 붙는 것 전부를 PC 의 루프백에 세운다. 표준 라이브러리만 쓴다.

에뮬레이터 안에서 ``10.0.2.2`` 는 PC 의 ``127.0.0.1`` 이다. 그래서 서버는 전부 ``127.0.0.1`` 에만 선다(랜에 열지 않는다).

====================  ======  ===============================================================
무엇                   포트     하는 일
====================  ======  ===============================================================
시험 프록시(TLS)        18443   사용자의 Squid 와 같은 꼴 — TLS 로 감싼 HTTP 포워드 프록시, Basic(realm ``proxy``),
                               407 뒤에 연결을 이어 쓴다. ``*.lab.test``·``labhost`` 만 잇고 나머지는 거절하고 적는다
시험 프록시(평문)       18080   위와 같되 평문 — 대조군
목적지(http)            8080   탐침 쪽들(``/probe/…``)과 서비스 워커(``/sw/…``)
목적지(https)           8443   같은 쪽. 인증서는 ``make_certs.py`` 가 만든 ``*.lab.test``
웹소켓                  8081   첫 글을 되돌려 준다
DNS                     53/udp ``*.lab.test``·``labhost`` 는 ``10.0.2.2`` 로 답하고 **나머지는 NXDOMAIN** — 모든 질의를
                               적는다. 에뮬레이터를 ``-dns-server 127.0.0.1`` 로 띄워야 여기로 온다
STUN                    3478–3482/udp  Binding 에 답하고 적는다(WebRTC 가 프록시를 비켜 UDP 로 나가는지)
====================  ======  ===============================================================

에뮬레이터의 다른 이름 질의를 막는 것(NXDOMAIN)은 실험을 닫힌 방에서 하려는 것이다 — 에뮬레이터가 PC 의 인터넷(사용자의
테더링일 수 있다)을 쓰지 않게 하고, 누가 어떤 이름을 찾았는지 남김없이 적는다.

모든 사건은 ``local/lab/events.jsonl`` 에 한 줄씩 남는다. ``run_lab.py`` 는 이 모듈을 불러 같은 프로세스에서 세운다.
혼자 돌리려면: ``python tools/lab/labserver.py`` (Ctrl+C 로 끝).
"""

from __future__ import annotations

import base64
import hashlib
import itertools
import json
import pathlib
import select
import shutil
import socket
import ssl
import struct
import subprocess
import sys
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import labmedia  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
LAB = ROOT / "local" / "lab"

EMULATOR_HOST = "10.0.2.2"
HTTP_PORT, HTTPS_PORT, WS_PORT = 8080, 8443, 8081
PROXY_TLS_PORT, PROXY_PLAIN_PORT = 18443, 18080
DNS_PORT = 53
STUN_PORTS = tuple(range(3478, 3493))  # WebRTC 탐침의 길마다 하나
DEST_PORTS = {HTTP_PORT, HTTPS_PORT, WS_PORT}
SIMPLE_NAMES = {"labhost"}
DNS_TTL = 5

QTYPES = {1: "A", 28: "AAAA", 5: "CNAME", 12: "PTR", 15: "MX", 16: "TXT", 33: "SRV", 64: "SVCB", 65: "HTTPS"}

_ids = itertools.count(1)


def is_lab_name(host: str) -> bool:
    host = host.lower().rstrip(".")
    return host == "lab.test" or host.endswith(".lab.test") or host in SIMPLE_NAMES


def proxy_may_reach(host: str, port: int) -> bool:
    return (is_lab_name(host) or host == EMULATOR_HOST) and port in DEST_PORTS


class Events:
    """사건 기록 — 메모리(실험이 읽는다)와 JSONL(사람이 읽는다)."""

    def __init__(self, path: pathlib.Path) -> None:
        self._lock = threading.Lock()
        self._items: list[dict] = []
        path.parent.mkdir(parents=True, exist_ok=True)
        self._file = open(path, "a", encoding="utf-8")

    def add(self, kind: str, **fields) -> None:
        event = {"t": time.time(), "kind": kind, **fields}
        with self._lock:
            self._items.append(event)
            self._file.write(json.dumps(event, ensure_ascii=False) + "\n")
            self._file.flush()

    def between(self, t0: float, t1: float | None = None) -> list[dict]:
        with self._lock:
            return [e for e in self._items if e["t"] >= t0 and (t1 is None or e["t"] <= t1)]


# ── 바이트 다루기 ─────────────────────────────────────────────────────────────────────────────────────────────────────


class Conn:
    """소켓 하나와 아직 쓰지 않은 바이트. 머리를 읽고 남은 것을 다음 요청·터널에 넘기려고 버퍼를 직접 든다."""

    MAX_HEAD = 64 * 1024

    def __init__(self, sock: socket.socket) -> None:
        self.sock = sock
        self.buf = b""

    def read_head(self) -> tuple[str, list[tuple[str, str]]] | None:
        while b"\r\n\r\n" not in self.buf:
            if len(self.buf) > self.MAX_HEAD:
                raise ValueError("head too large")
            chunk = self.sock.recv(65536)
            if not chunk:
                return None
            self.buf += chunk
        head, _, self.buf = self.buf.partition(b"\r\n\r\n")
        lines = head.decode("iso-8859-1").split("\r\n")
        headers = []
        for line in lines[1:]:
            name, sep, value = line.partition(":")
            if sep:
                headers.append((name.strip(), value.strip()))
        return lines[0], headers

    def read_exact(self, n: int) -> bytes:
        while len(self.buf) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                break
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out


def header(headers: list[tuple[str, str]], name: str) -> str | None:
    for n, v in headers:
        if n.lower() == name.lower():
            return v
    return None


def pump(a: socket.socket, b: socket.socket, idle: float = 60.0) -> None:
    """두 소켓을 한 스레드에서 잇는다 — TLS 소켓을 두 스레드가 동시에 읽고 쓰지 않게(OpenSSL 의 SSL 객체는 그것을 보장하지 않는다)."""
    peer = {a: b, b: a}
    socks = [a, b]
    for s in socks:
        s.settimeout(idle)
    while True:
        ready = [s for s in socks if isinstance(s, ssl.SSLSocket) and s.pending()]
        if not ready:
            ready, _, _ = select.select(socks, [], [], idle)
            if not ready:
                return
        for s in ready:
            try:
                data = s.recv(65536)
            except (ssl.SSLWantReadError, ssl.SSLWantWriteError, BlockingIOError):
                continue
            except OSError:
                return
            if not data:
                return
            try:
                peer[s].sendall(data)
            except OSError:
                return


def close_quietly(*socks) -> None:
    for s in socks:
        try:
            s.close()
        except OSError:
            pass


def split_host_port(authority: str, default: int) -> tuple[str, int]:
    if authority.startswith("["):
        host, _, rest = authority[1:].partition("]")
        port = int(rest[1:]) if rest.startswith(":") else default
        return host, port
    host, sep, port = authority.rpartition(":")
    if not sep:
        return authority, default
    return host, int(port)


# ── 시험 프록시 ──────────────────────────────────────────────────────────────────────────────────────────────────────


class LabProxy:
    """Squid 를 닮은 시험 프록시. 사용자의 Squid 설정(README)과 같게: Basic realm ``proxy``, 407 은 몸을 달고 연결을 이어 쓴다."""

    HOP = {"proxy-authorization", "proxy-connection", "connection", "keep-alive"}

    def __init__(self, lab: "LabServer", name: str, port: int, tls: ssl.SSLContext | None) -> None:
        self.lab, self.name, self.port, self.tls = lab, name, port, tls
        creds = f"{lab.user}:{lab.password}".encode("utf-8")
        self.expected = "Basic " + base64.b64encode(creds).decode("ascii")

    def start(self) -> None:
        self.server = socket.create_server(("127.0.0.1", self.port), backlog=64)
        threading.Thread(target=self._accept, name=f"proxy-{self.name}", daemon=True).start()

    def _accept(self) -> None:
        while True:
            try:
                sock, _ = self.server.accept()
            except OSError:
                return
            threading.Thread(target=self._serve, args=(sock,), daemon=True).start()

    def _event(self, ev: str, **fields) -> None:
        self.lab.events.add("proxy", proxy=self.name, ev=ev, **fields)

    def _serve(self, sock: socket.socket) -> None:
        cid = next(_ids)
        try:
            sock.settimeout(30)
            if self.tls is not None:
                try:
                    sock = self.tls.wrap_socket(sock, server_side=True)
                except (ssl.SSLError, OSError) as e:
                    self._event("tls-fail", conn=cid, error=str(e)[:200])
                    return
            c = Conn(sock)
            while True:
                got = c.read_head()
                if got is None:
                    return
                start, headers = got
                parts = start.split(" ")
                if len(parts) != 3:
                    sock.sendall(b"HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    return
                method, target, version = parts
                auth = header(headers, "Proxy-Authorization")
                if auth != self.expected:
                    self._event("auth-missing" if auth is None else "auth-wrong", conn=cid, method=method, target=target)
                    length = int(header(headers, "Content-Length") or 0)
                    if length:
                        c.read_exact(length)
                    body = b"<html><body>407 lab proxy</body></html>"
                    sock.sendall(
                        b"HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"proxy\"\r\n"
                        b"Content-Type: text/html\r\nContent-Length: " + str(len(body)).encode() + b"\r\nConnection: keep-alive\r\n\r\n" + body
                    )
                    continue
                if method == "CONNECT":
                    self._connect(c, cid, target)
                elif target.startswith("http://"):
                    self._forward(c, cid, method, target, version, headers)
                else:
                    sock.sendall(b"HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                return
        except (OSError, ValueError):
            return
        finally:
            close_quietly(sock)

    def _refuse(self, c: Conn, cid: int, method: str, target: str) -> None:
        # 실험실 밖으로는 잇지 않는다 — WebView 가 무엇을 프록시로 찾는지(예: 구글 서비스)는 이 기록으로 본다.
        self._event("refused", conn=cid, method=method, target=target)
        c.sock.sendall(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

    def _connect(self, c: Conn, cid: int, target: str) -> None:
        host, port = split_host_port(target, 443)
        if not proxy_may_reach(host, port):
            self._refuse(c, cid, "CONNECT", target)
            return
        try:
            up = socket.create_connection(("127.0.0.1", port), timeout=10)
        except OSError:
            self._event("dest-unreachable", conn=cid, target=target)
            c.sock.sendall(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            return
        self._event("connect", conn=cid, target=target)
        try:
            c.sock.sendall(b"HTTP/1.1 200 Connection established\r\n\r\n")
            if c.buf:
                up.sendall(c.buf)
                c.buf = b""
            pump(c.sock, up)
        finally:
            close_quietly(up)

    def _forward(self, c: Conn, cid: int, method: str, target: str, version: str, headers: list[tuple[str, str]]) -> None:
        u = urllib.parse.urlsplit(target)
        host, port = u.hostname or "", u.port or 80
        if not proxy_may_reach(host, port):
            self._refuse(c, cid, method, target)
            return
        path = (u.path or "/") + (f"?{u.query}" if u.query else "")
        out = [(n, v) for n, v in headers if n.lower() not in self.HOP]
        out += [("X-Lab-Via", self.name), ("Connection", "close")]
        body = c.read_exact(int(header(headers, "Content-Length") or 0))
        try:
            up = socket.create_connection(("127.0.0.1", port), timeout=10)
        except OSError:
            self._event("dest-unreachable", conn=cid, target=target)
            c.sock.sendall(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            return
        self._event("forward", conn=cid, method=method, target=target)
        try:
            request = f"{method} {path} {version}\r\n" + "".join(f"{n}: {v}\r\n" for n, v in out) + "\r\n"
            up.sendall(request.encode("iso-8859-1") + body)
            while True:
                data = up.recv(65536)
                if not data:
                    return
                c.sock.sendall(data)
        finally:
            close_quietly(up)


# ── 목적지 ───────────────────────────────────────────────────────────────────────────────────────────────────────────

PIXEL_GIF = base64.b64decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")

# WebRTC 시도 — 쪽 안의 틀마다 다른 STUN 포트로 보내, STUN 기록만 보고 어느 틀이 새는지 가른다.
RTC_ATTEMPT_JS = r"""
function attempt(where, w, port) {
  var report = function (m) { console.log('IROLAB rtc ' + where + ' ' + m); };
  var PC;
  try { PC = w.RTCPeerConnection || w.webkitRTCPeerConnection; } catch (e) { report('access-denied'); return; }
  if (typeof PC !== 'function') { report('unavailable'); return; }
  try {
    var pc = new PC({ iceServers: [{ urls: 'stun:10.0.2.2:' + port }] });
    pc.createDataChannel('x');
    pc.onicecandidate = function (e) {
      if (e.candidate) report('candidate ' + e.candidate.candidate); else report('gathering-complete');
    };
    pc.createOffer().then(function (o) { return pc.setLocalDescription(o); })
      .then(function () { report('offer-set'); }, function (err) { report('error ' + err); });
  } catch (e) { report('throw ' + e); }
}
"""


def dash_manifest(name: str) -> str:
    """DASH 목록 — 영상 화질 둘(번호 틀)과 소리 하나(시간 줄). live 는 실시간(dynamic), drm 은 ContentProtection. 조각은 vod/ 아래."""
    kind = "dynamic" if name == "live" else "static"
    protection = '<ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/>' if name == "drm" else ""
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="{kind}" mediaPresentationDuration="PT12S" minBufferTime="PT2S">\n'
        "  <BaseURL>vod/</BaseURL>\n"
        "  <Period>\n"
        f'    <AdaptationSet mimeType="video/mp4" contentType="video">{protection}\n'
        '      <SegmentTemplate timescale="1000" duration="4000" startNumber="1" initialization="$RepresentationID$/init.mp4"'
        ' media="$RepresentationID$/seg$Number%03d$.m4s"/>\n'
        '      <Representation id="v1" bandwidth="800000" width="640" height="360" codecs="avc1.4d401e"/>\n'
        '      <Representation id="v2" bandwidth="2500000" width="1280" height="720" codecs="avc1.4d401f"/>\n'
        "    </AdaptationSet>\n"
        f'    <AdaptationSet mimeType="audio/mp4" lang="ko">{protection}\n'
        '      <Representation id="a" bandwidth="128000" codecs="mp4a.40.2">\n'
        '        <SegmentTemplate timescale="48000" initialization="a/init.mp4" media="a/$Time$.m4s">\n'
        '          <SegmentTimeline><S t="0" d="192000" r="2"/></SegmentTimeline>\n'
        "        </SegmentTemplate>\n"
        "      </Representation>\n"
        "    </AdaptationSet>\n"
        "  </Period>\n"
        "</MPD>\n"
    )


HLS_MASTER = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
v1.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
v2.m3u8
"""


def hls_media(name: str) -> str:
    """조각 셋의 미디어 재생목록 — live 는 끝(#EXT-X-ENDLIST)이 없고, enc 는 AES-128 로 잠겼고(조각은 실제로 잠그지 않았다 — 받지 않는지만
    본다), fmp4 는 초기 조각이 있다."""
    lines = ["#EXTM3U", "#EXT-X-VERSION:7" if name == "fmp4" else "#EXT-X-VERSION:3", "#EXT-X-TARGETDURATION:4", "#EXT-X-MEDIA-SEQUENCE:0"]
    if name == "enc":
        lines.append('#EXT-X-KEY:METHOD=AES-128,URI="enc.key"')
    if name == "fmp4":
        lines.append('#EXT-X-MAP:URI="fmp4/init.mp4"')
    ext = "m4s" if name == "fmp4" else "ts"
    for i in range(3):
        lines += ["#EXTINF:4.0,", f"{name}/seg{i}.{ext}"]
    if name != "live":
        lines.append("#EXT-X-ENDLIST")
    return "\n".join(lines) + "\n"


def hls_bytes(path: str) -> bytes:
    """조각의 바이트 — 길에 따라 정해지는 무늬. TS 는 188 바이트 묶음 100개(18,800), fMP4 는 초기 1,000·조각 5,000.
    잠긴 재생목록(enc)의 조각은 이 무늬를 AES-128-CBC 로 실제로 잠근 것이다(:func:`hls_encrypted`)."""
    if path.endswith("init.mp4"):
        return b"LABINIT!" * 125
    if path.endswith(".m4s"):
        return b"LABFRAG!" * 625
    seed = sum(path.encode("ascii")) & 0xFF
    packet = bytes([0x47]) + bytes((seed + k) & 0xFF for k in range(187))
    return packet * 100


HLS_KEY = bytes(range(16))
_encrypted: dict[str, bytes] = {}


def hls_encrypted(path: str, sequence: int) -> bytes:
    """enc 재생목록의 조각 — 무늬를 열쇠 00..0F, IV = 미디어 순번(16 바이트 빅 엔디언 — 재생목록에 IV 가 없을 때의 HLS 규칙)으로
    잠근다(PKCS#7 채움). 표준 라이브러리에 AES 가 없어 openssl(make_certs.py 와 같은 것)로."""
    if path not in _encrypted:
        exe = shutil.which("openssl")
        if exe is None:
            raise RuntimeError("openssl 이 없다 — 잠긴 HLS 조각을 만들지 못한다")
        done = subprocess.run([exe, "enc", "-aes-128-cbc", "-K", HLS_KEY.hex(), "-iv", sequence.to_bytes(16, "big").hex(), "-nosalt"],
                              input=hls_bytes(path), capture_output=True, check=True)
        _encrypted[path] = done.stdout
    return _encrypted[path]


BLOB_SIZE = 300_000
FLAKY_SIZE = 300_000
FLAKY_ETAG = '"flaky-v1"'


def blob_bytes() -> bytes:
    """blob 시험의 내용 — 쪽의 스크립트가 같은 식으로 만든다."""
    return bytes((i * 7 + 3) & 0xFF for i in range(BLOB_SIZE))


def flaky_bytes() -> bytes:
    return bytes((i * 13 + 5) & 0xFF for i in range(FLAKY_SIZE))


def report_js() -> str:
    return "function report(k, v) { return fetch('/probe/report?t=TOK&k=' + k + '&v=' + encodeURIComponent(String(v))).catch(function () {}); }"


UACH_JS = report_js() + """
(async function () {
  var d = navigator.userAgentData;
  var r = { ua: navigator.userAgent, brands: d ? d.brands.map(function (b) { return b.brand + '/' + b.version; }) : null,
            mobile: d ? d.mobile : null, platform: d ? d.platform : null };
  if (d) {
    try {
      var h = await d.getHighEntropyValues(['model', 'platformVersion', 'fullVersionList']);
      r.model = h.model; r.platformVersion = h.platformVersion;
      r.full = (h.fullVersionList || []).map(function (b) { return b.brand + '/' + b.version; });
    } catch (e) { r.hev = String(e); }
  }
  report('uach', JSON.stringify(r));
  // 전용 워커의 navigator.userAgent — 쪽의 UA 를 물려받는가.
  try {
    var w = new Worker(URL.createObjectURL(new Blob(['postMessage(navigator.userAgent)'], { type: 'text/javascript' })));
    w.onmessage = function (e) { report('worker-ua', e.data); };
  } catch (e) { report('worker-ua', 'error ' + e); }
})();
"""

EME_JS = report_js() + """
(async function () {
  try {
    var access = await navigator.requestMediaKeySystemAccess('com.widevine.alpha', [{ initDataTypes: ['cenc'],
      videoCapabilities: [{ contentType: 'video/mp4; codecs="avc1.42E01E"' }] }]);
    await report('eme-access', access.keySystem);
    var keys = await access.createMediaKeys();
    await report('eme-keys', 'ok');
    var session = keys.createSession();
    session.addEventListener('message', function (e) { report('eme-message', e.message.byteLength); });
    var pssh = new Uint8Array([0, 0, 0, 50, 112, 115, 115, 104, 0, 0, 0, 0, 237, 239, 139, 169, 121, 214, 74, 206, 163, 200, 39, 220,
      213, 29, 33, 237, 0, 0, 0, 18, 18, 16, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]);
    await session.generateRequest('cenc', pssh);
    await report('eme-request', 'ok');
  } catch (e) { report('eme-error', e.name + ':' + e.message); }
})();
"""

BLOB_JS = """
setTimeout(function () {
  var n = SIZE, b = new Uint8Array(n);
  for (var i = 0; i < n; i++) b[i] = (i * 7 + 3) & 255;
  var u = URL.createObjectURL(new Blob([b], { type: 'application/octet-stream' }));
  var a = document.createElement('a');
  a.href = u;
  a.download = 'blob-MODE-TOK.bin';
  if (MODE_IS_DETACHED) {
    a.dispatchEvent(new MouseEvent('click'));
    if ('MODE' === 'detachedrevoke') URL.revokeObjectURL(u); else setTimeout(function () { URL.revokeObjectURL(u); }, 40000);
    return;
  }
  document.body.appendChild(a);
  a.click();
  a.remove();
  if ('MODE' === 'csp') setTimeout(function () { URL.revokeObjectURL(u); }, 40000); else URL.revokeObjectURL(u);
}, 800);
""".replace("MODE_IS_DETACHED", "'MODE'.indexOf('detached') === 0")

MAKER_JS = """
function report(k, v) { return fetch('/probe/report?k=' + k + '&v=' + encodeURIComponent(String(v))).catch(function () {}); }
async function make() {
  var c = document.createElement('canvas'); c.width = 320; c.height = 240; document.body.appendChild(c);
  var g = c.getContext('2d'), f = 0;
  var timer = setInterval(function () {
    g.fillStyle = 'hsl(' + ((f * 7) % 360) + ',70%,45%)'; g.fillRect(0, 0, 320, 240);
    g.fillStyle = '#fff'; g.font = '64px sans-serif'; g.fillText(String(f), 20, 140); g.fillRect((f * 5) % 320, 190, 40, 30); f++;
  }, 33);
  var ac = new AudioContext(); await ac.resume();
  var osc = ac.createOscillator(); osc.frequency.value = 440;
  var lfo = ac.createOscillator(); lfo.frequency.value = 2; var gain = ac.createGain(); gain.gain.value = 0.5;
  lfo.connect(gain.gain); osc.connect(gain);
  var dest = ac.createMediaStreamDestination(); gain.connect(dest); osc.start(); lfo.start();
  var v = c.captureStream(30).getVideoTracks()[0], a = dest.stream.getAudioTracks()[0];
  function rec(tracks, type, ms) {
    return new Promise(function (ok, bad) {
      var r = new MediaRecorder(new MediaStream(tracks), { mimeType: type, videoBitsPerSecond: 600000, audioBitsPerSecond: 96000,
        videoKeyFrameIntervalDuration: 1000 });
      var parts = [];
      r.ondataavailable = function (e) { if (e.data.size) parts.push(e.data); };
      r.onstop = function () { ok(new Blob(parts, { type: type })); };
      r.onerror = function (e) { bad(e); };
      r.start(1000);
      setTimeout(function () { r.stop(); }, ms);
    });
  }
  var jobs = [['v.mp4', [v], 'video/mp4;codecs=avc1.42E01E'], ['a.mp4', [a], 'audio/mp4;codecs=mp4a.40.2'],
              ['av.mp4', [v, a], 'video/mp4;codecs=avc1.42E01E,mp4a.40.2'], ['v.webm', [v], 'video/webm;codecs=vp9'],
              ['a.webm', [a], 'audio/webm;codecs=opus']];
  var results = await Promise.all(jobs.map(function (j) {
    return MediaRecorder.isTypeSupported(j[2]) ? rec(j[1], j[2], 6500).then(function (b) { return [j[0], b]; }) : Promise.resolve([j[0], null]);
  }));
  clearInterval(timer);
  for (var i = 0; i < results.length; i++) {
    var n = results[i][0], b = results[i][1];
    if (b) await fetch('/lab/upload?name=' + n, { method: 'POST', body: b });
    await report('made', n + ':' + (b ? b.size : -1));
  }
  document.title = 'maker-done';
  await report('maker-done', 1);
}
document.addEventListener('click', function () { make().catch(function (e) { report('maker-error', e); }); }, { once: true });
"""

MSE_JS = report_js() + """
var v = document.getElementById('v');
var ms = new MediaSource();
v.src = URL.createObjectURL(ms);
ms.addEventListener('sourceopen', async function () {
  var vsb = ms.addSourceBuffer('video/mp4; codecs="avc1.42E01E"'), asb = ms.addSourceBuffer('audio/mp4; codecs="mp4a.40.2"');
  async function feed(sb, base, n) {
    var urls = [base + '/init.mp4'];
    for (var i = 1; i <= n; i++) urls.push(base + '/seg' + i + '.m4s');
    for (var k = 0; k < urls.length; k++) {
      var b = await (await fetch('/real/' + urls[k] + '?t=TOK')).arrayBuffer();
      await new Promise(function (ok) { sb.addEventListener('updateend', ok, { once: true }); sb.appendBuffer(b); });
    }
  }
  try {
    await Promise.all([feed(vsb, 'v', NV), feed(asb, 'a', NA)]);
    ms.endOfStream();
    report('mse-ready', v.duration);
  } catch (e) { report('mse-error', e); }
});
document.addEventListener('click', function () { v.play().then(function () { report('mse-play', 1); }, function (e) { report('mse-play-error', e); }); });
v.addEventListener('ended', function () { report('mse-ended', v.currentTime); });
"""

CLEARKEY_JS = report_js() + """
(async function () {
  try {
    var access = await navigator.requestMediaKeySystemAccess('org.w3.clearkey', [{ initDataTypes: ['keyids'],
      videoCapabilities: [{ contentType: 'video/mp4; codecs="avc1.42E01E"' }] }]);
    var keys = await access.createMediaKeys();
    await document.getElementById('v').setMediaKeys(keys);
    report('clearkey', 'attached');
  } catch (e) { report('clearkey-error', e.name + ':' + e.message); }
})();
"""

PLAY_JS = report_js() + """
var v = document.getElementById('v');
document.addEventListener('click', function () { v.play().then(function () { report('play', 1); }, function (e) { report('play-error', e); }); });
v.addEventListener('ended', function () { report('play-ended', v.currentTime); });
"""


LAB_FILTER_LIST = """[Adblock Plus 2.0]
! Title: Iroiro lab test list
! Expires: 1 days
||ads.lab.test^
@@||ads.lab.test^*/allowed.js
/adbanner.
##.ad-slot
lab.test##.lab-sponsored
||csp.lab.test^$csp=script-src 'none'
"""


def page(title: str, body: str = "", head: str = "") -> bytes:
    return (
        f"<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width'>"
        f"<title>{title}</title>{head}</head><body>{body}</body></html>"
    ).encode("utf-8")


class Destination(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, lab: "LabServer", port: int, tls: ssl.SSLContext | None) -> None:
        self.lab, self.scheme = lab, ("https" if tls else "http")
        super().__init__(("127.0.0.1", port), DestinationHandler)
        if tls is not None:
            self.socket = tls.wrap_socket(self.socket, server_side=True, do_handshake_on_connect=False)

    def handle_error(self, request, client_address) -> None:
        pass  # 손님이 끊은 것 — 할 일이 없다(TLS 실패는 setup 이 적는다).


class DestinationHandler(BaseHTTPRequestHandler):
    server: Destination
    server_version = "lab"
    sys_version = ""

    def setup(self) -> None:
        if isinstance(self.request, ssl.SSLSocket):
            self.request.settimeout(10)
            try:
                self.request.do_handshake()
            except (ssl.SSLError, OSError) as e:
                self.server.lab.events.add("http", scheme="https", port=self.server.server_address[1], ev="tls-fail", error=str(e)[:200])
                raise
        super().setup()

    def log_message(self, format, *args) -> None:
        pass

    def do_GET(self) -> None:
        self._route()

    def do_HEAD(self) -> None:
        self._route()

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        keep = self.path.startswith("/lab/upload")
        got = 0
        parts = []
        while got < length:
            chunk = self.rfile.read(min(65536, length - got))
            if not chunk:
                break
            got += len(chunk)
            if keep and got <= 64 * 1024 * 1024:
                parts.append(chunk)
        self.body_len = got
        self.body = b"".join(parts)
        self._route()

    body_len = 0
    body = b""

    def _route(self) -> None:
        u = urllib.parse.urlsplit(self.path)
        q = urllib.parse.parse_qs(u.query)
        t = q.get("t", [""])[0]
        port = self.server.server_address[1]
        scheme = self.server.scheme
        self.server.lab.events.add(
            "http",
            scheme=scheme,
            port=port,
            ev="request",
            method=self.command,
            path=self.path,
            host=self.headers.get("Host"),
            via=self.headers.get("X-Lab-Via"),
            headers=self._headers_for_log(),
            body_len=self.body_len,
        )
        extra: list[tuple[str, str]] = []
        status, ctype, body = 200, "text/html; charset=utf-8", b""
        p = u.path
        if p == "/probe/basic" or p == "/probe/warm":
            body = page("basic-ok", "<p>basic</p><script>console.log('IROLAB loaded ' + location.href)</script>")
        elif p == "/probe/pixel":
            ctype, body = "image/gif", PIXEL_GIF
        elif p == "/probe/dns":
            body, extra = self._dns_page(t, scheme, port)
        elif p == "/probe/ws":
            body = page(
                "ws-probe",
                "<script>"
                f"var ws = new WebSocket('ws://w{t}.lab.test:{WS_PORT}/echo');"
                "ws.onopen = function () { console.log('IROLAB ws open'); ws.send('hi'); };"
                "ws.onmessage = function (e) { console.log('IROLAB ws echo ' + e.data); ws.close(); };"
                "ws.onerror = function () { console.log('IROLAB ws error'); };"
                "ws.onclose = function (e) { console.log('IROLAB ws close ' + e.code); };"
                "</script>",
            )
        elif p == "/probe/headers":
            main = json.dumps(self._headers_for_log())
            body = page(
                "headers-probe",
                "<script>"
                f"console.log('IROLAB headers main ' + JSON.stringify({main}));"
                "fetch('/probe/echo-headers').then(function (r) { return r.json(); })"
                ".then(function (j) { console.log('IROLAB headers sub ' + JSON.stringify(j)); },"
                " function (e) { console.log('IROLAB headers sub-failed ' + e); });"
                # 쪽이 스스로 다는 X-Requested-With(jQuery 의 AJAX 표지) — 사용자 지정 머리가 이것까지 덮는지
                "fetch('/probe/echo-headers?k=ajax', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })"
                ".then(function (r) { return r.json(); })"
                ".then(function (j) { console.log('IROLAB headers ajax ' + JSON.stringify(j)); },"
                " function (e) { console.log('IROLAB headers ajax-failed ' + e); });"
                "</script>",
            )
        elif p == "/probe/upload":
            n = int(q.get("n", ["4194304"])[0])
            body = page(
                "upload-probe",
                "<script>"
                f"fetch('/probe/sink?t={t}', {{ method: 'POST', body: new Uint8Array({n}) }})"
                ".then(function (r) { return r.text(); })"
                ".then(function (x) { console.log('IROLAB upload ' + x); }, function (e) { console.log('IROLAB upload failed ' + e); });"
                "</script>",
            )
        elif p == "/probe/sink":
            ctype = "text/plain"
            body = f"received={self.body_len}".encode("ascii")
        elif p == "/probe/echo-headers":
            ctype = "application/json"
            body = json.dumps(self._headers_for_log()).encode("utf-8")
        elif p == "/sw/reg.html":
            body = page(
                "sw-reg",
                "<script>"
                "navigator.serviceWorker.register('/sw/sw.js', { scope: '/sw/' })"
                ".then(function () { return navigator.serviceWorker.ready; })"
                ".then(function () { console.log('IROLAB sw registered'); document.title = 'sw-registered'; },"
                " function (e) { console.log('IROLAB sw register-failed ' + e); });"
                "</script>",
            )
        elif p == "/sw/sw.js":
            ctype = "text/javascript"
            body = SW_JS.encode("utf-8")
        elif p == "/sw/page.html":
            body = page("sw-page-direct", "<script>console.log('IROLAB sw page-direct')</script>")
        elif p == "/sw-net/page.html":
            body = page(
                "sw-page-ok",
                "<script>console.log('IROLAB sw page-ok ' + (navigator.serviceWorker.controller ? 'controlled' : 'uncontrolled'))</script>",
            )
        elif p == "/probe/webrtc2":
            body = self._webrtc2_page(t)
        elif p == "/probe/rtcframe":
            # 다른 출처의 틀 — 자기 문서에서 스스로 시도한다(문서 시작 스크립트가 그 출처에도 들어가는가).
            name = "".join(ch for ch in q.get("name", ["xorigin"])[0] if ch.isalnum() or ch == "-")
            rtc_port = int(q.get("port", ["3492"])[0])
            body = page("rtcframe", "<script>" + RTC_ATTEMPT_JS + f"attempt('{name}', window, {rtc_port});" + "</script>")
        elif p == "/probe/auth401":
            # 프록시와 같은 이름의 사이트가 같은 realm 으로 401 을 낸다 — WebView 의 인증 콜백(host, realm)으로는 프록시의 407 과 가릴 수 없다.
            if self.headers.get("Authorization") is None:
                status, body = 401, page("auth-required")
                extra = [("WWW-Authenticate", 'Basic realm="proxy"')]
            else:
                body = page("auth-received")
        elif p == "/probe/big":
            n = min(int(q.get("n", ["1000000"])[0]), 64 * 1024 * 1024)
            self._send_big(n)
            return
        elif p == "/probe/speed":
            n = int(q.get("n", ["16000000"])[0])
            body = page(
                "speed-probe",
                "<script>"
                "(async function () {"
                "  for (var i = 0; i < 3; i++) {"
                "    var t0 = performance.now();"
                f"    var r = await fetch('/probe/big?n={n}&i=' + i + '&t={t}', {{ cache: 'no-store' }});"
                "    var b = await r.arrayBuffer();"
                "    var ms = performance.now() - t0;"
                "    console.log('IROLAB speed ' + i + ' bytes=' + b.byteLength + ' ms=' + Math.round(ms) +"
                "      ' MBps=' + (b.byteLength / 1048576 / (ms / 1000)).toFixed(1));"
                "  }"
                "  console.log('IROLAB speed done');"
                "})().catch(function (e) { console.log('IROLAB speed failed ' + e); });"
                "</script>",
            )
        elif p == "/probe/cookie":
            # 쿠키를 심고 내려받기 고리를 둔다 — 브라우저의 내려받기가 그 쿠키와 Referer 를 싣는지 본다.
            extra = [("Set-Cookie", f"labc={t}; Path=/")]
            body = page(
                "cookie-probe",
                f"<p><a id='dl' href='/probe/attach?t={t}'>파일 받기</a></p>"
                f"<p><a id='dl2' href='/probe/attach-redirect?t={t}'>넘겨주기를 거쳐 받기</a></p>",
            )
        elif p == "/probe/attach-redirect":
            status = 302
            extra = [("Location", f"/probe/attach?t={t}&via=redirect"), ("Set-Cookie", f"labr={t}; Path=/")]
            body = b""
        elif p == "/probe/attach":
            # 한국어 이름(filename*)의 첨부 — 크기는 n 바이트.
            n = min(int(q.get("n", ["300000"])[0]), 16 * 1024 * 1024)
            name = urllib.parse.quote(f"실험 보고서 {t}.bin")
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Disposition", f"attachment; filename=\"lab.bin\"; filename*=UTF-8''{name}")
            self.send_header("Content-Length", str(n))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if self.command != "HEAD":
                left = n
                while left > 0:
                    chunk = self.BIG_BLOCK[: min(left, len(self.BIG_BLOCK))]
                    self.wfile.write(chunk)
                    left -= len(chunk)
            return
        elif p == "/cdn-cgi/trace":
            # 출구 IP 확인 쪽의 꼴(Cloudflare trace 처럼 키=값 줄). 주소는 문서용 대역(RFC 5737)이다 — 화면의 확인을 시험한다.
            ctype = "text/plain"
            body = f"fl=lab\nh={self.headers.get('Host')}\nip=203.0.113.7\nloc=JP\nvia={self.headers.get('X-Lab-Via')}\n".encode()
        elif p == "/hls/master.m3u8":
            ctype = "application/vnd.apple.mpegurl"
            body = HLS_MASTER.encode("ascii")
        elif p in ("/hls/v1.m3u8", "/hls/v2.m3u8", "/hls/live.m3u8", "/hls/enc.m3u8", "/hls/fmp4.m3u8"):
            ctype = "application/vnd.apple.mpegurl"
            body = hls_media(p[len("/hls/"):-len(".m3u8")]).encode("ascii")
        elif p == "/hls/enc.key":
            ctype = "application/octet-stream"
            body = bytes(range(16))
        elif p.startswith("/hls/flaky/seg"):
            # 둘째 조각은 처음 세 번 503 — 조각마다 세 번 해 보고 멈춘다(이어받기를 누르면 넷째에 받는다).
            key = (t, p)
            with self.server.lab.lock:
                n = self.server.lab.flaky[key] = self.server.lab.flaky.get(key, 0) + 1
            if p.startswith("/hls/flaky/seg1") and n <= 3:
                status, ctype, body = 503, "text/plain", b"busy"
            else:
                ctype, body = "video/mp2t", hls_bytes(p)
        elif p.startswith("/hls/enc/") and p.endswith(".ts"):
            ctype = "video/mp2t"
            body = hls_encrypted(p, int(p.rsplit("seg", 1)[1].split(".")[0]))
        elif p.startswith("/hls/") and p.endswith((".ts", ".m4s", ".mp4")):
            ctype = "video/mp4" if p.endswith((".m4s", ".mp4")) else "video/mp2t"
            body = hls_bytes(p)
        elif p in ("/dash/vod.mpd", "/dash/live.mpd", "/dash/drm.mpd"):
            ctype = "application/dash+xml"
            body = dash_manifest(p[len("/dash/"):-len(".mpd")]).encode("utf-8")
        elif p.startswith("/dash/vod/") and p.endswith((".mp4", ".m4s")):
            ctype = "video/mp4"
            if p.endswith("init.mp4"):
                body = b"LABINIT!" * 125
            elif p.startswith("/dash/vod/a/"):
                body = b"LABAUDI!" * 250
            else:
                body = b"LABFRAG!" * 625
        elif p == "/media/clip.mp4":
            ctype, body = "video/mp4", b"LABVIDEO" * 6250
        elif p == "/media/song.mp3":
            ctype, body = "audio/mpeg", b"LABAUDIO" * 2500
        elif p == "/probe/media":
            # 찾은 미디어 시험 — 영상·소리 요소와, 쪽의 스크립트가 받는 HLS 재생목록 넷(마스터·실시간·잠긴 것·fMP4).
            body = page(
                "media-probe",
                f"<video src='/media/clip.mp4?t={t}' preload='auto'></video><audio src='/media/song.mp3?t={t}' preload='auto'></audio>"
                "<script>['/hls/master.m3u8','/hls/live.m3u8','/hls/enc.m3u8','/hls/fmp4.m3u8','/hls/flaky.m3u8','/dash/vod.mpd',"
                "'/dash/live.mpd','/dash/drm.mpd']"
                f".forEach(function (u) {{ fetch(u + '?t={t}'); }});</script>",
            )
        elif p == "/lists/test.txt":
            # 광고 차단 시험 목록 — 호스트·무늬·예외·일반 숨김·사이트 숨김, 그리고 쓰지 못하는 규칙 하나($csp). ETag 로 304 를 준다.
            ctype = "text/plain; charset=utf-8"
            extra = [("ETag", '"lab-list-1"')]
            if self.headers.get("If-None-Match") == '"lab-list-1"':
                status = 304
            else:
                body = LAB_FILTER_LIST.encode("utf-8")
        elif p == "/probe/ads":
            body = page(
                "ads-probe",
                "<div class='ad-slot'>AD-SLOT</div><div class='lab-sponsored'>SPONSORED</div><div class='content'>CONTENT</div>"
                f"<script src='http://ads.lab.test:8080/ad.js?t={t}'></script>"
                f"<script src='http://ads.lab.test:8080/allowed.js?t={t}'></script>"
                f"<img src='http://img.lab.test:8080/adbanner.gif?t={t}'><img src='http://img.lab.test:8080/ok.gif?t={t}'>",
            )
        elif p == "/probe/find":
            # 쪽에서 찾기 — 'needle' 이 세 번.
            body = page("find-probe", "<p>first needle</p><p>second needle</p><p>third needle</p><p>haystack</p>")
        elif p == "/probe/links":
            # 길게 누르기 시험 — 화면 위쪽 절반을 덮는 고리 하나와 그 아래 그림 고리.
            body = page(
                "links-probe",
                f"<a id='big' href='/probe/basic?t={t}&from=links' style='display:block;height:45vh;font-size:40px;background:#cde'>"
                "새 탭 시험 고리</a>"
                f"<a href='/probe/basic?t={t}&from=img'><img src='/probe/pixel?t={t}' style='display:block;width:100%;height:30vh'></a>",
            )
        elif p == "/x":
            body = page("anchor-target")
        elif p == "/probe/report":
            # 쪽이 알리는 결과 — 주소(k·v)와 머리가 사건으로 남는다(위의 기록).
            status, ctype, body = 204, "text/plain", b""
        elif p == "/probe/uach":
            # 높은 엔트로피의 힌트(모델 등)를 청한다 — 다음 요청(보고)부터 실린다.
            extra = [("Accept-CH", "Sec-CH-UA-Model, Sec-CH-UA-Platform-Version, Sec-CH-UA-Full-Version-List")]
            body = page("uach-probe", "<script>" + UACH_JS.replace("TOK", t) + "</script>")
        elif p == "/probe/eme":
            body = page("eme-probe", "<script>" + EME_JS.replace("TOK", t) + "</script>")
        elif p == "/probe/blob":
            mode = "".join(ch for ch in q.get("mode", ["attached"])[0] if ch.isalnum())
            if mode == "csp":
                extra = [("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'")]
            body = page(f"blob-{mode}", "<p>blob</p><script>" + BLOB_JS.replace("MODE", mode).replace("TOK", t)
                        .replace("SIZE", str(BLOB_SIZE)) + "</script>")
        elif p == "/probe/flaky":
            self._flaky(t, q.get("mode", ["auto"])[0])
            return
        elif p == "/probe/slow":
            self._slow(t, min(int(q.get("n", ["8000000"])[0]), 64 * 1024 * 1024), int(q.get("rate", ["1000000"])[0]))
            return
        elif p == "/probe/clearkey":
            body = page("clearkey-probe", "<video id=v playsinline style='width:100%;height:40vh;background:#000'></video><script>"
                        + CLEARKEY_JS.replace("TOK", t) + "</script>")
        elif p == "/hls/flaky.m3u8":
            ctype = "application/vnd.apple.mpegurl"
            body = ("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n"
                    + "".join(f"#EXTINF:4.0,\nflaky/seg{i}.ts?t={t}\n" for i in range(3)) + "#EXT-X-ENDLIST\n").encode("ascii")
        elif p == "/lab/maker":
            body = page("maker", "<p>tap to make</p><script>" + MAKER_JS + "</script>")
        elif p == "/lab/upload":
            name = "".join(ch for ch in q.get("name", [""])[0] if ch.isalnum() or ch in "._-")
            if name and self.body:
                labmedia.MEDIA.mkdir(parents=True, exist_ok=True)
                (labmedia.MEDIA / name).write_bytes(self.body)
            ctype, body = "text/plain", f"saved {name} {len(self.body)}".encode()
        elif p.startswith("/real/"):
            status, ctype, body = self._real(p[len("/real/"):])
        elif p == "/probe/realmedia":
            # 실제 미디어의 목록 셋을 쪽의 스크립트가 받는다 — 찾은 미디어에 뜬다.
            body = page("realmedia-probe", "<script>['/real/dash.mpd','/real/fmp4.m3u8','/real/ts.m3u8','/real/dash-webm.mpd']"
                        f".forEach(function (u) {{ fetch(u + '?t={t}'); }});</script>")
        elif p == "/probe/mse":
            vs = len(labmedia.parts("v.mp4")[1]) if labmedia.have("v.mp4") else 0
            as_ = len(labmedia.parts("a.mp4")[1]) if labmedia.have("a.mp4") else 0
            body = page("mse-probe", "<video id=v playsinline style='width:100%;height:55vh;background:#000'></video><script>"
                        + MSE_JS.replace("TOK", t).replace("NV", str(vs)).replace("NA", str(as_)) + "</script>")
        elif p == "/probe/play":
            body = page("play-probe", f"<video id=v src='/real/av.mp4?t={t}' playsinline loop preload=auto "
                        "style='width:100%;height:55vh;background:#000'></video><script>" + PLAY_JS.replace("TOK", t) + "</script>")
        else:
            status, body = 404, page("not-found")
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for n, v in extra:
            self.send_header(n, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    BIG_BLOCK = bytes(range(256)) * 4096  # 1 MiB

    def _flaky(self, t: str, mode: str) -> None:
        """이어받기 시험 — 300,000 바이트(무늬), ETag·Range 를 안다. auto: 첫 요청은 100,000 바이트에서 끊는다. pause: 첫 요청은 끊고,
        둘째·셋째는 503(나아감 없는 실패 둘 — 앱은 세 번째 실패에서 멈춘다), 넷째부터 제대로 준다."""
        n_total = FLAKY_SIZE
        key = (t, "flaky", mode)
        # 주소창으로 연 WebView 의 탐색(Accept: text/html — 머리를 보고 내려받기로 넘긴다)은 세지 않고 그대로 준다. 앱의 받기만 센다.
        navigation = "text/html" in (self.headers.get("Accept") or "")
        with self.server.lab.lock:
            n = 0 if navigation else self.server.lab.flaky.setdefault(key, 0) + 1
            if not navigation:
                self.server.lab.flaky[key] = n
        if mode == "pause" and n in (2, 3):
            self.send_response(503)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        start = 0
        rng = self.headers.get("Range")
        if_range = self.headers.get("If-Range")
        if rng and rng.startswith("bytes=") and (if_range is None or if_range == FLAKY_ETAG):
            start = int(rng[len("bytes="):].split("-")[0])
        data = flaky_bytes()[start:]
        self.send_response(206 if start else 200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Disposition", f'attachment; filename="flaky-{mode}-{t}.bin"')
        self.send_header("ETag", FLAKY_ETAG)
        self.send_header("Accept-Ranges", "bytes")
        if start:
            self.send_header("Content-Range", f"bytes {start}-{n_total - 1}/{n_total}")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if self.command == "HEAD":
            return
        if n == 1:
            # 첫 요청은 앞부분만 주고 끊는다.
            self.wfile.write(data[:100_000])
            self.wfile.flush()
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            return
        self.wfile.write(data)

    def _slow(self, t: str, n: int, rate: int) -> None:
        """느린 첨부(초당 [rate] 바이트) — Range·If-Range 를 안다. 받는 도중 앱을 죽였다 다시 띄워 이어받는 시험."""
        etag = '"slow-v1"'
        start = 0
        rng = self.headers.get("Range")
        if rng and rng.startswith("bytes=") and self.headers.get("If-Range") in (None, etag):
            start = int(rng[len("bytes="):].split("-")[0])
        self.send_response(206 if start else 200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Disposition", f'attachment; filename="slow-{t}.bin"')
        self.send_header("ETag", etag)
        self.send_header("Accept-Ranges", "bytes")
        if start:
            self.send_header("Content-Range", f"bytes {start}-{n - 1}/{n}")
        self.send_header("Content-Length", str(n - start))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if self.command == "HEAD":
            return
        pos = start
        chunk = max(1, rate // 10)
        try:
            while pos < n:
                k = min(chunk, n - pos)
                self.wfile.write(bytes((i * 11 + 1) & 0xFF for i in range(pos, pos + k)))
                pos += k
                time.sleep(0.1)
        except OSError:
            pass

    def _real(self, rest: str) -> tuple[int, str, bytes]:
        """기기가 만든 시험 미디어(local/lab/media) — DASH·HLS(fMP4·TS+묶음 AAC)의 목록과 조각, 그리고 파일 그대로."""
        try:
            if rest == "dash.mpd":
                return 200, "application/dash+xml", labmedia.dash_mpd().encode()
            if rest == "dash-webm.mpd":
                return 200, "application/dash+xml", labmedia.dash_webm_mpd().encode()
            if rest in ("fmp4.m3u8", "ts.m3u8"):
                return 200, "application/vnd.apple.mpegurl", labmedia.hls_master(rest.split(".")[0]).encode()
            if rest in ("fmp4-v.m3u8", "fmp4-a.m3u8", "ts-v.m3u8", "ts-a.m3u8"):
                kind, track = rest.split(".")[0].split("-")
                return 200, "application/vnd.apple.mpegurl", labmedia.hls_media(kind, track).encode()
            folder, _, name = rest.partition("/")
            if folder in ("v", "a"):
                init, segs, _ = labmedia.parts("v.mp4" if folder == "v" else "a.mp4")
                if name == "init.mp4":
                    return 200, "video/mp4", init
                k = int(name[len("seg"):].split(".")[0]) - 1
                return 200, "video/mp4", segs[k]
            if folder == "tsv":
                k = int(name[len("seg"):].split(".")[0]) - 1
                return 200, "video/mp2t", labmedia.to_ts_segments()[k]
            if folder == "tsa":
                k = int(name[len("seg"):].split(".")[0]) - 1
                return 200, "audio/aac", labmedia.packed_aac_segments()[k]
            f = labmedia.MEDIA / "".join(ch for ch in rest if ch.isalnum() or ch in "._-")
            if f.is_file():
                return 200, "video/webm" if f.suffix == ".webm" else "video/mp4", f.read_bytes()
        except (FileNotFoundError, IndexError, ValueError, StopIteration) as e:
            return 404, "text/plain", f"no media: {e}".encode()
        return 404, "text/plain", b"no media"

    def _send_big(self, n: int) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(n))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        left = n
        while left > 0:
            chunk = self.BIG_BLOCK[: min(left, len(self.BIG_BLOCK))]
            self.wfile.write(chunk)
            left -= len(chunk)

    def _headers_for_log(self) -> dict:
        """받은 머리 — 계정은 값을 지우고, 실험실 프록시의 계정과 같은지만 남긴다(사이트로 새면 판정이 그것을 본다)."""
        lab = self.server.lab
        expected = "Basic " + base64.b64encode(f"{lab.user}:{lab.password}".encode("utf-8")).decode("ascii")
        out = {}
        for k, v in self.headers.items():
            if k.lower() in ("authorization", "proxy-authorization"):
                v = "<redacted: lab proxy credentials>" if v == expected else "<redacted>"
            out[k] = v
        return out

    @staticmethod
    def _dns_page(t: str, scheme: str, port: int) -> tuple[bytes, list[tuple[str, str]]]:
        # a: <link rel=dns-prefetch>, b: preconnect, c: Link 머리의 dns-prefetch, d: <a> (x-dns-prefetch-control on),
        # e: 스크립트가 rel 을 적어 넣은 dns-prefetch, f: 실제로 불러오는 그림(프록시를 타야 한다 — 대조군),
        # g: speculation rules 의 prefetch, h: <link rel=prefetch>, j: innerHTML 로 만든 dns-prefetch(스크립트 가드가 닿지 않는 길).
        n = lambda k: f"dnsleak-{t}-{k}.lab.test"  # noqa: E731
        rules = json.dumps({"prefetch": [{"source": "list", "urls": [f"{scheme}://{n('g')}:{port}/x"]}]})
        head = (
            "<meta http-equiv='x-dns-prefetch-control' content='on'>"
            f"<link rel='dns-prefetch' href='//{n('a')}'>"
            f"<link rel='preconnect' href='{scheme}://{n('b')}:{port}'>"
            f"<link rel='prefetch' href='{scheme}://{n('h')}:{port}/x'>"
            f"<script type='speculationrules'>{rules}</script>"
        )
        body = (
            f"<a href='{scheme}://{n('d')}:{port}/x'>anchor</a>"
            f"<img src='{scheme}://{n('f')}:{port}/probe/pixel' width=1 height=1>"
            "<script>"
            f"var l = document.createElement('link'); l.rel = 'dns-prefetch'; l.href = '//{n('e')}'; document.head.appendChild(l);"
            f"var holder = document.createElement('div'); holder.innerHTML = \"<link rel='dns-prefetch' href='//{n('j')}'>\";"
            "document.head.appendChild(holder.firstChild);"
            "console.log('IROLAB dns page ready');"
            "</script>"
        )
        return page("dns-probe", body, head), [("Link", f"<//{n('c')}>; rel=dns-prefetch")]

    @staticmethod
    def _webrtc2_page(t: str) -> bytes:
        """가드를 비켜 가는 길 열다섯 — 길마다 STUN 포트가 다르다(``RTC2_VECTORS``). 각 길은 RTC 가 남은 창을 손에 넣으려 한다."""

        def js_string(value: str) -> str:
            return json.dumps(value).replace("</", "<\\/")

        sandboxed = "<script>" + RTC_ATTEMPT_JS + "attempt('sandboxed', window, 3488);</script>"
        data_doc = "<script>" + RTC_ATTEMPT_JS + "attempt('data', window, 3489);</script>"
        helpers = (
            "function once(f) { var done = false; return function () { if (done) return; done = true;"
            " try { f.apply(this, arguments); } catch (e) { console.log('IROLAB rtc once-error ' + e); } }; }"
            "function vec(name, fn) { try { fn(); } catch (e) { console.log('IROLAB rtc ' + name + ' error ' + e); } }"
            "function tail() { var d = document.createElement('div'); document.body.appendChild(d); return d; }"
        )
        rest = (
            "vec('main', function () { attempt('main', window, 3478); });"
            # 스크립트가 만든 빈 틀 — contentWindow / frames[i] / 동기 onload 안의 frames[i]
            "vec('blank-cw', function () { var f = document.createElement('iframe'); document.body.appendChild(f);"
            " attempt('blank-cw', f.contentWindow, 3479); });"
            "vec('blank-frames', function () { var i = frames.length; document.body.appendChild(document.createElement('iframe'));"
            " attempt('blank-frames', frames[i], 3480); });"
            "vec('onload-frames', function () { var f = document.createElement('iframe'); var i = frames.length;"
            " f.onload = once(function () { attempt('onload-frames', frames[i], 3481); }); document.body.appendChild(f); });"
            # 문서에 붙은 요소의 innerHTML 로 만든 틀, 그리고 그 틀의 onload 속성
            "vec('innerhtml', function () { var h = tail(); var i = frames.length; h.innerHTML = '<iframe></iframe>';"
            " attempt('innerhtml', frames[i], 3482); });"
            "vec('inline-onload', function () { var h = tail(); var i = frames.length;"
            " window.__inl = once(function () { attempt('inline-onload', frames[i], 3483); });"
            " h.innerHTML = '<iframe onload=\"window.__inl()\"></iframe>'; });"
            # 자식 창의 손때 묻지 않은 appendChild 로 손자 틀을 만든다
            "vec('nested', function () { var f = document.createElement('iframe'); document.body.appendChild(f);"
            " var c = f.contentWindow; var g = c.document.createElement('iframe'); c.document.body.appendChild(g);"
            " attempt('nested', c.frames[0], 3486); });"
            "vec('srcdoc', function () { var f = document.createElement('iframe');"
            " f.srcdoc = \"<script>parent.attempt('srcdoc', window, 3487);<\\/script>\"; document.body.appendChild(f); });"
            "vec('sandboxed', function () { var f = document.createElement('iframe'); f.setAttribute('sandbox', 'allow-scripts');"
            " f.srcdoc = " + js_string(sandboxed) + "; document.body.appendChild(f); });"
            "vec('data', function () { var f = document.createElement('iframe');"
            " f.src = 'data:text/html;charset=utf-8,' + encodeURIComponent(" + js_string(data_doc) + "); document.body.appendChild(f); });"
            "vec('cdoc-dv', function () { var f = document.createElement('iframe'); document.body.appendChild(f);"
            " attempt('cdoc-dv', f.contentDocument.defaultView, 3490); });"
            "vec('explicit-blank', function () { var f = document.createElement('iframe'); f.src = 'about:blank'; var i = frames.length;"
            " f.onload = once(function () { attempt('explicit-blank', frames[i], 3491); }); document.body.appendChild(f); });"
            "vec('xorigin', function () { var f = document.createElement('iframe');"
            f" f.src = 'http://xo{t}.lab.test:8080/probe/rtcframe?name=xorigin&port=3492'; document.body.appendChild(f); }});"
            "console.log('IROLAB rtc page ready');"
        )
        body = (
            # 파서가 넣은 틀(맨 앞 — frames[0]) 과 document.write 로 넣은 틀(frames[1])
            "<iframe></iframe>"
            "<script>" + RTC_ATTEMPT_JS + helpers + "vec('parser', function () { attempt('parser', frames[0], 3484); });</script>"
            "<script>document.write('<iframe></iframe>');"
            "vec('docwrite', function () { attempt('docwrite', frames[frames.length - 1], 3485); });</script>"
            "<script>" + rest + "</script>"
        )
        return page("webrtc2-probe", body)



SW_JS = r"""
self.addEventListener('install', function () { self.skipWaiting(); });
self.addEventListener('activate', function (e) { e.waitUntil(self.clients.claim()); });
self.addEventListener('fetch', function (e) {
  var u = new URL(e.request.url);
  if (u.pathname !== '/sw/page.html') return;
  // 서비스 워커가 직접 네트워크로 간다 — 이 요청에는 WebView 가 붙어 있지 않다(프록시의 407 에 누가 답하는가).
  var net = u.origin + '/sw-net/page.html' + u.search;
  e.respondWith(fetch(net, { cache: 'no-store' }).catch(function (err) {
    var msg = String(err).replace(/[^A-Za-z0-9 :._-]/g, '');
    return new Response('<!doctype html><title>sw-fetch-failed</title><script>console.log("IROLAB sw fetch-failed ' + msg + '")<\/script>',
      { headers: { 'Content-Type': 'text/html' } });
  }));
});
"""


# ── 웹소켓 ───────────────────────────────────────────────────────────────────────────────────────────────────────────

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


class LabWebSocket:
    def __init__(self, lab: "LabServer") -> None:
        self.lab = lab

    def start(self) -> None:
        self.server = socket.create_server(("127.0.0.1", WS_PORT), backlog=32)
        threading.Thread(target=self._accept, name="ws", daemon=True).start()

    def _accept(self) -> None:
        while True:
            try:
                sock, _ = self.server.accept()
            except OSError:
                return
            threading.Thread(target=self._serve, args=(sock,), daemon=True).start()

    def _serve(self, sock: socket.socket) -> None:
        try:
            sock.settimeout(15)
            c = Conn(sock)
            got = c.read_head()
            if got is None:
                return
            start, headers = got
            host = header(headers, "Host")
            key = header(headers, "Sec-WebSocket-Key")
            self.lab.events.add("ws", ev="open", host=host, start=start, origin=header(headers, "Origin"))
            if key is None:
                sock.sendall(b"HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n")
                return
            accept = base64.b64encode(hashlib.sha1((key + WS_GUID).encode("ascii")).digest()).decode("ascii")
            sock.sendall(
                (
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
                ).encode("ascii")
            )
            b1, b2 = c.read_exact(2)
            n = b2 & 0x7F
            if n == 126:
                n = struct.unpack(">H", c.read_exact(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", c.read_exact(8))[0]
            mask = c.read_exact(4) if b2 & 0x80 else b"\0\0\0\0"
            payload = bytes(x ^ mask[i % 4] for i, x in enumerate(c.read_exact(n)))
            self.lab.events.add("ws", ev="message", host=host, data=payload.decode("utf-8", "replace"))
            if (b1 & 0x0F) == 1 and len(payload) < 126:
                sock.sendall(bytes([0x81, len(payload)]) + payload)
            c.read_exact(2)  # 닫기 틀을 기다린다(내용은 보지 않는다)
        except (OSError, ValueError):
            return
        finally:
            close_quietly(sock)


# ── DNS ─────────────────────────────────────────────────────────────────────────────────────────────────────────────


class LabDns:
    def __init__(self, lab: "LabServer") -> None:
        self.lab = lab

    def start(self) -> None:
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("127.0.0.1", DNS_PORT))
        threading.Thread(target=self._loop, name="dns", daemon=True).start()

    def _loop(self) -> None:
        while True:
            try:
                data, addr = self.sock.recvfrom(4096)
            except OSError:
                # 윈도에서 앞서 보낸 답이 닿지 못하면 다음 recvfrom 이 WSAECONNRESET 을 낸다 — 넘긴다.
                continue
            try:
                reply = self._answer(data)
            except (IndexError, struct.error, UnicodeDecodeError):
                continue
            try:
                self.sock.sendto(reply, addr)
            except OSError:
                pass

    def _answer(self, data: bytes) -> bytes:
        tid, flags = struct.unpack(">HH", data[:4])
        i, labels = 12, []
        while data[i] != 0:
            n = data[i]
            labels.append(data[i + 1 : i + 1 + n].decode("ascii"))
            i += 1 + n
        i += 1
        qtype, _ = struct.unpack(">HH", data[i : i + 4])
        question = data[12 : i + 4]
        name = ".".join(labels).lower()
        lab = is_lab_name(name)
        self.lab.events.add("dns", name=name, qtype=QTYPES.get(qtype, str(qtype)), lab=lab)
        answers = [EMULATOR_HOST] if lab and qtype == 1 else []
        rcode = 0 if lab else 3
        reply_flags = 0x8000 | 0x0400 | (flags & 0x0100) | 0x0080 | rcode
        out = struct.pack(">HHHHHH", tid, reply_flags, 1, len(answers), 0, 0) + question
        for ip in answers:
            out += b"\xc0\x0c" + struct.pack(">HHIH", 1, 1, DNS_TTL, 4) + socket.inet_aton(ip)
        return out


# ── STUN ────────────────────────────────────────────────────────────────────────────────────────────────────────────


class LabStun:
    COOKIE = 0x2112A442

    def __init__(self, lab: "LabServer") -> None:
        self.lab = lab

    def start(self) -> None:
        for port in STUN_PORTS:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.bind(("127.0.0.1", port))
            threading.Thread(target=self._loop, args=(s, port), name=f"stun-{port}", daemon=True).start()

    def _loop(self, s: socket.socket, port: int) -> None:
        while True:
            try:
                data, addr = s.recvfrom(2048)
            except OSError:
                continue
            kind = struct.unpack(">H", data[:2])[0] if len(data) >= 2 else -1
            self.lab.events.add("stun", port=port, src=f"{addr[0]}:{addr[1]}", type=kind, size=len(data))
            if len(data) < 20 or kind != 0x0001 or struct.unpack(">I", data[4:8])[0] != self.COOKIE:
                continue
            txid = data[8:20]
            ip = struct.unpack(">I", socket.inet_aton(addr[0]))[0]
            attr = struct.pack(">HHBBHI", 0x0020, 8, 0, 1, addr[1] ^ (self.COOKIE >> 16), ip ^ self.COOKIE)
            try:
                s.sendto(struct.pack(">HHI", 0x0101, len(attr), self.COOKIE) + txid + attr, addr)
            except OSError:
                pass


# ── 묶음 ────────────────────────────────────────────────────────────────────────────────────────────────────────────


class LabServer:
    def __init__(self) -> None:
        creds = json.loads((LAB / "credentials.json").read_text(encoding="utf-8"))
        self.user, self.password = creds["user"], creds["password"]
        self.events = Events(LAB / "events.jsonl")
        self.tls = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
        self.tls.load_cert_chain(str(LAB / "server.pem"), str(LAB / "server.key"))
        # 실패를 일부러 내는 길(이어받기 시험)의 요청 수.
        self.lock = threading.Lock()
        self.flaky: dict = {}

    def start(self) -> "LabServer":
        LabDns(self).start()
        LabStun(self).start()
        LabProxy(self, "tls", PROXY_TLS_PORT, self.tls).start()
        LabProxy(self, "plain", PROXY_PLAIN_PORT, None).start()
        LabWebSocket(self).start()
        for port, tls in ((HTTP_PORT, None), (HTTPS_PORT, self.tls)):
            server = Destination(self, port, tls)
            threading.Thread(target=server.serve_forever, name=f"dest-{port}", daemon=True).start()
        self.events.add("lab", ev="started")
        return self


def main() -> None:
    LabServer().start()
    print("실험실 서버가 섰다 — DNS 53, STUN 3478-3482, 프록시 18443(TLS)·18080, 목적지 8080·8443, 웹소켓 8081. Ctrl+C 로 끝.")
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
