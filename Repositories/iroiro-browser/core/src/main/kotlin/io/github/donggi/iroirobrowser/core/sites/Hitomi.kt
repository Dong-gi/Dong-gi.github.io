package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.SafeFileName
import java.util.Locale

/**
 * hitomi.la — 갤러리 정보(`galleries/{id}.js`)와 그림 주소의 공식(`gg.js`). 데스크톱(iroiro-downloader)에서 옮겼고 2026-10-11 에 사이트의
 * 스크립트와 실제 답으로 다시 확인했다(아래 [chooseExt]·[imageUrl]).
 *
 * 주소: `https://<서브도메인>.gold-usergeneratedcontent.net/{b}{g}/{hash}.{ext}` — `g = int(hash 의 끝 1자 + 그 앞 2자, 16)`, `b` 는 gg.js 의
 * 경로 머리(`'1791691201/'` 꼴), 서브도메인은 **확장자의 머리 글자 + (1 + m(g))** — avif 면 `a1`·`a2`, webp 면 `w1`·`w2`.
 */
internal object Hitomi {

    private const val LTN = "https://ltn.gold-usergeneratedcontent.net"
    private const val DOMAIN = "gold-usergeneratedcontent.net"
    const val GG_URL = "$LTN/gg.js"

    /** 갤러리 폴더 이름(`{id}_{제목}`)의 코드 포인트 상한 — 데스크톱과 같은 80(윈도의 MAX_PATH 를 위해 정한 값이었다). 자른 뒤 다듬는다. */
    private const val FOLDER_TITLE_CODE_POINTS = 80

    /** 계획에 싣는 확장자 — 서브도메인의 머리 글자가 이것으로 정해진다. */
    val EXTENSIONS = setOf("avif", "webp")

    fun galleryUrl(id: Long) = "$LTN/galleries/$id.js"

    /** 그림·정보 요청의 Referer — 그 갤러리를 읽는 쪽의 주소([SiteTarget.HitomiGallery.canonicalUrl] 와 같다). */
    fun readerUrl(id: Long) = "https://hitomi.la/reader/$id.html"

    class Gallery(val title: String, val creator: String?, val folder: List<String>, val files: List<File>)

    class File(val hash: String, val ext: String, val name: String)

    /**
     * `var galleryinfo = {…}` — 머리를 공백에 너그럽게 떼고(`var`·`let`·`const`), 값 하나를 JSON 으로 읽은 뒤 끝에는 공백과 `;` 하나만 허락한다
     * (실측 2026-10-11: 끝에 `;` 가 없었다). 머리 없이 `{` 로 시작해도 받는다.
     */
    fun parseGallery(bytes: ByteArray, id: Long): Gallery {
        val what = "gallery $id"
        val text = SiteResponses.text(bytes)
        if (SiteResponses.looksLikeHtml(text)) throw SiteException(SiteException.Reason.BLOCKED, "$what: JS 대신 HTML")
        var i = SiteResponses.firstNonSpace(text)
        if (!text.startsWith("{", i)) {
            i = skipWord(text, i, setOf("var", "let", "const")) ?: SiteResponses.unexpected("$what: 머리가 다르다")
            i = skipWord(text, SiteResponses.firstNonSpace(text, i), setOf("galleryinfo")) ?: SiteResponses.unexpected("$what: 머리가 다르다")
            i = SiteResponses.firstNonSpace(text, i)
            if (!text.startsWith("=", i)) SiteResponses.unexpected("$what: '=' 가 없다")
            i++
        }
        val (value, end) = try {
            Json.parsePrefix(text, i, Json.SITE)
        } catch (e: JsonException) {
            throw SiteResponses.jsonFailure(e, what)
        }
        var j = SiteResponses.firstNonSpace(text, end)
        if (text.startsWith(";", j)) j = SiteResponses.firstNonSpace(text, j + 1)
        if (j != text.length) SiteResponses.unexpected("$what: 값 뒤에 글이 남았다")
        val info = value as? Map<*, *> ?: SiteResponses.unexpected("$what: 객체가 아니다")
        return gallery(info, id, what)
    }

