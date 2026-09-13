package io.github.donggi.iroiroviewer.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchiveTree
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 아카이브 한 개를 읽어 화면에 낼 것을 만든다.
 *
 * ## 리더를 들고 있지 않는다
 *
 * 목록을 만든 뒤 [Archives] 리더를 **닫는다.** 화면이 살아 있는 내내 파일 핸들을 쥐고
 * 있으면, 사용자가 그 사이에 아카이브를 옮기거나 지웠을 때 무슨 일이 일어나는지가
 * 포맷마다 다르다. 미리보기와 풀기는 그때그때 다시 연다 — 여는 값은 목록을 만드는
 * 값에 비하면 작다(7z 도 헤더만 푼다).
 */
class ArchiveViewModel : ViewModel() {

    data class Doc(
        val path: String,
        val name: String,
        val fileBytes: Long,
        val formatId: FormatId,
        val tree: ArchiveTree,
        val entries: List<ArchiveEntry>,
        /** solid 압축인가. 참이면 화면이 무작위 접근을 아예 시도하지 않는다. */
        val solid: Boolean,
    ) {
        /** 아카이브 전체의 압축률. 엔트리별로는 7z 에 값이 없어 적지 않는다. */
        val ratio: Int
            get() {
                val declared = entries.sumOf { it.declaredSize.coerceAtLeast(0L) }
                return if (fileBytes > 0 && declared > 0) (declared / fileBytes).toInt() else 0
            }
    }

    sealed interface State {
        data object Loading : State
        data class Ready(val doc: Doc) : State
        data class Failed(val kind: Kind) : State {
            enum class Kind {
                /** 파일이 없거나 읽을 수 없다. */
                UNREADABLE,

                /** 아카이브가 아니거나 깨졌다. */
                CORRUPT,

                /** 우리가 다루지 않는 갈래(분할 아카이브 등). */
                UNSUPPORTED,

                /** 방어 상한을 넘었다. */
                TOO_LARGE,

                /** 아카이브 전체에 암호가 걸려 있다. */
                ENCRYPTED,
            }
        }
    }

