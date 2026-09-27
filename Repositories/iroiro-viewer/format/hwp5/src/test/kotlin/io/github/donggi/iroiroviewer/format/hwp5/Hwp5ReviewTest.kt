package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 13단계 구현 뒤의 적대적 검토가 잡은 것들의 회귀 시험 — 사설 영역 글자, 문서가 적는 표의 행 수, 글꼴 개수 칸, 제목의
 * 방향 바꾸기 문자, 주석 번호의 자리, 그리고 시험이 비어 있던 취소·평문 지우기.
 */
class Hwp5ReviewTest {

    /** 보조 평면 글자 하나를 UTF-16 두 칸으로 — [P.text] 는 `Char` 를 그대로 적는다. */
    private fun cp(codePoint: Int): String = String(Character.toChars(codePoint))

    @Test
    fun 한컴의_사설_영역_글자는_바로보기가_적는_글자로_옮긴다() {
        // 네모 안의 숫자(U+F02B1…)는 한컴 글꼴이 없으면 두부가 된다 — 공문서의 절 제목이 번호를 잃는다(표본 K04·K05·K11·K19).
        val overlap: (Int) -> ByteArray = { lc ->
            Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of("tcps")).wstr(cp(0xF02B6)).u8(0).u8(0).u32(0).toByteArray())
        }
        val file = HwpFile().apply {
            docInfo.paraShapes.add(PS(headType = 1, headLevel = 0)) // 1 개요
            section(
                P(1).text(cp(0xF02B1) + " 시도별 현황"),
                P().text("점검표 " + cp(0xF0854) + "행동 요령" + cp(0xF0855) + " 참고"),
                P().text("\u00AD 발열이 있는 경우"),
                P().ctrl(18, "tcps", overlap).text(" 산업별 일자리"),
                // 표에 없는 사설 영역 글자는 그대로 — 대리 쌍이 쪼개지지 않는다.
                P().text("모름 " + cp(0xF1234) + " 끝"),
            )
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("１ 시도별 현황</h1>" in body, body)
            assertTrue("점검표 『행동 요령』 참고" in body, body)
            assertTrue("<p>- 발열이 있는 경우</p>" in body, body)
            assertTrue("<p>６ 산업별 일자리</p>" in body, body)
            assertTrue("모름 ${cp(0xF1234)} 끝" in body, body)
            assertFalse(cp(0xF02B1) in body || cp(0xF02B6) in body, body)
            // 목차와 부분의 이름도 같은 글에서 나온다.
            assertEquals("１ 시도별 현황", doc.outline.single().title)
            assertEquals("１ 시도별 현황", doc.parts.single().label)
            // 옮긴 것뿐이면 버린 글자가 없다 — 글자 효과로 세지 않는다.
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.TEXT_EFFECT])
        }
    }

    @Test
    fun 글자_겹침에서_옮기지_못한_사설_영역_글자는_버리고_센다() {
        // 13단계 짝 대조(발견 14): HWPX 는 겹칠 글자 가운데 옮기지 못한 사설 영역 글자(한글 전용 글꼴의 기호)를 버리고 글자
        // 효과로 셌는데, HWP 는 그대로 적어 두부가 보였다. 이제 둘이 `HancomChars.compose` 하나를 쓴다.
        fun overlap(s: String): (Int) -> ByteArray = { lc ->
            Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of("tcps")).wstr(s).u8(0).u8(0).u32(0).toByteArray())
        }
        val file = HwpFile().section(
            P().ctrl(18, "tcps", overlap("" + cp(0xF02B3))).text(" 첫 절"),
            P().ctrl(18, "tcps", overlap(cp(0xF1234))).text(" 둘째 절"),
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<p>３ 첫 절</p>" in body, body)
            assertTrue("<p> 둘째 절</p>" in body, body)
            assertFalse("" in body || cp(0xF1234) in body, body)
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.TEXT_EFFECT])
        }
    }

    @Test
    fun 쓰지_않는_걷기는_표가_적은_빈_행을_돌지_않는다() {
        // 칸 없는 표 2,000개가 저마다 5,000행이라 적는다. 훑기가 행마다 돌면 틱이 1,000만 번이다(취소 확인 15만 번).
        val p = P()
        repeat(2_000) { p.ctrl(11, "tbl ", Ctrl.table(5_000, 800, emptyList(), rowCounts = IntArray(0))) }
        p.last = true
        val bytes = p.bytes(0)
        var checks = 0
        val env = Hwp5Env(DocInfo.EMPTY, UnsupportedFeatures(), { false }, { null }, { _, _ -> true })
        Hwp5Walker(env, sink = null, state = WalkState(), cfg = WalkConfig(checkCancel = { checks++ })).section(bytes, 0)
        assertTrue(checks < 1_000, "취소 확인 $checks 번")
        // 쓰는 걷기는 그대로 행마다 쓴다 — 쓴 양이 `HtmlWriter` 의 상한에 묶인다.
        val one = P().ctrl(11, "tbl ", Ctrl.table(40, 2, listOf(Ctrl.Cell(0, 0, listOf(P().text("칸")))), rowCounts = IntArray(0)))
        Hwp5.open(HwpFile().section(one)).use { doc -> assertEquals(40, doc.body().occurrences("<tr>")) }
    }

    @Test
    fun 겹침_격자는_칸이_덮는_넓이만큼만_걷기마다_묶어서_잡는다() {
        val env = Hwp5Env(DocInfo.EMPTY, UnsupportedFeatures(), { false }, { null }, { _, _ -> true })
        fun walk(p: P): Hwp5Walker {
            p.last = true
            return Hwp5Walker(env, sink = null, state = WalkState(), cfg = WalkConfig()).also { it.section(p.bytes(0), 0) }
        }
        // 작은 칸 둘을 가진 표 1,000개 — 5,000×800 이라 적었지만 격자는 칸이 덮는 1×2 만.
        val small = P()
        repeat(1_000) { small.ctrl(11, "tbl ", Ctrl.table(5_000, 800, listOf(Ctrl.Cell(0, 0, emptyList()), Ctrl.Cell(0, 1, emptyList())), rowCounts = IntArray(0))) }
        assertEquals(2_000L, walk(small).gridSlots)
        // 칸 둘이 4,000×1,000 을 덮는 표 100개(표 하나가 격자 상한에 딱 닿는다) — 걷기 하나가 잡는 합은 상한에서 멈춘다.
        val big = P()
        repeat(100) {
            big.ctrl(11, "tbl ", Ctrl.table(5_000, 1_000, listOf(Ctrl.Cell(0, 0, emptyList(), rowSpan = 3_999, colSpan = 1_000), Ctrl.Cell(3_999, 0, emptyList())), rowCounts = IntArray(0)))
        }
        val slots = walk(big).gridSlots
        assertTrue(slots in 1..Hwp5Limits.MAX_GRID_SLOTS_PER_WALK, "$slots")
        // 칸이 하나뿐이면 겹칠 것이 없다.
        assertEquals(0L, walk(P().ctrl(11, "tbl ", Ctrl.table(5_000, 800, listOf(Ctrl.Cell(0, 0, emptyList()))))).gridSlots)
    }

    @Test
    fun 글꼴_개수_칸이_음수여도_DocInfo_를_잃지_않는다() {
        // 음수 칸으로 합을 맞춰 한글 칸이 글꼴 레코드 수를 넘게 하면 `subList` 가 던져 서식·그림 목록 전체를 잃었다.
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(40)
        val file = HwpFile().apply {
            docInfo.bins.add(Bin(1, "png", png))
            docInfo.charShapes.add(CS(bold = true)) // 1
            section(P().text("굵게", 1).ctrl(11, "gso ", Ctrl.picture(1)))
        }
        val di = file.docInfo.bytes()
        val cur = RecordCursor(di, 0)
        while (cur.peek()) {
            if (cur.tag == HwpTag.ID_MAPPINGS) {
                // 글꼴 레코드는 2×7 = 14개. 한글 칸 20, 영어 칸 -16, 나머지 2씩 — 합이 14.
                fun put(i: Int, v: Int) = repeat(4) { b -> di[cur.payload + 4 * i + b] = (v ushr (8 * b)).toByte() }
                put(1, 20)
                put(2, -16)
                break
            }
            cur.advance()
        }
        file.docInfoRaw = di
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<img src=\"BinData/BIN0001.png\"" in body, body)
            assertTrue("font-weight:bold" in body, body)
            assertTrue(doc.warnings.none { it.code == FlowWarnings.AUX_FAILED }, "${doc.warnings}")
        }
    }

    @Test
    fun 제목의_제어_문자와_방향_바꾸기_문자는_목차와_부분_이름에서_뺀다() {
        // 부분의 이름은 알림 문장(경고의 detail)에 들어간다 — U+202E 하나가 문장을 거꾸로 보이게 한다.
        val file = HwpFile().apply {
            docInfo.paraShapes.add(PS(headType = 1, headLevel = 0))
            section(P(1).text("\u202E제목\u2066 둘\u0085셋\u200F\u061C"), P().text("본문"))
        }
        Hwp5.open(file).use { doc ->
            // 방향 표시(U+200E·200F·061C)도 버린다 — HWPX 와 한 규칙(`HancomTitles`)이 되며 늘었다(13단계 짝 대조).
            assertEquals("제목 둘 셋", doc.outline.single().title)
            assertEquals("제목 둘 셋", doc.parts.single().label)
        }
    }

    @Test
    fun 주석_번호는_주석의_자동_번호_자리에_적는다() {
        // 한글은 주석 번호를 자동 번호 컨트롤의 자리에 그린다. 첫 글보다 뒤에 있으면(`주 1) …`) 거기에.
        val file = HwpFile().section(
            P().text("본문").ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("주 ").ctrl(18, "atno", Ctrl.autoNumber(1, 1)).text(" 기준 설명")))),
        )
        Hwp5.open(file).use { doc ->
            val notes = doc.body().substringAfter("<section class=\"notes\">")
            assertTrue("<div id=\"fn-1\" class=\"note\"><p>주 <sup class=\"fnnum\"><a href=\"#fnref-1\">1)</a></sup> 기준 설명</p></div>" in notes, notes)
            assertEquals(1, notes.occurrences(">1)<"), notes)
        }
    }

    @Test
    fun 여는_중에_취소되면_끝까지_달리지_않는다() {
        // 구역 사이(진행률을 알린 뒤)에 잡을 취소한다 — 다음 구역 앞에서 멈춰야 한다.
        val file = HwpFile().apply {
            section(P().text("첫 구역"))
            section(P().text("둘째 구역"))
            docInfo.sectionCount = 2
        }
        val bytes = file.build()
        var outcome: OpenOutcome? = null
        var handed = false
        runBlocking {
            val job = launch {
                val self = coroutineContext[Job]!!
                outcome = Hwp5Opener(null).open(ByteArrayDocumentSource(bytes, "t.hwp"), { _, _ -> self.cancel() }) { handed = true }
            }
            job.join()
            assertTrue(job.isCancelled)
        }
        assertNull(outcome)
        assertFalse(handed)
        // 구역 안에서도 확인한다 — 문단 1,000개짜리 구역 하나에서 여러 번.
        val paras = (1..1_000).map { P().text("문단 $it") }
        val big = Rec.join(paras.also { it.last().last = true }.map { it.bytes(0) })
        var checks = 0
        val env = Hwp5Env(DocInfo.EMPTY, UnsupportedFeatures(), { false }, { null }, { _, _ -> true })
        Hwp5Walker(env, sink = null, state = WalkState(), cfg = WalkConfig(checkCancel = { checks++ })).section(big, 0)
        assertTrue(checks >= 10, "취소 확인 $checks 번")
    }

    @Test
    fun 배포용_문서의_평문은_구역을_바꿀_때와_닫을_때_0_으로_덮는다() {
        val file = HwpFile().apply {
            flags = 1L or 4L
            section(P().text("첫 구역의 잠긴 본문"))
            section(P().text("둘째 구역의 잠긴 본문"))
            docInfo.sectionCount = 2
        }
        val cfb = CfbFile.open(ByteArrayDocumentSource(file.build(), "t.hwp"))
        val header = Hwp5FileHeader.parse(cfb.readStream(cfb.find("FileHeader")!!, 4096))
        val pkg = Hwp5Package(cfb, header)
        val entries = pkg.sectionEntries(2)
        val first = pkg.section(0, entries[0]) {}
        assertTrue(first.any { it != 0.toByte() })
        val second = pkg.section(1, entries[1]) {}
        assertTrue(first.all { it == 0.toByte() }, "구역을 바꿨는데 앞 구역의 평문이 남았다")
        assertTrue(second.any { it != 0.toByte() })
        pkg.close()
        assertTrue(second.all { it == 0.toByte() }, "닫았는데 평문이 남았다")
    }

    @Test
    fun 마지막_문단_뒤의_수준_0_레코드는_본문이_아니다() {
        // 구역의 본문은 비트 31 이 켜진 최상위 문단에서 끝난다. 그 뒤의 레코드(확장 바탕쪽·메모 목록 — hwplib `ForSection`)를
        // 본문으로 읽으면 바탕쪽의 글이 본문 끝에 붙는다. 실물 표본에는 그런 구역이 없어 합성으로만 확인한다.
        val section = Rec.join(listOf(P().text("본문 끝").also { it.last = true }.bytes(0), P().text("바탕쪽 글").bytes(0)))
        val file = HwpFile().apply {
            section(P().text("x"))
            sections[0] = section
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("본문 끝" in body, body)
            assertFalse("바탕쪽 글" in body, body)
        }
    }
}
