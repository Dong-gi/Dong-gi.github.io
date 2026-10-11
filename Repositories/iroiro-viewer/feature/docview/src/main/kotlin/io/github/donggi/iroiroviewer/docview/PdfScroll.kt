package io.github.donggi.iroiroviewer.docview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.docview.pdf.PdfSearch
import io.github.donggi.iroiroviewer.docview.pdf.PdfSpreads
import io.github.donggi.iroiroviewer.safety.PdfLimits
import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import io.github.donggi.iroiroviewer.ui.image.DetailLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * PDF 를 **세로로 이어 보는** 화면 — 기본 보기다.
 * 구글 드라이브·삼성·Adobe 의 PDF 뷰어처럼 쪽을 화면 폭에 맞춰 위아래로 이어 붙이고, **배율은 문서 전체에 걸린다** — 키운 채
 * 다음 쪽으로 내려가도 그 배율이다. 예전의 한 쪽씩 넘기기(`PagedDocument`)는 ⋮ 의 '보기 방식' 으로 남는다(`AppPreferences.pdfContinuous`).
 *
 * ## 무엇을 그리고 무엇을 버리는가(메모리)
 *
 * - **줄 하나가 칸 하나다** — 쪽 하나, 두 쪽 보기면 펼침 하나([PdfSpreads.View]). `LazyColumn` 이 화면에 걸친 줄과 미리 만드는 하나만
 *   구성하고, 벗어난 줄은 그 자리에서 버린다(그리던 것은 취소되어 렌더 줄에서 돌아선다).
 * - **바닥층은 폭에 맞춘 한 장**([PdfPageStore.fittedToWidth]). 화면에 차지하는 넓이 그대로라, 걸친 줄의 합이 화면 두어 장이다. 화면보다
 *   긴 줄(가로 화면의 A4 세로)은 한 장 상한([io.github.donggi.iroiroviewer.safety.ImageLimits.scrollPageCap] — 살아 있는 몫의 1/3)에
 *   걸려 낮은 배율로 그린다. 캐시는 쪽 넘김과 같은 맞춤 캐시(`liveCap`)다.
 * - **확대해도 바닥층을 다시 그리지 않는다.** 목록 전체에 `graphicsLayer` 로 배율을 걸고, 그 배율이 바닥층의 배율을 넘은 만큼
 *   **보이는 자리만** 다시 그린다([PdfPageStore.regionDetail]). 줄마다 화면에 보이는 넓이대로 선명화 예산(`detailCap`)을 나눠 받으므로
 *   합이 한 장 몫이다([PdfScrollMath.shares]). 손가락이 멎은 뒤([DETAIL_WAIT_MS])에 그린다 — pdfium 의 잠금은 프로세스 전역이다.
 * - 그리기는 쪽 넘김과 같은 렌더 스레드 하나(`IroDispatchers.pdfRender`)를 지난다.
 *
 * ## 배율과 스크롤 — 목록은 배율 1 로 놓고 층이 키운다
 *
 * 목록은 언제나 화면 폭으로 배치되고, 왼쪽 위를 축으로 [PdfScrollState.scale] 배 키워 그린다. 그래서 화면의 y 는 목록의 y × 배율이다.
 * **세로 이동은 목록의 스크롤**이고(층 안에서 포인터 좌표가 함께 줄어 손가락을 정확히 따른다), 가로 이동은 층의 옮김
 * ([PdfScrollState.tx])이다. 키운 층에서 화면에 보이는 것은 목록의 위 `높이 / 배율` 뿐이라, 끝까지 보이게 아래 여백을 그만큼 둔다
 * ([PdfScrollMath.bottomPadding]).
 *
 * ## 몸짓
 *
 * | 몸짓 | 배율 1 | 확대돼 있으면 |
 * |---|---|---|
 * | 한 손가락 끌기 | 목록이 세로로(관성 그대로) | 우리가 위아래·좌우로 함께 밀고 놓으면 관성으로 미끄러진다 |
 * | 두 손가락 | 핀치 — 손가락 사이를 제자리에 두고 키운다 | 같다 |
 * | 두 번 두드림 | 짚은 자리를 2.5배로 | 맞춤(배율 1)으로 |
 * | 한 번 두드림 | 막대 여닫기(고른 글이 있으면 그것을 푼다) | 같다 |
 * | 길게 누름 | 그 자리의 낱말 고르기 | 같다 |
 *
 * 쪽 넘김과 같은 것: 지금 쪽(가운데 줄 — [PdfScrollMath.currentRow])을 VM 에 알려 쪽 번호·슬라이더·이어보기·쪽 목록·쪽으로 가기가
 * 한 벌로 남고, 찾기는 결과가 있는 자리까지 내려 보인다.
 */
