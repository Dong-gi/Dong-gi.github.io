package io.github.donggi.iroiroviewer.editor

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * 가로챈 요청의 URL 경로를 **엔진 폴더 안의 상대 자리**로 푼다 — 엔진이 묶음 밖(다른 엔진·APK 의 다른 자산)을 읽지 못하게.
 *
 * - 빈 경로·`/` 는 첫 쪽(`entry`).
 * - 퍼센트 인코딩을 UTF-8 로 한 번 푼다(`+` 는 그대로 — 폼 인코딩이 아니다). 깨진 인코딩은 거절.
 * - 가운데의 빈 조각(`//`)은 접는다(ONLYOFFICE 가 그렇게 이어 붙인 주소를 청한다). 끝의 `/` 는 거절 — 폴더는 내주지 않는다.
 * - `.`·`..`, 역슬래시·NUL 이 든 조각은 거절.
 */
object AssetPaths {

    fun resolve(encodedPath: String?, entry: String): String? {
        if (encodedPath == null || encodedPath == "" || encodedPath == "/") return entry
        if (!encodedPath.startsWith("/")) return null
        val decoded = decodePercent(encodedPath.substring(1)) ?: return null
        val parts = decoded.split('/')
        if (parts.last().isEmpty()) return null // 끝의 `/` — 폴더는 내주지 않는다
        // 가운데의 빈 조각(`//`)은 접는다 — ONLYOFFICE 가 `themes/` + `/themes.js` 처럼 이어 붙인 주소를 청한다.
        val kept = parts.filter { it.isNotEmpty() }
        for (part in kept) {
            if (part == "." || part == "..") return null
            if (part.any { it == '\\' || it == '\u0000' }) return null
        }
        return kept.joinToString("/")
    }

    /** `%XX` 를 바이트로 모아 UTF-8 로 엄격하게 읽는다. 잘린 `%`·16진이 아닌 것·UTF-8 이 아닌 바이트는 null. */
    fun decodePercent(s: String): String? {
        if ('%' !in s) return s
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '%') {
                if (i + 2 >= s.length) return null
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi < 0 || lo < 0) return null
                out.write(hi * 16 + lo)
                i += 3
            } else {
                // 퍼센트가 아닌 글자는 UTF-8 바이트로 옮긴다 — 다음 '%' 까지 한 덩이로(대리 쌍이 갈라지지 않게).
                val next = s.indexOf('%', i).let { if (it < 0) s.length else it }
                out.write(s.substring(i, next).toByteArray(Charsets.UTF_8))
                i = next
            }
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(out.toByteArray()))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }
}
