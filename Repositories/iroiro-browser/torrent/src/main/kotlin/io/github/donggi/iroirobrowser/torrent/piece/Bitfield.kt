package io.github.donggi.iroirobrowser.torrent.piece

/** 조각 표 — 와이어의 bitfield 와 같은 비트 차례(첫 바이트의 높은 비트가 조각 0). */
class Bitfield(val size: Int) {
    private val bytes = ByteArray((size + 7) / 8)
    var count: Int = 0
        private set

    operator fun get(i: Int): Boolean = bytes[i ushr 3].toInt() and (0x80 ushr (i and 7)) != 0

    fun set(i: Int) {
        if (get(i)) return
        bytes[i ushr 3] = (bytes[i ushr 3].toInt() or (0x80 ushr (i and 7))).toByte()
        count++
    }

    fun clear(i: Int) {
        if (!get(i)) return
        bytes[i ushr 3] = (bytes[i ushr 3].toInt() and (0x80 ushr (i and 7)).inv()).toByte()
        count--
    }

    fun setAll() {
        for (i in 0 until size) set(i)
    }

    val isComplete: Boolean get() = count == size

    fun toBytes(): ByteArray = bytes.copyOf()

    fun copy(): Bitfield = fromBytes(size, bytes)!!

    companion object {
        /**
         * 와이어·이어받기 자료의 바이트. 길이가 맞지 않거나 **남는 비트가 켜져 있으면** null — 규약을 어긴 bitfield 다.
         */
        fun fromBytes(size: Int, raw: ByteArray): Bitfield? {
            if (raw.size != (size + 7) / 8) return null
            val spare = raw.size * 8 - size
            if (spare > 0 && raw[raw.size - 1].toInt() and ((1 shl spare) - 1) != 0) return null
            val b = Bitfield(size)
            System.arraycopy(raw, 0, b.bytes, 0, raw.size)
            var c = 0
            for (x in raw) c += Integer.bitCount(x.toInt() and 0xFF)
            b.count = c
            return b
        }
    }
}
