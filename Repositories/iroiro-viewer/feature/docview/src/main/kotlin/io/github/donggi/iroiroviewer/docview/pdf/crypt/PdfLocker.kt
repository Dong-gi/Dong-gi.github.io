package io.github.donggi.iroiroviewer.docview.pdf.crypt

import java.net.IDN
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PDF 를 **AES-256 으로 잠그는** 규칙 — ISO 32000-2 §7.6.4 의 표준 보안 처리기 R6(`/V 5 /R 6`, 암호 필터 `/AESV3`). 잠그는 다시 쓰기는
 * [PdfDecryptor.lock] 이 한다(푸는 다시 쓰기와 같은 길을 반대로) — 여기는 열쇠와 `/Encrypt` 사전과 값 하나하나를 잠그는 일이다.
 *
 * ## 정한 것
 *
 * | 무엇 | 값 | 까닭 |
 * |---|---|---|
 * | 방식 | R6 · AES-256-CBC · 문자열과 스트림 모두 `/StdCF` | PDF 2.0 의 방식이고 지금 쓰는 뷰어(pdfium·pdf.js·qpdf·Acrobat)가 다 연다. R5 는 폐기된 확장이고 R2~R4 의 RC4·AES-128 은 약하다 |
 * | 권한 `/P` | −4 — 모든 권한 | 명세(표 22)의 규칙대로 비트 1·2 는 0, 나머지(예약 비트 7·8·13~32 포함)는 1. 이 기능은 '여는 암호' 다 — 권한 제한은 사용자가 고른 적이 없다 |
 * | 소유자 암호 | 사용자 암호와 다른 **무작위 32바이트**(보이지 않고 쓰고 덮는다) | 소유자 암호는 권한을 바꾸는 암호인데 권한 제한이 없으니 잃는 것이 없다. 사용자 암호와 같게 두면 그 암호를 아는 사람이 '소유자' 로 열려 도구에 따라 권한 경고 없이 다시 쓸 수 있다 — 쓸모없는 문을 하나 더 여는 셈이다 |
 * | 메타데이터 | 함께 잠근다(`/EncryptMetadata true`) | 기본값이다. 문서 정보(제목·저자)가 평문으로 남지 않는다 |
 * | 파일 키 | `SecureRandom` 32바이트 | 명세(Algorithm 8): 무작위여야 한다 |
 * | IV | 문자열·스트림마다 `SecureRandom` 16바이트 | 명세 7.6.3.2 |
 * | `/ID` | 있으면 그대로, 없으면 새로(무작위 16바이트 둘) | R6 은 열쇠에 `/ID` 를 쓰지 않지만 명세가 암호 문서에 요구한다 |
 *
 * **암호의 바이트** — SASLprep 한 뒤 UTF-8, **127바이트까지**([MAX_PASSWORD_BYTES]). 명세는 넘는 것을 잘라 쓰라고 하는데(여는 쪽도 자른다 —
 * [PdfSecurity] 의 후보), 잠그는 쪽은 자르지 않고 **거절한다** — 자르면 128바이트 뒤를 다르게 친 암호로도 열린다. 사용자가 모르는 동치를
 * 만들지 않는다. 한글은 한 글자 3바이트라 42자까지다.
 *
 * **SASLprep 이 바꾸는 암호도 거절한다**([PasswordProblem.UNPORTABLE]) — 한글 자판의 낱자(`ㅂ` U+3142 → NFKC 로 U+1107), 전각 글자, U+3000 같은
 * 공백, 폭 없는 글자. 명세대로 바꿔 잠그면 바꾸지 않고 해시하는 앱(qpdf 12 — 독립 검토가 재현했다)이 같은 암호로 열지 못한다. 그리고 SASLprep 이
 * 금하는 글자(제어·사용자 정의·서식·짝 없는 서로게이트·비문자)와 오른쪽에서 왼쪽으로 쓰는 글자(양방향 규칙)도 — 앱마다 다르게 다룬다.
 *
 * **오라클이 우리가 아니다** — qpdf(pikepdf)가 잠근 표본(`pdfcrypt/qpdf-r6.pdf`)의 소금과 그 파일 키로 우리가 다시 셈한 `/U`·`/O`·`/UE`·`/OE`·
 * `/Perms` 가 바이트로 같다(`PdfLockerTest`). 우리가 잠근 결과는 qpdf·pypdf 가 그 암호로만 연다(`samples-local/corpus/tools/verify_pdf_lock.py`).
 */
