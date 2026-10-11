package io.github.donggi.iroirobrowser.core

import java.nio.charset.Charset

/**
 * `Content-Disposition` 의 파일 이름(RFC 6266). 안드로이드 12 의 `URLUtil.guessFileName` 은 `filename*`(RFC 8187 — 한국어 이름이 대개
 * 여기 온다)을 모르므로 우리가 푼다.
 *
 * 순서: `filename*`(문자 집합과 퍼센트 부호화) > `filename`. `filename` 이 UTF-8 바이트를 ISO-8859-1 로 읽은 꼴(서버가 날 바이트를 보낸
 * 경우)이면 UTF-8 로 되돌리고, `%XX` 로 부호화된 꼴이면 푼다 — 둘 다 엄격한 UTF-8 로 풀릴 때만.
 */
object ContentDisposition {

    fun fileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val params = parameters(header)
        params["filename*"]?.let(::decodeExtValue)?.takeIf { it.isNotBlank() }?.let { return it }
        return params["filename"]?.let { repairLatin1(decodePercentIfUtf8(it)) }?.takeIf { it.isNotBlank() }
    }

    /** 매개변수 이름(소문자) → 값(따옴표를 푼 것). 첫 토막(inline/attachment)은 건너뛴다. 같은 이름이 거듭되면 앞의 것. */
    internal fun parameters(header: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = header.indexOf(';')
        if (i < 0) {
            // 'filename=…' 만 보내는 서버가 있다.
            if (!header.contains('=')) return out
            i = -1
        }
        while (i < header.length) {
            i++ // ';' 건너뛰기
            while (i < header.length && header[i].isWhitespace()) i++
            val eq = header.indexOf('=', i)
            if (eq < 0) break
            val name = header.substring(i, eq).trim().lowercase()
            var j = eq + 1
            while (j < header.length && header[j].isWhitespace()) j++
            val value: String
            if (j < header.length && header[j] == '"') {
                val sb = StringBuilder()
                j++
                while (j < header.length && header[j] != '"') {
                    if (header[j] == '\\' && j + 1 < header.length) j++
                    sb.append(header[j])
                    j++
                }
                value = sb.toString()
                j++ // 닫는 따옴표
                val semi = header.indexOf(';', j)
                i = if (semi < 0) header.length else semi
            } else {
                val semi = header.indexOf(';', j)
                value = header.substring(j, if (semi < 0) header.length else semi).trim()
                i = if (semi < 0) header.length else semi
            }
            if (name.isNotEmpty() && name !in out) out[name] = value
        }
        return out
    }

    /** RFC 8187: `charset'language'퍼센트부호화`. */
    internal fun decodeExtValue(value: String): String? {
        val first = value.indexOf('\'')
        if (first < 0) return null
        val second = value.indexOf('\'', first + 1)
        if (second < 0) return null
        val charset = try {
            Charset.forName(value.substring(0, first).trim().ifEmpty { "UTF-8" })
        } catch (e: IllegalArgumentException) {
            return null
        }
        val bytes = PercentCoding.bytes(value.substring(second + 1)) ?: return null
        return PercentCoding.strictDecode(bytes, charset)
    }

    private fun decodePercentIfUtf8(value: String): String {
        if (!PERCENT.containsMatchIn(value)) return value
        return PercentCoding.decodeUtf8(value) ?: value
    }

    /** UTF-8 바이트를 ISO-8859-1 로 읽어 깨진 글(예: `í•œêµ\u00AD`)이면 되돌린다. 0x80 이상이 없거나 UTF-8 이 아니면 그대로. */
    internal fun repairLatin1(value: String): String {
        if (value.none { it.code in 0x80..0xFF } || value.any { it.code > 0xFF }) return value
        return PercentCoding.strictDecode(value.toByteArray(Charsets.ISO_8859_1)) ?: value
    }

    private val PERCENT = Regex("%[0-9A-Fa-f]{2}")
}
