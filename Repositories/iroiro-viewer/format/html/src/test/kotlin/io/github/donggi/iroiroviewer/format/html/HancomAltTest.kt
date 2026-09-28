package io.github.donggi.iroiroviewer.format.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 두 한글 변환기가 같이 쓰는 그림 설명문 규칙(`HancomAlt`). 처음에는 HWP 5.0 변환기 안에만 있었고 HWPX 는 대체 글을 늘 비웠다.
 * 여기 있는 값은 **두 포맷의 답**이다 — 각 변환기의 시험이 제 포맷으로 같은 답을 확인한다.
 */
class HancomAltTest {

    private val auto = "그림입니다.\r\n원본 그림의 이름: CLP000043080017.bmp\r\n원본 그림의 크기: 가로 94pixel, 세로 33pixel"

    @Test
    fun 한글이_저절로_넣은_설명문은_버린다() {
        assertEquals("", HancomAlt.of(auto))
        assertEquals("", HancomAlt.of("묶음 개체입니다."))
        assertEquals("", HancomAlt.of("장식11-4입니다."))
        // 사진이면 EXIF 줄이 더 붙는다(`열쇠: 값`, `열쇠 : 값` 모두).
        assertEquals("", HancomAlt.of("$auto\n사진 찍은 날짜: 2026년 01월 07일 오후 2:10\n프로그램 이름 : Adobe Photoshop 24.1"))
        // 빈 설명은 쓸 것이 없다.
        assertEquals("", HancomAlt.of(null))
        assertEquals("", HancomAlt.of("  \r\n "))
    }

    @Test
    fun 사람이_쓴_설명은_남긴다() {
        assertEquals("그림입니다. 2024년 일자리 증감 그래프", HancomAlt.of("그림입니다. 2024년 일자리 증감 그래프"))
        // 짧은 '…입니다.' 라도 개체 종류의 이름이 아니면 사람이 쓴 것이다.
        assertEquals("조직도입니다.", HancomAlt.of("조직도입니다."))
        // 저절로 넣은 첫 줄 뒤에 사람이 이어 쓴 설명이 있으면 남긴다.
        assertTrue(HancomAlt.of("$auto\n분기별 매출 추이").endsWith("분기별 매출 추이"))
        // 앞뒤 공백은 떼고 대체 글의 상한으로 자른다.
        assertEquals("설명", HancomAlt.of("  설명 \r\n"))
        assertEquals(HancomAlt.MAX_CHARS, HancomAlt.of("가".repeat(1000)).length)
    }

    @Test
    fun 자르기_전에_가린다() {
        // 300자에서 잘라 읽으면 끝 줄의 `:` 앞이 잘려 저절로 넣은 설명이 사람이 쓴 것으로 남는다.
        val long = "그림입니다.\r\n원본 그림의 이름: CLP000043080017.bmp" +
            (1..12).joinToString("") { "\r\n사진 속성 $it 번째 항목(카메라 제조사가 정한 이름): 값 $it" }
        assertFalse(HancomAlt.isAutoComment(long.take(HancomAlt.MAX_CHARS)), "잘린 모양은 사람이 쓴 것처럼 보인다 — 표본이 그 경우여야 한다")
        assertTrue(long.length <= HancomAlt.MAX_READ_CHARS)
        assertEquals("", HancomAlt.of(long))
    }
}
