package io.github.donggi.iroiroviewer.format.html

/**
 * 한글 문서(HWP 5.0·HWPX)가 본문에 적지만 **기기가 그대로는 그리지 못하는 글자**를 뜻이 같은 유니코드로 옮긴다.
 *
 * **두 변환기가 이 표 하나를 쓴다.** 포맷 모듈끼리는 서로를 볼 수 없어 처음에는 HWP 5.0 변환기에만 있었고, 그래서 같은
 * 보도자료가 HWP 로는 `１ 신규 플레이어 진입` 으로, HWPX 로는 번호 자리가 두부로 보였다(13단계, 짝 표본 K11/K33·K19/K27). 흐름
 * 문서의 바탕인 여기 두면 한쪽만 고쳐지는 일이 없다.
 *
 * * **한컴의 사설 영역 글자**(보조 사설 영역 U+F0000~). 한컴 글꼴이 없는 기기에서는 두부(□)가 된다 — 공문서의 절 제목
 *   (`U+F02B1 신규 플레이어 진입`)이 번호를 잃고, 목차도 두부로 찬다(13단계 HWP 표본 12개 가운데 넷, 73곳 · HWPX 표본 둘, 54곳).
 *   옮길 글자는 **정책브리핑의 바로보기(한컴 문서를 HTML 로 바꿔 보여 주는 변환기)가 같은 자리에 적은 글자**다 — 앞뒤 글로
 *   자리를 맞춰 볼 수 있었던 곳은 모두 한 가지였다(13단계 검토). 한컴이 이 글자들의 뜻을 적은 공개 자료는 찾지 못했다.
 *   표에 없는 사설 영역 글자는 그대로 둔다.
 * * U+F53A — 한컴이 자기 명세 문서에서 '한글' 의 '한' 자리에 쓰는 로고 글자. **문맥으로 짐작한 것이다.**
 * * U+00AD(soft hyphen) — 한글은 보이는 붙임표로 그리고(바로보기도 `-` 로 적는다, 표본 K04 의 글머리 일곱 곳) 브라우저는
 *   줄 끝이 아니면 그리지 않는다. 글머리의 `-` 가 사라진다.
 */
object HancomChars {
    /** [codePoint] 를 옮긴 글자. 옮길 것이 아니면 null. */
    fun map(codePoint: Int): String? = when (codePoint) {
        0x00AD -> "-"
        HANCOM_LOGO -> "한"
        in BOXED_DIGIT_FIRST..BOXED_DIGIT_FIRST + 8 -> (FULLWIDTH_ONE + (codePoint - BOXED_DIGIT_FIRST)).toChar().toString()
        0xF0854 -> "『"
        0xF0855 -> "』"
        else -> null
    }

    /** 문자열 하나를 통째로. 옮길 것이 없으면 [s] 그대로(새 문자열을 만들지 않는다). */
    fun mapAll(s: String): String {
        var out: StringBuilder? = null
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val mapped = map(cp)
            if (mapped != null && out == null) out = StringBuilder(s.length).append(s, 0, i)
            if (out != null) {
                if (mapped != null) out.append(mapped) else out.append(s, i, i + n)
            }
            i += n
        }
        return out?.toString() ?: s
    }

    /** 겹칠 글자(글자 겹치기)를 옮긴 결과. [dropped] 면 그릴 수 없는 글자를 버렸다 — 부르는 쪽이 글자 효과로 센다. */
    class Composed(val text: String, val dropped: Boolean)

    /**
     * 글자 겹치기(HWP `tcps`·HWPX `hp:compose`)의 글. 공문서의 절 번호(네모 안의 숫자)가 여기 든다 — [mapAll] 로 옮기고,
     * 그러고도 남은 사설 영역 글자(한글 전용 글꼴의 기호)는 기기에 그릴 글꼴이 없어 **버린다**. 처음에는 HWPX 만 버리고 세었고
     * HWP 는 두부를 그대로 적었다(13단계 짝 대조).
     */
    fun compose(raw: String): Composed {
        val mapped = mapAll(raw)
        var sb: StringBuilder? = null
        var i = 0
        while (i < mapped.length) {
            val cp = mapped.codePointAt(i)
            val n = Character.charCount(cp)
            if (isPrivateUse(cp)) {
                if (sb == null) sb = StringBuilder(mapped.length).append(mapped, 0, i)
            } else {
                sb?.appendCodePoint(cp)
            }
            i += n
        }
        return if (sb == null) Composed(mapped, false) else Composed(sb.toString(), true)
    }

    /** 사설 영역 글자인가(기본 평면의 U+E000~F8FF, 보조 사설 영역 A·B). */
    fun isPrivateUse(cp: Int): Boolean = cp in 0xE000..0xF8FF || cp in 0xF0000..0x10FFFF

    private const val HANCOM_LOGO = 0xF53A

    /** 네모 안의 숫자 1~9(한컴 사설 영역). 바로보기는 전각 숫자 `１`~`９` 로 적는다. */
    private const val BOXED_DIGIT_FIRST = 0xF02B1
    private const val FULLWIDTH_ONE = 0xFF11
}

