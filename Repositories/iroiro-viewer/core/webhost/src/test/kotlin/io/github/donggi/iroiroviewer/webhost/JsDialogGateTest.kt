package io.github.donggi.iroiroviewer.webhost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 스크립트를 켠 쪽이 띄우는 대화상자를 세는 문([JsDialogGate]). 지키는 것은 하나다 — **대화상자를 끝없이 띄우는 쪽에서 사용자가
 * 빠져나올 길이 있다.** 대화상자는 화면 전체를 덮으므로, 이 문이 없으면 '확인' 을 누를 때마다 다음 것이 떠 원문으로 돌아갈 단추에
 * 손이 닿지 않는다.
 */
class JsDialogGateTest {

    @Test
    fun `처음 것에는 그만두기를 달지 않고 둘째부터 단다`() {
        val gate = JsDialogGate()
        assertTrue(gate.admit())
        assertFalse(gate.offersStop)
        assertTrue(gate.admit())
        assertTrue(gate.offersStop)
    }

    @Test
    fun `그만두면 그 뒤의 것을 모두 거절한다`() {
        val gate = JsDialogGate()
        gate.admit()
        gate.admit()
        gate.stop()
        repeat(100) { assertFalse(gate.admit()) }
        // 거절한 것은 세지 않는다.
        assertEquals(2, gate.shown)
    }
}
