package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.format.html.CharRefs
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * HTML 미리보기가 문서의 주소 하나를 **어디를 가리키는지** 읽는 한 곳.
 *
 * 브라우저가 `file:` 로 연 쪽에서 하는 것을 따른다(WHATWG URL) — 앞뒤의 공백·제어 문자를 떼고 가운데의 탭·줄바꿈을 지우되
 * **가운데의 공백은 이름의 일부로 둔다**, 역슬래시는 슬래시다, `#` 뒤는 조각이고 `?` 뒤는 질의다, `%XX` 는 푼다.
 *
 * **`URLDecoder` 를 쓰지 않는다**(함정 표) — 그것은 폼 인코딩이라 `+` 를 공백으로 바꾼다. `a+b.png` 는 `a+b.png` 다.
 * 잘못된 `%`(뒤에 16진 숫자 둘이 오지 않는 것)는 글자 그대로 둔다 — `100%.png` 는 그런 이름일 수 있다.
 *
 * 폴더 위로 올라가는 주소(`../`)·절대 경로·스킴이 붙은 것은 [Target.Outside] 다. 미리보기는 **그 파일의 폴더와 그 아래**만 싣는다.
 * 여기는 글자만 본다 — 실제 파일(심볼릭 링크)은 화면 쪽이 디스크에서 다시 본다.
 */
internal object PreviewUrls {

    sealed interface Target {
        /** 같은 쪽 안의 자리(`#…`). 원문 그대로 둔다. */
        data class Fragment(val raw: String) : Target

        /** 이 쪽 자신(`?x`·`?x#y`). 경로가 비고 질의만 있다. */
        data class Self(val fragment: String?) : Target

        /** 폴더 안의 파일. [path] 는 정규화했고 퍼센트를 푼 상대 경로, [fragment] 는 `#` 뒤(없으면 null). */
        data class Local(val path: String, val fragment: String?) : Target

        /** `data:` 주소. [url] 은 앞뒤를 다듬은 원문이다. */
        data class Data(val url: String) : Target

        /** 싣지 않는 곳 — 바깥(스킴·`//`), 절대 경로, 폴더 위. [script] 는 `javascript:`·`vbscript:` 다. */
        data class Outside(val script: Boolean) : Target

        /** 비었다. */
        data object Empty : Target
    }

    /**
     * @param raw 문서가 적은 주소(속성 값이면 문자 참조를 푼 뒤의 것, CSS 면 이스케이프를 푼 뒤의 것).
     * @param baseDir 가리키는 쪽의 폴더(미리보는 파일의 폴더 기준). 문서 자신이면 `""`, 하위 폴더의 스타일시트면 `"page_files/"`.
     */
    fun resolve(raw: String, baseDir: String): Target {
        val url = clean(raw)
        if (url.isEmpty()) return Target.Empty
        if (url[0] == '#') return Target.Fragment(url)
        // 특수 스킴(https·file)에서 브라우저는 역슬래시를 슬래시로 읽는다 — 윈도에서 만든 쪽의 `images\a.png` 가 그렇다.
        val slashed = if ('\\' in url) url.replace('\\', '/') else url
        // `//host/x` 는 스킴만 생략한 바깥이고, `/x` 는 이 폴더가 아니라 디스크의 뿌리다. 둘 다 싣지 않는다.
        if (slashed[0] == '/') return Target.Outside(script = false)
        val scheme = schemeOf(slashed)
        if (scheme != null) {
            return when (scheme) {
                "data" -> Target.Data(url)
                "javascript", "vbscript" -> Target.Outside(script = true)
                else -> Target.Outside(script = false)
            }
        }
        val hash = slashed.indexOf('#')
        val fragment = if (hash >= 0) slashed.substring(hash + 1).ifEmpty { null } else null
        val beforeHash = if (hash >= 0) slashed.substring(0, hash) else slashed
        val query = beforeHash.indexOf('?')
        val pathPart = if (query >= 0) beforeHash.substring(0, query) else beforeHash
        if (pathPart.isEmpty()) return Target.Self(fragment)
        val decoded = percentDecode(pathPart) ?: return Target.Outside(script = false)
        val path = normalize(baseDir + decoded) ?: return Target.Outside(script = false)
        // 폴더 자신(`./`)은 파일이 아니다.
        if (path.isEmpty()) return Target.Outside(script = false)
        return Target.Local(path, fragment)
    }

    /** 앞뒤의 C0 제어 문자·공백을 떼고 가운데의 탭·줄바꿈을 지운다(URL 명세). 가운데의 공백은 남긴다. */
    private fun clean(raw: String): String {
        var start = 0
        var end = raw.length
        while (start < end && raw[start] <= ' ') start++
        while (end > start && raw[end - 1] <= ' ') end--
        val s = raw.substring(start, end)
        return if (s.any { it == '\t' || it == '\n' || it == '\r' }) s.filterNot { it == '\t' || it == '\n' || it == '\r' } else s
    }

