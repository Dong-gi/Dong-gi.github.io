package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
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
import kotlin.test.assertTrue

/**
 * ar — 머리는 우리가 읽는다([ArScanner]). BSD 모양은 commons-compress 의 작성기가, GNU 모양(긴 이름 표 `//`·기호 표 `/`)은
 * 손으로 짓는다([ArBytes]) — commons-compress 는 GNU 모양을 쓰지 못하고 읽기만 한다. 우리 판정을 그 **읽기**와 견준다.
 */
class ArArchiveTest {

    private val dir: File = Files.createTempDirectory("iroiro-ar").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private val mtime = 1_789_207_200L

    /** 짧은 이름·긴 이름(16자 넘음)·공백이 든 이름·홀수 자료·빈 자료. */
    private val items = listOf(
        "a.o" to "짝수 자료!".toByteArray().copyOf(12),
        "odd.txt" to ByteArray(7) { 'x'.code.toByte() },
        "a-very-long-member-name.txt" to ByteArray(1001) { (it % 13).toByte() },
        "with space.txt" to "space".toByteArray(),
        "empty" to ByteArray(0),
    )

    private fun bsd(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { b ->
        ArArchiveOutputStream(b).use { out ->
            out.setLongFileMode(ArArchiveOutputStream.LONGFILE_BSD)
            for ((n, data) in entries) {
                out.putArchiveEntry(ArArchiveEntry(n, data.size.toLong(), 0, 0, 33188, mtime))
                out.write(data)
                out.closeArchiveEntry()
            }
        }
    }.toByteArray()

    private fun file(name: String, bytes: ByteArray) = File(dir, name).apply { writeBytes(bytes) }

    private fun open(f: File, limits: ParseLimits = ParseLimits.DEFAULT) = Archives.open(FileDocumentSource(f), limits, EntryBudget(limits))

    @Test
    fun `BSD 와 GNU 모양의 목록과 내용`() {
        for (f in listOf(file("bsd.ar", bsd(items)), file("gnu.ar", ArBytes.gnu(items)))) {
            open(f).use { r ->
                assertEquals(ArchiveKind.AR, r.kind, f.name)
                assertTrue(r.randomAccess, f.name)
                assertEquals(items.map { it.first }, r.entries.map { it.name }, "${f.name}: 기호 표·긴 이름 표는 항목이 아니다")
                for ((e, item) in r.entries.zip(items)) {
                    assertTrue(e.isReadable && !e.isDirectory && !e.isLink, "${f.name} ${e.name}")
                    assertEquals(item.second.size.toLong(), e.declaredSize, "${f.name} ${e.name}")
                    assertEquals(mtime * 1000, e.lastModified, "${f.name} ${e.name}")
                    assertContentEquals(item.second, r.open(e).use { it.readBytes() }, "${f.name} ${e.name}")
                }
            }
        }
    }

    @Test
    fun `commons-compress 의 읽기와 같은 답을 낸다`() {
        for ((label, bytes) in listOf("bsd" to bsd(items), "gnu" to ArBytes.gnu(items))) {
            val theirs = ArrayList<Pair<String, ByteArray>>()
            ArArchiveInputStream(bytes.inputStream()).use { input ->
                while (true) {
                    val e = input.nextEntry ?: break
                    // 그쪽은 GNU 기호 표(`/`)를 이름 없는 항목으로 낸다. 항목이 아니다.
                    if (e.name.isEmpty()) continue
                    theirs += e.name to input.readBytes()
                }
            }
            val c = Collector()
            Archives.open(ByteArrayDocumentSource(bytes, "$label.ar")).use { r ->
                r.extractSequentially(c)
                assertEquals(theirs.map { it.first }, r.entries.map { it.name }, label)
            }
            for ((i, t) in theirs.withIndex()) assertContentEquals(t.second, c.got[i], "$label ${t.first}")
        }
    }

    /** 데비안 꾸러미 — 첫 항목이 `debian-binary`. 안의 tar 들은 열지 않는다(압축 파일 안의 압축 파일이다). */
    @Test
    fun `deb 는 이름표만 다르고 안의 tar 는 항목이다`() {
        val control = TarBytes.gzip(TarBytes().file("./control", "Package: x\n".toByteArray()).end())
        val data = StreamBytes.xz(TarBytes().file("./usr/bin/x", "#!/bin/sh\n".toByteArray()).end())
        for ((label, bytes) in listOf(
            "bsd" to bsd(listOf("debian-binary" to "2.0\n".toByteArray(), "control.tar.gz" to control, "data.tar.xz" to data)),
            "gnu" to ArBytes.gnu(listOf("debian-binary" to "2.0\n".toByteArray(), "control.tar.gz" to control, "data.tar.xz" to data), symbolTable = false),
        )) {
            val f = file("pkg-$label.deb", bytes)
            assertEquals(ArchiveKind.DEB, Archives.detect(FileDocumentSource(f)), label)
            open(f).use { r ->
                assertEquals(ArchiveKind.DEB, r.kind, label)
                assertEquals("DEB", r.kind.label)
                assertEquals(listOf("debian-binary", "control.tar.gz", "data.tar.xz"), r.entries.map { it.name }, label)
                assertContentEquals(data, r.open(r.entries[2]).use { it.readBytes() }, label)
            }
        }
    }

    @Test
    fun `깨진 머리와 잘린 파일은 깨진 파일이다`() {
        val good = bsd(listOf("a.txt" to ByteArray(300) { 1 }, "b.txt" to "b".toByteArray()))
        // 첫 항목의 자료 한가운데에서 잘렸다.
        assertFailsWith<IOException> { open(file("cut.ar", good.copyOf(200))).use { it.entries } }
        // 머리 끝 표시(`` `\n ``)가 깨졌다.
        assertFailsWith<IOException> { open(file("fmag.ar", good.copyOf().also { it[8 + 58] = 'x'.code.toByte() })).use { it.entries } }
        // 크기 칸에 숫자가 아닌 글자.
        assertFailsWith<IOException> { open(file("size.ar", good.copyOf().also { it[8 + 50] = 'k'.code.toByte() })).use { it.entries } }
        // GNU 긴 이름이 표 밖을 가리킨다 / 표가 없다.
        val noTable = "!<arch>\n".toByteArray() + ArBytes.header("/99", 1) + byteArrayOf(1, '\n'.code.toByte())
        assertFailsWith<IOException> { open(file("notable.ar", noTable)).use { it.entries } }
        // BSD 긴 이름이 항목보다 길다.
        val bsdLong = "!<arch>\n".toByteArray() + ArBytes.header("#1/50", 10) + ByteArray(10)
        assertFailsWith<IOException> { open(file("bsdlong.ar", bsdLong)).use { it.entries } }
    }

    @Test
    fun `긴 이름은 상한에서 거절한다`() {
        val name = "n".repeat(70_000)
        val bsdLong = bsd(listOf(name to "x".toByteArray()))
        val e = assertFailsWith<ParseLimitExceededException> { open(file("long.ar", bsdLong)).use { it.entries } }
        assertEquals("arHeader", e.limitName)
        // 적힌 크기가 상한(16 MiB)을 넘는 GNU 이름 표 — 읽기 전에 거절한다. 남은 길이를 모르는 원본(메모리의 바이트)이라야
        // 상한이다 — 파일이면 '파일보다 길다' 가 먼저다(아래).
        val table = "!<arch>\n".toByteArray() + ArBytes.header("//", 64L shl 20) + ByteArray(100)
        val e2 = assertFailsWith<ParseLimitExceededException> {
            Archives.open(ByteArrayDocumentSource(table, "table.ar")).use { it.entries }
        }
        assertEquals("arHeader", e2.limitName)
    }

    /**
     * 파일에 남은 것보다 길다고 적힌 길이는 **깨진 머리다** — 상한이 아니다. '너무 크다' 로 보이면 화면의 말과 권하는 일이 달라진다.
     * GNU 이름 표(`//`)와 BSD 긴 이름(`#1/`) 둘 다 읽기 전에 거절한다.
     */
    @Test
    fun `파일보다 길다고 적힌 이름 표와 긴 이름은 깨진 파일이다`() {
        val table = "!<arch>\n".toByteArray() + ArBytes.header("//", 64L shl 20) + ByteArray(100)
        val bsdHuge = "!<arch>\n".toByteArray() + ArBytes.header("#1/999999", 9_999_999) + ByteArray(100)
        for ((n, bytes) in listOf("table.ar" to table, "bsdhuge.ar" to bsdHuge)) {
            val e = assertFailsWith<IOException>(n) { open(file(n, bytes)).use { it.entries } }
            assertFalse(e is ParseLimitExceededException, "$n: 깨진 머리를 '너무 크다' 로 말했다 — $e")
        }
    }

    /**
     * 맥의 BSD ar(cctools `ranlib`)는 기호 표를 `#1/20` 긴 이름 `__.SYMDEF SORTED`(NUL 채움)로 적는다고 알려져 있다 — 그 도구로
     * 만든 표본은 없어 모양을 손으로 짓는다. 이름 칸(`#1/20`)만 보면 보통 파일로 지나가 목록에 기호 표가 뜬다. commons-compress 의
     * BSD 작성기가 쓰는 모양(`#1/16`, 채움 없음)과 짧은 이름(`__.SYMDEF`)도 함께 거른다.
     */
    @Test
    fun `BSD 의 긴 이름으로 적힌 기호 표는 항목이 아니다`() {
        val symName = "__.SYMDEF SORTED".toByteArray() + ByteArray(4)
        val symData = ByteArray(36) { 7 }
        val realName = "real.o".toByteArray() + ByteArray(6)
        val cctools = "!<arch>\n".toByteArray() +
            ArBytes.header("#1/20", (symName.size + symData.size).toLong()) + symName + symData +
            ArBytes.header("__.SYMDEF", 8) + ByteArray(8) +
            ArBytes.header("#1/12", (realName.size + 5).toLong()) + realName + "hello".toByteArray() + byteArrayOf('\n'.code.toByte())
        val compress = bsd(listOf("__.SYMDEF SORTED" to symData, "real.o" to "hello".toByteArray()))
        for ((n, bytes) in listOf("cctools.a" to cctools, "compress.a" to compress)) {
            open(file(n, bytes)).use { r ->
                assertEquals(listOf("real.o"), r.entries.map { it.name }, n)
                assertContentEquals("hello".toByteArray(), r.open(r.entries.single()).use { it.readBytes() }, n)
            }
        }
    }

    /**
     * 긴 이름 표를 `__.SYMDEF` 로 시작하게 짓고 줄바꿈 없이 길게 두면, 그것을 가리키는 머리(`/0`)마다 표를 끝까지 훑고 복사했다 — 이름이
     * 기호 표로 풀려 항목 수에도 이름 상한에도 세지 않았다(17 MB `.ar` 하나로 목록이 몇 분). 이름 하나의 상한(64 KiB) 안에서
     * 끝나지 않는 이름은 상한이다 — 첫 머리에서 멈춘다.
     */
    @Test
    fun `긴 이름 표의 끝나지 않는 이름은 상한에서 멈춘다`() {
        // 짝수 길이로 둔다(홀수면 채움 한 바이트가 뒤따라야 한다).
        val table = "__.SYMDEF".toByteArray() + ByteArray(200_001) { 'x'.code.toByte() }
        val bytes = ByteArrayOutputStream().apply {
            write("!<arch>\n".toByteArray())
            write(ArBytes.header("//", table.size.toLong()))
            write(table)
            repeat(1_000) { write(ArBytes.header("/0", 0)) }
        }.toByteArray()
        val e = assertFailsWith<ParseLimitExceededException> { open(file("symdef.ar", bytes)).use { it.entries } }
        assertEquals("arHeader", e.limitName)
    }

    /** 항목이 아닌 멤버(기호 표·긴 이름 표)는 상한([ArFormat.MAX_META_MEMBERS])까지만 지나간다. 긴 이름 표는 하나다. */
    @Test
    fun `항목이 아닌 멤버에도 상한이 있다`() {
        fun withSymbolTables(n: Int) = ByteArrayOutputStream().apply {
            write("!<arch>\n".toByteArray())
            repeat(n) {
                write(ArBytes.header("/", 8))
                write(ByteArray(8))
            }
            write(ArBytes.header("a.o/", 2))
            write("hi".toByteArray())
        }.toByteArray()
        val e = assertFailsWith<ParseLimitExceededException> {
            open(file("many.ar", withSymbolTables(ArFormat.MAX_META_MEMBERS + 1))).use { it.entries }
        }
        assertEquals("arHeader", e.limitName)
        open(file("few.ar", withSymbolTables(3))).use { r -> assertEquals(listOf("a.o"), r.entries.map { it.name }) }

        val table = "abc/\n\n".toByteArray()
        val two = "!<arch>\n".toByteArray() + ArBytes.header("//", 6) + table + ArBytes.header("//", 6) + table +
            ArBytes.header("/0", 2) + "hi".toByteArray()
        assertFailsWith<IOException> { open(file("two-tables.ar", two)).use { it.entries } }
    }

    /** 기호 표 판정은 앞 아홉 바이트로 — 이름 전체를 글로 바꾸지 않는다. */
    @Test
    fun `기호 표 판정은 앞머리로 한다`() {
        assertTrue(ArFormat.isSymbolTable("__.SYMDEF".toByteArray()))
        assertTrue(ArFormat.isSymbolTable("__.SYMDEF SORTED".toByteArray() + ByteArray(4)))
        assertTrue(ArFormat.isSymbolTable("__.SYMDEF_64".toByteArray()))
        assertFalse(ArFormat.isSymbolTable("__.SYMDE".toByteArray()))
        assertFalse(ArFormat.isSymbolTable("x__.SYMDEF".toByteArray()))
        assertFalse(ArFormat.isSymbolTable(ByteArray(0)))
    }

    @Test
    fun `인터럽트가 순차 추출을 멈춘다`() {
        val f = file("stop.ar", bsd(items))
        try {
            Thread.currentThread().interrupt()
            assertFailsWith<InterruptedIOException> { open(f).use { it.extractSequentially(Collector()) } }
        } finally {
            Thread.interrupted()
        }
    }

    private class Collector : EntrySink {
        val got = LinkedHashMap<Int, ByteArray>()
        private val open = HashMap<Int, ByteArrayOutputStream>()

        override fun begin(entry: ArchiveEntry): OutputStream? =
            if (entry.isReadable) ByteArrayOutputStream().also { open[entry.index] = it } else null

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
            assertEquals(null, failure, entry.name)
            got[entry.index] = open.remove(entry.index)!!.toByteArray()
        }
    }
}

