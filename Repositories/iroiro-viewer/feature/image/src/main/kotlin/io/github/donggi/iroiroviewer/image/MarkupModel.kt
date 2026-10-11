package io.github.donggi.iroiroviewer.image

/*
 * 그리기·메모의 **값** — 획과 글 메모, 그리고 되돌리기. 안드로이드를 모른다(JVM 시험 — `MarkupHistoryTest`).
 *
 * ## 그림 좌표
 *
 * 모든 자리·굵기·글자 크기는 **그림 좌표**로 든다 — 원점은 보이는 방향(EXIF 를 적용한 모양)의 왼쪽 위이고, 단위는 **그림의 폭**이다
 * (폭이 1, 높이는 높이/폭). 화면에서 몇 배로 확대해 그렸든, 저장할 때 원본을 얼마나 줄여 떴든 같은 값이 같은 자리·같은 비율의
 * 굵기를 뜻한다. 세로를 높이로 따로 나누지 않는 것은 거리와 굵기가 두 축에서 같은 단위여야 하기 때문이다 — 그래야 지우개의 맞힘과
 * 직선 붙이기의 각도가 화면에서 보는 그대로 셈해진다([MarkupMath]).
 */

/** 획의 종류. 글 메모·지우개는 획이 아니다(화면의 도구는 `MarkupTool`). */
internal enum class StrokeKind { PEN, HIGHLIGHTER, LINE }

/** 그린 것 하나 — 획이나 글 메모. [id] 는 한 그림 안에서 유일하다(지우기·고치기·되돌리기가 이것으로 찾는다). */
internal sealed interface Mark {
    val id: Long
}

/**
 * 한 획. [points] 는 x·y 가 번갈아 놓인 그림 좌표이고 **만든 뒤 바꾸지 않는다**(그리는 쪽이 경로를 캐시하고, 큐에 실린 요청이 같은
 * 배열을 든다). 직선은 두 점이다. 점 하나는 점을 찍은 것이다(펜·형광펜으로 한 번 두드림).
 *
 * @param color 불투명 ARGB. 형광펜의 반투명은 그리는 쪽이 입힌다 — 고른 빛깔과 저장된 빛깔이 같아야 다시 고를 때 헷갈리지 않는다.
 * @param width 굵기(그림 좌표).
 */
internal class Stroke(
    override val id: Long,
    val kind: StrokeKind,
    val color: Int,
    val width: Float,
    val points: FloatArray,
) : Mark {
    val pointCount: Int get() = points.size / 2

    override fun toString(): String = "Stroke(id=$id, $kind, ${pointCount}점)"
}

/**
 * 글 메모. ([x], [y]) 는 글 상자(바탕을 깔지 않아도 같은 여백)의 왼쪽 위, [size] 는 글자 크기(그림 좌표)다.
 *
 * @param boxed 읽기 쉽게 반투명 바탕 상자를 깐다. 상자의 빛깔은 글의 빛깔이 밝은가로 정한다([MarkupMath.isLight]).
 */
internal data class Note(
    override val id: Long,
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val size: Float,
    val boxed: Boolean,
) : Mark {
    // 메모의 글을 로그·예외에 싣지 않는다(사용자가 쓴 글이다). 데이터 클래스의 기본 `toString` 은 글을 그대로 찍는다.
    override fun toString(): String = "Note(id=$id, ${text.length}자)"
}

/**
 * 그린 것의 목록과 되돌리기·다시 하기. **불변**이다 — 바꾸는 함수마다 새 값을 돌려준다(화면의 상태 흐름에 그대로 싣는다).
 *
 * 되돌리기는 **한 일**을 적어 둔다 — 목록을 통째로 찍어 두지 않는다(획이 수백 개면 찍을 때마다 그만큼 쌓인다). 지우개로 여럿을
 * 지운 한 번, '모두 지우기' 한 번, 메모를 끌어 옮긴 한 번이 각각 한 걸음이다. 새로 무엇을 하면 다시 하기 줄은 비운다(관행).
 *
 * 찾기는 자리(인덱스)가 아니라 [Mark.id] 로 한다 — 되돌리는 사이 목록이 바뀌었어도 엉뚱한 것을 지우지 않는다. 지운 것을 되살릴
 * 때만 원래 자리를 쓴다(겹친 순서 — 위에 그린 것이 위 — 를 지키려는 것이다).
 */
