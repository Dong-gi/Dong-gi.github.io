package io.github.donggi.iroiroviewer.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.donggi.iroiroviewer.format.text.LineEnd
import io.github.donggi.iroiroviewer.format.text.TextRow

/**
 * 줄 끝 표시에 쓰는 글자(14단계). 문구와 같이 `strings.xml` 에서 온다 — 화면이 [rememberLineEndMarks] 로 읽어 건넨다.
 *
 * 세 모양은 **움직임**을 그린다: LF 는 한 줄 내림(`↓`), CR 은 줄 머리로 돌아감(`←`), CRLF 는 둘을 이은 `⏎`.
 * 폭이 한 글자라 코드의 세로 정렬을 흩뜨리지 않는다. `⏎` 는 7단계의 인코딩 미리보기가 기기에서 이미 그렸다.
 */
data class LineEndMarks(val lf: String, val crlf: String, val cr: String) {
    fun of(end: LineEnd): String? = when (end) {
        LineEnd.NONE -> null
        LineEnd.LF -> lf
        LineEnd.CRLF -> crlf
        LineEnd.CR -> cr
    }
}

/**
 * 조각·찾은 자리·줄 끝 표시를 하나의 [AnnotatedString] 으로 합친다.
 *
 * **줄 끝 표시는 행의 글자 뒤에만 붙는다.** 찾기(`TextRow.text` 로 찾는다)와 강조의 좌표는 표시를 모른다 — 표시가
 * 글자 안에 들어가면 `찾을말⏎` 같은 줄이 찾기에 걸리지 않거나, 줄 끝을 켜는 순간 칠한 자리가 한 칸씩 밀린다.
 *
 * @param marks null 이면 줄 끝을 그리지 않는다.
 */
internal fun annotateRow(
    row: TextRow,
    colors: CodeColors,
    query: String,
    ignoreCase: Boolean,
    isCurrentHit: Boolean,
    marks: LineEndMarks?,
): AnnotatedString = buildAnnotatedString {
    append(row.text)
    for (s in row.spans) {
        // 조각이 행 길이를 넘는 일은 없어야 하지만, 넘으면 예외가 나므로 잘라 넣는다.
        val end = minOf(s.end, row.text.length)
        if (s.start >= end) continue
        addStyle(SpanStyle(color = colors.of(s.kind)), s.start, end)
    }
    if (query.isNotEmpty()) {
        var at = row.text.indexOf(query, 0, ignoreCase)
        while (at >= 0) {
            addStyle(
                SpanStyle(background = if (isCurrentHit) colors.currentHit else colors.hit),
                at,
                at + query.length,
            )
            at = row.text.indexOf(query, at + query.length, ignoreCase)
        }
    }
    val mark = marks?.of(row.lineEnd)
    if (mark != null) {
        val start = length
        append(mark)
        // 줄 번호와 같은 흐린 색. 본문이 아니라는 것이 한눈에 보여야 한다.
        addStyle(SpanStyle(color = colors.gutter), start, length)
    }
}

/**
 * 찾기 진행률(0~100). 행 수를 모르면 0.
 *
 * 화면은 **값이 바뀔 때만** 다시 그린다 — 512행마다 오는 알림을 그대로 흘리면 20만 행 파일에서 400번 그린다.
 */
internal fun searchPercent(scanned: Int, total: Int): Int {
    if (total <= 0) return 0
    val p = (scanned.toLong() * 100 / total).toInt()
    return p.coerceIn(0, 100)
}
