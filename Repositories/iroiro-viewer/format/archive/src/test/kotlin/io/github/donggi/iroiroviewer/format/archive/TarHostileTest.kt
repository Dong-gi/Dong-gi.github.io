package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **악의적으로 지은** tar 에서 리더가 무엇을 하는가. ZIP·7z·RAR 에 걸린 방어(`HostileArchiveTest`)가 tar 에도
 * 걸리는지, 그리고 tar 만의 구멍(메타 머리의 크기·링크에 붙은 크기·희소 파일)을 막는지 본다.
 */
class TarHostileTest {

    private val dir: File = Files.createTempDirectory("iroiro-tar").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun file(name: String, bytes: ByteArray) = TarBytes.write(File(dir, name), bytes)

    private fun open(f: File, limits: ParseLimits = ParseLimits.DEFAULT, budget: EntryBudget = EntryBudget(limits)) =
        Archives.open(FileDocumentSource(f), limits, budget)

    private fun names(f: File) = open(f).use { r -> r.entries.map { it.name } }

    private fun readAll(r: ArchiveReader, name: String) =
        r.open(r.entries.single { it.name == name }).use { it.readBytes() }

    /** 같은 표본을 압축 안 한 채·gzip·bzip2·xz 로 싸서 넷 다 본다 — 두 길(자리 옮기기·풀어 버리기)이 같아야 한다. */
    private fun allForms(name: String, tar: ByteArray): List<File> = listOf(
        file("$name.tar", tar),
        file("$name.tar.gz", TarBytes.gzip(tar)),
        file("$name.tar.bz2", TarBytes.bzip2(tar)),
        file("$name.tar.xz", TarBytes.xz(tar)),
    )

    // ---- 이름 ------------------------------------------------------------------

    @Test
    fun `위험한 이름은 ZIP 과 같은 관문을 지난다`() {
        val tar = TarBytes()
            .file("ok.txt", "ok".toByteArray())
            .file("../../../../sdcard/탈출.txt", "x".toByteArray())
            .file("/etc/passwd", "x".toByteArray())
            .file("a/../../b.txt", "x".toByteArray())
            .end()
        for (f in allForms("evil", tar)) {
            open(f).use { r ->
                val by = r.entries.associateBy { it.name }
                assertEquals("ok.txt", by.getValue("ok.txt").safeName, f.name)
                assertNull(by.getValue("../../../../sdcard/탈출.txt").safeName, f.name)
                assertNull(by.getValue("a/../../b.txt").safeName, f.name)
                assertEquals("etc/passwd", by.getValue("/etc/passwd").safeName, "절대경로는 다듬는다")
                for (e in r.entries.filter { it.safeName == null }) {
                    assertFalse(e.isReadable)
                    assertFailsWith<IllegalArgumentException> { r.open(e) }
                }
            }
        }
    }

    @Test
    fun `ustar 앞머리는 잇고 GNU 형식의 그 자리는 잇지 않는다`() {
        val tar = TarBytes()
            .header("c.txt", size = 1, prefix = "a/b".toByteArray()).data(byteArrayOf(1))
            // 옛 GNU 는 그 자리에 시각을 적는다 — 이름에 붙이면 엉뚱한 경로가 된다.
            .header("d.txt", size = 1, magic = TarBytes.Magic.GNU, prefix = "12345670123".toByteArray()).data(byteArrayOf(2))
            .end()
        assertEquals(listOf("a/b/c.txt", "d.txt"), names(file("prefix.tar", tar)))
    }

    /** 이름은 신원이 아니다 — 같은 이름 둘에서 서로 다른 바이트가 나와야 한다. */
    @Test
    fun `같은 이름 둘은 번호로 갈린다`() {
        val tar = TarBytes().file("same.txt", "first".toByteArray()).file("same.txt", "second".toByteArray()).end()
        for (f in allForms("dup", tar)) {
            open(f).use { r ->
                assertEquals(2, r.entries.size)
                assertContentEquals("first".toByteArray(), r.open(r.entries[0]).use { it.readBytes() }, f.name)
                assertContentEquals("second".toByteArray(), r.open(r.entries[1]).use { it.readBytes() }, f.name)
            }
        }
    }

    // ---- 메타 머리의 크기 --------------------------------------------------------------

