package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.Format

/**
 * 선택 모드의 상단 바.
 *
 * 색을 바꾸는 것이 중요하다 — 선택 모드는 **파일을 지울 수 있는 상태**라서, 평소와
 * 같아 보이면 사용자가 자기가 어느 모드에 있는지 모른 채 누른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
) {
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
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.Check, stringResource(R.string.browser_select_all))
            }
        },
    )
}

/** 선택 모드의 하단 조작 바. 이름 바꾸기는 하나만 골랐을 때만 의미가 있다. */
@Composable
fun SelectionActionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.browser_action_copy)) }
            TextButton(onClick = onMove) { Text(stringResource(R.string.browser_action_move)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.browser_action_delete)) }
            TextButton(onClick = onRename, enabled = count == 1) {
                Text(stringResource(R.string.browser_action_rename))
            }
            TextButton(onClick = onShare) { Text(stringResource(R.string.browser_action_share)) }
        }
    }
}

/**
 * 붙여넣기 바. 클립보드가 비지 않은 동안 계속 보인다.
 *
 * 이것이 떠 있는 동안 사용자는 **아무 폴더로나 자유롭게 이동**할 수 있다. 대상 폴더를
 * 먼저 고르게 하는 방식(폴더 피커)은 그 안에서 다시 탐색해야 하는데, 그 화면은 지금
 * 만든 브라우저보다 언제나 못하다.
 */
@Composable
fun PasteBar(
    clipboard: BrowserViewModel.Clipboard,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (clipboard.move) R.string.browser_paste_move else R.string.browser_paste_copy,
                    clipboard.paths.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.browser_paste_cancel)) }
            TextButton(onClick = onPaste) { Text(stringResource(R.string.browser_paste_here)) }
        }
    }
}

/**
 * 작업 진행 바.
 *
 * 진행률을 **바이트로** 보여주는 것이 중요하다. 파일 수로만 보여주면 큰 파일 하나를
 * 복사하는 동안 0% 에서 멈춰 있는 것처럼 보인다.
 */
@Composable
fun OperationBar(
    state: FileOpManager.State.Running,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            when (state.kind) {
                                FileOpManager.Kind.COPY -> R.string.browser_op_copying
                                FileOpManager.Kind.MOVE -> R.string.browser_op_moving
                                FileOpManager.Kind.TRASH -> R.string.browser_op_trashing
                                FileOpManager.Kind.DELETE, FileOpManager.Kind.PURGE -> R.string.browser_op_deleting
                                FileOpManager.Kind.RESTORE -> R.string.browser_op_restoring
                                FileOpManager.Kind.ROTATE -> R.string.browser_op_rotating
                                FileOpManager.Kind.EXTRACT -> R.string.browser_op_extracting
                                // 정합성 검사는 조용히 돈다. 이 바가 뜨는 일이 없다.
                                FileOpManager.Kind.RECONCILE -> R.string.browser_op_deleting
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val p = state.progress
                    if (p.filesTotal > 0) {
                        Text(
                            text = "${p.currentName}  ·  ${p.filesDone}/${p.filesTotal}" +
                                if (p.bytesTotal > 0) "  ·  ${Format.size(p.bytesDone)} / ${Format.size(p.bytesTotal)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.browser_op_cancel)) }
            }
            val p = state.progress
            if (p.bytesTotal > 0) {
                LinearProgressIndicator(
                    progress = { (p.bytesDone.toFloat() / p.bytesTotal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
            }
        }
    }
}

/**
 * 실패 이유의 문자열 자원.
 *
 * 컴포저블과 따로 둔 것은 결과가 **채널로** 오기 때문이다. 그 자리는 컴포지션 밖이라
 * `stringResource` 를 부를 수 없고 `context.getString` 을 써야 한다. 대응표는 하나여야 한다.
 */
fun reasonStringId(reason: FileOpEngine.Reason): Int = when (reason) {
    FileOpEngine.Reason.NO_SPACE -> R.string.browser_error_no_space
    FileOpEngine.Reason.NOT_FOUND -> R.string.browser_error_not_found
    FileOpEngine.Reason.PERMISSION -> R.string.browser_error_permission
    FileOpEngine.Reason.IO -> R.string.browser_error_io
    FileOpEngine.Reason.INVALID_NAME -> R.string.browser_error_invalid_name
    FileOpEngine.Reason.TARGET_INSIDE_SOURCE -> R.string.browser_error_target_inside
}

/** 실패 이유를 사람의 말로. */
@Composable
fun reasonText(reason: FileOpEngine.Reason): String = stringResource(reasonStringId(reason))

// 재생 실패와 이어보기 문구는 `core:playback` 의 `PlaybackText.kt` 에 있다 —
// 그것을 만들어 내는 `PlaybackFailure` 가 거기 있고, 재생 화면도 같은 문구를 쓴다.
