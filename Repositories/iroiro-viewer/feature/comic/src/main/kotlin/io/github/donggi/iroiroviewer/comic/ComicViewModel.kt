package io.github.donggi.iroiroviewer.comic

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.data.ComicProgressEntity
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.ArchivePasswordException
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.io.FileKey
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.SessionPasswords
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.ComicLimits
import io.github.donggi.iroiroviewer.safety.ImageLimits
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 만화 한 권을 읽는 화면의 상태.
 *
 * ## 무엇을 들고 있고 무엇을 안 들고 있는가
 *
 * [ComicSource] 와 [ComicPageStore] 를 들고 있다 — 8단계의 `ArchiveViewModel` 이
 * 리더를 닫아 버린 것과 반대인데, 이유가 있다. 목록 화면은 한 번 읽고 끝이지만 만화는
 * **쪽을 넘길 때마다 다시 읽는다.** 7z 에서 그때마다 새로 여는 것은 실측으로 제곱
 * 비용이었다(8단계 실측표).
 *
 * 대신 화면을 떠나면 [close] 로 반드시 놓는다. 그 사이 사용자가 파일을 지우면 다음
 * 읽기가 실패하고, 화면이 '이 쪽을 열 수 없습니다' 로 말한다.
 */
class ComicViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * 어떤 차례로 읽는가. 값은 DB 의 `read_direction` 에 그대로 들어간다.
     *
     * **새 책의 기본은 언제나 [LTR] 이다.** [open] 이 첫머리에서 기본값으로 되돌리고,
     * [restore] 가 저장된 값이 있을 때만 그 위에 얹는다 — 앞 책의 방향이 새 책으로
     * 옮겨 가던 9단계의 결함이 그 순서로 고쳐졌다.
     */
    enum class Direction(val code: Int) {
        /** 왼쪽에서 오른쪽. 서양 만화·일반 이미지 묶음. **새 책의 기본값이다.** */
        LTR(0),

        /** 오른쪽에서 왼쪽. 일본 만화의 기본이다. */
        RTL(1),

        /** 세로 스크롤. 웹툰. */
        VERTICAL(2),
        ;

        companion object {
            fun of(code: Int): Direction = entries.firstOrNull { it.code == code } ?: LTR
        }
    }

    data class Book(
        val path: String,
        val name: String,
        val pageCount: Int,
        val solid: Boolean,
        val store: ComicPageStore,
    )

    sealed interface State {
        data object Loading : State
        data class Ready(val book: Book) : State
        data class Failed(val kind: ComicOpen.Kind) : State
    }

    /** 화면에 한 번만 전하는 것. 상태로 두면 회전할 때마다 다시 뜬다. */
    sealed interface Event {
        /** 저장된 쪽에서 이어 열었다. 화면이 '처음부터' 를 권한다. */
        data class Resumed(val page: Int) : Event
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    private val _direction = MutableStateFlow(Direction.LTR)
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var opened: Opened? = null
    private var source: ComicSource? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var key: String? = null
    private var pageIndex: List<io.github.donggi.iroiroviewer.format.archive.ComicPage> = emptyList()

    /**
     * 엔트리 번호로 그 쪽을 연다. 그 엔트리가 쪽이 아니면 아무것도 하지 않는다 —
     * 압축 목록에서 그림이 아닌 항목을 탭한 경우이고, 그때는 화면이 '열 수 없습니다' 로
     * 끝내는 것이 맞다.
     */
    private fun jumpToEntry(entryIndex: Int) {
        val ordinal = pageIndex.firstOrNull { it.entryIndex == entryIndex }?.ordinal ?: return
        _page.value = ordinal
    }

    /**
     * **경로만으로 캐시하지 않는다.** 같은 경로에 다른 파일이 들어올 수 있고(덮어쓰기·
     * 복원), 그때 옛 목록을 그대로 쓰면 다른 책의 쪽이 열린다. 8단계가 같은 형태를
     * 아카이브 화면에서 한 번 고쳤다.
     */
    private data class Opened(val path: String, val size: Long, val modified: Long)

    /** 뷰포트가 정해지기 전에는 예산을 못 만든다. 화면이 알려 준다. */
    private var budget: ImageLimits.Budget? = null

    /**
     * @param startEntryIndex 아카이브 안의 **엔트리 번호**로 시작 쪽을 지정한다. -1 이면
     *   이어보기가 정한다. 압축 목록에서 그림 항목을 탭해 들어올 때 쓴다 — 8단계가
     *   '아카이브 안의 파일 미리보기' 로 미뤄 둔 것이 이 길이다. **자리가 아니라 엔트리
     *   번호를 받는다**: 목록은 찌꺼기와 못 읽는 항목을 걸러 내므로 두 수가 다르다.
     */
    fun open(path: String, startEntryIndex: Int = -1) {
        val file = File(path)
        val want = Opened(path, file.length(), file.lastModified())
        if (opened == want && _state.value is State.Ready) {
            if (startEntryIndex >= 0) jumpToEntry(startEntryIndex)
            return
        }
        lastStartEntry = startEntryIndex
        _asking.value = null

        loadJob?.cancel()
        closeSource()
        opened = want
        _state.value = State.Loading
        // **새 책은 기본값에서 시작한다.** ViewModel 은 액티비티에 묶여 있어 책을 바꿔도
        // 같은 객체가 남는다. 되돌리지 않으면 **앞 책의 읽던 쪽과 읽는 방향이 새 책에
        // 그대로 옮겨 간다** — 실제로 세로 스크롤로 보던 웹툰을 닫고 연 만화가 세로
        // 모드로 열려서 좌우로 밀리지 않았다(기기에서 잡았다).
        _page.value = 0
        _direction.value = Direction.LTR
        key = null
        pageIndex = emptyList()

        loadJob = viewModelScope.launch {
            val budget = budget ?: defaultBudget()
            // **아직 아무도 소유하지 않은 리더.** 여기까지 오는 도중에 취소되면
            // `finally` 가 닫는다 — 자세한 것은 `ComicOpen.openArchive` 의 주석.
            var orphan: ComicSource? = null
            // 이번 세션에 이 파일의 암호를 넣었으면 그것으로 연다(사본 — 끝나면 지운다).
            // 압축 목록에서 암호를 넣고 그림 항목을 누른 길이 이것이다.
            val password = if (file.isFile) SessionPasswords.get(SessionPasswords.keyOf(file)) else null
            try {
                val result = ComicOpen.open(path, ComicLimits.windowBytes(budget), password = password) { orphan = it }
                when (result) {
                    is ComicOpen.Result.Failed -> {
                        if (result.kind == ComicOpen.Kind.NEEDS_PASSWORD) {
                            // 기억해 둔 암호가 있었는데도 묻는다면 그것이 낡았다.
                            if (password != null) SessionPasswords.forget(SessionPasswords.keyOf(file))
                            _asking.value = false
                        }
                        _state.value = State.Failed(result.kind)
                    }
                    is ComicOpen.Result.Ready -> {
                        source = result.source
                        // 소유권이 [source] 로 넘어갔다. 이제 닫는 것은 closeSource 다.
                        orphan = null
                        val store = ComicPageStore(result.source, budget)
                        val book = Book(
                            path = path,
                            name = if (file.isDirectory) file.name else file.nameWithoutExtension,
                            pageCount = store.pageCount,
                            solid = result.source.solid,
                            store = store,
                        )
                        pageIndex = result.source.pages
                        // **읽는 방향은 어느 길로 들어오든 되살린다.** 쪽만 갈린다 —
                        // 엔트리를 지정해 들어왔으면 그 쪽으로 간다.
                        restore(file, book, resumePage = startEntryIndex < 0)
                        if (startEntryIndex >= 0) jumpToEntry(startEntryIndex)
                        // **읽던 쪽을 되살린 뒤에 Ready 를 알린다.**
                        //
                        // 먼저 알리면 페이저가 0쪽으로 서고, `snapshotFlow` 가 그 0을 곧바로
                        // 여기로 되돌려 **방금 되살린 쪽을 덮어쓴다.** 화면은 '5쪽부터 이어서
                        // 봅니다' 를 띄워 놓고 1쪽을 보여 준다 — 기기에서 실제로 그렇게 됐고,
                        // 이기고 지는 것이 DB 읽기와 첫 컴포지션의 경주라 재현이 들쭉날쭉하다.
                        // 순서를 뒤집으면 페이저가 처음부터 옳은 쪽에 서므로 경주 자체가 없다.
                        _state.value = State.Ready(book)
                    }
                }
            } finally {
                // 취소든 예외든, 주인 없는 리더를 남기지 않는다.
                orphan?.let { runCatching { it.close() } }
                password?.fill('\u0000')
            }
        }
    }

    /** 마지막으로 연 길의 시작 엔트리. 암호를 넣고 다시 열 때 같은 쪽으로 간다. */
    private var lastStartEntry = -1

    /** 암호를 묻는 중인가 — null 아님, false 처음, true 방금 넣은 것이 틀렸다. */
    private val _asking = MutableStateFlow<Boolean?>(null)
    val asking: StateFlow<Boolean?> = _asking.asStateFlow()

    fun requestPassword() {
        _asking.value = false
    }

    fun dismissPassword() {
        _asking.value = null
    }

    /**
     * 넣은 암호가 맞는지 확인하고, 맞으면 이번 세션에 기억한 뒤 다시 연다.
     *
     * 배열의 주인이 여기로 넘어온다. 확인이 끝나면 0 으로 덮는다.
     */
    fun submitPassword(password: CharArray) {
        val path = opened?.path
        if (path == null) {
            password.fill('\u0000')
            return
        }
        val file = File(path)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val ok = withContext(IroDispatchers.parsing) {
                    try {
                        Archives.open(FileDocumentSource(file), password = password).use { Archives.verifyPassword(it) }
                    } catch (e: ArchivePasswordException) {
                        false
                    }
                }
                if (ok) {
                    SessionPasswords.put(SessionPasswords.keyOf(file), password)
                    _asking.value = null
                    opened = null
                    open(path, lastStartEntry)
                } else {
                    _asking.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 확인이 '틀렸다' 가 아닌 이유로 끝났다 — 상한·풀지 않는 방식. 원문은 화면에 보내지 않는다.
                _asking.value = null
                _state.value = State.Failed(
                    when {
                        t is io.github.donggi.iroiroviewer.safety.ParseLimitExceededException ||
                            t.javaClass.simpleName.contains("MemoryLimit") -> ComicOpen.Kind.TOO_LARGE
                        t.javaClass.simpleName.contains("Encrypt") -> ComicOpen.Kind.ENCRYPTED
                        else -> ComicOpen.Kind.CORRUPT
                    },
                )
            } finally {
                password.fill('\u0000')
            }
        }
    }

    /**
     * 이어보기를 되살린다.
     *
     * **쪽 수가 달라졌으면 쪽 번호를 믿지 않는다.** 같은 키(크기+이름+수정시각)인데
     * 쪽 수가 바뀌는 일은 없어야 하지만, 있다면 그것은 우리 쪽 규칙(`ComicPages`)이
     * 바뀐 것이다 — 그때 옛 번호는 다른 그림을 가리킨다.
     *
     * @param resumePage 읽던 쪽까지 되살릴 것인가. 압축 목록에서 그림 항목을 탭해
     *   들어온 길은 **쪽을 이미 지정했으므로** false 다. 그래도 이 함수를 거쳐야 한다 —
     *   [key] 만 채우고 지나가면 **읽는 방향이 기본값(LTR)인 채로 열리고**, 화면을 나갈
     *   때 [close] 가 그 기본값을 `comic_progress.read_direction` 에 덮어쓴다. 일반
     *   압축(.zip·.7z·.rar)으로 묶은 만화는 이 길이 유일한 입구라, 오른쪽에서 왼쪽을
     *   골라도 다음에 열면 매번 왼쪽에서 오른쪽이었다.
     */
    private suspend fun restore(file: File, book: Book, resumePage: Boolean) {
        val k = FileKey.of(file.name, file.length(), file.lastModified())
        key = k
        // **`runCatching` 을 쓰지 않는다.** 그것은 `Throwable` 을 잡으므로 여기서 오는
        // `CancellationException` 까지 삼킨다. 이 아래로는 정지 지점이 하나도 없어서,
        // 삼키면 **취소된 잡이 끝까지 달려** 이미 닫힌 소스를 안은 `Ready` 를 다시
        // 세우고 `Event.Resumed` 를 다음 책에게 배달한다(채널이 버퍼를 들고 있다).
        // 저장소 규칙 그대로다 — 경계 catch 의 첫 줄은 언제나 취소를 되던진다.
        val saved = try {
            IroiroDatabase.get(getApplication()).comicProgress().find(k)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Iro.d(TAG) { "이어보기를 읽지 못했다: ${e.javaClass.simpleName}" }
            null
        }
        if (saved != null) {
            // 저장된 값이 있으면 그것이 이긴다. 없으면 위에서 넣어 둔 기본값 그대로다.
            _direction.value = Direction.of(saved.readDirection)
            if (resumePage && saved.pageCount == book.pageCount && saved.page in 1 until book.pageCount) {
                _page.value = saved.page
                events.trySend(Event.Resumed(saved.page))
            }
        }
    }

    fun onPageChanged(ordinal: Int) {
        if (ordinal == _page.value) return
        _page.value = ordinal
        scheduleSave()
    }

    fun setDirection(value: Direction) {
        if (value == _direction.value) return
        _direction.value = value
        scheduleSave()
    }

    /** '처음부터' 를 눌렀을 때. */
    fun restart() {
        _page.value = 0
        scheduleSave()
    }

    /**
     * 화면이 뷰포트와 힙 등급을 알려 준다.
     *
     * **예산을 화면 크기로 만든다.** 태블릿을 가로로 돌리면 한 장의 바이트가 두 배쯤
     * 되므로, 열 때 한 번 정하고 끝내면 회전 뒤에 예산이 실제와 어긋난다.
     */
    fun onViewport(width: Int, height: Int) {
        val next = ImageLimits.budgetOf(width, height, memoryClassMb())
        if (next == budget) return
        val first = budget == null
        budget = next
        if (!first) {
            // 옛 크기로 뜬 비트맵은 쓸모가 없다. 다시 열면 새 예산으로 만들어진다.
            (_state.value as? State.Ready)?.book?.store?.clearBitmaps()
        }
        Iro.d(TAG) { "예산 base=${next.baseBytes} live=${next.liveCap} pages=${next.livePages}" }
    }

    /**
     * 쪽을 넘길 때마다 DB 를 쓰지 않는다.
     *
     * 빠르게 넘기면 초에 열 번도 쓰게 되고, 그것은 읽는 내내 디스크를 두드리는 일이다.
     * 마지막 값만 남기면 되므로 짧게 모아 쓴다.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            save(snapshot())
        }
    }

    /**
     * 지금 값을 **그 자리에서** 찍어 둔다.
     *
     * [close] 가 쓰기를 코루틴에 맡기고 곧바로 상태를 되돌리기 때문에, 쓰는 쪽이 나중에
     * 읽으면 이미 지워진 값을 본다. 그러면 '마지막 쪽을 놓치지 않고 쓴다' 는 약속이
     * 코루틴 디스패처의 세부(같은 스레드면 즉시 도는가)에 달리게 된다.
     */
    private fun snapshot(): ComicProgressEntity? {
        val k = key ?: return null
        val book = (_state.value as? State.Ready)?.book ?: return null
        return ComicProgressEntity(
            fileKey = k,
            page = _page.value,
            pageCount = book.pageCount,
            readDirection = _direction.value.code,
            displayName = book.name,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun save(entity: ComicProgressEntity?) {
        if (entity == null) return
        // 읽는 쪽([restore])과 같은 이유로 취소를 삼키지 않는다. 여기서는 뒤따르는 일이
        // 없어 당장 해가 없지만, 규칙을 한 곳만 지키면 다음 사람이 어느 쪽이 맞는지 모른다.
        try {
            IroiroDatabase.get(getApplication()).comicProgress().upsert(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Iro.d(TAG) { "이어보기를 쓰지 못했다: ${e.javaClass.simpleName}" }
        }
    }

    /** 화면을 떠난다. **마지막 쪽을 놓치지 않고 쓴다.** */
    fun close() {
        saveJob?.cancel()
        // **값을 먼저 찍고 나서** 상태를 되돌린다. 아래에서 key·page 를 지우므로,
        // 코루틴이 나중에 읽으면 아무것도 쓰지 않게 된다.
        val pending = snapshot()
        // viewModelScope 는 이 뒤에도 살아 있다(화면이 사라져도 VM 은 남는다). 그래도
        // onCleared 까지 미루면 다음 책을 여는 사이에 취소될 수 있어 여기서 쓴다.
        viewModelScope.launch { save(pending) }
        loadJob?.cancel()
        closeSource()
        opened = null
        key = null
        _page.value = 0
        _state.value = State.Loading
        _asking.value = null
    }

    override fun onCleared() {
        closeSource()
    }

    private fun closeSource() {
        try {
            source?.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Iro.d(TAG) { "리더를 닫는 중 ${e.javaClass.simpleName}" }
        }
        source = null
    }

    private fun memoryClassMb(): Int {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE)
            as? ActivityManager ?: return FALLBACK_HEAP_MB
        // **largeMemoryClass 를 쓰지 않는다.** 매니페스트에 `largeHeap` 을 선언하지
        // 않았으므로 실제로 받는 것은 memoryClass 다. 큰 쪽을 쓰면 예산이 현실보다
        // 커지고, 그 예산을 믿은 코드가 OOM 으로 죽는다.
        return am.memoryClass.coerceAtLeast(MIN_HEAP_MB)
    }

    private fun defaultBudget(): ImageLimits.Budget {
        val m = getApplication<Application>().resources.displayMetrics
        return ImageLimits.budgetOf(m.widthPixels, m.heightPixels, memoryClassMb())
            .also { budget = it }
    }

    private companion object {
        const val TAG = "Comic"
        const val SAVE_DELAY_MS = 700L
        const val FALLBACK_HEAP_MB = 64
        const val MIN_HEAP_MB = 16
    }
}
