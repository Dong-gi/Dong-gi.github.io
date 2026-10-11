package io.github.donggi.iroirobrowser.browser

/**
 * 쪽과 앱 사이의 스크립트 둘.
 *
 * - [BRIDGE] — **쪽에서 보이지 않는 세계**(androidx.webkit 1.17 의 실행 세계 — 크롬 확장의 고립된 세계와 같다)에서 모든 틀에 문서 시작 때 돈다.
 *   쪽의 스크립트는 이 세계의 변수·함수·주입 객체를 볼 수 없고, 이 세계는 쪽과 DOM 만 함께 쓴다. 하는 일: `blob:` 고리를 누르면 바로 그 blob 을
 *   붙잡아 둔다(쪽은 흔히 누른 직후 주소를 거둔다 — 앱이 내려받기를 알아챌 때는 이미 늦다), 앱이 청하면 blob·녹화를 덩이로 넘긴다, 가로채기 중이면
 *   쪽의 갈고리([MSE_HOOK])에 통로를 건넨다, **요소 고르기**(요소 숨기기 — [PickProtocol])는 앱이 그 탭의 주 틀에 `pick-start` 를 보낼 때만 깨어
 *   누른 자리의 요소와 조상을 알리고 `pick-stop` 에 듣개·시계를 걷는다(그 전에는 함수만 있고 아무것도 걸지 않는다). 페이지의 DOM 은 바꾸지
 *   않는다 — 테두리는 앱이 덮개에 그리고, 누름은 앱이 가로채 페이지에 가지 않는다. 고르는 코드를 따로 넣지 않고 여기 두는 까닭은 실행
 *   세계에 스크립트를 넣는 길이 문서 시작(`addJavaScriptOnEvent`)뿐이라서다 — 이미 열린 페이지에 그때 넣을 길이 없다(androidx.webkit 1.17.1).
 *   그리고 **재생 중인 미디어**(화면 켜 두기 — [ScreenWake])를 알린다 — 미디어 이벤트를 포착 단계로 보고 소리가 나는가·영상인가가 바뀔 때,
 *   재생 중이면 10초마다, 앱이 물으면(`media-ask`).
 * - [MSE_HOOK] — **가로채기를 고른 탭에만, 그동안만** 쪽의 세계에 문서 시작 때 넣는다(사용자 결정, 2026-10-05 — 쪽이 알아챌 수 있는 것은 이것뿐이다).
 *   `SourceBuffer.appendBuffer` 를 감싸 재생기가 넣는 바이트를 **그 호출 안에서 복사해** 보낸다(브라우저가 곧 가져간다).
 * - [BLOB_HOOK] — blob 을 넘겨받지 못한 쪽(쪽의 CSP 가 `blob:` 읽기를 막거나 — 보이지 않는 세계에도 쪽의 CSP 가 걸린다 — 본문 밖의 고리를 누르고
 *   곧바로 주소를 거뒀다)에서 **사용자가 고르면 그 탭에만** 넣는다. `URL.createObjectURL` 이 받은 Blob 을 쥐어 두었다가 청하면 Blob 자체를 건넨다
 *   (네트워크 읽기가 아니라 CSP 가 걸리지 않는다).
 *
 * 앱으로 가는 것은 문자열(JSON — 알림)과 이진 덩이다. 이진 덩이의 머리: 1 바이트 종류(1 blob · 2 녹화 · 3 가로채기), 4 바이트 번호(빅 엔디언),
 * 가로채기면 8 바이트 `timestampOffset`(초, float64). 그 뒤가 내용이다.
 */
object BridgeScripts {

    /** 주입 객체의 이름 자리 — 설치할 때 무작위 이름으로 바꾼다(그 세계 안의 이름이라 쪽에는 보이지 않는다). */
    const val OBJECT_PLACEHOLDER = "__IROIRO_OBJ__"

    /** 가로채기의 표 자리 — 가로채기마다 무작위. */
    const val TOKEN_PLACEHOLDER = "__IROIRO_TOKEN__"

