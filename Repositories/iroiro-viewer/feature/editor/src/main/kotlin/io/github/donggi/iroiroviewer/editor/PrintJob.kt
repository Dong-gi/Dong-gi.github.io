package io.github.donggi.iroiroviewer.editor

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * 인쇄 — 엔진이 지은 인쇄용 문서를 안드로이드 인쇄(`PrintManager`)로 넘긴다. 시스템 인쇄 창에서 프린터를 고르거나 'PDF로 저장' 한다 — 이 앱에는
 * 권한이 필요 없다(PDF 는 인쇄 창이 사용자가 고른 곳에 쓴다).
 *
 * 문서는 둘 가운데 하나다 — 파일 이름의 확장자로 가린다:
 * - **PDF**(ONLYOFFICE — x2t 가 지은 것): 그 바이트를 그대로 인쇄 창에 준다([PdfAdapter]). 쪽 수와 첫 쪽의 크기는 `PdfRenderer` 로 읽는다.
 * - **HTML**(rhwp — 쪽마다 SVG, 쪽 크기는 `@page`): **화면에 붙이지 않은 WebView** 에 싣고 그 WebView 의 인쇄 어댑터를 쓴다(아래).
 *
 * 문서는 편집기와 같은 가짜 출처에서 연다(`/__io/page/<표>` — 엔진의 CSP, 글꼴은 편집기와 같은 자리에서). 자바스크립트를 켜지만 문서는
 * 스크립트를 돌리지 못한다(CSP 가 인라인을 막는다) — 앱이 글꼴이 다 내려왔는지 묻는 데만 쓴다(그 전에 찍으면 대체 글꼴로 나온다).
 * 인쇄 창이 끝나면(`onFinish`) WebView·표·파일을 치운다. 한 번에 하나만.
 */
