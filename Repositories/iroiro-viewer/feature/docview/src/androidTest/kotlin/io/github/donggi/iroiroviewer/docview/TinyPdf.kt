package io.github.donggi.iroiroviewer.docview

import java.io.ByteArrayOutputStream

/**
 * 시험용 PDF 를 **손으로 쓴다.**
 *
 * 10단계가 자막 시험을 하려고 `mkvmux.py` 로 EBML 을 손으로 쓴 것과 같은 판단이다 —
 * 저장소에 바이너리 표본을 커밋하지 않으면서(상위 저장소가 GitHub Pages 다) 확인하고
 * 싶은 성질만 정확히 갖춘 파일을 만든다. PDF 는 그러기에 특히 쉽다: 본문이 텍스트이고
 * 필요한 연산자가 `rg`(색)와 `re f`(칠한 사각형) 둘뿐이다.
 *
 * **쪽을 단색으로 만드는 것이 요점이다.** 9단계가 만화 쪽을 단색으로 만들어 화면 캡처의
 * 색으로 '지금 몇 쪽인가' 를 기계가 답하게 했다. 여기서는 렌더 결과 비트맵의 화소를
 * 직접 읽어 '이 쪽이 맞는가', '타일이 그 자리인가' 를 답하게 한다.
 */
internal object TinyPdf {

    /** 한 쪽의 내용. [ops] 는 PDF 내용 스트림 연산자다. */
    data class Page(val widthPt: Int, val heightPt: Int, val ops: String)

    /** 쪽 전체를 이 색으로 칠한다. 성분은 0~1 이다. */
    fun solid(r: Double, g: Double, b: Double, widthPt: Int, heightPt: Int) =
        Page(widthPt, heightPt, "$r $g $b rg 0 0 $widthPt $heightPt re f")

    /** 왼쪽 절반과 오른쪽 절반을 다른 색으로. 타일이 어느 자리를 떴는지 읽는 표본이다. */
    fun halves(widthPt: Int, heightPt: Int): Page {
        val half = widthPt / 2
        return Page(
            widthPt,
            heightPt,
            "1 0 0 rg 0 0 $half $heightPt re f " +
                "0 0 1 rg $half 0 ${widthPt - half} $heightPt re f",
        )
    }

    /**
     * 정상 PDF 하나.
     *
     * xref 의 오프셋을 **실제로 센다** — pdfium 은 어긋난 xref 를 복구해 주기도 하지만,
     * 그 복구 경로를 타는 표본으로 시험하면 우리가 무엇을 시험하는지 알 수 없게 된다.
     */
    fun bytes(pages: List<Page>): ByteArray = build(pages, encryptDict = null)

    /**
     * 암호가 걸린 PDF.
     *
     * `/Encrypt` 를 달고 `O`·`U` 에 **아무 바이트나** 넣는다. 표준 보안 처리기는 빈
     * 사용자 암호로 키를 만들어 `U` 와 견주는데, 그 비교가 어긋나면 그 자리에서
     * `SecurityException` 이다 — 내용 스트림까지 가지 않으므로 실제로 RC4 로 암호화할
     * 필요가 없다. **'암호가 걸려 있다' 는 사실만 갖춘 표본**이다 — 어떤 암호를 넣어도
     * 풀리지 않는다(맞는 암호가 없다). 실제로 잠근 표본은 `src/test/resources/pdfcrypt/`
     * 에 있다(pypdf·qpdf 가 잠갔다).
     */
    fun encryptedBytes(pages: List<Page>): ByteArray {
        val junk = "A".repeat(32).toByteArray(Charsets.US_ASCII).toHex()
        return build(pages, "<</Filter/Standard/V 1/R 2/O <$junk>/U <$junk>/P -1>>")
    }

    /** 빨간 쪽 하나에 [encryptDict] 를 `/Encrypt` 로 단 PDF. 보안 처리기의 갈래를 시험한다. */
    fun withEncrypt(encryptDict: String): ByteArray =
        build(listOf(solid(1.0, 0.0, 0.0, 200, 100)), encryptDict)

    private fun build(pages: List<Page>, encryptDict: String?): ByteArray {
        val encrypted = encryptDict != null
        require(pages.isNotEmpty())
        val out = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()

        fun put(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun obj(number: Int, body: String) {
            while (offsets.size < number) offsets.add(0)
            offsets[number - 1] = out.size()
            put("$number 0 obj\n$body\nendobj\n")
        }

        put("%PDF-1.4\n")

        // 1 카탈로그, 2 쪽 나무, 그 뒤로 쪽마다 둘(쪽, 내용).
        val kids = pages.indices.joinToString(" ") { "${3 + it * 2} 0 R" }
        obj(1, "<</Type/Catalog/Pages 2 0 R>>")
        obj(2, "<</Type/Pages/Kids[$kids]/Count ${pages.size}>>")
        pages.forEachIndexed { i, page ->
            val pageNo = 3 + i * 2
            val contentNo = pageNo + 1
            obj(
                pageNo,
                "<</Type/Page/Parent 2 0 R" +
                    "/MediaBox[0 0 ${page.widthPt} ${page.heightPt}]" +
                    "/Contents $contentNo 0 R>>",
            )
            obj(contentNo, "<</Length ${page.ops.length}>>\nstream\n${page.ops}\nendstream")
        }
        val encryptNo = 3 + pages.size * 2
        if (encryptDict != null) obj(encryptNo, encryptDict)

        val xref = out.size()
        put("xref\n0 ${offsets.size + 1}\n")
        // 자유 항목 하나. 항목은 **정확히 20바이트**여야 한다.
        put("0000000000 65535 f \n")
        for (offset in offsets) put(String.format("%010d 00000 n \n", offset))

        val trailer = buildString {
            append("<</Size ${offsets.size + 1}/Root 1 0 R")
            if (encrypted) {
                // 표준 보안 처리기는 키를 만들 때 ID 의 첫 조각을 쓴다. 없으면
                // pdfium 이 파일을 망가진 것으로 볼 수 있어, 있는 쪽으로 맞춘다.
                val id = "0123456789ABCDEF0123456789ABCDEF"
                append("/Encrypt $encryptNo 0 R/ID[<$id><$id>]")
            }
            append(">>")
        }
        put("trailer\n$trailer\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
