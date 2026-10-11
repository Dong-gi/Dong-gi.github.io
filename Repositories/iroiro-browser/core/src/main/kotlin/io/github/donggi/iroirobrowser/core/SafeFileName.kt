package io.github.donggi.iroirobrowser.core

/**
 * 받은 파일의 이름을 저장해도 되는 꼴로. 경로 구분자·제어 문자·FAT 이 받지 않는 글자는 `_` 로, **방향 제어 문자**(U+202E 등 —
 * `txt.exe` 를 `exe.txt` 로 보이게 하는 속임수)도 `_` 로 바꾼다. 앞뒤의 점·빈칸을 떼어 숨은 파일이 되지 않게 하고, 너무 길면 확장자를
 * 남기고 줄인다(UTF-8 바이트로 잰다 — 파일 시스템의 한계가 바이트다).
 */
object SafeFileName {

    /** ext4 의 255 바이트에서 ` (12)` 같은 겹침 꼬리에 자리를 남긴다. */
    const val MAX_BYTES = 200
    const val FALLBACK = "download"

    fun clean(name: String): String {
        val replaced = buildString(name.length) {
            for (c in name) {
                append(
                    when {
                        c.code < 0x20 || c.code == 0x7F -> '_'
                        c in RESERVED -> '_'
                        c.code in BIDI_CONTROLS -> '_'
                        else -> c
                    },
                )
            }
        }
        val trimmed = replaced.trim().trim('.', ' ').trim()
        if (trimmed.isEmpty() || trimmed.all { it == '_' }) return FALLBACK
        return limitBytes(trimmed)
    }

    private fun limitBytes(name: String): String {
        if (name.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= 16) name.substring(dot) else ""
        val base = name.substring(0, name.length - ext.length)
        val budget = MAX_BYTES - ext.toByteArray(Charsets.UTF_8).size
        val sb = StringBuilder()
        var used = 0
        var i = 0
        while (i < base.length) {
            val cp = base.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val n = chunk.toByteArray(Charsets.UTF_8).size
            if (used + n > budget) break
            sb.append(chunk)
            used += n
            i += Character.charCount(cp)
        }
        return sb.toString().trimEnd('.', ' ') + ext
    }

    private const val RESERVED = "/\\:*?\"<>|"
    private val BIDI_CONTROLS = setOf(0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069)
}
