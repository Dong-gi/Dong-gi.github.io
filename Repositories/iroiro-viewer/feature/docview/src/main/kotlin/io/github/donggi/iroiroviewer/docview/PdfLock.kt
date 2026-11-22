package io.github.donggi.iroiroviewer.docview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfLocker
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.ui.NewPasswordFields
import io.github.donggi.iroiroviewer.ui.PasswordDialog
import io.github.donggi.iroiroviewer.ui.rememberNewPasswordState
import java.util.concurrent.ConcurrentHashMap

/*
 * PDF 뷰어 위 막대의 **자물쇠**(사용자 2026-11-22 — '자물쇠 모양의 토글 단추로 암호를 풀거나 새로 설정'). 잠긴 문서(사용자 암호·소유자 암호만)면
 * 닫힌 자물쇠 — 누르면 '암호를 풀어 저장할까요?', 잠기지 않은 문서면 열린 자물쇠 — 누르면 새 암호를 두 번 받아 AES-256 으로 잠근다. 인증서·전용
 * 처리기 잠금은 흐린 닫힌 자물쇠 — 누르면 까닭. 바꾸는 것은 **그 파일 자체**다(큐의 `PdfRelock` — 바꾸기 전 판은 휴지통에).
 */

/** 큐가 돌려준 잠금 바꾸기의 결과를 **그 뷰어에** 돌려주는 자리(글 편집의 `TextSaveJobs` 와 같은 모양). 프로세스에 하나다. */
internal object PdfRelockJobs {

    private val waiting = ConcurrentHashMap<String, (FileOpManager.Finished) -> Unit>()

    /** 신원이 [id] 인 요청의 결과를 [onResult] 가 받는다. **작업 스레드에서 불린다** — 값만 옮기고 곧바로 돌아온다. */
    fun expect(id: String, onResult: (FileOpManager.Finished) -> Unit) {
        waiting[id] = onResult
    }

    /** 받을 자리를 거둔다 — 그 뒤에 끝난 결과는 앱의 결과 줄로 간다(파일 목록이 말한다). */
    fun forget(id: String) {
        waiting.remove(id)
    }

    val sink = FileOpManager.ResultSink { f ->
        val receiver = waiting.remove(f.id) ?: return@ResultSink false
        receiver(f)
        true
    }
}

/** 막대의 자물쇠 단추. [busy] 면 누를 수 없다(바꾸는 중). 바꿀 수 없는 잠금은 흐리게 — 누르면 까닭을 말한다(그래서 막지 않는다). */
@Composable
internal fun LockAction(button: DocViewModel.LockButton, busy: Boolean, onClick: () -> Unit) {
    val locked = button != DocViewModel.LockButton.UNLOCKED
    val description = stringResource(
        when (button) {
            DocViewModel.LockButton.UNLOCKED -> R.string.doc_lock_set
            DocViewModel.LockButton.LOCKED_OWNER, DocViewModel.LockButton.LOCKED_PASSWORD -> R.string.doc_lock_remove
            DocViewModel.LockButton.UNSUPPORTED -> R.string.doc_lock_unsupported_title
        },
    )
    IconButton(onClick = onClick, enabled = !busy) {
        Icon(
            imageVector = if (locked) LockClosedIcon else LockOpenIcon,
            contentDescription = description,
            modifier = if (button == DocViewModel.LockButton.UNSUPPORTED) Modifier.alpha(0.45f) else Modifier,
        )
    }
}

/**
 * 누른 뒤의 창 — 잠기지 않았으면 새 암호(두 칸 — `NewPasswordFields`, 저장되지 않는 상태), 잠겼으면 풀지 묻기, 바꿀 수 없으면 까닭.
 *
 * @param onLock **배열의 주인이 넘어간다**(받은 쪽이 다 쓰고 덮는다).
 */
@Composable
internal fun PdfLockDialog(
    button: DocViewModel.LockButton,
    onDismiss: () -> Unit,
    onLock: (CharArray) -> Unit,
    onUnlock: () -> Unit,
) {
    when (button) {
        DocViewModel.LockButton.UNLOCKED -> SetPasswordDialog(onDismiss, onLock)
        DocViewModel.LockButton.LOCKED_OWNER, DocViewModel.LockButton.LOCKED_PASSWORD -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.doc_unlock_title)) },
            text = {
                Text(
                    stringResource(
                        if (button == DocViewModel.LockButton.LOCKED_OWNER) R.string.doc_unlock_owner_message else R.string.doc_unlock_message,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onUnlock) { Text(stringResource(R.string.doc_unlock_confirm)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_lock_cancel)) } },
        )
        DocViewModel.LockButton.UNSUPPORTED -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.doc_lock_unsupported_title)) },
            text = { Text(stringResource(R.string.doc_lock_unsupported)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_lock_ok)) } },
        )
    }
}

/**
 * 새 암호 두 번. 막는 것 — 비었다·두 칸이 다르다·127바이트를 넘는다·다른 PDF 앱이 다르게 읽는 글자([PdfLocker.problemOf] — 잠그는 쪽과 같은 판단).
 */
