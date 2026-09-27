package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.html.BulletGlyphs
import io.github.donggi.iroiroviewer.format.html.HancomNumbers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 레코드 머리·알맹이 읽기·파일 머리·번호 모양 — 흐름 렌더 아래의 바탕. */
class Hwp5RecordsTest {

    @Test
    fun 머리의_태그_수준_크기를_비트대로_읽는다() {
        // 명세의 예: DISTRIBUTE_DOC_DATA, 수준 0, 크기 256 → 0x1000001C.
        val b = Bytes().i32(0x1000001C).zeros(256).toByteArray()
        val cur = RecordCursor(b, 0)
        assertTrue(cur.peek())
        assertEquals(0x1C, cur.tag)
        assertEquals(0, cur.level)
        assertEquals(256, cur.size)
        assertEquals(4, cur.payload)
        // 수준은 10–19 비트.
        val r = Rec.record(HwpTag.PARA_TEXT, 3, ByteArray(10))
        val c2 = RecordCursor(r, 0)
        c2.peek()
        assertEquals(HwpTag.PARA_TEXT, c2.tag)
        assertEquals(3, c2.level)
        assertEquals(10, c2.size)
    }

    @Test
    fun 크기_0xFFF_는_다음_32비트가_크기다() {
        val big = ByteArray(5000) { (it and 0x7F).toByte() }
        val r = Rec.record(HwpTag.PARA_TEXT, 1, big)
        assertEquals(4 + 4 + 5000, r.size)
        val cur = RecordCursor(r, 0)
        cur.peek()
        assertEquals(5000, cur.size)
        assertEquals(8, cur.payload)
        assertEquals(big[4999], r[cur.payload + 4999])
        // 딱 4095바이트도 늘인 모양이다('4095 이상').
        val exact = Rec.record(HwpTag.PARA_TEXT, 1, ByteArray(4095))
        assertEquals(0xFFF, exact.i32(0) ushr 20)
        val c2 = RecordCursor(exact, 0)
        c2.peek()
        assertEquals(4095, c2.size)
        c2.advance()
        assertFalse(c2.peek())
    }

    @Test
    fun 잘린_머리와_스트림을_넘는_크기는_깨진_파일이다() {
        assertFailsWith<HwpFormatException> { RecordCursor(byteArrayOf(0x42, 0, 0), 0).peek() }
        // 크기 칸이 100 인데 알맹이가 10.
        val lying = Bytes().i32(HwpTag.PARA_TEXT or (100 shl 20)).zeros(10).toByteArray()
        assertFailsWith<HwpFormatException> { RecordCursor(lying, 0).peek() }
        // 늘인 크기가 20억.
        val huge = Bytes().i32(HwpTag.PARA_TEXT or (0xFFF shl 20)).i32(2_000_000_000).zeros(4).toByteArray()
        assertFailsWith<HwpFormatException> { RecordCursor(huge, 0).peek() }
        // 늘인 크기 칸 자체가 잘렸다.
        val cut = Bytes().i32(HwpTag.PARA_TEXT or (0xFFF shl 20)).u16(1).toByteArray()
        assertFailsWith<HwpFormatException> { RecordCursor(cut, 0).peek() }
    }

    @Test
    fun 자식_트리는_수준으로_건너뛴다() {
        val stream = Rec.join(
            listOf(
                Rec.record(HwpTag.PARA_HEADER, 0, ByteArray(24)),
                Rec.record(HwpTag.PARA_TEXT, 1, ByteArray(4)),
                Rec.record(HwpTag.CTRL_HEADER, 1, ByteArray(4)),
                Rec.record(HwpTag.LIST_HEADER, 2, ByteArray(8)),
                Rec.record(HwpTag.PARA_HEADER, 2, ByteArray(24)),
                Rec.record(HwpTag.PARA_TEXT, 3, ByteArray(2)),
                Rec.record(HwpTag.PARA_HEADER, 0, ByteArray(24)),
            )
        )
        val cur = RecordCursor(stream, 0)
        cur.peek()
        cur.skipWithChildren()
        assertTrue(cur.peek())
        assertEquals(HwpTag.PARA_HEADER, cur.tag)
        assertEquals(0, cur.level)
        cur.skipWithChildren()
        assertFalse(cur.peek())
    }

    @Test
    fun 문자열은_레코드_안에_있어야_하고_상한에서_자른다() {
        val ok = Bytes().wstr("가나다라마").toByteArray()
        assertEquals("가나", PayloadReader(ok, 0, ok.size).wstr(2))
        val lie = Bytes().u16(50).u16('a'.code).toByteArray()
        assertFailsWith<HwpFormatException> { PayloadReader(lie, 0, lie.size).wstr() }
        val r = PayloadReader(ByteArray(3), 0, 3)
        assertEquals(7, r.u16Or(7).let { r.u16Or(7) }) // 두 번째는 모자라 기본값
    }

