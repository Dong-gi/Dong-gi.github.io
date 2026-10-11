package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `.ts`·`.mts` 를 목록이 어떻게 다루는가([AmbiguousStreams]) — 앞머리로 가른 답을 누름·큐·▶ 단추가 쓴다. */
class AmbiguousStreamsTest {

    private val dir = "/storage/emulated/0/작업"

    private fun entry(name: String, isDirectory: Boolean = false) = FileEntry(
        name = name,
        path = "$dir/$name",
        isDirectory = isDirectory,
        size = if (isDirectory) 0 else 1,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = MimeResolver.kindOf(name, isDirectory),
    )

    @Test
    fun `갈리는 이름만 읽는다`() {
        val read = ArrayList<String>()
        val result = AmbiguousStreams.sniff(
            listOf(entry("app.ts"), entry("녹화.ts"), entry("00001.MTS"), entry("a.mp4"), entry("b.tsx"), entry("x.ts", isDirectory = true)),
            look = { f: File -> read += f.name; f.name != "app.ts" },
        )
        assertEquals(listOf("app.ts", "녹화.ts", "00001.MTS"), read)
        assertEquals(mapOf("$dir/app.ts" to false, "$dir/녹화.ts" to true, "$dir/00001.MTS" to true), result)
    }

    @Test
    fun `읽지 못한 것은 표에 넣지 않는다`() {
        val result = AmbiguousStreams.sniff(listOf(entry("없다.ts")), look = { null })
        assertTrue(result.isEmpty())
    }

    @Test
    fun `상한까지만 읽는다`() {
        var reads = 0
        val many = (0 until AmbiguousStreams.MAX_SNIFF + 50).map { entry("f$it.ts") }
        AmbiguousStreams.sniff(many, look = { reads++; false })
        assertEquals(AmbiguousStreams.MAX_SNIFF, reads)
    }

    @Test
    fun `크기와 시각이 같으면 다시 읽지 않는다`() {
        val cache = HashMap<String, AmbiguousStreams.Sniffed>()
        var reads = 0
        val look = { _: File -> reads++; true }
        val a = entry("a.ts")
        val b = entry("b.ts")
        AmbiguousStreams.sniff(listOf(a, b), look = look, cache = cache)
        assertEquals(2, reads)
        // 같은 목록이 다시 섰다(폴더 감시 — 조각을 받는 HLS 폴더가 그렇다). 읽지 않고 같은 답이다.
        assertEquals(mapOf(a.path to true, b.path to true), AmbiguousStreams.sniff(listOf(a, b), look = look, cache = cache))
        assertEquals(2, reads)
        // 자란 것만 다시 읽는다. 목록에서 빠진 것은 기억에서 지운다 — 기억이 폴더 하나에 묶인다.
        AmbiguousStreams.sniff(listOf(a.copy(size = 2)), look = look, cache = cache)
        assertEquals(3, reads)
        assertEquals(setOf(a.path), cache.keys)
        // 상한은 **새로 읽는 수**다 — 기억한 답은 상한과 상관없이 쓴다.
        val many = (0 until AmbiguousStreams.MAX_SNIFF + 10).map { entry("f$it.ts") }
        val big = HashMap<String, AmbiguousStreams.Sniffed>()
        assertEquals(AmbiguousStreams.MAX_SNIFF, AmbiguousStreams.sniff(many, look = look, cache = big).size)
        val before = reads
        // 둘째 판: 기억한 1,000개 + 상한에 걸려 못 읽었던 10개를 새로 읽는다.
        assertEquals(AmbiguousStreams.MAX_SNIFF + 10, AmbiguousStreams.sniff(many, look = look, cache = big).size)
        assertEquals(before + 10, reads)
    }

    @Test
    fun `TypeScript 로 판정된 것은 글로 열고 큐에서 뺀다`() {
        val code = entry("app.ts")
        val video = entry("녹화.ts")
        val unknown = entry("모름.ts")
        val song = entry("노래.mp3")
        val streams = mapOf(code.path to false, video.path to true)
        assertTrue(AmbiguousStreams.isText(code, streams))
        assertFalse(AmbiguousStreams.isText(video, streams))
        // 아직 모르는 것은 이름대로(영상) — 누른 자리에서 화면이 본다.
        assertFalse(AmbiguousStreams.isText(unknown, streams))
        // 이름이 갈리지 않으면 표를 보지 않는다.
        assertFalse(AmbiguousStreams.isText(song, mapOf(song.path to false)))
        assertEquals(listOf(video, unknown, song), AmbiguousStreams.playable(listOf(code, video, unknown, song), streams))
    }

    @Test
    fun `단추는 영상으로 판정된 갈리는 이름만 센다`() {
        val code = entry("app.ts")
        val other = entry("util.mts")
        // TypeScript 만 있는 폴더 — 판정 전에도 후에도 단추가 없다(떴다 사라지지 않는다).
        assertFalse(AmbiguousStreams.hasPlayable(listOf(code, other), emptyMap()))
        assertFalse(AmbiguousStreams.hasPlayable(listOf(code, other), mapOf(code.path to false, other.path to false)))
        // 녹화 폴더 — 판정이 오면 단추가 뜬다.
        val rec = entry("녹화.ts")
        assertTrue(AmbiguousStreams.hasPlayable(listOf(rec), mapOf(rec.path to true)))
        // 갈리지 않는 미디어가 있으면 판정을 기다리지 않는다.
        assertTrue(AmbiguousStreams.hasPlayable(listOf(code, entry("노래.flac")), emptyMap()))
        assertEquals(FileKind.VIDEO, code.kind, "목록은 여전히 영상으로 적는다 — 파일을 열지 않는다")
    }
}
