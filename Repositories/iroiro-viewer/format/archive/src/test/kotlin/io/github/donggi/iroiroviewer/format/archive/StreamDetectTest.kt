package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 판별 — **내용이 이긴다**(`Archives`). 새 매직 일곱(LZ4·`.Z`·cpio 넷·ar)과 매직이 없는 LZMA, 그리고 옛 판별(ZIP·7z·RAR·
 * tar)이 그대로인지.
 */
class StreamDetectTest {

    private val dir: File = Files.createTempDirectory("iroiro-detect").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun file(name: String, bytes: ByteArray): File = File(dir, name).apply { writeBytes(bytes) }

    private fun detect(f: File) = Archives.detect(FileDocumentSource(f))

    private val body = "판별 시험 본문 ".repeat(200).toByteArray()

    private val tar = TarBytes().file("a.txt", "A".toByteArray()).file("b/c.txt", "C".toByteArray()).end()

    // ---- 압축 스트림 -------------------------------------------------------------------

    @Test
    fun `압축 스트림의 매직은 항목 하나짜리 모양이고 안이 tar 면 tar 다`() {
        val forms = listOf(
            Triple("gz", { b: ByteArray -> StreamBytes.gzip(b) }, StreamCodec.GZIP),
            Triple("bz2", { b: ByteArray -> StreamBytes.bzip2(b) }, StreamCodec.BZIP2),
            Triple("xz", { b: ByteArray -> StreamBytes.xz(b) }, StreamCodec.XZ),
            Triple("lz4", { b: ByteArray -> StreamBytes.lz4(b) }, StreamCodec.LZ4),
            Triple("lzma", { b: ByteArray -> StreamBytes.lzma(b) }, StreamCodec.LZMA),
            Triple("Z", { b: ByteArray -> ZBytes.compress(b) }, StreamCodec.Z),
        )
        for ((ext, pack, codec) in forms) {
            val single = file("one.txt.$ext", pack(body))
            val packed = file("two.tar.$ext", pack(tar))
            // 매직만 보면 항목 하나짜리다(안은 풀어 봐야 안다). LZMA 는 매직이 없어 여기서 답하지 않는다.
            val probed = Archives.probeKind(single.readBytes().copyOf(1024))
            if (codec == StreamCodec.LZMA) assertNull(probed, ext) else assertEquals(ArchiveKind.streamOf(codec), probed, ext)
            assertEquals(ArchiveKind.streamOf(codec), detect(single), ext)
            assertEquals(ArchiveKind.tarOf(codec), detect(packed), ext)
            Archives.open(FileDocumentSource(packed)).use { r ->
                assertEquals(ArchiveKind.tarOf(codec), r.kind, ext)
                assertEquals(listOf("a.txt", "b/c.txt"), r.entries.map { it.name }, ext)
                assertContentEquals("C".toByteArray(), r.open(r.entries[1]).use { it.readBytes() }, ext)
            }
        }
    }

    // ---- 판별의 비용 -------------------------------------------------------------------

    /**
     * **빈 블록마다 사전을 다시 잡지 않는다.** org.tukaani.xz 는 블록을 열 때마다 LZMA2 사전(여기서는 64 MiB)을 새로 잡는다 —
     * 16바이트짜리 빈 블록 4,000개가 그것만으로 10초 넘게 걸렸다. 목록 화면은 그동안 서 있다: 판별이 앞 512바이트를 찾아 그 블록들을
     * 다 지나고, 압축 tar 의 목록은 끝까지 푼다. 스트림마다 배열 보관소를 주면 앞 블록의 사전을 다음 블록이 받는다.
     */
    @Test
    fun `빈 블록을 수천 개 이은 xz 도 블록마다 사전을 다시 잡지 않는다`() {
        val t0 = System.nanoTime()
        val packed = file("blocks.tar.xz", StreamBytes.xzEmptyBlocks(4_000) + StreamBytes.xz(tar))
        Archives.open(FileDocumentSource(packed)).use { r ->
            assertEquals(ArchiveKind.TAR_XZ, r.kind)
            assertEquals(listOf("a.txt", "b/c.txt"), r.entries.map { it.name })
            assertContentEquals("C".toByteArray(), r.open(r.entries[1]).use { it.readBytes() })
        }
        val single = file("blocks.txt.xz", StreamBytes.xzEmptyBlocks(4_000) + StreamBytes.xz(body))
        Archives.open(FileDocumentSource(single)).use { r ->
            assertEquals(ArchiveKind.XZ, r.kind)
            assertContentEquals(body, r.open(r.entries.single()).use { it.readBytes() })
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 3_000, "${ms}ms — 블록마다 사전을 새로 잡았다")
    }

