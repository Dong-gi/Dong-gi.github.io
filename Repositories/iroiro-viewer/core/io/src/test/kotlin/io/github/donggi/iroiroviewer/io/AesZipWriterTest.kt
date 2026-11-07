package io.github.donggi.iroiroviewer.io

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WinZip AES 로 잠근 ZIP 쓰기([AesZipWriter]·[AesZipCrypto]).
 *
 * ## 오라클은 우리 코드가 아니다
 *
 * 이 시험의 읽개([MiniZip])는 쓰개와 **다른 길**로 푼다 — 열쇠는 JCE 의 `PBKDF2WithHmacSHA1`(SunJCE 는 암호 글자를 UTF-8 로 옮긴다),
 * 계수기는 `BigInteger` 를 리틀 엔디언으로 늘어놓아 블록마다 따로 암호화, 압축은 `java.util.zip.Inflater`. 머리 칸은 명세의 바이트 자리를
 * 그대로 읽는다. 이 앱의 리더(`format:archive`)로 읽는 왕복은 `feature:archive` 의 `AesZipRoundTripTest`, 파이썬 pyzipper 로 읽는 것은
 * 손으로 확인했다.
 */
class AesZipWriterTest {

    private val dir: File = Files.createTempDirectory("aeszip").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** 2026-01-02 03:04:06 UTC(초가 짝수 — DOS 시각은 2초 단위다). */
    private val time = 1_767_323_046_000L

    private fun zip(
        password: String = PASSWORD,
        limit: Long = AesZipWriter.ZIP64_LIMIT,
        build: AesZipWriter.() -> Unit,
    ): ByteArray {
        val file = File(dir, "t-${System.nanoTime()}.zip")
        FileOutputStream(file).use { out ->
            val key = assertNotNull(AesZipKey.of(password.toCharArray()))
            AesZipWriter(out.channel, key, limit).use { w ->
                w.build()
                w.finish()
            }
        }
        return file.readBytes()
    }

    /** 파일 항목 하나를 [chunk] 바이트씩 넣는다 — 버퍼·열쇠 흐름의 경계를 일부러 어긋나게 넘는다. */
    private fun AesZipWriter.file(
        name: String,
        data: ByteArray,
        compress: Boolean,
        modified: Long = time,
        hint: Long = data.size.toLong(),
        chunk: Int = 7001,
    ) {
        beginFile(name, modified, compress, hint)
        var off = 0
        while (off < data.size) {
            val n = minOf(chunk, data.size - off)
            write(data, off, n)
            off += n
        }
        finishFile()
    }

    // ---- 머리 칸 -------------------------------------------------------------------

