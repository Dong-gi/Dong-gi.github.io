package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.charset.WindowedDecoder
import java.io.InputStream

/**
 * 미리보기(마크다운·HTML)가 원문을 읽는 한 벌. BOM 은 바이트로 건너뛰고([bomLength]), [maxBytes] 에서 끊는다.
 *
 * 판정은 텍스트 뷰어가 이미 했다 — 여기서는 그 인코딩으로 디코드만 한다(판정은 `feature`, 디코딩은 `format`). 창 단위로 풀어
 * 창 경계의 글자가 깨지지 않는다([WindowedDecoder]). 우리가 읽지 못하는 인코딩이면 빈 글이다.
 */
internal fun readPreviewSource(
    openAt: (Long) -> InputStream,
    encoding: TextEncoding,
    bomLength: Int,
    maxBytes: Long,
): String {
    val cs = encoding.charset ?: return ""
    val sb = StringBuilder()
    openAt(bomLength.toLong()).use { raw ->
        WindowedDecoder(cs.newDecoder()).decodeAll(LimitedStream(raw, maxBytes)) { buf, off, len ->
            sb.append(buf, off, len)
        }
    }
    return sb.toString()
}
