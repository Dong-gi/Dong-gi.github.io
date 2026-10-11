package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.net.Clock
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.net.Transport
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** 어디에 붙으려 했는지 적는 소켓 공장. UDP 는 루프백에 묶는다(시험이 바깥 망에 귀를 열지 않게). */
class RecordingTransport(private val inner: Transport = Transport.DIRECT) : Transport {
    val tcp = CopyOnWriteArrayList<InetSocketAddress>()
    val blocking = CopyOnWriteArrayList<InetSocketAddress>()
    val udp = CopyOnWriteArrayList<InetSocketAddress>()

    override fun connectTcp(target: InetSocketAddress): SocketChannel {
        tcp += target
        return inner.connectTcp(target)
    }

    override fun connectBlocking(target: InetSocketAddress, timeoutMs: Int): Socket {
        blocking += target
        return inner.connectBlocking(target, timeoutMs)
    }

    override fun openUdp(): DatagramChannel {
        val ch = DatagramChannel.open()
        ch.configureBlocking(false)
        ch.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        return ch
    }

    override fun sendUdp(channel: DatagramChannel, data: ByteBuffer, target: InetSocketAddress): Int {
        udp += target
        return inner.sendUdp(channel, data, target)
    }

    /** 루프백의 허락한 포트·공용 주소 밖으로 붙으려 한 적이 있는가. */
    fun touched(host: String): Boolean = (tcp + blocking + udp).any { it.address.hostAddress == host }
}

/** 엔진 하나와 그 둘레 — 듣개의 소식을 적는다. */
class EngineHarness(
    val clock: Clock = Clock.SYSTEM,
    val config: TorrentConfig = TorrentConfig(dht = false),
    val filter: LoopbackFilter = LoopbackFilter(),
    resolver: Resolver = Resolver.SYSTEM,
    seed: Long = 1,
    val root: File = tempDir("torrent-engine"),
    start: Boolean = true,
) : Closeable {
    val transport = RecordingTransport()
    val network = AtomicBoolean(true)
    val logs = CopyOnWriteArrayList<String>()
    val events = CopyOnWriteArrayList<String>()
    val completedFiles = CopyOnWriteArrayList<Pair<Int, File>>()
    val finished = CopyOnWriteArrayList<String>()
    val errors = CopyOnWriteArrayList<Pair<TorrentError, String?>>()
    val statuses = CopyOnWriteArrayList<TorrentStatus>()

    private val listener = object : TorrentListener {
        override fun onStatus(status: TorrentStatus) {
            statuses += status
        }

        override fun onMetadata(id: String, info: TorrentInfo) {
            events += "metadata ${info.files.size}"
        }

        override fun onFileCompleted(id: String, fileIndex: Int, file: File) {
            completedFiles += fileIndex to file
            events += "file $fileIndex"
        }

        override fun onFinished(id: String) {
            finished += id
            events += "finished"
        }

        override fun onError(id: String, error: TorrentError, detail: String?) {
            errors += error to detail
            events += "error $error"
        }
    }

    val engine = TorrentEngine(
        root,
        config,
        TorrentEnvironment(
            clock = clock,
            random = Random(seed),
            transport = transport,
            resolver = resolver,
            addressFilter = filter,
            tls = null,
            networkAllowed = { network.get() },
            callbackExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "test-events").apply { isDaemon = true } },
            log = { logs += it },
        ),
        listener,
    )

    init {
        if (start) engine.start()
    }

    fun allow(vararg ports: Int) {
        filter.allowed.addAll(ports.toList())
    }

    fun status(id: String): TorrentStatus = engine.status(id) ?: error("no torrent $id")

    fun awaitState(id: String, state: TorrentState, timeoutMs: Long = 15_000) {
        waitUntil(timeoutMs, what = "$state (now ${engine.status(id)?.state} ${engine.status(id)?.errorDetail})") { engine.status(id)?.state == state }
    }

    fun awaitDone(id: String, timeoutMs: Long = 20_000) {
        waitUntil(timeoutMs, what = "completion (now ${engine.status(id)?.let { "${it.state} ${it.doneBytes}/${it.wantedBytes} peers ${it.connectedPeers}" }})") {
            engine.status(id)?.state == TorrentState.COMPLETED
        }
    }

    override fun close() {
        runCatching { engine.close() }
        root.deleteRecursively()
    }
}
