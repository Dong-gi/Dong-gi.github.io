package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.io.FileStamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 편집기의 저장 자리 — 어디에 쓸 것인가(`EditorSlots.plan`)와 이름 다듬기. */
class EditorSlotsTest {

    private val original = "/storage/emulated/0/Docs/보고서.hwp"
    private val stamp = FileStamp(1000, 5000)

    private fun session(
        keptPrevious: Boolean = false,
        created: Map<String, EditorSlots.Created> = emptyMap(),
    ) = EditorSlots.Session(
        token = "0".repeat(32),
        original = original,
        kind = "hwp",
        keptPrevious = keptPrevious,
        stamp = stamp,
        created = created,
        saves = 0,
        last = null,
        openedAt = 0,
    )

    @Test
    fun theFirstSaveReplacesTheOriginalAndKeepsThePreviousVersion() {
        val plan = EditorSlots.plan(session(), "hwp", "보고서.hwp") { stamp }!!
        assertEquals(original, plan.path)
        assertFalse(plan.createNew)
        assertTrue(plan.keepPrevious)
    }

    @Test
    fun laterSavesDoNotPileUpCopiesInTheTrash() {
        val plan = EditorSlots.plan(session(keptPrevious = true), "hwp", "보고서.hwp") { stamp }!!
        assertEquals(original, plan.path)
        assertFalse(plan.keepPrevious)
    }

    @Test
    fun aChangeFromOutsideIsKeptBeforeOverwriting() {
        val plan = EditorSlots.plan(session(keptPrevious = true), "hwp", "보고서.hwp") { FileStamp(1200, 9000) }!!
        assertTrue(plan.keepPrevious)
    }

    @Test
    fun anotherFormatOrNameBecomesANewFileBesideTheOriginal() {
        val asHwpx = EditorSlots.plan(session(), "hwpx", "보고서.hwpx") { null }!!
        assertEquals("/storage/emulated/0/Docs/보고서.hwpx", asHwpx.path.replace('\\', '/'))
        assertTrue(asHwpx.createNew)
        assertEquals("보고서.hwpx", asHwpx.nameKey)

        val renamed = EditorSlots.plan(session(), "hwp", "최종.hwp") { null }!!
        assertEquals("/storage/emulated/0/Docs/최종.hwp", renamed.path.replace('\\', '/'))
        assertTrue(renamed.createNew)
    }

    @Test
    fun aFileThisSessionMadeIsReplacedNextTime() {
        val made = "/storage/emulated/0/Docs/보고서 (2).hwpx"
        val s = session(created = mapOf("보고서.hwpx" to EditorSlots.Created(made, FileStamp(10, 20))))
        val again = EditorSlots.plan(s, "hwpx", "보고서.hwpx") { p -> if (p == made) FileStamp(10, 20) else null }!!
        assertEquals(made, again.path)
        assertFalse(again.createNew)
        assertFalse(again.keepPrevious)
        // 그사이 지웠으면 새로 만든다.
        val gone = EditorSlots.plan(s, "hwpx", "보고서.hwpx") { null }!!
        assertTrue(gone.createNew)
    }

    @Test
    fun namesFromTheEditorAreNotTrusted() {
        assertEquals("b.hwpx", EditorSlots.cleanName("../../a/b.hwpx", "hwpx", "x.hwp"))
        assertEquals("ab.hwp", EditorSlots.cleanName("a<>:\"|?*\u0000b.hwp", "hwp", "x.hwp"))
        assertEquals("보고서.hwpx", EditorSlots.cleanName("보고서.hwp", "hwpx", "x.hwp"))
        assertEquals("v1.2 초안.hwp", EditorSlots.cleanName("v1.2 초안", "hwp", "x.hwp"))
        assertEquals("원본.hwpx", EditorSlots.cleanName("...", "hwpx", "원본.hwp"))
        assertEquals("원본.hwp", EditorSlots.cleanName(null, "hwp", "원본.hwp"))
        assertEquals("document.hwp", EditorSlots.cleanName("", "hwp", ".hwp"))
        // 길이는 바이트로 묶는다(255 − 확장자) — 한글 300자는 83자(249바이트)가 된다(`newNamesAreCutByBytes`).
        assertEquals(83 + ".hwp".length, EditorSlots.cleanName("가".repeat(300), "hwp", "x.hwp").length)
        // 글자로는 120자까지다 — 편집기 쪽(`OutputNames`)과 같은 자리에서 잘라야 긴 이름의 제자리 저장이 맞는다.
        assertEquals(120 + ".hwp".length, EditorSlots.cleanName("a".repeat(300), "hwp", "x.hwp").length)
    }

