package io.github.donggi.iroiroviewer.docview

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.data.DocProgressEntity
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.data.ReaderAppearance
import io.github.donggi.iroiroviewer.docview.pdf.PdfDocument
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.docview.pdf.PdfSearch
import io.github.donggi.iroiroviewer.format.Documents
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.epub.EpubBook
import io.github.donggi.iroiroviewer.format.toOpenFailure
import io.github.donggi.iroiroviewer.io.FileKey
import io.github.donggi.iroiroviewer.io.FileProbe
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.ImageLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 문서 뷰어의 상태. 구조는 `ComicViewModel` 을 그대로 따른다.
 *
 * ## 만화에서 반드시 가져오는 것 넷
 *
 * 1. **[open] 첫머리에서 기본값으로 되돌린다.** 이 VM 은 액티비티에 묶여 있어 문서를
 *    바꿔도 같은 객체다 — 되돌리지 않으면 앞 문서의 읽던 쪽이 새 문서로 옮겨 간다
 *    (9단계가 웹툰을 닫고 연 만화가 세로 모드로 열리는 것으로 겪었다).
 * 2. **되살린 뒤에 [State.Ready] 를 낸다.** 먼저 내면 페이저가 컴포지션 첫 순간에 흘리는
 *    초기값 0 이 되돌아와 방금 되살린 쪽을 지운다.
 * 3. **경로만으로 같은 문서라고 보지 않는다** — `(경로, 크기, 수정시각)` 으로 본다.
 * 4. **`runCatching` 으로 감싸지 않는다.** 취소를 삼키면 닫힌 문서를 안은 `Ready` 가
 *    다시 서고, 이벤트가 다음 문서의 화면에 배달된다.
 *
 * ## 쪽 번호 하나로 둘을 센다
 *
 * PDF 에서 [page] 는 **쪽 번호**이고 EPUB 에서는 **차례(spine)의 번호**다. 둘을 같은
 * `doc_progress.page` 칸에 넣는 것은 뜻이 같기 때문이다 — '문서에서 몇 번째 자리를
 * 보고 있는가'.
 *
 * ## 장 안의 자리(14단계)
 *
 * EPUB·흐름 문서는 장·부분 **안의** 자리도 기억한다 — 스크롤 범위에 대한 비율([fraction])이고, `locator` 에
 * `"장:비율"` 로 적는다. `progress` 에는 문서 전체의 진행을 **본 몫**([seen] — 화면의 끝 가장자리)으로 적는다([DocLocator]).
 * PDF 는 둘 다 비운다. 화면(`LockedWebView`)이 스크롤할 때마다 알리고, 여기서는 **기록만 모았다가**([scheduleSave])
 * 떠날 때 마지막으로 쓴다.
 *
 * **비율은 흐름(`StateFlow`)으로 내보내지 않는다.** 스크롤 한 프레임마다 값이 바뀌므로, 화면이 그것을 받으면 프레임마다
 * 다시 그린다. 화면은 쪽을 **열 때만** 지금 자리를 묻고([fractionNow]), '처음부터' 처럼 같은 쪽 안에서 옮겨야 할 때는
 * 일련번호([jump])가 알린다.
 */
class DocViewModel(app: Application) : AndroidViewModel(app) {

    /** 열린 문서 하나. */
    sealed interface Doc {
        val path: String
        val name: String

        /** 자리의 총 개수. PDF 는 쪽 수, EPUB 은 장 수다. */
        val count: Int

        data class Pdf(
            override val path: String,
            override val name: String,
            override val count: Int,
            val store: PdfPageStore,
        ) : Doc

        data class Epub(
            override val path: String,
            override val name: String,
            val book: EpubBook,
        ) : Doc {
            override val count: Int get() = book.spine.size
        }

        /**
         * 흐름 문서 — docx·xlsx·pptx(12단계). 화면은 포맷을 모르고 `FlowDocument` 계약만 안다.
         * 자리는 **부분**(시트·슬라이드·글의 조각)이다.
         */
        data class Flow(
            override val path: String,
            override val name: String,
            val flow: FlowDocument,
        ) : Doc {
            override val count: Int get() = flow.parts.size
        }
    }

    sealed interface State {
        /**
         * 여는 중. [reached] 는 **이번 열기에서 파일에 닿았다**(실제로 열어 봤다 — [reachState])는 표시다. 닿기 전에는
         * 파일이 없거나 열리지 않을 수 있으므로 막대의 '다른 앱으로 열기' 를 두지 않는다([DocOpenWith.inMenu]). 닿은
         * 뒤의 판별·여는 일(docx·HWPX 는 몇 초, 상한 30초)은 기다리지 않고 넘길 수 있다.
         */
        data class Loading(val reached: Boolean = false) : State
        data class Ready(val doc: Doc) : State

