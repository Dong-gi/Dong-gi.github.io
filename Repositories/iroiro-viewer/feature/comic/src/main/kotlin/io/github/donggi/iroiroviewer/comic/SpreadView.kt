package io.github.donggi.iroiroviewer.comic

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import io.github.donggi.iroiroviewer.ui.image.DetailLayer
import io.github.donggi.iroiroviewer.ui.image.RasterDetail
import io.github.donggi.iroiroviewer.ui.image.rememberAnimatedPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 두 쪽 펼침 하나(14단계). [left]·[right] 는 **화면의 왼쪽·오른쪽**이다(읽는 차례가 아니다 — `Spreads.visualOrder`).
 *
 * ## 두 모양을 오간다
 *
 * - 두 쪽이 다 정지 그림으로 뜨면 [ZoomableSpread] — 확대·이동·선명화가 한 장짜리와 같다.
 * - 하나라도 아직 뜨는 중이거나, 움직이거나(GIF·애니WebP), 못 열었으면 **나란히 놓기만** 한다. 움직이는 쪽을 확대하지
 *   않는 9단계의 결정(프레임마다 다시 합성하는 값이 재생을 끊는다)이 펼침에도 그대로 걸린다.
 *
 * 나란히 놓을 때 두 쪽을 **가운데(제본선) 쪽으로 붙인다.** 같은 크기의 두 쪽이면 [ZoomableSpread] 가 그리는 자리와
 * 같아서, 두 번째 쪽이 뜨며 모양이 바뀌는 순간 그림이 옮겨 가지 않는다.
 *
 * ## 메모리
 *
 * 쪽마다 반쪽 자리에 **맞춘 크기 그대로**, [ComicPageStore.spreadPageCap](한 장 몫의 절반) 안에서 뜬다
 * ([ComicPageStore.half]) — 두 쪽이 화면 한 장의 몫을 나눠 쓴다. 둘을 한 비트맵으로 합치지 않는다(합친 것 한 장이 더
 * 생긴다). 선명화 조각도 쪽마다 선명화 몫의 절반이다.
 */
