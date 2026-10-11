package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.fake.Encryption
import io.github.donggi.iroirobrowser.torrent.fake.FakeDhtNode
import io.github.donggi.iroirobrowser.torrent.fake.FakeFile
import io.github.donggi.iroirobrowser.torrent.fake.FakeHttpTracker
import io.github.donggi.iroirobrowser.torrent.fake.FakeSeeder
import io.github.donggi.iroirobrowser.torrent.fake.FakeTorrent
import io.github.donggi.iroirobrowser.torrent.fake.FakeUdpTracker
import io.github.donggi.iroirobrowser.torrent.fake.FakeWebSeed
import io.github.donggi.iroirobrowser.torrent.fake.SeederConnection
import io.github.donggi.iroirobrowser.torrent.fake.SeederOptions
import io.github.donggi.iroirobrowser.torrent.fake.WebSeedOptions
import io.github.donggi.iroirobrowser.torrent.wire.Wire
import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 엔진 전체를 루프백의 가짜 군집(따로 지은 — `fake` 꾸러미, 엔진의 파서·상수를 쓰지 않는다)에 붙여 본다.
 */
class EngineIntegrationTest {

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

    private fun harness(config: TorrentConfig = TorrentConfig(dht = false), clock: io.github.donggi.iroirobrowser.torrent.net.Clock = io.github.donggi.iroirobrowser.torrent.net.Clock.SYSTEM, root: File = tempDir("torrent-engine")) =
        keep(EngineHarness(clock = clock, config = config, root = root))

    /** 파일 셋(마지막 조각은 짧다) — 파일 경계가 조각 가운데에 걸린다. */
    private fun threeFiles(announce: String? = null, urlList: List<String>? = null, httpSeeds: List<String>? = null): FakeTorrent = FakeTorrent(
        name = "set",
        files = listOf(
            FakeFile(listOf("one.bin"), FakeTorrent.data(1, 50_000)),
            FakeFile(listOf("dir", "two.bin"), FakeTorrent.data(2, 70_000)),
            FakeFile(listOf("three.bin"), FakeTorrent.data(3, 30_000)),
        ),
        pieceLength = 16384,
        announce = announce,
        urlList = urlList,
        httpSeeds = httpSeeds,
    )

    private fun assertFilesEqual(h: EngineHarness, t: FakeTorrent, id: String, indices: Iterable<Int> = t.layout.indices) {
        for (i in indices) {
            val entry = t.layout[i]
            if (entry.isPad) continue
            val f = assertNotNull(h.engine.fileOf(id, i))
            assertTrue(f.isFile, "file $i exists")
            val expected = t.payload.copyOfRange(entry.offset.toInt(), (entry.offset + entry.length).toInt())
            assertContentEquals(expected, f.readBytes(), "file $i content")
        }
    }

    /** 상대에게 보낸 것 가운데 올리기에 쓰이는 메시지가 없다(unchoke·piece·allowed_fast·have all·suggest). */
    private fun assertNeverUploaded(c: SeederConnection) {
        val forbidden = setOf(Wire.UNCHOKE, Wire.PIECE, Wire.ALLOWED_FAST, Wire.HAVE_ALL, Wire.SUGGEST, Wire.CHOKE)
        for (m in c.received) assertFalse(m.id in forbidden, "client sent forbidden message ${m.id}")
        for (m in c.received.filter { it.id == Wire.EXTENDED }) {
            val p = m.payload
            if (p.isNotEmpty() && p[0].toInt() != 0) {
                val s = String(p, 1, p.size - 1, Charsets.ISO_8859_1)
                assertFalse("msg_typei1e" in s, "ut_metadata data 를 보내지 않는다")
            }
        }
        c.clientExtHandshake?.let { hs ->
            assertFalse(hs.containsKey("metadata_size"), "metadata_size 를 알리지 않는다")
        }
    }

