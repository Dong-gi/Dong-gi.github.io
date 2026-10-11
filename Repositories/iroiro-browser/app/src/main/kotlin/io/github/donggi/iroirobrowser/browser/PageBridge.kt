package io.github.donggi.iroirobrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.adblock.Domains
import io.github.donggi.iroirobrowser.core.adblock.HideSelectors
import io.github.donggi.iroirobrowser.core.adblock.Request
import io.github.donggi.iroirobrowser.download.DownloadCenter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale

/**
 * 탭 하나의 **쪽↔앱 통로**. 쪽에서 보이지 않는 실행 세계([BridgeScripts.BRIDGE] — 모든 틀에)와 주입 객체를 두고, 거기서 오는 알림·바이트를 받는다:
 *
 * - **`blob:` 내려받기**(사용자 결정, 2026-10-05 — '지원합시다'). WebView 는 blob 주소만 알려 준다 — 그 주소의 바이트는 쪽 안에만 있다. 틀마다
 *   물어([findBlob]) 가진 틀이 덩이(512 KB)로 넘기고, 앱은 한 덩이를 쓴 뒤 다음 덩이를 청한다(메모리를 묶어 둔다).
 * - **실시간 녹화**([startRecording]) — 재생 중인 요소를 고르게 해 그 틀이 MediaRecorder 로 1초마다 넘긴다. 쪽을 다시 열지 않는다.
 * - **가로채기**([armCapture]) — 그 탭에만 쪽의 세계에 갈고리([BridgeScripts.MSE_HOOK])를 문서 시작 스크립트로 걸고(부르는 쪽이 쪽을 다시 연다),
 *   틀이 인사하면 통로를 건네게 한다. 끝나면([endCapture]) 갈고리를 걷는다. **가로채기를 건 사이트에 묶는다**(앞 판은 사이트·
 *   영상을 넘어 계속 담았다): 다른 사이트로 가는 탐색이 시작되면 갈고리를 걷어 두고([onNavigationStarted] — 다른 사이트의 문서에 넣지 않게),
 *   다른 사이트의 문서가 인사하면([onHello] — 주 틀의 출처로 가린다) 가로채기를 끝내 화면에 넘긴다(저장할지 묻는다). 통로는 그 사이트의 문서에만
 *   건넨다.
 * - **요소 고르기**([startPick] — 사용자 요청 2026-10-08, 요소 숨기기): 이 탭의 **주 틀**의 고르는 스크립트(실행 세계에 잠들어 있다)를 고르는
 *   동안만 깨워 누른 자리의 요소를 묻고, 그 알림([PickProtocol])을 고르는 화면([PickEvents] — [ElementPicker])에 넘긴다. 주 틀에 새 문서가
 *   인사하면(페이지가 바뀌었다) 고르기를 끝낸다.
 * - **유튜브 광고 가리기·건너뛰기**([YtGuard] — 사용자 결정 2026-10-11, B1·B2): 같은 실행 세계에 유튜브 출처에만 스크립트를 걸고, 그 틀이
 *   물으면([YtGuard.HELLO]) 켤지 답한다([Events.ytGuardWanted] — 광고 차단과 그 사이트의 끄기를 화면이 안다). 설정에서 돌아오면 다시 알린다
 *   ([refreshYtGuard]). 주 틀이 건너뛸 수 있게 된 단추의 자리를 알리면([YtGuard.SKIP]) 화면이 그 자리를 진짜 터치로 누르고([Events.onYtSkip]),
 *   광고가 나오는 동안을 알리면([YtGuard.AD]) 든다([ytAdShowing] — 뒤에서 재생 잇기).
 *
 * 이 기능은 실행 세계(`JS_INJECTION_IN_FRAME_AND_WORLD`)와 ArrayBuffer 알림을 아는 WebView 에서만 켠다([isSupported]). 모르면 앞 판처럼
 * blob 은 받지 못한다고 알린다.
 */
class PageBridge(private val context: Context, private val tab: Tab, private val events: Events) {

    interface Events {
        fun trace(message: String)

        /** 녹화·가로채기의 상태가 바뀌었다(막대를 다시 그린다). */
        fun onCaptureChanged(tab: Tab)

        /** 녹화가 끝났다 — 저장할 것을 넘긴다(쪽이 끝났거나 사용자가 멈췄다). */
        fun onRecordingFinished(tab: Tab, session: RecordingSession, saved: Boolean)

        /**
         * 가로채기가 사용자의 저장·취소 없이 끝났다(갈고리는 걷었다). [ask] 면 가로채기를 건 사이트를 떠났다 — 담은 것을 어떻게 할지 묻는다.
         * 아니면 탭을 닫았다 — 받은 데까지 저장한다(버릴지는 닫기 전에 화면이 물었다).
         */
        fun onCaptureEnded(tab: Tab, session: CaptureSession, ask: Boolean)

        /** 쓰기가 밀려 받은 것을 버리기 시작했다(녹화·가로채기마다 한 번). */
        fun onCaptureOverflow(tab: Tab)

        /** 이 탭에서 재생 중인 미디어의 합친 상태([playing])가 바뀌었다 — 화면 켜 두기를 다시 본다. */
        fun onPlayingChanged(tab: Tab) {}

        /** 재생 중인 영상의 제목([mediaTitle])이 바뀌었다 — 뒤에서 재생 잇기의 알림을 고친다. */
        fun onMediaTitleChanged(tab: Tab) {}

        /**
         * 주 문서가 [topHost] 인 이 탭의 유튜브 틀에서 광고를 가릴지([YtGuard.wanted] — 광고 차단이 켜져 있고 그 사이트에서 끄지 않았을 때만).
         * 메인 스레드. 기본은 끔 — 묻지 않는 화면에서는 걸지 않는다.
         */
        fun ytGuardWanted(topHost: String): Boolean = false

