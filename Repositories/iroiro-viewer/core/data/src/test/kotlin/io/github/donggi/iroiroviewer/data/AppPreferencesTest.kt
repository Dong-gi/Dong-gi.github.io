package io.github.donggi.iroiroviewer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    /**
     * 두 번 두드려 건너뛰는 시간. 기본 10초, 고를 수 있는 값만 남는다 — 이 값이 재생 화면의 몸짓의 걸음이라, 목록 밖의
     * 값이 새어 나가면 두드릴 때마다 엉뚱한 자리로 뛴다.
     */
    @Test
    fun `두 번 두드림의 걸음은 기본 10초이고 고를 수 있는 값만 남는다`() = runTest {
        assertEquals(AppPreferences.DEFAULT_TAP_SEEK_SECONDS, prefs.tapSeekSeconds.first())
        assertEquals(10, AppPreferences.DEFAULT_TAP_SEEK_SECONDS)
        prefs.setTapSeekSeconds(30)
        assertEquals(30, prefs.tapSeekSeconds.first())
        // 쓸 때 누른다.
        prefs.setTapSeekSeconds(7)
        assertEquals(AppPreferences.DEFAULT_TAP_SEEK_SECONDS, prefs.tapSeekSeconds.first())
        // 읽을 때도 누른다(옛 판·손으로 고친 파일).
        store.edit { it[intPreferencesKey("tap_seek_seconds")] = 600 }
        assertEquals(AppPreferences.DEFAULT_TAP_SEEK_SECONDS, prefs.tapSeekSeconds.first())
        // 한 칸만 바꾸는 쓰기 — 다른 재생 설정을 건드리지 않는다.
        prefs.setResumeOnBluetooth(false)
        prefs.setTapSeekSeconds(15)
        assertEquals(false, prefs.resumeOnBluetooth.first())
        assertEquals(15, prefs.tapSeekSeconds.first())
    }

    @Test
    fun `읽는 화면의 설정은 기본값에서 시작하고 범위를 누른다`() = runTest {
        // 화면 켜 두기 10분, 볼륨 키 끔(소리 조절을 빼앗지 않는다), PDF 는 이어 보기.
        assertEquals(AppPreferences.KEEP_SCREEN_DEFAULT_MINUTES, prefs.readerKeepScreenMinutes.first())
        assertEquals(false, prefs.comicVolumeKeys.first())
        assertEquals(true, prefs.pdfContinuous.first())

        prefs.setReaderKeepScreenMinutes(0)
        prefs.setComicVolumeKeys(true)
        prefs.setPdfContinuous(false)
        assertEquals(0, prefs.readerKeepScreenMinutes.first())
        assertEquals(true, prefs.comicVolumeKeys.first())
        assertEquals(false, prefs.pdfContinuous.first())

        // 손으로 고친 값이 화면을 몇 시간씩 켜 두지 않게.
        store.edit { it[intPreferencesKey("reader_keep_screen_minutes")] = 600 }
        assertEquals(AppPreferences.KEEP_SCREEN_MAX_MINUTES, prefs.readerKeepScreenMinutes.first())
        store.edit { it[intPreferencesKey("reader_keep_screen_minutes")] = -5 }
        assertEquals(0, prefs.readerKeepScreenMinutes.first())
        // 한 칸만 바꾸는 쓰기는 다른 칸을 건드리지 않는다.
        prefs.setReaderKeepScreenMinutes(5)
        assertEquals(true, prefs.comicVolumeKeys.first())
        assertEquals(false, prefs.pdfContinuous.first())
    }

    /**
     * 텍스트 뷰어의 설정은 **종류마다 따로** 남는다. 글 파일은 처음에 줄을 접고 줄 번호를 숨긴다 — 코드는 예전 그대로다.
     */
    @Test
    fun `글 파일과 코드 파일의 텍스트 뷰어 설정은 따로 남는다`() = runTest {
        assertEquals(TextViewerDefaults(), prefs.textViewer(TextViewerKind.CODE).first())
        assertEquals(TextViewerDefaults(wrap = true, lineNumbers = false), prefs.textViewer(TextViewerKind.TEXT).first())
        prefs.updateProseViewer { it.copy(lineNumbers = true, fontSp = 16) }
        assertEquals(TextViewerDefaults(wrap = true, lineNumbers = true, fontSp = 16), prefs.proseViewer.first())
        assertEquals(TextViewerDefaults(), prefs.textViewer.first(), "글의 설정이 코드에 번지지 않는다")
        prefs.updateTextViewer { it.copy(fontSp = 20, wrap = true) }
        assertEquals(TextViewerDefaults(wrap = true, lineNumbers = true, fontSp = 16), prefs.proseViewer.first(), "거꾸로도")
        prefs.setTextViewer(TextViewerKind.TEXT, TextViewerDefaults(fontSp = 1))
        assertEquals(TextViewerDefaults.MIN_FONT_SP, prefs.proseViewer.first().fontSp, "범위를 누른다")
    }

    /**
     * **예전에 바꾼 값을 물려받는다** — 둘이 한 벌이던 때에 저장한 글자 크기와 줄 끝 표시(처음 값과 다르면 사람이 고른 것이다)는 글 파일도
     * 그대로 쓴다. 줄 접기·줄 번호는 글의 처음 값이다. 그 뒤 코드의 값을 바꿔도 글은 물려받은 값에 머문다 — 첫 쓰기가 글의 값을 먼저
     * 적어 둔다.
     */
    @Test
    fun `예전에 바꾼 글자 크기와 줄 끝 표시는 글 파일이 물려받는다`() = runTest {
        // 예전 판(둘이 한 벌)이 적은 모양 — 코드의 키 넷만 있고 글의 키는 없다.
        store.edit {
            it[booleanPreferencesKey("text_wrap")] = false
            it[booleanPreferencesKey("text_line_numbers")] = true
            it[intPreferencesKey("text_font_sp")] = 18
            it[booleanPreferencesKey("text_show_line_ends")] = true
        }
        val inherited = TextViewerDefaults(wrap = true, lineNumbers = false, fontSp = 18, showLineEnds = true)
        assertEquals(inherited, prefs.proseViewer.first())
        assertEquals(TextViewerDefaults(wrap = false, lineNumbers = true, fontSp = 18, showLineEnds = true), prefs.textViewer.first())

        prefs.updateTextViewer { it.copy(fontSp = 10, showLineEnds = false) }
        assertEquals(inherited, prefs.proseViewer.first(), "코드의 값을 바꿔도 글은 물려받은 값에 머문다")
        assertEquals(TextViewerDefaults(wrap = false, lineNumbers = true, fontSp = 10, showLineEnds = false), prefs.textViewer.first())

        prefs.updateProseViewer { it.copy(showLineEnds = false) }
        prefs.updateTextViewer { it.copy(fontSp = 12) }
        assertEquals(inherited.copy(showLineEnds = false), prefs.proseViewer.first())
    }
}
