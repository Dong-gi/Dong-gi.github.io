package io.github.donggi.iroiroviewer.image

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import kotlinx.coroutines.CancellationException
import kotlin.math.hypot

/*
 * 그리는 화면의 **손가락** — 몸짓을 가르고(한 손가락은 도구, 두 손가락은 확대·이동) 도구마다 할 일을 한다. 화면은 `MarkupScreen`,
 * 셈은 `MarkupMath`.
 */

/**
 * 손가락 — **두 번째 손가락이 닿으면 확대·이동으로 바뀌고 그 몸짓이 끝날 때까지 그대로다**(손가락 하나를 떼도 그리지 않는다). 한
 * 손가락의 일은 [MarkupInput] 이 도구마다 한다. 시스템이 몸짓을 거두면(가장자리 뒤로 가기 등) 그리던 것을 버린다 — 반쯤 그은 선이
 * 남지 않게.
 */
internal suspend fun PointerInputScope.markupGestures(input: MarkupInput) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        input.begin(down.position.x, down.position.y)
        var transforming = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (!transforming && pressed >= 2) {
                    input.cancel()
                    transforming = true
                }
                if (transforming) {
                    if (pressed == 0) break
                    val centroid = event.calculateCentroid(useCurrent = true)
                    if (centroid.isSpecified) input.transform(event.calculateZoom(), event.calculatePan(), centroid)
                    event.changes.forEach { it.consume() }
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    // 시스템이 거둔 몸짓은 이미 소비된 '뗌' 으로 온다(Compose 가 지어 보낸다) — 그때는 버린다.
                    if (change == null || change.isConsumed) input.cancel() else input.end()
                    break
                }
                for (h in change.historical) input.move(h.position.x, h.position.y)
                input.move(change.position.x, change.position.y)
                change.consume()
            }
        } catch (e: CancellationException) {
            input.cancel()
            throw e
        }
    }
}

/** 화면에서 재는 크기들(화소). 화면의 밀도와 글꼴 배율로 정한다 — 글자 크기는 sp 라 글꼴을 키운 사람의 메모는 크게 시작한다. */
internal class InputMetrics(
    val penWidths: FloatArray,
    val highlighterWidths: FloatArray,
    val lineWidths: FloatArray,
    val textSizes: FloatArray,
    val eraserRadius: Float,
    val minStep: Float,
    val slop: Float,
    val minLine: Float,
    val noteReach: Float,
) {
    fun widthsOf(tool: MarkupTool): FloatArray = when (tool) {
        MarkupTool.HIGHLIGHTER -> highlighterWidths
        MarkupTool.LINE -> lineWidths
        else -> penWidths
    }

    companion object {
        fun of(density: Density): InputMetrics = with(density) {
            InputMetrics(
                penWidths = floatArrayOf(2.dp.toPx(), 4.dp.toPx(), 8.dp.toPx()),
                highlighterWidths = floatArrayOf(12.dp.toPx(), 20.dp.toPx(), 32.dp.toPx()),
                lineWidths = floatArrayOf(2.dp.toPx(), 4.dp.toPx(), 8.dp.toPx()),
                textSizes = floatArrayOf(16.sp.toPx(), 24.sp.toPx(), 36.sp.toPx()),
                eraserRadius = 14.dp.toPx(),
                minStep = 1.5.dp.toPx(),
                slop = 8.dp.toPx(),
                minLine = 8.dp.toPx(),
                noteReach = 8.dp.toPx(),
            )
        }
    }
}

/** 긋는 중인 획 — 점 버퍼가 자란다. 점이 늘 때마다 [version] 이 올라 손가락의 층만 다시 그린다. */
internal class LiveStroke {
    var kind = StrokeKind.PEN
        private set
    var color = 0
        private set
    var width = 0f
        private set
    var points = FloatArray(INITIAL_POINTS * 2)
        private set
    var count = 0
        private set

    /** 놓아서 ViewModel 로 보낸 획의 신원 — 그린 것에 들어온 것이 보이면 걷는다([MarkupInput.settle]). */
    var committedId: Long? = null

    var version by mutableIntStateOf(0)
        private set

    val lastX: Float get() = points[2 * count - 2]
    val lastY: Float get() = points[2 * count - 1]

    fun start(kind: StrokeKind, color: Int, width: Float, x: Float, y: Float) {
        this.kind = kind
        this.color = color
        this.width = width
        count = 0
        committedId = null
        append(x, y)
    }

    /** 점을 더한다. 상한([MarkupDraft.MAX_STROKE_POINTS])에 닿으면 더 늘이지 않는다(획이 그 자리에서 멈춘다). */
    fun append(x: Float, y: Float) {
        if (count >= MarkupDraft.MAX_STROKE_POINTS) return
        if (2 * count + 2 > points.size) points = points.copyOf(minOf(points.size * 2, MarkupDraft.MAX_STROKE_POINTS * 2))
        points[2 * count] = x
        points[2 * count + 1] = y
        count++
        version++
    }

