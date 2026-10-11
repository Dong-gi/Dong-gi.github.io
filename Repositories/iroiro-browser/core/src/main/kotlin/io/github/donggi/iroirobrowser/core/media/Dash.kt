package io.github.donggi.iroirobrowser.core.media

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/**
 * DASH 목록(MPD — ISO/IEC 23009-1) — 받기에 쓰는 만큼만 읽는다. **첫 기간(Period) 하나만** 본다.
 *
 * - 화질(`Representation` — 대역폭·크기·코덱·형식)과 그 조각 주소. `SegmentTemplate`(`$Number$`·`$Time$`·`$RepresentationID$`·
 *   `$Bandwidth$`, `%0Nd` 꼴, `SegmentTimeline` 의 `S@t·d·r`), `SegmentList`(`SegmentURL`·`Initialization`), `SegmentBase`·`BaseURL` 만
 *   (파일 하나 통째로). 위(`AdaptationSet`·`Period`)에 적힌 것을 물려받는다. `BaseURL` 은 목록 주소부터 차례로 대어 푼다.
 * - `type="dynamic"` 은 실시간 방송, `ContentProtection` 이 붙은 것은 DRM — 둘 다 표시만 하고(받지 않는 것은 부르는 쪽이 정한다).
 *
 * **DOCTYPE 이 든 문서는 읽지 않는다**(외부 엔티티·엔티티 폭탄 — MPD 에는 DOCTYPE 이 없다).
 */
