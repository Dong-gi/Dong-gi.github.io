package io.github.donggi.iroiroviewer.io.clip

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 영상(과 MP4·WebM·Ogg 의 소리)의 한 구간을 **다시 인코딩하지 않고** 새 그릇에 옮겨 담는다 — 플랫폼의 `MediaExtractor` 가 읽고
 * `MediaMuxer` 가 쓴다. 어느 그릇에 무엇을 담는가는 [RemuxPlan] 이 정한다.
 *
 * ## 영상은 키프레임에서만 시작할 수 있다
 *
 * 키프레임이 아닌 프레임은 앞 프레임을 참조한다. 다시 인코딩하지 않으면 **고른 시작 바로 앞의 키프레임**부터 담을 수밖에 없다 —
 * 결과는 고른 구간보다 앞에서 시작할 수 있다(휴대폰 녹화는 1초, 내려받은 영상은 2~10초 간격이 흔하다). 실제로 담은 시작을 결과가
 * 싣고 화면이 말한다. 소리 트랙은 그 키프레임의 시각부터 담아 영상과 맞춘다. 소리만 있는 파일은 프레임(AAC 는 약 23 ms) 단위로 맞는다.
 *
 * ## 끝 — B 프레임
 *
 * 프레임은 **푸는 차례**로 온다. B 프레임이 있으면 표시 시각이 끝보다 앞인 프레임이 끝을 넘은 프레임 뒤에 올 수 있어, 끝을 넘은
 * 첫 프레임에서 멈추지 않고 다음 키프레임(또는 [REORDER_SLACK_US])까지 보며 표시 시각이 끝보다 앞인 것만 담는다. 끝 바로 앞의
 * B 프레임이 끝 너머의 프레임을 참조하면 마지막 한두 프레임이 깨져 보일 수 있다(다시 인코딩하지 않는 대가).
 *
 * ## 옮기는 것
 *
 * 영상의 **회전**(휴대폰 세로 영상의 `rotation-degrees`)은 옮긴다 — 옮기지 않으면 세로 영상이 누워서 나온다. 제목·날짜·위치 같은
 * 메타데이터는 플랫폼이 쓸 길을 주지 않아 옮기지 않는다.
 *
 * AAC 표본이 ADTS 머리째 오면(TS 추출기 — `is-adts`) 머리를 떼고 담는다. 그 추출기는 PES 하나의 프레임을 묶어 표본 하나로 주므로
 * **프레임마다 갈라** 따로 담는다([Adts]) — 고른 구간의 경계와 들고 가기도 프레임 단위다.
 *
 * ## 그 구간에서 빈 소리 트랙
 *
 * 영상과 함께 담는 소리 트랙이 그 구간에 표본이 하나도 없으면(소리가 영상보다 먼저 끝났다, 둘째 소리가 늦게 시작한다) — **첫 소리
 * 트랙**이 비면 거절한다([Failure.EMPTY_AUDIO] — 소리 없는 영상을 말없이 만들지 않는다). **둘째부터**는 그 트랙만 빼고 담고 뺀 수를
 * 결과가 말한다. 빈 트랙을 그릇에 더해 두면 그릇을 닫을 수 있는지 알 수 없어 미리 본다 — 되감을 수 있는 그릇은 그 자리로 되감아
 * 트랙마다 첫 표본만 보고, 처음부터 읽는 그릇은 키프레임을 찾는 걸음에서 함께 본다. 예전에는 어느 트랙이든 비면 통째로 '고른 구간에
 * 담을 것이 없습니다' 였다.
 *
 * ## 되감기를 믿을 수 없는 그릇 — MPEG-TS·PS
 *
 * 그런 그릇은 되감지 않고 처음부터 읽는다([seeksUnreliably]). 고른 시작 앞의 표본은 쓰는 고리가 버린다 — 긴 녹화의 뒤쪽을 고르면
 * 그 앞을 다 읽는 만큼 오래 걸린다(대가다).
 */
internal object MediaRemux {

    /** 결과. [startUs]·[endUs] 는 실제로 담은 구간이다. 실패면 [failure]. */
    class Outcome(
        val failure: Failure?,
        val startUs: Long = 0,
        val endUs: Long = 0,
        val dropped: Int = 0,
    )

