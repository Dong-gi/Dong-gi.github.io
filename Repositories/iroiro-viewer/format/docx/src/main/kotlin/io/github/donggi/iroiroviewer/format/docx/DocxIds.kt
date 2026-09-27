package io.github.donggi.iroiroviewer.format.docx

/**
 * 본문에 적는 `id` 들. **`[a-z0-9-]` 만 쓴다.**
 *
 * 책갈피 이름(`w:bookmarkStart/@w:name`)은 문서가 적는 값이다. 그대로 `id` 로 쓰면 위생기를 지나더라도
 * 목차의 자리(`FlowOutline.anchor`)가 URL 조각으로 건너가는 길에서 인코딩 문제가 생기고, 우리가 쓰는
 * `h-`·`fn-` 과 겹칠 수 있다. 그래서 **되돌릴 수 있게**(단사) 옮긴다 — 영소문자·숫자는 그대로,
 * 나머지 바이트는 `-` 와 16진 두 자리로. `-` 자신도 옮기므로 두 이름이 같은 `id` 가 되지 않는다.
 */
internal object DocxIds {

    /** 제목 문단. 최상위 블록 번호라 문서 안에서 하나뿐이다. */
    fun heading(blockIndex: Int): String = "h-$blockIndex"

    /** 책갈피. 이름이 매우 길면 앞부분과 해시로 줄인다(워드는 40 자로 막는다). */
    fun bookmark(name: String): String {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size + 8).append("bm-")
        val limit = minOf(bytes.size, MAX_NAME_BYTES)
        for (i in 0 until limit) {
            val c = bytes[i].toInt() and 0xFF
            if (c in 'a'.code..'z'.code || c in '0'.code..'9'.code) {
                sb.append(c.toChar())
            } else {
                sb.append('-').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
        if (bytes.size > MAX_NAME_BYTES) sb.append("-h").append(Integer.toHexString(name.hashCode()))
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"
    private const val MAX_NAME_BYTES = 120
}
