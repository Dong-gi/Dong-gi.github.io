package io.github.donggi.iroiroviewer.editor

/**
 * 엔진 자산의 형식 — **우리 표에 있는 확장자만** 내준다(모르는 것은 404). 형식을 짐작하지 않는다: 브라우저는 형식 문자열로 스크립트·스타일을
 * 가르므로, 틀린 형식으로 내주면 엔진이 깨지거나(모듈 스크립트·`instantiateStreaming` 은 형식을 따진다) 내주지 말아야 할 것이 스크립트로 돈다.
 */
object Mime {

    private val table = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "css" to "text/css",
        "json" to "application/json",
        "map" to "application/json",
        "wasm" to "application/wasm",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "ico" to "image/x-icon",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "txt" to "text/plain",
        "xml" to "application/xml",
        "bin" to "application/octet-stream",
    )

    /** 글로 읽는 형식 — 응답에 `charset=utf-8` 을 붙인다. */
    private val text = setOf("text/html", "text/javascript", "text/css", "application/json", "image/svg+xml", "text/plain", "application/xml")

    fun of(path: String): String? {
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        return table[name.substring(dot + 1).lowercase()]
    }

    fun isText(mime: String): Boolean = mime in text
}
