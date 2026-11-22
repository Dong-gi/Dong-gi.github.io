package io.github.donggi.iroiroviewer.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 찍힌 시각 캐시 — 열쇠의 셋, 시간대 꼬리표, 파일 왕복, 깨진 파일. */
class CaptureDateCacheTest {

    private val seoul = "Asia/Seoul"

    private fun tempFile() = Files.createTempDirectory("dates").toFile().resolve(CaptureDateCache.FILE_NAME)

    @Test
    fun 크기나_수정_시각이_다르면_모른다로_답한다() {
        val c = CaptureDateCache(null).apply { useZone(seoul) }
        c.put("/a.jpg", 10, 100, 5_000, seoul)
        assertEquals(5_000L, c.lookup("/a.jpg", 10, 100))
        assertNull(c.lookup("/a.jpg", 11, 100))
        assertNull(c.lookup("/a.jpg", 10, 101))
        assertNull(c.lookup("/b.jpg", 10, 100))
    }

    @Test
    fun 날짜_없음도_기억한다() {
        val c = CaptureDateCache(null).apply { useZone(seoul) }
        c.put("/a.gif", 1, 2, CaptureDateCache.NONE, seoul)
        assertEquals(CaptureDateCache.NONE, c.lookup("/a.gif", 1, 2))
    }

    @Test
    fun 저장하고_새로_열면_같은_값이다() {
        val f = tempFile()
        CaptureDateCache(f).apply {
            useZone(seoul)
            put("/sdcard/DCIM/한글 이름.jpg", 10, 100, 5_000, seoul)
            put("/sdcard/DCIM/b.mp4", 20, 200, CaptureDateCache.NONE, seoul)
            save()
        }
        val back = CaptureDateCache(f).apply { useZone(seoul) }
        assertEquals(5_000L, back.lookup("/sdcard/DCIM/한글 이름.jpg", 10, 100))
        assertEquals(CaptureDateCache.NONE, back.lookup("/sdcard/DCIM/b.mp4", 20, 200))
        assertFalse(f.resolveSibling(f.name + ".tmp").exists())
    }

    @Test
    fun 시간대가_다르면_파일의_것도_메모리의_것도_버린다() {
        val f = tempFile()
        CaptureDateCache(f).apply {
            useZone(seoul)
            put("/a.jpg", 1, 1, 7, seoul)
            save()
        }
        // 파일에 적힌 시간대와 다르다.
        val other = CaptureDateCache(f).apply { useZone("America/New_York") }
        assertNull(other.lookup("/a.jpg", 1, 1))
        // 같은 프로세스 안에서 시간대가 바뀌어도.
        val mem = CaptureDateCache(null).apply { useZone(seoul); put("/a.jpg", 1, 1, 7, seoul) }
        // 다른 시간대로 셈한 값은 받지 않는다(읽는 사이 표의 시간대가 바뀌었다).
        mem.put("/b.jpg", 1, 1, 9, "Asia/Tokyo")
        assertNull(mem.lookup("/b.jpg", 1, 1))
        mem.useZone("Europe/London")
        assertNull(mem.lookup("/a.jpg", 1, 1))
        // 같은 시간대로 다시 열면 남아 있다 — 버린 것은 다른 시간대로 연 쪽이 저장하기 전까지 파일에 그대로다.
        assertEquals(7L, CaptureDateCache(f).apply { useZone(seoul) }.lookup("/a.jpg", 1, 1))
    }

    @Test
    fun 끝까지_본_뒤_없는_경로와_걷은_경로는_파일에서도_빠진다() {
        val f = tempFile()
        CaptureDateCache(f).apply {
            useZone(seoul)
            put("/a.jpg", 1, 1, 1, seoul)
            put("/gone.jpg", 1, 1, 2, seoul)
            put("/moved.jpg", 1, 1, 3, seoul)
            save()
        }
        CaptureDateCache(f).apply {
            useZone(seoul)
            retainOnly(setOf("/a.jpg", "/moved.jpg"))
            forget(listOf("/moved.jpg"))
            save()
        }
        val back = CaptureDateCache(f).apply { useZone(seoul) }
        assertEquals(1L, back.lookup("/a.jpg", 1, 1))
        assertNull(back.lookup("/gone.jpg", 1, 1))
        assertNull(back.lookup("/moved.jpg", 1, 1))
    }

    @Test
    fun 쓰다_죽어_남은_임시_파일은_처음_읽을_때_지운다() {
        val f = tempFile()
        val tmp = f.resolveSibling(f.name + ".tmp")
        tmp.writeText("/sdcard/DCIM/secret.jpg")
        CaptureDateCache(f).lookup("/x", 0, 0)
        assertFalse(tmp.exists())
    }

    @Test
    fun 깨진_파일과_다른_판과_잘린_파일은_빈_캐시로_시작한다() {
        val out = ByteArrayOutputStream()
        CaptureDateCache.write(CaptureDateCache.Snapshot(seoul, mapOf("/a.jpg" to Triple(1L, 2L, 3L))), out)
        val good = out.toByteArray()
        val snap = CaptureDateCache.read(ByteArrayInputStream(good))!!
        assertEquals(seoul, snap.zone)
        assertEquals(Triple(1L, 2L, 3L), snap.entries["/a.jpg"])

        val otherVersion = good.copyOf().also { it[7] = 99 }
        assertNull(CaptureDateCache.read(ByteArrayInputStream(otherVersion)))
        assertNull(CaptureDateCache.read(ByteArrayInputStream(good.copyOf(good.size - 3))))
        assertNull(CaptureDateCache.read(ByteArrayInputStream(byteArrayOf(1, 2, 3))))

        val f = tempFile()
        f.writeBytes(byteArrayOf(0x49, 0x52, 0x4F))
        assertNull(CaptureDateCache(f).apply { useZone(seoul) }.lookup("/a.jpg", 1, 2))
        // 쓰지 못할 파일은 지운다 — 경로가 평문으로 남지 않게.
        assertFalse(f.exists())
    }
}
