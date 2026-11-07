package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.media.HlsKeys
import io.github.donggi.iroirobrowser.core.media.PageRequests
import io.github.donggi.iroirobrowser.download.TextParts.Part

/**
 * 열쇠를 받지 못한 HLS 의 실패 글 — 갈래([HlsKeys.Report])를 짧은 조각으로. 고르는 일은 자원 번호와 인자로만
 * 해서 기기 없이 시험한다([TextParts] 의 꼴). 주소·쿠키·토큰·몸은 싣지 않는다 — 이 글은 목록 파일(downloads.json)에 남는다.
 */
internal object HlsKeyTexts {

    /** 응답의 꼴이 먼저, 그다음 넘겨주기·몇 번째 열쇠·사이트·쪽의 요청을 실었는가. */
    fun parts(r: HlsKeys.Report, sizes: TextParts.Sizes): List<Part> = buildList {
        add(
            when (r.kind) {
                HlsKeys.BodyKind.EMPTY -> Part(R.string.download_key_empty)
                HlsKeys.BodyKind.HTML -> Part(R.string.download_key_html, sizes.format(r.bytes))
                HlsKeys.BodyKind.JSON -> Part(R.string.download_key_json, sizes.format(r.bytes))
                HlsKeys.BodyKind.TEXT -> Part(R.string.download_key_text, sizes.format(r.bytes))
                HlsKeys.BodyKind.HEX -> Part(R.string.download_key_hex)
                HlsKeys.BodyKind.BASE64 -> Part(R.string.download_key_base64)
                HlsKeys.BodyKind.BINARY ->
                    if (r.bytes > HlsKeys.KEY_BYTES) Part(R.string.download_key_long, r.bytes.toInt()) else Part(R.string.download_key_short, r.bytes.toInt())
                HlsKeys.BodyKind.TOO_LARGE -> Part(R.string.download_key_too_large)
            },
        )
        if (r.redirects > 0) add(Part(R.string.download_key_redirects, r.redirects))
        if (r.count > 1) add(Part(R.string.download_key_ordinal, r.ordinal, r.count))
        if (r.crossSite) add(Part(R.string.download_key_cross_site))
        add(
            when (r.replay) {
                null -> Part(R.string.download_key_no_page_request)
                PageRequests.Match.PLAYLIST -> Part(R.string.download_key_playlist_referer)
                else -> Part(R.string.download_key_page_headers)
            },
        )
    }
}
