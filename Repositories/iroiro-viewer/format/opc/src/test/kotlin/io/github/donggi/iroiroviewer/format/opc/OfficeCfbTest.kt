package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.ByteArrayChannel
import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 암호가 걸린 OOXML 과 그 밖의 CFB — 표본 파일로.
 *
 * `src/test/resources/officecrypt/` 의 표본은 **우리가 아닌 도구**가 잠갔다(생성 스크립트 `make_samples.py`,
 * 저장소 밖):
 *
 * * `agile*.{docx,xlsx,pptx}`(AES-256·SHA-512) — **msoffcrypto-tool 6.0.0** 이 잠갔고, 같은 도구가 암호 확인·
 *   무결성 검사까지 켜고 풀어 평문과 같은지 확인했다. `plain.*` 이 그 평문이다(python-docx·openpyxl·
 *   python-pptx 가 열어 넣은 글자가 그대로 나오는 것을 확인한 패키지).
 * * `agile-sha1-aes128`·`agile-sha384-aes256`, `standard-*` — 명세대로 쓴 우리 Python 잠금기와 CFB 짜개가
 *   만들고 **msoffcrypto 가 풀어** 평문과 같은지 확인했다(Standard 는 암호 확인까지). `agile-sha256-aes192` 는
 *   msoffcrypto 가 192비트 열쇠를 다루지 못해 **독립 확인이 없다** — 명세의 '열쇠 길이로 자른다' 만 본다.
 * * `agile-spin-huge`·`agile-cert-only` — msoffcrypto 의 조각과 CFB 짜개로, 설명자만 고쳐 만들었다.
 * * `irm-drm*`·`legacy-*`·`hwp-like`·`rc4-cryptoapi`·`extensible` — 모양만 흉내 낸 합성 CFB(내용은 난수).
 *   모든 CFB 는 olefile 로 다시 열어 확인했다.
 */
class OfficeCfbTest {

    private val dir = File(javaClass.classLoader!!.getResource("officecrypt/plain.docx")!!.toURI()).parentFile

    private fun bytes(name: String) = File(dir, name).readBytes()

    private fun open(
        name: String,
        password: String?,
        limits: ParseLimits = ParseLimits.DEFAULT,
        data: ByteArray = bytes(name),
        checkCancelled: () -> Unit = {},
    ): OfficeCfb.Result = OfficeCfb.open(ByteArrayDocumentSource(data, name), password?.toCharArray(), limits, checkCancelled)

    private fun plainOf(r: OfficeCfb.Result): ByteArray = when (r) {
        is OfficeCfb.Result.Decrypted -> r.bytes
        is OfficeCfb.Result.Failed -> fail("풀리지 않았다: ${r.failure}")
    }

    private fun failureOf(r: OfficeCfb.Result): OpenFailure = when (r) {
        is OfficeCfb.Result.Decrypted -> fail("풀리면 안 된다")
        is OfficeCfb.Result.Failed -> r.failure
    }

    // ---- Agile (msoffcrypto 가 잠근 것) ------------------------------------------------

    @Test
    fun Agile_docx_를_맞는_암호로_풀면_평문_패키지와_같다() {
        assertContentEquals(bytes("plain.docx"), plainOf(open("agile.docx", "pw-ascii-123")))
    }

    @Test
    fun Agile_xlsx_를_한글_암호로_푼다() {
        assertContentEquals(bytes("plain.xlsx"), plainOf(open("agile-ko.xlsx", "암호123")))
    }

    @Test
    fun Agile_pptx_를_공백과_기호가_든_암호로_푼다() {
        assertContentEquals(bytes("plain.pptx"), plainOf(open("agile.pptx", "Slide Deck #7")))
    }