    /**
     * **판별은 시계를 보지 않는다** — 같은 파일이면 원본이 느려도 같은 모양이다. 예전에는 5초로 끊어, 느린 원본(시험은 이 작업의
     * 상한을 200 ms 로 줄였다)에서는 같은 파일이 항목 하나짜리(XZ)로, 빠른 원본에서는 TAR.XZ 로 열렸다 — 목록이 TAR.BZ2 로 세운
     * 파일을 풀기 순간 SD 가 멎으면 '전부 풀기' 가 다른 모양을 풀었다. 원본을 느리게 해도(16바이트마다 1 ms) 답이 같다.
     */
    @Test
    fun `판별은 시계를 보지 않는다 — 느린 원본도 같은 모양이다`() {
        val f = file("slow.tar.xz", StreamBytes.xzEmptyBlocks(300) + StreamBytes.xz(tar))
        val limits = ParseLimits.DEFAULT.copy(openTimeoutMs = 200)
        Archives.open(TrickleSource(f), limits, EntryBudget(limits)).use { r ->
            assertEquals(ArchiveKind.TAR_XZ, r.kind, "느린 원본에서 다른 모양으로 열렸다")
            assertEquals(listOf("a.txt", "b/c.txt"), r.entries.map { it.name })
        }
        Archives.open(FileDocumentSource(f)).use { r -> assertEquals(ArchiveKind.TAR_XZ, r.kind) }
        assertEquals(ArchiveKind.TAR_XZ, Archives.detect(TrickleSource(f)))
    }

    /**
     * **판별이 소비할 입력에는 상한이 있다**([StreamLimits.PROBE_INPUT_BYTES]). 앞 512바이트가 나오기까지 입력만 먹는 스트림(여기서는
     * LZ4 의 건너뛰는 프레임 5 MiB)은 넘으면 안을 보지 못한 채 항목 하나짜리로 연다 — 그 목록은 머리만 읽는다. 상한 안이면(1 MiB) tar 다.
     * 어느 쪽이든 **파일이 정한다**(몇 번을 열어도 같다).
     */
    @Test
    fun `판별이 입력 상한을 넘으면 안을 보지 않고 항목 하나로 연다`() {
        fun skippable(size: Int): ByteArray {
            val head = byteArrayOf(0x50, 0x2A, 0x4D, 0x18, (size and 0xFF).toByte(), ((size shr 8) and 0xFF).toByte(),
                ((size shr 16) and 0xFF).toByte(), ((size shr 24) and 0xFF).toByte())
            return head + ByteArray(size)
        }
        val far = file("far.tar.lz4", skippable(5 shl 20) + StreamBytes.lz4(tar))
        repeat(2) {
            assertEquals(ArchiveKind.LZ4, detect(far))
            Archives.open(FileDocumentSource(far)).use { r ->
                assertEquals(ArchiveKind.LZ4, r.kind)
                assertEquals("far.tar", r.entries.single().name)
                // 항목 하나로 열었어도 푸는 것은 된다 — 안의 tar 를 그대로 낸다(사용자는 그것을 풀어서 연다).
                assertContentEquals(tar, r.open(r.entries.single()).use { it.readBytes() })
            }
        }
        val near = file("near.tar.lz4", skippable(1 shl 20) + StreamBytes.lz4(tar))
        assertEquals(ArchiveKind.TAR_LZ4, detect(near))
    }

