package io.github.donggi.iroiroviewer.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 화면 방향 잠금. **세운 폰 하나와 그것을 감싸 도는 화살표 둘**이다.
 *
 * 10단계는 이 단추에 자물쇠(`Icons.Filled.Lock`)를 썼는데, VLC 를 쓰던 사람은 자물쇠를 **터치 잠금**으로 읽는다.
 * 터치 잠금을 더하면서 자물쇠는 그쪽에 주고 방향은 '도는 것' 으로 그린다 — 두 잠금이 같은 그림이면 어느 것이 무엇인지 이름을 읽어야
 * 안다. 코어 아이콘 세트에 화면 회전이 없어 직접 그린다(`PlaybackIcons` 와 같은 까닭). 켜짐은 색(`tint`)으로 말한다 — 섞기·반복과 같다.
 */
internal val RotationLockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RotationLock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 폰 — 모서리가 둥근 세로 직사각형(9..15 × 6..18).
            moveTo(10.2f, 6f)
            lineTo(13.8f, 6f)
            arcTo(1.2f, 1.2f, 0f, false, true, 15f, 7.2f)
            lineTo(15f, 16.8f)
            arcTo(1.2f, 1.2f, 0f, false, true, 13.8f, 18f)
            lineTo(10.2f, 18f)
            arcTo(1.2f, 1.2f, 0f, false, true, 9f, 16.8f)
            lineTo(9f, 7.2f)
            arcTo(1.2f, 1.2f, 0f, false, true, 10.2f, 6f)
            close()
            // 왼쪽 위 화살표 — 가운데(12,12)에서 반지름 9 의 호를 왼쪽에서 위쪽으로, 시계 방향으로 돈다.
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, false, true, 7.5f, 4.2f)
            moveTo(4.9f, 4f)
            lineTo(7.5f, 4.2f)
            lineTo(6.4f, 6.6f)
            // 오른쪽 아래 화살표 — 같은 원을 오른쪽에서 아래쪽으로.
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, false, true, 16.5f, 19.8f)
            moveTo(19.1f, 20f)
            lineTo(16.5f, 19.8f)
            lineTo(17.6f, 17.4f)
        }
    }.build()
}
