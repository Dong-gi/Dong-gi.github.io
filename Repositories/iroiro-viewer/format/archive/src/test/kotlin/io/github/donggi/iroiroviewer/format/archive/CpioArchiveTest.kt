package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * cpio — 머리는 우리가 읽는다([CpioScanner]). 표본은 commons-compress 의 작성기(네 형식 — **옛 이진은 큰 끝만** 쓴다)와
 * 손으로 지은 작은 끝 옛 이진([CpioBytes])이다. 우리 판정이 맞는지는 commons-compress 의 **읽기**와 견준다(차등 시험) —
 * 작성기와 읽기가 같은 라이브러리여도 우리 리더는 그 둘과 따로 쓰였다.
 */
class CpioArchiveTest {

    private val dir: File = Files.createTempDirectory("iroiro-cpio").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private val formats = listOf(
        CpioConstants.FORMAT_NEW,
        CpioConstants.FORMAT_NEW_CRC,
        CpioConstants.FORMAT_OLD_ASCII,
        CpioConstants.FORMAT_OLD_BINARY,
    )

    private val mtime = 1_789_207_200L

    /** 하나의 항목. [type] 은 파일 종류 비트(`C_ISREG` 등). */
    private class Item(val name: String, val data: ByteArray = ByteArray(0), val type: Int = CpioConstants.C_ISREG, val nlink: Long = 1, val ino: Long = 0)

    /** 이름 길이가 1~10 이고 자료가 홀수·짝수인 것을 섞어 채움을 모두 지나게 한다. */
    private val items = listOf(
        Item(".", type = CpioConstants.C_ISDIR),
        Item("d", type = CpioConstants.C_ISDIR),
        Item("d/a.txt", "가나다".toByteArray()),
        Item("d/bb", ByteArray(1) { 7 }),
        Item("d/ccc.bin", ByteArray(1023) { (it % 251).toByte() }),
        Item("empty", ByteArray(0)),
        Item("link", "d/a.txt".toByteArray(), type = CpioConstants.C_ISLNK),
        Item("dev/null", type = CpioConstants.C_ISCHR),
        Item("fifo", type = CpioConstants.C_ISFIFO),
        Item("../../탈출.txt", "x".toByteArray()),
        Item("/etc/passwd", "root".toByteArray()),
    )

    private fun cpio(format: Short, entries: List<Item> = items, encoding: String = "UTF-8"): ByteArray =
        ByteArrayOutputStream().also { b ->
            CpioArchiveOutputStream(b, format, CpioConstants.BLOCK_SIZE, encoding).use { out ->
                for (it in entries) {
                    val e = CpioArchiveEntry(format, it.name, it.data.size.toLong())
                    e.mode = it.type.toLong() or 420
                    e.time = mtime
                    if (it.nlink != 1L) e.numberOfLinks = it.nlink
                    if (it.ino != 0L) e.inode = it.ino
                    if (format == CpioConstants.FORMAT_NEW_CRC) e.chksum = it.data.sumOf { b -> b.toLong() and 0xFF } and 0xFFFF_FFFFL
                    out.putArchiveEntry(e)
                    out.write(it.data)
                    out.closeArchiveEntry()
                }
            }
        }.toByteArray()

    private fun file(name: String, bytes: ByteArray) = File(dir, name).apply { writeBytes(bytes) }

    private fun open(f: File, limits: ParseLimits = ParseLimits.DEFAULT) = Archives.open(FileDocumentSource(f), limits, EntryBudget(limits))

    @Test
    fun `네 형식의 목록과 종류와 내용`() {
        val forms = formats.map { file("all$it.cpio", cpio(it)) } + file("le.cpio", CpioBytes.binaryLittleEndian(items.map { it.name to it.data }, items.map { it.type }))
        for (f in forms) {
            open(f).use { r ->
                assertEquals(ArchiveKind.CPIO, r.kind, f.name)
                assertTrue(r.randomAccess, "${f.name}: 압축 안 한 파일이라 번호로 연다")
                val by = r.entries.associateBy { it.name }
                assertEquals(items.map { it.name }, r.entries.map { it.name }, f.name)
                assertTrue(by.getValue(".").isRootDirectory, f.name)
                assertTrue(by.getValue("d").isDirectory, f.name)
                for (n in listOf("link", "dev/null", "fifo")) {
                    assertTrue(by.getValue(n).isLink, "${f.name} $n: 링크·장치는 링크로 센다")
                    assertFalse(by.getValue(n).isReadable, "${f.name} $n")
                }
                assertNull(by.getValue("../../탈출.txt").safeName, f.name)
                assertEquals("etc/passwd", by.getValue("/etc/passwd").safeName, f.name)
                for (it in items.filter { i -> i.type == CpioConstants.C_ISREG && ArchiveEntry.sanitize(i.name) != null }) {
                    val e = by.getValue(it.name)
                    assertEquals(it.data.size.toLong(), e.declaredSize, "${f.name} ${it.name}")
                    assertEquals(mtime * 1000, e.lastModified, "${f.name} ${it.name}")
                    assertContentEquals(it.data, r.open(e).use { s -> s.readBytes() }, "${f.name} ${it.name}")
                }
            }
        }
    }