@Composable
internal fun ScrollDocument(
    doc: DocViewModel.Doc.Pdf,
    vm: DocViewModel,
    spread: Boolean,
    search: PdfSearch.State,
    viewportWidth: Int,
    viewportHeight: Int,
    onTap: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val store = doc.store
    val current by vm.page.collectAsStateWithLifecycle()
    val latestCurrent by rememberUpdatedState(current)
    val latestTap by rememberUpdatedState(onTap)
    val latestMessage by rememberUpdatedState(onMessage)
    val rows = PdfScrollMath.rowCount(doc.count, spread)
    fun rowOf(page: Int): Int = PdfScrollMath.rowOf(page, doc.count, spread)
    fun viewOf(row: Int): PdfSpreads.View =
        if (spread) PdfSpreads.View(PdfSpreads.pages(row, doc.count)) else PdfSpreads.View.single(row)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rowOf(current).coerceIn(0, (rows - 1).coerceAtLeast(0)))
    // 화면 크기가 바뀌면(회전) 배율을 맞춤으로 되돌린다 — 목록이 새 폭으로 다시 놓인다.
    val zoom = remember(doc.path, viewportWidth, viewportHeight) { PdfScrollState(viewportWidth.toFloat()) }
    val selection = remember(doc.path) { PdfSelection() }
    BackHandler(enabled = selection.active) { selection.clear() }

    // 첫 줄의 크기 — 아직 재지 않은 줄의 높이를 짐작하고(대개 같은 크기다), 최대 배율(원본의 2배)을 잰다.
    val firstLayout by produceState<PdfSpreads.Layout?>(null, store, spread) { value = store.layout(viewOf(0)) }
    val densityDpi = LocalConfiguration.current.densityDpi
    val maxScale = firstLayout?.let { ZoomMath.maxScale(PdfLimits.originalWidthPx(it.width, densityDpi), viewportWidth.toFloat()) }
        ?: ZoomMath.MIN_MAX_SCALE
    SideEffect { zoom.maxScale = maxScale }
    val estimateAspect = firstLayout?.let { it.height.toFloat() / it.width } ?: A4_ASPECT

    // ---- 지금 쪽 ↔ VM ----
    // 끝까지 내렸으면(문서가 화면보다 길 때) 마지막 줄이다 — 짧은 쪽이 여럿 보이는 끝에서 '마지막 쪽으로 가기' 가 앞 쪽으로 읽히지 않게.
    fun currentRowNow(): Int? = PdfScrollMath.currentRow(
        rows = rowsOf(listState.layoutInfo),
        windowTop = 0f,
        windowBottom = viewportHeight / zoom.scale,
        atEnd = !listState.canScrollForward && listState.canScrollBackward,
    )
    // 목록 → VM. 사용자가 내린 결과다. 펼침이면 첫 쪽을 적는다(쪽 넘김과 같다).
    LaunchedEffect(listState, zoom) {
        snapshotFlow { currentRowNow() }
            .distinctUntilChanged()
            .collect { row ->
                if (row == null) return@collect
                val pages = viewOf(row).pages
                if (latestCurrent !in pages) vm.onPageChanged(pages.first())
            }
    }
    // VM → 목록. 슬라이더·쪽으로 가기·쪽 목록·'처음부터' 가 여기로 들어온다. 이미 그 줄을 보고 있으면 움직이지 않는다.
    LaunchedEffect(current) {
        val target = rowOf(current)
        if (currentRowNow() != target) listState.scrollToItem(target)
    }
    // 찾기 — 결과가 있는 자리까지 내린다(쪽이 화면보다 길면 쪽의 처음으로는 모자란다). 가로로도 보이게.
    val match = search.currentMatch
    LaunchedEffect(match) {
        val m = match ?: return@LaunchedEffect
        val row = rowOf(m.page)
        val layout = store.layout(viewOf(row)) ?: return@LaunchedEffect
        val box = m.boxes.firstOrNull()?.let { PdfSearch.boxIn(layout, m.page, it) } ?: return@LaunchedEffect
        val rowHeight = viewportWidth.toFloat() * layout.height / layout.width
        listState.scrollToItem(row, PdfScrollMath.matchOffset(box[1], rowHeight, viewportHeight / zoom.scale))
        zoom.reveal(box[0] * viewportWidth, box[2] * viewportWidth)
    }
    // 고른 글이 든 줄이 화면에서 빠지면 푼다 — 보이지 않는 선택이 뒤로 가기를 먹지 않게(쪽 넘김은 쪽을 넘기면 푼다).
    LaunchedEffect(listState, selection) {
        snapshotFlow { selection.page to listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { (page, visible) ->
                if (page >= 0 && visible.isNotEmpty() && rowOf(page) !in visible) selection.clear()
            }
    }

    // ---- 선명화 조각 ----
    var details by remember(doc.path, spread, viewportWidth) { mutableStateOf<Map<Int, DetailLayer>>(emptyMap()) }
    val gridOpen by store.gridOpen.collectAsStateWithLifecycle()
    LaunchedEffect(store, spread, viewportWidth, viewportHeight, gridOpen, zoom) {
        if (gridOpen) {
            // 격자가 선명화 예산을 빌려 쓴다(`PdfPageStore.openGrid`) — 쥔 조각을 놓는다.
            details = emptyMap()
            return@LaunchedEffect
        }
        snapshotFlow {
            detailWants(
                rows = rowsOf(listState.layoutInfo),
                store = store,
                viewOf = ::viewOf,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                scale = zoom.scale,
                tx = zoom.tx,
                maxScale = zoom.maxScale,
            )
        }
            .distinctUntilChanged()
            .collectLatest { wants ->
                val wanted = wants.map { it.row }.toSet()
                // 화면에서 빠진 줄의 조각은 곧바로 놓는다. 남은 줄의 옛 조각은 새것이 올 때까지 둔다 — 자리는 쪽 안의 비율이라 옛것도
                // 제자리에 얹힌다(흐린 바닥보다 낫다).
                details = details.filterKeys { it in wanted }
                if (wants.isEmpty()) return@collectLatest
                delay(DETAIL_WAIT_MS)
                for (w in wants) {
                    val tile = store.regionDetail(
                        view = viewOf(w.row),
                        scale = w.scale,
                        baseScale = w.baseScale,
                        left = w.left,
                        top = w.top,
                        right = w.right,
                        bottom = w.bottom,
                        capBytes = w.capBytes,
                        maxScale = w.maxScale,
                    ) ?: continue
                    val at = DocDetail.fractionOf(tile.tile)
                    details = details + (w.row to DetailLayer(tile.image, at[0], at[1], at[2], at[3]))
                }
            }
    }

    // ---- 길게 눌러 고르기 ----
    val selectScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val unavailable = stringResource(R.string.doc_text_unavailable)
    val noText = stringResource(R.string.doc_text_none)
    val fingerPx = with(LocalDensity.current) { FINGER_SLOP.toPx() }
    fun selectAt(p: Offset) {
        if (!store.canSelectText) {
            latestMessage(unavailable)
            return
        }
        val hit = PdfScrollMath.rowAt(p.x, p.y, zoom.scale, zoom.tx, viewportWidth.toFloat(), rowsOf(listState.layoutInfo))
            ?: return
        val layout = store.knownLayout(viewOf(hit.row)) ?: return
        val (page, local) = PdfSelectionMath.fractionToPage(layout, hit.x, hit.y) ?: return
        if (local.x !in -0.02f..1.02f || local.y !in -0.02f..1.02f) return
        // 화면 화소 하나가 쪽의 몇 포인트인가 — 줄은 화면 폭에 맞춰 놓였고 확대가 그 위에 걸린다.
        val ptPerPx = layout.width / (viewportWidth * zoom.scale)
        val row = hit.row
        selectScope.launch {
            val picked = pickWord(store, selection, page, local, fingerPx * ptPerPx) {
                listState.layoutInfo.visibleItemsInfo.any { it.index == row }
            }
            when (picked) {
                WordPick.SELECTED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                WordPick.UNAVAILABLE -> latestMessage(unavailable)
                WordPick.NO_TEXT -> latestMessage(noText)
                WordPick.MISSED -> Unit
            }
        }
    }
    val latestSelectAt by rememberUpdatedState(::selectAt)

    val density = LocalDensity.current
    // 아래 여백은 **잴 때** 배율을 읽는다 — 컴포지션에서 읽으면 핀치의 프레임마다 화면 전체가 다시 구성된다.
    val bottomPadding = remember(zoom, viewportHeight, density) { ZoomBottomPadding(zoom, viewportHeight.toFloat(), density) }
    val gestureScope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            // 바깥이 먼저다(Initial) — 핀치와 확대한 문서의 끌기를 목록보다 먼저 잡는다. 안쪽이 두드림(Main)이다.
            .scrollZoomGestures(zoom, listState, handleBusy = { selection.grabbing || selection.dragging })
            .pointerInput(zoom, listState) {
                detectTapGestures(
                    onTap = { if (selection.active) selection.clear() else latestTap() },
                    onDoubleTap = { p ->
                        gestureScope.launch {
                            listState.stopScroll(MutatePriority.UserInput)
                            zoom.animateDoubleTap(p) { dy -> listState.dispatchRawDelta(dy) }
                        }
                    },
                    onLongPress = { p -> latestSelectAt(p) },
                )
            },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = bottomPadding,
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.tx
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            items(count = rows, key = { it }) { row ->
                val view = viewOf(row)
                ScrollRow(
                    store = store,
                    view = view,
                    widthPx = viewportWidth,
                    estimateAspect = estimateAspect,
                    detail = details[row],
                    highlights = search.on(view.pages),
                    currentMatch = search.currentMatch,
                    selection = selection,
                    zoom = zoom,
                    onMessage = onMessage,
                )
            }
        }
    }
}

