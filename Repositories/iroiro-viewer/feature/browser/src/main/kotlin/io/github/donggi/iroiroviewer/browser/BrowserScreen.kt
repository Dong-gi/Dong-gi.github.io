package io.github.donggi.iroiroviewer.browser

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.io.DirectoryLister
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.ShareHelper
import io.github.donggi.iroiroviewer.playback.FolderQueue
import io.github.donggi.iroiroviewer.playback.MiniPlayer
import io.github.donggi.iroiroviewer.playback.PlaybackConnection
import io.github.donggi.iroiroviewer.playback.PlayerSupport
import io.github.donggi.iroiroviewer.playback.SubtitleNames
import io.github.donggi.iroiroviewer.playback.playbackFailureText
import io.github.donggi.iroiroviewer.io.VolumeRegistry
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.ViewMode
import java.io.File
import io.github.donggi.iroiroviewer.io.R as IoR

/**
 * 파일 브라우저.
 *
 * 뒤로가기 우선순위를 **여기 한 곳에서** 정한다: 휴지통 닫기 → 선택 해제 → 이름 필터 →
 * 상위 폴더 → 홈 → 앱 종료. 화면마다 `BackHandler` 를 흩어 두면 어느 것이 먼저 먹는지
 * 아무도 모르게 된다.
 *
 * **작업 결과도 같은 이유로 여기서 한 번만 받는다.** 예전에는 폴더 화면만 결과를
 * 소비해서, 휴지통에서 누른 영구 삭제의 성패가 아무에게도 전해지지 않고 나중에 폴더로
 * 돌아간 순간 엉뚱한 자리에서 뒤늦게 떴다. 되돌릴 수 없는 조작의 결과를 화면이 받지
 * 못하는 구조였다.
 */
@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    onOpenDiagnostics: () -> Unit,
    /** 설정 화면(14단계). `app` 이 연다 — 설정은 `feature:settings` 에 있고 feature 끼리는 서로를 보지 않는다. */
    onOpenSettings: () -> Unit = {},
    onOpenImage: (List<FileEntry>, Int) -> Unit,
    onOpenText: (FileEntry) -> Unit,
    onOpenArchive: (FileEntry) -> Unit,
    /** 만화로 연다. 파일(cbz·cbr…)일 수도 **폴더**일 수도 있어 경로 하나를 받는다. */
    onOpenComic: (String) -> Unit,
    /** 문서 뷰어로 연다. 11단계는 PDF 하나이고 EPUB 이 같은 자리로 들어온다. */
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    val filtering by vm.filtering.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    // **ViewModel 이 든다.** 사진을 열면 이 화면이 컴포지션에서 통째로 빠지므로
    // `rememberSaveable` 로는 갤러리에 있었다는 사실이 남지 않는다(`subScreen` 의 주석).
    val subScreen by vm.subScreen.collectAsStateWithLifecycle()
    val showTrash = subScreen == BrowserViewModel.SUB_TRASH
    val showGallery = subScreen == BrowserViewModel.SUB_GALLERY
    val path = stack.lastOrNull()

    // **ViewModel 이 든 스낵바**를 쓴다. 이미지 뷰어까지 같은 것을 쓰므로 어느 화면에서
    // 일으킨 작업이든 결과가 보인다.
    //
    // **결과를 여기서 소비하지 않는다.** 소비기가 이 화면의 컴포지션에 묶여 있으면
    // 뷰어가 화면을 차지하는 순간 소비가 멎고, 뷰어에서 누른 삭제의 성패가 아무에게도
    // 전해지지 않는다. 소비는 `app` 의 Root 가 — 모든 화면의 공통 부모가 — 한 곳에서 한다.
    val snackbar = vm.snackbar

    BackHandler(
        enabled = showTrash || showGallery || selection.isNotEmpty() || filtering || stack.isNotEmpty()
    ) {
        when {
            showGallery || showTrash -> vm.closeSubScreen()
            selection.isNotEmpty() -> vm.clearSelection()
            filtering -> vm.setFiltering(false)
            vm.up() -> Unit
            else -> vm.goHome()
        }
    }

    when {
        showGallery -> GalleryScreen(
            vm = vm,
            snackbar = snackbar,
            // 갤러리에서 연 사진의 좌우는 **갤러리 목록 그대로**다. 폴더로 바꿔 열면
            // 시각 순으로 훑던 흐름이 끊긴다.
            onOpen = onOpenImage,
            onBack = { vm.closeSubScreen() },
            modifier = modifier,
        )
        showTrash -> TrashScreen(vm = vm, snackbar = snackbar, onBack = { vm.closeSubScreen() }, modifier = modifier)
        path == null -> HomeScreen(
            vm = vm,
            snackbar = snackbar,
            onOpenDiagnostics = onOpenDiagnostics,
            onOpenSettings = onOpenSettings,
            onOpenTrash = { vm.openTrash() },
            onOpenGallery = { vm.openGallery() },
            modifier = modifier,
        )
        else -> FolderScreen(
            vm = vm,
            path = path,
            stack = stack,
            snackbar = snackbar,
            onOpenImage = onOpenImage,
            onOpenText = onOpenText,
            onOpenArchive = onOpenArchive,
            onOpenComic = onOpenComic,
            onOpenDocument = onOpenDocument,
            modifier = modifier,
        )
    }
}

/**
 * 작업 결과 한 줄.
 *
 * 컴포저블이 아니라 평범한 함수다 — 결과는 채널에서 오므로 효과 안에서 문구를 만들어야
 * 하고, 거기서는 `stringResource` 를 부를 수 없다. 문구 자체는 여전히 `strings.xml` 에 있다.
 */
