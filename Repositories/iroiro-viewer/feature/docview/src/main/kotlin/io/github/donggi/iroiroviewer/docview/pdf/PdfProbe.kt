package io.github.donggi.iroiroviewer.docview.pdf

import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.FormatProbe
import io.github.donggi.iroiroviewer.format.ProbeContext

/**
 * 이것이 PDF 인가.
 *
 * **판별기가 `feature:docview` 에 있는 것은 PDF 에 포맷 모듈이 없기 때문이다** —
 * `android.graphics.pdf` 를 쓰므로 순수 JVM 인 `format/` 에 둘 수 없다. 다른 포맷의
 * 판별기는 자기 포맷 모듈에 산다(`EpubProbe`).
 *
 * ## 매직을 앞 64바이트에서만 본다
 *
 * 명세는 `%PDF-` 앞에 쓰레기가 붙은 파일도 허용하고(뷰어가 1024바이트까지 찾도록 권한다)
 * pdfium 도 그런 파일을 연다. 그런데 우리가 여기서 그만큼 읽어 봐야 **확장자가 `pdf` 면
 * 어차피 맡는다** — 판별이 하는 일은 '누가 열지' 를 고르는 것이고, 진짜 판정은 여는
 * 쪽이 한다. 그래서 싼 검사 둘로 끝낸다.
 */
object PdfProbe : FormatProbe {

    override fun probe(context: ProbeContext): FormatId? = when {
        // `%PDF-`
        context.headStartsWith(0x25, 0x50, 0x44, 0x46, 0x2D) -> FormatId.PDF
        context.extension == "pdf" -> FormatId.PDF
        else -> null
    }
}