    @Test
    fun downloadsFromTrackerSeedersInFileOrderAndUploadsNothing() {
        val tracker = keep(FakeHttpTracker())
        val t = threeFiles(announce = tracker.announceUrl)
        val mse = keep(FakeSeeder(t, SeederOptions(encryption = Encryption.ACCEPT_BOTH, requestFromClient = true, requestMetadataFromClient = true), bindAddress = lo(2)))
        val plain = keep(FakeSeeder(t, SeederOptions(encryption = Encryption.PLAIN_ONLY), bindAddress = lo(3)))
        tracker.peers = listOf(mse.address, plain.address)
        val h = harness()
        h.allow(tracker.port, mse.port, plain.port)
        val id = h.engine.add(t.torrentBytes)
        assertEquals(t.infoHashHex, id)
        h.awaitDone(id)
        assertFilesEqual(h, t, id)
        // 파일 하나씩, 토렌트 차례로 다 받았다고 알렸다.
        waitUntil(what = "file events") { h.completedFiles.size == 3 }
        assertEquals(listOf(0, 1, 2), h.completedFiles.map { it.first })
        waitUntil(what = "finished") { h.finished.size == 1 }

        // 트래커: started → completed → stopped, uploaded 는 늘 0, ip 를 보내지 않는다, 우리 peer id 머리.
        waitUntil(what = "stopped announce") { tracker.announces.any { it.event == "stopped" } }
        val events = tracker.announces.map { it.event }
        assertEquals("started", events.first())
        assertTrue(events.indexOf("completed") in 0 until events.indexOf("stopped"), "$events")
        for (a in tracker.announces) {
            assertEquals(0L, a.uploaded)
            assertFalse(a.params.containsKey("ip") || a.params.containsKey("ipv4") || a.params.containsKey("ipv6"))
            assertEquals("-IW0100-", String(a.peerId!!, 0, 8, Charsets.ISO_8859_1))
        }
        // 다 받으면 모든 연결을 끊는다(시드로 남지 않는다).
        waitUntil(what = "client closed all") { (mse.connections + plain.connections).all { it.isClosed } }
        assertEquals(0, h.status(id).connectedPeers)

        // MSE — 받는 피어와는 RC4, 평문만 받는 피어와는 MSE 를 시도했다가 평문으로 다시.
        assertTrue(mse.connections.any { it.encryption == "mse-rc4" }, mse.connections.map { it.encryption }.toString())
        assertTrue(plain.connections.any { it.encryption == "refused" } && plain.connections.any { it.encryption == "plain" }, plain.connections.map { it.encryption }.toString())
        val st = h.status(id)
        assertTrue(st.mseSucceeded >= 1 && st.plainFallbacks >= 1, "mse ${st.mseSucceeded} fallback ${st.plainFallbacks}")

        // 올리기 0 — 보낸 메시지·예약 비트·요청에 대한 답.
        for (c in mse.connections + plain.connections) {
            assertNeverUploaded(c)
            c.clientReserved?.let { r ->
                assertTrue(r[5].toInt() and 0x10 != 0 && r[7].toInt() and 0x04 != 0)
                assertEquals(0, r[7].toInt() and 0x01, "DHT 비트 없음")
            }
        }
        val asked = mse.connections.first { it.encryption == "mse-rc4" }
        val rejects = asked.received.filter { it.id == Wire.REJECT }
        assertEquals(3, rejects.size, "가짜가 보낸 요청 셋 모두 reject")
        // .torrent 로 넣은 것은 메타데이터가 필요 없어 ut_metadata 를 알리지도 않는다.
        assertEquals(emptyMap<Any, Any>(), asked.clientExtHandshake?.get("m"))
    }

    /** '100% 다운로드 가능한 파일들에 한하여, 파일 하나씩, 조각 처음부터 끝까지 순차적으로' — 다 구할 수 없는 파일은 미룬다. */
    @Test
    fun deferredFileWaitsUntilItsPiecesAppear() {
        val tracker = keep(FakeHttpTracker())
        val t = threeFiles(announce = tracker.announceUrl)
        // 조각 0(one.bin 의 첫 조각)이 없는 시더 — one.bin 은 시작할 수 없다.
        val partialHave = BooleanArray(t.pieceCount) { it != 0 }
        val partial = keep(FakeSeeder(t, SeederOptions(have = partialHave, useHaveAll = false), bindAddress = lo(2)))
        tracker.peers = listOf(partial.address)
        val h = harness()
        h.allow(tracker.port, partial.port)
        val id = h.engine.add(t.torrentBytes)
        // two.bin·three.bin 을 먼저 다 받고, one.bin 은 기다린다.
        waitUntil(what = "two and three") { h.completedFiles.map { it.first }.containsAll(listOf(1, 2)) }
        assertEquals(listOf(1, 2), h.completedFiles.map { it.first })
        h.awaitState(id, TorrentState.WAITING)
        assertEquals("waiting", h.status(id).files[0].state)
        // 시더가 조각 0 을 얻었다 — 이제 one.bin 을 받는다.
        val full = keep(FakeSeeder(t, bindAddress = lo(3)))
        tracker.peers = listOf(partial.address, full.address)
        h.allow(full.port)
        h.engine.pause(id)
        h.engine.resume(id)
        h.awaitDone(id)
        // 파일의 알림은 디스크에 내린 뒤에 온다 — '다 받음' 상태보다 늦을 수 있다(TorrentListener.onFinished).
        waitUntil(what = "file 0 event") { h.completedFiles.size == 3 }
        assertEquals(listOf(1, 2, 0), h.completedFiles.map { it.first })
        assertFilesEqual(h, t, id)
        // 조각은 파일마다 처음부터 차례로 청했다 — 시더가 받은 request 의 조각 번호가 줄지 않는다(엔드게임의 겹침은 빼고).
        // 미룬 one.bin 의 조각(1·2 — 그 시더가 가졌어도)은 one.bin 을 시작하기 전에는 청하지 않았다. 첫 요청은 two.bin 의 첫 조각(3).
        val firstConnection = partial.connections.first().received.filter { it.id == Wire.REQUEST }.map { it.int(0) }
        assertEquals(3, firstConnection.first(), "$firstConnection")
        assertTrue(firstConnection.none { it in 1..2 }, "미룬 파일의 조각을 청하지 않았다: $firstConnection")
    }

