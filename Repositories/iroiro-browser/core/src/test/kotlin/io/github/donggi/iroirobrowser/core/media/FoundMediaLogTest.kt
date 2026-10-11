package io.github.donggi.iroirobrowser.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FoundMediaLogTest {

    private fun FoundMediaLog.currentUrls(now: Long = 0) = listing(now).current.map { it.url }

    private fun FoundMediaLog.previousUrls(now: Long = 0) = listing(now).previous.map { s -> s.rows.map { it.url } }

    /** 첫 페이지를 커밋해 둔다. */
    private fun opened(url: String = "https://v.test/watch?v=1", title: String = "첫 영상"): FoundMediaLog =
        FoundMediaLog().apply { commit(url, title, 100, sameDocument = false, navigationStart = 0) }

    @Test
    fun SPA_이동은_목록을_지우지_않고_경계만_옮긴다() {
        val log = opened()
        assertEquals(FoundMediaLog.Change.NEW, log.found("https://cdn.test/a/master.m3u8", MediaKind.HLS, 200))
        // 유튜브 모바일의 다음 영상 — 같은 문서 안의 pushState.
        assertTrue(log.commit("https://v.test/watch?v=2", "둘째 영상", 300, sameDocument = true, navigationStart = null))
        log.found("https://cdn.test/b/master.m3u8", MediaKind.HLS, 400)
        val l = log.listing(500)
        assertEquals(listOf("https://cdn.test/b/master.m3u8"), l.current.map { it.url })
        assertEquals(1, l.previous.size)
        assertEquals("첫 영상", l.previous[0].page.title)
        assertEquals("https://v.test/watch?v=1", l.previous[0].page.url)
        assertEquals(300L, l.previous[0].page.left)
        assertEquals(listOf("https://cdn.test/a/master.m3u8"), l.previous[0].rows.map { it.url })
        // 항목은 찾은 페이지의 주소·제목을 든다(받을 때의 Referer·이름).
        assertEquals("https://v.test/watch?v=1", l.previous[0].rows[0].entry.pageUrl)
        assertEquals("둘째 영상", l.current[0].entry.pageTitle)
        assertEquals(1, log.currentCount())
    }

    @Test
    fun 항목은_찾은_때의_제목을_든다() {
        val log = opened(title = "")
        log.found("https://cdn.test/a.mp4", MediaKind.VIDEO, 200)
        // 제목이 찾은 뒤에 왔다 — 채운다.
        log.retitle("첫 영상")
        // SPA 가 pushState 전에 제목부터 바꿨다 — 앞 항목의 제목은 그대로다.
        log.retitle("둘째 영상")
        log.commit("https://v.test/watch?v=2", "둘째 영상", 300, sameDocument = true, navigationStart = null)
        log.found("https://cdn.test/b.mp4", MediaKind.VIDEO, 400)
        val l = log.listing(500)
        assertEquals("둘째 영상", l.current.single().entry.pageTitle)
        assertEquals("첫 영상", l.previous.single().rows.single().entry.pageTitle)
        // 앞 페이지의 것이 다시 요청되면 지금 페이지의 제목으로.
        log.found("https://cdn.test/a.mp4", MediaKind.VIDEO, 600)
        assertEquals(listOf("둘째 영상", "둘째 영상"), log.listing(700).current.map { it.entry.pageTitle })
    }

    @Test
    fun 조각만_바뀐_이동은_경계가_아니다() {
        val log = opened()
        log.found("https://cdn.test/clip.mp4", MediaKind.VIDEO, 200)
        assertFalse(log.commit("https://v.test/watch?v=1#comments", "첫 영상", 300, sameDocument = true, navigationStart = null))
        // 탐색 알림이 없는 WebView(같은 문서인지 모른다)에서도.
        assertFalse(log.commit("https://v.test/watch?v=1#top", "첫 영상", 310, sameDocument = null, navigationStart = null))
        assertEquals(listOf("https://cdn.test/clip.mp4"), log.currentUrls())
        // 다른 문서로 같은 주소를 다시 연 것(새로 고침)은 새 페이지다.
        assertTrue(log.commit("https://v.test/watch?v=1", "첫 영상", 320, sameDocument = false, navigationStart = 315))
        assertEquals(emptyList(), log.currentUrls())
    }

    @Test
    fun 커밋_전에_옛_재생기가_낸_요청은_옛_페이지에_남는다() {
        val log = opened()
        log.found("https://cdn.test/live/index.m3u8", MediaKind.HLS, 200)
        log.found("https://cdn.test/old/clip.mp4", MediaKind.VIDEO, 250)
        // 링크를 눌렀다(탐색 시작 1000). 커밋 알림(1500)이 오기 전에 — 옛 재생기가 재생목록을 다시 받고(이미 있던 것),
        // 새 문서의 <video> 가 제 파일을 요청했다(처음 보는 것).
        assertEquals(FoundMediaLog.Change.NONE, log.found("https://cdn.test/live/index.m3u8", MediaKind.HLS, 1100))
        assertEquals(FoundMediaLog.Change.NEW, log.found("https://cdn.test/new/movie.mp4", MediaKind.VIDEO, 1200))
        log.commit("https://v.test/next", "", 1500, sameDocument = false, navigationStart = 1000)
        log.retitle("다음 쪽")
        val l = log.listing(1600)
        assertEquals(listOf("https://cdn.test/new/movie.mp4"), l.current.map { it.url })
        assertEquals("다음 쪽", l.page.title)
        assertEquals(setOf("https://cdn.test/live/index.m3u8", "https://cdn.test/old/clip.mp4"), l.previous.single().rows.map { it.url }.toSet())
        // 탐색 시작 전에 찾은 것이 지금 다시 요청되면 지금 페이지로 올라온다.
        assertEquals(FoundMediaLog.Change.MOVED, log.found("https://cdn.test/old/clip.mp4", MediaKind.VIDEO, 1700))
        assertEquals(listOf("https://cdn.test/old/clip.mp4", "https://cdn.test/new/movie.mp4"), log.currentUrls(1800))
    }

    @Test
    fun 커밋되지_않은_탐색은_목록을_건드리지_않는다() {
        // 첨부 응답·204 — 커밋 알림이 없다. 찾은 것은 그대로 지금 페이지의 것이다.
        val log = opened()
        log.found("https://cdn.test/clip.mp4", MediaKind.VIDEO, 200)
        assertEquals(listOf("https://cdn.test/clip.mp4"), log.currentUrls())
        assertEquals(1, log.currentCount())
    }

    @Test
    fun 첫_커밋_전에_찾은_것은_그_페이지의_것이다() {
        val log = FoundMediaLog()
        log.found("https://cdn.test/clip.mp4", MediaKind.VIDEO, 50)
        log.commit("https://v.test/", "처음", 100, sameDocument = null, navigationStart = null)
        assertEquals(listOf("https://cdn.test/clip.mp4"), log.currentUrls())
        assertTrue(log.listing(0).previous.isEmpty())
    }

    @Test
    fun 지금_페이지는_최근에_요청된_차례() {
        val log = opened()
        log.found("https://cdn.test/a.mp4", MediaKind.VIDEO, 200)
        log.found("https://cdn.test/b.mp4", MediaKind.VIDEO, 300)
        log.found("https://cdn.test/a.mp4", MediaKind.VIDEO, 400)
        assertEquals(listOf("https://cdn.test/a.mp4", "https://cdn.test/b.mp4"), log.currentUrls())
    }

    @Test
    fun 번호만_다른_파일은_셋부터_한_줄() {
        val log = opened()
        assertEquals(FoundMediaLog.Change.NEW, log.found("https://cdn.test/v/seg-1.mp4?tok=a", MediaKind.VIDEO, 200))
        assertEquals(FoundMediaLog.Change.GREW, log.found("https://cdn.test/v/seg-2.mp4?tok=b", MediaKind.VIDEO, 210))
        // 둘은 따로 — 같은 쪽의 다른 영상(clip1·clip2)일 때가 많다.
        assertEquals(2, log.currentCount())
        assertEquals(listOf("https://cdn.test/v/seg-1.mp4?tok=a", "https://cdn.test/v/seg-2.mp4?tok=b"), log.currentUrls())
        assertEquals(FoundMediaLog.Change.GREW, log.found("https://cdn.test/v/seg-3.mp4?tok=c", MediaKind.VIDEO, 220))
        assertEquals(FoundMediaLog.Change.NONE, log.found("https://cdn.test/v/seg-3.mp4?tok=c", MediaKind.VIDEO, 230))
        val rows = log.listing(300).current
        assertEquals(1, rows.size)
        assertTrue(rows[0].series)
        assertEquals(3, rows[0].urls.size)
        assertEquals("https://cdn.test/v/seg-1.mp4?tok=a", rows[0].url)
        assertEquals(1, log.currentCount())
        // 다른 경로·다른 확장자는 다른 묶음.
        log.found("https://cdn.test/w/seg-1.mp4", MediaKind.VIDEO, 240)
        log.found("https://cdn.test/v/seg-1.m4a", MediaKind.AUDIO, 250)
        assertEquals(3, log.currentCount())
    }

    @Test
    fun 마스터를_읽으면_그_아래_재생목록을_숨긴다() {
        val log = opened()
        val master = "https://cdn.test/v/master.m3u8"
        log.found(master, MediaKind.HLS, 200)
        log.found("https://cdn.test/v/720/index.m3u8", MediaKind.HLS, 210)
        log.found("https://cdn.test/v/audio/ko.m3u8", MediaKind.HLS, 220)
        assertEquals(3, log.currentCount())
        assertTrue(log.claimRead(master))
        assertFalse(log.claimRead(master)) // 한 번만
        val text = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="한국어",DEFAULT=YES,URI="audio/ko.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="s",NAME="자막",URI="subs/ko.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,AUDIO="a"
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,AUDIO="a"
            360/index.m3u8
        """.trimIndent()
        log.readDone(master, HlsPlaylist.parse(text, master))
        assertEquals(listOf(master), log.currentUrls())
        val m = log.listing(0).current[0].entry.master!!
        assertEquals(2, m.variants)
        assertEquals(1, m.audio)
        assertEquals(1, m.subtitles)
        // 나중에 잡힌 자막·다른 화질도 숨는다. 하위 재생목록을 고르면 마스터로 돌린다.
        log.found("https://cdn.test/v/subs/ko.m3u8", MediaKind.HLS, 230)
        assertEquals(1, log.currentCount())
        assertSame(log.listing(0).current[0].entry, log.masterOf("https://cdn.test/v/360/index.m3u8"))
        assertNull(log.masterOf("https://cdn.test/other/index.m3u8"))
        // 마스터 아래의 것은 뒤에서 읽지 않는다.
        assertFalse(log.claimRead("https://cdn.test/v/720/index.m3u8"))
    }

    @Test
    fun 저지연_HLS_의_다시_받기는_한_줄이고_마스터_아래로_숨는다() {
        val log = opened()
        val master = "https://cdn.test/ll/master.m3u8"
        log.found(master, MediaKind.HLS, 200)
        assertEquals(FoundMediaLog.Change.NEW, log.found("https://cdn.test/ll/v.m3u8?_HLS_msn=10&_HLS_part=0", MediaKind.HLS, 210))
        assertEquals(FoundMediaLog.Change.NONE, log.found("https://cdn.test/ll/v.m3u8?_HLS_msn=10&_HLS_part=1", MediaKind.HLS, 220))
        assertEquals(2, log.currentCount())
        log.claimRead(master)
        log.readDone(master, HlsPlaylist.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8", master))
        assertEquals(listOf(master), log.currentUrls())
    }

    @Test
    fun 숨긴_재생목록이_다시_요청되면_마스터가_지금_페이지로_올라온다() {
        val log = opened()
        val master = "https://cdn.test/live/master.m3u8"
        log.found(master, MediaKind.HLS, 200)
        log.found("https://cdn.test/live/hi.m3u8", MediaKind.HLS, 210)
        log.claimRead(master)
        log.readDone(master, HlsPlaylist.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhi.m3u8", master))
        log.commit("https://v.test/watch?v=1&t=5", "같은 영상", 300, sameDocument = true, navigationStart = null)
        assertEquals(0, log.currentCount())
        // 실시간 재생목록을 다시 받는다 — 그 스트림은 지금 페이지의 것이다.
        assertEquals(FoundMediaLog.Change.MOVED, log.found("https://cdn.test/live/hi.m3u8", MediaKind.HLS, 400))
        assertEquals(listOf(master), log.currentUrls())
    }

    @Test
    fun 뒤에서_읽기는_페이지마다_묶는다() {
        val log = opened()
        val urls = (1..FoundMediaLog.MAX_READS_PER_PAGE + 2).map { "https://cdn.test/p$it.m3u8" }
        urls.forEachIndexed { i, u -> log.found(u, MediaKind.HLS, 200L + i) }
        val claimed = urls.count { log.claimRead(it) }
        assertEquals(FoundMediaLog.MAX_READS_PER_PAGE, claimed)
        assertFalse(log.claimRead("https://cdn.test/unknown.m3u8"))
        log.found("https://cdn.test/clip.mp4", MediaKind.VIDEO, 300)
        assertFalse(log.claimRead("https://cdn.test/clip.mp4")) // HLS 만
    }

    @Test
    fun 오래된_앞_페이지는_버린다() {
        val log = FoundMediaLog(maxPreviousPages = 2, keepMs = 10_000)
        // 이름에 숫자가 없게(경로만 다르게) — 번호만 다른 파일은 한 묶음이 된다.
        for (n in 1..4) {
            log.commit("https://s.test/$n", "쪽 $n", n * 1000L, sameDocument = false, navigationStart = n * 1000L)
            log.found("https://cdn.test/p$n/clip.mp4", MediaKind.VIDEO, n * 1000L + 10)
        }
        // 지금 4, 앞 페이지는 3·2 만(최근에 떠난 차례).
        assertEquals(listOf(listOf("https://cdn.test/p3/clip.mp4"), listOf("https://cdn.test/p2/clip.mp4")), log.previousUrls(4100))
        // 떠난 지 오래되면 버린다.
        assertEquals(emptyList(), log.previousUrls(30_000))
        assertEquals(listOf("https://cdn.test/p4/clip.mp4"), log.currentUrls(30_000))
        assertFalse(log.isEmpty(30_000))
        // 지금 페이지의 것은 시간이 지나도 남는다 — 떠나면 그때부터 센다.
        log.commit("https://s.test/5", "쪽 5", 40_000, sameDocument = false, navigationStart = 40_000)
        assertFalse(log.isEmpty(45_000))
        assertTrue(log.isEmpty(60_000))
    }
}
