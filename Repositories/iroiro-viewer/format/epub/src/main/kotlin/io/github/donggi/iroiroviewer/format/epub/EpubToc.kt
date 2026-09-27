package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.SafeXml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * 목차를 읽는다. EPUB2 의 `toc.ncx` 와 EPUB3 의 `nav.xhtml` 둘 다 XML 이라 같은 파서로 된다.
 *
 * ## 계층을 버리고 평평하게 만든다
 *
 * 둘 다 `navPoint`/`<ol>` 을 중첩해 장·절을 표현하는데, 우리는 **깊이만 세어** 한 줄
 * 목록으로 낸다. 트리를 들면 화면에 펼침 상태가 생기고, 그 상태는 쪽을 옮길 때마다
 * 맞춰 줘야 하는 또 하나의 상태다 — 9단계가 '없어도 되는 상태를 만들면 그 상태가
 * 틀리는 날이 온다' 고 적어 둔 그것이다. 깊이는 들여쓰기로만 쓴다.
 */
internal object EpubToc {

    /** 목차 한 줄. [href] 는 ZIP 엔트리 이름이고 조각(`#…`)은 떨어져 있다. */
    data class Entry(val title: String, val href: String, val depth: Int)

    private const val OPS_NS = "http://www.idpf.org/2007/ops"

    /**
     * EPUB2 의 `toc.ncx`.
     *
     * @param base `toc.ncx` 가 든 폴더. 그 안의 주소가 이것 기준이다.
     */
    fun fromNcx(
        input: InputStream,
        base: String,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ): List<Entry> {
        val parser = SafeXml.newParser(input, null, limits)
        val out = ArrayList<Entry>()
        var depth = 0
        var label: String? = null
        var inLabel = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "navPoint" -> { depth++; label = null }
                    "text" -> inLabel = true
                    "content" -> {
                        val src = parser.getAttributeValue(null, "src")
                        val href = if (src.isNullOrBlank()) null else EpubHref.resolve(base, src)
                        if (href != null) {
                            out.add(Entry(label?.trim().orEmpty(), href, depth - 1))
                            // 한 navPoint 가 content 를 둘 가질 수는 없지만, 있더라도
                            // 첫 줄만 남기려고 이름을 비운다.
                            label = null
                        }
                    }
                }

                XmlPullParser.TEXT -> if (inLabel && label == null) {
                    label = SafeXml.text(parser, limits)
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "navPoint" -> if (depth > 0) depth--
                    "text" -> inLabel = false
                }
            }
            event = parser.next()
        }
        return out
    }

    /**
     * EPUB3 의 `nav.xhtml`.
     *
     * **`epub:type="toc"` 인 `nav` 안만 읽는다.** 같은 파일에 `landmarks` 와 `page-list`
     * 가 함께 들어 있고, 그것까지 목차에 넣으면 '표지·판권·1쪽·2쪽…' 이 줄줄이 뜬다.
     */
    fun fromNav(
        input: InputStream,
        base: String,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ): List<Entry> {
        val parser = SafeXml.newParser(input, null, limits)
        val out = ArrayList<Entry>()
        var inToc = false
        var navDepth = 0
        var listDepth = 0
        var href: String? = null
        var text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "nav" -> {
                        navDepth++
                        val type = parser.getAttributeValue(OPS_NS, "type")
                            ?: parser.getAttributeValue(null, "type")
                        if (!inToc && type?.split(' ')?.contains("toc") == true) {
                            inToc = true
                            listDepth = 0
                        }
                    }

                    "ol" -> if (inToc) listDepth++

                    "a" -> if (inToc) {
                        val raw = parser.getAttributeValue(null, "href")
                        href = if (raw.isNullOrBlank()) null else EpubHref.resolve(base, raw)
                        text = StringBuilder()
                    }
                }

                XmlPullParser.TEXT -> if (inToc && href != null) {
                    text.append(SafeXml.text(parser, limits))
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "a" -> if (inToc && href != null) {
                        out.add(Entry(text.toString().trim(), href!!, (listDepth - 1).coerceAtLeast(0)))
                        href = null
                    }

                    "ol" -> if (inToc && listDepth > 0) listDepth--

                    "nav" -> {
                        if (inToc && navDepth > 0) inToc = false
                        if (navDepth > 0) navDepth--
                    }
                }
            }
            event = parser.next()
        }
        return out
    }
}
