"""Web 실험판의 실험을 에뮬레이터에서 돌리고 판정해 적는다. 표준 라이브러리만 쓴다.

준비(한 번):

    python tools/lab/make_certs.py                     # 실험용 인증서·시험 계정·디버그 NSC
    ./gradlew :app:assembleDebug                       # nsc_lab 이 걸린 디버그 APK
    env -u HTTP_PROXY -u HTTPS_PROXY -u NO_PROXY -u http_proxy -u https_proxy -u no_proxy \\
        emulator -avd Android_12_Phone -no-snapshot-load -dns-server 127.0.0.1

``-dns-server 127.0.0.1`` 은 에뮬레이터의 DNS 를 이 실험실(127.0.0.1:53)로 받는다. **프록시 환경변수를 지우고 띄운다** — 에뮬레이터는
``http_proxy`` 가 있으면 손님의 TCP 를 전부 그 프록시로 보낸다(이 PC 에는 폰 테더링의 프록시가 걸려 있고, 그 프록시는 사설 주소를
거절한다 — ``10.0.2.2`` 에 닿지 못해 모든 실험이 ``ERR_PROXY_CONNECTION_FAILED`` 로 끝났다). DNS 는 UDP 라 그래도 온다.

돌리기:

    python tools/lab/run_lab.py                # 전부
    python tools/lab/run_lab.py auth dns       # 묶음만 — 묶음 이름이나 실험 이름(아래 plan)

**에뮬레이터에만 깐다** — ``adb devices`` 에서 ``Android_12_Phone`` 에뮬레이터를 골라 확인하고, 실기기에는 손대지 않는다.
실험 하나는 앱 프로세스 하나다(``am start -S``). 앱은 본 것을 logcat ``IROLAB`` 에, 서버는 받은 것을 사건으로 적는다.
둘을 시각으로 맞춰 판정한다.

결과: ``local/lab/results-<시각>.md``(사람이 읽는다)와 ``.json``(원본).
"""

from __future__ import annotations

import argparse
import datetime
import json
import pathlib
import re
import secrets
import shlex
import shutil
import subprocess
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import labserver  # noqa: E402

ROOT = labserver.ROOT
LAB = labserver.LAB
PKG = "io.github.donggi.iroirobrowser"
ACTIVITY = f"{PKG}/.lab.LabActivity"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
AVD = "Android_12_Phone"
PROXY_HOST = "proxy.lab.test"
CLOSED_PORT = 18999  # 아무것도 듣지 않는 포트 — 프록시가 죽은 경우

PASS, LEAK, FAIL, INFO, UNKNOWN = "통과", "누출", "실패", "정보", "확인 불가"

# ── adb ─────────────────────────────────────────────────────────────────────────────────────────────────────────────


def find_adb() -> str:
    exe = shutil.which("adb")
    if exe:
        return exe
    props = (ROOT / "local.properties").read_text(encoding="utf-8")
    m = re.search(r"^sdk\.dir=(.*)$", props, re.M)
    if m:
        sdk = m.group(1).strip().replace("\\:", ":").replace("\\\\", "\\")
        cand = pathlib.Path(sdk) / "platform-tools" / "adb.exe"
        if cand.exists():
            return str(cand)
    sys.exit("adb 를 찾지 못했다.")


ADB = find_adb()
SERIAL = ""


class AdbError(RuntimeError):
    """adb 가 실패했거나 답하지 않았다. **명령의 인자를 싣지 않는다** — 실험의 extras 에 시험 계정이 있다."""


def adb(*args: str, timeout: float = 60, check: bool = True) -> str:
    cmd = [ADB, *(["-s", SERIAL] if SERIAL else []), *args]
    what = " ".join(args[:2])
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    except subprocess.TimeoutExpired:
        raise AdbError(f"adb {what}: {timeout:.0f}초 안에 답하지 않았다") from None
    if check and r.returncode != 0:
        raise AdbError(f"adb {what} 실패(코드 {r.returncode})")
    return r.stdout


def wait_for_emulator(limit: float = 240) -> bool:
    """에뮬레이터의 시스템이 다시 섰는지(부팅 끝) 기다린다 — 시스템 서버가 감시견에 죽었다 살아나는 일이 있었다."""
    deadline = time.time() + limit
    while time.time() < deadline:
        try:
            if adb("shell", "getprop", "sys.boot_completed", timeout=15, check=False).strip() == "1":
                time.sleep(5)
                return True
        except AdbError:
            pass
        time.sleep(3)
    return False


def pick_emulator() -> str:
    """``Android_12_Phone`` 에뮬레이터만 고른다. 실기기는 목록에 있어도 쓰지 않는다."""
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    serials = [line.split()[0] for line in out.splitlines()[1:] if line.strip().endswith("\tdevice") or line.strip().endswith(" device")]
    for s in serials:
        if not s.startswith("emulator-"):
            continue
        qemu = subprocess.run([ADB, "-s", s, "shell", "getprop", "ro.kernel.qemu"], capture_output=True, text=True).stdout.strip()
        qemu2 = subprocess.run([ADB, "-s", s, "shell", "getprop", "ro.boot.qemu"], capture_output=True, text=True).stdout.strip()
        name = subprocess.run([ADB, "-s", s, "emu", "avd", "name"], capture_output=True, text=True).stdout.split()
        if "1" in (qemu, qemu2) and name and name[0] == AVD:
            return s
    sys.exit(f"{AVD} 에뮬레이터가 떠 있지 않다 — 모듈 머리말의 명령으로 띄운다(-dns-server 127.0.0.1).")


# ── 실험 정의 ─────────────────────────────────────────────────────────────────────────────────────────────────────────


