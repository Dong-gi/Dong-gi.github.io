package io.github.donggi.iroiroviewer.io.clip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 소리만 뽑기가 우리 손으로 쓰는 그릇(`AudioExtract` 의 MP3·FLAC·WAV)과, 같은 회차에 고친 자르기의 셈(Xing 목차·TLEN·빈 WAV·취소).
 * 표본은 시험이 짓고([ClipSamples]) 결과는 **다른 길로** 다시 읽는다 — FLAC 은 시험 쪽의 VERBATIM 풀개로, MP3 는 머리와 표지로.
 */
class AudioExtractSinkTest {

    private val files = ArrayList<File>()

    @AfterTest
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun temp(bytes: ByteArray = ByteArray(0)): File = File.createTempFile("sink", ".bin").also {
        it.writeBytes(bytes)
        files += it
    }

    /** [sink] 에 표본들을 먹여 쓴 바이트. */
    private fun feed(sink: AudioExtract.RawSink, samples: List<ByteArray>): ByteArray {
        val f = temp()
        RandomAccessFile(f, "rw").use { raf ->
            val w = PatchableWriter(raf)
            sink.begin(w)
            // 추출기가 주는 버퍼처럼 — 표본보다 큰 배열의 앞쪽에 담아 준다(앞자리 0).
            for (s in samples) {
                val buf = ByteArray(s.size + 100)
                s.copyInto(buf)
                sink.sample(w, buf, 0, s.size)
            }
            sink.finish(w)
            w.flush()
        }
        return f.readBytes()
    }

    // ---- MP3 -----------------------------------------------------------------

    @Test
    fun `MP3 는 맨 앞에 Xing 머리 프레임을 지어 프레임 수와 바이트 수와 목차를 적는다`() {
        val frames = (1..60).map { ClipSamples.mp3Frame(it, padding = it % 4 == 0) }
        val out = feed(AudioExtract.Mp3Sink(), frames)
        val head = Mp3Cut.parseHeader(out)
        assertNotNull(head)
        assertEquals(3, head.layer)
        assertEquals(44100, head.sampleRate)
        assertEquals(1, out[1].toInt() and 1, "우리 머리 프레임에는 CRC 가 없다")
        // MPEG-1 스테레오의 측면 정보는 32바이트 — 그 뒤가 태그다. 패딩만 다르면 같은 비트율이라 고정(Info)이다.
        val at = 4 + 32
        assertEquals("Info", String(out, at, 4))
        assertEquals(7L, out.u32be(at + 4))
        assertEquals(60L, out.u32be(at + 8))
        assertEquals(out.size.toLong(), out.u32be(at + 12))
        // 프레임은 원본 그대로 이어진다.
        var p = head.frameLength
        for (f in frames) {
            assertContentEquals(f, out.copyOfRange(p, p + f.size))
            p += f.size
        }
        // 목차 — i 번째 칸은 i% 시각의 프레임 자리 × 256 / 전체 크기. 첫 칸은 첫 소리 프레임(머리 프레임 바로 뒤)이다.
        val toc = out.copyOfRange(at + 16, at + 116)
        val starts = LongArray(frames.size)
        var acc = head.frameLength.toLong()
        for (i in frames.indices) {
            starts[i] = acc
            acc += frames[i].size
        }
        for (i in 0 until 100) {
            val frame = i * frames.size / 100
            assertEquals((starts[frame] * 256 / out.size).toInt(), toc[i].toInt() and 0xFF, "목차 $i")
        }
    }

    /**
     * 프레임이 성기게 드는 칸 수(4,096)를 넘어도 목차는 프레임마다의 자리로 지은 것과 같다(한 칸의 단위 1/256 안에서) — 예전에는 목차
     * 때문에 프레임마다 Long 을 들어 긴 소리에서 수십 MB 였다.
     */
    @Test
    fun `긴 MP3 의 목차도 프레임마다 든 자리로 지은 것과 같다`() {
        val frames = (0 until 10_000).map { ClipSamples.mp3Frame(it, padding = it % 3 == 0) }
        val out = feed(AudioExtract.Mp3Sink(), frames)
        val head = Mp3Cut.parseHeader(out)!!
        val at = 4 + 32
        assertEquals(10_000L, out.u32be(at + 8))
        assertEquals(out.size.toLong(), out.u32be(at + 12))
        val starts = LongArray(frames.size)
        var acc = head.frameLength.toLong()
        for (i in frames.indices) {
            starts[i] = acc
            acc += frames[i].size
        }
        val toc = out.copyOfRange(at + 16, at + 116)
        for (i in 0 until 100) {
            val exact = (starts[i * frames.size / 100] * 256 / out.size).toInt()
            val got = toc[i].toInt() and 0xFF
            assertTrue(kotlin.math.abs(got - exact) <= 1, "목차 $i: $got ≈ $exact")
        }
    }

