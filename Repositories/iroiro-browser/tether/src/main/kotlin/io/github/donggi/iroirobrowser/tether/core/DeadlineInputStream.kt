package io.github.donggi.iroirobrowser.tether.core

import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * 머리(SOCKS5 의 인사·요청, HTTP 의 요청 머리)를 다 받기까지의 **전체 마감**.
 *
 * `soTimeout` 은 읽기 **하나**의 시한이다 — 읽기가 될 때마다 시한이 새로 걸리므로, 그것만으로는 몇 초에 한 바이트씩 흘리는 손님이 일꾼 하나와
 * 스레드 예산을 끝없이 쥔다(문서는 '머리를 다 받기까지 10초' 라 적었지만 코드는 그렇지 않았다). 그래서 읽기마다 **남은 시간**을 soTimeout
 * 으로 다시 걸고, 다 썼으면 읽지 않고 [SocketTimeoutException] 을 던진다(일꾼이 끝나고 연결이 닫힌다).
 *
 * 머리를 다 읽으면 [finish] — 그 뒤로는 손대지 않고 그대로 넘긴다(부르는 쪽이 중계·본문의 soTimeout 을 정한다). 버퍼로 감싸 쓴다 — 바이트
 * 하나마다 soTimeout 을 거는 시스템 호출이 들지 않게.
 */
internal class DeadlineInputStream(
    private val socket: Socket,
    private val source: InputStream,
    timeoutMs: Long,
    private val nowMs: () -> Long,
) : InputStream() {

    private val deadlineMs = nowMs() + timeoutMs

    @Volatile
    private var armed = true

    /** 머리를 다 읽었다 — 마감을 거둔다. */
    fun finish() {
        armed = false
    }

    override fun read(): Int {
        arm()
        return source.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        arm()
        return source.read(b, off, len)
    }

    override fun available(): Int = source.available()

    override fun close() = source.close()

    private fun arm() {
        if (!armed) return
        val left = deadlineMs - nowMs()
        if (left <= 0) throw SocketTimeoutException("머리를 다 받기까지의 시간이 지났다")
        socket.soTimeout = left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
