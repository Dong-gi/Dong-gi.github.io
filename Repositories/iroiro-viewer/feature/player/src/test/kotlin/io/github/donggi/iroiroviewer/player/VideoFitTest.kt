package io.github.donggi.iroiroviewer.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 화면 비율 방식과 두 손가락 확대의 셈(`VideoFit`). */
class VideoFitTest {

    private val eps = 0.01f
    private fun assertSize(w: Float, h: Float, s: VideoFit.Size) {
        assertEquals(w, s.width, eps)
        assertEquals(h, s.height, eps)
    }

    @Test
    fun `맞춤은 폭에 맞춰 보고 넘치면 높이에 맞춘다`() {
        // 세로 폰의 16:9 — 폭에 맞춘다.
        assertSize(1080f, 607.5f, VideoFit.boxOf(AspectMode.FIT, 1080f, 2400f, 16f / 9f))
        // 가로로 눕힌 20:9 폰 — 폭에 맞추면 1350 이 넘치므로 높이에 맞춘다(위아래가 잘리던 결함).
        assertSize(1920f, 1080f, VideoFit.boxOf(AspectMode.FIT, 2400f, 1080f, 16f / 9f))
    }

    @Test
    fun `채움은 화면을 덮고 보이는 몫은 화면이다`() {
        val box = VideoFit.boxOf(AspectMode.FILL, 1080f, 2400f, 16f / 9f)
        assertSize(4266.67f, 2400f, box)
        assertSize(1080f, 2400f, VideoFit.visibleOf(box, 1080f, 2400f))
    }

    @Test
    fun `늘림은 화면 그대로, 고정 비율은 그 비율로 맞춘다`() {
        assertSize(1080f, 2400f, VideoFit.boxOf(AspectMode.STRETCH, 1080f, 2400f, 16f / 9f))
        assertSize(1440f, 1080f, VideoFit.boxOf(AspectMode.R4_3, 2400f, 1080f, 2.35f))
        assertSize(1920f, 1080f, VideoFit.boxOf(AspectMode.R16_9, 2400f, 1080f, 1f))
    }

    @Test
    fun `비율을 모르면 화면 전체다`() {
        assertSize(1080f, 2400f, VideoFit.boxOf(AspectMode.FIT, 1080f, 2400f, 0f))
        assertSize(1080f, 2400f, VideoFit.boxOf(AspectMode.FILL, 1080f, 2400f, Float.NaN))
    }

    @Test
    fun `방식은 차례로 돌아 처음으로 온다`() {
        var m = AspectMode.FIT
        repeat(AspectMode.entries.size) { m = VideoFit.next(m) }
        assertEquals(AspectMode.FIT, m)
        assertEquals(AspectMode.FILL, VideoFit.next(AspectMode.FIT))
    }

    @Test
    fun `확대는 1배에서 4배 사이이고 가장자리를 화면 안으로 끌어오지 않는다`() {
        val box = VideoFit.Size(1080f, 607.5f)
        val big = VideoFit.apply(VideoFit.NO_ZOOM, 10f, 0f, 0f, 0f, 0f, box, 1080f, 2400f)
        assertEquals(VideoFit.MAX_ZOOM, big.scale)
        val small = VideoFit.apply(VideoFit.NO_ZOOM, 0.5f, 0f, 0f, 0f, 0f, box, 1080f, 2400f)
        assertEquals(1f, small.scale)
        // 2배: 폭 2160 — 좌우로 540 까지 옮길 수 있다. 높이 1215 는 화면(2400)보다 작아 가운데에 머문다.
        val z = VideoFit.apply(VideoFit.Zoom(2f), 1f, 5000f, 300f, 0f, 0f, box, 1080f, 2400f)
        assertEquals(540f, z.offsetX, eps)
        assertEquals(0f, z.offsetY, eps)
    }

    @Test
    fun `손가락 밑의 점이 손가락을 따라간다`() {
        val box = VideoFit.Size(1080f, 2400f)
        // 가운데에서 오른쪽으로 200 떨어진 곳을 축으로 2배 — 그 점(q = 200)이 그대로 200 에 남는다: o = 200 − 200·2 = −200.
        val z = VideoFit.apply(VideoFit.NO_ZOOM, 2f, 0f, 0f, 200f, 0f, box, 1080f, 2400f)
        assertEquals(-200f, z.offsetX, eps)
        assertEquals(200f, 200f * z.scale + z.offsetX, eps)
    }

    @Test
    fun `거의 1배로 줄여 놓으면 원래대로 붙는다`() {
        assertTrue(VideoFit.settle(VideoFit.Zoom(1.03f, 10f, 0f)).isIdentity)
        assertEquals(1.5f, VideoFit.settle(VideoFit.Zoom(1.5f, 10f, 0f)).scale)
    }

    @Test
    fun `화면이 돌면 이동을 새 범위로 누른다`() {
        // 가로 2400×1080 에서 16:9 상자(1920×1080)를 3배로 키워 구석까지 민 이동 → 세로 1080×2400 의 상자(1080×607.5)로.
        val moved = VideoFit.Zoom(3f, 1680f, 1080f)
        val portrait = VideoFit.boxOf(AspectMode.FIT, 1080f, 2400f, 16f / 9f)
        val z = VideoFit.clamp(moved, portrait, 1080f, 2400f)
        assertEquals(3f, z.scale)
        assertEquals(1080f, z.offsetX, eps) // (1080·3 − 1080) / 2
        assertEquals(0f, z.offsetY, eps) // 키운 높이 1822.5 가 화면보다 낮아 가운데
        // 범위 안이면 그대로, 확대가 없으면 그대로.
        val inside = VideoFit.Zoom(2f, 100f, 0f)
        assertEquals(inside, VideoFit.clamp(inside, portrait, 1080f, 2400f))
        assertTrue(VideoFit.clamp(VideoFit.NO_ZOOM, portrait, 1080f, 2400f).isIdentity)
    }
}