    /** 풀기 전에 물어야 하는 것. 화면이 이것을 보고 대화상자를 띄운다. */
    data class ExtractPlan(
        /** 풀 엔트리 번호. null 이면 전부. */
        val indices: List<Int>?,
        val fileCount: Int,
        val declaredBytes: Long,
        /** 아카이브가 있는 폴더. '여기에 풀기' 의 목적지다. */
        val destParent: String,
        /** 새 폴더 이름 후보(아카이브 이름에서 확장자를 뗀 것). */
        val folderName: String,
        /** 최상위 조각 수. 1개가 아니면 같은 폴더에 풀 때 흩어진다. */
        val topLevelCount: Int,
        /**
         * 최상위 조각 가운데 목적지에 **이미 폴더로 있는** 것.
         *
         * 파일은 여기 넣지 않는다 — 파일이 겹치는 것은 '덮어쓰기' 이고 [conflicts] 가
         * 이미 세어 말한다. 같은 사실을 두 번 말하면서 한 번은 틀린 말('그 안에 섞입니다')
         * 을 하게 된다.
         */
        val existingTopLevel: List<String>,
        /** '여기에 풀기' 로 갔을 때 덮어쓸 파일 수. */
        val conflicts: Int,
        /** 덮어쓸 파일 이름 표본. */
        val conflictSamples: List<String>,
        /** 풀 수 없는 항목 수(위험한 이름·링크·암호). */
        val refused: Int,
    ) {
        /**
         * 기본값은 '새 폴더' 인가.
         *
         * **개수만 보지 않는다.** 최상위가 하나여도 그 이름이 목적지에 이미 있으면 풀기가
         * 기존 폴더 안에 섞여 들어가고, 그것은 되돌리기 어렵다.
         */
        val preferNewFolder: Boolean
            get() = topLevelCount != 1 || existingTopLevel.isNotEmpty() || conflicts > 0
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 지금 보고 있는 아카이브 안 폴더 경로. 빈 문자열이 루트. */
    private val _folder = MutableStateFlow("")
    val folder: StateFlow<String> = _folder.asStateFlow()

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _plan = MutableStateFlow<ExtractPlan?>(null)
    val plan: StateFlow<ExtractPlan?> = _plan.asStateFlow()

    /**
     * 이미 읽은 아카이브의 신원. **경로만으로는 모자란다** — 같은 경로의 파일이 그 사이에
     * 바뀔 수 있고(우리가 방금 푼 뒤 덮어썼을 수도 있다), 그러면 낡은 목록을 보여 주면서
     * 엔트리 번호로 **다른 파일의 바이트**를 읽게 된다.
     */
    private data class Opened(val path: String, val size: Long, val modified: Long)

    private var opened: Opened? = null
    private var job: Job? = null

    fun open(path: String) {
        val file = File(path)
        val now = Opened(path, file.length(), file.lastModified())
        if (opened == now) return
        opened = now
        job?.cancel()
        job = viewModelScope.launch { load(path) }
    }

    private suspend fun load(path: String) {
        _state.value = State.Loading
        _folder.value = ""
        _filter.value = ""
        val file = File(path)
        if (!file.isFile) {
            _state.value = State.Failed(State.Failed.Kind.UNREADABLE)
            return
        }
        try {
            val doc = withContext(IroDispatchers.parsing) {
                val budget = EntryBudget(ParseLimits.DEFAULT)
                Archives.open(FileDocumentSource(file), ParseLimits.DEFAULT, budget).use { reader ->
                    Doc(
                        path = path,
                        name = file.name,
                        fileBytes = file.length(),
                        formatId = reader.formatId,
                        tree = ArchiveTree.build(reader.entries),
                        entries = reader.entries,
                        solid = reader.solid,
                    )
                }
            }
            _state.value = State.Ready(doc)
            Iro.d { "아카이브 ${file.name}: ${doc.formatId.label} · ${doc.entries.size}개 · solid=${doc.solid}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ParseLimitExceededException) {
            _state.value = State.Failed(State.Failed.Kind.TOO_LARGE)
        } catch (t: Throwable) {
            Iro.d { "아카이브 열기 실패: ${t::class.java.simpleName}" }
            _state.value = State.Failed(kindOf(t))
        }
    }

    /**
     * 예외를 화면이 읽을 종류로 옮긴다. **원문 메시지를 화면에 보내지 않는다** —
     * 거기에는 절대경로와 아카이브가 심은 문자열이 들어 있다.
     */
    private fun kindOf(t: Throwable): State.Failed.Kind {
        val name = t::class.java.simpleName
        return when {
            name.contains("Password", ignoreCase = true) ||
                name.contains("Encrypt", ignoreCase = true) -> State.Failed.Kind.ENCRYPTED
            t is IllegalStateException -> State.Failed.Kind.UNSUPPORTED
            t is IOException -> State.Failed.Kind.CORRUPT
            else -> State.Failed.Kind.CORRUPT
        }
    }

    // ---- 탐색 --------------------------------------------------------------------

    fun enter(folderPath: String) { _folder.value = folderPath }

    /** 한 겹 위로. 루트였으면 거짓을 준다 — 화면이 그때 뷰어를 닫는다. */
    fun up(): Boolean {
        val cur = _folder.value
        if (cur.isEmpty()) return false
        _folder.value = cur.substringBeforeLast('/', "")
        return true
    }

    fun setFilter(text: String) { _filter.value = text }

    /** 지금 폴더의 자식들. 필터가 있으면 **아카이브 전체**에서 찾는다. */
    fun rows(): List<ArchiveTree.Node> {
        val doc = (_state.value as? State.Ready)?.doc ?: return emptyList()
        val query = _filter.value
        if (query.isNotBlank()) {
            val out = ArrayList<ArchiveTree.Node>(64)
            collectMatching(doc.tree.root, query, out)
            return out
        }
        return doc.tree.folderAt(_folder.value)?.children ?: emptyList()
    }

    private fun collectMatching(node: ArchiveTree.Node, query: String, out: MutableList<ArchiveTree.Node>) {
        for (c in node.children) {
            if (!c.isDirectory && c.name.contains(query, ignoreCase = true)) out += c
            if (c.isDirectory) collectMatching(c, query, out)
        }
    }

    // ---- 풀기 --------------------------------------------------------------------

    /**
     * 풀기 전에 물어야 할 것을 모은다. **엔진과 같은 이름 계산**([ExtractNames])을 쓴다 —
     * 다르면 사용자가 본 목록과 실제로 덮어써지는 목록이 달라진다.
     */
    fun preparePlan(node: ArchiveTree.Node?) {
        val doc = (_state.value as? State.Ready)?.doc ?: return
        viewModelScope.launch {
            val plan = withContext(IroDispatchers.io) { buildPlan(doc, node) }
            _plan.value = plan
        }
    }

    fun dismissPlan() { _plan.value = null }

    private fun buildPlan(doc: Doc, node: ArchiveTree.Node?): ExtractPlan {
        val indices: List<Int>? = node?.let {
            val list = ArrayList<Int>()
            it.collectIndices(list)
            list
        }
        val selected = indices?.toHashSet()
        val chosen = doc.entries.filter { selected == null || it.index in selected }
        val parent = File(doc.path).parentFile ?: File("/")

        var conflicts = 0
        var refused = 0
        val samples = ArrayList<String>(CONFLICT_SAMPLES)
        for (e in chosen) {
            if (e.isDirectory) continue
            if (!e.isReadable) {
                refused++
                continue
            }
            // '여기에 풀기' 기준으로 센다. 새 폴더로 가면 충돌이 있을 수 없다.
            val target = ExtractNames.targetOf(parent, e)
            if (target == null) {
                refused++
                continue
            }
            if (target.exists()) {
                conflicts++
                if (samples.size < CONFLICT_SAMPLES) samples += target.name
            }
        }

        val topLevel = ExtractNames.topLevelNames(chosen)
        return ExtractPlan(
            indices = indices,
            fileCount = chosen.count { it.isReadable },
            declaredBytes = chosen.sumOf { it.declaredSize.coerceAtLeast(0L) },
            destParent = parent.absolutePath,
            folderName = File(doc.path).name.substringBeforeLast('.'),
            topLevelCount = topLevel.size,
            existingTopLevel = topLevel.filter { File(parent, it).isDirectory },
            conflicts = conflicts,
            conflictSamples = samples,
            refused = refused,
        )
    }

    private companion object {
        const val CONFLICT_SAMPLES = 5
    }
}
