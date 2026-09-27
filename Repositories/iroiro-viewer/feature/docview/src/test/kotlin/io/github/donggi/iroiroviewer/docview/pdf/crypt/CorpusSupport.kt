package io.github.donggi.iroiroviewer.docview.pdf.crypt

import java.io.File

/**
 * 실세계 말뭉치(`samples-local/corpus/`)를 읽는 시험 도구.
 *
 * **말뭉치와 오라클은 커밋하지 않는다**(저장소 규칙 '표본'). 여기 있는 것은 그것을 읽는 방법뿐이다.
 * 오라클(`oracle/<ID>.json`)은 `samples-local/corpus/tools/` 의 `oracle_pdf.py` 가 **pikepdf(qpdf 12.3.2)·pypdf 6.17** 로
 * 만든다 — 우리 파서가 아니다. `format:epub` 의 시험에 같은 이름의 도구가 있는데, 모듈 사이에 시험 코드를
 * 나눌 길이 없어 한 벌 더 둔다(순수 JVM 모듈과 안드로이드 모듈이다).
 */
internal object Corpus {

    val dir: File? = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { File(it, "samples-local/corpus") }
        .firstOrNull { File(it, "manifest.json").isFile }

    fun manifest(): List<Map<String, Any?>> {
        val root = dir ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return Json.parse(File(root, "manifest.json").readText(Charsets.UTF_8)) as List<Map<String, Any?>>
    }

    fun oracle(id: String): Map<String, Any?>? {
        val f = File(dir ?: return null, "oracle/$id.json")
        if (!f.isFile) return null
        @Suppress("UNCHECKED_CAST")
        return Json.parse(f.readText(Charsets.UTF_8)) as Map<String, Any?>
    }

    fun outFile(name: String): File? = dir?.let { File(it, "out").apply { mkdirs() } }?.let { File(it, name) }
}

/** 최소한의 JSON 읽개. 시험 전용 — 의존을 늘리지 않으려고 둔다. */
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
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else error("JSON: 뜻밖의 글자 '$c' ($i)")
            }
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
                i++ // ':'
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
            i++ // '"'
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> b.append('\n')
                        't' -> b.append('\t')
                        'r' -> b.append('\r')
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000C')
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> b.append(e)
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
