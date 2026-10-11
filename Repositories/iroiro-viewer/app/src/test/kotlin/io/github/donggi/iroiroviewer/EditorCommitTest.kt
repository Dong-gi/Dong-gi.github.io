package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.FileStamp
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 편집기의 저장 반영(`EditorSlots.commit`) — 반영의 답이 참인가, 반영마다 바이트가 따로인가, 세션의 수명. `commit` 자체는
 * 안드로이드(제공자·AtomicFile·큐)를 지나므로 그 판단과 기다림을 떼어 여기서 본다. 큐가 실제로 줄 선 요청만 거두는지는 계측 시험
 * (`FileOpQueueCancelTest` 의 `withdraw`)이 본다.
 */
class EditorCommitTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("editor-slot").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val original = "/storage/emulated/0/Docs/보고서.hwp"

    private fun finished(outcome: FileOpEngine.Outcome, extra: FileOpManager.Extra? = null) =
        FileOpManager.Finished("editor-1", FileOpManager.Kind.SAVE_DOC, outcome, extra)

    private fun session(token: String, openedAt: Long = 0, usedAt: Long = openedAt) = EditorSlots.Session(
        token = token,
        original = original,
        kind = "hwp",
        keptPrevious = false,
        stamp = FileStamp(1000, 5000),
        created = emptyMap(),
        saves = 0,
        last = null,
        openedAt = openedAt,
        usedAt = usedAt,
    )

    private fun token(n: Int) = n.toString(16).padStart(32, '0')

    // ---- M1 — 반영마다 따로 된 바이트 ------------------------------------------------------------------------------------------

    /**
     * 반영은 `incoming` 을 그 반영만의 파일로 옮긴 뒤 넣는다. 예전에는 모든 반영이 `incoming` 을 그대로 읽어, 앞 요청이 큐에 남은 채 다음 저장이
     * 그 파일을 자르고 다시 쓰면 앞 요청이 반쯤 쓴 문서를 원본 자리에 놓았다.
     */
    @Test
    fun eachCommitReadsItsOwnBytes() {
        val incoming = File(dir, "incoming").apply { writeBytes("첫 저장의 온전한 문서".toByteArray()) }
        val first = assertNotNull(EditorSlots.stage(incoming, "editor-1"))
        assertFalse(incoming.exists(), "다음 저장은 새 incoming 을 만든다")
        // 다음 저장이 incoming 을 처음부터 쓰는 중이다(제공자는 자르고 연다) — 앞 반영의 바이트는 그대로여야 한다.
        incoming.writeBytes("반쯤".toByteArray())
        assertContentEquals("첫 저장의 온전한 문서".toByteArray(), first.readBytes())
        val second = assertNotNull(EditorSlots.stage(incoming, "editor-2"))
        assertNotEquals(first, second)
        assertEquals(dir, first.parentFile, "같은 폴더 안의 rename 이라 원자적이다")
    }

    @Test
    fun nothingToStageIsNull() {
        assertNull(EditorSlots.stage(File(dir, "incoming"), "editor-1"))
    }

    // ---- M1 — 답은 참이어야 한다 ------------------------------------------------------------------------------------------------

    /** 줄 서서 기다리다 시간이 넘으면 줄에서 거두고 '바쁘다' 다 — 거둔 요청은 나중에 돌지 않는다(예전에는 '바쁘다' 고 답하고 큐에 남겨 뒀다). */
    @Test
    fun aQueuedCommitPastTheWaitIsWithdrawnAndAnsweredBusy() {
        val wait = EditorSlots.ResultWait()
        var withdrawn = false
        val result = wait.await(30) {
            withdrawn = true
            // 큐는 거둔 자리에서 곧바로 '취소' 를 낸다(`FileOpManager.withdraw`).
            wait.offer(finished(FileOpEngine.Outcome.Cancelled))
            true
        }
        assertTrue(withdrawn, "시간이 넘으면 줄에서 거둬야 한다")
        assertEquals(FileOpEngine.Outcome.Cancelled, result.outcome)
        assertEquals(EditorSlots.Reply(false, reason = EditorSlots.REASON_BUSY), EditorSlots.replyOf(result))
    }

    /** 이미 돌기 시작한 반영은 거두지 못한다 — 끝을 기다려 진짜 결과로 답한다('바쁘다' 고 해 놓고 원본을 바꾸지 않는다). */
    @Test
    fun aRunningCommitIsAnsweredWithItsRealResult() {
        val wait = EditorSlots.ResultWait()
        val saved = FileOpManager.Extra.DocSaved("/storage/emulated/0/Docs/보고서.hwp", FileStamp(10, 20), keptPrevious = true)
        val started = System.nanoTime()
        val worker = thread {
            Thread.sleep(300)
            wait.offer(finished(FileOpEngine.Outcome.Done(1, 0, 0), saved))
        }
        val result = wait.await(30) { false }
        worker.join()
        assertTrue((System.nanoTime() - started) / 1_000_000 >= 250, "끝날 때까지 기다려야 한다")
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), result.outcome)
        assertEquals(EditorSlots.Reply(true, name = "보고서.hwp"), EditorSlots.replyOf(result))
    }

    @Test
    fun aCommitThatEndsInTimeIsNotWithdrawn() {
        val wait = EditorSlots.ResultWait()
        wait.offer(finished(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, "보고서.hwp")))
        val result = wait.await(10_000) { fail("끝난 반영을 거두려 했다") }
        assertEquals(EditorSlots.Reply(false, reason = EditorSlots.REASON_SPACE), EditorSlots.replyOf(result))
    }

    /** 결과는 한 번만 받는다 — 거두기와 끝이 겹쳐도 먼저 온 것이 답이다. */
    @Test
    fun theFirstResultWins() {
        val wait = EditorSlots.ResultWait()
        wait.offer(finished(FileOpEngine.Outcome.Cancelled))
        wait.offer(finished(FileOpEngine.Outcome.Done(1, 0, 0)))
        assertEquals(FileOpEngine.Outcome.Cancelled, wait.await(10) { false }.outcome)
    }

    @Test
    fun repliesSayWhatHappened() {
        fun reason(r: FileOpEngine.Reason) = EditorSlots.replyOf(finished(FileOpEngine.Outcome.Failed(r, "x"))).reason
        assertEquals(EditorSlots.REASON_GONE, reason(FileOpEngine.Reason.NOT_FOUND))
        assertEquals(EditorSlots.REASON_PREVIOUS, reason(FileOpEngine.Reason.PREVIOUS_NOT_KEPT))
        assertEquals(EditorSlots.REASON_INVALID, reason(FileOpEngine.Reason.VERIFY_FAILED))
        assertEquals(EditorSlots.REASON_DENIED, reason(FileOpEngine.Reason.PERMISSION))
        assertEquals(EditorSlots.REASON_IO, reason(FileOpEngine.Reason.IO))
        // 끝났는데 쓴 파일이 없다 — 그럴 길은 없지만, 있다면 '저장했다' 를 말하지 않는다.
        assertEquals(EditorSlots.Reply(false, reason = EditorSlots.REASON_IO), EditorSlots.replyOf(finished(FileOpEngine.Outcome.Done(1, 0, 0))))
        // '세션 없음' 과 '원본 없음' 은 다른 글자다(편집기가 다른 문장을 보인다 — 다른 이름으로 저장해도 세션 없음은 같다).
        assertNotEquals(EditorSlots.REASON_GONE, EditorSlots.REASON_SESSION)
    }

    /** 세션이 닫히면 그 세션의 줄 선 반영을 꺼내 거둔다 — 다른 세션의 것은 두고. */
    @Test
    fun closingASessionDrainsOnlyItsCommits() {
        val p = EditorSlots.PendingCommits()
        p.add("t1", "a")
        p.add("t1", "b")
        p.add("t2", "c")
        p.remove("t1", "a")
        assertEquals(listOf("b"), p.drain("t1"))
        assertEquals(emptyList(), p.drain("t1"))
        // 꺼낸 뒤에 끝난 반영이 지우러 와도 아무 일 없다.
        p.remove("t1", "b")
        assertEquals(listOf("c"), p.drain("t2"))
    }

    // ---- m3 — 큐 안에서 견줄 표 ------------------------------------------------------------------------------------------------

    @Test
    fun plansCarryTheStampTheyExpect() {
        val s = session(token(1))
        val inPlace = assertNotNull(EditorSlots.plan(s, "hwp", "보고서.hwp") { s.stamp })
        assertEquals(s.stamp, inPlace.expected)
        val made = "/storage/emulated/0/Docs/보고서.hwpx"
        val withMade = s.copy(created = mapOf("보고서.hwpx" to EditorSlots.Created(made, FileStamp(10, 20))))
        val again = assertNotNull(EditorSlots.plan(withMade, "hwpx", "보고서.hwpx") { FileStamp(10, 20) })
        assertEquals(FileStamp(10, 20), again.expected)
    }

    // ---- m8 — 세션의 수명 ---------------------------------------------------------------------------------------------------

    /** 상한을 넘으면 가장 오래 **쓰이지 않은** 것부터 걷되, 편집기가 쥔 세션은 걷지 않는다. */
    @Test
    fun evictionSkipsHeldSessionsAndGoesByLastUse() {
        val sessions = (0 until 4).map { session(token(it), openedAt = it * 10L, usedAt = it * 10L) }
        assertEquals(listOf(token(0)), EditorSlots.evictable(sessions, emptySet(), 4))
        // 가장 먼저 연 세션을 다른 태스크의 편집기가 쥐고 있다 — 그다음 것을 걷는다(예전에는 연 때로 골라 그 편집기의 저장이 모두 실패했다).
        assertEquals(listOf(token(1)), EditorSlots.evictable(sessions, setOf(token(0)), 4))
        // 먼저 열었어도 방금 쓰인 세션은 남는다.
        val touched = sessions.map { if (it.token == token(0)) it.copy(usedAt = 1_000) else it }
        assertEquals(listOf(token(1)), EditorSlots.evictable(touched, emptySet(), 4))
        // 모두 쥐었으면 걷지 않는다 — 상한을 넘겨 둔다.
        assertEquals(emptyList(), EditorSlots.evictable(sessions, sessions.map { it.token }.toSet(), 4))
        assertEquals(emptyList(), EditorSlots.evictable(sessions, emptySet(), 8))
    }

    /** 시계가 뒤로 간 것은 지우는 까닭이 아니다. 나이는 마지막으로 쓰인 때로 잰다. */
    @Test
    fun ageIsMeasuredFromLastUseAndAClockGoingBackIsNotAge() {
        val day = 24L * 60 * 60 * 1000
        val s = session(token(1), openedAt = 100 * day, usedAt = 100 * day)
        assertFalse(EditorSlots.expired(s, 100 * day - 60_000), "시계를 1분 뒤로 돌렸다")
        assertFalse(EditorSlots.expired(s, 106 * day))
        assertTrue(EditorSlots.expired(s, 108 * day))
        assertFalse(EditorSlots.expired(s.copy(openedAt = 0), 106 * day), "연 지 오래여도 최근에 쓰였다")
    }

    // ---- 돌아왔을 때 한 번 말할 것 ---------------------------------------------------------------------------------------------

    @Test
    fun theNoticeOnReturn() {
        val pdf = "/storage/emulated/0/Docs/명세.pdf"
        val docx = "/storage/emulated/0/Docs/명세.docx"
        // PDF 를 고쳐 저장 — 곁에 새 .docx.
        assertEquals(
            EditorSlots.Notice("명세.docx", trashed = false),
            EditorSlots.noticeOf(EditorSlots.Summary(pdf, docx, 1, created = listOf(docx))),
        )
        // 제자리 저장 — 고치기 전 판을 휴지통에 남겼다(글 편집기처럼 한 번 말한다).
        assertEquals(
            EditorSlots.Notice(null, trashed = true),
            EditorSlots.noticeOf(EditorSlots.Summary(original, original, 2, trashed = true)),
        )
        // 새 파일을 만든 뒤 원본에 다시 저장했다 — 마지막이 원본이어도 만든 파일을 말한다.
        val hwpx = "/storage/emulated/0/Docs/보고서.hwpx"
        assertEquals(
            EditorSlots.Notice("보고서.hwpx", trashed = true),
            EditorSlots.noticeOf(EditorSlots.Summary(original, original, 3, created = listOf(hwpx), trashed = true)),
        )
        // 저장하지 않았다·'+ 새 문서' 의 제자리 저장(남길 판이 없었다) — 말할 것이 없다.
        assertNull(EditorSlots.noticeOf(EditorSlots.Summary(original, null, 0)))
        assertNull(EditorSlots.noticeOf(EditorSlots.Summary(original, original, 1)))
    }
}