    @Test
    fun 암호가_없으면_묻고_틀리면_틀렸다고_한다() {
        val none = failureOf(open("agile.docx", null))
        assertEquals(OpenFailure.PasswordRequired(OfficeCfb.PASSWORD, wrongPassword = false), none)
        val wrong = failureOf(open("agile.docx", "pw-ascii-124"))
        assertEquals(OpenFailure.PasswordRequired(OfficeCfb.PASSWORD, wrongPassword = true), wrong)
        assertIs<OpenFailure.PasswordRequired>(failureOf(open("agile-ko.xlsx", "암호12")))
        // 빈 암호도 암호다(틀린 암호).
        assertEquals(true, (failureOf(open("agile.docx", "")) as OpenFailure.PasswordRequired).wrongPassword)
    }

    @Test
    fun VelvetSweatshop_으로_잠근_파일은_묻지_않고_연다() {
        assertContentEquals(bytes("plain.xlsx"), plainOf(open("agile-velvet.xlsx", null)))
        // 다른 암호를 넣어도 기본 암호를 한 번 더 대 본다.
        assertContentEquals(bytes("plain.xlsx"), plainOf(open("agile-velvet.xlsx", "whatever")))
        assertContentEquals(bytes("plain.xlsx"), plainOf(open("agile-velvet.xlsx", "VelvetSweatshop")))
    }

    /** EncryptedPackage 스트림 안의 바이트를 파일에서 찾는다(배치를 가정하지 않는다). */
    private fun offsetInPackage(file: ByteArray, streamOffset: Int): Int {
        val stream = CfbFile.open(ByteArrayDocumentSource(file, "x")).use { it.readStream(it.find("EncryptedPackage")!!, 1 shl 24) }
        val window = stream.copyOfRange(streamOffset, streamOffset + 32)
        val at = (0..file.size - 32).single { i -> (0 until 32).all { file[i + it] == window[it] } }
        return at
    }

    @Test
    fun 암호문이_한_바이트라도_바뀌면_무결성_검사가_잡는다() {
        val data = bytes("agile.docx")
        val at = offsetInPackage(data, 1000)
        data[at] = (data[at].toInt() xor 0x01).toByte()
        assertEquals(OpenFailure.Corrupt(OfficeCfb.INTEGRITY), failureOf(open("agile.docx", "pw-ascii-123", data = data)))
    }

    @Test
    fun 앞_8바이트의_평문_크기도_무결성이_덮는다() {
        // 크기를 1 줄이면 암호문은 그대로라 풀리고 ZIP 서명도 맞는다 — HMAC 이 앞 8바이트를 빼고 셈하면
        // 여기서 '풀렸다' 가 된다. 명세는 HMAC 을 스트림 **전체**에 건다.
        val data = bytes("agile.docx")
        val at = offsetInPackage(data, 0)
        assertEquals(bytes("plain.docx").size, (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8))
        data[at] = (data[at] - 1).toByte()
        assertEquals(OpenFailure.Corrupt(OfficeCfb.INTEGRITY), failureOf(open("agile.docx", "pw-ascii-123", data = data)))
    }

    @Test
    fun 적힌_평문_크기가_암호문보다_길면_잘린_것이다() {
        val data = bytes("agile.docx")
        val at = offsetInPackage(data, 0)
        // 앞 8바이트(평문 크기)를 100만으로.
        for (i in 0 until 8) data[at + i] = ((1_000_000L shr (8 * i)) and 0xFF).toByte()
        assertEquals(OpenFailure.Corrupt(OfficeCfb.TRUNCATED), failureOf(open("agile.docx", null, data = data)))
    }

    @Test
    fun 잘린_파일은_깨진_것이다() {
        val data = bytes("agile.docx")
        assertIs<OpenFailure.Corrupt>(failureOf(open("agile.docx", "pw-ascii-123", data = data.copyOf(data.size - 1024))))
        assertIs<OpenFailure.Corrupt>(failureOf(open("agile.docx", "pw-ascii-123", data = data.copyOf(400))))
    }

