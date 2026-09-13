package io.github.donggi.iroiroviewer.model

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 날짜 묶기.
 *
 * **기기의 시간대를 쓰지 않는다.** 시험이 도는 기계의 설정에 답이 달리면 그 시험은
 * 아무것도 단언하지 않는다. 시간대를 인자로 주는 이유가 이것이다.
 */
class DayBucketTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun 하루의_경계는_그_날_0시부터_다음_날_0시_직전까지다() {
        val day = DayBucket.dayOf(at(seoul, 2026, 9, 13, 15, 30), seoul)

        assertEquals(2026, day.year)
        assertEquals(9, day.month)
        assertEquals(13, day.dayOfMonth)
        assertEquals(at(seoul, 2026, 9, 13), day.startMillis)
        assertEquals(at(seoul, 2026, 9, 14), day.endMillis)

        assertTrue(day.contains(at(seoul, 2026, 9, 13)), "0시 정각은 그 날이다")
        assertTrue(day.contains(at(seoul, 2026, 9, 13, 23, 59)))
        assertFalse(day.contains(at(seoul, 2026, 9, 14)), "다음 날 0시는 다음 날이다")
        assertFalse(day.contains(at(seoul, 2026, 9, 12, 23, 59)))
    }

    /**
     * **시간대가 바뀌면 같은 순간이 다른 날이다.** 서울의 9월 13일 08시는 UTC 로
     * 9월 12일 23시다. 기기 시간대를 그대로 쓰는 갤러리가 옳은 이유가 이것이다 —
     * 사용자가 사진을 찍은 날은 그 사람의 시계로 센 날이다.
     */
    @Test
    fun 시간대에_따라_같은_순간이_다른_날이_된다() {
        val moment = at(seoul, 2026, 9, 13, 8, 0)

        assertEquals(13, DayBucket.dayOf(moment, seoul).dayOfMonth)
        assertEquals(12, DayBucket.dayOf(moment, ZoneId.of("UTC")).dayOfMonth)
    }

    /**
     * **서머타임이 시작하는 날에 0시가 없는 시간대가 있다.** 상파울루는 자정에 시계를
     * 1시로 돌렸다. `LocalDate.atStartOfDay(zone)` 은 그때 01시를 주고, 우리 경계도
     * 그것을 따라야 그 날의 사진이 앞날로 새지 않는다.
     */
    @Test
    fun 자정이_없는_날에도_경계가_어긋나지_않는다() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val day = DayBucket.dayOf(at(saoPaulo, 2018, 11, 4, 12, 0), saoPaulo)

        assertEquals(4, day.dayOfMonth)
        assertEquals(
            at(saoPaulo, 2018, 11, 4, 1, 0),
            day.startMillis,
            "없는 자정 대신 그 날의 첫 순간이어야 한다",
        )
        assertTrue(day.contains(at(saoPaulo, 2018, 11, 4, 23, 59)))
        assertFalse(day.contains(at(saoPaulo, 2018, 11, 5, 0, 1)))
    }

    @Test
    fun 빈_목록은_구간이_없다() {
        assertTrue(DayBucket.sectionsOf(emptyList(), seoul).isEmpty())
    }

    @Test
    fun 같은_날은_한_구간으로_묶인다() {
        val times = listOf(
            at(seoul, 2026, 9, 13, 23, 0),
            at(seoul, 2026, 9, 13, 9, 0),
            at(seoul, 2026, 9, 13, 0, 0),
        )
        val sections = DayBucket.sectionsOf(times, seoul)

        assertEquals(1, sections.size)
        assertEquals(0, sections[0].firstIndex)
        assertEquals(3, sections[0].count)
        assertEquals(13, sections[0].day.dayOfMonth)
    }

    /**
     * **구간의 자리가 원래 목록의 자리와 맞아야 한다.** 이것이 어긋나면 화면에서
     * 사진을 눌렀을 때 다른 사진이 열린다 — 눈으로는 잡기 어려운 종류의 결함이다.
     */
    @Test
    fun 구간이_원래_목록의_자리를_그대로_가리킨다() {
        val times = listOf(
            at(seoul, 2026, 9, 13, 12, 0),
            at(seoul, 2026, 9, 13, 9, 0),
            at(seoul, 2026, 9, 12, 20, 0),
            at(seoul, 2026, 9, 10, 8, 0),
            at(seoul, 2026, 9, 10, 7, 0),
            at(seoul, 2025, 12, 31, 23, 0),
        )
        val sections = DayBucket.sectionsOf(times, seoul)

        assertEquals(4, sections.size)
        assertEquals(listOf(0, 2, 3, 5), sections.map { it.firstIndex })
        assertEquals(listOf(2, 1, 2, 1), sections.map { it.count })
        assertEquals(times.size, sections.sumOf { it.count })

        for (s in sections) {
            for (i in s.firstIndex until s.firstIndex + s.count) {
                assertTrue(s.day.contains(times[i]), "구간 ${s.firstIndex} 의 $i 번째가 그 날이 아니다")
            }
        }
    }

    @Test
    fun 해가_바뀌는_경계에서_구간이_갈린다() {
        val times = listOf(
            at(seoul, 2026, 1, 1, 0, 30),
            at(seoul, 2025, 12, 31, 23, 30),
        )
        val sections = DayBucket.sectionsOf(times, seoul)

        assertEquals(2, sections.size)
        assertEquals(2026, sections[0].day.year)
        assertEquals(2025, sections[1].day.year)
    }

    /**
     * 정렬되지 않은 목록은 **이웃한 것만** 묶는다. 같은 날이 두 구간으로 갈리더라도
     * 목록이 말하는 그대로라, 화면이 목록에 없는 순서를 지어내지는 않는다.
     */
    @Test
    fun 정렬되지_않으면_같은_날이_여러_구간이_된다() {
        val times = listOf(
            at(seoul, 2026, 9, 13, 12, 0),
            at(seoul, 2026, 9, 12, 12, 0),
            at(seoul, 2026, 9, 13, 11, 0),
        )
        val sections = DayBucket.sectionsOf(times, seoul)

        assertEquals(3, sections.size)
        assertEquals(listOf(13, 12, 13), sections.map { it.day.dayOfMonth })
    }
}
