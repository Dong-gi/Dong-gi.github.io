package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * ZIP·tar 엔트리 이름의 인코딩을 가려낸다.
 *
 * ZIP 명세에는 파일명 인코딩 필드가 없다. 플래그 비트 11 이 "UTF-8 이다" 를 뜻하지만
 * 한국에서 만들어진 아카이브 상당수는 CP949 바이트를 그 플래그 없이 넣고, 반대로
 * UTF-8 로 적으면서 플래그를 빠뜨린 도구도 흔하다. tar 는 표시조차 없다(PAX 의 `path` 만 UTF-8 이 약속이다).
 * 그래서 판정이 필요하다.
 *
 * ## 판정은 `core:charset` 의 것 하나다
 *
 * 7단계부터 판정이 두 벌이었다 — 여기와 `CharsetDetector`. CLAUDE.md 가 '두 곳을 함께 고쳐라' 로 버티던 빚을
 * 갚았다: 합치기 전에 지금의 답을 회귀 표본으로 박고(`EntryNameRegressionTest`), 그 답을 지키도록 `CharsetDetector`
 * 를 짧은 입력에 맞춘 뒤 이 파일은 판정을 **부르기만** 한다. 여기 남은 것은 ZIP 만의 사정 둘 — UTF-8 표시, 그리고
 * 결과를 진단 표지(`nameCharset`)로 옮기는 것 — 이다. 같은 바이트는 이제 글 뷰어와 압축 목록에서 같은 인코딩으로
 * 읽힌다(`EntryNameConsistencyTest` 가 지킨다).
 *
 * 합치며 바뀐 답(전부 예전이 틀렸던 것): Shift_JIS 이름이 CP949 찌꺼기 대신 일본어로, UTF-8 로 적은 `café` 가
 * `caf챕` 대신 `café` 로, Latin-1 로 적은 `café` 가 `caf�` 대신 `café` 로 읽힌다. 중국어(GB18030) 이름은 예전처럼
 * 한글 찌꺼기로 읽힌다 — 짧은 입력은 한국어로 기운다(`CharsetDetector` 의 '못 하는 것').
 *
 * ## 실측으로 확인한 것
 *
 * 안드로이드에서 `x-windows-949`·`MS949`·`windows-949` 는 전부 `EUC-KR` 이라는 이름으로
 * 돌아온다. 그러나 **그 이름 뒤에 있는 변환기는 ICU 의 UHC 라서 CP949 전체를 담는다** —
 * 에뮬레이터에서 직접 재 보니 `0x81 0x41` 이 U+AC02(갂)로 디코드되고 한글 음절 11,172자가
 * 하나도 빠짐없이 왕복한다. 즉 이름만 EUC-KR 이고 능력은 CP949 다.
 * (개발 PC 의 JVM 은 `x-windows-949` 라는 이름으로 같은 능력을 준다.) 후보를 찾는 순서는
 * `TextEncoding.CP949` 한 곳에 있다.
 */
object EntryNameDecoder {

    /**
     * 이 기기의 CP949. 안드로이드가 어떤 별칭을 아는지 확인되지 않아 순서대로 찾는다(`TextEncoding.CP949`).
     * 전부 없으면 null 이고, 그때는 판정이 다른 후보로 물러난다(깨지지만 예외는 나지 않는다).
     */
    val cp949: Charset? get() = TextEncoding.CP949.charset

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
     * @param raw 엔트리 이름의 원본 바이트. commons-compress 의 `getRawName()`, tar 머리의 이름 칸.
     * @param utf8Flag 일반 목적 비트 11(UTF-8 표시)이 서 있는가. tar 는 언제나 거짓이다.
     * @param fallback 원본 바이트를 못 얻었을 때 라이브러리가 준 문자열.
     */
    fun decode(raw: ByteArray?, utf8Flag: Boolean, fallback: String): Decoded {
        if (raw == null || raw.isEmpty()) return Decoded(fallback, "라이브러리 기본")

        if (utf8Flag) {
            // 플래그가 섰다고 실제로 UTF-8 인 것은 아니다. 엄격 디코드가 실패하면
            // 플래그가 거짓말을 한 것이므로 아래 판정으로 내려간다.
            strictUtf8(raw)?.let { return Decoded(it, "UTF-8(플래그)") }
        }

        val d = CharsetDetector.detect(raw)
        if (d.evidence is CharsetDetector.Evidence.AsciiOnly) {
            return Decoded(String(raw, StandardCharsets.US_ASCII), "ASCII")
        }
        val cs = d.encoding.charset ?: StandardCharsets.UTF_8
        val name = String(raw, cs)
        val label = when {
            d.encoding == TextEncoding.UTF_8 -> "UTF-8(플래그 없음)"
            d.encoding == TextEncoding.CP949 && d.evidence is CharsetDetector.Evidence.KoreanOverUtf8 ->
                "${cs.name()}(한글 우선)"
            // 어느 후보도 엄격하게 읽지 못해 너그럽게 읽었다 — 깨진 자리가 대체 문자로 남는다.
            d.encoding == TextEncoding.CP949 && d.confidence == CharsetDetector.Detection.Confidence.LOW &&
                d.runnerUp == TextEncoding.LATIN1 -> "${cs.name()}(대체)"
            else -> cs.name()
        }
        return Decoded(name, label)
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
}
