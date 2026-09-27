package io.github.donggi.iroiroviewer.format.archive

import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * 암호가 걸린 ZIP 항목을 푼다.
 *
 * ## 표본은 우리가 잠그지 않았다
 *
 * `src/test/resources/archivecrypt/` 의 표본은 **다른 도구**가 잠갔다 — 전통 방식은 반디집 7.45 의
 * `bz.exe`(한글 암호 표본 포함), WinZip AES 는 pyzipper 0.3(AES-128·256, 압축·저장). 평문은
 * `hello.txt`(글)와 `pattern.bin`(5000 바이트 무늬)이다. 암호는 `iroiro`, 한글 표본은 `비밀번호`.
 * 파이썬 `zipfile`(전통 방식)과 pyzipper(AES)가 자기 표본을 평문과 같게 푸는 것을 만들 때 확인했다.
 *
 * **반디집은 한글 암호를 CP949 로 적는다**(UTF-8 로는 파이썬도 못 푼다). 한글 표본이 그 후보를
 * 지킨다 — UTF-8 만 대 보는 구현은 여기서 실패한다.
 */
class ZipDecryptionTest {

    private val dir = File(javaClass.classLoader!!.getResource("archivecrypt/hello.txt")!!.toURI()).parentFile
    private fun plain(name: String) = File(dir, name).readBytes()

    private fun read(zipName: String, entry: String, password: String): ByteArray =
        ZipFile.builder().setFile(File(dir, zipName)).get().use { zip ->
            val e = zip.getEntry(entry)!!
            zip.getRawInputStream(e).use { raw ->
                ZipDecryption.open(e, raw, ZipDecryption.candidates(password.toCharArray())).use { it.readBytes() }
            }
        }

    @Test
    fun 전통_방식이_풀린다() {
        assertContentEquals(plain("hello.txt"), read("zipcrypto-bandizip.zip", "hello.txt", "iroiro"))
        assertContentEquals(plain("pattern.bin"), read("zipcrypto-bandizip.zip", "pattern.bin", "iroiro"))
    }

    @Test
    fun 반디집의_한글_암호가_풀린다() {
        assertContentEquals(plain("pattern.bin"), read("zipcrypto-bandizip-ko.zip", "pattern.bin", "비밀번호"))
    }

    @Test
    fun WinZip_AES_가_풀린다() {
        for (zip in listOf("aes128-pyzipper.zip", "aes256-pyzipper.zip")) {
            assertContentEquals(plain("hello.txt"), read(zip, "hello.txt", "iroiro"), zip)
            assertContentEquals(plain("pattern.bin"), read(zip, "pattern.bin", "iroiro"), zip)
        }
        assertContentEquals(plain("pattern.bin"), read("aes256-stored-pyzipper.zip", "pattern.bin", "iroiro"))
    }

    @Test
    fun 틀린_암호는_틀렸다고_말한다() {
        for (zip in listOf("zipcrypto-bandizip.zip", "aes128-pyzipper.zip", "aes256-pyzipper.zip")) {
            // 전통 방식은 확인 바이트가 1/256 로 틀린 암호를 통과시킨다. 그 경우도 '평문' 을 주면 안
            // 된다 — 끝의 CRC 가 잡는다. 어느 쪽이든 IOException 이어야 한다.
            assertFailsWith<IOException>(zip) { read(zip, "pattern.bin", "iroiro!") }
        }
        assertFailsWith<ZipDecryption.WrongPasswordException> { read("aes256-pyzipper.zip", "pattern.bin", "x") }
    }

    @Test
    fun PBKDF2_가_RFC_6070_의_값을_낸다() {
        // RFC 6070 의 시험 벡터(PBKDF2-HMAC-SHA1). WinZip AES 의 열쇠가 여기서 나온다.
        fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val p = "password".toByteArray()
        val s = "salt".toByteArray()
        assertContentEquals(hex("0c60c80f961f0e71f3a9b524af6012062fe037a6"), ZipDecryption.pbkdf2(p, s, 1, 20))
        assertContentEquals(hex("ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957"), ZipDecryption.pbkdf2(p, s, 2, 20))
        assertContentEquals(hex("4b007901b765489abead49d926f721d065a429c1"), ZipDecryption.pbkdf2(p, s, 4096, 20))
        assertContentEquals(
            hex("3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038"),
            ZipDecryption.pbkdf2("passwordPASSWORDpassword".toByteArray(), "saltSALTsaltSALTsaltSALTsaltSALTsalt".toByteArray(), 4096, 25),
        )
    }

    @Test
    fun 한글_암호의_후보에_CP949_가_있다() {
        val c = ZipDecryption.candidates("비밀번호".toCharArray())
        // UTF-8 과 CP949. 라틴 문자로는 옮길 수 없으니 ISO-8859-1 은 빠진다.
        kotlin.test.assertEquals(2, c.size)
        assertContentEquals("비밀번호".toByteArray(Charsets.UTF_8), c[0])
    }
}
