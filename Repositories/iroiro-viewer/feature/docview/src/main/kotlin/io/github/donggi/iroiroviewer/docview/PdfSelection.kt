package io.github.donggi.iroiroviewer.docview

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.docview.pdf.PdfSpreads
import io.github.donggi.iroiroviewer.docview.pdf.text.PageText
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextLayout
import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * PDF 의 **글 고르기**(사용자 요청 'PDF 뷰어에서도 선택·복사'). 길게 누르면 그 낱말이 골라지고, 손잡이로 넓히고,
 * 시스템의 떠 있는 도구 막대(복사·모두 선택)로 복사한다 — 안드로이드의 글 고르기 그대로다.
 *
 * ## 한 쪽 안에서만 고른다
 *
 * 선택은 **쪽 하나**의 글자 범위다(`[start, end)`). 쪽을 넘기면 풀린다. 쪽을 넘는 선택은 손잡이를 끌어 페이저를 넘기는 일과
 * 겹쳐서 받지 않았다(구글의 PDF 뷰어도 쪽마다다). '모두 선택' 은 그 쪽의 글 전부다.
 *
 * ## 좌표 셋
 *
 * 화면(뷰포트) ↔ 가상 쪽의 비율(두 쪽 보기면 펼침 전체) ↔ 쪽의 포인트(글 꺼내기의 좌표 — `PageText`). 그리는 변환은
 * `ZoomableImage` 와 `SearchHighlights` 가 쓰는 것과 **같은 식**이어야 한다 — 다르면 칠한 자리가 글자에서 미끄러진다
 * ([PdfSelectionMath], JVM 시험이 지킨다).
 *
 * 화면 ↔ 비율은 보기마다 다르다([SelectionFrame]) — 쪽 넘김은 확대 상태로 옮기고([ZoomFrame]), 세로로 이어 보기는 쪽
 * 상자 **안에서** 그리므로 비율에 상자 크기를 곱하면 끝이다([BoxFrame] — 확대는 목록 전체에 걸린 층이 한다).
 */
@Stable
internal class PdfSelection {
    var page by mutableIntStateOf(-1)
        private set
    var text by mutableStateOf<PageText?>(null)
        private set
    var start by mutableIntStateOf(0)
    var end by mutableIntStateOf(0)

    /** 손잡이를 끄는 중. 도구 막대를 숨긴다. */
    var dragging by mutableStateOf(false)

    /**
     * 손가락이 손잡이에 닿아 있다(끌기 전부터 — `Initial` 단계에서 적는다). 이어 보기의 몸짓(`ScrollDocument`)이 확대한 문서를 밀지
     * 않고 손잡이에 끌기를 넘기려고 본다 — 그 몸짓은 손잡이의 부모라 손잡이보다 먼저 이벤트를 받는다. 그리기와 상관없어 상태가 아니다.
     */
    var grabbing = false

    val active: Boolean get() = text != null && end > start

    fun set(page: Int, text: PageText, start: Int, end: Int) {
        this.page = page
        this.text = text
        this.start = start
        this.end = end
    }

    fun clear() {
        page = -1
        text = null
        start = 0
        end = 0
        dragging = false
        grabbing = false
    }

    val coversAll: Boolean get() = text?.let { start == 0 && end == it.glyphs.size } ?: false

    fun selectAll() {
        val t = text ?: return
        start = 0
        end = t.glyphs.size
    }
}

/**
 * 고른 것을 그리는 층의 좌표 ↔ 가상 쪽 안의 비율. 보기마다 하나다(머리말의 '좌표 셋').
 */
internal interface SelectionFrame {
    /** 가상 쪽 안의 비율 → 층의 좌표. 아직 잴 수 없으면 null. */
    fun toLocal(fx: Float, fy: Float): Offset?

    /** 층의 좌표 → 가상 쪽 안의 비율. 아직 잴 수 없으면 null. */
    fun toFraction(local: Offset): Offset?

    /**
     * 손잡이를 이만큼 줄여 그린다. 층이 확대돼 그려지면(이어 보기 — 목록 전체에 확대가 걸린다) 손잡이도 함께 커지므로 배율의 역수를
     * 준다 — 화면에서는 언제나 같은 크기다.
     */
    val handleScale: Float
}

