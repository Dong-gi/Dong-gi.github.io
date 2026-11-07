package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.io.AesZipKey
import io.github.donggi.iroiroviewer.io.AesZipWriter
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **독립 오라클(pyzipper)과 두 방향으로 견준다.** 환경 변수 `IROIRO_AESZIP_ORACLE` 이 폴더를 가리킬 때만 돈다 — 평소의 `test` 에서는 건너뛴다
 * (파이썬과 pyzipper 가 있어야 하고 표본이 수십 MB 다).
 *
 * 1. 우리가 잠근 ZIP 을 `ours/` 에, 그 평문을 `ours-plain/<표본>/` 에 둔다 — 파이썬이 pyzipper 로 풀어 바이트를 견주고 머리 칸을 읽는다.
 * 2. pyzipper 가 잠근 ZIP(`py/manifest.tsv`)을 우리 리더로 풀어 `py-plain/<표본>/` 의 평문과 견준다. 틀린 암호는 '틀렸다' 여야 하고, 한 비트를
 *    바꾼 표본(`py/tampered.tsv`)은 읽다가 실패해야 한다.
 *
 * 순서와 스크립트는 CLAUDE.md 의 'AES 암호 ZIP' 절에 적었다. 표본은 전부 이 자리에서 지은 합성 데이터다(실세계 문서가 아니다).
 */
class AesZipOracleTest {

    private val root: File? = System.getenv("IROIRO_AESZIP_ORACLE")?.let(::File)

    /** 2025-12-31 23:59:58 UTC. */
    private val time = 1_767_225_598_000L

    private class Sample(val name: String, val password: String, val limit: Long, val chunk: Int, val items: List<Pair<String, ByteArray?>>)

    private fun stored(name: String) = name.substringAfterLast('.') in setOf("jpg", "png", "mp4", "zip")

    private fun samples(): List<Sample> {
        val rnd = Random(1016)
        fun words(n: Int): ByteArray {
            val vocab = listOf("암호", "압축", "files", "zip", "AES", "가나다", "かな", "漢字", "x", "lorem", "ipsum", "123")
            val sb = StringBuilder()
            while (sb.length < n) sb.append(vocab[rnd.nextInt(vocab.size)]).append(if (rnd.nextInt(9) == 0) '\n' else ' ')
            return sb.toString().toByteArray()
        }
        val basic = listOf(
            "문서/" to null,
            "문서/메모.txt" to words(5000),
            "문서/빈 글.txt" to ByteArray(0),
            "문서/빈 폴더/" to null,
            "사진/" to null,
            "사진/IMG_0001.jpg" to rnd.nextBytes(20_000),
            "사진/빈 그림.png" to ByteArray(0),
            "日本語/" to null,
            "日本語/かな.txt" to "かなカナ\n".repeat(300).toByteArray(),
            "emoji 🎉.txt" to "🎉".toByteArray(),
            "한 바이트.bin" to byteArrayOf(0x42),
        )
        val big = listOf(
            "big-text.txt" to words(4 * 1024 * 1024),
            "big-random.bin" to rnd.nextBytes(2 * 1024 * 1024 + 3),
            "big-video.mp4" to rnd.nextBytes(3 * 1024 * 1024 + 5),
        )
        return listOf(
            Sample("basic", "iroiro-oracle", AesZipWriter.ZIP64_LIMIT, 65_536, basic),
            Sample("big", "big-파일-암호", AesZipWriter.ZIP64_LIMIT, 100_003, big),
            Sample("ko-password", "비밀번호123", AesZipWriter.ZIP64_LIMIT, 65_536, basic.take(4)),
            Sample("long-password", "p@ss-".repeat(25) + "끝", AesZipWriter.ZIP64_LIMIT, 65_536, basic.take(3)),
            Sample("zip64-forced", "zip64", 1, 65_536, basic),
            Sample("zip64-mixed", "zip64", 600, 65_536, basic),
            Sample("tiny-writes", "tiny", AesZipWriter.ZIP64_LIMIT, 3, basic.take(3)),
        )
    }

