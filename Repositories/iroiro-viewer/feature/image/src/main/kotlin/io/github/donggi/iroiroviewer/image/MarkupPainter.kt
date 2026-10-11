package io.github.donggi.iroiroviewer.image

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import io.github.donggi.iroiroviewer.io.CropSupport
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 획과 글 메모를 그린다 — **화면과 저장이 이 하나를 쓴다**(배율만 다르다).
 *
 * 화면(`MarkupScreen`)은 맞춤 크기의 그림 위에 확대·이동을 얹어 그리고, 저장([MarkupOverlay])은 원본 해상도로 뜬 비트맵 위에 그린다.
 * 둘 다 [draw] 에 '그림의 폭이 이 캔버스에서 몇 화소인가' 하나만 주고, 여기서는 모든 것을 **기준 단위**([REF] — 그림 폭 = 1000)로
 * 그린 뒤 캔버스를 그 비율로 키운다. 그래서 경로의 모양, 글의 줄 바꿈, 상자의 여백이 **배율과 상관없이 같다** — 화면에서 본 자리·굵기·
 * 글자 크기가 저장한 그림에 그대로 남는다. 글을 화면 크기로 따로 짜면 글자 폭의 반올림이 달라 줄이 다른 자리에서 꺾일 수 있다.
 *
 * 경로와 짜 놓은 글은 캐시한다 — 화면은 손가락이 움직일 때마다 전부 다시 그린다. 획은 불변이라([Stroke.points]) 한 번 만들면 된다.
 * 캐시는 이 객체의 것이라 저장 쪽은 새로 만들어 한 번 쓰고 버린다. 스레드 안전하지 않다 — 한 객체를 한 스레드에서만 쓴다.
 */
internal class MarkupPainter {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** 획 → 경로(기준 단위). 키는 객체 자신이다(`Stroke` 는 `equals` 를 덮지 않는다 — 같은 점을 가진 두 획도 다른 획이다). */
    private val paths = HashMap<Stroke, Path>()

    /** 글·크기·빛깔 → 짜 놓은 글. 자리는 키에 넣지 않는다 — 메모를 끄는 동안 프레임마다 다시 짜지 않게. */
    private val layouts = HashMap<LayoutKey, NoteLayout>()

    /**
     * [marks] 를 그린다. 캔버스의 원점이 그림의 왼쪽 위이고 그림의 폭이 [imageWidth] 화소라고 본다 — 그림 밖은 부르는 쪽이 자른다.
     * [skip] 의 것은 그리지 않는다(지우개가 지나가는 중이거나 끌어 옮기는 중인 것).
     */
    fun draw(canvas: Canvas, marks: List<Mark>, imageWidth: Float, skip: Set<Long> = emptySet()) {
        if (imageWidth <= 0f || !imageWidth.isFinite()) return
        val k = imageWidth / REF
        canvas.withScale(k, k) {
            for (m in marks) {
                if (m.id in skip) continue
                when (m) {
                    is Stroke -> drawStroke(this, m.kind, m.color, m.width, m.points, m.pointCount) {
                        paths.getOrPut(m) { pathOf(m.points, m.pointCount) }
                    }
                    is Note -> drawNote(this, m, m.x, m.y)
                }
            }
        }
        // 지운 것·되돌린 것의 캐시가 쌓이지 않게. 다시 살아나면(다시 하기) 다시 만든다 — 싸다.
        if (paths.size > marks.size + CACHE_SLACK || layouts.size > marks.size + CACHE_SLACK) retain(marks)
    }

    /** 긋는 중인 획 — 아직 [Stroke] 가 아니다(점 버퍼가 자란다). 경로는 매번 새로 짓는다. */
    fun drawLive(canvas: Canvas, kind: StrokeKind, color: Int, width: Float, points: FloatArray, count: Int, imageWidth: Float) {
        if (imageWidth <= 0f || count <= 0) return
        val k = imageWidth / REF
        canvas.withScale(k, k) { drawStroke(this, kind, color, width, points, count) { pathOf(points, count) } }
    }