/** 쪽 넘김 — 확대 상태([ZoomState])가 맞춘 그림을 옮긴다. `ZoomableImage` 와 같은 식이다([PdfSelectionMath]). */
internal class ZoomFrame(private val image: ImageBitmap, private val zoom: ZoomState) : SelectionFrame {
    override fun toLocal(fx: Float, fy: Float): Offset? =
        PdfSelectionMath.toScreen(fx, fy, image.width, image.height, zoom.viewport, zoom.scale, zoom.offset)

    override fun toFraction(local: Offset): Offset? =
        PdfSelectionMath.toFraction(local.x, local.y, image.width, image.height, zoom.viewport, zoom.scale, zoom.offset)

    override val handleScale: Float get() = 1f
}

/** 이어 보기 — 쪽 상자([width]×[height]) 안. 확대는 바깥 층이 하므로 [handleScale] 로 손잡이만 되돌린다. */
internal class BoxFrame(private val width: Float, private val height: Float, override val handleScale: Float) : SelectionFrame {
    override fun toLocal(fx: Float, fy: Float): Offset? =
        if (width > 0f && height > 0f) Offset(fx * width, fy * height) else null

    override fun toFraction(local: Offset): Offset? =
        if (width > 0f && height > 0f) Offset(local.x / width, local.y / height) else null
}

/** 길게 누른 자리의 낱말을 고른 결과([pickWord]). */
internal enum class WordPick {
    SELECTED,

    /** 이 문서에서 글을 꺼낼 수 없다(원본이 없다·구조를 못 읽는다). 화면이 말한다. */
    UNAVAILABLE,

    /** 그 쪽에 글이 하나도 없다(그림으로 된 쪽). 화면이 말한다. */
    NO_TEXT,

    /** 글이 있는 쪽의 빈 자리를 눌렀거나, 꺼내는 사이에 자리를 떠났다. 조용히 넘어간다. */
    MISSED,
}

/**
 * 쪽 [page] 의 [local](쪽 안의 비율) 자리의 낱말을 고른다 — 두 보기(쪽 넘김·이어 보기)가 함께 쓴다. 쪽의 글은 그때 처음 꺼낸다(쪽마다
 * 수십~수백 ms — 입출력 스레드에서). [slopPt] 는 손가락의 반지름을 쪽 포인트로 옮긴 것이고, [stillHere] 는 꺼내는 사이에 그 쪽을
 * 떠나지 않았는가다 — 떠났으면 보이지 않는 선택이 뒤로 가기를 먹는다.
 */
internal suspend fun pickWord(
    store: PdfPageStore,
    selection: PdfSelection,
    page: Int,
    local: Offset,
    slopPt: Float,
    stillHere: () -> Boolean,
): WordPick {
    val text = store.text(page) ?: return WordPick.UNAVAILABLE
    if (text.glyphs.none { it.text.isNotBlank() }) return WordPick.NO_TEXT
    val hit = PdfTextLayout.hit(text, local.x * text.width, local.y * text.height, slopPt) ?: return WordPick.MISSED
    val word = PdfTextLayout.wordAt(text, hit)
    if (word.isEmpty() || !stillHere()) return WordPick.MISSED
    selection.set(page, text, word.first, word.last + 1)
    return WordPick.SELECTED
}

/** 화면 ↔ 가상 쪽의 비율 ↔ 쪽의 포인트. 순수 함수다(`PdfSelectionMathTest`). */
internal object PdfSelectionMath {

    /**
     * 가상 쪽 안의 비율(0~1)을 화면(뷰포트) 좌표로. `ZoomableImage` 가 그리는 변환 그대로다 — 맞춘 그림을 가운데 두고,
     * 뷰포트 가운데를 축으로 키운 뒤, 옮긴다.
     */
    fun toScreen(fx: Float, fy: Float, imageW: Int, imageH: Int, viewport: Size, scale: Float, offset: Offset): Offset {
        val (fw, fh) = ZoomMath.fittedSize(imageW, imageH, viewport.width, viewport.height)
        val x0 = (viewport.width - fw) / 2f + fx * fw
        val y0 = (viewport.height - fh) / 2f + fy * fh
        val cx = viewport.width / 2f
        val cy = viewport.height / 2f
        return Offset((x0 - cx) * scale + cx + offset.x, (y0 - cy) * scale + cy + offset.y)
    }