    @Test
    fun 상한보다_큰_패키지는_암호를_묻기_전에_끊는다() {
        val small = ParseLimits.DEFAULT.copy(maxSingleOutput = 1000)
        assertEquals(OpenFailure.TooLarge(OfficeCfb.TOO_LARGE), failureOf(open("agile.docx", null, small)))
        assertEquals(OpenFailure.TooLarge(OfficeCfb.TOO_LARGE), failureOf(open("agile.docx", "pw-ascii-123", small)))
    }

    @Test
    fun spinCount_가_명세의_상한을_넘으면_풀지_않는다() {
        assertEquals(OpenFailure.Unsupported(OfficeCfb.UNSUPPORTED_CRYPTO), failureOf(open("agile-spin-huge.docx", "pw-ascii-123")))
        assertIs<OpenFailure.Unsupported>(failureOf(open("agile-spin-huge.docx", null)))
    }

    @Test
    fun 인증서로만_감싼_Agile_은_암호로_풀_수_없다() {
        assertEquals(OpenFailure.Encrypted(OfficeCfb.ENCRYPTED), failureOf(open("agile-cert-only.docx", null)))
        assertIs<OpenFailure.Encrypted>(failureOf(open("agile-cert-only.docx", "pw-ascii-123")))
    }

    // ---- 다른 해시·열쇠 길이 ------------------------------------------------------------

    @Test
    fun Agile_SHA1_AES128_작은_스트림에_든_패키지() {
        assertContentEquals(bytes("plain-small.docx"), plainOf(open("agile-sha1-aes128.docx", "sha1-pw")))
        assertIs<OpenFailure.PasswordRequired>(failureOf(open("agile-sha1-aes128.docx", "sha1-pX")))
    }

    @Test
    fun Agile_SHA384_AES256() {
        assertContentEquals(bytes("plain-small.docx"), plainOf(open("agile-sha384-aes256.docx", "sha384-pw")))
    }

    @Test
    fun Agile_SHA256_AES192_는_열쇠를_열쇠_길이로_자른다() {
        assertContentEquals(bytes("plain-small.docx"), plainOf(open("agile-sha256-aes192.docx", "sha256-pw")))
    }

    // ---- Standard ------------------------------------------------------------------------

    @Test
    fun Standard_AES128_을_푼다() {
        assertContentEquals(bytes("plain-small.docx"), plainOf(open("standard-aes128.docx", "std-pass-128")))
        assertEquals(false, (failureOf(open("standard-aes128.docx", null)) as OpenFailure.PasswordRequired).wrongPassword)
        assertEquals(true, (failureOf(open("standard-aes128.docx", "std-pass-129")) as OpenFailure.PasswordRequired).wrongPassword)
    }

    @Test
    fun Standard_AES256_은_X2_까지_이어_열쇠를_만든다() {
        assertContentEquals(bytes("plain.xlsx"), plainOf(open("standard-aes256.xlsx", "표준암호256")))
    }

    @Test
    fun Standard_는_무결성_값이_없어_깨진_암호문을_ZIP_서명이_잡는다() {
        // 검토가 더한 것 — 이 판정(풀린 내용이 ZIP 이 아니면 Corrupt)을 지워도 시험이 하나도 깨지지 않았다.
        // Standard 는 HMAC 이 없으니 맞는 암호로 깨진 암호문을 풀면 쓰레기가 나온다. 첫 블록을 건드려
        // 평문 앞 16바이트(ZIP 서명)를 깨뜨린다.
        val data = bytes("standard-aes128.docx")
        val at = offsetInPackage(data, 8)
        data[at] = (data[at].toInt() xor 0x01).toByte()
        assertEquals(OpenFailure.Corrupt(OfficeCfb.NOT_ZIP), failureOf(open("standard-aes128.docx", "std-pass-128", data = data)))
    }

