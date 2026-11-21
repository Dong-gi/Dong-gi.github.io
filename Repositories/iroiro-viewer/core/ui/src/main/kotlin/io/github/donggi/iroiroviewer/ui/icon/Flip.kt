package io.github.donggi.iroiroviewer.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 좌우 뒤집기 — 가운데 세로축과 그 양쪽에 마주 보는 세모(왼쪽은 채움, 오른쪽은 테두리). 상하 뒤집기는 이것을 90° 돌려 쓴다.
 *
 * 코어 세트에 뒤집기 아이콘이 없다([RotateRightIcon] 과 같은 까닭으로 직접 그린다). 모양은 흔히 쓰는 '축 + 거울에 비친 세모' 다.
 */
val FlipIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Flip",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 가운데 축 — 끊어 그려 '선' 이 아니라 '거울' 로 읽히게.
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
            var y = 3f
            while (y < 21f) {
                moveTo(12f, y)
                lineTo(12f, minOf(y + 2.2f, 21f))
                y += 4f
            }
        }
        // 왼쪽 세모(채움).
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.5f, 6f)
            lineTo(9.5f, 18f)
            lineTo(3f, 18f)
            close()
        }
        // 오른쪽 세모(테두리) — 왼쪽 것의 거울상.
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(14.5f, 6f)
            lineTo(14.5f, 18f)
            lineTo(21f, 18f)
            close()
        }
    }.build()
}