    @Test
    fun `머리 칸이 명세대로다 — 방식 99, 비트 0과 11, 판 51, CRC 0, AE-2 추가 필드`() {
        val text = "가나다 hello\n".repeat(300).toByteArray()
        val photo = Random(3).nextBytes(5000)
        val bytes = zip {
            addDirectory("폴더/", time)
            file("폴더/글.txt", text, compress = true)
            file("사진.jpg", photo, compress = false)
        }
        val z = MiniZip(bytes)
        assertEquals(listOf("폴더/", "폴더/글.txt", "사진.jpg"), z.central.map { it.nameText })

        for (c in z.central) {
            val l = z.local(c)
            // 로컬 머리와 가운데 목록이 같은 것을 말한다(7-Zip 은 어긋나면 머리 오류로 본다).
            assertEquals(c.flags, l.flags, c.nameText)
            assertEquals(c.method, l.method, c.nameText)
            assertEquals(c.versionNeeded, l.versionNeeded, c.nameText)
            assertEquals(c.dos, l.dos, c.nameText)
            assertEquals(c.crc, l.crc, c.nameText)
            assertEquals(c.compressedSize, l.compressedSize, c.nameText)
            assertEquals(c.size, l.size, c.nameText)
            assertContentEquals(c.name, l.name)
            // 데이터 기술자를 쓰지 않는다 — 머리를 고쳐 적는다.
            assertEquals(0, c.flags and 0x0008, c.nameText)
            // 이름은 UTF-8 이고 그 표시가 켜져 있다.
            assertTrue(c.flags and 0x0800 != 0, c.nameText)
            // 확장 시각(UTC 초)이 양쪽에 있다.
            assertEquals(time / 1000, u32(c.extras.getValue(0x5455), 1), c.nameText)
            assertEquals(time / 1000, u32(l.extras.getValue(0x5455), 1), c.nameText)
            assertEquals(AesZipWriter.dosTime(time), c.dos)
        }

        val folder = z.central[0]
        assertEquals(0, folder.method)
        assertEquals(0x0800, folder.flags, "폴더는 잠그지 않는다")
        assertFalse(0x9901 in folder.extras)
        assertEquals(0L, folder.size)
        assertEquals(20, folder.versionNeeded)

        for ((c, actual, plain) in listOf(Triple(z.central[1], 8, text), Triple(z.central[2], 0, photo))) {
            assertEquals(99, c.method, "압축 방식 칸은 99")
            assertEquals(0x0801, c.flags, "비트 0(암호)과 11(UTF-8)")
            assertEquals(51, c.versionNeeded)
            assertEquals(0L, c.crc, "AE-2 는 CRC 를 0 으로 둔다")
            assertEquals(plain.size.toLong(), c.size)
            // 판 2 · 'AE' · 세기 3 · 실제 방식 — 로컬 머리와 가운데 목록 양쪽.
            val expected = byteArrayOf(2, 0, 'A'.code.toByte(), 'E'.code.toByte(), 3, actual.toByte(), 0)
            assertContentEquals(expected, c.extras.getValue(0x9901))
            assertContentEquals(expected, z.local(c).extras.getValue(0x9901))
        }
        // '저장' 항목의 압축 크기는 정확히 솔트 16 + 확인값 2 + 평문 + 인증값 10 이다.
        assertEquals(photo.size + 28L, z.central[2].compressedSize)
        // 글은 줄어든다.
        assertTrue(z.central[1].compressedSize < text.size / 4)

        // 끝 레코드 — 항목 수, 가운데 목록의 자리. ZIP64 는 쓰지 않았다.
        assertEquals(3, z.eocdCount)
        assertFalse(z.zip64Eocd)
    }

    // ---- 독립 구현이 푼다 -------------------------------------------------------------------

    @Test
    fun `독립 구현이 푼다 — 저장·압축, 빈 파일, 한글 이름, 여러 MB`() {
        val rnd = Random(11)
        val big = ByteArray(3 * 1024 * 1024 + 123).also { b ->
            // 줄지 않는 것과 줄어드는 것을 섞는다.
            rnd.nextBytes(b)
            for (i in 0 until b.size step 3) b[i] = 'a'.code.toByte()
        }
        val files = linkedMapOf(
            "빈 글.txt" to (ByteArray(0) to true),
            "빈 그림.png" to (ByteArray(0) to false),
            "한 글자.txt" to ("가".toByteArray() to true),
            "日本語/テキスト.md" to ("かな\n".repeat(1000).toByteArray() to true),
            "큰 파일.bin" to (big to true),
            "큰 영상.mp4" to (big to false),
        )
        val bytes = zip {
            addDirectory("日本語/", time)
            for ((name, v) in files) file(name, v.first, v.second)
        }
        val z = MiniZip(bytes)
        for ((name, v) in files) {
            val c = z.central.first { it.nameText == name }
            assertContentEquals(v.first, z.decrypt(c, PASSWORD), name)
        }
    }

    @Test
    fun `한 바이트씩 넣어도 같은 평문이 나온다`() {
        val data = Random(5).nextBytes(4500)
        val bytes = zip {
            file("a.bin", data, compress = false, chunk = 1)
            file("b.txt", data, compress = true, chunk = 13)
        }
        val z = MiniZip(bytes)
        assertContentEquals(data, z.decrypt(z.central[0], PASSWORD))
        assertContentEquals(data, z.decrypt(z.central[1], PASSWORD))
    }

