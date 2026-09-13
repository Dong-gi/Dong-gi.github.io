package io.github.donggi.iroiroviewer.format.text

/**
 * XML·HTML·SVG 를 위한 훑개.
 *
 * ## 왜 파서가 아닌가
 *
 * `format:html`(11단계)은 **파싱**을 하지만 여기서는 하지 않는다. 강조는 깨진 파일에도
 * 무언가를 보여 줘야 하고, 사람이 뷰어로 여는 XML 은 깨져 있어서 여는 경우가 많다.
 * 훑개는 닫히지 않은 태그를 만나도 그 자리부터 다시 정신을 차린다.
 *
 * ## 색을 넷으로만 나눈다
 *
 * 태그 이름 · 속성 이름 · 속성 값 · 주석. 실체 참조(`&amp;`)는 칠하지 않는다 —
 * 본문에서 그것만 색이 다르면 글이 읽히지 않는다.
 */
class MarkupHighlighter(override val label: String) : RowHighlighter {

    override val multiline = true

    override fun highlight(row: String, startState: Int, out: MutableList<Span>): Int {
        var state = startState
        var i = 0
        val n = row.length

        while (i < n) {
            when (state) {
                STATE_COMMENT -> {
                    val end = row.indexOf("-->", i)
                    return if (end < 0) {
                        out += Span(i, n, TokenKind.COMMENT)
                        STATE_COMMENT
                    } else {
                        out += Span(i, end + 3, TokenKind.COMMENT)
                        i = end + 3
                        state = STATE_TEXT
                        continue
                    }
                }

                STATE_CDATA -> {
                    val end = row.indexOf("]]>", i)
                    return if (end < 0) {
                        out += Span(i, n, TokenKind.STRING)
                        STATE_CDATA
                    } else {
                        out += Span(i, end + 3, TokenKind.STRING)
                        i = end + 3
                        state = STATE_TEXT
                        continue
                    }
                }

                STATE_DQ, STATE_SQ -> {
                    val quote = if (state == STATE_DQ) '"' else '\''
                    val end = row.indexOf(quote, i)
                    return if (end < 0) {
                        out += Span(i, n, TokenKind.STRING)
                        state
                    } else {
                        out += Span(i, end + 1, TokenKind.STRING)
                        i = end + 1
                        state = STATE_TAG
                        continue
                    }
                }

                STATE_TAG -> {
                    val c = row[i]
                    when {
                        c == '>' -> {
                            i++
                            state = STATE_TEXT
                        }
                        c == '"' || c == '\'' -> {
                            val end = row.indexOf(c, i + 1)
                            if (end < 0) {
                                out += Span(i, n, TokenKind.STRING)
                                return if (c == '"') STATE_DQ else STATE_SQ
                            }
                            out += Span(i, end + 1, TokenKind.STRING)
                            i = end + 1
                        }
                        isNameStart(c) -> {
                            var j = i + 1
                            while (j < n && isNameChar(row[j])) j++
                            out += Span(i, j, TokenKind.ATTRIBUTE)
                            i = j
                        }
                        else -> i++
                    }
                }

                else -> { // STATE_TEXT
                    val lt = row.indexOf('<', i)
                    if (lt < 0) return STATE_TEXT
                    when {
                        row.startsWith("<!--", lt) -> {
                            i = lt
                            state = STATE_COMMENT
                        }
                        row.startsWith("<![CDATA[", lt) -> {
                            out += Span(lt, minOf(lt + 9, n), TokenKind.STRING)
                            i = lt + 9
                            state = STATE_CDATA
                        }
                        else -> {
                            // 태그 이름. `</div`·`<?xml`·`<!DOCTYPE` 도 여기로 온다.
                            var j = lt + 1
                            while (j < n && (row[j] == '/' || row[j] == '?' || row[j] == '!')) j++
                            while (j < n && isNameChar(row[j])) j++
                            out += Span(lt, j, TokenKind.TAG)
                            i = j
                            state = STATE_TAG
                        }
                    }
                }
            }
        }
        return state
    }

    // **이 클래스는 상태를 필드로 들지 않는다.** 훑개 하나를 여러 행이 나눠 쓰고, 나중에는
    // 여러 코루틴이 나눠 쓸 수 있다. 행 사이에 넘길 것은 반환값인 상태 정수뿐이다.

    private fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'
    private fun isNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == ':' || c == '-' || c == '.'

    companion object {
        private const val STATE_TEXT = 0
        private const val STATE_TAG = 1
        private const val STATE_COMMENT = 2
        private const val STATE_DQ = 3
        private const val STATE_SQ = 4
        private const val STATE_CDATA = 5
    }
}