    @Test
    fun webSeedOnlyAndBadWebSeedIsDropped() {
        // url-list 는 info 밖이라 info-hash 가 같다 — 웹 시드를 먼저 세우고 그 주소를 넣은 토렌트를 짓는다.
        val good = keep(FakeWebSeed(threeFiles()))
        val t = threeFiles(urlList = listOf(good.baseUrl))
        val h = harness()
        h.allow(good.port)
        val id = h.engine.add(t.torrentBytes)
        h.awaitDone(id)
        assertFilesEqual(h, t, id)
        assertTrue(good.requests.isNotEmpty() && good.requests.all { it.range?.startsWith("bytes=") == true }, "Range 로만 청한다")
        assertEquals(1, h.status(id).webSeeds)

        // 틀린 바이트를 주는 웹 시드는 버리고 피어에게서 받는다.
        val tracker = keep(FakeHttpTracker())
        val bad = keep(FakeWebSeed(threeFiles(), WebSeedOptions(corruptRanges = listOf(20_000L..20_100L))))
        val t3 = threeFiles(announce = tracker.announceUrl, urlList = listOf(bad.baseUrl))
        val peer = keep(FakeSeeder(t3))
        tracker.peers = listOf(peer.address)
        val h2 = harness()
        h2.allow(bad.port, tracker.port, peer.port)
        val id3 = h2.engine.add(t3.torrentBytes)
        h2.awaitDone(id3)
        assertFilesEqual(h2, t3, id3)
        val st = h2.status(id3)
        assertTrue(st.hashFailures >= 1, "해시 실패를 보았다")
        assertEquals(0, st.webSeeds, "그 웹 시드를 버렸다")
    }

    @Test
    fun udpTrackerWithRetransmitOnVirtualClock() {
        val udp = keep(FakeUdpTracker())
        udp.dropFirst = 1
        val t = threeFiles(announce = udp.announceUrl)
        val seeder = keep(FakeSeeder(t))
        udp.peers = listOf(seeder.address)
        val clock = ManualClock()
        val h = harness(clock = clock)
        h.allow(udp.port, seeder.port)
        val id = h.engine.add(t.torrentBytes)
        waitUntil(what = "first udp packet") { udp.connects.isNotEmpty() }
        Thread.sleep(200)
        assertTrue(udp.announces.isEmpty(), "첫 connect 는 버려졌다 — 아직 announce 없음")
        clock.advance(16_000) // BEP 15 — 15초 뒤 다시
        h.engine.networkChanged() // 루프를 깨운다
        h.awaitDone(id)
        val ann = udp.announces
        assertTrue(ann.isNotEmpty())
        assertEquals(2, ann.first().event, "started")
        assertTrue(ann.all { it.uploaded == 0L })
        assertFilesEqual(h, t, id)
    }

