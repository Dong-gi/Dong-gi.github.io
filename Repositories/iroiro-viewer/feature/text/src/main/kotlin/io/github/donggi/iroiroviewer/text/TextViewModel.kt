package io.github.donggi.iroiroviewer.text

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.charset.BinarySniffer
import io.github.donggi.iroiroviewer.charset.Bom
import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.format.text.MarkdownPreview
import io.github.donggi.iroiroviewer.format.text.RowHighlighter
import io.github.donggi.iroiroviewer.format.text.RowIndex
import io.github.donggi.iroiroviewer.format.text.RowReader
import io.github.donggi.iroiroviewer.format.text.TextHighlight
import io.github.donggi.iroiroviewer.format.text.TextIndexer
import io.github.donggi.iroiroviewer.format.text.TextLanguage
import io.github.donggi.iroiroviewer.format.text.TextRow
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 텍스트 뷰어의 상태.
 *
 * ## 왜 ViewModel 인가
 *
 * 색인은 파일 전체를 한 번 훑는 일이다. 화면 회전마다 다시 훑으면 200 MB 로그에서
 * 회전이 몇 초짜리 작업이 된다. 색인과 판정 결과는 **화면보다 오래 살아야 한다.**
 *
 * ## 창만 들고 있는다
 *
 * 행 목록을 통째로 들면 파일 크기가 그대로 힙이 된다. 화면에 보이는 앞뒤로
 * [WINDOW_ROWS] 행만 들고, 목록이 창 가장자리에 닿으면 다시 읽는다. 되읽기는 앵커가
 * 64 KiB 간격이라 한 번에 그만큼이다.
 *
 * ## 설정은 저장소에서 온다(14단계)
 *
 * 줄 접기·줄 번호·글자 크기·줄 끝 표시는 [ViewerSettings] 가 든다 — 바꾸면 곧바로 저장되고 다음 파일도 그 모양으로
 * 열린다. 저장소는 생성자로 받는다([TextViewerStore]) — 화면은 `AppPreferencesTextStore` 를, 시험은 가짜를 꽂는다.
 */
class TextViewModel(store: TextViewerStore) : ViewModel() {

    /** 다 읽어 낸 파일 하나. 화면이 이것만 보면 된다. */
    data class Doc(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val encoding: TextEncoding,
        val detection: CharsetDetector.Detection,
        val bomLength: Int,
        val index: RowIndex,
        val language: RowHighlighter,
        /** null 이면 강조가 꺼져 있다. [highlightOff] 가 이유를 말한다. */
        val highlight: TextHighlight?,
    ) {
        /** 마크다운 파일인가. 미리보기 단추를 이 파일에만 띄운다. */
        val markdown: Boolean get() = MarkdownPreview.isMarkdownName(name)

        /** 언어는 정했는데 강조를 못 켠 상태인가(파일이 상한보다 크다). */
        val highlightOff: Boolean
            get() = highlight == null && language !== io.github.donggi.iroiroviewer.format.text.PlainHighlighter
    }

    sealed interface State {
        /**
         * 여는 중. [reached] 는 **이번 열기에서 파일에 닿았다**(앞머리를 실제로 읽었다)는 표시다. 닿기 전에는 없거나
         * 못 읽는 파일일 수 있어 ⋮ 의 '다른 앱으로 열기' 를 흐리게 둔다([TextOpenWith.inMenu]).
         */
        data class Loading(val reached: Boolean = false) : State

        /** 색인 중. [rows] 는 지금까지 센 행, [scanned] 는 읽은 바이트. */
        data class Indexing(val rows: Int, val scanned: Long, val total: Long) : State

        data class Ready(val doc: Doc) : State

        data class Failed(val kind: Kind) : State {
            enum class Kind {
                /** 파일이 없거나 읽을 수 없다. */
                UNREADABLE,

                /** 글이 아니다. 열면 화면이 깨진 글자로 가득 찬다. */
                BINARY,

                /** BOM 은 알아봤는데 우리가 읽지 못하는 인코딩이다(UTF-32). */
                UNSUPPORTED_ENCODING,
            }
        }
    }

    /** 화면에 보이는 구간. [start] 행부터 [rows] 개다. */
    data class Window(val start: Int, val rows: List<TextRow>) {
        fun rowAt(row: Int): TextRow? = rows.getOrNull(row - start)
        fun covers(from: Int, to: Int): Boolean = from >= start && to <= start + rows.size
    }

