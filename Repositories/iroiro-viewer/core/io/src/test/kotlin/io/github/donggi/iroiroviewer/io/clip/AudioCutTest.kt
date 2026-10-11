package io.github.donggi.iroiroviewer.io.clip

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 우리가 직접 자르는 소리(WAV·MP3·FLAC)의 시험. 표본은 시험이 짓고([ClipSamples]), 결과는 **다른 길로** 다시 읽어 견준다 —
 * FLAC 은 시험 쪽의 VERBATIM 풀개로, WAV 는 바이트로, MP3 는 프레임 표지로.
 */
class AudioCutTest {

    private val files = ArrayList<File>()

    @AfterTest
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun temp(bytes: ByteArray): File = File.createTempFile("clip", ".bin").also {
        it.writeBytes(bytes)
        files += it
    }

    private fun cut(file: File, plan: suspend (RandomAccessFile) -> CutPlan): Pair<AudioCut, ByteArray> = runBlocking {
        RandomAccessFile(file, "r").use { raf ->
            val p = plan(raf)
            assertIs<CutPlan.Ok>(p)
            val out = ByteArrayOutputStream()
            var told = 0L
            p.cut.writeTo(out) { told += it }
            val bytes = out.toByteArray()
            assertEquals(bytes.size.toLong(), told, "알린 바이트와 쓴 바이트가 같아야 한다")
            assertEquals(p.cut.outputBytes, bytes.size.toLong(), "계획한 크기와 쓴 크기가 같아야 한다")
            p.cut to bytes
        }
    }

    private fun planOf(file: File, plan: suspend (RandomAccessFile) -> CutPlan): CutPlan = runBlocking {
        RandomAccessFile(file, "r").use { plan(it) }
    }

    // ---- WAV -----------------------------------------------------------------

    @Test
    fun `WAV 는 표본 경계에서 잘리고 머리가 새로 쓰인다`() {
        val rate = 8000
        val src = ClipSamples.wav(rate, 2, rate * 3, list = "INFOINAM\u0004\u0000\u0000\u0000abc\u0000".toByteArray(Charsets.ISO_8859_1))
        val f = temp(src)
        val (c, out) = cut(f) { WavCut.plan(it, 1_000_000, 2_000_000, "wav") }
        assertEquals(1_000_000, c.startUs)
        assertEquals(2_000_000, c.endUs)
        assertEquals("wav", c.extension)
        assertEquals("RIFF", String(out, 0, 4))
        assertEquals((out.size - 8).toLong(), out.u32le(4), "RIFF 크기")
        // 데이터 청크를 찾아 원본의 1초~2초와 바이트로 견준다.
        val dataAt = indexOf(out, "data".toByteArray())
        val len = out.u32le(dataAt + 4).toInt()
        assertEquals(rate * 4, len)
        val srcData = indexOf(src, "data".toByteArray()) + 8
        assertContentEquals(src.copyOfRange(srcData + rate * 4, srcData + rate * 8), out.copyOfRange(dataAt + 8, dataAt + 8 + len))
        assertTrue(indexOf(out, "LIST".toByteArray()) in 12 until dataAt, "LIST(INFO)를 옮긴다")
    }

    @Test
    fun `WAV 의 끝 너머는 비었고 압축 WAV 는 다루지 않는다`() {
        val f = temp(ClipSamples.wav(8000, 1, 8000))
        assertIs<CutPlan.Empty>(planOf(f) { WavCut.plan(it, 5_000_000, 6_000_000, "wav") })
        // 형식 2(ADPCM)로 바꾼다.
        val adpcm = ClipSamples.wav(8000, 1, 8000).also { it[20] = 2 }
        assertIs<CutPlan.Unsupported>(planOf(temp(adpcm)) { WavCut.plan(it, 0, 500_000, "wav") })
        // WAVE_FORMAT_EXTENSIBLE 의 PCM 은 다룬다.
        val ext = temp(ClipSamples.wav(8000, 2, 8000, extensible = true))
        assertIs<CutPlan.Ok>(planOf(ext) { WavCut.plan(it, 0, 500_000, "wav") })
    }

