package io.github.donggi.iroiroviewer.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * 그리는 화면의 아이콘. Material 아이콘 **코어 세트에 되돌리기·다시 하기·형광펜·지우개·글자가 없다**(펜은 `Edit` 가 있다) — 확장 세트를
 * 들이면 이 몇 개를 위해 수천 개가 딸려 오므로 직접 그린다(`RotateRightIcon` 과 같은 까닭). 모양은 그리기 앱들이 쓰는 보편적인
 * 것이다(굽은 화살표·형광펜·곧은 선·T·지우개). 선으로만 그려 `Icon` 의 빛깔을 그대로 받는다.
 */

/** 되돌리기 — 왼쪽으로 돌아가는 굽은 화살표. */
internal val UndoIcon: ImageVector by lazy {
    icon("MarkupUndo") {
        moveTo(4.5f, 9f)
        lineTo(14.5f, 9f)
        arcTo(5f, 5f, 0f, false, true, 14.5f, 19f)
        lineTo(10f, 19f)
        moveTo(8.5f, 5f)
        lineTo(4.5f, 9f)
        lineTo(8.5f, 13f)
    }
}

/** 다시 하기 — [UndoIcon] 의 거울상. */
internal val RedoIcon: ImageVector by lazy {
    icon("MarkupRedo") {
        moveTo(19.5f, 9f)
        lineTo(9.5f, 9f)
        arcTo(5f, 5f, 0f, false, false, 9.5f, 19f)
        lineTo(14f, 19f)
        moveTo(15.5f, 5f)
        lineTo(19.5f, 9f)
        lineTo(15.5f, 13f)
    }
}

/** 형광펜 — 비스듬한 굵은 촉과, 그 밑에 그어진 반투명한 띠. */
internal val HighlighterIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MarkupHighlighter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            // 몸통(비스듬한 사각형)과 촉.
            moveTo(9f, 14.5f)
            lineTo(16.5f, 4f)
            lineTo(20f, 6.5f)
            lineTo(12.5f, 17f)
            close()
            moveTo(9f, 14.5f)
            lineTo(7.5f, 18f)
            lineTo(10.5f, 18.5f)
            lineTo(12.5f, 17f)
        }
        // 그어진 띠 — 반투명으로 형광펜임을 보인다.
        path(fill = SolidColor(Color.Black), fillAlpha = 0.45f) {
            moveTo(3f, 19.5f)
            lineTo(21f, 19.5f)
            lineTo(21f, 22.5f)
            lineTo(3f, 22.5f)
            close()
        }
    }.build()
}

/** 직선 — 두 끝에 점이 달린 곧은 선. */
internal val LineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MarkupLine",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round) {
            moveTo(5f, 19f)
            lineTo(19f, 5f)
        }
        path(fill = SolidColor(Color.Black)) {
            dot(5f, 19f, 2.3f)
            dot(19f, 5f, 2.3f)
        }
    }.build()
}

/** 글 메모 — 굵은 T. */
internal val TextIcon: ImageVector by lazy {
    icon("MarkupText") {
        moveTo(5f, 5.5f)
        lineTo(19f, 5.5f)
        moveTo(12f, 5.5f)
        lineTo(12f, 19f)
        moveTo(9f, 19f)
        lineTo(15f, 19f)
    }
}

/** 지우개 — 비스듬히 놓인 지우개와 바닥 선. */
internal val EraserIcon: ImageVector by lazy {
    icon("MarkupEraser") {
        moveTo(8.5f, 19.5f)
        lineTo(3.5f, 14.5f)
        lineTo(13f, 5f)
        lineTo(20.5f, 12.5f)
        lineTo(13.5f, 19.5f)
        close()
        moveTo(8f, 10f)
        lineTo(15.5f, 17.5f)
        moveTo(13.5f, 19.5f)
        lineTo(20.5f, 19.5f)
    }
}

/** 선 하나로 그린 아이콘. */
private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}.build()

/** 중심 ([cx], [cy]) 반지름 [r] 의 원 — 반원 둘. */
private fun PathBuilder.dot(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

private const val STROKE = 2f