    const val BRIDGE = """
(function () {
  'use strict';
  var ch = globalThis['__IROIRO_OBJ__'];
  if (!ch) return;
  var frame = Math.random().toString(36).slice(2, 10) + Math.random().toString(36).slice(2, 10);
  var isTop = false;
  try { isTop = window.top === window; } catch (e) {}
  var CHUNK = 512 * 1024;
  var KEEP_MS = 120000;

  function send(o) { o.frame = frame; try { ch.postMessage(JSON.stringify(o)); } catch (e) {} }
  function sendBin(kind, id, buf, extra) {
    var head = kind === 3 ? 13 : 5;
    var out = new Uint8Array(buf.byteLength + head);
    var dv = new DataView(out.buffer);
    out[0] = kind;
    dv.setUint32(1, id >>> 0);
    if (kind === 3) dv.setFloat64(5, +extra || 0);
    out.set(new Uint8Array(buf), head);
    try { ch.postMessage(out.buffer); } catch (e) {}
  }

  // ── blob: 누르는 그 자리에서 붙잡는다(fetch 는 부른 때의 blob 을 쥔다 — 쪽이 곧바로 주소를 거둬도 남는다) ──
  // 늘 걸려 있어 쪽이 a.click() 을 거듭하면 그만큼 쥐게 된다(쪽의 메모리를 두 배로) — 개수·총량을 묶고(넘으면 오래된 것부터 놓는다)
  // KEEP_MS 가 지나면 놓는다(앞 판은 새 누름이 있을 때만 치웠다).
  var kept = new Map(), KEEP_MAX = 8, KEEP_BYTES = 256 * 1024 * 1024;
  function trimKept() {
    var total = 0;
    kept.forEach(function (v) { total += v.size; });
    var keys = kept.keys();
    while (kept.size > KEEP_MAX || total > KEEP_BYTES) {
      var k = keys.next();
      if (k.done) break;
      total -= kept.get(k.value).size;
      kept.delete(k.value);
    }
  }
  addEventListener('click', function (e) {
    var t = e.target;
    var a = t && t.closest ? t.closest('a[href],area[href]') : null;
    if (!a) return;
    var href = String(a.href || '');
    if (href.lastIndexOf('blob:', 0) !== 0) return;
    kept.delete(href);
    var k = { p: null, name: a.getAttribute('download') || '', size: 0 };
    k.p = fetch(href).then(function (r) { return r.blob(); }).then(function (b) {
      if (kept.get(href) === k) { k.size = b.size; trimKept(); }
      return b;
    }).catch(function () { return null; });
    kept.set(href, k);
    trimKept();
    setTimeout(function () { if (kept.get(href) === k) kept.delete(href); }, KEEP_MS);
  }, true);

  var sends = new Map();
  function giveBlob(m) {
    var k = kept.get(m.url);
    var p = k ? k.p : fetch(m.url).then(function (r) { return r.blob(); }).catch(function () { return null; });
    p.then(function (b) { return b || fromHook(m.url, m.id); }).then(function (b) {
      if (!b) { send({ op: 'blob-none', id: m.id }); return; }
      sends.set(m.id, { b: b, at: 0 });
      send({ op: 'blob-start', id: m.id, size: b.size, type: b.type || '', name: k ? k.name : '' });
    });
  }
  function pump(id) {
    var s = sends.get(id);
    if (!s) return;
    if (s.at >= s.b.size) { sends.delete(id); send({ op: 'blob-end', id: id }); return; }
    var end = Math.min(s.b.size, s.at + CHUNK);
    s.b.slice(s.at, end).arrayBuffer().then(function (buf) { s.at = end; sendBin(1, id, buf); },
      function (e) { sends.delete(id); send({ op: 'blob-error', id: id, why: String(e && e.name || e) }); });
  }

  // ── 녹화: 재생 중인 요소의 captureStream → MediaRecorder ──
  var recs = new Map();
  function candidates() {
    var out = [];
    var els = document.querySelectorAll('video,audio');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var area = 0;
      if (el.localName === 'video') { var r = el.getBoundingClientRect(); area = Math.max(0, r.width) * Math.max(0, r.height); }
      out.push({ el: el, playing: !el.paused && !el.ended && el.readyState > 2, area: area, drm: !!el.mediaKeys,
        video: el.localName === 'video' && el.videoWidth > 0 });
    }
    out.sort(function (a, b) { return (b.playing - a.playing) || (b.video - a.video) || (b.area - a.area); });
    return out;
  }
  function probe(m) {
    var c = candidates()[0];
    send({ op: 'rec-cand', id: m.id, has: !!c, playing: !!(c && c.playing), video: !!(c && c.video), drm: !!(c && c.drm),
      area: c ? c.area : 0, top: isTop });
  }
  function pickType(audioOnly, hasVideo, hasAudio) {
    var list = audioOnly || !hasVideo
      ? ['audio/mp4;codecs=mp4a.40.2', 'audio/webm;codecs=opus', 'audio/webm']
      : hasAudio
        ? ['video/mp4;codecs=avc1.42E01E,mp4a.40.2', 'video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm']
        : ['video/mp4;codecs=avc1.42E01E', 'video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'];
    for (var i = 0; i < list.length; i++) if (MediaRecorder.isTypeSupported(list[i])) return list[i];
    return '';
  }
  function startRec(m) {
    var c = candidates()[0];
    if (!c) { send({ op: 'rec-error', id: m.id, why: 'none' }); return; }
    if (c.drm) { send({ op: 'rec-error', id: m.id, why: 'drm' }); return; }
    var stream;
    try { stream = c.el.captureStream(); } catch (e) { send({ op: 'rec-error', id: m.id, why: String(e && e.name || e) }); return; }
    var tracks = m.audioOnly ? stream.getAudioTracks() : stream.getTracks();
    if (!tracks.length) { send({ op: 'rec-error', id: m.id, why: 'no-track' }); return; }
    var s = new MediaStream(tracks);
    var hasVideo = s.getVideoTracks().length > 0, hasAudio = s.getAudioTracks().length > 0;
    var type = pickType(m.audioOnly, hasVideo, hasAudio);
    var rec;
    try {
      rec = new MediaRecorder(s, type ? { mimeType: type, videoBitsPerSecond: 5000000, audioBitsPerSecond: 160000 } : undefined);
    } catch (e) { send({ op: 'rec-error', id: m.id, why: String(e && e.name || e) }); return; }
    var chain = Promise.resolve();
    var el = c.el;
    // 실제로 담긴 시간 — 요소가 멈추면 녹화도 멈추고 그때까지의 시간을 알린다(앱의 '녹화 중 · 1:23' 이 멈춰 있던 시간을 세지 않게).
    var recMs = 0, resumedAt = Date.now();
    function onPause() {
      try {
        if (rec.state !== 'recording') return;
        rec.pause();
        recMs += Date.now() - resumedAt;
        send({ op: 'rec-state', id: m.id, paused: true, ms: recMs });
      } catch (e) {}
    }
    function onPlay() {
      try {
        if (rec.state !== 'paused') return;
        rec.resume();
        resumedAt = Date.now();
        send({ op: 'rec-state', id: m.id, paused: false, ms: recMs });
      } catch (e) {}
    }
    function onEnded() { stopRec(m.id); }
    rec.ondataavailable = function (e) {
      if (!e.data || !e.data.size) return;
      var d = e.data;
      chain = chain.then(function () { return d.arrayBuffer(); }).then(function (buf) { sendBin(2, m.id, buf); });
    };
    rec.onstop = function () {
      el.removeEventListener('pause', onPause);
      el.removeEventListener('play', onPlay);
      el.removeEventListener('ended', onEnded);
      tracks.forEach(function (t) { try { t.stop(); } catch (e) {} });
      recs.delete(m.id);
      chain.then(function () { send({ op: 'rec-end', id: m.id }); });
    };
    rec.onerror = function (e) { send({ op: 'rec-error', id: m.id, why: String(e && e.error && e.error.name || 'error') }); };
    el.addEventListener('pause', onPause);
    el.addEventListener('play', onPlay);
    el.addEventListener('ended', onEnded);
    try { rec.start(1000); resumedAt = Date.now(); } catch (e) { send({ op: 'rec-error', id: m.id, why: String(e && e.name || e) }); return; }
    recs.set(m.id, rec);
    send({ op: 'rec-started', id: m.id, mime: rec.mimeType || type, video: hasVideo && !m.audioOnly, audio: hasAudio });
  }
  function stopRec(id) {
    var rec = recs.get(id);
    if (!rec) return;
    try { if (rec.state !== 'inactive') rec.stop(); } catch (e) {}
  }

  // ── blob 갈고리(사용자가 고른 탭에만): 쪽의 세계가 쥔 Blob 을 통로로 받는다 ──
  var blobPort = null, blobWaits = new Map();
  function armBlob(token) {
    if (blobPort) return;
    var mc = new MessageChannel();
    blobPort = mc.port1;
    blobPort.onmessage = function (e) {
      var m = e.data;
      var w = m && blobWaits.get(m.id);
      if (!w) return;
      blobWaits.delete(m.id);
      w(m.blob || null);
    };
    window.postMessage(token, '*', [mc.port2]);
  }
  function fromHook(url, id) {
    if (!blobPort) return Promise.resolve(null);
    return new Promise(function (ok) {
      blobWaits.set(id, ok);
      blobPort.postMessage({ op: 'get', url: url, id: id });
      setTimeout(function () { if (blobWaits.delete(id)) ok(null); }, 3000);
    });
  }

  // ── 가로채기: 쪽의 갈고리에 통로를 건넨다(쪽의 듣개보다 먼저 건 갈고리가 받아 멈추므로 쪽은 이 알림을 보지 못한다) ──
  var armed = false;
  function arm(token) {
    if (armed) return;
    armed = true;
    var mc = new MessageChannel();
    mc.port1.onmessage = function (e) {
      var m = e.data;
      if (!m || typeof m !== 'object') return;
      if (m.op === 'data') { if (m.buf) sendBin(3, m.sb, m.buf, m.tso); return; }
      var o = { op: 'cap-' + m.op };
      for (var k in m) if (k !== 'op') o[k] = m[k];
      send(o);
    };
    window.postMessage(token, '*', [mc.port2]);
  }

  // ── 요소 고르기(사용자가 고른 탭의 주 틀에서, 고르는 동안만 깬다 — 앱이 누름을 가로채 누른 자리를 알린다) ──
  // 페이지의 DOM 은 바꾸지 않는다(테두리는 앱이 덮개에 그린다). 좌표는 보이는 영역(visual viewport)에 대한 비율이다(PickProtocol).
  // 듣개·시계는 pick-start 에 걸고 pick-stop 에 걷는다. 이름은 PickProtocol 의 한도(100 글자·클래스 16·조상 64)로 줄여 보낸다.
  var pk = null;
  var TEST_ATTRS = ['data-testid', 'data-test-id', 'data-test', 'data-qa', 'data-cy'];
  function vport() {
    var v = window.visualViewport;
    return v ? { x: v.offsetLeft, y: v.offsetTop, w: v.width || 1, h: v.height || 1 }
      : { x: 0, y: 0, w: innerWidth || 1, h: innerHeight || 1 };
  }
  function under(m) {
    var v = vport();
    return document.elementFromPoint(v.x + m.x * v.w, v.y + m.y * v.h);
  }
  function boxOf(el) {
    var r = el.getBoundingClientRect(), v = vport();
    return [(r.left - v.x) / v.w, (r.top - v.y) / v.h, (r.right - v.x) / v.w, (r.bottom - v.y) / v.h];
  }
  function nodeOf(el) {
    var o = { t: String(el.localName || ''), i: '', c: [], n: 1, k: 1, a: [] };
    try {
      if (typeof el.id === 'string' && el.id.length <= 100) o.i = el.id;
      var cl = el.classList;
      for (var i = 0; cl && i < cl.length && o.c.length < 16; i++) if (cl[i].length <= 100) o.c.push(cl[i]);
      for (var j = 0; j < TEST_ATTRS.length; j++) {
        var v = el.getAttribute(TEST_ATTRS[j]);
        if (v && v.length <= 100) o.a.push([TEST_ATTRS[j], v]);
      }
      var p = el.parentElement;
      if (p) {
        o.k = 0;
        for (var c = p.firstElementChild; c; c = c.nextElementSibling) if (c.localName === el.localName) { o.k++; if (c === el) o.n = o.k; }
      }
    } catch (e) {}
    return o;
  }
  function pickSend(op, o) { o.op = op; o.id = pk.id; send(o); }
  function picked() { return pk && pk.path ? pk.path[pk.at] : null; }
  function report() {
    var el = picked();
    if (!el) return;
    if (!el.isConnected) { pk.path = null; pickSend('pick-gone', {}); return; }
    var chain = [];
    for (var e = el; e && e !== document.documentElement && chain.length < 64; e = e.parentElement) chain.push(nodeOf(e));
    var r = el.getBoundingClientRect(), b = boxOf(el);
    pk.last = b.join(',');
    pickSend('pick-info', { nodes: chain, rect: b, w: Math.round(r.width), h: Math.round(r.height),
      up: pk.at < pk.path.length - 1, down: pk.at > 0 });
  }
  function moved() {
    if (!pk) return;
    pk.raf = 0;
    var el = picked();
    if (!el) return;
    if (!el.isConnected) { pk.path = null; pickSend('pick-gone', {}); return; }
    var b = boxOf(el), key = b.join(',');
    if (key !== pk.last) { pk.last = key; pickSend('pick-rect', { rect: b }); }
  }
  function track() { if (pk && !pk.raf) pk.raf = requestAnimationFrame(moved); }
  function pickStart(m) {
    pickStop();
    pk = { id: m.id, path: null, at: 0, last: '', raf: 0, timer: setInterval(track, 400) };
    addEventListener('scroll', track, true);
    addEventListener('resize', track, true);
    if (window.visualViewport) { visualViewport.addEventListener('scroll', track); visualViewport.addEventListener('resize', track); }
  }
  function pickStop() {
    if (!pk) return;
    clearInterval(pk.timer);
    if (pk.raf) cancelAnimationFrame(pk.raf);
    removeEventListener('scroll', track, true);
    removeEventListener('resize', track, true);
    if (window.visualViewport) { visualViewport.removeEventListener('scroll', track); visualViewport.removeEventListener('resize', track); }
    pk = null;
  }
  function pickAt(m) {
    var path = [];
    for (var e = under(m); e && e !== document.body && e !== document.documentElement; e = e.parentElement) path.push(e);
    if (!path.length) { pk.path = null; pickSend('pick-none', {}); return; }
    pk.path = path;
    pk.at = 0;
    report();
  }
  function pickMove(d) {
    if (!pk.path) return;
    var to = pk.at + d;
    if (to < 0 || to >= pk.path.length) return;
    pk.at = to;
    report();
  }
  function canScroll(e, dx, dy) {
    var s = getComputedStyle(e);
    function room(o, pos, size, client, d) {
      return (o === 'auto' || o === 'scroll' || o === 'overlay') && size > client + 1 && (d > 0 ? pos + client < size - 1 : pos > 0);
    }
    return (dy !== 0 && room(s.overflowY, e.scrollTop, e.scrollHeight, e.clientHeight, dy)) ||
      (dx !== 0 && room(s.overflowX, e.scrollLeft, e.scrollWidth, e.clientWidth, dx));
  }
  function pickScroll(m) {
    var v = vport(), dx = m.dx * v.w, dy = m.dy * v.h;
    var target = document.scrollingElement || document.documentElement;
    for (var e = under(m); e && e !== document.documentElement; e = e.parentElement) {
      if (canScroll(e, dx, dy)) { target = e; break; }
    }
    try { target.scrollBy({ left: dx, top: dy, behavior: 'instant' }); } catch (x) { target.scrollLeft += dx; target.scrollTop += dy; }
    track();
  }
  function pickCheck(m) {
    var el = picked(), res = [], sels = m.sels || [];
    for (var i = 0; i < sels.length && i < 32; i++) {
      var n = -1, hit = false;
      try {
        var l = document.querySelectorAll(sels[i]);
        n = l.length;
        for (var j = 0; el && j < l.length && j < 2000; j++) if (l[j] === el) { hit = true; break; }
      } catch (x) {}
      res.push({ n: n, hit: hit });
    }
    pickSend('pick-checked', { res: res });
  }

  // ── 재생 중(화면 켜 두기 — ScreenWake): 이 틀에서 재생 중인 미디어 요소를 앱에 알린다 — 소리가 나는가(음소거·음량 0 이 아닌), 영상인가 ──
  // 미디어의 이벤트는 거품이 일지 않지만 포착 단계로는 창까지 온다 — 문서에 붙은 요소만이다(문서 밖의 new Audio()·shadow DOM 안·Web Audio 는
  // 모른다). 바뀌면 곧바로 알리고, 재생 중이면 10초마다 다시 본다 — 재생 중인 요소가 문서에서 떨어지면 멈춤 알림이 창까지 오지 않는다. 앱은 30초
  // 넘게 소식이 없는 틀을 잊는다(PlayingMedia). 다시 알림은 **고정 간격**이다 — 상태가 같은 이벤트(반복 재생이 한 바퀴 돌 때마다 오는 것 등)가
  // 시계를 처음부터 다시 걸면 다시 알림이 영영 나가지 않는다(앞 판 — 6초짜리 반복 소리에서 30초 만에 풀렸다).
  var media = new Set(), mediaLast = '', mediaTimer = 0;
  function mediaState() {
    var audible = false, video = false;
    media.forEach(function (el) {
      if (!el.isConnected || el.paused || el.ended) { media.delete(el); return; }
      if (!el.muted && el.volume > 0) audible = true;
      if (el.localName === 'video') video = true;
    });
    return (audible ? 'a' : '') + (video ? 'v' : '');
  }
  // 뒤에서 재생 잇기의 알림에 보일 영상 제목 — 유튜브 모바일 웹은 쪽 제목을 'YouTube' 로만 두고 영상 제목은 화면의 제목 칸에 적는다(다음 영상으로
  // 넘어가면 그 칸만 바뀐다 — 본문의 <title>·og:title 은 처음 연 영상의 것으로 남는다). WebView 에는 미디어 세션 API(navigator.mediaSession)가
  // 없다(2026-10-11 에뮬레이터). 유튜브가 아니거나 칸이 없으면 빈 글 — 앱이 쪽 제목을 쓴다. 읽기만 한다.
  function mediaTitle() {
    var h = location.hostname;
    if (h !== 'youtube.com' && h.slice(-12) !== '.youtube.com') return '';
    var el = document.querySelector('.ytVideoMetadataViewModelTitle');
    return el ? String(el.textContent || '').trim().slice(0, 200) : '';
  }
  function mediaReport(force) {
    var s = mediaState();
    if (force || s !== mediaLast) {
      mediaLast = s;
      send({ op: 'media', audible: s.indexOf('a') >= 0, video: s.indexOf('v') >= 0, title: s ? mediaTitle() : '' });
    }
    if (s && !mediaTimer) mediaTimer = setInterval(function () { mediaReport(true); }, 10000);
    else if (!s && mediaTimer) { clearInterval(mediaTimer); mediaTimer = 0; }
  }
  ['play', 'playing', 'pause', 'ended', 'volumechange', 'emptied', 'abort', 'error'].forEach(function (type) {
    addEventListener(type, function (e) {
      if (!(e.target instanceof HTMLMediaElement)) return;
      media.add(e.target);
      if (e.type === 'playing') mediaRecent = new WeakRef(e.target);
      mediaReport(false);
    }, true);
  });

  // ── 뒤에서 재생 잇기(BackgroundPlay)의 조작 — 알림·잠금 화면·이어폰 단추가 앱을 거쳐 온다(media-control) ──
  // 멈출 때는 이 틀에서 재생 중인 것을 모두 멈추고 기억해 두었다가, 틀 때는 그것을(없으면 마지막으로 재생한 것을) 다시 튼다. 다시 틀기는 사람이
  // 전에 그 쪽을 만진 뒤라야 된다(자동 재생 규칙 — 소리 나는 재생은 사람의 몸짓이 있던 쪽에서만). 쪽의 재생기는 요소의 멈춤·재생을 따라간다.
  var mediaHeld = [], mediaRecent = null;
  function mediaControl(play) {
    if (!play) {
      mediaHeld = [];
      media.forEach(function (el) {
        if (el.isConnected && !el.paused && !el.ended) { mediaHeld.push(el); el.pause(); }
      });
      return;
    }
    var list = mediaHeld.filter(function (el) { return el.isConnected; });
    mediaHeld = [];
    var recent = mediaRecent && mediaRecent.deref();
    if (!list.length && recent && recent.isConnected && recent.paused && !recent.ended) list = [recent];
    list.forEach(function (el) {
      var p = el.play();
      if (p && p.catch) p.catch(function () {});
    });
  }
  // 문서가 떠난다(다른 페이지로·틀이 빠진다·뒤로 가기 캐시) — 재생 중이었으면 끝났다고 알린다. 캐시에서 돌아오면 다시 본다.
  addEventListener('pagehide', function () {
    clearInterval(mediaTimer);
    mediaTimer = 0;
    if (mediaLast) { mediaLast = ''; send({ op: 'media', audible: false, video: false }); }
  }, true);
  addEventListener('pageshow', function (e) { if (e.persisted) mediaReport(true); }, true);

  ch.addEventListener('message', function (ev) {
    var m;
    try { m = typeof ev.data === 'string' ? JSON.parse(ev.data) : null; } catch (e) { return; }
    if (!m) return;
    switch (m.op) {
      case 'blob': giveBlob(m); break;
      case 'next': pump(m.id); break;
      case 'stop': sends.delete(m.id); break;
      case 'rec-probe': probe(m); break;
      case 'rec-start': startRec(m); break;
      case 'rec-stop': stopRec(m.id); break;
      case 'cap-arm': arm(m.token); break;
      case 'blob-arm': armBlob(m.token); break;
      case 'pick-start': if (isTop) pickStart(m); break;
      case 'pick-stop': if (pk && pk.id === m.id) pickStop(); break;
      case 'pick-at': if (pk && pk.id === m.id) pickAt(m); break;
      case 'pick-up': if (pk && pk.id === m.id) pickMove(1); break;
      case 'pick-down': if (pk && pk.id === m.id) pickMove(-1); break;
      case 'pick-scroll': if (pk && pk.id === m.id) pickScroll(m); break;
      case 'pick-check': if (pk && pk.id === m.id) pickCheck(m); break;
      case 'media-ask': mediaReport(true); break;
      case 'media-control': mediaControl(!!m.play); break;
    }
  });
  send({ op: 'hello', top: isTop });
})();
"""

