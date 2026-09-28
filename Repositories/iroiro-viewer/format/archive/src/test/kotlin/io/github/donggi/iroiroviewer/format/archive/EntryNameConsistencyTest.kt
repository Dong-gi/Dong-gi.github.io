package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **같은 바이트는 글 뷰어와 압축 목록에서 같은 인코딩으로 읽힌다.**
 *
 * CLAUDE.md 가 두 판정을 '함께 고쳐라' 로 묶어 두었던 까닭이 이것이다 — 한쪽만 고치면 문서만 보는 사람에게는
 * 일어날 수 없는 일(같은 파일이 두 화면에서 다르게 읽힌다)이 생긴다. 판정을 하나로 합쳤으니 그것이 계약이 된다.
 * ZIP 만의 두 길(UTF-8 표시, 유니코드 경로 추가필드)은 판정 앞에서 갈리므로 여기서 보지 않는다.
 */
class EntryNameConsistencyTest {

    private fun cs(name: String): Charset = Charset.forName(name)

    @Test
    fun `이름 판정과 글 판정이 같은 인코딩을 고른다`() {
        val cp949 = EntryNameDecoder.cp949 ?: return
        val samples = listOf(
            "한글이름.txt".toByteArray(cp949),
            "똠방각하.txt".toByteArray(cp949),
            "漢字한글.txt".toByteArray(cp949),
            "월간 경영 전략 분석 결과.txt".toByteArray(cp949),
            "짱짱.txt".toByteArray(cp949),
            "캡처.png".toByteArray(cp949),
            "DCIM · Pictures.jpg".toByteArray(cp949),
            "テスト資料.txt".toByteArray(cs("Shift_JIS")),
            "ﾃｽﾄ.txt".toByteArray(cs("Shift_JIS")),
            "中文文件.txt".toByteArray(cs("GB18030")),
            "한글이름.txt".toByteArray(Charsets.UTF_8),
            "café.txt".toByteArray(Charsets.UTF_8),
            "café.txt".toByteArray(Charsets.ISO_8859_1),
            "보고서".toByteArray(cp949) + byteArrayOf(0xFF.toByte()),
        )
        for (raw in samples) {
            val text = CharsetDetector.detect(raw)
            val name = EntryNameDecoder.decode(raw, utf8Flag = false, fallback = "")
            val textCharset = (text.encoding.charset ?: TextEncoding.UTF_8.charset!!).name()
            assertEquals(textCharset, name.charsetLabel.substringBefore('('), raw.joinToString(" ") { "%02X".format(it) })
            assertEquals(String(raw, text.encoding.charset!!), name.name)
        }
    }
}
