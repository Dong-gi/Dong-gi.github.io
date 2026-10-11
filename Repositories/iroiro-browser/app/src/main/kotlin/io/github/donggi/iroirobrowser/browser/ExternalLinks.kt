package io.github.donggi.iroirobrowser.browser

import android.content.Intent
import android.net.Uri
import java.net.URISyntaxException
import java.util.Locale

/**
 * WebView 가 다루지 않는 스킴(`intent:`·`market:`·`tel:`·`mailto:` …)을 다른 앱으로 넘기는 규칙 — 크롬과 같게: **주 프레임에서, 사용자가
 * 누른 것(또는 그 넘겨주기)만**, 언제나 사용자에게 묻고, 받는 앱을 고르는 것은 시스템이다. `intent:` 의 컴포넌트·셀렉터는 지운다(쪽이 우리
 * 앱의 숨은 화면이나 특정 앱의 내보내지 않은 화면을 겨냥하지 못하게). **다른 앱은 프록시를 거치지 않는다** — 묻는 글이 그렇게 말한다.
 */
object ExternalLinks {

    private val WEB = setOf("http", "https", "about", "data", "blob", "javascript")

    fun isWebScheme(url: Uri): Boolean = url.scheme?.lowercase(Locale.ROOT) in WEB

    /** 다른 앱에 줄 인텐트. 읽지 못하면 null. */
    fun intentFor(url: String): Intent? {
        if (url.startsWith("intent:", ignoreCase = true)) {
            val intent = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (e: URISyntaxException) {
                return null
            }
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            intent.flags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION).inv()
            return intent
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    /** `intent:` 의 `S.browser_fallback_url` — 받을 앱이 없을 때 대신 열 http(s) 주소. */
    fun fallbackUrl(url: String): String? {
        if (!url.startsWith("intent:", ignoreCase = true)) return null
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: URISyntaxException) {
            return null
        }
        return intent.getStringExtra("browser_fallback_url")?.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
    }

    /** 묻는 글에 보일 이름(스킴). */
    fun label(url: String): String = url.substringBefore(':').lowercase(Locale.ROOT)
}
