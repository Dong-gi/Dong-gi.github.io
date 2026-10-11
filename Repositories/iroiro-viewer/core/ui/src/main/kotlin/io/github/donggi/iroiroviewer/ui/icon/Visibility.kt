package io.github.donggi.iroiroviewer.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 눈 — 가린 글자를 **보이기**(비밀번호 칸의 '보기' 단추). [VisibilityOffIcon] 은 빗금을 그은 눈 — 다시 가리기.
 *
 * Material 아이콘 코어 세트에는 눈이 없다(`Visibility` 는 확장 세트다) — 이 하나를 위해 수천 개를 들이지 않고 직접 그린다
 * (`RotateRightIcon` 과 같은 까닭). 모양은 비밀번호 칸의 보편적인 눈(아몬드 윤곽 + 눈동자)이다.
 */
val VisibilityIcon: ImageVector by lazy { eye(slashed = false) }

/** 빗금을 그은 눈 — 보이는 글자를 다시 **가리기**. */
val VisibilityOffIcon: ImageVector by lazy { eye(slashed = true) }

private fun eye(slashed: Boolean): ImageVector = ImageVector.Builder(
    name = if (slashed) "VisibilityOff" else "Visibility",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // 윤곽 — 위아래 두 곡선이 양 끝에서 만난다.
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(2f, 12f)
        quadTo(12f, 2f, 22f, 12f)
        quadTo(12f, 22f, 2f, 12f)
        close()
    }
    // 눈동자.
    path(fill = SolidColor(Color.Black)) { circle(12f, 12f, 3.2f) }
    if (slashed) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(4f, 3.5f)
            lineTo(20f, 20.5f)
        }
    }
}.build()

/** 중심 ([cx], [cy]) 반지름 [r] 의 원 — 반원 둘. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
    close()
}
