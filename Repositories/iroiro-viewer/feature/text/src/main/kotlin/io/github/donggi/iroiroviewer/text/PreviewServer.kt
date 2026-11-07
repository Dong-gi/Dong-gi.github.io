package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.format.text.MarkdownPreview
import io.github.donggi.iroiroviewer.safety.LimitedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * 마크다운 미리보기의 WebView 가 청한 경로 하나에 **무엇을 내줄지** 정한다. WebView 와 떼어 두어 JVM 에서 시험한다(`PreviewServerTest`).
 *
 * 내주는 것은 둘뿐이다 — 쪽 자신, 그리고 쪽이 번호로 가리킨 그림(`img/N`). 번호가 가리키는 것은 변환기가 만든 표에 있고, 디스크의
 * 파일은 [PreviewFiles] 가 폴더 밖·형식·크기를 다시 보고 고른다. 그 밖의 경로는 null — 화면이 빈 404 를 준다.
 *
 * **경로를 다시 풀지 않는다.** 받는 경로는 `WebHost.pathOf` 가 준 것이고 그것은 `Uri.getPath()` — 이미 퍼센트를 푼 값이다. 우리가
 * 쓰는 경로는 번호뿐이라 풀 것도 없지만, 여기서 한 번 더 풀면 `%25` 가 든 이름이 두 번 풀린다(이름을 쪽에 적지 않는 까닭 가운데
 * 하나다).
 */
internal object PreviewServer {

    /** 내줄 것. [encoding] 은 글이면 `utf-8`, 아니면 null. */
    sealed interface Served {
        val mimeType: String
        val encoding: String?

        /** 메모리의 바이트(쪽). */
        class Bytes(override val mimeType: String, override val encoding: String?, val data: ByteArray) : Served

        /** 디스크의 파일. 형식은 우리 표에서 나왔고, [length] 는 고를 때 잰 크기다([PreviewFiles.Found.length]). */
        class FromFile(override val mimeType: String, val file: File, val length: Long) : Served {
            override val encoding: String? get() = null
        }
    }

    fun serve(p: TextViewModel.Preview.Ready, path: String): Served? {
        if (path == p.pagePath) return Served.Bytes(HTML, UTF_8, p.html)
        val index = MarkdownPreview.imageIndexOf(path) ?: return null
        val image = p.images.getOrNull(index) ?: return null
        return PreviewFiles.resolve(p.baseDir, image)?.let { Served.FromFile(it.mimeType, it.file, it.length) }
    }

    /**
     * 내줄 것의 스트림. 디스크의 파일은 **고를 때 잰 크기까지만** 읽는다 — 재고 나서 WebView 가 읽기까지 사이에 파일이 자라면(쓰는
     * 중인 그림) 크기 검사가 뜻을 잃는다. 넘으면 `ParseLimitExceededException`(입출력 예외다) — WebView 는 그 자원 하나를 실패로 본다.
     */
    fun open(s: Served): InputStream = when (s) {
        is Served.Bytes -> ByteArrayInputStream(s.data)
        is Served.FromFile -> LimitedInputStream(FileInputStream(s.file), s.length, LIMIT_NAME)
    }

    private const val LIMIT_NAME = "previewFile"
    private const val HTML = "text/html"
    private const val UTF_8 = "utf-8"
}
