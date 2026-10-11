package io.github.donggi.iroirobrowser.core

/**
 * WebView 의 기본 User-Agent 를 **크롬의 줄인 꼴**(User-Agent Reduction)로 바꾼다(사용자 결정, 2026-10-05 — '일반화된 UA 로').
 *
 * WebView 의 기본값은 `Mozilla/5.0 (Linux; Android 12; SM-A908N Build/SP1A.210812.016; wv) AppleWebKit/537.36 (KHTML, like Gecko)
 * Version/4.0 Chrome/153.0.8010.36 Mobile Safari/537.36` 처럼 **기기 모델·빌드 번호·`wv`** 를 싣는다. 크롬(안드로이드)은 2023 년부터
 * 모델을 `K` 로, 안드로이드 판을 `10` 으로, 크롬 판을 큰 번호만(`153.0.0.0`) 보낸다 — 그 꼴로 맞추면 수많은 크롬 사용자와 같은 글이 된다.
 * 크롬 판은 기기의 WebView 를 따른다(쪽이 보는 기능과 판이 어긋나지 않게).
 */
object UserAgents {

    private val CHROME = Regex("""\bChrome/(\d+)\.""")

    /** 줄인 꼴. 기본값에서 크롬 판을 읽지 못하면 null(그때는 기본값을 그대로 둔다). */
    fun generalize(defaultUserAgent: String): String? {
        val major = CHROME.find(defaultUserAgent)?.groupValues?.get(1) ?: return null
        // 폰은 'Mobile Safari', 태블릿은 'Safari' — WebView 의 기본값이 어느 쪽인지를 따른다.
        val safari = if (defaultUserAgent.contains(" Mobile Safari/")) "Mobile Safari/537.36" else "Safari/537.36"
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 $safari"
    }

    /**
     * '데스크톱 사이트' 의 글 — 크롬(안드로이드)의 데스크톱 사이트 요청이 내는 꼴(리눅스 데스크톱 크롬)을 같은 줄인 꼴로. 모바일 꼴과 같은
     * 일반화를 따른다(기기 모델·`wv` 없음, 크롬 판은 큰 번호만 — 기기의 WebView 를 따른다). 판을 읽지 못하면 null.
     */
    fun desktop(defaultUserAgent: String): String? {
        val major = CHROME.find(defaultUserAgent)?.groupValues?.get(1) ?: return null
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    /** 클라이언트 힌트의 상표 가운데 뺄 것 — WebView 임을 밝히는 것. */
    fun isWebViewBrand(brand: String): Boolean = brand.contains("WebView", ignoreCase = true)
}