    /**
     * **1 GiB 라고 적은 GNU 긴 이름.** commons-compress 의 tar 읽기는 적힌 크기만큼 메모리에 모은다. 우리는
     * 적힌 크기를 보고 곧바로 거절한다 — 이 시험이 1초 안에 끝나는 것 자체가 할당하지 않았다는 뜻이다.
     */
    @Test
    fun `적힌 크기가 큰 긴 이름과 PAX 머리는 읽기 전에 거절한다`() {
        val longName = TarBytes().header("././@LongLink", 'L', size = 1L shl 30).bytes()
        val e1 = assertFailsWith<ParseLimitExceededException> { names(file("longlink.tar", longName)) }
        assertEquals("tarHeader", e1.limitName)
        val pax = TarBytes().header("PaxHeader", 'x', size = 2L shl 20).bytes()
        val e2 = assertFailsWith<ParseLimitExceededException> { names(file("pax.tar", pax)) }
        assertEquals("tarHeader", e2.limitName)
    }

    @Test
    fun `깨진 PAX 줄은 깨진 파일이다`() {
        val body = "99 path=a\n".toByteArray()
        val tar = TarBytes().header("PaxHeader", 'x', size = body.size.toLong()).data(body).file("a", byteArrayOf(1)).end()
        assertFailsWith<IOException> { names(file("badpax.tar", tar)) }
    }

    @Test
    fun `PAX 의 크기와 이름과 시각이 머리보다 먼저다`() {
        val body = byteArrayOf(9, 8, 7, 6, 5)
        val pax = paxRecords("path" to "긴/경로/이름.bin", "size" to "5", "mtime" to "1789207200.5")
        // 머리의 크기 칸은 0 이다 — 8 GiB 넘는 파일을 pax 가 싣는 모양.
        val tar = TarBytes().header("PaxHeader", 'x', size = pax.size.toLong()).data(pax)
            .header("short", '0', size = 0).data(body).end()
        for (f in allForms("paxsize", tar)) {
            open(f).use { r ->
                val e = r.entries.single()
                assertEquals("긴/경로/이름.bin", e.name, f.name)
                assertEquals(5L, e.declaredSize)
                assertEquals(1_789_207_200_500L, e.lastModified)
                assertContentEquals(body, r.open(e).use { it.readBytes() }, f.name)
            }
        }
    }

    // ---- 머리 자체 ----------------------------------------------------------------

    @Test
    fun `검사합이 틀린 머리는 깨진 파일이다`() {
        val tar = TarBytes().file("a.txt", "a".toByteArray()).header("b.txt", size = 1, corruptChecksum = true)
            .data(byteArrayOf(1)).end()
        for (f in allForms("badsum", tar)) assertFailsWith<IOException>(f.name) { names(f) }
    }

    @Test
    fun `첫 머리가 tar 가 아니면 다루지 않는 형식이다`() {
        val notTar = ByteArray(2048) { (it % 7 + 1).toByte() }
        // 압축 안 한 것은 매직도 검사합도 없어 판별에서 떨어진다.
        assertNull(Archives.detect(FileDocumentSource(file("junk.tar", notTar))))
        assertFailsWith<IllegalStateException> { open(file("junk.tar", notTar)) }
        // 압축은 맞는데 안이 tar 가 아니다.
        for (f in listOf(file("junk.tar.gz", TarBytes.gzip(notTar)), file("junk.tar.xz", TarBytes.xz(notTar)))) {
            assertFailsWith<TarArchiveReader.NotTarException>(f.name) { open(f) }
            assertNull(Archives.detect(FileDocumentSource(f)), f.name)
        }
    }

