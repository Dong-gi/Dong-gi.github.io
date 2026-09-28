package io.github.donggi.iroiroviewer.io

import android.os.FileObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 폴더 감시의 순수 규칙 — 어떤 알림으로 다시 읽는가, 몰려 오는 알림을 어떻게 묶는가, 돌아왔을 때 다시 읽는가.
 *
 * `FileObserver` 의 상수는 컴파일 때 박히는 값이라 JVM 에서도 그대로 쓴다(메서드를 부르지 않는다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderWatchTest {

    // ---- 어떤 알림인가 -----------------------------------------------------------

    @Test
    fun 새_파일과_지운_파일과_옮긴_파일은_다시_읽는다() {
        assertTrue(WatchRules.isRelevant(FileObserver.CREATE, "사진.jpg"))
        assertTrue(WatchRules.isRelevant(FileObserver.DELETE, "사진.jpg"))
        assertTrue(WatchRules.isRelevant(FileObserver.MOVED_FROM, "사진.jpg"))
        assertTrue(WatchRules.isRelevant(FileObserver.MOVED_TO, "사진.jpg"))
        assertTrue(WatchRules.isRelevant(FileObserver.CLOSE_WRITE, "받는중.zip"))
    }

    @Test
    fun 쓰는_도중의_조각_알림은_듣지_않는다() {
        // MODIFY 는 1 GB 를 받는 동안 수만 번 온다. 끝은 CLOSE_WRITE 가 한 번 알린다.
        assertFalse(WatchRules.isRelevant(FileObserver.MODIFY, "받는중.zip"))
        assertFalse(WatchRules.isRelevant(FileObserver.ACCESS, "사진.jpg"))
        assertFalse(WatchRules.isRelevant(FileObserver.OPEN, "사진.jpg"))
    }

    @Test
    fun 우리_임시_파일은_듣지_않는다() {
        val part = "${AtomicFileWriter.PART_PREFIX}1a2b3c4d${AtomicFileWriter.PART_SUFFIX}"
        assertFalse(WatchRules.isRelevant(FileObserver.CREATE, part))
        assertFalse(WatchRules.isRelevant(FileObserver.CLOSE_WRITE, part))
        assertFalse(WatchRules.isRelevant(FileObserver.DELETE, part))
        // 같은 접두어라도 우리 꼴(끝이 .part)이 아니면 사용자의 파일이다.
        assertTrue(WatchRules.isRelevant(FileObserver.CREATE, "${AtomicFileWriter.PART_PREFIX}메모.txt"))
    }

    @Test
    fun 휴지통_폴더가_생긴_것은_듣지_않는다() {
        assertFalse(WatchRules.isRelevant(FileObserver.CREATE, TrashStore.DIR_NAME))
    }

    @Test
    fun 폴더에_대한_알림은_깃발이_섞여_와도_듣는다() {
        // 커널은 폴더에 대한 알림에 IN_ISDIR(0x40000000)을 섞어 보낸다.
        val isDir = 0x40000000
        assertTrue(WatchRules.isRelevant(FileObserver.CREATE or isDir, "새 폴더"))
        assertTrue(WatchRules.isRelevant(FileObserver.DELETE or isDir, "새 폴더"))
    }

    @Test
    fun 보고_있는_폴더_자신이_사라지면_다시_읽는다() {
        assertTrue(WatchRules.isRelevant(FileObserver.DELETE_SELF, null))
        assertTrue(WatchRules.isRelevant(FileObserver.MOVE_SELF, null))
        // 폴더 자신의 시각만 바뀐 것은 다시 읽을 까닭이 아니다.
        assertFalse(WatchRules.isRelevant(FileObserver.ATTRIB, null))
    }

    // ---- 어떻게 묶는가 -----------------------------------------------------------

    @Test
    fun 마감은_조용함과_최대_기다림_가운데_이른_쪽이다() {
        assertEquals(1_100L, SettleRule.deadline(burstStart = 0, lastEvent = 400, quietMs = 700, maxWaitMs = 3_000))
        assertEquals(3_000L, SettleRule.deadline(burstStart = 0, lastEvent = 2_800, quietMs = 700, maxWaitMs = 3_000))
    }

    @Test
    fun 몰려_온_알림은_한_번으로_묶인다() = runTest {
        val src = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val fired = mutableListOf<Long>()
        val job = launch { src.settle(700, 3_000) { testScheduler.currentTime }.collect { fired += testScheduler.currentTime } }
        runCurrent()
        // 0·100·200·300·400 ms 에 다섯 번.
        repeat(5) {
            src.emit(Unit)
            advanceTimeBy(100)
        }
        advanceTimeBy(5_000)
        job.cancel()
        assertEquals(listOf(1_100L), fired)
    }

    @Test
    fun 알림이_끊이지_않아도_최대_기다림마다_내보낸다() = runTest {
        // 흔한 디바운스는 사건이 끊이지 않으면 영영 내보내지 않는다 — 다른 앱이 사진을 한 장씩 옮겨 넣는 동안
        // 목록이 한 번도 바뀌지 않는다.
        val src = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val fired = mutableListOf<Long>()
        val job = launch { src.settle(700, 3_000) { testScheduler.currentTime }.collect { fired += testScheduler.currentTime } }
        runCurrent()
        // 0 ~ 4900 ms 동안 350 ms 마다.
        repeat(15) {
            src.emit(Unit)
            advanceTimeBy(350)
        }
        advanceTimeBy(5_000)
        job.cancel()
        // 첫 묶음은 3000 ms(최대 기다림), 둘째 묶음은 3150 ms 에 시작해 마지막(4900) 뒤 700 ms.
        assertEquals(listOf(3_000L, 5_600L), fired)
    }

    @Test
    fun 위쪽이_끝나면_남은_묶음을_내보내고_끝난다() = runTest {
        assertEquals(1, flowOf(Unit, Unit, Unit).settle(700, 3_000) { testScheduler.currentTime }.toList().size)
        assertEquals(0, emptyFlow<Unit>().settle(700, 3_000) { testScheduler.currentTime }.toList().size)
    }

    // ---- 돌아왔을 때 -------------------------------------------------------------

    private val folder = "/storage/emulated/0/Download"

    @Test
    fun 읽은_적이_없거나_다른_폴더면_다시_읽지_않는다() {
        val now = DirStamp(100, 0)
        assertFalse(ForegroundRelist.shouldRelist(null, folder, now))
        assertFalse(ForegroundRelist.shouldRelist(LastListed("/storage/emulated/0", DirStamp(1, 0)), folder, now))
        assertFalse(ForegroundRelist.shouldRelist(LastListed(folder, now), null, now))
    }

    @Test
    fun 이_폴더를_이미_읽는_중이면_다시_읽지_않는다() {
        // 첫 화면에서 전에 봤던 폴더로 다시 들어오면 옛 기록과 값이 다르지만, 새 읽기가 이미 돌고 있다.
        val last = LastListed(folder, DirStamp(100, 0))
        assertFalse(ForegroundRelist.shouldRelist(last, folder, DirStamp(200, 0), inFlight = true))
        assertTrue(ForegroundRelist.shouldRelist(last, folder, DirStamp(200, 0), inFlight = false))
    }

    @Test
    fun 수정_시각이_같으면_다시_읽지_않는다() {
        assertFalse(ForegroundRelist.shouldRelist(LastListed(folder, DirStamp(100, 5)), folder, DirStamp(100, 5)))
    }

    @Test
    fun 같은_초_안의_변화도_잡는다() {
        // 초 단위(`st_mtime`)로 견주면 읽은 뒤 1초 안에 생긴 파일을 놓친다.
        assertTrue(ForegroundRelist.shouldRelist(LastListed(folder, DirStamp(100, 5)), folder, DirStamp(100, 900_000_000)))
    }

    @Test
    fun 못_읽은_폴더도_수정_시각이_그대로면_돌아와서_다시_읽지_않는다() {
        // 막힌 폴더(Android/data)는 stat 은 되고 나열만 막힌다. 실패의 시각을 버리면(null) 지금 값과 늘 달라
        // 화면으로 돌아올 때마다 바뀐 것 없이 다시 읽는다.
        val stamp = DirStamp(100, 5)
        val failed = ForegroundRelist.lastListedOf(folder, DirectoryLister.Listing.Failed(DirectoryLister.Reason.LOCKED, stamp))
        assertEquals(LastListed(folder, stamp), failed)
        assertFalse(ForegroundRelist.shouldRelist(failed, folder, stamp))
        assertTrue(ForegroundRelist.shouldRelist(failed, folder, DirStamp(101, 0)))

        val ready = DirectoryLister.Listing.Ready(emptyList(), hiddenCount = 0, scanMillis = 0, stamp = stamp)
        assertEquals(LastListed(folder, stamp), ForegroundRelist.lastListedOf(folder, ready))
        // 아직 읽는 중이면 견줄 것이 없다 — 앞의 기록을 덮지 않는다.
        assertNull(ForegroundRelist.lastListedOf(folder, DirectoryLister.Listing.Scanning(500)))
    }

    @Test
    fun 폴더가_생기거나_사라지면_다시_읽는다() {
        assertTrue(ForegroundRelist.shouldRelist(LastListed(folder, null), folder, DirStamp(100, 0)))
        assertTrue(ForegroundRelist.shouldRelist(LastListed(folder, DirStamp(100, 0)), folder, null))
        // 없던 것이 여전히 없다.
        assertFalse(ForegroundRelist.shouldRelist(LastListed(folder, null), folder, null))
    }
}
