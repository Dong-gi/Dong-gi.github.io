package io.github.donggi.iroiroviewer.docview.pdf.crypt

import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextEngine
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextLayout
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PDF 잠그기(AES-256 R6 — [PdfLocker], [PdfDecryptor.lock]).
 *
 * **열쇠 셈의 오라클은 qpdf 다** — qpdf(pikepdf 10.13)가 잠근 표본(`pdfcrypt/qpdf-r6.pdf` 사용자 `iroiro`·소유자 `owner-pw`, `qpdf-r6-ko.pdf` 사용자
 * `비밀번호`)의 소금·파일 키·`/Perms` 꼬리를 그대로 넣으면 우리 셈이 다섯 값(`/U`·`/O`·`/UE`·`/OE`·`/Perms`)을 **바이트로 같게** 만든다. 우리가
 * 잠근 결과를 그 암호로 다시 여는 것은 우리 복호화기(여기)와 qpdf·엄격한 pypdf(`samples-local/corpus/tools/verify_pdf_lock.py` — 이 시험이
 * `out/pdf-lock-synthetic/` 에 결과를 남긴다)가 본다.
 */
class PdfLockerTest {

    private val dir = File(javaClass.classLoader!!.getResource("pdfcrypt/plain.pdf")!!.toURI()).parentFile

    private fun bytes(name: String) = File(dir, name).readBytes()

    private fun resolve(file: PdfFile, o: PdfObj?): PdfObj? {
        var v = o
        repeat(8) {
            val r = v as? PdfRef ?: return v
            v = (file.entries[r.num] as? PdfFile.InFile)?.let { file.readAt(it.offset)?.value }
        }
        return v
    }

    private fun encryptOf(data: ByteArray): PdfDict {
        val f = PdfFile.open(ByteArraySource(data))
        return resolve(f, f.trailer["Encrypt"]) as PdfDict
    }

    private fun str(d: PdfDict, k: String) = (d[k] as PdfStr).bytes

    /** 문서 정보의 `/Title` 바이트 그대로(풀지 않고). */
    private fun infoTitle(data: ByteArray): ByteArray {
        val f = PdfFile.open(ByteArraySource(data))
        return str(resolve(f, f.trailer["Info"]) as PdfDict, "Title")
    }

    private fun aes(mode: Int, transformation: String, key: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance(transformation)
        if (transformation.contains("CBC")) c.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        else c.init(mode, SecretKeySpec(key, "AES"))
        return c.doFinal(data)
    }

    /** qpdf 가 잠근 표본의 다섯 값을 그 소금·파일 키로 다시 셈해 견준다. */
    private fun assertSameAsQpdf(name: String, user: String, owner: String) {
        val enc = encryptOf(bytes(name))
        val u = str(enc, "U")
        val o = str(enc, "O")
        val userBytes = assertNotNull(PdfSecurity.r6PasswordBytes(user.toCharArray()))
        val ownerBytes = assertNotNull(PdfSecurity.r6PasswordBytes(owner.toCharArray()))
        // 파일 키 — 사용자 열쇠 소금으로 UE 를 푼다(Algorithm 2.A 의 사용자 길).
        val inter = PdfSecurity.hash2B(userBytes, u.copyOfRange(40, 48), ByteArray(0))
        val fileKey = aes(Cipher.DECRYPT_MODE, "AES/CBC/NoPadding", inter, str(enc, "UE"))
        val permsPlain = aes(Cipher.DECRYPT_MODE, "AES/ECB/NoPadding", fileKey, str(enc, "Perms"))
        assertEquals("adb", String(permsPlain, 9, 3, Charsets.ISO_8859_1), "$name: /Perms 를 그 파일 키로 풀었다")
        val p = (enc["P"] as PdfNum).int!!
        val salts = u.copyOfRange(32, 48) + o.copyOfRange(32, 48)
        val v = PdfLocker.r6Values(userBytes, ownerBytes, fileKey, salts, permsPlain.copyOfRange(12, 16), p, encryptMetadata = true)
        assertContentEquals(u.copyOf(48), v.u, "$name: /U")
        assertContentEquals(o.copyOf(48), v.o, "$name: /O")
        assertContentEquals(str(enc, "UE").copyOf(32), v.ue, "$name: /UE")
        assertContentEquals(str(enc, "OE").copyOf(32), v.oe, "$name: /OE")
        assertContentEquals(str(enc, "Perms").copyOf(16), v.perms, "$name: /Perms")
    }

