package io.github.donggi.iroirobrowser.torrent.tracker

import io.github.donggi.iroirobrowser.core.IpLiterals
import io.github.donggi.iroirobrowser.torrent.bencode.BBytes
import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.BInt
import io.github.donggi.iroirobrowser.torrent.bencode.BList
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.bencode.BencodeException
import io.github.donggi.iroirobrowser.torrent.net.HttpGet
import io.github.donggi.iroirobrowser.torrent.net.HttpLimits
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress

enum class AnnounceEvent(val http: String?, val udp: Int) {
    NONE(null, 0),
    COMPLETED("completed", 1),
    STARTED("started", 2),
    STOPPED("stopped", 3),
}

/**
 * announce 에 싣는 것. **`uploaded` 는 매개변수가 없다 — 요청을 짓는 곳이 늘 0 을 적는다**(올리기 0 — 우리는 아무것도 올리지 않는다).
 * `ip`·`ipv4`·`ipv6` 도 보내지 않는다(트래커는 연결의 주소를 쓴다).
 */
class AnnounceRequest(
    val infoHash: ByteArray,
    val peerId: ByteArray,
    /** 듣지 않는 포트다(들어오는 연결을 받지 않는다) — 규격이 요구해 적는다. */
    val port: Int,
    val downloaded: Long,
    val left: Long,
    val event: AnnounceEvent,
    val numWant: Int,
    val key: Int,
)

class AnnounceResponse(
    val peers: List<InetSocketAddress>,
    /** 다음 announce 까지(초) — [Intervals.clamp] 를 거친 값. */
    val intervalSec: Long,
    val seeders: Long?,
    val leechers: Long?,
    val warning: String?,
    val trackerId: ByteArray?,
)

/** 트래커가 거절했거나(`failure reason`) 답을 읽지 못했다. */
class TrackerException(message: String, val failureReason: String? = null) : IOException(message)

/** 트래커가 준 간격을 믿지 않고 묶는다 — 0·음수면 너무 자주, 거대하면 영영 묻지 않게 된다. */
object Intervals {
    const val MIN_SEC = 120L
    const val MAX_SEC = 2 * 60 * 60L
    const val DEFAULT_SEC = 30 * 60L

    fun clamp(interval: Long?, minInterval: Long?): Long {
        val base = interval ?: DEFAULT_SEC
        val floor = maxOf(MIN_SEC, minInterval?.takeIf { it in 1..MAX_SEC } ?: 0)
        return base.coerceIn(floor, MAX_SEC)
    }
}

/** 트래커 응답의 피어 목록 상한(BEP 23 의 compact 와 사전 꼴을 합쳐). */
const val MAX_PEERS_PER_RESPONSE = 500

/**
 * HTTP·HTTPS 트래커(BEP 3 · compact BEP 23 · IPv6 BEP 7 · 층 BEP 12 는 [TrackerSet]). 차단 호출이라 일꾼 스레드에서만 부른다.
 * 응답은 1 MiB 까지, gzip 을 청하지 않고(서버가 그래도 주면 푼 바이트로 상한), 넘겨주기 3번 — 모두 [HttpGet] 이 지킨다.
 */
class HttpTracker(private val http: HttpGet) {

    fun announce(url: String, req: AnnounceRequest, trackerId: ByteArray?): AnnounceResponse {
        val (status, body) = http.fetch(announceUrl(url, req, trackerId), Purpose.TRACKER, LIMITS)
        if (status != 200) {
            // 실패 까닭을 bencode 로 주는 트래커가 있다 — 읽을 수 있으면 그것을 쓴다.
            val reason = runCatching { failureReason(body) }.getOrNull()
            throw TrackerException("http $status", reason)
        }
        return parse(body)
    }

    companion object {
        val LIMITS = HttpLimits(maxBodyBytes = 1_048_576, connectTimeoutMs = 10_000, idleTimeoutMs = 15_000, totalTimeoutMs = 30_000, maxRedirects = 3)

        fun announceUrl(base: String, req: AnnounceRequest, trackerId: ByteArray?): String {
            val sb = StringBuilder(base)
            sb.append(if ('?' in base) '&' else '?')
            sb.append("info_hash=").append(HttpGet.Percent.encode(req.infoHash))
            sb.append("&peer_id=").append(HttpGet.Percent.encode(req.peerId))
            sb.append("&port=").append(req.port)
            sb.append("&uploaded=0")
            sb.append("&downloaded=").append(req.downloaded)
            sb.append("&left=").append(req.left)
            sb.append("&key=").append(String.format("%08X", req.key))
            req.event.http?.let { sb.append("&event=").append(it) }
            sb.append("&numwant=").append(req.numWant)
            sb.append("&compact=1&no_peer_id=1")
            trackerId?.let { sb.append("&trackerid=").append(HttpGet.Percent.encode(it)) }
            return sb.toString()
        }

        private fun failureReason(body: ByteArray): String? =
            (Bencode.decode(body, Bencode.Limits.TRACKER) as? BDict)?.string("failure reason")?.take(300)

        fun parse(body: ByteArray): AnnounceResponse {
            val d = try {
                Bencode.decode(body, Bencode.Limits.TRACKER) as? BDict
            } catch (e: BencodeException) {
                throw TrackerException("bad bencode: ${e.message}")
            } ?: throw TrackerException("not a dict")
            d.string("failure reason")?.let { throw TrackerException("failure", it.take(300)) }
            val peers = ArrayList<InetSocketAddress>()
            when (val p = d["peers"]) {
                is BBytes -> peers += compact(p.bytes, v6 = false)
                is BList -> for (item in p.items) {
                    if (peers.size >= MAX_PEERS_PER_RESPONSE) break
                    val e = item as? BDict ?: continue
                    val ip = e.string("ip") ?: continue
                    val port = e.long("port")?.takeIf { it in 1..65535 }?.toInt() ?: continue
                    // DNS 이름인 피어는 쓰지 않는다 — 남이 고른 이름을 풀지 않는다.
                    if (!IpLiterals.isIpv4(ip) && !IpLiterals.isIpv6(ip)) continue
                    peers += InetSocketAddress(InetAddress.getByName(ip), port)
                }
                else -> Unit
            }
            (d["peers6"] as? BBytes)?.let { peers += compact(it.bytes, v6 = true) }
            return AnnounceResponse(
                peers = peers.distinct().take(MAX_PEERS_PER_RESPONSE),
                intervalSec = Intervals.clamp((d["interval"] as? BInt)?.value, (d["min interval"] as? BInt)?.value),
                seeders = d.long("complete")?.takeIf { it >= 0 },
                leechers = d.long("incomplete")?.takeIf { it >= 0 },
                warning = d.string("warning message")?.take(300),
                trackerId = d.bytes("tracker id")?.takeIf { it.size in 1..256 },
            )
        }

        /** compact 꼴 — IPv4 는 6바이트(BEP 23), IPv6 는 18바이트(BEP 7). 길이가 맞지 않는 꼬리는 버린다. */
        fun compact(bytes: ByteArray, v6: Boolean): List<InetSocketAddress> {
            val step = if (v6) 18 else 6
            val out = ArrayList<InetSocketAddress>()
            var i = 0
            while (i + step <= bytes.size && out.size < MAX_PEERS_PER_RESPONSE) {
                val addr = InetAddress.getByAddress(bytes.copyOfRange(i, i + step - 2))
                val port = ((bytes[i + step - 2].toInt() and 0xFF) shl 8) or (bytes[i + step - 1].toInt() and 0xFF)
                if (port != 0) out += InetSocketAddress(addr, port)
                i += step
            }
            return out
        }
    }
}