    @Test
    fun unknownFormatsAreRefused() {
        assertNull(EditorSlots.plan(session(), "exe", "a.exe") { stamp })
    }

    /**
     * 같은 이름인가는 원본 이름에도 **같은 다듬기**를 하고 견준다. 편집기 쪽 이름은 엔진·`OutputNames`·`cleanName` 을 지나며
     * 다듬어지는데 원본 이름은 그대로 견주어, 글자 하나만 달라도 제자리 저장이 '곁에 새 파일 `(2)`' 이 되었다. 제자리면 쓰는 경로는 원본 그대로다.
     */
    @Test
    fun theSameNameIsJudgedAfterTheSameCleaning() {
        fun assertInPlace(originalName: String, kind: String, raw: String) {
            val s = session().copy(original = "/storage/emulated/0/Docs/$originalName", kind = kind)
            val plan = EditorSlots.plan(s, kind, raw) { stamp }!!
            assertFalse(plan.createNew, "$originalName ← $raw")
            assertEquals(s.original, plan.path, "$originalName ← $raw")
        }
        // rhwp 는 저장 이름의 확장자를 소문자로 갈아 끼운다 — 윈도에서 온 대문자 확장자.
        assertInPlace("공문.HWP", "hwp", "공문.hwp")
        // '+ 새 문서' 가 사용자가 친 대문자 확장자를 그대로 받아 만든 문서.
        assertInPlace("보고서.DOCX", "docx", "보고서.docx")
        // 점으로 시작하는 이름, 확장자 앞의 빈칸, 편집기 쪽이 뺀 금지 글자.
        assertInPlace(".메모.docx", "docx", ".메모.docx")
        assertInPlace("보고서 .docx", "docx", "보고서 .docx")
        assertInPlace("질문?.hwp", "hwp", "질문.hwp")
        // 바깥 저장소는 대소문자를 가리지 않는다 — 대소문자만 다른 이름은 같은 파일이다(새로 만들면 `(2)` 가 된다).
        assertInPlace("Report.docx", "docx", "report.docx")
        // 편집기는 이름을 120자에서 자른다(`OutputNames`) — 긴 이름도 원본이다.
        assertInPlace("a".repeat(200) + ".docx", "docx", "a".repeat(120) + ".docx")
        assertInPlace("가".repeat(100) + ".hwp", "hwp", "가".repeat(100) + ".hwp")
        // 이름이 정말 다르면 곁에 새 파일이다.
        val renamed = EditorSlots.plan(session().copy(original = "/storage/emulated/0/Docs/공문.HWP"), "hwp", "공문 수정.hwp") { stamp }!!
        assertTrue(renamed.createNew)
    }

    /** 이 세션이 만든 파일을 다시 찾는 것도 대소문자를 가리지 않는다 — 같은 파일을 두고 `(2)` 를 또 만들지 않게. */
    @Test
    fun aMadeFileIsFoundAgainRegardlessOfCase() {
        val made = "/storage/emulated/0/Docs/Final.hwp"
        val s = session(created = mapOf("Final.hwp" to EditorSlots.Created(made, FileStamp(10, 20))))
        val again = EditorSlots.plan(s, "hwp", "final.hwp") { p -> if (p == made) FileStamp(10, 20) else null }!!
        assertEquals(made, again.path)
        assertFalse(again.createNew)
    }

