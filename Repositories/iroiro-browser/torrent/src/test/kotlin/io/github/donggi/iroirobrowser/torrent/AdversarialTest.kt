package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.fake.DhtBehavior
import io.github.donggi.iroirobrowser.torrent.fake.Encryption
import io.github.donggi.iroirobrowser.torrent.fake.FakeDhtNode
import io.github.donggi.iroirobrowser.torrent.fake.FakeFile
import io.github.donggi.iroirobrowser.torrent.fake.FakeHttpTracker
import io.github.donggi.iroirobrowser.torrent.fake.FakeSeeder
import io.github.donggi.iroirobrowser.torrent.fake.FakeTorrent
import io.github.donggi.iroirobrowser.torrent.fake.SeederOptions
import io.github.donggi.iroirobrowser.torrent.fake.TrackerBehavior
import java.io.Closeable
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 적대 대본(CLAUDE.md 의 목록) — 틀린 해시·요청 안 한 블록·16 KiB 초과·길이 접두 2^32-1·느린 송신·남는 비트·범위 밖 have·두 번째
 * bitfield·거짓 metadata_size·사설 IP 가득한 피어 목록·1 MiB 넘는 트래커 응답·gzip 폭탄·리다이렉트 고리·127.0.0.1/169.254.169.254 로
 * 리다이렉트·interval 0/음수/거대, 그리고 마구잡이 프레임을 보내는 피어.
 */
class AdversarialTest {

    private val closeables = ArrayList<Closeable>()

    @kotlin.test.BeforeTest
    fun clearDiagnostics() = io.github.donggi.iroirobrowser.torrent.fake.FakeDiagnostics.clear()

    @AfterTest
    fun cleanUp() {
        val fakeErrors = io.github.donggi.iroirobrowser.torrent.fake.FakeDiagnostics.unexpectedErrors
        closeables.reversed().forEach { runCatching { it.close() } }
        assertTrue(fakeErrors.isEmpty(), "가짜 군집의 예상 밖 예외: $fakeErrors")
    }

    private fun <T : Closeable> keep(c: T): T = c.also { closeables += it }

    private fun torrent(tracker: FakeHttpTracker, size: Int = 41 * 16384 - 1000) = FakeTorrent(
        name = "adv",
        files = listOf(FakeFile(listOf("a.bin"), FakeTorrent.data(9, size / 2)), FakeFile(listOf("b.bin"), FakeTorrent.data(10, size - size / 2))),
        pieceLength = 16384,
        announce = tracker.announceUrl,
    )

    private fun assertContent(h: EngineHarness, t: FakeTorrent, id: String) {
        for ((i, e) in t.layout.withIndex()) {
            assertContentEquals(t.payload.copyOfRange(e.offset.toInt(), (e.offset + e.length).toInt()), h.engine.fileOf(id, i)!!.readBytes())
        }
    }

    /**
     * 적대 피어만 먼저 알리고 엔진이 그 연결을 끊기를 기다린 뒤, 정직한 시더를 더해 다 받는다. 적대 피어의 IP 는 막혀 다시 붙지 않는다.
     */
    private fun adversary(options: SeederOptions, reason: String, clock: ManualClock? = null, nudge: (EngineHarness) -> Unit = {}) {
        val tracker = keep(FakeHttpTracker())
        val t = torrent(tracker)
        val bad = keep(FakeSeeder(t, options, bindAddress = lo(2)))
        tracker.peers = listOf(bad.address)
        val h = keep(EngineHarness(clock = clock ?: io.github.donggi.iroirobrowser.torrent.net.Clock.SYSTEM, config = TorrentConfig(dht = false)))
        h.allow(tracker.port, bad.port)
        val id = h.engine.add(t.torrentBytes)
        waitUntil(what = "adversary connected") { bad.connections.isNotEmpty() }
        nudge(h)
        waitUntil(what = "$reason: client closes the adversary (${h.logs.filter { "closed" in it }})") {
            bad.connections.isNotEmpty() && bad.connections.all { it.isClosed } && bad.connections.any { it.closedByPeer }
        }
        assertTrue(h.logs.any { "closed:" in it && reason in it }, "까닭 '$reason' — ${h.logs.filter { "closed" in it }}")
        val tries = bad.connections.size
        // 정직한 시더를 더한다 — 적대 피어(같은 IP)는 막혀 다시 붙지 않는다.
        val good = keep(FakeSeeder(t, bindAddress = lo(3)))
        tracker.peers = listOf(bad.address, good.address)
        h.allow(good.port)
        h.engine.pause(id)
        h.engine.resume(id)
        if (clock != null) {
            // 손 시계 — 엔진의 1초 일이 돌도록 조금씩 민다.
            waitUntil(what = "done") {
                clock.advance(1_000)
                h.engine.networkChanged()
                h.status(id).state == TorrentState.COMPLETED
            }
        } else {
            h.awaitDone(id)
        }
        assertContent(h, t, id)
        assertEquals(tries, bad.connections.size, "막은 IP 에 다시 붙지 않았다")
    }

