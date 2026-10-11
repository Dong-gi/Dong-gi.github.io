package io.github.donggi.iroiroviewer.editor

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.donggi.iroiroviewer.editor.EditorContract.Kind
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

/**
 * 쪽(엔진)↔앱 통로. androidx.webkit 의 `WebMessageListener` 하나 — **그 엔진의 출처, 주 틀에서 온 것만** 받는다(`addJavascriptInterface` 는
 * 쓰지 않는다 — 모든 틀에 들어가고 부른 출처를 모른다).
 *
 * 쪽이 보내는 것(글은 JSON):
 * - `{"op":"ready"}` — 문서를 열었다.
 * - `{"op":"open-failed","reason":…}` — 문서를 열지 못했다(인사 대신). 까닭의 글은 쪽이 제 화면에 적었고, `reason` 은 갈래만 싣는다 —
 *   `cancelled`(암호 물음을 닫았다 — 앱이 '다시 열기' 를 단다)·`locked`·`corrupt`·`other`([EditorRules.OpenFailure]). 앱은 '편집' 단추를 띄우지 않는다.
 * - `{"op":"position","at":…}` — 앱이 물은 읽던 자리(`where` 의 답). 꼴은 엔진마다 다르고 앱은 꼴만 본다([EditorRules.position]) —
 *   보기 → 편집이 편집 쪽 첫 쪽의 주소에 실어 넘긴다.
 * - `{"op":"back-result","handled":참/거짓}` — 앱이 물은 뒤로 가기(`back`)의 답 — 쪽이 제 메뉴·대화상자를 닫았으면 참.
 * - `{"op":"state","dirty":참/거짓}` — 고친 것이 있는가.
 * - `{"op":"paste"}` — 사용자가 '붙이기' 를 눌렀다(앱이 클립보드를 읽어 `paste-data` 로 답한다).
 * - `{"op":"keyboard"}` — 쪽이 사용자의 손 없이 연 입력칸(문서를 열 때 묻는 암호 칸)에 초점을 줬다 — 웹 쪽은 사용자의 동작 없이 자판을 올리지
 *   못하므로 앱이 올린다.
 * - 바이트 — `save-begin`·이진 덩이·`save-end`([SaveAssembler] 의 약속). 머리의 `purpose` 가 쓰임을 말한다:
 *   - `save`(없으면 이것) — 문서 저장. 형식 `kind`, 쪽이 고른 이름 `name`. 편집 모드만, 그 엔진이 쓰는 형식만.
 *   - `print` — 인쇄할 문서. 제목 `name`. HTML(rhwp — 엔진이 쪽마다 그린 것, 첫 쪽의 크기 `widthMm`·`heightMm`) 또는 `format: "pdf"`
 *     (ONLYOFFICE — x2t 가 지은 PDF). 보기 모드도 받는다([PrintJob]).
 *   - `export` — 내보낸 파일(HTML·Word 문서·ONLYOFFICE 의 '다운로드' 형식). 이름 `name` 의 확장자로 가린다([OutputNames.exportName]).
 *     보기 모드도 받는다.
 * - `{"op":"save-error"}`(엔진이 내보내지 못했다)·`{"op":"save-cancelled"}`(앱이 청한 저장을 사용자가 그만뒀다 — 암호를 묻는 창을 닫았다 따위).
 * - `{"op":"transfer-error","purpose":"print"|"export"}` — 인쇄·내보내기할 것을 짓지 못했다(보내기 전에 — ONLYOFFICE 의 x2t 가 실패했다 따위).
 *
 * 앱이 보내는 것: 덩이마다 `ack`, 끝나면 `saved`·`save-failed`, 닫기 물음의 '저장' 이 청하는 `{"op":"save"}`, 자판의 보임 `{"op":"ime"}`,
 * 클립보드 `{"op":"paste-data","text":…,"html":…}`, 읽던 자리를 묻는 `{"op":"where"}`, 뒤로 가기를 먼저 묻는 `{"op":"back"}`.
 *
 * 덩이를 디스크에 쓰는 일은 [io](차례를 지키는 한 줄)에서 하고, 답은 [main] 에서 보낸다(`JavaScriptReplyProxy` 는 UI 스레드).
 *
 * 받지 않는 저장: 보기 모드([canSave] 거짓), 그 엔진이 쓰지 않는 형식([Engine.saves]). 돌려줄 파일의 이름은 [OutputNames] 가 정한다.
 * 저장은 [outDir](Files 에 돌려줄 자리), 인쇄·내보내기는 [spoolDir](잠깐 두는 자리)에 받는다.
 */
