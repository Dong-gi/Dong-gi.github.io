package io.github.donggi.iroiroviewer.editor

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 가로채기(`shouldInterceptRequest`)가 묻는 모든 요청에 답한다 — **우리 엔진의 출처가 아니거나 GET 이 아니면 빈 404**. 그 밖에는:
 *
 * - `/__io/in/<표>` — [DocumentStore] 가 떠 둔 문서.
 * - `/__io/page/<표>` — 인쇄할 쪽([PrintJob] — 엔진이 지은 HTML). 엔진의 CSP 를 붙여 HTML 로 내준다.
 * - 그 밖의 경로 — APK 의 `assets/engines/<엔진>/` 아래 자산([AssetPaths] 로 묶음 밖을 막고, [Mime] 표에 없는 확장자는 404).
 *
 * 응답에는 늘 `Cache-Control: no-store`·`X-Content-Type-Options: nosniff`, HTML 에는 엔진의 CSP 를 붙인다 — 묶음이 지은 `csp.txt` 가 있으면
 * 그것(ONLYOFFICE — 인라인 스크립트의 해시), 없으면 [Engine.csp]. 가로챈 응답은 어차피 HTTP 캐시에 들지 않는다(Chromium 의 `ShouldCacheResponse` 가
 * 거짓 — 조사).
 *
 * WebView 의 IO 스레드에서 불린다(동시에 여럿) — 상태를 들지 않는다.
 */
class AssetServer(
    private val assets: AssetManager,
    private val engine: Engine,
    private val documents: DocumentStore,
) {

    /** 이 엔진의 HTML 에 붙이는 정책 — 만들 때 한 번 읽는다(작은 파일). 여러 줄이면 머리로 실을 수 없어 버린다. */
    private val csp: String = try {
        assets.open("engines/${engine.id}/$CSP_FILE").bufferedReader().use { it.readText() }.trim().takeIf { it.isNotEmpty() && '\n' !in it }
    } catch (e: IOException) {
        null
    } ?: engine.csp

    fun serve(request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        if (url.scheme != "https" || url.host != engine.host || request.method != "GET") return notFound()
        val path = url.encodedPath
        if (path != null && path.startsWith(IO_IN)) return document(path.substring(IO_IN.length))
        if (path != null && path.startsWith(IO_PAGE)) return page(path.substring(IO_PAGE.length))
        val rel = AssetPaths.resolve(path, engine.entry) ?: return notFound()
        val mime = Mime.of(rel) ?: return notFound()
        val asset = engine.assetPath(rel) ?: return notFound()
        val stream = try {
            assets.open(asset, AssetManager.ACCESS_STREAMING)
        } catch (e: IOException) {
            return notFound()
        }
        return ok(mime, stream, html = mime == "text/html")
    }

    private fun document(token: String): WebResourceResponse {
        val file = documents.fileOf(token) ?: return notFound()
        val stream = try {
            FileInputStream(file)
        } catch (e: IOException) {
            return notFound()
        }
        return ok("application/octet-stream", stream, html = false)
    }

    private fun page(token: String): WebResourceResponse {
        val file = documents.pageOf(token) ?: return notFound()
        val stream = try {
            FileInputStream(file)
        } catch (e: IOException) {
            return notFound()
        }
        return ok("text/html", stream, html = true)
    }

    private fun ok(mime: String, stream: InputStream, html: Boolean): WebResourceResponse {
        val headers = baseHeaders()
        if (html) headers["Content-Security-Policy"] = csp
        return WebResourceResponse(mime, if (Mime.isText(mime)) "utf-8" else null, 200, "OK", headers, stream)
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 404, "Not Found", baseHeaders(), ByteArrayInputStream(ByteArray(0)))

    private fun baseHeaders(): MutableMap<String, String> = mutableMapOf(
        "Cache-Control" to "no-store",
        "X-Content-Type-Options" to "nosniff",
    )

    companion object {
        /** 문서를 받는 길. 엔진의 자산 경로와 겹치지 않게 밑줄 둘로 연다. */
        const val IO_IN = "/__io/in/"

        /** 인쇄할 쪽을 받는 길([PrintJob]). */
        const val IO_PAGE = "/__io/page/"

        /** 묶음이 지은 엔진의 CSP(한 줄) — tools/build_onlyoffice.py. 쪽에는 내주지 않는다([Engine.assetPath]). */
        const val CSP_FILE = "csp.txt"
    }
}
