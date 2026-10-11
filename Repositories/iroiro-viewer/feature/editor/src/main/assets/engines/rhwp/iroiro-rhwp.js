'use strict';
// rhwp 스튜디오 ↔ 편집기 화면(EditorActivity) 어댑터(우리 코드). tools/build_rhwp.py 가 스튜디오의 index.html 의 </body> 앞에 이 파일을 부르는
// 줄을 넣는다 — 고전 스크립트라 문서를 다 읽은 뒤, 스튜디오의 모듈(지연 실행)이 돌기 전에 돈다. 그래서 스튜디오가 시작하며 읽는 것을 여기서 먼저
// 고칠 수 있다.
//
// 하는 일:
//   1. 주소의 질의를 스튜디오의 것으로 바꾼다(?url=/__io/in/<표>&filename=<이름>, history.replaceState — 다시 읽지 않는다). 스튜디오는 그
//      문서를 열고 자동저장 복구·빈 문서를 건너뛴다.
//   2. 문서의 사본이 남는 저장소를 막는다 — IndexedDB 를 감춘다(자동저장·최근 문서·문서 이력이 스스로 메모리로 물러난다). 설정에서도
//      자동저장을 끈다. 처음 한 번은 폰에 맞는 기본값(폭 맞춤, 스킨 안내 건너뛰기)을 둔다.
//   3. 저장을 앱으로 돌린다 — 파일 시스템 접근 API 의 showSaveFilePicker 를 흉내 낸다. 스튜디오의 저장 명령(암호 문서면 암호를 다시 묻는다)이
//      그 손잡이에 쓰면 바이트가 덩이로 나뉘어 앱(iroiroHost)으로 가고, 앱이 '저장됨' 이라고 답해야 쓰기가 끝난다. 이 길의 실패는 무엇이든
//      AbortError 로 끝낸다 — 그 밖의 오류면 스튜디오가 '내려받기' 로 물러나 저장하지 않은 채 '저장됨' 으로 표시한다(file.ts). 쪽에서 난
//      실패(앱은 모른다)는 앱에 save-error 로 알린다.
//   4. 앱에서 할 수 없는 메뉴(새 문서·열기·최근 문서·문서 비교)를 걷고 그 단축키를 삼킨다. 인쇄 메뉴는 하나로 모아 11 로 돌린다.
//      새 창은 열지 않는다 — 문서의 링크는 앱이 주소를 보여 주고 물은 뒤 브라우저 앱으로 넘긴다(13).
//   5. 두 손가락 오므리기·벌리기를 스튜디오의 배율로 바꾼다(Ctrl+휠 — 다시 그리므로 글자가 흐려지지 않는다).
//   6. 보기 모드면 도구 상자를 숨기고 메뉴는 읽는 데 쓰는 것(인쇄·내보내기 포함)만 남기며, 문서에 대한 입력을 막는다(저장도 하지 않는다).
//      편집 모드도 끌어 놓은 파일은 그림만 받는다(17).
//   7. 앱에 알린다 — 문서를 열었다(ready), 고친 것이 있다/없다(state). 열지 못했으면 ready 대신 open-failed 와 그 갈래를 보내고 까닭을 쪽에
//      적는다(14).
//   8. 손가락 몸짓을 마우스로 바꾼다 — 길게 눌렀다 떼면 우클릭, 두 번 톡 친 뒤 끌면 블록 지정, 두 번 톡이면 더블 클릭.
//   9. 자판은 저절로 올라오지 않는다 — 상태 표시줄의 자판 단추로만 올리고 내린다(편집 모드). 누르는 자리는 48 CSS px 를 넘긴다.
//  10. '붙이기' 는 앱이 클립보드를 읽어 준다(웹 쪽은 읽지 못한다).
//  11. 인쇄·PDF — 스튜디오가 숨은 틀에 그린 인쇄 문서를 앱으로 보내고, 앱이 안드로이드 인쇄(PDF로 저장 포함)로 넘긴다.
//  12. 내보내기(HTML·Word 문서) — 스튜디오의 내려받기를 가로채 앱으로 보내고, 앱이 사용자가 고른 곳(시스템 저장 창)에 쓴다.
//  13. 문서의 링크 — 위 4.
//  15. 뒤로 가기 — 앱이 먼저 묻는다({"op":"back"}). 열린 대화상자·찾기 창·메뉴를 하나 닫고 닫았다고 답한다(닫을 것이 없으면 앱이 닫는다).
//  16. 읽던 자리 — 보기 → 편집이 넘긴다({"op":"where"} 의 답, 편집 쪽은 주소의 at).
//
// 앱과의 약속은 HostBridge·SaveAssembler 의 KDoc 에 있다(probe 엔진도 같은 약속을 쓴다).
(() => {
  const host = window.iroiroHost; // WebMessageListener 가 넣는다 — 이 출처의 주 틀에만.
  const params = new URLSearchParams(location.search);
  const token = params.get('doc') || '';
  const mode = params.get('mode') === 'view' ? 'view' : 'edit';
  const name = (params.get('name') || '').trim() || 'document.' + (params.get('kind') || 'hwp');
  // 스튜디오에 넘기는 이름 — 확장자가 스튜디오가 아는 것(.hwp·.hwpx·.hml)이 아니면 넘겨받은 형식의 것으로 바꾼다. 한글 서식
  // (.hwt·.hwtx·.owpml)을 파일 쪽이 HWP·HWPX 로 넘긴다(core:io 의 EditorApp 별칭). 스튜디오는 저장할 이름을 이 이름의 확장자만 갈아 짓고
  // 그 셋이 아니면 덧붙인다('신청서.hwt.hwp') — 바꿔 두면 고쳐 저장한 것이 원본 곁의 '신청서.hwp' 가 된다. 보이는 이름(name)은 그대로다.
  const studioName = /\.(hwp|hwpx|hml)$/i.test(name)
    ? name
    : name.replace(/\.[^./\\]*$/, '') + '.' + (params.get('kind') === 'hwpx' ? 'hwpx' : 'hwp');
  const editing = mode === 'edit' && !!host;

  const send = (o) => { if (host) host.postMessage(JSON.stringify(o)); };
  const log = (msg) => send({ op: 'log', msg: String(msg) });

  // ---- 14. 열기 실패 -----------------------------------------------------------------------------------------------------------
  // 스튜디오는 ?url= 로 넘긴 문서를 열지 못하면 까닭을 토스트로 알리고(main.ts 의 showLoadError — 오류 기록에 '[main] 파일 로드 실패' 를 남긴다)
  // 빈 새 문서('새 문서.hwp')를 연다(openBlankDocumentIfIdle). 그 빈 문서를 넘겨받은 문서로 알면 앱이 '편집' 단추를 띄우고, 고쳐 저장하면 빈 문서가
  // 원본 자리에 쓰인다(첫 저장이라 고치기 전 판은 휴지통에 남지만). 그 기록으로 실패를 알아채 — 인사(ready) 대신 open-failed 를 보내고(7), 빈 문서를
  // 덮어 까닭을 적고(showOpenFailure), 저장을 막는다(canSave). 갈래만 가른다 — 스튜디오의 글을 그대로 옮기지 않는다. 스튜디오가 그 기록의
  // 글을 바꾸면 알아채지 못한다(예전처럼 빈 문서가 남는다) — 지금 판으로 확인했다(풀 수 없는 방식으로 잠긴 HWP 표본, 2026-10-09).
  //
  // 문서 암호를 묻는 창을 닫으면 스튜디오는 실패 기록 없이('문서 열기를 취소했습니다' 만 상태 줄에 적고) 같은 빈 문서를
  // 연다 — 그래서 빈 문서를 지은 기록('[WasmBridge] 새 문서 생성')도 본다. 이 어댑터는 새 문서 명령을 걷었으므로(4) 그 기록은 열기가 실패했다는
  // 뜻이다. 암호를 물었는데 실패 기록이 없으면 '그만뒀다'(cancelled — 앱이 '다시 열기' 를 단다), 묻지도 않았으면 'other'.
  let openFailed = null; // 'locked' | 'corrupt' | 'other' — 스튜디오가 남긴 실패 기록의 갈래
  let blankCreated = false; // 스튜디오가 빈 새 문서를 지었다
  let passwordAsked = false; // 문서 암호를 물었다(#hwp-password-input — 스튜디오의 암호 창)
  {
    const original = console.error;
    console.error = function (...args) {
      if (!openFailed && typeof args[0] === 'string' && args[0].startsWith('[main] 파일 로드 실패')) {
        const why = args.slice(1).map((a) => String((a && a.message) || a)).join(' ');
        openFailed = /암호|password|encrypt|DRM/i.test(why) ? 'locked' : /^HTTP \d|fetch/i.test(why) ? 'other' : 'corrupt';
      }
      return original.apply(this, args);
    };
    const info = console.log;
    console.log = function (...args) {
      if (!blankCreated && typeof args[0] === 'string' && args[0].startsWith('[WasmBridge] 새 문서 생성')) blankCreated = true;
      return info.apply(this, args);
    };
    // 암호 창은 body 에 바로 붙는다(ModalDialog.show). 한 번 보면 그만 본다.
    const asked = new MutationObserver(() => {
      if (!document.getElementById('hwp-password-input')) return;
      passwordAsked = true;
      asked.disconnect();
    });
    asked.observe(document.body, { childList: true });
  }
  /** 열지 못한 갈래 — 앱이 단추를 고른다(cancelled 면 '다시 열기', 그 밖은 '다른 앱으로 열기'). 열었으면 null. */
  const failure = () => openFailed || (blankCreated ? (passwordAsked ? 'cancelled' : 'other') : null);
  /** 앱으로 저장해도 되는가 — 편집 모드이고 문서를 열었다(열지 못했으면 스튜디오가 연 빈 문서다). */
  const canSave = () => editing && !failure();

  const FAILURE_TEXT = {
    cancelled: '암호를 넣지 않아 문서를 열지 않았습니다.',
    locked: '문서를 열지 못했습니다 — 이 편집기가 풀지 못하는 방식으로 잠긴 문서입니다.',
    corrupt: '문서를 열지 못했습니다 — 깨졌거나 이 편집기가 읽지 못하는 문서입니다(한글 97 이전 판 등).',
    other: '문서를 열지 못했습니다.',
  };

  /** 열지 못했다 — 빈 문서를 덮어 까닭을 적는다(ONLYOFFICE 어댑터의 #io-error 와 같은 자리). 단추는 앱이 단다. 뒤로 가면 앱이 닫는다. */
  function showOpenFailure() {
    const reason = failure() || 'other';
    send({ op: 'open-failed', reason });
    const style = document.createElement('style');
    style.textContent = '#iroiro-open-failed{position:fixed;inset:0;z-index:2147483647;display:flex;align-items:center;' +
      'justify-content:center;padding:24px;box-sizing:border-box;text-align:center;font-size:16px;line-height:1.5;' +
      'background:#f2f2f2;color:#202124}' +
      '@media (prefers-color-scheme: dark){#iroiro-open-failed{background:#202124;color:#e8eaed}}';
    document.head.appendChild(style);
    const box = document.createElement('div');
    box.id = 'iroiro-open-failed';
    box.setAttribute('role', 'alert');
    box.textContent = FAILURE_TEXT[reason] || FAILURE_TEXT.other;
    document.body.appendChild(box);
  }

  // ---- 1. 주소 ------------------------------------------------------------------------------------------------------------
  {
    const q = new URLSearchParams();
    if (/^[0-9a-f]{16,64}$/.test(token)) {
      q.set('url', '/__io/in/' + token);
      q.set('filename', studioName);
    }
    const query = q.toString();
    history.replaceState(null, '', location.pathname + (query ? '?' + query : ''));
  }

  // ---- 2. 남는 저장소 -------------------------------------------------------------------------------------------------------
  try {
    Object.defineProperty(window, 'indexedDB', { value: undefined, writable: false, configurable: false });
  } catch (e) {
    log('indexedDB 를 감추지 못했다: ' + e);
  }
  try {
    const KEY = 'rhwp-settings';
    let s = null;
    try { s = JSON.parse(localStorage.getItem(KEY) || 'null'); } catch (_) { s = null; }
    if (!s || typeof s !== 'object') s = { version: 1 };
    s.autosave = Object.assign({}, s.autosave, { recoveryEnabled: false, idleSaveEnabled: false });
    // 'PDF로 저장' 앞의 안내는 데스크톱 브라우저의 인쇄 창 이야기다 — 이 앱은 안드로이드 인쇄 창을 띄운다(11). 안내 없이 곧바로 준비한다.
    s.dialog = Object.assign({}, s.dialog, { showPdfPrintGuidance: false });
    if (!localStorage.getItem('iroiro-defaults')) {
      s.view = Object.assign({}, s.view, { zoomFitMode: 'fitWidth' });
      s.theme = Object.assign({}, s.theme, { skinChosen: true });
      localStorage.setItem('iroiro-defaults', '1');
    }
    localStorage.setItem(KEY, JSON.stringify(s));
  } catch (e) {
    log('설정을 고치지 못했다: ' + e);
  }

  // ---- 4. 새 창·문서의 링크(13) ------------------------------------------------------------------------------------------------
  // 스튜디오는 문서의 링크(와 하이퍼링크 창의 '열어 보기')를 새 창으로 연다 — window.open('about:blank') 를 받은 뒤 그 창의
  // location.replace(주소). 이 앱에는 새 창도 인터넷도 없다. 그 자리에 가짜 창을 돌려주고, 주소가 오면 앱에 묻는다 — 앱이 주소를 보여 주고
  // 사용자가 '열기' 를 누르면 브라우저 앱으로 넘긴다(이 앱은 인터넷 없이 그대로). 묻는 길은 prompt 다: 답을 받을 때까지 쪽이 멈추므로,
  // 그만두면 여기서 던져 스튜디오가 방문 빛깔(글자 서식 — 고친 것으로 친다)을 칠하지 않게 한다(던진 글은 스튜디오가 알림으로 보인다).
  // 문서가 스크립트를 돌릴 길은 없다(CSP) — 이 prompt 를 부르는 것은 이 어댑터뿐이고, 앱도 이 출처의 쪽에서 온 것만 받는다.
  const LINK_PROMPT = 'iroiro:open-link';
  const openLink = (href) => {
    let url;
    try { url = new URL(String(href)); } catch (_) { throw new Error('열 수 없는 주소입니다.'); }
    if ((url.protocol !== 'http:' && url.protocol !== 'https:') || url.username || url.password || url.origin === location.origin) {
      throw new Error('웹 주소(http·https)만 엽니다.');
    }
    const answer = host ? window.prompt(LINK_PROMPT, url.href) : null;
    if (answer === 'opened') return;
    throw new Error(answer === 'no-app' ? '링크를 열 앱이 없습니다.' : '링크를 열지 않았습니다.');
  };
  /** 링크를 여는 데만 쓰는 가짜 창 — 주소를 넣으면 openLink. */
  const linkWindow = () => ({
    opener: null,
    closed: false,
    document: null,
    close() {},
    focus() {},
    blur() {},
    postMessage() {},
    location: {
      replace: (u) => openLink(u),
      assign: (u) => openLink(u),
      get href() { return 'about:blank'; },
      set href(u) { openLink(u); },
    },
  });
  window.open = (target) => {
    const u = target == null ? '' : String(target);
    if (u === '' || u === 'about:blank') return linkWindow();
    // 주소를 곧바로 주는 부름 — 이 출처의 쪽(인쇄 미리보기 창 print.html 따위 — 11 이 그 길을 막았다)은 열지 않고, 바깥 주소면 같은 길로
    // 묻는다. 열지 않았으면 '열지 못함'(null).
    try {
      if (new URL(u, location.href).origin === location.origin) return null;
      openLink(u);
      return linkWindow();
    } catch (e) {
      log('새 창: ' + e.message);
      return null;
    }
  };

  // ---- 3. 저장 --------------------------------------------------------------------------------------------------------------
  // 덩이 하나 1 MiB — 덩이마다 앱의 답을 기다리므로 왕복이 시간을 정한다(인쇄 문서 46 MB 가 256 KiB 덩이로 3.7초 — K05).
  const CHUNK = 1024 * 1024;
  let saveId = 0;
  let transactions = 0; // 이번 쪽에서 앱으로 보내기 시작한 저장의 수 — 앱이 청한 저장이 실제로 시작됐는지 본다.
  const waiters = new Map(); // "id:seq" → resolve(성공 여부)

  const wait = (key) => new Promise((resolve) => waiters.set(key, resolve));
  const abort = (why) => new DOMException(why, 'AbortError');
  const isAbort = (e) => e instanceof DOMException && e.name === 'AbortError';
  const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');

  /**
   * 저장 길에서 난 오류를 스튜디오에 돌려줄 꼴로 — 늘 AbortError. AbortError 는 앱이 거절한 것(앱이 이미 알고 까닭을
   * 띄웠다)이라 그대로 둔다. 그 밖(메모리·해시·통로가 끊김 — 쪽에서 난 것)은 앱이 모르므로 save-error 로 알린다 — 앱은 받던 것을 버리므로
   * 보내기 줄의 차례에 맞춰 보낸다(먼저 시작한 인쇄·내보내기를 끊지 않게).
   */
  function saveFailure(e) {
    if (isAbort(e)) return e;
    log('저장하지 못했다: ' + ((e && e.stack) || e));
    const report = line.then(() => send({ op: 'save-error' }));
    line = report.catch(() => {});
    return abort('저장하지 못했다: ' + ((e && e.message) || e));
  }

  function kindOf(fileName) {
    const m = /\.(hwpx|hwp)$/i.exec(fileName || '');
    return m ? m[1].toLowerCase() : null;
  }

  /**
   * 바이트를 앱으로 — save-begin(머리에 [head] 를 싣는다 — 용도 purpose 와 이름 따위), 덩이마다 ack, save-end, 그리고 앱의 saved.
   * 앱이 못 받으면 AbortError. 앱은 한 번에 하나만 받으므로(SaveAssembler) 차례로 줄 세운다 — 저장 중에 내보내기를 눌러도 저장이 끊기지 않게.
   */
  let line = Promise.resolve();
  function transfer(bytes, head) {
    const run = line.then(() => transferNow(bytes, head));
    line = run.catch(() => {});
    return run;
  }

  async function transferNow(bytes, head) {
    if (!host) throw abort('앱이 없다');
    const id = ++saveId;
    const sha = hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)));
    const begun = wait(id + ':-1');
    send(Object.assign({}, head, { op: 'save-begin', id, size: bytes.length, sha256: sha }));
    if (!(await begun)) throw abort('앱이 받지 않았다');
    let seq = 0;
    for (let at = 0; at < bytes.length; at += CHUNK) {
      const part = bytes.subarray(at, Math.min(bytes.length, at + CHUNK));
      const buf = new ArrayBuffer(8 + part.length);
      const view = new DataView(buf);
      view.setUint32(0, id);
      view.setUint32(4, seq);
      new Uint8Array(buf, 8).set(part);
      const acked = wait(id + ':' + seq);
      host.postMessage(buf);
      if (!(await acked)) throw abort('앱이 덩이 ' + seq + ' 을 받지 못했다');
      seq++;
    }
    const ended = wait(id + ':end');
    send({ op: 'save-end', id });
    if (!(await ended)) throw abort('앱이 받기를 끝내지 못했다');
  }

  /** 문서 저장 — 형식은 이름의 확장자(스튜디오가 고른 형식). 보기 모드는 저장하지 않는다. 실패는 늘 AbortError([saveFailure]). */
  async function sendToHost(bytes, fileName) {
    const kind = kindOf(fileName);
    if (!canSave() || !kind) throw abort('저장할 수 없다(보기 모드·열지 못한 문서이거나 형식이 HWP·HWPX 가 아니다)');
    transactions++;
    try {
      await transfer(bytes, { purpose: 'save', kind, name: fileName });
    } catch (e) {
      throw saveFailure(e);
    }
  }

  class HostWritable {
    constructor(fileName) { this.fileName = fileName; this.parts = []; }
    async write(data) {
      try {
        // 스튜디오는 바이트·Blob 을 쓴다. 쓰기 명령 꼴({ type: 'write', data })도 받는다(파일 시스템 접근 API 의 다른 꼴).
        const d = data && typeof data === 'object' && data.type === 'write' && 'data' in data ? data.data : data;
        this.parts.push(d instanceof Blob ? d : new Blob([d]));
      } catch (e) {
        throw saveFailure(e);
      }
    }
    async close() {
      let bytes;
      try {
        bytes = new Uint8Array(await new Blob(this.parts).arrayBuffer());
      } catch (e) {
        throw saveFailure(e);
      } finally {
        this.parts = [];
      }
      await sendToHost(bytes, this.fileName);
    }
    async abort() { this.parts = []; }
  }

  /** 스튜디오가 저장할 곳 — 이름만 있는 손잡이. 쓰면 앱으로 간다. */
  class HostFileHandle {
    constructor(fileName) { this.kind = 'file'; this.name = fileName; }
    async getFile() { throw new DOMException('읽을 수 없는 손잡이다', 'NotAllowedError'); }
    async createWritable() {
      if (!canSave()) throw abort('보기 모드이거나 열지 못한 문서');
      return new HostWritable(this.name);
    }
    async isSameEntry(other) { return other === this; }
    async queryPermission() { return 'granted'; }
    async requestPermission() { return 'granted'; }
  }

  Object.defineProperty(window, 'showSaveFilePicker', {
    configurable: false,
    writable: false,
    value: async (options) => {
      if (!canSave()) throw abort('보기 모드이거나 열지 못한 문서');
      const suggested = String((options && options.suggestedName) || studioName).split(/[\\/]/).pop();
      return new HostFileHandle(suggested);
    },
  });

  /** 앱이 청한 저장(닫기 물음의 '저장') — 스튜디오의 저장 명령을 그대로 부른다. 대화상자(암호)를 닫아 버리면 그만뒀다고 알린다. */
  function saveForHost() {
    const studio = window.rhwpStudio;
    if (!canSave() || !studio || !studio.automation) {
      send({ op: 'save-cancelled' });
      return;
    }
    const before = transactions;
    const result = studio.automation.execute('file:save', undefined, { allowDialog: true });
    if (!result || !result.ok) {
      log('file:save 를 부르지 못했다: ' + JSON.stringify(result));
      send({ op: 'save-cancelled' });
      return;
    }
    // 명령은 기다리지 않고 돌아온다. 저장이 시작됐으면(앱이 결과를 안다) 끝, 대화상자가 떠 있으면 기다리고, 둘 다 아니면 그만둔 것이다.
    const started = Date.now();
    const timer = setInterval(() => {
      if (transactions !== before) {
        clearInterval(timer);
      } else if (!document.querySelector('.modal-overlay') && Date.now() - started > 600) {
        clearInterval(timer);
        send({ op: 'save-cancelled' });
      }
    }, 150);
  }

  /** 앱이 알려 주는 자판의 보임(9) — 그 단추가 받는다. */
  let onIme = null;
  /** 앱이 읽어 준 클립보드(10). */
  let onPaste = null;

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
        saveForHost();
      } else if (m.op === 'ime') {
        if (onIme) onIme(m.shown === true);
      } else if (m.op === 'paste-data') {
        if (onPaste) onPaste(typeof m.text === 'string' ? m.text : '', typeof m.html === 'string' ? m.html : '');
      } else if (m.op === 'where') {
        send({ op: 'position', at: where() });
      } else if (m.op === 'back') {
        let handled = false;
        try { handled = closeTopmost(); } catch (err) { log('뒤로 가기: ' + err); }
        send({ op: 'back-result', handled });
      }
    });
  }

  // ---- 15. 뒤로 가기 -----------------------------------------------------------------------------------------------------------
  // 예전에는 메뉴·찾기 창을 연 채 뒤로 가면 문서가 통째로 닫혔다. 앱이 먼저 묻고({"op":"back"}), 여기서 맨 위에 열린 것을 하나
  // 닫는다: 대화상자(맨 위 것 — 스튜디오의 대화상자는 모두 Escape 로 닫힌다. 암호 창이면 '그만두기' 가 된다) → 맥락 메뉴 → 찾기 창(닫기 단추)
  // → 메뉴 막대·도구 상자의 펼침(바깥을 누른 것처럼 — 스튜디오가 문서의 mousedown 으로 접는다). 열지 못한 덮개(14) 위에서는 닫을 것이 없다.
  // 닫으려 한 것이 닫히지 않으면(스튜디오가 판을 바꿨다) 다음 뒤로 가기는 '없다' 로 답한다 — 뒤로 가기가 먹통이 되지 않게.
  let backTried = null;
  const visible = (el) => !!el && el.isConnected && el.getClientRects().length > 0;
  function escapeKey() {
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true, composed: true }));
  }
  function tapOutside() {
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, composed: true, view: window, button: 0, buttons: 1 }));
  }
  /** 지금 맨 위에 열린 것 — { el, close, open }(open 은 아직 열려 있는가). 없으면 null. */
  function topmostOpen() {
    const overlays = Array.from(document.querySelectorAll('body > .modal-overlay, body > .cp-overlay')).filter(visible);
    if (overlays.length) {
      const el = overlays[overlays.length - 1];
      return {
        el,
        open: () => visible(el),
        close: () => {
          escapeKey();
          // Escape 를 받지 않는 대화상자 — 제목 줄의 닫기 단추.
          if (el.isConnected) { const x = el.querySelector('.dialog-close'); if (visible(x)) x.click(); }
        },
      };
    }
    const context = Array.from(document.querySelectorAll('.context-menu')).filter(visible).pop();
    if (context) return { el: context, open: () => visible(context), close: () => { escapeKey(); if (context.isConnected) tapOutside(); } };
    const find = Array.from(document.querySelectorAll('.find-dialog')).filter(visible).pop();
    if (find) return { el: find, open: () => visible(find), close: () => { const x = find.querySelector('.dialog-close'); if (x) x.click(); } };
    const menu = Array.from(document.querySelectorAll('#menu-bar .menu-item.open, .tb-split.open, #style-bar .open, #status-bar .open')).pop();
    if (menu) return { el: menu, open: () => menu.classList.contains('open'), close: tapOutside };
    return null;
  }
  /** 맨 위에 열린 것을 하나 닫았으면 참. */
  function closeTopmost() {
    if (document.getElementById('iroiro-open-failed')) return false;
    const top = topmostOpen();
    if (!top || top.el === backTried) { backTried = null; return false; }
    top.close();
    backTried = top.el;
    // 닫혔으면 잊는다 — 같은 메뉴를 다시 열고 뒤로 가도 닫히게.
    setTimeout(() => { if (backTried === top.el && !top.open()) backTried = null; }, 450);
    return true;
  }

  // ---- 16. 읽던 자리 -----------------------------------------------------------------------------------------------------------
  // '편집' 은 WebView 를 새로 세우므로 예전에는 처음 쪽으로 돌아갔다. 앱이 물으면({"op":"where"}) 문서 영역의 스크롤 비율
  // ('s:<위>,<왼쪽>' — 0~1)로 답하고, 편집 쪽은 주소의 at 으로 받아 문서를 연 뒤 같은 비율로 민다. 스튜디오는 지금 쪽 번호를 밖에 내지 않고
  // (자동화의 getContext 에 없다) 커서도 밖에서 옮길 수 없어 비율로 한다 — 두 모드의 쪽 폭이 같으면(폭 맞춤) 같은 자리다. 글꼴이 늦게 와 쪽이
  // 다시 나뉘는 동안 높이가 바뀌므로 높이가 멈출 때까지 다시 민다. 사용자가 손대면 그만둔다.
  const startAt = (() => {
    const m = /^s:([0-9.]{1,12}),([0-9.]{1,12})$/.exec(params.get('at') || '');
    if (!m) return null;
    const top = Number(m[1]);
    const left = Number(m[2]);
    return top >= 0 && top <= 1 && left >= 0 && left <= 1 ? { top, left } : null;
  })();
  const scroller = () => document.getElementById('scroll-container');
  const ratio = (v) => Math.max(0, Math.min(1, v)).toFixed(5);
  /** 읽던 자리 — 문서를 열지 못했거나 아직 그리지 않았으면 null. */
  function where() {
    const c = scroller();
    if (!c || failure() || !(c.scrollHeight > 0)) return null;
    return 's:' + ratio(c.scrollTop / c.scrollHeight) + ',' + ratio(c.scrollWidth > 0 ? c.scrollLeft / c.scrollWidth : 0);
  }
  function restorePosition() {
    if (!startAt) return;
    let stopped = false;
    const stop = () => { stopped = true; };
    const types = ['touchstart', 'wheel', 'keydown', 'mousedown'];
    for (const t of types) window.addEventListener(t, stop, { capture: true, passive: true });
    const done = () => { for (const t of types) window.removeEventListener(t, stop, { capture: true }); };
    let lastHeight = -1;
    let steady = 0;
    let tries = 0;
    const tick = () => {
      if (stopped) return done();
      const c = scroller();
      if (c && c.scrollHeight > c.clientHeight) {
        const h = c.scrollHeight;
        c.scrollTop = Math.round(startAt.top * h);
        c.scrollLeft = Math.round(startAt.left * c.scrollWidth);
        steady = h === lastHeight ? steady + 1 : 0;
        lastHeight = h;
      }
      // 높이가 0.6초 그대로이거나 6초가 지났다.
      if (steady >= 4 || ++tries > 40) return done();
      setTimeout(tick, 150);
    };
    tick();
  }

  // ---- 4. 메뉴·단축키 --------------------------------------------------------------------------------------------------------
  const HIDDEN = ['file:new-doc', 'file:open', 'file:open-recent', 'file:clear-recent', 'edit:compare-documents'];
  // 보기 모드는 읽는 데 쓰는 것만 남긴다 — 입력·서식·쪽·표·도구 메뉴를 통째로 걷고, 파일·편집 메뉴는 아래 명령만.
  const VIEW_REMOVED_MENUS = ['insert', 'format', 'page', 'table', 'tool'];
  const VIEW_KEPT = new Set([
    'file:about', 'file:print-to-pdf', 'file:export-html', 'file:export-doc',
    'edit:copy', 'edit:select-all', 'edit:find', 'edit:find-again', 'edit:goto',
  ]);
  /** 인쇄 — 스튜디오의 'PDF로 저장'(숨은 틀 + print())을 앱의 인쇄로 돌린다(11). */
  const PRINT = 'file:print-to-pdf';
  const runPrint = () => {
    try { window.rhwpStudio.automation.execute(PRINT); } catch (e) { log('인쇄를 부르지 못했다: ' + e); }
  };

  function prune() {
    for (const id of HIDDEN) document.querySelectorAll('[data-cmd="' + id + '"]').forEach((el) => el.remove());
    // 인쇄는 하나로 — 'PDF로 저장…'(데스크톱 브라우저의 인쇄 창을 안내한다)을 걷고, '인쇄'(새 창의 미리보기 — 이 앱에는 새 창이 없다)가
    // 앱의 인쇄를 부르게 한다. 안드로이드 인쇄 창에서 프린터를 고르거나 'PDF로 저장' 한다. 메뉴는 누를 때 data-cmd 를 읽는다(menu-bar.ts).
    document.querySelectorAll('.md-item[data-cmd="' + PRINT + '"]').forEach((el) => el.remove());
    document.querySelectorAll('.md-item[data-cmd="file:print"]').forEach((el) => {
      el.setAttribute('data-cmd', PRINT);
      const label = el.querySelector('.md-label');
      if (label) {
        label.removeAttribute('data-i18n'); // 번역을 다시 입혀도 이 이름이 남게
        label.textContent = '인쇄·PDF로 저장…';
      }
    });
    document.querySelectorAll('.md-sub[data-recent]').forEach((el) => el.remove());
    if (!editing) {
      for (const menu of VIEW_REMOVED_MENUS) document.querySelectorAll('.menu-item[data-menu="' + menu + '"]').forEach((el) => el.remove());
      document.querySelectorAll('.menu-item[data-menu="file"] [data-cmd], .menu-item[data-menu="edit"] [data-cmd]').forEach((el) => {
        if (!VIEW_KEPT.has(el.getAttribute('data-cmd'))) el.remove();
      });
    }
    // 고아가 된 구분선(스튜디오의 embed 정리와 같은 규칙).
    document.querySelectorAll('.menu-dropdown').forEach((dropdown) => {
      dropdown.querySelectorAll('.md-sep').forEach((sep) => {
        const prev = sep.previousElementSibling;
        if (!prev || prev.classList.contains('md-sep')) sep.remove();
      });
      const last = dropdown.lastElementChild;
      if (last && last.classList.contains('md-sep')) last.remove();
    });
  }
  prune();

  // rhwp 0.8.7 의 도구 상자 첫 묶음(오려두기·복사하기·붙이기)은 어느 명령에도 이어져 있지 않다(data-cmd 가 없다 — 눌러도 아무 일이 없다).
  // 스튜디오가 단추를 잇기 전에(초기화 때 .tb-btn[data-cmd] 를 훑는다, main.ts) 명령 이름을 달아 준다.
  for (const [icon, cmd] of [['icon-cut', 'edit:cut'], ['icon-copy', 'edit:copy'], ['icon-paste', 'edit:paste']]) {
    const sprite = document.querySelector('#icon-toolbar .tb-btn:not([data-cmd]) .tb-sprite.' + icon);
    const btn = sprite && sprite.closest('.tb-btn');
    if (btn) btn.setAttribute('data-cmd', cmd);
  }

  /** 걷은 명령의 단축키 — Alt+N 새 문서, Ctrl+O 열기, Alt+Shift+V 문서 비교(한글 자판의 같은 자리 포함). */
  function isHiddenShortcut(e) {
    const key = (e.key || '').toLowerCase();
    const ctrl = e.ctrlKey || e.metaKey;
    if (e.altKey && !ctrl && !e.shiftKey && (key === 'n' || key === 'ㅜ')) return true;
    if (ctrl && !e.altKey && (key === 'o' || key === 'ㅐ')) return true;
    if (e.altKey && e.shiftKey && (key === 'v' || key === 'ㅍ')) return true;
    if (!editing && ctrl && (key === 's' || key === 'ㄴ')) return true;
    return false;
  }

  /** Ctrl+P — 스튜디오는 새 창의 인쇄 미리보기에 묶어 두었다. 앱의 인쇄로 돌린다(보기 모드에서도). */
  function isPrintShortcut(e) {
    const key = (e.key || '').toLowerCase();
    return (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey && (key === 'p' || key === 'ㅔ');
  }

  // ---- 6. 보기 모드의 입력 막기 ---------------------------------------------------------------------------------------------
  /** 보기 모드에서 그대로 두는 키 — 움직이기·고르기·복사·찾기. */
  function viewKeyAllowed(e) {
    const key = e.key || '';
    if (/^(Arrow(Up|Down|Left|Right)|Page(Up|Down)|Home|End|Escape|Shift|Control|Alt|Meta|Tab)$/.test(key)) return true;
    const ctrl = e.ctrlKey || e.metaKey;
    return ctrl && !e.altKey && /^[acfㅊㅁㄹ]$/i.test(key);
  }

  /** 대화상자(찾기·배율 등) 안의 것인가 — 보기 모드에서도 그 안의 입력은 둔다. */
  const inDialog = (t) => t instanceof Element && !!t.closest('.modal-overlay, .dialog-wrap, .find-dialog');

  /** 문서 쪽 입력칸 — 스튜디오가 키·한글 조합을 받는 숨은 textarea(input-handler.ts 가 #editor-area 에 붙인다). */
  const editorInput = () => document.querySelector('#editor-area > textarea');

  window.addEventListener('keydown', (e) => {
    if (isPrintShortcut(e)) {
      e.preventDefault();
      e.stopImmediatePropagation();
      if (!e.repeat) runPrint();
      return;
    }
    if (isHiddenShortcut(e) || (!editing && !inDialog(e.target) && !viewKeyAllowed(e))) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }, true);

  if (!editing) {
    for (const type of ['beforeinput', 'input', 'compositionstart', 'compositionupdate', 'compositionend', 'paste', 'cut', 'drop',
      'dragstart', 'dblclick', 'contextmenu']) {
      window.addEventListener(type, (e) => {
        if (inDialog(e.target)) return;
        if (e.cancelable) e.preventDefault();
        e.stopImmediatePropagation();
      }, true);
    }
    // 문서 쪽 글 입력 칸(스튜디오가 키를 받는 숨은 textarea)에 초점이 가도 자판을 띄우지 않는다.
    document.addEventListener('focusin', (e) => {
      const t = e.target;
      if (t instanceof HTMLTextAreaElement && !inDialog(t)) {
        t.readOnly = true;
        t.inputMode = 'none';
      }
    }, true);
    // 도구 상자를 숨긴다(메뉴는 위에서 읽기용만 남겼고, 상태 표시줄의 배율은 둔다) — 스튜디오의 창 제어와 같은 표시.
    const root = document.getElementById('studio-root');
    if (root) root.classList.add('rhwp-chrome-no-toolbar');
  }

  // ---- 17. 끌어 놓기(편집 모드) ---------------------------------------------------------------------------------------------------
  // 스튜디오는 문서 영역에 끌어 놓은 파일 가운데 첫 것이 HWP·HWPX·HML 이면 그 문서를 연다 — 지금 문서가 바뀌고, 저장하면 그 문서가 원본
  // 자리에 쓰인다(분할 화면에서 다른 앱의 파일을 끌어 놓는 길). 그림이면 그 자리에 넣는다 — 그림만 두고 나머지 파일은
  // 막는다. 파일이 아닌 것(글)은 그대로 둔다. 보기 모드는 6 이 모두 막는다.
  if (editing) {
    const IMAGE = /\.(png|jpe?g|gif|bmp|webp)$/i; // 스튜디오가 그림으로 받는 확장자와 같다
    window.addEventListener('drop', (e) => {
      const files = e.dataTransfer && e.dataTransfer.files;
      if (!files || files.length === 0 || IMAGE.test(files[0].name || '')) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      const c = document.getElementById('scroll-container');
      if (c) c.classList.remove('drag-over');
      log('끌어 놓은 파일을 받지 않았다(그림만 받는다)');
    }, true);
  }

  // ---- 5. 두 손가락 배율 ------------------------------------------------------------------------------------------------------
  // 스튜디오는 Ctrl+휠로 배율을 바꾼다(viewport-manager: 배율 × exp(-deltaY × 0.00625)). 손가락 사이가 r 배가 되면 deltaY = -ln(r) / 0.00625.
  {
    let last = 0;
    const gap = (t) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
    const target = () => document.getElementById('scroll-container');
    window.addEventListener('touchstart', (e) => {
      if (e.touches.length === 2) last = gap(e.touches);
    }, { capture: true, passive: true });
    window.addEventListener('touchmove', (e) => {
      if (e.touches.length !== 2 || last <= 0) return;
      e.preventDefault(); // 두 손가락으로 끄는 스크롤을 막는다
      const now = gap(e.touches);
      const r = now / last;
      const container = target();
      if (!container || !(r > 0) || Math.abs(Math.log(r)) < 0.01) return;
      last = now;
      container.dispatchEvent(new WheelEvent('wheel', {
        bubbles: true,
        cancelable: true,
        ctrlKey: true,
        deltaMode: 0,
        deltaY: -Math.log(r) / 0.00625,
        clientX: (e.touches[0].clientX + e.touches[1].clientX) / 2,
        clientY: (e.touches[0].clientY + e.touches[1].clientY) / 2,
      }));
    }, { capture: true, passive: false });
    window.addEventListener('touchend', (e) => {
      if (e.touches.length < 2) last = 0;
    }, { capture: true, passive: true });
  }

  // ---- 8. 손가락 몸짓 → 마우스 ----------------------------------------------------------------------------------------------
  // rhwp 는 마우스로만 고르고(누른 채 끌기) 우클릭으로 맥락 메뉴를 연다. 손가락은 이렇게 바꾼다(사용자 지정):
  //   톡                              그대로 — 브라우저가 만드는 마우스 누르기·떼기가 커서를 놓는다
  //   제자리에서 길게 눌렀다 떼기       우클릭 — 맥락 메뉴(고른 것이 없으면 그 자리에 커서를 먼저 놓는다). 시간이 차면 손가락 자리에 둥근 표시
  //   두 번 톡 + 두 번째를 떼지 않고 끌기 블록 지정 — 그 자리에서 마우스를 누른 채 끈다(끄는 동안 화면을 밀지 않는다)
  //   두 번 톡(끌지 않고 뗌)             더블 클릭 — 머리말·글상자·수식 편집 들어가기 따위
  //   한 손가락으로 끌기 / 두 손가락      화면 밀기(그대로) / 배율(5)
  // 넘겨받은 몸짓의 진짜 사건(브라우저가 터치에서 만든 마우스·맥락 메뉴 사건)은 삼킨다 — 우리가 만든 것(isTrusted 거짓)과 진짜 마우스의 것은
  // 그대로 간다. 보기 모드는 맥락 메뉴·더블 클릭을 막으므로(6) 블록 지정만 쓰인다(고른 뒤 편집 메뉴의 '복사하기').
  {
    const LONG_MS = 500;
    const DOUBLE_MS = 350;
    const DOUBLE_GAP = 40;
    const SLOP = 10;
    let start = null; // 지금 누른 손가락의 처음 자리
    let last = null;
    let downAt = 0;
    let moved = false;
    let double = false; // 두 번째 톡을 넘겨받았다
    let dragging = false;
    let longTimer = 0;
    let longReached = false;
    let lastTap = null; // 바로 앞의 짧은 톡 { x, y, at } — 두 번 톡 판정
    let quietUntil = 0;
    let ring = null;

    const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
    const inDoc = (t) => t instanceof Element && !!t.closest('#scroll-container') && !inDialog(t);
    const mouse = (type, p, opts = {}) => {
      const el = document.elementFromPoint(p.x, p.y) || document.body;
      const button = opts.button ?? (type === 'contextmenu' ? 2 : 0);
      el.dispatchEvent(new MouseEvent(type, {
        bubbles: true,
        cancelable: true,
        composed: true,
        view: window,
        clientX: p.x,
        clientY: p.y,
        screenX: p.x,
        screenY: p.y,
        button,
        buttons: type === 'mousedown' || type === 'mousemove' ? (button === 2 ? 2 : 1) : 0,
        detail: opts.detail ?? 1,
      }));
    };
    const showRing = (p) => {
      if (!ring) {
        ring = document.createElement('div');
        ring.setAttribute('aria-hidden', 'true');
        ring.style.cssText = 'position:fixed;width:44px;height:44px;margin:-22px 0 0 -22px;border-radius:50%;pointer-events:none;' +
          'z-index:2147483647;border:2px solid rgba(66,133,244,.9);background:rgba(66,133,244,.18)';
      }
      ring.style.left = p.x + 'px';
      ring.style.top = p.y + 'px';
      document.body.appendChild(ring);
    };
    const hideRing = () => { if (ring && ring.parentNode) ring.parentNode.removeChild(ring); };
    const quiet = () => { quietUntil = Date.now() + 400; };

    /** 우클릭 — 고른 것이 없으면 먼저 그 자리를 왼쪽으로 눌러 커서를 놓는다(맥락 메뉴의 명령이 그 자리에 듣게). */
    const rightClick = (p) => {
      let selected = false;
      try { selected = !!window.rhwpStudio.automation.getContext().hasSelection; } catch (_) { selected = false; }
      if (!selected) {
        mouse('mousedown', p);
        mouse('mouseup', p);
        mouse('click', p);
      }
      mouse('mousedown', p, { button: 2 });
      mouse('mouseup', p, { button: 2 });
      mouse('contextmenu', p);
    };

    const clear = () => {
      clearTimeout(longTimer);
      longTimer = 0;
      hideRing();
      start = null;
      double = false;
      dragging = false;
      longReached = false;
    };
    /** 몸짓이 도중에 끊겼다(두 번째 손가락·취소) — 끌던 것은 마우스 떼기로 맺는다. */
    const abandon = () => {
      if (double && last) {
        quiet();
        mouse('mouseup', last);
      }
      clear();
    };

    window.addEventListener('touchstart', (e) => {
      if (e.touches.length !== 1) {
        abandon();
        lastTap = null;
        return;
      }
      if (!inDoc(e.target)) {
        clear();
        return;
      }
      const t = e.touches[0];
      const p = { x: t.clientX, y: t.clientY };
      const now = Date.now();
      clear();
      start = p;
      last = p;
      downAt = now;
      moved = false;
      if (lastTap && now - lastTap.at < DOUBLE_MS && dist(p, lastTap) < DOUBLE_GAP) {
        double = true;
        lastTap = null;
        quiet();
        mouse('mousedown', p, { detail: 2 }); // 여기서 누른 채 — 끌면 블록, 그냥 떼면 더블 클릭
      } else if (editing) { // 보기 모드는 맥락 메뉴를 막으므로 길게 누르기를 쓰지 않는다
        longTimer = setTimeout(() => {
          longTimer = 0;
          longReached = true;
          showRing(start);
        }, LONG_MS);
      }
    }, { capture: true, passive: true });

    window.addEventListener('touchmove', (e) => {
      if (!start || e.touches.length !== 1) return;
      const t = e.touches[0];
      const p = { x: t.clientX, y: t.clientY };
      if (!moved && dist(p, start) > SLOP) {
        moved = true;
        clearTimeout(longTimer);
        longTimer = 0;
        longReached = false; // 길게 누른 뒤 움직였다 — 우클릭이 아니다
        hideRing();
      }
      if (!double) return; // 화면 밀기는 브라우저에 맡긴다
      if (e.cancelable) e.preventDefault();
      if (!moved) return;
      dragging = true;
      last = p;
      mouse('mousemove', p);
    }, { capture: true, passive: false });

    window.addEventListener('touchend', () => {
      if (!start) return;
      const p = start;
      const now = Date.now();
      if (double) {
        quiet();
        if (dragging) {
          mouse('mouseup', last);
        } else {
          mouse('mouseup', p, { detail: 2 });
          mouse('click', p, { detail: 2 });
          mouse('dblclick', p, { detail: 2 });
        }
      } else if (longReached && !moved) {
        quiet();
        rightClick(p);
      } else if (!moved && now - downAt < LONG_MS) {
        lastTap = { x: p.x, y: p.y, at: now };
      }
      clear();
    }, { capture: true, passive: true });

    window.addEventListener('touchcancel', abandon, { capture: true, passive: true });

    // 터치에서 온 진짜 사건인가 — 맥락 메뉴는 PointerEvent(pointerType), 마우스 사건은 sourceCapabilities 가 말한다(크로미엄).
    const fromTouch = (e) => e.pointerType === 'touch' || !!(e.sourceCapabilities && e.sourceCapabilities.firesTouchEvents);
    for (const type of ['mousedown', 'mouseup', 'click', 'dblclick', 'contextmenu']) {
      window.addEventListener(type, (e) => {
        // 문서 영역의 것만 — 몸짓 직후라도 맥락 메뉴·도구 상자를 빨리 누른 것은 그대로 간다.
        if (!e.isTrusted || !fromTouch(e) || !inDoc(e.target)) return;
        // 길게 누르기의 맥락 메뉴는 늘 삼킨다(떼는 순간 우리가 우클릭을 만든다). 그 밖에는 넘겨받은 몸짓 동안·직후만.
        if (type === 'contextmenu' || double || longReached || Date.now() < quietUntil) {
          e.preventDefault();
          e.stopImmediatePropagation();
        }
      }, true);
    }
  }

  // ---- 9. 자판은 단추로만 -----------------------------------------------------------------------------------------------------
  // 사용자 지정(2026-10-09): 문서를 눌러도 자판이 저절로 올라오지 않는다 — 상태 표시줄의 자판 단추를 눌러야 올라오고, 다시 누르면 내려간다.
  // 문서 쪽 입력칸에 virtualkeyboardpolicy=manual 을 걸어 초점을 받아도 자판을 띄우지 않게 하고, 단추가 navigator.virtualKeyboard 의
  // show()·hide() 를 부른다(VirtualKeyboard API — 사용자의 누름 안에서만 된다). 대화상자의 입력칸(찾기·암호·파일 이름)은 치려고 여는
  // 것이라 그대로 자판이 올라온다. 자판이 보이는지는 앱이 창의 인셋으로 알려 준다({"op":"ime"}) — 시스템의 뒤로 가기로 내려도 맞는다.
  if (editing) {
    const vk = navigator.virtualKeyboard;
    const manual = (ta) => {
      if (!ta) return;
      if (vk) {
        if (ta.getAttribute('virtualkeyboardpolicy') !== 'manual') ta.setAttribute('virtualkeyboardpolicy', 'manual');
      } else if (!ta.dataset.iroiroKeyboard) {
        ta.inputMode = 'none'; // 옛 WebView — 단추가 inputmode 를 풀고 초점을 다시 주어 띄운다
      }
    };
    // 스튜디오가 입력칸을 만들자마자(초점을 주기 전에) 수동으로 — 만든 뒤에는 그만 본다. 초점이 갈 때도 한 번 더 본다(다시 만들었을 때).
    const watcher = new MutationObserver(() => {
      const ta = editorInput();
      if (!ta) return;
      manual(ta);
      watcher.disconnect();
    });
    watcher.observe(document.documentElement, { childList: true, subtree: true });
    document.addEventListener('focusin', (e) => { if (e.target === editorInput()) manual(e.target); }, true);

    const button = document.createElement('button');
    button.type = 'button';
    button.id = 'iroiro-keyboard';
    button.className = 'stb-icon-btn';
    button.title = '자판 보이기·숨기기';
    button.setAttribute('aria-label', '자판');
    button.setAttribute('aria-pressed', 'false');
    button.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">' +
      '<rect x="2.5" y="6" width="19" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.6"/>' +
      '<path d="M6 10h1M9.5 10h1M13 10h1M16.5 10h1.5M6 13.5h1M9.5 13.5h5M16.5 13.5h1.5" stroke="currentColor" stroke-width="1.6" ' +
      'stroke-linecap="round"/></svg>';
    // 눌림(자판이 올라와 있음)은 스튜디오의 '골라 둠' 빛깔로 — 터치 뒤에 남는 :hover(status-bar.css)와 헷갈리지 않게 손가락 기기에서는
    // 그 hover 를 지운다. 누르는 자리는 48×48 CSS px 를 넘긴다(폰 폭의 상태 표시줄 단추는 44×36 이다). 보이는
    // 단추는 그대로 두고 위로 넓힌 투명한 자리(::before)가 함께 받는다 — 단추 위 문서의 몇 px 를 덮는다.
    const style = document.createElement('style');
    style.textContent = '@media (hover: none) { #iroiro-keyboard:hover { background: transparent; border-color: transparent; } } ' +
      '#iroiro-keyboard[aria-pressed="true"] { background: var(--ui-selected); border-color: var(--ui-border-strong); color: var(--ui-link); } ' +
      '#iroiro-keyboard { position: relative; z-index: 1; min-width: 48px; } ' +
      '#iroiro-keyboard::before { content: ""; position: absolute; left: 0; right: 0; bottom: 0; height: 48px; }';
    document.head.appendChild(style);
    let shown = false;
    const setShown = (v) => {
      shown = v;
      button.setAttribute('aria-pressed', String(v));
    };
    onIme = setShown;
    // 대화상자(이름·암호·찾기)의 입력칸은 치려고 여는 것이라 자판이 저절로 올라온다. 대화상자가 닫히면 자판을 그 전 모양으로 되돌린다 —
    // 문서로 돌아와서도 남으면 '자판은 단추로만' 이 깨진다(다른 이름으로 저장 뒤에 남는 것을 에뮬레이터에서 봤다). 대화상자는
    // 스튜디오가 body 에 바로 붙인다(ModalDialog.show).
    {
      let dialogOpen = false;
      let shownBefore = false;
      new MutationObserver(() => {
        const open = !!document.querySelector('body > .modal-overlay');
        if (open === dialogOpen) return;
        dialogOpen = open;
        if (open) {
          shownBefore = shown;
        } else if (!shownBefore && shown && vk) {
          // 초점이 문서 입력칸(수동)으로 돌아온 뒤에 내린다.
          setTimeout(() => { if (!shownBefore && shown) vk.hide(); }, 0);
        }
      }).observe(document.body, { childList: true });
    }
    // 누를 때 입력칸의 초점을 뺏지 않는다(뺏기면 자판을 띄울 곳이 없다).
    button.addEventListener('pointerdown', (e) => e.preventDefault());
    button.addEventListener('mousedown', (e) => e.preventDefault());
    button.addEventListener('click', () => {
      const ta = editorInput();
      if (!ta) return;
      if (shown) {
        if (vk) vk.hide();
        else { ta.inputMode = 'none'; ta.blur(); }
        return;
      }
      manual(ta);
      if (vk) {
        if (document.activeElement !== ta) ta.focus({ preventScroll: true });
        vk.show();
      } else {
        ta.dataset.iroiroKeyboard = '1';
        ta.removeAttribute('inputmode');
        ta.blur();
        ta.focus({ preventScroll: true });
        delete ta.dataset.iroiroKeyboard;
      }
    });
    const right = document.querySelector('#status-bar .stb-right');
    if (right) right.insertBefore(button, right.firstChild);
  }

  // ---- 10. 붙이기 -----------------------------------------------------------------------------------------------------------
  // 웹 쪽은 시스템 클립보드를 읽지 못한다 — 스튜디오의 '붙이기'(메뉴·도구 상자·맥락 메뉴가 부르는 document.execCommand('paste'))는
  // WebView 에서 늘 실패한다. 그 부름만 가로채 앱에 청하고({"op":"paste"}), 앱이 클립보드의 글·HTML 을 돌려주면 입력칸에 진짜 붙이기처럼
  // paste 사건을 일으킨다(스튜디오가 그 사건에서 글·HTML 을 읽는다 — input-handler-keyboard 의 onPaste). 자판의 클립보드로 붙이는 것은
  // 원래대로 된다.
  if (editing) {
    const execCommand = document.execCommand.bind(document);
    document.execCommand = (command, showUi, value) => {
      if (String(command).toLowerCase() === 'paste') {
        send({ op: 'paste' });
        return true;
      }
      return execCommand(command, showUi, value);
    };
    onPaste = (text, html) => {
      if (!text && !html) return;
      const ta = editorInput();
      if (!ta) return;
      if (document.activeElement !== ta) ta.focus({ preventScroll: true });
      const data = new DataTransfer();
      if (text) data.setData('text/plain', text);
      if (html) data.setData('text/html', html);
      ta.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
    };
  }

  // ---- 11. 인쇄·PDF ---------------------------------------------------------------------------------------------------------
  // 스튜디오의 'PDF로 저장' 은 숨은 틀(print.html)에 쪽마다 SVG 를 그려 넣고(쪽 크기는 @page 규칙) 그 틀의 window.print() 를 부른다 —
  // WebView 에서는 아무 일도 없다. 그 print() 를 가로채 그려 넣은 문서를 통째로 앱에 보낸다(덩이 통로, 용도 print). 앱은 그것을 화면에 붙이지
  // 않은 WebView 에 싣고 안드로이드 인쇄로 넘긴다 — 시스템 인쇄 창에서 프린터를 고르거나 'PDF로 저장' 한다(권한이 필요 없다).
  // 틀의 문서에는 글꼴 규칙이 없다 — 데스크톱 브라우저는 시스템 글꼴(맑은 고딕 따위, SVG 가 대체 글꼴로 적어 둔다)로 찍는다. 폰에는 그
  // 글꼴이 없으므로 화면이 쓰는 글꼴 규칙(#rhwp-web-font-faces — 문서가 청한 글꼴 이름 → 묶음의 글꼴 파일)을 옮겨 넣는다. 주소는 이 출처의
  // 절대 경로로 바꾼다 — 인쇄 문서는 다른 경로(/__io/page/)에서 열린다.
  {
    const FRAME_ID = 'rhwp-print-surface';
    const fontCss = () => {
      const s = document.getElementById('rhwp-web-font-faces');
      if (!s) return '';
      return s.textContent.replace(/url\((['"]?)([^'")]+)\1\)/g, (m, q, u) => {
        try {
          const abs = new URL(u, document.baseURI);
          return abs.origin === location.origin ? 'url("' + abs.pathname + '")' : 'url("about:invalid")';
        } catch (_) {
          return 'url("about:invalid")';
        }
      });
    };
    /** 첫 쪽의 크기(mm) — 앱이 인쇄 창의 용지를 그것으로 고른다. */
    const firstPage = (doc) => {
      const css = Array.from(doc.querySelectorAll('style'), (s) => s.textContent).join('\n');
      const m = /@page\s+rhwp-print-page-1\s*\{\s*size:\s*([\d.]+)mm\s+([\d.]+)mm/.exec(css);
      return m ? { widthMm: Number(m[1]), heightMm: Number(m[2]) } : {};
    };
    const hostPrint = (doc) => {
      if (!host) return;
      try {
        const started = Date.now();
        const style = doc.createElement('style');
        style.textContent = fontCss();
        doc.head.appendChild(style);
        const html = '<!DOCTYPE html>\n' + doc.documentElement.outerHTML;
        const head = Object.assign({ purpose: 'print', name: doc.title || name, pages: doc.querySelectorAll('.page').length }, firstPage(doc));
        const bytes = new TextEncoder().encode(html);
        log('인쇄 문서 ' + bytes.length + ' 바이트(만드는 데 ' + (Date.now() - started) + ' ms)');
        // 스튜디오는 print() 가 돌아오자마자 틀을 치운다 — 문서는 위에서 글로 떠 두었다.
        transfer(bytes, head)
          .then(() => log('인쇄 문서를 넘겼다(' + (Date.now() - started) + ' ms)'))
          .catch((e) => log('인쇄 문서를 넘기지 못했다: ' + e));
      } catch (e) {
        log('인쇄 문서를 만들지 못했다: ' + e);
      }
    };
    // 틀이 붙으면(그 load 전에) 그 창의 print 를 바꿔 둔다. 스튜디오는 틀이 다 그려진 뒤에야 print() 를 부른다.
    new MutationObserver(() => {
      const frame = document.getElementById(FRAME_ID);
      if (!frame || frame.dataset.iroiro) return;
      frame.dataset.iroiro = '1';
      frame.addEventListener('load', () => {
        try {
          frame.contentWindow.print = () => hostPrint(frame.contentDocument);
        } catch (e) {
          log('인쇄 틀을 잇지 못했다: ' + e);
        }
      });
    }).observe(document.body, { childList: true });
  }

  // ---- 12. 내보내기(HTML·Word 문서) -----------------------------------------------------------------------------------------
  // 스튜디오는 만든 파일을 <a download href="blob:…"> 로 내려받게 한다(file.ts 의 downloadBlob — 문서에 붙이지 않은 고리를 click()) —
  // WebView 에서는 아무 일도 없다. 그 click() 을 가로채 바이트를 앱에 보낸다(용도 export). 앱이 시스템의 '저장할 곳 고르기' 를 띄워 사용자가
  // 고른 자리에 쓴다(권한이 필요 없다). 받는 형식은 앱이 이름의 확장자로 가린다(HTML·Word 문서만).
  {
    const click = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      const href = this.href || '';
      if (host && this.hasAttribute('download') && href.startsWith('blob:')) {
        const fileName = this.download || '';
        // 스튜디오는 1초 뒤 주소를 거둔다 — 읽기는 지금 시작한다.
        fetch(href)
          .then((r) => r.arrayBuffer())
          .then((buf) => transfer(new Uint8Array(buf), { purpose: 'export', name: fileName }))
          .catch((e) => log('내보내지 못했다: ' + e));
        return;
      }
      return click.call(this);
    };
  }

  // ---- 7. 앱에 알리기 -------------------------------------------------------------------------------------------------------
  {
    let ready = false;
    let dirty = false;
    const timer = setInterval(() => {
      const studio = window.rhwpStudio;
      if (!studio || !studio.automation) return;
      let ctx;
      try { ctx = studio.automation.getContext(); } catch (_) { return; }
      if (!ready && ctx.hasDocument) {
        ready = true;
        // 열지 못했으면 지금 문서는 스튜디오가 대신 연 빈 문서다(14) — 인사하지 않는다(앱이 '편집' 단추를 띄우지 않는다).
        if (failure()) {
          showOpenFailure();
        } else {
          send({ op: 'ready' });
          restorePosition();
        }
      }
      const d = canSave() && !!ctx.isDirty;
      if (ready && d !== dirty) {
        dirty = d;
        send({ op: 'state', dirty: d });
      }
    }, 300);
    window.addEventListener('pagehide', () => clearInterval(timer));
  }
})();
