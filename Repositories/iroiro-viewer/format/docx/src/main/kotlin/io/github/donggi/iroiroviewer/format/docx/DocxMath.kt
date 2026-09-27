package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.xmlpull.v1.XmlPullParser

/**
 * 수식(OMML, `m:oMath`)을 **한 줄 글**로. 조판하지 않는다 — 손실이 있다는 것을 알고 쓰는 대체물이고,
 * 변환기가 [io.github.donggi.iroiroviewer.format.UnsupportedFeatures.EQUATION] 을 함께 센다.
 *
 * 글자만 이어 붙이면 `a over b` 가 `ab` 가 되어 뜻이 바뀐다. 그래서 뜻을 바꾸는 구조 몇 가지만
 * 기호로 적는다 — 분수 `(a)/(b)`(가로줄 없이 쌓은 것은 `n¦k`), 위·아래 첨자 `x^2`·`x_i`, 근호 `√(x)`, 괄호,
 * 큰 연산자 `∑`.
 */
internal object DocxMath {

    private const val MAX_CHARS = 4096
    private const val MAX_DEPTH = 64

    /** 가로줄 없이 쌓은 분수의 이음표(U+00A6) — UnicodeMath 의 'atop'. */
    private const val STACK = "¦"

    /** [p] 는 `m:oMath`·`m:oMathPara` 에 서 있다. 끝나면 그 끝 태그에 서 있다. */
    fun text(p: XmlPullParser, limits: ParseLimits): String {
        val sb = StringBuilder()
        walk(p, limits, sb, 0)
        return sb.toString().trim()
    }

    private fun walk(p: XmlPullParser, limits: ParseLimits, sb: StringBuilder, depth: Int) {
        if (depth > MAX_DEPTH) {
            OoxmlXml.skip(p, limits)
            return
        }
        var firstMath = true
        DocxProps.eachChild(p, limits) { name ->
            when {
                name == "t" -> append(sb, OoxmlXml.collectText(p, limits, MAX_CHARS))
                name == "f" -> fraction(p, limits, sb, depth)
                name == "sSup" || name == "sSub" || name == "sSubSup" || name == "sPre" -> script(p, limits, sb, depth)
                name == "rad" -> radical(p, limits, sb, depth)
                name == "d" -> delimiter(p, limits, sb, depth)
                name == "nary" -> nary(p, limits, sb, depth)
                // 문단 수식 안의 수식이 여럿이면 사이를 띄운다.
                name == "oMath" -> {
                    if (!firstMath) append(sb, "  ")
                    firstMath = false
                    walk(p, limits, sb, depth + 1)
                }
                // 속성(`m:rPr`·`m:fPr`·`w:rPr`…)은 글이 없다.
                name.endsWith("Pr") -> OoxmlXml.skip(p, limits)
                else -> walk(p, limits, sb, depth + 1)
            }
        }
    }

    private fun sub(p: XmlPullParser, limits: ParseLimits, depth: Int): String {
        val sb = StringBuilder()
        walk(p, limits, sb, depth + 1)
        return sb.toString()
    }

    private fun fraction(p: XmlPullParser, limits: ParseLimits, sb: StringBuilder, depth: Int) {
        var num = ""
        var den = ""
        var stacked = false
        DocxProps.eachChild(p, limits) { name ->
            when (name) {
                // 가로줄 없는 분수(`m:type=noBar`)는 나누기가 아니라 위아래로 쌓은 것이다 — 이항계수가 그것이다.
                // `/` 로 적으면 n÷k 로 읽혀 뜻이 바뀐다(실세계 표본 DX12 의 이항정리가 `(n/k)` 로 보였다).
                "fPr" -> DocxProps.eachChild(p, limits) { d ->
                    if (d == "type") stacked = OoxmlXml.attr(p, "val") == "noBar"
                    OoxmlXml.skip(p, limits)
                }
                "num" -> num = sub(p, limits, depth)
                "den" -> den = sub(p, limits, depth)
                else -> OoxmlXml.skip(p, limits)
            }
        }
        // 쌓은 것은 워드의 한 줄 수식 표기(UnicodeMath)처럼 `¦` 로 잇는다 — 괄호는 바깥의 `m:d` 가 준다.
        append(sb, group(num) + (if (stacked) STACK else "/") + group(den))
    }

