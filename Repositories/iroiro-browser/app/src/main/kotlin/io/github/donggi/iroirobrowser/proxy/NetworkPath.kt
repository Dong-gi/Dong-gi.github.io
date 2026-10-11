package io.github.donggi.iroirobrowser.proxy

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import io.github.donggi.iroirobrowser.AppUserAgent
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.ExitInfo
import io.github.donggi.iroirobrowser.core.ExitTrace
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.net.HttpFetcher
import io.github.donggi.iroirobrowser.net.LocalRelay
import io.github.donggi.iroirobrowser.net.UpstreamProxy
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/**
 * **이 앱의 네트워크 길을 정하는 유일한 곳.** 사용자의 결정(2026-10-04)대로 길은 [LocalRelay] 하나다:
 *
 * - 프록시가 켜져 있으면 중계를 세우고, WebView(프로세스 전체 — `ProxyController`)와 앱의 HTTP([httpRoute])를 모두 그리로 보낸다.
 *   프록시를 정하지 않았으면 중계가 모든 요청을 502 로 막는다 — **직접 연결로 가는 길은 없다.** `addDirect()` 를 넣지 않는다.
 * - **예외 규칙**(사설망·`*.local`·점 없는 이름 …)에 든 주소만 바로 간다 — WebView 는 override 의 bypass 로, 앱의 HTTP 는 같은 규칙을
 *   크로미움처럼 읽는 [io.github.donggi.iroirobrowser.core.BypassMatcher] 로 **홉마다**([AppRoute] — 사용자 결정 2026-10-10). 출구 확인은
 *   예외를 보지 않고 늘 중계로 간다(프록시의 출구를 보려는 것이다).
 * - 꺼져 있으면(사용자가 고른 것) 직접 연결이다.
 *
 * 상태([State])는 중계의 소식(원격에 닿았다·계정이 거절됐다·닿지 못했다)과 출구 확인([checkExit] — 앱의 HTTP 로 확인 쪽을 읽는다)으로
 * 정한다. 화면은 [Listener] 로 받는다. 공개 함수는 메인 스레드에서 부른다.
 *
 * **쪽은 [whenReady] 로만 연다.** 설정을 바꾸면([update]) 판([revision])이 오르고, 새 길이 WebView 에 걸리고 데워질 때까지 [whenReady] 가
 * 기다린다 — 그 사이에 연 쪽은 옛 길(켜던 중이면 직접)로 나가기 때문이다. **옛 길의 연결을 끊는 일**은 데우기를 기다리지 않는다 — 새 길이
 * WebView 에 걸리면 곧바로([whenApplied]). 데우기는 서비스 워커의 인증 캐시를 위한 것이지 끊는 데 필요한 것이 아니다. 길이 그대로인 저장
 * (출구 확인 주소만 바꿨다·아무것도 바꾸지 않았다)은 중계를 다시 세우지 않는다([ProxySettings.sameRoute]).
 *
 * 브라우저는 기본 프로필 하나만 쓴다(익명 탭은 두지 않는다 — 사용자 결정). 인증 캐시는 프로필마다 따로라(실험 profile) 프로필을 늘리면 그
 * 프로필도 데워야 한다.
 */
class NetworkPath(context: Context, private val store: SettingsStore) {

    enum class Phase {
        /** 사용자가 껐다 — 직접 연결. */
        OFF,

        /** 켜져 있는데 프록시를 정하지 않았다 — 아무 데도 가지 않는다. */
        NOT_CONFIGURED,

        /** 출구를 확인하는 중. */
        CHECKING,

        /** 출구를 확인했다. */
        OK,

        /** 출구가 한국이다 — 프록시를 거치지 않은 것 같다. */
        LOOKS_DIRECT,

        /** 프록시에 닿지 못한다. */
        UNREACHABLE,

        /** 프록시가 계정을 거절했다. */
        AUTH_REJECTED,

        /** 프록시에는 닿는데 출구 확인 쪽이 답하지 않는다. */
        CHECK_FAILED,

        /** 이 WebView 는 프록시 설정을 지원하지 않는다 — 열지 않는다. */
        UNSUPPORTED,
    }

    data class State(val phase: Phase, val exit: ExitInfo? = null)

    fun interface Listener {
        fun onState(state: State)
    }

