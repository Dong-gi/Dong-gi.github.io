package io.github.donggi.iroirobrowser.browser

import java.net.URI
import java.net.URISyntaxException

/**
 * 뒤에서 재생 잇기(사용자 요청, 2026-10-11: '브라우저 > 유튜브 재생 시 앱/탭이 백그라운드에 있거나 화면이 꺼져도 재생이 유지되도록 해주세요').
 * 이 파일은 판단(화면 밖의 계산 — 시험한다)이다. WebView 를 보이는 채로 두는 것은 [PageWebView.keepWindowVisible]·[PageWebView.backstaged],
 * 그 탭을 고르고 회를 여닫는 것은 BrowserActivity, 알림·잠금 화면·이어폰 단추는 [BackgroundPlayService] 다.
 *
 * **회(session)** — 뒤에서 재생을 잇는 탭 하나와 그 탭의 소리가 멈춘 때. 앱이 뒤로 가거나 그 탭을 떠나는 순간 소리가 나는 유튜브가 재생 중이면
 * 연다. 소리가 멈춰도 [PAUSE_GRACE_MS] 동안은 이어서 틀 수 있게 남기고, 그 뒤에 닫는다(그 탭은 여느 뒤의 탭으로 돌아간다).
 */
object BackgroundPlay {

    /** 소리가 멈춘 뒤 회를 남겨 두는 시간 — 알림·잠금 화면·이어폰 단추로 다시 튼다. 그동안 쪽은 보이는 채라 자원을 쓴다. */
    const val PAUSE_GRACE_MS = 10 * 60_000L

    /**
     * 그 탭이 뒤에서도 재생을 잇는가 — 주 문서([url])가 유튜브(https)이고 소리가 나는 미디어가 재생 중이다([state]). 광고가 나오는 중이면
     * ([adShowing] — 광고 가리기가 음소거했다) 영상이 재생 중인 것으로 족하다 — 광고는 사람이 소리와 함께 튼 재생에만 붙는다(검토).
     */
    fun wanted(url: String, state: PlayingMedia.State, adShowing: Boolean = false): Boolean =
        (state.audible || (adShowing && state.video)) && isYouTube(url)

    /** 주 문서가 유튜브인가 — 광고 가리기와 같은 출처 판단([YtGuard.isYouTubeOrigin]). 주소를 읽지 못하면 아니다. */
    fun isYouTube(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return false
        }
        return YtGuard.isYouTubeOrigin(uri.scheme, uri.host, uri.port)
    }

    /** 알림·잠금 화면의 제목 — 쪽 제목에서 유튜브가 붙이는 꼬리(' - YouTube')를 뗀다. 비면 [fallback]. */
    fun titleOf(pageTitle: String, fallback: String): String =
        cleanText(pageTitle.trim().removeSuffix(YOUTUBE_SUFFIX)).ifEmpty { fallback }

    /**
     * 쪽이 정한 글(화면의 영상 제목·쪽 제목)을 알림에 싣기 전에 다듬는다 — 제어 문자는 빈칸으로, 방향 제어 문자(U+202E 등)는 빼고
     * (남의 글이 알림의 뒤 글을 뒤집지 못하게), 빈칸을 하나로, [MAX_TEXT] 코드 포인트까지.
     */
    fun cleanText(text: String): String {
        val out = StringBuilder()
        var count = 0
        var i = 0
        while (i < text.length && count < MAX_TEXT) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp in BIDI_CONTROLS -> Unit
                Character.isISOControl(cp) || Character.isWhitespace(cp) -> if (out.isNotEmpty() && out.last() != ' ') {
                    out.append(' ')
                    count++
                }
                else -> {
                    out.appendCodePoint(cp)
                    count++
                }
            }
        }
        return out.toString().trim()
    }

    /** 알림에 싣는 글의 길이(코드 포인트). */
    const val MAX_TEXT = 200

    /** 방향 제어 문자 — LRM·RLM·ALM, 내장·덮어쓰기(LRE…RLO), 고립(LRI…PDI). */
    private val BIDI_CONTROLS = setOf(0x200E, 0x200F, 0x061C) + (0x202A..0x202E) + (0x2066..0x2069)

    /**
     * 서비스(알림)를 세워 둘지 — 회가 있고([alive]), 앱이 뒤에 있거나([foreground] 가 아니다) 그 탭이 지금 탭이 아니다([isCurrent]). 앞에서
     * 그 탭을 보고 있으면 알림이 필요 없다.
     */
    fun serviceWanted(alive: Boolean, foreground: Boolean, isCurrent: Boolean): Boolean = alive && (!foreground || !isCurrent)

    /** 회를 닫을 때인가 — 소리가 멈춘 때([stoppedAt], 재생 중이면 null)에서 [PAUSE_GRACE_MS] 가 지났다. 시계가 거꾸로 가면 아직이다. */
    fun expired(stoppedAt: Long?, now: Long): Boolean = stoppedAt != null && now - stoppedAt >= PAUSE_GRACE_MS

    private const val YOUTUBE_SUFFIX = "- YouTube"
}
