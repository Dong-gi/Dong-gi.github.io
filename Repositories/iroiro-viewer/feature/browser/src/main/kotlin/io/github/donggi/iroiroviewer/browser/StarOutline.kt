package io.github.donggi.iroiroviewer.browser

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
 * 빈 별.
 *
 * Material 아이콘 **코어 세트에 `StarBorder` 가 없다**(채워진 `Star` 만 있고,
 * `Outlined.Star` 는 이름과 달리 같은 채워진 모양이다). 확장 세트를 들이면 아이콘
 * 수천 개가 딸려 오므로 — 이 프로젝트는 코어만 쓰기로 했다 — 다섯 꼭짓점을 계산해
 * 직접 그린다. 재생 바의 일시정지 아이콘도 같은 이유로 직접 그렸다.
 *
 * 색만 바꾸고 모양은 같게 두면 즐겨찾기 여부가 **색 하나로만** 구분된다. 그것은 색을
 * 가려내기 어려운 사람에게 표시가 없는 것과 같다.
 */
val StarOutlineIcon: ImageVector by lazy {
    val cx = 12f
    val cy = 12f
    val outer = 9.2f
    val inner = 3.9f

    ImageVector.Builder(
        name = "StarOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            // 채우지 않는다. 채우면 Star 와 구분되지 않는다.
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) outer else inner
                // 위 꼭짓점에서 시작해 36도씩. 화면 좌표는 y 가 아래로 자란다.
                val a = Math.toRadians((-90 + 36 * k).toDouble())
                val x = cx + r * cos(a).toFloat()
                val y = cy + r * sin(a).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }.build()
}
