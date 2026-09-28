package io.github.donggi.iroiroviewer.comic

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 알림을 그 책이 열려 있는 동안만 띄운다(14단계).
 *
 * 다음 권은 뷰어를 닫지 않고 책을 바꾼다. 앞 책의 '처음부터' 가 새 책 위에 남아 있으면 그것을 누른 사람은 **새 책**을
 * 되감는다 — 화면 캡처로는 잡기 어려운 종류라(함정 표) 여기서 박는다. `SnackbarHostState` 는 화면 없이 도는 상태
 * 객체라 JVM 에서 그대로 쓴다.
 */
class BookSnackbarTest {

    private suspend fun SnackbarHostState.awaitShown() = withTimeout(5_000) {
        while (currentSnackbarData == null) delay(5)
    }

    @Test
    fun `책이 그대로면 누른 결과를 돌려준다`() = runBlocking {
        val host = SnackbarHostState()
        val result = async { host.showWhileCurrent({ true }, MutableStateFlow(0), "5쪽부터", "처음부터") }
        host.awaitShown()
        host.currentSnackbarData!!.performAction()
        assertEquals(SnackbarResult.ActionPerformed, withTimeout(5_000) { result.await() })
    }

    @Test
    fun `책이 바뀌면 떠 있던 알림을 걷고 아무것도 돌려주지 않는다`() = runBlocking {
        val host = SnackbarHostState()
        var current = true
        val changes = MutableStateFlow(0)
        val result = async { host.showWhileCurrent({ current }, changes, "5쪽부터", "처음부터") }
        host.awaitShown()
        // 다음 권을 열었다 — VM 은 책을 바꾸고 상태를 '여는 중' 으로 되돌린다.
        current = false
        changes.value = 1
        assertNull(withTimeout(5_000) { result.await() }, "바뀐 책에 '처음부터' 를 전하면 새 책이 되감긴다")
        assertNull(host.currentSnackbarData, "앞 책의 알림이 새 책 위에 남았다")
    }

    /** 늦게 배달된 알림(그 사이 책이 바뀌었다)은 띄우지도 않는다. */
    @Test
    fun `이미 바뀐 책의 알림은 띄우지 않는다`() = runBlocking {
        val host = SnackbarHostState()
        assertNull(host.showWhileCurrent({ false }, MutableStateFlow(0), "5쪽부터", "처음부터"))
        assertNull(host.currentSnackbarData)
    }

    /**
     * **닫은 책의 알림이 다시 연 같은 책에 배달되지 않는다.** 수집기(화면)는 뷰어를 닫으면 사라지지만 줄(VM)은 남는다.
     * 앞 알림을 띄우는 동안 줄을 선 '끝까지 읽었습니다. 다음 권' 이 남아 있다가, 같은 책을 다시 열어 1쪽을 보는 사람에게
     * 뜬다 — 경로가 같아 [showWhileCurrent] 로는 가려지지 않는다.
     */
    @Test
    fun `버린 알림은 다음 수집기에 배달되지 않는다`() = runBlocking {
        val events = BookEvents<String>()
        events.offer("앞 책의 이어보기")
        events.offer("앞 책의 다음 권")
        // 뷰어를 닫았다(수집기는 이미 없다) — VM 이 줄을 비운다.
        events.discardPending()
        events.offer("새로 연 책의 이어보기")
        assertEquals("새로 연 책의 이어보기", withTimeout(5_000) { events.flow.first() })
    }

    /** 버리지 않은 것은 그대로 배달된다 — 줄 자체는 버퍼를 든 채널이다. */
    @Test
    fun `버리지 않은 알림은 차례대로 배달된다`() = runBlocking {
        val events = BookEvents<Int>()
        events.offer(1)
        events.offer(2)
        assertEquals(listOf(1, 2), withTimeout(5_000) { events.flow.take(2).toList() })
    }

    /** 동작 단추를 붙인 알림의 기본 기간은 무기한이다(함정 표). 손으로 적은 값이 나가야 한다. */
    @Test
    fun `기간이 무기한이 아니다`() = runBlocking {
        val host = SnackbarHostState()
        val result = async { host.showWhileCurrent({ true }, MutableStateFlow(0), "다음 권", "열기") }
        host.awaitShown()
        assertEquals(SnackbarDuration.Long, host.currentSnackbarData!!.visuals.duration)
        host.currentSnackbarData!!.dismiss()
        assertEquals(SnackbarResult.Dismissed, withTimeout(5_000) { result.await() })
    }
}
