package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.hwp5.Hwp5Opener
import io.github.donggi.iroiroviewer.format.hwpx.HwpxOpener
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * 같은 보도자료의 HWP·HWPX 짝(`samples-local/hwp`, 커밋하지 않는다 — 없으면 건너뛴다)이 **같은 부분으로 나뉘고, 부분마다 같은
 * 이름과 같은 글로 시작하는가.**
 *
 * 두 한글 변환기는 서로를 볼 수 없어(포맷 모듈끼리 참조 금지) 둘을 함께 여는 시험은 조립하는 `app` 에만 둘 수 있다. 처음에는
 * 부분을 나누는 규칙이 달라(HWP 는 표의 칸 하나를 블록 하나로, HWPX 는 글자 60자로 셌다) K01 이 6부분, 같은 보도자료의 K25 가
 * 4부분이었다 — 부분 번호가 든 알림과 이어보기가 포맷에 따라 달랐다(13단계 짝 대조). 규칙은 이제 `format:html` 의
 * `HancomChunkMeter` 하나다.
 */
class HancomPairTest {

    private val dir: File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "samples-local/hwp") }
        .firstOrNull { it.isDirectory }

    @Test
    fun 같은_보도자료는_두_포맷에서_같은_부분으로_나뉜다() {
        assumeTrue("samples-local/hwp 가 없다", dir != null)
        var seen = 0
        for ((hwp, hwpx) in PAIRS) {
            val a = File(dir, "$hwp.hwp")
            val b = File(dir, "$hwpx.hwpx")
            if (!a.isFile || !b.isFile) continue
            seen++
            open(Hwp5Opener(null), a).use { da ->
                open(HwpxOpener(null), b).use { db ->
                    val pair = "$hwp/$hwpx"
                    assertEquals(da.parts.size, db.parts.size, "$pair 의 부분 수")
                    for (i in da.parts.indices) {
                        assertEquals(da.parts[i].label, db.parts[i].label, "$pair 부분 ${i + 1} 의 이름")
                        assertEquals(head(da.partHtml(i)), head(db.partHtml(i)), "$pair 부분 ${i + 1} 의 첫 글")
                    }
                }
            }
        }
        assumeTrue("짝 표본이 없다", seen > 0)
    }

    private fun open(opener: DocumentOpener, f: File): FlowDocument {
        val o = runBlocking { opener.open(FileDocumentSource(f)) }
        if (o !is OpenOutcome.Success) fail("${f.name} 가 열리지 않았다: $o")
        return o.document as FlowDocument
    }

    /** 부분의 본문에서 태그와 공백을 뺀 앞 [HEAD] 글자. 두 변환기의 태그 모양은 달라도 된다 — 글이 같아야 한다. */
    private fun head(html: String?): String {
        val body = html.orEmpty().substringAfter("<body").substringAfter('>')
        return TAG.replace(body, "").filterNot { it.isWhitespace() }.take(HEAD)
    }

    private companion object {
        val PAIRS = listOf("K01" to "K25", "K11" to "K33", "K19" to "K27")
        val TAG = Regex("<[^>]*>")
        const val HEAD = 40
    }
}
