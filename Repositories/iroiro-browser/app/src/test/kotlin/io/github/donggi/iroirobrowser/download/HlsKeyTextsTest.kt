package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.media.HlsKeys
import io.github.donggi.iroirobrowser.core.media.PageRequests
import kotlin.test.Test
import kotlin.test.assertEquals

/** 열쇠를 받지 못한 HLS 의 실패 글 — 고르는 일만 본다(자원 번호와 인자, 2026-10-16). */
class HlsKeyTextsTest {

    private val sizes = TorrentTexts.Sizes { "${it}B" }

    private fun report(
        kind: HlsKeys.BodyKind,
        bytes: Long = 0,
        redirects: Int = 0,
        ordinal: Int = 1,
        count: Int = 1,
        crossSite: Boolean = false,
        replay: PageRequests.Match? = null,
    ) = HlsKeys.Report(kind, bytes, redirects, ordinal, count, crossSite, replay)

    @Test
    fun 갈래가_다_실린다() {
        val r = report(HlsKeys.BodyKind.HTML, 3200, redirects = 1, ordinal = 2, count = 3, crossSite = true, replay = PageRequests.Match.EXACT)
        val parts = HlsKeyTexts.parts(r, sizes)
        assertEquals(
            listOf(
                R.string.download_key_html, R.string.download_key_redirects, R.string.download_key_ordinal, R.string.download_key_cross_site,
                R.string.download_key_page_headers,
            ),
            parts.map { it.res },
        )
        assertEquals(listOf<Any>("3200B"), parts[0].args)
        assertEquals(listOf<Any>(1), parts[1].args)
        assertEquals(listOf<Any>(2, 3), parts[2].args)
    }

    @Test
    fun 몸의_꼴마다_글이_다르다() {
        val res = HlsKeys.BodyKind.entries.map { HlsKeyTexts.parts(report(it, bytes = 32), sizes).first().res }
        assertEquals(res.size, res.toSet().size)
        val long = HlsKeyTexts.parts(report(HlsKeys.BodyKind.BINARY, 17), sizes).first()
        assertEquals(R.string.download_key_long, long.res)
        assertEquals(listOf<Any>(17), long.args)
        assertEquals(R.string.download_key_short, HlsKeyTexts.parts(report(HlsKeys.BodyKind.BINARY, 8), sizes).first().res)
    }

    @Test
    fun 덧붙임은_있을_때만() {
        // 넘겨주기 없음·열쇠 하나·같은 사이트 — 꼴과 '쪽의 요청 기록 없음' 둘뿐.
        assertEquals(
            listOf(R.string.download_key_empty, R.string.download_key_no_page_request),
            HlsKeyTexts.parts(report(HlsKeys.BodyKind.EMPTY), sizes).map { it.res },
        )
        val playlist = HlsKeyTexts.parts(report(HlsKeys.BodyKind.JSON, 30, replay = PageRequests.Match.PLAYLIST), sizes)
        assertEquals(R.string.download_key_playlist_referer, playlist.last().res)
        for (m in listOf(PageRequests.Match.EXACT, PageRequests.Match.PAGE_QUERY, PageRequests.Match.SAME_PATH)) {
            assertEquals(R.string.download_key_page_headers, HlsKeyTexts.parts(report(HlsKeys.BodyKind.TEXT, 30, replay = m), sizes).last().res)
        }
    }
}
