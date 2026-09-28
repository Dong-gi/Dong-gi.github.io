package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.data.ReaderAppearance
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.epub.ReaderStyle

/**
 * 설정의 '읽는 모양'([ReaderAppearance])을 **문서의 종류마다** 무엇으로 옮길지 정한다. 순수 함수라 JVM 시험이 답한다.
 *
 * ## 종류마다 다르게 거는 까닭
 *
 * | 종류 | 글자 크기 | 여백 | 바탕 | 왜 |
 * |---|---|---|---|---|
 * | EPUB 흐름 장 · 글(docx·HWP·HWPX) | ○ | ○ | ○ | 흘러서 읽는 글이다 |
 * | EPUB 고정 레이아웃 쪽 | × | × | × | 좌표로 얹은 쪽이라 글자를 키우면 칸을 넘고, 원본의 색이 곧 쪽이다 |
 * | 시트(xlsx) | ○ | × | × | 작은 칸의 글자를 키우는 것은 뜻이 있다(열 폭이 em 이라 함께 는다). 칸의 채움 색은 **값의 뜻**이라 덮지 않고, 여백은 표에 뜻이 없다 |
 * | 슬라이드(pptx) | × | × | × | 슬라이드 한 장이 한 화면에 들도록 길이를 화면 단위로 적었다 — 글자만 키우면 상자를 넘는다 |
 *
 * 글자 크기는 `WebSettings.textZoom` 이 한다(`LockedWebView`). 여백·바탕은 CSS 이고 그것을 만드는 곳은 [ReaderStyle] 하나다.
 */
internal object ReaderLooks {

    /** '보기' 판이 보여 줄 손잡이. */
    enum class Control { FONT, MARGIN, THEME }

    /** 글자 크기 한 칸. */
    const val FONT_STEP = 10

    /** 설정의 바탕 → 실제 바탕. '시스템' 은 기기의 밤 모드를 따른다. */
    fun tone(theme: Int, systemDark: Boolean): ReaderStyle.Tone = when (theme) {
        ReaderAppearance.THEME_LIGHT -> ReaderStyle.Tone.LIGHT
        ReaderAppearance.THEME_DARK -> ReaderStyle.Tone.DARK
        ReaderAppearance.THEME_SEPIA -> ReaderStyle.Tone.SEPIA
        else -> if (systemDark) ReaderStyle.Tone.DARK else ReaderStyle.Tone.LIGHT
    }

    fun margin(value: Int): ReaderStyle.Margin = when (value) {
        ReaderAppearance.MARGIN_NARROW -> ReaderStyle.Margin.NARROW
        ReaderAppearance.MARGIN_WIDE -> ReaderStyle.Margin.WIDE
        else -> ReaderStyle.Margin.NORMAL
    }

    /** `textZoom` 에 넣을 값. 설정이 이미 범위를 누르지만, 손으로 고친 값이 WebView 에 닿지 않게 한 번 더 누른다. */
    fun textZoom(percent: Int): Int =
        percent.coerceIn(ReaderAppearance.MIN_FONT_PERCENT, ReaderAppearance.MAX_FONT_PERCENT)

    /** 한 칸 크게. 끝에 닿으면 그대로. */
    fun larger(a: ReaderAppearance): ReaderAppearance = a.copy(fontPercent = textZoom(stepOf(a.fontPercent) + FONT_STEP))

    /** 한 칸 작게. */
    fun smaller(a: ReaderAppearance): ReaderAppearance = a.copy(fontPercent = textZoom(stepOf(a.fontPercent) - FONT_STEP))

    /** 칸에 맞지 않는 값(설정 화면이 5 단위로 적었다)은 먼저 칸에 맞춘다 — 한 번 누르면 칸 위로 간다. */
    private fun stepOf(percent: Int): Int = percent / FONT_STEP * FONT_STEP

    /** EPUB 의 손잡이. 모든 쪽이 고정 레이아웃이면 없다(고칠 것이 없다). */
    fun controlsForEpub(allFixed: Boolean): Set<Control> =
        if (allFixed) emptySet() else setOf(Control.FONT, Control.MARGIN, Control.THEME)

    fun controlsForFlow(kind: FlowKind): Set<Control> = when (kind) {
        FlowKind.DOCUMENT -> setOf(Control.FONT, Control.MARGIN, Control.THEME)
        FlowKind.SHEETS -> setOf(Control.FONT)
        FlowKind.SLIDES -> emptySet()
    }

    /** 한 장·부분을 그리는 모양. */
    data class Look(
        /** `WebSettings.textZoom`. */
        val textZoom: Int,
        /** 얹을 CSS(위생을 거칠 필요 없는 우리 글). 빈 글이면 얹지 않는다. */
        val css: String,
        /** WebView 와 그 둘레의 바탕(ARGB). */
        val background: Int,
        /** 이 값이 바뀌면 쪽을 다시 읽는다(CSS 가 바뀌었다). 글자 크기는 들지 않는다 — 다시 읽지 않고 건다. */
        val contentKey: Any,
    )

    /** 흐름 문서 부분 하나의 모양. */
    fun forFlow(kind: FlowKind, a: ReaderAppearance, systemDark: Boolean): Look {
        val controls = controlsForFlow(kind)
        val zoom = if (Control.FONT in controls) textZoom(a.fontPercent) else 100
        if (Control.THEME !in controls) return Look(zoom, "", WHITE, kind)
        val tone = tone(a.theme, systemDark)
        val margin = margin(a.margin)
        return Look(zoom, ReaderStyle.flow(margin, tone), ReaderStyle.background(tone), margin to tone)
    }

    /**
     * EPUB 장 하나의 모양. 고정 레이아웃 쪽은 책의 모양 그대로 화면에 맞춘다 — [viewWidth]·[viewHeight] 는 그때만 쓴다
     * (CSS 화소 — 화면 화소 ÷ 밀도).
     */
    fun forEpub(fixed: Boolean, a: ReaderAppearance, systemDark: Boolean, viewWidth: Double, viewHeight: Double): EpubLook {
        if (fixed) {
            return EpubLook(
                style = ReaderStyle.Look(viewWidth = viewWidth, viewHeight = viewHeight),
                textZoom = 100,
                background = ReaderStyle.FIXED_BACKDROP,
                // 화면 크기가 바뀌면(회전) 쪽을 다시 맞춘다.
                contentKey = viewWidth to viewHeight,
            )
        }
        val tone = tone(a.theme, systemDark)
        val margin = margin(a.margin)
        return EpubLook(
            style = ReaderStyle.Look(margin = margin, tone = tone, viewWidth = viewWidth, viewHeight = viewHeight),
            textZoom = textZoom(a.fontPercent),
            background = ReaderStyle.background(tone),
            contentKey = margin to tone,
        )
    }

    /** EPUB 장 하나의 모양. [style] 이 `EpubBook.chapterHtml` 로 건너간다. */
    data class EpubLook(
        val style: ReaderStyle.Look,
        val textZoom: Int,
        val background: Int,
        val contentKey: Any,
    )

    private const val WHITE = 0xFFFFFFFF.toInt()
}
