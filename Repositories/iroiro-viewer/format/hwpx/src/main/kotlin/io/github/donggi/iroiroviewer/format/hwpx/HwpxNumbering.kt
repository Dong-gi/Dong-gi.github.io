package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.html.HancomNumbers

/**
 * `hp:autoNumFormat` 하나 — 각주·미주·자동 번호의 표지 모양. [shape] 는 `HancomNumbers` 의 값(`type` 의 이름을
 * [HancomNumbers.shapeOf] 로 옮긴 것), [userChar] 는 `USER_CHAR` 일 때의 글자, 앞뒤 장식은 글자 그대로(`suffixChar=")"`).
 *
 * 번호 모양을 글자로 옮기는 표는 두 한글 변환기가 `format:html` 의 `HancomNumbers` 하나를 쓴다 — 처음에는 여기 따로 있어
 * 같은 문서의 번호가 달랐다(13단계 짝 대조: 모양 `SYMBOL` 이 HWPX 에서만 `•`, 시작 번호 0 이 HWPX 에서만 1).
 */
internal class AutoNumFormat(val shape: Int, val userChar: String, val prefix: String, val suffix: String) {

    /** 번호 [value] 의 표지. */
    fun label(value: Int): String = HancomNumbers.noteLabel(value, shape, userChar, prefix, suffix)

    companion object {
        val DEFAULT = AutoNumFormat(HancomNumbers.DIGIT, "", "", "")

        /** 각주의 한글 기본값 — `1)`. */
        val FOOTNOTE = AutoNumFormat(HancomNumbers.DIGIT, "", "", ")")
    }
}

/**
 * 각주·미주 모양 한 벌(`hp:footNotePr`·`hp:endNotePr`) — 표지 모양과 번호 매기기(`hp:numbering@type`·`@newNum`).
 *
 * 번호 매기기는 HWP 5.0 의 각주 모양(`NoteShape.numbering` — 0 이어서·1 구역마다·2 쪽마다)과 같은 세 가지다. 흐름 렌더에는
 * 쪽이 없으므로 **쪽마다([ON_PAGE])는 이어서 센다** — 한글이 저장한 번호(`hp:footNote@number`)는 쪽마다 1 로 돌아가 같은
 * 번호가 되풀이된다(표본 K26). HWP 5.0 변환기는 처음부터 제 셈으로 번호를 매긴다.
 *
 * @param newNum 시작 번호. 우리 셈의 1 이 이 번호다(HWP 의 `n + start - 1`).
 */
internal class NoteSettings(val format: AutoNumFormat, val numbering: Int, val newNum: Int) {
    companion object {
        const val CONTINUOUS = 0
        const val ON_SECTION = 1
        const val ON_PAGE = 2

        val DEFAULT = NoteSettings(AutoNumFormat.FOOTNOTE, CONTINUOUS, 1)

        /** `hp:numbering@type` 을 우리 값으로. 모르는 값은 이어서. */
        fun numberingOf(raw: String?): Int = when (raw?.uppercase()) {
            "ON_SECTION" -> ON_SECTION
            "ON_PAGE" -> ON_PAGE
            else -> CONTINUOUS
        }
    }
}