/** 줄 하나 — 폭에 맞춘 바닥층, 그 위에 선명화 조각·찾기 강조·고른 글. 목록 전체에 걸린 층이 함께 키운다. */
@Composable
private fun ScrollRow(
    store: PdfPageStore,
    view: PdfSpreads.View,
    widthPx: Int,
    estimateAspect: Float,
    detail: DetailLayer?,
    highlights: List<PdfSearch.Match>,
    currentMatch: PdfSearch.Match?,
    selection: PdfSelection,
    zoom: PdfScrollState,
    onMessage: (String) -> Unit,
) {
    val fit by produceState<RowFit>(RowFit.Loading, store, view, widthPx) {
        val image = store.fittedToWidth(view, widthPx)
        value = if (image == null) RowFit.Failed else RowFit.Ready(image)
    }
    // 크기는 그리면서 잰다 — 다 그린 뒤에는 정확한 비율이고, 그 전에는 첫 줄로 짐작한다(대개 같다).
    val layout = store.knownLayout(view)
    val aspect = layout?.let { it.height.toFloat() / it.width } ?: estimateAspect
    val heightDp = with(LocalDensity.current) { (widthPx * aspect).toDp() }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(ROW_PLACEHOLDER)
            .onSizeChanged { boxSize = it },
        contentAlignment = Alignment.Center,
    ) {
        when (val f = fit) {
            is RowFit.Loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))

            is RowFit.Failed -> Text(
                text = stringResource(R.string.doc_page_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(32.dp),
            )

            is RowFit.Ready -> {
                Canvas(Modifier.fillMaxSize()) {
                    drawRowLayers(f.image, detail)
                    if (layout != null) drawHighlights(layout, highlights, currentMatch)
                }
                if (layout != null && selection.page in view.pages && boxSize.width > 0) {
                    PdfSelectionOverlay(
                        layout = layout,
                        selection = selection,
                        // 층이 배율만큼 키워 그리므로 손잡이는 그만큼 줄여 둔다 — 화면에서 늘 같은 크기다.
                        frame = BoxFrame(boxSize.width.toFloat(), boxSize.height.toFloat(), 1f / zoom.scale),
                        // 배율이 바뀌면 떠 있는 도구 막대를 새 자리로 옮긴다(스크롤·가로 옮김은 층이 화면에서의 자리로 스스로 잰다).
                        frameKey = zoom.scale,
                        onMessage = onMessage,
                    )
                }
            }
        }
    }
}