    enum class Failure {
        UNSUPPORTED,

        /** 고른 구간에 담을 것이 없다(영상이면 영상이, 소리만이면 그 소리가 비었다). */
        EMPTY,

        /** 영상은 있는데 첫 소리 트랙이 그 구간에서 비었다 — 소리 없는 영상을 말없이 만들지 않는다. */
        EMPTY_AUDIO,
    }

    /** 읽기만 하는 판단 — 무엇을 어디에 담을지. 쓰기 전에 정해 이름(확장자)을 고른다. */
    class Prepared internal constructor(
        val choice: RemuxPlan.Choice,
        internal val extractor: MediaExtractor,
        internal val formats: List<MediaFormat>,
        internal val durationUs: Long,
        /** 원본 — 되감기를 믿을 수 없는 그릇에서 키프레임을 따로 걸어 찾을 때 하나 더 연다. */
        internal val src: File,
    ) : AutoCloseable {
        override fun close() {
            extractor.release()
        }
    }

    /**
     * 파일을 열어 담을 것을 정한다. 플랫폼이 읽지 못하거나(그릇을 모른다), 잠겼거나(DRM), 담을 코덱이 없으면 null.
     */
    fun prepare(src: File, sourceExt: String): Prepared? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(src.absolutePath)
            // 잠긴 미디어(DRM)는 다루지 않는다 — 풀지 않으므로 옮겨 담아도 열리지 않는다.
            if (extractor.drmInitData != null) {
                extractor.release()
                return null
            }
            val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val mimes = formats.map { it.getString(MediaFormat.KEY_MIME).orEmpty() }
            val choice = RemuxPlan.choose(mimes, sourceExt)
            if (choice == null) {
                extractor.release()
                return null
            }
            val duration = formats.maxOfOrNull { f ->
                if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            } ?: 0L
            return Prepared(choice, extractor, formats, duration, src)
        } catch (e: IOException) {
            extractor.release()
            return null
        } catch (e: RuntimeException) {
            // 플랫폼 추출기는 깨진 그릇에 IllegalArgumentException 등을 던진다.
            extractor.release()
            return null
        }
    }

    /**
     * [track] 에서 [us] 바로 앞의 키프레임 시각(마이크로초). 읽지 못하면 null. 미리 보기(`MediaClipper.preview`)가 쓴다 — [prepared] 는
     * 아직 읽지 않은 것이어야 한다(되감기를 믿을 수 없는 그릇은 처음부터 걷는다).
     */
    suspend fun keyframeBefore(prepared: Prepared, track: Int, us: Long): Long? = try {
        val ex = prepared.extractor
        ex.selectTrack(track)
        val t = if (seeksUnreliably(ex, prepared.src)) walkToKeyframe(ex, us) else seekAtOrBefore(ex, us)
        t.takeIf { it >= 0 }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        null
    } finally {
        runCatching { prepared.extractor.unselectTrack(track) }
    }

    /**
     * 되감기를 믿을 수 없는 그릇인가 — 그런 그릇은 되감지 않고 **처음부터 읽는다**. 플랫폼의 MPEG-TS 추출기는 소리 트랙만 고르면 되감지
     * 않고(`write` 의 들고 가기), 영상과 함께 되감으면 시간축을 **그 자리 뒤에 처음 읽은 PES** 로 다시 세운다 — 그보다 앞선 표본은 그
     * 시각으로 눌린다. 방송처럼 영상을 소리보다 앞서 실은 TS 에서 2.5~4.5초를 고르면 소리는 1.5초부터 담기고 영상은 열여섯 장뿐이었다
     * (계측 시험이 손으로 지은 TS 로 잰 값). MPEG-PS 추출기는 아예 되감지 못한다.
     *
     * 가르는 것은 추출기의 이름이다(`getMetrics` — 이 기기에서 `MPEG2TSExtractor`·`MPEG4Extractor`·`MatroskaExtractor`). 이름을 주지
     * 않으면 확장자로.
     */
    internal fun seeksUnreliably(ex: MediaExtractor, src: File): Boolean {
        val name = try {
            ex.metrics.getString(MediaExtractor.MetricsConstants.FORMAT)
        } catch (e: RuntimeException) {
            null
        }
        if (!name.isNullOrEmpty()) return name.startsWith("MPEG2TS") || name.startsWith("MPEG2PS")
        return src.extension.lowercase() in SEQUENTIAL_EXTENSIONS
    }

    /**
     * 되감지 않고 처음부터 걸어 [startUs] 이하의 마지막 동기 표본 시각을 찾는다 — 없으면 그 뒤의 첫 동기 표본, 그것도 없으면 -1.
     * [ex] 는 그 트랙 하나만 고른 채 처음에 서 있어야 하고, 걷고 나면 그 자리에 남는다.
     */
    private suspend fun walkToKeyframe(ex: MediaExtractor, startUs: Long): Long {
        var best = -1L
        var count = 0
        while (true) {
            if (count++ % 256 == 0) currentCoroutineContext().ensureActive()
            val t = ex.sampleTime
            if (t < 0) break
            val sync = ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
            if (t <= startUs) {
                if (sync) best = t
            } else if (sync) {
                // 고른 시각을 넘은 첫 동기 표본 — 앞의 것이 있으면 그것이고, 없으면 이것이 담을 수 있는 가장 이른 자리다.
                if (best < 0) best = t
                break
            }
            if (!ex.advance()) break
        }
        return best
    }

    /** 처음부터 걸어 찾은 것 — 담기 시작할 키프레임(없으면 -1)과, 그 키프레임부터 끝 앞까지 표본이 있는 소리 트랙. */
    private class Walk(val keyframeUs: Long, val audioPresent: Set<Int>)

    /**
     * 처음부터 읽는 그릇에서 — **따로 연** 추출기로 처음부터 걸어 [startUs] 이하의 마지막 키프레임([walkToKeyframe] 과 같은 셈)을 찾고,
     * 같은 걸음에 [audio] 트랙마다 [키프레임, [endUs]) 에 표본이 있는가를 본다. 따로 걸으면 긴 녹화를 한 번 더 읽는다. 쓰는 추출기는
     * 처음에 남겨 둔다.
     *
     * 소리 트랙마다 '[endUs] 앞의 가장 늦은 표본 시각' 하나만 들면 된다 — 트랙 안의 표본은 시각 차례로 오므로 그 값이 키프레임 이상이면
     * 그 구간에 표본이 있고, [endUs] 이상의 표본이 이미 왔는데 그 값이 키프레임보다 앞이면 없다. 키프레임을 찾은 뒤 모든 소리 트랙이
     * 그렇게 정해지면 멈춘다(대개 키프레임을 찾는 바로 그 자리다). 늦어도 끝을 [REORDER_SLACK_US] 넘으면 멈춘다 — 쓰는 고리가 읽는 데까지다.
     */
    private suspend fun walkFromStart(src: File, video: Int, audio: List<Int>, startUs: Long, endUs: Long): Walk {
        val other = MediaExtractor()
        try {
            other.setDataSource(src.absolutePath)
            other.selectTrack(video)
            for (a in audio) other.selectTrack(a)
            var best = -1L
            var keyFound = false
            val latest = LongArray(audio.size) { -1L }
            val passedEnd = BooleanArray(audio.size)
            var count = 0
            while (true) {
                if (count++ % 256 == 0) currentCoroutineContext().ensureActive()
                val track = other.sampleTrackIndex
                if (track < 0) break
                val t = other.sampleTime
                if (track == video) {
                    if (!keyFound && other.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        if (t <= startUs) {
                            best = t
                        } else {
                            // 고른 시각을 넘은 첫 키프레임 — 앞의 것이 있으면 그것이고, 없으면 이것이 담을 수 있는 가장 이른 자리다.
                            if (best < 0) best = t
                            keyFound = true
                        }
                    }
                } else {
                    val i = audio.indexOf(track)
                    if (i >= 0) {
                        if (t < endUs) {
                            if (t > latest[i]) latest[i] = t
                        } else {
                            passedEnd[i] = true
                        }
                    }
                }
                if (keyFound && audio.indices.all { latest[it] >= best || passedEnd[it] }) break
                // 끝을 넉넉히 넘었다 — 추출기는 고른 트랙들의 표본을 시각 차례로 주므로 이제 끝 앞의 소리는 오지 않는다(소리가 일찍 끝난
                // 녹화에서 파일 끝까지 걷지 않는다). 고른 시각 이하의 키프레임도 이미 다 봤다.
                if (t - REORDER_SLACK_US >= endUs) break
                if (!other.advance()) break
            }
            val present = HashSet<Int>()
            if (best >= 0) audio.forEachIndexed { i, a -> if (latest[i] >= best) present += a }
            return Walk(best, present)
        } finally {
            other.release()
        }
    }

    /**
     * 되감을 수 있는 그릇에서 [audio] 트랙마다 [fromUs, [endUs]) 에 표본이 있는가 — 그 자리로 되감아 트랙마다 첫 표본 하나만 본다.
     * 정해진 트랙은 곧바로 내려놓는다 — 남은 트랙만 읽으므로 끝까지 빈 트랙이 하나 남으면 곧 끝난다. [ex] 는 아무 트랙도 고르지 않은
     * 채 와서, 아무 트랙도 고르지 않은 채 돌아간다(쓰는 고리가 다시 고르고 되감는다).
     */
    private suspend fun audioPresentBySeeking(ex: MediaExtractor, audio: List<Int>, fromUs: Long, endUs: Long): Set<Int> {
        val pending = audio.toMutableSet()
        val present = HashSet<Int>()
        try {
            for (a in audio) ex.selectTrack(a)
            ex.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var count = 0
            while (pending.isNotEmpty()) {
                if (count++ % 256 == 0) currentCoroutineContext().ensureActive()
                val track = ex.sampleTrackIndex
                if (track < 0) break
                val t = ex.sampleTime
                if (track in pending && t >= fromUs) {
                    if (t < endUs) present += track
                    pending -= track
                    // 내려놓으면 지금 표본도 사라진다 — 나아가지 않고 남은 트랙의 다음 표본을 본다.
                    ex.unselectTrack(track)
                    continue
                }
                if (!ex.advance()) break
            }
        } finally {
            for (a in pending) runCatching { ex.unselectTrack(a) }
        }
        return present
    }

    /**
     * 고른 트랙 **하나만** 선택된 추출기를 [startUs] 이하의 마지막 동기 표본(영상은 키프레임, 소리는 모든 프레임)에 세운다. 선 자리의
     * 시각을 돌려준다(없으면 -1).
     *
     * 플랫폼 추출기마다 `SEEK_TO_PREVIOUS_SYNC` 의 뜻이 다르다 — MP4 는 그 시각 앞의 키프레임으로 가지만, **MKV 는 소리를 그 시각 뒤의
     * 첫 블록으로, 목차(Cues) 없는 영상을 그 시각 뒤의 키프레임으로** 보낸다(AOSP `MatroskaExtractor` 의 `BlockIterator::seek` —
     * 기기 시험이 잡았다: 1.0초를 고른 FLAC 소리가 1.02초부터 담겼다). 뒤로 갔으면 점점 더 앞에서 다시 찾아, 그 시각을 넘지 않는
     * 마지막 동기 표본까지 앞으로 걷는다.
     *
     * **뒤의 것을 찾다 끝에 닿은 것(-1)도 '뒤로 갔다' 로 본다.** 고른 시작이 마지막 키프레임(소리면 마지막 블록) 뒤면 그 뒤의 것이 없어
     * 추출기가 -1 을 준다 — 예전에는 그대로 돌려줘 미리 보기는 키프레임을 말하지 못하고 저장은 '고른 구간에 담을 것이 없습니다' 였다
     * (목차 없는 MKV·WebM — 브라우저 MediaRecorder 의 WebM 에 흔하다). 앞에서 다시 찾으면 그 마지막 것에 선다.
     */
    fun seekAtOrBefore(ex: MediaExtractor, startUs: Long): Long {
        ex.seekTo(startUs.coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val t = ex.sampleTime
        if (startUs <= 0 || t in 0..startUs) return t
        var back = SEEK_BACKOFF_US
        while (true) {
            val from = (startUs - back).coerceAtLeast(0)
            ex.seekTo(from, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val first = ex.sampleTime
            if (first in 0..startUs) {
                var best = first
                var steps = 0
                while (ex.advance() && steps++ < MAX_WALK) {
                    val n = ex.sampleTime
                    if (n < 0 || n > startUs) break
                    if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) best = n
                }
                // 그 동기 표본으로 — 어느 추출기든 동기 표본의 시각 그대로에 선다.
                ex.seekTo(best, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                return ex.sampleTime
            }
            if (from == 0L) {
                // 더 앞이 없다 — 처음부터 찾은 첫 동기 표본(고른 시각 뒤)이 담을 수 있는 가장 이른 자리다. 그것도 없으면(-1) 빈 트랙이다.
                if (first < 0) return first
                ex.seekTo(first, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                return ex.sampleTime
            }
            back *= 4
        }
    }

    /**
     * [prepared] 의 계획대로 [startUs, endUs) 를 [out] 에 쓴다. **던질 수 있다**(입출력·플랫폼 오류) — 부르는 쪽이 임시본을 버린다.
     *
     * 그릇을 세우는 데(트랙 더하기·시작) 실패하면 [Failure.UNSUPPORTED] 다 — 플랫폼이 그 코덱 설정을 받지 않는다. 쓰는 **도중의**
     * 실패(`writeSampleData`·`stop` 의 `IllegalStateException`)는 형식 탓이 아니다 — 대개 디스크가 찼다. 그것은 던진다(부르는 쪽이
     * 남은 공간을 보고 까닭을 고른다 — 예전에는 둘 다 '이 형식은 저장할 수 없습니다' 였다).
     *
     * @param onSample 옮긴 단위 하나(표본, ADTS 면 프레임 하나) — 바이트와 그 시각(마이크로초). 진행률과 남은 공간 확인에 쓴다.
     */
    suspend fun write(
        prepared: Prepared,
        startUs: Long,
        endUs: Long,
        out: FileDescriptor,
        onSample: (bytes: Long, atUs: Long) -> Unit,
    ): Outcome {
        val choice = prepared.choice
        val ex = prepared.extractor

        // 1. 시작 — 영상이면 고른 시각 바로 앞의 키프레임, 소리만이면 그 시각을 품은 프레임.
        //
        // **소리만이면 되감기를 믿지 않는다.** MPEG-TS 는 기준(영상) 트랙으로만 되감고 소리 트랙만 고르면 되감기가 아무 일도 하지
        // 않으며, MPEG-PS 는 아예 되감지 못한다(AOSP `MPEG2TSExtractor`·`MPEG2PSExtractor` — 30분 녹화의 25분부터를
        // 고르면 0:00 부터 담겼다). 그래서 쓰는 고리가 고른 시각 이하의 마지막 단위를 들고 가다가 그 시각을 넘는 단위가 오면 그것부터
        // 쓴다([held]) — 되감기가 들으면 들고 갈 것이 하나뿐이다. 소리만이면 트랙을 다시 고르지 않는다(풀었다 다시 고르면 TS 는 처음
        // 표본을 잃을 수 있다).
        val lead = choice.video ?: choice.audio.first()
        val audioOnly = choice.video == null
        // 되감기를 믿을 수 없는 그릇(TS·PS)은 처음부터 읽는다 — 소리만이면 들고 가기가 시작을 정하고, 영상이면 따로 연 추출기로 걸어
        // 키프레임을 찾는다(이 추출기는 처음에 남겨 둔다).
        val sequential = seeksUnreliably(ex, prepared.src)
        val offsetUs: Long
        // 영상과 함께 담는 소리 트랙 가운데 그 구간에 표본이 있는 것. null 이면 미리 보지 않았다(쓰고 나서 센다).
        val present: Set<Int>?
        when {
            audioOnly -> {
                ex.selectTrack(lead)
                offsetUs = if (sequential) ex.sampleTime else seekAtOrBefore(ex, startUs)
                present = null
            }
            sequential -> {
                val walk = walkFromStart(prepared.src, lead, choice.audio, startUs, endUs)
                offsetUs = walk.keyframeUs
                present = walk.audioPresent
            }
            else -> {
                ex.selectTrack(lead)
                offsetUs = seekAtOrBefore(ex, startUs)
                ex.unselectTrack(lead)
                // 둘째 소리 트랙부터는 비면 빼야 한다 — 그릇에 더하기 전에 안다. 소리가 하나면 쓰고 나서 센다(되감기를 한 번 아낀다).
                present = if (offsetUs in 0 until endUs && choice.audio.size > 1) {
                    audioPresentBySeeking(ex, choice.audio, offsetUs, endUs)
                } else {
                    null
                }
            }
        }
        if (offsetUs < 0 || offsetUs >= endUs) return Outcome(Failure.EMPTY)

        // 그 구간에서 빈 소리 트랙 — 첫 트랙이면 거절, 둘째부터는 빼고 센다(머리말).
        var audio = choice.audio
        var dropped = choice.dropped
        if (present != null && audio.isNotEmpty()) {
            if (audio.first() !in present) return Outcome(Failure.EMPTY_AUDIO)
            dropped += audio.count { it !in present }
            audio = audio.filter { it in present }
        }
        val tracks = listOfNotNull(choice.video) + audio

        // 2. 그릇. 세우는 데 실패하면 이 코덱 설정을 플랫폼이 받지 않는 것이다.
        val muxer = MediaMuxer(out, formatOf(choice.container))
        var started = false
        try {
            val muxIndex = HashMap<Int, Int>()
            try {
                for (t in tracks) muxIndex[t] = muxer.addTrack(muxFormatOf(prepared.formats[t]))
                choice.video?.let { v ->
                    val f = prepared.formats[v]
                    if (f.containsKey(MediaFormat.KEY_ROTATION)) {
                        val deg = ((f.getInteger(MediaFormat.KEY_ROTATION) % 360) + 360) % 360
                        if (deg % 90 == 0) muxer.setOrientationHint(deg)
                    }
                }
                muxer.start()
            } catch (e: IllegalStateException) {
                return Outcome(Failure.UNSUPPORTED)
            } catch (e: IllegalArgumentException) {
                return Outcome(Failure.UNSUPPORTED)
            }
            started = true

            if (!audioOnly) {
                for (t in tracks) ex.selectTrack(t)
                // 처음부터 읽는 그릇은 되감지 않는다 — 키프레임 앞의 표본은 아래 고리가 버린다.
                if (!sequential) ex.seekTo(offsetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val done = HashSet<Int>()
            val written = HashMap<Int, Int>()
            // AAC 를 ADTS 머리째 내주는 트랙 — 표본을 프레임으로 갈라 머리를 떼고 담는다.
            val adts = tracks.filter { isAdts(prepared.formats[it]) }.toSet()
            var buffer = ByteBuffer.allocateDirect(INITIAL_BUFFER)
            val info = MediaCodec.BufferInfo()
            var lastUs = offsetUs
            var count = 0
            // 출력 시각의 원점 — 영상이면 키프레임, 소리만이면 처음 쓴 단위의 시각(들고 간 단위면 그것).
            var baseUs = if (audioOnly) -1L else offsetUs
            // 소리만일 때 들고 가는 단위(고른 시각 이하의 마지막 것) — 담을 바이트 그대로다(ADTS 머리는 이미 뗐다).
            var held: ByteBuffer? = null
            var heldUs = -1L
            var heldSize = 0
            var heldFlags = 0

            fun put(track: Int, b: ByteBuffer, offset: Int, size: Int, atUs: Long, flags: Int) {
                info.set(offset, size, atUs - baseUs, if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(muxIndex.getValue(track), b, info)
                written[track] = (written[track] ?: 0) + 1
                if (atUs > lastUs) lastUs = atUs
                onSample(size.toLong(), atUs)
            }

            /** 들고 간 단위를 먼저 쓴다(있으면). */
            fun flushHeld(track: Int) {
                val h = held ?: return
                if (baseUs < 0) baseUs = heldUs
                put(track, h, 0, heldSize, heldUs, heldFlags)
                held = null
            }

            /** 이 단위를 들고 간다(앞서 들던 것은 버린다) — 다음 표본을 읽으면 [b] 가 덮이므로 옮겨 둔다. */
            fun hold(b: ByteBuffer, offset: Int, size: Int, atUs: Long, flags: Int) {
                val h = held?.takeIf { it.capacity() >= size } ?: ByteBuffer.allocateDirect(maxOf(size + size / 4, INITIAL_HELD))
                val from = b.duplicate()
                from.limit(offset + size)
                from.position(offset)
                h.clear()
                h.put(from)
                held = h
                heldUs = atUs
                heldSize = size
                heldFlags = flags
            }

            /** 담을 단위 하나(표본, ADTS 면 그 안의 프레임 하나)를 고른 구간에 맞춰 쓰거나, 들고 가거나, 버린다. */
            fun unit(track: Int, b: ByteBuffer, offset: Int, size: Int, atUs: Long, flags: Int) {
                if (atUs >= endUs) return
                // 키프레임보다 앞의 소리(ADTS 표본 안에서 키프레임 앞에 놓인 프레임 포함).
                if (!audioOnly && atUs < offsetUs) return
                if (audioOnly && atUs <= startUs) {
                    // 아직 고른 시각 이하 — 이 단위를 들고 간다.
                    hold(b, offset, size, atUs, flags)
                    return
                }
                if (audioOnly) {
                    flushHeld(track)
                    if (baseUs < 0) baseUs = atUs
                }
                put(track, b, offset, size, atUs, flags)
            }

            while (done.size < tracks.size) {
                if (count++ % 64 == 0) currentCoroutineContext().ensureActive()
                val track = ex.sampleTrackIndex
                if (track < 0) break
                val t = ex.sampleTime
                val flags = ex.sampleFlags
                if (track !in muxIndex || track in done) {
                    ex.advance()
                    continue
                }
                if (flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) return Outcome(Failure.UNSUPPORTED)
                val isVideo = track == choice.video
                if (t >= endUs) {
                    // 소리는 끝을 넘으면 그 트랙이 끝난다. 영상은 B 프레임 때문에 다음 키프레임까지 본다(머리말). '끝까지' 는 아주 큰
                    // 끝이라 더하면 넘친다 — 빼서 견준다.
                    if (!isVideo || flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0 || t - REORDER_SLACK_US >= endUs) done += track
                    ex.advance()
                    continue
                }
                val framed = track in adts
                // 키프레임보다 앞의 소리, 그리고 열린 GOP 의 앞쪽 B 프레임(앞 GOP 를 참조해 어차피 풀리지 않는다)은 읽지도 않고 버린다.
                // ADTS 표본은 읽어서 가른다 — 키프레임 앞에서 시작한 표본에 키프레임 뒤의 프레임이 들어 있을 수 있다.
                if (!audioOnly && t < offsetUs && !framed) {
                    ex.advance()
                    continue
                }
                // 영상의 첫 프레임은 키프레임이어야 한다 — 키프레임 앞의 시각으로 되감았으니 그래야 하지만, 추출기가 그 앞에 다른
                // 프레임을 내놓으면 결과가 첫 GOP 동안 깨진 화면으로 시작한다. 키프레임이 올 때까지 버린다.
                if (isVideo && (written[track] ?: 0) == 0 && flags and MediaExtractor.SAMPLE_FLAG_SYNC == 0) {
                    ex.advance()
                    continue
                }
                val size = ex.sampleSize.toInt()
                if (size < 0) break
                if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect(size + size / 4)
                buffer.clear()
                val n = ex.readSampleData(buffer, 0)
                if (n < 0) break
                if (framed) {
                    // ADTS 머리째 온 AAC — 프레임으로 갈라 머리를 떼고 담는다. 가를 수 없는 표본이면 담지 않는다 — 깨진 소리를 저장했다고
                    // 말하지 않는다. 프레임의 시각은 표본의 시각(첫 프레임의 것)에서 프레임마다 1024표본씩.
                    val frames = Adts.split(buffer, n) ?: return Outcome(Failure.UNSUPPORTED)
                    val rate = Adts.sampleRate(buffer, n).takeIf { it > 0 } ?: sampleRateOf(prepared.formats[track])
                    if (rate <= 0) return Outcome(Failure.UNSUPPORTED)
                    for ((k, f) in frames.withIndex()) {
                        val atUs = t + k.toLong() * Adts.SAMPLES_PER_FRAME * 1_000_000L / rate
                        unit(track, buffer, f.rawOffset, f.rawLength, atUs, flags)
                    }
                } else {
                    unit(track, buffer, 0, n, t, flags)
                }
                ex.advance()
            }
            // 고른 시각이 마지막 단위 안이었다 — 들고 간 것이 담을 전부다(끝 앞의 것만 들었다).
            if (audioOnly) flushHeld(lead)
            // 영상에 프레임이 하나도 없으면(그 구간이 영상의 끝 너머다) 담을 것이 없다. 소리만이면 그 소리가 그렇다.
            if ((written[lead] ?: 0) == 0) return Outcome(Failure.EMPTY)
            // 영상은 있는데 소리 트랙이 비었다 — 미리 본 길에서는 첫 소리 트랙만 남았을 수 있다(둘째부터는 빼고 왔다). 그릇에 빈 트랙을
            // 남기지 않는다.
            if (tracks.any { (written[it] ?: 0) == 0 }) return Outcome(Failure.EMPTY_AUDIO)
            muxer.stop()
            started = false
            val begin = if (baseUs >= 0) baseUs else offsetUs
            // 실제 끝 — 고른 끝과 미디어 길이 가운데 작은 것(마지막 프레임의 길이는 모른다).
            val end = if (prepared.durationUs > 0) minOf(endUs, prepared.durationUs) else maxOf(lastUs, begin)
            return Outcome(null, begin, end, dropped)
        } finally {
            // 멈추지 못한 그릇은 쓰레기다 — 부르는 쪽이 임시본을 지운다. 놓기는 반드시 한다(네이티브 쓰기 스레드가 있다).
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    /** 추출기가 AAC 표본을 ADTS 머리째 내준다고 말했는가(`is-adts` — TS 추출기가 그렇다). */
    private fun isAdts(f: MediaFormat): Boolean =
        f.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC &&
            f.containsKey(MediaFormat.KEY_IS_ADTS) && f.getInteger(MediaFormat.KEY_IS_ADTS) == 1

    /** 트랙 형식의 표본율 — ADTS 머리가 모르는 번호를 적었을 때 프레임의 길이를 셈한다. 없으면 0. */
    private fun sampleRateOf(f: MediaFormat): Int =
        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0

    /** 그릇에 넘길 형식 — ADTS 표시를 뗀다(그릇에는 머리를 뗀 날것이 들어간다). 원본의 형식은 건드리지 않는다. */
    private fun muxFormatOf(f: MediaFormat): MediaFormat =
        if (f.containsKey(MediaFormat.KEY_IS_ADTS)) MediaFormat(f).apply { removeKey(MediaFormat.KEY_IS_ADTS) } else f

    private fun formatOf(c: RemuxPlan.Container): Int = when (c) {
        RemuxPlan.Container.MP4 -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        RemuxPlan.Container.THREE_GPP -> MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
        RemuxPlan.Container.WEBM -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
        RemuxPlan.Container.OGG -> MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
    }

    /** 추출기가 이름을 주지 않을 때 처음부터 읽을 그릇의 확장자(MPEG-TS·PS). */
    private val SEQUENTIAL_EXTENSIONS = setOf("ts", "m2ts", "mts", "m2t", "tp", "trp", "mpg", "mpeg", "m2p", "vob", "mpe")

    /** [seekAtOrBefore] 가 처음 물러서는 거리 — 넷씩 곱해 늘린다(1초, 4초, 16초…). */
    private const val SEEK_BACKOFF_US = 1_000_000L

    /** [seekAtOrBefore] 가 앞으로 걷는 표본 수의 상한(영상 30 fps 로 약 9분) — 넘으면 그때까지 찾은 키프레임에 선다. */
    private const val MAX_WALK = 16_384

    /** 끝을 넘은 영상 프레임 뒤로 더 보는 거리. 키프레임이 그보다 멀면 여기서 멈춘다. */
    private const val REORDER_SLACK_US = 2_000_000L

    private const val INITIAL_BUFFER = 1 shl 20

    /** 들고 가는 소리 단위의 처음 크기(소리 프레임은 대개 수 KB 다). */
    private const val INITIAL_HELD = 64 * 1024
}