    data class SearchState(
        val query: String = "",
        val ignoreCase: Boolean = true,
        val hits: List<Int> = emptyList(),
        val cursor: Int = -1,
        val running: Boolean = false,
        /** 훑은 행과 전체 행. 진행률은 [percent]. */
        val scannedRows: Int = 0,
        val totalRows: Int = 0,
        /**
         * 이 찾기의 번호. 진행 알림은 **찾기를 돌리는 스레드에서** 오므로, 취소하거나 새로 찾은 뒤에 늦게 도착한
         * 알림이 새 상태를 고치지 않게 번호로 가린다.
         */
        val generation: Int = 0,
    ) {
        val currentRow: Int? get() = hits.getOrNull(cursor)
        val percent: Int get() = searchPercent(scannedRows, totalRows)

        /** 진행 알림을 얹는다. **다른 찾기의 것이거나 이미 끝났으면 그대로** — 알림은 늦게 올 수 있다. */
        fun withProgress(gen: Int, scanned: Int): SearchState =
            if (generation == gen && running) copy(scannedRows = scanned) else this

        /** 결과를 얹는다. 그 사이에 닫았거나 새로 찾았으면(번호가 다르면) 버린다. */
        fun withResult(gen: Int, found: List<Int>): SearchState =
            if (generation != gen) {
                this
            } else {
                copy(hits = found, cursor = if (found.isEmpty()) -1 else 0, running = false, scannedRows = totalRows)
            }

        /**
         * 찾을 말을 비우고 찾았다 — 결과와 진행을 비우고 **번호를 새로 받는다.** 번호를 그대로 두면 돌고 있던 찾기의 결과가
         * 나중에 도착해 빈 찾기 칸 아래에 '1 / 3' 을 띄우고 목록을 그 자리로 옮긴다.
         */
        fun withoutQuery(gen: Int): SearchState =
            copy(hits = emptyList(), cursor = -1, running = false, scannedRows = 0, totalRows = 0, generation = gen)
    }

    /**
     * 마크다운 미리보기(14단계). [Off] 가 아니면 화면은 본문 대신 이것을 그린다.
     */
    sealed interface Preview {
        data object Off : Preview

        data object Building : Preview

        /**
         * @param pagePath 쪽의 경로. 다시 만들 때마다 바뀐다 — 같은 주소면 WebView 가 다시 읽지 않는다.
         * @param html 껍데기까지 씌운 문서(UTF-8). WebView 의 스레드에서 매번 인코딩하지 않게 미리 바꿔 둔다.
         * @param images `img/N` 이 가리키는 상대 경로. [baseDir] 기준이다.
         */
        class Ready(
            val pagePath: String,
            val html: ByteArray,
            val images: List<String>,
            val baseDir: File,
            val truncated: Boolean,
        ) : Preview

        /** 원문이 상한보다 크다. 화면이 상한을 말한다. */
        data class TooLarge(val limitBytes: Long) : Preview

        data object Failed : Preview
    }

    private val _state = MutableStateFlow<State>(State.Loading())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _window = MutableStateFlow(Window(0, emptyList()))
    val window: StateFlow<Window> = _window.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    /**
     * 화면이 이 행으로 옮겨 가야 한다는 요청. 화면이 처리하면 [consumeScrollTo] 로 지운다.
     *
     * **한 번만 쓰는 사건이라 상태가 아니다.** 상태로 두면 회전할 때마다 다시 튄다.
     */
    private val _scrollTo = MutableStateFlow<Int?>(null)
    val scrollTo: StateFlow<Int?> = _scrollTo.asStateFlow()

    fun consumeScrollTo() { _scrollTo.value = null }

    /** 인코딩을 바꿔 보려는 사용자를 위한 미리보기. 인코딩마다 앞부분 몇 줄. */
    private val _previews = MutableStateFlow<Map<TextEncoding, String>>(emptyMap())
    val previews: StateFlow<Map<TextEncoding, String>> = _previews.asStateFlow()

    private val viewerSettings = ViewerSettings(viewModelScope, store)

    /** 뷰어 설정. 읽어 오는 동안(파일을 막 열었을 때)은 null 이다. */
    val settings: StateFlow<TextViewerDefaults?> = viewerSettings.value

    private val _preview = MutableStateFlow<Preview>(Preview.Off)
    val preview: StateFlow<Preview> = _preview.asStateFlow()

