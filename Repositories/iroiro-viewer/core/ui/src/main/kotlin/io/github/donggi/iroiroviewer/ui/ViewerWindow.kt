package io.github.donggi.iroiroviewer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 뷰어가 **창**에 바라는 것 — 시스템 바를 감출까, 바의 아이콘을 밝게 둘까, 화면을 켜 둘까, 볼륨 키로 쪽을 넘길까.
 *
 * ## 바라는 것만 적고, 거는 것은 액티비티다
 *
 * 창의 설정을 컴포저블이 직접 걸면, 화면이 정지한 동안 컴포지션이 다시 그려지지 않아 **보이지도 않는 화면이 옛 값을 쥐고 있다**
 * (CLAUDE.md 함정 표 '액티비티의 설정을 컴포저블이 쥐게 하지 마라' — 재생 화면의 방향 잠금이 겪었다). 그래서 화면은 여기에 바라는
 * 것만 적고, 액티비티(`app` 의 `ViewerWindowHost`)가 `repeatOnLifecycle(STARTED)` 로 그것을 창에 걸고 `onStop` 에서 놓는다 — 재생
 * 화면의 방향 잠금과 같은 모양이다. 액티비티마다 하나이고 [LocalViewerWindow] 로 내려간다. 주지 않은 곳(미리보기·시험)에서는 null 이라
 * 아무것도 하지 않는다.
 *
 * | 바람 | 누가 적는가 | 액티비티가 하는 일 |
 * |---|---|---|
 * | 어두운 뷰어가 떠 있다 | 화면 전이(`Root`) | 상태 표시줄·내비게이션 바의 아이콘을 밝게 — 검은 바탕 위의 어두운 시계는 거의 보이지 않는다 |
 * | 막대를 감췄다 | 그림·만화·PDF 뷰어([SystemBarsHidden]) | 시스템 바도 감춘다. 가장자리를 밀면 잠깐 보인다 |
 * | 읽는 중이다 | 화면 전이(`Root` — 만화·문서·글) | 마지막 조작 뒤 설정한 시간까지 화면을 켜 둔다 |
 * | 볼륨 키로 넘길 곳 | 만화 뷰어([VolumeKeyPaging]) | 설정이 켜져 있으면 볼륨 키를 삼켜 쪽을 넘긴다 |
 */
@Stable
class ViewerWindow {

    /** 시스템 바에 바라는 것. */
    data class Bars(
        /** 어두운 뷰어가 떠 있다 — 바의 아이콘을 밝게 둔다. */
        val dark: Boolean = false,
        /** 뷰어의 막대를 감췄다 — 시스템 바도 감춘다. */
        val hidden: Boolean = false,
    )

    private val _bars = MutableStateFlow(Bars())
    val bars: StateFlow<Bars> = _bars.asStateFlow()

    private val _reading = MutableStateFlow(false)

    /** 읽는 화면(만화·문서·글)이 떠 있다. 화면 켜 두기가 본다. */
    val reading: StateFlow<Boolean> = _reading.asStateFlow()

    /**
     * 볼륨 키로 쪽을 넘길 곳(만화 뷰어가 떠 있는 동안만 — [VolumeKeyPaging]). null 이면 볼륨 키는 소리다. 주 스레드에서만
     * 읽고 쓴다(컴포지션의 효과와 액티비티의 키 처리).
     */
    var pageTurner: ((forward: Boolean) -> Unit)? = null
        internal set

    /** 지금 화면의 종류가 바라는 것 — 화면 전이가 화면이 바뀔 때마다 적는다. */
    fun setScreen(darkBars: Boolean, reading: Boolean) {
        _bars.update { it.copy(dark = darkBars) }
        _reading.value = reading
    }

    internal fun setHidden(hidden: Boolean) {
        _bars.update { it.copy(hidden = hidden) }
    }
}

/** 이 액티비티의 [ViewerWindow]. 액티비티가 내려 준다. */
val LocalViewerWindow = staticCompositionLocalOf<ViewerWindow?> { null }

/**
 * 뷰어의 막대를 감춘 동안 **시스템 바도 감춰 달라**고 적는다. 화면이 컴포지션에서 빠지면(뷰어를 닫았다) 거둔다 — 바는 반드시
 * 돌아온다. 감추고 되돌리는 것은 액티비티다([ViewerWindow]).
 */
@Composable
fun SystemBarsHidden(hidden: Boolean) {
    val window = LocalViewerWindow.current ?: return
    SideEffect { window.setHidden(hidden) }
    DisposableEffect(window) {
        onDispose { window.setHidden(false) }
    }
}

/**
 * 이 화면이 떠 있는 동안 **볼륨 키로 쪽을 넘길 곳**을 둔다(만화). 키를 삼킬지는 액티비티가 설정을 보고 정한다
 * (기본은 끔 — 소리 조절을 빼앗지 않는다). [onTurn] 의 `forward` 는 읽는 차례로 앞(다음 쪽)이면 참이다.
 */
@Composable
fun VolumeKeyPaging(onTurn: (forward: Boolean) -> Unit) {
    val window = LocalViewerWindow.current ?: return
    val latest by rememberUpdatedState(onTurn)
    DisposableEffect(window) {
        val turner: (Boolean) -> Unit = { latest(it) }
        window.pageTurner = turner
        onDispose { if (window.pageTurner === turner) window.pageTurner = null }
    }
}