/**
 * GNU 모양의 ar 을 짓는다 — 기호 표(`/`), 긴 이름 표(`//`, 이름마다 `이름/\n`), 짧은 이름은 `이름/`, 긴 이름은 `/자리`.
 * GNU ar(binutils)이 정적 라이브러리를 쓰는 모양이다. commons-compress 의 작성기는 이것을 쓰지 못한다.
 */
internal object ArBytes {

    fun header(name: String, size: Long, mtime: Long = 1_789_207_200L): ByteArray {
        val h = ByteArray(60) { ' '.code.toByte() }
        fun put(at: Int, text: String) = text.toByteArray(Charsets.ISO_8859_1).copyInto(h, at)
        put(0, name)
        put(16, mtime.toString())
        put(28, "0")
        put(34, "0")
        put(40, "100644")
        put(48, size.toString())
        h[58] = '`'.code.toByte()
        h[59] = '\n'.code.toByte()
        return h
    }

    fun gnu(entries: List<Pair<String, ByteArray>>, symbolTable: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("!<arch>\n".toByteArray())
        fun member(name: String, data: ByteArray) {
            out.write(header(name, data.size.toLong()))
            out.write(data)
            if (data.size % 2 == 1) out.write('\n'.code)
        }
        if (symbolTable) member("/", ByteArray(8))
        val table = ByteArrayOutputStream()
        val offsets = HashMap<String, Int>()
        for ((n, _) in entries) {
            // GNU 는 15자를 넘거나 공백이 든 이름을 표에 둔다.
            if (n.length > 15 || n.contains(' ')) {
                offsets[n] = table.size()
                table.write("$n/\n".toByteArray(Charsets.UTF_8))
            }
        }
        if (table.size() > 0) member("//", table.toByteArray())
        for ((n, data) in entries) member(offsets[n]?.let { "/$it" } ?: "$n/", data)
        return out.toByteArray()
    }
}
