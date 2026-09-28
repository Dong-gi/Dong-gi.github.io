package io.github.donggi.iroiroviewer.docview.pdf.crypt

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PDF **표준 보안 처리기**(ISO 32000-1 §7.6.3, ISO 32000-2 §7.6.4). 암호로 파일 키를 얻고,
 * 그 키로 객체 하나하나의 문자열·스트림을 푼다.
 *
 * ## 이 앱이 이것을 직접 쓰는 이유
 *
 * 플랫폼이 암호를 받는 길(`PdfRenderer(pfd, LoadParams)`)은 **API 35 부터**다. 이 앱의 대상인
 * 안드로이드 12 폰은 거기 닿지 않고(SDK 확장 S=1, 실측), 그 폰에서 암호 PDF 를 열려면
 * 우리가 풀어서 pdfium 에 **평문 PDF 를 넘기는** 수밖에 없다.
 *
 * 알고리즘이 **공개 명세**라는 것이 이 일을 할 수 있는 근거다 — 방법이 공개되지 않은
 * 암호(HWP 문서 암호 등)는 이 앱이 열지 않는다.
 *
 * ## 지원하는 것
 *
 * | V | R | 암호 | 파일 키 |
 * |---|---|---|---|
 * | 1 | 2 | RC4 40비트 | Algorithm 2 |
 * | 2 | 3 | RC4 40~128비트 | Algorithm 2 (MD5 50회) |
 * | 4 | 4 | 암호 필터(`/V2` RC4 · `/AESV2` AES-128) | Algorithm 2 |
 * | 5 | 5 | `/AESV3` AES-256 | SHA-256 한 번(Adobe 확장 3, 폐기됐지만 파일은 남아 있다) |
 * | 5 | 6 | `/AESV3` AES-256 | Algorithm 2.B(SHA-2 반복) |
 *
 * **V3 과 공개 키 처리기(`/Adobe.PubSec`)는 열지 않는다.** V3 은 명세가 '공개되지 않은
 * 알고리즘' 이라고 적고, 공개 키 처리기는 암호가 아니라 **받는 사람의 인증서 개인 키**가 필요하다.
 *
 * ## RC4 를 직접 쓴다
 *
 * 안드로이드의 `Cipher.getInstance("ARCFOUR")` 는 BouncyCastle 제공자에서 나오는데, 그 제공자는
 * 판이 올라갈 때마다 잘려 나가고 있다. RC4 는 20줄짜리 알고리즘이라 **플랫폼이 언젠가 뺄
 * 수도 있는 것에 기대지 않는다.** MD5·SHA-2·AES 는 Conscrypt 가 주고, 그쪽은 빠질 일이 없다.
 */