    /**
     * **앞이 0 으로 찬 파일을 빈 tar 로 보지 않는다.** ISO 이미지는 앞 32 KiB 가 0 이고 `CD001` 이 그 뒤에 온다 —
     * `.iso` 도 목록에서 '압축' 종류라 이 리더에 닿는다. 앞머리만 보면 '항목 0개' 가 뜬다. 진짜 빈 tar(끝까지 0,
     * GNU tar 의 막 크기 10,240바이트)는 그대로 빈 목록이다.
     */
    @Test
    fun `앞이 0 인 디스크 이미지는 빈 tar 가 아니다`() {
        val iso = ByteArray(40 * 1024).also { "CD001".toByteArray().copyInto(it, 32 * 1024 + 1) }
        val f = file("disk.iso", iso)
        assertNull(Archives.detect(FileDocumentSource(f)))
        assertFailsWith<TarArchiveReader.NotTarException> { open(f) }
        // 1 MiB 를 넘는 0 도 빈 tar 가 아니다(디스크 이미지의 빈 앞부분).
        val zeros = file("zeros.img", ByteArray(TarLimits.MAX_EMPTY_TAR_BYTES + 4096))
        assertFailsWith<TarArchiveReader.NotTarException> { open(zeros) }

        val empty = file("blocked-empty.tar", ByteArray(10240))
        assertEquals(ArchiveKind.TAR, Archives.detect(FileDocumentSource(empty)))
        open(empty).use { assertTrue(it.entries.isEmpty()) }
        val emptyGz = file("blocked-empty.tar.gz", TarBytes.gzip(ByteArray(10240)))
        open(emptyGz).use { assertTrue(it.entries.isEmpty()) }
    }

    @Test
    fun `잘린 tar 는 깨진 파일이다`() {
        val whole = TarBytes().file("a.txt", ByteArray(4000) { 1 }).end()
        for (f in allForms("cut", whole.copyOf(512 + 1000))) assertFailsWith<IOException>(f.name) { names(f) }
    }

    @Test
    fun `크기 칸 — 8진이 아닌 글자와 음수 256진은 깨진 파일이고 양수 256진은 읽는다`() {
        val garbage = TarBytes().header("a", sizeField = "0000000009\u0000\u0000".toByteArray()).end()
        assertFailsWith<IOException> { names(file("oct.tar", garbage)) }
        val negative = ByteArray(12) { 0xFF.toByte() }
        assertFailsWith<IOException> { names(file("neg.tar", TarBytes().header("a", sizeField = negative).end())) }
        val big = ByteArray(12).also { it[0] = 0x80.toByte(); it[11] = 5 }
        val tar = TarBytes().header("b256", sizeField = big).data(byteArrayOf(1, 2, 3, 4, 5)).file("next", byteArrayOf(9)).end()
        open(file("b256.tar", tar)).use { r ->
            assertEquals(listOf(5L, 1L), r.entries.map { it.declaredSize })
            assertContentEquals(byteArrayOf(9), readAll(r, "next"))
        }
    }

    // ---- 종류마다의 자료 ------------------------------------------------------------

    /**
     * 링크·장치·폴더의 크기 칸을 **믿으면** 그만큼 건너뛰어 다음 머리를 놓친다. POSIX 는 자료가 없다고 적는다 —
     * 뒤의 파일이 멀쩡히 읽혀야 한다.
     */
    @Test
    fun `링크·장치·폴더에 적힌 크기는 무시한다`() {
        val tar = TarBytes()
            .header("sym", '2', size = 700)
            .header("chr", '3', size = 1234)
            .header("dir/", '5', size = 999)
            .file("after.txt", "after".toByteArray())
            .end()
        for (f in allForms("sized", tar)) {
            open(f).use { r ->
                val by = r.entries.associateBy { it.name.trimEnd('/') }
                assertTrue(by.getValue("sym").isLink && by.getValue("chr").isLink, f.name)
                assertTrue(by.getValue("dir").isDirectory, f.name)
                assertContentEquals("after".toByteArray(), readAll(r, "after.txt"), f.name)
            }
        }
    }

    /** 하드링크의 크기 칸 — 자료가 없는 옛 모양과, 자료를 실은 모양(pax 없이) 둘 다 다음 머리를 찾는다. */
    @Test
    fun `하드링크의 크기는 다음 칸을 보고 판단한다`() {
        val noData = TarBytes().header("hard", '1', size = 3000).file("after.txt", "A".toByteArray()).end()
        val withData = TarBytes().header("hard", '1', size = 3000).data(ByteArray(3000) { 0x41 })
            .file("after.txt", "B".toByteArray()).end()
        for ((label, tar) in listOf("자료 없음" to noData, "자료 있음" to withData)) {
            for (f in allForms("hard$label", tar)) {
                open(f).use { r ->
                    assertEquals(listOf("hard", "after.txt"), r.entries.map { it.name }, "$label ${f.name}")
                    assertTrue(r.entries[0].isLink)
                    assertEquals(1, readAll(r, "after.txt").size, "$label ${f.name}")
                }
            }
        }
    }

