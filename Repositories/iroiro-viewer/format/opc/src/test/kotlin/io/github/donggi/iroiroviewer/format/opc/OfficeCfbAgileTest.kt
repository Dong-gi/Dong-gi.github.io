package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import io.github.donggi.iroiroviewer.format.cfb.TinyCfb
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * 12단계 검토가 남긴 시험의 빈틈 둘 — **풀린 것이 ZIP 이 아닐 때**(Agile)와 **잠긴 패키지가 CFB 의 작은 스트림 경계(4096) 양쪽에
 * 있을 때** — 과, 로컬 표본으로만 보다가 늘 건너뛰던 **DIFAT 너머의 큰 패키지**.
 *
 * msoffcrypto 로 잠근 표본(`OfficeCfbTest`)으로는 둘을 만들 수 없다 — 그 도구는 ZIP 만 잠그고, 4096바이트 이하의 패키지를 작은
 * 스트림 자리에 잘못 쓴다(CLAUDE.md 함정 표). 그래서 여기서는 **명세(MS-OFFCRYPTO 2.3.4.10~15)대로 쓴 시험용 잠금기**([lock])로
 * 잠그고 [TinyCfb] 로 담는다. 이 잠금기는 우리 복호화기와 같은 손이 쓴 것이라 **오라클이 아니다** — 방식이 맞는지는 msoffcrypto·
 * 오피스의 표본이 이미 본다(`OfficeCfbTest`·`OfficeCryptoTest`·`OoxmlCorpusCryptoTest`). 여기서 보는 것은 판정의 갈래와 경계뿐이고,
 * 그래서 같은 잠금기로 잠근 **ZIP 은 원래대로 풀리는지**를 함께 본다(잠금기가 틀렸으면 그쪽이 먼저 깨진다).
 */
class OfficeCfbAgileTest {

    private val dir = File(javaClass.classLoader!!.getResource("officecrypt/plain.docx")!!.toURI()).parentFile

    private fun open(cfb: ByteArray, password: String?): OfficeCfb.Result =
        OfficeCfb.open(source(cfb), password?.toCharArray(), ParseLimits.DEFAULT) {}

    private fun plainOf(r: OfficeCfb.Result): ByteArray = when (r) {
        is OfficeCfb.Result.Decrypted -> r.bytes
        is OfficeCfb.Result.Failed -> fail("풀리지 않았다: ${r.failure}")
    }

    private fun failureOf(r: OfficeCfb.Result): OpenFailure = when (r) {
        is OfficeCfb.Result.Decrypted -> fail("풀리면 안 된다")
        is OfficeCfb.Result.Failed -> r.failure
    }

    @Test
    fun 잠금기로_잠근_ZIP_은_원래대로_풀린다() {
        val zip = File(dir, "plain-small.docx").readBytes()
        assertContentEquals(zip, plainOf(open(lock(zip, "시험 암호"), "시험 암호")))
        assertEquals(true, (failureOf(open(lock(zip, "시험 암호"), "틀린 암호")) as OpenFailure.PasswordRequired).wrongPassword)
    }

    @Test
    fun Agile_로_풀었는데_ZIP_이_아니면_깨진_것이다() {
        // 암호가 맞고 무결성(HMAC)도 맞는데 속이 OOXML 패키지가 아니다 — Standard 에는 이 시험이 있었고(무결성 값이 없어
        // 깨진 암호문이 여기로 온다) Agile 에는 없었다. 이 판정을 지워도 Agile 쪽은 아무 시험도 깨지지 않았다.
        val notZip = "이것은 OOXML 패키지가 아니다".toByteArray() + ByteArray(300) { it.toByte() }
        assertEquals(OpenFailure.Corrupt(OfficeCfb.NOT_ZIP), failureOf(open(lock(notZip, "pw"), "pw")))
        // 첫 네 바이트만 ZIP 서명이어도 받는다(판정은 서명만 본다 — 속은 OPC 리더가 가린다).
        val signature = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + notZip
        assertContentEquals(signature, plainOf(open(lock(signature, "pw"), "pw")))
    }