internal class PdfSecurity private constructor(
    val revision: Int,
    private val fileKey: ByteArray,
    private val stringMethod: Method,
    private val streamMethod: Method,
    private val embeddedMethod: Method,
    private val cryptFilters: Map<String, Method>,
    val encryptMetadata: Boolean,
) {

    /** 한 종류의 암호 방식. */
    enum class Method { IDENTITY, RC4, AES128, AES256 }

    /** 무엇을 푸는가. 방식이 이것으로 갈린다(`/StrF`·`/StmF`·`/EFF`). */
    enum class Kind { STRING, STREAM, EMBEDDED_FILE }

    /** 이 종류에 쓰이는 방식. */
    fun methodFor(kind: Kind): Method = when (kind) {
        Kind.STRING -> stringMethod
        Kind.STREAM -> streamMethod
        Kind.EMBEDDED_FILE -> embeddedMethod
    }

    /** 스트림이 `/Filter /Crypt` 로 이름을 댄 암호 필터. 모르는 이름이면 null. */
    fun methodByName(name: String): Method? =
        if (name == "Identity") Method.IDENTITY else cryptFilters[name]

    /**
     * 객체 하나의 키(Algorithm 1). R5·R6 은 파일 키를 그대로 쓴다.
     */
    fun objectKey(num: Int, gen: Int, method: Method): ByteArray {
        if (method == Method.AES256) return fileKey
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(fileKey)
        md5.update(byteArrayOf(num.toByte(), (num shr 8).toByte(), (num shr 16).toByte()))
        md5.update(byteArrayOf(gen.toByte(), (gen shr 8).toByte()))
        if (method == Method.AES128) md5.update(SALT)
        val h = md5.digest()
        return h.copyOf(minOf(fileKey.size + 5, 16))
    }

    /** 바이트를 한꺼번에 푼다. 문자열과 작은 스트림에 쓴다. */
    fun decrypt(num: Int, gen: Int, method: Method, data: ByteArray): ByteArray {
        if (method == Method.IDENTITY) return data
        val key = objectKey(num, gen, method)
        val d = decryptor(method, key)
        val a = d.update(data, 0, data.size)
        val b = d.finish()
        return if (b.isEmpty()) a else a + b
    }

    /**
     * 스트림처럼 조금씩 푸는 것. 큰 그림 스트림을 통째로 메모리에 올리지 않으려고 둔다.
     */
    fun decryptor(method: Method, key: ByteArray): StreamDecryptor = when (method) {
        Method.IDENTITY -> IdentityDecryptor
        Method.RC4 -> Rc4Decryptor(key)
        Method.AES128, Method.AES256 -> AesCbcDecryptor(key)
    }

    /** 파일 키를 지운다. 다 쓰고 부른다. */
    fun wipe() {
        fileKey.fill(0)
    }

    companion object {

        /** Algorithm 2 가 암호 뒤에 채워 넣는 32바이트(명세 고정값). */
        private val PAD = byteArrayOf(
            0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41,
            0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(),
            0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A,
        )

        /** AES 객체 키에 덧붙이는 소금. 글자 그대로 `sAlT` 다. */
        private val SALT = byteArrayOf(0x73, 0x41, 0x6C, 0x54)

        /**
         * `/Encrypt` 사전과 암호로 보안 처리기를 세운다.
         *
         * **여러 인코딩으로 시도한다.** R2~R4 의 암호는 명세상 PDFDocEncoding(ASCII 와 거의 같다)
         * 이지만, 한글 암호를 받은 도구들은 제각각 UTF-8·CP949 로 넣는다. 한 가지로만 시도하면
         * **맞는 암호를 넣고도 틀렸다는 말을 듣는다.** R5·R6 은 명세가 UTF-8(SASLprep)이라
         * 그것부터 본다.
         *
         * 사용자 암호로 먼저, 안 되면 소유자 암호로 본다 — 사람은 자기가 받은 암호가 어느
         * 쪽인지 모른다.
         *
         * @param id 트레일러 `/ID` 의 첫 문자열. 없으면 빈 배열.
         * @return 암호가 맞지 않으면 null.
         * @throws UnsupportedEncryption 이 앱이 풀지 않는 방식.
         */
        fun open(encrypt: PdfDict, id: ByteArray, password: CharArray): PdfSecurity? {
            val filter = encrypt.name("Filter")
            if (filter != "Standard") throw UnsupportedEncryption("보안 처리기 $filter")
            val v = encrypt.int("V") ?: 0
            val r = encrypt.int("R") ?: throw UnsupportedEncryption("R 이 없다")
            if (v !in setOf(1, 2, 4, 5)) throw UnsupportedEncryption("V $v")
            if (r !in 2..6) throw UnsupportedEncryption("R $r")

            val o = (encrypt["O"] as? PdfStr)?.bytes ?: throw UnsupportedEncryption("O 가 없다")
            val u = (encrypt["U"] as? PdfStr)?.bytes ?: throw UnsupportedEncryption("U 가 없다")
            val p = (encrypt["P"] as? PdfNum)?.long?.toInt() ?: 0
            val encryptMetadata = (encrypt["EncryptMetadata"] as? PdfBool)?.value ?: true

            // 암호 방식. V4·V5 는 암호 필터 사전을 본다.
            val filters = HashMap<String, Method>()
            // V1 은 40비트, V2 는 `/Length`(없으면 40). V4 는 아래에서 암호 필터가 정한다.
            var keyBits = encrypt.int("Length") ?: 40
            val stmF: Method
            val strF: Method
            val effM: Method
            if (v >= 4) {
                val cf = encrypt["CF"] as? PdfDict
                val lengths = HashMap<String, Int>()
                if (cf != null) {
                    for ((name, value) in cf.map) {
                        val d = value as? PdfDict ?: continue
                        val method = when (d.name("CFM")) {
                            "V2" -> Method.RC4
                            "AESV2" -> Method.AES128
                            "AESV3" -> Method.AES256
                            null, "None" -> Method.IDENTITY
                            else -> throw UnsupportedEncryption("CFM ${d.name("CFM")}")
                        }
                        filters[name] = method
                        // 바이트로 적는 도구와 비트로 적는 도구가 섞여 있다 — 40 보다 작으면 바이트다.
                        val len = d.int("Length")
                        if (len != null && len > 0) lengths[name] = if (len < 40) len * 8 else len
                    }
                }
                fun byName(key: String, default: Method): Method {
                    val n = encrypt.name(key) ?: return default
                    return if (n == "Identity") Method.IDENTITY
                    else filters[n] ?: throw UnsupportedEncryption("알 수 없는 암호 필터 $n")
                }
                stmF = byName("StmF", Method.IDENTITY)
                strF = byName("StrF", Method.IDENTITY)
                effM = byName("EFF", stmF)
                // **V4 의 키 길이는 `/StmF` 가 가리키는 필터가 정하고, 없으면 128비트다.**
                // 명세는 V4 의 최상위 `/Length` 를 요구하지 않고(표 20) 필터의 `/Length` 도
                // 선택이다(표 25). 기본값을 40 으로 두면 `/Length` 를 안 적은 정상 파일에서 맞는
                // 암호가 '틀렸다' 가 된다(적대적 검토가 잡았다). AESV2 는 명세상 128비트뿐이라
                // 적힌 값과 무관하게 128 이다. pdfium·pdf.js·qpdf 가 모두 128 을 기본으로 쓴다.
                if (v == 4) {
                    val named = encrypt.name("StmF")?.takeIf { it != "Identity" }
                        ?: encrypt.name("StrF")?.takeIf { it != "Identity" }
                    keyBits = when {
                        named != null && filters[named] == Method.AES128 -> 128
                        named != null -> lengths[named] ?: 128
                        else -> 128
                    }
                }
            } else {
                stmF = Method.RC4
                strF = Method.RC4
                effM = Method.RC4
            }

            val key = if (r >= 5) {
                authenticate56(r, o, u, encrypt, password)
            } else {
                val n = (keyBits / 8).coerceIn(5, 16).let { if (r == 2) 5 else it }
                authenticate234(r, n, o, u, p, id, encryptMetadata, password)
            } ?: return null

            return PdfSecurity(r, key, strF, stmF, effM, filters, encryptMetadata)
        }

        // ---- R2·R3·R4 -------------------------------------------------------------

        private fun authenticate234(
            r: Int,
            n: Int,
            o: ByteArray,
            u: ByteArray,
            p: Int,
            id: ByteArray,
            encryptMetadata: Boolean,
            password: CharArray,
        ): ByteArray? {
            for (candidate in legacyCandidates(password)) {
                try {
                    // 사용자 암호로
                    val asUser = fileKey234(r, n, pad(candidate), o, p, id, encryptMetadata)
                    if (checkUser234(r, asUser, u, id)) return asUser
                    asUser.fill(0)

                    // 소유자 암호로 — O 를 풀면 사용자 암호(채운 것)가 나온다(Algorithm 7)
                    val userPadded = ownerToUser234(r, n, candidate, o)
                    val asOwner = fileKey234(r, n, userPadded, o, p, id, encryptMetadata)
                    userPadded.fill(0)
                    if (checkUser234(r, asOwner, u, id)) return asOwner
                    asOwner.fill(0)
                } finally {
                    candidate.fill(0)
                }
            }
            return null
        }

        /** Algorithm 2. */
        private fun fileKey234(
            r: Int,
            n: Int,
            padded: ByteArray,
            o: ByteArray,
            p: Int,
            id: ByteArray,
            encryptMetadata: Boolean,
        ): ByteArray {
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(padded)
            md5.update(o.copyOf(32))
            md5.update(byteArrayOf(p.toByte(), (p shr 8).toByte(), (p shr 16).toByte(), (p shr 24).toByte()))
            md5.update(id)
            if (r >= 4 && !encryptMetadata) md5.update(byteArrayOf(-1, -1, -1, -1))
            var h = md5.digest()
            if (r >= 3) {
                repeat(50) { h = MessageDigest.getInstance("MD5").digest(h.copyOf(n)) }
            }
            return h.copyOf(n)
        }

        /** Algorithm 4(R2)·5(R3·R4). */
        private fun checkUser234(r: Int, key: ByteArray, u: ByteArray, id: ByteArray): Boolean {
            return if (r == 2) {
                val x = rc4(key, PAD)
                MessageDigest.isEqual(x, u.copyOf(32))
            } else {
                val md5 = MessageDigest.getInstance("MD5")
                md5.update(PAD)
                md5.update(id)
                var x = rc4(key, md5.digest())
                for (i in 1..19) x = rc4(xorKey(key, i), x)
                MessageDigest.isEqual(x, u.copyOf(16))
            }
        }

        /** Algorithm 7 의 앞 절반 — 소유자 암호로 사용자 암호(채운 것)를 되찾는다. */
        private fun ownerToUser234(r: Int, n: Int, owner: ByteArray, o: ByteArray): ByteArray {
            var h = MessageDigest.getInstance("MD5").digest(pad(owner))
            if (r >= 3) repeat(50) { h = MessageDigest.getInstance("MD5").digest(h) }
            val key = h.copyOf(if (r == 2) 5 else n)
            var x = o.copyOf(32)
            if (r == 2) {
                x = rc4(key, x)
            } else {
                for (i in 19 downTo 0) x = rc4(xorKey(key, i), x)
            }
            return x
        }

        private fun pad(pw: ByteArray): ByteArray {
            val out = ByteArray(32)
            val n = minOf(pw.size, 32)
            System.arraycopy(pw, 0, out, 0, n)
            System.arraycopy(PAD, 0, out, n, 32 - n)
            return out
        }

        private fun xorKey(key: ByteArray, i: Int): ByteArray =
            ByteArray(key.size) { (key[it].toInt() xor i).toByte() }

        // ---- R5·R6 ----------------------------------------------------------------

        private fun authenticate56(
            r: Int,
            o: ByteArray,
            u: ByteArray,
            encrypt: PdfDict,
            password: CharArray,
        ): ByteArray? {
            if (o.size < 48 || u.size < 48) throw UnsupportedEncryption("O·U 가 짧다")
            val oe = (encrypt["OE"] as? PdfStr)?.bytes
            val ue = (encrypt["UE"] as? PdfStr)?.bytes
            val u48 = u.copyOf(48)
            for (candidate in modernCandidates(password)) {
                try {
                    // 사용자: hash(암호, 검증 소금) == U[0:32]
                    if (MessageDigest.isEqual(hash56(r, candidate, u48, 32, null), u48.copyOf(32))) {
                        if (ue == null || ue.size < 32) throw UnsupportedEncryption("UE 가 없다")
                        val inter = hash56(r, candidate, u48, 40, null)
                        return aes256CbcNoPad(inter, ZERO_IV, ue.copyOf(32)).also { inter.fill(0) }
                    }
                    // 소유자: hash(암호, 검증 소금, U[0:48]) == O[0:32]
                    if (MessageDigest.isEqual(hash56(r, candidate, o, 32, u48), o.copyOf(32))) {
                        if (oe == null || oe.size < 32) throw UnsupportedEncryption("OE 가 없다")
                        val inter = hash56(r, candidate, o, 40, u48)
                        return aes256CbcNoPad(inter, ZERO_IV, oe.copyOf(32)).also { inter.fill(0) }
                    }
                } finally {
                    candidate.fill(0)
                }
            }
            return null
        }

        /**
         * R5 는 SHA-256 한 번, R6 은 Algorithm 2.B.
         *
         * @param saltAt 소금의 자리(32 = 검증 소금, 40 = 키 소금). 소금은 8바이트다.
         */
        private fun hash56(r: Int, pw: ByteArray, block: ByteArray, saltAt: Int, udata: ByteArray?): ByteArray {
            val salt = block.copyOfRange(saltAt, saltAt + 8)
            return if (r == 5) {
                val sha = MessageDigest.getInstance("SHA-256")
                sha.update(pw)
                sha.update(salt)
                if (udata != null) sha.update(udata)
                sha.digest()
            } else {
                hash2B(pw, salt, udata ?: ByteArray(0))
            }
        }

        /**
         * ISO 32000-2 Algorithm 2.B.
         *
         * 끝나는 조건이 **라운드 번호에 달린 부등식**이라 틀리기 쉽다 — 64회 이상 돌고, 그 뒤로는
         * E 의 마지막 바이트가 (라운드 − 32) 이하가 될 때 멈춘다. 라운드는 **올린 뒤의 값**으로
         * 견준다(pdf.js·qpdf 와 같다).
         */
        internal fun hash2B(pw: ByteArray, salt: ByteArray, udata: ByteArray): ByteArray {
            var k = MessageDigest.getInstance("SHA-256").run {
                update(pw); update(salt); update(udata); digest()
            }
            var round = 0
            while (true) {
                val seq = ByteArray(pw.size + k.size + udata.size)
                System.arraycopy(pw, 0, seq, 0, pw.size)
                System.arraycopy(k, 0, seq, pw.size, k.size)
                System.arraycopy(udata, 0, seq, pw.size + k.size, udata.size)
                val k1 = ByteArray(seq.size * 64)
                for (i in 0 until 64) System.arraycopy(seq, 0, k1, i * seq.size, seq.size)

                // **암호화**다(복호화가 아니다). K 의 앞 16바이트가 키, 뒤 16바이트가 IV.
                val e = Cipher.getInstance("AES/CBC/NoPadding").run {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.copyOf(16), "AES"), IvParameterSpec(k.copyOfRange(16, 32)))
                    doFinal(k1)
                }
                // 앞 16바이트를 큰 정수로 보고 3 으로 나눈 나머지 = 바이트 합을 3 으로 나눈
                // 나머지(256 ≡ 1 (mod 3) 이므로).
                var sum = 0
                for (i in 0 until 16) sum += e[i].toInt() and 0xFF
                k = MessageDigest.getInstance(
                    when (sum % 3) {
                        0 -> "SHA-256"
                        1 -> "SHA-384"
                        else -> "SHA-512"
                    }
                ).digest(e)
                round++
                if (round >= 64 && (e[e.size - 1].toInt() and 0xFF) <= round - 32) break
                if (round > 2048) break // 명세상 도달하지 않는다. 엉뚱한 입력의 무한 루프만 막는다
            }
            return k.copyOf(32)
        }

        // 매개변수 이름을 `iv` 로 두지 않는다 — 지역 이름이 이기긴 하지만, 수신 객체의
        // `Cipher.getIV()` 와 같은 이름이라 읽는 사람이 헷갈린다(아래 AesCbcDecryptor 참고).
        private fun aes256CbcNoPad(key: ByteArray, initVector: ByteArray, data: ByteArray): ByteArray =
            Cipher.getInstance("AES/CBC/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(initVector))
                doFinal(data)
            }

        private val ZERO_IV = ByteArray(16)

        // ---- 암호의 바이트 후보 ----------------------------------------------------

        /**
         * R2~R4: PDFDocEncoding(=ASCII 범위에서 latin-1) → UTF-8 → CP949. 32바이트에서 자른다.
         *
         * (UTF-16BE 는 대 보지 않는다. 그렇게 적는 도구를 확인하지 못했다 — 확인하지 못한 후보를
         * 늘리면 틀린 암호가 우연히 맞을 확률만 는다.)
         *
         * 겹치는 것은 한 번만 시도한다(ASCII 암호는 앞의 셋이 전부 같다).
         */
        private fun legacyCandidates(pw: CharArray): List<ByteArray> {
            val out = ArrayList<ByteArray>()
            fun add(b: ByteArray?) {
                if (b == null) return
                val t = b.copyOf(minOf(b.size, 32))
                b.fill(0)
                if (out.none { it.contentEquals(t) }) out.add(t) else t.fill(0)
            }
            add(encode(pw, Charsets.ISO_8859_1))
            add(encode(pw, Charsets.UTF_8))
            add(runCatching { encode(pw, Charset.forName("MS949")) }.getOrNull())
            return out
        }

        /**
         * R5·R6: SASLprep(RFC 4013) 한 UTF-8 → 그대로의 UTF-8 → latin-1. 127바이트에서 자른다.
         *
         * SASLprep 을 **전부** 구현하지 않는다. 우리가 하는 것은 그 가운데 실제로 결과를 바꾸는
         * 둘 — 여러 공백 문자를 보통 공백으로 모으고, NFKC 로 정규화하는 것 — 이고, 금지 문자
         * 검사는 하지 않는다(거절할 이유가 없다. 틀리면 '맞지 않다' 로 끝난다).
         */
        private fun modernCandidates(pw: CharArray): List<ByteArray> {
            val out = ArrayList<ByteArray>()
            fun add(b: ByteArray?) {
                if (b == null) return
                val t = b.copyOf(minOf(b.size, 127))
                b.fill(0)
                if (out.none { it.contentEquals(t) }) out.add(t) else t.fill(0)
            }
            val prepped = saslPrep(pw)
            add(encode(prepped, Charsets.UTF_8))
            prepped.fill('\u0000')
            add(encode(pw, Charsets.UTF_8))
            add(encode(pw, Charsets.ISO_8859_1))
            return out
        }

        private fun saslPrep(pw: CharArray): CharArray {
            val mapped = StringBuilder(pw.size)
            for (c in pw) {
                when {
                    // '아무것도 아닌 것으로 바꾸는' 글자(RFC 3454 B.1)
                    c == '­' || c == '͏' || c == '᠆' || c in '᠋'..'᠍' ||
                        c in '​'..'‍' || c == '⁠' || c in '︀'..'️' ||
                        c == '\uFEFF' -> Unit
                    // 공백 계열(RFC 3454 C.1.2) → 보통 공백
                    c == ' ' || c == ' ' || c in ' '..' ' || c == ' ' ||
                        c == ' ' || c == '　' -> mapped.append(' ')
                    else -> mapped.append(c)
                }
            }
            val norm = Normalizer.normalize(mapped, Normalizer.Form.NFKC)
            mapped.setLength(0)
            return norm.toCharArray()
        }

        /** 인코딩할 수 없는 글자가 있으면 null — 그 후보는 건너뛴다. */
        private fun encode(pw: CharArray, cs: Charset): ByteArray? {
            val enc = cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                val bb: ByteBuffer = enc.encode(CharBuffer.wrap(pw))
                val out = ByteArray(bb.remaining())
                bb.get(out)
                if (bb.hasArray()) bb.array().fill(0)
                out
            } catch (e: java.nio.charset.CharacterCodingException) {
                null
            }
        }

        /** RC4 한 번. 짧은 것(키 검사)에 쓴다. */
        internal fun rc4(key: ByteArray, data: ByteArray): ByteArray {
            val d = Rc4Decryptor(key)
            return d.update(data, 0, data.size)
        }
    }
}

