package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/** SpreadsheetML 을 읽을 때 되풀이되는 것들. 깊이 상한은 전부 `nextGuarded` 가 건다. */
internal object XlsxXml {

    /**
     * 지금 선 요소(START_TAG)의 **자식마다** [onChild] 를 부른다. 끝나면 그 요소의 END_TAG 에 서 있다.
     *
     * [onChild] 는 자식을 끝까지 읽어도 되고(그 자식의 END_TAG 에서 돌아온다) 속성만 보고 돌아와도
     * 된다 — 시작 태그에 그대로 서 있으면 여기서 하위 전체를 건너뛴다. 그래서 모르는 요소가 아무리
     * 깊어도 부르는 쪽이 신경 쓸 것이 없다.
     */
    inline fun children(p: XmlPullParser, limits: ParseLimits, onChild: (name: String) -> Unit) {
        val depth = p.depth
        while (true) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth <= depth) return
            if (ev == XmlPullParser.START_TAG && p.depth == depth + 1) {
                onChild(p.name)
                if (p.eventType == XmlPullParser.START_TAG && p.depth == depth + 1) OoxmlXml.skip(p, limits)
            }
        }
    }

    /** 문서의 뿌리 요소까지 간다. 뿌리가 없으면 false. */
    fun toRoot(p: XmlPullParser, limits: ParseLimits): Boolean {
        var ev = p.eventType
        while (ev != XmlPullParser.START_TAG && ev != XmlPullParser.END_DOCUMENT) ev = p.nextGuarded(limits)
        return ev == XmlPullParser.START_TAG
    }

    /**
     * 서식 있는 글자(`si`·`is`)의 **글자만**. 조각(`r/t`)을 이어 붙이고, 윗주(`rPh` — 일본어 후리가나
     * 따위)는 건너뛴다 — Excel 도 칸에는 본문만 보인다. 끝나면 그 요소의 END_TAG 에 서 있다.
     */
    fun readRichText(p: XmlPullParser, limits: ParseLimits, maxChars: Int = XlsxLimits.MAX_CELL_CHARS): String {
        val depth = p.depth
        val out = StringBuilder()
        while (true) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && p.depth <= depth) break
            if (ev != XmlPullParser.START_TAG) continue
            when (p.name) {
                "rPh", "rPr", "phoneticPr", "extLst" -> OoxmlXml.skip(p, limits)
                "t" -> {
                    val room = maxChars - out.length
                    if (room <= 0) OoxmlXml.skip(p, limits) else out.append(OoxmlXml.collectText(p, limits, room))
                }
                else -> Unit // `r` 따위는 안으로 들어간다.
            }
        }
        return decode(out)
    }

    /**
     * `_xHHHH_` 을 글자로 — ECMA-376 의 `ST_Xstring`. XML 이 담지 못하는 제어문자(`_x000D_`)와
     * 밑줄 자체(`_x005F_`)가 이 모양으로 온다. 왼쪽부터 한 번만 푼다 — `_x005F_x0041_` 은
     * `_x0041_` 이 된다(명세의 뜻이다).
     */
    fun decode(s: CharSequence): String {
        val str = s.toString()
        if (!str.contains("_x")) return str
        val out = StringBuilder(str.length)
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '_' && i + 6 < str.length && str[i + 1] == 'x' && str[i + 6] == '_') {
                val code = hex4(str, i + 2)
                if (code >= 0) {
                    out.append(code.toChar())
                    i += 7
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun hex4(s: String, at: Int): Int {
        var v = 0
        for (k in 0 until 4) {
            val d = Character.digit(s[at + k], 16)
            if (d < 0) return -1
            v = v * 16 + d
        }
        return v
    }
}