class Exp:
    def __init__(self, group: str, name: str, title: str, extras: dict, check, *, before: str | None = None) -> None:
        self.group, self.name, self.title, self.check, self.before = group, name, title, check, before
        self.tok = secrets.token_hex(3)
        self.run = f"{name}-{self.tok}"
        self.extras = {k: (v.format(t=self.tok) if isinstance(v, str) else v) for k, v in extras.items()}

    @property
    def timeout(self) -> float:
        return (int(self.extras.get("prewait", 0) if self.extras.get("pre") else 0) + int(self.extras.get("wait", 6000))) / 1000 + 30


class Observed:
    def __init__(self, exp: Exp, lines: list[str], events: list[dict], finished: bool) -> None:
        self.exp, self.tok, self.lines, self.events, self.finished = exp, exp.tok, lines, events, finished

    def app(self, prefix: str) -> list[str]:
        return [line for line in self.lines if line.startswith(prefix)]

    def has(self, line: str) -> bool:
        return line in self.lines

    def http(self, needle: str) -> list[dict]:
        return [e for e in self.events if e["kind"] == "http" and e.get("ev") == "request" and needle in (e.get("path") or "")]

    def http_host(self, needle: str) -> list[dict]:
        return [e for e in self.events if e["kind"] == "http" and e.get("ev") == "request" and needle in (e.get("host") or "")]

    def tls_fail(self) -> list[dict]:
        return [e for e in self.events if e["kind"] == "http" and e.get("ev") == "tls-fail"]

    def proxy(self, ev: str | None = None, target: str = "") -> list[dict]:
        return [e for e in self.events if e["kind"] == "proxy" and (ev is None or e["ev"] == ev) and target in e.get("target", "")]

    def dns(self, needle: str) -> list[dict]:
        return [e for e in self.events if e["kind"] == "dns" and needle in e["name"]]

    def stun(self) -> list[dict]:
        return [e for e in self.events if e["kind"] == "stun"]

    def ws(self, needle: str) -> list[dict]:
        return [e for e in self.events if e["kind"] == "ws" and needle in (e.get("host") or "")]

    def errors(self) -> list[str]:
        return self.app("error ") + self.app("httperror ") + self.app("sslerror ") + self.app("renderer ")

    def title(self) -> str:
        finals = self.app("final ")
        return finals[-1] if finals else "(제목 없음)"


def proxied_by(o: Observed, scheme: str, host_needle: str, via: str) -> bool:
    """요청이 그 프록시를 거쳤는가. http 는 프록시가 단 X-Lab-Via 로, https 는 프록시의 CONNECT 기록으로 본다."""
    if scheme == "http":
        return any(h.get("via") == via for h in o.http_host(host_needle))
    return any(e["proxy"] == via for e in o.proxy("connect", host_needle))


def auth_summary(o: Observed) -> list[str]:
    out = [f"앱: {line}" for line in o.app("auth ")]
    for proxy in ("tls", "plain"):
        missing = sum(1 for e in o.proxy("auth-missing") if e["proxy"] == proxy)
        wrong = sum(1 for e in o.proxy("auth-wrong") if e["proxy"] == proxy)
        if missing or wrong:
            out.append(f"프록시({proxy}) 407: 계정 없음 {missing}회, 틀림 {wrong}회")
    return out


def check_load(scheme: str, via: str, host_prefix: str):
    def check(o: Observed):
        host = f"{host_prefix}{o.tok}.lab.test"
        hits = o.http(f"t={o.tok}")
        obs = auth_summary(o) + [f"앱: {e}" for e in o.errors()] + [o.title()]
        obs += [f"앱: {line}" for line in o.app("relay ")][:8]
        if not hits:
            return FAIL, ["목적지에 닿지 않았다"] + obs
        if proxied_by(o, scheme, host, via):
            dns = o.dns(host)
            if dns:
                return LEAK, [f"프록시를 거쳤지만 목적지 이름을 기기가 찾았다({len(dns)}회)"] + obs
            return PASS, [f"프록시({via})를 거쳐 목적지에 닿았다"] + obs
        return LEAK, ["목적지에 닿았는데 프록시 기록이 없다 — 직접 연결"] + obs

    return check


def check_cancelled(o: Observed):
    hits = o.http(f"t={o.tok}")
    obs = auth_summary(o) + [f"앱: {e}" for e in o.errors()] + [o.title()]
    if hits:
        direct = [h for h in hits if h.get("via") is None]
        return (LEAK if direct else FAIL), ["계정을 거절했는데 목적지에 닿았다"] + obs
    return PASS, ["계정을 거절하자 목적지에 닿지 않았다(직접 연결로 넘어가지 않았다)"] + obs


def check_relayhttp(o: Observed):
    noauth = o.app("relayhttp noauth ")
    auth = o.app("relayhttp auth ")
    ok_auth = [line for line in auth if "status=200" in line]
    leaked = [line for line in noauth if "status=200" in line]
    obs = [f"앱: {line}" for line in o.app("relayhttp ")]
    obs.append(f"목적지 도착 {len(o.http(f't={o.tok}'))}건(인증한 두 번만 와야 한다)")
    if leaked:
        return LEAK, ["계정 없이 중계를 지났다"] + obs
    if len(ok_auth) == 2 and len(o.http(f"t={o.tok}")) == 2:
        return PASS, ["계정 없이는 막히고, Authenticator 로 중계 계정을 주면 http·https 모두 지난다"] + obs
    return FAIL, ["HttpURLConnection 이 중계로 나가지 못했다"] + obs