    @Test
    fun wrongHashBansPeer() = adversary(SeederOptions(corruptPieces = (0 until 41).toSet()), "bad data")

    @Test
    fun unrequestedBlock() = adversary(SeederOptions(sendUnrequestedBlock = true), "unrequested block")

    @Test
    fun blockOver16KiB() = adversary(SeederOptions(oversizedBlock = true), "bad length")

    @Test
    fun lengthPrefixMax() = adversary(SeederOptions(hugeLengthPrefix = true), "bad length 4294967295")

    @Test
    fun spareBits() = adversary(SeederOptions(spareBitsSet = true, useHaveAll = false), "bad bitfield")

    @Test
    fun haveOutOfRange() = adversary(SeederOptions(haveOutOfRange = true), "have out of range")

    @Test
    fun secondBitfield() = adversary(SeederOptions(secondBitfield = true, useHaveAll = false), "bitfield after bitfield")

    /** 한 바이트씩 흘리는 핸드셰이크 — 가상 시계로 20초를 넘기면 끊는다(실제로 기다리지 않는다). 막히지는 않는다(위반이 아니다). */
    @Test
    fun slowSenderTimesOut() {
        val tracker = keep(FakeHttpTracker())
        val t = torrent(tracker)
        val slow = keep(FakeSeeder(t, SeederOptions(encryption = Encryption.PLAIN_ONLY, slowDripHandshakeMs = 400), bindAddress = lo(2)))
        tracker.peers = listOf(slow.address)
        val clock = ManualClock()
        val h = keep(EngineHarness(clock = clock, config = TorrentConfig(dht = false)))
        h.allow(tracker.port, slow.port)
        h.engine.add(t.torrentBytes)
        // MSE 를 거절당하고 평문으로 다시 붙는다 — 그 연결이 한 바이트씩.
        waitUntil(what = "plain connection") { slow.connections.any { it.encryption == "plain" || it.encryption == "pending" } && slow.connections.size >= 2 }
        Thread.sleep(500)
        clock.advance(25_000)
        h.engine.networkChanged()
        waitUntil(what = "timeout close") { slow.connections.last().isClosed }
        assertTrue(h.logs.any { "handshake timeout" in it }, h.logs.filter { "closed" in it }.toString())
    }

    /** 거짓 metadata_size — 해시가 맞지 않으면 그 크기와 그 피어를 버리고 다른 피어에게서 받는다. */
    @Test
    fun falseMetadataSize() {
        val tracker = keep(FakeHttpTracker())
        val t = torrent(tracker)
        val liar = keep(FakeSeeder(t, SeederOptions(lieMetadataSize = 5000), bindAddress = lo(2)))
        tracker.peers = listOf(liar.address)
        val h = keep(EngineHarness(config = TorrentConfig(dht = false)))
        h.allow(tracker.port, liar.port)
        val id = h.engine.addMagnet(t.magnet(trackers = listOf(tracker.announceUrl)))
        waitUntil(what = "liar closed") { liar.connections.isNotEmpty() && liar.connections.all { it.isClosed } }
        assertTrue(h.logs.any { "metadata hash mismatch" in it }, h.logs.filter { "closed" in it }.toString())
        assertEquals(TorrentState.METADATA, h.status(id).state)
        val honest = keep(FakeSeeder(t, bindAddress = lo(3)))
        tracker.peers = listOf(liar.address, honest.address)
        h.allow(honest.port)
        h.engine.pause(id)
        h.engine.resume(id)
        h.awaitDone(id)
        assertContent(h, t, id)
    }

