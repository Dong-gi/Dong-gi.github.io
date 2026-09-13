package io.github.donggi.iroiroviewer.io

import java.util.Calendar
import java.util.Formatter
import java.util.Locale

/**
 * 화면에 숫자를 적는 방법.
 *
 * 단위는 **1024 기준**을 쓰되 표기는 KB·MB·GB 로 한다. 안드로이드 사용자가 다른 파일
 * 관리자에서 보던 것과 같아야 "같은 파일인데 크기가 다르게 나온다" 는 혼란이 없다.
 */
object Format {

    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun size(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        // 1000 을 넘으면 소수점이 의미 없고, 10 미만이면 한 자리가 필요하다.
        val digits = when {
            value >= 100 -> 0
            value >= 10 -> 1
            else -> 1
        }
        return String.format(Locale.US, "%.${digits}f %s", value, units[unit])
    }

    /**
     * 오늘이면 시각만, 올해면 월·일, 그 밖에는 연·월·일.
     *
     * 목록에서 가장 자주 보는 것은 '언제쯤인가' 이지 정확한 시각이 아니다. 긴 문자열은
     * 좁은 화면에서 파일 이름을 밀어낸다.
     */
    fun timestamp(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "-"
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val sameYear = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
        val sameDay = sameYear && cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        val sb = StringBuilder()
        Formatter(sb, Locale.KOREA).use { f ->
            when {
                sameDay -> f.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                sameYear -> f.format("%d월 %d일", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                else -> f.format(
                    "%d. %d. %d.",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                )
            }
        }
        return sb.toString()
    }
}
