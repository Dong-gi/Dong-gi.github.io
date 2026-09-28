package io.github.donggi.iroiroviewer.io

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 휴지통 예약 작업의 판단, 풀기 결과의 '열기', 되돌릴 때의 이름 규칙.
 */
class TrashJobAndResultTest {

    // ---- 예약 --------------------------------------------------------------------

    private val wanted = TrashPurgeJob.Spec(
        service = "io.github.donggi.iroiroviewer.io.TrashPurgeJobService",
        intervalMs = TrashPurgeJob.PERIOD_MS,
        flexMs = TrashPurgeJob.FLEX_MS,
        persisted = true,
        requiresIdle = true,
        requiresBatteryNotLow = true,
    )

    @Test
    fun 걸린_것이_없으면_건다() {
        assertTrue(TrashPurgeJob.needsSchedule(null, wanted))
    }

    @Test
    fun 같은_모양이_걸려_있으면_다시_걸지_않는다() {
        // 다시 걸면 주기의 시계가 처음부터 간다. 앱을 하루에 한 번보다 자주 켜면 영영 돌지 않는다.
        assertFalse(TrashPurgeJob.needsSchedule(wanted.copy(), wanted))
    }

    @Test
    fun 모양이_바뀌었으면_다시_건다() {
        assertTrue(TrashPurgeJob.needsSchedule(wanted.copy(intervalMs = wanted.intervalMs / 2), wanted))
        assertTrue(TrashPurgeJob.needsSchedule(wanted.copy(persisted = false), wanted))
        assertTrue(TrashPurgeJob.needsSchedule(wanted.copy(service = "옛.서비스"), wanted))
    }

    @Test
    fun 주기와_여유는_시스템이_받는_범위_안이다() {
        // JobInfo 는 주기 15분 미만·여유 5분 미만을 스스로 늘린다 — 늘어나면 걸린 것과 원하는 것이 달라져
        // 앱을 켤 때마다 다시 걸게 된다.
        assertTrue(TrashPurgeJob.PERIOD_MS >= 15 * 60 * 1000L)
        assertTrue(TrashPurgeJob.FLEX_MS >= 5 * 60 * 1000L)
        assertTrue(TrashPurgeJob.FLEX_MS <= TrashPurgeJob.PERIOD_MS)
        assertTrue(TrashPurgeJob.FLEX_MS >= TrashPurgeJob.PERIOD_MS / 20)
        // 멈춤을 받았을 때 다시 걸어 달라고 하지 않는다 — 주기 작업은 다음 주기에 다시 돈다.
        assertFalse(TrashPurgeJob.RESCHEDULE_ON_STOP)
    }

    // ---- 풀기 결과의 '열기' --------------------------------------------------------

    private val dest = "/storage/emulated/0/Download/만화"
    private fun extracted(destDir: String = dest) = FileOpManager.Extra.Extracted(ExtractReport(destDir))

    @Test
    fun 다_풀었으면_그_폴더를_연다() {
        val done = FileOpEngine.Outcome.Done(12, 0, 0)
        assertEquals(dest, ExtractResults.folderToOpen(FileOpManager.Kind.EXTRACT, done, extracted()))
        val f = FileOpManager.Finished("id", FileOpManager.Kind.EXTRACT, done, extracted())
        assertEquals(dest, f.folderToOpen)
    }

    @Test
    fun 조금이라도_풀었으면_연다() {
        val partial = FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, "a.zip", done = 3)
        assertEquals(dest, ExtractResults.folderToOpen(FileOpManager.Kind.EXTRACT, partial, extracted()))
        assertEquals(dest, ExtractResults.folderToOpen(FileOpManager.Kind.EXTRACT, FileOpEngine.Outcome.Cancelled, extracted()))
    }

    @Test
    fun 하나도_못_풀고_실패했으면_열기를_보이지_않는다() {
        // 그때 실린 곳은 목적지를 정하기 전의 부모 폴더일 수 있다 — '푼 것을 본다' 가 거짓이 된다.
        val failed = FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, "a.zip")
        assertNull(ExtractResults.folderToOpen(FileOpManager.Kind.EXTRACT, failed, extracted()))
    }

    @Test
    fun 풀기가_아니거나_목적지가_없으면_열기가_없다() {
        val done = FileOpEngine.Outcome.Done(1, 0, 0)
        assertNull(ExtractResults.folderToOpen(FileOpManager.Kind.COPY, done, extracted()))
        assertNull(ExtractResults.folderToOpen(FileOpManager.Kind.EXTRACT, done, null))
        assertNull(ExtractResults.folderToOpen(FileOpManager.Kind.EXTRACT, done, extracted("")))
        assertNull(ExtractResults.folderToOpen(FileOpManager.Kind.EXTRACT, done, FileOpManager.Extra.Trashed(listOf("u"))))
    }

    // ---- 되돌릴 때의 이름 ----------------------------------------------------------

    private val tmp: File = Files.createTempDirectory("restore-name").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    @Test
    fun 원래_이름을_그대로_쓴다_시험용_빈_파일은_남지_않는다() {
        assertEquals("사진 01.jpg", TrashStore.restoreNameIn(tmp, "사진 01.jpg"))
        // 만들어 보고 지운다 — 사용자의 폴더에 빈 파일을 남기면 그것이 곧 이름 충돌이 된다.
        assertFalse(File(tmp, "사진 01.jpg").exists())
        assertEquals(0, tmp.list()!!.size)
    }

    @Test
    fun 이미_있는_이름도_그대로다_겹침은_부르는_쪽이_본다() {
        File(tmp, "있는.txt").writeText("x")
        assertEquals("있는.txt", TrashStore.restoreNameIn(tmp, "있는.txt"))
    }

    @Test
    fun 구분자가_든_이름은_다른_폴더로_새지_않는다() {
        // 기록이 적은 이름을 믿지 않는다. 그 폴더 안에 `a` 가 있으면 `a/b` 는 **그 안에** 만들어진다.
        File(tmp, "a").mkdir()
        assertEquals("a_b", TrashStore.restoreNameIn(tmp, "a/b"))
        assertFalse(File(tmp, "a/b").exists())
        assertEquals("이름없음", TrashStore.restoreNameIn(tmp, ".."))
    }
}
