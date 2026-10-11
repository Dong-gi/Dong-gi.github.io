package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import java.nio.charset.Charset
import java.util.Locale

/**
 * HTML 미리보기가 `<style>` 의 CSS 를 위생기에 넘기기 전에 고치는 것 둘. **주소는 여기서 읽지 않는다** — 위생기와 한 벌인
 * `format:html` 의 `CssRefs` 가 읽는다(예전에는 여기 따로 한 벌이 있었다. 둘이면 한쪽만 고쳐진다).
 */
internal object CssUrls {

    /** `<style>` 의 CDATA 껍데기(`<![CDATA[`·`]]>`). XHTML 은 XML 이라 그 안이 그대로 CSS 다 — HTML 로 읽으면 첫 규칙이 깨진다. */
    fun withoutCdata(css: String): String =
        if ("<![CDATA[" !in css && "]]>" !in css) css else css.replace("<![CDATA[", "").replace("]]>", "")

    /**
     * 중괄호의 짝을 맞춘다 — `@media print{…}` 로 쌀 CSS 를 위해. 짝 없는 `}` 는 버리고 닫히지 않은 `{` 는 끝에서 닫는다.
     *
     * 그대로 싸면 짝 없는 `}` 하나가 우리 `@media` 를 먼저 닫아 **그 뒤의 인쇄용 규칙이 화면에 걸린다** — `a{…}} body{display:none}`
     * 인 인쇄용 시트가 쪽 전체를 숨겼다(브라우저는 그 시트 안에서 그 규칙을 버린다). 문자열·주석·이스케이프 안의
     * 괄호는 세지 않는다. 한 번 앞으로만 읽는다.
     */
    fun balanced(css: String): String {
        if ('{' !in css && '}' !in css) return css
        val out = StringBuilder(css.length + 8)
        var depth = 0
        var i = 0
        val n = css.length
        while (i < n) {
            val c = css[i]
            when {
                c == '/' && i + 1 < n && css[i + 1] == '*' -> {
                    val close = css.indexOf("*/", i + 2)
                    val end = if (close < 0) n else close + 2
                    out.append(css, i, end)
                    i = end
                }
                c == '"' || c == '\'' -> {
                    val end = stringEnd(css, i)
                    out.append(css, i, end)
                    i = end
                }
                c == '\\' -> {
                    out.append(css, i, minOf(i + 2, n))
                    i += 2
                }
                c == '{' -> {
                    depth++
                    out.append(c)
                    i++
                }
                c == '}' -> {
                    if (depth > 0) {
                        depth--
                        out.append(c)
                    }
                    i++
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        repeat(depth) { out.append('}') }
        return out.toString()
    }

    /** 문자열의 끝. 줄바꿈에서 끝난다(망가진 문자열은 그 앞까지다 — CSS 명세). */
    private fun stringEnd(css: String, open: Int): Int {
        val quote = css[open]
        var i = open + 1
        while (i < css.length) {
            val c = css[i]
            when {
                c == quote -> return i + 1
                c == '\n' || c == '\r' || c == '\u000C' -> return i
                c == '\\' -> i += 2
                else -> i++
            }
        }
        return css.length
    }
}

/**
 * 스타일시트 **파일**을 글로 읽는다. 차례는 CSS 명세의 것이다 — BOM, 맨 앞의 `@charset "…";`, 그리고 그 파일을 건 문서의
 * 인코딩(환경 인코딩). 결과는 UTF-8 로 내준다(`WebResource` 의 `utf-8` 이 `@charset` 보다 먼저다). `@charset` 규칙은 지운다 —
 * 이제 거짓말이다.
 */
internal object CssText {

    fun decode(bytes: ByteArray, fallback: Charset): String {
        val text = when {
            startsWith(bytes, 0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            startsWith(bytes, 0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            startsWith(bytes, 0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            else -> String(bytes, charsetRule(bytes) ?: fallback)
        }
        return withoutCharsetRule(text)
    }

    /**
     * 문서의 인코딩을 스타일시트의 기본값으로. UTF-16 은 UTF-8 로 바꾼다 — ASCII 와 맞지 않는 인코딩은 환경 인코딩이 될 수 없다
     * (HTML 명세가 그렇게 바꾼다). 모르면 UTF-8.
     */
    fun fallbackOf(encoding: TextEncoding?): Charset = when (encoding) {
        null, TextEncoding.UTF_16LE, TextEncoding.UTF_16BE -> Charsets.UTF_8
        else -> encoding.charset ?: Charsets.UTF_8
    }

    /** 바이트 맨 앞의 `@charset "이름";`(명세는 이 모양 그대로만 받는다). 모르는 이름이면 null. */
    private fun charsetRule(bytes: ByteArray): Charset? {
        val head = "@charset \""
        if (bytes.size < head.length + 2) return null
        for (k in head.indices) if (bytes[k] != head[k].code.toByte()) return null
        val start = head.length
        var end = start
        while (end < bytes.size && end - start < 64 && bytes[end] != '"'.code.toByte()) end++
        if (end + 1 >= bytes.size || bytes[end] != '"'.code.toByte() || bytes[end + 1] != ';'.code.toByte()) return null
        return charsetNamed(String(bytes, start, end - start, Charsets.ISO_8859_1))
    }

    /**
     * 이름 → 기기의 인코딩. 한국어·일본어·중국어 이름은 [TextEncoding] 에서 찾는다 — 안드로이드는 CP949 를 `EUC-KR` 이라는 이름으로
     * 준다(`TextEncoding.CP949` 의 주석). UTF-16 이라고 적었으면 UTF-8 이다(명세 — ASCII 로 쓴 `@charset` 이 UTF-16 일 수 없다).
     */
    private fun charsetNamed(name: String): Charset? {
        val key = name.trim().lowercase(Locale.ROOT)
        return when (key) {
            "utf-8", "utf8", "unicode-1-1-utf-8", "utf-16", "utf-16le", "utf-16be" -> Charsets.UTF_8
            "euc-kr", "cp949", "windows-949", "x-windows-949", "ks_c_5601-1987", "ksc5601", "korean" -> TextEncoding.CP949.charset
            "shift_jis", "shift-jis", "sjis", "x-sjis", "windows-31j", "ms932", "ms_kanji" -> TextEncoding.SHIFT_JIS.charset
            "gb18030", "gbk", "gb2312", "x-gbk" -> TextEncoding.GB18030.charset
            "big5", "big5-hkscs" -> TextEncoding.BIG5.charset
            // WHATWG 는 라틴 1 을 windows-1252 로 읽는다.
            "iso-8859-1", "latin1", "us-ascii", "ascii", "windows-1252" -> windows1252()
            else -> try {
                if (Charset.isSupported(key)) Charset.forName(key) else null
            } catch (e: IllegalArgumentException) {
                // 문서가 적은 이름이라 이름 꼴이 아닐 수 있다(`IllegalCharsetNameException`).
                null
            }
        }
    }

    private fun windows1252(): Charset = try {
        Charset.forName("windows-1252")
    } catch (e: IllegalArgumentException) {
        Charsets.ISO_8859_1
    }

    /** 맨 앞의 `@charset …;` 규칙을 지운다. */
    fun withoutCharsetRule(css: String): String {
        if (!css.startsWith("@charset", ignoreCase = true)) return css
        val semi = css.indexOf(';')
        return if (semi in 0 until 128) css.substring(semi + 1) else css
    }

    private fun startsWith(bytes: ByteArray, vararg b: Int): Boolean =
        bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
}
