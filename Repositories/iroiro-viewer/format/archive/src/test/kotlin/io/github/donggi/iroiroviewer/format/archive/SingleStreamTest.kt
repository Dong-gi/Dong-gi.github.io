package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.text.Normalizer
import java.util.zip.Deflater
import kotlin.random.Random
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
 * 파일 하나를 싼 압축 스트림 — 항목 하나짜리 아카이브([SingleStreamReader]).
 *
 * 지키는 것: 여섯 형식이 같은 바이트를 낸다 · 이름(FNAME 의 관문과 파일 이름에서 짓기) · 정직한 크기 · **목록은 풀지
 * 않는다** · 폭탄은 상한에서 끊긴다 · 깨진 것은 그 항목의 실패다 · 취소가 닿는다 · 진행률이 입력으로 끝난다.
 */
class SingleStreamTest {

    private val dir: File = Files.createTempDirectory("iroiro-stream").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private val text = ("가나다 single stream 본문 — ".repeat(40) + "\n").repeat(30).toByteArray()

    private fun file(name: String, bytes: ByteArray): File = File(dir, name).apply { writeBytes(bytes) }

    private fun open(f: File, limits: ParseLimits = ParseLimits.DEFAULT, budget: EntryBudget = EntryBudget(limits)) =
        Archives.open(FileDocumentSource(f), limits, budget)

    private fun readOnly(r: ArchiveReader): ByteArray = r.open(r.entries.single()).use { it.readBytes() }

    /** 여섯 형식으로 같은 본문을 싼다. 이름은 각 형식의 흔한 확장자다(LZMA 는 이름이 판별의 도움이다). */
    private fun allCodecs(base: String, body: ByteArray): Map<StreamCodec, File> = mapOf(
        StreamCodec.GZIP to file("$base.gz", StreamBytes.gzip(body)),
        StreamCodec.BZIP2 to file("$base.bz2", StreamBytes.bzip2(body)),
        StreamCodec.XZ to file("$base.xz", StreamBytes.xz(body)),
        StreamCodec.LZ4 to file("$base.lz4", StreamBytes.lz4(body)),
        StreamCodec.LZMA to file("$base.lzma", StreamBytes.lzma(body)),
        StreamCodec.Z to file("$base.Z", ZBytes.compress(body)),
    )

    // ---- 여섯 형식 ------------------------------------------------------------------

    @Test
    fun `여섯 형식이 항목 하나로 열리고 같은 바이트를 낸다`() {
        for ((codec, f) in allCodecs("note.txt", text)) {
            assertEquals(ArchiveKind.streamOf(codec), Archives.detect(FileDocumentSource(f)), f.name)
            open(f).use { r ->
                assertEquals(ArchiveKind.streamOf(codec), r.kind, f.name)
                assertTrue(r.kind.isSingleStream && !r.kind.isTar, f.name)
                assertTrue(r.randomAccess, "${f.name}: 항목이 하나라 앞에 풀어서 버릴 것이 없다")
                assertFalse(r.solid, f.name)
                val e = r.entries.single()
                assertEquals(0, e.index)
                assertEquals("note.txt", e.name, f.name)
                assertEquals("note.txt", e.safeName, f.name)
                assertTrue(e.isReadable, f.name)
                assertFalse(e.isDirectory || e.isLink || e.isEncrypted, f.name)
                assertContentEquals(text, readOnly(r), f.name)
                // 두 번 열어도 같다 — 열 때마다 원본을 새로 연다.
                assertContentEquals(text, readOnly(r), f.name)
            }
        }
    }

    @Test
    fun `순차 추출도 같은 바이트를 내고 진행률이 파일 크기 안에서 끝난다`() {
        for ((_, f) in allCodecs("seq.bin", text)) {
            val c = Collector()
            open(f).use { r ->
                r.extractSequentially(c)
                assertEquals(f.length(), r.inputBytesFor(null), f.name)
                assertEquals(f.length(), r.inputBytesFor(setOf(0)), f.name)
                assertEquals(0L, r.inputBytesFor(emptySet()), "${f.name}: 고르지 않으면 읽을 것이 없다")
            }
            assertTrue(c.failures.isEmpty(), "${f.name}: ${c.failures}")
            assertContentEquals(text, c.got[0], f.name)
            assertTrue(c.consumed.zipWithNext().all { (a, b) -> b >= a }, "${f.name}: 진행률이 뒤로 갔다")
            assertTrue(c.consumed.last() in 1..f.length(), "${f.name}: ${c.consumed.last()} / ${f.length()}")
        }
    }

