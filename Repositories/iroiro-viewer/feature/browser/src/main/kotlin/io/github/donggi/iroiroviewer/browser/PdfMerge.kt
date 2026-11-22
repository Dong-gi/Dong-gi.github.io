package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.PdfPageRanges
import io.github.donggi.iroiroviewer.io.PdfPagesSupport
import io.github.donggi.iroiroviewer.io.SessionPasswords
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import io.github.donggi.iroiroviewer.io.R as IoR

/*
 * 선택 메뉴의 **'PDF 합치기'** — 고른 PDF 여럿을 첫 파일 곁의 새 PDF 하나로(`첫이름_merged.pdf`). 차례는 **고른 차례**에서 시작하고
 * 대화상자의 화살표로 바꾼다. 짓고 쓰는 일은 파일 작업 큐(`FileOpManager.Request.PdfPages`)가 한다.
 */

internal object PdfMergeRules {

    /**
     * 합칠 것 — 둘 이상, [PdfPageRanges.MAX_MERGE_FILES] 이하, **지금 보이는** 파일이고 이름이 `.pdf`, 휴지통 밖. 아니면 null(메뉴 줄이 없다).
     * 차례는 고른 차례다([selection] 은 넣은 차례를 지키는 집합이다).
     */
    fun mergeable(selection: Set<String>, entries: List<FileEntry>): List<String>? {
        if (selection.size !in 2..PdfPageRanges.MAX_MERGE_FILES) return null
        val byPath = entries.associateBy { it.path }
        for (p in selection) {
            val e = byPath[p] ?: return null
            if (e.isDirectory || !PdfPageRanges.isPdfName(e.name) || TrashStore.isTrashPath(e.path)) return null
        }
        return selection.toList()
    }

    /** [i] 번째를 한 칸 옮긴다([delta] −1 위, +1 아래). 끝에서는 그대로. */
    fun moved(list: List<String>, i: Int, delta: Int): List<String> {
        val j = i + delta
        if (i !in list.indices || j !in list.indices) return list
        return list.toMutableList().apply { add(j, removeAt(i)) }
    }

    /**
     * 합칠 수 있는가 — 모두 읽혔고(사용자 암호 문서는 세션 암호로 풀려 쪽 수가 있다), 암호로 못 여는 방식이 없고, 쪽이 하나 이상이며 합이
     * [PdfPageRanges.MAX_PAGES] 이하. [Verdict.unlocks] 면 잠긴 것이 섞여 '잠기지 않은 PDF 로 저장합니다' 를 말한다. 아직 모르면 null.
     */
    fun verdict(inspections: Map<String, PdfPagesSupport.Inspection>, paths: List<String>): Verdict? {
        if (paths.any { it !in inspections }) return null
        val all = paths.map { inspections.getValue(it) }
        val readable = all.filterIsInstance<PdfPagesSupport.Inspection.Readable>()
        val blocked = readable.size != all.size || readable.any {
            it.lock == PdfPagesSupport.Lock.UNSUPPORTED || it.pageCount == null || it.pageCount == 0
        }
        val total = readable.sumOf { (it.pageCount ?: 0).toLong() }
        val tooMany = total > PdfPageRanges.MAX_PAGES
        val unlocks = readable.any { it.lock != PdfPagesSupport.Lock.NONE }
        return Verdict(!blocked && !tooMany, unlocks, tooMany)
    }

    data class Verdict(val ok: Boolean, val unlocks: Boolean, val tooMany: Boolean = false)
}

/**
 * 차례를 고르고 큐에 넣는다. 파일마다 쪽 수·잠금을 뒤에서 본다(쪽 나무만 — 객체를 다 훑지 않는다). 사용자 암호 문서는 **세션 암호**(그 파일을
 * 뷰어에서 암호로 연 것 — `SessionPasswords`)로 푼다. 없으면 그 줄이 '암호를 먼저 넣어 여세요' 로 막는다 — 대화상자에 암호 칸을 두지 않았다
 * (암호를 맞히는 일은 뷰어의 암호 화면이 이미 하고, 칸을 두면 그 암호를 또 한 곳에서 들어야 한다).
 */
