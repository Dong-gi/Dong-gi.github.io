package io.github.donggi.iroiroviewer.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.io.DirectoryLister
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.GalleryScanner
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.MediaIndex
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.io.VolumeRegistry
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.ui.ThumbnailStore
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.SortSpec
import io.github.donggi.iroiroviewer.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 파일 브라우저의 상태.
 *
 * ## 왜 Navigation 라이브러리를 쓰지 않는가
 *
 * 폴더 이동은 화면 전환이 아니라 **한 화면 안의 상태 변화**다. 경로를 라우트 문자열에
 * 넣으려면 임의의 유니코드와 `/` 를 인코딩해야 하고, 그러고도 백스택 항목마다 화면이
 * 새로 만들어져 스크롤 위치를 따로 챙겨야 한다. 경로 목록을 [SavedStateHandle] 에
 * 직접 들고 있는 편이 짧고, 프로세스가 죽었다 살아나도 그대로 복원된다.
 *
 * 뷰어들이 붙는 6단계 이후에 화면이 여럿이 되면 그때 Navigation 을 도입한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    app: Application,
    private val saved: SavedStateHandle,
) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)

    // ---- 백스택 ----------------------------------------------------------

    private val _stack = MutableStateFlow(saved.get<List<String>>(KEY_STACK) ?: emptyList())
    val stack: StateFlow<List<String>> = _stack.asStateFlow()

    // ---- 어느 하위 화면을 보고 있는가 ------------------------------------

    /**
     * 갤러리·휴지통 표시 여부. **화면이 아니라 여기 둔다.**
     *
     * `BrowserScreen` 안의 `rememberSaveable` 이었는데, 갤러리에서 사진을 열면 `app` 이
     * 화면을 `Viewer` 로 바꾸면서 **`BrowserScreen` 이 컴포지션에서 통째로 빠진다.**
     * `rememberSaveable` 은 구성 변경과 프로세스 재생성은 견디지만 **컴포지션에서 빠지는
     * 것은 견디지 못한다** — 그래서 뷰어에서 뒤로 나오면 갤러리가 아니라 첫 화면으로
     * 떨어졌다(사용자가 지적했다).
     *
     * ViewModel 은 액티비티에 묶여 있어 화면이 바뀌어도 살아 있고, [SavedStateHandle] 에
     * 얹어 두면 프로세스가 죽었다 살아나도 남는다 — 옛 주석이 지키려던 것까지 지킨다.
     */
    private val _subScreen = MutableStateFlow(saved.get<String>(KEY_SUB_SCREEN) ?: SUB_NONE)
    val subScreen: StateFlow<String> = _subScreen.asStateFlow()

    fun openGallery() = setSubScreen(SUB_GALLERY)
    fun openTrash() = setSubScreen(SUB_TRASH)
    fun closeSubScreen() = setSubScreen(SUB_NONE)

    private fun setSubScreen(value: String) {
        _subScreen.value = value
        saved[KEY_SUB_SCREEN] = value
    }

    val currentPath: String? get() = _stack.value.lastOrNull()

    /**
     * 폴더를 옮길 때는 **언제나 선택을 지운다.**
     *
     * 지우지 않으면 내부 저장소에서 고른 파일이 SD 카드 폴더에서도 선택된 채로 남아,
     * 화면에 보이지도 않는 항목이 삭제·이동의 대상이 된다. 두 볼륨에 걸친 선택까지
     * 만들어진다. 선택은 '지금 보고 있는 것' 에 대한 상태다.
     */
    fun open(path: String) {
        // 저장 상태에 들어가는 값은 언제나 ArrayList 여야 한다(위 jumpTo 주석 참고).
        _stack.value = ArrayList(_stack.value + path)
        saved[KEY_STACK] = _stack.value
        clearSelection()
        setFiltering(false)
    }

    /** 위로. 더 올라갈 곳이 없으면 false — 그때는 시스템이 뒤로가기를 처리한다. */
    fun up(): Boolean {
        val s = _stack.value
        if (s.size <= 1) return false
        _stack.value = ArrayList(s.dropLast(1))
        saved[KEY_STACK] = _stack.value
        clearSelection()
        return true
    }

    /**
     * 빵부스러기에서 위쪽 조각을 눌렀을 때.
     *
     * **`subList` 를 쓰지 않는다.** 그것은 원본을 들여다보는 뷰(`ArrayList$SubList`)라
     * [SavedStateHandle] 이 "Can't put value with type ... into saved state" 로 거부하고
     * 앱이 죽는다. `take` 는 새 `ArrayList` 를 만든다.
     */
    fun jumpTo(index: Int) {
        val s = _stack.value
        if (index < 0 || index >= s.size - 1) return
        _stack.value = ArrayList(s.take(index + 1))
        saved[KEY_STACK] = _stack.value
        clearSelection()
    }

    fun goHome() {
        _stack.value = emptyList()
        saved[KEY_STACK] = ArrayList<String>()
        clearSelection()
        setFiltering(false)
    }

    // ---- 설정 ------------------------------------------------------------

    val sortSpec: StateFlow<SortSpec> =
        prefs.sortSpec.stateIn(viewModelScope, SharingStarted.Eagerly, SortSpec.DEFAULT)

    val viewMode: StateFlow<ViewMode> =
        prefs.viewMode.stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.LIST)

    val showHidden: StateFlow<Boolean> =
        prefs.showHidden.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun sortBy(key: SortKey) = viewModelScope.launch { prefs.setSortSpec(sortSpec.value.toggled(key)) }

    fun toggleFoldersFirst() = viewModelScope.launch {
        prefs.setSortSpec(sortSpec.value.let { it.copy(foldersFirst = !it.foldersFirst) })
    }

    fun toggleViewMode() = viewModelScope.launch {
        prefs.setViewMode(if (viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
    }

    fun toggleHidden() = viewModelScope.launch { prefs.setShowHidden(!showHidden.value) }

    // ---- 이름 필터 -------------------------------------------------------

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _filtering = MutableStateFlow(false)
    val filtering: StateFlow<Boolean> = _filtering.asStateFlow()

    fun setFilter(text: String) { _filter.value = text }

    fun setFiltering(on: Boolean) {
        _filtering.value = on
        if (!on) _filter.value = ""
    }

    // ---- 볼륨 ------------------------------------------------------------

    private val _volumes = MutableStateFlow(emptyList<VolumeRegistry.Volume>())
    val volumes: StateFlow<List<VolumeRegistry.Volume>> = _volumes.asStateFlow()

    /**
     * 볼륨은 꽂았다 뺐다 한다. 화면이 보일 때마다 다시 읽는다 — 캐시해 두면 SD 를
     * 뺀 뒤에도 목록에 남아 '있는데 안 열리는' 항목이 된다.
     */
    fun refreshVolumes() = viewModelScope.launch {
        _volumes.value = withContext(Dispatchers.IO) { VolumeRegistry.volumes(getApplication()) }
    }

    // ---- 나열 ------------------------------------------------------------

    /** 목록을 다시 읽게 하는 방아쇠. 파일 작업이 끝나면 올린다. */
    private val refreshTick = MutableStateFlow(0)

    fun refresh() { refreshTick.value++ }

    private val listing: StateFlow<DirectoryLister.Listing> =
        combine(_stack, showHidden, refreshTick) { stack, hidden, _ -> stack.lastOrNull() to hidden }
            .flatMapLatest { (path, hidden) ->
                if (path == null) flowOf(DirectoryLister.Listing.Ready(emptyList(), 0, 0))
                else DirectoryLister.list(path, hidden)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectoryLister.Listing.Scanning(0))

    /**
     * 화면에 그릴 목록.
     *
     * 정렬과 필터가 나열과 분리되어 있는 것은, 사용자가 정렬 기준을 바꿀 때 **디스크를
     * 다시 읽지 않기** 위해서다. 1만 개 폴더에서 그 차이가 그대로 체감된다.
     *
     * `mapLatest` 라서 바뀌는 도중의 정렬은 취소된다 — 빠르게 여러 번 누르면 마지막
     * 것만 살아남는다.
     */
    val visible: StateFlow<VisibleState> =
        combine(listing, sortSpec, _filter) { listing, spec, query -> Triple(listing, spec, query) }
            .mapLatest { (listing, spec, query) ->
                when (listing) {
                    is DirectoryLister.Listing.Scanning -> VisibleState.Scanning(listing.count)
                    is DirectoryLister.Listing.Failed -> VisibleState.Failed(listing.reason)
                    is DirectoryLister.Listing.Ready -> withContext(Dispatchers.Default) {
                        val t0 = System.nanoTime()
                        val sorted = DirectoryLister.sort(listing.entries, spec)
                        val filtered = DirectoryLister.filter(sorted, query)
                        val sortMillis = (System.nanoTime() - t0) / 1_000_000
                        // 디버그 빌드에서만 남는다. 경로는 적지 않는다 — 개수와 시간이면 충분하다.
                        Iro.d { "나열 ${listing.entries.size}개 · 스캔 ${listing.scanMillis}ms · 정렬 ${sortMillis}ms" }
                        VisibleState.Ready(
                            entries = filtered,
                            totalCount = listing.entries.size,
                            hiddenCount = listing.hiddenCount,
                            scanMillis = listing.scanMillis,
                            sortMillis = sortMillis,
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisibleState.Scanning(0))

    // ---- 선택 모드 -------------------------------------------------------

    /**
     * 고른 항목의 경로.
     *
     * **[SavedStateHandle] 에 넣지 않는다.** 1만 개를 전체 선택하면 경로 문자열이
     * 수 MB 가 되고, 그것을 번들에 담는 순간 `TransactionTooLargeException` 으로 앱이
     * 죽는다. 화면 회전으로 잃어도 되는 값이다.
     */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    fun toggleSelection(path: String) {
        val s = _selection.value
        _selection.value = if (path in s) s - path else s + path
    }

    fun selectAll(paths: List<String>) { _selection.value = paths.toSet() }

    fun clearSelection() { _selection.value = emptySet() }

    // ---- 클립보드 --------------------------------------------------------

    /**
     * 복사·이동 대기열.
     *
     * 대상 폴더를 먼저 고르게 하는 대신 **클립보드 + 붙여넣기 바** 로 한 것은 탐색
     * 자유도 때문이다. 폴더 피커를 띄우면 그 안에서 다시 탐색해야 하는데, 그 화면은
     * 지금 만든 브라우저보다 언제나 못하다.
     */
    data class Clipboard(val paths: List<String>, val move: Boolean)

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    fun cutOrCopy(move: Boolean) {
        val paths = _selection.value.toList()
        if (paths.isEmpty()) return
        _clipboard.value = Clipboard(paths, move)
        clearSelection()
    }

    fun clearClipboard() { _clipboard.value = null }

    /**
     * 붙여넣기 전에 이름 충돌을 본다. 있으면 화면이 정책을 묻는다.
     *
     * [overwritable] 은 덮어쓸 수 있는 것, [blocked] 는 이름은 같은데 **종류가 다른**
     * 것이다(파일 자리에 폴더). 종류가 다르면 덮어쓰기를 제시하지 않는다 — '파일을
     * 덮어쓴다' 는 답이 '폴더를 통째로 지운다' 로 번역되면 안 된다.
     */
    data class PasteConflicts(val overwritable: List<String>, val blocked: List<String>) {
        val isEmpty get() = overwritable.isEmpty() && blocked.isEmpty()
    }

    private val _pendingConflicts = MutableStateFlow(PasteConflicts(emptyList(), emptyList()))
    val pendingConflicts: StateFlow<PasteConflicts> = _pendingConflicts.asStateFlow()

    /**
     * **엔진과 같은 이름으로 검사한다.** 예전에는 화면이 원래 이름으로 보고 엔진은
     * `PathRules.sanitize` 를 거친 이름으로 썼다. `a?.txt` 는 리눅스에서 합법이고 실제로
     * 만들어지는데 엔진은 `a_.txt` 에 쓰므로, 경고 없이 `a_.txt` 가 덮어써졌다.
     */
    fun requestPaste() {
        val clip = _clipboard.value ?: return
        val dest = currentPath ?: return
        viewModelScope.launch {
            val conflicts = withContext(Dispatchers.IO) {
                val over = ArrayList<String>()
                val blocked = ArrayList<String>()
                for (p in clip.paths) {
                    val src = java.io.File(p)
                    val target = java.io.File(dest, io.github.donggi.iroiroviewer.io.PathRules.sanitize(src.name))
                    if (!target.exists()) continue
                    if (target.isDirectory != src.isDirectory) blocked += target.name else over += target.name
                }
                PasteConflicts(over, blocked)
            }
            if (conflicts.isEmpty) paste(FileOpEngine.Conflict.SKIP)
            else _pendingConflicts.value = conflicts
        }
    }

    fun paste(conflict: FileOpEngine.Conflict) {
        val clip = _clipboard.value ?: return
        val dest = currentPath ?: return
        _pendingConflicts.value = PasteConflicts(emptyList(), emptyList())
        val app = getApplication<Application>()
        val id = newOpId()
        // 이동은 원본을 없앤다 — 그 썸네일은 더 이상 가리킬 것이 없다.
        if (clip.move) rememberThumbKeys(id, clip.paths)
        FileOpManager.enqueue(
            app,
            if (clip.move) FileOpManager.Request.Move(id, clip.paths, dest, conflict)
            else FileOpManager.Request.Copy(id, clip.paths, dest, conflict),
        )
        if (clip.move) _clipboard.value = null
    }

    fun cancelPaste() { _pendingConflicts.value = PasteConflicts(emptyList(), emptyList()) }

    /**
     * 앱 전체가 함께 쓰는 스낵바.
     *
     * 화면마다 따로 두면 **결과를 잃는다.** 결과는 채널로 하나씩 흐르는데, 그것을 소비하는
     * 코루틴이 한 화면의 컴포지션에 묶여 있으면 사용자가 다른 화면으로 가는 순간 소비가
     * 멎고 그 뒤의 모든 결과가 조용히 사라진다 — 3·4단계에서 고친 실패 형태 그대로다.
     *
     * **그래서 새 불변식이 하나 생긴다: 화면마다 이 상태에 붙은 `SnackbarHost` 를 하나씩
     * 두어야 한다.** 붙지 않은 화면으로 넘어가면 `showSnackbar` 가 영영 돌아오지 않는다.
     */
    val snackbar = androidx.compose.material3.SnackbarHostState()

    // ---- 조작 ------------------------------------------------------------

    val opState: StateFlow<FileOpManager.State> = FileOpManager.state

    /**
     * 끝난 작업. **화면 한 곳에서만 모은다** — 폴더 화면에서만 받으면 휴지통 화면에서
     * 일으킨 영구 삭제의 성패가 아무에게도 전해지지 않고, 나중에 폴더로 돌아간 순간
     * 엉뚱한 자리에서 뒤늦게 뜬다.
     */
    val opResults = FileOpManager.results

    fun cancelOperation() = FileOpManager.cancelCurrent()

    fun trashSelected() = trash(_selection.value.toList())

    /** 휴지통으로 보낸다. 뷰어도 이 길을 쓴다. */
    fun trash(paths: List<String>) {
        if (paths.isEmpty()) return
        clearSelection()
        val id = newOpId()
        rememberThumbKeys(id, paths)
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.Trash(id, paths))
    }

    /**
     * **메타데이터를 지운 사본**을 만들어 공유한다. 원본은 건드리지 않는다.
     *
     * 사진 한 장에 찍힌 좌표는 집 주소이기도 하다. 공유가 기본으로 그것을 함께 보내는
     * 것은 사용자가 동의한 적 없는 일이고, 받는 쪽도 대개 원하지 않는다.
     *
     * 사본은 앱 캐시의 `share/` 에 둔다 — 공유가 끝나면 시스템이 치우게 두고, 공유 대상
     * 앱에는 `FileProvider` 로 **이 인텐트에만, 읽기만** 준다.
     */
    fun shareSanitized(context: android.content.Context, path: String) {
        viewModelScope.launch {
            val src = java.io.File(path)
            val copy = io.github.donggi.iroiroviewer.ui.image.ExifReader.sanitizedCopy(
                src,
                io.github.donggi.iroiroviewer.io.ShareHelper.shareDir(getApplication()),
            )
            if (copy == null) {
                _lastError.value = FileOpEngine.Reason.INVALID_NAME
                return@launch
            }
            io.github.donggi.iroiroviewer.io.ShareHelper.share(
                context,
                listOf(copy.absolutePath),
                context.getString(R.string.browser_action_share),
            )
        }
    }

    /** 사진을 EXIF 방향 태그만 고쳐 돌린다. 뷰어가 부른다. */
    fun rotate(path: String, degrees: Int) {
        val id = newOpId()
        // 회전은 파일 내용을 바꾼다. 수정시각은 보존하므로 **키가 그대로**이고,
        // 그래서 옛 썸네일이 살아남는다 — 반드시 여기서 지워야 한다.
        rememberThumbKeys(id, listOf(path))
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.RotateExif(id, path, degrees))
    }

    /**
     * 아카이브를 푼다. **복사·이동과 같은 큐를 지난다** — 같은 폴더를 두 작업이 동시에
     * 만지지 않게 하고, 포그라운드 서비스·진행률·취소·결과 보고를 그대로 얻는다.
     */
    fun extract(
        archivePath: String,
        entryIndices: List<Int>?,
        destParent: String,
        newFolderName: String?,
        conflict: FileOpEngine.Conflict,
    ) {
        FileOpManager.enqueue(
            getApplication(),
            FileOpManager.Request.Extract(
                id = newOpId(),
                archivePath = archivePath,
                entryIndices = entryIndices,
                destParent = destParent,
                newFolderName = newFolderName,
                conflict = conflict,
            ),
        )
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                FileOpEngine(MediaIndex(getApplication())).rename(path, newName)
            }
            _lastError.value = (outcome as? FileOpEngine.Outcome.Failed)?.reason
            clearSelection()
            refresh()
        }
    }

    fun createFolder(name: String) {
        val parent = currentPath ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                FileOpEngine(MediaIndex(getApplication())).createFolder(parent, name)
            }
            _lastError.value = (outcome as? FileOpEngine.Outcome.Failed)?.reason
            refresh()
        }
    }

    private val _lastError = MutableStateFlow<FileOpEngine.Reason?>(null)
    val lastError: StateFlow<FileOpEngine.Reason?> = _lastError.asStateFlow()

    fun consumeError() { _lastError.value = null }

    // ---- 휴지통 ----------------------------------------------------------

    private val trashStore by lazy { TrashStore(getApplication(), MediaIndex(getApplication())) }

    val trashEntries: StateFlow<List<io.github.donggi.iroiroviewer.data.TrashEntryEntity>> =
        trashStore.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restoreFromTrash(uuid: String) {
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.Restore(newOpId(), uuid))
    }

    fun purgeFromTrash(uuids: List<String>) {
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.Purge(newOpId(), uuids))
    }

    /**
     * 방금 휴지통에 보낸 것을 되돌린다. 스낵바의 '되돌리기' 가 부른다.
     *
     * **uuid 로 되돌린다.** (부모·이름·최근 삭제시각)으로 되찾는 방법은 쓰지 않는다 —
     * 그런 질의가 DAO 에 없고, 무엇보다 이 프로젝트는 "신원은 이름이 아니다" 를 아카이브
     * 엔트리에서 한 번 배워 회귀 시험까지 붙여 막아 두었다.
     */
    fun undoTrash(uuids: List<String>) {
        for (uuid in uuids) restoreFromTrash(uuid)
    }

    /**
     * 기간이 지난 항목을 지우고 기록과 파일을 맞춘다.
     *
     * **파일 작업 큐에 넣는다.** 예전에는 여기서 바로 돌렸는데, 그러면 휴지통으로
     * 옮기는 중인 파일 — `rename` 은 끝났고 기록은 아직인 파일 — 을 '기록 없는 고아'
     * 로 보게 된다. 큐에 넣으면 두 작업이 겹치는 일 자체가 없다.
     */
    fun reconcileTrash() {
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.Reconcile)
    }


    // ---- 즐겨찾기 --------------------------------------------------------

    private val bookmarkDao by lazy {
        io.github.donggi.iroiroviewer.data.IroiroDatabase.get(getApplication()).bookmarks()
    }

    val bookmarks: StateFlow<List<io.github.donggi.iroiroviewer.data.BookmarkEntity>> =
        bookmarkDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 지금 폴더가 즐겨찾기인가. 목록에서 뽑는다 — 경로 하나를 또 물어보면 별 아이콘이
     * 목록보다 한 박자 늦게 바뀐다.
     */
    val currentBookmarked: StateFlow<Boolean> =
        combine(_stack, bookmarks) { stack, marks ->
            val path = stack.lastOrNull()
            path != null && marks.any { it.path == path }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleBookmark() {
        val path = currentPath ?: return
        viewModelScope.launch {
            if (bookmarkDao.has(path)) {
                bookmarkDao.remove(path)
            } else {
                bookmarkDao.upsert(
                    io.github.donggi.iroiroviewer.data.BookmarkEntity(
                        path = path,
                        label = java.io.File(path).name,
                        addedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch { bookmarkDao.remove(path) }
    }

    // ---- 갤러리 ----------------------------------------------------------

    /**
     * 갤러리는 **볼 때만 훑는다.**
     *
     * 앱을 켤 때마다 미리 훑어 두면 사진이 많은 기기에서 시작이 그만큼 느려지고, 대개는
     * 갤러리를 열지 않는다. `WhileSubscribed` 가 그것을 그대로 표현한다 — 화면이 붙으면
     * 훑고, 떨어지면 멈춘다.
     */
    private val galleryTick = MutableStateFlow(0)

    fun rescanGallery() { galleryTick.value++ }

    /**
     * 마지막으로 끝난 훑기.
     *
     * **다시 훑는 동안 격자를 비우지 않으려고 들고 있는다.** [GalleryScanner.scan] 은
     * 언제나 `Scanning(0)` 으로 시작하고, 그것을 그대로 그리면 사진 한 장을 지울 때마다
     * 격자가 통째로 사라졌다가 되돌아온다 — 화면이 깜빡이면 사용자는 무엇이 바뀐 것인지
     * 읽어 낼 수 없다(10단계가 재생 조작부에서 같은 판단을 했다).
     *
     * **flow 바깥의 필드인 것이 요점이다.** `WhileSubscribed` 가 구독이 끊긴 5초 뒤에
     * 상류를 끊으므로, 갤러리 → 사진 → 갤러리로 돌아오는 길에서는 flow 안에 든 값이
     * 전부 사라진다. 그 길이 바로 이 값이 필요한 길이다.
     */
    private var lastGalleryReady: GalleryScanner.Scan.Ready? = null

    val gallery: StateFlow<GalleryScanner.Scan> =
        combine(_volumes, galleryTick) { volumes, _ -> volumes }
            .flatMapLatest { volumes ->
                if (volumes.isEmpty()) flowOf(GalleryScanner.Scan.Scanning(0))
                else GalleryScanner.scan(GalleryScanner.defaultRoots(volumes))
            }
            .map { scan ->
                when (scan) {
                    is GalleryScanner.Scan.Ready -> scan.also { lastGalleryReady = it }
                    is GalleryScanner.Scan.Scanning -> lastGalleryReady ?: scan
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryScanner.Scan.Scanning(0))

    /**
     * 폴더를 다 읽을 때마다 **그 폴더 통에서** 원본이 사라진 썸네일을 지운다.
     *
     * 요구사항이 "원본 이미지 삭제 감지되면 썸네일도 자동 삭제" 인데, 파일시스템에는
     * 삭제 통지가 없다(있어도 앱이 꺼져 있는 동안의 삭제는 못 받는다). 그래서 '지금
     * 살아 있는 것' 의 목록이 나올 때마다 그 여집합을 지우는 쪽으로 뒤집는다.
     *
     * **갤러리가 아니라 여기서 부른다.** 갤러리는 이미지만 골라 훑으므로 그 폴더의
     * 문서·동영상 썸네일을 '없는 것' 으로 보고 지운다 — 청소할 자격이 없다. 폴더를
     * 통째로 열거하는 쪽은 [DirectoryLister] 뿐이고, 그것이 주는 목록은 숨김 파일과
     * 필터 이전의 전부다.
     */
    init {
        viewModelScope.launch {
            listing.collect { l ->
                if (l !is DirectoryLister.Listing.Ready) return@collect
                val folder = currentPath ?: return@collect
                val live = l.entries.mapTo(HashSet()) {
                    ThumbnailStore.keyOf(it.path, it.size, it.lastModified)
                }
                val removed = ThumbnailStore.sweepFolder(getApplication(), folder, live)
                if (removed > 0) Iro.d { "원본이 사라진 썸네일 ${removed}개를 지웠다" }
                // 총량 상한. 다른 앱이 지운 파일이나 이름이 바뀐 폴더의 썸네일은
                // 폴더 단위 청소가 닿지 않으므로 이쪽이 걷어 간다.
                ThumbnailStore.trim(getApplication())
            }
        }
    }


    // ---- 요청 신원과 썸네일 무효화 -----------------------------------------

    /**
     * 작업마다 붙이는 신원. 결과([FileOpManager.Finished])가 이것을 그대로 달고 돌아온다.
     */
    private fun newOpId(): String = java.util.UUID.randomUUID().toString()

    /**
     * 그 작업이 끝나면 지울 썸네일 키.
     *
     * **작업을 시키기 전에 뽑아 두어야 한다.** 키가 `sha256(경로+크기+수정시각)` 이라
     * 파일이 사라진 뒤에는 크기·수정시각을 읽을 수 없어 키를 만들 수 없다.
     */
    private val pendingThumbKeys = HashMap<String, List<Pair<String, String>>>()

    private fun rememberThumbKeys(opId: String, paths: List<String>) {
        val keys = paths.mapNotNull { p ->
            val f = java.io.File(p)
            if (f.isFile) p to ThumbnailStore.keyOf(f) else null
        }
        if (keys.isNotEmpty()) pendingThumbKeys[opId] = keys
    }

    /**
     * 결과 하나를 받아 뒤처리한다. 화면(`Root`)이 결과를 소비할 때 함께 부른다.
     *
     * 화면이 아니라 여기 두는 이유는, 썸네일 무효화가 **화면과 무관하게** 일어나야 하기
     * 때문이다 — 사용자가 어느 화면에 있든 방금 지운 사진의 썸네일은 사라져야 한다.
     */
    fun onOperationFinished(finished: FileOpManager.Finished) {
        val keys = pendingThumbKeys.remove(finished.id)
        if (keys != null && finished.outcome !is FileOpEngine.Outcome.Cancelled) {
            ThumbnailStore.invalidate(getApplication(), keys)
        }
        refresh()
        // **갤러리도 다시 훑는다.** [refresh] 가 미는 것은 `listing` 뿐이라, 사진을
        // 지우고 갤러리로 돌아오면 지운 사진이 **빈 칸으로** 그대로 남아 있었다
        // (`WhileSubscribed` 의 5초 안에 돌아오면 옛 목록이 그대로 나온다). 사용자가
        // 지적한 '더미' 가 그것이다. 지우기만이 아니라 이동·이름 바꾸기·되돌리기도
        // 갤러리가 보는 파일을 옮기므로 종류를 가리지 않고 부른다.
        rescanGallery()
        _contentGeneration.value++
    }

    /**
     * 파일 내용이 바뀐 횟수.
     *
     * 회전은 **수정시각을 일부러 보존**하므로 경로도 크기도 수정시각도 그대로다 —
     * 화면이 "달라졌다" 를 알아챌 단서가 하나도 없다. 그래서 세대를 따로 센다.
     * 뷰어의 디코딩이 (경로, 세대)로 열쇠를 삼으면 회전 직후 다시 뜬다.
     */
    private val _contentGeneration = MutableStateFlow(0)
    val contentGeneration: StateFlow<Int> = _contentGeneration.asStateFlow()

    // ---- 스크롤 위치 -----------------------------------------------------

    /**
     * 폴더마다 보고 있던 위치. 위로 갔다가 돌아왔을 때 처음으로 튕기지 않게 한다.
     *
     * 프로세스가 죽으면 잃어도 되는 값이라 [SavedStateHandle] 에 넣지 않는다 —
     * 폴더 수만큼 늘어나는 것을 번들에 담으면 TransactionTooLarge 로 가는 길이다.
     */
    private val scrollPositions = HashMap<String, Pair<Int, Int>>()

    fun rememberScroll(path: String, index: Int, offset: Int) {
        scrollPositions[path] = index to offset
    }

    fun scrollOf(path: String): Pair<Int, Int> = scrollPositions[path] ?: (0 to 0)

    companion object {
        const val SUB_NONE = ""
        const val SUB_GALLERY = "gallery"
        const val SUB_TRASH = "trash"

        private const val KEY_SUB_SCREEN = "browser.sub"
        private const val KEY_STACK = "browser.stack"
    }
}

/** 화면이 그릴 것. */
sealed interface VisibleState {

    data class Scanning(val count: Int) : VisibleState

    data class Ready(
        val entries: List<FileEntry>,
        /** 필터를 걸기 전의 개수. '3 / 1204' 처럼 보여주는 데 쓴다. */
        val totalCount: Int,
        val hiddenCount: Int,
        val scanMillis: Long,
        val sortMillis: Long,
    ) : VisibleState

    data class Failed(val reason: DirectoryLister.Reason) : VisibleState
}