    @Test
    fun `한글 암호와 긴 암호 — 바이트는 UTF-8 이다`() {
        val data = "비밀".toByteArray()
        for (pw in listOf("비밀번호", "p".repeat(100), "pässwörd ✓ 🔐")) {
            val z = MiniZip(zip(password = pw) { file("x.txt", data, compress = true) })
            assertContentEquals(data, z.decrypt(z.central[0], pw), pw)
        }
        assertContentEquals("비밀번호".toByteArray(Charsets.UTF_8), AesZipCrypto.utf8("비밀번호".toCharArray()))
    }

    // ---- 틀린 암호·바뀐 암호문 -------------------------------------------------------------------

    @Test
    fun `틀린 암호는 확인값에서 걸린다`() {
        val z = MiniZip(zip { file("a.txt", "hello".toByteArray(), compress = true) })
        assertFailsWith<WrongPassword> { z.decrypt(z.central[0], "Hello") }
        assertFailsWith<WrongPassword> { z.decrypt(z.central[0], PASSWORD + " ") }
    }

    @Test
    fun `암호문을 한 바이트 바꾸면 인증값이 잡는다`() {
        val data = Random(9).nextBytes(3000)
        val bytes = zip { file("a.bin", data, compress = false) }
        val z = MiniZip(bytes)
        val c = z.central[0]
        // 솔트·확인값 뒤, 암호문 한가운데의 한 바이트.
        val at = z.local(c).dataStart + 18 + 1500
        bytes[at] = (bytes[at].toInt() xor 0x01).toByte()
        val e = assertFailsWith<IllegalStateException> { MiniZip(bytes).decrypt(c, PASSWORD) }
        assertTrue("HMAC" in (e.message ?: ""), e.message)
    }

    // ---- 셈 -------------------------------------------------------------------

