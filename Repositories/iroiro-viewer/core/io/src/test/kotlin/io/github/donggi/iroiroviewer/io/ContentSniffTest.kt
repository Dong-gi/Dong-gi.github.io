package io.github.donggi.iroiroviewer.io

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `.ts`·`.mts` 를 앞머리로 가른다([ContentSniff]) — 글(TypeScript)인가, 영상 쪽인가. */
class ContentSniffTest {

    /**
     * [packet] 바이트 패킷 [count] 개 — 패킷마다 [prefix] 자리가 동기 바이트이고 나머지는 [fill] 이다. [skip] 바이트를 앞에서
     * 잘라 낸다(패킷 한가운데서 시작하는 녹화).
     */
    private fun ts(count: Int, packet: Int = 188, prefix: Int = 0, fill: Byte = 0x00, skip: Int = 0): ByteArray {
        val out = ByteArray(count * packet) { fill }
        for (i in 0 until count) out[i * packet + prefix] = 0x47
        return out.copyOfRange(skip, out.size)
    }

    private val source = """
        // 첫 줄
        import { foo } from "./foo"
        export const answer: number = 42
    """.trimIndent().repeat(40).toByteArray()

    @Test
    fun `MPEG-TS 와 M2TS 를 알아본다`() {
        assertTrue(ContentSniff.isMpegTs(ts(5)))
        assertTrue(ContentSniff.isMpegTs(ts(40)))
        // 캠코더(AVCHD)·블루레이 — 4바이트 시각 + 188 = 192바이트 패킷.
        assertTrue(ContentSniff.isMpegTs(ts(5, packet = 192, prefix = 4)))
        // 다섯에 모자라면 무늬가 아니다(media3 의 기준).
        assertFalse(ContentSniff.isMpegTs(ts(4)))
    }

    @Test
    fun `패킷 한가운데서 시작하는 녹화도 알아본다`() {
        // 잘라 낸 녹화 — 첫 동기 바이트가 100번째 자리다. 예전 판정(0번 자리만)은 이것을 글로 보냈다.
        val cut = ts(10, fill = 0x11, skip = 88)
        assertEquals(0x11, cut[0].toInt())
        assertTrue(ContentSniff.isMpegTs(cut))
        assertTrue(ContentSniff.isMpegTs(ts(10, packet = 192, prefix = 4, fill = 0x11, skip = 150)))
        assertFalse(ContentSniff.isText(cut))
    }

    @Test
    fun `글로 보이는 것만 글이다`() {
        assertTrue(ContentSniff.isText(source))
        // 첫 글자가 `G`(0x47)여도 패킷 자리가 다 맞지 않으면 글이다.
        assertTrue(ContentSniff.isText("GENERATED FILE — DO NOT EDIT\n".repeat(30).toByteArray()))
        // BOM 이 있는 UTF-16 소스도 글이다 — 텍스트 뷰어가 받는다.
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "export const a = 1\n".repeat(20).toByteArray(Charsets.UTF_16LE)
        assertTrue(ContentSniff.isText(utf16))
        // 빈 파일은 글이다(텍스트 뷰어가 빈 화면으로 연다).
        assertTrue(ContentSniff.isText(ByteArray(0)))
    }

    @Test
    fun `패킷 무늬가 없는 이진도 영상 쪽이다`() {
        // 깨진 녹화 — 동기 바이트가 하나도 없다. 글이 아니므로 이름대로 영상 쪽에 둔다(재생 화면이 깨졌다고 말한다).
        val broken = ByteArray(4096) { (it * 7).toByte() }
        assertFalse(ContentSniff.isMpegTs(broken))
        assertFalse(ContentSniff.isText(broken))
    }

    @Test
    fun `널 없는 널 패킷도 영상이다`() {
        // PID 0x1FFF 의 널 패킷은 머리 뒤가 전부 0xFF 라 널 바이트가 없다 — 판정기만 보면 글로 읽힌다.
        val nulls = ts(10, fill = 0xFF.toByte()).also { for (i in 0 until 10) { it[i * 188 + 1] = 0x1F; it[i * 188 + 3] = 0x10 } }
        assertTrue(nulls.none { it == 0.toByte() })
        assertFalse(ContentSniff.isText(nulls))
    }

    @Test
    fun `읽은 길이만 본다`() {
        // 버퍼 뒤쪽의 찌꺼기를 믿지 않는다 — 넷째 패킷까지만 읽었다.
        assertFalse(ContentSniff.isMpegTs(ts(5), length = 188 * 4))
        assertTrue(ContentSniff.isText(source + ByteArray(100), length = source.size))
    }

    @Test
    fun `이름으로 갈리지 않는 것만 내용으로 본다`() {
        assertTrue(ContentSniff.isAmbiguousStream("녹화.ts"))
        assertTrue(ContentSniff.isAmbiguousStream("00001.MTS"))
        assertFalse(ContentSniff.isAmbiguousStream("a.m2ts"))
        assertFalse(ContentSniff.isAmbiguousStream("a.tsx"))
        assertFalse(ContentSniff.isAmbiguousStream("a.mp4"))
    }

    @Test
    fun `파일로 판정한다`() {
        val dir = createTempDirectory("sniff").toFile()
        try {
            val video = File(dir, "녹화.ts").apply { writeBytes(ts(10)) }
            val code = File(dir, "app.ts").apply { writeText("export default function main() {}\n".repeat(30)) }
            val avchd = File(dir, "00001.MTS").apply { writeBytes(ts(10, packet = 192, prefix = 4)) }
            // 앞머리 길이보다 긴 글 — 판정기가 보는 만큼만 읽는다.
            val long = File(dir, "long.ts").apply { writeText("const x = 1\n".repeat(5_000)) }
            assertEquals(true, ContentSniff.looksLikeStream(video))
            assertEquals(false, ContentSniff.looksLikeStream(code))
            assertEquals(false, ContentSniff.looksLikeStream(long))
            assertTrue(ContentSniff.isTextNamedLikeStream(code))
            assertFalse(ContentSniff.isTextNamedLikeStream(video))
            assertFalse(ContentSniff.isTextNamedLikeStream(avchd))
            // 이름이 갈리지 않으면 열어 보지도 않는다.
            assertFalse(ContentSniff.isTextNamedLikeStream(File(dir, "app.tsx").apply { writeText("x") }))
            // 없는 파일은 단정하지 않는다 — 영상 쪽이 '열 수 없다' 를 말한다.
            assertNull(ContentSniff.looksLikeStream(File(dir, "없다.ts")))
            assertFalse(ContentSniff.isTextNamedLikeStream(File(dir, "없다.ts")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
