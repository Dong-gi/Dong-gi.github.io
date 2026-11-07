package io.github.donggi.iroirobrowser.download

import android.content.Context
import java.util.Locale

/**
 * 내려받기 화면·알림의 글을 고르는 꼴 — 자원 번호와 인자([Part])로만 골라 기기 없이 시험하고, 글로 짓는 것은 [text] 하나다. 갤러리 받기
 * ([GalleryRules])·열쇠를 받지 못한 글([HlsKeyTexts])이 쓴다.
 */
internal object TextParts {

    /** 공용 '내려받기' 폴더의 이름(`Environment.DIRECTORY_DOWNLOADS` 와 같은 값 — 그것은 시험의 껍데기 android.jar 에서 읽히지 않는다). */
    const val DOWNLOAD_DIR = "Download"

    /** 글 조각 — 자원과 그 인자. 인자가 [Part] 면 그것을 먼저 글로 짓는다. */
    class Part(val res: Int, vararg args: Any) {
        val args: List<Any> = args.toList()

        override fun toString(): String = "Part($res, $args)"
    }

    /** 바이트 수를 글로 — 화면은 `Formatter.formatShortFileSize`, 시험은 가짜. */
    fun interface Sizes {
        fun format(bytes: Long): String
    }

    fun text(context: Context, part: Part): String {
        val args = part.args.map { if (it is Part) text(context, it) else it }
        return if (args.isEmpty()) context.getString(part.res) else context.getString(part.res, *args.toTypedArray())
    }

    fun join(context: Context, parts: List<Part>): String = parts.joinToString(" · ") { text(context, it) }

    /** 파일의 형식 — 확장자로([lookup] 은 `MimeTypeMap`), 모르면 `application/octet-stream`. */
    fun mimeOf(fileName: String, lookup: (String) -> String?): String {
        val dot = fileName.lastIndexOf('.')
        val ext = if (dot > 0 && dot < fileName.length - 1) fileName.substring(dot + 1).lowercase(Locale.ROOT) else ""
        return ext.takeIf { it.isNotEmpty() }?.let(lookup)?.takeIf { it.contains('/') } ?: "application/octet-stream"
    }
}
