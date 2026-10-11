package io.github.donggi.iroirobrowser.core

import java.util.Locale

/**
 * 내려받을 파일의 이름을 정한다: `Content-Disposition` > 주소의 마지막 토막 > 호스트 이름 > `download`. 확장자가 없거나 형식과 어긋나면
 * 형식의 확장자를 붙인다(대강의 형식 — `octet-stream` 류 — 이면 손대지 않는다).
 *
 * 형식 ↔ 확장자 표는 바깥에서 준다(안드로이드의 `MimeTypeMap`, 시험에서는 작은 표).
 */
class DownloadNames(
    private val extensionForMime: (String) -> String?,
    private val mimeForExtension: (String) -> String?,
) {

    fun choose(contentDisposition: String?, url: String, mimeType: String?): String {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val base = ContentDisposition.fileName(contentDisposition)?.let(SafeFileName::clean)?.takeUnless { it == SafeFileName.FALLBACK }
            ?: fromUrl(url)
        return SafeFileName.clean(withExtension(base, mime))
    }

    /**
     * 이름은 이미 정해졌다(쪽이 준 `download` 속성·사용자가 고친 이름) — 저장해도 되는 꼴로 하고 확장자만 형식에 맞춘다(없으면 형식의 것을
     * 붙인다). 앞 판은 확장자 없는 blob 이름을 가짜 주소에 실어 [choose] 로 넘겨 이름을 통째로 잃었다('download.mp4').
     */
    fun named(name: String, mimeType: String?): String {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        return SafeFileName.clean(withExtension(SafeFileName.clean(name), mime))
    }

    private fun fromUrl(url: String): String {
        if (url.startsWith("data:", ignoreCase = true) || url.startsWith("blob:", ignoreCase = true)) return SafeFileName.FALLBACK
        val noFragment = url.substringBefore('#').substringBefore('?')
        val afterScheme = noFragment.substringAfter("://", noFragment)
        val host = afterScheme.substringBefore('/').substringAfterLast('@').substringBefore(':')
        val path = if ('/' in afterScheme) afterScheme.substring(afterScheme.indexOf('/')) else ""
        val last = path.trimEnd('/').substringAfterLast('/')
        val decoded = PercentCoding.decodeUtf8(last) ?: last
        val cleaned = SafeFileName.clean(decoded)
        if (cleaned != SafeFileName.FALLBACK) return cleaned
        return if (host.isNotEmpty()) SafeFileName.clean(host) else SafeFileName.FALLBACK
    }

    private fun withExtension(name: String, mime: String?): String {
        if (mime == null || mime in GENERIC) return name
        val expected = extensionForMime(mime) ?: return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot + 1).lowercase(Locale.ROOT) else ""
        if (ext.isEmpty()) return "$name.$expected"
        if (ext == expected) return name
        val extMime = mimeForExtension(ext)
        // 같은 갈래(image/…·video/…)면 이름의 확장자를 믿는다(jpg 와 jpeg, 서버가 갈래 안에서 틀리는 일이 흔하다).
        if (extMime != null && extMime.substringBefore('/') == mime.substringBefore('/')) return name
        // text/plain 은 서버가 아무 데나 붙이는 일이 흔하다 — 이름에 확장자가 있으면 그것을 믿는다.
        if (mime == "text/plain") return name
        return "$name.$expected"
    }

    companion object {
        /**
         * 확인 창에서 사용자가 고친 이름 — 저장해도 되는 꼴로. 비웠으면 [original] 그대로, 확장자를 지웠으면 [original] 의 것을 다시 붙인다
         * (형식을 잃지 않게 — 받는 쪽이 확장자로 형식을 정한다).
         */
        fun edited(typed: String, original: String): String {
            val clean = SafeFileName.clean(typed)
            if (typed.isBlank() || clean == SafeFileName.FALLBACK) return original
            val ext = extensionOf(original) ?: return clean
            return if (extensionOf(clean) == null) SafeFileName.clean("$clean.$ext") else clean
        }

        /** 이름 끝의 확장자 — 마지막 점 뒤가 글자·숫자 1~5자일 때만(`v1.2 clip` 의 `2 clip`·제목의 `1.5 - 사이트` 는 아니다). */
        private fun extensionOf(name: String): String? {
            val dot = name.lastIndexOf('.')
            if (dot <= 0) return null
            return name.substring(dot + 1).takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
        }

        val GENERIC = setOf(
            "application/octet-stream", "binary/octet-stream", "application/force-download", "application/x-download",
            "application/download", "application/unknown", "application/x-msdownload-unknown",
        )
    }
}