def check_failclosed(dest_name: str | None):
    def check(o: Observed):
        hits = o.http(f"t={o.tok}")
        tls = o.tls_fail()
        dns = o.dns(dest_name.format(t=o.tok)) if dest_name else []
        obs = [f"앱: {e}" for e in o.errors()] + [f"앱: {line}" for line in o.app("relay ")][:6] + [o.title()]
        if hits or tls:
            return LEAK, [f"프록시가 죽었는데 목적지에 닿았다(요청 {len(hits)}, TLS 시도 {len(tls)})"] + obs
        if dns:
            return LEAK, [f"목적지에 닿지는 않았지만 그 이름을 기기가 찾았다({len(dns)}회)"] + obs
        return PASS, ["목적지에 닿지 않았다 — 직접 연결로 넘어가지 않는다"] + obs

    return check


def check_bypass(expect_direct: bool, host_needle: str):
    def check(o: Observed):
        hits = o.http(f"t={o.tok}")
        needle = host_needle.format(t=o.tok)
        dns = o.dns(needle) if not re.fullmatch(r"[\d.]+", needle) else []
        obs = [f"목적지 요청 {len(hits)}건, X-Lab-Via={[h.get('via') for h in hits]}", f"기기의 DNS 질의 {len(dns)}건"]
        obs += [f"앱: {e}" for e in o.errors()]
        if not hits:
            return FAIL, ["목적지에 닿지 않았다"] + obs
        direct = all(h.get("via") is None for h in hits)
        if expect_direct:
            return (PASS, ["예외 규칙대로 직접 갔다"] + obs) if direct else (FAIL, ["예외 규칙이 듣지 않았다 — 프록시로 갔다"] + obs)
        if direct:
            return LEAK, ["예외가 아닌데 직접 갔다"] + obs
        if dns:
            return LEAK, ["프록시로 갔지만 이름을 기기가 찾았다"] + obs
        return PASS, ["예외가 아닌 이름은 프록시로 갔고 기기는 그 이름을 찾지 않았다"] + obs

    return check


DNS_PROBES = {
    "a": "<link rel=dns-prefetch>",
    "b": "<link rel=preconnect>",
    "c": "Link 머리의 dns-prefetch",
    "d": "<a href> (x-dns-prefetch-control on)",
    "e": "스크립트가 rel 을 적어 넣은 dns-prefetch",
    "f": "<img> (실제 요청 — 대조군)",
    "g": "speculation rules prefetch",
    "h": "<link rel=prefetch>",
    "j": "innerHTML 로 만든 dns-prefetch",
}


def check_dns(proxied: bool):
    def check(o: Observed):
        leaked = [k for k in DNS_PROBES if o.dns(f"dnsleak-{o.tok}-{k}.lab.test")]
        page_leak = bool(o.dns(f"d{o.tok}.lab.test"))
        loaded = bool(o.http(f"/probe/dns?t={o.tok}"))
        pixel = bool(o.http_host(f"dnsleak-{o.tok}-f"))
        obs = [f"쪽 도착={loaded}, 그림 요청 도착={pixel}, 쪽 이름 질의={page_leak}"]
        obs += [f"기기가 찾은 이름: {k} {DNS_PROBES[k]}" for k in leaked] or ["기기가 찾은 탐침 이름 없음"]
        obs += [f"앱: {e}" for e in o.errors()]
        if not loaded:
            return UNKNOWN, ["탐침 쪽이 열리지 않았다"] + obs
        if not proxied:
            return INFO, [f"직접 연결(대조군): 탐침 {len(leaked)}/{len(DNS_PROBES)} 을 기기가 찾았다 — DNS 기록이 살아 있다는 확인"] + obs
        if leaked or page_leak:
            return LEAK, ["프록시를 걸었는데 기기가 이름을 찾았다 — 통신사 DNS 로 나간다"] + obs
        return PASS, ["프록시를 걸면 기기는 어떤 이름도 찾지 않는다"] + obs

    return check


RTC2_VECTORS = {
    3478: "main", 3479: "blank-cw", 3480: "blank-frames", 3481: "onload-frames", 3482: "innerhtml", 3483: "inline-onload",
    3484: "parser", 3485: "docwrite", 3486: "nested", 3487: "srcdoc", 3488: "sandboxed", 3489: "data", 3490: "cdoc-dv",
    3491: "explicit-blank", 3492: "xorigin",
}


def check_webrtc(guarded: bool, frames: dict[int, str]):
    def check(o: Observed):
        stun_frames = sorted({frames.get(e["port"], str(e["port"])) for e in o.stun()})
        obs = [f"STUN 이 받은 길: {', '.join(stun_frames) or '없음'}"]
        obs += [f"앱: {line}" for line in o.app("inject ")]
        for where in frames.values():
            lines = [line[len(f"page rtc {where} "):] for line in o.app(f"page rtc {where} ")]
            kinds = sorted({_rtc_kind(x) for x in lines})
            obs.append(f"{where}: {', '.join(kinds) or '보고 없음'}")
        if stun_frames:
            what = "가드를 넣었는데도" if guarded else "가드 없이"
            return LEAK, [f"{what} WebRTC 가 프록시를 비켜 UDP 로 STUN 에 닿았다"] + obs
        if not o.has("page rtc page ready"):
            return UNKNOWN, ["탐침 쪽이 끝까지 돌지 않았다"] + obs
        return PASS, ["어느 틀에서도 STUN 에 닿지 않았다"] + obs

    return check


def _rtc_kind(line: str) -> str:
    if line.startswith("candidate "):
        m = re.search(r" typ (\w+)", line)
        addr = line.split(" ")[5] if len(line.split(" ")) > 5 else "?"
        return f"후보 {m.group(1) if m else '?'}({'mDNS' if addr.endswith('.local') else addr})"
    return line.split(" ")[0]


