package io.github.donggi.iroiroviewer.model

import java.time.Instant
import java.time.ZoneId

/**
 * 시각 목록을 **날짜로 묶는다.** 갤러리가 날짜 머리글을 다는 데 쓴다.
 *
 * ## 왜 순수 함수인가
 *
 * 시간대·서머타임·해가 바뀌는 경계가 전부 여기 모여 있고, 그것들은 화면 캡처로 확인할
 * 수 있는 것이 아니다(9·10단계가 쪽 판정과 제스처를 같은 이유로 순수 함수로 뺐다).
 * 여기 있으면 **시험이 답한다** — 시간대를 인자로 받으므로 시드니의 3월도 서울의 9월도
 * 같은 시험에서 확인된다.
 *
 * ## 왜 경계를 밀리초로 들고 있는가
 *
 * 사진 1만 장에 [dayOf] 를 1만 번 부르면 `Instant` 와 `ZonedDateTime` 이 1만 쌍
 * 만들어진다. 목록이 시각 순으로 정렬되어 있으면 **[Day.contains] 로 먼저 물어보고
 * 벗어날 때만** 새로 계산하면 되고, 그러면 호출이 날 수만큼으로 준다.
 */
object DayBucket {

    /** 하루. [startMillis] 이상 [endMillis] 미만이 그 날이다. */
    data class Day(
        val startMillis: Long,
        val endMillis: Long,
        val year: Int,
        val month: Int,
        val dayOfMonth: Int,
    ) {
        fun contains(millis: Long): Boolean = millis in startMillis until endMillis
    }

    /**
     * 목록의 한 구간. [firstIndex] 부터 [count] 개가 같은 날이다.
     *
     * **자리를 번호로 들고 있는 것이 요점이다.** 항목을 날짜별 목록으로 다시 담으면
     * 화면이 누른 칸의 **원래 자리**를 잃어버려, 사진을 눌렀을 때 다른 사진이 열린다.
     */
    data class Section(
        val day: Day,
        val firstIndex: Int,
        val count: Int,
    )

    /** [millis] 가 속한 날. */
    fun dayOf(millis: Long, zone: ZoneId): Day {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        // `atStartOfDay(zone)` 를 쓴다. 서머타임이 시작하는 날의 0시는 **없을 수 있고**,
        // 그때 이 함수는 건너뛴 뒤의 첫 시각을 준다. `LocalDateTime` 으로 빼고 더하면
        // 그 날만 경계가 한 시간 어긋난다.
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return Day(start, end, date.year, date.monthValue, date.dayOfMonth)
    }

    /**
     * 시각 목록을 날짜 구간으로 나눈다.
     *
     * **정렬을 여기서 하지 않는다.** 갤러리는 이미 새것부터 정렬해 두었고, 여기서 다시
     * 정렬하면 1만 개를 한 번 더 훑는다. 대신 **이웃한 같은 날만 묶는다** — 목록이
     * 정렬되어 있지 않으면 같은 날이 여러 구간으로 갈리지만, 그것은 목록이 말하는
     * 그대로라 화면이 거짓말을 하지는 않는다.
     */
    fun sectionsOf(times: List<Long>, zone: ZoneId): List<Section> {
        if (times.isEmpty()) return emptyList()
        val out = ArrayList<Section>(16)
        var day = dayOf(times[0], zone)
        var first = 0
        for (i in 1 until times.size) {
            if (day.contains(times[i])) continue
            out += Section(day, first, i - first)
            day = dayOf(times[i], zone)
            first = i
        }
        out += Section(day, first, times.size - first)
        return out
    }
}
