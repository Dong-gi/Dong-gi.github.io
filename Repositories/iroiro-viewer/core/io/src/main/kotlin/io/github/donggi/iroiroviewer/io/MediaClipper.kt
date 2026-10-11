package io.github.donggi.iroiroviewer.io

import android.annotation.SuppressLint
import io.github.donggi.iroiroviewer.io.clip.AudioCut
import io.github.donggi.iroiroviewer.io.clip.AudioExtract
import io.github.donggi.iroiroviewer.io.clip.CutPlan
import io.github.donggi.iroiroviewer.io.clip.FlacCut
import io.github.donggi.iroiroviewer.io.clip.MediaRemux
import io.github.donggi.iroiroviewer.io.clip.Mp3Cut
import io.github.donggi.iroiroviewer.io.clip.WavCut
import io.github.donggi.iroiroviewer.io.clip.WindowReader
import io.github.donggi.iroiroviewer.io.clip.ascii
import io.github.donggi.iroiroviewer.io.clip.id3v2Length
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 소리·영상의 한 구간을 **새 파일로** 저장한다(사용자 요청 '구간을 지정해 별도 저장'). **다시 인코딩하지 않는다** —
 * 화질·음질이 그대로이고 폰에서도 빠르다. 대가는 자를 수 있는 자리가 형식마다 정해져 있다는 것이다.
 *
 * | 형식 | 누가 자르나 | 자르는 자리 |
 * |---|---|---|
 * | WAV(PCM) | [WavCut] | 표본 — 고른 시각 그대로 |
 * | MP3·MP2 | [Mp3Cut] | 프레임(약 26 ms) |
 * | FLAC | [FlacCut] | 프레임(보통 약 0.09초) |
 * | MP4·MOV·3GP·MKV·WebM·TS·M4A·AAC·Opus·Ogg Vorbis | [MediaRemux](플랫폼 `MediaMuxer`) | 영상은 시작 앞의 키프레임, 소리는 프레임 |
 *
 * **소리만 뽑기**(사용자 요청 '오디오만 추출하여 별도로 저장') — 영상에서 소리 트랙 하나만 그 소리의 그릇으로 꺼낸다([AudioExtract]).
 * 구간을 고르면 그 구간만, 고르지 않으면(끝이 null) 끝까지다.
 *
 * 결과는 원본 곁의 `이름_clip.확장자`(소리만이면 `이름_audio.확장자`)다. 확장자는 담은 그릇을 따른다 — MKV 안의 H.264 는 MP4 로,
 * Ogg Vorbis 는 WebM 으로 담긴다(플랫폼이 쓸 수 있는 그릇이 그것뿐이다). 임시본에 다 쓰고 끝에 이름을 맡는다([NewFiles.place]).
 *
 * 담을 수 없는 것 — FLAC 을 품은 영상, AC-3·DTS 소리만 있는 영상, AV1, MP3 를 품은 MP4, 잠긴(DRM) 미디어, ADPCM·RF64 WAV — 은
 * [FileOpEngine.Reason.UNSUPPORTED] 로 끝난다(그 영상의 소리만은 뽑힐 수 있다 — FLAC·AC-3 를 품은 MKV).
 */
object MediaClipper {

    /** 이보다 짧은 구간은 받지 않는다. 화면이 같은 값으로 단추를 끈다. */
    const val MIN_CLIP_MS = 500L

    /** 결과 이름의 꼬리 — `노래_clip.mp3`. */
    const val TAG = "clip"

    /** 소리만 뽑은 결과의 꼬리 — `영상_audio.m4a`. */
    const val AUDIO_TAG = "audio"

    data class Result(
        val outcome: FileOpEngine.Outcome,
        val path: String?,
        val note: FileOpManager.SavedNote? = null,
    )

