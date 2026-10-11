package io.github.donggi.iroirobrowser.core.adblock

/**
 * 광고 규칙의 주소 무늬(ABP·uBO 문법) — 정규식으로 바꾸지 않고 직접 맞춘다.
 *
 * - `||` 로 시작하면 호스트의 이름 경계(호스트의 처음이나 `.` 바로 뒤)에서만 시작한다.
 * - `|` 로 시작하면 주소의 처음에서, `|` 로 끝나면 주소의 끝에서 맞아야 한다.
 * - `*` 는 아무 글(없어도 된다), `^` 는 구분 글자 하나(글자·숫자·`_ - . %` 가 아닌 것) 또는 주소의 끝.
 * - 대소문자는 가리지 않는다(`$match-case` 가 아니면 — 그때는 [matchCase]).
 *
 * 맞추는 쪽은 주소를 이미 소문자로 바꿔 둔다([Request.urlLower]).
 */
class UrlPattern private constructor(
    private val segments: List<String>,
    private val hostAnchor: Boolean,
    private val startAnchor: Boolean,
    private val endAnchor: Boolean,
    val matchCase: Boolean,
) {

    /** [url] 에 맞는가. [hostStart]·[hostEnd] 는 주소 안의 호스트 자리(`||` 에 쓴다). */
    fun matches(url: String, hostStart: Int, hostEnd: Int): Boolean {
        if (segments.isEmpty()) return true
        val first = segments[0]
        if (hostAnchor) {
            // 호스트의 이름 경계마다 해 본다(몇 곳 안 된다).
            var p = hostStart
            while (p in hostStart until maxOf(hostStart + 1, hostEnd)) {
                if (matchFrom(url, first, p) && rest(url, p + first.length, 1)) return true
                val dot = url.indexOf('.', p)
                if (dot < 0 || dot >= hostEnd) break
                p = dot + 1
            }
            return false
        }
        if (startAnchor) return matchFrom(url, first, 0) && rest(url, first.length, 1)
        // 앵커가 없으면 첫 조각을 아무 데서나 — 맞는 자리마다 해 본다(뒤에 `|` 가 있으면 첫 자리가 답이 아닐 수 있다).
        var from = 0
        while (true) {
            val p = find(url, first, from)
            if (p < 0) return false
            if (rest(url, p + first.length, 1)) return true
            from = p + 1
        }
    }

    /** [index] 번째 조각부터 [pos] 뒤에서 맞춘다(사이는 `*`). */
    private fun rest(url: String, pos: Int, index: Int): Boolean {
        if (index == segments.size) return !endAnchor || pos == url.length
        val seg = segments[index]
        if (index == segments.size - 1 && endAnchor) {
            // 마지막 조각은 끝에 붙어야 한다. 끝의 `^` 는 주소의 끝에 맞을 수도 있다(그러면 한 글자 덜 쓴다).
            val at = url.length - seg.length
            if (at >= pos && matchFrom(url, seg, at)) return true
            return seg.endsWith('^') && at + 1 >= pos && matchFrom(url, seg, at + 1)
        }
        // `*` 사이의 조각은 가장 앞의 자리가 늘 충분하다(앞에서 맞출수록 뒤가 넓다) — 단, 끝의 `^` 가 주소의 끝을 쓰는 경우까지 맞춘다.
        val p = find(url, seg, pos)
        return p >= 0 && rest(url, p + consumed(url, seg, p), index + 1)
    }

    /** [seg] 를 [url] 의 [at] 에서 맞추는가. `^` 는 구분 글자 하나 또는(조각의 끝에서) 주소의 끝. */
    private fun matchFrom(url: String, seg: String, at: Int): Boolean {
        var i = at
        for (k in seg.indices) {
            val c = seg[k]
            if (c == '^') {
                if (i == url.length) return k == seg.length - 1 // 주소의 끝은 마지막 `^` 하나로만
                if (!isSeparator(url[i])) return false
            } else {
                if (i >= url.length || url[i] != c) return false
            }
            i++
        }
        return true
    }

    private fun find(url: String, seg: String, from: Int): Int {
        var p = from
        // 첫 글자가 `^` 가 아니면 indexOf 로 건너뛴다.
        val head = seg[0]
        while (p <= url.length) {
            if (head != '^') {
                p = url.indexOf(head, p)
                if (p < 0) return -1
            }
            if (matchFrom(url, seg, p)) return p
            p++
        }
        return -1
    }

    /** [seg] 를 [p] 에서 맞췄을 때 쓴 글자 수(끝의 `^` 가 주소의 끝에 맞았으면 하나 적다). */
    private fun consumed(url: String, seg: String, p: Int): Int = if (p + seg.length > url.length) url.length - p else seg.length

    companion object {
        /** `^` 에 맞는 글자 — 글자·숫자·`_ - . %` 가 아닌 것. */
        fun isSeparator(c: Char): Boolean = !(c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%')

        /**
         * 규칙의 무늬 부분(앞의 `@@`·뒤의 `$선택지` 를 뗀 것)을 읽는다. 맞출 수 없는 꼴이면 null.
         */
        fun parse(text: String, matchCase: Boolean = false): UrlPattern? {
            var p = text
            var hostAnchor = false
            var startAnchor = false
            var endAnchor = false
            if (p.startsWith("||")) {
                hostAnchor = true
                p = p.substring(2)
            } else if (p.startsWith("|")) {
                startAnchor = true
                p = p.substring(1)
            }
            if (p.endsWith("|") && !p.endsWith("\\|")) {
                endAnchor = true
                p = p.dropLast(1)
            }
            if (!matchCase) p = p.lowercase()
            // `**` 는 `*` 하나와 같다. 앞뒤의 `*` 는 앵커를 없앤다.
            if (p.startsWith("*")) {
                hostAnchor = false
                startAnchor = false
            }
            if (p.endsWith("*")) endAnchor = false
            val segments = p.split('*').filter { it.isNotEmpty() }
            if (segments.isEmpty() && (hostAnchor || startAnchor || endAnchor)) return null
            return UrlPattern(segments, hostAnchor, startAnchor, endAnchor, matchCase)
        }
    }
}