    // ---- MP3 -----------------------------------------------------------------

    @Test
    fun `MP3 는 프레임 경계에서 잘리고 태그를 옮기고 Xing 을 새로 쓴다`() {
        val id3 = ClipSamples.id3v2("song")
        val frames = (1..100).map { ClipSamples.mp3Frame(it, padding = it % 3 == 0) }
        val xing = ClipSamples.xingFrame(100, frames.sumOf { it.size })
        val src = id3 + xing + frames.reduce { a, b -> a + b } + ClipSamples.id3v1()
        val f = temp(src)
        // 프레임 하나는 1152/44100 초(약 26.12 ms). 1.0초~2.0초 → 38번째(0부터)부터 77번째 앞까지.
        val (c, out) = cut(f) { Mp3Cut.plan(it, 1_000_000, 2_000_000, "mp3") }
        val unit = 1152L * 1_000_000L
        val first = (1_000_000L * 44100 / unit).toInt()
        val end = ((2_000_000L * 44100 + unit - 1) / unit).toInt()
        assertEquals(first * unit / 44100, c.startUs)
        assertEquals(end * unit / 44100, c.endUs)
        // 태그 그대로.
        assertContentEquals(id3, out.copyOfRange(0, id3.size))
        // 새 Xing — 프레임 수와 바이트 수가 자른 것의 것이다.
        val xAt = id3.size + 4 + 32
        assertEquals("Xing", String(out, xAt, 4))
        val kept = frames.subList(first, end)
        assertEquals(kept.size.toLong(), out.u32be(xAt + 8))
        assertEquals((xing.size + kept.sumOf { it.size }).toLong(), out.u32be(xAt + 12))
        // 프레임들은 원본의 그 프레임들 그대로(표지로 확인), 끝에 ID3v1.
        var at = id3.size + xing.size
        for (k in kept) {
            assertContentEquals(k, out.copyOfRange(at, at + k.size))
            at += k.size
        }
        assertEquals("TAG", String(out, at, 3))
        assertEquals(out.size, at + 128)
    }

    @Test
    fun `MP3 는 프레임 사이의 쓰레기를 건너 다시 맞춘다`() {
        val frames = (1..40).map { ClipSamples.mp3Frame(it) }
        // 20번째 프레임 앞에 쓰레기 37바이트(동기 부호 비슷한 것도 섞는다).
        val junk = ByteArray(37) { if (it % 5 == 0) 0xFF.toByte() else 0x11 }
        val src = frames.subList(0, 20).reduce { a, b -> a + b } + junk + frames.subList(20, 40).reduce { a, b -> a + b }
        val f = temp(src)
        val (_, out) = cut(f) { Mp3Cut.plan(it, 0, 10_000_000, "mp3") }
        // 쓰레기는 빠지고 프레임 40개만 남는다.
        assertEquals(frames.sumOf { it.size }, out.size)
        assertTrue(RandomAccessFile(f, "r").use { Mp3Cut.looksLikeMp3(it) })
    }

    @Test
    fun `MP3 가 아닌 것은 MP3 로 보지 않는다`() {
        // ADTS(AAC) 머리: FF F1 — 층 비트가 00(예약)이라 MPEG 오디오가 아니다.
        val adts = ByteArray(2000) { 0 }.also {
            it[0] = 0xFF.toByte()
            it[1] = 0xF1.toByte()
        }
        assertTrue(!RandomAccessFile(temp(adts), "r").use { Mp3Cut.looksLikeMp3(it) })
        assertIs<CutPlan.Unsupported>(planOf(temp(ByteArray(5000) { 7 })) { Mp3Cut.plan(it, 0, 1_000_000, "mp3") })
    }

    // ---- FLAC ----------------------------------------------------------------