    @Test
    fun 파일_머리의_서명과_속성() {
        val fh = Bytes().bytes("HWP Document File".toByteArray()).zeros(15).i32(0x05010001).u32(0x20005).u32(0).u32(4).zeros(208).toByteArray()
        val h = Hwp5FileHeader.parse(fh)
        assertEquals(5, h.major)
        assertTrue(h.compressed)
        assertTrue(h.distribution)
        assertFalse(h.password)
        assertFalse(h.drm)
        assertTrue(h.atLeast(5, 0, 3, 0))
        assertFalse(h.atLeast(5, 1, 1, 0))
        val bad = fh.copyOf().also { it[0] = 'X'.code.toByte() }
        assertFailsWith<HwpFormatException> { Hwp5FileHeader.parse(bad) }
        // HWP 3.0 의 앞머리는 CFB 가 아닌 파일의 첫 글자다.
        val v3 = "HWP Document File V3.00 \u001a\u0001\u0002\u0003\u0004\u0005".toByteArray()
        assertTrue(Hwp5FileHeader.isHwp3(v3))
        assertFalse(Hwp5FileHeader.isHwp3(fh))
    }

    @Test
    fun 번호_정의의_모양은_HWPX_와_한_표의_값으로_읽는다() {
        // 모양·형식·셈은 두 한글 변환기가 함께 쓰는 `HancomNumbers` 가 한다(그쪽 시험이 값 하나하나를 본다). 이 모듈이 하는 것은
        // HWP 의 모양 번호(수준 속성의 비트 5–9, 표 41)를 그 표로 옮기는 것이다 — 모르는 번호(17 이상)는 숫자.
        val b = Bytes()
        for ((k, shape) in listOf(0, 8, 1, 20, 31, 15, 16).withIndex()) {
            b.u32((shape shl 5).toLong()).u16(0).u16(0).u32(0).wstr(if (k == 1) "(^2)" else "^${k + 1}.")
        }
        b.u16(1)
        repeat(7) { b.u32(1) }
        val bytes = b.toByteArray()
        val def = NumberingDef.parse(PayloadReader(bytes, 0, bytes.size))
        assertEquals(
            listOf(
                HancomNumbers.DIGIT, HancomNumbers.HANGUL_SYLLABLE, HancomNumbers.CIRCLED_DIGIT, HancomNumbers.DIGIT, HancomNumbers.DIGIT,
                HancomNumbers.DECAGON_CIRCLE, HancomNumbers.DECAGON_CIRCLE_HANJA,
            ),
            def.shapes.take(7),
        )
        val values = intArrayOf(2, 3, HancomNumbers.UNSET, 0, 0, 0, 0, 0, 0, 0)
        assertEquals("(다)", HancomNumbers.fill(def.levels[1]!!.format, 1, values, def.shapes))
        assertEquals("2.다", HancomNumbers.fill("^n", 1, values, def.shapes))
        // 쓰이지 않은 깊은 수준은 비운다(`UNSET` 이 글자로 새지 않는다).
        assertEquals("2..", HancomNumbers.fill("^1.^3.", 1, values, def.shapes))
    }

    @Test
    fun 걷기_상태의_사본은_번호를_따로_센다() {
        // 부분의 시작마다 찍어 두는 상태([WalkState.copy]) — 사본을 세어도 원본이 움직이지 않아야 뒤 부분을 먼저 그려도 번호가 같다.
        val s = WalkState()
        val starts = IntArray(10) { 1 }
        assertEquals(1, s.numbers.next(1, 0, starts)[0])
        assertEquals(1, s.numbers.next(1, 1, starts)[1])
        s.footnoteNo = 4
        val snap = s.copy()
        s.numbers.next(1, 1, starts)
        s.footnoteNo++
        assertEquals(2, snap.numbers.next(1, 1, starts)[1]) // 사본은 따로 센다
        assertEquals(4, snap.footnoteNo)
        assertEquals(1, s.numbers.next(2, 0, starts)[0]) // 번호 정의마다 따로
    }

    @Test
    fun 글머리표의_사설_영역_글자를_옮긴다() {
        assertEquals("▪", BulletGlyphs.map('\uF0A7'))
        assertEquals("□", BulletGlyphs.map('\uF06F'))
        assertEquals("•", BulletGlyphs.map('\uF012'))
        assertEquals("•", BulletGlyphs.map('\uE123'))
        assertEquals("○", BulletGlyphs.map('○'))
    }

    @Test
    fun 필드_명령에서_링크의_대상을_읽는다() {
        assertTrue(HwpLinks.parse("http\\://www.example.com;1;0;0;") is HwpLinks.External)
        val inst = HwpLinks.parse("?#645989673;0;1;0;")
        assertTrue(inst is HwpLinks.Instance && inst.id == 645989673L)
        val bm = HwpLinks.parse("?책갈피1;0;0;0;")
        assertTrue(bm is HwpLinks.Bookmark && bm.name == "책갈피1")
        assertTrue(HwpLinks.parse(";1;0;0;") is HwpLinks.None)
        assertTrue(HwpLinks.parse("?#abc;") is HwpLinks.None)
    }

    @Test
    fun 색은_BGR_에서_옮기고_위_바이트가_차면_없음() {
        assertEquals(0xFF0000, colorOf(0x0000FFL))
        assertEquals(0x0000FF, colorOf(0xFF0000L))
        assertEquals(-1, colorOf(0xFFFFFFFFL))
        assertEquals("#ff8000", RunStyle.hex(0xFF8000))
        assertNull(RunStyle.hex(-1))
    }
}