/**
 * 바닥층과 선명화 조각. **둘 다 부동소수로 놓는다** — 목록 전체에 확대가 걸려 있어 잘린 1화소 미만의 어긋남이 배율만큼 커진다
 * (`ZoomableImage` 의 `drawBitmapLayers` 가 겪은 그것).
 */
private fun DrawScope.drawRowLayers(image: ImageBitmap, detail: DetailLayer?) {
    if (image.width <= 0 || image.height <= 0 || size.width <= 0f || size.height <= 0f) return
    scale(size.width / image.width, size.height / image.height, pivot = Offset.Zero) { drawImage(image) }
    if (detail != null && detail.image.width > 0 && detail.image.height > 0) {
        translate(detail.left * size.width, detail.top * size.height) {
            scale(
                detail.width * size.width / detail.image.width,
                detail.height * size.height / detail.image.height,
                pivot = Offset.Zero,
            ) {
                drawImage(detail.image)
            }
        }
    }
}

/** 찾기 결과. 쪽 넘김의 `SearchHighlights` 와 같은 자리 계산([PdfSearch.boxIn])이다 — 줄 상자가 곧 가상 쪽이다. */
private fun DrawScope.drawHighlights(layout: PdfSpreads.Layout, matches: List<PdfSearch.Match>, current: PdfSearch.Match?) {
    for (m in matches) {
        val color = if (m == current) SCROLL_HIGHLIGHT_CURRENT else SCROLL_HIGHLIGHT
        for (box in m.boxes) {
            val r = PdfSearch.boxIn(layout, m.page, box) ?: continue
            drawRect(
                color = color,
                topLeft = Offset(r[0] * size.width, r[1] * size.height),
                size = Size((r[2] - r[0]) * size.width, (r[3] - r[1]) * size.height),
            )
        }
    }
}

private sealed interface RowFit {
    data object Loading : RowFit
    data object Failed : RowFit
    data class Ready(val image: ImageBitmap) : RowFit
}

/**
 * 확대한 만큼의 아래 여백([PdfScrollMath.bottomPadding]). 목록은 여백을 **배치 단계**에서 읽으므로(`LazyList` 의 측정) 배율이 바뀌면
 * 다시 재기만 하고 다시 구성하지 않는다.
 */
private class ZoomBottomPadding(
    private val zoom: PdfScrollState,
    private val height: Float,
    private val density: Density,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateBottomPadding(): Dp = with(density) { PdfScrollMath.bottomPadding(height, zoom.scale).toDp() }
}

/** 목록의 배치 → 줄의 자리(목록 좌표 — 배율 1). */
private fun rowsOf(info: LazyListLayoutInfo): List<PdfScrollMath.Row> =
    info.visibleItemsInfo.map { PdfScrollMath.Row(it.index, it.offset.toFloat(), (it.offset + it.size).toFloat()) }

/** 선명화 조각 하나를 뜨는 요청. 눈금으로 끊어 둔 값이라 같은 요청이면 같은 값이다(흐름이 저절로 합쳐진다). */
private data class RowWant(
    val row: Int,
    val scaleStep: Int,
    val baseScale: Double,
    val leftStep: Int,
    val topStep: Int,
    val rightStep: Int,
    val bottomStep: Int,
    val capBytes: Long,
    val maxScale: Double,
) {
    val scale: Double get() = scaleStep.toDouble() / DocDetail.SCALE_STEPS
    val left: Double get() = leftStep.toDouble() / REGION_STEPS
    val top: Double get() = topStep.toDouble() / REGION_STEPS
    val right: Double get() = rightStep.toDouble() / REGION_STEPS
    val bottom: Double get() = bottomStep.toDouble() / REGION_STEPS
}