    /** 데우는 중인 숨은 WebView 하나. [finish] 는 한 번만 일한다. */
    private class Warming(val generation: Int, val finish: (warmed: Boolean) -> Unit)

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "web-exit-check").apply { isDaemon = true } }
    private val listeners = CopyOnWriteArraySet<Listener>()

    /** 지금의 중계. 프록시가 켜져 있으면 늘 있다. 다른 스레드(내려받기·인증기)가 읽는다. */
    @Volatile
    var relay: LocalRelay? = null
        private set

    @Volatile
    var settings: ProxySettings = store.load()
        private set

    /**
     * 앱의 HTTP 가 길을 고르는 기준 — 설정과 그때의 중계를 한 덩이로 바꿔 끼운다([publishRoute] — 메인 스레드). 다른 스레드(내려받기·광고
     * 목록·미리 읽기)는 한 번 읽어 받기 하나에 끝까지 쓴다. 중계가 서기 전에는 켜져 있으면 모두 null(받지 않는다), 꺼져 있으면 모두 바로다.
     */
    @Volatile
    private var route: AppRoute = AppRoute.of(settings, null)

    var state: State = State(Phase.CHECKING)
        private set

    /** 중계를 새로 세울 때마다 오른다 — 옛 중계의 소식과 옛 확인의 답을 버린다. */
    private var generation = 0

    /** 길이 바뀐 횟수 — 화면이 보고 열린 탭을 다시 불러온다(옛 길로 붙어 있는 연결을 끊게). */
    val revision: Int get() = generation
    private var webViewEngaged = false

    /** WebView 에 길(override 를 걸었거나 걷었다)이 걸린 판. */
    private var appliedGeneration = -1

    /** 데운 판. */
    private var warmedGeneration = -1

    /** 데우는 중인 것: 표(데우기 주소의 `?` 뒤 — 중계가 돌려 알린다) → 그 일. 옛 판의 늦은 소식을 가린다. */
    private val warming = HashMap<String, Warming>()
    private val readyWaiters = ArrayList<() -> Unit>()
    private val appliedWaiters = ArrayList<() -> Unit>()
    private var lastCheckAt = 0L
    private var foreground = false
    private val random = SecureRandom()
    /** 출구 확인의 UA — 쪽과 같은 일반화된 꼴(기기 모델·wv 를 싣지 않는다). */
    private val userAgent: String by lazy { AppUserAgent.of(app) }

    /** 앱이 뜰 때 한 번 — 중계를 세운다(WebView 는 아직 건드리지 않는다). */
    fun start() {
        restartRelay()
    }

    fun addListener(listener: Listener) {
        listeners += listener
        listener.onState(state)
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** 브라우저 화면이 뜰 때 — WebView 의 길을 걸고 데운다(처음 한 번). 그 뒤로는 [update] 가 다시 건다. */
    fun engageWebView() {
        if (webViewEngaged) return
        webViewEngaged = true
        prepare {}
    }

    /** 지금 판의 길이 WebView 에 걸리고 데워졌다 — 쪽을 열어도 된다. */
    val isReady: Boolean get() = appliedGeneration == generation && (!needsWarmUp() || warmedGeneration == generation)

    /**
     * 지금 판의 길이 WebView 에 걸리고 데워진 뒤에 [block]. 기다리는 사이에 길이 다시 바뀌면 새 길을 기다린다. 프록시가 켜져 있는데
     * WebView 가 프록시 설정을 지원하지 않으면 부르지 않는다(열지 않는다).
     */
    fun whenReady(block: () -> Unit) {
        if (isReady) block() else readyWaiters += block
    }

    /** 지금 판의 길이 WebView 에 걸렸다(데우기는 아직일 수 있다). */
    val isApplied: Boolean get() = appliedGeneration == generation

    /**
     * 지금 판의 길이 WebView 에 걸린 뒤에 [block] — **데우기를 기다리지 않는다.** 옛 길에 붙어 있던 연결을 끊는 데 쓴다(끈 상태에서 켜면 옛
     * 길이 '직접' 이라, 데우기(최대 [WARMUP_TIMEOUT_MS])까지 기다리면 그동안 열린 쪽이 스스로 내는 요청이 직접 나간다). 쪽을 여는 데는
     * [whenReady] 를 쓴다(서비스 워커의 요청은 데운 뒤에야 407 을 넘는다). 기다리는 사이에 길이 다시 바뀌면 새 길을 기다린다. 프록시가 켜져
     * 있는데 WebView 가 프록시 설정을 지원하지 않으면 부르지 않는다.
     */
    fun whenApplied(block: () -> Unit) {
        if (isApplied) block() else appliedWaiters += block
    }

    private fun needsWarmUp(): Boolean = settings.enabled && relay != null

    /** WebView 의 길을 지금 설정에 맞춰 걸고, 걸리면 데운다. 길이 걸리면 — 또는 걸 수 없으면(지원하지 않음) — [settled]. */
    private fun prepare(settled: () -> Unit) {
        val gen = generation
        applyToWebView(
            onApplied = {
                // 그 사이에 길이 또 바뀌었으면 이 판은 끝났다 — 새 판의 prepare 가 마저 한다.
                if (gen == generation) {
                    appliedGeneration = gen
                    flushApplied()
                    warmUp()
                }
                settled()
            },
            onUnsupported = settled,
        )
    }

    private fun flushApplied() {
        if (!isApplied) return
        val waiting = ArrayList(appliedWaiters)
        appliedWaiters.clear()
        waiting.forEach { it() }
    }

    /** WebView 의 길(`ProxyController` — 프로세스 전체)을 지금 설정에 맞춘다. 다 걸리면 [onApplied]. */
    private fun applyToWebView(onApplied: () -> Unit, onUnsupported: () -> Unit) {
        val s = settings
        val controller = if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) ProxyController.getInstance() else null
        if (controller == null) {
            if (s.enabled) {
                setState(State(Phase.UNSUPPORTED))
                onUnsupported()
            } else {
                onApplied()
            }
            return
        }
        val r = relay
        if (!s.enabled || r == null) {
            controller.clearProxyOverride(mainExecutor) { onApplied() }
            return
        }
        val config = try {
            buildConfig(r, s, withBypass = true)
        } catch (e: IllegalArgumentException) {
            // 설정 화면이 고른 규칙만 저장하므로 오지 않을 길이다. 와도 예외를 빼고 모두 프록시로 보낸다(더 좁은 쪽).
            debugLog("bypass rules rejected: ${e.message}")
            buildConfig(r, s, withBypass = false)
        }
        controller.setProxyOverride(config, mainExecutor) { onApplied() }
    }

    private fun buildConfig(r: LocalRelay, s: ProxySettings, withBypass: Boolean): ProxyConfig {
        val builder = ProxyConfig.Builder().addProxyRule("http://127.0.0.1:${r.port}")
        if (withBypass) {
            s.bypass.rules().forEach { builder.addBypassRule(it) }
            if (s.bypass.simpleHostnames) builder.bypassSimpleHostnames()
        }
        return builder.build()
    }

    /**
     * 보이지 않는 WebView 로 중계의 데우기 주소를 연다 — WebView 가 중계의 407 에 답하며 인증 캐시를 채운다(서비스 워커의 요청은 407 에
     * 답하지 못한다 — 실험 sw-cold). 바깥으로는 아무것도 나가지 않는다. 주소에 무작위 표를 달아 중계가 이 판의 데우기가 왔다고 알리게
     * 한다(옛 중계의 늦은 소식과 섞이지 않게). [timeoutMs] 안에 오지 않으면 데우지 못한 채로 연다(서비스 워커의 첫 요청만 실패할 수 있다).
     */
    // MissingOnRenderProcessGone: 아래의 WebViewClient 에 있다 — lint 의 오탐(PageClient 참고).
    @SuppressLint("MissingOnRenderProcessGone")
    private fun warmUp(timeoutMs: Long = WARMUP_TIMEOUT_MS) {
        val gen = generation
        if (isReady) {
            flushReady()
            return
        }
        if (appliedGeneration != gen || warming.values.any { it.generation == gen }) return
        val view = WebView(app)
        val tag = token()
        var done = false
        val work = Warming(gen) { ok ->
            if (!done) {
                done = true
                warming.remove(tag)
                main.post { view.destroy() }
                if (gen == generation) {
                    if (!ok) debugLog("warm-up timed out")
                    warmedGeneration = gen
                    flushReady()
                }
            }
        }
        warming[tag] = work
        view.webViewClient = object : WebViewClient() {
            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                val current = relay
                if (current != null && isRelayChallenge(current, host, realm)) {
                    handler.proceed(current.sessionUser, current.sessionPassword)
                } else {
                    handler.cancel()
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                work.finish(false)
                return true
            }
        }
        view.loadUrl("${LocalRelay.WARMUP_URL}?$tag")
        main.postDelayed({ work.finish(false) }, timeoutMs)
    }

    private fun flushReady() {
        if (!isReady) return
        val waiting = ArrayList(readyWaiters)
        readyWaiters.clear()
        waiting.forEach { it() }
    }

    /** 이 인증 요청이 지금 중계의 것인가 — realm 이 세션마다 무작위라 사이트가 흉내 내지 못한다. */
    fun isRelayChallenge(relay: LocalRelay, host: String, realm: String): Boolean =
        realm == relay.realm && (host == "127.0.0.1" || host == "127.0.0.1:${relay.port}")

    /**
     * 설정을 바꿨다(켜기·끄기도) — 저장하고, 중계를 새로 세우고(새 계정·realm·포트), WebView 의 길을 다시 걸고, 출구를 다시 본다.
     * 판([revision])이 오른다. [then] 은 WebView 의 길이 다시 걸린 뒤(WebView 를 아직 쓰지 않았으면 바로). 데우기는 [whenReady] 가 기다린다.
     *
     * **길이 그대로면 다시 세우지 않는다**([ProxySettings.sameRoute]) — 저장만 하고(출구 확인 주소가 바뀌었으면 출구를 다시 본다) 판도 그대로다.
     * 다시 세우면 열린 쪽을 모두 다시 부르고 옛 중계를 지나던 받기가 끊긴다 — 설정에서 아무것도 바꾸지 않고 '저장' 만 눌러도 그랬다.
     */
    fun update(newSettings: ProxySettings, then: () -> Unit = {}) {
        val old = settings
        store.save(newSettings)
        settings = newSettings
        if (newSettings.sameRoute(old)) {
            publishRoute()
            if (newSettings.exitCheckUrl != old.exitCheckUrl) checkExit()
            then()
            return
        }
        restartRelay()
        if (webViewEngaged) prepare(then) else then()
    }

    /**
     * 앱의 HTTP 가 쓸 길 — **홉마다** 고른다([AppRoute]): 예외 규칙에 든 주소는 바로, 나머지는 중계. 프록시를 껐으면 모두 바로, 켰는데 중계가
     * 없으면 모두 null([HttpFetcher.NoRoute] — 받지 않는다, 직접 나가지 않는다). 부른 때의 설정·중계를 붙든다 — 받기 하나(시도 하나)마다
     * 새로 얻는다. 아무 스레드에서나 부른다.
     */
    fun httpRoute(): HttpFetcher.Route = route

    /** 출구 확인의 길 — 늘 중계([AppRoute.relayOnly]). 중계가 없으면 null. */
    private fun exitRoute(): HttpFetcher.Route? = AppRoute.relayOnly(relay?.port)

    /** 지금의 설정·중계로 [route] 를 다시 짓는다 — 둘을 바꾼 뒤에(메인 스레드). */
    private fun publishRoute() {
        route = AppRoute.of(settings, relay?.port)
    }

    /** 화면이 보이는 동안 출구를 [PERIOD_MS] 마다 다시 본다. */
    fun setForeground(value: Boolean) {
        foreground = value
        main.removeCallbacks(periodic)
        if (value) {
            if (SystemClock.elapsedRealtime() - lastCheckAt > PERIOD_MS) checkExit()
            main.postDelayed(periodic, PERIOD_MS)
        }
    }

    private val periodic = object : Runnable {
        override fun run() {
            if (!foreground) return
            checkExit()
            main.postDelayed(this, PERIOD_MS)
        }
    }

    /** 출구 확인 — 앱의 HTTP 로 확인 쪽을 읽는다. **넘겨주기의 홉까지 늘 중계로**(예외 규칙을 보지 않는다 — [exitRoute]). */
    fun checkExit() {
        val s = settings
        if (!s.enabled || !s.isComplete) return
        val route = exitRoute() ?: return
        val gen = generation
        lastCheckAt = SystemClock.elapsedRealtime()
        if (state.phase != Phase.AUTH_REJECTED && state.phase != Phase.UNREACHABLE) setState(State(Phase.CHECKING, state.exit))
        val url = s.exitCheckUrl
        val agent = userAgent
        io.execute {
            val exit = try {
                HttpFetcher(route, connectTimeoutMs = CHECK_TIMEOUT_MS, readTimeoutMs = CHECK_TIMEOUT_MS)
                    .get(url, headersFor = { mapOf("User-Agent" to agent, "Cache-Control" to "no-cache") })
                    .use { r -> if (r.code == 200) ExitTrace.parse(readAtMost(r.body, 16 * 1024).decodeToString()) else null }
            } catch (e: IOException) {
                debugLog("exit check failed: ${e.javaClass.simpleName}")
                null
            }
            main.post {
                if (gen != generation) return@post
                state = when {
                    exit != null && exit.looksDirect -> State(Phase.LOOKS_DIRECT, exit)
                    exit != null -> State(Phase.OK, exit)
                    state.phase == Phase.AUTH_REJECTED || state.phase == Phase.UNREACHABLE -> state
                    else -> State(Phase.CHECK_FAILED)
                }
                listeners.forEach { it.onState(state) }
            }
        }
    }

    /** 앞의 [limit] 바이트까지만 읽는다(`readNBytes` 는 API 33 부터다). */
    private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun restartRelay() {
        relay?.close()
        relay = null
        generation++
        // 옛 판의 데우기를 끝낸다 — 숨은 WebView 를 없앤다(판이 달라 데웠다고 적지 않는다).
        ArrayList(warming.values).forEach { it.finish(false) }
        val s = settings
        if (!s.enabled) {
            publishRoute()
            setState(State(Phase.OFF))
            return
        }
        val upstream = if (s.isComplete) UpstreamProxy(true, s.host.trim(), s.port, s.user, s.password) else null
        val gen = generation
        relay = LocalRelay(
            upstream,
            HttpsURLConnection.getDefaultSSLSocketFactory(),
            HttpsURLConnection.getDefaultHostnameVerifier(),
            log = ::debugLog,
            listener = object : LocalRelay.Listener {
                override fun onUpstream(event: LocalRelay.UpstreamEvent) {
                    // 이 객체의 onUpstream 이 아니라 NetworkPath 의 것을 부른다 — 이름이 같으면 제 자신을 다시 부르며 메인 스레드를 돈다(그랬다).
                    main.post { if (gen == generation) handleUpstream(event) }
                }

                override fun onWarmedUp(tag: String) {
                    main.post { if (gen == generation) warming[tag]?.finish?.invoke(true) }
                }
            },
            errorPage = ::relayPage,
        ).start()
        // 옛 중계를 닫은 뒤 여기까지(새 중계를 세우는 동안) 받기를 시작한 쪽은 옛 기준을 쥐고 있다 — 닫힌 포트라 연결 오류로 끝나고 다음 시도가 이것을 얻는다.
        publishRoute()
        if (upstream == null) {
            setState(State(Phase.NOT_CONFIGURED))
        } else {
            setState(State(Phase.CHECKING))
            checkExit()
        }
    }

    /**
     * 중계가 스스로 만든 오류 응답에 싣는 짧은 페이지 — 몸이 비면 http 페이지는 하얀 빈 화면이 된다(https 의 터널 실패는 WebView 가 제 오류
     * 화면을 그린다). 목적지의 출처에서 열리므로 글만 둔다(중계가 스크립트·바깥 자원을 막는 정책도 건다). 중계의 스레드에서 불린다.
     */
    private fun relayPage(status: Int, reason: String): String {
        val text = app.getString(
            when {
                reason == "not-configured" -> R.string.relay_page_not_configured
                reason == "upstream-unreachable" || reason == "upstream-closed" -> R.string.relay_page_unreachable
                reason == "upstream-auth" -> R.string.relay_page_auth
                reason.startsWith("upstream-status-") -> R.string.relay_page_refused
                else -> R.string.relay_page_other
            },
        )
        val title = app.getString(R.string.relay_page_title)
        return "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta name=\"color-scheme\" content=\"light dark\">" +
            "<title>${html(title)}</title><style>body{font:16px/1.6 sans-serif;margin:2em 1.2em;max-width:36em}" +
            "small{opacity:.6}</style></head><body><h1 style=\"font-size:1.25em\">${html(title)}</h1><p>${html(text)}</p>" +
            "<p><small>${html("$status · $reason")}</small></p></body></html>"
    }

    private fun html(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun handleUpstream(event: LocalRelay.UpstreamEvent) {
        when (event) {
            LocalRelay.UpstreamEvent.AUTH_REJECTED -> setState(State(Phase.AUTH_REJECTED))
            LocalRelay.UpstreamEvent.UNREACHABLE -> if (state.phase != Phase.AUTH_REJECTED) setState(State(Phase.UNREACHABLE))
            LocalRelay.UpstreamEvent.REACHED -> if (state.phase == Phase.UNREACHABLE || state.phase == Phase.AUTH_REJECTED) {
                // 다시 닿았다 — 출구를 다시 본다.
                checkExit()
            }
        }
    }

    private fun setState(s: State) {
        if (s == state) return
        state = s
        listeners.forEach { it.onState(s) }
    }

    private fun token(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "WebNet"
        const val PERIOD_MS = 5 * 60_000L
        private const val CHECK_TIMEOUT_MS = 20_000
        private const val WARMUP_TIMEOUT_MS = 3_000L
    }
}