    /** [toScreen] 의 거꾸로. 그림이 아직 없으면(맞춘 크기 0) null. */
    fun toFraction(sx: Float, sy: Float, imageW: Int, imageH: Int, viewport: Size, scale: Float, offset: Offset): Offset? {
        val (fw, fh) = ZoomMath.fittedSize(imageW, imageH, viewport.width, viewport.height)
        if (fw <= 0f || fh <= 0f || scale <= 0f) return null
        val cx = viewport.width / 2f
        val cy = viewport.height / 2f
        val x0 = (sx - offset.x - cx) / scale + cx
        val y0 = (sy - offset.y - cy) / scale + cy
        return Offset((x0 - (viewport.width - fw) / 2f) / fw, (y0 - (viewport.height - fh) / 2f) / fh)
    }

    /** 쪽 [page] 의 점(쪽 포인트)을 가상 쪽의 비율로. 그 쪽이 이 가상 쪽에 없으면 null. */
    fun pageToFraction(layout: PdfSpreads.Layout, page: Int, text: PageText, x: Float, y: Float): Offset? {
        val part = layout.parts.firstOrNull { it.page == page } ?: return null
        if (layout.width <= 0 || layout.height <= 0 || text.width <= 0f || text.height <= 0f) return null
        val px = part.left + x / text.width * part.width
        val py = part.top + y / text.height * part.height
        return Offset(px / layout.width, py / layout.height)
    }

    /**
     * 가상 쪽의 비율이 놓인 쪽과, 그 쪽의 비율(0~1). 쪽 사이의 빈 자리(두 쪽 보기에서 높이가 다른 쪽의 위아래)는 가까운 쪽으로
     * 친다 — 손가락이 쪽 끝을 조금 벗어나도 고를 수 있게.
     */
    fun fractionToPage(layout: PdfSpreads.Layout, fx: Float, fy: Float): Pair<Int, Offset>? {
        if (layout.parts.isEmpty() || layout.width <= 0 || layout.height <= 0) return null
        val px = fx * layout.width
        val py = fy * layout.height
        val part = layout.parts.firstOrNull { px >= it.left && px < it.left + it.width }
            ?: layout.parts.minBy { p -> minOf(kotlin.math.abs(px - p.left), kotlin.math.abs(px - (p.left + p.width))) }
        return part.page to Offset((px - part.left) / part.width, (py - part.top) / part.height)
    }
}

/**
 * 고른 것을 쪽 그림 위에 칠하고 손잡이를 둔다. 이 칸([layout])에 고른 쪽이 없으면 아무것도 그리지 않는다.
 *
 * **칠하는 층은 포인터를 받지 않는다**(그리기만 한다) — 탭·핀치가 아래 그림으로 내려간다. 손잡이만 손가락을 받는다.
 *
 * @param frame 이 층의 좌표와 가상 쪽의 비율을 오가는 길(보기마다 다르다 — [SelectionFrame]).
 * @param frameKey 층 안에서 그림이 옮겨 가면 바뀌는 값(쪽 넘김의 확대·이동, 이어 보기의 배율). 바뀌면 떠 있는 도구 막대를 새 자리로
 *   옮긴다. 층 자체가 화면에서 옮겨 가는 것(이어 보기의 스크롤)은 이 층이 스스로 잰다(화면에서의 자리).
 */
