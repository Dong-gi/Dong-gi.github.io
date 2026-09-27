package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.CorruptFormatException
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.xmlpull.v1.XmlPullParser
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 암호 HWPX 의 항목 하나 — `META-INF/manifest.xml` 의 `odf:file-entry` 와 그 `odf:encryption-data`.
 *
 * 모양은 ODF 매니페스트를 빌렸고, 푸는 차례는 **한컴이 Apache-2.0 으로 공개한 OWPML 모델**
 * (`OWPML/Zip/encrypt.cpp`·`unzip.cpp`·`Document.cpp`)에 있다. 조사 노트의 요약:
 *
 * 1. 시작 열쇠 = SHA-256(암호 바이트) — `start-key-generation` 이 `xmldsig#sha256` 일 때. 적혀 있지 않으면
 *    모델의 기본값인 SHA-1.
 * 2. 열쇠 = PBKDF2-HMAC-SHA1(시작 열쇠, salt, iteration-count, key-size).
 * 3. AES-CBC(열쇠, IV), 채움 없음. 끝의 모자란 자리는 0 으로 채워져 있다(O16 실측 — 풀린 끝이 전부 0).
 * 4. 풀린 것은 **머리 없는 deflate**. ZIP 항목은 '저장' 으로 적혀 있어도 푼 뒤에 inflate 한다.
 *
 * 검증값(`checksum`)은 **inflate 한 평문의 첫 1024 바이트**의 SHA-256 이다(한컴 `Document.cpp` 의
 * `_CreateChecksum`, O16 의 일곱 항목 전부로 확인했다). ODF 명세는 '압축된 평문' 을 재지만 한컴은
 * 그렇게 하지 않는다 — 한컴의 것만 받는다. 검증값이 없거나 모르는 종류면 inflate 의 성공만 본다.
 *
 * @param salt·[iv] 같은 초에 만든 항목은 둘이 같을 수 있다 — 한컴 작성기가 `srand(time)` 을 쓴다
 *   (O16 이 그렇다). 깨진 것이 아니다.
 */
internal class EncryptedEntry(
    val name: String,
    val declaredSize: Long,
    val checksumAlgorithm: String?,
    val checksum: ByteArray?,
    val cipherKeyBytes: Int,
    val iv: ByteArray,
    val salt: ByteArray,
    val iterations: Int,
    val derivedKeyBytes: Int,
    val startKeyAlgorithm: String,
    val startKeyBytes: Int,
)

/** 암호 매니페스트를 읽은 결과. [unsupported] 가 있으면 우리가 풀 줄 모르는 방식이다(그 까닭, 우리가 쓴 말). */
internal class OdfManifest(val entries: Map<String, EncryptedEntry>, val unsupported: String?) {
    companion object {
        val EMPTY = OdfManifest(emptyMap(), null)
    }
}

/** 암호를 풀지 못했다. [wrongKey] 면 열쇠가 틀렸을 가능성이 크다(inflate 실패·검증값 불일치). */
internal class HwpxDecryptException(val wrongKey: Boolean) :
    CorruptFormatException(if (wrongKey) "암호를 풀지 못했다" else "암호 항목이 깨졌다")

internal object OdfManifestReader {

    private const val AES_PREFIX = "http://www.w3.org/2001/04/xmlenc#aes"
    private const val PBKDF2_SUFFIX = "pbkdf2"

