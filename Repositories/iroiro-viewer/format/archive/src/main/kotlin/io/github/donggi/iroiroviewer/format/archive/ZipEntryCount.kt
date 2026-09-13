package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

/**
 * ZIP 이 **스스로 적어 둔 엔트리 개수**를 읽는다. 목록을 만들기 전에.
 *
 * ## 왜 필요한가
 *
 * commons-compress 의 `ZipFile` 은 생성자에서 중앙 디렉터리를 전부 읽어 객체로 만든다.
 * 엔트리 백만 개짜리 작은 파일 하나로 힙이 넘어갈 수 있으므로 **열기 전에** 개수를 알아야 한다.
 *
 * ## 예전에 여기 있던 것이 무엇을 했는가
 *
 * 처음에는 `파일크기 / 46`(중앙 디렉터리 레코드의 최소 길이)을 **상계**로 구해
 * 그것을 개수 상한과 견줬다. 그런데 상계는 "이만큼까지 있을 수 있다" 는 뜻이라,
 * 460,000바이트만 넘으면 **엔트리가 3개인 zip 도 '10,000개를 넘는다'** 며 거절했다.
 * 실측으로 확인한 값이다 — 419,911바이트는 열리고 461,869바이트는 거절됐다.
 * 2단계부터 8단계 시작까지 **실사용 크기의 zip 을 거의 다 못 열고 있었다.**
 *
 * 상계로는 '너무 많다' 를 판정할 수 없다. 그래서 추측하지 않고 **적힌 수를 읽는다.**
 *
 * ## 읽는 법
 *
 * 끝에서부터 EOCD(`50 4B 05 06`)를 찾는다. 주석이 최대 65,535바이트라 뒤 64 KiB +
 * 22바이트만 보면 된다. 개수 칸이 `0xFFFF` 면 ZIP64 이므로 바로 앞의 로케이터
 * (`50 4B 06 07`)를 따라 ZIP64 EOCD(`50 4B 06 06`)에서 8바이트 값을 읽는다.
 */
internal object ZipEntryCount {

    /** 적힌 엔트리 수. 찾지 못하면 null — **그때는 추측하지 않는다.** */
    fun read(source: DocumentSource): Long? {
        val channel = source.openChannel() ?: return null
        return channel.use { read(it) }
    }

    private fun read(channel: SeekableByteChannel): Long? {
        val size = channel.size()
        if (size < EOCD_MIN) return null

        val tailLen = minOf(size, (MAX_COMMENT + EOCD_MIN).toLong()).toInt()
        val tailStart = size - tailLen
        val tail = readAt(channel, tailStart, tailLen) ?: return null

        // 끝에서부터 찾는다. 주석 안에 EOCD 서명과 같은 바이트가 들어 있을 수 있어서,
        // **주석 길이 칸이 남은 바이트와 맞는 자리**만 진짜로 본다.
        var at = tail.size - EOCD_MIN
        while (at >= 0) {
            if (matches(tail, at, EOCD_SIG)) {
                val commentLen = u16(tail, at + 20)
                if (at + EOCD_MIN + commentLen == tail.size) {
                    val total = u16(tail, at + 10).toLong()
                    if (total != 0xFFFFL) return total
                    return readZip64(channel, tail, at, tailStart)
                }
            }
            at--
        }
        return null
    }

    /**
     * ZIP64 의 진짜 개수.
     *
     * 로케이터는 EOCD **바로 앞** 20바이트에 있다. 그것이 가리키는 자리에서 ZIP64 EOCD 를
     * 읽어 오프셋 32의 8바이트를 취한다. 하나라도 어긋나면 null 을 돌려주고 추측하지 않는다.
     */
    private fun readZip64(
        channel: SeekableByteChannel,
        tail: ByteArray,
        eocdAt: Int,
        tailStart: Long,
    ): Long? {
        val locatorAt = eocdAt - ZIP64_LOCATOR_LEN
        if (locatorAt < 0 || !matches(tail, locatorAt, ZIP64_LOCATOR_SIG)) return null
        val z64Offset = u64(tail, locatorAt + 8)
        if (z64Offset < 0 || z64Offset >= tailStart + tail.size) return null

        val head = readAt(channel, z64Offset, ZIP64_EOCD_MIN) ?: return null
        if (!matches(head, 0, ZIP64_EOCD_SIG)) return null
        val total = u64(head, 32)
        return if (total < 0) null else total
    }

    private fun readAt(channel: SeekableByteChannel, position: Long, length: Int): ByteArray? {
        if (position < 0 || length <= 0) return null
        val buf = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(position)
        var read = 0
        while (read < length) {
            val n = channel.read(buf)
            if (n <= 0) break
            read += n
        }
        return if (read == length) buf.array() else null
    }

    private fun matches(bytes: ByteArray, at: Int, sig: ByteArray): Boolean {
        if (at < 0 || at + sig.size > bytes.size) return false
        return sig.indices.all { bytes[at + it] == sig[it] }
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u64(b: ByteArray, at: Int): Long {
        if (at + 8 > b.size) return -1
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        // 부호 있는 Long 을 넘는 값은 다룰 수 없다. 그런 아카이브는 어차피 상한에 걸린다.
        return if (v < 0) Long.MAX_VALUE else v
    }

    private val EOCD_SIG = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
    private val ZIP64_LOCATOR_SIG = byteArrayOf(0x50, 0x4B, 0x06, 0x07)
    private val ZIP64_EOCD_SIG = byteArrayOf(0x50, 0x4B, 0x06, 0x06)

    /** 주석이 없는 EOCD 의 길이. */
    private const val EOCD_MIN = 22

    /** ZIP 주석의 최대 길이. 개수 칸이 `u16` 이라 그 이상은 불가능하다. */
    private const val MAX_COMMENT = 0xFFFF

    private const val ZIP64_LOCATOR_LEN = 20

    /** ZIP64 EOCD 에서 우리가 보는 자리까지의 최소 길이(오프셋 32 + 8). */
    private const val ZIP64_EOCD_MIN = 56
}
