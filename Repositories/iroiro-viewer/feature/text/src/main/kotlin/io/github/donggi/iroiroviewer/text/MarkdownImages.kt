package io.github.donggi.iroiroviewer.text

import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * 마크다운 미리보기가 싣는 그림을 **디스크에서 고르는** 한 곳.
 *
 * 변환기(`MarkdownPreview.localImagePath`)가 이미 `..` 탈출·스킴·절대 경로를 걸렀지만 여기서 한 번 더 본다 —
 * 그 검사는 글자만 보고, 여기는 **실제 파일**을 본다. 폴더 안에 둔 심볼릭 링크가 밖을 가리킬 수 있어서
 * `canonicalFile` 로 풀어 본 뒤에 판단한다. 이 앱은 모든 파일 접근 권한을 가지고 돈다 — 사용자가 연 마크다운
 * 파일 하나가 그 권한으로 아무 파일이나 화면에 띄우게 두지 않는다.
 *
 * 싣는 것: 그 파일과 **같은 폴더와 그 아래**의, 그림 확장자를 가진, [MAX_IMAGE_BYTES] 이하의 파일.
 * `../assets/x.png` 처럼 위로 올라가는 README 의 그림은 대체 글로 보인다 — 그 대가를 알고 골랐다.
 */
internal object MarkdownImages {

    /** 그림 하나의 상한. 디코딩은 WebView 가 하고, 그 메모리는 렌더러 프로세스가 진다. */
    const val MAX_IMAGE_BYTES = 32L * 1024 * 1024

    /**
     * 확장자 → 형식. **이 표에 있는 것만** 내준다 — 형식을 파일에서 짐작하면 스타일시트나 문서로 읽힐 수 있다
     * (함정 표의 `rawResourceType`). SVG 는 `<img>` 로만 실리므로 그 안의 스크립트·바깥 참조가 돌지 않는다.
     */
    private val TYPES = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "avif" to "image/avif",
    )

    fun mimeOf(name: String): String? = TYPES[name.substringAfterLast('.', "").lowercase(Locale.ROOT)]

    /** [relative] 가 [baseDir] 안의 실을 수 있는 그림이면 그 파일, 아니면 null. */
    fun resolve(baseDir: File, relative: String): File? {
        if (relative.isEmpty() || relative.startsWith("/")) return null
        return try {
            val base = baseDir.canonicalFile
            val file = File(base, relative).canonicalFile
            val prefix = if (base.path.endsWith(File.separator)) base.path else base.path + File.separator
            when {
                !file.path.startsWith(prefix) -> null
                mimeOf(file.name) == null -> null
                !file.isFile -> null
                file.length() !in 1..MAX_IMAGE_BYTES -> null
                else -> file
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}