    /** 옛 GNU 희소 머리 뒤의 확장 머리를 건너뛰지 않으면 그것을 자료로, 자료를 머리로 읽는다. */
    @Test
    fun `희소 파일은 풀지 않고 확장 머리까지 건너뛴다`() {
        val tar = TarBytes()
            .header("sparse.img", 'S', size = 512, magic = TarBytes.Magic.GNU) { it[482] = 1 }
            .raw(ByteArray(512))
            .data(ByteArray(512) { 7 })
            .file("after.txt", "after".toByteArray())
            .end()
        val paxSparse = paxRecords("GNU.sparse.major" to "1", "GNU.sparse.minor" to "0")
        val tar2 = TarBytes().header("PaxHeader", 'x', size = paxSparse.size.toLong()).data(paxSparse)
            .file("sparse2.img", ByteArray(600) { 3 }).file("after.txt", "after".toByteArray()).end()
        for (f in allForms("sparse", tar) + allForms("paxsparse", tar2)) {
            open(f).use { r ->
                val first = r.entries.first()
                assertTrue(first.isLink, "${f.name}: 희소 파일은 링크처럼 건너뛴다")
                assertFalse(first.isReadable)
                assertContentEquals("after".toByteArray(), readAll(r, "after.txt"), f.name)
            }
        }
    }

    // ---- 상한 ------------------------------------------------------------------

    @Test
    fun `머리 수가 상한을 넘으면 목록이 끊긴다`() {
        val b = TarBytes()
        repeat(6) { b.file("f$it", byteArrayOf(it.toByte())) }
        val limits = ParseLimits.DEFAULT.copy(maxEntries = 5)
        for (f in allForms("many", b.end())) {
            val e = assertFailsWith<ParseLimitExceededException>(f.name) { open(f, limits).use { it.entries } }
            assertEquals("maxEntries", e.limitName)
        }
    }

    /**
     * **판별은 목록을 세우지 않는다.** 판별의 좁은 상한(`PROBE` 의 항목 1,000개)으로 리더를 만들면 압축 안 한 tar 는
     * 목록부터 세워, 쪽이 1,000장을 넘는 `.cbt` 가 '아카이브가 아니다' 로 떨어졌다.
     */
    @Test
    fun `판별은 항목이 많은 tar 도 tar 로 본다`() {
        val b = TarBytes()
        repeat(ParseLimits.PROBE.maxEntries + 5) { b.file("p$it.png", byteArrayOf(it.toByte())) }
        for (f in allForms("pages", b.end())) {
            assertTrue(Archives.detect(FileDocumentSource(f))?.isTar == true, f.name)
        }
    }

    /**
     * 압축 tar 의 목록은 **처음부터 끝까지 푸는 일**이라 총량 상한에 묶인다(7z 의 '건너뛸 양' 과 같은 규칙).
     * 압축 안 한 tar 는 자료를 자리 옮기기로 건너뛰므로 같은 상한에서도 목록이 선다.
     */
    @Test
    fun `압축 tar 의 목록은 푸는 양이 상한에 묶이고 압축 안 한 tar 는 아니다`() {
        val tar = TarBytes().file("big.bin", ByteArray(3 shl 20) { (it % 13).toByte() }).file("tail", byteArrayOf(1)).end()
        val limits = ParseLimits.DEFAULT.copy(maxTotalOutput = 1L shl 20)
        val forms = allForms("scan", tar)
        open(forms[0], limits).use { r -> assertEquals(2, r.entries.size, "압축 안 한 tar 는 목록이 선다") }
        for (f in forms.drop(1)) {
            val e = assertFailsWith<ParseLimitExceededException>(f.name) { open(f, limits).use { it.entries } }
            assertEquals("maxTotalOutput", e.limitName)
        }
    }

