package io.github.donggi.iroiroviewer.io.clip

import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * 영상에서 **소리 트랙 하나만** 새 파일로 뽑는다(사용자 요청 '오디오만 추출하여 별도로 저장'). 다시 인코딩하지 않는다 —
 * 소리의 바이트를 그대로 옮기고 그릇만 바꾼다. 무엇에 담는가는 [RemuxPlan.AudioOut] 이 정한다.
 *
 * | 소리 | 결과 | 누가 쓰나 |
 * |---|---|---|
 * | AAC | `.m4a` | 플랫폼 `MediaMuxer`(MP4) — [MediaRemux] |
 * | AMR-NB·AMR-WB | `.3ga` | `MediaMuxer`(3GPP) |
 * | Opus | `.opus` | `MediaMuxer`(Ogg) |
 * | Vorbis | `.weba` | `MediaMuxer`(WebM) — 플랫폼의 Ogg 쓰기는 Opus 만 받는다 |
 * | MP3 | `.mp3` | 우리 — 프레임을 잇고 앞에 Xing·Info 머리 프레임(길이·목차)을 둔다 |
 * | MP2 | `.mp2` | 우리 — 프레임을 잇는다 |
 * | AC-3·E-AC-3 | `.ac3`·`.eac3` | 우리 — 동기 프레임을 잇는다(프레임마다 머리를 품어 이어 붙이면 그대로 재생되는 파일이다) |
 * | FLAC | `.flac` | 우리 — 그릇의 머리(csd-0)에서 `fLaC`·STREAMINFO 를 세우고 프레임 번호를 0 부터 다시 매긴다(CRC 도) |
 * | PCM | `.wav` | 우리 — WAV 머리를 앞에 둔다 |
 *
 * 그래서 영상째로는 담을 수 없는 MKV 의 FLAC·PCM·AC-3 소리도 뽑힌다. 못 뽑는 것 — DTS·TrueHD·ALAC·빅 엔디언 PCM·머리(csd-0) 없는
 * FLAC, 잠긴(DRM) 미디어, 플랫폼이 읽지 못하는 그릇(AVI·FLV).
 *
 * 소리가 여럿이면 **뽑을 수 있는 첫 트랙** 하나다(나머지 수는 결과가 말한다). 소리 프레임은 모두 키프레임이라 고른 시각에서 프레임
 * 하나(대개 20~100 ms) 안쪽으로 맞는다.
 */
internal object AudioExtract {

    /** 열어 본 결과. */
    sealed interface Opened {
        class Ok(val prepared: Prepared) : Opened

        /** 소리 트랙이 없다(소리 없는 화면 녹화 등). */
        data object NoAudio : Opened

        /** 소리는 있는데 담을 수 없다(코덱·잠김·플랫폼이 못 읽는 그릇). */
        data object Unsupported : Opened
    }

    /** 무엇을 뽑을지 정한 것. 추출기를 쥐고 있으므로 반드시 닫는다. */
    class Prepared internal constructor(
        val choice: RemuxPlan.AudioChoice,
        internal val extractor: MediaExtractor,
        internal val formats: List<MediaFormat>,
        /** 뽑을 트랙의 길이(모르면 가장 긴 트랙의 길이, 그것도 모르면 0). */
        internal val durationUs: Long,
        /** 원본 — 되감기를 믿을 수 없는 그릇인지 가른다([MediaRemux.seeksUnreliably]). */
        internal val src: File,
    ) : AutoCloseable {
        /** 영상 트랙이 있는가 — 없으면 소리를 뽑는 것이 곧 그 파일의 구간 저장이다(`MediaClipper`). */
        val hasVideo: Boolean
            get() = formats.any { it.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }

        override fun close() {
            extractor.release()
        }
    }

