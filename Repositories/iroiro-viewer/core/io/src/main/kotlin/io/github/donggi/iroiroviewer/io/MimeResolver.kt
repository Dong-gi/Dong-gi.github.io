package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.FileKind

/**
 * 확장자로 종류와 MIME 을 정한다.
 *
 * 안드로이드의 `MimeTypeMap` 을 쓰지 않는 이유는 이 앱이 다루려는 것의 상당수를
 * 그것이 모르기 때문이다 — `mkv`·`flac`·`opus`·`cbz`·`cbr`·`hwp`·`hwpx`·`7z` 가 기기에
 * 따라 빈 문자열로 온다. 우리가 여는 포맷의 표는 우리가 가지고 있어야 한다.
 *
 * **목록을 그릴 때 파일을 열지 않는다.** 확장자만 본다. 내용으로 하는 판별(매직 바이트)은
 * 실제로 열 때 하고, 그때는 확장자가 거짓말을 해도 내용이 이긴다.
 */
object MimeResolver {

    private val image = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "ico", "svg", "tif", "tiff", "dng",
    )
    private val audio = setOf(
        "mp3", "flac", "opus", "ogg", "oga", "m4a", "aac", "wav", "wma", "alac", "aiff", "ape", "mka", "amr", "mid", "midi",
    )
    private val video = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "m4v", "3gp", "ts", "mpg", "mpeg", "m2ts", "rmvb", "ogv",
    )
    private val text = setOf(
        "txt", "log", "md", "markdown", "csv", "tsv", "srt", "ass", "ssa", "vtt", "smi", "nfo", "ini", "cfg", "conf",
    )
    private val code = setOf(
        "kt", "kts", "java", "js", "mjs", "ts", "tsx", "jsx", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cc",
        "cs", "swift", "php", "sh", "bash", "zsh", "bat", "ps1", "sql", "json", "xml", "yaml", "yml", "toml",
        "html", "htm", "css", "scss", "gradle", "pug", "vue", "dart", "lua", "pl", "r", "m", "mm",
    )
    private val archive = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "jar", "iso")
    private val comic = setOf("cbz", "cbr", "cb7", "cbt")
    private val document = setOf("doc", "docx", "odt", "rtf")
    private val sheet = setOf("xls", "xlsx", "ods")
    private val slide = setOf("ppt", "pptx", "odp")
    private val hwp = setOf("hwp", "hwpx", "hwt")
    private val ebook = setOf("epub", "mobi", "azw", "azw3", "fb2")
    private val font = setOf("ttf", "otf", "woff", "woff2", "ttc")

    /** 소문자 확장자. 점 없음. 없으면 빈 문자열. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun kindOf(name: String, isDirectory: Boolean): FileKind {
        if (isDirectory) return FileKind.FOLDER
        return when (extensionOf(name)) {
            in image -> FileKind.IMAGE
            in audio -> FileKind.AUDIO
            in video -> FileKind.VIDEO
            in comic -> FileKind.COMIC
            in archive -> FileKind.ARCHIVE
            in code -> FileKind.CODE
            in text -> FileKind.TEXT
            in document -> FileKind.DOCUMENT
            in sheet -> FileKind.SHEET
            in slide -> FileKind.SLIDE
            in hwp -> FileKind.HWP
            in ebook -> FileKind.EBOOK
            in font -> FileKind.FONT
            "pdf" -> FileKind.PDF
            "apk" -> FileKind.APK
            else -> FileKind.OTHER
        }
    }

    /**
     * 다른 앱으로 넘길 때 쓸 MIME. 모르면 `application/octet-stream`.
     *
     * 표에 있는 것만 정확히 적고 나머지는 종류의 대표값으로 준다 — 거짓 MIME 을 주면
     * 받는 앱이 엉뚱하게 처리한다.
     */
    fun mimeOf(name: String): String = when (val ext = extensionOf(name)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heif"
        "avif" -> "image/avif"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "opus", "ogg", "oga" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "zip", "cbz" -> "application/zip"
        "7z", "cb7" -> "application/x-7z-compressed"
        "rar", "cbr" -> "application/vnd.rar"
        "apk" -> "application/vnd.android.package-archive"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "hwp" -> "application/x-hwp"
        "hwpx" -> "application/hwp+zip"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        else -> when {
            ext.isEmpty() -> "application/octet-stream"
            kindOf(name, false) == FileKind.TEXT || kindOf(name, false) == FileKind.CODE -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