    /** 압축폭탄 항목 하나 — 그 항목만 압축비에서 끊기고, 순차 추출은 뒤의 항목을 계속 푼다. */
    @Test
    fun `압축비 상한이 항목마다 걸리고 나머지는 간다`() {
        val tar = TarBytes().file("zero.bin", ByteArray(16 shl 20)).file("ok.txt", "ok".toByteArray()).end()
        val f = file("bomb.tar.gz", TarBytes.gzip(tar))
        assertTrue(f.length() < 100_000, "표본이 폭탄이어야 한다: ${f.length()}")
        open(f).use { r ->
            val e = assertFailsWith<ParseLimitExceededException> { readAll(r, "zero.bin") }
            assertEquals("maxCompressionRatio", e.limitName)
        }
        val c = Collector()
        open(f).use { it.extractSequentially(c) }
        assertEquals(setOf(0), c.failures.keys)
        assertContentEquals("ok".toByteArray(), c.got[1])
    }

    /** solid 7z 와 같은 규칙 — 압축 tar 에서 건너뛰는 것은 '풀어서 버리는 것' 이라 예산에 잡힌다. */
    @Test
    fun `압축 tar 에서 아무것도 고르지 않아도 푼 양이 예산에 잡힌다`() {
        val b = TarBytes()
        repeat(8) { b.file("e$it", ByteArray(64 * 1024) { v -> (v + it).toByte() }) }
        val tar = b.end()
        for (f in allForms("skip", tar)) {
            val budget = EntryBudget(ParseLimits.DEFAULT)
            open(f, budget = budget).use { it.extractSequentially(Collector { false }) }
            val expected = if (f.name.endsWith(".tar")) 0L else 8L * 64 * 1024
            // 압축 tar 는 자료와 채움을 풀어서 버린다(여기서는 64 KiB 가 512 의 배수라 채움이 없다).
            assertEquals(expected, budget.totalOutput, f.name)
        }
    }

    /** xz 머리에 거대한 사전을 적은 파일. 해제기가 메모리를 잡기 전에 거절해야 한다. */
    @Test
    fun `xz 사전이 상한을 넘으면 열기 전에 거절한다`() {
        val xz = TarBytes.xz(TarBytes().file("a", byteArrayOf(1)).end())
        // 블록 머리: [크기/4-1][플래그][필터 ID 0x21][속성 크기 1][사전 코드] … [CRC32].
        // 사전 = (2 | 코드&1) << (코드/2 + 11) — 코드 30 은 128 MiB 다(상한 72 MiB 의 두 배 가까이).
        val at = 12
        val headerSize = ((xz[at].toInt() and 0xFF) + 1) * 4
        assertEquals(0x21, xz[at + 2].toInt(), "표본의 필터가 LZMA2 여야 한다")
        xz[at + 4] = 30
        val crc = CRC32().apply { update(xz, at, headerSize - 4) }.value
        for (i in 0 until 4) xz[at + headerSize - 4 + i] = (crc ushr (8 * i)).toByte()
        assertFailsWith<org.tukaani.xz.MemoryLimitException> { open(file("dict.tar.xz", xz)).use { it.entries } }
    }

    // ---- 취소 ------------------------------------------------------------------

    @Test
    fun `인터럽트가 목록과 순차 추출을 멈춘다`() {
        val b = TarBytes()
        repeat(4) { b.file("e$it", ByteArray(200_000) { v -> (v * it).toByte() }) }
        for (f in allForms("stop", b.end())) {
            try {
                Thread.currentThread().interrupt()
                assertFailsWith<InterruptedIOException>(f.name) { open(f).use { r -> r.extractSequentially(Collector()) } }
            } finally {
                Thread.interrupted()
            }
        }
    }

    // ---- 순차 추출과 진행률 --------------------------------------------------------------

