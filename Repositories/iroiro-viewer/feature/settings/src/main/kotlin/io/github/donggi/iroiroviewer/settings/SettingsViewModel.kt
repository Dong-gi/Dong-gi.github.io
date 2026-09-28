package io.github.donggi.iroiroviewer.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.data.CrashLog
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.data.ReaderAppearance
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.io.ShareHelper
import io.github.donggi.iroiroviewer.model.SortSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 설정 화면의 상태와 쓰기.
 *
 * ## 쓰기가 곧바로 걸린다
 *
 * 값은 전부 `AppPreferences` 의 흐름이다. 여기서 적으면 그 흐름을 보는 화면(목록·텍스트·만화·문서)이 다음에 그릴 때
 * 새 값을 받는다 — '저장' 단추가 없다. 이 화면이 값을 따로 들고 있지 않는 것이 요점이다: 사본을 들면 뷰어가 바꾼
 * 값(텍스트 뷰어의 줄 접기 같은 것)과 어긋난다.
 *
 * ## 쓰기의 결과를 사건으로 흘리지 않는다
 *
 * 모든 쓰기는 **`Deferred<Boolean>`** 을 돌려주고 화면이 그것을 기다려 알림을 띄운다. 사건 채널로 흘리면 화면이
 * 사라진 뒤에 끝난 결과가 채널에 남아 **다음에 설정을 열었을 때** 뜬다(9단계 뒤 감사가 `ComicViewModel` 에서 찾은
 * 모양). 기다리던 화면이 없어졌으면 알림도 없다 — 쓰기 자체는 이 ViewModel 의 스코프라 끝까지 간다.
 *
 * ## 실패는 삼키되 취소는 삼키지 않는다
 *
 * DataStore 의 쓰기는 `IOException`(저장공간 부족), Room 의 지우기는 SQLite 예외를 던질 수 있다. 잡지 않으면
 * `viewModelScope` 에서 앱이 죽는다. `runCatching` 은 취소까지 삼키므로 쓰지 않는다(CLAUDE.md '코드').
 */