        /**
         * 열지 못했다. **종류를 우리가 새로 만들지 않고 [OpenFailure] 를 그대로 쓴다** —
         * 그쪽이 `sealed` 라 화면의 `when` 이 빠뜨리면 컴파일러가 잡는다.
         *
         * [unreachable] 은 **파일에 닿지 못했다**(없다·열리지 않는다 — [unreachableState])는 표시다. 종류는 공용 매핑이
         * 준 것(`Io`·`NoPermission`)이라 '다른 앱으로 열기' 를 권하지 않는 것은 그대로고([DocOpenWith]), 바뀌는 것은
         * 문장뿐이다 — `Io` 의 '입출력이 실패했습니다' 는 없어진 파일을 디스크가 고장 난 것처럼 읽히게 한다(함정 표의
         * '깨진 ZIP' 과 같은 모양이다). 텍스트·압축 화면이 같은 자리에 쓰는 '이 파일을 읽을 수 없습니다' 로 알린다.
         */
        data class Failed(val failure: OpenFailure, val unreachable: Boolean = false) : State
    }

    /** 화면이 한 번 보여 주고 마는 것. */
    sealed interface Event {
        /**
         * 저장된 자리가 있다. 화면이 '이어서 봅니다' 를 띄운다.
         *
         * **문서의 종류를 함께 싣는다.** 이 사건은 `Ready` 보다 **먼저** 나가므로
         * (되살린 뒤에 화면을 세우는 순서 때문이다) 화면이 상태를 보고 '쪽' 인지
         * '장' 인지 고를 수가 없다. 함께 쓸 값은 한 흐름에 실어 보낸다 — 10단계가
         * `State.currentKind` 로 같은 결론에 닿았다.
         */
        data class Resumed(val page: Int, val unit: ResumeUnit, val inPlace: Boolean = false) : Event

        /**
         * 흐름 문서를 **처음** 열었다(저장된 자리가 없다). 화면이 '쪽 모양은 원본과 다르다' 를
         * 한 번 알린다 — 요구사항의 '첫 진입 고지'(CLAUDE.md '지원하지 않는 것').
         *
         * 문서마다 한 번이다. 앱 전체에 한 번이면 다른 종류(시트·슬라이드)의 다른 한계를 알릴
         * 기회가 사라지고, 매번이면 소음이 된다. 자리 기록은 닫을 때 언제나 남으므로(0쪽이라도)
         * '기록이 없다' 가 곧 '처음이다' 다.
         *
         * ([inPlace] 는 [Resumed] 의 것이다 — 첫 장·부분 **안에서** 이어 보면 '1장부터' 가 아니라 '읽던 자리부터' 라고 말한다.)
         */
        data class FirstFlowEntry(val kind: FlowKind) : Event

        /**
         * 고정 레이아웃 쪽이 있는 EPUB 을 **처음** 열었다. 화면이 '쪽마다 화면에 맞춰 보여 준다' 를 한 번 알린다 —
         * 글자 크기·여백·바탕이 그 쪽에는 걸리지 않는 까닭을 사용자가 알게. 문서마다 한 번인 것은 [FirstFlowEntry] 와 같다.
         */
        data object FixedLayoutEntry : Event
    }

    /** 이어보기 안내가 세는 단위. 문서의 종류에서 정해진다. */
    enum class ResumeUnit { PAGE, CHAPTER, SHEET, SLIDE, PART }

    private val _state = MutableStateFlow<State>(State.Loading())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    /** 지금 장·부분 안의 자리(0~1, 화면의 앞 가장자리). 흐름으로 내보내지 않는다(머리말). */
    private var fraction = 0f

    /** 지금 장·부분 안에서 본 몫(0~1, 화면의 끝 가장자리). 진행(`progress`)이 쓴다([DocLocator]). */
    private var seen = 0f

    private val _jump = MutableStateFlow(0)

    /** '같은 쪽 안에서 옮겨라' 의 일련번호. 옮길 자리는 [fractionNow] 가 준다. */
    val jump: StateFlow<Int> = _jump.asStateFlow()

    /** 화면이 쪽을 열 때 묻는 지금 자리. */
    fun fractionNow(): Float = fraction

    /**
     * 읽는 모양(글자 크기·여백·바탕). 설정 화면과 이 화면의 '보기' 판이 **같은 값**을 쓴다(`AppPreferences`).
     * 아직 읽지 않았으면 null — 화면은 그동안 쪽을 열지 않는다(기본 모양으로 한 번 그렸다가 다시 그리지 않게).
     */
    private val _appearance = MutableStateFlow<ReaderAppearance?>(null)
    val appearance: StateFlow<ReaderAppearance?> = _appearance.asStateFlow()

    private val prefs = AppPreferences(app.applicationContext)

    /** 쓰는 중에 저장소가 돌려주는 옛 값을 거른다([LocalFirst]). */
    private val appearanceWrites = LocalFirst<ReaderAppearance>()

    init {
        viewModelScope.launch {
            // 설정 파일이 깨졌으면 기본값으로 읽는다 — 읽는 모양 하나 때문에 문서 화면이 죽지 않게.
            prefs.readerAppearance
                .catch { emit(ReaderAppearance()) }
                .collect { stored -> appearanceWrites.stored(stored)?.let { _appearance.value = it } }
        }
    }

