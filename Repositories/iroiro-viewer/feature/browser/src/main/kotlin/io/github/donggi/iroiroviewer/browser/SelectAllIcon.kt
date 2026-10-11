package io.github.donggi.iroiroviewer.browser

import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * '전체 선택' 단추 — **누를 때마다 모두 고르고 모두 푸는 토글**.
 *
 * 예전 단추는 체크(✓) 아이콘이라 '다 골랐다(확인)' 로 읽혔고, 다시 눌러도 풀리지 않았다. 그림은 파일 관리자들이 '전체 선택' 에 쓰는
 * 모양(점선 네모)이고, **지금 상태를 그림으로도 말한다** — 모두 골랐으면 안쪽 네모가 채워진다(색 하나로만 말하지 않는다). 화면 낭독기에는
 * 토글로 읽힌다(`IconToggleButton` — '전체 선택, 선택됨').
 */
@Composable
fun SelectAllToggle(allSelected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconToggleButton(checked = allSelected, onCheckedChange = { onToggle() }, modifier = modifier) {
        Icon(
            imageVector = if (allSelected) SelectAllOnIcon else SelectAllOffIcon,
            contentDescription = stringResource(R.string.browser_select_all),
        )
    }
}

/** 점선 네모 — 네 귀퉁이와 변의 가운데 토막. 코어 아이콘 세트에 없어 직접 그린다(`StarOutlineIcon` 과 같은 까닭). */
private fun dashedFrame(name: String, innerFilled: Boolean): ImageVector =
    ImageVector.Builder(
        name = name,
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
            // 네 귀퉁이.
            moveTo(3f, 6.5f); lineTo(3f, 3f); lineTo(6.5f, 3f)
            moveTo(17.5f, 3f); lineTo(21f, 3f); lineTo(21f, 6.5f)
            moveTo(21f, 17.5f); lineTo(21f, 21f); lineTo(17.5f, 21f)
            moveTo(6.5f, 21f); lineTo(3f, 21f); lineTo(3f, 17.5f)
            // 변의 가운데 토막.
            moveTo(10.5f, 3f); lineTo(13.5f, 3f)
            moveTo(10.5f, 21f); lineTo(13.5f, 21f)
            moveTo(3f, 10.5f); lineTo(3f, 13.5f)
            moveTo(21f, 10.5f); lineTo(21f, 13.5f)
            // 안쪽 네모의 테두리 — 고른 상태에서는 아래에서 채운다.
            moveTo(8f, 8f); lineTo(16f, 8f); lineTo(16f, 16f); lineTo(8f, 16f); close()
        }
        if (innerFilled) {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 8f); lineTo(16f, 8f); lineTo(16f, 16f); lineTo(8f, 16f); close()
            }
        }
    }.build()

private val SelectAllOffIcon: ImageVector by lazy { dashedFrame("SelectAllOff", innerFilled = false) }
private val SelectAllOnIcon: ImageVector by lazy { dashedFrame("SelectAllOn", innerFilled = true) }
