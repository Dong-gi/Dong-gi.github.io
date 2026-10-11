package io.github.donggi.iroiroviewer

import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.ui.ViewerWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 뷰어가 [ViewerWindow] 에 적어 둔 바람을 **이 액티비티의 창**에 건다(예전에는 '그림·만화·PDF 뷰어도 막대를 감춰도 시스템
 * 바가 남는다', '읽는 동안 화면이 꺼진다', '만화를 볼륨 키로 넘길 수 없다' 였다).
 *
 * ## 액티비티가 쥔다
 *
 * 재생 화면의 방향 잠금과 같은 모양이다(CLAUDE.md 함정 표 '액티비티의 설정을 컴포저블이 쥐게 하지 마라'). 화면은 바라는 것만
 * 적고, 여기서 `repeatOnLifecycle(STARTED)` 로 모아 창에 건다 — 화면이 설 때마다 수집이 처음부터 돌아 **지금 값**을 다시 건다.
 * 놓는 것은 [onStop] 이다: 창이 보이지 않는 동안 아무것도 쥐지 않는다(바를 되돌리고, 화면 켜 둠을 푼다). 뷰어를 닫으면 화면이
 * 바람을 거두므로([ViewerWindow] 의 표) 바는 반드시 돌아온다.
 *
 * ## 무엇을 거는가
 *
 * - **바의 아이콘 색** — 어두운 뷰어(그림·만화·문서)가 떠 있으면 밝게, 아니면 테마(밤 모드)를 따른다. 이 창의 테마는 아이콘을
 *   건드리지 않는다(`IroiroTheme(systemBarIcons = false)` — 회전마다 테마가 다시 그려지며 이 값을 덮었다). 3버튼 내비게이션의
 *   바탕(시스템이 대비를 위해 깔아 주는 막)도 그 색을 따라 어두워진다.
 * - **시스템 바 감추기** — 뷰어가 막대를 감추면 함께 감추고, 가장자리를 밀면 잠깐 보인다(`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
 *   뷰어의 막대는 인셋을 `…IgnoringVisibility` 로 재서 바가 드나들 때 출렁이지 않는다.
 * - **화면 켜 두기** — 읽는 화면(만화·문서·글)이면 마지막 조작([onUserInteraction]) 뒤 설정한 시간까지 창에
 *   `FLAG_KEEP_SCREEN_ON` 을 건다. 그 뒤로는 기기의 화면 꺼짐 시간을 따른다. 글 뷰어의 코드를 건드리지 않고 화면의 **종류**로
 *   정한다(`Root` 가 적는다).
 * - **볼륨 키** — 설정이 켜져 있고 만화가 떠 있으면 키를 삼켜 쪽을 넘긴다([onKey]). 키도 조작이다 — 액티비티의 `dispatchKeyEvent` 가
 *   [onUserInteraction] 을 먼저 부른다.
 */
internal class ViewerWindowHost(private val activity: ComponentActivity) {

    /** 화면이 바람을 적는 곳. [Root] 의 위에서 `LocalViewerWindow` 로 내려간다. */
    val window = ViewerWindow()

    /** 처음 쓸 때 만든다 — 이 객체는 액티비티의 필드로 만들어지고, 그때는 아직 컨텍스트가 붙지 않았다. */
    private val prefs by lazy { AppPreferences(activity.applicationContext) }

    /** 마지막 조작의 시각(`uptimeMillis`). 손가락이 닿거나 키를 누를 때마다 바뀐다. */
    private val lastInteraction = MutableStateFlow(SystemClock.uptimeMillis())

    /** 볼륨 키 넘기기 설정. 화면이 서 있는 동안만 읽어 둔다. */
    private var volumeKeys = false

    /** 지금 창에 `FLAG_KEEP_SCREEN_ON` 이 걸려 있는가 — 손가락이 닿을 때마다 창 속성을 다시 보내지 않게. */
    private var keepingOn = false

    /** [ComponentActivity.onCreate] 에서 한 번 부른다. */
    fun start() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { window.bars.collect(::applyBars) }
                launch { keepScreenOnWhileReading() }
                // 설정의 읽기 실패는 저장소가 기본값으로 넘긴다(`AppPreferences` — 흐름이 던지지 않는다).
                launch { prefs.comicVolumeKeys.collect { volumeKeys = it } }
            }
        }
    }

    /** [ComponentActivity.onUserInteraction] 에서 부른다 — 누름(손가락이 닿는 순간)·키마다 온다. */
    fun onUserInteraction() {
        lastInteraction.value = SystemClock.uptimeMillis()
    }

    /** [ComponentActivity.onStop] 에서 부른다. 창이 보이지 않는 동안 아무것도 쥐지 않는다(머리말). 다시 서면 수집이 다시 건다. */
    fun onStop() {
        controller().show(WindowInsetsCompat.Type.systemBars())
        applyIcons(dark = false)
        setKeepScreenOn(false)
        volumeKeys = false
    }

    /**
     * 키 하나 — 액티비티의 `onKeyDown`·`onKeyUp` 이 부른다. 삼켰으면 참이고, 그러면 창의 기본 처리(`PhoneWindow` 의 소리 조절)에
     * 닿지 않는다. **누름·뗌·되풀이를 모두 삼킨다**: 하나라도 시스템에 가면 소리 막대가 뜨거나 소리가 바뀐다. 쪽은 처음 누를 때
     * 한 번만 넘긴다(눌러 둔다고 쪽이 내달리지 않게).
     *
     * `dispatchKeyEvent` 를 덮어쓰지 않는 까닭: AndroidX 의 `ComponentActivity` 가 그 함수를 라이브러리 안에 묶어 두었다(lint
     * `RestrictedApi`). 뷰(컴포즈)가 볼륨 키를 쓰지 않으므로 `onKeyDown` 까지 그대로 내려온다.
     */
    fun onKey(event: KeyEvent): Boolean {
        val forward = VolumeKeys.forwardOf(event.keyCode) ?: return false
        if (!volumeKeys) return false
        val turner = window.pageTurner ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) turner(forward)
        return true
    }

    private fun applyBars(bars: ViewerWindow.Bars) {
        applyIcons(bars.dark)
        val c = controller()
        if (bars.hidden) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * 바의 아이콘을 밝게(어두운 바탕 위) 둘지. 어두운 뷰어거나 밤 모드면 밝게. 내비게이션 바도 함께다 — 3버튼 내비게이션의
     * 대비 막(`enableEdgeToEdge` 가 켜 둔 `isNavigationBarContrastEnforced`)이 이 값을 따라 희거나 검게 깔린다. 검은 뷰어 아래에
     * 흰 막이 깔리던 것이 '밝은 막대' 로 보였다.
     */
    private fun applyIcons(dark: Boolean) {
        val light = !dark && !systemDark()
        val c = controller()
        c.isAppearanceLightStatusBars = light
        c.isAppearanceLightNavigationBars = light
    }

    private fun systemDark(): Boolean =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun controller(): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    /**
     * 읽는 화면이 떠 있는 동안 — 마지막 조작 뒤 설정한 시간까지 화면을 켜 둔다. 조작이 올 때마다 잰 시간을 새로 시작한다
     * (`collectLatest`). 시간이 지나면 깃발을 풀고, 그 뒤로는 기기가 정한 꺼짐 시간을 따른다(이미 지났으면 곧 어두워진다).
     */
    private suspend fun keepScreenOnWhileReading() {
        combine(window.reading, prefs.readerKeepScreenMinutes) { reading, m -> if (reading) m else 0 }
            .distinctUntilChanged()
            .collectLatest { m ->
                if (m <= 0) {
                    setKeepScreenOn(false)
                    return@collectLatest
                }
                val limitMs = m * MINUTE_MS
                lastInteraction.collectLatest { last ->
                    val remaining = KeepAwake.remainingMs(SystemClock.uptimeMillis(), last, limitMs)
                    if (remaining > 0) {
                        setKeepScreenOn(true)
                        delay(remaining)
                    }
                    setKeepScreenOn(false)
                }
            }
    }

    private fun setKeepScreenOn(on: Boolean) {
        if (on == keepingOn) return
        keepingOn = on
        if (on) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}

/** 어느 화면이 창에 무엇을 바라는가(`Root` 가 적는다). 순수 함수라 JVM 시험이 답한다. */
internal object ViewerScreens {

    /**
     * 바의 아이콘을 밝게 둘 **어두운 뷰어**인가 — 검은(그림·만화) 또는 어두운 회색(PDF) 바탕이고, EPUB 의 막대도 반투명 검정이다.
     * 글·압축·목록은 테마의 바탕이라 테마를 따른다. `else` 를 두지 않는다 — 화면이 늘면 여기서 정해야 컴파일된다.
     */
    fun darkBars(screen: AppScreen): Boolean = when (screen) {
        is AppScreen.Viewer, is AppScreen.Comic, is AppScreen.Doc -> true
        is AppScreen.Text, is AppScreen.Edit, is AppScreen.Editor, is AppScreen.Archive, AppScreen.Browser, AppScreen.Settings,
        is AppScreen.Diag, is AppScreen.Notice -> false
    }

    /**
     * 읽는 화면(만화·문서·글)인가 — 화면 켜 두기가 걸린다. 사진은 읽는 화면이 아니다(한 장을 몇 분씩 보지 않는다). 글 편집기도
     * 아니다 — 치는 동안은 그 조작이 화면을 깨워 둔다(시스템의 시간 그대로). 편집기로 연 자리([AppScreen.Editor])는 편집기가 따로 된
     * 화면으로 앞에 있다 — 화면 켜 두기는 그 화면이 스스로 건다.
     */
    fun reading(screen: AppScreen): Boolean = when (screen) {
        is AppScreen.Comic, is AppScreen.Doc, is AppScreen.Text -> true
        is AppScreen.Viewer, is AppScreen.Edit, is AppScreen.Editor, is AppScreen.Archive, AppScreen.Browser, AppScreen.Settings,
        is AppScreen.Diag, is AppScreen.Notice -> false
    }
}

/** 볼륨 키 넘기기의 방향. 순수 함수라 JVM 시험이 답한다. */
internal object VolumeKeys {

    /** 아래 키는 다음 쪽(앞으로), 위 키는 앞 쪽. 볼륨 키가 아니면 null — 삼키지 않는다. 만화 뷰어들의 관행이다. */
    fun forwardOf(keyCode: Int): Boolean? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> true
        KeyEvent.KEYCODE_VOLUME_UP -> false
        else -> null
    }
}

/** 화면 켜 두기의 시간 셈. 순수 함수라 JVM 시험이 답한다. */
internal object KeepAwake {

    /** 마지막 조작([lastMs]) 뒤 [limitMs] 까지 남은 시간. 지났으면 0. 시계가 거꾸로 가도(같은 `uptimeMillis` 라 없어야 한다) 상한을 넘지 않는다. */
    fun remainingMs(nowMs: Long, lastMs: Long, limitMs: Long): Long {
        if (limitMs <= 0) return 0
        val idle = (nowMs - lastMs).coerceAtLeast(0)
        return (limitMs - idle).coerceIn(0, limitMs)
    }
}
