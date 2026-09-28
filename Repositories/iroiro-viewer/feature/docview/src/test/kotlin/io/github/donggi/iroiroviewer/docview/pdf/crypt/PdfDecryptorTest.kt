package io.github.donggi.iroiroviewer.docview.pdf.crypt

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 암호 PDF 를 풀어 **평문 원본과 대조한다.**
 *
 * ## 표본은 우리가 잠그지 않았다
 *
 * `src/test/resources/pdfcrypt/` 의 표본은 **서로 다른 두 구현**이 잠갔다 — pypdf 6.17(순수
 * 파이썬)과 qpdf 12.3.2(C++, pikepdf 10.13 을 통해). 평문 원본(`plain.pdf`)만 손으로 썼다
 * (3쪽, 쪽마다 다른 단색 + 흰 숫자 + 16×16 그림 + 한글 제목 + XMP 메타데이터).
 * 사용자 암호 `iroiro`, 소유자 암호 `owner-pw`, 한글 표본은 사용자 암호 `비밀번호`.
 *
 * **자기가 잠그고 자기가 푼 시험은 판별력이 0 이다.** 지난 조사에서 HWP 배포용 문서의 왕복
 * 시험이 상수·순서·식 세 군데를 일부러 틀리게 심고도 전부 통과했다. 여기서는 잠근 쪽이 우리가
 * 아니고, 두 구현이 **서로의 표본을 푼다는 것**을 만들 때 확인했다(두 도구 모두 18개 전부).
 *
 * ## 무엇을 대조하는가
 *
 * 쪽 수, 쪽마다 **풀린** 내용 스트림, 그림 스트림 바이트, `/Info /Title`(UTF-16BE 한글),
 * 메타데이터 스트림. 암호가 한 바이트만 어긋나도 Flate 가 풀리지 않거나 제목이 깨진다.
 */
class PdfDecryptorTest {

    private val dir = File(javaClass.classLoader!!.getResource("pdfcrypt/plain.pdf")!!.toURI()).parentFile

    private fun bytes(name: String) = File(dir, name).readBytes()

    private fun decrypt(name: String, pw: String): Pair<PdfDecryptor.Result, ByteArray> {
        val sink = MemorySink()
        val r = PdfDecryptor.decrypt(ByteArraySource(bytes(name)), pw.toCharArray(), sink, 64L shl 20)
        return r to sink.toByteArray()
    }

    /** 문서 하나를 읽어 대조할 것들을 뽑는다. 암호가 풀린 뒤의 파일(또는 평문 원본)에 쓴다. */
    private class Probe(data: ByteArray) {
        val file = PdfFile.open(ByteArraySource(data))
        private val src = ByteArraySource(data)

        fun resolve(o: PdfObj?): PdfObj? {
            var v = o
            repeat(8) {
                val r = v as? PdfRef ?: return v
                v = when (val e = file.entries[r.num]) {
                    is PdfFile.InFile -> file.readAt(e.offset)?.value
                    is PdfFile.InStream -> objStm(e.stream)?.get(e.index)
                    else -> null
                }
            }
            return v
        }

        private fun objStm(num: Int): PdfDecryptor.ObjStm? {
            val e = file.entries[num] as? PdfFile.InFile ?: return null
            val o = file.readAt(e.offset) ?: return null
            return PdfDecryptor.ObjStm.parse(o.dict!!, streamRaw(o))
        }

        fun streamRaw(o: PdfFile.Indirect): ByteArray =
            file.readBytes(o.dataStart, file.streamLength(o) { resolve(it) }.toInt())

        /** 참조가 가리키는 스트림을 **필터까지 풀어** 준다. */
        fun streamOf(ref: PdfObj?): ByteArray? {
            val r = ref as? PdfRef ?: return null
            val e = file.entries[r.num] as? PdfFile.InFile ?: return null
            val o = file.readAt(e.offset) ?: return null
            if (!o.isStream) return null
            return PdfFilters.decode(o.dict!!, streamRaw(o))
        }

