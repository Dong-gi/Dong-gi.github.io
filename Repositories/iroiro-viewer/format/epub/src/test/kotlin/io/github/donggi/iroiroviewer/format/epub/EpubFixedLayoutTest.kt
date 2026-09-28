package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenOutcome
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 고정 레이아웃 책(EPUB 3.3 의 `rendition:layout`)을 **알아보고**, 장마다 맞는 모양을 얹는가.
 *
 * 11단계는 이런 책을 흐름으로 그렸다 — 좌표로 얹은 글과 그림이 화면 폭으로 다시 흘러 엇갈렸다. 여기서 박는 것은 셋이다:
 * 책 전체·차례 항목의 설정을 읽는 차례, 쪽의 화폭을 **위생 전의 글**에서 읽는 것(위생기가 `<meta>` 를 지운다), 흐름 장과
 * 고정 장이 한 책에 섞일 때 각자 제 모양을 받는 것.
 */
class EpubFixedLayoutTest {

    private val temp = ArrayList<File>()

    @AfterTest
    fun cleanUp() {
        temp.forEach { it.delete() }
    }

    private suspend fun open(bytes: ByteArray): EpubBook {
        val f = File.createTempFile("iroiro-fxl", ".epub").also { temp.add(it) }
        f.writeBytes(bytes)
        val outcome = EpubOpener().open(FileDocumentSource(f)) { }
        assertIs<OpenOutcome.Success>(outcome, "열지 못했다: $outcome")
        return outcome.document as EpubBook
    }

    private val viewportHead = """<meta name="viewport" content="width=1200, height=1600"/>"""

    private val pages = listOf(
        TinyEpub.Chapter("p1.xhtml", "1", "<div style=\"position:absolute;left:100px;top:200px\">글</div>", viewportHead),
        TinyEpub.Chapter("p2.xhtml", "2", "<p>둘</p>", viewportHead),
    )

    private val look = ReaderStyle.Look(
        margin = ReaderStyle.Margin.WIDE,
        tone = ReaderStyle.Tone.DARK,
        viewWidth = 411.0,
        viewHeight = 683.0,
    )

    @Test
    fun 책_전체가_고정_레이아웃이면_모든_쪽이_고정이다() = runTest {
        val book = open(TinyEpub.bytes(pages, packageMeta = """<meta property="rendition:layout">pre-paginated</meta>"""))
        book.use {
            assertTrue(it.spine.all { c -> c.fixedLayout })
            assertTrue(it.hasFixedLayout)
            assertTrue(it.allFixedLayout)
        }
    }

    @Test
    fun 고정_쪽은_위생_전의_뷰포트로_화폭을_맞추고_바탕을_바꾸지_않는다() = runTest {
        val book = open(TinyEpub.bytes(pages, packageMeta = """<meta property="rendition:layout">pre-paginated</meta>"""))
        book.use {
            val html = it.chapterHtml(0, look)!!
            // 위생기는 `<meta>` 를 지운다 — 그래도 화폭은 읽혔다.
            assertFalse("name=\"viewport\"" in html, html)
            assertTrue("width:900pt" in html && "height:1200pt" in html, html)
            assertTrue("zoom:34.25%" in html, html)
            // 고정 쪽에는 여백·어두운 바탕을 얹지 않는다(좌표와 원본 색이 뜻이다).
            assertFalse("#121212" in html, html)
            assertFalse("padding-left:30pt" in html, html)
        }
    }

    @Test
    fun 차례_항목의_설정이_책의_설정을_이긴다() = runTest {
        val chapters = pages + TinyEpub.Chapter("p3.xhtml", "3", "<p>셋</p>")
        // 책은 흐름, 둘째 장만 고정.
        val flowBook = open(TinyEpub.bytes(chapters, itemrefProperties = mapOf(1 to "page-spread-left rendition:layout-pre-paginated")))
        flowBook.use {
            assertEquals(listOf(false, true, false), it.spine.map { c -> c.fixedLayout })
            assertTrue(it.hasFixedLayout)
            assertFalse(it.allFixedLayout)
            // 흐름 장은 고른 여백·바탕을 받는다.
            val flow = it.chapterHtml(2, look)!!
            assertTrue("padding-left:30pt !important" in flow, flow)
            assertTrue("#121212" in flow, flow)
        }
        // 책은 고정, 첫 장만 흐름.
        val fixedBook = open(
            TinyEpub.bytes(
                chapters,
                packageMeta = """<meta property="rendition:layout">pre-paginated</meta>""",
                itemrefProperties = mapOf(0 to "rendition:layout-reflowable"),
            ),
        )
        fixedBook.use { assertEquals(listOf(false, true, true), it.spine.map { c -> c.fixedLayout }) }
    }

    @Test
    fun 다른_것을_꾸미는_meta_는_책의_설정이_아니다() = runTest {
        val book = open(
            TinyEpub.bytes(
                pages,
                packageMeta = """<meta refines="#x" property="rendition:layout">pre-paginated</meta>""",
            ),
        )
        book.use { assertFalse(it.hasFixedLayout) }
    }

    @Test
    fun EPUB2_의_fixed_layout_표시도_알아본다() = runTest {
        val book = open(TinyEpub.bytes(pages, packageMeta = """<meta name="fixed-layout" content="true"/>"""))
        book.use { assertTrue(it.allFixedLayout) }
        // EPUB3 의 설정이 있으면 그쪽이 이긴다.
        val both = open(
            TinyEpub.bytes(
                pages,
                packageMeta = """<meta name="fixed-layout" content="true"/><meta property="rendition:layout">reflowable</meta>""",
            ),
        )
        both.use { assertFalse(it.hasFixedLayout) }
    }

    @Test
    fun 쪽이_크기를_적지_않으면_책의_rendition_viewport_를_쓴다() = runTest {
        val bare = listOf(TinyEpub.Chapter("p1.xhtml", "1", "<p>하나</p>"))
        val book = open(
            TinyEpub.bytes(
                bare,
                packageMeta = """<meta property="rendition:layout">pre-paginated</meta>""" +
                    """<meta property="rendition:viewport">width=768, height=1024</meta>""",
            ),
        )
        book.use {
            val html = it.chapterHtml(0, look)!!
            assertTrue("width:576pt" in html && "height:768pt" in html, html)
        }
    }

    @Test
    fun 오른쪽에서_왼쪽으로_넘기는_책을_알아본다() = runTest {
        val rtl = open(TinyEpub.bytes(pages, spineAttributes = """page-progression-direction="rtl""""))
        rtl.use { assertTrue(it.rightToLeft) }
        val ltr = open(TinyEpub.bytes(pages, spineAttributes = """page-progression-direction="ltr""""))
        ltr.use { assertFalse(it.rightToLeft) }
    }

    @Test
    fun 차례의_그림_쪽은_화폭_없이_화면에_맞춘다() = runTest {
        // 만화형 고정 레이아웃 — 그림을 곧바로 차례에 둔다. 책의 화폭이 그림 크기라는 보장이 없다.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val book = open(
            TinyEpub.bytes(
                emptyList(),
                extras = mapOf("page1.png" to png),
                spineExtras = listOf("page1.png"),
                packageMeta = """<meta property="rendition:layout">pre-paginated</meta>""" +
                    """<meta property="rendition:viewport">width=768, height=1024</meta>""",
            ),
        )
        book.use {
            val html = it.chapterHtml(0, look)!!
            assertTrue("max-height:100vh" in html, html)
            assertFalse("zoom" in html, html)
        }
    }
}
