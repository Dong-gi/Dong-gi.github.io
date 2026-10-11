package io.github.donggi.iroirobrowser.browser

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import io.github.donggi.iroirobrowser.core.media.HlsPlaylist
import io.github.donggi.iroirobrowser.net.HttpFetcher
import io.github.donggi.iroirobrowser.proxy.NetworkPath
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * 받기 전에 묻는 작은 것 — 영상 파일의 크기(받기 확인 창 — 늘 '크기 모름' 이었다)와 HLS 재생목록이 마스터인지(찾은 미디어에서
 * 그 아래 재생목록을 숨긴다).
 *
 * **내려받기와 같은 길**로 묻는다 — 앱의 HTTP, 길은 [NetworkPath.httpRoute](중계, 예외 규칙에 든 사설망 주소는 바로 — 홉마다. 프록시를 켰는데
 * 중계가 없으면 묻지 않는다, 직접 나가지 않는다),
 * 머리는 쪽의 User-Agent·Referer·그 주소의 쿠키. 내려받기의 일꾼과 따로 돌고(받는 중에도 바로 답한다) 다시 해 보지 않으며 시간 제한도 짧다 —
 * 답이 없으면 '모름' 일 뿐이다.
 */
class MediaProbe(private val network: NetworkPath) {

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "web-probe").apply { isDaemon = true } }

    /**
     * 크기(바이트) — `Range: bytes=0-0` 에 대한 `Content-Range` 의 끝, 범위를 모르는 서버(200)면 `Content-Length`. 모르면 -1. 답은 메인 스레드로.
     * 본문은 읽지 않는다(범위를 모르는 서버가 통째로 보내도 곧 끊는다).
     */
    fun size(url: String, userAgent: String, referer: String?, done: (Long) -> Unit) = submit(done, -1L) {
        fetcher().get(url, headersFor = { u -> headers(userAgent, referer, u, RANGE_FIRST_BYTE) }, onSetCookie = storeCookies).use { r ->
            when (r.code) {
                206 -> RANGE_TOTAL.find(r.header("Content-Range").orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: -1L
                200 -> r.contentLength
                else -> -1L
            }
        }
    }

    /** HLS 재생목록을 읽는다 — 읽지 못하면 null. 넘겨주기를 따랐으면 상대 주소는 마지막 주소에 대어 푼다(재생기도 그렇게 푼다). */
    fun playlist(url: String, userAgent: String, referer: String?, done: (HlsPlaylist?) -> Unit) = submit(done, null) {
        fetcher().get(url, headersFor = { u -> headers(userAgent, referer, u, emptyMap()) }, onSetCookie = storeCookies).use { r ->
            if (r.code != 200) return@use null
            val bytes = readAtMost(r.body, MAX_PLAYLIST_BYTES) ?: return@use null
            HlsPlaylist.parse(bytes.decodeToString(), r.url)
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun <T> submit(done: (T) -> Unit, fallback: T, work: () -> T) {
        try {
            executor.execute {
                val result = try {
                    work()
                } catch (e: IOException) {
                    fallback
                } catch (e: HlsPlaylist.Invalid) {
                    fallback
                } catch (e: RuntimeException) {
                    // 잘못된 주소 따위(URL·HttpURLConnection 의 IllegalArgumentException) — 모름.
                    fallback
                }
                main.post { done(result) }
            }
        } catch (e: RejectedExecutionException) {
            // 화면이 닫혔다.
        }
    }

    /** 부를 때마다 지금 길로 — 길이 아직 없으면 [HttpFetcher.get] 이 연결을 열지 않고 [HttpFetcher.NoRoute](모름). */
    private fun fetcher(): HttpFetcher = HttpFetcher(network.httpRoute(), connectTimeoutMs = TIMEOUT_MS, readTimeoutMs = TIMEOUT_MS)

    /** 내려받기([io.github.donggi.iroirobrowser.download.DownloadCenter])와 같은 머리 — 쪽과 같게 보이도록. */
    private fun headers(userAgent: String, referer: String?, url: String, extra: Map<String, String>): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Accept", "*/*")
        referer?.let { put("Referer", it) }
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotEmpty() }?.let { put("Cookie", it) }
        putAll(extra)
    }

    private val storeCookies: (String, List<String>) -> Unit = { url, values -> values.forEach { CookieManager.getInstance().setCookie(url, it) } }

    /** [limit] 바이트까지 읽는다 — 넘으면 null(재생목록이 아니다). */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
        val RANGE_FIRST_BYTE = mapOf("Range" to "bytes=0-0")
        val RANGE_TOTAL = Regex("""bytes\s+\d+-\d+/(\d+)""")
    }
}
