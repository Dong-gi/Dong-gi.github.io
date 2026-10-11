package io.github.donggi.iroirobrowser.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.InputType
import android.text.format.Formatter
import android.util.Log
import android.util.TypedValue
import android.view.ContextMenu
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.EditorInfo
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.webkit.HttpAuthHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.webkit.Navigation
import androidx.webkit.NavigationListener
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import io.github.donggi.iroirobrowser.AppUserAgent
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.adblock.AdBlockService
import io.github.donggi.iroirobrowser.core.DataUrl
import io.github.donggi.iroirobrowser.core.adblock.AdBlocker
import io.github.donggi.iroirobrowser.core.adblock.CosmeticEngine
import io.github.donggi.iroirobrowser.core.adblock.HideSelectors
import io.github.donggi.iroirobrowser.core.adblock.Request
import io.github.donggi.iroirobrowser.core.adblock.RequestType
import io.github.donggi.iroirobrowser.core.media.FoundMediaLog
import io.github.donggi.iroirobrowser.core.media.MediaKind
import io.github.donggi.iroirobrowser.core.media.MediaSniffer
import io.github.donggi.iroirobrowser.core.sites.SiteTarget
import io.github.donggi.iroirobrowser.core.sites.SiteUrls
import io.github.donggi.iroirobrowser.download.DownloadCenter
import io.github.donggi.iroirobrowser.core.Omnibox
import io.github.donggi.iroirobrowser.core.Referrer
import io.github.donggi.iroirobrowser.download.DownloadsActivity
import io.github.donggi.iroirobrowser.download.GalleryPrompt
import io.github.donggi.iroirobrowser.download.GalleryRules
import io.github.donggi.iroirobrowser.download.TorrentPrompt
import io.github.donggi.iroirobrowser.download.TorrentRules
import io.github.donggi.iroirobrowser.library.LibraryActivity
import io.github.donggi.iroirobrowser.proxy.NetworkPath
import io.github.donggi.iroirobrowser.proxy.NetworkPath.Phase
import io.github.donggi.iroirobrowser.settings.SettingsActivity
import io.github.donggi.iroirobrowser.tether.TetherActivity
import io.github.donggi.iroirobrowser.ui.Ui
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

/**
 * 브라우저 화면 — 위의 막대(프록시 스위치·주소창·새로 고침·탭·메뉴), 진행 막대, 알림 띠, 탭의 WebView.
 *
 * **막대의 몸짓**(사용자 요청, 2026-10-05 — 크롬·파이어폭스의 관행대로): 막대를 좌우로 밀면 앞·뒤 탭(손가락을 따라 이웃 탭이 들어온다),
 * 아래로 당기면 곧바로 탭 목록([ToolbarGestureLayout]). 주소창을 고치는 중·찾기 줄이 열린 중에는 몸짓을 받지 않는다. 주소창에 치면 방문 기록·
 * 북마크·열린 탭에서 제안하고 아는 주소는 이어 채운다([AddressSuggestions]).
 *
 * **전체 화면**(사용자 요청, 2026-10-08 — 설정의 '전체 화면', 기본 켬): 상태 표시줄과 막대(진행 막대째)를 함께 숨기고 페이지가 화면 맨 위부터
 * 채운다(노치 자리까지 — `SHORT_EDGES`). 아래 내비게이션 바는 남긴다. **화면 맨 위 가장자리에서 아래로 밀 때만** 둘이 함께 나타나는데([BrowserFrame] —
 * 그 밀기는 페이지로 가지 않는다), 막대는 페이지를 밀어내지 않고 **위에 겹친다**(페이지가 다시 배치되거나 스크롤이 바뀌지 않게). 페이지를 누르거나
 * 막대를 만지지 않은 채 [AUTO_HIDE_MS] 가 지나면 다시 숨는다([BarTimer]) — 주소창을 고치는 중·찾기 줄·메뉴와 창(창이 초점을 잃는다)·막대의 몸짓·
 * 새 탭의 시작 화면 동안은 숨지 않는다. 알림 띠·받기 줄·찾기 줄은 막대 아래의 줄들([TopStrips])이라 막대가 숨으면 화면 맨 위(노치 밑)에서 페이지를
 * 밀어내고, 막대가 나타나면 막대 바로 아래로 내려온다 — 프록시 문제의 띠는 늘 보인다. TalkBack 을 켰거나 화면을 나눠 쓰면 지금까지처럼 그린다.
 *
 * **프록시는 왼쪽 위의 스위치 하나로 켜고 끈다**(사용자 지정, 2026-10-04 — 묻지 않는다, 다른 곳에는 두지 않는다). 스위치 옆 글은 길의 상태다
 * (출구 나라·확인 중·끊김·계정 거절…, [ProxyStatus]) — **스위치와 따로 된 글**이라 누르면 '연결 상태' 가 열린다(앞 판은 스위치 자신의 글이라
 * 뜻을 보려고 누르면 프록시가 꺼졌다). 켜고 끄면 프록시의 주소·계정은 그대로 두고 켜기만 바꾼다.
 *
 * 주 문서가 열리지 않으면 그 탭 위에 '열지 못함' 덮개([ErrorCover] — 까닭·다시 시도·연결 상태)를 덮는다. 닫은 탭은 되돌릴 수 있다
 * ([ClosedTabs] — 아래의 '탭을 닫았습니다 · 되돌리기', 메뉴의 '최근 닫은 탭'). 쪽 맨 위에서 쪽을 당기면 새로 고친다([PullRefreshLayout]).
 * 탭마다 '데스크톱 사이트'. 사용자가 누르지 않은 팝업은 막고 알린다(사이트마다 허용).
 *
 * **쪽은 길이 걸린 뒤에만 연다**([NetworkPath.whenReady]) — 걸리기 전에 연 쪽은 옛 길(켜던 중이면 직접)로 나간다.
 * 길이 바뀌면(설정을 고쳤다·프록시를 켜고 껐다) 열린 탭을 모두 다시 불러 옛 길에 붙어 있던 연결을 끊는다.
 *
 * 탭: 열린 탭의 주소는 앱이 다시 뜰 때 되살린다([SessionStore]). WebView 는 [MAX_LIVE_TABS] 개까지만 살려 두고 오래 안 본 탭은 접는다
 * (기록은 메모리에 두고 고르면 되살린다). 고리·그림을 길게 누르면 새 탭에서 열기·복사·공유·그림 받기.
 *
 * 주소가 없는 탭에는 시작 화면([StartPage] — 북마크·자주 간 곳)을 보인다. 다 연 쪽은 방문 기록에 적는다(중계가 막은 쪽·오류 쪽은 빼고).
 * 메뉴: 북마크에 추가·빼기, 북마크·방문 기록([LibraryActivity] — 고른 주소는 결과로만 받는다), 쪽에서 찾기(아래의 찾기 막대).
 *
 * 광고 차단([AdBlockService]): 요청을 [intercept] 에서 막고(WebView 의 스레드), 요소 숨김은 쪽을 시작할 때 그 사이트의 것과 열쇠 없는 일반
 * 규칙을, 다 연 뒤(그리고 2.5초·7초 뒤) 쪽이 가진 클래스·id 를 모아 그에 맞는 일반 규칙을 넣는다 — 쪽에 남는 전역 이름은 없다. 메뉴에서
 * 사이트마다 끈다.
 *
 * **요소 숨기기**(사용자 요청, 2026-10-08): 메뉴의 '요소 숨기기'(고리·그림을 길게 눌렀으면 '이 요소 숨기기' — 그 자리부터)로 고르는 상태
 * ([ElementPicker] — 덮개가 누름을 가로채고, 고르는 스크립트는 페이지가 못 보는 실행 세계에서 고르는 동안만 깬다)에 들어가 고른 요소를 그
 * 사이트에서 숨긴다. 규칙(`사이트##선택자`)은 광고 목록과 따로 저장하고([HiddenElements]) 페이지를 시작할 때 광고 차단의 요소 숨김과 같은 길로
 * 넣는다 — 광고 차단을 끄거나 그 사이트에서 끈 때도 건다. 메뉴의 '이 사이트에서 숨긴 요소 되돌리기' 가 그 사이트의 규칙을 지우고 다시 연다.
 *
 * 미디어: 쪽이 낸 요청 가운데 영상·소리·HLS·DASH 를 주소로 가려 모으고(광고 차단과 따로), 있으면 막대에 '찾은 미디어' 단추(지금 페이지에서
 * 찾은 수)를 보인다. 목록은 지우지 않고 **탐색이 커밋될 때 페이지의 경계만 옮긴다**([FoundMediaLog] — 탐색 알림의 시작 시각과 함께,
 * [watchNavigation]). 창들은 [MediaChooser] — HLS·DASH 는 목록을 읽어 화질을 고르게 하고(따로 된 소리는 받아 한 파일로 담는다), 실시간
 * 방송이면 '재생 중인 것 받기' 로 잇는다.
 *
 * **재생 중인 것 받기**(7단계 — 사용자: '스트리밍 비디오 직접 다운로드가 어려운 경우 재생 중인 오디오(가능하면 비디오 포함)를 다운로드',
 * 2026-10-05: 'MSE 가로채기·실시간 녹화 둘 다 — 사용자가 선택'): 메뉴나 '찾은 미디어' 에서 가로채기(쪽을 다시 열고 재생기가 받는 바이트를 그대로)·
 * 녹화(지금부터 실시간)·소리만 녹화를 고른다. 막대 아래 줄이 받은 양을 보이고 저장·취소를 둔다([PageBridge]) — 다른 탭에서 받는 중이면 그것을
 * 알리고 탭 단추·탭 목록에도 표시한다. 받는 동안 포그라운드 서비스와 알림([CaptureService]), 탭을 닫으면 묻는다([settleCaptures]), 앱이
 * 정리되어 저장하지 못한 것은 다음에 뜰 때 묻는다([CaptureRecovery]). `blob:` 내려받기도 같은 통로로 받는다.
 *
 * **토렌트**(2026-10-09 — 사용자 결정 ⑱·⑲·⑳): 페이지의 `.torrent` 내려받기는 '토렌트로 받기 / 파일로 저장', 마그넷 고리와 주소창의 `magnet:` 은
 * '이 앱에서 받기 / 다른 앱으로' 를 묻는다([TorrentPrompt] — 주소창의 마그넷은 더 이상 검색어가 되지 않는다). 토렌트는 프록시 스위치와 상관없이
 * 기기에서 바로 받는다 — '연결 상태' 가 그 예외를 늘 말한다.
 *
 * **갤러리 받기**(2026-10-11 — 사용자: 'pixiv, hitomi 기능을 브라우저에 통합'): 지금 페이지의 커밋된 주소나 길게 누른 고리를 [SiteUrls] 가
 * 알아보면(pixiv 작품·작가, hitomi 갤러리) ⋮ 메뉴와 길게 누르기 메뉴에 한 줄('이 작품 받기' …)이 생긴다 → [GalleryPrompt]. 받기는 내려받기와 같은
 * 길(프록시·그 탭의 WebView 쿠키)이다. 주소를 붙여 넣어 받는 길과 다른 앱의 VIEW 인텐트는 없다.
 */
@Suppress("DEPRECATION") // Switch: 플랫폼 위젯을 쓴다(새 의존 없이)
class BrowserActivity : Activity(), PageHost {

    private lateinit var app: WebApp
    override val network: NetworkPath get() = app.network
    private lateinit var session: SessionStore

    private lateinit var root: BrowserFrame

    /** 막대와 그 밑의 진행 막대 — 한 덩이로 나타나고 숨는다(전체 화면이면 쪽 위에 겹친다). */
    private lateinit var chrome: LinearLayout
    private lateinit var bar: ToolbarGestureLayout

    /** 막대 아래의 줄들 — 찾기·받기(녹화·가로채기)·알림 띠. 쪽을 밀어낸다. */
    private lateinit var strips: TopStrips
    private lateinit var proxySwitch: Switch

    /** 스위치 옆의 상태 글 — 누르면 '연결 상태'. 스위치의 글이 아니다([ProxyStatus]). 비면 숨는다. */
    private lateinit var proxyStatus: TextView
    private lateinit var address: EditText
    private lateinit var reload: ImageButton
    private lateinit var mediaButton: ImageButton
    private lateinit var tabsButton: TextView
    private lateinit var progress: ProgressBar
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: TextView

    /** 탭의 WebView·시작 화면·오류 덮개를 담는 자리 — 쪽 맨 위에서 당기면 새로 고친다. */
    private lateinit var content: PullRefreshLayout
    private lateinit var errorCover: ErrorCover

    /** 아래에 잠깐 뜨는 한 줄('탭을 닫았습니다 · 되돌리기', '팝업을 막았습니다 · 이 사이트 허용'). */
    private lateinit var messages: MessageBar

    /** 닫은 탭(되돌리기·최근 닫은 탭) — 앱이 사는 동안 메모리에만. */
    private val closed = ClosedTabs()

    /** 쪽의 자리 — [content](탭의 WebView·시작 화면)와 그 위에 덮는 것들(밀 때 들어오는 이웃 탭 [peek], 제안 목록). */
    private lateinit var stage: FrameLayout
    private lateinit var peek: FrameLayout
    private lateinit var peekTitle: TextView
    private lateinit var peekHost: TextView
    private lateinit var suggestions: AddressSuggestions
    private lateinit var fullscreen: FrameLayout
    private lateinit var startPage: StartPage
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView
    private lateinit var captureBar: LinearLayout
    private lateinit var captureText: TextView
    private lateinit var captureSave: Button
    private lateinit var captureCancel: Button

    /** 막대 아래 줄의 '그 탭으로' — 다른 탭에서 받는 중일 때. */
    private lateinit var captureGo: Button

    /** '찾은 미디어' 의 창들(목록·받기 확인·화질). */
    private lateinit var chooser: MediaChooser

    /** 토렌트로 들어가는 길 — `.torrent` 내려받기·마그넷 고리·주소창의 마그넷을 묻는다(사용자 결정 ⑱·⑲·⑳). */
    private lateinit var torrentPrompt: TorrentPrompt

    /** 갤러리 받기로 들어가는 길 — 메뉴·길게 누르기의 '이 작품 받기' … 를 묻는다(pixiv·hitomi). */
    private lateinit var galleryPrompt: GalleryPrompt

    /** 요소 숨기기의 고르는 상태 — 쪽의 자리 위의 덮개와 아래 막대. 화면을 다시 지으면 새로 만든다(고르던 것은 끝낸다). */
    private lateinit var picker: ElementPicker