def check_ws(o: Observed):
    host = f"w{o.tok}.lab.test"
    opened = o.ws(host)
    echo = o.has("page ws echo hi")
    tunnel = o.proxy("connect", host)
    dns = o.dns(host)
    obs = [f"웹소켓 서버 연결 {len(opened)}건, 프록시 CONNECT {len(tunnel)}건, 기기 DNS {len(dns)}건"]
    obs += [f"앱: {line}" for line in o.app("page ws ")] + [f"앱: {e}" for e in o.errors()]
    if not echo:
        return (LEAK if opened and not tunnel else FAIL), ["웹소켓이 오가지 않았다"] + obs
    if dns or not tunnel:
        return LEAK, ["웹소켓이 프록시를 비켰다"] + obs
    return PASS, ["ws:// 도 프록시의 CONNECT 로 갔고 기기는 이름을 찾지 않았다"] + obs


INTEREST = ["User-Agent", "X-Requested-With", "Accept-Language", "Sec-CH-UA", "Sec-CH-UA-Mobile", "Sec-CH-UA-Platform"]


def check_headers(o: Observed):
    obs = []
    for label, needle in (("문서", f"/probe/headers?t={o.tok}"), ("fetch", "/probe/echo-headers")):
        hits = [h for h in o.http(needle) if o.tok in (h.get("host") or "")]
        if not hits:
            obs.append(f"{label}: 요청 없음")
            continue
        headers = {k.lower(): v for k, v in hits[0]["headers"].items()}
        for name in INTEREST:
            obs.append(f"{label} {name}: {headers.get(name.lower(), '(없음)')}")
    return INFO, obs


def check_sw_reg(o: Observed):
    obs = [f"앱: {line}" for line in o.app("page sw ")] + auth_summary(o) + [f"앱: {e}" for e in o.errors()]
    return (PASS, ["서비스 워커가 등록됐다"] + obs) if o.has("page sw registered") else (FAIL, ["등록되지 않았다"] + obs)


def check_sw_page(o: Observed):
    obs = [f"앱: {line}" for line in o.app("page sw ")] + auth_summary(o) + [f"앱: {e}" for e in o.errors()] + [o.title()]
    net = o.http(f"/sw-net/page.html?t={o.tok}")
    obs.append(f"서비스 워커의 네트워크 요청이 목적지에 닿은 수: {len(net)}")
    if any(line.startswith("page sw page-ok") for line in o.lines):
        return PASS, ["서비스 워커의 요청도 프록시 인증을 지났다"] + obs
    if any(line.startswith("page sw fetch-failed") for line in o.lines):
        return FAIL, ["서비스 워커의 요청이 막혔다 — 쪽이 열리지 않는다"] + obs
    if o.has("page sw page-direct"):
        return INFO, ["서비스 워커가 그 쪽을 맡지 않았다"] + obs
    return UNKNOWN, ["쪽이 무엇도 보고하지 않았다"] + obs


def check_ambiguity(o: Observed):
    hits = o.http(f"/probe/auth401?t={o.tok}")
    leaked = [h for h in hits if h["headers"].get("Authorization", "").startswith("<redacted: lab proxy")]
    obs = auth_summary(o) + [f"사이트 요청 {len(hits)}건, 그중 실험실 프록시 계정을 실은 것 {len(leaked)}건", o.title()]
    if leaked:
        return LEAK, ["사이트의 401 에 프록시 계정을 넘겼다 — 콜백(host, realm)만으로는 프록시의 407 과 가릴 수 없다"] + obs
    if not hits:
        return UNKNOWN, ["사이트에 닿지 않았다"] + obs
    return PASS, ["사이트의 401 에는 프록시 계정을 넘기지 않았다"] + obs


def check_speed(o: Observed):
    lines = [line for line in o.app("page speed ") if "MBps=" in line]
    obs = [line[len("page speed "):] for line in lines] + [f"앱: {e}" for e in o.errors()]
    if not lines:
        return UNKNOWN, ["속도를 재지 못했다"] + obs
    rates = [float(re.search(r"MBps=([\d.]+)", line).group(1)) for line in lines]
    return INFO, [f"{len(rates)}회 평균 {sum(rates) / len(rates):.1f} MB/s (실험실 — PC 안의 루프백·파이썬 TLS 프록시)"] + obs


def check_headers_override(o: Observed):
    obs = [f"앱: {line}" for line in o.app("headers ")]
    hits = [h for h in o.http(f"/probe/headers?t={o.tok}")]
    subs = [h for h in o.http("/probe/echo-headers") if o.tok in (h.get("host") or "")]
    for label, group in (("문서", hits), ("fetch", subs)):
        if group:
            hs = {k.lower(): v for k, v in group[0]["headers"].items()}
            obs.append(f"{label} X-Requested-With: {hs.get('x-requested-with', '(없음)')!r}")
    pkg = [x for x in obs if PKG in x and "X-Requested-With" in x]
    if any("unsupported" in x or "failed" in x for x in obs):
        return INFO, ["사용자 지정 머리를 쓸 수 없다"] + obs
    return (LEAK if pkg else PASS), (["여전히 앱 패키지가 실린다"] if pkg else ["앱 패키지가 실리지 않는다"]) + obs