    /** 뿌리 바로 아래 항목 [name] 의 이름 마지막 글자를 바꾼다(디렉터리 칸은 128바이트 경계, 이름 길이 칸이 맞는 것). */
    private fun renameEntry(file: ByteArray, name: String, lastChar: Char) {
        val needle = name.toByteArray(Charsets.UTF_16LE)
        val len = (name.length + 1) * 2
        val at = (0..file.size - 128 step 128).single { i ->
            needle.indices.all { file[i + it] == needle[it] } &&
                ((file[i + 64].toInt() and 0xFF) or ((file[i + 65].toInt() and 0xFF) shl 8)) == len
        }
        file[at + (name.length - 1) * 2] = lastChar.code.toByte()
    }

    @Test
    fun 암호_정보와_잠긴_패키지가_짝을_잃으면() {
        // 암호 정보만 있다 — 패키지가 없으니 잘린 것이다.
        val noPackage = bytes("agile.docx").also { renameEntry(it, "EncryptedPackage", 'X') }
        assertEquals(OpenFailure.Corrupt(OfficeCfb.TRUNCATED), failureOf(open("agile.docx", "pw-ascii-123", data = noPackage)))
        // 패키지만 있다(변환은 암호 변환) — 암호 정보 없이는 풀 길이 없다.
        val noInfo = bytes("agile.docx").also { renameEntry(it, "EncryptionInfo", 'X') }
        assertEquals(OpenFailure.Encrypted(OfficeCfb.ENCRYPTED), failureOf(open("agile.docx", "pw-ascii-123", data = noInfo)))
    }

    @Test
    fun 무작위_접근이_없는_원본은_다루지_않는다() {
        val data = bytes("agile.docx")
        val streamOnly = object : DocumentSource {
            override val displayName = "a.docx"
            override val length = data.size.toLong()
            override fun openStream(): InputStream = data.inputStream()
        }
        assertIs<OpenFailure.Unsupported>(failureOf(OfficeCfb.open(streamOnly, "pw-ascii-123".toCharArray(), ParseLimits.DEFAULT) {}))
    }

    @Test
    fun RC4_CryptoAPI_와_Extensible_은_풀지_않는다() {
        for (name in listOf("rc4-cryptoapi.docx", "extensible.docx")) {
            assertEquals(OpenFailure.Unsupported(OfficeCfb.UNSUPPORTED_CRYPTO), failureOf(open(name, null)), name)
            assertEquals(OpenFailure.Unsupported(OfficeCfb.UNSUPPORTED_CRYPTO), failureOf(open(name, "x")), name)
        }
    }

    // ---- 암호가 아닌 잠금, 이전 형식, 그 밖 ----------------------------------------------------

    @Test
    fun IRM_으로_잠긴_패키지는_암호로_풀_수_없다() {
        assertEquals(OpenFailure.Encrypted(OfficeCfb.ENCRYPTED), failureOf(open("irm-drm.docx", null)))
        // 옆에 풀 수 있는 암호 정보가 있어도 변환이 이긴다 — 맞는 암호를 줘도 풀지 않는다.
        assertEquals(OpenFailure.Encrypted(OfficeCfb.ENCRYPTED), failureOf(open("irm-drm-with-info.docx", "pw-ascii-123")))
    }

    @Test
    fun 이전_형식은_LegacyFormat_이고_HWP_는_다루지_않는다() {
        for (name in listOf("legacy-word.doc", "legacy-excel.xls", "legacy-excel95.xls", "legacy-ppt.ppt")) {
            assertEquals(OpenFailure.LegacyFormat(OfficeCfb.LEGACY), failureOf(open(name, null)), name)
        }
        assertEquals(OpenFailure.Unsupported(OfficeCfb.NOT_OFFICE), failureOf(open("hwp-like.hwp", null)))
    }

    @Test
    fun CFB_가_아니면_깨진_것이다() {
        assertEquals(OpenFailure.Corrupt(OfficeCfb.CORRUPT_CFB), failureOf(open("plain.docx", null)))
        assertEquals(OpenFailure.Corrupt(OfficeCfb.CORRUPT_CFB), failureOf(open("empty", null, data = ByteArray(0))))
    }

