package io.github.donggi.iroiroviewer.webhost

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * **잠긴** WebView. 문서를 그리는 유일한 길이다.
 *
 * ## 무엇을 잠그는가
 *
 * 이 앱은 **모든 파일 접근 권한**을 가지고 돈다. 그래서 문서를 그리는 엔진에 구멍이
 * 하나라도 있으면 그 피해가 앱 샌드박스가 아니라 기기 저장소 전체다. 잠그는 항목은
 * 전부 그 사실에서 나온다.
 *
 * | 설정 | 왜 |
 * |---|---|
 * | `javaScriptEnabled = false` | 문서는 읽는 것이지 도는 것이 아니다. `format:html` 이 스크립트를 이미 지우지만 겹쳐 막는다. **켜는 곳이 없다** — 아래 '스크립트는 켜지 않는다' |
 * | `allowFileAccess = false` | **이것이 가장 중요하다.** 켜져 있으면 문서 안의 `file:///sdcard/…` 한 줄이 사용자의 파일을 읽는다 |
 * | `allowContentAccess = false` | `content://` 로 다른 앱의 프로바이더를 훑는 길을 막는다 |
 * | `allowFileAccessFromFileURLs` · `allowUniversalAccessFromFileURLs` | 위와 짝이다. 둘 다 기본값이 판마다 달라 **명시한다** |
 * | `domStorageEnabled = false` · `databaseEnabled = false` | 문서가 기기에 무언가 남길 이유가 없다 |
 * | `setGeolocationEnabled(false)` | 같은 이유다 |
 * | `mediaPlaybackRequiresUserGesture = true` | 열자마자 소리가 나지 않게 한다 |
 * | `cacheMode = LOAD_NO_CACHE` | 사용자의 책 내용이 WebView 캐시로 디스크에 복제되지 않게 한다. 만화 쪽을 디스크에 쓰지 않는 것과 같은 판단이다 |
 * | `safeBrowsingEnabled = false` | 안전 브라우징은 **URL 을 구글에 보내는 기능**이다. 우리 문서는 기기 안의 파일이고 그 이름이 밖으로 나갈 이유가 없다 |
 * | `blockNetworkLoads = true` | **명시한다.** `INTERNET` 이 없으면 기본값도 참이지만 선언하면 거짓으로 뒤집힌다 — 선언하는 날에도 이 줄이 막는다. 우리가 내주는 응답(가로챈 것)은 그대로 나간다 |
 *
 * ## 요청은 전부 우리를 지난다
 *
 * [WebViewClient.shouldInterceptRequest] 가 **모든** 요청을 받는다. 우리 호스트가 아니면
 * 빈 404 를 돌려준다 — `null` 을 돌려주면 WebView 가 스스로 가져오려 하고, 그 시도가
 * (막히더라도) 화면을 멎게 한다.
 *
 * ## 스크롤을 Compose 와 다투지 않는다
 *
 * WebView 는 스스로 스크롤한다. 그래서 이 컴포저블을 **스크롤 컨테이너 안에 넣지 마라** —
 * 높이가 무한이 되어 측정에서 죽거나, 두 스크롤이 서로 손가락을 빼앗는다.
 *
 * ## 읽는 모양과 읽던 자리
 *
 * 글자 크기는 **`WebSettings.textZoom`** 으로 건다 — 책의 CSS 가 글자 크기를 몇 겹으로 적어 두어도 그 위에서 곱해지므로
 * 책의 조판과 다투지 않고, 쪽을 다시 읽지 않는다. 여백·바탕은 CSS 라 문서를 다시 읽어야 한다([contentKey]). 둘 다 바뀌면
 * 글이 다시 흘러 같은 화소가 다른 곳을 가리키므로 **보던 자리(비율)로 돌아온다**([ReaderWebView]).
 *
 * 스크립트가 꺼져 있어 자리는 Kotlin 이 잰다 — 스크롤 범위와 위치를 비율로 바꾼다([ReadingScroll]).
 *
 * ## 렌더러가 죽으면
 *
 * WebView 는 그리는 일을 따로 떨어진 렌더러 프로세스에 맡기고, 이 앱의 WebView 는 그것 하나를 나눠 쓴다. 그것이 죽으면(문서가
 * 렌더러를 무너뜨렸거나, 시스템이 메모리를 찾아 죽였다) 떠 있는 WebView 가 모두 `onRenderProcessGone` 을 받는데, **처리하지 않으면
 * WebView 가 앱을 함께 끝낸다**(기본 구현이 false 를 돌려준다) — 그림 뷰어가 SVG 쪽을 미리 세우는 것만으로도 앱이 죽을 수 있다.
 * 죽은 WebView 는 다시 쓸 수 없으므로 떼어 내 부수고, 그 쪽은 비워
 * 둔 채 [onRenderGone] 으로 알린다. **다른 쪽([url]이 바뀐다)으로 가면 새 WebView 가 선다** — 새 렌더러로 다시 그린다. 같은 쪽을 다시
 * 세우지는 않는다 — 문서가 무너뜨린 것이면 되풀이될 뿐이다.
 *
 * ## 스크립트는 켜지 않는다
 *
 * 가로채기(`shouldInterceptRequest`)는 HTTP 요청만 본다. 스크립트가 돌면 웹소켓·WebRTC·이름 미리 찾기(DNS prefetch)가 그것을
 * 지나지 않고, 그 가운데 이름 미리 찾기와 WebRTC 는 CSP 로도 막지 못한다 — 이 앱이 `INTERNET` 을 쥐게 되면(Web 과 합치는 등)
 * 사용자의 폴더를 읽을 수 있는 쪽이 밖으로 보낼 길이 생긴다.
 */
