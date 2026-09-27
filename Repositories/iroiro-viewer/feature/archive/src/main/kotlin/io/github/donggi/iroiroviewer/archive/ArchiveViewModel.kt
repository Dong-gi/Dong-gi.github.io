package io.github.donggi.iroiroviewer.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchivePasswordException
import io.github.donggi.iroiroviewer.format.archive.ArchiveTree
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.SessionPasswords
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        /**
         * 이번 세션의 암호로 읽었다. 그 암호가 지워지면([SessionPasswords.clears]) 이 목록은
         * 낡은 것이 된다 — '풀렸다' 고 보여 주는데 실제로 풀 암호가 없다.
         */
        val unlocked: Boolean = false,
    ) {
        /**
         * 암호를 넣으면 읽히게 되는 항목이 있다. 화면이 '암호 넣기' 를 띄운다. 암호로도 못 여는
         * 항목([ArchiveEntry.lockedForGood])만 있으면 거짓이다 — 물어도 소용이 없다.
         */
        val locked: Boolean get() = entries.any { it.needsPassword }

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

        /** 헤더까지 잠겨 목록조차 암호 없이 읽을 수 없다(7z·RAR). */
        data object NeedsPassword : State

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

                /** 이 앱이 풀지 않는 방식으로 잠겼다(PKWARE 의 강한 암호화 등). */
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

    init {
        // 앱이 화면에서 사라져 세션 암호가 지워지면, 암호로 풀어 둔 목록을 다시 읽어 **다시
        // 잠근다.** 첫 값은 지금까지의 횟수라 건너뛴다.
        viewModelScope.launch {
            SessionPasswords.clears.drop(1).collect {
                val doc = (_state.value as? State.Ready)?.doc ?: return@collect
                if (!doc.unlocked) return@collect
                _asking.value = null
                // 풀린 목록으로 세운 풀기 계획도 낡았다 — 그대로 '풀기' 를 누르면 계획은 N개를
                // 말하는데 실제로는 암호가 없어 전부 거절된다(검토가 잡았다).
                _plan.value = null
                job?.cancel()
                job = viewModelScope.launch { load(doc.path) }
            }
        }
    }

    /**
     * 암호를 묻는 중인가. null 이면 묻지 않는다, false 면 처음 묻는다, true 면 **방금 넣은 것이
     * 틀려** 다시 묻는다. 헤더가 잠긴 것(목록 전체)과 일부 항목만 잠긴 것이 같은 창을 쓴다.
     */
    private val _asking = MutableStateFlow<Boolean?>(null)
    val asking: StateFlow<Boolean?> = _asking.asStateFlow()

    fun requestPassword() {
        _asking.value = false
    }

    fun dismissPassword() {
        _asking.value = null
    }

    /**
     * 넣은 암호로 열어 **맞는지 확인하고**, 맞으면 이번 세션에 기억한 뒤 목록을 다시 읽는다.
     *
     * 배열의 주인이 여기로 넘어온다. 확인이 끝나면(맞든 틀리든) 0 으로 덮는다 — 기억하는 것은
     * `SessionPasswords` 의 **사본**이다.
     */
    fun submitPassword(password: CharArray) {
        val path = opened?.path
        if (path == null) {
            password.fill('\u0000')
            return
        }
        val file = File(path)
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val ok = withContext(IroDispatchers.parsing) {
                    try {
                        Archives.open(FileDocumentSource(file), password = password).use { Archives.verifyPassword(it) }
                    } catch (e: ArchivePasswordException) {
                        false
                    }
                }
                if (ok) {
                    // 확인하는 사이에 앱이 화면에서 사라졌으면 `put` 이 받지 않는다(그 약속을
                    // 지키는 곳이 `SessionPasswords` 다). 그러면 아래 `load` 가 잠긴 목록을 세운다.
                    SessionPasswords.put(SessionPasswords.keyOf(file), password)
                    _asking.value = null
                    load(path)
                } else {
                    _asking.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Iro.d { "암호 확인 실패: ${t::class.java.simpleName}" }
                _asking.value = null
                _state.value = State.Failed(kindOf(t))
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun open(path: String) {
        val file = File(path)
        val now = Opened(path, file.length(), file.lastModified())
        if (opened == now) return
        // 다른 파일이다. 앞 파일의 암호 창과 풀기 계획을 넘겨받지 않는다 — 이 ViewModel 은
        // 액티비티에 묶여 파일을 바꿔도 같은 객체다(검토가 잡았다: 헤더가 잠긴 A 의 창이 B 위에 떴다).
        _asking.value = null
        _plan.value = null
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
        // 이번 세션에 이 파일의 암호를 이미 넣었으면 그것으로 연다(사본 — 끝나면 지운다).
        val key = SessionPasswords.keyOf(file)
        // 읽는 동안 세션 암호가 지워지면(앱이 화면에서 사라졌다) 이 결과는 '풀렸다' 고 말하면
        // 안 된다. 지운 횟수를 먼저 적어 두고 끝에서 견준다 — 지우는 쪽의 알림은 `Ready` 만 보므로
        // 읽는 중이면 지나친다(검토가 잡았다).
        val clearsBefore = SessionPasswords.clears.value
        val password = SessionPasswords.get(key)
        try {
            val doc = withContext(IroDispatchers.parsing) {
                val budget = EntryBudget(ParseLimits.DEFAULT)
                Archives.open(FileDocumentSource(file), ParseLimits.DEFAULT, budget, password).use { reader ->
                    Doc(
                        path = path,
                        name = file.name,
                        fileBytes = file.length(),
                        formatId = reader.formatId,
                        tree = ArchiveTree.build(reader.entries),
                        entries = reader.entries,
                        solid = reader.solid,
                        unlocked = password != null,
                    )
                }
            }
            if (password != null && SessionPasswords.clears.value != clearsBefore) {
                // 그 사이에 지워졌다. 암호 없이 다시 읽는다(다시 지워질 암호가 없으니 한 번이면 끝난다).
                password.fill('\u0000')
                load(path)
                return
            }
            _state.value = State.Ready(doc)
            Iro.d { "아카이브 ${file.name}: ${doc.formatId.label} · ${doc.entries.size}개 · solid=${doc.solid}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ArchivePasswordException) {
            // 헤더까지 잠겼다. 기억해 둔 암호가 있었다면 그것이 낡았다(파일이 바뀌었다).
            if (password != null) SessionPasswords.forget(key)
            _state.value = State.NeedsPassword
            _asking.value = false
        } catch (e: ParseLimitExceededException) {
            _state.value = State.Failed(State.Failed.Kind.TOO_LARGE)
        } catch (t: Throwable) {
            Iro.d { "아카이브 열기 실패: ${t::class.java.simpleName}" }
            _state.value = State.Failed(kindOf(t))
        } finally {
            password?.fill('\u0000')
        }
    }

    /**
     * 예외를 화면이 읽을 종류로 옮긴다. **원문 메시지를 화면에 보내지 않는다** —
     * 거기에는 절대경로와 아카이브가 심은 문자열이 들어 있다.
     */
    private fun kindOf(t: Throwable): State.Failed.Kind {
        val name = t::class.java.simpleName
        return when {
            t is ParseLimitExceededException || name.contains("MemoryLimit") -> State.Failed.Kind.TOO_LARGE
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
