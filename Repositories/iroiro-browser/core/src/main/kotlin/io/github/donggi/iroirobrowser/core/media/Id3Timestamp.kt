package io.github.donggi.iroirobrowser.core.media

/**
 * HLS 의 **묶음 소리**(packed audio — `.aac`·`.ac3` 조각)는 그릇에 시각이 없어 조각 앞의 ID3 태그에 첫 표본의 MPEG-2 시각(90 kHz)을 싣는다
 * (HLS 명세 3.4 — `PRIV` 틀, 소유자 `com.apple.streaming.transportStreamTimestamp`, 8 바이트 빅 엔디언의 아래 33 비트). 영상 TS 의 PTS 와
 * 같은 시계라 이것으로 소리와 영상의 시작을 맞춘다.
 */
object Id3Timestamp {

    private const val OWNER = "com.apple.streaming.transportStreamTimestamp"

    /** 첫 ID3 태그의 시각(마이크로초). 태그·틀이 없거나 깨졌으면 null. */
    fun firstTimestampUs(data: ByteArray): Long? {
        if (data.size < 10 || data[0] != 'I'.code.toByte() || data[1] != 'D'.code.toByte() || data[2] != '3'.code.toByte()) return null
        val major = data[3].toInt()
        if (major !in 2..4) return null
        val end = minOf(10L + syncsafe(data, 6), data.size.toLong()).toInt()
        var p = 10
        if (data[5].toInt() and 0x40 != 0) {
            // 확장 머리 — 2.4 는 제 길이를 포함한 syncsafe, 2.3 은 제 길이를 뺀 보통 정수.
            if (p + 4 > end) return null
            p += if (major == 4) syncsafe(data, p) else int32(data, p) + 4
        }
        val idLength = if (major == 2) 3 else 4
        val headerLength = if (major == 2) 6 else 10
        while (p + headerLength <= end) {
            if (data[p].toInt() == 0) break // 채움
            val id = String(data, p, idLength, Charsets.ISO_8859_1)
            val size = when (major) {
                2 -> ((data[p + 3].toInt() and 0xFF) shl 16) or ((data[p + 4].toInt() and 0xFF) shl 8) or (data[p + 5].toInt() and 0xFF)
                3 -> int32(data, p + 4)
                else -> syncsafe(data, p + 4)
            }
            val body = p + headerLength
            if (size <= 0 || body + size > end) return null
            if (id == "PRIV" || id == "PRV") {
                var nul = body
                while (nul < body + size && data[nul].toInt() != 0) nul++
                val owner = String(data, body, nul - body, Charsets.ISO_8859_1)
                if (owner == OWNER && body + size - (nul + 1) >= 8) {
                    var v = 0L
                    for (k in 0 until 8) v = (v shl 8) or (data[nul + 1 + k].toLong() and 0xFF)
                    val pts = v and 0x1FFFFFFFFL
                    return pts * 100 / 9
                }
            }
            p = body + size
        }
        return null
    }

    private fun syncsafe(d: ByteArray, at: Int): Int =
        ((d[at].toInt() and 0x7F) shl 21) or ((d[at + 1].toInt() and 0x7F) shl 14) or ((d[at + 2].toInt() and 0x7F) shl 7) or
            (d[at + 3].toInt() and 0x7F)

    private fun int32(d: ByteArray, at: Int): Int =
        ((d[at].toInt() and 0xFF) shl 24) or ((d[at + 1].toInt() and 0xFF) shl 16) or ((d[at + 2].toInt() and 0xFF) shl 8) or
            (d[at + 3].toInt() and 0xFF)
}
