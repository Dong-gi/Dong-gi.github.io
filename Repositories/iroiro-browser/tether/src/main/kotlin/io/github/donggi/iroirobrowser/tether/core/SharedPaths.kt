package io.github.donggi.iroirobrowser.tether.core

/**
 * 파일 서버의 경로 규약과 이름 도우미.
 *
 * API 가 받는 `path` 는 공유 폴더를 맨 위로 하는 **상대 경로**이고 구분자는 `/` 다. 빈 글이 맨 위다. [SharedTree] 는 성분마다 자식만 찾아
 * 내려가므로 트리 밖으로 나갈 길이 구조로 없지만, 여기서도 막는다 — 이 검사가 없으면 나중에 구현을 파일 경로 기반으로 바꿀 때 조용히 약해진다.
 */
object SharedPaths {

    /** 경로 성분의 최대 개수 — 끝없이 깊은 순회로 IPC 를 쏟아붓지 못하게. */
    const val MAX_SEGMENTS = 32

    /**
     * 상대 경로를 성분으로 쪼갠다. 거절: 절대 경로(`/` 로 시작), `.`·`..` 성분, 빈 성분, 역슬래시, NUL, 성분 수 초과. 빈 경로는 빈 목록(맨 위).
     * 공백은 정상적인 파일 이름 글자라 막지 않는다. NUL 은 네이티브 층에서 글을 자르는 데 쓰일 수 있어 막는다.
     *
     * @return 성분 목록. 경로가 틀렸으면 null
     */
    fun split(path: String): List<String>? {
        if (path.isEmpty()) return emptyList()
        if (path.startsWith("/")) return null
        if (path.contains('\\')) return null
        if (path.contains('\u0000')) return null
        val segments = path.split('/')
        if (segments.size > MAX_SEGMENTS) return null
        for (segment in segments) {
            if (segment.isEmpty()) return null
            if (segment == "." || segment == "..") return null
        }
        return segments
    }

    /** 올리기를 받는 동안의 임시 이름의 끝. */
    const val UPLOAD_TEMP_SUFFIX = ".part"

    /**
     * 올리기를 받는 동안의 임시 이름 — 숨은 파일(점으로 시작)이고 끝이 [UPLOAD_TEMP_SUFFIX] 다(`사진.jpg` → `.사진.jpg.part`). 다 받은 뒤에야
     * 원래 이름으로 바꾼다([SharedTree.commitUpload]) — 끊긴 올리기가 원래 파일을 건드리지 않게.
     */
    fun uploadTempName(name: String): String = ".$name$UPLOAD_TEMP_SUFFIX"

    /**
     * 확장자로 MIME 형식을 어림한다. 모르면 `application/octet-stream`(내려받기로 처리된다 — 파일을 옮기는 용도에는 그것이 맞다). 플랫폼의
     * `MimeTypeMap` 을 쓰지 않는다 — 모르는 확장자에 null 을 주는데, SAF 의 파일 만들기는 null 을 받지 않는다.
     */
    fun guessMimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MIME_BY_EXTENSION[extension] ?: "application/octet-stream"
    }

    /**
     * RFC 3986 의 unreserved 밖의 모든 바이트를 퍼센트 부호로 바꾼다(`Content-Disposition` 의 `filename*` — RFC 6266. 한글 이름이 깨지지
     * 않으려면 이 꼴이어야 한다). `URLEncoder` 는 공백을 `+` 로 바꾸는 폼 부호라 여기에 틀리다. 16진은 손으로 짠다 — `String.format` 은 기본
     * 로케일을 탄다.
     */
    fun percentEncode(text: String): String {
        val builder = StringBuilder()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (value < 0x80 && (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9') || char in "-_.~") {
                builder.append(char)
            } else {
                builder.append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
            }
        }
        return builder.toString()
    }

    private const val HEX = "0123456789ABCDEF"

    /** 브라우저가 페이지 안에서 열 수 있는 흔한 형식만. */
    private val MIME_BY_EXTENSION = mapOf(
        "txt" to "text/plain", "md" to "text/plain", "log" to "text/plain",
        "csv" to "text/csv", "json" to "application/json", "xml" to "text/xml",
        "html" to "text/html", "htm" to "text/html", "css" to "text/css",
        "js" to "text/javascript",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "svg" to "image/svg+xml", "heic" to "image/heic",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "ogg" to "audio/ogg",
        "flac" to "audio/flac", "wav" to "audio/wav",
        "mp4" to "video/mp4", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "pdf" to "application/pdf", "zip" to "application/zip",
        "apk" to "application/vnd.android.package-archive",
    )
}
