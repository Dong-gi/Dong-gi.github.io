package io.github.donggi.iroirobrowser.browser

import android.os.Bundle
import android.webkit.WebView
import io.github.donggi.iroirobrowser.core.adblock.AdBlocker
import io.github.donggi.iroirobrowser.core.media.FoundMediaLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * 탭 하나. WebView 가 없을 수 있다([webView] = null) — 렌더러가 죽었거나([crashed]), 살아 있는 탭 수를 넘어 접었거나([frozen]), 세션에서
 * 되살린 뒤 아직 고르지 않았다. 고르면 새로 만든다.
 */
class Tab(val id: Long, var webView: WebView?) {
    var title: String = ""

    /** 주소창에 보이는 주소 — 여는 중인 것까지. */
    var url: String = ""

    /** 지금 화면에 있는 쪽의 주소(탐색이 커밋된 것). 내려받기로 끝난 탐색은 커밋되지 않는다 — 그때 주소창을 이것으로 되돌린다. */
    var committedUrl: String = ""

    /** 이 탭이 마지막으로 청한 주 문서의 주소 — 넘겨주기를 따라간 것. 인증서 오류가 주 문서의 것인지 가린다(`onSslError`). */
    var mainUrl: String = ""
    var progress: Int = 100
    val loading: Boolean get() = progress < 100

    /** `window.open`·`target=_blank`·'새 탭에서 열기' 로 이 탭을 연 탭 — 뒤로 갈 곳이 없으면 이 탭을 닫고 그리로 돌아간다. */
    var openerId: Long? = null

    /** 렌더러가 죽어 화면을 잃었다. */
    var crashed: Boolean = false

    /** 지금 여는 쪽은 주소창에서 쳐서(또는 제안을 골라) 연 것 — 다 열면 방문 기록에 '친 곳' 으로 적는다(인라인 완성이 그런 곳을 먼저 채운다). */
    var typedNavigation: Boolean = false

    /**
     * 오류로 끝난 주 문서의 주소(연결 오류·중계가 막음) — 그 주소의 '다 열었다' 를 방문 기록에 적지 않는다. WebView 는 오류 응답을 쪽의
     * 시작보다 **먼저** 알리므로(중계의 502) 시작에서 지우지 않고, 그 주소를 다 열었다는 알림에서 쓰고 지운다.
     */
    var failedUrl: String? = null

    /** 접어 둔 탭의 뒤로·앞으로 기록(`WebView.saveState`) — 메모리에만 둔다. 고르면 이것으로 되살린다. */
    var frozen: Bundle? = null

    /** '데스크톱 사이트'(탭마다 — 일반화된 데스크톱 UA, [WebViews.applyUserAgent]). 세션에 남는다. */
    var desktop: Boolean = false

    /** 열지 못한 주 문서 — 있으면 이 탭 위에 '열지 못함' 덮개([ErrorCover])가 보인다. 새 쪽이 커밋되면 걷는다. */
    var error: PageError? = null

    /** 이 탭이 마지막으로 방문 기록에 적은 주소 — 같은 문서 안의 이동(SPA)이 같은 곳을 거듭 적지 않게. */
    var historyUrl: String = ""

    /** 마지막으로 화면에 보인(또는 만든) 때 — 접을 탭을 고른다(가장 오래 안 본 것부터). */
    var lastShown: Long = 0

    /** 광고 차단 — 지금 쪽의 결정. 주 문서 요청 때(WebView 의 스레드) 정하고, 하위 요청이 읽는다. */
    @Volatile
    var adPage: AdBlocker.Page? = null

    /** 이 쪽에서 막은 요청 수. */
    val adBlocked = AtomicInteger()

    /** 이 쪽에 이미 넣은 일반 숨김의 열쇠(`.이름`·`#이름`) — 메인 스레드. */
    val adKeys = HashSet<String>()

    /**
     * 이 탭에서 찾은 미디어 — **페이지마다 경계를 둔다**([FoundMediaLog] — 탐색이 커밋될 때 경계만 옮기고 지우지 않는다). WebView 의 스레드가
     * 더하고 화면이 읽는다. 항목은 찾은 페이지의 주소·제목을 든다(받기의 Referer·기본 이름).
     */
    val media = FoundMediaLog()

    /** 쪽↔앱 통로(blob·녹화·가로채기) — 이 WebView 판이 그 기능을 모르면 null. */
    var bridge: PageBridge? = null

    val label: String get() = title.ifBlank { url.ifBlank { "새 탭" } }
}
