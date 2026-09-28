package io.github.donggi.iroiroviewer.settings

import io.github.donggi.iroiroviewer.data.CrashLog
import io.github.donggi.iroiroviewer.data.ReaderAppearance
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.SortSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 설정 화면의 논리. 저장소·기록 표·크래시 기록을 가짜로 바꿔 끼우고 본다 — 화면이 부르는 함수가 **맞는 칸을, 맞는 범위로**
 * 적는가, 실패가 앱을 죽이지 않고 `false` 로 돌아오는가.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSettings : SettingsSource {
        override val showHidden = MutableStateFlow(false)
        override val sortSpec = MutableStateFlow(SortSpec(SortKey.DATE, ascending = false, foldersFirst = true))
        override val textViewer = MutableStateFlow(TextViewerDefaults())
        override val comicDefaultDirection = MutableStateFlow(0)
        override val readerAppearance = MutableStateFlow(ReaderAppearance())
        var fail: Exception? = null
        val calls = mutableListOf<String>()

        private fun guard(name: String) {
            calls += name
            fail?.let { throw it }
        }

        override suspend fun setShowHidden(show: Boolean) {
            guard("hidden")
            showHidden.value = show
        }

        override suspend fun setFoldersFirst(first: Boolean) {
            guard("foldersFirst")
            // 실물(AppPreferences.setFoldersFirst)처럼 그 칸만 바꾼다.
            sortSpec.value = sortSpec.value.copy(foldersFirst = first)
        }

        override suspend fun updateTextViewer(transform: (TextViewerDefaults) -> TextViewerDefaults) {
            guard("text")
            textViewer.value = transform(textViewer.value)
        }

        override suspend fun setComicDefaultDirection(code: Int) {
            guard("comic")
            comicDefaultDirection.value = code
        }

        override suspend fun updateReaderAppearance(transform: (ReaderAppearance) -> ReaderAppearance) {
            guard("reader")
            readerAppearance.value = transform(readerAppearance.value)
        }
    }

    private class FakeHistory : HistorySource {
        val cleared = mutableListOf<HistoryKind>()
        var fail: Exception? = null
        override suspend fun clear(kind: HistoryKind) {
            fail?.let { throw it }
            cleared += kind
        }
    }

    private class FakeCrashes(var count: Int = 0) : CrashSource {
        var exportFile: File? = null
        var clearWorks = true
        override fun summary() = if (count == 0) CrashLog.Summary.EMPTY else CrashLog.Summary(count, 1_000L * count)
        override fun export() = exportFile
        override fun clear(): Boolean {
            if (clearWorks) count = 0
            return clearWorks
        }
    }

    private val settings = FakeSettings()
    private val history = FakeHistory()
    private val crashes = FakeCrashes(count = 3)
    private fun vm() = SettingsViewModel(settings, history, crashes, io = dispatcher)

    @Test
    fun `저장된 값을 한 벌로 모아 보인다`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.values.collect { } }
        val v = vm.values.filterNotNull().first()
        assertEquals(SettingsViewModel.Values(false, true, TextViewerDefaults(), 0, ReaderAppearance()), v)
        // 다른 화면(목록)이 바꾼 값도 따라온다 — 이 화면은 사본을 들지 않는다.
        settings.showHidden.value = true
        assertEquals(true, vm.values.value?.showHidden)
        job.cancel()
    }

    @Test
    fun `폴더 먼저는 정렬 기준과 방향을 건드리지 않는다`() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.setFoldersFirst(false).await())
        assertEquals(SortSpec(SortKey.DATE, ascending = false, foldersFirst = false), settings.sortSpec.value)
        assertEquals(listOf("foldersFirst"), settings.calls)
    }

    @Test
    fun `텍스트 뷰어 값은 한 칸씩 바뀌고 글자 크기는 범위 안으로 눌린다`() = runTest(dispatcher) {
        val vm = vm()
        vm.setTextWrap(true).await()
        vm.setTextLineNumbers(false).await()
        vm.setTextLineEnds(true).await()
        vm.setTextFontSp(99).await()
        assertEquals(TextViewerDefaults(wrap = true, lineNumbers = false, fontSp = TextViewerDefaults.MAX_FONT_SP, showLineEnds = true),
            settings.textViewer.value)
        vm.setTextFontSp(3).await()
        assertEquals(TextViewerDefaults.MIN_FONT_SP, settings.textViewer.value.fontSp)
    }

    @Test
    fun `문서와 만화의 값은 범위 안으로 눌린다`() = runTest(dispatcher) {
        val vm = vm()
        vm.setReaderFontPercent(250).await()
        vm.setReaderMargin(9).await()
        vm.setReaderTheme(ReaderAppearance.THEME_SEPIA).await()
        assertEquals(ReaderAppearance(ReaderAppearance.MAX_FONT_PERCENT, ReaderAppearance.MARGIN_WIDE, ReaderAppearance.THEME_SEPIA),
            settings.readerAppearance.value)
        vm.setComicDirection(1).await()
        assertEquals(1, settings.comicDefaultDirection.value)
        vm.setComicDirection(5).await()
        assertEquals(2, settings.comicDefaultDirection.value)
    }

    @Test
    fun `쓰기가 실패하면 앱을 죽이지 않고 false 로 돌아온다`() = runTest(dispatcher) {
        val vm = vm()
        settings.fail = IOException("저장공간 부족")
        assertFalse(vm.setShowHidden(true).await())
        assertFalse(vm.setReaderTheme(1).await())
        assertEquals(false, settings.showHidden.value)
    }

    @Test
    fun `취소는 삼키지 않는다`() = runTest(dispatcher) {
        val vm = vm()
        settings.fail = CancellationException("취소")
        val result = vm.setShowHidden(true)
        assertTrue(result.isCancelled, "취소가 false 로 바뀌면 취소된 코루틴이 끝까지 달린다")
    }

    @Test
    fun `기록 둘은 저마다의 갈래로 지운다`() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.clearHistory(HistoryKind.PLAYBACK).await())
        assertTrue(vm.clearHistory(HistoryKind.READING).await())
        assertEquals(listOf(HistoryKind.PLAYBACK, HistoryKind.READING), history.cleared)
        history.fail = IllegalStateException("SQLite")
        assertFalse(vm.clearHistory(HistoryKind.READING).await())
    }

    @Test
    fun `크래시 기록은 건수와 시각만 들고, 지우면 0 이 된다`() = runTest(dispatcher) {
        val vm = vm()
        assertNull(vm.crash.value)
        vm.refreshCrashes().await()
        assertEquals(CrashLog.Summary(3, 3_000L), vm.crash.value)
        assertTrue(vm.clearCrashes().await())
        assertEquals(CrashLog.Summary.EMPTY, vm.crash.value)
    }

    @Test
    fun `크래시 기록을 다 지우지 못하면 실패로 알리고 남은 건수를 보인다`() = runTest(dispatcher) {
        val vm = vm()
        crashes.clearWorks = false
        assertFalse(vm.clearCrashes().await())
        assertEquals(3, vm.crash.value?.count)
    }

    @Test
    fun `공유 사본이 없으면 null`() = runTest(dispatcher) {
        val vm = vm()
        assertNull(vm.exportCrashes())
        crashes.exportFile = File("x.txt")
        assertEquals(File("x.txt"), vm.exportCrashes())
    }

    @Test
    fun `고지에 다녀오면 스크롤 자리가 돌아오고, 한 번 꺼내면 잊는다`() {
        val vm = vm()
        assertEquals(0 to 0, vm.takeScroll())
        vm.rememberScroll(17, 42)
        assertEquals(17 to 42, vm.takeScroll())
        assertEquals(0 to 0, vm.takeScroll(), "뒤로 나갔다 다시 열면 맨 위")
    }

    @Test
    fun `슬라이더 값은 칸에 붙는다 — 반은 올림`() {
        assertEquals(100, SettingsOptions.snap(104.9f, 70, 200, 10))
        assertEquals(110, SettingsOptions.snap(105f, 70, 200, 10))
        assertEquals(70, SettingsOptions.snap(10f, 70, 200, 10))
        assertEquals(200, SettingsOptions.snap(260f, 70, 200, 10))
        assertEquals(13, SettingsOptions.snap(12.5f, 9, 22, 1))
        assertEquals(12, SettingsOptions.sliderSteps(70, 200, 10))
        assertEquals(12, SettingsOptions.sliderSteps(9, 22, 1))
    }

    @Test
    fun `고르는 값의 표는 저장소의 값과 하나씩 맞는다`() {
        assertEquals(listOf(0, 1, 2), SettingsOptions.comicDirections.map { it.code })
        assertEquals(
            listOf(ReaderAppearance.MARGIN_NARROW, ReaderAppearance.MARGIN_NORMAL, ReaderAppearance.MARGIN_WIDE),
            SettingsOptions.readerMargins.map { it.code },
        )
        assertEquals(
            listOf(ReaderAppearance.THEME_SYSTEM, ReaderAppearance.THEME_LIGHT, ReaderAppearance.THEME_DARK, ReaderAppearance.THEME_SEPIA),
            SettingsOptions.readerThemes.map { it.code },
        )
        // 모르는 값이면 첫 항목의 이름.
        assertEquals(SettingsOptions.readerThemes.first().label, SettingsOptions.labelOf(SettingsOptions.readerThemes, 42))
    }
}
