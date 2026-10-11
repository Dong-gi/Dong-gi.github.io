package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **우리가 아닌 도구가 만든** 압축 스트림을 읽는다 — 오라클이 우리가 아니다(`TarOracleTest` 와 같은 자리).
 *
 * 표본(`src/test/resources/stream/`, 8개 모두 6KB 이하)은 아래 도구를 **그대로 불러** 만들었다. 부른 스크립트
 * (`make_stream_fixtures.sh`)와 `.Z` 작성기(`lzw_z.py`)는 저장소에 넣지 않았다(도구로만 쓴다):
 *
 * | 표본 | 만든 도구 | 무엇을 보나 |
 * |---|---|---|
 * | `hello.txt` | 파이썬 | 아래 표본들의 원문(한글 UTF-8 + 영문 60줄, 5,520바이트, 수정시각 2026-09-01 12:00 UTC) |
 * | `fname.gz` | GNU gzip 1.14 | FNAME 이 한글 UTF-8(`원본 메모.txt`), MTIME |
 * | `multi.gz` | GNU gzip 1.14 둘을 이어 붙임 | 이어 붙인 멤버, 이름은 첫 멤버의 것(`part1.txt`) |
 * | `hello.txt.lzma` | xz 5(`--format=lzma`, liblzma) | 크기 칸이 '모름' 이고 끝 표시가 있는 LZMA-alone |
 * | `hello.txt.Z` | `lzw_z.py`(우리 파이썬 작성기) — **GNU gzip 의 `gzip -dc` 로 풀어 원문과 같은지 확인했다** | 9→10→11비트, 300번째 코드 뒤 CLEAR |
 * | `small.tar.Z` | GNU tar 1.35(`--format=gnu`) + `lzw_z.py`(같은 확인) | tar 를 싼 `.Z` |
 * | `small.tlz` | GNU tar 1.35 + xz 5(`--format=lzma`) | tar 를 싼 LZMA — 매직 없이 이름(`.tlz`)과 머리로 알아본다 |
 * | `blocks.xz` | xz 5(`--block-size=1KiB`) + 0 채움 4바이트 + xz 5 | 스트림 둘·블록 일곱 — 인덱스의 크기 합(6,520) |
 */
class StreamOracleTest {

    private val dir = File(javaClass.classLoader!!.getResource("stream/hello.txt")!!.toURI()).parentFile
    private val hello = File(dir, "hello.txt").readBytes()

    /** 표본을 만들 때 `touch -d '2026-09-01 12:00:00 UTC'` 로 박은 시각. */
    private val stamp = Instant.parse("2026-09-01T12:00:00Z").toEpochMilli()

    private fun open(name: String) = Archives.open(FileDocumentSource(File(dir, name)), ParseLimits.DEFAULT, EntryBudget())

    private fun readOnly(r: ArchiveReader): ByteArray = r.open(r.entries.single()).use { it.readBytes() }

    @Test
    fun `GNU gzip 의 FNAME 과 MTIME 을 읽는다`() {
        open("fname.gz").use { r ->
            assertEquals(ArchiveKind.GZ, r.kind)
            val e = r.entries.single()
            assertEquals("원본 메모.txt", e.name)
            assertEquals("원본 메모.txt", e.safeName)
            assertEquals(stamp, e.lastModified)
            assertEquals(-1L, e.declaredSize, "gzip 의 ISIZE 는 보이지 않는다")
            assertContentEquals(hello, readOnly(r))
        }
    }

    @Test
    fun `이어 붙인 gzip 은 끝까지 풀리고 이름은 첫 멤버의 것이다`() {
        open("multi.gz").use { r ->
            assertEquals("part1.txt", r.entries.single().name)
            assertContentEquals(hello, readOnly(r))
        }
    }

    @Test
    fun `liblzma 가 만든 LZMA 는 이름의 도움으로 알아보고 푼다`() {
        val f = File(dir, "hello.txt.lzma")
        assertEquals(ArchiveKind.LZMA, Archives.detect(FileDocumentSource(f)))
        open("hello.txt.lzma").use { r ->
            assertEquals(ArchiveKind.LZMA, r.kind)
            assertEquals("hello.txt", r.entries.single().name)
            // xz 는 크기 칸을 '모름' 으로 적는다.
            assertEquals(-1L, r.entries.single().declaredSize)
            assertContentEquals(hello, readOnly(r))
        }
    }