    @Test
    fun `R6 의 다섯 값이 qpdf 가 잠근 것과 바이트로 같다`() {
        assertSameAsQpdf("qpdf-r6.pdf", "iroiro", "owner-pw")
        assertSameAsQpdf("qpdf-r6-ko.pdf", "비밀번호", "owner-pw")
    }

    @Test
    fun `새 암호의 규칙 — 비었거나 127 바이트를 넘거나 옮길 수 없으면 거절한다`() {
        assertEquals(PdfLocker.PasswordProblem.EMPTY, PdfLocker.problemOf(CharArray(0)))
        // SASLprep 이 지우는 글자(폭 없는 공백)만 쳤다 — 금하는 서식 글자다.
        assertEquals(PdfLocker.PasswordProblem.UNPORTABLE, PdfLocker.problemOf("\u200B\u200B".toCharArray()))
        // SASLprep(NFKC)이 바꾸는 글자 — 한글 자판의 낱자, 전각 글자, 전각 공백, 합자. 바꾸지 않고 해시하는 앱(qpdf)이 열지 못한다(독립 검토).
        for (pw in listOf("\u3142\u3148\u3137\u3131", "\uFF21\uFF22\uFF23", "a\u3000b", "\uFB01x")) {
            assertEquals(PdfLocker.PasswordProblem.UNPORTABLE, PdfLocker.problemOf(pw.toCharArray()), pw)
        }
        // 금하는 글자 — 제어 문자, 사용자 정의 영역, 오른쪽에서 왼쪽으로 쓰는 글자.
        for (pw in listOf("a\u0007b", "a\uE000b", "\u05E9\u05DC\u05D5\u05DD")) {
            assertEquals(PdfLocker.PasswordProblem.UNPORTABLE, PdfLocker.problemOf(pw.toCharArray()), pw)
        }
        // SASLprep 이 금하는데 글자 갈래로는 걸리지 않는 것 — 줄·문단 나눔, 대체 글자 둘, 한자 짜임 기호의 처음과 끝.
        for (pw in listOf("a\u2028b", "a\u2029b", "a\uFFFCb", "a\uFFFDb", "a\u2FF0b", "a\u2FFBb")) {
            assertEquals(PdfLocker.PasswordProblem.UNPORTABLE, PdfLocker.problemOf(pw.toCharArray()), pw)
        }
        // Unicode 3.2 에 없던 글자 — 이모지(6.1), 루피 기호(6.0), 한글 자모 확장 B(5.2). 여는 앱이 어떻게 다룰지 모른다.
        for (pw in listOf("pw\uD83D\uDE00", "a\u20B9b", "a\uD7B0b")) {
            assertEquals(PdfLocker.PasswordProblem.UNPORTABLE, PdfLocker.problemOf(pw.toCharArray()), pw)
        }
        // 흔한 암호는 그대로 — 완성형 한글, 라틴 글자(조합된 é), 보통 공백·기호, 한자·가나.
        for (pw in listOf("비밀번호", "caf\u00E9", "two words", "p@ss-W0rd!", "日本語かな", "~`!#\$%^&*()_+={}[]|\\:;\"'<>,.?/")) {
            assertNull(PdfLocker.problemOf(pw.toCharArray()), pw)
        }
        assertNull(PdfLocker.problemOf("a".repeat(127).toCharArray()))
        assertEquals(PdfLocker.PasswordProblem.TOO_LONG, PdfLocker.problemOf("a".repeat(128).toCharArray()))
        // 한글은 한 글자 3바이트 — 42자(126)까지.
        assertNull(PdfLocker.problemOf("가".repeat(42).toCharArray()))
        assertEquals(PdfLocker.PasswordProblem.TOO_LONG, PdfLocker.problemOf("가".repeat(43).toCharArray()))
        assertEquals(PdfLocker.PasswordProblem.UNENCODABLE, PdfLocker.problemOf(charArrayOf('a', '\uD800')))
    }

    private fun lock(data: ByteArray, password: String): ByteArray {
        val sink = MemorySink()
        assertEquals(PdfDecryptor.LockResult.Ok, PdfDecryptor.lock(ByteArraySource(data), password.toCharArray(), sink, 64L shl 20))
        return sink.toByteArray().also { keepForOracle(it, password) }
    }

    private fun texts(data: ByteArray): List<String> {
        val e = assertNotNull(PdfTextEngine.open(ByteArraySource(data)))
        return (0 until e.pageCount).map { i -> e.page(i).let { PdfTextLayout.text(it, 0, it.glyphs.size).orEmpty() } }
    }