    /** 우리 리더와 commons-compress 의 읽기가 같은 이름·같은 바이트를 낸다(작은 끝 옛 이진도 — 그쪽은 둘 다 읽는다). */
    @Test
    fun `commons-compress 의 읽기와 같은 답을 낸다`() {
        val forms = formats.map { cpio(it) } + CpioBytes.binaryLittleEndian(items.map { it.name to it.data }, items.map { it.type })
        for ((i, bytes) in forms.withIndex()) {
            val theirs = ArrayList<Pair<String, ByteArray>>()
            CpioArchiveInputStream(bytes.inputStream(), "UTF-8").use { input ->
                while (true) {
                    val e = input.nextEntry ?: break
                    theirs += e.name to input.readBytes()
                }
            }
            val ours = ArrayList<Pair<String, ByteArray>>()
            Archives.open(ByteArrayDocumentSource(bytes, "x$i.cpio")).use { r ->
                r.extractSequentially(object : EntrySink {
                    var buf = ByteArrayOutputStream()
                    override fun begin(entry: ArchiveEntry): OutputStream? {
                        // 링크의 자료(대상 경로)도 견주려고 이름만 보고 받는다 — 리더는 읽을 수 없는 항목을 실패로 돌린다.
                        buf = ByteArrayOutputStream()
                        return if (entry.isReadable) buf else null
                    }

                    override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
                        assertNull(failure, entry.name)
                        ours += entry.name to buf.toByteArray()
                    }
                })
                val readableNames = r.entries.filter { it.isReadable }.map { it.name }
                assertEquals(readableNames, ours.map { it.first }, "형식 $i")
            }
            val theirsReadable = theirs.filter { (n, _) -> n in ours.map { it.first } }
            assertEquals(theirsReadable.map { it.first }, ours.map { it.first }, "형식 $i")
            for ((a, b) in theirsReadable.zip(ours)) assertContentEquals(a.second, b.second, "형식 $i ${a.first}")
        }
    }

    /**
     * **새 형식의 하드링크** — 자료는 마지막 하나에만 있고 앞의 것은 크기 0 이다(GNU cpio 의 `newc`). 앞의 것을 파일로 풀면
     * 내용 없는 사본이 생긴다 — 링크로 센다. 옛 형식은 링크마다 자료를 실으므로 크기 0 은 정말 빈 파일이다.
     */
    @Test
    fun `새 형식의 크기 0 하드링크는 링크다`() {
        val body = "본문".toByteArray()
        val linked = listOf(Item("first", ByteArray(0), nlink = 2, ino = 77), Item("second", body, nlink = 2, ino = 77))
        for (format in listOf(CpioConstants.FORMAT_NEW, CpioConstants.FORMAT_NEW_CRC)) {
            open(file("hard$format.cpio", cpio(format, linked))).use { r ->
                val (first, second) = r.entries
                assertTrue(first.isLink && !first.isReadable, "형식 $format")
                assertTrue(second.isReadable, "형식 $format")
                assertContentEquals(body, r.open(second).use { it.readBytes() })
            }
        }
        open(file("hardodc.cpio", cpio(CpioConstants.FORMAT_OLD_ASCII, linked))).use { r ->
            assertTrue(r.entries.all { it.isReadable && !it.isLink }, "옛 형식은 그대로 파일이다")
        }
    }

    /** `070702` 의 검사값은 자료 바이트의 합이다. 다 읽었을 때 다르면 깨진 항목이다 — 번호로 열 때도, 순차로 풀 때도. */
    @Test
    fun `검사값이 맞지 않으면 그 항목이 깨졌다`() {
        val marker = "MARKER-0123456789".toByteArray()
        val bytes = cpio(CpioConstants.FORMAT_NEW_CRC, listOf(Item("a", marker), Item("b", "ok".toByteArray())))
        val at = indexOf(bytes, marker)
        bytes[at + 3] = (bytes[at + 3] + 1).toByte()
        val f = file("crc.cpio", bytes)
        open(f).use { r ->
            assertFailsWith<IOException> { r.open(r.entries[0]).use { it.readBytes() } }
            assertContentEquals("ok".toByteArray(), r.open(r.entries[1]).use { it.readBytes() })
        }
        val c = Collector()
        open(f).use { it.extractSequentially(c) }
        assertNotNull(c.failures[0])
        assertContentEquals("ok".toByteArray(), c.got[1])
        // 메모리의 바이트(스트림 길)도 같다.
        Archives.open(ByteArrayDocumentSource(bytes, "crc.cpio")).use { r ->
            assertFailsWith<IOException> { r.open(r.entries[0]).use { it.readBytes() } }
        }
    }

    @Test
    fun `CP949 로 적은 이름`() {
        val cp949 = EntryNameDecoder.cp949 ?: return
        val bytes = cpio(CpioConstants.FORMAT_NEW, listOf(Item("한글이름.txt", "가".toByteArray(cp949))), encoding = cp949.name())
        open(file("cp949.cpio", bytes)).use { r ->
            assertEquals("한글이름.txt", r.entries.single().name)
            assertEquals(cp949.name(), r.entries.single().nameCharset)
        }
    }

    // ---- 깨진 것·상한·취소 --------------------------------------------------------------

    @Test
    fun `깨진 머리와 잘린 파일은 깨진 파일이다`() {
        val good = cpio(CpioConstants.FORMAT_NEW, listOf(Item("a.txt", ByteArray(500) { 1 }), Item("b.txt", "b".toByteArray())))
        // 첫 항목의 자료 한가운데에서 잘렸다.
        assertFailsWith<IOException> { open(file("cut.cpio", good.copyOf(300))).use { it.entries } }
        // 둘째 머리의 매직이 깨졌다.
        val second = indexOf(good, "070701".toByteArray(), from = 6)
        val broken = good.copyOf().also { it[second + 5] = 'x'.code.toByte() }
        assertFailsWith<IOException> { open(file("magic.cpio", broken)).use { it.entries } }
        // 숫자 칸에 16진이 아닌 글자.
        val digits = good.copyOf().also { it[10] = 'z'.code.toByte() }
        assertNull(Archives.detect(FileDocumentSource(file("digits.cpio", digits))), "판별부터 cpio 가 아니다")
        // 끝 표시 없이 머리 경계에서 끝난 것은 끝으로 본다.
        val trailer = indexOf(good, "070701".toByteArray(), from = second + 6)
        open(file("notrailer.cpio", good.copyOf(trailer))).use { r -> assertEquals(listOf("a.txt", "b.txt"), r.entries.map { it.name }) }
    }

    /** 이름 길이 칸은 4 GiB 까지 적을 수 있다 — 적힌 길이가 상한(64 KiB)을 넘으면 읽기 전에 거절한다. */
    @Test
    fun `긴 이름은 읽기 전에 거절한다`() {
        val long = cpio(CpioConstants.FORMAT_NEW, listOf(Item("n".repeat(70_000), "x".toByteArray())))
        val e = assertFailsWith<ParseLimitExceededException> { open(file("long.cpio", long)).use { it.entries } }
        assertEquals("cpioHeader", e.limitName)
    }

    /**
     * 파일에 남은 것보다 긴 이름 길이는 **상한이 아니라 깨진 머리다.** 700바이트짜리 newc 의 이름 길이 칸이 `FFFFFFFF` 면 예전에는
     * '너무 크다' 로 보였다 — 그 이름은 그 파일에 있을 수 없다. 남은 길이를 모르는 원본(메모리의 바이트)에서는 그대로
     * 상한이 읽기 전에 거절한다.
     */
    @Test
    fun `파일보다 길다고 적힌 이름은 깨진 파일이다`() {
        val good = cpio(CpioConstants.FORMAT_NEW, listOf(Item("a.txt", ByteArray(400) { 1 })))
        // 새 ASCII 머리의 열두 번째 칸(이름 길이) — 매직 6바이트 뒤 8자리씩.
        val bad = good.copyOf(700).also { "FFFFFFFF".toByteArray().copyInto(it, 6 + 11 * 8) }
        val e = assertFailsWith<IOException> { open(file("bad.cpio", bad)).use { it.entries } }
        assertFalse(e is ParseLimitExceededException, "깨진 머리를 '너무 크다' 로 말했다 — $e")
        val e2 = assertFailsWith<ParseLimitExceededException> {
            Archives.open(ByteArrayDocumentSource(bad, "bad.cpio")).use { it.entries }
        }
        assertEquals("cpioHeader", e2.limitName)
    }

    @Test
    fun `항목 수 상한이 목록에서 걸린다`() {
        val many = cpio(CpioConstants.FORMAT_NEW, (0 until 12).map { Item("f$it", byteArrayOf(it.toByte())) })
        val limits = ParseLimits.DEFAULT.copy(maxEntries = 10)
        val e = assertFailsWith<ParseLimitExceededException> { open(file("many.cpio", many), limits).use { it.entries } }
        assertEquals("maxEntries", e.limitName)
    }

    @Test
    fun `인터럽트가 순차 추출을 멈춘다`() {
        val f = file("stop.cpio", cpio(CpioConstants.FORMAT_NEW))
        try {
            Thread.currentThread().interrupt()
            assertFailsWith<InterruptedIOException> { open(f).use { it.extractSequentially(Collector()) } }
        } finally {
            Thread.interrupted()
        }
    }

    /** 순차 추출의 진행률 — 압축 안 한 파일이라 고른 항목의 자료를 읽은 양이고 분모와 정확히 같다. */
    @Test
    fun `순차 추출이 고른 것만 내고 진행률이 분모에서 끝난다`() {
        val f = file("seq.cpio", cpio(CpioConstants.FORMAT_NEW))
        open(f).use { r ->
            val wanted = r.entries.filter { it.name == "d/a.txt" || it.name == "d/ccc.bin" }.map { it.index }.toSet()
            val c = Collector { it.index in wanted }
            r.extractSequentially(c)
            assertEquals(wanted, c.got.keys)
            assertEquals(r.inputBytesFor(wanted), c.consumed.last())
            assertEquals("가나다".toByteArray().size + 1023L, r.inputBytesFor(wanted))
        }
    }

    /** 만화의 쪽도 같은 규칙이다(이름의 자연 정렬). */
    @Test
    fun `cpio 로 묶은 그림이 쪽이 된다`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val f = file("pages.cpio", cpio(CpioConstants.FORMAT_NEW, listOf(Item("p10.png", png), Item("p2.png", png), Item("p1.png", png))))
        open(f).use { r -> assertEquals(listOf("p1.png", "p2.png", "p10.png"), ComicBook.pagesOf(r.entries).map { it.name }) }
    }

    // ---- 도우미 ----------------------------------------------------------------------

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        error("찾지 못했다")
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