        val encrypted: Boolean get() = file.trailer["Encrypt"] != null
        val catalog get() = resolve(file.trailer["Root"]) as PdfDict
        val pages: List<PdfDict>
            get() {
                val root = resolve(catalog["Pages"]) as PdfDict
                return (root["Kids"] as PdfArray).items.map { resolve(it) as PdfDict }
            }
        val title: String?
            get() {
                val info = resolve(file.trailer["Info"]) as? PdfDict ?: return null
                val t = (resolve(info["Title"]) as? PdfStr)?.bytes ?: return null
                return if (t.size >= 2 && t[0] == 0xFE.toByte() && t[1] == 0xFF.toByte()) {
                    String(t, 2, t.size - 2, Charsets.UTF_16BE)
                } else {
                    String(t, Charsets.ISO_8859_1)
                }
            }

        fun content(page: PdfDict): ByteArray = streamOf(page["Contents"])!!

        fun image(page: PdfDict): ByteArray {
            val res = resolve(page["Resources"]) as PdfDict
            val xo = resolve(res["XObject"]) as PdfDict
            return streamOf(xo["Im1"])!!
        }

        val metadata: ByteArray? get() = streamOf(catalog["Metadata"])
    }

    private val plain by lazy { Probe(bytes("plain.pdf")) }

    /** 풀린 결과가 평문 원본과 같은 내용인가. */
    private fun assertSameAsPlain(name: String, out: ByteArray) {
        val dec = Probe(out)
        assertTrue(!dec.encrypted, "$name: 결과에 /Encrypt 가 남았다")
        assertEquals(plain.pages.size, dec.pages.size, "$name: 쪽 수")
        assertEquals("암호 시험 문서", dec.title, "$name: 제목")
        for (i in plain.pages.indices) {
            assertContentEquals(plain.content(plain.pages[i]), dec.content(dec.pages[i]), "$name: ${i + 1}쪽 내용")
            assertContentEquals(plain.image(plain.pages[i]), dec.image(dec.pages[i]), "$name: ${i + 1}쪽 그림")
        }
        assertContentEquals(plain.metadata, dec.metadata, "$name: 메타데이터")
    }

    private val all = listOf(
        "pypdf-rc4-40.pdf", "pypdf-rc4-128.pdf", "pypdf-aes-128.pdf", "pypdf-aes-256-r5.pdf",
        "pypdf-aes-256.pdf", "qpdf-r2.pdf", "qpdf-r3.pdf", "qpdf-r4-aes.pdf", "qpdf-r4-rc4.pdf",
        "qpdf-r6.pdf", "qpdf-r6-objstm.pdf", "qpdf-r4-objstm.pdf", "qpdf-r4-cleartext-meta.pdf",
        "qpdf-r4-linear.pdf", "qpdf-r6-linear-objstm.pdf",
    )

    @Test
    fun 사용자_암호로_전부_풀린다() {
        for (name in all) {
            val (r, out) = decrypt(name, "iroiro")
            assertIs<PdfDecryptor.Result.Ok>(r, "$name: $r")
            assertSameAsPlain(name, out)
        }
    }

    @Test
    fun 소유자_암호로도_풀린다() {
        // 사람은 자기가 받은 암호가 어느 쪽인지 모른다.
        for (name in all) {
            val (r, out) = decrypt(name, "owner-pw")
            assertIs<PdfDecryptor.Result.Ok>(r, "$name: $r")
            assertSameAsPlain(name, out)
        }
    }

    @Test
    fun 한글_암호가_풀린다() {
        for (name in listOf("pypdf-aes-256-ko.pdf", "qpdf-r6-ko.pdf")) {
            val (r, out) = decrypt(name, "비밀번호")
            assertIs<PdfDecryptor.Result.Ok>(r, "$name: $r")
            assertSameAsPlain(name, out)
        }
    }

    @Test
    fun 틀린_암호는_틀렸다고_말한다() {
        for (name in all + "qpdf-r6-ko.pdf") {
            assertIs<PdfDecryptor.Result.WrongPassword>(decrypt(name, "iroiro!").first, name)
            assertIs<PdfDecryptor.Result.WrongPassword>(decrypt(name, "").first, name)
        }
    }

    @Test
    fun 소유자_암호만_걸린_문서는_빈_사용자_암호로_풀린다() {
        val (r, out) = decrypt("qpdf-r6-owner-only.pdf", "")
        assertIs<PdfDecryptor.Result.Ok>(r)
        assertSameAsPlain("owner-only", out)
    }