    /** 잠근 결과를 우리 복호화기로 그 암호로 풀어 돌려준다(틀린 암호·빈 암호는 거절되는 것까지 본다). */
    private fun unlockChecked(locked: ByteArray, password: String): ByteArray {
        assertEquals(PdfDecryptor.Inspection.NeedsPassword, PdfDecryptor.inspect(ByteArraySource(locked)))
        assertEquals(PdfDecryptor.Result.WrongPassword, PdfDecryptor.decrypt(ByteArraySource(locked), "wrong".toCharArray(), MemorySink(), 64L shl 20))
        val sink = MemorySink()
        assertEquals(PdfDecryptor.Result.Ok, PdfDecryptor.decrypt(ByteArraySource(locked), password.toCharArray(), sink, 64L shl 20))
        return sink.toByteArray()
    }

    @Test
    fun `잠근 PDF 는 그 암호로만 열리고 글·쪽이 원본과 같다`() {
        val plain = bytes("plain.pdf")
        for (pw in listOf("iroiro-lock", "열쇠와 자물쇠", "a".repeat(127), "가".repeat(42))) {
            val locked = lock(plain, pw)
            val enc = encryptOf(locked)
            assertEquals(6, enc.int("R"))
            assertEquals(5, enc.int("V"))
            assertEquals("AESV3", ((enc["CF"] as PdfDict)["StdCF"] as PdfDict).name("CFM"))
            assertEquals(-4, enc.int("P"))
            // 문자열이 잠겼다 — 문서 정보의 제목이 평문이 아니고, IV 16 + PKCS#7 로 채운 길이다.
            val plainTitle = infoTitle(plain)
            val lockedTitle = infoTitle(locked)
            assertTrue(!lockedTitle.contentEquals(plainTitle), "제목이 평문으로 남았다")
            assertEquals(16 + (plainTitle.size / 16 + 1) * 16, lockedTitle.size)
            val back = unlockChecked(locked, pw)
            assertEquals(texts(plain), texts(back), "글")
            // 트레일러에 /ID 가 있다(원본에 없었어도).
            val f = PdfFile.open(ByteArraySource(locked))
            assertEquals(2, (f.trailer["ID"] as PdfArray).items.size)
        }
    }

    @Test
    fun `객체 스트림·xref 스트림 문서도 잠그고 뿌리에 확장 수준 8 을 적는다`() {
        // qpdf 가 객체 스트림으로 잠근 표본을 우리가 풀어 '객체 스트림이 있는 평문' 을 만든다(뿌리가 객체 스트림 안에 있다).
        val sink = MemorySink()
        assertEquals(PdfDecryptor.Result.Ok, PdfDecryptor.decrypt(ByteArraySource(bytes("qpdf-r6-objstm.pdf")), "iroiro".toCharArray(), sink, 64L shl 20))
        val objStmPlain = sink.toByteArray()
        val locked = lock(objStmPlain, "객체스트림")
        val back = unlockChecked(locked, "객체스트림")
        assertEquals(texts(objStmPlain), texts(back))
        val f = PdfFile.open(ByteArraySource(back))
        val e = assertIs<PdfFile.InFile>(f.entries[(f.trailer["Root"] as PdfRef).num])
        val root = f.readAt(e.offset)!!.value as PdfDict
        val adbe = (root["Extensions"] as PdfDict)["ADBE"] as PdfDict
        assertEquals(8, adbe.int("ExtensionLevel"))
    }