internal class MarkupHistory private constructor(
    val marks: List<Mark>,
    private val done: List<Edit>,
    private val undone: List<Edit>,
) {
    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** 되돌릴 수 있는 걸음 수. 시험이 상한을 본다. */
    val undoDepth: Int get() = done.size

    fun add(mark: Mark): MarkupHistory = push(Edit.Add(mark))

    /** [ids] 에 든 것을 지운다. 하나도 없으면 그대로다(빈 걸음을 쌓지 않는다). */
    fun remove(ids: Set<Long>): MarkupHistory {
        val removed = marks.withIndex().filter { it.value.id in ids }.map { it.index to it.value }
        if (removed.isEmpty()) return this
        return push(Edit.Remove(removed))
    }

    /** 같은 [Mark.id] 의 것을 [after] 로 바꾼다(메모 고치기·옮기기). 없거나 같으면 그대로다. */
    fun replace(after: Mark): MarkupHistory {
        val before = marks.firstOrNull { it.id == after.id } ?: return this
        if (before == after) return this
        return push(Edit.Replace(before, after))
    }

    /** 모두 지운다 — 한 걸음이라 되돌리기 한 번으로 모두 돌아온다. */
    fun clear(): MarkupHistory = if (marks.isEmpty()) this else push(Edit.Clear(marks))

    fun undo(): MarkupHistory {
        val edit = done.lastOrNull() ?: return this
        return MarkupHistory(edit.revert(marks), done.dropLast(1), undone + edit)
    }

    fun redo(): MarkupHistory {
        val edit = undone.lastOrNull() ?: return this
        return MarkupHistory(edit.apply(marks), done + edit, undone.dropLast(1))
    }

    private fun push(edit: Edit): MarkupHistory = MarkupHistory(edit.apply(marks), (done + edit).takeLast(MAX_STEPS), emptyList())

    /** 한 걸음 — 앞으로 하는 법과 되돌리는 법. */
    private sealed interface Edit {
        fun apply(marks: List<Mark>): List<Mark>
        fun revert(marks: List<Mark>): List<Mark>

        class Add(val mark: Mark) : Edit {
            override fun apply(marks: List<Mark>) = marks + mark
            override fun revert(marks: List<Mark>) = marks.filter { it.id != mark.id }
        }

        /** [removed] 는 (지우기 전의 자리, 지운 것) — 자리가 오름차순이다. */
        class Remove(val removed: List<Pair<Int, Mark>>) : Edit {
            private val ids = removed.mapTo(HashSet()) { it.second.id }

            override fun apply(marks: List<Mark>) = marks.filter { it.id !in ids }

            // 오름차순으로 제자리에 끼우면 지우기 전의 차례가 그대로 돌아온다(앞의 것을 끼울 때마다 뒤의 자리가 맞아진다).
            override fun revert(marks: List<Mark>): List<Mark> {
                val out = marks.toMutableList()
                for ((index, mark) in removed) out.add(index.coerceAtMost(out.size), mark)
                return out
            }
        }

        class Replace(val before: Mark, val after: Mark) : Edit {
            override fun apply(marks: List<Mark>) = marks.map { if (it.id == after.id) after else it }
            override fun revert(marks: List<Mark>) = marks.map { if (it.id == before.id) before else it }
        }

        class Clear(val before: List<Mark>) : Edit {
            override fun apply(marks: List<Mark>) = emptyList<Mark>()
            override fun revert(marks: List<Mark>) = before
        }
    }

    companion object {
        val EMPTY = MarkupHistory(emptyList(), emptyList(), emptyList())

        /** 되살린 목록(초안)으로 시작한다 — 되돌리기 기록은 없다. */
        fun of(marks: List<Mark>) = MarkupHistory(marks, emptyList(), emptyList())

        /** 되돌릴 수 있는 걸음의 상한. 넘으면 가장 오래된 걸음부터 잊는다. */
        const val MAX_STEPS = 200
    }
}