    @Test
    fun 평문_문서는_암호가_없다고_말한다() {
        assertIs<PdfDecryptor.Result.NotEncrypted>(decrypt("plain.pdf", "iroiro").first)
    }

    @Test
    fun 메타데이터를_풀지_않는_표본에서_메타데이터가_평문으로_남는다() {
        // EncryptMetadata=false: 그 스트림은 **암호화되지 않았다.** 우리가 한 번 더 '풀면'
        // 멀쩡한 XMP 가 쓰레기가 된다. 위 대조에서 metadata 가 같다는 것이 그 확인이다.
        val (r, out) = decrypt("qpdf-r4-cleartext-meta.pdf", "iroiro")
        assertIs<PdfDecryptor.Result.Ok>(r)
        val xmp = String(Probe(out).metadata!!, Charsets.ISO_8859_1)
        assertTrue("IROIRO-XMP-MARK" in xmp, xmp.take(120))
    }

    @Test
    fun 결과는_우리_리더가_아닌_곳에서도_읽힌다() {
        // 결과 파일이 PDF 로서 온전한가 — 우리 리더가 관대해서 통과하는 것은 아닌가.
        // xref 가 가리키는 자리마다 정확히 그 번호의 객체가 있어야 한다.
        for (name in all) {
            val out = decrypt(name, "iroiro").second
            val f = PdfFile.open(ByteArraySource(out))
            assertTrue(!f.rebuilt, "$name: 결과의 xref 가 틀려 다시 세워야 했다")
            for ((num, e) in f.entries) {
                if (e is PdfFile.InFile) {
                    val o = assertNotNull(f.readAt(e.offset), "$name: $num 번이 그 자리에 없다")
                    assertEquals(num, o.num, "$name: xref 가 엉뚱한 객체를 가리킨다")
                }
            }
        }
    }

    @Test
    fun hash2B_가_독립_구현과_같은_값을_낸다() {
        // 기대값은 pypdf 6.17 의 `AlgV5.calculate_hash(6, …)` 로 뽑았다(우리 코드와 무관).
        fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val cases = listOf(
            Triple("69726f69726f", "0001020304050607", "") to
                "e64725d7bff91b6f7bcaac4deeffab731965ca294c41b972762d91077e16136f",
            Triple("ebb984ebb080ebb288ed98b8", "aaaaaaaaaaaaaaaa", "") to
                "06191d38e1f1d49150e67a95468040193ea00838fd94765f78278a5280462a52",
            Triple("6f776e65722d7077", "08090a0b0c0d0e0f", (0 until 48).joinToString("") { "%02x".format(it) }) to
                "32ca317b947eb557e101575e84c1d59289d4c3eedb42a1f19eac3090620d5f52",
            Triple("", "0102030405060708", "") to
                "8d1efb4f1bdbb651341704c2139de4f6be05d6d4609af56916b21646ed74825c",
        )
        for ((input, want) in cases) {
            val got = PdfSecurity.hash2B(hex(input.first), hex(input.second), hex(input.third))
            assertContentEquals(hex(want), got, "암호 ${input.first}")
        }
    }

    @Test
    fun 깨진_파일에서_멈추지_않는다() {
        val good = bytes("qpdf-r6.pdf")
        // 반으로 자른 것, 앞을 지운 것, 무작위 — 결과가 무엇이든 **끝나야** 한다.
        val cases = listOf(
            good.copyOf(good.size / 2),
            good.copyOfRange(100, good.size),
            ByteArray(4096) { (it * 31 + 7).toByte() },
            "%PDF-1.4\n".toByteArray(),
        )
        for (c in cases) {
            runCatching {
                PdfDecryptor.decrypt(ByteArraySource(c), "iroiro".toCharArray(), MemorySink(), 16L shl 20)
            }
        }
    }

    @Test
    fun 상한을_넘으면_멈춘다() {
        assertFailsWith<PdfDecryptor.TooLarge> {
            PdfDecryptor.decrypt(ByteArraySource(bytes("qpdf-r6.pdf")), "iroiro".toCharArray(), MemorySink(), 1024)
        }
    }