fun opResultMessage(context: android.content.Context, f: FileOpManager.Finished): String =
    when (val o = f.outcome) {
        is FileOpEngine.Outcome.Cancelled -> context.getString(R.string.browser_op_cancelled)
        is FileOpEngine.Outcome.Failed -> {
            val why = context.getString(reasonStringId(o.reason))
            // 실패해도 **여기까지 한 것**을 함께 말한다. 그것이 없으면 사용자는 절반이
            // 이미 옮겨진 것을 모른 채 같은 작업을 다시 건다.
            if (o.done > 0) context.getString(R.string.browser_op_failed_partial, why, o.done) else why
        }
        is FileOpEngine.Outcome.Done ->
            if (o.skipped == 0 && o.failed == 0) context.getString(R.string.browser_op_done, o.moved)
            else context.getString(R.string.browser_op_done_with_skips, o.moved, o.skipped, o.failed)
    } + refusedSuffix(context, f)

/**
 * 풀기가 **거부한 것**을 결과 한 줄에 덧붙인다.
 *
 * 조용히 삼키면 사용자는 아카이브 안의 파일 수와 푼 파일 수가 다른 것을 보고도 이유를
 * 알 수 없다. 아카이브가 적은 이름은 **여기에 끼워 넣지 않는다** — 우리 문구인 척하게 된다.
 */
