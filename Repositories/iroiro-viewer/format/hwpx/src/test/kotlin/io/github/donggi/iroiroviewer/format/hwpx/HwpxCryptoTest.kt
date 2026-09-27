package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.p
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.sec
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 암호 HWPX. 오라클은 셋이다 — ① 실물 O16(`HwpxSampleTest`, 한글이 잠근 파일), ② **파이썬이 따로 잠근** 표본
 * (`py-encrypted.hwpx`: `hashlib.pbkdf2_hmac` + `cryptography` 의 AES, 스크래치의 `make_encrypted.py` — 우리 코드를
 * 한 줄도 쓰지 않았다), ③ 시험 쪽 암호기([TestCrypto])와 RFC 6070 의 PBKDF2 값.
 */
class HwpxCryptoTest {

    private val locked = setOf("Contents/header.xml", "Contents/section0.xml")

    private fun lockedDoc(password: String = "1234", configure: (TinyHwpx) -> Unit = {}): ByteArray {
        val t = TinyHwpx().section(sec(p("잠긴 본문 첫 줄"), p("둘째 줄"))).lock(password.toCharArray(), locked)
        configure(t)
        return t.build()
    }

    @Test
    fun PBKDF2_는_RFC_6070_의_값과_같다() {
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        val pw = "password".toByteArray()
        val salt = "salt".toByteArray()
        assertEquals("0c60c80f961f0e71f3a9b524af6012062fe037a6", hex(Pbkdf2.derive("HmacSHA1", pw, salt, 1, 20) {}))
        assertEquals("ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957", hex(Pbkdf2.derive("HmacSHA1", pw, salt, 2, 20) {}))
        assertEquals("4b007901b765489abead49d926f721d065a429c1", hex(Pbkdf2.derive("HmacSHA1", pw, salt, 4096, 20) {}))
        // 여러 블록(dkLen 25 > 20).
        assertEquals(
            "3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038",
            hex(Pbkdf2.derive("HmacSHA1", "passwordPASSWORDpassword".toByteArray(), "saltSALTsaltSALTsaltSALTsaltSALTsalt".toByteArray(), 4096, 25) {}),
        )
    }

    @Test
    fun 암호_없이_열면_암호를_묻는다() {
        val o = Hwpx.open(lockedDoc())
        assertEquals(OpenFailure.PasswordRequired("암호가 걸린 HWPX", wrongPassword = false), (o as OpenOutcome.Failed).failure)
    }

    @Test
    fun 틀린_암호는_틀렸다고_말한다() {
        val o = Hwpx.open(lockedDoc(), "4321".toCharArray())
        val f = (o as OpenOutcome.Failed).failure as OpenFailure.PasswordRequired
        assertTrue(f.wrongPassword)
    }

    @Test
    fun 맞는_암호로_열리고_받은_암호_배열은_건드리지_않는다() {
        val pw = "1234".toCharArray()
        val o = Hwpx.open(lockedDoc(), pw)
        assertTrue(o is OpenOutcome.Success, o.toString())
        (o.document as FlowDocument).use { doc ->
            assertEquals(listOf("잠긴 본문 첫 줄", "둘째 줄"), paragraphs(doc.body()))
        }
        // 주인은 부르는 쪽이다(`DocViewModel` 이 지운다).
        assertContentEquals("1234".toCharArray(), pw)
    }

    @Test
    fun 검증값이_어긋나면_틀린_암호다() {
        // 열쇠는 맞아 inflate 는 되지만 검증값(평문 첫 1024 바이트의 SHA-256)이 다르다.
        val o = Hwpx.open(lockedDoc { it.tamperChecksum = true }, "1234".toCharArray())
        assertTrue(((o as OpenOutcome.Failed).failure as OpenFailure.PasswordRequired).wrongPassword, o.toString())
    }

    @Test
    fun 한글_암호도_UTF_8_로_연다() {
        // 한글이 한글 암호를 어떤 바이트로 넣는지는 **확인하지 못했다**(실물이 없다). 이 시험은 우리 쪽 UTF-8 경로만 지킨다.
        val bytes = lockedDoc("암호123")
        assertTrue(Hwpx.open(bytes, "암호123".toCharArray()) is OpenOutcome.Success)
        assertTrue(((Hwpx.open(bytes, "암호124".toCharArray()) as OpenOutcome.Failed).failure as OpenFailure.PasswordRequired).wrongPassword)
    }

