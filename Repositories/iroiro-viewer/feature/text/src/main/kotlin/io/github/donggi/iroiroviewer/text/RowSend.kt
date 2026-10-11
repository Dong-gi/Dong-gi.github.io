package io.github.donggi.iroiroviewer.text

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.selection.SelectionState

/**
 * 본문에서 고른 글을 **다른 앱으로 보내는** 고르기 막대의 항목(번역·검색 같은 `PROCESS_TEXT`).
 *
 * ## Compose 의 항목을 쓰지 않는 까닭
 *
 * compose-foundation 1.12.1 의 고르기 막대는 `PROCESS_TEXT` 항목을 스스로 달고(`addProcessedTextContextMenuItems`), 누르면 **날것의
 * 선택**(행마다 이어 붙인 글 — 4,096자 경계의 줄바꿈과 줄 끝 표시까지)을 그대로 인텐트에 싣는다. 우리 복사의 상한과 잇기([RowCopy])는
 * 클립보드를 가로채는 데만 걸려 이 길을 지나지 않는다 — 미니파이 JS 130행쯤을 고르고 '번역' 을 누르면 엑스트라가 바인더 한도(1 MB)를
 * 넘어 `TransactionTooLargeException` 으로 **앱이 죽었다**. 그래서 그 항목을 걸러 내고(화면의 `ProcessTextKey` 거르기)
 * 같은 앱 목록으로 우리 항목을 단다 — [RowCopy.joinPrefix] 가 이은 깨끗한 글을 [MAX_CHARS] 자까지만 보내고, 보내기의 실패를 잡는다.
 * 보내는 쪽은 읽기 전용이라고 적는다(`EXTRA_PROCESS_TEXT_READONLY` — Compose 는 거꾸로 적는다).
 */
internal object RowSend {

    /** 보내는 글의 상한 — 크롬이 `PROCESS_TEXT` 로 보내는 양과 같다(10만 자 = 20만 바이트, 바인더 한도의 5분의 1). */
    const val MAX_CHARS = 100_000

    /** 보낸 결과 가운데 화면이 말할 것. 그대로 보냈으면 말하지 않는다. */
    sealed interface Outcome {
        /** 길어서 앞 [chars] 자만 보냈다. */
        data class Truncated(val chars: Int) : Outcome

        /** 받는 앱을 띄우지 못했다(없어졌다·거절했다·바인더). */
        data object Failed : Outcome
    }

    /**
     * 이 앱이 띄울 수 있는 `PROCESS_TEXT` 앱. Compose 와 같은 거름(내보냈고 권한이 맞는 것)이다.
     *
     * `PROCESS_TEXT` 를 `<queries>` 에 적지 않는다(lint `QueryPermissionsNeeded` 를 까닭을 적고 껐다). 이 질의는 Compose 의 항목이 하던
     * 것과 **같은 질의**라 보이는 앱도 같다 — 앱 전체의 `<queries>`(`core:io` 의 VIEW·`content`·모든 형식)로 보이는 앱 가운데 이것을 받는
     * 것과 늘 보이는 시스템 앱. 여기에 `PROCESS_TEXT` 를 적으면 목록이 넓어지는 대신 '글을 받는 앱' 이 설치돼 있는지를 이 앱이 알게 된다 —
     * 그 사생활의 값은 고침이 아니라 사용자가 정할 일이라 그대로 두었다.
     */
    @SuppressLint("QueryPermissionsNeeded")
    fun activities(context: Context): List<ResolveInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType(PLAIN)
        val all = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return all.filter { usable(context, it) }
    }

    private fun usable(context: Context, info: ResolveInfo): Boolean {
        val a = info.activityInfo ?: return false
        if (a.packageName == context.packageName) return true
        val permission = a.permission
        return a.exported && (permission == null || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
    }

    /**
     * 고른 것을 [info] 의 앱으로 보낸다. 길면 앞부분만 보내고 [Outcome.Truncated], 띄우지 못하면 [Outcome.Failed] 를 알린다 — 던지지 않는다
     * (고르기 막대의 누름은 우리가 잡지 않으면 앱을 끝낸다).
     */
    fun send(context: Context, info: ResolveInfo, selection: SelectionState, onOutcome: (Outcome) -> Unit) {
        val joined = RowCopy.joinPrefix(selection.selectedTexts, MAX_CHARS)
        if (joined.text.isEmpty()) return
        val a = info.activityInfo ?: return
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType(PLAIN)
            .setClassName(a.packageName, a.name)
            .putExtra(Intent.EXTRA_PROCESS_TEXT, joined.text)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        // 화면의 문맥은 보통 액티비티다. 아니면 새 태스크로 띄워야 한다(그러지 않으면 던진다).
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            // `ActivityNotFoundException`(그새 지워졌다)·`SecurityException`·바인더 한도(`TransactionTooLargeException` 을 싼 것) —
            // 상한 밑이어도 그 순간 다른 큰 전송이 버퍼를 쓰고 있으면 바인더가 거절한다.
            onOutcome(Outcome.Failed)
            return
        }
        if (joined.truncated) onOutcome(Outcome.Truncated(MAX_CHARS))
    }

    private const val PLAIN = "text/plain"
}

/** 우리가 단 '보내기' 항목의 열쇠. Compose 의 `ProcessTextKey` 와 겹치지 않게 따로 둔다(그것은 거른다). */
internal data class RowSendKey(val index: Int)

/**
 * 고르기 막대에 다른 앱으로 보내는 항목을 단다([RowSend]). 고른 것이 없으면 달지 않는다. 앱 목록은 막대를 열 때마다 묻는다(Compose 와
 * 같다 — 그 사이에 설치·삭제가 있을 수 있다).
 */
internal fun TextContextMenuBuilderScope.sendItems(
    context: Context,
    selection: SelectionState,
    onOutcome: (RowSend.Outcome) -> Unit,
) {
    if (selection.selectedTexts.isEmpty()) return
    val apps = try {
        RowSend.activities(context)
    } catch (e: RuntimeException) {
        // 패키지 관리자에 묻는 것도 바인더다. 못 물으면 항목 없이 연다.
        return
    }
    if (apps.isEmpty()) return
    separator()
    apps.forEachIndexed { i, info ->
        item(key = RowSendKey(i), label = info.loadLabel(context.packageManager).toString()) {
            RowSend.send(context, info, selection, onOutcome)
            close()
        }
    }
    separator()
}
