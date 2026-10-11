package io.github.donggi.iroiroviewer.io

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `.ts`·`.mts` 를 다른 앱에 넘길 때의 MIME([ExternalOpen.mimeFor]·[ShareHelper.mimeOf]) — 내용이 글이면 `text/plain`, 아니면 이름의
 * MIME. 넘기는 두 길이 같은 판정을 써야 한다(공유는 한동안 TypeScript 를 영상으로 실었다).
 */
class AmbiguousMimeTest {

    private val dir = createTempDirectory("mime").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun packets(packet: Int = 188, prefix: Int = 0): ByteArray =
        ByteArray(packet * 10).also { for (i in 0 until 10) it[i * packet + prefix] = 0x47 }

    private val code = File(dir, "app.ts").apply { writeText("export const x: number = 1\n") }
    private val module = File(dir, "util.mts").apply { writeText("export default 1\n") }
    private val record = File(dir, "녹화.ts").apply { writeBytes(packets()) }
    private val avchd = File(dir, "00001.MTS").apply { writeBytes(packets(192, 4)) }

    private val noPlatform: (String) -> String? = { null }

    @Test
    fun `다른 앱으로 열기는 내용대로 넘긴다`() {
        assertEquals("text/plain", ExternalOpen.mimeFor(code, noPlatform))
        assertEquals("text/plain", ExternalOpen.mimeFor(module, noPlatform))
        assertEquals("video/mp2t", ExternalOpen.mimeFor(record, noPlatform))
        // 캠코더의 `.mts` 는 기타 종류지만 우리 표의 MIME 으로 넘긴다 — 영상 앱이 받는다.
        assertEquals("video/mp2t", ExternalOpen.mimeFor(avchd, noPlatform))
        // 없는 파일은 단정하지 않는다 — 이름대로.
        assertEquals("video/mp2t", ExternalOpen.mimeFor(File(dir, "없다.ts"), noPlatform))
    }

    @Test
    fun `공유도 같은 판정을 쓴다`() {
        assertEquals("text/plain", ShareHelper.mimeOf(listOf(code.path)))
        assertEquals("video/mp2t", ShareHelper.mimeOf(listOf(record.path)))
        assertEquals("video/mp2t", ShareHelper.mimeOf(listOf(avchd.path)))
        // 여럿이면 읽지 않는다 — 갈리는 이름이 끼면 모든 것이다.
        assertEquals("*/*", ShareHelper.mimeOf(listOf(record.path, File(dir, "b.ts").path)))
        assertEquals("image/png", ShareHelper.mimeOf(listOf(File(dir, "a.png").path, File(dir, "b.png").path)))
        assertEquals("*/*", ShareHelper.mimeOf(listOf(File(dir, "a.png").path, File(dir, "b.mp4").path)))
    }
}
