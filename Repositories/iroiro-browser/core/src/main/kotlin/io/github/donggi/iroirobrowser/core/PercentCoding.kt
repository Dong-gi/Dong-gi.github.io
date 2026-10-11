package io.github.donggi.iroirobrowser.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 퍼센트 부호화를 풀고 엄격하게(깨진 바이트를 받지 않고) 글자로 읽는다. */
internal object PercentCoding {

    /** `%XX` 를 바이트로. 그 밖의 글자는 그 글자의 UTF-8 바이트로(규격 밖이지만 그런 주소·머리가 흔하다). `%` 뒤가 16진이 아니면 null. */
    fun bytes(s: String): ByteArray? {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return null
                out.write(s.substring(i + 1, i + 3).toIntOrNull(16) ?: return null)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toByteArray()
    }

    fun strictDecode(bytes: ByteArray, charset: Charset = Charsets.UTF_8): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /** `%XX` 가 있고 엄격한 UTF-8 로 풀리면 푼 것, 아니면 null. */
    fun decodeUtf8(s: String): String? {
        if ('%' !in s) return s
        return bytes(s)?.let { strictDecode(it) }
    }
}
