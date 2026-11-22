package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.PdfPagesSupport
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 파일 목록의 'PDF 합치기' — 메뉴 줄이 서는 조건, 차례 바꾸기, 합칠 수 있는가([PdfMergeRules]). */
class PdfMergeRulesTest {

    private val folder = "/storage/emulated/0/Docs"

    private fun entry(name: String, dir: Boolean = false) = FileEntry(
        name = name,
        path = "$folder/$name",
        isDirectory = dir,
        size = 1,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = if (dir) FileKind.FOLDER else FileKind.PDF,
    )

    private val entries = listOf(entry("b.pdf"), entry("a.PDF"), entry("c.txt"), entry("d", dir = true))

    @Test
    fun `보이는 PDF 를 둘 이상 고르면 고른 차례로`() {
        val selection = linkedSetOf("$folder/b.pdf", "$folder/a.PDF")
        assertEquals(listOf("$folder/b.pdf", "$folder/a.PDF"), PdfMergeRules.mergeable(selection, entries))
        // 하나뿐·PDF 아닌 것·폴더·보이지 않는 것이 섞이면 줄이 없다.
        assertNull(PdfMergeRules.mergeable(linkedSetOf("$folder/b.pdf"), entries))
        assertNull(PdfMergeRules.mergeable(linkedSetOf("$folder/b.pdf", "$folder/c.txt"), entries))
        assertNull(PdfMergeRules.mergeable(linkedSetOf("$folder/b.pdf", "$folder/d"), entries))
        assertNull(PdfMergeRules.mergeable(linkedSetOf("$folder/b.pdf", "$folder/z.pdf"), entries))
    }

    @Test
    fun `화살표는 한 칸씩 옮기고 끝에서는 그대로`() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), PdfMergeRules.moved(list, 1, -1))
        assertEquals(listOf("a", "c", "b"), PdfMergeRules.moved(list, 1, 1))
        assertEquals(list, PdfMergeRules.moved(list, 0, -1))
        assertEquals(list, PdfMergeRules.moved(list, 2, 1))
    }

    @Test
    fun `잠긴 것은 세션 암호로 풀렸을 때만 합친다`() {
        val paths = listOf("x", "y")
        fun readable(n: Int?, lock: PdfPagesSupport.Lock) = PdfPagesSupport.Inspection.Readable(n, lock)
        assertNull(PdfMergeRules.verdict(mapOf("x" to readable(3, PdfPagesSupport.Lock.NONE)), paths))
        assertEquals(
            PdfMergeRules.Verdict(ok = true, unlocks = false),
            PdfMergeRules.verdict(mapOf("x" to readable(3, PdfPagesSupport.Lock.NONE), "y" to readable(2, PdfPagesSupport.Lock.NONE)), paths),
        )
        // 세션 암호로 풀렸다(쪽 수가 있다) — 합치되 '잠기지 않은 PDF 로 저장합니다'.
        assertEquals(
            PdfMergeRules.Verdict(ok = true, unlocks = true),
            PdfMergeRules.verdict(mapOf("x" to readable(3, PdfPagesSupport.Lock.NONE), "y" to readable(2, PdfPagesSupport.Lock.PASSWORD)), paths),
        )
        // 세션 암호가 없다(쪽 수를 모른다) — 막는다.
        assertEquals(
            false,
            PdfMergeRules.verdict(mapOf("x" to readable(3, PdfPagesSupport.Lock.NONE), "y" to readable(null, PdfPagesSupport.Lock.PASSWORD)), paths)?.ok,
        )
        // 인증서 잠금·읽지 못함·빈 문서도 막는다.
        for (bad in listOf(readable(null, PdfPagesSupport.Lock.UNSUPPORTED), PdfPagesSupport.Inspection.Unreadable, readable(0, PdfPagesSupport.Lock.NONE))) {
            assertEquals(false, PdfMergeRules.verdict(mapOf("x" to readable(3, PdfPagesSupport.Lock.NONE), "y" to bad), paths)?.ok, "$bad")
        }
        // 쪽 수의 합이 상한을 넘으면 미리 말한다.
        assertEquals(
            PdfMergeRules.Verdict(ok = false, unlocks = false, tooMany = true),
            PdfMergeRules.verdict(mapOf("x" to readable(15_000, PdfPagesSupport.Lock.NONE), "y" to readable(5_001, PdfPagesSupport.Lock.NONE)), paths),
        )
    }
}
