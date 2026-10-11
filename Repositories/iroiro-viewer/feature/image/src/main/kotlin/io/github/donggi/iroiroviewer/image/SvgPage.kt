package io.github.donggi.iroiroviewer.image

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import io.github.donggi.iroiroviewer.webhost.LockedWebView
import io.github.donggi.iroiroviewer.webhost.ResourceProvider
import io.github.donggi.iroiroviewer.webhost.WebHost
import io.github.donggi.iroiroviewer.webhost.WebResource
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * SVG 한 장. 잠긴 WebView 가 우리가 쓴 쪽([SvgSupport.pageHtml])에 SVG 를 `<img>` 로 실어 그린다 — 까닭은 [SvgSupport] 의 머리말.
 *
 * ## 손가락
 *
 * * **확대·이동은 WebView 가 한다**(`zoomable`). 벡터라 키울수록 다시 그려져 선명하다.
 * * **넘기기·끌어 닫기는 페이저와 그림 뷰어가 한다.** Compose 의 `AndroidView` 는 움직임을 부모가 먼저 보게 하고(뷰가 가로채기를
 *   막아 달라고 하지 않은 동안), 부모가 끌기를 시작하면 WebView 에 취소를 보낸다 — 화면에 맞춘 그림은 페이저가 넘긴다.
 *   **두 손가락이 닿았거나 확대된 동안은 WebView 가 가로채기를 막는다**(`holdsGestures`). 막지 않았더니 페이저가 손가락 하나의 가로
 *   움직임을 넘기기로 읽어 핀치를 중간에 끊었다(에뮬레이터 실측 — 가로 핀치가 거의 확대되지 않고 세로 핀치만 됐다).
 * * **두드리면 막대를 여닫는다.** WebView 가 눌림을 받아 쓰므로 부모의 두드림 인식기(소비되지 않은 눌림만 받는다)가 못 본다 — 그래서
 *   **소비하지 않고 엿보는** 인식기를 단다([observeTaps]).
 * * **두 번 두드리면 키운다** — 다른 그림과 같은 2.5배, 다시 두 번 두드리면 맞춤(`doubleTapZoom`). WebView 스스로는 두 번
 *   두드림으로 확대하지 않아(에뮬레이터 실측) 처음에는 **아무 일도 없었다** — 막대도 그대로였다(두 번째 눌림이 한 번 두드림을
 *   지운다). 두 번 두드림은 막대를 건드리지 않는다.
 */
@Composable
internal fun SvgPage(
    prepared: SvgSupport.Prepared,
    generation: Int,
    onTap: () -> Unit,
    /** 그리던 엔진(렌더러)이 죽었다 — 이 장은 다시 그려지지 않는다(`LockedWebView` 의 `onRenderGone`). */
    onRenderGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 파일이 바뀌면(세대) 주소가 달라져야 WebView 가 다시 읽는다 — 같은 주소면 다시 불러오지 않는다.
    val pagePath = "g$generation/page.html"
    val imagePath = "g$generation/$IMAGE_NAME"
    val html = remember(prepared, generation) {
        SvgSupport.pageHtml(IMAGE_NAME, prepared.root.aspect).toByteArray(Charsets.UTF_8)
    }
    val provider = remember(prepared, generation) {
        ResourceProvider { path ->
            try {
                when (path) {
                    pagePath -> WebResource("text/html", "utf-8", ByteArrayInputStream(html))
                    // 형식은 우리가 정한다(함정 표의 `rawResourceType`). 내용은 `<img>` 로만 실린다.
                    imagePath -> WebResource("image/svg+xml", null, prepared.open())
                    else -> null
                }
            } catch (e: IOException) {
                // WebView 의 다른 스레드다. 파일이 그새 사라지면 404 로 끝낸다(깨진 그림 표시).
                Iro.d { "SVG 를 내주지 못했다: ${e.javaClass.simpleName}" }
                null
            }
        }
    }
    var loading by remember(prepared, generation) { mutableStateOf(true) }
    val tapNow by rememberUpdatedState(onTap)
    Box(modifier.fillMaxSize().pointerInput(Unit) { observeTaps { tapNow() } }) {
        LockedWebView(
            url = WebHost.urlFor(pagePath),
            provider = provider,
            // 우리 쪽에는 링크가 없다. 어떤 길도 열지 않는다.
            onNavigate = { _, _ -> true },
            onReady = { loading = false },
            zoomable = true,
            // 두 손가락이 닿았거나 확대된 동안 페이저가 손가락을 가져가지 않는다 — 확대한 사진이 넘어가지 않는 것과 같은 약속이다.
            holdsGestures = true,
            doubleTapZoom = ZoomMath.DOUBLE_TAP_SCALE,
            onRenderGone = onRenderGone,
            backgroundColor = android.graphics.Color.BLACK,
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}

private const val IMAGE_NAME = "image.svg"

/**
 * 한 번 두드림을 **소비하지 않고** 엿본다. 모든 눌림을 `Initial` 단계에서 보고 아무것도 소비하지 않으므로 WebView(확대·이동)와
 * 부모(넘기기·끌어 닫기)가 그대로 받는다. 움직였거나 손가락이 둘이었거나 오래 눌렀으면 두드림이 아니고, 곧바로 두 번째 눌림이
 * 오면 두 번 두드림(WebView 의 확대)이라 부르지 않는다.
 */
private suspend fun PointerInputScope.observeTaps(onTap: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var tap = true
        var upAt = -1L
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } > 1) tap = false
            val c = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((c.position - down.position).getDistance() > slop) tap = false
            if (!c.pressed) {
                upAt = c.uptimeMillis
                break
            }
        }
        if (!tap || upAt < 0 || upAt - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        }
        if (second == null) onTap()
    }
}
