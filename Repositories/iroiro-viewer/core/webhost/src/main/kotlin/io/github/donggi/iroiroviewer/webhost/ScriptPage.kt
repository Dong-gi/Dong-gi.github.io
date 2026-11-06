package io.github.donggi.iroiroviewer.webhost

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

/**
 * 스크립트를 켠 쪽([LockedWebView] 의 `scripts`)이 더 갖는 것 — 쪽이 띄우는 대화상자, 쪽의 콘솔, 그리고 쪽이 기기에 남길 수 있는 것을
 * 막는 장치. 켜는 곳은 HTML 미리보기의 '스크립트 실행' 하나다.
 */
internal object ScriptPage {

    /** 대화상자에 싣는 글의 상한(글자). 넘는 것은 자른다 — 쪽이 수 MB 짜리 글로 대화상자를 띄울 수 있다. */
    const val MAX_MESSAGE_CHARS = 4_000

    /**
     * 앱 전체에 한 번 거는 것 둘. 둘 다 **WebView 전체에 걸리는 설정**이라 쪽마다가 아니다 — 그리고 스크립트가 꺼진 쪽은 둘 다 쓸
     * 일이 없다(쿠키를 주는 서버가 없고, 서비스 워커는 스크립트가 등록한다).
     *
     * * **쿠키를 받지 않는다.** 쪽이 `document.cookie` 로 적은 것은 기기에 남아 같은 출처의 다음 쪽이 읽는다(출처는 쪽마다 다르지만
     *   겹쳐 막는다).
     * * **서비스 워커의 요청은 모두 404 다.** 서비스 워커는 쪽이 닫힌 뒤에도 남아 그 출처의 요청을 가로챈다. 출처를 쪽마다 새로
     *   지으므로(`WebHost.isolatedHost`) 그 출처로 다시 올 쪽은 없지만, 워커가 스크립트를 받아 오지 못하게 한 겹 더 둔다.
     * * **앞 프로세스가 남긴 것을 지운다**([wipe]). 지우기는 쪽을 깨끗이 닫을 때 하는데, 쪽이 열린 채 프로세스가 죽으면(크래시·메모리·
     *   강제 종료) 풀린 메일이 IndexedDB 에 남은 채 디스크에 머문다 — 읽을 출처는 다시 오지 않지만 평문이다.
     */
    fun lockOnce() {
        if (locked) return
        wipe()
        CookieManager.getInstance().setAcceptCookie(false)
        val controller = ServiceWorkerController.getInstance()
        controller.serviceWorkerWebSettings.apply {
            allowContentAccess = false
            allowFileAccess = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        controller.setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = notFound()
        })
        locked = true
    }

    private var locked = false

    /**
     * 스크립트를 켠 쪽을 닫은 뒤(그리고 프로세스마다 처음 켤 때). 쪽이 남겼을 수 있는 출처별 저장소와 쿠키를 지운다. 이 앱의 다른 쪽은
     * 저장소를 쓰지 않으므로(잠긴 쪽은 DOM 저장소를 끄고 스크립트가 없다) **전부 지워도 잃는 것이 없다.** IndexedDB 가 비는 것은 두
     * 기기에서 확인했다 — 캐시 저장소·서비스 워커 등록까지 지우는지는 확인하지 않았다(그쪽은 출처가 다시 오지 않는 것에 기댄다).
     */
    fun wipe() {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
    }

    /**
     * 스크립트를 켠 쪽의 응답마다 붙이는 머리(2026-10-05 — 이 앱이 그때 `INTERNET` 을 선언했다. 2026-10-09 에 다시 걷었다).
     *
     * `Content-Security-Policy: connect-src 'self' data: blob:` — **웹소켓**·`fetch`·`XMLHttpRequest`·`EventSource`·`sendBeacon` 을 쪽의
     * 출처로 묶는다. 웹소켓은 가로채기를 지나지 않는다. 같은 폴더의 파일(우리 출처)은 그대로 읽힌다. 쪽이 스스로 적은 CSP 는 더 좁힐
     * 수만 있다(넓히지 못한다). **지금 밖으로 나가는 길을 막는 것은 `INTERNET` 이 없다는 것(OS)이다** — 이 머리는 겹쳐 둔 막음이다.
     *
     * **`INTERNET` 이 있던 동안 실험실에서 잰 것**(에뮬레이터, 브라우저 실험실의 DNS 기록기·HTTP·웹소켓·STUN 수신기): 그림·스타일·틀·`fetch`·
     * XHR·웹소켓(이름·IP)·EventSource·`sendBeacon`·폼·이동·새 창은 **하나도 닿지 않았다.** **막지 못한 것 둘** — 이름 미리 찾기
     * (`<link rel=dns-prefetch>`·`preconnect` — 쪽에 적힌 것도 스크립트가 넣은 것도 기기가 이름을 찾는다)와 **WebRTC**(STUN 으로 UDP 가
     * 나간다). 둘 다 WebView 에 끄는 설정이 없다(`X-DNS-Prefetch-Control: off` 머리도 붙여 봤지만 듣지 않았다). `INTERNET` 을 다시
     * 선언하는 날 그 둘부터 다시 본다(CLAUDE.md 의 'INTERNET 을 다시 걷었다').
     */
    val OFFLINE_HEADERS: Map<String, String> = mapOf(
        "Content-Security-Policy" to "connect-src 'self' data: blob:",
        // 비동기 클립보드(`navigator.clipboard`)의 쓰기·읽기를 쪽과 그 안의 모든 틀에서 끈다 — 권한 정책은 아래 틀로 물려 내려가고 쪽이
        // 넓히지 못한다. `execCommand('copy')`·`copy` 사건은 이 정책이 막지 않는다 — 그쪽은 [CLIPBOARD_GUARD] 다.
        "Permissions-Policy" to "clipboard-write=(), clipboard-read=()",
    )

    /**
     * **쪽이 클립보드에 쓰지 못하게 하는 문서 시작 스크립트**(2026-10-16 보안 점검 — '스크립트를 켠 HTML 미리보기가 클립보드로 같은 폴더의 내용을
     * 내보낼 수 있다'). 스크립트를 켠 쪽은 같은 폴더의 글·자료·그림을 이름으로 읽는다. 사용자가 무엇이든 누르는 순간(사용자 동작) 쪽은
     * `document.execCommand('copy')`·`navigator.clipboard.write…` 로 그것을 클립보드에 쓸 수 있었고, 다른 앱이 넘긴 HTML 이면 뷰어를 닫을 때
     * 앞으로 오는 그 앱이 클립보드를 읽는다. 그래서 **쪽의 스크립트보다 먼저**, **모든 틀에서**:
     *
     * - `Document.prototype.execCommand` — `copy`·`cut` 이면 아무것도 하지 않고 거짓(명령 글자는 한 번만 글로 바꿔 넘긴다 — 두 번 바꾸면
     *   `toString` 이 다른 답을 낼 수 있다).
     * - `Clipboard.prototype.write`·`writeText` — 거절된 약속(권한 정책이 플랫폼에서 한 번 더 막는다 — [OFFLINE_HEADERS]).
     * - `copy`·`cut` 사건 — 창의 포착 단계에서 **맨 먼저 건 듣개**가 멈춘다(쪽의 듣개·`oncopy` 에 닿지 않는다 — 사용자가 고른 글은 그대로
     *   복사된다). 그 사건의 `clipboardData` 는 null 이다(`setData` 로 바꿔치지 못한다).
     *
     * **쪽이 되돌리지 못한다.** 위 셋은 쓰기·지우기가 안 되는 속성으로 걸고, 고리가 부르는 함수(`Reflect.apply`·`toLowerCase`·사건의 멈춤·
     * `WeakSet` 의 두 함수·`Object.create`)는 쪽보다 먼저 쥔 사본을 쓴다 — 쪽이 나중에 내장 함수를 바꿔도 이 스크립트는 바뀐 것을 부르지
     * 않는다. 속성을 거는 설명 객체는 원형이 없는 객체다(쪽이 `Object.prototype` 에 심은 값이 끼지 않게).
     *
     * **틀마다.** 문서 시작 스크립트는 같은 출처의 **빈 틀**(about:blank — 스크립트가 만든 iframe)에는 들어가지 않는다 — 쪽이 그런 틀을
     * 만들어 그 틀의 손대지 않은 `execCommand` 를 쓸 수 있다. 그래서 틀이 생길 수 있는 모든 길의 바로 뒤에서 틀 나무를 훑어 같은 출처의 창마다
     * 위 셋과 같은 고리를 건다 — Web(iroiro-browser)의 WebRTC 가드(2판 — 15길을 실험으로 막았다)의 갈고리 목록을 그대로 쓴다: DOM 에 넣는
     * 메서드와 `innerHTML`·`outerHTML`, `document.write`, `execCommand`, `Range.insertNode`, `contentWindow`·`contentDocument`, 틀의 `load`
     * (문서의 포착 단계), 파서가 넣은 틀(MutationObserver), `window.open`. 다른 출처·data:·샌드박스 틀에는 문서 시작 스크립트가 들어간다
     * (`addDocumentStartJavaScript` 의 규칙 `*`).
     *
     * **남는 것.** 그 밖의 새 길, 쪽이 갈고리를 알아챌 수 있다는 것(`toString`), 그리고 **사용자가 스스로 고른 글의 복사** — 쪽이 보이지
     * 않게 심은 글(글자 크기 0 등)이 고른 범위 안에 있으면 함께 복사된다(막으려면 고르기를 끄는 수밖에 없다 — 끄지 않았다). 코틀린 글자열과
     * 셸이 먹지 않게 역슬래시·달러 기호를 쓰지 않는다(시험이 본다).
     */
    const val CLIPBOARD_GUARD = """
(function () {
  'use strict';
  var apply = Reflect.apply;
  var create = Object.create;
  var define = Object.defineProperty;
  var describe = Object.getOwnPropertyDescriptor;
  var toText = String;
  var lowerCase = String.prototype.toLowerCase;
  var stopNow = Event.prototype.stopImmediatePropagation;
  var eventType = describe(Event.prototype, 'type').get;
  var PromiseCtor = Promise;
  var promiseReject = Promise.reject;
  var ErrorCtor = Error;
  var seen = new WeakSet();
  var seenHas = WeakSet.prototype.has;
  var seenAdd = WeakSet.prototype.add;
  var root = window;
  var NODE = ['appendChild', 'insertBefore', 'replaceChild'];
  var ELEMENT = ['append', 'prepend', 'before', 'after', 'replaceWith', 'replaceChildren', 'insertAdjacentElement',
    'insertAdjacentHTML', 'setHTMLUnsafe', 'moveBefore'];
  var DOCUMENT = ['write', 'writeln', 'append', 'prepend', 'replaceChildren'];
  var SHADOW = ['setHTMLUnsafe'];
  var RANGE = ['insertNode', 'surroundContents'];
  var MARKUP = ['innerHTML', 'outerHTML'];
  var SHADOW_MARKUP = ['innerHTML'];
  var FRAMES = ['HTMLIFrameElement', 'HTMLFrameElement', 'HTMLObjectElement'];
  var FRAME_GETTERS = ['contentWindow', 'contentDocument'];
  var CLIPBOARD = ['write', 'writeText'];
  function clipboardEvent(type) { return type === 'copy' || type === 'cut'; }
  function data(value, enumerable, locked) {
    var d = create(null);
    d.value = value;
    d.writable = !locked;
    d.enumerable = enumerable;
    d.configurable = !locked;
    return d;
  }
  function accessor(get, set, enumerable, locked) {
    var d = create(null);
    d.get = get;
    d.set = set;
    d.enumerable = enumerable;
    d.configurable = !locked;
    return d;
  }
  function stop(e) { apply(stopNow, e, []); }
  function guard(w) {
    var D = w.Document && w.Document.prototype;
    var exec = D && describe(D, 'execCommand');
    if (exec && typeof exec.value === 'function') {
      var run = exec.value;
      try {
        define(D, 'execCommand', data(function execCommand(command) {
          var c = toText(command);
          if (clipboardEvent(apply(lowerCase, c, []))) return false;
          arguments[0] = c;
          try { return apply(run, this, arguments); } finally { sweep(); }
        }, exec.enumerable, true));
      } catch (e) {}
    }
    var C = w.Clipboard && w.Clipboard.prototype;
    for (var i = 0; C && i < CLIPBOARD.length; i++) {
      var m = describe(C, CLIPBOARD[i]);
      if (!m) continue;
      try {
        define(C, CLIPBOARD[i], data(function () {
          return apply(promiseReject, PromiseCtor, [new ErrorCtor('clipboard')]);
        }, m.enumerable, true));
      } catch (e) {}
    }
    var E = w.ClipboardEvent && w.ClipboardEvent.prototype;
    var cd = E && describe(E, 'clipboardData');
    if (cd && typeof cd.get === 'function') {
      var read = cd.get;
      try {
        define(E, 'clipboardData', accessor(function () {
          if (clipboardEvent(apply(eventType, this, []))) return null;
          return apply(read, this, []);
        }, undefined, cd.enumerable, true));
      } catch (e) {}
    }
    try {
      w.addEventListener('copy', stop, true);
      w.addEventListener('cut', stop, true);
    } catch (e) {}
  }
  function wrapMethods(proto, names) {
    for (var i = 0; proto && i < names.length; i++) {
      var d = describe(proto, names[i]);
      if (!d || typeof d.value !== 'function') continue;
      (function (f, name, enumerable) {
        try {
          define(proto, name, data(function () { try { return apply(f, this, arguments); } finally { sweep(); } }, enumerable, false));
        } catch (e) {}
      })(d.value, names[i], d.enumerable);
    }
  }
  function wrapSetters(proto, names) {
    for (var i = 0; proto && i < names.length; i++) {
      var d = describe(proto, names[i]);
      if (!d || typeof d.set !== 'function') continue;
      (function (get, set, name, enumerable) {
        try {
          define(proto, name, accessor(get, function (v) { try { apply(set, this, [v]); } finally { sweep(); } }, enumerable, false));
        } catch (e) {}
      })(d.get, d.set, names[i], d.enumerable);
    }
  }
  function wrapGetters(proto, names) {
    for (var i = 0; proto && i < names.length; i++) {
      var d = describe(proto, names[i]);
      if (!d || typeof d.get !== 'function') continue;
      (function (get, set, name, enumerable) {
        try {
          define(proto, name, accessor(function () { sweep(); return apply(get, this, []); }, set, enumerable, false));
        } catch (e) {}
      })(d.get, d.set, names[i], d.enumerable);
    }
  }
  function hook(w) {
    wrapMethods(w.Node && w.Node.prototype, NODE);
    wrapMethods(w.Element && w.Element.prototype, ELEMENT);
    wrapMethods(w.Document && w.Document.prototype, DOCUMENT);
    wrapMethods(w.ShadowRoot && w.ShadowRoot.prototype, SHADOW);
    wrapMethods(w.Range && w.Range.prototype, RANGE);
    wrapSetters(w.Element && w.Element.prototype, MARKUP);
    wrapSetters(w.ShadowRoot && w.ShadowRoot.prototype, SHADOW_MARKUP);
    for (var i = 0; i < FRAMES.length; i++) {
      var c = w[FRAMES[i]];
      wrapGetters(c && c.prototype, FRAME_GETTERS);
    }
    try {
      var open = w.open;
      w.open = function () { var r = apply(open, this, arguments); sweep(); return r; };
    } catch (e) {}
    try { w.document.addEventListener('load', sweep, true); } catch (e) {}
    try { new w.MutationObserver(sweep).observe(w.document, { childList: true, subtree: true }); } catch (e) {}
  }
  function sweep() {
    var stack = create(null);
    var top = 0;
    stack[top++] = root;
    while (top > 0) {
      var w = stack[--top];
      var d;
      try { d = w.document; } catch (e) { continue; }
      try {
        if (!apply(seenHas, seen, [d])) {
          apply(seenAdd, seen, [d]);
          guard(w);
          hook(w);
        }
      } catch (e) {}
      var n = 0;
      try { n = w.length; } catch (e) {}
      for (var i = 0; i < n; i++) {
        try { stack[top++] = w[i]; } catch (e) {}
      }
    }
  }
  sweep();
})();
"""

    /**
     * 이 기기의 WebView 가 문서 시작 스크립트를 받는가 — [CLIPBOARD_GUARD] 를 걸 수 있는가. 받지 않으면 **스크립트를 켜지 않는다**
     * (`LockedWebView` 가 자바스크립트를 끈 채로 두고, 화면은 '스크립트 실행' 을 내지 않는다 — `WebHost.scriptsAllowed`).
     */
    fun clipboardGuardSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /**
     * [view] 에 [CLIPBOARD_GUARD] 를 건다 — **쪽을 싣기 전에**(그 뒤의 모든 문서·틀에 들어간다). 규칙 `*` 는 모든 출처다 — 이 WebView 는
     * 우리 쪽만 싣고, 쪽이 만든 data:·샌드박스 틀(출처가 없다)까지 들어가야 한다. 걸지 못했으면 거짓이다 — 부른 쪽은 스크립트를 켜지 않는다.
     */
    fun installClipboardGuard(view: WebView): Boolean {
        // lint 의 `RequiresFeature` 가 보는 꼴 그대로 — 같은 함수 안에서 묻는다.
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        return try {
            WebViewCompat.addDocumentStartJavaScript(view, CLIPBOARD_GUARD, setOf("*"))
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    /** 스크립트를 켠 쪽을 닫으며 렌더러를 끝낸 때(`elapsedRealtime`, ms). 0 이면 아직 없다. */
    @Volatile
    private var terminatedAt = 0L

    /**
     * 렌더러를 끝낸다고 적는다. 이 앱의 WebView 는 렌더러 하나를 나눠 쓰므로, 그때 이미 그 렌더러에 붙어 있던 **다음 쪽**도 함께 죽는다 —
     * 그 쪽은 그것을 보고 한 번 다시 선다([terminatedRecently], `LockedWebView` 의 `onRenderProcessGone`).
     */
    fun markTerminated() {
        terminatedAt = SystemClock.elapsedRealtime()
    }

    /** 우리가 방금(몇 초 안) 렌더러를 끝냈는가. */
    fun terminatedRecently(): Boolean = terminatedAt != 0L && SystemClock.elapsedRealtime() - terminatedAt < REVIVE_WINDOW_MS

    private const val REVIVE_WINDOW_MS = 5_000L

    /** 서비스 워커가 받아 오려는 스크립트인가(명세가 그 요청에 `Service-Worker: script` 머리를 단다). 그런 요청에는 404 를 준다. */
    fun isServiceWorkerFetch(request: WebResourceRequest): Boolean = isServiceWorkerFetch(request.requestHeaders)

    internal fun isServiceWorkerFetch(headers: Map<String, String>?): Boolean =
        headers?.keys?.any { it.equals("Service-Worker", ignoreCase = true) } == true

    /**
     * 쪽 전체를 다른 곳으로 바꾸려는 요청인가. **POST 로 가는 길은 `shouldOverrideUrlLoading` 을 지나지 않는다**(문서에 적힌 동작) —
     * `<form method=post action=…>` 이 쪽을 같은 폴더의 다른 HTML 이나(그 쪽의 스크립트가 돈다) 빈 404 로 바꿨다. 그런
     * 요청에는 204 를 준다 — 브라우저는 204 를 받은 이동을 거두고 지금 쪽에 머문다. 쪽 자신을 다시 읽는 것은 막지 않는다.
     */
    fun refusesNavigation(isForMainFrame: Boolean, requestPath: String?, pagePath: String?): Boolean =
        isForMainFrame && requestPath != pagePath

    /** 이동을 거두게 하는 답(204). 몸이 없다. */
    fun noContent(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "No Content",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )
}

/**
 * 부수는 동안의 클라이언트. **가로채기를 놓지 않는다** — 맨 `WebViewClient` 로 갈아 끼우면 그 짧은 사이에 쪽이 내보내는 요청(`pagehide`·
 * `unload` 의 `sendBeacon`·keepalive `fetch`)이 진짜 네트워크로 갈 수 있다(지금은 `INTERNET` 이 없어 OS 가 막지만
 * 겹쳐 둔다. 이 앱이 `INTERNET` 을 선언했던 2026-10-05 ~ 10-09 에는 이것이 막았다). 그리고
 * 렌더러가 그 사이에 죽어도 **앱을 끝내지 않는다**(맨 클라이언트의 `onRenderProcessGone` 은 false 다).
 */
@SuppressLint("MissingOnRenderProcessGone")
// `onRenderProcessGone` 은 아래에 있다 — androidx.webkit 의 lint 가 내는 거짓 경고(`LockedWebView` 의 같은 줄).
internal object TeardownClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = notFound()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true
}

