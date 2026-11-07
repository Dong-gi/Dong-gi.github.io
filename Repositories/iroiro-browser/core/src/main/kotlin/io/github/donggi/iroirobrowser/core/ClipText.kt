package io.github.donggi.iroirobrowser.core

import java.util.Locale

/**
 * 복사한 글에서 열 링크를 고른다 — 주소창의 '복사한 링크 열기'. 글 전체가 주소면 그것([Omnibox] 와 같은 판단 —
 * 스킴 없는 도메인도), 아니면 글 안의 첫 http(s) 주소(끝에 붙은 문장 부호는 뗀다 — 짝이 맞는 닫는 괄호는 둔다). 없으면 null. 너무 긴 글은 보지
 * 않는다([MAX_CHARS]). **웹 주소(http·https)만** 고른다 — 주소창은 `data:`·`about:` 도 열지만, 복사한 글이 쪽을 짓게 두지 않는다.
 */
object ClipText {

    /** 이보다 긴 글은 보지 않는다 — 주소창이 받는 주소의 길이(세션에 남기는 상한)와 같은 자릿수. */
    const val MAX_CHARS = 8_192

    fun firstLink(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_CHARS) return null
        val lower = t.lowercase(Locale.ROOT)
        if (t.none { it.isWhitespace() }) {
            // 글 전체가 주소 — 스킴을 적었으면 끝의 문장 부호를 떼고(`https://example.com/x。`), 스킴이 없으면 주소창처럼 그대로.
            val whole = if (lower.startsWith("http://") || lower.startsWith("https://")) trimTail(t) else t
            if (isWebUrl(whole)) return whole
        }
        var from = 0
        while (from < t.length) {
            val at = lower.indexOf("http", from)
            if (at < 0) return null
            val schemeEnd = when {
                lower.startsWith("https://", at) -> at + "https://".length
                lower.startsWith("http://", at) -> at + "http://".length
                else -> -1
            }
            // 낱말 가운데의 http 는 주소의 시작이 아니다(`xhttp://`).
            if (schemeEnd < 0 || (at > 0 && lower[at - 1].isLetterOrDigit())) {
                from = at + 1
                continue
            }
            var end = schemeEnd
            while (end < t.length && !t[end].isWhitespace() && t[end] !in STOP) end++
            val candidate = trimTail(t.substring(at, end))
            if (candidate.length > schemeEnd - at && isWebUrl(candidate)) return candidate
            from = end
        }
        return null
    }

    /**
     * 글 전체가 스킴을 적은 http(s) 주소 하나인가 — 클립의 이름표를 볼 때(이 앱의 '링크 복사' 는 이름표에 주소를 둔다). 이름표에 파일 이름을
     * 다는 앱이 있어(`notes.txt` 는 [firstLink] 에게 주소다) 스킴이 없으면 거짓.
     */
    fun isExplicitLink(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_CHARS || t.any { it.isWhitespace() }) return false
        val lower = t.lowercase(Locale.ROOT)
        return (lower.startsWith("http://") || lower.startsWith("https://")) && isWebUrl(t)
    }

    /** [Omnibox] 가 주소로 보고 그 주소가 http(s) 인가. */
    private fun isWebUrl(s: String): Boolean {
        val target = Omnibox.classify(s) as? Omnibox.Target.Url ?: return false
        val url = target.url.lowercase(Locale.ROOT)
        return url.startsWith("http://") || url.startsWith("https://")
    }

    /** 끝에 붙은 문장 부호를 뗀다 — 닫는 괄호는 여는 괄호보다 많을 때만(위키백과의 `…_(동음이의)` 는 둔다). */
    private fun trimTail(s: String): String {
        var end = s.length
        while (end > 0) {
            val c = s[end - 1]
            val drop = when (c) {
                ')' -> s.substring(0, end).count { it == ')' } > s.substring(0, end).count { it == '(' }
                else -> c in TRAILING
            }
            if (!drop) break
            end--
        }
        return s.substring(0, end)
    }

    /** 주소가 끝나는 글자 — 빈칸 말고도 꺾쇠와 따옴표(메신저·HTML 에서 주소를 둘러싼다), 그리고 주소에 날것으로 오지 않는 전각 괄호·따옴표. */
    private val STOP = charArrayOf('<', '>', '"', Char(0x27), '「', '」', '『', '』', '（', '）', '【', '】', '〈', '〉', '《', '》', '“', '”', '‘', '’')

    /** 주소 끝에 붙어 오는 문장 부호(닫는 괄호는 [trimTail] 이 따로 본다). */
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ']', '}', '"', Char(0x27), '。', '、', '…')
}