    /** '보기' 판이 고른 모양. 곧바로 화면에 걸고(열린 문서에도) 설정에 남긴다. */
    fun setAppearance(value: ReaderAppearance) {
        _appearance.value = value
        appearanceWrites.beginWrite()
        viewModelScope.launch {
            try {
                prefs.setReaderAppearance(value)
            } catch (e: IOException) {
                // 쓰지 못해도 화면에는 걸려 있다(다음에 열 때 옛 모양일 뿐이다). 읽는 모양 하나로 문서 화면이 죽지 않게.
                Iro.d(TAG) { "읽는 모양을 쓰지 못했다: ${e::class.java.simpleName}" }
            } finally {
                appearanceWrites.endWrite()
            }
        }
    }

    /**
     * PDF 의 두 쪽 보기. 가로 화면에서만 뜻이 있다(화면이 가른다).
     *
     * **문서를 바꿔도 남긴다** — [open] 이 첫머리에서 되돌리는 것은 문서의 상태(읽던 쪽)이고 이것은 보는 사람의 모양이다.
     * 앱 전체의 설정으로 남긴다(`AppPreferences.pdfSpread` — 처음에는 세션 동안만 남았다).
     */
    private val _spread = MutableStateFlow(false)
    val spread: StateFlow<Boolean> = _spread.asStateFlow()

    /** 사용자가 이 화면에서 골랐다 — 늦게 끝난 설정 읽기가 그것을 덮지 않게. */
    private var spreadChosen = false

