package io.github.donggi.iroirobrowser.browser

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import io.github.donggi.iroirobrowser.core.media.MseSegments
import io.github.donggi.iroirobrowser.download.DownloadCenter
import io.github.donggi.iroirobrowser.download.Remux
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/**
 * **가로채기**(MSE) — 쪽의 재생기가 `SourceBuffer` 에 넣는 바이트를 버퍼마다 파일에 잇는다. 쓰기는 일꾼 하나가 차례로 한다(받는 것은 메인 스레드).
 *
 * 버퍼 하나는 여러 **덩이**(run)로 나뉜다 — 초기 조각(`ftyp`/`moov`·EBML 머리)이 올 때와 `timestampOffset` 이 바뀔 때 새 덩이를 연다. 초기 조각
 * 없이 `timestampOffset` 만 바뀌면 마지막 초기 조각을 앞에 다시 적어 덩이마다 홀로 읽히게 한다.
 *
 * **쓰기가 밀리면 버린다**(받은 만큼만): 아직 쓰지 못한 바이트가 [MAX_PENDING] 을 넘으면 초기 조각이 아닌 덩이는 버리고 [dropped] 에 센다 — 앞
 * 판은 대기열에 끝이 없어 쪽이 디스크보다 빨리 넣으면 메모리가 바닥날 수 있었다. 초기 조각은 버리지 않는다(버리면 뒤 조각들이 앞
 * 화질의 초기 조각으로 읽혀 깨진다).
 *
 * **영상마다**(MediaSource — 같은 사이트 안에서 다음 영상으로 가면 새 것이다) 처음 잡힌 때와 그때의 페이지 제목을 적는다([Clip]). 저장할 때
 * 고르고 그 제목이 이름이 된다(앞 판은 가장 큰 하나만, 저장하는 때의 페이지 제목으로 저장했다). 막대에는 저장하면 담길 크기를
 * 보인다([savables]).
 *
 * **되살리기**: 무엇을 어디에 썼는지(버퍼·덩이·영상의 제목)를 폴더의 `meta.json` 에 적어 둔다(바뀔 때, 많아야 [META_MS] 에 한 번). 시스템이
 * 앱을 정리해도 다음에 뜰 때 그것으로 저장한다([restore]).
 *
 * 저장([job])은 일꾼에서: 고른 영상(없으면 담길 것이 가장 많은 것)의 버퍼마다 **같은 초기 조각의 덩이들**(같은 화질 — 바이트가 가장 많은 무리)만
 * 첫 시각 차례로 담으며, 같은 시각의 표본은 한 번만 담는다(되감아 다시 받은 조각). 화질이 바뀐 구간은 빠진다.
 */
