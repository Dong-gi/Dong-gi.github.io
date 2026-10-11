package io.github.donggi.iroiroviewer.image

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 그리던 것의 **초안** — 프로세스가 죽어도 그리던 그림을 되살리려고 앱 전용 폴더에 적어 두는 꼴.
 *
 * ## 왜 저장 상태 번들이 아니라 파일인가
 *
 * 획 목록은 크다(획 하나에 점이 수백 개). 저장 상태 번들에 실으면 홈으로 나갈 때 바인더 한도를 넘어 앱이 죽는다(함정 표의
 * `rememberSelectionState`·`rememberTextFieldState` 와 같은 자리). 그래서 획은 ViewModel 이 들고, 앱이 화면에서 사라질 때(ON_STOP —
 * 그 뒤에만 시스템이 프로세스를 거둔다) 여기 적는다. 번들에는 그리던 그림의 경로 하나만 남는다. 되살린 뒤에는 되돌리기 기록이 없다.
 *
 * 그리기를 저장하거나 버리면 지운다. 다른 그림을 새로 그리기 시작해도 지운다 — 한 번에 하나뿐이다.
 *
 * ## 꼴
 *
 * 머리(표지·판·원본의 경로·크기·수정 시각·보이는 방향의 치수) 뒤에 그린 것을 차례로 적는다. 읽을 때는 **우리가 쓴 파일이라도 믿지
 * 않는다** — 쓰다 잘렸거나(쓰는 중에 죽었다) 판이 다르면 버린다. 수·길이에 상한을 두고 실수는 유한한지 본다.
 */
internal object MarkupDraft {

    /** 되살릴 것. [fileLength]·[fileModified]·[width]·[height] 가 지금의 원본과 같을 때만 되살린다(그사이 바뀌었으면 자리가 어긋난다). */
    class Data(
        val path: String,
        val fileLength: Long,
        val fileModified: Long,
        val width: Int,
        val height: Int,
        val marks: List<Mark>,
    ) {
        override fun toString(): String = "MarkupDraft.Data(${marks.size})"
    }

    /** 적는다. 흐름은 부르는 쪽이 닫는다. */
    @Throws(IOException::class)
    fun write(output: OutputStream, data: Data) {
        val out = DataOutputStream(output)
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeUTF(data.path)
        out.writeLong(data.fileLength)
        out.writeLong(data.fileModified)
        out.writeInt(data.width)
        out.writeInt(data.height)
        out.writeInt(data.marks.size)
        for (m in data.marks) {
            when (m) {
                is Stroke -> {
                    out.writeByte(TYPE_STROKE)
                    out.writeLong(m.id)
                    out.writeByte(m.kind.ordinal)
                    out.writeInt(m.color)
                    out.writeFloat(m.width)
                    out.writeInt(m.pointCount)
                    for (i in 0 until m.pointCount * 2) out.writeFloat(m.points[i])
                }
                is Note -> {
                    out.writeByte(TYPE_NOTE)
                    out.writeLong(m.id)
                    out.writeFloat(m.x)
                    out.writeFloat(m.y)
                    out.writeInt(m.color)
                    out.writeFloat(m.size)
                    out.writeBoolean(m.boxed)
                    out.writeUTF(m.text)
                }
            }
        }
        out.flush()
    }

    /** 읽는다. 꼴이 맞지 않으면(잘렸다·판이 다르다·값이 이상하다) null — 되살리지 않는다. 흐름은 부르는 쪽이 닫는다. */
    fun read(input: InputStream): Data? = try {
        readOrThrow(DataInputStream(input))
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun readOrThrow(input: DataInputStream): Data? {
        if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
        val path = input.readUTF()
        val length = input.readLong()
        val modified = input.readLong()
        val width = input.readInt()
        val height = input.readInt()
        if (width <= 0 || height <= 0) return null
        val count = input.readInt()
        if (count !in 0..MAX_MARKS) return null
        val marks = ArrayList<Mark>(count)
        val ids = HashSet<Long>()
        repeat(count) {
            val mark = when (input.readByte().toInt()) {
                TYPE_STROKE -> readStroke(input)
                TYPE_NOTE -> readNote(input)
                else -> null
            } ?: return null
            // 같은 신원이 둘이면 지우기·고치기가 엉뚱한 것을 건드린다.
            if (!ids.add(mark.id)) return null
            marks += mark
        }
        // 뒤에 남는 것이 있으면 우리가 쓴 꼴이 아니다.
        if (input.read() != -1) return null
        return Data(path, length, modified, width, height, marks)
    }

    private fun readStroke(input: DataInputStream): Stroke? {
        val id = input.readLong()
        val kind = StrokeKind.entries.getOrNull(input.readByte().toInt()) ?: return null
        val color = input.readInt()
        val width = input.readFloat()
        val n = input.readInt()
        if (!width.isFinite() || width <= 0f || n !in 1..MAX_STROKE_POINTS) return null
        if (kind == StrokeKind.LINE && n != 2) return null
        val points = FloatArray(n * 2)
        for (i in points.indices) {
            val v = input.readFloat()
            if (!v.isFinite()) return null
            points[i] = v
        }
        return Stroke(id, kind, color, width, points)
    }

    private fun readNote(input: DataInputStream): Note? {
        val id = input.readLong()
        val x = input.readFloat()
        val y = input.readFloat()
        val color = input.readInt()
        val size = input.readFloat()
        val boxed = input.readBoolean()
        val text = input.readUTF()
        if (!x.isFinite() || !y.isFinite() || !size.isFinite() || size <= 0f) return null
        if (text.isEmpty() || text.length > MAX_NOTE_CHARS) return null
        return Note(id, x, y, text, color, size, boxed)
    }

    /** 획 하나의 점 상한 — 그리는 쪽도 이만큼에서 획을 더 늘이지 않는다. 손가락으로 수천 화소를 그어야 닿는 수다. */
    const val MAX_STROKE_POINTS = 8192

    /** 메모 하나의 글자 상한 — 입력 칸도 이만큼에서 멈춘다. */
    const val MAX_NOTE_CHARS = 2000

    /** 되살릴 그린 것의 수 상한(읽는 쪽의 방어). */
    private const val MAX_MARKS = 20_000

    private const val MAGIC = 0x49524D4B // "IRMK"
    private const val VERSION = 1
    private const val TYPE_STROKE = 1
    private const val TYPE_NOTE = 2
}
