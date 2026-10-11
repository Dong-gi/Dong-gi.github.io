'use strict';
// ONLYOFFICE 편집기 틀(iframe) 안에서 도는 우리 코드 — tools/build_onlyoffice.py 가 세 편집기(문서·표·발표)의 main/index.html 끝에 넣는다
// (손질하는 CSS 는 iroiro-oo-inner.css). 바깥 쪽(host.html 의 iroiro-onlyoffice.js)과 같은 출처라 서로의 window 를 부른다 — 앱의 통로
// (iroiroHost)는 바깥 쪽 주 틀에만 들어오므로 앱에 보낼 것은 바깥 쪽(parent.iroiroOuter)을 거치고, 앱이 보낸 것은 바깥 쪽이 이 쪽의
// window.iroiroInner 로 넘긴다.
//
//   1. 자판은 단추로만(사용자 지정 — rhwp 어댑터의 9 와 같다). 문서의 입력칸(sdkjs 의 #area_id — 문서·표·발표가 하나를 같이 쓴다)에
//      virtualkeyboardpolicy=manual 을 걸어 문서를 눌러도 자판이 올라오지 않고, 상태 표시줄 오른쪽 끝의 자판 단추가 navigator.virtualKeyboard 의
//      show()·hide() 를 부른다. 대화상자·수식 입력줄·글꼴 상자의 입력칸은 치려고 누르는 것이라 그대로 올라오고, 문서로 돌아오면 그 전 모양으로
//      되돌린다.
//   2. 붙이기 — 편집기의 '붙여 넣기'(맥락 메뉴·도구 상자)는 document.execCommand('paste') 를 부르는데 WebView 에서는 늘 실패한다(그러면 편집기
//      안에서 복사한 것만 붙는다). 그 부름을 가로채 앱에 청하고, 앱이 시스템 클립보드의 글·HTML 을 읽어 주면 입력칸에 진짜 붙이기처럼 paste
//      사건을 일으킨다(sdkjs 가 그 사건의 clipboardData 를 읽는다). 자판의 클립보드로 붙이는 것은 원래대로 된다.
//   3. 문서의 링크(보기·편집 모두) — 편집기가 여는 새 창(window.open)을 가로채 웹 주소면 바깥 쪽에 넘긴다(앱이 묻고 브라우저 앱으로 — 바깥 쪽의 13).
(() => {
  const outer = (() => {
    try { return window.parent !== window ? window.parent.iroiroOuter : null; } catch (_) { return null; }
  })();
  if (!outer) return; // 우리 바깥 쪽 없이 열렸다 — 손대지 않는다.
  const log = (m) => outer.log('틀: ' + m);
  const editing = outer.editing === true;
  const input = () => document.getElementById('area_id');

  /** 앱이 알려 주는 자판의 보임(1)과 읽어 준 클립보드(2) — 바깥 쪽이 부른다. */
  const inner = window.iroiroInner = { ime() {}, paste() {} };

  // ---- 0. 고지 — 왼쪽 막대의 (i)(About) 칸에 묶은 것과 라이선스를 적는다(보기 모드에도) ----------------------------------------------------
  // 편집기가 스스로 보이는 것은 '문서 편집기 / 버전 0.0.0.0' 뿐이다. 앱에 실은 라이선스 글(tools/build_onlyoffice.py 가 licenses/ 에 둔다)을
  // 누르면 그 자리에 펼친다 — 같은 출처라 바로 읽는다. 주소는 글로만 적는다(이 앱은 밖으로 나가지 않는다).
  {
    const LICENSES = [
      ['ONLYOFFICE sdkjs의 라이선스', 'sdkjs-LICENSE.txt'],
      ['ONLYOFFICE web-apps의 라이선스', 'web-apps-LICENSE.txt'],
      ['x2t(변환기)의 라이선스', 'x2t-core-LICENSE.txt'],
      ['GNU AGPL 3.0 전문', 'AGPL-3.0-or-later.txt'],
      ['sdkjs가 쓰는 제3자 코드', 'sdkjs-3DPARTY.md'],
      ['x2t가 쓰는 제3자 코드', 'x2t-core-3DPARTY.md'],
      ['실은 글꼴과 그 라이선스', 'FONTS.txt'],
      ['SIL OFL 1.1 전문', 'OFL-1.1.txt'],
      ['Apache 2.0 전문', 'Apache-2.0.txt'],
    ];
    const addNotice = (panel) => {
      if (!panel || panel.querySelector('#iroiro-notice')) return;
      const box = document.createElement('div');
      box.id = 'iroiro-notice';
      const p = document.createElement('p');
      p.textContent = '이 편집기는 ONLYOFFICE의 sdkjs·web-apps·x2t를 CryptPad가 문서 서버 없이 쓰게 고친 빌드(v9.3.2+3)로 앱 안에 묶어 씁니다. ' +
        '그 코드는 GNU AGPL 3.0이고 소스는 github.com/cryptpad/onlyoffice-editor · github.com/cryptpad/onlyoffice-x2t-wasm에 있습니다. ' +
        '앱이 더한 손질(어댑터)은 iroiro-viewer 저장소의 feature/editor에 있습니다.';
      box.appendChild(p);
      for (const [label, file] of LICENSES) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'iroiro-notice-item';
        button.textContent = label;
        const pre = document.createElement('pre');
        pre.hidden = true;
        button.addEventListener('click', () => {
          if (!pre.hidden) { pre.hidden = true; return; }
          pre.hidden = false;
          if (pre.textContent) return;
          pre.textContent = '…';
          fetch('/licenses/' + file).then((r) => (r.ok ? r.text() : Promise.reject(new Error('HTTP ' + r.status))))
            .then((t) => { pre.textContent = t; })
            .catch(() => { pre.textContent = '이 빌드에 실리지 않았습니다.'; });
        });
        box.appendChild(button);
        box.appendChild(pre);
      }
      panel.appendChild(box);
    };
    // 칸은 (i) 를 처음 누를 때 채워진다 — 누른 뒤에 덧붙인다(문서 전체의 바뀜을 지켜보면 편집하는 내내 값을 치른다).
    document.addEventListener('click', (e) => {
      if (!(e.target && e.target.closest && e.target.closest('#left-btn-about'))) return;
      setTimeout(() => {
        const panel = document.getElementById('about-menu-panel');
        if (panel && panel.firstElementChild) addNotice(panel.firstElementChild);
      }, 300);
    }, true);
  }

  // ---- 3. 문서의 링크 ------------------------------------------------------------------------------------------------------------
  // 편집기는 문서의 링크를 window.open(주소)로 연다(문서 쪽 onHyperlinkClick — 보기 모드는 누르면, 편집 모드는 링크의 풍선에서). 이 앱에는 새
  // 창도 인터넷도 없어 아무 일도 없었다. 웹 주소면 바깥 쪽이 앱에 물어 브라우저 앱으로 넘기고, 그 밖(빈 창·이 출처·mailto
  // 따위)은 열지 않는다. 열지 않은 창은 null — 편집기는 그 값을 보고 물러난다(newDocumentPage && …).
  window.open = (target) => {
    const u = target == null ? '' : String(target);
    try {
      const url = new URL(u, location.href);
      if ((url.protocol === 'http:' || url.protocol === 'https:') && url.origin !== location.origin && typeof outer.openLink === 'function') {
        outer.openLink(url.href);
      } else {
        log('새 창을 열지 않았다: ' + url.protocol);
      }
    } catch (e) {
      log('새 창: ' + e);
    }
    return null;
  };

  if (!editing) return; // 보기 모드 — sdkjs 가 입력칸을 읽기 전용으로 둔다(자판이 오르지 않는다). 붙일 곳도 없다.

  // ---- 1. 자판 --------------------------------------------------------------------------------------------------------------
  const vk = navigator.virtualKeyboard;
  const manual = (ta) => {
    if (ta && vk && ta.getAttribute('virtualkeyboardpolicy') !== 'manual') ta.setAttribute('virtualkeyboardpolicy', 'manual');
  };
  // sdkjs 가 입력칸을 만들자마자(초점을 주기 전에) 수동으로 — 만든 뒤에는 그만 본다. 초점이 갈 때도 한 번 더 본다(다시 만들었을 때).
  if (!input()) {
    const watcher = new MutationObserver(() => {
      const ta = input();
      if (!ta) return;
      manual(ta);
      watcher.disconnect();
    });
    watcher.observe(document.documentElement, { childList: true, subtree: true });
  } else {
    manual(input());
  }

  let shown = false;
  const button = document.createElement('div');
  button.id = 'iroiro-kbd';
  button.setAttribute('role', 'button');
  button.setAttribute('aria-label', '자판');
  button.setAttribute('aria-pressed', 'false');
  button.title = '자판 보이기·숨기기';
  button.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" focusable="false">' +
    '<rect x="2.5" y="6" width="19" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.6"/>' +
    '<path d="M6 10h1M9.5 10h1M13 10h1M16.5 10h1.5M6 13.5h1M9.5 13.5h5M16.5 13.5h1.5" stroke="currentColor" stroke-width="1.6" ' +
    'stroke-linecap="round"/></svg>';
  const setShown = (v) => {
    shown = v;
    button.setAttribute('aria-pressed', String(v));
  };
  inner.ime = setShown;
  // 누를 때 입력칸의 초점을 뺏지 않는다(뺏기면 자판을 띄울 곳이 없다).
  button.addEventListener('pointerdown', (e) => e.preventDefault());
  button.addEventListener('mousedown', (e) => e.preventDefault());
  button.addEventListener('click', (e) => {
    e.stopPropagation();
    const ta = input();
    if (!ta || !vk) return;
    if (shown) {
      vk.hide();
      return;
    }
    manual(ta);
    if (document.activeElement !== ta) ta.focus({ preventScroll: true });
    vk.show();
  });

  // 다른 입력칸(대화상자·수식 입력줄·글꼴 상자)은 치려고 누른 것이라 자판이 저절로 올라온다. 문서 입력칸으로 돌아오면 그 전 모양으로 —
  // 문서로 돌아와서도 남으면 '자판은 단추로만' 이 깨진다(rhwp 에서 '다른 이름으로 저장' 뒤에 남는 것을 봤다).
  {
    let before = null; // 문서 입력칸을 떠날 때 자판이 보였는가
    const typing = (el) => el && el !== input() && (el.tagName === 'TEXTAREA' || el.isContentEditable ||
      (el.tagName === 'INPUT' && !/^(button|checkbox|radio|range|color|file|submit|reset|image|hidden)$/i.test(el.type)));
    document.addEventListener('focusin', (e) => {
      const t = e.target;
      if (t === input()) {
        manual(t);
        if (before === false && shown) {
          setTimeout(() => { if (shown && document.activeElement === input()) vk && vk.hide(); }, 0);
        }
        before = null;
      } else if (typing(t) && before === null) {
        before = shown;
      }
    }, true);
  }

  // 상태 표시줄이 그려지면 오른쪽 끝에 단추를 붙인다(편집기가 앱을 다 읽은 뒤 그린다).
  {
    let tries = 0;
    const timer = setInterval(() => {
      const bar = document.getElementById('statusbar');
      if (bar && bar.firstElementChild) {
        clearInterval(timer);
        bar.appendChild(button);
        document.body.classList.add('iroiro-has-kbd');
      } else if (++tries > 600) {
        clearInterval(timer);
        log('상태 표시줄을 찾지 못해 자판 단추를 붙이지 못했다');
      }
    }, 200);
  }

  // ---- 2. 붙이기 ------------------------------------------------------------------------------------------------------------
  {
    const execCommand = document.execCommand.bind(document);
    document.execCommand = (command, showUi, value) => {
      if (String(command).toLowerCase() === 'paste') {
        outer.send({ op: 'paste' });
        return true; // 편집기가 '편집기 안에서 복사한 것' 으로 물러나지 않게 — 진짜 붙이기는 앱이 읽어 준 뒤에 온다.
      }
      return execCommand(command, showUi, value);
    };
    inner.paste = (text, html) => {
      if (!text && !html) return;
      const ta = input();
      if (!ta) return;
      if (document.activeElement !== ta) ta.focus({ preventScroll: true });
      if (pasteInternal(text)) return;
      const data = new DataTransfer();
      if (text) data.setData('text/plain', text);
      if (html) data.setData('text/html', html);
      ta.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
    };
  }

  /**
   * 편집기 안에서 복사한 것을 붙이면 편집기의 내부 사본(LastCopyBinary — 글·HTML·내부 형식)으로 붙인다 — WebView 의 클립보드 쓰기는 글만 남겨
   * (2026-10-09 기기: 앱이 읽은 클립보드가 text/plain 하나) 서식·표·그림을 잃는다. 클립보드의 글이 그 사본의 글과 같을 때만(다른 앱에서 그 사이에
   * 복사했으면 클립보드 쪽을 붙인다). sdkjs 의 '붙여 넣기' 가 execCommand 에 실패했을 때 하는 일과 같다(Button_Paste).
   */
  function pasteInternal(text) {
    const C = window.AscCommon;
    const cb = C && C.g_clipboardBase;
    const F = C && C.c_oAscClipboardDataFormat;
    const last = cb && cb.LastCopyBinary;
    const api = window.editor || window.editorCell;
    if (!Array.isArray(last) || !F || !api || typeof api.asc_PasteData !== 'function') return false;
    const t = last.find((x) => x && x.type === F.Text);
    const i = last.find((x) => x && x.type === F.Internal);
    const norm = (s) => String(s == null ? '' : s).replace(/\r\n?/g, '\n').trim();
    if (!t || !i || !text || norm(t.data) !== norm(text)) return false;
    try {
      api.asc_PasteData(F.Internal, i.data, null, t.data);
      return true;
    } catch (e) {
      log('내부 사본으로 붙이지 못했다: ' + e);
      return false;
    }
  }
})();
