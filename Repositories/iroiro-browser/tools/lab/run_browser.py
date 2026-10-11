"""Web 브라우저 자체를 에뮬레이터에서 실험실에 붙여 확인한다(run_lab.py 가 WebView 의 성질을 잰다면, 이것은 앱이 그 성질대로 짜였는지 본다).

준비는 run_lab.py 와 같다(인증서·디버그 APK·실험실 DNS 로 띄운 에뮬레이터). 돌리기:

    python tools/lab/run_browser.py                  # 전부
    python tools/lab/run_browser.py ready download   # 고른 것만(앞 시나리오가 설정을 넣는다 — ready 를 함께)

브라우저의 설정은 디버그 전용 `LabSetupActivity` 로 넣는다(adb 의 셸만 열 수 있다 — DUMP 권한). 화면의 일은 디버그 빌드가 logcat `WebUI` 에
적는 줄로 읽고, 주소창은 실제로 두드려 친다(`input text`). 실험실 서버의 사건으로 길을 판정한다.

결과: ``local/lab/browser-<시각>.md``.
"""

from __future__ import annotations

import datetime
import hashlib
import json
import pathlib
import re
import secrets
import shlex
import subprocess
import sys
import time
import urllib.parse
import xml.etree.ElementTree as ET

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import labmedia  # noqa: E402
import labserver  # noqa: E402
import run_lab  # noqa: E402

PKG = run_lab.PKG
BROWSER = f"{PKG}/.browser.BrowserActivity"
SETUP = f"{PKG}/.lab.LabSetupActivity"
EXIT_URL = "https://exit.lab.test:8443/cdn-cgi/trace"
PASS, FAIL, INFO = "통과", "실패", "정보"

adb = run_lab.adb


def log_lines(tag: str = "WebUI") -> list[str]:
    out = adb("logcat", "-d", "-v", "raw", "-s", f"{tag}:I", check=False)
    return [line.strip() for line in out.splitlines() if line.strip() and not line.startswith("---------")]


def wait_log(pattern: str, timeout: float = 20) -> str | None:
    rx = re.compile(pattern)
    deadline = time.time() + timeout
    while time.time() < deadline:
        for line in log_lines():
            if rx.search(line):
                return line
        time.sleep(0.4)
    return None


def wait_page(mark: str, timeout: float = 20) -> str | None:
    """주소에 [mark] 가 든 쪽이 다 열렸다는 줄. 제목으로 기다리면 되살린 탭이 같은 제목의 쪽을 열 때 그것을 제 것으로 읽는다."""
    return wait_log(rf"^finished tab=\d+ .*{re.escape(mark)}", timeout)


def last_state() -> str | None:
    states = [line for line in log_lines() if line.startswith("state ")]
    return states[-1] if states else None


def setup(creds: dict, **extras: str) -> None:
    """브라우저의 프록시 설정을 넣는다(시험 계정). 값은 출력하지 않는다."""
    base = {"enabled": "1", "host": "proxy.lab.test", "port": str(labserver.PROXY_TLS_PORT), "user": creds["user"],
            "pass": creds["password"], "exit": EXIT_URL, "private": "1", "cidrs": "127.0.0.0/8,169.254.0.0/16"}
    base.update(extras)
    args = ["shell", "am", "start", "-W", "-n", SETUP]
    for k, v in base.items():
        args += ["--es", k, shlex.quote(v)]
    adb(*args)
    time.sleep(1.5)


def launch() -> bool:
    adb("logcat", "-c", check=False)
    adb("shell", "am", "start", "-S", "-W", "-n", BROWSER)
    return wait_log(r"^ready$", 15) is not None