class HostBridge(
    private val engine: Engine,
    private val outDir: File,
    private val spoolDir: File,
    private val name: String,
    private val canSave: Boolean,
    private val io: Executor,
    private val main: Handler,
    private val events: Events,
) {

    interface Events {
        fun onReady()

        /** 쪽이 문서를 열지 못했다(`open-failed`) — [reason] 은 쪽이 실은 갈래 글(없으면 null). */
        fun onOpenFailed(reason: String?)

        /** 앱이 물은 읽던 자리가 왔다([requestPosition]) — 쪽이 지은 글 그대로(없으면 null). 믿지 않는다. */
        fun onPosition(at: String?)

        /** 앱이 물은 뒤로 가기의 답([requestBack]) — 쪽이 제 메뉴·대화상자를 닫았으면 참. */
        fun onBackResult(handled: Boolean)
        fun onDirty(dirty: Boolean)
        fun onSaving()

        /**
         * 저장한 문서가 다 왔다. 화면은 그것을 Files 의 저장 자리에 반영한 뒤(없으면 곧바로) [done] 을 부른다 — 반영했으면 참. 쪽은 그 답을
         * 받아야 '저장됨' 이 된다(반영하지 못한 저장을 편집기가 저장된 것으로 두지 않게).
         */
        fun onSaved(file: File, kind: Kind, done: (Boolean) -> Unit)
        fun onSaveFailed()
        fun onSaveCancelled()

        /** 쪽의 '붙이기' — 웹 쪽은 클립보드를 읽지 못하므로 앱이 읽어 [deliverPaste] 로 준다. */
        fun onPasteRequest()

        /** 쪽이 스스로 연 입력칸(암호 칸)에 초점을 줬다 — 자판을 올린다. */
        fun onKeyboardRequest()

        /** 인쇄할 문서가 오기 시작했다(크면 몇 초 걸린다). */
        fun onPrintArriving()

        /** 인쇄할 문서가 다 왔다. [widthMm]·[heightMm] 는 첫 쪽의 크기(모르면 NaN). */
        fun onPrintReady(file: File, title: String, widthMm: Double, heightMm: Double)

        /** 내보낸 파일이 다 왔다 — 사용자가 고른 곳에 쓴다. */
        fun onExportReady(file: File, mime: String)

        /** 인쇄·내보내기를 받지 못했다. */
        fun onTransferFailed(purpose: Purpose)
    }

    enum class Purpose { SAVE, PRINT, EXPORT }

    /** 받고 있는 것 — 쓰임과 그 쓰임이 끝에 필요한 것. */
    private sealed class Incoming {
        class Save(val kind: Kind) : Incoming()
        class Print(val title: String, val widthMm: Double, val heightMm: Double, val pdf: Boolean) : Incoming()
        class Export(val name: String) : Incoming()
    }

    private var reply: JavaScriptReplyProxy? = null
    private val assembler = SaveAssembler(DocumentStore.LIMIT)

    /** 받고 있는 것(`save-begin` 이 말한 것). io 스레드에서만 읽고 쓴다. */
    private var incoming: Incoming? = null

    @SuppressLint("RequiresFeature") // isSupported() 가 둘을 함께 본다 — lint 는 다른 함수의 확인을 알아보지 못한다.
    fun install(view: WebView) {
        WebViewCompat.addWebMessageListener(view, OBJECT, setOf(engine.origin)) { _, message, sourceOrigin, isMainFrame, proxy ->
            if (!isMainFrame || !fromEngine(sourceOrigin)) return@addWebMessageListener
            reply = proxy
            if (message.type == WebMessageCompat.TYPE_ARRAY_BUFFER) {
                val bytes = message.arrayBuffer
                io.execute { answer(assembler.chunk(bytes)) }
            } else {
                onText(message.data ?: return@addWebMessageListener)
            }
        }
    }

    /** 닫기 물음의 '저장' — 쪽이 저장을 시작하게 한다(쪽이 아직 인사하지 않았으면 할 수 없다 — 거짓). */
    fun requestSave(): Boolean = post("""{"op":"save"}""")

    /** 보기 → 편집 — 쪽에 읽던 자리를 묻는다(답은 [Events.onPosition]). 쪽이 아직 말을 건 적이 없으면 물을 수 없다 — 거짓. */
    fun requestPosition(): Boolean = post("""{"op":"where"}""")

    /** 뒤로 가기 — 쪽이 열린 메뉴·대화상자를 먼저 닫게 묻는다(답은 [Events.onBackResult]). 물을 수 없으면 거짓. */
    fun requestBack(): Boolean = post("""{"op":"back"}""")

    /** 클립보드의 글(과 HTML)을 쪽에 준다 — 쪽이 진짜 붙이기처럼 paste 사건을 일으킨다(rhwp 어댑터의 10). */
    fun deliverPaste(text: String?, html: String?) {
        val o = JSONObject().put("op", "paste-data")
        if (text != null) o.put("text", text)
        if (html != null) o.put("html", html)
        post(o.toString())
    }

    /** 자판이 올라왔다/내려갔다 — 쪽의 자판 단추가 그 모양을 따른다(rhwp 어댑터의 9). */
    fun imeChanged(shown: Boolean) {
        post("""{"op":"ime","shown":$shown}""")
    }

    /** 화면이 끝날 때 — 쓰던 저장을 버린다. */
    fun close() {
        io.execute { assembler.abort() }
        reply = null
    }

    private fun fromEngine(origin: Uri): Boolean =
        origin.scheme == "https" && origin.host == engine.host && (origin.port == -1 || origin.port == 443)

    private fun onText(data: String) {
        val o = try {
            JSONObject(data)
        } catch (e: JSONException) {
            return
        }
        when (o.optString("op")) {
            "ready" -> events.onReady()
            "open-failed" -> events.onOpenFailed(o.optString("reason").take(REASON_LIMIT).ifEmpty { null })
            // 쪽이 모르면 null 을 싣는다 — optString 은 JSON 의 null 을 글자 "null" 로 준다.
            "position" -> events.onPosition(if (o.isNull("at")) null else o.optString("at"))
            "back-result" -> events.onBackResult(o.optBoolean("handled"))
            "state" -> events.onDirty(o.optBoolean("dirty"))
            "save-begin" -> {
                val id = o.optLong("id", -1)
                val size = o.optLong("size", -1)
                val sha = o.optString("sha256")
                val pageName = if (o.has("name")) o.optString("name") else null
                if (id < 0) return
                val purpose = o.optString("purpose", "save")
                val next: Incoming? = when (purpose) {
                    "save" -> Kind.of(o.optString("kind"))?.takeIf { canSave && it in engine.saves }?.let { Incoming.Save(it) }
                    "print" -> Incoming.Print(
                        OutputNames.title(pageName, name),
                        o.optDouble("widthMm", Double.NaN),
                        o.optDouble("heightMm", Double.NaN),
                        pdf = o.optString("format") == "pdf",
                    )
                    "export" -> OutputNames.exportName(pageName, name)?.let { Incoming.Export(it) }
                    else -> null
                }
                if (next == null) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "refused: purpose=${purpose.take(20)} canSave=$canSave kind=${o.optString("kind").take(20)}")
                    post("""{"op":"save-failed","id":$id}""")
                    when (purpose) {
                        "save" -> events.onSaveFailed()
                        "print" -> events.onTransferFailed(Purpose.PRINT)
                        "export" -> events.onTransferFailed(Purpose.EXPORT)
                    }
                    return
                }
                when (next) {
                    is Incoming.Save -> events.onSaving()
                    is Incoming.Print -> events.onPrintArriving()
                    is Incoming.Export -> {}
                }
                io.execute {
                    incoming = next
                    val dir = File(if (next is Incoming.Save) outDir else spoolDir, UUID.randomUUID().toString()).apply { mkdirs() }
                    val fileName = when (next) {
                        is Incoming.Save -> OutputNames.of(pageName, name, next.kind)
                        is Incoming.Print -> if (next.pdf) PRINT_PDF else PRINT_FILE
                        is Incoming.Export -> next.name
                    }
                    answer(assembler.begin(id, size, sha, File(dir, fileName)))
                }
            }
            "save-end" -> {
                val id = o.optLong("id", -1)
                io.execute { answer(assembler.end(id)) }
            }
            "save-error" -> {
                io.execute { assembler.abort() }
                events.onSaveFailed()
            }
            "save-cancelled" -> events.onSaveCancelled()
            "transfer-error" -> when (o.optString("purpose")) {
                "print" -> events.onTransferFailed(Purpose.PRINT)
                "export" -> events.onTransferFailed(Purpose.EXPORT)
            }
            "paste" -> events.onPasteRequest()
            "keyboard" -> events.onKeyboardRequest()
            "log" -> if (BuildConfig.DEBUG) Log.i(TAG, "page: ${o.optString("msg").take(500)}")
        }
    }

    /** io 스레드에서 — 한 걸음의 결과를 쪽과 화면에 알린다. */
    private fun answer(step: SaveAssembler.Step) {
        when (step) {
            is SaveAssembler.Step.Ack -> main.post { post("""{"op":"ack","id":${step.id},"seq":${step.seq}}""") }
            is SaveAssembler.Step.Done -> {
                val what = incoming
                incoming = null
                if (BuildConfig.DEBUG) Log.i(TAG, "received ${step.size} bytes for ${what?.javaClass?.simpleName}")
                main.post {
                    if (what is Incoming.Save) {
                        events.onSaved(step.file, what.kind) { ok ->
                            post(if (ok) """{"op":"saved","id":${step.id}}""" else """{"op":"save-failed","id":${step.id}}""")
                        }
                        return@post
                    }
                    post("""{"op":"saved","id":${step.id}}""")
                    when (what) {
                        is Incoming.Print -> events.onPrintReady(step.file, what.title, what.widthMm, what.heightMm)
                        is Incoming.Export -> events.onExportReady(step.file, OutputNames.exportMime(what.name))
                        else -> step.file.parentFile?.deleteRecursively()
                    }
                }
            }
            is SaveAssembler.Step.Failed -> {
                if (BuildConfig.DEBUG) Log.w(TAG, "transfer failed: ${step.why}")
                val what = incoming
                incoming = null
                main.post {
                    post("""{"op":"save-failed","id":${step.id}}""")
                    when (what) {
                        is Incoming.Print -> events.onTransferFailed(Purpose.PRINT)
                        is Incoming.Export -> events.onTransferFailed(Purpose.EXPORT)
                        else -> events.onSaveFailed()
                    }
                }
            }
        }
    }

    // 답할 손잡이는 install 이 건 듣개에서만 생기고, install 은 isSupported() 일 때만 불린다.
    @SuppressLint("RequiresFeature")
    private fun post(json: String): Boolean {
        val r = reply ?: return false
        return try {
            r.postMessage(json)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    companion object {
        /** 쪽에 보이는 주입 객체의 이름 — 엔진 어댑터가 이 이름으로 부른다(`iroiroHost.postMessage`). */
        const val OBJECT = "iroiroHost"
        private const val TAG = "EditorBridge"

        /** 인쇄할 문서를 받아 두는 이름(잠깐 두는 자리의 무작위 폴더 안) — [PrintJob] 이 확장자로 HTML·PDF 를 가린다. */
        private const val PRINT_FILE = "print.html"
        private const val PRINT_PDF = "print.pdf"

        /** `open-failed` 의 까닭 글의 상한 — 갈래 이름 하나다([EditorRules.OpenFailure]). */
        private const val REASON_LIMIT = 32

        fun isSupported(): Boolean =
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
    }
}