    @Test
    fun `순차 추출이 고른 것만 옳게 내고 진행률이 입력으로 끝난다`() {
        val b = TarBytes()
        for (i in 0 until 10) b.file("d/f$i.bin", ArchiveSamples.payload(i, 3000 + i * 700))
        val tar = b.end()
        for (f in allForms("seq", tar)) {
            val wanted = setOf(1, 4, 9)
            val c = Collector { it.index in wanted }
            val total = open(f).use { r ->
                r.extractSequentially(c)
                r.inputBytesFor(wanted)
            }
            assertEquals(wanted, c.got.keys, f.name)
            for (i in wanted) assertContentEquals(ArchiveSamples.payload(i, 3000 + i * 700), c.got[i], "${f.name} $i")
            assertTrue(c.consumed.zipWithNext().all { (a, z) -> z >= a }, "${f.name}: 진행률이 뒤로 갔다")
            val last = c.consumed.last()
            if (f.name.endsWith(".tar")) {
                // 압축 안 한 tar 는 고른 항목의 자료만 읽는다 — 분모와 정확히 같다.
                assertEquals(wanted.sumOf { (3000 + it * 700).toLong() }, total)
                assertEquals(total, last, f.name)
            } else {
                assertEquals(f.length(), total, "${f.name}: 분모는 파일 크기")
                assertTrue(last in 1..total, "${f.name}: $last / $total")
            }
        }
    }

    /** 채널을 주지 않는 원본(메모리의 바이트)은 압축 안 한 tar 도 앞에서부터 읽는다. */
    @Test
    fun `무작위 접근이 없는 원본에서도 읽힌다`() {
        val tar = TarBytes().file("a.txt", "A".toByteArray()).file("b.txt", "BB".toByteArray()).end()
        Archives.open(ByteArrayDocumentSource(tar, "x.tar")).use { r ->
            assertFalse(r.randomAccess)
            assertEquals(listOf("a.txt", "b.txt"), r.entries.map { it.name })
            assertContentEquals("BB".toByteArray(), readAll(r, "b.txt"))
        }
    }

