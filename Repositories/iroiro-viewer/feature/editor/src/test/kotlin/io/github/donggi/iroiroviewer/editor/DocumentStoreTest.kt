package io.github.donggi.iroiroviewer.editor

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 떠 둔 문서와 원본을 견주기·갈아타기(보기 → 편집이 연 때의 사본으로 열었다). */
class DocumentStoreTest {

    private val root: File = Files.createTempDirectory("docstore").toFile()
    private val store = DocumentStore(File(root, "in"))

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun bytes(n: Int, seed: Int = 7) = Random(seed).nextBytes(n)

    /** 한 번에 [step] 바이트까지만 주는 흐름 — 파이프·제공자의 흐름처럼. */
    private class Trickle(private val data: ByteArray, private val step: Int) : InputStream() {
        private var at = 0
        override fun read(): Int = if (at < data.size) data[at++].toInt() and 0xff else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (at >= data.size) return -1
            val n = minOf(len, step, data.size - at)
            System.arraycopy(data, at, b, off, n)
            at += n
            return n
        }
    }

    @Test
    fun theSameBytesAreTheSame() {
        val data = bytes(1000)
        val token = store.copyIn(ByteArrayInputStream(data))
        assertTrue(store.sameAs(token, ByteArrayInputStream(data.copyOf())))
    }

    @Test
    fun aChangedByteIsNotTheSame() {
        val data = bytes(200_000)
        val token = store.copyIn(ByteArrayInputStream(data))
        for (at in listOf(0, 65_535, 65_536, 199_999)) {
            val changed = data.copyOf().also { it[at] = (it[at] + 1).toByte() }
            assertFalse(store.sameAs(token, ByteArrayInputStream(changed)), "바뀐 자리 $at")
        }
    }

    @Test
    fun aLongerOrShorterFileIsNotTheSame() {
        val data = bytes(70_000)
        val token = store.copyIn(ByteArrayInputStream(data))
        assertFalse(store.sameAs(token, ByteArrayInputStream(data.copyOf(69_999))))
        assertFalse(store.sameAs(token, ByteArrayInputStream(data + byteArrayOf(0))))
        assertFalse(store.sameAs(token, ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun aTricklingStreamWithTheSameBytesIsTheSame() {
        // 흐름마다 한 번에 주는 양이 다르다 — 덩이째 견주면 같은 내용도 어긋난다.
        val data = bytes(150_001)
        val token = store.copyIn(Trickle(data, 2048))
        assertTrue(store.sameAs(token, Trickle(data, 1000)))
        assertTrue(store.sameAs(token, Trickle(data, 70_000)))
    }

    @Test
    fun anEmptyFileEqualsAnEmptyStream() {
        val token = store.copyIn(ByteArrayInputStream(ByteArray(0)))
        assertTrue(store.sameAs(token, ByteArrayInputStream(ByteArray(0))))
        assertFalse(store.sameAs(token, ByteArrayInputStream(byteArrayOf(1))))
    }

    @Test
    fun anUnknownOrForgottenTokenIsNeverTheSame() {
        val data = bytes(10)
        assertFalse(store.sameAs("0123456789abcdef0123456789abcdef", ByteArrayInputStream(data)))
        val token = store.copyIn(ByteArrayInputStream(data))
        store.forget(token)
        assertFalse(store.sameAs(token, ByteArrayInputStream(data)))
    }

    @Test
    fun forgetRemovesOnlyThatCopy() {
        val a = store.copyIn(ByteArrayInputStream(bytes(10, 1)))
        val b = store.copyIn(ByteArrayInputStream(bytes(10, 2)))
        val fileA = assertNotNull(store.fileOf(a))
        store.forget(a)
        assertNull(store.fileOf(a))
        assertFalse(fileA.exists())
        assertFalse(fileA.parentFile?.exists() == true, "표마다 따로 된 폴더째 지운다")
        assertNotNull(store.fileOf(b))
        // 모르는 표는 아무 일도 없다.
        store.forget(a)
        store.forget("ffffffffffffffffffffffffffffffff")
        assertNotNull(store.fileOf(b))
    }

    @Test
    fun aTooLargeCopyLeavesNothingBehind() {
        val e = assertFailsWith<DocumentStore.TooLarge> { store.copyIn(ByteArrayInputStream(bytes(2000)), limit = 1000) }
        assertEquals(1000L, e.limit)
        assertEquals(0, File(root, "in").listFiles()?.size ?: 0)
    }
}
