package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **우리가 아닌 도구가 만든** tar 를 읽는다 — 오라클이 우리가 아니다.
 *
 * 표본(`src/test/resources/tar/`, 11개 모두 20KB 이하)은 아래 도구를 **그대로 불러** 만들었다. 부른 스크립트는
 * 저장소에 넣지 않았다(도구로만 쓴다 — 미디어 표본의 ffmpeg 과 같은 규칙):
 *
 * | 표본 | 만든 도구 | 무엇을 보나 |
 * |---|---|---|
 * | `gnu-long.tar.gz` | GNU tar 1.35 `--format=gnu` | GNU 긴 이름(`L`), UTF-8 이름 바이트, 빈 폴더, 수정시각 |
 * | `pax.tar.xz` | GNU tar 1.35 `--format=pax` | PAX `path`(UTF-8), xz |
 * | `bsd-ustar.tar.bz2` | bsdtar 3.8.8(libarchive) `--format ustar` | 앞머리(prefix)로 갈린 긴 경로, **윈도우 bsdtar 가 CP949 로 적은 이름**, bzip2 |
 * | `v7.tar` | GNU tar `--format=v7` | 매직 없는 머리 — 검사합만으로 알아본다 |
 * | `empty.tar` | GNU tar | 끝 표시뿐 |
 * | `specials.tar` | 파이썬 tarfile(GNU) | 심볼릭 링크·하드링크·문자 장치·FIFO, 그 뒤의 파일 |
 * | `cp949.tar` | 파이썬 tarfile `encoding='cp949'` | CP949 이름 바이트 |
 * | `git-style.tar.gz` | 파이썬 tarfile(PAX) | 전역 PAX 머리(`g`, `git archive` 의 모양), 소수초 수정시각 |
 * | `concat.tar.gz`·`concat.tar.bz2` | 파이썬 gzip·bz2 | **이어 붙인** 멤버·스트림(pigz·pbzip2 의 모양) |
 * | `not-tar.gz` | 파이썬 gzip | tar 가 아닌 gzip |
 *
 * 목록은 만든 도구가 스스로 되읽어 확인했다(`tarfile.getnames`).
 */
class TarOracleTest {

    private val dir = File(javaClass.classLoader!!.getResource("tar/empty.tar")!!.toURI()).parentFile

    private fun open(name: String, limits: ParseLimits = ParseLimits.DEFAULT) =
        Archives.open(FileDocumentSource(File(dir, name)), limits, EntryBudget(limits))

    private fun read(r: ArchiveReader, name: String): ByteArray =
        r.open(r.entries.single { it.name.trimEnd('/') == name }).use { it.readBytes() }

    private val mtime = 1_789_207_200_000L

    private val longName = "src/" + "d".repeat(60) + "/" + "f".repeat(70) + ".txt"

    /** 긴 이름 파일의 내용. 파이썬이 윈도우에서 글 모드로 썼으므로 줄 끝이 CRLF 다(표본의 사실). */
    private val longBody = "long name body\r\n".toByteArray()

    private val pattern = ByteArray(3000) { ((it * 7) % 251).toByte() }

    private val srcNames = setOf(
        "src", "src/" + "d".repeat(60), longName, "src/emptydir", "src/readme.txt",
        "src/문서", "src/문서/sub", "src/문서/sub/pattern.bin", "src/문서/보고서.txt",
    )

    private fun names(r: ArchiveReader) = r.entries.map { it.name.trimEnd('/') }.toSet()

