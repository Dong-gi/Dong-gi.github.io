package io.github.donggi.iroiroviewer.format.epub

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * EPUB 내장 글꼴의 **난독화를 푼다.** 암호화가 아니라 XOR 이고, 방법이 공개돼 있다.
 *
 * ## 왜 푸는가
 *
 * 사용자가 2026-09-23 에 정했다 — '읽는 방법이 공개된 암호화는 연다'. 예전에는 글꼴만 걸린
 * 책을 열되 글꼴은 풀지 않았다(글꼴이 조용히 기본 글꼴로 바뀌고, 글꼴 안의 사용자 영역
 * 글자는 네모로 보였다).
 *
 * ## 두 방식
 *
 * | | IDPF(`http://www.idpf.org/2008/embedding`) | Adobe(`http://ns.adobe.com/pdf/enc#RC`) |
 * |---|---|---|
 * | 근거 | EPUB 3.3 명세 4.4 'Font obfuscation' | 명세가 아니다. Adobe 가 퍼뜨린 방식이고 오픈소스 구현(Readium)이 따른다 |
 * | 열쇠 | 고유 식별자에서 공백 넷(U+0020·0009·000D·000A)을 뺀 UTF-8 의 SHA-1(20바이트) | 식별자에서 `urn:uuid:` 와 `-` 를 뺀 16진 32자(16바이트) |
 * | 범위 | 앞 1040 바이트 | 앞 1024 바이트 |
 *
 * 둘 다 열쇠를 되풀이하며 XOR 하고, 범위 뒤는 그대로다. 난독화는 ZIP 압축 **전에** 하므로
 * 압축을 푼 바이트에 건다(명세 4.4.4).
 *
 * **시험의 오라클이 우리가 아니다.** 우리가 잠그고 우리가 푸는 왕복은 판별력이 0 이라
 * (`PdfDecryptorTest` 의 머리말), Readium 이 만든 난독화 글꼴 표본으로 확인했다 — 표본은
 * 제3자의 글꼴이라 커밋하지 않고 `samples-local/epub-deobfuscation/` 에 둔다(`FontObfuscationTest`).
 */
internal object FontObfuscation {

    const val IDPF = "http://www.idpf.org/2008/embedding"
    const val ADOBE = "http://ns.adobe.com/pdf/enc#RC"

    /** 우리가 푸는 알고리즘인가. */
    fun isObfuscation(algorithm: String?): Boolean = algorithm == IDPF || algorithm == ADOBE

    /** 열쇠와 범위. 이것을 [wrap] 에 준다. */
    class Mask internal constructor(internal val key: ByteArray, internal val length: Int)

    /**
     * 이 알고리즘의 열쇠를 만든다. 만들 수 없으면(식별자가 없거나 UUID 가 아니다) null.
     *
     * @param uniqueIdentifier `package@unique-identifier` 가 가리키는 `dc:identifier` 의 값.
     * @param identifiers 그 밖의 `dc:identifier` 값들. Adobe 방식은 고유 식별자가 UUID 가 아니면
     *   UUID 인 식별자를 찾는다 — Adobe 도구는 UUID 식별자를 따로 넣는다.
     */
    fun maskFor(algorithm: String, uniqueIdentifier: String?, identifiers: List<String>): Mask? =
        when (algorithm) {
            IDPF -> uniqueIdentifier?.let { idpfKey(it) }?.let { Mask(it, IDPF_LENGTH) }
            ADOBE -> (listOfNotNull(uniqueIdentifier) + identifiers).firstNotNullOfOrNull { adobeKey(it) }
                ?.let { Mask(it, ADOBE_LENGTH) }
            else -> null
        }

    /** IDPF 열쇠. 공백 넷을 뺀 값이 비면 null — 빈 문자열의 SHA-1 로 푸는 것은 추측이다. */
    internal fun idpfKey(uniqueIdentifier: String): ByteArray? {
        val stripped = uniqueIdentifier.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
        if (stripped.isEmpty()) return null
        return MessageDigest.getInstance("SHA-1").digest(stripped.toByteArray(Charsets.UTF_8))
    }

    /** Adobe 열쇠. `urn:uuid:` 와 `-`·공백을 빼고 16진 32자가 남아야 한다. */
    internal fun adobeKey(identifier: String): ByteArray? {
        var s = identifier.trim()
        if (s.startsWith("urn:uuid:", ignoreCase = true)) s = s.substring("urn:uuid:".length)
        s = s.filterNot { it == '-' || it.isWhitespace() }
        if (s.length != 32 || !s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return ByteArray(16) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** 난독화를 풀며 읽는 스트림. 닫으면 [input] 도 닫힌다. */
    fun wrap(input: InputStream, mask: Mask): InputStream = Deobfuscating(input, mask)

    const val IDPF_LENGTH = 1040
    const val ADOBE_LENGTH = 1024

    /**
     * **자리를 세며** 푼다. `skip` 도 자리를 옮기므로 그대로 위임하면 뒤에 읽는 바이트에
     * 엉뚱한 열쇠 바이트가 걸린다 — 범위 안에서는 읽어서 버린다.
     */
    private class Deobfuscating(input: InputStream, private val mask: Mask) : FilterInputStream(input) {
        private var position = 0L

        override fun read(): Int {
            val b = super.read()
            if (b < 0) return b
            val out = if (position < mask.length) {
                (b xor (mask.key[(position % mask.key.size).toInt()].toInt() and 0xFF)) and 0xFF
            } else {
                b
            }
            position++
            return out
        }

        // `FilterInputStream` 의 기본 구현은 이것을 바이트 하나씩 `read()` 로 푼다(함정 표).
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n <= 0) return n
            var i = 0
            while (i < n && position + i < mask.length) {
                val at = position + i
                b[off + i] = (b[off + i].toInt() xor mask.key[(at % mask.key.size).toInt()].toInt()).toByte()
                i++
            }
            position += n
            return n
        }

        override fun skip(n: Long): Long {
            if (n <= 0) return 0
            if (position >= mask.length) {
                val skipped = super.skip(n)
                position += skipped
                return skipped
            }
            // 범위 안에서는 읽어서 버린다 — 자리를 정확히 알아야 뒤 바이트를 옳게 푼다.
            val scratch = ByteArray(minOf(n, 8192L).toInt())
            val got = read(scratch, 0, scratch.size)
            return if (got < 0) 0 else got.toLong()
        }

        override fun markSupported(): Boolean = false
        override fun mark(readlimit: Int) {}
        override fun reset() = throw java.io.IOException("mark/reset 을 지원하지 않는다")
    }
}
