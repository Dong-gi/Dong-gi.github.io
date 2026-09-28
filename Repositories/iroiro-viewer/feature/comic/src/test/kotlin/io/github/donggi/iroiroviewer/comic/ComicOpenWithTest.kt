package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.comic.ComicOpen.Kind
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 만화 뷰어의 '다른 앱으로 열기' 가 **어디에 서는가.** 규칙(모든 화면이 같아야 한다): 이 앱이 보여 줄 수 없는 파일에는
 * 단추를 달고, 이 앱이 스스로 다루는 것(암호 묻기)·없어진 파일·폴더에는 달지 않는다.
 */
class ComicOpenWithTest {

    private val book = "/storage/emulated/0/Comics/책.cbz"

    @Test
    fun `이 앱이 보여 줄 수 없는 책에는 실패 화면에 단추를 단다`() {
        for (kind in listOf(Kind.UNSUPPORTED, Kind.CORRUPT, Kind.ENCRYPTED, Kind.TOO_LARGE)) {
            assertEquals(book, ComicOpenWith.failureTarget(kind, book), "$kind")
        }
    }

    /**
     * 암호는 이 앱이 묻고 푼다 — 단추가 '암호 넣기' 와 나란히 서면 어느 쪽이 길인지 흐려진다. 없어진 파일은 넘겨도
     * 열리지 않고, 그림이 없는 책은 보여 줄 것이 없는 것이지 못 보여 주는 것이 아니다.
     */
    @Test
    fun `암호를 묻는 책과 없는 파일과 그림 없는 책에는 달지 않는다`() {
        for (kind in listOf(Kind.NEEDS_PASSWORD, Kind.UNREADABLE, Kind.NO_PAGES)) {
            assertNull(ComicOpenWith.failureTarget(kind, book), "$kind")
        }
    }

    /** 이 표가 모든 실패 종류를 다룬다 — 종류가 늘면 이 시험이 먼저 깨져 표를 다시 보게 한다. */
    @Test
    fun `실패 종류마다 판단이 정해져 있다`() {
        val decided = listOf(
            Kind.UNSUPPORTED, Kind.CORRUPT, Kind.ENCRYPTED, Kind.TOO_LARGE,
            Kind.NEEDS_PASSWORD, Kind.UNREADABLE, Kind.NO_PAGES,
        )
        assertEquals(Kind.entries.toSet(), decided.toSet())
    }

    /** 폴더로 연 만화는 넘길 **파일**이 없다. 실패가 무엇이든 단추도 메뉴도 없다. */
    @Test
    fun `폴더로 연 만화에는 단추도 메뉴도 없다`() {
        for (kind in Kind.entries) assertNull(ComicOpenWith.failureTarget(kind, file = null), "$kind")
        assertNull(ComicOpenWith.menuTarget(file = null, failure = null))
        for (kind in Kind.entries) assertNull(ComicOpenWith.menuTarget(file = null, failure = kind), "$kind")
    }

    /** 메뉴는 사용자가 고른 것이라 넓다 — 열린 책에도, 암호를 묻는 책에도 선다. */
    @Test
    fun `메뉴는 열린 책과 암호를 묻는 책에도 선다`() {
        assertEquals(book, ComicOpenWith.menuTarget(book, failure = null))
        assertEquals(book, ComicOpenWith.menuTarget(book, Kind.NEEDS_PASSWORD))
        assertEquals(book, ComicOpenWith.menuTarget(book, Kind.NO_PAGES))
        assertEquals(book, ComicOpenWith.menuTarget(book, Kind.CORRUPT))
    }

    @Test
    fun `없어진 책에는 메뉴도 없다`() {
        assertNull(ComicOpenWith.menuTarget(book, Kind.UNREADABLE))
    }

    @Test
    fun `여는 중에는 메뉴가 없다`() {
        assertNull(ComicOpenWith.menuTarget(ComicViewModel.State.Loading))
        assertEquals(book, ComicOpenWith.menuTarget(ComicViewModel.State.Failed(Kind.CORRUPT, book)))
        assertNull(ComicOpenWith.menuTarget(ComicViewModel.State.Failed(Kind.NO_PAGES, file = null)))
    }

    // ---- 못 연 쪽 -----------------------------------------------------------------------

    /**
     * 폴더로 연 만화의 쪽은 **디스크 위의 그림 파일**이다. 이 기기가 못 푼 쪽(HEIC·AVIF·깨진 JPEG)은 다른 앱이 열 수 있다 —
     * '이 쪽을 열 수 없습니다' 곁에 **그 쪽의 파일**을 넘긴다.
     */
    @Test
    fun `폴더 만화의 못 연 쪽은 그 쪽의 파일을 넘긴다`() = runTest {
        withTempDir { dir ->
            val book = File(dir, "책").apply { mkdirs() }
            File(book, "1.jpg").writeBytes(ByteArray(16) { 1 })
            File(book, "2.heic").writeBytes(ByteArray(16) { 2 })
            val source = (ComicOpen.open(book.path, 1L shl 20) as ComicOpen.Result.Ready).source
            source.use {
                val page = it.pageFile(1)
                assertEquals(File(book, "2.heic").path, page?.path)
                assertEquals(page?.path, ComicOpenWith.pageFailureTarget(page))
            }
        }
    }

    /** 아카이브 안의 쪽에는 경로가 없다 — 뽑아서 넘기지 않는다(만화 뷰어가 하지 않기로 한 일이다). */
    @Test
    fun `아카이브 안의 쪽에는 단추가 없다`() = runTest {
        withTempDir { dir ->
            val cbz = File(dir, "책.cbz")
            java.util.zip.ZipOutputStream(cbz.outputStream()).use { out ->
                out.putNextEntry(java.util.zip.ZipEntry("1.jpg"))
                out.write(ByteArray(16) { 1 })
                out.closeEntry()
            }
            val source = (ComicOpen.open(cbz.path, 1L shl 20) as ComicOpen.Result.Ready).source
            source.use {
                assertNull(it.pageFile(0))
                assertNull(ComicOpenWith.pageFailureTarget(it.pageFile(0)))
            }
        }
    }

