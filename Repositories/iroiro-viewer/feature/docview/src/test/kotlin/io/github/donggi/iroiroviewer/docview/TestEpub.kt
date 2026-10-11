package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.epub.EpubBook
import io.github.donggi.iroiroviewer.format.epub.EpubOpener
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 시험이 쓰는 **열린 문서** 하나 — 장 하나짜리 EPUB 3 을 임시 파일로 짓고 이 앱의 여는이로 연다. 상태의 모양(`State.Ready`)만 보는
 * 시험이 쓴다. EPUB 의
 * 책은 여는이 밖에서 만들 수 없어(생성자가 그 모듈 안의 것이다) 진짜로 연다.
 */
internal object TestEpub {

    /** [title] 이 제목인 책을 [dir] 에 짓고 연다. 다 쓰면 [EpubBook.close] 한다. */
    fun open(title: String, dir: File): EpubBook {
        val file = File(dir, "test-${System.nanoTime()}.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                },
            )
            zip.write(mimetype)
            zip.closeEntry()
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                |  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                |</container>""".trimMargin(),
            )
            put(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
                |<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                |  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                |    <dc:identifier id="id">urn:uuid:7d1c1f30-6b40-4c1e-9d5b-000000000001</dc:identifier>
                |    <dc:title>$title</dc:title>
                |    <dc:language>ko</dc:language>
                |    <meta property="dcterms:modified">2026-10-09T00:00:00Z</meta>
                |  </metadata>
                |  <manifest>
                |    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                |    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                |  </manifest>
                |  <spine><itemref idref="c1"/></spine>
                |</package>""".trimMargin(),
            )
            put(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?>
                |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>차례</title></head>
                |<body><nav epub:type="toc"><ol><li><a href="c1.xhtml">1장</a></li></ol></nav></body></html>""".trimMargin(),
            )
            put(
                "OEBPS/c1.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?>
                |<html xmlns="http://www.w3.org/1999/xhtml"><head><title>1장</title></head><body><p>글</p></body></html>""".trimMargin(),
            )
        }
        val outcome = runBlocking { EpubOpener().open(FileDocumentSource(file)) }
        check(outcome is OpenOutcome.Success) { "시험 책을 열지 못했다: $outcome" }
        return outcome.document as EpubBook
    }
}