    /** 메모 하나를 ([x], [y]) 에 그린다 — 끌어 옮기는 중인 메모. */
    fun drawNoteMoved(canvas: Canvas, note: Note, x: Float, y: Float, imageWidth: Float) {
        if (imageWidth <= 0f) return
        val k = imageWidth / REF
        canvas.withScale(k, k) { drawNote(this, note, x, y) }
    }

    /** 메모 상자의 크기 `[폭, 높이]`(그림 좌표). 맞힘 판정과 그림 안에 두기([MarkupMath.clampBox])가 쓴다. */
    fun noteSize(note: Note): FloatArray {
        val l = layoutOf(note)
        return floatArrayOf(l.width / REF, l.height / REF)
    }

    private inline fun drawStroke(
        canvas: Canvas,
        kind: StrokeKind,
        color: Int,
        width: Float,
        points: FloatArray,
        count: Int,
        path: () -> Path,
    ) {
        if (count <= 0) return
        val argb = inkOf(kind, color)
        if (count == 1) {
            // 점 하나 — 두드려 찍은 점. 둥근 끝의 길이 0 선에 기대지 않고 원으로 그린다.
            fillPaint.color = argb
            canvas.drawCircle(points[0] * REF, points[1] * REF, width * REF / 2f, fillPaint)
            return
        }
        strokePaint.color = argb
        strokePaint.strokeWidth = width * REF
        // **한 획을 한 경로로 한 번에 그린다** — 반투명한 형광펜이 제 위를 겹쳐 지나가도 진해지지 않는다(한 번 그리기 안에서는 덮인
        // 자리를 한 번만 칠한다). 구간마다 따로 그리면 이음매마다 진한 점이 찍힌다.
        canvas.drawPath(path(), strokePaint)
    }

    private fun drawNote(canvas: Canvas, note: Note, x: Float, y: Float) {
        if (note.text.isEmpty()) return
        val l = layoutOf(note)
        val left = x * REF
        val top = y * REF
        if (note.boxed) {
            fillPaint.color = if (MarkupMath.isLight(note.color)) DARK_BOX else LIGHT_BOX
            val r = note.size * REF * RADIUS_EM
            canvas.drawRoundRect(left, top, left + l.width, top + l.height, r, r, fillPaint)
        }
        canvas.withTranslation(left + l.padX - l.lineLeft, top + l.padY) { l.layout.draw(this) }
    }

