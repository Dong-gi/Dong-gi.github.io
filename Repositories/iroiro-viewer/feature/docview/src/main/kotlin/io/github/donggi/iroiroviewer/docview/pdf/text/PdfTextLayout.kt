package io.github.donggi.iroiroviewer.docview.pdf.text

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 꺼낸 글자들 위에서 **고르기** — 누른 자리의 글자, 낱말, 손잡이의 경계, 칠할 사각형, 복사할 글. 순수 함수라 JVM 시험이
 * 답한다(`PdfTextLayoutTest`).
 *
 * ## 선택은 글자 사이의 경계 둘이다
 *
 * `[start, end)` — 글자 번호의 반열린 범위. 손잡이 하나는 경계 하나를 잡는다. 글자의 차례는 내용 스트림의 차례라서(대개
 * 읽는 차례) 두 경계 사이의 글자를 그 차례대로 잇는다. 단을 나눈 문서에서 단을 넘어 고르면 그 사이의 글이 함께 골라질 수
 * 있다 — pdfium·pdf.js 도 같다.
 *
 * ## 줄과 띄어쓰기는 자리로 짐작한다
 *
 * PDF 에는 줄도 낱말도 없다 — 글자가 놓인 자리뿐이다. 다음 글자가 기준선에서 글자 크기의 절반 넘게 벗어나면 줄이 바뀐 것으로,
 * 같은 줄에서 앞 글자 끝과 [SPACE_GAP] 넘게 떨어지면 띄어 쓴 것으로 본다(문서가 공백 글자를 따로 적었으면 그것을 쓴다).
 */
internal object PdfTextLayout {

    /** 띄어쓰기로 보는 빈 자리(글자 크기에 대한 비율). 낱말 사이는 보통 0.25 em, 자간은 0.1 em 아래다. */
    const val SPACE_GAP = 0.18f

    /** 한 번에 복사하는 글자 수의 상한 — 텍스트 뷰어와 같은 까닭(`RowCopy.MAX_CHARS` — 바인더 한도). */
    const val MAX_COPY_CHARS = 200_000

    fun sameLine(a: Glyph, b: Glyph): Boolean {
        // 방향이 크게 다르면(가로 → 세로) 다른 줄이다.
        if (a.dirX * b.dirX + a.dirY * b.dirY < 0.7f) return false
        val vx = b.originX - a.originX
        val vy = b.originY - a.originY
        // 앞 글자의 기준선에 수직인 거리.
        val perp = abs(vx * -a.dirY + vy * a.dirX)
        return perp <= 0.5f * max(a.size, b.size)
    }

    /** 같은 줄에서 앞 글자의 끝과 다음 글자의 시작 사이(나아가는 방향으로 잰 거리). */
    fun gap(a: Glyph, b: Glyph): Float {
        val endX = a.originX + a.dirX * a.advance
        val endY = a.originY + a.dirY * a.advance
        return (b.originX - endX) * a.dirX + (b.originY - endY) * a.dirY
    }

    private fun isSpace(g: Glyph) = g.text.isNotEmpty() && g.text.isBlank()

    /** 두 글자 사이에 넣을 것 — 줄바꿈, 띄어쓰기, 또는 없음. */
    fun separator(a: Glyph, b: Glyph): String {
        if (!sameLine(a, b)) return "\n"
        if (isSpace(a) || isSpace(b)) return ""
        val g = gap(a, b)
        val size = max(a.size, b.size)
        // 크게 뒤로 돌아간 것(같은 높이의 다른 단)도 띄어 둔다 — 글자가 붙어 버리면 두 낱말이 하나가 된다.
        return if (g > SPACE_GAP * size || g < -2f * size) " " else ""
    }