    private fun skipWord(text: String, at: Int, words: Set<String>): Int? {
        var end = at
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '$')) end++
        return if (end > at && text.substring(at, end) in words) end else null
    }

    private fun gallery(info: Map<*, *>, id: Long, what: String): Gallery {
        val shownId = info["id"]
        if (shownId != null && SiteResponses.integer(shownId) != id) SiteResponses.unexpected("$what: 다른 갤러리의 답")
        val title = SiteNames.display(info["title"] as? String) ?: SiteNames.display(info["japanese_title"] as? String) ?: id.toString()
        val label = names(info["artists"], "artist") ?: names(info["groups"], "group")
        val files: List<*> = when (val raw = info["files"]) {
            null -> emptyList<Any?>()
            is List<*> -> raw
            else -> SiteResponses.unexpected("$what: files 의 꼴")
        }
        val parsed = files.mapIndexed { index, f ->
            val file = f as? Map<*, *> ?: SiteResponses.unexpected("$what: 파일 $index 이 객체가 아니다")
            val hash = (file["hash"] as? String)?.lowercase(Locale.ROOT)
            if (hash == null || !isHash(hash)) SiteResponses.unexpected("$what: 파일 $index 의 hash")
            val name = file["name"] as? String
            val ext = chooseExt(file, name)
            File(hash, ext, "${stem(name, index)}.$ext")
        }
        val names = SiteNames.unique(parsed.map { SiteNames.clean(it.name) })
        val folder = listOf(
            Site.HITOMI.folder,
            SiteNames.clean(label ?: "unknown"),
            // 자른 뒤 다듬는다 — 자른 끝의 빈칸·점까지 떼려고(데스크톱과 같은 차례).
            SiteNames.clean(SiteNames.takeCodePoints(SiteNames.prepare("${id}_$title"), FOLDER_TITLE_CODE_POINTS)),
        )
        return Gallery(
            title = title,
            creator = label?.let { SiteNames.display(it) },
            folder = folder,
            files = parsed.mapIndexed { index, file -> File(file.hash, file.ext, names[index]) },
        )
    }

    /** 작가들을 `, ` 로 — 없거나 비면 null(그러면 그룹, 그것도 없으면 폴더 이름은 `unknown`). 데스크톱과 같다. */
    private fun names(value: Any?, key: String): String? {
        val list = value as? List<*> ?: return null
        val names = list.mapNotNull { ((it as? Map<*, *>)?.get(key) as? String)?.takeIf { name -> name.isNotBlank() } }
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** 이름의 마지막 성분에서 확장자를 뗀 것을 다듬어서. 이름이 없거나 다듬으면 비는 것이면 `001` 꼴 순번(데스크톱과 같다). */
    private fun stem(name: String?, index: Int): String {
        val number = (index + 1).toString().padStart(3, '0')
        if (name.isNullOrEmpty()) return number
        val base = name.substring(maxOf(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val cleaned = SiteNames.clean(stem)
        return if (cleaned == SafeFileName.FALLBACK && stem.trim() != cleaned) number else cleaned
    }

    /**
     * 확장자 — 사이트의 읽개(reader.js, 2025-03-20 판)는 `hasavif` 면 avif 를, 아니면 **늘 webp** 를 싣는다. 2026-10-11 의 갤러리 정보에는
     * `haswebp`·`hasjxl` 키가 아예 없었고(`hasavif:1` 만) 그래도 webp 가 `w` 서브도메인에서 206 으로 나왔다. 그래서 과제가 적은 차례
     * (`hasavif` → `haswebp` → `hasjxl` → 원래 확장자)의 뒤 둘은 쓰지 않는다 — jxl 은 사이트가 싣지 않고 안드로이드 12 가 못 열며, 원래
     * 확장자(jpg)는 CDN 이 주지 않는다(데스크톱 문서). **원래 gif 는 webp** 로 — 안드로이드 12 는 움직이는 WebP 를 틀지만(API 28+) 움직이는
     * AVIF 는 확인하지 못했다(그 갤러리의 gif 에도 `hasavif:1` 이 있었다).
     */
    fun chooseExt(file: Map<*, *>, name: String?): String {
        val original = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (original == "gif") return "webp"
        return if (SiteResponses.truthy(file["hasavif"])) "avif" else "webp"
    }

    fun isHash(hash: String): Boolean = hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }

    /** `g` — 해시의 끝 1자와 그 앞 2자를 이어 16진으로(gg.js 의 `s`, common.js 의 `subdomain_from_url` 과 같다). 0..4095. */
    fun g(hash: String): Int {
        val n = hash.length
        return (hex(hash[n - 1]) shl 8) or (hex(hash[n - 3]) shl 4) or hex(hash[n - 2])
    }

    private fun hex(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        else -> throw IllegalArgumentException("16진이 아니다")
    }

    /**
     * 그림 주소. 서브도메인 규칙은 2026-10-11 실측으로 정했다 — 같은 그림을 `Range: bytes=0-0` 으로 물어 avif 는 `a(1+m)` 이 206, webp 는
     * `w(1+m)` 이 206 이고 `a(1+m)` 은 404(데스크톱처럼 늘 `a` 를 쓰면 webp 가 하나도 안 받힌다), avif 를 다른 m 의 `a` 로 물으면 404(m 이
     * 뜻이 있다). 사이트의 common.js(`subdomain_from_url`)도 webp → `w`, avif → `a` 다. gallery-dl 1.32 의 '확장자 머리 글자 + (1+m)' 과 같다.
     */
    fun imageUrl(gg: Gg, hash: String, ext: String): String {
        require(isHash(hash) && ext in EXTENSIONS) { "해시·확장자" }
        val g = g(hash)
        return "https://${ext[0]}${1 + gg.m(g)}.$DOMAIN/${gg.b}$g/$hash.$ext"
    }

    /** gg.js 에서 읽은 공식. [b] 는 경로 머리(끝의 `/` 까지), [m] 은 서브도메인 번호의 0 또는 1(지금은 기본 1, 묶음 하나가 0). */
    class Gg(val b: String, private val fallback: Int, private val cases: Map<Int, Int>) {
        fun m(g: Int): Int = cases[g] ?: fallback

        /** 시험용 — 기본값과 다른 값을 갖는 case 의 수. */
        internal val caseCount: Int get() = cases.size
    }

    /**
     * gg.js 를 읽는다 — 데스크톱은 정규식 셋(`b: '…'`·`var o = N;`·case 묶음)으로 파일 어디서든 찾았다. 여기서는 작은 토큰 읽개로
     * **`m: function(g) {…}` 의 몸만** 문법대로 읽는다: `var o = N; switch (g) { (case N:)+ o = X; break; … [default: o = X; break;] } return o;`.
     * 공백·줄바꿈·주석·이름(`g`·`o`)·속성의 차례는 상관없고 `}` 바로 앞의 `;` 는 없어도 된다(줄인 스크립트). 그 밖의 꼴(조건문·다른 변수·
     * break 없이 이어지는 case)이면 UNEXPECTED — 틀린 주소를 조용히 짓는 것보다 '사이트가 바뀌었다' 가 낫다.
     * `s` 함수도 우리가 아는 공식(`/(..)(.)$/` 의 `[2]+[1]` 을 16진으로)인지 토큰으로 견준다 — 데스크톱은 보지 않았다. `b` 는 숫자와 끝의 `/`.
     * 실측(2026-10-11, 22,845 바이트): `var o = 1;`, case 2,112 개가 한 묶음으로 `o = 0; break;`, `b: '1791691201/'`.
     */
    fun parseGg(bytes: ByteArray): Gg {
        val text = SiteResponses.text(bytes)
        if (SiteResponses.looksLikeHtml(text)) throw SiteException(SiteException.Reason.BLOCKED, "gg.js: JS 대신 HTML")
        val tokens = try {
            JsTokens.read(text)
        } catch (e: IllegalArgumentException) {
            SiteResponses.unexpected("gg.js: ${e.message}")
        }
        return GgParser(tokens).parse()
    }
}

/** 작은 JS 토큰 — 이름(`$`·`_` 포함), 10진 정수, 글(따옴표 안 — 이스케이프는 푼다), 그 밖의 한 글자. 주석은 건너뛴다. */
internal class JsToken(val kind: Kind, val text: String) {
    enum class Kind { NAME, NUMBER, STRING, PUNCT }

    override fun toString(): String = "$kind($text)"
}

internal object JsTokens {

    /**
     * 글자 수 상한 — 실측 gg.js 는 22,845 바이트(case 2,112 개). g 는 0..4095 라 case 를 모두 적어도 50 KB 남짓이다. 덩이 상한(8 MiB)까지
     * 토큰으로 펴면 휴대폰의 힙을 크게 쓴다 — 그보다 훨씬 크면 꼴이 바뀐 것이다.
     */
    private const val MAX_CHARS = 1 shl 20

    /** 토큰 수 상한 — 실측은 약 6,400 개. */
    private const val MAX_TOKENS = 200_000

    /** 한 글자 구두점의 글 — 토큰마다 새로 만들지 않는다. */
    private val PUNCT = Array(128) { it.toChar().toString() }

    fun read(text: String): List<JsToken> {
        require(text.length <= MAX_CHARS) { "너무 크다(${text.length}자)" }
        val out = ArrayList<JsToken>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2)
                    require(end >= 0) { "주석이 닫히지 않았다" }
                    i = end + 2
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) i++
                    out += JsToken(JsToken.Kind.NAME, text.substring(start, i))
                }
                c in '0'..'9' -> {
                    val start = i
                    while (i < text.length && text[i] in '0'..'9') i++
                    require(i - start <= 18) { "숫자가 너무 길다" }
                    out += JsToken(JsToken.Kind.NUMBER, text.substring(start, i))
                }
                // 글 — gg.js 의 글('use strict' 와 b)에는 이스케이프가 없다. 이스케이프를 반쯤 풀어 다른 값을 짓느니 받지 않는다.
                c == '\'' || c == '"' -> {
                    val start = ++i
                    while (true) {
                        require(i < text.length) { "글이 닫히지 않았다" }
                        val d = text[i]
                        if (d == c) break
                        require(d != '\n' && d != '\r' && d != '\\') { "글 안의 줄바꿈·이스케이프" }
                        i++
                    }
                    out += JsToken(JsToken.Kind.STRING, text.substring(start, i))
                    i++
                }
                else -> {
                    out += JsToken(JsToken.Kind.PUNCT, if (c.code < 128) PUNCT[c.code] else c.toString())
                    i++
                }
            }
            require(out.size <= MAX_TOKENS) { "토큰이 너무 많다" }
        }
        return out
    }
}

