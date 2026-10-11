package io.github.donggi.iroiroviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.SortSpec
import io.github.donggi.iroiroviewer.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 앱 전체에 걸리는 몇 개짜리 설정. **Room 이 아니라 DataStore 인 이유**는
 * 이것들이 키-값 몇 개이고 질의가 필요 없기 때문이다.
 *
 * 반대로 초당 갱신되는 재생 위치는 DataStore 에 넣지 않는다 — 쓸 때마다 파일 전체를
 * 다시 쓰므로 그런 빈도를 감당하지 못한다. 그쪽은 Room 이다.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = settingsCorruptionHandler(),
)

/**
 * 설정 파일이 깨졌으면 **빈 설정으로 갈아 끼운다**.
 *
 * 처리기가 없으면 DataStore 는 `CorruptionException` 을 읽는 쪽에 그대로 던진다. 목록 화면의 ViewModel 이 설정을
 * `stateIn(Eagerly)` 로 들고 있어 그 예외는 `viewModelScope` 밖으로 나가 앱을 죽이고, 다음에 켜도 같은 파일이라 **첫 화면에서
 * 매번 죽는다** — 크래시 기록을 볼 설정 화면에도 닿지 못한다. 잃는 것은 정렬·숨김·뷰어 기본값 몇 개뿐이라 되살릴 값이
 * 없다. 시험이 같은 처리기를 JVM 의 DataStore 에 붙여 본다.
 */
internal fun settingsCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { emptyPreferences() }

/**
 * 설정 읽기·쓰기.
 *
 * **저장소를 받는 생성자가 따로 있는 이유**는 JVM 시험이다. 값의 범위를 누르는 규칙(옛 판이 적은 값·손으로 고친 값이
 * 화면을 깨뜨리지 않게)과 한 칸만 바꾸는 쓰기가 다른 칸을 건드리지 않는지는 진짜 DataStore 로 봐야 뜻이 있는데,
 * `Context` 에 묶인 위임(`preferencesDataStore`)은 기기에서만 만들어진다. 앱이 쓰는 길은 언제나 `Context` 쪽이다.
 */
