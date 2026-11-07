package io.github.donggi.iroirobrowser.browser

import java.net.URI
import java.net.URISyntaxException

/**
 * 뒤에서 재생 잇기 — 유튜브를 앱·탭이 뒤에 있거나 화면이 꺼져도 잇는다. 이 파일은 판단(화면 밖의 계산 — 시험한다)이다. WebView 를 보이는 채로 두는 것은 [PageWebView.keepWindowVisible]·[PageWebView.backstaged],
 * 그 탭을 고르고 회를 여닫는 것은 BrowserActivity, 알림·잠금 화면·이어폰 단추는 [BackgroundPlayService] 다.
 *
 * **회(session)** — 뒤에서 재생을 잇는 탭 하나와 그 탭의 소리가 멈춘 때. 앱이 뒤로 가거나 그 탭을 떠나는 순간 소리가 나는 유튜브가 재생 중이면
 * 연다. 소리가 멈춰도 [PAUSE_GRACE_MS] 동안은 이어서 틀 수 있게 남기고, 그 뒤에 닫는다(그 탭은 여느 뒤의 탭으로 돌아간다).
 */
object BackgroundPlay {

    /** 소리가 멈춘 뒤 회를 남겨 두는 시간 — 알림·잠금 화면·이어폰 단추로 다시 튼다. 그동안 쪽은 보이는 채라 자원을 쓴다. */
    const val PAUSE_GRACE_MS = 10 * 60_000L

    /**
     * 그 탭이 뒤에서도 재생을 잇는가 — 주 문서([url])가 유튜브(https)이고 소리가 나는 미디어가 재생 중이다([state]). 광고가 나오는 중이면
     * ([adShowing] — 광고 가리기가 음소거했다) 영상이 재생 중인 것으로 족하다 — 광고는 사람이 소리와 함께 튼 재생에만 붙는다(검토).
     */
    fun wanted(url: String, state: PlayingMedia.State, adShowing: Boolean = false): Boolean =
        (state.audible || (adShowing && state.video)) && isYouTube(url)