/**
 * 한글 문서의 글머리표 글자. 한글도 워드처럼 기호 글꼴(Wingdings)의 **사설 영역 글자**(U+F0A7 등)를 적는다 — HWP 실물의
 * 글머리표 다섯 가운데 둘이 그랬다. 그 글꼴이 없는 기기에서는 두부(□)가 되므로 뜻이 같은 유니코드 글자로 옮기고, 모르는
 * 사설 영역 글자는 가운뎃점으로 쓴다. 두 한글 변환기가 이 표 하나를 쓴다([HancomChars] 와 같은 까닭).
 * 12단계 docx 의 `ListMarkers.glyph` 에 같은 표가 한 벌 더 있다 — 고칠 때 함께 본다.
 */
object BulletGlyphs {
    fun map(c: Char): String {
        val code = c.code
        if (code in 0xF000..0xF0FF) return WINGDINGS[code - 0xF000] ?: "•"
        if (code in 0xE000..0xF8FF) return "•"
        if (code < 0x20) return "•"
        return c.toString()
    }

    /** 여러 글자로 된 글머리표(HWPX 는 문자열로 적는다). 보조 평면의 사설 영역 글자도 가운뎃점이 된다. */
    fun mapAll(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            when {
                cp <= 0xFFFF -> sb.append(map(cp.toChar()))
                cp >= 0xF0000 -> sb.append('•')
                else -> sb.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private val WINGDINGS = mapOf(
        0x6C to "●", 0x6E to "■", 0x6F to "□", 0x71 to "❑", 0x75 to "◆", 0x76 to "❖",
        0xA7 to "▪", 0xD8 to "➢", 0xE8 to "➔", 0xFB to "✗", 0xFC to "✓",
        0xFD to "☒", 0xFE to "☑", 0x9F to "•", 0xB7 to "•",
    )
}

/**
 * 한글 문서의 글꼴을 **명조인가 아닌가** 로만 옮긴다. 두 한글 변환기가 이 판정 하나를 쓴다.
 *
 * 글꼴 이름은 적지 않는다 — 한컴 글꼴(휴먼명조·한양중고딕·함초롬바탕…)은 안드로이드에 없어 이름을 적어도 기본(고딕)으로
 * 그려지고, 칸 수만 개의 표에서 글자마다 붙으면 HTML 이 수백 KB 는다(K01 에서 15,646번, K25 실측). 기기에서 달라 보여야
 * 하는 유일한 차이가 명조 대 고딕이다. 처음에는 HWP 5.0 변환기가 이름을 그대로 적고 HWPX 변환기만 명조를 옮겨 같은 문서의
 * 서체가 포맷마다 달랐다(13단계 짝 대조).
 */
object HancomFonts {
    fun isSerif(font: String?): Boolean {
        if (font == null) return false
        return SERIF_HINTS.any { font.contains(it, ignoreCase = true) }
    }

    /** 명조 계열(바탕·명조·궁서·Batang·Myeongjo·Times). */
    private val SERIF_HINTS = listOf("명조", "바탕", "궁서", "Batang", "Myeongjo", "Gungsuh", "Times", "Serif")
}
