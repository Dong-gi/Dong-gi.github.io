package io.github.donggi.iroirobrowser.torrent.session

import io.github.donggi.iroirobrowser.torrent.TorrentConfig
import io.github.donggi.iroirobrowser.torrent.TorrentEnvironment
import io.github.donggi.iroirobrowser.torrent.dht.Dht
import io.github.donggi.iroirobrowser.torrent.net.HttpGet
import io.github.donggi.iroirobrowser.torrent.tracker.HttpTracker
import io.github.donggi.iroirobrowser.torrent.tracker.UdpTrackerClient
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong

/**
 * 세션들이 함께 쓰는 것 — 이벤트 루프, 일꾼 풀, 트래커·DHT, 그리고 엔진 전체의 상한(연결 수·연결 시도 속도·디스크에 쓰려고 쌓인 바이트).
 *
 * 스레드: 이벤트 루프 하나 + 디스크 일꾼 하나(쓰기·해시·이어받기 자료 — 차례가 지켜져야 한다: 쓰기 → 내리기 → '확인한 조각' 적기) +
 * 트래커·이름 풀이 일꾼 넷 + 웹 시드 일꾼 둘 + 알림 하나. 연결이 몇 개든 이 수는 그대로다.
 */
internal class EngineCore(
    val config: TorrentConfig,
    val env: TorrentEnvironment,
    val root: File,
    val loop: EventLoop,
    val disk: ExecutorService,
    val io: ExecutorService,
    val webSeedPool: ExecutorService,
    val http: HttpGet,
    val httpTracker: HttpTracker,
    val udpTracker: UdpTrackerClient,
    val dht: Dht?,
    /** announce 에 적는 포트 — 듣지 않는다. 엔진마다 무작위 높은 포트. */
    val announcePort: Int,
    val notify: Notifier,
) {
    val stateDir = File(root, "state")
    val dataRoot = File(root, "data")

    /** 세션들 — 연결 수를 셀 때. 이벤트 루프에서만. */
    val sessions = LinkedHashMap<String, TorrentSession>()

    /** 세션이 끝나거나 멈췄다 — 엔진이 차례를 다시 정한다(이벤트 루프에서). */
    var onSessionStateChanged: () -> Unit = {}

    fun sessionStateChanged() = onSessionStateChanged()

    // ── 연결 예산(이벤트 루프에서만) ──────────────────────────────────────────
    private var tokens = config.newConnectionsPerSecond.toDouble()
    private var tokensAt = loop.now()

    fun totalConnections(): Int = sessions.values.sumOf { it.connectionCount() }

    fun halfOpen(): Int = sessions.values.sumOf { it.halfOpenCount() }

    /** 새 연결을 하나 시작해도 되는가 — 되면 토큰을 하나 쓴다. */
    fun tryTakeConnectSlot(): Boolean {
        if (totalConnections() >= config.maxConnections) return false
        if (halfOpen() >= config.maxHalfOpen) return false
        val now = loop.now()
        val rate = config.newConnectionsPerSecond.toDouble()
        tokens = minOf(rate, tokens + (now - tokensAt) * rate / 1000.0)
        tokensAt = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }

    // ── 디스크 역압 ─────────────────────────────────────────────────────────
    /** 디스크 일꾼에 넘겼지만 아직 쓰지 않은 바이트. */
    val pendingWrite = AtomicLong()
    private val lock = Object()

    fun canRead(): Boolean = pendingWrite.get() < HIGH_WATER

    fun addPending(n: Int) {
        pendingWrite.addAndGet(n.toLong())
    }

    /** 쓰기가 끝났다(이벤트 루프). 낮은 물높이 밑으로 내려가면 멈춘 연결을 다시 읽게 하고 웹 시드 일꾼을 깨운다. */
    fun releasePending(n: Int) {
        val left = pendingWrite.addAndGet(-n.toLong())
        if (left < LOW_WATER) {
            for (s in sessions.values) s.resumeReading()
            synchronized(lock) { lock.notifyAll() }
        }
    }

    /** 웹 시드 일꾼이 — 쌓인 쓰기가 높은 물높이 밑으로 내려갈 때까지 기다린다. */
    fun awaitRoom(cancelled: () -> Boolean) {
        synchronized(lock) {
            while (pendingWrite.get() >= HIGH_WATER && !cancelled() && !loop.isClosed) lock.wait(200)
        }
    }

    companion object {
        const val HIGH_WATER = 16L * 1024 * 1024
        const val LOW_WATER = 8L * 1024 * 1024
    }
}

/** 듣개 부르기 — 알림 스레드로 넘긴다. */
internal interface Notifier {
    fun status(s: io.github.donggi.iroirobrowser.torrent.TorrentStatus)
    fun metadata(id: String, info: io.github.donggi.iroirobrowser.torrent.TorrentInfo)
    fun fileCompleted(id: String, index: Int, file: File)
    fun finished(id: String)
    fun error(id: String, error: io.github.donggi.iroirobrowser.torrent.TorrentError, detail: String?)
}
