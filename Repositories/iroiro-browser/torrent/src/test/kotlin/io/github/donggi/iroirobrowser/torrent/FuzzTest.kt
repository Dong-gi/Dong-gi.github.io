package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.bencode.BencodeException
import io.github.donggi.iroirobrowser.torrent.dht.Krpc
import io.github.donggi.iroirobrowser.torrent.meta.MagnetLink
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.tracker.HttpTracker
import io.github.donggi.iroirobrowser.torrent.tracker.TrackerException
import io.github.donggi.iroirobrowser.torrent.wire.ExtensionHandshake
import io.github.donggi.iroirobrowser.torrent.wire.Inbound
import io.github.donggi.iroirobrowser.torrent.wire.MetadataMessage
import io.github.donggi.iroirobrowser.torrent.wire.Wire
import java.util.Base64
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 고정 씨앗의 작은 퍼저 — 올바른 입력을 뒤틀어(바이트 뒤집기·끼우기·지우기·덩이 겹치기·자르기·무작위) 파서에 넣고, **선언한 예외 밖의 것**
 * (넘침·인덱스·널·스택·메모리)이 나지 않는지 본다. 씨앗이 고정이라 실패는 늘 다시 난다.
 */
class FuzzTest {

    private fun mutate(src: ByteArray, rnd: Random): ByteArray {
        var b = src.copyOf()
        repeat(1 + rnd.nextInt(4)) {
            if (b.isEmpty()) b = byteArrayOf(rnd.nextInt(256).toByte())
            when (rnd.nextInt(7)) {
                0 -> b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte()
                1 -> { // 끼우기
                    val at = rnd.nextInt(b.size + 1)
                    val ins = ByteArray(1 + rnd.nextInt(8)).also(rnd::nextBytes)
                    b = b.copyOfRange(0, at) + ins + b.copyOfRange(at, b.size)
                }
                2 -> { // 지우기
                    val at = rnd.nextInt(b.size)
                    val n = minOf(b.size - at, 1 + rnd.nextInt(16))
                    b = b.copyOfRange(0, at) + b.copyOfRange(at + n, b.size)
                }
                3 -> { // 덩이 겹치기
                    val at = rnd.nextInt(b.size)
                    val n = minOf(b.size - at, 1 + rnd.nextInt(32))
                    val chunk = b.copyOfRange(at, at + n)
                    val to = rnd.nextInt(b.size + 1)
                    b = b.copyOfRange(0, to) + chunk + b.copyOfRange(to, b.size)
                }
                4 -> b = b.copyOf(rnd.nextInt(b.size + 1)) // 자르기
                5 -> { // 숫자를 큰 숫자로
                    val at = (0 until b.size).firstOrNull { b[(it + rnd.nextInt(b.size)) % b.size] in '0'.code.toByte()..'9'.code.toByte() }
                    if (at != null) {
                        val big = "9".repeat(1 + rnd.nextInt(25)).toByteArray()
                        b = b.copyOfRange(0, at) + big + b.copyOfRange(at + 1, b.size)
                    }
                }
                else -> { // 구조 문자
                    val at = rnd.nextInt(b.size)
                    b[at] = "ldie:0123456789-".random(kotlin.random.Random(rnd.nextLong())).code.toByte()
                }
            }
        }
        return b
    }