    /** 탐색 알림(androidx.webkit 의 NavigationListener — 실험 표의 NAVIGATION_LISTENER)을 아는 WebView 인가. 모르면 [onUrlChanged] 가 경계를 옮긴다. */
    private val navigationListenerSupported by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER) }

    private val tabs = ArrayList<Tab>()
    private var current: Tab? = null
    private var nextTabId = 1L

    /** 막대를 미는 동안 들어오는 이웃 탭(없으면 null — 끝이다). 그 탭의 WebView 를 [peek] 에 잠시 빌려 둔다. */
    private var swipeNeighbor: Tab? = null
    private var swipeDirection = 0
    private var gestureAnimating = false

    /** 길이 걸리기를 기다리는 일(쪽 열기). [whenReady]. */
    private val pending = ArrayList<() -> Unit>()
    private var seenRevision = -1

    /** [render] 가 스위치를 지금 설정에 맞추는 중 — 사용자의 손이 아니다. */
    private var rendering = false

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    /** 전체 화면 영상에 들어가기 전의 화면 방향 — 나올 때 되돌린다(들어가 있지 않으면 null). */
    private var orientationBeforeFullscreen: Int? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** 창이 초점을 되찾으면 주소창에 자판을 띄운다 — 새 탭을 탭 목록 창에서 열면 창이 닫히는 중이라 바로는 못 띄운다. */
    private var keyboardPending = false

    /** 마지막으로 '팝업을 막았습니다' 를 알린 탭과 쪽(`탭 번호 주소`) — 같은 쪽에서 거듭 막히면 다시 알리지 않는다. */
    private var lastPopupNotice = ""

    /** 지금 전체 화면으로 그리는가 — 설정·TalkBack·분할 화면으로 정한다([applyFullscreen]). */
    private var fullscreenMode = false

    /** 전체 화면에서 막대가 보이는가·언제 숨나. 전체 화면이 아니면 쓰지 않는다(막대는 늘 보인다). */
    private val barTimer = BarTimer(AUTO_HIDE_MS)

    /** 터치 탐색(TalkBack)이 켜져 있다 — 그 동안은 전체 화면으로 그리지 않는다(가장자리 밀기를 찾을 수 없다). */
    private var touchExploration = false
    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { on ->
        touchExploration = on
        if (!isDestroyed) applyFullscreen()
    }

    /** 막대(와 그 아래로 내려온 줄들)를 만지는 중 — 손을 떼면 숨기까지를 처음부터 센다. */
    private var chromeTouched = false

    /** 인셋에서 읽은 것 — 노치의 깊이, 막대를 부르는 가장자리 띠의 높이(px). */
    private var cutoutPx = 0
    private var edgePx = 0

    /** 쪽의 자리 맨 위에서 가려진 높이 — 시작 화면·제안 목록·덮개가 그만큼 내려 그린다([renderCovered]). -1 은 아직 맞추지 않음. */
    private var coveredTop = -1
    private val autoHide = Runnable { checkAutoHide() }
    private val coveredTask = Runnable { renderCovered() }

    private val main = Handler(Looper.getMainLooper())
    private val saveSession = Runnable { saveSessionNow() }
    private val stateListener = NetworkPath.Listener { render(it) }
    private var builtNight = false
    /**
     * 받기 줄을 2초마다 — 글이 바뀔 때만 고친다(쉼 없이 고치면 화면이 늘 바쁘다 — 접근성 이벤트가 끊이지 않는다). 어느 탭이든 받는 동안 돈다
     * (다른 탭에서 받는 중이라는 줄과 알림의 제목도 함께 고친다).
     */
    private val captureTick = object : Runnable {
        override fun run() {
            renderCaptureBar()
            publishCaptures()
            if (tabs.any { it.bridge?.busy == true }) main.postDelayed(this, CAPTURE_TICK_MS)
        }
    }

    /** 탭의 통로가 알리는 것 — 녹화·가로채기의 상태와 끝. */
    private val bridgeEvents = object : PageBridge.Events {
        override fun trace(message: String) = this@BrowserActivity.trace(message)

        override fun onCaptureChanged(tab: Tab) {
            if (isDestroyed) return
            renderCaptureBar()
            renderTabCount()
            publishCaptures()
            main.removeCallbacks(captureTick)
            main.postDelayed(captureTick, CAPTURE_TICK_MS)
        }

        override fun onRecordingFinished(tab: Tab, session: RecordingSession, saved: Boolean) {
            if (!saved) {
                session.discard()
                CaptureRegistry.release(session.dir)
                return
            }
            session.close()
            saveRemux(session.title.ifBlank { captureName(tab) }, session.dir) { session.job() }
            trace("recording saved bytes=${session.bytes}")
            if (!isDestroyed) Toast.makeText(this@BrowserActivity, R.string.capture_saving, Toast.LENGTH_LONG).show()
        }

        override fun onCaptureEnded(tab: Tab, session: CaptureSession, ask: Boolean) {
            when {
                // 담긴 것이 없다 — 물을 것도 저장할 것도 없다.
                session.savables().isEmpty() -> {
                    session.discard()
                    CaptureRegistry.release(session.dir)
                    if (ask && !isDestroyed) Toast.makeText(this@BrowserActivity, R.string.capture_left_empty, Toast.LENGTH_LONG).show()
                }
                ask && !isDestroyed -> askLeftSite(tab, session)
                else -> saveCaptureSession(tab, session, null, audioOnly = false)
            }
        }

        override fun onCaptureOverflow(tab: Tab) {
            if (isDestroyed) return
            Toast.makeText(this@BrowserActivity, R.string.capture_overflow, Toast.LENGTH_LONG).show()
            renderCaptureBar()
        }

        override fun onPlayingChanged(tab: Tab) {
            if (isDestroyed) return
            if (tab == current) updateScreenOn()
            if (tab == bgTab) onBackgroundPlayChanged(tab)
        }

        override fun onMediaTitleChanged(tab: Tab) {
            if (isDestroyed) return
            if (tab == bgTab) syncBackgroundPlay()
        }

        // 유튜브 광고 가리기(B1) — 광고 차단이 켜져 있고 그 사이트에서 끄지 않았을 때만(메뉴의 사이트별 끄기가 탈출구다).
        override fun ytGuardWanted(topHost: String): Boolean =
            app.adblock.let { YtGuard.wanted(it.enabled, topHost, it::isAllowed) }

        // 유튜브 광고 건너뛰기(B2) — 건너뛸 수 있게 된 단추를 진짜 터치로 누른다(앱이 앞에 있든 뒤에 있든). 전체 화면 영상이면 쪽이 그 화면에 있다.
        override fun onYtSkip(tab: Tab, x: Double, y: Double) {
            if (isDestroyed) return
            val page = tab.webView as? PageWebView ?: return
            val shown = customView?.takeIf { tab == current }
            val target = shown ?: page
            val point = YtGuard.tapPoint(x, y, target.width, target.height) ?: return
            val pressed = if (shown != null) {
                injectTap(point.first, point.second) { shown.dispatchTouchEvent(it) }
                true
            } else {
                page.tap(point.first, point.second)
            }
            trace("ytskip ${if (pressed) "tap" else "busy"} fullscreen=${shown != null} fg=$foreground")
        }
    }

    /** 재생 중 화면 켜 두기 — 지금 창에 `FLAG_KEEP_SCREEN_ON` 을 걸어 두었는가([updateScreenOn]). */
    private var keepingScreenOn = false

    /** 틀 하나가 오래될 때 다시 본다([PlayingMedia.nextStale]). */
    private val screenCheck = Runnable { updateScreenOn() }

    // ── 뒤에서 재생 잇기(BackgroundPlay) ──

    /** 앞에 있는가(onResume 부터 onPause 까지). */
    private var foreground = false

    /**
     * 뒤에서 재생을 잇는 탭(회) — 하나뿐이다. 앱이 뒤에 있는 동안의 지금 탭([PageWebView.keepWindowVisible])이거나, 다른 탭을 고른 사이 쪽의
     * 자리에 보이지 않게 남은 탭([PageWebView.backstaged]).
     */
    private var bgTab: Tab? = null

    /** 그 탭의 미디어가 멈춘 때(재생 중이면 null) — [BackgroundPlay.PAUSE_GRACE_MS] 가 지나면 회를 닫는다. */
    private var bgStoppedAt: Long? = null

    private val bgCheck = Runnable { checkBackgroundPlay() }

    /** 알림·잠금 화면·이어폰 단추([BackgroundPlayService]). */
    private val playbackController = object : BackgroundPlayback.Controller {
        override fun play() {
            bgTab?.bridge?.mediaControl(play = true)
        }

        override fun pause() {
            bgTab?.bridge?.mediaControl(play = false)
        }

        override fun close() = endBackgroundPlay(pauseMedia = true)
    }

    /** '찾은 미디어' 의 창이 묻는 것. */
    private val chooserHost = object : MediaChooser.Host {
        override fun trace(message: String) = this@BrowserActivity.trace(message)

        override fun chooseCapture() = this@BrowserActivity.chooseCapture()

        override fun askNotificationPermission() = this@BrowserActivity.askNotificationPermission()

        override fun onMediaChanged(tab: Tab) {
            if (tab == current && !isDestroyed) renderMediaButton()
        }
    }

    /** 토렌트의 묻기가 쓰는 것. */
    private val torrentHost = object : TorrentPrompt.Host {
        override fun trace(message: String) = this@BrowserActivity.trace(message)

        override fun askNotificationPermission() = this@BrowserActivity.askNotificationPermission()

        override fun showMessage(text: String, action: String, onAction: () -> Unit) {
            if (!isDestroyed) messages.show(text, action, onAction)
        }
    }

    /** 갤러리 받기의 묻기가 쓰는 것. */
    private val galleryHost = object : GalleryPrompt.Host {
        override fun trace(message: String) = this@BrowserActivity.trace(message)

        override fun askNotificationPermission() = this@BrowserActivity.askNotificationPermission()

        override fun showMessage(text: String, action: String, onAction: () -> Unit) {
            if (!isDestroyed) messages.show(text, action, onAction)
        }
    }

    /** 알림의 '저장'·'취소' — 받는 모든 탭에(저장은 고르지 않은 기본 — 녹화는 멈추고 저장, 가로채기는 담긴 것이 가장 많은 영상과 소리). */
    private val captureHost = CaptureRegistry.Host { save ->
        trace("capture notice ${if (save) "save" else "cancel"}")
        for (tab in tabs.filter { it.bridge?.busy == true }) {
            val bridge = tab.bridge ?: continue
            when {
                bridge.recording != null -> bridge.stopRecording(save)
                save -> bridge.endCapture()?.let { saveCaptureSession(tab, it, null, audioOnly = false) }
                else -> bridge.endCapture()?.let {
                    it.discard()
                    CaptureRegistry.release(it.dir)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = WebApp.of(this)
        session = SessionStore(this)
        chooser = MediaChooser(this, chooserHost)
        torrentPrompt = TorrentPrompt(this, torrentHost)
        galleryPrompt = GalleryPrompt(this, galleryHost)
        CaptureRegistry.host = captureHost
        BackgroundPlayback.controller = playbackController
        getSystemService(AccessibilityManager::class.java)?.let {
            touchExploration = it.isTouchExplorationEnabled
            it.addTouchExplorationStateChangeListener(touchExplorationListener)
        }
        buildUi()
        network.addListener(stateListener)
        restoreSession()
        seenRevision = network.revision
        network.engageWebView()
        whenReady { trace("ready") }
        if (savedInstanceState == null && network.settings.enabled && !network.settings.isComplete) {
            // 처음: 프록시를 켰는데 정하지 않았으면 아무 쪽도 열리지 않는다(중계가 막는다). 설정으로 먼저 보낸다 — 정하거나, 돌아와 스위치로 끈다.
            openSettings()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }
        offerLeftoverCaptures()
    }

    override fun onResume() {
        super.onResume()
        network.setForeground(true)
        foreground = true
        // 뒤에서 재생을 이었던 탭은 창이 실제로 다시 보일 때 저절로 실제 창 상태로 돌아간다(PageWebView.keepWindowVisible). 그 탭을 앞에서 보고
        // 있으면 회를 닫는다(알림이 필요 없다 — 다시 뒤로 가면 그때 다시 연다). 다른 탭이 앞이면 알림을 남긴다(단추로 멈출 수 있게).
        if (bgTab != null && bgTab == current) endBackgroundPlay(pauseMedia = false)
        syncBackgroundPlay()
        current?.webView?.onResume()
        // 전체 화면 — 설정 화면에서 바꿨을 수 있다. 돌아오면 막대는 숨긴 상태로(주소창을 고치던 중이면 그대로).
        applyFullscreen()
        applyForceZoom()
        // 유튜브 광고 가리기 — 광고 차단 설정 화면에서 바꾼 것을 이미 연 유튜브 틀에도 곧바로 알린다.
        tabs.forEach { it.bridge?.refreshYtGuard() }
        if (network.revision != seenRevision) {
            // 설정 화면에서 길이 바뀌었다 — 열린 탭을 다시 불러 옛 연결을 끊는다.
            reloadAllForNewPath()
        }
        // 내려받기 목록·알림의 '원래 페이지 열기' — 만료된 주소를 다시 고르러 그 페이지를 새 탭에서 연다.
        DownloadCenter.takePageToOpen()?.let { url -> load(newTab(select = true), url) }
        // 뒤에 있던 동안 쪽의 다시 알림이 늦었을 수 있다 — 지금 탭의 틀들에 재생 중인 것을 다시 묻는다.
        current?.bridge?.askPlaying()
        updateScreenOn()
    }

    override fun onPause() {
        network.setForeground(false)
        foreground = false
        // 뒤에서 재생을 잇는 탭(유튜브의 소리가 나는 재생 — BackgroundPlay)은 멈추지 않고 창이 보인다고 알린다 — 멈추면 크로미움과 쪽이 재생을
        // 멈춘다. 쪽의 자리에 보이지 않게 남은 탭도 창과 함께 숨으므로 같이 알린다. 서비스는 지금 세운다 — 막 뒤로 가는 순간이라 세울 수 있다.
        val tab = current
        if (tab != null && (tab == bgTab || playsInBackground(tab))) {
            beginBackgroundPlay(tab)
        } else {
            tab?.webView?.onPause()
        }
        (bgTab?.webView as? PageWebView)?.keepWindowVisible = true
        syncBackgroundPlay()
        main.removeCallbacks(autoHide)
        super.onPause()
    }

    // ── 뒤에서 재생 잇기(BackgroundPlay) ──────────────────────────────────────────────────────────────────────────

    /** [tab] 이 뒤에서도 재생을 잇는가([BackgroundPlay.wanted] — 그 탭의 재생 상태로). */
    private fun playsInBackground(tab: Tab): Boolean {
        val state = tab.bridge?.playing?.state(SystemClock.elapsedRealtime()) ?: return false
        return BackgroundPlay.wanted(tab.url, state, adShowing = tab.bridge?.ytAdShowing == true)
    }

    /** 회의 탭에서 미디어가 재생 중인가(소리가 없어도 — 유튜브 광고 가리기가 광고를 음소거한 동안도 재생이다). */
    private fun bgPlaying(tab: Tab): Boolean =
        tab.bridge?.playing?.state(SystemClock.elapsedRealtime())?.let { it.audible || it.video } == true

    /** [tab] 으로 회를 연다 — 회는 하나라 앞의 회는 멈추고 닫는다. */
    private fun beginBackgroundPlay(tab: Tab) {
        if (bgTab == tab) return
        endBackgroundPlay(pauseMedia = true)
        bgTab = tab
        bgStoppedAt = null
        trace("bgplay begin tab=${tab.id}")
    }

    /**
     * 회를 닫는다 — 그 탭은 여느 탭으로 돌아간다: 쪽의 자리에 보이지 않게 남았던 탭은 멈추고 떼고, 앱이 뒤에 있는 동안의 지금 탭은 숨는다.
     * [pauseMedia] 면 미디어도 멈춘다(알림의 '닫기'·새 회).
     */
    private fun endBackgroundPlay(pauseMedia: Boolean) {
        val tab = bgTab ?: return
        bgTab = null
        bgStoppedAt = null
        main.removeCallbacks(bgCheck)
        trace("bgplay end tab=${tab.id} pause=$pauseMedia")
        if (pauseMedia) tab.bridge?.mediaControl(play = false)
        (tab.webView as? PageWebView)?.let { view ->
            if (view.backstaged) {
                view.backstaged = false
                view.keepWindowVisible = false
                view.onPause()
                detach(view)
            } else if (tab == current && !foreground) {
                view.keepWindowVisible = false
                view.onPause()
            }
        }
        syncBackgroundPlay()
    }

    /** 회의 탭의 재생이 바뀌었다 — 멈춘 때를 적거나 지우고 알림을 고친다. 유튜브를 떠났으면 닫는다. */
    private fun onBackgroundPlayChanged(tab: Tab) {
        if (!BackgroundPlay.isYouTube(tab.url)) {
            endBackgroundPlay(pauseMedia = false)
            return
        }
        if (bgPlaying(tab)) {
            bgStoppedAt = null
            main.removeCallbacks(bgCheck)
        } else if (bgStoppedAt == null) {
            bgStoppedAt = SystemClock.elapsedRealtime()
            main.removeCallbacks(bgCheck)
            main.postDelayed(bgCheck, BackgroundPlay.PAUSE_GRACE_MS)
        }
        syncBackgroundPlay()
    }

    /** 멈춘 지 오래됐으면 회를 닫는다(Handler 의 시계는 기기가 잠든 동안 서지만 — 회가 조금 길어질 뿐이다). */
    private fun checkBackgroundPlay() {
        val stopped = bgStoppedAt ?: return
        val now = SystemClock.elapsedRealtime()
        if (BackgroundPlay.expired(stopped, now)) {
            endBackgroundPlay(pauseMedia = false)
        } else {
            main.postDelayed(bgCheck, stopped + BackgroundPlay.PAUSE_GRACE_MS - now)
        }
    }

    /** 알림(서비스)을 지금 회에 맞춘다([BackgroundPlay.serviceWanted]) — 앞에서 그 탭을 보고 있으면 알림을 걷는다. */
    private fun syncBackgroundPlay() {
        val tab = bgTab
        val state = if (tab != null && BackgroundPlay.serviceWanted(alive = true, foreground = foreground, isCurrent = tab == current)) {
            // 제목은 실행 세계가 화면의 제목 칸에서 읽은 영상 제목(유튜브 모바일 웹은 쪽 제목이 'YouTube' 뿐이다)을 먼저, 없으면 쪽 제목.
            val title = tab.bridge?.mediaTitle?.takeIf { it.isNotEmpty() } ?: BackgroundPlay.titleOf(tab.title, getString(R.string.bgplay_site))
            BackgroundPlayback.State(title, playing = bgPlaying(tab))
        } else {
            null
        }
        BackgroundPlayback.update(this, state)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && keyboardPending) showKeyboard()
        // 메뉴·탭 목록·대화상자·길게 누르기 메뉴·알림 창이 떠 있는 동안 창은 초점을 잃는다 — 그 동안은 막대를 숨기지 않고, 닫히면 처음부터 센다.
        scheduleAutoHide()
    }

    /** 분할 화면·자유형 창에서는 시스템이 상태 표시줄을 숨기지 못하게 한다 — 들고 날 때 전체 화면을 다시 맞춘다. */
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        applyFullscreen()
    }

    override fun onStop() {
        saveSessionNow()
        super.onStop()
    }

    override fun onDestroy() {
        main.removeCallbacks(saveSession)
        main.removeCallbacks(captureTick)
        main.removeCallbacks(screenCheck)
        main.removeCallbacks(autoHide)
        main.removeCallbacks(coveredTask)
        main.removeCallbacks(bgCheck)
        // 뒤에서 재생 잇기 — 화면이 사라지면 탭도 사라진다. 서비스를 걷는다(알림이 남지 않게).
        bgTab = null
        BackgroundPlayback.update(this, null)
        if (BackgroundPlayback.controller === playbackController) BackgroundPlayback.controller = null
        getSystemService(AccessibilityManager::class.java)?.removeTouchExplorationStateChangeListener(touchExplorationListener)
        network.removeListener(stateListener)
        picker.stop()
        suggestions.close()
        messages.hide()
        chooser.close()
        torrentPrompt.close()
        galleryPrompt.close()
        // 받던 녹화·가로채기는 파일째 남긴다 — 다음에 뜰 때 저장할지 묻는다(CaptureRecovery).
        tabs.forEach {
            it.bridge?.dispose(keep = true)
            it.webView?.destroy()
        }
        tabs.clear()
        if (CaptureRegistry.host === captureHost) CaptureRegistry.host = null
        CaptureRegistry.publish(emptyList())
        super.onDestroy()
    }

    /**
     * Android 12 이하의 뒤로. 13 이상은 [onCreate] 에서 건 플랫폼의 `OnBackInvokedCallback` 이 받는다(그때는 이것이 불리지 않는다).
     * lint 는 AndroidX 의 OnBackPressedDispatcher 를 권하지만 새 의존 없이 플랫폼 둘로 같은 일을 한다.
     */
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Android 12 이하의 뒤로 — 13 이상은 OnBackInvokedCallback")
    override fun onBackPressed() {
        handleBack()
    }

    /**
     * 밤 모드가 바뀌었다 — 화면을 다시 만들지 않으므로(매니페스트의 configChanges — 다시 만들면 탭이 죽는다) 막대만 새 색으로 다시 짓고
     * 탭의 WebView 를 옮겨 붙인다. WebView 는 쪽의 `prefers-color-scheme` 을 제가 따른다.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (night == builtNight) return
        hideCustomView()
        val typed = if (address.hasFocus()) address.text.toString() else null
        buildUi()
        render(network.state)
        current?.let { select(it) }
        renderStart()
        typed?.let {
            address.requestFocus()
            address.setText(it)
        }
        // 새로 지은 막대·줄들을 전체 화면의 지금 상태에 맞춘다(보이던 막대는 그대로 보인다).
        applyFullscreen(rebuilt = true)
    }

    private fun handleBack() {
        val tab = current
        when {
            customView != null -> hideCustomView()
            // 고르는 중이면 뒤로 가기는 고르기를 끝낸다(페이지는 그대로).
            picker.isActive -> {
                trace("pick back")
                picker.stop()
            }
            findBar.visibility == View.VISIBLE -> hideFind()
            address.hasFocus() -> {
                address.clearFocus()
                showUrl()
            }
            tab?.webView?.canGoBack() == true -> tab.webView?.goBack()
            tab?.openerId != null && tabs.size > 1 -> settleCaptures(listOf(tab), ask = true) { closeTab(tab) }
            else -> moveTaskToBack(true)
        }
    }

    // ── 화면 ──────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun buildUi() {
        builtNight = Ui.isNight(this)
        if (::suggestions.isInitialized) suggestions.close()
        if (::picker.isInitialized) picker.stop()
        root = BrowserFrame(this, frameListener)
        bar = ToolbarGestureLayout(this, toolbarGestures).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 위 여백은 인셋이 정한다(전체 화면이면 상태 표시줄의 높이를 더한다 — applyInsets).
            setPadding(Ui.dp(context, 8), Ui.dp(context, BAR_PAD_TOP_DP), Ui.dp(context, 2), Ui.dp(context, 4))
            // 전체 화면이면 쪽 위에 겹친다 — 페이지가 비치지 않게 칠한다(뿌리의 바탕과 같은 빛이라 막대 아래에 쪽을 둘 때는 지금과 같다).
            setBackgroundColor(Ui.themeColor(context, android.R.attr.colorBackground))
        }
        // 스위치에는 글을 두지 않는다 — 글이 스위치의 것이면 그 글을 누르는 것이 곧 프록시 끄기였다(묻지 않는 스위치라 열린 탭이 모두 직접
        // 연결로 다시 열렸다). 상태는 옆의 따로 된 글이 보이고, 낭독기는 스위치의 상태 설명으로 듣는다([render]).
        proxySwitch = Switch(this).apply {
            contentDescription = getString(R.string.proxy_switch)
            setOnCheckedChangeListener { _, on -> if (!rendering) setProxyEnabled(on) }
        }
        proxyStatus = TextView(this).apply {
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isSingleLine = true
            // 누르는 자리 — 막대의 높이만큼(48 dp) 잡히게.
            minHeight = Ui.dp(context, 48)
            setPadding(Ui.dp(context, 6), 0, Ui.dp(context, 6), 0)
            setBackgroundResource(TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId)
            visibility = View.GONE
            setOnClickListener { showStatus() }
        }
        address = EditText(this).apply {
            isSingleLine = true
            textSize = 15f
            hint = getString(R.string.address_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setSelectAllOnFocus(true)
            background = Ui.rounded(Color.argb(0x22, 0x88, 0x88, 0x88), 20, context)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8))
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    navigate(text.toString())
                    true
                } else {
                    false
                }
            }
            setOnFocusChangeListener { _, focused ->
                if (!focused) showUrl()
                // 주소를 치기 시작하면 요소 고르기는 끝낸다(제안 목록이 쪽을 덮는다).
                if (focused && ::picker.isInitialized) picker.stop()
                suggestions.onFocusChanged(focused)
                // 전체 화면 — 주소를 고치는 동안은 막대가 보여야 한다(새 탭·다시 지은 화면 등 어디서 초점을 주든). 끝나면 숨기까지를 센다.
                if (focused) showBar(animate = true, why = "focus") else scheduleAutoHide()
            }
        }
        mediaButton = Ui.iconButton(this, R.drawable.ic_download, getString(R.string.media_found)) { current?.let { chooser.show(it) } }.apply {
            visibility = View.GONE
            // 지금 페이지에서 찾은 수 — 단추 위에 그리고(배치를 바꾸지 않게 foreground), 화면 낭독기에는 상태 설명으로(바뀌면 읽는다).
            foreground = CountBadge(this@BrowserActivity)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        reload = Ui.iconButton(this, R.drawable.ic_reload, getString(R.string.reload)) {
            val tab = current ?: return@iconButton
            if (tab.loading) tab.webView?.stopLoading() else reloadTab(tab)
        }
        tabsButton = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Ui.themeColor(context, android.R.attr.textColorPrimary))
            background = Ui.rounded(Color.TRANSPARENT, 5, context, Ui.themeColor(context, android.R.attr.textColorPrimary))
            // 설명은 '탭 3개' — 수가 바뀔 때마다([renderTabCount]). 설명이 화면의 숫자를 덮으므로 수를 설명에 함께 둔다.
            setOnClickListener { showTabs() }
        }
        val more = Ui.iconButton(this, R.drawable.ic_more, getString(R.string.menu)) { showMenu(it) }
        bar.addView(proxySwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addView(proxyStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Ui.dp(this@BrowserActivity, 4)
        })
        bar.addView(mediaButton)
        bar.addView(reload)
        bar.addView(tabsButton, LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)).apply {
            marginStart = Ui.dp(this@BrowserActivity, 8)
            marginEnd = Ui.dp(this@BrowserActivity, 8)
        })
        bar.addView(more)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.INVISIBLE
        }
        banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(Ui.dp(context, 16), Ui.dp(context, 6), Ui.dp(context, 8), Ui.dp(context, 6))
        }
        bannerText = TextView(this).apply {
            textSize = 14f
            // 띠가 새로 뜨거나 글이 바뀌면 화면 낭독기가 읽는다([render] 가 띠를 보인 뒤에 글을 넣는다).
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val bannerAction = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.settings)
            minWidth = 0
            minimumWidth = 0
            setPadding(Ui.dp(context, 12), paddingTop, Ui.dp(context, 12), paddingBottom)
            setOnClickListener { openSettings() }
        }
        banner.addView(bannerText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        banner.addView(bannerAction)
        content = PullRefreshLayout(this, pullListener)
        // 시작 화면에서 고른 곳은 주소창에서 고른 것처럼 연다 — 새 탭이 주소창에 준 초점·자판을 거둔다.
        startPage = StartPage(this, app.library) { url -> navigateTo(url, typed = false) }
        content.addView(startPage.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        errorCover = ErrorCover(this, coverActions)
        content.addView(errorCover.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        buildStage()
        buildFindBar()
        buildCaptureBar()

        // 막대와 진행 막대 — 한 덩이로 나타나고 숨는다. 진행 막대에는 바탕이 없다 — 막대가 숨으면 화면 맨 위에 걸려 페이지 위로 여는 중을 보인다.
        chrome = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chrome.addView(bar, Ui.matchWidth())
        chrome.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)))
        // 막대 아래의 줄들 — 쪽을 밀어낸다(지금처럼). 전체 화면에서 막대가 나타나면 막대 바로 아래로 내려와 페이지 위에 겹치므로 바탕을 칠하고,
        // 뒤에 붙은 쪽의 자리보다 위에 그려지고 먼저 눌리게 높이를 조금 준다(그림자는 두지 않는다).
        strips = TopStrips(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.themeColor(context, android.R.attr.colorBackground))
            translationZ = 1f
            outlineProvider = null
        }
        strips.addView(findBar, Ui.matchWidth())
        strips.addView(captureBar, Ui.matchWidth())
        strips.addView(banner, Ui.matchWidth())
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(strips, Ui.matchWidth())
        column.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(chrome, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.arrange(chrome, column)
        root.overlayChrome = fullscreenMode
        fullscreen = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(fullscreen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        messages = MessageBar(this)
        root.addView(messages.view, messages.params())
        // 막대·줄들의 높이가 바뀌면(돌리기·인셋·줄이 뜨고 짐) 숨은 자리·내려온 자리를 다시 맞춘다 — 그리기 전에(첫 배치에서도) 맞는다.
        val relayout = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) placeChrome()
        }
        chrome.addOnLayoutChangeListener(relayout)
        strips.addOnLayoutChangeListener(relayout)
        coveredTop = -1
        Ui.drawBehindBars(this, root)
        root.setOnApplyWindowInsetsListener { v, insets -> applyInsets(v, insets) }
        setContentView(root)
    }

    /**
     * 인셋 — 뿌리의 여백(막대·키보드·노치. 전체 화면이면 위는 비운다 — 쪽이 화면 맨 위부터), 막대의 위 여백(전체 화면이면 상태 표시줄의 높이 —
     * 숨어 있어도 그 높이를 쓴다: 나타날 때 막대가 다시 배치되지 않게), 줄들의 노치 여백, 가장자리 띠의 높이. 전체 화면 영상 중이면 여백이 없다.
     * 상태 표시줄이 숨고 나타나는 것은 여기서 아무것도 바꾸지 않는다 — 그래서 페이지가 출렁이지 않는다.
     */
    private fun applyInsets(v: View, insets: WindowInsets): WindowInsets {
        val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
        val statusTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        cutoutPx = insets.getInsets(WindowInsets.Type.displayCutout()).top
        edgePx = FullscreenLayout.edgeHeight(statusTop, cutoutPx, Ui.dp(this, EDGE_FALLBACK_DP))
        if (customView != null) {
            v.setPadding(0, 0, 0, 0)
        } else {
            v.setPadding(safe.left, FullscreenLayout.rootTop(fullscreenMode, safe.top), safe.right, safe.bottom)
        }
        val barTop = Ui.dp(this, BAR_PAD_TOP_DP) + if (fullscreenMode) FullscreenLayout.barTop(statusTop, cutoutPx) else 0
        if (bar.paddingTop != barTop) bar.setPadding(bar.paddingLeft, barTop, bar.paddingRight, bar.paddingBottom)
        strips.inset = if (fullscreenMode) cutoutPx else 0
        return insets
    }

    /** 쪽의 자리 — [content] 위에 이웃 탭의 자리([peek])와 제안 목록을 겹친다. */
    private fun buildStage() {
        stage = FrameLayout(this)
        val match = { FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        peek = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Ui.themeColor(context, android.R.attr.colorBackground))
        }
        // 이웃 탭의 WebView 가 없으면(접었거나 아직 열지 않았다) 제목과 호스트를 가운데 보인다.
        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 32), 0, Ui.dp(context, 32), 0)
        }
        peekTitle = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            maxLines = 2
            setTypeface(typeface, Typeface.BOLD)
        }
        peekHost = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            alpha = 0.7f
            isSingleLine = true
        }
        label.addView(peekTitle, Ui.matchWidth())
        label.addView(peekHost, Ui.matchWidth())
        peek.addView(label, match())
        suggestions = AddressSuggestions(this, address, app.library, suggestionHost)
        stage.addView(peek, match())
        stage.addView(content, match())
        // 당겨서 새로 고침의 동그라미 — 쪽 위(밀 때 따라가지 않게 content 밖), 제안 목록 아래.
        stage.addView(content.indicator, content.indicatorParams())
        stage.addView(suggestions.view, match())
        // 요소 숨기기의 덮개와 막대 — 맨 위(덮개가 쪽으로 가는 누름을 모두 받는다). 덮개는 쪽(WebView)과 같은 크기다.
        picker = ElementPicker(this, pickerHost)
        picker.attach(stage)
    }

    /** 주소창 제안이 묻는 것 — 열린 탭, 친 글의 뜻, 고른 줄을 여는 일. */
    private val suggestionHost = object : AddressSuggestions.Host {
        override fun openTabs(): List<AddressSuggestions.OpenTab> =
            tabs.filter { it != current && isWeb(it.url) }.map { AddressSuggestions.OpenTab(it.id, it.url, it.title) }

        override fun describe(text: String): AddressSuggestions.Typed? = when (val t = Omnibox.classify(text)) {
            null -> null
            is Omnibox.Target.Url -> AddressSuggestions.Typed(true, t.url)
            is Omnibox.Target.Search -> AddressSuggestions.Typed(false, app.settings.searchEngine.label)
            // 마그넷은 열 주소도 검색어도 아니다 — '이동' 줄이 토렌트로 넘긴다는 것을 말한다(Enter 와 같다).
            is Omnibox.Target.Magnet -> AddressSuggestions.Typed(true, getString(R.string.torrent_suggest))
        }

        override fun open(url: String) = navigateTo(url, typed = true)

        override fun go(text: String) = navigate(text)

        override fun switchTab(id: Long) {
            val tab = tabs.firstOrNull { it.id == id } ?: return
            suggestions.hide()
            address.clearFocus()
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
            select(tab)
            trace("suggest switch tab=${tab.id}")
        }

        override fun showsStartPage(): Boolean = current?.url.isNullOrEmpty()

        override fun dismiss() {
            suggestions.hide()
            address.clearFocus()
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
            trace("suggest dismissed")
        }
    }

    /** 당겨서 새로 고침 — 지금 탭의 쪽이 맨 위에 있고, 주소창·덮개·전체 화면·탭 넘기기 중이 아닐 때만. */
    private val pullListener = object : PullRefreshLayout.Listener {
        override fun canPull(): Boolean {
            val tab = current ?: return false
            val view = tab.webView ?: return false
            if (view.parent !== content || tab.url.isEmpty() || errorCover.isShowing) return false
            if (address.hasFocus() || customView != null || gestureAnimating) return false
            return !view.canScrollVertically(-1)
        }

        override fun onRefresh() {
            val tab = current ?: return
            trace("pull refresh tab=${tab.id}")
            reloadTab(tab)
        }
    }

    /** '열지 못함' 덮개의 단추. */
    private val coverActions = object : ErrorCover.Actions {
        override fun retry() {
            val tab = current ?: return
            trace("cover retry tab=${tab.id}")
            // 덮개가 있는 탭의 새로 고침은 다시 시도다([reloadTab]).
            reloadTab(tab)
        }

        override fun showStatus() = this@BrowserActivity.showStatus()

        override fun dismiss() {
            val tab = current ?: return
            tab.error?.dismissed = true
            renderError()
        }
    }

    // ── 막대의 몸짓 ───────────────────────────────────────────────────────────────────────────────────────────────

    private val toolbarGestures = object : ToolbarGestureLayout.Listener {
        override fun gesturesEnabled(x: Float, y: Float): Boolean {
            // 주소창을 고치는 중(커서·글 고르기가 먼저다 — 크롬·파이어폭스도 끈다), 찾기 줄, 전체 화면 영상, 앞 몸짓의 마무리 중에는 받지 않는다.
            // 스위치 위에서 시작한 것은 스위치의 것(손잡이를 끈다). 옆의 상태 글은 스위치가 아니다(누르면 '연결 상태') — 거기서 시작한 밀기는 받는다.
            if (address.hasFocus() || findBar.visibility == View.VISIBLE || customView != null || gestureAnimating || current == null) return false
            val r = Rect()
            proxySwitch.getHitRect(r)
            return !r.contains(x.toInt(), y.toInt())
        }

        override fun onSwipe(dx: Float) {
            // 탭을 넘기기 시작했다 — 고르기는 끝낸다(덮개는 쪽과 함께 밀리지 않는다).
            picker.stop()
            dragTabs(dx)
        }

        override fun onSwipeEnd(dx: Float, velocityX: Float, cancelled: Boolean) = endTabDrag(dx, velocityX, cancelled)

        override fun onPullDown() {
            trace("pull tabs")
            picker.stop()
            showTabs()
        }
    }

    /**
     * 막대를 좌우로 미는 중 — 지금 탭이 손가락을 따라가고 [SWIPE_GAP_DP] 떨어져 이웃 탭이 들어온다(왼쪽으로 밀면 다음 탭, 오른쪽이면 앞 탭 —
     * 탭 목록의 차례, 끝에서 처음으로 돌지 않는다). 이웃의 WebView 가 살아 있으면 그것을 [peek] 에 잠시 빌려 실제 쪽을 보인다(크롬·파이어폭스는
     * 떠 둔 그림을 쓴다). 끝이면(더 갈 탭이 없다) 폭의 [EDGE_FRACTION] 까지만 줄어드는 비율로 따라온다.
     */
    private fun dragTabs(dx: Float) {
        val tab = current ?: return
        val direction = if (dx < 0) 1 else -1
        if (direction != swipeDirection) {
            swipeDirection = direction
            showPeek(tabs.getOrNull(tabs.indexOf(tab) + direction))
        }
        val width = stage.width.toFloat()
        if (swipeNeighbor == null) {
            val edge = width * EDGE_FRACTION
            content.translationX = sign(dx) * edge * tanh(abs(dx) / edge)
            return
        }
        val gap = Ui.dp(this, SWIPE_GAP_DP).toFloat()
        content.translationX = dx
        peek.translationX = if (direction > 0) dx + width + gap else dx - width - gap
    }

    /**
     * 밀기를 끝냈다(파이어폭스의 판정): 튕겼으면 민 방향으로 튕겼을 때만 넘기고 반대로 튕겼으면 되돌린다. 튕기지 않았으면 이웃이 폭의
     * [SWIPE_COMMIT_FRACTION] 넘게 보였을 때 넘긴다. 넘기기 [COMMIT_MS], 되돌리기 [CANCEL_MS](반대로 튕겼으면 [FLING_CANCEL_MS]).
     */
    private fun endTabDrag(dx: Float, velocityX: Float, cancelled: Boolean) {
        val neighbor = swipeNeighbor
        val width = stage.width.toFloat()
        val gap = Ui.dp(this, SWIPE_GAP_DP).toFloat()
        val direction = swipeDirection
        swipeDirection = 0
        val flung = abs(velocityX) >= ViewConfiguration.get(this).scaledMinimumFlingVelocity
        val flungBack = flung && sign(velocityX) != sign(dx)
        val commit = !cancelled && neighbor != null && dx != 0f &&
            (if (flung) !flungBack else abs(dx) - gap >= width * SWIPE_COMMIT_FRACTION)
        gestureAnimating = true
        val ease = AnimationUtils.loadInterpolator(this, android.R.interpolator.linear_out_slow_in)
        if (commit) {
            val out = if (direction > 0) -(width + gap) else width + gap
            content.animate().translationX(out).setDuration(COMMIT_MS).setInterpolator(ease).setListener(null).start()
            peek.animate().translationX(0f).setDuration(COMMIT_MS).setInterpolator(ease)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        peek.animate().setListener(null)
                        // 빌려 둔 WebView 는 select 가 제자리(content)로 옮긴다.
                        swipeNeighbor = null
                        if (neighbor in tabs) select(neighbor)
                        resetSwipe()
                        trace("swipe tab=${neighbor.id}")
                    }
                }).start()
        } else {
            val ms = if (flungBack) FLING_CANCEL_MS else CANCEL_MS
            val back = if (direction > 0) width + gap else -(width + gap)
            content.animate().translationX(0f).setDuration(ms).setInterpolator(ease).setListener(null).start()
            peek.animate().translationX(back).setDuration(ms).setInterpolator(ease)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        peek.animate().setListener(null)
                        releasePeek()
                        resetSwipe()
                    }
                }).start()
        }
    }

    private fun resetSwipe() {
        content.translationX = 0f
        peek.translationX = 0f
        peek.visibility = View.GONE
        gestureAnimating = false
        // 탭 넘기기가 끝났다 — 전체 화면이면 숨기까지를 처음부터 센다.
        scheduleAutoHide()
    }

    /** 이웃 탭을 [peek] 에 보인다 — 살아 있는 WebView 면 빌려 오고, 아니면 제목·호스트. null 이면 숨긴다. */
    private fun showPeek(neighbor: Tab?) {
        releasePeek()
        swipeNeighbor = neighbor
        if (neighbor == null) {
            peek.visibility = View.GONE
            return
        }
        peekTitle.text = neighbor.label
        peekHost.text = Uri.parse(neighbor.url).host.orEmpty()
        val view = neighbor.webView
        // 뒤에서 재생을 잇는 탭은 빌려 오지 않는다 — 떼었다 붙이면 크로미움이 숨김을 알려 재생이 멈춘다(제목·호스트를 보인다).
        if (view != null && neighbor.url.isNotEmpty() && !neighbor.crashed && neighbor != bgTab) {
            detach(view)
            peek.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // 멈춰 둔(onPause) WebView 는 새로 붙여도 그리지 않는다 — 빌려 온 동안 깨운다(돌려줄 때 다시 멈춘다).
            view.onResume()
        }
        peek.translationX = (stage.width + Ui.dp(this, SWIPE_GAP_DP)).toFloat() * swipeDirection
        peek.visibility = View.VISIBLE
    }

    /** 빌려 둔 WebView 를 [peek] 에서 뗀다(그 탭은 화면에 없는 탭으로 돌아간다 — 다시 멈춘다). */
    private fun releasePeek() {
        for (i in peek.childCount - 1 downTo 0) {
            val child = peek.getChildAt(i)
            if (child is WebView) {
                child.onPause()
                detach(child)
            }
        }
    }

    /**
     * 뷰를 화면에서 뗀다. **쥐고 있던 초점을 먼저 거둔다.** 초점을 쥔 뷰를 떼면 안드로이드가 초점을 처음 만나는 '누르면 초점을 받는'
     * 뷰로 넘기는데(`ViewGroup.removeViewInternal` 의 `rootViewRequestFocus` — 터치 모드에서도 넘긴다), 이 화면에서 그것이 주소창이다.
     * 그러면 쪽을 누르거나 길게 눌러 WebView 가 초점을 쥔 뒤 탭을 바꾸거나 닫을 때마다 주소창이 고치는 상태가 되어 — 글이 모두
     * 골라지고 제안 목록이 쪽을 덮는다 — 새 탭의 주소도 그리지 못했다(`showUrl` 은 고치는 중이면 손대지 않는다). 사용자가 짚었다:
     * 길게 눌러 새 탭에서 연 뒤 막대를 밀어 그 탭으로 넘기면. 거두기(`clearFocus`)는 터치 모드에서 초점을 어디에도 넘기지 않는다
     * (API 28 부터 — `View.clearFocus` 의 `sAlwaysAssignFocus`). 쪽을 떼는 곳은 모두 이것을 지난다.
     */
    private fun detach(view: View) {
        if (view.hasFocus()) view.clearFocus()
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /** 지금 탭의 쪽(WebView)을 둘 자리 — 시작 화면·덮개 밑, 뒤에서 재생을 잇는(보이지 않는) 쪽 위. */
    private fun pageIndex(): Int = (0 until content.childCount).count { (content.getChildAt(it) as? PageWebView)?.backstaged == true }

    // ── 전체 화면 ─────────────────────────────────────────────────────────────────────────────────────────────────
    //
    // 상태 표시줄과 막대를 **함께** 보이고 숨긴다 — 상태 표시줄은 우리가 `show`/`hide(statusBars())` 로 다룬다. 시스템이 가장자리 밀기에 잠깐 보이는
    // 막대(transient)는 앱에 인셋으로 알려지지 않으므로(AOSP 12 InsetsPolicy — 보이지 않는 것으로 고쳐 보낸다) 그것을 따라 막대를 보일 수 없다.
    // 그래서 밀기는 우리가 알아보고([BrowserFrame]) 우리가 상태 표시줄을 보인다 — 시스템이 그 막대를 잠깐 보이던 중이면 앱이 보이기를 청하는 순간
    // 그 '잠깐' 이 풀리고(같은 소스의 checkAbortTransient) 그대로 남는다. 행동을 BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 로 두는 까닭: 기본 행동
    // (BEHAVIOR_DEFAULT)이면 시스템이 그 밀기에 상태 표시줄을 스스로 보이고 손가락을 상태 표시줄로 넘겨 알림 창이 따라 내려온다(AOSP 12
    // DisplayPolicy.requestTransientBars 의 transferTouch).

    /** 뿌리가 묻고 알리는 것 — 가장자리 밀기와 손가락이 닿고 떨어짐. */
    private val frameListener = object : BrowserFrame.Listener {
        override fun edgeEnabled(): Boolean = fullscreenMode && !barTimer.shown && customView == null

        override fun edgeHeight(): Float = edgePx.toFloat()

        override fun onEdgeSwipe() {
            showBar(animate = true, why = "edge")
            // 막대를 부른 손가락은 손을 뗄 때까지 막대를 만지는 것으로 센다 — 떼는 때부터 숨기까지를 센다.
            touchChrome(true)
        }

        override fun onTouchStart(x: Float, y: Float) = onScreenTouched(x, y)

        override fun onTouchEnd() {
            if (chromeTouched) touchChrome(false)
        }
    }

    /**
     * 화면에 손가락이 닿았다(페이지로 가기 전). 전체 화면에서 막대가 보이면 — 막대(와 그 아래로 내려온 줄들) 위면 숨기까지를 멈추고, 그 밖이면
     * (페이지·아래 알림 줄…) 곧바로 숨긴다(붙잡힌 때 — 주소창을 고치는 중 등 — 가 아니면). 손가락은 그대로 제 갈 곳으로 간다(누른 고리는 열린다).
     * 숨으며 올라가는 중인 막대를 누르면 다시 내린다.
     */
    private fun onScreenTouched(x: Float, y: Float) {
        if (!fullscreenMode || customView != null) return
        if (!barTimer.shown) {
            if (bar.visibility == View.VISIBLE && hits(bar, x, y)) {
                showBar(animate = true, why = "catch")
                touchChrome(true)
            }
            return
        }
        if (hits(chrome, x, y) || (strips.height > 0 && hits(strips, x, y))) {
            touchChrome(true)
            return
        }
        barTimer.hold(barHeld(), SystemClock.uptimeMillis())
        if (barTimer.pageTouched()) hideBar(animate = true, why = "page")
    }

    private fun touchChrome(down: Boolean) {
        chromeTouched = down
        barTimer.touchBar(down, SystemClock.uptimeMillis())
        scheduleAutoHide()
    }

    /** 뿌리의 [x]·[y] 가 [view] 위인가 — 옮겨 그린 거리(translation)까지 셈한다. */
    private fun hits(view: View, x: Float, y: Float): Boolean {
        var left = 0f
        var top = 0f
        var v: View = view
        while (v !== root) {
            left += v.left + v.translationX
            top += v.top + v.translationY
            v = v.parent as? View ?: return false
        }
        return x >= left && x < left + view.width && y >= top && y < top + view.height
    }

    /**
     * '항상 확대 허용' 을 살아 있는 탭의 WebView 에 맞춘다 — 화면에 돌아올 때(설정 화면에서 바꿨을 수 있다). 켜면 이미 연 페이지에도 한 번 돌려
     * 곧바로 듣게 하고(쪽의 세계에서 — 다음 문서부터는 보이지 않는 세계의 문서 시작 스크립트가 맡는다), 끄면 다음에 여는 페이지부터다. 접힌 탭은
     * 다시 만들 때 그때의 설정으로 건다([createWebView]).
     */
    private fun applyForceZoom() {
        val on = app.settings.forceZoom
        tabs.forEach { t ->
            val view = t.webView ?: return@forEach
            if (WebViews.applyForceZoom(view, on) && t.url.isNotEmpty()) view.evaluateJavascript(PageScripts.FORCE_ZOOM, null)
        }
    }

    /**
     * 전체 화면을 지금의 설정·TalkBack·분할 화면에 맞춘다 — 화면에 돌아올 때(onResume — 설정 화면에서 바꿨을 수 있다), TalkBack·분할 화면이 바뀔
     * 때, 밤 모드로 화면을 다시 지었을 때([rebuilt]). 돌아오면 막대는 숨긴 상태로 — 있어야 하는 때([barNeeded] — 주소창을 고치던 중·시작 화면)가
     * 아니면. 다시 지었을 때는 보이던 그대로.
     */
    private fun applyFullscreen(rebuilt: Boolean = false) {
        val want = FullscreenLayout.effective(app.settings.fullscreen, touchExploration, isInMultiWindowMode)
        if (want != fullscreenMode || rebuilt) {
            if (want != fullscreenMode) trace("fullscreen ${if (want) "on" else "off"}")
            fullscreenMode = want
            root.overlayChrome = want
            if (want) {
                // 가장자리 밀기에 시스템이 상태 표시줄을 '잠깐' 만 보이게(위 절의 머리말) — 알림 창이 따라 내려오지 않는다.
                window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                // 노치 자리까지 페이지를 그린다 — 기본 모드면 상태 표시줄을 숨겨도 그 띠가 검게 남는다(가로 화면의 옆 노치는 뿌리의 여백이 피한다).
                if (customView == null) setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
            } else {
                main.removeCallbacks(autoHide)
                chromeTouched = false
                if (customView == null) {
                    setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT)
                    window.insetsController?.show(WindowInsets.Type.statusBars())
                }
            }
            root.requestApplyInsets()
        }
        // 전체 화면 영상 중이면 영상이 끝날 때(hideCustomView) 맞춘다.
        if (customView != null) return
        when {
            !fullscreenMode -> placeChrome()
            barNeeded() || (rebuilt && barTimer.shown) -> {
                showBar(animate = false, why = if (rebuilt) "rebuilt" else "needed")
                placeChrome()
            }
            else -> hideBar(animate = false, why = "resume")
        }
    }

    private fun setCutoutMode(mode: Int) {
        val attrs = window.attributes
        if (attrs.layoutInDisplayCutoutMode == mode) return
        attrs.layoutInDisplayCutoutMode = mode
        window.attributes = attrs
    }

    /** 막대를 보인다(상태 표시줄과 함께) — 전체 화면에서만. 이미 보이면 숨기까지를 처음부터 센다. [why] 는 디버그 기록. */
    private fun showBar(animate: Boolean, why: String) {
        if (!fullscreenMode) return
        val was = barTimer.shown
        barTimer.autoHideMs = autoHideMs()
        barTimer.show(SystemClock.uptimeMillis())
        if (customView == null) window.insetsController?.show(WindowInsets.Type.statusBars())
        if (!was) {
            moveChrome(animate)
            trace("bar shown $why")
        }
        scheduleAutoHide()
    }

    /** 막대를 숨긴다(상태 표시줄과 함께) — 전체 화면에서만. */
    private fun hideBar(animate: Boolean, why: String) {
        if (!fullscreenMode) return
        val was = barTimer.shown
        barTimer.hide()
        chromeTouched = false
        main.removeCallbacks(autoHide)
        if (customView == null) window.insetsController?.hide(WindowInsets.Type.statusBars())
        moveChrome(animate && was)
        if (was) trace("bar hidden $why")
    }

    /**
     * 막대·줄들을 지금 상태의 자리로 — 보이면 막대는 제자리, 줄들은 막대 바로 아래로 내려오고, 숨으면 막대만큼 위로(진행 막대가 화면 맨 위에
     * 걸린다). [animate] 면 상태 표시줄과 같은 빠르기로 민다(시스템이 상태 표시줄을 미는 시간과 곡선 — [REVEAL_MS]).
     */
    private fun moveChrome(animate: Boolean) {
        if (!::chrome.isInitialized) return
        val shown = !fullscreenMode || barTimer.shown
        val chromeY = if (shown) 0f else -bar.height.toFloat()
        val stripsY = FullscreenLayout.stripsShift(fullscreenMode, shown, bar.height, strips.paddingTop).toFloat()
        chrome.animate().cancel()
        strips.animate().cancel()
        if (shown) bar.visibility = View.VISIBLE
        postCovered()
        if (!animate || bar.height == 0) {
            chrome.translationY = chromeY
            strips.translationY = stripsY
            if (!shown) bar.visibility = View.INVISIBLE
            return
        }
        val ease = AnimationUtils.loadInterpolator(this, android.R.interpolator.fast_out_slow_in)
        val ms = if (shown) REVEAL_MS else CONCEAL_MS
        val moving = bar
        chrome.animate().translationY(chromeY).setDuration(ms).setInterpolator(ease).withEndAction {
            // 다 올라간 막대는 보이지 않게 둔다 — 화면 밖에서 초점·화면 낭독기의 자리가 되지 않게(자리는 그대로 — 다시 내릴 거리다).
            if (fullscreenMode && !barTimer.shown && moving === bar) moving.visibility = View.INVISIBLE
        }.start()
        strips.animate().translationY(stripsY).setDuration(ms).setInterpolator(ease).start()
    }

    /** 막대·줄들의 높이가 바뀌었다(돌리기·인셋·줄이 뜨고 짐) — 지금 상태의 자리로 곧바로. */
    private fun placeChrome() = moveChrome(animate = false)

    /** 가려진 높이를 다음 차례에 맞춘다 — 배치 중에 불려도(높이가 바뀐 알림) 다른 뷰의 여백을 그 자리에서 바꾸지 않게. */
    private fun postCovered() {
        main.removeCallbacks(coveredTask)
        main.post(coveredTask)
    }

    /**
     * 쪽의 자리 맨 위에서 가려진 만큼([FullscreenLayout.coveredTop] — 나타난 막대·노치) 그 위에 덮는 화면의 뷰를 내린다: 시작 화면·주소창 제안·
     * '열지 못함' 덮개. 페이지(WebView)는 내리지 않는다.
     */
    private fun renderCovered() {
        if (isDestroyed || !::chrome.isInitialized) return
        val top = FullscreenLayout.coveredTop(fullscreenMode, barTimer.shown, bar.height, strips.paddingTop, strips.height > 0, cutoutPx)
        if (top == coveredTop) return
        coveredTop = top
        startPage.view.setPadding(0, top, 0, 0)
        suggestions.view.setPadding(0, top, 0, 0)
        errorCover.setTopInset(top)
    }

    /** 막대가 있어야 하는 때 — 주소창을 고치는 중·찾기 줄·새 탭의 시작 화면(채울 페이지가 없다 — 주소를 칠 곳이다). 돌아오거나 처음 그릴 때 이것이면 보인다. */
    private fun barNeeded(): Boolean = address.hasFocus() || findBar.visibility == View.VISIBLE || current?.url.isNullOrEmpty()

    /**
     * 막대를 숨기지 않는 때 — [barNeeded] 에 더해 창이 초점을 잃은 동안(메뉴·탭 목록·대화상자·길게 누르기 메뉴·알림 창 — 다 떠 있는 창이 초점을
     * 가져간다), 막대를 밀어 탭을 넘기는 중과 그 마무리.
     */
    private fun barHeld(): Boolean = barNeeded() || !hasWindowFocus() || gestureAnimating || swipeDirection != 0

    /** 숨길 때를 다시 잡는다 — 붙잡는 까닭이 바뀌었을 수 있는 자리마다 부른다(빠뜨려도 붙잡힌 동안은 [AUTO_HIDE_MS] 마다 다시 본다). */
    private fun scheduleAutoHide() {
        main.removeCallbacks(autoHide)
        if (!fullscreenMode || customView != null || !::chrome.isInitialized) return
        val now = SystemClock.uptimeMillis()
        barTimer.hold(barHeld(), now)
        val at = barTimer.nextCheck(now) ?: return
        main.postAtTime(autoHide, at)
    }

    private fun checkAutoHide() {
        if (isDestroyed || !fullscreenMode || customView != null) return
        val now = SystemClock.uptimeMillis()
        barTimer.hold(barHeld(), now)
        if (barTimer.due(now)) hideBar(animate = true, why = "timeout") else scheduleAutoHide()
    }

    /** 숨기까지의 시간 — 접근성의 '조작할 시간' 설정이 더 길게 권하면 그만큼([MessageBar] 와 같다). */
    private fun autoHideMs(): Long = getSystemService(AccessibilityManager::class.java)
        ?.getRecommendedTimeoutMillis(AUTO_HIDE_MS.toInt(), AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_TEXT)
        ?.toLong() ?: AUTO_HIDE_MS

    /** 쪽에서 찾기 — 막대 아래의 한 줄(찾을 글·몇째/몇 개·이전·다음·닫기). 찾을 글을 바꿀 때마다 다시 찾는다. */
    private fun buildFindBar() {
        findBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 2), 0)
        }
        findInput = EditText(this).apply {
            isSingleLine = true
            textSize = 15f
            hint = getString(R.string.find_in_page)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val q = s?.toString().orEmpty()
                    val view = current?.webView ?: return
                    if (q.isEmpty()) {
                        view.clearMatches()
                        findCount.text = ""
                    } else {
                        view.findAllAsync(q)
                    }
                }
            })
            setOnEditorActionListener { _, _, _ ->
                current?.webView?.findNext(true)
                true
            }
        }
        findCount = TextView(this).apply {
            textSize = 13f
            alpha = 0.75f
            setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 4), 0)
        }
        findBar.addView(findInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        findBar.addView(findCount)
        findBar.addView(Ui.iconButton(this, R.drawable.ic_up, getString(R.string.find_prev)) { current?.webView?.findNext(false) })
        findBar.addView(Ui.iconButton(this, R.drawable.ic_down, getString(R.string.find_next)) { current?.webView?.findNext(true) })
        findBar.addView(Ui.iconButton(this, R.drawable.ic_close, getString(R.string.find_close)) { hideFind() })
    }

    /** 재생 중인 것 받기 — 막대 아래의 한 줄(받은 양·저장·취소). 지금 탭에 받는 것이 있을 때만 보인다. */
    private fun buildCaptureBar() {
        captureBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 4), 0)
        }
        captureText = TextView(this).apply { textSize = 13f }
        fun button(text: Int, click: () -> Unit) = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            this.text = getString(text)
            minWidth = 0
            minimumWidth = 0
            setPadding(Ui.dp(context, 10), paddingTop, Ui.dp(context, 10), paddingBottom)
            setOnClickListener { click() }
        }
        captureSave = button(R.string.capture_save) { saveCapture() }
        captureCancel = button(R.string.capture_cancel) { cancelCapture() }
        captureGo = button(R.string.capture_go_tab) { tabs.firstOrNull { it != current && it.bridge?.busy == true }?.let { select(it) } }
        captureBar.addView(captureText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        captureBar.addView(captureSave)
        captureBar.addView(captureCancel)
        captureBar.addView(captureGo)
    }

    /**
     * 막대 아래 줄 — 지금 탭이 받는 중이면 받은 것(녹화는 실제로 담긴 시간, 가로채기는 저장하면 담길 크기)과 저장·취소. 지금 탭은 아니지만 다른
     * 탭이 받는 중이면 그것을 알리고 '그 탭으로'(다른 탭으로 넘어가면 녹화 중임을 잊기 쉬웠다).
     */
    private fun renderCaptureBar() {
        val bridge = current?.bridge
        val capture = bridge?.capture
        val recording = bridge?.recording
        val other = if (capture == null && recording == null) tabs.firstOrNull { it != current && it.bridge?.busy == true } else null
        fun show(v: View, on: Boolean) {
            val want = if (on) View.VISIBLE else View.GONE
            if (v.visibility != want) v.visibility = want
        }
        fun text(v: TextView, t: String) {
            if (v.text.toString() != t) v.text = t
        }
        when {
            capture != null -> {
                show(captureBar, true)
                text(captureText, CaptureText.status(this, null, capture))
                show(captureSave, !capture.drm && capture.savables().isNotEmpty())
                text(captureSave, getString(R.string.capture_save))
                show(captureCancel, true)
                show(captureGo, false)
            }
            recording != null -> {
                show(captureBar, true)
                text(captureText, CaptureText.status(this, recording, null))
                show(captureSave, !recording.stopping)
                text(captureSave, getString(R.string.capture_stop_save))
                show(captureCancel, true)
                show(captureGo, false)
            }
            other != null -> {
                show(captureBar, true)
                val busy = other.bridge
                text(captureText, getString(if (busy?.recording != null) R.string.capture_other_recording else R.string.capture_other_intercepting, other.label))
                show(captureSave, false)
                show(captureCancel, false)
                show(captureGo, true)
            }
            else -> show(captureBar, false)
        }
    }

    /** 메뉴·찾은 미디어의 '재생 중인 것 받기' — 가로채기·녹화·소리만 녹화를 고른다. */
    private fun chooseCapture() {
        val tab = current ?: return
        val bridge = tab.bridge
        if (bridge == null || tab.webView == null) {
            Toast.makeText(this, R.string.capture_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        if (bridge.capture != null || bridge.recording != null) {
            Toast.makeText(this, R.string.capture_busy, Toast.LENGTH_SHORT).show()
            return
        }
        val modes = arrayOf(getString(R.string.capture_mode_intercept), getString(R.string.capture_mode_record), getString(R.string.capture_mode_record_audio))
        AlertDialog.Builder(this)
            .setTitle(R.string.capture_menu)
            .setItems(modes) { _, which ->
                when (which) {
                    0 -> startIntercept(tab)
                    else -> startRecording(tab, audioOnly = which == 2)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 가로채기 — 갈고리를 걸고 쪽을 다시 연다(재생기가 처음부터 다시 받는 것을 담는다). **이 사이트에 묶는다** — 다른 사이트로 가면 멈추고 저장할지
     * 묻는다.
     */
    private fun startIntercept(tab: Tab) {
        val view = tab.webView ?: return
        val bridge = tab.bridge ?: return
        bridge.armCapture(view, PageBridge.siteOf(Request.hostOf(tab.committedUrl))) ?: return
        askNotificationPermission()
        trace("capture start")
        Toast.makeText(this, R.string.capture_intercept_note, Toast.LENGTH_LONG).show()
        reloadTab(tab)
    }

    private fun startRecording(tab: Tab, audioOnly: Boolean) {
        val bridge = tab.bridge ?: return
        askNotificationPermission()
        bridge.startRecording(audioOnly) { error ->
            if (error != null) {
                trace("recording refused $error")
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 막대의 '저장' — 녹화는 멈추고 저장, 가로채기는 영상이 여럿이면 고르게 한 뒤 '영상과 소리'·'소리만'. */
    private fun saveCapture() {
        val tab = current ?: return
        val bridge = tab.bridge ?: return
        if (bridge.recording != null) {
            bridge.stopRecording(save = true)
            return
        }
        val capture = bridge.capture ?: return
        pickClip(capture) { clip ->
            AlertDialog.Builder(this)
                .setTitle(R.string.capture_save_what)
                .setItems(arrayOf(getString(R.string.capture_save_both), getString(R.string.capture_save_audio))) { _, which ->
                    val session = bridge.endCapture() ?: return@setItems
                    saveCaptureSession(tab, session, clip.clip.ms, audioOnly = which == 1)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /**
     * 저장할 영상 — 가로챈 영상(MediaSource)이 여럿이면 고르게 한다(사이트 안에서 다음 영상으로 갔다 — 앞 판은 가장 큰 하나만 저장했다).
     * 이름은 그 영상이 처음 잡힌 때의 페이지 제목.
     */
    private fun pickClip(capture: CaptureSession, then: (CaptureSession.Savable) -> Unit) {
        val clips = capture.savables()
        when (clips.size) {
            0 -> return
            1 -> then(clips[0])
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.capture_save_which)
                .setItems(clips.map { clipLabel(capture, it) }.toTypedArray()) { _, which -> then(clips[which]) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun clipLabel(capture: CaptureSession, clip: CaptureSession.Savable): String =
        getString(R.string.capture_clip_row, clip.clip.title.ifBlank { capture.site }, Formatter.formatShortFileSize(this, clip.total))

    /** 가로챈 것을 저장으로 넘긴다 — [ms] 는 고른 영상(없으면 담길 것이 가장 많은 것), 이름은 그 영상이 처음 잡힌 때의 제목(없으면 사이트). */
    private fun saveCaptureSession(tab: Tab, session: CaptureSession, ms: String?, audioOnly: Boolean) {
        session.close()
        val chosen = ms ?: session.savables().firstOrNull()?.clip?.ms
        val title = chosen?.let { session.clips[it]?.title }.orEmpty()
        saveRemux(title.ifBlank { session.site.ifBlank { captureName(tab) } }, session.dir) { session.job(chosen, audioOnly) }
        trace("capture saved audioOnly=$audioOnly")
        if (!isDestroyed) Toast.makeText(this, R.string.capture_saving, Toast.LENGTH_LONG).show()
    }

    /** 녹화·가로채기를 저장으로 넘긴다 — 그 폴더는 '남은 것' 이 아니다(저장 일이 끝나면 지운다). */
    private fun saveRemux(name: String, dir: File, job: () -> DownloadCenter.RemuxJob) {
        CaptureRegistry.claim(dir)
        app.downloads.enqueueRemux(name, job)
    }

    /**
     * 가로채기를 건 사이트를 떠나 가로채기가 끝났다 — 담은 것을 저장할지 묻는다. 영상이 여럿이면 저장할 것을 고른다. 묻는 창을 그냥 닫으면 저장한다
     * (받은 것을 잃지 않는 쪽).
     */
    private fun askLeftSite(tab: Tab, session: CaptureSession) {
        val clips = session.savables()
        val builder = AlertDialog.Builder(this)
            .setNegativeButton(R.string.capture_discard) { _, _ ->
                session.discard()
                CaptureRegistry.release(session.dir)
                trace("capture left discarded")
            }
            .setOnCancelListener { saveCaptureSession(tab, session, null, audioOnly = false) }
        if (clips.size > 1) {
            builder.setTitle(R.string.capture_left_pick)
                .setItems(clips.map { clipLabel(session, it) }.toTypedArray()) { _, which ->
                    saveCaptureSession(tab, session, clips[which].clip.ms, audioOnly = false)
                }
        } else {
            builder.setTitle(R.string.capture_left_title)
                .setMessage(getString(R.string.capture_left_message, session.site, Formatter.formatShortFileSize(this, clips.sumOf { it.total })))
                .setPositiveButton(R.string.capture_save) { _, _ -> saveCaptureSession(tab, session, null, audioOnly = false) }
        }
        builder.show()
    }

    private fun cancelCapture() {
        val bridge = current?.bridge ?: return
        if (bridge.recording != null) {
            bridge.stopRecording(save = false)
        } else {
            bridge.endCapture()?.let {
                it.discard()
                CaptureRegistry.release(it.dir)
            }
            trace("capture cancelled")
        }
    }

    /** 받은 것의 이름 — 쪽의 제목(없으면 사이트 이름). */
    private fun captureName(tab: Tab): String = tab.title.ifBlank { Request.hostOf(tab.committedUrl).ifBlank { "video" } }

    /**
     * 탭을 닫기 직전 — 그 탭들의 녹화·가로채기를 어떻게 할지 정한 뒤 [close] 를 부른다. **닫는 자리마다 이것을 지난다**(뒤로 가기·탭 목록의 닫기·
     * 모두 닫기·쪽의 `window.close`). 앞 판은 묻지 않고 버렸다.
     *
     * 사용자가 닫는 것이면([ask]) '저장하고 닫기 / 버리고 닫기 / 취소' 를 묻는다. 쪽이 스스로 닫는 것(`window.close`)이면 묻지 않고 **받은 데까지
     * 저장한다** — 쪽이 바뀔 때와 같다. 쪽이 띄우게 한 창이 사용자의 녹화를 지우는 길이 되지 않게(버리는 쪽을 기본으로 하면 쪽 하나가 지울 수 있다).
     * 저장은 닫기가 한다(`closeTab` → [PageBridge.dispose] 가 받은 데까지 넘긴다) — 버리기만 여기서 한다.
     */
    private fun settleCaptures(closing: List<Tab>, ask: Boolean, close: () -> Unit) {
        val busy = closing.filter { it.bridge?.busy == true }
        if (busy.isEmpty()) {
            close()
            return
        }
        if (!ask) {
            trace("capture closed by page — saved")
            Toast.makeText(this, R.string.capture_saved_on_close, Toast.LENGTH_LONG).show()
            close()
            return
        }
        val message = if (busy.size == 1) getString(R.string.capture_close_message, captureMark(busy[0]).orEmpty())
        else getString(R.string.capture_close_message_many, busy.size)
        AlertDialog.Builder(this)
            .setTitle(R.string.capture_close_title)
            .setMessage(message)
            .setPositiveButton(R.string.capture_close_save) { _, _ ->
                trace("capture close save")
                close()
            }
            .setNeutralButton(R.string.capture_close_discard) { _, _ ->
                trace("capture close discard")
                busy.forEach { it.bridge?.discardAll() }
                close()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 탭 목록 줄·닫기 물음의 표시 — '● 녹화 중 · 1:23' / '● 가로채는 중 · 12 MB'. 받는 중이 아니면 null. */
    private fun captureMark(tab: Tab): String? {
        val bridge = tab.bridge ?: return null
        bridge.recording?.let { return getString(R.string.capture_tab_recording, CaptureText.clock(it.recordedMs())) }
        val capture = bridge.capture ?: return null
        val total = capture.savables().firstOrNull()?.total ?: 0L
        return getString(R.string.capture_tab_intercepting, Formatter.formatShortFileSize(this, total))
    }

    /**
     * 녹화·가로채기 중인 것을 알림([CaptureService])에 알린다 — 없던 것이 생기면 서비스를 세운다(받는 동안 시스템이 앱을 정리하지 않게).
     * 서비스는 다 끝나면 스스로 내려간다.
     */
    private fun publishCaptures() {
        val active = tabs.mapNotNull { t ->
            val b = t.bridge ?: return@mapNotNull null
            if (!b.busy) null else CaptureRegistry.Active(t.id, t.label, b.recording, b.capture)
        }
        val before = CaptureRegistry.snapshot().size
        CaptureRegistry.publish(active)
        if (active.isNotEmpty() && before == 0) CaptureService.start(this)
    }

    /** 앞 프로세스가 남긴 녹화·가로채기 — 시스템이 앱을 정리해 저장하지 못했다. 찾는 것은 일꾼에서(폴더를 읽는다), 묻는 것은 메인에서. */
    private fun offerLeftoverCaptures() {
        val root = PageBridge.captureRoot(this)
        Thread({
            val left = CaptureRecovery.find(root)
            if (left.isNotEmpty()) main.post { if (!isDestroyed) askLeftovers(left) }
        }, "web-capture-recovery").apply { isDaemon = true }.start()
    }

    private fun askLeftovers(left: List<CaptureRecovery.Leftover>) {
        trace("capture leftovers ${left.size}")
        AlertDialog.Builder(this)
            .setTitle(R.string.capture_leftover_title)
            .setMessage(getString(R.string.capture_leftover_message, left.size, Formatter.formatShortFileSize(this, left.sumOf { it.bytes })))
            .setPositiveButton(R.string.capture_save) { _, _ ->
                for (l in left) {
                    val name = l.title.ifBlank { getString(if (l.recording) R.string.capture_name_recording else R.string.capture_name_intercept) }
                    saveRemux(name, l.dir, l.job)
                }
                Toast.makeText(this, R.string.capture_saving, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.capture_discard) { _, _ ->
                Thread({ left.forEach { it.dir.deleteRecursively() } }, "web-capture-recovery").apply { isDaemon = true }.start()
            }
            .show()
    }

    private fun showFind() {
        picker.stop()
        // 찾는 동안은 막대가 보인다(찾기 줄은 막대 바로 아래에 붙는다).
        showBar(animate = true, why = "find")
        findBar.visibility = View.VISIBLE
        findInput.requestFocus()
        findInput.selectAll()
        getSystemService(InputMethodManager::class.java).showSoftInput(findInput, 0)
        if (findInput.text.isNotEmpty()) current?.webView?.findAllAsync(findInput.text.toString())
    }

    private fun hideFind() {
        if (findBar.visibility != View.VISIBLE) return
        current?.webView?.clearMatches()
        findCount.text = ""
        findBar.visibility = View.GONE
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(findInput.windowToken, 0)
        scheduleAutoHide()
    }

    /** 주소가 없는 탭이면 시작 화면을 보인다(WebView 위에). */
    private fun renderStart() {
        val show = current?.url.isNullOrEmpty()
        if (show) {
            startPage.view.visibility = View.VISIBLE
            startPage.view.bringToFront()
            startPage.refresh()
            trace("start shown")
        } else {
            startPage.view.visibility = View.GONE
        }
        // 시작 화면이 보이는 동안은 막대를 숨기지 않는다(채울 페이지가 없다 — 주소를 칠 곳이다). 사라지면 숨기까지를 센다.
        scheduleAutoHide()
    }

    /** 디버그 빌드에서만 — PC 의 시험 도구(tools/lab/run_browser.py)가 logcat 의 `WebUI` 로 화면의 일을 읽는다. 릴리스에서는 R8 이 지운다. */
    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.i("WebUI", message)
    }

    /**
     * 스위치(켜짐·꺼짐 — 화면 낭독기에는 길의 상태까지 상태 설명으로), 옆의 상태 글(누르면 '연결 상태'), 알림 띠. 오류 직후에 온 길의 소식은
     * 그 오류의 까닭이라 덮개도 다시 고른다.
     */
    private fun render(state: NetworkPath.State) {
        trace("state ${state.phase} ${state.exit?.country ?: "-"}")
        val night = Ui.isNight(this)
        val look = ProxyStatus.of(state.phase, state.exit?.country)
        val color = when (look.tone) {
            ProxyStatus.Tone.GOOD -> if (night) GREEN_NIGHT else GREEN
            ProxyStatus.Tone.WARN -> if (night) AMBER_NIGHT else AMBER
            ProxyStatus.Tone.BAD -> if (night) RED_NIGHT else RED
        }
        val enabled = network.settings.enabled
        rendering = true
        proxySwitch.isChecked = enabled
        rendering = false
        // 상태 설명을 두면 낭독기는 '켜짐/꺼짐' 대신 이것을 읽는다 — 켜짐·꺼짐을 앞에 함께 적는다.
        val spoken = getString(look.spokenRes, ProxyStatus.countryName(state.exit?.country))
        proxySwitch.stateDescription = getString(if (enabled) R.string.proxy_state_on else R.string.proxy_state_off, spoken)
        if (look.hidden) {
            proxyStatus.visibility = View.GONE
        } else {
            proxyStatus.text = if (look.labelRes != 0) getString(look.labelRes) else look.labelText
            proxyStatus.setTextColor(color)
            proxyStatus.contentDescription = getString(R.string.proxy_status_label, spoken)
            proxyStatus.visibility = View.VISIBLE
        }
        val message = when (state.phase) {
            Phase.NOT_CONFIGURED -> R.string.banner_not_configured
            Phase.UNREACHABLE -> R.string.banner_unreachable
            Phase.AUTH_REJECTED -> R.string.banner_auth
            Phase.LOOKS_DIRECT -> R.string.banner_looks_direct
            Phase.UNSUPPORTED -> R.string.banner_unsupported
            else -> null
        }
        if (message == null) {
            banner.visibility = View.GONE
            // 다음에 띠가 뜰 때 글이 바뀐 것으로 읽히게(알림 영역은 글이 바뀔 때 읽는다).
            bannerText.text = ""
        } else {
            // 상태 글과 같은 빛을 옅게 — 막대와 갈리게.
            banner.setBackgroundColor(Color.argb(0x2A, Color.red(color), Color.green(color), Color.blue(color)))
            val text = getString(message)
            banner.visibility = View.VISIBLE
            if (bannerText.text.toString() != text) bannerText.text = text
        }
        if (PageErrors.isPathFailure(state.phase)) {
            val now = SystemClock.elapsedRealtime()
            tabs.forEach { t -> t.error?.takeIf { it.proxyOn && now - it.at < PATH_NEWS_MS }?.phase = state.phase }
        }
        renderError()
    }

    /**
     * 스위치 — 프록시를 켜거나 끈다. 묻지 않는다(사용자 지정). 주소·계정·예외는 그대로 두고 켜기만 바꾼다. 켰는데 프록시를 정하지 않았으면
     * 설정을 연다(정하기 전에는 아무 데도 나가지 않는다).
     */
    private fun setProxyEnabled(on: Boolean) {
        val s = network.settings
        if (s.enabled == on) return
        trace("proxy ${if (on) "on" else "off"}")
        network.update(s.copy(enabled = on))
        // 켤 때는 옛 길이 '직접' 이다 — 열린 쪽의 연결을 그 자리에서 끊는다(reloadAllForNewPath). 길을 바꾼 **뒤에** 부른다 — 다시 여는 일이 새 길을 기다리게.
        reloadAllForNewPath(cutNow = on)
        if (on && !s.isComplete) openSettings()
    }

    /**
     * 길이 바뀌었다 — 옛 길에 붙어 있던 연결(웹소켓·재생기의 조각 받기·XHR 등)을 끊고 새 길로 다시 연다. **[NetworkPath.update] 뒤에 부른다**
     * (그래야 다시 여는 일이 새 길을 기다린다).
     *
     * - [cutNow](끈 상태에서 켰다): 옛 길이 '직접' 이라, 새 길이 걸리고 데워질 때까지(최대 3초) 열린 쪽이 스스로 내는 요청과 이어져 있던 연결이
     *   그대로 직접 나간다(중계를 다시 세우는 다른 바꿈은 옛 중계를 닫아 이미 끊겼다 — fail-closed). 그래서 **그 자리에서 탭을 접는다** — WebView 를
     *   없애면 그 쪽의 연결이 모두 끊기고, 뒤로·앞으로 기록은 메모리에 남는다. 지금 탭은 곧바로 새 WebView 를 세워 두고 길이 서고 데워진 뒤
     *   되살리며(서비스 워커의 요청은 데운 뒤에야 407 을 넘는다), 뒤의 탭은 고를 때 되살린다. 녹화·가로채기 중인 탭은 접으면 받기가 끊기므로
     *   접지 않고, 새 길이 WebView 에 걸리는 즉시([NetworkPath.whenApplied] — 데우기를 기다리지 않는다) 다시 불러 끊는다.
     * - 그 밖: 새 길이 서고 데워진 뒤 열린 탭을 다시 불러 잇는다.
     *
     * 다시 불러 녹화가 끝나면(쪽이 바뀌면 받은 데까지 저장한다) 그 까닭을 알린다.
     */
    private fun reloadAllForNewPath(cutNow: Boolean = false) {
        seenRevision = network.revision
        picker.stop()
        val recording = tabs.count { it.bridge?.recording != null }
        if (cutNow) {
            // 뒤에서 재생을 잇던 탭도 접힌다(옛 길의 연결을 끊는다) — 회를 먼저 닫는다.
            endBackgroundPlay(pauseMedia = false)
            val busy = tabs.filter { it.bridge?.let { b -> b.capture != null || b.recording != null } == true }
            val shown = current
            tabs.filter { it.webView != null && it.url.isNotEmpty() && it !in busy }.forEach { freeze(it) }
            trace("cut for path ${network.revision} busy=${busy.size}")
            // 지금 탭은 새 WebView 를 세워 둔다 — 되살리기는 길이 서고 데워진 뒤에 돈다(select → thaw → whenReady).
            if (shown != null && shown.webView == null && shown in tabs) select(shown)
            network.whenApplied {
                if (isDestroyed) return@whenApplied
                trace("reloaded busy for path ${network.revision}")
                busy.forEach { t -> t.webView?.takeIf { t in tabs }?.reload() }
            }
        } else {
            whenReady {
                trace("reloaded for path ${network.revision}")
                tabs.forEach { t -> t.webView?.takeIf { t.url.isNotEmpty() }?.reload() }
            }
        }
        if (recording > 0) Toast.makeText(this, R.string.recording_ended_by_path, Toast.LENGTH_LONG).show()
    }

    /** 메뉴의 '연결 상태' — 길의 상태·출구·DNS 미리 찾기 안내. */
    private fun showStatus() {
        val s = network.state
        val settings = network.settings
        val lines = buildList {
            add(
                getString(
                    when (s.phase) {
                        Phase.OFF -> R.string.status_off
                        Phase.NOT_CONFIGURED -> R.string.status_not_configured
                        Phase.CHECKING -> R.string.status_checking
                        Phase.OK -> R.string.status_ok
                        Phase.LOOKS_DIRECT -> R.string.status_looks_direct
                        Phase.UNREACHABLE -> R.string.status_unreachable
                        Phase.AUTH_REJECTED -> R.string.status_auth
                        Phase.CHECK_FAILED -> R.string.status_check_failed
                        Phase.UNSUPPORTED -> R.string.status_unsupported
                    },
                ),
            )
            if (settings.enabled && settings.isComplete) add(getString(R.string.status_proxy, "${settings.host}:${settings.port}"))
            s.exit?.let { add(getString(R.string.status_exit, it.ip, it.country ?: "?")) }
            if (settings.enabled) add(getString(R.string.status_dns_note))
            // 직접 연결로 넘어가지 않는다는 약속의 예외 — 토렌트는 스위치와 상관없이 늘 바로 나간다(사용자 결정). 켜고 끄는 상태와 상관없이 늘 보인다.
            add(getString(R.string.status_torrent_note))
            // 그리고 테더링이 중계하는 다른 기기의 트래픽(:tether 프로세스 — 이 화면은 그 상태를 모른다. 늘 보인다).
            add(getString(R.string.status_tether_note))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.status_title)
            .setMessage(lines.joinToString("\n\n"))
            .setNeutralButton(R.string.settings) { _, _ -> openSettings() }
            .setNegativeButton(R.string.close, null)
        if (settings.enabled && settings.isComplete) builder.setPositiveButton(R.string.check_again) { _, _ -> network.checkExit() }
        builder.show()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showMenu(anchor: View) {
        val tab = current
        val ads = app.adblock
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, M_BACK, 0, R.string.back).isEnabled = tab?.webView?.canGoBack() == true
        menu.menu.add(0, M_FORWARD, 1, R.string.forward).isEnabled = tab?.webView?.canGoForward() == true
        menu.menu.add(0, M_NEW_TAB, 2, R.string.new_tab)
        menu.menu.add(0, M_TABS, 3, getString(R.string.tabs_count, tabs.size))
        menu.menu.add(0, M_RECENT_TABS, 3, R.string.recent_tabs).isEnabled = !closed.isEmpty
        val pageUrl = tab?.committedUrl.orEmpty()
        val web = pageUrl.startsWith("http://") || pageUrl.startsWith("https://")
        val marked = web && app.library.isBookmarked(pageUrl)
        menu.menu.add(0, M_BOOKMARK, 4, if (marked) R.string.bookmark_remove else R.string.bookmark_add).isEnabled = web
        menu.menu.add(0, M_BOOKMARKS, 4, R.string.bookmarks)
        menu.menu.add(0, M_HISTORY, 4, R.string.history)
        menu.menu.add(0, M_FIND, 4, R.string.find_in_page).isEnabled = tab?.webView != null && !tab.url.isEmpty()
        menu.menu.add(0, M_DESKTOP, 4, R.string.desktop_site).apply {
            isCheckable = true
            isChecked = tab?.desktop == true
            isEnabled = tab != null && !tab.url.isEmpty()
        }
        val pageHost = Request.hostOf(pageUrl)
        val adsOffHere = ads.isAllowed(pageHost)
        if (ads.enabled && web) {
            menu.menu.add(0, M_ADBLOCK, 4, if (adsOffHere) getString(R.string.adblock_site_on) else getString(R.string.adblock_site_off, tab?.adBlocked?.get() ?: 0))
        }
        // 요소 숨기기 — 광고 차단을 꺼도 쓴다(사용자가 직접 고른 것이라). 되돌리기는 그 사이트에 숨긴 것이 있을 때만.
        menu.menu.add(0, M_HIDE_ELEMENT, 4, R.string.hide_element).isEnabled = web && tab?.webView != null && !errorCover.isShowing
        val hiddenHere = if (web) app.hidden.countFor(pageHost) else 0
        if (hiddenHere > 0) menu.menu.add(0, M_UNHIDE_SITE, 4, getString(R.string.hide_restore_site, hiddenHere))
        // 팝업을 허용한 사이트에서만 — 풀 길(설정에도 있다).
        if (web && app.popups.isAllowed(pageHost)) menu.menu.add(0, M_POPUPS, 4, R.string.popup_block_site)
        menu.menu.add(0, M_CAPTURE, 4, R.string.capture_menu).isEnabled = web && tab?.webView != null
        // 갤러리 받기 — 지금 페이지(커밋된 주소)를 알아볼 때만 한 줄(pixiv 작품·작가, hitomi 갤러리).
        val gallery = if (web) recognizeGallery(pageUrl) else null
        if (gallery != null) menu.menu.add(0, M_GALLERY, 4, GalleryRules.menuRes(gallery))
        menu.menu.add(0, M_DOWNLOADS, 4, R.string.downloads)
        menu.menu.add(0, M_SHARE, 5, R.string.share).isEnabled = !tab?.url.isNullOrEmpty()
        menu.menu.add(0, M_OUTSIDE, 6, R.string.open_outside).isEnabled = tab?.url?.startsWith("http") == true
        menu.menu.add(0, M_STATUS, 7, R.string.status_title)
        // 테더링(usb-tether 를 합침 — 그 화면은 따로 된 프로세스 :tether 에서 뜬다).
        menu.menu.add(0, M_TETHER, 8, R.string.tether_menu)
        menu.menu.add(0, M_SETTINGS, 8, R.string.settings)
        menu.setOnMenuItemClickListener { item ->
            // 메뉴에서 무엇을 고르든 하던 요소 고르기는 끝낸다('요소 숨기기' 는 새로 시작한다).
            picker.stop()
            when (item.itemId) {
                M_BACK -> tab?.webView?.goBack()
                M_FORWARD -> tab?.webView?.goForward()
                M_NEW_TAB -> openNewTabForTyping()
                M_TABS -> showTabs()
                M_RECENT_TABS -> showRecentTabs()
                M_DESKTOP -> tab?.let { setDesktop(it, !it.desktop) }
                M_POPUPS -> {
                    app.popups.setAllowed(pageHost, false)
                    trace("popups blocked again $pageHost")
                    Toast.makeText(this, getString(R.string.popup_blocked_again, app.popups.siteOf(pageHost)), Toast.LENGTH_SHORT).show()
                }
                M_BOOKMARK -> tab?.let { toggleBookmark(it, pageUrl, marked) }
                M_BOOKMARKS -> openLibrary(LibraryActivity.MODE_BOOKMARKS)
                M_HISTORY -> openLibrary(LibraryActivity.MODE_HISTORY)
                M_FIND -> showFind()
                M_ADBLOCK -> {
                    ads.setAllowed(pageHost, !adsOffHere)
                    trace("adblock site ${if (adsOffHere) "on" else "off"} $pageHost")
                    tab?.let { reloadTab(it) }
                }
                M_HIDE_ELEMENT -> tab?.let { startPicking(it) }
                M_UNHIDE_SITE -> tab?.let { unhideSite(it, pageHost) }
                M_CAPTURE -> chooseCapture()
                M_GALLERY -> if (tab != null && gallery != null) offerGallery(tab, gallery)
                M_DOWNLOADS -> startActivity(Intent(this, DownloadsActivity::class.java))
                M_SHARE -> tab?.url?.let { share(it) }
                M_OUTSIDE -> tab?.url?.let { openOutside(it) }
                M_STATUS -> showStatus()
                M_TETHER -> startActivity(Intent(this, TetherActivity::class.java))
                M_SETTINGS -> openSettings()
            }
            true
        }
        menu.show()
    }

    /**
     * 메뉴의 '데스크톱 사이트'(탭마다). 일반화된 데스크톱 UA 로 바꾸고([WebViews.applyUserAgent] — UA 일반화 결정을 따른다)
     * 쪽을 다시 부른다(바꾼 UA 는 다음 요청부터다). 세션에 남고, 그 탭에서 연 새 탭이 물려받는다.
     */
    private fun setDesktop(tab: Tab, on: Boolean) {
        tab.desktop = on
        trace("desktop ${if (on) "on" else "off"} tab=${tab.id}")
        tab.webView?.let { WebViews.applyUserAgent(it, on) }
        if (tab.url.isNotEmpty()) reloadTab(tab)
        scheduleSave()
    }

    private fun toggleBookmark(tab: Tab, url: String, marked: Boolean) {
        if (marked) {
            app.library.removeBookmark(url) {
                trace("bookmark removed $url")
                Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            }
        } else {
            app.library.addBookmark(url, tab.title) {
                trace("bookmark added $url")
                Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 북마크·방문 기록 화면 — 고른 주소는 결과로만 받는다([onActivityResult]). */
    private fun openLibrary(mode: String) {
        startActivityForResult(Intent(this, LibraryActivity::class.java).putExtra(LibraryActivity.EXTRA_MODE, mode), REQ_LIBRARY)
    }

    private fun share(url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(send, null))
    }

    private fun copy(url: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(url, url))
        // 13 이상은 시스템이 복사를 알린다.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** 다른 브라우저로 — **프록시를 거치지 않는다**. 구글 로그인처럼 WebView 에서 막히는 쪽을 위한 것. 먼저 묻는다. */
    private fun openOutside(url: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.open_outside)
            .setMessage(R.string.open_outside_warning)
            .setPositiveButton(R.string.open) { _, _ ->
                try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)), null))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── 탭 ──────────────────────────────────────────────────────────────────────────────────────────────────────

    /** 앞서 열려 있던 탭을 되살린다 — 주소·제목만 두고 WebView 는 고를 때 만든다(고른 탭만 바로 연다). 없으면 빈 탭 하나. */
    private fun restoreSession() {
        val saved = session.load()
        if (saved == null || saved.tabs.isEmpty()) {
            newTab(select = true)
            return
        }
        saved.tabs.forEach { s ->
            tabs += Tab(nextTabId++, null).apply {
                url = s.url
                committedUrl = s.url
                title = s.title
                desktop = s.desktop
            }
        }
        trace("restored ${tabs.size}")
        select(tabs[saved.selected.coerceIn(0, tabs.size - 1)])
    }

    private fun scheduleSave() {
        main.removeCallbacks(saveSession)
        main.postDelayed(saveSession, SAVE_DELAY_MS)
    }

    private fun saveSessionNow() {
        main.removeCallbacks(saveSession)
        val saved = tabs.map { t ->
            val url = t.committedUrl.ifEmpty { t.url }
            // data: 는 수십 MB 일 수 있고 about:blank 는 빈 탭이다.
            val keep = url.takeIf { it.length <= MAX_SAVED_URL && !it.startsWith("data:", true) && it != "about:blank" } ?: ""
            SessionStore.Saved(keep, t.title, t.desktop)
        }
        session.save(SessionStore.Session(saved, tabs.indexOf(current).coerceAtLeast(0)))
    }

    private fun newTab(select: Boolean, opener: Tab? = null): Tab {
        val tab = Tab(nextTabId++, null)
        // 연 탭에서 이어지는 탭(새 창·새 탭에서 열기)은 그 탭의 '데스크톱 사이트' 를 물려받는다 — 같은 사이트가 꼴을 바꾸지 않게.
        tab.desktop = opener?.desktop == true
        tab.webView = createWebView(tab)
        tab.openerId = opener?.id
        tab.lastShown = SystemClock.elapsedRealtime()
        val at = opener?.let { tabs.indexOf(it) + 1 } ?: tabs.size
        tabs.add(at.coerceIn(0, tabs.size), tab)
        if (select) {
            select(tab)
        } else {
            renderTabCount()
            trimLiveTabs()
            scheduleSave()
        }
        return tab
    }

    private fun createWebView(tab: Tab): WebView {
        // 통로는 탭마다 하나 — WebView 를 다시 만들면(접기·렌더러 죽음) 새 WebView 에 다시 건다.
        val bridge = if (PageBridge.isSupported()) tab.bridge ?: PageBridge(this, tab, bridgeEvents).also { tab.bridge = it } else null
        val view = WebViews.create(this, bridge)
        if (tab.desktop) WebViews.applyUserAgent(view, desktop = true)
        WebViews.applyForceZoom(view, app.settings.forceZoom)
        view.webViewClient = PageClient(this, tab)
        view.webChromeClient = PageChrome(this, tab)
        // 같은 문서 안의 이동(SPA)을 방문 기록에 — [onSameDocumentNavigation].
        PageNavigation.attach(view, this, tab)
        // 쪽의 뿌리가 맨 위에서 더 밀렸다 — 지금 탭이면 당겨서 새로 고침이 듣는다.
        (view as? PageWebView)?.onTopOverscroll = { if (tab == current && tab.webView === view) content.onTopOverscroll() }
        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            onDownload(tab, url, userAgent, contentDisposition, mimeType, contentLength)
        }
        registerForContextMenu(view)
        view.setFindListener { active, total, done ->
            if (done && tab == current && findBar.visibility == View.VISIBLE) {
                findCount.text = if (total == 0) "0/0" else "${active + 1}/$total"
                trace("find ${active + 1}/$total")
            }
        }
        watchNavigation(view, tab)
        return view
    }

    /**
     * 주 틀의 탐색 알림(androidx.webkit 의 NavigationListener — 실험 표의 NAVIGATION_LISTENER). 찾은 미디어의 **페이지 경계를 커밋 때 옮긴다** —
     * 다른 문서로의 커밋이면 그 탐색의 시작 시각과 함께(그 뒤 처음 찾은 것은 새 페이지의 것이다 — [FoundMediaLog.commit]). 커밋되지 않은
     * 탐색(첨부 응답·204·취소)은 목록을 건드리지 않는다. 가로채기의 갈고리는 다른 사이트로 가는 탐색 동안 걷어 둔다([PageBridge.onNavigationStarted]).
     * 모르는 WebView 면 [onUrlChanged] 가 경계를 옮긴다(시작 시각 없이).
     */
    private fun watchNavigation(view: WebView, tab: Tab) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER)) {
            WebViewCompat.addNavigationListener(view, object : NavigationListener {
                private val started = HashMap<Navigation, Long>()

                override fun onNavigationStarted(navigation: Navigation) {
                    if (navigation.isSameDocument) return
                    started[navigation] = SystemClock.elapsedRealtime()
                    tab.bridge?.onNavigationStarted(navigation.url)
                }

                override fun onNavigationRedirected(navigation: Navigation) {
                    if (!navigation.isSameDocument) tab.bridge?.onNavigationStarted(navigation.url)
                }

                override fun onNavigationCompleted(navigation: Navigation) {
                    val since = started.remove(navigation)
                    val committed = navigation.didCommit()
                    if (!navigation.isSameDocument) tab.bridge?.onNavigationFinished(if (committed) navigation.url else null)
                    if (committed) onNavigationCommitted(tab, navigation.url, navigation.isSameDocument, since)
                }
            })
        }
    }

    /** 탐색이 커밋됐다 — 찾은 미디어의 페이지 경계를 옮긴다(지우지 않는다). 같은 문서 안의 이동이면 지금 제목을 그 페이지의 것으로. */
    private fun onNavigationCommitted(tab: Tab, url: String, sameDocument: Boolean?, navigationStart: Long?) {
        val title = if (sameDocument == true) tab.title else ""
        if (!tab.media.commit(url, title, SystemClock.elapsedRealtime(), sameDocument, navigationStart)) return
        trace("media page ${if (sameDocument == true) "same-document" else "new"} $url")
        if (tab == current) renderMediaButton()
    }

    private fun select(tab: Tab) {
        hideFind()
        // 탭을 바꾸면(같은 탭을 다시 붙여도) 고르기는 끝낸다.
        picker.stop()
        // 주소를 고치던 중에 탭을 바꾸면(탭 목록에서 고르기·닫기) 고치던 글을 버린다 — 그대로 두면 [showUrl] 이 고치는 중이라며 손대지 않아 옛 탭에서
        // 친 글(새 탭이면 빈 칸)이 새 탭의 주소 자리에 남고, 전체 화면에서는 그 초점 때문에 막대가 숨지 않는다(2026-10-08 기기에서 봤다). 새 탭을
        // 치려고 연 길([openNewTabForTyping])은 고른 뒤에 초점을 다시 준다.
        if (address.hasFocus()) {
            suggestions.hide()
            address.clearFocus()
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
        }
        val leaving = current
        leaving?.webView?.let {
            if (leaving != tab && it is PageWebView && (leaving == bgTab || playsInBackground(leaving))) {
                // 뒤에서 재생을 잇는다 — 떼지 않고 보이지 않게 둔다(BackgroundPlay — 떼었다 붙이면 재생이 멈춘다).
                beginBackgroundPlay(leaving)
                it.backstaged = true
            } else {
                it.onPause()
                detach(it)
            }
        }
        current = tab
        tab.lastShown = SystemClock.elapsedRealtime()
        if (tab.webView == null) {
            tab.webView = createWebView(tab)
            tab.crashed = false
            val state = tab.frozen
            tab.frozen = null
            when {
                state != null -> thaw(tab, state)
                tab.url.isNotEmpty() -> load(tab, tab.url)
            }
        }
        tab.webView?.let {
            if (it is PageWebView && it.backstaged && it.parent === content) {
                // 뒤에서 재생을 잇던 탭으로 돌아왔다 — 붙인 채 다시 보인다(떼었다 붙이면 재생이 멈춘다). 앞에서 보고 있으니 회는 닫는다(아래).
                it.backstaged = false
            } else {
                detach(it)
                // 시작 화면 밑에 둔다 — 주소가 없는 탭이면 시작 화면이 덮는다. 뒤에서 재생을 잇는(보이지 않는) 쪽보다는 위.
                content.addView(it, pageIndex(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                it.onResume()
            }
        }
        if (tab == bgTab && foreground) endBackgroundPlay(pauseMedia = false)
        syncBackgroundPlay()
        showUrl()
        renderProgress(tab)
        renderTabCount()
        renderStart()
        renderError()
        renderMediaButton()
        renderCaptureBar()
        main.removeCallbacks(captureTick)
        main.post(captureTick)
        trimLiveTabs()
        scheduleSave()
        // 재생 중 화면 켜 두기는 지금 탭의 것만 본다 — 고른 탭의 틀들에 다시 묻는다(뒤에 있던 동안 다시 알림이 늦었을 수 있다).
        tab.bridge?.askPlaying()
        updateScreenOn()
    }

    /** 살아 있는 WebView 가 [MAX_LIVE_TABS] 를 넘으면 가장 오래 안 본 탭부터 접는다(탭마다 수십 MB). */
    private fun trimLiveTabs() {
        val live = tabs.filter { it.webView != null }
        if (live.size <= MAX_LIVE_TABS) return
        // 녹화·가로채기 중인 탭은 접지 않는다(쪽이 멈추면 받기도 멈춘다).
        live.filter { it != current && it != bgTab && it.bridge?.let { b -> b.capture != null || b.recording != null } != true }
            .sortedBy { it.lastShown }.take(live.size - MAX_LIVE_TABS).forEach { freeze(it) }
    }

    /** 탭을 접는다 — 뒤로·앞으로 기록을 메모리에 두고 WebView 를 없앤다. */
    private fun freeze(tab: Tab) {
        val view = tab.webView ?: return
        val state = Bundle()
        tab.frozen = if (view.saveState(state) != null) state else null
        detach(view)
        view.destroy()
        tab.webView = null
        tab.progress = 100
        trace("frozen tab=${tab.id}")
    }

    /** 접은 탭을 되살린다 — 새 WebView 에 기록을 되돌리면 지금 쪽을 다시 연다. 길이 걸린 뒤에. */
    private fun thaw(tab: Tab, state: Bundle) {
        whenReady {
            val view = tab.webView ?: return@whenReady
            trace("thawed tab=${tab.id}")
            if (view.restoreState(state) == null && tab.url.isNotEmpty()) view.loadUrl(tab.url)
        }
    }

    /** 탭 하나를 닫는다 — 탭 목록의 ✕, 연 탭으로 돌아가는 뒤로. */
    private fun closeTab(tab: Tab) = closeTabs(listOf(tab))

    private fun closeAllTabs() = closeTabs(ArrayList(tabs))

    /**
     * **탭을 닫는 유일한 길** — 하나든 모두든(탭 목록의 ✕·모두 닫기·연 탭으로 돌아가는 뒤로·쪽의 `window.close`). [undoable] 이면 닫은 탭을
     * 뒤로·앞으로 기록과 함께 [closed] 에 남기고(접은 탭과 같은 `saveState` 묶음) 아래에 '탭을 닫았습니다 · 되돌리기' 를 잠깐 띄운다.
     * 지금 탭을 닫으면 연 탭(없으면 옆 탭)으로, 다 닫으면 빈 새 탭 하나.
     */
    private fun closeTabs(targets: List<Tab>, undoable: Boolean = true) {
        val closing = targets.filter { it in tabs }
        if (closing.isEmpty()) return
        if (picker.tab in closing) picker.stop()
        // ── 녹화·가로채기 중인 탭을 닫을 때(BR-MEDIA 의 자리): 지금은 아래의 dispose 가 받은 것을 버린다. 묻거나 저장하려면 여기서 그런 탭을
        //    걸러 물은 뒤 이 함수를 다시 부른다 — 탭을 닫는 길은 여기 하나다.
        val batch = closed.newBatch()
        val was = current
        val wasIndex = tabs.indexOf(was)
        if (bgTab != null && bgTab in closing) endBackgroundPlay(pauseMedia = false)
        for (tab in closing) {
            val index = tabs.indexOf(tab)
            if (undoable) {
                closed.push(
                    ClosedTabs.Entry(tab.committedUrl.ifEmpty { tab.url }, tab.title, snapshot(tab), index, tab.openerId, tab.desktop, batch,
                        selected = tab == was),
                )
            }
            tabs.removeAt(index)
            tab.bridge?.dispose()
            tab.webView?.let {
                detach(it)
                it.destroy()
            }
            tab.webView = null
            tab.frozen = null
        }
        trace("closed tabs=${closing.size} undoable=$undoable")
        var placeholder: Tab? = null
        if (was != null && was in closing) {
            current = null
            val next = tabs.firstOrNull { it.id == was.openerId } ?: tabs.getOrNull(wasIndex.coerceAtMost(tabs.size - 1))
            if (next != null) select(next) else placeholder = newTab(select = true)
        } else {
            renderTabCount()
            scheduleSave()
        }
        val count = closed.countOf(batch)
        if (count > 0) {
            val text = if (count == 1) getString(R.string.tab_closed) else getString(R.string.tabs_closed, count)
            messages.show(text, getString(R.string.undo)) { restoreClosed(closed.takeBatch(batch), placeholder) }
        }
    }

    /** 닫는 탭의 뒤로·앞으로 기록 — 살아 있으면 WebView 에서, 접혔으면 접어 둔 것, 아직 열지 않았으면 없다(주소로 연다). */
    private fun snapshot(tab: Tab): Bundle? {
        tab.frozen?.let { return it }
        val view = tab.webView ?: return null
        val state = Bundle()
        return if (view.saveState(state) != null) state else null
    }

    /**
     * 닫은 탭을 되살린다 — 닫을 때의 자리로, 고르면 접은 탭처럼 뒤로·앞으로 기록이 돌아온다. 닫을 때 보던 탭(없으면 마지막 것)을 고른다.
     * 마지막 탭을 닫아 생긴 빈 새 탭([placeholder])은 아직 쓰지 않았으면 걷는다.
     */
    private fun restoreClosed(entries: List<ClosedTabs.Entry>, placeholder: Tab? = null) {
        if (entries.isEmpty()) return
        // 자리 차례로(앞 자리부터) 넣어야 뒤의 자리가 맞는다 — ClosedTabs.takeBatch 가 그 차례로 준다.
        val restored = entries.map { e ->
            Tab(nextTabId++, null).apply {
                url = e.url
                committedUrl = e.url
                title = e.title
                frozen = e.state
                desktop = e.desktop
                openerId = e.openerId?.takeIf { id -> tabs.any { it.id == id } }
                lastShown = SystemClock.elapsedRealtime()
            }.also { tabs.add(e.index.coerceIn(0, tabs.size), it) }
        }
        trace("restored closed ${entries.size}")
        val unused = placeholder?.takeIf { it in tabs && it.url.isEmpty() && it.webView?.canGoBack() != true }
        select(restored[entries.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: restored.lastIndex])
        unused?.let { closeTabs(listOf(it), undoable = false) }
    }

    /** 메뉴의 '최근 닫은 탭' — 새것부터. 고르면 그 탭을 되살린다(기록째). */
    private fun showRecentTabs() {
        val entries = closed.all.toList()
        if (entries.isEmpty()) return
        val labels = entries.map { e ->
            val host = Uri.parse(e.url).host.orEmpty()
            if (host.isEmpty() || e.label == e.url) e.label else "${e.label} · $host"
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.recent_tabs)
            .setItems(labels.toTypedArray()) { _, which ->
                val e = entries[which]
                if (closed.take(e)) restoreClosed(listOf(e))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /**
     * 새 탭을 열고 주소창에 초점과 자판을 준다 — 새 탭은 주소를 치려고 여는 일이 많다(크롬은 초점을 주지 않고 시작 화면만 보이지만, 그러면
     * 주소창을 한 번 더 눌러야 한다). 빈 글로는 제안 목록을 덮지 않아 시작 화면(북마크·자주 간 곳)이 그대로 보이고, 치기 시작하면 목록이
     * 뜬다(앞 판은 초점만 주어 자판 없이 제안 목록이 시작 화면을 가렸다).
     */
    private fun openNewTabForTyping() {
        newTab(select = true)
        address.requestFocus()
        showKeyboard()
    }

    /** 주소창에 자판을 띄운다 — 창이 아직 초점을 갖지 않았으면(닫히는 창 뒤) 초점을 되찾을 때([onWindowFocusChanged]). */
    private fun showKeyboard() {
        if (!address.hasFocus()) {
            keyboardPending = false
            return
        }
        if (!hasWindowFocus()) {
            keyboardPending = true
            return
        }
        keyboardPending = false
        address.post { getSystemService(InputMethodManager::class.java).showSoftInput(address, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun showTabs() {
        // 줄의 '닫기' 가 제목의 탭 수도 고친다 — 목록만 다시 그리면 닫은 뒤에도 연 때의 수가 남았다.
        var dialog: AlertDialog? = null
        val adapter = object : BaseAdapter() {
            override fun getCount() = tabs.size
            override fun getItem(position: Int) = tabs[position]
            override fun getItemId(position: Int) = tabs[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tab = tabs[position]
                val row = LinearLayout(this@BrowserActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 4), Ui.dp(context, 6))
                    // 줄 안의 '닫기' 단추가 초점을 받을 수 있으면 ListView 가 줄 누르기를 넘기지 않는다(탭을 고를 수 없었다). 단추는 초점 없이도 눌린다.
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
                val texts = LinearLayout(this@BrowserActivity).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(TextView(this@BrowserActivity).apply {
                    text = tab.label
                    textSize = 15f
                    isSingleLine = true
                    if (tab == current) setTypeface(typeface, Typeface.BOLD)
                })
                texts.addView(TextView(this@BrowserActivity).apply {
                    text = tab.url
                    textSize = 12f
                    alpha = 0.7f
                    isSingleLine = true
                })
                // 녹화·가로채기 중인 탭.
                captureMark(tab)?.let { mark ->
                    texts.addView(TextView(this@BrowserActivity).apply {
                        text = mark
                        textSize = 12f
                        isSingleLine = true
                        setTextColor(if (Ui.isNight(context)) RED_NIGHT else RED)
                    })
                }
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.iconButton(this@BrowserActivity, R.drawable.ic_close, getString(R.string.close_tab_named, tab.label)) {
                    settleCaptures(listOf(tab), ask = true) {
                        closeTab(tab)
                        notifyDataSetChanged()
                        dialog?.setTitle(getString(R.string.tabs_count, tabs.size))
                    }
                })
                return row
            }
        }
        val list = ListView(this).apply {
            this.adapter = adapter
            // 탭이 많으면 지금 탭이 보이게.
            setSelection(tabs.indexOf(current).coerceAtLeast(0))
        }
        val shown = AlertDialog.Builder(this)
            .setTitle(getString(R.string.tabs_count, tabs.size))
            .setView(list)
            .setPositiveButton(R.string.new_tab) { _, _ -> openNewTabForTyping() }
            .setNeutralButton(R.string.close_all_tabs) { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.close_all_question, tabs.size))
                    .setPositiveButton(R.string.close_all_tabs) { _, _ -> settleCaptures(ArrayList(tabs), ask = true) { closeAllTabs() } }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.close, null)
            .create()
        dialog = shown
        list.setOnItemClickListener { _, _, position, _ ->
            select(tabs[position])
            shown.dismiss()
        }
        // 목록에서 ✕ 로 닫으면 '되돌리기' 는 이 창 뒤에 뜬다(창이 가린다) — 창을 닫은 때부터 다시 센다.
        shown.setOnDismissListener { messages.restartTimer() }
        shown.show()
    }

    // ── 길게 누르기 ──────────────────────────────────────────────────────────────────────────────────────────────

    /** 고리·그림을 길게 눌렀다 — 글을 길게 누른 것이면 아무것도 넣지 않는다(WebView 가 글 고르기를 한다). */
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val view = v as? WebView ?: return
        val tab = tabs.firstOrNull { it.webView === view } ?: return
        val hit = view.hitTestResult
        val extra = hit.extra
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> if (extra != null) linkItems(menu, tab, extra)
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // 그림을 감싼 고리 — extra 는 그림 주소다. 고리의 주소는 WebView 에 따로 묻는다(채워진 뒤 메시지는 버린다).
                val message = main.obtainMessage()
                view.requestFocusNodeHref(message)
                message.data.getString("url")?.takeIf { it.isNotEmpty() }?.let { linkItems(menu, tab, it) }
                if (extra != null) imageItems(menu, tab, extra)
            }
            WebView.HitTestResult.IMAGE_TYPE -> if (extra != null) imageItems(menu, tab, extra)
        }
        if (menu.size() > 0) hideItem(menu, tab, view)
    }

    /**
     * 길게 누른 고리·그림을 그 자리에서 고르기 시작한다('이 요소 숨기기'). WebView 는 고리·그림의 주소만 알려 주므로 자리는 손가락이 닿은 곳
     * ([PageWebView.lastDownX]) — 그 밖의 자리(글·빈 곳)는 메뉴의 '요소 숨기기' 로 들어가 누른다.
     */
    private fun hideItem(menu: ContextMenu, tab: Tab, view: WebView) {
        val page = view as? PageWebView ?: return
        val x = page.lastDownX
        val y = page.lastDownY
        if (x.isNaN() || y.isNaN() || view.width <= 0 || view.height <= 0 || !isWeb(tab.committedUrl)) return
        val nx = (x / view.width).toDouble()
        val ny = (y / view.height).toDouble()
        menu.add(R.string.hide_this_element).setOnMenuItemClickListener {
            startPicking(tab, nx, ny)
            true
        }
    }

    private fun linkItems(menu: ContextMenu, tab: Tab, url: String) {
        menu.setHeaderTitle(url.take(MENU_TITLE_MAX))
        if (ExternalLinks.isWebScheme(Uri.parse(url))) {
            menu.add(R.string.open_in_new_tab).setOnMenuItemClickListener {
                openInBackground(tab, url)
                true
            }
            // 누르면 재생해 버리는 고리(.mp4 등)도 받을 수 있게 — 받기 확인 창(이름·미리 읽은 크기).
            menu.add(R.string.download_link).setOnMenuItemClickListener {
                chooser.confirmDownload(tab, url, null, null)
                true
            }
            // 고리가 갤러리 받기의 대상이면(pixiv 작품·작가, hitomi 갤러리) 그 페이지를 열지 않고 곧바로 받기로.
            recognizeGallery(url)?.let { target ->
                menu.add(GalleryRules.menuRes(target)).setOnMenuItemClickListener {
                    offerGallery(tab, target)
                    true
                }
            }
        }
        menu.add(R.string.copy_link).setOnMenuItemClickListener {
            copy(url)
            true
        }
        menu.add(R.string.share_link).setOnMenuItemClickListener {
            share(url)
            true
        }
    }

    private fun imageItems(menu: ContextMenu, tab: Tab, src: String) {
        if (menu.size() == 0) menu.setHeaderTitle(src.take(MENU_TITLE_MAX))
        val web = ExternalLinks.isWebScheme(Uri.parse(src))
        if (web || src.startsWith("data:", ignoreCase = true)) {
            menu.add(R.string.download_image).setOnMenuItemClickListener {
                downloadImage(tab, src)
                true
            }
        }
        if (web) {
            menu.add(R.string.open_image_in_new_tab).setOnMenuItemClickListener {
                openInBackground(tab, src)
                true
            }
        }
    }

    /** 뒤에 새 탭을 열어 [url] 을 연다(지금 탭은 그대로). 그 탭에서 뒤로 갈 곳이 없으면 이 탭으로 돌아온다. */
    private fun openInBackground(opener: Tab, url: String) {
        val tab = newTab(select = false, opener = opener)
        trace("background tab=${tab.id} $url")
        load(tab, url)
        Toast.makeText(this, R.string.opened_in_background, Toast.LENGTH_SHORT).show()
    }

    /**
     * 갤러리 받기의 대상인가([SiteUrls.recognize] — 순수 함수, 망에 나가지 않는다). http(s) 이고 너무 길지 않은 주소만 묻고, 알아보기가 던지면
     * 줄을 두지 않는다 — 메뉴를 여는 일 하나가 앱을 내리지 않게(주소는 페이지가 정한 글이다).
     */
    private fun recognizeGallery(url: String): SiteTarget? {
        if (!isWeb(url) || url.length > MAX_SAVED_URL) return null
        return try {
            SiteUrls.recognize(url)
        } catch (e: RuntimeException) {
            trace("gallery recognize failed ${e.javaClass.simpleName}")
            null
        } catch (e: StackOverflowError) {
            trace("gallery recognize overflow")
            null
        }
    }

    /** 갤러리 받기를 묻는다 — 그 탭의 WebView 의 User-Agent 로(쿠키도 그 WebView 의 것 — 페이지와 같게 보이도록). */
    private fun offerGallery(tab: Tab, target: SiteTarget) {
        val userAgent = tab.webView?.settings?.userAgentString?.takeIf { it.isNotEmpty() } ?: AppUserAgent.of(this)
        galleryPrompt.offer(target, userAgent)
    }

    private fun downloadImage(tab: Tab, src: String) {
        val downloads = app.downloads
        val mime = if (src.startsWith("data:", ignoreCase = true)) DataUrl.parse(src)?.mimeType else null
        val name = downloads.names.choose(null, src, mime)
        val referer = Referrer.strictOriginWhenCrossOrigin(tab.committedUrl, src)
        askNotificationPermission()
        downloads.enqueue(src, name, mime, tab.webView?.settings?.userAgentString.orEmpty(), referer, tab.committedUrl, tab.title)
        trace("download asked $name")
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
    }

    // ── 열기 ──────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 지금 길이 걸리고 데워진 뒤에 [block]([NetworkPath.whenReady]). 기다리는 일은 이 화면이 쥐고 있다가 한꺼번에 돌린다 — 화면이 그
     * 사이에 닫히면 버린다(닫힌 WebView 를 건드리지 않게).
     */
    private fun whenReady(block: () -> Unit) {
        if (network.isReady) {
            block()
            return
        }
        pending += block
        if (pending.size == 1) {
            network.whenReady {
                if (isDestroyed) return@whenReady
                val waiting = ArrayList(pending)
                pending.clear()
                waiting.forEach { it() }
            }
        }
    }

    /**
     * 주소창에서 Enter. 인라인 완성이 그대로 있으면 그것이 가리키는 곳으로([AddressSuggestions.destinationFor]), 아니면 친 글을 주소·검색어로
     * 가른다. 주소로 간 것은 '친 것' 으로 센다(검색은 세지 않는다 — 검색 결과 쪽을 인라인으로 채울 일은 없다).
     */
    private fun navigate(input: String) {
        suggestions.destinationFor(input)?.let {
            navigateTo(it, typed = true)
            return
        }
        val target = Omnibox.classify(input)
        if (target is Omnibox.Target.Magnet) {
            // 주소창의 마그넷은 토렌트로(사용자 결정 ⑳) — 앞 판은 검색어가 되어 info-hash 가 검색 엔진에 갔다. 주소창은 지금 쪽의 주소로 되돌린다.
            suggestions.hide()
            address.clearFocus()
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
            showUrl()
            torrentPrompt.offerMagnet(target.uri) { openExternalApp(target.uri) }
            return
        }
        val url = Omnibox.resolve(input, app.settings.searchEngine) ?: return
        navigateTo(url, typed = target is Omnibox.Target.Url)
    }

    /** 주소창에서 고른 곳으로 간다 — [typed] 면 그 쪽을 다 열었을 때 '친 곳' 으로 적는다(인라인 완성이 그런 곳을 먼저 채운다). */
    private fun navigateTo(url: String, typed: Boolean) {
        val tab = current ?: newTab(select = true)
        tab.typedNavigation = typed
        load(tab, url)
        suggestions.hide()
        address.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
    }

    private fun load(tab: Tab, url: String) {
        tab.url = url
        // 다른 곳으로 간다 — 앞 오류의 덮개를 걷는다.
        tab.error = null
        if (tab == current) {
            showUrl()
            renderStart()
            renderError()
        }
        whenReady { tab.webView?.loadUrl(url) }
    }

    /**
     * 새로 고침(막대의 단추·당겨서 새로 고침·덮개의 [다시 시도]). 덮개가 있으면 다시 시도다 — 오류 쪽이 커밋됐으면(그 주소가 지금 쪽) 다시
     * 부르고, 아니면(앞 쪽이 그대로 남았다 — 인증서를 넘기지 않으면 탐색이 멈춘다) 그 주소를 다시 연다. 덮개는 결과가 올 때까지 '다시 여는 중'
     * 으로 남고, 다음 커밋이 오면 걷힌다(다시 실패하면 새 오류로 바뀐다).
     */
    private fun reloadTab(tab: Tab) {
        if (picker.tab == tab) picker.stop()
        val error = tab.error?.takeIf { !it.dismissed }
        if (error != null) {
            error.retrying = true
            error.committed = true
            if (tab == current) renderError()
        } else {
            tab.error = null
            if (tab == current) renderError()
        }
        if (tab.webView == null || tab.crashed) {
            tab.crashed = false
            select(tab)
        } else if (error != null && tab.committedUrl != error.url) {
            whenReady { tab.webView?.loadUrl(error.url) }
        } else {
            whenReady { tab.webView?.reload() }
        }
    }

    /** 지금 탭의 '열지 못함' 덮개 — 오류가 있고 사용자가 닫지 않았으면 보인다. */
    private fun renderError() {
        val tab = current
        val error = tab?.error
        if (error == null || error.dismissed || tab.url.isEmpty()) errorCover.hide() else errorCover.show(error)
    }

    /** 주 문서의 오류를 탭에 둔다 — 같은 주소의 앞 오류가 더 자세하면(중계의 표·인증서) 그것을 두고 이번 실패로 다시 센다. */
    private fun setError(tab: Tab, url: String, source: PageErrors.Source) {
        val next = PageError(url, source, network.state.phase, network.settings.enabled, SystemClock.elapsedRealtime())
        val old = tab.error
        if (old != null && !PageErrors.replaces(old.verdict, old.url == url, next.verdict)) {
            old.committed = false
            old.retrying = false
            old.dismissed = false
            old.at = next.at
            old.phase = next.phase
        } else {
            tab.error = next
        }
        if (tab == current) renderError()
    }

    private fun showUrl() {
        if (address.hasFocus()) return
        val url = current?.url.orEmpty()
        address.setText(if (url == "about:blank") "" else url)
    }

    private fun renderProgress(tab: Tab) {
        if (tab != current) return
        progress.progress = tab.progress
        progress.visibility = if (tab.loading) View.VISIBLE else View.INVISIBLE
        reload.setImageResource(if (tab.loading) R.drawable.ic_stop else R.drawable.ic_reload)
        reload.contentDescription = getString(if (tab.loading) R.string.stop else R.string.reload)
    }

    private fun renderTabCount() {
        tabsButton.text = String.format(Locale.getDefault(), "%d", tabs.size)
        // 화면 낭독기는 '탭 3개' 로 — 설명이 화면의 숫자를 덮으므로 수를 함께 둔다.
        tabsButton.contentDescription = getString(R.string.tabs_count, tabs.size)
        renderTabsCaptureMark()
    }

    /** 녹화·가로채기 중인 탭이 있으면 탭 단추를 빨갛게 — 다른 탭에 있어도 받는 중임이 보이게. */
    private fun renderTabsCaptureMark() {
        val busy = tabs.any { it.bridge?.busy == true }
        // 바뀔 때만 다시 칠한다(표는 단추에 — 밤 모드로 막대를 다시 지으면 새 단추라 다시 칠한다).
        if (tabsButton.tag == busy) return
        tabsButton.tag = busy
        val color = if (busy) (if (Ui.isNight(this)) RED_NIGHT else RED) else Ui.themeColor(this, android.R.attr.textColorPrimary)
        tabsButton.setTextColor(color)
        tabsButton.background = Ui.rounded(Color.TRANSPARENT, 5, this, color)
        tabsButton.stateDescription = if (busy) getString(R.string.capture_tabs_state) else null
    }

    // ── PageHost ─────────────────────────────────────────────────────────────────────────────────────────────────

    override fun onStarted(tab: Tab, url: String) {
        // 고르던 페이지가 스스로 바뀌었다(넘겨주기·시계) — 사용자가 바꾼 것(새로 고침·뒤로·탭)은 그 자리에서 이미 끝냈다.
        picker.endForNavigation(tab)
        tab.bridge?.onMainFrame(Request.hostOf(url))
        tab.url = url
        // 새 쪽 — 앞 쪽의 제목을 지운다(제목 없는 쪽이 앞 쪽의 제목으로 적히지 않게).
        tab.title = ""
        tab.progress = 10
        // 새 문서 — 다 열기 전의 같은 문서 안 이동은 다 연 때 한 번 적는다([onSameDocumentNavigation]).
        tab.historyUrl = ""
        // 오류가 온 주소의 곧이은 첫 커밋은 WebView 가 연 제 오류 쪽이다(오류는 커밋보다 먼저, 곧바로 온다) — 덮개를 둔다. 그 밖의 커밋은
        // 새 쪽이다(오류 쪽 없이 끝난 실패 — 인증서를 넘기지 않으면 앞 쪽이 남는다 — 뒤에 같은 주소가 다시 열린 것도).
        tab.error?.let { e ->
            val errorPage = !e.committed && e.url == url && SystemClock.elapsedRealtime() - e.at < ERROR_PAGE_MS
            if (errorPage) e.committed = true else tab.error = null
        }
        if (tab == current) {
            showUrl()
            renderStart()
            renderError()
        }
        renderProgress(tab)
        adStart(tab, url)
    }

    override fun onFinished(tab: Tab, url: String) {
        trace("finished tab=${tab.id} $url")
        tab.url = url
        tab.committedUrl = url
        tab.progress = 100
        // 다시 불러온 쪽은 제목이 같으면 WebView 가 제목을 다시 알리지 않는다 — 시작에서 지운 제목을 WebView 에 묻는다.
        if (tab.title.isBlank()) tab.webView?.title?.takeIf { it.isNotBlank() && it != url }?.let { tab.title = it }
        if (tab == current) showUrl()
        renderProgress(tab)
        // 설정의 '사이트별 데이터' 가 데이터가 있을 만한 곳을 알게.
        app.sites.record(url)
        val failed = tab.failedUrl == url
        tab.failedUrl = null
        if (!failed && isWeb(url)) app.library.visit(url, tab.title, typed = tab.typedNavigation)
        tab.historyUrl = url
        tab.typedNavigation = false
        // 다시 여는 중이던 덮개가 남았으면(탐색이 커밋 없이 끝났다) 단추를 되살린다.
        tab.error?.takeIf { it.retrying }?.let {
            it.retrying = false
            if (tab == current) renderError()
        }
        adCollect(tab, url, 0)
        scheduleSave()
    }

    /**
     * 같은 문서 안에서 주소가 바뀌었다(SPA — 유튜브 모바일의 다음 영상 등). 새 문서는 다 연 뒤에 적지만 이 이동에는 '다 열었다' 가 오지 않아
     * 앞 판은 방문 기록에 남지 않았다. 새로 고침, 조각(`#…`)만 바뀐 것, 문서를 다 열기 전의 이동(다 연 때 마지막 주소를 적는다)은
     * 적지 않는다. 제목은 지금의 것 — 쪽이 제목을 늦게 바꾸면 [onTitle] 이 고친다.
     */
    override fun onSameDocumentNavigation(tab: Tab, url: String, reload: Boolean) {
        if (reload || !isWeb(url) || tab.historyUrl.isEmpty()) return
        if (url.substringBefore('#') == tab.historyUrl.substringBefore('#')) return
        tab.historyUrl = url
        trace("spa visit tab=${tab.id} $url")
        app.library.visit(url, tab.title)
    }

    private fun isWeb(url: String) = url.startsWith("http://") || url.startsWith("https://")

    // ── 광고 차단 ──────────────────────────────────────────────────────────────────────────────────────────────

    override fun intercept(tab: Tab, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!isWeb(url)) return null
        if (request.isForMainFrame) {
            tab.mainUrl = url
            // 찾은 미디어는 여기서 비우지 않는다 — 탐색이 커밋될 때 페이지의 경계만 옮긴다(watchNavigation). 요청 때 비우면 커밋되지 않는
            // 탐색(첨부·204)이 목록을 지웠고, 커밋 전에 옛 재생기가 낸 요청이 새 목록에 들어갔다.
            adMainFrame(tab, url)
            return null
        }
        adBlock(tab, request, url)?.let { return it }
        sniffMedia(tab, url)
        return null
    }

    /** 주 문서 — 그 쪽의 광고 차단 결정을 정한다. */
    private fun adMainFrame(tab: Tab, url: String) {
        tab.adBlocked.set(0)
        val ads = app.adblock
        val engine = ads.engine
        tab.adPage = if (ads.enabled && engine != null) adPageFor(ads, engine, url) else null
    }

    /** 하위 요청 — 광고면 막는 응답. */
    private fun adBlock(tab: Tab, request: WebResourceRequest, url: String): WebResourceResponse? {
        val ads = app.adblock
        if (!ads.enabled) return null
        val engine = ads.engine ?: return null
        val page = tab.adPage
        if (page?.allowAll == true) return null
        val accept = request.requestHeaders?.entries?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
        val types = RequestType.guess(url, accept, isMainFrame = false, method = request.method ?: "GET")
        engine.shouldBlock(url, page, types) ?: return null
        tab.adBlocked.incrementAndGet()
        trace("adblock blocked $url")
        return blockedResponse(types)
    }

    /**
     * 하위 요청 — 영상·소리·HLS·DASH 면 이 탭의 찾은 미디어에 더한다(그때의 페이지에 — [FoundMediaLog.found]). 처음 보는 HLS 재생목록은 뒤에서
     * 읽어 마스터인지 본다(그 아래 재생목록을 숨긴다).
     */
    private fun sniffMedia(tab: Tab, url: String) {
        val kind = MediaSniffer.classify(url) ?: return
        val change = tab.media.found(url, kind, SystemClock.elapsedRealtime())
        if (change == FoundMediaLog.Change.NONE) return
        main.post {
            if (isDestroyed) return@post
            if (change == FoundMediaLog.Change.NEW) {
                trace("media found $kind $url")
                if (kind == MediaKind.HLS) chooser.readBehind(tab, url)
            }
            if (tab == current) renderMediaButton()
        }
    }

    /**
     * 막대의 '찾은 미디어' — 지금 페이지에서 찾은 수를 단추 위에 보이고 화면 낭독기에는 상태로 알린다(단추가 바뀌면 읽는다).
     * 앞 페이지의 것만 있으면 흐리게 둔다(목록에서 볼 수 있다).
     */
    private fun renderMediaButton() {
        val media = current?.media
        val count = media?.currentCount() ?: 0
        mediaButton.visibility = if (media != null && !media.isEmpty(SystemClock.elapsedRealtime())) View.VISIBLE else View.GONE
        mediaButton.alpha = if (count > 0) 1f else MEDIA_DIM
        (mediaButton.foreground as? CountBadge)?.count = count
        mediaButton.stateDescription = if (count > 0) getString(R.string.media_found_state, count) else getString(R.string.media_found_state_previous)
    }

    private fun adPageFor(ads: AdBlockService, engine: AdBlocker, url: String): AdBlocker.Page {
        val host = Request.hostOf(url)
        return if (ads.isAllowed(host)) AdBlocker.Page(url, host, allowAll = true, noCosmetic = true, noGenericCosmetic = true) else engine.page(url)
    }

    /** 막은 요청의 응답 — 비어 있는 것(그림은 투명한 1×1 GIF — 깨진 그림 표시가 남지 않게). */
    private fun blockedResponse(types: Int): WebResourceResponse {
        val (mime, body) = when (types) {
            RequestType.IMAGE -> "image/gif" to TRANSPARENT_GIF
            RequestType.SCRIPT -> "text/javascript" to ByteArray(0)
            RequestType.STYLESHEET -> "text/css" to ByteArray(0)
            RequestType.SUBDOCUMENT -> "text/html" to ByteArray(0)
            else -> "text/plain" to ByteArray(0)
        }
        return WebResourceResponse(mime, "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(body))
    }

    /**
     * 쪽을 시작할 때 — 그 사이트의 숨김 규칙과 열쇠 없는 일반 규칙, 그리고 **사용자가 숨긴 요소**([HiddenElements] — 그 사이트의 것). 사용자의
     * 규칙은 광고 차단의 켜기·사이트별 끄기·목록의 예외와 상관없이 늘 넣는다(사용자가 직접 고른 것이라). 넣는 길은 같다(한 `<style>`).
     */
    private fun adStart(tab: Tab, url: String) {
        tab.adKeys.clear()
        if (!isWeb(url)) return
        val own = app.hidden.cssFor(Request.hostOf(url))
        val ads = app.adblock
        val engine = ads.engine?.takeIf { ads.enabled }
        var lists = ""
        if (engine != null) {
            var page = tab.adPage
            // 캐시·되살리기로 연 쪽은 주 문서 요청을 거치지 않을 수 있다 — 쪽의 결정이 앞 쪽의 것이면 다시 정한다.
            if (page == null || page.host != Request.hostOf(url)) {
                page = adPageFor(ads, engine, url)
                tab.adPage = page
            }
            lists = engine.startCss(page)
        }
        val css = lists + own
        if (css.isEmpty()) return
        tab.webView?.evaluateJavascript(styleScript(css), null)
        trace("adblock start css ${lists.count { it == '\n' }} own ${own.count { it == '\n' }}")
    }

    /** 쪽을 다 연 뒤 — 쪽이 가진 클래스·id 를 모아 그에 맞는 일반 숨김 규칙을 넣는다. 2.5초·7초 뒤에 한 번씩 더(늦게 붙는 광고). */
    private fun adCollect(tab: Tab, url: String, round: Int) {
        val ads = app.adblock
        if (!ads.enabled || !isWeb(url) || tab.url != url) return
        val engine = ads.engine ?: return
        val page = tab.adPage ?: return
        if (page.noCosmetic || page.noGenericCosmetic) return
        val view = tab.webView ?: return
        view.evaluateJavascript(COLLECT_NAMES) { result ->
            if (tab.webView !== view || tab.url != url || isDestroyed) return@evaluateJavascript
            val (classes, ids) = parseNames(result)
            val newClasses = classes.filter { tab.adKeys.add(".$it") }
            val newIds = ids.filter { tab.adKeys.add("#$it") }
            val css = engine.genericCss(page, newClasses, newIds)
            if (css.isNotEmpty()) view.evaluateJavascript(styleScript(css), null)
            trace("adblock generic css ${css.count { it == '\n' }} round=$round")
        }
        if (round < 2) main.postDelayed({ adCollect(tab, url, round + 1) }, if (round == 0) 2_500L else 4_500L)
    }

    private fun parseNames(json: String?): Pair<List<String>, List<String>> = try {
        val o = JSONObject(json ?: "{}")
        fun list(key: String): List<String> = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        list("c") to list("i")
    } catch (e: org.json.JSONException) {
        emptyList<String>() to emptyList()
    }

    /** CSS 를 쪽에 넣는 스크립트 — 아직 문서의 뿌리가 없으면 DOMContentLoaded 에. */
    private fun styleScript(css: String): String {
        val quoted = JSONObject.quote(css)
        return "(function(c){function a(){var p=document.head||document.documentElement;if(!p)return false;var s=document.createElement('style');" +
            "s.textContent=c;p.appendChild(s);return true}if(!a())document.addEventListener('DOMContentLoaded',a,{once:true})})($quoted)"
    }

    /** [styleScript] 로 넣은 그 CSS 를 걷는 스크립트 — 글이 꼭 같은 `<style>` 만(숨긴 요소의 되돌리기 — 그 규칙 하나만 든 것이다). */
    private fun unstyleScript(css: String): String {
        val quoted = JSONObject.quote(css)
        return "(function(c){var s=document.querySelectorAll('style');for(var i=0;i<s.length;i++)if(s[i].textContent===c)s[i].remove()})($quoted)"
    }

    // ── 요소 숨기기 ────────────────────────────────────────────────────────────────────────────────────────────

    /** 고르는 상태가 숨기라고 할 때 — 넓은 요소면 한 번 더 묻는다. */
    private val pickerHost = object : ElementPicker.Host {
        override fun trace(message: String) = this@BrowserActivity.trace(message)

        override fun hide(tab: Tab, selector: String, info: PickProtocol.Info) {
            val cover = info.box.cover()
            if (!HideSelectors.isBroad(cover)) {
                hideElement(tab, selector)
                return
            }
            trace("pick broad ${(cover * 100).roundToInt()}%")
            AlertDialog.Builder(this@BrowserActivity)
                .setTitle(R.string.picker_broad_title)
                .setMessage(getString(R.string.picker_broad_message, (cover * 100).roundToInt()))
                .setPositiveButton(R.string.picker_hide) { _, _ -> if (picker.tab == tab) hideElement(tab, selector) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /**
     * 메뉴의 '요소 숨기기'(또는 길게 누르기의 '이 요소 숨기기' — [x]·[y] 는 그 자리, 보이는 영역에 대한 비율). 통로가 없는 WebView 판이면
     * 할 수 없다고 알린다. 주소창·찾기·제안·아래 알림 줄은 걷는다.
     */
    private fun startPicking(tab: Tab, x: Double? = null, y: Double? = null) {
        if (tab != current) return
        val bridge = tab.bridge
        if (bridge == null || tab.webView == null) {
            Toast.makeText(this, R.string.capture_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        hideFind()
        if (address.hasFocus()) {
            address.clearFocus()
            showUrl()
        }
        // 주소창이든 페이지의 입력칸이든 자판은 내린다(덮개가 페이지를 가린다 — 칠 곳이 없다).
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(window.decorView.windowToken, 0)
        suggestions.hide()
        messages.hide()
        if (!picker.start(tab, bridge, x, y)) Toast.makeText(this, R.string.picker_unavailable, Toast.LENGTH_LONG).show()
    }

    /**
     * 고른 요소를 그 사이트에서 숨긴다 — 규칙을 저장하고([HiddenElements] — 광고 목록과 따로), 지금 페이지에는 광고 차단의 요소 숨김과 같은 길
     * ([styleScript])로 곧바로 넣는다. 같은 사이트의 다른 탭에는 다시 열 때 걸린다. '숨겼습니다 · 되돌리기' 를 잠깐 띄운다.
     */
    private fun hideElement(tab: Tab, selector: String) {
        val view = tab.webView ?: return
        val host = Request.hostOf(tab.committedUrl)
        val hidden = app.hidden
        val rule = hidden.ruleFor(host, selector)
        picker.stop()
        if (rule == null) {
            trace("hide refused $selector")
            Toast.makeText(this, R.string.picker_no_selector, Toast.LENGTH_LONG).show()
            return
        }
        val css = CosmeticEngine.css(listOf(rule.selector))
        view.evaluateJavascript(styleScript(css), null)
        if (!hidden.add(rule)) {
            trace("hide again $rule")
            Toast.makeText(this, R.string.picker_already, Toast.LENGTH_SHORT).show()
            return
        }
        trace("hide $rule")
        messages.show(getString(R.string.picker_hidden, rule.site), getString(R.string.undo)) {
            hidden.remove(rule)
            // 그 WebView 가 살아 있으면 그 자리에서 걷는다 — 글이 꼭 같은 `<style>` 만 걷으므로 다른 페이지로 갔으면(그 페이지는 시작할 때 모든
            // 규칙을 한 `<style>` 로 받았다) 아무것도 걷지 않고, 다음에 열 때부터 걸리지 않는다.
            if (tab.webView === view) view.evaluateJavascript(unstyleScript(css), null)
            trace("hide undone $rule")
        }
    }

    /**
     * 메뉴의 '이 사이트에서 숨긴 요소 되돌리기' — 그 사이트의 사용자 규칙을 모두 지우고 다시 연다(이미 넣은 CSS 는 다시 열어야 걷힌다).
     * '다시 숨기기' 는 지운 규칙을 되살리고 그 사이트의 페이지에 곧바로 다시 넣는다.
     */
    private fun unhideSite(tab: Tab, host: String) {
        val hidden = app.hidden
        val site = hidden.siteOf(host)
        val removed = hidden.removeSite(site)
        if (removed.isEmpty()) return
        trace("unhide site $site ${removed.size}")
        reloadTab(tab)
        messages.show(getString(R.string.hide_restored, site, removed.size), getString(R.string.hide_again)) {
            hidden.restore(removed)
            trace("unhide undone $site")
            val view = tab.webView
            if (view != null && tab in tabs && hidden.siteOf(Request.hostOf(tab.committedUrl)) == site) {
                view.evaluateJavascript(styleScript(CosmeticEngine.css(removed.map { it.selector })), null)
            }
        }
    }

    override fun onUrlChanged(tab: Tab, url: String) {
        tab.url = url
        tab.committedUrl = url
        if (tab == bgTab && !BackgroundPlay.isYouTube(url)) endBackgroundPlay(pauseMedia = false)
        if (tab == current) showUrl()
        // 찾은 미디어의 페이지 경계 — 탐색 알림을 모르는 WebView 에서만(아는 WebView 는 watchNavigation 이 시작 시각과 함께 옮긴다).
        if (!navigationListenerSupported) onNavigationCommitted(tab, url, sameDocument = null, navigationStart = null)
        scheduleSave()
    }

    override fun onProgress(tab: Tab, progress: Int) {
        tab.progress = progress
        renderProgress(tab)
    }

    override fun onTitle(tab: Tab, title: String) {
        trace("title tab=${tab.id} $title")
        tab.title = title
        if (tab == bgTab) syncBackgroundPlay()
        // 찾은 미디어 — 지금 페이지의 제목(제목 없이 찾은 항목에도 채운다).
        tab.media.retitle(title)
        // 제목은 쪽을 다 열기 전에도 온다 — 그때 기록에 아직 줄이 없으면 다 연 뒤의 visit 이 이 제목으로 적는다.
        if (tab.failedUrl != tab.url && isWeb(tab.url)) app.library.retitle(tab.url, title)
        scheduleSave()
    }

    /**
     * 주 문서가 열리지 않았다 — 그 탭 위에 '열지 못함' 덮개를 둔다(까닭은 오류·중계와 프록시 서버의 표·길의 상태로 고른다 — [PageErrors]).
     * 앞 판은 WebView 의 영어 오류 쪽이나 빈 쪽뿐이었다. 사용자가 멈췄거나 다른 탐색이 앞선 것은 덮지 않는다.
     */
    override fun onMainFrameError(tab: Tab, url: String, source: PageErrors.Source) {
        trace("error tab=${tab.id} $source $url")
        tab.failedUrl = url
        if (PageErrors.isIgnored(source)) return
        setError(tab, url, source)
    }

    /**
     * 인증서 오류(넘기지 않는다). **주 문서의 것만** 덮개로 알린다 — 그림 하나의 인증서로 쪽 전체가 막힌 것처럼 보이지 않게(앞 판은 하위
     * 자원에도 '열지 않았습니다' 토스트를 띄웠다). 덮개는 탭의 것이라 뒤에서 연 탭이면 그 탭으로 갔을 때 보인다. 주 문서인지는 그 탭이 마지막으로
     * 청한 주 문서의 주소(넘겨주기를 따라간 것 — [intercept])나 연 주소로 가린다.
     */
    override fun onSslError(tab: Tab, url: String) {
        val mainFrame = url.isNotEmpty() && (url == tab.mainUrl || url == tab.url)
        trace("ssl error tab=${tab.id} main=$mainFrame $url")
        if (!mainFrame) return
        tab.failedUrl = url
        setError(tab, url, PageErrors.Source.Certificate)
    }

    override fun askSiteLogin(host: String, realm: String, handler: HttpAuthHandler) {
        val user = EditText(this).apply {
            hint = getString(R.string.login_user)
            isSingleLine = true
        }
        val pass = EditText(this).apply {
            hint = getString(R.string.login_password)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), 0)
            addView(user)
            addView(pass)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.login_title, host))
            .setMessage(realm)
            .setView(form)
            .setPositiveButton(R.string.login) { _, _ -> handler.proceed(user.text.toString(), pass.text.toString()) }
            .setNegativeButton(R.string.cancel) { _, _ -> handler.cancel() }
            .setOnCancelListener { handler.cancel() }
            .show()
    }

    override fun askExternal(url: String) {
        if (TorrentRules.isMagnet(url)) {
            // 마그넷은 '이 앱에서 받기 / 다른 앱으로' 를 묻는다(사용자 결정 ⑱) — 그 창이 다른 앱은 프록시를 거치지 않는다고도 말한다.
            torrentPrompt.offerMagnet(url) { openExternalApp(url) }
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.external_title, ExternalLinks.label(url)))
            .setMessage(R.string.external_warning)
            .setPositiveButton(R.string.open) { _, _ -> openExternalApp(url) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 다른 앱에 넘긴다(시스템이 받을 앱을 고른다). 받을 앱이 없으면 `intent:` 의 대신 열 주소, 그것도 없으면 알린다. */
    private fun openExternalApp(url: String) {
        val intent = ExternalLinks.intentFor(url)
        val opened = intent != null && try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
        if (!opened) {
            val fallback = ExternalLinks.fallbackUrl(url)
            if (fallback != null) current?.let { load(it, fallback) } else Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    override fun openWindow(opener: Tab, resultMsg: Message): Boolean {
        val tab = newTab(select = true, opener = opener)
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = tab.webView
        resultMsg.sendToTarget()
        return true
    }

    override fun popupAllowed(opener: Tab): Boolean = app.popups.isAllowed(Request.hostOf(opener.committedUrl))

    /**
     * 사용자가 누르지 않은 팝업을 막았다(누른 뒤 한참 있다 여는 결제 창 등 — 앞 판은 소리 없이 막았다). 아래에 '팝업을 막았습니다 ·
     * 이 사이트 허용' 을 띄운다 — 허용하면 그 사이트(등록 도메인)의 팝업을 그 뒤로 연다. 막힌 창은 다시 열 수 없다(WebView 가 그 주소를
     * 알려 주지 않는다) — 쪽에서 다시 해 보게 한다. 같은 쪽에서 거듭 막히면(광고) 한 번만 알린다.
     */
    override fun onPopupBlocked(opener: Tab) {
        val host = Request.hostOf(opener.committedUrl)
        trace("popup blocked tab=${opener.id} $host")
        if (opener != current || host.isEmpty()) return
        val key = "${opener.id} ${opener.committedUrl}"
        if (key == lastPopupNotice) return
        lastPopupNotice = key
        val site = app.popups.siteOf(host)
        messages.show(getString(R.string.popup_blocked, site), getString(R.string.popup_allow)) {
            app.popups.setAllowed(host, true)
            trace("popups allowed $site")
            Toast.makeText(this, getString(R.string.popup_allowed, site), Toast.LENGTH_LONG).show()
        }
    }

    /** 쪽이 스스로 닫았다(`window.close` — 로그인 창 등). 되돌리기는 띄우지 않는다(사용자가 닫은 것이 아니다). */
    override fun closeWindow(tab: Tab) {
        // 쪽이 스스로 닫는다 — 묻지 않고 받은 데까지 저장한다(settleCaptures 의 머리말). 되돌리기는 띄우지 않는다.
        settleCaptures(listOf(tab), ask = false) { closeTabs(listOf(tab), undoable = false) }
    }

    /**
     * **재생 중 화면 켜 두기**(사용자 요청, 2026-10-08 — '미디어 재생 중엔 화면 꺼짐을 방지'). 지금 탭에서 소리가 나는 미디어가 재생 중이거나,
     * 전체 화면 영상을 보는 동안 영상이 재생 중이면 창에 `FLAG_KEEP_SCREEN_ON` 을 건다([ScreenWake]). 창이 보이는 동안만 듣는 깃발이라 앱을
     * 떠나면 저절로 풀리고, 걷을 때 시스템이 꺼짐 시간을 처음부터 센다(`ON_AFTER_RELEASE`). 틀이 알리지 못하고 사라졌을 때를 위해 틀 하나가
     * 오래되는 때 다시 본다.
     */
    private fun updateScreenOn() {
        main.removeCallbacks(screenCheck)
        val now = SystemClock.elapsedRealtime()
        val playing = current?.bridge?.playing
        val state = playing?.state(now) ?: PlayingMedia.State.NONE
        val keep = ScreenWake.wanted(state, fullscreenVideo = customView != null)
        if (keep != keepingScreenOn) {
            keepingScreenOn = keep
            if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            trace("screen-on ${if (keep) "keep" else "release"} audible=${state.audible} video=${state.video} fullscreen=${customView != null}")
        }
        playing?.nextStale(now)?.let { main.postDelayed(screenCheck, it - now) }
    }

    override fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        picker.stop()
        fullscreen.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fullscreen.visibility = View.VISIBLE
        messages.hide()
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // 영상은 지금까지처럼 노치를 피해 그린다(노치까지 그리는 것은 전체 화면의 페이지다). 막대의 시계는 멈춘다 — 끝나면 숨긴 채로 돌아간다.
        if (fullscreenMode) setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT)
        main.removeCallbacks(autoHide)
        // 회전 잠금을 켠 폰에서도 영상을 가로로 볼 수 있게 — 전체 화면 동안은 기기의 방향 센서를 따른다. 영상이 가로인지 알아
        // 가로로 묶지(크롬) 못하는 것은 WebView 가 전체 화면 요소의 크기를 알려 주지 않아서다 — 알려면 쪽에 스크립트를 넣어야 한다.
        if (orientationBeforeFullscreen == null) orientationBeforeFullscreen = requestedOrientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        root.requestApplyInsets()
        // 전체 화면으로 보는 영상은 음소거여도 화면을 켜 둔다.
        updateScreenOn()
    }

    override fun hideCustomView() {
        val view = customView ?: return
        detach(view)
        fullscreen.visibility = View.GONE
        customView = null
        if (fullscreenMode) {
            // 전체 화면으로 돌아간다 — 아래 내비게이션 바만 되살리고 상태 표시줄과 막대는 숨긴 채로, 페이지는 다시 노치까지.
            window.insetsController?.show(WindowInsets.Type.navigationBars())
            setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
            hideBar(animate = false, why = "video")
        } else {
            window.insetsController?.show(WindowInsets.Type.systemBars())
            // 영상 중에 전체 화면이 꺼졌으면(TalkBack·분할 화면 — applyFullscreen 은 영상이 끝나기를 기다렸다) 막대를 제자리로.
            setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT)
            placeChrome()
        }
        // 들어가기 전의 방향으로(보통은 '정하지 않음' — 시스템의 회전 설정을 따른다).
        orientationBeforeFullscreen?.let { requestedOrientation = it }
        orientationBeforeFullscreen = null
        root.requestApplyInsets()
        customCallback?.onCustomViewHidden()
        customCallback = null
        updateScreenOn()
    }

    override fun pickFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            startActivityForResult(params.createIntent(), REQ_FILES)
            true
        } catch (e: ActivityNotFoundException) {
            fileCallback = null
            false
        }
    }

    @Deprecated("플랫폼 Activity 의 결과 받기 — AndroidX 없이")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILES) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        if (requestCode == REQ_LIBRARY) {
            val url = data?.getStringExtra(LibraryActivity.RESULT_URL)
            if (resultCode == RESULT_OK && url != null) {
                trace("library open $url")
                val target = if (data.getBooleanExtra(LibraryActivity.RESULT_NEW_TAB, false)) newTab(select = true) else current ?: newTab(select = true)
                load(target, url)
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onProtectedMedia(tab: Tab, origin: String) {
        trace("protected-media granted $origin")
    }

    /**
     * 렌더러가 사라졌다 — 그 탭의 WebView 를 부순다(앱은 산다). 지금 탭이면 덮개로 까닭과 [다시 시도]를(앞 판은 빈 화면과 토스트뿐이었다),
     * 뒤의 탭은 고를 때 다시 연다(앞 판과 같이).
     */
    override fun onRendererGone(tab: Tab, crashed: Boolean) {
        if (picker.tab == tab) picker.stop()
        if (tab == bgTab) endBackgroundPlay(pauseMedia = false)
        // 녹화는 받은 데까지 저장한다(쪽이 사라져 더 넘기지 못한다). 가로채기는 다시 연 쪽에서 잇는다.
        tab.bridge?.onPageGone()
        tab.webView?.let {
            detach(it)
            it.destroy()
        }
        tab.webView = null
        tab.crashed = true
        trace("renderer gone tab=${tab.id} crashed=$crashed")
        if (tab == current) {
            val error = PageError(tab.url, PageErrors.Source.Renderer(crashed), network.state.phase, network.settings.enabled, SystemClock.elapsedRealtime())
            // 다시 열면 새 WebView 가 그 주소를 연다 — 그 커밋은 새 쪽이다.
            error.committed = true
            tab.error = error
            renderProgress(tab.apply { progress = 100 })
            renderError()
        }
    }

    private fun onDownload(tab: Tab, url: String, userAgent: String, contentDisposition: String?, mimeType: String?, contentLength: Long) {
        if (url.startsWith("blob:", ignoreCase = true)) {
            onBlobDownload(tab, url, contentDisposition, mimeType)
            return
        }
        // 내려받기로 끝난 탐색은 화면을 바꾸지 않는다 — 주소창을 지금 쪽으로 되돌리고, Referer 는 그 쪽에서(크롬의 기본 정책).
        tab.url = tab.committedUrl
        tab.progress = 100
        if (tab == current) showUrl()
        renderProgress(tab)
        val referer = Referrer.strictOriginWhenCrossOrigin(tab.committedUrl, url)
        val downloads = app.downloads
        val name = downloads.names.choose(contentDisposition, url, mimeType)
        trace("download asked $name")
        val size = if (contentLength > 0) Formatter.formatShortFileSize(this, contentLength) else getString(R.string.size_unknown)
        val pageUrl = tab.committedUrl
        val pageTitle = tab.title
        val saveFile = {
            askNotificationPermission()
            downloads.enqueue(url, name, mimeType?.takeIf { it.isNotBlank() }, userAgent, referer, pageUrl, pageTitle)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        }
        if (TorrentRules.isTorrentFile(mimeType, name, url)) {
            // `.torrent` 는 '토렌트로 받기 / 파일로 저장' 을 묻는다(사용자 결정 ⑲). 파일로 저장은 지금까지의 내려받기다.
            torrentPrompt.offerFile(name, size, url, userAgent, referer, saveFile)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.download_question)
            .setMessage(getString(R.string.download_detail, name, size))
            .setPositiveButton(R.string.download) { _, _ -> saveFile() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * `blob:` — 쪽 안의 파일. 그 바이트는 쪽에만 있어 틀들에 물어 가진 틀이 넘겨준다([PageBridge]). 이름은 고리의 `download` 속성 →
     * WebView 가 준 Content-Disposition → 형식으로 고른 이름.
     */
    private fun onBlobDownload(tab: Tab, url: String, contentDisposition: String?, mimeType: String?) {
        tab.url = tab.committedUrl
        tab.progress = 100
        if (tab == current) showUrl()
        renderProgress(tab)
        val bridge = tab.bridge
        if (bridge == null) {
            Toast.makeText(this, R.string.download_blob_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        trace("blob asked $url")
        bridge.findBlob(url) { offer ->
            if (isDestroyed) return@findBlob
            if (offer == null) {
                trace("blob none")
                offerBlobHook(tab)
                return@findBlob
            }
            val mime = offer.type.substringBefore(';').ifBlank { mimeType?.takeIf { it.isNotBlank() } ?: "" }.ifBlank { null }
            val downloads = app.downloads
            // 고리의 download 속성이 이름이다 — 확장자만 형식에 맞춘다(앞 판은 확장자 없는 이름을 버려 'download.mp4' 였다).
            val name = offer.name.takeIf { it.isNotBlank() }?.let { downloads.names.named(it, mime) }
                ?: downloads.names.choose(contentDisposition, "blob:/download", mime)
            trace("download asked $name")
            val size = if (offer.size >= 0) Formatter.formatShortFileSize(this, offer.size) else getString(R.string.size_unknown)
            AlertDialog.Builder(this)
                .setTitle(R.string.download_question)
                .setMessage(getString(R.string.download_detail, name, size))
                .setPositiveButton(R.string.download) { _, _ ->
                    askNotificationPermission()
                    bridge.acceptBlob(offer, downloads.startPushed(name, mime, offer.size))
                    Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> bridge.declineBlob(offer) }
                .setOnCancelListener { bridge.declineBlob(offer) }
                .show()
        }
    }

    /**
     * 쪽이 blob 을 넘겨주지 못했다 — 쪽이 이미 주소를 거뒀거나 쪽의 CSP 가 읽기를 막았다. 사용자가 고르면 이 탭에 blob 갈고리를 걸고 쪽을 다시
     * 연다(그 사이트에 있는 동안만 — [PageBridge.armBlob]). 그 뒤 받기를 한 번 더 누르면 된다.
     */
    private fun offerBlobHook(tab: Tab) {
        val view = tab.webView ?: return
        val bridge = tab.bridge ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.download_blob_gone)
            .setMessage(R.string.download_blob_hook_question)
            .setPositiveButton(R.string.download_blob_hook) { _, _ ->
                bridge.armBlob(view, Request.hostOf(tab.committedUrl))
                reloadTab(tab)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    private companion object {
        /** 막대를 밀 때 이웃 탭이 보여야 넘어가는 몫(쪽 폭의) — 파이어폭스 25%. 그보다 적어도 민 방향으로 튕기면 넘어간다. */
        private const val SWIPE_COMMIT_FRACTION = 0.25f

        /** 지금 탭과 들어오는 이웃 탭 사이의 틈(dp) — 크롬 30, 파이어폭스 48. */
        private const val SWIPE_GAP_DP = 30

        /** 끝에서 밀면(더 갈 탭이 없다) 쪽 폭의 이만큼까지만 줄어드는 비율로 따라온다 — 크롬 1/5, 파이어폭스 20%. */
        private const val EDGE_FRACTION = 0.2f

        /** 넘기기·되돌리기·반대로 튕겨 되돌리기(ms) — 파이어폭스. */
        private const val COMMIT_MS = 250L
        private const val CANCEL_MS = 200L
        private const val FLING_CANCEL_MS = 150L

        /**
         * 전체 화면에서 막대를 만지지 않은 채 이만큼 지나면 숨는다(ms — 접근성의 '조작할 시간' 이 더 길면 그만큼). 시스템이 잠깐 보이는 상태
         * 표시줄은 2.25초에 숨는데(AOSP 12 SystemUI AutoHideController 의 AUTO_HIDE_TIMEOUT_MS) 그것은 보기만 하는 막대다 — 이 막대는 상태 글(출구
         * 나라)을 읽고 엄지를 메뉴·탭·주소창으로 옮길 틈이 있어야 한다. 그보다 길면 페이지를 가린 채 오래 남는다(페이지를 누르면 곧바로 숨는다).
         */
        private const val AUTO_HIDE_MS = 4_000L

        /**
         * 막대가 내려오고 올라가는 시간(ms) — 상태 표시줄을 미는 시간과 같다(플랫폼 InsetsController 의 ANIMATION_DURATION_MOVE_IN_MS·_OUT_MS 와
         * 곡선 (0.4, 0, 0.2, 1) = fast_out_slow_in, BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 일 때 — SDK 의 API 36.1 소스에서 확인).
         */
        private const val REVEAL_MS = 275L
        private const val CONCEAL_MS = 340L

        /**
         * 막대를 부르는 가장자리 띠 — 상태 표시줄의 높이를 모를 때(dp — 창이 화면 맨 위에 닿지 않는다 등). 띠는 상태 표시줄의 높이가 기본이다 —
         * 시스템이 상태 표시줄을 부르는 밀기도 그 높이에서 시작한 것을 본다(AOSP 12 SystemGesturesPointerEventListener — 위에 노치가 있으면
         * 조금 더 넓힌다). 24dp 는 안드로이드의 오랜 상태 표시줄 높이다.
         */
        private const val EDGE_FALLBACK_DP = 24

        /** 막대의 위 여백(dp) — 전체 화면이면 상태 표시줄의 높이를 더한다. */
        private const val BAR_PAD_TOP_DP = 4

        const val REQ_FILES = 10
        const val REQ_NOTIFICATIONS = 11
        const val REQ_LIBRARY = 12
        const val M_BACK = 1
        const val M_FORWARD = 2
        const val M_NEW_TAB = 3
        const val M_TABS = 4
        const val M_DOWNLOADS = 5
        const val M_SHARE = 6
        const val M_OUTSIDE = 7
        const val M_SETTINGS = 8
        const val M_STATUS = 9
        const val M_BOOKMARK = 10
        const val M_BOOKMARKS = 11
        const val M_HISTORY = 12
        const val M_FIND = 13
        const val M_ADBLOCK = 14
        const val M_CAPTURE = 15
        const val M_RECENT_TABS = 16
        const val M_DESKTOP = 17
        const val M_POPUPS = 18
        const val M_HIDE_ELEMENT = 19
        const val M_UNHIDE_SITE = 20
        const val M_TETHER = 21
        const val M_GALLERY = 22
        const val CAPTURE_TICK_MS = 2_000L

        /** 주 문서의 오류가 난 뒤 이만큼 안에 온 길의 고장(닿지 못함·계정 거절…)은 그 오류의 까닭으로 본다(중계의 소식이 조금 늦게 온다). */
        const val PATH_NEWS_MS = 5_000L

        /** 오류 뒤 이만큼 안에 같은 주소가 커밋되면 WebView 의 오류 쪽이다(곧바로 온다) — 그보다 늦으면 새로 연 쪽이다. */
        const val ERROR_PAGE_MS = 3_000L

        /** '찾은 미디어' 단추 — 지금 페이지에서 찾은 것이 없고 앞 페이지의 것만 있을 때. */
        const val MEDIA_DIM = 0.45f

        /** 쪽이 가진 클래스·id 를 모은다(IIFE — 쪽에 이름을 남기지 않는다). 너무 큰 쪽은 앞의 것만. */
        const val COLLECT_NAMES = "(function(){var c={},i={},e=document.querySelectorAll('[class],[id]');" +
            "for(var k=0;k<e.length&&k<20000;k++){var x=e[k];if(typeof x.id==='string'&&x.id)i[x.id]=1;var l=x.classList;" +
            "if(l)for(var j=0;j<l.length;j++)c[l[j]]=1}return{c:Object.keys(c).slice(0,8000),i:Object.keys(i).slice(0,4000)}})()"

        /** 투명한 1×1 GIF. */
        val TRANSPARENT_GIF: ByteArray = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
        )

        /** 살아 있는 WebView 의 수 — 넘으면 오래 안 본 탭을 접는다(CLAUDE.md '사용자가 정할 것' — 지금은 이 값). */
        const val MAX_LIVE_TABS = 5

        const val SAVE_DELAY_MS = 1_000L
        const val MAX_SAVED_URL = 8 * 1024
        const val MENU_TITLE_MAX = 200

        val GREEN = Color.rgb(0x2E, 0x7D, 0x32)
        val AMBER = Color.rgb(0xB2, 0x6A, 0x00)
        val RED = Color.rgb(0xC6, 0x28, 0x28)
        val GREEN_NIGHT = Color.rgb(0x81, 0xC7, 0x84)
        val AMBER_NIGHT = Color.rgb(0xFF, 0xB7, 0x4D)
        val RED_NIGHT = Color.rgb(0xEF, 0x9A, 0x9A)
    }
}
