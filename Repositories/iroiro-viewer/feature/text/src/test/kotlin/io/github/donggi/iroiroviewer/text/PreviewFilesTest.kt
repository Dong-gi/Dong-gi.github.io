package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.format.text.PreviewAsset
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 미리보기가 싣는 파일을 **디스크에서** 고르는 검사. 변환기가 글자로 거른 뒤에도 여기서 폴더 밖을 한 번 더 막는다.
 */
class PreviewFilesTest {

    private lateinit var root: File
    private lateinit var base: File

    private val image = PreviewAsset.Use.IMAGE
    private val style = PreviewAsset.Use.STYLE
    private val markdown = PreviewAsset.Use.MARKDOWN

    @BeforeTest
    fun setUp() {
        // 시스템 임시 폴더가 아니라 이 모듈의 빌드 폴더 안에 만든다 — 시험이 저장소 밖에 흔적을 남기지 않게.
        root = File(System.getProperty("user.dir"), "build/tmp/preview-files-test").absoluteFile
        root.deleteRecursively()
        base = File(root, "docs").apply { mkdirs() }
        File(base, "a.png").writeBytes(byteArrayOf(1, 2, 3))
        File(base, "sub").mkdirs()
        File(base, "sub/b.JPG").writeBytes(byteArrayOf(1))
        File(base, "note.txt").writeBytes(byteArrayOf(1))
        File(base, "empty.png").writeBytes(ByteArray(0))
        File(base, "dir.png").mkdirs()
        File(base, "f.woff2").writeBytes(byteArrayOf(7))
        File(base, "s.css").writeBytes("p{}".toByteArray())
        File(root, "secret.png").writeBytes(byteArrayOf(9))
        File(root, "secret.css").writeBytes("p{}".toByteArray())
        File(base, ".png").writeBytes(byteArrayOf(4))
        File(base, "c.jfif").writeBytes(byteArrayOf(5))
        File(base, "d.ico").writeBytes(byteArrayOf(6))
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `폴더 안의 그림만 싣는다`() {
        assertEquals(File(base, "a.png").canonicalFile, PreviewFiles.resolve(base, "a.png", image)?.file)
        assertEquals("image/png", PreviewFiles.resolve(base, "a.png", image)?.mimeType)
        assertEquals(File(base, "sub/b.JPG").canonicalFile, PreviewFiles.resolve(base, "sub/b.JPG", image)?.file)
        assertEquals("image/jpeg", PreviewFiles.resolve(base, "sub/b.JPG", image)?.mimeType)
    }

    @Test
    fun `폴더 밖·그림 아닌 것·빈 것·폴더는 싣지 않는다`() {
        assertNull(PreviewFiles.resolve(base, "../secret.png", image))
        assertNull(PreviewFiles.resolve(base, "sub/../../secret.png", image))
        assertNull(PreviewFiles.resolve(base, root.absolutePath + "/secret.png", image))
        assertNull(PreviewFiles.resolve(base, "note.txt", image))
        assertNull(PreviewFiles.resolve(base, "empty.png", image))
        assertNull(PreviewFiles.resolve(base, "dir.png", image))
        assertNull(PreviewFiles.resolve(base, "none.png", image))
        assertNull(PreviewFiles.resolve(base, "", image))
    }

    @Test
    fun `글꼴은 CSS 가 가리켰을 때만`() {
        assertEquals("font/woff2", PreviewFiles.resolve(base, "f.woff2", style)?.mimeType)
        assertNull(PreviewFiles.resolve(base, "f.woff2", image))
        // 스타일시트는 파일로 내주지 않는다 — 위생을 거친 사본만 나간다.
        assertNull(PreviewFiles.resolve(base, "s.css", style))
    }

    @Test
    fun `스타일시트는 폴더 안의 css 만, 상한 안에서만 읽는다`() {
        assertContentEquals("p{}".toByteArray(), PreviewFiles.readStylesheet(base, "s.css", 1024))
        assertNull(PreviewFiles.readStylesheet(base, "s.css", 2))
        assertNull(PreviewFiles.readStylesheet(base, "../secret.css", 1024))
        assertNull(PreviewFiles.readStylesheet(base, "a.png", 1024))
        assertNull(PreviewFiles.readStylesheet(base, "none.css", 1024))
    }

    /**
     * 마크다운은 **14단계의 표 그대로다**(HTML 미리보기를 세우며 마크다운의 동작이 바뀌었다). `.jfif`·`.ico` 는 HTML 만
     * 싣고, 점으로 시작하는 이름(`.png`)도 예전처럼 싣는다.
     */
    @Test
    fun `마크다운의 그림 표는 예전 그대로`() {
        assertEquals("image/png", PreviewFiles.resolve(base, ".png", markdown)?.mimeType)
        assertNull(PreviewFiles.resolve(base, "c.jfif", markdown))
        assertNull(PreviewFiles.resolve(base, "d.ico", markdown))
        assertNull(PreviewFiles.resolve(base, "f.woff2", markdown))
        assertEquals("image/jpeg", PreviewFiles.resolve(base, "c.jfif", image)?.mimeType)
        assertEquals("image/x-icon", PreviewFiles.resolve(base, "d.ico", image)?.mimeType)
        assertEquals("image/png", PreviewFiles.resolve(base, "a.png", markdown)?.mimeType)
        assertEquals("image/jpeg", PreviewFiles.resolve(base, "sub/b.JPG", markdown)?.mimeType)
    }

    @Test
    fun `고를 때 잰 크기를 함께 준다`() {
        assertEquals(3L, PreviewFiles.resolve(base, "a.png", image)?.length)
        assertEquals(1L, PreviewFiles.resolve(base, "sub/b.JPG", image)?.length)
    }

    /**
     * 심볼릭 링크를 **가짜 풀이 함수**로 — 윈도는 권한 없이 링크를 만들 수 없어 아래의 진짜 링크 시험이 건너뛴다.
     * 판단은 풀어 본 경로로만 한다: 폴더 밖이면 싣지 않고, 형식도 풀어 본 실제 이름으로 정한다.
     */
    @Test
    fun `밖을 가리키는 링크 — 가짜 풀이로`() {
        val links = mapOf(
            File(base, "link.png").path to File(root, "secret.png"),
            File(base, "link.css").path to File(root, "secret.css"),
            File(base, "fake.png").path to File(base, "note.txt"),
            File(base, "ok.png").path to File(base, "a.png"),
        )
        val canonical: (File) -> File = { f -> (links[f.path] ?: f).canonicalFile }
        assertNull(PreviewFiles.resolve(base, "link.png", image, canonical))
        assertNull(PreviewFiles.readStylesheet(base, "link.css", 1024, canonical))
        assertNull(PreviewFiles.resolve(base, "fake.png", image, canonical))
        // 폴더 안의 그림을 가리키는 링크는 싣는다 — 내주는 것은 풀어 본 파일이다.
        assertEquals(File(base, "a.png").canonicalFile, PreviewFiles.resolve(base, "ok.png", image, canonical)?.file)
        // 바탕 폴더 자신이 링크여도 풀어 본 경로끼리 견준다.
        val movedBase: (File) -> File = { f -> if (f.path == base.path) File(root, "docs").canonicalFile else f.canonicalFile }
        assertEquals(File(base, "a.png").canonicalFile, PreviewFiles.resolve(base, "a.png", image, movedBase)?.file)
    }

    /** 스크립트를 실행하는 쪽의 표 — 그림·글꼴에 스타일시트·자료가 더해지고, 폴더 밖과 링크는 똑같이 막는다. */
    @Test
    fun `스크립트를 실행하는 쪽의 파일 고르기`() {
        assertEquals("text/css", PreviewFiles.resolveLive(base, "s.css")?.mimeType)
        assertEquals("text/plain", PreviewFiles.resolveLive(base, "note.txt")?.mimeType)
        assertEquals("font/woff2", PreviewFiles.resolveLive(base, "f.woff2")?.mimeType)
        assertEquals("image/jpeg", PreviewFiles.resolveLive(base, "sub/b.JPG")?.mimeType)
        assertNull(PreviewFiles.resolveLive(base, "../secret.png"))
        assertNull(PreviewFiles.resolveLive(base, "empty.png"))
        assertNull(PreviewFiles.resolveLive(base, "dir.png"))
        assertNull(PreviewFiles.resolveLive(base, ""))
        val links = mapOf(File(base, "link.css").path to File(root, "secret.css"))
        val canonical: (File) -> File = { f -> (links[f.path] ?: f).canonicalFile }
        assertNull(PreviewFiles.resolveLive(base, "link.css", canonical))
    }

    /** 폴더 안의 심볼릭 링크가 밖을 가리킨다. 글자로는 폴더 안이라 변환기가 통과시키는 모양이다. */
    @Test
    fun `밖을 가리키는 링크는 싣지 않는다`() {
        val made = try {
            Files.createSymbolicLink(File(base, "link.png").toPath(), File(root, "secret.png").toPath())
            Files.createSymbolicLink(File(base, "link.css").toPath(), File(root, "secret.css").toPath())
            // 폴더 안의 다른 것을 가리키는 링크인데 형식이 다르다 — 형식은 풀어 본 실제 이름으로 정한다.
            Files.createSymbolicLink(File(base, "fake.png").toPath(), File(base, "note.txt").toPath())
            true
        } catch (e: Exception) {
            false // 윈도에서 권한이 없으면 링크를 못 만든다. 그때는 건너뛴다.
        }
        assumeTrue(made)
        assertNull(PreviewFiles.resolve(base, "link.png", image))
        assertNull(PreviewFiles.readStylesheet(base, "link.css", 1024))
        assertNull(PreviewFiles.resolve(base, "fake.png", image))
    }
}
