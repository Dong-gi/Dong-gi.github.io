package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/**
 * HWPX 를 읽을 때 되풀이되는 것들.
 *
 * ## 이름공간을 거의 보지 않는다
 *
 * HWPX 에는 **세 벌의 이름공간**이 있다 — 한컴의 2011 핵심(`hwpml/2011` 아래), 그 곁의 2016 확장
 * (`hwpml/2016` 아래), 그리고 KS X 6101:2024 의 스키마가 쓰는 `owpml.org/owpml/2024` 아래의 것. 실물은 전부
 * 2011 + 2016 이지만(조사 노트) 2024 를 쓰는 작성기가 언젠가 나올 수 있다. 접두어는 문서 마음이다.
 * 그래서 요소는 **지역 이름**으로 가른다 — `format:opc` 의 `OoxmlXml` 과 같은 판단이다. 본문·머리 XML 에
 * 같은 지역 이름의 남의 요소가 섞이는 일은 없다(한컴 모델의 요소 표로 확인했다).
 */
internal object HwpxXml {

    /** 지역 이름이 [name] 인 속성. 이름공간을 보지 않는다(`hp:required-namespace` 도 이것으로 찾는다). */
    fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) {
            if (p.getAttributeName(i) == name) return p.getAttributeValue(i)
        }
        return null
    }

    /** 정수 속성. 없거나 숫자가 아니면 null. 한글은 부호 없는 값(`4294967295`)을 쓰는 자리가 있어 `Long` 을 거친다. */
    fun int(p: XmlPullParser, name: String): Int? {
        val v = attr(p, name)?.trim()?.toLongOrNull() ?: return null
        return if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
    }

    /** 참·거짓. 한글은 `0`/`1` 로도, `true`/`false` 로도 적는다. */
    fun bool(p: XmlPullParser, name: String): Boolean? = when (attr(p, name)?.trim()?.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }

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
     * [maxChars] 를 넘는 것은 버린다. CDATA 도 `next()` 가 TEXT 로 준다 — 수식 스크립트가 CDATA 로
     * 오는 파일이 있다(rhwp 의 보고).
     */
    fun collectText(p: XmlPullParser, limits: ParseLimits, maxChars: Int): String {
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

    /**
     * 지금 선 요소의 자식마다 [onChild] 를 부른다. [onChild] 는 **자식 요소를 끝까지 소비해야** 한다
     * (끝 태그에 서서 돌아온다).
     */
    inline fun eachChild(p: XmlPullParser, limits: ParseLimits, onChild: (String) -> Unit) {
        val depth = p.depth
        while (true) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth == depth) return
            if (ev == XmlPullParser.START_TAG) onChild(p.name)
        }
    }

    /** 첫 요소(뿌리)까지 간다. 뿌리가 없으면 거짓. */
    fun toRoot(p: XmlPullParser, limits: ParseLimits): Boolean {
        var ev = p.eventType
        while (ev != XmlPullParser.START_TAG) {
            if (ev == XmlPullParser.END_DOCUMENT) return false
            ev = p.nextGuarded(limits)
        }
        return true
    }

    /**
     * 한글의 색(`#RRGGBB`)을 CSS 의 모양(`#rrggbb`)으로. `none` 이나 모르는 모양이면 null.
     * 여덟 자리(`#AARRGGBB`)는 앞의 알파를 버린다 — 한글은 무늬 색에 그렇게 적는다.
     */
    fun color(raw: String?): String? {
        val v = raw?.trim()?.removePrefix("#") ?: return null
        return io.github.donggi.iroiroviewer.format.html.CssValues.hexColor(v)
    }
}
