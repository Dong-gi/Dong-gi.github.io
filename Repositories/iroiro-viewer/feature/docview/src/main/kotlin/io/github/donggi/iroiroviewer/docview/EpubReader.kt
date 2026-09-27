package io.github.donggi.iroiroviewer.docview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.donggi.iroiroviewer.format.epub.EpubBook
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.webhost.LockedWebView
import io.github.donggi.iroiroviewer.webhost.ResourceProvider
import io.github.donggi.iroiroviewer.webhost.WebHost
import io.github.donggi.iroiroviewer.webhost.WebResource
import java.io.ByteArrayInputStream

/**
 * EPUB 한 권을 읽는 층.
 *
 * ## 장 하나를 통째로 띄우고, 스크롤은 WebView 가 한다
 *
 * 만화·PDF 는 `HorizontalPager` 로 쪽을 넘기지만 EPUB 에는 **쪽이라는 것이 없다.**
 * 장 하나의 길이는 글의 길이가 정하고, 그것을 우리가 화면 크기로 잘라 쪽을 만들면
 * 글꼴 크기가 바뀔 때마다 쪽 번호가 달라진다 — 이어보기가 가리키는 자리가 그때마다
 * 움직인다는 뜻이다. **차례(spine)의 번호가 우리가 세는 유일한 자리**이고, 장 안에서는
 * 세로로 스크롤한다.
 *
 * ## 자원을 통째로 읽어서 넘긴다
 *
 * `shouldInterceptRequest` 는 **다른 스레드**에서 온다. ZIP 리더를 그 스레드가 직접
 * 물고 있으면 사용자가 화면을 떠나 책을 닫는 순간과 겹칠 수 있어, 읽는 동안만 잠그고
 * **바이트로 만들어** 넘긴다. EPUB 의 자원은 그림·CSS·글꼴이라 그렇게 해도 되는 크기다
 * (상한은 [MAX_RESOURCE_BYTES]).
 */
