package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.bencode.BBytes
import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.BInt
import io.github.donggi.iroirobrowser.torrent.bencode.BList
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.bencode.BencodeException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class BencodeTest {

    @Test
    fun pythonVectors() {
        for (c in PythonVectors.bencode) {
            val bytes = unhex(c.inputHex)
            val ok = runCatching { Bencode.decode(bytes) }.exceptionOrNull()
            if (c.valid) {
                if (ok != null) fail("${c.note}: rejected ($ok)")
            } else {
                if (ok == null) fail("${c.note}: accepted")
                assertTrue(ok is BencodeException, "${c.note}: $ok")
            }
        }
        assertTrue(PythonVectors.bencode.size >= 40)
    }

    @Test
    fun valuesAndSpans() {
        val src = "d4:infod6:lengthi12e4:name3:abce3:fooli-7e0:ee".toByteArray()
        val d = Bencode.decode(src) as BDict
        val info = d["info"] as BDict
        assertEquals("d6:lengthi12e4:name3:abce", String(src, info.start, info.end - info.start))
        assertEquals(12L, info.long("length"))
        assertEquals("abc", info.string("name"))
        val list = d["foo"] as BList
        assertEquals(-7L, (list.items[0] as BInt).value)
        assertEquals(0, (list.items[1] as BBytes).bytes.size)
    }

    @Test
    fun limits() {
        // 깊이 64 는 되고 65 는 안 된다.
        val ok = "l".repeat(64) + "e".repeat(64)
        Bencode.decode(ok.toByteArray())
        val deep = "l".repeat(65) + "e".repeat(65)
        assertFailsWith<BencodeException> { Bencode.decode(deep.toByteArray()) }
        // 토큰 상한
        val many = "l" + "i1e".repeat(100) + "e"
        assertFailsWith<BencodeException> { Bencode.decode(many.toByteArray(), Bencode.Limits(1000, 64, 50)) }
        // 크기 상한(읽기 전에)
        assertFailsWith<BencodeException> { Bencode.decode(ByteArray(2000) { '0'.code.toByte() }, Bencode.Limits(1000, 64, 50)) }
        // 길이는 할당 전에 남은 바이트와 견준다 — 큰 길이 한 줄로 메모리를 잡지 못한다.
        val e = assertFailsWith<BencodeException> { Bencode.decode("2000000000:x".toByteArray()) }
        assertTrue("longer than input" in e.message!!)
    }

    @Test
    fun prefixLeavesTrailingBytes() {
        val payload = "d8:msg_typei1e5:piecei0e10:total_sizei5ee".toByteArray() + byteArrayOf(1, 2, 3, 4, 5)
        val v = Bencode.decodePrefix(payload, 0, payload.size, Bencode.Limits.PEER) as BDict
        assertEquals(payload.size - 5, v.end)
        assertFailsWith<BencodeException> { Bencode.decode(payload) }
    }

    @Test
    fun encodeIsCanonical() {
        val bytes = Bencode.encode(mapOf("b" to 1L, "a" to listOf("x", 2), "Z" to byteArrayOf(0, -1)))
        assertContentEquals("d1:Z2:".toByteArray() + byteArrayOf(0, -1) + "1:al1:xi2ee1:bi1ee".toByteArray(), bytes)
        // 다시 읽어 같다
        val d = Bencode.decode(bytes) as BDict
        assertEquals(listOf("Z", "a", "b"), d.entries.keys.toList())
    }
}
