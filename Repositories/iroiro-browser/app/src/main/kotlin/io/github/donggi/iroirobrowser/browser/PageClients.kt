package io.github.donggi.iroirobrowser.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.Navigation
import androidx.webkit.NavigationListener
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.donggi.iroirobrowser.net.LocalRelay
import io.github.donggi.iroirobrowser.proxy.NetworkPath

/** 탭의 WebView 가 화면(브라우저 화면)에 알리고 묻는 것. */
interface PageHost {
    val network: NetworkPath

    fun onStarted(tab: Tab, url: String)
    fun onFinished(tab: Tab, url: String)
    fun onUrlChanged(tab: Tab, url: String)

    /** 같은 문서 안에서 주소가 바뀌었다(SPA 의 `pushState`·`replaceState`·조각) — 주 틀만, 커밋된 것만([PageNavigation]). */
    fun onSameDocumentNavigation(tab: Tab, url: String, reload: Boolean)
    fun onProgress(tab: Tab, progress: Int)
    fun onTitle(tab: Tab, title: String)

    /** 주 문서가 열리지 않았다 — 연결 오류·중계가 막음·프록시 서버의 오류 쪽([PageErrors.Source]). */
    fun onMainFrameError(tab: Tab, url: String, source: PageErrors.Source)

    /** 인증서 오류(넘기지 않는다) — 주 문서의 것인지는 화면이 가린다(하위 자원 하나의 인증서로 쪽 전체가 막힌 것처럼 보이지 않게). */
    fun onSslError(tab: Tab, url: String)
    fun askSiteLogin(host: String, realm: String, handler: HttpAuthHandler)
    fun askExternal(url: String)
    fun openWindow(opener: Tab, resultMsg: Message): Boolean

    /** 사용자가 누르지 않은 팝업을 열어도 되는가 — 그 사이트를 허용했을 때만. */
    fun popupAllowed(opener: Tab): Boolean

    /** 사용자가 누르지 않은 팝업을 막았다 — 알리고 사이트 허용을 권한다. */
    fun onPopupBlocked(opener: Tab)
    fun closeWindow(tab: Tab)
    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
    fun hideCustomView()
    fun pickFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean

    /** 렌더러가 사라졌다 — [crashed] 면 쪽이 멈추게 했고, 아니면 시스템이 메모리를 찾으려 닫았다. */
    fun onRendererGone(tab: Tab, crashed: Boolean)

    /** 쪽이 DRM 재생(보호된 미디어)을 청해 주었다. */
    fun onProtectedMedia(tab: Tab, origin: String)

    /** 요청마다 — **WebView 의 스레드에서**(메인이 아니다). 광고면 막는 응답, 아니면 null. */
    fun intercept(tab: Tab, request: WebResourceRequest): WebResourceResponse?
}

/**
 * 탭의 WebViewClient. 브라우저로서의 결정이 여기 있다:
 *
 * - **프록시 인증**: 중계의 407(무작위 realm 으로 가린다)에만 중계 계정으로 답한다. 그 밖의 인증 요청은 사이트의 401 이다 — 사용자에게
 *   묻는다. 원격 프록시의 계정은 WebView 에 아예 없다(중계가 붙인다).
 * - **SSL 오류는 넘기지 않는다**(크롬처럼 '그래도 열기' 를 두지 않는다).
 * - WebView 가 다루지 않는 스킴은 [ExternalLinks] 의 규칙대로 사용자에게 묻는다.
 */
// MissingOnRenderProcessGone: 아래에 있다 — androidx.webkit 1.17 의 lint 가 Kotlin 의 상위 생성자 호출을 맨 WebViewClient 로 잘못 읽는다.
@SuppressLint("MissingOnRenderProcessGone")
class PageClient(private val host: PageHost, private val tab: Tab) : WebViewClient() {