private fun refusedSuffix(context: android.content.Context, f: FileOpManager.Finished): String {
    val report = (f.extra as? FileOpManager.Extra.Extracted)?.report ?: return ""
    if (report.refusedTotal <= 0) return ""
    return context.getString(R.string.browser_op_refused, report.refusedTotal)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderScreen(
    vm: BrowserViewModel,
    path: String,
    stack: List<String>,
    snackbar: SnackbarHostState,
    onOpenImage: (List<FileEntry>, Int) -> Unit,
    onOpenText: (FileEntry) -> Unit,
    onOpenArchive: (FileEntry) -> Unit,
    onOpenComic: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sortSpec by vm.sortSpec.collectAsStateWithLifecycle()
    val showHidden by vm.showHidden.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val filtering by vm.filtering.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val opState by vm.opState.collectAsStateWithLifecycle()
    val conflicts by vm.pendingConflicts.collectAsStateWithLifecycle()
    val lastError by vm.lastError.collectAsStateWithLifecycle()
    val bookmarked by vm.currentBookmarked.collectAsStateWithLifecycle()
    val pullRefreshing by vm.pullRefreshing.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val restorePick by vm.restorePick.collectAsStateWithLifecycle()
    val restoreConflicts by vm.pendingRestoreConflicts.collectAsStateWithLifecycle()

    // **보이는 동안만 폴더를 듣는다**(B2). 이 효과는 컴포지션이 아니라 수명 주기의 알림으로 돈다 — 화면이 멈추면
    // 재구성이 멎어도(함정 표) `onStop` 은 온다. 뷰어가 이 화면을 대신하면 컴포지션에서 빠지면서 똑같이 꺼지고,
    // 돌아오면 켜지면서 떠나 있던 동안의 변화를 수정 시각으로 한 번 본다. 폴더가 바뀌는 것은 VM 이 따라간다.
    LifecycleStartEffect(Unit) {
        vm.onFolderShown()
        onStopOrDispose { vm.onFolderHidden() }
    }

    // **`context.getString` 을 컴포저블 안에서 부르지 않는다.** 그 값은 구성 변경을 따라가지 않는다
    // (lint `LocalContextGetResourceValueCall` 이 오류로 잡는다 — `app` 의 Root 가 같은 까닭으로 고쳤다).
    val shareTitle = stringResource(R.string.browser_action_share)

    var detail by remember { mutableStateOf<DetailRequest?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 이 앱이 뷰어를 갖지 않은 파일은 다른 앱으로 연다. 못 열었으면 우리 문장을 이 화면의 스낵바로 알린다.
    val openWith = rememberOpenWith(snackbar)
    // 선택 메뉴의 '다른 앱으로 열기'·'정보' 가 겨눌 것 — 파일 하나를 골랐을 때만 있다. 한 개 고른 동안 1만 줄을
    // 재구성마다 훑지 않도록 선택과 목록이 바뀔 때만 다시 찾는다.
    val singleFile = remember(selection, visible) {
        OpenWithRules.singleFile(selection, (visible as? VisibleState.Ready)?.entries.orEmpty())
    }

    // 작업 결과는 BrowserScreen 이 한 곳에서 받는다. 여기서는 이 화면에서만 나는
    // 오류(이름 바꾸기·새 폴더)만 다룬다.
    val errorMessage = lastError?.let { reasonText(it) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // **보여 준 뒤에 소비한다.** 먼저 소비하면 키(errorMessage)가 null 로 바뀌어
            // LaunchedEffect 가 재시작되면서 showSnackbar 가 취소된다 — 오류가 화면에
            // 한 번도 안 뜨는 이유가 이것이었다.
            snackbar.showSnackbar(errorMessage)
            vm.consumeError()
        }
    }

    // **재생 실패만 여기서 말한다 — 목록이 맨 앞일 때만.** 재생 화면이 위에 떠 있는 동안 말하고 지우면 그 화면의 문구와
    // '다른 앱으로 열기' 가 몇 초 뒤 사라진다(`PlaybackNotice`). 이어보기 제안의 자리는 아래 주석 참고.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playback by PlaybackConnection.state.collectAsStateWithLifecycle(minActiveState = PlaybackNotice.IN_FRONT)
    val playbackFailure = playback.failure
    val playbackMessage = playbackFailure?.let { playbackFailureText(it) }
    LaunchedEffect(playbackFailure, playbackMessage, lifecycle) {
        if (playbackFailure != null && playbackMessage != null) {
            PlaybackNotice.tellOnce(
                lifecycle,
                // 떠나 있는 동안 지워졌으면(재생 화면에서 다른 곡을 틀었다) 옛 문구를 띄우지 않고, 그새 다른 실패로 바뀌었으면
                // 그것을 지우지 않는다. 말하기 전과 지우기 전에 커넥션의 지금 값을 본다.
                pending = { PlaybackConnection.state.value.failure == playbackFailure },
                show = { snackbar.showSnackbar(playbackMessage) },
                consume = PlaybackConnection::consumeFailure,
            )
        }
    }
    // **이어보기 안내를 여기서 그리지 않는다.**
    //
    // 예전에는 이 자리에 스낵바가 있었는데 둘 다 틀렸다. 하나는 **자리**다 — 파일 목록은
    // 재생을 보는 화면이 아니라서, 사용자는 목록을 넘기는 내내 재생 이야기를 읽고 있어야
    // 했다. 다른 하나는 **수명**이다 — `showSnackbar` 에 `actionLabel` 을 주면 기간이
    // `Indefinite` 가 되므로(Material 3 의 기본값) 누르거나 밀기 전에는 사라지지 않았다.
    // 사용자가 "제한시간 없이 계속 노출된다" 고 지적한 것이 그것이다.
    //
    // 지금은 재생 화면·재생목록에서만 3초간 묻는다(`PlayerScreen`).

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                if (selection.isNotEmpty()) {
                    SelectionTopAppBar(
                        count = selection.size,
                        onClear = vm::clearSelection,
                        onSelectAll = {
                            (visible as? VisibleState.Ready)?.let { ready ->
                                vm.selectAll(ready.entries.map { it.path })
                            }
                        },
                        // **'다른 앱으로 열기' 는 언제나 고르게 한다**(Mode.CHOOSE) — 사용자가 고르겠다고 말한 자리다.
                        // 띄웠으면 공유처럼 선택을 끝낸다. 못 띄웠으면 선택을 남긴다(공유 같은 다른 길을 고를 수 있게).
                        onOpenWith = singleFile?.let { entry ->
                            {
                                if (openWith(entry, ExternalOpen.Mode.CHOOSE) == ExternalOpen.Result.STARTED) {
                                    vm.clearSelection()
                                }
                            }
                        },
                        // 누르면 다른 앱으로 가는 파일(APK·모르는 형식)은 이 길이 아니면 정보를 볼 수 없다.
                        // 선택은 남긴다 — 시트는 위에 잠깐 뜨는 것이고, 닫으면 고르던 자리로 돌아온다.
                        onInfo = singleFile?.let { entry -> { detail = DetailRequest(entry) } },
                    )
                } else {
                    BrowserAppBar(
                        title = displayName(path, volumes),
                        bookmarked = bookmarked,
                        onToggleBookmark = vm::toggleBookmark,
                        filtering = filtering,
                        filter = filter,
                        viewMode = viewMode,
                        sortSpec = sortSpec,
                        showHidden = showHidden,
                        onUp = { if (!vm.up()) vm.goHome() },
                        onFilterChange = vm::setFilter,
                        onFilteringChange = vm::setFiltering,
                        onToggleViewMode = { vm.toggleViewMode() },
                        onSortBy = { vm.sortBy(it) },
                        onToggleFoldersFirst = { vm.toggleFoldersFirst() },
                        onToggleHidden = { vm.toggleHidden() },
                        // 폴더 만화. 이 폴더에 그림이 있을 때만 켠다 — 눌러 봤자
                        // '그림이 한 장도 없습니다' 로 끝나는 메뉴를 보여 주지 않는다.
                        canReadAsComic = (visible as? VisibleState.Ready)
                            ?.entries?.any { it.kind == FileKind.IMAGE } == true,
                        onReadAsComic = { onOpenComic(path) },
                        onRefresh = vm::pullToRefresh,
                    )
                }
                StatusLine(visible = visible, filtering = filtering)
                Breadcrumb(
                    stack = stack,
                    label = { displayName(it, volumes) },
                    onJump = vm::jumpTo,
                    onHome = vm::goHome,
                )
                HorizontalDivider()
            }
        },
        bottomBar = {
            Column {
                // 재생 바가 가장 위. 파일을 탐색하는 내내 보이면서, 화면을 나가도
                // 재생이 이어진다는 것을 눈으로 알려 주는 자리다.
                MiniPlayer()
                (opState as? FileOpManager.State.Running)?.let {
                    OperationBar(state = it, onCancel = vm::cancelOperation)
                }
                if (selection.isNotEmpty()) {
                    SelectionActionBar(
                        count = selection.size,
                        onCopy = { vm.cutOrCopy(move = false) },
                        onMove = { vm.cutOrCopy(move = true) },
                        onDelete = { confirmDelete = true },
                        onRename = { renaming = selection.firstOrNull() },
                        onShare = {
                            ShareHelper.share(
                                context,
                                selection.toList(),
                                shareTitle,
                            )
                            vm.clearSelection()
                        },
                    )
                } else {
                    restorePick?.let {
                        RestorePickBar(
                            pick = it,
                            onRestoreHere = vm::requestRestoreHere,
                            onCancel = vm::cancelRestorePick,
                        )
                    }
                    clipboard?.let {
                        PasteBar(
                            clipboard = it,
                            onPaste = vm::requestPaste,
                            onCancel = vm::clearClipboard,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selection.isEmpty() && clipboard == null && restorePick == null) {
                // **단추 둘을 세로로 쌓는다.** 자리를 다투는 대신 쌓는 것이 Material 의
                // 관행이고, 무엇보다 **둘 다 언제나 보인다** — 자리를 교대하면 사용자가
                // 방금 있던 단추를 찾아 헤맨다.
                //
                // **크기를 같게 한다.** 위를 작은 FAB 으로 두었더니 둘이 들쭉날쭉해 보였다
                // (사용자가 지적했다). 둘 다 같은 격의 동작이라 격을 나눌 이유가 없다.
                //
                // 섞기·반복은 여기 두지 않는다. FAB 을 길게 눌러야 나오는 기능은
                // 발견될 수단이 없다 — 그 둘은 재생 화면의 단추다.
                val playable = (visible as? VisibleState.Ready)?.entries.orEmpty()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (FolderQueue.hasPlayable(playable)) {
                        FloatingActionButton(
                            onClick = {
                                PlaybackConnection.playQueue(
                                    context,
                                    FolderQueue.plan(playable, null),
                                    playable.filter { SubtitleNames.isSubtitle(it.name) },
                                )
                                // **재생목록을 곧바로 보여 준다.** ▶ 는 '이 폴더를 튼다' 가
                                // 아니라 '이 폴더를 재생목록으로 연다' 이다 — 무엇이 큐에
                                // 들었고 지금 몇 번째인지 보이지 않으면 다음/이전으로만
                                // 움직이게 되고, 18곡에서 16번째로 가려면 열다섯 번을 누른다.
                                PlayerSupport.open(context, showPlaylist = true)
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                stringResource(R.string.browser_play_folder),
                            )
                        }
                    }
                    FloatingActionButton(onClick = { newFolder = true }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.browser_action_new_folder))
                    }
                }
            }
        },
    ) { inner ->
        // **당겨서 새로고침**(사용자 요청). 목록을 비우지 않는다 — 다시 읽는 동안 옛 목록이 그대로 있고
        // (`ListingHold`) 표시만 위에 뜬다. **고르는 중에는 끈다** — 다시 읽혀 줄이 움직이면 다른 줄을 누르게 된다.
        RefreshableBox(
            refreshing = pullRefreshing,
            onRefresh = vm::pullToRefresh,
            enabled = selection.isEmpty(),
            modifier = Modifier.padding(inner).fillMaxSize(),
        ) {
            when (val s = visible) {
                is VisibleState.Scanning -> CenteredNote(stringResource(R.string.browser_scanning, s.count))

                // 안내 화면도 당길 수 있어야 한다 — SD 를 다시 꽂았거나 다른 앱이 폴더를 되살렸을 때 다시 읽는 길이다.
                is VisibleState.Failed -> ScrollableNote { FailureNote(s.reason) }

                is VisibleState.Ready -> {
                    if (s.entries.isEmpty()) {
                        ScrollableNote {
                            CenteredNote(
                                stringResource(
                                    if (filter.isBlank()) R.string.browser_empty
                                    else R.string.browser_empty_filtered
                                )
                            )
                        }
                    } else {
                        key(path, viewMode) {
                            FileListing(
                                path = path,
                                entries = s.entries,
                                viewMode = viewMode,
                                selection = selection,
                                badges = badges,
                                vm = vm,
                                onOpen = { e ->
                                    // 어디로 가는지는 `TapRoute` 가 종류마다 정한다 — `else` 로 떨어지는 값이 없어서,
                                    // 종류가 늘면 컴파일이 멈추고 시험이 모든 종류를 표로 지킨다.
                                    //
                                    // **방금 다른 앱을 띄웠으면 그 창이 뜨기 전에 온 누름은 흘려보낸다**
                                    // (`OpenWithRules.SETTLE_MS`) — 두 번 누른 APK 가 설치 관리자를 두 겹 띄우거나,
                                    // 시트·메뉴의 '다른 앱으로 열기' 를 두 번 누른 것이 그 아래 줄을 여는 일을 막는다.
                                    if (openWith.settling()) Unit
                                    else if (selection.isNotEmpty()) vm.toggleSelection(e.path)
                                    else when (TapRoute.of(e)) {
                                        TapRoute.FOLDER -> vm.open(e.path)
                                        // 미디어는 곧바로 튼다. 재생은 서비스에서 도므로
                                        // 이 화면을 떠나도 이어진다.
                                        //
                                        // **누른 하나가 아니라 폴더를 큐로 만든다**(10단계).
                                        // 그래야 알림과 재생 화면의 '다음/이전' 이 뜻을 갖는다.
                                        // 목록은 **지금 보이는 것 그대로**다 — 걸러 놓았으면
                                        // 걸러진 것만 듣는다. 사진 뷰어가 같은 규칙이다.
                                        //
                                        // 영상은 재생 화면까지 연다. 소리만 나는 파일은 미니
                                        // 바로 충분하지만, 영상을 탭해 놓고 목록에 남아 소리만
                                        // 나는 것은 아무도 기대하지 않는다.
                                        TapRoute.PLAYER -> {
                                            PlaybackConnection.playQueue(
                                                context,
                                                FolderQueue.plan(s.entries, e.path),
                                                // 자막 후보는 **지금 목록에 보이는 것**에서
                                                // 고른다. 재생을 시작하려고 폴더를 다시
                                                // 나열하지 않는다 — 그 값이 1만 개 폴더에서
                                                // 394ms 다(3단계 실측).
                                                s.entries.filter { SubtitleNames.isSubtitle(it.name) },
                                            )
                                            // **영상이든 소리든 재생 화면으로 간다.**
                                            //
                                            // 예전에는 영상만 열었다. 그러면 소리 파일은
                                            // 미니 바만 뜨는데, 이어보기 제안이 재생
                                            // 화면·재생목록에만 있으므로 **소리에는 그
                                            // 제안이 닿지 않았다**(사용자가 지적했다).
                                            //
                                            // 소리는 목록을 펼친 채로 연다 — 그 화면에는
                                            // 볼 것이 제목 한 줄뿐이라 목록이 가릴 것이
                                            // 없고, 오히려 그것이 본체다. 영상은 반대라
                                            // false 다(`PlayerLauncher.open` 의 주석).
                                            PlayerSupport.open(
                                                context,
                                                showPlaylist = e.kind == FileKind.AUDIO,
                                            )
                                        }
                                        // 사진은 뷰어로. 좌우로 넘길 목록은 **지금 보고 있는
                                        // 이 폴더의 이미지들**이다 — 눈에 보이는 순서 그대로.
                                        TapRoute.IMAGE -> {
                                            val images = s.entries.filter { it.kind == FileKind.IMAGE }
                                            val at = images.indexOfFirst { it.path == e.path }
                                            if (at >= 0) onOpenImage(images, at) else detail = DetailRequest(e)
                                        }
                                        // 글은 뷰어로. 판정이 틀리면(바이너리를 .txt 로
                                        // 둔 파일) 뷰어가 그 자리에서 말한다 — 목록이
                                        // 파일을 열어 보고 정하는 일은 하지 않는다.
                                        TapRoute.TEXT -> onOpenText(e)
                                        // **확장자로 만화라고 말한 것만** 만화로 연다.
                                        // 일반 압축은 목록 화면으로 간다 — 그 안에 그림이
                                        // 있어도 사용자가 '이것은 만화다' 라고 말한 적이 없다.
                                        // (압축 화면의 그림 항목을 탭하면 그때 만화로 연다.)
                                        TapRoute.COMIC -> onOpenComic(e.path)
                                        TapRoute.ARCHIVE -> onOpenArchive(e)
                                        // 문서는 문서 뷰어로. PDF·EPUB(11단계), 오피스 문서
                                        // (DOCUMENT·SHEET·SLIDE, 12단계), 한글(HWP, 13단계).
                                        //
                                        // 오피스 쪽도 통째로 보낸다 — `.doc`·`.odt` 처럼 우리가 못
                                        // 여는 것이 섞여 있지만 뷰어가 '이전 형식입니다'·'다루지
                                        // 않는 문서입니다' 로 정확히 말한다(아래 EBOOK 과 같은 판단).
                                        //
                                        // EBOOK 을 통째로 보내는 것은 그 안에 `.mobi` 처럼
                                        // 우리가 못 여는 것이 섞여 있어도 **뷰어가 '이 앱이
                                        // 다루지 않는 문서입니다' 로 정확히 말하기** 때문이다.
                                        // 목록이 확장자로 미리 거르면 그 판정이 두 곳으로
                                        // 갈리고, 그것은 이 저장소가 여러 번 겪은 형태다.
                                        TapRoute.DOCUMENT -> onOpenDocument(e.path)
                                        // **이 앱에 뷰어가 없는 파일은 다른 앱으로 연다**(APK 는 설치 관리자).
                                        // 누른 그대로 여는 길이라 Mode.VIEW 다 — 기본 앱이 있으면 묻지 않는다.
                                        // 못 열었으면(이 형식을 아는 앱이 없다·넘길 수 없는 자리) 예전처럼 정보
                                        // 시트를 띄우고 까닭을 거기에도 적는다(`DetailRequest.notice`). 앞의 것이면
                                        // 시트의 '다른 앱으로 열기' 가 모든 앱에서 고르는 창으로 간다.
                                        TapRoute.EXTERNAL ->
                                            OpenWithRules.afterTap(e, openWith(e, ExternalOpen.Mode.VIEW))
                                                ?.let { detail = it }
                                        TapRoute.DETAIL -> detail = DetailRequest(e)
                                    }
                                },
                                onLongPress = { vm.toggleSelection(it.path) },
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { shown ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailSheet(
                entry = shown.entry,
                notice = shown.notice,
                // **언제나 고르게 한다**(Mode.CHOOSE). 기본 앱이 정해져 있어도 묻는다 — '다른 앱으로' 를 누른 사람은
                // 방금 기본 앱이 아닌 것을 원한다고 말했다. 폴더·휴지통 안의 것에는 단추가 없다.
                // 눌러서 '이 형식을 아는 앱이 없습니다' 로 뜬 시트라면 이 단추가 **그래도 열어 보는 길**이다(길게 눌러 고른
                // 뒤의 ⋮ 말고는 없다) — 받는 앱이 없는 형식이라 `ExternalOpen` 이 모든 앱에서 고르는 창으로 넓힌다. 그래서
                // 눌러서 다른 앱으로 가는 종류는 모두 이 단추를 갖는다(`OpenWithTest` 가 박는다).
                onOpenWith = if (OpenWithRules.canHandOff(shown.entry)) {
                    {
                        val result = openWith(shown.entry, ExternalOpen.Mode.CHOOSE)
                        // 선택 메뉴의 '정보' 에서 왔으면 선택 메뉴의 '다른 앱으로 열기' 와 똑같이 끝낸다 — 띄웠으면
                        // 선택도 끝난다. 눌러서 뜬 시트라면 고른 것이 없어 아무 일도 없다.
                        if (result == ExternalOpen.Result.STARTED) vm.clearSelection()
                        detail = OpenWithRules.afterChoose(shown, result)
                    }
                } else {
                    null
                },
            )
        }
    }

    if (newFolder) {
        NameDialog(
            title = stringResource(R.string.browser_dialog_new_folder),
            label = stringResource(R.string.browser_dialog_folder_name),
            initial = "",
            confirmLabel = stringResource(R.string.browser_dialog_create),
            selectBaseName = false,
            onConfirm = { vm.createFolder(it); newFolder = false },
            onDismiss = { newFolder = false },
        )
    }

    renaming?.let { target ->
        NameDialog(
            title = stringResource(R.string.browser_dialog_rename),
            label = stringResource(R.string.browser_dialog_name),
            initial = File(target).name,
            confirmLabel = stringResource(R.string.browser_dialog_ok),
            selectBaseName = true,
            onConfirm = { vm.rename(target, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    if (confirmDelete) {
        DeleteConfirmDialog(
            count = selection.size,
            onConfirm = { vm.trashSelected(); confirmDelete = false },
            onDismiss = { confirmDelete = false },
        )
    }

    if (!conflicts.isEmpty) {
        ConflictDialog(conflicts = conflicts, onPick = vm::paste, onDismiss = vm::cancelPaste)
    }

    // '여기에 복원' 의 이름 충돌. 붙여넣기와 같은 대화상자·같은 규칙이다(덮어쓰기는 종류가 같을 때만).
    if (!restoreConflicts.isEmpty) {
        ConflictDialog(conflicts = restoreConflicts, onPick = vm::restoreHere, onDismiss = vm::cancelRestoreConflicts)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserAppBar(
    title: String,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    filtering: Boolean,
    filter: String,
    viewMode: ViewMode,
    sortSpec: io.github.donggi.iroiroviewer.model.SortSpec,
    showHidden: Boolean,
    onUp: () -> Unit,
    onFilterChange: (String) -> Unit,
    onFilteringChange: (Boolean) -> Unit,
    onToggleViewMode: () -> Unit,
    onSortBy: (SortKey) -> Unit,
    onToggleFoldersFirst: () -> Unit,
    onToggleHidden: () -> Unit,
    canReadAsComic: Boolean,
    onReadAsComic: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_up))
            }
        },
        title = {
            if (filtering) {
                // 앱바가 입력 필드로 변한다. 별도 화면을 띄우면 목록이 사라져서
                // '거르는 중' 이라는 감각이 끊긴다.
                val focus = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    focus.requestFocus()
                    keyboard?.show()
                }
                TextField(
                    value = filter,
                    onValueChange = onFilterChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.browser_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            } else {
                Text(text = title, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        },
        actions = {
            if (filtering) {
                IconButton(onClick = { onFilteringChange(false) }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.browser_search_close))
                }
            } else {
                // 별이 검색보다 앞이다. 지금 폴더를 즐겨찾기에 넣는 것은 '이 폴더에
                // 대한' 조작이고, 나머지는 '보는 방식' 에 대한 조작이다.
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Filled.Star else StarOutlineIcon,
                        contentDescription = stringResource(
                            if (bookmarked) R.string.browser_bookmark_remove else R.string.browser_bookmark_add
                        ),
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onFilteringChange(true) }) {
                    Icon(Icons.Filled.Search, stringResource(R.string.browser_search))
                }
                // 글리프는 손으로 그린 캔버스라 설명이 따로 없다 — 화면 낭독기가 이름 없는 단추로 읽지 않게 단추에 준다.
                val viewModeLabel = stringResource(R.string.browser_view_mode)
                IconButton(
                    onClick = onToggleViewMode,
                    modifier = Modifier.semantics { contentDescription = viewModeLabel },
                ) { ViewModeGlyph(viewMode) }
                // **⋮ 단추와 메뉴를 한 상자에 담는다.** Compose 의 Popup 은 자기를 감싼 **부모
                // 레이아웃 노드**를 앵커로 삼는다. `actions` 에 형제로 두면 앵커가 아이콘 줄
                // 전체가 되어 메뉴가 맨 왼쪽 아이콘 아래에서 시작한다(사용자가 지적했다).
                // `offset` 으로 보정하지 마라 — 화면 폭·글꼴 배율에서 다시 어긋난다.
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.browser_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            stringResource(R.string.browser_sort),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                        SortItem(R.string.browser_sort_name, SortKey.NAME, sortSpec, onSortBy)
                        SortItem(R.string.browser_sort_date, SortKey.DATE, sortSpec, onSortBy)
                        SortItem(R.string.browser_sort_size, SortKey.SIZE, sortSpec, onSortBy)
                        SortItem(R.string.browser_sort_kind, SortKey.KIND, sortSpec, onSortBy)
                        HorizontalDivider()
                        CheckItem(R.string.browser_sort_folders_first, sortSpec.foldersFirst) { onToggleFoldersFirst() }
                        CheckItem(R.string.browser_show_hidden, showHidden) { onToggleHidden() }
                        HorizontalDivider()
                        // **당겨서 새로고침과 같은 일을 메뉴에도 둔다.** 당기기는 몸짓뿐이라 화면 낭독기로는 찾을
                        // 길이 없고(목록 줄에 초점이 있으면 목록을 감싼 상자의 동작은 읽히지 않는다), 손가락을
                        // 쓰기 어려운 사람에게도 없는 것과 같다. 고르는 중에는 이 앱바가 선택 앱바로 바뀌어 닿지 않는다.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_refresh)) },
                            onClick = {
                                menuOpen = false
                                onRefresh()
                            },
                        )
                        if (canReadAsComic) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_read_as_comic)) },
                                onClick = {
                                    menuOpen = false
                                    onReadAsComic()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SortItem(
    labelRes: Int,
    key: SortKey,
    spec: io.github.donggi.iroiroviewer.model.SortSpec,
    onSortBy: (SortKey) -> Unit,
) {
    val selected = spec.key == key
    DropdownMenuItem(
        text = {
            // 방향을 화살표로 보여 준다. 같은 항목을 다시 누르면 뒤집힌다는 것을
            // 메뉴를 닫지 않고도 알 수 있어야 한다.
            Text(stringResource(labelRes) + if (selected) (if (spec.ascending) "  ↑" else "  ↓") else "")
        },
        onClick = { onSortBy(key) },
        trailingIcon = if (selected) {
            { Icon(Icons.Filled.Check, null) }
        } else null,
    )
}

@Composable
private fun CheckItem(labelRes: Int, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = onClick,
        trailingIcon = if (checked) {
            { Icon(Icons.Filled.Check, null) }
        } else null,
    )
}

/** 목록·격자 아이콘. 아이콘 라이브러리에 없어서 직접 그린다. */
@Composable
private fun ViewModeGlyph(mode: ViewMode) {
    val color = MaterialTheme.colorScheme.onSurface
    // **24dp 로 고정한다.** `fillMaxSize` 로 두면 글리프가 48dp 짜리 터치 영역을 통째로
    // 채워, 옆의 24dp 아이콘들보다 눈에 띄게 크고 물결 효과의 둥근 모서리에 잘려 보인다
    // (사용자가 지적했다).
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        if (mode == ViewMode.LIST) {
            val s = w * 0.38f
            val gap = w * 0.08f
            for (r in 0..1) for (c in 0..1) {
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(c * (s + gap), r * (s + gap)),
                    size = androidx.compose.ui.geometry.Size(s, s),
                )
            }
        } else {
            val lh = h * 0.16f
            for (r in 0..2) {
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, r * (lh * 2f)),
                    size = androidx.compose.ui.geometry.Size(w, lh),
                )
            }
        }
    }
}