    @Test
    fun 파이썬이_따로_잠근_표본을_연다() {
        val bytes = assertNotNull(javaClass.getResourceAsStream("/hwpx/py-encrypted.hwpx")).use { it.readBytes() }
        assertTrue(bytes.size <= 20 * 1024, "표본은 20KB 이하 — ${bytes.size}")
        assertTrue((Hwpx.open(bytes) as OpenOutcome.Failed).failure is OpenFailure.PasswordRequired)
        val o = Hwpx.open(bytes, "pw-2026".toCharArray())
        assertTrue(o is OpenOutcome.Success, o.toString())
        (o.document as FlowDocument).use { doc ->
            val paras = paragraphs(doc.body())
            assertEquals(40, paras.size)
            assertEquals("파이썬으로 잠근 문단 1", paras.first())
            assertEquals("끝 문단", paras.last())
            assertTrue("font-weight:bold" in doc.body(), "굵은 글자 모양(잠긴 머리)이 풀려야 한다")
            assertEquals("", doc.title) // 제목은 파일 이름에 맡긴다(`HwpxDocument.title`)
        }
    }

    @Test
    fun 모르는_암호_방식과_지나친_반복_수는_다루지_않는다() {
        val blowfish = lockedDoc().let { replaceInZip(it, "META-INF/manifest.xml") { m -> m.replace("xmlenc#aes256-cbc", "Blowfish CFB") } }
        val o1 = Hwpx.open(blowfish, "1234".toCharArray())
        assertTrue((o1 as OpenOutcome.Failed).failure is OpenFailure.Unsupported, o1.toString())
        // 암호 없이도 같은 답이다 — 물어도 소용이 없는 것을 묻지 않는다.
        assertTrue((Hwpx.open(blowfish) as OpenOutcome.Failed).failure is OpenFailure.Unsupported)

        val heavy = lockedDoc().let { replaceInZip(it, "META-INF/manifest.xml") { m -> m.replace("iteration-count=\"1024\"", "iteration-count=\"999999999\"") } }
        assertTrue((Hwpx.open(heavy, "1234".toCharArray()) as OpenOutcome.Failed).failure is OpenFailure.Unsupported)
    }

    @Test
    fun 잠긴_그림도_풀어서_내준다() {
        val png = ByteArray(300) { (it * 7).toByte() }
        val t = TinyHwpx().section(sec(Hwpx.pRaw(Hwpx.run("""<hp:pic><hp:curSz width="100"/><hc:img binaryItemIDRef="image1"/></hp:pic>"""))))
            .binary("image1", "BinData/image1.png", "image/png", png)
            .lock("1234".toCharArray(), locked + "BinData/image1.png")
        val o = Hwpx.open(t.build(), "1234".toCharArray())
        (o as OpenOutcome.Success).document.use { doc ->
            val bytes = assertNotNull((doc as FlowDocument).openResource("BinData/image1.png")).use { it.readBytes() }
            assertContentEquals(png, bytes)
        }
    }

    @Test
    fun 머리_없는_deflate_를_풀고_상한을_넘으면_멈춘다() {
        val plain = ByteArray(200_000) { (it % 251).toByte() }
        val d = Deflater(9, true)
        d.setInput(plain)
        d.finish()
        val buf = ByteArray(300_000)
        val n = d.deflate(buf)
        d.end()
        assertContentEquals(plain, HwpxDecryptor.inflate(buf.copyOf(n), 1_000_000) {})
        assertFailsWith<ParseLimitExceededException> { HwpxDecryptor.inflate(buf.copyOf(n), 100_000) {} }
        assertFailsWith<HwpxDecryptException> { HwpxDecryptor.inflate(ByteArray(64) { 0x7F }, 1_000_000) {} }
    }

    @Test
    fun 암호의_UTF_8_바이트는_String_을_거치지_않는다() {
        assertContentEquals("한a".toByteArray(Charsets.UTF_8), HwpxDecryptor.utf8("한a".toCharArray()))
        // 짝 잃은 대리 문자는 인코더의 대체 바이트로(예외를 내지 않는다).
        assertTrue(HwpxDecryptor.utf8(charArrayOf('a', '\uD800')).size >= 2)
    }

    @Test
    fun 닫으면_열쇠를_0_으로_덮는다() {
        val d = HwpxDecryptor.create("1234".toCharArray(), setOf("SHA-256"))
        val keysField = HwpxDecryptor::class.java.getDeclaredField("startKeys").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val keys = keysField.get(d) as Map<String, ByteArray>
        assertTrue(keys.values.single().any { it != 0.toByte() })
        d.close()
        assertTrue(keys.values.single().all { it == 0.toByte() })
    }

    // ---- 검토에서 더한 것: 풀어 내는 양과 열쇠 유도의 상한 ----

