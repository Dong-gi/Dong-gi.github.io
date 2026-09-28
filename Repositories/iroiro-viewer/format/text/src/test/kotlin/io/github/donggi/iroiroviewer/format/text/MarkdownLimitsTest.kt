package io.github.donggi.iroiroviewer.format.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **악의적인 마크다운.** 입력은 사용자가 고른 아무 파일이고 크기 상한(2 MiB)까지 온다.
 *
 * 되부름·제곱 시간·노드 폭발 셋을 막는다. 시간 시험의 크기는 **옛 판이(또는 순진한 구현이) 확실히 넘는
 * 크기로** 잡았다 — 저장소의 교훈이다(DOCTYPE 을 0.56 MB 로 쟀더니 JIT 이 옛 판을 빠르게 돌려 결함을 못 봤다).
 */
class MarkdownLimitsTest {

    private fun timed(label: String, limitMs: Long = 4_000, block: () -> Unit) {
        val t0 = System.nanoTime()
        try {
            block()
        } catch (e: StackOverflowError) {
            fail("$label: 스택이 넘쳤다")
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < limitMs, "$label: ${ms}ms")
    }

    private val size = 1_500_000

    @Test
    fun `깊은 인용`() = timed("인용") {
        val body = MarkdownPreview.body(">".repeat(100_000) + " 끝").html
        // 깊이 상한을 넘은 `>` 는 글자로 남는다.
        val depth = Regex("<blockquote>").findAll(body).count()
        assertEquals(MdBlockParser.MAX_DEPTH, depth)
        assertTrue(body.contains("&gt;"))
    }

    @Test
    fun `깊은 목록`() = timed("목록") {
        val md = (0 until 5_000).joinToString("\n") { "  ".repeat(it) + "- a$it" }
        val body = MarkdownPreview.body(md).html
        assertTrue(Regex("<ul>").findAll(body).count() <= MdBlockParser.MAX_DEPTH / 2 + 1)
    }

    @Test
    fun `깊은 강조`() = timed("강조") {
        val md = "*".repeat(size / 2) + "a" + "*".repeat(size / 2)
        val body = MarkdownPreview.body(md).html
        assertTrue(Regex("<(em|strong)>").findAll(body).count() <= MdInlineParser.MAX_DEPTH)
    }

    @Test
    fun `번갈아 여는 강조`() = timed("번갈이") {
        MarkdownPreview.body("*a _b ".repeat(size / 6))
        MarkdownPreview.body("**a".repeat(size / 3))
        MarkdownPreview.body("_a_b".repeat(size / 4))
    }

    @Test
    fun `깊은 그림 괄호`() = timed("그림") {
        val md = "![".repeat(size / 8) + "a" + "](b)".repeat(size / 8)
        MarkdownPreview.body(md)
    }

    /**
     * 짝 없는 구분자가 쌓인 뒤에 링크·그림이 이어진다. 링크를 닫을 때마다 그 안의 강조를 푸는데, 참조 구현처럼 바닥을
     * 지나쳐 쌓기 전체를 훑으면 문단마다 제곱이다(고침을 되돌려 이 시험을 돌리면 75초였다).
     */
    @Test
    fun `구분자 뒤의 링크가 쌓기를 다시 훑지 않는다`() = timed("구분자와 링크") {
        val images = MarkdownPreview.body(List(5) { "~![x](y)".repeat(40_000) }.joinToString("\n\n"))
        assertTrue(images.html.contains("<img"), images.html.take(200))
        val refs = MarkdownPreview.body("[a]: #b\n\n" + List(5) { "*[a]".repeat(80_000) }.joinToString("\n\n"))
        assertTrue(refs.html.contains("<a href=\"#b\">"), refs.html.take(200))
    }

    @Test
    fun `닫히지 않는 링크 목적지`() = timed("목적지") {
        MarkdownPreview.body("[a](".repeat(size / 4))
        MarkdownPreview.body("[a](<".repeat(size / 5))
        MarkdownPreview.body("[a](b \"".repeat(size / 7))
        MarkdownPreview.body("[a]".repeat(size / 3))
        MarkdownPreview.body("[".repeat(size))
        MarkdownPreview.body("]".repeat(size))
        MarkdownPreview.body("[a".repeat(size / 2) + "](#x)".repeat(1000))
    }

    @Test
    fun `짝 없는 백틱`() = timed("백틱") {
        MarkdownPreview.body("` ".repeat(size / 2))
        MarkdownPreview.body((1..3000).joinToString(" ") { "`".repeat(it % 50 + 1) + "x" })
    }

    @Test
    fun `자동 링크와 엔티티`() = timed("꺾쇠") {
        MarkdownPreview.body("<a".repeat(size / 2))
        MarkdownPreview.body("<http://".repeat(size / 8))
        MarkdownPreview.body("&#".repeat(size / 2))
        MarkdownPreview.body("&amp".repeat(size / 4))
    }

    @Test
    fun `아주 긴 한 줄`() = timed("긴 줄") {
        val r = MarkdownPreview.body("가".repeat(size))
        assertTrue(r.html.startsWith("<p>"))
    }

    @Test
    fun `많은 블록은 상한에서 멈추고 줄였다고 말한다`() = timed("블록") {
        val r = MarkdownPreview.body("- a\n".repeat(300_000))
        assertTrue(r.truncated)
    }

    @Test
    fun `많은 표 칸`() = timed("표") {
        val header = "|" + "a|".repeat(10_000)
        val r = MarkdownPreview.body(header + "\n" + "|" + "-|".repeat(10_000) + "\n" + "|x".repeat(100_000))
        // 칸이 너무 많은 머리는 표가 아니다.
        assertTrue("<table>" !in r.html)
        val ok = MarkdownPreview.body("|a|b|\n|-|-|\n" + "|1|2|3|4|5|\n".repeat(50_000))
        assertTrue("<table>" in ok.html)
    }

    @Test
    fun `많은 참조 정의`() = timed("참조") {
        val md = (0 until 60_000).joinToString("\n") { "[r$it]: #x$it" } + "\n\n[r5] [r59999]"
        val body = MarkdownPreview.body(md).html
        assertTrue("<a href=\"#x5\">r5</a>" in body, body.takeLast(200))
    }

    @Test
    fun `결과 상한에 닿으면 줄였다고 말한다`() {
        val r = MarkdownPreview.body("`a` ".repeat(10_000), maxChars = 20_000)
        assertTrue(r.truncated)
        assertTrue(r.html.length < 21_000)
    }

    @Test
    fun `취소는 체크포인트에서 던진다`() {
        var calls = 0
        try {
            MarkdownPreview.render("줄\n".repeat(10_000)) {
                calls++
                if (calls == 3) throw kotlinx.coroutines.CancellationException("취소")
            }
            fail("던지지 않았다")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals(3, calls)
        }
    }
}
