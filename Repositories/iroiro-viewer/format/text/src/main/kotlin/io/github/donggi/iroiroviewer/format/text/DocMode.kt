package io.github.donggi.iroiroviewer.format.text

import java.util.Locale

/**
 * HTML 문서가 **어느 모드로 그려지는가**(쿼크·제한 쿼크·표준) — 첫 DOCTYPE 이 정한다(HTML 명세의 'initial' 삽입 모드).
 *
 * ## 왜 따지는가
 *
 * 위생기는 DOCTYPE 을 지우고(XHTML 의 것에는 엔티티 선언이 섞인다), EPUB·마크다운 미리보기의 껍데기(`HtmlShell`)는 언제나
 * `<!DOCTYPE html>` 을 붙여 표준 모드로 그린다 — 그쪽은 XML 이라 어느 읽기 프로그램에서도 표준 모드이기 때문이다(함정 표).
 * 사용자의 HTML 은 다르다. **DOCTYPE 이 없는 옛 쪽은 쿼크 모드에서 만들어졌고**, 쓴 사람이 본 모양이 그것이다 — 표의 글자 크기,
 * `height:100%` 표, 그림 밑의 틈. 표준 모드로 바꿔 그리면 우리가 그 쪽을 다시 짜는 셈이다. 그래서 HTML 은 **문서의 DOCTYPE 이
 * 정하는 대로**, XHTML 은 언제나 표준으로 그린다([HtmlPreview.prepare]).
 *
 * 제한 쿼크(XHTML 1.0 Transitional 등)는 표준과 거의 같아(표 칸 안의 그림 높이 하나) 표준으로 그린다.
 */
internal object DocMode {

    enum class Mode { NO_QUIRKS, LIMITED_QUIRKS, QUIRKS }

    /**
     * @param doctype `<!DOCTYPE` 뒤부터 첫 `>` 앞까지. 문서에 (내용보다 먼저 온) DOCTYPE 이 없으면 null — 쿼크다.
     * @param closed 그 `>` 가 있었는가. 없으면(파일 끝) 명세가 강제 쿼크로 본다.
     */
    fun of(doctype: String?, closed: Boolean): Mode {
        if (doctype == null || !closed) return Mode.QUIRKS
        val d = parse(doctype) ?: return Mode.QUIRKS
        if (!d.name.equals("html", ignoreCase = true)) return Mode.QUIRKS
        val pub = d.publicId?.lowercase(Locale.ROOT)
        val sys = d.systemId?.lowercase(Locale.ROOT)
        if (pub != null) {
            if (pub in QUIRKS_PUBLIC_EXACT) return Mode.QUIRKS
            if (QUIRKS_PUBLIC_PREFIXES.any { pub.startsWith(it) }) return Mode.QUIRKS
            if (sys == null && (pub.startsWith(HTML401_FRAMESET) || pub.startsWith(HTML401_TRANSITIONAL))) return Mode.QUIRKS
        }
        if (sys == IBM_XHTML_SYSTEM) return Mode.QUIRKS
        if (pub != null) {
            if (pub.startsWith(XHTML10_FRAMESET) || pub.startsWith(XHTML10_TRANSITIONAL)) return Mode.LIMITED_QUIRKS
            if (sys != null && (pub.startsWith(HTML401_FRAMESET) || pub.startsWith(HTML401_TRANSITIONAL))) {
                return Mode.LIMITED_QUIRKS
            }
        }
        return Mode.NO_QUIRKS
    }

    private class Parsed(val name: String, val publicId: String?, val systemId: String?)

    /**
     * 명세의 DOCTYPE 토큰 상태를 줄인 것. **강제 쿼크면 null** — 이름이 없다, `PUBLIC`·`SYSTEM` 이 아닌 글이 온다, 식별자의 따옴표가
     * 닫히기 전에 `>` 가 왔다(문자열이 그 자리에서 끝났다), 공개 식별자 뒤에 따옴표가 아닌 글이 온다. 시스템 식별자 뒤의 군글은
     * 강제 쿼크가 아니다(명세가 그렇다).
     */
    private fun parse(s: String): Parsed? {
        val n = s.length
        var i = skipSpace(s, 0)
        if (i >= n) return null
        val nameStart = i
        while (i < n && !isSpace(s[i])) i++
        val name = s.substring(nameStart, i)
        i = skipSpace(s, i)
        if (i >= n) return Parsed(name, null, null)
        val system = when {
            s.regionMatches(i, "PUBLIC", 0, 6, ignoreCase = true) -> false
            s.regionMatches(i, "SYSTEM", 0, 6, ignoreCase = true) -> true
            else -> return null
        }
        i = skipSpace(s, i + 6)
        if (i >= n || !isQuote(s[i])) return null
        val first = quoted(s, i) ?: return null
        i = skipSpace(s, first.second)
        if (system) return Parsed(name, null, first.first)
        if (i >= n) return Parsed(name, first.first, null)
        if (!isQuote(s[i])) return null
        val second = quoted(s, i) ?: return null
        return Parsed(name, first.first, second.first)
    }

