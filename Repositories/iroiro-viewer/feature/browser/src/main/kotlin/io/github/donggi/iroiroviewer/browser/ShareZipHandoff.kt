package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.FileOpManager

/**
 * 묶어 보내기의 결과를 **파일 목록이 앞에 설 때까지** 들고 있는다.
 *
 * 작업 결과(`FileOpManager.results`)는 앞에 있는 `Root` 가 받는다 — 묶는 사이 사용자가 다른 앱에서 이 앱으로 파일을 열었으면(`OpenActivity`)
 * 그 화면이 받는다. 거기서 공유 창을 띄우면 남이 넘긴 파일을 보던 화면 위에 엉뚱한 공유 창이 뜬다. 그래서 그 화면은 여기 맡기고, 파일
 * 목록(`MainActivity` 의 `Root`)이 앞에 설 때 꺼내 공유 창을 띄운다(미뤄 둔 '빠진 것' 도 그때).
 *
 * `FileOpManager.redeliver` 로 돌려보내지 않는다 — 그 줄을 다른 앱에서 연 화면이 바로 다시 받아 같은 결과가 제자리를 돈다. 프로세스에
 * 하나이고 메모리에만 있다(ZIP 은 `ShareZips` 가 띄우기 전까지 지우지 않는다).
 */
object ShareZipHandoff {

    private val held = ArrayDeque<FileOpManager.Finished>()

    /** 맡긴다. */
    fun hold(finished: FileOpManager.Finished) {
        synchronized(held) { held.addLast(finished) }
    }

    /** 가장 먼저 맡긴 것 하나. 없으면 null. 하나씩 꺼낸다 — 공유 창을 띄우면 꺼내는 쪽이 멈추므로 나머지는 다음에 선 때 꺼낸다. */
    fun poll(): FileOpManager.Finished? = synchronized(held) { held.removeFirstOrNull() }
}
