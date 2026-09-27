package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/**
 * OOXML 을 읽을 때 되풀이되는 것들.
 *
 * ## 이름공간을 거의 보지 않는다
 *
 * OOXML 에는 **두 벌의 이름공간**이 있다 — 과도기(Transitional, `schemas.openxmlformats.org`)와
 * 엄격(Strict, `purl.oclc.org/ooxml`). 같은 요소가 둘 중 어느 이름공간으로든 온다. 요소 이름
 * (`w:p`·`a:blip`)은 **지역 이름만** 보고 가려도 겹치는 일이 드물어서, 이 모듈의 변환기들은
 * 지역 이름으로 가른다. 겹치는 자리(관계 id 인 `r:id` 와 다른 `id`)만 이름공간을 본다([rel]).
 */
object OoxmlXml {

    /** 관계(`r:`) 이름공간 — 과도기·엄격. */
    val RELATIONSHIP_NS = setOf(
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
        "http://purl.oclc.org/ooxml/officeDocument/relationships",
    )

    /**
     * 지역 이름이 [name] 인 속성의 값. **이름공간을 보지 않는다**(`w:val` 과 `val` 을 같게 본다).
     * 관계 id(`r:id`·`r:embed`)는 [rel] 로 찾는다 — 같은 지역 이름의 다른 속성과 겹친다.
     */
    fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) {
            if (p.getAttributeName(i) == name) return p.getAttributeValue(i)
        }
        return null
    }

    /** 관계 이름공간의 속성(`r:id`·`r:embed`·`r:link`). */
    fun rel(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) {
            if (p.getAttributeName(i) == name && p.getAttributeNamespace(i) in RELATIONSHIP_NS) {
                return p.getAttributeValue(i)
            }
        }
        return null
    }

    /**
     * OOXML 의 참·거짓(`ST_OnOff`). 속성이 없으면 **켜짐**이다 — `<w:b/>` 는 굵게다.
     * `0`·`false`·`off` 만 꺼짐으로 본다.
     */
    fun onOff(p: XmlPullParser, name: String = "val"): Boolean {
        val v = attr(p, name) ?: return true
        return !(v == "0" || v.equals("false", true) || v.equals("off", true))
    }

    /** 정수 속성. 없거나 숫자가 아니면 null. */
    fun int(p: XmlPullParser, name: String): Int? = attr(p, name)?.trim()?.toIntOrNull()

    /** 긴 정수 속성(EMU 는 `Int` 를 넘는다). */
    fun long(p: XmlPullParser, name: String): Long? = attr(p, name)?.trim()?.toLongOrNull()

    /**
     * 지금 선 요소(START_TAG)의 **하위 전체를 건너뛴다.** 끝나면 그 요소의 END_TAG 에 서 있다.
     * 깊이 상한은 [nextGuarded] 가 건다.
     */
    fun skip(p: XmlPullParser, limits: ParseLimits) {
        check(p.eventType == XmlPullParser.START_TAG) { "시작 태그에서만 건너뛴다" }
        var depth = 1
        while (depth > 0) {
            when (p.nextGuarded(limits)) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * 지금 선 요소의 **글자만** 모은다(하위 요소의 글자까지). 끝나면 END_TAG 에 서 있다.
     * 모은 양이 [maxChars] 를 넘으면 거기서 자른다.
     */
    fun collectText(p: XmlPullParser, limits: ParseLimits, maxChars: Int = 64 * 1024): String {
        check(p.eventType == XmlPullParser.START_TAG) { "시작 태그에서만 모은다" }
        val out = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.nextGuarded(limits)) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT -> if (out.length < maxChars) {
                    val t = p.text ?: ""
                    out.append(t, 0, minOf(t.length, maxChars - out.length))
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return out.toString()
    }
}
