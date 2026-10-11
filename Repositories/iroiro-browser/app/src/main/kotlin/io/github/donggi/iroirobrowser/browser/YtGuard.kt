package io.github.donggi.iroirobrowser.browser

import java.util.Locale

/**
 * **유튜브 광고 가리기(B1)와 건너뛰기(B2)** — 사용자 결정 2026-10-11 '유튜브 B1만 넣어주세요', 그날 밤 '유튜브 광고 건너뛰기 버튼을 자동으로
 * 누르도록 기능 추가해주세요(포그라운드/백그라운드 무관)'. 광고가 재생되는 동안 그 영상과 광고 덮개를 가리고 `<video>` 를 음소거하며(B1),
 * 건너뛸 수 있게 되면 앱이 그 단추를 **진짜 터치로** 누른다(B2 — 앱이 앞에 있든 뒤에 있든, [SKIP]·[tapPoint]). 끝으로 감기(B3)는 넣지 않았다 —
 * 건너뛸 수 없는 광고는 가리고 끈 채 끝까지 간다.
 *
 * 기본 광고 목록(EasyList)이 광고 물음(`ad_break`)을 막아도 플레이어 응답에 실린 광고는 온다(2026-10-11 저녁 — 목록을 켠 채 앞 광고 묶음). 이것이
 * 그 광고의 뒷받침이다(CLAUDE.md '유튜브 광고 검토').
 *
 * - **쪽의 세계를 건드리지 않는다.** 스크립트([SCRIPT])는 통로([PageBridge])와 같은 보이지 않는 실행 세계에서 돌고 DOM 만 다룬다 — 재생기의
 *   클래스를 보고, `<video>` 의 `muted` 를 바꾸고, `<style>` 하나를 넣는다. 쪽의 함수·전역을 감싸지 않는다. 그 세계를 모르는 WebView 면 걸지
 *   않는다(쪽의 세계로 물러서지 않는다 — B1 없음).
 * - **유튜브 출처에만** 건다([ORIGIN_RULES] — youtube.com 과 퍼가기의 youtube-nocookie.com, 둘 다 그 이름과 그 아래 이름).
 * - **켜는 것은 앱이 정한다.** 문서 시작 스크립트는 광고 차단의 상태를 모른다 — 틀이 [HELLO] 로 묻고 앱이 [GUARD] 로 답한다([wanted] — 광고
 *   차단이 켜져 있고 그 탭의 주 문서 사이트에서 끄지 않았을 때만). 메뉴의 '이 사이트에서 광고 차단 끄기' 가 탈출구다(쪽을 다시 연다). 새 설정
 *   스위치는 두지 않았다.
 *
 * 순수 판단은 JVM 시험(YtGuardTest)이, 스크립트는 PC 의 node 가 작은 DOM 흉내로 따로 시험했다.
 */
object YtGuard {

    /** 틀 → 앱: 켤지 묻는다(문서를 시작할 때·뒤로 가기 캐시에서 돌아올 때). 칸 `g` 는 그 틀의 무작위 이름. */
    const val HELLO = "yt-hello"

    /** 앱 → 틀: 켜기·끄기(`on`)와 가린 자리의 표시 글(`label`). 앱이 상태를 바꿀 때(설정에서 돌아올 때)도 보낸다. */
    const val GUARD = "yt-guard"

    /**
     * 틀 → 앱: 건너뛸 수 있게 된 단추의 가운데(`x`·`y` — 보이는 영역에 대한 비율). 앱이 그 자리를 진짜 터치로 누른다([tapPoint]). 주 틀의 것만
     * 받는다 — 비율이 주 틀의 보이는 영역에 대한 것이다(퍼가기 틀의 광고는 가리기만 한다).
     */
    const val SKIP = "yt-skip"

    /** 틀 → 앱: 광고가 나오기 시작했다·끝났다(`on`) — 광고를 음소거한 동안 앱이 뒤로 가도 재생을 잇게([BackgroundPlay.wanted]). 주 틀의 것만. */
    const val AD = "yt-ad"

    /** 앱이 건너뛰기를 누르는 최소 간격 — 스크립트도 2초에 한 번만 청하지만 앱이 한 번 더 막는다. */
    const val SKIP_MIN_GAP_MS = 1_000L