    /**
     * 입력 상한은 해제기와 상관없이 **입력을 셀 때** 본다 — 상한이 0 이면 첫 읽기에서 멈춘다. 압축 안 한 tar 에는 걸지 않는다.
     */
    @Test
    fun `판별의 입력 상한은 모든 압축 형식에 걸린다`() {
        val forms = mapOf(
            StreamCodec.GZIP to StreamBytes.gzip(tar),
            StreamCodec.BZIP2 to StreamBytes.bzip2(tar),
            StreamCodec.XZ to StreamBytes.xz(tar),
            StreamCodec.LZ4 to StreamBytes.lz4(tar),
            StreamCodec.LZMA to StreamBytes.lzma(tar),
            StreamCodec.Z to ZBytes.compress(tar),
        )
        for ((codec, bytes) in forms) {
            val src = FileDocumentSource(file("t-${codec.name}.tar.bin", bytes))
            assertFailsWith<ProbeLimitException>(codec.name) {
                TarArchiveReader.startsLikeTar(src, codec, probe = true, probeInputBytes = 0)
            }
            assertTrue(TarArchiveReader.startsLikeTar(src, codec, probe = true), codec.name)
        }
        val plain = FileDocumentSource(file("t.tar", tar))
        assertTrue(TarArchiveReader.startsLikeTar(plain, null, probe = true, probeInputBytes = 0), "압축 안 한 tar 에는 걸지 않는다")
    }

    @Test
    fun `새 tar 의 이름표`() {
        assertEquals("TAR.LZ4", ArchiveKind.TAR_LZ4.label)
        assertEquals("TAR.LZMA", ArchiveKind.TAR_LZMA.label)
        assertEquals("TAR.Z", ArchiveKind.TAR_Z.label)
        assertEquals(listOf("GZ", "BZ2", "XZ", "LZ4", "LZMA", "Z"), StreamCodec.entries.map { ArchiveKind.streamOf(it).label })
        assertEquals(listOf("CPIO", "AR", "DEB"), listOf(ArchiveKind.CPIO, ArchiveKind.AR, ArchiveKind.DEB).map { it.label })
        // 갈래는 상수마다 하나다 — 이름표가 겹치면 화면에서 둘이 갈리지 않는다.
        assertEquals(ArchiveKind.entries.size, ArchiveKind.entries.map { it.label }.toSet().size)
    }

    @Test
    fun `LZ4 는 프레임 매직과 앞의 건너뛰는 프레임과 이어 붙인 프레임을 받는다`() {
        val frame = StreamBytes.lz4(body)
        assertEquals(ArchiveKind.LZ4, Archives.probeKind(frame))
        // 건너뛰는 프레임(`5x 2A 4D 18` + 길이 + 자료)이 앞에 온다.
        val skippable = byteArrayOf(0x53, 0x2A, 0x4D, 0x18, 5, 0, 0, 0, 1, 2, 3, 4, 5)
        val f = file("skip.lz4", skippable + frame + frame)
        assertEquals(ArchiveKind.LZ4, detect(f))
        Archives.open(FileDocumentSource(f)).use { r ->
            assertContentEquals(body + body, r.open(r.entries.single()).use { it.readBytes() }, "프레임을 끝까지")
        }
        // 옛 LZ4(`lz4 -l`)는 commons-compress 가 읽지 못한다 — 다루지 않는 형식이다.
        assertNull(Archives.probeKind(byteArrayOf(0x02, 0x21, 0x4C, 0x18) + ByteArray(100)))
    }

    @Test
    fun `Z 는 코드 폭이 9에서 16 사이일 때만이다`() {
        val good = ZBytes.compress(body)
        assertEquals(ArchiveKind.Z, Archives.probeKind(good))
        // 코드 폭 8·17·31, 그리고 쓰지 않는 두 비트(0x20·0x40)가 선 것.
        for (flags in listOf(0x80 or 8, 0x80 or 17, 0x80 or 31, 0xA0 or 16, 0xC0 or 16)) {
            val bad = good.copyOf().also { it[2] = flags.toByte() }
            assertNull(Archives.probeKind(bad), "플래그 ${flags.toString(16)}")
            assertNull(detect(file("bad.Z", bad)), "플래그 ${flags.toString(16)}")
        }
        // 블록 모드를 끈 것도 받는다.
        assertEquals(ArchiveKind.Z, Archives.probeKind(ZBytes.compress(body, blockMode = false)))
    }

    // ---- LZMA — 매직이 없다 ------------------------------------------------------------