/** gg.js 의 세 속성(m·s·b)을 토큰에서 찾아 읽는다. 각각 꼭 한 번 나와야 한다. */
private class GgParser(private val t: List<JsToken>) {
    private var p = 0

    fun parse(): Hitomi.Gg {
        val mAt = property("m", function = true)
        val sAt = property("s", function = true)
        val bAt = property("b", function = false)
        val b = t[bAt].text
        if (!B_PATTERN.matches(b)) SiteResponses.unexpected("gg.js: b 의 꼴")
        checkS(sAt)
        p = mAt
        return readM(b)
    }

    /** `name : function` 이나 `name : '…'` 의 자리(function 이면 `function` 다음, 글이면 그 글). 없거나 둘이면 UNEXPECTED. */
    private fun property(name: String, function: Boolean): Int {
        var found = -1
        for (i in 0 until t.size - 2) {
            if (!isName(i, name) || !isPunct(i + 1, ":")) continue
            if (i > 0 && isPunct(i - 1, ".")) continue
            val hit = if (function) {
                if (isName(i + 2, "function")) i + 3 else -1
            } else {
                if (t[i + 2].kind == JsToken.Kind.STRING) i + 2 else -1
            }
            if (hit < 0) continue
            if (found >= 0) SiteResponses.unexpected("gg.js: $name 이 둘")
            found = hit
        }
        if (found < 0) SiteResponses.unexpected("gg.js: $name 이 없다")
        return found
    }

