package io.github.donggi.iroiroviewer.image

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.ui.image.ImageIo
import io.github.donggi.iroiroviewer.ui.image.ImageProbe
import kotlin.math.roundToInt

/**
 * '잘라서 저장' 화면 — 그림 위에 사각형을 놓고 모서리·변을 끌어 고른다(사용자 요청 '지정한 직사각형 영역을 별도 저장').
 *
 * 관행을 따른다(구글 포토·삼성 갤러리의 자르기): 처음에는 그림 전체가 골라져 있고, 바깥은 어둡게, 사각형 안에는 삼등분 선, 네
 * 모서리에 ㄱ자 손잡이. 아래에 비율(자유·원본·1:1·4:3·3:4·16:9·9:16)과 고른 크기(원본 화소)가 있다.
 *
 * 좌표는 원본의 화면 방향 화소다([CropMath]). 화면에는 화면에 맞춰 줄여 뜬 그림을 그리고, 그 배율 하나로 손가락과 사각형을 옮긴다.
 * 저장하는 것은 원본 해상도다 — 뜨는 일은 큐가 돌 때 `ImageCropRenderer` 가 한다.
 *
 * **이 화면 안에서는 확대하지 않는다.** 큰 그림의 아주 작은 자리를 고르기는 어렵다 — 손가락 하나가 원본 화소 수십 개를 덮는다.
 * 남긴 빈자리다(CLAUDE.md).
 */