    /** LZMA 는 **이름이 그렇다고 할 때만** 본다. 같은 바이트도 `.bin` 이면 아카이브가 아니다. */
    @Test
    fun `LZMA 는 이름의 도움 없이는 알아보지 않는다`() {
        val lzma = StreamBytes.lzma(body)
        assertTrue(LzmaHeader.plausible(lzma.copyOf(LzmaHeader.PROBE_BYTES)), "표본의 머리가 그럴듯해야 한다")
        assertNull(detect(file("data.bin", lzma)))
        assertFailsWith<IllegalStateException> { Archives.open(FileDocumentSource(file("data.bin", lzma))) }
        for (name in listOf("x.lzma", "X.LZMA", "x.tlz", "x.tar.lzma")) assertEquals(ArchiveKind.LZMA, detect(file(name, lzma)), name)
        assertEquals(ArchiveKind.TAR_LZMA, detect(file("t.tlz", StreamBytes.lzma(tar))))
    }

    /**
     * **무작위 바이트는 `.lzma` 라고 적혀 있어도 LZMA 가 아니다.** 머리의 그럴듯함(속성·사전·크기·첫 바이트)이 걸러 낸다.
     * 0x5D 로 시작하게 해도(LZMA 의 흔한 속성 바이트) 마찬가지다.
     */
    @Test
    fun `무작위 바이트는 이름이 lzma 여도 LZMA 가 아니다`() {
        for (seed in 0 until 200) {
            val bytes = Random(seed).nextBytes(4096)
            if (seed % 2 == 0) bytes[0] = 0x5D
            val f = file("random$seed.lzma", bytes)
            assertNull(detect(f), "씨앗 $seed")
            assertFailsWith<IllegalStateException>("씨앗 $seed") { Archives.open(FileDocumentSource(f)) }
        }
    }

    /**
     * 머리는 그럴듯한데 **앞부분이 풀리지 않으면 LZMA 가 아니다**(깨진 LZMA 가 아니다) — 매직이 없으니 '깨졌다' 라고 말할 근거가
     * 없다. 범위 부호기가 `FF` 로 시작하면 첫 기호가 '앞의 것을 되풀이' 가 되어 곧바로 풀리지 않는다.
     */
    @Test
    fun `머리만 그럴듯하고 풀리지 않으면 아카이브가 아니다`() {
        val head = byteArrayOf(0x5D, 0, 0, 0x80.toByte(), 0) + ByteArray(8) { 0xFF.toByte() } + byteArrayOf(0)
        assertTrue(LzmaHeader.plausible(head))
        val f = file("fake.lzma", head + ByteArray(600) { 0xFF.toByte() })
        assertNull(detect(f))
        assertFailsWith<IllegalStateException> { Archives.open(FileDocumentSource(f)) }
    }

    @Test
    fun `LZMA 머리의 그럴듯함`() {
        fun head(props: Int = 0x5D, dict: Long = 1L shl 23, size: Long = -1L, first: Int = 0): ByteArray {
            val h = ByteArray(LzmaHeader.PROBE_BYTES)
            h[0] = props.toByte()
            for (i in 0 until 4) h[1 + i] = (dict ushr (8 * i)).toByte()
            for (i in 0 until 8) h[5 + i] = (size ushr (8 * i)).toByte()
            h[13] = first.toByte()
            return h
        }
        assertTrue(LzmaHeader.plausible(head()))
        assertTrue(LzmaHeader.plausible(head(dict = (1L shl 20) + (1L shl 19), size = 12_345)))
        assertTrue(LzmaHeader.plausible(head(dict = 1L shl 12)))
        // 속성: 225 이상, lc + lp > 4(xz-utils 가 풀지 않는다).
        assertTrue(!LzmaHeader.plausible(head(props = 225)))
        assertTrue(!LzmaHeader.plausible(head(props = 8 + 9 * 1)), "lc 8 + lp 1")
        // 사전: 2^n·2^n+2^(n-1) 이 아니다, 너무 작다, `FFFFFFFF`(해제기가 풀지 못한다).
        for (d in listOf(0L, 1L shl 11, (1L shl 23) + 1, 3_000_000L, 0xFFFF_FFFFL, 1L shl 31)) {
            assertTrue(!LzmaHeader.plausible(head(dict = d)), "사전 $d")
        }
        // 크기: 256 GiB 이상(모름(-1)은 된다).
        assertTrue(!LzmaHeader.plausible(head(size = 1L shl 38)))
        // 범위 부호기의 첫 바이트는 언제나 0 이다.
        assertTrue(!LzmaHeader.plausible(head(first = 1)))
        assertTrue(!LzmaHeader.plausible(head().copyOf(13)), "짧다")
    }