@Composable
internal fun PdfMergeDialog(paths: List<String>, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val context = LocalContext.current
    var order by remember(paths) { mutableStateOf(paths) }
    // 세션 암호의 열쇠(파일의 신원) — 입출력 스레드에서 구해 둔다. 암호 자체는 들지 않는다(쓸 때 사본을 꺼낸다).
    val keys = remember(paths) { HashMap<String, String>() }
    // 세션 암호가 지워지면(앱이 화면에서 사라졌다) 처음부터 다시 살핀다 — 그동안 저장이 막히고, 잠긴 줄은 암호를 먼저 넣으라고 말한다.
    val clears by SessionPasswords.clears.collectAsState()
    val inspections by produceState(emptyMap<String, PdfPagesSupport.Inspection>(), paths, clears) {
        val engine = PdfPagesSupport.engine
        val out = LinkedHashMap<String, PdfPagesSupport.Inspection>()
        for (p in paths) {
            out[p] = withContext(IroDispatchers.io) {
                val job = coroutineContext.job
                val key = SessionPasswords.keyOf(File(p))
                synchronized(keys) { keys[p] = key }
                val password = SessionPasswords.get(key)
                try {
                    // 대화상자를 닫으면 나무 걷기도 멈춘다(취소를 넘긴다).
                    engine?.inspect(p, password) { if (!job.isActive) throw CancellationException() }
                        ?: PdfPagesSupport.Inspection.Unreadable
                } finally {
                    password?.fill('\u0000')
                }
            }
            value = LinkedHashMap(out)
        }
    }
    val verdict = PdfMergeRules.verdict(inspections, order)
    val name = PdfPageRanges.mergeName(File(order.first()).name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(IoR.string.io_pdf_merge_title)) },
        text = {
            Column {
                Text(stringResource(IoR.string.io_pdf_merge_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(order, key = { _, p -> p }) { i, p ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(File(p).name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                                Text(
                                    when (val x = inspections[p]) {
                                        null -> stringResource(IoR.string.io_pdf_merge_checking)
                                        PdfPagesSupport.Inspection.Unreadable -> stringResource(IoR.string.io_pdf_merge_unreadable)
                                        is PdfPagesSupport.Inspection.Readable -> when {
                                            x.lock == PdfPagesSupport.Lock.UNSUPPORTED ->
                                                stringResource(IoR.string.io_pdf_merge_lock_unsupported)
                                            x.pageCount == null -> stringResource(IoR.string.io_pdf_merge_locked)
                                            x.lock == PdfPagesSupport.Lock.PASSWORD ->
                                                stringResource(IoR.string.io_pdf_merge_pages_password, x.pageCount ?: 0)
                                            x.lock == PdfPagesSupport.Lock.OWNER_ONLY ->
                                                stringResource(IoR.string.io_pdf_merge_pages_unlock, x.pageCount ?: 0)
                                            else -> stringResource(IoR.string.io_pdf_merge_pages, x.pageCount ?: 0)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(enabled = i > 0, onClick = { order = PdfMergeRules.moved(order, i, -1) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, stringResource(IoR.string.io_pdf_merge_up))
                            }
                            IconButton(enabled = i < order.size - 1, onClick = { order = PdfMergeRules.moved(order, i, 1) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, stringResource(IoR.string.io_pdf_merge_down))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    verdict == null -> Unit
                    verdict.tooMany -> Text(
                        stringResource(IoR.string.io_pdf_merge_too_many, PdfPageRanges.MAX_PAGES),
                        color = MaterialTheme.colorScheme.error,
                    )
                    !verdict.ok -> Text(stringResource(IoR.string.io_pdf_merge_blocked), color = MaterialTheme.colorScheme.error)
                    else -> {
                        Text(stringResource(IoR.string.io_pdf_merge_name, name), style = MaterialTheme.typography.bodySmall)
                        if (verdict.unlocks) {
                            Text(stringResource(IoR.string.io_pdf_merge_unlock_notice), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = verdict?.ok == true,
                onClick = {
                    FileOpManager.enqueue(
                        context,
                        FileOpManager.Request.PdfPages(
                            id = UUID.randomUUID().toString(),
                            // 사용자 암호 문서는 세션 암호의 사본을 싣는다 — 큐가 쓰고 덮는다(돌지 않고 버려져도 덮는다).
                            parts = order.map { path ->
                                val locked = (inspections[path] as? PdfPagesSupport.Inspection.Readable)?.lock ==
                                    PdfPagesSupport.Lock.PASSWORD
                                val key = synchronized(keys) { keys[path] }
                                PdfPagesSupport.Part(path, null, password = if (locked && key != null) SessionPasswords.get(key) else null)
                            },
                            destDir = File(order.first()).parent.orEmpty(),
                            name = name,
                            merge = true,
                        ),
                    )
                    onQueued()
                },
            ) { Text(stringResource(IoR.string.io_pdf_merge_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_dialog_cancel)) }
        },
    )
}
