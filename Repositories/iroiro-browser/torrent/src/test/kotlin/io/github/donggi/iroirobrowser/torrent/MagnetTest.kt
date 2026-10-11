package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.meta.HostPort
import io.github.donggi.iroirobrowser.torrent.meta.MagnetLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.fail

class MagnetTest {

    @Test
    fun pythonVectors() {
        for (c in PythonVectors.magnets) {
            when (c.expect) {
                "ok" -> {
                    val m = try {
                        MagnetLink.parse(c.uri)
                    } catch (e: TorrentException) {
                        fail("${c.uri}: $e")
                    }
                    assertEquals(c.infoHashHex, m.infoHashHex, c.uri)
                    assertEquals(c.name, m.displayName, c.uri)
                    assertEquals(c.trackers, m.trackers, c.uri)
                    assertEquals(c.webSeeds, m.webSeeds, c.uri)
                    assertEquals(c.peers, m.peers.map { it.toString() }, c.uri)
                }
                "v2-only" -> assertEquals(TorrentError.V2_ONLY, assertFailsWith<TorrentException>(c.uri) { MagnetLink.parse(c.uri) }.error)
                else -> assertEquals(TorrentError.INVALID_MAGNET, assertFailsWith<TorrentException>(c.uri) { MagnetLink.parse(c.uri) }.error)
            }
        }
    }

    @Test
    fun ignoresExactSourceAndOddKeys() {
        val m = MagnetLink.parse("magnet:?xt=urn:btih:${"a".repeat(40)}&xs=http%3A%2F%2F10.0.0.1%2Fx.torrent&as=http://x&tr=ftp://no&ws=file:///etc")
        assertEquals(emptyList(), m.trackers)
        assertEquals(emptyList(), m.webSeeds)
    }

    @Test
    fun rejects() {
        listOf("http://x", "magnet:", "magnet:?xt=urn:btih:zz", "magnet:?xt=urn:btih:${"a".repeat(40)}&xt=urn:btih:${"b".repeat(40)}", "magnet:?xt=urn:btih:%zz")
            .forEach { assertFailsWith<TorrentException>(it) { MagnetLink.parse(it) } }
        assertFailsWith<TorrentException> { MagnetLink.parse("magnet:?xt=urn:btih:${"a".repeat(40)}&dn=" + "x".repeat(70_000)) }
    }

    @Test
    fun hostPort() {
        assertEquals("1.2.3.4:6881", HostPort.parse("1.2.3.4:6881").toString())
        assertEquals("[::1]:1", HostPort.parse("[::1]:1").toString())
        assertEquals("peer.example:51413", HostPort.parse("Peer.Example:51413").toString())
        for (bad in listOf("1.2.3.4", "1.2.3.4:0", "1.2.3.4:70000", "a b:1", "::1:5", "[::1]5", "u@h:1")) assertNull(HostPort.parse(bad), bad)
    }
}