    @Test
    fun `GNU 형식 — 긴 이름과 UTF-8 이름과 수정시각`() {
        open("gnu-long.tar.gz").use { r ->
            assertEquals(ArchiveKind.TAR_GZ, r.kind)
            assertFalse(r.randomAccess, "압축 tar 는 스트림이다")
            assertEquals(srcNames, names(r))
            assertContentEquals("hello tar\n".toByteArray(), read(r, "src/readme.txt"))
            assertContentEquals(longBody, read(r, longName))
            assertContentEquals(pattern, read(r, "src/문서/sub/pattern.bin"))
            assertContentEquals("한글 내용\n".toByteArray(), read(r, "src/문서/보고서.txt"))
            assertTrue(r.entries.single { it.name.trimEnd('/') == "src/emptydir" }.isDirectory)
            // `--mtime` 로 박은 시각이 모든 항목에 있다.
            assertTrue(r.entries.all { it.lastModified == mtime }, r.entries.map { it.lastModified }.toString())
            assertEquals("UTF-8(플래그 없음)", r.entries.single { it.name == "src/문서/보고서.txt" }.nameCharset)
        }
    }

    @Test
    fun `PAX 형식 — UTF-8 경로와 xz`() {
        open("pax.tar.xz").use { r ->
            assertEquals(ArchiveKind.TAR_XZ, r.kind)
            assertEquals(srcNames, names(r))
            assertEquals("UTF-8(PAX)", r.entries.single { it.name == "src/문서/보고서.txt" }.nameCharset)
            assertContentEquals(pattern, read(r, "src/문서/sub/pattern.bin"))
            assertContentEquals(longBody, read(r, longName))
        }
    }

    /** 윈도우의 bsdtar 는 이름을 **ANSI 코드 페이지(CP949)** 로 적었다. ZIP 과 같은 판정이 되살린다. */
    @Test
    fun `bsdtar ustar — 앞머리로 갈린 경로와 CP949 이름`() {
        val cp949 = EntryNameDecoder.cp949 ?: return
        open("bsd-ustar.tar.bz2").use { r ->
            assertEquals(ArchiveKind.TAR_BZIP2, r.kind)
            assertEquals(srcNames, names(r))
            assertEquals(cp949.name(), r.entries.single { it.name == "src/문서/보고서.txt" }.nameCharset)
            assertContentEquals(pattern, read(r, "src/문서/sub/pattern.bin"))
            assertContentEquals(longBody, read(r, longName))
        }
    }

    @Test
    fun `V7 tar 는 매직이 없어도 검사합으로 알아본다`() {
        open("v7.tar").use { r ->
            assertEquals(ArchiveKind.TAR, r.kind)
            assertTrue(r.randomAccess, "압축 안 한 tar 파일은 무작위 접근")
            assertEquals(setOf("src/readme.txt", "src/emptydir"), names(r))
            assertTrue(r.entries.single { it.name.trimEnd('/') == "src/emptydir" }.isDirectory)
            assertContentEquals("hello tar\n".toByteArray(), read(r, "src/readme.txt"))
        }
    }

    @Test
    fun `빈 tar 는 빈 목록이다`() {
        open("empty.tar").use { r -> assertTrue(r.entries.isEmpty()) }
    }

    /** 링크·장치·FIFO 는 풀지 않고, **그 뒤의 파일이 멀쩡히 읽혀야** 자료 크기를 옳게 건너뛴 것이다. */
    @Test
    fun `특수 항목은 링크로 걸러지고 뒤의 파일은 멀쩡하다`() {
        open("specials.tar").use { r ->
            val byName = r.entries.associateBy { it.name.trimEnd('/') }
            for (n in listOf("top/link.txt", "top/hard.txt", "top/null", "top/fifo")) {
                val e = byName.getValue(n)
                assertTrue(e.isLink, "$n 은 링크로 세야 한다")
                assertFalse(e.isReadable, n)
            }
            assertTrue(byName.getValue("top").isDirectory)
            assertEquals(mtime - 86_400_000L, byName.getValue("top").lastModified, "폴더의 시각도 읽는다")
            assertContentEquals("readme body\n".toByteArray(), read(r, "top/readme.txt"))
            assertContentEquals("after specials\n".toByteArray(), read(r, "top/after.txt"))
        }
    }

