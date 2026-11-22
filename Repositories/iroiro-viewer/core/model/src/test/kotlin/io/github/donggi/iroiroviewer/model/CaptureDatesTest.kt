package io.github.donggi.iroiroviewer.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 찍힌 시각 읽기. 시간대는 언제나 인자로 준다(시험 기계의 설정에 답이 달리지 않게). */
class CaptureDatesTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun 시차_칸이_없으면_기기_시간대의_벽시계로_읽는다() {
        assertEquals(at(seoul, 2023, 1, 15, 12, 30, 45), CaptureDates.exifMillis("2023:01:15 12:30:45", null, seoul))
        assertEquals(at(newYork, 2023, 1, 15, 12, 30, 45), CaptureDates.exifMillis("2023:01:15 12:30:45", null, newYork))
    }

    @Test
    fun 시차_칸이_있으면_기기_시간대보다_그것이_이긴다() {
        // 도쿄(+09:00)에서 23:30 에 찍은 것을 뉴욕 시간대의 기기가 읽어도 같은 순간이다.
        val expected = at(ZoneOffset.ofHours(9), 2023, 1, 15, 23, 30)
        assertEquals(expected, CaptureDates.exifMillis("2023:01:15 23:30:00", "+09:00", newYork))
        assertEquals(at(ZoneOffset.ofHoursMinutes(-3, -30), 2023, 1, 15, 23, 30),
            CaptureDates.exifMillis("2023:01:15 23:30:00", "-03:30", seoul))
    }

    @Test
    fun 이상한_시차_칸은_없는_것으로_친다() {
        val local = at(seoul, 2023, 1, 15, 12, 0)
        for (bad in listOf("", "   :  ", "+9:00", "+0900", "+19:00", "UTC", "+09:00:00")) {
            assertEquals(local, CaptureDates.exifMillis("2023:01:15 12:00:00", bad, seoul), bad)
        }
    }

    @Test
    fun 빈_값과_영의_날짜와_틀린_날짜는_null() {
        val bad = listOf(
            null, "", "    :  :     :  :  ", "0000:00:00 00:00:00", "0000:01:01 00:00:00",
            "2023:02:30 12:00:00", "2023:13:01 12:00:00", "2023:01:01 24:00:00", "2023:01:01 12:60:00",
            "2023:1:5 1:2:3", "2023/01/15 12:30:45", "garbage", "2023:01:15", "1799:12:31 23:59:59",
        )
        for (s in bad) assertNull(CaptureDates.exifMillis(s, null, seoul), s.toString())
    }

    @Test
    fun 꼬리의_공백과_NUL_은_떼고_다른_꼬리는_버린다() {
        val t = at(seoul, 2023, 1, 15, 12, 30, 45)
        assertEquals(t, CaptureDates.exifMillis("2023:01:15 12:30:45\u0000", null, seoul))
        assertEquals(t, CaptureDates.exifMillis(" 2023:01:15 12:30:45 ", null, seoul))
        assertNull(CaptureDates.exifMillis("2023:01:15 12:30:45.123", null, seoul))
        assertNull(CaptureDates.exifMillis("2023:01:15 12:30:45+09:00", null, seoul))
    }

    @Test
    fun 날짜_구분이_줄표이거나_T_로_이은_것도_받는다() {
        val t = at(seoul, 2023, 1, 15, 12, 30, 45)
        assertEquals(t, CaptureDates.exifMillis("2023-01-15 12:30:45", null, seoul))
        assertEquals(t, CaptureDates.exifMillis("2023:01:15T12:30:45", null, seoul))
        // 한쪽만 줄표면 받지 않는다.
        assertNull(CaptureDates.exifMillis("2023-01:15 12:30:45", null, seoul))
    }

    @Test
    fun 서머타임으로_없는_시각은_뒤로_밀어_읽는다() {
        // 뉴욕 2023-03-12 02:30 은 없다(02:00 → 03:00). 예외 없이 03:30 으로 읽힌다.
        assertEquals(at(newYork, 2023, 3, 12, 3, 30), CaptureDates.exifMillis("2023:03:12 02:30:00", null, newYork))
    }

    @Test
    fun 영상의_날짜는_UTC_로_읽는다() {
        val t = at(ZoneOffset.UTC, 2023, 1, 15, 12, 30, 45)
        assertEquals(t, CaptureDates.videoMillis("20230115T123045.000Z"))
        assertEquals(t, CaptureDates.videoMillis("20230115T123045Z"))
        assertEquals(t, CaptureDates.videoMillis("20230115T123045"))
    }

    @Test
    fun 영상의_빈_날짜와_MP4_기준일과_틀린_꼴은_null() {
        val bad = listOf(
            null, "", "19040101T000000.000Z", "19700101T000000.000Z", "20231301T000000.000Z",
            "20230230T000000.000Z", "2023-01-15T12:30:45Z", "20230115", "20230115T1230.000Z",
        )
        for (s in bad) assertNull(CaptureDates.videoMillis(s), s.toString())
    }
}