/**
 * 쪽이 띄우는 대화상자를 **세는** 문. 대화상자를 끝없이 띄우는 쪽에서 사용자가 빠져나올 길이다 — 두 번째부터 '더 띄우지 않게' 를 단다
 * (데스크톱 크롬과 같다). 고르면 그 WebView 가 사는 동안의 대화상자를 모두 거절한다.
 *
 * **쪽을 다시 읽어도 세는 것을 되돌리지 않는다.** 되돌리면 대화상자를 띄우고 스스로 다시 읽는 쪽이 문을 매번 새로 연다. 이 WebView 는
 * 쪽 하나만 보이므로(다른 쪽으로는 가지 않는다) 'WebView 가 사는 동안' 이 곧 '이 쪽' 이다.
 */
internal class JsDialogGate {
    var shown = 0
        private set
    var stopped = false
        private set

    /** 띄워도 되는가. 되면 센다. */
    fun admit(): Boolean {
        if (stopped) return false
        shown++
        return true
    }

    /** 지금 띄우는 대화상자에 '더 띄우지 않게' 를 다는가 — 두 번째부터. */
    val offersStop: Boolean get() = shown >= 2

    fun stop() {
        stopped = true
    }
}

/** 쪽이 띄운 대화상자 하나. [result] 에 **반드시 한 번** 답한다 — 답하기 전까지 쪽의 스크립트가 멈춰 있다. */
internal class JsDialog(
    val kind: Kind,
    val message: String,
    val defaultValue: String?,
    val result: JsResult,
    val offersStop: Boolean,
) {
    enum class Kind { ALERT, CONFIRM, PROMPT }
}

