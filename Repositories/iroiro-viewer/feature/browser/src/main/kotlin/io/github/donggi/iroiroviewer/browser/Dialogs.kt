package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.io.PathRules
import io.github.donggi.iroiroviewer.io.TrashStore

/**
 * 이름을 받는 다이얼로그. 새 폴더와 이름 바꾸기가 같은 것을 쓴다.
 *
 * **입력하는 동안 검사한다.** 확인을 누른 뒤에 "쓸 수 없는 이름입니다" 를 보여주면
 * 무엇이 문제인지 모른 채 다시 시도하게 된다.
 */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    /** 이름 바꾸기면 확장자를 뺀 부분만 선택해 준다. 확장자를 지우는 실수가 줄어든다. */
    selectBaseName: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember {
        val end = if (selectBaseName) {
            val dot = initial.lastIndexOf('.')
            if (dot > 0) dot else initial.length
        } else {
            initial.length
        }
        mutableStateOf(TextFieldValue(initial, TextRange(0, end)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val verdict = PathRules.validate(value.text)
    val error = (verdict as? PathRules.Verdict.Rejected)?.reason

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null && value.text.isNotEmpty(),
                    modifier = Modifier.focusRequester(focus),
                )
                if (error != null && value.text.isNotEmpty()) {
                    Text(
                        text = nameErrorText(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (verdict as? PathRules.Verdict.Ok)?.let { onConfirm(it.name) } },
                enabled = verdict is PathRules.Verdict.Ok,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_dialog_cancel)) }
        },
    )
}

@Composable
private fun nameErrorText(reason: PathRules.Reason): String = when (reason) {
    PathRules.Reason.EMPTY -> stringResource(R.string.browser_error_invalid_name)
    PathRules.Reason.DOT_ONLY -> stringResource(R.string.browser_error_invalid_name)
    PathRules.Reason.FORBIDDEN_CHAR -> stringResource(R.string.browser_error_name_forbidden)
    PathRules.Reason.CONTROL_CHAR -> stringResource(R.string.browser_error_invalid_name)
    PathRules.Reason.TOO_LONG -> stringResource(R.string.browser_error_name_too_long)
    PathRules.Reason.TRAILING_DOT_OR_SPACE -> stringResource(R.string.browser_error_name_trailing)
}

/** 삭제 확인. 되돌릴 수 있다는 사실을 **여기서** 말해야 사용자가 편하게 누른다. */
@Composable
fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_delete_title)) },
        text = {
            Text(stringResource(R.string.browser_delete_body, count, TrashStore.DEFAULT_RETENTION_DAYS))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.browser_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_dialog_cancel)) }
        },
    )
}

/**
 * **되돌릴 수 없는 삭제**의 확인.
 *
 * 이 대화상자가 없던 것이 이 앱에서 가장 위험한 자리였다. '휴지통으로 보내기' 는
 * 확인을 받는데 '완전히 삭제' 와 '휴지통 비우기' 는 누르는 즉시 실행됐다 — 보호가
 * 정확히 거꾸로 걸려 있었다.
 *
 * 그래서 [DeleteConfirmDialog] 와 **눈으로 구분되게** 만든다. 제목이 다르고, 개수와
 * 용량을 적고, 확인 단추가 error 색이다.
 */
@Composable
fun PurgeConfirmDialog(
    count: Int,
    bytes: Long,
    unavailable: Int,
    all: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(if (all) R.string.browser_purge_all_title else R.string.browser_purge_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (all) R.string.browser_purge_all_body else R.string.browser_purge_body,
                        count,
                        Format.size(bytes),
                    )
                )
                if (unavailable > 0) {
                    Text(
                        text = stringResource(R.string.browser_purge_skipped, unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.browser_purge_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_dialog_cancel)) }
        },
    )
}

/**
 * 이름 충돌.
 *
 * 항목마다 묻지 않고 **한 번 물어 남은 전부에 적용**한다. 100개를 붙여넣는데 100번
 * 물으면 사용자는 아무 버튼이나 누르게 되고, 그것이 덮어쓰기면 돌이킬 수 없다.
 *
 * **선택지를 세로 한 열로 세운다.** `AlertDialog` 의 `dismissButton` 은 `confirmButton`
 * 왼쪽에 놓이므로, 거기에 덮어쓰기를 넣으면 평소 '취소' 가 있던 자리에 가장 위험한
 * 선택지가 온다. 그리고 취소가 아예 없었다 — 바깥을 눌러야만 빠져나갈 수 있었다.
 */
@Composable
fun ConflictDialog(
    conflicts: BrowserViewModel.PasteConflicts,
    onPick: (FileOpEngine.Conflict) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_conflict_title)) },
        text = {
            Column {
                val names = conflicts.overwritable + conflicts.blocked
                Text(
                    stringResource(
                        R.string.browser_conflict_body,
                        names.size,
                        names.firstOrNull().orEmpty(),
                    )
                )
                if (conflicts.blocked.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.browser_conflict_blocked, conflicts.blocked.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        // 한 열로 세우고 위험한 것을 맨 아래에 둔다. confirmButton 만 쓰는 것은
        // 이 자리만이 전체 너비를 받기 때문이다.
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onPick(FileOpEngine.Conflict.KEEP_BOTH) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.browser_conflict_keep_both)) }
                TextButton(
                    onClick = { onPick(FileOpEngine.Conflict.SKIP) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.browser_conflict_skip)) }
                if (conflicts.overwritable.isNotEmpty()) {
                    TextButton(
                        onClick = { onPick(FileOpEngine.Conflict.OVERWRITE) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = stringResource(R.string.browser_conflict_overwrite),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.browser_dialog_cancel))
                }
            }
        },
    )
}
