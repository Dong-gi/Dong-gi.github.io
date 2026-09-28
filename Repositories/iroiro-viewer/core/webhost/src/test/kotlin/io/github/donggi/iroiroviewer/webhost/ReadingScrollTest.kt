package io.github.donggi.iroiroviewer.webhost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 읽던 자리의 비율 계산. WebView 안에서 도는 쪽(`ReaderWebView`)은 계측 없이는 돌릴 수 없으므로, 판단은 전부 여기 있다.
 */
class ReadingScrollTest {

    @Test
    fun 세로로_넘치면_세로_아니면_가로다() {
        assertEquals(ReadingScroll.Axis.VERTICAL, ReadingScroll.axisOf(maxX = 0, maxY = 5000))
        // 넓은 표 하나로 가로로도 조금 넘치는 가로 문서 — 세로로 잰다.
        assertEquals(ReadingScroll.Axis.VERTICAL, ReadingScroll.axisOf(maxX = 40, maxY = 5000))
        // 세로쓰기 장은 세로로 넘치지 않고 가로로만 넘친다.
        assertEquals(ReadingScroll.Axis.HORIZONTAL, ReadingScroll.axisOf(maxX = 9000, maxY = 0))
        assertNull(ReadingScroll.axisOf(0, 0))
    }

    @Test
    fun 오른쪽에서_넘기는_책은_세로로_조금_넘쳐도_가로로_잰다() {
        // 세로쓰기 장에 화면보다 긴 그림 하나 — 세로로 몇십 화소 넘친다. 세로를 먼저 보면 장 전체를 세로로 재고, 첫 줄을
        // 오른쪽 끝에 맞추는 일이 세로 축의 0 으로 떨어져 아무 일도 하지 않는다.
        assertEquals(ReadingScroll.Axis.HORIZONTAL, ReadingScroll.axisOf(maxX = 9000, maxY = 30, rightToLeft = true))
        // 가로쓰기 RTL 책(가로로 넘치지 않는다)은 그대로 세로다.
        assertEquals(ReadingScroll.Axis.VERTICAL, ReadingScroll.axisOf(maxX = 0, maxY = 5000, rightToLeft = true))
        // 왼쪽에서 넘기는 책은 예전 그대로 세로가 먼저다.
        assertEquals(ReadingScroll.Axis.VERTICAL, ReadingScroll.axisOf(maxX = 9000, maxY = 30, rightToLeft = false))
        assertNull(ReadingScroll.axisOf(0, 0, rightToLeft = true))
    }

    @Test
    fun 한_화면에_드는_쪽은_다_본_것이다() {
        // 마지막 장이 짧은 책(판권) — 자리는 언제나 0 이지만 본 몫은 1 이어야 진행이 끝에 닿는다.
        assertEquals(1f, ReadingScroll.seenOf(position = 0, max = 0, extent = 2000, fromEnd = false))
        // 처음: 한 화면만큼 봤다. 범위 = max + extent = 10000.
        assertEquals(0.2f, ReadingScroll.seenOf(position = 0, max = 8000, extent = 2000, fromEnd = false), 1e-6f)
        // 끝까지 내렸다.
        assertEquals(1f, ReadingScroll.seenOf(position = 8000, max = 8000, extent = 2000, fromEnd = false))
        // 세로쓰기 — 오른쪽 끝(스크롤 max)이 처음이다.
        assertEquals(0.2f, ReadingScroll.seenOf(position = 8000, max = 8000, extent = 2000, fromEnd = true), 1e-6f)
        assertEquals(1f, ReadingScroll.seenOf(position = 0, max = 8000, extent = 2000, fromEnd = true))
        // 넘쳐 끄는 순간의 값은 잘라 준다.
        assertEquals(1f, ReadingScroll.seenOf(position = 9000, max = 8000, extent = 2000, fromEnd = false))
    }

    @Test
    fun 오른쪽에서_잴_때는_오른쪽_끝이_처음이다() {
        assertEquals(0f, ReadingScroll.fractionOf(1000, 1000, fromEnd = true))
        assertEquals(1f, ReadingScroll.fractionOf(0, 1000, fromEnd = true))
        assertEquals(0.25f, ReadingScroll.fractionOf(750, 1000, fromEnd = true))
        assertEquals(1000, ReadingScroll.positionOf(0f, 1000, fromEnd = true))
        assertEquals(750, ReadingScroll.positionOf(0.25f, 1000, fromEnd = true))
    }

    @Test
    fun 비율과_자리가_왕복한다() {
        for (max in listOf(1, 7, 999, 12_345, 1_000_000)) {
            for (fromEnd in listOf(false, true)) {
                for (p in listOf(0, max / 3, max / 2, max)) {
                    val f = ReadingScroll.fractionOf(p, max, fromEnd)
                    // 화소 하나 안쪽으로 돌아온다(부동소수 한 번을 지난다).
                    val back = ReadingScroll.positionOf(f, max, fromEnd)
                    assertTrue(kotlin.math.abs(back - p) <= 1, "max=$max p=$p fromEnd=$fromEnd → $back")
                }
            }
        }
    }

