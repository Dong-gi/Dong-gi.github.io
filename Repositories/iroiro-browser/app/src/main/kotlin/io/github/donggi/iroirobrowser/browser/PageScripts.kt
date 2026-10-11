package io.github.donggi.iroirobrowser.browser

/** 모든 쪽에 넣는 문서 시작 스크립트. */
object PageScripts {

    /**
     * **WebRTC 를 지운다.** 크롬 확장이라면 `webRTCIPHandlingPolicy = disable_non_proxied_udp` 로 막는데 WebView 에는 그 설정이 없다 — WebRTC 의
     * STUN 은 UDP 라 프록시를 타지 않고 폰의 실제 IP(와 사설 IP — WebView 는 mDNS 로 가리지 않는다)를 드러낸다(실험 webrtc2).
     *
     * 문서 시작 스크립트는 같은 출처의 **빈 틀**(about:blank)에는 들어가지 않는다 — 그래서 틀이 생길 수 있는 모든 길의 바로 뒤에서 틀
     * 나무를 훑어 같은 출처의 창마다 RTC 를 지우고 그 창의 길목에도 같은 갈고리를 건다:
     *
     * - DOM 에 넣는 메서드(`appendChild` … `insertAdjacentHTML`, `document.write`, `execCommand`, `Range.insertNode`)와
     *   `innerHTML`·`outerHTML` 의 setter — 부른 뒤(finally)
     * - `contentWindow`·`contentDocument` getter — 돌려주기 전
     * - 틀의 `load` — 문서에 건 **포착 단계** 듣개(빈 틀의 load 는 넣는 그 자리에서 동기로 불리고, 포착 단계가 틀 자신의 onload 보다 먼저다.
     *   load 는 창까지 올라가지 않으니 문서에 건다)
     * - 파서가 넣은 틀 — MutationObserver(파서는 다음 스크립트를 돌리기 전에 마이크로태스크를 비운다)
     * - `window.open`
     *
     * 갈고리를 건 문서는 닫힌 WeakSet 으로 기억한다(쪽이 흉내 낼 표식을 창에 두지 않는다). 실험이 잰 15길(같은 출처의 빈 틀 열 가지·
     * srcdoc·샌드박스·data:·다른 출처)을 모두 막았다. 남는 위험: 그 밖의 새 길, 쪽이 갈고리를 알아챌 수 있다(`toString`).
     */
    const val WEBRTC_GUARD = """
(function () {
  'use strict';
  var root = window;
  var hooked = new WeakSet();
  var RX = /^(webkit)?RTC/;
  function strip(w) {
    var names;
    try { names = Object.getOwnPropertyNames(w); } catch (e) { return; }
    for (var i = 0; i < names.length; i++) {
      var n = names[i];
      if (!RX.test(n)) continue;
      try { delete w[n]; } catch (e) {}
      try { Object.defineProperty(w, n, { value: undefined, writable: false, configurable: false, enumerable: false }); } catch (e) {}
    }
  }
  function sweep() {
    var stack = [root];
    while (stack.length) {
      var w = stack.pop();
      try {
        if (typeof w.RTCPeerConnection !== 'undefined' || typeof w.webkitRTCPeerConnection !== 'undefined' ||
            typeof w.RTCDataChannel !== 'undefined') strip(w);
        var d = w.document;
        if (!hooked.has(d)) { hooked.add(d); hook(w); }
      } catch (e) { continue; }
      var n = 0;
      try { n = w.length; } catch (e) {}
      for (var i = 0; i < n; i++) { try { stack.push(w[i]); } catch (e) {} }
    }
  }
  function wrapMethods(proto, names) {
    if (!proto) return;
    names.forEach(function (name) {
      var d = Object.getOwnPropertyDescriptor(proto, name);
      if (!d || typeof d.value !== 'function') return;
      var f = d.value;
      var g = function () { try { return f.apply(this, arguments); } finally { sweep(); } };
      try { Object.defineProperty(proto, name, { value: g, writable: d.writable, enumerable: d.enumerable, configurable: d.configurable }); } catch (e) {}
    });
  }
  function wrapSetters(proto, names) {
    if (!proto) return;
    names.forEach(function (name) {
      var d = Object.getOwnPropertyDescriptor(proto, name);
      if (!d || !d.set) return;
      var set = d.set;
      try {
        Object.defineProperty(proto, name, { get: d.get, set: function (v) { try { set.call(this, v); } finally { sweep(); } },
          enumerable: d.enumerable, configurable: d.configurable });
      } catch (e) {}
    });
  }
  function wrapGetters(proto, names) {
    if (!proto) return;
    names.forEach(function (name) {
      var d = Object.getOwnPropertyDescriptor(proto, name);
      if (!d || !d.get) return;
      var get = d.get;
      try {
        Object.defineProperty(proto, name, { get: function () { sweep(); return get.call(this); }, set: d.set,
          enumerable: d.enumerable, configurable: d.configurable });
      } catch (e) {}
    });
  }
  function hook(w) {
    wrapMethods(w.Node && w.Node.prototype, ['appendChild', 'insertBefore', 'replaceChild']);
    wrapMethods(w.Element && w.Element.prototype, ['append', 'prepend', 'before', 'after', 'replaceWith', 'replaceChildren',
      'insertAdjacentElement', 'insertAdjacentHTML', 'setHTMLUnsafe', 'moveBefore']);
    wrapMethods(w.Document && w.Document.prototype, ['write', 'writeln', 'execCommand', 'append', 'prepend', 'replaceChildren']);
    wrapMethods(w.ShadowRoot && w.ShadowRoot.prototype, ['setHTMLUnsafe']);
    wrapMethods(w.Range && w.Range.prototype, ['insertNode', 'surroundContents']);
    wrapSetters(w.Element && w.Element.prototype, ['innerHTML', 'outerHTML']);
    wrapSetters(w.ShadowRoot && w.ShadowRoot.prototype, ['innerHTML']);
    ['HTMLIFrameElement', 'HTMLFrameElement', 'HTMLObjectElement'].forEach(function (c) {
      wrapGetters(w[c] && w[c].prototype, ['contentWindow', 'contentDocument']);
    });
    try {
      var open = w.open;
      w.open = function () { var r = open.apply(this, arguments); sweep(); try { if (r) { strip(r); } } catch (e) {} return r; };
    } catch (e) {}
    try { w.document.addEventListener('load', sweep, true); } catch (e) {}
    try { new w.MutationObserver(sweep).observe(w.document, { childList: true, subtree: true }); } catch (e) {}
  }
  sweep();
})();
"""