    @Test
    fun `건너뛰라고 하면 아무것도 풀지 않는다`() {
        for ((_, f) in allCodecs("skip.bin", text)) {
            val budget = EntryBudget()
            val c = Collector { false }
            open(f, budget = budget).use { it.extractSequentially(c) }
            assertTrue(c.got.isEmpty() && c.failures.isEmpty(), f.name)
            assertEquals(0L, budget.totalOutput, f.name)
        }
    }

    @Test
    fun `빈 본문도 빈 항목으로 열린다`() {
        for ((_, f) in allCodecs("empty.txt", ByteArray(0))) {
            open(f).use { r -> assertContentEquals(ByteArray(0), readOnly(r), f.name) }
        }
    }

    /** 파일이 아닌 원본(메모리의 바이트)도 열린다 — 시각만 모른다(압축 파일의 시각이 없다). */
    @Test
    fun `메모리의 바이트로도 열린다`() {
        for ((codec, f) in allCodecs("mem.txt", text)) {
            Archives.open(ByteArrayDocumentSource(f.readBytes(), f.name)).use { r ->
                assertEquals(ArchiveKind.streamOf(codec), r.kind)
                assertContentEquals(text, readOnly(r), f.name)
            }
        }
    }

    // ---- 크기 ------------------------------------------------------------------------

    @Test
    fun `크기는 형식이 파일 전체의 크기를 적을 때만 보인다`() {
        val sizes = allCodecs("size.txt", text).mapValues { (_, f) -> open(f).use { it.entries.single().declaredSize } }
        assertEquals(-1L, sizes[StreamCodec.GZIP], "gzip 의 ISIZE 는 멤버 하나의 2^32 나머지라 보이지 않는다")
        assertEquals(-1L, sizes[StreamCodec.BZIP2])
        assertEquals(-1L, sizes[StreamCodec.LZ4], "LZ4 의 내용 크기는 프레임마다의 값이다")
        assertEquals(-1L, sizes[StreamCodec.Z])
        assertEquals(text.size.toLong(), sizes[StreamCodec.XZ], "xz 는 인덱스의 크기")
        assertEquals(-1L, sizes[StreamCodec.LZMA], "xz 가 만든 LZMA 는 크기 칸이 '모름' 이다")
        val known = file("known.lzma", StreamBytes.lzma(text, knownSize = true))
        open(known).use { r ->
            assertEquals(text.size.toLong(), r.entries.single().declaredSize, "LZMA SDK 모양은 머리에 크기를 적는다")
            assertContentEquals(text, readOnly(r))
        }
    }

    /** gzip 의 ISIZE 를 믿으면 이어 붙인 파일에서 틀린다 — 그래서 보이지 않는다(-1). 실제로는 끝까지 풀린다. */
    @Test
    fun `이어 붙인 gzip 은 끝까지 풀리고 크기를 지어내지 않는다`() {
        val parts = listOf("첫째 ".repeat(500), "둘째 ".repeat(700), "셋째".repeat(10)).map { it.toByteArray() }
        val f = file(
            "multi.gz",
            StreamBytes.gzipMember(parts[0], name = "a.txt".toByteArray()) +
                StreamBytes.gzipMember(parts[1], name = "b.txt".toByteArray()) +
                StreamBytes.gzipMember(parts[2]),
        )
        open(f).use { r ->
            val e = r.entries.single()
            assertEquals("a.txt", e.name, "이름은 첫 멤버의 것이다")
            assertEquals(-1L, e.declaredSize)
            assertContentEquals(parts[0] + parts[1] + parts[2], readOnly(r))
        }
    }

    // ---- 이름 ------------------------------------------------------------------------

    @Test
    fun `이름이 없으면 압축 파일 이름에서 짓는다`() {
        val cases = mapOf(
            "log.txt.gz" to "log.txt",
            "LOG.TXT.GZ" to "LOG.TXT",
            "x.Z" to "x",
            "backup.tgz" to "backup.tar",
            "backup.taz" to "backup.tar",
            "backup.tbz2" to "backup.tar",
            "backup.txz" to "backup.tar",
            "backup.tlz" to "backup.tar",
            "README" to "README~",
            "photo.jpg" to "photo",
            "a .gz" to StreamNames.FALLBACK,
            ".gz" to ".gz~",
        )
        for ((archive, inner) in cases) assertEquals(inner, StreamNames.derived(archive), archive)
        // 실제 리더도 그 이름을 쓴다(gzip 이지만 tar 가 아닌 `.tgz`).
        open(file("notes.tgz", StreamBytes.gzip(text))).use { r ->
            assertEquals(ArchiveKind.GZ, r.kind)
            assertEquals("notes.tar", r.entries.single().name)
        }
    }

