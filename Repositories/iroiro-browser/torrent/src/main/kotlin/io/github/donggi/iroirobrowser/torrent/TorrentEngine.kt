package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.dht.Dht
import io.github.donggi.iroirobrowser.torrent.meta.MagnetLink
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.net.HttpGet
import io.github.donggi.iroirobrowser.torrent.session.EngineCore
import io.github.donggi.iroirobrowser.torrent.session.EventLoop
import io.github.donggi.iroirobrowser.torrent.session.Notifier
import io.github.donggi.iroirobrowser.torrent.session.TorrentSession
import io.github.donggi.iroirobrowser.torrent.storage.AtomicFiles
import io.github.donggi.iroirobrowser.torrent.storage.ResumeData
import io.github.donggi.iroirobrowser.torrent.tracker.HttpTracker
import io.github.donggi.iroirobrowser.torrent.tracker.UdpTrackerClient
import io.github.donggi.iroirobrowser.torrent.util.Hex
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * **토렌트 받기 엔진** — 앱 안의 순수 코틀린(새 의존 없음). 받기만 하고 **아무것도 올리지 않는다**([TorrentSession] 의 불변식). 토렌트는
 * **프록시를 거치지 않고 기기에서 바로** 나간다(사용자 결정) — 모든 소켓은 [io.github.donggi.iroirobrowser.torrent.net.Transport] 를
 * 지나고 그것은 `Proxy.NO_PROXY` 로만 연다.
 *
 * - 받은 파일은 [root] 아래 `data/<info-hash>/…`(앱 안 저장소), 이어받기 자료와 .torrent 는 `state/`. 다 받은 파일을 공용 '내려받기' 로
 *   옮기는 일은 앱이 한다 — 엔진은 [TorrentListener.onFileCompleted] 로 알리고, 앱은 옮긴 뒤 [releaseFile] 을 부른다.
 * - [start] 가 저장된 토렌트를 되살린다 — 받던 것도 **멈춤**으로(앱이 죽었다 다시 뜬 것으로 본다. 사용자가 이어받는다).
 * - 모든 공개 함수는 어느 스레드에서 불러도 된다(엔진의 이벤트 루프로 넘겨 처리한다 — 바뀌는 일은 그 스레드 하나만 한다).
 */
