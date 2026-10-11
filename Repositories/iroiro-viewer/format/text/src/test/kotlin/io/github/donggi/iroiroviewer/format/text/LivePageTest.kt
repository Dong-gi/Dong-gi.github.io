package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 스크립트를 실행하는 쪽([LivePage])이 정하는 것 — 쪽이 어떤 인코딩으로 읽힐지, 쪽이 가리키는 파일을 어떤 형식으로 내줄지 — 와,
 * 위생을 거친 쪽이 '스크립트 실행' 단추를 달지([HtmlPreview.Result.scripted]).
 *
 * 인코딩 쪽의 기준은 HTML 명세의 '미리 훑기' 다 — 브라우저가 같은 훑기를 하므로, 우리가 '문서가 말했다' 고 보고 입을 다문 쪽은
 * 브라우저도 문서의 말대로 읽는다. 어긋나면 우리 판정으로 문서의 선언을 덮거나(이긴다 — 전송 쪽의 인코딩이다), 선언 없는 옛 쪽을
 * 브라우저의 기본값(UTF-8)에 맡기게 된다.
 */
class LivePageTest {

    private fun scan(html: String): String? =
        html.toByteArray(Charsets.ISO_8859_1).let { LivePage.declaredCharset(it, it.size) }

    @Test
    fun `meta charset 을 찾는다`() {
        assertEquals("euc-kr", scan("<html><head><meta charset=\"EUC-KR\"><title>x</title>"))
        assertEquals("shift_jis", scan("<meta charset=Shift_JIS>"))
        assertEquals("utf-8", scan("<META CHARSET='UTF-8'>"))
        assertEquals("euc-kr", scan("<meta/charset=euc-kr>"))
    }

    @Test
    fun `http-equiv 와 content 의 짝`() {
        assertEquals("euc-kr", scan("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=euc-kr\">"))
        // 차례가 바뀌어도 같다 — 명세는 속성을 다 읽은 뒤에 판정한다.
        assertEquals("ks_c_5601-1987", scan("<meta content='text/html;charset=ks_c_5601-1987' http-equiv='content-type'>"))
    }

    @Test
    fun `content 만으로는 선언이 아니다`() {
        // 명세가 http-equiv="content-type" 을 함께 요구한다(`<meta name=… content=…>` 의 글에 'charset=' 이 들어 있을 수 있다).
        assertNull(scan("<meta content=\"text/html; charset=euc-kr\">"))
        assertNull(scan("<meta name=\"description\" content=\"charset=euc-kr 로 쓴 쪽\">"))
    }

    @Test
    fun `주석과 다른 태그의 속성 안에 속지 않는다`() {
        assertNull(scan("<!-- <meta charset=euc-kr> --><p>본문"))
        assertNull(scan("<div title=\"<meta charset=euc-kr>\"><p>"))
        assertNull(scan("<metadata charset=euc-kr>"))
        // `<!-->` 는 그 자리에서 닫힌 주석이다(명세가 `<!--` 의 두 `-` 도 끝으로 센다) — 그 뒤의 선언은 진짜다.
        assertEquals("euc-kr", scan("<!--><meta charset=euc-kr>"))
        // 처리 지시·닫는 태그·DOCTYPE 은 `>` 까지 건너뛴다.
        assertEquals("big5", scan("<?xml version=\"1.0\"?><!DOCTYPE html></x><meta charset=big5>"))
    }

    @Test
    fun `앞 1024 바이트 밖은 보지 않는다`() {
        assertNull(scan("a".repeat(1_020) + "<meta charset=euc-kr>"))
        assertNull(scan("<!-- " + "x".repeat(1_100) + " --><meta charset=euc-kr>"))
        assertEquals("euc-kr", scan("a".repeat(900) + "<meta charset=euc-kr>"))
    }

    @Test
    fun `모르는 이름과 잘린 선언은 선언이 아니다`() {
        assertNull(scan("<meta charset=\"x-no-such-encoding-9\">"))
        // 첫 선언이 실패면 그 `<meta>` 는 끝이다(명세의 '실패') — 같은 태그의 content 로 넘어가지 않는다.
        assertNull(scan("<meta charset=nope-nope content='text/html; charset=utf-8' http-equiv=content-type>"))
        // 태그가 1024 바이트 안에서 끝나지 않았다.
        assertNull(scan("<meta charset=\"euc-kr\""))
        assertNull(scan("<meta charset="))
    }

    @Test
    fun `content 에서 이름 꺼내기`() {
        assertEquals("euc-kr", LivePage.fromContent("text/html;charset=EUC-KR"))
        assertEquals("utf-8", LivePage.fromContent("text/html; charset = 'utf-8'"))
        assertEquals("big5", LivePage.fromContent("a charsetx charset=big5; q=1"))
        assertNull(LivePage.fromContent("text/html"))
        assertNull(LivePage.fromContent("charset=\"utf-8"))
        assertNull(LivePage.fromContent("charset="))
    }

