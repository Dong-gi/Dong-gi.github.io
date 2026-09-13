package io.github.donggi.iroiroviewer.io

import java.io.File

/**
 * 파일시스템에 **이름을 쓰는 모든 자리**가 지나야 하는 관문.
 *
 * 새 폴더·이름 바꾸기·아카이브 해제·휴지통 복원이 전부 여기를 지난다. 한 곳이라도
 * 빠지면 그 경로로만 이상한 이름이 들어오고, 그런 파일은 나중에 지우기도 어렵다.
 *
 * 길이를 **바이트로** 재는 것이 중요하다. ext4·F2FS 는 파일명을 255**바이트**로 제한하는데
 * 한글은 UTF-8 로 3바이트라 85자면 상한이다. 글자 수로 재면 한국어 이름에서만 실패한다.
 */
object PathRules {

    /** 대부분의 안드로이드 파일시스템이 받는 이름의 최대 바이트. */
    const val MAX_NAME_BYTES = 255

    /**
     * 파일시스템이나 사용자를 혼란스럽게 하는 글자.
     *
     * `/` 는 경로 구분자라 이름에 들어갈 수 없고, 나머지는 FAT/exFAT(SD 카드)가 거부한다.
     * 내부 저장소에서만 되는 이름을 허용하면 SD 로 옮기는 순간 실패한다.
     */
    private const val FORBIDDEN = "/\\:*?\"<>|"

    sealed interface Verdict {
        data class Ok(val name: String) : Verdict
        data class Rejected(val reason: Reason) : Verdict
    }

    enum class Reason {
        EMPTY,
        DOT_ONLY,
        FORBIDDEN_CHAR,
        CONTROL_CHAR,
        TOO_LONG,
        TRAILING_DOT_OR_SPACE,
    }

    /**
     * 사용자가 입력한 이름을 검사한다. **고쳐 주지 않는다** — 새 폴더나 이름 바꾸기에서
     * 사용자가 적은 것과 다른 이름이 만들어지면 그것대로 놀랍다.
     */
    fun validate(name: String): Verdict {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Verdict.Rejected(Reason.EMPTY)
        if (trimmed == "." || trimmed == "..") return Verdict.Rejected(Reason.DOT_ONLY)
        if (trimmed.any { it.code < 0x20 || it.code == 0x7F }) return Verdict.Rejected(Reason.CONTROL_CHAR)
        if (trimmed.any { it in FORBIDDEN }) return Verdict.Rejected(Reason.FORBIDDEN_CHAR)
        // 다듬은 뒤의 이름으로 판단한다. 다듬기 전 문자열을 보면 " 새 폴더 " 처럼
        // 앞뒤 공백만 있는 입력이 거부되는데, 그것은 다듬어 주면 되는 것이다.
        if (trimmed.endsWith('.')) return Verdict.Rejected(Reason.TRAILING_DOT_OR_SPACE)
        if (trimmed.toByteArray().size > MAX_NAME_BYTES) return Verdict.Rejected(Reason.TOO_LONG)
        return Verdict.Ok(trimmed)
    }

    /**
     * 우리가 만들어 내는 이름(아카이브 해제·복원)을 쓸 수 있게 다듬는다.
     *
     * 자를 때 **확장자를 살린다.** 앞에서부터 255바이트를 자르면 `.jpg` 가 날아가
     * 무슨 파일인지 알 수 없게 된다.
     */
    fun sanitize(name: String): String {
        var s = name.trim().ifEmpty { "이름없음" }
        s = s.map { c ->
            when {
                c.code < 0x20 || c.code == 0x7F -> '_'
                c in FORBIDDEN -> '_'
                else -> c
            }
        }.joinToString("")
        s = s.trimEnd('.', ' ').ifEmpty { "이름없음" }
        return truncateToBytes(s, MAX_NAME_BYTES)
    }

    /** UTF-8 바이트 수를 넘지 않게 자르되 확장자를 남긴다. */
    fun truncateToBytes(name: String, maxBytes: Int): String {
        if (name.toByteArray().size <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= 12) name.substring(dot) else ""
        val extBytes = ext.toByteArray().size
        val room = (maxBytes - extBytes).coerceAtLeast(1)
        val base = if (ext.isEmpty()) name else name.substring(0, dot)
        return (cutToBytes(base, room).ifEmpty { "이름없음" }) + ext
    }

