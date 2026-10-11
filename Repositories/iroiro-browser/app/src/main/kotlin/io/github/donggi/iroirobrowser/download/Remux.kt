package io.github.donggi.iroirobrowser.download

import android.media.MediaCodec
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * **그릇만 바꿔 담는다**(다시 인코딩하지 않는다 — 화질이 그대로고 빠르다). 따로 받은 영상과 소리(HLS 의 따로 된 소리·DASH)를 한 파일로 합치고,
 * 가로챈 바이트(MSE)·녹화(MediaRecorder 의 조각난 그릇)를 길이와 찾기 표가 있는 MP4·WebM 으로 만든다.
 *
 * 읽기는 **media3 의 추출기**(사용자 승인, 2026-10-05 — `media3-extractor` 1.11.1: fMP4·MP4·TS·ADTS·MP3·WebM), 쓰기는 플랫폼 `MediaMuxer`.
 * 플랫폼의 `MediaExtractor` 를 쓰지 않는 까닭(viewer 의 실측): fMP4 의 `tfdt` 를 읽지 않아 조각 사이의 틈이 사라지고, TS 의 첫 PTS 보다 이른
 * PTS 를 0 으로 누르며, TS 의 AAC 를 ADTS 머리째 넘겨 `MediaMuxer` 가 깨진 파일을 만든다.
 *
 * 줄기([Source])마다 덩이([Run])를 차례로 읽어 영상 또는 소리 트랙 하나를 뽑고, 줄기들을 **시각 차례로 엇갈려** 담는다(한 줄기를 다 담고
 * 다음 줄기를 담으면 `MediaMuxer` 가 메모리에 쌓는다). 시작은 맞춘다: 줄기들의 첫 시각이 [ALIGN_LIMIT_US] 안에서 갈리면 같은 시계로 보고
 * 가장 이른 것을 0 으로(TS 의 PTS·fMP4 의 tfdt·묶음 AAC 의 ID3 시각), 더 갈리면 시계가 다르다고 보고 줄기마다 제 첫 시각을 0 으로.
 *
 * 담는 그릇은 코덱이 정한다 — 모두 WebM 의 것(VP8·VP9·Opus·Vorbis)이면 WebM, 모두 MP4 의 것(H.264·H.265·AAC, 14 이상은 AV1)이면 MP4,
 * 그 밖은 [Unsupported](부르는 쪽이 따로 된 파일로 남긴다). DRM 으로 잠긴 것은 [Encrypted].
 */
@OptIn(UnstableApi::class)
class Remux(sources: List<Source>, private val cancelled: () -> Boolean = { false }) : Closeable {

    enum class Container { TS, FMP4, MP4, ADTS, MP3, WEBM }

    /** 읽을 덩이 — [file] 의 [start] 부터 [length] 바이트. [offsetUs] 를 표본 시각에 더한다(묶음 AAC 의 ID3 시각·MSE 의 timestampOffset). */
    class Run(val file: File, val container: Container, val start: Long = 0, val length: Long = file.length() - start, val offsetUs: Long = 0)

    /** 한 줄기 — 차례로 읽을 덩이들에서 [type](`C.TRACK_TYPE_VIDEO`·`AUDIO`)의 트랙 하나를 뽑는다. [skip] 은 담지 않을 표본 시각(가로채기의 겹침). */
    class Source(val runs: List<Run>, val type: Int, val skip: (runIndex: Int, timeUs: Long) -> Boolean = { _, _ -> false })

    /** 무엇으로 담을지 — 이것으로 '내려받기' 의 이름·형식을 정한 뒤 [writeTo]. */
    class Plan(val muxerFormat: Int, val extension: String, val mimeType: String, val video: Format?, val audio: Format?)

    open class Failure(message: String) : IOException(message)
    class Encrypted : Failure("DRM 으로 잠긴 미디어라 담지 않습니다")
    class Unsupported(message: String) : Failure(message)
    class Cancelled : IOException("cancelled")