    // ---- 암호와 취소 ----------------------------------------------------------------------

    @Test
    fun 넣은_암호는_지우지_않는다() {
        val password = "pw-ascii-123".toCharArray()
        OfficeCfb.open(ByteArrayDocumentSource(bytes("agile.docx"), "a"), password, ParseLimits.DEFAULT) {}
        assertContentEquals("pw-ascii-123".toCharArray(), password)
    }

    @Test
    fun 열쇠_유도_중에_취소를_본다() {
        var calls = 0
        plainOf(open("agile.docx", "pw-ascii-123") { calls++ })
        // SHA-512 10만 회 — 만 번마다 한 번.
        assertTrue(calls >= 10, "calls=$calls")
        assertFailsWith<InterruptedIOException> {
            open("agile.docx", "pw-ascii-123") { throw InterruptedIOException("취소") }
        }
        assertFailsWith<CancellationException> {
            open("standard-aes128.docx", null) { throw CancellationException("취소") }
        }
    }

    /**
     * `samples-local/cfb/big-agile.docx`(커밋하지 않는다, 생성 스크립트 `big_samples.py`) — msoffcrypto 가 잠근
     * 9 MB 패키지. 그 도구의 CFB 짜개가 FAT 섹터 146개를 쓰므로 **남이 쓴 DIFAT 체인**을 지난다. 평문은 두지 않고
     * SHA-256 만 옆에 둔다. 파일 채널(`FileDocumentSource`)로 연다. 없으면 건너뛴다.
     */
    @Test
    fun 로컬의_큰_표본은_DIFAT_을_지나_풀린다() {
        val local = generateSequence(dir) { it.parentFile }
            .map { File(it, "samples-local/cfb/big-agile.docx") }
            .firstOrNull { it.isFile }
        assumeTrue("samples-local/cfb/big-agile.docx 가 없다", local != null)
        val expected = File(local!!.path + ".sha256").readText().trim()
        var calls = 0
        val plain = plainOf(OfficeCfb.open(FileDocumentSource(local), "big-secret".toCharArray(), ParseLimits.DEFAULT) { calls++ })
        try {
            val sha = MessageDigest.getInstance("SHA-256").digest(plain).joinToString("") { "%02x".format(it) }
            assertEquals(expected, sha)
            // 열쇠 유도 10번 + 푸는 동안 1 MiB 마다.
            assertTrue(calls >= 10 + 8, "calls=$calls")
        } finally {
            plain.fill(0)
        }
    }

    @Test
    fun 파일_채널이_인터럽트로_닫히면_입출력_실패가_아니라_취소다() {
        val data = bytes("agile.docx")
        val source = object : DocumentSource {
            override val displayName = "a.docx"
            override val length = data.size.toLong()
            override fun openStream(): InputStream = data.inputStream()
            override fun openChannel(): SeekableByteChannel = object : SeekableByteChannel by ByteArrayChannel(data) {
                override fun read(dst: ByteBuffer): Int = throw ClosedByInterruptException()
            }
        }
        assertFailsWith<InterruptedIOException> { OfficeCfb.open(source, null, ParseLimits.DEFAULT) {} }
    }

    @Test
    fun 기본_암호를_대_보는_값은_열쇠_유도_한_번이다() {
        // 암호가 없으면 VelvetSweatshop 만(10만 회 → 취소 확인 10번), 틀린 암호면 그것과 VelvetSweatshop(20번).
        var none = 0
        failureOf(open("agile.docx", null) { none++ })
        assertEquals(10, none)
        var wrong = 0
        failureOf(open("agile.docx", "nope") { wrong++ })
        assertEquals(20, wrong)
        // 넣은 암호가 VelvetSweatshop 이면 두 번 대지 않는다.
        var velvet = 0
        failureOf(open("agile.docx", "VelvetSweatshop") { velvet++ })
        assertEquals(10, velvet)
        assertFalse(velvet > 10)
    }
}