class SettingsViewModel(
    private val settings: SettingsSource,
    private val history: HistorySource,
    private val crashes: CrashSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** 화면이 그리는 값 한 벌. 처음 읽기 전에는 null — 기본값을 먼저 그렸다가 저장된 값으로 튀는 것을 막는다. */
    data class Values(
        val showHidden: Boolean,
        val foldersFirst: Boolean,
        val text: TextViewerDefaults,
        val comicDirection: Int,
        val reader: ReaderAppearance,
    )

    val values: StateFlow<Values?> = combine(
        settings.showHidden,
        settings.sortSpec,
        settings.textViewer,
        settings.comicDefaultDirection,
        settings.readerAppearance,
    ) { hidden, sort, text, comic, reader -> Values(hidden, sort.foldersFirst, text, comic, reader) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _crash = MutableStateFlow<CrashLog.Summary?>(null)

    /** 크래시 기록의 건수와 마지막 시각. **내용은 여기에도 없다** — 화면에 나갈 길을 처음부터 두지 않는다. */
    val crash: StateFlow<CrashLog.Summary?> = _crash.asStateFlow()

    // ---- 화면 자리 ----------------------------------------------------------------------------

    /**
     * 고지·진단으로 떠나기 직전의 스크롤 자리. 그 두 화면으로 가면 설정 화면이 컴포지션에서 통째로 빠져 목록의 자리가
     * 사라진다 — `rememberSaveable` 은 그것을 견디지 못한다(CLAUDE.md 함정). 액티비티에 묶인 이 객체가 한 번 맡아 둔다.
     */
    private var savedScroll: Pair<Int, Int>? = null

    fun rememberScroll(index: Int, offset: Int) {
        savedScroll = index to offset
    }

    /** 맡긴 자리를 **한 번** 꺼낸다. 뒤로 나갔다가 다시 열면 맡긴 것이 없어 맨 위(0, 0)다. */
    fun takeScroll(): Pair<Int, Int> = (savedScroll ?: (0 to 0)).also { savedScroll = null }

    // ---- 파일 목록 ----------------------------------------------------------------------------

    fun setShowHidden(show: Boolean) = attempt { settings.setShowHidden(show) }

    fun setFoldersFirst(first: Boolean) = attempt { settings.setFoldersFirst(first) }

    // ---- 텍스트 뷰어 ---------------------------------------------------------------------------

    fun setTextWrap(on: Boolean) = attempt { settings.updateTextViewer { it.copy(wrap = on) } }

    fun setTextLineNumbers(on: Boolean) = attempt { settings.updateTextViewer { it.copy(lineNumbers = on) } }

    fun setTextLineEnds(on: Boolean) = attempt { settings.updateTextViewer { it.copy(showLineEnds = on) } }

    fun setTextFontSp(sp: Int) = attempt {
        val v = sp.coerceIn(TextViewerDefaults.MIN_FONT_SP, TextViewerDefaults.MAX_FONT_SP)
        settings.updateTextViewer { it.copy(fontSp = v) }
    }

    // ---- 만화 ---------------------------------------------------------------------------------

    fun setComicDirection(code: Int) = attempt { settings.setComicDefaultDirection(code.coerceIn(0, 2)) }

    // ---- 문서 ---------------------------------------------------------------------------------

    fun setReaderFontPercent(percent: Int) = attempt {
        val v = percent.coerceIn(ReaderAppearance.MIN_FONT_PERCENT, ReaderAppearance.MAX_FONT_PERCENT)
        settings.updateReaderAppearance { it.copy(fontPercent = v) }
    }

    fun setReaderMargin(code: Int) = attempt {
        val v = code.coerceIn(ReaderAppearance.MARGIN_NARROW, ReaderAppearance.MARGIN_WIDE)
        settings.updateReaderAppearance { it.copy(margin = v) }
    }

    fun setReaderTheme(code: Int) = attempt {
        val v = code.coerceIn(ReaderAppearance.THEME_SYSTEM, ReaderAppearance.THEME_SEPIA)
        settings.updateReaderAppearance { it.copy(theme = v) }
    }

    // ---- 기록 ---------------------------------------------------------------------------------

    fun clearHistory(kind: HistoryKind) = attempt { history.clear(kind) }

    // ---- 크래시 기록 ----------------------------------------------------------------------------

    /** 화면에 들어올 때마다 다시 읽는다 — 기록은 앞선 프로세스가 죽으면서 남기므로 이 화면이 모르는 사이에 는다. */
    fun refreshCrashes() = attempt { _crash.value = withContext(io) { crashes.summary() } }

    /** 공유 사본을 만든다. 기록이 없거나 만들지 못했으면 null. 보내는 일(인텐트)은 화면이 한다 — `Context` 가 거기 있다. */
    suspend fun exportCrashes(): File? = try {
        withContext(io) { crashes.export() }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    fun clearCrashes() = attempt {
        val cleared = withContext(io) { crashes.clear() }
        _crash.value = withContext(io) { crashes.summary() }
        check(cleared)
    }

    /**
     * 쓰기 하나. 끝까지 가면 true, 실패하면 false. **던지지 않는다**(클래스 주석).
     * `check` 가 던지는 `IllegalStateException` 도 여기서 false 가 된다.
     */
    private fun attempt(block: suspend () -> Unit): Deferred<Boolean> = viewModelScope.async {
        try {
            block()
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    companion object {
        /** 공유 사본의 자리 — 캐시의 공유 폴더 아래. `FileProvider` 가 여는 곳이 거기뿐이다(`ShareHelper`). */
        private const val SHARE_SUBDIR = "crash"
        private const val SHARE_NAME = "iroiro-crash.txt"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                create(app)
            }
        }

        private fun create(app: Application): SettingsViewModel = SettingsViewModel(
            settings = PreferencesSettingsSource(AppPreferences(app)),
            history = RoomHistorySource(IroiroDatabase.get(app)),
            crashes = FileCrashSource(CrashLog.of(app)) {
                File(File(ShareHelper.shareDir(app), SHARE_SUBDIR), SHARE_NAME)
            },
        )
    }
}

/** 설정 화면이 읽고 쓰는 값. 실물은 [PreferencesSettingsSource], 시험은 가짜다. */
interface SettingsSource {
    val showHidden: Flow<Boolean>
    val sortSpec: Flow<SortSpec>
    val textViewer: Flow<TextViewerDefaults>
    val comicDefaultDirection: Flow<Int>
    val readerAppearance: Flow<ReaderAppearance>
    suspend fun setShowHidden(show: Boolean)
    suspend fun setFoldersFirst(first: Boolean)
    suspend fun updateTextViewer(transform: (TextViewerDefaults) -> TextViewerDefaults)
    suspend fun setComicDefaultDirection(code: Int)
    suspend fun updateReaderAppearance(transform: (ReaderAppearance) -> ReaderAppearance)
}

/** 지울 수 있는 기록의 갈래. 화면의 세 줄과 하나씩 맞는다. */
enum class HistoryKind {
    /** 재생 위치(`playback_position`). */
    PLAYBACK,

    /** 만화·문서에서 읽던 자리(`comic_progress`·`doc_progress`). 만화의 책마다 고른 방향도 여기 산다. */
    READING,

    // `recent_location` 은 지우는 줄을 두지 않는다 — 그 표에 쓰는 코드가 아직 없어(`RecentLocationDao.visit` 호출 0건)
    // 지울 것이 언제나 없는 단추가 된다. 최근 위치를 기록하기 시작하는 때에 더한다.
}

interface HistorySource {
    suspend fun clear(kind: HistoryKind)
}

/** 크래시 기록. 블로킹이다 — 부르는 쪽이 IO 로 넘긴다. */
interface CrashSource {
    fun summary(): CrashLog.Summary
    fun export(): File?
    fun clear(): Boolean
}

internal class PreferencesSettingsSource(private val prefs: AppPreferences) : SettingsSource {
    override val showHidden = prefs.showHidden
    override val sortSpec = prefs.sortSpec
    override val textViewer = prefs.textViewer
    override val comicDefaultDirection = prefs.comicDefaultDirection
    override val readerAppearance = prefs.readerAppearance
    override suspend fun setShowHidden(show: Boolean) = prefs.setShowHidden(show)
    override suspend fun setFoldersFirst(first: Boolean) = prefs.setFoldersFirst(first)
    override suspend fun updateTextViewer(transform: (TextViewerDefaults) -> TextViewerDefaults) =
        prefs.updateTextViewer(transform)
    override suspend fun setComicDefaultDirection(code: Int) = prefs.setComicDefaultDirection(code)
    override suspend fun updateReaderAppearance(transform: (ReaderAppearance) -> ReaderAppearance) =
        prefs.updateReaderAppearance(transform)
}

internal class RoomHistorySource(private val db: IroiroDatabase) : HistorySource {
    override suspend fun clear(kind: HistoryKind) {
        when (kind) {
            HistoryKind.PLAYBACK -> db.playbackPositions().clear()
            HistoryKind.READING -> {
                db.comicProgress().clear()
                db.docProgress().clear()
            }
        }
    }
}

/**
 * 크래시 기록의 실물. 공유 사본은 [shareFile] 한 자리에 **덮어쓴다** — 공유할 때마다 캐시에 사본이 쌓이지 않게.
 * 지울 때 그 사본도 함께 지운다: 원본을 지웠는데 캐시에 사본이 남아 있으면 지운 것이 아니다.
 */
internal class FileCrashSource(private val log: CrashLog, private val shareFile: () -> File) : CrashSource {
    override fun summary() = log.summary()
    override fun export() = log.exportTo(shareFile())
    override fun clear(): Boolean {
        val copy = shareFile()
        if (copy.exists()) copy.delete()
        return log.clear()
    }
}
