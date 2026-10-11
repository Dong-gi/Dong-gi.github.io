package io.github.donggi.iroirobrowser.torrent.tracker

import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.HttpException
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.session.EventLoop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Locale
import java.util.Random
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** 트래커 하나의 상태(화면용). */
class TrackerStatus(
    val url: String,
    val tier: Int,
    /** 'ok'·'announcing'·'error'·'idle'. */
    val state: String,
    val lastError: String?,
    val lastPeers: Int,
    val seeders: Long?,
    val leechers: Long?,
    /** 다음 announce 까지 남은 밀리초(엔진의 시계로). */
    val nextAnnounceInMs: Long,
)

/**
 * 토렌트 하나의 트래커들(BEP 12). 층마다 차례를 처음 한 번 섞고, 층마다 한 트래커에 묻는다 — 되는 트래커는 층의 맨 앞으로, 실패하면 같은 층의
 * 다음 트래커로. 모든 층에 묻는다(피어를 넓게 얻으려고 — qBittorrent 의 기본과 같다). **공개 트래커를 덧붙이지 않는다**(사용자 결정 ⑦).
 *
 * HTTP 는 일꾼 풀에서 막히는 호출로, UDP 는 [UdpTrackerClient] 로(이름만 일꾼 풀에서 풀어 거른다). 이벤트 루프에서만 쓴다.
 */
