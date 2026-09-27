package io.github.donggi.iroiroviewer.format.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `encryption.xml` 이 DRM 인지 글꼴 난독화인지 가린다.
 *
 * **틀리는 두 방향의 대가가 다르다.** DRM 을 글꼴로 보면 사용자가 깨진 글자를 보고
 * 앱이 고장 난 줄 알고, 글꼴을 DRM 으로 보면 **멀쩡히 읽히는 상업 EPUB 을 통째로
 * 거절한다.** 그래서 둘 다 시험한다.
 */
class EpubEncryptionTest {

    private fun parse(xml: String) = EpubEncryption.parse(xml.byteInputStream())

    private val ENC_NS = "http://www.w3.org/2001/04/xmlenc#"
    private val AES = "http://www.w3.org/2001/04/xmlenc#aes128-cbc"

    /** `(URI, 알고리즘)` 쌍마다 `EncryptedData` 하나. 알고리즘이 null 이면 적지 않는다. */
    private fun doc(vararg refs: Pair<String, String?>): String {
        val items = refs.joinToString("") { (uri, alg) ->
            val method = alg?.let { """<enc:EncryptionMethod Algorithm="$it"/>""" } ?: ""
            """<enc:EncryptedData>$method<enc:CipherData><enc:CipherReference URI="$uri"/></enc:CipherData></enc:EncryptedData>"""
        }
        return """<?xml version="1.0"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="$ENC_NS">$items</encryption>"""
    }

    @Test
    fun 난독화된_글꼴은_풀_글꼴로_잡는다() {
        val result = parse(
            doc("OEBPS/fonts/a.otf" to FontObfuscation.IDPF, "OEBPS/fonts/b.woff2" to FontObfuscation.ADOBE)
        )
        assertEquals(
            mapOf("OEBPS/fonts/a.otf" to FontObfuscation.IDPF, "OEBPS/fonts/b.woff2" to FontObfuscation.ADOBE),
            result.entries,
        )
        val plan = assertIs<EpubEncryption.Plan.Open>(EpubEncryption.plan(result))
        assertEquals(2, plan.obfuscated.size)
        assertEquals(0, plan.lockedFonts)
    }

    @Test
    fun 본문이_잠기면_열지_않는다() {
        val result = parse(doc("OEBPS/fonts/a.otf" to FontObfuscation.IDPF, "OEBPS/text/ch1.xhtml" to AES))
        assertIs<EpubEncryption.Plan.Refuse>(EpubEncryption.plan(result))
    }

    @Test
    fun 난독화_방식이라도_본문이면_연다() {
        // 명세는 난독화를 글꼴에만 쓰라고 하지만, 방식이 난독화면 **풀 수 있다** — 거절할 이유가 없다.
        val plan = EpubEncryption.plan(parse(doc("OEBPS/images/a.png" to FontObfuscation.IDPF)))
        assertIs<EpubEncryption.Plan.Open>(plan)
    }

    @Test
    fun 풀_수_없는_방식의_글꼴만이면_알리고_연다() {
        // 본문은 멀쩡하다. 그 글꼴이 기본 글꼴로 대신 보일 뿐이다.
        val plan = assertIs<EpubEncryption.Plan.Open>(
            EpubEncryption.plan(parse(doc("OEBPS/fonts/a.otf" to AES, "OEBPS/fonts/b.ttf" to null)))
        )
        assertEquals(0, plan.obfuscated.size)
        assertEquals(2, plan.lockedFonts)
    }

    @Test
    fun 무엇이_걸렸는지_모르면_열지_않는다() {
        // 비어 있는 `<EncryptedData/>` 를 '아마 글꼴이겠지' 로 넘기면, 본문이 암호화된
        // 책을 깨진 글자로 보여 주게 된다. 모르면 모른다고 말한다.
        val result = parse("""<?xml version="1.0"?><encryption><EncryptedData/></encryption>""")
        assertIs<EpubEncryption.Plan.Refuse>(EpubEncryption.plan(result))
    }

    @Test
    fun 풀리지_않는_주소가_하나라도_있으면_열지_않는다() {
        // **오래 구멍이었다.** 풀리지 않는 주소를 조용히 버렸기 때문에, 글꼴 하나와 책 밖을
        // 가리키는 본문 주소 하나가 적힌 책이 '글꼴만 걸렸다' 로 통과했다.
        val result = parse(doc("OEBPS/fonts/a.otf" to FontObfuscation.IDPF, "../../escape.xhtml" to AES))
        assertEquals(1, result.unresolved)
        assertIs<EpubEncryption.Plan.Refuse>(EpubEncryption.plan(result))
    }

    @Test
    fun 방식은_항목마다_따로다() {
        // 앞 항목의 방식이 방식을 적지 않은 뒤 항목에 붙으면, 잠긴 본문이 '난독화' 로 풀린다.
        val result = parse(doc("OEBPS/fonts/a.otf" to FontObfuscation.IDPF, "OEBPS/text/ch1.xhtml" to null))
        assertEquals(null, result.entries["OEBPS/text/ch1.xhtml"])
        assertIs<EpubEncryption.Plan.Refuse>(EpubEncryption.plan(result))
    }

    /**
     * **`EncryptedData` 가 하나도 없으면 잠긴 것이 없다.** 예전 판은 파일 전체가 비었으면 거절해,
     * 아무것도 잠기지 않은 책을 '잠겼다' 고 말했다(검토가 잡았다). 명세상 틀린 파일이지만 거절할
     * 이유는 아니다.
     */
    @Test
    fun 잠긴_것이_하나도_적혀_있지_않으면_연다() {
        val result = parse("""<?xml version="1.0"?><encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"/>""")
        val plan = assertIs<EpubEncryption.Plan.Open>(EpubEncryption.plan(result))
        assertEquals(0, plan.obfuscated.size + plan.lockedFonts)
    }

    /**
     * **가리키는 곳 없는 블록이 하나라도 있으면 거절한다.** 예전 판은 그런 블록을 조용히 건너뛰어,
     * 글꼴 하나가 함께 적혀 있으면 '글꼴만 걸렸다' 로 통과했다 — 전부 비었을 때만 거절했다.
     */
    @Test
    fun 가리키는_곳_없는_블록이_하나라도_있으면_열지_않는다() {
        val xml = """<?xml version="1.0"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="$ENC_NS">
              <enc:EncryptedData><enc:EncryptionMethod Algorithm="${FontObfuscation.IDPF}"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/fonts/a.otf"/></enc:CipherData></enc:EncryptedData>
              <enc:EncryptedData><enc:EncryptionMethod Algorithm="$AES"/>
                <enc:CipherData><enc:CipherValue>AAAA</enc:CipherValue></enc:CipherData></enc:EncryptedData>
            </encryption>"""
        val result = parse(xml)
        assertEquals(1, result.unresolved)
        assertIs<EpubEncryption.Plan.Refuse>(EpubEncryption.plan(result))
    }

    @Test
    fun 퍼센트_인코딩된_이름도_푼다() {
        val result = parse(doc("OEBPS/fonts/%ED%95%9C%EA%B8%80.otf" to FontObfuscation.IDPF))
        assertEquals(setOf("OEBPS/fonts/한글.otf"), result.paths)
    }
}
