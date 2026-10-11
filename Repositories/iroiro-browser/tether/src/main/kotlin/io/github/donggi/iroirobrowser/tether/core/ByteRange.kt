package io.github.donggi.iroirobrowser.tether.core

/** `Range` 머리가 가리킨 바이트 구간. 양 끝을 포함한다. */
class ByteRange(val start: Long, val end: Long) {

    override fun equals(other: Any?): Boolean = other is ByteRange && other.start == start && other.end == end

    override fun hashCode(): Int = (start * 31 + end).hashCode()

    override fun toString(): String = "$start-$end"

    companion object {
        /**
         * `Range: bytes=start-end` 를 읽는다. 받는 꼴: `bytes=0-499`, `bytes=500-`(끝까지), `bytes=-500`(마지막 500바이트). 여러 구간
         * (`bytes=0-1,5-6`)은 받지 않는다 — multipart/byteranges 로 답해야 하고 브라우저가 파일 받기에 쓰지 않는다.
         *
         * @return 맞춘 구간. 머리가 없거나 맞출 수 없으면 null(부르는 쪽은 머리가 있는데 null 이면 416)
         */
        fun parse(header: String?, totalLength: Long): ByteRange? {
            if (header == null) return null
            if (totalLength <= 0) return null

            val spec = header.trim().removePrefix("bytes=").trim()
            if (spec.isEmpty() || spec.contains(',')) return null

            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val startText = spec.substring(0, dash)
            val endText = spec.substring(dash + 1)

            val start: Long
            val end: Long
            if (startText.isEmpty()) {
                // 꼬리 꼴: 마지막 N 바이트
                val suffix = endText.toLongOrNull() ?: return null
                if (suffix <= 0) return null
                start = maxOf(0, totalLength - suffix)
                end = totalLength - 1
            } else {
                start = startText.toLongOrNull() ?: return null
                if (start < 0 || start >= totalLength) return null
                end = if (endText.isEmpty()) {
                    totalLength - 1
                } else {
                    val parsed = endText.toLongOrNull() ?: return null
                    minOf(parsed, totalLength - 1)
                }
                if (end < start) return null
            }
            return ByteRange(start, end)
        }
    }
}
