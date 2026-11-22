package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.io.PdfPagesSupport
import kotlin.test.Test
import kotlin.test.assertEquals

/** 쪽 뽑기 대화상자가 말할 것([ExtractCheck]). */
class ExtractCheckTest {

    private fun readable(n: Int?, lock: PdfPagesSupport.Lock) = PdfPagesSupport.Inspection.Readable(n, lock)

    @Test
    fun `잠금과 쪽 수로 가른다`() {
        assertEquals(ExtractCheck.Checking, ExtractCheck.of(null, 5))
        assertEquals(ExtractCheck.Ready(unlocks = false), ExtractCheck.of(readable(5, PdfPagesSupport.Lock.NONE), 5))
        // 소유자 암호만·세션 암호로 풀린 사용자 암호 — 잠기지 않은 PDF 로 저장한다고 말한다.
        assertEquals(ExtractCheck.Ready(unlocks = true), ExtractCheck.of(readable(5, PdfPagesSupport.Lock.OWNER_ONLY), 5))
        assertEquals(ExtractCheck.Ready(unlocks = true), ExtractCheck.of(readable(5, PdfPagesSupport.Lock.PASSWORD), 5))
        // 세션 암호가 지워졌다(앱이 화면에서 사라졌다).
        assertEquals(ExtractCheck.Blocked(ExtractCheck.Why.NEEDS_PASSWORD), ExtractCheck.of(readable(null, PdfPagesSupport.Lock.PASSWORD), 5))
        assertEquals(ExtractCheck.Blocked(ExtractCheck.Why.LOCK_UNSUPPORTED), ExtractCheck.of(readable(null, PdfPagesSupport.Lock.UNSUPPORTED), 5))
        assertEquals(ExtractCheck.Blocked(ExtractCheck.Why.UNREADABLE), ExtractCheck.of(PdfPagesSupport.Inspection.Unreadable, 5))
        // 화면과 쪽 수가 다르면 고른 번호가 다른 쪽일 수 있다.
        assertEquals(ExtractCheck.Blocked(ExtractCheck.Why.UNREADABLE), ExtractCheck.of(readable(6, PdfPagesSupport.Lock.NONE), 5))
    }
}