@Composable
fun LockedWebView(
    url: String,
    provider: ResourceProvider,
    /**
     * 우리 호스트 안의 **다른 쪽**으로 가려 한다(장 사이 링크).
     * `true` 를 주면 우리가 처리한 것으로 보고 WebView 는 움직이지 않는다.
     *
     * [fragment] 는 링크의 조각(`#` 뒤, 없으면 null)이다. **함께 건넨다** — 경로만 받으면 다른
     * 장의 각주·책갈피로 가는 링크가 그 장의 맨 위에 떨어진다.
     */
    onNavigate: (path: String, fragment: String?) -> Boolean,
    modifier: Modifier = Modifier,
    /** 쪽을 다 그렸다. 화면이 '여는 중' 을 지우는 데 쓴다. */
    onReady: () -> Unit = {},
    /**
     * 손가락으로 확대할 수 있는가. 흐르는 글(EPUB·마크다운)은 끈다 — 글은 흘러서 폭에 맞고, 확대하면 줄이
     * 화면 밖으로 나가 가로로 밀어야 읽힌다. **흐르지 않는 것은 켠다** — 고정 레이아웃 EPUB 의 쪽, SVG, 화면보다
     * 넓게 짜였을 수 있는 쪽.
     */
    zoomable: Boolean = false,
    /**
     * 확대와 두 손가락 몸짓을 부모에게 빼앗기지 않는가([ReaderWebView.holdsGestures]). **페이저 안에서 WebView 가 확대를 맡는 쪽**
     * (그림 뷰어의 SVG)만 켠다 — 두 손가락이 닿았거나 확대된 동안 넘기기·끌어 닫기가 손가락을 가져가지 않는다. 확대한 사진이
     * 넘어가지 않는 것과 같은 약속이다.
     */
    holdsGestures: Boolean = false,
    /**
     * 두 번 두드리면 화면에 맞춘 배율의 몇 배로 키우는가 — 확대돼 있으면 맞춘 배율로 되돌린다([ReaderWebView.doubleTapZoom]).
     * null 이면 하지 않는다. **그림 뷰어의 SVG 만 준다** — 같은 뷰어의 다른 그림과 몸짓이 같아야 한다. WebView 스스로는 두 번
     * 두드림으로 확대하지 않는다(에뮬레이터 실측).
     */
    doubleTapZoom: Float? = null,
    /**
     * 그리던 엔진(렌더러 프로세스)이 죽었다 — 이 쪽은 다시 그려지지 않는다(다른 쪽으로 가거나 다시 열면 새 WebView 가 선다). 화면이
     * 까닭을 말할 수 있게 알린다. 아래 '렌더러가 죽으면' 참고.
     */
    onRenderGone: () -> Unit = {},
    /** 글자 크기(%). `WebSettings.textZoom` 이다. 바뀌면 다시 읽지 않고 그 자리에서 적용한다. */
    textZoom: Int = 100,
    /** WebView 의 바탕색(ARGB). 쪽이 그려지기 전 한 프레임이 이 색이다 — 문서의 바탕과 맞춘다. */
    backgroundColor: Int = Color.WHITE,
    /** 오른쪽에서 왼쪽으로 넘기는 책인가. 세로쓰기 장의 처음을 오른쪽 끝으로 보고, 자리를 오른쪽에서 잰다. */
    rightToLeft: Boolean = false,
    /**
     * 새 쪽을 열 때 옮겨 갈 자리(0~1)를 **그때** 묻는다. null 이면 WebView 에 맡긴다. 값이 아니라 함수인 것은, 화면이 다시
     * 만들어져(구성 변경) 새 WebView 가 열 때 **처음 연 때의 자리가 아니라 지금 자리**가 필요하기 때문이다.
     */
    startFraction: () -> Float? = { null },
    /**
     * 같은 쪽 안에서 옮겨 가라는 요청의 **일련번호**. 바뀔 때마다 [startFraction] 의 자리로 한 번 옮긴다('처음부터').
     * 값을 요청으로 쓰지 않는다 — 같은 자리를 두 번 청하면 두 번째가 사라진다(함정 표의 `MutableStateFlow`).
     */
    jump: Int = 0,
    /** 바뀌면 같은 쪽을 **다시 읽는다**(여백·바탕처럼 CSS 로 거는 모양). 보던 자리로 돌아온다. */
    contentKey: Any? = null,
    /** 사용자가 옮긴 자리(0~1). 스크롤할 때마다 오므로 받는 쪽이 모아서 쓴다. */
    onFraction: ((Float) -> Unit)? = null,
    /**
     * 화면 끝 가장자리까지 본 몫(0~1) — 진행(`progress`)에 쓴다. 한 화면에 드는 쪽은 1 이다([ReadingScroll.seenOf]).
     * 쪽이 다 읽힌 뒤에만 온다.
     */
    onSeen: ((Float) -> Unit)? = null,
    /**
     * 장을 넘기는 몸짓([ReadingTurn]). 흐르는 글은 끝에서 더 당기고([ReadingTurn.Mode.PULL]), 쪽 모양이 고정된
     * 것은 좌우로 밀거나 가장자리를 두드린다([ReadingTurn.Mode.SWIPE]). 기본은 없음 — 문서를 읽는 화면만 준다.
     */
    turn: ReadingTurn.Mode = ReadingTurn.Mode.NONE,
    /** 밀기·두드림으로 넘기는 방향이 오른쪽에서 왼쪽인가(책의 `page-progression-direction`). */
    turnRightToLeft: Boolean = false,
    /** 넘겼다 — 참이면 다음. 경계는 부르는 쪽이 거른다. */
    onTurn: ((forward: Boolean) -> Unit)? = null,
    /** 당김의 진행(−1~1). 0 이면 걷혔다. */
    onPull: ((Float) -> Unit)? = null,
) {
    // **콜백을 `remember` 의 키로 쓰지 않는다.** 람다는 재구성마다 새 객체라 키로 쓰면
    // 클라이언트가 매번 다시 만들어지고, 그때마다 `webViewClient` 가 갈린다.
    // `rememberUpdatedState` 는 객체를 그대로 두고 **안의 값만** 최신으로 바꾼다 —
    // 장 번호를 읽는 `onNavigate` 가 낡은 값을 보지 않는 것도 이 장치 덕이다.
    val latestProvider by rememberUpdatedState(provider)
    val latestNavigate by rememberUpdatedState(onNavigate)
    val latestReady by rememberUpdatedState(onReady)
    val latestFraction by rememberUpdatedState(onFraction)
    val latestSeen by rememberUpdatedState(onSeen)
    val latestGone by rememberUpdatedState(onRenderGone)
    val latestTurn by rememberUpdatedState(onTurn)
    val latestPull by rememberUpdatedState(onPull)
    // 렌더러가 죽은 쪽의 주소와, 그 뒤에 세울 새 WebView 의 번호(머리말의 '렌더러가 죽으면').
    var goneUrl by remember { mutableStateOf<String?>(null) }
    var incarnation by remember { mutableIntStateOf(0) }
    val client = remember {
        object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                // 우리 호스트가 아니면 언제나 빈 404 다.
                val path = WebHost.pathOf(request.url) ?: return notFound()
                val resource = latestProvider.open(path) ?: return notFound()
                return WebResourceResponse(
                    resource.mimeType,
                    resource.encoding,
                    resource.stream,
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val path = WebHost.pathOf(request.url)
                // **우리 호스트가 아니면 그냥 삼킨다.** 바깥 링크를 브라우저로 넘기는
                // 길도 있지만, 그것은 사용자가 고른 적 없는 앱에 문서의 내용을 건네는
                // 일이다. 눌러도 아무 일이 없는 편이 정직하다.
                if (path == null) return true
                return latestNavigate(path, request.url.fragment?.takeIf { it.isNotEmpty() })
            }

            // **처음 그려진 때** 알린다. `onPageFinished` 는 문서 전체의 배치가 끝나야 와서, 큰 문서(칸 10만 개짜리 표)는
            // 3초에 보이고도 45초 뒤에야 온다 — 그동안 '여는 중' 동그라미가 다 그려진 쪽을 덮는다. 뒤의 것은 남겨 둔다
            // (첫 그림 신호가 오지 않는 쪽 대비).
            override fun onPageCommitVisible(view: WebView, url: String) {
                (view as? ReaderWebView)?.onVisible()
                latestReady()
            }

            override fun onPageFinished(view: WebView, url: String) {
                (view as? ReaderWebView)?.onFinished()
                latestReady()
            }

            override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
                (view as? ReaderWebView)?.onScaleChanged(oldScale, newScale)
            }

            // **true 를 돌려준다** — false(기본값)면 WebView 가 앱을 끝낸다. 그 대가로 이 뷰를 떼어 내 부숴야 한다(문서의 약속).
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val reader = view as? ReaderWebView
                goneUrl = reader?.requestedUrl
                reader?.dead = true
                reader?.cancelRestore()
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                incarnation++
                latestGone()
                return true
            }
        }
    }

    // 이 쪽을 그리던 렌더러가 죽었다 — 비워 둔다. 다른 쪽으로 가면(아래 `key`) 새 WebView 가 선다.
    if (goneUrl == url) return

    key(incarnation) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                ReaderWebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // 책은 흰 종이다(어두운·세피아 바탕을 고르면 그 색). 이 한 줄이 없으면 쪽이 그려지기 전 한 프레임이
                    // 검게 번쩍인다.
                    setBackgroundColor(backgroundColor)
                    settings.textZoom = textZoom
                    this.rightToLeft = rightToLeft
                    this.holdsGestures = holdsGestures
                    this.doubleTapZoom = doubleTapZoom
                    this.turnMode = turn
                    this.turnRightToLeft = turnRightToLeft
                    appliedJump = jump
                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                    webViewClient = client
                    // 내려받기를 아예 받지 않는다. 문서 안의 링크가 파일을 저장하게 두면
                    // 그 파일의 출처가 우리가 만든 가짜 호스트가 된다.
                    setDownloadListener { _, _, _, _, _ -> }
                    lock(settings)
                    if (zoomable) {
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        // 확대 단추(+/−)는 띄우지 않는다. 손가락으로 한다.
                        settings.displayZoomControls = false
                        isHorizontalScrollBarEnabled = true
                    }
                }
            },
            update = { view ->
                view.webViewClient = client
                view.onFraction = { latestFraction?.invoke(it) }
                view.onSeen = { latestSeen?.invoke(it) }
                view.onTurn = { latestTurn?.invoke(it) }
                view.onPull = { latestPull?.invoke(it) }
                view.rightToLeft = rightToLeft
                view.holdsGestures = holdsGestures
                view.doubleTapZoom = doubleTapZoom
                view.turnMode = turn
                view.turnRightToLeft = turnRightToLeft
                view.setBackgroundColor(backgroundColor)
                if (view.settings.textZoom != textZoom) {
                    // 글자 크기가 바뀌면 글이 다시 흐른다. 보던 자리를 잡아 두고 배치가 끝난 뒤 되돌아온다.
                    view.keepPlace()
                    view.settings.textZoom = textZoom
                }
                val fragment = url.substringAfter('#', "").isNotEmpty()
                // **`view.url` 과 견주지 않는다.** 그것은 WebView 가 지금 보이는 쪽의 주소라 ① 한글·일본어 경로를 퍼센트
                // 인코딩한 모양으로 돌려주고(`七.xhtml` → `%E4%B8%83.xhtml`), ② 장 안의 앵커를 누르면 `#note3` 가 붙는다.
                // 둘 다 '다른 쪽' 으로 보여, 재구성될 때마다 같은 장을 다시 읽고 앵커를 누른 뒤에는 장의 처음으로 튈 수 있다
                // (코드로 읽은 것이고 기기에서 그 증상을 확인하지는 않았다). 우리가 마지막으로 청한 주소와 견준다.
                when {
                    view.requestedUrl != url -> {
                        view.appliedJump = jump
                        view.contentKey = contentKey
                        view.open(url, ReadingScroll.startOf(startFraction(), fragment, rightToLeft))
                    }
                    view.contentKey != contentKey -> {
                        view.contentKey = contentKey
                        view.reloadKeepingPlace()
                    }
                    view.appliedJump != jump -> {
                        view.appliedJump = jump
                        view.moveTo(ReadingScroll.startOf(startFraction(), fragment, rightToLeft) ?: 0f)
                    }
                }
            },
            onRelease = { view ->
                // **컴포지션을 떠날 때 반드시 부순다.** WebView 는 자기 스레드와 네이티브
                // 자원을 쥐고 있어서, 놓아두면 책을 여닫을 때마다 하나씩 쌓인다.
                view.cancelRestore()
                view.onFraction = null
                view.onSeen = null
                view.onTurn = null
                view.onPull = null
                // 렌더러가 죽어 이미 부순 뷰다 — 부순 WebView 를 부르면 경고만 남기고 아무 일도 하지 않는다.
                if (!view.dead) {
                    view.stopLoading()
                    // 부수는 동안에도 가로채기를 놓지 않는다 — 맨 클라이언트로 바꾸면 그 사이의 요청을 WebView 가 스스로 가져가려 든다.
                    view.webViewClient = TeardownClient
                    view.loadUrl("about:blank")
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                }
            },
        )
    }
}