    /**
     * `odf:manifest` 를 읽는다. 암호 정보가 없는 항목(평문 문서의 매니페스트는 빈 요소다)은 버린다.
     * 모르는 방식이 하나라도 있으면 [OdfManifest.unsupported] 에 적는다 — 그 항목만 평문으로 보고 넘어가면
     * 암호문을 XML 로 읽게 된다.
     */
    fun parse(p: XmlPullParser, limits: ParseLimits): OdfManifest {
        if (!HwpxXml.toRoot(p, limits)) return OdfManifest.EMPTY
        val out = LinkedHashMap<String, EncryptedEntry>()
        var unsupported: String? = null
        HwpxXml.eachChild(p, limits) { name ->
            if (name != "file-entry") {
                HwpxXml.skip(p, limits)
                return@eachChild
            }
            val path = HwpxXml.attr(p, "full-path")
            val size = HwpxXml.attr(p, "size")?.trim()?.toLongOrNull() ?: -1L
            var built: EntryBuilder? = null
            HwpxXml.eachChild(p, limits) { child ->
                if (child == "encryption-data") built = readEncryptionData(p, limits) else HwpxXml.skip(p, limits)
            }
            val b = built
            if (path == null || b == null) return@eachChild
            if (out.size >= HwpxLimits.MAX_ENCRYPTED_ENTRIES) {
                unsupported = unsupported ?: "암호 항목이 너무 많다"
                return@eachChild
            }
            val problem = b.problem()
            if (problem != null) {
                unsupported = unsupported ?: problem
                return@eachChild
            }
            out[HwpxNames.key(path)] = b.build(HwpxNames.normalize(path), size)
        }
        return OdfManifest(out, unsupported)
    }

    private class EntryBuilder {
        var checksumType: String? = null
        var checksum: ByteArray? = null
        var algorithm: String? = null
        var iv: ByteArray? = null
        var kdf: String? = null
        var salt: ByteArray? = null
        var iterations: Int? = null
        var derivedKeySize: Int? = null
        var startKeyAlgorithm: String? = null
        var startKeySize: Int? = null
        var malformed = false

        fun cipherKeyBytes(): Int? {
            val a = algorithm ?: return null
            if (!a.startsWith(AES_PREFIX, ignoreCase = true) || !a.endsWith("-cbc", ignoreCase = true)) return null
            return when (a.substring(AES_PREFIX.length).substringBefore('-')) {
                "128" -> 16
                "192" -> 24
                "256" -> 32
                else -> null
            }
        }

        /** 풀 수 없는 까닭. 풀 수 있으면 null. 문장은 진단용이다(화면은 코드로 문장을 고른다). */
        fun problem(): String? {
            if (malformed) return "암호 매니페스트의 값이 깨졌다"
            if (cipherKeyBytes() == null) return "암호 방식을 모른다"
            val k = kdf
            if (k == null || !k.endsWith(PBKDF2_SUFFIX, ignoreCase = true)) return "열쇠 유도 방식을 모른다"
            val it = iterations ?: return "반복 수가 없다"
            if (it < 1 || it > HwpxLimits.MAX_KDF_ITERATIONS) return "반복 수가 범위 밖이다"
            val s = salt
            if (s == null || s.isEmpty() || s.size > 1024) return "salt 가 없다"
            if (iv?.size != 16) return "IV 가 16 바이트가 아니다"
            val start = startAlgorithm()
            if (start == null) return "시작 열쇠 방식을 모른다"
            val dk = derivedKeySize ?: cipherKeyBytes()!!
            if (dk != cipherKeyBytes()) return "열쇠 길이가 암호 방식과 맞지 않는다"
            return null
        }

        /** 시작 열쇠의 다이제스트 이름(JCA). 적혀 있지 않으면 한컴 모델의 기본값(SHA-1). */
        fun startAlgorithm(): String? {
            val n = startKeyAlgorithm ?: return "SHA-1"
            return when {
                n.endsWith("sha256", ignoreCase = true) || n.equals("SHA256", ignoreCase = true) -> "SHA-256"
                n.endsWith("sha1", ignoreCase = true) || n.equals("SHA1", ignoreCase = true) -> "SHA-1"
                else -> null
            }
        }

        fun build(name: String, size: Long): EncryptedEntry {
            val keyBytes = cipherKeyBytes()!!
            val start = startAlgorithm()!!
            val digestBytes = if (start == "SHA-256") 32 else 20
            val ck = checksumType?.lowercase()
            val ckAlg = when {
                ck == null -> null
                "sha256" in ck && "1k" in ck -> "SHA-256"
                "sha1" in ck && "1k" in ck -> "SHA-1"
                else -> null
            }
            return EncryptedEntry(
                name = name,
                declaredSize = size,
                checksumAlgorithm = if (checksum != null) ckAlg else null,
                checksum = checksum,
                cipherKeyBytes = keyBytes,
                iv = iv!!,
                salt = salt!!,
                iterations = iterations!!,
                derivedKeyBytes = keyBytes,
                startKeyAlgorithm = start,
                startKeyBytes = (startKeySize ?: digestBytes).coerceIn(1, digestBytes),
            )
        }
    }

