package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/**
 * 여는 동안 슬라이드마다 한 번 훑어 얻는 것 — 목차에 쓸 **제목**과 **애니메이션이 있는가**.
 *
 * 값을 만들지 않고 흘려보내기만 한다(`DrawingParser` 보다 훨씬 가볍다). 슬라이드가 2,000 장이어도
 * 여는 시간이 그리는 시간만큼 들지 않게 하려는 것이다.
 */
internal class SlideScan(val title: String, val animated: Boolean)

internal object PptxScan {

    /** 목차·부분 이름의 글자 수 상한. */
    const val MAX_LABEL = 120

    /** 모으는 글자의 상한. 제목 하나가 수 MB 여도 여기서 멈춘다. */
    private const val MAX_COLLECT = 1_000

    /** 애니메이션 동작 요소(`p:timing` 안). 이것이 하나라도 있어야 '애니메이션이 있다' 로 본다 — 오피스는 빈 `timing` 뿌리를 남기기도 한다. */
    private val ANIMATION_BEHAVIORS = setOf(
        "anim", "animEffect", "animMotion", "animRot", "animScale", "animClr", "set", "cmd", "audio", "video",
    )

    /**
     * 제목은 `title`·`ctrTitle` 개체 틀의 글자다. 문단 사이·줄바꿈은 공백 하나로 모은다.
     * 전환 효과(`p:transition`)는 **자식(효과)이 있을 때만** 센다 — 자식 없는 전환은 '몇 초 뒤
     * 넘기기' 같은 시간 설정뿐이다.
     */
    fun scan(p: XmlPullParser, limits: ParseLimits): SlideScan {
        var title: String? = null
        var spDepth = -1
        var isTitle = false
        val buf = StringBuilder()
        var timingDepth = -1
        var transitionDepth = -1
        var animated = false
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG) {
                val n = p.name
                val d = p.depth
                when {
                    n == "sp" && spDepth < 0 -> {
                        spDepth = d
                        isTitle = false
                        buf.setLength(0)
                    }
                    n == "ph" && spDepth >= 0 -> {
                        val t = OoxmlXml.attr(p, "type")
                        if (t == "title" || t == "ctrTitle") isTitle = true
                    }
                    n == "t" && spDepth >= 0 && isTitle && title == null -> {
                        if (buf.length < MAX_COLLECT) buf.append(OoxmlXml.collectText(p, limits, MAX_COLLECT - buf.length))
                    }
                    n == "br" && spDepth >= 0 && isTitle -> buf.append(' ')
                    n == "timing" && timingDepth < 0 -> timingDepth = d
                    timingDepth >= 0 && n in ANIMATION_BEHAVIORS -> animated = true
                    n == "transition" && transitionDepth < 0 -> transitionDepth = d
                    transitionDepth >= 0 && d == transitionDepth + 1 -> animated = true
                }
            } else if (e == XmlPullParser.END_TAG) {
                val n = p.name
                val d = p.depth
                if (n == "p" && spDepth >= 0 && isTitle) buf.append(' ')
                if (n == "sp" && d == spDepth) {
                    if (isTitle && title == null) label(buf).takeIf { it.isNotEmpty() }?.let { title = it }
                    spDepth = -1
                    isTitle = false
                }
                if (n == "timing" && d == timingDepth) timingDepth = -1
                if (n == "transition" && d == transitionDepth) transitionDepth = -1
            }
            e = p.nextGuarded(limits)
        }
        return SlideScan(title.orEmpty(), animated)
    }

    /**
     * 사람에게 보일 한 줄. 공백(줄바꿈·탭·세로 탭 포함)을 하나로 모으고 앞뒤를 떼어 [MAX_LABEL] 자로 자른다.
     * 자른 자리가 대리 문자 쌍의 가운데면 반쪽을 버린다.
     */
    fun label(raw: CharSequence): String {
        val sb = StringBuilder()
        var space = false
        for (c in raw) {
            if (c.isWhitespace() || c.code < 0x20) {
                space = sb.isNotEmpty()
            } else {
                if (space) sb.append(' ')
                space = false
                sb.append(c)
            }
            if (sb.length > MAX_LABEL) break
        }
        var s = if (sb.length > MAX_LABEL) sb.substring(0, MAX_LABEL) else sb.toString()
        if (s.isNotEmpty() && s.last().isHighSurrogate()) s = s.dropLast(1)
        return s.trim()
    }

    /** 문서 제목(`docProps/core.xml` 의 `dc:title`). 없으면 빈 문자열. */
    fun coreTitle(p: XmlPullParser, limits: ParseLimits): String {
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "title" && p.namespace == DC_NS) {
                return label(OoxmlXml.collectText(p, limits, MAX_COLLECT))
            }
            e = p.nextGuarded(limits)
        }
        return ""
    }

    /** 메모 부분의 메모 수(옛 `p:cm`, 새 `p188:cm` 모두 지역 이름이 `cm`). */
    fun countComments(p: XmlPullParser, limits: ParseLimits, max: Int): Int {
        var n = 0
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT && n < max) {
            if (e == XmlPullParser.START_TAG && p.name == "cm") n++
            e = p.nextGuarded(limits)
        }
        return n
    }

    /**
     * SmartArt 데이터 부분에서 **캐시된 그림을 가리키는 관계 id** 를 찾는다
     * (`dsp:dataModelExt relId="rId6"`). 그 id 는 데이터 부분이 아니라 **슬라이드**의 관계에 있다.
     */
    fun diagramDrawingRelId(p: XmlPullParser, limits: ParseLimits): String? {
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "dataModelExt") {
                return OoxmlXml.attr(p, "relId")?.takeIf { it.isNotBlank() }
            }
            e = p.nextGuarded(limits)
        }
        return null
    }

    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
}