    @Test
    fun 잠긴_패키지가_작은_스트림_경계의_양쪽에_있어도_푼다() {
        // `EncryptedPackage` 스트림의 길이 = 8(평문 크기) + 암호문(16의 배수) + 끝의 남는 바이트. 4096 미만이면 작은 스트림,
        // 4096 이상이면 일반 섹터다(명세 2.6.3). 4096 바로 그 길이는 남는 바이트 8 로만 만들 수 있다(오피스도 조각을 채워 쓴다).
        val zip = File(dir, "plain-small.docx").readBytes()
        val cases = listOf(
            4080 to 0, // 스트림 4088 — 작은 스트림
            4080 to 8, // 스트림 4096 — 경계 그 자리, 일반 섹터
            4088 to 0, // 스트림 4104 — 일반 섹터
            4096 to 0, // 평문이 Agile 조각(4096) 하나를 꽉 채운다
            4097 to 0, // 조각 둘, 둘째는 1바이트(16으로 채운다)
        )
        for ((size, tail) in cases) {
            val plain = zip.copyOf(size).also { if (size > zip.size) for (i in zip.size until size) it[i] = (i * 7).toByte() }
            val cfb = lock(plain, "경계", tail = tail)
            val streamLength = CfbFile.open(source(cfb)).use { f -> f.streamLength(f.find("EncryptedPackage")!!) }
            assertEquals(8L + (size + 15) / 16 * 16 + tail, streamLength, "평문 $size + 끝 $tail")
            assertContentEquals(plain, plainOf(open(cfb, "경계")), "평문 $size + 끝 $tail (스트림 $streamLength)")
        }
    }

    @Test
    fun 큰_패키지는_DIFAT_을_지나_흘려_읽으며_풀고_1MiB_마다_취소를_본다() {
        // 예전 `OfficeCfbTest` 의 로컬 표본 시험(msoffcrypto 가 잠근 9 MB — 파일이 없어 늘 건너뛰었다)이 보던 것을 여기서 늘 본다.
        // 7 MB 를 넘는 스트림은 FAT 섹터가 109개를 넘어 **DIFAT 체인 너머의 FAT** 으로 이어지고, 풀기는 그것을 흘려 읽는다
        // (`CfbFile.openStream`). 푸는 동안의 취소 확인(1 MiB 마다)도 그 시험만 보고 있었다.
        val plain = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(7_400_000) { (it * 31 + (it ushr 9)).toByte() }
        val cfb = lock(plain, "큰 표본")
        val difatSectors = (cfb[TinyCfb.H_NUM_DIFAT].toInt() and 0xFF) or ((cfb[TinyCfb.H_NUM_DIFAT + 1].toInt() and 0xFF) shl 8)
        kotlin.test.assertTrue(difatSectors > 0, "DIFAT 을 쓰지 않는 표본이다")
        var calls = 0
        val got = plainOf(OfficeCfb.open(source(cfb), "큰 표본".toCharArray(), ParseLimits.DEFAULT) { calls++ })
        try {
            assertContentEquals(plain, got)
        } finally {
            got.fill(0)
        }
        // 열쇠 유도 한 번(spin 1000 이라 확인 한 번) + 푸는 동안 1 MiB 마다 한 번(7 MB 면 일곱 번).
        kotlin.test.assertTrue(calls >= 1 + 7, "calls=$calls")
    }

    // ---- 시험용 잠금기(명세대로) --------------------------------------------------------------------

    private fun source(bytes: ByteArray) = ByteArrayDocumentSource(bytes, "locked.docx")

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun sha512(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").run {
        parts.forEach { update(it) }
        digest()
    }

    private fun le32(i: Int) = byteArrayOf(i.toByte(), (i shr 8).toByte(), (i shr 16).toByte(), (i shr 24).toByte())

    private fun fit(b: ByteArray, n: Int): ByteArray = if (b.size >= n) b.copyOf(n) else ByteArray(n) { 0x36 }.also { b.copyInto(it) }

    private fun padBlock(b: ByteArray): ByteArray = b.copyOf((b.size + 15) / 16 * 16)