class DashManifest(
    val dynamic: Boolean,
    /** 길이(초). 모르면 0. */
    val duration: Double,
    val videos: List<Representation>,
    val audios: List<Representation>,
) {
    class Representation(
        val id: String,
        val bandwidth: Long,
        val width: Int?,
        val height: Int?,
        val codecs: String?,
        val mimeType: String?,
        val lang: String?,
        /** DRM(`ContentProtection`) 이 붙었다. */
        val protected: Boolean,
        val init: Part?,
        val segments: List<Part>,
    ) {
        val label: String
            get() = listOfNotNull(
                if (width != null && height != null) "${width}x$height" else lang,
                if (bandwidth > 0) String.format(Locale.ROOT, if (bandwidth >= 1_000_000) "%.1f Mbps" else "%.0f kbps", if (bandwidth >= 1_000_000) bandwidth / 1_000_000.0 else bandwidth / 1000.0) else null,
            ).joinToString(" · ").ifEmpty { id }
    }

    /** 조각 하나 — 주소와(있으면) 그 파일의 바이트 범위. */
    class Part(val url: String, val offset: Long?, val length: Long?)

    class Invalid(message: String) : Exception(message)

    companion object {

        fun parse(text: String, manifestUrl: String): DashManifest {
            if (text.contains("<!DOCTYPE", ignoreCase = true)) throw Invalid("DOCTYPE 이 든 목록은 읽지 않는다")
            val doc = try {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    isExpandEntityReferences = false
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                }
                factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            } catch (e: Exception) {
                throw Invalid("MPD 를 읽지 못했다: ${e.javaClass.simpleName}")
            }
            val mpd = doc.documentElement
            if (mpd == null || local(mpd) != "MPD") throw Invalid("MPD 가 아니다")
            val dynamic = mpd.getAttribute("type") == "dynamic"
            var base = baseUrl(mpd, manifestUrl)
            val period = children(mpd, "Period").firstOrNull() ?: throw Invalid("Period 가 없다")
            base = baseUrl(period, base)
            val duration = isoDuration(period.getAttribute("duration")) ?: isoDuration(mpd.getAttribute("mediaPresentationDuration")) ?: 0.0
            val videos = ArrayList<Representation>()
            val audios = ArrayList<Representation>()
            for (set in children(period, "AdaptationSet")) {
                val setBase = baseUrl(set, base)
                val setProtected = children(set, "ContentProtection").isNotEmpty()
                val setMime = set.getAttribute("mimeType").ifEmpty { null }
                val contentType = set.getAttribute("contentType").ifEmpty { null }
                for (rep in children(set, "Representation")) {
                    val mime = rep.getAttribute("mimeType").ifEmpty { setMime }
                    val kind = (contentType ?: mime?.substringBefore('/'))?.lowercase(Locale.ROOT)
                    if (kind != "video" && kind != "audio") continue
                    val repBase = baseUrl(rep, setBase)
                    val (init, segments) = addressing(rep, set, period, repBase, duration)
                    val r = Representation(
                        id = rep.getAttribute("id"),
                        bandwidth = rep.getAttribute("bandwidth").toLongOrNull() ?: 0,
                        width = (rep.getAttribute("width").ifEmpty { set.getAttribute("width") }).toIntOrNull(),
                        height = (rep.getAttribute("height").ifEmpty { set.getAttribute("height") }).toIntOrNull(),
                        codecs = rep.getAttribute("codecs").ifEmpty { set.getAttribute("codecs") }.ifEmpty { null },
                        mimeType = mime,
                        lang = set.getAttribute("lang").ifEmpty { null },
                        protected = setProtected || children(rep, "ContentProtection").isNotEmpty(),
                        init = init,
                        segments = segments,
                    )
                    if (kind == "video") videos += r else audios += r
                }
            }
            if (videos.isEmpty() && audios.isEmpty()) throw Invalid("영상·소리가 없다")
            return DashManifest(dynamic, duration, videos.sortedByDescending { it.bandwidth }, audios.sortedByDescending { it.bandwidth })
        }

        /** 조각 주소 — 화질에 없으면 위(AdaptationSet·Period)의 것을 물려받는다. */
        private fun addressing(rep: Element, set: Element, period: Element, base: String, duration: Double): Pair<Part?, List<Part>> {
            val template = listOf(rep, set, period).firstNotNullOfOrNull { children(it, "SegmentTemplate").firstOrNull() }
            if (template != null) return fromTemplate(template, rep, base, duration)
            val list = listOf(rep, set, period).firstNotNullOfOrNull { children(it, "SegmentList").firstOrNull() }
            if (list != null) {
                val init = children(list, "Initialization").firstOrNull()?.let { i ->
                    part(resolve(base, i.getAttribute("sourceURL").ifEmpty { "" }.ifEmpty { base }), i.getAttribute("range"))
                }
                val segs = children(list, "SegmentURL").map { s ->
                    part(if (s.getAttribute("media").isEmpty()) base else resolve(base, s.getAttribute("media")), s.getAttribute("mediaRange"))
                }
                return init to segs
            }
            // SegmentBase 이거나 BaseURL 만 — 파일 하나(fMP4 통째로).
            return null to listOf(Part(base, null, null))
        }

        private fun fromTemplate(t: Element, rep: Element, base: String, duration: Double): Pair<Part?, List<Part>> {
            val id = rep.getAttribute("id")
            val bandwidth = rep.getAttribute("bandwidth")
            val timescale = t.getAttribute("timescale").toLongOrNull()?.takeIf { it > 0 } ?: 1L
            val startNumber = t.getAttribute("startNumber").toLongOrNull() ?: 1L
            val media = t.getAttribute("media")
            if (media.isEmpty()) throw Invalid("SegmentTemplate 에 media 가 없다")
            val init = t.getAttribute("initialization").ifEmpty { null }?.let { Part(resolve(base, fill(it, id, bandwidth, null, null)), null, null) }
            val timeline = children(t, "SegmentTimeline").firstOrNull()
            val segments = ArrayList<Part>()
            if (timeline != null) {
                var time = 0L
                var number = startNumber
                val end = (duration * timescale).toLong()
                for (s in children(timeline, "S")) {
                    s.getAttribute("t").toLongOrNull()?.let { time = it }
                    val d = s.getAttribute("d").toLongOrNull() ?: throw Invalid("S 에 d 가 없다")
                    var r = s.getAttribute("r").toLongOrNull() ?: 0L
                    // r = -1: 기간이 끝날 때까지(다음 S 의 시작이나 길이로).
                    if (r < 0) r = if (end > time && d > 0) (end - time + d - 1) / d - 1 else 0
                    repeat((r + 1).coerceAtMost(MAX_SEGMENTS.toLong()).toInt()) {
                        segments += Part(resolve(base, fill(media, id, bandwidth, number, time)), null, null)
                        time += d
                        number++
                    }
                    if (segments.size >= MAX_SEGMENTS) break
                }
            } else {
                val segDuration = t.getAttribute("duration").toLongOrNull()?.takeIf { it > 0 } ?: throw Invalid("SegmentTemplate 의 길이를 모른다")
                val count = if (duration > 0) ceil(duration * timescale / segDuration).toLong() else 0L
                if (count <= 0) throw Invalid("조각 수를 모른다(길이가 없다)")
                for (k in 0 until count.coerceAtMost(MAX_SEGMENTS.toLong())) {
                    segments += Part(resolve(base, fill(media, id, bandwidth, startNumber + k, k * segDuration)), null, null)
                }
            }
            return init to segments
        }

        /** `$RepresentationID$`·`$Number$`·`$Time$`·`$Bandwidth$`(`%05d` 꼴 포함)·`$$`. */
        internal fun fill(template: String, id: String, bandwidth: String, number: Long?, time: Long?): String {
            val out = StringBuilder()
            var i = 0
            while (i < template.length) {
                val c = template[i]
                if (c != '$') {
                    out.append(c)
                    i++
                    continue
                }
                val close = template.indexOf('$', i + 1)
                if (close < 0) {
                    out.append(template.substring(i))
                    break
                }
                val token = template.substring(i + 1, close)
                if (token.isEmpty()) {
                    out.append('$')
                } else {
                    val name = token.substringBefore('%')
                    val format = if ('%' in token) "%" + token.substringAfter('%') else null
                    val value: Any? = when (name) {
                        "RepresentationID" -> id
                        "Number" -> number
                        "Time" -> time
                        "Bandwidth" -> bandwidth.toLongOrNull()
                        else -> null
                    }
                    out.append(
                        when {
                            value == null -> "$$token$"
                            format != null && value is Long -> try {
                                String.format(Locale.ROOT, format, value)
                            } catch (e: java.util.IllegalFormatException) {
                                value.toString()
                            }
                            else -> value.toString()
                        },
                    )
                }
                i = close + 1
            }
            return out.toString()
        }

        /** `range="a-b"` 를 [Part] 로. */
        private fun part(url: String, range: String): Part {
            if (range.isEmpty()) return Part(url, null, null)
            val a = range.substringBefore('-').trim().toLongOrNull()
            val b = range.substringAfter('-', "").trim().toLongOrNull()
            return if (a != null && b != null && b >= a) Part(url, a, b - a + 1) else Part(url, null, null)
        }

        /** ISO 8601 기간(`PT1H2M3.5S`, `P1DT2H`)을 초로. */
        internal fun isoDuration(text: String?): Double? {
            if (text.isNullOrBlank()) return null
            val m = Regex("""^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$""").matchEntire(text.trim())
                ?: return null
            val (d, h, min, s) = m.destructured
            return (d.toDoubleOrNull() ?: 0.0) * 86400 + (h.toDoubleOrNull() ?: 0.0) * 3600 + (min.toDoubleOrNull() ?: 0.0) * 60 + (s.toDoubleOrNull() ?: 0.0)
        }

        private fun baseUrl(e: Element, parent: String): String {
            val b = children(e, "BaseURL").firstOrNull()?.textContent?.trim().orEmpty()
            return if (b.isEmpty()) parent else resolve(parent, b)
        }

        private fun resolve(base: String, ref: String): String = HlsPlaylist.resolve(base, ref)

        private fun local(n: Node): String = (n.localName ?: n.nodeName).substringAfter(':')

        private fun children(e: Element, name: String): List<Element> {
            val out = ArrayList<Element>()
            val nodes = e.childNodes
            for (k in 0 until nodes.length) {
                val n = nodes.item(k)
                if (n is Element && local(n) == name) out += n
            }
            return out
        }

        private const val MAX_SEGMENTS = 50_000
    }
}
