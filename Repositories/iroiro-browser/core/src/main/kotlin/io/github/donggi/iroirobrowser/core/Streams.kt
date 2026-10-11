package io.github.donggi.iroirobrowser.core

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 응답의 몸을 **정한 만큼만** 읽는다 — 내려받기가 몸을 통째로 메모리에 올리지 않게. 자리와 길이는 Long 으로 센다(2 GB 넘는 파일의 자리도).
 * `readNBytes`·`skipNBytes`(안드로이드 API 33)를 쓰지 않는다.
 */
object Streams {

    /** 상한을 넘었다 — 몸을 통째로 쥐려다 메모리를 다 쓰지 않게 끊는다. */
    class TooLarge(val limit: Long) : IOException("body larger than $limit bytes")

    /** 끝까지 읽되 [limit] 바이트를 넘으면 [TooLarge](재생목록·열쇠처럼 작아야 할 것). */
    fun readAll(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size().toLong() + n > limit) throw TooLarge(limit.toLong())
            out.write(buffer, 0, n)
        }
    }

    /**
     * [input] 의 [offset] 부터 [length] 바이트만 내주는 흐름 — 범위를 물었는데 서버가 통째로(200) 줄 때 그 범위만 읽는다(앞 판은 몸을 다
     * 읽은 뒤 `offset.toInt()` 로 잘라 2 GB 넘는 자리에서 음수로 뒤집혔다). 그 전에 끝나면 [EOFException] — 범위가 몸 밖이다.
     */
    fun slice(input: InputStream, offset: Long, length: Long): InputStream {
        require(offset >= 0 && length >= 0) { "negative range" }
        return Slice(input, offset, length)
    }

    private class Slice(input: InputStream, private var skip: Long, private var left: Long) : FilterInputStream(input) {

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            skipAhead()
            if (left <= 0) return -1
            if (len == 0) return 0
            val n = `in`.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw EOFException("body ended $left bytes before the range end")
            left -= n
            return n
        }

        override fun skip(n: Long): Long {
            skipAhead()
            val step = `in`.skip(minOf(n, left))
            left -= step
            return step
        }

        override fun available(): Int = minOf(`in`.available().toLong(), left).toInt()

        override fun markSupported(): Boolean = false

        /** 앞자리를 건너뛴다. `skip` 이 0 을 주면(흐름마다 약속이 다르다) 한 바이트 읽어 끝인지 본다. */
        private fun skipAhead() {
            while (skip > 0) {
                val step = `in`.skip(skip)
                if (step > 0) {
                    skip -= step
                    continue
                }
                if (`in`.read() < 0) throw EOFException("body ended before the range start")
                skip--
            }
        }
    }
}
