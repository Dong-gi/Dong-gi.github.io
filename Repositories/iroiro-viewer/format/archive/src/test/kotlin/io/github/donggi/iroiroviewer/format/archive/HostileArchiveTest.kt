package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 악의적으로 만든 아카이브에서 리더가 무엇을 하는가.
 *
 * **8단계가 이 코드를 제품으로 만든다** — 2단계에서는 목록만 보았지만 이제 사용자가
 * 실제로 푼다. 푸는 쪽이 믿고 쓰는 불변식(safeName·isReadable·예산)이 정말 성립하는지
 * 여기서 못 박는다. 하나라도 무너지면 그것은 '기능 부족' 이 아니라 **기기 저장소 전체에
 * 쓰기 권한을 가진 프로세스의 구멍**이다.
 */
class HostileArchiveTest {

    private val dir: File = Files.createTempDirectory("iroiro-hostile").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun reader(file: File, limits: ParseLimits = ParseLimits.DEFAULT) =
        Archives.open(FileDocumentSource(file), limits, EntryBudget(limits))

    /** 경로 탈출·절대경로·드라이브 문자·끝점. 전부 safeName 으로 걸러져야 한다. */
    @Test
    fun `위험한 이름은 safeName 이 없거나 상대경로로 다듬어진다`() {
        val zip = ArchiveSamples.zipWithNames(
            File(dir, "evil.zip"),
            listOf(
                "ok.txt",
                "../../../../sdcard/탈출.txt",
                "/etc/passwd",
                "C:/Windows/system32/evil.dll",
                "trailing. ",
                "a/../../b.txt",
            ),
        )
        reader(zip).use { r ->
            val byName = r.entries.associateBy { it.name }

            assertEquals("ok.txt", byName["ok.txt"]!!.safeName)
            assertNull(byName["../../../../sdcard/탈출.txt"]!!.safeName, "위로 올라가는 이름")
            assertNull(byName["trailing. "]!!.safeName, "끝의 점과 공백")
            assertNull(byName["a/../../b.txt"]!!.safeName, "가운데 .. 도 막는다")

            // 절대경로와 드라이브 문자는 **거부가 아니라 상대경로로 다듬는다.**
            // 실제 아카이브에 흔하고, 다듬으면 안전하게 풀 수 있다.
            assertEquals("etc/passwd", byName["/etc/passwd"]!!.safeName)
            assertEquals(
                "Windows/system32/evil.dll",
                byName["C:/Windows/system32/evil.dll"]!!.safeName,
            )

            // 다듬어지지 않은 것은 열리지도 않아야 한다.
            for (e in r.entries.filter { it.safeName == null }) {
                assertFalse(e.isReadable)
                assertFailsWith<IllegalArgumentException> { r.open(e) }
            }
        }
    }

    /**
     * **같은 이름 두 엔트리에서 다른 바이트가 나온다.**
     *
     * 2단계가 이름을 신원으로 쓰다가 밟은 결함이라 회귀 시험으로 박아 둔다.
     */
    @Test
    fun `같은 이름이어도 인덱스로 서로 다른 바이트가 나온다`() {
        val zip = File(dir, "dup.zip")
        ZipArchiveOutputStream(zip).use { out ->
            for (i in 0..1) {
                out.putArchiveEntry(ZipArchiveEntry("same.txt"))
                out.write("엔트리 $i\n".toByteArray())
                out.closeArchiveEntry()
            }
        }
        reader(zip).use { r ->
            val same = r.entries.filter { it.name == "same.txt" }
            assertEquals(2, same.size)
            val texts = same.map { e -> r.open(e).use { String(it.readBytes()) } }
            assertEquals(listOf("엔트리 0\n", "엔트리 1\n"), texts)
        }
    }

    /** 심볼릭 링크 엔트리는 내용이 파일이 아니라 대상 경로다. 풀어서는 안 된다. */
    @Test
    fun `심볼릭 링크 엔트리는 읽을 수 없다`() {
        val zip = File(dir, "link.zip")
        ZipArchiveOutputStream(zip).use { out ->
            out.putArchiveEntry(ZipArchiveEntry("real.txt"))
            out.write("진짜\n".toByteArray())
            out.closeArchiveEntry()

            val link = ZipArchiveEntry("link.txt")
            link.unixMode = 0b1010_000_111_111_111 // S_IFLNK | 0777
            out.putArchiveEntry(link)
            out.write("/etc/passwd".toByteArray())
            out.closeArchiveEntry()
        }
        reader(zip).use { r ->
            val link = r.entries.first { it.name == "link.txt" }
            assertTrue(link.isLink, "링크로 잡혀야 한다")
            assertFalse(link.isReadable)
            assertFailsWith<IllegalArgumentException> { r.open(link) }
        }
    }

