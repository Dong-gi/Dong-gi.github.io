package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.io.TextSaver
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 뷰어의 '편집' 단추([TextEditGate]) — 고칠 수 없는 자리는 숨기고, 크기 때문이면 두고 까닭을 말한다. 그리고 뷰어가
 * 편집기의 저장 뒤에 다시 읽는 표([TextSaves]).
 */
class TextEditGateTest {

    @Test
    fun `저장 볼륨의 쓸 수 있는 파일이면 연다`() {
        assertEquals(TextEditGate.Open, TextEditGate.of(TextSaver.Place.OK, 1_000, TextEncoding.UTF_8))
        assertEquals(TextEditGate.Open, TextEditGate.of(TextSaver.Place.OK, TextLimits.EDIT_MAX_BYTES, TextEncoding.CP949))
    }

    @Test
    fun `고칠 수 없는 자리는 숨긴다 — 받은 사본·원격 사본·휴지통·읽기 전용`() {
        for (place in TextSaver.Place.entries - TextSaver.Place.OK) {
            assertEquals(TextEditGate.Hidden, TextEditGate.of(place, 10, TextEncoding.UTF_8), place.name)
        }
    }

    @Test
    fun `크기 때문이면 숨기지 않고 상한을 말한다`() {
        val gate = TextEditGate.of(TextSaver.Place.OK, TextLimits.EDIT_MAX_BYTES + 1, TextEncoding.UTF_8)
        assertEquals(TextEditGate.TooLarge(TextLimits.EDIT_MAX_BYTES), gate)
        // 자리가 먼저다 — 앱 캐시의 큰 파일은 '크다' 가 아니라 숨긴다.
        assertEquals(
            TextEditGate.Hidden,
            TextEditGate.of(TextSaver.Place.OUTSIDE, TextLimits.EDIT_MAX_BYTES + 1, TextEncoding.UTF_8),
        )
    }

    @Test
    fun `이 앱이 고르는 인코딩은 모두 다시 쓸 수 있다`() {
        for (encoding in TextEncoding.userChoices) {
            if (encoding.charset == null) continue
            assertTrue(TextEditGate.writable(encoding), encoding.label)
        }
    }

    @Test
    fun `편집기가 저장한 파일은 뷰어가 한 번 다시 읽는다`() {
        val path = "/storage/emulated/0/gate-test.txt"
        assertNull(TextSaves.take(path))
        TextSaves.record(path, TextEncoding.UTF_8)
        assertEquals(TextEncoding.UTF_8, TextSaves.take(path))
        assertNull(TextSaves.take(path))
    }
}
