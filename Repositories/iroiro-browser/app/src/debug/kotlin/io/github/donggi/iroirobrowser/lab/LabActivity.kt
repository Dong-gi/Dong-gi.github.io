package io.github.donggi.iroirobrowser.lab

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.webkit.CustomHeader
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.net.LocalRelay
import io.github.donggi.iroirobrowser.net.UpstreamProxy
import java.io.IOException
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executor
import javax.net.ssl.HttpsURLConnection

/**
 * **실험판의 유일한 화면.** 브라우저 화면을 짓기 전에 WebView 가 실제로 어떻게 움직이는지 잰다 — 프록시 인증, 직접 연결로 새는가,
 * DNS·WebRTC 누출, 서비스 워커. 실험 하나는 앱 프로세스 하나다: PC 의 도구(`tools/lab/run_lab.py`)가 `am start -S` 로 앱을
 * 죽였다 살리며 조건을 extras 로 넘기고, 이 화면은 본 것을 logcat 의 `IROLAB` 태그에 적는다(한 줄 = `<run> <사건>`).
 *
 * 프록시 설정(ProxyController)은 프로세스 전체에 걸린다 — 그래서 실험마다 프로세스를 새로 띄운다.
 *
 * **디버그 빌드에만 있다**(`app/src/debug`) — 런처에 나오지 않고 adb 로만 연다. 실제 프록시의 확인은 브라우저의 설정·출구 표시가 한다.
 */
class LabActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }
    private lateinit var params: LabParams
    private var webView: WebView? = null
    private var hiddenView: WebView? = null
    private var relay: LocalRelay? = null

    /** 프록시 407 에 답한 횟수(호스트별) — 계정이 틀렸을 때 끝없이 되묻지 않게. */
    private val authAttempts = HashMap<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        val extras = intent.extras
        val experiment = extras != null && extras.containsKey("exp")
        val intro = TextView(this).apply {
            text = getString(R.string.lab_intro)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        root.addView(intro)
        setContentView(root)
        if (!experiment) return
        params = LabParams.from(extras)
        log("start exp=${params.exp} mode=${params.mode} sdk=${Build.VERSION.SDK_INT}")
        when (params.exp) {
            "info" -> info()
            "load" -> load(root)
            "relayhttp" -> relayHttp()
            else -> log("error unknown exp")
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        hiddenView?.destroy()
        hiddenView = null
        relay?.close()
        relay = null
        super.onDestroy()
    }

    // ── info: WebView 판과 기능 ─────────────────────────────────────────────────────────────────────────────────────

    private fun info() {
        val pkg = WebViewCompat.getCurrentWebViewPackage(this)
        log("webview package=${pkg?.packageName} version=${pkg?.versionName}")
        log("ua ${WebSettings.getDefaultUserAgent(this)}")
        for ((name, feature) in FEATURES) {
            log("feature $name=${WebViewFeature.isFeatureSupported(feature)}")
        }
        log("done")
    }

    // ── load: WebView 로 쪽을 연다 ─────────────────────────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun load(root: LinearLayout) {
        val view = WebView(this)
        webView = view
        if (params.profile.isNotEmpty()) {
            // 프로필마다 네트워크 문맥(쿠키·캐시·인증 캐시)이 따로다. 프록시 설정이 그 프로필에도 걸리는지, 407 을 다시 묻는지 본다.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().getOrCreateProfile(params.profile)
                WebViewCompat.setProfile(view, params.profile)
                log("profile ${params.profile}")
            } else {
                log("profile unsupported")
            }
        }
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            // 세이프 브라우징은 WebView 가 아니라 Play 서비스가 묻는다(앱의 프록시를 타지 않는다). 실험의 변수를 줄이려 끈다.
            safeBrowsingEnabled = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        view.webViewClient = LabClient()
        view.webChromeClient = LabChrome()

        when (params.guard) {
            "1" -> inject(view, "webrtc-guard-1", LabScripts.WEBRTC_GUARD)
            "2" -> inject(view, "webrtc-guard-2", LabScripts.WEBRTC_GUARD_V2)
        }
        if (params.dnsGuard) inject(view, "dns-guard", LabScripts.DNS_GUARD)
        if (params.speculative == "off") disableSpeculativeLoading(view)
        if (params.headers == "xrw-empty") overrideRequestedWith(view)

        applyProxy {
            val pre = params.pre
            if (pre != null) {
                log("pre $pre hidden=${params.preHidden}")
                if (params.preHidden) {
                    // 화면에 붙이지 않은 두 번째 WebView — 같은 프로필(같은 인증 캐시)을 쓴다.
                    val hidden = WebView(this)
                    hiddenView = hidden
                    hidden.settings.javaScriptEnabled = true
                    hidden.webViewClient = LabClient()
                    hidden.webChromeClient = LabChrome()
                    hidden.loadUrl(pre)
                } else {
                    view.loadUrl(pre)
                }
                main.postDelayed({ open(view) }, params.preWaitMs)
            } else {
                open(view)
            }
        }
    }

    /** 문서 시작 스크립트를 넣는다 — [LabParams.inject] 가 `event` 면 1.17 의 addJavaScriptOnEvent(쪽의 세계)로. */
    private fun inject(view: WebView, name: String, script: String) {
        try {
            val event = params.inject == "event"
            if (event && WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
                val world = WebViewCompat.getExecutionWorld(view, JavaScriptExecutionWorld.PAGE_WORLD_NAME)
                WebViewCompat.addJavaScriptOnEvent(view, script, WebViewCompat.INJECTION_EVENT_DOCUMENT_START, setOf("*"), world)
                log("inject $name via event")
            } else if (!event && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*"))
                log("inject $name via start")
            } else {
                log("inject $name unsupported(${params.inject})")
            }
        } catch (e: RuntimeException) {
            log("inject $name failed ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 투기적 로딩을 끈다 — DNS 미리 찾기와 상관이 있는지 본다(실측: 없다). 1.17 에서 실험 표지가 붙은 API 다. */
    @OptIn(WebSettingsCompat.ExperimentalSpeculativeLoading::class)
    private fun disableSpeculativeLoading(view: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SPECULATIVE_LOADING)) {
            WebSettingsCompat.setSpeculativeLoadingStatus(view.settings, WebSettingsCompat.SPECULATIVE_LOADING_DISABLED)
            log("speculative loading disabled")
        } else {
            log("speculative loading unsupported")
        }
    }

    /**
     * WebView 는 모든 요청에 `X-Requested-With: <앱 패키지>` 를 단다(실측). 그것을 끄던 API(REQUESTED_WITH_HEADER_ALLOW_LIST)는
     * WebView 153 이 지원하지 않는다 — 1.17 의 사용자 지정 머리로 같은 이름을 덮을 수 있는지 본다.
     */
    private fun overrideRequestedWith(view: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.CUSTOM_REQUEST_HEADERS)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                try {
                    WebViewCompat.getProfile(view).addCustomHeader(CustomHeader("X-Requested-With", "", setOf("*")))
                    log("headers custom X-Requested-With set")
                } catch (e: RuntimeException) {
                    log("headers custom failed ${e.javaClass.simpleName}: ${e.message}")
                }
            } else {
                log("headers custom unsupported(profile)")
            }
        } else {
            log("headers custom unsupported")
        }
    }

    private fun open(view: WebView) {
        log("open ${params.url}")
        view.loadUrl(params.url)
        main.postDelayed({
            view.evaluateJavascript("document.title") { title -> log("final title=$title url=${view.url}") }
            // 오류 쪽은 onReceivedError 없이 뜨기도 한다(서비스 워커의 첫 요청) — 본문에 오류 이름이 있다.
            view.evaluateJavascript("(document.body ? document.body.innerText : '').replace(/\\s+/g, ' ').slice(0, 240)") { text ->
                log("final text=$text")
            }
            main.postDelayed({ log("done") }, 300)
        }, params.waitMs)
    }

    /** 실험의 [LabParams.mode] 대로 프록시를 걸고(또는 걷고), 걸린 뒤에 [then]. */
    private fun applyProxy(then: () -> Unit) {
        val controller = if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) ProxyController.getInstance() else null
        if (controller == null) {
            log("error proxy override unsupported")
            return
        }
        val rule = when (params.mode) {
            "direct" -> {
                controller.clearProxyOverride(mainExecutor) {
                    log("proxy cleared")
                    then()
                }
                return
            }
            "https" -> "https://${params.proxyHost}:${params.proxyPort}"
            "http" -> "http://${params.proxyHost}:${params.httpProxyPort}"
            "relay" -> {
                val r = startRelay()
                "http://127.0.0.1:${r.port}"
            }
            else -> {
                log("error unknown mode")
                return
            }
        }
        val builder = ProxyConfig.Builder().addProxyRule(rule)
        for (b in params.bypass) {
            if (b == "<simple>") builder.bypassSimpleHostnames() else builder.addBypassRule(b)
        }
        // addDirect() 를 부르지 않는다 — 프록시에 닿지 못하면 직접 연결로 넘어가지 않아야 한다(fail-closed). 실험이 그것을 잰다.
        controller.setProxyOverride(builder.build(), mainExecutor) {
            log("proxy applied mode=${params.mode} rule=$rule bypass=${params.bypass.joinToString(",")}")
            then()
        }
    }

    private fun startRelay(): LocalRelay {
        val upstream = UpstreamProxy(tls = true, host = params.proxyHost, port = params.proxyPort, user = params.user, password = params.pass)
        val r = LocalRelay(
            upstream,
            HttpsURLConnection.getDefaultSSLSocketFactory(),
            HttpsURLConnection.getDefaultHostnameVerifier(),
            log = { log(it) },
        ).start()
        relay = r
        return r
    }

    /**
     * 407 에 넣을 계정. 이 실험의 프록시가 묻는 것이 아니면 null — 사이트의 401 에는 계정을 넘기지 않는다.
     * WebView 가 넘기는 `host` 에 포트가 붙는지는 실험이 본다(둘 다 받는다).
     */
    private fun credentialsFor(host: String, realm: String): Pair<String, String>? {
        val r = relay
        val proxyPort = if (params.mode == "http") params.httpProxyPort else params.proxyPort
        return when {
            r != null && realm == r.realm && (host == "127.0.0.1" || host == "127.0.0.1:${r.port}") ->
                r.sessionUser to r.sessionPassword
            (params.mode == "https" || params.mode == "http") &&
                (host.equals(params.proxyHost, ignoreCase = true) || host.equals("${params.proxyHost}:$proxyPort", ignoreCase = true)) ->
                params.user to params.pass
            else -> null
        }
    }

    // onRenderProcessGone 은 아래에 있다 — lint(androidx.webkit 1.17 의 MissingOnRenderProcessGone)가 Kotlin 의 상위 생성자 호출
    // `WebViewClient()` 을 맨 WebViewClient 를 만드는 것으로 잘못 읽는다.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class LabClient : WebViewClient() {

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            val n = (authAttempts[host] ?: 0) + 1
            authAttempts[host] = n
            log("auth host=$host realm=$realm n=$n stored=${handler.useHttpAuthUsernamePassword()}")
            val creds = credentialsFor(host, realm)
            if (creds != null && params.answerAuth && n <= MAX_AUTH_ATTEMPTS) {
                handler.proceed(creds.first, creds.second)
                log("auth answered host=$host")
            } else {
                handler.cancel()
                log("auth cancelled host=$host")
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            log("page started $url")
        }

        override fun onPageFinished(view: WebView, url: String) {
            log("page finished $url title=${view.title}")
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            log("error url=${request.url} main=${request.isForMainFrame} code=${error.errorCode} desc=${error.description}")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            val marker = response.responseHeaders?.entries?.firstOrNull { it.key.equals(LocalRelay.MARKER, ignoreCase = true) }?.value
            log("httperror url=${request.url} main=${request.isForMainFrame} status=${response.statusCode} relay=$marker")
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // 실험 인증 기관을 믿는 설정(디버그의 nsc_lab)이 빠졌거나 이름이 인증서와 맞지 않을 때. 넘어가지 않는다.
            log("sslerror url=${error.url} primary=${error.primaryError}")
            handler.cancel()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            log("renderer gone crashed=${detail.didCrash()}")
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            if (webView === view) webView = null
            return true
        }
    }

    private inner class LabChrome : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            val text = message.message()
            if (text.startsWith(PAGE_PREFIX)) log("page " + text.substring(PAGE_PREFIX.length)) else log("console ${message.messageLevel()} ${text.take(300)}")
            return true
        }
    }

    // ── relayhttp: 앱의 HttpURLConnection 이 중계를 쓸 수 있는가 ───────────────────────────────────────────────────────

    private fun relayHttp() {
        val r = startRelay()
        Thread({
            val urls = params.url.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", r.port))
            // 앞: 계정 없이(중계가 407 로 막아야 한다). 뒤: 이 중계에만 계정을 주는 Authenticator.
            for (u in urls) fetch("noauth", u, proxy)
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    // 플랫폼의 HttpURLConnection 은 프록시 주소를 거꾸로 찾은 이름(127.0.0.1 → 'localhost')으로 넘긴다(실측) —
                    // 이름이 아니라 주소(루프백)와 포트로 가린다.
                    val forRelay = requestorType == RequestorType.PROXY && requestingPort == r.port &&
                        requestingSite?.isLoopbackAddress == true
                    log(
                        "relayhttp authenticator type=$requestorType host=$requestingHost site=$requestingSite port=$requestingPort " +
                            "scheme=$requestingScheme match=$forRelay",
                    )
                    return if (forRelay) PasswordAuthentication(r.sessionUser, r.sessionPassword.toCharArray()) else null
                }
            })
            for (u in urls) fetch("auth", u, proxy)
            Authenticator.setDefault(null)
            log("done")
        }, "lab-relayhttp").start()
    }

    private fun fetch(label: String, url: String, proxy: Proxy) {
        try {
            val c = URL(url).openConnection(proxy) as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.instanceFollowRedirects = false
            val code = c.responseCode
            val body = runCatching { (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } }.getOrNull()
            log("relayhttp $label url=$url status=$code relay=${c.getHeaderField(LocalRelay.MARKER)} bytes=${body?.size} via=${c.getHeaderField("X-Lab-Via")}")
            c.disconnect()
        } catch (e: IOException) {
            log("relayhttp $label url=$url exception=${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun log(event: String) {
        val run = if (this::params.isInitialized) params.run else "-"
        Log.i(TAG, "$run $event")
    }

    companion object {
        private const val TAG = "IROLAB"
        private const val PAGE_PREFIX = "IROLAB "
        private const val MAX_AUTH_ATTEMPTS = 3

        // REQUESTED_WITH_HEADER_ALLOW_LIST 는 1.17 에서 라이브러리 밖에서 부를 수 없게 막혔다(RestrictedApi). WebView 153 에서
        // false 였고(2026-10-04 실측) 지금은 목록에서 뺐다. 실험 표지가 붙은 기능은 지원 여부만 묻는다.
        private val FEATURES = listOf(
            "PROXY_OVERRIDE" to WebViewFeature.PROXY_OVERRIDE,
            "PROXY_OVERRIDE_REVERSE_BYPASS" to WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS,
            "DOCUMENT_START_SCRIPT" to WebViewFeature.DOCUMENT_START_SCRIPT,
            "WEB_MESSAGE_LISTENER" to WebViewFeature.WEB_MESSAGE_LISTENER,
            "SERVICE_WORKER_BASIC_USAGE" to WebViewFeature.SERVICE_WORKER_BASIC_USAGE,
            "SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST" to WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST,
            "MULTI_PROFILE" to WebViewFeature.MULTI_PROFILE,
            "ALGORITHMIC_DARKENING" to WebViewFeature.ALGORITHMIC_DARKENING,
            "SAFE_BROWSING_ENABLE" to WebViewFeature.SAFE_BROWSING_ENABLE,
            "USER_AGENT_METADATA" to WebViewFeature.USER_AGENT_METADATA,
            "SPECULATIVE_LOADING" to WebViewFeature.SPECULATIVE_LOADING,
            "BACK_FORWARD_CACHE" to WebViewFeature.BACK_FORWARD_CACHE,
            "JS_INJECTION_IN_FRAME_AND_WORLD" to WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD,
            "CUSTOM_REQUEST_HEADERS" to WebViewFeature.CUSTOM_REQUEST_HEADERS,
            "PRERENDER_WITH_URL" to WebViewFeature.PRERENDER_WITH_URL,
            "SERVICE_WORKER_BLOCK_NETWORK_LOADS" to WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS,
            "NAVIGATION_LISTENER" to WebViewFeature.NAVIGATION_LISTENER,
            "COOKIE_INTERCEPT" to WebViewFeature.COOKIE_INTERCEPT,
            "DELETE_BROWSING_DATA" to WebViewFeature.DELETE_BROWSING_DATA,
            "HTTP_CACHE_MANAGER" to WebViewFeature.HTTP_CACHE_MANAGER,
            "SAVE_STATE" to WebViewFeature.SAVE_STATE,
            "DEFAULT_TRAFFICSTATS_TAGGING" to WebViewFeature.DEFAULT_TRAFFICSTATS_TAGGING,
            "WEB_AUTHENTICATION" to WebViewFeature.WEB_AUTHENTICATION,
            "PAYMENT_REQUEST" to WebViewFeature.PAYMENT_REQUEST,
            "DOWNLOAD_FAVICONS_ENABLED" to WebViewFeature.DOWNLOAD_FAVICONS_ENABLED,
        ) + experimentalFeatures()

        /** 실험 표지가 붙은 기능 — 지원 여부만 묻는다. */
        @OptIn(
            Profile.ExperimentalPreconnect::class,
            Profile.ExperimentalUrlPrefetch::class,
            Profile.ExperimentalWarmUpRendererProcess::class,
        )
        private fun experimentalFeatures() = listOf(
            "PRECONNECT" to WebViewFeature.PRECONNECT,
            "PROFILE_URL_PREFETCH" to WebViewFeature.PROFILE_URL_PREFETCH,
            "WARM_UP_RENDERER_PROCESS" to WebViewFeature.WARM_UP_RENDERER_PROCESS,
        )
    }
}