def check_ajax_header(o: Observed):
    obs = [f"앱: {line}" for line in o.app("headers custom")]
    ajax = [h for h in o.http("/probe/echo-headers?k=ajax") if o.tok in (h.get("host") or "")]
    plain = [h for h in o.http("/probe/echo-headers") if o.tok in (h.get("host") or "") and "k=ajax" not in h["path"]]
    for label, group in (("쪽이 단 AJAX 요청", ajax), ("보통 fetch", plain)):
        if group:
            hs = {k.lower(): v for k, v in group[0]["headers"].items()}
            obs.append(f"{label}: X-Requested-With={hs.get('x-requested-with', '(없음)')!r}")
    if not ajax:
        return UNKNOWN, ["AJAX 요청이 오지 않았다"] + obs
    value = {k.lower(): v for k, v in ajax[0]["headers"].items()}.get("x-requested-with")
    if value == "XMLHttpRequest":
        return PASS, ["쪽이 단 값은 그대로 간다 — 사용자 지정 머리는 WebView 가 붙이는 값만 덮는다"] + obs
    return FAIL, [f"쪽이 단 값까지 덮였다({value!r}) — AJAX 를 이 머리로 가리는 사이트가 깨질 수 있다"] + obs


def check_wrong_password(o: Observed):
    hits = o.http(f"t={o.tok}")
    obs = auth_summary(o) + [f"앱: {e}" for e in o.errors()] + [f"앱: {line}" for line in o.app("relay ")][:6] + [o.title()]
    if hits:
        return LEAK, ["틀린 계정인데 목적지에 닿았다"] + obs
    return PASS, ["목적지에 닿지 않았다 — 사용자에게 무엇이 보이는지는 아래 기록"] + obs


def check_refused(o: Observed):
    dns = o.dns("blocked.example")
    refused = o.proxy("refused", "blocked.example")
    obs = [f"프록시가 거절한 CONNECT {len(refused)}건, 기기의 DNS 질의 {len(dns)}건"] + [f"앱: {e}" for e in o.errors()] + [o.title()]
    if dns:
        return LEAK, ["프록시가 거절하자 기기가 이름을 직접 찾았다 — 직접 연결을 시도한 흔적"] + obs
    if not refused:
        return UNKNOWN, ["프록시에 요청이 오지 않았다"] + obs
    return PASS, ["프록시가 거절해도 직접 연결로 넘어가지 않는다"] + obs


def check_upload(o: Observed):
    sink = [h for h in o.http(f"/probe/sink?t={o.tok}")]
    obs = [f"앱: {line}" for line in o.app("page upload")] + [f"목적지가 받은 몸: {[h.get('body_len') for h in sink]}"]
    obs += [f"앱: {e}" for e in o.errors()]
    if sink and sink[0].get("body_len") == 4194304 and o.has("page upload received=4194304"):
        return PASS, ["4 MiB 를 온전히 올렸다"] + obs
    return FAIL, ["올리기가 온전하지 않다"] + obs


def check_info(o: Observed):
    return INFO, [line for line in o.lines if line.startswith(("webview ", "ua ", "feature "))]


