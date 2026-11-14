package io.github.donggi.iroirobrowser.browser

import android.os.Handler
import android.os.Looper
import io.github.donggi.iroirobrowser.core.Streams
import io.github.donggi.iroirobrowser.core.media.HlsPlaylist
import io.github.donggi.iroirobrowser.download.HopHeaders
import io.github.donggi.iroirobrowser.net.HttpFetcher
import io.github.donggi.iroirobrowser.proxy.NetworkPath
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * 받기 전에 묻는 작은 것 — 영상 파일의 크기(받기 확인 창)와 HLS 재생목록이 마스터인지(찾은 미디어에서
 * 그 아래 재생목록을 숨긴다).
 *
 * **내려받기와 같은 길**로 묻는다 — 앱의 HTTP, 길은 [NetworkPath.httpRoute](중계, 예외 규칙에 든 사설망 주소는 바로 — 홉마다. 프록시를 켰는데
 * 중계가 없으면 묻지 않는다, 직접 나가지 않는다),
 * 머리는 내려받기와 같은 [HopHeaders.fromPage] — 쪽의 User-Agent·Referer(홉마다 쪽에서 짓는다)·그 주소의 쿠키. 내려받기의 일꾼과 따로 돌고(받는
 * 중에도 바로 답한다) 다시 해 보지 않으며 시간 제한도 짧다 — 답이 없으면 '모름' 일 뿐이다.
 */
class MediaProbe(private val network: NetworkPath) {

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "web-probe").apply { isDaemon = true } }

    /**
     * 크기(바이트) — `Range: bytes=0-0` 에 대한 `Content-Range` 의 끝, 범위를 모르는 서버(200)면 `Content-Length`. 모르면 -1. 답은 메인 스레드로.
     * 본문은 읽지 않는다(범위를 모르는 서버가 통째로 보내도 곧 끊는다). [page] 는 그 미디어를 찾은 쪽 — Referer 를 홉마다 그것에서 짓는다.
     */
    fun size(url: String, userAgent: String, page: String?, done: (Long) -> Unit) = submit(done, -1L) {
        fetcher().get(url, headersFor = { u -> HopHeaders.fromPage(userAgent, page, u, RANGE_FIRST_BYTE) }, onSetCookie = HopHeaders.storeCookies).use { r ->
            when (r.code) {
                206 -> RANGE_TOTAL.find(r.header("Content-Range").orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: -1L
                200 -> r.contentLength
                else -> -1L
            }
        }
    }

    /** HLS 재생목록을 읽는다 — 읽지 못하면 null. 넘겨주기를 따랐으면 상대 주소는 마지막 주소에 대어 푼다(재생기도 그렇게 푼다). [page] 는 [size] 와 같다. */
    fun playlist(url: String, userAgent: String, page: String?, done: (HlsPlaylist?) -> Unit) = submit(done, null) {
        fetcher().get(url, headersFor = { u -> HopHeaders.fromPage(userAgent, page, u) }, onSetCookie = HopHeaders.storeCookies).use { r ->
            if (r.code != 200) return@use null
            // 상한을 넘으면 재생목록이 아니다 — 모름.
            val bytes = try {
                Streams.readAll(r.body, MAX_PLAYLIST_BYTES)
            } catch (e: Streams.TooLarge) {
                return@use null
            }
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

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
        val RANGE_FIRST_BYTE = mapOf("Range" to "bytes=0-0")
        val RANGE_TOTAL = Regex("""bytes\s+\d+-\d+/(\d+)""")
    }
}
