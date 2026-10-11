'use strict';
// 통로 시험 엔진. 진짜 엔진의 어댑터가 따를 약속(EditorActivity·HostBridge·SaveAssembler 의 KDoc)을 그대로 쓴다:
//   문서 받기 — fetch('/__io/in/<표>')
//   알리기   — iroiroHost.postMessage('{"op":"ready"}' | '{"op":"open-failed","reason":…}' | '{"op":"state","dirty":…}')
//   저장     — save-begin(크기·SHA-256·형식) → 이진 덩이(머리 8바이트: 번호·차례, 빅 엔디언) 하나마다 앱의 ack 를 기다림 → save-end
//   앱의 청 — {"op":"save"}(닫기 물음의 '저장'), {"op":"where"}(읽던 자리 — 이 엔진은 모른다고 답한다), {"op":"back"}(닫을 것이 없다고 답한다)
(function () {
  const params = new URLSearchParams(location.search);
  const token = params.get('doc') || '';
  const kind = params.get('kind') || 'probe';
  const mode = params.get('mode') || 'edit';
  const name = params.get('name') || 'document';
  const host = window.iroiroHost;
  const CHUNK = 256 * 1024;
  const $ = (id) => document.getElementById(id);

  let original = new Uint8Array(0);
  let dirty = false;
  let saving = false;
  let saveId = 0;
  const waiters = new Map(); // "id:seq" → resolve

  function send(o) { if (host) host.postMessage(JSON.stringify(o)); }
  function log(msg) { send({ op: 'log', msg: String(msg) }); }
  function status(t) { $('status').textContent = t; }
  function hex(bytes) { return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join(''); }
  async function sha256(bytes) { return hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))); }

  function setDirty(d) {
    if (d === dirty) return;
    dirty = d;
    send({ op: 'state', dirty: d });
  }

  if (host) {
    host.addEventListener('message', (e) => {
      let m;
      try { m = JSON.parse(e.data); } catch (_) { return; }
      if (m.op === 'ack') {
        const k = m.id + ':' + m.seq;
        const r = waiters.get(k);
        if (r) { waiters.delete(k); r(true); }
      } else if (m.op === 'saved') {
        const r = waiters.get(m.id + ':end');
        if (r) { waiters.delete(m.id + ':end'); r(true); }
      } else if (m.op === 'save-failed') {
        for (const [k, r] of waiters) if (k.startsWith(m.id + ':')) { waiters.delete(k); r(false); }
      } else if (m.op === 'save') {
        save();
      } else if (m.op === 'where') {
        send({ op: 'position', at: null });
      } else if (m.op === 'back') {
        send({ op: 'back-result', handled: false });
      }
    });
  }

  function wait(key) { return new Promise((resolve) => waiters.set(key, resolve)); }

  async function save() {
    if (saving || mode !== 'edit') return;
    saving = true;
    status('저장하는 중…');
    try {
      const extra = new TextEncoder().encode($('text').value);
      const out = new Uint8Array(original.length + extra.length);
      out.set(original, 0);
      out.set(extra, original.length);
      const id = ++saveId;
      const begun = wait(id + ':-1');
      send({ op: 'save-begin', id, size: out.length, sha256: await sha256(out), kind });
      if (!(await begun)) throw new Error('begin');
      let seq = 0;
      for (let at = 0; at < out.length; at += CHUNK) {
        const part = out.subarray(at, Math.min(out.length, at + CHUNK));
        const buf = new ArrayBuffer(8 + part.length);
        const view = new DataView(buf);
        view.setUint32(0, id);
        view.setUint32(4, seq);
        new Uint8Array(buf, 8).set(part);
        const acked = wait(id + ':' + seq);
        host.postMessage(buf);
        if (!(await acked)) throw new Error('chunk ' + seq);
        seq++;
      }
      const ended = wait(id + ':end');
      send({ op: 'save-end', id });
      if (!(await ended)) throw new Error('end');
      original = out;
      $('text').value = '';
      setDirty(false);
      await show(out);
      status('저장했습니다 — ' + out.length + '바이트, 덩이 ' + seq + '개');
    } catch (e) {
      log('save failed: ' + e.message);
      status('저장하지 못했습니다');
    } finally {
      saving = false;
    }
  }

  async function show(bytes) {
    $('size').textContent = bytes.length + '바이트';
    $('sha').textContent = await sha256(bytes);
    $('head').textContent = hex(bytes.subarray(0, 32)) || '(비었다)';
  }

  async function open() {
    $('name').textContent = name;
    $('kind').textContent = kind;
    $('mode').textContent = mode === 'edit' ? '편집' : '보기';
    if (!host) { status('통로가 없습니다'); return; }
    try {
      const res = await fetch('/__io/in/' + encodeURIComponent(token));
      if (!res.ok) throw new Error('HTTP ' + res.status);
      original = new Uint8Array(await res.arrayBuffer());
      await show(original);
    } catch (e) {
      status('문서를 받지 못했습니다: ' + e.message);
      log('open failed: ' + e.message);
      send({ op: 'open-failed', reason: 'other' });
      return;
    }
    if (mode === 'edit') {
      $('edit').hidden = false;
      $('text').addEventListener('input', () => setDirty($('text').value.length > 0));
      $('save').addEventListener('click', save);
    }
    send({ op: 'ready' });
    status('열었습니다');
  }

  open();
})();