    @Test
    fun `계수기는 1 부터의 리틀 엔디언이다 — 독립 구현과 같고 자바의 CTR 과 다르다`() {
        val key = Random(1).nextBytes(32)
        val n = 300 * 16 + 5 // 블록 255→256(둘째 바이트로 올림)을 넘는다
        val ours = ByteArray(n)
        AesCtrHmac().apply { start(key.copyOf(), ByteArray(32)) }.encrypt(ours, 0, n) // 0 을 암호화하면 열쇠 흐름이다
        val ecb = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        val independent = ByteArray(n)
        for (b in 0 until (n + 15) / 16) {
            val block = ecb.doFinal(le16(BigInteger.valueOf(b + 1L)))
            for (i in 0 until 16) if (b * 16 + i < n) independent[b * 16 + i] = block[i]
        }
        assertContentEquals(independent, ours)

        // 자바의 'AES/CTR' 은 빅 엔디언으로 올린다 — 첫 블록만 같고 둘째부터 다르다(그것을 쓰면 다른 도구에서 깨진다).
        val java = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(le16(BigInteger.ONE)))
        }.doFinal(ByteArray(32))
        assertContentEquals(ours.copyOfRange(0, 16), java.copyOfRange(0, 16))
        assertFalse(ours.copyOfRange(16, 32).contentEquals(java.copyOfRange(16, 32)))

        // 큰 번호의 블록 — 앞 8바이트에 리틀 엔디언, 뒤 8바이트는 0.
        for (number in listOf(1L, 255L, 256L, 65_536L, 1L shl 32, (1L shl 40) + 7)) {
            val got = ByteArray(16)
            AesZipCrypto.counterBlock(number, got, 0)
            assertContentEquals(le16(BigInteger.valueOf(number)), got, "블록 $number")
        }
    }

    @Test
    fun `PBKDF2 가 RFC 6070 의 값을 낸다`() {
        fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        fun prf(p: String) = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(p.toByteArray(), "HmacSHA1")) }
        val salt = "salt".toByteArray()
        assertContentEquals(hex("0c60c80f961f0e71f3a9b524af6012062fe037a6"), AesZipCrypto.pbkdf2(prf("password"), salt, 1, 20))
        assertContentEquals(hex("ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957"), AesZipCrypto.pbkdf2(prf("password"), salt, 2, 20))
        assertContentEquals(hex("4b007901b765489abead49d926f721d065a429c1"), AesZipCrypto.pbkdf2(prf("password"), salt, 4096, 20))
        assertContentEquals(
            hex("3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038"),
            AesZipCrypto.pbkdf2(prf("passwordPASSWORDpassword"), "saltSALTsaltSALTsaltSALTsaltSALTsalt".toByteArray(), 4096, 25),
        )
    }

    @Test
    fun `항목마다 솔트가 달라 같은 내용도 다른 암호문이다`() {
        val data = "같은 내용".repeat(100).toByteArray()
        val z = MiniZip(zip {
            file("a.txt", data, compress = false)
            file("b.txt", data, compress = false)
        })
        val a = z.local(z.central[0])
        val b = z.local(z.central[1])
        val saltA = z.bytes.copyOfRange(a.dataStart, a.dataStart + 16)
        val saltB = z.bytes.copyOfRange(b.dataStart, b.dataStart + 16)
        assertFalse(saltA.contentEquals(saltB), "솔트가 같다 — 열쇠 흐름이 겹친다")
        val cipherA = z.bytes.copyOfRange(a.dataStart + 18, a.dataStart + 18 + data.size)
        val cipherB = z.bytes.copyOfRange(b.dataStart + 18, b.dataStart + 18 + data.size)
        assertFalse(cipherA.contentEquals(cipherB))
    }

    // ---- ZIP64 -------------------------------------------------------------------

    @Test
    fun `ZIP64 — 넘친 크기·자리를 ZIP64 칸에 적고 끝 레코드를 더한다`() {
        val text = "zip64 ".repeat(500).toByteArray()
        val photo = Random(4).nextBytes(700)
        // 32비트의 끝을 작게 줘서 4 GiB 를 만들지 않고 ZIP64 길을 탄다.
        for (limit in listOf(1L, 600L)) {
            val bytes = zip(limit = limit) {
                addDirectory("d/", time)
                file("d/t.txt", text, compress = true)
                file("p.jpg", photo, compress = false)
                file("e.txt", ByteArray(0), compress = true)
                addDirectory("뒤/", time)
            }
            val z = MiniZip(bytes)
            assertTrue(z.zip64Eocd, "limit=$limit: ZIP64 끝 레코드가 없다")
            assertEquals(5L, z.zip64Count)
            assertEquals(listOf("d/", "d/t.txt", "p.jpg", "e.txt", "뒤/"), z.central.map { it.nameText })
            for (c in z.central) {
                val l = z.local(c)
                assertEquals(c.size, l.size, "limit=$limit ${c.nameText}")
                assertEquals(c.compressedSize, l.compressedSize, "limit=$limit ${c.nameText}")
                // 넘친 칸은 0xFFFFFFFF 이고 진짜 값은 ZIP64 칸에 있다(MiniZip 이 풀어 읽었다).
                if (c.offset >= limit) assertEquals(0xFFFFFFFFL, c.rawOffset, "limit=$limit ${c.nameText}")
                if (c.compressedSize >= limit) assertEquals(0xFFFFFFFFL, c.rawCompressedSize, "limit=$limit ${c.nameText}")
                if (c.method == 99) assertTrue(c.versionNeeded >= 45)
            }
            assertContentEquals(text, z.decrypt(z.central[1], PASSWORD), "limit=$limit")
            assertContentEquals(photo, z.decrypt(z.central[2], PASSWORD), "limit=$limit")
            assertContentEquals(ByteArray(0), z.decrypt(z.central[3], PASSWORD), "limit=$limit")
        }
    }

    @Test
    fun `머리에 자리를 잡지 않은 항목이 넘치면 쓰지 않고 던진다`() {
        val file = File(dir, "over.zip")
        FileOutputStream(file).use { out ->
            AesZipWriter(out.channel, AesZipKey.of(PASSWORD.toCharArray())!!, zip64Limit = 2000).use { w ->
                // 훑을 때는 비어 있던 파일이 읽는 사이 자랐다.
                w.beginFile("grew.bin", time, compress = false, sizeHint = 0)
                val data = Random(8).nextBytes(3000)
                w.write(data, 0, data.size)
                assertFailsWith<AesZipWriter.SizeOverflow> { w.finishFile() }
            }
        }
    }

    // ---- 암호의 바이트 -------------------------------------------------------------------

    @Test
    fun `빈 암호와 옮길 수 없는 암호는 열쇠가 되지 않는다`() {
        assertNull(AesZipKey.of(CharArray(0)))
        // 짝 없는 서로게이트 — UTF-8 로 옮길 수 없다. 화면도 같은 판정으로 막는다.
        val lone = charArrayOf('a', '\uD83D', 'b')
        assertNull(AesZipKey.of(lone))
        assertNull(AesZipCrypto.utf8(lone))
        assertFalse(AesZipCrypto.isEncodable(String(lone)))
        assertFalse(AesZipCrypto.isEncodable("\uDC00"))
        assertTrue(AesZipCrypto.isEncodable("🔐 비밀"))
        // 받은 배열은 건드리지 않는다(지우는 것은 넘긴 쪽이다).
        val pw = "abc".toCharArray()
        assertNotNull(AesZipKey.of(pw))
        assertEquals("abc", String(pw))
    }

    @Test
    fun `영문·숫자·기호만이면 ASCII 다`() {
        assertTrue(AesZipCrypto.isAscii("Abc123!@# ~"))
        assertFalse(AesZipCrypto.isAscii("비밀"))
        assertFalse(AesZipCrypto.isAscii("café"))
    }

    // ---- 시각 -------------------------------------------------------------------

    @Test
    fun `DOS 시각 — 2초 단위, 1980년 앞과 2107년 뒤는 끝에 묶는다`() {
        val utc = ZoneOffset.UTC
        // 2026-01-02 03:04:06
        val dos = AesZipWriter.dosTime(time, utc)
        assertEquals(((2026 - 1980) shl 9) or (1 shl 5) or 2, dos ushr 16)
        assertEquals((3 shl 11) or (4 shl 5) or 3, dos and 0xFFFF)
        // 홀수 초는 내린다.
        assertEquals(dos, AesZipWriter.dosTime(time + 1000, utc))
        assertEquals((1 shl 21) or (1 shl 16), AesZipWriter.dosTime(0, utc))
        val far = AesZipWriter.dosTime(9_999_999_999_000L, utc) // 2286년
        assertEquals((127 shl 9) or (12 shl 5) or 31, far ushr 16)
        assertEquals((23 shl 11) or (59 shl 5) or 29, far and 0xFFFF)
    }

    @Test
    fun `시각을 모르면 확장 시각을 지어내지 않는다`() {
        val z = MiniZip(zip { file("a.txt", "a".toByteArray(), compress = true, modified = 0) })
        assertFalse(0x5455 in z.central[0].extras)
        assertFalse(0x5455 in z.local(z.central[0]).extras)
    }

    // ---- 독립 읽개 -------------------------------------------------------------------

    private class WrongPassword : Exception()

    private class Entry(
        val versionNeeded: Int,
        val flags: Int,
        val method: Int,
        val dos: Int,
        val crc: Long,
        val compressedSize: Long,
        val size: Long,
        val name: ByteArray,
        val extras: Map<Int, ByteArray>,
        val offset: Long,
        val rawCompressedSize: Long,
        val rawOffset: Long,
        val dataStart: Int,
    ) {
        val nameText: String get() = String(name, Charsets.UTF_8)
    }

    /**
     * 명세의 바이트 자리를 그대로 읽는 작은 읽개 — 쓰개와 코드를 나누지 않는다. ZIP64(가운데 목록의 넘친 칸, 로컬 머리의 두 크기, 끝 레코드)를
     * 따른다.
     */
    private class MiniZip(val bytes: ByteArray) {
        val central: List<Entry>
        val eocdCount: Int
        var zip64Eocd = false
        var zip64Count = -1L

        init {
            var eocd = bytes.size - 22
            while (eocd >= 0 && u32(bytes, eocd) != 0x06054B50L) eocd--
            check(eocd >= 0) { "끝 레코드가 없다" }
            eocdCount = u16(bytes, eocd + 10)
            var cdSize = u32(bytes, eocd + 12)
            var cdOffset = u32(bytes, eocd + 16)
            val locator = eocd - 20
            if (locator >= 0 && u32(bytes, locator) == 0x07064B50L) {
                zip64Eocd = true
                val at = u64(bytes, locator + 8).toInt()
                check(u32(bytes, at) == 0x06064B50L) { "ZIP64 끝 레코드의 서명" }
                check(at + 56 == locator) { "ZIP64 끝 레코드와 위치표가 붙어 있어야 한다" }
                check(u64(bytes, at + 4) == 44L)
                zip64Count = u64(bytes, at + 32)
                check(u64(bytes, at + 24) == zip64Count)
                cdSize = u64(bytes, at + 40)
                cdOffset = u64(bytes, at + 48)
            }
            val list = ArrayList<Entry>()
            var p = cdOffset.toInt()
            while (p < cdOffset + cdSize) {
                check(u32(bytes, p) == 0x02014B50L) { "가운데 목록의 서명" }
                val nameLen = u16(bytes, p + 28)
                val extraLen = u16(bytes, p + 30)
                val commentLen = u16(bytes, p + 32)
                val name = bytes.copyOfRange(p + 46, p + 46 + nameLen)
                val extras = extras(bytes, p + 46 + nameLen, extraLen)
                val rawC = u32(bytes, p + 20)
                val rawU = u32(bytes, p + 24)
                val rawO = u32(bytes, p + 42)
                // 넘친 칸만 차례대로(평문 크기, 압축 크기, 자리) ZIP64 칸에 있다.
                val z = extras[0x0001]
                var zi = 0
                fun next(): Long = u64(z!!, zi).also { zi += 8 }
                val size = if (rawU == 0xFFFFFFFFL) next() else rawU
                val compressed = if (rawC == 0xFFFFFFFFL) next() else rawC
                val offset = if (rawO == 0xFFFFFFFFL) next() else rawO
                if (z != null) check(zi == z.size) { "ZIP64 칸에 남는 값이 있다" }
                list += Entry(
                    u16(bytes, p + 6), u16(bytes, p + 8), u16(bytes, p + 10), u32(bytes, p + 12).toInt(), u32(bytes, p + 16),
                    compressed, size, name, extras, offset, rawC, rawO, -1,
                )
                p += 46 + nameLen + extraLen + commentLen
            }
            central = list
            // 빈틈이 없다 — 항목의 데이터는 압축 크기만큼이고 바로 뒤가 다음 머리(마지막은 가운데 목록)다. 인증값을 적힌 것보다 길게 쓰면
            // 가운데 목록으로 읽는 리더는 모른 채 지나가므로 여기서 잡는다.
            val starts = central.map { it.offset } + cdOffset
            for ((i, c) in central.sortedBy { it.offset }.withIndex()) {
                check(local(c).dataStart + c.compressedSize == starts.sorted()[i + 1]) { "${c.nameText} 뒤에 빈틈이 있다" }
            }
        }

        fun local(c: Entry): Entry {
            val p = c.offset.toInt()
            check(u32(bytes, p) == 0x04034B50L) { "로컬 머리의 서명" }
            val nameLen = u16(bytes, p + 26)
            val extraLen = u16(bytes, p + 28)
            val extras = extras(bytes, p + 30 + nameLen, extraLen)
            val rawC = u32(bytes, p + 18)
            val rawU = u32(bytes, p + 22)
            var size = rawU
            var compressed = rawC
            if (rawU == 0xFFFFFFFFL || rawC == 0xFFFFFFFFL) {
                // 로컬 머리의 ZIP64 칸에는 두 크기가 다 있어야 한다(APPNOTE 4.5.3).
                val z = extras.getValue(0x0001)
                check(z.size == 16) { "로컬 ZIP64 칸은 16바이트" }
                size = u64(z, 0)
                compressed = u64(z, 8)
            }
            return Entry(
                u16(bytes, p + 4), u16(bytes, p + 6), u16(bytes, p + 8), u32(bytes, p + 10).toInt(), u32(bytes, p + 14),
                compressed, size, bytes.copyOfRange(p + 30, p + 30 + nameLen), extras, c.offset, rawC, c.offset,
                p + 30 + nameLen + extraLen,
            )
        }

        /** 독립 구현으로 푼다. 확인값이 틀리면 [WrongPassword], 인증값이 틀리면 'HMAC' 을 말하는 [IllegalStateException]. */
        fun decrypt(c: Entry, password: String): ByteArray {
            check(c.method == 99)
            val aes = c.extras.getValue(0x9901)
            check(u16(aes, 0) == 2 && aes[2] == 'A'.code.toByte() && aes[3] == 'E'.code.toByte() && aes[4] == 3.toByte())
            val actual = u16(aes, 5)
            val l = local(c)
            var p = l.dataStart
            val salt = bytes.copyOfRange(p, p + 16)
            p += 16
            val verifier = bytes.copyOfRange(p, p + 2)
            p += 2
            val n = (c.compressedSize - 28).toInt()
            val cipher = bytes.copyOfRange(p, p + n)
            p += n
            val auth = bytes.copyOfRange(p, p + 10)
            val km = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(PBEKeySpec(password.toCharArray(), salt, 1000, 66 * 8)).encoded
            if (!verifier.contentEquals(km.copyOfRange(64, 66))) throw WrongPassword()
            val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(km.copyOfRange(32, 64), "HmacSHA1")) }
            check(mac.doFinal(cipher).copyOf(10).contentEquals(auth)) { "HMAC 이 맞지 않다" }
            val ecb = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(km.copyOfRange(0, 32), "AES")) }
            val plain = ByteArray(n)
            for (b in 0 until (n + 15) / 16) {
                val ks = ecb.doFinal(le16(BigInteger.valueOf(b + 1L)))
                for (i in 0 until 16) {
                    val at = b * 16 + i
                    if (at < n) plain[at] = (cipher[at].toInt() xor ks[i].toInt()).toByte()
                }
            }
            return when (actual) {
                0 -> plain
                8 -> inflate(plain)
                else -> error("방식 $actual")
            }
        }
    }

    private companion object {
        const val PASSWORD = "iroiro-시험"

        fun le16(n: BigInteger): ByteArray {
            val be = n.toByteArray()
            val out = ByteArray(16)
            for (i in be.indices) {
                val at = be.size - 1 - i
                if (i < 16) out[i] = be[at]
            }
            return out
        }

        fun inflate(data: ByteArray): ByteArray {
            val inf = Inflater(true)
            try {
                inf.setInput(data)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (!inf.finished()) {
                    val k = inf.inflate(buf)
                    if (k == 0 && !inf.finished() && (inf.needsInput() || inf.needsDictionary())) error("deflate 흐름이 끝나지 않았다")
                    out.write(buf, 0, k)
                }
                check(inf.remaining == 0) { "deflate 뒤에 남는 바이트가 있다" }
                return out.toByteArray()
            } finally {
                inf.end()
            }
        }

        fun extras(b: ByteArray, at: Int, len: Int): Map<Int, ByteArray> {
            val map = LinkedHashMap<Int, ByteArray>()
            var p = at
            while (p < at + len) {
                val id = u16(b, p)
                val size = u16(b, p + 2)
                check(id !in map) { "추가 필드 $id 가 두 번" }
                map[id] = b.copyOfRange(p + 4, p + 4 + size)
                p += 4 + size
            }
            check(p == at + len) { "추가 필드의 길이가 맞지 않다" }
            return map
        }

        fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

        fun u32(b: ByteArray, at: Int): Long = (u16(b, at).toLong()) or (u16(b, at + 2).toLong() shl 16)

        fun u64(b: ByteArray, at: Int): Long = u32(b, at) or (u32(b, at + 4) shl 32)
    }
}
