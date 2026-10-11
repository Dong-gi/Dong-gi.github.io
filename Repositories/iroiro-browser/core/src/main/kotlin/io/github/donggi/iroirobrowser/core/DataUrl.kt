package io.github.donggi.iroirobrowser.core

import java.util.Base64
import java.util.Locale

/**
 * `data:` 주소(RFC 2397)를 바이트로 — 쪽이 만든 내려받기(`<a download href="data:…">`)는 네트워크를 거치지 않고 여기서 푼다.
 * 꼴: `data:[<형식>][;charset=…][;base64],<자료>`. 형식이 없으면 `text/plain`. 틀린 꼴이면 null.
 */
class DataUrl private constructor(val mimeType: String, val bytes: ByteArray) {

    companion object {
        /** 이보다 큰 `data:` 는 받지 않는다(주소 글자열 하나가 통째로 메모리에 있다). */
        const val MAX_BYTES = 64 * 1024 * 1024

        fun parse(url: String): DataUrl? {
            if (!url.regionMatches(0, "data:", 0, 5, ignoreCase = true)) return null
            val comma = url.indexOf(',')
            if (comma < 0) return null
            val meta = url.substring(5, comma)
            val payload = url.substring(comma + 1)
            val parts = meta.split(';').map { it.trim() }
            val base64 = parts.any { it.equals("base64", ignoreCase = true) }
            val mime = parts.firstOrNull()?.takeIf { it.contains('/') }?.lowercase(Locale.ROOT) ?: "text/plain"
            val bytes = if (base64) {
                // 빈칸은 브라우저처럼 건너뛰고 빠진 '=' 는 채우되, 그 밖의 글자는 받지 않는다(MIME 해독기는 아무 글자나 조용히 버린다).
                val text = (PercentCoding.bytes(payload) ?: return null).decodeToString().filterNot { it == ' ' || it in "\t\n\r\u000C" }
                val padded = if (text.length % 4 == 0) text else text + "=".repeat(4 - text.length % 4)
                try {
                    Base64.getDecoder().decode(padded)
                } catch (e: IllegalArgumentException) {
                    return null
                }
            } else {
                PercentCoding.bytes(payload) ?: return null
            }
            if (bytes.size > MAX_BYTES) return null
            return DataUrl(mime, bytes)
        }
    }
}
