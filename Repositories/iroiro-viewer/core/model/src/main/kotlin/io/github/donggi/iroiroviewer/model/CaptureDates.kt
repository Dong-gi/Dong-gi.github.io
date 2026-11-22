package io.github.donggi.iroiroviewer.model

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * 사진·영상이 **찍힌 시각**을 메타데이터의 글에서 읽는다. 갤러리가 날짜로 묶는 데 쓴다.
 *
 * 값을 못 믿으면 `null` 이다 — 부르는 쪽이 수정일로 돌아간다. 틀린 날짜로 묶는 것보다 수정일로 묶는 것이 낫다:
 * 수정일은 적어도 '이 파일이 이 기기에 온 날' 이라는 뜻을 갖는다.
 *
 * ## 왜 순수 함수인가
 *
 * 카메라·앱마다 이 글을 제멋대로 쓴다(빈 칸, `0000:00:00 00:00:00`, 날짜 구분이 `-`, 끝의 NUL). 기기에서 그 표본을 다 모을 수는
 * 없고, JVM 시험은 한 줄씩 답한다.
 */
object CaptureDates {

    /** 이보다 앞선 해는 믿지 않는다. 0년(`0000`)은 ISO 로는 올바른 해라 엄격 해석이 걸러 주지 않는다. */
    const val MIN_YEAR = 1800

    /** EXIF 2.3 의 날짜 꼴. `uuuu` 와 STRICT 라야 `02-30`·`13월` 이 걸린다(`yyyy` 는 연대가 없으면 STRICT 에서 늘 실패한다). */
    private val EXIF: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)

    private val OFFSET = Regex("[+-]\\d{2}:\\d{2}")

    /** `MediaMetadataRetriever.METADATA_KEY_DATE` 의 꼴 — `20230115T123045.000Z`. 소수 초와 `Z` 는 없을 수도 있다. */
    private val VIDEO = Regex("(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})(?:\\.\\d+)?Z?")

    /**
     * EXIF `DateTimeOriginal`(+ `OffsetTimeOriginal`)을 에포크 밀리초로.
     *
     * 시차 칸이 올바르면 그것으로, 없거나 이상하면 [zone](기기 시간대)으로 읽는다 — EXIF 의 날짜는 **그 자리의 벽시계**이고, 시차 칸이
     * 없으면 어느 시계인지 모른다. 사진은 대개 사는 곳에서 찍으므로 기기 시간대가 가장 덜 틀린다.
     *
     * 날짜 구분이 `-` 인 것(`2023-01-15 12:30:45` — 몇몇 앱이 그렇게 쓴다)과 `T` 사이도 받는다. 끝의 공백·NUL 은 떼지만 그 밖의
     * 꼬리(소수 초 등)가 붙으면 버린다 — 모양을 짐작해 받아 주면 틀린 날짜를 믿게 된다.
     */
    fun exifMillis(dateTime: String?, offset: String?, zone: ZoneId): Long? {
        val raw = dateTime?.trim { it <= ' ' } ?: return null
        if (raw.length != 19) return null
        val chars = raw.toCharArray()
        if (chars[4] == '-' && chars[7] == '-') {
            chars[4] = ':'
            chars[7] = ':'
        }
        if (chars[10] == 'T') chars[10] = ' '
        val local = try {
            LocalDateTime.parse(String(chars), EXIF)
        } catch (e: DateTimeParseException) {
            return null
        }
        if (local.year < MIN_YEAR) return null
        val off = offsetOf(offset)
        return if (off != null) local.toInstant(off).toEpochMilli() else local.atZone(zone).toInstant().toEpochMilli()
    }

    /** `+09:00` 꼴만. 범위 밖(`+19:00`)·빈 칸(`   :  `)은 `null`. */
    fun offsetOf(offset: String?): ZoneOffset? {
        val s = offset?.trim { it <= ' ' } ?: return null
        if (!OFFSET.matches(s)) return null
        return try {
            ZoneOffset.of(s)
        } catch (e: DateTimeException) {
            null
        }
    }

    /**
     * 영상의 날짜 글(`MediaMetadataRetriever.METADATA_KEY_DATE`)을 에포크 밀리초로. **UTC 로 읽는다** — 그 값은 MP4 `mvhd` 의 만든
     * 시각이고 MP4 는 UTC 로 적는다(`Z` 가 없어도 같다). 그 칸에 그 자리의 벽시계를 적는 카메라(일부 액션캠 — 확인하지 못했다)의
     * 영상은 시차만큼 어긋나 밤늦게 찍은 것이 다른 날로 묶일 수 있다.
     *
     * 1970 년 이전과 정확히 0 은 버린다 — 시각을 적지 않은 MP4 는 칸이 0 이라 **1904-01-01**(MP4 의 기준일)로 나오고, 0 을 1970 기준으로
     * 적는 쓰개도 있다.
     */
    fun videoMillis(date: String?): Long? {
        val s = date?.trim { it <= ' ' } ?: return null
        val m = VIDEO.matchEntire(s) ?: return null
        val (y, mo, d, h, mi, se) = m.destructured
        val millis = try {
            LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), se.toInt())
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: DateTimeException) {
            return null
        }
        return if (millis > 0) millis else null
    }
}