    init {
        viewModelScope.launch {
            val saved = try {
                prefs.pdfSpread.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (saved != null && !spreadChosen) _spread.value = saved
        }
    }

    fun setSpread(on: Boolean) {
        spreadChosen = true
        _spread.value = on
        viewModelScope.launch {
            try {
                prefs.setPdfSpread(on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 남기지 못해도 이 세션의 보기는 그대로다.
            }
        }
    }

    /** PDF 찾기(API 35 이상). */
    private val _search = MutableStateFlow(PdfSearch.State())
    internal val search: StateFlow<PdfSearch.State> = _search.asStateFlow()
    private var searchJob: Job? = null

    /** 이 기기에서 PDF 찾기가 되는가. 화면이 단추를 띄울지 정한다. */
    @get:ChecksSdkIntAtLeast(api = 35)
    val canSearchPdf: Boolean get() = Build.VERSION.SDK_INT >= 35

    /**
     * 버린 것과 경고.
     *
     * **`UnsupportedFeatures` 를 화면이 직접 보게 두면 배지가 뜨지 않는다.** 그것은
     * 그냥 가변 객체라 값이 늘어도 재구성이 일어나지 않고, 장을 읽는 일은 WebView 의
     * **다른 스레드**에서 일어난다(기기에서 그 증상을 봤다 — 스크립트를 버린 장을
     * 보고 있는데 알림 단추가 없었다). 흐름으로 내보내는 쪽이 유일하게 도는 길이다.
     */
    private val _notice = MutableStateFlow(Notice())
    val notice: StateFlow<Notice> = _notice.asStateFlow()

    /** 화면이 보여 줄 알림 한 벌. */
    data class Notice(
        val warnings: List<ParseWarning> = emptyList(),
        val dropped: Map<String, Int> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = warnings.isEmpty() && dropped.isEmpty()
    }

    /**
     * 장을 읽고 난 뒤 화면이 부른다. **다른 스레드에서 온다** —
     * `MutableStateFlow.value` 는 그것을 견딘다.
     */
    fun noteRead() {
        val document: io.github.donggi.iroiroviewer.format.OpenedDocument = when (val d = (_state.value as? State.Ready)?.doc) {
            is Doc.Epub -> d.book
            is Doc.Flow -> d.flow
            else -> return
        }
        _notice.value = Notice(document.warnings, document.unsupported.snapshot())
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<Event> = _events.asSharedFlow()

    /** 지금 열려 있는 문서의 신원. `(경로, 크기, 수정시각)` 이다. */
    private var opened: Triple<String, Long, Long>? = null

    private var document: OpenedDocument? = null
    private var key: String? = null
    private var budget: ImageLimits.Budget? = null
    private var saveJob: Job? = null
    private var openJob: Job? = null

    fun open(path: String) {
        val file = File(path)
        val want = Triple(path, file.length(), file.lastModified())
        // **같은 문서면 다시 열지 않는다** — 열려 있을 때뿐 아니라 여는 중이거나 암호를
        // 기다리는 중일 때도. 화면이 새로 구성되면(회전) `LaunchedEffect(path)` 가 이 함수를
        // 다시 부르는데, 그때 다시 열면 **넣은 암호로 푸는 중이던 작업이 취소되고** 암호
        // 없이 새로 시작해 사용자가 넣은 암호가 사라진다.
        if (opened == want && !needsReopen(_state.value)) return

        // **앞 문서의 상태를 여기서 끊는다.** 아래 코루틴이 실패로 끝나도 남지 않는다.
        openJob?.cancel()
        clearSearch()
        closeDocument()
        opened = want
        key = null
        _page.value = 0
        fraction = 0f
        seen = 0f
        _notice.value = Notice()
        start(file, password = null, reached = false)
    }

    /**
     * 사용자가 넣은 암호로 지금 문서를 다시 연다.
     *
     * **배열의 주인이 여기로 넘어온다.** 여는 일이 끝나면(성공·실패·취소 어느 쪽이든)
     * 0 으로 덮는다. 저장하지 않는다 — `SavedStateHandle` 에도 DB 에도 로그에도. 그래서
     * 프로세스가 죽었다 살아나면 다시 묻는다. 암호를 기억해 두는 편의보다 **암호가 어디에도
     * 남지 않는 것**이 이 앱의 쪽이다.
     */
    fun submitPassword(password: CharArray) {
        val path = opened?.first
        if (path == null || _state.value is State.Ready) {
            password.fill('\u0000')
            return
        }
        openJob?.cancel()
        // 암호를 물은 것은 여는이가 이 파일을 읽었기 때문이다 — 닿은 채로 시작한다. 닿지 않은 채로 시작하면 막대의 ⋮ 가
        // 암호 화면(있다) → 여는 중(없다) → 닿았다(있다)로 한 번 깜박인다. 그사이 파일이 없어졌으면 아래 검사가 실패 화면으로
        // 바꾸고, 그 전에 누른 것은 `ExternalOpen` 이 '넘길 수 없다' 로 받는다(없는 파일에는 URI 를 만들지 않는다).
        start(File(path), password, reached = true)
    }

    /**
     * 여는 일이 [failure] 로 끝난 뒤의 실패 화면. **실패한 뒤에 파일에 한 번 더 닿아 본다** — 여는 동안 없어진 파일의 실패를
     * 그 종류대로 두면 '깨졌다'·'다루지 않는다' 가 되어 없는 파일에 '다른 앱으로 열기' 를 권한다([failedAfter]). 실패한 길에서만
     * 서술자 하나를 열고 닫는다.
     */
    private suspend fun failedState(file: File, failure: OpenFailure): State.Failed =
        withContext(IroDispatchers.io) { failedAfter(failure, file.isFile, openErrorOf(file)) }

    /** 이미 같은 문서를 붙들고 있어도 다시 열어야 하는가. 실패했을 때뿐이다(암호 대기는 빼고). */
    private fun needsReopen(state: State): Boolean =
        state is State.Failed && state.failure !is OpenFailure.PasswordRequired

    private fun start(file: File, password: CharArray?, reached: Boolean) {
        val path = file.path
        _state.value = State.Loading(reached)
        openJob = viewModelScope.launch {
            try {
                openNow(file, path, password)
            } finally {
                // 성공·실패·취소 어느 쪽이든 여기서 지운다. 여는이는 지우지 않는다(주인이 아니다).
                password?.fill('\u0000')
            }
        }
    }

    private suspend fun openNow(file: File, path: String, password: CharArray?) {
        val budget = budget ?: defaultBudget()
        val source = FileDocumentSource(file)
        // **파일에 닿는지 판별보다 먼저 본다.** 판별은 앞부분을 읽지 못하면 조용히 '모르는 형식' 으로 끝나(`FormatRegistry`
        // 가 예외를 삼킨다) 없어진 파일이 '이 앱이 다루지 않는 문서입니다' 가 되고, 그 갈래는 '다른 앱으로 열기' 까지
        // 권한다([DocOpenWith]) — 받는 앱도 같은 파일에 닿지 못한다. 프로세스가 되살린 화면이 그사이 휴지통으로 간 파일을
        // 여는 것이 그 길이다(앱의 화면 상태는 경로를 저장해 둔다). `canRead()` 로 묻지 않고 **실제로 열어 본다** — 묻는 답과
        // 여는 답이 FUSE 위에서 같다는 것을 확인한 적이 없다. 치르는 값은 서술자 하나를 열고 닫는 것이다.
        // 닿았으면 '닿은 채로 여는 중' 을 낸다 — 그때부터 막대가 '다른 앱으로 열기' 를 둔다([State.Loading.reached]).
        val reach = withContext(IroDispatchers.io) { reachState(file.isFile, openErrorOf(file)) }
        _state.value = reach
        if (reach is State.Failed) return
        // **무엇을 여는지 우리가 정하지 않는다**(`DocumentSupport` 의 주석). 판별은 파일 앞부분을
        // 읽고 ZIP 이면 중앙 디렉터리까지 읽으므로 **주 스레드에서 하지 않는다**(디스패처 규칙).
        val opener = withContext(IroDispatchers.io) { DocumentSupport.registry.openerFor(source, password) }
        if (opener == null) {
            // 판별은 읽기 실패를 삼키므로 그사이 없어진 파일도 여기로 온다 — 한 번 더 닿아 본다([failedState]).
            _state.value = failedState(file, OpenFailure.Unsupported("여는이가 없다"))
            return
        }

        // **취소가 문서를 잃어버리는 창을 막는다.** `Documents.open` 은 `withTimeout`
        // 안에서 문서를 만들어 밖으로 주는데, 그 사이 취소되면 코루틴이 값을 버린다 —
        // 받은 적이 없으니 닫을 수 없고 pdfium 문서와 파일 서술자가 GC 를 기다린다.
        // 그래서 **만든 자리에서** 받아 두고, 성공 경로에서만 주인을 바꾼다.
        var orphan: OpenedDocument? = null
        try {
            // **여는 일은 주 스레드에서 하지 않는다**(디스패처 규칙). 여는이 대부분(EPUB·OOXML)은 스스로
            // 디스패처를 고르지 않는 순수 JVM 코드라, 여기서 옮기지 않으면 ZIP·XML 을 주 스레드에서 읽는다 —
            // 큰 문서에서 화면이 멎고 시간 상한(`withTimeout`)도 뒤로가기도 끼어들 틈이 없다(12단계 검토가
            // 잡았다. 11단계의 EPUB 도 같았다). 문서는 `onOpen` 으로 받으므로 돌아오는 사이에 취소돼도 새지 않는다.
            val outcome = withContext(IroDispatchers.parsing) { Documents.open(opener, source) { orphan = it } }

            when (outcome) {
                is OpenOutcome.Failed -> {
                    Iro.d(TAG) { "열지 못했다: ${outcome.failure::class.java.simpleName}" }
                    // 여는 데 30초까지 걸린다. 그사이 SD 를 빼거나 다른 앱이 지우면 여는이는 입출력 예외를 '깨졌다' 로
                    // 옮긴다(함정 표의 '깨진 ZIP') — 한 번 더 닿아 보고 가른다([failedState]).
                    _state.value = failedState(file, outcome.failure)
                }

                is OpenOutcome.Success -> {
                    val doc = docOf(outcome.document, path, file.name, budget)
                    if (doc == null) {
                        _state.value = State.Failed(OpenFailure.Unsupported("다루지 않는 문서"))
                        return
                    }
                    document = outcome.document
                    orphan = null // 주인이 바뀌었다
                    // **되살린 뒤에 `Ready` 를 낸다.** 차례가 뒤집히면 되살린 쪽이 지워진다.
                    val seenBefore = restore(file, doc)
                    _state.value = State.Ready(doc)
                    val firstEntry = when {
                        seenBefore -> null
                        doc is Doc.Flow -> Event.FirstFlowEntry(doc.flow.kind)
                        doc is Doc.Epub && doc.book.hasFixedLayout -> Event.FixedLayoutEntry
                        else -> null
                    }
                    if (firstEntry != null) {
                        _events.tryEmit(firstEntry)
                        // 기록을 **지금** 남긴다. 닫을 때만 쓰면 닫지 않고 떠난 길(프로세스가 죽었다)에서
                        // 기록이 없어 다음에 또 '처음' 으로 알린다.
                        scheduleSave()
                    }
                    // 여는 동안 나온 경고는 지금 이미 안다. 버린 것은 장·부분을 읽어야 는다.
                    if (doc is Doc.Epub) _notice.value = Notice(doc.book.warnings, emptyMap())
                    if (doc is Doc.Flow) _notice.value = Notice(doc.flow.warnings, doc.flow.unsupported.snapshot())
                }
            }
        } finally {
            // 취소·실패로 버려진 것을 닫는다.
            orphan?.close()
        }
    }

    /**
     * 열린 문서를 화면이 쓰는 모양으로.
     *
     * **`else` 로 끝내지 않는다.** 12·13단계가 새 문서 타입을 더하면 여기 한 줄이
     * 늘어야 하고, 잊으면 '열렸는데 화면이 비어 있다' 가 된다. null 로 끝나는 길이
     * 있는 것은 이음매가 우리가 모르는 것을 줄 수 있기 때문이다.
     */
    private fun docOf(
        document: OpenedDocument,
        path: String,
        name: String,
        budget: ImageLimits.Budget,
    ): Doc? = when (document) {
        is PdfDocument -> Doc.Pdf(path, name, document.pageCount, PdfPageStore(document, budget))
        is EpubBook -> Doc.Epub(path, document.title.ifBlank { name }, document)
        is FlowDocument -> Doc.Flow(path, document.title.ifBlank { name }, document)
        else -> null
    }

    /**
     * 저장된 자리를 되살린다.
     *
     * **자리의 수가 다르면 저장된 번호를 믿지 않는다** — 파일이 바뀌었다는 뜻이고,
     * 그 번호는 다른 곳을 가리킨다.
     *
     * @return 이 문서의 기록이 있었는가(자리를 되살리지 않았어도). 첫 진입 고지가 쓴다.
     */
    private suspend fun restore(file: File, doc: Doc): Boolean {
        val fileKey = FileKey.of(file.name, file.length(), file.lastModified())
        key = fileKey
        val saved = withContext(IroDispatchers.io) {
            IroiroDatabase.get(context).docProgress().find(fileKey)
        } ?: return false
        val page = saved.page ?: return true
        if (saved.pageCount != null && saved.pageCount != doc.count) {
            Iro.d(TAG) { "자리 수가 달라졌다(${saved.pageCount} → ${doc.count}). 저장된 자리를 버린다" }
            return true
        }
        if (page !in 0 until doc.count) return true
        // 장 안의 자리. 적힌 장 번호가 `page` 와 다르면 믿지 않는다(`DocLocator.decode`). PDF 는 쓰지 않는다.
        val inside = if (doc is Doc.Pdf) null else DocLocator.decode(saved.locator, page)
        // **첫 장의 처음이면 알릴 것이 없다**(11단계부터 그랬다). 첫 장 **안의** 자리는 되살리고 알린다 — '처음부터' 로
        // 돌아갈 길이 그 알림에 있다.
        if (page == 0 && (inside == null || inside < RESUME_MIN_FRACTION)) return true
        _page.value = page
        fraction = inside ?: 0f
        // 본 몫은 화면이 쪽을 다 읽은 뒤 알린다. 그 전에 떠나면 자리만큼은 본 것으로 둔다(진행이 뒤로 가지 않게).
        seen = fraction
        _events.tryEmit(Event.Resumed(page, unitOf(doc), inPlace = page == 0))
        return true
    }

    private fun unitOf(doc: Doc): ResumeUnit = when (doc) {
        is Doc.Pdf -> ResumeUnit.PAGE
        is Doc.Epub -> ResumeUnit.CHAPTER
        is Doc.Flow -> when (doc.flow.kind) {
            FlowKind.SHEETS -> ResumeUnit.SHEET
            FlowKind.SLIDES -> ResumeUnit.SLIDE
            FlowKind.DOCUMENT -> ResumeUnit.PART
        }
    }

    fun onPageChanged(ordinal: Int) {
        if (ordinal == _page.value) return
        _page.value = ordinal
        // 다른 장으로 갔다 — 그 장의 처음이다. 앞 장의 비율을 새 장에 옮기지 않는다.
        fraction = 0f
        seen = 0f
        scheduleSave()
    }

    /**
     * 화면이 장·부분 안의 자리를 알린다. 스크롤할 때마다 온다 — 값만 적어 두고 기록은 모아서 쓴다([scheduleSave]).
     *
     * **[chapter] 가 지금 장이 아니면 버린다.** 장이 바뀌는 사이에 앞 장의 WebView 가 보낸 값이 새 장의 자리로 적히면
     * 다음에 엉뚱한 곳에서 연다.
     */
    fun onFraction(chapter: Int, value: Float) {
        if (chapter != _page.value || value.isNaN()) return
        val f = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(f - fraction) < FRACTION_EPSILON) return
        fraction = f
        scheduleSave()
    }

    /** 화면이 장·부분 안에서 본 몫을 알린다(`LockedWebView.onSeen`). 받는 규칙은 [onFraction] 과 같다. */
    fun onSeen(chapter: Int, value: Float) {
        if (chapter != _page.value || value.isNaN()) return
        val s = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(s - seen) < FRACTION_EPSILON) return
        seen = s
        scheduleSave()
    }

    /** 처음부터 본다. 이어보기 안내의 단추가 부른다. */
    fun restart() = startAt(0)

    /**
     * 장·부분 [ordinal] 의 **처음**으로 간다(목차에서 골랐다). 지금 보는 장을 골라도 그 장의 처음으로 간다 — 예전에는
     * 쪽이 바뀌지 않아 아무 일도 없었다.
     */
    fun startAt(ordinal: Int) {
        _page.value = ordinal
        fraction = 0f
        seen = 0f
        // 장이 그대로면 쪽이 바뀌지 않아 화면이 옮길 까닭을 모른다 — 일련번호로 알린다(함정 표의 `MutableStateFlow`).
        _jump.value++
        scheduleSave()
    }

    // ---- PDF 찾기(API 35 이상) -----------------------------------------------------------------------

    /**
     * [raw] 를 찾는다. 보고 있는 쪽부터 끝까지, 그다음 처음부터 — 첫 결과가 '다음' 이다([PdfSearch.order]).
     *
     * 쪽 하나를 찾는 동안만 문서의 잠금을 쥐므로(`PdfDocument.search`) 찾는 중에도 쪽을 넘기고 그릴 수 있다. 새로 찾거나
     * 문서를 닫으면 앞의 찾기를 취소한다 — 쪽 하나를 찾는 도중에는 취소가 닿지 않지만(렌더와 같다) 다음 쪽 전에 멈춘다.
     */
    fun search(raw: String) {
        // 갈래를 함수 하나로 가른다 — 코루틴 람다 안의 호출은 lint 가 바깥의 `SDK_INT` 검사를 보지 못한다(NewApi 0 을 지킨다).
        if (Build.VERSION.SDK_INT >= 35) searchFrom35(raw)
    }

    @RequiresApi(35)
    private fun searchFrom35(raw: String) {
        val pdf = (_state.value as? State.Ready)?.doc as? Doc.Pdf ?: return
        val query = PdfSearch.normalize(raw)
        searchJob?.cancel()
        if (query == null) {
            _search.value = PdfSearch.State()
            return
        }
        val from = _page.value
        val order = PdfSearch.order(from, pdf.count)
        _search.value = PdfSearch.State(query = query, total = order.size)
        searchJob = viewModelScope.launch {
            for (ordinal in order) {
                val found = pdf.store.search(ordinal, query).orEmpty()
                val before = _search.value
                val next = before.add(found, from)
                _search.value = next
                // 처음 찾은 결과로 간다. 그 뒤로는 사용자가 옮긴다.
                if (before.current < 0 && next.current >= 0) goTo(next)
                if (next.capped) break
            }
        }
    }

    fun nextMatch() = goTo(_search.value.next())

    fun previousMatch() = goTo(_search.value.previous())

    private fun goTo(next: PdfSearch.State) {
        _search.value = next
        next.currentMatch?.let { onPageChanged(it.page) }
    }

    /**
     * [path] 의 문서에서 찾기가 살아 있는가. 화면이 다시 만들어질 때 찾기 막대를 다시 세울지 정한다 — **문서를 함께 본다**:
     * 다른 문서를 여는 순간에는 앞 문서의 찾기가 아직 남아 있다(`open` 이 효과에서 돌아 지우기 전이다).
     */
    fun isSearching(path: String): Boolean = opened?.first == path && _search.value.active

    /** 찾기를 닫는다. */
    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _search.value = PdfSearch.State()
    }