internal object PdfLocker {

    /** R6 이 쓰는 암호 바이트의 상한(명세 — 넘으면 잘린다. 우리는 넘으면 거절한다). */
    const val MAX_PASSWORD_BYTES = 127

    /** 모든 권한(명세 표 22 — 비트 1·2 만 0). */
    const val ALL_PERMISSIONS = -4

    enum class PasswordProblem {
        /** 비었다. */
        EMPTY,

        /** 127바이트를 넘는다(SASLprep 한 UTF-8 로). */
        TOO_LONG,

        /** 바이트로 옮길 수 없는 글자(짝 없는 서로게이트)가 있다. */
        UNENCODABLE,

        /** 다른 PDF 앱이 다르게 읽는 글자가 있다 — SASLprep 이 바꾸거나 금하는 글자, 오른쪽에서 왼쪽으로 쓰는 글자(머리말). */
        UNPORTABLE,
    }

    /** 새 암호로 쓸 수 없는 까닭. 쓸 수 있으면 null. 받은 배열은 덮지 않는다. */
    fun problemOf(password: CharArray): PasswordProblem? {
        if (password.isEmpty()) return PasswordProblem.EMPTY
        val bytes = PdfSecurity.r6PasswordBytes(password) ?: return PasswordProblem.UNENCODABLE
        try {
            return when {
                !portable(password) -> PasswordProblem.UNPORTABLE
                bytes.size > MAX_PASSWORD_BYTES -> PasswordProblem.TOO_LONG
                else -> null
            }
        } finally {
            bytes.fill(0)
        }
    }

    /** 어느 앱에서나 같은 바이트로 읽히는가(머리말) — 금하는 글자가 없고 SASLprep 이 바꾸지 않는다. */
    private fun portable(pw: CharArray): Boolean {
        var i = 0
        while (i < pw.size) {
            val cp = Character.codePointAt(pw, i)
            i += Character.charCount(cp)
            when (Character.getType(cp).toByte()) {
                Character.CONTROL, Character.PRIVATE_USE, Character.SURROGATE, Character.UNASSIGNED, Character.FORMAT -> return false
            }
            if (cp in 0xFDD0..0xFDEF || (cp and 0xFFFE) == 0xFFFE) return false
            // SASLprep 이 금하는데 위 갈래가 놓치는 것 — 줄·문단 나눔(C.2.2), 대체 글자(C.2.2·C.6), 한자 짜임 기호(C.7).
            if (cp == 0x2028 || cp == 0x2029 || cp == 0xFFFC || cp == 0xFFFD || cp in 0x2FF0..0x2FFB) return false
            // Unicode 3.2 에 없던 글자(이모지 등) — SASLprep 의 표는 3.2 이고, 여는 앱이 그것을 어떻게 다루는지 모른다(Acrobat 미확인).
            if (cp > 0x7E && !inUnicode32(cp)) return false
            when (Character.getDirectionality(cp)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return false
            }
        }
        return PdfSecurity.isSaslPrepStable(pw)
    }

