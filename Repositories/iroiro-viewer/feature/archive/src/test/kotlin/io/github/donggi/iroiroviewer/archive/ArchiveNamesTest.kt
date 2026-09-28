package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.TarArchiveReader
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/** 풀기의 이름 계산([ExtractNames])과 실패의 종류([failureKindOf]). */
class ArchiveNamesTest {

    @Test
    fun `새 폴더 이름은 두 겹 확장자를 함께 뗀다`() {
        assertEquals("backup", ExtractNames.folderNameOf("backup.tar.gz"))
        assertEquals("backup", ExtractNames.folderNameOf("backup.TAR.XZ"))
        assertEquals("src-1.2", ExtractNames.folderNameOf("src-1.2.tar.bz2"))
        assertEquals("backup", ExtractNames.folderNameOf("backup.tgz"))
        assertEquals("만화 1권", ExtractNames.folderNameOf("만화 1권.cbt"))
        assertEquals("사진.zip", ExtractNames.folderNameOf("사진.zip.7z"))
        assertEquals("README", ExtractNames.folderNameOf("README"))
        // 이름 전체가 확장자인 것을 빈 이름으로 만들지 않는다.
        assertEquals(".tar.gz", ExtractNames.folderNameOf(".tar.gz"))
        assertEquals(".hidden", ExtractNames.folderNameOf(".hidden"))
    }

    private fun entry(name: String, dir: Boolean) = ArchiveEntry(
        index = 0,
        name = name,
        safeName = ArchiveEntry.sanitize(name),
        declaredSize = 0,
        compressedSize = 0,
        isDirectory = dir,
        isLink = false,
        isEncrypted = false,
        crc = -1,
        nameCharset = "시험",
    )

    /** 폴더 엔트리와 그 안의 파일이 **같은 폴더**로 가야 한다(엔진이 폴더 엔트리도 이 계산을 지난다). */
    @Test
    fun `폴더 엔트리와 안의 파일이 같은 이름 계산을 지난다`() {
        val dir = ExtractNames.relPathOf(entry("a?b/", dir = true))
        val file = ExtractNames.relPathOf(entry("a?b/c.txt", dir = false))
        assertEquals("a_b", dir)
        assertEquals("a_b/c.txt", file)
        assertEquals(dir, file!!.substringBeforeLast('/'))
    }

    @Test
    fun `상한은 어느 상한인가로 가른다`() {
        assertEquals(
            ArchiveViewModel.State.Failed.Kind.TOO_LARGE,
            failureKindOf(ParseLimitExceededException("maxEntries", "많다")),
        )
        assertEquals(
            ArchiveViewModel.State.Failed.Kind.TOO_BIG,
            failureKindOf(ParseLimitExceededException("maxTotalOutput", "크다")),
        )
        // xz·7z 의 사전 메모리 상한(라이브러리 예외 — 이름으로 가른다).
        class MemoryLimitException : IOException()
        assertEquals(ArchiveViewModel.State.Failed.Kind.TOO_BIG, failureKindOf(MemoryLimitException()))
    }

    @Test
    fun `tar 가 아닌 압축 스트림은 다루지 않는 형식이다`() {
        assertEquals(ArchiveViewModel.State.Failed.Kind.UNSUPPORTED, failureKindOf(TarArchiveReader.NotTarException()))
        assertEquals(ArchiveViewModel.State.Failed.Kind.CORRUPT, failureKindOf(IOException("깨졌다")))
    }
}