    /** `( 인자 ) {` 를 읽고 인자 이름을. */
    private fun parameter(): String {
        expectPunct("(")
        val param = expectName()
        expectPunct(")")
        expectPunct("{")
        return param
    }

    private fun readM(b: String): Hitomi.Gg {
        val param = parameter()
        val declare = expectName()
        if (declare != "var" && declare != "let") fail("m 의 첫 문장")
        val variable = expectName()
        expectPunct("=")
        var fallback = expectSmall()
        expectPunct(";")
        expectWord("switch")
        expectPunct("(")
        if (expectName() != param) fail("switch 의 대상")
        expectPunct(")")
        expectPunct("{")
        val cases = HashMap<Int, Int>()
        var sawDefault = false
        while (!peekPunct("}")) {
            val labels = ArrayList<Int>()
            var isDefault = false
            while (true) {
                when {
                    peekWord("case") -> {
                        p++
                        labels += expectCaseLabel()
                        expectPunct(":")
                    }
                    peekWord("default") -> {
                        p++
                        expectPunct(":")
                        isDefault = true
                    }
                    else -> break
                }
            }
            if (labels.isEmpty() && !isDefault) fail("case 가 없는 묶음")
            if (expectName() != variable) fail("묶음의 대입")
            expectPunct("=")
            val value = expectSmall()
            endStatement()
            // 묶음은 break 로 끝나야 한다 — 이어지는 case(fallthrough)를 우리 표가 나타내지 못한다. 마지막 묶음만 break 없이 닫혀도 같다.
            if (peekWord("break")) {
                p++
                endStatement()
            } else if (!peekPunct("}")) {
                fail("break 가 없는 묶음")
            }
            // switch 는 처음 맞는 case 를 고른다 — 같은 번호가 둘이면 앞의 것.
            for (label in labels) cases.putIfAbsent(label, value)
            if (isDefault) {
                if (sawDefault) fail("default 가 둘")
                sawDefault = true
                fallback = value
            }
        }
        expectPunct("}")
        if (peekPunct(";")) p++
        expectWord("return")
        if (expectName() != variable) fail("return 의 대상")
        endStatement()
        expectPunct("}")
        if (cases.size > MAX_CASES) fail("case 가 너무 많다")
        val distinct = cases.filterValues { it != fallback }
        return Hitomi.Gg(b, fallback, distinct)
    }