    /**
     * @param endMs 구간의 끝. null 이면 미디어의 끝까지.
     * @param audioOnly 소리 트랙 하나만 뽑는다.
     * @param onPlaced 새 파일을 놓은 순간에 불린다([NewFiles.place]) — 그 뒤에 온 취소가 결과를 버려도 큐가 '놓였다' 를 안다.
     */
    suspend fun clip(
        path: String,
        startMs: Long,
        endMs: Long?,
        audioOnly: Boolean,
        media: MediaIndex?,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onPlaced: (Result) -> Unit = {},
    ): Result = withContext(IroDispatchers.io) {
        val src = File(path)
        if (!src.isFile) return@withContext failed(FileOpEngine.Reason.NOT_FOUND, src.name)
        val dir = src.parentFile ?: return@withContext failed(FileOpEngine.Reason.IO, src.name)
        if (startMs < 0 || (endMs != null && endMs - startMs < MIN_CLIP_MS)) {
            return@withContext failed(FileOpEngine.Reason.EMPTY_RANGE, src.name)
        }
        AtomicFileWriter.sweepPartials(dir)
        onProgress(FileOpEngine.Progress(0, 0, 0, 1, src.name))
        val startUs = startMs * 1000
        val endUs = endMs?.let { it * 1000 } ?: TO_END_US
        val ext = NewFiles.extensionOf(src.name)
        val tag = if (audioOnly) AUDIO_TAG else TAG
        val job = Job(src, dir, media, onProgress, onPlaced)
        try {
            when (sniff(src)) {
                // 소리 파일은 그 자체가 소리다 — '소리만' 이어도 구간 저장과 같다(다른 꼬리를 단다).
                Kind.WAV -> cutAudio(job, tag) { WavCut.plan(it, startUs, endUs, ext) }
                Kind.FLAC -> cutAudio(job, tag) { FlacCut.plan(it, startUs, endUs, ext) }
                Kind.MP3 -> cutAudio(job, tag) { Mp3Cut.plan(it, startUs, endUs, ext) }
                Kind.OTHER -> if (audioOnly) extractAudio(job, startUs, endUs) else remux(job, startUs, endUs, ext)
            }
        } catch (e: IOException) {
            // 판별에서 파일을 읽지 못했다.
            failed(NewFiles.reasonOf(e), src.name)
        }
    }

    /** 소리 트랙이 어떤가 — 구간 칸의 '소리만 저장' 단추가 미리 본다. */
    enum class AudioState {
        /** 뽑을 수 있다. */
        WRITABLE,

        /** 소리는 있는데 뽑을 수 없다(코덱·잠김). */
        UNSUPPORTED,

        /** 소리 트랙이 없다. */
        NONE,
    }

    /**
     * 저장하기 전에 미리 보는 것 — 재생 화면의 구간 칸이 누르기 전에 말한다(기기에서 확인하다 나온 빈자리: 고른 시작이 0:30.1 인데 담긴
     * 영상은 0:20.8 부터였고, 소리가 FLAC 인 MKV 는 누른 뒤에야 '잘라 저장할 수 없다' 를 들었다).
     *
     * @param supported 영상째로(또는 소리 파일 그대로) 담을 수 있는가. 우리가 직접 자르는 소리(WAV·MP3·FLAC)는 머리를 다 읽지 않고
     *   참으로 둔다 — 그 안의 드문 갈래(ADPCM WAV 등)는 저장할 때 말한다.
     * @param videoStartMs 영상이면 실제로 시작할 자리(고른 시작 바로 앞의 키프레임). 영상이 아니면 null.
     * @param audio 소리만 뽑을 수 있는가.
     */
    data class Preview(val supported: Boolean, val videoStartMs: Long?, val audio: AudioState = AudioState.WRITABLE)

    suspend fun preview(path: String, startMs: Long): Preview = withContext(IroDispatchers.io) {
        val src = File(path)
        if (!src.isFile) return@withContext Preview(false, null, AudioState.UNSUPPORTED)
        val kind = try {
            sniff(src)
        } catch (e: IOException) {
            return@withContext Preview(false, null, AudioState.UNSUPPORTED)
        }
        if (kind != Kind.OTHER) return@withContext Preview(true, null, AudioState.WRITABLE)
        val audio = when (val o = AudioExtract.prepare(src)) {
            is AudioExtract.Opened.Ok -> {
                o.prepared.close()
                AudioState.WRITABLE
            }
            AudioExtract.Opened.NoAudio -> AudioState.NONE
            AudioExtract.Opened.Unsupported -> AudioState.UNSUPPORTED
        }
        val prepared = MediaRemux.prepare(src, NewFiles.extensionOf(src.name))
            // 소리만 든 그릇(MKA 의 FLAC·AC-3, 소리만 든 TS 의 MP2)은 플랫폼이 다시 담지 못해도 저장된다 — 소리 뽑기로([remux]).
            ?: return@withContext Preview(audio == AudioState.WRITABLE && !hasVideoTrack(src), null, audio)
        prepared.use { p ->
            val keyframe = p.choice.video?.let { MediaRemux.keyframeBefore(p, it, startMs.coerceAtLeast(0) * 1000) }?.div(1000)
            Preview(true, keyframe, audio)
        }
    }

    private enum class Kind { WAV, FLAC, MP3, OTHER }