    /** 마지막 점을 옮긴다 — 직선의 끝. */
    fun moveLast(x: Float, y: Float) {
        if (count == 0) return
        points[2 * count - 2] = x
        points[2 * count - 1] = y
        version++
    }

    fun toStroke(id: Long): Stroke = Stroke(id, kind, color, width, points.copyOf(count * 2))

    fun clear() {
        count = 0
        committedId = null
        version++
    }

    private companion object {
        const val INITIAL_POINTS = 256
    }
}

/** 끌어 옮기는 메모의 지금 자리(그림 좌표). [committed] 면 놓아서 ViewModel 로 보냈다 — 그린 것에 들어오면 걷는다. */
internal data class NoteMove(val id: Long, val x: Float, val y: Float, val committed: Boolean = false)

/**
 * 한 손가락이 도구마다 하는 일. 화면이 구성마다 지금 값([style]·[marks]·[aspect]·[metrics])을 적어 두고, 손가락이 닿은 순간의 도구로
 * 한 몸짓을 끝까지 한다(도중에 도구를 바꿔도 그 몸짓은 그대로다). 그린 것은 놓는 순간 ViewModel 로 간다.
 */
internal class MarkupInput(
    private val vm: MarkupViewModel,
    private val painter: MarkupPainter,
    private val zoom: ZoomState,
    private val live: LiveStroke,
) {
    var style = MarkupStyle()
    var marks: List<Mark> = emptyList()
    var aspect = 1f
    var metrics: InputMetrics = InputMetrics.of(Density(1f))

    /** 지우개가 지나간 것 — 놓기 전까지 감춰만 둔다(놓으면 한 걸음으로 지운다). */
    var hidden by mutableStateOf<Set<Long>>(emptySet())

    /** 끌어 옮기는 메모. */
    var moving by mutableStateOf<NoteMove?>(null)

    /** 지우개가 닿은 자리(화면 좌표) — 동그라미를 보인다. */
    var eraserAt by mutableStateOf<Offset?>(null)

    private var active = false
    private var tool = MarkupTool.PEN
    private var view: MarkupView? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var traveled = false
    private var minStep = 0f
    private var grabbed: Note? = null

    /** 지금의 변환. 아직 재지 않았으면 null. */
    fun view(): MarkupView? = MarkupView(
        viewportWidth = zoom.viewport.width,
        viewportHeight = zoom.viewport.height,
        fittedWidth = zoom.fitted.width,
        fittedHeight = zoom.fitted.height,
        scale = zoom.scale,
        offsetX = zoom.offset.x,
        offsetY = zoom.offset.y,
    ).takeIf { it.valid }

    fun begin(sx: Float, sy: Float) {
        val v = view() ?: return
        active = true
        view = v
        tool = style.tool
        downX = sx
        downY = sy
        traveled = false
        val x = v.imageX(sx)
        val y = v.imageY(sy)
        startX = x
        startY = y
        lastX = x
        lastY = y
        when (tool) {
            MarkupTool.PEN, MarkupTool.HIGHLIGHTER, MarkupTool.LINE -> {
                minStep = v.toImage(metrics.minStep)
                live.start(kindOf(tool), style.colorOf(tool), v.toImage(widthOf(tool)), x, y)
                // 직선은 처음부터 두 점이다 — 끝을 옮긴다.
                if (tool == MarkupTool.LINE) live.append(x, y)
            }
            MarkupTool.TEXT -> grabbed = noteAt(x, y, v.toImage(metrics.noteReach))
            MarkupTool.ERASER -> {
                eraserAt = Offset(sx, sy)
                eraseAlong(x, y, x, y, v)
            }
        }
    }

    fun move(sx: Float, sy: Float) {
        if (!active) return
        val v = view ?: return
        if (!traveled && hypot(sx - downX, sy - downY) > metrics.slop) traveled = true
        val x = v.imageX(sx)
        val y = v.imageY(sy)
        when (tool) {
            MarkupTool.PEN, MarkupTool.HIGHLIGHTER ->
                if (MarkupMath.farEnough(live.lastX, live.lastY, x, y, minStep)) live.append(x, y)
            MarkupTool.LINE -> {
                val end = MarkupMath.snapLine(startX, startY, x, y)
                live.moveLast(end[0], end[1])
            }
            MarkupTool.TEXT -> {
                val n = grabbed
                if (n != null && traveled) {
                    val size = painter.noteSize(n)
                    val at = MarkupMath.clampBox(n.x + (x - startX), n.y + (y - startY), size[0], size[1], aspect)
                    moving = NoteMove(n.id, at[0], at[1])
                }
            }
            MarkupTool.ERASER -> {
                eraserAt = Offset(sx, sy)
                eraseAlong(lastX, lastY, x, y, v)
            }
        }
        lastX = x
        lastY = y
    }

    fun end() {
        if (!active) return
        active = false
        val v = view ?: return
        when (tool) {
            MarkupTool.PEN, MarkupTool.HIGHLIGHTER -> commitLive()
            MarkupTool.LINE -> {
                val length = hypot(live.lastX - startX, live.lastY - startY)
                // 두드림·떨림은 선이 아니다.
                if (v.toScreen(length) >= metrics.minLine) commitLive() else live.clear()
            }
            MarkupTool.TEXT -> {
                val n = grabbed
                val m = moving
                when {
                    n != null && m != null && traveled -> {
                        if (m.x == n.x && m.y == n.y) {
                            moving = null
                        } else {
                            moving = m.copy(committed = true)
                            vm.replace(n.copy(x = m.x, y = m.y))
                        }
                    }
                    traveled -> Unit // 빈 자리를 끌었다 — 할 일이 없다.
                    n != null -> vm.editNote(n)
                    // 그림 위를 두드렸을 때만 새 메모다 — 둘레의 검은 자리에 단 메모는 저장되지 않는다.
                    MarkupMath.inside(startX, startY, 0f, 0f, 1f, aspect) -> vm.startNote(startX, startY)
                }
                grabbed = null
            }
            MarkupTool.ERASER -> {
                eraserAt = null
                val ids = hidden
                if (ids.isNotEmpty()) vm.remove(ids)
            }
        }
    }

    fun cancel() {
        if (!active) return
        active = false
        when (tool) {
            MarkupTool.PEN, MarkupTool.HIGHLIGHTER, MarkupTool.LINE -> live.clear()
            MarkupTool.TEXT -> {
                moving = null
                grabbed = null
            }
            MarkupTool.ERASER -> {
                eraserAt = null
                hidden = emptySet()
            }
        }
    }

    /** 두 손가락 — 뷰어와 같은 확대·이동([ZoomState.transform], 중심은 뷰포트 가운데가 원점). */
    fun transform(zoomChange: Float, pan: Offset, centroid: Offset) {
        val pivot = Offset(centroid.x - zoom.viewport.width / 2f, centroid.y - zoom.viewport.height / 2f)
        zoom.transform(zoomChange, pan, pivot)
    }

    /** 낭독기의 '가운데에 글 메모 달기' — 지금 보이는 자리의 가운데(그림 안으로 들인다). */
    fun startNoteAtCenter(): Boolean {
        val v = view() ?: return false
        val x = v.imageX(v.viewportWidth / 2f).coerceIn(0f, 1f)
        val y = v.imageY(v.viewportHeight / 2f).coerceIn(0f, aspect)
        vm.startNote(x, y)
        return true
    }

    /** 그린 것이 바뀌었다 — 놓은 것이 들어왔으면 손가락의 것을 걷는다. */
    fun settle(marks: List<Mark>) {
        val ids = marks.mapTo(HashSet()) { it.id }
        live.committedId?.let { if (it in ids) live.clear() }
        if (hidden.isNotEmpty() && !active) hidden = hidden.filterTo(HashSet()) { it in ids }
        moving?.let { if (it.committed) moving = null }
    }

    private fun commitLive() {
        if (live.count == 0) return
        val id = vm.newId()
        live.committedId = id
        vm.add(live.toStroke(id))
    }

    private fun widthOf(tool: MarkupTool): Float {
        val widths = metrics.widthsOf(tool)
        return widths[style.levelOf(tool).coerceIn(0, widths.lastIndex)]
    }

    /** 가장 위에 그려진 메모부터 — 겹쳐 있으면 보이는 것을 잡는다. */
    private fun noteAt(x: Float, y: Float, reach: Float): Note? {
        for (i in marks.indices.reversed()) {
            val n = marks[i] as? Note ?: continue
            val size = painter.noteSize(n)
            if (MarkupMath.inside(x, y, n.x - reach, n.y - reach, n.x + size[0] + reach, n.y + size[1] + reach)) return n
        }
        return null
    }

    private fun eraseAlong(ax: Float, ay: Float, bx: Float, by: Float, v: MarkupView) {
        val r = v.toImage(metrics.eraserRadius)
        val hit = HashSet<Long>()
        for (m in marks) {
            if (m.id in hidden) continue
            val touched = when (m) {
                is Stroke -> MarkupMath.strokeHit(m, ax, ay, bx, by, r)
                is Note -> {
                    val size = painter.noteSize(m)
                    MarkupMath.rectHit(m.x, m.y, m.x + size[0], m.y + size[1], ax, ay, bx, by, r)
                }
            }
            if (touched) hit += m.id
        }
        if (hit.isNotEmpty()) hidden = hidden + hit
    }

    private fun kindOf(tool: MarkupTool): StrokeKind = when (tool) {
        MarkupTool.HIGHLIGHTER -> StrokeKind.HIGHLIGHTER
        MarkupTool.LINE -> StrokeKind.LINE
        else -> StrokeKind.PEN
    }
}