    private class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)

    private val readers = sources.map { Reader(it) }
    private var live: List<Reader> = emptyList()
    private var plan: Plan? = null

    /** 줄기마다 첫 표본 시각(형식을 읽은 것만 — 트랙이 없으면 null). */
    fun firstTimes(): List<Long?> {
        readers.forEach { it.prime() }
        return readers.map { r -> r.firstTimeUs.takeIf { r.format != null && it != Long.MAX_VALUE } }
    }

    /** 형식을 읽고 담을 그릇을 정한다. 읽을 트랙이 없는 줄기는 뺀다. */
    fun prepare(): Plan {
        plan?.let { return it }
        readers.forEach { it.prime() }
        if (readers.any { it.encrypted }) throw Encrypted()
        live = readers.filter { it.format != null && it.queue.isNotEmpty() }
        if (live.isEmpty()) throw Unsupported("담을 영상·소리를 찾지 못했습니다")
        val mimes = live.map { it.format?.sampleMimeType.orEmpty() }
        val video = live.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.format
        val audio = live.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.format
        val p = when {
            mimes.all { it in WEBM_MIMES } ->
                Plan(MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, "webm", if (video != null) "video/webm" else "audio/webm", video, audio)
            mimes.all { it in mp4Mimes() } ->
                Plan(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, if (video != null) "mp4" else "m4a", if (video != null) "video/mp4" else "audio/mp4", video, audio)
            else -> throw Unsupported("이 코덱 조합(${mimes.joinToString()})은 한 파일로 담지 못합니다")
        }
        plan = p
        return p
    }

    /** 담는다 — 담은 길이(마이크로초)를 돌려준다. [onSample] 은 담은 표본 수(진행 표시). */
    fun writeTo(fd: FileDescriptor, onSample: (Int) -> Unit = {}): Long {
        val p = prepare()
        val muxer = try {
            MediaMuxer(fd, p.muxerFormat)
        } catch (e: IOException) {
            throw Failure("파일을 쓰지 못했습니다")
        }
        try {
            try {
                for (r in live) r.muxTrack = muxer.addTrack(MediaFormatUtil.createMediaFormatFromFormat(r.format!!))
                p.video?.rotationDegrees?.takeIf { it != 0 }?.let { muxer.setOrientationHint(it) }
                muxer.start()
            } catch (e: RuntimeException) {
                // IllegalArgumentException(형식)·IllegalStateException(그릇이 그 코덱을 모른다)
                throw Unsupported("이 형식(${live.joinToString { it.format?.sampleMimeType.orEmpty() }})을 담지 못했습니다")
            }
            val firsts = live.map { it.firstTimeUs }
            val earliest = firsts.min()
            val sameClock = firsts.max() - earliest <= ALIGN_LIMIT_US
            for (r in live) r.baseUs = if (sameClock) earliest else r.firstTimeUs
            val info = MediaCodec.BufferInfo()
            var written = 0
            var lastUs = 0L
            while (true) {
                if (cancelled()) throw Cancelled()
                var pick: Reader? = null
                var pickTime = Long.MAX_VALUE
                for (r in live) {
                    if (!r.fill()) continue
                    val t = r.queue.first().timeUs - r.baseUs
                    if (t < pickTime) {
                        pick = r
                        pickTime = t
                    }
                }
                val reader = pick ?: break
                val s = reader.queue.removeFirst()
                val t = (s.timeUs - reader.baseUs).coerceAtLeast(0)
                info.set(0, s.data.size, t, if (s.flags and C.BUFFER_FLAG_KEY_FRAME != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(reader.muxTrack, ByteBuffer.wrap(s.data), info)
                lastUs = maxOf(lastUs, t)
                if (++written % 200 == 0) onSample(written)
                if (reader.encrypted) throw Encrypted()
            }
            if (written == 0) throw Unsupported("담을 표본이 없습니다")
            try {
                muxer.stop()
            } catch (e: RuntimeException) {
                throw Failure("파일을 마무리하지 못했습니다")
            }
            onSample(written)
            return lastUs
        } finally {
            runCatching { muxer.release() }
        }
    }

    override fun close() {
        readers.forEach { it.close() }
    }

    // ── 한 줄기를 읽는 것 ─────────────────────────────────────────────────────────────────────────────────────

    private inner class Reader(val source: Source) : Closeable {
        val type = source.type
        var format: Format? = null
        val queue = ArrayDeque<Sample>()
        var firstTimeUs = Long.MAX_VALUE
        var baseUs = 0L
        var muxTrack = -1
        var encrypted = false

        /** 첫 덩이가 트랙을 다 알렸는데 원하는 꼴이 없다 — 끝까지 읽지 않는다(영상 TS 에서 소리를 찾을 때 등). */
        var noTrack = false

        private var runIndex = -1
        private var range: FileRange? = null
        private var extractor: Extractor? = null
        private var input: DefaultExtractorInput? = null
        private var offsetUs = 0L
        private val holder = PositionHolder()
        private var ended = false

        private var primed = false

        /** 처음 몇 표본을 읽어 형식과 첫 시각을 안다(B 프레임이 있으면 첫 표본이 가장 이른 것이 아닐 수 있다 — 몇 개를 본다). */
        fun prime() {
            if (primed) return
            primed = true
            while (queue.size < PRIME_SAMPLES && !encrypted && !noTrack && readSome()) Unit
            firstTimeUs = queue.minOfOrNull { it.timeUs } ?: Long.MAX_VALUE
        }

        /** 큐에 표본이 있게 한다 — 없고 더 읽을 것도 없으면 false. */
        fun fill(): Boolean {
            while (queue.isEmpty()) {
                if (encrypted || noTrack || !readSome()) return false
            }
            return true
        }

        /** 조금 더 읽는다 — 더 읽을 것이 없으면 false. */
        private fun readSome(): Boolean {
            if (ended) return false
            if (cancelled()) throw Cancelled()
            val ex = extractor ?: return openNext()
            val result = try {
                ex.read(input!!, holder)
            } catch (e: EOFException) {
                // 끝이 잘린 덩이(가로채기를 멈춘 자리·끊긴 조각) — 거기까지만 쓴다.
                Extractor.RESULT_END_OF_INPUT
            } catch (e: androidx.media3.common.ParserException) {
                throw Unsupported("그릇을 읽지 못했습니다(${source.runs[runIndex].container})")
            }
            when (result) {
                Extractor.RESULT_END_OF_INPUT -> {
                    closeRun()
                    return openNext()
                }
                Extractor.RESULT_SEEK -> input = DefaultExtractorInput(range!!.readerAt(holder.position), holder.position, range!!.length)
            }
            return true
        }

        private fun openNext(): Boolean {
            runIndex++
            val run = source.runs.getOrNull(runIndex)
            if (run == null) {
                ended = true
                return false
            }
            val r = FileRange(run.file, run.start, run.length)
            range = r
            offsetUs = run.offsetUs
            val ex = newExtractor(run.container)
            ex.init(RunOutput(runIndex))
            extractor = ex
            input = DefaultExtractorInput(r.readerAt(0), 0, r.length)
            return true
        }

        private fun closeRun() {
            runCatching { extractor?.release() }
            extractor = null
            input = null
            range?.close()
            range = null
        }

        override fun close() {
            closeRun()
            ended = true
        }

        /** 덩이 하나의 추출기가 알리는 곳 — 원하는 꼴의 첫 트랙만 받고 나머지는 버린다. */
        private inner class RunOutput(private val index: Int) : ExtractorOutput {
            private var chosen = -1

            override fun track(id: Int, trackType: Int): TrackOutput {
                if (chosen == -1 && trackType == type) chosen = id
                return if (id == chosen) Sink(index) else DiscardingTrackOutput()
            }

            override fun endTracks() {
                if (chosen == -1 && index == 0) noTrack = true
            }

            override fun seekMap(seekMap: SeekMap) = Unit
        }

        /** 표본을 모은다 — 추출기는 한 표본을 여러 번에 나눠 넘기고 끝에 시각을 알린다(그 뒤의 [offset] 바이트는 다음 표본의 것). */
        private inner class Sink(private val index: Int) : TrackOutput {
            private var pending = ByteArray(64 * 1024)
            private var pendingLength = 0

            override fun format(format: Format) {
                if (format.drmInitData != null) encrypted = true
                if (this@Reader.format == null) this@Reader.format = format
            }

            override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
                ensure(pendingLength + length)
                val n = input.read(pending, pendingLength, length)
                if (n == C.RESULT_END_OF_INPUT) {
                    if (allowEndOfInput) return C.RESULT_END_OF_INPUT
                    throw EOFException()
                }
                pendingLength += n
                return n
            }

            override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
                ensure(pendingLength + length)
                data.readBytes(pending, pendingLength, length)
                pendingLength += length
            }

            override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
                if (cryptoData != null || flags and C.BUFFER_FLAG_ENCRYPTED != 0) {
                    encrypted = true
                    return
                }
                val end = pendingLength - offset
                val start = end - size
                if (start < 0 || size < 0) {
                    pendingLength = 0
                    return
                }
                var from = start
                var count = size
                if (flags and C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA != 0 && count >= 4) {
                    // [본 표본의 길이 4바이트][본 표본][덧붙은 데이터] — 본 표본만 담는다.
                    val main = ((pending[from].toInt() and 0xFF) shl 24) or ((pending[from + 1].toInt() and 0xFF) shl 16) or
                        ((pending[from + 2].toInt() and 0xFF) shl 8) or (pending[from + 3].toInt() and 0xFF)
                    from += 4
                    count = main.coerceIn(0, count - 4)
                }
                val time = timeUs + offsetUs
                if (timeUs != C.TIME_UNSET && !source.skip(index, time)) queue.addLast(Sample(pending.copyOfRange(from, from + count), time, flags))
                // 다음 표본의 앞부분(offset 바이트)을 앞으로 당긴다.
                System.arraycopy(pending, end, pending, 0, offset)
                pendingLength = offset
            }

            private fun ensure(capacity: Int) {
                if (capacity <= pending.size) return
                pending = pending.copyOf(maxOf(capacity, pending.size * 2))
            }
        }
    }

    /** 파일의 한 구간을 앞으로만 읽는다(추출기가 되돌아가면 새 읽개를 만든다). 64 KB 씩 모아 읽는다. */
    private class FileRange(file: File, private val start: Long, val length: Long) : Closeable {
        private val raf = RandomAccessFile(file, "r")

        fun readerAt(position: Long): DataReader = object : DataReader {
            private var pos = position
            private val buffer = ByteArray(64 * 1024)
            private var bufferStart = 0L
            private var bufferLength = 0

            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (pos >= this@FileRange.length) return C.RESULT_END_OF_INPUT
                if (pos < bufferStart || pos >= bufferStart + bufferLength) {
                    raf.seek(start + pos)
                    val want = minOf(buffer.size.toLong(), this@FileRange.length - pos).toInt()
                    val n = raf.read(buffer, 0, want)
                    if (n <= 0) return C.RESULT_END_OF_INPUT
                    bufferStart = pos
                    bufferLength = n
                }
                val n = minOf((bufferStart + bufferLength - pos).toInt(), length)
                System.arraycopy(buffer, (pos - bufferStart).toInt(), target, offset, n)
                pos += n
                return n
            }
        }

        override fun close() {
            runCatching { raf.close() }
        }
    }

    companion object {
        /** 줄기들의 첫 시각이 이만큼 안에서 갈리면 같은 시계로 본다. */
        private const val ALIGN_LIMIT_US = 2_000_000L

        /** 첫 시각을 정하려고 미리 읽는 표본 수. */
        private const val PRIME_SAMPLES = 24

        private val WEBM_MIMES = setOf(MimeTypes.VIDEO_VP8, MimeTypes.VIDEO_VP9, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS)

        private fun mp4Mimes(): Set<String> = buildSet {
            addAll(listOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_MP4V, MimeTypes.AUDIO_AAC))
            // MediaMuxer 의 MP4 는 14(API 34)부터 AV1 을 담는다.
            if (Build.VERSION.SDK_INT >= 34) add(MimeTypes.VIDEO_AV1)
        }

        /** 덩이 하나의 첫 표본 시각([type] 트랙) — 읽지 못하거나 그 트랙이 없으면 null. 가로채기가 덩이를 시각 차례로 놓을 때 쓴다. */
        fun firstTimeUs(run: Run, type: Int): Long? = try {
            Remux(listOf(Source(listOf(run), type))).use { it.firstTimes().firstOrNull() }
        } catch (e: IOException) {
            null
        }

        fun newExtractor(container: Container): Extractor = when (container) {
            Container.TS -> TsExtractor(
                TsExtractor.MODE_SINGLE_PMT,
                0,
                SubtitleParser.Factory.UNSUPPORTED,
                // 시각을 0 으로 당기지 않는다 — 따로 된 소리와 같은 시계(PTS)로 맞추려고.
                TimestampAdjuster(TimestampAdjuster.MODE_NO_OFFSET),
                DefaultTsPayloadReaderFactory(0),
                TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
            )
            Container.FMP4 -> FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED, 0)
            Container.MP4 -> Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED, 0)
            Container.ADTS -> AdtsExtractor()
            Container.MP3 -> Mp3Extractor()
            Container.WEBM -> MatroskaExtractor(SubtitleParser.Factory.UNSUPPORTED, 0)
        }

        /** 머리 바이트로 그릇을 가린다(부르는 쪽이 모를 때). */
        fun sniff(head: ByteArray): Container? {
            fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
            fun ascii(i: Int, s: String) = i + s.length <= head.size && s.indices.all { head[i + it].toInt() == s[it].code }
            return when {
                at(0) == 0x47 && (head.size < 189 || at(188) == 0x47) -> Container.TS
                at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3 -> Container.WEBM
                ascii(4, "ftyp") || ascii(4, "styp") || ascii(4, "moof") || ascii(4, "moov") || ascii(4, "sidx") ->
                    if (containsAscii(head, "moof") || containsAscii(head, "mvex")) Container.FMP4 else Container.MP4
                ascii(0, "ID3") || (at(0) == 0xFF && (at(1) and 0xF6) == 0xF0) -> Container.ADTS
                at(0) == 0xFF && (at(1) and 0xE0) == 0xE0 -> Container.MP3
                else -> null
            }
        }

        private fun containsAscii(head: ByteArray, s: String): Boolean {
            val b = s.toByteArray(Charsets.US_ASCII)
            outer@ for (i in 0..head.size - b.size) {
                for (k in b.indices) if (head[i + k] != b[k]) continue@outer
                return true
            }
            return false
        }
    }
}
