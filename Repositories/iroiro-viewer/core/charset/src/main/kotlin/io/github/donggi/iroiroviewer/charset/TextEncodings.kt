package io.github.donggi.iroiroviewer.charset

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 이 앱이 다루는 문자 인코딩과, 그 이름을 기기에서 실제로 찾는 법.
 *
 * ## 왜 이름을 하드코딩하지 않는가
 *
 * 안드로이드는 `x-windows-949`·`MS949`·`windows-949` 를 전부 **`EUC-KR`** 이라는 이름으로
 * 돌려준다(실측표 참고). 그런데 그 이름 뒤의 변환기는 ICU 의 UHC 라서 **능력은 CP949
 * 전체**다 — 이름만 EUC-KR 이고 완성형 밖 한글까지 왕복한다. 개발 PC 의 JVM 은 같은
 * 능력을 `x-windows-949` 라는 이름으로 준다.
 *
 * 그래서 이름을 못 박지 않고 후보를 순서대로 찾는다. 단위 시험이 PC 에서 통과했다고
 * 기기에서 같을 것이라고 믿지 마라 — 이 프로젝트는 이미 그것을 실측으로 배웠다.
 */
enum class TextEncoding(
    /** 화면과 기록에 쓰는 이름. 기기가 뭐라 부르든 우리는 이 이름으로 말한다. */
    val label: String,
    private val candidates: List<String>,
) {
    UTF_8("UTF-8", listOf("UTF-8")),
    UTF_16LE("UTF-16LE", listOf("UTF-16LE")),
    UTF_16BE("UTF-16BE", listOf("UTF-16BE")),

    /**
     * 한국어 레거시. **이름은 EUC-KR 로 나와도 능력은 CP949 다**(위 주석).
     * 우리는 CP949 를 얻으려 하고, 못 얻으면 엄격 EUC-KR 이라도 쓴다.
     */
    CP949("CP949", listOf("x-windows-949", "MS949", "windows-949", "EUC-KR")),

    SHIFT_JIS("Shift_JIS", listOf("Shift_JIS", "windows-31j", "MS932")),

    /** 중국어 간체. 요구사항의 넷에 들지 않지만 판정에서 가려내야 오판이 준다. */
    GB18030("GB18030", listOf("GB18030", "GBK")),

    /** 중국어 번체. 같은 이유. */
    BIG5("Big5", listOf("Big5", "Big5-HKSCS")),

    /** 서유럽. 어떤 바이트열이든 받으므로 **판정의 최후 수단**이지 후보가 아니다. */
    LATIN1("ISO-8859-1", listOf("ISO-8859-1"));

    /** 이 기기에서 실제로 잡힌 Charset. 없으면 null. */
    val charset: Charset? by lazy {
        for (name in candidates) {
            val cs = runCatching {
                if (Charset.isSupported(name)) Charset.forName(name) else null
            }.getOrNull()
            if (cs != null) return@lazy cs
        }
        null
    }

    /** 기기가 실제로 쓰는 이름. 진단 화면이 보여 준다 — 우리 라벨과 다를 수 있다. */
    val platformName: String? get() = charset?.name()

    /**
     * 한 문자가 최대 몇 바이트인가. **코드포인트 기준**이다.
     *
     * `CharsetEncoder.maxBytesPerChar()` 를 쓰면 안 된다 — 그것은 자바 `char` 당 값이라
     * UTF-8 에 3.0 을 돌려주고, 4바이트 문자는 서러게이트 2개라 6으로 계산된다.
     * 아래 값은 전 코드포인트를 실제로 인코딩해 잰 것이다.
     */
    val maxBytesPerCodePoint: Int
        get() = when (this) {
            UTF_8, GB18030, UTF_16LE, UTF_16BE -> 4
            CP949, SHIFT_JIS, BIG5 -> 2
            LATIN1 -> 1
        }

    /**
     * 줄바꿈(`0x0A`)이 **멀티바이트 문자의 일부로 나타날 수 있는가.**
     *
     * 이 한 값이 줄 색인 방식을 가른다. 거짓이면 바이트에서 `0x0A` 를 세는 것으로 충분하고
     * (빠르다), 참이면 **디코드하면서** 세야 한다.
     *
     * 전 코드포인트를 각 인코딩으로 인코딩해 산출 바이트에 `0x0A` 가 있는지 전수 조사한
     * 결과다 — UTF-8·CP949·EUC-KR·Shift_JIS·GB18030·Big5 는 **0건**, UTF-16 은 **8,695건**
     * (예: `U+AC0A '갊'` = `AC 0A`).
     */
    val newlineCanHideInChar: Boolean
        get() = this == UTF_16LE || this == UTF_16BE

    companion object {
        /**
         * 자동 판정의 후보. **요구사항의 넷을 중심에 두되** 중국어 둘을 함께 본다 —
         * 후보에서 빼면 중국어 파일이 CP949 나 Shift_JIS 로 잘못 읽힌다.
         *
         * [LATIN1] 은 여기 없다. 어떤 바이트열이든 받아들이므로 후보에 넣으면 언제나
         * 이긴다 — 그것은 판정이 아니라 포기다.
         */
        val autoCandidates: List<TextEncoding> = listOf(
            UTF_8, CP949, SHIFT_JIS, GB18030, BIG5,
        )

        /** 사용자가 손으로 고를 수 있는 목록. 최후 수단인 Latin-1 도 여기에는 있다. */
        val userChoices: List<TextEncoding> = entries.toList()
    }
}

/**
 * 파일 앞의 바이트 순서 표식.
 *
 * [encoding] 이 null 인 것은 **BOM 은 알아보지만 우리가 읽지 못하는 인코딩**이다.
 * 그런 파일은 "지원하지 않습니다" 로 정확히 끝내야 한다 — UTF-16 인 척 열어 널 바이트가
 * 가득한 화면을 보여 주는 것이 가장 나쁘다.
 */
enum class Bom(val bytes: ByteArray, val encoding: TextEncoding?) {
    // **UTF-32 를 UTF-16 보다 먼저 둔다.** `FF FE 00 00`(UTF-32LE)은 `FF FE`(UTF-16LE)로
    // 시작하므로, 순서가 뒤집히면 UTF-32 파일이 UTF-16LE 로 먹히고 널이 가득 보인다.
    UTF32LE(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00), null),
    UTF32BE(byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte()), null),
    UTF8(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), TextEncoding.UTF_8),
    UTF16LE(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), TextEncoding.UTF_16LE),
    UTF16BE(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), TextEncoding.UTF_16BE);

    val length: Int get() = bytes.size

    companion object {
        /** 앞머리에서 BOM 을 찾는다. 긴 것부터 본다(위 주석). */
        fun detect(head: ByteArray, length: Int = head.size): Bom? {
            for (bom in entries) {
                if (length >= bom.length && bom.bytes.indices.all { head[it] == bom.bytes[it] }) {
                    return bom
                }
            }
            return null
        }
    }
}

/**
 * 자바는 **UTF-8 BOM 을 먹지 않는다.** `EF BB BF` 뒤의 글자를 읽으면 본문 첫 글자가
 * `U+FEFF` 가 되어 화면에 보이지 않는 글자가 하나 붙는다(그리고 첫 줄 길이가 1 늘어난다).
 *
 * 그래서 BOM 은 **읽기 전에 바이트 단위로 건너뛴다.** 이 상수는 그 사실을 잊지 않도록
 * 이름으로 남긴 것이다.
 */
val UTF8_BOM_NOT_CONSUMED_BY_JAVA: Charset = StandardCharsets.UTF_8