    private fun lockedPackage(t: TinyHwpx, limits: ParseLimits): HwpxPackage =
        HwpxPackage.open(ByteArrayDocumentSource(t.build(), "x.hwpx"), limits).also {
            assertEquals(UnlockResult.Unlocked, it.unlock("1234".toCharArray()) {})
            it.loadContents()
        }

    @Test
    fun 잠긴_XML_은_파서_상한을_한_바이트_넘는_데까지만_푼다() {
        // 평문 구역이 파서 상한(`maxXmlBytes`)을 넘으면 파서가 거기서 멈춘다. 잠긴 구역도 그 앞까지만 풀어야 한다 —
        // 다 풀면 작은 암호문 하나가 평문 상한(256 MiB)을 통째로 메모리에 올린다.
        val t = TinyHwpx().section(sec(*(1..400).map { p("잠긴 문단 $it") }.toTypedArray()))
            .lock("1234".toCharArray(), setOf("Contents/section0.xml"))
        lockedPackage(t, ParseLimits(maxXmlBytes = 4000)).use { pkg ->
            val n = assertNotNull(pkg.open("Contents/section0.xml")).use { it.readBytes().size }
            assertEquals(4001, n)
        }
    }

    @Test
    fun 잠긴_그림은_청한_상한을_넘게_풀지_않고_null_을_준다() {
        val big = ByteArray(5000) { 'a'.code.toByte() }
        val t = TinyHwpx().section(sec(p("본문"))).binary("image1", "BinData/image1.png", "image/png", big)
            .lock("1234".toCharArray(), setOf("Contents/section0.xml", "BinData/image1.png"))
        // 평문 상한(`maxSingleOutput`)이 그림보다 작아도 청한 상한이 더 작으면 '너무 크다' 가 아니라 null 이다
        // (평문 항목의 `readAll` 과 같다). 예전에는 평문 상한까지 풀다 던졌다.
        lockedPackage(t, ParseLimits(maxSingleOutput = 3000)).use { pkg ->
            assertEquals(null, pkg.readBytes("BinData/image1.png", 1000))
            assertFailsWith<ParseLimitExceededException> { pkg.readBytes("BinData/image1.png", 10_000) }
        }
        lockedPackage(t, ParseLimits()).use { pkg -> assertContentEquals(big, pkg.readBytes("BinData/image1.png", 5000)) }
    }

    @Test
    fun 암호_확인은_가장_작은_항목을_끝까지_풀지_않는다() {
        // 암호문이 가장 작은 항목(0 으로 채운 1.5 MB 그림 — 암호문은 1.5 KB 남짓)이 곧 압축 폭탄일 수 있다. 확인은
        // 앞부분만 푼다. 구역은 잘 줄지 않는 글로 채워 암호문이 그림보다 크게 한다.
        val zeros = ByteArray(1536 * 1024)
        val rnd = java.util.Random(7)
        val t = TinyHwpx().section(sec(*(1..400).map { p(java.lang.Long.toHexString(rnd.nextLong())) }.toTypedArray()))
            .binary("image1", "BinData/image1.png", "image/png", zeros)
            .lock("1234".toCharArray(), setOf("Contents/section0.xml", "BinData/image1.png"))
        HwpxPackage.open(ByteArrayDocumentSource(t.build(), "x.hwpx"), ParseLimits(maxSingleOutput = 1024 * 1024)).use { pkg ->
            assertEquals(UnlockResult.Unlocked, pkg.unlock("1234".toCharArray()) {})
            assertEquals(UnlockResult.WrongPassword, pkg.unlock("4321".toCharArray()) {})
        }

        // 가장 작은 암호문도 클 수 있다(잠긴 것이 큰 구역 하나뿐). 확인은 암호문도 앞부분만 읽는다 — 예전에는 암호문을
        // 통째로 읽다 평문 상한에 걸려 '너무 크다' 로 끝났다.
        val bulky = TinyHwpx().section(sec(*(1..60_000).map { p(java.lang.Long.toHexString(rnd.nextLong())) }.toTypedArray()))
            .lock("1234".toCharArray(), setOf("Contents/section0.xml"))
        HwpxPackage.open(ByteArrayDocumentSource(bulky.build(), "x.hwpx"), ParseLimits(maxSingleOutput = 512 * 1024)).use { pkg ->
            assertEquals(UnlockResult.Unlocked, pkg.unlock("1234".toCharArray()) {})
        }
    }

