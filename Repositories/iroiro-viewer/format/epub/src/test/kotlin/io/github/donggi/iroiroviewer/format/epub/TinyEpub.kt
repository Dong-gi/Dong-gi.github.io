package io.github.donggi.iroiroviewer.format.epub

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 시험용 EPUB 을 **손으로 쓴다.**
 *
 * 10단계의 `mkvmux.py`, 11단계의 `TinyPdf` 와 같은 판단이다 — 저장소에 바이너리 표본을
 * 커밋하지 않으면서(상위 저장소가 GitHub Pages 다) 확인하고 싶은 성질만 정확히 갖춘
 * 파일을 만든다. EPUB 은 ZIP + XML 이라 JDK 만으로 만들 수 있다.
 *
 * **`java.util.zip` 을 쓰는 것은 여기가 시험이기 때문이다.** 제품 코드는 8단계의
 * `ZipArchiveReader` 로만 ZIP 을 읽는다.
 */
internal object TinyEpub {

    /** 한 장. [body] 는 `<body>` 안에 그대로 들어간다. */
    data class Chapter(val path: String, val title: String, val body: String)

    fun bytes(
        chapters: List<Chapter>,
        title: String = "시험책",
        language: String = "ko",
        /** OPF 가 놓이는 폴더. 상대 주소 해결을 시험하려고 바꿀 수 있게 둔다. */
        opfDir: String = "OEBPS",
        extras: Map<String, ByteArray> = emptyMap(),
        /** 명세를 어기고 `mimetype` 을 압축해 넣는다. 실물에 흔하다. */
        deflateMimetype: Boolean = false,
        includeMimetype: Boolean = true,
        includeContainer: Boolean = true,
        /** EPUB3 의 `nav.xhtml` 을 넣는다. false 면 EPUB2 의 `toc.ncx` 를 넣는다. */
        nav: Boolean = true,
        /** 고유 식별자(`unique-identifier` 가 가리키는 `dc:identifier`). null 이면 넣지 않는다. */
        identifier: String? = null,
        /** 그 밖의 `dc:identifier` 들. Adobe 난독화가 UUID 를 여기서 찾는다. */
        otherIdentifiers: List<String> = emptyList(),
        /** ZIP 뿌리에 그대로 넣는 엔트리(`META-INF/encryption.xml` 등). */
        rootEntries: Map<String, ByteArray> = emptyMap(),
        /**
         * [extras] 가운데 **차례에도** 올릴 것. 장 뒤에 **[extras] 에 넣은 순서대로** 붙는다(이 목록의 순서가
         * 아니다). 만화형 책은 그림을 곧바로 차례에 둔다.
         */
        spineExtras: List<String> = emptyList(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (includeMimetype) {
                if (deflateMimetype) {
                    put(zip, "mimetype", "application/epub+zip".toByteArray())
                } else {
                    stored(zip, "mimetype", "application/epub+zip".toByteArray())
                }
            }
            if (includeContainer) {
                put(
                    zip, "META-INF/container.xml",
                    """<?xml version="1.0"?>
                    |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    |  <rootfiles><rootfile full-path="$opfDir/content.opf"
                    |    media-type="application/oebps-package+xml"/></rootfiles>
                    |</container>
                    """.trimMargin().toByteArray(),
                )
            }

            for ((path, data) in rootEntries) put(zip, path, data)

            val manifest = StringBuilder()
            val spine = StringBuilder()
            chapters.forEachIndexed { i, c ->
                manifest.append(
                    """<item id="c$i" href="${c.path}" media-type="application/xhtml+xml"/>"""
                )
                spine.append("""<itemref idref="c$i"/>""")
                put(zip, "$opfDir/${c.path}", page(c.title, c.body).toByteArray())
            }
            for ((path, data) in extras) {
                val id = path.filter { it.isLetterOrDigit() }
                manifest.append("""<item id="x$id" href="$path" media-type="${mimeOf(path)}"/>""")
                if (path in spineExtras) spine.append("""<itemref idref="x$id"/>""")
                put(zip, "$opfDir/$path", data)
            }

            if (nav) {
                manifest.append(
                    """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
                )
                put(zip, "$opfDir/nav.xhtml", navXhtml(chapters).toByteArray())
            } else {
                manifest.append(
                    """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
                )
                put(zip, "$opfDir/toc.ncx", ncx(chapters).toByteArray())
            }

            put(
                zip, "$opfDir/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
                |<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                |  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                |    <dc:title>$title</dc:title>
                |    <dc:language>$language</dc:language>
                |    ${identifier?.let { "<dc:identifier id='id'>$it</dc:identifier>" } ?: ""}
                |    ${otherIdentifiers.joinToString("") { "<dc:identifier>$it</dc:identifier>" }}
                |  </metadata>
                |  <manifest>$manifest</manifest>
                |  <spine${if (nav) "" else """ toc="ncx""""}>$spine</spine>
                |</package>
                """.trimMargin().toByteArray(),
            )
        }
        return out.toByteArray()
    }

    fun page(title: String, body: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE html>
        |<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head>
        |<body>$body</body></html>
        """.trimMargin()

    private fun navXhtml(chapters: List<Chapter>): String {
        val items = chapters.joinToString("") { """<li><a href="${it.path}">${it.title}</a></li>""" }
        // `landmarks` 를 함께 넣는다. 실물 nav.xhtml 이 그 모양이고, 우리 목차 파서가
        // `epub:type="toc"` 인 것만 읽는지 확인하는 것이 이 줄의 일이다.
        val landmarks = chapters.firstOrNull()?.let {
            """<nav epub:type="landmarks"><ol><li><a href="${it.path}">표지</a></li></ol></nav>"""
        }.orEmpty()
        return """<?xml version="1.0" encoding="UTF-8"?>
        |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        |<body>$landmarks
        |<nav epub:type="toc"><ol>$items</ol></nav></body></html>
        """.trimMargin()
    }

    private fun ncx(chapters: List<Chapter>): String {
        val points = chapters.mapIndexed { i, c ->
            """<navPoint id="n$i" playOrder="${i + 1}">
            |<navLabel><text>${c.title}</text></navLabel><content src="${c.path}"/></navPoint>
            """.trimMargin()
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8"?>
        |<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
        |<navMap>$points</navMap></ncx>
        """.trimMargin()
    }

    private fun mimeOf(path: String): String = when {
        path.endsWith(".css") -> "text/css"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") -> "image/jpeg"
        path.endsWith(".mp3") -> "audio/mpeg"
        path.endsWith(".xhtml") -> "application/xhtml+xml"
        else -> "application/octet-stream"
    }

    private fun put(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    /** 명세가 요구하는 대로 **압축하지 않고** 넣는다. STORED 는 크기와 CRC 를 미리 준다. */
    private fun stored(zip: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.method = ZipEntry.STORED
        entry.size = data.size.toLong()
        entry.compressedSize = data.size.toLong()
        entry.crc = CRC32().apply { update(data) }.value
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }
}