    // ---- cpio·ar ----------------------------------------------------------------------

    @Test
    fun `cpio 의 네 형식과 ar 과 deb 를 알아본다`() {
        for (format in listOf(CpioConstants.FORMAT_NEW, CpioConstants.FORMAT_NEW_CRC, CpioConstants.FORMAT_OLD_ASCII, CpioConstants.FORMAT_OLD_BINARY)) {
            val f = file("x$format.cpio", cpio(format))
            assertEquals(ArchiveKind.CPIO, Archives.probeKind(f.readBytes().copyOf(1024)), "형식 $format")
            assertEquals(ArchiveKind.CPIO, detect(f), "형식 $format")
        }
        // 옛 이진의 작은 끝(리눅스·x86 이 쓰는 차례) — commons-compress 는 큰 끝만 쓴다. 손으로 짓는다.
        assertEquals(ArchiveKind.CPIO, detect(file("le.cpio", CpioBytes.binaryLittleEndian(listOf("a.txt" to "A".toByteArray())))))
        assertEquals(ArchiveKind.AR, detect(file("x.ar", ar(listOf("a.txt" to body)))))
        assertEquals(ArchiveKind.DEB, detect(file("x.deb", ar(listOf("debian-binary" to "2.0\n".toByteArray(), "control.tar.gz" to body)))))
        // `.deb` 라고 적혀 있어도 첫 항목이 `debian-binary` 가 아니면 그냥 ar 이다(이름이 아니라 내용).
        assertEquals(ArchiveKind.AR, detect(file("fake.deb", ar(listOf("a.txt" to body)))))
    }

    /** 옛 이진 cpio 의 매직은 두 바이트뿐이다 — `C7 71` 로 시작하는 다른 파일을 cpio 라고 하지 않는다. */
    @Test
    fun `두 바이트 매직만 맞는 파일은 cpio 가 아니다`() {
        for (seed in 0 until 100) {
            val bytes = Random(seed).nextBytes(1024)
            bytes[0] = if (seed % 2 == 0) 0xC7.toByte() else 0x71
            bytes[1] = if (seed % 2 == 0) 0x71 else 0xC7.toByte()
            assertNull(Archives.probeKind(bytes), "씨앗 $seed")
        }
        // ASCII 매직 뒤가 숫자가 아니다(전화번호 목록 같은 글).
        assertNull(Archives.probeKind("0707012345 김철수\n".repeat(20).toByteArray()))
    }

    // ---- 옛 판별은 그대로다 --------------------------------------------------------------