    @Test
    fun 빈_항목으로는_암호를_확인하지_않는다() {
        // 평문이 빈 항목(`size="0"`, 암호문 0 바이트)은 어떤 열쇠로도 '풀린다'. 그것으로 확인하면 틀린 암호가 통과하고,
        // 사용자는 '틀렸다' 대신 '깨진 파일' 을 본다.
        val base = lockedDoc()
        val withEmpty = rebuild(base) { entries ->
            val manifest = String(entries.getValue("META-INF/manifest.xml"))
            val first = Regex("<odf:file-entry .*?</odf:file-entry>").find(manifest)!!.value
            val empty = first.replace(Regex("full-path=\"[^\"]*\""), "full-path=\"Scripts/empty\"")
                .replace(Regex(" size=\"\\d+\""), " size=\"0\"")
            entries["META-INF/manifest.xml"] = manifest.replace("</odf:manifest>", "$empty</odf:manifest>").toByteArray()
            entries["Scripts/empty"] = ByteArray(0)
        }
        val wrong = Hwpx.open(withEmpty, "4321".toCharArray())
        assertTrue(((wrong as OpenOutcome.Failed).failure as OpenFailure.PasswordRequired).wrongPassword, wrong.toString())
        assertTrue(Hwpx.open(withEmpty, "1234".toCharArray()) is OpenOutcome.Success)
    }

    @Test
    fun 열쇠_유도_반복_수의_합에_상한이_있다() {
        // 항목마다 salt 가 다르면 항목마다 유도한다. 반복 수 상한만으로는 '항목 수 × 반복 수' 가 묶이지 않는다.
        val pw = "1234".toCharArray()
        val plain = "<x/>".toByteArray()
        val entries = (1..3).map { i ->
            val enc = TestCrypto.encrypt(pw, plain)
            EncryptedEntry("e$i", plain.size.toLong(), "SHA-256", enc.checksum, 32, enc.iv, enc.salt, 1024, 32, "SHA-256", 32) to enc.data
        }
        HwpxDecryptor.create(pw, setOf("SHA-256"), iterationBudget = 2 * 1024).use { d ->
            assertContentEquals(plain, d.decrypt(entries[0].first, entries[0].second, 1000) {})
            assertContentEquals(plain, d.decrypt(entries[1].first, entries[1].second, 1000) {})
            // 캐시에서 꺼낸 열쇠는 세지 않는다.
            assertContentEquals(plain, d.decrypt(entries[0].first, entries[0].second, 1000) {})
            assertFailsWith<ParseLimitExceededException> { d.decrypt(entries[2].first, entries[2].second, 1000) {} }
        }
    }

    @Test
    fun 잠긴_항목의_평문은_스트림을_닫으면_0_으로_덮인다() {
        val t = TinyHwpx().section(sec(p("지워질 평문"))).lock("1234".toCharArray(), locked)
        lockedPackage(t, ParseLimits()).use { pkg ->
            val stream = assertNotNull(pkg.open("Contents/section0.xml"))
            assertTrue(stream is WipingInputStream, stream.javaClass.name)
            assertTrue("지워질 평문" in String(stream.use { it.readBytes() }, Charsets.UTF_8))
        }
        val plain = "평문".toByteArray()
        WipingInputStream(plain).close()
        assertTrue(plain.all { it == 0.toByte() })
    }

    /** ZIP 의 항목을 이름 → 바이트로 고쳐 다시 싼다(저장 방식은 원래대로, 새 항목은 저장). */
    private fun rebuild(bytes: ByteArray, edit: (MutableMap<String, ByteArray>) -> Unit): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        val stored = HashSet<String>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                entries[e.name] = z.readBytes()
                if (e.method == java.util.zip.ZipEntry.STORED) stored.add(e.name)
            }
        }
        val before = entries.keys.toSet()
        edit(entries)
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            for ((n, d) in entries) {
                val e = java.util.zip.ZipEntry(n)
                if (n in stored || n !in before) {
                    e.method = java.util.zip.ZipEntry.STORED
                    e.size = d.size.toLong()
                    e.compressedSize = d.size.toLong()
                    e.crc = java.util.zip.CRC32().apply { update(d) }.value
                }
                z.putNextEntry(e)
                z.write(d)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun replaceInZip(bytes: ByteArray, name: String, edit: (String) -> String): ByteArray {
        val entries = ArrayList<Triple<String, ByteArray, Boolean>>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val data = z.readBytes()
                entries.add(Triple(e.name, if (e.name == name) edit(String(data)).toByteArray() else data, e.method == java.util.zip.ZipEntry.STORED))
            }
        }
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            for ((n, d, stored) in entries) {
                val e = java.util.zip.ZipEntry(n)
                if (stored) {
                    e.method = java.util.zip.ZipEntry.STORED
                    e.size = d.size.toLong()
                    e.compressedSize = d.size.toLong()
                    e.crc = java.util.zip.CRC32().apply { update(d) }.value
                }
                z.putNextEntry(e)
                z.write(d)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
