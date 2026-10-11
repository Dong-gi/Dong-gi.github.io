package io.github.donggi.iroiroviewer.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.webhost.LockedWebView
import io.github.donggi.iroiroviewer.webhost.ResourceProvider
import io.github.donggi.iroiroviewer.webhost.WebHost
import io.github.donggi.iroiroviewer.webhost.WebResource

/**
 * 미리보기(마크다운·HTML)를 띄우는 층. 문서 뷰어와 **같은 잠긴 WebView** 다(자바스크립트 꺼짐, 파일·콘텐츠 접근 꺼짐,
 * 우리 호스트 밖의 요청은 빈 404).
 *
 * 누를 수 있는 것은 같은 쪽 안의 `#자리` 뿐이다 — 쪽이 같으면 WebView 가 그 자리로 옮기고, 다른 곳(다른 쪽으로 가는 링크·그림을
 * 누른 것)으로는 가지 않는다: `onNavigate` 가 '우리가 처리했다' 로 답해 삼킨다. 마크다운은 바깥 링크를 애초에 링크로 쓰지 않았고,
 * HTML 은 그것을 아무 일도 하지 않는 주소(`HtmlPreview.LINK`)로 바꿔 두었다.
 *
 * 바탕은 흰색이다 — WebView 가 쪽을 그리기 전 한 프레임도 흰 종이다. HTML 은 그 위에 **쪽 자신의 바탕**을 칠한다.
 *
 * 글자 크기([textZoom], %)는 쪽을 다시 만들지 않고 WebView 가 그 자리에서 건다 — 읽던 자리가 남는다. 뷰어의 글자
 * 크기(sp)를 기본 크기(13sp)에 대한 백분율로 넘긴다. **HTML 은 손가락으로 확대된다**([PreviewKind.zoomable]).
 *
 * **바이트 그대로 내주는 쪽**(`Preview.Live` — 사용자가 '스크립트 실행' 을 골랐다)은 **새** WebView 를 그 쪽만의 호스트에서
 * 연다(`LockedWebView` 의 '스크립트를 켜면' — 아래 `key` 가 호스트로 갈라 위생을 거친 쪽의 WebView 를 다시 쓰지 않는다). 쪽의
 * 대화상자(`alert`·`confirm`·`prompt`)는 거기서 그린다.
 */
@Composable
internal fun PreviewPane(preview: TextViewModel.Preview.Ready, textZoom: Int, modifier: Modifier = Modifier) {
    val provider = remember(preview) { providerFor(preview) }
    var loading by remember(preview) { mutableStateOf(true) }
    // 그리던 엔진(WebView 의 렌더러)이 죽었다 — 그 자리가 비므로 까닭을 적는다(`LockedWebView` 의 '렌더러가 죽으면'). 앱은 살아 있다.
    var gone by remember(preview) { mutableStateOf(false) }
    val live = preview.live
    val host = live?.host ?: WebHost.HOST
    Box(modifier.fillMaxSize().background(Color.White)) {
        // 확대·스크립트 설정은 WebView 를 만들 때만 걸린다. 종류나 호스트(스크립트를 켠 쪽은 열 때마다 새 호스트다)가 바뀌면 새로 만든다 —
        // 스크립트를 켠 쪽과 끈 쪽이 한 WebView 를 나눠 쓰지 않는다.
        key(preview.kind, host) {
            LockedWebView(
                url = WebHost.urlFor(preview.pagePath, host),
                provider = provider,
                onNavigate = { path, _ -> path != preview.pagePath },
                onReady = { loading = false },
                zoomable = preview.kind.zoomable,
                onRenderGone = {
                    gone = true
                    loading = false
                },
                textZoom = textZoom,
                scripts = live != null,
                host = host,
                // 쪽의 콘솔은 디버그 기록에만. 릴리스에서는 받지도 않는다 — 받으면 쪽이 쏟아내는 줄마다 글을 짓는다.
                onConsole = if (live != null && Iro.debug) { line -> Iro.d { "미리보기 콘솔: $line" } } else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (gone) {
            Text(
                text = stringResource(R.string.text_preview_render_gone),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
    }
}

/**
 * 쪽 하나와 쪽이 번호로 가리킨 것을 내준다. 무엇을 내줄지도, 디스크의 파일을 얼마나 읽을지도 [PreviewServer] 가 정한다.
 */
private fun providerFor(p: TextViewModel.Preview.Ready): ResourceProvider = ResourceProvider { path ->
    try {
        PreviewServer.serve(p, path)?.let { s -> WebResource(s.mimeType, s.encoding, PreviewServer.open(s)) }
    } catch (t: Exception) {
        // WebView 의 다른 스레드에서 오는 호출이다. 파일이 그 사이에 사라질 수 있다 — 404 로 끝낸다.
        Iro.d { "미리보기 자원을 내주지 못했다: ${t.javaClass.simpleName}" }
        null
    }
}
