package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.dht.Krpc
import io.github.donggi.iroirobrowser.torrent.tracker.AnnounceEvent
import io.github.donggi.iroirobrowser.torrent.tracker.AnnounceRequest
import io.github.donggi.iroirobrowser.torrent.tracker.HttpTracker
import io.github.donggi.iroirobrowser.torrent.tracker.Intervals
import io.github.donggi.iroirobrowser.torrent.tracker.TrackerException
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackerCodecTest {

    private val req = AnnounceRequest(ByteArray(20) { it.toByte() }, "-IW0100-abcdefghijkl".toByteArray(), 51234, 1000, 5000, AnnounceEvent.STARTED, 50, 0x1234)

    @Test
    fun announceUrl() {
        val url = HttpTracker.announceUrl("http://t.example/announce?passkey=x", req, null)
        assertTrue(url.startsWith("http://t.example/announce?passkey=x&info_hash=%00%01%02"), url)
        assertTrue("&uploaded=0&" in url, url)
        assertTrue("&event=started" in url)
        assertTrue("&compact=1" in url)
        for (p in listOf("&ip=", "&ipv4=", "&ipv6=")) assertFalse(p in url, "$p 를 보내지 않는다")
        val none = HttpTracker.announceUrl("http://t/a", AnnounceRequest(req.infoHash, req.peerId, 1, 0, 0, AnnounceEvent.NONE, 0, 1), null)
        assertFalse("event=" in none)
    }

    @Test
    fun parseResponses() {
        val compact = Bencode.encode(
            mapOf(
                "interval" to 1800L, "min interval" to 900L, "complete" to 5L, "incomplete" to 2L,
                "peers" to byteArrayOf(1, 2, 3, 4, 0x1A, 0xE1.toByte(), 5, 6, 7, 8, 0, 0, 9, 9),
                "peers6" to (ByteArray(15) + byteArrayOf(1) + byteArrayOf(0x1A, 0xE1.toByte())),
            ),
        )
        val r = HttpTracker.parse(compact)
        assertEquals(listOf("1.2.3.4:6881", "[0:0:0:0:0:0:0:1]:6881"), r.peers.map { "${if (it.address is java.net.Inet6Address) "[${it.address.hostAddress}]" else it.address.hostAddress}:${it.port}" })
        assertEquals(1800, r.intervalSec)
        assertEquals(5, r.seeders)
        val dict = Bencode.encode(mapOf("interval" to 60L, "peers" to listOf(mapOf("ip" to "9.9.9.9", "port" to 51413L), mapOf("ip" to "evil.example", "port" to 1L), mapOf("ip" to "1.1.1.1", "port" to 0L))))
        assertEquals(listOf(InetSocketAddress("9.9.9.9", 51413)), HttpTracker.parse(dict).peers, "이름인 피어·포트 0 은 버린다")
        val fail = assertFailsWith<TrackerException> { HttpTracker.parse(Bencode.encode(mapOf("failure reason" to "unregistered torrent"))) }
        assertEquals("unregistered torrent", fail.failureReason)
        assertFailsWith<TrackerException> { HttpTracker.parse("<html>".toByteArray()) }
    }

    @Test
    fun intervalsAreClamped() {
        assertEquals(Intervals.MIN_SEC, Intervals.clamp(0, null))
        assertEquals(Intervals.MIN_SEC, Intervals.clamp(-5, null))
        assertEquals(Intervals.MAX_SEC, Intervals.clamp(Long.MAX_VALUE, null))
        assertEquals(Intervals.DEFAULT_SEC, Intervals.clamp(null, null))
        assertEquals(900, Intervals.clamp(600, 900))
        assertEquals(1800, Intervals.clamp(1800, -1))
    }

    @Test
    fun udpVectorsDecode() {
        // 파이썬이 지은 패킷의 꼴 — 우리가 짓는 announce 와 같은 자리(엔진은 UdpTrackerClient 안에서 짓는다 — 통합 시험이 가짜 UDP 트래커로 본다).
        val ann = unhex(PythonVectors.udp.first { it.id == "announce-request" }.bytesHex)
        assertEquals(98, ann.size)
        assertEquals(0L, java.nio.ByteBuffer.wrap(ann, 72, 8).long, "uploaded")
        val connect = unhex(PythonVectors.udp.first { it.id == "connect-request" }.bytesHex)
        assertEquals(0x41727101980L, java.nio.ByteBuffer.wrap(connect, 0, 8).long)
    }

    @Test
    fun krpcVectors() {
        val q = PythonVectors.krpc.first { it.id == "get_peers-query" }
        val parsed = Bencode.decode(unhex(q.bytesHex)) as io.github.donggi.iroirobrowser.torrent.bencode.BDict
        val a = parsed.dict("a")!!
        val ours = Krpc.getPeers("aa".toByteArray(), a.bytes("id")!!, a.bytes("info_hash")!!)
        assertEquals(q.bytesHex, hex(ours), "우리 get_peers 질의가 바이트까지 같다(ro=1 포함)")
        val f = PythonVectors.krpc.first { it.id == "find_node-query" }
        val fa = (Bencode.decode(unhex(f.bytesHex)) as io.github.donggi.iroirobrowser.torrent.bencode.BDict).dict("a")!!
        assertEquals(f.bytesHex, hex(Krpc.findNode("bb".toByteArray(), fa.bytes("id")!!, fa.bytes("target")!!)))

        for (c in PythonVectors.krpcResponses) {
            val m = Krpc.parse(unhex(c.bytesHex)) as Krpc.Message.Response
            assertEquals(c.nodeIdsHex, m.nodes.map { hex(it.id) })
            assertEquals(c.nodeAddrs, m.nodes.map { "${it.address.address.hostAddress}:${it.address.port}" })
            assertEquals(c.peers, m.values.map { "${it.address.hostAddress}:${it.port}" })
        }
        val e = Krpc.parse(unhex(PythonVectors.krpc.first { it.id == "error-response" }.bytesHex)) as Krpc.Message.Error
        assertEquals(201, e.code)
        // 깨진 것
        assertEquals(null, Krpc.parse("garbage".toByteArray()))
        assertEquals(null, Krpc.parse(Bencode.encode(mapOf("t" to "aa", "y" to "r", "r" to mapOf("id" to "short")))))
    }
}