/**
 * 지금 화면에 보이는 줄마다 선명화 요청 — 바닥층의 배율을 넘게 키운 줄만. 예산은 보이는 넓이대로 나눈다([PdfScrollMath.shares]).
 * 크기를 아직 모르는 줄은 건너뛴다(문서를 만지지 않는다).
 */
private fun detailWants(
    rows: List<PdfScrollMath.Row>,
    store: PdfPageStore,
    viewOf: (Int) -> PdfSpreads.View,
    viewportWidth: Int,
    viewportHeight: Int,
    scale: Float,
    tx: Float,
    maxScale: Float,
): List<RowWant> {
    val regions = PdfScrollMath.visibleRegions(rows, viewportWidth.toFloat(), viewportHeight.toFloat(), scale, tx)
    if (regions.isEmpty()) return emptyList()
    class Candidate(val region: PdfScrollMath.Region, val fitScale: Double, val baseScale: Double)
    val candidates = regions.mapNotNull { region ->
        val layout = store.knownLayout(viewOf(region.row)) ?: return@mapNotNull null
        val base = store.widthPlan(layout, viewportWidth)?.scale ?: return@mapNotNull null
        val fitScale = viewportWidth.toDouble() / layout.width
        // 바닥층이 이미 그 해상도면(배율 1 의 세로 화면) 다시 그릴 것이 없다 — 예산도 나누지 않는다.
        if (!PdfLimits.needsDetail(fitScale * scale, base)) return@mapNotNull null
        Candidate(region, fitScale, base)
    }
    if (candidates.isEmpty()) return emptyList()
    val caps = PdfScrollMath.shares(candidates.map { it.region.area }, store.detailCap)
    return candidates.mapIndexed { i, c ->
        RowWant(
            row = c.region.row,
            scaleStep = floor(c.fitScale * scale * DocDetail.SCALE_STEPS + 0.5).toInt().coerceAtLeast(1),
            baseScale = c.baseScale,
            // 바깥으로 연다(왼·위 내림, 오른·아래 올림) — 끊은 눈금 때문에 보이는 가장자리가 흐린 채 남지 않게.
            leftStep = floor(c.region.left * REGION_STEPS).toInt(),
            topStep = floor(c.region.top * REGION_STEPS).toInt(),
            rightStep = ceil(c.region.right * REGION_STEPS).toInt(),
            bottomStep = ceil(c.region.bottom * REGION_STEPS).toInt(),
            capBytes = caps[i],
            maxScale = maxScale * c.fitScale,
        )
    }.filter { it.capBytes > 0 }
}

/**
 * 문서 전체의 배율과 가로 옮김. 세로 자리는 목록의 스크롤이 든다(머리말 '배율과 스크롤'). 계산은 [PdfScrollMath] 에 있다.
 */
@Stable
internal class PdfScrollState(private val width: Float) {

    var scale by mutableFloatStateOf(1f)
        private set

    /** 층의 가로 옮김(화면 화소). 0 이면 왼쪽 끝이 보이고 −(배율 − 1) × 폭이면 오른쪽 끝이다. */
    var tx by mutableFloatStateOf(0f)
        private set

    /** 최대 배율 — 원본(종이의 실제 크기)의 2배([ZoomMath.maxScale]). 화면이 첫 줄을 재서 준다. */
    var maxScale by mutableFloatStateOf(ZoomMath.MIN_MAX_SCALE)

    val zoomed: Boolean get() = !ZoomMath.sameScale(scale, 1f)

    /**
     * 핀치 한 번 — [pivot](화면)을 제자리에 두고 키운 뒤 [pan] 만큼 옮긴다. 가로는 여기서 바꾸고, **목록이 앞으로 가야 할 거리**(목록
     * 좌표, 앞으로가 양수)를 돌려준다 — 부르는 쪽이 목록에 준다.
     */
    fun zoomBy(zoomChange: Float, pivot: Offset, pan: Offset): Float {
        val from = scale
        val to = (from * zoomChange).coerceIn(1f, maxScale.coerceAtLeast(1f))
        tx = PdfScrollMath.clampTx(PdfScrollMath.txAfterZoom(tx, pivot.x, from, to) + pan.x, to, width)
        scale = to
        return PdfScrollMath.scrollAfterZoom(pivot.y, pan.y, from, to)
    }

    /** 한 손가락으로 민다(확대돼 있을 때). 가로는 여기서, 세로는 목록이 — 목록이 앞으로 가야 할 거리를 돌려준다. */
    fun panBy(delta: Offset): Float {
        tx = PdfScrollMath.clampTx(tx + delta.x, scale, width)
        return -delta.y / scale
    }