    /** 새 이름은 **바이트로** 자른다 — ext4·F2FS 의 이름 상한은 255바이트다. 한글은 한 글자가 3바이트다. 서로게이트 쌍을 가르지 않는다. */
    @Test
    fun newNamesAreCutByBytes() {
        val korean = EditorSlots.cleanName("가".repeat(300), "hwp", "x.hwp")
        assertTrue(korean.toByteArray(Charsets.UTF_8).size <= 255, "${korean.toByteArray(Charsets.UTF_8).size} 바이트")
        assertEquals("가".repeat(83) + ".hwp", korean)
        // 4바이트 글자(서로게이트 쌍) — 글자(UTF-16)로 120자 = 60개(240바이트). 쌍의 앞 반쪽에서 자르지 않는다 — 앞에 한 글자가 더 있으면 59개.
        assertEquals("😀".repeat(60) + ".docx", EditorSlots.cleanName("😀".repeat(100), "docx", "x.docx"))
        assertEquals("a" + "😀".repeat(59) + ".docx", EditorSlots.cleanName("a" + "😀".repeat(100), "docx", "x.docx"))
    }

    /**
     * 편집기 쪽(`OutputNames` — 받은 문서를 이 이름으로 앱 캐시에 먼저 쓰고 그 이름을 반영에 싣는다)과 파일 쪽 다듬기가 **같은 자리에서 자른다** —
     * 그래야 긴 이름·특이한 이름의 '저장' 도 원본 자리에 놓인다. 한쪽만 고치면 긴 이름이 곁의 새 파일이 된다.
     */
    @Test
    fun theEditorsNamingAndOursAgree() {
        val originals = listOf(
            "공문.HWP", ".메모.hwp", "보고서 .hwp", "질문?.hwp",
            "a".repeat(200) + ".hwp", "가".repeat(100) + ".hwp", "a" + "😀".repeat(100) + ".hwp",
            "가".repeat(83) + "ab.hwp", // 255바이트
        )
        for (name in originals) {
            // 편집기가 '저장' 으로 보내는 이름 — 쪽이 댄 이름(원본 그대로), 모자라면 Files 가 준 보이는 이름.
            val sent = io.github.donggi.iroiroviewer.editor.OutputNames.of(name, name, io.github.donggi.iroiroviewer.editor.EditorContract.Kind.HWP)
            assertTrue(sent.toByteArray(Charsets.UTF_8).size <= 255, "$name → $sent")
            val s = session().copy(original = "/storage/emulated/0/Docs/$name")
            val plan = EditorSlots.plan(s, "hwp", sent) { stamp }!!
            assertFalse(plan.createNew, "$name → $sent")
            assertEquals(s.original, plan.path, name)
        }
    }

    /** ONLYOFFICE — 이전 형식·PDF 를 연 것은 OOXML 로 저장되어 원본 곁에 새 파일이 된다. 다른 형식의 확장자도 떼고 붙인다. */
    @Test
    fun legacyAndPdfSavesBecomeANewOoxmlFile() {
        val legacy = EditorSlots.plan(session().copy(original = "/storage/emulated/0/Docs/옛 보고서.doc", kind = "doc"), "docx", "옛 보고서.docx") { null }!!
        assertEquals("/storage/emulated/0/Docs/옛 보고서.docx", legacy.path.replace('\\', '/'))
        assertTrue(legacy.createNew)
        val pdf = EditorSlots.plan(session().copy(original = "/storage/emulated/0/Docs/명세.pdf", kind = "pdf"), "docx", "명세.docx") { null }!!
        assertEquals("/storage/emulated/0/Docs/명세.docx", pdf.path.replace('\\', '/'))
        assertTrue(pdf.createNew)
        // 같은 형식(ODF·CSV·매크로 문서)은 제자리에.
        val csv = session().copy(original = "/storage/emulated/0/Docs/표.csv", kind = "csv")
        val inPlace = EditorSlots.plan(csv, "csv", "표.csv") { stamp }!!
        assertEquals(csv.original, inPlace.path)
        assertFalse(inPlace.createNew)
        assertEquals("보고서.xlsx", EditorSlots.cleanName("보고서.xls", "xlsx", "x.xls"))
        assertEquals("발표.pptx", EditorSlots.cleanName("발표.ppt", "pptx", "x.ppt"))
        assertEquals("v2.0 표.csv", EditorSlots.cleanName("v2.0 표", "csv", "x.csv"))
    }
}