    private fun tone(channels: Int, n: Int, withFalseSync: Boolean = false) = Array(channels) { c ->
        ShortArray(n) { i ->
            // 소리 데이터 속에 동기 부호(FF F8)를 일부러 심는다 — -8 = 0xFFF8.
            if (withFalseSync && i % 50 == 0) (-8).toShort() else ((i * (c + 3)) % 30000 - 15000).toShort()
        }
    }

    @Test
    fun `FLAC 은 프레임 경계에서 잘리고 번호와 CRC 를 다시 쓴다`() {
        val rate = 8000
        val block = 1024
        val samples = tone(2, rate * 4, withFalseSync = true)
        val f = temp(ClipSamples.flac(samples, rate, block, seekTable = true))
        val (c, out) = cut(f) { FlacCut.plan(it, 1_000_000, 2_500_000, "flac") }
        val firstFrame = rate * 1 / block // 7 — 1초(8000)를 품은 프레임은 7번(7168~8191)
        val endFrame = (rate * 5 / 2 + block - 1) / block // 20 (2.5초 = 20000 표본, 19번 프레임이 19456~20479)
        assertEquals(firstFrame.toLong() * block * 1_000_000 / rate, c.startUs)
        val d = ClipSamples.decodeVerbatim(out, 2)
        // 번호는 0 부터.
        assertEquals((0 until endFrame - firstFrame).map { it.toLong() }, d.numbers)
        // 표본은 원본의 그 자리 그대로.
        val from = firstFrame * block
        val to = endFrame * block
        for (ch in 0 until 2) assertContentEquals(samples[ch].copyOfRange(from, to), d.samples[ch])
        assertEquals((to - from).toLong(), d.totalSamples)
        // MD5 는 0(모른다), 목차는 버리고 주석은 옮긴다.
        assertTrue(d.streamInfo.copyOfRange(18, 34).all { it == 0.toByte() })
        assertEquals(listOf(0, 4), d.blockTypes)
    }

    @Test
    fun `가변 블록 FLAC 은 표본 번호를 0 부터 다시 쓴다`() {
        val rate = 8000
        val samples = tone(1, rate * 2)
        val f = temp(ClipSamples.flac(samples, rate, 576, variable = true, comment = null))
        val (_, out) = cut(f) { FlacCut.plan(it, 500_000, 1_500_000, "flac") }
        val d = ClipSamples.decodeVerbatim(out, 1)
        assertEquals(0L, d.numbers.first())
        // 다음 프레임의 번호는 앞 프레임의 표본 수만큼 는다.
        for (i in 1 until d.numbers.size) assertEquals(d.numbers[i - 1] + 576, d.numbers[i])
        val first = (rate / 2) / 576 * 576
        assertContentEquals(samples[0].copyOfRange(first, first + d.samples[0].size), d.samples[0])
        assertEquals(listOf(0), d.blockTypes)
    }

    @Test
    fun `FLAC 의 끝 너머는 비었고 FLAC 이 아니면 다루지 않는다`() {
        val f = temp(ClipSamples.flac(tone(1, 8000), 8000, 1024))
        assertIs<CutPlan.Empty>(planOf(f) { FlacCut.plan(it, 5_000_000, 6_000_000, "flac") })
        assertIs<CutPlan.Unsupported>(planOf(temp(ByteArray(100))) { FlacCut.plan(it, 0, 1_000_000, "flac") })
    }