    private fun aesCbc(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
            .doFinal(padBlock(data))

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    /**
     * Agile(AES-256·SHA-512)로 [plain] 을 잠근 CFB. 값(소금·비밀 열쇠)은 고정이다 — 시험이 날마다 같아야 한다.
     * [tail] 은 암호문 뒤에 붙이는 남는 바이트(무결성 값은 그것까지 덮는다).
     */
    private fun lock(plain: ByteArray, password: String, spin: Int = 1000, tail: Int = 0): ByteArray {
        val keySalt = hex("0f1e2d3c4b5a69788796a5b4c3d2e1f0")
        val pwSalt = hex("00112233445566778899aabbccddeeff")
        val secret = ByteArray(32) { (it * 11 + 3).toByte() }
        val verifierInput = ByteArray(16) { (it * 5 + 1).toByte() }

        // 암호 → Hn(2.3.4.11).
        var h = sha512(pwSalt, password.toByteArray(Charsets.UTF_16LE))
        for (i in 0 until spin) h = sha512(le32(i), h)
        fun blockKey(b: String) = fit(sha512(h, hex(b)), 32)
        val pwIv = fit(pwSalt, 16)
        val encInput = aesCbc(blockKey("fea7d2763b4b9e79"), pwIv, verifierInput)
        val encValue = aesCbc(blockKey("d7aa0f6d3061344e"), pwIv, sha512(verifierInput))
        val encKey = aesCbc(blockKey("146e0be7abacd0d6"), pwIv, secret)

        // 패키지 — 4096바이트 조각마다 IV = H(keyData 소금 + LE32(조각 번호)).
        val pkg = ByteArrayOutputStream()
        pkg.write(ByteArray(8).also { for (i in 0 until 8) it[i] = (plain.size.toLong() shr (8 * i)).toByte() })
        var off = 0
        var index = 0
        while (off < plain.size) {
            val n = minOf(4096, plain.size - off)
            pkg.write(aesCbc(secret, fit(sha512(keySalt, le32(index)), 16), plain.copyOfRange(off, off + n)))
            off += n
            index++
        }
        pkg.write(ByteArray(tail) { 0x5A })
        val encryptedPackage = pkg.toByteArray()

        // 무결성 — 스트림 전체의 HMAC-SHA512(2.3.4.14).
        val hmacKey = ByteArray(64) { (it * 3 + 7).toByte() }
        val hmacValue = Mac.getInstance("HmacSHA512").run {
            init(SecretKeySpec(hmacKey, "HmacSHA512"))
            doFinal(encryptedPackage)
        }
        val encHmacKey = aesCbc(secret, fit(sha512(keySalt, hex("5fb2ad010cb9e1f6")), 16), hmacKey)
        val encHmacValue = aesCbc(secret, fit(sha512(keySalt, hex("a0677f02b22c8433")), 16), hmacValue)

        val common = "blockSize=\"16\" keyBits=\"256\" hashSize=\"64\" cipherAlgorithm=\"AES\" cipherChaining=\"ChainingModeCBC\" hashAlgorithm=\"SHA512\""
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n" +
            "<encryption xmlns=\"http://schemas.microsoft.com/office/2006/encryption\" " +
            "xmlns:p=\"http://schemas.microsoft.com/office/2006/keyEncryptor/password\">" +
            "<keyData saltSize=\"16\" $common saltValue=\"${b64(keySalt)}\"/>" +
            "<dataIntegrity encryptedHmacKey=\"${b64(encHmacKey)}\" encryptedHmacValue=\"${b64(encHmacValue)}\"/>" +
            "<keyEncryptors><keyEncryptor uri=\"http://schemas.microsoft.com/office/2006/keyEncryptor/password\">" +
            "<p:encryptedKey spinCount=\"$spin\" saltSize=\"16\" $common saltValue=\"${b64(pwSalt)}\" " +
            "encryptedVerifierHashInput=\"${b64(encInput)}\" encryptedVerifierHashValue=\"${b64(encValue)}\" " +
            "encryptedKeyValue=\"${b64(encKey)}\"/></keyEncryptor></keyEncryptors></encryption>"
        // 판 4.4 + 예약 0x40(2.3.4.10).
        val info = byteArrayOf(4, 0, 4, 0, 0x40, 0, 0, 0) + xml.toByteArray(Charsets.UTF_8)
        return TinyCfb(3).stream("EncryptionInfo", info).stream("EncryptedPackage", encryptedPackage).build().bytes
    }
}
