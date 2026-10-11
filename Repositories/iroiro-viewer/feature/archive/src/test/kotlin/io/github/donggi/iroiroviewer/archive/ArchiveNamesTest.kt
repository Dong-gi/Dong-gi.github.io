package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.TarArchiveReader
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @Test
    fun `새 압축 tar 도 두 겹을 떼고 파일 하나를 싼 것은 한 겹을 뗀다`() {
        assertEquals("backup", ExtractNames.folderNameOf("backup.tar.lz4"))
        assertEquals("backup", ExtractNames.folderNameOf("backup.tar.lzma"))
        assertEquals("backup", ExtractNames.folderNameOf("backup.tar.Z"))
        assertEquals("backup", ExtractNames.folderNameOf("backup.tlz"))
        assertEquals("backup", ExtractNames.folderNameOf("backup.taz"))
        assertEquals("pkg_1.0_all", ExtractNames.folderNameOf("pkg_1.0_all.deb"))
        assertEquals("initrd", ExtractNames.folderNameOf("initrd.cpio"))
        // 파일 하나를 싼 것 — 폴더는 안의 파일 이름과 같다(7-Zip 의 '…\에 풀기').
        assertEquals("log.txt", ExtractNames.folderNameOf("log.txt.gz"))
        assertEquals("x", ExtractNames.folderNameOf("x.Z"))
    }

    @Test
    fun `크기를 모르는 파일이 섞이면 풀 크기를 모른다`() {
        fun file(i: Int, size: Long) = entry("f$i", dir = false).copy(index = i, declaredSize = size)
        assertEquals(30L, knownSizeOf(listOf(file(0, 10), file(1, 20))))
        assertEquals(null, knownSizeOf(listOf(file(0, 10), file(1, -1))), "파일 하나를 싼 .gz 의 크기는 -1 이다")
        assertEquals(0L, knownSizeOf(emptyList()))
        // 폴더와 풀 수 없는 항목은 세지 않는다 — 대화상자의 '파일 n개' 와 같은 것을 센다.
        val unsafe = entry("../x", dir = false).copy(index = 2, declaredSize = -1)
        val folder = entry("d/", dir = true).copy(index = 3, declaredSize = -1)
        assertEquals(10L, knownSizeOf(listOf(file(0, 10), unsafe, folder)))
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

    /**
     * 압축 tar 의 리더가 tar 가 아니라고 하면 다루지 않는 형식이다 — 입구(`Archives.open`)는 그 경우를 항목 하나짜리로 열므로
     * 화면에 이 실패가 오는 것은 리더를 직접 만든 길뿐이다. 매직이 없는 LZMA 가 풀리지 않는 것도 '아카이브가 아니다' 다.
     */
    @Test
    fun `tar 가 아니라는 것과 아카이브가 아니라는 것은 다루지 않는 형식이다`() {
        assertEquals(ArchiveViewModel.State.Failed.Kind.UNSUPPORTED, failureKindOf(TarArchiveReader.NotTarException()))
        assertEquals(ArchiveViewModel.State.Failed.Kind.UNSUPPORTED, failureKindOf(IllegalStateException("아카이브가 아니다")))
        assertEquals(ArchiveViewModel.State.Failed.Kind.CORRUPT, failureKindOf(IOException("깨졌다")))
        // cpio·ar 의 이름 상한(tar 와 같은 값)은 '풀어야 할 양이 너무 크다' 다 — tar 의 머리 상한과 같은 갈래.
        for (limit in listOf("tarHeader", "cpioHeader", "arHeader")) {
            assertEquals(ArchiveViewModel.State.Failed.Kind.TOO_BIG, failureKindOf(ParseLimitExceededException(limit, "길다")), limit)
        }
    }

    /**
     * 봉쇄를 확인하지 못하는 이름(canonical 경로가 실패한다 — PATH_MAX 를 넘는 이름, 링크 고리)은 **쓸 수 없는 이름**이다. 예전에는 그
     * 입출력 예외가 계획('전부 풀기')에서 앱을 죽였다. 윈도에서는 긴 경로가 실패하지 않아 실패하는 리졸버를 꽂는다.
     */
    @Test
    fun `봉쇄를 확인하지 못하는 이름은 쓸 수 없는 이름이다`() {
        val dest = File("dest")
        assertNull(ExtractNames.inside(dest, "a/b.txt") { _, _ -> throw IOException("File name too long") })
        // 취소는 그대로 올라간다.
        assertFailsWith<InterruptedIOException> { ExtractNames.inside(dest, "a") { _, _ -> throw InterruptedIOException() } }
        assertEquals(File("x"), ExtractNames.inside(dest, "a") { _, _ -> File("x") })
    }
}