@Composable
internal fun PdfSelectionOverlay(
    layout: PdfSpreads.Layout,
    selection: PdfSelection,
    frame: SelectionFrame,
    frameKey: Any?,
    onMessage: (String) -> Unit,
) {
    val text = selection.text ?: return
    if (selection.page !in layout.parts.map { it.page } || !selection.active) return
    val page = selection.page
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
    val handleColor = MaterialTheme.colorScheme.primary
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 층이 화면에서 놓인 자리 — 스크롤하면 바뀐다(`coords` 는 같은 객체라 바뀐 것으로 보이지 않는다).
    var origin by remember { mutableStateOf(Offset.Unspecified) }

    fun screenOf(x: Float, y: Float): Offset? {
        val f = PdfSelectionMath.pageToFraction(layout, page, text, x, y) ?: return null
        return frame.toLocal(f.x, f.y)
    }

    val rects = remember(text, selection.start, selection.end) { PdfTextLayout.rects(text, selection.start, selection.end) }
    // **왼쪽 위에서 잰다.** 부르는 쪽(쪽 하나의 상자)은 아이를 가운데 놓는다 — 그 안에 손잡이를 바로 두면 화면 가운데에서
    // 옮겨진 자리에 그려져 보이지 않았다(기기에서 잡았다).
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    coords = it
                    origin = it.positionInRoot()
                },
        ) {
            // 사각형의 두 귀퉁이를 층의 좌표로 옮겨 칠한다. 변환이 고른 배율과 옮김뿐이라(돌리지 않는다) 사각형은 사각형으로 간다 —
            // 예전의 `translate`·`scale` 로 그리던 것과 같은 자리다.
            for (r in rects) {
                val a = PdfSelectionMath.pageToFraction(layout, page, text, r[0], r[1]) ?: continue
                val b = PdfSelectionMath.pageToFraction(layout, page, text, r[2], r[3]) ?: continue
                val tl = frame.toLocal(a.x, a.y) ?: continue
                val br = frame.toLocal(b.x, b.y) ?: continue
                drawRect(color = highlight, topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y))
            }
        }

        // ---- 손잡이 ----
        val glyphs = text.glyphs
        val first = glyphs.getOrNull(selection.start)
        val last = glyphs.getOrNull(selection.end - 1)
        val density = LocalDensity.current
        val handleSize = 22.dp
        val handlePx = with(density) { handleSize.toPx() } * frame.handleScale
        if (first != null && last != null) {
            val startAt = screenOf(first.left, first.bottom)
            val endAt = screenOf(last.right, last.bottom)
            if (startAt != null) {
                SelectionHandle(
                    at = startAt,
                    isStart = true,
                    color = handleColor,
                    sizePx = handlePx,
                    selection = selection,
                    toPage = { screen -> pagePoint(screen, frame, layout, page, text) },
                    lineHeight = { screenOf(first.left, first.top)?.let { startAt.y - it.y } ?: 0f },
                )
            }
            if (endAt != null) {
                SelectionHandle(
                    at = endAt,
                    isStart = false,
                    color = handleColor,
                    sizePx = handlePx,
                    selection = selection,
                    toPage = { screen -> pagePoint(screen, frame, layout, page, text) },
                    lineHeight = { screenOf(last.right, last.top)?.let { endAt.y - it.y } ?: 0f },
                )
            }
        }

    }

    // ---- 떠 있는 도구 막대(복사·모두 선택) ----
    val toolbar = LocalTextToolbar.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.doc_text_copied)
    val tooLong = stringResource(R.string.doc_text_too_long, "%,d".format(Locale.ROOT, PdfTextLayout.MAX_COPY_CHARS))
    val failed = stringResource(R.string.doc_text_copy_failed)
    val latestMessage by rememberUpdatedState(onMessage)
    LaunchedEffect(selection.start, selection.end, selection.dragging, frameKey, coords, origin) {
        if (selection.dragging) {
            toolbar.hide()
            return@LaunchedEffect
        }
        // 손가락이 멎기를 기다린다 — 확대·이동 중에 프레임마다 막대를 옮기면 깜빡인다.
        delay(TOOLBAR_DELAY_MS)
        val c = coords ?: return@LaunchedEffect
        val tl = rects.firstOrNull()?.let { screenOf(it[0], it[1]) } ?: return@LaunchedEffect
        val br = rects.lastOrNull()?.let { screenOf(it[2], it[3]) } ?: return@LaunchedEffect
        val rootTl = c.localToRoot(Offset(minOf(tl.x, br.x), minOf(tl.y, br.y)))
        val rootBr = c.localToRoot(Offset(maxOf(tl.x, br.x), maxOf(tl.y, br.y)))
        toolbar.showMenu(
            rect = Rect(rootTl, rootBr),
            onCopyRequested = {
                val t = PdfTextLayout.text(text, selection.start, selection.end)
                if (t == null) {
                    latestMessage(tooLong)
                } else {
                    scope.launch {
                        try {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", t)))
                            // 안드로이드 13 부터는 시스템이 스스로 알린다(겹쳐 알리지 않는다 — 텍스트 뷰어와 같다).
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) latestMessage(copied)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: RuntimeException) {
                            latestMessage(failed)
                        }
                    }
                }
                selection.clear()
            },
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = if (selection.coversAll) null else ({ selection.selectAll() }),
        )
    }
    DisposableEffect(Unit) { onDispose { toolbar.hide() } }
}

