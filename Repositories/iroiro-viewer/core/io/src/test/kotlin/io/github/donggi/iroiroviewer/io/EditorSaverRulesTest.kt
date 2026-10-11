package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 편집기가 저장한 문서를 고쳐 쓰기 전에 고치기 전 판을 휴지통에 남기는가([EditorSaver.keepsPrevious]). 넣는 쪽은 넣기
 * 전에만 견줄 수 있다 — 반영이 큐에 줄 서 있는 사이 밖에서 바뀐 원본은 그 판을 남기지 않고 덮였다. 그래서 큐 안에서 다시 본다(글 저장
 * [TextSaver] 와 같은 자리). 휴지통에 실제로 남는지는 계측 시험(`EditorSaverTest`)이 본다.
 */
class EditorSaverRulesTest {

    private val seen = FileStamp(1000, 5000)
    private val changed = FileStamp(1200, 9000)

    @Test
    fun theFirstSaveOfASessionKeepsThePreviousVersion() {
        assertTrue(EditorSaver.keepsPrevious(keepPrevious = true, expected = seen, now = seen))
    }

    @Test
    fun aLaterSaveOfAnUnchangedFileDoesNotPileUpCopies() {
        assertFalse(EditorSaver.keepsPrevious(keepPrevious = false, expected = seen, now = seen))
    }

    /** 넣을 때는 같았는데 큐에서 차례를 기다리는 사이 다른 앱이 바꿨다 — 첫 저장이 아니어도 그 판을 남긴다. */
    @Test
    fun aChangeWhileQueuedIsKept() {
        assertTrue(EditorSaver.keepsPrevious(keepPrevious = false, expected = seen, now = changed))
    }

    /** 기대한 표를 모르면 남긴다 — 덮어 잃는 쪽으로 틀리지 않는다. */
    @Test
    fun anUnknownExpectationKeeps() {
        assertTrue(EditorSaver.keepsPrevious(keepPrevious = false, expected = null, now = seen))
    }

    /** 남길 것이 없다 — 없는 파일, 빈 파일('+ 새 문서' 의 빈 원본처럼). */
    @Test
    fun nothingToKeep() {
        assertFalse(EditorSaver.keepsPrevious(keepPrevious = true, expected = seen, now = null))
        assertFalse(EditorSaver.keepsPrevious(keepPrevious = true, expected = seen, now = FileStamp(0, 9000)))
    }
}
