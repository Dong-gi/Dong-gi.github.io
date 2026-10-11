package io.github.donggi.iroirobrowser.torrent.meta

import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentException
import io.github.donggi.iroirobrowser.torrent.util.Base32
import io.github.donggi.iroirobrowser.torrent.util.Hex
import java.io.ByteArrayOutputStream

/** `host:port` — 마그넷의 `x.pe` 처럼 남이 준 주소. 이름이면 나중에 풀어 얻은 주소를 거른다. */
class HostPort(val host: String, val port: Int) {
    override fun toString(): String = if (':' in host) "[$host]:$port" else "$host:$port"

    companion object {
        /** `1.2.3.4:6881`, `[2001:db8::1]:6881`, `name:6881`. 못 읽으면 null. */
        fun parse(s: String): HostPort? {
            val text = s.trim()
            if (text.isEmpty() || text.length > 300) return null
            val host: String
            val portText: String
            if (text.startsWith("[")) {
                val close = text.indexOf(']')
                if (close < 0 || close + 1 >= text.length || text[close + 1] != ':') return null
                host = text.substring(1, close)
                portText = text.substring(close + 2)
            } else {
                val colon = text.lastIndexOf(':')
                if (colon <= 0 || text.indexOf(':') != colon) return null
                host = text.substring(0, colon)
                portText = text.substring(colon + 1)
            }
            if (host.isEmpty() || portText.isEmpty() || portText.length > 5 || !portText.all(Char::isDigit)) return null
            val port = portText.toInt()
            if (port !in 1..65535) return null
            if (host.any { it.isWhitespace() || it == '/' || it == '@' || it == '%' }) return null
            return HostPort(host.lowercase(), port)
        }
    }
}

/**
 * 마그넷 주소(BEP 9). 받는 것: `xt=urn:btih:`(16진 40자·base32 32자), `dn`, `tr`(트래커 — 하나씩 층 하나), `ws`(웹 시드 — 메타데이터를 받은 뒤에
 * 쓴다), `x.pe`(피어 주소). `xt=urn:btmh:`(v2)만 있으면 [TorrentError.V2_ONLY] — btih 와 함께 있으면(하이브리드) btih 를 쓴다.
 *
 * **`xs`(그 토렌트 파일의 주소)·`as`·`kt`·`so` 는 읽지 않는다** — `xs` 를 따르면 남이 고른 주소로 받기를 하게 된다(그 받기는 프록시를 거치는
 * 웹 쪽 길도, 토렌트 쪽 길도 아니어야 할 이유가 없다 — 쓸 곳이 없다). 메타데이터는 피어에게서(BEP 9) 받는다.
 */
class MagnetLink(
    val infoHash: ByteArray,
    val displayName: String?,
    val trackers: List<String>,
    val webSeeds: List<String>,
    val peers: List<HostPort>,
    /** 원래 주소 — 이어받기 자료에 둔다(메타데이터를 받기 전에 앱이 죽어도 되살린다). */
    val uri: String,
) {
    val infoHashHex: String get() = Hex.encode(infoHash)

    companion object {
        const val MAX_LENGTH = 64 * 1024
        const val MAX_TRACKERS = 50
        const val MAX_WEB_SEEDS = 20
        const val MAX_PEERS = 50
        private val XT_N = Regex("xt\\.\\d{1,3}")
        private val TR_N = Regex("tr\\.\\d{1,3}")

        fun parse(uri: String): MagnetLink {
            val text = uri.trim()
            if (text.length > MAX_LENGTH) bad("too long")
            if (!text.regionMatches(0, "magnet:?", 0, 8, ignoreCase = true)) bad("not a magnet")
            val query = text.substring(8).substringBefore('#')
            var btih: ByteArray? = null
            var hasBtmh = false
            var name: String? = null
            val trackers = LinkedHashSet<String>()
            val webSeeds = LinkedHashSet<String>()
            val peers = ArrayList<HostPort>()
            for (pair in query.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val key = pair.substring(0, eq).lowercase()
                val value = percentDecode(pair.substring(eq + 1)) ?: continue
                when {
                    key == "xt" || XT_N.matches(key) -> when {
                        value.regionMatches(0, "urn:btih:", 0, 9, ignoreCase = true) -> {
                            val h = infoHashOf(value.substring(9)) ?: bad("bad btih")
                            if (btih != null && !btih.contentEquals(h)) bad("two different btih")
                            btih = h
                        }
                        value.regionMatches(0, "urn:btmh:", 0, 9, ignoreCase = true) -> hasBtmh = true
                    }
                    key == "dn" -> if (name == null) name = value.take(1024)
                    key == "tr" || TR_N.matches(key) ->
                        if (trackers.size < MAX_TRACKERS && Metainfo.isTrackerUrl(value)) trackers.add(value)
                    key == "ws" -> if (webSeeds.size < MAX_WEB_SEEDS && Metainfo.isWebSeedUrl(value)) webSeeds.add(value)
                    key == "x.pe" -> if (peers.size < MAX_PEERS) HostPort.parse(value)?.let(peers::add)
                }
            }
            val hash = btih ?: if (hasBtmh) throw TorrentException(TorrentError.V2_ONLY) else bad("no btih")
            return MagnetLink(hash, name, trackers.toList(), webSeeds.toList(), peers, text)
        }

        private fun infoHashOf(s: String): ByteArray? = when (s.length) {
            40 -> Hex.decode(s)
            32 -> Base32.decode(s)?.takeIf { it.size == 20 }
            else -> null
        }

        /** `%XX` 와 `+`(빈칸)를 푼다. UTF-8 로 읽는다. 깨진 `%` 면 null. */
        internal fun percentDecode(s: String): String? {
            val out = ByteArrayOutputStream(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when (c) {
                    '%' -> {
                        if (i + 2 >= s.length) return null
                        val hi = Character.digit(s[i + 1], 16)
                        val lo = Character.digit(s[i + 2], 16)
                        if (hi < 0 || lo < 0) return null
                        out.write((hi shl 4) or lo)
                        i += 3
                    }
                    '+' -> {
                        out.write(' '.code)
                        i++
                    }
                    else -> {
                        val cp = s.codePointAt(i)
                        val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
                        out.write(bytes, 0, bytes.size)
                        i += Character.charCount(cp)
                    }
                }
            }
            return out.toByteArray().toString(Charsets.UTF_8)
        }

        private fun bad(detail: String): Nothing = throw TorrentException(TorrentError.INVALID_MAGNET, detail)
    }
}