    /**
     * **사이트가 막아 둔 확대를 푼다**(사용자 요청 2026-10-09 — '사이트 설정과 무관하게 확대하여 볼 수 있게'). 설정 '항상 확대 허용'
     * (`SettingsStore.forceZoom`, 기본 켬)이 켜져 있을 때만 건다([WebViews.applyForceZoom]).
     *
     * 사이트는 `<meta name="viewport">` 의 `user-scalable=no`·`maximum-scale=1` 로 두 손가락 확대를 막는다. 크롬의 '확대/축소 강제 사용'·
     * 삼성 인터넷의 '모든 웹페이지 확대' 가 듣는 자리가 그것인데 WebView 에는 그 설정이 없다 — 그래서 그 둘만 걷어 낸 내용으로 고쳐 쓴다.
     * 폭(`width`)·처음 배율(`initial-scale`)·최소 배율은 그대로라 페이지의 배치는 바뀌지 않는다. 확대 상한은 5배(크롬의 기본값)보다 낮을
     * 때만 걷는다.
     *
     * - 내용은 Blink 의 `HTMLMetaElement::ParseContentAttribute` 와 **같은 차례로** 쌍을 나눠 읽고, 남길 쌍만 `키=값, …` 으로 다시 적는다 —
     *   공백·쌍반점으로 가른 꼴이나 `=` 앞뒤의 공백도 Blink 가 읽는 그대로 남는다.
     * - 주 틀에만(뷰포트는 주 틀의 것만 듣는다). 쪽이 나중에 넣은 meta·나중에 바꾼 content 도 MutationObserver 로 다시 고친다.
     * - 쪽이 고친 것을 되잠그는 고리 — 같은 요소를 1초 안에 열 번 넘게 고치게 되면 그 요소는 그대로 둔다.
     * - CSS `touch-action` 과 쪽이 두 손가락 몸짓을 스스로 받는 곳(지도·그림판)은 건드리지 않는다 — 그 쪽의 몸짓이 그대로 돈다.
     *
     * 쪽에서 보이지 않는 실행 세계에서 돈다(그 기능을 모르는 WebView 면 쪽의 세계에서). 역슬래시를 쓰지 않는다(코틀린 글자열과 셸이 먹지 않게
     * 제어 문자는 `fromCharCode` 로 짓는다).
     */
    const val FORCE_ZOOM = """
(function () {
  'use strict';
  var top = false;
  try { top = window.top === window; } catch (e) {}
  if (!top) return;
  var FLOOR = 5;
  var TAB = String.fromCharCode(9), LF = String.fromCharCode(10), CR = String.fromCharCode(13), NUL = String.fromCharCode(0);
  var marks = new WeakMap();
  function lower(s) {
    return s.replace(/[A-Z]/g, function (c) { return String.fromCharCode(c.charCodeAt(0) + 32); });
  }
  function sep(c) {
    return c === ' ' || c === TAB || c === LF || c === CR || c === '=' || c === ',' || c === ';' || c === NUL;
  }
  function pairs(content) {
    var s = lower(content), n = s.length, i = 0, out = [];
    function at(k) { return k < n ? s.charAt(k) : NUL; }
    while (i < n) {
      while (sep(at(i))) { if (i >= n) break; i++; }
      var kb = i;
      while (!sep(at(i))) { if (i >= n) break; i++; }
      var ke = i;
      while (at(i) !== '=') { var c = at(i); if (c === ',' || c === ';' || i >= n) break; i++; }
      while (sep(at(i))) { var d = at(i); if (d === ',' || d === ';' || i >= n) break; i++; }
      var vb = i;
      while (!sep(at(i))) { if (i >= n) break; i++; }
      out.push([s.slice(kb, ke), s.slice(vb, i)]);
    }
    return out;
  }
  function number(v) {
    var f = parseFloat(v);
    return isNaN(f) ? 0 : f;
  }
  function locksUser(v) {
    if (v === 'yes' || v === 'device-width' || v === 'device-height') return false;
    return Math.abs(number(v)) < 1;
  }
  function capsZoom(v) {
    if (v === 'device-width' || v === 'device-height') return false;
    if (v === 'yes') return true;
    var f = number(v);
    return f >= 0 && f < FLOOR;
  }
  function unlock(content) {
    var list = pairs(content), kept = [], changed = false;
    for (var i = 0; i < list.length; i++) {
      var k = list[i][0], v = list[i][1];
      if ((k === 'user-scalable' && locksUser(v)) || (k === 'maximum-scale' && capsZoom(v))) { changed = true; continue; }
      if (k) kept.push(v ? k + '=' + v : k);
    }
    return changed ? kept.join(', ') : null;
  }
  function allowed(m) {
    var now = Date.now(), s = marks.get(m);
    if (!s) { marks.set(m, { start: now, n: 1, dead: false }); return true; }
    if (s.dead) return false;
    if (now - s.start > 1000) { s.start = now; s.n = 1; return true; }
    if (++s.n > 10) { s.dead = true; return false; }
    return true;
  }
  function fix(m) {
    if (!m || m.localName !== 'meta') return;
    var name = m.getAttribute('name');
    if (name === null || lower(name) !== 'viewport') return;
    var content = m.getAttribute('content');
    if (content === null) return;
    var fixed = unlock(content);
    if (fixed === null || !allowed(m)) return;
    m.setAttribute('content', fixed);
  }
  function walk(node) {
    if (node.nodeType !== 1) return;
    if (node.localName === 'meta') { fix(node); return; }
    if (!node.firstElementChild) return;
    var list = node.getElementsByTagName('meta');
    for (var i = 0; i < list.length; i++) fix(list[i]);
  }
  new MutationObserver(function (records) {
    for (var i = 0; i < records.length; i++) {
      var r = records[i];
      if (r.type === 'attributes') { fix(r.target); continue; }
      for (var j = 0; j < r.addedNodes.length; j++) walk(r.addedNodes[j]);
    }
  }).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['name', 'content'] });
  var all = document.getElementsByTagName('meta');
  for (var i = 0; i < all.length; i++) fix(all[i]);
})();
"""
}