    /**
     * UTF-8 바이트 상한까지만 남긴다. **글자 단위가 아니라 코드포인트 단위로 센다** —
     * `Char` 로 돌면 이모지(서러게이트 쌍)가 반으로 잘려 깨진 글자가 남는다.
     */
    private fun cutToBytes(s: String, maxBytes: Int): String {
        if (s.toByteArray().size <= maxBytes) return s
        val sb = StringBuilder()
        var used = 0
        var i = 0
        while (i < s.length) {
            val chars = Character.charCount(s.codePointAt(i))
            val piece = s.substring(i, i + chars)
            val size = piece.toByteArray().size
            if (used + size > maxBytes) break
            sb.append(piece)
            used += size
            i += chars
        }
        return sb.toString()
    }

    /**
     * 같은 이름이 이미 있을 때 쓸 다음 이름. `사진.jpg` → `사진 (2).jpg`.
     *
     * 확장자 앞에 번호를 넣는 것은 안드로이드·윈도우 파일 관리자의 공통 관행이고,
     * 뒤에 붙이면(`사진.jpg (2)`) 확장자가 바뀌어 버린다.
     *
     * **번호가 들어갈 자리를 먼저 비운다.** 다 만들고 나서 255바이트로 자르면 ` (2)` 가
     * 통째로 잘려 후보가 원본과 똑같아지고, 1만 번 헛돈 끝에 **이미 있는 이름을 돌려준다.**
     * 그것을 받은 쪽은 '둘 다 보관' 인 줄 알고 남의 파일을 덮어쓴다(복사는 `delete` 후
     * `rename`, 휴지통 복원은 `rename` 자체가 대상을 지운다). 이 함수의 계약은 하나다 —
     * **없는 이름만 돌려준다.**
     *
     * @throws java.io.IOException 쓸 수 있는 이름을 만들지 못했을 때. 있는 이름을
     *   돌려주느니 실패하는 편이 낫다.
     */
    fun nextAvailable(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val hasExt = dot > 0 && name.length - dot <= 12
        val ext = if (hasExt) name.substring(dot) else ""
        val room = (MAX_NAME_BYTES - ext.toByteArray().size - SUFFIX_BYTES).coerceAtLeast(1)
        val base = cutToBytes(if (hasExt) name.substring(0, dot) else name, room).ifEmpty { "이름없음" }

        for (n in 2 until 10_000) {
            val candidate = "$base ($n)$ext"
            if (!File(dir, candidate).exists()) return candidate
        }
        // 1만 번 부딪히면 번호로 풀 문제가 아니다. 그래도 있는 이름을 돌려주지는 않는다.
        repeat(8) {
            val candidate = "$base (${java.util.UUID.randomUUID().toString().take(8)})$ext"
            if (!File(dir, candidate).exists()) return candidate
        }
        throw java.io.IOException("사용할 수 있는 이름을 만들지 못했다")
    }

    /**
     * 이 파일의 썸네일을 디스크에 남기면 안 되는가.
     *
     * `.nomedia` 가 있는 폴더는 사용자가 "갤러리에 넣지 마라" 고 말한 곳이고, 휴지통은
     * 지운 것이다. 둘 다 축소본이 앱 저장소에 남으면 **지운 것이 지워지지 않은 셈**이 되고
     * 숨긴 것이 숨겨지지 않은 셈이 된다. 메모리에만 두고 앱을 끄면 사라지게 한다.
     */
    fun isNoMediaOrTrash(path: String): Boolean {
        if (TrashStore.isTrashPath(path)) return true
        val parent = File(path).parentFile ?: return false
        return File(parent, ".nomedia").exists()
    }

    /**
     * 번호를 위해 비워 두는 바이트. ` (9999)` 는 7바이트, ` (a1b2c3d4)` 는 11바이트다.
     * 넉넉히 잡아도 잃는 것은 이름 끝 몇 글자뿐이고, 모자라면 위 계약이 깨진다.
     */
    private const val SUFFIX_BYTES = 16
}
