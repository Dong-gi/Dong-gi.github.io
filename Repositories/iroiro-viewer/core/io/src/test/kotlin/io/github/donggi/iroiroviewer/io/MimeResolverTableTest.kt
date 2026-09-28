package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 확장자 표([MimeResolver.extensionTable])를 통째로 훑는다. [MimeResolver.kindOf] 가 이 표 하나로 답하고, 두 표가
 * 겹치면 말없이 앞의 것이 이기므로(한동안 `ts` 가 영상과 코드 두 표에 있었다) 겹침과 계열 어긋남을 잡는 것은 이
 * 시험뿐이다.
 */
class MimeResolverTableTest {

    private val none: (String) -> String? = { null }

    // 어떤 확장자에도 계열 밖의 MIME 을 주는 플랫폼 — 그래도 열 때의 답은 계열 안이어야 한다.
    private val hostile: (String) -> String? = { "application/x-evil" }

    private val all = MimeResolver.extensionTable.flatMap { (kind, exts) -> exts.map { kind to it } }

    @Test
    fun `한 확장자는 한 표에만 있다`() {
        val twice = all.groupBy { it.second }.filterValues { it.size > 1 }
        assertTrue(twice.isEmpty(), "두 표에 든 확장자: $twice")
    }

    @Test
    fun `확장자로 정하는 종류는 모두 표에 한 줄씩 있다`() {
        // 폴더와 기타 말고는 모두 확장자로 정해진다. 새 종류를 표에 적지 않으면 kindOf 가 그 종류를 영영 내지 않는다.
        val rows = MimeResolver.extensionTable.map { it.first }
        val expected = FileKind.entries.filter { it != FileKind.FOLDER && it != FileKind.OTHER }
        assertEquals(expected.sorted(), rows.sorted())
        assertTrue(MimeResolver.extensionTable.none { it.second.isEmpty() })
    }

    @Test
    fun `표 밖의 이름은 기타이고 폴더는 이름을 보지 않는다`() {
        assertEquals(FileKind.OTHER, MimeResolver.kindOf("a.qqq", isDirectory = false))
        assertEquals(FileKind.OTHER, MimeResolver.kindOf("README", isDirectory = false))
        assertEquals(FileKind.FOLDER, MimeResolver.kindOf("사진.jpg", isDirectory = true))
    }

    @Test
    fun `표의 종류와 kindOf 가 같다`() {
        for ((kind, ext) in all) {
            assertEquals(kind, MimeResolver.kindOf("x.$ext", isDirectory = false), ext)
            assertEquals(kind, MimeResolver.kindOf("X.${ext.uppercase()}", isDirectory = false), ext)
        }
    }

    @Test
    fun `그림 소리 영상 글 코드는 열 때 자기 계열이다`() {
        val family = mapOf(
            FileKind.IMAGE to "image/", FileKind.AUDIO to "audio/", FileKind.VIDEO to "video/",
            FileKind.TEXT to "text/", FileKind.CODE to "text/",
        )
        for ((kind, ext) in all) {
            val prefix = family[kind] ?: continue
            for (platform in listOf(none, hostile)) {
                val mime = ExternalMime.resolve("x.$ext", platform)
                assertTrue(mime.startsWith(prefix), "$ext → $mime")
            }
        }
    }

    @Test
    fun `표의 MIME 은 소문자이고 모양이 바르다`() {
        // 안드로이드의 MIME 대조는 대소문자를 가린다. 플랫폼 표와 FileProvider 가 주는 값도 소문자다.
        for ((_, ext) in all) {
            val mime = MimeResolver.mimeOf("x.$ext")
            assertEquals(mime.lowercase(), mime, ext)
            val slash = mime.indexOf('/')
            assertTrue(slash > 0 && slash < mime.length - 1 && mime.indexOf('/', slash + 1) < 0, "$ext → $mime")
            assertTrue(mime.none { it.isWhitespace() || it == ';' || it == '*' }, "$ext → $mime")
        }
    }

    /**
     * 검토한 값을 박는다. 바꾸려면 여기서 먼저 고치고 까닭을 [MimeResolver.mimeOf] 의 KDoc 에 적는다 — 틀린 MIME 은
     * 파일을 엉뚱한 앱에 보낸다.
     */
    @Test
    fun `검토한 MIME`() {
        val expected = mapOf(
            // IANA 등록값
            "doc" to "application/msword", "dot" to "application/msword",
            "xls" to "application/vnd.ms-excel", "xlt" to "application/vnd.ms-excel",
            "ppt" to "application/vnd.ms-powerpoint", "pot" to "application/vnd.ms-powerpoint",
            "pps" to "application/vnd.ms-powerpoint",
            "docm" to "application/vnd.ms-word.document.macroenabled.12",
            "dotm" to "application/vnd.ms-word.template.macroenabled.12",
            "xlsm" to "application/vnd.ms-excel.sheet.macroenabled.12",
            "xltm" to "application/vnd.ms-excel.template.macroenabled.12",
            "pptm" to "application/vnd.ms-powerpoint.presentation.macroenabled.12",
            "potm" to "application/vnd.ms-powerpoint.template.macroenabled.12",
            "ppsm" to "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
            "odt" to "application/vnd.oasis.opendocument.text",
            "ods" to "application/vnd.oasis.opendocument.spreadsheet",
            "odp" to "application/vnd.oasis.opendocument.presentation",
            "rtf" to "application/rtf",
            "azw" to "application/vnd.amazon.ebook", "azw3" to "application/vnd.amazon.mobi8-ebook",
            "ttf" to "font/ttf", "otf" to "font/otf", "woff" to "font/woff", "woff2" to "font/woff2",
            "ttc" to "font/collection",
            "jar" to "application/java-archive",
            "tif" to "image/tiff", "tiff" to "image/tiff", "ico" to "image/vnd.microsoft.icon",
            "heic" to "image/heic", "heif" to "image/heif",
            "amr" to "audio/amr",
            "3gp" to "video/3gpp", "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "ogv" to "video/ogg",
            "m2ts" to "video/mp2t", "ts" to "video/mp2t",
            "rmvb" to "application/vnd.rn-realmedia-vbr",
            // 등록값이 없거나 앱이 등록값 대신 거르는 사실상의 값(플랫폼 표와 같다)
            "mobi" to "application/x-mobipocket-ebook", "fb2" to "application/x-fictionbook+xml",
            "iso" to "application/x-iso9660-image", "dng" to "image/x-adobe-dng",
            "wma" to "audio/x-ms-wma", "mka" to "audio/x-matroska", "mid" to "audio/midi", "midi" to "audio/midi",
            "aiff" to "audio/x-aiff", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv",
            "hwp" to "application/x-hwp", "hwpx" to "application/hwp+zip",
            "apk" to "application/vnd.android.package-archive",
        )
        for ((ext, mime) in expected) assertEquals(mime, MimeResolver.mimeOf("x.$ext"), ext)
    }
}
