package io.github.donggi.iroiroviewer.docview

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.data.DocProgressEntity
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.docview.pdf.PdfDocument
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.format.Documents
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.epub.EpubBook
import io.github.donggi.iroiroviewer.io.FileKey
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
 * 보고 있는가'. EPUB 의 장 안쪽 위치(스크롤)는 아직 저장하지 않는다. 그 칸
 * (`locator`·`progress`)은 비어 있고, 채우는 것은 장 안 위치를 실제로 쓸 때다.
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
        data object Loading : State
        data class Ready(val doc: Doc) : State

        /**
         * 열지 못했다. **종류를 우리가 새로 만들지 않고 [OpenFailure] 를 그대로 쓴다** —
         * 그쪽이 `sealed` 라 화면의 `when` 이 빠뜨리면 컴파일러가 잡는다.
         */
        data class Failed(val failure: OpenFailure) : State
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
        data class Resumed(val page: Int, val unit: ResumeUnit) : Event

        /**
         * 흐름 문서를 **처음** 열었다(저장된 자리가 없다). 화면이 '쪽 모양은 원본과 다르다' 를
         * 한 번 알린다 — 요구사항의 '첫 진입 고지'(CLAUDE.md '지원하지 않는 것').
         *
         * 문서마다 한 번이다. 앱 전체에 한 번이면 다른 종류(시트·슬라이드)의 다른 한계를 알릴
         * 기회가 사라지고, 매번이면 소음이 된다. 자리 기록은 닫을 때 언제나 남으므로(0쪽이라도)
         * '기록이 없다' 가 곧 '처음이다' 다.
         */
        data class FirstFlowEntry(val kind: FlowKind) : Event
    }

    /** 이어보기 안내가 세는 단위. 문서의 종류에서 정해진다. */
    enum class ResumeUnit { PAGE, CHAPTER, SHEET, SLIDE, PART }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

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
        closeDocument()
        opened = want
        key = null
        _page.value = 0
        _notice.value = Notice()
        start(file, password = null)
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
        start(File(path), password)
    }

    /** 이미 같은 문서를 붙들고 있어도 다시 열어야 하는가. 실패했을 때뿐이다(암호 대기는 빼고). */
    private fun needsReopen(state: State): Boolean =
        state is State.Failed && state.failure !is OpenFailure.PasswordRequired

    private fun start(file: File, password: CharArray?) {
        val path = file.path
        _state.value = State.Loading
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
        // **무엇을 여는지 우리가 정하지 않는다**(`DocumentSupport` 의 주석). 판별은 파일 앞부분을
        // 읽고 ZIP 이면 중앙 디렉터리까지 읽으므로 **주 스레드에서 하지 않는다**(디스패처 규칙).
        val opener = withContext(IroDispatchers.io) { DocumentSupport.registry.openerFor(source, password) }
        if (opener == null) {
            _state.value = State.Failed(OpenFailure.Unsupported("여는이가 없다"))
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
                    _state.value = State.Failed(outcome.failure)
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
                    if (doc is Doc.Flow && !seenBefore) {
                        _events.tryEmit(Event.FirstFlowEntry(doc.flow.kind))
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
        if (page !in 1 until doc.count) return true
        _page.value = page
        _events.tryEmit(Event.Resumed(page, unitOf(doc)))
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
        scheduleSave()
    }

    /** 처음부터 본다. 이어보기 안내의 단추가 부른다. */
    fun restart() {
        _page.value = 0
        scheduleSave()
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
        val entity = DocProgressEntity(
            fileKey = fileKey,
            page = _page.value,
            pageCount = doc.count,
            displayName = doc.name,
            updatedAt = System.currentTimeMillis(),
        )
        // 취소를 삼키지 않는다 — `runCatching` 을 쓰면 취소된 코루틴이 끝까지 달린다.
        withContext(IroDispatchers.io) {
            IroiroDatabase.get(context).docProgress().upsert(entity)
        }
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
        val closing = document
        val doc = (_state.value as? State.Ready)?.doc
        val entity = key?.let { k ->
            doc?.let {
                DocProgressEntity(
                    fileKey = k,
                    page = _page.value,
                    pageCount = it.count,
                    displayName = it.name,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        document = null
        opened = null
        key = null
        _state.value = State.Loading
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

        /** 닫기와 마지막 기록. 화면·VM 보다 오래 산다 — 떠나는 순간에 시작한 일이 끝까지 가야 한다. */
        val closer = CoroutineScope(SupervisorJob() + IroDispatchers.io)
    }
}