    /** 사설·루프백·링크 로컬·IPv4-mapped 주소로 가득한 피어 목록 — 붙는 곳은 정직한 시더 하나뿐. */
    @Test
    fun privatePeerListIsFiltered() {
        val tracker = keep(FakeHttpTracker())
        val t = torrent(tracker)
        val good = keep(FakeSeeder(t, bindAddress = lo(2)))
        val closedPort = ServerSocket(0).use { it.localPort }
        tracker.peers = listOf(
            InetSocketAddress("10.0.0.1", 6881), InetSocketAddress("192.168.1.1", 6881), InetSocketAddress("172.16.5.5", 6881),
            InetSocketAddress("127.0.0.1", closedPort), InetSocketAddress("169.254.169.254", 6881), InetSocketAddress("100.64.0.1", 6881),
            InetSocketAddress("0.0.0.0", 6881), InetSocketAddress("224.0.0.1", 6881), InetSocketAddress("8.8.8.8", 25), good.address,
        )
        tracker.peers6 = listOf(InetSocketAddress("::1", 6881), InetSocketAddress("fe80::1", 6881), InetSocketAddress("fd00::1", 6881))
        val h = keep(EngineHarness(config = TorrentConfig(dht = false)))
        h.allow(tracker.port, good.port)
        val id = h.engine.add(t.torrentBytes)
        h.awaitDone(id)
        assertContent(h, t, id)
        assertEquals(setOf(good.address), h.transport.tcp.toSet(), "붙은 곳: ${h.transport.tcp}")
    }

    private fun trackerAttack(behavior: TrackerBehavior, expectError: String): EngineHarness {
        val tracker = keep(FakeHttpTracker())
        tracker.behavior = behavior
        val t = torrent(tracker)
        val h = keep(EngineHarness(config = TorrentConfig(dht = false)))
        h.allow(tracker.port)
        val id = h.engine.add(t.torrentBytes)
        waitUntil(what = "tracker error ($expectError)") { h.status(id).trackers.any { it.state == "error" } }
        val err = h.status(id).trackers.first().lastError.orEmpty()
        assertTrue(expectError in err, "error '$err'")
        assertEquals(TorrentState.WAITING, h.status(id).state, "엔진은 멀쩡하다")
        return h
    }

    @Test
    fun hugeTrackerResponse() {
        trackerAttack(TrackerBehavior.HugeBody(2 * 1024 * 1024), "TOO_LARGE")
        trackerAttack(TrackerBehavior.HugeBodyChunked(2 * 1024 * 1024), "TOO_LARGE")
    }

