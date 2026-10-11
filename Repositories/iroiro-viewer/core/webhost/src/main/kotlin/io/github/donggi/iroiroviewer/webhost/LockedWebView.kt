package io.github.donggi.iroiroviewer.webhost

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
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
import androidx.core.net.toUri
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
 * | `javaScriptEnabled = false` | 문서는 읽는 것이지 도는 것이 아니다. `format:html` 이 스크립트를 이미 지우지만 겹쳐 막는다. **켜는 곳은 하나다** — 아래 '스크립트를 켜면' |
 * | `allowFileAccess = false` | **이것이 가장 중요하다.** 켜져 있으면 문서 안의 `file:///sdcard/…` 한 줄이 사용자의 파일을 읽는다 |
 * | `allowContentAccess = false` | `content://` 로 다른 앱의 프로바이더를 훑는 길을 막는다 |
 * | `allowFileAccessFromFileURLs` · `allowUniversalAccessFromFileURLs` | 위와 짝이다. 둘 다 기본값이 판마다 달라 **명시한다** |
 * | `domStorageEnabled = false` · `databaseEnabled = false` | 문서가 기기에 무언가 남길 이유가 없다 |
 * | `setGeolocationEnabled(false)` | 같은 이유다 |
 * | `mediaPlaybackRequiresUserGesture = true` | 열자마자 소리가 나지 않게 한다 |
 * | `cacheMode = LOAD_NO_CACHE` | 사용자의 책 내용이 WebView 캐시로 디스크에 복제되지 않게 한다. 9단계가 만화 쪽을 디스크에 쓰지 않기로 한 그 판단이다 |
 * | `safeBrowsingEnabled = false` | 안전 브라우징은 **URL 을 구글에 보내는 기능**이다. 우리 문서는 기기 안의 파일이고 그 이름이 밖으로 나갈 이유가 없다 |
 * | `blockNetworkLoads = true` | **명시한다.** `INTERNET` 이 없으면 기본값도 참이지만, 이 앱이 `INTERNET` 을 선언했던 동안(2026-10-05 ~ 10-09, SFTP) 기본값이 거짓으로 뒤집혔다 — 다시 선언하는 날에도 이 줄이 막는다. 우리가 내주는 응답(가로챈 것)은 그대로 나간다 |
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
 *
 * ## 렌더러가 죽으면
 *
 * WebView 는 그리는 일을 따로 떨어진 렌더러 프로세스에 맡기고, 이 앱의 WebView 는 그것 하나를 나눠 쓴다. 그것이 죽으면(문서가
 * 렌더러를 무너뜨렸거나, 시스템이 메모리를 찾아 죽였다) 떠 있는 WebView 가 모두 `onRenderProcessGone` 을 받는데, **처리하지 않으면
 * WebView 가 앱을 함께 끝낸다**(기본 구현이 false 를 돌려준다). 11단계부터 그 처리가 없었다 — 저장소의 SVG 하나를 그림 뷰어가 미리
 * 세우는 것만으로도 앱이 죽을 수 있게 되어 붙였다. 죽은 WebView 는 다시 쓸 수 없으므로 떼어 내 부수고, 그 쪽은 비워
 * 둔 채 [onRenderGone] 으로 알린다. **다른 쪽([url]이 바뀐다)으로 가면 새 WebView 가 선다** — 새 렌더러로 다시 그린다. 같은 쪽을 다시
 * 세우지는 않는다 — 문서가 무너뜨린 것이면 되풀이될 뿐이다.
 *
 * ## 스크립트를 켜면([scripts])
 *
 * 메일에 붙어 오는 보안 메일은 암호를 받아 **스크립트가** 내용을 풀어 보여 준다 — 스크립트를 끄면 내용이 없다. 그래서 HTML
 * 미리보기에서 사용자가 고르면(쪽마다, 저장하지 않는다) 스크립트를 켠다. 켜도 그대로인 것과 더하는 것:
 *
 * | | |
 * |---|---|
 * | 그대로 | 파일·콘텐츠 접근 꺼짐, DOM 저장소·위치 꺼짐, 캐시 없음, 내려받기 삼킴, **우리 호스트가 아닌 요청은 빈 404**, 다른 쪽으로 가는 길은 [onNavigate] |
 * | 출처 | 쪽마다 새로 지은 호스트([host] — `WebHost.isolatedHost`). 쪽이 남긴 저장소·서비스 워커를 읽을 쪽이 다시 오지 않는다. 잠긴 쪽의 호스트(`book.iroiro.invalid`)로 가는 요청도 404 다 |
 * | 쿠키·서비스 워커 | 쿠키를 받지 않고, 서비스 워커의 요청은 모두 404(`ScriptPage.lockOnce`). 닫으면 출처별 저장소를 지운다(`ScriptPage.wipe`) |
 * | 새 창 | 열지 않는다(`setSupportMultipleWindows(false)` — `window.open` 은 이 창의 이동이 되고 [onNavigate] 가 삼킨다) |
 * | 이동 | GET 은 [onNavigate] 가 삼키고, **POST(폼)는 `shouldOverrideUrlLoading` 을 지나지 않아** 가로채기가 204 로 거둔다 — 쪽에 머문다(`ScriptPage.refusesNavigation`) |
 * | 전체 화면·자동 완성 | 전체 화면은 들어주지 않는다. 쪽의 입력칸에 안드로이드 자동 완성(저장된 카드·암호)을 내밀지 않는다 |
 * | 닫을 때 | **렌더러를 끝낸다** — 멈추지 않는 스크립트가 렌더러를 붙들면 그 뒤의 모든 WebView 가 비고, 풀린 글이 렌더러의 메모리에 남는다. 그리고 출처별 저장소를 지운다. 부수는 동안에도 가로채기를 놓지 않는다(`TeardownClient`) |
 * | 대화상자 | `alert`·`confirm`·`prompt` 를 우리가 그린다(`JsDialogView`). 둘째부터 '더 띄우지 않게' 가 붙는다(`JsDialogGate`) |
 * | 콘솔 | [onConsole] 로만 — 받지 않으면 WebView 가 logcat 에 스스로 적는다고 알려져 있다(확인하지 않았다) |
 *
 * 가로채기(`shouldInterceptRequest`)는 HTTP 요청만 본다 — 웹소켓·WebRTC·이름 미리 찾기(DNS prefetch)는 그것을 지나지 않고, 스크립트를
 * 켠 쪽은 같은 폴더의 파일을 읽을 수 있다. **그것이 밖으로 나가지 않는 것은 이 앱에 `INTERNET` 이 없기 때문이다**(OS 가 앱의 소켓·이름
 * 찾기를 막는다). 이 앱이 `INTERNET` 을 선언했던 동안(2026-10-05 ~ 10-09) 응답 머리의 CSP([ScriptPage.OFFLINE_HEADERS])가 웹소켓·
 * `fetch` 를 막았지만 이름 미리 찾기와 WebRTC 는 막지 못했다(실험실) — **`INTERNET` 을 다시 선언하는 날 이 모드부터 다시 본다.**
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
     * 장의 각주·책갈피로 가는 링크가 그 장의 맨 위에 떨어진다.
     */
    onNavigate: (path: String, fragment: String?) -> Boolean,
    modifier: Modifier = Modifier,
    /** 쪽을 다 그렸다. 화면이 '여는 중' 을 지우는 데 쓴다. */
    onReady: () -> Unit = {},
    /**
     * 손가락으로 확대할 수 있는가. 흐르는 글(EPUB·마크다운)은 끈다 — 글은 흘러서 폭에 맞고, 확대하면 줄이
     * 화면 밖으로 나가 가로로 밀어야 읽힌다. **흐르지 않는 것은 켠다** — 고정 레이아웃 EPUB 의 쪽, SVG, 화면보다
     * 넓게 짜였을 수 있는 HTML 쪽.
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
     * 쪽의 스크립트를 돌리는가(위 '스크립트를 켜면'). **HTML 미리보기의 '스크립트 실행' 만 켠다.** [zoomable] 처럼 WebView 를 만들 때만
     * 걸린다 — 바꾸려면 부르는 쪽이 `key` 로 새로 세운다.
     */
    scripts: Boolean = false,
    /** 이 쪽의 호스트. 스크립트를 켠 쪽은 `WebHost.isolatedHost` 가 지은 것을 준다. [url] 도 이 호스트여야 한다. */
    host: String = WebHost.HOST,
    /** 쪽의 콘솔 한 줄(디버그 기록). 스크립트를 켠 쪽만 온다. */
    onConsole: ((String) -> Unit)? = null,
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
    val latestHost by rememberUpdatedState(host)
    val latestConsole by rememberUpdatedState(onConsole)
    val latestScripts by rememberUpdatedState(scripts)
    val latestTurn by rememberUpdatedState(onTurn)
    val latestPull by rememberUpdatedState(onPull)
    // 지금 쪽의 경로. 스크립트를 켠 쪽이 쪽 전체를 다른 곳으로 바꾸려는 요청을 가르는 데 쓴다(`ScriptPage.refusesNavigation`).
    val latestPagePath by rememberUpdatedState(remember(url, host) { WebHost.pathOf(url.toUri(), host) })
    // 렌더러가 죽은 쪽의 주소와, 그 뒤에 세울 새 WebView 의 번호(머리말의 '렌더러가 죽으면').
    var goneUrl by remember { mutableStateOf<String?>(null) }
    var incarnation by remember { mutableIntStateOf(0) }
    // 우리가 끝낸 렌더러에 함께 쓰러져 한 번 다시 세웠는가(아래 `onRenderProcessGone`). 한 번뿐이다 — 되풀이되면 그대로 비운다.
    var revived by remember { mutableStateOf(false) }
    // 스크립트를 켠 쪽이 띄운 대화상자(머리말의 '스크립트를 켜면'). 답하기 전까지 쪽의 스크립트가 멈춰 있다.
    var jsDialog by remember { mutableStateOf<JsDialog?>(null) }
    val dialogGate = remember { JsDialogGate() }
    val chrome = remember {
        ScriptChrome(
            gate = dialogGate,
            show = { d ->
                // 답하지 않은 것이 남아 있으면 거절한다 — 하나가 답을 기다리는 동안 쪽은 멈춰 있어 겹칠 일이 드물지만, 남겨 두면 그
                // 쪽의 스크립트가 영영 깨어나지 않는다.
                jsDialog?.result?.cancel()
                jsDialog = d
            },
            console = { latestConsole },
        )
    }
    val client = remember {
        object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                // 바이트 그대로 내주는 쪽(스크립트를 켰다)만 아래 둘이 필요하다 — 위생을 거친 쪽에는 폼도 스크립트도 없다.
                val raw = latestScripts
                // 서비스 워커가 받아 오려는 스크립트에는 아무것도 주지 않는다(스크립트를 켠 쪽만 그런 요청을 낸다).
                if (raw && ScriptPage.isServiceWorkerFetch(request)) return notFound()
                val path = WebHost.pathOf(request.url, latestHost)
                // 쪽 전체를 다른 곳으로 — POST 는 `shouldOverrideUrlLoading` 을 지나지 않으므로 여기서 거둔다(204, 쪽에 머문다).
                if (raw && ScriptPage.refusesNavigation(request.isForMainFrame, path, latestPagePath)) {
                    return ScriptPage.noContent()
                }
                // 우리 호스트가 아니면 언제나 빈 404 다.
                if (path == null) return notFound()
                val resource = latestProvider.open(path) ?: return notFound()
                // 스크립트를 켠 쪽은 응답마다 머리를 붙인다(`ScriptPage.OFFLINE_HEADERS` — 웹소켓 등. 겹쳐 둔 막음이다).
                if (latestScripts) {
                    return WebResourceResponse(
                        resource.mimeType,
                        resource.encoding,
                        200,
                        "OK",
                        ScriptPage.OFFLINE_HEADERS,
                        resource.stream,
                    )
                }
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
                val path = WebHost.pathOf(request.url, latestHost)
                // **우리 호스트가 아니면 그냥 삼킨다.** 바깥 링크를 브라우저로 넘기는
                // 길도 있지만, 그것은 사용자가 고른 적 없는 앱에 문서의 내용을 건네는
                // 일이다. 눌러도 아무 일이 없는 편이 정직하다.
                if (path == null) return true
                return latestNavigate(path, request.url.fragment?.takeIf { it.isNotEmpty() })
            }

            // **처음 그려진 때** 알린다. `onPageFinished` 는 문서 전체의 배치가 끝나야 오는데, 큰 시트는
            // 표가 3초에 보이고도 45초 뒤에야 그것이 왔다(걷은 시트 뷰어에서 잰 것 — 태블릿 에뮬레이터) — 그동안 '여는 중'
            // 동그라미가 다 그려진 표를 덮고 있었다. 뒤의 것은 남겨 둔다(첫 그림 신호가 오지 않는 쪽 대비).
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
                // **우리가 끝낸 렌더러에 함께 쓰러진 쪽이면 한 번 다시 세운다.** 스크립트를 켠 쪽을 닫을 때 렌더러를 끝내는데(아래
                // `onRelease`), 같은 때에 세운 다음 쪽(예: '멈추기' 로 다시 세운 위생을 거친 쪽)이 그 렌더러에 붙어 있으면 함께 죽는다 —
                // 실험실에서 '그리던 엔진이 멈췄습니다' 로 잡혔다(2026-10-05). 문서가 무너뜨린 것(`didCrash`)이나 시스템이 죽인 것은 그대로 비운다.
                val ours = !detail.didCrash() && ScriptPage.terminatedRecently() && !revived
                if (!ours) goneUrl = reader?.requestedUrl
                reader?.dead = true
                reader?.cancelRestore()
                // 답을 기다리던 대화상자는 쪽과 함께 사라졌다.
                jsDialog = null
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                if (latestScripts) ScriptPage.wipe()
                incarnation++
                if (ours) revived = true else latestGone()
                return true
            }
        }
    }

    // 이 쪽을 그리던 렌더러가 죽었다 — 비워 둔다. 다른 쪽으로 가면(아래 `key`) 새 WebView 가 선다.
    if (goneUrl == url) return

    jsDialog?.let { d ->
        JsDialogView(d, onStop = dialogGate::stop, onDone = { if (jsDialog === d) jsDialog = null })
    }

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
                    if (scripts) {
                        // 머리말의 '스크립트를 켜면'. 나머지 잠금은 그대로다.
                        ScriptPage.lockOnce()
                        settings.javaScriptEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        webChromeClient = chrome
                        // 안드로이드 자동 완성이 쪽의 입력칸에 저장된 카드·암호를 내밀지 않게 한다 — 문서가 그 칸을 무엇으로든 꾸밀 수 있다.
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    }
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
                view.onTurn = null
                view.onPull = null
                // 답을 기다리던 대화상자에 답한다 — 쪽이 사라지므로 '취소' 다.
                jsDialog?.result?.cancel()
                jsDialog = null
                // 렌더러가 죽어 이미 부순 뷰다 — 부순 WebView 를 부르면 경고만 남기고 아무 일도 하지 않는다.
                if (!view.dead) {
                    // 스크립트를 켠 쪽의 렌더러는 부순 뒤에 **끝낸다.** 멈추지 않는 스크립트(`while(true){}`)는 렌더러의 주 스레드를 붙들고,
                    // 이 앱의 WebView 는 렌더러 하나를 나눠 쓰므로 그 뒤에 세우는 모든 WebView(위생을 거친 쪽·책·SVG)가 비어 있게 된다.
                    // 끝내면 풀린 메일이 렌더러의 메모리에 남지도 않는다. 손잡이는 부수기 전에 쥔다(API 29).
                    val renderer = if (scripts) view.webViewRenderProcess else null
                    view.stopLoading()
                    view.webViewClient = TeardownClient
                    view.webChromeClient = null
                    view.loadUrl("about:blank")
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    if (renderer != null) {
                        ScriptPage.markTerminated()
                        renderer.terminate()
                    }
                    if (scripts) ScriptPage.wipe()
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
    // **네트워크에서 가져오지 않는다.** `INTERNET` 이 없으면 기본값도 참이지만(문서에 적힌 규칙) 명시한다 — 이 앱이 `INTERNET` 을
    // 선언했던 동안(2026-10-05 ~ 10-09) 기본값이 거짓으로 뒤집혔다. 가로챈 응답(우리가 내주는 쪽·그림)은 이것과 상관없이 나간다
    // — 켜진 채로 책의 그림이 떠 왔다.
    // **`blockNetworkImage` 는 켜지 않는다.** 겹겹 방어로 켰더니 우리가 내주는 그림이 함께 막혔다(기기에서 확인: 같은 책의 CSS 는
    // 붙는데 `<img>` 만 깨진 아이콘으로 떴다 — 그때는 두 설정을 함께 켰고, 탓을 `blockNetworkLoads` 에도 돌렸다. 그것은 처음부터
    // 켜져 있었다). 우리 URL 이 `https://` 라 WebView 가 그것을 네트워크 그림으로 센다.
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