    @Test
    fun `ZIP·7z·RAR·tar 의 판별은 그대로다`() {
        val zip = ArchiveSamples.zip(File(dir, "a.zip"), count = 2, bytesEach = 10)
        val sevenZ = ArchiveSamples.sevenZ(File(dir, "a.7z"), count = 2, bytesEach = 10)
        assertEquals(ArchiveKind.ZIP, detect(zip))
        assertEquals(ArchiveKind.SEVEN_Z, detect(sevenZ))
        assertEquals(ArchiveKind.RAR, Archives.probeKind(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00) + ByteArray(100)))
        assertEquals(ArchiveKind.RAR, Archives.probeKind(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00) + ByteArray(100)))
        assertEquals(ArchiveKind.TAR, detect(file("a.tar", tar)))
        assertEquals(ArchiveKind.TAR, detect(file("empty.tar", ByteArray(10240))))
        assertNull(Archives.probeKind(byteArrayOf(0x50, 0x4B, 0x07, 0x08) + ByteArray(100)), "분할 ZIP")
        assertNull(detect(file("plain.txt", body)))
        // 등록소가 쓰는 옛 판별(16바이트)은 새 형식을 모른다고 답한다 — 아카이브는 목록 화면의 종류로 열린다.
        assertNull(Archives.probeContainer(StreamBytes.gzip(body).copyOf(16)))
        assertEquals(FormatId.ZIP, Archives.probeContainer(zip.readBytes().copyOf(16)))
    }

    /**
     * **tar 의 검사합이 압축 매직보다 먼저다.** tar 의 첫 머리는 첫 항목의 이름으로 시작한다 — `BZh9…` 폴더를 묶은 tar 가
     * bzip2 로, `070701…` 은 cpio 로, `!<arch>` 는 ar 로 보일 수 있었다.
     */
    @Test
    fun `첫 이름이 다른 매직처럼 생긴 tar 도 tar 다`() {
        val names = listOf(
            "BZh91AY&SY/a.txt".toByteArray(),
            "070701/a.txt".toByteArray(),
            "!<arch>\nx".toByteArray(),
            byteArrayOf(0x1F, 0x9D.toByte(), 0x90.toByte(), 'x'.code.toByte()),
            byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 'x'.code.toByte()),
            byteArrayOf(0x04, 0x22, 0x4D, 0x18, 'x'.code.toByte()),
        )
        for (raw in names) {
            val label = raw.joinToString(" ") { (it.toInt() and 0xFF).toString(16) }
            val t = TarBytes().header("x", size = 1, nameBytes = raw).data("x".toByteArray()).end()
            val f = file("tricky.tar", t)
            assertEquals(ArchiveKind.TAR, Archives.probeKind(t.copyOf(1024)), label)
            assertEquals(ArchiveKind.TAR, detect(f), label)
            Archives.open(FileDocumentSource(f)).use { r -> assertEquals(1, r.entries.size, label) }
        }
    }

    /** `war`·`ear`·`aar` 는 ZIP 이다 — 내용으로 알아보므로 확장자를 따로 볼 일이 없다. */
    @Test
    fun `war·ear·aar 는 ZIP 으로 열린다`() {
        for ((name, entry) in listOf("app.war" to "WEB-INF/web.xml", "app.ear" to "META-INF/application.xml", "lib.aar" to "AndroidManifest.xml")) {
            val f = File(dir, name)
            ZipArchiveOutputStream(f).use { out ->
                out.putArchiveEntry(ZipArchiveEntry(entry))
                out.write("<x/>".toByteArray())
                out.closeArchiveEntry()
            }
            assertEquals(ArchiveKind.ZIP, detect(f), name)
            Archives.open(FileDocumentSource(f), budget = EntryBudget()).use { r ->
                assertEquals(ArchiveKind.ZIP, r.kind, name)
                assertEquals(listOf(entry), r.entries.map { it.name }, name)
            }
        }
    }

    // ---- 도우미 ----------------------------------------------------------------------

    private fun cpio(format: Short): ByteArray = ByteArrayOutputStream().also { b ->
        CpioArchiveOutputStream(b, format).use { out ->
            val e = CpioArchiveEntry(format, "a.txt", 3)
            e.mode = CpioConstants.C_ISREG.toLong() or 420
            // `070702` 의 검사값 — 작성기가 쓴 자료와 견준다.
            if (format == CpioConstants.FORMAT_NEW_CRC) e.chksum = "abc".sumOf { it.code.toLong() }
            out.putArchiveEntry(e)
            out.write("abc".toByteArray())
            out.closeArchiveEntry()
        }
    }.toByteArray()

    /**
     * 느린 원본 — 읽을 때마다 [chunk] 바이트까지만 주고 1 ms 쉰다. `available` 이 0 이라 위의 버퍼가 더 모으려 들지 않는다.
     * 채널(xz 의 끝 인덱스를 읽는 길)은 빠르다.
     */
    private class TrickleSource(private val f: File, private val chunk: Int = 16) : DocumentSource {
        override val displayName: String get() = f.name
        override val length: Long get() = f.length()
        override fun asFile(): File = f
        override fun openChannel(): SeekableByteChannel = FileDocumentSource(f).openChannel()

        override fun openStream(): InputStream = object : FilterInputStream(f.inputStream()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                Thread.sleep(1)
                return super.read(b, off, minOf(len, chunk))
            }

            override fun available(): Int = 0
        }
    }

    private fun ar(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { b ->
        ArArchiveOutputStream(b).use { out ->
            out.setLongFileMode(ArArchiveOutputStream.LONGFILE_BSD)
            for ((n, data) in entries) {
                out.putArchiveEntry(ArArchiveEntry(n, data.size.toLong()))
                out.write(data)
                out.closeArchiveEntry()
            }
        }
    }.toByteArray()
}