    /** 주 문서가 유튜브인가 — 광고 가리기와 같은 출처 판단([YtGuard.isYouTubeOrigin]). 주소를 읽지 못하면 아니다. */
    fun isYouTube(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return false
        }
        return YtGuard.isYouTubeOrigin(uri.scheme, uri.host, uri.port)
    }

    /** 알림·잠금 화면의 제목 — 쪽 제목에서 유튜브가 붙이는 꼬리(' - YouTube')를 뗀다. 비면 [fallback]. */
    fun titleOf(pageTitle: String, fallback: String): String =
        cleanText(pageTitle.trim().removeSuffix(YOUTUBE_SUFFIX)).ifEmpty { fallback }

    /**
     * 쪽이 정한 글(화면의 영상 제목·쪽 제목)을 알림에 싣기 전에 다듬는다 — 제어 문자는 빈칸으로, 방향 제어 문자(U+202E 등)는 빼고
     * (남의 글이 알림의 뒤 글을 뒤집지 못하게), 빈칸을 하나로, [MAX_TEXT] 코드 포인트까지.
     */
    fun cleanText(text: String, max: Int = MAX_TEXT): String {
        val out = StringBuilder()
        var count = 0
        var i = 0
        while (i < text.length && count < max) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp in BIDI_CONTROLS -> Unit
                Character.isISOControl(cp) || Character.isWhitespace(cp) -> if (out.isNotEmpty() && out.last() != ' ') {
                    out.append(' ')
                    count++
                }
                else -> {
                    out.appendCodePoint(cp)
                    count++
                }
            }
        }
        return out.toString().trim()
    }

    /** 알림에 싣는 글의 길이(코드 포인트). */
    const val MAX_TEXT = 200

    /** 방향 제어 문자 — LRM·RLM·ALM, 내장·덮어쓰기(LRE…RLO), 고립(LRI…PDI). */
    private val BIDI_CONTROLS = setOf(0x200E, 0x200F, 0x061C) + (0x202A..0x202E) + (0x2066..0x2069)

    /**
     * 서비스(알림)를 세워 둘지 — 회가 있고([alive]), 앱이 뒤에 있거나([foreground] 가 아니다) 그 탭이 지금 탭이 아니다([isCurrent]). 앞에서
     * 그 탭을 보고 있으면 알림이 필요 없다.
     */
    fun serviceWanted(alive: Boolean, foreground: Boolean, isCurrent: Boolean): Boolean = alive && (!foreground || !isCurrent)

    /** 회를 닫을 때인가 — 소리가 멈춘 때([stoppedAt], 재생 중이면 null)에서 [PAUSE_GRACE_MS] 가 지났다. 시계가 거꾸로 가면 아직이다. */
    fun expired(stoppedAt: Long?, now: Long): Boolean = stoppedAt != null && now - stoppedAt >= PAUSE_GRACE_MS

    private const val YOUTUBE_SUFFIX = "- YouTube"

    /**
     * **유튜브가 늘 '보임' 을 읽게 하는 스크립트** — 유튜브의 주 틀, **쪽의 세계**, 문서 시작(사용자 결정 — 브레이브의
     * 방식, CLAUDE.md '브레이브와 견줌'). 유튜브 모바일 재생기는 `visibilityState` 가 'visible' 이 아니면(`visibilitychange` 와 `timeupdate` 마다
     * 본다) 재생기를 세운다(`stopVideo` — 프리미엄이 아니면). 뒤에서 재생을 잇는 동안 쪽은 크로미움에 '보임' 이지만, 다른 길로 잠깐이라도 숨으면
     * (화면을 다시 짓기·시스템의 창 전환) 유튜브가 그 틈에 멈춘다. 이 스크립트가 있으면 크로미움이 숨은 동안 영상을 멈췄다가 보이면 저절로 다시
     * 틀고, 유튜브는 숨은 줄 모른다.
     *
     * 문서의 `visibilityState`·`hidden`(크로미움에 있으면 접두어 붙은 둘도)을 그 문서 객체에서 '보임' 으로 덮고, `visibilitychange` 를 창의 포착
     * 단계에서 멈춘다 — 쪽의 스크립트보다 먼저 건 듣개라 쪽의 듣개(창·문서 모두)에 닿지 않는다. 쪽의 함수(`addEventListener` 등)는 감싸지 않는다.
     * 앱의 실행 세계는 문서 객체가 따로라 실제 값을 읽는다(그 세계의 스크립트는 이 사건을 듣지 않는다). 퍼가기 틀에서는 아무것도 하지 않는다.
     * 역슬래시·달러를 쓰지 않는다(코틀린의 날 글자열).
     */
    const val VISIBILITY_SCRIPT = """(function () {
  if (window.top !== window) return;
  var d = document;
  function keep(name, value) {
    if (!(name in d)) return;
    try {
      Object.defineProperty(d, name, { configurable: true, enumerable: false, get: function () { return value; } });
    } catch (e) {}
  }
  keep('visibilityState', 'visible');
  keep('hidden', false);
  keep('webkitVisibilityState', 'visible');
  keep('webkitHidden', false);
  function stop(e) { e.stopImmediatePropagation(); }
  window.addEventListener('visibilitychange', stop, true);
  window.addEventListener('webkitvisibilitychange', stop, true);
})();"""

    /**
     * **뒤에서 재생을 잇는 동안 영상 요소를 숨기는 스크립트**(CLAUDE.md 'CPU — 크롬·브레이브와 견줌').
     * 화면 밖에 그리는 동안([OffscreenDraw]) 틀마다의 **실행 세계**에서 돌린다(`JavaScriptReplyProxy.executeJavaScript` — [PageBridge.hideVideos]).
     * 숨은 영상은 크로미움이 화면에 내지 않고(영상 층의 제출이 서고 크로미움이 프레임을 스스로 흘려보낸다 — 버린 프레임으로 세지 않는다) WebView 가
     * 다시 그리기를 청하지 않아, 화면 밖 그리기가 거의 선다. 소리와 해독은 그대로다.
     *
     * [HIDE_ID_PLACEHOLDER] 는 이 프로세스의 무작위 이름(스타일 요소의 id), [HIDE_FLAG_PLACEHOLDER] 는 `true`(숨긴다)·`false`(되돌린다). 그 틀의
     * 영상 수를 돌려준다(되돌릴 때는 0). 쪽의 함수는 부르지 않는다(DOM 만 — 쪽도 그 스타일 요소를 볼 수는 있다). 역슬래시·달러를 쓰지 않는다.
     */
    const val HIDE_VIDEO_SCRIPT = """(function () {
  var d = document;
  var s = d.getElementById('__HIDE_ID__');
  if (__HIDE__) {
    if (!s) {
      s = d.createElement('style');
      s.id = '__HIDE_ID__';
      s.textContent = 'video{visibility:hidden!important}';
      (d.head || d.documentElement).appendChild(s);
    }
    return d.getElementsByTagName('video').length;
  }
  if (s) s.remove();
  return 0;
})()"""

    const val HIDE_ID_PLACEHOLDER = "__HIDE_ID__"
    const val HIDE_FLAG_PLACEHOLDER = "__HIDE__"

    /** [HIDE_VIDEO_SCRIPT] 를 채운다 — [id] 는 영문·숫자만(스크립트의 글자열에 그대로 들어간다). */
    fun hideVideoScript(id: String, hide: Boolean): String {
        require(id.isNotEmpty() && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) { "id" }
        return HIDE_VIDEO_SCRIPT.replace(HIDE_ID_PLACEHOLDER, id).replace(HIDE_FLAG_PLACEHOLDER, hide.toString())
    }
}
