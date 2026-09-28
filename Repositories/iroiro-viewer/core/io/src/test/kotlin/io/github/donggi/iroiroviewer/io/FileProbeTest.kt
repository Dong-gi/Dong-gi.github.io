package io.github.donggi.iroiroviewer.io

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 여는 탐침([FileProbe]). 뷰어 넷이 이 답으로 '깨졌다' 와 '읽을 수 없다' 를 가른다. */
class FileProbeTest {

    @Test
    fun `실제로 열리는 보통 파일만 참이다`() {
        val dir = createTempDirectory("fileprobe").toFile()
        try {
            val file = File(dir, "책.cbz").apply { writeBytes(ByteArray(8)) }
            assertTrue(FileProbe.opens(file))
            assertNull(FileProbe.openError(file))
            // JVM 에서는 있는데 열리지 않는 파일을 만들 수 없어 여는 일을 바꿔 끼운다(FUSE 의 권한 거부는 이 모양으로 온다).
            assertFalse(FileProbe.opens(file) { throw FileNotFoundException("EACCES (Permission denied)") }, "있는데 열리지 않는다")
            assertFalse(FileProbe.opens(file) { throw SecurityException("거부") })
            assertIs<FileNotFoundException>(FileProbe.openError(file) { throw FileNotFoundException("EACCES") })
            assertFalse(FileProbe.opens(dir), "폴더")
            // 폴더를 여는 일이 성공하는 판(플랫폼마다 다르다)에서도 폴더는 넘길 파일이 아니다.
            assertFalse(FileProbe.opens(dir) {}, "폴더 — 여는 일이 성공해도")
            file.delete()
            assertFalse(FileProbe.opens(file), "없다")
            assertIs<IOException>(FileProbe.openError(file), "없는 파일을 열면 예외를 준다")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `여는 일의 다른 예외는 삼키지 않는다`() {
        // 잡는 것은 입출력과 보안 거부뿐이다 — 코드의 결함(런타임 예외)까지 '열리지 않는다' 로 읽으면 결함이 숨는다.
        val file = File("없어도 된다")
        val thrown = runCatching { FileProbe.openError(file) { throw IllegalStateException("결함") } }.exceptionOrNull()
        assertIs<IllegalStateException>(thrown)
    }
}
