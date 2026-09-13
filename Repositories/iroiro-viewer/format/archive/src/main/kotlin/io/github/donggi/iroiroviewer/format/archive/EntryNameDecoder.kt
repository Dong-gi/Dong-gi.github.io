package io.github.donggi.iroiroviewer.format.archive

import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * ZIP 엔트리 이름의 인코딩을 가려낸다.
 *
 * ZIP 명세에는 파일명 인코딩 필드가 없다. 플래그 비트 11 이 "UTF-8 이다" 를 뜻하지만
 * 한국에서 만들어진 아카이브 상당수는 CP949 바이트를 그 플래그 없이 넣고, 반대로
 * UTF-8 로 적으면서 플래그를 빠뜨린 도구도 흔하다. 그래서 판정이 필요하다.
 *
 * ## 실측으로 확인한 것
 *
 * 안드로이드에서 `x-windows-949`·`MS949`·`windows-949` 는 전부 `EUC-KR` 이라는 이름으로
 * 돌아온다. 그러나 **그 이름 뒤에 있는 변환기는 ICU 의 UHC 라서 CP949 전체를 담는다** —
 * 에뮬레이터에서 직접 재 보니 `0x81 0x41` 이 U+AC02(갂)로 디코드되고 한글 음절 11,172자가
 * 하나도 빠짐없이 왕복한다. 즉 이름만 EUC-KR 이고 능력은 CP949 다.
 * (개발 PC 의 JVM 은 `x-windows-949` 라는 이름으로 같은 능력을 준다.)
 */
object EntryNameDecoder {

    /**
     * 이 기기의 CP949. 안드로이드가 어떤 별칭을 아는지 확인되지 않아 순서대로 찾는다.
     * 전부 없으면 null 이고, 그때는 UTF-8 로 물러난다(깨지지만 예외는 나지 않는다).
     */
    val cp949: Charset? by lazy { findCharset("x-windows-949", "MS949", "windows-949", "EUC-KR") }

    /** 진단 화면이 보여줄, 실제로 찾아낸 이름. 없으면 null. */
    val cp949Name: String? get() = cp949?.name()

    fun findCharset(vararg candidates: String): Charset? {
        for (name in candidates) {
            val cs = runCatching { if (Charset.isSupported(name)) Charset.forName(name) else null }
                .getOrNull()
            if (cs != null) return cs
        }
        return null
    }

    /** 판정 결과. 어떤 근거로 그 인코딩을 골랐는지까지 남긴다(진단과 회귀 시험용). */
    data class Decoded(val name: String, val charsetLabel: String)

    /**
     * @param raw 엔트리 이름의 원본 바이트. commons-compress 의 `getRawName()`.
     * @param utf8Flag 일반 목적 비트 11(UTF-8 표시)이 서 있는가.
     * @param fallback 원본 바이트를 못 얻었을 때 라이브러리가 준 문자열.
     */
    fun decode(raw: ByteArray?, utf8Flag: Boolean, fallback: String): Decoded {
        if (raw == null || raw.isEmpty()) return Decoded(fallback, "라이브러리 기본")

        if (utf8Flag) {
            // 플래그가 섰다고 실제로 UTF-8 인 것은 아니다. 엄격 디코드가 실패하면
            // 플래그가 거짓말을 한 것이므로 아래 판정으로 내려간다.
            strictUtf8(raw)?.let { return Decoded(it, "UTF-8(플래그)") }
        }

        if (raw.all { it >= 0 }) return Decoded(String(raw, StandardCharsets.US_ASCII), "ASCII")

        val cs = cp949
        strictUtf8(raw)?.let { utf8 ->
            // CP949 바이트열 가운데 우연히 유효한 UTF-8 인 것이 있다(두 바이트 조합의
            // 약 2%). 짧은 한국어 이름이 그렇게 걸리면 라틴 문자 몇 개로 바뀌어 버린다.
            // 그래서 '엄격 UTF-8 성공' 만으로 끝내지 않고, CP949 로 읽었을 때 한글이
            // 또렷하게 나오는지를 함께 본다.
            if (cs != null && !utf8.hasHangul()) {
                val korean = String(raw, cs)
                if (korean.looksKorean()) return Decoded(korean, "${cs.name()}(한글 우선)")
            }
            return Decoded(utf8, if (utf8Flag) "UTF-8(플래그)" else "UTF-8(플래그 없음)")
        }

        if (cs != null) return Decoded(String(raw, cs), cs.name())

        return Decoded(String(raw, StandardCharsets.UTF_8), "UTF-8(대체)")
    }

    /**
     * 엄격 UTF-8 디코드. 한 바이트라도 규칙에 맞지 않으면 null 이다.
     *
     * 기본 디코더는 잘못된 바이트를 U+FFFD 로 바꿔 버려서 "성공했다" 와 "깨졌다" 를
     * 구분할 수 없다. 그래서 REPORT 로 바꿔 예외를 받는다.
     */
    private fun strictUtf8(raw: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(raw))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun String.hasHangul(): Boolean = any { it in '가'..'힣' || it in 'ㄱ'..'ㆎ' }

    /**
     * 비ASCII 글자의 대부분이 한글 음절인가. 판정을 '한글이 하나라도 있는가' 로 하면
     * 일본어·중국어 이름을 CP949 로 잘못 읽은 결과에도 한글이 섞여 들어 오판한다.
     */
    private fun String.looksKorean(): Boolean {
        if (contains('�')) return false
        var nonAscii = 0
        var hangul = 0
        for (c in this) {
            if (c.code < 0x80) continue
            nonAscii++
            if (c in '가'..'힣') hangul++
        }
        return nonAscii > 0 && hangul * 10 >= nonAscii * 7
    }
}