class PrintJob(
    private val activity: Activity,
    private val engine: Engine,
    private val documents: DocumentStore,
    private val file: File,
    private val title: String,
    private val paper: PaperSizes.Match?,
    private val listener: Listener,
) {

    interface Listener {
        /** 인쇄 창을 띄웠다. */
        fun onPrintShown()

        /** 인쇄 창을 띄우지 못했다(문서를 싣지 못했다 따위) — 치웠다. */
        fun onPrintFailed()

        /** 인쇄 창이 끝났다(찍었거나 그만뒀다) — 치웠다. */
        fun onPrintDone()
    }

    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private var token: String? = null
    private var shown = false
    private var done = false
    private var polls = 0

    // MissingOnRenderProcessGone: 아래의 WebViewClient 에 있다(EditorActivity 와 같은 lint 의 오탐).
    @SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
    fun start() {
        if (file.name.endsWith(".pdf")) {
            startPdf()
            return
        }
        val t = documents.registerPage(file)
        token = t
        val server = AssetServer(activity.assets, engine, documents)
        val v = WebView(activity)
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            textZoom = 100
        }
        v.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = server.serve(request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String) {
                if (BuildConfig.DEBUG) Log.i(TAG, "print page loaded")
                if (!shown && !done) waitForFonts()
            }

            // 렌더러는 편집기와 나눠 쓴다 — 편집기 쪽도 같은 알림을 받아 스스로 알린다. 여기서는 치우기만.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (BuildConfig.DEBUG) Log.w(TAG, "print renderer gone crash=${detail.didCrash()}")
                end(failed = !shown)
                return true
            }
        }
        view = v
        v.loadUrl(engine.origin + AssetServer.IO_PAGE + t)
    }

    /** 인쇄 창이 뜨기 전에 그만둔다(화면이 끝날 때). 떠 있으면 그 창이 끝낼 때까지 둔다 — 그 사이 WebView 를 부수면 인쇄 창이 깨진다. */
    fun cancel() {
        if (!shown) end(failed = false)
    }

    /** 화면이 끝날 때 — 인쇄 창이 떠 있어도 치운다(그 창의 다음 부름은 [Adapter] 가 '취소' 로 답한다). */
    fun destroy() {
        end(failed = false)
    }

    /** 문서를 그리며 글꼴을 청하게 한 뒤(배치를 한 번 일으킨다) 다 내려올 때까지 기다린다. 너무 오래면 그대로 찍는다. */
    private fun waitForFonts() {
        val v = view ?: return
        v.evaluateJavascript(FONTS_STATUS) { status ->
            if (shown || done) return@evaluateJavascript
            polls++
            if ((status == "\"loaded\"" && polls >= MIN_POLLS) || polls >= MAX_POLLS) {
                if (BuildConfig.DEBUG) Log.i(TAG, "print fonts=$status after $polls polls")
                show()
            } else {
                main.postDelayed({ if (!shown && !done) waitForFonts() }, POLL_MS)
            }
        }
    }

    private fun show() {
        val v = view ?: return
        show(Adapter(v.createPrintDocumentAdapter(title)), paper)
    }

    /** PDF — 쪽 수와 첫 쪽의 크기를 읽은 뒤(작업 스레드) 인쇄 창을 띄운다. 읽지 못하면 깨진 PDF 다 — 띄우지 않는다. */
    private fun startPdf() {
        Thread({
            val (pages, first) = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { r ->
                        val size = if (r.pageCount > 0) r.openPage(0).use { p -> PaperSizes.of(p.width * MM_PER_POINT, p.height * MM_PER_POINT) } else null
                        r.pageCount to size
                    }
                }
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "pdf unreadable", e)
                main.post { end(failed = true) }
                return@Thread
            } catch (e: SecurityException) { // 암호가 걸린 PDF — 엔진이 짓지 않는다
                if (BuildConfig.DEBUG) Log.w(TAG, "pdf unreadable", e)
                main.post { end(failed = true) }
                return@Thread
            }
            if (BuildConfig.DEBUG) Log.i(TAG, "print pdf pages=$pages first=$first")
            main.post { if (!done) show(PdfAdapter(pages), paper ?: first) }
        }, "editor-print").start()
    }

    private fun show(adapter: PrintDocumentAdapter, paper: PaperSizes.Match?) {
        val attributes = PrintAttributes.Builder()
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        paper?.let { attributes.setMediaSize(mediaOf(it)) }
        try {
            activity.getSystemService(PrintManager::class.java).print(title, adapter, attributes.build())
        } catch (e: RuntimeException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "print failed", e)
            end(failed = true)
            return
        }
        shown = true
        listener.onPrintShown()
    }

    /** WebView 가 내준 것에 '끝' 을 더한다 — 인쇄 창이 닫히면 치운다. 치운 뒤의 부름은 '취소' 로 답한다. */
    private inner class Adapter(private val inner: PrintDocumentAdapter) : PrintDocumentAdapter() {
        override fun onStart() {
            if (!done) inner.onStart()
        }

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (done) callback.onLayoutCancelled() else inner.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            if (done) callback.onWriteCancelled() else inner.onWrite(pages, destination, cancellationSignal, callback)
        }

        override fun onFinish() {
            if (!done) inner.onFinish()
            end(failed = false)
        }
    }

    /**
     * 지은 PDF 를 그대로 내준다 — 쪽의 배치는 이미 정해졌다(용지를 바꿔도 다시 짓지 않는다 — 인쇄 창이 맞춰 줄인다). 쪽 고르기는 인쇄 창이
     * 한다(다 썼다고 답하면 고른 쪽만 찍는다). 치운 뒤의 부름은 '취소' 로 답한다.
     */
    private inner class PdfAdapter(private val pages: Int) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (done || cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("$title.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(if (pages > 0) pages else PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, oldAttributes == null)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            if (done) {
                callback.onWriteCancelled()
                return
            }
            Thread({
                // 인쇄 창의 파일 서술자는 창의 것 — 흐름을 닫지 않는다(닫으면 서술자가 닫힌다).
                val result = try {
                    FileInputStream(file).use { input ->
                        val out = FileOutputStream(destination.fileDescriptor)
                        input.copyTo(out)
                        out.flush()
                    }
                    null
                } catch (e: IOException) {
                    e.message ?: "io"
                }
                main.post {
                    when {
                        cancellationSignal?.isCanceled == true -> callback.onWriteCancelled()
                        result == null -> callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        else -> callback.onWriteFailed(result)
                    }
                }
            }, "editor-print-write").start()
        }

        override fun onFinish() {
            end(failed = false)
        }
    }

    private fun end(failed: Boolean) {
        if (done) return
        done = true
        main.removeCallbacksAndMessages(null)
        token?.let(documents::forgetPage)
        token = null
        view?.let {
            it.stopLoading()
            it.destroy()
        }
        view = null
        file.parentFile?.deleteRecursively()
        if (failed) listener.onPrintFailed() else listener.onPrintDone()
    }

    private fun mediaOf(match: PaperSizes.Match): PrintAttributes.MediaSize {
        val m = when (match.paper) {
            PaperSizes.Paper.ISO_A3 -> PrintAttributes.MediaSize.ISO_A3
            PaperSizes.Paper.ISO_A4 -> PrintAttributes.MediaSize.ISO_A4
            PaperSizes.Paper.ISO_A5 -> PrintAttributes.MediaSize.ISO_A5
            PaperSizes.Paper.JIS_B4 -> PrintAttributes.MediaSize.JIS_B4
            PaperSizes.Paper.JIS_B5 -> PrintAttributes.MediaSize.JIS_B5
            PaperSizes.Paper.NA_LETTER -> PrintAttributes.MediaSize.NA_LETTER
            PaperSizes.Paper.NA_LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
            PaperSizes.Paper.NA_TABLOID -> PrintAttributes.MediaSize.NA_TABLOID
        }
        return if (match.landscape) m.asLandscape() else m.asPortrait()
    }

    private companion object {
        const val TAG = "EditorPrint"
        const val POLL_MS = 100L
        const val MIN_POLLS = 2
        const val MAX_POLLS = 150

        /** PDF 의 쪽 크기(포인트, 1/72 인치) → mm. */
        const val MM_PER_POINT = 25.4 / 72.0

        /** 배치를 한 번 일으켜(그려야 할 글꼴을 청하게) 글꼴 묶음의 상태를 돌려준다. */
        const val FONTS_STATUS =
            "(function(){void document.documentElement.getBoundingClientRect();return document.fonts?document.fonts.status:'loaded';})()"
    }
}
