package io.github.donggi.iroiroviewer.ui.image

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asImageBitmap
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 사진·만화 쪽의 **선명화 조각**을 쥔다. 확대하면 원본의 보이는 자리를 다시 떠서
 * [ZoomableImage] 의 `detail` 로 넘길 [DetailLayer] 를 준다.
 *
 * PDF 뷰어의 타일(`DocPageView`)과 같은 모양이다 — 확대 상태를 눈금으로 끊어 보고
 * ([RasterDetail.want]), 손가락이 멎기를 잠깐 기다렸다가 뜨고, 새 요청이 오면 앞의 것을
 * 버린다(`collectLatest`). 다른 것은 **다시 그리는 것이 아니라 원본을 다시 읽는다**는 것뿐이다.
 *
 * ## 조각은 캐시하지 않는다
 *
 * 한 장에 하나만 산다. 확대는 지금 보는 쪽에서만 일어나고(나머지 쪽은 배율이 1 로 되돌려진다),
 * 조각을 쌓아 두면 그만큼 예산이 는다. 밀려난 조각은 `recycle` 하지 않고 놓는다 — 아직 그리는
 * 프레임이 있을 수 있다(함정 표의 '`LruCache` 에서 밀려난 비트맵을 즉시 `recycle()` 하지 마라').
 *
 * @param key 이 조각이 속한 그림. 바뀌면 조각을 버린다.
 * @param baseWidth 바닥층 비트맵의 폭.
 * @param originalWidth·[originalHeight] 원본(화면 방향)의 화소. 모르면 0 — 조각을 뜨지 않는다.
 * @param capBytes 조각 한 장의 상한(`ImageLimits.Budget.detailCap`).
 * @param decode 화면 방향 사각형과 표본을 받아 **화면 방향으로 돌린** 조각을 준다. 못 뜨면 null.
 */
@Composable
fun rememberRasterDetail(
    key: Any?,
    state: ZoomState,
    baseWidth: Int,
    originalWidth: Int,
    originalHeight: Int,
    capBytes: Long,
    decode: suspend (RasterDetail.Want) -> Bitmap?,
): DetailLayer? {
    var layer by remember(key) { mutableStateOf<DetailLayer?>(null) }
    val decodeNow by rememberUpdatedState(decode)

    LaunchedEffect(key, state, baseWidth, originalWidth, originalHeight, capBytes) {
        layer = null
        if (originalWidth <= 0 || originalHeight <= 0 || capBytes <= 0) return@LaunchedEffect
        snapshotFlow {
            RasterDetail.want(
                zoom = state.scale,
                viewportWidth = state.viewport.width,
                viewportHeight = state.viewport.height,
                fittedWidth = state.fitted.width,
                fittedHeight = state.fitted.height,
                offsetX = state.offset.x,
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
                // 손가락이 멎기를 기다린다. 끄는 동안 프레임마다 원본을 읽으면 디코딩이
                // 줄을 서고, 그 줄이 밀리는 만큼 조각이 늦게 온다.
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

/** 확대가 멎었다고 보는 시간. PDF 타일과 같은 값이다. */
private const val DETAIL_DELAY_MS = 180L