/** 이 앱이 풀지 않는 방식. 화면이 '지원하지 않는 방식' 으로 말한다. */
internal class UnsupportedEncryption(message: String) : Exception(message)

/** 조금씩 푸는 것. */
internal interface StreamDecryptor {
    fun update(data: ByteArray, off: Int, len: Int): ByteArray

    /** 남은 것(AES 의 마지막 블록에서 채움을 뗀 것). */
    fun finish(): ByteArray
}

internal object IdentityDecryptor : StreamDecryptor {
    override fun update(data: ByteArray, off: Int, len: Int) = data.copyOfRange(off, off + len)
    override fun finish() = ByteArray(0)
}

/** RC4. 키 스케줄 뒤에는 바이트 하나에 연산 몇 개뿐이다. */
internal class Rc4Decryptor(key: ByteArray) : StreamDecryptor {
    private val s = IntArray(256) { it }
    private var i = 0
    private var j = 0

    init {
        if (key.isNotEmpty()) {
            var jj = 0
            for (ii in 0 until 256) {
                jj = (jj + s[ii] + (key[ii % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[ii]
                s[ii] = s[jj]
                s[jj] = t
            }
        }
    }

    override fun update(data: ByteArray, off: Int, len: Int): ByteArray {
        val out = ByteArray(len)
        for (k in 0 until len) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val t = s[i]
            s[i] = s[j]
            s[j] = t
            out[k] = (data[off + k].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
        }
        return out
    }

    override fun finish() = ByteArray(0)
}

/**
 * AES-CBC. **앞 16바이트가 IV** 이고 뒤에 PKCS#7 채움이 붙는다(명세 7.6.3.2).
 *
 * `NoPadding` 으로 풀고 채움을 **우리가** 뗀다. 플랫폼의 `PKCS5Padding` 은 채움이 틀리면
 * 예외를 던지는데, 실물 PDF 에는 채움을 잘못 만든 것이 섞여 있다 — 그 한 스트림 때문에 문서
 * 전체를 못 여는 것보다, 채움이 이상하면 **떼지 않고 두는** 편이 낫다(pdf.js 도 그렇게 한다).
 *
 * 마지막 블록은 채움을 알 수 없으므로 언제나 **한 블록을 쥐고 있다가** [finish] 에서 내준다.
 */
internal class AesCbcDecryptor(private val key: ByteArray) : StreamDecryptor {
    private var cipher: Cipher? = null
    // `iv` 로 부르지 마라 — 아래 `Cipher.apply { }` 안에서 그 이름은 `Cipher.getIV()` 로
    // 풀리고, 초기화 전의 그 값은 null 이다(시험에서 실제로 NPE 가 났다).
    private val ivBytes = ByteArray(16)
    private var ivFill = 0
    private val pending = java.io.ByteArrayOutputStream()

    override fun update(data: ByteArray, off: Int, len: Int): ByteArray {
        var p = off
        val end = off + len
        // IV 를 먼저 모은다.
        while (ivFill < 16 && p < end) ivBytes[ivFill++] = data[p++]
        if (ivFill < 16) return ByteArray(0)
        if (cipher == null) {
            cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivBytes))
            }
        }
        pending.write(data, p, end - p)
        val all = pending.toByteArray()
        // 블록 단위로 자르되 마지막 온전한 블록 하나는 남겨 둔다(채움이 거기 있다).
        val whole = all.size / 16 * 16
        val give = if (whole >= 16) whole - 16 else 0
        pending.reset()
        pending.write(all, give, all.size - give)
        if (give == 0) return ByteArray(0)
        return cipher!!.update(all, 0, give) ?: ByteArray(0)
    }

    override fun finish(): ByteArray {
        val c = cipher ?: return ByteArray(0) // IV 조차 없었다 — 빈 값
        val rest = pending.toByteArray()
        val whole = rest.size / 16 * 16
        // 16 의 배수가 아닌 꼬리는 잘린 파일이다. 온전한 블록까지만 푼다.
        val plain = if (whole > 0) (c.update(rest, 0, whole) ?: ByteArray(0)) else ByteArray(0)
        runCatching { c.doFinal() }
        if (plain.isEmpty()) return plain
        val padLen = plain[plain.size - 1].toInt() and 0xFF
        val ok = padLen in 1..16 && padLen <= plain.size &&
            (plain.size - padLen until plain.size).all { (plain[it].toInt() and 0xFF) == padLen }
        return if (ok) plain.copyOf(plain.size - padLen) else plain
    }
}