    /**
     * 누를 자리 — 스크립트가 보낸 비율([x]·[y], 보이는 영역에 대한)을 그 뷰([width]×[height] 픽셀)의 픽셀로. 0 과 1 사이(끝은 빼고)가 아니거나
     * 수가 아니거나 뷰의 크기가 없으면 null(누르지 않는다).
     */
    fun tapPoint(x: Double, y: Double, width: Int, height: Int): Pair<Float, Float>? {
        if (!(x > 0.0 && x < 1.0 && y > 0.0 && y < 1.0) || width <= 0 || height <= 0) return null
        return (x * width).toFloat() to (y * height).toFloat()
    }

    /** 거는 사이트 — 유튜브와 그 퍼가기. 이름 자체와 그 아래 이름(m·www·music …). */
    private val SITES = listOf("youtube.com", "youtube-nocookie.com")

    /**
     * 스크립트를 거는 출처 규칙(`addJavaScriptOnEvent`). androidx.webkit 의 규칙에서 별표 꼴은 그 아래 이름만 덮고 이름 자체는 덮지 않아
     * 둘 다 적는다. https 만, 기본 포트만([isYouTubeOrigin] 과 같은 판단).
     */
    val ORIGIN_RULES: Set<String> = SITES.flatMap { listOf("https://$it", "https://*.$it") }.toSet()

    /**
     * 통로에 온 출처(WebView 가 준 것 — 쪽이 꾸밀 수 없다)가 [ORIGIN_RULES] 에 드는가. 스크립트는 그 규칙으로만 걸리지만 앱도 같은 판단으로 한
     * 번 더 본다 — 다른 출처의 틀에 켜라고 답하지 않게.
     */
    fun isYouTubeOrigin(scheme: String?, host: String?, port: Int): Boolean {
        if (!"https".equals(scheme, ignoreCase = true)) return false
        if (port != -1 && port != HTTPS_PORT) return false
        val h = host?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return false
        return SITES.any { h == it || h.endsWith(".$it") }
    }

    /**
     * 켤지 — 광고 차단이 켜져 있고([adBlockOn]) 그 탭의 주 문서 사이트([topHost])에서 끄지 않았을 때만([siteAllowed] 가 거짓). 퍼가기 틀도 주
     * 문서의 사이트를 따른다 — 막는 규칙(요청 막기)이 주 문서의 결정을 따르고, 메뉴의 사이트별 끄기도 주소창의 사이트를 끈다. 사이트를 모르면
     * 끈다(물을 데가 없다).
     */
    fun wanted(adBlockOn: Boolean, topHost: String, siteAllowed: (String) -> Boolean): Boolean =
        adBlockOn && topHost.isNotEmpty() && !siteAllowed(topHost)

    private const val HTTPS_PORT = 443

