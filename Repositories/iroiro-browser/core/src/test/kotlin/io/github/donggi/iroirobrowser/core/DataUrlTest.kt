package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataUrlTest {

    @Test
    fun base64() {
        val d = DataUrl.parse("data:image/png;base64,iVBORw0KGgo=")!!
        assertEquals("image/png", d.mimeType)
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), d.bytes)
    }

    @Test
    fun 퍼센트_부호화와_기본_형식() {
        val d = DataUrl.parse("data:,hello%20%ED%95%9C")!!
        assertEquals("text/plain", d.mimeType)
        assertEquals("hello 한", d.bytes.decodeToString())
        assertEquals("text/csv", DataUrl.parse("data:Text/CSV;charset=utf-8,a,b")!!.mimeType)
        assertEquals("a,b", DataUrl.parse("data:text/csv;charset=utf-8,a,b")!!.bytes.decodeToString())
    }

    @Test
    fun 틀린_꼴은_null() {
        assertNull(DataUrl.parse("https://x.test"))
        assertNull(DataUrl.parse("data:text/plain"))
        assertNull(DataUrl.parse("data:;base64,@@@"))
        assertNull(DataUrl.parse("data:,%Z1"))
    }
}