    /** 암호 걸린 엔트리는 **복호화하지 않는다.** 목록에는 보이되 열리지 않는다. */
    @Test
    fun `암호 걸린 엔트리는 읽을 수 없다`() {
        val zip = File(dir, "enc.zip")
        ZipArchiveOutputStream(zip).use { out ->
            out.putArchiveEntry(ZipArchiveEntry("secret.txt"))
            out.write("비밀\n".toByteArray())
            out.closeArchiveEntry()

            out.putArchiveEntry(ZipArchiveEntry("plain.txt"))
            out.write("공개\n".toByteArray())
            out.closeArchiveEntry()
        }
        // **commons-compress 는 암호 엔트리를 쓰지 못한다** — `write` 가
        // UnsupportedZipFeatureException 으로 거부한다. 정상으로 쓴 뒤 플래그만 켠다.
        ArchiveSamples.markEncrypted(zip, "secret.txt")
        reader(zip).use { r ->
            val secret = r.entries.first { it.name == "secret.txt" }
            assertTrue(secret.isEncrypted)
            assertFalse(secret.isReadable)
            assertFailsWith<IllegalArgumentException> { r.open(secret) }
            // 나머지는 멀쩡히 열려야 한다 — 하나가 잠겼다고 아카이브 전체를 포기하지 않는다.
            assertTrue(r.entries.first { it.name == "plain.txt" }.isReadable)
        }
    }

    /**
     * 압축폭탄. **목록은 보이고, 읽으려 하면 상한에서 끊긴다.**
     *
     * 목록 단계에서 거부하면 멀쩡한 큰 아카이브까지 못 열게 된다 — 상한은 실제로
     * 읽은 바이트에 걸려야 한다.
     */
    @Test
    fun `압축폭탄은 읽는 도중에 끊긴다`() {
        val zip = File(dir, "bomb.zip")
        ZipArchiveOutputStream(zip).use { out ->
            out.putArchiveEntry(ZipArchiveEntry("zero.bin"))
            val chunk = ByteArray(1 shl 20)
            repeat(300) { out.write(chunk) } // 300 MiB 의 0
            out.closeArchiveEntry()
        }
        val limits = ParseLimits.DEFAULT
        reader(zip, limits).use { r ->
            val e = r.entries.single()
            assertTrue(e.isReadable, "목록에서는 열 수 있는 항목으로 보여야 한다")
            val thrown = assertFailsWith<ParseLimitExceededException> {
                r.open(e).use { s ->
                    val buf = ByteArray(1 shl 16)
                    while (s.read(buf) >= 0) Unit
                }
            }
            // **압축비가 먼저 걸린다.** 0 으로 채운 300 MiB 는 1,000:1 을 훌쩍 넘고,
            // 비율 검사는 1 MiB 를 내놓은 뒤부터 도므로 단일 출력 상한(256 MiB)보다
            // 훨씬 빨리 도달한다. 크기 상한은 '압축이 잘 안 되는 큰 엔트리' 쪽 방어다.
            assertEquals("maxCompressionRatio", thrown.limitName)
        }
    }

    /** 목록만 볼 때 쓰는 상한이 실제로 더 빡빡한가. */
    @Test
    fun `PROBE 상한은 엔트리 수를 먼저 막는다`() {
        val zip = ArchiveSamples.zip(File(dir, "many.zip"), count = 1_200, bytesEach = 32)
        assertFailsWith<ParseLimitExceededException> {
            reader(zip, ParseLimits.PROBE).use { it.entries.size }
        }
        // 기본 상한(10,000)으로는 열린다.
        reader(zip).use { assertEquals(1_200, it.entries.size) }
    }

    /** 아카이브 안의 아카이브는 **엔트리로만** 보인다. 여는 것은 호출자의 결정이다. */
    @Test
    fun `아카이브 안의 아카이브는 그냥 엔트리다`() {
        val inner = ArchiveSamples.zip(File(dir, "inner.zip"), count = 2, bytesEach = 64)
        val outer = File(dir, "outer.zip")
        ZipArchiveOutputStream(outer).use { out ->
            out.putArchiveEntry(ZipArchiveEntry("inner/inner.zip"))
            out.write(inner.readBytes())
            out.closeArchiveEntry()
        }
        reader(outer).use { r ->
            val e = r.entries.single()
            assertEquals("inner/inner.zip", e.safeName)
            assertTrue(e.isReadable)
            val head = r.open(e).use { it.readNBytes(4) }
            assertEquals(0x50.toByte(), head[0], "내용은 그냥 ZIP 바이트다")
        }
    }
}