/** 층의 점 → 쪽 [page] 의 포인트. 다른 쪽 위면 그 쪽의 가장자리로 누른다(선택은 쪽 하나다). */
private fun pagePoint(screen: Offset, frame: SelectionFrame, layout: PdfSpreads.Layout, page: Int, text: PageText): Offset? {
    val f = frame.toFraction(screen) ?: return null
    val part = layout.parts.firstOrNull { it.page == page } ?: return null
    val px = (f.x * layout.width - part.left) / part.width
    val py = (f.y * layout.height - part.top) / part.height
    return Offset(px.coerceIn(0f, 1f) * text.width, py.coerceIn(0f, 1f) * text.height)
}

/**
 * 손잡이 하나. 시스템 손잡이의 모양(동그라미의 한 귀퉁이를 각지게 해 글을 가리킨다)을 그린다. 끌면 손가락이 있는 **줄의 높이**
 * 쯤을 경계로 본다 — 손잡이는 글줄 아래에 매달려 있으므로 손가락 자리를 그대로 쓰면 아랫줄이 골라진다.
 *
 * 앞 손잡이를 뒤 손잡이 너머로 끌면 둘이 자리를 바꾼다(안드로이드의 글 고르기와 같다).
 */
@Composable
private fun SelectionHandle(
    at: Offset,
    isStart: Boolean,
    color: Color,
    sizePx: Float,
    selection: PdfSelection,
    toPage: (Offset) -> Offset?,
    lineHeight: () -> Float,
) {
    val latestAt by rememberUpdatedState(at)
    val latestLine by rememberUpdatedState(lineHeight)
    val latestToPage by rememberUpdatedState(toPage)
    // 앞 손잡이는 오른쪽 위 귀퉁이가 경계에 닿고, 뒤 손잡이는 왼쪽 위 귀퉁이가 닿는다.
    val left = if (isStart) at.x - sizePx else at.x
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), at.y.roundToInt()) }
            .size(with(LocalDensity.current) { sizePx.toDp() })
            // 닿는 순간을 적는다(끌기 전부터) — 이어 보기의 몸짓이 손잡이에 끌기를 넘긴다([PdfSelection.grabbing]).
            .pointerInput(selection) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    selection.grabbing = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        selection.grabbing = false
                    }
                }
            }
            .pointerInput(selection, isStart) {
                var finger = Offset.Zero
                var draggingStart = isStart
                detectDragGestures(
                    onDragStart = {
                        selection.dragging = true
                        draggingStart = isStart
                        // 끄는 점은 손잡이가 아니라 그 위의 글줄 가운데다.
                        finger = Offset(latestAt.x, latestAt.y - latestLine() / 2f)
                    },
                    onDragEnd = { selection.dragging = false },
                    onDragCancel = { selection.dragging = false },
                    onDrag = { change, delta ->
                        change.consume()
                        finger += delta
                        val text = selection.text ?: return@detectDragGestures
                        val p = latestToPage(finger) ?: return@detectDragGestures
                        val b = PdfTextLayout.boundaryAt(text, p.x, p.y) ?: return@detectDragGestures
                        if (draggingStart) {
                            if (b >= selection.end) {
                                // 뒤 손잡이를 넘었다 — 자리를 바꾼다.
                                selection.start = (selection.end - 1).coerceAtLeast(0)
                                selection.end = b.coerceAtLeast(selection.start + 1)
                                draggingStart = false
                            } else {
                                selection.start = b
                            }
                        } else {
                            if (b <= selection.start) {
                                selection.end = (selection.start + 1).coerceAtMost(text.glyphs.size)
                                selection.start = b.coerceAtMost(selection.end - 1)
                                draggingStart = true
                            } else {
                                selection.end = b
                            }
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(color, radius = r, center = Offset(size.width / 2f, size.height / 2f))
            // 경계를 가리키는 귀퉁이를 채운다(앞은 오른쪽 위, 뒤는 왼쪽 위).
            drawRect(
                color,
                topLeft = if (isStart) Offset(size.width / 2f, 0f) else Offset.Zero,
                size = Size(size.width / 2f, size.height / 2f),
            )
        }
    }
}

private const val TOOLBAR_DELAY_MS = 180L
