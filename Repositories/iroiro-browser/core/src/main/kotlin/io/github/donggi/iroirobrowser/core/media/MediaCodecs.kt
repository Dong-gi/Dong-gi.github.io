package io.github.donggi.iroirobrowser.core.media

import java.util.Locale

/**
 * 화질 창에 보이는 것 — 코덱 이름과 어림한 크기(화질 창에 크기 어림·코덱이 없었다).
 *
 * 코덱 글은 RFC 6381 의 꼴(`avc1.64001f,mp4a.40.2` — HLS 의 `CODECS`·DASH 의 `@codecs`)이다. 자막 코덱(`stpp`·`wvtt`)은 뺀다.
 */
object MediaCodecs {

    /** `avc1.64001f,mp4a.40.2` → `H.264/AAC`. 모르는 코덱은 점 앞의 이름 그대로. 하나도 없으면 null. */
    fun describe(codecs: String?): String? {
        if (codecs.isNullOrBlank()) return null
        val names = codecs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { nameOf(it) }.distinct()
        return names.joinToString("/").ifEmpty { null }
    }

    /** 영상 코덱이 하나라도 있는가 — 없으면 '소리만' 인 화질이다. 코덱을 모르면 null. */
    fun hasVideo(codecs: String?): Boolean? {
        if (codecs.isNullOrBlank()) return null
        return codecs.split(',').any { c -> VIDEO_PREFIXES.any { c.trim().lowercase(Locale.ROOT).startsWith(it) } }
    }

    /**
     * 초당 비트([bitsPerSecond])와 길이([seconds])로 어림한 크기(바이트). 하나라도 모르면 null. HLS 의 `BANDWIDTH` 는 가장 높은 순간의 값이라
     * 실제보다 크게 나온다 — 부르는 쪽이 `AVERAGE-BANDWIDTH` 가 있으면 그것을 준다.
     */
    fun estimateBytes(bitsPerSecond: Long, seconds: Double): Long? =
        if (bitsPerSecond <= 0 || seconds <= 0.0 || !seconds.isFinite()) null else (bitsPerSecond * seconds / 8).toLong()

    private fun nameOf(codec: String): String? {
        val c = codec.lowercase(Locale.ROOT)
        return when {
            c.startsWith("avc1") || c.startsWith("avc3") -> "H.264"
            c.startsWith("hvc1") || c.startsWith("hev1") -> "H.265"
            c.startsWith("dvh1") || c.startsWith("dvhe") -> "Dolby Vision"
            c.startsWith("av01") -> "AV1"
            c.startsWith("vp09") || c == "vp9" -> "VP9"
            c.startsWith("vp08") || c == "vp8" -> "VP8"
            c == "mp4a.69" || c == "mp4a.6b" || c == "mp3" -> "MP3"
            c == "mp4a.a5" || c.startsWith("ac-3") -> "AC-3"
            c == "mp4a.a6" || c.startsWith("ec-3") -> "E-AC-3"
            c.startsWith("ac-4") -> "AC-4"
            c.startsWith("mp4a") -> "AAC"
            c == "opus" -> "Opus"
            c == "vorbis" -> "Vorbis"
            c == "flac" || c == "flac.0" -> "FLAC"
            c.startsWith("stpp") || c.startsWith("wvtt") -> null
            else -> codec.substringBefore('.')
        }
    }

    private val VIDEO_PREFIXES = listOf("avc1", "avc3", "hvc1", "hev1", "dvh1", "dvhe", "av01", "vp09", "vp08", "vp9", "vp8")
}
