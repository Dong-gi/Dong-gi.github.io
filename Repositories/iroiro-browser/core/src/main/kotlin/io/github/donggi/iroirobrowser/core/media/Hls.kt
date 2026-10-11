package io.github.donggi.iroirobrowser.core.media

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * HLS 재생목록(RFC 8216) — 받기에 쓰는 만큼만 읽는다.
 *
 * - **마스터**: 화질(`#EXT-X-STREAM-INF` — 대역폭·해상도·코덱·소리 묶음)과 따로 된 소리(`#EXT-X-MEDIA:TYPE=AUDIO`).
 * - **미디어**: 조각(`#EXTINF` 와 주소 — `#EXT-X-BYTERANGE` 면 한 파일의 일부), 잠금(`#EXT-X-KEY`), 초기 조각(`#EXT-X-MAP` — fMP4),
 *   첫 조각의 번호(`#EXT-X-MEDIA-SEQUENCE` — 잠금의 기본 IV), 끝(`#EXT-X-ENDLIST` — 없으면 실시간 방송).
 *
 * 상대 주소는 재생목록의 주소에 대어 푼다.
 */
sealed class HlsPlaylist {

    class Master(
        val variants: List<Variant>,
        val audio: List<Rendition>,
        /** 자막 재생목록(`#EXT-X-MEDIA:TYPE=SUBTITLES`). 받지는 않는다 — 찾은 미디어에서 마스터 아래로 숨긴다. */
        val subtitles: List<Rendition> = emptyList(),
        /** 그 밖에 이 마스터가 가리키는 재생목록 — 다른 각도의 영상(`TYPE=VIDEO`)·빨리 감기용(`#EXT-X-I-FRAME-STREAM-INF`). */
        val related: List<String> = emptyList(),
    ) : HlsPlaylist() {
        /**
         * 이 마스터 아래의 재생목록 주소 모두 — 재생기는 마스터를 읽은 뒤 이것들을 따로 받으므로 찾은 미디어에 줄이 따로 뜬다. 그 줄을 고르면
         * 소리 없는 영상·소리만·자막 조각이 받아졌다 — 찾은 미디어가 이 주소들을 마스터 아래로 숨긴다.
         */
        val childUris: Set<String>
            get() = buildSet {
                variants.forEach { add(it.uri) }
                (audio + subtitles).forEach { r -> r.uri?.let { add(it) } }
                addAll(related)
            }
    }

    class Media(
        val segments: List<Segment>,
        /** `#EXT-X-ENDLIST` 가 있다 — 다시 받아도 늘지 않는다(주문형). 없으면 실시간 방송. */
        val ended: Boolean,
        val targetDuration: Double,
    ) : HlsPlaylist() {
        val totalDuration: Double get() = segments.sumOf { it.duration }

        /** 조각이 fMP4 다(초기 조각이 있다). */
        val fragmentedMp4: Boolean get() = segments.any { it.map != null }

        /** 잠긴 조각의 방식들(`NONE` 은 빼고). */
        val encryption: Set<String> get() = segments.mapNotNull { it.key?.method?.takeIf { m -> m != "NONE" } }.toSet()
    }

    class Variant(
        val uri: String,
        val bandwidth: Long,
        val resolution: String?,
        val codecs: String?,
        val audioGroup: String?,
        /** `AVERAGE-BANDWIDTH` — 크기를 어림할 때 [bandwidth](가장 높은 순간)보다 가깝다. 없으면 0. */
        val averageBandwidth: Long = 0,
    ) {
        /** 고르는 화면에 보일 이름 — `1280x720 · 2.5 Mbps`. */
        val label: String
            get() = listOfNotNull(resolution, if (bandwidth > 0) String.format(Locale.ROOT, "%.1f Mbps", bandwidth / 1_000_000.0) else null)
                .joinToString(" · ").ifEmpty { uri.substringAfterLast('/') }
    }