    @Test
    fun `비트율이 바뀌는 MP3 는 Xing 이다`() {
        val frames = listOf(ClipSamples.mp3Frame(1), lowRateFrame(), ClipSamples.mp3Frame(2))
        val out = feed(AudioExtract.Mp3Sink(), frames)
        assertEquals("Xing", String(out, 4 + 32, 4))
        assertEquals(3L, out.u32be(4 + 32 + 8))
    }

    @Test
    fun `프레임 경계로 나뉘지 않는 흐름은 태그 없이 무음 프레임으로 둔다`() {
        val good = ClipSamples.mp3Frame(1)
        val broken = good.copyOfRange(0, 100) // 프레임의 앞 100바이트만
        val out = feed(AudioExtract.Mp3Sink(), listOf(good, broken))
        val head = Mp3Cut.parseHeader(out)!!
        assertTrue(out.copyOfRange(4, head.frameLength).all { it == 0.toByte() }, "태그를 적지 않는다")
        assertContentEquals(good + broken, out.copyOfRange(head.frameLength, out.size))
    }

    @Test
    fun `Xing 머리 프레임은 태그와 목차가 들 만큼 큰 비트율을 고른다`() {
        // MPEG-1 모노 44.1 kHz — 측면 정보 17 + 머리 4 + 태그 116 = 137바이트가 든다. 40 kbps 는 130, 48 kbps 는 156.
        val mono = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0xC4.toByte())
        val (frame, at) = XingInfo.synthesize(mono)!!
        assertEquals(4 + 17, at)
        val h = Mp3Cut.parseHeader(frame)!!
        assertEquals(48, h.bitrateKbps)
        assertEquals(3, h.channelMode)
        assertTrue(h.frameLength >= at + XingInfo.TAG_BYTES)
        // 층 II 에는 Xing 을 짓지 않는다.
        val layer2 = byteArrayOf(0xFF.toByte(), 0xFD.toByte(), 0x90.toByte(), 0xC4.toByte())
        assertNull(XingInfo.synthesize(layer2))
    }

    /** 64 kbps 스테레오 44.1 kHz 의 무음 프레임(208바이트). */
    private fun lowRateFrame(): ByteArray {
        val b = ByteArray(144 * 64_000 / 44100)
        b[0] = 0xFF.toByte()
        b[1] = 0xFB.toByte()
        b[2] = (5 shl 4).toByte()
        return b
    }

    // ---- FLAC ----------------------------------------------------------------

    @Test
    fun `FLAC 은 그릇의 머리로 세우고 번호를 0 부터 다시 매긴다`() {
        val rate = 8000
        val block = 1024
        val samples = Array(2) { c -> ShortArray(rate * 2) { i -> ((i * (c + 5)) % 20000 - 10000).toShort() } }
        val whole = ClipSamples.flac(samples, rate, block)
        // 그릇이 주는 모양 — 머리(csd-0)와 프레임. 프레임 7번부터 뽑은 것처럼 먹인다.
        val decoded = ClipSamples.decodeVerbatim(whole, 2)
        val head = AudioExtract.FlacHead.parse(whole.copyOfRange(0, decoded.firstFrameOffset))
        assertNotNull(head)
        assertEquals(listOf(4), head.kept.map { it.first }, "주석은 옮긴다")
        val frames = decoded.frames.drop(7)
        val out = feed(AudioExtract.FlacSink(head), frames)
        val d = ClipSamples.decodeVerbatim(out, 2)
        assertEquals((frames.indices).map { it.toLong() }, d.numbers)
        for (ch in 0 until 2) assertContentEquals(samples[ch].copyOfRange(7 * block, samples[ch].size), d.samples[ch])
        assertEquals((samples[0].size - 7 * block).toLong(), d.totalSamples)
        assertTrue(d.streamInfo.copyOfRange(18, 34).all { it == 0.toByte() }, "MD5 는 0")
    }

    /**
     * 이어 붙인 두 프레임은 **CRC-16 이 그대로 맞는다**(앞 프레임의 CRC 를 품고 센 CRC 는 0 이 된다) — 그래서 '표본 하나 = 프레임
     * 하나' 를 CRC 로 확인할 수 없다(처음 판이 그렇게 했다가 이 시험에 걸렸다). 경계를 찾아 나눠 쓴다.
     */
    @Test
    fun `FLAC 표본 하나에 프레임이 둘이면 나눠 번호를 매긴다`() {
        val samples = arrayOf(ShortArray(4096) { (it % 300 - 150).toShort() })
        val whole = ClipSamples.flac(samples, 8000, 1024)
        val decoded = ClipSamples.decodeVerbatim(whole, 1)
        val head = AudioExtract.FlacHead.parse(whole.copyOfRange(0, decoded.firstFrameOffset))!!
        val glued = decoded.frames[2] + decoded.frames[3]
        assertEquals(
            ((glued[glued.size - 2].toInt() and 0xFF) shl 8) or (glued[glued.size - 1].toInt() and 0xFF),
            Crc.crc16(0, glued, 0, glued.size - 2),
            "이어 붙인 두 프레임의 CRC-16 은 맞는다",
        )
        val out = feed(AudioExtract.FlacSink(head), listOf(decoded.frames[1], glued))
        val d = ClipSamples.decodeVerbatim(out, 1)
        assertEquals(listOf(0L, 1L, 2L), d.numbers)
        assertContentEquals(samples[0].copyOfRange(1024, 4096), d.samples[0])
    }

    @Test
    fun `FLAC 표본이 프레임으로 끝나지 않으면 쓰지 않는다`() {
        val samples = arrayOf(ShortArray(2048) { (it % 300).toShort() })
        val whole = ClipSamples.flac(samples, 8000, 1024)
        val decoded = ClipSamples.decodeVerbatim(whole, 1)
        val head = AudioExtract.FlacHead.parse(whole.copyOfRange(0, decoded.firstFrameOffset))!!
        val cut = decoded.frames[0].copyOfRange(0, decoded.frames[0].size - 10)
        assertFailsWith<AudioExtract.Unwritable> { feed(AudioExtract.FlacSink(head), listOf(cut)) }
    }

    @Test
    fun `그릇이 주는 FLAC 머리의 세 모양`() {
        val info = ByteArray(34) { it.toByte() }
        assertContentEquals(info, AudioExtract.FlacHead.parse(info)!!.streamInfo)
        val blocks = byteArrayOf(0x80.toByte(), 0, 0, 34) + info
        assertContentEquals(info, AudioExtract.FlacHead.parse(blocks)!!.streamInfo)
        assertContentEquals(info, AudioExtract.FlacHead.parse("fLaC".toByteArray() + blocks)!!.streamInfo)
        assertNull(AudioExtract.FlacHead.parse(null))
        assertNull(AudioExtract.FlacHead.parse(byteArrayOf(0x80.toByte(), 0, 0, 40) + info), "길이가 넘친다")
        assertNull(AudioExtract.FlacHead.parse(byteArrayOf(0x84.toByte(), 0, 0, 2, 1, 2)), "STREAMINFO 가 없다")
    }

    // ---- WAV -----------------------------------------------------------------

    @Test
    fun `PCM 은 WAV 머리를 두고 잇고 크기는 끝에서 고친다`() {
        val pcm = AudioExtract.PcmLayout(48000, 2, 24, false)
        val a = ByteArray(600) { it.toByte() }
        val b = ByteArray(301) { (it * 3).toByte() } // 홀수 — 채움 바이트가 붙는다
        val out = feed(AudioExtract.WavSink(pcm), listOf(a, b))
        assertEquals("RIFF", String(out, 0, 4))
        assertEquals((36 + 901 + 1).toLong(), out.u32le(4))
        assertEquals(1, out.u16le(20), "정수 PCM")
        assertEquals(2, out.u16le(22))
        assertEquals(48000L, out.u32le(24))
        assertEquals(48000L * 6, out.u32le(28), "초당 바이트")
        assertEquals(6, out.u16le(32), "블록 정렬")
        assertEquals(24, out.u16le(34))
        assertEquals(901L, out.u32le(40))
        assertContentEquals(a + b, out.copyOfRange(44, 44 + 901))
        assertEquals(44 + 901 + 1, out.size)
        val float = feed(AudioExtract.WavSink(AudioExtract.PcmLayout(44100, 1, 32, true)), listOf(ByteArray(8)))
        assertEquals(3, float.u16le(20), "실수 PCM")
    }

    // ---- 같은 회차에 고친 자르기의 셈 ---------------------------------------------

    @Test
    fun `MP3 를 자르면 Xing 에 목차를 적는다`(): Unit = runBlocking {
        val frames = (1..100).map { ClipSamples.mp3Frame(it) }
        val xing = ClipSamples.xingFrame(100, frames.sumOf { it.size })
        val f = temp(xing + frames.reduce { a, b -> a + b })
        val cut = RandomAccessFile(f, "r").use { raf ->
            val p = Mp3Cut.plan(raf, 1_000_000, 2_000_000, "mp3")
            assertIs<CutPlan.Ok>(p)
            val out = ByteArrayOutputStream()
            p.cut.writeTo(out) {}
            out.toByteArray()
        }
        val at = 4 + 32
        assertEquals(7L, cut.u32be(at + 4), "프레임 수 + 바이트 수 + 목차")
        val toc = cut.copyOfRange(at + 16, at + 116)
        assertTrue((1 until 100).all { (toc[it].toInt() and 0xFF) >= (toc[it - 1].toInt() and 0xFF) }, "목차는 늘기만 한다")
        assertEquals(xing.size * 256 / cut.size, toc[0].toInt() and 0xFF, "첫 칸은 첫 소리 프레임")
    }

    @Test
    fun `MP3 를 자르면 ID3 의 TLEN 을 자른 길이로 고친다`(): Unit = runBlocking {
        val tlen = "0000300000".toByteArray() // 5분 — 원본 길이
        val id3 = id3v23(listOf("TIT2" to (byteArrayOf(0) + "song".toByteArray()), "TLEN" to (byteArrayOf(0) + tlen)))
        val frames = (1..100).map { ClipSamples.mp3Frame(it) }
        val f = temp(id3 + frames.reduce { a, b -> a + b })
        val out = RandomAccessFile(f, "r").use { raf ->
            val p = Mp3Cut.plan(raf, 0, 1_000_000, "mp3") as CutPlan.Ok
            val o = ByteArrayOutputStream()
            p.cut.writeTo(o) {}
            o.toByteArray()
        }
        // 1초 = 39 프레임(1152표본) → 1018 ms. 자리 수는 그대로(앞을 0 으로).
        val at = indexOf(out, "TLEN".toByteArray()) + 10 + 1
        assertEquals("0000001018", String(out, at, 10))
        assertEquals(id3.size, id3v2Length(ArraySource(out)).toInt(), "태그 길이는 그대로")
    }

    @Test
    fun `고칠 수 없는 TLEN 은 건드리지 않는다`() {
        // 숫자가 아닌 값·UTF-16·압축 깃발.
        assertNull(Id3.tlenDigits(ArraySource(id3v23(listOf("TLEN" to (byteArrayOf(0) + "3:00".toByteArray())))), 30))
        val utf16 = id3v23(listOf("TLEN" to byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte(), '1'.code.toByte(), 0)))
        assertNull(Id3.tlenDigits(ArraySource(utf16), utf16.size.toLong()))
        val compressed = id3v23(listOf("TLEN" to (byteArrayOf(0) + "1000".toByteArray())), frameFlags = 0x0080)
        assertNull(Id3.tlenDigits(ArraySource(compressed), compressed.size.toLong()))
        assertNull(Id3.digits(123456, 5), "자리가 모자라면 고치지 않는다")
    }

    @Test
    fun `정말 빈 WAV 데이터 뒤의 청크를 소리로 자르지 않는다`(): Unit = runBlocking {
        val wav = ClipSamples.wav(8000, 1, 8000)
        // data 크기를 0 으로 하고 그 자리에 LIST 를 둔다 — 빈 데이터 + 정보 청크.
        val dataAt = indexOf(wav, "data".toByteArray())
        val list = "LIST".toByteArray() + byteArrayOf(4, 0, 0, 0) + "INFO".toByteArray()
        val bytes = wav.copyOfRange(0, dataAt) + "data".toByteArray() + byteArrayOf(0, 0, 0, 0) + list
        val p = RandomAccessFile(temp(bytes), "r").use { WavCut.plan(it, 0, 1_000_000, "wav") }
        assertIs<CutPlan.Empty>(p)
        // 크기 0 뒤가 청크가 아니면 흘려 쓴 파일이다 — 파일 끝까지가 소리다.
        val streamed = wav.copyOfRange(0, dataAt) + "data".toByteArray() + byteArrayOf(0, 0, 0, 0) + ByteArray(1600) { 7 }
        assertIs<CutPlan.Ok>(RandomAccessFile(temp(streamed), "r").use { WavCut.plan(it, 0, 100_000, "wav") })
    }

    /** 끝에 ID3v1 꼬리를 단 FLAC 을 끝까지 자르면 마지막 프레임도 담긴다 — 예전에는 끝을 파일 끝·다음 동기로만 받아 잃었다. */
    @Test
    fun `태그 꼬리를 단 FLAC 의 마지막 프레임을 잃지 않는다`(): Unit = runBlocking {
        val samples = arrayOf(ShortArray(4096) { (it % 500 - 250).toShort() })
        val tagged = ClipSamples.flac(samples, 8000, 1024) + ClipSamples.id3v1()
        val out = RandomAccessFile(temp(tagged), "r").use { raf ->
            val p = FlacCut.plan(raf, 0, 10_000_000, "flac") as CutPlan.Ok
            val o = ByteArrayOutputStream()
            p.cut.writeTo(o) {}
            o.toByteArray()
        }
        val d = ClipSamples.decodeVerbatim(out, 1)
        assertEquals(4, d.numbers.size, "프레임 넷 모두")
        assertContentEquals(samples[0], d.samples[0])
    }

    /** 메타데이터 블록 머리가 파일 끝에서 잘린 FLAC — 길이 바이트가 -1 이라 음수 길이로 던지던 것을 '다루지 않는다' 로. */
    @Test
    fun `잘린 FLAC 머리는 다루지 않는다로 끝난다`(): Unit = runBlocking {
        val whole = ClipSamples.flac(arrayOf(ShortArray(1024) { 1 }), 8000, 1024)
        // fLaC + STREAMINFO(4+34) 뒤에 둘째 블록의 머리 첫 바이트만 남긴다.
        val cut = whole.copyOfRange(0, 4 + 4 + 34) + byteArrayOf(0x04)
        cut[4] = 0x00 // STREAMINFO 가 마지막이 아니라고 적는다
        val p = RandomAccessFile(temp(cut), "r").use { FlacCut.plan(it, 0, 1_000_000, "flac") }
        assertIs<CutPlan.Unsupported>(p)
    }

    @Test
    fun `MP3 훑기는 취소를 본다`() {
        val frames = (1..200).map { ClipSamples.mp3Frame(it) }
        val f = temp(frames.reduce { a, b -> a + b })
        val cancelled = Job().also { it.cancel() }
        assertFailsWith<CancellationException> {
            runBlocking(cancelled) { RandomAccessFile(f, "r").use { Mp3Cut.plan(it, 0, 1_000_000, "mp3") } }
        }
    }

    @Test
    fun `소리만 뽑을 트랙은 쓸 수 있는 첫 소리다`() {
        val c = RemuxPlan.chooseAudio(listOf("video/avc", "audio/x-iroiro-unwritable", "audio/ac3", "audio/mp4a-latm", "text/vtt"))!!
        assertEquals(2, c.track)
        assertEquals(RemuxPlan.AudioOut.AC3, c.out)
        assertEquals(2, c.dropped, "다른 소리 트랙 둘(쓸 수 없는 것 포함). 영상·자막은 세지 않는다")
        assertEquals(RemuxPlan.AudioOut.MP2, RemuxPlan.audioOutFor("audio/mpeg-L2"))
        assertEquals(RemuxPlan.AudioOut.FLAC, RemuxPlan.audioOutFor("audio/flac"))
        assertEquals(RemuxPlan.AudioOut.WAV, RemuxPlan.audioOutFor("audio/raw"))
        assertNull(RemuxPlan.audioOutFor("audio/vnd.dts"))
        assertNull(RemuxPlan.chooseAudio(listOf("video/avc", "audio/vnd.dts")))
        assertNull(RemuxPlan.chooseAudio(listOf("video/avc")))
    }

    // ---- 짓기 ------------------------------------------------------------------

    /** ID3v2.3 태그 — (틀 이름, 몸) 차례대로. */
    private fun id3v23(frames: List<Pair<String, ByteArray>>, frameFlags: Int = 0): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, b) in frames) {
            body.write(id.toByteArray())
            body.write(byteArrayOf((b.size shr 24).toByte(), (b.size shr 16).toByte(), (b.size shr 8).toByte(), b.size.toByte()))
            body.write(byteArrayOf((frameFlags shr 8).toByte(), frameFlags.toByte()))
            body.write(b)
        }
        val n = body.size()
        val head = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            ((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte(),
        )
        return head + body.toByteArray()
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
