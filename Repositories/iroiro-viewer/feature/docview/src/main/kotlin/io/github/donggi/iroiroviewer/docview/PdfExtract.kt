package io.github.donggi.iroiroviewer.docview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.docview.pdf.edit.PdfPagesEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.PdfPageRanges
import io.github.donggi.iroiroviewer.io.PdfPagesSupport
import io.github.donggi.iroiroviewer.io.SessionPasswords
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/*
 * PDF 뷰어 ⋮ 의 **'쪽 뽑기'** — 고른 쪽을 원본 곁의 새 PDF 로(`이름_p1-3_7.pdf`). 짓고 쓰는 일은 파일 작업 큐가 한다
 * (`FileOpManager.Request.PdfPages` — 진행·취소·결과는 다른 새 파일 쓰기와 같은 막대·알림·스낵바). 읽기 전용 사본(다른 앱이 준 것)에는
 * 줄이 없다 — 곁에 쓰면 사용자가 찾아갈 수 없는 앱 캐시다.
 */

/** 대화상자가 말할 것 — 판단만(JVM 시험). */
internal sealed interface ExtractCheck {
    /** 아직 파일을 보는 중. */
    data object Checking : ExtractCheck

    /** 뽑을 수 없다 — [why]. */
    data class Blocked(val why: Why) : ExtractCheck

    /** 뽑을 수 있다. [unlocks] 면 잠긴 문서라 '잠기지 않은 PDF 로 저장합니다' 를 저장 전에 말한다. */
    data class Ready(val unlocks: Boolean) : ExtractCheck

    enum class Why {
        /** 구조를 읽지 못했거나 화면과 쪽 수가 다르다. */
        UNREADABLE,

        /** 사용자 암호 문서인데 세션 암호가 없다(앱이 화면에서 사라지면 지워진다) — 다시 넣어 열어야 한다. */
        NEEDS_PASSWORD,

        /** 암호로는 열 수 없는 방식(인증서·전용 처리기). */
        LOCK_UNSUPPORTED,
    }

    companion object {
        /** 엔진이 본 것([inspection])과 화면이 센 쪽 수([pageCount]). 둘이 다르면 고른 번호가 다른 쪽일 수 있어 막는다. */
        fun of(inspection: PdfPagesSupport.Inspection?, pageCount: Int): ExtractCheck = when (inspection) {
            null -> Checking
            PdfPagesSupport.Inspection.Unreadable -> Blocked(Why.UNREADABLE)
            is PdfPagesSupport.Inspection.Readable -> when {
                inspection.lock == PdfPagesSupport.Lock.UNSUPPORTED -> Blocked(Why.LOCK_UNSUPPORTED)
                inspection.lock == PdfPagesSupport.Lock.PASSWORD && inspection.pageCount == null -> Blocked(Why.NEEDS_PASSWORD)
                inspection.pageCount != pageCount -> Blocked(Why.UNREADABLE)
                else -> Ready(unlocks = inspection.lock != PdfPagesSupport.Lock.NONE)
            }
        }
    }
}

/**
 * 쪽 범위를 묻고 큐에 넣는다. 처음 값은 지금 보는 쪽이다.
 *
 * @param pageCount 화면(플랫폼)이 센 쪽 수 — 엔진이 다르게 세면 막는다(요청에도 실어 큐 안에서 다시 본다).
 */
