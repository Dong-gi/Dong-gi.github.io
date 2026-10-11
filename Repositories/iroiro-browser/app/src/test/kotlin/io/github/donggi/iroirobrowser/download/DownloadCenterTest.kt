package io.github.donggi.iroirobrowser.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 내려받기의 순수한 판정 — 기기 없이 볼 수 있는 것만(받기 자체는 MediaStore·WebView 의 쿠키를 쓴다). */
class DownloadCenterTest {

    @Test
    fun 기록_없는_감춘_항목만_지운다() {
        // 앞 프로세스가 합치기·blob 받기 도중에 죽어 남긴 감춘 항목은 목록에 기록이 없다 — 그것만 지운다.
        val started = 1_800_000_000_000L
        val before = started / 1000 - 60
        val entries = listOf(
            MediaStoreWriter.PendingEntry(1, before), // 멈춘 받기가 이어 쓸 곳 — 목록에 있다
            MediaStoreWriter.PendingEntry(2, before), // 고아
            MediaStoreWriter.PendingEntry(3, started / 1000), // 이 프로세스가 막 만든 것(지금 받는 것)
            MediaStoreWriter.PendingEntry(4, started / 1000 + 5),
        )
        assertEquals(listOf(2L), DownloadCenter.orphansOf(entries, known = setOf(1L), startedAtMs = started).map { it.id })
    }

    @Test
    fun 원래_페이지는_한_번만_가져간다() {
        // 내려받기 목록의 '원래 페이지 열기' 는 주소를 프로세스 안에서만 넘긴다 — 브라우저 화면이 한 번 가져가면 비운다.
        assertNull(DownloadCenter.takePageToOpen())
        DownloadCenter.openPageLater("https://site.test/watch?v=1")
        DownloadCenter.openPageLater("https://site.test/watch?v=2")
        assertEquals("https://site.test/watch?v=2", DownloadCenter.takePageToOpen())
        assertNull(DownloadCenter.takePageToOpen())
    }
}
