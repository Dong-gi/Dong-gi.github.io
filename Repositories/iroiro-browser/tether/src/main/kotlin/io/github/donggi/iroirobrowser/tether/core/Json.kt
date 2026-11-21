package io.github.donggi.iroirobrowser.tether.core

/**
 * 파일 서버의 손으로 쓴 최소 JSON 쓰개. 범용 직렬화기를 들이지 않는다(의존을 늘리지 않으려고) — 값은 글·수·참거짓·null·목록·맵만. 다만
 * **글의 이스케이프는 정확해야 한다** — 파일 이름에 `"`·역슬래시·제어 문자가 들 수 있다. 읽기는 [JsonReader].
 */
object Json {

    fun error(message: String): String = obj("error" to message)

    fun folderMissing(): String =
        obj("error" to "공유 폴더가 정해지지 않았습니다. 폰의 Web 에서 메뉴 › 테더링 › ‘공유 폴더 고르기’ 로 고르세요.", "needsFolder" to true)

    /** 올리기의 답 — [name] 은 **실제로 둔 이름**(서버가 다듬었을 수 있다 — 화면이 이것을 쓴다), [trashed] 는 덮은 원래 파일을 휴지통으로 옮겼는가. */
    fun uploaded(name: String, bytes: Long, trashed: Boolean = false): String = obj("ok" to true, "name" to name, "bytes" to bytes, "trashed" to trashed)

    /**
     * 폴더 목록 — 폴더 먼저, 그다음 파일. 묶음마다 이름 차례(대소문자 무시). [truncated] 면 상한에서 잘랐다(화면이 알린다). 휴지통을 빼는 것은
     * 부르는 쪽이다.
     */
    fun listing(path: String, rootName: String?, children: List<SharedEntry>, truncated: Boolean = false): String {
        val sorted = children.sortedWith(
            compareByDescending<SharedEntry> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
        val entries = sorted.joinToString(",") { child ->
            """{"name":${quote(child.name)},"dir":${child.isDirectory},""" +
                """"size":${child.size},"modified":${child.modified}}"""
        }
        val tail = if (truncated) ""","truncated":true""" else ""
        return """{"path":${quote(path)},"root":${quote(rootName ?: "")},"entries":[$entries]$tail}"""
    }

    /** 이름·값 쌍으로 객체 하나. 값은 [value] 가 받는 꼴. */
    fun obj(vararg pairs: Pair<String, Any?>): String = value(linkedMapOf(*pairs))

    /** 글·수·참거짓·null·목록(`List`)·맵(`Map<String, *>`)을 JSON 으로. 다른 것은 받지 않는다(던진다 — 코드의 실수). */
    fun value(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(out: StringBuilder, v: Any?) {
        when (v) {
            null -> out.append("null")
            is String -> out.append(quote(v))
            is Boolean -> out.append(v)
            is Int, is Long -> out.append(v)
            is Double -> out.append(if (v.isFinite()) v.toString() else "null")
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, item) in v) {
                    if (!first) out.append(',')
                    first = false
                    out.append(quote(k as String)).append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                v.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("JSON 으로 쓰지 않는 값: ${v.javaClass.simpleName}")
        }
    }

    /** JSON 글 리터럴로 감싼다. 제어 문자는 `\uXXXX` 로(16진은 손으로 — `String.format` 은 기본 로케일을 탄다). */
    fun quote(text: String): String {
        val builder = StringBuilder(text.length + 2)
        builder.append('"')
        for (char in text) {
            when {
                char == '"' -> builder.append("\\\"")
                char == '\\' -> builder.append("\\\\")
                char == '\n' -> builder.append("\\n")
                char == '\r' -> builder.append("\\r")
                char == '\t' -> builder.append("\\t")
                char.code < 0x20 || char == '\u2028' || char == '\u2029' -> {
                    builder.append("\\u")
                    builder.append(HEX[(char.code shr 12) and 0x0F]).append(HEX[(char.code shr 8) and 0x0F])
                    builder.append(HEX[(char.code shr 4) and 0x0F]).append(HEX[char.code and 0x0F])
                }
                else -> builder.append(char)
            }
        }
        builder.append('"')
        return builder.toString()
    }

    private const val HEX = "0123456789abcdef"
}