/**
 * **작은 끝** 옛 이진 cpio(리눅스·x86 의 `cpio -H bin`)를 짓는다 — commons-compress 의 작성기는 큰 끝만 쓴다. 낱말은 작은 끝,
 * 32비트 값은 높은 낱말이 먼저(PDP-11 의 차례), 이름 뒤와 자료 뒤를 짝수 자리로 채운다. commons-compress 의 **읽기**가 이것을
 * 읽는지 `CpioArchiveTest` 가 확인한다(그쪽은 두 차례를 다 읽는다).
 */
internal object CpioBytes {
    fun binaryLittleEndian(entries: List<Pair<String, ByteArray>>, types: List<Int>? = null, mtime: Long = 1_789_207_200L): ByteArray {
        val out = ByteArrayOutputStream()
        fun word(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }
        fun long(v: Long) {
            word(((v shr 16) and 0xFFFF).toInt())
            word((v and 0xFFFF).toInt())
        }
        fun header(name: ByteArray, mode: Int, size: Int, ino: Int) {
            word(0x71C7)
            word(0)
            word(ino)
            word(mode)
            word(0)
            word(0)
            word(1)
            word(0)
            long(mtime)
            word(name.size + 1)
            long(size.toLong())
            out.write(name)
            out.write(0)
            if ((26 + name.size + 1) % 2 == 1) out.write(0)
        }
        entries.forEachIndexed { i, (n, data) ->
            header(n.toByteArray(), (types?.get(i) ?: CpioConstants.C_ISREG) or 420, data.size, i + 1)
            out.write(data)
            if (data.size % 2 == 1) out.write(0)
        }
        header(CpioConstants.CPIO_TRAILER.toByteArray(), 0, 0, 0)
        return out.toByteArray()
    }
}