    /** 영상 트랙이 있는가(미리 보기가 '소리만 든 그릇' 을 가른다). 읽지 못하면 있다고 본다 — 영상을 소리로 바꿔 저장하지 않게. */
    private fun hasVideoTrack(src: File): Boolean = when (val o = AudioExtract.prepare(src)) {
        is AudioExtract.Opened.Ok -> o.prepared.use { it.hasVideo }
        else -> true
    }

    /** 내용으로 가른다 — 확장자는 믿지 않는다(이름이 `.mp3` 인 MP4 가 있다). */
    private fun sniff(src: File): Kind = RandomAccessFile(src, "r").use { raf ->
        val r = WindowReader(raf)
        val riff = r.ascii(0, 4)
        when {
            (riff == "RIFF" || riff == "RF64") && r.ascii(8, 4) == "WAVE" -> Kind.WAV
            r.ascii(id3v2Length(r), 4) == "fLaC" -> Kind.FLAC
            Mp3Cut.looksLikeMp3(raf) -> Kind.MP3
            else -> Kind.OTHER
        }
    }

    /** 한 번의 저장이 함께 들고 다니는 것. */
    private class Job(
        val src: File,
        val dir: File,
        val media: MediaIndex?,
        val onProgress: (FileOpEngine.Progress) -> Unit,
        val onPlaced: (Result) -> Unit,
    )

    /** 우리가 직접 자르는 소리(WAV·MP3·FLAC). */
    private suspend fun cutAudio(job: Job, tag: String, plan: suspend (RandomAccessFile) -> CutPlan): Result =
        RandomAccessFile(job.src, "r").use { raf ->
            val planned = try {
                plan(raf)
            } catch (e: OutOfMemoryError) {
                // 계획은 고른 구간의 프레임을 기억한다 — 아주 긴 구간이면 힙을 넘을 수 있다. 여기서 잡지 않으면 큐의 경계가 이름 없는
                // '입출력이 실패했습니다' 로 바꿨다. 쓰기 전이라 버릴 것도 없다.
                Iro.e(message = "구간 저장의 계획이 메모리를 넘었다")
                return failed(FileOpEngine.Reason.RANGE_TOO_LONG, job.src.name)
            }
            val cut: AudioCut = when (planned) {
                is CutPlan.Ok -> planned.cut
                CutPlan.Unsupported -> return failed(FileOpEngine.Reason.UNSUPPORTED, job.src.name)
                CutPlan.Empty -> return failed(FileOpEngine.Reason.EMPTY_RANGE, job.src.name)
                CutPlan.TooLong -> return failed(FileOpEngine.Reason.RANGE_TOO_LONG, job.src.name)
            }
            val name = NewFiles.derivedName(job.src.name, tag, cut.extension)
            val pending = try {
                AtomicFileWriter.begin(File(job.dir, name), 0L)
            } catch (e: IOException) {
                return failed(NewFiles.reasonOf(e), name)
            }
            val progress = Progress(job.src.name, job.onProgress, cut.outputBytes)
            val space = SpaceCheck(job.dir)
            try {
                // **닫지 않고 비우기만 한다** — 아래 `place` 가 fsync 한 뒤에 닫는다.
                val out = BufferedOutputStream(pending.stream, BUFFER)
                cut.writeTo(out) { n ->
                    progress.add(n)
                    space.wrote(n)
                }
                out.flush()
                val note = FileOpManager.SavedNote.Clipped(cut.startUs / 1000, cut.endUs / 1000, 0)
                val target = NewFiles.place(pending, job.dir, name, job.media) { job.onPlaced(done(it, note)) }
                done(target, note)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { pending.abort() }
                Result(FileOpEngine.Outcome.Cancelled, null)
            } catch (e: Stop) {
                withContext(NonCancellable) { pending.abort() }
                failed(e.reason, name)
            } catch (t: Throwable) {
                withContext(NonCancellable) { pending.abort() }
                Iro.e(message = "구간 저장이 실패했다: ${t::class.java.simpleName}")
                failed(silentReason(job.dir, t), name)
            }
        }

