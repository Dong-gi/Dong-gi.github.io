package io.github.donggi.iroiroviewer.comic

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * 쪽의 바이트를 상한까지 읽는 `readAllBounded` — 2.0.0(minSdk 36)이 손으로 돈 고리를 `readNBytes` 로 바꿨다. 상한의 경계(같으면 받고, 한
 * 바이트라도 넘으면 null)와, 한 번에 조금씩만 주는 흐름(FUSE·해제기)과, 배열 하나에 담을 수 없는 상한을 박는다.
 */
class ReadAllBoundedTest {

    private val bytes = ByteArray(100_000) { (it * 31).toByte() }

    /** 한 번에 [step] 바이트까지만 주는 흐름. */
    private class Trickle(private val inner: InputStream, private val step: Int) : InputStream() {
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, step))
    }

    @Test
    fun 상한보다_작거나_같으면_전부다() {
        assertContentEquals(bytes, ByteArrayInputStream(bytes).readAllBounded(cap = 200_000))
        assertContentEquals(bytes, ByteArrayInputStream(bytes).readAllBounded(cap = bytes.size.toLong()))
        assertContentEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAllBounded(cap = 0))
    }

    @Test
    fun 한_바이트라도_넘으면_null() {
        assertNull(ByteArrayInputStream(bytes).readAllBounded(cap = bytes.size - 1L))
        assertNull(ByteArrayInputStream(byteArrayOf(1)).readAllBounded(cap = 0))
    }

    @Test
    fun 조금씩_주는_흐름도_끝까지_읽는다() {
        assertContentEquals(bytes, Trickle(ByteArrayInputStream(bytes), 7).readAllBounded(cap = bytes.size.toLong()))
        assertNull(Trickle(ByteArrayInputStream(bytes), 7).readAllBounded(cap = bytes.size - 1L))
    }

    @Test
    fun 배열에_담을_수_없는_상한도_던지지_않는다() {
        // 상한을 Int 로 자르다 넘치면 음수 길이로 IllegalArgumentException 이다.
        assertContentEquals(bytes, ByteArrayInputStream(bytes).readAllBounded(cap = Long.MAX_VALUE))
        assertContentEquals(bytes, ByteArrayInputStream(bytes).readAllBounded(cap = Int.MAX_VALUE.toLong()))
        // 음수 상한은 0 으로 — 빈 흐름만 받는다.
        assertNull(ByteArrayInputStream(bytes).readAllBounded(cap = -5L))
        assertContentEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAllBounded(cap = -5L))
    }
}
