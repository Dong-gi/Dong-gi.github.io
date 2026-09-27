package io.github.donggi.iroiroviewer.docview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.webhost.LockedWebView
import io.github.donggi.iroiroviewer.webhost.ResourceProvider
import io.github.donggi.iroiroviewer.webhost.WebHost
import io.github.donggi.iroiroviewer.webhost.WebResource
import java.io.ByteArrayInputStream

/**
 * 흐름 문서(docx·xlsx·pptx — 12단계, HWPX — 13단계)를 읽는 층. `EpubReader` 와 같은 모양이다.
 *
 * ## 화면은 포맷을 모른다
 *
 * 이 층이 아는 것은 `FlowDocument` 계약 하나다(`format:api`). 시트를 그리든 슬라이드를 그리든
 * 부분 하나의 HTML 을 받아 띄울 뿐이다 — 그래서 13단계의 HWPX 가 이 화면을 고치지 않고 붙는다.
 *
 * ## 부분 하나를 통째로 띄운다
 *
 * 부분 안의 스크롤은 WebView 가 한다(시트는 가로로도 민다). 부분을 넘기는 것은 아래 막대와
 * 목차다. 목차의 자리(`anchor`)는 URL 의 조각(`#id`)으로 건넨다 — 같은 부분이면 WebView 가
 * 그 자리로 옮기기만 한다.
 */
@Composable
fun FlowReader(
    doc: DocViewModel.Doc.Flow,
    part: Int,
    /** 목차나 부분 사이 링크가 가리킨 자리. 부분이 같으면 WebView 가 그 자리로 간다. */
    anchor: String?,
    /** 다른 부분으로 간다. [anchor] 는 링크의 조각이다(없으면 null — 부분의 처음). */
    onPart: (part: Int, anchor: String?) -> Unit,
    /** 부분 하나를 그렸다. 버린 것의 집계가 늘었을 수 있다. */
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = doc.flow
    val latestRead by rememberUpdatedState(onRead)
    val provider = remember(flow) { providerFor(flow) { latestRead() } }
    var loading by remember(flow, part) { mutableStateOf(true) }

    val path = flow.parts.getOrNull(part)?.path
    Box(modifier.fillMaxSize().background(Color.White)) {
        if (path != null) {
            val url = WebHost.urlFor(path) + (anchor?.let { "#$it" } ?: "")
            LockedWebView(
                url = url,
                provider = provider,
                onNavigate = { target, fragment ->
                    val index = flow.partIndexOf(target)
                    when {
                        index == part -> false
                        index >= 0 -> {
                            onPart(index, fragment)
                            true
                        }
                        // 부분이 아닌 곳(그림을 누른 것 등)으로는 가지 않는다.
                        else -> true
                    }
                },
                onReady = { loading = false },
                zoomable = flow.kind != FlowKind.DOCUMENT,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 부분을 넘기는 줄. 부분이 하나(짧은 docx)면 이전·다음을 감추고 목차만 둔다.
 */
@Composable
fun FlowBottomBar(
    doc: DocViewModel.Doc.Flow,
    part: Int,
    onPart: (Int) -> Unit,
    onOutline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = doc.count
    val label = doc.flow.parts.getOrNull(part)?.label.orEmpty()
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(enabled = part > 0, onClick = { onPart(part - 1) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.doc_previous_part),
                tint = if (part > 0) Color.White else Color.White.copy(alpha = 0.3f),
            )
        }
        TextButton(onClick = onOutline, modifier = Modifier.weight(1f)) {
            Text(
                text = partCaption(doc.flow.kind, part, count, label),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        IconButton(enabled = part < count - 1, onClick = { onPart(part + 1) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.doc_next_part),
                tint = if (part < count - 1) Color.White else Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

/** 아래 막대의 글. 시트는 이름을 함께 보인다 — 시트는 번호보다 이름으로 찾는다. */
@Composable
private fun partCaption(kind: FlowKind, part: Int, count: Int, label: String): String = when (kind) {
    FlowKind.SHEETS -> if (label.isNotBlank()) stringResource(R.string.doc_sheet_named, part + 1, count, label)
    else stringResource(R.string.doc_sheet_of, part + 1, count)
    FlowKind.SLIDES -> stringResource(R.string.doc_slide_of, part + 1, count)
    FlowKind.DOCUMENT -> if (count > 1) stringResource(R.string.doc_part_of, part + 1, count)
    else stringResource(R.string.doc_outline)
}

/** 목차. 제목·시트·슬라이드. 평평한 목록에 깊이만 들여쓴다(`EpubTocList` 와 같다). */
@Composable
fun FlowOutlineList(
    flow: FlowDocument,
    current: Int,
    onPick: (part: Int, anchor: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (flow.outline.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.doc_no_toc))
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        items(flow.outline.size) { i ->
            val entry = flow.outline[i]
            Text(
                text = entry.title.ifBlank { untitled(flow.kind, entry.partIndex, flow.parts.size) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.partIndex == current && entry.anchor == null) FontWeight.Bold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry.partIndex, entry.anchor) }
                    .padding(
                        start = 24.dp + (entry.depth.coerceIn(0, 4) * 16).dp,
                        end = 24.dp,
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
            )
        }
    }
}

/**
 * 제목이 없는 목차 줄. 슬라이드는 제목 없는 것이 흔해서(그림 한 장짜리) '이름 없는 장' 으로
 * 채우면 목차가 같은 말로 도배된다 — 번호가 유일한 표지다.
 */
@Composable
private fun untitled(kind: FlowKind, part: Int, count: Int): String = when (kind) {
    FlowKind.SLIDES -> stringResource(R.string.doc_slide_of, part + 1, count)
    FlowKind.SHEETS -> stringResource(R.string.doc_sheet_of, part + 1, count)
    FlowKind.DOCUMENT -> stringResource(R.string.doc_untitled_chapter)
}

/**
 * 문서 안의 것을 내주는 이음매. 부분은 위생을 거친 HTML, 그 밖은 그림 바이트다
 * (`FlowDocument.openResource` 가 화면이 그릴 수 있는 그림만 준다).
 */
private fun providerFor(flow: FlowDocument, onRead: () -> Unit): ResourceProvider = ResourceProvider { path ->
    try {
        val index = flow.partIndexOf(path)
        if (index >= 0) {
            val html = flow.partHtml(index) ?: return@ResourceProvider null
            onRead()
            WebResource("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
        } else {
            val stream = flow.openResource(path) ?: return@ResourceProvider null
            val type = flow.mediaTypeOf(path) ?: guessImageType(path)
            WebResource(type, null, stream)
        }
    } catch (t: Throwable) {
        // 다른 스레드에서 오는 호출이라 문서가 닫히는 순간과 겹칠 수 있다. 404 로 끝낸다.
        Iro.d(TAG) { "흐름 문서 자원을 내주지 못했다: ${t::class.java.simpleName}" }
        null
    }
}

private fun guessImageType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "svg" -> "image/svg+xml"
    else -> "application/octet-stream"
}

private const val TAG = "docview"