        /**
         * 이 탭의 유튜브 주 틀에서 광고를 건너뛸 수 있게 됐다 — 그 단추의 가운데([x]·[y] — 보이는 영역에 대한 비율)를 진짜 터치로 누른다
         * ([YtGuard.tapPoint]). 켜져 있을 때([ytGuardWanted])만 온다. 메인 스레드.
         */
        fun onYtSkip(tab: Tab, x: Double, y: Double) {}
    }

    /** 요소 고르기의 알림을 받는 곳([ElementPicker]) — 고르는 중인 그 고르기·그 주 틀의 것만 온다. 메인 스레드. */
    interface PickEvents {
        fun onPicked(info: PickProtocol.Info)

        /** 고른 요소의 자리가 바뀌었다(스크롤·배치). */
        fun onPickMoved(box: PickProtocol.Box)

        /** 누른 자리에 고를 요소가 없다(`html`·`body` 뿐이다). */
        fun onPickNone()

        /** 고른 요소가 페이지에서 사라졌다(SPA 가 그 부분을 바꿨다). */
        fun onPickGone()

        /** 선택자 후보마다 잰 결과 — 후보와 같은 차례. */
        fun onPickChecked(checks: List<HideSelectors.Check>)

        /** 고르던 문서가 사라졌다(주 틀에 새 문서가 인사했다·렌더러가 죽었다·탭을 닫는다) — 고르기를 끝낸다. */
        fun onPickEnded()
    }

    /** 쪽의 blob 하나를 넘겨주겠다는 답. */
    class BlobOffer internal constructor(val id: Int, val url: String, val size: Long, val type: String, val name: String, internal val frame: String)

    private class Frame(val id: String, val top: Boolean, val proxy: JavaScriptReplyProxy)

    private class BlobRequest(val id: Int, val done: (BlobOffer?) -> Unit, var waiting: Int) {
        var answered = false
    }

    private class Transfer(val offer: BlobOffer, val pushed: DownloadCenter.Pushed) {
        var lastChunk = System.currentTimeMillis()
    }

    private class RecordingProbe(val id: Int, val audioOnly: Boolean, val done: (String?) -> Unit) {
        val answers = ArrayList<Pair<String, JSONObject>>()
        var started = false
    }

    /** 고르는 중인 것 — 번호와 그 주 틀(그 문서의 통로)과 받는 곳. */
    private class Pick(val id: Int, val frame: String, val events: PickEvents)

    private val main = Handler(Looper.getMainLooper())
    private val frames = LinkedHashMap<String, Frame>()
    private val blobRequests = HashMap<Int, BlobRequest>()
    private val transfers = HashMap<Int, Transfer>()
    private var probe: RecordingProbe? = null
    private var nextId = 1
    private var captureChunks = 0
    private var view: WebView? = null
    private var hook: ScriptHandler? = null
    private var captureToken: String? = null

    /** 가로채기를 건 사이트 — 그 사이트의 문서에만 통로를 건넨다. */
    private var captureSite: String? = null

    /** 다른 사이트로 가는 탐색 동안 갈고리를 걷어 두었다(커밋되지 않으면 다시 건다). */
    private var hookSuspended = false

    /** 지금 주 틀의 문서의 사이트(그 인사의 출처로). */
    private var topSite: String? = null

    /** 지금 주 틀의 문서의 호스트(그 인사의 출처로) — 유튜브 광고 가리기가 그 사이트의 광고 차단 끄기를 본다. */
    private var topHost: String? = null

    /** 이 문서에서 물어 온 유튜브 틀(스크립트가 정한 이름 → 답장 객체) — 설정이 바뀌면 다시 알린다([refreshYtGuard]). */
    private val ytFrames = LinkedHashMap<String, JavaScriptReplyProxy>()
    private var blobHook: ScriptHandler? = null
    private var blobToken: String? = null
    private var blobHost: String? = null
    private var pick: Pick? = null

    var capture: CaptureSession? = null
        private set

    var recording: RecordingSession? = null
        private set

    /** 녹화나 가로채기를 하는 중이다. */
    val busy: Boolean get() = capture != null || recording != null

    /** 이 탭의 틀들에서 재생 중인 미디어(화면 켜 두기 — [ScreenWake]). 메인 스레드. */
    val playing = PlayingMedia()

    /**
     * 재생 중인 영상의 제목 — 유튜브 모바일 웹은 쪽 제목을 'YouTube' 로만 두어, 실행 세계가 화면의 제목 칸에서 읽어 보낸다(BRIDGE 의
     * `mediaTitle` — WebView 에는 미디어 세션 API 가 없다). 뒤에서 재생 잇기의 알림·잠금 화면이 쓴다([BackgroundPlay]). 쪽이 정한 글이라
     * 그대로 싣지 않고 다듬는다([BackgroundPlay.cleanText]). 모르면 빈 글. 메인 스레드.
     */
    var mediaTitle = ""
        private set

    /** 재생 중인 틀이 알린 제목을 든다 — 바뀌었으면 참. 빈 제목(칸이 없는 쪽)은 앞의 것을 지우지 않는다. */
    private fun onMediaTitle(title: String): Boolean {
        val t = BackgroundPlay.cleanText(title)
        if (t.isEmpty() || t == mediaTitle) return false
        mediaTitle = t
        return true
    }

