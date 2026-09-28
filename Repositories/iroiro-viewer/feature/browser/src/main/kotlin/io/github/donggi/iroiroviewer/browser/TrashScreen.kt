package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.data.TrashEntryEntity
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.io.TrashStore

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
 * **줄을 눌러도 아무것도 열지 않는다.** 폴더 목록에서는 뷰어가 없는 파일을 누르면 다른 앱으로 가지만, 휴지통의 것을
 * 다른 앱에 넘기면 받은 앱이 사본을 남길 수 있다 — 지운 것이 밖으로 새어 나간다. 보려면 먼저 되돌린다(폴더 목록의
 * `TapRoute` 도 휴지통 안의 경로는 다른 앱으로 보내지 않는다).
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

    /** 지금 붙어 있는 저장소의 항목만 실제로 지울 수 있다. */
    val available = entries.filter { e -> volumes.any { it.id == e.volumeId } }
    val unavailable = entries.size - available.size

    var confirming by remember { mutableStateOf<List<TrashEntryEntity>?>(null) }
    var confirmingAll by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_up))
                    }
                },
                title = { Text(stringResource(R.string.browser_trash)) },
                actions = {
                    if (available.isNotEmpty()) {
                        TextButton(onClick = { confirming = available; confirmingAll = true }) {
                            Text(stringResource(R.string.browser_trash_purge_all))
                        }
                    }
                },
            )
        },
    ) { inner ->
        RefreshableBox(
            refreshing = refreshing,
            onRefresh = vm::pullTrash,
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

            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.uuid }) { entry ->
                    TrashRow(
                        entry = entry,
                        available = volumes.any { it.id == entry.volumeId },
                        onRestore = { vm.restoreFromTrash(entry.uuid) },
                        onRestoreElsewhere = { vm.startRestoreElsewhere(entry) },
                        onPurge = { confirming = listOf(entry); confirmingAll = false },
                    )
                    HorizontalDivider()
                }
                item(key = "note") {
                    Text(
                        text = stringResource(R.string.browser_trash_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    confirming?.let { targets ->
        PurgeConfirmDialog(
            count = targets.size,
            bytes = targets.sumOf { it.size },
            unavailable = if (confirmingAll) unavailable else 0,
            all = confirmingAll,
            onConfirm = {
                vm.purgeFromTrash(targets.map { it.uuid })
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun TrashRow(
    entry: TrashEntryEntity,
    available: Boolean,
    onRestore: () -> Unit,
    onRestoreElsewhere: () -> Unit,
    onPurge: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
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
