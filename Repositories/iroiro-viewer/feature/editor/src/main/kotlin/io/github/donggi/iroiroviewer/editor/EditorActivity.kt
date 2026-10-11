package io.github.donggi.iroiroviewer.editor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import io.github.donggi.iroiroviewer.editor.EditorContract.Kind
import io.github.donggi.iroiroviewer.io.R as IoR
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 문서 하나를 보고 고치는 화면 — WebView 하나에 엔진(웹 편집기)을 띄운다.
 *
 * 차례: 이 앱이 띄웠는지 본다([callerAllowed]) → 넘겨받은 문서를 앱 캐시에 떠 둔다([DocumentStore]) → 엔진의 첫 쪽을 가짜 출처로
 * 연다([AssetServer]) → 쪽이 문서를 받아 그린다 → 고치고 저장하면 덩이로 받아([HostBridge]) 저장 자리에 반영한다(파일 쪽 — [EditorContract]).
 *
 * **따로 된 프로세스(`:editor`)에서 돈다**(매니페스트). 파일 쪽과 나누는 것은 저장 자리(제공자 호출)뿐이다.
 *
 * 그 밖에 쪽이 청하는 것 — 인쇄([PrintJob] — 안드로이드 인쇄 창, 'PDF로 저장' 포함), 내보내기(시스템의 '저장할 곳 고르기' 로 사용자가 고른
 * 곳에 쓴다), 문서의 링크(주소를 보여 주고 물은 뒤 브라우저 앱으로 — 이 앱은 인터넷이 없다), 그림 넣기, 붙이기.
 *
 * **보기 모드가 오피스·한글 문서의 뷰어다**(파일 목록이 문서를 누르면 여기로 온다). 파일 쪽이 저장 자리를 함께 주면 문서를 연
 * 뒤 오른쪽 아래에 '편집' 단추가 뜨고, 누르면 같은 문서를 편집 모드로 다시 연다([switchToEdit] — 원본을 다시 읽어 연 뒤 바뀌었으면 지금 파일로,
 * 읽던 자리를 편집 쪽에 넘긴다). 그 곁의 ⋮ 가 파일 이름과 '다른 앱으로 열기' 를 든다(이 앱의 막대가 없는 화면이라). 보기 모드에서는
 * 읽는 동안 화면을 켜 둔다(파일 쪽의 설정 — [EditorContract.EXTRA_KEEP_SCREEN_MINUTES]). 떠 있는 단추와 오류 화면의 단추는 [EditorRules] 가 정한다.
 *
 * 뒤로 가기는 먼저 쪽에 묻는다 — 엔진의 메뉴·대화상자가 열려 있으면 그것부터 닫는다([onBack]).
 *
 * 화면은 돌리기·밤 모드·글꼴 크기 따위에도 다시 만들지 않는다(매니페스트의 configChanges) — 다시 만들면 편집기와 고친 것이 사라진다. 프로세스가
 * 죽으면 고친 것도 사라진다(편집기의 자동 저장은 1단계에서 정한다).
 */
class EditorActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var io: ExecutorService
    private lateinit var root: FrameLayout
    private lateinit var documents: DocumentStore
    private var web: WebView? = null
    private var bridge: HostBridge? = null
    private var overlay: LinearLayout? = null

    private lateinit var kind: Kind
    private lateinit var engine: Engine
    private var mode = EditorContract.MODE_EDIT
    private var name = "document"

    private var dirty = false
    private var saving = false
    private var closeAfterSave = false

    /** 마지막으로 저장한 것 — 끝날 때 결과로 돌려준다(저장 자리가 없는 시험대). */
    private var saved: Pair<File, Kind>? = null

    /**
     * Files 의 저장 자리([EditorContract.EXTRA_SAVE_SLOT]) — 있으면 저장할 때마다 거기에 반영한다. 보기 모드에서도 올 수 있다 — 그때는 '편집'
     * 단추로 편집 모드로 바꿀 수 있다는 뜻이다(받은 사본처럼 고쳐 둘 곳이 없는 문서에는 오지 않는다).
     */
    private var slot: Uri? = null

    /** 떠 둔 문서의 표([DocumentStore]) — 보기에서 편집으로 바꿀 때·다시 열 때 원본과 견주는 사본([recopy]). */
    private var token: String? = null

    /** 떠 있는 단추 묶음([refreshFloatingBar]) — ⋮ 와 주 단추('편집'·'다시 열기'·'다른 앱으로 열기'). 무엇을 띄울지는 [EditorRules.floating]. */
    private var floatingBar: LinearLayout? = null

    /** ⋮ 가 연 메뉴 — 단추 묶음을 다시 지을 때 함께 걷는다(떨어져 나간 단추에 매달려 남지 않게). */
    private var morePopup: PopupMenu? = null

    /** 쪽이 문서를 열었다고 인사했다(`ready`). */
    private var opened = false

    /** 쪽이 문서를 열지 못했다고 알린 까닭(`open-failed`) — 늦게 온 인사로 '편집' 단추를 띄우지 않는다. */
    private var openFailure: EditorRules.OpenFailure? = null

    /** 보기 → 편집·다시 열기를 준비하는 중(원본을 다시 읽고 읽던 자리를 묻는 동안) — 단추를 걷고 두 번 누르기를 막는다. */
    private var reloading = false

    /** 지금 덮개가 보이는 글과 단추([showOverlay]) — 밤 모드·글꼴 크기가 바뀌면 새 모양으로 다시 짓는다([onConfigurationChanged]). */
    private var overlaySpec: OverlaySpec? = null

    /** 마지막으로 받은 설정 — 무엇이 바뀌었는지 가른다([onConfigurationChanged]). */
    private var lastConfig: Configuration? = null

    /** '새 파일로 저장했습니다' — 이 화면에서 처음 알리는 이름만([EditorRules.NewFileNotices]). [name] 이 정해진 뒤 처음 저장할 때 선다. */
    private val notices by lazy { EditorRules.NewFileNotices(name) }

    /** 뒤로 가기를 쪽에 물은 채 답을 기다린다([onBack]). */
    private var backPending = false
    private val backTimeout = Runnable {
        if (!backPending) return@Runnable
        backPending = false
        closeOrAsk()
    }

    /** 읽던 자리를 쪽에 물은 채 답을 기다린다([askPosition]) — 답이나 시간 넘김이 오면 부른다. */
    private var positionWaiter: ((String?) -> Unit)? = null
    private val positionTimeout = Runnable {
        val waiter = positionWaiter ?: return@Runnable
        positionWaiter = null
        waiter(null)
    }

    /** '다른 앱으로 열기' 를 띄운 때 — 고르는 창이 뜨기 전의 두 번째 누름을 흘려보낸다(CLAUDE.md 함정 표 'startActivity 는 받는 앱의 창이 뜨기 전에 돌아온다'). */
    private var openedWithAt = 0L

    /** 읽는 동안 화면 켜 두기 — 보기 모드에서 마지막 조작 뒤 이만큼(파일 쪽의 설정). 0 이면 기기의 화면 꺼짐 시간 그대로. */
    private var keepScreenMs = 0L

    /** 인텐트가 준 보기 모드의 켜 두기 — 열지 못해 멈췄다가 다시 열면 이 값으로 되돌린다. */
    private var viewKeepScreenMs = 0L
    private var keepingScreenOn = false
    private val releaseScreen = Runnable { setKeepScreenOn(false) }

    /** 창이 보이는가(onStart~onStop) — 보이지 않는 창에 화면 켜 두기를 걸지 않는다. */
    private var started = false

    /** 그림 넣기(쪽의 `<input type=file>`)가 기다리는 답 — 시스템 고르개에서 돌아오면 준다. */
    private var chooser: ValueCallback<Array<Uri>>? = null

    /** 자판이 올라와 있는가(창의 인셋) — 바뀔 때만 쪽에 알린다. */
    private var imeShown = false

    /** 인쇄 — 쪽이 문서를 보내는 동안·인쇄 창을 띄우기 전까지 떠 있는 물음, 그리고 지금의 인쇄([PrintJob], 한 번에 하나). */
    private var printDialog: AlertDialog? = null
    private var printWaiting = false
    private var printJob: PrintJob? = null

    /** 내보낸 파일 — 사용자가 저장할 곳을 고르는 동안 기다린다. */
    private var pendingExport: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io = Executors.newSingleThreadExecutor()
        root = FrameLayout(this).apply { setBackgroundColor(themeColor(android.R.attr.colorBackground)) }
        setContentView(root)
        lastConfig = Configuration(resources.configuration)
        fitInsets()
        registerBack()
        sweepOld()
        documents = DocumentStore(File(File(workDir(), "in"), UUID.randomUUID().toString()))

        if (!callerAllowed()) return showError(EditorRules.Error.CALLER)
        val uri = intent?.data
        val k = Kind.of(intent?.getStringExtra(EditorContract.EXTRA_KIND))
        if (intent?.action != EditorContract.ACTION_OPEN || uri == null || k == null) return showError(EditorRules.Error.NO_DOCUMENT)
        kind = k
        engine = Engine.forKind(k)
        mode = if (intent.getStringExtra(EditorContract.EXTRA_MODE) == EditorContract.MODE_VIEW) EditorContract.MODE_VIEW else EditorContract.MODE_EDIT
        name = intent.getStringExtra(EditorContract.EXTRA_NAME)?.substringAfterLast('/')?.take(200)?.ifBlank { null } ?: "document.${k.ext}"
        val s = slotOf(intent)
        if (s == null || slotAllowed(s)) {
            slot = s
        } else if (mode == EditorContract.MODE_EDIT) {
            // 고치러 왔는데 저장을 반영할 자리를 믿을 수 없다 — 열지 않는다. 보러 온 것이면 자리 없이 보인다('편집' 단추만 없다).
            return showError(EditorRules.Error.SLOT)
        }
        if (mode == EditorContract.MODE_VIEW) {
            val minutes = intent.getIntExtra(EditorContract.EXTRA_KEEP_SCREEN_MINUTES, 0).coerceIn(0, KEEP_SCREEN_MAX_MINUTES)
            viewKeepScreenMs = minutes * MINUTE_MS
            keepScreenMs = viewKeepScreenMs
        }
        if (!engine.installed(assets)) return showError(EditorRules.Error.NO_ENGINE)
        if (!HostBridge.isSupported()) return showError(EditorRules.Error.WEBVIEW)

        showStatus(getString(R.string.editor_opening))
        recopy(null) { copied, _ -> openCopied(copied, null) }
    }

    /** 문서를 떠 둔 결과 — 표, 또는 이 화면이 아는 오류(문서가 너무 크면 그 상한 — [limit]). */
    private sealed class Copied {
        class Token(val token: String) : Copied()
        class Failed(val error: EditorRules.Error, val limit: Long = 0) : Copied()
    }

    /** 덮개에 보이는 것 — 글과 단추(위에서 아래로). 단추가 없으면 '여는 중' 같은 기다림이다. */
    private class OverlaySpec(val text: String, val actions: List<EditorRules.Action>)

    /** 떠 온 결과로 편집기를 연다 — 못 떴으면 까닭과 단추. [at] 은 편집 쪽이 갈 읽던 자리. */
    private fun openCopied(copied: Copied, at: String?) {
        when (copied) {
            is Copied.Token -> {
                token = copied.token
                open(copied.token, at)
            }
            is Copied.Failed -> showError(copied.error, copied.limit)
        }
    }

    /**
     * 넘겨받은 문서를 (다시) 떠 온다 — io 스레드에서. [old] 가 있으면 먼저 그 사본과 지금의 원본을 바이트로 견주어([DocumentStore.sameAs]) 같으면
     * 그대로 쓴다(새로 쓰지 않는다). 다르면 새로 떠 오고 옛 사본을 지운다 — [then] 의 둘째 값이 참이다(원본이 연 뒤 바뀌었다 — 예전에는
     * 오래 읽다 '편집' 하면 연 때의 사본을 고쳐 저장했다). 받은 URI(파일 쪽의 FileProvider)로는 크기만 물을 수 있고 수정 시각은 알 수 없어
     * 내용으로 견준다. 옛 사본이 사라졌으면(캐시를 비웠다) 새로 떠 오되 바뀌었다고 하지 않는다. 화면이 끝났으면 [then] 을 부르지 않는다.
     */
    private fun recopy(old: String?, then: (Copied, Boolean) -> Unit) {
        val uri = intent?.data
        io.execute {
            var changed = false
            val copied: Copied = try {
                if (uri == null) throw IOException("문서가 없다")
                val kept = old?.takeIf { documents.fileOf(it) != null }
                val same = kept != null && (contentResolver.openInputStream(uri) ?: throw IOException("열 수 없다")).use { documents.sameAs(kept, it) }
                if (same) {
                    Copied.Token(kept)
                } else {
                    val fresh = documents.copyIn(contentResolver, uri)
                    if (old != null) documents.forget(old)
                    changed = kept != null
                    Copied.Token(fresh)
                }
            } catch (e: DocumentStore.TooLarge) {
                Copied.Failed(EditorRules.Error.TOO_LARGE, e.limit)
            } catch (e: IOException) {
                Copied.Failed(EditorRules.Error.READ)
            } catch (e: SecurityException) {
                Copied.Failed(EditorRules.Error.READ)
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                then(copied, changed)
            }
        }
    }

    // ---- 부른 앱 ---------------------------------------------------------------------------------------------------

    /**
     * 이 앱이 띄웠는가(파일 화면, 또는 디버그 시험대). 화면을 내보내지 않으므로 남의 앱은 띄우지 못한다 — 그래도 겹쳐 본다.
     * `startActivityForResult` 로만 부른 쪽을 알 수 있다 — 결과를 받지 않는 부름은 받지 않는다.
     */
    private fun callerAllowed(): Boolean = callingActivity?.packageName == packageName

    // ---- 편집기 -------------------------------------------------------------------------------------------------------

    // MissingOnRenderProcessGone: 아래의 WebViewClient 에 있다 — androidx.webkit 1.17 의 lint 가 Kotlin 의 상위 생성자 호출을 맨
    // WebViewClient 로 잘못 읽는다(브라우저에서 확인한 오탐).
    @SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
    private fun open(token: String, at: String?) {
        hideStatus()
        opened = false
        openFailure = null
        val server = AssetServer(assets, engine, documents)
        lockServiceWorkers()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        val view = WebView(this)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            textZoom = 100
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = server.serve(request)

            // 엔진은 한 쪽짜리다 — 쪽이 일으키는 탐색은 모두 막는다(우리 출처 안이라도: 떠나면 편집기와 고친 것이 사라진다). 앱이 부르는
            // loadUrl 은 여기를 지나지 않는다. 문서의 링크도 바깥으로 열지 않는다(어댑터가 window.open 을 막고, 남은 것은 여기서).
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            // 렌더러가 죽었다(대개 메모리 — 큰 문서). 뷰를 떼고 까닭과 '다시 열기'·'다른 앱으로 열기' 를 단다 — 고치던 것이 있었을 때만 잃었을 수
            // 있다고 말한다(보기 모드에서는 잃을 것이 없다).
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (BuildConfig.DEBUG) Log.w(TAG, "renderer gone crash=${detail.didCrash()}")
                dropWebView()
                showError(EditorRules.Error.RENDERER)
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (BuildConfig.DEBUG) Log.i(TAG, "console ${message.messageLevel()}: ${message.message().take(500)} @${message.lineNumber()}")
                return true
            }

            // 엔진의 alert·confirm(저장 실패 알림 따위) — WebView 의 기본 창은 제목에 가짜 출처의 주소를 적는다. 우리 창으로 띄운다.
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                jsDialog(message, result, withCancel = false)
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                jsDialog(message, result, withCancel = true)
                return true
            }

            // 엔진들은 prompt 를 쓰지 않는다 — 어댑터가 문서의 링크를 열지 묻는 길(LINK_PROMPT, 이 엔진의 출처에서 온 것)만 받고, 그 밖에는
            // 취소로 답한다. 답할 때까지 쪽이 멈춘다 — 그래서 '그만두기' 를 쪽이 알고 방문 빛깔을 칠하지 않는다(rhwp 어댑터의 4).
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
                val from = Uri.parse(url)
                if (message == LINK_PROMPT && from.scheme == "https" && from.host == engine.host) {
                    linkDialog(defaultValue, result)
                } else {
                    result.cancel()
                }
                return true
            }

            // 그림 넣기 — 사용자가 시스템 고르개에서 고른 그림 하나만 읽는다(저장소 권한 없이, 고른 것에만 읽기 권한이 온다). 문서를 고르는
            // 것(열기·문서 비교)은 받지 않는다 — 문서의 주인은 Files 다. 보기 모드도 받지 않는다.
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                val types = params.acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                if (mode != EditorContract.MODE_EDIT || types.isEmpty() || !types.all { it in IMAGE_TYPES }) {
                    callback.onReceiveValue(null)
                    return true
                }
                chooser?.onReceiveValue(null)
                chooser = callback
                val pick = Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                try {
                    startActivityForResult(pick, REQUEST_IMAGE)
                } catch (e: ActivityNotFoundException) {
                    chooser = null
                    callback.onReceiveValue(null)
                }
                return true
            }
        }
        val b = HostBridge(engine, OutputProvider.rootOf(this), spoolDir(), name, mode == EditorContract.MODE_EDIT, io, main, bridgeEvents)
        b.install(view)
        bridge = b
        web = view
        root.addView(view, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.loadUrl(engine.entryUrl(token, kind, mode, name, at))
        // 보기 모드로 (다시) 열었다 — 열지 못해 멈춘 화면 켜 두기를 되돌린다.
        if (mode == EditorContract.MODE_VIEW && viewKeepScreenMs > 0) {
            keepScreenMs = viewKeepScreenMs
            holdScreenOn()
        }
        // 문서에 닿았다 — 보기 모드면 ⋮(파일 이름·다른 앱으로 열기)를 곧바로 띄운다(여는 동안·암호를 묻는 동안에도 다른 앱으로 넘길 수 있게).
        refreshFloatingBar()
    }

    private val bridgeEvents = object : HostBridge.Events {
        override fun onReady() {
            if (BuildConfig.DEBUG) Log.i(TAG, "ready ${engine.id} ${kind.id} $mode")
            // 쪽이 인사하기 전(문서 암호를 묻는 동안 따위)에 바뀐 자판의 보임은 쪽에 닿지 못했다 — 지금 것을 한 번 알린다.
            bridge?.imeChanged(imeShown)
            // 늦게 온 인사 — 이미 열지 못했다고 알렸다.
            if (openFailure != null) return
            // 문서가 열렸다 — 보기 모드이고 고쳐 둘 곳이 있으면 '편집' 단추를 띄운다(암호를 묻는 동안에는 아직 없다).
            opened = true
            refreshFloatingBar()
        }

        // 쪽이 문서를 열지 못했다 — 까닭의 글은 쪽이 제 화면에 적었다(엔진마다 다르다), [reason] 은 갈래만. 고칠 것이 없으니 '편집' 단추를 걷고,
        // 암호 물음을 닫았을 뿐이면 '다시 열기', 그 밖은 이 문서를 다른 앱으로 넘길 단추를 단다(파일 쪽 뷰어들의 실패 화면이 하던 것). 읽을 것도
        // 없으니 화면 켜 두기도 그만둔다.
        override fun onOpenFailed(reason: String?) {
            if (BuildConfig.DEBUG) Log.i(TAG, "open failed ${engine.id} ${kind.id} $mode reason=$reason")
            openFailure = EditorRules.OpenFailure.of(reason)
            opened = false
            stopKeepingScreenOn()
            refreshFloatingBar()
        }

        override fun onPosition(at: String?) {
            val waiter = positionWaiter ?: return
            positionWaiter = null
            main.removeCallbacks(positionTimeout)
            waiter(EditorRules.position(at))
        }

        override fun onBackResult(handled: Boolean) {
            if (!backPending) return
            backPending = false
            main.removeCallbacks(backTimeout)
            if (!handled) closeOrAsk()
        }

        override fun onDirty(dirty: Boolean) {
            this@EditorActivity.dirty = dirty
        }

        override fun onSaving() {
            saving = true
        }

        override fun onSaved(file: File, kind: Kind, done: (Boolean) -> Unit) {
            if (BuildConfig.DEBUG) Log.i(TAG, "saved ${file.length()} bytes as ${kind.id}")
            val target = slot
            if (target == null) {
                // 저장 자리가 없다(디버그 시험대) — 끝날 때 결과로 돌려준다.
                saving = false
                dirty = false
                saved = file to kind
                setResult(RESULT_OK, resultIntent(file, kind))
                done(true)
                if (closeAfterSave) finish()
                return
            }
            // Files 의 저장 자리에 반영한다 — 반영해야 '저장됨' 이다. 보낸 파일은 Files 가 가졌다(못 가졌으면 쪽이 다음 저장에 다시 보낸다).
            io.execute {
                val outcome = commitToSlot(target, file, kind)
                file.parentFile?.deleteRecursively()
                main.post {
                    saving = false
                    if (outcome.ok) {
                        dirty = false
                        val savedName = outcome.name ?: file.name
                        setResult(RESULT_OK, Intent().putExtra(EditorContract.EXTRA_KIND, kind.id).putExtra(EditorContract.EXTRA_NAME, savedName))
                        done(true)
                        // 형식·이름을 바꿔 저장했으면 Files 가 원본 곁에 새 파일을 만들었다 — 어디에 있는지 말한다. 이 화면에서 그 이름을 처음 알릴
                        // 때만이다 — 같은 세션의 다음 저장은 그 새 파일을 고쳐 쓸 뿐이다.
                        val announce = notices.toAnnounce(savedName)
                        if (announce != null && !isFinishing) {
                            Toast.makeText(this@EditorActivity, getString(R.string.editor_saved_new_file, announce), Toast.LENGTH_LONG).show()
                        }
                        if (closeAfterSave) finish()
                    } else {
                        closeAfterSave = false
                        done(false)
                        message(slotFailureText(outcome.reason))
                    }
                }
            }
        }

        override fun onSaveFailed() {
            saving = false
            closeAfterSave = false
            AlertDialog.Builder(this@EditorActivity).setMessage(R.string.editor_save_failed).setPositiveButton(R.string.editor_close, null).show()
        }

        override fun onSaveCancelled() {
            closeAfterSave = false
        }

        // 사용자가 쪽의 '붙이기' 를 눌렀다 — 클립보드의 첫 항목의 글과 HTML 만 준다(그림·파일 주소는 주지 않는다). 안드로이드 12 이상은
        // 읽을 때마다 '붙여넣음' 알림을 띄운다. 보기 모드는 붙이지 않는다. 너무 크면(PASTE_LIMIT 자) 주지 않는다.
        override fun onPasteRequest() {
            if (mode != EditorContract.MODE_EDIT || !hasWindowFocus()) return
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
            if (BuildConfig.DEBUG) Log.i(TAG, "paste: items=${clip?.itemCount} types=${clip?.description?.let { d -> (0 until d.mimeTypeCount).map { d.getMimeType(it) } }}")
            val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return
            val text = item.text?.toString()
            val html = item.htmlText
            if (text == null && html == null) return
            if ((text?.length ?: 0) + (html?.length ?: 0) > PASTE_LIMIT) return
            bridge?.deliverPaste(text, html)
        }

        // 쪽이 스스로 연 입력칸(문서를 열 때 묻는 암호 칸) — 웹 쪽은 사용자의 동작 없이 자판을 올리지 못한다. 쪽의 초점이 그 칸에 있으므로
        // WebView 에 자판을 붙이면 그 칸으로 친다. 화면이 앞에 없으면 올리지 않는다.
        override fun onKeyboardRequest() {
            val view = web ?: return
            if (!hasWindowFocus()) return
            view.requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(view, 0)
        }

        // 인쇄 — 쪽이 인쇄 문서를 보내기 시작했다. 크면 몇 초 걸리므로 물음을 띄워 둔다(그만둘 수 있다).
        override fun onPrintArriving() {
            if (printJob != null) return
            printWaiting = true
            if (printDialog == null) {
                printDialog = AlertDialog.Builder(this@EditorActivity)
                    .setMessage(R.string.editor_print_preparing)
                    .setNegativeButton(R.string.editor_cancel) { _, _ -> cancelPrint() }
                    .setOnCancelListener { cancelPrint() }
                    .show()
            }
        }

        override fun onPrintReady(file: File, title: String, widthMm: Double, heightMm: Double) {
            if (!printWaiting || printJob != null || isFinishing || isDestroyed) {
                file.parentFile?.deleteRecursively()
                return
            }
            printWaiting = false
            val job = PrintJob(this@EditorActivity, engine, documents, file, title, PaperSizes.of(widthMm, heightMm), printEvents)
            printJob = job
            job.start()
        }

        override fun onExportReady(file: File, mime: String) {
            if (isFinishing || isDestroyed) {
                file.parentFile?.deleteRecursively()
                return
            }
            pendingExport?.parentFile?.deleteRecursively()
            pendingExport = file
            val pick = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime)
                .putExtra(Intent.EXTRA_TITLE, file.name)
            try {
                startActivityForResult(pick, REQUEST_EXPORT)
            } catch (e: ActivityNotFoundException) {
                pendingExport = null
                file.parentFile?.deleteRecursively()
                message(R.string.editor_export_no_picker)
            }
        }

        override fun onTransferFailed(purpose: HostBridge.Purpose) {
            if (purpose == HostBridge.Purpose.PRINT) {
                printWaiting = false
                dismissPrintDialog()
                message(R.string.editor_print_failed)
            } else {
                message(R.string.editor_export_failed)
            }
        }
    }

    private val printEvents = object : PrintJob.Listener {
        override fun onPrintShown() {
            dismissPrintDialog()
        }

        override fun onPrintFailed() {
            printJob = null
            dismissPrintDialog()
            message(R.string.editor_print_failed)
        }

        override fun onPrintDone() {
            printJob = null
            dismissPrintDialog()
        }
    }

    private fun cancelPrint() {
        printWaiting = false
        printDialog = null
        printJob?.cancel()
    }

    private fun dismissPrintDialog() {
        val d = printDialog ?: return
        printDialog = null
        d.setOnCancelListener(null)
        d.dismiss()
    }

    /** 내보낸 파일을 사용자가 고른 곳(시스템의 '저장할 곳 고르기' 가 만든 문서)에 쓴다. */
    private fun writeExport(file: File, target: Uri) {
        io.execute {
            val ok = try {
                val out = try {
                    contentResolver.openOutputStream(target, "wt")
                } catch (e: IllegalArgumentException) {
                    contentResolver.openOutputStream(target, "w") // "wt" 를 모르는 제공자 — 새로 만든 빈 문서라 덮어쓸 것이 없다
                }
                (out ?: throw IOException("열지 못했다")).use { o -> file.inputStream().use { it.copyTo(o) } }
                true
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            } finally {
                file.parentFile?.deleteRecursively()
            }
            val shownName = if (ok) displayName(target) ?: file.name else null
            main.post {
                if (isFinishing || isDestroyed) return@post
                if (shownName != null) {
                    Toast.makeText(this, getString(R.string.editor_export_done, shownName), Toast.LENGTH_SHORT).show()
                } else {
                    message(R.string.editor_export_failed)
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: RuntimeException) {
        null
    }

    /**
     * 문서의 링크를 열지 묻는다 — 주소의 호스트를 따로 보인다(글자로 꾸민 링크·비슷한 모양의 주소에 속지 않게. 국제화 도메인은 쪽이
     * 퓨니코드로 바꿔 보낸다). '열기' 면 브라우저 앱으로 넘긴다. 쪽은 [result] 의 답을 기다린다 — `opened`·`no-app`, 그만두면 취소.
     */
    private fun linkDialog(href: String?, result: JsPromptResult) {
        val target = href?.takeIf { it.length <= LINK_LIMIT }?.let(Uri::parse)
        if (target == null || target.scheme !in WEB_SCHEMES || target.host.isNullOrEmpty() || target.userInfo != null || isFinishing || isDestroyed) {
            result.cancel()
            return
        }
        var answered = false
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_link_title)
            .setMessage(getString(R.string.editor_link_message, target.host, href))
            .setPositiveButton(R.string.editor_link_open) { _, _ ->
                answered = true
                val view = Intent(Intent.ACTION_VIEW, target)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = try {
                    startActivity(view)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
                result.confirm(if (opened) "opened" else "no-app")
            }
            .setNegativeButton(R.string.editor_cancel) { _, _ ->
                answered = true
                result.cancel()
            }
            .setOnDismissListener { if (!answered) result.cancel() }
            .show()
    }

    private fun message(text: Int) = message(getString(text))

    private fun message(text: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setMessage(text).setPositiveButton(R.string.editor_close, null).show()
    }

    /** 편집기가 앱 캐시에 쓰는 것의 자리 — 파일 쪽의 캐시(공유 사본·저장 자리)와 섞이지 않게 `editor/` 아래에 둔다. */
    private fun workDir(): File = File(cacheDir, OutputProvider.WORK)

    private fun spoolDir(): File = File(workDir(), SPOOL)

    /** 저장 자리에 반영한 결과 — [name] 은 Files 가 실제로 쓴 파일의 이름, [reason] 은 못 썼을 때의 까닭 글자(Files 의 `EditorSlots`). */
    private class SlotOutcome(val ok: Boolean, val name: String?, val reason: String?)

    /** io 스레드에서 — [file] 을 Files 의 저장 자리에 쓰고 반영을 부른다. Files 는 파일 작업 큐가 끝날 때까지 답하지 않는다. */
    private fun commitToSlot(target: Uri, file: File, kind: Kind): SlotOutcome = try {
        (contentResolver.openOutputStream(target, "wt") ?: throw IOException("열지 못했다")).use { out ->
            file.inputStream().use { it.copyTo(out, 256 * 1024) }
        }
        val reply = contentResolver.call(
            target,
            EditorContract.METHOD_COMMIT,
            target.lastPathSegment,
            Bundle().apply {
                putString(EditorContract.KEY_KIND, kind.id)
                putString(EditorContract.KEY_NAME, file.name)
            },
        )
        if (reply?.getBoolean(EditorContract.KEY_OK) == true) {
            SlotOutcome(true, reply.getString(EditorContract.KEY_NAME), null)
        } else {
            SlotOutcome(false, null, reply?.getString(EditorContract.KEY_REASON))
        }
    } catch (e: IOException) {
        if (BuildConfig.DEBUG) Log.w(TAG, "slot write failed: ${e.javaClass.simpleName}")
        SlotOutcome(false, null, null)
    } catch (e: SecurityException) {
        if (BuildConfig.DEBUG) Log.w(TAG, "slot denied")
        SlotOutcome(false, null, REASON_DENIED)
    } catch (e: IllegalArgumentException) {
        // 저장 자리의 제공자를 찾지 못했다(Files 를 지웠다·바꿔 깔았다).
        SlotOutcome(false, null, REASON_GONE_APP)
    }

    private fun slotFailureText(reason: String?): Int = when (reason) {
        "gone" -> R.string.editor_save_failed_gone
        "session" -> R.string.editor_save_failed_session
        "space" -> R.string.editor_save_failed_space
        "previous" -> R.string.editor_save_failed_previous
        "invalid" -> R.string.editor_save_failed_invalid
        "busy" -> R.string.editor_save_failed_busy
        REASON_DENIED, REASON_GONE_APP -> R.string.editor_save_failed_files
        else -> R.string.editor_save_failed
    }

    /** 저장 자리 — 인텐트의 추가값. */
    private fun slotOf(intent: Intent): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EditorContract.EXTRA_SAVE_SLOT, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EditorContract.EXTRA_SAVE_SLOT)
    }

    /** 이 앱의 저장 자리의 꼴인가 — content, 저장 자리의 권한자, 자리의 표 하나. */
    private fun slotAllowed(s: Uri): Boolean =
        s.scheme == "content" && s.authority == EditorContract.slotAuthority(this) &&
            s.pathSegments.size == 2 && s.pathSegments[0] == "slot" && SLOT_TOKEN.matches(s.pathSegments[1])

    private fun resultIntent(file: File, kind: Kind): Intent =
        Intent().setDataAndType(OutputProvider.uriFor(this, file), kind.mime)
            .putExtra(EditorContract.EXTRA_KIND, kind.id)
            .putExtra(EditorContract.EXTRA_NAME, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun jsDialog(message: String, result: JsResult, withCancel: Boolean) {
        if (isFinishing || isDestroyed) {
            result.cancel()
            return
        }
        var answered = false
        // 확인 단추는 이 앱의 문구다 — android.R.string.ok 는 기기의 언어를 따라 영어 기기에서 'OK' 곁에 '취소' 가 섰다(이 앱 화면은 늘 한국어다).
        val builder = AlertDialog.Builder(this)
            .setMessage(message.take(2000))
            .setPositiveButton(R.string.editor_ok) { _, _ ->
                answered = true
                result.confirm()
            }
            .setOnDismissListener { if (!answered) result.cancel() }
        if (withCancel) {
            builder.setNegativeButton(R.string.editor_cancel) { _, _ ->
                answered = true
                result.cancel()
            }
        }
        builder.show()
    }

    /** 서비스 워커는 쓰지 않는다 — 그 요청은 WebViewClient 를 지나지 않으므로 모두 빈 404 로 막는다. */
    private fun lockServiceWorkers() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse =
                WebResourceResponse("text/plain", "utf-8", 404, "Not Found", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
        })
        ServiceWorkerController.getInstance().serviceWorkerWebSettings.apply {
            allowContentAccess = false
            allowFileAccess = false
            blockNetworkLoads = true
        }
    }

    private fun dropWebView() {
        val view = web ?: return
        web = null
        bridge?.close()
        bridge = null
        root.removeView(view)
        view.destroy()
    }

    /**
     * 보기 → 편집('편집' 단추). **같은 문서를 편집 모드로 다시 연다.** 엔진들은 처음 열 때 모드를 정하므로(rhwp 어댑터의 입력 막기,
     * ONLYOFFICE 의 `editorConfig.mode`) 쪽 안에서 바꾸지 않고 WebView 를 새로 세운다. 보기 모드에서는 고친 것이 없어 잃을 것이 없다. 문서에 암호가
     * 걸려 있으면 엔진이 다시 묻는다(암호는 어디에도 두지 않는다). 화면 켜 두기는 보기 모드의 것이라 함께 푼다 — 고치는 동안은 그 조작이 화면을
     * 깨워 둔다(파일 쪽 글 편집기와 같은 판단).
     *
     * 두 가지를 함께 한다 — **원본을 다시 읽는다**([recopy] — 오래 읽는 사이 바뀌었으면 연 때의 사본이 아니라 지금 파일로 열고
     * 그렇다고 말한다. 읽지 못하면 보기 그대로 두고 말한다), **읽던 자리를 넘긴다**([askPosition] — 보기 쪽 어댑터가 알린 자리를 편집 쪽 첫 쪽의
     * 주소에 싣는다. 쪽이 답하지 않으면 처음부터). 둘을 함께 기다리는 동안 단추를 걷는다(두 번 누르기를 막는다).
     */
    private fun switchToEdit() {
        val old = token ?: return
        if (mode != EditorContract.MODE_VIEW || slot == null || reloading || isFinishing || isDestroyed) return
        // 인쇄할 것을 받는 동안·인쇄 창을 띄운 동안에는 바꾸지 않는다 — 그 일이 지금 쪽에 매여 있다.
        if (printWaiting || printJob != null) return
        reloading = true
        refreshFloatingBar()
        var at: String? = null
        var answered = false
        var copied: Copied? = null
        var changed = false
        val proceed = {
            val c = copied
            if (answered && c != null) finishSwitch(c, changed, at)
        }
        askPosition { position ->
            at = position
            answered = true
            proceed()
        }
        recopy(old) { c, ch ->
            copied = c
            changed = ch
            proceed()
        }
    }

    /** [switchToEdit] 의 둘(다시 떠 온 문서·읽던 자리)이 다 왔다 — 편집 모드로 다시 연다. 원본을 읽지 못했으면 보기 그대로 두고 말한다. */
    private fun finishSwitch(copied: Copied, changed: Boolean, at: String?) {
        reloading = false
        if (isFinishing || isDestroyed) return
        if (copied is Copied.Token) token = copied.token
        // 기다리는 사이 인쇄를 시작했다 — 그 일이 지금 쪽에 매여 있다. 보기 그대로 둔다(다시 누르면 된다).
        if (printWaiting || printJob != null) {
            refreshFloatingBar()
            return
        }
        when (copied) {
            is Copied.Failed -> {
                // 고쳐 저장할 원본을 읽지 못했다(지워졌다·옮겨졌다·커졌다) — 편집으로 바꾸지 않는다.
                refreshFloatingBar()
                message(if (copied.error == EditorRules.Error.TOO_LARGE) errorText(copied.error, copied.limit) else getString(R.string.editor_switch_unreadable))
            }
            is Copied.Token -> {
                if (changed) Toast.makeText(this, R.string.editor_reloaded_changed, Toast.LENGTH_LONG).show()
                mode = EditorContract.MODE_EDIT
                stopKeepingScreenOn()
                dirty = false
                dropWebView()
                open(copied.token, at)
            }
        }
    }

    /** 보기 쪽 어댑터에 읽던 자리를 묻는다 — 답(꼴을 본 것)이나 [POSITION_TIMEOUT_MS] 넘김이 오면 [then]. 쪽이 아직 말을 건 적이 없으면 곧바로 null. */
    private fun askPosition(then: (String?) -> Unit) {
        if (bridge?.requestPosition() != true) return then(null)
        positionWaiter = then
        main.postDelayed(positionTimeout, POSITION_TIMEOUT_MS)
    }

    /**
     * 다시 열기 — 렌더러가 죽었을 때·문서를 읽지 못했을 때·암호 물음을 닫아 열지 못했을 때. **원본을 다시 떠 와**([recopy]) 지금 모드로
     * 새 편집기를 연다 — 편집 모드에서 저장한 뒤 렌더러가 죽었으면 연 때의 사본이 아니라 저장한 판을 다시 연다(옛 사본으로 다시 고쳐 저장하면 저장한
     * 것을 옛 판으로 덮는다). 암호는 엔진이 다시 묻는다.
     */
    private fun reopen() {
        if (reloading || isFinishing || isDestroyed) return
        if (printWaiting || printJob != null) return
        reloading = true
        removeFloatingBar()
        showStatus(getString(R.string.editor_opening))
        recopy(token) { copied, _ ->
            reloading = false
            // 옛 편집기는 쓸 데가 없다(죽었거나 열지 못한 쪽) — 다시 떠 오지 못했어도 걷는다(덮개 아래에 렌더러를 남겨 두지 않는다).
            dirty = false
            dropWebView()
            openCopied(copied, null)
        }
    }

    // ---- 닫기 ---------------------------------------------------------------------------------------------------------

    private fun registerBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { onBack() }
        }
    }

    // 13 이상은 플랫폼의 OnBackInvokedCallback(registerBack), 12 는 이것 — AndroidX 의 디스패처는 새 의존이다(브라우저와 같은 판단).
    @Deprecated("API 33 아래에서만 불린다 — 그 위는 registerBack 의 콜백")
    @Suppress("DEPRECATION")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        onBack()
    }

    /**
     * 뒤로 가기 — **먼저 쪽에 묻는다**(엔진의 메뉴·찾기 창을 연 채 뒤로 가면 문서가 닫혔다). 쪽(어댑터)이 열린 메뉴·대화상자를
     * 닫았으면 그것으로 끝이고, 닫을 것이 없었거나 [BACK_TIMEOUT_MS] 안에 답이 없으면 [closeOrAsk]. 쪽이 아직 말을 건 적이 없으면 묻지 않는다.
     */
    private fun onBack() {
        if (saving || backPending) return
        val b = bridge
        if (web != null && b != null && !reloading && b.requestBack()) {
            backPending = true
            main.postDelayed(backTimeout, BACK_TIMEOUT_MS)
            return
        }
        closeOrAsk()
    }

    /** 쪽이 닫을 것이 없었다 — 고친 것이 없으면 닫고, 있으면 저장할지 묻는다. */
    private fun closeOrAsk() {
        if (saving || isFinishing || isDestroyed) return
        if (!dirty || web == null || mode != EditorContract.MODE_EDIT) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_unsaved_title)
            .setMessage(R.string.editor_unsaved_message)
            .setPositiveButton(R.string.editor_save) { _, _ ->
                closeAfterSave = true
                if (bridge?.requestSave() != true) {
                    closeAfterSave = false
                    bridgeEvents.onSaveFailed()
                }
            }
            .setNegativeButton(R.string.editor_discard) { _, _ -> finish() }
            .setNeutralButton(R.string.editor_cancel, null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_IMAGE -> {
                chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                chooser = null
            }
            REQUEST_EXPORT -> {
                val file = pendingExport ?: return
                pendingExport = null
                val target = data?.data
                if (resultCode == RESULT_OK && target != null) writeExport(file, target) else file.parentFile?.deleteRecursively()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    // ---- 읽는 동안 화면 켜 두기(보기 모드) ------------------------------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        started = true
        holdScreenOn()
    }

    override fun onStop() {
        // 창이 보이지 않는 동안 아무것도 쥐지 않는다. 다시 서면 그때부터 다시 잰다.
        started = false
        main.removeCallbacks(releaseScreen)
        setKeepScreenOn(false)
        super.onStop()
    }

    /** 손가락이 닿을 때·키를 누를 때마다 온다(액티비티가 WebView 보다 먼저 받는다). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        holdScreenOn()
    }

    /** 지금부터 [keepScreenMs] 동안 화면을 켜 둔다 — 조작이 올 때마다 다시 잰다(파일 쪽 `ViewerWindowHost` 의 규칙과 같다). */
    private fun holdScreenOn() {
        if (keepScreenMs <= 0 || !started) return
        setKeepScreenOn(true)
        main.removeCallbacks(releaseScreen)
        main.postDelayed(releaseScreen, keepScreenMs)
    }

    /** 보기 모드가 끝났다(편집으로 바꿨다·못 열었다) — 화면 켜 두기를 그만둔다. */
    private fun stopKeepingScreenOn() {
        keepScreenMs = 0
        main.removeCallbacks(releaseScreen)
        setKeepScreenOn(false)
    }

    private fun setKeepScreenOn(on: Boolean) {
        if (on == keepingScreenOn) return
        keepingScreenOn = on
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        main.removeCallbacks(releaseScreen)
        main.removeCallbacks(backTimeout)
        main.removeCallbacks(positionTimeout)
        positionWaiter = null
        morePopup?.dismiss()
        morePopup = null
        chooser?.onReceiveValue(null)
        chooser = null
        printJob?.destroy()
        printJob = null
        dismissPrintDialog()
        pendingExport?.parentFile?.deleteRecursively()
        pendingExport = null
        dropWebView()
        io.execute { if (::documents.isInitialized) documents.clear() }
        io.shutdown()
        super.onDestroy()
    }

    // ---- 화면 조각 ------------------------------------------------------------------------------------------------------

    private fun showStatus(text: String) = showOverlay(text, emptyList())

    /** 이 화면이 아는 오류 — 까닭과 단추([EditorRules.actions] — 문서 탓이면 '다른 앱으로 열기', 다시 해 볼 만하면 '다시 열기', 그리고 '닫기'). */
    private fun showError(error: EditorRules.Error, limit: Long = 0) {
        removeFloatingBar()
        stopKeepingScreenOn()
        showOverlay(errorText(error, limit), EditorRules.actions(error, intent?.data != null))
    }

    private fun errorText(error: EditorRules.Error, limit: Long = 0): String = when (error) {
        EditorRules.Error.CALLER -> getString(R.string.editor_error_caller)
        EditorRules.Error.NO_DOCUMENT -> getString(R.string.editor_error_no_document)
        EditorRules.Error.SLOT -> getString(R.string.editor_error_slot)
        EditorRules.Error.NO_ENGINE -> getString(R.string.editor_error_no_engine)
        EditorRules.Error.WEBVIEW -> getString(R.string.editor_error_webview)
        EditorRules.Error.TOO_LARGE -> getString(R.string.editor_error_too_large, "${limit / (1024 * 1024)} MB")
        EditorRules.Error.READ -> getString(R.string.editor_error_read)
        EditorRules.Error.RENDERER -> getString(
            if (EditorRules.rendererLostEdits(mode == EditorContract.MODE_EDIT, dirty)) R.string.editor_error_renderer_unsaved else R.string.editor_error_renderer,
        )
    }

    /**
     * 화면 위에 뜨는 단추 묶음을 지금 상태로 다시 짓는다 — 오른쪽 아래에 ⋮ 와 주 단추([EditorRules.floating]).
     *
     * - 주 단추 — 보기 모드의 **'편집'**(뷰어 교체. 문서를 연 뒤 — 쪽이 인사한 뒤 — 파일 쪽이 저장 자리를 줬을 때만. 받은 사본처럼 고쳐
     *   둘 곳이 없는 문서에는 없다), 열지 못했을 때의 **'다시 열기'**(암호 물음을 닫았다)나 **'다른 앱으로 열기'**.
     * - **⋮** — 이 화면에는 이 앱의 막대가 없어 보기 모드에 '다른 앱으로 열기' 도 파일 이름도 없었다. 메뉴의 머리에 파일
     *   이름, 줄에 '다른 앱으로 열기'. 고치는 동안에는 두지 않는다.
     *
     * 문서 앱들의 보기 화면이 연필 단추를 두는 자리다 — 엔진의 아래 막대(쪽·배율·시트 탭)를 가리지 않게 그 위로 띄운다([EDIT_BUTTON_LIFT_DP]).
     * 덮개(여는 중·오류)가 떠 있거나 보기 → 편집을 준비하는 동안은 없다. 자판이 떠 있는 동안(찾기 칸)은 숨긴다([fitInsets]). 플랫폼 뷰로 그린다 —
     * 이 화면은 새 의존 없이 선다(Material 구성 요소를 들이지 않는다).
     */
    private fun refreshFloatingBar() {
        removeFloatingBar()
        if (isFinishing || isDestroyed || reloading || overlay != null || web == null) return
        val f = EditorRules.floating(mode == EditorContract.MODE_VIEW, opened, openFailure, slot != null, intent?.data != null)
        if (f.primary == null && !f.more) return
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (imeShown) View.GONE else View.VISIBLE
        }
        if (f.more) {
            bar.addView(moreButton(), LinearLayout.LayoutParams(dp(MORE_SIZE_DP), dp(MORE_SIZE_DP)).apply { marginEnd = dp(12) })
        }
        when (f.primary) {
            EditorRules.Primary.EDIT -> bar.addView(primaryButton(getString(R.string.editor_switch_to_edit), R.drawable.editor_ic_edit) { switchToEdit() })
            EditorRules.Primary.REOPEN -> bar.addView(primaryButton(getString(R.string.editor_reopen), R.drawable.editor_ic_reopen) { reopen() })
            EditorRules.Primary.OPEN_WITH -> bar.addView(primaryButton(getString(IoR.string.io_open_with), null) { openWithOtherApp() })
            null -> {}
        }
        floatingBar = bar
        root.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(16)
                bottomMargin = dp(EDIT_BUTTON_LIFT_DP)
            },
        )
    }

    /** 강조색의 둥근 주 단추(글과 아이콘). */
    private fun primaryButton(text: String, icon: Int?, onClick: () -> Unit): Button {
        // 강조색 바탕 위의 글 — 밝은 테마의 강조색은 짙고 어두운 테마의 것은 옅다(기기 기본 테마). 반대 글색(inverse)이 그 짝이다.
        val fg = themeColor(android.R.attr.textColorPrimaryInverse)
        val shape = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(themeColor(android.R.attr.colorAccent))
        }
        return Button(this).apply {
            setText(text)
            isAllCaps = false
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            if (icon != null) {
                val drawable = getDrawable(icon)?.mutate()?.apply { setTint(fg) }
                setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
                compoundDrawablePadding = dp(8)
            }
            background = RippleDrawable(ColorStateList.valueOf((fg and 0x00FFFFFF) or 0x40000000), shape, null)
            minHeight = dp(56)
            minimumHeight = dp(56)
            setPaddingRelative(dp(if (icon != null) 20 else 24), 0, dp(24), 0)
            elevation = dp(6).toFloat()
            setOnClickListener { onClick() }
        }
    }

    /** ⋮ — 바탕색의 둥근 단추(주 단추와 갈리게 테두리와 그림자). 화면 낭독 이름은 다른 뷰어들과 같은 '더보기'. */
    private fun moreButton(): ImageButton {
        val fg = themeColor(android.R.attr.textColorPrimary)
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(themeColor(android.R.attr.colorBackground))
            setStroke(dp(1), (fg and 0x00FFFFFF) or 0x33000000)
        }
        return ImageButton(this).apply {
            setImageDrawable(getDrawable(R.drawable.editor_ic_more)?.mutate()?.apply { setTint(fg) })
            background = RippleDrawable(ColorStateList.valueOf((fg and 0x00FFFFFF) or 0x33000000), circle, null)
            contentDescription = getString(R.string.editor_more)
            elevation = dp(6).toFloat()
            setOnClickListener { v -> showMoreMenu(v) }
        }
    }

    /** ⋮ 의 메뉴 — 머리에 파일 이름(누를 수 없는 줄 — 이 화면에는 제목 막대가 없다), 줄에 '다른 앱으로 열기'. */
    private fun showMoreMenu(anchor: View) {
        morePopup?.dismiss()
        val popup = PopupMenu(this, anchor, Gravity.END)
        popup.menu.add(Menu.NONE, MENU_NAME, 0, name).isEnabled = false
        popup.menu.add(Menu.NONE, MENU_OPEN_WITH, 1, IoR.string.io_open_with)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_OPEN_WITH) openWithOtherApp()
            true
        }
        popup.setOnDismissListener { if (morePopup === popup) morePopup = null }
        morePopup = popup
        popup.show()
    }

    /**
     * 이 문서를 다른 앱으로 — 넘겨받은 문서의 content URI(파일 쪽 FileProvider)를 읽기 권한과 함께 고르는 창으로 넘긴다. 보기 모드의 ⋮ 와 열지 못한
     * 문서·오류 화면의 단추가 부른다(받은 사본에도 된다 — 그 URI 는 이 앱의 제공자라 같은 앱이 읽기 권한을 다시 줄 수 있다). 이 앱의 '다른 앱에서
     * 열기'(`OpenActivity`)는 고르는 창에서 뺀다 — 고르면 이 편집기가 또 열린다. 그 형식을 받는 다른 앱이 없으면 모든 형식으로 넓히고 그 창의 이름도
     * 바꾼다 — 파일 쪽의 '다른 앱으로 열기'(`ExternalOpen` 의 '모든 앱에서 고르기')와 같은 판단·같은 문구다(HWP 를 받는 앱이 없는 기기가 흔하다,
     * 문구는 `core:io` 의 한 벌 — CLAUDE.md '다른 앱으로 열기' R5). 그 함수는 파일 경로를 받으므로 경로를 모르는 이 화면에서는 쓰지 않는다. 받는 앱을
     * 묻는 일은 `core:io` 매니페스트의 `<queries>`(VIEW·content) 덕에 된다. 고르는 창이 뜨기 전의 두 번째 누름은 흘려보낸다.
     */
    // QueryPermissionsNeeded: 선언은 `core:io` 의 매니페스트에 있다(VIEW·content — 병합이 앱에 싣는다). 모듈의 lint 는 그 병합을 보지 못한다.
    @SuppressLint("QueryPermissionsNeeded")
    private fun openWithOtherApp() {
        val uri = intent?.data ?: return
        val now = System.currentTimeMillis()
        if (now - openedWithAt in 0 until OPEN_WITH_SETTLE_MS) return
        openedWithAt = now
        val mime = if (::kind.isInitialized) kind.mime else "*/*"
        val typed = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        @Suppress("DEPRECATION") // 플래그 객체를 받는 판은 API 33 부터다(minSdk 31).
        val others = packageManager.queryIntentActivities(typed, PackageManager.MATCH_DEFAULT_ONLY).filter { it.activityInfo.packageName != packageName }
        val widened = others.isEmpty()
        val view = (if (widened) Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*") else typed)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(view, getString(if (widened) IoR.string.io_open_with_any else IoR.string.io_open_with))
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(packageName, OPEN_ACTIVITY)))
        try {
            startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            message(IoR.string.io_open_with_no_app)
        }
    }

    private fun removeFloatingBar() {
        morePopup?.dismiss()
        morePopup = null
        val bar = floatingBar ?: return
        floatingBar = null
        root.removeView(bar)
    }

    /** 화면을 덮는 판 — 글과 단추(위에서 아래로). 단추가 없으면 기다림('여는 중…')이다. 떠 있는 단추 묶음은 걷는다. */
    private fun showOverlay(text: String, actions: List<EditorRules.Action>) {
        hideStatus()
        removeFloatingBar()
        val pad = dp(24)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
            isClickable = true
        }
        box.addView(TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            accessibilityLiveRegion = TextView.ACCESSIBILITY_LIVE_REGION_POLITE
        })
        for ((i, action) in actions.withIndex()) {
            box.addView(Button(this).apply {
                setText(
                    when (action) {
                        EditorRules.Action.REOPEN -> R.string.editor_reopen
                        EditorRules.Action.OPEN_WITH -> IoR.string.io_open_with
                        EditorRules.Action.CLOSE -> R.string.editor_close
                    },
                )
                isAllCaps = false
                minWidth = dp(OVERLAY_BUTTON_MIN_WIDTH_DP)
                setOnClickListener {
                    when (action) {
                        EditorRules.Action.REOPEN -> reopen()
                        EditorRules.Action.OPEN_WITH -> openWithOtherApp()
                        EditorRules.Action.CLOSE -> finish()
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(if (i == 0) 16 else 8) })
        }
        overlay = box
        overlaySpec = OverlaySpec(text, actions)
        root.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun hideStatus() {
        overlay?.let { root.removeView(it) }
        overlay = null
        overlaySpec = null
    }

    /**
     * 화면을 다시 만들지 않는다(매니페스트의 configChanges) — 밤 모드·글꼴 크기·굵기·밀도가 바뀌었으면 이 화면이 지은 것(바탕·떠 있는 단추·덮개)을 새
     * 모양으로 다시 짓는다. 편집기(WebView)는 그대로다 — 쪽은 `textZoom` 100 이라 글꼴 크기를 따르지 않는다. 돌리기만이면 다시 짓지
     * 않는다(⋮ 의 메뉴가 떠 있을 수 있다).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val changes = lastConfig?.diff(newConfig) ?: 0
        lastConfig = Configuration(newConfig)
        if (changes and RESTYLE_CHANGES == 0 || !::root.isInitialized) return
        root.setBackgroundColor(themeColor(android.R.attr.colorBackground))
        overlaySpec?.let { spec -> showOverlay(spec.text, spec.actions) }
        refreshFloatingBar()
    }

    /** 막대·자판·노치에 가리지 않게 — 화면 전체를 쓰되 그 몫만큼 안쪽으로 민다(자판이 뜨면 편집기가 줄어든다). 자판의 보임은 쪽에 알린다. */
    private fun fitInsets() {
        window.setDecorFitsSystemWindows(false)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            val shown = insets.isVisible(WindowInsets.Type.ime())
            if (shown != imeShown) {
                imeShown = shown
                bridge?.imeChanged(shown)
                // 자판 위에 떠 있는 단추('편집'·⋮)가 찾기 칸·대화상자를 가리지 않게.
                floatingBar?.visibility = if (shown) View.GONE else View.VISIBLE
            }
            WindowInsets.CONSUMED
        }
    }

    /**
     * 지난 것을 치운다. 이 프로세스의 첫 화면이면 넘겨받은 문서의 사본(`in/`)과 인쇄·내보내기의 임시본(`spool/`)을 모두 — 그것을 쓰던 화면은
     * 지난 프로세스와 함께 끝났다(강제 종료·재설치처럼 onDestroy 없이 끝나면 남는다 — 문서의 사본을 하루씩 남겨 두지 않는다). 돌려준
     * 파일(`out/`)은 하루 넘은 것만(Files 가 아직 읽고 있을 수 있다).
     */
    private fun sweepOld() {
        if (!sweptThisProcess) {
            sweptThisProcess = true
            for (dir in listOf(File(workDir(), "in"), spoolDir())) dir.listFiles()?.forEach { it.deleteRecursively() }
        }
        val cutoff = System.currentTimeMillis() - DAY_MS
        OutputProvider.rootOf(this).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private fun themeColor(attr: Int): Int {
        val a = obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getColor(0, Color.WHITE)
        } finally {
            a.recycle()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "Editor"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MINUTE_MS = 60_000L

        /** 파일 쪽의 '다른 앱에서 열기' 화면 — 이 편집기를 다시 띄우므로 [openWithOtherApp] 의 고르는 창에서 뺀다. 이름으로만 안다(`app` 을 보지 않는다). */
        private const val OPEN_ACTIVITY = "io.github.donggi.iroiroviewer.OpenActivity"

        /** 화면 켜 두기의 상한(분) — 파일 쪽 설정의 상한(`AppPreferences.KEEP_SCREEN_MAX_MINUTES`)과 같다. 이 모듈은 그 저장소를 보지 않는다. */
        private const val KEEP_SCREEN_MAX_MINUTES = 60

        /**
         * 떠 있는 단추 묶음('편집'·⋮)을 화면 아래에서 띄우는 높이(dp). 엔진의 아래 막대 — rhwp 의 상태 표시줄, ONLYOFFICE 의 쪽·배율 막대 — 를
         * 가리지 않게 넉넉히 잡았다(막대의 높이는 엔진 판마다 다르다).
         */
        private const val EDIT_BUTTON_LIFT_DP = 72

        /** ⋮ 단추의 지름(dp) — 누르는 자리 48dp 를 넘긴다. */
        private const val MORE_SIZE_DP = 48

        /** 덮개(오류 화면)의 단추 폭 하한(dp) — 위아래로 쌓인 단추들의 폭을 얼추 맞춘다. */
        private const val OVERLAY_BUTTON_MIN_WIDTH_DP = 160

        private const val MENU_NAME = 1
        private const val MENU_OPEN_WITH = 2

        /** '다른 앱으로 열기' 를 두 번 눌러 고르는 창이 둘 뜨지 않게 — 첫 누름 뒤 이만큼(ms)의 누름은 흘려보낸다. */
        private const val OPEN_WITH_SETTLE_MS = 700L

        /**
         * 뒤로 가기에서 쪽이 답할 때까지 기다리는 시간(ms) — 쪽의 메뉴·대화상자를 닫았는지. 넘기면 쪽이 멈춘 것으로 보고 닫기로 간다(뒤로 가기가
         * 먹통이 되지 않게).
         */
        private const val BACK_TIMEOUT_MS = 600L

        /** 보기 → 편집에서 읽던 자리를 묻고 기다리는 시간(ms) — 원본을 다시 읽는 동안 함께 기다린다. 넘기면 처음부터 연다. */
        private const val POSITION_TIMEOUT_MS = 800L

        /** 이 화면이 지은 것(바탕·단추·덮개)을 다시 지어야 하는 설정 바뀜 — 밤 모드·글꼴 크기·굵기·밀도. */
        private const val RESTYLE_CHANGES =
            ActivityInfo.CONFIG_UI_MODE or ActivityInfo.CONFIG_FONT_SCALE or ActivityInfo.CONFIG_FONT_WEIGHT_ADJUSTMENT or ActivityInfo.CONFIG_DENSITY

        /** 이 프로세스에서 [sweepOld] 가 지난 프로세스의 것을 치웠는가(주 스레드에서만). */
        private var sweptThisProcess = false
        private const val REQUEST_IMAGE = 1
        private const val REQUEST_EXPORT = 2
        private const val PASTE_LIMIT = 8 * 1024 * 1024

        /** 저장 자리의 표 — Files 의 `EditorSlots` 와 같은 꼴(128비트 16진). */
        private val SLOT_TOKEN = Regex("[0-9a-f]{32}")
        private const val REASON_DENIED = "denied"
        private const val REASON_GONE_APP = "gone-app"

        /** 인쇄·내보내기를 잠깐 두는 자리(앱 캐시의 spool/). */
        private const val SPOOL = "spool"

        /** 어댑터가 문서의 링크를 열지 묻는 prompt 의 글(iroiro-rhwp.js·iroiro-onlyoffice.js 의 LINK_PROMPT 와 같아야 한다). */
        private const val LINK_PROMPT = "iroiro:open-link"
        private const val LINK_LIMIT = 8192
        private val WEB_SCHEMES = setOf("http", "https")

        /** 그림 넣기가 받는 형식 — rhwp 의 그림 넣기가 대는 것(insert.ts)과 같다. */
        private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/bmp", "image/webp", "image/*")
    }
}
