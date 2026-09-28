package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.io.VolumeRegistry

/**
 * 첫 화면. 볼륨과 바로가기.
 *
 * 볼륨을 **화면이 보일 때마다 다시 읽는다.** SD 를 뽑았는데 목록에 남아 있으면
 * 눌렀을 때 '사라졌습니다' 가 뜨는데, 그것보다 애초에 없는 편이 낫다.
 */
@Composable
fun HomeScreen(
    vm: BrowserViewModel,
    snackbar: SnackbarHostState,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val volumes by vm.volumes.collectAsStateWithLifecycle()

    val trash by vm.trashEntries.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        vm.refreshVolumes()
        onPauseOrDispose { }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        LazyColumn(modifier = Modifier.padding(inner).fillMaxWidth()) {
            item(key = "h-storage") {
                SectionHeader(stringResource(R.string.browser_home_title))
            }
            items(volumes, key = { it.id }) { v ->
                VolumeRow(v, onClick = { vm.open(v.path) })
            }
            // 즐겨찾기는 볼륨 바로 아래다. 바로가기보다 위인 것은, 이것이 **사용자가
            // 직접 만든 목록**이어서 기계가 준 목록보다 먼저 보이는 것이 맞기 때문이다.
            if (bookmarks.isNotEmpty()) {
                item(key = "h-bookmarks") {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    SectionHeader(stringResource(R.string.browser_bookmarks))
                }
                items(bookmarks, key = { "b:" + it.path }) { mark ->
                    val exists = remember(mark.path) { java.io.File(mark.path).isDirectory }
                    ShortcutRow(
                        title = mark.label.ifEmpty { java.io.File(mark.path).name },
                        // 없어진 폴더를 목록에서 지우지 않는다. SD 를 빼 둔 것일 수도 있고,
                        // 무엇보다 사용자가 넣은 것을 우리가 말없이 치우면 안 된다.
                        subtitle = if (exists) mark.path else stringResource(R.string.browser_bookmark_missing),
                        enabled = exists,
                        onClick = { vm.open(mark.path) },
                        onLongClick = { vm.removeBookmark(mark.path) },
                    )
                }
            }
            item(key = "h-shortcuts") {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SectionHeader(stringResource(R.string.browser_home_shortcuts))
            }
            item(key = "gallery") {
                ShortcutRow(
                    title = stringResource(R.string.browser_gallery),
                    subtitle = stringResource(R.string.browser_gallery_desc),
                    onClick = onOpenGallery,
                )
            }
            item(key = "trash") {
                ShortcutRow(
                    title = stringResource(R.string.browser_trash),
                    subtitle = if (trash.isEmpty()) stringResource(R.string.browser_trash_empty)
                    else stringResource(R.string.browser_count, trash.size),
                    onClick = onOpenTrash,
                )
            }
            item(key = "settings") {
                ShortcutRow(
                    title = stringResource(R.string.browser_home_settings),
                    subtitle = stringResource(R.string.browser_home_settings_desc),
                    onClick = onOpenSettings,
                )
            }
            item(key = "diag") {
                ShortcutRow(
                    title = stringResource(R.string.browser_home_diagnostics),
                    subtitle = stringResource(R.string.browser_home_diagnostics_desc),
                    onClick = onOpenDiagnostics,
                )
            }
        }
    }
}

/**
 * 바로가기 한 줄.
 *
 * 즐겨찾기를 **길게 눌러 뺀다.** 줄마다 X 를 두면 첫 화면이 지우기 버튼 목록처럼
 * 보이고, 실수로 누르기도 쉽다. 길게 누르기는 안드로이드에서 '이 항목에 대한 조작'
 * 을 뜻하는 관행이다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun VolumeRow(volume: VolumeRegistry.Volume, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = volume.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (volume.isRemovable) {
                Text(
                    text = stringResource(R.string.browser_home_removable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 용량 막대. 남은 용량을 숫자로만 적으면 감이 오지 않는다.
        LinearProgressIndicator(
            progress = { volume.usedFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.browser_home_free,
                Format.size(volume.freeBytes),
                Format.size(volume.totalBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