    private var openedPath: String? = null
    private var head: ByteArray = ByteArray(0)
    private var headLength = 0
    private var indexJob: Job? = null
    private var windowJob: Job? = null
    private var searchJob: Job? = null
    private var previewJob: Job? = null
    private var searchGeneration = 0
    private var previewGeneration = 0

    // ---- 열기 ------------------------------------------------------------------

    /**
     * @param entry 화면의 입장 표([ViewerSettings.enter]). 회전하면 같은 값이 오고, 화면을 떠났다 돌아오면 새 값이 온다.
     */
    fun open(path: String, entry: Long) {
        // 새로 들어왔으면 설정을 저장된 값으로 다시 읽고 미리보기를 끈다 — 같은 파일을 다시 열어도 원문부터 보인다.
        if (viewerSettings.enter(entry)) closePreview()
        if (openedPath == path) return
        openedPath = path
        closePreview()
        indexJob?.cancel()
        indexJob = viewModelScope.launch { load(path, forced = null) }
    }

    /** 설정을 바꾼다. 저장소에도 쓴다 — 다음 파일이 이 모양으로 열린다. */
    fun updateSettings(transform: (TextViewerDefaults) -> TextViewerDefaults) {
        // 미리보기의 글자 크기도 이 설정을 따르지만 다시 만들지 않는다 — 화면이 `textZoom` 으로 그 자리에서 건다
        // ([MarkdownPane]). 다시 만들면 WebView 가 쪽을 새로 읽어 읽던 자리가 맨 위로 돌아간다.
        viewerSettings.update(transform)
    }

    /** 사용자가 인코딩을 손으로 골랐다. **색인을 다시 만든다** — 줄이 다르게 갈릴 수 있다. */
    fun chooseEncoding(encoding: TextEncoding) {
        val path = openedPath ?: return
        indexJob?.cancel()
        indexJob = viewModelScope.launch { load(path, forced = encoding) }
    }

    /** 사용자가 언어를 손으로 골랐다. 색인은 그대로고 강조만 다시 만든다. */
    fun chooseLanguage(language: RowHighlighter) {
        val cur = (_state.value as? State.Ready)?.doc ?: return
        viewModelScope.launch {
            val file = File(cur.path)
            val hl = withContext(IroDispatchers.parsing) {
                TextHighlight.prepare(opener(file), cur.encoding, cur.index, language)
            }
            _state.value = State.Ready(cur.copy(language = language, highlight = hl))
            reloadWindow(force = true)
        }
    }

