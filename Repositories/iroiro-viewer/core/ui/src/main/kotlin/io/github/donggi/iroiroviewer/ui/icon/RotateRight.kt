package io.github.donggi.iroiroviewer.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 오른쪽으로 회전.
 *
 * Material 아이콘 **코어 세트 48개에 회전 계열이 하나도 없다**(직접 세어 확인했다).
 * 확장 세트를 들이면 이 하나를 위해 수천 개가 딸려 오므로 직접 그린다 — 빈 별
 * (`StarOutline`)과 재생 바의 일시정지 아이콘도 같은 이유로 직접 그렸다.
 *
 * 모양은 '위가 트인 원호 + 오른쪽 끝의 화살촉' 이다. 회전 아이콘의 보편적인 형태이고,
 * 사람들이 이미 아는 모양을 새로 발명하지 않는다.
 */
val RotateRightIcon: ImageVector by lazy {
    val cx = 12f
    val cy = 12.5f
    val r = 7.5f

    ImageVector.Builder(
        name = "RotateRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 위쪽이 트인 원호. -60° 에서 시작해 시계 방향으로 300°.
            var first = true
            var deg = -60
            while (deg <= 240) {
                val a = Math.toRadians(deg.toDouble())
                val x = cx + r * cos(a).toFloat()
                val y = cy + r * sin(a).toFloat()
                if (first) {
                    moveTo(x, y)
                    first = false
                } else {
                    lineTo(x, y)
                }
                deg += 10
            }
        }
        // 화살촉. 원호가 끝나는 오른쪽 위를 향한다.
        path(
            fill = SolidColor(Color.Black),
        ) {
            val a = Math.toRadians(-60.0)
            val tipX = cx + r * cos(a).toFloat()
            val tipY = cy + r * sin(a).toFloat()
            moveTo(tipX + 2.6f, tipY - 0.6f)
            lineTo(tipX - 1.2f, tipY - 2.8f)
            lineTo(tipX - 1.0f, tipY + 2.4f)
            close()
        }
    }.build()
}
