package io.github.donggi.iroiroviewer.image

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 그리던 것의 초안([MarkupDraft]) — 프로세스가 죽어도 되살리려고 적는 꼴. 적은 것은 그대로 읽히고, 잘렸거나 이상한 것은 **되살리지
 * 않는다**(우리가 쓴 파일이라도 믿지 않는다).
 */
class MarkupDraftTest {

    private val marks = listOf(
        Stroke(1, StrokeKind.PEN, 0xFFE53935.toInt(), 0.004f, floatArrayOf(0.1f, 0.2f, 0.15f, 0.22f, 0.3f, 0.4f)),
        Stroke(2, StrokeKind.HIGHLIGHTER, 0xFFFDD835.toInt(), 0.03f, floatArrayOf(0.5f, 0.5f)),
        Note(5, 0.2f, 0.3f, "첫 줄\n둘째 줄 ✍️", 0xFFFFFFFF.toInt(), 0.04f, boxed = true),
        Stroke(9, StrokeKind.LINE, 0xFF000000.toInt(), 0.002f, floatArrayOf(0.1f, 0.6f, 0.9f, 0.6f)),
    )

    private fun data(marks: List<Mark> = this.marks) =
        MarkupDraft.Data("/storage/emulated/0/DCIM/사진.jpg", 123_456L, 1_700_000_000_000L, 4000, 3000, marks)

    private fun bytesOf(d: MarkupDraft.Data): ByteArray = ByteArrayOutputStream().also { MarkupDraft.write(it, d) }.toByteArray()

    private fun read(bytes: ByteArray) = MarkupDraft.read(ByteArrayInputStream(bytes))

    @Test
    fun `적은 것이 그대로 읽힌다`() {
        val back = assertNotNull(read(bytesOf(data())))
        assertEquals("/storage/emulated/0/DCIM/사진.jpg", back.path)
        assertEquals(123_456L, back.fileLength)
        assertEquals(1_700_000_000_000L, back.fileModified)
        assertEquals(4000, back.width)
        assertEquals(3000, back.height)
        assertEquals(marks.size, back.marks.size)
        for ((a, b) in marks.zip(back.marks)) {
            assertEquals(a.id, b.id)
            when (a) {
                is Stroke -> {
                    b as Stroke
                    assertEquals(a.kind, b.kind)
                    assertEquals(a.color, b.color)
                    assertEquals(a.width, b.width)
                    assertContentEquals(a.points, b.points)
                }
                is Note -> assertEquals(a, b)
            }
        }
    }

    @Test
    fun `빈 그림도 적고 읽힌다`() {
        assertEquals(0, assertNotNull(read(bytesOf(data(emptyList())))).marks.size)
    }

    /** 쓰는 도중에 죽어 잘린 초안 — 어디서 잘렸든 되살리지 않는다. */
    @Test
    fun `잘린 초안은 버린다`() {
        val whole = bytesOf(data())
        for (n in 0 until whole.size) {
            assertNull(read(whole.copyOf(n)), "앞 $n 바이트")
        }
    }

    @Test
    fun `꼴이 다르면 버린다`() {
        val whole = bytesOf(data())
        // 표지가 다르다.
        assertNull(read(whole.copyOf().also { it[0] = 0 }))
        // 판이 다르다.
        assertNull(read(whole.copyOf().also { it[7] = 9 }))
        // 뒤에 남는 것이 있다.
        assertNull(read(whole + byteArrayOf(0)))
    }

    /** 손으로 지은 초안 — 값 하나만 이상하다. */
    private fun forged(body: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeInt(0x49524D4B)
            writeInt(1)
            writeUTF("/a.jpg")
            writeLong(1)
            writeLong(2)
            writeInt(100)
            writeInt(100)
            body()
            flush()
        }
    }.toByteArray()

    private fun DataOutputStream.stroke(id: Long, kind: Int, width: Float, vararg xy: Float) {
        writeByte(1)
        writeLong(id)
        writeByte(kind)
        writeInt(0)
        writeFloat(width)
        writeInt(xy.size / 2)
        xy.forEach { writeFloat(it) }
    }

    @Test
    fun `값이 이상하면 버린다`() {
        // 멀쩡한 것은 읽힌다(지은 꼴이 맞는지).
        assertNotNull(read(forged { writeInt(1); stroke(1, 0, 0.01f, 0.1f, 0.1f) }))
        // 실수가 유한하지 않다.
        assertNull(read(forged { writeInt(1); stroke(1, 0, 0.01f, Float.NaN, 0.1f) }))
        assertNull(read(forged { writeInt(1); stroke(1, 0, Float.POSITIVE_INFINITY, 0.1f, 0.1f) }))
        // 굵기가 0.
        assertNull(read(forged { writeInt(1); stroke(1, 0, 0f, 0.1f, 0.1f) }))
        // 모르는 종류.
        assertNull(read(forged { writeInt(1); stroke(1, 7, 0.01f, 0.1f, 0.1f) }))
        // 직선인데 점이 셋.
        assertNull(read(forged { writeInt(1); stroke(1, StrokeKind.LINE.ordinal, 0.01f, 0f, 0f, 1f, 1f, 2f, 2f) }))
        // 같은 신원이 둘.
        assertNull(read(forged { writeInt(2); stroke(3, 0, 0.01f, 0.1f, 0.1f); stroke(3, 0, 0.01f, 0.2f, 0.2f) }))
        // 수가 터무니없다 — 배열을 잡기 전에 거절한다.
        assertNull(read(forged { writeInt(Int.MAX_VALUE) }))
        assertNull(read(forged { writeInt(-1) }))
        // 점의 수가 상한을 넘는다.
        assertNull(
            read(
                forged {
                    writeInt(1)
                    writeByte(1)
                    writeLong(1)
                    writeByte(0)
                    writeInt(0)
                    writeFloat(0.01f)
                    writeInt(MarkupDraft.MAX_STROKE_POINTS + 1)
                },
            ),
        )
        // 빈 메모.
        assertNull(
            read(
                forged {
                    writeInt(1)
                    writeByte(2)
                    writeLong(1)
                    writeFloat(0.1f)
                    writeFloat(0.1f)
                    writeInt(0)
                    writeFloat(0.05f)
                    writeBoolean(true)
                    writeUTF("")
                },
            ),
        )
    }
}