def dump() -> list[dict]:
    """화면의 뷰(브라우저 화면 — 비밀번호 칸이 없는 화면에서만 부른다)."""
    adb("shell", "uiautomator", "dump", "/sdcard/web-ui.xml", check=False)
    raw = adb("exec-out", "cat", "/sdcard/web-ui.xml", check=False)
    adb("shell", "rm", "-f", "/sdcard/web-ui.xml", check=False)
    nodes = []
    try:
        root = ET.fromstring(raw[raw.index("<"):])
    except (ValueError, ET.ParseError):
        return nodes
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        nodes.append({"cls": n.get("class", ""), "text": n.get("text", ""), "desc": n.get("content-desc", ""),
                      "id": n.get("resource-id", ""), "x": (x1 + x2) // 2, "y": (y1 + y2) // 2})
    return nodes


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def snap(name: str) -> None:
    """실패한 순간의 화면을 실험실 기록 곁에 남긴다(`local/` 은 커밋하지 않는다). 화면을 못 떠도 시험은 잇는다."""
    try:
        r = subprocess.run([run_lab.ADB, "-s", run_lab.SERIAL, "exec-out", "screencap", "-p"], capture_output=True, timeout=30)
    except subprocess.TimeoutExpired:
        return
    if r.returncode == 0 and r.stdout.startswith(b"\x89PNG"):
        (run_lab.LAB / f"fail-{name}.png").write_bytes(r.stdout)


def tap_node(key: str, value: str, timeout: float = 8) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in dump():
            if n[key] == value:
                tap(n["x"], n["y"])
                time.sleep(0.6)
                return True
        time.sleep(0.5)
    return False


def tap_text(text: str, timeout: float = 8) -> bool:
    return tap_node("text", text, timeout)


def tap_prefix(prefix: str, timeout: float = 8) -> bool:
    """글이 [prefix] 로 시작하는 것을 누른다(숫자가 붙는 메뉴 항목)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in dump():
            if n["text"].startswith(prefix):
                tap(n["x"], n["y"])
                time.sleep(0.6)
                return True
        time.sleep(0.5)
    return False


def tap_desc(desc: str, timeout: float = 8) -> bool:
    """설명(contentDescription)으로 — 메뉴 단추·길 표시."""
    return tap_node("desc", desc, timeout)


def tap_dialog_positive(timeout: float = 8) -> bool:
    """떠 있는 물음 창의 긍정 단추(뒤의 화면에 같은 글의 단추가 있어도)."""
    return tap_node("id", "android:id/button1", timeout)


def tap_switch(timeout: float = 8) -> bool:
    """막대 왼쪽의 프록시 스위치(화면에 스위치는 그것 하나다)."""
    return tap_node("cls", "android.widget.Switch", timeout)


def long_press(x: int, y: int, ms: int = 1200) -> None:
    adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), str(ms))


def tab_count() -> int | None:
    """막대의 탭 단추 — 설명이 '탭 3개' 다(화면 낭독기가 수를 읽게, 2026-10-07)."""
    for n in dump():
        m = re.fullmatch(r"탭 (\d+)개", n["desc"])
        if m and n["text"].isdigit():
            return int(n["text"])
    return None


def tap_tabs_button(timeout: float = 8) -> bool:
    """막대의 탭 단추(설명 '탭 N개')를 눌러 탭 목록을 연다."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in dump():
            if re.fullmatch(r"탭 \d+개", n["desc"]) and n["text"].isdigit():
                tap(n["x"], n["y"])
                time.sleep(0.6)
                return True
        time.sleep(0.5)
    return False


def wait_network(timeout: float = 90) -> str:
    """기기의 기본 네트워크가 Wi-Fi 로 자리 잡을 때까지 — 막 부팅한 에뮬레이터는 모바일에서 Wi-Fi 로 넘어가며 첫 연결을 끊는다
    (첫 시나리오가 '끊김' 으로 실패했다)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        out = adb("shell", "dumpsys", "connectivity", check=False)
        m = re.search(r"Active default network: (\d+)", out)
        wifi = re.search(r"NetworkAgentInfo\{network\{(\d+)\}[^\n]*ni\{WIFI CONNECTED", out)
        if m and wifi and m.group(1) == wifi.group(1):
            time.sleep(5)
            return f"Wi-Fi(network {m.group(1)})"
        time.sleep(2)
    return "기다림 끝(Wi-Fi 아님)"


def open_new_tab() -> bool:
    return tap_desc("메뉴") and tap_text("새 탭")


def focused() -> str:
    # dumpsys window 의 mCurrentFocus 첫 줄은 마지막 ANR 때의 기록일 수 있다 — 마지막 줄이 지금의 것이다.
    lines = [l for l in adb("shell", "dumpsys", "window", check=False).splitlines() if "mCurrentFocus" in l]
    return lines[-1] if lines else ""


def back_to_browser(tries: int = 3) -> bool:
    """뒤로를 눌러 브라우저 화면으로 돌아온다(자판이 떠 있으면 첫 뒤로는 자판을 닫는다)."""
    for _ in range(tries):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)
        if "BrowserActivity" in focused():
            return True
    return False


def type_address(text: str) -> None:
    # 앞 단계의 줄과 섞이지 않게 logcat 을 비우고 친다.
    adb("logcat", "-c", check=False)
    # 화면을 읽는 uiautomator 가 가끔 '쉬는 때' 를 못 찾고 빈손으로 끝난다(알림이 막 뜬 때 등) — 몇 번 더 읽는다.
    edits = []
    for _ in range(6):
        edits = [n for n in dump() if n["cls"].endswith("EditText")]
        if edits:
            break
        time.sleep(1.5)
    if not edits:
        raise RuntimeError("주소창을 찾지 못했다")
    tap(edits[0]["x"], edits[0]["y"])
    time.sleep(0.4)
    # 주소창은 눌리면 글 전체를 고른다 — 치면 바뀐다. 빈칸은 %s 로.
    adb("shell", "input", "text", shlex.quote(text.replace(" ", "%s")))
    # **친 글이 다 들어간 뒤에 Enter 를 누른다.** 기기가 바쁘면(영상 녹화를 막 저장했다) 자판이 글자를 늦게 넣어, 곧바로 누른 Enter 가
    # 앞부분만 열었다(`…/probe/play?t=…` 가 `…/probe/pla` 로 — 실험실의 404 쪽이 흰 화면으로 남았다, 2026-10-05).
    deadline = time.time() + 8
    while time.time() < deadline:
        if any(n["cls"].endswith("EditText") and n["text"] == text for n in dump()):
            break
        time.sleep(0.4)
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")


def events_since(lab: labserver.LabServer, t0: float) -> list[dict]:
    return lab.events.between(t0)


def pull_download(name: str) -> bytes | None:
    """기기의 '내려받기' 폴더에서 파일을 그대로 가져온다(이진 — run_lab.adb 는 글로 읽는다)."""
    cmd = [run_lab.ADB, "-s", run_lab.SERIAL, "exec-out", "cat " + shlex.quote("/sdcard/Download/" + name)]
    try:
        r = subprocess.run(cmd, capture_output=True, timeout=90)
    except subprocess.TimeoutExpired:
        return None
    return r.stdout if r.returncode == 0 and r.stdout and not r.stdout.startswith(b"cat:") else None


def downloads_listing() -> dict[str, int]:
    """'내려받기' 폴더의 이름 → 크기."""
    out = adb("shell", "ls", "-l", "/sdcard/Download/", check=False)
    found = {}
    for line in out.splitlines():
        m = re.match(r"\S+\s+\d+\s+\S+\s+\S+\s+(\d+)\s+\S+\s+\S+\s+(.+)$", line.strip())
        if m:
            found[m.group(2)] = int(m.group(1))
    return found


def done_name(line: str | None) -> str | None:
    """'download DONE <이름> bytes=…' 의 이름."""
    m = re.match(r"^download \S+ (.+?) bytes=", line or "")
    return m.group(1) if m else None


def screen_center(fraction_y: float = 0.4) -> tuple[int, int]:
    out = adb("shell", "wm", "size", check=False)
    m = re.search(r"(\d+)x(\d+)", out)
    w, h = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)
    return w // 2, int(h * fraction_y)


def reports(lab: labserver.LabServer, t0: float, host: str = "") -> list[tuple[str, str]]:
    """쪽이 /probe/report 로 알린 (k, v)."""
    out = []
    for e in events_since(lab, t0):
        if e["kind"] != "http" or not e.get("path", "").startswith("/probe/report") or host not in (e.get("host") or ""):
            continue
        q = urllib.parse.parse_qs(urllib.parse.urlsplit(e["path"]).query)
        out.append((q.get("k", [""])[0], q.get("v", [""])[0]))
    return out


def wait_report(lab: labserver.LabServer, t0: float, key: str, timeout: float = 20, host: str = "") -> str | None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        for k, v in reports(lab, t0, host):
            if k == key:
                return v
        time.sleep(0.5)
    return None


class Scenario:
    def __init__(self, name: str, title: str):
        self.name, self.title, self.notes = name, title, []
        self.verdict = INFO

    def note(self, s: str) -> None:
        self.notes.append(s)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    run_lab.SERIAL = run_lab.pick_emulator()
    creds = json.loads((run_lab.LAB / "credentials.json").read_text(encoding="utf-8"))
    lab = labserver.LabServer().start()
    adb("install", "-r", "-t", str(run_lab.APK), timeout=180)
    adb("shell", "pm", "clear", PKG, check=False)
    run_lab.dns_sanity(lab)
    net = wait_network()
    print(f"에뮬레이터 {run_lab.SERIAL} · 실험실 서버 · 디버그 APK · 기본 네트워크 {net}")
    results: list[Scenario] = []

    wanted = set(sys.argv[1:])

    def run(s: Scenario, body) -> None:
        if wanted and s.name not in wanted:
            return
        try:
            body(s)
        except Exception as e:  # 시나리오의 결함 — 나머지는 잇는다
            s.verdict = FAIL
            s.note(f"예외: {e!r}")
        if s.verdict == FAIL:
            snap(s.name)
        print(f"[{s.verdict}] {s.name}: {s.notes[0] if s.notes else ''}", flush=True)
        results.append(s)

    def ready_ok(s: Scenario) -> None:
        setup(creds)
        t0 = time.time()
        if not launch():
            s.verdict = FAIL
            s.note("데우기가 끝나지 않았다(ready 없음)")
            return
        line = wait_log(r"^state OK ", 25)
        ev = events_since(lab, t0 - 3)
        exit_connect = [e for e in ev if e["kind"] == "proxy" and e["ev"] == "connect" and "exit.lab.test" in e["target"]]
        warm_at_proxy = [e for e in ev if e["kind"] == "proxy" and "warmup.iroiro.invalid" in e.get("target", "")]
        warm_dns = [e for e in ev if e["kind"] == "dns" and "warmup" in e["name"]]
        s.note(f"상태 줄: {line}" + ("" if line else f" (마지막 상태: {last_state()})"))
        s.note(f"출구 확인의 CONNECT {len(exit_connect)}건, 데우기가 프록시에 간 것 {len(warm_at_proxy)}건, 데우기 이름 질의 {len(warm_dns)}건")
        # 출구 확인도 일반화된 UA 로(사용자 결정, 2026-10-05 — 앞 판은 WebView 의 기본값을 실어 기기 모델이 나갔다).
        trace_ua = [{k.lower(): v for k, v in e["headers"].items()}.get("user-agent", "") for e in ev
                    if e["kind"] == "http" and e.get("path", "").startswith("/cdn-cgi/trace")]
        s.note(f"출구 확인의 User-Agent {trace_ua[:1]}")
        s.verdict = PASS if (line and "JP" in line and exit_connect and not warm_at_proxy and not warm_dns and trace_ua
                             and all("; wv)" not in u and "Android 10; K)" in u for u in trace_ua)) else FAIL

    def navigate(s: Scenario, url: str, title: str, host: str, via: str | None, scheme: str) -> None:
        t0 = time.time()
        type_address(url)
        got = wait_page(host) and wait_log(rf"^title tab=\d+ {re.escape(title)}$", 5)
        time.sleep(1.0)
        ev = events_since(lab, t0)
        hits = [e for e in ev if e["kind"] == "http" and e.get("ev") == "request" and host in (e.get("host") or "")]
        dns = [e for e in ev if e["kind"] == "dns" and host in e["name"]]
        connects = [e for e in ev if e["kind"] == "proxy" and e["ev"] == "connect" and host in e["target"]]
        s.note(f"제목 줄: {got}")
        s.note(f"목적지 요청 {len(hits)}건(X-Lab-Via={[h.get('via') for h in hits][:3]}), CONNECT {len(connects)}건, 기기 DNS {len(dns)}건")
        if via is None:
            s.verdict = PASS if got and hits and all(h.get("via") is None for h in hits) and not connects else FAIL
        elif scheme == "http":
            s.verdict = PASS if got and hits and all(h.get("via") == via for h in hits) and not dns else FAIL
        else:
            s.verdict = PASS if got and hits and connects and not dns else FAIL

    tok = secrets.token_hex(3)
    run(Scenario("ready", "설정 → 데우기 → 출구 확인(JP)"), ready_ok)
    run(Scenario("http", "주소창으로 http 쪽 — 중계·프록시를 거친다"),
        lambda s: navigate(s, f"http://b{tok}.lab.test:8080/probe/basic?t={tok}", "basic-ok", f"b{tok}.lab.test", "tls", "http"))
    run(Scenario("https", "주소창으로 https 쪽"),
        lambda s: navigate(s, f"https://c{tok}.lab.test:8443/probe/basic?t={tok}", "basic-ok", f"c{tok}.lab.test", "tls", "https"))
    run(Scenario("schemeless", "스킴 없이 친 이름은 https"),
        lambda s: navigate(s, f"e{tok}.lab.test:8443/probe/basic?t={tok}", "basic-ok", f"e{tok}.lab.test", "tls", "https"))

    def search(s: Scenario) -> None:
        t0 = time.time()
        type_address(f"lab search {tok}")
        time.sleep(4)
        ev = events_since(lab, t0)
        refused = [e for e in ev if e["kind"] == "proxy" and e["ev"] == "refused" and "www.google.com" in e.get("target", "")]
        # 기기의 DNS 로는 가릴 수 없다 — 에뮬레이터의 다른 앱(Play 서비스 등)이 구글 이름을 늘 묻는다. 프록시가 받은 것으로만 본다.
        s.note(f"프록시가 거절한 검색 엔진 CONNECT {len(refused)}건(실험실은 밖으로 잇지 않는다 — 프록시까지 갔다는 뜻)")
        s.verdict = PASS if refused else FAIL

    run(Scenario("search", "검색어는 검색 엔진으로 — 프록시를 거친다"), search)

    def webrtc(s: Scenario) -> None:
        t0 = time.time()
        type_address(f"http://r{tok}.lab.test:8080/probe/webrtc2?t={tok}")
        wait_log(r"^title tab=\d+ webrtc2-probe$", 15)
        time.sleep(8)
        stun = [e for e in events_since(lab, t0) if e["kind"] == "stun"]
        s.note(f"STUN 이 받은 것 {len(stun)}건(15길)")
        s.verdict = PASS if not stun else FAIL

    run(Scenario("webrtc", "WebRTC 가드(15길)"), webrtc)

    def headers(s: Scenario) -> None:
        t0 = time.time()
        type_address(f"https://x{tok}.lab.test:8443/probe/headers?t={tok}")
        wait_log(r"^title tab=\d+ headers-probe$", 15)
        time.sleep(2)
        ev = [e for e in events_since(lab, t0) if e["kind"] == "http" and f"x{tok}" in (e.get("host") or "")]
        values = {e["path"]: {k.lower(): v for k, v in e["headers"].items()}.get("x-requested-with") for e in ev}
        s.note(f"X-Requested-With: {values}")
        doc = [v for p, v in values.items() if p.startswith("/probe/headers")]
        ajax = [v for p, v in values.items() if "k=ajax" in p]
        s.verdict = PASS if doc and doc[0] == "" and ajax and ajax[0] == "XMLHttpRequest" else FAIL

    run(Scenario("headers", "X-Requested-With 는 빈 값, 쪽의 AJAX 머리는 그대로"), headers)

    def download(s: Scenario) -> None:
        t0 = time.time()
        type_address(f"http://d{tok}.lab.test:8080/probe/cookie?t={tok}")
        wait_log(r"^title tab=\d+ cookie-probe$", 15)
        type_address(f"http://d{tok}.lab.test:8080/probe/attach?t={tok}")
        asked = wait_log(r"^download asked ", 15)
        s.note(f"물음: {asked}")
        if not asked or not tap_text("받기"):
            s.verdict = FAIL
            s.note("받기 물음을 누르지 못했다")
            return
        done = wait_log(r"^download (DONE|FAILED|CANCELLED) ", 30)
        s.note(f"끝: {done}")
        ev = [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("path", "").startswith(f"/probe/attach?t={tok}")]
        # 첫 요청은 WebView 의 탐색(머리를 보고 내려받기로 넘긴다), 마지막은 앱의 받기다.
        heads = [{k.lower(): v for k, v in e["headers"].items()} for e in ev]
        s.note(f"첨부 요청 {len(ev)}건, via={[e.get('via') for e in ev]}, 마지막의 Cookie={heads[-1].get('cookie') if heads else None}, "
               f"Referer={heads[-1].get('referer') if heads else None}")
        listing = adb("shell", "ls", "-l", shlex.quote(f"/sdcard/Download/실험 보고서 {tok}.bin"), check=False).strip()
        s.note(f"파일: {listing[-90:]}")
        ok = bool(done and "DONE" in done and " 300000 " in f" {listing} " and len(ev) >= 2 and all(e.get("via") == "tls" for e in ev)
                  and f"labc={tok}" in (heads[-1].get("cookie") or "") and (heads[-1].get("referer") or "").endswith(f"/probe/cookie?t={tok}"))
        s.verdict = PASS if ok else FAIL

    run(Scenario("download", "내려받기 — 쿠키·Referer 를 싣고 중계로, 한국어 이름으로 내려받기 폴더에"), download)

    def service_worker(s: Scenario) -> None:
        type_address("https://sw.lab.test:8443/sw/reg.html")
        reg = wait_log(r"^title tab=\d+ sw-registered$", 15)
        s.note(f"등록: {reg}")
        t0 = time.time()
        if not launch():
            s.verdict = FAIL
            s.note("다시 띄운 뒤 ready 없음")
            return
        type_address(f"https://sw.lab.test:8443/sw/page.html?t={tok}")
        page = wait_log(r"^title tab=\d+ sw-page-(ok|direct)$", 15)
        net = [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("path", "").startswith(f"/sw-net/page.html?t={tok}")]
        s.note(f"새 프로세스의 첫 탐색: {page}, 서비스 워커의 요청이 닿은 수 {len(net)}")
        if net:
            h = {k.lower(): v for k, v in net[0]["headers"].items()}
            s.note(f"서비스 워커 요청의 User-Agent={h.get('user-agent')!r} sec-ch-ua={h.get('sec-ch-ua')!r}")
        s.verdict = PASS if reg and page and page.endswith("sw-page-ok") and net else FAIL

    run(Scenario("service-worker", "서비스 워커 — 새 프로세스의 첫 탐색(데우기 덕에 열린다)"), service_worker)

    def page_requests(host: str, since: float) -> list[dict]:
        return [e for e in events_since(lab, since) if e["kind"] == "http" and e.get("ev") == "request" and host in (e.get("host") or "")]

    def session_restore(s: Scenario) -> None:
        before = tab_count()
        opened = open_new_tab()
        type_address(f"http://n{tok}.lab.test:8080/probe/basic?t=S{tok}")
        got = wait_page(f"t=S{tok}")
        time.sleep(2)  # 세션은 1초 뒤에 적는다
        t0 = time.time()
        if not launch():
            s.verdict = FAIL
            s.note("다시 띄운 뒤 ready 없음")
            return
        restored = wait_log(r"^restored \d+$", 5)
        loaded = wait_log(rf"^finished tab=\d+ .*t=S{tok}", 20)
        hits = page_requests(f"n{tok}.lab.test", t0)
        after = tab_count()
        s.note(f"탭 {before} → 새 탭 {opened}/{got} → 다시 띄움: {restored}, 탭 {after}, 고른 탭: {loaded}, via={[h.get('via') for h in hits]}")
        s.verdict = PASS if (before and got and restored and restored == f"restored {before + 1}" and after == before + 1
                             and loaded and hits and all(h.get("via") == "tls" for h in hits)) else FAIL

    run(Scenario("session", "세션 되살리기 — 앱을 죽였다 띄우면 탭이 돌아오고 고른 탭이 (프록시로) 열린다"), session_restore)

    def tab_limit(s: Scenario) -> None:
        # 지금 탭에서 두 쪽(a1 → a2)을 열어 기록을 만들고, 새 탭 다섯을 연다 → 살아 있는 WebView 가 다섯을 넘어 이 탭이 접힌다.
        type_address(f"http://a{tok}.lab.test:8080/probe/basic?t=a1{tok}")
        wait_page(f"t=a1{tok}")
        type_address(f"http://a{tok}.lab.test:8080/probe/basic?t=a2{tok}")
        first = wait_page(f"t=a2{tok}")
        first_id = re.search(r"tab=(\d+)", first).group(1) if first else None
        frozen = None
        for i in range(5):
            adb("logcat", "-c", check=False)
            open_new_tab()
            # 새 탭을 고를 때 접는다 — 주소를 치면(type_address 가 logcat 을 비운다) 그 줄이 사라지므로 먼저 본다.
            frozen = frozen or wait_log(rf"^frozen tab={first_id}$", 2)
            type_address(f"http://l{i}{tok}.lab.test:8080/probe/basic?t=l{i}{tok}")
            wait_page(f"t=l{i}{tok}")
        # 탭 목록에서 접힌 탭(a2 주소)을 고른다 → 되살아나 a2 를 다시 열고, 뒤로 가면 a1 이 열린다(기록이 돌아왔다).
        adb("logcat", "-c", check=False)
        listed = tap_tabs_button() and tap_text(f"http://a{tok}.lab.test:8080/probe/basic?t=a2{tok}")
        thawed = wait_log(rf"^thawed tab={first_id}$", 15)
        reopened = wait_log(rf"^finished tab={first_id} .*t=a2{tok}", 20)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        back = wait_log(rf"^finished tab={first_id} .*t=a1{tok}", 20)
        s.note(f"접힘: {frozen}, 목록에서 고름 {listed}, 되살림: {thawed}, 다시 열림: {reopened is not None}, 뒤로: {back is not None}")
        s.verdict = PASS if first_id and frozen and listed and thawed and reopened and back else FAIL

    run(Scenario("tab-limit", "살아 있는 탭은 다섯 — 오래 안 본 탭을 접고, 고르면 기록째 되살린다"), tab_limit)

    def context_menu(s: Scenario) -> None:
        type_address(f"http://m{tok}.lab.test:8080/probe/links?t=m{tok}")
        got = wait_log(r"^title tab=\d+ links-probe$", 20)
        time.sleep(1)
        before = tab_count()
        t0 = time.time()
        adb("logcat", "-c", check=False)
        long_press(540, 640)
        opened = tap_text("새 탭에서 열기")
        background = wait_log(r"^background tab=\d+ ", 10)
        loaded = wait_log(rf"^finished tab=\d+ .*from=links", 20)
        hits = [h for h in page_requests(f"m{tok}.lab.test", t0) if "from=links" in h.get("path", "")]
        after = tab_count()
        s.note(f"쪽: {got}, 메뉴 {opened}, {background}, 탭 {before} → {after}, 새 탭의 요청 via={[h.get('via') for h in hits]}")
        s.verdict = PASS if (got and opened and background and loaded and before and after == before + 1
                             and hits and all(h.get("via") == "tls" for h in hits)) else FAIL

    run(Scenario("context-menu", "고리를 길게 눌러 새 탭에서 열기 — 지금 탭은 그대로, 새 탭은 프록시로"), context_menu)

    def site_data(s: Scenario) -> None:
        # 사이트 둘에 쿠키를 심고(lab.test 와 10.0.2.2), '사이트별 데이터' 에서 하나만 지운다 → 그 사이트만 쿠키가 없다.
        host_a = f"k{tok}.lab.test"
        type_address(f"http://{host_a}:8080/probe/cookie?t=K{tok}")
        a = wait_page(f"t=K{tok}")
        type_address(f"http://10.0.2.2:8080/probe/cookie?t=Q{tok}")
        b = wait_page(f"t=Q{tok}")
        adb("logcat", "-c", check=False)
        # -W 없이 — 디버그 화면이 다른 화면을 열고 바로 끝나면 am 이 그 끝을 60초 넘게 기다렸다.
        adb("shell", "am", "start", "-n", SETUP, "--es", "open", "sitedata")
        time.sleep(2)
        listed = tap_text(host_a) and tap_dialog_positive()
        deleted = wait_log(r"^site deleted ", 10)
        back = back_to_browser()
        t0 = time.time()
        type_address(f"http://{host_a}:8080/probe/basic?t=A{tok}")
        wait_page(f"t=A{tok}")
        type_address(f"http://10.0.2.2:8080/probe/basic?t=B{tok}")
        wait_page(f"t=B{tok}")
        ev = [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("ev") == "request"]

        def cookie_of(marker: str) -> str | None:
            hit = [e for e in ev if marker in e.get("path", "")]
            return {k.lower(): v for k, v in hit[0]["headers"].items()}.get("cookie") if hit else "(요청 없음)"

        ca, cb = cookie_of(f"t=A{tok}"), cookie_of(f"t=B{tok}")
        s.note(f"심기 {a is not None}/{b is not None}, 목록에서 지움 {listed}: {deleted}, 지운 사이트의 쿠키={ca}, 다른 사이트의 쿠키={cb}")
        s.verdict = PASS if (a and b and listed and deleted and back and "lab.test" in deleted and ca is None
                             and cb is not None and f"labc=Q{tok}" in cb) else FAIL

    run(Scenario("site-data", "사이트별 데이터 — 고른 사이트의 쿠키만 지운다"), site_data)

    def bookmark(s: Scenario) -> None:
        # 쪽을 북마크에 넣고 → 메뉴가 '북마크에서 빼기' 로 바뀌고 → 새 탭의 시작 화면에서 그 북마크를 누르면 열린다 → 뺀다.
        type_address(f"http://bk{tok}.lab.test:8080/probe/basic?t=bk{tok}")
        got = wait_page(f"t=bk{tok}")
        adb("logcat", "-c", check=False)
        added = tap_desc("메뉴") and tap_text("북마크에 추가")
        added_line = wait_log(rf"^bookmark added .*t=bk{tok}", 5)
        menu_says_remove = tap_desc("메뉴") and any(n["text"] == "북마크에서 빼기" for n in dump())
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.6)
        adb("logcat", "-c", check=False)
        open_new_tab()
        start = wait_log(r"^start shown$", 5)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")  # 새 탭이 주소창에 준 자판을 닫는다
        time.sleep(0.6)
        from_start = tap_text("basic-ok")
        opened = wait_page(f"t=bk{tok}")
        adb("logcat", "-c", check=False)
        removed = tap_desc("메뉴") and tap_text("북마크에서 빼기")
        removed_line = wait_log(rf"^bookmark removed .*t=bk{tok}", 5)
        s.note(f"쪽 {got is not None}, 넣음 {added}/{added_line is not None}, 메뉴가 '빼기' 로 {menu_says_remove}, 시작 화면 {start is not None}, "
               f"시작 화면에서 열림 {from_start}/{opened is not None}, 뺌 {removed}/{removed_line is not None}")
        s.verdict = PASS if (got and added and added_line and menu_says_remove and start and from_start and opened and removed
                             and removed_line) else FAIL

    run(Scenario("bookmark", "북마크 — 넣기·메뉴가 빼기로 바뀜·새 탭의 시작 화면에서 열기·빼기"), bookmark)

    def find(s: Scenario) -> None:
        type_address(f"http://fd{tok}.lab.test:8080/probe/find?t=fd{tok}")
        got = wait_page(f"t=fd{tok}")
        adb("logcat", "-c", check=False)
        opened = tap_desc("메뉴") and tap_text("쪽에서 찾기")
        time.sleep(0.8)
        adb("shell", "input", "text", "needle")
        first = wait_log(r"^find 1/3$", 8)
        adb("logcat", "-c", check=False)
        nxt = tap_desc("다음 찾기")
        second = wait_log(r"^find 2/3$", 5)
        closed = tap_desc("찾기 닫기")
        s.note(f"쪽 {got is not None}, 찾기 막대 {opened}, 첫 찾기 {first}, 다음 {nxt}/{second}, 닫기 {closed}")
        s.verdict = PASS if got and opened and first and nxt and second and closed else FAIL

    run(Scenario("find", "쪽에서 찾기 — needle 세 개 중 1/3 → 다음 2/3"), find)

    def failure(s: Scenario, extras: dict, expect: str) -> None:
        setup(creds, **extras)
        t0 = time.time()
        launch()
        state = wait_log(rf"^state {expect}", 25)
        type_address(f"http://f{tok}{expect.lower()[:3]}.lab.test:8080/probe/basic?t={tok}")
        time.sleep(4)
        hits = [e for e in events_since(lab, t0) if e["kind"] == "http" and f"f{tok}" in (e.get("host") or "")]
        dns = [e for e in events_since(lab, t0) if e["kind"] == "dns" and f"f{tok}" in e["name"]]
        s.note(f"상태: {state}, 목적지 요청 {len(hits)}건, 기기 DNS {len(dns)}건")
        s.verdict = PASS if state and not hits and not dns else FAIL

    run(Scenario("unreachable", "프록시에 닿지 못함 — 끊김, 직접 나가지 않는다"), lambda s: failure(s, {"port": "18999"}, "UNREACHABLE"))
    run(Scenario("auth-rejected", "틀린 비밀번호 — 계정, 직접 나가지 않는다"),
        lambda s: failure(s, {"pass": "wrong-" + secrets.token_hex(4)}, "AUTH_REJECTED"))

    def not_configured(s: Scenario) -> None:
        setup(creds, host="")
        # 앞 시나리오(계정 거절)의 프로세스가 하던 일(광고 목록 받기 등)이 섞이지 않게 먼저 끝내고 센다. 끊긴 TLS 손잡기(tls-fail)는 요청이 아니다.
        adb("shell", "am", "force-stop", PKG, check=False)
        time.sleep(1.0)
        t0 = time.time()
        adb("logcat", "-c", check=False)
        adb("shell", "am", "start", "-S", "-W", "-n", BROWSER)
        state = wait_log(r"^state NOT_CONFIGURED", 15)
        time.sleep(2)
        top = adb("shell", "dumpsys", "window", check=False)
        settings_on_top = any("SettingsActivity" in line for line in top.splitlines() if "mCurrentFocus" in line)
        ev = [e for e in events_since(lab, t0) if e["kind"] in ("proxy", "http") and e.get("ev") != "tls-fail"]
        s.note(f"상태: {state}, 설정 화면이 먼저 뜸={settings_on_top}, 실험실에 온 요청 {len(ev)}건 {[e.get('ev') for e in ev]}")
        s.verdict = PASS if state and settings_on_top and not ev else FAIL
        adb("shell", "input", "keyevent", "KEYCODE_BACK")

    run(Scenario("not-configured", "정하지 않음 — 설정이 먼저 뜨고 아무 데도 나가지 않는다"), not_configured)

    def off_direct(s: Scenario) -> None:
        setup(creds, enabled="0")
        t0 = time.time()
        launch()
        state = wait_log(r"^state OFF", 10)
        type_address(f"http://o{tok}.lab.test:8080/probe/basic?t={tok}")
        got = wait_page(f"o{tok}.lab.test")
        ev = events_since(lab, t0)
        hits = [e for e in ev if e["kind"] == "http" and f"o{tok}" in (e.get("host") or "")]
        dns = [e for e in ev if e["kind"] == "dns" and f"o{tok}" in e["name"]]
        s.note(f"상태: {state}, 쪽: {got}, 목적지 요청 via={[h.get('via') for h in hits]}, 기기 DNS {len(dns)}건(직접이니 있어야 한다)")
        s.verdict = PASS if state and got and hits and all(h.get("via") is None for h in hits) and dns else FAIL

    run(Scenario("off", "끄면 직접 — 사용자가 고른 것"), off_direct)

    def back_on(s: Scenario) -> None:
        setup(creds)
        launch()
        line = wait_log(r"^state OK ", 25)
        s.note(f"상태: {line}")
        s.verdict = PASS if line else FAIL

    run(Scenario("back-on", "다시 켜면 프록시로"), back_on)

    def toggle_switch(s: Scenario) -> None:
        # 켜진(OK) 상태에서 쪽을 하나 열고, 막대의 스위치로 끄고(묻지 않는다), 다시 켠다 — 그때마다 열린 탭이 새 길로 다시 불린다.
        host = f"t{tok}.lab.test"
        type_address(f"http://{host}:8080/probe/basic?t={tok}")
        got = wait_page(host)
        time.sleep(1.5)  # 뒤늦은 파비콘까지(끄기 전의 일)
        adb("logcat", "-c", check=False)
        t1 = time.time()  # 스위치를 누르는 때 — 이 뒤로는 모두 직접이어야 한다
        off_tapped = tap_switch()
        off = wait_log(r"^state OFF", 10)
        reloaded = wait_log(r"^reloaded for path", 10)
        time.sleep(2)
        direct = page_requests(host, t1)
        t2 = time.time()
        adb("logcat", "-c", check=False)
        on_tapped = tap_switch()
        on = wait_log(r"^state OK ", 25)
        time.sleep(1)
        proxied = page_requests(host, t2)
        doc_off = [h for h in direct if h.get("path", "").startswith("/probe/basic")]
        doc_on = [h for h in proxied if h.get("path", "").startswith("/probe/basic")]
        s.note(f"끄기: {off} / {reloaded}, 끈 뒤의 요청 {[(h.get('path'), h.get('via')) for h in direct]} — "
               f"켜기: {on}, 켠 뒤의 요청 {[(h.get('path'), h.get('via')) for h in proxied]}")
        s.verdict = PASS if (got and off_tapped and off and reloaded and doc_off and all(h.get("via") is None for h in direct)
                             and on_tapped and on and doc_on and all(h.get("via") == "tls" for h in proxied)) else FAIL

    run(Scenario("toggle-switch", "막대의 스위치로 끄고 켜기(묻지 않는다) — 열린 탭이 새 길(직접 → 프록시)로 다시 불린다"), toggle_switch)

    def toggle_unreachable(s: Scenario) -> None:
        setup(creds, port="18999")
        launch()
        state = wait_log(r"^state UNREACHABLE", 25)
        adb("logcat", "-c", check=False)
        off_tapped = tap_switch()
        off = wait_log(r"^state OFF", 10)
        host = f"u{tok}.lab.test"
        t1 = time.time()
        type_address(f"http://{host}:8080/probe/basic?t={tok}")
        got = wait_page(host)
        time.sleep(1)
        hits = page_requests(host, t1)
        s.note(f"상태: {state} → {off}, 쪽: {got}, via={[h.get('via') for h in hits]}")
        s.verdict = PASS if state and off_tapped and off and got and hits and all(h.get("via") is None for h in hits) else FAIL

    run(Scenario("toggle-unreachable", "프록시에 닿지 못할 때 스위치로 끄면 직접"), toggle_unreachable)

    def library_rows(text: str) -> list[dict]:
        """방문 기록·북마크 화면에서 [text] 가 든 줄(찾기 칸 자신은 뺀다)."""
        return [n for n in dump() if text in n["text"] and not n["cls"].endswith("EditText")]

    def library_search(text: str) -> bool:
        edits = [n for n in dump() if n["cls"].endswith("EditText")]
        if not edits:
            return False
        tap(edits[0]["x"], edits[0]["y"])
        time.sleep(0.4)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        for _ in range(40):
            adb("shell", "input", "keyevent", "KEYCODE_DEL")
        adb("shell", "input", "text", text)
        time.sleep(1.2)
        return True

    def history(s: Scenario) -> None:
        # 중계가 막는 동안(불통) 연 쪽은 방문 기록에 없다 — 앱을 다시 띄우지 않고 그 자리에서 본다(되살린 탭이 나중에 다른 길로 열면 정당하게 적힌다).
        setup(creds, port="18999")
        launch()
        wait_log(r"^state UNREACHABLE", 25)
        type_address(f"http://hb{tok}.lab.test:8080/probe/basic?t=hb{tok}")
        blocked = wait_page(f"t=hb{tok}")
        time.sleep(1)
        adb("logcat", "-c", check=False)
        opened = tap_desc("메뉴") and tap_text("방문 기록")
        time.sleep(1.5)
        searched = library_search(f"hb{tok}")
        blocked_rows = library_rows(f"hb{tok}")
        # 앞서 연 쪽(북마크 시나리오) — 찾아서 누르면 결과로 돌아가 지금 탭에서 연다(막힌 길이라 열리지는 않아도 그 주소로 간다).
        searched = searched and library_search(f"bk{tok}")
        rows = library_rows(f"bk{tok}")
        if rows:
            tap(rows[0]["x"], rows[0]["y"])
        line = wait_log(rf"^library open .*t=bk{tok}", 8)
        went = wait_page(f"t=bk{tok}")
        s.note(f"막힌 쪽 {blocked is not None}, 화면 {opened}, 찾기 {searched}, 막힌 쪽의 줄 {len(blocked_rows)}개, 앞서 연 쪽의 줄 {len(rows)}개, "
               f"{line}, 그 주소로 감 {went is not None}")
        s.verdict = PASS if blocked and opened and searched and not blocked_rows and rows and line and went else FAIL

    run(Scenario("history", "방문 기록 — 막힌 쪽은 적지 않고, 찾아서 누르면 지금 탭에서 연다"), history)

    ads_page = f"http://pg{tok}.lab.test:8080/probe/ads?t={tok}"

    def adblock(s: Scenario) -> None:
        # 실험실의 목록을 (프록시로) 받아 엔진을 짓고, 광고 쪽을 연다 → 광고 스크립트·광고 그림은 실험실에 닿지 않고, 예외와 보통 그림은 닿는다.
        setup(creds)
        launch()
        wait_log(r"^state OK ", 25)
        t0 = time.time()
        adb("logcat", "-c", check=False)
        adb("shell", "am", "start", "-W", "-n", SETUP, "--es", "adblock", "1", "--es", "adblock_list", "http://lists.lab.test:8080/lists/test.txt")
        ready = wait_log(r"^adblock ready [1-9]", 20)
        fetched = [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("path", "").startswith("/lists/test.txt")]
        type_address(ads_page)
        got = wait_page(f"probe/ads?t={tok}")
        time.sleep(3)
        ev = [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("ev") == "request"]

        def hits(path: str) -> int:
            return len([e for e in ev if e.get("path", "").startswith(path)])

        lines = log_lines()
        start = [l for l in lines if l.startswith("adblock start css")]
        generic = [l for l in lines if l.startswith("adblock generic css") and not l.startswith("adblock generic css 0 ")]
        blocked = [l for l in lines if l.startswith("adblock blocked")]
        s.note(f"엔진 {ready}, 목록 받기 via={[e.get('via') for e in fetched]}, 쪽 {got is not None}, 광고 스크립트 {hits(f'/ad.js?t={tok}')} · "
               f"예외 스크립트 {hits(f'/allowed.js?t={tok}')} · 광고 그림 {hits(f'/adbanner.gif?t={tok}')} · 보통 그림 {hits(f'/ok.gif?t={tok}')}, "
               f"막음 {len(blocked)}줄, 시작 숨김 {start[:1]}, 일반 숨김 {generic[:1]}")
        s.verdict = PASS if (ready and fetched and all(e.get("via") == "tls" for e in fetched) and got and hits(f"/ad.js?t={tok}") == 0
                             and hits(f"/allowed.js?t={tok}") >= 1 and hits(f"/adbanner.gif?t={tok}") == 0 and hits(f"/ok.gif?t={tok}") >= 1
                             and start and generic) else FAIL

    run(Scenario("adblock", "광고 차단 — 목록을 프록시로 받아 광고 요청을 막고 요소를 숨긴다(예외는 통과)"), adblock)

    def adblock_site(s: Scenario) -> None:
        # 이 사이트에서 끄면 광고 스크립트가 닿고, 다시 켜면 다시 막힌다.
        t1 = time.time()
        adb("logcat", "-c", check=False)
        off = tap_desc("메뉴") and tap_prefix("이 사이트에서 광고 차단 끄기")
        reloaded_off = wait_page(f"probe/ads?t={tok}")
        time.sleep(2)
        while_off = [e for e in events_since(lab, t1) if e["kind"] == "http" and e.get("path", "").startswith(f"/ad.js?t={tok}")]
        t2 = time.time()
        adb("logcat", "-c", check=False)
        on = tap_desc("메뉴") and tap_text("이 사이트에서 광고 차단 켜기")
        reloaded_on = wait_page(f"probe/ads?t={tok}")
        time.sleep(2)
        while_on = [e for e in events_since(lab, t2) if e["kind"] == "http" and e.get("path", "").startswith(f"/ad.js?t={tok}")]
        s.note(f"끄기 {off}/{reloaded_off is not None} → 광고 스크립트 {len(while_off)}건, 켜기 {on}/{reloaded_on is not None} → {len(while_on)}건")
        s.verdict = PASS if off and reloaded_off and while_off and on and reloaded_on and not while_on else FAIL

    run(Scenario("adblock-site", "광고 차단 — 사이트별로 끄고 다시 켜기"), adblock_site)

    media_page = f"http://md{tok}.lab.test:8080/probe/media?t={tok}"

    def media_open() -> tuple[bool, str | None]:
        type_address(media_page)
        # 막 부팅한 에뮬레이터에서는 첫 쪽이 20초를 넘긴 적이 있다.
        got = wait_page(f"probe/media?t={tok}", 40)
        found = wait_log(r"^media found HLS .*fmp4\.m3u8", 10) and wait_log(r"^media found VIDEO .*clip\.mp4", 10)
        return bool(got and found), found

    def media_hls(s: Scenario) -> None:
        # 쪽의 스크립트가 받은 마스터 재생목록 → 찾은 미디어 → 화질 고르기 → 조각 셋을 프록시로 받아 한 파일(.ts)로.
        ok, found = media_open()
        t0 = time.time()
        adb("logcat", "-c", check=False)
        opened = tap_desc("찾은 미디어") and tap_text("HLS · master.m3u8")
        variants = wait_log(r"^media variants ", 10)
        picked = tap_text("1280x720 · 2.5 Mbps")
        done = wait_log(r"^download (DONE|FAILED|CANCELLED) .*parts=", 30)
        segs = [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("path", "").startswith("/hls/v2/seg")]
        refs = [{k.lower(): v for k, v in e["headers"].items()}.get("referer") for e in segs]
        s.note(f"찾음 {ok}, 고름 {opened}/{variants}/{picked}, {done}, 조각 요청 {len(segs)}건 via={[e.get('via') for e in segs]}, Referer={refs[:1]}")
        s.verdict = PASS if (ok and opened and variants and picked and done and "DONE" in done and ".ts bytes=56400 parts=3/3" in done
                             and len(segs) == 3 and all(e.get("via") == "tls" for e in segs) and all(refs)) else FAIL

    run(Scenario("media-hls", "HLS — 찾은 미디어에서 화질을 골라 조각을 프록시로 받아 한 .ts 로"), media_hls)

    def media_fmp4(s: Scenario) -> None:
        adb("logcat", "-c", check=False)
        opened = tap_desc("찾은 미디어") and tap_text("HLS · fmp4.m3u8")
        confirmed = tap_dialog_positive(10)
        done = wait_log(r"^download (DONE|FAILED|CANCELLED) .*parts=", 30)
        s.note(f"고름 {opened}, 받기 {confirmed}, {done}")
        # 초기 조각도 잇는 조각 하나로 센다(조각 4/4).
        s.verdict = PASS if opened and confirmed and done and "DONE" in done and ".mp4 bytes=16000 parts=4/4" in done else FAIL

    run(Scenario("media-fmp4", "HLS(fMP4) — 초기 조각 + 조각들을 한 .mp4 로"), media_fmp4)

    def media_refuse(s: Scenario) -> None:
        adb("logcat", "-c", check=False)
        live = tap_desc("찾은 미디어") and tap_text("HLS · live.m3u8")
        live_line = wait_log(r"^media refused 실시간", 10)
        s.note(f"실시간 {live}/{live_line}")
        s.verdict = PASS if live and live_line else FAIL

    run(Scenario("media-refuse", "HLS — 실시간 방송은 받지 않는다"), media_refuse)

    def media_aes(s: Scenario) -> None:
        # AES-128 로 실제로 잠근 조각 셋(열쇠 00..0F, IV = 순번) → 풀어서 한 .ts — 내용이 원래 무늬와 같아야 한다(사용자 결정, 2026-10-05).
        adb("logcat", "-c", check=False)
        opened = tap_desc("찾은 미디어") and tap_text("HLS · enc.m3u8")
        confirmed = tap_dialog_positive(10)
        done = wait_log(r"^download (DONE|FAILED|PAUSED) .*parts=", 30)
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        expect = b"".join(labserver.hls_bytes(f"/hls/enc/seg{i}.ts") for i in range(3))
        s.note(f"고름 {opened}, 받기 {confirmed}, {done}, 내용 {'같음' if data == expect else '다름'}({len(data or b'')} 바이트)")
        s.verdict = PASS if opened and confirmed and done and "DONE" in done and "parts=3/3" in done and data == expect else FAIL

    run(Scenario("media-aes", "HLS(AES-128) — 잠긴 조각을 풀어 한 .ts 로(내용 확인)"), media_aes)

    def media_file(s: Scenario) -> None:
        adb("logcat", "-c", check=False)
        opened = tap_desc("찾은 미디어") and tap_text("영상 · clip.mp4")
        confirmed = tap_dialog_positive(10)
        done = wait_log(r"^download (DONE|FAILED|CANCELLED) .*clip", 30)
        s.note(f"고름 {opened}, 받기 {confirmed}, {done}")
        s.verdict = PASS if opened and confirmed and done and "DONE" in done and "bytes=50000" in done else FAIL

    run(Scenario("media-file", "보통 영상 파일 — 찾은 미디어에서 받기"), media_file)

    def wait_all(patterns: list[str], timeout: float = 40) -> list[str | None]:
        """여러 줄을 함께 기다린다(받기 둘이 끝나는 차례는 정해지지 않는다)."""
        rxs = [re.compile(p) for p in patterns]
        found: list[str | None] = [None] * len(rxs)
        deadline = time.time() + timeout
        while time.time() < deadline and not all(found):
            for line in log_lines():
                for k, rx in enumerate(rxs):
                    if found[k] is None and rx.search(line):
                        found[k] = line
            time.sleep(0.5)
        return found

    def media_dash(s: Scenario) -> None:
        # 쪽이 받은 DASH 목록 → 찾은 미디어 → 영상 화질 → 영상·소리를 받아 합치려 한다. 실험실의 가짜 조각은 MP4 가 아니라 합치지 못한다 —
        # 앞 판처럼 영상(.mp4)과 소리(.m4a)를 따로 된 파일로 남겨야 한다(진짜 미디어의 합치기는 merge-* 시나리오).
        t0 = time.time()
        adb("logcat", "-c", check=False)
        before = downloads_listing()
        opened = tap_desc("찾은 미디어") and tap_text("DASH · vod.mpd")
        variants = wait_log(r"^media variants ", 10)
        picked = tap_text("1280x720 · 2.5 Mbps")
        done = wait_log(r"^download (DONE|FAILED|PAUSED) .*parts=8/8", 40)
        fell_back = wait_log(r"^download merge-failed ", 3)
        after = downloads_listing()
        new = {k: v for k, v in after.items() if k not in before}
        segs = [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("path", "").startswith("/dash/vod/")]
        s.note(f"고름 {opened}/{variants}/{picked}, {done}, 합치기 실패 → 따로: {fell_back is not None}, 새 파일 {new}, 조각 요청 {len(segs)}건 "
               f"via={sorted(set(e.get('via') for e in segs))}")
        s.verdict = PASS if (opened and variants and picked and done and "DONE" in done and "bytes=23000" in done and fell_back
                             and sorted(new.values()) == [7000, 16000] and len(segs) == 8 and all(e.get("via") == "tls" for e in segs)) else FAIL

    run(Scenario("media-dash", "DASH — 영상·소리를 받아 합치려다 못하면 따로 된 파일로(가짜 조각)"), media_dash)

    def media_dash_refuse(s: Scenario) -> None:
        adb("logcat", "-c", check=False)
        live = tap_desc("찾은 미디어") and tap_text("DASH · live.mpd")
        live_line = wait_log(r"^media refused 실시간", 10)
        drm = tap_desc("찾은 미디어") and tap_text("DASH · drm.mpd")
        drm_line = wait_log(r"^media refused DRM", 10)
        s.note(f"실시간 {live}/{live_line}, DRM {drm}/{drm_line}")
        s.verdict = PASS if live and live_line and drm and drm_line else FAIL

    run(Scenario("media-dash-refuse", "DASH — 실시간 방송과 DRM 은 받지 않는다"), media_dash_refuse)

    # ── 7단계: 결정 반영(2026-10-05) ────────────────────────────────────────────────────────────────────────────

    def resume_hls(s: Scenario) -> None:
        # 둘째 조각이 세 번 503 → 조각마다 세 번 해 보고 멈춤(받은 조각은 남는다) → 목록에서 이어받기 → 첫 조각을 다시 받지 않고 끝까지.
        t0 = time.time()
        adb("logcat", "-c", check=False)
        opened = tap_desc("찾은 미디어") and tap_text("HLS · flaky.m3u8")
        confirmed = tap_dialog_positive(10)
        paused = wait_log(r"^download PAUSED .*parts=1/3", 40)
        resumed = resume_in_list(done_name(paused) or "media-probe")
        done = wait_log(r"^download (DONE|FAILED|PAUSED) .*parts=3/3", 30)
        back_to_browser()
        segs = [e["path"].split("?")[0] for e in events_since(lab, t0) if e["kind"] == "http" and e.get("path", "").startswith("/hls/flaky/seg")]
        counts = {p: segs.count(p) for p in sorted(set(segs))}
        s.note(f"고름 {opened}/{confirmed}, 멈춤 {paused}, 이어받기 {resumed}, {done}, 조각 요청 {counts}")
        s.verdict = PASS if (opened and confirmed and paused and resumed and done and "DONE" in done and "bytes=56400" in done
                             and counts.get("/hls/flaky/seg0.ts") == 1 and counts.get("/hls/flaky/seg1.ts") == 4
                             and counts.get("/hls/flaky/seg2.ts") == 1) else FAIL

    def app_requests(t0: float, prefix: str) -> list[dict]:
        """앱의 HTTP 가 낸 요청만(주소창으로 연 WebView 의 탐색 — Accept: text/html — 은 뺀다)."""
        return [e for e in events_since(lab, t0) if e["kind"] == "http" and e.get("path", "").startswith(prefix)
                and "text/html" not in {k.lower(): v for k, v in e["headers"].items()}.get("accept", "")]

    def resume_in_list(name: str) -> bool:
        """메뉴 → 내려받기 → 그 줄 → '이어받기'."""
        ok = tap_desc("메뉴") and tap_text("내려받기")
        time.sleep(1)
        ok = ok and tap_text(name, 10)
        return bool(ok and tap_dialog_positive(10))

    run(Scenario("resume-hls", "HLS — 조각이 세 번 실패하면 멈추고, 이어받기로 받은 조각 뒤부터"), resume_hls)

    def resume_auto(s: Scenario) -> None:
        # 첫 요청이 100,000 바이트에서 끊긴다 → 앱이 스스로 Range(+If-Range)로 이어 받아 300,000 바이트를 온전히.
        t0 = time.time()
        type_address(f"http://f{tok}.lab.test:8080/probe/flaky?t={tok}&mode=auto")
        asked = wait_log(r"^download asked ", 15)
        tapped = tap_dialog_positive(10)
        done = wait_log(r"^download (DONE|FAILED|PAUSED) flaky-auto", 40)
        retry = [line for line in log_lines() if line.startswith("download retry flaky-auto")]
        reqs = app_requests(t0, "/probe/flaky")
        ranges = [{k.lower(): v for k, v in e["headers"].items()}.get("range") for e in reqs]
        if_range = [{k.lower(): v for k, v in e["headers"].items()}.get("if-range") for e in reqs]
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        s.note(f"물음 {asked is not None}/{tapped}, {done}, 다시 {retry[:1]}, 요청 {len(reqs)}건 Range={ranges} If-Range={if_range}, "
               f"내용 {'같음' if data == labserver.flaky_bytes() else '다름'}")
        s.verdict = PASS if (done and "DONE" in done and "bytes=300000" in done and len(reqs) == 2 and ranges[1] == "bytes=100000-"
                             and if_range[1] == labserver.FLAKY_ETAG and data == labserver.flaky_bytes()) else FAIL

    run(Scenario("resume-auto", "끊긴 내려받기를 스스로 이어 받는다(Range·If-Range)"), resume_auto)

    def resume_pause(s: Scenario) -> None:
        # 끊긴 뒤 503 둘 — 나아감 없이 세 번 실패하면 멈춤(받은 100,000 바이트는 남는다) → 목록에서 이어받기 → 그 뒤부터.
        t0 = time.time()
        type_address(f"http://g{tok}.lab.test:8080/probe/flaky?t={tok}&mode=pause")
        asked = wait_log(r"^download asked ", 15)
        tapped = tap_dialog_positive(10)
        paused = wait_log(r"^download PAUSED flaky-pause", 40)
        notice = adb("shell", "dumpsys", "notification", "--noredact", check=False)
        has_action = f"flaky-pause-{tok}.bin" in notice and "이어받기" in notice
        s.note(f"멈춤 알림의 '이어받기' 단추 {has_action}")
        resumed = resume_in_list(done_name(paused) or f"flaky-pause-{tok}.bin")
        done = wait_log(r"^download (DONE|FAILED|PAUSED) flaky-pause.*bytes=300000", 30)
        back_to_browser()
        reqs = app_requests(t0, "/probe/flaky")
        ranges = [{k.lower(): v for k, v in e["headers"].items()}.get("range") for e in reqs]
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        s.note(f"물음 {asked is not None}/{tapped}, 멈춤 {paused}, 이어받기 {resumed}, {done}, 요청 {len(reqs)}건 Range={ranges}, "
               f"내용 {'같음' if data == labserver.flaky_bytes() else '다름'}")
        s.verdict = PASS if (paused and "bytes=100000" in paused and has_action and resumed and done and "DONE" in done and len(reqs) == 4
                             and ranges[3] == "bytes=100000-" and data == labserver.flaky_bytes()) else FAIL

    run(Scenario("resume-pause", "나아감 없이 세 번 실패하면 멈추고, 사용자가 이어받기"), resume_pause)

    def resume_restart(s: Scenario) -> None:
        # 받는 도중 앱이 죽었다 — 다시 띄우면 목록에 '멈춤(앱이 멈춰 중단됐습니다)' 으로 남고, 이어받으면 받은 데서부터(Range).
        t0 = time.time()
        n = 8_000_000
        type_address(f"http://sl{tok}.lab.test:8080/probe/slow?t={tok}&n={n}&rate=1000000")
        asked = wait_log(r"^download asked ", 15)
        tapped = tap_dialog_positive(10)
        time.sleep(4)
        adb("shell", "am", "force-stop", PKG, check=False)
        time.sleep(1)
        relaunched = launch()
        name = f"slow-{tok}.bin"
        listed = tap_desc("메뉴") and tap_text("내려받기")
        time.sleep(1)
        rows = [x["text"] for x in dump() if x["text"]]
        paused_row = any("앱이 멈춰 중단됐습니다" in r for r in rows)
        resumed = tap_text(name, 10) and tap_dialog_positive(10)
        done = wait_log(r"^download (DONE|FAILED|PAUSED) slow-", 40)
        back_to_browser()
        reqs = app_requests(t0, "/probe/slow")
        ranges = [{k.lower(): v for k, v in e["headers"].items()}.get("range") for e in reqs]
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        want = bytes((i * 11 + 1) & 0xFF for i in range(n))
        s.note(f"물음 {asked is not None}/{tapped}, 다시 띄움 {relaunched}, 목록 {listed} 멈춤 줄 {paused_row}, 이어받기 {resumed}, {done}, "
               f"요청 {len(reqs)}건 Range={ranges}, 내용 {'같음' if data == want else '다름'}")
        s.verdict = PASS if (relaunched and paused_row and resumed and done and "DONE" in done and len(reqs) == 2 and ranges[1]
                             and ranges[1] != "bytes=0-" and data == want) else FAIL

    run(Scenario("resume-restart", "받는 도중 앱이 죽어도 목록에 멈춤으로 남고, 이어받으면 받은 데서부터"), resume_restart)

    def ua(s: Scenario) -> None:
        # 일반화된 UA(사용자 결정, 2026-10-05) — 글은 크롬의 줄인 꼴(wv·모델 없음), 힌트에 WebView 상표·모델이 없다.
        t0 = time.time()
        host = f"u{tok}.lab.test"
        type_address(f"https://{host}:8443/probe/uach?t={tok}")
        wait_page(f"probe/uach?t={tok}", 20)
        js_raw = wait_report(lab, t0, "uach", 15, host)
        ev = [e for e in events_since(lab, t0) if e["kind"] == "http" and host in (e.get("host") or "")]
        page = next((e for e in ev if e["path"].startswith("/probe/uach")), None)
        rep = next((e for e in ev if e["path"].startswith("/probe/report")), None)
        h = {k.lower(): v for k, v in (page or {}).get("headers", {}).items()}
        rh = {k.lower(): v for k, v in (rep or {}).get("headers", {}).items()}
        js = json.loads(js_raw) if js_raw else {}
        ua_header = h.get("user-agent") or ""
        worker_ua = wait_report(lab, t0, "worker-ua", 8, host)
        s.note(f"전용 워커의 navigator.userAgent={worker_ua!r}")
        s.note(f"UA={ua_header!r} · sec-ch-ua={h.get('sec-ch-ua')!r} · 보고의 model={rh.get('sec-ch-ua-model')!r} "
               f"full={rh.get('sec-ch-ua-full-version-list')!r} · JS brands={js.get('brands')} model={js.get('model')!r} "
               f"platformVersion={js.get('platformVersion')!r}")
        good_ua = "; wv)" not in ua_header and "Android 10; K)" in ua_header and re.search(r"Chrome/\d+\.0\.0\.0 Mobile Safari", ua_header)
        no_wv = lambda v: v is not None and "WebView" not in v  # noqa: E731
        s.verdict = PASS if (good_ua and no_wv(h.get("sec-ch-ua")) and js.get("ua") == ua_header and js.get("brands")
                             and not any("WebView" in b for b in js["brands"]) and js.get("model") == ""
                             and rh.get("sec-ch-ua-model") == '""' and no_wv(rh.get("sec-ch-ua-full-version-list"))) else FAIL

    run(Scenario("ua", "User-Agent·클라이언트 힌트 — 크롬의 줄인 꼴, WebView 상표·기기 모델 없음"), ua)

    def drm(s: Scenario) -> None:
        # DRM 재생 허용(사용자 결정, 2026-10-05) — 쪽이 Widevine 을 청하면 보호된 미디어 권한을 준다.
        t0 = time.time()
        host = f"k{tok}.lab.test"
        type_address(f"https://{host}:8443/probe/eme?t={tok}")
        granted = wait_log(r"^protected-media granted", 15)
        time.sleep(4)
        got = reports(lab, t0, host)
        s.note(f"권한 {granted}, 쪽의 보고 {got}")
        access = any(k == "eme-access" for k, _ in got)
        unsupported = any(k == "eme-error" and v.startswith("NotSupportedError") for k, v in got)
        s.verdict = PASS if granted and access else (INFO if unsupported and not access else FAIL)

    run(Scenario("drm", "DRM(Widevine) — 보호된 미디어 권한을 준다"), drm)

    def blob(s: Scenario, mode: str, must: bool, hook: bool = False) -> None:
        # 쪽이 만든 파일(blob:) — 고리를 누르는 그 자리에서 보이지 않는 세계가 붙잡고, 앱이 물으면 덩이로 넘긴다. [hook] 이면 처음엔 넘겨받지
        # 못하고(쪽의 CSP·곧바로 거둔 본문 밖의 고리), '준비하고 다시 열기' 로 그 탭에 blob 갈고리를 건 뒤 다시 열린 쪽에서 받는다.
        adb("logcat", "-c", check=False)
        type_address(f"http://bl{mode}{tok}.lab.test:8080/probe/blob?t={tok}&mode={mode}")
        armed = None
        if hook:
            none = wait_log(r"^blob none", 20)
            if none and tap_dialog_positive(10):
                armed = wait_log(r"^blob armed ", 10)
            s.note(f"처음엔 넘겨받지 못함 {none is not None}, 갈고리 {armed}")
        # 본문 밖의 고리는 누름이 보이지 않아 download 속성(이름)을 모른다 — WebView 도 그 이름을 알려 주지 않는다. 이름은 'download'.
        asked = wait_log(r"^download asked ", 20)
        tapped = tap_dialog_positive(10) if asked else False
        done = wait_log(r"^download (DONE|FAILED|CANCELLED) ", 30) if tapped else None
        gone = wait_log(r"^blob none", 1)
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        s.note(f"물음 {asked}, {done}, 없음 {gone is not None}, 내용 {'같음' if data == labserver.blob_bytes() else '다름'}")
        ok = bool(done and "DONE" in done and "bytes=300000" in done and data == labserver.blob_bytes() and (armed or not hook))
        s.verdict = PASS if ok else (FAIL if must else INFO)

    run(Scenario("blob", "blob: — 누른 뒤 곧바로 주소를 거두는 쪽(본문에 붙인 고리)"), lambda s: blob(s, "attached", True))
    run(Scenario("blob-detached", "blob: — 본문 밖의 고리, 40초 뒤 거둠(FileSaver.js 꼴)"), lambda s: blob(s, "detached", True))
    run(Scenario("blob-csp", "blob: — 쪽의 CSP(default-src 'self')가 읽기를 막는다 → 갈고리를 걸고 다시 열어 받는다"),
        lambda s: blob(s, "csp", True, hook=True))
    run(Scenario("blob-revoked", "blob: — 본문 밖의 고리를 누르고 곧바로 거둔다 → 갈고리를 걸고 다시 열어 받는다"),
        lambda s: blob(s, "detachedrevoke", True, hook=True))

    def maker(s: Scenario) -> None:
        # 진짜 미디어(H.264·AAC 의 조각난 MP4, VP9·Opus WebM)를 기기의 MediaRecorder 로 만든다 — 처음 한 번(local/lab/media).
        names = ("v.mp4", "a.mp4", "av.mp4")
        if labmedia.have(*names):
            s.verdict = PASS
            s.note("이미 있다: " + ", ".join(f"{n} {(labmedia.MEDIA / n).stat().st_size}" for n in names))
            return
        t0 = time.time()
        type_address(f"http://mk{tok}.lab.test:8080/lab/maker?t={tok}")
        wait_page("lab/maker", 20)
        time.sleep(1)
        tap(*screen_center(0.3))
        done = wait_report(lab, t0, "maker-done", 60)
        made = [v for k, v in reports(lab, t0) if k == "made"]
        s.note(f"만듦 {made}, 끝 {done}")
        s.verdict = PASS if done and labmedia.have(*names) else FAIL

    run(Scenario("maker", "시험 미디어 만들기(기기의 MediaRecorder)"), maker)

    def merged(s: Scenario, label: str, variant: str, want_seconds: float) -> None:
        """찾은 미디어의 [label] → 화질 [variant] → 영상과 따로 된 소리를 받아 한 MP4 — 꺼내 트랙을 센다."""
        adb("logcat", "-c", check=False)
        opened = tap_desc("찾은 미디어") and tap_text(label)
        variants = wait_log(r"^media variants ", 10)
        picked = tap_text(variant)
        merged_line = wait_log(r"^download (merged|merge-failed) ", 60)
        done = wait_log(r"^download (DONE|FAILED|PAUSED) ", 10)
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        info = labmedia.mp4_info(data) if data else {}
        kinds = sorted(t["handler"] for t in info.get("tracks", []))
        s.note(f"고름 {opened}/{variants}/{picked}, {merged_line}, {done}, 담긴 것 {info}")
        s.verdict = PASS if (merged_line and merged_line.startswith("download merged") and done and "DONE" in done and ".mp4 " in done
                             and kinds == ["soun", "vide"] and info.get("duration", 0) >= want_seconds) else FAIL

    def real_open() -> bool:
        type_address(f"http://rm{tok}.lab.test:8080/probe/realmedia?t={tok}")
        got = wait_page(f"probe/realmedia?t={tok}", 40)
        found = wait_log(r"^media found DASH .*real/dash\.mpd", 10) and wait_log(r"^media found HLS .*real/ts\.m3u8", 10)
        return bool(got and found)

    def merge_dash(s: Scenario) -> None:
        if not labmedia.have("v.mp4", "a.mp4") or not real_open():
            s.verdict = FAIL
            s.note("시험 미디어나 쪽이 없다")
            return
        merged(s, "DASH · dash.mpd", "320x240 · 600 kbps", 5.0)

    run(Scenario("merge-dash", "DASH(진짜 H.264·AAC) — 영상과 소리를 한 MP4 로"), merge_dash)
    def merge_hls(s: Scenario, label: str) -> None:
        if not labmedia.have("v.mp4", "a.mp4") or not real_open():
            s.verdict = FAIL
            s.note("시험 미디어나 쪽이 없다")
            return
        merged(s, label, "320x240 · 0.7 Mbps", 5.0)

    run(Scenario("merge-hls", "HLS fMP4 + 따로 된 소리 — 한 MP4 로"), lambda s: merge_hls(s, "HLS · fmp4.m3u8"))
    run(Scenario("merge-ts", "HLS TS + 묶음 AAC(ID3 시각) — 한 MP4 로"), lambda s: merge_hls(s, "HLS · ts.m3u8"))

    def merge_webm(s: Scenario) -> None:
        # DASH 의 WebM(VP9 영상 + Opus 소리, 파일 하나씩) → 한 WebM(그릇은 코덱이 정한다).
        if not labmedia.have("v.webm", "a.webm") or not real_open():
            s.verdict = FAIL
            s.note("시험 미디어나 쪽이 없다")
            return
        adb("logcat", "-c", check=False)
        opened = tap_desc("찾은 미디어") and tap_text("DASH · dash-webm.mpd")
        variants = wait_log(r"^media variants ", 10)
        picked = tap_text("320x240 · 600 kbps")
        merged_line = wait_log(r"^download (merged|merge-failed) ", 60)
        done = wait_log(r"^download (DONE|FAILED|PAUSED) ", 10)
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        codecs = labmedia.webm_codecs(data) if data else []
        s.note(f"고름 {opened}/{variants}/{picked}, {merged_line}, {done}, EBML {bool(data and data[:4] == bytes([0x1A, 0x45, 0xDF, 0xA3]))}, 코덱 {codecs}")
        s.verdict = PASS if (merged_line and merged_line.startswith("download merged") and done and "DONE" in done and ".webm " in done
                             and data[:4] == bytes([0x1A, 0x45, 0xDF, 0xA3]) and "V_VP9" in codecs and "A_OPUS" in codecs) else FAIL

    run(Scenario("merge-webm", "DASH WebM(VP9 + Opus) — 한 WebM 으로"), merge_webm)

    def record(s: Scenario, audio_only: bool) -> None:
        # 재생 중인 <video> 를 지금부터 녹화(쪽을 다시 열지 않는다) → 멈추고 저장 → 길이·찾기 표가 있는 그릇으로 다시 담는다.
        t0 = time.time()
        host = f"pl{'a' if audio_only else 'v'}{tok}.lab.test"
        type_address(f"http://{host}:8080/probe/play?t={tok}")
        wait_page(f"probe/play?t={tok}", 30)
        time.sleep(1.5)
        tap(*screen_center(0.3))
        playing = wait_report(lab, t0, "play", 10, host)
        adb("logcat", "-c", check=False)
        mode = "소리만 녹화 — 지금부터 실시간" if audio_only else "녹화 — 지금부터 실시간(다시 인코딩)"
        chosen = tap_desc("메뉴") and tap_text("재생 중인 것 받기") and tap_text(mode)
        started = wait_log(r"^recording started ", 10)
        time.sleep(4)
        stopped = tap_text("멈추고 저장", 8)
        done = wait_log(r"^download (DONE|FAILED) ", 40)
        remuxed = [line for line in log_lines() if line.startswith("download remux")]
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        info = labmedia.mp4_info(data) if data else {}
        kinds = sorted(t["handler"] for t in info.get("tracks", []))
        s.note(f"재생 {playing}, 고름 {chosen}, {started}, 멈춤 {stopped}, {done}, {remuxed[:1]}, 담긴 것 {info}")
        want = ["soun"] if audio_only else ["soun", "vide"]
        ext = ".m4a " if audio_only else ".mp4 "
        s.verdict = PASS if (playing and chosen and started and stopped and done and "DONE" in done and ext in done and kinds == want
                             and 2.0 <= info.get("duration", 0) <= 9.0) else FAIL

    run(Scenario("record", "실시간 녹화 — 재생 중인 영상을 4초, 영상+소리 MP4"), lambda s: record(s, False))
    run(Scenario("record-audio", "실시간 녹화 — 소리만, M4A"), lambda s: record(s, True))

    def capture(s: Scenario) -> None:
        # MSE 가로채기 — 갈고리를 걸고 쪽을 다시 연다 → 재생기가 넣는 바이트를 그대로 → 저장(영상과 소리) → 한 MP4.
        if not labmedia.have("v.mp4", "a.mp4"):
            s.verdict = FAIL
            s.note("시험 미디어가 없다")
            return
        t0 = time.time()
        host = f"ms{tok}.lab.test"
        type_address(f"http://{host}:8080/probe/mse?t={tok}")
        wait_page(f"probe/mse?t={tok}", 30)
        first = wait_report(lab, t0, "mse-ready", 20, host)
        adb("logcat", "-c", check=False)
        t1 = time.time()
        chosen = tap_desc("메뉴") and tap_text("재생 중인 것 받기") and tap_text("가로채기 — 화질 그대로(쪽을 다시 엽니다)")
        armed = wait_log(r"^capture armed", 10)
        again = wait_report(lab, t1, "mse-ready", 30, host)
        time.sleep(1)
        tap(*screen_center(0.3))
        ended = wait_report(lab, t1, "mse-ended", 30, host)
        saved = tap_text("저장", 8) and tap_text("영상과 소리", 8)
        done = wait_log(r"^download (DONE|FAILED) ", 60)
        data = pull_download(done_name(done) or "") if done and "DONE" in done else None
        info = labmedia.mp4_info(data) if data else {}
        kinds = sorted(t["handler"] for t in info.get("tracks", []))
        s.note(f"처음 {first}, 고름 {chosen}, {armed}, 다시 연 쪽 {again}, 끝 {ended}, 저장 {saved}, {done}, 담긴 것 {info}")
        s.verdict = PASS if (chosen and armed and again and saved and done and "DONE" in done and ".mp4 " in done
                             and kinds == ["soun", "vide"] and info.get("duration", 0) >= 5.0) else FAIL

    run(Scenario("capture", "MSE 가로채기 — 쪽을 다시 열어 재생기가 넣는 바이트를 그대로, 한 MP4"), capture)

    def capture_drm(s: Scenario) -> None:
        # 가로채기 중 쪽이 MediaKeys 를 요소에 붙이면(EME — 여기서는 ClearKey) DRM 으로 알고 저장하지 않는다.
        t0 = time.time()
        host = f"ck{tok}.lab.test"
        type_address(f"https://{host}:8443/probe/clearkey?t={tok}")
        wait_page(f"probe/clearkey?t={tok}", 20)
        adb("logcat", "-c", check=False)
        t1 = time.time()
        chosen = tap_desc("메뉴") and tap_text("재생 중인 것 받기") and tap_text("가로채기 — 화질 그대로(쪽을 다시 엽니다)")
        attached = wait_report(lab, t1, "clearkey", 20, host)
        drm_line = wait_log(r"^capture drm", 10)
        time.sleep(2.5)
        texts = [x["text"] for x in dump() if x["text"]]
        shown = any("DRM" in x for x in texts)
        no_save = "저장" not in texts
        cancelled = tap_text("취소", 8)
        s.note(f"고름 {chosen}, 쪽 {attached or reports(lab, t0, host)}, {drm_line}, 막대 {shown}, 저장 단추 없음 {no_save}, 취소 {cancelled}")
        s.verdict = PASS if chosen and attached and drm_line and shown and no_save else FAIL

    run(Scenario("capture-drm", "가로채기 — 쪽이 EME(MediaKeys)를 쓰면 DRM 이라 저장하지 않는다"), capture_drm)

    adb("shell", "am", "force-stop", PKG, check=False)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    out = run_lab.LAB / f"browser-{stamp}.md"
    lines = [f"# Web 브라우저 확인 ({datetime.datetime.now():%Y-%m-%d %H:%M})", "", "| 시나리오 | 무엇 | 판정 | 핵심 |", "|---|---|---|---|"]
    for r in results:
        lines.append(f"| `{r.name}` | {r.title} | **{r.verdict}** | {(r.notes[0] if r.notes else '').replace('|', '/')} |")
    lines.append("")
    for r in results:
        lines += [f"## {r.name} — {r.title}", "", f"판정: **{r.verdict}**", ""] + [f"- {n}" for n in r.notes] + [""]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"결과: local/lab/{out.name}")


if __name__ == "__main__":
    main()
