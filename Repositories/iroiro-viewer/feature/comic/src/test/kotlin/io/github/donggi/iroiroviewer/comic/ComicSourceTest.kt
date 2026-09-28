package io.github.donggi.iroiroviewer.comic

import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쪽을 **바이트로** 공급하는 세 갈래.
 *
 * ## 왜 JVM 시험으로 되는가
 *
 * [ComicSource] 는 `android.*` 를 하나도 쓰지 않는다 — 디코딩은 `ComicPageStore` 가
 * 하고 여기는 아카이브에서 바이트를 꺼내는 데까지다. 그 경계 덕에 **창이 제대로 도는가**,
 * **엔트리 번호가 살아 있는가**, **패스마다 예산이 새로 서는가** 를 에뮬레이터 없이
 * 초 단위로 확인할 수 있다. 이 셋이 9단계에서 가장 망가지기 쉬운 자리다.
 */
class ComicSourceTest {

    private val tmp: File = File(System.getProperty("java.io.tmpdir"), "iroiro-comic-${System.nanoTime()}")
        .apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun body(tag: String, size: Int = 64): ByteArray =
        ByteArray(size) { i -> (tag[i % tag.length].code + i).toByte() }

    private fun writeZip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val f = File(tmp, name)
        ZipArchiveOutputStream(f).use { out ->
            for ((n, b) in entries) {
                val e = ZipArchiveEntry(n)
                e.size = b.size.toLong()
                out.putArchiveEntry(e)
                out.write(b)
                out.closeArchiveEntry()
            }
            out.finish()
        }
        return f
    }

    private fun writeSevenZ(name: String, entries: List<Pair<String, ByteArray>>): File {
        val f = File(tmp, name)
        SevenZOutputFile(f).use { out ->
            out.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2)))
            for ((n, b) in entries) {
                val e = SevenZArchiveEntry()
                e.name = n
                e.size = b.size.toLong()
                e.setHasStream(true)
                out.putArchiveEntry(e)
                out.write(b)
                out.closeArchiveEntry()
            }
            out.finish()
        }
        return f
    }

    private fun writeTar(name: String, entries: List<Pair<String, ByteArray>>, gzip: Boolean = false): File {
        val f = File(tmp, name)
        val raw = f.outputStream().buffered()
        val sink = if (gzip) GzipCompressorOutputStream(raw) else raw
        TarArchiveOutputStream(sink).use { out ->
            for ((n, b) in entries) {
                val e = TarArchiveEntry(n)
                e.size = b.size.toLong()
                out.putArchiveEntry(e)
                out.write(b)
                out.closeArchiveEntry()
            }
            out.finish()
        }
        return f
    }

    private suspend fun open(file: File, windowCap: Long = 1L shl 20): ComicSource {
        val r = ComicOpen.open(file.path, windowCap)
        assertTrue(r is ComicOpen.Result.Ready, "열려야 한다: $r")
        return r.source
    }

    // ---- ZIP ----------------------------------------------------------------------

    @Test
    fun `zip 의 쪽이 자연 정렬로 선다`() = runTest {
        val f = writeZip(
            "book.cbz",
            listOf(
                "10.png" to body("j"),
                "2.png" to body("b"),
                "readme.txt" to body("x"),
                "1.png" to body("a"),
            ),
        )
        open(f).use { src ->
            assertEquals(listOf("1.png", "2.png", "10.png"), src.pages.map { it.name })
            assertContentEquals(body("a"), src.bytes(0))
            assertContentEquals(body("b"), src.bytes(1))
            assertContentEquals(body("j"), src.bytes(2))
            assertEquals(false, src.solid)
        }
    }

    /**
     * **같은 이름의 엔트리가 둘이면 둘 다 쪽이고, 서로 다른 바이트를 준다.**
     *
     * 2단계가 ZIP 에서, 8단계가 7z 에서 각각 한 번씩 밟은 결함의 만화판이다 — 이름으로
     * 되짚으면 사용자가 보는 쪽과 다른 그림이 열린다.
     */
    @Test
    fun `같은 이름 두 쪽이 서로 다른 바이트를 준다`() = runTest {
        val f = writeZip("dup.cbz", listOf("a.png" to body("첫"), "a.png" to body("둘")))
        open(f).use { src ->
            assertEquals(2, src.pages.size)
            assertContentEquals(body("첫"), src.bytes(0))
            assertContentEquals(body("둘"), src.bytes(1))
        }
    }

    @Test
    fun `맥 찌꺼기를 쪽으로 세지 않는다`() = runTest {
        val f = writeZip(
            "mac.cbz",
            listOf(
                "__MACOSX/._01.png" to body("z"),
                "01.png" to body("a"),
                "__MACOSX/._02.png" to body("z"),
                "02.png" to body("b"),
            ),
        )
        open(f).use { src -> assertEquals(listOf("01.png", "02.png"), src.pages.map { it.name }) }
    }

    @Test
    fun `없는 쪽은 널이다`() = runTest {
        val f = writeZip("one.cbz", listOf("a.png" to body("a")))
        open(f).use { src ->
            assertNull(src.bytes(-1))
            assertNull(src.bytes(1))
        }
    }

    // ---- solid 7z -----------------------------------------------------------------

    @Test
    fun `7z 는 solid 이고 쪽을 차례대로 준다`() = runTest {
        val entries = (1..12).map { "%02d.png".format(it) to body("p$it", 128) }
        val f = writeSevenZ("book.cb7", entries)
        open(f).use { src ->
            assertEquals(true, src.solid)
            assertEquals(12, src.pages.size)
            for (i in 0 until 12) {
                assertContentEquals(body("p${i + 1}", 128), src.bytes(i), "쪽 $i")
            }
        }
    }

    /** 되돌아가도 옳은 바이트가 나온다. 패스를 다시 여는 길이 실제로 도는지 본다. */
    @Test
    fun `7z 에서 되돌아가도 같은 쪽이 나온다`() = runTest {
        val entries = (1..20).map { "%02d.png".format(it) to body("q$it", 256) }
        val f = writeSevenZ("back.cb7", entries)
        open(f).use { src ->
            assertContentEquals(body("q20", 256), src.bytes(19))
            assertContentEquals(body("q1", 256), src.bytes(0))
            assertContentEquals(body("q13", 256), src.bytes(12))
            assertContentEquals(body("q1", 256), src.bytes(0))
        }
    }

    /**
     * 창이 작아도 **요청한 쪽은 언제나 나온다.**
     *
     * 창은 성능 장치이지 기능 제한이 아니다. 상한이 한 쪽보다 작아도 그 쪽 하나는
     * 담아야 한다 — 그러지 않으면 '열리지 않는 만화' 가 된다.
     */
    @Test
    fun `창이 한 쪽보다 작아도 그 쪽은 나온다`() = runTest {
        val entries = (1..6).map { "%02d.png".format(it) to body("r$it", 4096) }
        val f = writeSevenZ("tiny.cb7", entries)
        open(f, windowCap = 1).use { src ->
            for (i in entries.indices) {
                assertContentEquals(body("r${i + 1}", 4096), src.bytes(i), "쪽 $i")
            }
        }
    }

    /**
     * **이 시험이 이 파일의 이유다.**
     *
     * `EntryBudget.entryCount` 는 `resetOutput` 이 되돌리지 않는 단조 증가 값이고,
     * 리더 생성자가 엔트리마다 `beginEntry()` 를 부른다. 패스마다 예산을 새로 만들지
     * 않으면 정해진 횟수만큼 쪽을 넘긴 뒤 **멀쩡한 만화가 `maxEntries` 로 거절된다.**
     * 화면에서는 '갑자기 쪽이 안 열린다' 로 보이고 다시 열면 멀쩡해 재현이 어렵다.
     *
     * 여기서는 엔트리 300개짜리를 창 1바이트로 열어 패스를 40번 넘게 돌린다 —
     * 예산을 재사용하면 12,000 > 10,000 이라 반드시 걸린다.
     */
    @Test
    fun `패스를 많이 돌려도 엔트리 상한에 걸리지 않는다`() = runTest {
        val count = 300
        val entries = (1..count).map { "%03d.png".format(it) to body("s$it", 16) }
        val f = writeSevenZ("many.cb7", entries)
        open(f, windowCap = 1).use { src ->
            assertEquals(count, src.pages.size)
            // 창이 1바이트라 쪽마다 패스가 새로 돈다. 40번이면 12,000 엔트리다.
            for (round in 0 until 40) {
                val page = (round * 7) % count
                assertNotNull(src.bytes(page), "$round 번째 패스에서 쪽 $page 가 사라졌다")
            }
        }
    }

    // ---- 폴더 ----------------------------------------------------------------------

    @Test
    fun `폴더 만화도 같은 차례다`() = runTest {
        val dir = File(tmp, "folder").apply { mkdirs() }
        File(dir, "10.png").writeBytes(body("j"))
        File(dir, "1.png").writeBytes(body("a"))
        File(dir, "2.png").writeBytes(body("b"))
        File(dir, "note.txt").writeBytes(body("x"))
        open(dir).use { src ->
            assertEquals(listOf("1.png", "2.png", "10.png"), src.pages.map { it.name })
            assertContentEquals(body("a"), src.bytes(0))
            assertContentEquals(body("j"), src.bytes(2))
        }
    }

    // ---- tar(.cbt) ------------------------------------------------------------------

    /**
     * **`.cbt` 가 열린다**(14단계). 여는 문이 예전에는 앞 16바이트의 매직(`probeContainer`)이라 tar 는 언제나
     * '다루지 않는 형식' 이었다 — tar 의 표지는 257바이트 자리에 있다. 압축 안 한 tar 는 번호로 여는 리더다.
     */
    @Test
    fun `cbt 가 자연 정렬로 열리고 번호로 읽힌다`() = runTest {
        val f = writeTar(
            "book.cbt",
            listOf("10.png" to body("j"), "2.png" to body("b"), "notes.txt" to body("x"), "1.png" to body("a")),
        )
        open(f).use { src ->
            assertEquals(listOf("1.png", "2.png", "10.png"), src.pages.map { it.name })
            assertContentEquals(body("b"), src.bytes(1))
            assertContentEquals(body("j"), src.bytes(2))
            assertContentEquals(body("a"), src.bytes(0))
            assertEquals(false, src.solid)
        }
    }

    /** 압축 tar 는 흐름이라 앞에서부터 풀어야 한다 — solid 7z 와 같은 창의 길로 연다. */
    @Test
    fun `gzip 으로 싼 cbt 는 solid 길로 열린다`() = runTest {
        val f = writeTar("book.cbt", listOf("1.png" to body("a"), "2.png" to body("b"), "3.png" to body("c")), gzip = true)
        open(f).use { src ->
            assertEquals(3, src.pages.size)
            assertEquals(true, src.solid)
            assertContentEquals(body("c"), src.bytes(2))
            assertContentEquals(body("a"), src.bytes(0))
        }
    }

    /** gzip 으로 싼 것이 tar 가 아니면(파일 하나를 `gzip` 한 것) 여전히 다루지 않는다고 말한다. */
    @Test
    fun `tar 가 아닌 gz 는 다루지 않는다고 말한다`() = runTest {
        val f = File(tmp, "single.cbt")
        GzipCompressorOutputStream(f.outputStream()).use { it.write(body("just one file", 4096)) }
        assertEquals(
            ComicOpen.Kind.UNSUPPORTED,
            (ComicOpen.open(f.path, 1 shl 20) as ComicOpen.Result.Failed).kind,
        )
    }

    // ---- 열지 못하는 것 --------------------------------------------------------------

    @Test
    fun `그림이 없으면 정확히 그렇게 말한다`() = runTest {
        val f = writeZip("none.cbz", listOf("a.txt" to body("a"), "b.xml" to body("b")))
        assertEquals(
            ComicOpen.Kind.NO_PAGES,
            (ComicOpen.open(f.path, 1 shl 20) as ComicOpen.Result.Failed).kind,
        )
    }

    @Test
    fun `아카이브가 아니면 다루지 않는다고 말한다`() = runTest {
        val f = File(tmp, "plain.cbz").apply { writeBytes(body("not an archive", 512)) }
        assertEquals(
            ComicOpen.Kind.UNSUPPORTED,
            (ComicOpen.open(f.path, 1 shl 20) as ComicOpen.Result.Failed).kind,
        )
    }

    @Test
    fun `없는 파일은 읽을 수 없다고 말한다`() = runTest {
        assertEquals(
            ComicOpen.Kind.UNREADABLE,
            (ComicOpen.open(File(tmp, "없다.cbz").path, 1 shl 20) as ComicOpen.Result.Failed).kind,
        )
    }

    @Test
    fun `그림이 없는 폴더도 정확히 말한다`() = runTest {
        val dir = File(tmp, "empty").apply { mkdirs() }
        File(dir, "a.txt").writeBytes(body("a"))
        assertEquals(
            ComicOpen.Kind.NO_PAGES,
            (ComicOpen.open(dir.path, 1 shl 20) as ComicOpen.Result.Failed).kind,
        )
    }

    /** `.cbz` 라고 적힌 7z 는 실제로 흔하다. **매직이 이긴다.** */
    @Test
    fun `확장자가 거짓말해도 매직으로 연다`() = runTest {
        val f = writeSevenZ("거짓말.cbz", listOf("01.png" to body("a", 32)))
        open(f).use { src ->
            assertEquals(true, src.solid)
            assertContentEquals(body("a", 32), src.bytes(0))
        }
    }

    /**
     * **창은 요청한 쪽보다 앞에서 시작한다.**
     *
     * 페이저가 `beyondViewportPageCount = 1` 로 앞뒤 한 장씩을 띄우기 때문에, 창을
     * 요청한 쪽에서 정확히 시작하면 바로 다음 요청(이전 쪽)이 반드시 창 밖이 되어
     * **방금 만든 창을 버리고 패스를 한 번 더 돈다.** 기기 실측에서 15쪽으로 뛰는 데
     * 1683ms + 1021ms 가 든 것이 이 형태였다.
     */
    @Test
    fun `앞쪽 이웃을 다시 요청해도 패스가 늘지 않는다`() = runTest {
        val entries = (1..30).map { "%02d.png".format(it) to body("w$it", 1024) }
        val f = writeSevenZ("look.cb7", entries)
        val src = open(f, windowCap = 1L shl 20) as SolidComicSource
        src.use {
            assertNotNull(src.bytes(14))
            val after = src.passCount
            // 페이저가 이어서 부르는 이웃들. 전부 창 안이어야 한다.
            assertNotNull(src.bytes(13))
            assertNotNull(src.bytes(15))
            assertEquals(after, src.passCount, "이웃을 읽는 데 패스가 더 돌았다")
        }
    }

    /** 앞으로 차례대로 읽으면 창 안에서는 패스가 한 번이다. */
    @Test
    fun `창 안에서 앞으로 읽으면 패스가 한 번이다`() = runTest {
        val entries = (1..30).map { "%02d.png".format(it) to body("v$it", 1024) }
        val f = writeSevenZ("seq.cb7", entries)
        val src = open(f, windowCap = 1L shl 20) as SolidComicSource
        src.use {
            for (i in 0 until 30) assertNotNull(src.bytes(i), "쪽 $i")
            assertEquals(1, src.passCount, "창이 전부 담을 수 있는데 패스가 여러 번 돌았다")
        }
    }

    /**
     * **두 쪽 보기로 뛰어들어도 패스가 한 번이다**(14단계). 페이저가 지금 펼침 `[15,16]` 과 앞뒤 펼침 `[13,14]`·`[17,18]` 을
     * 함께 띄운다. 오른쪽에서 왼쪽이면 화면 왼쪽(뒤 쪽 16)을 먼저 청하고, 창이 거기서 한 쪽만 물러서면 앞 칸의 13 이 창
     * 밖이라 패스가 한 번 더 돈다 — 9단계가 한 쪽 보기에서 고친 것과 같은 모양이다.
     */
    @Test
    fun `두 쪽 보기로 뛰어들어도 앞 칸 때문에 패스가 더 돌지 않는다`() = runTest {
        val entries = (1..30).map { "%02d.png".format(it) to body("x$it", 1024) }
        val f = writeSevenZ("spread-jump.cb7", entries)
        val src = open(f, windowCap = 1L shl 20) as SolidComicSource
        src.use {
            src.setLookBehind(Spreads.lookBehind(twoUp = true))
            // 화면이 청하는 차례: 지금 펼침(왼쪽 = 뒤 쪽 먼저) → 다음 펼침 → 앞 펼침.
            for (page in listOf(16, 15, 17, 18, 13, 14)) {
                assertContentEquals(body("x${page + 1}", 1024), src.bytes(page), "쪽 $page")
            }
            assertEquals(1, src.passCount, "앞 칸을 뜨느라 패스가 더 돌았다")
        }
    }

    /**
     * 두 쪽 보기로 **이어 읽을 때는** 앞 칸이 이미 떠 있으므로 창이 세 쪽씩 물러서지 않는다 — 물러서면 앞으로 담을 자리만
     * 줄어 패스가 한 쪽 보기보다 자주 돈다. 페이저의 차례대로 청하면(가게가 뜬 쪽을 들고 있어 같은 쪽은 두 번 청하지 않는다)
     * 두 보기의 패스 수가 같아야 한다.
     */
    @Test
    fun `두 쪽 보기로 이어 읽으면 패스가 한 쪽 보기보다 많지 않다`() = runTest {
        val entries = (1..30).map { "%02d.png".format(it) to body("y$it", 1024) }
        val f = writeSevenZ("spread-seq.cb7", entries)
        suspend fun passesReading(twoUp: Boolean): Int {
            val src = open(f, windowCap = 8L * 1024) as SolidComicSource
            src.use {
                src.setLookBehind(Spreads.lookBehind(twoUp))
                for (page in 0 until 30) assertContentEquals(body("y${page + 1}", 1024), src.bytes(page), "쪽 $page")
                return src.passCount
            }
        }
        val single = passesReading(twoUp = false)
        val spread = passesReading(twoUp = true)
        assertTrue(single > 1, "창이 작아야 이 시험이 뜻을 가진다: $single")
        assertEquals(single, spread, "두 쪽 보기로 이어 읽는데 패스가 더 돌았다")
    }
}