    /**
     * 뷰포트가 정해졌다. 예산이 여기서 나온다.
     *
     * 회전하면 예산이 달라지므로 **옛 크기로 뜬 비트맵을 놓는다** — 쓸모가 없고,
     * 다시 보이는 순간 새 예산으로 만들어진다. EPUB 에는 우리가 든 비트맵이 없다
     * (그림은 WebView 가 들고 관리한다).
     */
    fun onViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val next = ImageLimits.budgetOf(width, height, memoryClassMb())
        if (next == budget) return
        val first = budget == null
        budget = next
        if (!first) ((_state.value as? State.Ready)?.doc as? Doc.Pdf)?.store?.clearBitmaps()
        Iro.d(TAG) {
            "예산 base=${next.baseBytes} live=${next.liveCap} detail=${next.detailCap} pages=${next.livePages}"
        }
    }

    /**
     * 쪽을 넘길 때마다 DB 를 쓰지 않는다. 빠르게 넘기면 초에 열 번도 쓰게 된다 —
     * 마지막 값만 남으면 되므로 짧게 모아 쓴다.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    private suspend fun save() {
        val fileKey = key ?: return
        val doc = (_state.value as? State.Ready)?.doc ?: return
        val entity = entityOf(fileKey, doc)
        // 취소를 삼키지 않는다 — `runCatching` 을 쓰면 취소된 코루틴이 끝까지 달린다.
        withContext(IroDispatchers.io) {
            IroiroDatabase.get(context).docProgress().upsert(entity)
        }
    }

    /**
     * 지금 자리의 기록. PDF 는 쪽만(`locator`·`progress` 는 비운다), 흐름 문서는 장 안의 자리와 문서 전체의 진행까지
     * ([DocLocator.fields] — 왜 PDF 에 진행을 적지 않는지도 거기 있다).
     */
    private fun entityOf(fileKey: String, doc: Doc): DocProgressEntity {
        val page = _page.value
        val fields = DocLocator.fields(paged = doc is Doc.Pdf, index = page, fraction = fraction, seen = seen, count = doc.count)
        return DocProgressEntity(
            fileKey = fileKey,
            page = page,
            pageCount = doc.count,
            locator = fields.locator,
            progress = fields.progress,
            displayName = doc.name,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 화면을 떠난다. **문서를 닫는 유일한 자리다.**
     *
     * **상태는 그 자리에서 끊는다.** 예전에는 마지막 자리를 DB 에 쓴 **뒤에** `opened`·상태를
     * 비웠는데, 그 쓰기가 끝나기 전에 같은 문서를 다시 누르면 `open` 이 '이미 열려 있다' 로
     * 돌아가고, 곧이어 늦은 정리가 그 문서를 닫아 **끝나지 않는 동그라미**가 남았다(적대적
     * 검토가 잡았다). 저장할 값과 닫을 문서는 지금 붙들어 두고, 쓰기가 끝나면 **붙든 것**을 닫는다.
     */
    fun close() {
        openJob?.cancel()
        saveJob?.cancel()
        clearSearch()
        val closing = document
        val doc = (_state.value as? State.Ready)?.doc
        val entity = key?.let { k -> doc?.let { entityOf(k, it) } }
        document = null
        opened = null
        key = null
        _state.value = State.Loading()
        val db = IroiroDatabase.get(context)
        // **VM 스코프가 아니라 앱 수명의 IO 스코프에서** 쓰고 닫는다. 흐름 문서의 `close` 는 그리기 잠금을
        // 기다리므로 주 스레드에서 부르면 큰 시트를 그리는 동안 화면이 멎는다(12단계 검토가 잡았다). VM 이
        // 곧 치워지면(`onCleared`) VM 스코프의 일은 시작도 못 하고 취소되어 문서가 닫히지 않는다.
        closer.launch {
            try {
                // 나가기 전에 마지막 자리를 남긴다.
                if (entity != null) db.docProgress().upsert(entity)
            } finally {
                closing?.close()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeDocument()
    }

    /** 문서를 떼어 **주 스레드 밖에서** 닫는다(위 [close] 의 주석). */
    private fun closeDocument() {
        val d = document ?: return
        document = null
        closer.launch { d.close() }
    }

    private val context: Context get() = getApplication<Application>().applicationContext

    private fun memoryClassMb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.memoryClass
    }

    /** 뷰포트를 아직 모를 때의 예산. 화면 전체를 기준으로 잡는다. */
    private fun defaultBudget(): ImageLimits.Budget {
        val m = context.resources.displayMetrics
        return ImageLimits.budgetOf(m.widthPixels, m.heightPixels, memoryClassMb())
            .also { budget = it }
    }

    private companion object {
        const val TAG = "docview"
        const val SAVE_DELAY_MS = 700L

        /** 이만큼 달라져야 새 자리로 친다. 손가락이 멎은 뒤의 1화소 떨림으로 기록을 다시 쓰지 않게. */
        const val FRACTION_EPSILON = 0.0005f

        /** 첫 장 안에서 이만큼은 와야 '이어서 봅니다' 를 띄운다 — 처음 몇 줄을 내린 것은 이어 볼 자리가 아니다. */
        const val RESUME_MIN_FRACTION = 0.02f

        /** 닫기와 마지막 기록. 화면·VM 보다 오래 산다 — 떠나는 순간에 시작한 일이 끝까지 가야 한다. */
        val closer = CoroutineScope(SupervisorJob() + IroDispatchers.io)
    }
}

/**
 * 파일에 닿지 못하는 실패. 닿으면 null — 그때부터는 판별과 여는이가 정한다.
 *
 * **여는 데 난 예외는 공용 매핑(`toOpenFailure`)에 맡긴다** — 여는이가 같은 파일을 받았을 때 내는 답과 같게(권한은
 * `NoPermission`, 나머지 입출력은 `Io`). 없는 파일(폴더 포함)도 `Io` 다 — `FileNotFoundException` 이 `IOException` 이라 공용
 * 매핑도 그렇게 답한다. 셋 다 [DocOpenWith.onFailure] 가 '권하지 않는다' 로 읽는다.
 */
internal fun reachFailureOf(isFile: Boolean, openError: Throwable?): OpenFailure? = when {
    !isFile -> OpenFailure.Io("파일이 없다")
    openError != null -> openError.toOpenFailure()
    else -> null
}

/**
 * 파일에 닿지 못했을 때의 실패 화면 상태. 닿으면 null.
 *
 * 종류는 [reachFailureOf] 의 것 그대로이고, **문장을 가르는 표시([DocViewModel.State.Failed.unreachable])를 함께 세운다**
 * — 없는 파일이 '입출력이 실패했습니다' 로 나가지 않게(그 표시의 주석).
 */
internal fun unreachableState(isFile: Boolean, openError: Throwable?): DocViewModel.State.Failed? =
    reachFailureOf(isFile, openError)?.let { DocViewModel.State.Failed(it, unreachable = true) }

/**
 * 파일에 닿아 보고 난 뒤의 상태 — 닿지 못했으면 그 실패 화면([unreachableState]), 닿았으면 **닿은 채로 여는 중**이다.
 * 둘 사이에 다른 답은 없다: 여는 중의 막대가 '다른 앱으로 열기' 를 두는 것은 이 함수가 닿았다고 답한 뒤뿐이다
 * ([DocOpenWith.inMenu]).
 */
internal fun reachState(isFile: Boolean, openError: Throwable?): DocViewModel.State =
    unreachableState(isFile, openError) ?: DocViewModel.State.Loading(reached = true)

/**
 * 여는 일이 [failure] 로 끝난 **뒤에** 파일에 닿아 본 답으로 실패 화면을 정한다. 닿지 못하면 무엇으로 실패했든 닿지 못한 실패
 * 화면([unreachableState] — 단추도 ⋮ 도 없다)이고, 닿으면 여는이의 답 그대로다. 열기 전에 닿았어도([reachState]) 여는 동안
 * 없어질 수 있다 — 판별은 그 읽기 실패를 삼켜 '여는이가 없다(Unsupported)' 로, OOXML·EPUB 은 리더를 여는 자리의 입출력 예외를
 * '깨졌다(Corrupt)' 로 낸다. 둘 다 '다른 앱으로 열기' 를 권하는 갈래다.
 */
internal fun failedAfter(failure: OpenFailure, isFile: Boolean, openError: Throwable?): DocViewModel.State.Failed =
    unreachableState(isFile, openError) ?: DocViewModel.State.Failed(failure)

/**
 * [file] 을 읽으려고 열어 본다. 열리면 null, 아니면 그 예외(메시지는 화면에 나가지 않는다 — [reachFailureOf] 가 종류만 본다).
 * 탐침은 [FileProbe] 한 벌이다 — 압축·만화·이미지 뷰어가 같은 답을 낸다.
 */
internal fun openErrorOf(file: File): Throwable? = FileProbe.openError(file)