    private fun readEncryptionData(p: XmlPullParser, limits: ParseLimits): EntryBuilder {
        val b = EntryBuilder()
        b.checksumType = HwpxXml.attr(p, "checksum-type")
        b.checksum = decode(HwpxXml.attr(p, "checksum"), b)
        HwpxXml.eachChild(p, limits) { name ->
            when (name) {
                "algorithm" -> {
                    b.algorithm = HwpxXml.attr(p, "algorithm-name")?.trim()
                    b.iv = decode(HwpxXml.attr(p, "initialisation-vector"), b)
                }
                "key-derivation" -> {
                    b.kdf = HwpxXml.attr(p, "key-derivation-name")?.trim()
                    b.salt = decode(HwpxXml.attr(p, "salt"), b)
                    b.iterations = HwpxXml.int(p, "iteration-count")
                    b.derivedKeySize = HwpxXml.int(p, "key-size")
                }
                "start-key-generation" -> {
                    b.startKeyAlgorithm = HwpxXml.attr(p, "start-key-generation-name")?.trim()
                    b.startKeySize = HwpxXml.int(p, "key-size")
                }
            }
            HwpxXml.skip(p, limits)
        }
        return b
    }

    private fun decode(raw: String?, b: EntryBuilder): ByteArray? {
        val v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (v.length > 8192) {
            b.malformed = true
            return null
        }
        return try {
            Base64.getMimeDecoder().decode(v)
        } catch (e: IllegalArgumentException) {
            b.malformed = true
            null
        }
    }
}

/**
 * 암호 HWPX 의 항목을 푸는 것. **패키지가 닫힐 때 함께 닫힌다** — 그때 시작 열쇠·유도한 열쇠를 0 으로 덮는다.
 *
 * ## 암호를 들고 있지 않는다
 *
 * 만들 때 암호(`CharArray`)에서 **시작 열쇠만** 계산하고 암호의 바이트는 곧바로 덮는다. 암호 배열 자체는
 * 부르는 쪽(`DocViewModel`)의 것이라 건드리지 않는다(CLAUDE.md '암호를 다루는 규칙').
 *
 * ## 암호의 바이트
 *
 * UTF-8 이다(hwpxlib_ext 와 한컴 포럼의 질문이 그렇게 적는다). O16(`123456`)으로 확인한 것은 ASCII 뿐이고,
 * **한글 암호를 한글이 어떤 바이트로 넣는지는 확인하지 못했다** — 한글 암호로 잠근 실물이 없다.
 *
 * `SecretKeySpec` 은 열쇠를 안에 복사해 두고 지울 방법을 주지 않는다. 그 사본은 GC 를 기다린다(JCA 의 한계).
 *
 * ## 열쇠 유도의 총량
 *
 * 한컴은 항목마다 salt 를 새로 만들 수 있어 열쇠를 항목마다 따로 유도한다. 항목 하나의 반복 수 상한
 * ([HwpxLimits.MAX_KDF_ITERATIONS])만으로는 '항목 수 × 반복 수' 가 묶이지 않는다 — 반복 수 100만짜리 그림 천 장이면
 * 그림을 청할 때마다 1초씩, 합해 수십 분을 쓴다(그리기 쪽은 잠금을 쥔 채다). 그래서 문서 하나의 **유도 반복 수의
 * 합**을 [iterationBudget] 으로 묶고, 넘으면 그 항목을 '너무 크다' 로 끝낸다.
 */
