package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 독립 도구가 만든 표본. **python-docx 가 쓰고, python-docx 가 읽은 문단 목록**(`.golden.txt`)이 오라클이다 —
 * 우리 변환기가 같은 문단을 같은 차례로 내놓아야 한다. 표본과 오라클은 스크래치의 `make_sample.py` 가
 * 한 번에 만든다(저장소에 넣지 않는다).
 */
class DocxSampleTest {

    private fun resource(name: String): ByteArray =
        assertNotNull(javaClass.getResourceAsStream("/docx/$name"), name).use { it.readBytes() }

    private fun norm(s: String) = s.replace('\t', ' ').split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    @Test
    fun python_docx_표본의_문단이_차례대로_모두_나온다() {
        val bytes = resource("python-docx-sample.docx")
        assertTrue(bytes.size <= 20 * 1024, "표본은 20KB 이하 — ${bytes.size}")
        val golden = resource("python-docx-sample.golden.txt").toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
        Docx.openBytes(bytes).use { doc ->
            val html = doc.body()
            val ours = paragraphs(html).map { norm(it) }.filter { it.isNotEmpty() }
            assertEquals(golden, ours)

            assertEquals("표본 문서 제목", doc.title)
            assertEquals(
                listOf("첫째 장 — 시작" to 0, "1.1 목록" to 1, "1.2 표" to 1, "둘째 장 — 그림" to 0),
                doc.outline.map { it.title to it.depth },
            )
            // python-docx 의 목록 스타일은 스타일에 번호(`numPr`)를 둔다.
            assertEquals(listOf("•", "•", "1.", "2.", "3."), markers(html))
            assertTrue("colspan=\"2\"" in html && "rowspan=\"2\"" in html, html)
            assertTrue(Regex("<img src=\"word/media/image1\\.png\" alt=\"Picture 1\" style=\"width:72pt\"/>").containsMatchIn(html), html)
            assertTrue(doc.openResource("word/media/image1.png") != null)
            assertTrue("text-align:center" in html)
            assertTrue("&lt;script&gt;" in html && "<script" !in html)
            assertTrue(doc.unsupported.snapshot().keys.none { it == UnsupportedFeatures.UNSUPPORTED_IMAGE })
        }
    }

    /**
     * 실세계 문서(`samples-local/docx/`, 저장소에 넣지 않는다). 있으면 전부 열어 **무너지지 않는지** 본다 —
     * 모든 조각이 그려지고, 버린 것이 있으면 센다.
     */
    @Test
    fun 실세계_표본이_무너지지_않는다() {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "samples-local/docx") }
            .firstOrNull { it.isDirectory }
        assumeTrue("samples-local/docx 가 없다", dir != null)
        val files = dir!!.listFiles { f -> f.name.endsWith(".docx", ignoreCase = true) }.orEmpty()
        assumeTrue("표본이 없다", files.isNotEmpty())
        for (f in files) {
            Docx.openBytes(f.readBytes()).use { doc ->
                assertTrue(doc.parts.isNotEmpty(), f.name)
                for (i in doc.parts.indices) assertNotNull(doc.partHtml(i), "${f.name} #$i")
            }
        }
    }
}
