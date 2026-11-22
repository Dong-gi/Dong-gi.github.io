package io.github.donggi.iroiroviewer.io

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 찍힌 시각 읽기의 실패 가르기.
 *
 * ExifInterface 자체는 JVM 시험에서 돌지 않는다(정적 초기화가 `android.util.Log` 를 부른다) — 그래서 그것이 기기에서 하는 일을
 * 그대로 흉내 낸 파서로 가르는 자리를 시험한다: 조작한 PNG 의 `eXIf` 길이를 믿고 `ByteArray(길이)` 를 잡는 것.
 */
class CaptureDateReadTest {

    /** 독립 검토가 재현한 꼴 — IHDR 뒤의 `eXIf` 덩이가 길이를 0x7FFFFFF0 으로 적는다(57바이트). */
    private val evilPng: ByteArray = run {
        val out = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val ihdr = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0)
        d.writeInt(ihdr.size)
        d.write("IHDR".toByteArray())
        d.write(ihdr)
        d.writeInt(0)
        d.writeInt(0x7FFFFFF0)
        d.write("eXIf".toByteArray())
        d.write(ByteArray(16))
        out.toByteArray()
    }

    /** ExifInterface 1.4.2 의 PNG 갈래처럼 — 덩이 길이를 믿고 그만큼 잡는다. */
    private fun naivePngExif(bytes: ByteArray): Long? {
        val d = DataInputStream(ByteArrayInputStream(bytes))
        d.skipBytes(8)
        while (true) {
            val length = d.readInt()
            val type = String(ByteArray(4).also { d.readFully(it) }, Charsets.US_ASCII)
            if (type == "eXIf") {
                val data = ByteArray(length)
                d.readFully(data)
                return null
            }
            d.skipBytes(length + 4)
        }
    }

    @Test
    fun 조작한_PNG_의_eXIf_길이로_무너진_파서는_날짜_없음이고_던지지_않는다() {
        assertEquals(57, evilPng.size)
        // 그 파서가 실제로 OutOfMemoryError 를 던지는지 먼저 본다 — 아니면 이 시험은 아무것도 시험하지 않는다.
        assertFailsWith<OutOfMemoryError> { naivePngExif(evilPng) }
        val r = CaptureDateRead.read(open = { evilPng }, parse = { naivePngExif(it) })
        assertEquals(CaptureDateRead.Result.NoDate, r)
    }

    @Test
    fun 열지_못한_것은_모른다이고_남기지_않는다() {
        assertEquals(
            CaptureDateRead.Result.Unknown,
            CaptureDateRead.read(open = { throw FileNotFoundException("x") }, parse = { _: Unit -> 1L }),
        )
        // 영상의 setDataSource 처럼 여는 자리의 런타임 예외도.
        assertEquals(
            CaptureDateRead.Result.Unknown,
            CaptureDateRead.read(open = { throw IllegalArgumentException("x") }, parse = { _: Unit -> 1L }),
        )
        // 연 뒤 읽는 도중의 입출력 실패도 일시적일 수 있다.
        assertEquals(
            CaptureDateRead.Result.Unknown,
            CaptureDateRead.read(open = { }, parse = { throw IOException("EIO") }),
        )
        // 클래스 초기화 실패는 파일 탓이 아니다 — '날짜 없음' 으로 남기지 않는다.
        assertEquals(
            CaptureDateRead.Result.Unknown,
            CaptureDateRead.read(open = { }, parse = { throw ExceptionInInitializerError("Log") }),
        )
        assertEquals(
            CaptureDateRead.Result.Unknown,
            CaptureDateRead.read(open = { }, parse = { throw NoClassDefFoundError("x") }),
        )
    }

    @Test
    fun 읽었으나_날짜가_없거나_파서가_던지면_날짜_없음() {
        assertEquals(CaptureDateRead.Result.NoDate, CaptureDateRead.read(open = { }, parse = { null }))
        assertEquals(
            CaptureDateRead.Result.NoDate,
            CaptureDateRead.read(open = { }, parse = { throw IndexOutOfBoundsException() }),
        )
        assertEquals(
            CaptureDateRead.Result.NoDate,
            CaptureDateRead.read(open = { }, parse = { throw StackOverflowError() }),
        )
        assertEquals(CaptureDateRead.Result.Taken(42), CaptureDateRead.read(open = { }, parse = { 42L }))
    }

    @Test
    fun 취소는_그대로_던진다() {
        assertFailsWith<CancellationException> {
            CaptureDateRead.read(open = { throw CancellationException("open") }, parse = { _: Unit -> 1L })
        }
        val e = assertFailsWith<CancellationException> {
            CaptureDateRead.read(open = { }, parse = { throw CancellationException("parse") })
        }
        assertTrue(e.message == "parse")
    }
}