private fun lock(settings: WebSettings) {
    settings.javaScriptEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.domStorageEnabled = false
    // 지금 판에서는 언제나 꺼져 있어 deprecated 지만, **끄는 것을 글로 남겨 둔다** —
    // 기본값이 판마다 달랐던 설정이고 이 줄이 사라지면 왜 안전한지가 함께 사라진다.
    @Suppress("DEPRECATION")
    settings.databaseEnabled = false
    settings.setGeolocationEnabled(false)
    settings.mediaPlaybackRequiresUserGesture = true
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.safeBrowsingEnabled = false
    // **네트워크에서 가져오지 않는다.** `INTERNET` 이 없으면 기본값도 참이지만(문서에 적힌 규칙) 선언하면 거짓으로 뒤집히므로
    // 명시한다. 가로챈 응답(우리가 내주는 쪽·그림)은 이것과 상관없이 나간다.
    // **`blockNetworkImage` 는 켜지 않는다.** 우리 URL 이 `https://` 라 WebView 가 우리가 내주는 그림도 네트워크 그림으로 세어
    // 막는다(기기에서 확인: 같은 책의 CSS 는 붙는데 `<img>` 만 깨진 아이콘으로 떴다).
    settings.blockNetworkLoads = true
    settings.loadsImagesAutomatically = true
    // 화면 폭에 맞춘다. 끄면 데스크톱 폭(980px)으로 잡아 글자가 깨알같이 작아진다.
    settings.useWideViewPort = false
    settings.loadWithOverviewMode = false
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
}

/**
 * 우리가 내주지 않는 것에 대한 답.
 *
 * **`null` 을 돌려주지 않는다.** null 은 "WebView 가 알아서 가져가라" 는 뜻이고, 그러면
 * 시도가 일어나(`blockNetworkLoads` 가 막더라도) 그동안 쪽이 멎어 보인다.
 */
internal fun notFound(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "utf-8",
    404,
    "Not Found",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)

/**
 * 부수는 동안의 클라이언트. **가로채기를 놓지 않는다** — 맨 `WebViewClient` 로 갈아 끼우면 그 짧은 사이에 쪽이 내보내는 요청이
 * 진짜 네트워크로 갈 수 있다(지금은 `INTERNET` 이 없어 OS 가 막지만 겹쳐 둔다). 그리고 렌더러가 그 사이에 죽어도 **앱을 끝내지
 * 않는다**(맨 클라이언트의 `onRenderProcessGone` 은 false 다).
 */
internal object TeardownClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = notFound()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true
}
