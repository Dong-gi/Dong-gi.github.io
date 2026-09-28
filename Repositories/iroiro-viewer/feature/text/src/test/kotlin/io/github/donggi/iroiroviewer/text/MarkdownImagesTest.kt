package io.github.donggi.iroiroviewer.text

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 미리보기가 싣는 그림을 **디스크에서** 고르는 검사. 변환기가 글자로 거른 뒤에도 여기서 폴더 밖을 한 번 더 막는다.
 */
class MarkdownImagesTest {

    private lateinit var root: File
    private lateinit var base: File

    @BeforeTest
    fun setUp() {
        // 시스템 임시 폴더가 아니라 이 모듈의 빌드 폴더 안에 만든다 — 시험이 저장소 밖에 흔적을 남기지 않게.
        root = File(System.getProperty("user.dir"), "build/tmp/markdown-images-test").absoluteFile
        root.deleteRecursively()
        base = File(root, "docs").apply { mkdirs() }
        File(base, "a.png").writeBytes(byteArrayOf(1, 2, 3))
        File(base, "sub").mkdirs()
        File(base, "sub/b.JPG").writeBytes(byteArrayOf(1))
        File(base, "note.txt").writeBytes(byteArrayOf(1))
        File(base, "empty.png").writeBytes(ByteArray(0))
        File(base, "dir.png").mkdirs()
        File(root, "secret.png").writeBytes(byteArrayOf(9))
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `폴더 안의 그림만 싣는다`() {
        assertEquals(File(base, "a.png").canonicalFile, MarkdownImages.resolve(base, "a.png"))
        assertEquals(File(base, "sub/b.JPG").canonicalFile, MarkdownImages.resolve(base, "sub/b.JPG"))
    }

    @Test
    fun `폴더 밖·그림 아닌 것·빈 것·폴더는 싣지 않는다`() {
        assertNull(MarkdownImages.resolve(base, "../secret.png"))
        assertNull(MarkdownImages.resolve(base, "sub/../../secret.png"))
        assertNull(MarkdownImages.resolve(base, root.absolutePath + "/secret.png"))
        assertNull(MarkdownImages.resolve(base, "note.txt"))
        assertNull(MarkdownImages.resolve(base, "empty.png"))
        assertNull(MarkdownImages.resolve(base, "dir.png"))
        assertNull(MarkdownImages.resolve(base, "none.png"))
        assertNull(MarkdownImages.resolve(base, ""))
    }

    /** 폴더 안의 심볼릭 링크가 밖을 가리킨다. 글자로는 폴더 안이라 변환기가 통과시키는 모양이다. */
    @Test
    fun `밖을 가리키는 링크는 싣지 않는다`() {
        val link = File(base, "link.png").toPath()
        val made = try {
            Files.createSymbolicLink(link, File(root, "secret.png").toPath())
            true
        } catch (e: Exception) {
            false // 윈도에서 권한이 없으면 링크를 못 만든다. 그때는 건너뛴다.
        }
        assumeTrue(made)
        assertNull(MarkdownImages.resolve(base, "link.png"))
    }

    @Test
    fun `형식은 우리 표에서만 나온다`() {
        assertEquals("image/png", MarkdownImages.mimeOf("A.PNG"))
        assertEquals("image/jpeg", MarkdownImages.mimeOf("x.jpeg"))
        assertEquals("image/svg+xml", MarkdownImages.mimeOf("d.svg"))
        assertNull(MarkdownImages.mimeOf("x.html"))
        assertNull(MarkdownImages.mimeOf("x.css"))
        assertNull(MarkdownImages.mimeOf("png"))
    }
}