    class Rendition(val groupId: String, val name: String, val language: String?, val uri: String?, val isDefault: Boolean)

    class Segment(
        val uri: String,
        val duration: Double,
        /** `#EXT-X-BYTERANGE` — 그 파일의 [offset] 부터 [length] 바이트. 없으면 파일 전체. */
        val byteRange: ByteRange?,
        val key: Key?,
        /** 이 조각의 번호(`#EXT-X-MEDIA-SEQUENCE` + 차례) — 잠금에 IV 가 없을 때 IV 다. */
        val sequence: Long,
        val map: InitSection?,
        val discontinuity: Boolean,
    )

    class ByteRange(val length: Long, val offset: Long)

    class Key(val method: String, val uri: String?, val iv: ByteArray?, val keyFormat: String?) {
        /** 표준 AES-128(전체 조각 CBC, 키 형식 identity). */
        val isAes128: Boolean get() = method == "AES-128" && (keyFormat == null || keyFormat == "identity")
    }

    /**
     * 초기 조각(`#EXT-X-MAP`). [key] 는 **그 태그가 나온 자리에서 걸려 있던 잠금**이다 — RFC 8216 4.3.2.4: `EXT-X-KEY` 는 그 뒤(다음 키까지)에
     * 선언된 조각과 초기 조각에 걸린다. 그래서 MAP 이 KEY 보다 앞이면(ffmpeg 의 fMP4) 초기 조각은 잠기지 않았다.
     */
    class InitSection(val uri: String, val byteRange: ByteRange?, val key: Key? = null)

    class Invalid(message: String) : Exception(message)

