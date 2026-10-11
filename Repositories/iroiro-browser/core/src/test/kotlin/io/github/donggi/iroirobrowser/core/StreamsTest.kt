package io.github.donggi.iroirobrowser.core

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamsTest {

    private val data = ByteArray(1000) { (it % 251).toByte() }

    @Test
    fun 범위만_읽는다() {
        val part = Streams.slice(ByteArrayInputStream(data), 100, 50).readBytes()
        assertContentEquals(data.copyOfRange(100, 150), part)
        // 끝까지 맞물린 범위.
        assertContentEquals(data.copyOfRange(900, 1000), Streams.slice(ByteArrayInputStream(data), 900, 100).readBytes())
        assertEquals(0, Streams.slice(ByteArrayInputStream(data), 10, 0).readBytes().size)
    }

    @Test
    fun 범위가_몸_밖이면_끝을_알린다() {
        assertFailsWith<EOFException> { Streams.slice(ByteArrayInputStream(data), 950, 100).readBytes() }
        assertFailsWith<EOFException> { Streams.slice(ByteArrayInputStream(data), 2000, 10).readBytes() }
    }

    @Test
    fun 자리는_Long_으로_센다() {
        // 앞 판은 offset.toInt() 로 잘라 2 GB 넘는 자리에서 음수가 됐다. 3 GB 를 건너뛰고(만들지 않고 세기만 한다) 그 뒤 4 바이트를 읽는다.
        val start = 3L * 1024 * 1024 * 1024
        val huge = CountingStream(start + 10)
        val part = Streams.slice(huge, start, 4).readBytes()
        assertContentEquals(byteArrayOf(at(start), at(start + 1), at(start + 2), at(start + 3)), part)
    }

    @Test
    fun skip_이_0_을_주는_흐름도_건너뛴다() {
        val stubborn = object : InputStream() {
            private var i = 0
            override fun read(): Int = if (i < data.size) data[i++].toInt() and 0xFF else -1
            override fun skip(n: Long): Long = 0
        }
        assertContentEquals(data.copyOfRange(10, 20), Streams.slice(stubborn, 10, 10).readBytes())
    }

    @Test
    fun 상한을_넘으면_끊는다() {
        assertContentEquals(data, Streams.readAll(ByteArrayInputStream(data), 1000))
        assertFailsWith<Streams.TooLarge> { Streams.readAll(ByteArrayInputStream(data), 999) }
    }

    private fun at(position: Long): Byte = (position % 251).toByte()

    /** [size] 바이트짜리 흐름 — 바이트는 자리로 정한다(메모리에 두지 않는다). */
    private inner class CountingStream(private val size: Long) : InputStream() {
        private var position = 0L

        override fun read(): Int = if (position < size) at(position++).toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= size) return -1
            val n = minOf(len.toLong(), size - position).toInt()
            for (k in 0 until n) b[off + k] = at(position + k)
            position += n
            return n
        }

        override fun skip(n: Long): Long {
            val step = minOf(n, size - position).coerceAtLeast(0)
            position += step
            return step
        }
    }
}
