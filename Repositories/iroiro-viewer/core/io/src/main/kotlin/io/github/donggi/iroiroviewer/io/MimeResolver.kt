package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.FileKind
import java.util.Locale

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

    // `jfif`·`jpe` 는 JPEG 의 다른 이름이고(웹에서 받은 사진에 흔하다) `apng`·`wbmp` 도 플랫폼 디코더가 읽는다 — 내용으로
    // 디코딩하므로 이름만 알면 된다. `svg`·`svgz` 는 플랫폼 디코더가 없어 그림 뷰어가 잠긴 WebView 로 그린다.
    private val image = setOf(
        "jpg", "jpeg", "jfif", "jpe", "png", "apng", "gif", "webp", "bmp", "wbmp", "heic", "heif", "avif", "ico",
        "svg", "svgz", "tif", "tiff", "dng",
    )
    // 표에 더한 것은 **media3 의 추출기가 이미 읽는** 것이다 — `m4b`·`m4r`·`3ga` 는 MP4, `awb` 는 AMR-WB, `weba` 는
    // WebM, `ac3`·`eac3` 는 돌비 원시 스트림(디코더는 기기에 달렸다), `mp2` 는 MPEG 층 II(MP3 추출기가 읽는다 — 방송 녹화의
    // 소리를 뽑으면 나온다). 추출기가 없는 것은 더하지 않았다(`aif`·`dts`).
    private val audio = setOf(
        "mp3", "mp2", "flac", "opus", "ogg", "oga", "m4a", "m4b", "m4r", "3ga", "aac", "wav", "wma", "alac", "aiff", "ape",
        "mka", "weba", "amr", "awb", "ac3", "eac3", "mid", "midi",
    )
    // `m2t` 는 HDV 캠코더의 MPEG-TS, `3g2`·`3gpp`·`f4v`·`qt` 는 MP4·QuickTime, `vob` 은 MPEG-PS, `divx` 는 AVI 다.
    // **`ts` 는 TypeScript 와 확장자를 나눠 쓴다** — 목록은 파일을 열지 않으므로 영상으로 적고, 누를 때 내용으로
    // 가른다(`ContentSniff.isAmbiguousStream`).
    //
    // **`mts` 는 여기 없다**(기타다). 캠코더(AVCHD)의 `.mts` 는 192바이트 패킷의 M2TS 라 media3 가 틀지 못한다 — 영상으로 적으면
    // 재생 화면이 실패하고 나서야 다른 앱으로 넘어가고, 폴더의 ▶ 가 전부 실패할 큐를 만든다(이 회차 전에는 기타라
    // 누르면 곧바로 다른 앱이었다). 그래서 기타로 두고, TypeScript 의 `.mts` 만 누를 때 내용으로 가려 텍스트 뷰어로 보낸다.
    private val video = setOf(
        "mp4", "mkv", "webm", "avi", "divx", "mov", "qt", "wmv", "flv", "f4v", "m4v", "3gp", "3gpp", "3g2", "ts",
        "m2t", "mpg", "mpeg", "vob", "m2ts", "rmvb", "ogv",
    )
    private val text = setOf(
        "txt", "log", "md", "markdown", "mdown", "mkd", "mkdn", "mdwn", "csv", "tsv", "srt", "ass", "ssa", "vtt", "smi",
        "ttml", "dfxp", "nfo", "ini", "cfg", "conf",
    )
    // `ts`·`mts` 는 여기 없다 — 영상(MPEG-TS)과 기타(AVCHD)로 본다(위). 두 표에 함께 두었을 때도 먼저 맞는 영상이 이겨(그때는
    // `when` 이 골랐다) 이 자리는 한 번도 쓰이지 않았다. 겹쳐도 아무도 말하지 않으므로 시험이 겹침을 막는다.
    //
    // 줄 둘째부터는 **텍스트 뷰어가 이미 강조하는** 확장자다(`format:text` 의 `TextLanguage`). 목록이 몰라 다른 앱으로
    // 넘기고 있었다. 두 표가 어긋나지 않게 `app` 의 시험(`TextNamesConsistencyTest`)이 견준다.
    private val code = setOf(
        "kt", "kts", "java", "js", "mjs", "tsx", "jsx", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cc",
        "cs", "swift", "php", "sh", "bash", "zsh", "bat", "ps1", "sql", "json", "xml", "yaml", "yml", "toml",
        "html", "htm", "css", "scss", "gradle", "pug", "vue", "dart", "lua", "pl", "r", "m", "mm",
        "aidl", "cxx", "hh", "hxx", "inl", "cjs", "cts", "phtml", "pyi", "pyw", "gemspec", "rake", "ksh",
        "less", "sass", "json5", "jsonc", "webmanifest", "properties", "env", "xsd", "xsl", "xslt", "plist", "resx",
        "csproj", "vcxproj", "pom", "xhtml", "profile", "bashrc", "zshrc", "editorconfig",
    )
    // 둘째 줄은 **지금 라이브러리가 순수 JVM 으로 읽는** 것이다 — 단독 압축 스트림(`lz4`·`lzma`·`z` 는 commons-compress,
    // `gz`·`bz2`·`xz` 는 원래 있었다)과 그것으로 싼 tar(`tlz`·`taz`), `cpio`, `ar`(·`deb`), ZIP 인 `war`·`ear`·`aar`.
    // 무엇으로 여는지는 내용이 정한다(`Archives.detect`).
    private val archive = setOf(
        "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "tbz", "txz", "jar", "iso",
        "lz4", "lzma", "tlz", "z", "taz", "cpio", "ar", "deb", "war", "ear", "aar",
    )
    private val comic = setOf("cbz", "cbr", "cb7", "cbt")
    // 매크로(`m`)·서식(`t`·`x`)·쇼(`pps`) 파일도 같은 종류다. 빠뜨리면 목록이 '기타' 로 그려 **뷰어에 닿지 않는다**
    // — 여는이(`OoxmlProbe`)는 여는데 누를 길이 없었다.
    private val document = setOf("doc", "docx", "docm", "dot", "dotx", "dotm", "odt", "rtf")
    private val sheet = setOf("xls", "xlsx", "xlsm", "xlt", "xltx", "xltm", "ods")
    private val slide = setOf("ppt", "pptx", "pptm", "pot", "potx", "potm", "pps", "ppsx", "ppsm", "odp")
    // `hwtx` 는 HWPX 서식, `owpml` 은 한글 2018 부터의 OWPML 이름이다(HWPX 와 같은 모양).
    private val hwp = setOf("hwp", "hwpx", "hwt", "hwtx", "owpml")
    private val ebook = setOf("epub", "mobi", "azw", "azw3", "fb2")
    private val font = setOf("ttf", "otf", "woff", "woff2", "ttc")

    /**
     * 확장자가 아니라 **이름으로** 정하는 글 파일. 텍스트 뷰어가 이름으로 강조하는 것들이다(`TextLanguage` 의 이름 표) —
     * 확장자가 없거나(`Makefile`·`Dockerfile`) 점으로 시작해(`.gitignore`·`.env`) [extensionOf] 가 빈 글을 준다. 소문자로 견준다.
     */
    private val codeNames = setOf(
        "dockerfile", "containerfile", "makefile", "gnumakefile", ".bashrc", ".zshrc", ".profile", ".bash_profile",
        ".gitignore", ".gitattributes", ".editorconfig", ".npmrc", ".env",
    )

    /** 이름으로 정하는 글 파일 표. 시험(`TextNamesConsistencyTest`)이 텍스트 뷰어의 이름 표와 견준다. */
    internal val codeNameTable: Set<String> get() = codeNames

    /**
     * 종류별 확장자 표. **[kindOf] 가 이 표 하나로 답한다** — 예전에는 `kindOf` 가 같은 차례의 `when` 을 따로 두어,
     * `when` 에만 더한 갈래는 표를 훑는 시험을 비켜 갔다(두 벌은 한쪽만 고쳐진다). 한 확장자가 두 표에 들면 앞의 것이
     * 이기고 아무도 말하지 않으므로, 겹침은 JVM 시험(`MimeResolverTableTest`)이 막는다.
     */
    internal val extensionTable: List<Pair<FileKind, Set<String>>> = listOf(
        FileKind.IMAGE to image, FileKind.AUDIO to audio, FileKind.VIDEO to video, FileKind.COMIC to comic,
        FileKind.ARCHIVE to archive, FileKind.CODE to code, FileKind.TEXT to text, FileKind.DOCUMENT to document,
        FileKind.SHEET to sheet, FileKind.SLIDE to slide, FileKind.HWP to hwp, FileKind.EBOOK to ebook,
        FileKind.FONT to font, FileKind.PDF to setOf("pdf"), FileKind.APK to setOf("apk"),
    )

    // 목록의 줄마다 불리므로 표를 한 번 뒤집어 둔다(찾기 한 번). 겹치면 앞의 표가 이긴다 — 예전 `when` 과 같은 답.
    private val kindByExtension: Map<String, FileKind> = HashMap<String, FileKind>().apply {
        for ((kind, exts) in extensionTable) for (ext in exts) putIfAbsent(ext, kind)
    }

    /** 소문자 확장자. 점 없음. 없으면 빈 문자열. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun kindOf(name: String, isDirectory: Boolean): FileKind {
        if (isDirectory) return FileKind.FOLDER
        kindByExtension[extensionOf(name)]?.let { return it }
        // 표에 없는 이름만 이름 표를 본다 — 목록의 줄마다 불리므로 소문자 바꾸기를 아낀다.
        return if (name.lowercase(Locale.ROOT) in codeNames) FileKind.CODE else FileKind.OTHER
    }

    /**
     * 다른 앱으로 넘길 때 쓸 MIME. 모르면 `application/octet-stream`.
     *
     * 표에 있는 것만 정확히 적고 나머지는 종류의 대표값으로 준다 — 거짓 MIME 을 주면
     * 받는 앱이 엉뚱하게 처리한다. 값은 IANA 에 등록된 것을 먼저, 없으면 널리 쓰이는 `x-` 값을 쓴다.
     * 열기(`ACTION_VIEW`)는 모를 때 한 번 더 좁혀 간다(`ExternalMime`) — 이 표가 `octet-stream` 을 주면 받는 앱이
     * 거의 없기 때문이다. 이 앱이 열지 않는 형식(`.doc`·`.odt`·`.mobi`·글꼴·`.iso`)도 여기 적는 까닭이 그것이다.
     *
     * **전부 소문자로 적는다.** 안드로이드의 MIME 대조는 대소문자를 가리고(`IntentFilter.addDataType` 의 문서가
     * '언제나 소문자로 적어라' 라고 한다), 플랫폼 표(`MimeTypeMap`)와 `FileProvider.getType` 이 주는 값도 소문자다.
     * 마이크로소프트가 등록한 모양(`macroEnabled` 처럼 대문자가 섞인 것)을 그대로 적으면 소문자로 선언한 앱의 필터를 비켜 간다.
     *
     * 플랫폼 표와 다른 값이 있다(API 31 에뮬레이터 이미지의 표를 직접 읽어 견줬다) — `.m4a` 는 플랫폼이
     * `audio/mpeg`, `.avi` 는 `video/avi`, `.rar` 은 `application/rar`, `.cbz` 는 `application/x-cbz` 다. 그림·소리·영상은
     * 받는 앱이 계열 전체로 거르므로 어느 쪽이든 같은 앱이 나오고, 나머지는 등록된 값을 지킨다.
     */
    fun mimeOf(name: String): String = when (extensionOf(name)) {
        "jpg", "jpeg", "jfif", "jpe" -> "image/jpeg"
        "apng" -> "image/apng"
        "wbmp" -> "image/vnd.wap.wbmp"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "avif" -> "image/avif"
        "svg", "svgz" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "tif", "tiff" -> "image/tiff"
        "ico" -> "image/vnd.microsoft.icon"
        "dng" -> "image/x-adobe-dng"
        "mp3", "mp2" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "opus", "ogg", "oga" -> "audio/ogg"
        "m4a", "m4b", "m4r" -> "audio/mp4"
        "3ga" -> "audio/3gpp"
        "weba" -> "audio/webm"
        "awb" -> "audio/amr-wb"
        "ac3" -> "audio/ac3"
        "eac3" -> "audio/eac3"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "wma" -> "audio/x-ms-wma"
        "mka" -> "audio/x-matroska"
        "amr" -> "audio/amr"
        "mid", "midi" -> "audio/midi"
        "aiff" -> "audio/x-aiff"
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov", "qt" -> "video/quicktime"
        "ts", "mts", "m2t", "m2ts" -> "video/mp2t"
        "3gp", "3gpp" -> "video/3gpp"
        "3g2" -> "video/3gpp2"
        "f4v" -> "video/mp4"
        "vob" -> "video/mpeg"
        "divx" -> "video/x-msvideo"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "mpg", "mpeg" -> "video/mpeg"
        "ogv" -> "video/ogg"
        "rmvb" -> "application/vnd.rn-realmedia-vbr"
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw" -> "application/vnd.amazon.ebook"
        "azw3" -> "application/vnd.amazon.mobi8-ebook"
        "fb2" -> "application/x-fictionbook+xml"
        "zip", "cbz" -> "application/zip"
        "7z", "cb7" -> "application/x-7z-compressed"
        "rar", "cbr" -> "application/vnd.rar"
        "tar", "cbt" -> "application/x-tar"
        "gz", "tgz" -> "application/gzip"
        "bz2", "tbz2", "tbz" -> "application/x-bzip2"
        "xz", "txz" -> "application/x-xz"
        "iso" -> "application/x-iso9660-image"
        "jar", "war", "ear" -> "application/java-archive"
        "aar" -> "application/zip"
        "lz4" -> "application/x-lz4"
        "lzma" -> "application/x-lzma"
        "tlz" -> "application/x-lzma-compressed-tar"
        "z" -> "application/x-compress"
        "taz" -> "application/x-tarz"
        "cpio" -> "application/x-cpio"
        "ar" -> "application/x-archive"
        "deb" -> "application/vnd.debian.binary-package"
        "apk" -> "application/vnd.android.package-archive"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "doc", "dot" -> "application/msword"
        "docm" -> "application/vnd.ms-word.document.macroenabled.12"
        "dotx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
        "dotm" -> "application/vnd.ms-word.template.macroenabled.12"
        "xls", "xlt" -> "application/vnd.ms-excel"
        "xlsm" -> "application/vnd.ms-excel.sheet.macroenabled.12"
        "xltx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.template"
        "xltm" -> "application/vnd.ms-excel.template.macroenabled.12"
        "ppt", "pot", "pps" -> "application/vnd.ms-powerpoint"
        "pptm" -> "application/vnd.ms-powerpoint.presentation.macroenabled.12"
        "potx" -> "application/vnd.openxmlformats-officedocument.presentationml.template"
        "potm" -> "application/vnd.ms-powerpoint.template.macroenabled.12"
        "ppsx" -> "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
        "ppsm" -> "application/vnd.ms-powerpoint.slideshow.macroenabled.12"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        "rtf" -> "application/rtf"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttc" -> "font/collection"
        "hwp" -> "application/x-hwp"
        "hwpx" -> "application/hwp+zip"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        // 표에 없는 글·코드는 `text/plain` 이다 — 확장자가 없어도 이름 표가 글이라고 하면(`Makefile`) 그렇다.
        else -> when (kindOf(name, false)) {
            FileKind.TEXT, FileKind.CODE -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
