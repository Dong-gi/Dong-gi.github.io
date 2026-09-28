package io.github.donggi.iroiroviewer.comic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 만화 뷰어가 직접 그리는 아이콘.
 *
 * **Material 아이콘 코어 세트에 격자 모양이 없다**(코어는 49개 — CLAUDE.md 함정 표). `GridView`·`Apps` 는 확장 세트에만
 * 있고, 확장 세트는 들이지 않기로 했다. `List` 로 대신하면 '목록' 으로 읽혀 쪽 그림이 뜬다는 것이 전해지지 않는다.
 */
internal object ComicIcons {

    /** 쪽 목록 — 네 칸 격자. 24dp 판에 칸 8dp·틈 2dp·가장자리 3dp(Material 아이콘의 여백 관행). */
    val Grid: ImageVector by lazy {
        ImageVector.Builder(
            name = "ComicGrid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            for ((x, y) in listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f)) {
                moveTo(x, y)
                horizontalLineTo(x + 8f)
                verticalLineTo(y + 8f)
                horizontalLineTo(x)
                close()
            }
        }.build()
    }
}
