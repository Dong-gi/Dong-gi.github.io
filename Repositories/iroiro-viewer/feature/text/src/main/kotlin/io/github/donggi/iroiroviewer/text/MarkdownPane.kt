package io.github.donggi.iroiroviewer.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.donggi.iroiroviewer.format.text.MarkdownPreview
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.webhost.LockedWebView
import io.github.donggi.iroiroviewer.webhost.ResourceProvider
import io.github.donggi.iroiroviewer.webhost.WebHost
import io.github.donggi.iroiroviewer.webhost.WebResource
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * 마크다운 미리보기를 띄우는 층. 문서 뷰어와 **같은 잠긴 WebView** 다(자바스크립트 꺼짐, 파일·콘텐츠 접근 꺼짐,
 * 우리 호스트 밖의 요청은 빈 404).
 *
 * 누를 수 있는 것은 같은 쪽 안의 `#자리` 뿐이다 — 쪽이 같으면 WebView 가 그 자리로 옮기고, 다른 곳(그림을 누른
 * 것 등)으로는 가지 않는다. 바깥 링크는 변환기가 애초에 링크로 쓰지 않았다.
 *
 * 글자 크기([textZoom], %)는 쪽을 다시 만들지 않고 WebView 가 그 자리에서 건다 — 읽던 자리가 남는다. 뷰어의 글자
 * 크기(sp)를 기본 크기(13sp)에 대한 백분율로 넘긴다.
 */
@Composable
internal fun MarkdownPane(preview: TextViewModel.Preview.Ready, textZoom: Int, modifier: Modifier = Modifier) {
    val provider = remember(preview) { providerFor(preview) }
    var loading by remember(preview) { mutableStateOf(true) }
    Box(modifier.fillMaxSize().background(Color.White)) {
        LockedWebView(
            url = WebHost.urlFor(preview.pagePath),
            provider = provider,
            onNavigate = { path, _ -> path != preview.pagePath },
            onReady = { loading = false },
            textZoom = textZoom,
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 쪽 하나와 그림들을 내준다. 그림은 **번호로만** 온다(`img/3`) — 번호가 가리키는 상대 경로는 변환기가 만든 표에
 * 있고, 실제 파일은 [MarkdownImages.resolve] 가 폴더 밖이 아닌지 다시 보고 고른다.
 */
private fun providerFor(p: TextViewModel.Preview.Ready): ResourceProvider = ResourceProvider { path ->
    try {
        if (path == p.pagePath) {
            WebResource("text/html", "utf-8", ByteArrayInputStream(p.html))
        } else {
            val index = MarkdownPreview.imageIndexOf(path) ?: return@ResourceProvider null
            val rel = p.images.getOrNull(index) ?: return@ResourceProvider null
            val file = MarkdownImages.resolve(p.baseDir, rel) ?: return@ResourceProvider null
            val type = MarkdownImages.mimeOf(file.name) ?: return@ResourceProvider null
            WebResource(type, null, FileInputStream(file))
        }
    } catch (t: Exception) {
        // WebView 의 다른 스레드에서 오는 호출이다. 파일이 그 사이에 사라질 수 있다 — 404 로 끝낸다.
        Iro.d { "마크다운 자원을 내주지 못했다: ${t.javaClass.simpleName}" }
        null
    }
}