    companion object {

        /** [text] 를 읽는다. [baseUrl] 은 재생목록의 주소(상대 주소를 푼다). 재생목록이 아니면 [Invalid]. */
        fun parse(text: String, baseUrl: String): HlsPlaylist {
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull()?.startsWith("#EXTM3U") != true) throw Invalid("#EXTM3U 가 없다")
            return if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) master(lines, baseUrl) else media(lines, baseUrl)
        }

        private fun master(lines: List<String>, base: String): Master {
            val variants = ArrayList<Variant>()
            val audio = ArrayList<Rendition>()
            val subtitles = ArrayList<Rendition>()
            val related = ArrayList<String>()
            var pending: Map<String, String>? = null
            for (line in lines) {
                when {
                    line.startsWith("#EXT-X-STREAM-INF:") -> pending = attributes(line.substringAfter(':'))
                    line.startsWith("#EXT-X-I-FRAME-STREAM-INF:") -> attributes(line.substringAfter(':'))["URI"]?.let { related += resolve(base, it) }
                    line.startsWith("#EXT-X-MEDIA:") -> {
                        val a = attributes(line.substringAfter(':'))
                        val rendition = Rendition(a["GROUP-ID"].orEmpty(), a["NAME"].orEmpty(), a["LANGUAGE"], a["URI"]?.let { resolve(base, it) },
                            a["DEFAULT"] == "YES")
                        when (a["TYPE"]) {
                            "AUDIO" -> audio += rendition
                            "SUBTITLES" -> subtitles += rendition
                            "VIDEO" -> rendition.uri?.let { related += it }
                        }
                    }
                    line.startsWith("#") -> Unit
                    pending != null -> {
                        val a = pending
                        variants += Variant(resolve(base, line), a["BANDWIDTH"]?.toLongOrNull() ?: 0, a["RESOLUTION"], a["CODECS"], a["AUDIO"],
                            a["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0)
                        pending = null
                    }
                }
            }
            if (variants.isEmpty()) throw Invalid("화질이 없다")
            return Master(variants.sortedByDescending { it.bandwidth }, audio, subtitles, related)
        }

        private fun media(lines: List<String>, base: String): Media {
            val segments = ArrayList<Segment>()
            var sequence = 0L
            var targetDuration = 0.0
            var ended = false
            var key: Key? = null
            var map: InitSection? = null
            var duration: Double? = null
            var range: ByteRange? = null
            var discontinuity = false
            var nextOffset = 0L
            for (line in lines) {
                when {
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0
                    line.startsWith("#EXT-X-TARGETDURATION:") -> targetDuration = line.substringAfter(':').trim().toDoubleOrNull() ?: 0.0
                    line.startsWith("#EXT-X-ENDLIST") -> ended = true
                    line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") -> discontinuity = true
                    line.startsWith("#EXTINF:") -> duration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                    line.startsWith("#EXT-X-BYTERANGE:") -> range = byteRange(line.substringAfter(':').trim(), nextOffset)
                    line.startsWith("#EXT-X-KEY:") -> {
                        val a = attributes(line.substringAfter(':'))
                        val method = a["METHOD"] ?: "NONE"
                        key = if (method == "NONE") null else Key(method, a["URI"]?.let { resolve(base, it) }, a["IV"]?.let(::hex), a["KEYFORMAT"])
                    }
                    line.startsWith("#EXT-X-MAP:") -> {
                        val a = attributes(line.substringAfter(':'))
                        map = a["URI"]?.let { InitSection(resolve(base, it), a["BYTERANGE"]?.let { r -> byteRange(r, 0) }, key) }
                    }
                    line.startsWith("#") -> Unit
                    else -> {
                        // `#EXT-X-BYTERANGE` 에 시작이 없으면 앞 조각(같은 파일 — RFC 8216 이 그렇게 정한다)의 끝에서 잇는다(위의 byteRange).
                        segments += Segment(resolve(base, line), duration ?: 0.0, range, key, sequence + segments.size, map, discontinuity)
                        nextOffset = range?.let { it.offset + it.length } ?: 0L
                        duration = null
                        range = null
                        discontinuity = false
                    }
                }
            }
            return Media(segments, ended, targetDuration)
        }

        /** `length[@offset]` — offset 이 없으면 [continueFrom](앞 조각의 끝). */
        private fun byteRange(text: String, continueFrom: Long): ByteRange? {
            val length = text.substringBefore('@').trim().toLongOrNull() ?: return null
            val offset = if ('@' in text) text.substringAfter('@').trim().toLongOrNull() ?: return null else continueFrom
            return ByteRange(length, offset)
        }

        /** 속성 목록 — `KEY=VALUE,KEY="VALUE, WITH COMMA",…`. */
        internal fun attributes(text: String): Map<String, String> {
            val out = HashMap<String, String>()
            var i = 0
            while (i < text.length) {
                val eq = text.indexOf('=', i)
                if (eq < 0) break
                val name = text.substring(i, eq).trim().trimStart(',').trim()
                var j = eq + 1
                val value: String
                if (j < text.length && text[j] == '"') {
                    val close = text.indexOf('"', j + 1)
                    val end = if (close < 0) text.length else close
                    value = text.substring(j + 1, end)
                    j = if (close < 0) text.length else close + 1
                } else {
                    val comma = text.indexOf(',', j)
                    val end = if (comma < 0) text.length else comma
                    value = text.substring(j, end).trim()
                    j = end
                }
                if (name.isNotEmpty()) out[name.uppercase(Locale.ROOT)] = value
                i = if (j < text.length && text[j] == ',') j + 1 else j
            }
            return out
        }

        private fun hex(text: String): ByteArray? {
            val h = text.removePrefix("0x").removePrefix("0X")
            if (h.length % 2 != 0 || h.any { it.digitToIntOrNull(16) == null }) return null
            return ByteArray(h.length / 2) { k -> h.substring(k * 2, k * 2 + 2).toInt(16).toByte() }
        }

        internal fun resolve(base: String, ref: String): String = try {
            URI(base).resolve(URI(ref.replace(" ", "%20"))).toString()
        } catch (e: URISyntaxException) {
            ref
        } catch (e: IllegalArgumentException) {
            ref
        }
    }
}