    @Test
    fun RC4_가_알려진_값을_낸다() {
        // RFC 6229 가 아니라 가장 널리 인용되는 시험 벡터(Key/Plaintext → BBF316E8D940AF0AD3).
        val out = PdfSecurity.rc4("Key".toByteArray(), "Plaintext".toByteArray())
        assertEquals("bbf316e8d940af0ad3", out.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun 공개_키_처리기는_지원하지_않는다고_말한다() {
        val dict = PdfDict(linkedMapOf("Filter" to PdfName("Adobe.PubSec"), "V" to PdfNum("4"), "R" to PdfNum("4")))
        assertFailsWith<UnsupportedEncryption> { PdfSecurity.open(dict, ByteArray(0), "x".toCharArray()) }
    }

    // ---- 적대적 검토가 잡은 것 -------------------------------------------------------
    //
    // 표본을 **같은 길이로** 고쳐 만든다 — 바이트 수가 그대로면 xref 의 오프셋이 그대로 맞아
    // 다른 것은 건드리지 않고 그 성질 하나만 바뀐다.

    private fun sameLength(bytes: ByteArray, from: String, to: String): ByteArray {
        require(from.length == to.length)
        val s = String(bytes, Charsets.ISO_8859_1)
        assertTrue(from in s, "표본에 '$from' 이 없다")
        return s.replace(from, to).toByteArray(Charsets.ISO_8859_1)
    }

    private fun decryptBytes(bytes: ByteArray, pw: String = "iroiro", max: Long = 64L shl 20): Pair<PdfDecryptor.Result, ByteArray> {
        val sink = MemorySink()
        return PdfDecryptor.decrypt(ByteArraySource(bytes), pw.toCharArray(), sink, max) to sink.toByteArray()
    }

    @Test
    fun V4_에_Length_가_없어도_128비트로_푼다() {
        // 명세는 V4 의 `/Length` 를 요구하지 않는다(표 20·25). 예전에는 없으면 40비트로 쳐서
        // 맞는 암호가 '틀렸다' 가 됐다.
        var b = bytes("qpdf-r4-aes.pdf")
        b = sameLength(b, "/CFM /AESV2 /Length 16", "/CFM /AESV2           ")
        b = sameLength(b, "/Filter /Standard /Length 128", "/Filter /Standard            ")
        val (r, out) = decryptBytes(b)
        assertEquals(PdfDecryptor.Result.Ok, r)
        assertSameAsPlain("V4 /Length 없음", out)
    }

    @Test
    fun V4_RC4_에_Length_가_없으면_40비트가_아니라_128비트다() {
        // 위 시험은 AESV2 라 **판별력이 없었다** — AESV2 는 적힌 값과 무관하게 128 로 가므로, `/Length` 가 없을 때의 기본값을
        // 40 으로 되돌려도 통과했다(11단계 뒤 반증조가 '시험의 빈틈' 으로 적은 것). 기본값이 실제로 쓰이는 것은 RC4(`/V2`)
        // 필터다. qpdf 12.3 가 이 변형을 같은 암호로 연다(스크래치패드의 `mkcrypt_ko.py`·`verify_ko.py` — pypdf 6.17 은
        // 40 으로 쳐서 열지 못한다. 두 오라클이 갈리는 자리이고, 우리는 pdfium·qpdf 쪽이다).
        var b = bytes("qpdf-r4-rc4.pdf")
        b = sameLength(b, "/CFM /V2 /Length 16", "/CFM /V2           ")
        b = sameLength(b, "/Filter /Standard /Length 128", "/Filter /Standard            ")
        val (r, out) = decryptBytes(b)
        assertEquals(PdfDecryptor.Result.Ok, r)
        assertSameAsPlain("V4 RC4 /Length 없음", out)
    }

    @Test
    fun V4_RC4_의_필터가_바이트로_적은_Length_를_읽는다() {
        // 필터의 `/Length 16` 은 **바이트**다(비트로 적는 도구와 섞여 있다 — 40 보다 작으면 바이트). 최상위 `/Length` 를 지워도
        // 필터의 값으로 128비트가 된다.
        val b = sameLength(bytes("qpdf-r4-rc4.pdf"), "/Filter /Standard /Length 128", "/Filter /Standard            ")
        val (r, out) = decryptBytes(b)
        assertEquals(PdfDecryptor.Result.Ok, r)
        assertSameAsPlain("V4 RC4 필터 Length", out)
    }

    // ---- R2~R4 의 한글 암호 ----------------------------------------------------------
    //
    // R2~R4 의 암호는 명세상 PDFDocEncoding 이라 한글을 담을 수 없고, 도구마다 제 인코딩으로 넣는다. 그래서 후보를 여럿
    // 시도한다(`PdfSecurity.legacyCandidates`: latin-1 → UTF-8 → CP949). 11단계 뒤에는 이 후보에 **표본이 하나도 없었다** —
    // 후보 하나를 지워도 시험이 통과했다. 표본 셋은 pypdf 6.17 이 암호의 **바이트**를 그대로 받아 잠갔고(qpdf 12.3 는
    // R3·R4 에서 PDFDocEncoding 밖의 암호로 잠그기를 거절한다), 잠근 뒤 qpdf·pypdf 가 **그 인코딩으로만** 열고 다른 인코딩
    // 으로는 열지 못하는 것을 확인했다(`verify_ko.py`). 그래서 인코딩마다 판별력이 있다.

    @Test
    fun R2_의_CP949_한글_암호가_풀린다() {
        // 반디집·한국 도구들이 흔히 쓰는 모양(ZIP 암호와 같은 사정 — 함정 표).
        val (r, out) = decrypt("pypdf-rc4-40-ko-cp949.pdf", "비밀번호")
        assertIs<PdfDecryptor.Result.Ok>(r, r.toString())
        assertSameAsPlain("R2 CP949", out)
    }

    @Test
    fun R3_의_UTF8_한글_암호가_풀린다() {
        val (r, out) = decrypt("pypdf-rc4-128-ko-utf8.pdf", "비밀번호")
        assertIs<PdfDecryptor.Result.Ok>(r, r.toString())
        assertSameAsPlain("R3 UTF-8", out)
    }

    @Test
    fun R4_AES_의_CP949_한글_암호가_풀린다() {
        val (r, out) = decrypt("pypdf-aes-128-ko-cp949.pdf", "비밀번호")
        assertIs<PdfDecryptor.Result.Ok>(r, r.toString())
        assertSameAsPlain("R4 CP949", out)
    }

    @Test
    fun 한글_표본도_소유자_암호로_풀리고_틀린_한글_암호는_틀렸다고_말한다() {
        for (name in listOf("pypdf-rc4-40-ko-cp949.pdf", "pypdf-rc4-128-ko-utf8.pdf", "pypdf-aes-128-ko-cp949.pdf")) {
            val (r, out) = decrypt(name, "owner-pw")
            assertIs<PdfDecryptor.Result.Ok>(r, "$name: $r")
            assertSameAsPlain("$name 소유자", out)
            // 앞 두 글자가 같은 틀린 암호. 32바이트 채움 뒤에 우연히 맞는 일이 없어야 한다.
            assertIs<PdfDecryptor.Result.WrongPassword>(decrypt(name, "비밀").first, name)
            assertIs<PdfDecryptor.Result.WrongPassword>(decrypt(name, "비밀번호1").first, name)
        }
    }

    @Test
    fun stream_뒤의_공백을_데이터로_읽지_않는다() {
        // `stream \n` — 명세 위반이지만 pdfium·pdf.js·qpdf 가 다 받는다. 공백을 데이터로 읽으면
        // AES 의 IV 가 어긋나 모든 쪽이 깨진다.
        val b = sameLength(bytes("qpdf-r4-aes.pdf"), ">>\nstream\n", ">>stream \n")
        val (r, out) = decryptBytes(b)
        assertEquals(PdfDecryptor.Result.Ok, r)
        assertSameAsPlain("stream 공백", out)
    }

    @Test
    fun xref_가_밀린_파일은_다시_세워_푼다() {
        // '표가 1 번에서 시작한다' 는 흔한 도구 결함. 모든 항목이 한 칸씩 밀린다. pdfium 은 첫
        // 항목을 대조해 다시 세우고, 우리도 그래야 `/Encrypt` 를 엉뚱한 객체로 읽지 않는다.
        val b = sameLength(bytes("qpdf-r2.pdf"), "\nxref\n0 ", "\nxref\n1 ")
        assertIs<PdfDecryptor.Inspection.NeedsPassword>(PdfDecryptor.inspect(ByteArraySource(b)))
        val (r, out) = decryptBytes(b)
        assertEquals(PdfDecryptor.Result.Ok, r)
        assertSameAsPlain("xref 밀림", out)
    }

    @Test
    fun 번호가_큰_객체_하나가_xref_를_부풀리지_않는다() {
        // 증분 저장으로 `8000000 0 obj null` 하나를 더한다. 예전에는 0 부터 가장 큰 번호까지
        // 빽빽한 표를 써서 몇 KB 파일이 168 MB 를 요구했다. 지금은 쓴 번호만 소구역으로 적는다.
        val orig = bytes("qpdf-r2.pdf")
        val f = PdfFile.open(ByteArraySource(orig))
        val prev = String(orig, Charsets.ISO_8859_1).let { s ->
            Regex("""startxref\s+(\d+)""").findAll(s).last().groupValues[1]
        }
        fun ref(o: PdfObj?) = (o as PdfRef).let { "${it.num} ${it.gen} R" }
        val idHex = ((f.trailer["ID"] as PdfArray).items[0] as PdfStr).bytes.joinToString("") { "%02x".format(it) }
        val head = orig + "\n8000000 0 obj\nnull\nendobj\n".toByteArray(Charsets.ISO_8859_1)
        val objAt = orig.size + 1
        val xrefAt = head.size
        val tail = "xref\n8000000 1\n${"%010d".format(objAt)} 00000 n \ntrailer\n" +
            "<< /Size 8000001 /Root ${ref(f.trailer["Root"])} /Encrypt ${ref(f.trailer["Encrypt"])} " +
            "/ID [<$idHex><$idHex>] /Prev $prev >>\nstartxref\n$xrefAt\n%%EOF\n"
        val b = head + tail.toByteArray(Charsets.ISO_8859_1)
        // 상한 1 MiB — 빽빽한 표였다면 여기서 TooLarge 였다.
        val (r, out) = decryptBytes(b, max = 1L shl 20)
        assertEquals(PdfDecryptor.Result.Ok, r)
        assertTrue(out.size < 64 * 1024, "결과가 부풀었다: ${out.size}")
        assertSameAsPlain("큰 번호", out)
    }

    @Test
    fun 예측자의_폭이_데이터보다_넓어도_부풀지_않는다() {
        // `/Columns` 는 파일이 적는 값이다. 몇 바이트에 64 MiB 폭을 적으면 예전에는 64 MiB 줄 버퍼를
        // 잡고 0 으로 채워 내보냈다.
        val parms = PdfDict(linkedMapOf(
            "Predictor" to PdfNum("12"), "Columns" to PdfNum("1048576"),
            "Colors" to PdfNum("32"), "BitsPerComponent" to PdfNum("16"),
        ))
        val out = PdfFilters.predict(byteArrayOf(2, 1, 2, 3, 4), parms)
        assertTrue(out.size <= 5, "부풀었다: ${out.size}")
    }

    @Test
    fun PDF_머리가_없으면_훑지_않고_끝낸다() {
        // 확장자만 `.pdf` 인 큰 파일이 플랫폼에서 '깨졌다' 로 온다. 머리가 없으면 곧바로 끝낸다.
        var calls = 0
        val junk = ByteArray(4 shl 20) { (it * 7).toByte() }
        val r = PdfDecryptor.inspect(ByteArraySource(junk)) { calls++ }
        assertIs<PdfDecryptor.Inspection.NotEncrypted>(r)
        assertEquals(0, calls, "훑었다")
    }

    @Test
    fun 다시_세우는_동안_취소가_닿는다() {
        // 머리는 있지만 뿌리가 없는 큰 파일 — 다시 세우기가 통째로 훑는다. 취소가 닿아야 하고,
        // 예전처럼 두 번 훑지도 않아야 한다.
        val big = "%PDF-1.7\n".toByteArray(Charsets.ISO_8859_1) + ByteArray(8 shl 20) { 'a'.code.toByte() }
        var calls = 0
        assertFailsWith<java.util.concurrent.CancellationException> {
            PdfFile.open(ByteArraySource(big)) {
                calls++
                if (calls > 3) throw java.util.concurrent.CancellationException("취소")
            }
        }
        var chunks = 0
        assertFailsWith<PdfSyntaxException> { PdfFile.open(ByteArraySource(big)) { chunks++ } }
        assertTrue(chunks <= 10, "두 번 훑었다: 조각 $chunks 번")
    }

    // ---- 암호 없이 들여다보기(inspect) --------------------------------------------
    //
    // pdfium 은 '암호가 필요하다' 와 '모르는 처리기다' 를 같은 `SecurityException` 으로 말한다.
    // 화면이 암호를 물을지 말지는 이 갈래가 정한다 — 틀리면 **물어도 소용없는 암호를 묻거나**
    // (공개 키), **물어야 할 것을 안 묻는다.**

    private fun inspect(bytes: ByteArray) = PdfDecryptor.inspect(ByteArraySource(bytes))

    @Test
    fun 사용자_암호가_걸린_문서는_암호가_필요하다고_말한다() {
        for (name in all.filter { "owner-only" !in it }) {
            assertIs<PdfDecryptor.Inspection.NeedsPassword>(inspect(bytes(name)), name)
        }
    }

    @Test
    fun 소유자_암호만_걸린_문서는_암호_없이_열린다고_말한다() {
        // 여기서 암호를 물으면 사용자는 **있지도 않은 암호**를 요구받는다.
        assertIs<PdfDecryptor.Inspection.OpenWithoutPassword>(inspect(bytes("qpdf-r6-owner-only.pdf")))
    }

    @Test
    fun 평문은_잠겨_있지_않다고_말한다() {
        assertIs<PdfDecryptor.Inspection.NotEncrypted>(inspect(bytes("plain.pdf")))
    }

    @Test
    fun 공개_키로_잠긴_문서는_물어도_소용없다고_말한다() {
        val pdf = minimalPdf("<</Filter/Adobe.PubSec/SubFilter/adbe.pkcs7.s5/V 4/R 4>>")
        assertIs<PdfDecryptor.Inspection.Unsupported>(inspect(pdf))
    }

    @Test
    fun 모르는_처리기는_물어도_소용없다고_말한다() {
        // 상업 DRM 이 자기 이름으로 건 처리기. 명세가 없으니 풀 길도 없다.
        val pdf = minimalPdf("<</Filter/FOPN_foweb/V 3/R 3/Length 128>>")
        assertIs<PdfDecryptor.Inspection.Unsupported>(inspect(pdf))
    }

    @Test
    fun 파일_채널로_읽어도_같은_결과가_나온다() {
        // 앱은 파일을 메모리에 올리지 않고 채널에서 위치를 주어 읽는다. 시험이 늘
        // `ByteArraySource` 만 타면 그 길은 한 번도 돌지 않는다.
        for (name in listOf("qpdf-r6-linear-objstm.pdf", "pypdf-rc4-40.pdf")) {
            val viaFile = java.io.FileInputStream(File(dir, name)).channel.use { ch ->
                val sink = MemorySink()
                val r = PdfDecryptor.decrypt(FileChannelSource(ch), "iroiro".toCharArray(), sink, 64L shl 20)
                assertEquals(PdfDecryptor.Result.Ok, r, name)
                sink.toByteArray()
            }
            assertContentEquals(decrypt(name, "iroiro").second, viaFile, name)
        }
    }

    /** 카탈로그·쪽 나무·빈 쪽 하나에 [encrypt] 사전을 단 PDF. xref 는 정확히 쓴다. */
    private fun minimalPdf(encrypt: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        fun put(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: String) {
            offsets.add(out.size())
            put("${offsets.size} 0 obj\n$body\nendobj\n")
        }
        put("%PDF-1.7\n")
        obj("<</Type/Catalog/Pages 2 0 R>>")
        obj("<</Type/Pages/Kids[3 0 R]/Count 1>>")
        obj("<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]>>")
        obj(encrypt)
        val xref = out.size()
        put("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) put(String.format("%010d 00000 n \n", o))
        put("trailer\n<</Size ${offsets.size + 1}/Root 1 0 R/Encrypt 4 0 R/ID[<00112233445566778899aabbccddeeff><00112233445566778899aabbccddeeff>]>>\n")
        put("startxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}