    @Test
    fun `어떤 바이트열에도 던지지 않는다`() {
        val alphabet = "<>!-/?=\"' \tmetaMETAcharsethtp-equivcontent;xyz".toByteArray()
        val rnd = Random(20261003)
        repeat(3_000) {
            val bytes = ByteArray(rnd.nextInt(0, 1_200)) { alphabet[rnd.nextInt(alphabet.size)] }
            LivePage.declaredCharset(bytes, bytes.size)
        }
        // 길이를 바이트 배열보다 크게 줘도 배열 안에서 멈춘다.
        LivePage.declaredCharset(ByteArray(3), 10_000)
    }

    @Test
    fun `고른 인코딩이 문서의 선언을 이긴다`() {
        val head = "<meta charset=euc-kr><p>".toByteArray()
        assertEquals(
            "shift_jis",
            LivePage.transportLabel(TextEncoding.SHIFT_JIS, TextEncoding.CP949, hasBom = false, head = head, headLength = head.size),
        )
    }

    @Test
    fun `BOM 이나 문서의 선언이 있으면 브라우저에 맡긴다`() {
        val declared = "<meta charset=euc-kr><p>".toByteArray()
        assertNull(LivePage.transportLabel(null, TextEncoding.CP949, hasBom = false, head = declared, headLength = declared.size))
        val bare = "<p>본문</p>".toByteArray()
        assertNull(LivePage.transportLabel(null, TextEncoding.CP949, hasBom = true, head = bare, headLength = bare.size))
        // 브라우저는 BOM 을 사용자가 고른 것보다도 먼저 따른다 — 고른 이름을 줘도 듣지 않으니 주지 않는다.
        assertNull(LivePage.transportLabel(TextEncoding.SHIFT_JIS, TextEncoding.UTF_8, hasBom = true, head = bare, headLength = bare.size))
    }

    @Test
    fun `아무 말도 없으면 우리 판정 — UTF-8 이면 브라우저의 기본값에 맡긴다`() {
        val bare = "<p>본문</p>".toByteArray(charset("x-windows-949"))
        assertEquals("euc-kr", LivePage.transportLabel(null, TextEncoding.CP949, hasBom = false, head = bare, headLength = bare.size))
        // 문서가 적은 이름을 브라우저가 모르면 선언이 없는 것과 같다 — 우리 판정으로 물러난다.
        val unknown = "<meta charset=x-no-such-encoding-9>".toByteArray()
        assertEquals("euc-kr", LivePage.transportLabel(null, TextEncoding.CP949, false, unknown, unknown.size))
        // 우리 판정이 UTF-8 이면 주지 않는다 — 앞이 ASCII 뿐이라 UTF-8 로 본 쪽의 늦은 `<meta charset=euc-kr>`(1024 바이트 뒤)를
        // 브라우저는 읽는데, 우리가 utf-8 을 주면 그것을 덮었다.
        val late = ("<!--" + "x".repeat(1_500) + "--><meta charset=euc-kr>").toByteArray()
        assertNull(LivePage.transportLabel(null, TextEncoding.UTF_8, false, late, late.size))
    }

    @Test
    fun `브라우저가 모르는 이름은 선언으로 치지 않는다`() {
        // 자바는 알고 크롬은 모르는 이름 — 선언으로 보고 입을 다물면 크롬은 선언을 버리고 UTF-8 로 읽어 한글이 깨졌다.
        for (label in listOf("cp949", "ms949", "x-windows-949")) {
            assertFalse(LivePage.isKnownLabel(label), label)
            val head = "<meta charset=$label><p>".toByteArray()
            assertEquals("euc-kr", LivePage.transportLabel(null, TextEncoding.CP949, false, head, head.size), label)
        }
        assertTrue(LivePage.isKnownLabel("ks_c_5601-1987"))
        assertTrue(LivePage.isKnownLabel("windows-949"))
        // 브라우저가 '대체 문자' 로만 읽는 이름은 일부러 뺐다.
        assertFalse(LivePage.isKnownLabel("iso-2022-kr"))
    }