    private fun layoutOf(note: Note): NoteLayout = layouts.getOrPut(LayoutKey(note.text, note.size, note.color)) {
        val size = note.size * REF
        // 글마다 제 붓을 든다 — `StaticLayout` 은 붓을 참조로 들고 그릴 때 읽는다. 한 붓을 돌려 쓰면 마지막 메모의 크기·빛깔로 그려진다.
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            textSize = size
            color = note.color
            typeface = Typeface.DEFAULT
        }
        val wrap = ceil(MarkupMath.noteWrapWidth(note.size) * REF).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(note.text, 0, note.text.length, paint, wrap)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        // 줄의 실제 폭으로 상자를 줄인다(짧은 메모에 넓은 상자가 깔리지 않게). 오른쪽에서 쓰는 글도 받게 왼쪽 끝도 잰다.
        var lineLeft = Float.MAX_VALUE
        var lineRight = 0f
        for (i in 0 until layout.lineCount) {
            lineLeft = min(lineLeft, layout.getLineLeft(i))
            lineRight = max(lineRight, layout.getLineRight(i))
        }
        if (lineLeft > lineRight) lineLeft = 0f
        val padX = size * PAD_X_EM
        val padY = size * PAD_Y_EM
        NoteLayout(
            layout = layout,
            lineLeft = lineLeft,
            width = (lineRight - lineLeft) + 2f * padX,
            height = layout.height + 2f * padY,
            padX = padX,
            padY = padY,
        )
    }

    private fun retain(marks: List<Mark>) {
        val strokes = HashSet<Stroke>()
        val notes = HashSet<LayoutKey>()
        for (m in marks) {
            when (m) {
                is Stroke -> strokes += m
                is Note -> notes += LayoutKey(m.text, m.size, m.color)
            }
        }
        paths.keys.retainAll(strokes)
        layouts.keys.retainAll(notes)
    }

    private data class LayoutKey(val text: String, val size: Float, val color: Int)

    /** 짜 놓은 글(기준 단위). [width]·[height] 는 여백까지 든 상자다. */
    private class NoteLayout(
        val layout: StaticLayout,
        val lineLeft: Float,
        val width: Float,
        val height: Float,
        val padX: Float,
        val padY: Float,
    )

    companion object {
        /** 기준 단위 — 그림의 폭을 이만큼으로 보고 그린다. 그림 좌표(폭 = 1)를 그대로 쓰면 굵기·글자 크기가 0.00x 라 다루기 어렵다. */
        const val REF = 1000f

        /** 형광펜의 불투명도(255 중). 밑의 글이 비쳐야 한다. */
        const val HIGHLIGHT_ALPHA = 0x66

        /** 메모 상자의 여백·모서리(글자 크기의 몫). */
        private const val PAD_X_EM = 0.35f
        private const val PAD_Y_EM = 0.2f
        private const val RADIUS_EM = 0.3f

        /** 밝은 글 밑의 어두운 상자, 어두운 글 밑의 밝은 상자 — 둘 다 그림이 비친다. */
        private const val DARK_BOX = 0x99000000.toInt()
        private const val LIGHT_BOX = 0xCCFFFFFF.toInt()

        /** 캐시가 그린 것보다 이만큼 넘게 많으면 정리한다. */
        private const val CACHE_SLACK = 32

        /** 획의 잉크 — 형광펜은 고른 빛깔을 반투명으로. */
        fun inkOf(kind: StrokeKind, color: Int): Int =
            if (kind == StrokeKind.HIGHLIGHTER) (color and 0x00FFFFFF) or (HIGHLIGHT_ALPHA shl 24) else color

        /**
         * 점 → 경로(기준 단위). 손가락이 지난 점들을 **가운데 점을 지나는 2차 곡선**으로 잇는다 — 꺾은선으로 이으면 빠르게 그은 곡선이
         * 각지다. 끝은 마지막 점까지 곧게 잇는다.
         */
        fun pathOf(points: FloatArray, count: Int): Path {
            val path = Path()
            if (count <= 0) return path
            path.moveTo(points[0] * REF, points[1] * REF)
            if (count == 1) return path
            if (count == 2) {
                path.lineTo(points[2] * REF, points[3] * REF)
                return path
            }
            for (i in 1 until count - 1) {
                val x = points[2 * i]
                val y = points[2 * i + 1]
                val nx = points[2 * i + 2]
                val ny = points[2 * i + 3]
                path.quadTo(x * REF, y * REF, (x + nx) / 2f * REF, (y + ny) / 2f * REF)
            }
            path.lineTo(points[2 * count - 2] * REF, points[2 * count - 1] * REF)
            return path
        }
    }
}

/**
 * 큐에 싣는 그린 것(`CropSupport.Overlay`) — 원본 해상도로 뜬 그림 위에 [MarkupPainter] 로 그린다. [marks] 는 불변이다(목록도, 획의
 * 점도) — 큐에 줄 선 동안 화면이 다음 그림을 그려도 이 요청의 것은 그대로다.
 */
internal class MarkupOverlay(private val marks: List<Mark>) : CropSupport.Overlay {

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        // 화면도 그림 밖을 잘라 그린다 — 그림 밖으로 나간 획은 저장한 그림에도 없다(화면에서 본 그대로).
        canvas.withClip(0, 0, width, height) { MarkupPainter().draw(this, marks, width.toFloat()) }
    }

    // 메모의 글을 로그에 싣지 않는다 — 요청(`SaveCrop`)의 `toString` 이 이것을 부른다.
    override fun toString(): String = "MarkupOverlay(${marks.size})"
}