    /** .cbt — tar 로 묶은 만화도 같은 쪽 규칙을 탄다. */
    @Test
    fun `tar 로 묶은 만화의 쪽`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val tar = TarBytes()
            .file("p10.png", png).file("p2.png", png).file("p1.png", png)
            .file("ComicInfo.xml", "<x/>".toByteArray()).file("__MACOSX/._p1.png", png)
            .header("link.png", '2')
            .end()
        for (f in allForms("comic", tar)) {
            open(f).use { r ->
                assertEquals(listOf("p1.png", "p2.png", "p10.png"), ComicBook.pagesOf(r.entries).map { it.name }, f.name)
            }
        }
    }

    // ---- 도우미 ------------------------------------------------------------------

    // ---- 검토가 더한 것 — 머리 하나의 상한으로는 막히지 않던 것 -----------------------------------------

    /**
     * **이름은 목록이 끝까지 들고 있다.** 머리 하나(PAX 1 MiB·긴 이름 64 KiB)의 상한만 있으면, 같은 긴 이름을 되풀이한
     * 몇 MB 짜리 `.tar.gz` 가 항목 상한까지 곱해져 기가바이트의 이름을 힙에 올린다. 하나의 길이(64 KiB)와 합(16 MiB)을
     * 함께 묶는다.
     */
    @Test
    fun `이름 하나의 길이와 이름 합이 상한에 묶인다`() {
        val longPath = paxRecords("path" to "p/" + "a".repeat(70 * 1024))
        val one = TarBytes().header("PaxHeader", 'x', size = longPath.size.toLong()).data(longPath)
            .file("short", byteArrayOf(1)).end()
        for (f in listOf(file("onelong.tar", one), file("onelong.tar.gz", TarBytes.gzip(one)))) {
            val e = assertFailsWith<ParseLimitExceededException>(f.name) { names(f) }
            assertEquals("tarHeader", e.limitName, f.name)
        }

        val b = TarBytes()
        val entries = TarLimits.MAX_NAME_BYTES_TOTAL / (60 * 1024) + 2
        repeat(entries) {
            val pax = paxRecords("path" to "d$it/" + "n".repeat(60 * 1024))
            b.header("PaxHeader", 'x', size = pax.size.toLong()).data(pax).file("x", byteArrayOf(it.toByte()))
        }
        val many = b.end()
        val gz = file("manynames.tar.gz", TarBytes.gzip(many))
        assertTrue(gz.length() < many.size / 50, "표본이 작아야 공격이다: ${gz.length()}")
        for (f in listOf(file("manynames.tar", many), gz)) {
            val e = assertFailsWith<ParseLimitExceededException>(f.name) { names(f) }
            assertEquals("tarHeader", e.limitName, f.name)
            // 순차 추출(목록 없이 도는 길)도 같은 머리를 지난다.
            val e2 = assertFailsWith<ParseLimitExceededException>(f.name) { open(f).use { it.extractSequentially(Collector()) } }
            assertEquals("tarHeader", e2.limitName, f.name)
        }
    }

    /**
     * **희소 파일 표시는 한 비트다.** `GNU.sparse.*` 열쇠를 값째 모으면 PAX 머리를 이어 붙인 파일이 머리마다 4만 개씩
     * 맵을 키운다. 이름·크기·시각·문자셋 넷 말고는 들고 있지 않는다.
     */
    @Test
    fun `이어 붙인 PAX 머리가 희소 열쇠를 쌓지 않는다`() {
        val keys = (0 until 5_000).map { "GNU.sparse.k$it" to "1" }.toTypedArray()
        val first = PaxHeaders.parse(paxRecords("path" to "a", *keys))
        val second = PaxHeaders.parse(paxRecords("mtime" to "5", *keys))
        val merged = first.merge(second)
        assertTrue(merged.sparse)
        assertEquals(2, merged.keptKeys, "희소 열쇠가 쌓였다")
        assertFalse(PaxHeaders.parse(paxRecords("path" to "a")).sparse)
    }

    /**
     * PAX `mtime` 이 `Long` 밀리초로 담기지 않으면 머리의 시각을 쓴다 — `BigDecimal.toLong()` 은 넘치면 아래 64비트만
     * 남겨 **엉뚱한 날짜**를 만든다. 자릿수가 터무니없이 긴 값은 읽어 보지도 않는다(읽는 비용이 자릿수의 제곱이다).
     */
    @Test
    fun `PAX 시각이 넘치거나 터무니없이 길면 머리의 시각을 쓴다`() {
        // 둘째 값은 **범위 안의 올바른 시각**(2023-11-14)을 PAX 머리 상한(1 MiB) 안의 백만 자리로 적은 것이다 — 읽으면
        // 몇 초가 들고 답은 머리와 다르다. 머리의 시각이 나오면 읽지 않았다는 뜻이다(시간을 재지 않고 가른다).
        for (mtime in listOf("99999999999999999999", "1700000000." + "0".repeat(999_000))) {
            val pax = paxRecords("mtime" to mtime)
            val tar = TarBytes().header("PaxHeader", 'x', size = pax.size.toLong()).data(pax)
                .file("a.txt", "a".toByteArray()).end()
            open(file("mtime${mtime.length}.tar", tar)).use { r ->
                assertEquals(1_789_207_200_000L, r.entries.single().lastModified, "mtime ${mtime.take(24)}…")
            }
        }
        // 짧고 올바른 값은 그대로 읽는다(위 두 경우가 '언제나 머리' 로 통과하지 않게).
        val pax = paxRecords("mtime" to "1700000000.25")
        val tar = TarBytes().header("PaxHeader", 'x', size = pax.size.toLong()).data(pax).file("a.txt", "a".toByteArray()).end()
        open(file("mtime-ok.tar", tar)).use { r -> assertEquals(1_700_000_000_250L, r.entries.single().lastModified) }
    }

    private fun paxRecords(vararg kv: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((k, v) in kv) {
            val body = " $k=$v\n".toByteArray()
            var len = body.size + 1
            while ((len.toString().length + body.size) != len) len = len.toString().length + body.size
            out.write(len.toString().toByteArray())
            out.write(body)
        }
        return out.toByteArray()
    }

    private class Collector(val select: (ArchiveEntry) -> Boolean = { true }) : EntrySink {
        val got = LinkedHashMap<Int, ByteArray>()
        val failures = LinkedHashMap<Int, Throwable>()
        val consumed = ArrayList<Long>()
        private val open = HashMap<Int, ByteArrayOutputStream>()

        override fun begin(entry: ArchiveEntry): OutputStream? {
            if (!entry.isReadable || !select(entry)) return null
            return ByteArrayOutputStream().also { open[entry.index] = it }
        }

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
            val buf = open.remove(entry.index) ?: return
            if (failure != null) failures[entry.index] = failure else got[entry.index] = buf.toByteArray()
        }

        override fun consumed(inputBytes: Long) {
            consumed += inputBytes
        }
    }
}
