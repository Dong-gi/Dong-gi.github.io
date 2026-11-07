package io.github.donggi.iroirobrowser.download

import kotlin.test.Test
import kotlin.test.assertEquals

class TextPartsTest {

    @Test
    fun 형식은_확장자로_모르면_octet_stream() {
        val table = mapOf("mkv" to "video/x-matroska", "iso" to "application/x-iso9660-image", "xyz" to "nonsense")
        val lookup = { ext: String -> table[ext] }
        assertEquals("video/x-matroska", TextParts.mimeOf("Movie.MKV", lookup))
        assertEquals("application/x-iso9660-image", TextParts.mimeOf("a.b.iso", lookup))
        assertEquals("application/octet-stream", TextParts.mimeOf("README", lookup))
        assertEquals("application/octet-stream", TextParts.mimeOf("trailing.", lookup))
        assertEquals("application/octet-stream", TextParts.mimeOf(".hidden", lookup))
        assertEquals("application/octet-stream", TextParts.mimeOf("a.xyz", lookup))
    }
}
