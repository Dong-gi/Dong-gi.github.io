package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.format.text.HtmlPreview
import io.github.donggi.iroiroviewer.format.text.LivePage
import io.github.donggi.iroiroviewer.format.text.MarkdownPreview
import io.github.donggi.iroiroviewer.format.text.PreviewAsset
import io.github.donggi.iroiroviewer.format.text.PreviewTypes
import io.github.donggi.iroiroviewer.safety.LimitedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * 미리보기의 WebView 가 청한 경로 하나에 **무엇을 내줄지** 정한다. WebView 와 떼어 두어 JVM 에서 시험한다(`PreviewServerTest`).
 *
 * 내주는 것은 셋뿐이다 — 쪽 자신, 그리고 쪽이 번호로 가리킨 것(마크다운 `img/N`, HTML `r/N`). 번호가 가리키는 것은 변환기가 만든
 * 표에 있고, 디스크의 파일은 [PreviewFiles] 가 폴더 밖·형식·크기를 다시 보고 고른다. 그 밖의 경로는 null — 화면이 빈 404 를 준다.
 * 스크립트를 실행하는 쪽은 번호 표가 없다 — 쪽의 폴더 안의 경로를 그대로 받아 같은 검사로 고른다([serveLive]).
 *
 * **경로를 다시 풀지 않는다.** 받는 경로는 `WebHost.pathOf` 가 준 것이고 그것은 `Uri.getPath()` — 이미 퍼센트를 푼 값이다. 우리가
 * 쓰는 경로는 번호뿐이라 풀 것도 없지만, 여기서 한 번 더 풀면 `%25` 가 든 이름이 두 번 풀린다(마크다운·HTML 모두 이름을 쪽에
 * 적지 않는 까닭 가운데 하나다).
 */
internal object PreviewServer {

    /** 내줄 것. [encoding] 은 글이면 `utf-8`, 아니면 null. */
    sealed interface Served {
        val mimeType: String
        val encoding: String?

        /** 메모리의 바이트(쪽·위생을 거친 스타일시트·`data:` 를 푼 것). */
        class Bytes(override val mimeType: String, override val encoding: String?, val data: ByteArray) : Served

        /**
         * 디스크의 파일. 형식은 우리 표에서 나왔고, [length] 는 고를 때 잰 크기다([PreviewFiles.Found.length]). [encoding] 은 스크립트를
         * 실행하는 쪽 자신에만 붙는다(`Preview.Live.encoding`) — 나머지는 브라우저가 정한다.
         */
        class FromFile(
            override val mimeType: String,
            val file: File,
            val length: Long,
            override val encoding: String? = null,
        ) : Served
    }

    fun serve(p: TextViewModel.Preview.Ready, path: String): Served? {
        p.live?.let { return serveLive(p, it, path) }
        if (path == p.pagePath) return Served.Bytes(HTML, UTF_8, p.html)
        val index = when (p.kind) {
            PreviewKind.MARKDOWN -> MarkdownPreview.imageIndexOf(path)
            PreviewKind.HTML -> HtmlPreview.assetIndexOf(path)
        } ?: return null
        return when (val asset = p.assets.getOrNull(index) ?: return null) {
            is PreviewAsset.Disk -> PreviewFiles.resolve(p.baseDir, asset.path, asset.use)?.let {
                Served.FromFile(it.mimeType, it.file, it.length)
            }
            is PreviewAsset.Sheet -> asset.css?.let { Served.Bytes(CSS, UTF_8, it) }
            is PreviewAsset.Data -> Served.Bytes(asset.mimeType, null, asset.bytes)
        }
    }

    /**
     * 스크립트를 실행하는 쪽([LivePage]). 쪽 자신은 **파일의 바이트 그대로**, 그 밖의 경로는 쪽의 폴더 기준 상대 경로로 보고 디스크에서
     * 고른다 — 폴더 밖·심볼릭 링크·크기·형식은 [PreviewFiles.resolveLive] 가 본다. 번호 표가 없다(앞 패스를 거치지 않았다).
     * 그 쪽의 스크립트는 폴더와 그 아래의 파일을 읽을 수 있지만 밖으로 보낼 길이 없다 — 이 앱에는 `INTERNET` 이 없다.
     */
    private fun serveLive(p: TextViewModel.Preview.Ready, live: TextViewModel.Preview.Live, path: String): Served? {
        // 쪽의 형식도 이름으로 — `.xhtml` 은 XML 로 읽혀야 `<div/>` 가 뒤의 쪽을 삼키지 않는다.
        if (path == p.pagePath) {
            return Served.FromFile(PreviewTypes.ofLive(live.file.name) ?: HTML, live.file, live.length, live.encoding)
        }
        val relative = LivePage.relativeOf(path) ?: return null
        return PreviewFiles.resolveLive(p.baseDir, relative)?.let { Served.FromFile(it.mimeType, it.file, it.length) }
    }

    /**
     * 내줄 것의 스트림. 디스크의 파일은 **고를 때 잰 크기까지만** 읽는다 — 재고 나서 WebView 가 읽기까지 사이에 파일이 자라면(쓰는
     * 중인 그림·로그) 크기 검사가 뜻을 잃는다. 넘으면 `ParseLimitExceededException`(입출력 예외다) — WebView 는 그
     * 자원 하나를 실패로 본다.
     */
    fun open(s: Served): InputStream = when (s) {
        is Served.Bytes -> ByteArrayInputStream(s.data)
        is Served.FromFile -> LimitedInputStream(FileInputStream(s.file), s.length, LIMIT_NAME)
    }

    private const val LIMIT_NAME = "previewFile"
    private const val HTML = "text/html"
    private const val CSS = "text/css"
    private const val UTF_8 = "utf-8"
}