    @Test
    fun magnetFetchesMetadataFromPeersAndStoresTorrent() {
        val t = threeFiles()
        val seeder = keep(FakeSeeder(t, SeederOptions(requestMetadataFromClient = true)))
        val h = harness()
        h.allow(seeder.port)
        val magnet = t.magnet(peers = listOf("127.0.0.1:${seeder.port}"))
        val id = h.engine.addMagnet(magnet)
        h.awaitDone(id)
        assertFilesEqual(h, t, id)
        waitUntil(what = "metadata event") { "metadata 3" in h.events }
        // 받은 info 를 .torrent 로 남겼다(다음에 마그넷 없이 되살린다).
        val saved = File(h.root, "state/$id.torrent")
        waitUntil(what = "saved torrent") { saved.isFile }
        assertEquals(id, io.github.donggi.iroirobrowser.torrent.meta.Metainfo.parse(saved.readBytes()).infoHashHex)
        for (c in seeder.connections) {
            assertNeverUploaded(c)
            assertEquals(3L, (c.clientExtHandshake?.get("m") as? Map<*, *>)?.get("ut_metadata"), "메타데이터가 필요할 때 ut_metadata 를 알린다")
        }
        val replies = seeder.connections.flatMap { it.clientUtMetadataReplies }
        assertTrue(replies.isNotEmpty() && replies.all { it["msg_type"] == 2L }, "남이 청한 메타데이터는 거절한다: $replies")
    }

