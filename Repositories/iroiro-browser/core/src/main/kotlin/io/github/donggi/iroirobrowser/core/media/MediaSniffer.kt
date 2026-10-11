package io.github.donggi.iroirobrowser.core.media

import io.github.donggi.iroirobrowser.core.SafeFileName
import java.util.Locale

/** 쪽이 낸 요청 가운데 받을 만한 미디어. */
enum class MediaKind {
    /** HLS 재생목록(`.m3u8`). */
    HLS,

    /** DASH 목록(`.mpd`) — 6단계. */
    DASH,
    VIDEO,
    AUDIO,
}

/**
 * 요청 주소로 미디어를 가린다 — WebView 는 응답의 형식을 알려 주지 않으므로 주소의 확장자로(쿼리의 `format=` 따위는 보지 않는다).
 * 재생목록의 조각(`.ts`·`.m4s`·`.aac` 조각)은 미디어로 치지 않는다 — 조각 하나하나가 목록을 덮지 않게, 재생목록이 따로 잡힌다.
 */
object MediaSniffer {

    fun classify(url: String): MediaKind? {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
        return when (extension(url)) {
            "m3u8" -> MediaKind.HLS
            "mpd" -> MediaKind.DASH
            "mp4", "m4v", "webm", "mov", "mkv", "3gp" -> MediaKind.VIDEO
            "mp3", "m4a", "ogg", "oga", "opus", "wav", "flac" -> MediaKind.AUDIO
            else -> null
        }
    }

    /**
     * 같은 미디어의 요청들을 하나로 묶는 열쇠 — 조각 표시(`#…`)를 떼고, 저지연 HLS 의 전달 지시(`_HLS_msn`·`_HLS_part`·`_HLS_skip` 따위 —
     * RFC 8216bis 의 `_HLS_` 쿼리)를 뗀다. 재생기가 재생목록을 다시 받을 때마다 그것을 바꿔 달아 같은 재생목록이 줄마다 새로 떴고 마스터 아래로도
     * 숨지 않았다. 그 밖의 쿼리는 그대로 둔다(범위 요청이 쿼리만 다른 경우는 드물고, 쿼리가 다르면 다른 영상일 수 있다).
     */
    fun key(url: String): String {
        val noFragment = url.substringBefore('#')
        val q = noFragment.indexOf('?')
        if (q < 0 || !noFragment.contains("_HLS_")) return noFragment
        val kept = noFragment.substring(q + 1).split('&').filterNot { it.startsWith("_HLS_") }
        return noFragment.substring(0, q) + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
    }

    /**
     * 번호만 다른 같은 경로의 파일들(`seg-1.mp4`, `seg-2.mp4` …)을 묶는 열쇠 — 마지막 이름의 **마지막 숫자 묶음**을 `#` 로 바꾸고 쿼리·조각
     * 표시를 뗀다(`video_1080p_00012.mp4` 는 `00012` 를). 이름에 숫자가 없으면 null — 그 주소 하나로 친다. 조각을 `.mp4` 로 내는 재생기에서
     * 조각마다 '영상 · 1.mp4' 줄이 수십 개 쌓였다.
     */
    fun seriesKey(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val slash = path.lastIndexOf('/')
        val name = path.substring(slash + 1)
        // 확장자의 숫자(`mp4`·`3gp`)는 번호가 아니다.
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val digits = LAST_DIGITS.find(stem) ?: return null
        return path.substring(0, slash + 1) + stem.replaceRange(digits.range, "#") + name.substring(stem.length)
    }

    /** 주소 경로의 마지막 이름(디코딩하지 않는다). 없으면 호스트. */
    fun displayName(url: String): String {
        val path = url.substringBefore('#').substringBefore('?')
        val afterScheme = path.substringAfter("://", path)
        val last = afterScheme.substringAfter('/', "").substringAfterLast('/')
        return last.ifEmpty { afterScheme.substringBefore('/') }
    }

    /**
     * 찾은 미디어를 받을 때의 기본 이름 — **페이지 제목**에 주소의 확장자를 붙인다(주소 끝의 이름은 흔히 `3f9a1c….mp4` 같은 무의미한 글자다).
     * 제목이 없거나 이름으로 쓸 글자가 남지 않으면 null(부르는 쪽이 주소의 이름을 쓴다).
     */
    fun nameFromTitle(title: String, url: String): String? {
        val base = SafeFileName.clean(title)
        if (title.isBlank() || base == SafeFileName.FALLBACK) return null
        val ext = extension(url)
        return SafeFileName.clean(if (ext.isEmpty()) base else "$base.$ext")
    }

    internal fun extension(url: String): String {
        val name = displayName(url)
        val dot = name.lastIndexOf('.')
        return if (dot < 0) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    /** 이름의 마지막 숫자 묶음(그 뒤에는 숫자가 없다). */
    private val LAST_DIGITS = Regex("""\d+(?=\D*$)""")
}