    /**
     * XML 로 내주는 쪽(`.xhtml`)은 **XML 선언**이 문서의 선언이다 — 크롬은 XML 로 읽는 쪽의 `<meta>` 를 보지 않는다. XML 선언 없이
     * `<meta charset="euc-kr">` 만 적은 EUC-KR `.xhtml` 에 인코딩을 주지 않으면 크롬이 XML 의 기본값(UTF-8)으로 읽어 한글이 깨졌다.
     * 차례: BOM > 고른 것 > XML 선언 > 우리 판정.
     */
    @Test
    fun `XML 로 내주는 쪽은 XML 선언을 보고 meta 는 보지 않는다`() {
        fun label(text: String, detected: TextEncoding = TextEncoding.CP949, chosen: TextEncoding? = null, bom: Boolean = false): String? {
            val head = text.toByteArray(Charsets.ISO_8859_1)
            return LivePage.transportLabel(chosen, detected, bom, head, head.size, xml = true)
        }
        val metaOnly = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"euc-kr\"/></head>"
        assertEquals("euc-kr", label(metaOnly), "meta 만 적은 쪽에는 우리 판정을 준다")
        // 같은 머리를 HTML 로 내주면 meta 가 선언이다(예전 규칙 그대로).
        val head = metaOnly.toByteArray(Charsets.ISO_8859_1)
        assertNull(LivePage.transportLabel(null, TextEncoding.CP949, false, head, head.size))

        assertNull(label("<?xml version=\"1.0\" encoding=\"EUC-KR\"?><html/>"), "XML 선언은 브라우저가 따른다")
        assertNull(label("<?xml version='1.0' encoding='shift_jis' standalone='yes'?><html/>"))
        assertEquals("euc-kr", label("<?xml version=\"1.0\"?><html/>"), "encoding 이 없으면 XML 의 기본값이 아니라 우리 판정")
        assertEquals("euc-kr", label("<?xml version=\"1.0\" encoding=\"cp949\"?><html/>"), "브라우저가 모르는 이름은 선언이 아니다")
        assertEquals("euc-kr", label(" <?xml version=\"1.0\" encoding=\"utf-8\"?>"), "선언은 첫 바이트에서 시작한다")
        assertEquals("euc-kr", label("<?xml version=\"1.0\" encoding=\"utf-8\""), "끝나지 않은 선언은 선언이 아니다")
        assertNull(label("<?xml version=\"1.0\"?><html/>", detected = TextEncoding.UTF_8), "판정이 UTF-8 이면 기본값과 같다")
        assertEquals("shift_jis", label("<?xml version=\"1.0\" encoding=\"euc-kr\"?>", chosen = TextEncoding.SHIFT_JIS), "고른 것이 이긴다")
        assertNull(label("<?xml version=\"1.0\"?>", bom = true), "BOM 이 먼저다")

        assertTrue(LivePage.servedAsXml("a.xhtml"))
        assertTrue(LivePage.servedAsXml("A.XHTML"))
        assertFalse(LivePage.servedAsXml("a.html"))
        assertFalse(LivePage.servedAsXml("a.htm"))
    }

    @Test
    fun `모든 인코딩에 브라우저가 아는 이름이 있다`() {
        for (e in TextEncoding.entries) {
            val label = LivePage.labelOf(e)
            assertTrue(LivePage.isKnownLabel(label), "$e → $label")
            assertEquals(label, label.lowercase())
        }
        // CP949 는 WHATWG 표의 이름이 아니다 — 그 표의 EUC-KR 이 곧 windows-949 다.
        assertEquals("euc-kr", LivePage.labelOf(TextEncoding.CP949))
    }

    @Test
    fun `쪽의 폴더 안의 경로`() {
        assertNull(LivePage.relativeOf(LivePage.PAGE_PATH))
        assertEquals("img/a.png", LivePage.relativeOf("f/img/a.png"))
        assertNull(LivePage.relativeOf("x.png"))
        assertNull(LivePage.relativeOf("ff/x.png"))
        assertNull(LivePage.relativeOf("r/0"))
    }

    @Test
    fun `스크립트를 실행하는 쪽이 싣는 형식`() {
        assertEquals("text/javascript", PreviewTypes.ofLive("app.JS"))
        assertEquals("text/javascript", PreviewTypes.ofLive("m.mjs"))
        assertEquals("text/css", PreviewTypes.ofLive("s.css"))
        assertEquals("application/json", PreviewTypes.ofLive("d.json"))
        assertEquals("text/html", PreviewTypes.ofLive("frame.htm"))
        assertEquals("image/png", PreviewTypes.ofLive("a.png"))
        assertEquals("font/woff2", PreviewTypes.ofLive("f.woff2"))
        // 소리·영상·문서와 이름 없는 것은 싣지 않는다.
        for (name in listOf("a.pdf", "a.mp4", "a.mp3", "a.zip", "a.exe", "README", "a.")) assertNull(PreviewTypes.ofLive(name), name)
    }

    @Test
    fun `스크립트를 버린 쪽에만 단추를 단다`() {
        assertTrue(HtmlPreview.render("<p>본문</p><script>x()</script>").scripted)
        assertTrue(HtmlPreview.render("<p onclick=\"x()\">본문</p>").scripted)
        assertTrue(HtmlPreview.render("<a href=\"javascript:x()\">링크</a>").scripted)
        // `srcdoc` 안의 스크립트는 위생기에게 '프레임' 으로만 세어진다 — 그 쪽에도 단추가 붙어야 한다.
        assertTrue(HtmlPreview.render("<iframe srcdoc=\"&lt;script&gt;x()&lt;/script&gt;\"></iframe><p>본문</p>").scripted)
        // 바깥 그림만 버린 쪽 — 버린 것은 있지만 스크립트를 켜도 달라지는 것이 없다.
        val remote = HtmlPreview.render("<img src=\"https://example.com/a.png\"><p>본문</p>")
        assertTrue(remote.withheld)
        assertFalse(remote.scripted)
        assertFalse(HtmlPreview.render("<p>본문</p>").scripted)
        // 돌지 않는 자료 덩이(`application/ld+json`)는 스크립트가 아니다.
        assertFalse(HtmlPreview.render("<script type=\"application/ld+json\">{}</script><p>본문</p>").scripted)
    }
}
