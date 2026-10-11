package io.github.donggi.iroiroviewer.editor

import io.github.donggi.iroiroviewer.editor.PaperSizes.Paper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaperSizesTest {

    @Test
    fun findsTheStandardPaper() {
        assertEquals(PaperSizes.Match(Paper.ISO_A4, landscape = false), PaperSizes.of(210.0, 297.0))
        assertEquals(PaperSizes.Match(Paper.ISO_A4, landscape = false), PaperSizes.of(209.99, 296.995))
        assertEquals(PaperSizes.Match(Paper.JIS_B5, landscape = false), PaperSizes.of(182.0, 257.0))
        assertEquals(PaperSizes.Match(Paper.NA_LETTER, landscape = false), PaperSizes.of(215.9, 279.4))
    }

    @Test
    fun turnsLandscapePages() {
        assertEquals(PaperSizes.Match(Paper.ISO_A4, landscape = true), PaperSizes.of(297.0, 210.0))
        assertEquals(PaperSizes.Match(Paper.ISO_A3, landscape = true), PaperSizes.of(420.0, 297.0))
    }

    @Test
    fun leavesOtherSizesToThePrintDialog() {
        assertNull(PaperSizes.of(100.0, 150.0))
        assertNull(PaperSizes.of(210.0, 290.0))
        assertNull(PaperSizes.of(0.0, 297.0))
        assertNull(PaperSizes.of(Double.NaN, 297.0))
    }
}