    private inline fun fuzz(name: String, seeds: List<ByteArray>, iterations: Int, seed: Long, body: (ByteArray) -> Unit) {
        val rnd = Random(seed)
        val t0 = System.nanoTime()
        for (i in 0 until iterations) {
            val input = mutate(seeds[rnd.nextInt(seeds.size)], rnd)
            try {
                body(input)
            } catch (e: Throwable) {
                fail("$name #$i: ${e::class.java.name}: ${e.message} — input ${hex(input.copyOf(minOf(200, input.size)))}")
            }
        }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 60_000, "$name 가 너무 느리다")
    }

    private val torrents: List<ByteArray> = PythonVectors.torrents.map { Base64.getDecoder().decode(it.torrentBase64) }

    @Test
    fun bencode() {
        val seeds = PythonVectors.bencode.filter { it.valid }.map { unhex(it.inputHex) } + torrents
        fuzz("bencode", seeds, 30_000, 1L) { input ->
            try {
                Bencode.decode(input)
            } catch (_: BencodeException) {
            }
            try {
                Bencode.decodePrefix(input, 0, input.size, Bencode.Limits.PEER)
            } catch (_: BencodeException) {
            }
        }
    }

    @Test
    fun metainfo() {
        fuzz("metainfo", torrents, 20_000, 2L) { input ->
            val m = try {
                Metainfo.parse(input)
            } catch (_: TorrentException) {
                return@fuzz
            }
            // 받아들였으면 불변식
            check(m.pieceCount >= 1 && m.totalLength > 0)
            var off = 0L
            for (f in m.files) {
                check(f.offset == off && f.length >= 0)
                off += f.length
                for (c in f.path) check(c.isNotEmpty() && c != "." && c != ".." && '/' !in c && '\\' !in c && ':' !in c) { "path $c" }
            }
            check(off == m.totalLength)
            check((m.totalLength - 1) / m.pieceLength + 1 == m.pieceCount.toLong())
        }
    }

    @Test
    fun magnet() {
        val seeds = PythonVectors.magnets.map { it.uri.toByteArray() }
        fuzz("magnet", seeds, 20_000, 3L) { input ->
            try {
                MagnetLink.parse(String(input, Charsets.ISO_8859_1))
            } catch (_: TorrentException) {
            }
        }
    }

    @Test
    fun krpc() {
        val seeds = PythonVectors.krpc.map { unhex(it.bytesHex) }
        fuzz("krpc", seeds, 30_000, 4L) { input -> Krpc.parse(input) }
    }

    @Test
    fun trackerResponses() {
        val seeds = listOf(
            Bencode.encode(mapOf("interval" to 1800L, "peers" to ByteArray(60) { it.toByte() }, "peers6" to ByteArray(36))),
            Bencode.encode(mapOf("interval" to 60L, "peers" to listOf(mapOf("ip" to "1.2.3.4", "port" to 6881L)))),
            Bencode.encode(mapOf("failure reason" to "nope")),
        )
        fuzz("tracker", seeds, 20_000, 5L) { input ->
            try {
                HttpTracker.parse(input)
            } catch (_: TrackerException) {
            }
        }
    }

    /** 피어 메시지 — 길이 검사를 지난 프레임은 무엇이든 읽혀야 한다(예외 없이). 확장 메시지의 사전도. */
    @Test
    fun peerMessages() {
        val rnd = Random(6)
        for (i in 0 until 50_000) {
            val id = if (rnd.nextInt(4) == 0) rnd.nextInt(256) else listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 13, 14, 15, 16, 17, 20)[rnd.nextInt(16)]
            val pieceCount = if (rnd.nextBoolean()) 0 else 1 + rnd.nextInt(5000)
            val range = Wire.lengthRange(id, pieceCount)
            val len = if (rnd.nextInt(10) == 0) rnd.nextInt(Int.MAX_VALUE) else range.first + rnd.nextInt(minOf(64, range.last - range.first + 1))
            if (len !in range) continue // 엔진은 여기서 끊는다
            val frame = ByteArray(len).also(rnd::nextBytes)
            frame[0] = id.toByte()
            try {
                val m = Inbound.parse(frame)
                if (m is Inbound.Extended) {
                    ExtensionHandshake.parse(m.payload)
                    MetadataMessage.parse(m.payload)
                }
            } catch (e: Throwable) {
                fail("frame id $id len $len: $e")
            }
        }
        val seeds = listOf(
            Bencode.encode(mapOf("m" to mapOf("ut_metadata" to 3L), "metadata_size" to 1000L, "v" to "x")),
            Bencode.encode(mapOf("msg_type" to 1L, "piece" to 0L, "total_size" to 10L)) + ByteArray(10),
        )
        fuzz("extension", seeds, 20_000, 7L) { input ->
            ExtensionHandshake.parse(input)
            MetadataMessage.parse(input)
        }
    }
}