    @Test
    fun dhtFindsPeersForTrackerlessMagnet() {
        val t = threeFiles()
        val seeder = keep(FakeSeeder(t))
        // 부트스트랩 노드 → (가까운 노드 + 사설 주소의 노드) → 가까운 노드가 피어를 준다.
        val near = keep(FakeDhtNode(t.infoHash.copyOf().also { it[19] = (it[19].toInt() xor 1).toByte() }))
        near.peersByInfoHash[t.infoHashHex] = listOf(seeder.address, InetSocketAddress("10.0.0.7", 6881), InetSocketAddress("127.0.0.1", 1))
        val boot = keep(FakeDhtNode(ByteArray(20) { 0x55 }))
        boot.nodes = listOf(
            near.nodeId to near.address,
            ByteArray(20) { 7 } to InetSocketAddress("192.168.0.9", 6881),
            ByteArray(20) { 8 } to InetSocketAddress("127.0.0.1", 9), // 루프백의 다른 포트 — 거른다
        )
        val config = TorrentConfig(dht = true, dhtBootstrap = listOf("127.0.0.1" to boot.port))
        val h = harness(config = config)
        h.allow(boot.port, near.port, seeder.port)
        val id = h.engine.addMagnet(t.magnet())
        h.awaitDone(id)
        assertFilesEqual(h, t, id)
        val queries = boot.queries + near.queries
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.all { it.readOnly == 1L }, "모든 질의에 ro=1")
        assertTrue(queries.all { it.method == "get_peers" })
        assertFalse(h.transport.touched("192.168.0.9") || h.transport.touched("10.0.0.7"), "사설 주소에 보내지 않았다")
        assertFalse(h.transport.udp.any { it.port == 9 } || h.transport.tcp.any { it.port == 1 })
        assertTrue((h.status(id).peerSources["dht"] ?: 0) >= 1)
    }

    @Test
    fun resumeAfterRestartContinuesWithoutRefetching() {
        val tracker = keep(FakeHttpTracker())
        val t = threeFiles(announce = tracker.announceUrl)
        val root = tempDir("torrent-resume")
        // 첫 엔진 — 블록 여덟 개(조각 넷)를 받은 뒤 시더가 끊는다.
        val first = keep(FakeSeeder(t, SeederOptions(disconnectAfterBlocks = 8)))
        tracker.peers = listOf(first.address)
        val h1 = EngineHarness(config = TorrentConfig(dht = false), root = root)
        h1.allow(tracker.port, first.port)
        val id = h1.engine.add(t.torrentBytes)
        waitUntil(what = "some pieces") { h1.status(id).doneBytes >= 4 * 16384 }
        h1.engine.close()
        // 다음 엔진 — 멈춤으로 되살아나고, 이어 받으면 이미 받은 조각을 다시 청하지 않는다.
        val second = keep(FakeSeeder(t))
        tracker.peers = listOf(second.address)
        val h2 = keep(EngineHarness(config = TorrentConfig(dht = false), root = root, start = false))
        h2.allow(tracker.port, second.port)
        val restored = h2.engine.start()
        assertEquals(1, restored.size)
        // 되살린 직후는 크기 대조(일꾼 스레드) 중이다 — 끝나면 멈춤.
        assertTrue(restored[0].state == TorrentState.CHECKING || restored[0].state == TorrentState.PAUSED, "${restored[0].state}")
        h2.awaitState(id, TorrentState.PAUSED)
        assertTrue(h2.status(id).doneBytes >= 4 * 16384)
        h2.engine.resume(id)
        h2.awaitDone(id)
        assertFilesEqual(h2, t, id)
        val again = second.connections.flatMap { c -> c.received.filter { it.id == Wire.REQUEST }.map { it.int(0) } }.toSet()
        assertTrue((0..3).none { it in again }, "받은 조각 0..3 을 다시 청하지 않았다: $again")
    }

    @Test
    fun resumeRehashesFilesWhoseSizeChanged() {
        val tracker = keep(FakeHttpTracker())
        val t = threeFiles(announce = tracker.announceUrl)
        val root = tempDir("torrent-rehash")
        val seeder = keep(FakeSeeder(t))
        tracker.peers = listOf(seeder.address)
        val h1 = EngineHarness(config = TorrentConfig(dht = false), root = root)
        h1.allow(tracker.port, seeder.port)
        val id = h1.engine.add(t.torrentBytes, selectedFiles = setOf(0, 1))
        h1.awaitDone(id)
        val two = h1.engine.fileOf(id, 1)!!
        h1.engine.close()
        // 두 번째 파일을 잘라 둔다(앱 밖에서 망가졌다).
        java.io.RandomAccessFile(two, "rw").use { it.setLength(10_000) }
        val h2 = keep(EngineHarness(config = TorrentConfig(dht = false), root = root, start = false))
        h2.allow(tracker.port, seeder.port)
        h2.engine.start().single()
        // 크기가 어긋난 two.bin 의 조각을 다시 해시해 잘린 부분을 잃는다 — 다 받음이 풀리고 멈춤으로.
        h2.awaitState(id, TorrentState.PAUSED)
        val after = h2.status(id)
        assertTrue(after.doneBytes < after.wantedBytes, "${after.doneBytes} < ${after.wantedBytes}")
        // one.bin 의 마지막 조각(3)은 two.bin 의 앞과 함께다 — 그 조각을 확인할 수 없으니 one.bin 도 '다 받음' 이 풀린다(다시 받아 다시 알린다).
        assertTrue(after.files[0].state != "done" && after.files[1].state != "done", after.files.map { it.state }.toString())
        h2.engine.resume(id)
        h2.awaitDone(id)
        assertFilesEqual(h2, t, id, listOf(0, 1))
    }

    @Test
    fun selectionCreatesOnlyChosenFilesAndNetworkHookPauses() {
        val tracker = keep(FakeHttpTracker())
        val t = threeFiles(announce = tracker.announceUrl)
        val seeder = keep(FakeSeeder(t))
        tracker.peers = listOf(seeder.address)
        val h = harness()
        h.allow(tracker.port, seeder.port)
        h.network.set(false)
        h.engine.networkChanged()
        val id = h.engine.add(t.torrentBytes, selectedFiles = setOf(1))
        h.awaitState(id, TorrentState.NETWORK_WAIT)
        Thread.sleep(300)
        assertTrue(seeder.connections.isEmpty(), "망 조건이 거짓이면 붙지 않는다")
        assertTrue(tracker.announces.isEmpty())
        h.network.set(true)
        h.engine.networkChanged()
        h.awaitDone(id)
        assertFilesEqual(h, t, id, listOf(1))
        assertFalse(h.engine.fileOf(id, 0)!!.exists(), "고르지 않은 파일은 만들지 않는다")
        assertFalse(h.engine.fileOf(id, 2)!!.exists())
        val st = h.status(id)
        assertEquals(listOf("skipped", "done", "skipped"), st.files.map { it.state })
        assertEquals(70_000, st.wantedBytes)
        // 파일의 알림은 '다 받음' 상태보다 늦을 수 있다(2026-10-10 — lint·R8 과 함께 돌 때 한 번 여기서 빈 목록을 봤다).
        waitUntil(what = "file event") { h.completedFiles.isNotEmpty() }
        assertEquals(listOf(1), h.completedFiles.map { it.first })
    }

    /** BEP 17(Hoffman 꼴 httpseeds — ?info_hash=…&piece=N) 웹 시드만으로. */
    @Test
    fun bep17HttpSeed() {
        val seed = keep(FakeWebSeed(threeFiles(), WebSeedOptions(supportBep17 = true)))
        val t = threeFiles(httpSeeds = listOf(seed.bep17Url))
        assertEquals(threeFiles().infoHashHex, t.infoHashHex, "httpseeds 는 info 밖이다")
        val h = harness()
        h.allow(seed.port)
        val id = h.engine.add(t.torrentBytes)
        h.awaitDone(id)
        assertFilesEqual(h, t, id)
        assertTrue(seed.requests.isNotEmpty() && seed.requests.all { "piece=" in it.rawTarget && "info_hash=" in it.rawTarget }, seed.requests.map { it.rawTarget }.toString())
    }

    /** 앱이 다 받은 파일을 옮기고 releaseFile 을 부른다 — 엔진은 그 파일을 다시 만들거나 쓰지 않는다. */
    @Test
    fun releasedFileIsNotRecreated() {
        val tracker = keep(FakeHttpTracker())
        val t = threeFiles(announce = tracker.announceUrl)
        val seeder = keep(FakeSeeder(t))
        tracker.peers = listOf(seeder.address)
        val h = harness()
        h.allow(tracker.port, seeder.port)
        val moved = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val id = h.engine.add(t.torrentBytes, paused = true)
        // 앱의 몫 — 파일 하나 다 받음을 들으면 옮기고(여기서는 지우고) 알린다.
        val mover = Thread {
            while (!Thread.currentThread().isInterrupted) {
                for ((index, file) in h.completedFiles) {
                    if (index in moved) continue
                    val copy = File(h.root, "moved-$index")
                    file.copyTo(copy, overwrite = true)
                    file.delete()
                    h.engine.releaseFile(id, index)
                    moved += index
                }
                runCatching { Thread.sleep(10) }.onFailure { return@Thread }
            }
        }.apply { isDaemon = true; start() }
        h.engine.resume(id)
        h.awaitDone(id)
        waitUntil(what = "all moved") { moved.size == 3 }
        mover.interrupt()
        for (i in 0..2) {
            assertFalse(h.engine.fileOf(id, i)!!.exists(), "옮긴 파일 $i 를 다시 만들지 않았다")
            val e = t.layout[i]
            assertContentEquals(t.payload.copyOfRange(e.offset.toInt(), (e.offset + e.length).toInt()), File(h.root, "moved-$i").readBytes())
        }
        assertTrue(h.status(id).files.all { it.released })
    }

    /** private 토렌트는 마그넷으로 와도 메타데이터를 받는 순간 거부하고 연결을 끊는다. */
    @Test
    fun privateMagnetIsRefusedAfterMetadata() {
        val t = FakeTorrent("p", listOf(FakeFile(listOf("p.bin"), FakeTorrent.data(5, 40_000))), 16384, singleFile = true, isPrivate = true)
        val seeder = keep(FakeSeeder(t))
        val h = harness()
        h.allow(seeder.port)
        val id = h.engine.addMagnet(t.magnet(peers = listOf("127.0.0.1:${seeder.port}")))
        h.awaitState(id, TorrentState.ERROR)
        assertEquals(TorrentError.PRIVATE_TORRENT, h.status(id).error)
        waitUntil(what = "connections closed") { seeder.connections.all { it.isClosed } }
        assertTrue(h.errors.any { it.first == TorrentError.PRIVATE_TORRENT })
        assertFalse(h.engine.fileOf(id, 0)?.exists() == true)
    }

    @Test
    fun queueAndRemove() {
        val tracker = keep(FakeHttpTracker())
        val torrents = (1..3).map { n ->
            FakeTorrent("q$n", listOf(FakeFile(listOf("f$n.bin"), FakeTorrent.data(100 + n, 40_000))), 16384, singleFile = true, announce = tracker.announceUrl)
        }
        // 시더가 없다 — 셋 다 받을 수 없어 기다린다. 동시 둘, 셋째는 차례를 기다린다.
        val h = harness(config = TorrentConfig(dht = false, maxActiveTorrents = 2))
        h.allow(tracker.port)
        val ids = torrents.map { h.engine.add(it.torrentBytes) }
        waitUntil(what = "states") { h.status(ids[2]).state == TorrentState.QUEUED && h.status(ids[0]).state != TorrentState.QUEUED }
        assertTrue(h.status(ids[1]).state != TorrentState.QUEUED)
        h.engine.remove(ids[0], deleteData = true)
        waitUntil(what = "third admitted") { h.status(ids[2]).state != TorrentState.QUEUED }
        assertEquals(2, h.engine.statuses().size)
        waitUntil(what = "state files removed") { !File(h.root, "state/${ids[0]}.resume").exists() && !File(h.root, "state/${ids[0]}.torrent").exists() }
        val dup = runCatching { h.engine.add(torrents[1].torrentBytes) }.exceptionOrNull() as? TorrentException
        assertEquals(TorrentError.DUPLICATE, dup?.error)
    }
}
