package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.cfb.CfbEntry
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import io.github.donggi.iroiroviewer.format.cfb.CfbFormatException
import io.github.donggi.iroiroviewer.format.cfb.CfbLimits
import io.github.donggi.iroiroviewer.safety.DecryptLimits
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.channels.ClosedByInterruptException
import java.util.concurrent.CancellationException

/**
 * CFB(OLE2)로 싸인 오피스 파일을 연다 — **암호가 걸린 OOXML**(MS-OFFCRYPTO)과 이전 형식.
 *
 * | CFB 안에 있는 것 | 결과 |
 * |---|---|
 * | `EncryptionInfo` + `EncryptedPackage`, Agile(4.4) 또는 Standard AES(2.2·3.2·4.2) | 암호로 푼 **평문 패키지(ZIP) 바이트**. 암호가 없으면 [OpenFailure.PasswordRequired], 틀리면 `wrongPassword = true` |
 * | 같은 것이지만 RC4 CryptoAPI · Extensible(3.3·4.3) · AES 아닌 방식 · `spinCount` 1,000만 초과 | [OpenFailure.Unsupported] |
 * | `\u0006DataSpaces` 에 암호가 아닌 변환(IRM·DRM), 또는 인증서로만 감싼 Agile, 또는 `EncryptionInfo` 없는 `EncryptedPackage` | [OpenFailure.Encrypted] — 암호로 풀 수 없다 |
 * | `WordDocument`·`Workbook`·`Book`·`PowerPoint Document` | [OpenFailure.LegacyFormat] |
 * | 그 밖의 CFB(HWP 의 `FileHeader` 등) | [OpenFailure.Unsupported] |
 * | 깨진 CFB · 깨진 암호 정보 · 무결성(HMAC) 불일치 · 풀었는데 ZIP 이 아님 · 잘린 패키지 | [OpenFailure.Corrupt] |
 * | 적힌 평문 크기가 `min(maxSingleOutput, 256 MiB)` 초과 · 설명자가 64 KiB 초과 | [OpenFailure.TooLarge] |
 *
 * ## 암호를 묻기 전에 볼 수 있는 것은 먼저 본다
 *
 * 방식(풀 수 있는가)과 패키지의 모양(적힌 크기가 스트림 길이·상한 안인가)은 **암호 없이** 가린다.
 * 그렇지 않으면 사용자는 암호를 넣고 나서야 '풀 수 없다'·'깨졌다' 를 듣는다.
 *
 * ## `VelvetSweatshop`
 *
 * 엑셀은 '읽기 전용 권장' 등으로 **사용자 암호 없이** 파일을 잠글 때 이 이름의 기본 암호를 쓴다
 * (MS-OFFCRYPTO 가 적어 둔 값이다). 그런 파일에 암호를 물으면 사용자는 있지도 않은 암호를 요구받는다
 * (PDF 의 '소유자 암호만 걸린 문서' 와 같은 판단 — CLAUDE.md '암호를 다루는 규칙'). 그래서
 * **암호가 없으면 이것부터**, 넣은 암호가 틀렸으면 **이것을 한 번 더** 대 보고, 맞으면 그냥 연다.
 * 대가는 암호 확인 한 번(Agile 기본값이면 SHA-512 10만 회)이다.
 *
 * ## 평문
 *
 * **평문은 메모리에만 둔다**(CLAUDE.md '암호가 걸린 파일') — 돌려주는 [Result.Decrypted] 의 바이트가
 * 유일한 사본이다. 실패로 끝나는 길에서는 만든 평문을 0 으로 덮고 버린다.
 */
object OfficeCfb {

    sealed interface Result {
        /** 풀린 OPC 패키지(ZIP) 바이트. */
        class Decrypted(val bytes: ByteArray) : Result

        class Failed(val failure: OpenFailure) : Result
    }

    /**
     * 평문 패키지 크기의 절대 상한. **값을 새로 정하지 않는다** — 암호 PDF 와 '풀어 낸 평문 한 벌을
     * RAM 에 든다' 는 같은 예산이고, 상한은 `core:safety` 한곳에 있어야 한다(CLAUDE.md '안전').
     */
    const val MAX_DECRYPTED_BYTES = DecryptLimits.MAX_BYTES

    /** `EncryptionInfo` 스트림의 상한 — 판 8바이트 + XML 설명자 64 KiB. */
    const val MAX_INFO_BYTES = 8L + 64 * 1024

    private val VELVET = "VelvetSweatshop".toCharArray()