@Composable
internal fun CropScreen(
    path: String,
    probe: ImageProbe,
    onCancel: () -> Unit,
    onSave: (IntArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onCancel() }
    val w = probe.displayWidth.toFloat()
    val h = probe.displayHeight.toFloat()
    val density = LocalDensity.current
    // 화면에 맞게 뜰 크기 — 창의 긴 변(화소). 고르는 화면은 확대하지 않으므로 이만큼이면 된다.
    val window = LocalWindowInfo.current.containerSize
    val targetLongest = maxOf(window.width, window.height)
    // 뷰어의 바닥층과 같은 디코더로 화면에 맞게 뜬다 — 보이는 방향이 뷰어와 같다.
    val preview by produceState<ImageBitmap?>(null, path, targetLongest) {
        value = ImageIo.decodeFitted(path, targetLongest)?.asImageBitmap()
    }
    var box by remember(path) { mutableStateOf(CropMath.full(w, h)) }
    var ratio by remember(path) { mutableStateOf(CropRatio.FREE) }
    val areaDescription = stringResource(R.string.image_crop_area)

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // 아래 깔린 뷰어(페이저·막대)에 손가락이 닿지 않게 한다 — 포인터 노드가 없으면 히트 테스트가 형제를 지나 아래로 간다
            // (함정 표). 의미를 합치는 `clickable` 대신 빈 인식기를 단다 — 이 화면의 단추들이 낭독기에 따로 읽혀야 한다.
            .pointerInput(Unit) {}
            .safeDrawingPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, stringResource(R.string.image_crop_cancel), tint = Color.White)
            }
            Text(
                text = stringResource(R.string.image_crop_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSave(CropMath.toPixels(box, probe.displayWidth, probe.displayHeight)) }) {
                Text(
                    text = stringResource(R.string.image_crop_save),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val margin = with(density) { MARGIN.toPx() }
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            // 손잡이가 화면 끝에 붙지 않게 둘레를 비우고 맞춘다.
            val scale = minOf((boxW - 2 * margin) / w, (boxH - 2 * margin) / h).coerceAtLeast(MIN_SCALE)
            val dw = w * scale
            val dh = h * scale
            val x0 = (boxW - dw) / 2
            val y0 = (boxH - dh) / 2
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = areaDescription }
                    .pointerInput(scale, x0, y0, w, h) {
                        val reach = REACH.toPx() / scale
                        val minSide = MIN_SIDE.toPx() / scale
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val grip = CropMath.grip(box, (down.position.x - x0) / scale, (down.position.y - y0) / scale, reach)
                                ?: return@awaitEachGesture
                            down.consume()
                            val start = box
                            var dx = 0f
                            var dy = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val d = change.positionChange()
                                dx += d.x
                                dy += d.y
                                change.consume()
                                box = CropMath.drag(start, grip, dx / scale, dy / scale, w, h, ratio.value(w, h), minSide)
                            }
                        }
                    },
            ) {
                preview?.let { img ->
                    drawImage(
                        image = img,
                        dstOffset = IntOffset(x0.roundToInt(), y0.roundToInt()),
                        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                drawSelection(box, scale, x0, y0, dw, dh)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            for (r in CropRatio.entries) {
                FilterChip(
                    selected = ratio == r,
                    onClick = {
                        ratio = r
                        r.value(w, h)?.let { box = CropMath.withRatio(box, it, w, h) }
                    },
                    label = { Text(stringResource(r.label)) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
            // 처음처럼 — 그림 전체, 자유 비율.
            TextButton(onClick = {
                ratio = CropRatio.FREE
                box = CropMath.full(w, h)
            }) {
                Text(stringResource(R.string.image_crop_reset), color = Color.White)
            }
        }
        val px = CropMath.toPixels(box, probe.displayWidth, probe.displayHeight)
        Text(
            text = stringResource(R.string.image_crop_size, px[2] - px[0], px[3] - px[1]),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
        )
    }
}

/** 고른 자리를 그린다 — 바깥을 어둡게, 테두리·삼등분 선·ㄱ자 모서리. */
private fun DrawScope.drawSelection(box: CropMath.Box, scale: Float, x0: Float, y0: Float, dw: Float, dh: Float) {
    val l = x0 + box.left * scale
    val t = y0 + box.top * scale
    val r = x0 + box.right * scale
    val b = y0 + box.bottom * scale
    val dim = Color.Black.copy(alpha = DIM_ALPHA)
    drawRect(dim, Offset(x0, y0), Size(dw, t - y0))
    drawRect(dim, Offset(x0, b), Size(dw, y0 + dh - b))
    drawRect(dim, Offset(x0, t), Size(l - x0, b - t))
    drawRect(dim, Offset(r, t), Size(x0 + dw - r, b - t))

    val thin = 1.dp.toPx()
    val grid = Color.White.copy(alpha = GRID_ALPHA)
    for (i in 1..2) {
        val gx = l + (r - l) * i / 3
        val gy = t + (b - t) * i / 3
        drawLine(grid, Offset(gx, t), Offset(gx, b), thin)
        drawLine(grid, Offset(l, gy), Offset(r, gy), thin)
    }
    drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 2.dp.toPx()))

    // ㄱ자 모서리 — 테두리보다 굵게, 잡을 자리임을 보인다. 사각형이 작으면 팔을 줄인다.
    val arm = minOf(CORNER_ARM.toPx(), (r - l) / 2, (b - t) / 2)
    val thick = 4.dp.toPx()
    val c = Color.White
    fun corner(x: Float, y: Float, sx: Float, sy: Float) {
        drawLine(c, Offset(x, y), Offset(x + sx * arm, y), thick)
        drawLine(c, Offset(x, y), Offset(x, y + sy * arm), thick)
    }
    corner(l, t, 1f, 1f)
    corner(r, t, -1f, 1f)
    corner(l, b, 1f, -1f)
    corner(r, b, -1f, -1f)
}

/** 비율 고르기. [value] 는 폭/높이, 자유면 null. */
internal enum class CropRatio(val label: Int) {
    FREE(R.string.image_crop_ratio_free),
    ORIGINAL(R.string.image_crop_ratio_original),
    SQUARE(R.string.image_crop_ratio_square),
    FOUR_THREE(R.string.image_crop_ratio_4_3),
    THREE_FOUR(R.string.image_crop_ratio_3_4),
    SIXTEEN_NINE(R.string.image_crop_ratio_16_9),
    NINE_SIXTEEN(R.string.image_crop_ratio_9_16),
    ;

    fun value(w: Float, h: Float): Float? = when (this) {
        FREE -> null
        ORIGINAL -> if (h > 0f) w / h else null
        SQUARE -> 1f
        FOUR_THREE -> 4f / 3f
        THREE_FOUR -> 3f / 4f
        SIXTEEN_NINE -> 16f / 9f
        NINE_SIXTEEN -> 9f / 16f
    }
}

/** 그림 둘레의 여백 — 가장자리의 손잡이를 잡을 수 있게. */
private val MARGIN = 24.dp

/** 손가락이 손잡이를 잡는 거리. */
private val REACH = 24.dp

/** 고른 자리의 가장 작은 변(화면에서). */
private val MIN_SIDE = 32.dp

private val CORNER_ARM = 18.dp
private const val DIM_ALPHA = 0.6f
private const val GRID_ALPHA = 0.45f

/** 화면이 그림보다 작아질 수는 없다 — 0 으로 나누지 않게. */
private const val MIN_SCALE = 0.0001f