    /**
     * 옮길 메타데이터 블록(주석·그림)은 원본의 자리만 들고 쓸 때 옮긴다 — 그사이 파일이 바뀌어 그 자리가 그 블록이 아니면 멈춘다(쓰다 만
     * 임시본은 부르는 쪽이 버린다). 잘못된 바이트를 주석으로 옮기지 않는다.
     */
    @Test
    fun `FLAC 의 옮길 블록이 그사이 바뀌었으면 쓰지 않는다`() {
        val src = ClipSamples.flac(tone(1, 8000), 8000, 1024)
        val f = temp(src)
        RandomAccessFile(f, "rw").use { raf ->
            val p = runBlocking { FlacCut.plan(raf, 0, 500_000, "flac") }
            assertIs<CutPlan.Ok>(p)
            // 주석 블록(종류 4)의 머리를 그림(6)으로 바꾼다 — 계획한 뒤에 파일이 바뀐 것처럼.
            val at = indexOf(src, byteArrayOf(0x84.toByte()))
            assertTrue(at > 4, "주석 블록 머리(마지막 블록 · 종류 4)")
            raf.seek(at.toLong())
            raf.write(0x86)
            assertFailsWith<java.io.IOException> { runBlocking { p.cut.writeTo(ByteArrayOutputStream()) {} } }
        }
    }

    @Test
    fun `FLAC 번호의 UTF-8 꼴`() {
        assertContentEquals(byteArrayOf(0x7F), FlacCut.encodeNumber(0x7F))
        assertContentEquals(byteArrayOf(0xC2.toByte(), 0x80.toByte()), FlacCut.encodeNumber(0x80))
        assertEquals(6, FlacCut.encodeNumber(0x7FFFFFFF).size)
        assertEquals(7, FlacCut.encodeNumber(1L shl 35).size)
    }

    // ---- 읽기·기억 ---------------------------------------------------------------

    /**
     * 길이는 만들 때 한 번 읽는다 — 그 뒤에 파일이 줄면(다른 앱이 잘라 다시 썼다) 줄어든 자리를 파일 밖으로 본다. 예전에는 빈 창에서
     * `read` 가 진행 없이 끝없이 돌았고(FLAC 훑기가 취소도 닿지 않은 채 멈췄다), `u8` 이 지난 창의 바이트를 돌려줬다.
     */
    @org.junit.Test(timeout = 10_000)
    fun `WindowReader 는 그사이 줄어든 파일에서 멈춘다`() {
        val f = temp(ByteArray(200_000) { (it % 251).toByte() })
        RandomAccessFile(f, "rw").use { raf ->
            val r = WindowReader(raf, windowSize = 4096)
            assertEquals(200_000L, r.length)
            assertEquals(7, r.u8(7))
            raf.setLength(10_000)
            // 창 밖의 줄어든 자리 — 파일 밖이다.
            assertEquals(-1, r.u8(150_000))
            val dst = ByteArray(100)
            assertEquals(0, r.read(150_000, dst))
            // 줄어든 끝에 걸친 읽기는 남은 만큼만.
            assertEquals(40, r.read(9_960, dst))
            assertEquals((9_960 % 251).toByte(), dst[0])
            // FLAC 머리 읽기도 끝난다(머리가 아니다).
            assertEquals(null, FlacCut.parseHeader(r, 150_000))
        }
    }

    @Test
    fun `성긴 자리는 칸 수 이하면 그대로다`() {
        val s = SparseOffsets(capacity = 8)
        val exact = LongArray(8) { it * 100L + it * it }
        exact.forEach { s.add(it) }
        assertEquals(8L, s.count)
        for (i in 0 until 8) assertEquals(exact[i], s.offsetOf(i.toLong()))
    }

    @Test
    fun `성긴 자리는 칸이 차면 간격을 늘리고 사이를 선으로 잇는다`() {
        // 고정 길이(고정 비트율)면 선이 곧 답이다 — 몇 번을 걸러도 정확하다.
        val cbr = SparseOffsets(capacity = 8)
        repeat(1000) { cbr.add(417L * it) }
        for (f in 0 until 1000) assertEquals(417L * f, cbr.offsetOf(f.toLong()), "프레임 $f")
        // 길이가 바뀌어도(가변 비트율 — 417·418바이트를 오간다) 어긋남은 간격 하나의 바이트 차이를 넘지 않는다.
        val lengths = IntArray(5000) { if (it % 3 == 0) 418 else if (it % 7 == 0) 1044 else 417 }
        val at = LongArray(lengths.size)
        for (i in 1 until at.size) at[i] = at[i - 1] + lengths[i - 1]
        val vbr = SparseOffsets(capacity = 64)
        at.forEach { vbr.add(it) }
        // 칸 64개에 5,000 프레임 — 간격은 128 프레임이 된다.
        val stride = 128
        for (f in at.indices) {
            val d = kotlin.math.abs(vbr.offsetOf(f.toLong()) - at[f])
            assertTrue(d <= (1044L - 417) * stride, "프레임 $f 의 어긋남 $d")
        }
        assertEquals(at.last(), vbr.offsetOf(at.size - 1L), "마지막 프레임은 그대로")
    }