    // `OpenFailure.detail` — 우리가 쓴 고정 문장뿐이다. 화면은 종류로 문구를 고른다.
    internal const val PASSWORD = "암호가 걸린 오피스 문서다"
    internal const val ENCRYPTED = "암호로 풀 수 없는 방식(IRM·인증서)으로 잠겼다"
    internal const val UNSUPPORTED_CRYPTO = "풀지 않는 암호화 방식이다"
    internal const val LEGACY = "이전 형식 오피스 문서다"
    internal const val NOT_OFFICE = "오피스 문서가 아닌 CFB 다"
    internal const val CORRUPT_CFB = "CFB 구조가 깨졌다"
    internal const val CORRUPT_INFO = "암호 정보가 깨졌다"
    internal const val INTEGRITY = "풀린 내용이 무결성 검사를 통과하지 못했다"
    internal const val NOT_ZIP = "풀린 내용이 OOXML 패키지가 아니다"
    internal const val TRUNCATED = "잠긴 패키지가 잘렸다"
    internal const val TOO_LARGE = "잠긴 패키지가 너무 크다"
    private const val NO_CHANNEL = "무작위 접근을 주지 않는 원본이다"

    private val LEGACY_STREAMS = listOf("WordDocument", "Workbook", "Book", "PowerPoint Document")