class AppPreferences internal constructor(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        /** [comicPageLayout] 의 값 — `feature:comic` 의 `PageLayout` 차례와 같다. */
        const val COMIC_LAYOUT_SINGLE = 0
        const val COMIC_LAYOUT_DOUBLE = 1
        const val COMIC_LAYOUT_AUTO = 2

        /** [tapSeekSeconds] 가 고를 수 있는 값(초) — 재생 화면을 두 번 두드려 한 번에 건너뛰는 시간. */
        val TAP_SEEK_CHOICES: List<Int> = listOf(5, 10, 15, 30)

        /** [tapSeekSeconds] 의 기본값 — 큰 재생기들(YouTube·MX·VLC)의 기본이 10초다. */
        const val DEFAULT_TAP_SEEK_SECONDS = 10

        /** [readerKeepScreenMinutes] 의 기본값(분). 검토가 권한 '마지막 조작 뒤 5~10분' 의 긴 쪽이다. */
        const val KEEP_SCREEN_DEFAULT_MINUTES = 10

        /** [readerKeepScreenMinutes] 의 상한(분). 손으로 고친 값이 화면을 몇 시간씩 켜 두지 않게 누른다. */
        const val KEEP_SCREEN_MAX_MINUTES = 60
    }

    private object Keys {
        val sortKey = intPreferencesKey("sort_key")
        val sortAscending = booleanPreferencesKey("sort_ascending")
        val foldersFirst = booleanPreferencesKey("folders_first")
        val viewMode = intPreferencesKey("view_mode")
        val showHidden = booleanPreferencesKey("show_hidden")

        // 14단계 — 뷰어의 기본값. 7·9·11단계가 '설정 화면을 만들 때' 로 미뤄 둔 것이다.
        val textWrap = booleanPreferencesKey("text_wrap")
        val textLineNumbers = booleanPreferencesKey("text_line_numbers")
        val textFontSp = intPreferencesKey("text_font_sp")
        val textShowLineEnds = booleanPreferencesKey("text_show_line_ends")
        val comicDirection = intPreferencesKey("comic_default_direction")
        val readerFontPercent = intPreferencesKey("reader_font_percent")
        val readerMargin = intPreferencesKey("reader_margin")
        val readerTheme = intPreferencesKey("reader_theme")
        val comicPageLayout = intPreferencesKey("comic_page_layout")
        val pdfSpread = booleanPreferencesKey("pdf_spread")
        val resumeOnBluetooth = booleanPreferencesKey("resume_on_bluetooth")
        val tapSeekSeconds = intPreferencesKey("tap_seek_seconds")

        // 읽는 화면: 화면 켜 두기·만화의 볼륨 키·PDF 의 보기 방식.
        val readerKeepScreenMinutes = intPreferencesKey("reader_keep_screen_minutes")
        val comicVolumeKeys = booleanPreferencesKey("comic_volume_keys")
        val pdfContinuous = booleanPreferencesKey("pdf_continuous")

        // 텍스트 뷰어의 **글 파일** 기본값([TextViewerKind.TEXT]). 위의 `text_*` 넷은 코드 파일의 것으로 남는다(예전 값을
        // 그대로 쓴다 — 아래 [readProseViewer] 의 '물려받기').
        val proseWrap = booleanPreferencesKey("text_prose_wrap")
        val proseLineNumbers = booleanPreferencesKey("text_prose_line_numbers")
        val proseFontSp = intPreferencesKey("text_prose_font_sp")
        val proseShowLineEnds = booleanPreferencesKey("text_prose_show_line_ends")
    }

    /**
     * 읽기의 입출력 실패는 **기본값**으로 넘긴다(DataStore 문서가 권하는 모양). 깨진 파일은 위의 처리기가 갈아 끼우지만
     * 그 밖의 읽기 실패(`IOException`)도 흐름을 모으는 쪽(`stateIn`·`combine`)에서 앱을 죽인다. 기본값으로 그리는 편이
     * 낫다 — 흐름은 거기서 끝나므로 그 뒤의 쓰기는 다음에 화면을 새로 열 때 보인다. 취소는 `IOException` 이 아니라 그대로 간다.
     */
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val sortSpec: Flow<SortSpec> = data.map { p ->
        SortSpec(
            key = SortKey.entries.getOrElse(p[Keys.sortKey] ?: 0) { SortKey.NAME },
            ascending = p[Keys.sortAscending] ?: true,
            foldersFirst = p[Keys.foldersFirst] ?: true,
        )
    }

    val viewMode: Flow<ViewMode> = data.map { p ->
        ViewMode.entries.getOrElse(p[Keys.viewMode] ?: 0) { ViewMode.LIST }
    }

    val showHidden: Flow<Boolean> = data.map { p -> p[Keys.showHidden] ?: false }

    suspend fun setSortSpec(spec: SortSpec) {
        store.edit { p ->
            p[Keys.sortKey] = spec.key.ordinal
            p[Keys.sortAscending] = spec.ascending
            p[Keys.foldersFirst] = spec.foldersFirst
        }
    }

    /**
     * '폴더 먼저' 한 칸만 바꾼다(14단계, 설정 화면).
     *
     * [setSortSpec] 에 '지금 값의 사본' 을 넘기면 읽은 때와 쓰는 때 사이에 목록 화면이 바꾼 정렬 기준을 옛 값으로 되돌린다.
     * DataStore 의 `edit` 한 번 안에서 그 칸만 적으면 다른 칸은 그 순간의 값 그대로다.
     */
    suspend fun setFoldersFirst(first: Boolean) {
        store.edit { it[Keys.foldersFirst] = first }
    }

    suspend fun setViewMode(mode: ViewMode) {
        store.edit { it[Keys.viewMode] = mode.ordinal }
    }

    suspend fun setShowHidden(show: Boolean) {
        store.edit { it[Keys.showHidden] = show }
    }

    // ---- 14단계: 뷰어의 기본값 ------------------------------------------------------------------
    //
    // **한 DataStore 파일에 둔다.** 같은 파일(`settings`)에 DataStore 를 둘 만들면 '같은 파일에 여러 DataStore' 로
    // 죽는다 — 설정이 늘어도 이 클래스에 키를 더한다. 값의 범위는 여기서 누른다(DataStore 는 누가 무엇을 적었는지
    // 모른다 — 옛 판이 적은 값이나 손으로 고친 값도 화면을 깨뜨리지 않게).

    val textViewer: Flow<TextViewerDefaults> = data.map { p -> readTextViewer(p) }

    suspend fun setTextViewer(value: TextViewerDefaults) {
        store.edit { p -> writeTextViewer(p, value) }
    }

    /**
     * 지금 값에서 바꾼 것만 적는다. 읽기와 쓰기가 **한 `edit` 안**이라, 설정 화면이 글자 크기를 바꾸는 사이 뷰어가 줄 접기를
     * 바꿔도 어느 한쪽이 지워지지 않는다([setFoldersFirst] 와 같은 까닭).
     */
    suspend fun updateTextViewer(transform: (TextViewerDefaults) -> TextViewerDefaults) {
        store.edit { p -> writeTextViewer(p, transform(readTextViewer(p))) }
    }

    /**
     * 종류별 텍스트 뷰어 설정. [TextViewerKind.CODE] 는 [textViewer] 그대로, [TextViewerKind.TEXT] 는 글 파일의 것이다
     * ([proseViewer]).
     */
    fun textViewer(kind: TextViewerKind): Flow<TextViewerDefaults> = when (kind) {
        TextViewerKind.CODE -> textViewer
        TextViewerKind.TEXT -> proseViewer
    }

    suspend fun setTextViewer(kind: TextViewerKind, value: TextViewerDefaults) {
        when (kind) {
            TextViewerKind.CODE -> setTextViewer(value)
            TextViewerKind.TEXT -> store.edit { p -> writeProseViewer(p, value) }
        }
    }

    /**
     * **글 파일**(`.txt`·`.md`·`.log`·자막 …)의 텍스트 뷰어 설정. 처음 값이 코드와 다르다 — 줄을 접고 줄 번호를 숨긴다. 문단마다 한 줄인
     * 산문이 코드의 기본값(접지 않음·줄 번호)으로 열려 문장이 화면 밖으로 뻗었다. 예전에는 둘이 한 벌이라
     * 줄 접기를 켜면 코드도 함께 접혔다.
     */
    val proseViewer: Flow<TextViewerDefaults> = data.map { p -> readProseViewer(p) }

    /** [updateTextViewer] 의 글 파일 판 — 한 칸만 바꾸는 쓰기가 다른 칸을 되돌리지 않는다. */
    suspend fun updateProseViewer(transform: (TextViewerDefaults) -> TextViewerDefaults) {
        store.edit { p -> writeProseViewer(p, transform(readProseViewer(p))) }
    }

    /** 새 만화책의 읽는 방향 — `ComicProgressEntity.readDirection` 과 같은 값(0 왼→오, 1 오→왼, 2 세로). 기본은 왼→오. */
    val comicDefaultDirection: Flow<Int> = data.map { p -> (p[Keys.comicDirection] ?: 0).coerceIn(0, 2) }

    suspend fun setComicDefaultDirection(code: Int) {
        store.edit { it[Keys.comicDirection] = code.coerceIn(0, 2) }
    }

    /**
     * 만화의 쪽 배치 — 0 한 쪽, 1 두 쪽, 2 자동(가로 화면이면 두 쪽). 기본은 자동. 책마다가 아니라 앱 전체의 값이다 — 두 쪽으로
     * 보는 사람은 대개 모든 책을 그렇게 본다. 처음(14단계)에는 세션 동안만 남아 앱을 다시 켜면 자동으로 돌아갔다.
     */
    val comicPageLayout: Flow<Int> = data.map { p -> (p[Keys.comicPageLayout] ?: COMIC_LAYOUT_AUTO).coerceIn(0, COMIC_LAYOUT_AUTO) }

    suspend fun setComicPageLayout(code: Int) {
        store.edit { it[Keys.comicPageLayout] = code.coerceIn(0, COMIC_LAYOUT_AUTO) }
    }

    /** PDF 를 가로 화면에서 두 쪽으로 보는가(앱 전체). 기본은 한 쪽. [comicPageLayout] 과 같은 까닭으로 남긴다. */
    val pdfSpread: Flow<Boolean> = data.map { p -> p[Keys.pdfSpread] ?: false }

    suspend fun setPdfSpread(on: Boolean) {
        store.edit { it[Keys.pdfSpread] = on }
    }

    /**
     * 블루투스(이어폰·스피커·차)가 끊겨 멈춘 재생을, 다시 연결되면 **이어서 튼다**. 기본은 켜짐 — 요구사항의 'VLC 에서 차용' 항목이다
     * (연결/해제 시 자동 재개/멈춤). 끊길 때 멈추는 것은 설정과 상관없이 언제나 한다(소리가 스피커로 새지 않게).
     */
    val resumeOnBluetooth: Flow<Boolean> = data.map { p -> p[Keys.resumeOnBluetooth] ?: true }

    suspend fun setResumeOnBluetooth(on: Boolean) {
        store.edit { it[Keys.resumeOnBluetooth] = on }
    }

    /** 흐름 문서(EPUB)를 읽는 모양. */
    val readerAppearance: Flow<ReaderAppearance> = data.map { p -> readReaderAppearance(p) }

    suspend fun setReaderAppearance(value: ReaderAppearance) {
        store.edit { p -> writeReaderAppearance(p, value) }
    }

    /** [updateTextViewer] 와 같다 — 한 칸만 바꾸는 쓰기가 다른 칸을 되돌리지 않는다. */
    suspend fun updateReaderAppearance(transform: (ReaderAppearance) -> ReaderAppearance) {
        store.edit { p -> writeReaderAppearance(p, transform(readReaderAppearance(p))) }
    }

    /**
     * 재생 화면을 두 번 두드려 건너뛰는 시간(초) — [TAP_SEEK_CHOICES] 가운데 하나, 기본 [DEFAULT_TAP_SEEK_SECONDS].
     * 10단계에는 30초 고정이었다 — 놓친 대사를 다시 들으려는 사람에게는 너무 멀었다. 목록 밖의 값(옛 판·손으로 고친 값)은 기본값으로
     * 읽는다 — 이 값이 제스처의 걸음이라 엉뚱한 값이면 두드릴 때마다 엉뚱한 자리로 뛴다.
     */
    val tapSeekSeconds: Flow<Int> = data.map { p -> tapSeekOrDefault(p[Keys.tapSeekSeconds]) }

    suspend fun setTapSeekSeconds(seconds: Int) {
        store.edit { it[Keys.tapSeekSeconds] = tapSeekOrDefault(seconds) }
    }

    private fun tapSeekOrDefault(seconds: Int?): Int = seconds?.takeIf { it in TAP_SEEK_CHOICES } ?: DEFAULT_TAP_SEEK_SECONDS

    // ---- 읽는 화면 ------------------------------------------------------------------

    /**
     * 읽는 화면(만화·문서·글)에서 **마지막 조작 뒤 화면을 켜 두는 시간**(분). 0 이면 켜 두지 않는다(기기의 화면 꺼짐 시간을 따른다).
     * 기본 [KEEP_SCREEN_DEFAULT_MINUTES] — 한 쪽을 기기의 꺼짐 시간(갤럭시는 흔히 30초)보다 오래 읽으면 화면이 꺼졌다.
     * 거는 것은 액티비티다(`ViewerWindowHost`).
     */
    val readerKeepScreenMinutes: Flow<Int> = data.map { p ->
        (p[Keys.readerKeepScreenMinutes] ?: KEEP_SCREEN_DEFAULT_MINUTES).coerceIn(0, KEEP_SCREEN_MAX_MINUTES)
    }

    suspend fun setReaderKeepScreenMinutes(minutes: Int) {
        store.edit { it[Keys.readerKeepScreenMinutes] = minutes.coerceIn(0, KEEP_SCREEN_MAX_MINUTES) }
    }

    /** 만화를 **볼륨 키로 넘긴다**(아래 키가 다음 쪽). 기본은 끔 — 켜면 만화를 보는 동안 볼륨 키가 소리를 바꾸지 않는다. */
    val comicVolumeKeys: Flow<Boolean> = data.map { p -> p[Keys.comicVolumeKeys] ?: false }

    suspend fun setComicVolumeKeys(on: Boolean) {
        store.edit { it[Keys.comicVolumeKeys] = on }
    }

    /**
     * PDF 를 **세로로 이어 보는가**(쪽을 폭에 맞춰 이어 붙이고 배율이 문서 전체에 걸린다). 거짓이면 예전의 한 쪽씩 넘기기다.
     * 기본은 이어 보기 — 구글 드라이브·삼성·Adobe 의 PDF 뷰어가 그렇다. 뷰어의 ⋮ 에서 고르고 여기 남는다.
     */
    val pdfContinuous: Flow<Boolean> = data.map { p -> p[Keys.pdfContinuous] ?: true }

    suspend fun setPdfContinuous(on: Boolean) {
        store.edit { it[Keys.pdfContinuous] = on }
    }

    private fun readTextViewer(p: Preferences) = TextViewerDefaults(
        wrap = p[Keys.textWrap] ?: false,
        lineNumbers = p[Keys.textLineNumbers] ?: true,
        fontSp = (p[Keys.textFontSp] ?: TextViewerDefaults.DEFAULT_FONT_SP)
            .coerceIn(TextViewerDefaults.MIN_FONT_SP, TextViewerDefaults.MAX_FONT_SP),
        showLineEnds = p[Keys.textShowLineEnds] ?: false,
    )

    private fun writeTextViewer(p: MutablePreferences, value: TextViewerDefaults) {
        // 글 파일의 값이 아직 예전 값을 물려받는 중이면([readProseViewer]) 코드의 값을 바꾸기 **전에** 그것을 적어 둔다 — 그러지 않으면
        // 코드의 글자 크기를 바꿀 때 글의 것도 따라 바뀐다(둘은 이제 따로다).
        if (p[Keys.proseWrap] == null) writeProseViewer(p, readProseViewer(p))
        p[Keys.textWrap] = value.wrap
        p[Keys.textLineNumbers] = value.lineNumbers
        p[Keys.textFontSp] = value.fontSp.coerceIn(TextViewerDefaults.MIN_FONT_SP, TextViewerDefaults.MAX_FONT_SP)
        p[Keys.textShowLineEnds] = value.showLineEnds
    }

    /**
     * 글 파일의 설정을 읽는다. **아직 한 번도 바꾸지 않았으면** 줄 접기·줄 번호는 글의 처음 값([TextViewerDefaults.defaultsFor])이고,
     * 글자 크기와 줄 끝 표시는 **예전(둘이 한 벌이던 때)에 저장한 값을 물려받는다** — 그 둘은 처음 값과 다르면 사람이 고른 것이다(크게
     * 보는 사람은 글도 크게 본다). 줄 접기·줄 번호는 예전 값이 처음 값과 같으면 고른 것인지 알 길이 없고, 다르면(접기 켬·번호 끔)
     * 글의 처음 값과 같다 — 어느 쪽이든 글의 처음 값이 맞다. 글이나 코드의 설정을 한 번 바꾸면 글의 넷이 모두 적혀 그때부터 따로
     * 간다([writeTextViewer] 가 코드의 값을 바꾸기 전에 글의 것을 먼저 적는다).
     */
    private fun readProseViewer(p: Preferences): TextViewerDefaults {
        val start = TextViewerDefaults.defaultsFor(TextViewerKind.TEXT)
        return TextViewerDefaults(
            wrap = p[Keys.proseWrap] ?: start.wrap,
            lineNumbers = p[Keys.proseLineNumbers] ?: start.lineNumbers,
            fontSp = (p[Keys.proseFontSp] ?: p[Keys.textFontSp] ?: start.fontSp)
                .coerceIn(TextViewerDefaults.MIN_FONT_SP, TextViewerDefaults.MAX_FONT_SP),
            showLineEnds = p[Keys.proseShowLineEnds] ?: p[Keys.textShowLineEnds] ?: start.showLineEnds,
        )
    }

    private fun writeProseViewer(p: MutablePreferences, value: TextViewerDefaults) {
        p[Keys.proseWrap] = value.wrap
        p[Keys.proseLineNumbers] = value.lineNumbers
        p[Keys.proseFontSp] = value.fontSp.coerceIn(TextViewerDefaults.MIN_FONT_SP, TextViewerDefaults.MAX_FONT_SP)
        p[Keys.proseShowLineEnds] = value.showLineEnds
    }

    private fun readReaderAppearance(p: Preferences) = ReaderAppearance(
        fontPercent = (p[Keys.readerFontPercent] ?: ReaderAppearance.DEFAULT_FONT_PERCENT)
            .coerceIn(ReaderAppearance.MIN_FONT_PERCENT, ReaderAppearance.MAX_FONT_PERCENT),
        margin = (p[Keys.readerMargin] ?: ReaderAppearance.MARGIN_NORMAL).coerceIn(0, ReaderAppearance.MARGIN_WIDE),
        theme = (p[Keys.readerTheme] ?: ReaderAppearance.THEME_SYSTEM).coerceIn(0, ReaderAppearance.THEME_SEPIA),
    )

    private fun writeReaderAppearance(p: MutablePreferences, value: ReaderAppearance) {
        p[Keys.readerFontPercent] =
            value.fontPercent.coerceIn(ReaderAppearance.MIN_FONT_PERCENT, ReaderAppearance.MAX_FONT_PERCENT)
        p[Keys.readerMargin] = value.margin.coerceIn(0, ReaderAppearance.MARGIN_WIDE)
        p[Keys.readerTheme] = value.theme.coerceIn(0, ReaderAppearance.THEME_SEPIA)
    }
}