    private var relayAttempts = 0

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, authHost: String, realm: String) {
        val relay = host.network.relay
        if (relay != null && host.network.isRelayChallenge(relay, authHost, realm)) {
            // 중계의 계정은 세션 내내 같다 — 거듭 묻는다면 무언가 어긋났다. 끝없이 되묻지 않게 끊는다.
            if (++relayAttempts <= MAX_RELAY_ATTEMPTS) handler.proceed(relay.sessionUser, relay.sessionPassword) else handler.cancel()
            return
        }
        host.askSiteLogin(authHost, realm, handler)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = host.intercept(tab, request)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (ExternalLinks.isWebScheme(url)) return false
        // 다른 앱으로: 주 프레임에서, 사용자가 누른 것(또는 그 넘겨주기)만 묻는다. 그 밖은 조용히 막는다.
        if (request.isForMainFrame && (request.hasGesture() || request.isRedirect)) host.askExternal(url.toString())
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        host.onStarted(tab, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        relayAttempts = 0
        host.onFinished(tab, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        host.onUrlChanged(tab, url)
    }

    /**
     * 중계가 스스로 만든 오류 응답(정하지 않음·원격 불통·계정 거절 — `X-Iroiro-Relay` 머리)도 주 문서의 오류로 친다(기록에 적지 않게, 덮개가
     * 까닭을 말하게). 프록시 서버(Squid)가 만든 오류 쪽(`X-Squid-Error`)도 — 그 쪽은 영어라 덮개가 우리말로 까닭을 덧붙인다.
     */
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (!request.isForMainFrame) return
        val headers = errorResponse.responseHeaders ?: return
        val url = request.url.toString()
        val relay = headers.entries.firstOrNull { it.key.equals(LocalRelay.MARKER, ignoreCase = true) }
        if (relay != null) {
            host.onMainFrameError(tab, url, PageErrors.Source.Relay(relay.value.orEmpty(), errorResponse.statusCode))
            return
        }
        val squid = headers.entries.firstOrNull { it.key.equals(SQUID_ERROR, ignoreCase = true) }
        if (squid != null) host.onMainFrameError(tab, url, PageErrors.Source.Proxy(squid.value.orEmpty(), errorResponse.statusCode))
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            host.onMainFrameError(tab, request.url.toString(), PageErrors.Source.Network(error.errorCode, error.description?.toString().orEmpty()))
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        host.onSslError(tab, error.url ?: "")
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        // true 를 돌려주는 대가로 이 WebView 를 떼어 내 부숴야 한다(문서의 약속) — 화면이 한다.
        host.onRendererGone(tab, detail.didCrash())
        return true
    }

    private companion object {
        const val MAX_RELAY_ATTEMPTS = 3

        /** Squid 가 제 오류 쪽에 다는 머리(`ERR_ACCESS_DENIED 0` 꼴). */
        const val SQUID_ERROR = "X-Squid-Error"
    }
}

/**
 * 주 틀의 탐색 알림(androidx.webkit 1.17 의 `NavigationListener`) — **같은 문서 안의 이동**(SPA 의 `pushState`·`replaceState`·조각)을
 * 가린다. `doUpdateVisitedHistory` 는 새 문서의 커밋에도 오고 그것이 같은 문서인지 말하지 않아(새 문서는 다 연 뒤 방문 기록에 적으므로 두 번
 * 적게 된다) 이것을 쓴다. 그 기능을 모르는 WebView 면 걸지 않는다 — 그때는 SPA 이동을 방문 기록에 적지 못한다.
 */
class PageNavigation private constructor(private val host: PageHost, private val tab: Tab) : NavigationListener {

    override fun onNavigationCompleted(navigation: Navigation) {
        if (navigation.isSameDocument && navigation.didCommit()) host.onSameDocumentNavigation(tab, navigation.url, navigation.isReload)
    }

    companion object {
        /** [view] 에 건다 — 그 기능이 없으면 아무것도 하지 않는다. */
        fun attach(view: WebView, host: PageHost, tab: Tab) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER)) {
                WebViewCompat.addNavigationListener(view, PageNavigation(host, tab))
            }
        }
    }
}

/** 탭의 WebChromeClient — 진행·제목·새 창·전체 화면·파일 고르기, 그리고 권한(보호된 미디어만 준다). */
class PageChrome(private val host: PageHost, private val tab: Tab) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        host.onProgress(tab, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        host.onTitle(tab, title ?: "")
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        // 사용자가 누르지 않은 팝업은 열지 않는다 — 그 사이트를 허용했으면 연다. 막으면 알린다(앞 판은 소리 없이 막았다).
        if (!isUserGesture && !host.popupAllowed(tab)) {
            host.onPopupBlocked(tab)
            return false
        }
        return host.openWindow(tab, resultMsg)
    }

    override fun onCloseWindow(window: WebView) {
        host.closeWindow(tab)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        host.showCustomView(view, callback)
    }

    override fun onHideCustomView() {
        host.hideCustomView()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.pickFiles(filePathCallback, fileChooserParams)

    /**
     * **보호된 미디어(DRM 재생)만 준다**(사용자 결정, 2026-10-05 — 'DRM 영상 재생을 지원합시다'). 쪽이 EME 로 라이선스를 청하려면 이것이
     * 있어야 한다(Widevine 은 출처마다 기기 식별자를 쓴다). 카메라·마이크는 이 앱에 그 권한이 없고 주지 않는다 — 함께 청하면 모두 거절한다.
     * DRM 이 걸린 영상은 재생만 되고 받기·가로채기·녹화는 하지 않는다.
     */
    override fun onPermissionRequest(request: PermissionRequest) {
        val resources = request.resources
        if (resources.isNotEmpty() && resources.all { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
            request.grant(resources)
            host.onProtectedMedia(tab, request.origin?.toString() ?: "")
        } else {
            request.deny()
        }
    }

    /**
     * 위치는 주지 않는다. 테더링을 합친 뒤로(2026-10-10) 앱이 위치 권한(Wi-Fi Direct 에 드는 — 12 이하)을 가질 수 있다 — 이 거절과 WebViews 의
     * `setGeolocationEnabled(false)` 가 OS 권한이라는 바깥 겹 없이 지키는 선이다.
     */
    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }
}