    @Test
    fun `gzip 의 FNAME 을 이름으로 쓴다`() {
        fun nameOf(fname: ByteArray, archive: String = "x.gz"): String =
            open(file(archive, StreamBytes.gzip(text, name = fname))).use { it.entries.single().name }
        assertEquals("inner.txt", nameOf("inner.txt".toByteArray()))
        assertEquals("한글 이름.txt", nameOf("한글 이름.txt".toByteArray(Charsets.UTF_8)), "GNU gzip(리눅스) 의 UTF-8")
        val cp949 = EntryNameDecoder.cp949
        if (cp949 != null) assertEquals("한글이름.txt", nameOf("한글이름.txt".toByteArray(cp949)), "한국어 윈도우 도구의 CP949")
        assertEquals("café.txt", nameOf("café.txt".toByteArray(Charsets.ISO_8859_1)), "RFC 1952 의 라틴-1")
    }

    /** FNAME 은 공격자가 적는 값이다. 경로의 마지막 조각만, 제어 문자 없이, 풀기의 관문을 지난 것만 쓴다. */
    @Test
    fun `악의적인 FNAME 은 마지막 조각만 쓰거나 버린다`() {
        fun nameOf(fname: ByteArray): ArchiveEntry =
            open(file("evil.gz", StreamBytes.gzip(text, name = fname))).use { it.entries.single() }
        val cases = mapOf(
            "../../x" to "x",
            "../../../../sdcard/탈출.txt" to "탈출.txt",
            "/etc/passwd" to "passwd",
            "C:\\Windows\\system32\\evil.dll" to "evil.dll",
            "a/b/../c.txt" to "c.txt",
            // 쓸 수 없으면 압축 파일 이름에서 짓는다(`evil.gz` → `evil`).
            ".." to "evil",
            "." to "evil",
            "dir/" to "evil",
            "dir\\" to "evil",
            "trailing. " to "evil",
            "" to "evil",
            "a\u0001b.txt" to "evil",
            "a\u007Fb.txt" to "evil",
            "a\tb.txt" to "evil",
            // 압축 파일 자신과 같은 이름 — '여기에 풀기' 가 자기를 덮어쓸 수 없어 풀리지 않는다.
            "evil.gz" to "evil",
            "EVIL.GZ" to "evil",
        )
        for ((fname, want) in cases) {
            val e = nameOf(fname.toByteArray(Charsets.UTF_8))
            assertEquals(want, e.name, "FNAME '$fname'")
            assertEquals(want, e.safeName, "FNAME '$fname'")
            assertTrue(e.isReadable, "FNAME '$fname'")
        }
        // UTF-8 로 적은 C1 제어 문자(U+0085). `ArchivePath.sanitize` 는 C0·DEL 만 거른다.
        assertEquals("evil", nameOf(byteArrayOf(0x61, 0xC2.toByte(), 0x85.toByte(), 0x62)).name)
        // 상한(4,096바이트)을 넘는 이름은 쓰지 않는다 — 해제는 그대로 된다.
        val long = ByteArray(GzipHeaders.MAX_NAME_BYTES + 1) { 'n'.code.toByte() }
        open(file("long.gz", StreamBytes.gzip(text, name = long))).use { r ->
            assertEquals("long", r.entries.single().name)
            assertContentEquals(text, readOnly(r))
        }
    }

    /**
     * FNAME 이 **풀기에서 압축 파일 자신이 되면** 쓰지 않는다. `a?b.gz` 는 풀기의 관문(`PathRules.sanitize`)에서 `a_b.gz` 가 되고,
     * 대소문자를 가리지 않는 볼륨(FAT SD 카드)에서 그것은 `A_B.GZ` 와 한 파일이다 — 그대로 두면 엔진이 마지막에 막아 그 항목이
     * 풀리지 않는다(예전에는 엔진도 막지 못했다). 압축 파일 이름에서 짓는다.
     */
    @Test
    fun `풀기에서 압축 파일 자신이 되는 FNAME 은 쓰지 않는다`() {
        for (fname in listOf("a?b.gz", "a*b.GZ", " a_b.gz", "a|b.gz", "a_b.gz", "A_B.GZ")) {
            val named = StreamNames.of(fname.toByteArray(), "A_B.GZ")
            assertEquals("A_B", named.name, "FNAME '$fname'")
            assertEquals("압축 파일 이름", named.label, "FNAME '$fname'")
        }
        // 정규화만 다른 이름 — 맥에서 온 한글 이름은 자모로 풀린 NFD 인 일이 흔하다. 압축 파일 이름(NFC)과 같은 이름이다.
        val nfd = Normalizer.normalize("사진.gz", Normalizer.Form.NFD)
        assertTrue(nfd != "사진.gz", "표본이 NFD 가 아니다")
        assertEquals("사진", StreamNames.of(nfd.toByteArray(Charsets.UTF_8), "사진.gz").name)
        // 압축 파일과 겹치지 않는 이름은 적힌 그대로 보인다 — 글자를 바꾸는 것은 풀기의 일이다.
        assertEquals("a?b.txt", StreamNames.of("a?b.txt".toByteArray(), "A_B.GZ").name)
        open(file("A_B.GZ", StreamBytes.gzip(text, name = "a?b.gz".toByteArray()))).use { r ->
            assertEquals("A_B", r.entries.single().name)
            assertContentEquals(text, readOnly(r))
        }
    }

