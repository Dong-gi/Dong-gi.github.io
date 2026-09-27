package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EPUB 을 여는 길 전체를 JVM 에서 돈다.
 *
 * **에뮬레이터가 필요 없는 것이 이 모듈을 순수 JVM 으로 둔 값이다** — ZIP·XML·문자열뿐이라
 * 초 단위로 돌고, 그래서 깨진 표본을 마음껏 만들어 볼 수 있다. 11단계의 PDF 는 반대로
 * pdfium 이 있어야 해서 같은 종류의 시험을 계측으로 밀어야 했다.
 */
class EpubOpenerTest {

    private val temp = ArrayList<File>()

    @AfterTest
    fun cleanUp() {
        temp.forEach { it.delete() }
    }

    private fun file(bytes: ByteArray): File {
        val f = File.createTempFile("iroiro-epub", ".epub").also { temp.add(it) }
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun 장이_언어를_적지_않으면_책의_언어를_단다() = runTest {
        // 일본어 책에 keep-all 이 걸리면 줄 끝이 들쭉날쭉하다 — 언어를 알아야 껍데기가 되돌린다(`HtmlShell`).
        val book = open(TinyEpub.bytes(listOf(TinyEpub.Chapter("OEBPS/c1.xhtml", "一", "<p>吾輩は猫である。</p>")), language = "ja"))
        book.use { assertTrue("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ja\">" in book.chapterHtml(0)!!, book.chapterHtml(0)) }
    }

    /** 연다. 실패하면 시험이 거기서 끝난다. */
    private suspend fun open(bytes: ByteArray): EpubBook {
        var orphan: OpenedDocument? = null
        val outcome = EpubOpener().open(FileDocumentSource(file(bytes))) { orphan = it }
        assertIs<OpenOutcome.Success>(outcome, "열지 못했다: $outcome")
        return outcome.document as EpubBook
    }

    private suspend fun failure(bytes: ByteArray): OpenFailure {
        var orphan: OpenedDocument? = null
        try {
            val outcome = EpubOpener().open(FileDocumentSource(file(bytes))) { orphan = it }
            assertIs<OpenOutcome.Failed>(outcome, "열려서는 안 된다: $outcome")
            return outcome.failure
        } finally {
            orphan?.close()
        }
    }

    private val threeChapters = listOf(
        TinyEpub.Chapter("ch1.xhtml", "첫째 장", "<h1>첫째 장</h1><p>하나</p>"),
        TinyEpub.Chapter("ch2.xhtml", "둘째 장", "<h1>둘째 장</h1><p>둘</p>"),
        TinyEpub.Chapter("ch3.xhtml", "셋째 장", "<h1>셋째 장</h1><p>셋</p>"),
    )

    @Test
    fun 차례와_제목을_읽는다() = runTest {
        val book = open(TinyEpub.bytes(threeChapters, title = "한글 제목"))
        book.use {
            assertEquals("한글 제목", it.title)
            assertEquals("ko", it.language)
            assertEquals(3, it.spine.size)
            assertEquals("OEBPS/ch1.xhtml", it.spine[0].path)
            assertEquals("application/xhtml+xml", it.spine[0].mediaType)
        }
    }

    @Test
    fun 본문이_위생을_지나서_나온다() = runTest {
        val book = open(
            TinyEpub.bytes(
                listOf(
                    TinyEpub.Chapter(
                        "ch1.xhtml", "장",
                        "<p>본문</p><script>alert(1)</script>" +
                            """<img src="https://tracker/p.gif"/><img src="img/a.png"/>""",
                    )
                ),
                extras = mapOf("img/a.png" to ByteArray(8)),
            )
        )
        book.use {
            val html = assertNotNull(it.chapterHtml(0))
            assertTrue("본문" in html, html)
            assertFalse("alert" in html, html)
            assertFalse("tracker" in html, html)
            // **상대 주소는 그대로 남는다.** 절대 주소로 바꾸지 않는 이유는
            // `chapterHtml` 의 주석에 있다 — 경로 계산이 두 벌이 되지 않게 한다.
            assertTrue("""src="img/a.png"""" in html, html)
            assertFalse(it.unsupported.isEmpty, "버린 것을 세지 않았다")
            // **표준 모드여야 한다.** 위생기가 원본의 DOCTYPE 을 지우므로 껍데기가 다시 붙이지 않으면
            // `text/html` 로 받은 WebView 가 쿼크 모드로 그린다(원본 XHTML 은 어디서나 표준 모드다).
            assertTrue(html.startsWith("<!DOCTYPE html>"), html.take(80))
        }
    }

    @Test
    fun 책_안에_없는_그림은_주소가_지워진다() = runTest {
        val book = open(
            TinyEpub.bytes(
                listOf(TinyEpub.Chapter("ch1.xhtml", "장", """<img src="없는그림.png"/><p>글</p>"""))
            )
        )
        book.use {
            val html = assertNotNull(it.chapterHtml(0))
            assertFalse("없는그림" in html, html)
            assertTrue("글" in html, html)
        }
    }

    @Test
    fun 상대_주소가_폴더를_넘어_풀린다() = runTest {
        val book = open(
            TinyEpub.bytes(
                listOf(TinyEpub.Chapter("text/ch1.xhtml", "장", """<img src="../img/a.png"/>""")),
                extras = mapOf("img/a.png" to ByteArray(4)),
            )
        )
        book.use {
            val html = assertNotNull(it.chapterHtml(0))
            // 주소는 그대로 두되, **책 안에 있는지 확인한 뒤에** 남긴다.
            assertTrue("../img/a.png" in html, html)
            assertTrue(it.has("OEBPS/img/a.png"))
            assertNotNull(it.openResource("OEBPS/img/a.png")).close()
        }
    }

    @Test
    fun 위로_올라가는_주소는_책_밖이다() = runTest {
        val book = open(
            TinyEpub.bytes(
                listOf(
                    TinyEpub.Chapter(
                        "ch1.xhtml", "장",
                        """<img src="../../../../etc/passwd"/><p>글</p>""",
                    )
                )
            )
        )
        book.use {
            val html = assertNotNull(it.chapterHtml(0))
            assertFalse("passwd" in html, html)
            assertNull(it.openResource("etc/passwd"))
        }
    }

    @Test
    fun EPUB3_목차를_읽는다() = runTest {
        val book = open(TinyEpub.bytes(threeChapters, nav = true))
        book.use {
            assertEquals(3, it.toc.size, it.toc.toString())
            assertEquals("첫째 장", it.toc[0].title)
            assertEquals(0, it.toc[0].spineIndex)
            assertEquals(2, it.toc[2].spineIndex)
            // `landmarks` 의 '표지' 가 섞여 들어오면 안 된다.
            assertFalse(it.toc.any { e -> e.title == "표지" }, it.toc.toString())
        }
    }

    @Test
    fun EPUB2_목차도_읽는다() = runTest {
        val book = open(TinyEpub.bytes(threeChapters, nav = false))
        book.use {
            assertEquals(3, it.toc.size, it.toc.toString())
            assertEquals("둘째 장", it.toc[1].title)
            assertEquals(1, it.toc[1].spineIndex)
        }
    }

    @Test
    fun 장_사이_링크를_차례_번호로_바꾼다() = runTest {
        val book = open(TinyEpub.bytes(threeChapters))
        book.use {
            assertEquals(1, it.spineIndexOf("OEBPS/ch2.xhtml"))
            assertEquals(-1, it.spineIndexOf("OEBPS/nav.xhtml"))
        }
    }

    @Test
    fun mimetype_이_어긋나도_열되_경고를_남긴다() = runTest {
        // 명세는 압축하지 말라고 하지만 실물에는 어긴 파일이 흔하다. 거절하면
        // 멀쩡히 읽히는 책을 '깨졌다' 고 말하게 된다.
        val book = open(TinyEpub.bytes(threeChapters, deflateMimetype = true))
        book.use { assertEquals(3, it.spine.size) }

        val noMime = open(TinyEpub.bytes(threeChapters, includeMimetype = false))
        noMime.use {
            assertTrue(
                it.warnings.any { w -> w.code == EpubOpener.WARN_MIMETYPE },
                it.warnings.toString(),
            )
        }
    }

    @Test
    fun container_가_없으면_열지_않는다() = runTest {
        assertIs<OpenFailure.Corrupt>(
            failure(TinyEpub.bytes(threeChapters, includeContainer = false))
        )
    }

    @Test
    fun 읽을_차례가_없으면_열지_않는다() = runTest {
        assertIs<OpenFailure.Corrupt>(failure(TinyEpub.bytes(emptyList())))
    }

    @Test
    fun EPUB_이_아니면_열지_않는다() = runTest {
        // **'깨진 파일' 이지 '입출력 실패' 가 아니다.** 예전에는 `Io` 였다 — 화면에 '입출력이 실패했습니다' 가 떠
        // 디스크가 고장 난 것처럼 읽혔다(PDF·CFB 는 같은 이유로 따로 가린다).
        assertIs<OpenFailure.Corrupt>(failure("이건 EPUB 이 아니다".toByteArray()))
    }

    @Test
    fun 덜_받은_책은_깨진_파일이다() = runTest {
        // 내려받다 끊긴 책 — ZIP 의 중앙 디렉터리(파일 끝)가 없다. 실세계 말뭉치를 반으로 자른 책이 전부
        // `Io`('입출력이 실패했습니다')로 끝났다(검토가 잡았다).
        val whole = TinyEpub.bytes(threeChapters)
        assertIs<OpenFailure.Corrupt>(failure(whole.copyOf(whole.size / 2)))
        assertIs<OpenFailure.Corrupt>(failure(whole.copyOf(whole.size - 1)))
    }

    @Test
    fun 닫은_책은_자원을_주지_않는다() = runTest {
        val book = open(TinyEpub.bytes(threeChapters))
        assertNotNull(book.openResource("OEBPS/ch1.xhtml")).close()
        book.close()
        book.close() // 두 번 닫아도 안전하다
        assertNull(book.openResource("OEBPS/ch1.xhtml"))
    }

    @Test
    fun 본문을_읽을_때마다_버린_것이_쌓인다() = runTest {
        val book = open(
            TinyEpub.bytes(
                listOf(
                    TinyEpub.Chapter("ch1.xhtml", "1", "<script>a</script>"),
                    TinyEpub.Chapter("ch2.xhtml", "2", "<script>b</script>"),
                )
            )
        )
        book.use {
            // 열자마자 0 인 것이 정직한 상태다 — 본문을 아직 읽지 않았다.
            assertTrue(it.unsupported.isEmpty)
            it.chapterHtml(0)
            val after = it.unsupported.total
            it.chapterHtml(1)
            assertTrue(it.unsupported.total > after, "두 번째 장의 몫이 안 세어졌다")
        }
    }

    @Test
    fun 차례에_올라온_그림은_글이_아니라_그림으로_보인다() = runTest {
        // 만화형 EPUB(IDPF haruko-jpeg)은 JPEG 를 곧바로 차례에 둔다. 예전에는 그 바이트를 UTF-8 글로 읽어
        // 뜻 없는 글자와 가짜 태그(`<i/>`·`onX=…`)가 화면을 채웠다 — 실세계 말뭉치가 잡았다.
        // 그림 바이트에 `<b onload=x>` 를 일부러 심는다 — 글로 읽으면 그것이 태그로 살아난다.
        val picture = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) +
            "<b onload=x>가짜</b>".toByteArray() + ByteArray(40) { (it * 37).toByte() }
        val book = open(
            TinyEpub.bytes(
                listOf(TinyEpub.Chapter("ch1.xhtml", "1", "<p>글</p>")),
                extras = mapOf("p1.png" to picture, "voice.mp3" to ByteArray(64) { 0x55 }),
                spineExtras = listOf("p1.png", "voice.mp3"),
            )
        )
        book.use {
            assertEquals(3, it.spine.size)
            val page = assertNotNull(it.chapterHtml(1))
            val encoded = java.util.Base64.getEncoder().encodeToString(picture)
            assertTrue("<img alt=\"\" src=\"data:image/png;base64,$encoded\"/>" in page, page.take(300))
            assertFalse("가짜" in page, "그림 바이트가 글로 새어 나왔다")
            assertTrue(it.unsupported.isEmpty, "그린 그림을 버린 것으로 셌다: ${it.unsupported.snapshot()}")
            // 그릴 수 없는 형식은 빈 쪽이고, 빠진 것이 있다고 센다.
            val voice = assertNotNull(it.chapterHtml(2))
            assertFalse("UUUU" in voice, "소리 바이트가 글로 새어 나왔다")
            assertEquals(1, it.unsupported.snapshot()[UnsupportedFeatures.UNKNOWN_ELEMENT])
        }
    }

    @Test
    fun 형식을_모른다고_적은_차례_항목은_내용을_보고_가른다() = runTest {
        // 매니페스트가 `application/octet-stream` 이라 적은 항목을 통째로 '글이 아닌 것' 으로 보면, 형식만
        // 잘못 적은 XHTML 장이 빈 쪽이 된다(그림 차례를 고치기 전에는 글로 읽혀 보이던 장이다 — 검토가 잡았다).
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(24) { 7 }
        val book = open(
            TinyEpub.bytes(
                listOf(TinyEpub.Chapter("ch1.xhtml", "1", "<p>글</p>")),
                extras = mapOf(
                    // BOM 은 눈에 보이지 않는 글자라 소스에 그대로 적지 않는다.
                    "p2.bin" to ("\uFEFF\n\n" + TinyEpub.page("2", "<p>잘못 적힌 장</p>")).toByteArray(),
                    "p3.bin" to png,
                    "p4.bin" to ByteArray(64) { 0x55 },
                ),
                spineExtras = listOf("p2.bin", "p3.bin", "p4.bin"),
            )
        )
        book.use {
            assertEquals("application/octet-stream", it.spine[1].mediaType)
            assertTrue("잘못 적힌 장" in assertNotNull(it.chapterHtml(1)), "형식을 잘못 적은 XHTML 장이 비었다")
            val picture = java.util.Base64.getEncoder().encodeToString(png)
            assertTrue("src=\"data:image/png;base64,$picture\"" in assertNotNull(it.chapterHtml(2)))
            assertTrue(it.unsupported.isEmpty, "보인 것을 버린 것으로 셌다: ${it.unsupported.snapshot()}")
            // 어느 쪽도 아니면 예전처럼(그림 차례를 고친 뒤처럼) 빈 쪽이고 빠진 것이 있다고 센다.
            assertFalse("UUUU" in assertNotNull(it.chapterHtml(3)), "모르는 바이트가 글로 새어 나왔다")
            assertEquals(1, it.unsupported.snapshot()[UnsupportedFeatures.UNKNOWN_ELEMENT])
        }
    }

    // ---- 글꼴 난독화 ------------------------------------------------------------------

    private val uuid = "urn:uuid:36d5078e-ff7d-468e-a5f3-f47c14b91f2f"

    /** 파이썬 hashlib 으로 따로 구한 열쇠(`FontObfuscationTest` 참고). 여기서 우리 함수를 쓰지 않는다. */
    private val idpfKey = "d2855e2244f7ae733ab0d7bbadf7f1c2a44a939b"
    private val adobeKey = "36d5078eff7d468ea5f3f47c14b91f2f"

    private fun obfuscate(plain: ByteArray, keyHex: String, n: Int): ByteArray {
        val key = ByteArray(keyHex.length / 2) { keyHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return plain.copyOf().also { for (i in 0 until minOf(n, it.size)) it[i] = (it[i].toInt() xor key[i % key.size].toInt()).toByte() }
    }

    private val fontA = ByteArray(2500) { (it * 13 + 1).toByte() }
    private val fontB = ByteArray(1500) { (it * 7 + 3).toByte() }

    private fun encryptionXml(vararg refs: Pair<String, String>): ByteArray {
        val items = refs.joinToString("") { (uri, alg) ->
            """<enc:EncryptedData><enc:EncryptionMethod Algorithm="$alg"/><enc:CipherData>""" +
                """<enc:CipherReference URI="$uri"/></enc:CipherData></enc:EncryptedData>"""
        }
        return ("""<?xml version="1.0"?><encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" """ +
            """xmlns:enc="http://www.w3.org/2001/04/xmlenc#">$items</encryption>""").toByteArray()
    }

    @Test
    fun 난독화된_글꼴을_풀어서_준다() = runTest {
        val book = open(
            TinyEpub.bytes(
                threeChapters,
                identifier = uuid,
                extras = mapOf(
                    "fonts/a.otf" to obfuscate(fontA, idpfKey, 1040),
                    "fonts/b.ttf" to obfuscate(fontB, adobeKey, 1024),
                ),
                rootEntries = mapOf(
                    "META-INF/encryption.xml" to encryptionXml(
                        "OEBPS/fonts/a.otf" to FontObfuscation.IDPF,
                        "OEBPS/fonts/b.ttf" to FontObfuscation.ADOBE,
                    ),
                ),
            )
        )
        book.use {
            assertContentEquals(fontA, it.openResource("OEBPS/fonts/a.otf")!!.use { s -> s.readBytes() })
            assertContentEquals(fontB, it.openResource("OEBPS/fonts/b.ttf")!!.use { s -> s.readBytes() })
            // 다 풀었으면 알릴 것이 없다.
            assertTrue(it.warnings.none { w -> w.code == EpubOpener.WARN_OBFUSCATED }, "${it.warnings}")
        }
    }

    @Test
    fun 열쇠를_만들_수_없으면_알리고_연다() = runTest {
        // 고유 식별자가 없으면 IDPF 열쇠가 없다. 본문은 멀쩡하므로 책은 열고, 글꼴은 날것으로 둔다.
        val book = open(
            TinyEpub.bytes(
                threeChapters,
                extras = mapOf("fonts/a.otf" to obfuscate(fontA, idpfKey, 1040)),
                rootEntries = mapOf(
                    "META-INF/encryption.xml" to encryptionXml("OEBPS/fonts/a.otf" to FontObfuscation.IDPF),
                ),
            )
        )
        book.use {
            assertTrue(it.warnings.any { w -> w.code == EpubOpener.WARN_OBFUSCATED }, "${it.warnings}")
        }
    }

    /**
     * **난독화 방식이 본문에 걸렸는데 열쇠를 못 만들면 열지 않는다.** 예전 판은 그것을 '글꼴을 못
     * 풀었다' 로 세고 열어, 앞 1040 바이트가 뒤섞인 장을 화면에 보냈다(검토가 잡았다).
     */
    @Test
    fun 열쇠를_못_만드는_난독화_본문은_열지_않는다() = runTest {
        val f = failure(
            TinyEpub.bytes(
                threeChapters,
                rootEntries = mapOf(
                    "META-INF/encryption.xml" to encryptionXml("OEBPS/ch1.xhtml" to FontObfuscation.IDPF),
                ),
            )
        )
        assertIs<OpenFailure.Encrypted>(f)
    }

    /**
     * **식별자 끝의 유니코드 공백을 벗기지 않는다.** IDPF 열쇠는 XML 공백 넷(U+0020·0009·000D·000A)만
     * 뺀 식별자의 SHA-1 이다. 예전 판은 코틀린 `trim()` 으로 NBSP 까지 벗겨 열쇠가 틀렸고, 글꼴이
     * **아무 알림 없이** 깨졌다.
     */
    @Test
    fun 식별자_끝의_NBSP_는_열쇠의_일부다() = runTest {
        val id = "isbn:9788901000000\u00A0"
        val key = java.security.MessageDigest.getInstance("SHA-1").digest(id.toByteArray(Charsets.UTF_8))
        val keyHex = key.joinToString("") { "%02x".format(it) }
        val book = open(
            TinyEpub.bytes(
                threeChapters,
                identifier = id,
                extras = mapOf("fonts/a.otf" to obfuscate(fontA, keyHex, 1040)),
                rootEntries = mapOf(
                    "META-INF/encryption.xml" to encryptionXml("OEBPS/fonts/a.otf" to FontObfuscation.IDPF),
                ),
            )
        )
        book.use {
            assertContentEquals(fontA, it.openResource("OEBPS/fonts/a.otf")!!.use { s -> s.readBytes() })
        }
    }

    @Test
    fun 본문이_잠긴_책은_열지_않는다() = runTest {
        val f = failure(
            TinyEpub.bytes(
                threeChapters,
                identifier = uuid,
                rootEntries = mapOf(
                    "META-INF/encryption.xml" to encryptionXml(
                        "OEBPS/ch1.xhtml" to "http://www.w3.org/2001/04/xmlenc#aes128-cbc",
                    ),
                ),
            )
        )
        assertIs<OpenFailure.Encrypted>(f)
    }
}
