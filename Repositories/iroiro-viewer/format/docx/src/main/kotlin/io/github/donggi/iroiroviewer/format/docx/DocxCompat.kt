package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.xmlpull.v1.XmlPullParser

/**
 * 호환 블록(`mc:AlternateContent`, ECMA-376 Part 3) — 같은 내용을 새 방식(`mc:Choice`)과 옛 방식
 * (`mc:Fallback`)으로 함께 적은 것. **하나만** 골라야 글상자의 글이 두 번 나오지 않는다.
 *
 * ## 어느 가지를 고르는가
 *
 * 명세의 규칙 그대로다 — `Requires` 의 이름공간을 **전부 이해하는 첫 `mc:Choice`**, 없으면
 * `mc:Fallback`. '이해한다' 는 그 가지를 우리가 제대로 그린다는 뜻이다: 2010 년 이후의 그리기
 * 모양(`wps`·`wpg`·`wpc`)은 글상자의 글과 그림을 그대로 주므로 이해하는 쪽이고, 이모지 기호(`w16se`)·
 * 새 차트(`cx`)·잉크처럼 우리가 그리지 못하는 것은 이해하지 않는 쪽이라 옛 방식(대개 글자나 그림)이 뽑힌다.
 *
 * 흐름으로 읽으므로 뒤에 `mc:Fallback` 이 있는지 미리 볼 수 없다 — '대체가 있으면 대체' 는
 * 되감기 없이는 할 수 없는 규칙이고, 명세의 규칙은 앞에서부터 한 번에 정해진다.
 */
internal object DocxCompat {

    private val UNDERSTOOD = setOf(
        "http://schemas.microsoft.com/office/word/2010/wordprocessingShape",
        "http://schemas.microsoft.com/office/word/2010/wordprocessingGroup",
        "http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas",
        "http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing",
        "http://schemas.microsoft.com/office/word/2010/wordml",
        "http://schemas.microsoft.com/office/word/2012/wordml",
        "http://schemas.microsoft.com/office/drawing/2010/main",
        "http://schemas.openxmlformats.org/officeDocument/2006/math",
    )

    private val SPACES = Regex("\\s+")

    /**
     * [p] 는 `mc:AlternateContent` 에 서 있다. 고른 가지의 시작 태그 위에서 [branch] 를 부른다
     * ([branch] 는 그 가지를 끝까지 소비해야 한다). 고른 것이 없으면 거짓.
     */
    inline fun choose(p: XmlPullParser, limits: ParseLimits, branch: () -> Unit): Boolean {
        var taken = false
        DocxProps.eachChild(p, limits) { name ->
            if (!taken && (name == "Fallback" || (name == "Choice" && understood(p)))) {
                taken = true
                branch()
            } else {
                OoxmlXml.skip(p, limits)
            }
        }
        return taken
    }

    /** 이 `mc:Choice` 의 `Requires` 를 전부 이해하는가. 접두어를 **이름공간으로 풀어** 본다. */
    fun understood(p: XmlPullParser): Boolean {
        val req = OoxmlXml.attr(p, "Requires")?.trim() ?: return false
        if (req.isEmpty() || req.length > 256) return false
        val prefixes = req.split(SPACES)
        return prefixes.all { prefix -> p.getNamespace(prefix) in UNDERSTOOD }
    }
}
