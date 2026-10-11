package io.github.donggi.iroirobrowser.tether.core

/**
 * 파일 서버의 손으로 쓴 최소 JSON. 내보내는 꼴이 넷으로 정해져 있어 범용 직렬화기가 필요 없다(의존을 늘리지 않으려고). 다만 **글의
 * 이스케이프는 정확해야 한다** — 파일 이름에 `"`·역슬래시·제어 문자가 들 수 있다.
 */
object Json {

    fun error(message: String): String = """{"error":${quote(message)}}"""

    fun folderMissing(): String =
        """{"error":${quote("공유 폴더가 정해지지 않았습니다. 폰의 Web 에서 메뉴 › 테더링 › ‘공유 폴더 고르기’ 로 고르세요.")},"needsFolder":true}"""

    fun uploaded(name: String, bytes: Long): String = """{"ok":true,"name":${quote(name)},"bytes":$bytes}"""

    /** 폴더 목록 — 폴더 먼저, 그다음 파일. 묶음마다 이름 차례(대소문자 무시). */
    fun listing(path: String, rootName: String?, children: List<SharedEntry>): String {
        val sorted = children.sortedWith(
            compareByDescending<SharedEntry> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
        val entries = sorted.joinToString(",") { child ->
            """{"name":${quote(child.name)},"dir":${child.isDirectory},""" +
                """"size":${child.size},"modified":${child.modified}}"""
        }
        return """{"path":${quote(path)},"root":${quote(rootName ?: "")},"entries":[$entries]}"""
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
                char.code < 0x20 -> {
                    builder.append("\\u00")
                    builder.append(HEX[char.code shr 4]).append(HEX[char.code and 0x0F])
                }
                else -> builder.append(char)
            }
        }
        builder.append('"')
        return builder.toString()
    }

    private const val HEX = "0123456789abcdef"
}
