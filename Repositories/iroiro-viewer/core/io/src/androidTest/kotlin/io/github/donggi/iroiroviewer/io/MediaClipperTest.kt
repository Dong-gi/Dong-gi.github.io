package io.github.donggi.iroiroviewer.io

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.io.clip.Crc
import io.github.donggi.iroiroviewer.io.clip.FlacCut
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * 구간 저장([MediaClipper]). 표본은 시험이 짓는다 — MP4 는 **이 기기의 인코더**(H.264·AAC)로, FLAC 은 손으로(VERBATIM), WAV 는
 * 바이트로. 오라클은 플랫폼이다: 결과를 `MediaExtractor` 로 다시 읽고, FLAC 은 플랫폼의 디코더로 원본과 결과를 풀어 표본을 견준다.
 */
@RunWith(AndroidJUnit4::class)
class MediaClipperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    @Before
    fun setUp() {
        val root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "cliptest-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun clip(src: File, startMs: Long, endMs: Long): MediaClipper.Result = runBlocking {
        MediaClipper.clip(src.absolutePath, startMs, endMs, audioOnly = false, media = null, onProgress = {})
    }

    private fun audio(src: File, startMs: Long = 0, endMs: Long? = null): MediaClipper.Result = runBlocking {
        MediaClipper.clip(src.absolutePath, startMs, endMs, audioOnly = true, media = null, onProgress = {})
    }

    private fun partials(): List<String> = work.list().orEmpty().filter { it.startsWith(AtomicFileWriter.PART_PREFIX) }

    @Test
    fun 영상은_시작_앞의_키프레임부터_담기고_회전이_남는다() {
        val src = File(work, "src.mp4")
        makeMp4(src, seconds = 4, fps = 10, rotation = 90)
        // 1.5초 바로 앞의 키프레임 — 원본에서 잰다(인코더가 키프레임 간격을 정확히 지킨다고 가정하지 않는다).
        val key = MediaExtractor().run {
            setDataSource(src.absolutePath)
            val v = (0 until trackCount).first { getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
            selectTrack(v)
            seekTo(1_500_000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            sampleTime.also { release() }
        }
        assertTrue("키프레임이 1.5초 앞이어야 한다: $key", key in 0..1_500_000)

        val result = clip(src, 1500, 2500)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!)
        assertEquals("src_clip.mp4", out.name)
        val note = (result.note as FileOpManager.SavedNote.Clipped)
        assertEquals(key / 1000, note.startMs)
        assertEquals(2500L, note.endMs)
        assertTrue(partials().isEmpty())

        MediaExtractor().run {
            setDataSource(out.absolutePath)
            assertEquals(2, trackCount)
            val formats = (0 until trackCount).map { getTrackFormat(it) }
            val v = formats.indexOfFirst { it.getString(MediaFormat.KEY_MIME) == "video/avc" }
            val a = formats.indexOfFirst { it.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" }
            assertTrue(v >= 0 && a >= 0)
            assertEquals(90, formats[v].getInteger(MediaFormat.KEY_ROTATION))
            selectTrack(v)
            assertEquals(0L, sampleTime)
            assertTrue("첫 영상 프레임은 키프레임이다", sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
            var frames = 0
            var last = 0L
            while (sampleTrackIndex >= 0) {
                frames++
                last = maxOf(last, sampleTime)
                advance()
            }
            // 키프레임부터 2.5초 앞까지의 프레임(10 fps).
            val expected = ((2_500_000 - key) / 100_000).toInt()
            assertEquals(expected, frames)
            assertTrue("끝을 넘지 않는다: $last", last < 2_500_000 - key)
            release()
        }
    }

    @Test
    fun WAV_는_고른_표본만큼_담긴다() {
        val rate = 8000
        val src = File(work, "voice.wav").apply { writeBytes(wav(rate, frames = rate * 2)) }
        val result = clip(src, 250, 750)
        assertTrue(result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!).readBytes()
        assertEquals("RIFF", String(out, 0, 4))
        // 머리 44바이트(fmt 16) + 0.5초 × 8000 × 2바이트.
        assertEquals(44 + rate / 2 * 2, out.size)
        val srcBytes = src.readBytes()
        assertArrayEquals(srcBytes.copyOfRange(44 + rate / 4 * 2, 44 + rate * 3 / 4 * 2), out.copyOfRange(44, out.size))
    }

    @Test
    fun FLAC_을_자르면_플랫폼_디코더가_원본과_같은_표본을_푼다() {
        val rate = 44100
        val block = 4096
        val n = rate * 3
        val samples = ShortArray(n) { i -> (sin(i * 2 * Math.PI * 440 / rate) * 12000).toInt().toShort() }
        val src = File(work, "tone.flac").apply { writeBytes(flac(samples, rate, block)) }
        val result = clip(src, 1000, 2000)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val original = decodePcm(src)
        val cut = decodePcm(File(result.path!!))
        assertEquals("원본은 그대로 풀린다", n, original.size)
        val from = rate / block * block
        val to = (rate * 2 + block - 1) / block * block
        assertEquals(to - from, cut.size)
        assertArrayEquals(original.copyOfRange(from, to), cut)
    }

    @Test
    fun 담을_수_없는_것은_형식으로_말한다() {
        val fake = File(work, "notes.mp4").apply { writeText("이것은 영상이 아니다".repeat(100)) }
        val result = clip(fake, 0, 1000)
        assertEquals(FileOpEngine.Reason.UNSUPPORTED, (result.outcome as FileOpEngine.Outcome.Failed).reason)
        assertTrue(partials().isEmpty())
        assertEquals(listOf("notes.mp4"), work.list()!!.toList())
    }

    @Test
    fun 같은_이름이_있으면_번호를_붙인다() {
        val src = File(work, "voice.wav").apply { writeBytes(wav(8000, frames = 8000)) }
        File(work, "voice_clip.wav").writeText("이미 있다")
        val result = clip(src, 0, 600)
        assertEquals("voice_clip (2).wav", File(result.path!!).name)
        assertEquals("이미 있다", File(work, "voice_clip.wav").readText())
    }

    @Test
    fun 취소하면_아무것도_남지_않는다() = runBlocking {
        // 크게 지어 쓰는 사이에 끊는다(약 64 MB).
        val rate = 48000
        val src = File(work, "long.wav").apply { writeBytes(wav(rate, frames = rate * 340)) }
        val job = async { MediaClipper.clip(src.absolutePath, 0, 339_000, audioOnly = false, media = null, onProgress = {}) }
        delay(30)
        job.cancel()
        runCatching { job.await() }
        // 취소 뒤 정리를 기다린다(임시본을 지우는 것은 NonCancellable 안이다).
        for (i in 0 until 50) {
            if (partials().isEmpty()) break
            Thread.sleep(50)
        }
        assertTrue(partials().toString(), partials().isEmpty())
        assertEquals(listOf("long.wav"), work.list()!!.toList())
    }

    // ---- 소리만 뽑기 ---------------------------------------------------------------

    @Test
    fun 영상에서_소리만_뽑으면_m4a_에_원본과_같은_표본이_담긴다() {
        val src = File(work, "src.mp4")
        makeMp4(src, seconds = 3, fps = 10, rotation = 0)
        val result = audio(src)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!)
        assertEquals("src_audio.m4a", out.name)
        assertTrue(partials().isEmpty())
        val formats = MediaExtractor().run {
            setDataSource(out.absolutePath)
            (0 until trackCount).map { getTrackFormat(it) }.also { release() }
        }
        assertEquals("소리 트랙 하나", listOf("audio/mp4a-latm"), formats.map { it.getString(MediaFormat.KEY_MIME) })
        // 오라클 — 원본의 소리 표본과 바이트가 같다(다시 인코딩하지 않는다).
        val before = samplesOf(src, "audio/")
        val after = samplesOf(out, "audio/")
        assertEquals(before.size, after.size)
        for (i in before.indices) assertArrayEquals("표본 $i", before[i].second, after[i].second)
        assertEquals(0L, after.first().first)
    }

    @Test
    fun 구간을_골라_소리만_뽑으면_그_구간의_표본만_담긴다() {
        val src = File(work, "src.mp4")
        makeMp4(src, seconds = 3, fps = 10, rotation = 0)
        val result = audio(src, 1000, 2000)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val note = result.note as FileOpManager.SavedNote.Clipped
        // 소리 프레임은 모두 키프레임이다 — 시작은 1초를 품은 프레임(AAC 1024표본 ≈ 23 ms)이고, 영상처럼 앞당겨지지 않는다.
        assertTrue("시작 ${note.startMs}", note.startMs in 976..1000)
        val before = samplesOf(src, "audio/").filter { it.first >= note.startMs * 1000 && it.first < 2_000_000 }
        val after = samplesOf(File(result.path!!), "audio/")
        assertEquals(before.size, after.size)
        for (i in before.indices) assertArrayEquals(before[i].second, after[i].second)
    }

    @Test
    fun 소리_트랙이_없는_영상은_그렇게_말한다() {
        val src = File(work, "silent.mp4")
        val video = encodeVideo(seconds = 1, fps = 10)
        MediaMuxer(src.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).run {
            val t = addTrack(video.first)
            start()
            for ((data, info) in video.second) writeSampleData(t, ByteBuffer.wrap(data), info)
            stop()
            release()
        }
        val result = audio(src)
        assertEquals(FileOpEngine.Reason.NO_AUDIO, (result.outcome as FileOpEngine.Outcome.Failed).reason)
        assertEquals(listOf("silent.mp4"), work.list()!!.toList())
        assertEquals(MediaClipper.AudioState.NONE, runBlocking { MediaClipper.preview(src.absolutePath, 0) }.audio)
    }

    @Test
    fun MKV_의_FLAC_소리는_FLAC_으로_뽑히고_플랫폼_디코더가_같은_표본을_푼다() {
        val rate = 44100
        val block = 4096
        val n = rate * 3
        val samples = ShortArray(n) { i -> (sin(i * 2 * Math.PI * 330 / rate) * 9000).toInt().toShort() }
        val flac = flac(samples, rate, block)
        // FLAC 파일을 머리(fLaC + STREAMINFO)와 프레임으로 가른다 — 머리는 CodecPrivate, 프레임은 블록 하나씩.
        val head = flac.copyOfRange(0, 4 + 4 + 34)
        val frames = splitFlacFrames(flac, head.size, n, block)
        val blocks = frames.mapIndexed { i, f -> (i.toLong() * block * 1000 / rate) to f }
        val src = File(work, "tone.mkv").apply { writeBytes(mkv("A_FLAC", head, rate, 1, 16, blocks)) }
        // 영상째로는 담을 수 없다(MP4 에 FLAC 을 넣지 못한다) — 소리만은 뽑힌다.
        val preview = runBlocking { MediaClipper.preview(src.absolutePath, 0) }
        assertEquals(MediaClipper.AudioState.WRITABLE, preview.audio)

        val result = audio(src, 1000, 2000)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!)
        assertEquals("tone_audio.flac", out.name)
        // 1초를 품은 프레임(10번, 0.929초)부터 2초 앞의 마지막 프레임(21번)까지.
        val first = 10
        val last = 21
        val decoded = decodePcm(out)
        assertArrayEquals(samples.copyOfRange(first * block, (last + 1) * block), decoded)
        // STREAMINFO 의 표본 수가 담은 길이다.
        val info = out.readBytes().copyOfRange(8, 8 + 34)
        val total = ((info[13].toLong() and 0x0F) shl 32) or ((info[14].toLong() and 0xFF) shl 24) or
            ((info[15].toLong() and 0xFF) shl 16) or ((info[16].toLong() and 0xFF) shl 8) or (info[17].toLong() and 0xFF)
        assertEquals(((last + 1 - first) * block).toLong(), total)
    }

    @Test
    fun MKV_의_PCM_소리는_WAV_로_뽑힌다() {
        val rate = 8000
        val pcm = ByteArray(rate * 2 * 2) { i -> (i * 13 + (i shr 5)).toByte() } // 2초, 16비트 모노
        val blockBytes = rate / 10 * 2 // 100 ms
        val blocks = (0 until pcm.size / blockBytes).map { i ->
            (i * 100L) to pcm.copyOfRange(i * blockBytes, (i + 1) * blockBytes)
        }
        val src = File(work, "voice.mkv").apply { writeBytes(mkv("A_PCM/INT/LIT", null, rate, 1, 16, blocks)) }
        val result = audio(src)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!)
        assertEquals("voice_audio.wav", out.name)
        val bytes = out.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(1, bytes[20].toInt()) // PCM
        assertEquals(16, bytes[34].toInt())
        assertEquals(pcm.size, ByteBuffer.wrap(bytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertArrayEquals(pcm, bytes.copyOfRange(44, bytes.size))
    }

    @Test
    fun MKV_의_MP3_소리는_목차를_단_MP3_로_뽑힌다() {
        // 가변 비트율 — 앞 절반은 32 kbps, 뒤 절반은 320 kbps 의 무음 프레임(몸이 0 이면 디코더는 무음으로 푼다).
        val count = 80
        val frames = (0 until count).map { i -> silentMp3Frame(if (i < count / 2) 1 else 14) }
        val blocks = frames.mapIndexed { i, f -> (i * 1152L * 1000 / 44100) to f }
        val src = File(work, "talk.mkv").apply { writeBytes(mkv("A_MPEG/L3", null, 44100, 1, 0, blocks)) }
        val result = audio(src)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!)
        assertEquals("talk_audio.mp3", out.name)
        val bytes = out.readBytes()
        // 첫 프레임은 우리가 지은 Xing 머리 — MPEG-1 모노는 측면 정보가 17바이트다.
        val tagAt = 4 + 17
        assertEquals("Xing", String(bytes, tagAt, 4))
        val flags = ByteBuffer.wrap(bytes, tagAt + 4, 4).int
        assertEquals("프레임 수·바이트 수·목차", 7, flags)
        assertEquals(count, ByteBuffer.wrap(bytes, tagAt + 8, 4).int)
        assertEquals(bytes.size, ByteBuffer.wrap(bytes, tagAt + 12, 4).int)
        // 플랫폼도 그 머리로 길이를 안다(프레임 수 × 1152 / 44100).
        val duration = MediaExtractor().run {
            setDataSource(out.absolutePath)
            getTrackFormat(0).getLong(MediaFormat.KEY_DURATION).also { release() }
        }
        val expected = count * 1152L * 1_000_000 / 44100
        assertTrue("길이 $duration ≈ $expected", kotlin.math.abs(duration - expected) < 30_000)
    }

    @Test
    fun 소리만_든_TS_는_다시_담지_못해도_고른_구간이_소리_뽑기로_저장된다() {
        // 플랫폼은 MP2 를 다시 담지 못한다 — 영상이 없으니 소리 뽑기가 곧 그 구간이다(엔진은 쓸 수 있는데 칸이 막았다).
        // 프레임마다 번호를 적어 두고 결과의 프레임 번호를 바이트로 읽는다. 프레임 하나는 1152표본 = 24 ms.
        val src = File(work, "radio.ts").apply { writeBytes(ts(TsStream(0x101, 0x03, 0xC0, mp2Units(250)))) }
        val preview = runBlocking { MediaClipper.preview(src.absolutePath, 3010) }
        assertTrue("미리 보기가 저장할 수 있다고 말해야 한다", preview.supported)
        assertEquals(MediaClipper.AudioState.WRITABLE, preview.audio)

        for ((result, name) in listOf(clip(src, 3010, 4500) to "radio_clip.mp2", audio(src, 3010, 4500) to "radio_audio.mp2")) {
            assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
            val out = File(result.path!!)
            assertEquals(name, out.name)
            // 3.010초를 품은 프레임(125번, 3.000초)부터 4.5초 앞의 마지막 프레임(187번, 4.488초)까지.
            assertEquals((125..187).toList(), mp2Numbers(out))
            assertEquals(3000L, (result.note as FileOpManager.SavedNote.Clipped).startMs)
        }
        assertTrue(partials().isEmpty())
    }

    @Test
    fun 영상이_있는_TS_에서_소리만_뽑으면_되감기가_듣지_않아도_고른_시각부터_담긴다() {
        // 플랫폼의 TS 추출기는 기준(영상) 트랙으로만 되감는다 — 소리 트랙만 고르면 되감기가 아무 일도 하지 않아, 추출기가 처음부터
        // 내주는 대로 쓰면 고른 시작이 아니라 0:00 부터 담겼다(30분 녹화의 25분부터를 고르면 0:00 부터).
        val src = File(work, "show.ts").apply {
            writeBytes(ts(TsStream(0x100, 0x1B, 0xE0, h264Units(seconds = 6, fps = 10)), TsStream(0x101, 0x03, 0xC0, mp2Units(250))))
        }
        // MP4 는 MP2 를 담지 못한다 — 영상째로는 저장할 수 없고 소리만은 된다.
        val preview = runBlocking { MediaClipper.preview(src.absolutePath, 3010) }
        assertTrue("영상째로는 저장할 수 없어야 한다", !preview.supported)
        assertEquals(MediaClipper.AudioState.WRITABLE, preview.audio)
        assertEquals(FileOpEngine.Reason.UNSUPPORTED, (clip(src, 3010, 4500).outcome as FileOpEngine.Outcome.Failed).reason)

        val result = audio(src, 3010, 4500)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!)
        assertEquals("show_audio.mp2", out.name)
        assertEquals((125..187).toList(), mp2Numbers(out))
        assertEquals(3000L, (result.note as FileOpManager.SavedNote.Clipped).startMs)
        assertTrue(partials().isEmpty())
    }

    @Test
    fun 영상이_있는_TS_의_AAC_를_뽑으면_고른_시각을_품은_프레임부터_같은_바이트로_담긴다() {
        // 위와 같은 되감기 함정을 플랫폼 그릇(MP4)으로 쓰는 길에서 — 소리만 담을 때 고른 시각 이하의 마지막 표본을 들고 간다.
        val (adts, raw) = aacUnits(seconds = 4)
        val src = File(work, "news.ts").apply {
            writeBytes(ts(TsStream(0x100, 0x1B, 0xE0, h264Units(seconds = 4, fps = 10)), TsStream(0x101, 0x0F, 0xC0, adts)))
        }
        val result = audio(src, 1500, 3000)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val out = File(result.path!!)
        assertEquals("news_audio.m4a", out.name)
        // 오라클은 인코더가 준 프레임과 그 시각이다(TS 를 다시 읽지 않는다 — 읽는 쪽도 같은 추출기다).
        val firstIndex = raw.indexOfLast { it.first <= 1_500_000 }
        val expected = raw.filterIndexed { i, (t, _) -> i >= firstIndex && t < 3_000_000 }
        val got = samplesOf(out, "audio/")
        assertEquals(expected.size, got.size)
        // 플랫폼의 TS 추출기는 AAC 를 ADTS 머리째 준다(`is-adts`) — 머리를 떼지 않으면 m4a 의 표본마다 7바이트가 붙어 다른 재생기가
        // 풀지 못한다(이 시험이 처음 잡았다: 186바이트 프레임이 193바이트로 담겼다).
        for (i in expected.indices) assertArrayEquals("프레임 $i", expected[i].second, got[i].second)
        val note = result.note as FileOpManager.SavedNote.Clipped
        assertTrue("시작 ${note.startMs}", note.startMs in 1477..1500)

        // 영상째로는 MP4 로 담긴다(H.264 + AAC) — 소리는 키프레임(1초)부터, 역시 머리를 뗀 날것으로.
        val whole = clip(src, 1500, 3000)
        assertTrue(whole.outcome.toString(), whole.outcome is FileOpEngine.Outcome.Done)
        val clipped = File(whole.path!!)
        assertEquals("news_clip.mp4", clipped.name)
        val keyUs = (whole.note as FileOpManager.SavedNote.Clipped).startMs * 1000
        assertTrue("키프레임 $keyUs", keyUs in 900_000..1_500_000)
        val clipExpected = raw.filter { (t, _) -> t >= keyUs - 100 && t < 3_000_000 }
        val clipGot = samplesOf(clipped, "audio/")
        assertEquals(clipExpected.size, clipGot.size)
        for (i in clipExpected.indices) assertArrayEquals("영상째 프레임 $i", clipExpected[i].second, clipGot[i].second)
        assertTrue(partials().isEmpty())
    }

    @Test
    fun 방송처럼_영상을_앞서_실은_TS_도_고른_구간이_어긋나지_않는다() {
        // 방송은 영상을 소리보다 앞서 싣는다(여기서는 0.5초). 플랫폼의 TS 추출기는 영상과 함께 되감으면 시간축을 그 자리 뒤에 처음 읽은
        // PES 로 다시 세워, 2.5~4.5초를 고르면 소리는 1.5초부터·영상은 열여섯 장만 담겼다(이 시험이 처음 잡았다). TS 는 처음부터 읽는다.
        val (adts, raw) = aacUnits(seconds = 6)
        val src = File(work, "bcast.ts").apply {
            writeBytes(ts(TsStream(0x100, 0x1B, 0xE0, h264Units(seconds = 6, fps = 10), leadTicks = 45_000), TsStream(0x101, 0x0F, 0xC0, adts)))
        }
        // 미리 보기도 같은 키프레임(2초 — 인코더의 키프레임 간격은 1초다)을 말한다.
        assertEquals(2000L, runBlocking { MediaClipper.preview(src.absolutePath, 2500) }.videoStartMs)

        val whole = clip(src, 2500, 4500)
        assertTrue(whole.outcome.toString(), whole.outcome is FileOpEngine.Outcome.Done)
        val clipped = File(whole.path!!)
        assertEquals(2000L, (whole.note as FileOpManager.SavedNote.Clipped).startMs)
        // 영상 — 2.0초 키프레임부터 4.4초까지 스물다섯 장, 100 ms 간격 그대로.
        val video = samplesOf(clipped, "video/")
        assertEquals((0 until 25).map { it * 100_000L }, video.map { it.first })
        // 소리 — 키프레임 이후 4.5초 앞까지의 프레임이 같은 바이트로, 같은 간격으로.
        val expected = raw.filter { (t, _) -> t >= 2_000_000 && t < 4_500_000 }
        val got = samplesOf(clipped, "audio/")
        assertEquals(expected.size, got.size)
        for (i in expected.indices) assertArrayEquals("영상째 프레임 $i", expected[i].second, got[i].second)
        assertTrue("소리가 영상과 맞아야 한다: ${got.first().first}", kotlin.math.abs(got.first().first - (expected.first().first - 2_000_000)) < 1_000)

        // 소리만 — 2.5초를 품은 프레임부터.
        val only = audio(src, 2500, 4500)
        assertTrue(only.outcome.toString(), only.outcome is FileOpEngine.Outcome.Done)
        val first = raw.indexOfLast { it.first <= 2_500_000 }
        val onlyExpected = raw.filterIndexed { i, (t, _) -> i >= first && t < 4_500_000 }
        val onlyGot = samplesOf(File(only.path!!), "audio/")
        assertEquals(onlyExpected.size, onlyGot.size)
        for (i in onlyExpected.indices) assertArrayEquals("소리만 프레임 $i", onlyExpected[i].second, onlyGot[i].second)
        assertTrue(partials().isEmpty())
    }

    /**
     * 플랫폼의 TS 추출기는 PES 하나에서 시작하는 ADTS 프레임을 **모두 묶어** 표본 하나로 준다(머리째 — 이 기기 실측: 프레임 다섯을 실은
     * PES 가 964바이트 표본 하나). ffmpeg 가 만든 TS·HLS 가 흔히 이 꼴이다. 예전에는 첫 머리의 길이가 표본과 달라 소리만 저장과 영상째
     * 구간 저장이 **둘 다** '이 형식은 별도로 저장할 수 없습니다' 였다. 프레임마다 갈라 담으면 인코더가 준 프레임과 바이트가 같고, 시각도
     * 프레임마다 1024표본씩이다.
     */
    @Test
    fun PES_하나에_AAC_다섯을_실은_TS_도_소리만_저장과_구간_저장이_되고_프레임이_같은_바이트로_담긴다() {
        val (adts, raw) = aacUnits(seconds = 4)
        // 다섯씩 묶어 PES 하나로 — 시각은 첫 프레임의 것이다(PES 의 PTS 는 그 안에서 시작하는 첫 접근 단위의 것).
        val grouped = adts.chunked(5).map { g -> g.first().first to g.map { it.second }.reduce { a, b -> a + b } }
        val src = File(work, "hls.ts").apply {
            writeBytes(ts(TsStream(0x100, 0x1B, 0xE0, h264Units(seconds = 4, fps = 10)), TsStream(0x101, 0x0F, 0xC0, grouped)))
        }
        // 추출기가 정말 PES 하나를 표본 하나로 주는가 — 아니면 이 시험이 지키려는 길을 타지 않는다.
        assertEquals("PES 하나가 표본 하나로 와야 한다", grouped.first().second.size, samplesOf(src, "audio/").first().second.size)

        val only = audio(src, 1500, 3000)
        assertTrue(only.outcome.toString(), only.outcome is FileOpEngine.Outcome.Done)
        val onlyOut = File(only.path!!)
        assertEquals("hls_audio.m4a", onlyOut.name)
        // 오라클은 인코더가 준 프레임과 그 시각이다 — 1.5초를 품은 프레임부터 3초 앞까지.
        val firstIndex = raw.indexOfLast { it.first <= 1_500_000 }
        val expected = raw.filterIndexed { i, (t, _) -> i >= firstIndex && t < 3_000_000 }
        val got = samplesOf(onlyOut, "audio/")
        assertEquals(expected.size, got.size)
        for (i in expected.indices) assertArrayEquals("소리만 프레임 $i", expected[i].second, got[i].second)
        // 묶여 온 다섯이 한 시각에 몰리지 않는다 — 프레임마다 1024 / 44100 초(약 23.2 ms).
        for (i in 1 until got.size) {
            val d = got[i].first - got[i - 1].first
            assertTrue("프레임 $i 의 간격 $d", kotlin.math.abs(d - 23_220) <= 50)
        }
        val onlyNote = only.note as FileOpManager.SavedNote.Clipped
        assertTrue("시작 ${onlyNote.startMs}", onlyNote.startMs in 1477..1500)

        // 영상째로 — MP4(H.264 + AAC). 소리는 키프레임(1초)부터, 키프레임 앞에서 시작한 PES 안의 뒤쪽 프레임까지 같은 바이트로.
        val whole = clip(src, 1500, 3000)
        assertTrue(whole.outcome.toString(), whole.outcome is FileOpEngine.Outcome.Done)
        val clipped = File(whole.path!!)
        assertEquals("hls_clip.mp4", clipped.name)
        val keyUs = (whole.note as FileOpManager.SavedNote.Clipped).startMs * 1000
        assertTrue("키프레임 $keyUs", keyUs in 900_000..1_500_000)
        val clipExpected = raw.filter { (t, _) -> t >= keyUs - 100 && t < 3_000_000 }
        val clipGot = samplesOf(clipped, "audio/")
        assertEquals(clipExpected.size, clipGot.size)
        for (i in clipExpected.indices) assertArrayEquals("영상째 프레임 $i", clipExpected[i].second, clipGot[i].second)
        assertTrue(partials().isEmpty())
    }

    /**
     * 목차(Cues) 없는 MKV 의 되감기는 그 시각 **뒤의** 블록으로 간다 — 고른 시작이 마지막 블록 안이면 뒤의 것이 없어 추출기가 -1 을 준다.
     * 예전에는 그 -1 을 그대로 받아 '고른 구간에 담을 것이 없습니다' 였다(영상이면 미리 보기가 키프레임을 말하지 못했다 — 같은 도우미다).
     * 앞에서 다시 찾아 마지막 블록부터 담는다.
     */
    @Test
    fun 목차_없는_MKV_의_마지막_블록_안에서_시작해도_그_블록부터_담긴다() {
        val rate = 44100
        val block = 4096
        val n = rate * 3
        val samples = ShortArray(n) { i -> (sin(i * 2 * Math.PI * 330 / rate) * 9000).toInt().toShort() }
        val flac = flac(samples, rate, block)
        val head = flac.copyOfRange(0, 4 + 4 + 34)
        val frames = splitFlacFrames(flac, head.size, n, block)
        val blocks = frames.mapIndexed { i, f -> (i.toLong() * block * 1000 / rate) to f }
        val src = File(work, "tail.mkv").apply { writeBytes(mkv("A_FLAC", head, rate, 1, 16, blocks)) }
        // 마지막 블록(32번, 2.972초 ~ 3.0초) 안을 시작으로, 끝까지.
        val lastMs = blocks.last().first
        val result = audio(src, lastMs + 15)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        assertEquals(lastMs, (result.note as FileOpManager.SavedNote.Clipped).startMs)
        assertArrayEquals(samples.copyOfRange((frames.size - 1) * block, n), decodePcm(File(result.path!!)))
    }

    /**
     * 소리 트랙이 둘인 영상에서 **둘째**가 그 구간에 비면(짧은 해설 트랙 등) 그 트랙만 빼고 담고 뺀 수를 결과가 말한다 — 예전에는 영상과
     * 첫 소리가 다 있어도 '고른 구간에 담을 것이 없습니다' 였다. **첫** 소리가 비면 소리 없는 영상을 만들지 않고, 빈 것이 영상이 아니라
     * 소리라고 말한다.
     */
    @Test
    fun 둘째_소리가_그_구간에_비면_빼고_담고_첫_소리가_비면_소리가_없다고_말한다() {
        val video = encodeVideo(seconds = 4, fps = 10)
        val long = encodeAudio(seconds = 4)
        val short = encodeAudio(seconds = 1)

        val dub = File(work, "dub.mp4").apply { mp4Of(this, video, long, short) }
        val result = clip(dub, 2000, 3000)
        assertTrue(result.outcome.toString(), result.outcome is FileOpEngine.Outcome.Done)
        val note = result.note as FileOpManager.SavedNote.Clipped
        assertEquals("빈 둘째 소리를 뺐다", 1, note.droppedTracks)
        val out = File(result.path!!)
        val mimes = MediaExtractor().run {
            setDataSource(out.absolutePath)
            (0 until trackCount).map { getTrackFormat(it).getString(MediaFormat.KEY_MIME) }.also { release() }
        }
        assertEquals(listOf("audio/mp4a-latm", "video/avc"), mimes.sortedBy { it })
        // 담긴 소리는 긴 트랙의 그 구간이다(키프레임부터).
        val keyUs = note.startMs * 1000
        val expected = long.second.filter { (_, info) -> info.presentationTimeUs >= keyUs - 100 && info.presentationTimeUs < 3_000_000 }
        val got = samplesOf(out, "audio/")
        assertEquals(expected.size, got.size)
        for (i in expected.indices) assertArrayEquals("프레임 $i", expected[i].first, got[i].second)

        // 첫 소리가 그 구간에 없다 — 소리 없는 영상을 만들지 않는다.
        val late = File(work, "late.mp4").apply { mp4Of(this, video, short, long) }
        assertEquals(FileOpEngine.Reason.NO_AUDIO_IN_RANGE, (clip(late, 2000, 3000).outcome as FileOpEngine.Outcome.Failed).reason)
        assertTrue(partials().isEmpty())
    }

    /**
     * 처음부터 읽는 그릇(TS)에서도 — 소리가 영상보다 먼저 끝난 구간은 '담을 것이 없다' 가 아니라 '소리가 없다' 다. 키프레임을 찾는 걸음이
     * 소리 트랙도 함께 보아, 영상을 다 쓰기 전에 안다.
     */
    @Test
    fun TS_에서_소리가_먼저_끝난_구간은_소리가_없다고_말한다() {
        val (adts, _) = aacUnits(seconds = 1)
        val src = File(work, "cut.ts").apply {
            writeBytes(ts(TsStream(0x100, 0x1B, 0xE0, h264Units(seconds = 4, fps = 10)), TsStream(0x101, 0x0F, 0xC0, adts)))
        }
        assertEquals(FileOpEngine.Reason.NO_AUDIO_IN_RANGE, (clip(src, 2000, 3000).outcome as FileOpEngine.Outcome.Failed).reason)
        // 소리가 있는 구간은 그대로 된다.
        assertTrue(clip(src, 0, 900).outcome is FileOpEngine.Outcome.Done)
        assertTrue(partials().isEmpty())
    }

    /** 영상 하나와 소리 여럿의 MP4 — 이 기기의 인코더가 준 표본을 시각 차례로 섞어 담는다(트랙은 넘긴 차례대로). */
    private fun mp4Of(
        file: File,
        video: Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>>,
        vararg audio: Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>>,
    ) {
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vt = muxer.addTrack(video.first)
        val at = audio.map { muxer.addTrack(it.first) }
        muxer.start()
        val all = video.second.map { Triple(vt, it.first, it.second) } +
            audio.withIndex().flatMap { (i, a) -> a.second.map { Triple(at[i], it.first, it.second) } }
        for ((t, data, info) in all.sortedBy { it.third.presentationTimeUs }) {
            muxer.writeSampleData(t, ByteBuffer.wrap(data), info)
        }
        muxer.stop()
        muxer.release()
    }

    /** 결과 MP2 의 프레임 번호들([mp2Units] 가 끝 4바이트에 적었다). */
    private fun mp2Numbers(file: File): List<Int> {
        val bytes = file.readBytes()
        assertEquals(0, bytes.size % MP2_FRAME)
        return (0 until bytes.size / MP2_FRAME).map { ByteBuffer.wrap(bytes, (it + 1) * MP2_FRAME - 4, 4).int }
    }

    /** 트랙 하나([mimePrefix] 로 시작하는 첫 트랙)의 표본들 — (시각, 바이트). */
    private fun samplesOf(file: File, mimePrefix: String): List<Pair<Long, ByteArray>> {
        val ex = MediaExtractor()
        ex.setDataSource(file.absolutePath)
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(mimePrefix) }
        ex.selectTrack(track)
        val out = ArrayList<Pair<Long, ByteArray>>()
        val buf = ByteBuffer.allocate(1 shl 20)
        while (ex.sampleTrackIndex >= 0) {
            buf.clear()
            val n = ex.readSampleData(buf, 0)
            out += ex.sampleTime to buf.array().copyOfRange(buf.arrayOffset(), buf.arrayOffset() + n)
            ex.advance()
        }
        ex.release()
        return out
    }

    /**
     * VERBATIM FLAC([flac]) 의 프레임들 — 지은 모양 그대로 길이를 센다(동기 부호로 가르면 소리 데이터 속의 우연한 `FF F8` 에 속을 수
     * 있다). 프레임 = 머리(4 + 번호 + 블록 크기 2 + CRC-8 1) + 하위 프레임 머리 1 + 표본 × 2 + CRC-16 2.
     */
    private fun splitFlacFrames(flac: ByteArray, from: Int, total: Int, block: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var at = from
        var index = 0L
        var left = total
        while (left > 0) {
            val bs = minOf(block, left)
            val len = 4 + FlacCut.encodeNumber(index).size + 2 + 1 + 1 + bs * 2 + 2
            out += flac.copyOfRange(at, at + len)
            at += len
            left -= bs
            index++
        }
        check(at == flac.size) { "프레임 길이 셈이 틀렸다" }
        return out
    }

    /** 무음 MP3 프레임 — MPEG-1 층 III, 44.1 kHz, 모노, CRC 없음, 비트율 번호 [bitrateIndex]. 몸(측면 정보·주 데이터)이 0 이다. */
    private fun silentMp3Frame(bitrateIndex: Int): ByteArray {
        val kbps = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)[bitrateIndex]
        val f = ByteArray(144 * kbps * 1000 / 44100)
        f[0] = 0xFF.toByte()
        f[1] = 0xFB.toByte()
        f[2] = (bitrateIndex shl 4).toByte()
        f[3] = 0xC4.toByte()
        return f
    }

    /**
     * 소리 트랙 하나의 Matroska — EBML 을 손으로 쓴다(인코더 없이). [blocks] 는 (밀리초, 바이트). 클러스터 하나, SimpleBlock 마다 키프레임.
     */
    private fun mkv(
        codec: String,
        codecPrivate: ByteArray?,
        rate: Int,
        channels: Int,
        bitDepth: Int,
        blocks: List<Pair<Long, ByteArray>>,
    ): ByteArray {
        fun vint(v: Long): ByteArray = when {
            v < 0x7F -> byteArrayOf((0x80 or v.toInt()).toByte())
            v < 0x3FFF -> byteArrayOf((0x40 or (v shr 8).toInt()).toByte(), v.toByte())
            v < 0x1FFFFF -> byteArrayOf((0x20 or (v shr 16).toInt()).toByte(), (v shr 8).toByte(), v.toByte())
            else -> ByteArray(8).also { b ->
                b[0] = 0x01
                for (k in 1 until 8) b[k] = (v shr (8 * (7 - k))).toByte()
            }
        }
        fun el(id: Int, body: ByteArray): ByteArray {
            val idBytes = when {
                id > 0xFFFFFF -> byteArrayOf((id shr 24).toByte(), (id shr 16).toByte(), (id shr 8).toByte(), id.toByte())
                id > 0xFFFF -> byteArrayOf((id shr 16).toByte(), (id shr 8).toByte(), id.toByte())
                id > 0xFF -> byteArrayOf((id shr 8).toByte(), id.toByte())
                else -> byteArrayOf(id.toByte())
            }
            return idBytes + vint(body.size.toLong()) + body
        }
        fun uint(id: Int, v: Long): ByteArray {
            val all = ByteBuffer.allocate(8).putLong(v).array()
            val firstNonZero = all.indexOfFirst { it != 0.toByte() }.let { if (it < 0) 7 else it }
            return el(id, all.copyOfRange(firstNonZero, 8))
        }
        fun float(id: Int, v: Double): ByteArray = el(id, ByteBuffer.allocate(8).putDouble(v).array())
        fun str(id: Int, v: String): ByteArray = el(id, v.toByteArray(Charsets.US_ASCII))
        fun cat(vararg parts: ByteArray): ByteArray = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()

        val ebml = el(
            0x1A45DFA3,
            cat(uint(0x4286, 1), uint(0x42F7, 1), uint(0x42F2, 4), uint(0x42F3, 8), str(0x4282, "matroska"), uint(0x4287, 4), uint(0x4285, 2)),
        )
        val lastMs = blocks.last().first
        val info = el(0x1549A966, cat(uint(0x2AD7B1, 1_000_000), float(0x4489, (lastMs + 50).toDouble()), str(0x4D80, "iroiro-test"), str(0x5741, "iroiro-test")))
        val audioEl = el(0xE1, cat(float(0xB5, rate.toDouble()), uint(0x9F, channels.toLong()), if (bitDepth > 0) uint(0x6264, bitDepth.toLong()) else ByteArray(0)))
        val entry = el(
            0xAE,
            cat(uint(0xD7, 1), uint(0x73C5, 1), uint(0x83, 2), str(0x86, codec), codecPrivate?.let { el(0x63A2, it) } ?: ByteArray(0), audioEl),
        )
        val tracks = el(0x1654AE6B, entry)
        val simpleBlocks = blocks.map { (ms, data) ->
            el(0xA3, cat(byteArrayOf(0x81.toByte(), (ms shr 8).toByte(), ms.toByte(), 0x80.toByte()), data))
        }
        val cluster = el(0x1F43B675, cat(uint(0xE7, 0), *simpleBlocks.toTypedArray()))
        return cat(ebml, el(0x18538067, cat(info, tracks, cluster)))
    }

    /** TS 에 실을 흐름 하나 — PID, stream_type(1B H.264 · 03 MPEG-1 소리 · 0F AAC ADTS), PES 의 stream_id, (PTS 90 kHz, 바이트) 단위들. */
    private class TsStream(
        val pid: Int,
        val type: Int,
        val streamId: Int,
        val units: List<Pair<Long, ByteArray>>,
        /** 파일에서 이만큼(90 kHz) 앞서 싣는다 — 방송은 영상을 소리보다 먼저 보낸다(푸는 데 더 오래 걸린다). */
        val leadTicks: Long = 0,
    )

    /**
     * MPEG-TS 를 손으로 쓴다(인코더 없이 — 영상·AAC 의 바이트는 이 기기의 인코더가 준다). 단위마다 PES 하나와 그 PTS, 첫 흐름의 PES
     * 첫 패킷에 PCR, 단위는 PTS 차례로 섞는다. 표(PAT·PMT)는 50 단위마다 다시 싣는다(방송처럼).
     */
    private fun ts(vararg streams: TsStream): ByteArray {
        val pmtPid = 0x1000
        val out = ByteArrayOutputStream()
        val counter = IntArray(0x2000)

        fun crc32(b: ByteArray): Int {
            var crc = -1
            for (x in b) {
                crc = crc xor ((x.toInt() and 0xFF) shl 24)
                repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
            }
            return crc
        }

        fun section(pid: Int, body: ByteArray) {
            val p = ByteArray(188) { 0xFF.toByte() }
            p[0] = 0x47
            p[1] = (0x40 or (pid shr 8)).toByte()
            p[2] = pid.toByte()
            p[3] = (0x10 or (counter[pid]++ and 0x0F)).toByte()
            p[4] = 0 // pointer_field
            System.arraycopy(body, 0, p, 5, body.size)
            ByteBuffer.wrap(p, 5 + body.size, 4).putInt(crc32(body))
            out.write(p)
        }

        // PAT(프로그램 1 → PMT) · PMT(PCR 은 첫 흐름, 흐름마다 stream_type 과 PID).
        val pat = byteArrayOf(0x00, 0xB0.toByte(), 13, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00, 0x00, 0x01, (0xE0 or (pmtPid shr 8)).toByte(), pmtPid.toByte())
        val pcrPid = streams[0].pid
        val pmtBody = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x00, 0x01, 0xC1.toByte(), 0x00, 0x00, (0xE0 or (pcrPid shr 8)).toByte(), pcrPid.toByte(), 0xF0.toByte(), 0x00))
            for (s in streams) write(byteArrayOf(s.type.toByte(), (0xE0 or (s.pid shr 8)).toByte(), s.pid.toByte(), 0xF0.toByte(), 0x00))
        }.toByteArray()
        val pmt = byteArrayOf(0x02, 0xB0.toByte(), (pmtBody.size + 4).toByte()) + pmtBody

        fun pes(pid: Int, payload: ByteArray, pcr: Long?) {
            var at = 0
            var first = true
            while (at < payload.size) {
                val p = ByteArray(188)
                p[0] = 0x47
                p[1] = ((if (first) 0x40 else 0) or (pid shr 8)).toByte()
                p[2] = pid.toByte()
                // 어댑테이션 필드 — PCR 을 실을 첫 패킷은 길이 1 + 깃발 1 + PCR 6, 마지막 패킷은 남는 자리를 채운다.
                val withPcr = first && pcr != null
                var af = if (withPcr) 8 else 0
                val left = payload.size - at
                if (left < 184 - af) af = 184 - left
                p[3] = ((if (af > 0) 0x30 else 0x10) or (counter[pid]++ and 0x0F)).toByte()
                if (af > 0) {
                    p[4] = (af - 1).toByte()
                    if (af >= 2) {
                        p[5] = if (withPcr) 0x10 else 0x00
                        var k = 6
                        if (withPcr) {
                            val base = pcr!!
                            p[6] = (base shr 25).toByte()
                            p[7] = (base shr 17).toByte()
                            p[8] = (base shr 9).toByte()
                            p[9] = (base shr 1).toByte()
                            p[10] = (((base and 1) shl 7) or 0x7E).toByte()
                            p[11] = 0
                            k = 12
                        }
                        for (i in k until 4 + af) p[i] = 0xFF.toByte()
                    }
                }
                val room = 184 - af
                System.arraycopy(payload, at, p, 4 + af, room)
                at += room
                first = false
                out.write(p)
            }
        }

        val units = streams.withIndex()
            .flatMap { (i, s) -> s.units.map { Triple(it.first, i, it.second) } }
            .sortedWith(compareBy({ it.first - streams[it.second].leadTicks }, { it.second }))
        for ((n, u) in units.withIndex()) {
            if (n % 50 == 0) {
                section(0, pat)
                section(pmtPid, pmt)
            }
            val (pts, i, data) = u
            val s = streams[i]
            // PES 길이는 16비트다 — 넘으면 0(영상에만 허락된 '정하지 않음').
            val len = (3 + 5 + data.size).let { if (it <= 0xFFFF) it else 0 }
            val head = byteArrayOf(
                0, 0, 1, s.streamId.toByte(),
                (len shr 8).toByte(), len.toByte(),
                0x80.toByte(), 0x80.toByte(), 5,
                (0x21 or ((pts shr 29) and 0x0E).toInt()).toByte(),
                (pts shr 22).toByte(),
                (0x01 or ((pts shr 14) and 0xFE).toInt()).toByte(),
                (pts shr 7).toByte(),
                (0x01 or ((pts shl 1) and 0xFE).toInt()).toByte(),
            )
            pes(s.pid, head + data, if (i == 0) pts else null)
        }
        return out.toByteArray()
    }

    /**
     * MPEG-1 층 II(48 kHz 모노 128 kbps) 무음 프레임 [count] 개 — 24 ms 간격의 TS 단위. 프레임의 끝 4바이트에 번호를 적는다 — 할당이
     * 모두 0 인 프레임의 나머지는 보조 데이터 자리라 디코더가 소리로 읽지 않는다. 결과에서 번호를 바이트로 읽는다([mp2Numbers]).
     */
    private fun mp2Units(count: Int): List<Pair<Long, ByteArray>> = (0 until count).map { i ->
        val frame = ByteArray(MP2_FRAME)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFD.toByte() // MPEG-1 · 층 II · CRC 없음
        frame[2] = 0x84.toByte() // 128 kbps · 48 kHz
        frame[3] = 0xC0.toByte() // 모노
        ByteBuffer.wrap(frame, MP2_FRAME - 4, 4).putInt(i)
        i * 2160L to frame // 24 ms × 90 kHz
    }

    /** 이 기기의 인코더가 준 H.264 를 TS 단위로 — 접근 단위 구분자(AUD)를 앞에, 키프레임에는 SPS·PPS 를 붙인다. */
    private fun h264Units(seconds: Int, fps: Int): List<Pair<Long, ByteArray>> {
        val (format, samples) = encodeVideo(seconds, fps)
        fun annexB(key: String): ByteArray {
            val b = format.getByteBuffer(key)!!.duplicate()
            val bytes = ByteArray(b.remaining()).also { b.get(it) }
            val hasStart = bytes.size > 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                (bytes[2] == 1.toByte() || (bytes[2] == 0.toByte() && bytes[3] == 1.toByte()))
            return if (hasStart) bytes else byteArrayOf(0, 0, 0, 1) + bytes
        }
        val sps = annexB("csd-0")
        val pps = annexB("csd-1")
        val aud = byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte())
        return samples.map { (data, info) ->
            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
            info.presentationTimeUs * 9 / 100 to (aud + (if (key) sps + pps else ByteArray(0)) + data)
        }
    }

    /**
     * 이 기기의 인코더가 준 AAC(LC, 44.1 kHz 모노)를 ADTS 로 싼 TS 단위와, 싸기 전의 프레임(시각 마이크로초, 바이트) — 결과의 오라클이다.
     */
    private fun aacUnits(seconds: Int): Pair<List<Pair<Long, ByteArray>>, List<Pair<Long, ByteArray>>> {
        val (_, samples) = encodeAudio(seconds)
        val adts = samples.map { (data, info) ->
            val len = 7 + data.size
            val head = byteArrayOf(
                0xFF.toByte(), 0xF1.toByte(), // MPEG-4 · CRC 없음
                0x50, // LC · 44.1 kHz(번호 4)
                (0x40 or (len shr 11)).toByte(), // 채널 1
                (len shr 3).toByte(),
                (((len and 7) shl 5) or 0x1F).toByte(),
                0xFC.toByte(),
            )
            info.presentationTimeUs * 9 / 100 to (head + data)
        }
        return adts to samples.map { (data, info) -> info.presentationTimeUs to data }
    }

    // ---- 표본 짓기 ---------------------------------------------------------------

    /** 16비트 모노 PCM WAV(머리 44바이트). */
    private fun wav(rate: Int, frames: Int): ByteArray {
        val data = ByteArray(frames * 2)
        for (i in 0 until frames) {
            data[i * 2] = (i * 7).toByte()
            data[i * 2 + 1] = (i shr 3).toByte()
        }
        val b = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data.size).put(data)
        return b.array()
    }

    /** VERBATIM FLAC(모노 16비트) — 시험 쪽 JVM 표본(`ClipSamples.flac`)과 같은 모양이다. */
    private fun flac(samples: ShortArray, rate: Int, block: Int): ByteArray {
        val frames = ArrayList<ByteArray>()
        var start = 0
        var index = 0L
        while (start < samples.size) {
            val bs = minOf(block, samples.size - start)
            val h = ByteArrayOutputStream()
            h.write(0xFF)
            h.write(0xF8)
            h.write(7 shl 4)
            h.write(4 shl 1)
            h.write(FlacCut.encodeNumber(index))
            h.write(((bs - 1) shr 8) and 0xFF)
            h.write((bs - 1) and 0xFF)
            val head = h.toByteArray()
            val f = ByteArrayOutputStream()
            f.write(head)
            f.write(Crc.crc8(head, 0, head.size))
            f.write(0x02)
            for (i in 0 until bs) {
                val v = samples[start + i].toInt()
                f.write((v shr 8) and 0xFF)
                f.write(v and 0xFF)
            }
            val body = f.toByteArray()
            val crc = Crc.crc16(0, body, 0, body.size)
            frames += body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
            start += bs
            index++
        }
        val info = ByteArray(34)
        info[0] = (block shr 8).toByte()
        info[1] = block.toByte()
        info[2] = (block shr 8).toByte()
        info[3] = block.toByte()
        val packed = (rate.toLong() shl 44) or (0L shl 41) or (15L shl 36) or samples.size.toLong()
        for (i in 0 until 8) info[10 + i] = (packed shr (56 - 8 * i)).toByte()
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        out.write(0x80)
        out.write(byteArrayOf(0, 0, 34))
        out.write(info)
        frames.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** 플랫폼의 디코더로 소리 트랙 하나를 16비트 PCM 으로 푼다. */
    private fun decodePcm(file: File): ShortArray {
        val ex = MediaExtractor()
        ex.setDataSource(file.absolutePath)
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val out = ArrayList<Short>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val shorts = buf.asShortBuffer()
                while (shorts.hasRemaining()) out += shorts.get()
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        ex.release()
        return out.toShortArray()
    }

    /**
     * 이 기기의 인코더로 MP4 를 짓는다 — H.264(320×240, [fps], 키프레임 1초마다) + AAC(모노 44.1 kHz). 둘을 먼저 다 인코딩해
     * 두고 시각 차례로 섞어 담는다.
     */
    private fun makeMp4(file: File, seconds: Int, fps: Int, rotation: Int) {
        val video = encodeVideo(seconds, fps)
        val audio = encodeAudio(seconds)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(rotation)
        val vt = muxer.addTrack(video.first)
        val at = muxer.addTrack(audio.first)
        muxer.start()
        val all = video.second.map { Triple(vt, it.first, it.second) } + audio.second.map { Triple(at, it.first, it.second) }
        for ((t, data, info) in all.sortedBy { it.third.presentationTimeUs }) {
            muxer.writeSampleData(t, ByteBuffer.wrap(data), info)
        }
        muxer.stop()
        muxer.release()
    }

    private fun encodeVideo(seconds: Int, fps: Int): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>> {
        val w = 320
        val h = 240
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val frames = seconds * fps
        var fed = 0
        val samples = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var done = false
        while (!done) {
            if (fed <= frames) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    if (fed == frames) {
                        codec.queueInputBuffer(i, 0, 0, fed * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val image = codec.getInputImage(i)!!
                        // 프레임마다 밝기를 바꾼다 — 인코더가 프레임을 건너뛰지 않게.
                        for ((p, plane) in image.planes.withIndex()) {
                            val buf = plane.buffer
                            val pw = if (p == 0) w else w / 2
                            val ph = if (p == 0) h else h / 2
                            val value = if (p == 0) ((fed * 37) % 220 + 16).toByte() else 128.toByte()
                            for (y in 0 until ph) for (x in 0 until pw) {
                                buf.put(y * plane.rowStride + x * plane.pixelStride, value)
                            }
                        }
                        codec.queueInputBuffer(i, 0, w * h * 3 / 2, fed * 1_000_000L / fps, 0)
                    }
                    fed++
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                o >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        val data = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.get(data)
                        samples += data to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }
        }
        codec.stop()
        codec.release()
        return outFormat!! to samples
    }

    private fun encodeAudio(seconds: Int): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>> {
        val rate = 44100
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val total = rate * seconds
        var fed = 0
        var eos = false
        val samples = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var done = false
        while (!done) {
            if (!eos) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!.order(ByteOrder.LITTLE_ENDIAN)
                    val n = minOf(1024, total - fed)
                    if (n <= 0) {
                        codec.queueInputBuffer(i, 0, 0, fed * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eos = true
                    } else {
                        buf.clear()
                        for (k in 0 until n) buf.putShort((sin((fed + k) * 2 * Math.PI * 440 / rate) * 8000).toInt().toShort())
                        codec.queueInputBuffer(i, 0, n * 2, fed * 1_000_000L / rate, 0)
                        fed += n
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                o >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        val data = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.get(data)
                        samples += data to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }
        }
        codec.stop()
        codec.release()
        return outFormat!! to samples
    }

    private companion object {
        /** MPEG-1 층 II 프레임 하나 — 48 kHz · 128 kbps 는 덧바이트 없이 384바이트다(144 × 128000 / 48000). */
        const val MP2_FRAME = 384
    }
}
