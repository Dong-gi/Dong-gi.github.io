'use strict';
// ONLYOFFICE 편집기 ↔ 편집기 화면(EditorActivity) 어댑터(우리 코드). host.html 이 부른다 — 편집기(web-apps·sdkjs)는 틀(iframe) 안에서 돌고, 이
// 어댑터는 바깥 쪽(주 틀 — 앱의 통로 iroiroHost 가 들어오는 곳)에서 돈다.
//
// 하는 일:
//   1. 문서를 받는다(/__io/in/<표>) — x2t(웹어셈블리, ONLYOFFICE 의 변환기)로 편집기의 내부 형식(Editor.bin)과 그림들로 바꾼다. 형식마다
//      길이 다르다(SPECS — 곧바로, 또는 OOXML 을 한 번 거쳐서). 암호가 걸렸으면 묻는다(8). 바꾼 결과가 비었는데 원본에는 내용이 있으면 연
//      것으로 치지 않는다(5-2). 열지 못하면 ready 대신 open-failed 와 그 갈래(cancelled·corrupt·other)를 보낸다.
//   2. 편집기를 띄운다(CryptPad 의 DocsAPI 덮개 — 문서 서버 없이 쓰게 고친 빌드). 문서 서버 대신 이 어댑터가 '가짜 서버' 노릇을 한다:
//      auth → documentOpen(Editor.bin 과 그림의 blob 주소), 잠금은 곧바로 내준다(혼자 쓴다), 바뀐 것(saveChanges)은 받았다고 답한다.
//   3. 저장 — 편집기의 '저장'(Ctrl+S 포함)은 forceSaveStart 로 온다. 편집기의 지금 모습(asc_nativeGetFile — 내부 형식)을 x2t 로 저장할
//      형식으로 바꿔 앱으로 보낸다(rhwp 와 같은 통로·같은 약속 — save-begin·덩이·save-end). 앱이 '저장됨' 이라고 답해야 끝이다. 앱이 청한
//      저장(닫기 물음의 '저장')도 같은 길. 이전 형식(doc·xls·ppt …)과 PDF 는 같은 형식으로 쓰지 않고 OOXML 로 저장한다 — Files 가 원본
//      곁에 새 파일로 둔다(형식이 바뀐 저장).
//   4. 고친 것이 있는가 — 편집기의 '문서 상태' 는 가짜 서버가 받으면 '저장됨' 으로 돌아가므로 '고침 없음' 쪽은 쓰지 않는다. 바뀐 것이 오거나
//      편집기가 '고쳐졌다' 고 하면(onDocumentStateChange — 바뀐 것이 가짜 서버에 가기 전에 온다) 고친 것, 파일 저장이 끝나면(그 사이 바뀐
//      것이 없으면) 고친 것 없음. 뒤로 가기에서 닫을 것이 없으면 아직 보내지 않은 고침도 한 번 더 본다(11).
//   5. 그림 — 문서의 그림은 x2t 가 꺼낸 것을 blob 주소로, 새로 넣는 그림(파일에서)은 앱의 그림 고르개(input file)로. 저장할 때 x2t 의 작업
//      폴더에 다시 쓴다(매크로 문서의 vbaProject.bin 도 거기 있다 — 그래서 매크로가 그대로 남는다).
//   6. 인쇄·내보내기 — 편집기의 '인쇄' 는 sdkjs 가 지은 인쇄 자료를 APP.printPdf(CryptPad 빌드의 갈고리)로 넘긴다 → x2t 가 PDF 를 짓고 앱이
//      안드로이드 인쇄 창에 넘긴다. '다운로드'(다른 형식으로)는 asc_DownloadAs 를 가로채 x2t 로 바꿔 앱의 '저장할 곳 고르기' 로 보낸다.
//   7. 틀 안의 우리 손질(iroiro-oo-inner.js — 자판은 단추로만·붙이기)과 잇는다 — 앱의 통로는 이 주 틀에만 있으므로 여기를 거친다.
//   8. 암호 — x2t 가 '암호가 필요하다'(90)·'틀렸다'(91)를 돌려주면 이 쪽의 칸으로 묻는다. PDF 는 x2t 가 그 둘을 말하지 않고 빈 문서를 내므로
//      /Encrypt 가 있는데 결과가 비었으면 묻는다. 넣은 암호는 이 쪽이 사는 동안만 들고, 저장·OOXML·ODF 내보내기에 같은 암호를 건다.
//   9. 글(TXT·CSV) — 이 빌드의 x2t 는 글을 쓸 때 글자마다 아래 1바이트만 남기고(2026-10-09 기기: '첫' U+CCAB → AB) CP949 를 읽지 못한다.
//      그래서 읽을 때는 우리가 인코딩을 가려 UTF-8 로 바꿔 넘기고, 쓸 때는 편집기의 문서 API(AscBuilder — 글: GetText, 표: 칸의 보이는 값)로
//      글을 받아 원래 모양(인코딩·BOM·줄 끝·마지막 줄바꿈·구분자)으로 우리가 쓴다.
//  10. 알림 띠 — 형식이 바뀌는 저장·글만 남는 저장처럼 저장하기 전에 알아야 할 것을 편집을 열 때 한 번 말한다.
//  11. 뒤로 가기 — 앱이 먼저 묻는다({"op":"back"}). 이 쪽의 칸(암호·물음), 편집기의 창·펼친 메뉴·파일 메뉴·왼쪽 판을 하나 닫고 닫았다고 답한다.
//  12. 읽던 자리 — 보기 → 편집이 넘긴다({"op":"where"} 의 답, 편집 쪽은 주소의 at): 문서·발표는 쪽, 표는 시트와 보이는 첫 칸.
//  13. 문서의 링크 — 틀 안의 window.open 을 가로채(iroiro-oo-inner.js) 앱이 주소를 보여 주고 물은 뒤 브라우저 앱으로 넘긴다(rhwp 어댑터의 4·13
//      과 같은 길).
//
// 앱과의 약속은 HostBridge·SaveAssembler 의 KDoc 에 있다(rhwp·probe 엔진과 같다).
(() => {
  const host = window.iroiroHost; // WebMessageListener 가 넣는다 — 이 출처의 주 틀에만.
  const params = new URLSearchParams(location.search);
  const token = params.get('doc') || '';
  const kind = (params.get('kind') || '').toLowerCase();
  const mode = params.get('mode') === 'view' ? 'view' : 'edit';
  const name = (params.get('name') || '').trim() || 'document.' + kind;
  const editing = mode === 'edit' && !!host;

  const send = (o) => { if (host) host.postMessage(JSON.stringify(o)); };
  const log = (msg) => { send({ op: 'log', msg: String(msg) }); };

  // 형식(앱이 준 형식 글자 — 확장자와 같다) → 편집기의 종류(doc), 바탕 형식(base — x2t 가 편집기의 내부 형식과 바로 주고받는 OOXML).
  //   save — 저장할 형식(없으면 같은 형식). 이전 형식·PDF 는 OOXML 로 — Files 가 원본 곁에 새 파일로 둔다.
  //   via  — 열 때 바탕 형식을 거친다(x2t 가 내부 형식으로 바로 읽지 못한다: doc 은 코드 88, pdf 는 빈 결과 — 2026-10-09 기기).
  //   text — 우리가 읽고 쓰는 글(9).  note — 알림 띠(10)의 갈래.
  const W = 'word';
  const C = 'cell';
  const S = 'slide';
  const SPECS = {
    docx: { doc: W, base: 'docx' },
    docm: { doc: W, base: 'docx', note: 'macro' },
    dotx: { doc: W, base: 'docx' },
    dotm: { doc: W, base: 'docx', note: 'macro' },
    doc: { doc: W, base: 'docx', save: 'docx', via: true, note: 'legacy' },
    dot: { doc: W, base: 'docx', save: 'dotx', via: true, note: 'legacy' },
    odt: { doc: W, base: 'docx', note: 'convert' },
    txt: { doc: W, base: 'docx', text: 'txt' },
    pdf: { doc: W, base: 'docx', save: 'docx', via: true, note: 'pdf' },
    xlsx: { doc: C, base: 'xlsx' },
    xlsm: { doc: C, base: 'xlsx', note: 'macro' },
    xltx: { doc: C, base: 'xlsx' },
    xltm: { doc: C, base: 'xlsx', note: 'macro' },
    xls: { doc: C, base: 'xlsx', save: 'xlsx', note: 'legacy' },
    xlt: { doc: C, base: 'xlsx', save: 'xltx', note: 'legacy' },
    ods: { doc: C, base: 'xlsx', note: 'convert' },
    csv: { doc: C, base: 'xlsx', text: 'csv' },
    pptx: { doc: S, base: 'pptx' },
    pptm: { doc: S, base: 'pptx', note: 'macro' },
    ppsx: { doc: S, base: 'pptx' },
    ppsm: { doc: S, base: 'pptx', note: 'macro' },
    potx: { doc: S, base: 'pptx' },
    potm: { doc: S, base: 'pptx', note: 'macro' },
    ppt: { doc: S, base: 'pptx', save: 'pptx', note: 'legacy' },
    pot: { doc: S, base: 'pptx', save: 'potx', note: 'legacy' },
    pps: { doc: S, base: 'pptx', save: 'ppsx', note: 'legacy' },
    odp: { doc: S, base: 'pptx', note: 'convert' },
  };
  // 바탕 OOXML 의 다른 판(매크로·서식·쇼) — x2t 에 이 번호를 m_nFormatTo 로 주어야 그 판의 형식으로 쓴다(없으면 내용 형식이 docx 인 채
  // 확장자만 docm 이 된다 — 2026-10-09 기기). sdkjs 의 Asc.c_oAscFileType 과 같다.
  const FORMAT_CODE = { docm: 75, dotx: 76, dotm: 77, xlsm: 261, xltx: 262, xltm: 263, pptm: 133, ppsx: 132, ppsm: 134, potx: 135, potm: 136 };
  // 암호를 걸어 쓸 수 있는 형식(OOXML 은 MS-OFFCRYPTO, ODF 는 그 매니페스트의 암호화 — 2026-10-09 기기에서 둘 다 x2t 로 확인).
  const ENCRYPTABLE = new Set(['docx', 'docm', 'dotx', 'dotm', 'xlsx', 'xlsm', 'xltx', 'xltm', 'pptx', 'pptm', 'ppsx', 'ppsm', 'potx', 'potm',
    'odt', 'ods', 'odp']);

  const spec = SPECS[kind];
  const saveKind = spec ? (spec.save || kind) : null;
  const baseName = name.replace(/\.[a-z0-9]+$/i, '').trim() || 'document';
  const saveName = saveKind ? baseName + '.' + saveKind : null;

  const busyEl = document.getElementById('io-busy');
  const busyText = document.getElementById('io-busy-text');
  const errorEl = document.getElementById('io-error');
  const busy = (text) => {
    if (text) { busyText.textContent = text; busyEl.hidden = false; } else { busyEl.hidden = true; }
  };
  /**
   * 문서를 열지 못했다(모두 인사하기 전이다) — 까닭의 글은 쪽에 적고, 앱에는 갈래만 알린다([reason] — 'cancelled' 면 앱이 '다시 열기', 그 밖은
   * '다른 앱으로 열기' 를 단다). 한 번만.
   */
  let failed = false;
  const fail = (text, e, reason) => {
    if (e) log(text + ': ' + (e && e.stack || e));
    if (failed) return;
    failed = true;
    busy(null);
    errorEl.textContent = text;
    errorEl.hidden = false;
    send({ op: 'open-failed', reason: reason || 'other' });
  };

  // ---- 1. x2t ---------------------------------------------------------------------------------------------------------------
  // emscripten 의 Module — x2t.js 가 이 객체를 쓴다(locateFile 로 wasm 의 자리, onRuntimeInitialized 로 준비됨).
  let x2tPromise = null;
  function x2t() {
    if (x2tPromise) return x2tPromise;
    x2tPromise = new Promise((resolve, reject) => {
      const m = {
        locateFile: (p) => '/x2t/' + p,
        print: (t) => log('x2t: ' + t),
        printErr: (t) => log('x2t! ' + t),
        onRuntimeInitialized: () => resolve(m),
        onAbort: (why) => reject(new Error('x2t 가 멈췄다: ' + why)),
      };
      window.Module = m;
      const s = document.createElement('script');
      // 절대 주소로 — CryptPad 가 x2t 에 덧붙인 앞 코드가 제 src 를 new URL(src) 로 읽는다(기준 없이).
      s.src = location.origin + '/x2t/x2t.js';
      s.onerror = () => reject(new Error('x2t.js 를 읽지 못했다'));
      document.head.appendChild(s);
    });
    return x2tPromise;
  }

  function rmrf(fs, path) {
    let st;
    try { st = fs.stat(path); } catch (_) { return; }
    if (fs.isDir(st.mode)) {
      for (const n of fs.readdir(path)) if (n !== '.' && n !== '..') rmrf(fs, path + '/' + n);
      fs.rmdir(path);
    } else {
      fs.unlink(path);
    }
  }

  /** x2t 작업 폴더를 새로 — /working(입력·출력)·media(그림)·fonts(PDF 만)·themes. */
  function freshWorking(m) {
    rmrf(m.FS, '/working');
    m.FS.mkdir('/working');
    m.FS.mkdir('/working/media');
    m.FS.mkdir('/working/fonts');
    m.FS.mkdir('/working/themes');
  }

  /** 그림 폴더만 새로 — 두 걸음으로 열 때 첫 걸음이 남긴 것이 문서의 그림에 섞이지 않게. */
  function freshMedia(m) {
    rmrf(m.FS, '/working/media');
    m.FS.mkdir('/working/media');
  }

  const xmlText = (t) => String(t).replace(/[<>&'"]/g, (c) => '&#' + c.charCodeAt(0) + ';');

  /** x2t 한 번 — [from] 을 [to] 로. 돌아온 값(rc — 0 이 성공, 90 암호가 필요, 91 암호가 틀림)과 결과(없으면 null). */
  function run(m, from, to, extra) {
    const params = '<?xml version="1.0" encoding="utf-8"?>'
      + '<TaskQueueDataConvert xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">'
      + '<m_sFileFrom>' + from + '</m_sFileFrom>'
      + '<m_sThemeDir>/working/themes</m_sThemeDir>'
      + '<m_sFileTo>' + to + '</m_sFileTo>'
      + (extra || '')
      + '<m_bIsNoBase64>false</m_bIsNoBase64>'
      + '</TaskQueueDataConvert>';
    try { m.FS.unlink(to); } catch (_) { /* 없다 */ }
    m.FS.writeFile('/working/params.xml', params);
    let rc;
    try {
      rc = m.ccall('main1', 'number', ['string'], ['/working/params.xml']);
    } catch (e) {
      throw new Error('x2t 변환 중 오류: ' + e);
    }
    let out = null;
    try { out = m.FS.readFile(to); } catch (_) { /* 만들지 못했다 */ }
    return { rc, out };
  }

  /** x2t 한 번 — 결과가 없으면 던진다(돌아온 값과 함께). */
  function convert(m, from, to, extra) {
    const r = run(m, from, to, extra);
    if (!r.out) throw new Error('x2t 가 결과를 만들지 못했다(코드 ' + r.rc + ')');
    return r.out;
  }

  /**
   * 편집기의 지금 모습(내부 형식)을 바이트로 — asc_nativeGetFile3(세 편집기 모두에 있다)이 base64 를 거치지 않은 바이트와 머리
   * ('DOCY;v10;<길이>;' 꼴 — x2t 가 그대로 읽는다)를 따로 준다. 옛 길(asc_nativeGetFile)은 문서를 base64 글로 지어 PDF 에서 바꾼 43쪽 문서
   * (13 MB)에서 짓기 20초, 그 글을 FS.writeFile 에 주면 emscripten 이 글자마다 수를 담은 배열로 바꿔 32초가 더 걸렸고 렌더러가 기기의
   * 메모리를 다 썼다(2026-10-09 기기). 없으면 옛 길로 가되 글은 TextEncoder 로 바꾼다.
   */
  const nativeFile = (api) => {
    // 표 편집기의 asc_nativeGetFile3 은 바이트가 아니라 base64 글(안에 또 머리가 있다)을 준다 — 바이트로 올 때만 이 길로(문서·발표).
    if (typeof api.asc_nativeGetFile3 === 'function' && !api.wbModel) {
      const r = api.asc_nativeGetFile3();
      if (r && (r.data instanceof Uint8Array || r.data instanceof ArrayBuffer) && typeof r.header === 'string') {
        const head = new TextEncoder().encode(r.header);
        let data = r.data instanceof Uint8Array ? r.data : new Uint8Array(r.data);
        // 머리가 말하는 길이까지만 — 쓰개의 버퍼가 그보다 클 수 있다.
        const len = Number(r.header.split(';')[2]);
        if (Number.isInteger(len) && len >= 0 && len < data.length) data = data.subarray(0, len);
        const out = new Uint8Array(head.length + data.length);
        out.set(head);
        out.set(data, head.length);
        return out;
      }
    }
    const d = api.asc_nativeGetFile();
    return typeof d === 'string' ? new TextEncoder().encode(d) : new Uint8Array(d);
  };

  /** 작업 폴더를 새로 하고 지금 문서(/working/document.bin)와 그림을 둔다 — 저장·인쇄·내보내기의 첫걸음. */
  function stage(m, api) {
    freshWorking(m);
    for (const [n, v] of media) m.FS.writeFile('/working/media/' + n, v.bytes);
    m.FS.writeFile('/working/document.bin', nativeFile(api));
  }

  /** 넣은 암호(8) — 걸어서 쓸 형식이면 x2t 의 저장 암호로. */
  const savePassword = (ext) => (password && ENCRYPTABLE.has(ext) ? '<m_sSavePassword>' + xmlText(password) + '</m_sSavePassword>' : '');

  /**
   * 지금 문서를 [ext] 로 — 바탕 OOXML(과 그 판)은 곧바로, 그 밖(odt·ods·odp·rtf 내보내기 …)은 바탕을 한 번 거친다(x2t 는 내부 형식을 OOXML 로만 바로
   * 쓴다 — CryptPad 와 같다). 작업 폴더는 [stage] 가 차려 두었다. 글(txt·csv)은 여기로 오지 않는다(9).
   */
  function writeAs(m, ext) {
    const base = spec.base;
    if (ext === base || FORMAT_CODE[ext]) {
      const code = FORMAT_CODE[ext] ? '<m_nFormatTo>' + FORMAT_CODE[ext] + '</m_nFormatTo>' : '';
      return convert(m, '/working/document.bin', '/working/document.' + ext, code + savePassword(ext));
    }
    const step = convert(m, '/working/document.bin', '/working/document.' + base, '');
    m.FS.writeFile('/working/step.' + base, step);
    return convert(m, '/working/step.' + base, '/working/export.' + ext, savePassword(ext));
  }

  // ---- 2. 그림 -------------------------------------------------------------------------------------------------------------
  // 이름(media/ 를 뺀 것) → { bytes, url }. 저장할 때 x2t 의 /working/media 에 다시 쓴다.
  const media = new Map();
  const mimeOf = (n) => {
    const e = (n.split('.').pop() || '').toLowerCase();
    return ({ png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', bmp: 'image/bmp', webp: 'image/webp',
      svg: 'image/svg+xml', emf: 'image/x-emf', wmf: 'image/x-wmf', tif: 'image/tiff', tiff: 'image/tiff' })[e] || 'application/octet-stream';
  };
  function addMedia(fileName, bytes) {
    const old = media.get(fileName);
    if (old) URL.revokeObjectURL(old.url);
    const url = URL.createObjectURL(new Blob([bytes], { type: mimeOf(fileName) }));
    media.set(fileName, { bytes, url });
    return url;
  }
  const mediaUrls = () => {
    const o = {};
    for (const [n, v] of media) o['media/' + n] = v.url;
    return o;
  };
  let mediaSeq = 0;
  function uniqueMediaName(original) {
    const ext = ((original || '').split('.').pop() || 'png').toLowerCase().replace(/[^a-z0-9]/g, '') || 'png';
    let n;
    do { n = 'iroiro-image' + (++mediaSeq) + '.' + ext; } while (media.has(n));
    return n;
  }

  /** 문서 안의 그림 이름(media/…, 또는 주소 끝의 media/…) → blob 주소. 모르면 빈 글(편집기가 제 길로 받는다). */
  function imageUrlFor(nameOrUrl) {
    const s = String(nameOrUrl || '');
    if (/^(data|blob):/.test(s)) return '';
    const key = s.replace(/^.*\/media\//, '').replace(/^media\//, '');
    const m = media.get(key);
    return m ? m.url : '';
  }

  // 편집기(틀 안)가 부르는 갈고리 — CryptPad 빌드의 sdkjs 가 window.parent.APP 을 본다. getImageURL 은 덮개(api.js)의 connectMockServer 가
  // 제 것으로 갈아 끼우고 서버의 getImageURL(아래)을 부른다 — 둘 다 imageUrlFor 로 간다(서로를 부르면 맴돈다).
  const APP = window.APP = {
    urlArgs: '',
    getImageURL(nameOrUrl, cb) { cb(imageUrlFor(nameOrUrl)); },
    // 그림 넣기(파일에서) — 앱의 그림 고르개(EditorActivity.onShowFileChooser, 그림만).
    AddImage(success, error) {
      pickImage().then((file) => {
        if (!file) return void (error && error());
        return file.arrayBuffer().then((buf) => {
          const n = uniqueMediaName(file.name);
          const url = addMedia(n, new Uint8Array(buf));
          editorWindow() && editorWindow().AscCommon && editorWindow().AscCommon.g_oDocumentUrls.addImageUrl('media/' + n, url);
          success({ name: n, url });
        });
      }).catch((e) => { log('그림 넣기: ' + e); if (error) error(); });
    },
    // 끌어 놓기·붙이기의 그림 파일. 그림만 받는다(끌어 놓은 다른 파일을 그림 자리에 싣지 않는다).
    UploadImageFiles(files, _docId, _userId, _jwt, cb) {
      const all = Array.from(files || []);
      const images = all.filter((f) => /^image\//.test(f.type || '') || /\.(png|jpe?g|gif|bmp|webp|svg|tiff?|emf|wmf)$/i.test(f.name || ''));
      if (images.length < all.length) log('그림이 아닌 파일 ' + (all.length - images.length) + '개를 받지 않았다');
      if (!images.length) return void cb(new Error('그림이 아니다'), []);
      Promise.all(images.map((f) => f.arrayBuffer().then((buf) => {
        const n = uniqueMediaName(f.name);
        const url = addMedia(n, new Uint8Array(buf));
        const w = editorWindow();
        if (w && w.AscCommon) w.AscCommon.g_oDocumentUrls.addImageUrl('media/' + n, url);
        return url;
      }))).then((urls) => cb(null, urls)).catch((e) => { log('그림 올리기: ' + e); cb(e, []); });
    },
    remoteTheme() {},
    changeTheme() {},
    onFastChange() {},
    // 인쇄(6) — sdkjs 의 인쇄·'PDF 로 다운로드' 가 지은 인쇄 자료. cb() 를 부르면 편집기가 기다림을 끝낸다.
    printPdf(dataContainer, cb) {
      const exportName = pdfFor;
      pdfFor = null;
      buildPdf(dataContainer && dataContainer.data)
        .then((pdf) => transfer(pdf, exportName ? { purpose: 'export', name: exportName } : { purpose: 'print', format: 'pdf', name: baseName }))
        .catch((e) => {
          log('PDF 를 짓지 못했다: ' + (e && e.stack || e));
          if (!appKnows(e)) send({ op: 'transfer-error', purpose: exportName ? 'export' : 'print' });
        })
        .finally(() => {
          busy(null);
          try { cb(); } catch (_) { /* 편집기가 이미 닫혔다 */ }
        });
    },
  };

  function pickImage() {
    return new Promise((resolve) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = 'image/*';
      input.style.display = 'none';
      input.addEventListener('change', () => { resolve(input.files && input.files[0] || null); input.remove(); }, { once: true });
      input.addEventListener('cancel', () => { resolve(null); input.remove(); }, { once: true });
      document.body.appendChild(input);
      input.click();
    });
  }

  // ---- 3. 앱으로 보내기(rhwp 어댑터의 3 과 같은 약속) ----------------------------------------------------------------------------
  const CHUNK = 1024 * 1024;
  let saveId = 0;
  const waiters = new Map(); // "id:seq" → resolve(성공 여부)
  const wait = (key) => new Promise((resolve) => waiters.set(key, resolve));
  const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');

  /**
   * 앱이 받기를 거절했다(save-failed) — 앱은 거절하는 자리마다 제 까닭을 이미 띄웠다(저장 자리의 까닭·'바쁘다'·'세션 없음' 따위). 그것을 쪽이
   * save-error·transfer-error 로 되알리면 앱이 일반 실패 창을 그 위에 하나 더 띄워 까닭이 가려진다(2026-10-10 기기 — 세션을 지운 뒤의 저장).
   * rhwp 어댑터는 같은 것을 AbortError 로 가른다(saveFailure).
   */
  const refused = (why) => Object.assign(new Error(why), { refused: true });
  const appKnows = (e) => e === CANCELLED || (e && e.refused === true);

  let line = Promise.resolve();
  function transfer(bytes, head) {
    const run = line.then(() => transferNow(bytes, head));
    line = run.catch(() => {});
    return run;
  }

  async function transferNow(bytes, head) {
    if (!host) throw new Error('앱이 없다');
    const id = ++saveId;
    const sha = hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)));
    const begun = wait(id + ':-1');
    send(Object.assign({}, head, { op: 'save-begin', id, size: bytes.length, sha256: sha }));
    if (!(await begun)) throw refused('앱이 받지 않았다');
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
      if (!(await acked)) throw refused('앱이 덩이 ' + seq + ' 을 받지 못했다');
      seq++;
    }
    const ended = wait(id + ':end');
    send({ op: 'save-end', id });
    if (!(await ended)) throw refused('앱이 받기를 끝내지 못했다');
  }

  // ---- 4. 편집기 -----------------------------------------------------------------------------------------------------------
  let docEditor = null;
  let ready = false;
  const editorWindow = () => {
    const f = document.querySelector('#io-editor iframe');
    return f && f.contentWindow;
  };
  const editorApi = () => {
    const w = editorWindow();
    return w && (w.editor || w.editorCell);
  };

  // 고친 것이 있는가(4). saveChanges 가 오면 참, 파일 저장이 끝나고 그 사이 바뀐 것이 없으면 거짓.
  let dirty = false;
  let changeSeq = 0;
  const setDirty = (d) => {
    if (d === dirty) return;
    dirty = d;
    send({ op: 'state', dirty: d });
  };

  // 가짜 서버 — 혼자 쓰는 사람 하나.
  const USER_ID = 'iroiro';
  const USER_INDEX = 1;
  const UNIQUE = USER_ID + USER_INDEX;
  const now = () => Date.now();
  const toOO = (msg) => { if (docEditor) docEditor.sendMessageToOO(msg); };

  function fromOO(msg) {
    switch (msg && msg.type) {
      case 'auth':
        // 덮개(api.js)가 authChanges·auth·documentOpen 을 이미 보냈다(아래 sendMessageToOO 가로채기로 그림 주소를 더한다).
        break;
      case 'isSaveLock':
        toOO({ type: 'saveLock', saveLock: false });
        break;
      case 'getLock': {
        const locks = {};
        for (const b of (msg.block || [])) {
          const key = typeof b === 'string' ? b : 'iroiro-' + Math.random().toString(36).slice(2);
          locks[key] = { time: now(), user: UNIQUE, block: b };
        }
        toOO({ type: 'getLock', locks });
        break;
      }
      case 'saveChanges':
        if (ready) { changeSeq++; setDirty(true); }
        if (msg.endSaveChanges === false) toOO({ type: 'savePartChanges', changesIndex: -1, time: now() });
        else toOO({ type: 'unSaveLock', index: -1, time: now() });
        break;
      case 'unLockDocument':
        if (msg.releaseLocks) toOO({ type: 'releaseLock', locks: [] });
        if (msg.isSave) toOO({ type: 'unSaveLock', time: -1, index: -1 });
        break;
      case 'getMessages':
        toOO({ type: 'message' });
        break;
      case 'forceSaveStart': {
        const time = now();
        toOO({ type: 'forceSaveStart', messages: { code: 0, time } });
        save().then((ok) => toOO({ type: 'forceSave', messages: { type: 1, time, success: ok } }));
        break;
      }
      case 'openDocument': {
        // 슬라이드를 복제할 때 그림 주소를 다시 묻는다.
        const o = msg.message || {};
        if (o.c === 'imgurls') {
          const urls = [];
          for (const n of (Array.isArray(o.data) ? o.data : [])) {
            const key = String(n).replace(/^media\//, '');
            const m = media.get(key);
            if (m) urls.push({ path: n, url: m.url });
          }
          toOO({ type: 'documentOpen', data: { type: 'imgurls', status: 'ok', data: { urls, error: 0 } } });
        }
        break;
      }
      default:
        break;
    }
  }

  function participants() {
    return {
      index: USER_INDEX,
      list: [{ id: UNIQUE, idOriginal: USER_ID, username: '나', indexUser: USER_INDEX, connectionId: 'iroiro-connection',
        isCloseCoAuthoring: false, view: !editing }],
    };
  }

  function createEditor(binUrl) {
    const config = {
      document: {
        // 편집기에는 늘 바탕 OOXML 로 보인다 — 편집기가 보는 것은 내부 형식(bin)뿐이고, 'pdf' 를 주면 이 빌드에 없는 PDF 편집기를 찾는다.
        fileType: spec.base,
        key: 'iroiro-' + now(),
        title: name,
        url: binUrl,
        permissions: {
          // download — 파일 메뉴의 '다운로드'(다른 형식으로 내보내기, 6). 편집기는 문서 서버로 보내려 하지만 asc_DownloadAs 를 가로챈다.
          edit: editing, download: true, print: true, comment: editing, review: editing, fillForms: editing,
          modifyFilter: editing, modifyContentControl: editing, protect: false, chat: false,
        },
      },
      documentType: spec.doc,
      editorConfig: {
        mode: editing ? 'edit' : 'view',
        lang: 'ko',
        region: 'ko-KR',
        user: { id: USER_ID, name: '나' },
        coEditing: { mode: 'fast', change: false },
        customization: {
          // 폰의 좁은 화면 — 머리·도구 상자를 줄이고, 눈금자를 숨기고, 오른쪽 설정 판은 접어 둔다(누르면 열린다). 글은 폭에 맞춘다.
          compactHeader: true,
          compactToolbar: true,
          toolbarHideFileName: true,
          hideRightMenu: true,
          hideRulers: true,
          // 글은 폭에 맞춘다(-2), 발표는 슬라이드 하나가 다 보이게(-1), 표는 100%.
          zoom: spec.doc === W ? -2 : spec.doc === S ? -1 : 100,
          // 발표의 노트 칸은 처음에 숨긴다(폰의 높이 — 보기 탭에서 다시 연다). 슬라이드 목록은 문서가 열린 뒤 접는다(onDocumentReady).
          hideNotes: true,
          comments: editing,
          help: false,
          plugins: false,
          macros: false,
          macrosMode: 'disable',
          feedback: false,
          goback: false,
          forcesave: true,
          autosave: false,
          // 맞춤법 사전은 싣지 않는다(tools/build_onlyoffice.py 가 걷는다). 새 기능 안내도 끈다.
          features: { spellcheck: { mode: false, change: false }, featuresTips: false },
          layout: {
            header: { users: false },
            statusBar: { docLang: false, textLang: false },
          },
          // 기기의 밝기·어둡기를 따른다.
          uiTheme: 'theme-system',
          integrationMode: 'embed',
        },
      },
      events: {
        onAppReady: () => log('onAppReady'),
        onDocumentReady: () => {
          hookDownloads();
          // 발표 — 왼쪽 슬라이드 목록이 폰 폭의 40% 를 먹어 슬라이드가 18% 로 작아진다. 접어 두고(왼쪽 메뉴의 슬라이드 단추로 다시 연다) 다시 맞춘다.
          if (spec.doc === S) {
            const api = editorApi();
            try {
              if (api && typeof api.ShowThumbnails === 'function') api.ShowThumbnails(false);
              if (api && typeof api.zoomFitToPage === 'function') setTimeout(() => api.zoomFitToPage(), 0);
            } catch (e) {
              log('슬라이드 목록을 접지 못했다: ' + e);
            }
          }
          // 바꾼 결과가 비었는데 원본에 내용이 있으면 연 것으로 치지 않는다(5-2) — 그 빈 문서를 고쳐 저장하면 원본 자리에 빈 문서가 쓰인다.
          verifyNotEmpty().then((ok) => {
            if (failed) return;
            if (!ok) {
              fail('문서를 열지 못했습니다 — 깨졌거나 이 편집기가 읽지 못하는 문서입니다.', null, 'corrupt');
              return;
            }
            ready = true;
            busy(null);
            send({ op: 'ready' });
            log('문서를 열었다');
            showNotes();
            restorePosition();
          });
        },
        // 편집기가 '고쳐졌다' 고 할 때(4) — 바뀐 것(saveChanges)은 편집기의 모아 보내기 시간만큼 늦게 오고, 그 사이 뒤로 가면 앱이 고친 것이
        // 없는 줄 알고 묻지 않고 닫았다. '고침 없음'(false)은 가짜 서버가 받았다는 뜻일 뿐이라 쓰지 않는다.
        onDocumentStateChange: (e) => {
          if (e && e.data === true && ready && editing) {
            changeSeq++;
            setDirty(true);
          }
        },
        onError: (e) => {
          const d = e && e.data;
          log('편집기 오류: ' + JSON.stringify(d));
          if (!ready) fail('문서를 열지 못했습니다.', null, 'other');
        },
        onWarning: (e) => log('편집기 경고: ' + JSON.stringify(e && e.data)),
      },
    };
    docEditor = new window.DocsAPI.DocEditor('io-placeholder', config);
    // 덮개의 auth 처리가 보내는 documentOpen 에 그림 주소를 싣는다(편집기의 g_oDocumentUrls 가 받는다).
    const original = docEditor.sendMessageToOO.bind(docEditor);
    docEditor.sendMessageToOO = (msg) => {
      if (msg && msg.type === 'documentOpen' && msg.data && msg.data.type === 'open' && msg.data.data) {
        Object.assign(msg.data.data, mediaUrls());
      }
      original(msg);
    };
    docEditor.connectMockServer({
      onMessage: fromOO,
      getParticipants: participants,
      getImageURL: (n) => Promise.resolve(imageUrlFor(n)),
    });
  }

  // ---- 5. 열기 -------------------------------------------------------------------------------------------------------------
  /** 넣은 암호(8) — 이 쪽이 사는 동안만. 저장할 때 같은 암호를 건다. */
  let password = null;

  async function open() {
    if (!spec) return fail('이 형식은 이 편집기가 열지 않습니다.', null, 'other');
    if (!/^[0-9a-f]{16,64}$/.test(token)) return fail('열 문서가 없습니다.', null, 'other');
    busy('문서를 여는 중…');
    let bytes;
    try {
      const r = await fetch('/__io/in/' + token);
      if (!r.ok) throw new Error('HTTP ' + r.status);
      bytes = new Uint8Array(await r.arrayBuffer());
    } catch (e) {
      return fail('문서를 읽지 못했습니다.', e, 'other');
    }
    let input = bytes;
    let extra = '';
    if (spec.text) {
      // 9 — 인코딩을 우리가 가려 UTF-8(BOM 없이)로 넘긴다.
      const read = readText(bytes, spec.text);
      if (!read) return fail('글자 인코딩을 알아보지 못해 열지 않았습니다 — UTF-8·UTF-16·CP949(EUC-KR) 글만 엽니다.', null, 'corrupt');
      textInfo = read.info;
      input = new TextEncoder().encode(read.text);
      extra = spec.text === 'txt'
        ? '<m_nFormatFrom>69</m_nFormatFrom><m_nFormatTo>8193</m_nFormatTo><m_nCsvTxtEncoding>46</m_nCsvTxtEncoding>'
        : '<m_nFormatFrom>260</m_nFormatFrom><m_nFormatTo>8194</m_nFormatTo><m_nCsvTxtEncoding>46</m_nCsvTxtEncoding>' +
          '<m_nCsvDelimiter>' + DELIMITERS[textInfo.delimiter].code + '</m_nCsvDelimiter>';
    }
    let m;
    try {
      m = await x2t();
    } catch (e) {
      return fail('변환기를 준비하지 못했습니다.', e, 'other');
    }
    const encrypted = kind === 'pdf' && hasPdfEncrypt(bytes);
    let bin = null;
    let wrong = false;
    const t0 = performance.now();
    for (;;) {
      let r;
      try {
        if (kind === 'pdf') busy('PDF를 고칠 수 있는 문서로 바꾸는 중…');
        r = await toBin(m, input, extra);
      } catch (e) {
        return fail('문서를 열지 못했습니다 — 깨졌거나 이 편집기가 읽지 못하는 문서입니다.', e, 'corrupt');
      }
      // PDF — x2t 는 암호를 몰라도 빈 문서를 내놓는다. /Encrypt 가 있는데 비었으면 암호를 묻는다.
      const locked = r.rc === 90 || r.rc === 91 || (encrypted && r.empty);
      if (!locked) {
        bin = r.bin;
        if (!bin) return fail('문서를 열지 못했습니다 — 깨졌거나 이 편집기가 읽지 못하는 문서입니다.', new Error('x2t 코드 ' + r.rc), 'corrupt');
        break;
      }
      // 암호를 넣었는데 다시 잠겼으면 틀린 것이다(91 은 x2t 가 그렇다고 말한 것).
      wrong = password != null;
      const p = await askPassword(wrong);
      // 그만뒀다 — 앱이 '다시 열기' 를 단다(다시 열면 다시 묻는다).
      if (p == null) return fail('암호를 넣지 않아 문서를 열지 않았습니다.', null, 'cancelled');
      password = p;
      busy('문서를 여는 중…');
    }
    for (const n of m.FS.readdir('/working/media')) {
      if (n === '.' || n === '..') continue;
      addMedia(n, m.FS.readFile('/working/media/' + n));
    }
    log('x2t: ' + bytes.length + ' → ' + bin.length + ' 바이트, 그림 ' + media.size + '개, ' + Math.round(performance.now() - t0) + 'ms');
    const binUrl = URL.createObjectURL(new Blob([bin]));
    try {
      createEditor(binUrl);
    } catch (e) {
      return fail('편집기를 띄우지 못했습니다.', e, 'other');
    }
  }

  /**
   * 받은 바이트 → 편집기의 내부 형식. 곧바로 안 되는 형식(via)과, 곧바로 실패한 형식은 바탕 OOXML 을 한 번 거친다. 암호가 필요하거나
   * 틀렸으면(90·91) 그 값을 돌려준다. PDF 는 거친 결과가 비었는지(empty)도 본다(8).
   */
  async function toBin(m, bytes, extra) {
    freshWorking(m);
    const input = '/working/input.' + kind;
    m.FS.writeFile(input, bytes);
    const pw = password != null ? '<m_sPassword>' + xmlText(password) + '</m_sPassword>' : '';
    if (!spec.via) {
      const r = run(m, input, '/working/document.bin', extra + pw);
      if (r.out || r.rc === 90 || r.rc === 91 || spec.text) return { rc: r.rc, bin: r.out };
      log('x2t 가 곧바로 열지 못했다(코드 ' + r.rc + ') — ' + spec.base + ' 를 거친다');
      freshMedia(m);
    }
    const step = '/working/step.' + spec.base;
    const r1 = run(m, input, step, pw);
    if (r1.rc === 90 || r1.rc === 91) return { rc: r1.rc, bin: null };
    if (!r1.out) return { rc: r1.rc, bin: null };
    const empty = kind === 'pdf' ? await docxIsEmpty(r1.out) : false;
    freshMedia(m);
    const r2 = run(m, step, '/working/document.bin', '');
    return { rc: r2.rc, bin: r2.out, empty };
  }

  /** PDF 에 /Encrypt 가 있는가 — 암호를 물을지 정하는 데만 쓴다(있어도 소유자 암호뿐이면 x2t 가 그냥 연다). */
  function hasPdfEncrypt(b) {
    const t = [0x2F, 0x45, 0x6E, 0x63, 0x72, 0x79, 0x70, 0x74]; // "/Encrypt"
    const n = b.length - t.length;
    outer: for (let i = 0; i <= n; i++) {
      if (b[i] !== 0x2F) continue;
      for (let j = 1; j < t.length; j++) if (b[i + j] !== t[j]) continue outer;
      return true;
    }
    return false;
  }

  /** x2t 가 PDF 에서 지은 docx 의 본문에 글·그림이 하나도 없는가. 읽지 못하면 비지 않은 것으로 친다(괜히 암호를 묻지 않게). */
  async function docxIsEmpty(docx) {
    try {
      const xml = await zipText(docx, 'word/document.xml');
      if (xml == null) return false;
      return !/<w:t[\s>]|<w:drawing|<w:pict|<w:object|<m:oMath/.test(xml);
    } catch (e) {
      log('PDF 결과를 보지 못했다: ' + e);
      return false;
    }
  }

  /** ZIP 안의 항목 하나를 글로(저장 또는 deflate 만 — x2t 가 지은 것을 읽는 데만 쓴다). 없거나 못 읽으면 null. */
  async function zipText(bytes, entry) {
    const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let eocd = -1;
    for (let i = bytes.length - 22; i >= 0 && i >= bytes.length - 22 - 0xFFFF; i--) {
      if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) return null;
    const count = dv.getUint16(eocd + 10, true);
    let at = dv.getUint32(eocd + 16, true);
    const want = new TextEncoder().encode(entry);
    for (let k = 0; k < count && at + 46 <= bytes.length; k++) {
      if (dv.getUint32(at, true) !== 0x02014b50) return null;
      const method = dv.getUint16(at + 10, true);
      const size = dv.getUint32(at + 20, true);
      const nlen = dv.getUint16(at + 28, true);
      const xlen = dv.getUint16(at + 30, true);
      const clen = dv.getUint16(at + 32, true);
      const local = dv.getUint32(at + 42, true);
      const n = bytes.subarray(at + 46, at + 46 + nlen);
      if (n.length === want.length && n.every((v, i) => v === want[i])) {
        if (local + 30 > bytes.length || dv.getUint32(local, true) !== 0x04034b50) return null;
        const start = local + 30 + dv.getUint16(local + 26, true) + dv.getUint16(local + 28, true);
        const data = bytes.subarray(start, start + size);
        if (method === 0) return new TextDecoder().decode(data);
        if (method !== 8) return null;
        const out = await new Response(new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).arrayBuffer();
        return new TextDecoder().decode(out);
      }
      at += 46 + nlen + xlen + clen;
    }
    return null;
  }

  /** ZIP 의 항목 이름들(가운데 목록). ZIP 이 아니면 null. */
  function zipNames(bytes) {
    const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let eocd = -1;
    for (let i = bytes.length - 22; i >= 0 && i >= bytes.length - 22 - 0xFFFF; i--) {
      if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) return null;
    const count = dv.getUint16(eocd + 10, true);
    let at = dv.getUint32(eocd + 16, true);
    const dec = new TextDecoder();
    const names = [];
    for (let k = 0; k < count && at + 46 <= bytes.length; k++) {
      if (dv.getUint32(at, true) !== 0x02014b50) return null;
      const nlen = dv.getUint16(at + 28, true);
      const xlen = dv.getUint16(at + 30, true);
      const clen = dv.getUint16(at + 32, true);
      names.push(dec.decode(bytes.subarray(at + 46, at + 46 + nlen)));
      at += 46 + nlen + xlen + clen;
    }
    return names;
  }

  // ---- 5-2. 빈 결과 --------------------------------------------------------------------------------------------------------------
  // x2t 가 읽지 못한 문서를 오류 없이 빈 문서로 바꿔 내놓는 일이 있다. 그대로 인사하면 앱이 '편집' 단추를 띄우고, 고쳐
  // 저장하면 빈 문서가 원본 자리에 쓰인다. 편집기의 문서가 비었을 때만 원본을 다시 받아(/__io/in/<표>) 내용이 있었는지 본다 — 있었으면 열지 못한
  // 것으로 친다. 원본을 들여다볼 수 있는 형식(OOXML·ODF — ZIP)만 본다. 이전 형식(doc·xls·ppt)·글·PDF 는 보지 않는다(비었다고 가를 길이 없다).
  // 모르면(편집기 API 가 없다·읽지 못했다) 비지 않은 것으로 친다 — 괜히 막지 않게. 서식 파일(dotx·xltx·potx)은 본디 빌 수 있어 원본에 내용이
  // 있을 때만 걸린다.
  async function verifyNotEmpty() {
    try {
      if (!editorLooksEmpty()) return true;
      const r = await fetch('/__io/in/' + token);
      if (!r.ok) return true;
      const has = await inputHasContent(new Uint8Array(await r.arrayBuffer()));
      if (has) log('편집기의 문서는 비었는데 원본에는 내용이 있다 — 열지 못한 것으로 친다');
      return !has;
    } catch (e) {
      log('빈 결과를 보지 못했다: ' + e);
      return true;
    }
  }

  /** 편집기의 문서가 비었는가 — 문서: 빈 문단 하나뿐(그림 없이), 발표: 슬라이드 0장, 표: 모든 시트가 A1 하나(빈 칸)이고 그림이 없다. */
  function editorLooksEmpty() {
    const w = editorWindow();
    const api = editorApi();
    if (!w || !api) return false;
    if (spec.doc === S) return typeof api.getCountPages === 'function' && api.getCountPages() === 0;
    const B = w.AscBuilder;
    if (!B) return false;
    if (spec.doc === W) {
      const A = (B.Word && B.Word.Api) || B.Api;
      if (!A || typeof A.GetDocument !== 'function') return false;
      const doc = A.GetDocument();
      const count = doc.GetElementsCount();
      if (count > 1) return false;
      if (typeof doc.GetAllDrawingObjects === 'function' && doc.GetAllDrawingObjects().length) return false;
      const el = count === 1 ? doc.GetElement(0) : null;
      if (el && typeof el.GetClassType === 'function' && el.GetClassType() !== 'paragraph') return false;
      return !el || String(typeof el.GetText === 'function' ? el.GetText() : '').trim() === '';
    }
    const A = B.Cell && B.Cell.Api;
    if (!A || typeof A.GetSheets !== 'function') return false;
    for (const ws of A.GetSheets()) {
      if (String(ws.GetUsedRange().GetAddress(false, false, 'xlA1', false) || 'A1') !== 'A1') return false;
      const v = ws.GetRange('A1').GetValue();
      if (v != null && String(v).trim() !== '') return false;
      if (typeof ws.GetAllDrawings === 'function' && ws.GetAllDrawings().length) return false;
    }
    return true;
  }

  /** 원본에 내용이 있었는가 — ZIP 의 목록과 본문만 본다(OOXML·ODF). 들여다볼 수 없는 형식은 거짓(모른다). */
  async function inputHasContent(b) {
    const names = zipNames(b);
    if (!names) return false;
    if (/^(docx|docm|dotx|dotm)$/.test(kind)) {
      const xml = await zipText(b, 'word/document.xml');
      return xml != null && /<w:t(?:\s[^>]*)?>[^<]*[^<\s]|<w:drawing[\s>]|<w:pict[\s>]|<w:object[\s>]|<m:oMath[\s>]|<w:tbl[\s>]/.test(xml);
    }
    if (/^(xlsx|xlsm|xltx|xltm)$/.test(kind)) {
      if (names.some((n) => /^xl\/drawings\/drawing\d+\.xml$/.test(n))) return true;
      for (const n of names.filter((x) => /^xl\/worksheets\/sheet\d+\.xml$/.test(x))) {
        const xml = await zipText(b, n);
        if (xml != null && /<v>|<is>|<f[\s>/]/.test(xml)) return true;
      }
      return false;
    }
    if (/^(pptx|pptm|ppsx|ppsm|potx|potm)$/.test(kind)) return names.some((n) => /^ppt\/slides\/slide\d+\.xml$/.test(n));
    if (kind === 'odp' || kind === 'odt' || kind === 'ods') {
      const xml = await zipText(b, 'content.xml');
      const body = xml != null ? /<office:body[\s>][\s\S]*<\/office:body>/.exec(xml) : null;
      if (!body) return false;
      if (kind === 'odp') return /<draw:page[\s>]/.test(body[0]);
      return /<draw:(frame|g|custom-shape)[\s>]|office:value=/.test(body[0]) || body[0].replace(/<[^>]*>/g, '').trim() !== '';
    }
    return false;
  }

  // ---- 6. 저장 -------------------------------------------------------------------------------------------------------------
  let saving = null;
  function save() {
    if (saving) return saving;
    saving = saveNow().finally(() => { saving = null; });
    return saving;
  }

  /** 사용자가 저장을 그만두었다(글의 인코딩 물음에서 '취소') — 실패가 아니다. */
  const CANCELLED = new Error('그만두었다');

  async function saveNow() {
    if (!editing) return false;
    const api = editorApi();
    if (!api) return false;
    const seq = changeSeq;
    busy('저장하는 중…');
    try {
      let out;
      const t0 = performance.now();
      if (spec.text) {
        out = await textBytes(spec.text, textInfo, true);
      } else {
        const m = await x2t();
        stage(m, api);
        out = writeAs(m, saveKind);
      }
      log('저장 ' + saveKind + ': ' + Math.round(performance.now() - t0) + 'ms, ' + out.length + ' 바이트');
      await transfer(out, { purpose: 'save', kind: saveKind, name: saveName });
      if (changeSeq === seq) setDirty(false);
      return true;
    } catch (e) {
      // 그만둔 것은 알리지 않는다 — 앱이 청한 저장이면 아래 'save' 의 처리가 save-cancelled 를 보낸다(닫지 않는다). 앱이 거절한 것도 알리지
      // 않는다 — 앱이 까닭을 이미 띄웠다(refused).
      log('저장하지 못했다: ' + (e && e.stack || e));
      if (!appKnows(e)) send({ op: 'save-error' });
      return false;
    } finally {
      busy(null);
    }
  }

  // ---- 6-2. 인쇄·내보내기 -----------------------------------------------------------------------------------------------------
  // 인쇄: sdkjs 가 쪽을 그린 인쇄 자료(pdf.bin)와 지금 문서(document.bin)로 x2t 가 PDF 를 짓는다(CryptPad 와 같은 길). 글꼴은 문서가 쓰는 것만
  // 작업 폴더의 fonts 에 둔다 — PDF 에 심는다. '다운로드 → PDF' 도 이 길로 지어(pdfFor 에 이름을 두고) 내보내기로 보낸다.
  let pdfFor = null;

  async function buildPdf(printData) {
    if (printData == null) throw new Error('인쇄 자료가 없다');
    busy('PDF를 짓는 중…');
    const api = editorApi();
    if (!api) throw new Error('편집기가 없다');
    const m = await x2t();
    stage(m, api);
    m.FS.writeFile('/working/pdf.bin', typeof printData === 'string' ? printData : new Uint8Array(printData));
    await stageFonts(m);
    const t0 = performance.now();
    const pdf = convert(m, '/working/document.bin', '/working/document.pdf', '<m_sFontDir>/working/fonts/</m_sFontDir>');
    log('PDF: ' + pdf.length + ' 바이트, ' + Math.round(performance.now() - t0) + 'ms');
    return pdf;
  }

  /**
   * 문서가 쓰는 글꼴(편집기의 글꼴 적재기가 '필요함' 으로 표시한 것)을 작업 폴더에 — 이름은 CryptPad 처럼 '가족 이름_Bold.ttf'. 바이트는
   * 편집기의 FreeType 이 되살린 sfnt(editorSfnt — WOFF2 도 TTF 가 된다)이고, 그것을 못 얻으면 원래 파일을 받아 둔다.
   */
  async function stageFonts(m) {
    const w = editorWindow();
    const loader = w && w.AscCommon && w.AscCommon.g_font_loader;
    if (!loader || !Array.isArray(loader.fontInfos)) return;
    const SUFFIX = { indexR: '', indexB: '_Bold', indexI: '_Italic', indexBI: '_Bold_Italic' };
    const jobs = [];
    const seen = new Set();
    let fromEditor = 0;
    for (const f of loader.fontInfos) {
      if (!f || !f.NeedStyles) continue;
      for (const k of Object.keys(SUFFIX)) {
        const i = f[k];
        if (typeof i !== 'number' || i < 0) continue;
        const file = loader.fontFiles && loader.fontFiles[i];
        if (!file || typeof file.Id !== 'string' || !/^[A-Za-z0-9_\-/.]+$/.test(file.Id) || file.Id.includes('..')) continue;
        const target = '/working/fonts/' + String(f.Name).replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_') + SUFFIX[k] + '.ttf';
        if (seen.has(target)) continue;
        seen.add(target);
        const sfnt = editorSfnt(w, file);
        if (sfnt) {
          m.FS.writeFile(target, sfnt);
          fromEditor++;
          continue;
        }
        jobs.push(fetch('/fonts/' + file.Id)
          .then((r) => (r.ok ? r.arrayBuffer() : null))
          .then((b) => { if (b) m.FS.writeFile(target, new Uint8Array(b)); })
          .catch(() => {}));
      }
    }
    await Promise.all(jobs);
    log('PDF 글꼴 ' + seen.size + '개(편집기에서 ' + fromEditor + ')');
  }

  /**
   * 편집기(sdkjs)가 연 글꼴의 sfnt(TrueType) 바이트. x2t 는 WOFF2 를 PDF 에 심지 못해 글자를 윤곽선으로 그리고 몇 글자(술·특·필·록 …)를 틀리게
   * 그렸다(2026-10-09 기기). FreeType 은 WOFF2 를 열 때 표를 되살린 온전한 sfnt 를 face 의 stream 에 둔다 — 편집기의 글꼴 메모리로 face 를
   * 하나 더 열어 그 바이트를 떠 온다(새 의존·APK 증가 없음). FT_FaceRec 의 stream(wasm32 에서 104)·num_glyphs(16)·units_per_EM(68)과
   * FT_StreamRec 의 base·size(0·4)는 FreeType 의 공개 구조다 — 떠 온 sfnt 의 maxp·head 와 견줘 자리가 맞을 때만 쓴다. 못 하면 null.
   */
  function editorSfnt(w, file) {
    const F = w && w.AscFonts;
    if (!F || typeof F.FT_Open_Face2 !== 'function' || typeof F.CreateNativeStreamByIndex !== 'function') return null;
    if (!(typeof file.stream_index === 'number' && file.stream_index >= 0)) return null; // 편집기가 아직 읽지 않았다
    let lib = 0;
    let face = 0;
    try {
      F.CreateNativeStreamByIndex(file.stream_index);
      const st = F.g_fonts_streams[file.stream_index];
      if (!st || st.asc_marker !== true || !st.data || !st.len) return null;
      lib = F.FT_CreateLibrary();
      if (!lib) return null;
      face = F.FT_Open_Face2(lib, st.data, st.len, 0);
      if (!face) return null;
      const heap = F.GetUint8ArrayFromPointer(0, 1).buffer; // 웹어셈블리 메모리 전체
      const dv = new DataView(heap);
      const numGlyphs = dv.getUint32(face + 16, true);
      const upem = dv.getUint16(face + 68, true);
      const stream = dv.getUint32(face + 104, true);
      if (!stream || stream + 8 > heap.byteLength) return null;
      const base = dv.getUint32(stream, true);
      const size = dv.getUint32(stream + 4, true);
      if (!base || size < 12 || base + size > heap.byteLength) return null;
      const bytes = new Uint8Array(heap, base, size).slice();
      return sfntMatches(bytes, numGlyphs, upem) ? bytes : null;
    } catch (e) {
      log('글꼴을 떠 오지 못했다(' + file.Id + '): ' + e);
      return null;
    } finally {
      if (face) F.FT_Done_Face(face);
      if (lib) F.FT_Done_Library(lib);
    }
  }

  /** sfnt 인가 — TrueType/OpenType 머리, 표 디렉터리가 안에 있고, maxp 의 글리프 수·head 의 em 단위가 face 와 같다. */
  function sfntMatches(b, numGlyphs, upem) {
    const dv = new DataView(b.buffer, b.byteOffset, b.byteLength);
    const version = dv.getUint32(0);
    if (version !== 0x00010000 && version !== 0x4F54544F && version !== 0x74727565) return false;
    const n = dv.getUint16(4);
    if (n < 1 || n > 128 || 12 + 16 * n > b.length) return false;
    let glyphs = -1;
    let em = -1;
    for (let i = 0; i < n; i++) {
      const at = 12 + 16 * i;
      const tag = dv.getUint32(at);
      const off = dv.getUint32(at + 8);
      const len = dv.getUint32(at + 12);
      if (off + len > b.length) return false;
      if (tag === 0x6D617870 && len >= 6) glyphs = dv.getUint16(off + 4); // maxp
      if (tag === 0x68656164 && len >= 20) { // head — 매직 0x5F0F3CF5
        if (dv.getUint32(off + 12) !== 0x5F0F3CF5) return false;
        em = dv.getUint16(off + 18);
      }
    }
    return glyphs === numGlyphs && em === upem;
  }

  // 내보내기: 편집기의 asc_FileType → 확장자. x2t 가 쓰는 것(과 우리가 쓰는 글 둘)만 — 그림(PNG·JPG)·PDF/A·MD·XLSB 는 받지 않는다(그 단추는
  // iroiro-oo-inner.css 가 숨긴다).
  const EXPORTS = {
    65: 'docx', 67: 'odt', 68: 'rtf', 69: 'txt', 70: 'html', 72: 'epub', 73: 'fb2', 76: 'dotx', 79: 'ott',
    257: 'xlsx', 259: 'ods', 260: 'csv', 262: 'xltx', 266: 'ots',
    129: 'pptx', 131: 'odp', 132: 'ppsx', 135: 'potx', 138: 'otp',
    513: 'pdf',
  };

  /** 편집기의 '다운로드' 를 가로챈다(문서가 열린 뒤 한 번). PDF 는 인쇄 자료가 필요해 원래 길로 보내고 printPdf 에서 받는다. */
  function hookDownloads() {
    const api = editorApi();
    if (!api || api.iroiroDownloads || typeof api.asc_DownloadAs !== 'function') return;
    api.iroiroDownloads = true;
    const original = api.asc_DownloadAs.bind(api);
    api.asc_DownloadAs = (options) => {
      const fileType = options ? (typeof options.asc_getFileType === 'function' ? options.asc_getFileType() : options.fileType) : undefined;
      const ext = EXPORTS[fileType];
      if (!ext) {
        log('내보내지 않는 형식: ' + fileType);
        send({ op: 'transfer-error', purpose: 'export' });
        return;
      }
      const outName = baseName + '.' + ext;
      if (ext === 'pdf') {
        pdfFor = outName;
        original(options);
        return;
      }
      exportAs(ext, outName, options);
    };
  }

  async function exportAs(ext, outName, options) {
    const api = editorApi();
    if (!api) return;
    busy('내보내는 중…');
    try {
      const t0 = performance.now();
      let out;
      if (ext === 'txt' || ext === 'csv') {
        out = await textBytes(ext, exportTextInfo(ext, options), false);
      } else {
        const m = await x2t();
        stage(m, api);
        out = writeAs(m, ext);
      }
      log('내보내기 ' + ext + ': ' + out.length + ' 바이트, ' + Math.round(performance.now() - t0) + 'ms');
      await transfer(out, { purpose: 'export', name: outName });
    } catch (e) {
      log('내보내지 못했다: ' + (e && e.stack || e));
      if (!appKnows(e)) send({ op: 'transfer-error', purpose: 'export' });
    } finally {
      busy(null);
    }
  }

  // ---- 6-3. 틀 안의 우리 손질과 잇기 -------------------------------------------------------------------------------------------
  window.iroiroOuter = { send, log, editing, openLink };
  const inner = () => {
    const w = editorWindow();
    return w && w.iroiroInner;
  };

  // ---- 8. 암호를 묻는 칸 ------------------------------------------------------------------------------------------------------
  /** 암호를 묻는다(앞서 넣은 것이 틀렸으면 [wrong]). 넣으면 그 글, 그만두면 null. 자판은 칸이 초점을 받아 저절로 올라온다. */
  function askPassword(wrong) {
    return new Promise((resolve) => {
      const box = document.getElementById('io-pass');
      const form = document.getElementById('io-pass-form');
      const input = document.getElementById('io-pass-input');
      const error = document.getElementById('io-pass-error');
      const cancel = document.getElementById('io-pass-cancel');
      busy(null);
      error.hidden = !wrong;
      input.value = '';
      box.hidden = false;
      // 문서를 열 때 묻는 칸이라 사용자의 손이 없었다 — 웹 쪽은 그때 자판을 올리지 못하므로 초점을 준 뒤 앱에 청한다.
      setTimeout(() => {
        input.focus();
        send({ op: 'keyboard' });
      }, 0);
      const done = (v) => {
        box.hidden = true;
        form.onsubmit = null;
        cancel.onclick = null;
        input.value = '';
        input.blur();
        resolve(v);
      };
      form.onsubmit = (e) => {
        e.preventDefault();
        const v = input.value;
        if (!v) { input.focus(); return; }
        done(v.slice(0, 255));
      };
      cancel.onclick = () => done(null);
    });
  }

  /** 예·아니오를 묻는 칸 — [yes] 를 누르면 참. */
  function ask(text, yes) {
    return new Promise((resolve) => {
      const box = document.getElementById('io-ask');
      const body = document.getElementById('io-ask-text');
      const ok = document.getElementById('io-ask-ok');
      const no = document.getElementById('io-ask-cancel');
      const was = busyEl.hidden;
      busy(null);
      body.textContent = text;
      ok.textContent = yes;
      box.hidden = false;
      const done = (v) => {
        box.hidden = true;
        ok.onclick = null;
        no.onclick = null;
        if (!was) busyEl.hidden = false;
        resolve(v);
      };
      ok.onclick = () => done(true);
      no.onclick = () => done(false);
    });
  }

  // ---- 9. 글(TXT·CSV) ---------------------------------------------------------------------------------------------------------
  /** 연 글의 모양 — { encoding(TextDecoder 의 이름), bom, eol, finalEol, delimiter(csv) }. 저장할 때 그대로 되살린다. */
  let textInfo = null;

  // CSV 의 구분자 — x2t 의 c_oAscCsvDelimiter 번호와 화면에 보일 이름.
  const DELIMITERS = {
    ',': { code: 4, label: '쉼표' },
    ';': { code: 2, label: '세미콜론' },
    '\t': { code: 1, label: '탭' },
  };

  /**
   * 받은 글의 인코딩을 가린다 — BOM, BOM 없는 UTF-16(널 바이트의 자리), 엄격한 UTF-8, 그 밖은 CP949(WHATWG 의 euc-kr — CP949 를 다 안다).
   * CP949 로도 읽히지 않는 바이트가 있으면 null — 저장하면 그 자리가 바뀌므로 열지 않는다.
   */
  function readText(b, which) {
    let encoding = null;
    let bom = false;
    let start = 0;
    if (b[0] === 0xEF && b[1] === 0xBB && b[2] === 0xBF) { encoding = 'utf-8'; bom = true; start = 3; }
    else if (b[0] === 0xFF && b[1] === 0xFE && b[2] === 0 && b[3] === 0 && b.length % 4 === 0) { encoding = 'utf-32le'; bom = true; start = 4; }
    else if (b[0] === 0 && b[1] === 0 && b[2] === 0xFE && b[3] === 0xFF) { encoding = 'utf-32be'; bom = true; start = 4; }
    else if (b[0] === 0xFF && b[1] === 0xFE) { encoding = 'utf-16le'; bom = true; start = 2; }
    else if (b[0] === 0xFE && b[1] === 0xFF) { encoding = 'utf-16be'; bom = true; start = 2; }
    const body = b.subarray(start);
    if (!encoding) {
      // 널 바이트가 많으면 BOM 없는 UTF-16 — 짝수 자리(빅 엔디언)와 홀수 자리(리틀 엔디언) 가운데 많은 쪽.
      let even = 0;
      let odd = 0;
      const n = Math.min(body.length, 4096);
      for (let i = 0; i < n; i++) if (body[i] === 0) { if (i % 2) odd++; else even++; }
      if (n >= 2 && (even + odd) * 5 > n) encoding = odd >= even ? 'utf-16le' : 'utf-16be';
    }
    let text;
    try {
      if (encoding === 'utf-32le' || encoding === 'utf-32be') {
        text = decodeUtf32(body, encoding === 'utf-32le');
      } else if (encoding) {
        text = new TextDecoder(encoding, { fatal: true }).decode(body);
      } else {
        try {
          text = new TextDecoder('utf-8', { fatal: true }).decode(body);
          encoding = 'utf-8';
        } catch (_) {
          text = new TextDecoder('euc-kr', { fatal: true }).decode(body);
          encoding = 'euc-kr';
        }
      }
    } catch (e) {
      log('글의 인코딩을 알아보지 못했다: ' + e);
      return null;
    }
    let crlf = 0;
    let lf = 0;
    let cr = 0;
    for (let i = 0; i < text.length; i++) {
      const c = text.charCodeAt(i);
      if (c === 13) { if (text.charCodeAt(i + 1) === 10) { crlf++; i++; } else cr++; } else if (c === 10) lf++;
    }
    // 줄바꿈이 없으면 — CSV 는 RFC 4180 의 CRLF, 글은 LF.
    const eol = crlf + lf + cr === 0 ? (which === 'csv' ? '\r\n' : '\n') : crlf >= lf && crlf >= cr ? '\r\n' : lf >= cr ? '\n' : '\r';
    const finalEol = /[\r\n]$/.test(text);
    const info = { encoding, bom, eol, finalEol };
    if (which === 'csv') info.delimiter = guessDelimiter(text);
    return { text, info };
  }

  function decodeUtf32(b, little) {
    if (b.length % 4) throw new Error('UTF-32 의 길이가 4 의 배수가 아니다');
    const dv = new DataView(b.buffer, b.byteOffset, b.byteLength);
    let s = '';
    for (let i = 0; i < b.length; i += 4) {
      const cp = dv.getUint32(i, little);
      if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) throw new Error('UTF-32 가 아니다');
      s += String.fromCodePoint(cp);
    }
    return s;
  }

  /** CSV 의 구분자 — 따옴표 밖에서 줄마다 센 수가 가장 고르고 1 개 넘게 나뉘는 것(쉼표·탭·세미콜론). 모르면 쉼표. */
  function guessDelimiter(text) {
    const rows = [];
    let row = { ',': 0, ';': 0, '\t': 0 };
    let quoted = false;
    for (let i = 0; i < text.length && rows.length < 50; i++) {
      const c = text[i];
      if (c === '"') { quoted = !quoted; continue; }
      if (quoted) continue;
      if (c === '\n' || c === '\r') {
        if (c === '\r' && text[i + 1] === '\n') i++;
        rows.push(row);
        row = { ',': 0, ';': 0, '\t': 0 };
        continue;
      }
      if (c in row) row[c]++;
    }
    if (rows.length < 50 && (row[','] || row[';'] || row['\t'])) rows.push(row);
    let best = ',';
    let bestScore = -1;
    for (const d of [',', '\t', ';']) {
      const counts = rows.map((r) => r[d]).filter((n) => n > 0);
      if (!counts.length) continue;
      const tally = new Map();
      for (const n of counts) tally.set(n, (tally.get(n) || 0) + 1);
      const mode = Math.max(...tally.values());
      const score = mode * 1000 + counts.length;
      if (score > bestScore) { best = d; bestScore = score; }
    }
    return best;
  }

  /** 내보내기(다운로드 → TXT·CSV)의 모양 — 편집기의 물음에서 고른 인코딩·구분자. 줄 끝은 CRLF, UTF 는 BOM 을 붙인다(x2t 가 하던 대로). */
  function exportTextInfo(ext, options) {
    const adv = options && (typeof options.asc_getAdvancedOptions === 'function' ? options.asc_getAdvancedOptions() : options.advancedOptions);
    const get = (o, getter, prop) => (o ? (typeof o[getter] === 'function' ? o[getter]() : o[prop]) : undefined);
    const index = get(adv, 'asc_getCodePage', 'codePage');
    const encoding = encodingOfIndex(index) || 'utf-8';
    let delimiter = ',';
    if (ext === 'csv') {
      const d = get(adv, 'asc_getDelimiter', 'delimiter');
      const ch = get(adv, 'asc_getDelimiterChar', 'delimiterChar');
      delimiter = ({ 1: '\t', 2: ';', 3: ':', 4: ',', 5: ' ' })[Array.isArray(d) ? d[0] : d] || (typeof ch === 'string' && ch.length === 1 ? ch : ',');
    }
    return { encoding, bom: encoding.startsWith('utf-'), eol: '\r\n', finalEol: true, delimiter };
  }

  // 편집기의 인코딩 번호(c_oAscEncodings 의 첫 칸) → 코드 페이지 → TextDecoder 의 이름. 쓰기는 encodeText 가 한다.
  const CODEPAGES = {
    65001: 'utf-8', 1200: 'utf-16le', 1201: 'utf-16be', 12000: 'utf-32le', 12001: 'utf-32be',
    949: 'euc-kr', 51949: 'euc-kr', 932: 'shift_jis', 936: 'gbk', 54936: 'gb18030', 950: 'big5', 20932: 'euc-jp',
    866: 'ibm866', 874: 'windows-874', 20866: 'koi8-r', 21866: 'koi8-u',
    1250: 'windows-1250', 1251: 'windows-1251', 1252: 'windows-1252', 1253: 'windows-1253', 1254: 'windows-1254',
    1255: 'windows-1255', 1256: 'windows-1256', 1257: 'windows-1257', 1258: 'windows-1258',
    28591: 'iso-8859-1', 28592: 'iso-8859-2', 28593: 'iso-8859-3', 28594: 'iso-8859-4', 28595: 'iso-8859-5',
    28596: 'iso-8859-6', 28597: 'iso-8859-7', 28598: 'iso-8859-8', 28603: 'iso-8859-13', 28605: 'iso-8859-15',
  };
  function encodingOfIndex(index) {
    if (!Number.isInteger(index)) return null;
    const w = editorWindow();
    const table = w && w.AscCommon && w.AscCommon.c_oAscEncodings;
    const row = Array.isArray(table) ? table.find((e) => Array.isArray(e) && e[0] === index) : null;
    return row ? CODEPAGES[row[1]] || null : null;
  }

  /**
   * 지금 문서의 글을 [info] 의 모양으로 — 글(txt)은 문서 API 의 GetText, 표(csv)는 지금 시트의 칸의 보이는 값. [saving] 이면 원래 인코딩으로
   * 쓸 수 없는 글자가 있을 때 UTF-8 로 바꿀지 묻고(그만두면 CANCELLED), 내보내기면 묻지 않고 '?' 로 쓴다(사용자가 그 인코딩을 골랐다).
   */
  async function textBytes(which, info, saving) {
    const w = editorWindow();
    const B = w && w.AscBuilder;
    if (!B) throw new Error('편집기의 문서 API 가 없다');
    let text;
    if (which === 'txt') {
      const A = (B.Word && B.Word.Api) || B.Api;
      if (!A || typeof A.GetDocument !== 'function') throw new Error('글 API 가 없다');
      // sdkjs 의 GetText 는 글자를 String.fromCharCode(코드 포인트)로 잇는다 — BMP 밖 글자(이모지 따위)가 아래 16비트로 잘린다(2026-10-09 기기:
      // U+1F600 → U+F600). 부르는 동안만 그 틀의 fromCharCode 를 코드 포인트를 아는 것으로 바꿔 끼운다(동기 호출 하나 — 끝나면 되돌린다).
      const S = w.String;
      const fromCharCode = S.fromCharCode;
      S.fromCharCode = function (...units) {
        return units.length === 1 && units[0] > 0xFFFF && units[0] <= 0x10FFFF ? S.fromCodePoint(units[0]) : fromCharCode.apply(S, units);
      };
      try {
        text = A.GetDocument().GetText({
          Numbering: true, Math: true, TableCellSeparator: '\t', TableRowSeparator: '\n', ParaSeparator: '\n', TabSymbol: '\t', NewLineSeparator: '\n',
        });
      } finally {
        S.fromCharCode = fromCharCode;
      }
      // GetText 는 마지막 문단 뒤에도 문단 구분을 붙인다.
      if (text.endsWith('\n')) text = text.slice(0, -1);
    } else {
      text = sheetCsv(B, info.delimiter);
    }
    text = text.replace(/\r\n|\r|\n/g, info.eol);
    if (info.finalEol && text !== '' && !text.endsWith(info.eol)) text += info.eol;
    let r = encodeText(text, info.encoding, info.bom);
    if (r.lost && saving) {
      const go = await ask('이 문서의 인코딩(' + encodingLabel(info.encoding) + ')으로 쓸 수 없는 글자가 ' + r.lost +
        '개 있습니다. UTF-8로 바꿔 저장할까요?', 'UTF-8로 저장');
      if (!go) throw CANCELLED;
      info.encoding = 'utf-8';
      // 표(CSV)는 BOM 을 붙인다 — 엑셀이 BOM 없는 UTF-8 CSV 를 지역 인코딩으로 읽는다.
      info.bom = which === 'csv';
      r = encodeText(text, info.encoding, info.bom);
    }
    return r.bytes;
  }

  /** 지금 시트(편집기가 CSV 로 쓰는 것과 같다)를 A1 부터 쓴 마지막 칸까지 CSV 로. 줄은 \n 으로 잇는다(부르는 쪽이 원래 줄 끝으로 바꾼다). */
  function sheetCsv(B, delimiter) {
    const A = B.Cell && B.Cell.Api;
    if (!A || typeof A.GetActiveSheet !== 'function') throw new Error('표 API 가 없다');
    const ws = A.GetActiveSheet();
    const used = String(ws.GetUsedRange().GetAddress(false, false, 'xlA1', false) || 'A1');
    const end = used.split(':').pop();
    let rows = ws.GetRange('A1:' + end).GetText();
    if (!Array.isArray(rows)) rows = [[rows]];
    const quote = (v) => {
      const s = v == null ? '' : String(v);
      return s.includes(delimiter) || /["\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    };
    const out = rows.map((r) => (Array.isArray(r) ? r : [r]).map(quote).join(delimiter)).join('\n');
    // 빈 시트(A1 하나가 비었다)는 빈 파일로.
    return rows.length === 1 && out === '' ? '' : out;
  }

  const encodingLabel = (e) => ({ 'utf-8': 'UTF-8', 'utf-16le': 'UTF-16LE', 'utf-16be': 'UTF-16BE', 'utf-32le': 'UTF-32LE',
    'utf-32be': 'UTF-32BE', 'euc-kr': 'CP949' })[e] || e.toUpperCase();

  /** [text] → [encoding] 의 바이트(+ BOM). 그 인코딩에 없는 글자는 '?' 로 쓰고 그 수를 lost 로 센다. */
  function encodeText(text, encoding, bom) {
    if (encoding === 'utf-8') {
      const body = new TextEncoder().encode(text);
      if (!bom) return { bytes: body, lost: 0 };
      const out = new Uint8Array(body.length + 3);
      out.set([0xEF, 0xBB, 0xBF]);
      out.set(body, 3);
      return { bytes: out, lost: 0 };
    }
    if (encoding === 'utf-16le' || encoding === 'utf-16be') {
      const little = encoding === 'utf-16le';
      const off = bom ? 2 : 0;
      const out = new Uint8Array(off + text.length * 2);
      const dv = new DataView(out.buffer);
      if (bom) dv.setUint16(0, 0xFEFF, little);
      for (let i = 0; i < text.length; i++) dv.setUint16(off + i * 2, text.charCodeAt(i), little);
      return { bytes: out, lost: 0 };
    }
    if (encoding === 'utf-32le' || encoding === 'utf-32be') {
      const little = encoding === 'utf-32le';
      const cps = Array.from(text, (ch) => ch.codePointAt(0));
      const off = bom ? 4 : 0;
      const out = new Uint8Array(off + cps.length * 4);
      const dv = new DataView(out.buffer);
      if (bom) dv.setUint32(0, 0xFEFF, little);
      cps.forEach((cp, i) => dv.setUint32(off + i * 4, cp, little));
      return { bytes: out, lost: 0 };
    }
    const table = legacyTable(encoding);
    const out = [];
    let lost = 0;
    for (const ch of text) {
      const cp = ch.codePointAt(0);
      if (cp < 0x80) { out.push(cp); continue; }
      const seq = table.get(ch);
      if (seq) { for (const b of seq) out.push(b); } else { out.push(0x3F); lost++; }
    }
    return { bytes: Uint8Array.from(out), lost };
  }

  /**
   * 옛 인코딩(CP949·Shift_JIS·GBK·Big5·windows-125x …)의 쓰기 표 — 웹에는 그 인코더가 없어(TextEncoder 는 UTF-8 뿐) 그 인코딩의 TextDecoder 로
   * 한 바이트(0x80–0xFF)와 두 바이트(앞 0x81–0xFE · 뒤 0x40–0xFE)를 모두 풀어 거꾸로 세운다. 두 바이트마다 줄바꿈(0x0A — 어느 디코더도 다음
   * 글자의 일부로 먹지 않는다)을 끼워 한 번에 풀고, 한 글자로 풀린 것만 받는다. 처음 쓸 때 한 번(2만 4천 짝).
   */
  const legacyTables = new Map();
  function legacyTable(label) {
    let t = legacyTables.get(label);
    if (t) return t;
    t = new Map();
    const dec = new TextDecoder(label);
    const pairs = [];
    for (let lead = 0x81; lead <= 0xFE; lead++) for (let trail = 0x40; trail <= 0xFE; trail++) pairs.push(lead, trail, 0x0A);
    const parts = dec.decode(Uint8Array.from(pairs)).split('\n');
    if (parts.length === pairs.length / 3 + 1) {
      let k = 0;
      for (let lead = 0x81; lead <= 0xFE; lead++) {
        for (let trail = 0x40; trail <= 0xFE; trail++) {
          const s = parts[k++];
          if (s.length === 1 && s !== '�' && s.charCodeAt(0) >= 0x80 && !t.has(s)) t.set(s, [lead, trail]);
        }
      }
    } else {
      log('두 바이트 표를 세우지 못했다(' + label + ')');
    }
    for (let b = 0x80; b <= 0xFF; b++) {
      const s = dec.decode(Uint8Array.of(b));
      if (s.length === 1 && s !== '�' && !t.has(s)) t.set(s, [b]);
    }
    legacyTables.set(label, t);
    return t;
  }

  // ---- 10. 알림 띠 ------------------------------------------------------------------------------------------------------------
  /** 편집을 열 때 한 번 — 저장하기 전에 알아야 할 것. 닫기 단추로, 아니면 15초 뒤 저절로 걷힌다. 보기 모드에는 없다(저장하지 않는다). */
  function showNotes() {
    if (!editing) return;
    const notes = [];
    const kept = '원래 파일은 그대로 두고 곁에 새 파일을 만듭니다: ‘' + saveName + '’';
    if (spec.note === 'pdf') notes.push('PDF를 문서로 바꿔 열어 모양이 원래와 다를 수 있습니다. 저장하면 ' + kept);
    if (spec.note === 'legacy') notes.push('이전 형식(' + kind.toUpperCase() + ') 문서입니다. 저장하면 ' + kept);
    if (spec.note === 'convert') notes.push(kind.toUpperCase() + ' 문서는 저장하면 서식 일부가 원래와 달라질 수 있습니다.');
    if (spec.note === 'macro') notes.push('매크로는 실행하지 않습니다 — 저장하면 그대로 남습니다.');
    if (spec.text === 'txt') notes.push('저장하면 글자만 남습니다(서식·그림 제외). 인코딩 ' + encodingLabel(textInfo.encoding) + '.');
    if (spec.text === 'csv') {
      notes.push('저장하면 지금 시트의 보이는 값만 남습니다(서식·수식·다른 시트 제외). 인코딩 ' +
        encodingLabel(textInfo.encoding) + ' · 구분자 ' + DELIMITERS[textInfo.delimiter].label + '.');
    }
    if (password != null) notes.push('암호가 걸린 문서입니다 — 저장하면 같은 암호가 걸립니다.');
    if (!notes.length) return;
    const box = document.getElementById('io-note');
    document.getElementById('io-note-text').textContent = notes.join('\n');
    const close = document.getElementById('io-note-close');
    let timer = 0;
    const hide = () => { box.hidden = true; clearTimeout(timer); close.onclick = null; };
    close.onclick = hide;
    box.hidden = false;
    timer = setTimeout(hide, 15000);
  }

  /** 알림 띠에 짧게(5초) 한 줄 — 링크를 열 앱이 없을 때 따위. */
  function flash(text) {
    const box = document.getElementById('io-note');
    document.getElementById('io-note-text').textContent = text;
    const close = document.getElementById('io-note-close');
    let timer = 0;
    const hide = () => { box.hidden = true; clearTimeout(timer); close.onclick = null; };
    close.onclick = hide;
    box.hidden = false;
    timer = setTimeout(hide, 5000);
  }

  // ---- 11. 뒤로 가기 -----------------------------------------------------------------------------------------------------------
  // 예전에는 메뉴·창을 연 채 뒤로 가면 문서가 통째로 닫혔다. 앱이 먼저 묻고({"op":"back"}), 맨 위에 열린 것을 하나 닫는다:
  // 이 쪽의 칸(암호 — '그만두기' 가 된다, 물음 — '취소'), 편집기의 창(asc-window — 닫기 단추, 없으면 Escape), 펼친 메뉴(web-apps 의 메뉴 관리자가
  // 모두 접는다), 파일 메뉴·왼쪽 판(About·찾기·댓글 — 편집기의 Escape 단축키가 닫는다). 열지 못한 덮개(#io-error) 위에서는 닫을 것이 없다.
  // 닫을 것이 없으면 거짓 — 앱이 닫는다(고친 것이 있으면 묻는다). 그 전에 편집기에 아직 보내지 않은 고침(입력 중인 칸 포함)이 있으면 '고친 것
  // 있음' 을 먼저 알린다(4). 닫으려 한 것이 닫히지 않으면(편집기가 판을 바꿨다) 다음 뒤로 가기는 거짓으로 답한다 — 뒤로 가기가 먹통이 되지 않게.
  let backTried = null;
  const shownIn = (w, el) => !!el && el.isConnected && el.offsetWidth > 0 && el.offsetHeight > 0 && w.getComputedStyle(el).visibility !== 'hidden';
  /** 틀 안에 Escape — web-apps·sdkjs 는 keyCode 를 본다(KeyboardEvent 를 만들 때 줄 수 없어 붙인다). 문서 입력칸이 아니라 body 로 보낸다. */
  function escapeIn(w) {
    const ev = new w.KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true });
    Object.defineProperty(ev, 'keyCode', { get: () => 27 });
    Object.defineProperty(ev, 'which', { get: () => 27 });
    w.document.body.dispatchEvent(ev);
  }
  /** 지금 맨 위에 열린 것 — { el, close, open }(open 은 아직 열려 있는가). 없으면 null. */
  function topmostOpen() {
    const pass = document.getElementById('io-pass');
    if (!pass.hidden) return { el: pass, open: () => !pass.hidden, close: () => document.getElementById('io-pass-cancel').click() };
    const askBox = document.getElementById('io-ask');
    if (!askBox.hidden) return { el: askBox, open: () => !askBox.hidden, close: () => document.getElementById('io-ask-cancel').click() };
    const w = editorWindow();
    const d = w && w.document;
    if (!d || !d.body) return null;
    const wins = Array.from(d.querySelectorAll('.asc-window')).filter((el) => shownIn(w, el));
    if (wins.length) {
      const z = (el) => Number(w.getComputedStyle(el).zIndex) || 0;
      const top = wins.reduce((a, b) => (z(b) >= z(a) ? b : a));
      return {
        el: top,
        open: () => shownIn(w, top),
        close: () => {
          const x = top.querySelector('.header .tool.close');
          if (shownIn(w, x)) x.click(); else escapeIn(w);
        },
      };
    }
    const menu = Array.from(d.querySelectorAll('.open > .dropdown-menu')).find((el) => shownIn(w, el));
    if (menu) {
      const manager = w.Common && w.Common.UI && w.Common.UI.Menu && w.Common.UI.Menu.Manager;
      return { el: menu, open: () => shownIn(w, menu), close: () => { if (!(manager && manager.hideAll())) escapeIn(w); } };
    }
    const panel = ['file-menu-panel', 'about-menu-panel', 'left-panel-search', 'left-panel-comments', 'left-panel-chat', 'left-panel-navigation',
      'left-panel-plugins'].map((id) => d.getElementById(id)).find((el) => shownIn(w, el));
    if (panel) return { el: panel, open: () => shownIn(w, panel), close: () => escapeIn(w) };
    return null;
  }
  /** 맨 위에 열린 것을 하나 닫았으면 참. 닫을 것이 없으면 아직 보내지 않은 고침을 알리고 거짓. */
  function closeTopmost() {
    if (!errorEl.hidden) return false;
    let top = null;
    try { top = topmostOpen(); } catch (e) { log('뒤로 가기: ' + e); }
    if (!top || top.el === backTried) {
      backTried = null;
      noteUnsentChanges();
      return false;
    }
    try { top.close(); } catch (e) { log('닫지 못했다: ' + e); }
    backTried = top.el;
    // 닫혔으면 잊는다 — 같은 창·메뉴를 다시 열고 뒤로 가도 닫히게.
    setTimeout(() => { if (backTried === top.el && !top.open()) backTried = null; }, 450);
    return true;
  }
  function noteUnsentChanges() {
    if (!editing || !ready || dirty) return;
    const api = editorApi();
    if (!api) return;
    try {
      const modified = typeof api.asc_isDocumentModified === 'function' ? api.asc_isDocumentModified()
        : typeof api.isDocumentModified === 'function' && api.isDocumentModified();
      if (modified) {
        changeSeq++;
        setDirty(true);
      }
    } catch (e) {
      log('고침을 보지 못했다: ' + e);
    }
  }

  // ---- 12. 읽던 자리 -----------------------------------------------------------------------------------------------------------
  // '편집' 은 WebView 를 새로 세우므로 예전에는 처음으로 돌아갔다. 앱이 물으면({"op":"where"}) 문서·발표는 지금 쪽(슬라이드)
  // 번호('p:<0부터>'), 표는 시트와 보이는 첫 칸('c:<시트>,<행>,<열>' — 스크롤 막대의 단위, 고정 틀 아래부터)으로 답하고, 편집 쪽은 주소의 at 으로
  // 받아 문서를 연 뒤 그리로 간다. 커서·고른 칸은 옮기지 않는다(쪽 안의 자리까지는 넘기지 않는다 — 쪽의 맨 위로 간다). 문서는 쪽을 나누며
  // 늘어나므로 그 쪽이 생길 때까지 기다린다. 사용자가 손대면 그만둔다.
  const startAt = (() => {
    const a = params.get('at') || '';
    let m = /^p:(\d{1,6})$/.exec(a);
    if (m && spec && spec.doc !== C) return { page: Number(m[1]) };
    m = /^c:(\d{1,4}),(\d{1,8}(?:\.\d{1,3})?),(\d{1,8}(?:\.\d{1,3})?)$/.exec(a);
    if (m && spec && spec.doc === C) return { sheet: Number(m[1]), y: Number(m[2]), x: Number(m[3]) };
    return null;
  })();
  const num = (v) => (Number.isFinite(v) && v >= 0 ? String(Math.round(v * 1000) / 1000) : null);
  /** 읽던 자리 — 문서를 열지 못했거나 모르면 null. */
  function where() {
    if (!ready || failed) return null;
    const api = editorApi();
    if (!api) return null;
    try {
      if (spec.doc === C) {
        const wb = api.wb;
        const ws = wb && typeof wb.getWorksheet === 'function' ? wb.getWorksheet() : null;
        if (!ws || typeof api.asc_getActiveWorksheetIndex !== 'function') return null;
        const smooth = typeof wb.getSmoothScrolling === 'function' && wb.getSmoothScrolling();
        const y = num(smooth ? ws.getFirstVisibleRowSmoothScroll(true) : ws.getFirstVisibleRow(true));
        const x = num(smooth ? ws.getFirstVisibleColSmoothScroll(true) : ws.getFirstVisibleCol(true));
        const sheet = api.asc_getActiveWorksheetIndex();
        return y != null && x != null && Number.isInteger(sheet) && sheet >= 0 ? 'c:' + sheet + ',' + y + ',' + x : null;
      }
      const page = typeof api.getCurrentPage === 'function' ? api.getCurrentPage() : -1;
      return Number.isInteger(page) && page >= 0 ? 'p:' + page : null;
    } catch (e) {
      log('읽던 자리를 보지 못했다: ' + e);
      return null;
    }
  }
  function restorePosition() {
    if (!startAt) return;
    const api = editorApi();
    const w = editorWindow();
    if (!api || !w) return;
    let stopped = false;
    const stop = () => { stopped = true; };
    const types = ['touchstart', 'mousedown', 'wheel', 'keydown'];
    for (const t of types) w.addEventListener(t, stop, { capture: true, passive: true });
    const done = () => { for (const t of types) w.removeEventListener(t, stop, { capture: true }); };
    if (spec.doc === C) {
      try {
        // 숨긴 시트는 고르지 않는다(asc_showWorksheet 는 숨김을 푼다).
        if (startAt.sheet !== api.asc_getActiveWorksheetIndex() && startAt.sheet < api.asc_getWorksheetsCount() &&
          !api.asc_isWorksheetHidden(startAt.sheet)) api.asc_showWorksheet(startAt.sheet);
      } catch (e) {
        log('시트를 고르지 못했다: ' + e);
      }
      setTimeout(() => {
        try {
          const wb = api.wb;
          // 스크롤 막대가 부르는 길(WorkbookView 의 _onScrollY·_onScrollX — 보이는 첫 행·열로 민다). 이 빌드에 없으면 시트만 고른다.
          if (!stopped && wb && typeof wb._onScrollY === 'function' && typeof wb._onScrollX === 'function') {
            wb._onScrollY(startAt.y);
            wb._onScrollX(startAt.x);
          }
        } catch (e) {
          log('읽던 자리로 가지 못했다: ' + e);
        }
        done();
      }, 0);
      return;
    }
    let tries = 0;
    const tick = () => {
      if (stopped) return done();
      try {
        if (api.getCountPages() > startAt.page) {
          if (api.getCurrentPage() !== startAt.page) api.goToPage(startAt.page);
          return done();
        }
      } catch (e) {
        log('읽던 쪽으로 가지 못했다: ' + e);
        return done();
      }
      // 그 쪽이 아직 없다(쪽을 나누는 중) — 10초까지 기다린다.
      if (++tries > 50) return done();
      setTimeout(tick, 200);
    };
    tick();
  }

  // ---- 13. 문서의 링크 ---------------------------------------------------------------------------------------------------------
  // 편집기는 문서의 링크를 틀 안의 window.open 으로 연다(문서 보기·편집 모두) — 이 앱에는 새 창도 인터넷도 없어 아무 일도 없었다.
  // iroiro-oo-inner.js 가 그 부름을 가로채 여기로 보낸다. 앱이 주소를 보여 주고 물은 뒤 브라우저 앱으로 넘긴다 — rhwp 어댑터의 13 과 같은 prompt
  // 길이다(앱은 이 출처의 쪽에서 온 그 글만 받는다 — EditorActivity 의 LINK_PROMPT). 열 앱이 없으면 띠로 말한다.
  const LINK_PROMPT = 'iroiro:open-link';
  function openLink(href) {
    let url;
    try { url = new URL(String(href)); } catch (_) { return; }
    if ((url.protocol !== 'http:' && url.protocol !== 'https:') || url.username || url.password || url.origin === location.origin) return;
    if (!host) return;
    const answer = window.prompt(LINK_PROMPT, url.href);
    if (answer === 'no-app') flash('링크를 열 앱이 없습니다.');
  }

  // ---- 7. 앱에서 오는 것 ------------------------------------------------------------------------------------------------------
  if (host) {
    host.addEventListener('message', (e) => {
      let msg;
      try { msg = JSON.parse(e.data); } catch (_) { return; }
      if (msg.op === 'ack') {
        const k = msg.id + ':' + msg.seq;
        const r = waiters.get(k);
        if (r) { waiters.delete(k); r(true); }
      } else if (msg.op === 'saved') {
        const r = waiters.get(msg.id + ':end');
        if (r) { waiters.delete(msg.id + ':end'); r(true); }
      } else if (msg.op === 'save-failed') {
        for (const [k, r] of waiters) if (k.startsWith(msg.id + ':')) { waiters.delete(k); r(false); }
      } else if (msg.op === 'save') {
        // 닫기 물음의 '저장'.
        if (!editing || !ready) { send({ op: 'save-cancelled' }); return; }
        save().then((ok) => { if (!ok) send({ op: 'save-cancelled' }); });
      } else if (msg.op === 'ime') {
        const i = inner();
        if (i) i.ime(msg.shown === true);
      } else if (msg.op === 'paste-data') {
        const i = inner();
        if (i) i.paste(typeof msg.text === 'string' ? msg.text : '', typeof msg.html === 'string' ? msg.html : '');
      } else if (msg.op === 'where') {
        send({ op: 'position', at: where() });
      } else if (msg.op === 'back') {
        send({ op: 'back-result', handled: closeTopmost() });
      }
    });
  }

  window.addEventListener('error', (e) => log('쪽 오류: ' + (e.message || e) + ' @' + (e.filename || '') + ':' + (e.lineno || '')));
  window.addEventListener('unhandledrejection', (e) => log('처리 안 된 거절: ' + (e.reason && e.reason.stack || e.reason)));

  open();
})();
