package io.github.donggi.iroiroviewer.playback

import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 자막 이름 짝짓기.
 *
 * **추측이라는 것이 이 파일의 주제다.** 규칙이 넓으면 남의 자막이 붙고, 좁으면 흔한
 * 이름(`영화.ko.srt`)을 놓친다. 경계를 여기에 박아 둔다.
 */
class SubtitleNamesTest {

    private fun f(name: String, dir: Boolean = false, locked: Boolean = false) = FileEntry(
        name = name,
        path = "/v/$name",
        isDirectory = dir,
        size = 100,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = if (dir) FileKind.FOLDER else FileKind.TEXT,
        isLocked = locked,
    )

    @Test
    fun 확장자로_자막을_가린다() {
        assertTrue(SubtitleNames.isSubtitle("a.srt"))
        assertTrue(SubtitleNames.isSubtitle("A.SRT"))
        assertTrue(SubtitleNames.isSubtitle("a.ass"))
        assertTrue(SubtitleNames.isSubtitle("a.ssa"))
        assertTrue(SubtitleNames.isSubtitle("a.vtt"))
        assertFalse(SubtitleNames.isSubtitle("a.txt"))
        assertFalse(SubtitleNames.isSubtitle("srt"))
        assertFalse(SubtitleNames.isSubtitle(".srt"))
    }

    /** media3 에 SAMI 파서가 없어 SRT 로 바꿔 넘긴다 — 자막이고, 짝을 찾고, SRT 의 MIME 으로 붙고, 바이트 길을 지난다. */
    @Test
    fun smi_는_SRT_로_바꿔_붙는_자막이다() {
        for (name in listOf("영화.smi", "영화.SMI", "영화.ko.sami")) {
            assertTrue(SubtitleNames.isSubtitle(name), name)
            assertTrue(SubtitleNames.needsTranscoding(name), name)
            assertEquals("application/x-subrip", SubtitleNames.mimeOf(name), name)
            assertTrue(SubtitleShift.supports(name), name)
        }
        assertTrue(SubtitleNames.matches("영화.mkv", "영화.smi"))
        assertEquals(setOf("smi", "sami"), SubtitleNames.SAMI_EXTENSIONS)
    }

    @Test
    fun 같은_이름이면_붙는다() {
        assertTrue(SubtitleNames.matches("영화.mkv", "영화.srt"))
        assertTrue(SubtitleNames.matches("영화.mkv", "영화.ko.srt"))
        assertTrue(SubtitleNames.matches("영화.mkv", "영화.eng.ass"))
        // 대소문자는 가리지 않는다 — FAT 볼륨에서 흔들린다.
        assertTrue(SubtitleNames.matches("Movie.MP4", "movie.SRT"))
    }

    /** **점까지 맞아야 한다.** 이것이 없으면 `영화2.srt` 가 `영화.mkv` 에 붙는다. */
    @Test
    fun 이름이_겹치기만_하면_붙지_않는다() {
        assertFalse(SubtitleNames.matches("영화.mkv", "영화2.srt"))
        assertFalse(SubtitleNames.matches("영화.mkv", "영화-한글.srt"))
        assertFalse(SubtitleNames.matches("영화.mkv", "다른영화.srt"))
        assertFalse(SubtitleNames.matches("영화.mkv", "영화.txt"))
    }

    @Test
    fun 기본_자막은_짝이_맞는_것만() {
        val sibs = listOf(f("영화.srt"), f("딴것.srt"))
        assertEquals("영화.srt", SubtitleNames.defaultFor("영화.mkv", sibs)?.name)
        assertNull(SubtitleNames.defaultFor("없는영화.mkv", sibs))
    }

    /**
     * 후보 목록은 **짝이 맞는 것이 앞, 나머지가 뒤**다.
     *
     * 나머지까지 주는 것이 요점이다 — 이름 규칙이 우리 추측과 다른 자막
     * (`영화-한글자막.srt`)도 한 번 눌러 붙일 수 있어야 막다른 길이 없다.
     */
    @Test
    fun 후보는_짝이_먼저고_나머지도_준다() {
        val sibs = listOf(f("영화-한글자막.srt"), f("영화.ko.srt"), f("영화.srt"), f("메모.txt"))
        val got = SubtitleNames.candidatesFor("영화.mkv", sibs).map { it.name }
        assertEquals(listOf("영화.ko.srt", "영화.srt", "영화-한글자막.srt"), got)
    }

    @Test
    fun 폴더와_잠긴_것은_후보가_아니다() {
        val sibs = listOf(f("영화.srt", dir = true), f("영화.ko.srt", locked = true), f("영화.vtt"))
        assertEquals(listOf("영화.vtt"), SubtitleNames.candidatesFor("영화.mkv", sibs).map { it.name })
        assertNull(SubtitleNames.defaultFor("영화.mkv", sibs.take(2)))
    }

    @Test
    fun MIME_을_확장자로_정한다() {
        assertEquals("application/x-subrip", SubtitleNames.mimeOf("a.srt"))
        assertEquals("text/x-ssa", SubtitleNames.mimeOf("a.ass"))
        assertEquals("text/x-ssa", SubtitleNames.mimeOf("a.SSA"))
        assertEquals("text/vtt", SubtitleNames.mimeOf("a.vtt"))
    }

    @Test
    fun `TTML 은 자막이지만 인코딩을 바꾸지 않는다`() {
        // media3 에 TTML 파서가 있다. XML 이라 인코딩을 선언에 적으므로 바이트를 UTF-8 로 바꾸면 선언과 어긋난다.
        for (name in listOf("영화.ttml", "영화.ko.dfxp", "A.TTML")) {
            assertTrue(SubtitleNames.isSubtitle(name), name)
            assertEquals("application/ttml+xml", SubtitleNames.mimeOf(name), name)
            assertFalse(SubtitleNames.needsTranscoding(name), name)
        }
        assertTrue(SubtitleNames.matches("영화.mkv", "영화.ttml"))
        for (name in listOf("a.srt", "a.ASS", "a.ssa", "a.vtt")) assertTrue(SubtitleNames.needsTranscoding(name), name)
        assertTrue(SubtitleNames.needsTranscoding("a.smi"))
    }

    /** 짝이 맞는 것 가운데 SAMI 는 뒤다 — 이름 차례로는 `.smi` 가 `.srt` 보다 먼저지만 원래 형식이 낫다(바꿔 넣으면 언어 하나·서식 없음). */
    @Test
    fun 짝이_맞는_SAMI_는_원래_형식_뒤로_민다() {
        val siblings = listOf(f("영화.smi"), f("영화.srt"), f("영화.ko.sami"), f("영화.ass"), f("다른.srt"))
        assertEquals("영화.srt", SubtitleNames.defaultFor("영화.mkv", siblings)?.name)
        assertEquals(
            listOf("영화.ass", "영화.srt", "영화.ko.sami", "영화.smi", "다른.srt"),
            SubtitleNames.candidatesFor("영화.mkv", siblings).map { it.name },
        )
        // SAMI 뿐이면 그것이 붙는다.
        assertEquals("영화.smi", SubtitleNames.defaultFor("영화.mkv", listOf(f("영화.smi"), f("다른.srt")))?.name)
        // SAMI 가 아닌 것끼리는 폴더가 준 차례 그대로다(예전과 같다).
        assertEquals("영화.srt", SubtitleNames.defaultFor("영화.mkv", listOf(f("영화.srt"), f("영화.ass")))?.name)
    }
}
