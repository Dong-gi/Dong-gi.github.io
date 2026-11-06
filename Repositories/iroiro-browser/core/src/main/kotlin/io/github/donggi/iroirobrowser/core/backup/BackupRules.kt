package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.IpLiterals
import io.github.donggi.iroirobrowser.core.LiveTabs
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.core.Retention
import io.github.donggi.iroirobrowser.core.SearchEngine
import io.github.donggi.iroirobrowser.core.adblock.Domains
import io.github.donggi.iroirobrowser.core.adblock.HideRule
import io.github.donggi.iroirobrowser.core.adblock.HideSelectors
import io.github.donggi.iroirobrowser.core.adblock.UserHides
import java.util.Locale

/**
 * 가져오는 값을 거르는 규칙(설정 백업 [BackupJson]). 못 쓰는 값이면 null — 부르는 쪽이 그 항목을 건너뛰고 센다.
 *
 * **설정 화면이 쓰는 검사를 그대로 부른다** — 예외 규칙은 [Bypass.normalizeHost]·[Bypass.normalizeCidr](앞의 0·8진수 함정을 거르는 그 칸),
 * 프록시 호스트는 [ProxySettings.checkHost], 포트는 설정 화면과 같은 1~65535, 숨긴 요소는 저장 파일을 읽을 때와 같은
 * [UserHides.isValidSite]·[HideSelectors.isSafeSelector], 출구 확인·광고 목록의 주소는 설정 화면처럼 `http://`·`https://` 로 시작해야 한다
 * (거기에 빈칸·제어 글자·방향 제어 글자를 더 막는다). 화면에 검사가 없던 북마크·방문 기록의 주소와 제목은 여기서 정한다 — [webUrl]·[cleanTitle].
 */
object BackupRules {

    /** 주소의 글자 상한 — 세션이 되살리는 주소의 상한(`BrowserActivity.MAX_SAVED_URL`)과 같다. */
    const val MAX_URL_CHARS = 8 * 1024

    /** 제목의 글자 상한(UTF-16 단위 — 서로게이트 쌍은 가르지 않는다). */
    const val MAX_TITLE_CHARS = 500

    const val MAX_USER_CHARS = 256

    /** 간 횟수의 상한 — 파일이 적은 큰 수가 제안의 점수를 흔들지 않게. */
    const val MAX_VISITS = 1_000_000

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val LIST_ID = Regex("""[a-z0-9][a-z0-9._-]{0,63}""")