    /**
     * 그 글자가 Unicode 3.2 에 있던 것인가 — nameprep(RFC 3491, SASLprep 과 같은 StringPrep 의 3.2 표)이 '배정되지 않은 글자'
     * (표 A.1)로 거절하지 않는가. `java.net.IDN` 은 JDK 와 안드로이드(ICU 의 IDNA2003)가 함께 그 표로 한다 — 새 의존 없이 3.2 를 묻는 길이다.
     * nameprep 이 금하는 글자도 던지는데, 그것은 SASLprep 도 금한다(부분집합). ASCII 는 부르지 않는다(모두 3.2 에 있다).
     */
    private fun inUnicode32(cp: Int): Boolean = try {
        IDN.toASCII(String(Character.toChars(cp)), 0)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    /** `/Encrypt` 사전의 다섯 값(Algorithm 8·9·10). */
    class R6Values(val o: ByteArray, val u: ByteArray, val oe: ByteArray, val ue: ByteArray, val perms: ByteArray)

    /**
     * 다섯 값을 셈한다 — 무작위를 밖에서 받는 순수한 함수다(시험이 오라클의 소금으로 같은 값을 다시 만든다).
     *
     * @param user 사용자 암호의 바이트(SASLprep·UTF-8, 127 이하).
     * @param owner 소유자 암호의 바이트(127 이하).
     * @param fileKey 파일 키 32바이트.
     * @param salts 소금 32바이트 — 사용자 검증·사용자 열쇠·소유자 검증·소유자 열쇠 소금이 8바이트씩 차례로.
     * @param permsTail `/Perms` 의 마지막 4바이트(무작위).
     */
    fun r6Values(
        user: ByteArray,
        owner: ByteArray,
        fileKey: ByteArray,
        salts: ByteArray,
        permsTail: ByteArray,
        p: Int = ALL_PERMISSIONS,
        encryptMetadata: Boolean = true,
    ): R6Values {
        require(fileKey.size == 32 && salts.size == 32 && permsTail.size == 4)
        require(user.size <= MAX_PASSWORD_BYTES && owner.size <= MAX_PASSWORD_BYTES)
        // Algorithm 8 — U = hash(암호, 사용자 검증 소금) ‖ 검증 소금 ‖ 열쇠 소금, UE = AES-256(hash(암호, 열쇠 소금), IV 0, 채움 없음)(파일 키).
        val userValidation = salts.copyOfRange(0, 8)
        val userKeySalt = salts.copyOfRange(8, 16)
        val u = PdfSecurity.hash2B(user, userValidation, ByteArray(0)) + userValidation + userKeySalt
        val userInter = PdfSecurity.hash2B(user, userKeySalt, ByteArray(0))
        val ue = aesNoPad(Cipher.ENCRYPT_MODE, "AES/CBC/NoPadding", userInter, fileKey).also { userInter.fill(0) }
        // Algorithm 9 — 같은 꼴이되 해시에 U 48바이트를 더한다.
        val ownerValidation = salts.copyOfRange(16, 24)
        val ownerKeySalt = salts.copyOfRange(24, 32)
        val o = PdfSecurity.hash2B(owner, ownerValidation, u) + ownerValidation + ownerKeySalt
        val ownerInter = PdfSecurity.hash2B(owner, ownerKeySalt, u)
        val oe = aesNoPad(Cipher.ENCRYPT_MODE, "AES/CBC/NoPadding", ownerInter, fileKey).also { ownerInter.fill(0) }
        // Algorithm 10 — P(리틀 엔디언 4) ‖ FF FF FF FF ‖ 'T'·'F' ‖ "adb" ‖ 무작위 4, 파일 키로 AES-256-ECB.
        val block = ByteArray(16)
        for (i in 0 until 4) block[i] = (p shr (8 * i)).toByte()
        for (i in 4 until 8) block[i] = 0xFF.toByte()
        block[8] = (if (encryptMetadata) 'T' else 'F').code.toByte()
        block[9] = 'a'.code.toByte()
        block[10] = 'd'.code.toByte()
        block[11] = 'b'.code.toByte()
        System.arraycopy(permsTail, 0, block, 12, 4)
        val perms = aesNoPad(Cipher.ENCRYPT_MODE, "AES/ECB/NoPadding", fileKey, block).also { block.fill(0) }
        return R6Values(o, u, oe, ue, perms)
    }

    /** `/Encrypt` 사전(명세 표 20·21·25). qpdf 가 쓰는 꼴과 같다 — 필터 사전의 `/Length` 는 바이트(32), 맨 위는 비트(256). */
    fun encryptDict(v: R6Values, p: Int = ALL_PERMISSIONS): PdfDict {
        val stdCf = PdfDict(
            linkedMapOf(
                "AuthEvent" to PdfName("DocOpen"),
                "CFM" to PdfName("AESV3"),
                "Length" to PdfNum("32"),
            ),
        )
        return PdfDict(
            linkedMapOf(
                "Filter" to PdfName("Standard"),
                "V" to PdfNum("5"),
                "R" to PdfNum("6"),
                "Length" to PdfNum("256"),
                "CF" to PdfDict(linkedMapOf("StdCF" to stdCf)),
                "StmF" to PdfName("StdCF"),
                "StrF" to PdfName("StdCF"),
                "P" to PdfNum(p.toString()),
                "EncryptMetadata" to PdfBool(true),
                "O" to PdfStr(v.o),
                "U" to PdfStr(v.u),
                "OE" to PdfStr(v.oe),
                "UE" to PdfStr(v.ue),
                "Perms" to PdfStr(v.perms),
            ),
        )
    }

    /**
     * 잠그는 다시 쓰기의 상대([PdfDecryptor.lock] 이 쓴다) — 새 파일 키를 만들고 그 키로 값·스트림을 잠근다. 다 쓰면 [wipe].
     *
     * @param password 사용자 암호(덮지 않는다 — 부르는 쪽의 배열이다). [problemOf] 를 지나야 한다.
     */
    class Target(password: CharArray, private val random: SecureRandom = SecureRandom()) {
        private val fileKey = ByteArray(32).also { random.nextBytes(it) }

        /** 새 `/Encrypt` 사전. */
        val encryptDict: PdfDict

        init {
            val user = PdfSecurity.r6PasswordBytes(password) ?: throw IllegalArgumentException("암호를 바이트로 옮길 수 없다")
            val owner = ByteArray(32).also { random.nextBytes(it) }
            val salts = ByteArray(32).also { random.nextBytes(it) }
            val tail = ByteArray(4).also { random.nextBytes(it) }
            try {
                require(user.isNotEmpty() && user.size <= MAX_PASSWORD_BYTES) { "암호의 길이" }
                encryptDict = encryptDict(r6Values(user, owner, fileKey, salts, tail))
            } finally {
                user.fill(0)
                owner.fill(0)
            }
        }

        /** 새 `/ID`(트레일러) — 원본에 없을 때 쓴다. 무작위 16바이트 둘. */
        fun newId(): PdfArray {
            val a = ByteArray(16).also { random.nextBytes(it) }
            return PdfArray(mutableListOf(PdfStr(a), PdfStr(a.copyOf())))
        }

        /** 문자열 하나를 잠근다 — 무작위 IV ‖ AES-256-CBC(PKCS#7). */
        fun encryptString(plain: ByteArray): ByteArray {
            val e = streamEncryptor()
            return e.update(plain, 0, plain.size) + e.finish()
        }

        /** 스트림 하나를 잠그는 것(조금씩). */
        fun streamEncryptor(): StreamTransform {
            val iv = ByteArray(16).also { random.nextBytes(it) }
            return AesCbcEncryptor(fileKey, iv)
        }

        /**
         * 값 안의 문자열을 전부 잠근다 — **새 값**을 돌려준다. 서명 사전의 `/Contents` 는 두다(명세 7.6.1 — 푸는 쪽 [PdfCryptRules.decryptStrings]
         * 와 같은 규칙. 잠그면 서명은 어차피 깨진다 — 바이트가 바뀐다).
         */
        fun encryptStrings(v: PdfObj, depth: Int = 0): PdfObj {
            if (depth > PdfParser.MAX_DEPTH) return v
            return when (v) {
                is PdfStr -> PdfStr(encryptString(v.bytes))
                is PdfArray -> PdfArray(v.items.mapTo(ArrayList(v.items.size)) { encryptStrings(it, depth + 1) })
                is PdfDict -> {
                    val isSig = v.name("Type") == "Sig" || v.name("Type") == "DocTimeStamp" ||
                        (v["ByteRange"] != null && v["Contents"] is PdfStr)
                    val out = LinkedHashMap<String, PdfObj>(v.map.size)
                    for ((k, x) in v.map) out[k] = if (isSig && k == "Contents") x else encryptStrings(x, depth + 1)
                    PdfDict(out)
                }
                else -> v
            }
        }

        fun wipe() {
            fileKey.fill(0)
        }
    }

    // 매개변수를 `iv` 로 부르지 않는다(함정 표 — `Cipher.getIV()`). ECB 는 `/Perms` 의 한 블록뿐이다 — 명세(Algorithm 10)가 그렇게 정한다.
    @android.annotation.SuppressLint("GetInstance")
    private fun aesNoPad(mode: Int, transformation: String, key: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance(transformation)
        if (transformation.contains("CBC")) {
            c.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        } else {
            c.init(mode, SecretKeySpec(key, "AES"))
        }
        return c.doFinal(data)
    }
}