@Composable
private fun SetPasswordDialog(onDismiss: () -> Unit, onLock: (CharArray) -> Unit) {
    val state = rememberNewPasswordState()
    // 판단에 쓸 사본은 그 자리에서 만들고 덮는다(첫 칸의 글자 — 칸의 버퍼는 덮을 길이 없다, 같은 한계).
    val problem = if (state.isEmpty) {
        PdfLocker.PasswordProblem.EMPTY
    } else {
        val t = state.text
        val chars = CharArray(t.length) { t[it] }
        try {
            PdfLocker.problemOf(chars)
        } finally {
            chars.fill('\u0000')
        }
    }
    val ok = problem == null && state.matches
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_lock_set_title)) },
        text = {
            Column {
                Text(stringResource(R.string.doc_lock_set_message))
                NewPasswordFields(state, Modifier.padding(top = 12.dp), label = stringResource(R.string.doc_lock_new_password))
                val why = when (problem) {
                    PdfLocker.PasswordProblem.TOO_LONG -> R.string.doc_lock_too_long
                    PdfLocker.PasswordProblem.UNENCODABLE, PdfLocker.PasswordProblem.UNPORTABLE -> R.string.doc_lock_unportable
                    PdfLocker.PasswordProblem.EMPTY, null -> null
                }
                if (why != null) {
                    Text(
                        stringResource(why),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { state.take()?.let(onLock) }) { Text(stringResource(R.string.doc_lock_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_lock_cancel)) } },
    )
}

/**
 * 큐가 멈추고 묻는 것 — 풀 암호를 다시(세션 암호가 지워졌다 — 앱이 화면에서 사라지면 지운다), 바꾸기 전 판을 휴지통에 남기지 못했는데 그래도
 * 바꿀까.
 */
@Composable
internal fun PdfLockQuestionDialog(
    question: DocViewModel.LockQuestion,
    onPassword: (CharArray) -> Unit,
    onWithoutPrevious: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (question) {
        DocViewModel.LockQuestion.PASSWORD, DocViewModel.LockQuestion.WRONG_PASSWORD -> PasswordDialog(
            title = stringResource(R.string.doc_unlock_title),
            message = stringResource(R.string.doc_unlock_need_password),
            wrong = question == DocViewModel.LockQuestion.WRONG_PASSWORD,
            onDismiss = onDismiss,
            onSubmit = onPassword,
            confirm = stringResource(R.string.doc_unlock_confirm),
        )
        DocViewModel.LockQuestion.WITHOUT_PREVIOUS -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.doc_lock_without_previous_title)) },
            text = { Text(stringResource(R.string.doc_lock_without_previous_message)) },
            confirmButton = { TextButton(onClick = onWithoutPrevious) { Text(stringResource(R.string.doc_lock_anyway)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_lock_cancel)) } },
        )
    }
}

/**
 * 자물쇠 둘 — 코어 아이콘 세트에는 닫힌 `Lock` 만 있다(열린 것은 확장 세트다 — 함정 표의 49개). 두 모양이 같은 붓으로 그려져야 토글로 읽히므로
 * 둘 다 직접 그린다: 몸통(열쇠 구멍이 뚫린 둥근 사각형)과 고리. 닫힌 것은 고리의 두 다리가 몸통에 들고, 열린 것은 한 다리가 들려 있다.
 */
internal val LockClosedIcon: ImageVector by lazy { lock(open = false) }
internal val LockOpenIcon: ImageVector by lazy { lock(open = true) }

private fun lock(open: Boolean): ImageVector = ImageVector.Builder(
    name = if (open) "LockOpen" else "LockClosed",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // 고리 — 반원 위에 두 다리. 열린 것은 고리를 위로 들고 왼쪽 다리를 몸통 밖에서 끝낸다.
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
        if (open) {
            moveTo(16f, 10.5f)
            lineTo(16f, 5.5f)
            arcTo(4f, 4f, 0f, false, false, 8f, 5.5f)
            lineTo(8f, 6.5f)
        } else {
            moveTo(8f, 10.5f)
            lineTo(8f, 7.5f)
            arcTo(4f, 4f, 0f, false, true, 16f, 7.5f)
            lineTo(16f, 10.5f)
        }
    }
    // 몸통 — 열쇠 구멍을 짝수-홀수 규칙으로 뚫는다.
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        roundRect(5f, 10f, 19f, 21.5f, 2f)
        circle(12f, 15.2f, 1.6f)
    }
}.build()

private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
    moveTo(l + radius, t)
    lineTo(r - radius, t)
    arcTo(radius, radius, 0f, false, true, r, t + radius)
    lineTo(r, b - radius)
    arcTo(radius, radius, 0f, false, true, r - radius, b)
    lineTo(l + radius, b)
    arcTo(radius, radius, 0f, false, true, l, b - radius)
    lineTo(l, t + radius)
    arcTo(radius, radius, 0f, false, true, l + radius, t)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, false, cx + r, cy)
    arcTo(r, r, 0f, false, false, cx - r, cy)
    close()
}