    private fun script(p: XmlPullParser, limits: ParseLimits, sb: StringBuilder, depth: Int) {
        var base = ""
        var lower = ""
        var upper = ""
        DocxProps.eachChild(p, limits) { name ->
            when (name) {
                "e" -> base = sub(p, limits, depth)
                "sub" -> lower = sub(p, limits, depth)
                "sup" -> upper = sub(p, limits, depth)
                else -> OoxmlXml.skip(p, limits)
            }
        }
        val out = StringBuilder(base)
        if (lower.isNotEmpty()) out.append('_').append(group(lower))
        if (upper.isNotEmpty()) out.append('^').append(group(upper))
        append(sb, out)
    }

    private fun radical(p: XmlPullParser, limits: ParseLimits, sb: StringBuilder, depth: Int) {
        var degree = ""
        var body = ""
        DocxProps.eachChild(p, limits) { name ->
            when (name) {
                "deg" -> degree = sub(p, limits, depth)
                "e" -> body = sub(p, limits, depth)
                else -> OoxmlXml.skip(p, limits)
            }
        }
        append(sb, (if (degree.isNotBlank()) "[$degree]" else "") + "√(" + body + ")")
    }

    private fun delimiter(p: XmlPullParser, limits: ParseLimits, sb: StringBuilder, depth: Int) {
        var begin = "("
        var end = ")"
        var sep = "|"
        val parts = ArrayList<String>()
        DocxProps.eachChild(p, limits) { name ->
            when (name) {
                "dPr" -> DocxProps.eachChild(p, limits) { d ->
                    when (d) {
                        "begChr" -> begin = OoxmlXml.attr(p, "val") ?: ""
                        "endChr" -> end = OoxmlXml.attr(p, "val") ?: ""
                        "sepChr" -> sep = OoxmlXml.attr(p, "val") ?: ""
                    }
                    OoxmlXml.skip(p, limits)
                }
                "e" -> if (parts.size < 64) parts.add(sub(p, limits, depth)) else OoxmlXml.skip(p, limits)
                else -> OoxmlXml.skip(p, limits)
            }
        }
        append(sb, begin + parts.joinToString(sep) + end)
    }

    private fun nary(p: XmlPullParser, limits: ParseLimits, sb: StringBuilder, depth: Int) {
        var op = "∫"
        var lower = ""
        var upper = ""
        var body = ""
        DocxProps.eachChild(p, limits) { name ->
            when (name) {
                "naryPr" -> DocxProps.eachChild(p, limits) { d ->
                    if (d == "chr") op = OoxmlXml.attr(p, "val") ?: op
                    OoxmlXml.skip(p, limits)
                }
                "sub" -> lower = sub(p, limits, depth)
                "sup" -> upper = sub(p, limits, depth)
                "e" -> body = sub(p, limits, depth)
                else -> OoxmlXml.skip(p, limits)
            }
        }
        val out = StringBuilder(op)
        if (lower.isNotEmpty()) out.append('_').append(group(lower))
        if (upper.isNotEmpty()) out.append('^').append(group(upper))
        append(sb, out.append(' ').append(body))
    }

    /** 한 글자면 괄호 없이, 아니면 괄호로 묶는다 — `x^2` 와 `x^(n+1)`. */
    private fun group(s: String): String = if (s.length <= 1) s else "($s)"

    private fun append(sb: StringBuilder, s: CharSequence) {
        if (sb.length >= MAX_CHARS) return
        sb.append(s, 0, minOf(s.length, MAX_CHARS - sb.length))
    }
}