    fun prepare(src: File): Opened {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(src.absolutePath)
            // 잠긴 미디어(DRM)는 다루지 않는다 — 풀지 않으므로 옮겨 담아도 열리지 않는다.
            if (extractor.drmInitData != null) {
                extractor.release()
                return Opened.Unsupported
            }
            val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val mimes = formats.map { writableMime(it) }
            if (mimes.none { it.startsWith("audio/") }) {
                extractor.release()
                return Opened.NoAudio
            }
            val choice = RemuxPlan.chooseAudio(mimes)
            if (choice == null) {
                extractor.release()
                return Opened.Unsupported
            }
            val own = formats[choice.track].longOrZero(MediaFormat.KEY_DURATION)
            val duration = if (own > 0) own else formats.maxOfOrNull { it.longOrZero(MediaFormat.KEY_DURATION) } ?: 0L
            return Opened.Ok(Prepared(choice, extractor, formats, duration, src))
        } catch (e: IOException) {
            extractor.release()
            return Opened.Unsupported
        } catch (e: RuntimeException) {
            // 플랫폼 추출기는 깨진 그릇에 IllegalArgumentException 등을 던진다.
            extractor.release()
            return Opened.Unsupported
        }
    }

    /**
     * 트랙의 MIME — 우리가 쓸 수 없는 갈래(머리 없는 FLAC, 우리가 모르는 PCM)는 '쓸 수 없는 소리' 로 바꿔 고르기에서 빠지게 한다
     * (소리 트랙으로는 센다 — 담지 않은 수에 든다).
     */
    private fun writableMime(f: MediaFormat): String = when (val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()) {
        "audio/flac" -> if (FlacHead.parse(csd0(f)) != null) mime else UNWRITABLE_AUDIO
        "audio/raw" -> if (PcmLayout.of(f) != null) mime else UNWRITABLE_AUDIO
        else -> mime
    }

    /**
     * [prepared] 의 소리 트랙에서 [startUs, endUs) 를 [out] 에 쓴다. **던질 수 있다**(입출력·플랫폼 오류) — 부르는 쪽이 임시본을 버린다.
     *
     * @param onSample 옮긴 표본 하나 — 바이트와 그 표본의 시각. 진행률과 남은 공간 확인에 쓴다.
     */
    suspend fun write(
        prepared: Prepared,
        startUs: Long,
        endUs: Long,
        out: RandomAccessFile,
        onSample: (bytes: Long, atUs: Long) -> Unit,
    ): MediaRemux.Outcome {
        val choice = prepared.choice
        val container = choice.out.container
        if (container != null) {
            // 플랫폼이 담는 그릇 — 다시 담기와 같은 길이다(소리 트랙 하나만 담는 계획으로). 추출기의 주인은 [prepared] 다 — 이 계획은
            // 닫지 않는다.
            val plan = RemuxPlan.Choice(container, choice.out.extension, null, listOf(choice.track), choice.dropped)
            val remux = MediaRemux.Prepared(plan, prepared.extractor, prepared.formats, prepared.durationUs, prepared.src)
            return MediaRemux.write(remux, startUs, endUs, out.fd, onSample)
        }
        val format = prepared.formats[choice.track]
        val sink: RawSink = when (choice.out) {
            RemuxPlan.AudioOut.MP3 -> Mp3Sink()
            RemuxPlan.AudioOut.FLAC -> FlacSink(FlacHead.parse(csd0(format)) ?: return unsupported())
            RemuxPlan.AudioOut.WAV -> WavSink(PcmLayout.of(format) ?: return unsupported())
            else -> PlainSink
        }
        return try {
            writeRaw(prepared, sink, startUs, endUs, PatchableWriter(out), onSample)
        } catch (e: Unwritable) {
            unsupported()
        }
    }

    private fun unsupported() = MediaRemux.Outcome(MediaRemux.Failure.UNSUPPORTED)

    /** 우리가 쓰는 그릇 — 표본을 하나씩 받아 [sink] 가 쓴다. */
    private suspend fun writeRaw(
        p: Prepared,
        sink: RawSink,
        startUs: Long,
        endUs: Long,
        w: PatchableWriter,
        onSample: (bytes: Long, atUs: Long) -> Unit,
    ): MediaRemux.Outcome {
        val ex = p.extractor
        val track = p.choice.track
        ex.selectTrack(track)
        // 소리 프레임은 모두 키프레임이다 — 고른 시각을 품은 프레임부터(MKV 는 그냥 되감으면 뒤의 프레임으로 간다 — [MediaRemux.seekAtOrBefore]).
        // **되감기를 믿지 않는다** — TS 의 소리 트랙만·MPEG-PS 는 되감지 못해 처음부터 온다(그 까닭은 `MediaRemux.write`). 고른 시각
        // 이하의 마지막 표본을 들고 가다가([held]) 그 시각을 넘는 표본이 오면 그것부터 쓴다. 그런 그릇은 아예 되감지 않는다
        // ([MediaRemux.seeksUnreliably] — TS 는 되감기가 들면 시간축을 다시 세운다).
        if (!MediaRemux.seeksUnreliably(ex, p.src)) MediaRemux.seekAtOrBefore(ex, startUs)
        var buffer = ByteBuffer.allocate(INITIAL_BUFFER)
        var held = ByteBuffer.allocate(INITIAL_BUFFER)
        var heldSize = -1
        var heldUs = -1L
        var firstUs = -1L
        var lastUs = 0L
        var count = 0
        sink.begin(w)

        fun write(b: ByteBuffer, n: Int, t: Long) {
            if (firstUs < 0) firstUs = t
            sink.sample(w, b.array(), b.arrayOffset(), n)
            lastUs = t
            onSample(n.toLong(), t)
        }

        while (true) {
            if (count++ % 64 == 0) currentCoroutineContext().ensureActive()
            if (ex.sampleTrackIndex != track) break
            val t = ex.sampleTime
            if (t < 0 || t >= endUs) break
            if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) return unsupported()
            val size = ex.sampleSize.toInt()
            if (size < 0) break
            if (t <= startUs) {
                // 아직 고른 시각 이하 — 들고 간다(앞서 들던 것은 버린다).
                if (size > held.capacity()) held = ByteBuffer.allocate(size + size / 4)
                held.clear()
                heldSize = ex.readSampleData(held, 0)
                if (heldSize < 0) break
                heldUs = t
                ex.advance()
                continue
            }
            if (heldSize >= 0) {
                write(held, heldSize, heldUs)
                heldSize = -1
            }
            if (size > buffer.capacity()) buffer = ByteBuffer.allocate(size + size / 4)
            buffer.clear()
            val n = ex.readSampleData(buffer, 0)
            if (n < 0) break
            write(buffer, n, t)
            ex.advance()
        }
        // 고른 시각이 마지막 프레임 안이었다 — 들고 간 것이 담을 전부다.
        if (heldSize >= 0 && heldUs < endUs) write(held, heldSize, heldUs)
        if (firstUs < 0) return MediaRemux.Outcome(MediaRemux.Failure.EMPTY)
        sink.finish(w)
        w.flush()
        // 실제 끝 — 고른 끝과 트랙 길이 가운데 작은 것(마지막 프레임의 길이는 모른다).
        val end = if (p.durationUs > 0) minOf(endUs, p.durationUs) else maxOf(lastUs, firstUs)
        return MediaRemux.Outcome(null, firstUs, end, p.choice.dropped)
    }

    private fun csd0(f: MediaFormat): ByteArray? = f.getByteBuffer("csd-0")?.let { b ->
        val d = b.duplicate()
        d.rewind()
        ByteArray(d.remaining()).also { d.get(it) }
    }

    private fun MediaFormat.longOrZero(key: String): Long = if (containsKey(key)) getLong(key) else 0L

    /** 이 표본을 이 그릇에 쓸 수 없다(흐름이 우리가 아는 모양이 아니다). 쓰다 만 것은 부르는 쪽이 버린다. */
    internal class Unwritable : Exception()

    /** 표본을 받아 그릇을 쓰는 것. [begin] → [sample]… → [finish]. 안드로이드를 모른다(JVM 시험이 표본을 지어 먹인다). */
    internal interface RawSink {
        fun begin(w: PatchableWriter) {}

        fun sample(w: PatchableWriter, b: ByteArray, off: Int, len: Int)

        fun finish(w: PatchableWriter) {}
    }

    /** 이어 붙이면 되는 흐름(MP2·AC-3·E-AC-3) — 프레임이 머리를 품고 있다. */
    internal object PlainSink : RawSink {
        override fun sample(w: PatchableWriter, b: ByteArray, off: Int, len: Int) {
            w.write(b, off, len)
        }
    }

    /**
     * MP3 — 프레임을 잇고 맨 앞에 Xing·Info 머리 프레임을 둔다. 가변 비트율 MP3 에 그것이 없으면 재생기가 첫 프레임의 비트율로 길이를
     * 어림해(무음으로 시작하는 곡은 첫 프레임이 작다) 길이와 탐색이 크게 틀린다. 첫 표본에서 같은 판·표본율로 자리를 지어 두고, 다 쓴
     * 뒤 프레임 수·바이트 수·목차를 채운다. 표본이 프레임 경계로 나뉘지 않으면(우리가 모르는 흐름) 태그 없이 무음 프레임으로 둔다.
     *
     * 목차에 쓸 프레임 자리는 성기게 든다([SparseOffsets]) — 프레임마다 Long 을 들면 긴 소리에서 수십 MB 였다.
     */
    internal class Mp3Sink : RawSink {
        private var info: ByteArray? = null
        private var tagAt = 0
        private var started = false
        private var clean = true
        private val offsets = SparseOffsets()
        private var bitrate = -1
        private var vbr = false

        override fun sample(w: PatchableWriter, b: ByteArray, off: Int, len: Int) {
            if (!started) {
                started = true
                XingInfo.synthesize(b.copyOfRange(off, off + minOf(4, len)))?.let { (frame, at) ->
                    info = frame
                    tagAt = at
                    w.write(frame)
                }
            }
            if (info != null && clean) {
                // 표본 안의 프레임을 센다(대개 하나). 경계가 맞지 않으면 세기를 그만둔다.
                var p = 0
                while (p < len) {
                    val h = Mp3Cut.parseHeader(b, off + p, off + len)
                    if (h == null || p + h.frameLength > len || offsets.count >= MAX_MP3_FRAMES) {
                        clean = false
                        break
                    }
                    offsets.add(w.position + p)
                    if (bitrate < 0) bitrate = h.bitrateKbps else if (h.bitrateKbps != bitrate) vbr = true
                    p += h.frameLength
                }
            }
            w.write(b, off, len)
        }

        override fun finish(w: PatchableWriter) {
            val frame = info ?: return
            if (!clean || offsets.count == 0L) return
            val dataSize = w.position
            val frames = offsets.count.toInt()
            val toc = XingInfo.toc(frames, dataSize) { offsets.offsetOf(it.toLong()) }
            XingInfo.fill(frame, tagAt, if (vbr) "Xing" else "Info", frames, dataSize, toc)
            w.patch(0, frame)
        }
    }

    /**
     * FLAC — `fLaC` + 그릇의 머리(STREAMINFO·주석·그림) + 프레임. 프레임 번호를 0 부터 다시 매기고 CRC-8·CRC-16 을 다시 센다(구간
     * 저장의 [FlacCut] 과 같은 셈). 표본 하나에 프레임이 여럿이면 [FlacCut.frameEnd] 의 세 겹 확인으로 나눠 쓴다(이어 붙인 두 프레임은
     * CRC-16 하나로는 가를 수 없다). 경계를 찾지 못하면 멈춘다 — 짐작으로 이어 붙인 FLAC 은 깨진 파일이다.
     */
    internal class FlacSink(private val head: FlacHead) : RawSink {
        private var frames = 0L
        private var samples = 0L
        private var minSize = Long.MAX_VALUE
        private var maxSize = 0L
        private var first: FlacCut.FrameHeader? = null

        override fun begin(w: PatchableWriter) {
            // 블록마다 곧바로 쓴다 — 머리 한 벌로 묶어(두 배까지 자라는 버퍼) 다시 복사하면 큰 그림에서 메모리가 몇 벌이 된다.
            w.write("fLaC".toByteArray(Charsets.US_ASCII))
            val blocks = listOf(FlacCut.TYPE_STREAMINFO to head.streamInfo) + head.kept
            blocks.forEachIndexed { i, (type, body) ->
                w.write(FlacCut.blockHeader(type, body.size, last = i == blocks.lastIndex))
                w.write(body)
            }
        }

        override fun sample(w: PatchableWriter, b: ByteArray, off: Int, len: Int) {
            val bytes = if (off == 0) b else b.copyOfRange(off, off + len)
            val src = ArraySource(bytes, len)
            var start = 0L
            while (start < len) {
                val h = FlacCut.parseHeader(src, start) ?: throw Unwritable()
                val stream = first ?: h.also { first = it }
                if (!h.sameStream(stream)) throw Unwritable()
                val end = FlacCut.frameEnd(src, start, h, stream, len.toLong()) ?: throw Unwritable()
                writeFrame(w, bytes, start.toInt(), end.toInt(), h)
                start = end
            }
        }

        private fun writeFrame(w: PatchableWriter, b: ByteArray, from: Int, to: Int, h: FlacCut.FrameHeader) {
            // 번호를 차례대로 — 고정 블록이면 프레임 차례, 가변이면 첫 표본 차례.
            val newHeader = h.rewrite(if (h.variable) samples else frames)
            val bodyFrom = from + h.length
            val bodyLen = to - 2 - bodyFrom
            var crc = Crc.crc16(0, newHeader, 0, newHeader.size)
            crc = Crc.crc16(crc, b, bodyFrom, bodyLen)
            w.write(newHeader)
            w.write(b, bodyFrom, bodyLen)
            w.write((crc shr 8) and 0xFF)
            w.write(crc and 0xFF)
            val size = (newHeader.size + bodyLen + 2).toLong()
            minSize = minOf(minSize, size)
            maxSize = maxOf(maxSize, size)
            frames++
            samples += h.blockSize
        }

        override fun finish(w: PatchableWriter) {
            // STREAMINFO 의 몸은 `fLaC`(4) + 블록 머리(4) 뒤다.
            w.patch(8, FlacCut.streamInfoFor(head.streamInfo, minSize, maxSize, samples))
        }
    }

    /** WAV — 머리(44바이트)를 두고 PCM 을 잇는다. 크기는 끝에서 고친다. 4 GiB 를 넘으면 멈춘다(RF64 는 쓰지 않는다). */
    internal class WavSink(private val pcm: PcmLayout) : RawSink {
        private var data = 0L

        override fun begin(w: PatchableWriter) {
            w.write(header(0))
        }

        override fun sample(w: PatchableWriter, b: ByteArray, off: Int, len: Int) {
            if (HEADER + data + len > MAX_RIFF) throw Unwritable()
            w.write(b, off, len)
            data += len
        }

        override fun finish(w: PatchableWriter) {
            // 데이터 청크가 홀수 길이면 채움 바이트 하나 — 뒤에 무엇이 오든 짝수 자리여야 한다(RIFF).
            if (data and 1L != 0L) w.write(0)
            w.patch(0, header(data))
        }

        /** RIFF 머리 — `fmt `(16바이트, 정수 PCM 이면 1·실수면 3)와 `data` 머리. */
        fun header(dataBytes: Long): ByteArray {
            val h = ByteArray(HEADER)
            "RIFF".toByteArray(Charsets.US_ASCII).copyInto(h, 0)
            h.putU32le(4, 36 + dataBytes + (dataBytes and 1))
            "WAVE".toByteArray(Charsets.US_ASCII).copyInto(h, 8)
            "fmt ".toByteArray(Charsets.US_ASCII).copyInto(h, 12)
            h.putU32le(16, 16)
            val blockAlign = pcm.channels * pcm.bits / 8
            h[20] = (if (pcm.float) 3 else 1).toByte()
            h[22] = pcm.channels.toByte()
            h[23] = (pcm.channels shr 8).toByte()
            h.putU32le(24, pcm.rate.toLong())
            h.putU32le(28, pcm.rate.toLong() * blockAlign)
            h[32] = blockAlign.toByte()
            h[33] = (blockAlign shr 8).toByte()
            h[34] = pcm.bits.toByte()
            "data".toByteArray(Charsets.US_ASCII).copyInto(h, 36)
            h.putU32le(40, dataBytes)
            return h
        }

        companion object {
            const val HEADER = 44
            const val MAX_RIFF = 0xFFFFFFFFL - 8
        }
    }

    /**
     * 그릇이 준 FLAC 머리(csd-0) — STREAMINFO 와 옮길 블록(주석·그림). 그릇마다 모양이 다르다: `fLaC` 로 시작하는 것(MKV 의
     * CodecPrivate), 메타데이터 블록부터인 것, STREAMINFO 34바이트만인 것.
     */
    class FlacHead(val streamInfo: ByteArray, val kept: List<Pair<Int, ByteArray>>) {
        companion object {
            fun parse(csd: ByteArray?): FlacHead? {
                csd ?: return null
                if (csd.size == 34) return FlacHead(csd.copyOf(), emptyList())
                var pos = if (csd.size >= 4 && String(csd, 0, 4, Charsets.ISO_8859_1) == "fLaC") 4 else 0
                var info: ByteArray? = null
                val kept = ArrayList<Pair<Int, ByteArray>>()
                var keptBytes = 0L
                while (pos + 4 <= csd.size) {
                    val h = csd[pos].toInt() and 0xFF
                    val type = h and 0x7F
                    val len = ((csd[pos + 1].toInt() and 0xFF) shl 16) or ((csd[pos + 2].toInt() and 0xFF) shl 8) or
                        (csd[pos + 3].toInt() and 0xFF)
                    val body = pos + 4
                    if (type == 127 || body + len > csd.size) return null
                    when (type) {
                        FlacCut.TYPE_STREAMINFO -> {
                            if (len != 34 || info != null) return null
                            info = csd.copyOfRange(body, body + len)
                        }
                        FlacCut.TYPE_VORBIS_COMMENT, FlacCut.TYPE_PICTURE -> if (keptBytes + len <= FlacCut.MAX_KEPT_BYTES) {
                            kept += type to csd.copyOfRange(body, body + len)
                            keptBytes += len
                        }
                    }
                    pos = body + len
                    if (h and 0x80 != 0) break
                }
                return info?.let { FlacHead(it, kept) }
            }
        }
    }

    /** PCM 의 모양 — 표본율·채널·표본 크기·실수인가. 우리가 쓸 수 없는 모양(빅 엔디언·모르는 인코딩)이면 [of] 가 null. */
    class PcmLayout(val rate: Int, val channels: Int, val bits: Int, val float: Boolean) {
        companion object {
            fun of(f: MediaFormat): PcmLayout? {
                if (!f.containsKey(MediaFormat.KEY_SAMPLE_RATE) || !f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) return null
                val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (rate <= 0 || channels !in 1..32) return null
                // 빅 엔디언 PCM(MOV 의 `twos`, MKV 의 A_PCM/INT/BIG)은 쓰지 않는다 — 추출기가 바이트를 뒤집어 주는지 모른다.
                if (f.containsKey(KEY_BIG_ENDIAN) && f.getInteger(KEY_BIG_ENDIAN) != 0) return null
                if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    return when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                        AudioFormat.ENCODING_PCM_8BIT -> PcmLayout(rate, channels, 8, false)
                        AudioFormat.ENCODING_PCM_16BIT -> PcmLayout(rate, channels, 16, false)
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> PcmLayout(rate, channels, 24, false)
                        AudioFormat.ENCODING_PCM_32BIT -> PcmLayout(rate, channels, 32, false)
                        AudioFormat.ENCODING_PCM_FLOAT -> PcmLayout(rate, channels, 32, true)
                        else -> null
                    }
                }
                // 인코딩이 적혀 있지 않으면 표본 크기 열쇠를, 그것도 없으면 플랫폼의 기본값(16비트)을 따른다.
                val bits = if (f.containsKey(KEY_BITS)) f.getInteger(KEY_BITS) else 16
                return if (bits in setOf(8, 16, 24, 32)) PcmLayout(rate, channels, bits, false) else null
            }

            private const val KEY_BIG_ENDIAN = "pcm-big-endian"
            private const val KEY_BITS = "bits-per-sample"
        }
    }

    /** 고르기에서 빼되 소리 트랙으로는 세는 MIME. */
    private const val UNWRITABLE_AUDIO = "audio/x-iroiro-unwritable"

    private const val INITIAL_BUFFER = 256 * 1024

    /**
     * MP3 목차를 짓는 프레임 수의 상한 — Xing 의 프레임 수 칸(32비트)과 그 셈의 Int 에 드는 만큼. 넘으면 목차 없이 둔다. 자리를 성기게
     * 든 뒤로는 메모리의 상한이 아니다(예전에는 약 60시간 — 프레임마다 8바이트를 들었다).
     */
    private const val MAX_MP3_FRAMES = Int.MAX_VALUE.toLong()
}