def plan(creds: dict) -> list[Exp]:
    base = {
        "phost": PROXY_HOST,
        "pport": str(labserver.PROXY_TLS_PORT),
        "hport": str(labserver.PROXY_PLAIN_PORT),
        "user": creds["user"],
        "pass": creds["password"],
    }
    http_url = "http://{host}.lab.test:8080/probe/basic?t={{t}}"
    https_url = "https://{host}.lab.test:8443/probe/basic?t={{t}}"
    out: list[Exp] = []

    def add(group: str, name: str, title: str, extras: dict, check, **kw) -> None:
        out.append(Exp(group, name, title, {**base, **extras}, check, **kw))

    add("info", "info", "WebView 판과 기능", {"exp": "info"}, check_info, before="clear")

    for mode, via, label in (("https", "tls", "TLS 프록시 직접"), ("http", "plain", "평문 프록시(대조군)"), ("relay", "tls", "앱 안 중계")):
        for scheme in ("http", "https"):
            url = (http_url if scheme == "http" else https_url).format(host="a{t}")
            add("auth", f"auth-{mode}-{scheme}", f"{label} · {scheme} 쪽", {"exp": "load", "mode": mode, "url": url},
                check_load(scheme, via, "a"))
    add("auth", "auth-https-cancel", "TLS 프록시 · 407 에 답하지 않음", {"exp": "load", "mode": "https", "answer": "0",
        "url": http_url.format(host="a{t}")}, check_cancelled)
    add("auth", "auth-https-reuse", "TLS 프록시 · 한 프로세스에서 두 쪽(인증 재사용)", {"exp": "load", "mode": "https",
        "pre": http_url.format(host="p{t}"), "prewait": "4000", "url": https_url.format(host="a{t}")}, check_load("https", "tls", "a"))

    add("relayhttp", "relayhttp", "앱의 HttpURLConnection 이 중계를 쓴다", {"exp": "relayhttp", "mode": "relay",
        "url": http_url.format(host="h{t}") + "," + https_url.format(host="h{t}")}, check_relayhttp)

    dead = {"pport": str(CLOSED_PORT)}
    add("failclosed", "failclosed-https-http", "TLS 프록시가 죽음 · http 쪽(IP)", {"exp": "load", "mode": "https", **dead,
        "url": "http://10.0.2.2:8080/probe/basic?t={t}"}, check_failclosed(None))
    add("failclosed", "failclosed-https-https", "TLS 프록시가 죽음 · https 쪽(이름)", {"exp": "load", "mode": "https", **dead,
        "url": https_url.format(host="f{t}")}, check_failclosed("f{t}"))
    add("failclosed", "failclosed-relay-http", "중계 뒤 프록시가 죽음 · http 쪽(IP)", {"exp": "load", "mode": "relay", **dead,
        "url": "http://10.0.2.2:8080/probe/basic?t={t}"}, check_failclosed(None))
    add("failclosed", "failclosed-relay-https", "중계 뒤 프록시가 죽음 · https 쪽(이름)", {"exp": "load", "mode": "relay", **dead,
        "url": https_url.format(host="f{t}")}, check_failclosed("f{t}"))
    add("failclosed", "failclosed-name", "프록시 이름을 못 찾음", {"exp": "load", "mode": "https", "phost": "noproxy.invalid",
        "url": "http://10.0.2.2:8080/probe/basic?t={t}"}, check_failclosed(None))

    add("bypass", "bypass-cidr", "예외 10.0.0.0/8 · 10.0.2.2", {"exp": "load", "mode": "https", "bypass": "10.0.0.0/8",
        "url": "http://10.0.2.2:8080/probe/basic?t={t}"}, check_bypass(True, "10.0.2.2"))
    add("bypass", "bypass-wildcard", "예외 *.direct.lab.test", {"exp": "load", "mode": "https", "bypass": "*.direct.lab.test",
        "url": "http://b{t}.direct.lab.test:8080/probe/basic?t={t}"}, check_bypass(True, "b{t}.direct.lab.test"))
    add("bypass", "bypass-simple", "예외 <simple> · labhost", {"exp": "load", "mode": "https", "bypass": "<simple>",
        "url": "http://labhost:8080/probe/basic?t={t}"}, check_bypass(True, "labhost"))
    add("bypass", "bypass-control", "예외에 들지 않는 이름(대조군)", {"exp": "load", "mode": "https", "bypass": "*.direct.lab.test",
        "url": http_url.format(host="n{t}")}, check_bypass(False, "n{t}.lab.test"))

    dns_http = "http://d{t}.lab.test:8080/probe/dns?t={t}"
    dns_https = "https://d{t}.lab.test:8443/probe/dns?t={t}"
    add("dns", "dns-https-http", "DNS 누출 · TLS 프록시 · http 쪽", {"exp": "load", "mode": "https", "url": dns_http, "wait": "8000"},
        check_dns(True))
    add("dns", "dns-https-https", "DNS 누출 · TLS 프록시 · https 쪽", {"exp": "load", "mode": "https", "url": dns_https, "wait": "8000"},
        check_dns(True))
    add("dns", "dns-relay-http", "DNS 누출 · 중계 · http 쪽", {"exp": "load", "mode": "relay", "url": dns_http, "wait": "8000"},
        check_dns(True))
    add("dns", "dns-direct-http", "DNS · 프록시 없음(대조군)", {"exp": "load", "mode": "direct", "url": dns_http, "wait": "8000"},
        check_dns(False))
    add("dns", "dns-guard", "DNS 누출 · 스크립트 가드(dns-prefetch)", {"exp": "load", "mode": "https", "url": dns_http, "wait": "8000",
        "dnsguard": "1"}, check_dns(True))
    add("dns", "dns-specoff", "DNS 누출 · 투기적 로딩 끔", {"exp": "load", "mode": "https", "url": dns_http, "wait": "8000",
        "speculative": "off"}, check_dns(True))

    rtc2 = "http://r{t}.lab.test:8080/probe/webrtc2?t={t}"
    for guard, inject, title in (("0", "start", "가드 없음"), ("1", "start", "1판 · 문서 시작"), ("1", "event", "1판 · 틀·세계 지정 주입"),
                                 ("2", "start", "2판 · 문서 시작"), ("2", "event", "2판 · 틀·세계 지정 주입")):
        add("webrtc2", f"webrtc2-g{guard}-{inject}", f"WebRTC 우회 15길 · {title}", {"exp": "load", "mode": "https", "url": rtc2,
            "wait": "9000", "guard": guard, "inject": inject}, check_webrtc(guard != "0", RTC2_VECTORS))

    ambiguity = "https://proxy.lab.test:8443/probe/auth401?t={t}"
    add("ambiguity", "ambiguity-https", "프록시와 같은 이름의 사이트가 같은 realm 으로 401 · TLS 프록시 직접", {"exp": "load", "mode": "https",
        "url": ambiguity}, check_ambiguity)
    add("ambiguity", "ambiguity-relay", "같은 401 · 앱 안 중계(무작위 realm)", {"exp": "load", "mode": "relay", "url": ambiguity},
        check_ambiguity)

    add("failclosed", "failclosed-badcert-https", "프록시 인증서를 믿을 수 없음 · TLS 프록시 직접", {"exp": "load", "mode": "https",
        "phost": "10.0.2.2", "url": "http://10.0.2.2:8080/probe/basic?t={t}"}, check_failclosed(None))
    add("failclosed", "failclosed-badcert-relay", "프록시 인증서를 믿을 수 없음 · 중계", {"exp": "load", "mode": "relay",
        "phost": "10.0.2.2", "url": "http://10.0.2.2:8080/probe/basic?t={t}"}, check_failclosed(None))

    speed = "https://s{t}.lab.test:8443/probe/speed?t={t}&n=16000000"
    add("speed", "speed-https", "속도 · TLS 프록시 직접", {"exp": "load", "mode": "https", "url": speed, "wait": "25000"}, check_speed)
    add("speed", "speed-relay", "속도 · 앱 안 중계", {"exp": "load", "mode": "relay", "url": speed, "wait": "25000"}, check_speed)

    add("headers", "headers-xrw", "X-Requested-With 를 사용자 지정 머리로 덮기", {"exp": "load", "mode": "https", "headers": "xrw-empty",
        "url": "https://x{t}.lab.test:8443/probe/headers?t={t}"}, check_headers_override)

    add("headers", "headers-xrw-ajax", "X-Requested-With 덮기 · 쪽이 단 AJAX 머리", {"exp": "load", "mode": "https",
        "headers": "xrw-empty", "url": "https://x{t}.lab.test:8443/probe/headers?t={t}"}, check_ajax_header)

    add("auth", "auth-https-wrongpass", "TLS 프록시 직접 · 틀린 비밀번호", {"exp": "load", "mode": "https", "pass": "wrong-password",
        "url": http_url.format(host="a{t}")}, check_wrong_password)
    add("auth", "auth-relay-wrongpass", "중계 · 원격의 틀린 비밀번호", {"exp": "load", "mode": "relay", "pass": "wrong-password",
        "url": http_url.format(host="a{t}")}, check_wrong_password)
    add("auth", "auth-relay-wrongpass-https", "중계 · 원격의 틀린 비밀번호 · https 쪽", {"exp": "load", "mode": "relay",
        "pass": "wrong-password", "url": https_url.format(host="a{t}")}, check_wrong_password)

    add("failclosed", "failclosed-refused", "프록시가 CONNECT 를 거절(403)", {"exp": "load", "mode": "https",
        "url": "https://blocked.example/x?t={t}"}, check_refused)

    for mode in ("https", "relay"):
        add("upload", f"upload-{mode}-http", f"http 쪽에서 4 MiB 올리기 · {mode}", {"exp": "load", "mode": mode,
            "url": "http://u{t}.lab.test:8080/probe/upload?t={t}", "wait": "10000"}, check_upload)
        add("upload", f"upload-{mode}-https", f"https 쪽에서 4 MiB 올리기 · {mode}", {"exp": "load", "mode": mode,
            "url": "https://u{t}.lab.test:8443/probe/upload?t={t}", "wait": "10000"}, check_upload)

    # 프로필: 프록시 설정(프로세스 전체)이 다른 프로필에도 걸리는가, 인증 캐시는 프로필마다 따로인가
    add("profile", "profile-https-http", "다른 프로필 · TLS 프록시 · http 쪽", {"exp": "load", "mode": "https", "profile": "p2",
        "url": http_url.format(host="a{t}")}, check_load("http", "tls", "a"))
    add("profile", "profile-relay-https", "다른 프로필 · 중계 · https 쪽", {"exp": "load", "mode": "relay", "profile": "p2",
        "url": https_url.format(host="a{t}")}, check_load("https", "tls", "a"))
    add("profile", "profile-auth-shared", "기본 프로필에서 인증한 뒤 다른 프로필 · TLS 프록시", {"exp": "load", "mode": "https",
        "profile": "p2", "pre": https_url.format(host="p{t}"), "prehidden": "1", "prewait": "4000",
        "url": https_url.format(host="a{t}")}, check_load("https", "tls", "a"))

    ws = "http://k{t}.lab.test:8080/probe/ws?t={t}"
    add("ws", "ws-https", "웹소켓 · TLS 프록시", {"exp": "load", "mode": "https", "url": ws}, check_ws)
    add("ws", "ws-relay", "웹소켓 · 중계", {"exp": "load", "mode": "relay", "url": ws}, check_ws)

    add("headers", "headers-https", "WebView 가 붙이는 머리", {"exp": "load", "mode": "https",
        "url": "https://x{t}.lab.test:8443/probe/headers?t={t}"}, check_headers)

    for group, mode in (("sw", "https"), ("swrelay", "relay")):
        sw = "https://sw.lab.test:8443/sw/page.html?t={t}"
        add(group, f"{group}-reg", f"서비스 워커 등록 · {mode}", {"exp": "load", "mode": mode,
            "url": "https://sw.lab.test:8443/sw/reg.html", "wait": "8000"}, check_sw_reg, before="clear")
        add(group, f"{group}-cold", f"서비스 워커 · 새 프로세스의 첫 요청 · {mode}", {"exp": "load", "mode": mode, "url": sw,
            "wait": "8000"}, check_sw_page)
        add(group, f"{group}-warm", f"서비스 워커 · 앞서 다른 쪽으로 인증 · {mode}", {"exp": "load", "mode": mode,
            "pre": "https://warm.lab.test:8443/probe/warm?t={t}", "prewait": "4000", "url": sw, "wait": "8000"}, check_sw_page)
        add(group, f"{group}-hiddenwarm", f"서비스 워커 · 화면 밖 WebView 로 인증 · {mode}", {"exp": "load", "mode": mode,
            "pre": "https://warm.lab.test:8443/probe/warm?t={t}", "prehidden": "1", "prewait": "4000", "url": sw, "wait": "8000"},
            check_sw_page)
    return out