    /** gzip 폭탄 — 우리는 gzip 을 청하지 않는데 트래커가 200 MiB 의 0 을 눌러 보낸다. 가짜 트래커에 없는 손잡이라 시험 안의 작은 서버로. */
    @Test
    fun gzipBombTracker() {
        val bomb = java.io.ByteArrayOutputStream().also { b ->
            java.util.zip.GZIPOutputStream(b).use { z -> repeat(200) { z.write(ByteArray(1024 * 1024)) } }
        }.toByteArray()
        val ss = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        closeables += ss
        Thread {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                runCatching {
                    s.use {
                        val input = it.getInputStream()
                        val head = StringBuilder()
                        while (!head.endsWith("\r\n\r\n")) {
                            val c = input.read()
                            if (c < 0) break
                            head.append(c.toChar())
                        }
                        it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${bomb.size}\r\n\r\n".toByteArray())
                        it.getOutputStream().write(bomb)
                    }
                }
            }
        }.apply { isDaemon = true }.start()
        val t = FakeTorrent("adv", listOf(FakeFile(listOf("a.bin"), FakeTorrent.data(9, 40_000))), 16384, singleFile = true, announce = "http://127.0.0.1:${ss.localPort}/announce")
        val h = keep(EngineHarness(config = TorrentConfig(dht = false)))
        h.allow(ss.localPort)
        val id = h.engine.add(t.torrentBytes)
        waitUntil(what = "tracker error") { h.status(id).trackers.any { it.state == "error" } }
        assertTrue("TOO_LARGE" in h.status(id).trackers.first().lastError.orEmpty(), h.status(id).trackers.first().lastError)
        assertEquals(TorrentState.WAITING, h.status(id).state)
    }

    @Test
    fun redirectLoopTracker() {
        trackerAttack(TrackerBehavior.RedirectLoop, "TOO_MANY_REDIRECTS")
    }

    @Test
    fun redirectToLoopbackAndMetadataService() {
        val victim = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        closeables += victim
        for (target in listOf("http://127.0.0.1:${victim.localPort}/announce", "http://169.254.169.254/latest/meta-data/", "http://[::1]:${victim.localPort}/")) {
            val h = trackerAttack(TrackerBehavior.RedirectTo(target), "BLOCKED_ADDRESS")
            assertFalse(h.transport.blocking.any { it.port == victim.localPort || it.address.hostAddress == "169.254.169.254" }, "${h.transport.blocking}")
        }
    }

    /** interval 0·음수는 최소(120초)로, 거대한 값은 최대(2시간)로 묶는다 — 가상 시계로 본다. */
    @Test
    fun intervalsAreClampedByTheEngine() {
        for ((interval, expectAfterMs) in listOf(0L to 120_000L, -30L to 120_000L, 4_000_000_000L to 2 * 3_600_000L)) {
            val tracker = keep(FakeHttpTracker())
            tracker.interval = interval
            val t = torrent(tracker)
            val clock = ManualClock()
            val h = keep(EngineHarness(clock = clock, config = TorrentConfig(dht = false)))
            h.allow(tracker.port)
            h.engine.add(t.torrentBytes)
            waitUntil(what = "first announce") { tracker.announces.size == 1 }
            Thread.sleep(300)
            clock.advance(expectAfterMs - 5_000)
            h.engine.networkChanged()
            Thread.sleep(500)
            assertEquals(1, tracker.announces.size, "interval $interval: 너무 일찍 다시 묻지 않는다")
            clock.advance(10_000)
            h.engine.networkChanged()
            waitUntil(what = "second announce for $interval") { tracker.announces.size == 2 }
        }
    }

    /** DHT 노드가 깨진 답·틀린 transaction id 를 준다 — 엔진은 멀쩡하다. */
    @Test
    fun hostileDhtNodes() {
        val tracker = keep(FakeHttpTracker())
        val t = torrent(tracker)
        val good = keep(FakeSeeder(t, bindAddress = lo(2)))
        tracker.peers = listOf(good.address)
        val malformed = keep(FakeDhtNode(ByteArray(20) { 1 })).also { it.behavior = DhtBehavior.Malformed }
        val wrongTid = keep(FakeDhtNode(ByteArray(20) { 2 })).also { it.behavior = DhtBehavior.WrongTransactionId }
        val config = TorrentConfig(dht = true, dhtBootstrap = listOf("127.0.0.1" to malformed.port, "127.0.0.1" to wrongTid.port))
        val h = keep(EngineHarness(config = config))
        h.allow(tracker.port, good.port, malformed.port, wrongTid.port)
        val id = h.engine.add(t.torrentBytes)
        h.awaitDone(id)
        assertTrue(malformed.queries.isNotEmpty() || wrongTid.queries.isNotEmpty())
        assertEquals(0, h.status(id).peerSources["dht"] ?: 0)
    }

    /** 핸드셰이크 뒤 마구잡이 프레임을 보내는 피어(고정 씨앗) — 엔진은 그 연결을 끊고, 정직한 시더에게서 다 받는다. */
    @Test
    fun garbageFramesFuzz() {
        val tracker = keep(FakeHttpTracker())
        val t = torrent(tracker)
        val rnd = Random(11)
        val garbageServers = (0 until 6).map { n ->
            val ss = ServerSocket(0, 5, lo(10 + n))
            closeables += ss
            Thread {
                // MSE 시도(끊김) 뒤 평문으로 다시 붙는다 — 몇 번 받는다.
                repeat(3) {
                    runCatching {
                    ss.accept().use { s ->
                        s.soTimeout = 5_000
                        val input = s.getInputStream()
                        val out = DataOutputStream(s.getOutputStream())
                        // MSE 는 모른다 — 첫 바이트가 19 가 아니면 닫는다(엔진이 평문으로 다시 붙는다).
                        val first = input.read()
                        if (first != 19) return@runCatching
                        input.readNBytes(67)
                        out.write(19)
                        out.write("BitTorrent protocol".toByteArray())
                        out.write(ByteArray(8).also { it[7] = 0x04; it[5] = 0x10 })
                        out.write(t.infoHash)
                        out.write("-ZZ0000-garbagegarbag".toByteArray().copyOf(20))
                        val local = Random(rnd.nextLong() + n)
                        repeat(200) {
                            val len = when (local.nextInt(5)) {
                                0 -> local.nextInt()
                                1 -> 0
                                else -> local.nextInt(40)
                            }
                            out.writeInt(len)
                            val body = ByteArray(maxOf(0, minOf(len, 64))).also(local::nextBytes)
                            if (body.isNotEmpty()) body[0] = listOf(0, 1, 4, 5, 6, 7, 13, 14, 15, 16, 17, 20, 99)[local.nextInt(13)].toByte()
                            out.write(body)
                        }
                        out.flush()
                        Thread.sleep(500)
                    }
                    }
                }
            }.apply { isDaemon = true }.start()
            InetSocketAddress(ss.inetAddress, ss.localPort)
        }
        val good = keep(FakeSeeder(t, bindAddress = lo(2)))
        tracker.peers = garbageServers + good.address
        val h = keep(EngineHarness(config = TorrentConfig(dht = false)))
        h.allow(tracker.port, good.port, *garbageServers.map { it.port }.toIntArray())
        val id = h.engine.add(t.torrentBytes)
        h.awaitDone(id, 30_000)
        assertContent(h, t, id)
        assertEquals(0, h.errors.size)
    }
}