    private suspend fun load(path: String, forced: TextEncoding?) {
        _state.value = State.Loading()
        _window.value = Window(0, emptyList())
        searchJob?.cancel()
        searchGeneration++
        _search.value = SearchState()
        // 인코딩을 바꿔 다시 읽는 동안 앞 판정으로 만든 미리보기를 띄우지 않는다. 다 읽으면 다시 만든다.
        if (_preview.value !is Preview.Off) {
            previewJob?.cancel()
            _preview.value = Preview.Building
        }

        val file = File(path)
        val size = runCatching { file.length() }.getOrDefault(0L)
        if (!file.isFile) {
            _state.value = State.Failed(State.Failed.Kind.UNREADABLE)
            return
        }

        try {
            // **앞머리는 언제나 이 파일에서 새로 읽는다.** 캐시해 두고 인코딩을 바꿀 때만
            // 다시 읽는 방식을 쓰면, 앞 파일의 앞머리로 BOM 을 판정해 새 파일의 첫 세
            // 바이트를 건너뛰는 경로가 생긴다. 한 번 읽는 값이 그 위험보다 싸다.
            val want = minOf(size, CharsetDetector.DETECT_SCAN_MAX.toLong()).toInt()
            head = ByteArray(maxOf(want, 1))
            headLength = withContext(IroDispatchers.io) {
                FileInputStream(file).use { readFully(it, head, want) }
            }
            // 앞머리를 읽었다 — 파일에 닿았다. 여기부터 ⋮ 의 '다른 앱으로 열기' 를 누를 수 있다([State.Loading.reached]).
            // 판정·색인이 끝나기를 기다리지 않는다(200 MB 로그의 색인은 몇 초다).
            _state.value = State.Loading(reached = true)

            // **미리보기를 실패보다 먼저 만든다.** 바이너리로 판정해 되돌아가는 길에서도
            // 사용자는 '인코딩을 골라 열어 보기' 를 누를 수 있고, 그때 앞 파일의
            // 미리보기가 떠 있으면 엉뚱한 글을 보고 인코딩을 고르게 된다.
            _previews.value = buildPreviews(head, headLength)

            // **바이너리 판정이 먼저다.** 인코딩 판정은 어떤 바이트열에도 답을 내놓으므로,
            // 그림 파일을 먼저 거르지 않으면 깨진 글자로 가득한 화면이 뜬다.
            if (forced == null) {
                val verdict = BinarySniffer.sniff(head, minOf(headLength, BinarySniffer.SNIFF_BYTES))
                if (verdict is BinarySniffer.Verdict.Binary) {
                    _state.value = State.Failed(State.Failed.Kind.BINARY)
                    return
                }
            }

            val detection = CharsetDetector.detect(head, headLength)
            if (forced == null && detection.evidence is CharsetDetector.Evidence.Unsupported) {
                _state.value = State.Failed(State.Failed.Kind.UNSUPPORTED_ENCODING)
                return
            }
            val encoding = forced ?: detection.encoding
            if (encoding.charset == null) {
                _state.value = State.Failed(State.Failed.Kind.UNSUPPORTED_ENCODING)
                return
            }

            // BOM 은 **바이트 단위로 건너뛴다.** 자바가 UTF-8 BOM 을 먹지 않아서,
            // 그냥 읽으면 첫 줄 앞에 보이지 않는 글자가 하나 붙는다.
            val bom = Bom.detect(head, headLength)
            val bomLength = if (bom?.encoding == encoding) bom.length else 0

            val opener = opener(file)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val index = withContext(IroDispatchers.parsing) {
                TextIndexer.index(opener, size, encoding, bomLength) { scanned, rows ->
                    _state.value = State.Indexing(rows, scanned, size)
                }
            }

            val indexedAt = android.os.SystemClock.elapsedRealtime()
            val language = TextLanguage.forFileName(file.name)
            val highlight = withContext(IroDispatchers.parsing) {
                TextHighlight.prepare(opener, encoding, index, language)
            }
            val highlightedAt = android.os.SystemClock.elapsedRealtime()

            _state.value = State.Ready(
                Doc(
                    path = path,
                    name = file.name,
                    sizeBytes = size,
                    encoding = encoding,
                    detection = detection,
                    bomLength = bomLength,
                    index = index,
                    language = language,
                    highlight = highlight,
                ),
            )
            // 실측용 기록. 릴리스에서는 호출 지점째 사라진다.
            Iro.d {
                "텍스트 ${file.name}: ${encoding.label} · ${index.rowCount}행 · ${index.lineCount}줄 · " +
                    "앵커 ${index.anchorCount}(${index.memoryBytes}B) · ${size}B · " +
                    "색인 ${indexedAt - startedAt}ms · 강조 ${highlightedAt - indexedAt}ms" +
                    (if (highlight == null) " (꺼짐)" else "")
            }
            reloadWindow(force = true)
            if (_preview.value !is Preview.Off) buildPreview()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Iro.d { "텍스트 열기 실패: ${e.javaClass.simpleName}" }
            _state.value = State.Failed(State.Failed.Kind.UNREADABLE)
        }
    }

    // ---- 미리보기 -----------------------------------------------------------------

    fun togglePreview() {
        if (_preview.value is Preview.Off) buildPreview() else closePreview()
    }

    private fun closePreview() {
        previewJob?.cancel()
        previewJob = null
        _preview.value = Preview.Off
    }