# ── 돌리기 ───────────────────────────────────────────────────────────────────────────────────────────────────────────


def read_log(run: str) -> list[str]:
    out = adb("logcat", "-d", "-v", "raw", "-s", "IROLAB:I", check=False)
    prefix = run + " "
    return [line.strip()[len(prefix):] for line in out.splitlines() if line.strip().startswith(prefix)]


def execute(lab: labserver.LabServer, exp: Exp) -> Observed:
    if exp.before == "clear":
        adb("shell", "pm", "clear", PKG, check=False)
    adb("logcat", "-c", check=False)
    t0 = time.time()
    args = ["shell", "am", "start", "-S", "-W", "-n", ACTIVITY, "--es", "run", exp.run]
    for k, v in exp.extras.items():
        args += ["--es", k, shlex.quote(str(v))]
    adb(*args)
    deadline = time.time() + exp.timeout
    finished = False
    lines: list[str] = []
    while time.time() < deadline:
        lines = read_log(exp.run)
        if "done" in lines:
            finished = True
            break
        time.sleep(0.5)
    time.sleep(1.0)  # 늦게 오는 DNS·STUN 을 기다린다
    t1 = time.time()
    lines = read_log(exp.run)
    adb("shell", "am", "force-stop", PKG, check=False)
    return Observed(exp, lines, lab.events.between(t0, t1), finished)