/**
 * 텍스트 뷰어의 기본값. 뷰어에서 바꾸면 여기에 남아 다음 파일도 그 모양으로 열린다(7단계가 '파일을 닫으면 기본값으로
 * 돌아간다' 로 미뤄 둔 것). **종류([TextViewerKind])마다 따로 남는다** — 이 클래스의 기본 인자는 코드의 처음 값이다.
 */
data class TextViewerDefaults(
    val wrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val fontSp: Int = DEFAULT_FONT_SP,
    /** 줄 끝 문자(`⏎`)를 보인다. */
    val showLineEnds: Boolean = false,
) {
    companion object {
        const val DEFAULT_FONT_SP = 13
        const val MIN_FONT_SP = 9
        const val MAX_FONT_SP = 22

        /** 종류마다의 처음 값 — 글은 접고 줄 번호를 숨긴다. 코드는 예전 그대로(접지 않음 — 들여쓰기가 뜻을 가진다, 줄 번호). */
        fun defaultsFor(kind: TextViewerKind): TextViewerDefaults = when (kind) {
            TextViewerKind.TEXT -> TextViewerDefaults(wrap = true, lineNumbers = false)
            TextViewerKind.CODE -> TextViewerDefaults()
        }
    }
}

/**
 * 텍스트 뷰어가 설정을 따로 드는 파일의 갈래. 가르는 것은 파일 목록과 같은 표다(`core:io` 의 `MimeResolver.kindOf` — 목록이 '코드' 로
 * 그리는 것이 [CODE], 나머지는 [TEXT]). 이 모듈은 그 표를 보지 않는다 — 뷰어가 가른다.
 */
enum class TextViewerKind {
    /** 글 — `.txt`·`.md`·`.log`·`.csv`·자막·`.ini`, 그리고 이름으로 종류를 모르는 파일. */
    TEXT,

    /** 코드 — 강조하는 언어의 파일(`.kt`·`.js`·`.html`·`.json` …). */
    CODE,
}

/**
 * 흐름 문서를 읽는 모양 — 글자 크기(문서 크기의 %), 좌우 여백, 바탕색. 11단계의 `chapterHtml(extraCss)` 가 이 자리를
 * 열어 두었다.
 */
data class ReaderAppearance(
    val fontPercent: Int = DEFAULT_FONT_PERCENT,
    val margin: Int = MARGIN_NORMAL,
    val theme: Int = THEME_SYSTEM,
) {
    companion object {
        const val DEFAULT_FONT_PERCENT = 100
        const val MIN_FONT_PERCENT = 70
        const val MAX_FONT_PERCENT = 200
        const val MARGIN_NARROW = 0
        const val MARGIN_NORMAL = 1
        const val MARGIN_WIDE = 2
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_SEPIA = 3
    }
}