class TorrentEngine(
    private val root: File,
    private val config: TorrentConfig = TorrentConfig(),
    private val env: TorrentEnvironment = TorrentEnvironment(),
    private val listener: TorrentListener = TorrentListener.NONE,
) : Closeable {

    private val loop = EventLoop(env.clock, env.log)
    private val disk: ExecutorService = Executors.newSingleThreadExecutor(daemon("torrent-disk"))
    private val io: ExecutorService = Executors.newFixedThreadPool(4, daemon("torrent-io"))
    private val webSeedPool: ExecutorService = Executors.newFixedThreadPool(2, daemon("torrent-webseed"))
    private val ownNotifier: ExecutorService? = if (env.callbackExecutor == null) Executors.newSingleThreadExecutor(daemon("torrent-events")) else null
    private val notifyExecutor: Executor = env.callbackExecutor ?: ownNotifier!!
    private val core: EngineCore
    private val view = ConcurrentHashMap<String, TorrentSession>()

    @Volatile
    private var started = false

    @Volatile
    private var closed = false
    private var networkOk = true

    init {
        val http = HttpGet(env.transport, env.resolver, env.addressFilter, env.tls, env.hostnameVerifier, config.userAgent)
        val udp = UdpTrackerClient(loop, env.transport, env.addressFilter, env.random, env.log)
        val dht = if (config.dht) Dht(loop, env.transport, env.resolver, env.addressFilter, io, env.random, config.dhtBootstrap, env.log) else null
        core = EngineCore(
            config = config,
            env = env,
            root = root,
            loop = loop,
            disk = disk,
            io = io,
            webSeedPool = webSeedPool,
            http = http,
            httpTracker = HttpTracker(http),
            udpTracker = udp,
            dht = dht,
            announcePort = 49152 + env.random.nextInt(65535 - 49152),
            notify = Dispatcher(),
        )
        core.onSessionStateChanged = { schedule() }
    }

    // ── 시작과 끝 ───────────────────────────────────────────────────────────

    /**
     * 루프를 세우고 저장된 토렌트를 되살린다(받던 것은 멈춤으로). 되살린 것들의 상태를 돌려준다. 한 번만.
     */
    fun start(): List<TorrentStatus> {
        check(!started) { "already started" }
        started = true
        core.stateDir.mkdirs()
        core.dataRoot.mkdirs()
        loop.start("torrent-net")
        val restored = readSaved()
        return onLoop {
            for (r in restored) {
                if (core.sessions.containsKey(r.id)) continue
                val s = TorrentSession(core, r.infoHash, r.magnet, r.meta, r.resume, null, startPaused = true, addedAtMs = r.resume?.addedAtMs ?: 0L)
                core.sessions[s.id] = s
                view[s.id] = s
                s.open()
                r.failure?.let { s.fail(it.error, it.detail) }
            }
            networkOk = queryNetwork()
            for (s in core.sessions.values) s.setNetworkAllowed(networkOk)
            loop.schedule(NETWORK_CHECK_MS) { networkTick() }
            schedule()
            core.sessions.values.map { it.status }
        }
    }

    private class Restored(
        val id: String,
        val infoHash: ByteArray,
        val meta: Metainfo?,
        val magnet: MagnetLink?,
        val resume: ResumeData?,
        val failure: TorrentException?,
    )

    private fun readSaved(): List<Restored> {
        val files = core.stateDir.listFiles().orEmpty()
        val ids = files.mapNotNull { f ->
            val n = f.name
            when {
                n.endsWith(".resume") -> n.removeSuffix(".resume")
                n.endsWith(".torrent") -> n.removeSuffix(".torrent")
                else -> null
            }
        }.filter { it.length == 40 && Hex.decode(it) != null }.distinct().sorted()
        val out = ArrayList<Restored>()
        for (id in ids) {
            val resume = AtomicFiles.read(File(core.stateDir, "$id.resume"))?.let(ResumeData::decode)
            val torrentBytes = AtomicFiles.read(File(core.stateDir, "$id.torrent"))
            var failure: TorrentException? = null
            val meta = torrentBytes?.let {
                try {
                    Metainfo.parse(it)
                } catch (e: TorrentException) {
                    failure = e
                    null
                }
            }
            if (meta != null && meta.infoHashHex != id) continue
            val magnet = if (meta == null) resume?.magnet?.let { runCatching { MagnetLink.parse(it) }.getOrNull() } else null
            if (meta == null && magnet == null) continue
            val ordered = resume
            out += Restored(id, Hex.decode(id)!!, meta, magnet, ordered, failure)
        }
        // 넣은 차례(더한 시각)대로 — 차례 정하기가 그것을 따른다.
        return out.sortedBy { it.resume?.addedAtMs ?: 0L }
    }

    /** 엔진을 닫는다 — 이어받기 자료를 적고 모든 연결을 끊는다(트래커에 stopped 를 보내려 한다). */
    override fun close() {
        if (closed) return
        closed = true
        if (started && !loop.isClosed) {
            runCatching {
                onLoop {
                    for (s in core.sessions.values) s.shutdown()
                    core.dht?.close()
                }
            }
        }
        disk.shutdown()
        runCatching { disk.awaitTermination(10, TimeUnit.SECONDS) }
        // 멈출 때 보낸 announce(stopped)가 나갈 짧은 틈 — HTTP 는 일꾼 풀에서, UDP 는 루프에서(루프는 아직 돈다).
        io.shutdown()
        runCatching { io.awaitTermination(3, TimeUnit.SECONDS) }
        io.shutdownNow()
        webSeedPool.shutdownNow()
        loop.close()
        ownNotifier?.shutdown()
    }

    // ── 넣기 ────────────────────────────────────────────────────────────────

    /** .torrent 바이트를 읽기만 한다(넣지 않는다) — '묻기' 화면용. */
    fun inspect(torrent: ByteArray): TorrentInfo = TorrentInfo(Metainfo.parse(torrent))

    /** 마그넷 주소를 읽기만 한다. */
    fun inspectMagnet(uri: String): MagnetLink = MagnetLink.parse(uri)

    /**
     * .torrent 를 넣는다. [selectedFiles] 가 null 이면 기본 고르기(패드·심볼릭 링크 빼고 모두). 넣은 토렌트의 id(info-hash 16진)를 돌려준다.
     * 받지 않는 것(private·순수 v2·깨진 것·이미 있는 것)은 [TorrentException].
     */
    fun add(torrent: ByteArray, selectedFiles: Set<Int>? = null, paused: Boolean = false): String {
        ensureOpen()
        val meta = Metainfo.parse(torrent)
        return onLoop {
            if (core.sessions.containsKey(meta.infoHashHex)) throw TorrentException(TorrentError.DUPLICATE)
            val s = TorrentSession(core, meta.infoHash, null, meta, null, selectedFiles, paused, System.currentTimeMillis())
            core.sessions[s.id] = s
            view[s.id] = s
            val file = s.torrentFile()
            val bytes = torrent.copyOf()
            disk.execute { runCatching { AtomicFiles.write(file, bytes) } }
            s.open()
            s.setNetworkAllowed(networkOk)
            s.saveResume()
            schedule()
            s.id
        }
    }

    /** 마그넷을 넣는다. 메타데이터는 피어에게서 받는다(BEP 9). */
    fun addMagnet(uri: String, paused: Boolean = false): String {
        ensureOpen()
        val magnet = MagnetLink.parse(uri)
        return onLoop {
            if (core.sessions.containsKey(magnet.infoHashHex)) throw TorrentException(TorrentError.DUPLICATE)
            val s = TorrentSession(core, magnet.infoHash, magnet, null, null, null, paused, System.currentTimeMillis())
            core.sessions[s.id] = s
            view[s.id] = s
            s.open()
            s.setNetworkAllowed(networkOk)
            s.saveResume()
            schedule()
            s.id
        }
    }

    // ── 다루기 ──────────────────────────────────────────────────────────────

    fun selectFiles(id: String, files: Set<Int>) = withSession(id) { it.selectFiles(files) }

    fun pause(id: String) = withSession(id) {
        it.pause()
        schedule()
    }

    fun resume(id: String) = withSession(id) {
        it.resume()
        schedule()
    }

    /** 앱이 다 받은 파일을 옮겼다 — 엔진은 그 파일을 다시 읽거나 쓰지 않는다. */
    fun releaseFile(id: String, fileIndex: Int) = withSession(id) { it.releaseFile(fileIndex) }

    /** 지운다. [deleteData] 면 앱 안에 받아 둔 파일도(옮겨 간 파일은 이미 엔진의 것이 아니다). */
    fun remove(id: String, deleteData: Boolean) {
        ensureOpen()
        onLoop {
            val s = core.sessions.remove(id) ?: throw TorrentException(TorrentError.UNKNOWN_TORRENT)
            view.remove(id)
            s.remove(deleteData) {}
            schedule()
        }
    }

    /**
     * 이어받기 자료를 지금 적는다(받은 것을 디스크까지 내린 뒤) — 앱이 뒤로 가거나 서비스를 멈출 때. 엔진은 30초마다·멈출 때·파일을
     * 다 받을 때 스스로도 적는다.
     */
    fun saveNow() {
        ensureOpen()
        onLoop { for (s in core.sessions.values) s.saveResume() }
    }

    fun status(id: String): TorrentStatus? = view[id]?.status

    fun statuses(): List<TorrentStatus> = view.values.map { it.status }.sortedBy { it.addedAtMs }

    /** 메타데이터(마그넷이면 받은 뒤에). */
    fun info(id: String): TorrentInfo? = view[id]?.meta?.let { TorrentInfo(it) }

    /** 앱 안에 받는 그 파일의 자리(다 받기 전에도). */
    fun fileOf(id: String, fileIndex: Int): File? {
        val meta = view[id]?.meta ?: return null
        if (fileIndex !in meta.files.indices) return null
        var f = File(core.dataRoot, id)
        for (c in meta.files[fileIndex].path) f = File(f, c)
        return f
    }

    /** 망이 바뀌었다 — 망 조건 훅을 곧바로 다시 묻는다. */
    fun networkChanged() {
        if (closed || !started) return
        loop.post { applyNetwork(queryNetwork()) }
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private fun withSession(id: String, block: (TorrentSession) -> Unit) {
        ensureOpen()
        onLoop {
            val s = core.sessions[id] ?: throw TorrentException(TorrentError.UNKNOWN_TORRENT)
            block(s)
        }
    }

    /** 동시 토렌트 수에 맞춰 받을 것을 정한다(넣은 차례대로). */
    private fun schedule() {
        var running = 0
        for (s in core.sessions.values) {
            val run = s.wantsToRun && running < config.maxActiveTorrents
            if (run) {
                if (!s.checkSpace()) continue
                running++
            }
            s.setAdmitted(run)
        }
    }

    private fun networkTick() {
        if (loop.isClosed) return
        applyNetwork(queryNetwork())
        loop.schedule(NETWORK_CHECK_MS) { networkTick() }
    }

    private fun applyNetwork(ok: Boolean) {
        if (ok == networkOk) return
        networkOk = ok
        for (s in core.sessions.values) s.setNetworkAllowed(ok)
    }

    /** 훅이 예외를 던지면 '안 됨' 으로 본다(망 조건이 확실할 때만 나간다). */
    private fun queryNetwork(): Boolean = try {
        env.networkAllowed()
    } catch (e: Exception) {
        env.log("networkAllowed failed: $e")
        false
    }

    private fun ensureOpen() {
        if (closed) throw TorrentException(TorrentError.ENGINE_CLOSED)
        check(started) { "call start() first" }
    }

    /** 루프에서 돌려 결과를 기다린다(루프 스레드에서 부르면 바로). */
    private fun <T> onLoop(block: () -> T): T {
        if (loop.inLoop()) return block()
        val task = FutureTask(block)
        loop.post(task)
        try {
            return task.get(30, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            throw TorrentException(TorrentError.ENGINE_CLOSED, "engine not responding")
        }
    }

    private inner class Dispatcher : Notifier {
        private fun run(block: () -> Unit) {
            try {
                notifyExecutor.execute {
                    try {
                        block()
                    } catch (e: Exception) {
                        env.log("listener failed: $e")
                    }
                }
            } catch (_: Exception) {
                // 닫히는 중
            }
        }

        override fun status(s: TorrentStatus) = run { listener.onStatus(s) }
        override fun metadata(id: String, info: TorrentInfo) = run { listener.onMetadata(id, info) }
        override fun fileCompleted(id: String, index: Int, file: File) = run { listener.onFileCompleted(id, index, file) }
        override fun finished(id: String) = run { listener.onFinished(id) }
        override fun error(id: String, error: TorrentError, detail: String?) = run { listener.onError(id, error, detail) }
    }

    companion object {
        const val NETWORK_CHECK_MS = 5_000L

        private fun daemon(name: String): ThreadFactory {
            val n = AtomicInteger()
            return ThreadFactory { r -> Thread(r, "$name-${n.incrementAndGet()}").apply { isDaemon = true } }
        }
    }
}