    const val MSE_HOOK = """
(function () {
  'use strict';
  var TOKEN = '__IROIRO_TOKEN__';
  var port = null, queue = [], queued = 0, LIMIT = 96 * 1024 * 1024;
  window.addEventListener('message', function (e) {
    if (port || e.data !== TOKEN || !e.ports || !e.ports.length) return;
    e.stopImmediatePropagation();
    port = e.ports[0];
    var q = queue || [];
    queue = null;
    for (var i = 0; i < q.length; i++) port.postMessage(q[i].m, q[i].t);
  }, true);
  // 통로가 오지 않으면(가로채기를 건 사이트를 떠나는 사이에 이 갈고리가 든 문서 — 앱이 통로를 건네지 않는다) 쌓지 않는다.
  setTimeout(function () { if (!port) { queue = null; queued = 0; } }, 20000);
  function post(m, transfer) {
    if (port) { port.postMessage(m, transfer || []); return; }
    if (!queue) return;
    if (m.buf) { queued += m.buf.byteLength; if (queued > LIMIT) return; }
    queue.push({ m: m, t: transfer || [] });
  }
  var ids = new WeakMap();
  function idOf(o) {
    var v = ids.get(o);
    if (!v) { v = (Math.floor(Math.random() * 2147483646) + 1); ids.set(o, v); }
    return v;
  }
  function wrap(proto, name, after) {
    if (!proto) return;
    var d = Object.getOwnPropertyDescriptor(proto, name);
    if (!d || typeof d.value !== 'function') return;
    var f = d.value;
    Object.defineProperty(proto, name, {
      value: function () { var r = f.apply(this, arguments); try { after.call(this, arguments, r); } catch (e) {} return r; },
      writable: d.writable, enumerable: d.enumerable, configurable: d.configurable
    });
  }
  ['MediaSource', 'ManagedMediaSource', 'WebKitMediaSource'].forEach(function (n) {
    var C = window[n];
    if (!C || !C.prototype) return;
    wrap(C.prototype, 'addSourceBuffer', function (args, sb) { post({ op: 'sb', ms: idOf(this), sb: idOf(sb), mime: String(args[0]) }); });
    wrap(C.prototype, 'endOfStream', function () { post({ op: 'eos', ms: idOf(this) }); });
  });
  var SB = window.SourceBuffer;
  if (SB && SB.prototype) {
    var d = Object.getOwnPropertyDescriptor(SB.prototype, 'appendBuffer');
    if (d && typeof d.value === 'function') {
      var append = d.value;
      Object.defineProperty(SB.prototype, 'appendBuffer', {
        value: function (data) {
          try {
            var u = data instanceof ArrayBuffer ? new Uint8Array(data)
              : ArrayBuffer.isView(data) ? new Uint8Array(data.buffer, data.byteOffset, data.byteLength) : null;
            if (u && u.byteLength) { var c = u.slice(); post({ op: 'data', sb: idOf(this), tso: this.timestampOffset, buf: c.buffer }, [c.buffer]); }
          } catch (e) {}
          return append.apply(this, arguments);
        },
        writable: d.writable, enumerable: d.enumerable, configurable: d.configurable
      });
    }
    wrap(SB.prototype, 'changeType', function (args) { post({ op: 'type', sb: idOf(this), mime: String(args[0]) }); });
  }
  if (window.HTMLMediaElement) wrap(HTMLMediaElement.prototype, 'setMediaKeys', function (args) { if (args[0]) post({ op: 'drm' }); });
})();
"""
    const val BLOB_HOOK = """
(function () {
  'use strict';
  var TOKEN = '__IROIRO_TOKEN__';
  var kept = new Map();
  var create = URL.createObjectURL, revoke = URL.revokeObjectURL;
  URL.createObjectURL = function (o) {
    var u = create.apply(this, arguments);
    try { if (o instanceof Blob) kept.set(u, { b: o, t: Date.now(), gone: 0 }); } catch (e) {}
    return u;
  };
  URL.revokeObjectURL = function (u) {
    try { var k = kept.get(u); if (k) k.gone = Date.now(); } catch (e) {}
    return revoke.apply(this, arguments);
  };
  function prune() {
    var now = Date.now();
    kept.forEach(function (v, k) { if (v.gone && now - v.gone > 120000) kept.delete(k); });
  }
  var port = null;
  window.addEventListener('message', function (e) {
    if (port || e.data !== TOKEN || !e.ports || !e.ports.length) return;
    e.stopImmediatePropagation();
    port = e.ports[0];
    port.onmessage = function (ev) {
      var m = ev.data;
      if (!m || m.op !== 'get') return;
      prune();
      var k = kept.get(m.url);
      port.postMessage({ id: m.id, blob: k ? k.b : null });
    };
  }, true);
})();
"""
}
