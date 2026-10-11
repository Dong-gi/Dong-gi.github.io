package io.github.donggi.iroirobrowser.core.media

/**
 * 가로채기(MSE)가 받은 덩이의 앞이 **초기 조각**인가 — 그렇다면 그 길이. 재생기는 화질을 바꾸거나 처음 시작할 때 초기 조각을 넣는다 — 그 자리에서
 * 새 덩이(run)를 열어야 덩이마다 홀로 읽힌다(조각들은 그 앞의 초기 조각으로만 읽힌다).
 *
 * - MP4(`video/mp4`·`audio/mp4`): 맨 위 상자를 걷는다 — `ftyp`·`moov`(와 그 사이의 것)가 초기 조각, `moof`·`styp`·`sidx`·`emsg`·`mdat`·`prft`
 *   부터가 조각. 첫 상자가 `ftyp`·`moov` 가 아니거나 `moov` 가 없으면 초기 조각이 아니다(0).
 * - WebM(`…/webm`): EBML 머리(`1A 45 DF A3`)로 시작하면 첫 Cluster(`1F 43 B6 75`) 앞까지(없으면 덩이 전체).
 * - 그 밖(`audio/mpeg`·`audio/aac` 의 바이트 흐름)은 초기 조각이 없다(0).
 */
object MseSegments {

    fun initLength(mime: String, d: ByteArray, offset: Int = 0): Int {
        val n = d.size - offset
        if (n < 8) return 0
        if (mime.contains("webm", ignoreCase = true)) {
            if (!(u8(d, offset) == 0x1A && u8(d, offset + 1) == 0x45 && u8(d, offset + 2) == 0xDF && u8(d, offset + 3) == 0xA3)) return 0
            for (i in offset + 4 until d.size - 3) {
                if (u8(d, i) == 0x1F && u8(d, i + 1) == 0x43 && u8(d, i + 2) == 0xB6 && u8(d, i + 3) == 0x75) return i - offset
            }
            return n
        }
        if (!mime.contains("mp4", ignoreCase = true)) return 0
        var p = offset
        var sawMoov = false
        while (p + 8 <= d.size) {
            var size = (u8(d, p).toLong() shl 24) or (u8(d, p + 1).toLong() shl 16) or (u8(d, p + 2).toLong() shl 8) or u8(d, p + 3).toLong()
            val type = String(d, p + 4, 4, Charsets.ISO_8859_1)
            if (type in MEDIA_BOXES) break
            if (p == offset && type != "ftyp" && type != "moov") return 0
            if (type == "moov") sawMoov = true
            if (size == 1L && p + 16 <= d.size) {
                size = 0
                for (k in 8 until 16) size = (size shl 8) or u8(d, p + k).toLong()
            }
            if (size < 8) break
            if (p + size > d.size) {
                // 덩이가 상자 중간에서 끝났다 — 초기 조각이 덩이 끝까지 이어진다고 본다.
                p = d.size
                break
            }
            p += size.toInt()
        }
        return if (sawMoov) minOf(p, d.size) - offset else 0
    }

    private val MEDIA_BOXES = setOf("moof", "styp", "sidx", "emsg", "mdat", "prft")

    private fun u8(d: ByteArray, i: Int) = d[i].toInt() and 0xFF
}