@OptIn(UnstableApi::class)
class CaptureSession(
    val dir: File,
    /** 가로채기를 건 사이트(등록 도메인 어림) — 그 사이트를 떠나면 가로채기가 끝난다([PageBridge]). */
    val site: String,
    private val trace: (String) -> Unit = {},
) {

    class Buffer(val sb: Int, val ms: String, mime: String, val file: File) {
        class Run(val start: Long, var length: Long, val tsoUs: Long, val initKey: Int)

        /** 일꾼만 만진다(저장은 일꾼이 남은 쓰기를 다 한 뒤에 읽는다). */
        val runs = ArrayList<Run>()

        @Volatile var mime: String = mime
        @Volatile var bytes = 0L

        /** 저장하면 담길 바이트 — 같은 초기 조각 무리 가운데 가장 큰 것. 일꾼이 고치고 막대가 읽는다. */
        @Volatile var savable = 0L

        internal var out: FileOutputStream? = null
        internal var lastInit: ByteArray? = null
        internal var lastTsoUs = Long.MIN_VALUE
        private val groups = HashMap<Int, Long>()

        val isVideo: Boolean get() = mime.startsWith("video/")
        val needsInit: Boolean get() = !(mime.startsWith("audio/mpeg") || mime.startsWith("audio/aac"))

        /** 영상 버퍼의 MIME 에 소리 코덱도 있는가(한 버퍼에 섞인 것). */
        val hasAudioCodec: Boolean
            get() = Regex("""\b(mp4a|opus|vorbis|ac-3|ec-3|flac)""").containsMatchIn(mime)

        internal fun grow(run: Run, n: Long) {
            run.length += n
            val total = (groups[run.initKey] ?: 0L) + n
            groups[run.initKey] = total
            if (total > savable) savable = total
        }
    }

    /** 영상 하나(MediaSource) — 처음 잡힌 때(벽시계)와 그때의 페이지 제목. */
    class Clip(val ms: String, val title: String, val firstAt: Long)

    /** 저장하면 담길 것 — 영상 하나의 영상·소리 바이트. */
    class Savable(val clip: Clip, val video: Long, val audio: Long) {
        val total: Long get() = video + audio
    }

    val buffers = ConcurrentHashMap<Int, Buffer>()
    val clips = ConcurrentHashMap<String, Clip>()

    @Volatile var drm = false
    @Volatile var closed = false

    /** 쓰기가 밀려 버린 바이트(메인 스레드가 센다). */
    @Volatile var dropped = 0L
        private set

    val started = SystemClock.elapsedRealtime()

    private val pending = AtomicLong()
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "web-capture").apply { isDaemon = true } }

    /** 마지막으로 `meta.json` 을 적은 때 — 일꾼. */
    private var metaAt = 0L

    init {
        dir.mkdirs()
    }

    /** 버퍼가 생겼다. [title] 은 지금 페이지의 제목 — 그 영상이 처음 잡힌 때의 것으로 남는다(저장할 이름). */
    fun addBuffer(frame: String, ms: Int, sb: Int, mime: String, title: String) {
        val key = "$frame:$ms"
        clips.putIfAbsent(key, Clip(key, title, System.currentTimeMillis()))
        if (buffers.putIfAbsent(sb, Buffer(sb, key, mime, File(dir, "sb-$sb.bin"))) == null) execute { saveMeta() }
    }

    fun changeType(sb: Int, mime: String) {
        buffers[sb]?.let { b ->
            execute {
                b.mime = mime
                b.lastInit = null
            }
        }
    }

    /**
     * 덩이 하나를 쓰기에 넘긴다(메인 스레드). 쓰기가 [MAX_PENDING] 넘게 밀렸으면 초기 조각이 아닌 것은 버린다 — 이번에 처음 버리기 시작했으면
     * true(부르는 쪽이 사용자에게 한 번 알린다).
     */
    fun append(sb: Int, tsoSeconds: Double, data: ByteArray, offset: Int): Boolean {
        if (closed) return false
        val length = (data.size - offset).toLong()
        if (length <= 0) return false
        if (pending.get() + length > MAX_PENDING) {
            val b = buffers[sb]
            val init = b != null && b.needsInit && MseSegments.initLength(b.mime, data, offset) > 0
            if (!init) {
                val first = dropped == 0L
                dropped += length
                return first
            }
        }
        pending.addAndGet(length)
        val queued = execute {
            try {
                val b = buffers[sb]
                if (b == null) trace("capture data for unknown sb=$sb") else write(b, tsoSeconds, data, offset)
            } catch (e: IOException) {
                // 쓰지 못한 것은 버린다(저장소가 찼다 등) — 저장할 때 받은 만큼만 담는다.
                trace("capture write failed ${e.javaClass.simpleName}")
            } catch (e: RuntimeException) {
                trace("capture write failed ${e.javaClass.simpleName} ${e.message}")
            } finally {
                pending.addAndGet(-length)
            }
        }
        if (!queued) pending.addAndGet(-length)
        return false
    }

    private fun write(b: Buffer, tsoSeconds: Double, data: ByteArray, offset: Int) {
        val length = data.size - offset
        if (length <= 0) return
        val out = b.out ?: FileOutputStream(b.file, true).also { b.out = it }
        val tsoUs = if (tsoSeconds.isFinite()) (tsoSeconds * 1_000_000).toLong() else 0L
        val initLength = if (b.needsInit) MseSegments.initLength(b.mime, data, offset) else 0
        val current = b.runs.lastOrNull()
        var opened = true
        when {
            initLength > 0 -> {
                b.lastInit = data.copyOfRange(offset, offset + initLength)
                b.runs += Buffer.Run(b.bytes, 0, tsoUs, b.lastInit.contentHashCode())
            }
            !b.needsInit && current == null -> b.runs += Buffer.Run(b.bytes, 0, tsoUs, 0)
            current == null || (b.needsInit && tsoUs != current.tsoUs) -> {
                // 초기 조각 없이 새 덩이 — 마지막 초기 조각을 앞에 다시 적는다(없으면 읽을 수 없으니 버린다).
                val init = b.lastInit ?: return
                b.runs += Buffer.Run(b.bytes, 0, tsoUs, init.contentHashCode())
                out.write(init)
                b.bytes += init.size
                b.grow(b.runs.last(), init.size.toLong())
            }
            else -> opened = false
        }
        out.write(data, offset, length)
        b.bytes += length
        b.grow(b.runs.last(), length.toLong())
        b.lastTsoUs = tsoUs
        if (opened || SystemClock.elapsedRealtime() - metaAt >= META_MS) saveMeta()
    }

    /** 바이트가 버퍼마다 얼마인가 — (영상, 소리). */
    fun totals(): Pair<Long, Long> {
        var video = 0L
        var audio = 0L
        for (b in buffers.values) if (b.isVideo) video += b.bytes else audio += b.bytes
        return video to audio
    }

    /** 저장하면 담길 것 — 영상(MediaSource)마다, 많은 것부터. 아직 담긴 것이 없으면 빈 목록. */
    fun savables(): List<Savable> = buffers.values.groupBy { it.ms }.mapNotNull { (ms, list) ->
        val video = list.filter { it.isVideo }.maxOfOrNull { it.savable } ?: 0L
        val audio = list.filter { !it.isVideo }.maxOfOrNull { it.savable } ?: 0L
        if (video + audio <= 0L) null else Savable(clips[ms] ?: Clip(ms, "", 0L), video, audio)
    }.sortedByDescending { it.total }

    /** 저장 — 일꾼에서 부른다(덩이의 첫 시각을 읽는다). [ms] 는 고른 영상(없으면 담길 것이 가장 많은 것). */
    fun job(ms: String?, audioOnly: Boolean): DownloadCenter.RemuxJob {
        // 쓰기가 다 끝나기를 기다리고 일꾼을 닫는다.
        if (!writer.isShutdown) writer.submit { closeFiles(); saveMeta() }.get()
        writer.shutdown()
        val pick = ms ?: savables().firstOrNull()?.clip?.ms
        val chosen = buffers.values.filter { it.bytes > 0 && it.ms == pick }
        val video = chosen.filter { it.isVideo }.maxByOrNull { it.savable }
        val audio = chosen.filter { !it.isVideo }.maxByOrNull { it.savable } ?: video?.takeIf { it.hasAudioCodec }
        val sources = listOfNotNull(
            if (audioOnly) null else video?.let { source(it, C.TRACK_TYPE_VIDEO) },
            audio?.let { source(it, C.TRACK_TYPE_AUDIO) },
        )
        val fallback = listOfNotNull(if (audioOnly) null else video, audio?.takeIf { it !== video }).mapNotNull { fallbackOf(it) }
        return DownloadCenter.RemuxJob(sources, fallback, dir)
    }

    private fun containerOf(b: Buffer): Remux.Container = when {
        b.mime.contains("webm") -> Remux.Container.WEBM
        b.mime.startsWith("audio/mpeg") -> Remux.Container.MP3
        b.mime.startsWith("audio/aac") -> Remux.Container.ADTS
        else -> Remux.Container.FMP4
    }

    /** 버퍼 하나의 줄기 — 같은 초기 조각 무리 가운데 바이트가 가장 많은 것, 첫 시각 차례, 같은 시각은 한 번. */
    private fun source(b: Buffer, type: Int): Remux.Source {
        val container = containerOf(b)
        val group = b.runs.groupBy { it.initKey }.maxByOrNull { (_, runs) -> runs.sumOf { it.length } }?.value ?: emptyList()
        val runs = group.map { Remux.Run(b.file, container, it.start, it.length, it.tsoUs) }
            .map { it to (Remux.firstTimeUs(it, type) ?: Long.MAX_VALUE) }
            .filter { it.second != Long.MAX_VALUE }
            .sortedBy { it.second }
            .map { it.first }
        val seen = HashSet<Long>()
        return Remux.Source(runs, type, skip = { _, timeUs -> !seen.add(timeUs) })
    }

    /** 담지 못할 때 남길 것 — 그 버퍼의 가장 긴 덩이를 그대로(초기 조각 + 조각들 — 홀로 재생된다). */
    private fun fallbackOf(b: Buffer): DownloadCenter.RemuxJob.Fallback? {
        val run = b.runs.maxByOrNull { it.length } ?: return null
        val ext = when (containerOf(b)) {
            Remux.Container.WEBM -> "webm"
            Remux.Container.MP3 -> "mp3"
            Remux.Container.ADTS -> "aac"
            else -> if (b.isVideo) "mp4" else "m4a"
        }
        val f = File(dir, "fallback-${b.sb}.$ext")
        RandomAccessFile(b.file, "r").use { raf ->
            FileOutputStream(f).use { out ->
                raf.seek(run.start)
                val buffer = ByteArray(256 * 1024)
                var left = run.length
                while (left > 0) {
                    val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
        }
        val mime = when (ext) {
            "webm" -> if (b.isVideo) "video/webm" else "audio/webm"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            else -> "video/mp4"
        }
        return DownloadCenter.RemuxJob.Fallback(f, ext, mime, audio = !b.isVideo)
    }

    /** 버린다(저장하지 않고). */
    fun discard() {
        closed = true
        if (!execute { closeFiles(); dir.deleteRecursively() }) dir.deleteRecursively()
        writer.shutdown()
    }

    /** 저장으로 넘긴다 — 더 받지 않는다(남은 쓰기는 [job] 이 기다린 뒤 일꾼을 닫는다. 파일은 저장 일이 지운다). */
    fun close() {
        closed = true
    }

    /** 화면이 사라진다 — 더 받지 않고 **파일은 남긴다**(다음에 뜰 때 저장할지 묻는다 — [CaptureRecovery]). */
    fun release() {
        closed = true
        execute { closeFiles(); saveMeta() }
        writer.shutdown()
    }

    private fun closeFiles() {
        buffers.values.forEach {
            runCatching { it.out?.close() }
            it.out = null
        }
    }

    /** 무엇을 어디에 썼는지 — 일꾼에서. */
    private fun saveMeta() {
        metaAt = SystemClock.elapsedRealtime()
        val clipArray = JSONArray()
        clips.values.forEach { clipArray.put(JSONObject().put("ms", it.ms).put("title", it.title).put("at", it.firstAt)) }
        val bufferArray = JSONArray()
        buffers.values.forEach { b ->
            val runArray = JSONArray()
            b.runs.forEach { r -> runArray.put(JSONArray().put(r.start).put(r.length).put(r.tsoUs).put(r.initKey)) }
            bufferArray.put(JSONObject().put("sb", b.sb).put("ms", b.ms).put("mime", b.mime).put("file", b.file.name).put("runs", runArray))
        }
        writeMeta(dir, JSONObject().put("kind", KIND).put("site", site).put("clips", clipArray).put("buffers", bufferArray))
    }

    /** 일꾼에 넘긴다 — 일꾼이 이미 닫혔으면 false. */
    private fun execute(task: () -> Unit): Boolean = try {
        writer.execute(task)
        true
    } catch (e: RejectedExecutionException) {
        false
    }

    companion object {
        /** 쓰지 못하고 기다리는 바이트의 상한 — 넘으면 버린다(머리말). */
        const val MAX_PENDING = 48L * 1024 * 1024

        private const val META_MS = 1_000L
        private const val KIND = "capture"

        /**
         * 앞 프로세스가 남긴 가로채기를 `meta.json` 으로 되살린다 — 가로채기가 아니거나 읽지 못하면 null. 덩이는 적힌 길이만큼만 담는다(적은 뒤에
         * 쓴 것은 다음 적기 전에 끊겼다 — 파일이 더 짧으면 그만큼).
         */
        fun restore(dir: File): CaptureSession? {
            val o = readMeta(dir) ?: return null
            if (o.optString("kind") != KIND) return null
            val s = CaptureSession(dir, o.optString("site"))
            s.closed = true
            o.optJSONArray("clips")?.let { a ->
                for (i in 0 until a.length()) {
                    val c = a.optJSONObject(i) ?: continue
                    val ms = c.optString("ms")
                    s.clips[ms] = Clip(ms, c.optString("title"), c.optLong("at"))
                }
            }
            val buffers = o.optJSONArray("buffers") ?: return null
            for (i in 0 until buffers.length()) {
                val bo = buffers.optJSONObject(i) ?: continue
                // 폴더 밖을 가리키지 않게 이름만.
                val file = File(dir, File(bo.optString("file")).name)
                if (!file.isFile) continue
                val b = Buffer(bo.optInt("sb"), bo.optString("ms"), bo.optString("mime"), file)
                val size = file.length()
                val runs = bo.optJSONArray("runs") ?: continue
                for (k in 0 until runs.length()) {
                    val r = runs.optJSONArray(k) ?: continue
                    val start = r.optLong(0)
                    val length = minOf(r.optLong(1), size - start)
                    if (start < 0 || length <= 0) continue
                    val run = Buffer.Run(start, 0, r.optLong(2), r.optInt(3))
                    b.runs += run
                    b.grow(run, length)
                    b.bytes = maxOf(b.bytes, start + length)
                }
                if (b.runs.isNotEmpty()) s.buffers[b.sb] = b
            }
            return s
        }
    }
}

/**
 * **실시간 녹화** — 쪽의 MediaRecorder 가 1초마다 넘기는 조각난 그릇을 파일 하나에 잇는다. 저장은 [job] — 길이와 찾기 표가 있는 그릇으로 다시 담는다.
 *
 * 쓰기가 [CaptureSession.MAX_PENDING] 넘게 밀리면 버린다(첫 덩이 — 그릇의 머리 — 는 버리지 않는다). '녹화 중 · 1:23' 은 **실제로 담긴 시간**이다 —
 * 요소가 멈추면 녹화도 멈추고 쪽이 그 사실과 그때까지 담긴 시간을 알린다([onState] — 앞 판은 '지금 − 시작' 이라 멈춰 있던 시간까지 셌다).
 * 무엇을 녹화하는지는 `meta.json` 에 적어, 시스템이 앱을 정리해도 다음에 뜰 때 저장한다([restore]).
 */
@OptIn(UnstableApi::class)
class RecordingSession(
    val dir: File,
    val id: Int,
    val frame: String,
    val mime: String,
    val audioOnly: Boolean,
    val hasVideo: Boolean,
    /** 녹화를 시작한 때의 페이지 제목 — 저장할 이름. */
    val title: String,
) {

    val file = File(dir, "recording.part")
    val started = SystemClock.elapsedRealtime()

    @Volatile var bytes = 0L
    @Volatile var stopping = false
    @Volatile var closed = false

    /** 쓰기가 밀려 버린 바이트(메인 스레드가 센다). */
    @Volatile var dropped = 0L
        private set

    /** 요소가 멈춰 녹화도 멈췄다. */
    @Volatile var paused = false
        private set

    @Volatile private var recordedMs = 0L
    @Volatile private var resumedAt = started

    /** 첫 덩이를 받았다 — 메인 스레드. */
    private var accepted = false
    private val pending = AtomicLong()
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "web-record").apply { isDaemon = true } }
    private var out: FileOutputStream? = null

    init {
        dir.mkdirs()
        execute {
            writeMeta(
                dir,
                JSONObject().put("kind", KIND).put("mime", mime).put("audioOnly", audioOnly).put("hasVideo", hasVideo).put("title", title),
            )
        }
    }

    /** 쪽이 녹화를 멈췄거나(요소가 멈췄다) 다시 시작했다 — [ms] 는 그때까지 실제로 담긴 시간. 메인 스레드. */
    fun onState(paused: Boolean, ms: Long) {
        recordedMs = ms
        resumedAt = SystemClock.elapsedRealtime()
        this.paused = paused
    }

    /** 실제로 담긴 시간(밀리초). */
    fun recordedMs(now: Long = SystemClock.elapsedRealtime()): Long = if (paused) recordedMs else recordedMs + (now - resumedAt)

    /** 덩이 하나를 쓰기에 넘긴다(메인 스레드). 쓰기가 밀려 이번에 처음 버리기 시작했으면 true. */
    fun append(data: ByteArray, offset: Int): Boolean {
        if (closed) return false
        val length = (data.size - offset).toLong()
        if (length <= 0) return false
        if (accepted && pending.get() + length > CaptureSession.MAX_PENDING) {
            val first = dropped == 0L
            dropped += length
            return first
        }
        accepted = true
        pending.addAndGet(length)
        val queued = execute {
            try {
                val o = out ?: FileOutputStream(file, true).also { out = it }
                o.write(data, offset, length.toInt())
                bytes += length
            } catch (e: IOException) {
                // 버린다.
            } finally {
                pending.addAndGet(-length)
            }
        }
        if (!queued) pending.addAndGet(-length)
        return false
    }

    private val container: Remux.Container get() = if (mime.contains("webm")) Remux.Container.WEBM else Remux.Container.FMP4

    val extension: String get() = if (container == Remux.Container.WEBM) "webm" else if (audioOnly || !hasVideo) "m4a" else "mp4"

    /** 저장 — 일꾼에서 부른다. */
    fun job(): DownloadCenter.RemuxJob {
        if (!writer.isShutdown) writer.submit { closeFile() }.get()
        writer.shutdown()
        val run = Remux.Run(file, container)
        val sources = listOfNotNull(
            if (audioOnly || !hasVideo) null else Remux.Source(listOf(run), C.TRACK_TYPE_VIDEO),
            Remux.Source(listOf(run), C.TRACK_TYPE_AUDIO),
        )
        val raw = DownloadCenter.RemuxJob.Fallback(file, extension, mime.substringBefore(';'), audio = audioOnly || !hasVideo)
        return DownloadCenter.RemuxJob(sources, listOf(raw), dir)
    }

    fun discard() {
        closed = true
        if (!execute { closeFile(); dir.deleteRecursively() }) dir.deleteRecursively()
        writer.shutdown()
    }

    /** 저장으로 넘긴다 — 더 받지 않는다(남은 쓰기는 [job] 이 기다린다). */
    fun close() {
        closed = true
    }

    /** 화면이 사라진다 — 더 받지 않고 **파일은 남긴다**(다음에 뜰 때 저장할지 묻는다 — [CaptureRecovery]). */
    fun release() {
        closed = true
        execute { closeFile() }
        writer.shutdown()
    }

    private fun closeFile() {
        runCatching { out?.close() }
        out = null
    }

    private fun execute(task: () -> Unit): Boolean = try {
        writer.execute(task)
        true
    } catch (e: RejectedExecutionException) {
        false
    }

    companion object {
        private const val KIND = "recording"

        /** 앞 프로세스가 남긴 녹화를 `meta.json` 으로 되살린다 — 녹화가 아니거나 읽지 못하면 null. */
        fun restore(dir: File): RecordingSession? {
            val o = readMeta(dir) ?: return null
            if (o.optString("kind") != KIND) return null
            val file = File(dir, "recording.part")
            if (!file.isFile) return null
            return RecordingSession(dir, 0, "", o.optString("mime"), o.optBoolean("audioOnly"), o.optBoolean("hasVideo", true), o.optString("title"))
                .apply {
                    bytes = file.length()
                    closed = true
                    // 더 쓸 것이 없다 — 머리말을 다시 적는 일만 끝내고 일꾼을 내린다(버리면 일꾼이 남지 않게).
                    writer.shutdown()
                }
        }
    }
}

/** 녹화·가로채기의 폴더에 두는 메모 — 무엇을 어디에 썼는지(되살리기). */
private const val META = "meta.json"

/** `meta.json` 을 통째로 바꿔 쓴다 — 임시 파일에 쓰고 이름을 바꾼다(도중에 죽어도 앞 것이 남는다). 쓰지 못하면 그만이다(되살리기만 못 한다). */
private fun writeMeta(dir: File, o: JSONObject) {
    val tmp = File(dir, "$META.tmp")
    try {
        tmp.writeText(o.toString())
        if (!tmp.renameTo(File(dir, META))) tmp.delete()
    } catch (e: IOException) {
        tmp.delete()
    }
}

private fun readMeta(dir: File): JSONObject? = try {
    JSONObject(File(dir, META).readText())
} catch (e: IOException) {
    null
} catch (e: JSONException) {
    null
}