    /**
     * **NUL 없는 이름이 파일 전체에 걸쳐도 메모리가 늘지 않는다.** commons-compress 의 gzip 은 그것을 끝까지 모은다
     * (`GzipHeaders` 의 주석). 여기서는 4 KiB 까지만 들고 버린다.
     *
     * 목록은 **선다** — 판별은 입력 상한(4 MiB)에서 멈춰 안을 보지 못하고, 목록은 이름 칸을 4,096바이트까지만 읽어 '이름 없음' 으로
     * 멈춘다(이름은 압축 파일 이름에서 짓는다). 머리가 깨졌다는 것은 푸는 순간 드러난다. 예전에는 판별이 8 MiB 를 다 읽고 '깨졌다' 로
     * 끝났는데, 그 답은 시계에 달려 있었다(느린 기기에서는 5초에 끊겨 목록이 같은 머리를 끝까지 다시 읽었다).
     */
    @Test
    fun `끝나지 않는 FNAME 은 들고 있지 않는다`() {
        val head = byteArrayOf(0x1F, 0x8B.toByte(), 8, 0x08, 0, 0, 0, 0, 0, 3)
        val f = file("endless.gz", head + ByteArray(8 shl 20) { 'a'.code.toByte() })
        open(f).use { r ->
            assertEquals("endless", r.entries.single().name)
            assertFailsWith<IOException> { readOnly(r) }
        }
        // 머리만 읽는 함수도 상한 안에서 끝난다(이름을 쓸 수 없으면 null).
        val h = GzipHeaders.readAfterId(ByteArrayInputStreamOf(head.copyOfRange(2, head.size) + ByteArray(70_000) { 'b'.code.toByte() } + 0)::next)
        assertNull(h.nameBytes)
    }

    @Test
    fun `FEXTRA·FCOMMENT·FHCRC 가 있는 머리도 읽는다`() {
        val f = file(
            "fields.gz",
            StreamBytes.gzipMember(
                text,
                name = "fields.txt".toByteArray(),
                comment = "설명".toByteArray(),
                extra = ByteArray(300) { it.toByte() },
                headerCrc = true,
                mtime = 1_789_207_200L,
            ),
        )
        open(f).use { r ->
            val e = r.entries.single()
            assertEquals("fields.txt", e.name)
            assertEquals(1_789_207_200_000L, e.lastModified, "MTIME")
            assertContentEquals(text, readOnly(r))
        }
    }

    /** 머리에 시각이 없으면(gzip 의 MTIME 0, 다른 형식) 압축 파일의 시각이다 — bunzip2·unxz·gunzip 이 하는 것과 같다. */
    @Test
    fun `시각이 없으면 압축 파일의 시각을 쓴다`() {
        val stamp = 1_700_000_000_000L
        for ((_, f) in allCodecs("time.txt", text)) {
            assertTrue(f.setLastModified(stamp))
            open(f).use { r -> assertEquals(stamp, r.entries.single().lastModified, f.name) }
        }
    }

    // ---- 목록은 풀지 않는다 ----------------------------------------------------------------

    /**
     * **1 GiB 의 0** 을 싼 gzip(64 MiB 의 0 을 싼 멤버를 열여섯 번 이어 붙였다 — 약 1 MiB). 목록은 앞부분만 읽는다:
     * 머리와 '안이 tar 인가' 를 보는 512바이트(버퍼가 한 번 64 KiB 를 읽는다)뿐이다. 끝까지 풀면 파일 전체를 읽는다.
     */
    @Test
    fun `목록은 폭탄을 풀지 않는다`() {
        val member = StreamBytes.zeroMember(64 shl 20)
        val bomb = ByteArrayOutputStream().apply { repeat(16) { write(member) } }.toByteArray()
        val f = file("zeros.bin.gz", bomb)
        val src = CountingSource(f)
        val budget = EntryBudget()
        Archives.open(src, ParseLimits.DEFAULT, budget).use { r ->
            assertEquals(ArchiveKind.GZ, r.kind)
            assertEquals("zeros.bin", r.entries.single().name)
        }
        assertEquals(0L, budget.totalOutput, "목록이 예산에 잡힐 만큼 풀었다")
        assertTrue(src.bytesRead < 256 * 1024, "목록이 ${src.bytesRead}바이트를 읽었다 (파일 ${f.length()})")
        assertTrue(f.length() > 900 * 1024, "표본이 커야 이 시험이 뜻을 가진다: ${f.length()}")
    }

