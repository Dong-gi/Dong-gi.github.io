package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.comic.ComicOpen.Kind
import io.github.donggi.iroiroviewer.format.archive.ArchivePasswordException
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 만화를 여는 동안 던져진 것의 종류([openFailureKind]) — 특히 **스택이 넘친 것**(기기에서 재현했다: 빈 LZ4 프레임
 * 20만 개짜리 `.cbt` 를 둔 폴더를 열기만 했는데 표지 썸네일이 이 길을 지나 프로세스가 죽었다).
 */
class ComicOpenFailureTest {

    @Test
    fun `던져진 것마다 실패의 종류가 정해져 있다`() {
        assertEquals(Kind.NEEDS_PASSWORD, openFailureKind(ArchivePasswordException(wrongPassword = false)))
        assertEquals(Kind.TOO_LARGE, openFailureKind(ParseLimitExceededException("maxEntries", "x")))
        assertEquals(Kind.CORRUPT, openFailureKind(IOException()))
        assertEquals(Kind.UNSUPPORTED, openFailureKind(IllegalStateException()))
        assertEquals(Kind.CORRUPT, openFailureKind(IllegalArgumentException()))
        assertEquals(Kind.TOO_LARGE, openFailureKind(OutOfMemoryError()))
        assertEquals(Kind.CORRUPT, openFailureKind(StackOverflowError()), "스택이 넘친 것은 그 파일이 깨진 것이다")
        // 그 밖의 `Error` 는 우리 결함이다 — 받지 않는다(부르는 쪽이 그대로 던진다).
        assertNull(openFailureKind(InternalError()))
        assertNull(openFailureKind(NoClassDefFoundError()))
    }

    /**
     * 기기에서 재현한 파일 그대로 — 빈 LZ4 프레임 20만 개(11바이트씩)를 이은 `x.cbt`. 프로세스가 죽지 않고 정의된 실패로 끝난다: 판별은
     * 안을 보지 못한 항목 하나짜리 LZ4 로 열고, 그 항목(`x`)은 그림이 아니다.
     */
    @Test
    fun `빈 LZ4 프레임만 든 만화는 그림이 없는 책이다`() = runTest {
        val dir = createTempDirectory("iroiro-comic-lz4").toFile()
        try {
            val frame = byteArrayOf(0x04, 0x22, 0x4D, 0x18, 0x60, 0x70, 0x73, 0, 0, 0, 0)
            val bytes = ByteArray(frame.size * 200_000)
            for (i in 0 until 200_000) frame.copyInto(bytes, i * frame.size)
            val cbt = File(dir, "x.cbt").apply { writeBytes(bytes) }
            val r = ComicOpen.open(cbt.path, 1L shl 20)
            assertEquals(Kind.NO_PAGES, (r as ComicOpen.Result.Failed).kind)
        } finally {
            dir.deleteRecursively()
        }
    }
}
