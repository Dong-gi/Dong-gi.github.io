package io.github.donggi.iroiroviewer.charset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 인코딩 판정.
 *
 * **인코딩을 틀리면 파일 전체가 쓸모없어진다.** 이미지가 흐린 것과는 무게가 다르다 —
 * 사용자는 자기 문서를 읽지 못하고, 더 나쁜 것은 **그럴듯한 다른 글자**가 나와서
 * 무엇이 잘못됐는지도 모르는 경우다.
 *
 * 표본은 **고정 바이트열**로 만든다. 우리가 고른 charset 으로 써서 같은 것으로 읽는
 * 왕복은 아무것도 증명하지 못한다 — 2단계 적대적 검토에서 배운 교훈이다.
 */
class CharsetDetectorTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun encoded(s: String, enc: TextEncoding) = s.toByteArray(enc.charset!!)

    // ---- BOM --------------------------------------------------------------

    @Test
    fun `BOM 이 있으면 그것으로 끝난다`() {
        val utf8 = bytes(0xEF, 0xBB, 0xBF) + "hello".toByteArray()
        val d = CharsetDetector.detect(utf8)
        assertEquals(TextEncoding.UTF_8, d.encoding)
        assertEquals(CharsetDetector.Detection.Confidence.CERTAIN, d.confidence)
        assertIs<CharsetDetector.Evidence.ByBom>(d.evidence)
    }

    /**
     * `FF FE 00 00` 은 UTF-32LE 다. UTF-16LE BOM(`FF FE`)으로 시작하므로 순서가 뒤집히면
     * **UTF-16 으로 먹히고 널이 가득한 화면**이 나온다.
     */
    @Test
    fun `UTF-32 를 UTF-16 으로 먹지 않고 지원하지 않는다고 말한다`() {
        val utf32le = bytes(0xFF, 0xFE, 0x00, 0x00, 0x68, 0x00, 0x00, 0x00)
        val d = CharsetDetector.detect(utf32le)
        val e = d.evidence
        assertIs<CharsetDetector.Evidence.Unsupported>(e)
        assertEquals(Bom.UTF32LE, e.bom)
    }

    @Test
    fun `UTF-16 BOM 은 방향까지 확정이다`() {
        val le = bytes(0xFF, 0xFE) + encoded("안녕", TextEncoding.UTF_16LE)
        assertEquals(TextEncoding.UTF_16LE, CharsetDetector.detect(le).encoding)
        val be = bytes(0xFE, 0xFF) + encoded("안녕", TextEncoding.UTF_16BE)
        assertEquals(TextEncoding.UTF_16BE, CharsetDetector.detect(be).encoding)
    }

    // ---- ASCII ------------------------------------------------------------

    @Test
    fun `비ASCII 가 없으면 ASCII 로 확정한다`() {
        val d = CharsetDetector.detect("fun main() { println(\"hi\") }\n".toByteArray())
        assertEquals(TextEncoding.UTF_8, d.encoding)
        assertIs<CharsetDetector.Evidence.AsciiOnly>(d.evidence)
    }

    // ---- 본론 -------------------------------------------------------------

    @Test
    fun `UTF-8 한국어를 UTF-8 로 읽는다`() {
        val d = CharsetDetector.detect(encoded(KOREAN, TextEncoding.UTF_8))
        assertEquals(TextEncoding.UTF_8, d.encoding)
    }

    @Test
    fun `CP949 한국어를 CP949 로 읽는다`() {
        val d = CharsetDetector.detect(encoded(KOREAN, TextEncoding.CP949))
        assertEquals(TextEncoding.CP949, d.encoding, "근거: ${d.evidence}")
    }

    @Test
    fun `Shift_JIS 일본어를 Shift_JIS 로 읽는다`() {
        val d = CharsetDetector.detect(encoded(JAPANESE, TextEncoding.SHIFT_JIS))
        assertEquals(TextEncoding.SHIFT_JIS, d.encoding, "근거: ${d.evidence}")
    }

    @Test
    fun `UTF-8 일본어를 UTF-8 로 읽는다`() {
        assertEquals(TextEncoding.UTF_8, CharsetDetector.detect(encoded(JAPANESE, TextEncoding.UTF_8)).encoding)
    }

    /**
     * **CP949 바이트가 우연히 유효한 UTF-8 인 경우.**
     *
     * `징` 은 CP949 로 `C2 A1` 인데 UTF-8 로도 유효해서 `¡` 로 읽힌다. 짧은 한국어 문서가
     * 그렇게 걸리면 라틴 기호 더미가 된다. UTF-8 이 엄격 디코드를 통과했더라도 **CJK 가
     * 하나도 없고 한글로 읽으면 또렷하면** 한글을 골라야 한다.
     */
    @Test
    fun `UTF-8 로도 읽히는 CP949 를 한글로 읽는다`() {
        // 전부 CP949 2바이트 한글이면서 UTF-8 로도 유효한 글자들.
        val trap = "징짖짙짚짜짝짠짢짤짧짬짭짯짰짱째짹짼쨀쨈"
        val raw = encoded(trap, TextEncoding.CP949)
        // 전제 확인 — 이 바이트열이 정말 UTF-8 로도 읽힌다.
        val asUtf8 = String(raw, TextEncoding.UTF_8.charset!!)
        assertTrue(!asUtf8.contains('�'), "시험 전제가 깨졌다: UTF-8 로 안 읽힌다")

        val d = CharsetDetector.detect(raw)
        assertEquals(TextEncoding.CP949, d.encoding, "근거: ${d.evidence}")
        assertIs<CharsetDetector.Evidence.KoreanOverUtf8>(d.evidence)
    }

    @Test
    fun `중국어 간체를 한국어나 일본어로 읽지 않는다`() {
        val d = CharsetDetector.detect(encoded(CHINESE, TextEncoding.GB18030))
        assertTrue(
            d.encoding == TextEncoding.GB18030 || d.encoding == TextEncoding.BIG5,
            "중국어가 ${d.encoding.label} 로 갔다. 근거: ${d.evidence}",
        )
    }

    /**
     * 창 끝에서 잘린 문자 때문에 **정답이 탈락하면 안 된다.**
     *
     * 판정 창을 자르는 자리는 대개 문자 한가운데다. 그것을 '디코드 실패' 로 세면 정답
     * 인코딩이 떨어지고 엉뚱한 것이 이긴다 — 가장 억울한 오판이다.
     */
    @Test
    fun `창 끝에서 잘려도 정답이 탈락하지 않는다`() {
        val full = encoded(KOREAN.repeat(60), TextEncoding.CP949)
        // 한글 한 글자의 한가운데에서 자른다.
        for (cut in listOf(full.size - 1, full.size - 3, full.size - 5)) {
            val d = CharsetDetector.detect(full, cut)
            assertEquals(TextEncoding.CP949, d.encoding, "$cut 바이트에서 잘랐을 때. 근거: ${d.evidence}")
        }
    }

    @Test
    fun `앞이 전부 ASCII 인 파일도 뒤의 한글을 본다`() {
        val head = "// ".repeat(300) + "\n"
        val raw = head.toByteArray() + encoded(KOREAN, TextEncoding.CP949)
        val d = CharsetDetector.detect(raw)
        assertEquals(TextEncoding.CP949, d.encoding, "근거: ${d.evidence}")
    }

    @Test
    fun `판정 근거가 값으로 나온다`() {
        val d = CharsetDetector.detect(encoded(KOREAN, TextEncoding.CP949))
        // 화면이 문장을 만들 수 있게 **값**이어야 한다. 문자열이면 번역이 막힌다.
        assertTrue(
            d.evidence is CharsetDetector.Evidence.ByScript ||
                d.evidence is CharsetDetector.Evidence.OnlyCandidate ||
                d.evidence is CharsetDetector.Evidence.KoreanOverUtf8,
            "예상 못 한 근거: ${d.evidence}",
        )
    }

    // ---- ZIP 파일명 판정과 합치며 고친 것 ------------------------------------------------------
    //
    // 판정이 두 벌(여기와 `EntryNameDecoder`)이던 것을 하나로 합쳤다. 이름은 짧아서, 긴 글에 맞춘 규칙이 짧은
    // 입력에서 틀리던 자리가 드러났다 — 아래는 **예전 글 판정이 틀리던** 것들이다(괄호 안이 예전 답).

    /** (Shift_JIS) — `한글` 의 CP949 두 바이트는 Shift_JIS 로 반각 가나 둘이다. 반각 가나를 일본어의 표지로 세지 않는다. */
    @Test
    fun `짧은 CP949 글을 반각 가나로 읽지 않는다`() {
        val d = CharsetDetector.detect(encoded("한글이름", TextEncoding.CP949))
        assertEquals(TextEncoding.CP949, d.encoding, "근거: ${d.evidence}")
    }

    /** (GB18030) — 받침 있는 글자가 대부분인 제목. 16자가 안 되면 받침 검사로 가짜라 하지 않는다. */
    @Test
    fun `받침이 많은 짧은 한국어를 중국어로 읽지 않는다`() {
        val d = CharsetDetector.detect(encoded("월간 경영 전략 분석 결과", TextEncoding.CP949))
        assertEquals(TextEncoding.CP949, d.encoding, "근거: ${d.evidence}")
    }

    /** (CP949) — `é`(C3 A9)는 CP949 로 `챕` 이다. 악센트 글자만 든 UTF-8 글이 통째로 한글이 됐다. */
    @Test
    fun `악센트가 붙은 라틴 글을 한글로 읽지 않는다`() {
        val french = "Le café était très réputé à Genève. Voilà où ça s'arrête: déjà vu, naïve.\n"
        val d = CharsetDetector.detect(encoded(french, TextEncoding.UTF_8))
        assertEquals(TextEncoding.UTF_8, d.encoding, "근거: ${d.evidence}")
    }

    /** (ISO-8859-1) — 깨진 바이트 하나가 64 KiB 창의 엄격 디코드를 전부 떨어뜨려 문서 전체가 `ÇÑ±Û` 로 나왔다. */
    @Test
    fun `깨진 바이트가 하나 섞인 CP949 글은 CP949 로 읽되 확실하지 않다고 한다`() {
        val raw = encoded(KOREAN.repeat(4), TextEncoding.CP949)
        raw[raw.size / 2] = 0xFF.toByte()
        val d = CharsetDetector.detect(raw)
        assertEquals(TextEncoding.CP949, d.encoding, "근거: ${d.evidence}")
        assertEquals(CharsetDetector.Detection.Confidence.LOW, d.confidence)
    }

    /**
     * 위 `악센트가 붙은 라틴 글` 의 규칙이 **흔한 한국어 낱말을 라틴 글자로 만들지 않는다.** 이 낱말들의 CP949 바이트는
     * 그대로 유효한 UTF-8 이다 — `캡처` = `ĸó`, `체크` = `üũ`, `치킨` = `ġŲ`, `호환` = `ȣȯ`. 비ASCII 글자끼리 붙은
     * 것까지 낱말로 셌더니 넷 다 UTF-8 로 넘어갔다(검토가 잡았다. 옛 이름 판정은 넷 다 한글로 읽었다).
     */
    @Test
    fun `UTF-8 로도 읽히는 한국어 낱말을 라틴 글자로 읽지 않는다`() {
        for (word in listOf("캡처", "체크", "치킨", "호환", "청크", "캡처 (1).png", "체크_2024.txt")) {
            val raw = encoded(word, TextEncoding.CP949)
            assertTrue(!String(raw, TextEncoding.UTF_8.charset!!).contains('�'), "시험 전제가 깨졌다: $word")
            val d = CharsetDetector.detect(raw)
            assertEquals(TextEncoding.CP949, d.encoding, "$word — 근거: ${d.evidence}")
        }
        // 라틴 낱말은 그대로 UTF-8 이다(ASCII 글자 바로 옆의 서유럽 글자).
        for (word in listOf("café", "Größe", "naïve", "São Paulo", "Zürich")) {
            assertEquals(TextEncoding.UTF_8, CharsetDetector.detect(encoded(word, TextEncoding.UTF_8)).encoding, word)
        }
    }

    /**
     * **기호만 든 CP949 글.** `·`(A1 A4)는 Shift_JIS 로 반각 문장 부호 `｡､` 다. 그것을 일본어의 본토 글자로 세어
     * `DCIM · Pictures` 같은 이름이 Shift_JIS 로 이겼다. 어느 후보도 글자를 내놓지 않으면 목록의 차례(CP949 가 앞)다.
     */
    @Test
    fun `기호만 든 CP949 글을 반각 문장 부호로 읽지 않는다`() {
        val d = CharsetDetector.detect(encoded("DCIM · Pictures · 2024", TextEncoding.CP949))
        assertEquals(TextEncoding.CP949, d.encoding, "근거: ${d.evidence}")
        assertEquals(CharsetDetector.Detection.Confidence.LOW, d.confidence)
    }

    /** 합치며 바뀌지 않아야 하는 것 — 짧은 일본어, Latin-1 로 적은 서유럽 글. */
    @Test
    fun `짧은 일본어와 Latin-1 글은 그대로다`() {
        assertEquals(TextEncoding.SHIFT_JIS, CharsetDetector.detect(encoded("テスト資料", TextEncoding.SHIFT_JIS)).encoding)
        assertEquals(TextEncoding.LATIN1, CharsetDetector.detect(encoded("café crème", TextEncoding.LATIN1)).encoding)
    }

    private companion object {
        const val KOREAN = "한글 텍스트 파일입니다. 인코딩을 자동으로 판정해야 합니다. " +
            "둘째 줄과 셋째 줄이 있고 줄바꿈은 LF 입니다.\n"
        const val JAPANESE = "日本語のテキストファイルです。エンコーディングを自動で判定する" +
            "必要があります。ひらがな・カタカナ・漢字が混ざっています。\n"
        const val CHINESE = "这是一个中文文本文件。需要自动判定编码。" +
            "简体中文的常用字分布与日文韩文不同。\n"
    }
}