    /**
     * **0 으로 된 파일을 싼 것은 빈 tar 가 아니다.** 첫 칸이 0 이면 빈 tar 일 수도 있지만, 안의 이름(FNAME, 없으면 압축 파일
     * 이름에서 지은 것)이 tar 가 아니면 항목 하나다 — 예전에는 본문이 1,000,000바이트까지 '항목 0개' 의 TAR.GZ 로 열려 그 파일을
     * 풀 길이가 없었다. 진짜 빈 tar 는 그대로다.
     */
    @Test
    fun `0 으로 된 파일을 싼 것은 빈 tar 가 아니라 항목 하나다`() {
        for (size in listOf(512, 10 * 1024, 100_000, 1_000_000)) {
            val f = file("zero-$size.gz", StreamBytes.gzip(ByteArray(size), name = "zero.img".toByteArray()))
            assertEquals(ArchiveKind.GZ, Archives.detect(FileDocumentSource(f)), "$size")
            open(f).use { r ->
                assertEquals(ArchiveKind.GZ, r.kind, "$size")
                assertEquals("zero.img", r.entries.single().name, "$size")
                assertContentEquals(ByteArray(size), readOnly(r), "$size")
            }
        }
        // 이름이 없어도 — 압축 파일 이름에서 지은 것이 tar 가 아니면 항목 하나다. 여섯 형식 모두.
        for ((codec, f) in allCodecs("zero.img", ByteArray(10 * 1024))) {
            assertEquals(ArchiveKind.streamOf(codec), Archives.detect(FileDocumentSource(f)), f.name)
            open(f).use { r -> assertEquals("zero.img", r.entries.single().name, f.name) }
        }
        // 진짜 빈 tar — FNAME 이 tar 이거나(`gzip backup.tar`), 이름이 없고 압축 파일 이름이 tar 를 말한다(`tar -czf`).
        val named = file("backup.gz", StreamBytes.gzip(ByteArray(10240), name = "backup.tar".toByteArray()))
        open(named).use { r ->
            assertEquals(ArchiveKind.TAR_GZ, r.kind, "FNAME 이 tar 다")
            assertTrue(r.entries.isEmpty())
        }
        for ((codec, f) in allCodecs("empty.tar", ByteArray(10240))) {
            assertEquals(ArchiveKind.tarOf(codec), Archives.detect(FileDocumentSource(f)), f.name)
            open(f).use { r -> assertTrue(r.entries.isEmpty(), f.name) }
        }
    }

    /**
     * 목록이 푸는 것은 앞 512바이트다 — **0 으로 시작하는 본문이라도.** 압축하지 않고 담은(deflate 의 '저장' 블록) gzip 은 푼
     * 만큼 파일을 읽으므로, 원본에서 읽은 바이트가 곧 푼 양이다. 1,000,000바이트의 0 을 담은 `zero.img` 의 목록이 빈 tar 인지
     * 보려고 그것을 다 풀었다(위의 시험은 압축한 바이트만 세어 놓쳤다). 이름이 tar 일 때만 1 MiB 까지 더 푼다.
     */
    @Test
    fun `목록은 0 으로 시작하는 본문도 앞만 푼다`() {
        val stored = file(
            "zero.img.gz",
            StreamBytes.gzipMember(ByteArray(1_000_000), name = "zero.img".toByteArray(), level = Deflater.NO_COMPRESSION),
        )
        assertTrue(stored.length() > 1_000_000, "저장 블록이라 압축되지 않아야 한다: ${stored.length()}")
        val src = CountingSource(stored)
        Archives.open(src).use { r -> assertEquals(ArchiveKind.GZ, r.kind) }
        assertTrue(src.bytesRead < 256 * 1024, "목록이 ${src.bytesRead}바이트를 읽었다 (파일 ${stored.length()})")
        // tar 이름이면 빈 tar 인지 보려고 더 풀지만 1 MiB 에서 멈춘다 — 그 뒤까지 0 이면 빈 tar 가 아니다(디스크 이미지).
        val tarNamed = file("big.tar.gz", StreamBytes.gzipMember(ByteArray(3_000_000), level = Deflater.NO_COMPRESSION))
        val src2 = CountingSource(tarNamed)
        Archives.open(src2).use { r -> assertEquals(ArchiveKind.GZ, r.kind) }
        assertTrue(src2.bytesRead < (1 shl 20) + 256 * 1024, "목록이 ${src2.bytesRead}바이트를 읽었다")
    }