    /**
     * 탭의 WebView 에 실행 세계·주입 객체·문서 시작 스크립트를 건다(다음 탐색부터). WebView 를 새로 만들었으면(렌더러가 죽었다) 걸어 두었던
     * 갈고리도 새 WebView 에 건다 — 앞 판은 그때 가로채기가 말없이 멈췄다.
     */
    @SuppressLint("RequiresFeature") // isSupported() 가 두 기능을 함께 본다 — lint 는 다른 함수의 확인을 알아보지 못한다.
    fun install(view: WebView) {
        if (!isSupported()) return
        val world = WebViewCompat.getExecutionWorld(view, WORLD)
        WebViewCompat.addWebMessageListener(view, OBJECT, setOf("*"), world) { _, message, sourceOrigin, isMainFrame, proxy ->
            onMessage(message, sourceOrigin, isMainFrame, proxy)
        }
        WebViewCompat.addJavaScriptOnEvent(
            view,
            BridgeScripts.BRIDGE.replace(BridgeScripts.OBJECT_PLACEHOLDER, OBJECT),
            WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
            setOf("*"),
            world,
        )
        // 유튜브 광고 가리기 — 같은 세계에(주입 객체로 앱에 묻는다), 유튜브 출처에만, 통로 뒤에(같은 틀이면 더한 차례로 돈다 — 통로의 인사가
        // 먼저 와서 주 틀의 호스트를 안다). 켤지는 그 틀이 물을 때 답한다. 걸지 못하면 B1 없이 간다.
        runCatching {
            WebViewCompat.addJavaScriptOnEvent(
                view,
                YtGuard.SCRIPT.replace(BridgeScripts.OBJECT_PLACEHOLDER, OBJECT),
                WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                YtGuard.ORIGIN_RULES,
                world,
            )
        }.onFailure { events.trace("ytguard not installed ${it.javaClass.simpleName}") }
        this.view = view
        // 새 WebView 다 — 앞 WebView 의 틀들이 알린 재생은 끝났다. 물어 온 유튜브 틀도 앞 WebView 의 것이다.
        forgetPlaying()
        ytFrames.clear()
        captureToken?.let { if (!hookSuspended) hook = addHook(view, BridgeScripts.MSE_HOOK, it) }
        blobToken?.let { blobHook = addHook(view, BridgeScripts.BLOB_HOOK, it) }
    }

    /** 쪽의 세계에 문서 시작 갈고리를 건다(다음 탐색부터) — 통로는 [isSupported] 일 때만 걸리므로 그 기능도 있다. */
    @SuppressLint("RequiresFeature")
    private fun addHook(view: WebView, script: String, token: String): ScriptHandler? =
        runCatching { WebViewCompat.addDocumentStartJavaScript(view, script.replace(BridgeScripts.TOKEN_PLACEHOLDER, token), setOf("*")) }.getOrNull()

