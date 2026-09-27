package io.github.donggi.iroiroviewer.format.epub

import java.io.File

/**
 * 실세계 말뭉치(`samples-local/corpus/`)를 읽는 시험 도구.
 *
 * **말뭉치와 오라클은 커밋하지 않는다**(저장소 규칙 '표본'). 여기 있는 것은 그것을 읽는 방법뿐이다 —
 * `manifest.json`(파일마다 출처·라이선스·암호)과 `oracle/<ID>.json`(독립 도구가 뽑은 기대값).
 * 오라클은 `samples-local/corpus/tools/` 의 `oracle_epub.py`·`oracle_pdf.py` 가 zipfile·lxml·pikepdf·pypdf 로 만든다 —
 * 우리 파서가 아니다.
 */
internal object Corpus {

    /** 저장소 뿌리 쪽으로 올라가며 찾는다. 모듈 폴더에서 돌므로 두 단계 위에 있다. */
    val dir: File? = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { File(it, "samples-local/corpus") }
        .firstOrNull { File(it, "manifest.json").isFile }

    /** `manifest.json` 의 항목들. 말뭉치가 없으면 빈 목록. */
    fun manifest(): List<Map<String, Any?>> {
        val root = dir ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return Json.parse(File(root, "manifest.json").readText(Charsets.UTF_8)) as List<Map<String, Any?>>
    }

    /** 오라클 하나. 없으면 null — 그때는 오라클 대조만 건너뛰고 동작 시험은 돈다. */
    fun oracle(id: String): Map<String, Any?>? {
        val f = File(dir ?: return null, "oracle/$id.json")
        if (!f.isFile) return null
        @Suppress("UNCHECKED_CAST")
        return Json.parse(f.readText(Charsets.UTF_8)) as Map<String, Any?>
    }

    /** 시험이 남기는 결과(보고용). 말뭉치 곁에 두고 커밋하지 않는다. */
    fun outFile(name: String): File? = dir?.let { File(it, "out").apply { mkdirs() } }?.let { File(it, name) }
}

/**
 * 최소한의 JSON 읽개. **시험 전용이다** — 의존을 늘리지 않으려고 둔다(저장소 규칙).
 * 객체는 `Map`, 배열은 `List`, 수는 `Double`(정수면 `Long`), 그 밖은 `String`·`Boolean`·null.
 */
internal object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "JSON 끝에 남은 글자가 있다: ${p.i}" }
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            ws()
            require(i < s.length) { "JSON 이 끝났다" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("JSON: 뜻밖의 글자 '$c' ($i)")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "JSON: $word 가 아니다 ($i)" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') { i++; return out }
            while (true) {
                ws()
                val k = str()
                ws()
                require(s[i] == ':') { "JSON: ':' 가 없다 ($i)" }
                i++
                out[k] = value()
                ws()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> error("JSON: 객체가 닫히지 않았다 ($i)")
                }
            }
        }

        private fun arr(): List<Any?> {
            val out = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') { i++; return out }
            while (true) {
                out.add(value())
                ws()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> error("JSON: 배열이 닫히지 않았다 ($i)")
                }
            }
        }

        private fun str(): String {
            require(s[i] == '"') { "JSON: 문자열이 아니다 ($i)" }
            i++
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> b.append('\n')
                            't' -> b.append('\t')
                            'r' -> b.append('\r')
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'u' -> {
                                b.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return t.toLongOrNull() ?: t.toDouble()
        }
    }
}

/**
 * 글자 대조. **파이썬 오라클(`oracle_epub.py` 의 `tokens`)과 같은 규칙**이어야 한다 — 소문자로 바꾸고,
 * 한자·가나는 한 글자가 한 낱말, 그 밖은 글자·숫자·결합 부호가 이어진 덩어리가 한 낱말이다.
 * 한중일 글은 띄어 쓰지 않으므로 띄어쓰기로 자르면 장 하나가 낱말 몇 개가 된다.
 */
internal object CorpusText {

    private fun isCjk(cp: Int) = cp in 0x3040..0x30FF || cp in 0x3400..0x4DBF || cp in 0x4E00..0x9FFF ||
        cp in 0xF900..0xFAFF || cp in 0x20000..0x2FFFF

    private fun isWordChar(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }

    fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        val lower = text.lowercase()
        var i = 0
        while (i < lower.length) {
            val cp = lower.codePointAt(i)
            i += Character.charCount(cp)
            when {
                isCjk(cp) -> {
                    if (cur.isNotEmpty()) { out.add(cur.toString()); cur.setLength(0) }
                    out.add(String(Character.toChars(cp)))
                }
                isWordChar(cp) -> cur.appendCodePoint(cp)
                else -> if (cur.isNotEmpty()) { out.add(cur.toString()); cur.setLength(0) }
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    /** 낱말 주머니끼리의 재현율·정밀도. 둘 다 비면 (1, 1). */
    fun recallPrecision(ours: List<String>, oracle: List<String>): Pair<Double, Double> {
        if (ours.isEmpty() && oracle.isEmpty()) return 1.0 to 1.0
        val a = ours.groupingBy { it }.eachCount()
        val b = oracle.groupingBy { it }.eachCount()
        var common = 0
        for ((k, n) in a) common += minOf(n, b[k] ?: 0)
        val recall = if (oracle.isEmpty()) 1.0 else common.toDouble() / oracle.size
        val precision = if (ours.isEmpty()) 1.0 else common.toDouble() / ours.size
        return recall to precision
    }

    /**
     * 우리가 WebView 에 내주는 HTML 에서 **화면에 보일 글**만. `<head>`(제목·우리 스타일)·`<style>`·
     * `<script>`·주석을 빼고 태그를 지운 뒤 엔티티를 푼다. DOM 을 만들지 않는다(시험이라도 같은 규칙).
     */
    fun visibleText(html: String): String {
        var s = html
        s = Regex("(?is)<!--.*?-->").replace(s, " ")
        s = Regex("(?is)<head[\\s>].*?</head\\s*>").replace(s, " ")
        s = Regex("(?is)<(style|script)[\\s>].*?</\\1\\s*>").replace(s, " ")
        s = Regex("(?s)<[^>]*>").replace(s, " ")
        return decodeEntities(s)
    }

    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

    fun decodeEntities(s: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);").replace(s) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)
                ?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
            body.startsWith("#") -> body.substring(1).toIntOrNull()
                ?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
            else -> NAMED[body] ?: m.value
        }
    }
}
