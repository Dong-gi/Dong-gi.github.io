package io.github.donggi.iroiroviewer.ui

import android.graphics.ImageDecoder
import io.github.donggi.iroiroviewer.ui.ThumbnailFailures.Cause
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 썸네일 부정 캐시 — **한 번의 메모리 부족이 그 사진의 썸네일을 세션 내내 지우지 않는가.**
 *
 * 예전 캐시는 '파일이 있는데 못 만들었다' 를 전부 영구 실패로 적었다. 사진 여러 장을 빠르게 훑는
 * 동안 한 번 메모리가 빠듯했던 칸은 앱을 껐다 켤 때까지 종류 배지로 남았다. 그 판정을 박는다.
 */
class ThumbnailFailuresTest {

    private var now = 0L
    private val memo = ThumbnailFailures { now }

    @Test
    fun 메모리_부족은_영구가_되지_않는다() {
        // 몇 번을 거듭해도 — 파일이 아니라 그 순간의 사정이다.
        repeat(50) {
            assertFalse(memo.record("k", ThumbnailFailures.causeOf(OutOfMemoryError())))
            assertFalse(memo.isPermanent("k"))
            now += ThumbnailFailures.MAX_BACKOFF_MS
            assertEquals(0L, memo.waitMs("k"), "물러난 시간이 지나면 다시 해 볼 수 있어야 한다")
        }
    }

    @Test
    fun 일시_실패는_물러났다가_다시_한다() {
        memo.record("k", Cause.TRANSIENT)
        assertEquals(ThumbnailFailures.BASE_BACKOFF_MS, memo.waitMs("k"))
        now += 500
        assertEquals(ThumbnailFailures.BASE_BACKOFF_MS - 500, memo.waitMs("k"))
        now += ThumbnailFailures.BASE_BACKOFF_MS
        assertEquals(0L, memo.waitMs("k"))
    }

    @Test
    fun 물러나는_시간은_두_배씩_늘고_10분에서_멈춘다() {
        assertEquals(2_000L, ThumbnailFailures.backoffMs(1))
        assertEquals(4_000L, ThumbnailFailures.backoffMs(2))
        assertEquals(8_000L, ThumbnailFailures.backoffMs(3))
        assertEquals(ThumbnailFailures.MAX_BACKOFF_MS, ThumbnailFailures.backoffMs(40))
        assertEquals(ThumbnailFailures.MAX_BACKOFF_MS, ThumbnailFailures.backoffMs(Int.MAX_VALUE))
        assertEquals(ThumbnailFailures.BASE_BACKOFF_MS, ThumbnailFailures.backoffMs(0))
    }

    @Test
    fun 못_읽는_형식은_영구다() {
        // SVG·TIFF — 플랫폼이 '디코더를 만들지 못했다' 를 이 값으로 준다.
        assertTrue(memo.record("svg", ThumbnailFailures.causeOfDecodeError(ImageDecoder.DecodeException.SOURCE_MALFORMED_DATA)))
        assertTrue(memo.isPermanent("svg"))
        assertEquals(ThumbnailFailures.PERMANENT_WAIT, memo.waitMs("svg"))
        now += ThumbnailFailures.MAX_BACKOFF_MS * 10
        assertEquals(ThumbnailFailures.PERMANENT_WAIT, memo.waitMs("svg"), "영구는 시간이 지나도 영구다")
    }

    @Test
    fun 디코더의_실패_갈래() {
        assertEquals(Cause.PERMANENT, ThumbnailFailures.causeOfDecodeError(ImageDecoder.DecodeException.SOURCE_MALFORMED_DATA))
        // 도중에 끝난 파일 — 받는 중이면 다 받은 뒤 크기가 바뀌어 키가 바뀐다. 이 키의 바이트는 계속 모자란다.
        assertEquals(Cause.PERMANENT, ThumbnailFailures.causeOfDecodeError(ImageDecoder.DecodeException.SOURCE_INCOMPLETE))
        // 디코더가 아니라 읽기가 던졌다.
        assertEquals(Cause.TRANSIENT, ThumbnailFailures.causeOfDecodeError(ImageDecoder.DecodeException.SOURCE_EXCEPTION))
        assertEquals(Cause.UNCERTAIN, ThumbnailFailures.causeOfDecodeError(99))
    }

    @Test
    fun 예외의_갈래() {
        assertEquals(Cause.TRANSIENT, ThumbnailFailures.causeOf(OutOfMemoryError()))
        assertEquals(Cause.TRANSIENT, ThumbnailFailures.causeOf(IOException()))
        assertEquals(Cause.TRANSIENT, ThumbnailFailures.causeOf(FileNotFoundException()))
        assertEquals(Cause.UNCERTAIN, ThumbnailFailures.causeOf(IllegalStateException()))
    }

    @Test
    fun 불확실한_실패는_거듭되면_영구가_된다() {
        // 동영상 — 코덱이 없는 것과 추출기가 잠깐 거절한 것이 같은 예외다.
        repeat(ThumbnailFailures.MAX_UNCERTAIN - 1) {
            assertFalse(memo.record("v", Cause.UNCERTAIN))
            now += ThumbnailFailures.MAX_BACKOFF_MS
        }
        assertTrue(memo.record("v", Cause.UNCERTAIN))
        assertTrue(memo.isPermanent("v"))
    }

    @Test
    fun 메모리_부족이_섞여도_불확실_횟수만_승격을_정한다() {
        repeat(10) { memo.record("v", Cause.TRANSIENT) }
        assertFalse(memo.record("v", Cause.UNCERTAIN))
        assertFalse(memo.isPermanent("v"))
    }

    @Test
    fun 지우면_처음부터다() {
        memo.record("k", Cause.PERMANENT)
        memo.clear("k")
        assertFalse(memo.isPermanent("k"))
        assertEquals(0L, memo.waitMs("k"))
        repeat(5) { memo.record("t", Cause.TRANSIENT) }
        memo.clear("t")
        memo.record("t", Cause.TRANSIENT)
        assertEquals(ThumbnailFailures.BASE_BACKOFF_MS, memo.waitMs("t"), "물러나는 시간도 처음으로 돌아간다")
    }

    @Test
    fun 키마다_따로다() {
        memo.record("a", Cause.PERMANENT)
        assertEquals(0L, memo.waitMs("b"))
        assertFalse(memo.isPermanent("b"))
    }
}
