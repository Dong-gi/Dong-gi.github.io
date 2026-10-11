package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.format.text.PreviewAsset
import io.github.donggi.iroiroviewer.format.text.PreviewTypes
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * 미리보기(마크다운·HTML)가 싣는 파일을 **디스크에서 고르는** 한 곳.
 *
 * 변환기(`MarkdownPreview.localImagePath`·`HtmlPreview`)가 이미 `..` 탈출·스킴·절대 경로를 걸렀지만 여기서 한 번 더 본다 —
 * 그 검사는 글자만 보고, 여기는 **실제 파일**을 본다. 폴더 안에 둔 심볼릭 링크가 밖을 가리킬 수 있어서 `canonicalFile` 로 풀어
 * 본 뒤에 판단한다. 이 앱은 모든 파일 접근 권한을 가지고 돈다 — 사용자가 연 파일 하나가 그 권한으로 아무 파일이나 화면에 띄우게
 * 두지 않는다.
 *
 * 싣는 것: 미리보는 파일과 **같은 폴더와 그 아래**의, 쓰임([PreviewAsset.Use])에 맞는 형식(`PreviewTypes` 의 표 — 내용을 보고
 * 짐작하지 않는다)의, [MAX_FILE_BYTES] 이하의 파일. 형식은 **풀어 본 실제 파일의 이름**으로 정한다 — `a.png` 라는 링크가
 * `notes.txt` 를 가리키면 그림이 아니다. `../assets/x.png` 처럼 위로 올라가는 그림은 싣지 않는다 — 그 대가를 알고 골랐다.
 */
internal object PreviewFiles {

    /** 그림·글꼴 하나의 상한. 디코딩은 WebView 가 하고, 그 메모리는 렌더러 프로세스가 진다. */
    const val MAX_FILE_BYTES = 32L * 1024 * 1024

    /**
     * 고른 파일과 내줄 형식. [length] 는 고를 때 잰 크기다 — 화면은 **그만큼만** 읽는다(`PreviewServer.open`). 재고 나서 읽기까지
     * 사이에 파일이 자라도(쓰는 중인 그림·로그) 상한을 넘지 않게.
     */
    class Found(val file: File, val mimeType: String, val length: Long)

    /** [relative] 가 [baseDir] 안의, [use] 로 실을 수 있는 파일이면 그것, 아니면 null. */
    fun resolve(baseDir: File, relative: String, use: PreviewAsset.Use): Found? =
        resolve(baseDir, relative, use, CANONICAL)

    /**
     * [canonical] 은 경로를 실제 파일로 푸는 함수다(보통 `canonicalFile` — 심볼릭 링크를 따라간다). 시험이 가짜를 꽂는다 — 윈도는
     * 권한 없이 링크를 만들 수 없어 진짜 링크로 하는 시험이 그 자리에서 건너뛴다.
     */
    internal fun resolve(baseDir: File, relative: String, use: PreviewAsset.Use, canonical: (File) -> File): Found? =
        resolve(baseDir, relative, MAX_FILE_BYTES, canonical) { PreviewTypes.ofFile(it, use) }

    /**
     * 스크립트를 실행하는 쪽([io.github.donggi.iroiroviewer.format.text.LivePage])이 청한 파일. 검사는 위와 같고(폴더와 그 아래, 실제
     * 파일로 푼 경로, 크기), 형식만 그 쪽의 표다(`PreviewTypes.ofLive` — 스크립트·스타일시트·자료·HTML 을 더했다).
     */
    fun resolveLive(baseDir: File, relative: String): Found? = resolveLive(baseDir, relative, CANONICAL)

    internal fun resolveLive(baseDir: File, relative: String, canonical: (File) -> File): Found? =
        resolve(baseDir, relative, MAX_FILE_BYTES, canonical) { PreviewTypes.ofLive(it) }

    /**
     * 스타일시트 파일의 바이트. `.css` 만, [maxBytes] 이하만(넘으면 null — 반쯤 읽은 CSS 는 규칙을 깨뜨린다).
     * **`File.readBytes()` 를 쓰지 않는다**(저장소 규칙) — 크기를 먼저 보고 그만큼만 읽는다.
     */
    fun readStylesheet(baseDir: File, relative: String, maxBytes: Long): ByteArray? =
        readStylesheet(baseDir, relative, maxBytes, CANONICAL)

    internal fun readStylesheet(baseDir: File, relative: String, maxBytes: Long, canonical: (File) -> File): ByteArray? {
        val found = resolve(baseDir, relative, maxBytes, canonical) { if (PreviewTypes.isStylesheetName(it)) CSS else null }
            ?: return null
        return try {
            readAll(found.file, found.length)
        } catch (e: IOException) {
            null
        }
    }

    private fun resolve(
        baseDir: File,
        relative: String,
        maxBytes: Long,
        canonical: (File) -> File,
        typeOf: (String) -> String?,
    ): Found? {
        if (relative.isEmpty() || relative.startsWith("/")) return null
        return try {
            val base = canonical(baseDir)
            val file = canonical(File(base, relative))
            val prefix = if (base.path.endsWith(File.separator)) base.path else base.path + File.separator
            val type = typeOf(file.name)
            val length = if (file.isFile) file.length() else 0L
            when {
                !file.path.startsWith(prefix) -> null
                type == null -> null
                !file.isFile -> null
                length !in 1..maxBytes -> null
                else -> Found(file, type, length)
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** 고를 때 잰 크기([length])만큼만 읽는다. 그 사이에 파일이 자라도 그것을 넘지 않는다. */
    private fun readAll(file: File, length: Long): ByteArray? {
        if (length !in 1..Int.MAX_VALUE.toLong()) return null
        val buffer = ByteArray(length.toInt())
        var read = 0
        FileInputStream(file).use { input ->
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n <= 0) break
                read += n
            }
        }
        return if (read == buffer.size) buffer else buffer.copyOf(read)
    }

    private const val CSS = "text/css"

    private val CANONICAL: (File) -> File = { it.canonicalFile }
}