    @Test
    fun `우리가 잠근 표본을 pyzipper 가 견줄 수 있게 쓴다`() {
        val base = root
        assumeTrue("IROIRO_AESZIP_ORACLE 이 없다 — 건너뛴다", base != null)
        base!!
        val out = File(base, "ours").apply { deleteRecursively(); mkdirs() }
        val plainRoot = File(base, "ours-plain").apply { deleteRecursively(); mkdirs() }
        val manifest = StringBuilder()
        for (s in samples()) {
            val zip = File(out, "${s.name}.zip")
            FileOutputStream(zip).use { fos ->
                AesZipWriter(fos.channel, AesZipKey.of(s.password.toCharArray())!!, s.limit).use { w ->
                    for ((name, data) in s.items) {
                        if (data == null) {
                            w.addDirectory(name, time)
                            continue
                        }
                        w.beginFile(name, time, compress = !stored(name), sizeHint = data.size.toLong())
                        var off = 0
                        while (off < data.size) {
                            val n = minOf(s.chunk, data.size - off)
                            w.write(data, off, n)
                            off += n
                        }
                        w.finishFile()
                    }
                    w.finish()
                }
            }
            val plain = File(plainRoot, s.name)
            for ((name, data) in s.items) {
                val f = File(plain, name)
                if (data == null) f.mkdirs() else f.apply { parentFile.mkdirs() }.writeBytes(data)
            }
            manifest.append(zip.name).append('\t').append(hex(s.password.toByteArray(Charsets.UTF_8)))
                .append('\t').append(s.limit).append('\n')
        }
        File(out, "manifest.tsv").writeText(manifest.toString())
    }

    @Test
    fun `pyzipper 가 잠근 표본을 우리 리더가 푼다`() {
        val base = root
        assumeTrue("IROIRO_AESZIP_ORACLE 이 없다 — 건너뛴다", base != null && File(base, "py/manifest.tsv").isFile)
        base!!
        val py = File(base, "py")
        var archives = 0
        var files = 0
        for (line in File(py, "manifest.tsv").readLines().filter { it.isNotBlank() }) {
            val (zipName, pwHex) = line.split('\t')
            val password = String(unhex(pwHex), Charsets.UTF_8)
            val plain = File(base, "py-plain/${zipName.removeSuffix(".zip")}")
            Archives.open(FileDocumentSource(File(py, zipName)), password = password.toCharArray()).use { r ->
                val expected = plain.walkTopDown().filter { it.isFile }.map { it.relativeTo(plain).invariantSeparatorsPath }.toSet()
                val got = r.entries.filter { !it.isDirectory }.map { it.name }.toSet()
                assertEquals(expected, got, zipName)
                for (e in r.entries) {
                    if (e.isDirectory) continue
                    assertTrue(e.isEncrypted && e.isReadable, "$zipName ${e.name}")
                    val bytes = r.open(e).use { it.readBytes() }
                    assertContentEquals(File(plain, e.name).readBytes(), bytes, "$zipName ${e.name}")
                    files++
                }
                assertTrue(Archives.verifyPassword(r), zipName)
            }
            Archives.open(FileDocumentSource(File(py, zipName)), password = "x$password".toCharArray()).use { r ->
                assertFalse(Archives.verifyPassword(r), "$zipName: 틀린 암호가 맞다고 한다")
            }
            archives++
        }
        assertTrue(archives > 0 && files > 0)

        // 한 비트를 바꾼 표본 — 우리 리더는 읽다가 실패해야 한다(틀린 평문을 끝까지 주지 않는다).
        val tampered = File(py, "tampered.tsv")
        if (tampered.isFile) {
            for (line in tampered.readLines().filter { it.isNotBlank() }) {
                val (zipName, pwHex, entryHex) = line.split('\t')
                val entryName = String(unhex(entryHex), Charsets.UTF_8)
                Archives.open(FileDocumentSource(File(py, zipName)), password = String(unhex(pwHex), Charsets.UTF_8).toCharArray()).use { r ->
                    val e = r.entries.first { it.name == entryName }
                    assertFailsWith<IOException>("$zipName $entryName") { r.open(e).use { it.readBytes() } }
                }
            }
        }
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