    @Test
    fun `xz 의 크기는 끝의 인덱스만 읽어 안다`() {
        // 압축이 안 되는 2 MiB — 파일이 커야 '조금만 읽었다' 가 뜻을 가진다.
        val body = Random(9).nextBytes(2 shl 20)
        val f = file("big.xz", StreamBytes.xz(body, preset = 0))
        val src = CountingSource(f)
        Archives.open(src).use { r -> assertEquals(body.size.toLong(), r.entries.single().declaredSize) }
        assertTrue(src.bytesRead < 256 * 1024, "목록이 ${src.bytesRead}바이트를 읽었다 (파일 ${f.length()})")
    }

    // ---- 폭탄과 상한 ------------------------------------------------------------------------

    /** 64 MiB 의 0 — 형식마다. 압축비 상한(100:1)이 1 MiB 를 낸 뒤부터 보고 끊는다. 순차 추출에서는 그 항목만의 실패다. */
    @Test
    fun `폭탄은 압축비 상한에서 끊긴다`() {
        val zeros = ByteArray(64 shl 20)
        val bombs = mapOf(
            "zero.bin.gz" to StreamBytes.zeroMember(zeros.size),
            "zero.bin.xz" to StreamBytes.xz(zeros, preset = 0),
            // 작성기가 느린 셋은 8~16 MiB 로 — 압축비 상한은 1 MiB 를 낸 뒤부터 보므로 크기는 상관없다.
            "zero.bin.lz4" to StreamBytes.lz4(zeros.copyOf(8 shl 20)),
            "zero.bin.bz2" to StreamBytes.bzip2(zeros.copyOf(8 shl 20)),
            "zero.bin.lzma" to StreamBytes.lzma(zeros.copyOf(16 shl 20), preset = 0),
            "zero.bin.Z" to ZBytes.compress(zeros.copyOf(8 shl 20)),
        )
        // 풀기가 쓰는 상한 — 총량은 여유 공간, 항목 하나는 64 GiB. 압축비는 그대로다.
        val extract = ParseLimits.forExtract(freeBytes = 32L shl 30)
        for ((name, bytes) in bombs) {
            val f = file(name, bytes)
            assertTrue(f.length() * 100 < 8L shl 20, "$name: 표본이 폭탄이어야 한다 (${f.length()})")
            open(f).use { r ->
                val e = assertFailsWith<ParseLimitExceededException>(name) { readOnly(r) }
                assertEquals("maxCompressionRatio", e.limitName, name)
            }
            val c = Collector()
            open(f, extract).use { it.extractSequentially(c) }
            val failure = assertNotNull(c.failures[0], "$name: 항목의 실패로 보고해야 한다")
            assertEquals("maxCompressionRatio", (failure as ParseLimitExceededException).limitName, name)
            assertTrue(c.written[0]!! < 4L shl 20, "$name: ${c.written[0]}바이트를 쓴 뒤에야 끊겼다")
        }
    }

    @Test
    fun `압축이 안 되는 큰 항목은 크기 상한에서 끊긴다`() {
        val noise = Random(3).nextBytes(3 shl 19)
        val limits = ParseLimits.DEFAULT.copy(maxSingleOutput = 1L shl 20)
        for ((_, f) in allCodecs("noise.bin", noise)) {
            open(f, limits).use { r ->
                val e = assertFailsWith<ParseLimitExceededException>(f.name) { readOnly(r) }
                assertEquals("maxSingleOutput", e.limitName, f.name)
            }
        }
    }

    /** 총량 상한은 아카이브 전체를 끝낸다 — 항목의 실패로 세지 않고 위로 던진다(다른 리더와 같은 계약). */
    @Test
    fun `총량 상한은 위로 던진다`() {
        val limits = ParseLimits.DEFAULT.copy(maxTotalOutput = 1L shl 20, maxCompressionRatio = Int.MAX_VALUE)
        val f = file("total.bin.gz", StreamBytes.gzip(ByteArray(3 shl 20)))
        val e = assertFailsWith<ParseLimitExceededException> { open(f, limits).use { it.extractSequentially(Collector()) } }
        assertEquals("maxTotalOutput", e.limitName)
    }

    // ---- 깨진 것 ---------------------------------------------------------------------