    /**
     * 북마크·방문 기록의 주소 — `http`·`https` 만 받는다(`javascript:`·`data:`·`file:`·`intent:`·`place:` 따위는 버린다). 호스트가 있어야 하고,
     * 사용자 정보(`https://good.example@evil.example/` — 속이는 주소의 흔한 꼴)·역슬래시·빈칸·제어 글자·보이지 않는 글자(방향 제어 등)·
     * 짝 없는 서로게이트가 든 것은 받지 않는다. 포트는 1~65535. 스킴만 소문자로 고친다(WebView 가 주는 주소와 같은 꼴 — 이미 있는 북마크와
     * 견줄 때). 나머지는 손대지 않는다.
     */
    fun webUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty() || s.length > MAX_URL_CHARS || !cleanUrlChars(s)) return null
        val colon = s.indexOf(':')
        if (colon <= 0) return null
        val scheme = s.substring(0, colon).lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        if (!s.startsWith("//", colon + 1)) return null
        val start = colon + 3
        var end = start
        while (end < s.length && s[end] != '/' && s[end] != '?' && s[end] != '#') end++
        if (!validAuthority(s.substring(start, end))) return null
        return scheme + s.substring(colon)
    }

    /**
     * 출구 확인 주소·사용자가 더한 광고 목록의 주소 — 설정 화면(출구 확인)·광고 차단 화면(목록 더하기)과 같이 `https://`·`http://` 로 시작해야
     * 한다. 거기에 빈칸·제어 글자·보이지 않는 글자·역슬래시와 길이를 더 막는다.
     */
    fun settingsUrl(raw: String): String? {
        val s = raw.trim()
        if (s.length > MAX_URL_CHARS || !(s.startsWith("https://") || s.startsWith("http://")) || !cleanUrlChars(s)) return null
        return s
    }

    /**
     * 제목 — 제어 글자·줄 바꿈은 빈칸으로, 보이지 않는 서식 글자(방향 제어 U+202A–202E·U+2066–2069·U+200E/200F·U+061C, 폭 없는 빈칸, BOM, 소프트
     * 하이픈 …)는 지운다(이모지와 글자를 잇는 U+200D·U+200C 는 둔다). 빈칸은 하나로 줄이고 앞뒤를 자르며 [MAX_TITLE_CHARS] 에서 끊는다
     * (서로게이트 쌍은 가르지 않는다). 짝 없는 서로게이트는 U+FFFD — SQLite·파일에 UTF-8 로 쓸 때 깨지지 않게. 두 번 걸러도 같다.
     */
    fun cleanTitle(raw: String): String {
        val out = StringBuilder(minOf(raw.length, MAX_TITLE_CHARS))
        var space = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            val pair = Character.isHighSurrogate(c) && i + 1 < raw.length && Character.isLowSurrogate(raw[i + 1])
            val code = if (pair) Character.toCodePoint(c, raw[i + 1]) else c.code
            val width = if (pair) 2 else 1
            i += width
            val type = Character.getType(code)
            when {
                type == FORMAT && code != ZWJ && code != ZWNJ -> continue
                type == CONTROL || type == LINE_SEPARATOR || type == PARAGRAPH_SEPARATOR || type == SPACE_SEPARATOR ||
                    Character.isWhitespace(code) -> space = true
                else -> {
                    val gap = if (space && out.isNotEmpty()) 1 else 0
                    val unit = if (type == SURROGATE) 1 else width
                    if (out.length + gap + unit > MAX_TITLE_CHARS) break
                    if (gap == 1) out.append(' ')
                    space = false
                    if (type == SURROGATE) out.append(Char(0xFFFD)) else out.appendCodePoint(code)
                }
            }
        }
        return out.toString()
    }

    /**
     * 팝업 허용·광고 차단을 끈 사이트 — 숨긴 요소의 사이트와 같은 글자 규칙([UserHides.isValidSite])을 지나고 등록 도메인으로 고친 것(두
     * 목록이 등록 도메인으로 기억한다 — `PopupSites`·`AdBlockService`).
     */
    fun site(raw: String): String? {
        val s = raw.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (!UserHides.isValidSite(s)) return null
        return Domains.registrable(s).takeIf { UserHides.isValidSite(it) }
    }

    /** 숨긴 요소 하나 — 저장 파일을 읽을 때([UserHides.parse])와 같은 검사. */
    fun hideRule(site: String, selector: String): HideRule? {
        val s = site.trim().lowercase(Locale.ROOT)
        val sel = selector.trim()
        return if (UserHides.isValidSite(s) && HideSelectors.isSafeSelector(sel)) HideRule(s, sel) else null
    }

    /** 예외 규칙의 호스트 무늬 — 설정 화면의 칸과 같은 [Bypass.normalizeHost]. */
    fun bypassHost(raw: String): String? = try {
        Bypass.normalizeHost(raw)
    } catch (e: Bypass.Invalid) {
        null
    }

    /** 예외 규칙의 IP 대역 — 설정 화면의 칸과 같은 [Bypass.normalizeCidr]. */
    fun bypassCidr(raw: String): String? = try {
        Bypass.normalizeCidr(raw)
    } catch (e: Bypass.Invalid) {
        null
    }

    /** 검색 엔진 — 이 앱이 아는 id 만([SearchEngine.byId] 처럼 모르면 기본값으로 떨어뜨리지 않는다 — 지금 값을 둔다). */
    fun searchEngine(raw: String): String? = SearchEngine.entries.firstOrNull { it.id == raw }?.id

    /** 살아 있는 탭의 수 — 설정 화면이 고르게 하는 범위([LiveTabs.CHOICES] 의 처음과 끝) 안의 것만(앱의 설정이 그 범위로 맞춘다). */
    fun liveTabs(raw: Long): Int? = if (raw in LiveTabs.CHOICES.first()..LiveTabs.CHOICES.last()) raw.toInt() else null

    /** 방문 기록 보관 기간 — 설정 화면이 고르게 하는 값([Retention.HISTORY_DAYS])만. */
    fun historyDays(raw: Long): Int? = Retention.HISTORY_DAYS.firstOrNull { it.toLong() == raw }

    /** 캐시 비우기 주기 — 설정 화면이 고르게 하는 값([Retention.CACHE_DAYS])만. */
    fun cacheDays(raw: Long): Int? = Retention.CACHE_DAYS.firstOrNull { it.toLong() == raw }

    /** 들어 있는 광고 목록의 id(꼴만 — 이 앱에 그 목록이 있는지는 [ImportPlan] 이 본다). */
    fun listId(raw: String): String? = raw.takeIf { LIST_ID.matches(it) }

    /** 프록시 호스트 — 설정 화면의 [ProxySettings.checkHost]. */
    fun proxyHost(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && ProxySettings.checkHost(it) == null }

    /** 프록시 포트 — 설정 화면의 [ProxySettings.checkPort] 와 같은 범위. */
    fun proxyPort(raw: Long): Int? = if (raw in 1..65535) raw.toInt() else null

    /** 프록시 사용자 — 설정 화면은 거르지 않는다. 여기서는 제어 글자(머리 끼워 넣기)와 길이만 막는다. */
    fun proxyUser(raw: String): String? = raw.takeIf { s -> s.length <= MAX_USER_CHARS && s.none { it.code < 0x20 || it.code == 0x7F } }

    /** 때(밀리초) — 1970 뒤이고 지금보다 하루 넘게 앞서지 않은 것. */
    fun time(ms: Long, now: Long): Long? = if (ms > 0 && ms <= now + DAY_MS) ms else null

    /** [text] 를 [max] 글자까지 — 서로게이트 쌍을 가르지 않는다. */
    fun clip(text: String, max: Int): String {
        if (text.length <= max) return text
        val end = if (max > 0 && Character.isHighSurrogate(text[max - 1])) max - 1 else max
        return text.substring(0, end)
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────────────────────────────────────────

    private val FORMAT = Character.FORMAT.toInt()
    private val CONTROL = Character.CONTROL.toInt()
    private val LINE_SEPARATOR = Character.LINE_SEPARATOR.toInt()
    private val PARAGRAPH_SEPARATOR = Character.PARAGRAPH_SEPARATOR.toInt()
    private val SPACE_SEPARATOR = Character.SPACE_SEPARATOR.toInt()
    private val SURROGATE = Character.SURROGATE.toInt()
    private const val ZWJ = 0x200D
    private const val ZWNJ = 0x200C

    /** 주소에 둘 수 없는 글자 — 빈칸·제어 글자·서식 글자(방향 제어 등)·역슬래시·짝 없는 서로게이트. */
    private fun cleanUrlChars(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) return false
                if (Character.getType(Character.toCodePoint(c, s[i + 1])) == FORMAT) return false
                i += 2
                continue
            }
            if (Character.isLowSurrogate(c) || c.isWhitespace() || c == '\\' || Character.isISOControl(c) || Character.getType(c) == FORMAT) {
                return false
            }
            i++
        }
        return true
    }

    /** `호스트[:포트]` — 사용자 정보는 받지 않는다. IPv6 는 대괄호로만. */
    private fun validAuthority(a: String): Boolean {
        if (a.isEmpty() || '@' in a) return false
        val port: String?
        if (a[0] == '[') {
            val close = a.indexOf(']')
            if (close < 0 || !IpLiterals.isIpv6(a.substring(1, close))) return false
            val after = a.substring(close + 1)
            port = when {
                after.isEmpty() -> null
                after[0] == ':' -> after.substring(1)
                else -> return false
            }
        } else {
            val c = a.indexOf(':')
            if (c >= 0 && a.indexOf(':', c + 1) >= 0) return false
            if (!validHostName(if (c < 0) a else a.substring(0, c))) return false
            port = if (c < 0) null else a.substring(c + 1)
        }
        if (port.isNullOrEmpty()) return true
        return port.length <= 5 && port.all { it in '0'..'9' } && port.toInt() in 1..65535
    }

    /** 호스트 이름 — 글자·숫자(국제화 이름의 글자 포함)·`-`·`_` 로 된 낱말을 점으로 이은 것. 끝의 점 하나는 받는다. `%` 는 받지 않는다. */
    private fun validHostName(h: String): Boolean {
        val name = if (h.endsWith('.')) h.substring(0, h.length - 1) else h
        if (name.isEmpty() || name.length > 253) return false
        return name.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }
}
