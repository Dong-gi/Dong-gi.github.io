package io.github.donggi.iroiroviewer.player

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 구간 저장·소리만 저장을 거절할 자리 — 앱 캐시의 공유 폴더 아래는 **모두**다. 다른 앱이 넘긴 사본(`share/inbox`)만
 * 거절해 그 밖의 사본(그때의 네트워크 저장소 사본 `share/remote`)이 그 길로 빠졌고, '저장했습니다' 가 뜬 파일은 캐시에 남았다.
 */
class ClipSourceTest {

    private val root: File = Files.createTempDirectory("clip-source").toFile()
    private val share = File(root, "cache/share").apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun file(rel: String) = File(root, rel).apply {
        parentFile?.mkdirs()
        writeBytes(ByteArray(1))
    }

    @Test
    fun `공유 폴더 아래는 모두 앱의 사본이다`() {
        assertTrue(ClipSource.isAppCopy(file("cache/share/remote/1f2e/영상.mp4").path, share))
        assertTrue(ClipSource.isAppCopy(file("cache/share/inbox/9a8b/받은.mp4").path, share))
    }

    @Test
    fun `기기의 폴더는 아니다`() {
        assertFalse(ClipSource.isAppCopy(file("storage/Movies/영상.mp4").path, share))
    }

    /** 이름이 같은 말로 시작하는 옆 폴더(`share2`)는 아래가 아니다. 경로를 글자로만 견주면 이것을 잘못 거절한다. */
    @Test
    fun `이름만 비슷한 옆 폴더는 아니다`() {
        assertFalse(ClipSource.isAppCopy(file("cache/share2/a.mp4").path, share))
        assertFalse(ClipSource.isUnder("/data/cache/share2/a.mp4", "/data/cache/share"))
        assertTrue(ClipSource.isUnder("/data/cache/share/a.mp4", "/data/cache/share"))
        assertTrue(ClipSource.isUnder("/data/cache/share/a.mp4", "/data/cache/share/"))
        // 공유 폴더 자신은 '아래' 가 아니다(파일이 아니다).
        assertFalse(ClipSource.isUnder("/data/cache/share", "/data/cache/share"))
    }

    /** `..` 로 돌아 들어온 경로도 정규화한 자리로 본다 — 밖으로 나간 것은 밖이고, 들어온 것은 안이다. */
    @Test
    fun `정규화한 자리로 견준다`() {
        file("storage/Movies/b.mp4")
        assertFalse(ClipSource.isAppCopy(File(share, "../../storage/Movies/b.mp4").path, share))
        file("cache/share/remote/x/c.mp4")
        assertTrue(ClipSource.isAppCopy(File(root, "storage/../cache/share/remote/x/c.mp4").path, share))
    }
}