    /** 찾은 것이 가로로 화면 밖이면 보이게 옮긴다. [left]·[right] 는 목록 좌표(배율 1)다. */
    fun reveal(left: Float, right: Float) {
        tx = PdfScrollMath.txToReveal(tx, scale, width, left, right)
    }

    /** 손을 뗀 뒤 가로로 미끄러진다. */
    suspend fun flingX(velocity: Float, decay: DecayAnimationSpec<Float>) {
        val lower = -((scale - 1f) * width).coerceAtLeast(0f)
        if (lower >= 0f || abs(velocity) < 1f) return
        val anim = Animatable(tx)
        anim.updateBounds(lower, 0f)
        anim.animateDecay(velocity, decay) { tx = value }
    }

    /** 두 번 두드림 — 맞춤 ↔ 2.5배를 오간다(사진·만화와 같은 두 칸, `ZoomMath.doubleTapTarget`). [scroll] 이 목록을 옮긴다. */
    suspend fun animateDoubleTap(pivot: Offset, scroll: (Float) -> Unit) {
        val target = ZoomMath.doubleTapTarget(scale, minOf(ZoomMath.DOUBLE_TAP_SCALE, maxScale))
        val anim = Animatable(scale)
        anim.animateTo(target, tween(DOUBLE_TAP_MS)) {
            if (scale > 0f) {
                val dy = zoomBy(value / scale, pivot, Offset.Zero)
                if (dy != 0f) scroll(dy)
            }
        }
    }

    private companion object {
        const val DOUBLE_TAP_MS = 220
    }
}

/**
 * 이어 보기의 몸짓 — 핀치와 **확대한 문서의 끌기**(가로·세로·관성)를 목록보다 먼저 잡는다(`Initial`). 배율 1 의 세로 끌기는 목록이
 * 그대로 한다(관성·끝의 늘어남이 모두 목록의 것이다). 배율 1 에서 가로로 끌면 아무것도 움직이지 않지만 소비해서 두드림이 되지 않게 한다.
 *
 * [handleBusy] 가 참이면(고른 글의 손잡이에 손가락이 닿아 있다) 한 손가락 끌기를 잡지 않는다 — 손잡이는 이 상자의 자식이라 우리가
 * 먼저 잡으면 손잡이가 끌리지 않는다.
 */
