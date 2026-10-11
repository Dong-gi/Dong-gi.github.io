package io.github.donggi.iroiroviewer.editor

import io.github.donggi.iroiroviewer.editor.EditorContract.Kind
import io.github.donggi.iroiroviewer.io.PathRules

/**
 * 돌려줄 파일의 이름 — **순수**(JVM 시험). 쪽이 댄 이름(다른 이름으로 저장에서 사용자가 친 것)을 쓰되 믿지 않는다: 마지막 조각만, 제어 문자와
 * 흔한 파일 시스템(FAT·exFAT·NTFS)이 받지 않는 글자를 빼고, 길이를 묶고, 확장자는 저장한 형식의 것으로 맞춘다.
 *
 * 내보내기([exportName])와 인쇄 작업의 이름([title])도 같은 다듬기를 지난다.
 */
object OutputNames {

    private const val MAX = 120
    private val BAD = Regex("""[\u0000-\u001f\u007f<>:"|?*]""")
    private val DOC_EXTS = Kind.entries.map { it.ext }.toSet() + "hml"

    /**
     * 내보내기가 받는 확장자와 그 형식 — rhwp 스튜디오의 'HTML로 내보내기'·'Word 문서(.doc)로 내보내기'(HTML 로 된 .doc), ONLYOFFICE 의
     * '다운로드'(x2t 가 쓰는 형식 — PDF·OOXML·ODF·RTF·TXT·CSV·EPUB·FB2 와 그 서식 파일). 한글 문서(hwp·hwpx)는 내보내기가 아니라 저장이다.
     */
    private val EXPORT_MIME = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "doc" to "application/msword",
        "pdf" to "application/pdf",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "dotx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ott" to "application/vnd.oasis.opendocument.text-template",
        "rtf" to "application/rtf",
        "txt" to "text/plain",
        "epub" to "application/epub+zip",
        "fb2" to "application/x-fictionbook+xml",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xltx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "ots" to "application/vnd.oasis.opendocument.spreadsheet-template",
        "csv" to "text/csv",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "potx" to "application/vnd.openxmlformats-officedocument.presentationml.template",
        "ppsx" to "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "otp" to "application/vnd.oasis.opendocument.presentation-template",
    )

    /** [pageName] 이 쓸 만하면 그것, 아니면 [fallback](Files 가 준 보이는 이름)으로. 확장자는 [kind] 의 것. */
    fun of(pageName: String?, fallback: String, kind: Kind): String {
        val base = baseOf(pageName) ?: baseOf(fallback) ?: "document"
        return withExtension(base, kind.ext)
    }

    /**
     * 내보낸 파일의 이름 — 확장자가 [EXPORT_MIME] 의 것이 아니면 null(받지 않는다). 이름 부분이 비면 [fallback] 의 이름으로.
     */
    fun exportName(pageName: String?, fallback: String): String? {
        val n = clean(pageName) ?: return null
        val ext = n.substringAfterLast('.', "").lowercase()
        if (ext !in EXPORT_MIME) return null
        val base = trimBase(n.dropLast(ext.length + 1)) ?: baseOf(fallback) ?: "document"
        return withExtension(base, ext)
    }

    /**
     * 이름 부분에 확장자를 잇되 **UTF-8 바이트로** 255(ext4·F2FS 의 이름 상한 — [PathRules.MAX_NAME_BYTES])를 넘지 않게 이름 부분을 줄인다.
     * 받은 문서·내보낸 파일은 이 이름으로 앱 캐시에 **먼저** 쓰인다([HostBridge]) — 글자 수([MAX])로만 묶으면 한글 120자가
     * 360바이트라 그 파일을 만들지 못해, 저장이 파일 쪽에 닿기도 전에 까닭 없는 '저장하지 못했습니다' 로 끝났다(255바이트 이름의 `.hwp`·`.doc` 를
     * `.hwpx`·`.docx` 로 저장할 때도 한 바이트가 넘쳤다). 서로게이트 쌍은 가르지 않는다.
     */
    private fun withExtension(base: String, ext: String): String {
        val room = PathRules.MAX_NAME_BYTES - ".$ext".toByteArray(Charsets.UTF_8).size
        val fitted = PathRules.cutToBytes(base, room).trimEnd { it.isWhitespace() || it == '.' }.ifEmpty { "document" }
        return "$fitted.$ext"
    }

    /** [exportName] 이 돌려준 이름의 형식. */
    fun exportMime(name: String): String =
        EXPORT_MIME[name.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

    /** 인쇄 작업의 이름 — 시스템 인쇄 창이 보이고, 'PDF로 저장' 이 처음 내놓는 파일 이름이 된다. 문서 확장자는 뗀다. */
    fun title(pageName: String?, fallback: String): String = baseOf(pageName) ?: baseOf(fallback) ?: "document"

    private fun baseOf(name: String?): String? {
        var n = clean(name) ?: return null
        val ext = n.substringAfterLast('.', "")
        if (ext.isNotEmpty() && ext.lowercase() in DOC_EXTS) n = n.dropLast(ext.length + 1)
        return trimBase(n)
    }

    /** 마지막 조각만, 받지 않는 글자를 뺀다. */
    private fun clean(name: String?): String? {
        if (name == null) return null
        val n = BAD.replace(name.substringAfterLast('/').substringAfterLast('\\'), "").trim()
        return n.ifEmpty { null }
    }

    /** 앞뒤의 점·빈칸 — 윈도·FAT 가 끝의 점을 버리고, 앞의 점은 숨은 파일이 된다. 길이를 묶는다. */
    private fun trimBase(base: String): String? {
        var n = base.trim().trim('.').trim()
        // 서로게이트 쌍의 앞 반쪽에서 자르지 않는다 — 파일 쪽(`EditorSlots.cleanName`)도 같은 자리에서 자른다(같은 이름 판정).
        if (n.length > MAX) n = n.take(if (Character.isHighSurrogate(n[MAX - 1])) MAX - 1 else MAX).trim()
        return n.ifEmpty { null }
    }
}
