package io.github.donggi.iroiroviewer.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.data.TrashEntryEntity
import io.github.donggi.iroiroviewer.io.DirStamp
import io.github.donggi.iroiroviewer.io.DirectoryLister
import io.github.donggi.iroiroviewer.io.DirectoryWatcher
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.ForegroundRelist
import io.github.donggi.iroiroviewer.io.GalleryScanner
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.LastListed
import io.github.donggi.iroiroviewer.io.MediaIndex
import io.github.donggi.iroiroviewer.io.TrashPurgeJob
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.io.VolumeRegistry
import io.github.donggi.iroiroviewer.io.settle
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.ui.ThumbnailStore
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.SortSpec
import io.github.donggi.iroiroviewer.model.ViewMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * 목록을 다시 읽게 하는 방아쇠. 파일 작업이 끝났을 때·폴더가 바뀌었다는 알림이 왔을 때·당겨서 새로고침할 때
     * 올린다. **번호가 곧 꼬리표다**([TaggedListing]) — 같은 목록이 다시 나와도 '다시 읽기가 끝났다' 를 알 수 있다.
     */
    private val refreshTick = MutableStateFlow(0)

    fun refresh() { refreshTick.update { it + 1 } }

    /** 폴더 하나를 읽은 것에 (경로, 번호) 꼬리표를 붙인다. 모으는 것은 아래 `init` 하나다. */
    private val taggedListing: Flow<TaggedListing> =
        combine(_stack, showHidden, refreshTick) { stack, hidden, tick -> Triple(stack.lastOrNull(), hidden, tick) }
            .flatMapLatest { (path, hidden, tick) ->
                if (path == null) {
                    flowOf(TaggedListing(null, tick, DirectoryLister.Listing.Ready(emptyList(), 0, 0)))
                } else {
                    DirectoryLister.list(path, hidden).map { TaggedListing(path, tick, it) }
                }
            }

    /**
     * 화면에 보일 나열. **같은 폴더를 다시 읽는 동안 옛 목록을 붙들어 둔다**([ListingHold.next]) — 예전에는 파일
     * 하나를 지울 때마다 목록이 '0개 읽는 중' 으로 바뀌었다가 돌아왔다.
     */
    private val _held = MutableStateFlow(ListingHold.INITIAL)

    /** 마지막으로 끝난 읽기(화면으로 돌아왔을 때 견줄 값). 주 스레드에서만 쓰고 읽는다. */
    private var lastListed: LastListed? = null

    /** 총량 청소를 마지막으로 돌린 폴더. 같은 폴더를 다시 읽을 때마다 캐시 전체를 훑지 않으려는 것이다. */
    private var trimmedFor: String? = null

    /** 지금 도는 썸네일 청소([onListingArrived]). 주 스레드에서만 바꾼다. */
    private var housekeeping: Job? = null

    /** 고르는 동안 미룬 저절로 다시 읽기의 폴더([autoRefresh]). 없으면 null. 주 스레드에서만 쓴다. */
    private var deferredRefreshFor: String? = null

    /**
     * 폴더 화면이 보이는가. 보일 때만 폴더를 듣는다([onFolderShown]).
     *
     * **`init` 보다 앞에 둔다.** `viewModelScope` 는 `Dispatchers.Main.immediate` 라 주 스레드에서 만든 VM 의
     * `init` 안 `launch` 는 그 자리에서 첫 정지 지점까지 돈다 — 그때 아래에 적힌 프로퍼티는 아직 null 이다.
     */
    private val _watching = MutableStateFlow(false)

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
        combine(_held, sortSpec, _filter) { held, spec, query -> Triple(held.shown, spec, query) }
            // 붙들어 둔 목록 위에서 '다시 읽는 중' 깃발만 바뀐 것은 다시 정렬할 까닭이 아니다(1만 개에 87 ms).
            .distinctUntilChanged()
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
                // 압축 화면에서 넣은 암호가 있으면 **지금** 사본을 붙든다(`Request.Extract` 의 주석).
                password = io.github.donggi.iroiroviewer.io.SessionPasswords.get(
                    io.github.donggi.iroiroviewer.io.SessionPasswords.keyOf(java.io.File(archivePath)),
                ),
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
        dropFromRestorePick(listOf(uuid))
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.Restore(newOpId(), uuid))
    }

    fun purgeFromTrash(uuids: List<String>) {
        dropFromRestorePick(uuids)
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.Purge(newOpId(), uuids))
    }

    /**
     * 휴지통에서 따로 되돌리거나 지운 항목은 '다른 곳에 복원' 바에서도 뺀다. 남겨 두면 바가 이미 없는 항목을
     * 가리키고, '여기에 복원' 이 '1개 실패' 로 끝난다.
     */
    private fun dropFromRestorePick(uuids: List<String>) {
        val pick = _restorePick.value ?: return
        val left = pick.uuids - uuids.toSet()
        _restorePick.value = if (left.isEmpty()) null else pick.copy(uuids = left)
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
        FileOpManager.enqueue(getApplication(), FileOpManager.Request.Reconcile())
        // **앱을 켜지 않아도 하루에 한 번 돌게 건다**(B3). 이미 같은 모양으로 걸려 있으면 아무것도 하지 않는다 —
        // 다시 걸면 주기의 시계가 처음부터 가서, 앱을 매일 켜는 사람에게는 영영 돌지 않는다(`TrashPurgeJob.Spec`).
        // 이 함수가 프로세스당 한 번 불리는 자리(`Root`)라 조립을 바꿀 것이 없다.
        viewModelScope.launch(Dispatchers.IO) { TrashPurgeJob.ensureScheduled(getApplication()) }
    }

    /** 휴지통의 당겨서 새로고침 표시. */
    private val _trashRefreshing = MutableStateFlow(false)
    val trashRefreshing: StateFlow<Boolean> = _trashRefreshing.asStateFlow()

    /**
     * 휴지통을 당겼다. 볼륨을 다시 읽고(SD 를 다시 꽂았으면 '연결되지 않음' 이 풀린다), 기록과 파일을 맞춘다.
     *
     * **맞추기는 앱을 켤 때의 검사 그대로다** — 파일 작업 큐에서 돌고, 무엇인지 아는 것만 지운다(기간이 지난
     * 기록·짝 없는 사이드카). 기록 없는 파일은 지우지 않고 목록에 띄운다. 목록 자체는 Room 이 정본이라 저절로 바뀐다.
     */
    fun pullTrash() {
        if (_trashRefreshing.value) return
        _trashRefreshing.value = true
        viewModelScope.launch {
            holdIndicator {
                _volumes.value = withContext(Dispatchers.IO) { VolumeRegistry.volumes(getApplication()) }
                val done = CompletableDeferred<Boolean>()
                FileOpManager.enqueue(getApplication(), FileOpManager.Request.Reconcile(done = done))
                // 큐가 긴 복사를 돌리는 중이면 차례가 늦게 온다. 표시를 그만큼 붙들지 않는다.
                withTimeoutOrNull(TRASH_PULL_TIMEOUT_MS) { done.await() }
            }
            _trashRefreshing.value = false
        }
    }

    // ---- 다른 곳에 복원 ----------------------------------------------------

    /**
     * 휴지통 항목을 **고른 폴더로** 되돌리는 중(B4). 붙여넣기 바([clipboard])와 같은 모양이다 — 폴더 피커를 따로
     * 띄우지 않고 브라우저로 원하는 폴더까지 가서 '여기에 복원' 을 누른다.
     *
     * [SavedStateHandle] 에 넣지 않는다. 클립보드와 같은 값이다(프로세스가 죽으면 잃어도 된다 — 휴지통에 그대로 있다).
     */
    data class RestorePick(val uuids: List<String>, val firstName: String)

    private val _restorePick = MutableStateFlow<RestorePick?>(null)
    val restorePick: StateFlow<RestorePick?> = _restorePick.asStateFlow()

    private val _pendingRestoreConflicts = MutableStateFlow(PasteConflicts(emptyList(), emptyList()))
    val pendingRestoreConflicts: StateFlow<PasteConflicts> = _pendingRestoreConflicts.asStateFlow()

    /**
     * '다른 곳에 복원'. 고르는 상태로 들어가고, 고르기 시작할 폴더로 간다 — 원래 폴더가 있으면 거기, 없으면 그
     * 항목의 볼륨 루트([RestoreStart.folderFor]). 휴지통은 첫 화면에서 들어오는데 첫 화면에는 붙여넣기 바가 없어서,
     * 그대로 휴지통만 닫으면 바가 보이지 않는다.
     */
    fun startRestoreElsewhere(entry: TrashEntryEntity) {
        _restorePick.value = RestorePick(listOf(entry.uuid), entry.originalName)
        viewModelScope.launch {
            val volumes = volumesOrLoad()
            val start = withContext(Dispatchers.IO) {
                RestoreStart.folderFor(
                    originalParent = entry.originalParent,
                    volumeId = entry.volumeId,
                    volumes = volumes.map { it.id to it.path },
                    isDirectory = { java.io.File(it).isDirectory },
                )
            }
            if (start == null) closeSubScreen() else applyStack(FolderStack.stackFor(start, volumes.map { it.path }))
        }
    }

    /** '여기에 복원'. 붙여넣기와 같이 **엔진과 같은 이름으로** 충돌을 먼저 본다(`TrashStore.restoreConflicts`). */
    fun requestRestoreHere() {
        val pick = _restorePick.value ?: return
        val dest = currentPath ?: return
        viewModelScope.launch {
            val (over, blocked) = trashStore.restoreConflicts(pick.uuids, dest)
            val conflicts = PasteConflicts(over, blocked)
            if (conflicts.isEmpty) restoreHere(FileOpEngine.Conflict.SKIP)
            else _pendingRestoreConflicts.value = conflicts
        }
    }

    fun restoreHere(conflict: FileOpEngine.Conflict) {
        val pick = _restorePick.value ?: return
        val dest = currentPath ?: return
        _pendingRestoreConflicts.value = PasteConflicts(emptyList(), emptyList())
        FileOpManager.enqueue(
            getApplication(),
            FileOpManager.Request.RestoreTo(newOpId(), ArrayList(pick.uuids), dest, conflict),
        )
        _restorePick.value = null
    }

    fun cancelRestoreConflicts() { _pendingRestoreConflicts.value = PasteConflicts(emptyList(), emptyList()) }

    fun cancelRestorePick() {
        _restorePick.value = null
        cancelRestoreConflicts()
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

    fun rescanGallery() { galleryTick.update { it + 1 } }

    /** 끝난 마지막 훑기의 번호. 당겨서 새로고침이 자기 번호의 훑기가 끝났는지 여기서 본다(목록의 꼬리표와 같다). */
    private val galleryCompleted = MutableStateFlow(-1)

    private val _galleryRefreshing = MutableStateFlow(false)
    val galleryRefreshing: StateFlow<Boolean> = _galleryRefreshing.asStateFlow()

    /** 갤러리를 당겼다 — 다시 훑는다. 훑는 동안 격자는 앞의 것을 그대로 보인다([lastGalleryReady]). */
    fun pullGallery() {
        if (_galleryRefreshing.value) return
        _galleryRefreshing.value = true
        val target = galleryTick.updateAndGet { it + 1 }
        viewModelScope.launch {
            holdIndicator {
                withTimeoutOrNull(PULL_TIMEOUT_MS) { galleryCompleted.first { it >= target } }
            }
            _galleryRefreshing.value = false
        }
    }

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
        combine(_volumes, galleryTick) { volumes, tick -> volumes to tick }
            .flatMapLatest { (volumes, tick) ->
                if (volumes.isEmpty()) {
                    // 훑을 곳이 없다 — 끝난 것으로 친다. 아니면 당긴 표시가 시간 상한까지 돈다.
                    flowOf(tick to GalleryScanner.Scan.Scanning(0)).onEach { galleryCompleted.value = tick }
                } else {
                    GalleryScanner.scan(GalleryScanner.defaultRoots(volumes)).map { tick to it }
                }
            }
            .map { (tick, scan) ->
                when (scan) {
                    is GalleryScanner.Scan.Ready -> scan.also {
                        lastGalleryReady = it
                        galleryCompleted.value = tick
                    }
                    is GalleryScanner.Scan.Scanning -> lastGalleryReady ?: scan
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryScanner.Scan.Scanning(0))

    /**
     * 폴더 목록을 모으고(썸네일 청소·선택 줄이기는 [onListingArrived]), 보고 있는 폴더를 듣는다.
     */
    init {
        // **나열을 모으는 것은 여기 하나다.** 화면이 떠나 있어도 모은다(예전의 `listing.collect` 도 그랬다) —
        // 뷰어에서 사진을 지우면 돌아오기 전에 목록이 이미 새것이다.
        viewModelScope.launch {
            taggedListing.collect { t ->
                _held.value = ListingHold.next(_held.value, t)
                onListingArrived(t)
            }
        }

        // **보고 있는 폴더를 듣는다**(B2). 화면이 보일 때만([onFolderShown]) — 멈춘 화면을 위해 다시 읽는 것은
        // 버리는 일이고, 돌아올 때 수정 시각을 한 번 견주면 된다. 폴더가 바뀌면 `flatMapLatest` 가 앞의 감시를 끊는다.
        viewModelScope.launch {
            combine(_stack, _watching) { stack, on -> if (on) stack.lastOrNull() else null }
                .distinctUntilChanged()
                .flatMapLatest { path ->
                    if (path == null) emptyFlow()
                    else DirectoryWatcher.changes(path).settle(WATCH_QUIET_MS, WATCH_MAX_WAIT_MS).map { path }
                }
                .collect { path ->
                    Iro.d { "폴더가 바뀌었다는 알림 — 다시 읽는다" }
                    autoRefresh(path)
                }
        }

        // 고르는 동안 미룬 저절로 다시 읽기를 고르기가 끝나면 한 번 한다([AutoRefreshRules]).
        viewModelScope.launch {
            _selection.collect { s ->
                if (s.isNotEmpty()) return@collect
                val deferred = deferredRefreshFor ?: return@collect
                deferredRefreshFor = null
                if (AutoRefreshRules.readDeferred(deferred, currentPath)) refresh()
            }
        }
    }

    /**
     * 폴더 감시·수정 시각 대조가 부르는 다시 읽기. **고르는 중이면 미룬다**([AutoRefreshRules.readNow]) — 사용자가
     * 시킨 것(작업 결과·당겨서 새로고침)은 이 길을 지나지 않고 곧바로 읽는다.
     */
    private fun autoRefresh(path: String) {
        if (AutoRefreshRules.readNow(selecting = _selection.value.isNotEmpty())) {
            refresh()
        } else {
            deferredRefreshFor = path
        }
    }

    /**
     * 나열 하나가 도착했을 때의 뒤처리 — 돌아왔을 때 견줄 수정 시각, 사라진 것을 뺀 선택, 썸네일 청소.
     *
     * 썸네일 청소가 여기인 까닭: 요구사항이 "원본 이미지 삭제 감지되면 썸네일도 자동 삭제" 인데 파일시스템에는
     * 앱이 꺼져 있는 동안의 삭제 통지가 없다. 그래서 '지금 살아 있는 것' 의 목록이 나올 때마다 그 여집합을 지운다.
     * **갤러리가 아니라 여기서 부른다** — 갤러리는 이미지만 골라 훑으므로 그 폴더의 문서·동영상 썸네일을 '없는 것'
     * 으로 보고 지운다. 폴더를 통째로 열거하는 쪽은 [DirectoryLister] 뿐이다.
     */
    private fun onListingArrived(t: TaggedListing) {
        val folder = t.path ?: return
        val l = t.listing
        // 아직 읽는 중이면 할 일이 없다. 끝난 것은 실패여도 그 시각을 적는다([ForegroundRelist.lastListedOf]).
        lastListed = ForegroundRelist.lastListedOf(folder, l) ?: return
        if (folder == currentPath) SelectionRules.prune(_selection.value, l)?.let { _selection.value = it }
        if (l !is DirectoryLister.Listing.Ready) return

        // **청소를 따로 돌린다.** 나열을 모으는 코루틴 안에서 기다리면 그동안 다음 나열이 화면에 오르지 못한다 —
        // 폴더 감시·당겨서 새로고침으로 같은 폴더를 자주 다시 읽게 된 뒤로, 목록과 당긴 표시가 캐시 정리를
        // 기다리는 꼴이 된다. 새 목록이 오면 앞의 청소는 그만둔다(지우는 것은 낡은 캐시뿐이라 다음 목록이 마저 한다).
        housekeeping?.cancel()
        housekeeping = viewModelScope.launch { sweepThumbnails(folder, l.entries) }
    }

    private suspend fun sweepThumbnails(folder: String, entries: List<FileEntry>) {
        // 키는 항목마다 SHA-256 이다(1만 개면 수십 ms). 주 스레드에서 뺀다.
        val live = withContext(Dispatchers.Default) {
            entries.mapTo(HashSet()) { ThumbnailStore.keyOf(it.path, it.size, it.lastModified) }
        }
        val removed = ThumbnailStore.sweepFolder(getApplication(), folder, live)
        if (removed > 0) Iro.d { "원본이 사라진 썸네일 ${removed}개를 지웠다" }
        // 총량 상한. 다른 앱이 지운 파일이나 이름이 바뀐 폴더의 썸네일은 폴더 단위 청소가 닿지 않으므로 이쪽이
        // 걷어 간다. **폴더를 옮겼을 때만** 돌린다 — 캐시 전체를 stat 하므로, 폴더 감시로 같은 폴더를 자주 다시
        // 읽게 된 뒤로 그때마다 돌리면 그것이 가장 비싼 일이 된다. 끝난 뒤에 적는다 — 도중에 그만두었으면 다음에 다시 한다.
        if (trimmedFor != folder) {
            ThumbnailStore.trim(getApplication())
            trimmedFor = folder
        }
    }

    // ---- 당겨서 새로고침 · 폴더 감시 --------------------------------------

    /**
     * 당겨서 새로고침의 표시(B1, 사용자 요청). **사용자가 당긴 것에만** 켠다 — 폴더 감시나 파일 작업 뒤의 다시
     * 읽기는 조용히 돈다(목록을 붙들어 두므로 볼 것이 없다).
     */
    private val _pullRefreshing = MutableStateFlow(false)
    val pullRefreshing: StateFlow<Boolean> = _pullRefreshing.asStateFlow()

    /**
     * 당겼다 놓았다. 지금 폴더를 다시 읽고, **그 읽기가 끝날 때까지** 표시를 켜 둔다.
     *
     * 끝을 아는 방법은 번호다 — 올린 번호 이상의 읽기가 끝나야([HeldListing.completedTick]) 끈다. 그 사이에 다른
     * 폴더로 옮겨도 새 폴더의 읽기가 같은 번호로 끝나므로 표시가 남지 않는다.
     */
    fun pullToRefresh() {
        if (_pullRefreshing.value) return
        _pullRefreshing.value = true
        val target = refreshTick.updateAndGet { it + 1 }
        viewModelScope.launch {
            holdIndicator {
                withTimeoutOrNull(PULL_TIMEOUT_MS) { _held.first { it.completedTick >= target } }
            }
            _pullRefreshing.value = false
        }
    }

    /**
     * 표시를 **적어도 [MIN_INDICATOR_MS] 동안** 켜 둔다. 작은 폴더는 몇 ms 에 읽혀, 표시가 켜졌다 꺼지는 것이
     * 한 프레임 안에 끝나면 사용자는 당긴 것이 먹혔는지 알 수 없다.
     */
    private suspend fun holdIndicator(work: suspend () -> Unit) {
        val started = System.nanoTime()
        work()
        val left = MIN_INDICATOR_MS - (System.nanoTime() - started) / 1_000_000
        if (left > 0) delay(left)
    }

    /**
     * 폴더 화면이 **보이기 시작했다**(앱이 앞으로 왔다 · 뷰어에서 돌아왔다 · 첫 화면에서 들어왔다).
     *
     * 듣기를 켜고, 들지 않던 동안의 변화를 **수정 시각 한 번**으로 본다 — 그동안 바뀌었으면 다시 읽는다.
     * 알림은 FUSE 너머 다른 앱의 변화를 놓칠 수 있다(`DirectoryWatcher` 의 가정). 처음 들어온 폴더는 읽기가
     * 이미 돌고 있으므로 견주지 않는다([ForegroundRelist.shouldRelist]).
     */
    fun onFolderShown() {
        _watching.value = true
        val path = currentPath ?: return
        val last = lastListed
        // 이 폴더의 목록이 아직 안 나왔으면(방금 들어왔다) 그 읽기가 곧 새것이다. 다시 읽는 중이어도 같다.
        val held = _held.value
        val inFlight = held.path != path || held.shown is DirectoryLister.Listing.Scanning || held.refreshing
        viewModelScope.launch {
            val now = withContext(Dispatchers.IO) { DirStamp.of(path) }
            if (currentPath == path && ForegroundRelist.shouldRelist(last, path, now, inFlight)) {
                Iro.d { "떠나 있던 동안 폴더가 바뀌었다 — 다시 읽는다" }
                autoRefresh(path)
            }
        }
    }

    /** 폴더 화면이 **가려졌다**(앱이 뒤로 갔다 · 뷰어가 열렸다 · 첫 화면으로 나왔다). 듣기를 끈다. */
    fun onFolderHidden() {
        _watching.value = false
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
        // **갤러리도 다시 훑는다.** [refresh] 가 미는 것은 폴더 목록(`taggedListing`)뿐이라, 사진을
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

    // ---- 폴더로 가기 -----------------------------------------------------

    /**
     * 그 폴더로 간다(B5 — 풀기가 끝난 스낵바의 '열기'). **보통의 탐색과 같은 백스택**을 만든다
     * ([FolderStack.stackFor]) — 첫 화면에서 볼륨을 누르고 한 칸씩 들어간 것과 같아서, 위로 가기가 한 칸씩
     * 올라가고 빵부스러기가 온전하다. 갤러리·휴지통은 닫고, 선택과 이름 필터는 [open] 처럼 지운다.
     *
     * 폴더가 그 사이 사라졌으면 그 자리의 목록이 '사라졌습니다' 로 말한다 — 여기서 따로 막지 않는다.
     */
    fun openFolder(path: String) {
        viewModelScope.launch {
            val roots = volumesOrLoad().map { it.path }
            applyStack(FolderStack.stackFor(path, roots))
        }
    }

    /** 백스택을 통째로 바꾼다. 저장 상태에는 언제나 새 `ArrayList` 로 넣는다(`subList` 함정). */
    private fun applyStack(stack: List<String>) {
        _stack.value = ArrayList(stack)
        saved[KEY_STACK] = _stack.value
        closeSubScreen()
        clearSelection()
        setFiltering(false)
    }

    /** 볼륨 목록. 첫 화면을 거치지 않고 온 길(프로세스가 되살아나 압축 화면에서 시작)이면 비어 있어 여기서 읽는다. */
    private suspend fun volumesOrLoad(): List<VolumeRegistry.Volume> =
        _volumes.value.ifEmpty {
            withContext(Dispatchers.IO) { VolumeRegistry.volumes(getApplication()) }.also { _volumes.value = it }
        }

    /**
     * 풀기 결과에서 '열기' 로 갈 폴더(없으면 null). `app` 의 스낵바가 이것으로 단추를 보일지 정한다 —
     * 규칙은 `core:io` 의 `ExtractResults` 에 있다.
     */
    fun folderToOpen(finished: FileOpManager.Finished): String? = finished.folderToOpen

    // ---- 읽던 쪽 배지 ----------------------------------------------------

    /**
     * 이어보기 표가 바뀔 때마다 한 번씩 흐른다. **뷰어에서 돌아왔을 때 배지가 새것인 까닭이 이것이다** — 뷰어는
     * 닫을 때 마지막 자리를 앱 수명의 스코프에서 **늦게** 쓴다(`DocViewModel.close`). 돌아온 순간 한 번 묻는 것만으로는
     * 그 쓰기보다 먼저 물어 옛 쪽을 보인다. 표의 무효화를 들으면 쓰기가 끝난 뒤에 다시 묻는다.
     */
    private val progressChanges: Flow<Unit> = flow {
        emitAll(
            IroiroDatabase.get(getApplication()).invalidationTracker
                .createFlow("comic_progress", "doc_progress", emitInitialState = false)
                .map { },
        )
    }
        .catch { e ->
            // 표를 들을 수 없어도 목록은 그려야 한다. 한 번은 묻는다(아래 onStart).
            Iro.d { "이어보기 표를 듣지 못했다: ${e::class.java.simpleName}" }
        }
        .onStart { emit(Unit) }

    /**
     * 지금 폴더의 읽던 쪽 배지(경로 → 배지). 만화·문서만, **주 스레드 밖에서**, [io.github.donggi.iroiroviewer.data.ProgressLookup.MAX_KEYS]
     * 씩 묻는다([ReadingBadges.lookup]). 휴지통 화면은 이것을 보지 않는다.
     */
    val badges: StateFlow<Map<String, ReadingBadge>> =
        combine(
            _held
                .map { h -> h.path to (h.shown as? DirectoryLister.Listing.Ready)?.entries }
                .distinctUntilChanged(),
            progressChanges,
        ) { folder, _ -> folder }
            .mapLatest { (path, entries) ->
                if (entries == null) emptyMap() else lookupBadges(path, entries)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 배지를 찾는다. **실패해도 목록을 죽이지 않는다** — 이 흐름은 `stateIn` 의 공유 코루틴이라 예외가 새면
     * 앱이 내려간다(3·4단계가 `DirectoryIteratorException` 으로 밟은 형태). 취소는 되던진다.
     */
    private suspend fun lookupBadges(path: String?, entries: List<FileEntry>): Map<String, ReadingBadge> = try {
        val db = IroiroDatabase.get(getApplication())
        ReadingBadges.lookup(path, entries, db.comicProgress()::findAll, db.docProgress()::findAll)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Iro.d { "읽던 쪽 배지를 찾지 못했다: ${e::class.java.simpleName}" }
        emptyMap()
    }

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

        /**
         * 폴더 알림을 묶는 시간. 마지막 알림 뒤 0.7초가 조용하면 다시 읽고, 알림이 끊이지 않아도 3초마다는 읽는다
         * ([io.github.donggi.iroiroviewer.io.SettleRule]). 1만 개 폴더를 한 번 읽는 데 0.48초가 든다(3단계 실측) —
         * 그보다 자주 읽으면 읽기가 끝나기 전에 다음 읽기가 앞의 것을 끊는다.
         */
        private const val WATCH_QUIET_MS = 700L
        private const val WATCH_MAX_WAIT_MS = 3_000L

        /** 당긴 표시의 최소 시간. 위 [holdIndicator] 주석 참고. */
        private const val MIN_INDICATOR_MS = 350L

        /** 당긴 표시를 끝없이 붙들지 않는다. 1만 개 폴더도 1초 안이다 — 넉넉히. */
        private const val PULL_TIMEOUT_MS = 30_000L

        /** 휴지통의 맞추기는 큐에서 차례를 기다린다. 긴 복사가 도는 동안 표시만 돌게 두지 않는다. */
        private const val TRASH_PULL_TIMEOUT_MS = 10_000L
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