    /** 우리 코틀린 `.Z` 작성기가 GNU gzip 으로 확인한 파이썬 작성기와 **같은 바이트**를 낸다 — 시험의 표본이 진짜 모양이다. */
    @Test
    fun `Z 작성기가 GNU gzip 으로 확인한 표본과 같은 바이트를 낸다`() {
        assertContentEquals(File(dir, "hello.txt.Z").readBytes(), ZBytes.compress(hello, 16, blockMode = true, clearAfter = 300))
        assertContentEquals(File(dir, "small.tar.Z").readBytes(), ZBytes.compress(tarOf("small.tar.Z"), 16))
    }

    @Test
    fun `GNU gzip 이 푸는 Z 를 푼다`() {
        open("hello.txt.Z").use { r ->
            assertEquals(ArchiveKind.Z, r.kind)
            assertEquals("hello.txt", r.entries.single().name)
            assertContentEquals(hello, readOnly(r))
        }
    }

    @Test
    fun `GNU tar 를 Z 와 LZMA 로 싼 것이 tar 로 열린다`() {
        for ((name, kind) in listOf("small.tar.Z" to ArchiveKind.TAR_Z, "small.tlz" to ArchiveKind.TAR_LZMA)) {
            assertEquals(kind, Archives.detect(FileDocumentSource(File(dir, name))), name)
            open(name).use { r ->
                assertEquals(kind, r.kind, name)
                assertFalse(r.randomAccess, "$name: 압축 tar 는 스트림이다")
                assertEquals(listOf("a.txt", "dir/", "dir/b.txt"), r.entries.map { it.name }, name)
                assertContentEquals(hello, r.open(r.entries[0]).use { it.readBytes() }, name)
                assertContentEquals(ByteArray(3000) { 'b'.code.toByte() }, r.open(r.entries[2]).use { it.readBytes() }, name)
                assertTrue(r.entries.all { it.lastModified == stamp }, name)
            }
        }
    }

    /** 스트림 둘(블록 다섯 + 둘)과 그 사이의 0 채움 — 인덱스를 끝에서 거꾸로 읽어 크기를 더한다. 푼 것과 같아야 한다. */
    @Test
    fun `xz 의 크기는 스트림마다의 인덱스를 더한 것이다`() {
        open("blocks.xz").use { r ->
            assertEquals(ArchiveKind.XZ, r.kind)
            val e = r.entries.single()
            assertEquals(6_520L, e.declaredSize)
            val body = readOnly(r)
            assertEquals(e.declaredSize, body.size.toLong())
            assertContentEquals(hello + hello.copyOf(1000), body)
        }
    }

    /** 순차 추출(풀기가 쓰는 길)도 같은 바이트를 낸다. 진행률은 파일 크기 안에서 끝난다. */
    @Test
    fun `순차 추출이 같은 바이트를 내고 진행률이 파일 크기에서 끝난다`() {
        for (name in listOf("fname.gz", "multi.gz", "hello.txt.lzma", "hello.txt.Z", "blocks.xz")) {
            val got = ByteArrayOutputStream()
            val consumed = ArrayList<Long>()
            val failures = ArrayList<Throwable>()
            open(name).use { r ->
                r.extractSequentially(object : EntrySink {
                    override fun begin(entry: ArchiveEntry): OutputStream = got
                    override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
                        if (failure != null) failures += failure
                        assertEquals(got.size().toLong(), written)
                    }

                    override fun consumed(inputBytes: Long) {
                        consumed += inputBytes
                    }
                })
                assertTrue(failures.isEmpty(), "$name: $failures")
                val total = r.inputBytesFor(null)
                assertEquals(File(dir, name).length(), total, name)
                assertTrue(consumed.zipWithNext().all { (a, b) -> b >= a }, "$name: 진행률이 뒤로 갔다")
                assertTrue(consumed.last() in 1..total, "$name: ${consumed.last()} / $total")
            }
            val expected = if (name == "blocks.xz") hello + hello.copyOf(1000) else hello
            assertContentEquals(expected, got.toByteArray(), name)
        }
    }

    private fun tarOf(zName: String): ByteArray =
        org.apache.commons.compress.compressors.z.ZCompressorInputStream(File(dir, zName).inputStream()).use { it.readBytes() }
}
