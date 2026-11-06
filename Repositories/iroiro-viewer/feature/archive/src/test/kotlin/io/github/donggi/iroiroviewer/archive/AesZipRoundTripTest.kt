package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchivePasswordException
import io.github.donggi.iroiroviewer.format.archive.ArchiveReader
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.format.archive.EntrySink
import io.github.donggi.iroiroviewer.io.AesZipKey
import io.github.donggi.iroiroviewer.io.AesZipWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **우리가 잠근 ZIP 을 우리 리더가 푼다** — 쓰개(`core:io` 의 [AesZipWriter])와 리더(`format:archive` 의 `ZipArchiveReader`·
 * `ZipDecryption`)는 서로를 볼 수 없어(의존 표) 형식을 두 벌로 갖는다. 둘이 같은 형식을 말하는지를 둘 다 보는 이 모듈이 지킨다(풀기가 둘을
 * 잇는 자리다). 압축 목록·풀기가 쓰는 길 그대로 연다(`Archives.open`·`extractSequentially`).
 *
 * 독립 구현으로 푸는 시험은 `core:io` 의 `AesZipWriterTest`, pyzipper 와의 양방향은 [AesZipOracleTest] 다.
 */
class AesZipRoundTripTest {

    private val dir: File = Files.createTempDirectory("aeszip-rt").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** 2026-03-04 05:06:08 UTC — 확장 시각(초)이 그대로 돌아와야 한다. */
    private val time = 1_772_600_768_000L

    private class Item(val name: String, val data: ByteArray?, val compress: Boolean = true) {
        val isDirectory: Boolean get() = data == null
    }

    private val rnd = Random(20261016)

    /** 여러 MB — 줄지 않는 것과 줄어드는 것을 섞는다(리더의 압축비 상한 100:1 에 걸리지 않게). */
    private val big = ByteArray(3 * 1024 * 1024 + 77).also { b ->
        rnd.nextBytes(b)
        for (i in b.indices step 2) b[i] = ('a'.code + i % 7).toByte()
    }

    private val items = listOf(
        Item("문서/", null),
        Item("문서/빈 폴더/", null),
        Item("문서/메모.txt", "첫 줄\n둘째 줄\n".repeat(500).toByteArray()),
        Item("문서/빈 글.txt", ByteArray(0)),
        Item("사진/", null),
        Item("사진/IMG_0001.jpg", rnd.nextBytes(20_000), compress = false),
        Item("사진/빈 그림.png", ByteArray(0), compress = false),
        Item("日本語 ファイル.txt", "かなカナ漢字\n".repeat(200).toByteArray()),
        Item("큰 파일.bin", big),
        Item("큰 영상.mp4", big, compress = false),
    )

    private fun make(password: String, limit: Long = AesZipWriter.ZIP64_LIMIT, list: List<Item> = items): File {
        val file = File(dir, "z-${System.nanoTime()}.zip")
        FileOutputStream(file).use { out ->
            AesZipWriter(out.channel, AesZipKey.of(password.toCharArray())!!, limit).use { w ->
                for (it in list) {
                    if (it.isDirectory) {
                        w.addDirectory(it.name, time)
                        continue
                    }
                    val data = it.data!!
                    w.beginFile(it.name, time, it.compress, data.size.toLong())
                    var off = 0
                    while (off < data.size) {
                        val n = minOf(65_536, data.size - off)
                        w.write(data, off, n)
                        off += n
                    }
                    w.finishFile()
                }
                w.finish()
            }
        }
        return file
    }

    private fun open(file: File, password: String?): ArchiveReader =
        Archives.open(FileDocumentSource(file), password = password?.toCharArray())

    private fun read(r: ArchiveReader, e: ArchiveEntry): ByteArray = r.open(e).use { it.readBytes() }

    @Test
    fun `우리가 잠근 ZIP 을 우리 리더가 푼다 — 이름·폴더·시각·크기·내용`() {
        val zip = make(PASSWORD)
        open(zip, PASSWORD).use { r ->
            assertEquals(items.map { it.name }, r.entries.map { it.name })
            for ((item, e) in items.zip(r.entries)) {
                assertEquals(item.isDirectory, e.isDirectory, item.name)
                // 폴더는 잠그지 않는다. 파일은 잠겼고, 암호를 받았으니 읽힌다.
                assertEquals(!item.isDirectory, e.isEncrypted, item.name)
                assertEquals(item.name.trimEnd('/'), e.safeName, item.name)
                assertEquals(time, e.lastModified, "${item.name}: 확장 시각(UTC 초)이 돌아와야 한다")
                if (item.isDirectory) continue
                assertTrue(e.isReadable, item.name)
                assertEquals(item.data!!.size.toLong(), e.declaredSize, item.name)
                assertContentEquals(item.data, read(r, e), item.name)
            }
            assertTrue(Archives.verifyPassword(r))
        }
    }

