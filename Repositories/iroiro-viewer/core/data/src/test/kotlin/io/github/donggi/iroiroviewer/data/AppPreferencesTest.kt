package io.github.donggi.iroiroviewer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.SortSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 설정 저장소 — **진짜 DataStore**(JVM 파일)로 본다. 뷰어 넷(텍스트·만화·문서·목록)이 이 값을 그대로 쓰므로, 범위를 벗어난
 * 값이 새어 나가면 화면이 깨지고, 한 칸만 바꾸는 쓰기가 다른 칸을 되돌리면 사용자가 바꾼 것이 조용히 사라진다.
 */
class AppPreferencesTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "iroiro-prefs-${System.nanoTime()}").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
    private val prefs = AppPreferences(store)

    @AfterTest
    fun cleanup() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `아무것도 적지 않았으면 기본값이다`() = runTest {
        assertEquals(TextViewerDefaults(), prefs.textViewer.first())
        assertEquals(ReaderAppearance(), prefs.readerAppearance.first())
        assertEquals(0, prefs.comicDefaultDirection.first())
        assertEquals(SortSpec.DEFAULT, prefs.sortSpec.first())
        assertEquals(false, prefs.showHidden.first())
    }

    @Test
    fun `만화의 쪽 배치와 PDF 두 쪽 보기는 남고 범위를 누른다`() = runTest {
        assertEquals(AppPreferences.COMIC_LAYOUT_AUTO, prefs.comicPageLayout.first())
        assertEquals(false, prefs.pdfSpread.first())
        prefs.setComicPageLayout(AppPreferences.COMIC_LAYOUT_DOUBLE)
        prefs.setPdfSpread(true)
        assertEquals(AppPreferences.COMIC_LAYOUT_DOUBLE, prefs.comicPageLayout.first())
        assertEquals(true, prefs.pdfSpread.first())
        store.edit { it[intPreferencesKey("comic_page_layout")] = 9 }
        assertEquals(AppPreferences.COMIC_LAYOUT_AUTO, prefs.comicPageLayout.first())
        // 한 칸만 바꾸는 쓰기가 다른 칸을 건드리지 않는다.
        prefs.setComicPageLayout(AppPreferences.COMIC_LAYOUT_SINGLE)
        assertEquals(true, prefs.pdfSpread.first())
    }

    @Test
    fun `범위 밖에 적힌 값은 읽을 때 누른다`() = runTest {
        // 옛 판이나 손으로 고친 파일이 남긴 값 — 저장소는 누가 적었는지 모른다.
        store.edit {
            it[intPreferencesKey("text_font_sp")] = 99
            it[intPreferencesKey("reader_font_percent")] = 5
            it[intPreferencesKey("reader_margin")] = 9
            it[intPreferencesKey("reader_theme")] = -3
            it[intPreferencesKey("comic_default_direction")] = 7
        }
        assertEquals(TextViewerDefaults.MAX_FONT_SP, prefs.textViewer.first().fontSp)
        assertEquals(
            ReaderAppearance(ReaderAppearance.MIN_FONT_PERCENT, ReaderAppearance.MARGIN_WIDE, ReaderAppearance.THEME_SYSTEM),
            prefs.readerAppearance.first(),
        )
        assertEquals(2, prefs.comicDefaultDirection.first())
    }

    @Test
    fun `쓸 때도 누른다`() = runTest {
        prefs.setTextViewer(TextViewerDefaults(fontSp = 1))
        assertEquals(TextViewerDefaults.MIN_FONT_SP, prefs.textViewer.first().fontSp)
        prefs.setReaderAppearance(ReaderAppearance(fontPercent = 900, theme = 42))
        assertEquals(ReaderAppearance(ReaderAppearance.MAX_FONT_PERCENT, ReaderAppearance.MARGIN_NORMAL, ReaderAppearance.THEME_SEPIA),
            prefs.readerAppearance.first())
        prefs.setComicDefaultDirection(-1)
        assertEquals(0, prefs.comicDefaultDirection.first())
    }

    @Test
    fun `폴더 먼저만 바꾸면 정렬 기준과 방향은 그대로다`() = runTest {
        prefs.setSortSpec(SortSpec(SortKey.SIZE, ascending = false, foldersFirst = true))
        prefs.setFoldersFirst(false)
        assertEquals(SortSpec(SortKey.SIZE, ascending = false, foldersFirst = false), prefs.sortSpec.first())
    }

    /**
     * 깨진 설정 파일(길이가 127 이라 적고 두 바이트만 있는 proto). 처리기가 없는 저장소를 **일부러** 만들어 읽기 실패의
     * 길을 탄다 — 흐름이 던지면 목록 화면의 `stateIn` 이 앱을 죽인다(`AppPreferences` 의 `data` 주석).
     */
    private fun corruptStore(handler: Boolean): DataStore<Preferences> {
        val file = File(dir, "broken-$handler.preferences_pb").apply { writeBytes(byteArrayOf(0x0A, 0x7F, 0x01, 0x02)) }
        return if (handler) {
            PreferenceDataStoreFactory.create(corruptionHandler = settingsCorruptionHandler(), scope = scope) { file }
        } else {
            PreferenceDataStoreFactory.create(scope = scope) { file }
        }
    }

    @Test
    fun `읽기가 실패해도 흐름은 던지지 않고 기본값을 준다`() = runTest {
        val broken = corruptStore(handler = false)
        // 표본이 정말 읽기 실패의 길을 타는지부터 — 날것의 저장소는 던져야 한다.
        assertFailsWith<IOException> { broken.data.first() }
        val p = AppPreferences(broken)
        assertEquals(SortSpec.DEFAULT, p.sortSpec.first())
        assertEquals(false, p.showHidden.first())
        assertEquals(TextViewerDefaults(), p.textViewer.first())
        assertEquals(0, p.comicDefaultDirection.first())
        assertEquals(ReaderAppearance(), p.readerAppearance.first())
    }

    @Test
    fun `깨진 파일은 빈 설정으로 갈아 끼우고 쓰기도 된다`() = runTest {
        val p = AppPreferences(corruptStore(handler = true))
        assertEquals(false, p.showHidden.first())
        p.setShowHidden(true)
        p.updateTextViewer { it.copy(fontSp = 18) }
        assertEquals(true, p.showHidden.first())
        assertEquals(18, p.textViewer.first().fontSp)
    }

    @Test
    fun `한 칸만 바꾸는 쓰기는 다른 칸을 건드리지 않는다`() = runTest {
        prefs.setTextViewer(TextViewerDefaults(wrap = true, lineNumbers = false, fontSp = 17, showLineEnds = true))
        prefs.updateTextViewer { it.copy(fontSp = 12) }
        assertEquals(TextViewerDefaults(wrap = true, lineNumbers = false, fontSp = 12, showLineEnds = true), prefs.textViewer.first())

        prefs.setReaderAppearance(ReaderAppearance(fontPercent = 150, margin = ReaderAppearance.MARGIN_WIDE, theme = ReaderAppearance.THEME_DARK))
        prefs.updateReaderAppearance { it.copy(theme = ReaderAppearance.THEME_SEPIA) }
        assertEquals(
            ReaderAppearance(fontPercent = 150, margin = ReaderAppearance.MARGIN_WIDE, theme = ReaderAppearance.THEME_SEPIA),
            prefs.readerAppearance.first(),
        )
    }
}
