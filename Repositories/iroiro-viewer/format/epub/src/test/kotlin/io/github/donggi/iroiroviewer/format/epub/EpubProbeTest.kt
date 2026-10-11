package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ProbeContext
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * EPUB 판별([EpubProbe]). HWPX 는 EPUB 처럼 생겼다(`mimetype` + `META-INF/container.xml`) — 그것을 가리던 HWPX 판별기가 없으므로
 * 이 판별기가 스스로 가린다. 고침을 되돌리면(저장된 `mimetype` 의 내용·`content.hpf` 를 보지 않으면) 'HWPX' 둘이 깨진다.
 */
class EpubProbeTest {

    private fun zip(mimetype: String?, storedMimetype: Boolean, vararg names: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            if (mimetype != null) {
                // UTF-8 — 앞에 BOM 을 붙인 표본(아래)이 그 바이트(EF BB BF) 그대로 들어가게. ASCII 글자는 같은 바이트다.
                val bytes = mimetype.toByteArray(Charsets.UTF_8)
                val entry = ZipEntry("mimetype")
                if (storedMimetype) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                z.putNextEntry(entry)
                z.write(bytes)
                z.closeEntry()
            }
            for (n in names) {
                z.putNextEntry(ZipEntry(n))
                z.write("<x/>".toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** @param namesKnown 거짓이면 ZIP 의 이름 목록을 모른다(판별기가 목록을 읽지 못한 길 — 확장자로 본다). */
    private fun probe(bytes: ByteArray, extension: String, namesKnown: Boolean = true): FormatId? {
        val names = ZipInputStream(bytes.inputStream()).use { input ->
            generateSequence { input.nextEntry }.map { it.name }.toList()
        }
        val context = ProbeContext(
            source = ByteArrayDocumentSource(bytes, "a.$extension"),
            extension = extension,
            head = bytes.copyOf(64),
            zipEntryNames = lazy { if (namesKnown) names else null },
        )
        return EpubProbe.probe(context)
    }

    @Test
    fun 저장된_mimetype_이_EPUB_이면_맡는다() {
        assertEquals(FormatId.EPUB, probe(zip("application/epub+zip", true, "META-INF/container.xml"), "epub"))
        // 이름이 틀려도 내용이 말한다.
        assertEquals(FormatId.EPUB, probe(zip("application/epub+zip", true, "META-INF/container.xml"), "zip"))
    }

    @Test
    fun 압축한_mimetype_은_이름으로_본다() {
        assertEquals(FormatId.EPUB, probe(zip("application/epub+zip", false, "META-INF/container.xml"), "epub"))
    }

    @Test
    fun HWPX_는_container_가_있어도_맡지_않는다() {
        val hwpx = zip("application/hwp+zip", true, "META-INF/container.xml", "Contents/content.hpf", "Contents/section0.xml")
        assertNull(probe(hwpx, "hwpx"))
        // mimetype 을 압축해 넣은 HWPX — 이름(content.hpf)으로 가린다.
        val packed = zip("application/hwp+zip", false, "META-INF/container.xml", "Contents/content.hpf")
        assertNull(probe(packed, "hwpx"))
    }

    @Test
    fun 다른_OCF_형식도_맡지_않는다() {
        // ODF 는 mimetype 이 저장된 첫 항목이고 container.xml 대신 manifest.xml 을 쓴다.
        assertNull(probe(zip("application/vnd.oasis.opendocument.text", true, "META-INF/manifest.xml"), "odt"))
        assertNull(probe(zip(null, false, "word/document.xml"), "docx"))
    }

    /**
     * **저장된 `mimetype` 의 내용 하나로 '아니다' 를 정하지 않는다**. 내용이 조금 어긋난 EPUB(앞에 BOM·공백, 끝에 줄바꿈)은
     * 이름으로 넘어가 `container.xml` 로 맡는다 — 10-09 의 판은 이것을 '이 앱이 다루지 않는 문서' 로 끝냈다(고침을 되돌리면 앞의 둘이 깨진다).
     */
    @Test
    fun 내용이_조금_어긋난_mimetype_은_이름으로_본다() {
        assertEquals(FormatId.EPUB, probe(zip("﻿application/epub+zip", true, "META-INF/container.xml"), "epub"))
        assertEquals(FormatId.EPUB, probe(zip(" application/epub+zip", true, "META-INF/container.xml"), "epub"))
        // 끝의 줄바꿈은 내용의 앞머리로 맡는다.
        assertEquals(FormatId.EPUB, probe(zip("application/epub+zip\n", true, "META-INF/container.xml"), "epub"))
        // 모르는 형식의 mimetype — container.xml 이 있으면 맡고(EPUB 을 바탕으로 한 꾸러미가 있다), 없으면 맡지 않는다.
        assertEquals(FormatId.EPUB, probe(zip("application/x-ibooks+zip", true, "META-INF/container.xml"), "ibooks"))
        assertNull(probe(zip("application/x-something", true, "content.xml"), "zip"))
        // 이름을 모르면 확장자로 — 앞이 어긋난 EPUB 도 `.epub` 이면 맡는다(틀렸으면 여는이가 'container.xml 이 없다' 로 끝낸다).
        assertEquals(FormatId.EPUB, probe(zip("﻿application/epub+zip", true, "META-INF/container.xml"), "epub", namesKnown = false))
    }

    /** 알려진 다른 형식(HWPX·ODF)은 내용만으로 맡지 않는다 — 이름을 모르고 확장자가 `.epub` 이어도. */
    @Test
    fun 알려진_다른_형식은_내용으로_정한다() {
        assertNull(probe(zip("application/hwp+zip", true, "META-INF/container.xml", "Contents/content.hpf"), "epub", namesKnown = false))
        assertNull(probe(zip("application/vnd.oasis.opendocument.presentation", true, "META-INF/manifest.xml"), "epub", namesKnown = false))
        // 이름 길이 칸이 `mimetype` 이 아니면(같은 글자로 시작하는 다른 이름) 내용을 읽지 않는다 — 이름으로 본다. 길이 칸을 보지 않으면 이
        // 이름의 뒷부분(`application/hwp+zip…`)을 내용으로 읽어 HWPX 로 정한다.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            // 적힌 길이가 그 뒷부분을 덮게 길게 — 짧으면 길이 칸을 보지 않아도 내용이 짧아 갈리지 않는다.
            val body = "x".repeat(40).toByteArray()
            z.putNextEntry(ZipEntry("mimetypeapplication/hwp+zip.txt").apply {
                method = ZipEntry.STORED
                size = body.size.toLong()
                compressedSize = body.size.toLong()
                crc = CRC32().apply { update(body) }.value
            })
            z.write(body)
            z.closeEntry()
            z.putNextEntry(ZipEntry("META-INF/container.xml"))
            z.write("<x/>".toByteArray())
            z.closeEntry()
        }
        assertEquals(FormatId.EPUB, probe(out.toByteArray(), "epub"))
    }
}