    @Test
    fun `gzip 의 검사값과 길이와 자료가 틀리면 깨진 항목이다`() {
        val broken = mapOf(
            "crc.gz" to StreamBytes.gzipMember(text, badCrc = true),
            "isize.gz" to StreamBytes.gzipMember(text, badSize = true),
            "cut.gz" to StreamBytes.gzip(text).let { it.copyOf(it.size - 5) },
            "garbage.gz" to StreamBytes.gzip(text) + "쓰레기".toByteArray(),
            // 자료의 끝 가까이를 바꾼다 — 앞 512바이트는 풀려야 목록이 선다(안이 tar 인지 보려고 거기까지 푼다).
            "deflate.gz" to StreamBytes.gzip(text).also { it[it.size - 12] = (it[it.size - 12].toInt() xor 0x55).toByte() },
        )
        for ((name, bytes) in broken) {
            val f = file(name, bytes)
            // 목록은 선다 — 머리는 멀쩡하다.
            open(f).use { r ->
                assertEquals(1, r.entries.size, name)
                assertFailsWith<IOException>(name) { readOnly(r) }
            }
            val c = Collector()
            open(f).use { it.extractSequentially(c) }
            assertNotNull(c.failures[0], "$name: 그 항목의 실패다")
        }
    }

    /** 멤버 뒤의 0 채움은 끝이다(테이프·블록 장치가 남긴다). 0 이 아닌 것이 섞이면 깨졌다. */
    @Test
    fun `gzip 뒤의 0 채움은 받아들인다`() {
        open(file("padded.gz", StreamBytes.gzip(text) + ByteArray(4096))).use { r -> assertContentEquals(text, readOnly(r)) }
        val dirty = StreamBytes.gzip(text) + ByteArray(100) + byteArrayOf(1) + ByteArray(10)
        open(file("dirty.gz", dirty)).use { r -> assertFailsWith<IOException> { readOnly(r) } }
    }