    // ---- 다시 담기 계획 --------------------------------------------------------

    @Test
    fun `그릇은 코덱이 정한다`() {
        val avcAac = RemuxPlan.choose(listOf("video/avc", "audio/mp4a-latm"), "mkv")!!
        assertEquals(RemuxPlan.Container.MP4, avcAac.container)
        assertEquals("mp4", avcAac.extension)
        assertEquals(0, avcAac.dropped)
        assertEquals("mov", RemuxPlan.choose(listOf("video/avc", "audio/mp4a-latm"), "mov")!!.extension)
        assertEquals(RemuxPlan.Container.THREE_GPP, RemuxPlan.choose(listOf("video/3gpp", "audio/3gpp"), "3gp")!!.container)

        val vp9 = RemuxPlan.choose(listOf("video/x-vnd.on2.vp9", "audio/opus", "audio/opus", "text/x-ssa"), "mkv")!!
        assertEquals(RemuxPlan.Container.WEBM, vp9.container)
        assertEquals(listOf(1), vp9.audio)
        assertEquals(2, vp9.dropped, "두 번째 소리와 자막")

        // 소리가 있는데 담을 수 있는 소리가 없으면 거절 — 말없이 소리 없는 영상을 만들지 않는다.
        assertEquals(null, RemuxPlan.choose(listOf("video/avc", "audio/ac3"), "mkv"))
        assertEquals(null, RemuxPlan.choose(listOf("video/av01", "audio/opus"), "webm"))
        // 담을 수 있는 소리가 하나라도 있으면 나머지는 빼고 센다.
        val mixed = RemuxPlan.choose(listOf("video/avc", "audio/ac3", "audio/mp4a-latm", "text/vtt"), "mkv")!!
        assertEquals(listOf(2), mixed.audio)
        assertEquals(2, mixed.dropped)
    }

    @Test
    fun `소리만 있으면 그 코덱의 그릇에`() {
        assertEquals("m4a", RemuxPlan.choose(listOf("audio/mp4a-latm"), "aac")!!.extension)
        assertEquals("m4a", RemuxPlan.choose(listOf("audio/mp4a-latm"), "m4a")!!.extension)
        val opus = RemuxPlan.choose(listOf("audio/opus"), "opus")!!
        assertEquals(RemuxPlan.Container.OGG, opus.container)
        assertEquals("opus", opus.extension)
        assertEquals(RemuxPlan.Container.WEBM, RemuxPlan.choose(listOf("audio/opus"), "mka")!!.container)
        val vorbis = RemuxPlan.choose(listOf("audio/vorbis"), "ogg")!!
        assertEquals(RemuxPlan.Container.WEBM, vorbis.container)
        assertEquals("webm", vorbis.extension)
        assertEquals(null, RemuxPlan.choose(listOf("audio/flac"), "mka"))
        assertEquals(null, RemuxPlan.choose(listOf("text/plain"), "srt"))
    }

    private fun indexOf(b: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..b.size - needle.size) {
            for (j in needle.indices) if (b[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun ByteArray.u32be(at: Int): Long =
        ((this[at].toLong() and 0xFF) shl 24) or ((this[at + 1].toLong() and 0xFF) shl 16) or
            ((this[at + 2].toLong() and 0xFF) shl 8) or (this[at + 3].toLong() and 0xFF)
}
