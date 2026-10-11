package io.github.donggi.iroiroviewer.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 되돌리기 — 왼쪽으로 꺾여 돌아가는 화살표(↶). [RedoIcon] 은 그 거울상이다.
 *
 * Material 아이콘 코어 세트에는 되돌리기·다시 하기가 없다(`Undo`·`Redo` 는 확장 세트다 — 코어의 49개를 aar 로 셌다) — 이 둘을 위해
 * 수천 개를 들이지 않고 직접 그린다([RotateRightIcon]·[VisibilityIcon] 과 같은 까닭). 글 편집기(`feature:text`)에서 처음 그렸다.
 * 지금은 글 편집기만 쓰지만 아이콘은 여기 둔다(다른 화면이 되돌리기를 얻으면 같은 모양을 쓴다).
 */
val UndoIcon: ImageVector by lazy { turnArrow(mirrored = false) }

/** 다시 하기 — 오른쪽으로 꺾여 나아가는 화살표(↷). */
val RedoIcon: ImageVector by lazy { turnArrow(mirrored = true) }

private fun turnArrow(mirrored: Boolean): ImageVector {
    // 왼쪽(되돌리기) 기준으로 적고, 다시 하기는 가로로 뒤집는다(x → 24 − x).
    fun x(v: Float) = if (mirrored) 24f - v else v
    return ImageVector.Builder(
        name = if (mirrored) "Redo" else "Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 몸통 — 오른쪽 아래에서 위로 휘어 왼쪽으로 나아간다.
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(x(19f), 18f)
            curveTo(x(18.2f), 12.6f, x(14.2f), 9.5f, x(9f), 9.5f)
            lineTo(x(5f), 9.5f)
        }
        // 화살촉.
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(x(8.5f), 6f)
            lineTo(x(5f), 9.5f)
            lineTo(x(8.5f), 13f)
        }
    }.build()
}