    /**
     * `s: function(h) { var m = /(..)(.)$/.exec(h); return parseInt(m[2]+m[1], 16).toString(10); }` — 이름은 무엇이든(같은 이름끼리 맞아야
     * 한다), 공백·`let`·`const` 는 상관없다. 다르면 우리 공식([Hitomi.g])이 사이트와 다를 수 있다.
     */
    private fun checkS(at: Int) {
        p = at
        val param = parameter()
        val decl = expectName()
        if (decl != "var" && decl != "let" && decl != "const") fail("s 의 첫 문장")
        val match = expectName()
        val pattern = listOf("=", "/", "(", ".", ".", ")", "(", ".", ")", "\$", "/", ".", "exec", "(", param, ")", ";",
            "return", "parseInt", "(", match, "[", "2", "]", "+", match, "[", "1", "]", ",", "16", ")", ".", "toString", "(", "10", ")")
        for (expected in pattern) {
            if (p >= t.size || t[p].text != expected) fail("s 함수가 아는 꼴이 아니다")
            p++
        }
        endStatement()
        expectPunct("}")
    }

    /** 문장의 끝 — `;`, 또는 `}` 바로 앞이면 생략해도 된다(줄인 스크립트가 흔히 뺀다). `}` 는 먹지 않는다. */
    private fun endStatement() {
        if (peekPunct(";")) {
            p++
            return
        }
        if (!peekPunct("}")) fail("';' 가 있어야 한다")
    }

    private fun isName(i: Int, name: String) = i < t.size && t[i].kind == JsToken.Kind.NAME && t[i].text == name
    private fun isPunct(i: Int, s: String) = i in t.indices && t[i].kind == JsToken.Kind.PUNCT && t[i].text == s
    private fun peekPunct(s: String) = isPunct(p, s)
    private fun peekWord(s: String) = isName(p, s)

    private fun expectPunct(s: String) {
        if (!isPunct(p, s)) fail("'$s' 가 있어야 한다")
        p++
    }

    private fun expectWord(s: String) {
        if (!isName(p, s)) fail("'$s' 가 있어야 한다")
        p++
    }

    private fun expectName(): String {
        if (p >= t.size || t[p].kind != JsToken.Kind.NAME) fail("이름이 있어야 한다")
        return t[p++].text
    }

    /** m 의 값 — 서브도메인 번호(1 + m)가 되므로 작은 음 아닌 정수만. */
    private fun expectSmall(): Int {
        if (p >= t.size || t[p].kind != JsToken.Kind.NUMBER || t[p].text.length > 2) fail("작은 정수가 있어야 한다")
        return t[p++].text.toInt()
    }

    /** case 의 번호 — g 는 0..4095(16진 세 자리)다. 그 밖이면 꼴이 바뀐 것이다. */
    private fun expectCaseLabel(): Int {
        if (p >= t.size || t[p].kind != JsToken.Kind.NUMBER || t[p].text.length > 4) fail("case 번호가 있어야 한다")
        val n = t[p++].text.toInt()
        if (n > 4095) fail("case 번호가 범위 밖")
        return n
    }

    private fun fail(what: String): Nothing = SiteResponses.unexpected("gg.js: $what(토큰 $p)")

    companion object {
        private val B_PATTERN = Regex("[0-9]{1,20}/")
        private const val MAX_CASES = 4096
    }
}
