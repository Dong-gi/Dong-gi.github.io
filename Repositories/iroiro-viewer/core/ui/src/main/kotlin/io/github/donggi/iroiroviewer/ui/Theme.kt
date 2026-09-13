package io.github.donggi.iroiroviewer.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 앱의 테마. **화면마다 다시 정의하지 않는다.**
 *
 * minSdk 31 이라 다이나믹 컬러를 분기 없이 그냥 쓸 수 있다. 사용자의 배경화면에서
 * 뽑은 색이 들어오므로 우리가 색을 고르는 것보다 기기와 잘 어울린다.
 *
 * [forceDark] 는 미디어·이미지 뷰어용이다. 사진과 영상은 밝은 배경에서 테두리가
 * 번져 보여서, 시스템이 밝은 모드여도 뷰어만은 어둡게 둔다.
 */
@Composable
fun IroiroTheme(
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = forceDark || isSystemInDarkTheme()
    val colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // 상태표시줄 글자색을 배경에 맞춘다. 이것을 안 하면 밝은 배경에 흰 글자가 되어
            // 시각이 보이지 않는다.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