    /**
     * 플랫폼이 읽고 쓰는 그릇(영상, 그리고 MP4·WebM·Ogg 의 소리). 다시 담지 못하는 **소리만 든** 그릇(MKA 의 FLAC·AC-3·PCM·MP3, 소리만
     * 든 TS 의 MP2)은 소리 뽑기가 쓴다 — 영상이 없으니 그것이 곧 그 구간이다(그 파일들은 엔진이 쓸 수 있는데 칸이 막았다).
     */
    private suspend fun remux(job: Job, startUs: Long, endUs: Long, ext: String): Result {
        val prepared = MediaRemux.prepare(job.src, ext) ?: return extractAudio(job, startUs, endUs, tag = TAG, onlyWithoutVideo = true)
        return prepared.use { p ->
            val name = NewFiles.derivedName(job.src.name, TAG, p.choice.extension)
            // 처음 몇 %의 분모 어림 — 파일 크기에 구간이 차지하는 몫을 곱한다(그릇의 머리·색인은 빼지 않는다). 그 뒤로는 시각으로 잰다.
            val estimate = if (p.durationUs > 0) {
                val span = (minOf(endUs, p.durationUs) - startUs).coerceAtLeast(0)
                (job.src.length() * (span.toDouble() / p.durationUs)).toLong().coerceAtLeast(1)
            } else {
                job.src.length()
            }
            writeMuxed(job, name, startUs, endUs, p.durationUs, estimate) { pending, onSample ->
                MediaRemux.write(p, startUs, endUs, pending.file.fd, onSample)
            }
        }
    }

    /**
     * 영상에서 소리 트랙 하나만([AudioExtract]).
     *
     * @param onlyWithoutVideo 영상이 있는 파일이면 뽑지 않는다(구간 저장이 다시 담지 못한 영상을 소리만으로 바꿔 저장하지 않게).
     */
    private suspend fun extractAudio(
        job: Job,
        startUs: Long,
        endUs: Long,
        tag: String = AUDIO_TAG,
        onlyWithoutVideo: Boolean = false,
    ): Result {
        val prepared = when (val o = AudioExtract.prepare(job.src)) {
            is AudioExtract.Opened.Ok -> o.prepared
            AudioExtract.Opened.NoAudio ->
                return failed(if (onlyWithoutVideo) FileOpEngine.Reason.UNSUPPORTED else FileOpEngine.Reason.NO_AUDIO, job.src.name)
            AudioExtract.Opened.Unsupported -> return failed(FileOpEngine.Reason.UNSUPPORTED, job.src.name)
        }
        if (onlyWithoutVideo && prepared.hasVideo) {
            prepared.close()
            return failed(FileOpEngine.Reason.UNSUPPORTED, job.src.name)
        }
        return prepared.use { p ->
            val name = NewFiles.derivedName(job.src.name, tag, p.choice.out.extension)
            // 소리의 크기는 그 트랙의 비트율로 어림한다(모르면 처음 몇 %는 끝을 모르는 진행으로 보인다).
            val bitrate = p.formats[p.choice.track].let { f ->
                if (f.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) f.getInteger(android.media.MediaFormat.KEY_BIT_RATE) else 0
            }
            val span = (minOf(endUs, p.durationUs.takeIf { it > 0 } ?: endUs) - startUs).coerceAtLeast(0)
            val estimate = if (bitrate > 0 && span < TO_END_US) bitrate.toLong() / 8 * span / 1_000_000 else 0L
            writeMuxed(job, name, startUs, endUs, p.durationUs, estimate) { pending, onSample ->
                AudioExtract.write(p, startUs, endUs, pending.file, onSample)
            }
        }
    }

    /**
     * 그릇을 되감아 쓰는 길(플랫폼의 `MediaMuxer`, 그리고 머리를 끝에서 고치는 우리 소리 그릇)의 공통 — 임시본을 열고, [write] 가
     * 쓰고, 실패를 까닭으로 옮기고, 이름을 맡아 놓는다.
     */
    private suspend fun writeMuxed(
        job: Job,
        name: String,
        startUs: Long,
        endUs: Long,
        durationUs: Long,
        estimate: Long,
        write: suspend (AtomicFileWriter.RandomPending, (Long, Long) -> Unit) -> MediaRemux.Outcome,
    ): Result {
        val pending = try {
            AtomicFileWriter.beginRandomAccess(File(job.dir, name))
        } catch (e: IOException) {
            return failed(NewFiles.reasonOf(e), name)
        }
        val end = if (durationUs > 0) minOf(endUs, durationUs) else endUs
        val progress = Progress(job.src.name, job.onProgress, estimate, startUs, end)
        val space = SpaceCheck(job.dir)
        try {
            val outcome = write(pending) { n, atUs ->
                progress.add(n, atUs)
                space.wrote(n)
            }
            outcome.failure?.let { f ->
                withContext(NonCancellable) { pending.abort() }
                val reason = when (f) {
                    MediaRemux.Failure.UNSUPPORTED -> FileOpEngine.Reason.UNSUPPORTED
                    MediaRemux.Failure.EMPTY -> FileOpEngine.Reason.EMPTY_RANGE
                    // 영상은 있는데 소리가 비었다 — '담을 것이 없다' 와 가른다(무엇이 빈 것인지 말한다).
                    MediaRemux.Failure.EMPTY_AUDIO -> FileOpEngine.Reason.NO_AUDIO_IN_RANGE
                }
                return failed(reason, job.src.name)
            }
            val note = FileOpManager.SavedNote.Clipped(outcome.startUs / 1000, outcome.endUs / 1000, outcome.dropped)
            val target = NewFiles.place(pending, job.dir, name, job.media) { job.onPlaced(done(it, note)) }
            return done(target, note)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { pending.abort() }
            return Result(FileOpEngine.Outcome.Cancelled, null)
        } catch (e: Stop) {
            withContext(NonCancellable) { pending.abort() }
            return failed(e.reason, name)
        } catch (t: Throwable) {
            // 쓰는 도중의 실패 — 플랫폼의 그릇은 errno 없이 `IllegalStateException` 을 던진다(대개 디스크가 찼다). 그릇을 세우는
            // 실패(형식 탓)는 위에서 UNSUPPORTED 로 왔다.
            withContext(NonCancellable) { pending.abort() }
            Iro.e(message = "구간 저장(다시 담기)이 실패했다: ${t::class.java.simpleName}")
            return failed(silentReason(job.dir, t), name)
        }
    }