def dns_sanity(lab: labserver.LabServer) -> None:
    name = f"dnscheck-{secrets.token_hex(3)}.lab.test"
    t0 = time.time()
    adb("shell", "ping", "-c", "1", "-W", "1", name, check=False, timeout=20)
    time.sleep(1.0)
    if not [e for e in lab.events.between(t0) if e["kind"] == "dns" and e["name"] == name]:
        sys.exit("에뮬레이터의 DNS 질의가 이 실험실로 오지 않는다 — 에뮬레이터를 -dns-server 127.0.0.1 로 다시 띄운다.")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("groups", nargs="*")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--repeat", type=int, default=1, help="고른 실험을 몇 번씩 돌리는가(가끔만 나는 결함을 잰다)")
    args = parser.parse_args()

    global SERIAL
    SERIAL = pick_emulator()
    creds = json.loads((LAB / "credentials.json").read_text(encoding="utf-8"))
    lab = labserver.LabServer().start()
    print(f"에뮬레이터 {SERIAL} · 실험실 서버 시작")
    if not args.skip_install:
        if not APK.exists():
            sys.exit("디버그 APK 가 없다 — ./gradlew :app:assembleDebug")
        adb("install", "-r", "-t", str(APK), timeout=180)
        print("디버그 APK 설치")
    dns_sanity(lab)
    print("DNS 기록 확인")

    exps = [
        e
        for _ in range(max(1, args.repeat))
        for e in plan(creds)
        if not args.groups or e.group in args.groups or e.name in args.groups
    ]
    results = []
    try:
        for exp in exps:
            try:
                o = execute(lab, exp)
            except AdbError as e:
                # 에뮬레이터가 답하지 않았다(2026-10-04: 시스템 서버가 감시견에 죽었다). 이 실험은 '확인 불가' 로 두고 다시 선 뒤 잇는다.
                print(f"[{UNKNOWN}] {exp.name}: {e}")
                results.append((exp, Observed(exp, [], [], False), UNKNOWN, [str(e)]))
                if not wait_for_emulator():
                    print("에뮬레이터가 돌아오지 않는다 — 여기서 멈춘다")
                    break
                continue
            try:
                verdict, notes = exp.check(o)
            except Exception as e:  # 판정 코드의 결함 — 원본은 남긴다
                verdict, notes = UNKNOWN, [f"판정 실패: {e!r}"]
            if not o.finished:
                notes = ["(앱이 done 을 적지 않았다 — 시간 초과)"] + notes
            print(f"[{verdict}] {exp.name}: {notes[0] if notes else ''}", flush=True)
            results.append((exp, o, verdict, notes))
            time.sleep(1.5)  # 에뮬레이터의 시스템 서버에 숨 돌릴 틈을 준다
    finally:
        if results:
            save(results, lab)


def save(results, lab: labserver.LabServer) -> None:
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    write_report(results, LAB / f"results-{stamp}.md", lab)
    (LAB / f"results-{stamp}.json").write_text(
        json.dumps(
            [{"name": e.name, "run": e.run, "verdict": v, "notes": n, "app": o.lines, "events": o.events} for e, o, v, n in results],
            ensure_ascii=False,
            indent=1,
        ),
        encoding="utf-8",
    )
    print(f"결과: local/lab/results-{stamp}.md")


def write_report(results, path: pathlib.Path, lab: labserver.LabServer) -> None:
    info = next((o for e, o, v, n in results if e.name == "info"), None)
    lines = [f"# Web 실험판 실험 결과 ({datetime.datetime.now():%Y-%m-%d %H:%M})", ""]
    if info:
        lines += [f"- {line}" for line in info.lines if line.startswith(("webview ", "ua "))]
    lines += [f"- 에뮬레이터 {AVD} ({SERIAL})", "", "| 실험 | 조건 | 판정 | 핵심 |", "|---|---|---|---|"]
    for e, o, v, n in results:
        lines.append(f"| `{e.name}` | {e.title} | **{v}** | {(n[0] if n else '').replace('|', '/')} |")
    lines.append("")
    refused: dict[str, int] = {}
    other_dns: dict[str, int] = {}
    for e, o, v, n in results:
        lines += [f"## {e.name} — {e.title}", "", f"판정: **{v}**", ""]
        lines += [f"- {x}" for x in n]
        lines += ["", "<details><summary>앱 기록</summary>", "", "```"] + o.lines[:80] + ["```", "", "</details>", ""]
        for ev in o.events:
            if ev["kind"] == "proxy" and ev["ev"] == "refused":
                refused[ev["target"]] = refused.get(ev["target"], 0) + 1
            if ev["kind"] == "dns" and not ev.get("lab"):
                other_dns[ev["name"]] = other_dns.get(ev["name"], 0) + 1
    lines += ["## 실험 동안 프록시가 거절한 실험실 밖 목적지", ""]
    lines += [f"- `{k}` × {c}" for k, c in sorted(refused.items(), key=lambda x: -x[1])] or ["- 없음"]
    lines += ["", "## 실험 동안 기기가 찾은 실험실 밖 이름 (다른 앱의 것일 수 있다)", ""]
    lines += [f"- `{k}` × {c}" for k, c in sorted(other_dns.items(), key=lambda x: -x[1])[:40]] or ["- 없음"]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
