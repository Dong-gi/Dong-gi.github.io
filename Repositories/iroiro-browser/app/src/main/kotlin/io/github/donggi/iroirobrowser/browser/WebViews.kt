package io.github.donggi.iroirobrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.CustomHeader
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.core.UserAgents
import java.security.SecureRandom
import java.util.WeakHashMap

/** 탭 하나의 WebView 를 만든다 — 브라우저로서의 기본값과 모든 쪽에 넣는 스크립트·머리. */
object WebViews {

    private var headerInstalled = false
    private var serviceWorkersConfigured = false

    /** 이 기기의 WebView 가 처음 정하는 UA 글과 힌트 — 모바일·데스크톱 꼴을 이것에서 짓는다(프로세스에 하나, 첫 WebView 에서 읽는다). */
    private var defaultAgent: String? = null
    private var defaultMetadata: UserAgentMetadata? = null

    /** [bridge] — 쪽과 앱 사이의 통로(쪽에서 보이지 않는 세계에 둔다, [PageBridge]). 이 WebView 판이 그 기능을 모르면 null. */
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, bridge: PageBridge?): WebView {
        // 쪽의 뿌리가 맨 위에서 더 밀린 것을 알리는 WebView — 당겨서 새로 고침([PullRefreshLayout])이 듣는다.
        val view = PageWebView(context)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 이 앱의 파일·다른 앱의 content: 를 쪽이 읽지 못하게(기본값은 content 켜짐).
            allowFileAccess = false
            allowContentAccess = false
            // 세이프 브라우징은 쓰지 않는다(사용자 결정, 2026-10-04 — Play 서비스가 프록시 밖에서 묻는다). 매니페스트의 meta-data 도 끈다.
            safeBrowsingEnabled = false
            // 새 창(window.open·target=_blank)은 새 탭으로. **사용자가 누르지 않은 팝업은 앱이 막고 알린다**(PageChrome.onCreateWindow —
            // 사이트마다 허용할 수 있다). 이것을 끄면 크로미움이 그런 팝업을 소리 없이 막아 앱이 알 길이 없다.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // 위치 API 를 끈다 — 앱이 테더링(Wi-Fi Direct) 때문에 위치 권한을 가질 수 있어도 페이지에는 주지 않는다(PageChrome 도 거절한다).
            setGeolocationEnabled(false)
        }
        rememberDefaults(view.settings)
        applyUserAgent(view, desktop = false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, PageScripts.WEBRTC_GUARD, setOf("*"))
        }
        bridge?.install(view)
        installHeaders(view)
        configureServiceWorkers()
        return view
    }

    /** 첫 WebView 의 기본값을 적어 둔다 — 그 뒤로는 UA 를 바꾼 WebView 만 있어 기본값을 다시 읽을 수 없다. */
    private fun rememberDefaults(settings: WebSettings) {
        if (defaultAgent != null) return
        defaultAgent = settings.userAgentString
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val d = WebSettingsCompat.getUserAgentMetadata(settings)
            defaultMetadata = d
            if (BuildConfig.DEBUG) Log.i("WebUI", "ua-metadata default ${d.brandVersionList} model=${d.model} platform=${d.platformVersion}")
        }
    }

    /**
     * **일반화된 User-Agent**(사용자 결정, 2026-10-05). 글은 크롬의 줄인 꼴([UserAgents.generalize] — `wv`·기기 모델이 빠진다), 클라이언트
     * 힌트(`Sec-CH-UA*`·`navigator.userAgentData`)는 WebView 의 값에서 **`Android WebView` 상표와 기기 모델을 뺀다**. 글만 바꾸고 힌트를
     * 두면 WebView 는 낮은 엔트로피의 힌트만 보내는데 그 상표 목록에 WebView 가 남는다(androidx.webkit 의 문서) — 그래서 둘 다 정한다.
     *
     * [desktop] 이면 **데스크톱 사이트**(탭마다): 크롬(안드로이드)의 '데스크톱 사이트' 가 내는 리눅스 데스크톱 꼴을 같은 줄인
     * 꼴로([UserAgents.desktop]), 힌트는 모바일 아님·플랫폼 Linux·x86 64비트·모델 없음으로. 바꾼 UA 는 다음 요청부터라 부르는 쪽이 쪽을
     * 다시 부른다. 쪽의 화면 폭(뷰포트)은 그대로다 — 이미 넓은 뷰포트(`useWideViewPort`)를 쓴다.
     */
    fun applyUserAgent(view: WebView, desktop: Boolean) {
        val settings = view.settings
        val base = defaultAgent ?: settings.userAgentString
        val agent = (if (desktop) UserAgents.desktop(base) else UserAgents.generalize(base)) ?: return
        settings.userAgentString = agent
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val d = defaultMetadata ?: WebSettingsCompat.getUserAgentMetadata(settings)
            val brands = d.brandVersionList.filterNot { UserAgents.isWebViewBrand(it.brand) }
            // 빈 목록은 '기본값을 쓰라' 는 뜻이라 WebView 상표가 돌아온다 — 그때는 크로뮴 하나를 둔다.
            val list = brands.ifEmpty {
                val major = Regex("""Chrome/(\d+)""").find(agent)?.groupValues?.get(1) ?: return
                listOf(UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setMajorVersion(major).setFullVersion("$major.0.0.0").build())
            }
            val builder = UserAgentMetadata.Builder(d).setBrandVersionList(list).setModel("")
            if (desktop) {
                builder.setMobile(false).setPlatform("Linux").setPlatformVersion("").setArchitecture("x86").setBitness(64).setWow64(false)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA_FORM_FACTORS)) {
                    builder.setFormFactors(listOf(UserAgentMetadata.FORM_FACTOR_DESKTOP))
                }
            }
            WebSettingsCompat.setUserAgentMetadata(settings, builder.build())
        }
    }

    /** '항상 확대 허용' 의 스크립트를 건 WebView 와 그 손잡이 — 설정을 끄면 걷는다. 메인 스레드에서만. */
    private val forceZoomScripts = WeakHashMap<WebView, ScriptHandler>()

    /** 확대 스크립트의 실행 세계 이름 — 프로세스마다 무작위(쪽은 그 세계를 볼 수 없지만 고정된 이름을 두지 않는다 — [PageBridge] 와 같다). */
    private val forceZoomWorld: String by lazy {
        val b = ByteArray(9)
        SecureRandom().nextBytes(b)
        "z" + b.joinToString("") { "%02x".format(it) }
    }

    /**
     * **'항상 확대 허용'**([PageScripts.FORCE_ZOOM] — 사이트가 막아 둔 두 손가락 확대를 푼다)을 [view] 에 걸거나 걷는다. **다음에 여는 문서부터**
     * 듣는다(문서 시작 스크립트다). 새로 걸었으면 참 — 부르는 쪽이 이미 연 페이지에도 한 번 돌릴 수 있다([PageScripts.FORCE_ZOOM] 은 여러 번
     * 돌아도 같다).
     *
     * 쪽에서 보이지 않는 실행 세계(`JS_INJECTION_IN_FRAME_AND_WORLD`)에 둔다 — 쪽의 스크립트가 고치는 코드를 보거나 바꾸지 못한다. 그 기능을 모르는
     * WebView 면 쪽의 세계에 둔다(`DOCUMENT_START_SCRIPT`). 둘 다 모르면 걸지 않는다.
     */
    fun applyForceZoom(view: WebView, on: Boolean): Boolean {
        val held = forceZoomScripts[view]
        if (!on) {
            held?.remove()
            forceZoomScripts.remove(view)
            return false
        }
        if (held != null) return false
        val handler = runCatching {
            when {
                WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD) ->
                    WebViewCompat.addJavaScriptOnEvent(
                        view,
                        PageScripts.FORCE_ZOOM,
                        WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                        setOf("*"),
                        WebViewCompat.getExecutionWorld(view, forceZoomWorld),
                    )
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ->
                    WebViewCompat.addDocumentStartJavaScript(view, PageScripts.FORCE_ZOOM, setOf("*"))
                else -> null
            }
        }.getOrNull() ?: return false
        forceZoomScripts[view] = handler
        return true
    }

    /**
     * 서비스 워커도 쪽과 같게 이 앱의 파일·다른 앱의 content: 에 닿지 못하게 한다 — 서비스 워커의 설정은 따로이고 기본값은 둘 다 켜짐이다.
     */
    private fun configureServiceWorkers() {
        if (serviceWorkersConfigured) return
        serviceWorkersConfigured = true
        ServiceWorkerController.getInstance().serviceWorkerWebSettings.apply {
            allowContentAccess = false
            allowFileAccess = false
        }
    }

    /**
     * WebView 는 모든 요청에 `X-Requested-With: <이 앱의 패키지>` 를 단다 — 개인 앱의 이름이 곧 사람을 가리킨다. 그것을 끄던 API 는 WebView
     * 153 이 지원하지 않고, 1.17 의 사용자 지정 머리로 **빈 값**으로 덮는다(쪽이 스스로 단 값은 그대로 간다 — 실험 headers-xrw-ajax).
     * 프로필에 한 번 걸면 그 프로필의 모든 WebView 에 걸린다.
     */
    private fun installHeaders(view: WebView) {
        if (headerInstalled) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.CUSTOM_REQUEST_HEADERS)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching {
                    WebViewCompat.getProfile(view).addCustomHeader(CustomHeader("X-Requested-With", "", setOf("*")))
                    headerInstalled = true
                }
            }
        }
    }
}