    /** [at] 의 따옴표부터 짝까지. 짝이 없으면 null — 잘린 자리가 `>` 였다는 뜻이다. */
    private fun quoted(s: String, at: Int): Pair<String, Int>? {
        val q = s[at]
        val close = s.indexOf(q, at + 1)
        if (close < 0) return null
        return s.substring(at + 1, close) to close + 1
    }

    private fun isQuote(c: Char): Boolean = c == '"' || c == '\''

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C'

    private fun skipSpace(s: String, from: Int): Int {
        var i = from
        while (i < s.length && isSpace(s[i])) i++
        return i
    }

    private const val HTML401_FRAMESET = "-//w3c//dtd html 4.01 frameset//"
    private const val HTML401_TRANSITIONAL = "-//w3c//dtd html 4.01 transitional//"
    private const val XHTML10_FRAMESET = "-//w3c//dtd xhtml 1.0 frameset//"
    private const val XHTML10_TRANSITIONAL = "-//w3c//dtd xhtml 1.0 transitional//"
    private const val IBM_XHTML_SYSTEM = "http://www.ibm.com/data/dtd/v11/ibmxhtml1-transitional.dtd"

    private val QUIRKS_PUBLIC_EXACT = setOf(
        "-//w3o//dtd w3 html strict 3.0//en//",
        "-/w3c/dtd html 4.0 transitional/en",
        "html",
    )

    /** HTML 명세(13.2.6.4.1)의 목록을 소문자로 옮겼다. 앞부분이 같으면 쿼크다. */
    private val QUIRKS_PUBLIC_PREFIXES = listOf(
        "+//silmaril//dtd html pro v0r11 19970101//",
        "-//as//dtd html 3.0 aswedit + extensions//",
        "-//advasoft ltd//dtd html 3.0 aswedit + extensions//",
        "-//ietf//dtd html 2.0 level 1//",
        "-//ietf//dtd html 2.0 level 2//",
        "-//ietf//dtd html 2.0 strict level 1//",
        "-//ietf//dtd html 2.0 strict level 2//",
        "-//ietf//dtd html 2.0 strict//",
        "-//ietf//dtd html 2.0//",
        "-//ietf//dtd html 2.1e//",
        "-//ietf//dtd html 3.0//",
        "-//ietf//dtd html 3.2 final//",
        "-//ietf//dtd html 3.2//",
        "-//ietf//dtd html 3//",
        "-//ietf//dtd html level 0//",
        "-//ietf//dtd html level 1//",
        "-//ietf//dtd html level 2//",
        "-//ietf//dtd html level 3//",
        "-//ietf//dtd html strict level 0//",
        "-//ietf//dtd html strict level 1//",
        "-//ietf//dtd html strict level 2//",
        "-//ietf//dtd html strict level 3//",
        "-//ietf//dtd html strict//",
        "-//ietf//dtd html//",
        "-//metrius//dtd metrius presentational//",
        "-//microsoft//dtd internet explorer 2.0 html strict//",
        "-//microsoft//dtd internet explorer 2.0 html//",
        "-//microsoft//dtd internet explorer 2.0 tables//",
        "-//microsoft//dtd internet explorer 3.0 html strict//",
        "-//microsoft//dtd internet explorer 3.0 html//",
        "-//microsoft//dtd internet explorer 3.0 tables//",
        "-//netscape comm. corp.//dtd html//",
        "-//netscape comm. corp.//dtd strict html//",
        "-//o'reilly and associates//dtd html 2.0//",
        "-//o'reilly and associates//dtd html extended 1.0//",
        "-//o'reilly and associates//dtd html extended relaxed 1.0//",
        "-//sq//dtd html 2.0 hotmetal + extensions//",
        "-//softquad software//dtd hotmetal pro 6.0::19990601::extensions to html 4.0//",
        "-//softquad//dtd hotmetal pro 4.0::19971010::extensions to html 4.0//",
        "-//spyglass//dtd html 2.0 extended//",
        "-//sun microsystems corp.//dtd hotjava html//",
        "-//sun microsystems corp.//dtd hotjava strict html//",
        "-//w3c//dtd html 3 1995-03-24//",
        "-//w3c//dtd html 3.2 draft//",
        "-//w3c//dtd html 3.2 final//",
        "-//w3c//dtd html 3.2//",
        "-//w3c//dtd html 3.2s draft//",
        "-//w3c//dtd html 4.0 frameset//",
        "-//w3c//dtd html 4.0 transitional//",
        "-//w3c//dtd html experimental 19960712//",
        "-//w3c//dtd html experimental 970421//",
        "-//w3c//dtd w3 html//",
        "-//w3o//dtd w3 html 3.0//",
        "-//webtechs//dtd mozilla html 2.0//",
        "-//webtechs//dtd mozilla html//",
    )
}
