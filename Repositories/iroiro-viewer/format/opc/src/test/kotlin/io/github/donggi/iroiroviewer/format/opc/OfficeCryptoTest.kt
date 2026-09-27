package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * MS-OFFCRYPTO 의 계산 조각 — **우리가 만들지 않은 값**과 견준다.
 *
 * * 알려진 답(KAT)은 msoffcrypto-tool 6.0.0 의 docstring 에 적힌 값이다. 그 값들은 그 도구의 시험 입력(오피스가
 *   만든 `example_password.docx`·`ecma376standard_password.docx`)에서 나왔다 — 암호 `Password1234_`.
 *   생성 스크립트(`kat.py`, 저장소 밖)가 msoffcrypto 로 다시 돌려 맞는지 확인한 뒤 16진수로 옮겼다.
 * * 반복 해시는 Python `hashlib` 로 셈한 값이다.
 */
class OfficeCryptoTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun pw(s: String) = OfficeCrypto.utf16le(s.toCharArray())

    private val noCancel: () -> Unit = {}

    @Test
    fun 암호는_UTF16LE_로_바꾼다() {
        val s = "암호 pw😀"
        assertContentEquals(s.toByteArray(Charsets.UTF_16LE), OfficeCrypto.utf16le(s.toCharArray()))
    }

    @Test
    fun 반복_해시는_hashlib_과_같다() {
        assertContentEquals(
            hex("eca52d7139920fbdbb5cdb74311723d623366235"),
            OfficeCrypto.iteratedHash(OfficeHash.SHA1, ByteArray(16) { it.toByte() }, pw("암호 pw"), 1000, noCancel),
        )
        assertContentEquals(
            hex("0aa7ed7052535ee0bc570795208a10b3d535d7d2944985e2b96ce96343c6fe534f1824919c771663f4e73a13f68dffff"),
            OfficeCrypto.iteratedHash(OfficeHash.SHA384, ByteArray(16) { (it + 16).toByte() }, pw("P@ss"), 12345, noCancel),
        )
    }

    @Test
    fun 반복_해시는_만_번마다_취소를_본다() {
        var calls = 0
        OfficeCrypto.iteratedHash(OfficeHash.SHA1, ByteArray(16), pw("x"), 100_000) { calls++ }
        assertEquals(10, calls)
    }

    @Test
    fun 반복_해시가_취소로_끝나면_반쯤_돈_중간값을_덮는다() {
        // 검토가 잡은 것 — 취소가 나면 Hn(암호에서 유도한 값)이 버려진 배열에 그대로 남았다.
        val h = ByteArray(OfficeHash.SHA512.size)
        var calls = 0
        assertFailsWith<java.util.concurrent.CancellationException> {
            OfficeCrypto.iteratedHashInto(h, OfficeHash.SHA512, ByteArray(16) { 1 }, pw("secret"), 100_000) {
                // 두 번째 확인(1만 회 뒤)에서 취소 — 그때 h 는 이미 암호에서 유도한 값으로 차 있다.
                if (++calls == 2) throw java.util.concurrent.CancellationException("취소")
            }
        }
        assertEquals(2, calls)
        assertContentEquals(ByteArray(h.size), h)
        // 끝까지 돌면 결과가 그대로 남는다(위 KAT 와 같은 값).
        val done = ByteArray(OfficeHash.SHA1.size)
        OfficeCrypto.iteratedHashInto(done, OfficeHash.SHA1, ByteArray(16) { it.toByte() }, pw("암호 pw"), 1000, noCancel)
        assertContentEquals(hex("eca52d7139920fbdbb5cdb74311723d623366235"), done)
    }

    @Test
    fun 길이_맞추기는_자르거나_0x36_으로_채운다() {
        assertContentEquals(byteArrayOf(1, 2), OfficeCrypto.fit(byteArrayOf(1, 2, 3), 2))
        assertContentEquals(byteArrayOf(1, 0x36, 0x36), OfficeCrypto.fit(byteArrayOf(1), 3))
        assertEquals(OfficeHash.SHA512, OfficeHash.of("SHA-512"))
        assertEquals(OfficeHash.SHA1, OfficeHash.of("SHA1"))
        assertNull(OfficeHash.of("MD5"))
    }

    // ---- Standard: 오피스가 만든 파일의 값 ------------------------------------------------

    private fun standardInfo(salt: ByteArray, verifier: ByteArray, verifierHash: ByteArray, flags: Int = 0x24, alg: Int = 0x660E, keyBits: Int = 128): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) {
            out.write(v and 0xFF)
            out.write(v shr 8 and 0xFF)
        }
        fun u32(v: Int) {
            u16(v and 0xFFFF)
            u16(v ushr 16)
        }
        val csp = "Microsoft Enhanced RSA and AES Cryptographic Provider\u0000".toByteArray(Charsets.UTF_16LE)
        u16(3)
        u16(2)
        u32(flags)
        u32(32 + csp.size)
        u32(flags)
        u32(0)
        u32(alg)
        u32(0x8004)
        u32(keyBits)
        u32(0x18)
        u32(0)
        u32(0)
        out.write(csp)
        u32(16)
        out.write(salt)
        out.write(verifier)
        u32(20)
        out.write(verifierHash)
        return out.toByteArray()
    }

    private val stdSalt = hex("e88266490c5bd1eebd2b4394e3f830ef")
    private val stdVerifier = hex("516f732e966fac17b1c5d7d8cc36c928")
    private val stdVerifierHash = hex("2b6168dabe2911ad2bd37c1746745c14d3cf1bb140a48f4e6f3d23880872b16a")

    @Test
    fun Standard_열쇠_유도와_암호_확인이_오피스_값과_맞는다() {
        val std = StandardEncryption.parse(standardInfo(stdSalt, stdVerifier, stdVerifierHash))
        val key = assertNotNull(std.keyFor(pw("Password1234_"), noCancel))
        assertContentEquals(hex("40b13a71f90b966e375408f2d181a1aa"), key)
        assertNull(std.keyFor(pw("Password1234"), noCancel))
        assertNull(std.keyFor(pw("password1234_"), noCancel))
    }

    @Test
    fun Standard_의_RC4_와_외부_방식은_풀지_않는다() {
        fun failureOf(info: ByteArray): OpenFailure =
            assertFailsWith<OfficeCryptoFailure> { StandardEncryption.parse(info) }.failure
        // fAES 가 꺼졌다 — RC4 CryptoAPI.
        assertIs<OpenFailure.Unsupported>(failureOf(standardInfo(stdSalt, stdVerifier, stdVerifierHash, flags = 0x04, alg = 0x6801)))
        // fExternal.
        assertIs<OpenFailure.Unsupported>(failureOf(standardInfo(stdSalt, stdVerifier, stdVerifierHash, flags = 0x34)))
        // 알고리즘과 열쇠 길이가 맞지 않는다.
        assertIs<OpenFailure.Corrupt>(failureOf(standardInfo(stdSalt, stdVerifier, stdVerifierHash, keyBits = 256)))
        // 잘린 정보.
        assertIs<OpenFailure.Corrupt>(failureOf(standardInfo(stdSalt, stdVerifier, stdVerifierHash).copyOf(150)))
    }

    // ---- Agile: 오피스가 만든 파일의 값 ---------------------------------------------------

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    private fun agileXml(
        salt: ByteArray,
        input: ByteArray,
        value: ByteArray,
        keyValue: ByteArray = ByteArray(32),
        spin: String = "100000",
        cipher: String = "AES",
        hash: String = "SHA512",
        keyBits: Int = 256,
        passwordEncryptor: Boolean = true,
        certificate: Boolean = false,
        integrity: Boolean = true,
    ): ByteArray {
        val common = "saltSize=\"16\" blockSize=\"16\" keyBits=\"$keyBits\" hashSize=\"64\" cipherAlgorithm=\"$cipher\" " +
            "cipherChaining=\"ChainingModeCBC\" hashAlgorithm=\"$hash\""
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n")
        sb.append("<encryption xmlns=\"http://schemas.microsoft.com/office/2006/encryption\" ")
        sb.append("xmlns:p=\"http://schemas.microsoft.com/office/2006/keyEncryptor/password\" ")
        sb.append("xmlns:c=\"http://schemas.microsoft.com/office/2006/keyEncryptor/certificate\">")
        sb.append("<keyData saltSize=\"16\" blockSize=\"16\" keyBits=\"256\" hashSize=\"64\" cipherAlgorithm=\"AES\" ")
        sb.append("cipherChaining=\"ChainingModeCBC\" hashAlgorithm=\"SHA512\" saltValue=\"${b64(ByteArray(16) { 7 })}\"/>")
        if (integrity) sb.append("<dataIntegrity encryptedHmacKey=\"${b64(ByteArray(64))}\" encryptedHmacValue=\"${b64(ByteArray(64))}\"/>")
        sb.append("<keyEncryptors>")
        if (passwordEncryptor) {
            sb.append("<keyEncryptor uri=\"http://schemas.microsoft.com/office/2006/keyEncryptor/password\">")
            sb.append("<p:encryptedKey spinCount=\"$spin\" $common saltValue=\"${b64(salt)}\" ")
            sb.append("encryptedVerifierHashInput=\"${b64(input)}\" encryptedVerifierHashValue=\"${b64(value)}\" ")
            sb.append("encryptedKeyValue=\"${b64(keyValue)}\"/></keyEncryptor>")
        }
        if (certificate) {
            sb.append("<keyEncryptor uri=\"http://schemas.microsoft.com/office/2006/keyEncryptor/certificate\">")
            sb.append("<c:encryptedKey encryptedKeyValue=\"${b64(ByteArray(256))}\" x509Certificate=\"AAAA\" certVerifier=\"AAAA\"/>")
            sb.append("</keyEncryptor>")
        }
        sb.append("</keyEncryptors></encryption>")
        return sb.toString().toByteArray()
    }

    private val agSalt = hex("cbca1c999343fbad92075634150034b0")
    private val agInput = hex("39eea54e26e514798c284bc7714d38ac")
    private val agValue = hex(
        "14376d6d817334e6b0ff4fd8221a7c678e5d8a784e8f999f4c188930c36a4b29" +
            "c5b333605b5cd403b05003adcf18cca8cbab8debe373c65604a0becfae5c0ad0",
    )

    private fun agile(xml: ByteArray) = AgileEncryption.parse(xml, 0, ParseLimits.DEFAULT)

    @Test
    fun Agile_암호_확인이_오피스_값과_맞는다() {
        val a = agile(agileXml(agSalt, agInput, agValue))
        assertNotNull(a.keyFor(pw("Password1234_"), noCancel))
        assertNull(a.keyFor(pw("Password1234"), noCancel))
    }

    @Test
    fun Agile_비밀_열쇠_풀기가_오피스_값과_맞는다() {
        // 확인값과 감싼 열쇠가 서로 다른 파일에서 왔으므로 조각으로 확인한다: Hn → 블록 열쇠 → AES-CBC.
        val salt = hex("4c725d45dc610f939412a04da7910466")
        val h = OfficeCrypto.iteratedHash(OfficeHash.SHA512, salt, pw("Password1234_"), 100_000, noCancel)
        val k3 = OfficeCrypto.fit(OfficeCrypto.hash(OfficeHash.SHA512, h, hex("146e0be7abacd0d6")), 32)
        val secret = OfficeCrypto.aesCbcDecrypt(k3, OfficeCrypto.fit(salt, 16), hex("a16cd5165a7ab9d271113ed386a78cf49692e8e527b0c5fc0055ed080b7cb94b"))
        assertContentEquals(hex("40206609d9faadf24b076aebf2c435b74292c8b8a7aa81bc679be89711b02ac2"), secret)
    }

    @Test
    fun Agile_설명자의_방식과_길이를_견준다() {
        fun failureOf(xml: ByteArray): OpenFailure = assertFailsWith<OfficeCryptoFailure> { agile(xml) }.failure
        // 명세의 상한(1,000만)을 넘는 반복은 풀지 않는다. 상한 그 자체는 받는다.
        assertIs<OpenFailure.Unsupported>(failureOf(agileXml(agSalt, agInput, agValue, spin = "10000001")))
        agile(agileXml(agSalt, agInput, agValue, spin = "10000000"))
        assertIs<OpenFailure.Corrupt>(failureOf(agileXml(agSalt, agInput, agValue, spin = "-1")))
        assertIs<OpenFailure.Corrupt>(failureOf(agileXml(agSalt, agInput, agValue, spin = "many")))
        assertIs<OpenFailure.Unsupported>(failureOf(agileXml(agSalt, agInput, agValue, cipher = "DES")))
        assertIs<OpenFailure.Unsupported>(failureOf(agileXml(agSalt, agInput, agValue, hash = "MD5")))
        // hashSize(64)가 해시(SHA-1 = 20)와 맞지 않는다.
        assertIs<OpenFailure.Corrupt>(failureOf(agileXml(agSalt, agInput, agValue, hash = "SHA1")))
        assertIs<OpenFailure.Corrupt>(failureOf(agileXml(agSalt, agInput, agValue, keyBits = 100)))
        // 감싼 확인 해시가 해시 크기보다 짧다.
        assertIs<OpenFailure.Corrupt>(failureOf(agileXml(agSalt, agInput, agValue.copyOf(32))))
        // 블록 배수가 아니다.
        assertIs<OpenFailure.Corrupt>(failureOf(agileXml(agSalt, agInput, agValue, keyValue = ByteArray(33))))
        assertIs<OpenFailure.Corrupt>(failureOf(agileXml(agSalt, agInput, agValue, integrity = false)))
        assertIs<OpenFailure.Corrupt>(failureOf("<not-encryption/>".toByteArray()))
        assertIs<OpenFailure.Corrupt>(failureOf("<encryption".toByteArray()))
        // 인증서로만 감쌌다 — 암호로는 풀 수 없다. 둘 다 있으면 암호 쪽을 쓴다.
        assertIs<OpenFailure.Encrypted>(failureOf(agileXml(agSalt, agInput, agValue, passwordEncryptor = false, certificate = true)))
        agile(agileXml(agSalt, agInput, agValue, certificate = true))
    }
}
