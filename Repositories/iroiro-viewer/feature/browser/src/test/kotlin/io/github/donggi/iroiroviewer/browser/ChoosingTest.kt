package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.FileOpEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 이름 칸의 실패를 어디서 말하는가, '전체 선택' 토글, 휴지통의 고르기.
 */
class ChoosingTest {

    /** 겹친 이름만 칸 안에서 말한다 — 칸에서 고칠 수 있는 것이 그것뿐이다. */
    @Test
    fun `이름이 겹쳤을 때만 칸을 열어 둔다`() {
        assertTrue(NameAttempt.keepsDialog(FileOpEngine.Reason.EXISTS))
        assertFalse(NameAttempt.keepsDialog(null), "성공하면 닫는다")
        for (r in FileOpEngine.Reason.entries - FileOpEngine.Reason.EXISTS) {
            assertFalse(NameAttempt.keepsDialog(r), "$r 는 칸을 닫고 스낵바로 말한다")
        }
    }

    @Test
    fun `전체 선택은 다시 누르면 모두 푼다`() {
        val all = listOf("/a", "/b", "/c")
        assertEquals(all.toSet(), SelectAll.toggled(setOf("/a"), all), "일부만 골랐으면 모두 고른다")
        assertEquals(emptySet(), SelectAll.toggled(all.toSet(), all), "모두 골랐으면 모두 푼다")
        assertTrue(SelectAll.allSelected(setOf("/a", "/b", "/c"), all))
        assertFalse(SelectAll.allSelected(setOf("/a"), all))
    }

    /** 보이는 것이 없으면 '모두 골랐다' 가 아니다 — 빈 목록에서 단추가 켜짐으로 보이면 거짓말이다. */
    @Test
    fun `빈 목록은 모두 고른 것이 아니다`() {
        assertFalse(SelectAll.allSelected(emptySet(), emptyList()))
        assertEquals(emptySet(), SelectAll.toggled(emptySet(), emptyList()))
    }

    /** 걸러 보이는 목록에서 '전체' 는 보이는 것이다 — 보이지 않는 옛 선택은 남기지 않는다(지울 때 보이지 않던 것이 함께 지워지지 않게). */
    @Test
    fun `전체 선택은 보이는 것만 고른다`() {
        assertEquals(setOf("/b", "/c"), SelectAll.toggled(setOf("/z"), listOf("/b", "/c")))
    }

    @Test
    fun `휴지통의 선택은 지금 있는 것만 남는다`() {
        assertEquals(setOf("u2"), TrashPick.present(setOf("u1", "u2"), listOf("u2", "u3")))
        assertEquals(emptySet(), TrashPick.present(setOf("u1"), emptyList()))
    }
}
