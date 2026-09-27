package io.github.donggi.iroiroviewer.comic

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 암호가 걸린 만화.
 *
 * 표본은 **우리가 잠그지 않았다** — pyzipper(WinZip AES)와 py7zr(7z AES-256)가 잠갔고, 두 도구가
 * 자기 표본을 암호로 되읽어 쪽 내용이 맞는 것을 먼저 확인했다(스크래치패드의 `mkcomiccrypt.py`).
 * 쪽 내용은 결정적이라 여기서 다시 만든다: `"PAGE-n "` 을 마흔 번.
 */
class ComicPasswordTest {

    private val tmp: File = File(System.getProperty("java.io.tmpdir"), "iroiro-comiccrypt-${System.nanoTime()}")
        .apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun sample(name: String): File {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/comiccrypt/$name")) { name }.use { it.readBytes() }
        return File(tmp, name).apply { writeBytes(bytes) }
    }

    private fun page(n: Int): ByteArray = "PAGE-$n ".repeat(40).toByteArray(Charsets.US_ASCII)

    private fun password(): CharArray = "comic-pass".toCharArray()

    private suspend fun kindOf(file: File, password: CharArray? = null): ComicOpen.Kind {
        val r = ComicOpen.open(file.path, 1L shl 20, password = password)
        if (r is ComicOpen.Result.Ready) r.source.close()
        assertTrue(r is ComicOpen.Result.Failed, "실패해야 한다: $r")
        return r.kind
    }

    /**
     * **쪽을 폴더에 담은 잠긴 CBZ 가 '그림이 없다' 로 나가지 않는다.**
     *
     * 예전 조건은 '폴더 항목이 하나도 없고 전부 잠겼다' 였다. 폴더 항목이 있는 책(흔하다)은
     * 그 조건에서 빠져 `NO_PAGES` 가 됐다 — 암호를 물을 길 자체가 없었다.
     */
    @Test
    fun `폴더에 담긴 잠긴 쪽은 암호를 묻는다`() = runTest {
        assertEquals(ComicOpen.Kind.NEEDS_PASSWORD, kindOf(sample("folder-aes.cbz")))
    }

    @Test
    fun `암호를 받으면 잠긴 CBZ 의 쪽이 원본 그대로 나온다`() = runTest {
        val pw = password()
        val r = ComicOpen.open(sample("folder-aes.cbz").path, 1L shl 20, password = pw)
        assertTrue(r is ComicOpen.Result.Ready, "열려야 한다: $r")
        r.source.use { src ->
            assertEquals(listOf("ch01/001.png", "ch01/002.png", "ch02/003.png"), src.pages.map { it.name })
            assertContentEquals(page(1), src.bytes(0))
            assertContentEquals(page(3), src.bytes(2))
        }
    }

    @Test
    fun `항목만 잠긴 7z 도 암호를 묻는다`() = runTest {
        assertEquals(ComicOpen.Kind.NEEDS_PASSWORD, kindOf(sample("pages-aes.cb7")))
    }

    /** 헤더까지 잠겼으면 목록조차 못 읽는다 — '깨졌다' 가 아니라 '암호가 필요하다' 다. */
    @Test
    fun `헤더까지 잠긴 7z 는 깨졌다가 아니라 암호를 묻는다`() = runTest {
        assertEquals(ComicOpen.Kind.NEEDS_PASSWORD, kindOf(sample("header-aes.cb7")))
    }

    /**
     * **solid 소스는 패스마다 아카이브를 새로 연다.** 그 재열기에 암호가 실려야 한다 —
     * 목록만 암호로 읽고 쪽을 꺼낼 때 빠뜨리면 첫 패스부터 모든 쪽이 null 이다.
     */
    @Test
    fun `헤더까지 잠긴 7z 를 암호로 열면 쪽이 원본 그대로 나온다`() = runTest {
        for (name in listOf("header-aes.cb7", "pages-aes.cb7")) {
            val r = ComicOpen.open(sample(name).path, 1L shl 20, password = password())
            assertTrue(r is ComicOpen.Result.Ready, "$name 이 열려야 한다: $r")
            r.source.use { src ->
                assertEquals(true, src.solid, name)
                assertEquals(3, src.pages.size, name)
                assertContentEquals(page(2), src.bytes(1), name)
                assertContentEquals(page(3), src.bytes(2), name)
                assertContentEquals(page(1), src.bytes(0), name)
            }
        }
    }

    /**
     * **넘긴 배열은 부른 쪽의 것이다.** 소스는 자기 사본을 들고 닫을 때 그것을 지운다.
     * 부른 쪽의 배열까지 지우면 `ComicViewModel` 이 들고 있던 값이 모르는 사이에 사라진다.
     */
    @Test
    fun `소스를 닫아도 부른 쪽의 암호 배열은 그대로다`() = runTest {
        val pw = password()
        val r = ComicOpen.open(sample("header-aes.cb7").path, 1L shl 20, password = pw)
        assertTrue(r is ComicOpen.Result.Ready)
        r.source.use { it.bytes(0) }
        assertContentEquals("comic-pass".toCharArray(), pw)
    }
}
