package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.GalleryScanner
import io.github.donggi.iroiroviewer.io.ShareHelper
import io.github.donggi.iroiroviewer.model.DayBucket
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.GalleryAlbums
import io.github.donggi.iroiroviewer.model.GalleryRoots
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.model.PathNames
import io.github.donggi.iroiroviewer.ui.ThumbnailStore
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 갤러리. 잘 알려진 사진 폴더를 훑어 격자로 보여 준다.
 *
 * ## 왜 격자가 폴더 목록과 따로 있는가
 *
 * 파일 관리자의 격자는 '이 폴더 안' 을 보여 주지만, 사진을 찾을 때 사용자는 폴더를
 * 생각하지 않는다. DCIM 아래에 카메라·스크린샷·다운로드가 흩어져 있어도 머릿속에서는
 * 하나의 '사진' 이다. 그래서 여러 뿌리를 한 격자에 시각 순으로 합친다.
 *
 * **보기가 둘이다** — 날짜(찍은 날로 묶은 격자)와 앨범(사진·영상이 든 폴더마다 한 칸 — [GalleryAlbums]). 앨범을 누르면 그 폴더의 격자가
 * 날짜 묶음 그대로 선다. 앨범은 같은 훑기의 결과를 폴더로 묶은 것이다(다시 훑지 않는다). 보기와 연 앨범은 ViewModel 이 든다 — 사진을
 * 열면 이 화면이 컴포지션에서 빠진다.
 *
 * **길게 눌러 여럿 고른다**(파일 목록과 같은 관행 — 고르는 동안 누르면 더하고 뺀다, '전체 선택' 은 다시 누르면 모두 푼다). 고른 것으로 공유·
 * 이동·삭제, ⋮ 의 ZIP으로 묶어 보내기·위치 지우고 공유(사본)·원본에서 위치 지우기(묻는다). 모두 파일 작업 큐를 지난다(공유만 빼고 —
 * 원본을 그대로 넘긴다). 싣는 차례는 격자의 차례다([GalleryRules.ordered]). 공유·위치 지우고 공유는 [GalleryRules.SHARE_LIMIT] 개까지 —
 * 넘으면 'ZIP으로 묶어 보내기' 를 권한다. 공유 창을 짓는 일(파일마다 보기)은 주 스레드 밖에서 한다.
 *
 * 썸네일은 [ThumbnailStore] 가 만들고 다시 쓴다. **갤러리는 썸네일 통을 청소하지 않는다**(사진·영상만 골라 훑으므로 자격이 없다 — 그 폴더를
 * 폴더 목록으로 열 때와 총량 상한이 걷는다). 이 앱의 작업(지우기·옮기기·위치 지우기)은 끝날 때 그 열쇠를 지운다(`onOperationFinished`). 그래서
 * **`Android/media` 의 사진은 디스크에 남기지 않는다**(`persist=false` — 메신저가 지운 사진의 축소본이 앱 저장소에 남지 않게).
 *
 * **영상도 같은 격자에 든다**(▶ 를 얹는다). 누르면 재생 화면으로 연다 — 사진 뷰어에 넘기는 목록에는 **사진만** 담는다. 섞어 넘기면
 * 뷰어가 영상을 그림으로 디코딩하려다 빈 쪽이 되고, 좌우로 넘기던 흐름이 거기서 끊긴다. 앨범 안에서 연 사진의 좌우는 그 앨범의 사진이다.
 *
 * **당기면 다시 훑는다**(`BrowserViewModel.pullGallery`). 훑는 동안 격자는 앞의 것을 그대로 보인다 — 다른 앱이
 * 방금 찍은 사진을 넣었을 때 갤러리를 나갔다 들어오지 않아도 되는 길이다. 고르는 중에는 끈다(줄이 움직이면 다른 칸을 누르게 된다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    vm: BrowserViewModel,
    snackbar: SnackbarHostState,
    onOpen: (List<FileEntry>, Int) -> Unit,
    onPlay: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareFailed = stringResource(R.string.browser_share_failed)
    val scan by vm.gallery.collectAsStateWithLifecycle()
    val refreshing by vm.galleryRefreshing.collectAsStateWithLifecycle()
    val view by vm.galleryView.collectAsStateWithLifecycle()
    val album by vm.galleryAlbum.collectAsStateWithLifecycle()
    val selection by vm.gallerySelection.collectAsStateWithLifecycle()
    val opState by vm.opState.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.browser_action_share)
    val galleryName = stringResource(R.string.browser_gallery)
    val fallbackZipName = stringResource(R.string.browser_compress_fallback_name)

    // **'오늘' 의 기준을 훑기마다 한 번만 잡는다.** 머리글마다 `currentTimeMillis` 를
    // 부르면 자정을 지나는 순간 화면의 절반은 '오늘', 절반은 '어제' 가 될 수 있다.
    // 다시 훑으면 다시 잡히므로 오래 켜 두어도 결국 따라온다.
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(scan, zone) { DayBucket.dayOf(System.currentTimeMillis(), zone) }
    // 어제의 마지막 밀리초. 하루를 빼면 서머타임이 있는 시간대에서 어긋난다.
    val yesterday = remember(today, zone) { DayBucket.dayOf(today.startMillis - 1, zone) }

    val ready = scan as? GalleryScanner.Scan.Ready
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    // 이름이 겹쳐 위 폴더까지 붙일 때 볼륨의 경로 조각(`0`·`1234-ABCD`) 대신 화면 이름(`내부 저장소`·SD 카드의 이름)을 쓴다.
    val volumeNames = remember(volumes) { volumes.associate { it.path to it.label } }
    val albums = remember(ready, volumeNames) { ready?.let { GalleryAlbums.of(it.items, volumeNames) }.orEmpty() }
    val openAlbum = album?.let { folder -> albums.firstOrNull { it.folder == folder } }
    val showingAlbums = view == BrowserViewModel.GALLERY_ALBUMS && album == null
    // 지금 격자에 보일 것 — 날짜 보기면 전부, 앨범이면 그 폴더(날짜 묶음 그대로 — `GalleryAlbums.subset`).
    val shown = remember(ready, album) {
        val folder = album
        when {
            ready == null -> null
            folder != null -> GalleryAlbums.subset(ready.items, ready.sections, folder)
            else -> GalleryAlbums.Subset(ready.items, ready.sections)
        }
    }
    // 연 앨범이 다시 훑은 뒤 사라졌으면(안의 것을 모두 지웠다·옮겼다) 앨범 목록으로 돌아간다.
    LaunchedEffect(ready, album, openAlbum) {
        if (ready != null && album != null && openAlbum == null) vm.closeAlbum()
    }
    val selecting = selection.isNotEmpty()
    val visiblePaths = remember(shown, showingAlbums) {
        if (showingAlbums) emptyList() else shown?.items?.map { it.path }.orEmpty()
    }
    val allSelected = remember(selection, visiblePaths) { SelectAll.allSelected(selection, visiblePaths) }
    // 고른 것을 격자의 차례로 — 공유·묶기·옮기기가 이 차례로 싣는다. 대화상자를 여는 순간의 것을 붙든다(아래 `pending*`).
    val orderedEntries = remember(selection, ready) { GalleryRules.ordered(selection, ready?.items.orEmpty()) }
    val ordered = remember(orderedEntries) { orderedEntries.map { it.path } }
    var pendingDelete by remember { mutableStateOf<List<FileEntry>?>(null) }
    var pendingStrip by remember { mutableStateOf<List<FileEntry>?>(null) }
    val tooManyToShare = stringResource(R.string.browser_gallery_share_too_many, GalleryRules.SHARE_LIMIT)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                if (selecting) {
                    GallerySelectionTopBar(
                        count = selection.size,
                        allSelected = allSelected,
                        plan = remember(ordered) { GalleryRules.plan(ordered) },
                        onClear = vm::clearGallerySelection,
                        onToggleSelectAll = { vm.toggleGallerySelectAll(visiblePaths) },
                        onShareZip = {
                            vm.shareZipFromGallery(ordered, openAlbum?.label ?: galleryName, fallbackZipName)
                        },
                        onShareClean = {
                            if (GalleryRules.canShareDirectly(ordered.size)) {
                                vm.shareCleanFromGallery(ordered)
                            } else {
                                scope.launch { snackbar.showSnackbar(tooManyToShare) }
                            }
                        },
                        onStripLocation = { pendingStrip = orderedEntries },
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = vm::galleryBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_up))
                            }
                        },
                        title = {
                            Text(
                                text = when {
                                    openAlbum != null -> PathNames.forDisplay(openAlbum.label)
                                    ready != null -> stringResource(R.string.browser_gallery_count, ready.items.size)
                                    else -> galleryName
                                },
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        },
                    )
                    // 보기 전환 — 앨범 안에서는 감춘다(제목이 앨범 이름이고, 뒤로 가기가 앨범 목록으로 간다).
                    if (album == null) {
                        val tab = if (view == BrowserViewModel.GALLERY_ALBUMS) 1 else 0
                        PrimaryTabRow(selectedTabIndex = tab) {
                            Tab(
                                selected = tab == 0,
                                onClick = { vm.setGalleryView(BrowserViewModel.GALLERY_DATES) },
                                text = { Text(stringResource(R.string.browser_gallery_view_dates)) },
                            )
                            Tab(
                                selected = tab == 1,
                                onClick = { vm.setGalleryView(BrowserViewModel.GALLERY_ALBUMS) },
                                text = { Text(stringResource(R.string.browser_gallery_view_albums)) },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                (opState as? FileOpManager.State.Running)?.let {
                    OperationBar(state = it, onCancel = vm::cancelOperation)
                }
                if (selecting) {
                    GallerySelectionBar(
                        onShare = {
                            // 원본 그대로 보낸다(위치가 든 채) — 지우고 보내려면 ⋮ 의 '위치 지우고 공유'. 너무 많으면 ZIP 을 권한다.
                            // 공유 창은 입출력 디스패처에서 짓고(파일마다 본다) 주 스레드에서 띄운다.
                            val paths = ordered
                            scope.launch {
                                if (!GalleryRules.canShareDirectly(paths.size)) {
                                    snackbar.showSnackbar(tooManyToShare)
                                    return@launch
                                }
                                val chooser = withContext(IroDispatchers.io) { ShareHelper.chooserFor(context, paths, shareTitle) }
                                if (chooser != null && ShareHelper.start(context, chooser)) {
                                    vm.clearGallerySelection()
                                } else {
                                    snackbar.showSnackbar(shareFailed)
                                }
                            }
                        },
                        onMove = { vm.moveFromGallery(ordered) },
                        onDelete = { pendingDelete = orderedEntries },
                    )
                }
            }
        },
    ) { inner ->
        RefreshableBox(
            refreshing = refreshing,
            onRefresh = vm::pullGallery,
            enabled = !selecting,
            modifier = Modifier.padding(inner).fillMaxSize(),
        ) {
            when (val s = scan) {
                is GalleryScanner.Scan.Scanning -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.browser_gallery_scanning, s.found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is GalleryScanner.Scan.Ready -> when {
                    s.items.isEmpty() -> {
                        // 사진이 없다는 안내도 당길 수 있어야 한다 — 사진을 넣고 다시 훑는 자리가 바로 여기다.
                        ScrollableNote {
                            Text(
                                stringResource(
                                    R.string.browser_gallery_empty,
                                    GalleryScanner.DEFAULT_FOLDERS.joinToString(" · "),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    showingAlbums -> AlbumGrid(albums = albums, onOpen = { vm.openAlbum(it.folder) })
                    else -> {
                        val subset = shown ?: GalleryAlbums.Subset(s.items, s.sections)
                        // 앨범마다 스크롤 자리를 따로 둔다 — 한 상태를 같이 쓰면 앨범을 열 때 날짜 보기의 자리에서 시작한다.
                        key(album) {
                            PhotoGrid(
                                items = subset.items,
                                sections = subset.sections,
                                today = today,
                                yesterday = yesterday,
                                selection = selection,
                                onTap = { entry, photos, at ->
                                    when {
                                        selecting -> vm.toggleGallerySelection(entry.path)
                                        at >= 0 -> onOpen(photos, at)
                                        else -> onPlay(entry)
                                    }
                                },
                                onLongPress = { vm.toggleGallerySelection(it.path) },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { entries ->
        DeleteConfirmDialog(
            count = entries.size,
            onConfirm = {
                vm.trashFromGallery(entries)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingStrip?.let { entries ->
        StripLocationDialog(
            plan = remember(entries) { GalleryRules.plan(entries.map { it.path }) },
            onConfirm = {
                vm.stripLocationFromGallery(entries)
                pendingStrip = null
            },
            onDismiss = { pendingStrip = null },
        )
    }
}

/**
 * 사진·영상 격자 — 날짜 머리글 + 칸. 날짜 보기와 앨범 안이 같은 것을 쓴다.
 *
 * @param onTap 누른 칸, 뷰어에 넘길 사진만의 목록, 그 목록에서의 자리(영상이면 -1).
 */
@Composable
private fun PhotoGrid(
    items: List<FileEntry>,
    sections: List<DayBucket.Section>,
    today: DayBucket.Day,
    yesterday: DayBucket.Day,
    selection: Set<String>,
    onTap: (FileEntry, List<FileEntry>, Int) -> Unit,
    onLongPress: (FileEntry) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // 뷰어에 넘길 사진만의 목록과, 격자 자리 → 그 목록 자리. 보이는 것이 바뀔 때만 다시 만든다.
    val photos = remember(items) { items.filter { it.kind == FileKind.IMAGE } }
    val photoAt = remember(items) {
        var n = 0
        IntArray(items.size) { if (items[it].kind == FileKind.IMAGE) n++ else -1 }
    }
    LazyVerticalGrid(
        // 칸 수를 고정하지 않는다. 태블릿 가로에서 3열이면 칸 하나가
        // 손바닥만 해진다.
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
    ) {
        for (section in sections) {
            item(
                key = "day:${section.day.startMillis}",
                // 머리글은 한 줄을 통째로 쓴다. 칸 하나로 두면 사진
                // 사이에 글자가 끼어 어느 날의 것인지 읽히지 않는다.
                span = { GridItemSpan(maxLineSpan) },
            ) {
                DayHeader(section.day, today, yesterday)
            }
            itemsIndexed(
                items = items.subList(section.firstIndex, section.firstIndex + section.count),
                key = { _, e -> e.path },
            ) { i, entry ->
                // **구간 안의 자리를 원래 목록의 자리로 되돌린다.**
                // 이것을 빠뜨리면 두 번째 날짜부터 누른 것과 다른
                // 사진이 열린다 — 화면에는 아무 오류도 나지 않는다.
                val flat = section.firstIndex + i
                GalleryCell(
                    entry = entry,
                    selected = entry.path in selection,
                    onClick = { onTap(entry, photos, photoAt[flat]) },
                    onLongClick = { onLongPress(entry) },
                )
            }
        }
    }
}

/** 앨범 목록 — 폴더마다 한 칸(표지 · 이름 · 개수). 차례는 가장 새 것이 새로운 앨범부터([GalleryAlbums.of]). */
@Composable
private fun AlbumGrid(albums: List<GalleryAlbums.Album>, onOpen: (GalleryAlbums.Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
    ) {
        items(albums, key = { it.folder }) { album ->
            Column(
                modifier = Modifier
                    .padding(4.dp)
                    .clickable { onOpen(album) },
            ) {
                Thumbnail(entry = album.cover, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Text(
                    // 폴더 이름 속의 방향 제어 글자는 보이는 표시로(목록과 같다).
                    text = PathNames.forDisplay(album.label),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = stringResource(R.string.browser_gallery_album_count, album.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 날짜 머리글.
 *
 * 오늘·어제는 **이름으로 부른다** — 숫자보다 빨리 읽히고, 갤러리에서 가장 자주 보는
 * 두 줄이다. 올해 안이면 해를 적지 않는다. 머리글마다 같은 네 자리가 되풀이될 뿐이고,
 * 해가 다른 줄에서만 그것이 정보가 된다.
 */
@Composable
private fun DayHeader(day: DayBucket.Day, today: DayBucket.Day, yesterday: DayBucket.Day) {
    val text = when {
        day.startMillis == today.startMillis -> stringResource(R.string.browser_gallery_date_today)
        day.startMillis == yesterday.startMillis ->
            stringResource(R.string.browser_gallery_date_yesterday)
        day.year == today.year ->
            stringResource(R.string.browser_gallery_date_md, day.month, day.dayOfMonth)
        else ->
            stringResource(R.string.browser_gallery_date_ymd, day.year, day.month, day.dayOfMonth)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
    )
}

/**
 * 격자 한 칸. 고른 칸은 그림 위를 덮고 귀퉁이에 ✓ 표시를 단다 — 색만으로 말하지 않는다(파일 목록의 칸과 같은 표시, 화면 낭독기에는 '선택됨').
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryCell(entry: FileEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Thumbnail(entry = entry, modifier = Modifier.fillMaxSize())
        if (selected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            SelectedMark(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

/**
 * 썸네일 하나 — 칸이 화면에 들어올 때 읽고 나가면 취소된다(`produceState`). 목록 전체를 미리 디코딩하면 사진 3,000장짜리 기기에서 메모리가
 * 먼저 끝난다. 영상은 ▶ 를 얹는다 — 썸네일이 아직 없어도 얹어 영상임을 먼저 알린다.
 */
@Composable
private fun Thumbnail(entry: FileEntry, modifier: Modifier) {
    val context = LocalContext.current
    val key = remember(entry.path, entry.size, entry.lastModified) {
        ThumbnailStore.keyOf(entry.path, entry.size, entry.lastModified)
    }
    val isVideo = entry.kind == FileKind.VIDEO
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key) {
        value = ThumbnailStore.get(
            context,
            entry.path,
            key,
            kind = if (isVideo) ThumbnailStore.Kind.VIDEO else ThumbnailStore.Kind.IMAGE,
            // 메신저 폴더의 축소본은 디스크에 남기지 않는다(머리말).
            persist = !GalleryRoots.isMediaPath(entry.path),
        )
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                // 격자에서 파일 이름을 하나하나 읽어 주면 낭독기가 쓸 수 없게 된다.
                // 칸의 이름은 파일 이름 하나로 충분하다.
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * 갤러리에서 고르는 동안의 위 막대 — 색을 바꿔 고르는 중임을 알린다(파일 목록의 [SelectionTopAppBar] 와 같은 모양). ⋮ 에는 고른 것을 보내거나
 * 바꾸는 셋 — 묶어 보내기, 위치 지우고 공유(사본), 원본에서 위치 지우기(묻는다). 위치 지우기는 그것을 할 수 있는 사진이 하나라도 있을 때만 켠다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GallerySelectionTopBar(
    count: Int,
    allSelected: Boolean,
    plan: GalleryRules.Plan,
    onClear: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onShareZip: () -> Unit,
    onShareClean: () -> Unit,
    onStripLocation: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, stringResource(R.string.browser_selection_clear))
            }
        },
        title = { Text(stringResource(R.string.browser_selection_count, count)) },
        actions = {
            SelectAllToggle(allSelected = allSelected, onToggle = onToggleSelectAll)
            // 단추와 메뉴를 한 상자에 담는다(함정 표 — Popup 의 앵커).
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.browser_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_share_as_zip)) },
                        onClick = {
                            menuOpen = false
                            onShareZip()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_gallery_share_clean)) },
                        enabled = plan.canShareClean,
                        onClick = {
                            menuOpen = false
                            onShareClean()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_gallery_strip_location)) },
                        enabled = plan.canStripInPlace,
                        onClick = {
                            menuOpen = false
                            onStripLocation()
                        },
                    )
                }
            }
        },
    )
}

/** 갤러리에서 고르는 동안의 아래 막대 — 공유 · 이동 · 삭제. */
@Composable
private fun GallerySelectionBar(onShare: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onShare) { Text(stringResource(R.string.browser_action_share)) }
            TextButton(onClick = onMove) { Text(stringResource(R.string.browser_action_move)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.browser_action_delete)) }
        }
    }
}

/**
 * '원본에서 위치 지우기' 의 확인. **원본을 바꾸고 되돌릴 수 없다**는 것을 말한다(휴지통에 사본을 남기지 않는다 — CLAUDE.md). 원본에서 지울 수
 * 없는 형식(JPEG·WebP 가 아닌 것 — 영상·PNG·HEIC)이 섞였으면 그 수를 함께 적는다.
 */
@Composable
private fun StripLocationDialog(plan: GalleryRules.Plan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.browser_gallery_strip_title)) },
        text = {
            Column {
                Text(stringResource(R.string.browser_gallery_strip_body, plan.scrubInPlace))
                val left = plan.total - plan.scrubInPlace
                if (left > 0) {
                    Text(
                        text = stringResource(R.string.browser_gallery_strip_left, left),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.browser_gallery_strip_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_dialog_cancel)) }
        },
    )
}