@Composable
internal fun PdfExtractDialog(path: String, pageCount: Int, currentPage: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember(path) { mutableStateOf((currentPage + 1).toString()) }
    // 세션 암호의 열쇠(파일의 신원 — 경로가 아니다). 입출력 스레드에서 한 번 구해 둔다.
    var passwordKey by remember(path) { mutableStateOf<String?>(null) }
    // 세션 암호가 지워지면(앱이 화면에서 사라졌다) 다시 살핀다 — 살피는 동안은 저장이 막히고, 암호가 없으면 그렇게 말한다.
    val clears by SessionPasswords.clears.collectAsState()
    val inspection by produceState<PdfPagesSupport.Inspection?>(null, path, clears) {
        value = withContext(IroDispatchers.io) {
            val job = coroutineContext.job
            val key = SessionPasswords.keyOf(File(path)).also { passwordKey = it }
            // 사본을 꺼내 쓰고 곧바로 덮는다(대화상자가 암호를 들고 있지 않는다).
            val password = SessionPasswords.get(key)
            try {
                // 대화상자를 닫으면 나무 걷기도 멈춘다(취소를 넘긴다).
                PdfPagesEngine.inspect(path, password) { if (!job.isActive) throw CancellationException() }
            } finally {
                password?.fill('\u0000')
            }
        }
    }
    val check = ExtractCheck.of(inspection, pageCount)
    val parsed = PdfPageRanges.parse(text, pageCount)
    val pages = (parsed as? PdfPageRanges.Parsed.Pages)?.pages
    val source = File(path)
    val name = pages?.let { PdfPageRanges.extractName(source.name, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_extract_pages)) },
        text = {
            Column {
                when (check) {
                    ExtractCheck.Checking -> Text(stringResource(R.string.doc_extract_checking))
                    is ExtractCheck.Blocked -> Text(
                        stringResource(
                            when (check.why) {
                                ExtractCheck.Why.NEEDS_PASSWORD -> R.string.doc_extract_locked
                                ExtractCheck.Why.LOCK_UNSUPPORTED -> R.string.doc_extract_lock_unsupported
                                ExtractCheck.Why.UNREADABLE -> R.string.doc_extract_unreadable
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                    is ExtractCheck.Ready -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v -> text = v.take(PdfPageRanges.MAX_INPUT) },
                            label = { Text(stringResource(R.string.doc_extract_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            singleLine = true,
                            isError = parsed is PdfPageRanges.Parsed.Error,
                            supportingText = {
                                Text(
                                    when (parsed) {
                                        is PdfPageRanges.Parsed.Pages ->
                                            stringResource(R.string.doc_extract_summary, parsed.pages.size, name.orEmpty())
                                        is PdfPageRanges.Parsed.Error -> when (parsed.kind) {
                                            PdfPageRanges.ErrorKind.EMPTY -> stringResource(R.string.doc_extract_error_empty)
                                            PdfPageRanges.ErrorKind.SYNTAX -> stringResource(R.string.doc_extract_error_syntax)
                                            PdfPageRanges.ErrorKind.OUT_OF_RANGE ->
                                                stringResource(R.string.doc_extract_error_range, pageCount)
                                            PdfPageRanges.ErrorKind.REVERSED -> stringResource(R.string.doc_extract_error_reversed)
                                            PdfPageRanges.ErrorKind.TOO_MANY ->
                                                stringResource(R.string.doc_extract_error_too_many, PdfPageRanges.MAX_PAGES)
                                        }
                                    },
                                )
                            },
                        )
                        if (check.unlocks) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.doc_extract_unlock_notice), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = check is ExtractCheck.Ready && pages != null && name != null,
                onClick = {
                    if (pages != null && name != null) {
                        // 사용자 암호 문서면 세션 암호의 사본을 요청에 싣는다 — 큐가 쓰고 덮는다(돌지 않고 버려져도 덮는다).
                        val password = (inspection as? PdfPagesSupport.Inspection.Readable)
                            ?.takeIf { it.lock == PdfPagesSupport.Lock.PASSWORD }
                            ?.let { passwordKey?.let(SessionPasswords::get) }
                        FileOpManager.enqueue(
                            context,
                            FileOpManager.Request.PdfPages(
                                id = UUID.randomUUID().toString(),
                                parts = listOf(PdfPagesSupport.Part(path, ArrayList(pages), expectedCount = pageCount, password = password)),
                                destDir = source.parent.orEmpty(),
                                name = name,
                                merge = false,
                            ),
                        )
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.doc_extract_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_cancel)) }
        },
    )
}