private fun Modifier.scrollZoomGestures(
    zoom: PdfScrollState,
    list: LazyListState,
    handleBusy: () -> Boolean,
): Modifier = pointerInput(zoom, list) {
    val decay = splineBasedDecay<Float>(this)
    coroutineScope {
        var fling: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fling?.cancel()
            fling = null
            val slop = viewConfiguration.touchSlop
            var pinched = false
            var panning = false
            var sideways = false
            var travelled = Offset.Zero
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) {
                    if (panning) {
                        val v = tracker.calculateVelocity()
                        val s = zoom.scale
                        fling = launch {
                            launch { zoom.flingX(v.x, decay) }
                            launch { list.flingBy(-v.y / s, decay) }
                        }
                    }
                    break
                }
                if (pressed >= 2) {
                    if (!pinched) {
                        pinched = true
                        panning = false
                        // 목록이 끌던 것·미끄러지던 것을 멈춘다 — 핀치와 다투지 않게.
                        launch { list.stopScroll(MutatePriority.UserInput) }
                    }
                    val dy = zoom.zoomBy(event.calculateZoom(), event.calculateCentroid(useCurrent = false), event.calculatePan())
                    if (dy != 0f) list.dispatchRawDelta(dy)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    continue
                }
                // 핀치 뒤에 남은 손가락 하나는 목록에 맡긴다(층 안이라 키운 그대로 손가락을 따른다).
                if (pinched || handleBusy()) continue
                val change = event.changes.firstOrNull { it.pressed } ?: continue
                val move = change.positionChange()
                tracker.addPosition(change.uptimeMillis, change.position)
                when {
                    panning -> {
                        val dy = zoom.panBy(move)
                        if (dy != 0f) list.dispatchRawDelta(dy)
                        change.consume()
                    }
                    sideways -> change.consume()
                    else -> {
                        travelled += move
                        if (travelled.getDistance() <= slop) continue
                        if (zoom.zoomed) {
                            panning = true
                            // 미끄러지던 목록이 이 누름을 먼저 잡았으면 놓게 한다(우리가 밀기 시작한다).
                            launch { list.stopScroll(MutatePriority.UserInput) }
                            val dy = zoom.panBy(move)
                            if (dy != 0f) list.dispatchRawDelta(dy)
                            change.consume()
                        } else if (abs(travelled.x) > abs(travelled.y) && !list.isScrollInProgress) {
                            // 배율 1 의 가로 끌기 — 움직일 것은 없지만 놓았을 때 두드림(막대 여닫기)이 되지 않게 소비한다. 목록이 이미
                            // 세로로 밀고 있으면(세로로 내리다 옆으로 꺾었다) 그대로 둔다 — 소비하면 내리던 것이 관성 없이 멈춘다.
                            sideways = true
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}

/** 목록을 [velocity](목록 좌표의 초당 화소, 앞으로가 양수)로 미끄러뜨린다 — 확대한 문서를 우리가 민 뒤의 관성. */
private suspend fun LazyListState.flingBy(velocity: Float, decay: DecayAnimationSpec<Float>) {
    if (abs(velocity) < 1f) return
    scroll {
        var last = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
            val delta = value - last
            val consumed = scrollBy(delta)
            last = value
            // 끝에 닿았다 — 더 미끄러지지 않는다.
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

/**
 * 이어 보기의 계산. 안드로이드도 Compose 도 모른다 — JVM 시험이 답한다(`PdfScrollMathTest`). 좌표 약속은 [ScrollDocument] 의
 * 머리말 그대로다: 목록은 배율 1 로 놓이고(목록 좌표), 층이 왼쪽 위를 축으로 [scale] 배 키운 뒤 가로로 `tx` 옮긴다. 화면의 점
 * `(x, y)` 는 목록의 `((x − tx) / 배율, y / 배율)` 이다.
 */
internal object PdfScrollMath {

    /** 목록의 줄 하나 — 목록 좌표의 위·아래. */
    data class Row(val index: Int, val top: Float, val bottom: Float)

    /** 줄 하나에서 화면에 보이는 자리. 넷은 줄(가상 쪽) 안의 비율이고, [area] 는 화면에서의 넓이(화소²)다. */
    data class Region(val row: Int, val left: Float, val top: Float, val right: Float, val bottom: Float, val area: Float)

    /** 줄 안의 한 점. [x]·[y] 는 줄(가상 쪽) 안의 비율이다. */
    data class Hit(val row: Int, val x: Float, val y: Float)

    /** 줄의 수 — 쪽마다 하나, 두 쪽 보기면 펼침마다 하나. */
    fun rowCount(pageCount: Int, spread: Boolean): Int = if (spread) PdfSpreads.count(pageCount) else pageCount.coerceAtLeast(0)

    /** 쪽 [page] 가 든 줄. */
    fun rowOf(page: Int, pageCount: Int, spread: Boolean): Int =
        if (spread) PdfSpreads.spreadOf(page, pageCount) else page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    /** 가로 옮김의 범위 — `[−(배율 − 1) × 폭, 0]`. */
    fun clampTx(tx: Float, scale: Float, width: Float): Float {
        val lower = -((scale - 1f) * width).coerceAtLeast(0f)
        return if (tx.isNaN()) 0f else tx.coerceIn(lower, 0f)
    }

    /** 배율을 [from] 에서 [to] 로 바꿀 때 화면의 [pivotX] 아래 점을 제자리에 두는 가로 옮김. */
    fun txAfterZoom(tx: Float, pivotX: Float, from: Float, to: Float): Float {
        if (from <= 0f) return tx
        return pivotX - (pivotX - tx) / from * to
    }

    /**
     * 같은 때 목록이 앞으로 가야 할 거리(목록 좌표). 화면의 [pivotY] 아래 점이 [pivotY] + [panY] 로 간다 — 그 점의 목록 좌표는
     * `pivotY / from` 이고 바뀐 뒤에 `(pivotY + panY) / to` 에 와야 하므로 그 차만큼 스크롤한다.
     */
    fun scrollAfterZoom(pivotY: Float, panY: Float, from: Float, to: Float): Float {
        if (from <= 0f || to <= 0f) return 0f
        return pivotY / from - (pivotY + panY) / to
    }

    /** 목록의 아래 여백(목록 좌표). 키운 층에서 화면에 보이는 것은 목록의 위 `높이 / 배율` 이라, 끝까지 내려 보려면 나머지만큼 더 간다. */
    fun bottomPadding(height: Float, scale: Float): Float =
        if (scale <= 1f || height <= 0f) 0f else height - height / scale

    /**
     * 지금 쪽으로 칠 줄. 화면 맨 위의 줄이 통째로 보이면 그 줄(쪽으로 가기로 막 뛰어온 자리 — 짧은 가로 쪽이 여럿 보여도 고른 쪽이다),
     * 아니면 화면 가운데를 지나는 줄, 가운데가 줄 사이 틈이면 그 아래 줄이다. [windowTop]·[windowBottom] 은 보이는 창(목록 좌표)이다.
     *
     * [atEnd] 면(더 내릴 수 없는데 위로는 올릴 수 있다 — 문서가 화면보다 길다) 창의 **마지막 줄**이다. 목록은 마지막 줄을 맨 위로 올리지
     * 못하므로, 짧은 쪽이 여럿 보이는 끝에서 맨 위 줄로 세면 '마지막 쪽으로 가기' 가 몇 쪽 앞으로 읽혔다.
     */
    fun currentRow(rows: List<Row>, windowTop: Float, windowBottom: Float, atEnd: Boolean = false): Int? {
        val visible = rows.filter { it.bottom > windowTop && it.top < windowBottom }.sortedBy { it.top }
        if (atEnd) return visible.lastOrNull()?.index
        val first = visible.firstOrNull() ?: return null
        if (first.top >= windowTop - EDGE_PX && first.bottom <= windowBottom + EDGE_PX) return first.index
        val center = (windowTop + windowBottom) / 2f
        visible.firstOrNull { center >= it.top && center < it.bottom }?.let { return it.index }
        return (visible.firstOrNull { it.top >= center } ?: visible.last()).index
    }

    /** 화면의 점 → 그 아래 줄과 줄 안의 비율. 줄 사이 틈이나 줄 밖이면 null. 줄은 폭 [width] 를 꽉 채운다. */
    fun rowAt(x: Float, y: Float, scale: Float, tx: Float, width: Float, rows: List<Row>): Hit? {
        if (scale <= 0f || width <= 0f) return null
        val lx = (x - tx) / scale
        val ly = y / scale
        val row = rows.firstOrNull { ly >= it.top && ly < it.bottom } ?: return null
        val h = row.bottom - row.top
        if (h <= 0f) return null
        return Hit(row.index, lx / width, (ly - row.top) / h)
    }

    /** 줄마다 화면에 보이는 자리. 보이지 않는 줄은 빠진다. 줄은 목록 폭 [width] 를 꽉 채운다. */
    fun visibleRegions(rows: List<Row>, width: Float, height: Float, scale: Float, tx: Float): List<Region> {
        if (width <= 0f || height <= 0f || scale <= 0f) return emptyList()
        val wl = (-tx / scale).coerceAtLeast(0f)
        val wr = ((width - tx) / scale).coerceAtMost(width)
        val wb = height / scale
        if (wr <= wl) return emptyList()
        return rows.mapNotNull { row ->
            val h = row.bottom - row.top
            val t = maxOf(row.top, 0f)
            val b = minOf(row.bottom, wb)
            if (h <= 0f || b <= t) return@mapNotNull null
            Region(
                row = row.index,
                left = wl / width,
                top = (t - row.top) / h,
                right = wr / width,
                bottom = (b - row.top) / h,
                area = (wr - wl) * (b - t) * scale * scale,
            )
        }
    }

    /**
     * 선명화 예산 [cap] 을 넓이대로 나눈다. **내림이라 합이 [cap] 을 넘지 않는다** — 줄마다 따로 뜬 조각이 동시에 살아 있어도 한 장
     * 몫(`detailCap`) 안이다.
     */
    fun shares(areas: List<Float>, cap: Long): List<Long> {
        val total = areas.filter { it > 0f && it.isFinite() }.sumOf { it.toDouble() }
        if (cap <= 0 || total <= 0.0) return areas.map { 0L }
        return areas.map { a -> if (a > 0f && a.isFinite()) floor(cap * (a / total)).toLong() else 0L }
    }

    /** 찾은 것을 보일 때 줄 안에서 내릴 거리(목록 좌표) — 그 줄이 화면의 위 1/3 쯤에 오게. 음수면 0. */
    fun matchOffset(topFraction: Float, rowHeight: Float, windowHeight: Float): Int =
        (topFraction * rowHeight - windowHeight / 3f).coerceAtLeast(0f).roundToInt()

    /**
     * [left]~[right](목록 좌표)가 가로로 보이게 하는 옮김. 이미 보이면 그대로, 아니면 그 왼쪽이 화면 폭의 1/10 에 오게(잘라 준다).
     */
    fun txToReveal(tx: Float, scale: Float, width: Float, left: Float, right: Float): Float {
        if (scale <= 0f) return tx
        val visibleLeft = -tx / scale
        val visibleRight = (width - tx) / scale
        if (left >= visibleLeft && right <= visibleRight) return tx
        return clampTx(-(left * scale) + width * REVEAL_MARGIN, scale, width)
    }

    /** 줄의 위·아래가 창의 끝에 닿았다고 보는 여유(목록 화소). 스크롤의 반올림을 덮는다. */
    private const val EDGE_PX = 1f

    private const val REVEAL_MARGIN = 0.1f
}

/** 줄 사이의 틈. 쪽의 경계가 보이게 — 바닥(어두운 회색)이 비친다. */
private val ROW_GAP = 8.dp

/** 아직 그리지 못한 줄의 바탕. 바닥보다 조금 밝아 쪽의 자리가 보인다. */
private val ROW_PLACEHOLDER = Color(0xFF3A3A3A)

/** 크기를 모르는 동안의 줄 비율(A4 세로). 첫 줄을 재면 그것으로 바뀐다. */
private const val A4_ASPECT = 842f / 595f

/** 보이는 자리의 눈금(1/400). 스크롤 한 화소마다 새 조각을 청하지 않게 끊는다(`DocDetail` 의 눈금과 같은 까닭). */
private const val REGION_STEPS = 400

/** 확대·스크롤이 멎었다고 보는 시간. 쪽 넘김의 선명화와 같은 값이다. */
private const val DETAIL_WAIT_MS = 180L

/** 길게 누른 손가락이 닿은 것으로 치는 거리. 쪽 넘김과 같다. */
private val FINGER_SLOP = 14.dp

/** 찾기 결과의 칠 — 쪽 넘김과 같은 색. */
private val SCROLL_HIGHLIGHT = Color(0x66FFD54F)
private val SCROLL_HIGHLIGHT_CURRENT = Color(0x99FF8F00)
