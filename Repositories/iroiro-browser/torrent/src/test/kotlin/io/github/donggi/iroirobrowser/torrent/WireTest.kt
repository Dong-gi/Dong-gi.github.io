package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.wire.ExtensionHandshake
import io.github.donggi.iroirobrowser.torrent.wire.Inbound
import io.github.donggi.iroirobrowser.torrent.wire.MetadataMessage
import io.github.donggi.iroirobrowser.torrent.wire.Outbound
import io.github.donggi.iroirobrowser.torrent.wire.Wire
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WireTest {

    @Test
    fun handshake() {
        val ih = ByteArray(20) { 1 }
        val id = "-IW0100-abcdefghijkl".toByteArray()
        val hs = Wire.handshake(ih, id)
        assertEquals(68, hs.size)
        assertEquals(19, hs[0].toInt())
        val parsed = Wire.parseHandshake(hs)!!
        assertTrue(parsed.supportsExtensions)
        assertTrue(parsed.supportsFast)
        assertEquals(0, parsed.reserved[7].toInt() and 0x01, "DHT 비트는 켜지 않는다(듣지 않는다)")
        assertContentEquals(ih, parsed.infoHash)
        assertNull(Wire.parseHandshake(hs.copyOf(67)))
        assertNull(Wire.parseHandshake(byteArrayOf(18) + hs.copyOfRange(1, 68)))
    }

    @Test
    fun lengthRanges() {
        assertEquals(1..1, Wire.lengthRange(Wire.CHOKE, 10))
        assertEquals(10..(9 + 16384), Wire.lengthRange(Wire.PIECE, 10))
        assertEquals(3..3, Wire.lengthRange(Wire.BITFIELD, 10)) // 1 + ceil(10/8)
        assertTrue(0xFFFFFFFFL > Wire.lengthRange(Wire.EXTENDED, 10).last)
        assertTrue(Wire.lengthRange(99, 10).last <= 1024)
    }

    @Test
    fun inboundParse() {
        fun frame(id: Int, vararg ints: Int, tail: ByteArray = ByteArray(0)): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(id)
            for (v in ints) out.write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))
            out.write(tail)
            return out.toByteArray()
        }
        val p = Inbound.parse(frame(Wire.PIECE, 3, 16384, tail = byteArrayOf(9, 9))) as Inbound.Piece
        assertEquals(3, p.index)
        assertEquals(16384, p.begin)
        assertContentEquals(byteArrayOf(9, 9), p.data)
        val r = Inbound.parse(frame(Wire.REQUEST, 1, 2, 3)) as Inbound.Request
        assertEquals(listOf(1, 2, 3), listOf(r.index, r.begin, r.length))
        assertTrue(Inbound.parse(frame(Wire.HAVE_ALL)) is Inbound.HaveAll)
        assertTrue(Inbound.parse(frame(200)) is Inbound.Unknown)
        val e = Inbound.parse(frame(Wire.EXTENDED, tail = byteArrayOf(0) + "de".toByteArray())) as Inbound.Extended
        assertEquals(0, e.extensionId)
    }

    /**
     * **올리기 0 — 구조.** 이 모듈에서 [Outbound] 를 잇는 클래스는 이것뿐이다(컴파일된 클래스를 모두 훑는다). unchoke·piece·allowed_fast·
     * have_all·suggest·choke 와 ut_metadata 의 data 를 짓는 타입이 없다.
     */
    @Test
    fun outboundTypesAreOnlyTheseAndNeverUpload() {
        val root = File(Outbound::class.java.protectionDomain.codeSource.location.toURI())
        val found = root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.mapNotNull { f ->
            val name = f.relativeTo(root).path.replace(File.separatorChar, '.').removeSuffix(".class")
            val cls = runCatching { Class.forName(name, false, Outbound::class.java.classLoader) }.getOrNull() ?: return@mapNotNull null
            if (cls != Outbound::class.java && Outbound::class.java.isAssignableFrom(cls)) cls.simpleName else null
        }.toSet()
        assertEquals(
            setOf(
                "KeepAlive", "Interested", "NotInterested", "Have", "Bitfield", "Request", "Cancel", "HaveNone", "RejectRequest",
                "ExtendedHandshake", "MetadataRequest", "MetadataReject",
            ),
            found,
        )
        // 보내는 메시지 id 가 모두 '받기만' 의 것이다.
        val samples = listOf(
            Outbound.KeepAlive, Outbound.Interested, Outbound.NotInterested, Outbound.Have(1), Outbound.Bitfield(byteArrayOf(0)),
            Outbound.Request(0, 0, 16384), Outbound.Cancel(0, 0, 16384), Outbound.HaveNone, Outbound.RejectRequest(0, 0, 16384),
            Outbound.ExtendedHandshake(ExtensionHandshake.ours(true)), Outbound.MetadataRequest(2, 0), Outbound.MetadataReject(2, 0),
        )
        val forbidden = setOf(Wire.CHOKE, Wire.UNCHOKE, Wire.PIECE, Wire.ALLOWED_FAST, Wire.HAVE_ALL, Wire.SUGGEST, Wire.PORT)
        for (m in samples) {
            val b = m.encode()
            if (b.size == 4) continue
            val id = b[4].toInt() and 0xFF
            assertFalse(id in forbidden, "$m sends $id")
            if (id == Wire.EXTENDED && b[5].toInt() != 0) {
                val d = Bencode.decode(b.copyOfRange(6, b.size)) as BDict
                assertTrue(d.long("msg_type") in setOf(0L, 2L), "ut_metadata data 를 보내지 않는다")
            }
        }
    }

    @Test
    fun extensionHandshake() {
        val ours = Bencode.decode(ExtensionHandshake.ours(wantMetadata = true)) as BDict
        assertEquals(ExtensionHandshake.OUR_UT_METADATA_ID.toLong(), ours.dict("m")!!.long("ut_metadata"))
        for (k in listOf("metadata_size", "v", "p", "yourip", "ipv4", "ipv6", "reqq")) assertNull(ours[k], "$k 를 싣지 않는다")
        val plain = Bencode.decode(ExtensionHandshake.ours(wantMetadata = false)) as BDict
        assertTrue(plain.dict("m")!!.entries.isEmpty())

        val theirs = ExtensionHandshake.parse(Bencode.encode(mapOf("m" to mapOf("ut_metadata" to 7L), "metadata_size" to 1234L, "reqq" to 250L, "v" to "X")))
        assertNotNull(theirs)
        assertEquals(7, theirs.utMetadataId)
        assertEquals(1234, theirs.metadataSize)
        // 상한을 넘는 metadata_size 는 없는 것으로
        val huge = ExtensionHandshake.parse(Bencode.encode(mapOf("m" to mapOf("ut_metadata" to 7L), "metadata_size" to 20_000_000L)))!!
        assertNull(huge.metadataSize)
        assertNull(ExtensionHandshake.parse("not bencode".toByteArray()))
    }

    @Test
    fun metadataMessage() {
        val payload = Bencode.encode(mapOf("msg_type" to 1L, "piece" to 0L, "total_size" to 3L)) + byteArrayOf(1, 2, 3)
        val m = MetadataMessage.parse(payload)!!
        assertEquals(MetadataMessage.DATA, m.type)
        assertContentEquals(byteArrayOf(1, 2, 3), m.data)
        assertNull(MetadataMessage.parse(Bencode.encode(mapOf("piece" to 0L))))
        assertNull(MetadataMessage.parse(Bencode.encode(mapOf("msg_type" to 0L, "piece" to 99_999L))))
    }
}
