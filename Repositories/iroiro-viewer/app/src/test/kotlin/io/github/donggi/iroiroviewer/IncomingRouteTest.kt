package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.model.FileKind
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 다른 앱이 넘긴 파일이 어느 화면으로 가는가([IncomingRoute]). 파일 목록의 누름(`TapRoute`)과 같은 표여야 한다 — 같은 파일이
 * 어디서 열었느냐에 따라 다른 화면으로 가면 고장으로 읽힌다.
 */
class IncomingRouteTest {

    private val dir: File = Files.createTempDirectory("incoming").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `종류마다 파일 목록의 누름과 같은 화면으로 간다`() {
        val expected = mapOf(
            FileKind.AUDIO to IncomingRoute.PLAYER,
            FileKind.VIDEO to IncomingRoute.PLAYER,
            FileKind.IMAGE to IncomingRoute.IMAGE,
            FileKind.TEXT to IncomingRoute.TEXT,
            FileKind.CODE to IncomingRoute.TEXT,
            FileKind.COMIC to IncomingRoute.COMIC,
            FileKind.ARCHIVE to IncomingRoute.ARCHIVE,
            FileKind.PDF to IncomingRoute.DOCUMENT,
            FileKind.EBOOK to IncomingRoute.DOCUMENT,
            FileKind.DOCUMENT to IncomingRoute.DOCUMENT,
            FileKind.SHEET to IncomingRoute.DOCUMENT,
            FileKind.SLIDE to IncomingRoute.DOCUMENT,
            FileKind.HWP to IncomingRoute.DOCUMENT,
            // 이 앱에 뷰어가 없다 — 이름과 '다른 앱으로 열기'.
            FileKind.APK to IncomingRoute.NONE,
            FileKind.FONT to IncomingRoute.NONE,
            FileKind.OTHER to IncomingRoute.NONE,
            FileKind.FOLDER to IncomingRoute.NONE,
        )
        // 종류가 늘면 이 표도 늘어야 한다 — 빠진 종류가 없는지 센다.
        assertEquals(FileKind.entries.toSet(), expected.keys)
        for ((kind, route) in expected) assertEquals(route, IncomingRoute.of(kind), kind.name)
    }

    @Test
    fun `뷰어가 있는 갈래만 화면을 연다`() {
        assertEquals(AppScreen.Viewer("/p"), IncomingRoute.IMAGE.screenFor("/p"))
        assertEquals(AppScreen.Text("/p"), IncomingRoute.TEXT.screenFor("/p"))
        assertEquals(AppScreen.Archive("/p"), IncomingRoute.ARCHIVE.screenFor("/p"))
        // 만화는 이어보기 자리에서 연다(엔트리 번호 -1 — 파일 목록에서 연 것과 같다).
        assertEquals(AppScreen.Comic("/p", -1), IncomingRoute.COMIC.screenFor("/p"))
        assertEquals(AppScreen.Doc("/p"), IncomingRoute.DOCUMENT.screenFor("/p"))
        // 편집기가 쪽 그대로 보이는 문서(오피스·한글)는 편집기의 보기 모드로. 다른 갈래는 편집기를 보지 않는다.
        assertEquals(AppScreen.Editor("/p", "docx"), IncomingRoute.DOCUMENT.screenFor("/p", "docx"))
        assertEquals(AppScreen.Text("/p"), IncomingRoute.TEXT.screenFor("/p", "csv"))
        assertNull(IncomingRoute.PLAYER.screenFor("/p"))
        assertNull(IncomingRoute.NONE.screenFor("/p"))
    }

    @Test
    fun `ts 는 앞머리로 가른다`() {
        // TypeScript 는 글이다 — 영상으로 넘기면 재생기가 '깨진 파일' 로 끝낸다.
        val script = File(dir, "app.ts").apply { writeText("export const a: number = 1;\n") }
        assertEquals(IncomingRoute.TEXT, IncomingRoute.of(script))
        // MPEG-TS(188바이트 패킷의 동기 바이트)는 영상이다.
        val stream = File(dir, "clip.ts").apply {
            writeBytes(ByteArray(188 * 8) { i -> if (i % 188 == 0) 0x47 else 0x1F })
        }
        assertEquals(IncomingRoute.PLAYER, IncomingRoute.of(stream))
        // 캠코더의 `.mts` 는 이 앱이 틀지 못한다(기타) — TypeScript 면 글이다.
        val mts = File(dir, "util.mts").apply { writeText("export {}\n") }
        assertEquals(IncomingRoute.TEXT, IncomingRoute.of(mts))
    }
}
