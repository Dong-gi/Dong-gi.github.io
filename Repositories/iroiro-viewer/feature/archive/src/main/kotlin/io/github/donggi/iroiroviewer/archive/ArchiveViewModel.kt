package io.github.donggi.iroiroviewer.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchiveKind
import io.github.donggi.iroiroviewer.format.archive.ArchivePasswordException
import io.github.donggi.iroiroviewer.format.archive.ArchiveTree
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileProbe
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
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
        /** 컨테이너의 모양. 화면이 형식 이름(ZIP·7z·RAR·TAR.GZ…)을 이것으로 적는다. */
        val kind: ArchiveKind,
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
        /**
         * 목록을 읽는 중. [reached] 는 **이번 열기에서 파일에 닿았다**(실제로 열어 봤다 — [reachState])는 표시다. 닿기
         * 전에는 없거나 열리지 않는 파일일 수 있어 ⋮ 의 '다른 앱으로 열기' 를 흐리게 둔다([ArchiveOpenWith.inMenu]).
         * 닿은 뒤의 목록 읽기(압축한 tar 는 몇 초)는 기다리지 않고 넘길 수 있다.
         */
        data class Loading(val reached: Boolean = false) : State
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

                /** 항목이 너무 많다(`maxEntries`). */
                TOO_LARGE,

                /**
                 * 풀어야 할 양이 너무 크다 — 압축한 tar 의 목록은 처음부터 끝까지 풀어야 하고 그 양이 총량 상한에
                 * 묶인다. xz·7z 의 사전이 메모리 상한을 넘는 것도 여기다.
                 */
                TOO_BIG,

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

    private val _state = MutableStateFlow<State>(State.Loading())
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
                val ok = runInterruptible(IroDispatchers.parsing) {
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
                // 취소가 리더 안에서 `InterruptedIOException` 으로 올라왔다면 실패가 아니다 — 다음 파일의 화면에
                // 앞 파일의 '깨졌다' 를 쓰지 않게 여기서 멈춘다.
                currentCoroutineContext().ensureActive()
                Iro.d { "암호 확인 실패: ${t::class.java.simpleName}" }
                _asking.value = null
                // 암호 창이 떠 있는 사이에 파일이 없어졌을 수 있다 — 그 입출력 예외는 '깨졌다' 가 아니다([failureKindOf]).
                val reachable = withContext(IroDispatchers.io) { isReachable(file) }
                _state.value = State.Failed(failureKindOf(t, reachable))
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
        _state.value = State.Loading()
        _folder.value = ""
        _filter.value = ""
        val file = File(path)
        // **있는지만이 아니라 열리는지까지 본다.** 열리지 않는 파일을 리더에 넘기면 그 예외(`FileNotFoundException` —
        // 안드로이드는 권한 거부도 이것으로 알린다)가 `IOException` 이라 [failureKindOf] 가 '깨졌다' 로 옮기고, 그 갈래는
        // '다른 앱으로 열기' 까지 권한다([ArchiveOpenWith]) — 받는 앱도 같은 파일에 닿지 못한다. 문서 뷰어가 같은 까닭으로
        // 같은 검사를 한다(`DocViewModel` 의 `openErrorOf`). 치르는 값은 서술자 하나를 열고 닫는 것이다.
        // 닿았으면 '닿은 채로 읽는 중' 을 낸다 — 그때부터 ⋮ 의 '다른 앱으로 열기' 를 누를 수 있다([State.Loading.reached]).
        val reach = reachState(withContext(IroDispatchers.io) { isReachable(file) })
        _state.value = reach
        if (reach is State.Failed) return
        // 이번 세션에 이 파일의 암호를 이미 넣었으면 그것으로 연다(사본 — 끝나면 지운다).
        val key = SessionPasswords.keyOf(file)
        // 읽는 동안 세션 암호가 지워지면(앱이 화면에서 사라졌다) 이 결과는 '풀렸다' 고 말하면
        // 안 된다. 지운 횟수를 먼저 적어 두고 끝에서 견준다 — 지우는 쪽의 알림은 `Ready` 만 보므로
        // 읽는 중이면 지나친다(검토가 잡았다).
        val clearsBefore = SessionPasswords.clears.value
        val password = SessionPasswords.get(key)
        try {
            // **인터럽트로 끊을 수 있게 읽는다.** 압축한 tar 의 목록은 스트림을 끝까지 풀어야 해서 몇 초씩 걸린다 —
            // 화면을 떠나 잡이 취소되어도 `withContext` 는 블로킹 해제에 닿지 못해 끝까지 달린다.
            val doc = runInterruptible(IroDispatchers.parsing) {
                val budget = EntryBudget(ParseLimits.DEFAULT)
                Archives.open(FileDocumentSource(file), ParseLimits.DEFAULT, budget, password).use { reader ->
                    Doc(
                        path = path,
                        name = file.name,
                        fileBytes = file.length(),
                        kind = reader.kind,
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
            Iro.d { "아카이브 ${file.name}: ${doc.kind.label} · ${doc.entries.size}개 · solid=${doc.solid}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ArchivePasswordException) {
            // 헤더까지 잠겼다. 기억해 둔 암호가 있었다면 그것이 낡았다(파일이 바뀌었다).
            if (password != null) SessionPasswords.forget(key)
            _state.value = State.NeedsPassword
            _asking.value = false
        } catch (t: Throwable) {
            // 읽는 도중 취소되면 리더가 `InterruptedIOException` 을 던진다(`runInterruptible` 은 그것을 취소로
            // 옮기지 않는다). 그대로 '깨졌다' 를 쓰면 **새로 연 파일의 화면**에 앞 파일의 실패가 덮인다.
            currentCoroutineContext().ensureActive()
            Iro.d { "아카이브 열기 실패: ${t::class.java.simpleName}" }
            // 목록을 읽는 사이에 파일이 없어졌을 수 있다(압축한 tar 는 몇십 초를 읽는다 — 그동안 SD 를 빼거나 다른 앱이
            // 지운다). 실패한 길에서만 한 번 더 열어 본다([failureKindOf]).
            val reachable = withContext(IroDispatchers.io) { isReachable(file) }
            _state.value = State.Failed(failureKindOf(t, reachable))
        } finally {
            password?.fill('\u0000')
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
            folderName = ExtractNames.folderNameOf(File(doc.path).name),
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

/**
 * [file] 이 목록을 읽으러 갈 수 있는 파일인가 — 보통 파일이고 **실제로 열린다.**
 *
 * `canRead()` 로 묻지 않는다 — 묻는 답과 여는 답이 FUSE 위에서 같다는 것을 확인한 적이 없고, 틀리면 멀쩡한 압축 파일이
 * 전부 '읽을 수 없다' 가 된다. 탐침은 [FileProbe] 한 벌이다(문서·만화·이미지 뷰어가 같은 답을 낸다). [open] 은 시험이 열리지
 * 않는 파일을 흉내 내는 자리다(JVM 에서는 있는데 열리지 않는 파일을 만들 수 없다).
 */
internal fun isReachable(file: File, open: (File) -> Unit = FileProbe.openAndClose): Boolean = FileProbe.opens(file, open)

/**
 * 파일에 닿아 본 뒤의 상태 — 닿지 못했으면 단추 없는 실패 화면, 닿았으면 **닿은 채로 읽는 중**이다. 읽는 중의 ⋮ 가
 * '다른 앱으로 열기' 를 누를 수 있게 하는 것은 이 함수가 닿았다고 답한 뒤뿐이다([ArchiveOpenWith.inMenu]).
 */
internal fun reachState(reachable: Boolean): ArchiveViewModel.State =
    if (reachable) {
        ArchiveViewModel.State.Loading(reached = true)
    } else {
        ArchiveViewModel.State.Failed(ArchiveViewModel.State.Failed.Kind.UNREADABLE)
    }

/**
 * 읽다 실패한 종류 — 실패한 **뒤에** 파일에 닿는지([reachable])를 함께 본다. 닿지 못하면 무엇이 던져졌든 '읽을 수 없다'
 * (UNREADABLE)다. 열어 볼 때([reachState])는 닿았는데 읽는 사이에 없어진 파일(지웠다·SD 를 뺐다)은 리더가 맨
 * `IOException` 으로 알리고, 그것을 [failureKindOf] 가 '깨졌다' 로 옮기면 실패 화면이 **없는 파일에** '다른 앱으로 열기' 를
 * 권한다([ArchiveOpenWith.onFailure]). 닿으면 예외의 종류 그대로다.
 */
internal fun failureKindOf(t: Throwable, reachable: Boolean): ArchiveViewModel.State.Failed.Kind =
    if (reachable) failureKindOf(t) else ArchiveViewModel.State.Failed.Kind.UNREADABLE

/**
 * 예외를 화면이 읽을 종류로 옮긴다. **원문 메시지를 화면에 보내지 않는다** —
 * 거기에는 절대경로와 아카이브가 심은 문자열이 들어 있다.
 *
 * 상한은 **어느 상한인가**로 가른다. 항목 수는 '너무 많다', 풀어야 할 양(압축한 tar 의 목록·사전 메모리)은
 * '너무 크다' 다 — 둘을 한 문장으로 말하면 1 GiB 넘는 `.tar.gz` 에 '항목이 너무 많다' 가 뜬다.
 */
internal fun failureKindOf(t: Throwable): ArchiveViewModel.State.Failed.Kind {
    val name = t::class.java.simpleName
    return when {
        t is ParseLimitExceededException && t.limitName == "maxEntries" -> ArchiveViewModel.State.Failed.Kind.TOO_LARGE
        t is ParseLimitExceededException || name.contains("MemoryLimit") -> ArchiveViewModel.State.Failed.Kind.TOO_BIG
        name.contains("Password", ignoreCase = true) ||
            name.contains("Encrypt", ignoreCase = true) -> ArchiveViewModel.State.Failed.Kind.ENCRYPTED
        t is IllegalStateException -> ArchiveViewModel.State.Failed.Kind.UNSUPPORTED
        t is IOException -> ArchiveViewModel.State.Failed.Kind.CORRUPT
        else -> ArchiveViewModel.State.Failed.Kind.CORRUPT
    }
}