    /** 스킴이 붙어 있으면 소문자 이름. RFC 3986 의 문법 그대로(영문으로 시작, 영문·숫자·`+-.`, 그리고 `:`). */
    private fun schemeOf(url: String): String? {
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        for (i in 0 until colon) {
            val c = url[i]
            val alpha = c in 'a'..'z' || c in 'A'..'Z'
            val ok = if (i == 0) alpha else alpha || c in '0'..'9' || c == '+' || c == '-' || c == '.'
            if (!ok) return null
        }
        return url.substring(0, colon).lowercase(Locale.ROOT)
    }

    /**
     * `%XX` 를 풀어 UTF-8 로 읽는다. 잘못된 `%` 는 글자 그대로 둔다. 푼 것이 UTF-8 이 아니면(옛 쪽의 `%B0%A1` 은 EUC-KR 이다)
     * 원문을 이름으로 본다. 제어 문자가 나오면 null — 파일 이름이 될 수 없고, `%00` 은 경로를 끊는 수법이다.
     */
    fun percentDecode(s: String): String? {
        val decoded = if ('%' !in s) s else decodeEscapes(s)
        if (decoded.any { it < ' ' || it == '\u007F' }) return null
        return decoded
    }

    private fun decodeEscapes(s: String): String {
        val bytes = percentDecodeBytes(s)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            s
        }
    }

    /** `%XX` 는 그 바이트로, 나머지 글자는 UTF-8 바이트로. 잘못된 `%` 는 `%` 한 바이트다. */
    fun percentDecodeBytes(s: String): ByteArray {
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val pct = s.indexOf('%', i)
            val runEnd = if (pct < 0) s.length else pct
            if (runEnd > i) {
                // `%` 는 ASCII 라 그 자리에서 자르면 서로게이트 쌍이 갈리지 않는다.
                val run = s.substring(i, runEnd).toByteArray(Charsets.UTF_8)
                out.write(run, 0, run.size)
            }
            if (pct < 0) break
            val hi = if (pct + 1 < s.length) hexValue(s[pct + 1]) else -1
            val lo = if (pct + 2 < s.length) hexValue(s[pct + 2]) else -1
            if (hi >= 0 && lo >= 0) {
                out.write(hi * 16 + lo)
                i = pct + 3
            } else {
                out.write('%'.code)
                i = pct + 1
            }
        }
        return out.toByteArray()
    }

    /** ASCII 16진 숫자만. `Character.digit` 은 전각 숫자도 받는다 — 브라우저는 받지 않는다. */
    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /** `.`·`..`·빈 조각을 푼다. 뿌리 위로 올라가면 null. */
    private fun normalize(path: String): String? {
        val parts = ArrayList<String>()
        for (seg in path.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> {
                    if (parts.isEmpty()) return null
                    parts.removeAt(parts.lastIndex)
                }
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    // ---- 문자 참조 -----------------------------------------------------------------

    /**
     * 속성 값의 문자 참조를 푼다 — 주소로 읽기 전에. `a&amp;b.png` 는 `a&b.png` 이고, `java&#115;cript:` 는 `javascript:` 다.
     * 표와 규칙은 위생기와 한 벌이다(`format:html` 의 `CharRefs`). 모르는 이름 참조가 남아도 주소에서는 상관없다 — 우리는 주소를
     * 번호로 바꾸거나 지우므로 그 글이 어디로도 가지 않는다. `style` 속성은 다르다(그때는 [CharRefs.decode] 를 직접 본다).
     */
    fun decodeEntities(s: String): String = CharRefs.decode(s).text

    // ---- srcset ----------------------------------------------------------------------

    /** `srcset` 의 후보 하나. 서술자가 없으면 1x 다. 폭 서술자(`640w`)가 있으면 [width] 가 선다. */
    class Candidate(val url: String, val density: Double, val width: Int?)

    /** 한 `srcset` 에서 읽는 후보의 상한. 실물은 열 개를 넘지 않는다. */
    private const val MAX_CANDIDATES = 64

    /**
     * HTML 명세의 `srcset` 분석을 줄인 것. 후보는 쉼표로 가르되 **주소 안의 쉼표**(`data:image/png;base64,…`)는 주소의 일부다 —
     * 주소는 공백까지 읽는다. 서술자가 틀린 후보는 버린다(명세와 같다).
     */
    fun parseSrcset(value: String): List<Candidate> {
        val out = ArrayList<Candidate>()
        val n = value.length
        var i = 0
        while (i < n && out.size < MAX_CANDIDATES) {
            while (i < n && (isSpace(value[i]) || value[i] == ',')) i++
            if (i >= n) break
            val start = i
            while (i < n && !isSpace(value[i])) i++
            var url = value.substring(start, i)
            var descriptors = ""
            if (url.endsWith(',')) {
                url = url.trimEnd(',')
            } else {
                val dStart = i
                var depth = 0
                while (i < n) {
                    val c = value[i]
                    if (c == '(') depth++
                    else if (c == ')' && depth > 0) depth--
                    else if (c == ',' && depth == 0) break
                    i++
                }
                descriptors = value.substring(dStart, i).trim()
                if (i < n) i++
            }
            if (url.isEmpty()) continue
            candidateOf(url, descriptors)?.let(out::add)
        }
        return out
    }

    private fun candidateOf(url: String, descriptors: String): Candidate? {
        var density: Double? = null
        var width: Int? = null
        for (d in descriptors.split(' ', '\t', '\n', '\r', '\u000C')) {
            if (d.isEmpty()) continue
            when (d.last()) {
                'x' -> density = d.dropLast(1).toDoubleOrNull()?.takeIf { it > 0 && density == null } ?: return null
                'w' -> width = d.dropLast(1).toIntOrNull()?.takeIf { it > 0 && width == null } ?: return null
                'h' -> Unit
                else -> return null
            }
        }
        if (density != null && width != null) return null
        return Candidate(url, density ?: 1.0, width)
    }

    /**
     * 고를 차례. 폰은 화면 폭이 400dp 남짓에 배율이 2~3 이라 **2x(폭이면 1,600) 이하에서 가장 큰 것**이 먼저고, 그보다 큰 것은
     * 작은 것부터다 — 디코딩은 렌더러가 하지만 메모리는 같은 기기의 것이다. 폭 서술자가 하나라도 있으면 폭으로 고른다.
     */
    fun preferred(candidates: List<Candidate>): List<Candidate> {
        if (candidates.isEmpty()) return candidates
        val byWidth = candidates.any { it.width != null }
        return if (byWidth) {
            val ws = candidates.filter { it.width != null }
            ws.filter { it.width!! <= MAX_PREFERRED_WIDTH }.sortedByDescending { it.width } +
                ws.filter { it.width!! > MAX_PREFERRED_WIDTH }.sortedBy { it.width }
        } else {
            candidates.filter { it.density <= MAX_PREFERRED_DENSITY }.sortedByDescending { it.density } +
                candidates.filter { it.density > MAX_PREFERRED_DENSITY }.sortedBy { it.density }
        }
    }

    private const val MAX_PREFERRED_DENSITY = 2.0
    private const val MAX_PREFERRED_WIDTH = 1600

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C'

    // ---- data: ------------------------------------------------------------------------

    /** 푼 `data:` 하나. [mediaType] 은 적힌 그대로(소문자, 매개변수를 뗀 것)다 — 내줄지는 [PreviewTypes.ofData] 가 정한다. */
    class Decoded(val mediaType: String, val bytes: ByteArray)

    /**
     * `data:[형식][;base64],자료` 를 푼다(WHATWG 의 data: 처리를 줄인 것). 자료는 퍼센트를 풀고, base64 면 공백을 무시하고 푼다.
     * 틀렸거나 [maxBytes] 를 넘으면 null.
     */
    fun decodeData(url: String, maxBytes: Long): Decoded? {
        if (!url.regionMatches(0, "data:", 0, 5, ignoreCase = true)) return null
        val comma = url.indexOf(',')
        if (comma < 0) return null
        // 자료가 상한보다 확실히 크면 풀지 않는다. 퍼센트(바이트당 세 글자)·base64(세 바이트당 네 글자)를 감안한 상계다.
        if (url.length - comma > maxBytes * 3 + 1024) return null
        val header = url.substring(5, comma)
        val params = header.split(';')
        val base64 = params.size > 1 && params.last().trim().equals("base64", ignoreCase = true)
        val type = params[0].trim().lowercase(Locale.ROOT).ifEmpty { "text/plain" }
        val raw = percentDecodeBytes(url.substring(comma + 1))
        val bytes = if (base64) base64(raw) ?: return null else raw
        if (bytes.size > maxBytes) return null
        return Decoded(type, bytes)
    }

    /** 너그러운 base64(명세의 forgiving-base64) — 공백을 무시하고, 끝의 `=` 는 없어도 된다. 알파벳 밖의 글자면 null. */
    private fun base64(raw: ByteArray): ByteArray? {
        val chars = ByteArray(raw.size)
        var len = 0
        for (b in raw) {
            val c = b.toInt()
            if (c == 0x20 || c == 0x09 || c == 0x0A || c == 0x0C || c == 0x0D) continue
            chars[len++] = b
        }
        if (len % 4 == 0 && len > 0 && chars[len - 1] == '='.code.toByte()) {
            len--
            if (chars[len - 1] == '='.code.toByte()) len--
        }
        if (len % 4 == 1) return null
        val out = ByteArray(len * 3 / 4)
        var acc = 0
        var bits = 0
        var o = 0
        for (k in 0 until len) {
            val v = base64Value(chars[k].toInt())
            if (v < 0) return null
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[o++] = (acc shr bits).toByte()
                acc = acc and ((1 shl bits) - 1)
            }
        }
        return if (o == out.size) out else out.copyOf(o)
    }

    private fun base64Value(c: Int): Int = when (c) {
        in 'A'.code..'Z'.code -> c - 'A'.code
        in 'a'.code..'z'.code -> c - 'a'.code + 26
        in '0'.code..'9'.code -> c - '0'.code + 52
        '+'.code -> 62
        '/'.code -> 63
        else -> -1
    }
}