    /**
     * 유튜브 틀의 실행 세계(통로와 같은 세계 — 주입 객체를 함께 쓴다)에서 문서 시작 때 도는 스크립트. 앱이 켜라고 할 때까지 아무것도 하지 않는다.
     *
     * - **알아채기** — 재생기(`.html5-video-player`)에 광고 표지 클래스 `ad-showing` 이 붙고 빠지는 것을 MutationObserver 로 보고, 1초마다 한 번
     *   더 본다(늦게 생긴 재생기·놓친 변화). 미디어 이벤트(포착 단계)로도 재생기를 찾는다. 광고는 본편과 **같은 `<video>`** 로 재생된다(실측).
     * - **가리기** — 광고가 있는 동안만 `<style>` 하나: 광고 중인 재생기 안의 `<video>` 와 광고 덮개를 `visibility:hidden`, 건너뛰기 칸은
     *   `visibility:visible` 로 되살린다(사람이 누른다). **opacity 를 쓰지 않는다** — 덮개와 영상에 걸자 쌓임 순서가 바뀌어 단추 자리의 맨 위가
     *   `<video>` 가 되었다(에뮬레이터, elementFromPoint). 가운데에 짧은 표시(앱이 준 글, `pointer-events:none`).
     * - **소리** — 광고 미디어마다 한 번 정한다: 켜져 있으면 끄고 '우리 것' 으로 적는다. 광고 중에 소리가 다시 켜지면 그 광고가 시작된 뒤 사람이
     *   막 만졌는지(신뢰된 누름·키)로 가른다 — 사람이면 그 뜻을 따르고, 아니면 쪽이 한 것이라 다시 끈다(광고 미디어마다 여덟 번까지). 광고가
     *   끝나면 **우리가 끈 것만** 되돌린다. 배속·감기는 하지 않는다.
     * - **건너뛰기(B2)** — 광고 중인 재생기의 건너뛰기 단추가 크기를 얻고(건너뛸 수 있게 되기 전에는 그 칸이 `display:none` — 실측) 그 가운데의
     *   맨 위가 단추이면(`elementFromPoint`) 그 자리를 앱에 알린다([SKIP]) — 2초에 한 번, 광고 미디어마다 다섯 번까지. 스크립트의 `click()` 은
     *   신뢰되지 않은 사건이라 쪽이 가려낼 수 있어 쓰지 않는다. 알린 뒤 1.5초 동안 단추 밖에 떨어진 신뢰된 누름이 재생기 안이나 고리면 막는다 —
     *   그 사이 쪽이 바뀌어 다른 것이 눌리지 않게. 광고가 나오고 끝나는 것도 알린다([AD]).
     * - **원래대로** — 꺼짐 알림·문서가 떠날 때(`pagehide`)·재생기나 영상이 문서에서 떨어질 때. SPA 이동은 클래스·연결 상태로 따라간다.
     *
     * 역슬래시와 달러 기호를 쓰지 않는다 — 코틀린의 날 글자열·셸이 먹지 않게(PageScripts 의 선례). CSS 글자열에 넣는 표시 글은 따옴표·
     * 역슬래시 등을 번호로 가려 뺀다.
     */
    const val SCRIPT = """
(function () {
  'use strict';
  var ch = globalThis['__IROIRO_OBJ__'];
  if (!ch) return;
  var gid = Math.random().toString(36).slice(2, 10) + Math.random().toString(36).slice(2, 10);
  var PLAYER = 'html5-video-player', AD = 'ad-showing';
  var TICK_MS = 1000, GESTURE_MS = 1500, MAX_FIGHTS = 8, LABEL_MAX = 40;
  // 가리는 것 — 영상과 광고 덮개(광고 표지·몇째 광고·남은 시간·광고주 방문). 되살리는 것 — 건너뛰기 칸(사람이 직접 누른다).
  // visibility 는 쌓임 순서를 바꾸지 않고, 가린 것은 누름을 받지 않으며, 가린 것 안의 칸만 visible 로 되살릴 수 있다.
  var HIDE = ['video', '.video-ads', '.ytp-ad-module', '.ytp-ad-player-overlay', '.ytp-ad-player-overlay-layout',
    '.ytp-ad-player-overlay-instream-info', '.ytp-ad-pod-index--mweb', '.ytp-ad-duration-remaining--mweb',
    '.ytp-ad-visit-advertiser-button', '.ytp-ad-text', '.ytp-ad-image-overlay'];
  var KEEP = ['.ytp-ad-skip-ad-slot', '.ytp-ad-skip-button-slot', '.ytp-ad-skip-button-container', '.ytp-skip-ad'];
  // 건너뛰기 단추(B2) — 건너뛸 수 있게 되면(대개 5초 뒤) 크기가 생긴다. 앱이 그 가운데를 진짜 터치로 누른다.
  var SKIP = ['.ytp-ad-skip-button-modern', '.ytp-skip-ad-button', '.ytp-ad-skip-button'];
  var SKIP_EVERY_MS = 2000, SKIP_MAX = 5, ARM_MS = 1500;
  var on = false, label = '', style = null, timer = 0, lastGesture = 0;
  var adOn = false, skipAt = 0, skipTries = 0, armed = null, armedUntil = 0;
  var players = new Map();
  var decided = new Set();
  var ours = new Map();

  function send(o) { o.g = gid; try { ch.postMessage(JSON.stringify(o)); } catch (e) {} }
  function playerOf(el) {
    try { return el && el.closest ? el.closest('.' + PLAYER) : null; } catch (e) { return null; }
  }
  function showing(p) {
    try { return !!(p && p.isConnected && p.classList.contains(AD)); } catch (e) { return false; }
  }
  // 앱이 준 글을 CSS 글자열에 넣는다 — 제어 글자·큰따옴표·작은따옴표·역슬래시·꺾쇠·중괄호·쌍반점을 빼고 줄인다.
  function clean(s) {
    s = String(s == null ? '' : s);
    var out = '';
    for (var i = 0; i < s.length && out.length < LABEL_MAX; i++) {
      var c = s.charCodeAt(i);
      if (c < 32 || c === 127 || c === 34 || c === 39 || c === 92 || c === 60 || c === 62 || c === 123 || c === 125 || c === 59) continue;
      out += s.charAt(i);
    }
    return out;
  }
  function scoped(list) {
    return list.map(function (s) { return '.' + PLAYER + '.' + AD + ' ' + s; }).join(',');
  }
  function css() {
    var s = scoped(HIDE) + '{visibility:hidden!important}' + scoped(KEEP) + '{visibility:visible!important}';
    // 가린 자리 가운데의 짧은 표시 — 손가락을 가로채지 않는다. 재생기의 조작 막대가 위에 오면 그 아래에 깔린다.
    if (label) s += '.' + PLAYER + '.' + AD + '::after{content:"' + label + '"!important;position:absolute!important;' +
      'left:50%!important;top:50%!important;transform:translate(-50%,-50%)!important;padding:6px 12px!important;' +
      'border-radius:16px!important;background:rgba(0,0,0,0.6)!important;color:#fff!important;' +
      'font:14px/1.4 sans-serif!important;white-space:nowrap!important;pointer-events:none!important}';
    return s;
  }
  function addStyle() {
    if (style && style.isConnected) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    if (!style) { style = document.createElement('style'); style.textContent = css(); }
    root.appendChild(style);
  }
  function removeStyle() {
    if (style && style.parentNode) style.parentNode.removeChild(style);
  }
  function watch(p) {
    if (!p || players.has(p)) return;
    var mo = null;
    try {
      mo = new MutationObserver(apply);
      mo.observe(p, { attributes: true, attributeFilter: ['class'] });
    } catch (e) {}
    players.set(p, mo);
  }
  function unwatch(p) {
    var mo = players.get(p);
    if (mo) { try { mo.disconnect(); } catch (e) {} }
    players.delete(p);
  }
  function scan() {
    var list = document.getElementsByClassName(PLAYER);
    for (var i = 0; i < list.length; i++) watch(list[i]);
  }
  function release(v) {
    ours.delete(v);
    try { if (v.muted) v.muted = false; } catch (e) {}
  }
  function vport() {
    var v = window.visualViewport;
    return v ? { x: v.offsetLeft, y: v.offsetTop, w: v.width || 1, h: v.height || 1 } : { x: 0, y: 0, w: innerWidth || 1, h: innerHeight || 1 };
  }
  // 건너뛸 수 있게 된 단추 — 크기가 있고, 그 가운데에서 맨 위가 단추(나 그 안)다(숨은 것은 맨 위가 될 수 없다). 없으면 null.
  function skipButton(p) {
    for (var i = 0; i < SKIP.length; i++) {
      var list = p.querySelectorAll(SKIP[i]);
      for (var j = 0; j < list.length; j++) {
        var b = list[j], r = b.getBoundingClientRect();
        if (r.width < 4 || r.height < 4) continue;
        var x = r.left + r.width / 2, y = r.top + r.height / 2, top = document.elementFromPoint(x, y);
        if (top && (top === b || b.contains(top))) return { b: b, x: x, y: y };
      }
    }
    return null;
  }
  // 그 단추를 누르라고 앱에 알린다 — 2초에 한 번, 광고 미디어마다 SKIP_MAX 번까지. 보이는 영역 밖이면 알리지 않는다(누를 수 없다).
  function trySkip() {
    var t = Date.now();
    if (skipTries >= SKIP_MAX || t - skipAt < SKIP_EVERY_MS) return;
    var hit = null;
    players.forEach(function (mo, p) { if (!hit && showing(p)) hit = skipButton(p); });
    if (!hit) return;
    var v = vport(), rx = (hit.x - v.x) / v.w, ry = (hit.y - v.y) / v.h;
    if (!(rx > 0 && rx < 1 && ry > 0 && ry < 1)) return;
    skipAt = t;
    skipTries++;
    armed = hit.b;
    armedUntil = t + ARM_MS;
    send({ op: 'yt-skip', x: rx, y: ry });
  }
  // 지금 상태에 맞춘다 — 광고 중인 재생기의 영상은 광고 미디어마다 한 번 소리를 정하고(켜져 있으면 끄고 우리 것으로 적는다), 광고가 끝났거나
  // 문서에서 떨어진 영상은 우리가 끈 것만 되돌린다. 가리기는 CSS 가 클래스를 따라 하고 style 은 광고가 있는 동안만 둔다.
  function apply() {
    if (!on) return;
    var t = Date.now(), any = false;
    players.forEach(function (mo, p) {
      if (!p.isConnected) { unwatch(p); return; }
      if (!showing(p)) return;
      any = true;
      var vs = p.getElementsByTagName('video');
      for (var i = 0; i < vs.length; i++) {
        var v = vs[i];
        if (decided.has(v)) continue;
        decided.add(v);
        if (!v.muted) { ours.set(v, { since: t, fights: 0 }); v.muted = true; }
      }
    });
    decided.forEach(function (v) { if (!showing(playerOf(v))) decided.delete(v); });
    ours.forEach(function (st, v) { if (!showing(playerOf(v))) release(v); });
    if (any) addStyle(); else removeStyle();
    if (any !== adOn) { adOn = any; skipTries = 0; send({ op: 'yt-ad', on: any }); }
    if (any) trySkip(); else armed = null;
  }
  function start() {
    on = true;
    scan();
    apply();
    if (!timer) timer = setInterval(function () { scan(); apply(); }, TICK_MS);
  }
  function stop() {
    on = false;
    if (timer) { clearInterval(timer); timer = 0; }
    players.forEach(function (mo, p) { unwatch(p); });
    decided.clear();
    ours.forEach(function (st, v) { release(v); });
    removeStyle();
    armed = null;
    if (adOn) { adOn = false; send({ op: 'yt-ad', on: false }); }
  }
  // 새 미디어(광고 묶음의 다음 광고 등)면 소리를 다시 정한다. 재생이 시작되면 그 재생기를 지켜본다(늦게 생긴 재생기).
  ['loadstart', 'emptied', 'play', 'playing'].forEach(function (type) {
    addEventListener(type, function (e) {
      var v = e.target;
      if (!on || !(v instanceof HTMLMediaElement)) return;
      if (type === 'loadstart' || type === 'emptied') {
        decided.delete(v);
        skipTries = 0;
        var st = ours.get(v);
        if (st) { st.since = Date.now(); st.fights = 0; }
      }
      watch(playerOf(v));
      apply();
    }, true);
  });
  // 광고 중에 소리가 다시 켜졌다 — 그 광고 미디어가 시작된 뒤 사람이 막 만졌으면 그 뜻을 따르고(되돌릴 것에서 뺀다), 아니면 쪽이 한 것이라
  // 다시 끈다. 쪽과 다투는 것은 광고 미디어마다 MAX_FIGHTS 번까지.
  addEventListener('volumechange', function (e) {
    var v = e.target;
    if (!on || !(v instanceof HTMLMediaElement)) return;
    var st = ours.get(v);
    if (!st || v.muted || !showing(playerOf(v))) return;
    if (lastGesture > st.since && Date.now() - lastGesture <= GESTURE_MS) { ours.delete(v); return; }
    if (++st.fights > MAX_FIGHTS) { ours.delete(v); return; }
    v.muted = true;
  }, true);
  ['pointerdown', 'touchstart', 'mousedown', 'keydown'].forEach(function (type) {
    addEventListener(type, function (e) { if (e.isTrusted) lastGesture = Date.now(); }, { capture: true, passive: true });
  });
  // 앱이 누르는 사이(ARM_MS) 단추 밖에 떨어진 신뢰된 누름이 재생기 안이나 고리면 막는다 — 그 사이 쪽이 바뀌어 다른 것(광고주 방문 등)이
  // 눌리지 않게. 단추를 누른 것은 그대로 둔다.
  addEventListener('click', function (e) {
    if (!armed || !e.isTrusted || Date.now() > armedUntil) return;
    var t = e.target;
    if (t && armed.contains(t)) { armed = null; return; }
    if (!playerOf(t) && !(t && t.closest && t.closest('a[href]'))) return;
    e.preventDefault();
    e.stopImmediatePropagation();
  }, true);
  // 문서가 떠난다 — 원래대로. 뒤로 가기 캐시에서 돌아오면 다시 묻는다.
  addEventListener('pagehide', function () { if (on) stop(); }, true);
  addEventListener('pageshow', function (e) { if (e.persisted) send({ op: 'yt-hello' }); }, true);
  ch.addEventListener('message', function (ev) {
    var m;
    try { m = typeof ev.data === 'string' ? JSON.parse(ev.data) : null; } catch (e) { return; }
    if (!m || m.op !== 'yt-guard') return;
    var l = clean(m.label);
    if (l !== label) { label = l; removeStyle(); style = null; }
    if (!m.on) { if (on) stop(); return; }
    if (on) apply(); else start();
  });
  send({ op: 'yt-hello' });
})();
"""
}
