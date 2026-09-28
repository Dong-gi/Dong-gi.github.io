package io.github.donggi.iroiroviewer.browser

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 목록이 재생 실패를 **맨 앞일 때만** 말하고 지우는가(`PlaybackNotice`). 진짜 `LifecycleRegistry` 와 `repeatOnLifecycle` 을
 * 그대로 돌린다 — 수명 주기의 알림으로 거두는 것이 이 규칙의 요점이라 판단 함수만 떼어 시험하면 그 요점을 보지 못한다.
 *
 * 액티비티의 차례는 이렇게 흉내 낸다: 목록 위에 재생 화면이 뜨는 동안 = STARTED(PAUSED), 다 덮었다 = CREATED(STOPPED),
 * 돌아왔다 = RESUMED. 스낵바는 [Snackbar] 가 흉내 낸다 — 보이는 동안 멈춰 있고, 시계가 다 되면 끝난다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackNoticeTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** 주 스레드 검사를 하지 않는 수명 주기(JVM 시험에는 루퍼가 없다). */
    private class Screen : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        fun at(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    /** 스낵바 흉내. 띄운 수·거둔 수를 세고, [timeOut] 으로 시계가 다 된 것을 흉내 낸다. */
    private class Snackbar {
        var shown = 0
        var withdrawn = 0
        private var showing: CompletableDeferred<Unit>? = null

        suspend fun show() {
            shown++
            val gate = CompletableDeferred<Unit>().also { showing = it }
            try {
                gate.await()
            } catch (e: CancellationException) {
                withdrawn++
                throw e
            }
        }

        fun timeOut() {
            showing?.complete(Unit)
        }
    }

    private fun TestScope.tell(screen: Screen, bar: Snackbar, pending: () -> Boolean = { true }, consumed: () -> Unit) =
        launch { PlaybackNotice.tellOnce(screen.lifecycle, pending, { bar.show() }, consumed) }

    @Test
    fun `목록이 맨 앞이면 말하고 끝까지 보인 뒤에 지운다`() = runTest(main) {
        val screen = Screen().apply { at(Lifecycle.State.RESUMED) }
        val bar = Snackbar()
        var consumed = 0
        val job = tell(screen, bar) { consumed++ }
        advanceUntilIdle()
        assertEquals(1, bar.shown)
        assertEquals(0, consumed, "보이는 동안에는 지우지 않는다")
        bar.timeOut()
        advanceUntilIdle()
        assertEquals(1, consumed)
        job.cancel()
    }

    @Test
    fun `재생 화면이 뜨는 동안의 목록(STARTED)은 말하지도 지우지도 않는다`() = runTest(main) {
        // 에뮬레이터에서 잡힌 결함의 차례 — 목록을 눌러 재생 화면을 여는 사이에 실패가 왔다. 목록은 아직 STARTED 다.
        val screen = Screen().apply { at(Lifecycle.State.STARTED) }
        val bar = Snackbar()
        var consumed = 0
        val job = tell(screen, bar) { consumed++ }
        advanceUntilIdle()
        bar.timeOut()
        advanceUntilIdle()
        assertEquals(0, bar.shown)
        assertEquals(0, consumed, "재생 화면이 보이는 실패를 목록이 지우면 그 화면의 문구와 단추가 사라진다")

        // 재생 화면이 목록을 다 덮었다.
        screen.at(Lifecycle.State.CREATED)
        advanceUntilIdle()
        assertEquals(0, bar.shown)

        // 돌아왔다 — 이제 한 번 말하고, 끝까지 보인 뒤 지운다.
        screen.at(Lifecycle.State.RESUMED)
        advanceUntilIdle()
        assertEquals(1, bar.shown)
        bar.timeOut()
        advanceUntilIdle()
        assertEquals(1, consumed)
        job.cancel()
    }

    @Test
    fun `말하는 도중에 재생 화면이 뜨면 거두고 지우지 않고 돌아오면 다시 말한다`() = runTest(main) {
        val screen = Screen().apply { at(Lifecycle.State.RESUMED) }
        val bar = Snackbar()
        var consumed = 0
        val job = tell(screen, bar) { consumed++ }
        advanceUntilIdle()
        assertEquals(1, bar.shown)

        // 미니 바를 눌러 재생 화면을 열었다(목록 PAUSED).
        screen.at(Lifecycle.State.STARTED)
        advanceUntilIdle()
        assertEquals(1, bar.withdrawn, "가려지는 목록의 스낵바는 거둔다")
        // 거둔 뒤에 시계가 다 된 것처럼 굴어도 지우지 않는다.
        bar.timeOut()
        advanceUntilIdle()
        assertEquals(0, consumed)

        screen.at(Lifecycle.State.CREATED)
        screen.at(Lifecycle.State.RESUMED)
        advanceUntilIdle()
        assertEquals(2, bar.shown, "돌아오면 아직 남은 실패를 한 번 말한다")
        bar.timeOut()
        advanceUntilIdle()
        assertEquals(1, consumed)
        job.cancel()
    }

    @Test
    fun `한 번 끝까지 말했으면 다시 서도 말하지 않는다`() = runTest(main) {
        val screen = Screen().apply { at(Lifecycle.State.RESUMED) }
        val bar = Snackbar()
        var consumed = 0
        val job = tell(screen, bar) { consumed++ }
        advanceUntilIdle()
        bar.timeOut()
        advanceUntilIdle()
        // 화면이 지워진 실패를 다시 그려 이 일을 거두기 전에 한 번 내려갔다 올라왔다.
        screen.at(Lifecycle.State.STARTED)
        screen.at(Lifecycle.State.RESUMED)
        advanceUntilIdle()
        assertEquals(1, bar.shown)
        assertEquals(1, consumed)
        job.cancel()
    }

    @Test
    fun `떠나 있는 동안 지워진 실패는 돌아와도 말하지 않는다`() = runTest(main) {
        // 재생 화면에서 다른 곡을 틀면 커넥션이 실패를 지운다. 목록의 화면은 돌아온 뒤 한 프레임 동안 옛 값을 든다.
        var stillThere = true
        val screen = Screen().apply { at(Lifecycle.State.CREATED) }
        val bar = Snackbar()
        var consumed = 0
        val job = tell(screen, bar, pending = { stillThere }) { consumed++ }
        advanceUntilIdle()
        stillThere = false
        screen.at(Lifecycle.State.RESUMED)
        advanceUntilIdle()
        assertEquals(0, bar.shown)
        assertEquals(0, consumed)
        job.cancel()
    }

    @Test
    fun `말하는 사이에 다른 실패로 바뀌었으면 끝나도 지우지 않는다`() = runTest(main) {
        // 지우는 함수(`consumeFailure`)는 커넥션의 실패를 무엇이든 지운다. 스낵바가 끝나는 순간 새 실패가 이미 와 있고
        // 화면이 이 일을 아직 거두지 않았으면(한 프레임), 말한 적 없는 새 실패를 지운다 — 목록도, 뒤에 여는 재생 화면도 못 말한다.
        var same = true
        val screen = Screen().apply { at(Lifecycle.State.RESUMED) }
        val bar = Snackbar()
        var consumed = 0
        val job = tell(screen, bar, pending = { same }) { consumed++ }
        advanceUntilIdle()
        assertEquals(1, bar.shown)
        same = false
        bar.timeOut()
        advanceUntilIdle()
        assertEquals(0, consumed, "말하지 않은 실패를 지우면 목록도 재생 화면도 그것을 말하지 못한다")
        job.cancel()
    }

    @Test
    fun `맨 앞은 RESUMED 다`() {
        // 목록의 수집(`collectAsStateWithLifecycle`)도 이 값을 쓴다. STARTED 로 되돌리면 결함이 돌아온다.
        assertEquals(Lifecycle.State.RESUMED, PlaybackNotice.IN_FRONT)
    }
}
