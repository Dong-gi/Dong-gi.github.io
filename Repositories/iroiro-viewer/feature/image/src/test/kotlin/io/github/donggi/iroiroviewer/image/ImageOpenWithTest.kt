package io.github.donggi.iroiroviewer.image

import io.github.donggi.iroiroviewer.image.ImageOpenWith.PageFailure
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이미지 뷰어의 '다른 앱으로 열기' 가 **어디에 서는가.** 규칙(모든 화면이 같아야 한다): 이 앱이 보여 주지 못하는
 * 파일에는 단추를 달고, 없어졌거나 읽을 수 없는 파일에는 달지 않는다.
 */
class ImageOpenWithTest {

    private fun entry(path: String, isDirectory: Boolean = false) = FileEntry(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = isDirectory,
        size = 10,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = if (isDirectory) FileKind.FOLDER else FileKind.IMAGE,
    )

    /** SVG·TIFF·못 푸는 HEIC 는 파일이 멀쩡히 있다 — 다른 앱은 열 수 있다. */
    @Test
    fun `있는 파일을 못 풀었으면 단추를 단다`() {
        val failure = ImageOpenWith.failureOf(isFile = true, opens = true)
        assertEquals(PageFailure.UNDECODABLE, failure)
        assertTrue(ImageOpenWith.offersOnFailure(failure))
    }

    /**
     * 디코더는 '없는 파일' 에도 null 을 준다. 그 값만 보고 단추를 달면 누른 사람은 '넘길 수 없다' 는 말만 듣는다 —
     * 받는 앱도 없는 파일에는 닿지 못한다.
     */
    @Test
    fun `없어진 파일에는 단추를 달지 않는다`() {
        val failure = ImageOpenWith.failureOf(isFile = false, opens = false)
        assertEquals(PageFailure.UNREADABLE, failure)
        assertFalse(ImageOpenWith.offersOnFailure(failure))
    }

    @Test
    fun `열리지 않는 파일에도 단추를 달지 않는다`() {
        assertFalse(ImageOpenWith.offersOnFailure(ImageOpenWith.failureOf(isFile = true, opens = false)))
        // 폴더(isFile 거짓)는 읽을 수 있어도 넘길 파일이 아니다.
        assertFalse(ImageOpenWith.offersOnFailure(ImageOpenWith.failureOf(isFile = false, opens = true)))
    }

    /**
     * 메뉴는 **열어 보인** 그림에도 선다 — 편집기로 넘기려는 사람이 있다. 이 뷰어가 못 푼 장에도 선다. 대상은 지금 장의
     * 경로다.
     */
    @Test
    fun `메뉴는 지금 장의 경로를 넘긴다`() {
        val svg = entry("/storage/emulated/0/DCIM/a.svg")
        assertEquals(svg.path, ImageOpenWith.menuTarget(svg, failure = null))
        assertEquals(svg.path, ImageOpenWith.menuTarget(svg, PageFailure.UNDECODABLE))
    }

    @Test
    fun `지금 장이 없거나 폴더면 메뉴 줄을 감춘다`() {
        assertNull(ImageOpenWith.menuTarget(null, failure = null))
        assertNull(ImageOpenWith.menuTarget(entry("/storage/emulated/0/DCIM", isDirectory = true), failure = null))
    }

    /** 파일에 닿지 못한 장(그새 지워졌다·열리지 않는다)은 넘겨도 열리지 않는다 — 누르면 '넘길 수 없다' 만 돌아오는 줄이다. */
    @Test
    fun `지금 장이 파일에 닿지 못했으면 메뉴 줄을 감춘다`() {
        assertNull(ImageOpenWith.menuTarget(entry("/storage/emulated/0/DCIM/gone.jpg"), PageFailure.UNREADABLE))
    }

    /**
     * 여는 탐침. `canRead()` 가 아니라 **실제로 열어 본다** — 문서·압축 화면과 같은 탐침이다. JVM 에서는 있는데 열리지 않는
     * 파일을 만들 수 없어 여는 일을 바꿔 끼워 흉내 낸다.
     */
    @Test
    fun `여는 탐침은 실제로 열리는 파일만 참이다`() {
        val dir = createTempDirectory("imageopenwith").toFile()
        try {
            val file = File(dir, "a.svg").apply { writeText("<svg/>") }
            assertTrue(ImageOpenWith.opensForReading(file))
            assertEquals(PageFailure.UNDECODABLE, ImageOpenWith.failureOf(file.isFile, ImageOpenWith.opensForReading(file)))
            // 있는데 열리지 않는다(FUSE 의 권한 거부 따위) — 파일에 닿지 못한 것이다.
            val denied = ImageOpenWith.opensForReading(file) { throw FileNotFoundException("EACCES (Permission denied)") }
            assertFalse(denied)
            assertEquals(PageFailure.UNREADABLE, ImageOpenWith.failureOf(file.isFile, denied))
            assertFalse(ImageOpenWith.opensForReading(file) { throw SecurityException("거부") })
            // 없다.
            val gone = File(dir, "gone.png")
            assertFalse(ImageOpenWith.opensForReading(gone))
            assertEquals(PageFailure.UNREADABLE, ImageOpenWith.failureOf(gone.isFile, ImageOpenWith.opensForReading(gone)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
