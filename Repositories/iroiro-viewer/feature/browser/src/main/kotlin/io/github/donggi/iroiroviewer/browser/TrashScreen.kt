package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.data.TrashEntryEntity
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.model.FileEntry

/**
 * 휴지통.
 *
 * 볼륨이 빠진 항목은 **숨기지 않고 버튼만 막는다.** 목록에서 사라지면 사용자는 그것이
 * 지워졌다고 생각하는데, 실제로는 SD 카드 안에 그대로 있다.
 *
 * **영구 삭제는 반드시 확인을 거친다.** 이 화면의 두 단추(`완전히 삭제`·`휴지통 비우기`)
 * 는 이 앱에서 유일하게 되돌릴 수 없는 조작이다. 되돌릴 수 있는 '휴지통으로 보내기' 가
 * 확인을 받는데 이쪽이 즉시 실행되면 보호가 거꾸로 걸린 것이다.
 *
 * **당기면 볼륨을 다시 읽고 기록과 파일을 맞춘다**(`BrowserViewModel.pullTrash`). SD 를 다시 꽂았을 때
 * '저장소가 연결되지 않음' 을 푸는 길이고, 다른 앱이 휴지통 폴더를 건드렸으면 앱을 다시 켜지 않아도 맞춰진다.
 * 이 화면에는 **읽던 쪽 배지를 달지 않는다** — 지운 것에 '읽던 쪽' 을 다는 것은 지운 것이 지워지지 않은 셈이다.
 *
 * **줄을 눌러도 아무것도 열지 않는다** — 누르면 고를 뿐이다(아래). 폴더 목록에서는 뷰어가 없는 파일을 누르면 다른 앱으로
 * 가지만, 휴지통의 것을 다른 앱에 넘기면 받은 앱이 사본을 남길 수 있다 — 지운 것이 밖으로 새어 나간다. 보려면 먼저 되돌린다(폴더
 * 목록의 `TapRoute` 도 휴지통 안의 경로는 다른 앱으로 보내지 않는다).
 *
 * ## 여럿을 골라 한꺼번에
 *
 * 사진 40장을 지운 뒤 스낵바의 되돌리기를 놓치면 줄마다 '되돌리기' 를 40번 눌러야 했다. **폴더 목록과 같은 고르기**를 둔다 — 길게
 * 누르면 고르고, 고르는 동안은 누를 때마다 넣고 뺀다(그때 줄의 단추는 거둔다 — 고르는 손이 단추를 누르지 않게). 여기서는 **그냥 눌러도
 * 고른다** — 줄을 누르는 것에 다른 뜻이 없어(위 문단) 아무 반응이 없던 자리다. 위 막대가 개수와 '전체 선택'(다시 누르면 모두 푼다)을,
 * 아래 막대가 되돌리기·다른 곳에 복원·완전히 삭제를 든다. 손대는 것은 저장소가 붙어 있는 항목뿐이다 —
 * 붙지 않은 것을 골랐으면 완전히 삭제의 확인이 '남긴다' 고 말한다. 고르기는 ViewModel 이 든다(`BrowserViewModel.trashSelection`).
 *
 * ## 썸네일
 *
 * 이름만으로는 `IMG_…` 가운데 어느 사진인지 알 수 없다. 그림·영상은 썸네일을 보이되 **디스크에 남기지 않는다**(`persist = false` —
 * 휴지통의 경로라 `FileItems` 의 [Thumb] 가 그렇게 가른다). 지운 사진의 축소본이 앱 저장소에 남으면 지운 것이 지워지지 않은 셈이다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    vm: BrowserViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by vm.trashEntries.collectAsStateWithLifecycle()
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val refreshing by vm.trashRefreshing.collectAsStateWithLifecycle()
    val selection by vm.trashSelection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()

    /** 지금 붙어 있는 저장소의 항목만 실제로 지울 수 있다. */
    val available = entries.filter { e -> volumes.any { it.id == e.volumeId } }
    val unavailable = entries.size - available.size
    // 고른 것 가운데 손댈 수 있는 것 — 저장소가 붙어 있다. 나머지는 확인 창이 '남긴다' 고 말한다.
    val pickedAvailable = available.filter { it.uuid in selection }
    val pickedUnavailable = selection.size - pickedAvailable.size

    var confirming by remember { mutableStateOf<List<TrashEntryEntity>?>(null) }
    var confirmingAll by remember { mutableStateOf(false) }
    var confirmingSkipped by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                // 폴더 목록의 선택 막대와 같은 모양 — 색이 바뀌는 것은 '지울 수 있는 상태' 라는 표시다(`SelectionTopAppBar`).
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    navigationIcon = {
                        IconButton(onClick = vm::clearTrashSelection) {
                            Icon(Icons.Filled.Close, stringResource(R.string.browser_selection_clear))
                        }
                    },
                    title = { Text(stringResource(R.string.browser_selection_count, selection.size)) },
                    actions = {
                        val all = entries.map { it.uuid }
                        SelectAllToggle(
                            allSelected = SelectAll.allSelected(selection, all),
                            onToggle = { vm.toggleSelectAllTrash(all) },
                        )
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_up))
                        }
                    },
                    title = { Text(stringResource(R.string.browser_trash)) },
                    actions = {
                        if (available.isNotEmpty()) {
                            TextButton(onClick = { confirming = available; confirmingAll = true; confirmingSkipped = 0 }) {
                                Text(stringResource(R.string.browser_trash_purge_all))
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                TrashSelectionBar(
                    enabled = pickedAvailable.isNotEmpty(),
                    onPurge = {
                        confirming = pickedAvailable
                        confirmingAll = false
                        confirmingSkipped = pickedUnavailable
                    },
                    onRestoreElsewhere = { vm.startRestoreElsewhere(pickedAvailable) },
                    onRestore = {
                        vm.restoreFromTrash(pickedAvailable.map { it.uuid })
                        vm.clearTrashSelection()
                    },
                )
            }
        },
    ) { inner ->
        RefreshableBox(
            refreshing = refreshing,
            onRefresh = vm::pullTrash,
            // 고르는 중에는 당기지 않는다 — 다시 읽혀 줄이 움직이면 다른 줄을 고르게 된다(폴더 목록과 같다).
            enabled = !selecting,
            modifier = Modifier.padding(inner).fillMaxSize(),
        ) {
            if (entries.isEmpty()) {
                ScrollableNote {
                    Text(
                        stringResource(R.string.browser_trash_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@RefreshableBox
            }

            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.uuid }) { entry ->
                    val volume = volumes.firstOrNull { it.id == entry.volumeId }
                    TrashRow(
                        entry = entry,
                        volumePath = volume?.path,
                        selected = entry.uuid in selection,
                        selecting = selecting,
                        allowLoad = !listState.isScrollInProgress,
                        onToggle = { vm.toggleTrashSelection(entry.uuid) },
                        onRestore = { vm.restoreFromTrash(listOf(entry.uuid)) },
                        onRestoreElsewhere = { vm.startRestoreElsewhere(listOf(entry)) },
                        onPurge = { confirming = listOf(entry); confirmingAll = false; confirmingSkipped = 0 },
                    )
                    HorizontalDivider()
                }
                item(key = "note") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.browser_trash_select_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.browser_trash_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    confirming?.let { targets ->
        PurgeConfirmDialog(
            count = targets.size,
            bytes = targets.sumOf { it.size },
            unavailable = if (confirmingAll) unavailable else confirmingSkipped,
            all = confirmingAll,
            onConfirm = {
                vm.purgeFromTrash(targets.map { it.uuid })
                vm.clearTrashSelection()
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
}

/**
 * 고르는 동안의 아래 막대 — 고른 것을 한꺼번에(폴더 목록의 `SelectionActionBar` 와 같은 모양). 위험한 것(완전히 삭제)이 맨 앞이고
 * 오류 색이다 — 줄 하나의 단추와 같은 차례라, 손이 익은 자리에 같은 일이 있다.
 *
 * @param enabled 손댈 수 있는 것(저장소가 붙어 있다)을 하나라도 골랐는가.
 */
@Composable
private fun TrashSelectionBar(
    enabled: Boolean,
    onPurge: () -> Unit,
    onRestoreElsewhere: () -> Unit,
    onRestore: () -> Unit,
) {
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
            TextButton(onClick = onPurge, enabled = enabled) {
                Text(
                    text = stringResource(R.string.browser_trash_purge),
                    color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = onRestoreElsewhere, enabled = enabled) {
                Text(stringResource(R.string.browser_trash_restore_elsewhere))
            }
            TextButton(onClick = onRestore, enabled = enabled) {
                Text(stringResource(R.string.browser_trash_restore))
            }
        }
    }
}

/**
 * 휴지통 한 줄.
 *
 * @param volumePath 그 항목의 볼륨 — 붙어 있지 않으면 null 이고, 단추가 막히고 썸네일을 읽지 않는다.
 * @param selecting 고르는 중인가. 그동안은 누르면 넣고 빼며, 줄의 단추를 거둔다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashRow(
    entry: TrashEntryEntity,
    volumePath: String?,
    selected: Boolean,
    selecting: Boolean,
    allowLoad: Boolean,
    onToggle: () -> Unit,
    onRestore: () -> Unit,
    onRestoreElsewhere: () -> Unit,
    onPurge: () -> Unit,
) {
    val available = volumePath != null
    Column(
        Modifier
            .fillMaxWidth()
            // 고르기 — 폴더 목록과 같다(길게 누르면 고르고, 고르는 동안은 누를 때마다 넣고 뺀다). 여기서는 그냥 눌러도 고른다 —
            // 열 것이 없는 자리다(클래스 주석).
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onToggle,
            )
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            // 배경색만으로는 화면 낭독기가 읽지 못한다 — 지울 수 있는 상태다(`FileRow` 와 같다).
            .semantics { this.selected = selected }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TrashThumb(entry, volumePath, allowLoad)
                if (selected) SelectedMark(Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.originalName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    text = buildString {
                        append(stringResource(R.string.browser_trash_deleted_at, Format.timestamp(entry.deletedAt)))
                        if (!entry.isDirectory) {
                            append("  ·  ")
                            append(Format.size(entry.size))
                        }
                        if (!available) {
                            append("  ·  ")
                            append(stringResource(R.string.browser_trash_unavailable))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 기록 없이 발견된 항목. 예전에는 이런 것을 말없이 영구 삭제했는데, 그것은
                // '무엇인지 모르겠다' 를 '지워도 된다' 로 읽는 것이었다. 이제는 이렇게 띄운다.
                if (entry.originalParent == TrashStore.UNKNOWN_PARENT) {
                    Text(
                        text = stringResource(R.string.browser_trash_unknown_origin, TrashStore.RECOVERED_DIR),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        // **고르는 동안에는 줄의 단추를 거둔다** — 고르려고 누른 손이 '완전히 삭제' 를 누르지 않게. 한꺼번에 하는 단추는 아래 막대에 있다.
        if (!selecting) {
            // **단추가 셋이 되어 줄이 넘칠 수 있다**(글꼴을 키운 폰). 한 줄에 못 담으면 다음 줄로 내린다 — 잘리면
            // 가장 오른쪽의 '되돌리기' 가 사라진다.
            FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onPurge, enabled = available) {
                    Text(
                        text = stringResource(R.string.browser_trash_purge),
                        color = if (available) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    )
                }
                // 원래 자리를 모르는 항목(정합성 검사가 되살린 고아)을 볼륨 루트의 '복구된 항목' 이 아니라 사용자가
                // 고른 폴더로 꺼내는 길이다. 원래 자리를 아는 항목도 다른 곳에 두고 싶을 수 있어 모두에 둔다.
                TextButton(onClick = onRestoreElsewhere, enabled = available) {
                    Text(stringResource(R.string.browser_trash_restore_elsewhere))
                }
                TextButton(onClick = onRestore, enabled = available) {
                    Text(stringResource(R.string.browser_trash_restore))
                }
            }
        }
    }
}

/**
 * 휴지통 항목의 그림 — 그림·영상이면 썸네일, 아니면 종류 배지(이름의 확장자로 가른다 — 휴지통 안의 파일은 이름이 uuid 다).
 *
 * **디스크에 남기지 않는다**: 휴지통의 경로라 [Thumb] 가 `persist = false` 로 읽는다(`PathRules.isNoMediaOrTrash`). 만화의 표지는
 * 꺼내지 않는다 — 표지를 꺼내는 쪽은 확장자로 형식을 가리는데 휴지통 안의 파일에는 확장자가 없다. 저장소가 붙어 있지 않으면 읽지 않는다.
 */
@Composable
private fun TrashThumb(entry: TrashEntryEntity, volumePath: String?, allowLoad: Boolean) {
    val stored = volumePath?.let { TrashStore.storedFileOf(it, entry.uuid).path }
    val shown = remember(entry.uuid, stored) {
        FileEntry(
            name = entry.originalName,
            // 저장소가 빠졌으면 읽을 자리가 없다 — 이름만으로 배지를 그린다(그 경로는 열지 않는다).
            path = stored ?: entry.originalName,
            isDirectory = entry.isDirectory,
            size = entry.size,
            // 썸네일의 열쇠(경로·크기·시각)가 항목마다 갈리면 된다 — 지운 시각이 그 몫을 한다(메모리에만 든다).
            lastModified = entry.deletedAt,
            isHidden = false,
            isSymlink = false,
            kind = MimeResolver.kindOf(entry.originalName, entry.isDirectory),
        )
    }
    Thumb(entry = shown, size = 40.dp, allowLoad = allowLoad && stored != null, coverAllowed = false)
}
