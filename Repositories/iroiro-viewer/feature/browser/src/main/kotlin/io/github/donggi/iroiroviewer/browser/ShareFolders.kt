package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.model.FileEntry

/**
 * 공유에 폴더가 섞였을 때의 규칙. 화면은 이것을 부르기만 한다 — 순수 함수라 JVM 시험이 지킨다(`ShareFoldersTest`).
 *
 * 공유 인텐트는 파일만 싣는다. 예전에는 고른 것 가운데 폴더가 **말없이 빠졌다**. 이제 폴더가 있으면 묻는다 — 'ZIP으로 묶어 보내기'
 * (앱 캐시에 묶어 그 ZIP 하나를 보낸다 — `ShareZips`), 파일이 함께 있으면 '파일만 보내기'(폴더가 빠진다는 것을 문장이 말한다), 취소.
 */
internal object ShareFolders {

    /** 고른 것을 파일과 폴더로 가른 것. 차례는 고른 차례 그대로다 — [all] 은 둘을 가르기 전의 차례(ZIP 의 맨 위 차례). */
    data class Split(val files: List<String>, val folders: List<String>, val all: List<String>) {
        /** 물어야 하는가 — 폴더가 하나라도 있다. */
        val needsChoice: Boolean get() = folders.isNotEmpty()
    }

    /**
     * 가른다. 폴더인가는 **지금 보이는 목록**([entries])의 값을 먼저 쓴다(나열이 `lstat` 으로 정한 것 — 링크는 폴더가 아니다). 목록에 없는 것
     * (이름 필터에 가려진 채 골라 둔 것)만 [isDirectory] 로 묻는다 — 주 스레드에서 부르므로 수천 개를 고른 공유가 수천 번 묻지 않게.
     */
    fun split(selection: Collection<String>, entries: List<FileEntry>, isDirectory: (String) -> Boolean): Split {
        val known = HashMap<String, Boolean>(entries.size * 2)
        for (e in entries) known[e.path] = e.isDirectory
        val files = ArrayList<String>()
        val folders = ArrayList<String>()
        for (path in selection) {
            if (known[path] ?: isDirectory(path)) folders += path else files += path
        }
        return Split(files, folders, ArrayList(selection))
    }

    /** ZIP 에서 빠진 것의 갈래. */
    enum class LeftOut { SKIPPED, UNREADABLE }

    /** 말할 조각 — 0 인 것은 말하지 않는다('0개 건너뜀' 을 적지 않는다). 차례는 건너뜀 → 못 읽음. */
    fun leftOut(skipped: Int, unreadable: Int): List<Pair<LeftOut, Int>> = buildList {
        if (skipped > 0) add(LeftOut.SKIPPED to skipped)
        if (unreadable > 0) add(LeftOut.UNREADABLE to unreadable)
    }

    /** 묶어 보내기가 끝난 뒤 할 일. */
    enum class After {
        /** 공유 창을 띄운다. 말할 것이 없다. */
        SHARE,

        /** 공유 창을 띄우고, 담지 못한 것(건너뜀·못 읽음)을 말한다. */
        SHARE_WITH_SKIPS,

        /** 담을 것이 없었다(링크만 골랐다) — 보내지 않고 말한다. */
        NOTHING,

        /** 취소했다고 말한다. */
        CANCELLED,

        /** 실패의 까닭을 말한다. */
        FAILED,
    }

    /**
     * '위치 지우고 공유'([FileOpManager.Kind.SHARE_CLEAN])가 끝난 뒤 할 일. 만든 사본이 하나도 없으면(고른 것이 모두 영상·HEIC 따위) 보내지 않고
     * 말한다. 건너뛴 것(지울 수 없는 형식)과 못 읽은 것은 돌아와서 말한다.
     */
    fun afterClean(outcome: FileOpEngine.Outcome, extra: FileOpManager.Extra?): After = when (outcome) {
        is FileOpEngine.Outcome.Cancelled -> After.CANCELLED
        is FileOpEngine.Outcome.Failed -> After.FAILED
        is FileOpEngine.Outcome.Done -> when {
            extra !is FileOpManager.Extra.ShareCopies || extra.paths.isEmpty() -> After.NOTHING
            outcome.skipped > 0 || outcome.failed > 0 -> After.SHARE_WITH_SKIPS
            else -> After.SHARE
        }
    }

    /** 결과로 할 일을 정한다. */
    fun after(outcome: FileOpEngine.Outcome, extra: FileOpManager.Extra?): After = when (outcome) {
        is FileOpEngine.Outcome.Cancelled -> After.CANCELLED
        is FileOpEngine.Outcome.Failed -> After.FAILED
        is FileOpEngine.Outcome.Done -> when {
            extra !is FileOpManager.Extra.ShareReady -> After.NOTHING
            outcome.skipped > 0 || outcome.failed > 0 -> After.SHARE_WITH_SKIPS
            else -> After.SHARE
        }
    }
}
