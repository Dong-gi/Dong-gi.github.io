package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 영상 화면 캡처의 크기와 이름 꼬리(`FrameShot`). */
class FrameShotTest {

    @Test
    fun `정사각 화소는 원본 크기 그대로`() {
        assertEquals(FrameShot.Size(1920, 1080), FrameShot.sizeOf(1920, 1080, 1f, Long.MAX_VALUE))
        assertEquals(FrameShot.Size(1280, 720), FrameShot.sizeOf(1280, 720, 0f, Long.MAX_VALUE))
    }

    @Test
    fun `정사각이 아닌 화소는 좁은 쪽을 늘린다`() {
        assertEquals(FrameShot.Size(768, 576), FrameShot.sizeOf(720, 576, 16f / 15, Long.MAX_VALUE))
        assertEquals(FrameShot.Size(720, 640), FrameShot.sizeOf(720, 480, 0.75f, Long.MAX_VALUE))
    }

    @Test
    fun `상한을 넘으면 비율을 지키며 줄인다`() {
        val s = FrameShot.sizeOf(7680, 4320, 1f, 16_000_000)!!
        assertTrue(s.width.toLong() * s.height <= 16_000_000)
        assertEquals(16.0 / 9, s.width.toDouble() / s.height, 0.01)
    }

    @Test
    fun `크기를 모르면 null`() {
        assertNull(FrameShot.sizeOf(0, 1080, 1f))
        assertNull(FrameShot.sizeOf(1920, -1, 1f))
    }

    @Test
    fun `이름 꼬리는 1000분의 1초까지 적는다`() {
        assertEquals("capture_0m00.000s", FrameShot.tag(0))
        assertEquals("capture_0m12.345s", FrameShot.tag(12_345))
        assertEquals("capture_59m59.999s", FrameShot.tag(3_599_999))
        assertEquals("capture_1h02m03.004s", FrameShot.tag(3_723_004))
        assertEquals("capture_0m00.000s", FrameShot.tag(-5))
        assertEquals("영상_capture_0m12.345s.jpg", NewFiles.derivedName("영상.mp4", FrameShot.tag(12_345), FrameShot.EXTENSION))
    }
}
