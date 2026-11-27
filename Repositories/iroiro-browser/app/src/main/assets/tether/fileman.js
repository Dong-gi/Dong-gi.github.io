/*
  Web 테더링의 파일 화면 — 폰의 공유 폴더를 손님의 브라우저에서 다룬다(시놀로지·QNAP File Station, Nextcloud 의 파일 화면 꼴).
  서버는 tether 모듈의 FileServer 이고 API 는 그 머리말에 있다. 바닐라 JS 하나(바깥 라이브러리 없음 — 서버의 CSP 가 이 서버의 것만 받는다).

  지키는 것:
  - 파일 이름은 늘 글로만 그린다(textContent·setAttribute 의 글) — 글을 HTML 로 끼워 넣는 길을 쓰지 않는다(FileManTest). 이름 안의 HTML
    이 돌지 않게.
  - 미리 보기는 그림·영상·소리를 <img>·<video>·<audio> 로, 글은 앞부분을 받아 <pre> 의 글로. HTML·SVG 를 이 출처의 문서로 열지 않는다.
    PDF 는 새 탭(서버가 PDF 만 inline 으로 준다).
  - 받기는 <a download> 로 — 브라우저가 디스크로 흘려 받는다(fetch 로 받아 Blob 을 만들면 통째로 메모리에 쌓인다). 여러 개·폴더는 서버가
    zip 으로 흘려 보낸다(POST /api/zip 으로 표를 받고 GET /api/zip?id= 를 연다).
  - 바꾸는 요청은 PUT 과 JSON 의 POST 뿐이다(서버가 다른 꼴을 받지 않는다 — 다른 사이트의 쪽이 사전 확인 없이 보낼 수 없는 꼴).
  - 관리 기능(새 폴더·이름 바꾸기·옮기기·복사·지우기·휴지통·폴더째 올리기)은 폰의 '관리 허용' 이 켜져 있을 때만 — 꺼져 있으면 단추를 흐리게
    하고 까닭을 짧게. 서버가 요청마다 다시 본다(403).
*/
(function () {
  'use strict';

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 작은 도구
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function $(id) { return document.getElementById(id); }

  function h(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  var SVG_NS = 'http://www.w3.org/2000/svg';

  // 24×24 선 아이콘(이 화면을 위해 그린 것).
  var ICONS = {
    folder: 'M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5z',
    file: 'M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5',
    image: 'M4 5h16v14H4zM4 16l5-5 4 4 2.5-2.5L20 17M15.5 8.5h.01',
    video: 'M3.5 6h12v12h-12zM15.5 10.5 20.5 7.5v9l-5-3',
    audio: 'M9 18V6l10-2v12M9 18a2.5 2.5 0 1 1-2.5-2.5A2.5 2.5 0 0 1 9 18zM19 16a2.5 2.5 0 1 1-2.5-2.5A2.5 2.5 0 0 1 19 16z',
    text: 'M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5M9 12h6M9 15h6M9 18h4',
    archive: 'M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5M11 5v1.5M11 8.5V10M11 12v1.5M10 15.5h2v2.5h-2z',
    pdf: 'M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5M8.5 17.5c2-1 4.5-5 4-7.5-.4-1.6-2-.6-1.4 1.2.8 2.3 3 4.6 5.4 4.6',
    doc: 'M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5M9 13l1.5 5 1.5-4 1.5 4 1.5-5',
    up: 'M12 19V5M5.5 11.5 12 5l6.5 6.5',
    refresh: 'M20 12a8 8 0 1 1-2.34-5.66M20 4.5V10h-5.5',
    upload: 'M12 15V4M7 9l5-5 5 5M4.5 15.5V19a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-3.5',
    download: 'M12 4v11M7 10l5 5 5-5M4.5 15.5V19a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-3.5',
    newFolder: 'M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5zM12 11v5M9.5 13.5h5',
    rename: 'M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4',
    move: 'M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5zM9 13.5h6M12.5 11l2.5 2.5-2.5 2.5',
    copy: 'M9 9h11v11H9zM5 15H4V4h11v1',
    trash: 'M4 7h16M9.5 7V4.5h5V7M6 7l1 13h10l1-13M10 11v5M14 11v5',
    info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5v.5',
    grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
    list: 'M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01',
    help: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .9-1 1.6v.6M12 17v.01',
    close: 'M6 6l12 12M18 6 6 18',
    prev: 'M15 5l-7 7 7 7',
    next: 'M9 5l7 7-7 7',
    chevron: 'M9 6l6 6-6 6',
    zoomIn: 'M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM15.5 15.5 20 20M7.5 10.5h6M10.5 7.5v6',
    zoomOut: 'M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM15.5 15.5 20 20M7.5 10.5h6',
    fit: 'M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5',
    restore: 'M4 12a8 8 0 1 0 2.34-5.66M4 4.5V10h5.5',
    menu: 'M4 6h16M4 12h16M4 18h16',
    back: 'M19 12H5M11 6l-6 6 6 6',
    retry: 'M20 12a8 8 0 1 1-2.34-5.66M20 4.5V10h-5.5',
    play: 'M8 5v14l11-7z',
    folderUp: 'M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5zM12 16.5v-5M9.5 14 12 11.5l2.5 2.5'
  };

  function icon(name, cls) {
    var s = document.createElementNS(SVG_NS, 'svg');
    s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('class', 'ic' + (cls ? ' ' + cls : ''));
    s.setAttribute('aria-hidden', 'true');
    var p = document.createElementNS(SVG_NS, 'path');
    p.setAttribute('d', ICONS[name] || ICONS.file);
    if (name === 'play') { p.setAttribute('fill', 'currentColor'); p.setAttribute('stroke', 'none'); }
    s.appendChild(p);
    return s;
  }

  /** 도구 단추에 아이콘과 글을 넣는다(좁은 창에서는 글을 감춘다 — title·aria-label 은 남는다). */
  function decorate(button, iconName, label) {
    button.textContent = '';
    button.appendChild(icon(iconName));
    if (label) {
      button.appendChild(h('span', 'lbl', label));
      button.setAttribute('aria-label', label);
    }
  }

  var store = {
    get: function (k, d) { try { var v = localStorage.getItem('fileman.' + k); return v == null ? d : v; } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem('fileman.' + k, v); } catch (e) { /* 사생활 보호 창 */ } }
  };

  function formatSize(bytes) {
    if (bytes == null || isNaN(bytes)) return '';
    if (bytes < 1024) return bytes + ' B';
    var units = ['KB', 'MB', 'GB', 'TB'];
    var v = bytes / 1024;
    for (var i = 0; i < units.length; i++) {
      if (v < 1024 || i === units.length - 1) return (v < 10 ? v.toFixed(1) : Math.round(v)) + ' ' + units[i];
      v /= 1024;
    }
    return bytes + ' B';
  }

  function pad(n) { return n < 10 ? '0' + n : String(n); }

  function formatWhen(ms) {
    if (!ms) return '';
    var d = new Date(ms);
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function join(dir, name) { return dir ? dir + '/' + name : name; }
  function parentOf(path) { var i = path.lastIndexOf('/'); return i < 0 ? '' : path.slice(0, i); }
  function baseOf(path) { var i = path.lastIndexOf('/'); return i < 0 ? path : path.slice(i + 1); }
  /** 이름·경로를 열쇠로 쓰는 맵 — 원형이 없다. `{}` 면 constructor·toString·__proto__ 같은 이름이 늘 '있는 것' 으로 읽힌다. */
  function dict() { return Object.create(null); }

  function url(endpoint, params) {
    var parts = [];
    for (var k in params) if (Object.prototype.hasOwnProperty.call(params, k) && params[k] != null) parts.push(k + '=' + encodeURIComponent(params[k]));
    return endpoint + (parts.length ? '?' + parts.join('&') : '');
  }

  function fileUrl(path, extra) { var p = { path: path }; if (extra) for (var k in extra) p[k] = extra[k]; return url('/api/file', p); }

  // 숫자는 수로(파일2 < 파일10), 라틴 글자 다음에 한글(윈도 탐색기·File Station 과 같은 차례 — 'ko' 의 차례는 한글을 앞에 둔다).
  var collator = (window.Intl && Intl.Collator) ? new Intl.Collator('en', { numeric: true, sensitivity: 'base' }) : null;
  function compareNames(a, b) { return collator ? collator.compare(a, b) : (a.toLowerCase() < b.toLowerCase() ? -1 : a.toLowerCase() > b.toLowerCase() ? 1 : 0); }

  /**
   * 새 이름에서 빼는 글자 — 서버(SharedPaths.isStrippedCodePoint)와 같은 것: C0·DEL·C1 제어, 줄·문단 나눔(U+2028·2029), 그리고 Unicode
   * Default_Ignorable_Code_Point 전부(방향 제어·폭 없는 글자·소프트 하이픈·한글 채움·이체 선택자·태그 글자 …, 보충 평면은 u 플래그로 코드
   * 포인트째). 거절하지 않고 뺀다(사용자 결정) — 화면은 미리 보여 주기만 하고 쓰는 이름은 서버가 정한다(답의 name).
   */
  var STRIP_RE = /[\u0000-\u001f\u007f-\u009f\u061C\u180E\u200B-\u200F\u2028-\u202E\u2060-\u2064\u2066-\u2069\uFEFF]/gu;
  /** 휴지통 판정에서만 떼는 글자 — STRIP_RE 에 Default_Ignorable 전부(서버 SharedPaths.isIgnorableCodePoint 와 같다). */
  var IGNORABLE_RE = /[\u0000-\u001f\u007f-\u009f\u00AD\u034F\u061C\u115F\u1160\u17B4\u17B5\u180B-\u180F\u200B-\u200F\u2028-\u202E\u2060-\u206F\u3164\uFE00-\uFE0F\uFEFF\uFFA0\uFFF0-\uFFF8\u{1BCA0}-\u{1BCA3}\u{1D173}-\u{1D17A}\u{E0000}-\u{E0FFF}]/gu;
  function cleanName(name) { return name.replace(STRIP_RE, ''); }

  /** 맨 위에서 휴지통 자리를 가리키는 이름인가 — 서버(SharedPaths.isTrashName)처럼 무시되는 글자 전부·대소문자·끝의 점과 공백을 가리지 않는다. */
  function isTrashName(name) { return name.replace(IGNORABLE_RE, '').replace(/[. ]+$/, '').toLowerCase() === '.web-trash'; }

  /** 새로 짓는 이름의 검사 — 보이지 않는 글자를 뺀 뒤 서버(SharedPaths.isValidName)와 같은 규칙. 틀리면 까닭, 맞으면 ''. */
  function nameProblem(name, atRoot) {
    if (atRoot === undefined) atRoot = !S.path;
    var c = cleanName(name || '');
    if (!c.trim()) return '이름을 적으세요';
    if (c === '.' || c === '..') return '쓸 수 없는 이름입니다';
    if (/[\/\\]/.test(c)) return '/ 와 \\ 는 쓸 수 없습니다';
    if (new TextEncoder().encode(c).length > 255) return '이름이 너무 깁니다';
    if (atRoot && isTrashName(c)) return '그 이름은 휴지통이 씁니다';
    return '';
  }

  /** 입력 칸 아래의 미리 보기 — 보이지 않는 글자를 빼면 무엇이 되는지. */
  function cleanHint(v) {
    var c = cleanName(v);
    return c !== v && c.trim() ? '보이지 않는 글자를 빼고 ‘' + c + '’ 로 씁니다' : '';
  }

  /** 겹치지 않는 이름 — '이름 (1).확장자'(서버의 SharedPaths.freeName 과 같은 꼴). */
  function freeName(name, isDir, taken) {
    var lower = dict();
    taken.forEach(function (n) { lower[n.toLowerCase()] = true; });
    if (!lower[name.toLowerCase()]) return name;
    var dot = isDir ? -1 : name.lastIndexOf('.');
    var stem = dot > 0 ? name.slice(0, dot) : name;
    var ext = dot > 0 ? name.slice(dot) : '';
    for (var n = 1; n < 10000; n++) {
      var c = stem + ' (' + n + ')' + ext;
      if (!lower[c.toLowerCase()]) return c;
    }
    return stem + ' (' + Date.now() + ')' + ext;
  }

  // ── 종류 ──

  function setOf(list) { var o = dict(); list.split(' ').forEach(function (x) { o[x] = true; }); return o; }
  var EXT = {
    image: setOf('jpg jpeg png gif webp bmp avif heic heif svg tif tiff ico'),
    video: setOf('mp4 m4v webm mkv mov 3gp avi wmv ts mts flv'),
    audio: setOf('mp3 m4a aac ogg oga opus flac wav amr wma mid midi'),
    text: setOf('txt md markdown log csv tsv json xml html htm css js mjs ts tsx jsx kt kts java py rb go rs c h cpp hpp cc cs swift sh bash zsh bat cmd ps1 ini conf cfg yaml yml toml srt vtt ass sql gradle properties gitignore env tex rst diff patch'),
    archive: setOf('zip 7z rar gz tgz xz bz2 tar zst apk jar iso'),
    pdf: setOf('pdf'),
    doc: setOf('doc docx hwp hwpx xls xlsx ppt pptx odt ods odp rtf epub')
  };
  var KIND_LABEL = { folder: '폴더', image: '그림', video: '동영상', audio: '소리', text: '글', archive: '압축', pdf: 'PDF', doc: '문서', file: '파일' };
  // 이 화면이 보여 줄 수 있는 것(브라우저가 그리는 형식만 — SVG·HEIC 는 아니다).
  var VIEW_IMAGE = setOf('jpg jpeg png gif webp bmp avif');
  var VIEW_VIDEO = setOf('mp4 m4v webm mkv mov');
  var VIEW_AUDIO = setOf('mp3 m4a aac ogg oga opus flac wav');
  var THUMB_FALLBACK_BYTES = 3 * 1024 * 1024;

  function extOf(name) { var i = name.lastIndexOf('.'); return i > 0 ? name.slice(i + 1).toLowerCase() : ''; }

  function kindOf(entry) {
    if (entry.dir) return 'folder';
    var e = extOf(entry.name);
    for (var k in EXT) if (EXT[k][e]) return k;
    return 'file';
  }

  /** 미리 보기 갈래 — 'image'·'video'·'audio'·'text', 아니면 null. */
  function viewKind(entry) {
    if (entry.dir) return null;
    var e = extOf(entry.name);
    if (VIEW_IMAGE[e]) return 'image';
    if (VIEW_VIDEO[e]) return 'video';
    if (VIEW_AUDIO[e]) return 'audio';
    if (EXT.text[e]) return 'text';
    return null;
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 서버
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function api(method, address, body) {
    var opts = { method: method, cache: 'no-store', credentials: 'same-origin', headers: {} };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    return fetch(address, opts).then(function (r) {
      return r.text().then(function (t) {
        var d = null;
        try { d = t ? JSON.parse(t) : null; } catch (e) { d = null; }
        if (r.status === 403 && d && d.manageOff) { S.info.manage = false; applyInfo(); }
        return { status: r.status, ok: r.ok, data: d };
      });
    }, function () { return { status: 0, ok: false, data: null }; });
  }

  function errText(res, fallback) {
    if (res.data && res.data.error) return res.data.error;
    if (res.status === 0) return '폰에 닿지 못했습니다 — 핫스팟 연결을 확인하세요';
    return (fallback || '실패했습니다') + ' (HTTP ' + res.status + ')';
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 상태
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  var S = {
    path: '',
    root: '',
    entries: [],
    view: [],
    truncated: false,
    needsFolder: false,
    loaded: false,
    sort: { key: 'name', dir: 1 },
    filter: '',
    mode: store.get('mode', 'list') === 'grid' ? 'grid' : 'list',
    selected: dict(),  // 열쇠 → true (열쇠: 보통은 이름, 휴지통에서는 id)
    count: 0,
    anchor: -1,
    cursor: -1,
    trash: false,
    trashItems: [],
    info: { manage: false, available: true },
    coarse: window.matchMedia ? matchMedia('(pointer: coarse)').matches : false,
    loadToken: 0
  };
  (function () {
    var saved = (store.get('sort', 'name:1') || 'name:1').split(':');
    if (['name', 'size', 'modified', 'kind'].indexOf(saved[0]) >= 0) S.sort = { key: saved[0], dir: saved[1] === '-1' ? -1 : 1 };
  })();

  function keyOf(entry) { return S.trash ? entry.id : entry.name; }
  function isSelected(entry) { return !!S.selected[keyOf(entry)]; }
  function selectedEntries() { return S.view.filter(isSelected); }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 요소
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  var el = {};
  ['title', 'storage', 'manageBadge', 'toolbar', 'upBtn', 'refreshBtn', 'uploadBtn', 'uploadMenu', 'uploadFilesBtn', 'uploadFolderBtn',
    'newFolderBtn', 'downloadBtn', 'renameBtn', 'moveBtn', 'copyBtn', 'deleteBtn', 'infoBtn', 'trashBtn', 'restoreBtn', 'purgeBtn',
    'emptyTrashBtn', 'filter', 'sortSel', 'viewBtn', 'helpBtn', 'crumbs', 'notice', 'main', 'tree', 'treeBack', 'treeBtn', 'pane', 'head',
    'scroller', 'spacer', 'items', 'empty', 'status', 'queue', 'queueTitle', 'queueClear', 'queueFold', 'queueList', 'viewer', 'vClose',
    'vTitle', 'vCount', 'vZoomOut', 'vZoom', 'vZoomIn', 'vFit', 'vDownload', 'vStage', 'vPrev', 'vNext', 'dialogBack', 'toasts',
    'pickFiles', 'pickFolder'].forEach(function (id) { el[id] = $(id); });

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 알림
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function toast(text, kind) {
    var t = h('div', 'toast' + (kind ? ' ' + kind : ''), text);
    el.toasts.appendChild(t);
    setTimeout(function () { t.remove(); }, kind === 'err' ? 6000 : 3200);
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 정보(저장 공간·관리 허용)
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function loadInfo() {
    return api('GET', '/api/info').then(function (res) {
      if (res.ok && res.data) { S.info = res.data; applyInfo(); }
    });
  }

  function applyInfo() {
    var info = S.info;
    if (info.free != null && info.total) {
      el.storage.hidden = false;
      var used = Math.max(0, Math.min(1, 1 - info.free / info.total));
      el.storage.querySelector('.meter > i').style.width = (used * 100).toFixed(1) + '%';
      el.storage.querySelector('.txt').textContent = '남은 공간 ' + formatSize(info.free) + ' / ' + formatSize(info.total);
      el.storage.title = '남은 공간 ' + formatSize(info.free) + ' / ' + formatSize(info.total);
      el.storage.classList.toggle('low', info.free / info.total < 0.05);
    } else {
      el.storage.hidden = true;
    }
    el.manageBadge.hidden = !S.loaded || S.needsFolder;
    el.manageBadge.textContent = info.manage ? '관리 켜짐' : '관리 꺼짐';
    el.manageBadge.classList.toggle('on', !!info.manage);
    el.manageBadge.title = info.manage
      ? '이 기기에서 폴더를 만들고 옮기고 지울 수 있습니다(지운 것은 휴지통으로)'
      : '받기·올리기만 됩니다. 폴더 만들기·옮기기·지우기는 폰의 테더링 화면에서 ‘관리 허용’ 을 켜야 합니다';
    if (!info.manage && S.trash) exitTrash();
    updateToolbar();
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 목록 읽기
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function pathFromHash() {
    var raw = location.hash.replace(/^#/, '');
    try { return decodeURIComponent(raw); } catch (e) { return ''; }
  }

  function navigate(path) {
    if (S.trash) exitTrash(true);
    var hash = '#' + encodeURIComponent(path);
    if (location.hash === hash || (!path && !location.hash)) load(path);
    else location.hash = hash;
    closeTree();
  }

  /** 폴더를 읽는다. [keep] 이면 고른 것·스크롤을 남긴다(같은 폴더를 다시 읽을 때). */
  function load(path, keep) {
    var token = ++S.loadToken;
    el.status.textContent = '읽는 중…';
    return api('GET', url('/api/list', { path: path })).then(function (res) {
      if (token !== S.loadToken) return;
      S.loaded = true;
      if (!res.ok) {
        if (res.data && res.data.needsFolder) {
          S.needsFolder = true;
          S.entries = [];
          S.path = '';
          rebuildView();
          showEmpty(res.data.error, '');
          applyInfo();
          return;
        }
        if (res.status === 404 && path) {
          toast('폴더를 찾을 수 없습니다 — 위 폴더로 갑니다', 'err');
          navigate(parentOf(path));
          return;
        }
        toast(errText(res, '목록을 읽지 못했습니다'), 'err');
        updateStatus();
        return;
      }
      var data = res.data;
      var same = data.path === S.path && keep;
      S.needsFolder = false;
      if (!same) {
        S.selected = dict();
        S.anchor = -1;
        S.cursor = -1;
        S.filter = '';
        el.filter.value = '';
        el.scroller.scrollTop = 0;
      }
      S.path = data.path || '';
      S.root = data.root || '';
      S.entries = data.entries || [];
      S.truncated = !!data.truncated;
      Tree.sync(S.path, S.entries);
      rebuildView();
      renderCrumbs();
      el.notice.className = '';
      el.notice.hidden = !S.truncated;
      el.notice.textContent = S.truncated ? '항목이 너무 많아 앞의 일부만 보입니다 — 하위 폴더로 나누면 모두 보입니다.' : '';
      document.title = (S.path ? baseOf(S.path) : (S.root || '공유 폴더')) + ' — Web 테더링';
      applyInfo();
    });
  }

  function reload() {
    if (S.trash) return loadTrash();
    loadInfo();
    return load(S.path, true);
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 거르기·정렬
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function sortValue(entry, key) {
    if (key === 'size') return entry.dir ? -1 : entry.size;
    if (key === 'modified') return S.trash ? entry.deleted : entry.modified;
    return 0;
  }

  function rebuildView() {
    var source = S.trash ? S.trashItems : S.entries;
    var f = S.filter.trim().toLowerCase();
    var list = f ? source.filter(function (e) { return e.name.toLowerCase().indexOf(f) >= 0; }) : source.slice();
    var key = S.sort.key, dir = S.sort.dir;
    list.sort(function (a, b) {
      if (a.dir !== b.dir) return a.dir ? -1 : 1;
      var c = 0;
      if (key === 'name') c = compareNames(a.name, b.name);
      else if (key === 'kind') c = compareNames(KIND_LABEL[kindOf(a)], KIND_LABEL[kindOf(b)]) || compareNames(extOf(a.name), extOf(b.name));
      else c = sortValue(a, key) - sortValue(b, key);
      if (c === 0 && key !== 'name') c = compareNames(a.name, b.name) * dir;
      return c * dir;
    });
    S.view = list;
    // 보이지 않게 된 것은 고른 것에서 뺀다.
    var keep = dict();
    var count = 0;
    list.forEach(function (e) { var k = keyOf(e); if (S.selected[k]) { keep[k] = true; count++; } });
    S.selected = keep;
    S.count = count;
    if (S.cursor >= list.length) S.cursor = list.length - 1;
    V.nodes = dict();
    renderHead();
    layout();
    renderVisible();
    if (!S.needsFolder) {
      if (list.length === 0) showEmpty(f ? '찾은 것이 없습니다' : (S.trash ? '휴지통이 비었습니다' : '빈 폴더입니다'),
        f ? '‘' + S.filter + '’ 이(가) 든 이름이 이 폴더에 없습니다.' : (S.trash ? '' : '파일을 여기로 끌어다 놓거나 ‘올리기’ 를 누르세요.'));
      else el.empty.hidden = true;
    }
    updateToolbar();
    updateStatus();
  }

  function showEmpty(big, small) {
    el.empty.textContent = '';
    el.empty.appendChild(h('div', 'big', big));
    if (small) el.empty.appendChild(h('div', '', small));
    el.empty.hidden = false;
  }

  function setSort(key, dir) {
    S.sort = { key: key, dir: dir };
    store.set('sort', key + ':' + dir);
    el.sortSel.value = key + ':' + dir;
    if (el.sortSel.value !== key + ':' + dir) el.sortSel.selectedIndex = -1;
    rebuildView();
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 보이는 줄만 그리기
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  var V = { rowH: 38, tileW: 140, tileH: 156, cols: 1, nodes: dict(), pending: false };

  function measure() {
    var narrow = window.matchMedia && matchMedia('(max-width: 760px)').matches;
    V.rowH = S.coarse ? 52 : 38;
    V.tileW = narrow ? 112 : 140;
    V.tileH = narrow ? 136 : 156;
    document.documentElement.classList.toggle('coarse', S.coarse);
  }

  function layout() {
    var n = S.view.length;
    var grid = S.mode === 'grid';
    el.items.classList.toggle('grid', grid);
    el.head.hidden = grid;
    if (grid) {
      var w = el.scroller.clientWidth - 12;
      V.cols = Math.max(1, Math.floor(w / V.tileW));
      el.items.style.gridTemplateColumns = 'repeat(' + V.cols + ', minmax(0, 1fr))';
      el.items.style.setProperty('--tile-h', V.tileH + 'px');
      el.spacer.style.height = (Math.ceil(n / V.cols) * V.tileH + 12) + 'px';
    } else {
      el.items.style.gridTemplateColumns = '';
      el.spacer.style.height = (n * V.rowH) + 'px';
    }
  }

  function scheduleRender() {
    if (V.pending) return;
    V.pending = true;
    requestAnimationFrame(function () { V.pending = false; renderVisible(); });
  }

  function renderVisible() {
    var n = S.view.length;
    var top = el.scroller.scrollTop;
    var height = el.scroller.clientHeight || 600;
    var first, last, offset;
    if (S.mode === 'grid') {
      var firstRow = Math.max(0, Math.floor(top / V.tileH) - 2);
      var lastRow = Math.ceil((top + height) / V.tileH) + 2;
      first = firstRow * V.cols;
      last = Math.min(n, lastRow * V.cols);
      offset = firstRow * V.tileH;
    } else {
      first = Math.max(0, Math.floor(top / V.rowH) - 8);
      last = Math.min(n, Math.ceil((top + height) / V.rowH) + 8);
      offset = first * V.rowH;
    }
    el.items.style.transform = 'translateY(' + offset + 'px)';
    var keepNodes = dict();
    var frag = document.createDocumentFragment();
    for (var i = first; i < last; i++) {
      var entry = S.view[i];
      var k = (S.trash ? 't:' : 'e:') + keyOf(entry);
      var node = V.nodes[k] || (S.mode === 'grid' ? makeTile(entry) : makeRow(entry));
      node.dataset.i = String(i);
      paintState(node, entry, i);
      keepNodes[k] = node;
      frag.appendChild(node);
    }
    V.nodes = keepNodes;
    el.items.textContent = '';
    el.items.appendChild(frag);
    Thumbs.pump();
  }

  function paintState(node, entry, i) {
    var sel = isSelected(entry);
    node.classList.toggle('sel', sel);
    node.classList.toggle('cursor', i === S.cursor);
    node.setAttribute('aria-selected', sel ? 'true' : 'false');
  }

  function repaint() {
    var nodes = el.items.children;
    for (var j = 0; j < nodes.length; j++) {
      var i = +nodes[j].dataset.i;
      if (S.view[i]) paintState(nodes[j], S.view[i], i);
    }
    updateToolbar();
    updateStatus();
    updateHeadCheck();
  }

  function thumbBox(entry, size) {
    var kind = S.trash ? (entry.dir ? 'folder' : kindOf(entry)) : kindOf(entry);
    var box = h('span', 'thumb k-' + kind);
    box.appendChild(icon(kind === 'doc' ? 'doc' : kind));
    if (!S.trash && (kind === 'image' || kind === 'video' || kind === 'audio')) Thumbs.want(box, entry, size, kind);
    return box;
  }

  function checkButton() {
    var b = h('button', 'ck');
    b.type = 'button';
    b.tabIndex = -1;
    b.setAttribute('aria-label', '고르기');
    b.appendChild(h('i'));
    return b;
  }

  function makeRow(entry) {
    var row = h('div', 'row cols' + (S.trash ? ' trash' : ''));
    row.setAttribute('role', 'option');
    row.appendChild(checkButton());
    var name = h('div', 'name');
    name.appendChild(thumbBox(entry, 160));
    var two = h('div', 'two');
    var txt = h('span', 'txt', entry.name);
    txt.title = entry.name;
    two.appendChild(txt);
    var sub = S.trash
      ? (entry.from ? '원래 자리: ' + entry.from : '원래 자리: 맨 위') + ' · ' + formatWhen(entry.deleted)
      : (entry.dir ? '폴더' : formatSize(entry.size)) + (entry.modified ? ' · ' + formatWhen(entry.modified) : '');
    two.appendChild(h('span', 'sub', sub));
    name.appendChild(two);
    row.appendChild(name);
    if (S.trash) {
      row.appendChild(h('div', 'meta col-from', entry.from ? entry.from : '(맨 위)'));
      row.appendChild(h('div', 'meta col-when', formatWhen(entry.deleted)));
      row.appendChild(h('div', 'meta r col-size', entry.dir ? '' : formatSize(entry.size)));
    } else {
      row.appendChild(h('div', 'meta r col-size', entry.dir ? '' : formatSize(entry.size)));
      row.appendChild(h('div', 'meta col-when', formatWhen(entry.modified)));
      row.appendChild(h('div', 'meta col-kind', KIND_LABEL[kindOf(entry)]));
    }
    return row;
  }

  function makeTile(entry) {
    var tile = h('div', 'tile');
    tile.setAttribute('role', 'option');
    tile.title = entry.name + (entry.dir ? '' : '\n' + formatSize(entry.size)) + (entry.modified ? '\n' + formatWhen(entry.modified) : '');
    tile.appendChild(checkButton());
    tile.appendChild(thumbBox(entry, 320));
    tile.appendChild(h('div', 'label', entry.name));
    return tile;
  }

  // ── 머리(목록 보기의 칸 이름 — 누르면 정렬) ──

  function renderHead() {
    el.head.textContent = '';
    el.head.className = 'cols' + (S.trash ? ' trash' : '');
    var all = checkButton();
    all.id = 'allCheck';
    all.setAttribute('aria-label', '모두 고르기');
    all.addEventListener('click', function () { if (S.count === S.view.length && S.count > 0) clearSelection(); else selectAll(); });
    el.head.appendChild(all);
    var cols = S.trash
      ? [['name', '이름', ''], [null, '원래 자리', 'col-from'], ['modified', '지운 때', 'col-when'], ['size', '크기', 'r col-size']]
      : [['name', '이름', ''], ['size', '크기', 'r col-size'], ['modified', '수정한 때', 'col-when'], ['kind', '종류', 'col-kind']];
    cols.forEach(function (c) {
      var b = h('button', c[2]);
      b.type = 'button';
      var arrow = S.sort.key === c[0] ? (S.sort.dir > 0 ? ' ↑' : ' ↓') : '';
      b.textContent = c[1] + arrow;
      if (c[0]) {
        if (S.sort.key === c[0]) b.classList.add('sorted');
        b.addEventListener('click', function () { setSort(c[0], S.sort.key === c[0] ? -S.sort.dir : (c[0] === 'name' || c[0] === 'kind' ? 1 : -1)); });
      } else {
        b.disabled = true;
      }
      el.head.appendChild(b);
    });
    updateHeadCheck();
  }

  function updateHeadCheck() {
    var all = $('allCheck');
    if (!all) return;
    all.classList.toggle('on', S.count > 0 && S.count === S.view.length);
    all.classList.toggle('some', S.count > 0 && S.count < S.view.length);
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 섬네일 — 보이는 것만, 동시에 넷
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  var Thumbs = {
    queue: [],
    active: 0,
    max: 4,
    failed: dict(),

    want: function (box, entry, size, kind) {
      var path = join(S.path, entry.name);
      var key = path + '|' + entry.modified + '|' + size;
      if (Thumbs.failed[key] === 2) return;
      var img = h('img');
      img.alt = '';
      img.decoding = 'async';
      img.hidden = true;
      box.appendChild(img);
      // 서버의 섬네일이 없으면 브라우저가 그리는 작은 그림은 원본으로 대신한다(서버는 남이 올린 그림을 풀지 않는다 — 서버의 머리말).
      var fallback = kind === 'image' && VIEW_IMAGE[extOf(entry.name)] && entry.size <= THUMB_FALLBACK_BYTES ? fileUrl(path) : null;
      var first = Thumbs.failed[key] === 1 ? fallback : url('/api/thumb', { path: path, size: size, v: entry.modified });
      if (!first) return;
      Thumbs.queue.push({ img: img, box: box, src: first, fallback: first === fallback ? null : fallback, key: key, video: kind === 'video' });
    },

    pump: function () {
      while (Thumbs.active < Thumbs.max && Thumbs.queue.length) {
        var job = Thumbs.queue.shift();
        if (!job.img.isConnected) continue;
        Thumbs.start(job);
      }
    },

    start: function (job) {
      Thumbs.active++;
      var done = function () { Thumbs.active--; Thumbs.pump(); };
      job.img.onload = function () {
        job.img.hidden = false;
        var svg = job.box.querySelector('svg.ic');
        if (svg) svg.remove();
        if (job.video) job.box.appendChild(icon('play', 'play'));
        done();
      };
      job.img.onerror = function () {
        if (job.fallback) {
          Thumbs.failed[job.key] = 1;
          job.src = job.fallback;
          job.fallback = null;
          Thumbs.queue.unshift(job);
        } else {
          Thumbs.failed[job.key] = 2;
        }
        done();
      };
      job.img.src = job.src;
    }
  };

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 고르기
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function setSelected(entry, on) {
    var k = keyOf(entry);
    if (on && !S.selected[k]) { S.selected[k] = true; S.count++; }
    else if (!on && S.selected[k]) { delete S.selected[k]; S.count--; }
  }

  function selectOnly(i) {
    S.selected = dict();
    S.count = 0;
    if (S.view[i]) setSelected(S.view[i], true);
    repaint();
  }

  function toggleAt(i) {
    if (!S.view[i]) return;
    setSelected(S.view[i], !isSelected(S.view[i]));
    repaint();
  }

  function selectRange(a, b, add) {
    if (!add) { S.selected = dict(); S.count = 0; }
    var lo = Math.min(a, b), hi = Math.max(a, b);
    for (var i = lo; i <= hi; i++) if (S.view[i]) setSelected(S.view[i], true);
    repaint();
  }

  function selectAll() {
    S.view.forEach(function (e) { setSelected(e, true); });
    repaint();
  }

  function clearSelection() {
    S.selected = dict();
    S.count = 0;
    repaint();
  }

  function setCursor(i, scroll) {
    if (!S.view.length) { S.cursor = -1; return; }
    S.cursor = Math.max(0, Math.min(S.view.length - 1, i));
    if (scroll !== false) ensureVisible(S.cursor);
    repaint();
  }

  function ensureVisible(i) {
    var top, bottom;
    if (S.mode === 'grid') {
      var row = Math.floor(i / V.cols);
      top = row * V.tileH;
      bottom = top + V.tileH + 12;
    } else {
      top = i * V.rowH;
      bottom = top + V.rowH;
    }
    var st = el.scroller.scrollTop, ch = el.scroller.clientHeight;
    if (top < st) el.scroller.scrollTop = top;
    else if (bottom > st + ch) el.scroller.scrollTop = bottom - ch;
    renderVisible();
  }

  function selectByName(names) {
    S.selected = dict();
    S.count = 0;
    var first = -1;
    S.view.forEach(function (e, i) {
      if (names.indexOf(e.name) >= 0) { setSelected(e, true); if (first < 0) first = i; }
    });
    if (first >= 0) setCursor(first);
    else repaint();
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 길(빵 부스러기)·폴더 나무
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function renderCrumbs() {
    el.crumbs.textContent = '';
    var add = function (label, path, current) {
      var b = h('button', '', label);
      b.type = 'button';
      if (current) b.setAttribute('aria-current', 'page');
      b.addEventListener('click', function () { navigate(path); });
      el.crumbs.appendChild(b);
    };
    if (S.trash) {
      add(S.root || '공유 폴더', '', false);
      el.crumbs.appendChild(h('span', 'slash', '›'));
      var t = h('button', '', '휴지통');
      t.type = 'button';
      t.setAttribute('aria-current', 'page');
      el.crumbs.appendChild(t);
      return;
    }
    var segs = S.path ? S.path.split('/') : [];
    add(S.root || '공유 폴더', '', segs.length === 0);
    var acc = '';
    segs.forEach(function (s, i) {
      acc = join(acc, s);
      el.crumbs.appendChild(h('span', 'slash', '›'));
      add(s, acc, i === segs.length - 1);
    });
  }

  var Tree = {
    nodes: (function () { var o = dict(); o[''] = { path: '', loaded: false, open: true, children: [] }; return o; })(),

    node: function (path) {
      if (!Tree.nodes[path]) Tree.nodes[path] = { path: path, loaded: false, open: false, children: [] };
      return Tree.nodes[path];
    },

    /** 목록을 읽은 폴더의 하위 폴더를 나무에도 맞춘다. */
    sync: function (path, entries) {
      var n = Tree.node(path);
      n.children = entries.filter(function (e) { return e.dir; }).map(function (e) { return e.name; }).sort(compareNames);
      n.loaded = true;
      // 길의 조상은 펼친다.
      var p = path;
      while (true) {
        Tree.node(p).open = true;
        if (!p) break;
        p = parentOf(p);
      }
      Tree.ensureAncestors(path).then(Tree.render);
    },

    ensureAncestors: function (path) {
      var chain = [];
      var p = parentOf(path);
      while (true) { if (!Tree.node(p).loaded) chain.unshift(p); if (!p) break; p = parentOf(p); }
      if (!path) chain = chain.filter(function (x) { return x !== ''; });
      var step = Promise.resolve();
      chain.forEach(function (c) { step = step.then(function () { return Tree.fetch(c); }); });
      return step;
    },

    fetch: function (path) {
      return api('GET', url('/api/list', { path: path, dirs: 1 })).then(function (res) {
        var n = Tree.node(path);
        n.loaded = true;
        n.children = res.ok && res.data ? res.data.entries.map(function (e) { return e.name; }).sort(compareNames) : [];
      });
    },

    toggle: function (path) {
      var n = Tree.node(path);
      n.open = !n.open;
      if (n.open && !n.loaded) Tree.fetch(path).then(Tree.render);
      else Tree.render();
    },

    render: function () {
      var frag = document.createDocumentFragment();
      var walk = function (path, name, depth) {
        var n = Tree.node(path);
        var row = h('div', 'node' + (!S.trash && path === S.path ? ' current' : ''));
        row.style.paddingLeft = (6 + depth * 16) + 'px';
        var tw = h('button', 'tw' + (n.open ? ' open' : '') + (n.loaded && n.children.length === 0 ? ' leaf' : ''));
        tw.type = 'button';
        tw.tabIndex = -1;
        tw.setAttribute('aria-label', n.open ? '접기' : '펼치기');
        tw.appendChild(icon('chevron'));
        tw.addEventListener('click', function (e) { e.stopPropagation(); Tree.toggle(path); });
        row.appendChild(tw);
        row.appendChild(icon('folder', 'ficon'));
        var label = h('span', 'label', name);
        label.title = name;
        row.appendChild(label);
        row.addEventListener('click', function () { navigate(path); });
        frag.appendChild(row);
        if (n.open) n.children.forEach(function (c) { walk(join(path, c), c, depth + 1); });
      };
      walk('', S.root || '공유 폴더', 0);
      el.tree.textContent = '';
      el.tree.appendChild(frag);
    }
  };

  function openTree() { el.tree.classList.add('open'); el.treeBack.classList.add('open'); }
  function closeTree() { el.tree.classList.remove('open'); el.treeBack.classList.remove('open'); }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 도구 막대·아래 줄
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  var MANAGE_OFF_TIP = '폰의 테더링 화면에서 ‘관리 허용’ 을 켜면 쓸 수 있습니다';

  function updateToolbar() {
    var sel = S.count;
    var manage = !!S.info.manage;
    var ready = S.loaded && !S.needsFolder;
    var normal = !S.trash;
    [el.upBtn, el.refreshBtn, el.uploadBtn, el.newFolderBtn, el.downloadBtn, el.renameBtn, el.moveBtn, el.copyBtn, el.deleteBtn, el.infoBtn,
      el.trashBtn].forEach(function (b) { b.hidden = !normal; });
    el.restoreBtn.hidden = el.purgeBtn.hidden = el.emptyTrashBtn.hidden = normal;
    el.toolbar.classList.toggle('in-trash', !normal);
    if (!normal) {
      el.refreshBtn.hidden = false;
      el.restoreBtn.disabled = el.purgeBtn.disabled = sel === 0;
      el.emptyTrashBtn.disabled = S.trashItems.length === 0;
    }
    el.upBtn.disabled = !S.path;
    el.uploadBtn.disabled = !ready;
    el.downloadBtn.disabled = !ready || sel === 0;
    el.infoBtn.disabled = !ready;
    var tips = { newFolderBtn: '새 폴더 (N)', renameBtn: '이름 바꾸기 (F2)', moveBtn: '옮기기 (M)', copyBtn: '복사 (C)', deleteBtn: '지우기 (Delete)', trashBtn: '휴지통' };
    Object.keys(tips).forEach(function (id) { el[id].title = manage ? tips[id] : tips[id] + ' — ' + MANAGE_OFF_TIP; });
    el.newFolderBtn.disabled = !ready || !manage;
    el.renameBtn.disabled = !ready || !manage || sel !== 1;
    el.moveBtn.disabled = el.copyBtn.disabled = el.deleteBtn.disabled = !ready || !manage || sel === 0;
    el.trashBtn.disabled = !ready || !manage;
    el.uploadFolderBtn.disabled = !manage;
    el.uploadFolderBtn.title = manage ? '' : MANAGE_OFF_TIP;
    el.filter.disabled = !ready;
    decorate(el.viewBtn, S.mode === 'grid' ? 'list' : 'grid', S.mode === 'grid' ? '목록' : '격자');
  }

  function updateStatus() {
    el.status.textContent = '';
    if (S.needsFolder) return;
    var dirs = 0, files = 0;
    S.view.forEach(function (e) { if (e.dir) dirs++; else files++; });
    var total = S.trash ? S.trashItems.length : S.entries.length;
    var left = (S.trash ? '휴지통 ' : '') + '항목 ' + total + '개' + (S.filter ? ' · 찾은 것 ' + S.view.length + '개' : '') +
      ' · 폴더 ' + dirs + ' · 파일 ' + files;
    el.status.appendChild(h('span', '', left));
    if (S.count) {
      var bytes = 0, dirSel = 0;
      selectedEntries().forEach(function (e) { if (e.dir) dirSel++; else bytes += e.size || 0; });
      el.status.appendChild(h('span', '', S.count + '개 고름' + (bytes ? ' · ' + formatSize(bytes) : '') + (dirSel ? ' · 폴더 ' + dirSel + '개' : '')));
    }
    el.status.appendChild(h('span', 'grow'));
    var help = h('button', 'wide-only', '단축키 ?');
    help.type = 'button';
    help.addEventListener('click', showHelp);
    el.status.appendChild(help);
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 열기·받기
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function open(entry) {
    if (!entry) return;
    if (S.trash) { selectOnly(S.view.indexOf(entry)); return; }
    if (entry.dir) { navigate(join(S.path, entry.name)); return; }
    var kind = kindOf(entry);
    if (kind === 'pdf') { window.open(fileUrl(join(S.path, entry.name), { inline: 1 }), '_blank', 'noopener'); return; }
    if (viewKind(entry)) { Viewer.open(entry); return; }
    downloadFile(join(S.path, entry.name), entry.name);
  }

  /** 브라우저가 디스크로 흘려 받게 한다(<a download> — 서버는 늘 attachment 로 준다). */
  function clickDownload(href, name) {
    var a = h('a');
    a.href = href;
    a.download = name || '';
    a.rel = 'noopener';
    a.hidden = true;
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { a.remove(); }, 1000);
  }

  function downloadFile(path, name) { clickDownload(fileUrl(path), name); }

  function downloadSelection() {
    var items = selectedEntries();
    if (!items.length) return;
    if (items.length === 1 && !items[0].dir) { downloadFile(join(S.path, items[0].name), items[0].name); return; }
    var names = items.map(function (e) { return e.name; });
    toast('묶음(zip)을 준비합니다…');
    api('POST', '/api/zip', { base: S.path, names: names }).then(function (res) {
      if (!res.ok) { toast(errText(res, '묶음 받기를 시작하지 못했습니다'), 'err'); return; }
      clickDownload(url('/api/zip', { id: res.data.id }), res.data.name);
      toast('‘' + res.data.name + '’ 받기를 시작했습니다 — 파일 ' + res.data.count + '개 · ' + formatSize(res.data.bytes), 'ok');
    });
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 대화
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  var Dialog = { open: false, close: null };

  /**
   * 대화 하나 — opts: title, text(글 또는 글 목록), body(요소), input({value, select}), validate(fn → 까닭), check(글),
   * buttons([{label, value, primary, danger}]). Enter 는 primary, Esc 는 null.
   *
   * **창은 한 번에 하나다** — 열려 있으면 그 창이 닫힌 뒤에 연다. 새 창이 열린 창을 덮으면 덮인 창의 약속이 영영 풀리지 않는다: 올리기의
   * 묻기가 떠 있는 동안 옮기기의 '같은 이름' 창(답을 기다리던 요청)이 뜨자 묻기가 사라지고 대기열이 '살피는 중' 에 멈췄다(Q.asking 이
   * 풀리지 않는다 — 11-21 검토, Edge 헤드리스에서 재현).
   */
  var dialogTail = Promise.resolve();
  function dialog(opts) {
    var shown = dialogTail.then(function () { return openDialog(opts); });
    dialogTail = shown.then(function () {}, function () {});
    return shown;
  }

  function openDialog(opts) {
    return new Promise(function (resolve) {
      var back = el.dialogBack;
      back.textContent = '';
      var box = h('div', 'dialog');
      box.setAttribute('role', 'dialog');
      box.setAttribute('aria-modal', 'true');
      var title = h('h2', '', opts.title);
      title.id = 'dlgTitle';
      box.setAttribute('aria-labelledby', 'dlgTitle');
      box.appendChild(title);
      var body = h('div', 'body');
      (Array.isArray(opts.text) ? opts.text : opts.text ? [opts.text] : []).forEach(function (t) { body.appendChild(h('p', '', t)); });
      if (opts.dim) body.appendChild(h('p', 'dim', opts.dim));
      if (opts.body) body.appendChild(opts.body);
      var input = null, err = null, check = null, hint = null;
      if (opts.input) {
        input = h('input');
        input.type = 'text';
        input.value = opts.input.value || '';
        input.spellcheck = false;
        input.autocomplete = 'off';
        input.setAttribute('aria-label', opts.title);
        body.appendChild(input);
        err = h('div', 'err');
        body.appendChild(err);
        // 입력이 바뀔 때마다 고쳐 쓰는 미리 보기(보이지 않는 글자를 빼면 무엇이 되는지 — opts.hint).
        if (opts.hint) { hint = h('p', 'dim'); body.appendChild(hint); }
      }
      if (opts.check) {
        var label = h('label', 'check');
        check = h('input');
        check.type = 'checkbox';
        label.appendChild(check);
        label.appendChild(document.createTextNode(opts.check));
        body.appendChild(label);
      }
      box.appendChild(body);
      var btns = h('div', 'btns');
      var primary = null;
      var finish = function (value) {
        if (value !== null && input && opts.validate) {
          var problem = opts.validate(input.value);
          if (problem) { err.textContent = problem; input.focus(); return; }
        }
        back.hidden = true;
        back.textContent = '';
        Dialog.open = false;
        Dialog.close = null;
        resolve(value === null ? null : { value: value, text: input ? input.value : null, checked: check ? check.checked : false });
      };
      (opts.buttons || [{ label: '확인', value: 'ok', primary: true }]).forEach(function (b) {
        var bt = h('button', b.primary ? 'primary' : b.danger ? 'danger' : '', b.label);
        bt.type = 'button';
        // 고를 수 없는 단추(b.disabled)는 흐리게 두고 까닭을 title 로 — 기본 단추(Enter)가 되지 않는다.
        if (b.title) bt.title = b.title;
        if (b.disabled) bt.disabled = true;
        bt.addEventListener('click', function () { finish(b.value == null ? null : b.value); });
        if ((b.primary || b.danger) && !b.disabled) primary = primary || { el: bt, value: b.value };
        btns.appendChild(bt);
      });
      box.appendChild(btns);
      back.appendChild(box);
      back.hidden = false;
      Dialog.open = true;
      Dialog.close = function () { finish(null); };
      Dialog.enter = function () { if (primary) finish(primary.value); };
      back.onclick = function (e) { if (e.target === back) finish(null); };
      if (input) {
        input.focus();
        var sel = opts.input.select;
        if (sel) input.setSelectionRange(sel[0], sel[1]); else input.select();
        if (hint) hint.textContent = opts.hint(input.value);
        input.addEventListener('input', function () {
          err.textContent = opts.validate ? opts.validate(input.value) : '';
          if (hint) hint.textContent = opts.hint(input.value);
        });
      } else if (primary) {
        primary.el.focus();
      }
    });
  }

  function cancelButton() { return { label: '그만두기', value: null }; }

  function namesPreview(names) {
    var shown = names.slice(0, 6).map(function (n) { return '‘' + n + '’'; }).join(', ');
    return shown + (names.length > 6 ? ' 외 ' + (names.length - 6) + '개' : '');
  }

  /** 여러 항목을 다룬 결과 — doneLabel 은 '옮김'·'복사함' 꼴, failTitle 은 '옮기지 못한 것' 꼴. */
  function reportOutcome(res, doneLabel, failTitle) {
    if (!res.ok) { toast(errText(res, failTitle.replace(/ 것$/, '습니다').replace('못한습니다', '못했습니다')), 'err'); return; }
    var d = res.data || {};
    var done = (d.done || []).length, skipped = (d.skipped || []).length, failed = d.failed || [];
    var parts = [];
    if (done) parts.push(done + '개 ' + doneLabel);
    if (skipped) parts.push(skipped + '개 건너뜀');
    if (failed.length) parts.push(failed.length + '개 실패');
    toast(parts.join(' · ') || '바뀐 것이 없습니다', failed.length ? 'err' : 'ok');
    if (failed.length) {
      dialog({
        title: failTitle,
        text: failed.slice(0, 20).map(function (f) { return '‘' + f.name + '’ — ' + f.error; }),
        buttons: [{ label: '닫기', value: 'ok', primary: true }]
      });
    }
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 관리 — 새 폴더·이름 바꾸기·옮기기·복사·지우기
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function needManage() {
    if (S.info.manage) return false;
    toast('관리 기능이 꺼져 있습니다 — ' + MANAGE_OFF_TIP, 'err');
    return true;
  }

  function takenNames(except) {
    return S.entries.map(function (e) { return e.name; }).filter(function (n) { return n !== except; });
  }

  function newFolder() {
    if (needManage()) return;
    var taken = takenNames();
    dialog({
      title: '새 폴더',
      input: { value: freeName('새 폴더', true, taken) },
      validate: function (v) {
        var p = nameProblem(v);
        if (p) return p;
        // 같은 이름 검사는 보이지 않는 글자를 뺀 뒤의 이름으로(서버와 같다).
        var c = cleanName(v).toLowerCase();
        if (taken.some(function (n) { return n.toLowerCase() === c; })) return '같은 이름이 이미 있습니다';
        return '';
      },
      hint: cleanHint,
      buttons: [cancelButton(), { label: '만들기', value: 'ok', primary: true }]
    }).then(function (r) {
      if (!r) return;
      var name = cleanName(r.text);
      api('PUT', url('/api/folder', { path: join(S.path, name) })).then(function (res) {
        if (!res.ok) { toast(errText(res, '폴더를 만들지 못했습니다'), 'err'); return; }
        load(S.path, true).then(function () { selectByName([res.data.name]); });
      });
    });
  }

  function renameSelected() {
    if (needManage()) return;
    var items = selectedEntries();
    if (items.length !== 1) return;
    var entry = items[0];
    var taken = takenNames(entry.name);
    var dot = entry.dir ? -1 : entry.name.lastIndexOf('.');
    dialog({
      title: '이름 바꾸기',
      input: { value: entry.name, select: [0, dot > 0 ? dot : entry.name.length] },
      validate: function (v) {
        var p = nameProblem(v);
        if (p) return p;
        var c = cleanName(v).toLowerCase();
        if (taken.some(function (n) { return n.toLowerCase() === c; })) return '같은 이름이 이미 있습니다';
        return '';
      },
      hint: cleanHint,
      buttons: [cancelButton(), { label: '바꾸기', value: 'ok', primary: true }]
    }).then(function (r) {
      var name = r ? cleanName(r.text) : '';
      if (!r || name === entry.name) return;
      api('POST', '/api/rename', { path: join(S.path, entry.name), name: name }).then(function (res) {
        if (!res.ok) { toast(errText(res, '이름을 바꾸지 못했습니다'), 'err'); return; }
        load(S.path, true).then(function () { selectByName([res.data.name]); });
      });
    });
  }

  function deleteSelected() {
    if (needManage()) return;
    var items = selectedEntries();
    if (!items.length) return;
    var names = items.map(function (e) { return e.name; });
    dialog({
      title: '휴지통으로 옮기기',
      text: names.length + '개를 휴지통으로 옮길까요?',
      dim: namesPreview(names) + ' — 휴지통에서 되살릴 수 있습니다.',
      buttons: [cancelButton(), { label: '휴지통으로', value: 'ok', danger: true }]
    }).then(function (r) {
      if (!r) return;
      api('POST', '/api/delete', { base: S.path, names: names }).then(function (res) {
        reportOutcome(res, '휴지통으로 옮김', '휴지통으로 옮기지 못한 것');
        load(S.path, true);
      });
    });
  }

  /** 옮길 곳 고르기 — 폴더만 보이는 작은 탐색기. 고른 경로(맨 위는 '') 또는 null. */
  function pickFolder(title, actionLabel, sources) {
    var at = S.path;
    var picker = h('div', 'picker');
    var head = h('div', 'at');
    var upB = h('button', '', '위로');
    upB.type = 'button';
    var where = h('span', 'where');
    head.appendChild(upB);
    head.appendChild(where);
    var dirs = h('div', 'dirs');
    picker.appendChild(head);
    picker.appendChild(dirs);
    var token = 0;
    var show = function (path) {
      at = path;
      where.textContent = (S.root || '공유 폴더') + (path ? ' › ' + path.split('/').join(' › ') : '');
      upB.disabled = !path;
      dirs.textContent = '';
      dirs.appendChild(h('div', 'none', '읽는 중…'));
      var mine = ++token;
      api('GET', url('/api/list', { path: path, dirs: 1 })).then(function (res) {
        if (mine !== token) return;
        dirs.textContent = '';
        var list = res.ok && res.data ? res.data.entries : [];
        if (!list.length) dirs.appendChild(h('div', 'none', '하위 폴더가 없습니다'));
        list.sort(function (a, b) { return compareNames(a.name, b.name); }).forEach(function (e) {
          var full = join(path, e.name);
          var b = h('button');
          b.type = 'button';
          b.appendChild(icon('folder'));
          b.appendChild(h('span', '', e.name));
          // 옮기는 폴더 자신으로는 들어가지 못한다(서버도 자기 안으로를 거절한다).
          if (path === S.path && sources.indexOf(e.name) >= 0) b.disabled = true;
          b.addEventListener('click', function () { show(full); });
          dirs.appendChild(b);
        });
      });
    };
    upB.addEventListener('click', function () { show(parentOf(at)); });
    show(at);
    return dialog({
      title: title,
      body: picker,
      buttons: [cancelButton(), { label: actionLabel, value: 'ok', primary: true }]
    }).then(function (r) { return r ? at : null; });
  }

  function transferSelected(copy) {
    if (needManage()) return;
    var items = selectedEntries();
    if (!items.length) return;
    var names = items.map(function (e) { return e.name; });
    var verb = copy ? '복사' : '옮기기';
    pickFolder(names.length + '개를 ' + (copy ? '복사할' : '옮길') + ' 곳', '여기로 ' + verb, names).then(function (to) {
      if (to == null) return;
      var send = function (conflict) {
        return api('POST', copy ? '/api/copy' : '/api/move', { base: S.path, names: names, to: to, conflict: conflict });
      };
      send('ask').then(function (res) {
        if (res.status === 409 && res.data && res.data.conflicts) {
          return dialog({
            title: '같은 이름이 있습니다',
            text: '대상 폴더에 같은 이름이 ' + res.data.conflicts.length + '개 있습니다.',
            dim: namesPreview(res.data.conflicts),
            buttons: [cancelButton(), { label: '건너뛰기', value: 'skip' }, { label: '새 이름으로', value: 'rename', primary: true }]
          }).then(function (r) { return r ? send(r.value) : null; });
        }
        return res;
      }).then(function (res) {
        if (!res) return;
        reportOutcome(res, copy ? '복사함' : '옮김', copy ? '복사하지 못한 것' : '옮기지 못한 것');
        load(S.path, true);
      });
    });
  }

  function showInfo() {
    var items = selectedEntries();
    var dl = h('dl');
    var row = function (k, v) { dl.appendChild(h('dt', '', k)); dl.appendChild(h('dd', '', v)); };
    if (items.length === 1) {
      var e = items[0];
      row('이름', e.name);
      if (S.trash) {
        row('원래 자리', e.from || '(맨 위)');
        row('지운 때', formatWhen(e.deleted));
      } else {
        row('종류', KIND_LABEL[kindOf(e)] + (extOf(e.name) && !e.dir ? ' (.' + extOf(e.name) + ')' : ''));
        row('자리', '/' + join(S.path, e.name));
        if (e.modified) row('수정한 때', formatWhen(e.modified));
      }
      if (!e.dir) row('크기', formatSize(e.size) + ' (' + e.size.toLocaleString('ko-KR') + ' 바이트)');
    } else {
      var list = items.length ? items : S.view;
      var dirs = 0, files = 0, bytes = 0;
      list.forEach(function (x) { if (x.dir) dirs++; else { files++; bytes += x.size || 0; } });
      row(items.length ? '고른 것' : '이 폴더', (items.length || list.length) + '개');
      if (!items.length) row('자리', '/' + S.path);
      row('폴더', dirs + '개');
      row('파일', files + '개 · ' + formatSize(bytes));
    }
    dialog({
      title: items.length === 1 ? '정보' : items.length ? '고른 것의 정보' : '폴더 정보',
      body: dl,
      dim: items.length !== 1 && dirsIn(items.length ? items : S.view) ? '폴더 안의 크기는 세지 않습니다.' : null,
      buttons: [{ label: '닫기', value: 'ok', primary: true }]
    });
  }

  function dirsIn(list) { return list.some(function (x) { return x.dir; }); }

  function showHelp() {
    var keys = h('div', 'keys');
    [
      ['↑ ↓ ← →', '움직이기 (Shift 와 함께 — 이어 고르기)'], ['Enter', '열기'], ['Space', '고르기·풀기'], ['Ctrl + A', '모두 고르기'],
      ['Esc', '고른 것 풀기'], ['Backspace · Alt + ↑', '위 폴더'], ['/', '이 폴더에서 찾기'], ['V', '목록·격자'], ['R', '새로 고침'],
      ['U', '파일 올리기'], ['D', '받기'], ['I', '정보'], ['N', '새 폴더 (관리)'], ['F2', '이름 바꾸기 (관리)'],
      ['M · C', '옮기기·복사 (관리)'], ['Delete', '휴지통으로 (관리)'], ['보기에서 ← →', '앞·뒤'], ['보기에서 + − 0', '확대·축소·맞추기']
    ].forEach(function (k) {
      var kb = h('span');
      kb.appendChild(h('kbd', '', k[0]));
      keys.appendChild(kb);
      keys.appendChild(h('span', '', k[1]));
    });
    dialog({ title: '단축키', body: keys, dim: '여러 개 고르기 — Ctrl(맥은 ⌘)을 누른 채 누르기, Shift 로 이어 고르기. 터치에서는 왼쪽 네모.', buttons: [{ label: '닫기', value: 'ok', primary: true }] });
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 휴지통
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function enterTrash() {
    if (needManage()) return;
    S.trash = true;
    S.selected = dict();
    S.count = 0;
    S.cursor = -1;
    S.filter = '';
    el.filter.value = '';
    el.scroller.scrollTop = 0;
    renderCrumbs();
    Tree.render();
    loadTrash();
  }

  function loadTrash() {
    el.status.textContent = '읽는 중…';
    return api('GET', '/api/trash').then(function (res) {
      if (!S.trash) return;
      if (!res.ok) { toast(errText(res, '휴지통을 읽지 못했습니다'), 'err'); exitTrash(); return; }
      S.trashItems = res.data.items || [];
      if (S.sort.key === 'kind') S.sort = { key: 'modified', dir: -1 };
      // 보관 기간 한 줄 — 폰의 테더 화면에서 정한다(서버가 지난 것을 저절로 비운다).
      var days = res.data.retentionDays;
      el.notice.className = 'info';
      el.notice.textContent = days > 0 ? '보관 기간 ' + days + '일 — 지난 것은 저절로 비워집니다' : '보관 기간 없음 — 저절로 비우지 않습니다';
      el.notice.hidden = false;
      rebuildView();
    });
  }

  function exitTrash(silent) {
    if (!S.trash) return;
    S.trash = false;
    el.notice.hidden = true;
    S.selected = dict();
    S.count = 0;
    S.cursor = -1;
    if (!silent) { load(S.path); Tree.render(); }
  }

  function restoreSelected() {
    var ids = selectedEntries().map(function (e) { return e.id; });
    if (!ids.length) return;
    api('POST', '/api/trash/restore', { ids: ids }).then(function (res) {
      if (res.ok) {
        var done = (res.data.done || []).map(function (d) { return '/' + d.to; });
        toast(done.length + '개를 되살렸습니다' + (done.length === 1 ? ' — ' + done[0] : ''), (res.data.failed || []).length ? 'err' : 'ok');
        if ((res.data.failed || []).length) reportOutcome(res, '되살림', '되살리지 못한 것');
      } else {
        toast(errText(res, '되살리지 못했습니다'), 'err');
      }
      loadTrash();
    });
  }

  function purgeSelected(all) {
    var ids = all ? null : selectedEntries().map(function (e) { return e.id; });
    if (!all && !ids.length) return;
    dialog({
      title: all ? '휴지통 비우기' : '영구 삭제',
      text: all ? '휴지통의 ' + S.trashItems.length + '개를 모두 지울까요?' : ids.length + '개를 영구히 지울까요?',
      dim: '지운 것은 되살릴 수 없습니다.',
      buttons: [cancelButton(), { label: all ? '비우기' : '영구 삭제', value: 'ok', danger: true }]
    }).then(function (r) {
      if (!r) return;
      api('POST', '/api/trash/purge', all ? { all: true } : { ids: ids }).then(function (res) {
        if (res.ok) toast(res.data.count + '개를 지웠습니다', 'ok');
        else toast(errText(res, '지우지 못했습니다'), 'err');
        loadTrash();
      });
    });
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 올리기 대기열 — 파일마다 진행·속도·그만두기·다시, 같은 이름이면 묻기
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  /*
    같은 이름이 있으면 **덮어쓸지 한 번 묻는다**(사용자 결정). 조용히 덮는 길을 두지 않는다:
    - 서버는 `overwrite=1` 이 실린 요청만 덮는다(없으면 409) — 그 값은 사용자가 그 파일에 '덮어쓰기' 를 고른 뒤에만 싣는다.
    - '모든 파일에 적용'(윈도의 덮어쓰기 창처럼 — 겹치는 수를 알면 함께 보인다)은 **그 올리기 묶음(batch — 한 번 고르거나 끌어다 놓은 것)
      안에서만** 산다. 다음 올리기·'다시' 는 새 묶음이라 다시 묻는다.
    - '다시' 는 고른 것(덮어쓰기·새 이름)을 지우고 처음부터 살핀다.
    - 묻는 창의 기본 단추(Enter)는 '새 이름으로' 다 — 덮어쓰기가 아니다.
    - 같은 이름이 **폴더**면 덮어쓸 수 없다(한 폴더에 같은 이름의 파일과 폴더는 둘 수 없다) — '덮어쓰기' 는 흐리게, '모든 파일에 적용' 으로
      덮어쓰기를 고른 묶음에서도 그런 것은 새 이름으로 올린다(내용을 버리지 않게).
    - 이름은 보이지 않는 글자를 뺀 이름으로 살피고 보낸다(cleanName). 서버가 실제로 둔 이름(답의 name)을 쓴다.
    - 덮어쓴 원래 파일은 서버가 휴지통으로 옮긴다(답의 trashed) — 줄에 '원래 것은 휴지통에'. 원래 것이 0바이트면 서버가
      휴지통 없이 바꾸고 trashed 는 false 다(그 줄에는 아무것도 적지 않는다).
  */
  var Q = { items: [], running: 0, max: 2, asking: false, askWaiting: [], names: dict(), seq: 0, folded: false, renderPending: false };

  /** 올릴 것을 넣는다 — files: [{file, rel}] (rel 은 폴더째 올릴 때의 상대 경로). 한 번 넣은 것이 한 묶음이다. */
  function enqueue(files, baseDir) {
    if (!files.length) return;
    var nested = files.some(function (f) { return f.rel && f.rel.indexOf('/') >= 0; });
    if (nested && !S.info.manage) {
      toast('폴더째 올리기는 ' + MANAGE_OFF_TIP.replace('쓸 수 있습니다', '됩니다'), 'err');
      files = files.filter(function (f) { return !f.rel || f.rel.indexOf('/') < 0; });
      if (!files.length) return;
    }
    var batch = { policy: null };
    var dirs = dict();
    files.forEach(function (f) {
      var rel = f.rel || f.file.name;
      // 파일 이름과 새로 만들 폴더의 성분은 보이지 않는 글자를 뺀 이름으로(서버와 같은 규칙 — 같은 이름도 뺀 뒤의 이름으로 살핀다).
      var parts = rel.split('/').map(cleanName);
      var problem = '';
      parts.forEach(function (p, i) { if (!problem) problem = nameProblem(p, i === 0 && !baseDir); });
      var name = parts.pop();
      var dir = parts.length ? join(baseDir, parts.join('/')) : baseDir;
      dirs[dir] = true;
      Q.items.push({
        id: ++Q.seq, file: f.file, dir: dir, name: name, parents: dir !== baseDir, state: problem ? 'failed' : 'waiting', mode: null, batch: batch,
        loaded: 0, total: f.file.size, speed: 0, samples: [], xhr: null, message: problem ? '쓸 수 없는 이름 — ' + problem : '',
        cleaned: parts.concat([name]).join('/') !== rel, folderClash: false, trashed: false
      });
    });
    // 이 묶음이 올릴 폴더의 이름들은 새로 읽는다(앞 올리기 뒤에 바뀌었을 수 있다 — 겹치는 수가 맞게).
    Object.keys(dirs).forEach(function (d) { Q.names[d] = null; });
    Q.folded = false;
    el.queue.classList.remove('folded');
    renderQueue();
    pump();
  }

  /** 그 폴더에 있는 이름들(대기열이 정한 이름 포함) — 소문자 이름 → {name, dir}. 폴더마다 한 번 읽는다. */
  function namesIn(dir) {
    if (!Q.names[dir]) {
      Q.names[dir] = api('GET', url('/api/list', { path: dir })).then(function (res) {
        var set = dict();
        if (res.ok && res.data) res.data.entries.forEach(function (e) { set[e.name.toLowerCase()] = { name: e.name, dir: !!e.dir }; });
        return set;
      });
    }
    return Q.names[dir];
  }

  function pending(it) { return !it.mode && (it.state === 'waiting' || it.state === 'checking'); }

  /**
   * 이 묶음에서 아직 정하지 않은 것 가운데 같은 이름과 겹치는 수(이 파일 포함) — '모든 파일에 적용' 곁에 보인다. 묶음이 올릴 폴더를 다 읽어
   * 본다(폴더째 올리기에서 폴더가 너무 많으면 세지 않고 -1).
   */
  function clashCount(item) {
    var dirs = [], seen = dict();
    Q.items.forEach(function (it) {
      if (it.batch === item.batch && pending(it) && !seen[it.dir]) { seen[it.dir] = true; dirs.push(it.dir); }
    });
    if (dirs.length > 16) return Promise.resolve(-1);
    return Promise.all(dirs.map(namesIn)).then(function (sets) {
      var byDir = dict();
      dirs.forEach(function (d, i) { byDir[d] = sets[i]; });
      var n = 0;
      Q.items.forEach(function (it) { if (it.batch === item.batch && pending(it) && byDir[it.dir] && byDir[it.dir][it.name.toLowerCase()]) n++; });
      return n;
    });
  }

  function pump() {
    if (Q.asking) return;
    while (Q.running < Q.max) {
      var item = null;
      for (var i = 0; i < Q.items.length; i++) if (Q.items[i].state === 'waiting') { item = Q.items[i]; break; }
      if (!item) break;
      item.state = 'checking';
      Q.running++;
      prepare(item);
    }
    renderQueue();
  }

  function prepare(item) {
    namesIn(item.dir).then(function (set) {
      if (item.state !== 'checking') { Q.running--; pump(); return; }
      var existing = set[item.name.toLowerCase()];
      if (!existing || item.mode === 'overwrite' || item.mode === 'renamed') { start(item, set); return; }
      var decide = function (mode) {
        if (mode === 'skip') { item.state = 'skipped'; item.message = '건너뜀 — 같은 이름이 있습니다'; Q.running--; pump(); return; }
        // 폴더는 파일로 덮을 수 없다 — '모든 파일에 적용' 으로 덮어쓰기를 골랐어도 그런 것은 새 이름으로(건너뛰면 그 파일이 올라가지 않는다).
        if (mode === 'overwrite' && existing.dir) { mode = 'rename'; item.folderClash = true; }
        if (mode === 'rename') {
          var taken = Object.keys(set).map(function (k) { return set[k].name; });
          item.name = freeName(item.name, false, taken);
          item.mode = 'renamed';
        } else {
          item.mode = 'overwrite';
        }
        start(item, set);
      };
      ask(item, decide, existing);
    });
  }

  /**
   * 같은 이름을 묻는다 — **한 번에 하나씩**. 동시에 살피는 둘(Q.max)이 함께 물으면 뒤의 창이 앞의 창을 덮어 앞의 것이 영영 '살피는 중' 에
   * 남았다. 기다리는 동안 다른 물음에서 '모든 파일에 적용' 을 골랐으면 그 답을 따르고, 그만두었으면 묻지 않는다. existing 은 겹친 항목
   * ({name, dir}) — 폴더면 '덮어쓰기' 를 고를 수 없다.
   */
  function ask(item, decide, existing) {
    if (item.state !== 'checking') { Q.running--; nextAsk(); pump(); return; }
    if (item.batch.policy) { decide(item.batch.policy); nextAsk(); return; }
    if (Q.asking) { Q.askWaiting.push(function () { ask(item, decide, existing); }); return; }
    Q.asking = true;
    clashCount(item).then(function (count) {
      // 세는 동안 그만두었으면 묻지 않는다.
      if (item.state !== 'checking') { Q.asking = false; Q.running--; nextAsk(); pump(); return; }
      // '모든 파일에 적용' 은 이 묶음에 겹치는 것이 더 있을 때만(셀 수 없으면 정하지 않은 것이 남았을 때) 보인다.
      var more = count < 0
        ? Q.items.some(function (it) { return it !== item && it.batch === item.batch && pending(it); })
        : count > 1;
      var folder = existing.dir;
      dialog({
        title: '같은 이름이 있습니다',
        text: '‘' + item.name + '’ 이(가) ' + (item.dir ? '‘' + item.dir + '’' : '맨 위 폴더') + '에 이미 있습니다.' + (folder ? '' : ' 덮어쓸까요?'),
        dim: folder ? FOLDER_CLASH : '',
        check: more ? '모든 파일에 적용' + (count > 1 ? ' (겹치는 파일 ' + count + '개)' : '') : null,
        buttons: [
          { label: '건너뛰기', value: 'skip' },
          { label: '새 이름으로', value: 'rename', primary: true },
          { label: '덮어쓰기', value: 'overwrite', danger: true, disabled: folder, title: folder ? FOLDER_CLASH : '' }
        ]
      }).then(function (r) {
        Q.asking = false;
        var mode = r ? r.value : 'skip';
        if (r && r.checked) item.batch.policy = mode;
        // 묻는 동안 그만두었으면 올리지 않는다.
        if (item.state !== 'checking') { Q.running--; } else { decide(mode); }
        nextAsk();
        pump();
      });
    });
  }

  /** 같은 이름이 폴더일 때의 까닭 — 서버(SharedOps.FOLDER_SAME_NAME)와 같은 글. */
  var FOLDER_CLASH = '같은 이름의 폴더가 있습니다';

  function nextAsk() {
    if (Q.asking) return;
    var next = Q.askWaiting.shift();
    if (next) next();
  }

  function start(item, set) {
    // 대기열 안에서 같은 이름을 두 번 쓰지 않게 미리 잡는다.
    set[item.name.toLowerCase()] = { name: item.name, dir: false };
    item.state = 'uploading';
    item.loaded = 0;
    item.samples = [{ t: Date.now(), b: 0 }];
    // 덮어쓰기는 사용자가 이 파일에 '덮어쓰기' 를 고른 뒤에만 싣는다. 아니면 서버가 같은 이름을 막는다(물은 뒤 다른 손님이 그 이름을 만든
    // 틈도 — 409 면 다시 묻는다).
    var params = { path: join(item.dir, item.name), overwrite: item.mode === 'overwrite' ? '1' : '0' };
    if (item.parents) params.parents = '1';
    var xhr = new XMLHttpRequest();
    item.xhr = xhr;
    xhr.open('PUT', url('/api/file', params), true);
    xhr.upload.onprogress = function (e) {
      item.loaded = e.loaded;
      var now = Date.now();
      item.samples.push({ t: now, b: e.loaded });
      while (item.samples.length > 2 && now - item.samples[0].t > 3000) item.samples.shift();
      var s0 = item.samples[0];
      item.speed = now > s0.t ? (e.loaded - s0.b) * 1000 / (now - s0.t) : 0;
      scheduleQueueRender();
    };
    xhr.onload = function () {
      var d = null;
      try { d = JSON.parse(xhr.responseText); } catch (e) { d = null; }
      item.xhr = null;
      Q.running--;
      if (xhr.status === 200 && d && d.ok) {
        item.state = 'done';
        item.loaded = item.total;
        item.message = '';
        // 서버가 실제로 둔 이름을 쓴다(보이지 않는 글자를 뺐거나 제공자가 이름을 바꿨을 수 있다).
        if (typeof d.name === 'string' && d.name && d.name !== item.name) {
          delete set[item.name.toLowerCase()];
          item.name = d.name;
          set[item.name.toLowerCase()] = { name: item.name, dir: false };
        }
        item.trashed = d.trashed === true;
        afterUpload(item);
      } else if (xhr.status === 409 && d && d.exists) {
        // 사이에 누가 같은 이름을 만들었다 — 다시 묻는다.
        delete set[item.name.toLowerCase()];
        Q.names[item.dir] = null;
        item.state = 'waiting';
        item.mode = null;
      } else {
        if (xhr.status === 403 && d && d.manageOff) { S.info.manage = false; applyInfo(); }
        item.state = 'failed';
        item.message = (d && d.error) || ('실패 (HTTP ' + xhr.status + ')');
      }
      pump();
    };
    xhr.onerror = function () {
      item.xhr = null;
      Q.running--;
      item.state = 'failed';
      item.message = '전송이 끊겼습니다';
      pump();
    };
    xhr.onabort = function () {
      item.xhr = null;
      Q.running--;
      item.state = 'canceled';
      item.message = '그만둠';
      pump();
    };
    // File 을 그대로 본문에 — 브라우저가 Content-Length 를 붙이고 흘려 보낸다.
    xhr.send(item.file);
    renderQueue();
  }

  var reloadTimer = null;
  function afterUpload(item) {
    if (S.trash) return;
    if (item.dir === S.path || item.dir.indexOf(S.path ? S.path + '/' : '') === 0) {
      clearTimeout(reloadTimer);
      reloadTimer = setTimeout(function () { load(S.path, true); loadInfo(); }, 500);
    }
  }

  function cancelItem(item) {
    if (item.xhr) item.xhr.abort();
    else if (item.state === 'waiting' || item.state === 'checking') { item.state = 'canceled'; item.message = '그만둠'; }
    renderQueue();
  }

  /** 다시 — 새 묶음으로, 고른 것(덮어쓰기·새 이름)을 지우고 같은 이름을 다시 살핀다(있으면 다시 묻는다). */
  function retryItem(item) {
    item.state = 'waiting';
    item.message = '';
    item.loaded = 0;
    item.mode = null;
    item.folderClash = false;
    item.trashed = false;
    item.batch = { policy: null };
    Q.names[item.dir] = null;
    pump();
  }

  /** 끝난 줄의 덧말 — 이름을 다듬었는가·새 이름으로 올렸는가(같은 이름의 폴더 때문인가)·덮은 원래 것이 휴지통에 갔는가. */
  function doneNotes(it) {
    var notes = [];
    if (it.cleaned) notes.push('이름 다듬음');
    if (it.mode === 'renamed') notes.push(it.folderClash ? '새 이름(같은 이름의 폴더)' : '새 이름');
    if (it.trashed) notes.push('원래 것은 휴지통에');
    return notes.length ? ' · ' + notes.join(' · ') : '';
  }

  function scheduleQueueRender() {
    if (Q.renderPending) return;
    Q.renderPending = true;
    setTimeout(function () { Q.renderPending = false; renderQueue(); }, 250);
  }

  function renderQueue() {
    if (!Q.items.length) { el.queue.hidden = true; return; }
    el.queue.hidden = false;
    var counts = { active: 0, left: 0, done: 0, failed: 0 };
    Q.items.forEach(function (it) {
      if (it.state === 'uploading' || it.state === 'checking') counts.active++;
      else if (it.state === 'waiting') counts.left++;
      else if (it.state === 'done') counts.done++;
      else if (it.state === 'failed') counts.failed++;
    });
    el.queueTitle.textContent = counts.active + counts.left
      ? '올리는 중 ' + counts.active + ' · 남은 것 ' + counts.left + ' · 끝 ' + counts.done
      : '올리기 끝 — ' + counts.done + '개' + (counts.failed ? ' · 실패 ' + counts.failed : '');
    el.queueFold.textContent = Q.folded ? '펴기' : '접기';
    var list = el.queueList;
    list.textContent = '';
    Q.items.slice(-200).forEach(function (it) {
      var row = h('div', 'job ' + it.state);
      var top = h('div', 'top');
      var nm = h('span', 'nm', it.parents ? join(it.dir, it.name) : it.name);
      nm.title = join(it.dir, it.name);
      top.appendChild(nm);
      var pct = it.total ? Math.floor(it.loaded * 100 / it.total) : 100;
      var st = it.state === 'uploading' ? pct + '% · ' + formatSize(it.speed) + '/s'
        : it.state === 'waiting' ? '기다림' : it.state === 'checking' ? '살피는 중'
          : it.state === 'done' ? '끝 · ' + formatSize(it.total) + doneNotes(it) : it.message;
      top.appendChild(h('span', 'st', st));
      if (it.state === 'uploading' || it.state === 'waiting' || it.state === 'checking') {
        var c = h('button');
        c.type = 'button';
        c.title = '그만두기';
        c.setAttribute('aria-label', '그만두기');
        c.appendChild(icon('close'));
        c.addEventListener('click', function () { cancelItem(it); });
        top.appendChild(c);
      } else if (it.state === 'failed' || it.state === 'canceled') {
        var r = h('button');
        r.type = 'button';
        r.title = '다시';
        r.setAttribute('aria-label', '다시');
        r.appendChild(icon('retry'));
        r.addEventListener('click', function () { retryItem(it); });
        top.appendChild(r);
      }
      row.appendChild(top);
      var bar = h('div', 'bar');
      var fill = h('i');
      fill.style.width = (it.state === 'done' ? 100 : it.state === 'uploading' ? pct : 0) + '%';
      bar.appendChild(fill);
      row.appendChild(bar);
      list.appendChild(row);
    });
  }

  /** 끌어다 놓은 것 — 폴더면 안의 파일까지(webkitGetAsEntry). 1만 개에서 멈춘다. */
  function collectDrop(dt) {
    var entries = [];
    if (dt.items && dt.items.length && dt.items[0].webkitGetAsEntry) {
      for (var i = 0; i < dt.items.length; i++) {
        var en = dt.items[i].kind === 'file' ? dt.items[i].webkitGetAsEntry() : null;
        if (en) entries.push(en);
      }
    }
    if (!entries.length) return Promise.resolve(Array.prototype.map.call(dt.files || [], function (f) { return { file: f, rel: f.name }; }));
    var out = [];
    var LIMIT = 10000;
    var walk = function (entry, prefix) {
      if (out.length >= LIMIT) return Promise.resolve();
      if (entry.isFile) {
        return new Promise(function (resolve) {
          entry.file(function (f) { out.push({ file: f, rel: prefix + f.name }); resolve(); }, function () { resolve(); });
        });
      }
      if (!entry.isDirectory) return Promise.resolve();
      var reader = entry.createReader();
      var all = [];
      var readMore = function () {
        return new Promise(function (resolve) {
          reader.readEntries(function (batch) { resolve(batch); }, function () { resolve([]); });
        }).then(function (batch) {
          if (!batch.length) return null;
          all = all.concat(Array.prototype.slice.call(batch));
          return readMore();
        });
      };
      return readMore().then(function () {
        var step = Promise.resolve();
        all.forEach(function (child) { step = step.then(function () { return walk(child, prefix + entry.name + '/'); }); });
        return step;
      });
    };
    var step = Promise.resolve();
    entries.forEach(function (en) { step = step.then(function () { return walk(en, ''); }); });
    return step.then(function () {
      if (out.length >= LIMIT) toast('한 번에 ' + LIMIT + '개까지 올립니다 — 나머지는 다시 놓으세요', 'err');
      return out;
    });
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 보기 — 그림(앞·뒤·확대)·동영상·소리·글
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  var TEXT_LIMIT = 256 * 1024;

  var Viewer = {
    list: [], index: 0, kind: null, img: null, zoom: 1, fitZoom: 1, x: 0, y: 0, pointers: {}, pinch: null, media: null,

    open: function (entry) {
      Viewer.list = S.view.filter(function (e) { return viewKind(e); });
      Viewer.index = Math.max(0, Viewer.list.indexOf(entry));
      el.viewer.hidden = false;
      Viewer.show();
    },

    close: function () {
      Viewer.clear();
      el.viewer.hidden = true;
      el.scroller.focus();
    },

    clear: function () {
      if (Viewer.media) { try { Viewer.media.pause(); Viewer.media.removeAttribute('src'); Viewer.media.load(); } catch (e) { /* 이미 닫힘 */ } }
      Viewer.media = null;
      Viewer.img = null;
      Array.prototype.slice.call(el.vStage.children).forEach(function (c) { if (c !== el.vPrev && c !== el.vNext) c.remove(); });
    },

    step: function (d) {
      if (Viewer.list.length < 2) return;
      Viewer.index = (Viewer.index + d + Viewer.list.length) % Viewer.list.length;
      Viewer.show();
    },

    show: function () {
      Viewer.clear();
      var entry = Viewer.list[Viewer.index];
      if (!entry) { Viewer.close(); return; }
      var path = join(S.path, entry.name);
      var kind = viewKind(entry);
      Viewer.kind = kind;
      el.vTitle.textContent = entry.name;
      el.vTitle.title = entry.name;
      el.vCount.textContent = (Viewer.index + 1) + ' / ' + Viewer.list.length;
      var many = Viewer.list.length > 1;
      el.vPrev.hidden = el.vNext.hidden = !many;
      var zoomable = kind === 'image';
      [el.vZoomIn, el.vZoomOut, el.vFit].forEach(function (b) { b.hidden = !zoomable; });
      el.vZoom.hidden = !zoomable;
      el.vDownload.onclick = function () { downloadFile(path, entry.name); };
      // 줄의 커서도 따라간다(닫으면 그 자리). 고르지는 않는다 — 터치에서는 고른 것이 있으면 누르기가 고르기가 된다.
      var at = S.view.indexOf(entry);
      if (at >= 0) { S.cursor = at; ensureVisible(at); repaint(); }
      if (kind === 'image') {
        var img = h('img');
        img.alt = entry.name;
        img.draggable = false;
        img.addEventListener('load', function () { Viewer.fit(); });
        img.addEventListener('error', function () { Viewer.message('그림을 열 수 없습니다 — 받아서 보세요'); });
        img.src = fileUrl(path);
        Viewer.img = img;
        el.vStage.insertBefore(img, el.vPrev);
      } else if (kind === 'video' || kind === 'audio') {
        var m = h(kind);
        m.controls = true;
        m.autoplay = true;
        m.preload = 'metadata';
        if (kind === 'video') m.playsInline = true;
        m.addEventListener('error', function () { Viewer.message('이 브라우저가 재생하지 못하는 형식입니다 — 받아서 보세요'); });
        m.src = fileUrl(path);
        Viewer.media = m;
        el.vStage.insertBefore(m, el.vPrev);
      } else if (kind === 'text') {
        var box = h('div', 'text');
        var pre = h('pre', '', '읽는 중…');
        if (entry.size > TEXT_LIMIT) box.appendChild(h('div', 'cut', '앞 ' + formatSize(TEXT_LIMIT) + '만 보입니다(전체 ' + formatSize(entry.size) + ') — 모두 보려면 받으세요.'));
        box.appendChild(pre);
        el.vStage.insertBefore(box, el.vPrev);
        var mine = entry;
        fetch(fileUrl(path), { headers: { Range: 'bytes=0-' + (TEXT_LIMIT - 1) }, cache: 'no-store' }).then(function (r) {
          if (!r.ok) throw new Error(String(r.status));
          return r.arrayBuffer();
        }).then(function (buf) {
          if (Viewer.list[Viewer.index] !== mine) return;
          // 글로만 그린다(textContent) — HTML 이어도 돌지 않는다.
          pre.textContent = decodeText(buf);
        }).catch(function () { pre.textContent = '읽지 못했습니다.'; });
      }
    },

    message: function (text) {
      Viewer.clear();
      el.vStage.insertBefore(h('div', 'msg', text), el.vPrev);
    },

    fit: function () {
      var img = Viewer.img;
      if (!img || !img.naturalWidth) return;
      var sw = el.vStage.clientWidth, sh = el.vStage.clientHeight;
      Viewer.fitZoom = Math.min(1, sw / img.naturalWidth, sh / img.naturalHeight);
      Viewer.setZoom(Viewer.fitZoom);
      Viewer.center();
    },

    center: function () {
      var img = Viewer.img;
      if (!img) return;
      Viewer.x = (el.vStage.clientWidth - img.naturalWidth * Viewer.zoom) / 2;
      Viewer.y = (el.vStage.clientHeight - img.naturalHeight * Viewer.zoom) / 2;
      Viewer.apply();
    },

    setZoom: function (z, cx, cy) {
      var img = Viewer.img;
      if (!img) return;
      z = Math.max(Math.min(Viewer.fitZoom, 0.05), Math.min(8, z));
      if (cx == null) { cx = el.vStage.clientWidth / 2; cy = el.vStage.clientHeight / 2; }
      // 가리킨 자리를 그대로 두고 키운다.
      Viewer.x = cx - (cx - Viewer.x) * (z / Viewer.zoom);
      Viewer.y = cy - (cy - Viewer.y) * (z / Viewer.zoom);
      Viewer.zoom = z;
      if (z <= Viewer.fitZoom + 1e-6) { Viewer.zoom = z; Viewer.center(); return; }
      Viewer.apply();
    },

    apply: function () {
      if (!Viewer.img) return;
      Viewer.img.style.transform = 'translate(' + Viewer.x + 'px, ' + Viewer.y + 'px) scale(' + Viewer.zoom + ')';
      el.vZoom.textContent = Math.round(Viewer.zoom * 100) + '%';
    }
  };

  function decodeText(buf) {
    var bytes = new Uint8Array(buf);
    try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); } catch (e) { /* UTF-8 이 아니다 */ }
    try { return new TextDecoder('euc-kr').decode(bytes); } catch (e2) { /* 그 디코더가 없다 */ }
    return new TextDecoder('utf-8').decode(bytes);
  }

  // 그림 끌기·두 손가락 확대·밀어 넘기기
  el.vStage.addEventListener('wheel', function (e) {
    if (Viewer.kind !== 'image') return;
    e.preventDefault();
    var r = el.vStage.getBoundingClientRect();
    Viewer.setZoom(Viewer.zoom * (e.deltaY < 0 ? 1.15 : 1 / 1.15), e.clientX - r.left, e.clientY - r.top);
  }, { passive: false });

  el.vStage.addEventListener('dblclick', function (e) {
    if (Viewer.kind !== 'image' || e.target !== Viewer.img) return;
    var r = el.vStage.getBoundingClientRect();
    if (Viewer.zoom > Viewer.fitZoom + 1e-6) Viewer.fit(); else Viewer.setZoom(Math.max(1, Viewer.fitZoom * 2), e.clientX - r.left, e.clientY - r.top);
  });

  el.vStage.addEventListener('pointerdown', function (e) {
    if (Viewer.kind !== 'image' || e.target.closest('button')) return;
    el.vStage.setPointerCapture(e.pointerId);
    Viewer.pointers[e.pointerId] = { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY, t: Date.now() };
    var ids = Object.keys(Viewer.pointers);
    if (ids.length === 2) {
      var a = Viewer.pointers[ids[0]], b = Viewer.pointers[ids[1]];
      Viewer.pinch = { d: Math.hypot(a.x - b.x, a.y - b.y), z: Viewer.zoom };
    }
  });
  el.vStage.addEventListener('pointermove', function (e) {
    var p = Viewer.pointers[e.pointerId];
    if (!p) return;
    var dx = e.clientX - p.x, dy = e.clientY - p.y;
    p.x = e.clientX;
    p.y = e.clientY;
    var ids = Object.keys(Viewer.pointers);
    if (ids.length === 2 && Viewer.pinch) {
      var a = Viewer.pointers[ids[0]], b = Viewer.pointers[ids[1]];
      var r = el.vStage.getBoundingClientRect();
      Viewer.setZoom(Viewer.pinch.z * Math.hypot(a.x - b.x, a.y - b.y) / Viewer.pinch.d, (a.x + b.x) / 2 - r.left, (a.y + b.y) / 2 - r.top);
    } else if (Viewer.zoom > Viewer.fitZoom + 1e-6) {
      Viewer.x += dx;
      Viewer.y += dy;
      Viewer.apply();
    }
  });
  function endPointer(e) {
    var p = Viewer.pointers[e.pointerId];
    if (!p) return;
    delete Viewer.pointers[e.pointerId];
    if (Object.keys(Viewer.pointers).length < 2) Viewer.pinch = null;
    // 맞춘 크기에서 옆으로 밀면 앞·뒤로.
    var mx = e.clientX - p.sx, my = e.clientY - p.sy;
    if (Viewer.zoom <= Viewer.fitZoom + 1e-6 && Math.abs(mx) > 60 && Math.abs(mx) > Math.abs(my) * 1.5 && Date.now() - p.t < 600) Viewer.step(mx < 0 ? 1 : -1);
  }
  el.vStage.addEventListener('pointerup', endPointer);
  el.vStage.addEventListener('pointercancel', endPointer);

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 사건
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  function wire() {
    decorate(el.treeBtn, 'menu', null);
    decorate(el.upBtn, 'up', '위로');
    decorate(el.refreshBtn, 'refresh', '새로 고침');
    decorate(el.uploadBtn, 'upload', '올리기');
    decorate(el.newFolderBtn, 'newFolder', '새 폴더');
    decorate(el.downloadBtn, 'download', '받기');
    decorate(el.renameBtn, 'rename', '이름 바꾸기');
    decorate(el.moveBtn, 'move', '옮기기');
    decorate(el.copyBtn, 'copy', '복사');
    decorate(el.deleteBtn, 'trash', '지우기');
    decorate(el.infoBtn, 'info', '정보');
    decorate(el.trashBtn, 'trash', '휴지통');
    decorate(el.restoreBtn, 'restore', '되살리기');
    decorate(el.purgeBtn, 'close', '영구 삭제');
    decorate(el.emptyTrashBtn, 'trash', '휴지통 비우기');
    decorate(el.helpBtn, 'help', null);
    decorate(el.uploadFilesBtn, 'upload', '파일 올리기');
    decorate(el.uploadFolderBtn, 'folderUp', '폴더째 올리기');
    decorate(el.vClose, 'close', null);
    decorate(el.vPrev, 'prev', null);
    decorate(el.vNext, 'next', null);
    decorate(el.vZoomIn, 'zoomIn', null);
    decorate(el.vZoomOut, 'zoomOut', null);
    decorate(el.vFit, 'fit', null);
    decorate(el.vDownload, 'download', null);
    el.sortSel.value = S.sort.key + ':' + S.sort.dir;

    el.treeBtn.addEventListener('click', function () { if (el.tree.classList.contains('open')) closeTree(); else openTree(); });
    el.treeBack.addEventListener('click', closeTree);
    el.upBtn.addEventListener('click', function () { if (S.path) navigate(parentOf(S.path)); });
    el.refreshBtn.addEventListener('click', reload);
    el.uploadBtn.addEventListener('click', function (e) { e.stopPropagation(); el.uploadMenu.hidden = !el.uploadMenu.hidden; });
    el.uploadFilesBtn.addEventListener('click', function () { el.uploadMenu.hidden = true; el.pickFiles.click(); });
    el.uploadFolderBtn.addEventListener('click', function () { el.uploadMenu.hidden = true; if (!needManage()) el.pickFolder.click(); });
    document.addEventListener('click', function (e) { if (!e.target.closest('.menu-wrap')) el.uploadMenu.hidden = true; });
    el.newFolderBtn.addEventListener('click', newFolder);
    el.downloadBtn.addEventListener('click', downloadSelection);
    el.renameBtn.addEventListener('click', renameSelected);
    el.moveBtn.addEventListener('click', function () { transferSelected(false); });
    el.copyBtn.addEventListener('click', function () { transferSelected(true); });
    el.deleteBtn.addEventListener('click', deleteSelected);
    el.infoBtn.addEventListener('click', showInfo);
    el.trashBtn.addEventListener('click', enterTrash);
    el.restoreBtn.addEventListener('click', restoreSelected);
    el.purgeBtn.addEventListener('click', function () { purgeSelected(false); });
    el.emptyTrashBtn.addEventListener('click', function () { purgeSelected(true); });
    el.viewBtn.addEventListener('click', toggleMode);
    el.helpBtn.addEventListener('click', showHelp);
    el.sortSel.addEventListener('change', function () { var p = el.sortSel.value.split(':'); setSort(p[0], +p[1]); });
    el.filter.addEventListener('input', function () { S.filter = el.filter.value; S.cursor = -1; el.scroller.scrollTop = 0; rebuildView(); });

    el.pickFiles.addEventListener('change', function () {
      enqueue(Array.prototype.map.call(el.pickFiles.files, function (f) { return { file: f, rel: f.name }; }), S.path);
      el.pickFiles.value = '';
    });
    el.pickFolder.addEventListener('change', function () {
      enqueue(Array.prototype.map.call(el.pickFolder.files, function (f) { return { file: f, rel: f.webkitRelativePath || f.name }; }), S.path);
      el.pickFolder.value = '';
    });

    el.queueClear.addEventListener('click', function () {
      Q.items = Q.items.filter(function (it) { return it.state === 'uploading' || it.state === 'waiting' || it.state === 'checking'; });
      renderQueue();
    });
    el.queueFold.addEventListener('click', function () { Q.folded = !Q.folded; el.queue.classList.toggle('folded', Q.folded); renderQueue(); });

    el.scroller.addEventListener('scroll', scheduleRender, { passive: true });
    if (window.ResizeObserver) new ResizeObserver(function () { measure(); layout(); renderVisible(); }).observe(el.scroller);
    else window.addEventListener('resize', function () { measure(); layout(); renderVisible(); });

    el.items.addEventListener('click', function (e) {
      var node = e.target.closest('[data-i]');
      if (!node) return;
      var i = +node.dataset.i;
      var entry = S.view[i];
      if (!entry) return;
      if (e.target.closest('.ck')) { toggleAt(i); S.anchor = i; setCursor(i, false); return; }
      if (e.shiftKey) { selectRange(S.anchor >= 0 ? S.anchor : i, i, e.ctrlKey || e.metaKey); setCursor(i, false); return; }
      if (e.ctrlKey || e.metaKey) { toggleAt(i); S.anchor = i; setCursor(i, false); return; }
      if (S.coarse) {
        // 터치 — 누르면 연다. 이미 고른 것이 있으면 고르기를 이어 간다(폰의 파일 앱들처럼).
        if (S.count > 0) toggleAt(i); else open(entry);
        S.anchor = i;
        return;
      }
      selectOnly(i);
      S.anchor = i;
      setCursor(i, false);
    });
    el.items.addEventListener('dblclick', function (e) {
      if (S.coarse) return;
      var node = e.target.closest('[data-i]');
      if (node && !e.target.closest('.ck')) open(S.view[+node.dataset.i]);
    });
    el.scroller.addEventListener('click', function (e) {
      if (e.target === el.scroller || e.target === el.spacer || e.target === el.items) clearSelection();
    });

    // 끌어다 놓아 올리기 — 목록 칸 어디든.
    var depth = 0;
    var hasFiles = function (e) { return e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') >= 0; };
    el.pane.addEventListener('dragenter', function (e) { if (!hasFiles(e)) return; e.preventDefault(); depth++; el.pane.classList.add('drop-hot'); });
    el.pane.addEventListener('dragover', function (e) { if (!hasFiles(e)) return; e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; });
    el.pane.addEventListener('dragleave', function () { depth = Math.max(0, depth - 1); if (!depth) el.pane.classList.remove('drop-hot'); });
    el.pane.addEventListener('drop', function (e) {
      if (!hasFiles(e)) return;
      e.preventDefault();
      depth = 0;
      el.pane.classList.remove('drop-hot');
      if (S.trash || S.needsFolder) { toast('여기에는 올릴 수 없습니다', 'err'); return; }
      var base = S.path;
      collectDrop(e.dataTransfer).then(function (files) { enqueue(files, base); });
    });
    // 쪽의 다른 자리에 놓아도 브라우저가 파일을 열어 버리지 않게.
    ['dragover', 'drop'].forEach(function (t) { document.addEventListener(t, function (e) { if (hasFiles(e)) e.preventDefault(); }); });

    // 보기
    el.vClose.addEventListener('click', Viewer.close);
    el.vPrev.addEventListener('click', function () { Viewer.step(-1); });
    el.vNext.addEventListener('click', function () { Viewer.step(1); });
    el.vZoomIn.addEventListener('click', function () { Viewer.setZoom(Viewer.zoom * 1.25); });
    el.vZoomOut.addEventListener('click', function () { Viewer.setZoom(Viewer.zoom / 1.25); });
    el.vFit.addEventListener('click', function () { Viewer.fit(); });

    document.addEventListener('keydown', onKey);
    window.addEventListener('hashchange', function () { load(pathFromHash()); });
    window.addEventListener('focus', function () { loadInfo(); });
    if (window.matchMedia) {
      var mq = matchMedia('(pointer: coarse)');
      var onCoarse = function () { S.coarse = mq.matches; measure(); layout(); renderVisible(); };
      if (mq.addEventListener) mq.addEventListener('change', onCoarse);
    }
    window.addEventListener('beforeunload', function (e) {
      if (Q.items.some(function (it) { return it.state === 'uploading' || it.state === 'waiting'; })) { e.preventDefault(); e.returnValue = ''; }
    });
  }

  function toggleMode() {
    S.mode = S.mode === 'grid' ? 'list' : 'grid';
    store.set('mode', S.mode);
    V.nodes = dict();
    layout();
    renderVisible();
    if (S.cursor >= 0) ensureVisible(S.cursor);
    updateToolbar();
  }

  function onKey(e) {
    if (!el.viewer.hidden) {
      var k = e.key;
      if (k === 'Escape') { e.preventDefault(); Viewer.close(); }
      else if (k === 'ArrowLeft') { e.preventDefault(); Viewer.step(-1); }
      else if (k === 'ArrowRight') { e.preventDefault(); Viewer.step(1); }
      else if (Viewer.kind === 'image' && (k === '+' || k === '=')) Viewer.setZoom(Viewer.zoom * 1.25);
      else if (Viewer.kind === 'image' && (k === '-' || k === '_')) Viewer.setZoom(Viewer.zoom / 1.25);
      else if (Viewer.kind === 'image' && k === '0') Viewer.fit();
      return;
    }
    if (Dialog.open) {
      if (e.key === 'Escape') { e.preventDefault(); Dialog.close(); }
      else if (e.key === 'Enter' && e.target.tagName !== 'BUTTON') { e.preventDefault(); Dialog.enter(); }
      return;
    }
    var t = e.target;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'SELECT' || t.tagName === 'TEXTAREA')) {
      if (t === el.filter && e.key === 'Escape') { el.filter.value = ''; S.filter = ''; rebuildView(); el.scroller.focus(); }
      else if (t === el.filter && (e.key === 'ArrowDown' || e.key === 'Enter')) { e.preventDefault(); el.scroller.focus(); setCursor(0); }
      return;
    }
    if (S.needsFolder) return;
    var ctrl = e.ctrlKey || e.metaKey;
    var n = S.view.length;
    var step = S.mode === 'grid' ? V.cols : 1;
    var move = function (to) {
      if (!n) return;
      to = Math.max(0, Math.min(n - 1, to));
      if (e.shiftKey) { if (S.anchor < 0) S.anchor = S.cursor < 0 ? to : S.cursor; selectRange(S.anchor, to, ctrl); }
      else if (!ctrl) { selectOnly(to); S.anchor = to; }
      setCursor(to);
    };
    var cur = S.cursor < 0 ? -1 : S.cursor;
    var pageRows = Math.max(1, Math.floor(el.scroller.clientHeight / (S.mode === 'grid' ? V.tileH : V.rowH)));
    switch (e.key) {
      case 'ArrowDown': e.preventDefault(); if (e.altKey) break; move(cur < 0 ? 0 : cur + step); return;
      case 'ArrowUp':
        e.preventDefault();
        if (e.altKey) { if (S.path && !S.trash) navigate(parentOf(S.path)); return; }
        move(cur < 0 ? 0 : cur - step); return;
      case 'ArrowRight': if (S.mode === 'grid') { e.preventDefault(); move(cur + 1); } return;
      case 'ArrowLeft': if (S.mode === 'grid') { e.preventDefault(); move(cur - 1); } return;
      case 'Home': e.preventDefault(); move(0); return;
      case 'End': e.preventDefault(); move(n - 1); return;
      case 'PageDown': e.preventDefault(); move(cur + pageRows * step); return;
      case 'PageUp': e.preventDefault(); move(cur - pageRows * step); return;
      case 'Enter': if (cur >= 0) { e.preventDefault(); open(S.view[cur]); } return;
      case ' ': if (cur >= 0) { e.preventDefault(); toggleAt(cur); S.anchor = cur; } return;
      case 'Escape': if (S.count) clearSelection(); else if (S.trash) exitTrash(); return;
      case 'Backspace': e.preventDefault(); if (S.trash) exitTrash(); else if (S.path) navigate(parentOf(S.path)); return;
      case 'Delete': if (!S.trash && S.count) { e.preventDefault(); deleteSelected(); } else if (S.trash && S.count) { e.preventDefault(); purgeSelected(false); } return;
      case 'F2': if (!S.trash) { e.preventDefault(); renameSelected(); } return;
      case '/': e.preventDefault(); el.filter.focus(); el.filter.select(); return;
      case '?': showHelp(); return;
    }
    if (ctrl && (e.key === 'a' || e.key === 'A')) { e.preventDefault(); selectAll(); return; }
    if (ctrl || e.altKey) return;
    switch (e.key.toLowerCase()) {
      case 'v': toggleMode(); break;
      case 'r': reload(); break;
      case 'u': if (!S.trash) el.pickFiles.click(); break;
      case 'd': if (!S.trash) downloadSelection(); break;
      case 'i': showInfo(); break;
      case 'n': if (!S.trash) newFolder(); break;
      case 'm': if (!S.trash && S.count) transferSelected(false); break;
      case 'c': if (!S.trash && S.count) transferSelected(true); break;
    }
  }

  // ════════════════════════════════════════════════════════════════════════════════════════════════
  // 시작
  // ════════════════════════════════════════════════════════════════════════════════════════════════

  wire();
  measure();
  renderHead();
  updateToolbar();
  loadInfo().then(function () { return load(pathFromHash()); }).then(function () { el.scroller.focus(); });
  setInterval(function () { if (!document.hidden) loadInfo(); }, 30000);
})();