@Composable
internal fun SpreadView(
    store: ComicPageStore,
    left: Int,
    right: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    zoomState: ZoomState,
    onTap: () -> Unit,
    /** 못 연 쪽의 파일을 다른 앱으로 연다(`FailedPage` — 폴더로 연 만화의 쪽에만 단추가 선다). */
    onOpenWith: (String) -> Unit,
) {
    val slotWidth = (viewportWidth / 2).coerceAtLeast(1)
    val a by rememberHalf(store, left, slotWidth, viewportHeight)
    val b by rememberHalf(store, right, slotWidth, viewportHeight)
    val l = a
    val r = b

    if (l is PageState.Still && r is PageState.Still) {
        ZoomableSpread(
            store = store,
            leftOrdinal = left,
            rightOrdinal = right,
            leftPage = l,
            rightPage = r,
            state = zoomState,
            onTap = onTap,
        )
        val apng = l.info.apng || r.info.apng
        if (apng) ApngNotice()
    } else {
        // 확대할 수 없는 모양에서 확대가 남아 있으면 다음에 [ZoomableSpread] 가 설 때 엉뚱한 자리에서 시작한다.
        LaunchedEffect(zoomState) { zoomState.reset() }
        Row(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        ) {
            Half(l, store, left, onOpenWith, Alignment.CenterEnd, Modifier.weight(1f).fillMaxHeight())
            Half(r, store, right, onOpenWith, Alignment.CenterStart, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** 반쪽 자리 하나를 뜬다. 정지·움직임·실패 셋으로 갈린다(한 쪽 보기의 `ComicPageView` 와 같은 갈래). */
@Composable
private fun rememberHalf(store: ComicPageStore, ordinal: Int, slotWidth: Int, slotHeight: Int): State<PageState> =
    produceState<PageState>(PageState.Loading, store, ordinal, slotWidth, slotHeight) {
        val info = store.info(ordinal)
        value = when {
            info == null -> PageState.Failed
            info.animated -> {
                val target = SpreadMath.slotTarget(info.displayWidth, info.displayHeight, slotWidth, slotHeight)
                val drawable = store.moving(ordinal, target, store.spreadPageCap)
                if (drawable != null) PageState.Moving(drawable, info) else PageState.Failed
            }
            else -> {
                // 자리에 맞춘 크기 그대로 뜬다 — 2의 거듭제곱 표본은 반쪽 몫에 들려고 자리보다 작게 떠서 흐려진다
                // ([SpreadMath.halfSize]).
                val bitmap = store.half(ordinal, slotWidth, slotHeight)
                if (bitmap != null) PageState.Still(bitmap, info) else PageState.Failed
            }
        }
    }

@Composable
private fun Half(
    state: PageState,
    store: ComicPageStore,
    ordinal: Int,
    onOpenWith: (String) -> Unit,
    align: Alignment,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = align) {
        when (state) {
            is PageState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            is PageState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 반쪽 자리라도 단추를 단다 — 단추가 제 누름을 먼저 받으므로 바깥의 두드림(막대 토글)과 다투지 않는다.
                FailedPage(store, ordinal, onOpenWith, Modifier.padding(16.dp))
            }

            is PageState.Still -> Image(
                bitmap = state.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = align,
                modifier = Modifier.fillMaxSize(),
            )

            is PageState.Moving -> Image(
                painter = rememberAnimatedPainter(state.drawable),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = align,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 확대·이동되는 펼침. **제스처 구조는 `ZoomableImage` 와 글자 그대로 같다** — 바깥이 탭·더블탭, 안쪽이
 * `transformable(canPan)`. 그래서 배율 1 에서는 한 손가락 끌기를 소비하지 않아 **페이저가 받아 넘어가고**, 확대하면
 * 팬을 소비해 **넘어가지 않는다**(9단계의 '확대한 채 밀기' 약속).
 *
 * `ZoomableImage` 를 쓰지 못하는 까닭은 그것이 비트맵 한 장을 받기 때문이다. 두 쪽을 한 장으로 합치면 예산을 넘긴다.
 * 변환은 여전히 [ZoomState] 하나에서만 읽는다 — 두 쪽이 같은 변환을 써야 제본선이 벌어지지 않는다.
 */
@Composable
private fun ZoomableSpread(
    store: ComicPageStore,
    leftOrdinal: Int,
    rightOrdinal: Int,
    leftPage: PageState.Still,
    rightPage: PageState.Still,
    state: ZoomState,
    onTap: () -> Unit,
) {
    val frame = remember(leftPage.bitmap, rightPage.bitmap) {
        SpreadMath.frameOf(leftPage.bitmap.width, leftPage.bitmap.height, rightPage.bitmap.width, rightPage.bitmap.height)
    } ?: return
    val li = leftPage.info
    val ri = rightPage.info
    val original = SpreadMath.originalWidthOf(li.displayWidth, li.displayHeight, ri.displayWidth, ri.displayHeight)
        .takeIf { it > 0 } ?: frame.contentWidth
    // 선명화 몫([ComicPageStore.detailCap])을 두 쪽이 절반씩 쓴다.
    val detailCap = store.detailCap / 2
    val leftDetail = rememberSpreadDetail(
        key = Triple(store, leftOrdinal, rightOrdinal),
        state = state,
        frame = frame,
        left = true,
        baseWidth = leftPage.bitmap.width,
        originalWidth = if (li.canUseRegionDecoder) li.width else 0,
        originalHeight = if (li.canUseRegionDecoder) li.height else 0,
        capBytes = detailCap,
    ) { want -> store.region(leftOrdinal, intArrayOf(want.left, want.top, want.right, want.bottom), want.sample) }
    val rightDetail = rememberSpreadDetail(
        key = Triple(store, rightOrdinal, leftOrdinal),
        state = state,
        frame = frame,
        left = false,
        baseWidth = rightPage.bitmap.width,
        originalWidth = if (ri.canUseRegionDecoder) ri.width else 0,
        originalHeight = if (ri.canUseRegionDecoder) ri.height else 0,
        capBytes = detailCap,
    ) { want -> store.region(rightOrdinal, intArrayOf(want.left, want.top, want.right, want.bottom), want.sample) }

    val scope = rememberCoroutineScope()
    val transformable = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val pivot = Offset(
            centroid.x - state.viewport.width / 2f,
            centroid.y - state.viewport.height / 2f,
        )
        state.transform(zoomChange, panChange, pivot)
    }
    LaunchedEffect(transformable.isTransformInProgress) {
        if (transformable.isTransformInProgress) state.stopAnimation()
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { position ->
                        scope.launch {
                            state.stopAnimation()
                            val pivot = Offset(
                                position.x - state.viewport.width / 2f,
                                position.y - state.viewport.height / 2f,
                            )
                            state.animateDoubleTap(pivot)
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .transformable(state = transformable, canPan = { state.canPan }),
        ) {
            var canvasSize by remember { mutableStateOf(Size.Zero) }
            SideEffect {
                if (canvasSize.width > 0f) state.onLayout(canvasSize, frame.contentWidth, frame.contentHeight, original)
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        canvasSize = Size(it.width.toFloat(), it.height.toFloat())
                        state.onLayout(canvasSize, frame.contentWidth, frame.contentHeight, original)
                    },
            ) {
                drawSpread(leftPage.bitmap, rightPage.bitmap, frame, leftDetail, rightDetail, state)
            }
        }
    }
}

/** `ZoomableImage.drawFitted` 와 같은 변환 차례로 두 쪽을 그린다. 자리는 부동소수로 둔다(잘린 어긋남이 배율만큼 커진다). */
private fun DrawScope.drawSpread(
    left: ImageBitmap,
    right: ImageBitmap,
    frame: SpreadMath.Frame,
    leftDetail: DetailLayer?,
    rightDetail: DetailLayer?,
    state: ZoomState,
) {
    val (fw, fh) = ZoomMath.fittedSize(frame.contentWidth, frame.contentHeight, size.width, size.height)
    if (fw <= 0f || fh <= 0f) return
    translate(state.offset.x, state.offset.y) {
        scale(state.scale, pivot = center) {
            val x0 = (size.width - fw) / 2f
            val y0 = (size.height - fh) / 2f
            val lw = fw * frame.leftFraction
            drawPage(left, x0, y0, lw, fh, leftDetail)
            drawPage(right, x0 + lw, y0, fw - lw, fh, rightDetail)
        }
    }
}

private fun DrawScope.drawPage(bitmap: ImageBitmap, x: Float, y: Float, w: Float, h: Float, detail: DetailLayer?) {
    if (w <= 0f || h <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return
    translate(x, y) {
        scale(w / bitmap.width, h / bitmap.height, pivot = Offset.Zero) { drawImage(bitmap) }
    }
    if (detail != null && detail.image.width > 0 && detail.image.height > 0) {
        translate(x + detail.left * w, y + detail.top * h) {
            scale(detail.width * w / detail.image.width, detail.height * h / detail.image.height, pivot = Offset.Zero) {
                drawImage(detail.image)
            }
        }
    }
}

/**
 * 펼침의 한 쪽에 얹을 **선명화 조각**. `rememberRasterDetail` 과 같은 흐름(눈금으로 끊어 보고, 손가락이 멎기를 기다렸다가
 * 뜨고, 새 요청이 오면 앞의 것을 버린다)인데, 그 쪽을 **가운데 놓인 한 장처럼** 보이게 이동량을 옮겨서 묻는다
 * ([SpreadMath.pageCenterOffset]) — `RasterDetail.want` 의 좌표 약속이 '그림이 뷰포트 가운데에 있다' 이기 때문이다.
 */
@Composable
private fun rememberSpreadDetail(
    key: Any?,
    state: ZoomState,
    frame: SpreadMath.Frame,
    left: Boolean,
    baseWidth: Int,
    originalWidth: Int,
    originalHeight: Int,
    capBytes: Long,
    decode: suspend (RasterDetail.Want) -> Bitmap?,
): DetailLayer? {
    var layer by remember(key) { mutableStateOf<DetailLayer?>(null) }
    val decodeNow by rememberUpdatedState(decode)

    LaunchedEffect(key, state, frame, baseWidth, originalWidth, originalHeight, capBytes) {
        layer = null
        if (originalWidth <= 0 || originalHeight <= 0 || capBytes <= 0) return@LaunchedEffect
        snapshotFlow {
            val fw = state.fitted.width
            val pageWidth = if (left) fw * frame.leftFraction else fw * (1f - frame.leftFraction)
            RasterDetail.want(
                zoom = state.scale,
                viewportWidth = state.viewport.width,
                viewportHeight = state.viewport.height,
                fittedWidth = pageWidth,
                fittedHeight = state.fitted.height,
                offsetX = state.offset.x + state.scale * SpreadMath.pageCenterOffset(fw, frame.leftFraction, left),
                offsetY = state.offset.y,
                baseWidth = baseWidth,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                capBytes = capBytes,
            )
        }
            .distinctUntilChanged()
            .collectLatest { want ->
                if (want == null) {
                    layer = null
                    return@collectLatest
                }
                delay(DETAIL_DELAY_MS)
                val piece = decodeNow(want) ?: return@collectLatest
                layer = DetailLayer(
                    image = piece.asImageBitmap(),
                    left = want.left.toFloat() / originalWidth,
                    top = want.top.toFloat() / originalHeight,
                    width = want.width.toFloat() / originalWidth,
                    height = want.height.toFloat() / originalHeight,
                )
            }
    }
    return layer
}

/** 확대가 멎었다고 보는 시간. 한 장짜리(`rememberRasterDetail`)·PDF 타일과 같은 값이다. */
private const val DETAIL_DELAY_MS = 180L