    /**
     * `[start, end)` 의 글. 글자를 모르는 코드는 빠지고, 합자(ﬁ)는 풀고, 줄 끝의 공백은 지운다. [limit] 을 넘으면 null.
     */
    fun text(page: PageText, start: Int, end: Int, limit: Int = MAX_COPY_CHARS): String? {
        val g = page.glyphs
        val s = start.coerceIn(0, g.size)
        val e = end.coerceIn(s, g.size)
        val out = StringBuilder()
        for (i in s until e) {
            if (i > s) {
                val sep = separator(g[i - 1], g[i])
                if (sep == "\n") {
                    while (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
                }
                out.append(sep)
            }
            appendClean(out, g[i].text)
            if (out.length > limit) return null
        }
        return out.toString().trimEnd(' ')
    }

    private fun appendClean(out: StringBuilder, text: String) {
        for (ch in text) {
            when (ch) {
                '�', '\u0000' -> Unit
                ' ' -> out.append(' ')
                'ﬀ' -> out.append("ff")
                'ﬁ' -> out.append("fi")
                'ﬂ' -> out.append("fl")
                'ﬃ' -> out.append("ffi")
                'ﬄ' -> out.append("ffl")
                'ﬅ', 'ﬆ' -> out.append("st")
                else -> if (ch >= ' ' || ch == '\t') out.append(ch)
            }
        }
    }

    /** 낱말을 이루는 글자인가 — 글자·숫자와 이음표(밑줄·하이픈 가운데). 한글·한자·가나는 글자다. */
    private fun isWordChar(g: Glyph): Boolean {
        val t = g.text
        if (t.isEmpty()) return false
        val c = t.codePointAt(0)
        return Character.isLetterOrDigit(c) || c == '_'.code || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
    }

    /**
     * [i] 번째 글자가 든 낱말 — `[start, end)`. 글자가 아니면(문장 부호·기호) 그 글자 하나다. 낱말은 같은 줄에서 띄어 쓰지
     * 않고 이어진 글자들이다.
     */
    fun wordAt(page: PageText, i: Int): IntRange {
        val g = page.glyphs
        if (i !in g.indices) return IntRange.EMPTY
        if (!isWordChar(g[i])) return i..i
        var s = i
        while (s > 0 && isWordChar(g[s - 1]) && separator(g[s - 1], g[s]).isEmpty()) s--
        var e = i
        while (e + 1 < g.size && isWordChar(g[e + 1]) && separator(g[e], g[e + 1]).isEmpty()) e++
        return s..e
    }

    /** 점에서 상자까지의 거리(안이면 0). */
    private fun distance(g: Glyph, x: Float, y: Float): Float {
        val dx = max(max(g.left - x, 0f), x - g.right)
        val dy = max(max(g.top - y, 0f), y - g.bottom)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * 누른 자리의 글자. 상자 안이 아니어도 [slop](포인트) 안이면 가장 가까운 것 — 손가락은 글자보다 굵다. 없으면 null.
     *
     * **띄어쓰기 글자는 고르지 않는다** — 낱말 사이를 누르면 가까운 낱말이 골라져야 한다(기기에서 띄어쓰기 하나만 칠해졌다).
     */
    fun hit(page: PageText, x: Float, y: Float, slop: Float): Int? {
        var best = -1
        var bestD = Float.MAX_VALUE
        page.glyphs.forEachIndexed { i, g ->
            if (g.text.isBlank()) return@forEachIndexed
            val d = distance(g, x, y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best.takeIf { it >= 0 && bestD <= slop }
    }

    /**
     * 손잡이가 놓인 자리의 **경계**(0~글자 수). 가장 가까운 글자를 찾고, 그 글자의 가운데보다 나아가는 쪽이면 그 뒤,
     * 아니면 그 앞이다. 글자가 없으면 null.
     */
    fun boundaryAt(page: PageText, x: Float, y: Float): Int? {
        var best = -1
        var bestD = Float.MAX_VALUE
        page.glyphs.forEachIndexed { i, g ->
            val d = distance(g, x, y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best < 0) return null
        val g = page.glyphs[best]
        val cx = (g.left + g.right) / 2f
        val cy = (g.top + g.bottom) / 2f
        val along = (x - cx) * g.dirX + (y - cy) * g.dirY
        return if (along >= 0f) best + 1 else best
    }

    /**
     * `[start, end)` 를 칠할 사각형들(포인트, `[왼, 위, 오른, 아래]`). 같은 줄에 이어진 글자들을 한 사각형으로 묶는다.
     */
    fun rects(page: PageText, start: Int, end: Int): List<FloatArray> {
        val g = page.glyphs
        val s = start.coerceIn(0, g.size)
        val e = end.coerceIn(s, g.size)
        val out = ArrayList<FloatArray>()
        var cur: FloatArray? = null
        for (i in s until e) {
            val gl = g[i]
            val c = cur
            if (c != null && sameLine(g[i - 1], gl) && gap(g[i - 1], gl) > -2f * gl.size) {
                c[0] = min(c[0], gl.left)
                c[1] = min(c[1], gl.top)
                c[2] = max(c[2], gl.right)
                c[3] = max(c[3], gl.bottom)
            } else {
                cur = floatArrayOf(gl.left, gl.top, gl.right, gl.bottom).also { out += it }
            }
        }
        return out
    }
}
