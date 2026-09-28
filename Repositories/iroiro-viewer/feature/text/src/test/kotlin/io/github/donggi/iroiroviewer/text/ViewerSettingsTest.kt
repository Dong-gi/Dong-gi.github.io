package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 뷰어 설정이 **저장소에서 시작해 저장소로 돌아가는가**(14단계, T1). 회전과 '새로 열기' 를 입장 표로 가른다.
 */
class ViewerSettingsTest {

    private class FakeStore(
        var stored: TextViewerDefaults = TextViewerDefaults(),
        var loadDelay: Long = 0,
        var saveDelay: Long = 0,
    ) : TextViewerStore {
        val saves = ArrayList<TextViewerDefaults>()
        var loads = 0
        var failLoad = false
        var failSave = false

        /** 읽기를 시작한 순간의 값을 준다 — 느린 디스크에서 읽는 것처럼. */
        override suspend fun load(): TextViewerDefaults {
            val snapshot = stored
            delay(loadDelay)
            loads++
            if (failLoad) throw IOException("망가진 파일")
            return snapshot
        }

        override suspend fun save(value: TextViewerDefaults) {
            delay(saveDelay)
            if (failSave) throw IOException("디스크")
            stored = value
            saves += value
        }
    }

    private fun TestScope.settings(store: FakeStore) = ViewerSettings(this, store)

    @Test
    fun `처음 들어오면 저장된 값으로 시작한다`() = runTest {
        val store = FakeStore(TextViewerDefaults(wrap = true, lineNumbers = false, fontSp = 18, showLineEnds = true))
        val s = settings(store)
        assertTrue(s.enter(1L))
        // 읽는 동안은 null — 화면이 기본값으로 한 번 그렸다가 바뀌지 않게.
        assertNull(s.value.value)
        advanceUntilIdle()
        assertEquals(store.stored, s.value.value)
    }

    @Test
    fun `바꾸면 곧바로 보이고 저장된다`() = runTest {
        val store = FakeStore(saveDelay = 50)
        val s = settings(store)
        s.enter(1L)
        advanceUntilIdle()
        s.update { it.copy(wrap = true) }
        assertEquals(true, s.value.value?.wrap, "화면에는 곧바로")
        assertTrue(store.saves.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(TextViewerDefaults(wrap = true)), store.saves)
    }

    /** 회전은 같은 표로 다시 들어온다. 그때 다시 읽으면 **아직 쓰지 못한 값이 옛 값으로 되돌아간다.** */
    @Test
    fun `회전은 다시 읽지 않는다`() = runTest {
        val store = FakeStore(saveDelay = 1_000)
        val s = settings(store)
        s.enter(7L)
        advanceUntilIdle()
        s.update { it.copy(showLineEnds = true) }
        runCurrent()
        assertFalse(s.enter(7L), "같은 표는 새로 들어온 것이 아니다")
        assertEquals(true, s.value.value?.showLineEnds)
        advanceUntilIdle()
        assertEquals(1, store.loads)
        assertEquals(true, s.value.value?.showLineEnds)
    }

    /**
     * 바꾸자마자 파일을 닫고 다른 파일을 연다. 다시 읽기가 앞선 쓰기보다 먼저 돌면 **방금 바꾼 것이 사라진 채로** 열린다.
     * 쓰기와 읽기가 한 자물쇠를 먼저 온 순서로 지나야 한다.
     */
    @Test
    fun `새로 열면 앞선 쓰기가 끝난 뒤에 다시 읽는다`() = runTest {
        val store = FakeStore(saveDelay = 1_000)
        val s = settings(store)
        s.enter(1L)
        advanceUntilIdle()
        s.update { it.copy(fontSp = 20) }
        runCurrent()
        assertTrue(s.enter(2L))
        assertNull(s.value.value)
        advanceUntilIdle()
        assertEquals(20, s.value.value?.fontSp)
    }

    @Test
    fun `밖에서 바뀐 기본값은 다음 입장에서 보인다`() = runTest {
        val store = FakeStore()
        val s = settings(store)
        s.enter(1L)
        advanceUntilIdle()
        // 설정 화면 같은 다른 곳이 기본값을 바꿨다.
        store.stored = TextViewerDefaults(lineNumbers = false)
        s.enter(1L)
        advanceUntilIdle()
        assertEquals(true, s.value.value?.lineNumbers, "같은 입장에서는 그대로")
        s.enter(2L)
        advanceUntilIdle()
        assertEquals(false, s.value.value?.lineNumbers)
    }

    @Test
    fun `빠르게 여러 번 바꾸면 차례대로 쓰이고 마지막 값이 남는다`() = runTest {
        val store = FakeStore(saveDelay = 10)
        val s = settings(store)
        s.enter(1L)
        advanceUntilIdle()
        repeat(5) { s.update { it.copy(fontSp = it.fontSp + 1) } }
        advanceUntilIdle()
        assertEquals((14..18).toList(), store.saves.map { it.fontSp })
        assertEquals(18, store.stored.fontSp)
    }

    @Test
    fun `읽는 동안의 바꾸기는 무시한다`() = runTest {
        val store = FakeStore(loadDelay = 1_000)
        val s = settings(store)
        s.enter(1L)
        s.update { it.copy(wrap = true) }
        advanceUntilIdle()
        assertEquals(false, s.value.value?.wrap)
        assertTrue(store.saves.isEmpty())
    }

    @Test
    fun `늦게 끝난 옛 입장의 읽기는 버린다`() = runTest {
        val store = FakeStore(loadDelay = 1_000)
        val s = settings(store)
        s.enter(1L)
        runCurrent()
        store.stored = TextViewerDefaults(fontSp = 21)
        s.enter(2L)
        // 옛 입장의 읽기(기본값)가 끝났다. 그것을 얹으면 새 입장이 옛 값으로 한 번 그려진다.
        advanceTimeBy(1_001)
        runCurrent()
        assertNull(s.value.value)
        advanceUntilIdle()
        assertEquals(21, s.value.value?.fontSp)
    }

    @Test
    fun `저장소를 못 읽거나 못 써도 뷰어는 돈다`() = runTest {
        val store = FakeStore().apply { failLoad = true; failSave = true }
        val s = settings(store)
        s.enter(1L)
        advanceUntilIdle()
        assertEquals(TextViewerDefaults(), s.value.value)
        s.update { it.copy(wrap = true) }
        advanceUntilIdle()
        assertEquals(true, s.value.value?.wrap)
    }

    @Test
    fun `글자 크기는 범위 안으로 누른다`() = runTest {
        val store = FakeStore(TextViewerDefaults(fontSp = 3))
        val s = settings(store)
        s.enter(1L)
        advanceUntilIdle()
        assertEquals(TextViewerDefaults.MIN_FONT_SP, s.value.value?.fontSp)
        s.update { it.copy(fontSp = 99) }
        assertEquals(TextViewerDefaults.MAX_FONT_SP, s.value.value?.fontSp)
        // 끝에서 더 키우면 바뀐 것이 없으니 쓰지 않는다.
        s.update { it.copy(fontSp = it.fontSp + 1) }
        advanceUntilIdle()
        assertEquals(1, store.saves.size)
    }
}