    /**
     * 여는 **사이에** 없어진 책(solid 아카이브를 훑는 몇 초 동안 지웠거나 SD 카드를 뽑았다)은 깨진 책이 아니다 — 리더의
     * `IOException` 을 그대로 '깨졌다' 로 두면 없는 파일에 '다른 앱으로 열기' 가 선다. 실패한 길에서 한 번 더 열어 본다.
     */
    @Test
    fun `여는 사이에 없어진 책은 깨진 책이 아니다`() = runTest {
        withTempDir { dir ->
            val cbz = File(dir, "깨짐.cbz")
            java.util.zip.ZipOutputStream(cbz.outputStream()).use { out ->
                out.putNextEntry(java.util.zip.ZipEntry("1.jpg"))
                out.write(ByteArray(64) { 1 })
                out.closeEntry()
            }
            // 중앙 디렉터리를 잘라 낸다 — 리더가 `IOException` 을 낸다.
            cbz.writeBytes(cbz.readBytes().copyOf(40))
            assertEquals(Kind.CORRUPT, (ComicOpen.open(cbz.path, 1L shl 20) as ComicOpen.Result.Failed).kind)

            var probes = 0
            val gone = ComicOpen.open(cbz.path, 1L shl 20, reachable = { probes++ == 0 })
            assertEquals(Kind.UNREADABLE, (gone as ComicOpen.Result.Failed).kind)
            assertEquals(2, probes, "여는 문에서 한 번, 실패한 뒤에 한 번")
            assertNull(ComicOpenWith.failureTarget(Kind.UNREADABLE, cbz.path), "없는 책에는 단추가 없다")
        }
    }

    /**
     * 예외가 아니라 **값으로** 돌아오는 실패도 같다. 압축 판별(`Archives.detect`)은 두 번째 읽기의 실패를 삼켜 '다루지 않는
     * 형식' 으로 돌려주므로, 여는 사이에 없어진 `.cbt` 는 예외 없이 UNSUPPORTED 로 온다. 단추를 세울 실패는 무엇으로 끝났든
     * 한 번 더 열어 본다.
     */
    @Test
    fun `다루지 않는 형식으로 끝나도 없어졌으면 읽을 수 없다`() = runTest {
        withTempDir { dir ->
            val notArchive = File(dir, "가짜.cbz").apply { writeBytes("이것은 압축 파일이 아니다".toByteArray()) }
            assertEquals(Kind.UNSUPPORTED, (ComicOpen.open(notArchive.path, 1L shl 20) as ComicOpen.Result.Failed).kind)

            var probes = 0
            val gone = ComicOpen.open(notArchive.path, 1L shl 20, reachable = { probes++ == 0 })
            assertEquals(Kind.UNREADABLE, (gone as ComicOpen.Result.Failed).kind)
            assertEquals(2, probes)
        }
    }

    /** 단추가 없는 실패(그림이 없다·암호를 묻는다)는 다시 열어 보지 않는다 — 열어 볼 까닭이 없다. */
    @Test
    fun `단추가 없는 실패는 다시 열어 보지 않는다`() = runTest {
        withTempDir { dir ->
            val empty = File(dir, "빈책.cbz")
            java.util.zip.ZipOutputStream(empty.outputStream()).use { out ->
                out.putNextEntry(java.util.zip.ZipEntry("설명.txt"))
                out.write("그림이 없다".toByteArray())
                out.closeEntry()
            }
            var probes = 0
            val r = ComicOpen.open(empty.path, 1L shl 20, reachable = { probes++; true })
            assertEquals(Kind.NO_PAGES, (r as ComicOpen.Result.Failed).kind)
            assertEquals(1, probes, "여는 문에서 한 번뿐")
        }
    }

    /** 그새 지워졌거나 열리지 않는 쪽 파일은 넘겨도 열리지 않는다. */
    @Test
    fun `사라졌거나 열리지 않는 쪽 파일에는 단추가 없다`() {
        withTempDir { dir ->
            val page = File(dir, "3.avif").apply { writeBytes(ByteArray(16)) }
            assertEquals(page.path, ComicOpenWith.pageFailureTarget(page))
            assertNull(ComicOpenWith.pageFailureTarget(page) { isReachable(it) { throw FileNotFoundException("EACCES") } })
            page.delete()
            assertNull(ComicOpenWith.pageFailureTarget(page))
            assertNull(ComicOpenWith.pageFailureTarget(null))
        }
    }

    /**
     * 여는 탐침. `canRead()` 가 아니라 **실제로 열어 본다**(문서·압축 화면과 같은 탐침). JVM 에서는 있는데 열리지 않는 파일을
     * 만들 수 없어 여는 일을 바꿔 끼워 흉내 낸다.
     */
    @Test
    fun `여는 탐침은 실제로 열리는 보통 파일만 참이다`() {
        withTempDir { dir ->
            val file = File(dir, "책.cbz").apply { writeBytes(ByteArray(8)) }
            assertTrue(isReachable(file))
            assertFalse(isReachable(file) { throw FileNotFoundException("EACCES (Permission denied)") }, "있는데 열리지 않는다")
            assertFalse(isReachable(file) { throw SecurityException("거부") })
            assertFalse(isReachable(dir), "폴더")
            file.delete()
            assertFalse(isReachable(file), "없다")
        }
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDirectory("comicopenwith").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
