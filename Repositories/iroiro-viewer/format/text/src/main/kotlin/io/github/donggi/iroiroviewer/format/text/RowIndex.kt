package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.safety.TextLimits

/**
 * 어디를 읽으면 몇 번째 행이 나오는가.
 *
 * ## 왜 '줄' 이 아니라 '행' 인가
 *
 * 사용자가 보는 한 칸은 파일의 한 줄이 아닐 수 있다. 줄바꿈 없는 480 KB 짜리 미니파이
 * JS 한 줄은 **여러 행**으로 나뉘어야 하고, 그렇지 않으면 그 한 줄을 그리다 화면이 멎는다.
 * 그래서 목록의 좌표는 **행**이고, 줄 번호는 그 행이 속한 **논리 줄**로 따로 든다.
 *
 * 이 구분을 처음부터 두 이름으로 못 박는 것이 중요하다. 하나로 뭉뚱그리면 '줄 번호로
 * 가기' 와 '스크롤 위치' 가 서로 다른 뜻의 같은 수를 쓰게 되고, 그 버그는 긴 줄이 있는
 * 파일에서만 나타나 재현이 어렵다.
 *
 * ## 왜 성기게 두는가
 *
 * 줄마다 바이트 오프셋을 들면 1 GB·1천만 줄 파일에서 **76 MB** 다. 앱 힙이 얼마든
 * 그것만으로 끝이다. 앵커를 64 KiB 또는 256행마다 하나씩만 두면 같은 파일이 **0.9 MB**
 * 이고, 대가는 '앵커에서 목표 행까지 다시 읽기' 인데 그 되읽기가 **64 KiB + 한 행**으로
 * 묶인다(두 조건을 함께 걸었기 때문이다).
 *
 * ## 앵커는 반드시 행 시작이다
 *
 * 임의의 바이트 오프셋을 앵커로 삼으면 안 된다. CP949·Shift_JIS 는 자기동기화가 아니라,
 * 문자 한가운데에서 읽기 시작하면 `U+FFFD` 하나 없이 **그럴듯한 다른 글자**가 나온다.
 * 행 시작은 (가) 줄 시작이거나 (나) 긴 줄을 자른 자리이고, 둘 다 색인이 앞에서부터
 * 훑어 만든 자리라 문자 경계가 보장된다.
 */
class RowIndex(
    /** 앵커의 바이트 오프셋. 오름차순. */
    private val offsets: LongArray,
    /** 그 앵커가 가리키는 행 번호(0부터). */
    private val rows: IntArray,
    /** 그 행이 속한 논리 줄 번호(0부터). */
    private val lines: IntArray,
    /** 앵커 개수. 배열은 더 클 수 있다(자라면서 두 배씩 늘린다). */
    private val count: Int,
    /** 전체 행 수. */
    val rowCount: Int,
    /** 전체 논리 줄 수. */
    val lineCount: Int,
    /** 파일 끝까지 다 훑었는가. 취소되었으면 false. */
    val complete: Boolean,
    /** 색인을 만든 시점의 파일 크기. 이것이 달라지면 색인이 무효다. */
    val sourceBytes: Long,
) {

    /** 이 행보다 앞이면서 가장 가까운 앵커. 없으면 null(파일 시작부터 읽는다). */
    fun anchorFor(row: Int): Anchor? {
        if (count == 0) return null
        var lo = 0
        var hi = count - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (rows[mid] <= row) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (found < 0) return null
        return Anchor(found, offsets[found], rows[found], lines[found])
    }

    /** 이 논리 줄의 첫 행. 없으면 -1. 줄 번호로 가기가 쓴다. */
    fun rowOfLine(line: Int): Int {
        if (count == 0) return -1
        var lo = 0
        var hi = count - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid] <= line) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        // 앵커가 성기므로 정확한 행은 읽어 봐야 안다. 여기서는 **그 앵커의 행**을 준다 —
        // 읽는 쪽이 거기서부터 앞으로 세어 정확히 맞춘다.
        return if (found < 0) -1 else rows[found]
    }

    val anchorCount: Int get() = count

    /** [i] 번째 앵커가 가리키는 행. 강조 상태표를 앵커에 맞춰 채울 때 쓴다. */
    fun anchorRowAt(i: Int): Int = rows[i]

    /** 메모리로 얼마나 쓰는가. 진단 화면이 보여 준다. */
    val memoryBytes: Long get() = count.toLong() * TextLimits.ANCHOR_BYTES_IN_MEMORY

    /**
     * [index] 는 앵커 배열에서의 자리다. **강조 상태표가 이 번호로 붙는다** —
     * 행 번호로 다시 찾으면 이분 탐색이 두 번이 되고, 두 탐색이 어긋나면 주석 색이
     * 엉뚱한 행부터 시작한다.
     */
    data class Anchor(val index: Int, val byteOffset: Long, val row: Int, val line: Int)

    companion object {
        /** 아직 아무것도 모르는 색인. 파일을 막 열었을 때. */
        fun empty(sourceBytes: Long): RowIndex =
            RowIndex(LongArray(0), IntArray(0), IntArray(0), 0, 0, 0, false, sourceBytes)
    }
}

/** 색인을 만드는 동안 자라는 앵커 모음. */
internal class AnchorBuilder {
    private var offsets = LongArray(1024)
    private var rows = IntArray(1024)
    private var lines = IntArray(1024)
    private var n = 0

    fun add(offset: Long, row: Int, line: Int) {
        if (n == offsets.size) grow()
        offsets[n] = offset
        rows[n] = row
        lines[n] = line
        n++
    }

    private fun grow() {
        val size = offsets.size * 2
        offsets = offsets.copyOf(size)
        rows = rows.copyOf(size)
        lines = lines.copyOf(size)
    }

    fun build(rowCount: Int, lineCount: Int, complete: Boolean, sourceBytes: Long): RowIndex =
        RowIndex(offsets, rows, lines, n, rowCount, lineCount, complete, sourceBytes)

    val size: Int get() = n
}