    /**
     * 원문을 읽어 HTML 로 바꾼다. **원문 전체를 한 번 더 읽는다** — 창만 들고 있는 뷰어와 달리 마크다운은 문서
     * 전체를 봐야 뜻이 정해진다(참조 정의는 뒤에 올 수 있다). 그래서 크기 상한([MarkdownPreview.MAX_SOURCE_BYTES])
     * 이 있다.
     */
    private fun buildPreview() {
        val doc = (_state.value as? State.Ready)?.doc ?: return
        if (!doc.markdown) return
        previewJob?.cancel()
        if (doc.sizeBytes > MarkdownPreview.MAX_SOURCE_BYTES) {
            _preview.value = Preview.TooLarge(MarkdownPreview.MAX_SOURCE_BYTES)
            return
        }
        _preview.value = Preview.Building
        previewJob = viewModelScope.launch {
            try {
                // 글자 크기는 쪽에 적지 않는다(기본 100%) — 화면이 `textZoom` 으로 곱한다. 그래서 설정을 읽는 중에
                // 만들어도 크기가 굳지 않는다.
                val file = File(doc.path)
                val source = withContext(IroDispatchers.io) {
                    MarkdownPreview.readSource(opener(file), doc.encoding, doc.bomLength)
                }
                val (result, bytes) = withContext(IroDispatchers.parsing) {
                    val job = coroutineContext.job
                    val r = MarkdownPreview.render(source) { job.ensureActive() }
                    r to r.html.toByteArray(Charsets.UTF_8)
                }
                previewGeneration++
                _preview.value = Preview.Ready(
                    pagePath = MarkdownPreview.pagePath(previewGeneration),
                    html = bytes,
                    images = result.images,
                    baseDir = file.absoluteFile.parentFile ?: file.absoluteFile,
                    truncated = result.truncated,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 예외 문구는 화면에 내보내지 않는다. 종류만 남긴다.
                Iro.d { "마크다운 미리보기 실패: ${e.javaClass.simpleName}" }
                _preview.value = Preview.Failed
            } catch (e: OutOfMemoryError) {
                // 결과는 상한(글자 400만)으로 묶었지만 만드는 동안 몇 벌이 겹친다. 미리보기 하나 때문에 앱을 내리지
                // 않는다 — 원문 보기는 창만 들고 있어 그대로 돈다(이미지 디코딩이 같은 판단을 한다).
                Iro.d { "마크다운 미리보기 메모리 부족" }
                _preview.value = Preview.Failed
            }
        }
    }

    // ---- 창 ---------------------------------------------------------------------

    /** 화면이 [first] 행부터 [visible] 행을 보고 있다고 알린다. */
    fun onVisible(first: Int, visible: Int) {
        val doc = (_state.value as? State.Ready)?.doc ?: return
        val w = _window.value
        val need = first - EDGE_ROWS to first + visible + EDGE_ROWS
        if (w.rows.isNotEmpty() && w.covers(need.first.coerceAtLeast(0), minOf(need.second, doc.index.rowCount))) {
            return
        }
        loadWindow(doc, (first - WINDOW_ROWS / 3).coerceAtLeast(0))
    }

    private fun reloadWindow(force: Boolean) {
        val doc = (_state.value as? State.Ready)?.doc ?: return
        loadWindow(doc, if (force) _window.value.start else 0)
    }

    private fun loadWindow(doc: Doc, start: Int) {
        windowJob?.cancel()
        windowJob = viewModelScope.launch {
            val rows = withContext(IroDispatchers.parsing) {
                RowReader.read(
                    opener(File(doc.path)),
                    doc.encoding,
                    doc.index,
                    start,
                    WINDOW_ROWS,
                    doc.highlight,
                )
            }
            _window.value = Window(start, rows)
        }
    }

    // ---- 찾기 --------------------------------------------------------------------

    fun setQuery(query: String) {
        _search.value = _search.value.copy(query = query)
    }

    fun toggleIgnoreCase() {
        _search.value = _search.value.copy(ignoreCase = !_search.value.ignoreCase)
        if (_search.value.query.isNotEmpty()) runSearch()
    }

    fun runSearch() {
        val doc = (_state.value as? State.Ready)?.doc ?: return
        val s = _search.value
        if (s.query.isEmpty()) {
            // 돌고 있던 찾기를 멈춘다. 멈추지 않으면 그 결과가 나중에 빈 찾기 위에 얹힌다.
            searchJob?.cancel()
            _search.value = s.withoutQuery(++searchGeneration)
            return
        }
        searchJob?.cancel()
        val gen = ++searchGeneration
        val total = doc.index.rowCount
        _search.value = s.copy(
            running = true,
            hits = emptyList(),
            cursor = -1,
            scannedRows = 0,
            totalRows = total,
            generation = gen,
        )
        searchJob = viewModelScope.launch {
            val hits = withContext(IroDispatchers.parsing) {
                var lastPercent = -1
                RowReader.search(
                    opener(File(doc.path)),
                    doc.encoding,
                    doc.index,
                    s.query,
                    s.ignoreCase,
                ) { scanned ->
                    // 퍼센트가 바뀔 때만 알린다. **`update` 로 고친다** — 이 콜백은 찾기 스레드에서 돌고, 그 사이에
                    // 사용자가 찾기를 닫거나 새로 찾을 수 있다. 번호가 다르면 늦게 온 알림이다.
                    val p = searchPercent(scanned, total)
                    if (p != lastPercent) {
                        lastPercent = p
                        _search.update { it.withProgress(gen, scanned) }
                    }
                }
            }
            _search.update { it.withResult(gen, hits) }
            if (_search.value.generation == gen) hits.firstOrNull()?.let { _scrollTo.value = it }
        }
    }

    fun stepHit(delta: Int) {
        val s = _search.value
        if (s.hits.isEmpty()) return
        val next = (s.cursor + delta).let { (it % s.hits.size + s.hits.size) % s.hits.size }
        _search.value = s.copy(cursor = next)
        _scrollTo.value = s.hits[next]
    }

    fun clearSearch() {
        searchJob?.cancel()
        // 번호를 올려 두어 취소 직후에 도착하는 알림이 빈 상태를 고치지 않게 한다.
        searchGeneration++
        _search.value = SearchState()
    }

    /** 사람이 세는 줄 번호(1부터)로 간다. 없는 줄이면 아무 일도 안 한다. */
    fun goToLine(humanLine: Int) {
        val doc = (_state.value as? State.Ready)?.doc ?: return
        viewModelScope.launch {
            val row = withContext(IroDispatchers.parsing) {
                RowReader.rowOfLine(opener(File(doc.path)), doc.encoding, doc.index, humanLine - 1)
            }
            if (row >= 0) _scrollTo.value = row
        }
    }

    // ---- 도우미 -------------------------------------------------------------------

    /**
     * 그 오프셋에서 새 스트림을 연다.
     *
     * **`skip` 을 쓰지 않는다.** `skip` 은 요청한 만큼 건너뛰었다고 약속하지 않아서,
     * 짧게 건너뛰면 색인의 오프셋과 어긋난 자리에서 읽기 시작한다. CP949 같은 비자기동기
     * 인코딩에서는 그것이 **그럴듯한 다른 글자**로 나온다 — 아무 오류 없이.
     */
    private fun opener(file: File): (Long) -> InputStream = { offset ->
        val fis = FileInputStream(file)
        if (offset > 0) fis.channel.position(offset)
        fis
    }

    /**
     * [want] 바이트를 채울 때까지 읽는다. 실제로 읽은 수를 돌려준다.
     *
     * **`InputStream.readNBytes` 를 쓰지 마라.** 자바 9 의 함수지만 안드로이드에는
     * **API 33 부터** 있고, 이 앱의 minSdk 는 31 이다. 컴파일은 통과하고 Android 12
     * 에서 `NoSuchMethodError` 로 앱이 죽는다 — 실제로 한 번 죽였다.
     *
     * 한 번의 `read` 가 요청한 만큼 준다고 가정해서도 안 된다. FUSE 로 올라온
     * `/sdcard` 는 한 번에 주는 양이 요청보다 작을 수 있다.
     */
    private fun readFully(stream: InputStream, buffer: ByteArray, want: Int): Int {
        var read = 0
        while (read < want) {
            val n = stream.read(buffer, read, want - read)
            if (n <= 0) break
            read += n
        }
        return read
    }

    /** 인코딩마다 앞부분을 디코드해 보여 준다. 사용자가 눈으로 고를 수 있게. */
    private fun buildPreviews(head: ByteArray, length: Int): Map<TextEncoding, String> {
        val n = minOf(length, PREVIEW_BYTES)
        if (n <= 0) return emptyMap()
        return TextEncoding.userChoices.mapNotNull { enc ->
            val cs = enc.charset ?: return@mapNotNull null
            val text = runCatching { String(head, 0, n, cs) }.getOrNull() ?: return@mapNotNull null
            enc to text.lineSequence().take(2).joinToString(" ⏎ ").take(PREVIEW_CHARS)
        }.toMap()
    }

    companion object {
        /** 한 번에 들고 있는 행. 1080×2400 화면에 40행쯤 보이므로 위아래로 넉넉하다. */
        const val WINDOW_ROWS = 400

        /** 창 가장자리에서 이만큼 안쪽에 오면 다시 읽는다. */
        private const val EDGE_ROWS = 40

        private const val PREVIEW_BYTES = 256
        private const val PREVIEW_CHARS = 120

        /** 강조를 켤 수 있는 파일 크기. 화면이 이 값을 사람에게 보여 준다. */
        val highlightLimitBytes: Long get() = TextLimits.HIGHLIGHT_MAX_BYTES
    }
}