/**
 * 빵부스러기. 볼륨 아래로 몇 단계든 들어갈 수 있으므로 가로 스크롤이 필요하다.
 * 마지막 조각(현재 폴더)은 누를 수 없다 — 이미 거기 있다.
 */
@Composable
private fun Breadcrumb(
    stack: List<String>,
    label: (String) -> String,
    onJump: (Int) -> Unit,
    onHome: () -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(stack.size) { scroll.animateScrollTo(scroll.maxValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.browser_home_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onHome).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        stack.forEachIndexed { i, p ->
            Text("›", color = MaterialTheme.colorScheme.outline)
            val last = i == stack.lastIndex
            Text(
                text = label(p),
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .then(if (last) Modifier else Modifier.clickable { onJump(i) })
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 개수 줄. 필터를 걸면 '몇 개 중 몇 개' 가 되어야 한다 — 그냥 줄어든 목록만 보이면
 * 거른 것인지 원래 그만큼인지 알 수 없다.
 */
@Composable
private fun StatusLine(visible: VisibleState, filtering: Boolean) {
    val text = when (visible) {
        is VisibleState.Scanning -> stringResource(R.string.browser_scanning, visible.count)
        is VisibleState.Failed -> ""
        is VisibleState.Ready -> buildString {
            append(
                if (filtering && visible.entries.size != visible.totalCount) {
                    stringResource(R.string.browser_count_filtered, visible.entries.size, visible.totalCount)
                } else {
                    stringResource(R.string.browser_count, visible.entries.size)
                }
            )
            if (visible.hiddenCount > 0) {
                append(" · ")
                append(stringResource(R.string.browser_hidden_count, visible.hiddenCount))
            }
        }
    }
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
    )
}

/**
 * 목록 본체.
 *
 * `key` 와 `contentType` 을 주는 것이 큰 폴더에서의 스크롤 성능을 가른다 — key 가 없으면
 * 목록이 바뀔 때마다 모든 항목이 새로 구성되고, contentType 이 없으면 재활용이 종류를
 * 넘나들며 일어나 매번 다시 측정한다.
 */
@Composable
private fun FileListing(
    path: String,
    entries: List<FileEntry>,
    viewMode: ViewMode,
    selection: Set<String>,
    badges: Map<String, ReadingBadge>,
    vm: BrowserViewModel,
    onOpen: (FileEntry) -> Unit,
    onLongPress: (FileEntry) -> Unit,
) {
    val saved = remember(path) { vm.scrollOf(path) }

    if (viewMode == ViewMode.LIST) {
        val state = rememberLazyListState()
        var restored by remember(path) { mutableStateOf(false) }
        LaunchedEffect(entries.isNotEmpty()) {
            // 항목이 도착한 뒤에 복원해야 한다. 비어 있을 때 부르면 0 으로 잘린다.
            if (!restored && entries.isNotEmpty()) {
                state.scrollToItem(saved.first.coerceAtMost(entries.lastIndex), saved.second)
                restored = true
            }
        }
        LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
            if (restored) {
                vm.rememberScroll(path, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(items = entries, key = { it.path }, contentType = { it.kind }) { entry ->
                FileRow(
                    entry = entry,
                    selected = entry.path in selection,
                    onClick = { onOpen(entry) },
                    onLongClick = { onLongPress(entry) },
                    allowLoad = !state.isScrollInProgress,
                    badge = badges[entry.path],
                )
            }
        }
    } else {
        val state = rememberLazyGridState()
        var restored by remember(path) { mutableStateOf(false) }
        LaunchedEffect(entries.isNotEmpty()) {
            if (!restored && entries.isNotEmpty()) {
                state.scrollToItem(saved.first.coerceAtMost(entries.lastIndex), saved.second)
                restored = true
            }
        }
        LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
            if (restored) {
                vm.rememberScroll(path, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            state = state,
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(items = entries, key = { it.path }, contentType = { it.kind }) { entry ->
                FileGridItem(
                    entry = entry,
                    selected = entry.path in selection,
                    onClick = { onOpen(entry) },
                    onLongClick = { onLongPress(entry) },
                    // **스크롤 중에는 새 썸네일을 만들지 않는다.** 썸네일 디스패처는
                    // 선입선출이라, 빠르게 훑는 동안 계속 요청을 넣으면 이미 지나간 칸이
                    // 줄 앞을 차지해 정작 멈춘 자리가 늦게 온다. 취소를 잘 거는 것보다
                    // 애초에 줄을 세우지 않는 편이 싸다.
                    allowLoad = !state.isScrollInProgress,
                    badge = badges[entry.path],
                )
            }
        }
    }
}

/**
 * 화면에 적을 이름.
 *
 * 볼륨 루트에서 경로의 마지막 조각을 쓰면 `/storage/emulated/0` 이 `0` 으로 나온다.
 * 사용자가 아는 이름은 '내부 저장소' 이지 `0` 이 아니다.
 */
private fun displayName(path: String, volumes: List<VolumeRegistry.Volume>): String {
    volumes.firstOrNull { it.path == path }?.let { return it.label }
    return File(path).name.ifEmpty { path }
}

@Composable
private fun CenteredNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 못 읽는 폴더에 **빈 화면을 보여주지 않는다.** 왜 비었는지를 말해 주지 않으면
 * 사용자는 앱이 고장 났다고 생각한다.
 */
@Composable
private fun FailureNote(reason: DirectoryLister.Reason) {
    val (titleRes, bodyRes) = when (reason) {
        DirectoryLister.Reason.LOCKED ->
            R.string.browser_failed_locked_title to R.string.browser_failed_locked_body
        DirectoryLister.Reason.NOT_FOUND ->
            R.string.browser_failed_not_found_title to R.string.browser_failed_not_found_body
        DirectoryLister.Reason.NOT_A_DIRECTORY ->
            R.string.browser_failed_not_dir_title to R.string.browser_failed_not_dir_body
        DirectoryLister.Reason.IO ->
            R.string.browser_failed_io_title to R.string.browser_failed_io_body
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * 파일의 정보. 뷰어가 없는 파일을 눌렀는데 다른 앱으로 넘기지 못했을 때와 선택 메뉴의 '정보' 에서 뜬다. 드물게는 누른
 * 것만으로도 뜬다 — 목록에서 사라진 사진, 폴더라고 적혔는데 폴더가 아닌 항목, 휴지통 안의 뷰어 없는 파일(`TapRoute.DETAIL`).
 *
 * @param notice 다른 앱으로 넘기지 못한 까닭(`ExternalOpen.messageOf`). 이 시트가 화면 아래를 덮어 같은 순간의 스낵바가
 *   가려지므로 여기에도 적는다. 문장은 `core:io` 의 것을 그대로 쓴다 — 한 벌만 둔다(함정 표 '문구는 그것을 만들어 내는
 *   타입 곁에'). **화면 낭독기가 바뀐 까닭을 읽도록 알림 영역으로 둔다** — 시트의 단추를 눌러 실패하면 초점은 단추에 남고
 *   글만 바뀌므로, 알림 영역이 아니면 듣는 사람은 무엇이 일어났는지 모른다.
 * @param onOpenWith '다른 앱으로 열기'. null 이면 단추가 없다(폴더·휴지통 안).
 */
@Composable
private fun DetailSheet(entry: FileEntry, @StringRes notice: Int?, onOpenWith: (() -> Unit)?) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        Text(entry.name, style = MaterialTheme.typography.titleMedium)
        if (notice != null) {
            Text(
                stringResource(notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        DetailRow(stringResource(R.string.browser_detail_kind), kindLabel(entry.kind))
        if (!entry.isDirectory) {
            DetailRow(
                stringResource(R.string.browser_detail_size),
                io.github.donggi.iroiroviewer.io.Format.size(entry.size),
            )
        }
        DetailRow(
            stringResource(R.string.browser_detail_modified),
            io.github.donggi.iroiroviewer.io.Format.timestamp(entry.lastModified),
        )
        DetailRow(stringResource(R.string.browser_detail_path), entry.path)
        if (onOpenWith != null) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onOpenWith) { Text(stringResource(IoR.string.io_open_with)) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