    /**
     * @param password 사용자가 넣은 암호. **쓰기만 하고 지우지 않는다**(주인은 부르는 쪽이다).
     *   이 함수가 만드는 사본(UTF-16LE 바이트)은 이 함수가 지운다.
     * @param checkCancelled 긴 계산(열쇠 유도 만 회마다, 풀기 1 MiB 마다) 사이에 부른다. 취소면 던진다 —
     *   던진 것이 `CancellationException`·`InterruptedIOException` 이면 **그대로 밖으로 나간다.**
     *   그 밖의 예외는 여기서 끝나고 [Result.Failed] 가 된다.
     */
    fun open(
        source: DocumentSource,
        password: CharArray?,
        limits: ParseLimits,
        checkCancelled: () -> Unit,
    ): Result {
        return try {
            val channel = source.openChannel() ?: return fail(OpenFailure.Unsupported(NO_CHANNEL))
            CfbFile.open(channel, CfbLimits.from(limits)).use { cfb -> classify(cfb, password, limits, checkCancelled) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: ClosedByInterruptException) {
            // 파일 채널은 인터럽트를 받으면 **채널을 닫고** 이것을 던진다 — 입출력 실패가 아니라 취소다.
            // `Io` 로 옮기면 사용자가 나간 화면에 '입출력이 실패했다' 가 남는다.
            throw InterruptedIOException("취소")
        } catch (e: OfficeCryptoFailure) {
            fail(e.failure)
        } catch (e: CfbFormatException) {
            fail(OpenFailure.Corrupt(CORRUPT_CFB))
        } catch (e: ParseLimitExceededException) {
            fail(OpenFailure.TooLarge(TOO_LARGE))
        } catch (e: IOException) {
            fail(OpenFailure.Io("입출력이 실패했다"))
        } catch (e: SecurityException) {
            fail(OpenFailure.NoPermission("읽을 권한이 없다"))
        } catch (e: OutOfMemoryError) {
            fail(OpenFailure.TooLarge("메모리가 모자랐다"))
        } catch (e: Exception) {
            // 예외 이름만 — 메시지에는 파일에서 읽은 값이 들어 있을 수 있다.
            fail(OpenFailure.Corrupt(e::class.java.simpleName))
        }
    }

    private fun fail(failure: OpenFailure) = Result.Failed(failure)

    private fun classify(cfb: CfbFile, password: CharArray?, limits: ParseLimits, checkCancelled: () -> Unit): Result {
        val root = cfb.root
        val info = cfb.child(root, "EncryptionInfo")?.takeIf { it.isStream }
        val pkg = cfb.child(root, "EncryptedPackage")?.takeIf { it.isStream }
        val dataSpaces = cfb.child(root, "\u0006DataSpaces")?.takeIf { it.isStorage }

        // 변환이 암호가 아니면(IRM) 무엇이 들었든 암호로는 풀 수 없다. 이전 형식의 IRM 문서도 여기서 끝난다.
        if (dataSpaces != null && OfficeCryptoDataSpaces.hasForeignTransform(cfb, dataSpaces)) {
            return fail(OpenFailure.Encrypted(ENCRYPTED))
        }
        if (pkg != null) {
            if (info == null) return fail(OpenFailure.Encrypted(ENCRYPTED))
            return decryptPackage(cfb, info, pkg, password, limits, checkCancelled)
        }
        if (info != null) return fail(OpenFailure.Corrupt(TRUNCATED))
        if (LEGACY_STREAMS.any { cfb.child(root, it)?.isStream == true }) return fail(OpenFailure.LegacyFormat(LEGACY))
        return fail(OpenFailure.Unsupported(NOT_OFFICE))
    }

    private fun decryptPackage(
        cfb: CfbFile,
        info: CfbEntry,
        pkg: CfbEntry,
        password: CharArray?,
        limits: ParseLimits,
        checkCancelled: () -> Unit,
    ): Result {
        val decryptor = decryptorOf(cfb.readStream(info, MAX_INFO_BYTES), limits)

        // 패키지의 모양 — 암호를 묻기 전에.
        val streamLength = cfb.streamLength(pkg)
        if (streamLength < 8) return fail(OpenFailure.Corrupt(TRUNCATED))
        val prefix = ByteArray(8)
        cfb.openStream(pkg).use { OfficeCrypto.readFully(it, prefix, 8) }
        var declared = 0L
        for (i in 7 downTo 0) declared = (declared shl 8) or (prefix[i].toLong() and 0xFF)
        // 적힌 평문보다 암호문이 짧으면 잘린 것이다. 암호문은 16바이트 블록 배수다(Agile 의 4096바이트
        // 조각도 16의 배수다). 앞의 비교가 먼저 서야 뒤의 반올림이 넘치지 않는다.
        val available = streamLength - 8
        if (declared < 0 || declared > available || (declared + 15) / 16 * 16 > available) {
            return fail(OpenFailure.Corrupt(TRUNCATED))
        }
        if (declared > minOf(limits.maxSingleOutput, MAX_DECRYPTED_BYTES)) return fail(OpenFailure.TooLarge(TOO_LARGE))

        val key = unlock(decryptor, password, checkCancelled)
            ?: return fail(OpenFailure.PasswordRequired(PASSWORD, wrongPassword = password != null))
        try {
            val plain = decryptor.decrypt(key, cfb, pkg, declared, checkCancelled)
            if (!isZip(plain)) {
                plain.fill(0)
                return fail(OpenFailure.Corrupt(NOT_ZIP))
            }
            return Result.Decrypted(plain)
        } finally {
            key.fill(0)
        }
    }

    /** `EncryptionInfo` 의 판으로 방식을 고른다(MS-OFFCRYPTO 2.3.4.x). */
    private fun decryptorOf(info: ByteArray, limits: ParseLimits): OfficeDecryptor {
        if (info.size < 8) throw OfficeCryptoFailure(OpenFailure.Corrupt(CORRUPT_INFO))
        val major = (info[0].toInt() and 0xFF) or ((info[1].toInt() and 0xFF) shl 8)
        val minor = (info[2].toInt() and 0xFF) or ((info[3].toInt() and 0xFF) shl 8)
        return when {
            major == 4 && minor == 4 -> AgileEncryption.parse(info, 8, limits)
            minor == 2 && major in 2..4 -> StandardEncryption.parse(info)
            // Extensible(3.3·4.3)과 RC4(1.1 — 이진 형식 전용)와 모르는 판.
            else -> throw OfficeCryptoFailure(OpenFailure.Unsupported(UNSUPPORTED_CRYPTO))
        }
    }

    /**
     * 넣은 암호 → (틀리면) `VelvetSweatshop` 순으로 대 본다. 암호가 없으면 `VelvetSweatshop` 만.
     * 맞으면 열쇠(부른 쪽이 지운다), 아니면 null. 암호의 바이트 사본은 여기서 만들고 여기서 지운다.
     */
    private fun unlock(decryptor: OfficeDecryptor, password: CharArray?, checkCancelled: () -> Unit): ByteArray? {
        if (password != null) {
            tryPassword(decryptor, password, checkCancelled)?.let { return it }
            if (password.contentEquals(VELVET)) return null
        }
        return tryPassword(decryptor, VELVET, checkCancelled)
    }

    private fun tryPassword(decryptor: OfficeDecryptor, password: CharArray, checkCancelled: () -> Unit): ByteArray? {
        val bytes = OfficeCrypto.utf16le(password)
        try {
            return decryptor.keyFor(bytes, checkCancelled)
        } finally {
            bytes.fill(0)
        }
    }

    private fun isZip(b: ByteArray): Boolean =
        b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte()
}
