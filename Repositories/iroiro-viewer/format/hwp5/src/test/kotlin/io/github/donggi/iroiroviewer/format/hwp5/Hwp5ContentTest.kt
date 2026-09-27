package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 문단 안 — 제어 글자, 글자 모양, 문단 모양, 번호, 필드, 각주, 수식. */
class Hwp5ContentTest {

    @Test
    fun 제어_글자를_걷는다_줄바꿈_탭_묶음빈칸_하이픈() {
        val file = HwpFile().section(P().text("첫째").br().text("둘째").tab().text("셋째").nbsp().text("넷").hyphen().text("다섯"))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("첫째<br/>둘째\t셋째\u00A0넷-다섯" in body, body)
            // 8칸 컨트롤의 알맹이(0x4141 = 'A')가 글자로 새지 않는다.
            assertFalse("A" in plain(body), body)
        }
    }

    @Test
    fun 확장_컨트롤은_8칸을_건너뛰고_짝이_맞는_레코드를_쓴다() {
        // 확장 컨트롤 셋(구역 정의·수식·자동 번호) 사이의 글이 모두 제자리에 나온다.
        val file = HwpFile().section(
            P().ctrl(2, "secd", Ctrl.section()).text("앞").ctrl(11, "eqed", Ctrl.equation("a over b")).text("가운데")
                .ctrl(18, "atno", Ctrl.autoNumber(kind = 4, number = 7, suffix = ')')).text("뒤"),
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue(Regex("앞<span class=\"math\">a over b</span>가운데7\\)뒤").containsMatchIn(body), body)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.EQUATION])
            assertFalse("B" in plain(body))
        }
    }

    @Test
    fun 컨트롤_ID_가_어긋나면_그_컨트롤만_버린다() {
        // 글에는 'eqed' 가 적혔는데 짝 레코드는 'tbl ' 이다 — 표를 수식 자리에 그리지 않는다.
        val file = HwpFile().section(P().text("글").ctrl(11, "eqed", Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().text("칸")))))).text("끝"))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("글" in body && "끝" in body)
            assertFalse("<table" in body, body)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.UNKNOWN_ELEMENT])
        }
    }

    @Test
    fun 빈_칸은_칸마다_줄_하나를_남긴다() {
        // 빈 문단 세기(셋까지)를 표 전체로 하면 넷째 빈 칸부터 줄이 사라져 행이 납작해졌다(K19). HWPX 와 같이 칸마다 센다.
        val cells = (0 until 6).map { Ctrl.Cell(0, it, listOf(P())) }
        val file = HwpFile().section(P().ctrl(11, "tbl ", Ctrl.table(1, 6, cells)))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertEquals(6, Regex("<td[^>]*><p[^>]*><br/></p></td>").findAll(body).count(), body)
        }
    }

    @Test
    fun 모양이_3D_단선인_취소선은_긋지_않는다() {
        // 공문서의 글자 모양이 흔히 '여부 1 · 밑줄 자리 가운데 · 모양 3D 단선' 이다(K19 는 512개 중 112개). 그 모양이 걸린
        // '보도자료'·'배포' 에 줄을 그었다 — 한컴의 HWPX 내보내기는 같은 모양을 `3D` 로 적고 HWPX 변환기는 긋지 않는다.
        val file = HwpFile().apply {
            docInfo.charShapes.addAll(
                listOf(
                    CS(strike = true, underline = 2, strikeShape = 15), // 1 공문서의 그 모양
                    CS(strike = true, strikeShape = 7), // 2 2중선 — 진짜 취소선
                    CS(underline = 2), // 3 밑줄 자리 '가운데'(모양 실선)
                )
            )
            section(P().text("보도자료", 1).text(" 지운 글", 2).text(" 가운데", 3))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("보도자료<span style=\"text-decoration:line-through\"> 지운 글</span>" in body, body)
            assertTrue("<span style=\"text-decoration:line-through\"> 가운데</span>" in body, body)
            assertEquals(2, Regex("line-through").findAll(body).count(), body)
        }
    }

    @Test
    fun 글자_모양_구간이_굵게_기울임_색_밑줄_취소선_첨자가_된다() {
        val file = HwpFile().apply {
            docInfo.charShapes.addAll(
                listOf(
                    CS(bold = true), // 1
                    CS(italic = true, color = 0xFF0000), // 2
                    CS(underline = 1), // 3
                    CS(strike = true), // 4
                    CS(sup = true), // 5
                    CS(size = 2000), // 6
                    CS(shade = 0x00FFFF00L), // 7 음영(BGR) = 하늘색
                )
            )
            section(
                P().text("보통").text("굵게", 1).text("기울임", 2).text("밑줄", 3).text("취소", 4).text("위", 5)
                    .text("크게", 6).text("음영", 7).text("끝", 0),
            )
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("보통<span style=\"font-weight:bold\">굵게</span>" in body, body)
            assertTrue("<span style=\"font-style:italic;color:#ff0000\">기울임</span>" in body, body)
            assertTrue("<span style=\"text-decoration:underline\">밑줄</span>" in body, body)
            assertTrue("<span style=\"text-decoration:line-through\">취소</span>" in body, body)
            assertTrue("<sup>위</sup>" in body, body)
            assertTrue("<span style=\"font-size:200%\">크게</span>" in body, body)
            assertTrue("<span style=\"background-color:#00ffff\">음영</span>" in body, body)
            assertTrue(body.endsWith("끝</p></div>"), body) // 바탕 글꼴(함초롬바탕)이 명조라 부분 전체가 `div.serif` 다
        }
    }

    @Test
    fun 글꼴은_이름이_아니라_명조와_고딕으로만_옮긴다() {
        // HWP 는 한컴 글꼴 이름을 적고 HWPX 는 명조만 옮겨 같은 문서의 서체가 포맷마다 달랐다(13단계 짝 대조).
        val file = HwpFile().apply {
            docInfo.charShapes.add(CS(face = 1)) // 1 — HY헤드라인M(고딕 계열)
            section(P().text("바탕 명조 ").text("고딕 제목", 1))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue(body.startsWith("<div class=\"serif\">"), body)
            assertTrue("<span style=\"font-family:sans-serif\">고딕 제목</span>" in body, body)
            assertFalse("함초롬" in body || "HY헤드라인" in body, body) // 기기에 없는 이름은 적지 않는다
        }
    }

    @Test
    fun 흰_글자는_자기_음영이_어두우면_그대로_쓴다() {
        // 칸의 면 색만 보면 남색 음영 위의 흰 글자가 지워져 검게 사라졌다(검토가 잡았다). 음영은 COLORREF(0x00bbggrr)다.
        val file = HwpFile().apply {
            docInfo.charShapes.add(CS(color = 0xFFFFFF, shade = 0x00623D1CL)) // 1 — 흰 글자, 남색 음영
            section(P().text("앞 ").text("음영 위 흰 글", 1))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue(Regex("color:#ffffff[^\"]*background-color:#1c3d62|background-color:#1c3d62[^\"]*color:#ffffff").containsMatchIn(body), body)
        }
    }

    @Test
    fun 어두운_칸_안의_흰_칸은_흰색을_적고_표_뒤의_빈_줄은_남는다() {
        val file = HwpFile().apply {
            docInfo.borderFills.add(0x1C3D62) // 1 — 남색
            docInfo.borderFills.add(0xFFFFFF) // 2 — 흰색
            val inner = Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().text("안쪽 흰 칸")), borderFill = 2)))
            val outer = Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().ctrl(11, "tbl ", inner)), borderFill = 1)))
            // 빈 문단 넷이 든 칸 뒤의 빈 문단 둘 — 칸 안의 빈 줄이 표 밖까지 세어지면 둘이 사라졌다(검토가 잡았다).
            val empties = Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().text("칸"), P(), P(), P(), P()))))
            section(P().ctrl(11, "tbl ", outer), P().ctrl(11, "tbl ", empties), P(), P(), P().text("뒤"))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("background-color:#ffffff" in body, body) // 남색 안의 흰 칸
            val after = body.substringAfterLast("</table>")
            assertEquals(2, Regex("<p[^>]*><br/></p>").findAll(after).count(), body)
        }
    }

    @Test
    fun 네_변이_없는_칸은_테두리를_그리지_않는다() {
        // 보도자료 머리의 '보도시점·배포' 줄은 선이 없는 칸이다. HWP 는 테두리 종류를 읽지 않아 모든 칸에 선을 그었다 —
        // HWPX 는 `td.nb` 로 지웠다(13단계 짝 대조).
        val file = HwpFile().apply {
            docInfo.borderFills.add(-1) // 1 — 채우기 없음, 네 변 없음
            docInfo.borderFills.add(-1) // 2 — 채우기 없음, 네 변 실선
            docInfo.borderlessFills.add(1)
            section(P().ctrl(11, "tbl ", Ctrl.table(1, 2, listOf(
                Ctrl.Cell(0, 0, listOf(P().text("선 없음")), borderFill = 1),
                Ctrl.Cell(0, 1, listOf(P().text("선 있음")), borderFill = 2),
            ))))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue(Regex("<td class=\"nb\"[^>]*><p[^>]*>선 없음").containsMatchIn(body), body)
            assertTrue(Regex("<td(?! class)[^>]*><p[^>]*>선 있음").containsMatchIn(body), body)
        }
    }

    @Test
    fun 흰_글자는_바탕을_모르는_곳에서_적지_않는다() {
        val file = HwpFile().apply {
            docInfo.charShapes.add(CS(color = 0xFFFFFF)) // 1
            docInfo.borderFills.add(0x0080C0) // 1 — 짙은 파랑
            section(
                P().text("흰 글", 1),
                P().ctrl(11, "tbl ", Ctrl.table(1, 1, listOf(Ctrl.Cell(0, 0, listOf(P().text("칸의 흰 글", 1)), borderFill = 1)))),
            )
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            // 본문(흰 바탕)에서는 색을 버린다 — 흰 화면에 흰 글이 사라지지 않게.
            assertFalse(Regex("<p[^>]*color:#ffffff[^>]*>흰 글").containsMatchIn(body), body)
            // 짙은 칸 안에서는 문서의 색 그대로.
            assertTrue(Regex("background-color:#0080c0[^>]*><p[^>]*color:#ffffff[^>]*>칸의 흰 글").containsMatchIn(body), body)
        }
    }

    @Test
    fun 문단_모양이_정렬_들여쓰기_간격이_된다() {
        val file = HwpFile().apply {
            docInfo.paraShapes.addAll(
                listOf(
                    PS(align = 3), // 1 가운데
                    PS(left = 2000, indent = -2620), // 2 내어쓰기 13.1pt(두 배로 적힌 값)
                    PS(indent = 2000, before = 1200, after = 600, lineSpacing = 200), // 3
                    PS(align = 0), // 4 양쪽 — 적지 않는다
                    PS(left = -600, right = -400), // 5 쪽 여백 안으로 내민 문단 — 흐름에서는 0 이라 적지 않는다(HWPX 와 같다)
                )
            )
            section(
                P(paraShape = 1).text("가운데"), P(paraShape = 2).text("내어"), P(paraShape = 3).text("들여"), P(paraShape = 4).text("양쪽"),
                P(paraShape = 5).text("내민"),
            )
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<p style=\"text-align:center\">가운데</p>" in body, body)
            // 한글의 내어쓰기: 첫 줄이 왼쪽 여백(10pt)에서, 나머지 줄이 13.1pt 더 들어간다.
            assertTrue("<p style=\"margin-left:23.1pt;text-indent:-13.1pt\">내어</p>" in body, body)
            assertTrue("<p style=\"text-indent:10pt;margin-top:6pt;margin-bottom:3pt;line-height:2\">들여</p>" in body, body)
            assertTrue("<p>양쪽</p>" in body, body)
            assertTrue("<p>내민</p>" in body, body)
        }
    }

    @Test
    fun 줄_간격은_단위_없는_수로_적는다() {
        // 백분율(`130%`)은 문단에서 길이로 풀린 뒤 자식에게 그 길이로 물려진다 — 문단보다 큰 글자가 좁은 줄에 눌린다(13단계
        // 짝 대조: K19 의 14pt 글이 19.2pt 줄에). 단위 없는 수는 자식마다 자기 글자 크기에 곱한다. HWPX 와 같은 모양이다.
        val file = HwpFile().apply {
            docInfo.charShapes.add(CS(size = 1400)) // 1
            docInfo.paraShapes.addAll(listOf(PS(lineSpacing = 130), PS(lineSpacing = 160), PS(lineSpacing = 175))) // 1 2 3
            section(P(paraShape = 1).text("작은 글 ").text("큰 글", 1), P(paraShape = 2).text("기본"), P(paraShape = 3).text("넓게"))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<p style=\"line-height:1.3\">작은 글 <span style=\"font-size:140%\">큰 글</span></p>" in body, body)
            assertTrue("<p>기본</p>" in body, body) // 160% 는 바탕 스타일의 1.6 이다
            assertTrue("<p style=\"line-height:1.75\">넓게</p>" in body, body)
            assertFalse(Regex("line-height:[0-9.]+%").containsMatchIn(doc.partHtml(0)!!), doc.partHtml(0))
        }
    }

    @Test
    fun 바탕_스타일은_HWPX_와_한_벌이다() {
        // 13단계 짝 대조가 찾은 어긋남 — 칸의 기본 세로 정렬(HWP 위), 제목의 줄 간격(HWP 140%), 캡션 크기(HWP .92em),
        // 칸 끝 문단의 여백 규칙(HWP 만). 이제 둘이 `HancomCss.FLOW` 하나를 쓴다.
        Hwp5.open(HwpFile().section(P().text("글"))).use { doc ->
            val html = doc.partHtml(0)!!
            assertTrue("vertical-align:middle" in html, html)
            assertTrue("line-height:1.6" in html, html)
            assertFalse("td>:last-child" in html || "140%" in html || ".92em" in html, html)
        }
    }

    @Test
    fun 개요_문단은_제목이_되고_목차에_오른다() {
        val file = HwpFile().apply {
            docInfo.charShapes.add(CS(size = 1600, bold = true)) // 1
            docInfo.numberings.add(listOf(0 to "^1.", 8 to "^2)"))
            docInfo.paraShapes.addAll(
                listOf(
                    PS(headType = 1, headLevel = 0), // 1 개요 1
                    PS(headType = 1, headLevel = 1), // 2 개요 2
                )
            )
            section(
                P().ctrl(2, "secd", Ctrl.section(outlineNumbering = 1)),
                P(paraShape = 1).text("개요", 1),
                P(paraShape = 2).text("세부 하나"),
                P(paraShape = 2).text("세부 둘"),
                P(paraShape = 1).text("다음 장", 1),
                P(paraShape = 2).text("다시 세부"),
            )
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue(Regex("<h1 id=\"p-1\" style=\"font-size:160%;font-weight:bold\"><span class=\"mk\">1\\.</span>개요</h1>").containsMatchIn(body), body)
            assertTrue("<span class=\"mk\">가)</span>세부 하나</h2>" in body, body)
            assertTrue("<span class=\"mk\">나)</span>세부 둘</h2>" in body, body)
            assertTrue("<span class=\"mk\">2.</span>다음 장</h1>" in body, body)
            // 위 수준을 세면 아래 수준은 처음부터.
            assertTrue("<span class=\"mk\">가)</span>다시 세부</h2>" in body, body)
            assertEquals(listOf("1. 개요" to 0, "가) 세부 하나" to 1, "나) 세부 둘" to 1, "2. 다음 장" to 0, "가) 다시 세부" to 1), doc.outline.map { it.title to it.depth })
            assertEquals("p-1", doc.outline[0].anchor)
        }
    }

    @Test
    fun 제목은_머리_모양으로만_가리고_스타일_이름은_보지_않는다() {
        // HWPX 는 `headingType="OUTLINE"` 만 본다. HWP 만 스타일 이름 `개요 N` 도 제목으로 보아 같은 문서의 목차가 갈릴 수
        // 있었다(13단계 짝 대조). 명세 S01·S02 의 `개요 N` 문단은 전부 머리 모양도 개요라 실물의 목차는 그대로다.
        val file = HwpFile().apply {
            docInfo.styles.add("개요 3" to 0)
            docInfo.paraShapes.add(PS(headType = 1, headLevel = 2)) // 1 개요 3
            section(P(style = 1).text("스타일만 개요"), P(paraShape = 1, style = 1).text("머리 모양이 개요"), P().text("본문"))
        }
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("<p>스타일만 개요</p>" in body, body)
            assertTrue("<h3 id=\"p-1\"" in body, body)
            assertEquals(listOf("머리 모양이 개요" to 2), doc.outline.map { it.title to it.depth })
        }
    }

    @Test
    fun 번호_문단과_글머리표() {
        val file = HwpFile().apply {
            docInfo.numberings.add(listOf(1 to "^1", 0 to "(^2)"))
            docInfo.bullets.add('\uF0A7')
            docInfo.bullets.add('○')
            docInfo.paraShapes.addAll(
                listOf(
                    PS(headType = 2, headLevel = 0, numbering = 1), // 1
                    PS(headType = 2, headLevel = 1, numbering = 1), // 2
                    PS(headType = 3, numbering = 1), // 3 사설 영역 글머리표
                    PS(headType = 3, numbering = 2), // 4
                )
            )
            section(P(1).text("하나"), P(2).text("가"), P(1).text("둘"), P(3).text("점"), P(4).text("동그라미"))
        }
        Hwp5.open(file).use { doc ->
            val marks = Regex("<span class=\"mk\">(.*?)</span>").findAll(doc.body()).map { it.groupValues[1] }.toList()
            assertEquals(listOf("①", "(1)", "②", "▪", "○"), marks)
            // 번호 문단은 제목이 아니다.
            assertTrue(doc.outline.isEmpty())
        }
    }

    @Test
    fun 번호는_HWPX_와_같은_표로_센다() {
        // 13단계 짝 대조로 갈렸던 셋 — 동그라미 숫자 21~50(HWP 는 `21`), 26 을 넘는 영문자(HWP 는 `ab`), 시작 번호 0(HWPX 는 `1`).
        // 이제 두 변환기가 `HancomNumbers` 하나를 쓴다. 모양 17 이상(표에 없는 번호)은 숫자다.
        val file = HwpFile().apply {
            docInfo.numberings.add(listOf(1 to "^1")) // 1 동그라미 숫자
            docInfo.numberings.add(listOf(5 to "^1)")) // 2 영문 소문자
            docInfo.numberings.add(listOf(0 to "^1.", 31 to "^1.^2")) // 3 시작 번호 0, 모르는 모양
            docInfo.numberingStarts[2] = intArrayOf(0, 1, 1, 1, 1, 1, 1)
            docInfo.paraShapes.addAll(
                listOf(
                    PS(headType = 2, headLevel = 0, numbering = 1), // 1
                    PS(headType = 2, headLevel = 0, numbering = 2), // 2
                    PS(headType = 2, headLevel = 0, numbering = 3), // 3
                    PS(headType = 2, headLevel = 1, numbering = 3), // 4
                )
            )
            val paras = ArrayList<P>()
            repeat(51) { paras.add(P(1).text("동그라미 $it")) }
            repeat(28) { paras.add(P(2).text("영문 $it")) }
            paras.add(P(3).text("영"))
            paras.add(P(4).text("영의 하나"))
            section(*paras.toTypedArray())
        }
        Hwp5.open(file).use { doc ->
            val marks = Regex("<span class=\"mk\">(.*?)</span>").findAll(doc.body()).map { it.groupValues[1] }.toList()
            assertEquals(listOf("⑳", "㉑", "㉟", "㊱", "㊿", "51"), listOf(19, 20, 34, 35, 49, 50).map { marks[it] })
            assertEquals(listOf("z)", "aa)", "bb)"), marks.subList(51 + 25, 51 + 28))
            assertEquals(listOf("0.", "0.1"), marks.takeLast(2))
        }
    }

    @Test
    fun 각주와_미주는_표지를_달고_끝에_모인다() {
        val file = HwpFile().section(
            P().ctrl(2, "secd", Ctrl.section(end = Ctrl.noteShape(shape = 3, prefix = null, suffix = '.')))
                .text("본문").ctrl(17, "fn  ", Ctrl.note(false, listOf(P().ctrl(18, "atno", Ctrl.autoNumber(1, 1)).text(" 첫 각주"))))
                .text(" 이어서").ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("둘째 각주"))))
                .ctrl(17, "en  ", Ctrl.note(true, listOf(P().text("미주 내용")))),
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("본문<sup class=\"fnref\" id=\"fnref-1\"><a href=\"#fn-1\">1)</a></sup> 이어서<sup class=\"fnref\" id=\"fnref-2\"><a href=\"#fn-2\">2)</a></sup>" in body, body)
            assertTrue("<sup class=\"fnref\" id=\"enref-1\"><a href=\"#en-1\">i.</a></sup>" in body, body)
            val notes = body.substringAfter("<section class=\"notes\">")
            assertTrue(notes.indexOf("id=\"fn-1\"") < notes.indexOf("id=\"fn-2\"") && notes.indexOf("id=\"fn-2\"") < notes.indexOf("id=\"en-1\""), notes)
            // 주석 본문의 자동 번호(atno)는 우리가 단 표지와 겹치므로 한 번만 보인다.
            assertTrue("<div id=\"fn-1\" class=\"note\"><p><sup class=\"fnnum\"><a href=\"#fnref-1\">1)</a></sup> 첫 각주</p></div>" in notes, notes)
            assertTrue("<sup class=\"fnnum\"><a href=\"#enref-1\">i.</a></sup>미주 내용" in notes, notes)
            assertEquals(1, notes.occurrences(">1)<"), notes)
        }
    }

    @Test
    fun 구역마다_새로_세는_각주() {
        val restart = Ctrl.noteShape(numbering = 1)
        val file = HwpFile().apply {
            section(P().ctrl(2, "secd", Ctrl.section(foot = restart)).text("가").ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("a")))).ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("b")))))
            section(P().ctrl(2, "secd", Ctrl.section(foot = restart)).text("나").ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("c")))))
            docInfo.sectionCount = 2
        }
        Hwp5.open(file).use { doc ->
            assertEquals(2, doc.parts.size) // 구역은 언제나 부분의 경계
            assertTrue(">2)</a>" in doc.body(0))
            assertTrue(">1)</a>" in doc.body(1) && ">2)</a>" !in doc.body(1), doc.body(1))
        }
    }

    @Test
    fun 각주_모양의_사용자_기호를_쓴다() {
        // 13단계 짝 대조: HWP 변환기는 사용자 기호(모양 0x81)를 읽지 않고 16 을 넘는 모양을 숫자로 바꿔, `*` 로 적은 각주(K26)가
        // HWP 로만 숫자였다. 사용자 기호는 알맹이 자리 4 의 WCHAR 이다(명세 표 133). 0x80(네 글자 되풀이)은 어떤 글자인지
        // 몰라 숫자로 쓴다(`HancomNumbers`).
        val file = HwpFile().section(
            P().ctrl(2, "secd", Ctrl.section(foot = Ctrl.noteShape(shape = 0x81, userChar = '*', suffix = null), end = Ctrl.noteShape(shape = 0x80)))
                .text("본문").ctrl(17, "fn  ", Ctrl.note(false, listOf(P().text("별표 각주"))))
                .ctrl(17, "en  ", Ctrl.note(true, listOf(P().text("미주")))),
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("본문<sup class=\"fnref\" id=\"fnref-1\"><a href=\"#fn-1\">*</a></sup>" in body, body)
            assertTrue("<a href=\"#en-1\">1)</a>" in body, body)
            assertTrue("<a href=\"#fnref-1\">*</a></sup>별표 각주" in body, body)
        }
    }

    @Test
    fun 자동_번호의_모양과_사용자_기호도_같은_표로() {
        // 그림·표 번호(atno 종류 3·4)는 적힌 번호를 쓴다. 모양은 각주와 같은 표이고 사용자 기호는 알맹이 자리 10 이다(표 142).
        val file = HwpFile().section(
            P().text("표 ").ctrl(18, "atno", Ctrl.autoNumber(kind = 4, number = 3, shape = 0x81, userChar = '※', suffix = ')'))
                .text(" 그림 ").ctrl(18, "atno", Ctrl.autoNumber(kind = 3, number = 21, shape = 1)),
        )
        Hwp5.open(file).use { doc -> assertTrue("표 ※) 그림 ㉑" in doc.body(), doc.body()) }
    }

    @Test
    fun 새_번호_지정은_다음_주석의_번호를_바꾼다() {
        // 13단계 짝 대조: HWP 는 새 번호 지정(`nwno`, 표 144)을 버려 각주 번호가 1부터 이어졌고, HWPX 는 한글이 저장한 번호
        // (`@number`)로 새 번호를 보였다. 종류 1·2(각주·미주)만 셈을 옮긴다 — 표 번호(4)는 각주와 관계없다.
        fun fn(s: String) = Ctrl.note(false, listOf(P().text(s)))
        val file = HwpFile().section(
            P().ctrl(2, "secd", Ctrl.section(end = Ctrl.noteShape(start = 3)))
                .text("가").ctrl(17, "fn  ", fn("a")).ctrl(17, "fn  ", fn("b"))
                .ctrl(21, "nwno", Ctrl.newNumber(1, 7)).ctrl(17, "fn  ", fn("c")).ctrl(17, "fn  ", fn("d"))
                .ctrl(21, "nwno", Ctrl.newNumber(1, 1)).ctrl(17, "fn  ", fn("e"))
                .ctrl(21, "nwno", Ctrl.newNumber(2, 10)).ctrl(17, "en  ", Ctrl.note(true, listOf(P().text("f"))))
                .ctrl(21, "nwno", Ctrl.newNumber(4, 99)).ctrl(17, "fn  ", fn("g")),
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            val labels = Regex("<sup class=\"fnref\"[^>]*><a[^>]*>(.*?)</a>").findAll(body).map { it.groupValues[1] }.toList()
            // 미주는 시작 번호가 3 이어도 새 번호 10 을 보인다.
            assertEquals(listOf("1)", "2)", "7)", "8)", "1)", "10)", "2)"), labels)
            // 번호가 되돌아가도(`1)` 이 둘) 링크의 id 는 겹치지 않는다 — 뒤의 표지가 앞의 본문으로 가지 않게.
            val ids = Regex(" id=\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
            assertEquals(ids.size, ids.toSet().size, "$ids")
            val notes = body.substringAfter("<section class=\"notes\">")
            assertTrue("<div id=\"fn-5\" class=\"note\"><p><sup class=\"fnnum\"><a href=\"#fnref-5\">1)</a></sup>e</p></div>" in notes, notes)
        }
    }

    @Test
    fun 하이퍼링크는_바깥이면_글자만_문서_안이면_링크() {
        val target = P().text("여기가 목적지").also { it.instanceId = 424242 }
        val file = HwpFile().section(
            P().text("바깥 ").ctrl(3, "%hlk", Ctrl.field("%hlk", "https\\://example.com/a;1;0;0;")).text("누르세요").fieldEnd().text(" 끝"),
            P().ctrl(3, "%hlk", Ctrl.field("%hlk", "?#424242;0;1;0;")).text("안쪽으로").fieldEnd(),
            target,
        )
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("바깥 <span class=\"ext\">누르세요</span> 끝" in body, body)
            assertFalse("example.com" in body)
            assertTrue("<a href=\"#p-2\">안쪽으로</a>" in body, body)
            assertTrue("<p id=\"p-2\">여기가 목적지</p>" in body, body)
        }
    }

    @Test
    fun 머리말은_세고_그리지_않는다() {
        val file = HwpFile().section(P().ctrl(16, "head", Ctrl.header()).text("본문"), P().ctrl(16, "head", Ctrl.header()).text("또"))
        Hwp5.open(file).use { doc ->
            assertFalse("머리말 글" in doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.HEADER_FOOTER])
        }
    }

    @Test
    fun 문서의_글은_이스케이프된다() {
        val file = HwpFile().section(P().text("<script>alert(1)</script> & \"q\""))
        Hwp5.open(file).use { doc ->
            val body = doc.body()
            assertTrue("&lt;script&gt;" in body && "<script" !in body, body)
            assertTrue("&amp;" in body)
        }
    }

    @Test
    fun 빈_문단은_셋까지만_남긴다() {
        val file = HwpFile().section(P().text("앞"), P(), P(), P(), P(), P(), P().text("뒤"))
        Hwp5.open(file).use { doc ->
            assertEquals(3, doc.body().occurrences("<p><br/></p>"), doc.body())
        }
    }

    @Test
    fun 글자_효과는_센다() {
        val file = HwpFile().apply {
            docInfo.charShapes.add(CS(effect = true))
            section(P().text("그림자", 1), P().text("또", 1))
        }
        Hwp5.open(file).use { doc ->
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.TEXT_EFFECT])
            // 다시 그려도 두 번 세지 않는다(세는 것은 훑기뿐).
            doc.partHtml(0)
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.TEXT_EFFECT])
            assertTrue(doc.warnings.none { it.code == FlowWarnings.PART_FAILED })
        }
    }
}