/**
 * 스크립트를 켠 쪽의 크롬 클라이언트. 대화상자는 우리가 그린다([JsDialogView]) — 우리 문구·글자 상한·'더 띄우지 않게' 를 쥐려는
 * 것이다. (WebView 의 기본 대화상자는 제목에 쪽의 주소 — 우리의 가짜 호스트 — 를 적고, 크롬 클라이언트가 아예 없으면 대화상자를 소리 없이
 * 거절한다고 알려져 있다. 둘 다 이 앱에서 확인하지는 않았다.)
 *
 * 콘솔은 [console] 로만 흘린다(디버그 기록). **`true` 를 돌려준다** — 받지 않으면 WebView 가 쪽의 콘솔을 logcat 에 스스로 적는다고
 * 알려져 있다(릴리스에서도 — 이 앱에서 확인하지는 않았다). 쪽의 글이 거기 실릴 수 있다.
 */
internal class ScriptChrome(
    private val gate: JsDialogGate,
    private val show: (JsDialog) -> Unit,
    private val console: () -> ((String) -> Unit)?,
) : WebChromeClient() {

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
        open(JsDialog.Kind.ALERT, message, null, result)

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
        open(JsDialog.Kind.CONFIRM, message, null, result)

    override fun onJsPrompt(
        view: WebView,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult,
    ): Boolean = open(JsDialog.Kind.PROMPT, message, defaultValue, result)

    /** 떠나도 되는가를 묻지 않는다 — 이 WebView 는 쪽을 떠나지 않고, 닫는 것은 사용자가 한 일이다. */
    override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.confirm()
        return true
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        console()?.invoke("${message.messageLevel()} ${message.lineNumber()}: ${message.message().take(CONSOLE_CHARS)}")
        return true
    }

    /**
     * 전체 화면을 **들어주지 않는다.** 들어주려면 쪽이 건넨 뷰를 우리 창에 얹어야 하는데, 그것은 문서가 앱의 화면 전체를 차지하게 하는
     * 일이다. 받지도 거두지도 않으면 쪽이 전체 화면에 들어간 줄 알고 비어 보일 수 있어 곧바로 '닫혔다' 를 돌려준다.
     */
    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        callback.onCustomViewHidden()
    }

    private fun open(kind: JsDialog.Kind, message: String?, defaultValue: String?, result: JsResult): Boolean {
        if (!gate.admit()) {
            result.cancel()
            return true
        }
        show(
            JsDialog(
                kind = kind,
                message = message.orEmpty().take(ScriptPage.MAX_MESSAGE_CHARS),
                defaultValue = defaultValue?.take(ScriptPage.MAX_MESSAGE_CHARS),
                result = result,
                offersStop = gate.offersStop,
            ),
        )
        return true
    }

    private companion object {
        const val CONSOLE_CHARS = 160
    }
}

