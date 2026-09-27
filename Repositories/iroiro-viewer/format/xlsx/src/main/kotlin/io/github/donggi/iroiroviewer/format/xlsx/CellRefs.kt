package io.github.donggi.iroiroviewer.format.xlsx

/** 셀 범위(`A1:C3`). 행·열은 1 부터. */
internal data class CellRange(val top: Int, val left: Int, val bottom: Int, val right: Int)

/**
 * `A1` 모양의 셀 주소. `$` 는 무시한다(절대 참조 표시일 뿐이다).
 *
 * 범위를 벗어나는 주소(`XFE1`, 1048577 행)는 **null** 이다 — 문서가 적는 값이라 믿지 않는다.
 */
internal object CellRefs {

    const val MAX_ROWS = 1_048_576
    const val MAX_COLUMNS = 16_384

    /** 열 글자(`A`·`XFD`) → 번호. 글자가 아니거나 범위 밖이면 -1. */
    fun columnIndex(s: CharSequence, from: Int = 0, to: Int = s.length): Int {
        if (from >= to || to - from > 3) return -1
        var n = 0
        for (i in from until to) {
            val c = s[i]
            val v = when (c) {
                in 'A'..'Z' -> c - 'A' + 1
                in 'a'..'z' -> c - 'a' + 1
                else -> return -1
            }
            n = n * 26 + v
        }
        return if (n in 1..MAX_COLUMNS) n else -1
    }

    /** 번호 → 열 글자. */
    fun columnName(col: Int): String {
        var n = col
        val out = CharArray(3)
        var i = 3
        while (n > 0 && i > 0) {
            val r = (n - 1) % 26
            out[--i] = 'A' + r
            n = (n - 1) / 26
        }
        return String(out, i, 3 - i)
    }

    /**
     * `B3` → (행, 열) 을 한 `Long` 에. 행이 없는 `B`, 열이 없는 `3` 은 null.
     * 꺼낼 때는 [rowOf]·[colOf].
     */
    fun parse(ref: String): Long? {
        var i = 0
        val n = ref.length
        if (i < n && ref[i] == '$') i++
        val colStart = i
        while (i < n && ref[i].isLetter()) i++
        val col = columnIndex(ref, colStart, i)
        if (col < 0) return null
        if (i < n && ref[i] == '$') i++
        val rowStart = i
        var row = 0
        while (i < n && ref[i] in '0'..'9') {
            row = row * 10 + (ref[i] - '0')
            if (row > MAX_ROWS) return null
            i++
        }
        if (i == rowStart || i != n || row < 1) return null
        return pack(row, col)
    }

    /** 열 글자만 읽는다(`B3` → 2). 셀의 `r` 에서 열만 필요할 때. 없으면 -1. */
    fun columnOf(ref: String): Int {
        var i = 0
        if (i < ref.length && ref[i] == '$') i++
        val start = i
        while (i < ref.length && ref[i].isLetter()) i++
        return columnIndex(ref, start, i)
    }

    /** `A1:C3` 또는 `A1`. 모서리 순서가 뒤집혀 있으면 바로잡는다. */
    fun parseRange(ref: String): CellRange? {
        val colon = ref.indexOf(':')
        val a = parse(if (colon < 0) ref.trim() else ref.substring(0, colon).trim()) ?: return null
        val b = if (colon < 0) a else parse(ref.substring(colon + 1).trim()) ?: return null
        return CellRange(
            top = minOf(rowOf(a), rowOf(b)),
            left = minOf(colOf(a), colOf(b)),
            bottom = maxOf(rowOf(a), rowOf(b)),
            right = maxOf(colOf(a), colOf(b)),
        )
    }

    fun pack(row: Int, col: Int): Long = (row.toLong() shl 20) or col.toLong()
    fun rowOf(packed: Long): Int = (packed shr 20).toInt()
    fun colOf(packed: Long): Int = (packed and 0xFFFFF).toInt()
}
