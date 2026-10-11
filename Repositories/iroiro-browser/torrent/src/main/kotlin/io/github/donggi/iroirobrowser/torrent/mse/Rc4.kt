package io.github.donggi.iroirobrowser.torrent.mse

import java.nio.ByteBuffer

/**
 * RC4 — MSE/PE 가 정한 흐름 암호. **직접 짓는다** — 안드로이드의 보안 제공자에는 `ARCFOUR` 가 없을 수 있다(약한 암호라 빠지는 중이다).
 * MSE 는 키 흐름의 앞 [drop] 바이트(1024)를 버린다. 같은 객체로 이어 부르면 흐름이 이어진다.
 */
class Rc4(key: ByteArray, drop: Int = 0) {
    private val s = IntArray(256) { it }
    private var i = 0
    private var j = 0

    init {
        require(key.isNotEmpty() && key.size <= 256)
        var jj = 0
        for (ii in 0 until 256) {
            jj = (jj + s[ii] + (key[ii % key.size].toInt() and 0xFF)) and 0xFF
            val t = s[ii]
            s[ii] = s[jj]
            s[jj] = t
        }
        if (drop > 0) skip(drop)
    }

    private fun next(): Int {
        i = (i + 1) and 0xFF
        j = (j + s[i]) and 0xFF
        val t = s[i]
        s[i] = s[j]
        s[j] = t
        return s[(s[i] + s[j]) and 0xFF]
    }

    fun skip(n: Int) {
        repeat(n) { next() }
    }

    /** 제자리에서 암호화·복호(같은 일이다). */
    fun process(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        for (k in offset until offset + length) data[k] = (data[k].toInt() xor next()).toByte()
    }

    /** 버퍼의 [from] 부터 [until] 까지를 제자리에서. 위치는 바꾸지 않는다. */
    fun process(buf: ByteBuffer, from: Int, until: Int) {
        if (buf.hasArray()) {
            process(buf.array(), buf.arrayOffset() + from, until - from)
        } else {
            for (k in from until until) buf.put(k, (buf.get(k).toInt() xor next()).toByte())
        }
    }
}