    @Test
    fun `압축 머리부터 깨졌으면 목록이 깨졌다고 말한다`() {
        // gzip 머리의 예약 비트가 섰다.
        val f = file("reserved.gz", byteArrayOf(0x1F, 0x8B.toByte(), 8, 0xE0.toByte()) + ByteArray(600))
        assertFailsWith<IOException> { open(f).use { it.entries } }
        // xz 매직 뒤가 쓰레기.
        val x = file("junk.xz", byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00) + ByteArray(600) { 7 })
        assertFailsWith<IOException> { open(x).use { it.entries } }
        assertNull(Archives.detect(FileDocumentSource(x)), "판별은 '아니다' 로 답한다")
    }

    // ---- 취소 ----------------------------------------------------------------------

    @Test
    fun `인터럽트가 목록과 읽기와 순차 추출을 멈춘다`() {
        for ((_, f) in allCodecs("stop.bin", Random(5).nextBytes(400_000))) {
            try {
                Thread.currentThread().interrupt()
                assertFailsWith<InterruptedIOException>(f.name) { open(f).use { r -> r.extractSequentially(Collector()) } }
            } finally {
                Thread.interrupted()
            }
            // 푸는 도중에 — 첫 쓰기 뒤에 인터럽트가 온다.
            open(f).use { r ->
                try {
                    val sink = object : EntrySink {
                        override fun begin(entry: ArchiveEntry): OutputStream = object : OutputStream() {
                            override fun write(b: Int) = Unit
                            override fun write(b: ByteArray, off: Int, len: Int) = Thread.currentThread().interrupt()
                        }

                        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) =
                            throw AssertionError("${f.name}: 취소를 항목의 실패로 보고했다 — $failure")
                    }
                    assertFailsWith<InterruptedIOException>(f.name) { r.extractSequentially(sink) }
                } finally {
                    Thread.interrupted()
                }
            }
        }
    }

    /**
     * **해제기가 풀어 내는 것 없이 입력만 먹는 동안에도** 취소가 닿는다 — LZ4 의 건너뛰는 프레임(여기서는 8 MiB)을 지나는 동안
     * 위의 복사 고리는 돌지 않는다. 닿는 곳은 입력을 세는 스트림(`CountingInputStream`)이 읽을 때마다 보는 것뿐이다.
     */
    @Test
    fun `입력만 먹는 동안에도 취소가 닿는다`() {
        val skip = 8 shl 20
        val skippable = byteArrayOf(0x50, 0x2A, 0x4D, 0x18, 0, 0, 0x80.toByte(), 0) + ByteArray(skip)
        val f = file("skip.bin.lz4", skippable + StreamBytes.lz4(text))
        open(f).use { r ->
            assertContentEquals(text, readOnly(r), "건너뛰는 프레임 뒤의 본문")
            try {
                Thread.currentThread().interrupt()
                assertFailsWith<InterruptedIOException> { r.open(r.entries.single()).use { it.readBytes() } }
            } finally {
                Thread.interrupted()
            }
        }
    }

    /**
     * xz 의 스트림 사이 채움은 0 이 4바이트씩이다. 거꾸로 읽는 양에 상한(1 MiB)이 있다 — 넘으면 크기를 보이지 않을 뿐이고
     * 푸는 것은 그대로 된다(해제기는 채움을 명세대로 받는다).
     */
    @Test
    fun `xz 의 채움이 상한을 넘으면 크기를 보이지 않는다`() {
        val small = file("pad-small.xz", StreamBytes.xz(text) + ByteArray(4096))
        open(small).use { r -> assertEquals(text.size.toLong(), r.entries.single().declaredSize) }
        val big = file("pad-big.xz", StreamBytes.xz(text) + ByteArray(2 shl 20))
        open(big).use { r ->
            assertEquals(-1L, r.entries.single().declaredSize)
            assertContentEquals(text, readOnly(r))
        }
        // 인덱스가 깨졌으면 크기만 모른다 — 목록은 선다.
        val xz = StreamBytes.xz(text)
        val broken = file("index.xz", xz.copyOf().also { it[it.size - 16] = (it[it.size - 16] + 1).toByte() })
        open(broken).use { r -> assertEquals(-1L, r.entries.single().declaredSize) }
    }

    // ---- 도우미 ----------------------------------------------------------------------

    private class Collector(val select: (ArchiveEntry) -> Boolean = { true }) : EntrySink {
        val got = LinkedHashMap<Int, ByteArray>()
        val failures = LinkedHashMap<Int, Throwable>()
        val written = LinkedHashMap<Int, Long>()
        val consumed = ArrayList<Long>()
        private val open = HashMap<Int, ByteArrayOutputStream>()

        override fun begin(entry: ArchiveEntry): OutputStream? {
            if (!entry.isReadable || !select(entry)) return null
            return ByteArrayOutputStream().also { open[entry.index] = it }
        }

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
            val buf = open.remove(entry.index) ?: return
            this.written[entry.index] = written
            if (failure != null) failures[entry.index] = failure else got[entry.index] = buf.toByteArray()
        }

        override fun consumed(inputBytes: Long) {
            consumed += inputBytes
        }
    }

    /**
     * 판별이 입력 상한(4 MiB)에서 멈춘 gzip(끝나지 않는 주석)을 목록이 **마감 없이 다시 읽지 않는다** — 이름·주석 칸은 4,096바이트까지만
     * 읽고 '이름 없음' 으로 멈춘다(이름은 압축 파일 이름에서 짓는다). 예전에는 판별이 끊긴 뒤 목록이 같은 머리를 파일 끝까지 다시 읽었다.
     * 푸는 것은 그대로 된다 — 해제기는 자료에 닿아야 하므로 주석을 끝까지 지나간다.
     */
    @Test
    fun `목록은 gzip 의 긴 주석을 끝까지 읽지 않는다`() {
        val comment = ByteArray(6 shl 20) { 'c'.code.toByte() }
        val f = file("long-comment.txt.gz", StreamBytes.gzipMember(text, name = "inner.txt".toByteArray(), comment = comment))
        val src = CountingSource(f)
        Archives.open(src).use { r ->
            assertEquals(ArchiveKind.GZ, r.kind)
            assertEquals("long-comment.txt", r.entries.single().name, "주석을 다 읽지 못한 머리의 이름은 쓰지 않는다")
            // 판별 4 MiB + 목록의 머리 몇 KB. 예전에는 목록이 주석 6 MiB 를 한 번 더 읽었다.
            assertTrue(src.bytesRead < (5L shl 20), "목록이 ${src.bytesRead}바이트를 읽었다")
            assertContentEquals(text, readOnly(r))
        }
        // 짧은 주석이면 이름을 그대로 쓴다.
        val short = file("short-comment.gz", StreamBytes.gzipMember(text, name = "inner.txt".toByteArray(), comment = "c".toByteArray()))
        open(short).use { r -> assertEquals("inner.txt", r.entries.single().name) }
    }

    /** 원본에서 읽은 바이트를 센다 — 목록이 얼마나 읽는지 본다. */
    private class CountingSource(private val f: File) : DocumentSource {
        var bytesRead = 0L
        override val displayName: String get() = f.name
        override val length: Long get() = f.length()
        override fun asFile(): File = f

        override fun openStream(): InputStream = object : FilterInputStream(f.inputStream()) {
            override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bytesRead += it }
        }

        override fun openChannel(): SeekableByteChannel {
            val ch = FileDocumentSource(f).openChannel()
            return object : SeekableByteChannel by ch {
                override fun read(dst: ByteBuffer): Int = ch.read(dst).also { if (it > 0) bytesRead += it }
            }
        }
    }

    /** 바이트 배열을 하나씩 내주는 것(끝이면 -1). */
    private class ByteArrayInputStreamOf(private val bytes: ByteArray) {
        private var at = 0
        fun next(): Int = if (at < bytes.size) bytes[at++].toInt() and 0xFF else -1
    }
}