internal class Announcer(
    private val loop: EventLoop,
    private val io: Executor,
    private val http: HttpTracker,
    private val udp: UdpTrackerClient,
    private val resolver: Resolver,
    private val filter: AddressFilter,
    tiers: List<List<String>>,
    random: Random,
    /** 그 event 의 announce 매개변수를 지금 상태로 짓는다. */
    private val requestFor: (AnnounceEvent) -> AnnounceRequest,
    private val onPeers: (List<InetSocketAddress>) -> Unit,
    private val log: (String) -> Unit,
) {
    private inner class Entry(val url: String, val tierIndex: Int) {
        var nextAt = 0L
        var failures = 0
        var lastError: String? = null
        var lastPeers = 0
        var seeders: Long? = null
        var leechers: Long? = null
        var busy = false
        var started = false
        var trackerId: ByteArray? = null
        var everOk = false
        var resolved: InetSocketAddress? = null
        var resolvedAt = 0L
        var udpHandle: UdpTrackerClient.Handle? = null
        var generation = 0
    }

    private val tiers: List<MutableList<Entry>> = tiers.mapIndexed { i, urls ->
        urls.map { Entry(it, i) }.shuffled(random).toMutableList()
    }
    private val current = IntArray(this.tiers.size)
    private var running = false
    private var generation = 0

    val isEmpty: Boolean get() = tiers.isEmpty()

    fun start() {
        running = true
        generation++
        val now = loop.now()
        for (tier in tiers) for (e in tier) {
            e.nextAt = now
            e.started = false
            e.busy = false
        }
        tick()
    }

    /** 1초마다 — 때가 된 층의 트래커에 묻는다. */
    fun tick() {
        if (!running) return
        val now = loop.now()
        for ((t, tier) in tiers.withIndex()) {
            if (tier.isEmpty()) continue
            if (current[t] >= tier.size) current[t] = 0
            val e = tier[current[t]]
            if (e.busy || now < e.nextAt) continue
            announce(e, if (e.started) AnnounceEvent.NONE else AnnounceEvent.STARTED)
        }
    }

    /**
     * 멈춘다 — `started` 를 보낸 트래커에 [completed] 면 `completed` 를, **그것이 끝난 뒤** `stopped` 를 보낸다(차례가 뒤바뀌면 트래커는
     * 떠난 피어가 다 받았다고 읽는다 — 시험이 일꾼 풀에서 뒤바뀌는 것을 잡았다). 결과는 기록만 한다.
     */
    fun stop(completed: Boolean) {
        if (!running) return
        running = false
        generation++
        for (tier in tiers) for (e in tier) {
            e.udpHandle?.cancel()
            e.busy = false
            if (!e.started) continue
            e.started = false
            if (completed) fire(e, AnnounceEvent.COMPLETED) { fire(e, AnnounceEvent.STOPPED) {} } else fire(e, AnnounceEvent.STOPPED) {}
        }
    }

    fun statuses(): List<TrackerStatus> {
        val now = loop.now()
        return tiers.flatMap { tier ->
            tier.map { e ->
                TrackerStatus(
                    url = e.url,
                    tier = e.tierIndex,
                    state = when {
                        e.busy -> "announcing"
                        e.lastError != null -> "error"
                        e.everOk -> "ok"
                        else -> "idle"
                    },
                    lastError = e.lastError,
                    lastPeers = e.lastPeers,
                    seeders = e.seeders,
                    leechers = e.leechers,
                    nextAnnounceInMs = if (running) maxOf(0, e.nextAt - now) else -1,
                )
            }
        }
    }

    private fun announce(e: Entry, event: AnnounceEvent) {
        val req = requestFor(event)
        e.busy = true
        val gen = generation
        val done = { response: AnnounceResponse?, error: String?, refused: Boolean ->
            if (gen == generation) onResult(e, event, response, error, refused)
        }
        submit(e, req, done)
    }

    /** 멈출 때 보내는 것 — 결과는 기록만 하고, 끝나면(실패해도) [then] 을 루프에서. */
    private fun fire(e: Entry, event: AnnounceEvent, then: () -> Unit) {
        val req = requestFor(event)
        submit(e, req) { _, error, _ ->
            if (error != null) log("tracker ${event.name.lowercase(Locale.ROOT)} failed: ${e.url} $error")
            then()
        }
    }

    private fun submit(e: Entry, req: AnnounceRequest, done: (AnnounceResponse?, String?, Boolean) -> Unit) {
        val lower = e.url.lowercase(Locale.ROOT)
        try {
            if (lower.startsWith("udp://")) {
                val cached = e.resolved
                if (cached != null && loop.now() - e.resolvedAt < RESOLVE_TTL_MS) {
                    e.udpHandle = udp.announce(cached, req) { r, err -> done(r, err, false) }
                    return
                }
                val hostPort = udpHostPort(e.url) ?: return loop.post { done(null, "bad udp url", true) }
                io.execute {
                    val target = try {
                        resolve(hostPort.first, hostPort.second)
                    } catch (ex: Exception) {
                        loop.post { done(null, "resolve: ${ex.message}", false) }
                        return@execute
                    }
                    loop.post {
                        if (target == null) {
                            done(null, "blocked address", true)
                        } else {
                            e.resolved = target
                            e.resolvedAt = loop.now()
                            e.udpHandle = udp.announce(target, req) { r, err -> done(r, err, false) }
                        }
                    }
                }
            } else {
                io.execute {
                    try {
                        val r = http.announce(e.url, req, e.trackerId)
                        loop.post { done(r, null, false) }
                    } catch (ex: TrackerException) {
                        loop.post { done(null, ex.failureReason?.let { "refused: $it" } ?: ex.message, ex.failureReason != null) }
                    } catch (ex: HttpException) {
                        val refused = ex.kind == HttpException.Kind.BLOCKED_ADDRESS || ex.kind == HttpException.Kind.BAD_URL
                        loop.post { done(null, ex.message, refused) }
                    } catch (ex: Exception) {
                        loop.post { done(null, ex.toString(), false) }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            done(null, "engine closing", false)
        }
    }

    /** 이름을 풀어 거른 첫 주소(일꾼 스레드). 다 걸러지면 null. */
    private fun resolve(host: String, port: Int): InetSocketAddress? {
        val all: List<InetAddress> = resolver.resolve(host)
        val ok = all.firstOrNull { filter.allows(it, port, Purpose.TRACKER) } ?: return null
        return InetSocketAddress(ok, port)
    }

    private fun onResult(e: Entry, event: AnnounceEvent, response: AnnounceResponse?, error: String?, refused: Boolean) {
        e.busy = false
        e.udpHandle = null
        val now = loop.now()
        if (response != null) {
            if (event == AnnounceEvent.STARTED) e.started = true
            e.everOk = true
            e.failures = 0
            e.lastError = null
            e.lastPeers = response.peers.size
            e.seeders = response.seeders
            e.leechers = response.leechers
            response.trackerId?.let { e.trackerId = it }
            e.nextAt = now + response.intervalSec * 1000
            // 되는 트래커를 층의 맨 앞으로(BEP 12).
            val tier = tiers[e.tierIndex]
            tier.remove(e)
            tier.add(0, e)
            current[e.tierIndex] = 0
            if (response.peers.isNotEmpty()) onPeers(response.peers)
        } else {
            e.failures++
            e.lastError = error
            val backoff = if (refused) REFUSED_BACKOFF_MS else minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl minOf(e.failures - 1, 6))
            e.nextAt = now + backoff
            val tier = tiers[e.tierIndex]
            if (tier.size > 1) current[e.tierIndex] = (tier.indexOf(e) + 1) % tier.size
        }
    }

    private fun udpHostPort(url: String): Pair<String, Int>? {
        val rest = url.substring(6).substringBefore('/').substringBefore('?')
        val host: String
        val portText: String
        if (rest.startsWith("[")) {
            val close = rest.indexOf(']')
            if (close < 0 || close + 2 > rest.length || rest[close + 1] != ':') return null
            host = rest.substring(1, close)
            portText = rest.substring(close + 2)
        } else {
            val colon = rest.lastIndexOf(':')
            if (colon <= 0) return null
            host = rest.substring(0, colon)
            portText = rest.substring(colon + 1)
        }
        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        if (host.isEmpty() || host.any { it.isWhitespace() || it == '@' }) return null
        return host to port
    }

    companion object {
        const val BASE_BACKOFF_MS = 60_000L
        const val MAX_BACKOFF_MS = 60 * 60_000L
        const val REFUSED_BACKOFF_MS = 30 * 60_000L
        const val RESOLVE_TTL_MS = 10 * 60_000L
    }
}