/**
 * 쪽이 띄운 대화상자. 제목이 **'문서가 띄운'** 것임을 말한다 — 이 앱의 창과 섞이지 않게(문서가 앱인 척 묻는 것을 막는다). 글은
 * 글자 그대로 보인다(HTML 이 아니다). 뒤로 가기·바깥을 누르는 것은 '취소' 다(알림창이면 확인과 같다).
 */
@Composable
internal fun JsDialogView(dialog: JsDialog, onStop: () -> Unit, onDone: () -> Unit) {
    var stop by remember(dialog) { mutableStateOf(false) }
    var input by remember(dialog) { mutableStateOf(dialog.defaultValue.orEmpty()) }
    fun answer(ok: Boolean) {
        if (stop) onStop()
        when {
            !ok -> dialog.result.cancel()
            dialog.kind == JsDialog.Kind.PROMPT -> (dialog.result as JsPromptResult).confirm(input)
            else -> dialog.result.confirm()
        }
        onDone()
    }
    AlertDialog(
        onDismissRequest = { answer(ok = dialog.kind == JsDialog.Kind.ALERT) },
        title = { Text(stringResource(R.string.webhost_js_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(dialog.message, style = MaterialTheme.typography.bodyMedium)
                if (dialog.kind == JsDialog.Kind.PROMPT) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        // 쪽이 묻는 것은 생년월일 같은 값일 수 있다 — 키보드가 고쳐 쓰거나 배우게 두지 않는다.
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (dialog.offersStop) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = stop, role = Role.Checkbox, onValueChange = { stop = it })
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = stop, onCheckedChange = null)
                        Text(
                            stringResource(R.string.webhost_js_stop),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { answer(ok = true) }) { Text(stringResource(R.string.webhost_js_ok)) }
        },
        dismissButton = if (dialog.kind == JsDialog.Kind.ALERT) {
            null
        } else {
            { TextButton(onClick = { answer(ok = false) }) { Text(stringResource(R.string.webhost_js_cancel)) } }
        },
    )
}