    @Test
    fun `순차 풀기도 푼다 — 풀기 화면이 쓰는 길`() {
        val zip = make(PASSWORD)
        val got = HashMap<String, ByteArray>()
        open(zip, PASSWORD).use { r ->
            r.extractSequentially(object : EntrySink {
                val bufs = HashMap<Int, ByteArrayOutputStream>()
                override fun begin(entry: ArchiveEntry): OutputStream? =
                    if (entry.isReadable) ByteArrayOutputStream().also { bufs[entry.index] = it } else null

                override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
                    assertNull(failure, entry.name)
                    got[entry.name] = bufs.getValue(entry.index).toByteArray()
                }
            })
        }
        for (item in items.filterNot { it.isDirectory }) assertContentEquals(item.data, got[item.name], item.name)
    }

    @Test
    fun `암호 없이 열면 자물쇠, 틀린 암호는 틀렸다고 말한다`() {
        val zip = make(PASSWORD)
        open(zip, null).use { r ->
            for (e in r.entries.filterNot { it.isDirectory }) {
                assertTrue(e.isEncrypted && e.needsPassword && !e.isReadable, e.name)
            }
        }
        open(zip, "$PASSWORD!").use { r ->
            assertFalse(Archives.verifyPassword(r))
            val e = r.entries.first { it.name == "문서/메모.txt" }
            val failure = assertFailsWith<ArchivePasswordException> { read(r, e) }
            assertTrue(failure.wrongPassword)
        }
    }

    @Test
    fun `암호문이 한 바이트 바뀌면 리더가 잡는다 — 틀린 평문을 주지 않는다`() {
        val photo = rnd.nextBytes(10_000)
        val text = "바뀌면 안 되는 글\n".repeat(400).toByteArray()
        val zip = make(PASSWORD, list = listOf(Item("a.jpg", photo, compress = false), Item("b.txt", text)))
        val bytes = zip.readBytes()
        // 각 항목의 암호문 한가운데 — 로컬 머리(30 + 이름 + 추가 필드) 뒤의 솔트 16 · 확인값 2 를 지나서.
        fun dataAt(offset: Int): Int {
            val nameLen = (bytes[offset + 26].toInt() and 0xFF) or ((bytes[offset + 27].toInt() and 0xFF) shl 8)
            val extraLen = (bytes[offset + 28].toInt() and 0xFF) or ((bytes[offset + 29].toInt() and 0xFF) shl 8)
            return offset + 30 + nameLen + extraLen + 18
        }
        val first = dataAt(0)
        bytes[first + 5000] = (bytes[first + 5000].toInt() xor 0x40).toByte()
        val secondHeader = indexOf(bytes, byteArrayOf(0x50, 0x4B, 0x03, 0x04), from = first)
        val second = dataAt(secondHeader)
        bytes[second + 3] = (bytes[second + 3].toInt() xor 0x01).toByte()
        val tampered = File(dir, "tampered.zip").apply { writeBytes(bytes) }
        open(tampered, PASSWORD).use { r ->
            for (e in r.entries) assertFailsWith<IOException>(e.name) { read(r, e) }
        }
    }

    /**
     * **해제기가 먼저 멈춰도 인증값을 본다.** CTR 은 암호문의 비트를 골라 뒤집으면 평문의 같은 비트가 뒤집힌다 — deflate 첫 블록의 '마지막
     * 블록' 비트(첫 바이트의 맨 아래 비트)를 켜면 해제기는 첫 블록에서 끝난다. 그때 남은 암호문을 읽지 않으면 인증값을 보지 않은 채 잘린 평문이
     * '정상의 끝' 으로 나간다(AE-2 에는 CRC 도 없다). 암호문이 해제기의 한 번 채우기(64 KiB)보다 커야 그 길을 탄다.
     */
    @Test
    fun `deflate 의 마지막 블록 비트를 켜 뒤를 잘라 내도 리더가 잡는다`() {
        val letters = ByteArray(1_500_000) { ('a'.code + rnd.nextInt(26)).toByte() }
        val zip = make(PASSWORD, list = listOf(Item("letters.txt", letters)))
        val bytes = zip.readBytes()
        val nameLen = (bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)
        val extraLen = (bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)
        val first = 30 + nameLen + extraLen + 18
        bytes[first] = (bytes[first].toInt() xor 0x01).toByte()
        val tampered = File(dir, "bfinal.zip").apply { writeBytes(bytes) }
        open(tampered, PASSWORD).use { r ->
            val e = r.entries.single()
            assertTrue(e.compressedSize > 256 * 1024, "표본이 작으면 이 길을 타지 않는다: ${e.compressedSize}")
            assertFailsWith<IOException> { read(r, e) }
        }
    }

    @Test
    fun `ZIP64 칸을 써도 우리 리더가 푼다`() {
        for (limit in listOf(1L, 5_000L)) {
            val zip = make(PASSWORD, limit = limit)
            open(zip, PASSWORD).use { r ->
                assertEquals(items.map { it.name }, r.entries.map { it.name }, "limit=$limit")
                for ((item, e) in items.zip(r.entries)) {
                    if (item.isDirectory) continue
                    assertEquals(item.data!!.size.toLong(), e.declaredSize, "limit=$limit ${item.name}")
                    assertContentEquals(item.data, read(r, e), "limit=$limit ${item.name}")
                }
            }
        }
    }

    @Test
    fun `한글 암호 — 리더의 첫 후보(UTF-8)로 풀린다`() {
        val zip = make("비밀번호 123", list = items.take(4))
        open(zip, "비밀번호 123").use { r ->
            assertContentEquals(items[2].data, read(r, r.entries[2]))
            assertTrue(Archives.verifyPassword(r))
        }
    }

    private fun indexOf(b: ByteArray, sig: ByteArray, from: Int): Int {
        var i = from
        while (i <= b.size - sig.size) {
            if (sig.indices.all { b[i + it] == sig[it] }) return i
            i++
        }
        error("찾지 못했다")
    }

    private companion object {
        const val PASSWORD = "iroiro AES 암호"
    }
}
