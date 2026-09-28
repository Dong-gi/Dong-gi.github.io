package io.github.donggi.iroiroviewer.webhost

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * | `javaScriptEnabled = false` | 문서는 읽는 것이지 도는 것이 아니다. `format:html` 이 스크립트를 이미 지우지만 겹쳐 막는다 |
 * | `allowFileAccess = false` | **이것이 가장 중요하다.** 켜져 있으면 문서 안의 `file:///sdcard/…` 한 줄이 사용자의 파일을 읽는다 |
 * | `allowContentAccess = false` | `content://` 로 다른 앱의 프로바이더를 훑는 길을 막는다 |
 * | `allowFileAccessFromFileURLs` · `allowUniversalAccessFromFileURLs` | 위와 짝이다. 둘 다 기본값이 판마다 달라 **명시한다** |
 * | `domStorageEnabled = false` · `databaseEnabled = false` | 문서가 기기에 무언가 남길 이유가 없다 |
 * | `setGeolocationEnabled(false)` | 같은 이유다 |
 * | `mediaPlaybackRequiresUserGesture = true` | 열자마자 소리가 나지 않게 한다 |
 * | `cacheMode = LOAD_NO_CACHE` | 사용자의 책 내용이 WebView 캐시로 디스크에 복제되지 않게 한다. 9단계가 만화 쪽을 디스크에 쓰지 않기로 한 그 판단이다 |
 * | `safeBrowsingEnabled = false` | 안전 브라우징은 **URL 을 구글에 보내는 기능**이다. 우리 문서는 기기 안의 파일이고 그 이름이 밖으로 나갈 이유가 없다 |
 *
 * **`blockNetworkLoads` 는 켜지 않는다** — 켜면 우리가 내주는 그림까지 막힌다(아래 주석).
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
 * ## 읽는 모양과 읽던 자리(14단계)
 *
 * 글자 크기는 **`WebSettings.textZoom`** 으로 건다 — 책의 CSS 가 글자 크기를 몇 겹으로 적어 두어도 그 위에서 곱해지므로
 * 책의 조판과 다투지 않고, 쪽을 다시 읽지 않는다. 여백·바탕은 CSS 라 문서를 다시 읽어야 한다([contentKey]). 둘 다 바뀌면
 * 글이 다시 흘러 같은 화소가 다른 곳을 가리키므로 **보던 자리(비율)로 돌아온다**([ReaderWebView]).
 *
 * 스크립트가 꺼져 있어 자리는 Kotlin 이 잰다 — 스크롤 범위와 위치를 비율로 바꾼다([ReadingScroll]).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LockedWebView(
    url: String,
    provider: ResourceProvider,
    /**
     * 우리 호스트 안의 **다른 쪽**으로 가려 한다(장 사이 링크).
     * `true` 를 주면 우리가 처리한 것으로 보고 WebView 는 움직이지 않는다.
     *
     * [fragment] 는 링크의 조각(`#` 뒤, 없으면 null)이다. **함께 건넨다** — 경로만 받으면 다른
     * 장의 각주·책갈피로 가는 링크가 그 장의 맨 위에 떨어진다(12단계 검토가 잡았다).
     */
    onNavigate: (path: String, fragment: String?) -> Boolean,
    modifier: Modifier = Modifier,
    /** 쪽을 다 그렸다. 화면이 '여는 중' 을 지우는 데 쓴다. */
    onReady: () -> Unit = {},
    /**
     * 손가락으로 확대할 수 있는가. 글(EPUB·docx)은 끈다 — 글은 흘러서 폭에 맞고, 확대하면 줄이
     * 화면 밖으로 나가 가로로 밀어야 읽힌다. **시트와 슬라이드는 켠다** — 셀의 작은 글자와
     * 슬라이드의 그림은 흐르지 않는다.
     */
    zoomable: Boolean = false,
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
    val client = remember {
        object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse {
                val path = WebHost.pathOf(request.url)
                val resource = if (path == null) null else latestProvider.open(path)
                if (resource == null) return notFound()
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

            // **처음 그려진 때** 알린다. `onPageFinished` 는 문서 전체의 배치가 끝나야 오는데, 큰 시트는
            // 표가 3초에 보이고도 45초 뒤에야 그것이 왔다(12단계 태블릿 에뮬레이터 실측) — 그동안 '여는 중'
            // 동그라미가 다 그려진 표를 덮고 있었다. 뒤의 것은 남겨 둔다(첫 그림 신호가 오지 않는 쪽 대비).
            override fun onPageCommitVisible(view: WebView, url: String) {
                (view as? ReaderWebView)?.onVisible()
                latestReady()
            }

            override fun onPageFinished(view: WebView, url: String) {
                (view as? ReaderWebView)?.onFinished()
                latestReady()
            }
        }
    }

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
            view.rightToLeft = rightToLeft
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
            // (14단계에 코드를 읽어 본 것이다 — 기기에서 그 증상을 확인하지는 않았다). 우리가 마지막으로 청한 주소와 견준다.
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
            view.stopLoading()
            view.webViewClient = WebViewClient()
            view.loadUrl("about:blank")
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        },
    )
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
    // **`blockNetworkLoads`·`blockNetworkImage` 는 켜지 않는다.** 겹겹 방어로 켰더니
    // **우리가 내주는 그림이 함께 막혔다**(기기에서 확인: 같은 책의 CSS 는 붙는데
    // `<img>` 만 깨진 아이콘으로 떴다). 우리 URL 이 `https://` 라 WebView 가 그것을
    // 네트워크 이미지로 세고, 그 검사가 `shouldInterceptRequest` 보다 앞에 있다.
    // 막는 일은 다른 두 겹이 이미 한다 — `INTERNET` 미선언(OS 수준)과, 우리 호스트가
    // 아닌 요청에 빈 404 를 돌려주는 가로채기.
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
 * `INTERNET` 이 없어도 시도는 일어나 그동안 쪽이 멎어 보인다.
 */
private fun notFound(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "utf-8",
    404,
    "Not Found",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)