    @Test
    fun `CP949 로 적은 이름`() {
        val cp949 = EntryNameDecoder.cp949 ?: return
        open("cp949.tar").use { r ->
            assertEquals(listOf("한글이름.txt", "자료/보고서.txt"), r.entries.map { it.name })
            assertTrue(r.entries.all { it.nameCharset == cp949.name() })
            assertContentEquals("가나다\n".toByteArray(cp949), read(r, "한글이름.txt"))
        }
    }

    /** `git archive` 의 첫 머리는 전역 PAX 다. 항목이 아니다. */
    @Test
    fun `전역 PAX 머리는 항목이 아니고 소수초 시각을 읽는다`() {
        open("git-style.tar.gz").use { r ->
            assertEquals(listOf("프로젝트/설명.md", "프로젝트/소수초.txt"), r.entries.map { it.name })
            assertEquals(mtime + 250, r.entries[1].lastModified)
            assertContentEquals("내용\n".toByteArray(), read(r, "프로젝트/설명.md"))
        }
    }

    /** 첫 멤버에서 멈추면 뒤의 항목이 **통째로** 사라진다. */
    @Test
    fun `이어 붙인 gzip·bzip2 를 끝까지 푼다`() {
        for (name in listOf("concat.tar.gz", "concat.tar.bz2")) {
            open(name).use { r ->
                assertEquals(7, r.entries.size, name)
                assertContentEquals("after specials\n".toByteArray(), read(r, "top/after.txt"), name)
            }
        }
    }

    @Test
    fun `tar 가 아닌 gzip 은 다루지 않는 형식이다`() {
        assertFailsWith<TarArchiveReader.NotTarException> { open("not-tar.gz") }
        assertNull(Archives.detect(FileDocumentSource(File(dir, "not-tar.gz"))))
    }

    @Test
    fun `판별이 모양을 가른다`() {
        fun detect(n: String) = Archives.detect(FileDocumentSource(File(dir, n)))
        assertEquals(ArchiveKind.TAR_GZ, detect("gnu-long.tar.gz"))
        assertEquals(ArchiveKind.TAR_XZ, detect("pax.tar.xz"))
        assertEquals(ArchiveKind.TAR_BZIP2, detect("bsd-ustar.tar.bz2"))
        assertEquals(ArchiveKind.TAR, detect("v7.tar"))
        assertEquals(ArchiveKind.TAR, detect("specials.tar"))
        assertEquals(ArchiveKind.TAR, detect("empty.tar"))
        // tar 계열은 등록소의 신원이 없다. 옛 판별(ZIP·7z·RAR)은 그대로 모른다고 답한다.
        assertNull(Archives.probeContainer(File(dir, "gnu-long.tar.gz").readBytes().copyOf(16)))
    }

    /** 순차 추출이 도구의 표본에서 목록과 같은 바이트를 낸다 — 압축 tar 는 목록 없이 돈다. */
    @Test
    fun `순차 추출이 목록 없이도 같은 항목을 낸다`() {
        for (name in listOf("gnu-long.tar.gz", "pax.tar.xz", "bsd-ustar.tar.bz2", "specials.tar")) {
            val got = LinkedHashMap<String, ByteArray>()
            open(name).use { r ->
                r.extractSequentially(object : EntrySink {
                    val open = HashMap<Int, ByteArrayOutputStream>()
                    override fun begin(entry: ArchiveEntry): OutputStream? {
                        if (!entry.isReadable) return null
                        return ByteArrayOutputStream().also { open[entry.index] = it }
                    }

                    override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
                        assertNull(failure, "$name ${entry.name}")
                        got[entry.name] = open.remove(entry.index)!!.toByteArray()
                    }
                })
            }
            open(name).use { r ->
                val readable = r.entries.filter { it.isReadable }
                assertEquals(readable.map { it.name }, got.keys.toList(), name)
                for (e in readable) assertContentEquals(r.open(e).use { it.readBytes() }, got[e.name], "$name ${e.name}")
            }
        }
    }
}