    // ── 받기 ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun onMessage(message: WebMessageCompat, origin: Uri, isMainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        if (message.type == WebMessageCompat.TYPE_ARRAY_BUFFER) {
            onBinary(message.arrayBuffer)
            return
        }
        val o = try {
            JSONObject(message.data ?: return)
        } catch (e: JSONException) {
            return
        }
        val frame = o.optString("frame")
        when (o.optString("op")) {
            "hello" -> onHello(frame, o.optBoolean("top") && isMainFrame, origin, proxy)
            "blob-start" -> onBlobStart(o, frame)
            "blob-none" -> onBlobNone(o.optInt("id"))
            "blob-end" -> transfers.remove(o.optInt("id"))?.pushed?.finish()
            "blob-error" -> transfers.remove(o.optInt("id"))?.pushed?.fail(o.optString("why"))
            "rec-cand" -> probe?.takeIf { it.id == o.optInt("id") }?.answers?.add(frame to o)
            "rec-started" -> onRecordingStarted(o, frame)
            "rec-state" -> recording?.takeIf { it.id == o.optInt("id") }?.let {
                it.onState(o.optBoolean("paused"), o.optLong("ms"))
                events.onCaptureChanged(tab)
            }
            "rec-error" -> onRecordingError(o)
            "rec-end" -> onRecordingEnd(o.optInt("id"))
            "cap-sb" -> capture?.let {
                // 이 영상이 처음 잡힌 때의 제목 — 저장할 이름.
                it.addBuffer(frame, o.optInt("ms"), o.optInt("sb"), o.optString("mime"), tab.title)
                events.trace("capture buffer ${o.optString("mime")}")
            }
            "cap-type" -> capture?.changeType(o.optInt("sb"), o.optString("mime"))
            "cap-drm" -> capture?.let {
                it.drm = true
                events.trace("capture drm")
                events.onCaptureChanged(tab)
            }
            PickProtocol.INFO, PickProtocol.RECT, PickProtocol.CHECKED, PickProtocol.NONE, PickProtocol.GONE ->
                onPickMessage(o.optString("op"), o, frame, isMainFrame)
            "media" -> {
                val audible = o.optBoolean("audible")
                val video = o.optBoolean("video")
                // 제목은 재생 중인 틀의 것만 — 멈춘 틀·미디어 없는 틀이 지금 재생의 제목을 덮지 않게. 다음 영상으로 넘어가면 바뀐다.
                val titleChanged = (audible || video) && onMediaTitle(o.optString("title"))
                onPlaying(frame, audible, video)
                if (titleChanged) events.onMediaTitleChanged(tab)
            }
            YtGuard.HELLO -> onYtHello(o.optString("g"), origin, isMainFrame, proxy)
            YtGuard.SKIP -> onYtSkip(o, origin, isMainFrame)
            YtGuard.AD -> onYtAd(o.optBoolean("on"), origin, isMainFrame)
        }
    }

    private fun onBinary(bytes: ByteArray) {
        if (bytes.size < 5) return
        val head = ByteBuffer.wrap(bytes)
        val kind = head.get(0).toInt()
        val id = head.getInt(1)
        when (kind) {
            KIND_BLOB -> onBlobChunk(id, bytes)
            KIND_RECORDING -> if (recording?.takeIf { it.id == id }?.append(bytes, 5) == true) overflowed()
            KIND_CAPTURE -> if (bytes.size >= 13) {
                if (++captureChunks <= 3) events.trace("capture data sb=$id bytes=${bytes.size - 13} session=${capture != null}")
                if (capture?.append(id, head.getDouble(5), bytes, 13) == true) overflowed()
            }
        }
    }

    private fun overflowed() {
        events.trace("capture overflow")
        events.onCaptureOverflow(tab)
    }

    /**
     * 틀이 문서를 시작했다 — 주 틀의 새 문서면 앞 문서의 틀들을 잊고 그 문서의 사이트를 적는다(인사의 출처로 — 쪽이 꾸밀 수 없다). 가로채기를 건
     * 사이트를 떠났으면 가로채기를 끝낸다. 가로채기 중이면 그 사이트의 문서에만 통로를 건네게 한다.
     */
    private fun onHello(frame: String, top: Boolean, origin: Uri, proxy: JavaScriptReplyProxy) {
        if (top) {
            // 고르던 문서가 바뀌었다 — 새 문서의 고르는 스크립트는 아무것도 모른다.
            endPick()
            frames.clear()
            // 앞 문서의 재생은 끝났다(그 틀들이 떠나며 알렸어도, 못 알렸어도).
            forgetPlaying()
            topSite = siteOf(origin.host.orEmpty())
            topHost = origin.host.orEmpty()
            // 앞 문서의 유튜브 틀은 문서와 함께 사라졌다(새 문서의 틀은 이 인사 뒤에 묻는다).
            ytFrames.clear()
            // 녹화하던 쪽이 바뀌었다 — 받은 데까지 저장한다(마지막 1초는 잃는다).
            recording?.let { r ->
                if (!r.stopping) {
                    events.trace("recording page-changed")
                    finishRecording(r, save = true)
                }
            }
            val site = captureSite
            if (capture != null && site != null && topSite != site) {
                events.trace("capture left site")
                endCapture()?.let { events.onCaptureEnded(tab, it, ask = true) }
            }
        }
        frames[frame] = Frame(frame, top, proxy)
        events.trace("bridge hello top=$top frames=${frames.size}")
        captureToken?.takeIf { topSite == captureSite }?.let { post(proxy, JSONObject().put("op", "cap-arm").put("token", it)) }
        blobToken?.let { post(proxy, JSONObject().put("op", "blob-arm").put("token", it)) }
    }

    /** 통로는 [isSupported] 일 때만 걸리므로 답장 객체가 있으면 그 기능도 있다(lint 는 다른 함수의 확인을 알아보지 못한다). */
    @SuppressLint("RequiresFeature")
    private fun post(proxy: JavaScriptReplyProxy, o: JSONObject) {
        runCatching { proxy.postMessage(o.toString()) }
    }

    private fun post(frame: String, o: JSONObject) {
        frames[frame]?.let { post(it.proxy, o) }
    }

    // ── blob ─────────────────────────────────────────────────────────────────────────────────────────────

    /** 이 탭의 틀들에 [url] 의 blob 을 묻는다 — 가진 틀의 답(크기·형식·이름) 또는 null(아무도 없다·시간 넘김). 메인 스레드. */
    fun findBlob(url: String, done: (BlobOffer?) -> Unit) {
        if (frames.isEmpty()) {
            done(null)
            return
        }
        val id = nextId++
        val req = BlobRequest(id, done, frames.size)
        blobRequests[id] = req
        val ask = JSONObject().put("op", "blob").put("id", id).put("url", url)
        frames.values.forEach { post(it.proxy, ask) }
        main.postDelayed({
            if (blobRequests.remove(id) != null && !req.answered) {
                req.answered = true
                done(null)
            }
        }, BLOB_ANSWER_MS)
    }

    private fun onBlobStart(o: JSONObject, frame: String) {
        val id = o.optInt("id")
        val req = blobRequests[id]
        if (req == null || req.answered) {
            post(frame, JSONObject().put("op", "stop").put("id", id))
            return
        }
        req.answered = true
        blobRequests.remove(id)
        req.done(BlobOffer(id, "", o.optLong("size", -1), o.optString("type"), o.optString("name"), frame))
    }

    private fun onBlobNone(id: Int) {
        val req = blobRequests[id] ?: return
        if (--req.waiting <= 0 && !req.answered) {
            req.answered = true
            blobRequests.remove(id)
            req.done(null)
        }
    }

    /** 받기로 했다 — 덩이를 청하기 시작한다. */
    fun acceptBlob(offer: BlobOffer, pushed: DownloadCenter.Pushed) {
        val t = Transfer(offer, pushed)
        transfers[offer.id] = t
        post(offer.frame, JSONObject().put("op", "next").put("id", offer.id))
        watch(offer.id)
    }

    fun declineBlob(offer: BlobOffer) {
        post(offer.frame, JSONObject().put("op", "stop").put("id", offer.id))
    }

    private fun onBlobChunk(id: Int, bytes: ByteArray) {
        val t = transfers[id] ?: return
        t.lastChunk = System.currentTimeMillis()
        if (t.pushed.cancelled) {
            transfers.remove(id)
            post(t.offer.frame, JSONObject().put("op", "stop").put("id", id))
            t.pushed.fail("")
            return
        }
        t.pushed.write(bytes, 5, bytes.size - 5) {
            if (transfers[id] === t) post(t.offer.frame, JSONObject().put("op", "next").put("id", id))
        }
    }

    /** 덩이가 한동안 오지 않으면(쪽이 바뀌었다) 실패로. */
    private fun watch(id: Int) {
        main.postDelayed({
            val t = transfers[id] ?: return@postDelayed
            if (System.currentTimeMillis() - t.lastChunk > BLOB_STALL_MS) {
                transfers.remove(id)
                t.pushed.fail(context.getString(R.string.blob_stalled))
            } else {
                watch(id)
            }
        }, BLOB_STALL_MS / 2)
    }

    // ── 녹화 ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 재생 중인 요소를 녹화한다 — 틀마다 물어 가장 알맞은 것(재생 중·영상·큰 것)을 고른다. [done] 은 시작했으면 null, 아니면 까닭. 메인 스레드.
     */
    fun startRecording(audioOnly: Boolean, done: (String?) -> Unit) {
        if (recording != null || capture != null) {
            done(context.getString(R.string.capture_busy))
            return
        }
        if (frames.isEmpty()) {
            done(context.getString(R.string.capture_nothing_playing))
            return
        }
        val id = nextId++
        val p = RecordingProbe(id, audioOnly, done)
        probe = p
        val ask = JSONObject().put("op", "rec-probe").put("id", id)
        frames.values.forEach { post(it.proxy, ask) }
        main.postDelayed({ pickRecording(p) }, PROBE_MS)
    }

    private fun pickRecording(p: RecordingProbe) {
        if (probe !== p) return
        events.trace("recording candidates ${p.answers.map { it.second.toString() }}")
        val best = p.answers.filter { it.second.optBoolean("has") }.maxWithOrNull(
            compareBy<Pair<String, JSONObject>>({ it.second.optBoolean("playing") }, { !p.audioOnly && it.second.optBoolean("video") },
                { it.second.optDouble("area") }),
        )
        when {
            best == null || !best.second.optBoolean("playing") -> {
                probe = null
                p.done(context.getString(R.string.capture_nothing_playing))
            }
            best.second.optBoolean("drm") -> {
                probe = null
                p.done(context.getString(R.string.capture_drm))
            }
            else -> {
                post(best.first, JSONObject().put("op", "rec-start").put("id", p.id).put("audioOnly", p.audioOnly))
                main.postDelayed({
                    if (probe === p && !p.started) {
                        probe = null
                        p.done(recordFailure("timeout"))
                    }
                }, START_MS)
            }
        }
    }

    private fun onRecordingStarted(o: JSONObject, frame: String) {
        val p = probe ?: return
        if (p.id != o.optInt("id")) return
        p.started = true
        probe = null
        val dir = File(captureRoot(context), "rec-${System.currentTimeMillis()}")
        // 녹화하는 동안은 '남은 것' 이 아니다(되살리기가 건너뛴다).
        CaptureRegistry.claim(dir)
        recording = RecordingSession(dir, p.id, frame, o.optString("mime"), p.audioOnly, o.optBoolean("video"), tab.title)
        events.trace("recording started ${o.optString("mime")} video=${o.optBoolean("video")} audio=${o.optBoolean("audio")}")
        events.onCaptureChanged(tab)
        p.done(null)
    }

    private fun onRecordingError(o: JSONObject) {
        val why = o.optString("why")
        val p = probe
        if (p != null && p.id == o.optInt("id")) {
            probe = null
            val message = if (why == "drm") context.getString(R.string.capture_drm) else recordFailure(why)
            events.trace("recording error $why")
            p.done(message)
            return
        }
        recording?.takeIf { it.id == o.optInt("id") }?.let {
            events.trace("recording error $why")
            finishRecording(it, save = true)
        }
    }

    /** 녹화를 시작하지 못한 까닭 — 쪽이 준 이름(DOMException 의 이름 따위)을 읽는 말로('(SecurityError)' 가 그대로 나왔다). */
    private fun recordFailure(why: String): String {
        if (why == "none") return context.getString(R.string.capture_nothing_playing)
        val reason = when (why) {
            "timeout" -> R.string.record_why_timeout
            "SecurityError" -> R.string.record_why_security
            "NotSupportedError" -> R.string.record_why_unsupported
            "no-track" -> R.string.record_why_no_track
            "InvalidStateError" -> R.string.record_why_state
            else -> R.string.record_why_other
        }
        return context.getString(R.string.capture_record_failed, context.getString(reason))
    }

    private fun onRecordingEnd(id: Int) {
        val r = recording?.takeIf { it.id == id } ?: return
        events.trace("recording ended bytes=${r.bytes}")
        finishRecording(r, save = !r.closed)
    }

    /** 녹화를 멈춘다 — [save] 면 저장으로 넘긴다(쪽이 마지막 조각을 보낸 뒤). */
    fun stopRecording(save: Boolean) {
        val r = recording ?: return
        if (r.stopping) return
        r.stopping = true
        if (!save) r.closed = true
        post(r.frame, JSONObject().put("op", "rec-stop").put("id", r.id))
        events.onCaptureChanged(tab)
        // 쪽이 답하지 않으면(틀이 사라졌다) 받은 데까지로 끝낸다.
        main.postDelayed({ if (recording === r) finishRecording(r, save) }, STOP_MS)
    }

    private fun finishRecording(r: RecordingSession, save: Boolean) {
        if (recording !== r) return
        recording = null
        r.stopping = true
        events.onRecordingFinished(tab, r, save && r.bytes > 0)
        events.onCaptureChanged(tab)
    }

    /** 렌더러가 죽어 쪽이 사라졌다 — 녹화는 받은 데까지 저장한다(쪽이 더는 넘기지 못한다). 가로채기는 다시 연 쪽에서 잇는다([install]). */
    fun onPageGone() {
        endPick()
        frames.clear()
        forgetPlaying()
        ytFrames.clear()
        recording?.let { r ->
            if (!r.stopping) {
                events.trace("recording page-gone")
                finishRecording(r, save = true)
            }
        }
    }

    // ── 재생 중(화면 켜 두기) ─────────────────────────────────────────────────────────────────────────────

    /** 틀이 재생 중인 미디어를 알렸다(바뀔 때·재생 중이면 10초마다·물으면) — 인사한 틀의 것만 받는다(앞 문서의 늦은 알림은 버린다). */
    private fun onPlaying(frame: String, audible: Boolean, video: Boolean) {
        if (frame !in frames) return
        if (playing.report(frame, audible, video, SystemClock.elapsedRealtime())) events.onPlayingChanged(tab)
    }

    /** 이 탭의 틀들에 재생 중인 것을 다시 묻는다 — 탭을 고를 때·앱으로 돌아올 때(뒤에 있던 동안 다시 알림이 늦었을 수 있다). */
    fun askPlaying() {
        val ask = JSONObject().put("op", "media-ask")
        frames.values.forEach { post(it.proxy, ask) }
    }

    /**
     * 뒤에서 재생 잇기의 조작([BackgroundPlay] — 알림·잠금 화면·이어폰 단추) — 이 탭의 틀들에 미디어를 멈추거나([play] 거짓) 다시 틀라고 한다.
     * 재생 중이거나 멈춰 둔 것을 가진 틀만 움직인다(BRIDGE 의 media-control).
     */
    fun mediaControl(play: Boolean) {
        val msg = JSONObject().put("op", "media-control").put("play", play)
        frames.values.forEach { post(it.proxy, msg) }
    }

    private fun forgetPlaying() {
        mediaTitle = ""
        ytAdShowing = false
        if (playing.clear(SystemClock.elapsedRealtime())) events.onPlayingChanged(tab)
    }

    // ── 유튜브 광고 가리기(B1) ────────────────────────────────────────────────────────────────────────────────

    /**
     * 유튜브 틀의 스크립트가 켤지 묻는다 — 그 틀에 답한다. 출처는 WebView 가 준 것이라 쪽이 꾸밀 수 없고, 스크립트를 건 출처 규칙과 같은
     * 판단([YtGuard.isYouTubeOrigin])으로 한 번 더 본다. 사이트는 **주 문서의 것**이다 — 주 틀이면 그 출처, 퍼가기 틀이면 주 틀의 인사가 알린
     * 호스트(아직 모르면 탭의 주소).
     */
    private fun onYtHello(id: String, origin: Uri, isMainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        if (!YtGuard.isYouTubeOrigin(origin.scheme, origin.host, origin.port)) return
        if (id.isNotEmpty()) {
            ytFrames.remove(id)
            ytFrames[id] = proxy
            // 퍼가기가 많은 쪽에서 무한히 늘지 않게 — 오래된 것부터 놓는다(놓친 틀은 다음 문서·다시 열 때 다시 묻는다).
            while (ytFrames.size > MAX_YT_FRAMES) ytFrames.remove(ytFrames.keys.first())
        }
        val host = if (isMainFrame) origin.host.orEmpty() else ytTopHost()
        val on = events.ytGuardWanted(host)
        events.trace("ytguard ${if (on) "on" else "off"} main=$isMainFrame frames=${ytFrames.size}")
        post(proxy, ytGuardMessage(on))
    }

    /**
     * 켤지를 이 탭의 유튜브 틀들에 다시 알린다 — 광고 차단 설정(전체 켜기·끄기, 끈 사이트 목록)을 바꾸고 돌아왔을 때. 쪽을 다시 열지 않아도
     * 곧바로 듣는다(메뉴의 사이트별 끄기는 쪽을 다시 연다).
     */
    fun refreshYtGuard() {
        if (ytFrames.isEmpty()) return
        val message = ytGuardMessage(events.ytGuardWanted(ytTopHost()))
        ytFrames.values.forEach { post(it, message) }
    }

    private fun ytTopHost(): String = topHost ?: Request.hostOf(tab.url)

    /** 마지막으로 건너뛰기를 누르게 한 때(elapsedRealtime) — [YtGuard.SKIP_MIN_GAP_MS]. */
    private var ytSkipAt = 0L

    /**
     * 유튜브의 주 틀이 건너뛸 수 있게 된 광고 단추의 자리를 알렸다 — 켜져 있으면 화면이 그 자리를 진짜 터치로 누른다. 주 틀만 받는다(자리가 주
     * 틀의 보이는 영역에 대한 비율이다). 출처는 WebView 가 준 것이라 쪽이 꾸밀 수 없다.
     */
    private fun onYtSkip(o: JSONObject, origin: Uri, isMainFrame: Boolean) {
        if (!isMainFrame || !YtGuard.isYouTubeOrigin(origin.scheme, origin.host, origin.port)) return
        if (!events.ytGuardWanted(origin.host.orEmpty())) return
        val now = SystemClock.elapsedRealtime()
        if (now - ytSkipAt < YtGuard.SKIP_MIN_GAP_MS) return
        ytSkipAt = now
        events.onYtSkip(tab, o.optDouble("x", Double.NaN), o.optDouble("y", Double.NaN))
    }

    /**
     * 유튜브의 주 틀에서 광고가 나오는 중인가([YtGuard.AD]) — 광고는 음소거되어(B1) '소리가 나는 재생' 이 아니어도 앱이 뒤로 가면 재생을 잇는다
     * ([BackgroundPlay.wanted]). 새 문서·렌더러가 죽으면 잊는다([forgetPlaying]). 메인 스레드.
     */
    var ytAdShowing = false
        private set

    private fun onYtAd(on: Boolean, origin: Uri, isMainFrame: Boolean) {
        if (!isMainFrame || !YtGuard.isYouTubeOrigin(origin.scheme, origin.host, origin.port)) return
        ytAdShowing = on
    }

    private fun ytGuardMessage(on: Boolean): JSONObject =
        JSONObject().put("op", YtGuard.GUARD).put("on", on).put("label", context.getString(R.string.ytguard_label))

    // ── 가로채기 ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 가로채기를 건다 — 쪽의 세계에 갈고리를 걸고(다음 탐색부터) 세션을 연다. 부르는 쪽이 쪽을 다시 연다. [site] 는 지금 쪽의 사이트(등록 도메인
     * 어림) — 이 사이트를 떠나면 가로채기가 끝난다.
     */
    fun armCapture(view: WebView, site: String): CaptureSession? {
        if (!isSupported() || capture != null || recording != null) return null
        val token = randomName("t")
        hook = addHook(view, BridgeScripts.MSE_HOOK, token)
        this.view = view
        captureToken = token
        captureSite = site
        hookSuspended = false
        val dir = File(captureRoot(context), "cap-${System.currentTimeMillis()}")
        // 폴더가 생기기 전에 — 되살리기가 쓰는 중인 것을 남은 것으로 보지 않게.
        CaptureRegistry.claim(dir)
        val session = CaptureSession(dir, site) { events.trace(it) }
        captureChunks = 0
        capture = session
        events.trace("capture armed $site")
        events.onCaptureChanged(tab)
        return session
    }

    /** 가로채기를 끝낸다 — 갈고리를 걷는다(지금 문서에 걸린 것은 다음 탐색까지 남는다 — 받은 것은 부르는 쪽이 저장하거나 버린다). */
    fun endCapture(): CaptureSession? {
        val s = capture ?: return null
        hook?.let { runCatching { it.remove() } }
        hook = null
        captureToken = null
        captureSite = null
        hookSuspended = false
        capture = null
        s.closed = true
        events.onCaptureChanged(tab)
        return s
    }

    /**
     * 주 틀의 다른 문서로 가는 탐색이 시작됐다(또는 넘겨졌다) — 가로채기를 건 사이트 밖으로 가면 갈고리를 걷어 둔다(다른 사이트의 문서에 넣지
     * 않게). 커밋되지 않거나 그 사이트로 돌아오면 다시 건다([onNavigationFinished]).
     */
    fun onNavigationStarted(url: String) {
        val site = captureSite ?: return
        if (siteOf(Request.hostOf(url)) == site) {
            resumeHook()
            return
        }
        if (hookSuspended) return
        hook?.let { runCatching { it.remove() } }
        hook = null
        hookSuspended = true
        events.trace("capture hook suspended")
    }

    /** 그 탐색이 끝났다 — 다른 사이트로 커밋했으면 그대로 둔다(그 문서의 인사가 가로채기를 끝낸다). 아니면 걷어 둔 갈고리를 다시 건다. */
    fun onNavigationFinished(committedUrl: String?) {
        val site = captureSite ?: return
        if (committedUrl != null && siteOf(Request.hostOf(committedUrl)) != site) return
        resumeHook()
    }

    private fun resumeHook() {
        if (!hookSuspended) return
        val token = captureToken ?: return
        val v = view ?: return
        hook = addHook(v, BridgeScripts.MSE_HOOK, token)
        hookSuspended = false
        events.trace("capture hook resumed")
    }

    /**
     * blob 갈고리를 건다 — 넘겨받지 못한 쪽에서 사용자가 고를 때만, 이 탭에(다음 탐색부터 — 부르는 쪽이 쪽을 다시 연다). 그 사이트를 떠나면 걷는다
     * ([onMainFrame]).
     */
    fun armBlob(view: WebView, host: String) {
        if (!isSupported()) return
        disarmBlob()
        val token = randomName("b")
        blobHook = addHook(view, BridgeScripts.BLOB_HOOK, token)
        blobToken = token
        blobHost = host
        events.trace("blob armed $host")
    }

    private fun disarmBlob() {
        blobHook?.let { runCatching { it.remove() } }
        blobHook = null
        blobToken = null
        blobHost = null
    }

    /** 주 문서가 바뀐다 — blob 갈고리를 건 사이트를 떠났으면 걷는다. */
    fun onMainFrame(host: String) {
        val armed = blobHost ?: return
        if (host != armed) {
            disarmBlob()
            events.trace("blob disarmed")
        }
    }

    // ── 요소 고르기 ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * 요소 고르기를 시작한다 — 이 탭의 **주 틀**에서 잠들어 있던 고르는 스크립트를 깨운다(그동안만 듣개·시계를 건다 — 페이지의 세계는 건드리지
     * 않는다). 주 틀의 통로가 아직 인사하지 않았으면(페이지를 여는 중) false. 하던 고르기는 끝낸다.
     */
    fun startPick(events: PickEvents): Boolean {
        stopPick()
        val top = frames.values.firstOrNull { it.top } ?: return false
        val p = Pick(nextId++, top.id, events)
        pick = p
        post(top.proxy, JSONObject().put("op", PickProtocol.START).put("id", p.id))
        return true
    }

    /** 이 자리(보이는 영역에 대한 비율)의 요소를 고른다. */
    fun pickAt(x: Double, y: Double) = sendPick(PickProtocol.AT) { it.put("x", x).put("y", y) }

    /** 감싼 요소로(넓게). */
    fun pickWider() = sendPick(PickProtocol.WIDER) {}

    /** 앞서 고른 안쪽 요소로(좁게). */
    fun pickNarrower() = sendPick(PickProtocol.NARROWER) {}

    /** [x]·[y] 자리의 스크롤 상자(없으면 문서)를 [dx]·[dy](보이는 영역에 대한 비율)만큼 민다. */
    fun pickScroll(x: Double, y: Double, dx: Double, dy: Double) =
        sendPick(PickProtocol.SCROLL) { it.put("x", x).put("y", y).put("dx", dx).put("dy", dy) }

    /** 선택자 후보마다 지금 페이지에서 맞는 수와 고른 요소가 그 안에 있는지 잰다 — 답은 [PickEvents.onPickChecked]. */
    fun checkSelectors(selectors: List<String>) = sendPick(PickProtocol.CHECK) { it.put("sels", JSONArray(selectors)) }

    /** 고르기를 끝낸다 — 스크립트가 듣개·시계를 걷는다. 고르는 중이 아니면 아무것도 하지 않는다. */
    fun stopPick() {
        val p = pick ?: return
        pick = null
        post(p.frame, JSONObject().put("op", PickProtocol.STOP).put("id", p.id))
    }

    private inline fun sendPick(op: String, fill: (JSONObject) -> Unit) {
        val p = pick ?: return
        val o = JSONObject().put("op", op).put("id", p.id)
        fill(o)
        post(p.frame, o)
    }

    /** 고르는 스크립트의 알림 — 고르는 중인 그 고르기(번호)·그 주 틀의 것만 받는다. */
    private fun onPickMessage(op: String, o: JSONObject, frame: String, isMainFrame: Boolean) {
        val p = pick ?: return
        if (!isMainFrame || frame != p.frame || o.optInt("id") != p.id) return
        when (op) {
            PickProtocol.INFO -> {
                val info = PickProtocol.info(treeOf(o))
                if (info != null) p.events.onPicked(info) else p.events.onPickNone()
            }
            PickProtocol.RECT -> PickProtocol.box(tree(o.opt("rect")))?.let { p.events.onPickMoved(it) }
            PickProtocol.CHECKED -> p.events.onPickChecked(PickProtocol.checks(tree(o.opt("res"))).orEmpty())
            PickProtocol.NONE -> p.events.onPickNone()
            PickProtocol.GONE -> p.events.onPickGone()
        }
    }

    /** 고르던 문서가 사라졌다 — 고르는 쪽에 알린다(그 문서의 스크립트는 문서와 함께 사라졌다). */
    private fun endPick() {
        val p = pick ?: return
        pick = null
        p.events.onPickEnded()
    }

    /**
     * 탭을 닫는다(또는 화면이 사라진다). 남은 녹화·가로채기는 [keep] 이면 파일째 남기고(다음에 뜰 때 저장할지 묻는다 — 화면이 사라질 때), 아니면
     * 받은 데까지 저장으로 넘긴다 — 쪽이 바뀔 때와 같다. 버릴지는 닫기 전에 화면이 물어 [discardAll] 로 정한다(앞 판은 묻지 않고 버렸다).
     */
    fun dispose(keep: Boolean = false) {
        // 고르기는 화면이 탭을 닫기 전에 끝낸다 — 여기서는 조용히 잊는다(알리면 '페이지가 바뀌었다' 가 된다).
        pick = null
        // 재생도 조용히 — 화면이 다른 탭을 고르며 다시 본다.
        playing.clear(SystemClock.elapsedRealtime())
        ytFrames.clear()
        recording?.let { r ->
            if (keep) {
                recording = null
                r.release()
                CaptureRegistry.release(r.dir)
            } else {
                finishRecording(r, save = true)
            }
        }
        endCapture()?.let { s ->
            if (keep) {
                s.release()
                CaptureRegistry.release(s.dir)
            } else {
                events.onCaptureEnded(tab, s, ask = false)
            }
        }
        probe = null
        transfers.values.forEach { it.pushed.fail("") }
        transfers.clear()
        view = null
    }

    /** 녹화·가로채기를 버린다 — 사용자가 탭을 닫으며 '버리고 닫기' 를 골랐다. */
    fun discardAll() {
        recording?.let { r ->
            recording = null
            r.discard()
            CaptureRegistry.release(r.dir)
            events.onCaptureChanged(tab)
        }
        endCapture()?.let { s ->
            s.discard()
            CaptureRegistry.release(s.dir)
        }
    }

    companion object {
        private const val KIND_BLOB = 1
        private const val KIND_RECORDING = 2
        private const val KIND_CAPTURE = 3
        private const val BLOB_ANSWER_MS = 6_000L
        private const val BLOB_STALL_MS = 20_000L
        private const val PROBE_MS = 800L
        private const val START_MS = 5_000L
        private const val STOP_MS = 6_000L

        /** 다시 알릴 유튜브 틀의 수 — 한 문서에 퍼가기가 이보다 많으면 오래된 것부터 놓는다. */
        private const val MAX_YT_FRAMES = 32

        /** 실행 세계와 주입 객체의 이름 — 프로세스마다 무작위(쪽은 그 세계를 볼 수 없지만 고정된 이름을 두지 않는다). */
        private val WORLD = randomName("w")
        private val OBJECT = randomName("o")

        private fun randomName(prefix: String): String {
            val b = ByteArray(9)
            SecureRandom().nextBytes(b)
            return prefix + b.joinToString("") { "%02x".format(it) }
        }

        /** 사이트 — 등록 도메인 어림(`m.example.com` 과 `www.example.com` 은 같은 사이트). */
        fun siteOf(host: String): String = Domains.registrable(host.lowercase(Locale.ROOT))

        /** JSON 을 읽은 나무(맵·목록·수·글·참거짓) — [PickProtocol] 은 org.json 없이 이것을 읽는다(JVM 시험에서 돌게). */
        private fun tree(v: Any?): Any? = when (v) {
            is JSONObject -> treeOf(v)
            is JSONArray -> (0 until v.length()).map { tree(v.opt(it)) }
            JSONObject.NULL -> null
            else -> v
        }

        private fun treeOf(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { tree(o.opt(it)) }

        fun isSupported(): Boolean =
            WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

        /**
         * 가로채기·녹화의 임시 폴더(앱 안 — 백업에서 빠진다). 앱이 떠도 지우지 않는다 — 앞 프로세스가 저장하지 못하고 끝났으면 저장할지 묻는다
         * ([CaptureRecovery]).
         */
        fun captureRoot(context: Context): File = File(context.noBackupFilesDir, "capture")
    }
}
