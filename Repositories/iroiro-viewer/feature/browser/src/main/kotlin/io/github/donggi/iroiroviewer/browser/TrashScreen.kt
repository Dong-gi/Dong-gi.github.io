package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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
        if (entries.isEmpty()) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.browser_trash_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(Modifier.padding(inner).fillMaxSize()) {
            items(entries, key = { it.uuid }) { entry ->
                TrashRow(
                    entry = entry,
                    available = volumes.any { it.id == entry.volumeId },
                    onRestore = { vm.restoreFromTrash(entry.uuid) },
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
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onPurge, enabled = available) {
                Text(
                    text = stringResource(R.string.browser_trash_purge),
                    color = if (available) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = onRestore, enabled = available) {
                Text(stringResource(R.string.browser_trash_restore))
            }
        }
    }
}
