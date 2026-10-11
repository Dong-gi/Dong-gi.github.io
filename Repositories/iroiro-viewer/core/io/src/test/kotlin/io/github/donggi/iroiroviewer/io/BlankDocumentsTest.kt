package io.github.donggi.iroiroviewer.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 빈 오피스 문서의 서식 옮겨 적기([BlankDocuments]). 서식은 이 모듈의 자산(`src/main/assets/blank/`)을 그대로 읽는다 — 그
 * 폴더는 시험의 입력으로 적어 두었다(`build.gradle.kts`). 견주는 것은 바이트가 아니라 **풀어 낸 항목**이다(압축은 기계마다 같다는 보장이
 * 없다) — 표지 둘 말고는 서식과 같아야 한다.
 */
class BlankDocumentsTest {

    private fun template(kind: String): ByteArray = File("src/main/assets/blank/blank.$kind").readBytes()

    private class Entry(val name: String, val method: Int, val bytes: ByteArray)

    private fun entries(zip: ByteArray): List<Entry> {
        val out = ArrayList<Entry>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val e = input.nextEntry ?: break
                out += Entry(e.name, e.method, input.readBytes())
            }
        }
        return out
    }

    private fun write(kind: String, now: Long = NOW): Pair<ByteArray, List<String>> {
        val sink = ByteArrayOutputStream()
        val names = BlankDocuments.write(ByteArrayInputStream(template(kind)), sink, now)
        return sink.toByteArray() to names
    }

    @Test
    fun `서식은 넷이고 모두 있다`() {
        assertEquals(listOf("docx", "xlsx", "pptx", "hwpx"), BlankDocuments.KINDS)
        for (kind in BlankDocuments.KINDS) assertTrue(template(kind).size > 1000, kind)
    }

    @Test
    fun `만든 시각만 지금으로 바꾸고 나머지는 서식 그대로다`() {
        for (kind in BlankDocuments.KINDS) {
            val before = entries(template(kind))
            val (written, names) = write(kind)
            val after = entries(written)
            assertEquals(before.map { it.name }, after.map { it.name }, kind)
            assertEquals(before.map { it.name }, names, kind)
            assertEquals(before.map { it.method }, after.map { it.method }, "$kind 압축 방식")
            var stamped = 0
            for ((b, a) in before.zip(after)) {
                val was = String(b.bytes, Charsets.UTF_8)
                if (BlankDocuments.STAMP in was) {
                    stamped++
                    assertEquals(was.replace(BlankDocuments.STAMP, STAMP_NOW), String(a.bytes, Charsets.UTF_8), "$kind ${b.name}")
                } else {
                    assertContentEquals(b.bytes, a.bytes, "$kind ${b.name}")
                }
            }
            assertEquals(1, stamped, "$kind — 표지가 든 항목은 하나다(core.xml 또는 content.hpf)")
            assertFalse(BlankDocuments.STAMP in after.joinToString { String(it.bytes, Charsets.UTF_8) }, kind)
        }
    }

    @Test
    fun `HWPX 는 mimetype 이 맨 앞의 압축하지 않은 항목이다`() {
        val after = entries(write("hwpx").first)
        assertEquals("mimetype", after.first().name)
        assertEquals(ZipEntry.STORED, after.first().method)
        assertEquals("application/hwp+zip", String(after.first().bytes, Charsets.US_ASCII))
    }

    @Test
    fun `머리에 크기를 적어 데이터 기술자가 붙지 않는다`() {
        for (kind in BlankDocuments.KINDS) {
            val (written, names) = write(kind)
            // 로컬 머리만 따라 걷는다 — 크기를 머리에 적었으면 다음 머리가 정확히 그 뒤에 온다.
            var at = 0
            var seen = 0
            while (u32(written, at) == LOCAL_SIG) {
                val flags = u16(written, at + 6)
                assertEquals(0, flags and 0x8, "$kind 의 ${names[seen]} 에 데이터 기술자 비트")
                val csize = u32(written, at + 18)
                val nameLen = u16(written, at + 26)
                val extraLen = u16(written, at + 28)
                at += 30 + nameLen + extraLen + csize.toInt()
                seen++
            }
            assertEquals(names.size, seen, "$kind — 머리를 따라 모든 항목을 걸었다")
            assertEquals(CENTRAL_SIG, u32(written, at), "$kind — 그 뒤는 중앙 디렉터리다")
        }
    }

    @Test
    fun `시각은 초까지 UTC 로 적는다`() {
        val core = entries(write("docx", now = 1_760_000_000_999L).first).single { it.name == "docProps/core.xml" }
        assertTrue("2025-10-09T08:53:20Z" in String(core.bytes, Charsets.UTF_8))
    }

    @Test
    fun `받은 흐름을 닫지 않는다`() {
        var closed = false
        val sink = object : FilterOutputStream(ByteArrayOutputStream()) {
            override fun close() {
                closed = true
            }
        }
        BlankDocuments.write(ByteArrayInputStream(template("xlsx")), sink, NOW)
        assertFalse(closed, "임시본은 큐가 내리고 놓는다")
    }

    @Test
    fun `다시 읽어 보기는 이름의 차례와 CRC 까지 본다`() {
        val (written, names) = write("pptx")
        val dir = File(System.getProperty("java.io.tmpdir") ?: ".")
        val file = File.createTempFile("blank", ".pptx", dir)
        try {
            file.writeBytes(written)
            assertTrue(BlankDocuments.verify(file, names))
            assertFalse(BlankDocuments.verify(file, names.reversed()), "차례가 다르면")
            assertFalse(BlankDocuments.verify(file, names.dropLast(1)), "수가 다르면")
            // 압축된 몸의 바이트 하나를 뒤집으면 풀면서 CRC 가 맞지 않는다(또는 풀리지 않는다).
            val broken = written.copyOf()
            val bodyAt = 30 + u16(broken, 26) + u16(broken, 28) + 2
            broken[bodyAt] = (broken[bodyAt].toInt() xor 0x55).toByte()
            file.writeBytes(broken)
            assertFalse(BlankDocuments.verify(file, names))
            file.writeBytes(written.copyOf(written.size / 2))
            assertFalse(BlankDocuments.verify(file, names), "잘린 파일")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `서식의 꼴이 아니면 던진다`() {
        assertFailsWith<IOException> { BlankDocuments.write(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), NOW) }
        assertFailsWith<IOException> {
            BlankDocuments.write(ByteArrayInputStream("이것은 ZIP 이 아니다".toByteArray()), ByteArrayOutputStream(), NOW)
        }
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long =
        if (at + 4 > b.size) -1L else (u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16))

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val STAMP_NOW = "2025-10-09T08:53:20Z"
        const val LOCAL_SIG = 0x04034B50L
        const val CENTRAL_SIG = 0x02014B50L
    }
}
