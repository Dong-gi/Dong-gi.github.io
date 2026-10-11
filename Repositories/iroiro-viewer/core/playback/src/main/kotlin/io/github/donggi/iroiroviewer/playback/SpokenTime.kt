package io.github.donggi.iroiroviewer.playback

/**
 * 시각을 **화면 낭독기가 읽을 말**의 조각으로 — '1분 23초' 의 (1분, 23초), '45분' 의 (45분).
 *
 * 재생 막대는 Material 슬라이더라 낭독기가 값을 범위의 백분율('34퍼센트')로 읽었다. 듣는 사람이 알고 싶은 것은
 * 지금이 몇 분 몇 초이고 전체가 얼마인가다 — 그리고 `1:23` 꼴을 그대로 주면 낭독기가 '일 콜론 이십삼' 으로 읽는다. 조각을 고르는
 * 일은 순수 계산이라 여기서 하고(JVM 시험이 박는다), 말로 붙이는 일은 문자열 자원이 한다(`spokenTime` — [TrackLabels] 가 조각만
 * 만들고 붙이는 일은 자원에 맡기는 것과 같은 갈래다. 계산 쪽에 한국어를 박으면 5단계의 '문구 두 벌' 이 된다).
 */
object SpokenTime {

    enum class Kind { HOURS, MINUTES, SECONDS }

    data class Part(val kind: Kind, val value: Long)

    /**
     * [ms] 의 조각들. **0 인 조각은 뺀다** — '45분 0초' 가 아니라 '45분', '1시간 0분 5초' 가 아니라 '1시간 5초'. 모두 0 이면 '0초'
     * 한 조각이다(빈 말을 만들지 않는다). 초 아래는 버린다(화면의 `1:23` 과 같은 자리). 음수는 0 이다.
     */
    fun partsOf(ms: Long): List<Part> {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val parts = ArrayList<Part>(3)
        if (h > 0) parts += Part(Kind.HOURS, h)
        if (m > 0) parts += Part(Kind.MINUTES, m)
        if (s > 0 || parts.isEmpty()) parts += Part(Kind.SECONDS, s)
        return parts
    }
}
