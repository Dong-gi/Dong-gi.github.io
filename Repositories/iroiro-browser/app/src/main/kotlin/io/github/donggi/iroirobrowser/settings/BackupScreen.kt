package io.github.donggi.iroirobrowser.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.core.backup.BackupException
import io.github.donggi.iroirobrowser.core.backup.BackupJson
import io.github.donggi.iroirobrowser.core.backup.BackupRules
import io.github.donggi.iroirobrowser.core.backup.ImportPlan
import io.github.donggi.iroirobrowser.ui.Ui
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 설정 › 백업 — 북마크·설정을 파일로 내보내고 가져온다(사용자 요청 2026-10-16 — '북마크·설정 내보내기/가져오기 => 구현해주세요'). 릴리스도 이 PC 의
 * 디버그 키로 서명하고 앱 자료는 백업·기기 이전에서 빠진다 — 키를 바꾸거나 다시 깔면 북마크·설정이 모두 사라진다. 그것을 옮기는 길이다.
 *
 * - 파일은 **SAF 로만** 고른다(`ACTION_CREATE_DOCUMENT`·`ACTION_OPEN_DOCUMENT` — 저장소 권한이 없다). 고르는 동안 화면이 다시 지어져도
 *   잃지 않게 내보내기의 고름(방문 기록·프록시 주소)은 요청 번호에 싣는다.
 * - **북마크는 설정 파일에 함께 든다** — 따로 내보내고 가져오는 단추(넷스케이프 북마크 HTML)는 두지 않는다(사용자 2026-10-16: '북마크
 *   내보내기/가져오기를 별도로 나누지 말고, 설정 내보내기/가져오기에 포함해주세요').
 * - 글을 짓고 읽고 견주는 일은 `:core` 의 `backup/`(BackupJson·ImportPlan), 상태를 모으고 거는 일은 [BackupData]. 여기는 화면·
 *   스레드다 — 파일 읽기·쓰기는 따로 된 스레드, 결과는 메인 스레드. 도는 동안 다른 백업 단추는 듣지 않는다.
 * - **가져오기는 무엇이 바뀔지 수로 묻고 나서 한다.** 설정 화면에 보이는 것이 바뀌면 이 화면을 다시 짓는다(`recreate` — 칸이 새 값을 보이게).
 *   그때 저장하지 않은 프록시 칸의 변경은 버려진다 — 확인 창이 그렇게 말한다.
 */
class BackupScreen(private val activity: Activity, private val isDirty: () -> Boolean) {

    private val app = WebApp.of(activity)
    private val context = activity.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 설정 화면의 단추 — (글, 할 일). */
    val actions: List<Pair<Int, () -> Unit>> = listOf(
        R.string.backup_export to { askExport() },
        R.string.backup_import to { pick(Intent.ACTION_OPEN_DOCUMENT, "*/*", null, REQ_IMPORT) },
    )

    /** 설정 화면의 `onActivityResult` 가 넘긴다 — 이 화면이 고르라고 한 것이면 true. */
    fun onResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val export = requestCode in REQ_EXPORT..(REQ_EXPORT or FLAG_HISTORY or FLAG_PROXY)
        if (!export && requestCode != REQ_IMPORT) return false
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return true
        if (requestCode == REQ_IMPORT) {
            importSettings(uri)
        } else {
            exportSettings(uri, history = (requestCode and FLAG_HISTORY) != 0, proxy = (requestCode and FLAG_PROXY) != 0)
        }
        return true
    }

    // ── 내보내기 ──────────────────────────────────────────────────────────────────────────────────────────────

    /** 넣을 것을 고르는 창 — 방문 기록과 프록시 주소는 고를 때만(기본은 둘 다 뺀다). */
    private fun askExport() {
        if (busy.get()) return
        val history = CheckBox(activity).apply { text = activity.getString(R.string.backup_with_history) }
        val proxy = CheckBox(activity).apply { text = activity.getString(R.string.backup_with_proxy) }
        val warning = TextView(activity).apply {
            text = activity.getString(R.string.backup_export_warning)
            textSize = 13f
            alpha = 0.75f
            setPadding(0, Ui.dp(activity, 8), 0, 0)
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0)
            addView(history, Ui.matchWidth())
            addView(proxy, Ui.matchWidth())
            addView(warning, Ui.matchWidth())
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.backup_export)
            .setView(box)
            .setPositiveButton(R.string.backup_export_go) { _, _ ->
                val flags = (if (history.isChecked) FLAG_HISTORY else 0) or (if (proxy.isChecked) FLAG_PROXY else 0)
                pick(Intent.ACTION_CREATE_DOCUMENT, "application/json", "web-backup-${LocalDate.now()}.json", REQ_EXPORT or flags)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportSettings(uri: Uri, history: Boolean, proxy: Boolean) {
        if (!busy.compareAndSet(false, true)) return
        app.library.exportRows(history) { (bookmarks, rows) ->
            val exported = BackupData.snapshot(app, proxy, bookmarks, if (history) rows else null)
            background(R.string.backup_write_failed, { write(uri, BackupJson.encode(exported.backup)) }) { done, error ->
                busy.set(false)
                when {
                    done == null -> toast(error)
                    history -> toast(context.getString(R.string.backup_exported_history, exported.bookmarks, exported.history))
                    else -> toast(context.getString(R.string.backup_exported, exported.bookmarks))
                }
            }
        }
    }

    // ── 가져오기 ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun importSettings(uri: Uri) {
        if (!busy.compareAndSet(false, true)) return
        background(R.string.backup_read_failed, {
            BackupJson.decode(String(readBytes(uri, BackupJson.MAX_BYTES), Charsets.UTF_8), System.currentTimeMillis())
        }) { decoded, error ->
            if (decoded == null) {
                busy.set(false)
                toast(error)
                return@background
            }
            app.library.knownUrls { (bookmarks, history) ->
                busy.set(false)
                confirmSettings(ImportPlan.of(BackupData.current(app, bookmarks, history), decoded.backup, decoded.skipped))
            }
        }
    }

    private fun confirmSettings(plan: ImportPlan) {
        if (!alive()) return
        if (plan.isEmpty) {
            toast(context.getString(R.string.backup_nothing))
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.backup_import)
            .setMessage(summary(plan))
            .setPositiveButton(R.string.backup_import_go) { _, _ -> applySettings(plan) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applySettings(plan: ImportPlan) {
        if (!busy.compareAndSet(false, true)) return
        val refresh = refreshesScreen(plan)
        BackupData.apply(app, plan)
        app.library.importRows(plan.bookmarks, plan.history) {
            busy.set(false)
            toast(context.getString(if (plan.needsPassword) R.string.backup_imported_password else R.string.backup_imported), long = true)
            if (refresh && alive()) activity.recreate()
        }
    }

    // ── 확인 창의 글 ──────────────────────────────────────────────────────────────────────────────────────────

    /** 바뀔 것을 한 줄씩 — 수와 경고만. */
    private fun summary(plan: ImportPlan): String {
        val lines = ArrayList<String>()
        bookmarksLine(plan)?.let { lines += it }
        if (plan.history.isNotEmpty()) lines += context.getString(R.string.backup_sum_history, plan.historyNew, plan.historyMerged)
        if (plan.historyTrimmed) lines += context.getString(R.string.backup_sum_history_cap, app.library.historyCap)
        if (plan.hidden.isNotEmpty()) lines += context.getString(R.string.backup_sum_hidden, plan.hidden.size)
        if (plan.popupSites.isNotEmpty()) lines += context.getString(R.string.backup_sum_popups, plan.popupSites.size)
        if (plan.allowedSites.isNotEmpty()) lines += context.getString(R.string.backup_sum_allowed, plan.allowedSites.size)
        if (plan.listsToAdd.isNotEmpty() || plan.listToggles.isNotEmpty()) {
            lines += context.getString(R.string.backup_sum_lists, plan.listsToAdd.size, plan.listToggles.size)
        }
        if (plan.settingsChanged > 0) lines += context.getString(R.string.backup_sum_settings, plan.settingsChanged)
        // 바로 가게 되는 곳은 이름으로 — 그곳에는 실제 IP 가 드러난다(빠지는 규칙은 프록시로 가는 쪽이라 바꾼다고만).
        val opened = plan.bypassAdded +
            listOfNotNull(
                context.getString(R.string.backup_bypass_private).takeIf { plan.privateOpened },
                context.getString(R.string.backup_bypass_simple).takeIf { plan.simpleOpened },
            )
        when {
            opened.isNotEmpty() -> lines += context.getString(R.string.backup_sum_bypass_added, names(opened))
            plan.bypassChanged -> lines += context.getString(R.string.backup_sum_bypass)
        }
        val proxy = plan.proxy
        if (plan.exitChanged && proxy != null) lines += context.getString(R.string.backup_sum_exit, brief(proxy.exitCheckUrl))
        if (plan.accountChanged && proxy != null) lines += context.getString(R.string.backup_sum_proxy, brief("${proxy.host}:${proxy.port}"))
        if (plan.needsPassword) lines += context.getString(R.string.backup_sum_password)
        if (plan.publicCidrs.isNotEmpty()) lines += context.getString(R.string.backup_sum_public, plan.publicCidrs.joinToString(", "))
        skippedLine(plan)?.let { lines += it }
        if (refreshesScreen(plan) && isDirty()) lines += context.getString(R.string.backup_sum_unsaved)
        return lines.joinToString("\n")
    }

    private fun bookmarksLine(plan: ImportPlan): String? = when {
        plan.bookmarksKnown > 0 -> context.getString(R.string.backup_sum_bookmarks_known, plan.bookmarks.size, plan.bookmarksKnown)
        plan.bookmarks.isNotEmpty() -> context.getString(R.string.backup_sum_bookmarks, plan.bookmarks.size)
        else -> null
    }

    private fun skippedLine(plan: ImportPlan): String? = if (plan.skipped > 0) context.getString(R.string.backup_sum_skipped, plan.skipped) else null

    /** 이름 다섯까지 — 넘으면 '… 외 N개'. */
    private fun names(list: List<String>): String {
        val shown = list.take(5).joinToString(", ") { brief(it) }
        return if (list.size <= 5) shown else context.getString(R.string.backup_sum_more, shown, list.size - 5)
    }

    /** 확인 창에 보일 만큼 — 긴 주소는 앞 80 글자와 '…'(서로게이트 쌍은 가르지 않는다). */
    private fun brief(text: String): String = if (text.length <= 80) text else BackupRules.clip(text, 80) + "…"

    /** 설정 화면에 보이는 것(프록시·예외 규칙 칸, 낱개 설정, 광고 차단 줄, 팝업 목록)이 바뀌는가 — 바뀌면 화면을 다시 짓는다. */
    private fun refreshesScreen(plan: ImportPlan): Boolean =
        plan.proxy != null || plan.settingsChanged > 0 || plan.popupSites.isNotEmpty() || plan.listsToAdd.isNotEmpty() || plan.listToggles.isNotEmpty()

    // ── 파일과 스레드 ─────────────────────────────────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION") // 플랫폼 Activity 의 결과 받기 — AndroidX 의 결과 API 는 새 의존이다(테더 화면·북마크 화면과 같다)
    private fun pick(action: String, type: String, title: String?, request: Int) {
        if (busy.get()) return
        val intent = Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType(type)
        if (title != null) intent.putExtra(Intent.EXTRA_TITLE, title)
        try {
            activity.startActivityForResult(intent, request)
        } catch (e: ActivityNotFoundException) {
            toast(context.getString(R.string.backup_no_picker))
        }
    }

    /**
     * [task] 를 따로 된 스레드에서 — 끝나면 메인 스레드에서 [done](값, 실패면 화면에 보일 글). 받지 않은 파일은 그 까닭([BackupException.reason]),
     * 그 밖의 실패(제공자의 입출력·권한·형식 — 무엇이 올지 모른다)는 [failure] 한 줄로. 앱을 죽이지 않는다.
     */
    private fun <T : Any> background(failure: Int, task: () -> T, done: (T?, String) -> Unit) {
        Thread({
            val (value, message) = try {
                task() to ""
            } catch (e: BackupException) {
                null to context.getString(reasonText(e.reason))
            } catch (e: OutOfMemoryError) {
                null to context.getString(R.string.backup_too_large)
            } catch (e: Exception) {
                null to context.getString(failure)
            }
            main.post { done(value, message) }
        }, "web-backup").apply { isDaemon = true }.start()
    }

    private fun reasonText(reason: BackupException.Reason): Int = when (reason) {
        BackupException.Reason.TOO_LARGE -> R.string.backup_too_large
        BackupException.Reason.NOT_BACKUP -> R.string.backup_not_backup
        BackupException.Reason.NEWER_VERSION -> R.string.backup_newer
        BackupException.Reason.MALFORMED -> R.string.backup_malformed
    }

    /** 파일을 [max] 바이트까지 — 넘으면 [BackupException.Reason.TOO_LARGE](끝까지 읽지 않는다). */
    private fun readBytes(uri: Uri, max: Int): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("no stream")
        input.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                if (out.size() + n > max) throw BackupException(BackupException.Reason.TOO_LARGE, "${max}바이트 넘음")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    /**
     * 고른 문서에 쓴다 — 'wt'(잘라 내고 쓰기)를 먼저, 그 꼴을 모르는 제공자면 'w'. 쓰다 실패하면 그 문서를 지운다 — 방금 만든 것이라 반쯤 쓴
     * 백업이 남지 않게(지우지 못하면 그대로 둔다).
     */
    private fun write(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val resolver = context.contentResolver
        try {
            val out = try {
                resolver.openOutputStream(uri, "wt")
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: FileNotFoundException) {
                null
            } ?: resolver.openOutputStream(uri, "w") ?: throw FileNotFoundException("no stream")
            out.use { it.write(bytes) }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
    }

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun toast(text: String, long: Boolean = false) {
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** 내보내기의 요청 번호 — 아래 두 비트가 고름(방문 기록·프록시 주소)이다. */
        const val REQ_EXPORT = 0x5B00
        const val FLAG_HISTORY = 1
        const val FLAG_PROXY = 2
        const val REQ_IMPORT = 0x5B10

        /** 프로세스에 하나 — 화면이 다시 지어져도 도는 일이 겹치지 않게. */
        val busy = AtomicBoolean(false)
    }
}
