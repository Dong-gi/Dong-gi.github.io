package io.github.donggi.iroiroviewer.editor

import kotlin.math.abs

/**
 * 문서의 쪽 크기(mm) → 인쇄 창에 먼저 고를 표준 용지 — **순수**(JVM 시험). 인쇄 창의 처음 용지는 기기의 지역을 따른다(미국 영어면 레터) —
 * 한글 문서는 거의 A4 라, 문서가 말하는 용지를 먼저 골라 둔다. 사용자는 인쇄 창에서 바꿀 수 있다.
 *
 * 표준이 아니면 null — 인쇄 창의 처음 값을 둔다(사용자 지정 크기는 프린터·PDF 저장이 받지 않을 수 있다).
 */
object PaperSizes {

    /** 세로 방향의 폭·높이(mm). 이름은 안드로이드 `PrintAttributes.MediaSize` 의 상수와 같다([PrintJob] 이 옮긴다). */
    enum class Paper(val widthMm: Double, val heightMm: Double) {
        ISO_A3(297.0, 420.0),
        ISO_A4(210.0, 297.0),
        ISO_A5(148.0, 210.0),
        JIS_B4(257.0, 364.0),
        JIS_B5(182.0, 257.0),
        NA_LETTER(215.9, 279.4),
        NA_LEGAL(215.9, 355.6),
        NA_TABLOID(279.4, 431.8),
    }

    data class Match(val paper: Paper, val landscape: Boolean)

    /** 1 mm 안쪽이면 같은 용지로 친다 — 한글 문서는 크기를 1/7200 인치 정수로 적어 A4 의 297 mm 가 296.99 mm 가 된다. */
    private const val TOLERANCE_MM = 1.0

    fun of(widthMm: Double, heightMm: Double): Match? {
        if (!(widthMm > 0 && heightMm > 0)) return null
        val landscape = widthMm > heightMm
        val short = minOf(widthMm, heightMm)
        val long = maxOf(widthMm, heightMm)
        val paper = Paper.entries.firstOrNull { abs(it.widthMm - short) <= TOLERANCE_MM && abs(it.heightMm - long) <= TOLERANCE_MM }
            ?: return null
        return Match(paper, landscape)
    }
}
