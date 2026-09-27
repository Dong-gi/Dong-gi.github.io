package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.HancomChunkPolicy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 표·그림·글상자·조각 나누기·깨진 입력. */
class Hwp5StructureTest {

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) + ByteArray(200) { it.toByte() }

    @Test
    fun 표의_병합과_칸의_차례() {
        // 3×3, 첫 칸이 세 행을 덮고(가려진 칸은 레코드에 없다) 가운데 행의 칸이 두 열을 덮는다.
        // 칸을 일부러 행 차례가 아니게 적는다 — 그려지는 것은 행 차례다.
        // 세로 정렬: 가운데(1)는 적지 않는다 — 바탕 스타일의 칸이 가운데다(한글의 기본값, `HancomCss.FLOW`). 위(0)·아래(2)만 적는다.
        val cells = listOf(
            Ctrl.Cell(1, 1, listOf(P().text("가운데 넓은 칸")), colSpan = 2, vAlign = 1),
            Ctrl.Cell(0, 0, listOf(P().text("세로 칸")), rowSpan = 3, vAlign = 2),
            Ctrl.Cell(0, 1, listOf(P().text("B1")), vAlign = 1),
            Ctrl.Cell(0, 2, listOf(P().text("C1")), vAlign = 1),
            Ctrl.Cell(2, 1, listOf(P().text("B3"), P().text("B3 둘째")), vAlign = 1),
            Ctrl.Cell(2, 2, listOf(P().text("C3")), borderFill = 1, vAlign = 0),
        )
        val file = HwpFile().apply {
            docInfo.borderFills.add(0xFFE0A0)
            section(P().text("표 앞").ctrl(11, "tbl ", Ctrl.table(3, 3, cells)).text("표 뒤"))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            val table = body.substringAfter("<table>").substringBefore("</table>")
            val rows = table.split("<tr>").drop(1).map { plain(it) }
            assertEquals(listOf("세로 칸B1C1", "가운데 넓은 칸", "B3B3 둘째C3"), rows)
            assertTrue("<td rowspan=\"3\" style=\"vertical-align:bottom\"><p>세로 칸</p></td>" in table, table)
            assertTrue("<td colspan=\"2\"><p>가운데 넓은 칸</p></td>" in table, table)
            assertTrue("<td style=\"background-color:#ffe0a0;vertical-align:top\"><p>C3</p></td>" in table, table)
            assertFalse("middle" in table, table)
            // 표는 문단 안의 컨트롤이지만 p 안에 둘 수 없다 — 문단을 끊고 잇는다.
            assertTrue("<p>표 앞</p><table>" in body && "</table><p>표 뒤</p>" in body, body)
        }
    }

    @Test
    fun 겹치거나_격자_밖인_칸은_그리지_않되_잃지_않는다() {
        val cells = listOf(
            Ctrl.Cell(0, 0, listOf(P().text("넓은 칸")), colSpan = 2),
            Ctrl.Cell(0, 1, listOf(P().text("겹친 칸"))),
            Ctrl.Cell(5, 0, listOf(P().text("밖의 칸"))),
            Ctrl.Cell(1, 0, listOf(P().text("정상"))),
        )
        val file = HwpFile().section(P().ctrl(11, "tbl ", Ctrl.table(2, 2, cells, rowCounts = intArrayOf(2, 1))), P().text("다음"))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("넓은 칸" in body && "정상" in body && "다음" in body, body)
            assertFalse("겹친 칸" in body || "밖의 칸" in body, body)
            assertTrue(doc.warnings.none { it.code == FlowWarnings.PART_FAILED })
        }
    }

    @Test
    fun 표의_캡션과_표_안의_표() {
        val inner = Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().text("안쪽")))))
        val cells = listOf(Ctrl.Cell(0, 0, listOf(P().text("바깥 칸").ctrl(11, "tbl ", inner))))
        val caption = listOf(P().text("표 ").ctrl(18, "atno", Ctrl.autoNumber(kind = 4, number = 3)).text(" 요약"))
        val file = HwpFile().section(P().ctrl(11, "tbl ", Ctrl.table(1, 1, cells, caption = caption)))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<p>바깥 칸</p><table><tr><td style=\"vertical-align:top\"><p>안쪽</p></td></tr></table>" in body, body)
            // 아래 캡션은 표 뒤에.
            assertTrue(Regex("</table></td></tr></table><div class=\"cap\"><p>표 3 요약</p></div>").containsMatchIn(body), body)
        }
    }

    @Test
    fun 수식의_캡션도_자리를_따른다() {
        // 13단계 짝 대조: 표·그리기 개체는 위·왼쪽 캡션을 앞에 두는데 수식만 언제나 뒤에 두었다.
        val top = listOf(P().text("식 1"))
        val bottom = listOf(P().text("식 2"))
        val file = HwpFile().section(
            P().text("앞 글 ").ctrl(11, "eqed", Ctrl.equation("x^2", caption = top, captionDir = 2)).text(" 뒤 글"),
            P().ctrl(11, "eqed", Ctrl.equation("y over 2", caption = bottom, captionDir = 3)),
            P().ctrl(11, "eqed", Ctrl.equation("z", caption = listOf(P().text("식 3")), captionDir = 0)), // 왼쪽도 앞
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<p>앞 글 </p><div class=\"cap\"><p>식 1</p></div><p><span class=\"math\">x^2</span> 뒤 글</p>" in body, body)
            assertTrue("<p><span class=\"math\">y over 2</span></p><div class=\"cap\"><p>식 2</p></div>" in body, body)
            assertTrue("<div class=\"cap\"><p>식 3</p></div><p><span class=\"math\">z</span></p>" in body, body)
        }
    }

    @Test
    fun 너무_깊은_표는_자르고_알린다() {
        var nested: (Int) -> ByteArray = Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().text("바닥")))))
        repeat(Hwp5Limits.MAX_TABLE_DEPTH + 2) {
            val inner = nested
            nested = Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().text("층").ctrl(11, "tbl ", inner)))))
        }
        val file = HwpFile().section(P().ctrl(11, "tbl ", nested), P().text("표 다음 글"))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertEquals(Hwp5Limits.MAX_TABLE_DEPTH, body.occurrences("<table>"))
            assertFalse("바닥" in body)
            assertTrue("표 다음 글" in body)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED }, "${doc.warnings}")
        }
    }

    @Test
    fun 행_수를_부풀린_표는_상한에서_멈춘다() {
        val cells = listOf(Ctrl.Cell(0, 0, listOf(P().text("하나"))), Ctrl.Cell(59_999, 0, listOf(P().text("끝 칸"))))
        val counts = IntArray(60_000).also { it[0] = 1; it[59_999] = 1 }
        val file = HwpFile().section(P().ctrl(11, "tbl ", Ctrl.table(60_000, 1, cells, rowCounts = counts)))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertEquals(Hwp5Limits.MAX_ROWS, body.occurrences("<tr>"))
            assertFalse("끝 칸" in body)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 한글이_저절로_넣은_그림_설명은_대체_글로_쓰지_않는다() {
        // K19 의 그림 25개 중 20개가 이 설명문이었다 — 원본 파일 이름·크기가 화면 낭독기로 읽히고 그림이 깨지면 화면에 뜬다.
        val auto = "그림입니다.\r\n원본 그림의 이름: CLP000043080017.bmp\r\n원본 그림의 크기: 가로 94pixel, 세로 33pixel"
        val file = HwpFile().apply {
            docInfo.bins.add(Bin(storageId = 1, ext = "PNG", data = png))
            section(
                P().ctrl(11, "gso ", Ctrl.picture(binItem = 1, alt = auto)),
                P().ctrl(11, "gso ", Ctrl.picture(binItem = 1, alt = "묶음 개체입니다.")),
                P().ctrl(11, "gso ", Ctrl.picture(binItem = 1, alt = "그림입니다. 2024년 일자리 증감 그래프")),
                // 사진이면 EXIF 줄이 더 붙는다(표본의 32개). 그래도 자동 설명이다.
                P().ctrl(11, "gso ", Ctrl.picture(binItem = 1, alt = auto + "\r\n사진 찍은 날짜: 2026년 01월 07일 오후 2:10\r\n프로그램 이름 : Adobe Photoshop 24.1")),
                // 짧은 '…입니다.' 라도 개체 종류의 이름이 아니면 사람이 쓴 것이다.
                P().ctrl(11, "gso ", Ctrl.picture(binItem = 1, alt = "조직도입니다.")),
                P().ctrl(11, "gso ", Ctrl.picture(binItem = 1, alt = "장식11-4입니다.")),
            )
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertEquals(4, Regex("alt=\"\"").findAll(body).count(), body)
            assertTrue("alt=\"그림입니다. 2024년 일자리 증감 그래프\"" in body, body) // 사람이 쓴 것으로 보이면 남긴다
            assertTrue("alt=\"조직도입니다.\"" in body, body)
            assertFalse("CLP000043080017" in body || "Photoshop" in body, body)
        }
    }

    @Test
    fun 그림은_BIN_DATA_의_차례로_찾아_풀어서_내준다() {
        val file = HwpFile().apply {
            // 저장소 번호와 차례가 다르다(실물 K05 처럼) — 차례 2 가 저장소 5 다.
            docInfo.bins.add(Bin(storageId = 9, ext = "jpg", data = ByteArray(10)))
            docInfo.bins.add(Bin(storageId = 5, ext = "PNG", data = png))
            section(P().text("그림 ").ctrl(11, "gso ", Ctrl.picture(binItem = 2, width = 14400, alt = "설명 글")).text(" 뒤"))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("그림 <img src=\"BinData/BIN0005.PNG\" alt=\"설명 글\" style=\"width:144pt\"/> 뒤" in body, body)
            val stream = assertNotNull(doc.openResource("BinData/BIN0005.PNG"))
            assertContentEquals(png, stream.use { it.readBytes() }) // 압축(따른다 + 파일 압축)을 풀어서
            assertEquals("image/png", doc.mediaTypeOf("BinData/bin0005.png")) // 이름은 대소문자를 가리지 않는다
            // 꾸러미 밖·다른 스트림은 내주지 않는다.
            assertNull(doc.openResource("FileHeader"))
            assertNull(doc.openResource("BinData/../FileHeader"))
            assertNull(doc.openResource("DocInfo"))
        }
    }

    @Test
    fun 그림의_압축_칸을_따르고_풀리지_않으면_날것으로() {
        val file = HwpFile().apply {
            docInfo.bins.add(Bin(1, "png", png, compression = 2)) // 압축하지 않음
            docInfo.bins.add(Bin(2, "png", png, compression = 1)) // 언제나 압축
            section(P().ctrl(11, "gso ", Ctrl.picture(1)).ctrl(11, "gso ", Ctrl.picture(2)))
        }
        val bytes = file.build()
        Hwp5.open(bytes).use { doc ->
            assertContentEquals(png, doc.openResource("BinData/BIN0001.png")!!.use { it.readBytes() })
            assertContentEquals(png, doc.openResource("BinData/BIN0002.png")!!.use { it.readBytes() })
        }
        // '따른다'(0)인데 파일이 압축이라 해 놓고 날것을 둔 그림 — 그림에 한해 날것으로 쓴다.
        // 날것으로 저장('압축하지 않음')한 뒤 DocInfo 의 BIN_DATA 칸만 '따른다' 로 되돌려 만든다.
        val tricky = HwpFile().apply {
            docInfo.bins.add(Bin(1, "png", png, compression = 2))
            section(P().ctrl(11, "gso ", Ctrl.picture(1)))
        }
        val di = tricky.docInfo.bytes()
        tricky.docInfoRaw = di.also { patchBinCompression(it) }
        Hwp5.open(tricky.build()).use { doc ->
            assertContentEquals(png, doc.openResource("BinData/BIN0001.png")!!.use { it.readBytes() })
            // 본문도 그 그림을 건다 — 자원 상한을 재는 쪽(`Hwp5Package.fitsResource`, 13단계 짝 대조)이 풀리지 않는 압축을
            // '넘는다' 로 보면 바탕은 내주는데 `img` 가 사라지고 '그릴 수 없는 그림' 으로 잘못 센다.
            assertTrue("<img src=\"BinData/BIN0001.png\"" in doc.body(), doc.body())
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.UNSUPPORTED_IMAGE])
        }
    }

    /** DocInfo 안의 첫 BIN_DATA 의 압축 칸(비트 4–5)을 0 으로. */
    private fun patchBinCompression(di: ByteArray) {
        val cur = RecordCursor(di, 0)
        while (cur.peek()) {
            if (cur.tag == HwpTag.BIN_DATA) {
                di[cur.payload] = (di[cur.payload].toInt() and 0xCF).toByte()
                return
            }
            cur.advance()
        }
    }

    @Test
    fun 그릴_수_없는_그림과_연결된_그림은_세기만_한다() {
        val file = HwpFile().apply {
            docInfo.bins.add(Bin(1, "wmf", ByteArray(40)))
            docInfo.bins.add(Bin(2, "png", png, link = true))
            section(P().ctrl(11, "gso ", Ctrl.picture(1)).ctrl(11, "gso ", Ctrl.picture(2)).ctrl(11, "gso ", Ctrl.picture(7)).text("글"))
        }
        Hwp5.open(file).use { doc ->
            assertFalse("<img" in doc.body())
            val dropped = doc.unsupported.snapshot()
            assertEquals(2, dropped[UnsupportedFeatures.UNSUPPORTED_IMAGE]) // wmf + 없는 차례 7
            assertEquals(1, dropped[UnsupportedFeatures.LINKED_FILE])
            assertNull(doc.openResource("BinData/BIN0001.wmf")) // 화면이 못 그리는 것은 내주지도 않는다
        }
    }

    @Test
    fun 글상자와_묶음_안의_그림() {
        val file = HwpFile().apply {
            docInfo.bins.add(Bin(1, "png", png))
            docInfo.bins.add(Bin(2, "png", png))
            section(
                P().text("앞 글").ctrl(11, "gso ", Ctrl.textBox(listOf(P().text("상자 안 첫 줄"), P().text("상자 안 둘째 줄")))).text("뒤 글"),
                P().ctrl(11, "gso ", Ctrl.group(1, 2)),
            )
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<p>앞 글</p><div class=\"tb\"><p>상자 안 첫 줄</p><p>상자 안 둘째 줄</p></div><p>뒤 글</p>" in body, body)
            assertEquals(2, body.occurrences("<img src=\"BinData/BIN000"), body)
            // 묶음 안의 그림은 자기 폭(50pt)을 쓴다.
            assertTrue("style=\"width:50pt\"" in body, body)
            // 글상자가 든 사각형은 글을 그렸으므로 '도형' 으로 세지 않는다(HWPX·docx 와 같다 — 아래 시험).
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE])
        }
    }

    @Test
    fun 도형은_글상자가_없는_것만_세고_개체_연결선도_도형이다() {
        // 13단계 짝 대조: HWP 는 글상자를 그린 도형까지 세어 같은 보도자료의 '도형' 배지가 HWPX 보다 컸다(K01 8, K19 6 —
        // HWPX 는 3, 0). 개체 연결선(`$col`)은 세지도 않고 버렸다(K04 의 둘).
        val file = HwpFile().section(
            P().text("앞").ctrl(11, "gso ", Ctrl.textBox(listOf(P().text("상자 글")))),
            P().ctrl(11, "gso ", Ctrl.drawing("\$rec")).ctrl(11, "gso ", Ctrl.drawing("\$col")).text("뒤"),
        )
        Hwp5.open(file).use { doc ->
            assertTrue("상자 글" in doc.body(), doc.body())
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE]) // 빈 사각형 + 연결선
            doc.partHtml(0)
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE]) // 그려도 두 번 세지 않는다
        }
    }

    @Test
    fun 자원_상한을_넘게_풀리는_그림은_img_를_쓰지_않고_센다() {
        // 13단계 짝 대조: HWP 는 풀어서 32 MiB 를 넘는 그림에도 `img` 를 적었고, 바탕이 그것을 내주지 않아 깨진 그림만 떴다.
        // 상한을 작게 두고 두 길(날것·압축)을 본다 — 압축된 것은 **푼 크기**가 넘는다(0 이 이어져 몇십 바이트로 줄어든다).
        val cap = 1_000L
        val big = png + ByteArray(cap.toInt()) // 1,208바이트
        val exact = png + ByteArray(cap.toInt() - png.size) // 딱 상한
        val file = HwpFile().apply {
            docInfo.bins.add(Bin(1, "png", big, compression = 2)) // 날것 — 스트림이 넘는다
            docInfo.bins.add(Bin(2, "png", big, compression = 1)) // 압축 — 스트림은 작고 푼 것이 넘는다
            docInfo.bins.add(Bin(3, "png", exact, compression = 1)) // 압축 — 푼 것이 딱 상한(들어간다)
            docInfo.bins.add(Bin(4, "png", exact, compression = 2)) // 날것 — 딱 상한
            section(
                P().text("그림").ctrl(11, "gso ", Ctrl.picture(1)).ctrl(11, "gso ", Ctrl.picture(2))
                    .ctrl(11, "gso ", Ctrl.picture(3)).ctrl(11, "gso ", Ctrl.picture(4)).ctrl(11, "gso ", Ctrl.picture(2)),
            )
        }
        val bytes = file.build()
        Hwp5.open(bytes, Hwp5Options(maxImageBytes = cap)).use { doc ->
            val body = doc.body()
            assertFalse("BIN0001" in body || "BIN0002" in body, body)
            assertTrue("<img src=\"BinData/BIN0003.png\"" in body && "<img src=\"BinData/BIN0004.png\"" in body, body)
            assertEquals(3, doc.unsupported.snapshot()[UnsupportedFeatures.UNSUPPORTED_IMAGE]) // 날것 하나 + 압축 둘(같은 그림 두 번)
        }
        // 제품의 상한(32 MiB)이면 모두 들어간다.
        Hwp5.open(bytes).use { doc ->
            assertEquals(5, doc.body().occurrences("<img"), doc.body())
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.UNSUPPORTED_IMAGE])
        }
    }

    @Test
    fun 셈만_하는_풀기는_inflate_와_같은_자리에서_넘는다고_본다() {
        val data = ByteArray(10_000) { (it % 7).toByte() }
        val packed = HwpFile.deflate(data)
        assertFalse(HwpInflate.exceeds(packed.inputStream(), 10_000))
        assertTrue(HwpInflate.exceeds(packed.inputStream(), 9_999))
        // 잘린 스트림은 깨진 것이다 — `inflate` 와 같다.
        val cut = packed.copyOf(packed.size / 2)
        assertFailsWith<HwpFormatException> { HwpInflate.inflate(cut, 0, cut.size, 100_000, "t") }
        assertFailsWith<HwpFormatException> { HwpInflate.exceeds(cut.inputStream(), 100_000) }
    }

    @Test
    fun 삽입_개체와_차트는_센다() {
        val ole: (Int) -> ByteArray = { lc ->
            Rec.join(
                listOf(
                    Rec.record(HwpTag.CTRL_HEADER, lc, Ctrl.common("gso ")),
                    Rec.record(HwpTag.SHAPE_COMPONENT, lc + 1, Ctrl.component("\$ole", true)),
                    Rec.record(HwpTag.SHAPE_COMPONENT_OLE, lc + 2, ByteArray(26)),
                )
            )
        }
        val chart: (Int) -> ByteArray = { lc ->
            Rec.join(
                listOf(
                    Rec.record(HwpTag.CTRL_HEADER, lc, Ctrl.common("gso ")),
                    Rec.record(HwpTag.SHAPE_COMPONENT, lc + 1, Ctrl.component("\$ole", true)),
                    Rec.record(HwpTag.CHART_DATA, lc + 2, ByteArray(2)),
                )
            )
        }
        val file = HwpFile().section(P().text("개체").ctrl(11, "gso ", ole).ctrl(11, "gso ", chart).text("끝"))
        Hwp5.open(file).use { doc ->
            assertTrue("개체끝" in plain(doc.body()), doc.body())
            val dropped = doc.unsupported.snapshot()
            assertEquals(1, dropped[UnsupportedFeatures.EMBEDDED_OBJECT])
            assertEquals(1, dropped[UnsupportedFeatures.CHART])
        }
    }

    @Test
    fun 책갈피는_그_문단에_id_를_준다() {
        // 책갈피 이름은 자식 CTRL_DATA(파라미터 셋)의 첫 문자열이다.
        val bookmark: (Int) -> ByteArray = { lc ->
            Rec.join(
                listOf(
                    Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of("bokm")).toByteArray()),
                    Rec.record(HwpTag.CTRL_DATA, lc + 1, Bytes().u16(0x21B).u16(1).u16(0x4000).u16(1).wstr("결론").toByteArray()),
                )
            )
        }
        val file = HwpFile().section(
            P().ctrl(3, "%hlk", Ctrl.field("%hlk", "?결론;0;0;0;")).text("결론으로").fieldEnd(),
            P().text("중간"),
            P().text("결론 문단").ctrl(22, "bokm", bookmark),
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<a href=\"#p-2\">결론으로</a>" in body, body)
            assertTrue("<p id=\"p-2\">결론 문단</p>" in body, body)
        }
    }

    @Test
    fun 짝이_모두_어긋난_컨트롤이_많아도_오래_걸리지_않는다() {
        // 확장 컨트롤 2,000개, 짝 레코드는 전부 다른 ID — 찾는 폭을 묶지 않으면 제곱(400만 번)으로 돈다.
        val p = P().text("시작")
        repeat(2_000) { p.ctrl(11, "eqed", Ctrl.autoNumber(3, 1)) }
        p.text("끝")
        val file = HwpFile().section(p)
        val t0 = System.nanoTime()
        Hwp5.open(file).use { doc ->
            assertTrue("시작끝" in plain(doc.body()))
            assertEquals(2_000, doc.unsupported.snapshot()[UnsupportedFeatures.UNKNOWN_ELEMENT])
        }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000)
    }

    @Test
    fun 긴_문서는_제목_앞에서_조각이_나뉘고_번호가_이어진다() {
        val file = HwpFile().apply {
            docInfo.numberings.add(listOf(0 to "^1.")) // 1 — 번호 문단
            docInfo.numberings.add(listOf(0 to "")) // 2 — 개요(표지 없음)
            docInfo.paraShapes.add(PS(headType = 1, headLevel = 0)) // 1
            docInfo.paraShapes.add(PS(headType = 2, headLevel = 0, numbering = 1)) // 2
            val paras = arrayListOf(P().ctrl(2, "secd", Ctrl.section(outlineNumbering = 2)))
            for (ch in 1..4) {
                paras.add(P(1).text("장 $ch"))
                repeat(30) { paras.add(P(2).text("번호 문단 " + "글".repeat(20))) }
            }
            section(*paras.toTypedArray())
        }
        val small = Hwp5Options(chunk = HancomChunkPolicy(softChars = 500, softBlocks = 1000, hardChars = 100_000, hardBlocks = 100_000))
        Hwp5.open(file, small).use { doc ->
            assertEquals(4, doc.parts.size, "${doc.parts}")
            assertEquals(listOf("장 1", "장 2", "장 3", "장 4"), doc.parts.map { it.label })
            assertEquals(listOf(0, 1, 2, 3), doc.outline.map { it.partIndex })
            // 번호 문단의 셈이 조각을 건너 이어진다(1..120). 조각을 뒤에서부터 그려도 같다.
            val marks = (3 downTo 0).map { i -> Regex("<span class=\"mk\">(\\d+)\\.</span>").findAll(doc.body(i)).map { it.groupValues[1].toInt() }.toList() }.reversed()
            assertEquals((1..120).toList(), marks.flatten())
            // 조각 사이 링크(목차)가 가리키는 id 가 그 조각에 있다.
            for (o in doc.outline) assertTrue("id=\"${o.anchor}\"" in doc.body(o.partIndex), o.toString())
        }
    }

    @Test
    fun 표의_칸도_조각의_무게로_센다() {
        // 칸 30개짜리 표 넷 — 최상위 문단은 넷뿐이지만 칸이 쌓여 조각이 나뉜다(표는 쪼개지 않는다).
        fun grid() = Ctrl.table(5, 6, (0 until 30).map { Ctrl.Cell(it / 6, it % 6, listOf(P().text("칸$it"))) })
        val file = HwpFile().section(*Array(4) { P().ctrl(11, "tbl ", grid()) })
        val policy = Hwp5Options(chunk = HancomChunkPolicy(softChars = 1_000_000, softBlocks = 1_000_000, hardChars = 1_000_000, hardBlocks = 50))
        Hwp5.open(file, policy).use { doc ->
            assertEquals(2, doc.parts.size, "${doc.parts}")
            assertEquals(2, doc.body(0).occurrences("<table>"))
            assertEquals(2, doc.body(1).occurrences("<table>"))
        }
    }

    @Test
    fun 상태를_찍을_예산이_없어도_구역의_경계에서는_끊는다() {
        // 공용 저울(`HancomChunkMeter`)로 옮기며 지켜야 했던 약속 — 예산이 다하면 구역 안에서는 더 끊지 않지만, 구역이 바뀌면
        // 언제나 끊는다(부분 하나가 구역 둘에 걸치면 다시 그릴 때 뒤 구역을 잃는다).
        val file = HwpFile().apply {
            section(*Array(6) { P().text("첫 구역 $it") })
            section(*Array(6) { P().text("둘째 구역 $it") })
            docInfo.sectionCount = 2
        }
        val policy = Hwp5Options(chunk = HancomChunkPolicy(softChars = 1, softBlocks = 1, hardChars = 1, hardBlocks = 1, snapshotBudget = 1))
        Hwp5.open(file, policy).use { doc ->
            assertEquals(2, doc.parts.size, "${doc.parts}")
            assertTrue("첫 구역 5" in doc.body(0) && "둘째 구역" !in doc.body(0), doc.body(0))
            assertTrue("둘째 구역 0" in doc.body(1) && "둘째 구역 5" in doc.body(1), doc.body(1))
        }
    }

    @Test
    fun 새_번호_지정은_부분의_경계를_건너_이어진다() {
        // 새 번호 지정(`nwno`, 13단계 짝 대조)은 걷기 상태의 각주 셈을 옮긴다 — 훑기가 부분의 시작마다 찍는 상태에도 들어가야
        // 뒤 부분의 번호가 앞 부분에서 이어진다(훑기와 그리기가 같은 컨트롤을 같은 차례로 소비한다는 약속). 한 부분만 보는
        // 시험은 그리기에서만 셈을 옮겨도 통과한다. 링크의 `id` 는 부분마다 1 부터다(HWPX 와 같다).
        val file = HwpFile().apply {
            docInfo.paraShapes.add(PS(headType = 1, headLevel = 0))
            section(
                P(1).text("장 1"),
                P().text("가").ctrl(21, "nwno", Ctrl.newNumber(1, 7)).ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("a")))),
                P(1).text("장 2"),
                P().text("나").ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("b")))),
            )
        }
        Hwp5.open(file, Hwp5Options(chunk = HancomChunkPolicy(softChars = 1, hardChars = 100_000, hardBlocks = 100_000))).use { doc ->
            assertEquals(2, doc.parts.size, "${doc.parts}")
            // 뒤 부분을 먼저 그려도 같다 — 시작 상태는 훑기가 찍어 둔 것이다.
            assertTrue("<a href=\"#fn-1\">8)</a>" in doc.body(1), doc.body(1))
            assertTrue("<a href=\"#fn-1\">7)</a>" in doc.body(0), doc.body(0))
        }
    }

    @Test
    fun 다른_조각의_문단을_가리키는_링크는_부분_경로로() {
        val file = HwpFile().apply {
            docInfo.paraShapes.add(PS(headType = 1, headLevel = 0))
            section(
                P().ctrl(3, "%hlk", Ctrl.field("%hlk", "?#777;0;1;0;")).text("둘째 장으로").fieldEnd(),
                P().text("글".repeat(600)),
                P(1).text("둘째 장").also { it.instanceId = 777 },
            )
        }
        Hwp5.open(file, Hwp5Options(chunk = HancomChunkPolicy(softChars = 100, hardChars = 100_000, hardBlocks = 100_000))).use { doc ->
            assertEquals(2, doc.parts.size)
            assertTrue("<a href=\"~part-1.html#p-2\">둘째 장으로</a>" in doc.body(0), doc.body(0))
            assertTrue("<h1 id=\"p-2\"" in doc.body(1))
        }
    }

    @Test
    fun 구역_중간의_깨진_레코드는_부분의_실패로_끝나고_앞의_글은_남는다() {
        val good = Rec.join(listOf(P().text("멀쩡한 첫 문단").bytes(0), P().text("멀쩡한 둘째 문단").bytes(0)))
        // 크기 칸이 스트림을 넘는 레코드를 끝에 붙인다.
        val broken = good + Bytes().i32(HwpTag.PARA_HEADER or (500 shl 20)).zeros(10).toByteArray()
        val file = HwpFile().apply {
            section(P().text("x"))
            sections[0] = broken
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("멀쩡한 첫 문단" in body && "멀쩡한 둘째 문단" in body, body)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.PART_FAILED }, "${doc.warnings}")
        }
    }

    @Test
    fun 깨진_DocInfo_는_기본_서식으로_그리고_알린다() {
        val file = HwpFile().apply {
            docInfoRaw = byteArrayOf(0x15, 0x00, 0x50, 0x7F) // 크기가 스트림을 넘는 글자 모양 레코드
            section(P().text("서식 없이라도 보인다"))
        }
        Hwp5.open(file).use { doc ->
            assertTrue("서식 없이라도 보인다" in doc.body())
            assertTrue(doc.warnings.any { it.code == FlowWarnings.AUX_FAILED && it.detail == "DocInfo" }, "${doc.warnings}")
        }
        // 레코드 하나의 알맹이만 짧으면 자리를 지키고(뒤 번호가 밀리지 않는다) 알린다.
        val shortOne = HwpFile().apply {
            docInfo.charShapes.add(CS(bold = true)) // 1
            docInfo.charShapes.add(CS(italic = true)) // 2
            section(P().text("기울임", 2))
        }
        val di = shortOne.docInfo.bytes()
        shortOne.docInfoRaw = truncateCharShape(di, 1)
        Hwp5.open(shortOne).use { doc ->
            assertTrue("font-style:italic" in doc.body() || "<p style=\"font-style:italic\">" in doc.body(), doc.body())
            assertTrue(doc.warnings.any { it.code == FlowWarnings.AUX_FAILED })
        }
    }

    /** [di] 의 [index] 번째 글자 모양 레코드를 10바이트로 줄인 DocInfo. */
    private fun truncateCharShape(di: ByteArray, index: Int): ByteArray {
        val parts = ArrayList<ByteArray>()
        val cur = RecordCursor(di, 0)
        var n = 0
        while (cur.peek()) {
            val payload = di.copyOfRange(cur.payload, cur.payload + cur.size)
            if (cur.tag == HwpTag.CHAR_SHAPE && n++ == index) {
                parts.add(Rec.record(cur.tag, cur.level, payload.copyOf(10)))
            } else {
                parts.add(Rec.record(cur.tag, cur.level, payload))
            }
            cur.advance()
        }
        return Rec.join(parts)
    }

    @Test
    fun 쓰기_상한에서_멈추고_잘렸다고_알린다() {
        val paras = (1..200).map { P().text("문단 $it " + "가".repeat(200)) }
        val file = HwpFile().section(*paras.toTypedArray())
        Hwp5.open(file, Hwp5Options(maxChars = 5_000)).use { doc ->
            val html = assertNotNull(doc.partHtml(0))
            assertTrue(html.length < 20_000, "${html.length}")
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
            assertTrue(html.endsWith("</body></html>"))
        }
    }

    @Test
    fun 적힌_구역_수보다_스트림이_적으면_있는_데까지() {
        val file = HwpFile().apply {
            section(P().text("첫 구역"))
            docInfo.sectionCount = 3
        }
        Hwp5.open(file).use { doc ->
            assertEquals(1, doc.parts.size)
            assertTrue("첫 구역" in doc.body())
            assertTrue(doc.warnings.any { it.code == FlowWarnings.PART_FAILED && it.detail == "Section1" }, "${doc.warnings}")
        }
    }
}