    /** 고전 xref 로 짓는다 — 객체는 1부터. [trailerExtra] 는 트레일러에 더할 글, [startxref] 를 주면 그 자리를 적는다(망가뜨리기). */
    private fun classic(objects: List<String>, trailerExtra: String = "", startxref: Int? = null, offsetOf: (Int, Int) -> Int = { _, at -> at }): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.7\n")
        val offsets = objects.mapIndexed { i, body ->
            val at = out.size()
            w("${i + 1} 0 obj\n$body\nendobj\n")
            offsetOf(i + 1, at)
        }
        val xrefAt = out.size()
        w("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) w(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", o))
        w("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R $trailerExtra >>\nstartxref\n${startxref ?: xrefAt}\n%%EOF\n")
        return out.toByteArray()
    }

    private val threeObjects = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Resources << >> /Contents 4 0 R >>",
        "<< /Length 10 >>\nstream\nBT ET q Q \nendstream",
    )

    @Test
    fun `원본 자리를 바꾸는 다시 쓰기는 읽지 못한 객체·잘린 스트림·다시 세운 xref 를 거절한다`() {
        val good = classic(threeObjects)
        assertEquals(PdfDecryptor.LockResult.Ok, PdfDecryptor.lock(ByteArraySource(good), "x".toCharArray(), MemorySink(), 1L shl 20))
        // 3번의 xref 자리가 다른 번호의 객체(2번)를 가리킨다 — 3번을 읽지 못한다(다시 세웠으면 그것으로 거절한다).
        val goodText = String(good, Charsets.ISO_8859_1)
        val wrongAt = classic(threeObjects, offsetOf = { n, at -> if (n == 3) goodText.indexOf("2 0 obj") else at })
        assertFailsWith<PdfDecryptor.Damaged> { PdfDecryptor.lock(ByteArraySource(wrongAt), "x".toCharArray(), MemorySink(), 1L shl 20) }
        // 스트림이 적힌 길이보다 먼저 끝난다(파일이 잘렸다).
        val truncated = classic(threeObjects.toMutableList().also { it[3] = "<< /Length 4000 >>\nstream\nBT ET" })
        assertFailsWith<PdfDecryptor.Damaged> { PdfDecryptor.lock(ByteArraySource(truncated), "x".toCharArray(), MemorySink(), 1L shl 20) }
        // startxref 가 틀렸다 — 다시 세운다. 우리가 '고친' 판으로 원본을 바꾸지 않는다.
        val rebuilt = classic(threeObjects, startxref = 3)
        assertTrue(PdfFile.open(ByteArraySource(rebuilt)).rebuilt)
        assertFailsWith<PdfDecryptor.Damaged> { PdfDecryptor.lock(ByteArraySource(rebuilt), "x".toCharArray(), MemorySink(), 1L shl 20) }
    }

    @Test
    fun `제 endstream 이 망가진 스트림은 뒤 스트림의 것을 빌려 통과하지 못한다`() {
        // 4번의 /Length 가 틀려(파일보다 길다) 찾는 길로 간다. 5번은 멀쩡한 스트림 — 그 endstream 이 '빌릴' 후보다.
        val objects = threeObjects.toMutableList().also { it[3] = "<< /Length 999 >>\nstream\nBT ET q Q \nendstream" } +
            "<< /Length 3 >>\nstream\nabc\nendstream"
        val wrongLength = classic(objects)
        // 길이만 틀리고 endstream 이 제자리면 받는다(흔하다).
        assertEquals(PdfDecryptor.LockResult.Ok, PdfDecryptor.lock(ByteArraySource(wrongLength), "x".toCharArray(), MemorySink(), 1L shl 20))
        fun corrupt(pdf: ByteArray, word: String, with: String): ByteArray {
            val text = String(pdf, Charsets.ISO_8859_1)
            val at = text.indexOf(word, text.indexOf("4 0 obj"))
            return pdf.copyOf().also { with.toByteArray(Charsets.ISO_8859_1).copyInto(it, at) }
        }
        // 4번의 endstream 이 망가졌다 — 4번의 endobj 가 먼저 나온다.
        val noEnd = corrupt(wrongLength, "endstream", "Xndstream")
        assertFailsWith<PdfDecryptor.Damaged> { PdfDecryptor.lock(ByteArraySource(noEnd), "x".toCharArray(), MemorySink(), 1L shl 20) }
        // endobj 까지 망가졌다 — 다음 객체의 머리(`5 0 obj`)가 먼저 나온다.
        val noEndObj = corrupt(noEnd, "endobj", "Xndobj")
        assertFailsWith<PdfDecryptor.Damaged> { PdfDecryptor.lock(ByteArraySource(noEndObj), "x".toCharArray(), MemorySink(), 1L shl 20) }
    }

    @Test
    fun `푸는 길도 제자리일 때만 읽지 못한 객체를 거절한다 — 뷰어의 메모리 파일은 그대로 너그럽다`() {
        val locked = lock(classic(threeObjects + "<< /Title (info) >>", trailerExtra = "/Info 5 0 R"), "pw")
        // 문서 정보 객체(5)의 머리를 망가뜨린다 — 그 객체만 읽지 못한다.
        val text = String(locked, Charsets.ISO_8859_1)
        val at = text.indexOf("\n5 0 obj") + 1
        val broken = locked.copyOf().also { "5 0 xbj".toByteArray(Charsets.ISO_8859_1).copyInto(it, at) }
        assertEquals(PdfDecryptor.Result.Ok, PdfDecryptor.decrypt(ByteArraySource(broken), "pw".toCharArray(), MemorySink(), 1L shl 20))
        assertFailsWith<PdfDecryptor.Damaged> {
            PdfDecryptor.decrypt(ByteArraySource(broken), "pw".toCharArray(), MemorySink(), 1L shl 20, strict = true)
        }
    }

    @Test
    fun `제자리 다시 쓰기는 옛 선형화 사전을 빼고 트레일러의 직접 문서 정보도 잠근다`() {
        val linear = MemorySink()
        PdfDecryptor.decrypt(ByteArraySource(bytes("qpdf-r4-linear.pdf")), "iroiro".toCharArray(), linear, 64L shl 20)
        val lenient = String(linear.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(lenient.contains("/Linearized"), "표본이 선형화된 것이 아니다 — 시험이 아무것도 보지 않는다")
        val strict = MemorySink()
        PdfDecryptor.decrypt(ByteArraySource(bytes("qpdf-r4-linear.pdf")), "iroiro".toCharArray(), strict, 64L shl 20, strict = true)
        assertTrue(!String(strict.toByteArray(), Charsets.ISO_8859_1).contains("/Linearized"))
        val back = unlockChecked(lock(linear.toByteArray(), "lin"), "lin")
        assertTrue(!String(back, Charsets.ISO_8859_1).contains("/Linearized"))

        // 트레일러에 바로 놓인 문서 정보 — 간접 객체로 옮겨 잠그고, 풀면 그 글이 돌아온다.
        val direct = classic(threeObjects, trailerExtra = "/Info << /Title (Secret Title) >>")
        val locked = lock(direct, "info")
        assertTrue(!String(locked, Charsets.ISO_8859_1).contains("Secret Title"), "문서 정보가 평문으로 남았다")
        val f = PdfFile.open(ByteArraySource(unlockChecked(locked, "info")))
        assertEquals("Secret Title", String(str(resolve(f, f.trailer["Info"]) as PdfDict, "Title"), Charsets.ISO_8859_1))
    }

    @Test
    fun `잠긴 문서는 다시 잠그지 않는다`() {
        assertEquals(
            PdfDecryptor.LockResult.AlreadyEncrypted,
            PdfDecryptor.lock(ByteArraySource(bytes("qpdf-r6.pdf")), "x".toCharArray(), MemorySink(), 64L shl 20),
        )
    }

    @Test
    fun `같은 암호로 두 번 잠가도 열쇠·소금·IV 가 다르다`() {
        val a = lock(bytes("plain.pdf"), "same")
        val b = lock(bytes("plain.pdf"), "same")
        assertTrue(!str(encryptOf(a), "U").contentEquals(str(encryptOf(b), "U")))
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `AES 잠그개는 조금씩 넣어도 한 번에 넣은 것과 같고 푸는 쪽이 되돌린다`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16) { it.toByte() }
        val data = ByteArray(100_003) { (it * 31).toByte() }
        val whole = AesCbcEncryptor(key, iv).let { it.update(data, 0, data.size) + it.finish() }
        val pieces = AesCbcEncryptor(key, iv).let { e ->
            val out = java.io.ByteArrayOutputStream()
            var at = 0
            while (at < data.size) {
                val n = minOf(777, data.size - at)
                out.write(e.update(data, at, n))
                at += n
            }
            out.write(e.finish())
            out.toByteArray()
        }
        assertContentEquals(whole, pieces)
        assertContentEquals(iv, whole.copyOf(16))
        assertEquals(16 + (data.size / 16 + 1) * 16, whole.size)
        val d = AesCbcDecryptor(key)
        assertContentEquals(data, d.update(whole, 0, whole.size) + d.finish())
    }

    /**
     * 스트림 [streams] 개의 `/Length` 가 객체 스트림 속 객체를 가리키는 PDF(xref 스트림). [distinct] 면 스트림마다 다른 객체 스트림(하나에
     * [pad] 바이트), 아니면 모두 같은 객체 스트림의 칸을 가리킨다.
     */
    private fun lengthsInObjStreams(streams: Int, distinct: Boolean, pad: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        val offsets = java.util.TreeMap<Int, Int>()
        val inStream = java.util.TreeMap<Int, Pair<Int, Int>>() // 번호 → (객체 스트림, 칸)
        fun obj(num: Int, body: String) {
            offsets[num] = out.size()
            w("$num 0 obj\n$body\nendobj\n")
        }
        w("%PDF-1.7\n")
        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Resources << >> >>")
        val stms = if (distinct) streams else 1
        for (s in 0 until stms) {
            val members = if (distinct) listOf(1000 + s) else List(streams) { 1000 + it }
            val head = members.mapIndexed { i, m -> "$m ${i * 2}" }.joinToString(" ") + " "
            val body = members.joinToString(" ") { "5" } + " ".repeat(pad)
            members.forEachIndexed { i, m -> inStream[m] = (100 + s) to i }
            val data = head + body
            obj(100 + s, "<< /Type /ObjStm /N ${members.size} /First ${head.length} /Length ${data.length} >>\nstream\n$data\nendstream")
        }
        for (i in 0 until streams) obj(5000 + i, "<< /Length ${1000 + i} 0 R >>\nstream\nABCDE\nendstream")
        val xrefNum = 9000
        val xrefAt = out.size()
        val rows = java.io.ByteArrayOutputStream()
        fun row(type: Int, a: Int, b: Int) {
            rows.write(type)
            rows.write(java.nio.ByteBuffer.allocate(4).putInt(a).array())
            rows.write(byteArrayOf((b shr 8).toByte(), b.toByte()))
        }
        for (n in 0..xrefNum) {
            when {
                n == xrefNum -> row(1, xrefAt, 0)
                n in offsets -> row(1, offsets.getValue(n), 0)
                n in inStream -> inStream.getValue(n).let { (s, i) -> row(2, s, i) }
                else -> row(0, 0, if (n == 0) 0xFFFF else 0)
            }
        }
        val bin = rows.toByteArray()
        w("$xrefNum 0 obj\n<< /Type /XRef /Size ${xrefNum + 1} /W [1 4 2] /Root 1 0 R /Length ${bin.size} >>\nstream\n")
        out.write(bin)
        w("\nendstream\nendobj\nstartxref\n$xrefAt\n%%EOF\n")
        return out.toByteArray()
    }

    @Test
    fun `객체 스트림을 푸는 일에 읽는 예산이 걸린다 — 제자리 잠그기·풀기`() {
        // 스트림 40 개가 저마다 다른 객체 스트림(200 KB)을 가리킨다 — 다 풀면 8 MB 다. 예산 2 MB 면 멈춘다.
        val many = lengthsInObjStreams(40, distinct = true, pad = 200_000)
        assertEquals(PdfDecryptor.LockResult.Ok, PdfDecryptor.lock(ByteArraySource(many), "x".toCharArray(), MemorySink(), 64L shl 20))
        assertFailsWith<PdfDecryptor.TooLarge> {
            PdfDecryptor.lock(ByteArraySource(many), "x".toCharArray(), MemorySink(), 64L shl 20, readBudget = 2_000_000)
        }
        // 같은 객체 스트림의 칸 40 개 — 한 번만 푼다(캐시). 같은 예산으로 지난다.
        val shared = lengthsInObjStreams(40, distinct = false, pad = 200_000)
        assertEquals(
            PdfDecryptor.LockResult.Ok,
            PdfDecryptor.lock(ByteArraySource(shared), "x".toCharArray(), MemorySink(), 64L shl 20, readBudget = 2_000_000),
        )
        // 푸는 길(`decrypt`)도 같은 해석기를 지난다 — 우리가 잠근 판은 `/Length` 를 바로 적어 이 길을 타지 않아 여기서는 잠그는 길로 본다.
    }

    /** 말뭉치가 있는 저장소면 결과를 `out/pdf-lock-synthetic/` 에 남긴다 — `verify_pdf_lock.py` 가 qpdf·엄격한 pypdf 로 그 암호로만 열리는지 본다. */
    private fun keepForOracle(pdf: ByteArray, password: String) {
        val out = syntheticOut ?: return
        val name = "${javaClass.simpleName}-${System.nanoTime()}.pdf"
        File(out, name).writeBytes(pdf)
        File(out, "expect.tsv").appendText("$name\t$password\n", Charsets.UTF_8)
    }

    private companion object {
        /** 이 실행의 결과만 남게 시험 JVM 마다 한 번 비운다(앞 실행·되돌려 본 판의 결과가 오라클에 섞이지 않게). */
        val syntheticOut: File? by lazy {
            Corpus.dir?.let { File(it, "out/pdf-lock-synthetic").apply { deleteRecursively(); mkdirs() } }
        }
    }
}
