package io.github.donggi.iroiroviewer.settings

import androidx.annotation.StringRes
import io.github.donggi.iroiroviewer.data.ReaderAppearance
import kotlin.math.floor

/**
 * 고르는 값의 차례와 이름. **값(저장되는 숫자)과 이름(화면의 글)을 한 표에 둔다** — 화면이 `when` 으로 따로 가르면 값이
 * 하나 늘 때 대화상자와 요약 줄 가운데 한쪽만 고쳐진다.
 */
internal object SettingsOptions {

    data class Option(val code: Int, @param:StringRes val label: Int)

    /** `ComicProgressEntity.readDirection` 과 같은 값. */
    val comicDirections = listOf(
        Option(0, R.string.settings_direction_ltr),
        Option(1, R.string.settings_direction_rtl),
        Option(2, R.string.settings_direction_vertical),
    )

    val readerMargins = listOf(
        Option(ReaderAppearance.MARGIN_NARROW, R.string.settings_margin_narrow),
        Option(ReaderAppearance.MARGIN_NORMAL, R.string.settings_margin_normal),
        Option(ReaderAppearance.MARGIN_WIDE, R.string.settings_margin_wide),
    )

    val readerThemes = listOf(
        Option(ReaderAppearance.THEME_SYSTEM, R.string.settings_theme_system),
        Option(ReaderAppearance.THEME_LIGHT, R.string.settings_theme_light),
        Option(ReaderAppearance.THEME_DARK, R.string.settings_theme_dark),
        Option(ReaderAppearance.THEME_SEPIA, R.string.settings_theme_sepia),
    )

    /** 모르는 값(옛 판이 적은 것)이면 첫 항목 — 저장소가 이미 범위를 누르지만 화면이 그것에 기대지 않는다. */
    fun labelOf(options: List<Option>, code: Int): Int = (options.firstOrNull { it.code == code } ?: options.first()).label

    /** 문서 글자 크기의 한 칸(%). 70~200 을 열세 칸으로 — 슬라이더의 눈금과 저장되는 값이 같은 칸에 선다. */
    const val READER_FONT_STEP = 10

    /**
     * 슬라이더가 준 실수를 칸에 붙인다. 슬라이더는 눈금이 있어도 끄는 도중에 칸 사이의 값을 준다 — 그 값을 그대로
     * 반올림하면(`roundToInt` 는 짝수 쪽으로 간다) 0.5 에서 칸이 흔들린다. `floor(x + 0.5)` 로 적는다(CLAUDE.md 함정).
     */
    fun snap(raw: Float, min: Int, max: Int, step: Int): Int {
        val steps = floor((raw - min) / step + 0.5f).toInt()
        return (min + steps * step).coerceIn(min, max)
    }

    /** 칸 사이의 눈금 수(Material 3 `Slider.steps` — 양 끝을 뺀 수). */
    fun sliderSteps(min: Int, max: Int, step: Int): Int = ((max - min) / step - 1).coerceAtLeast(0)
}