@Composable
fun EpubReader(
    doc: DocViewModel.Doc.Epub,
    chapter: Int,
    /** 장 사이 링크가 가리킨 자리(`id`). 장이 바뀌면 WebView 가 그 자리에서 연다. */
    anchor: String?,
    /** 다른 장으로 간다. [anchor] 는 링크의 조각이다(없으면 null — 장의 처음). */
    onChapter: (chapter: Int, anchor: String?) -> Unit,
    /** 장 하나를 읽어 위생기를 지났다. 버린 것의 집계가 늘었을 수 있다. */
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = doc.book
    // 콜백을 키로 쓰지 않는다(`LockedWebView` 의 같은 주석). 공급기는 책이 바뀔 때만 새로 만든다.
    val latestRead by rememberUpdatedState(onRead)
    val provider = remember(book) { providerFor(book) { latestRead() } }
    var loading by remember(book, chapter) { mutableStateOf(true) }

    val path = book.spine.getOrNull(chapter)?.path
    Box(modifier.fillMaxSize().background(Color.White)) {
        if (path != null) {
            LockedWebView(
                url = WebHost.urlFor(path) + (anchor?.let { "#$it" } ?: ""),
                provider = provider,
                onNavigate = { target, fragment ->
                    val index = book.spineIndexOf(target)
                    when {
                        // 같은 장 안의 앵커다. WebView 가 스스로 그 자리로 간다.
                        index == chapter -> false
                        index >= 0 -> {
                            onChapter(index, fragment)
                            true
                        }
                        // 차례 밖(각주 전용 쪽 등)은 열지 않는다. 열면 '다음 장' 이
                        // 어디인지 알 수 없는 자리에 사용자가 갇힌다.
                        else -> true
                    }
                },
                onReady = { loading = false },
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
 * 장을 넘기는 줄. 쪽 번호 대신 **장 번호**를 보여 준다.
 *
 * 슬라이더를 두지 않는 것은 장 수가 대개 열 남짓이라 끌 자리가 없기 때문이다.
 * 대신 목차가 그 일을 한다 — 장에는 이름이 있어 번호보다 낫다.
 */
@Composable
fun EpubBottomBar(
    chapter: Int,
    count: Int,
    onChapter: (Int) -> Unit,
    onToc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            // **시스템 막대를 피한다.** 겹치지 않는 칸 구조라 우리가 피해야 한다 —
            // 겹치는 구조였다면 반투명 막대가 그 위를 덮어 주지만 여기는 아니다.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(enabled = chapter > 0, onClick = { onChapter(chapter - 1) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.doc_previous_chapter),
                tint = if (chapter > 0) Color.White else Color.White.copy(alpha = 0.3f),
            )
        }
        TextButton(onClick = onToc) {
            Text(
                text = stringResource(R.string.doc_chapter_of, chapter + 1, count),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(enabled = chapter < count - 1, onClick = { onChapter(chapter + 1) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.doc_next_chapter),
                tint = if (chapter < count - 1) Color.White else Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

/**
 * 목차. **평평한 목록에 들여쓰기만 준다**(`EpubToc` 의 주석 참고).
 *
 * 차례 밖을 가리키는 줄은 **누를 수 없게** 둔다. 지우지 않는 이유는 목차의 모양이
 * 책이 말한 그대로여야 하기 때문이다 — 없는 줄이 되면 사용자는 자기가 잘못 본 줄 안다.
 */
@Composable
fun EpubTocList(
    book: EpubBook,
    current: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (book.toc.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.doc_no_toc))
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        items(book.toc.size) { i ->
            val entry = book.toc[i]
            val enabled = entry.spineIndex >= 0
            Text(
                text = entry.title.ifBlank { stringResource(R.string.doc_untitled_chapter) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.spineIndex == current) FontWeight.Bold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) Color.Unspecified
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (enabled) {
                            Modifier.clickableRow { onPick(entry.spineIndex) }
                        } else {
                            Modifier
                        }
                    )
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

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

/**
 * 책 안의 것을 내주는 이음매.
 *
 * **장(XHTML)은 바이트 그대로 나가지 않는다** — [EpubBook.chapterHtml] 이 위생기를
 * 지난 결과를 준다. 그 밖의 자원(그림·CSS·글꼴)은 바이트 그대로다.
 *
 * **스타일시트도 위생을 거친다.** 장 안의 `<style>` 만 걸러서는 구멍이 남는다 —
 * `<link rel="stylesheet" href="book.css">` 로 걸린 파일은 이 길로 들어오므로
 * [EpubBook.styleSheet] 을 지나게 한다. 위생의 구멍은 한 군데면 충분히 뚫린다.
 */
private fun providerFor(book: EpubBook, onRead: () -> Unit): ResourceProvider = ResourceProvider { path ->
    try {
        val index = book.spineIndexOf(path)
        // 매니페스트의 형식은 책이 적은 값이다 — `text/css; charset=utf-8`·대문자·틀린 형식이 온다. 글자 그대로 견주면
        // 그런 스타일시트가 위생 없이 나가고, 매개변수가 붙은 것은 WebView 가 그대로 적용한다(실세계 말뭉치 검토).
        val declared = book.mediaTypeOf(path)?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (index >= 0) {
            val html = synchronized(book) { book.chapterHtml(index) } ?: return@ResourceProvider null
            onRead()
            WebResource("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
        } else if (declared == "text/css" || guessType(path) == "text/css") {
            // 이름과 적힌 형식 가운데 **하나라도** CSS 면 위생을 거친다 — 날것으로 나가는 스타일시트가 없게.
            val css = synchronized(book) { book.styleSheet(path) } ?: return@ResourceProvider null
            onRead()
            WebResource("text/css", "utf-8", ByteArrayInputStream(css.toByteArray()))
        } else {
            // **날것으로 내주는 것은 그림과 글꼴뿐이다**(`rawResourceType`). 차례 밖의 문서(HTML·XML)는 위생을 거치지
            // 않은 것이라 내주지 않는다 — 그리로 가는 탐색은 이미 막혀 있고(`onNavigate`) 문서를 품는 요소는 위생기가
            // 지우지만, 남은 길이 없게 여기서도 닫는다.
            val type = rawResourceType(declared, path) ?: return@ResourceProvider null
            val bytes = synchronized(book) { readAll(book, path) } ?: return@ResourceProvider null
            WebResource(type, null, ByteArrayInputStream(bytes))
        }
    } catch (t: Throwable) {
        // 다른 스레드에서 오는 호출이라 **책이 닫히는 순간과 겹칠 수 있다.**
        // 그때는 404 로 끝낸다 — 화면은 이미 사라지는 중이다.
        Iro.d(TAG) { "자원을 내주지 못했다: ${t::class.java.simpleName}" }
        null
    }
}

/**
 * 책의 자원을 **날것으로** 내줄 때의 MIME. 그림·글꼴만이고, 그 밖이면 null(내주지 않는다).
 *
 * **책이 적은 형식 문자열을 그대로 WebView 에 넘기지 않는다.** 크롬은 표준 모드에서도 형식이 비었거나
 * `application/x-unknown-content-type` 이거나 쉼표 앞이 `text/css` 인 것(`text/css,x`)을 스타일시트로 적용한다 —
 * 그런 형식으로 적힌 파일이 CSS 위생을 건너뛰고 적용됐다(검토가 크롬에서 재현했다). 그래서 형식은 **우리 표에서만** 나온다.
 */
internal fun rawResourceType(declared: String?, path: String): String? {
    val named = declared?.let { RAW_ALIASES[it] ?: it }?.takeIf { it in RAW_TYPES }
    return named ?: guessType(path).takeIf { it in RAW_TYPES }
}

/** 날것으로 내줄 수 있는 형식. */
private val RAW_TYPES = setOf(
    "image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml",
    "font/otf", "font/ttf", "font/woff", "font/woff2", "font/collection", "application/vnd.ms-fontobject",
)

/** EPUB 의 매니페스트가 흔히 쓰는 옛 이름 → 표의 이름. */
private val RAW_ALIASES = mapOf(
    "image/jpg" to "image/jpeg",
    "application/vnd.ms-opentype" to "font/otf",
    "application/x-font-otf" to "font/otf",
    "application/font-sfnt" to "font/ttf",
    "application/x-font-ttf" to "font/ttf",
    "application/x-font-truetype" to "font/ttf",
    "application/font-woff" to "font/woff",
    "application/font-woff2" to "font/woff2",
)

private fun readAll(book: EpubBook, path: String): ByteArray? {
    val stream = book.openResource(path) ?: return null
    return stream.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            // **선언된 크기를 믿지 않고 실제로 읽은 바이트를 센다**(저장소 규칙).
            if (total > MAX_RESOURCE_BYTES) {
                Iro.d(TAG) { "자원이 상한을 넘었다" }
                return null
            }
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
}

/**
 * 매니페스트가 MIME 을 말해 주지 않을 때의 추측.
 *
 * **틀리면 화면이 비지만 밖으로 나가는 것은 없다.** 그래서 모르면
 * `application/octet-stream` 으로 끝낸다 — 거짓 MIME 을 주면 WebView 가 엉뚱하게 처리한다.
 */
private fun guessType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "xhtml", "html", "htm" -> "text/html"
    "css" -> "text/css"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "otf" -> "font/otf"
    "ttf" -> "font/ttf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    // `EpubEncryption` 이 글꼴로 세는 확장자와 맞춘다. 한쪽만 알면 같은 파일이 한쪽에서는
    // 글꼴, 다른 쪽에서는 알 수 없는 바이트가 된다.
    "ttc" -> "font/collection"
    "eot" -> "application/vnd.ms-fontobject"
    else -> "application/octet-stream"
}

private const val TAG = "docview"

/** 자원 하나의 상한. EPUB 의 그림·글꼴이 이보다 크면 정상이 아니다. */
private const val MAX_RESOURCE_BYTES = 32L * 1024 * 1024