    @Test
    fun 범위를_벗어난_값을_잘라_준다() {
        // 넘쳐 끄는 순간의 음수·최대 초과, 퇴화한 비율.
        assertEquals(0f, ReadingScroll.fractionOf(-30, 1000, fromEnd = false))
        assertEquals(1f, ReadingScroll.fractionOf(1030, 1000, fromEnd = false))
        assertEquals(0f, ReadingScroll.fractionOf(500, 0, fromEnd = false))
        assertEquals(1000, ReadingScroll.positionOf(3f, 1000, fromEnd = false))
        assertEquals(0, ReadingScroll.positionOf(Float.NaN, 1000, fromEnd = false))
        assertEquals(0, ReadingScroll.positionOf(0.5f, 0, fromEnd = true))
    }

    @Test
    fun 세로쓰기_책의_장은_오른쪽_끝에서_연다() {
        // E03·E04 의 '첫 줄이 오른쪽 끝에 반쯤 걸린다' — WebView 는 왼쪽 끝(0)에서 시작한다. 오른쪽에서 넘기는 책이면
        // 저장된 자리가 없어도 0(= 오른쪽 끝)을 청한다.
        assertEquals(0f, ReadingScroll.startOf(saved = null, hasFragment = false, rightToLeft = true))
        assertEquals(0f, ReadingScroll.startOf(saved = 0f, hasFragment = false, rightToLeft = true))
        // 가로쓰기 책은 WebView 의 기본값(위 끝)에 맡긴다 — 쓸데없이 옮기지 않는다.
        assertNull(ReadingScroll.startOf(saved = null, hasFragment = false, rightToLeft = false))
        assertNull(ReadingScroll.startOf(saved = 0f, hasFragment = false, rightToLeft = false))
    }

    @Test
    fun 저장된_자리가_이기고_조각은_그보다_이긴다() {
        assertEquals(0.4f, ReadingScroll.startOf(saved = 0.4f, hasFragment = false, rightToLeft = false))
        assertEquals(0.4f, ReadingScroll.startOf(saved = 0.4f, hasFragment = false, rightToLeft = true))
        assertEquals(1f, ReadingScroll.startOf(saved = 7f, hasFragment = false, rightToLeft = false))
        // 각주로 가는 링크 — WebView 가 스스로 그 자리로 간다. 우리가 옮기면 다툰다.
        assertNull(ReadingScroll.startOf(saved = 0.4f, hasFragment = true, rightToLeft = true))
        assertNull(ReadingScroll.startOf(saved = Float.NaN, hasFragment = false, rightToLeft = false))
    }

    @Test
    fun 배치가_끝나고_범위가_그대로면_멈춘다() {
        val r = ReadingScroll.Restore(0.5f)
        // 쪽이 다 읽히기 전에는 범위가 그대로여도 계속한다(그림·글꼴이 뒤에 붙는다).
        assertTrue(r.again(1000, pageFinished = false))
        assertTrue(r.again(1000, pageFinished = false))
        // 다 읽힌 뒤: 범위가 바뀌었다 → 계속, 그대로 두 번 → 멈춘다.
        assertTrue(r.again(4000, pageFinished = true))
        assertTrue(r.again(4000, pageFinished = true))
        assertFalse(r.again(4000, pageFinished = true))
    }

    @Test
    fun 글자_크기를_바꾸면_다시_흐른_뒤에야_멈춘다() {
        // 쪽은 이미 다 읽혔다(onPageFinished 가 다시 오지 않는다). 다시 흐르기가 늦으면 처음 몇 번은 옛 범위 그대로다 —
        // 그것을 '그대로' 로 세어 멈추면 뒤늦은 배치가 자리를 밀어낸다.
        val r = ReadingScroll.Restore(0.5f, awaitChange = true)
        assertTrue(r.again(4000, pageFinished = true))
        assertTrue(r.again(4000, pageFinished = true))
        assertTrue(r.again(4000, pageFinished = true))
        // 이제 다시 흘렀다(글이 커져 길어졌다). 그 뒤로 두 번 그대로면 멈춘다.
        assertTrue(r.again(5200, pageFinished = true))
        assertTrue(r.again(5200, pageFinished = true))
        assertFalse(r.again(5200, pageFinished = true))
    }

    @Test
    fun 다시_흐르지_않는_쪽도_끝내는_멈춘다() {
        // 한 화면에 드는 쪽 — 범위가 끝내 0 이다. 같은 자리로 되풀이해 옮길 뿐이고 상한에서 멈춘다.
        val r = ReadingScroll.Restore(0.3f, awaitChange = true)
        var n = 0
        while (r.again(0, pageFinished = true)) n++
        assertEquals(ReadingScroll.MAX_TRIES - 1, n)
    }

    @Test
    fun 끝없이_되풀이하지_않는다() {
        val r = ReadingScroll.Restore(0.5f)
        var n = 0
        while (r.again(n * 10, pageFinished = false)) n++
        assertEquals(ReadingScroll.MAX_TRIES - 1, n)
    }
}