    /** 실패의 까닭 — errno 를 실은 것은 그대로, 싣지 않은 것은 남은 공간을 보고 고른다([NewFiles.reasonWhenSilent]). */
    private fun silentReason(dir: File, t: Throwable): FileOpEngine.Reason {
        val r = NewFiles.reasonOf(t)
        return if (r == FileOpEngine.Reason.IO) NewFiles.reasonWhenSilent(dir, r) else r
    }

    private fun done(target: File, note: FileOpManager.SavedNote) =
        Result(FileOpEngine.Outcome.Done(1, 0, 0), target.absolutePath, note)

    private fun failed(reason: FileOpEngine.Reason, name: String) = Result(FileOpEngine.Outcome.Failed(reason, name), null)

    private class Stop(val reason: FileOpEngine.Reason) : Exception()

    /**
     * 진행 알림 — [REPORT_NANOS] 에 한 번(ZIP 만들기와 같다). 쓸 크기를 아는 길(우리가 자르는 소리)은 바이트로, 그릇을 다시 쓰는 길은
     * **시각으로** 잰다 — 표본의 시각이 [startUs]~[endUs] 의 몇 %인가. 분모(전체 크기)는 쓴 바이트를 그 몫으로 나눠 어림한다(처음
     * 2%까지는 [estimate]). 영상 파일의 크기로 어림하면 소리만 뽑을 때 진행이 몇 %에서 끝났다.
     */
    private class Progress(
        private val name: String,
        private val sink: (FileOpEngine.Progress) -> Unit,
        private val estimate: Long,
        private val startUs: Long = -1,
        private val endUs: Long = -1,
    ) {
        private var done = 0L
        private var lastAt = 0L

        fun add(n: Long, atUs: Long = -1) {
            done += n
            val now = System.nanoTime()
            if (now - lastAt < REPORT_NANOS) return
            lastAt = now
            val span = endUs - startUs
            val total = if (atUs >= 0 && startUs >= 0 && span > 0) {
                val f = ((atUs - startUs).toDouble() / span).coerceIn(0.0, 1.0)
                if (f >= 0.02) (done / f).toLong() else estimate
            } else {
                estimate
            }
            sink(FileOpEngine.Progress(if (total > 0) done.coerceAtMost(total) else done, total, 0, 1, name))
        }
    }

    /** 남은 공간을 지킨다(ZIP 만들기와 같은 값 — [ZipMaker.SPACE_MARGIN_BYTES]). */
    private class SpaceCheck(private val dir: File) {
        private var since = 0L

        @SuppressLint("UsableSpace")
        fun wrote(n: Long) {
            since += n
            if (since < SPACE_CHECK_BYTES) return
            since = 0
            // 남길 몫을 재는 자리라 보수적인 값(usableSpace)이 맞다.
            if (dir.usableSpace in 1 until ZipMaker.SPACE_MARGIN_BYTES) throw Stop(FileOpEngine.Reason.NO_SPACE)
        }
    }

    /** '끝까지' — 셈이 넘치지 않는 큰 끝(열흘). 그보다 긴 미디어는 없다고 본다. */
    private const val TO_END_US = 10L * 24 * 3600 * 1_000_000

    private const val SPACE_CHECK_BYTES = 16L * 1024 * 1024
    private const val REPORT_NANOS = 100_000_000L
    private const val BUFFER = 64 * 1024
}