internal class HwpxDecryptor private constructor(
    /** 다이제스트 이름 → 시작 열쇠(자르기 전). */
    private val startKeys: Map<String, ByteArray>,
    private val iterationBudget: Long,
) : AutoCloseable {

    /** 유도한 열쇠의 캐시. 한컴은 항목마다 salt 를 새로 만들지만 같은 초에 만든 것은 겹친다. */
    private val derived = HashMap<String, ByteArray>()

    /** 지금까지 유도에 쓴 반복 수의 합(캐시에서 꺼낸 것은 세지 않는다). */
    private var iterationsSpent = 0L

    /** PBKDF2 의 PRF. 검증에서 정해진다([verify]). */
    private var prf = PRF_SHA1

    private var closed = false

    /**
     * 항목 하나를 풀어 **평문**을 준다. 부르는 쪽이 다 쓴 뒤 0 으로 덮는다.
     *
     * @param cap 평문의 상한. 선언된 크기(`size`)는 믿지 않는다 — 실제로 푼 바이트를 센다.
     * @param truncate 참이면 평문이 [cap] 을 넘을 때 던지지 않고 **앞의 [cap] 바이트만** 준다(XML 파서가 제 상한에서
     *   멈추듯 앞부분은 살린다). 거짓이면 넘을 때 던진다.
     * @throws HwpxDecryptException 열쇠가 틀렸거나 항목이 깨졌다.
     * @throws ParseLimitExceededException 평문이 [cap] 을 넘는다([truncate] 가 거짓일 때), 또는 유도 총량을 넘었다.
     */
    fun decrypt(
        entry: EncryptedEntry,
        cipherText: ByteArray,
        cap: Long,
        truncate: Boolean = false,
        checkCancel: () -> Unit,
    ): ByteArray {
        check(!closed) { "닫힌 복호화기" }
        val key = keyFor(entry, prf, checkCancel)
        return decryptWith(entry, key, cipherText, cap, truncate, checkCancel)
    }

    /**
     * 받은 암호가 맞는지 항목 하나로 확인한다. PRF 는 한컴 모델의 HMAC-SHA1 을 먼저, 안 되면 HMAC-SHA256
     * 을 본다(rhwp 가 둘을 차례로 본다 — 한컴의 것은 SHA1 뿐이지만 다른 작성기가 있을 수 있다).
     * 맞은 PRF 를 이 뒤로 쓴다.
     *
     * **앞의 [VERIFY_BYTES] 만 푼다.** 검증값은 평문의 첫 1024 바이트만 재고, 틀린 열쇠는 deflate 의 첫 블록에서
     * 드러난다. 끝까지 풀면 '가장 작은 암호문' 으로 고른 항목이 압축 폭탄일 때 암호를 확인하는 것만으로 평문 상한
     * (수백 MB)을 다 쓴다.
     */
    fun verify(entry: EncryptedEntry, cipherText: ByteArray, checkCancel: () -> Unit): Boolean {
        for (candidate in listOf(PRF_SHA1, PRF_SHA256)) {
            val key = keyFor(entry, candidate, checkCancel)
            val plain = try {
                decryptWith(entry, key, cipherText, VERIFY_BYTES, truncate = true, checkCancel = checkCancel)
            } catch (e: HwpxDecryptException) {
                null
            }
            if (plain != null) {
                plain.fill(0)
                prf = candidate
                return true
            }
        }
        return false
    }

    private fun keyFor(entry: EncryptedEntry, prfName: String, checkCancel: () -> Unit): ByteArray {
        val cacheKey = buildString {
            append(prfName).append('|').append(entry.startKeyAlgorithm).append('|').append(entry.startKeyBytes)
            append('|').append(entry.iterations).append('|').append(entry.derivedKeyBytes).append('|')
            append(Base64.getEncoder().encodeToString(entry.salt))
        }
        derived[cacheKey]?.let { return it }
        val start = startKeys[entry.startKeyAlgorithm] ?: throw HwpxDecryptException(wrongKey = false)
        if (iterationsSpent + entry.iterations > iterationBudget) {
            throw ParseLimitExceededException("maxKdfIterations", "열쇠 유도 반복 수의 합이 ${iterationBudget}회를 넘는다")
        }
        iterationsSpent += entry.iterations
        val password = if (entry.startKeyBytes < start.size) start.copyOf(entry.startKeyBytes) else start
        val key = try {
            Pbkdf2.derive(prfName, password, entry.salt, entry.iterations, entry.derivedKeyBytes, checkCancel)
        } finally {
            if (password !== start) password.fill(0)
        }
        derived[cacheKey] = key
        return key
    }

    private fun decryptWith(
        entry: EncryptedEntry,
        key: ByteArray,
        cipherText: ByteArray,
        cap: Long,
        truncate: Boolean,
        checkCancel: () -> Unit,
    ): ByteArray {
        // 채움이 없으므로 16 의 배수여야 한다. 끝의 모자란 조각은 풀 수 없다 — deflate 는 그 앞에서 끝난다.
        val aligned = cipherText.size - cipherText.size % BLOCK
        if (aligned == 0) {
            if (entry.declaredSize == 0L) return ByteArray(0)
            throw HwpxDecryptException(wrongKey = false)
        }
        val deflated = try {
            val c = Cipher.getInstance("AES/CBC/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(entry.iv))
            c.doFinal(cipherText, 0, aligned)
        } catch (e: java.security.GeneralSecurityException) {
            throw HwpxDecryptException(wrongKey = false)
        }
        // 잘라 받을 때도 검증값을 잴 만큼(첫 1024 바이트)은 푼다.
        val limit = if (truncate) maxOf(cap, CHECKSUM_BYTES.toLong()) else cap
        val plain = try {
            inflate(deflated, limit, truncate, checkCancel)
        } finally {
            deflated.fill(0)
        }
        val expected = entry.checksum
        val alg = entry.checksumAlgorithm
        if (expected != null && alg != null) {
            val ok = checksumMatches(alg, plain, expected)
            if (!ok) {
                plain.fill(0)
                throw HwpxDecryptException(wrongKey = true)
            }
        }
        return plain
    }

    private fun checksumMatches(alg: String, plain: ByteArray, expected: ByteArray): Boolean {
        val md = MessageDigest.getInstance(alg)
        md.update(plain, 0, minOf(plain.size, CHECKSUM_BYTES))
        return MessageDigest.isEqual(md.digest(), expected)
    }

    override fun close() {
        if (closed) return
        closed = true
        for (k in startKeys.values) k.fill(0)
        for (k in derived.values) k.fill(0)
        derived.clear()
    }

    companion object {
        const val PRF_SHA1 = "HmacSHA1"
        const val PRF_SHA256 = "HmacSHA256"
        private const val BLOCK = 16
        private const val CHECKSUM_BYTES = 1024

        /** 암호를 확인할 때 풀어 보는 평문의 앞부분. */
        private const val VERIFY_BYTES = 64L * 1024

        /**
         * 암호에서 시작 열쇠들을 만든다. [algorithms] 는 매니페스트에 나온 다이제스트 이름들.
         * 암호의 UTF-8 바이트는 여기서만 살고 곧 덮는다.
         *
         * @param iterationBudget 이 복호화기가 열쇠 유도에 쓸 반복 수의 합(클래스 주석). 시험만 바꾼다.
         */
        fun create(
            password: CharArray,
            algorithms: Set<String>,
            iterationBudget: Long = HwpxLimits.MAX_KDF_TOTAL_ITERATIONS,
        ): HwpxDecryptor {
            val bytes = utf8(password)
            try {
                val keys = HashMap<String, ByteArray>()
                for (alg in algorithms) keys[alg] = MessageDigest.getInstance(alg).digest(bytes)
                return HwpxDecryptor(keys, iterationBudget)
            } finally {
                bytes.fill(0)
            }
        }

        /** `CharArray` 를 `String` 을 거치지 않고 UTF-8 로. 인코더의 중간 버퍼도 덮는다. */
        fun utf8(password: CharArray): ByteArray {
            val enc = Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val bb = enc.encode(CharBuffer.wrap(password))
            val out = ByteArray(bb.remaining())
            bb.get(out)
            if (bb.hasArray()) bb.array().fill(0)
            return out
        }

        /**
         * 머리 없는 deflate 를 푼다. 기억을 한 번에 잡지 않고 자라게 한다 — 선언된 크기(`size`)는
         * 공격자가 적는 값이다. 자랄 때 옛 버퍼를 덮는다(평문이다).
         *
         * @param truncate 참이면 [cap] 에 닿았을 때 던지지 않고 거기까지의 앞부분을 준다.
         */
        fun inflate(data: ByteArray, cap: Long, truncate: Boolean = false, checkCancel: () -> Unit): ByteArray {
            val inflater = Inflater(true)
            var buf = ByteArray(minOf(64 * 1024L, maxOf(cap, 16L)).toInt())
            var n = 0
            try {
                inflater.setInput(data)
                var dummyFed = false
                var rounds = 0
                while (!inflater.finished()) {
                    if (n == buf.size) {
                        if (buf.size.toLong() >= cap) {
                            if (truncate) break
                            buf.fill(0)
                            throw ParseLimitExceededException("maxSingleOutput", "암호를 푼 평문이 ${cap}바이트를 넘는다")
                        }
                        val next = minOf(buf.size.toLong() * 2, cap).toInt()
                        val grown = buf.copyOf(next)
                        buf.fill(0)
                        buf = grown
                    }
                    val r = try {
                        inflater.inflate(buf, n, buf.size - n)
                    } catch (e: DataFormatException) {
                        buf.fill(0)
                        throw HwpxDecryptException(wrongKey = true)
                    }
                    n += r
                    if (r == 0 && !inflater.finished()) {
                        // `nowrap` 은 끝에 '가짜 바이트' 하나를 더 먹어야 끝을 알 때가 있다(`Inflater` 의 문서).
                        if (inflater.needsInput() && !dummyFed) {
                            dummyFed = true
                            inflater.setInput(ByteArray(1))
                            continue
                        }
                        buf.fill(0)
                        throw HwpxDecryptException(wrongKey = true)
                    }
                    if (++rounds and 63 == 0) checkCancel()
                }
                val out = buf.copyOf(n)
                buf.fill(0)
                return out
            } finally {
                inflater.end()
            }
        }
    }
}

/**
 * PBKDF2(RFC 2898 §5.2). JCA 의 `PBKDF2WithHmacSHA1` 을 쓰지 않는 이유 — 그것은 암호를 **글자**
 * (`PBEKeySpec(char[])`)로 받는데, 한컴의 방식은 시작 열쇠(SHA-256 다이제스트, 이진 32 바이트)를 암호로
 * 넣는다. 이진 바이트는 글자로 되돌려 넣을 수 없다. RFC 6070 의 시험 값으로 맞춘다(시험 참고).
 */
internal object Pbkdf2 {

    fun derive(
        prf: String,
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int,
        checkCancel: () -> Unit,
    ): ByteArray {
        require(iterations >= 1 && keyBytes >= 1)
        val mac = Mac.getInstance(prf)
        mac.init(SecretKeySpec(password, prf))
        val hLen = mac.macLength
        val out = ByteArray(keyBytes)
        val u = ByteArray(hLen)
        val t = ByteArray(hLen)
        val blocks = (keyBytes + hLen - 1) / hLen
        try {
            for (i in 1..blocks) {
                mac.update(salt)
                mac.update(byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte()))
                mac.doFinal(u, 0)
                System.arraycopy(u, 0, t, 0, hLen)
                for (j in 2..iterations) {
                    mac.update(u)
                    mac.doFinal(u, 0)
                    for (k in 0 until hLen) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
                    if (j and 4095 == 0) checkCancel()
                }
                val offset = (i - 1) * hLen
                System.arraycopy(t, 0, out, offset, minOf(hLen, keyBytes - offset))
            }
            return out
        } catch (e: Throwable) {
            out.fill(0)
            throw e
        } finally {
            u.fill(0)
            t.fill(0)
        }
    }
}
