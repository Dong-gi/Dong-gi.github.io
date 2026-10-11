package io.github.donggi.iroiroviewer.io

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 글 편집의 규칙 — 어디를 고칠 수 있는가([TextSaver.placeOf]), '밖에서 바뀌었는가' 의 표([FileStamp]), 새 실패 까닭의
 * 문장. 실제로 쓰는 일(임시본·rename·휴지통 사본)은 파일시스템이 답하므로 계측 시험(`TextSaverTest`)이 본다.
 */
class TextSaverRulesTest {

    @Test
    fun `저장 볼륨의 보통 파일만 고친다`() {
        assertEquals(TextSaver.Place.OK, TextSaver.placeOf("/storage/emulated/0/Documents/메모.txt", isFile = true, writable = true))
        assertEquals(TextSaver.Place.OK, TextSaver.placeOf("/storage/1A2B-3C4D/notes/a.md", isFile = true, writable = true))
        // 다른 앱이 넘긴 사본은 앱 캐시다 — 곁에 써도 사용자가 찾아갈 수 없고, 캐시 정리가 지운다.
        val cache = "/data/user/0/io.github.donggi.iroiroviewer/cache/share"
        assertEquals(TextSaver.Place.OUTSIDE, TextSaver.placeOf("$cache/inbox/9a8b/받은.txt", isFile = true, writable = true))
        assertEquals(TextSaver.Place.OUTSIDE, TextSaver.placeOf("$cache/remote/1-x/원격.txt", isFile = true, writable = true))
        // 앱 내부·이웃 이름·볼륨 루트 위.
        assertEquals(TextSaver.Place.OUTSIDE, TextSaver.placeOf("/data/user/0/io.github.donggi.iroiroviewer/files/a.txt", true, true))
        assertEquals(TextSaver.Place.OUTSIDE, TextSaver.placeOf("/storagex/a.txt", isFile = true, writable = true))
        // 휴지통 안은 지운 것이다 — 고치지 않는다.
        assertEquals(
            TextSaver.Place.OUTSIDE,
            TextSaver.placeOf("/storage/emulated/0/${TrashStore.DIR_NAME}/1f2e", isFile = true, writable = true),
        )
    }

    @Test
    fun `보통 파일이 아니거나 쓸 수 없으면 그 까닭이다`() {
        assertEquals(TextSaver.Place.NOT_FILE, TextSaver.placeOf("/storage/emulated/0/Documents", isFile = false, writable = true))
        assertEquals(TextSaver.Place.READ_ONLY, TextSaver.placeOf("/storage/1A2B-3C4D/a.txt", isFile = true, writable = false))
        // 자리가 먼저다 — 캐시의 읽기 전용 파일은 '읽기 전용' 이 아니라 '밖' 이다.
        assertEquals(TextSaver.Place.OUTSIDE, TextSaver.placeOf("/data/local/tmp/a.txt", isFile = true, writable = false))
    }

    @Test
    fun `표는 크기와 수정 시각이고 하나만 달라도 다르다`() {
        val dir = Files.createTempDirectory("stamp").toFile()
        try {
            val f = File(dir, "a.txt").apply { writeText("가나다") }
            f.setLastModified(1_700_000_000_000)
            val first = FileStamp.of(f)
            assertEquals(FileStamp(9, 1_700_000_000_000), first)
            // 같은 크기로 고쳐 써도 시각이 바뀌면 다르다.
            f.writeText("라마바")
            f.setLastModified(1_700_000_005_000)
            assertNotEquals(first, FileStamp.of(f))
            // 지워진 파일·폴더는 표가 없다 — 저장은 그것을 '바뀌었다' 로 본다.
            assertNull(FileStamp.of(dir))
            f.delete()
            assertNull(FileStamp.of(f))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 새 까닭 둘은 제 문장이다 — 잘라 저장의 '바뀌었다'(다시 열어 고르세요)와 섞이면 편집에서 엉뚱한 일을 시킨다. */
    @Test
    fun `글 편집의 실패 까닭은 제 문장이다`() {
        val elsewhere = FileOpText.reasonRes(FileOpEngine.Reason.CHANGED_ELSEWHERE)
        val previous = FileOpText.reasonRes(FileOpEngine.Reason.PREVIOUS_NOT_KEPT)
        for (other in FileOpEngine.Reason.entries - FileOpEngine.Reason.CHANGED_ELSEWHERE) {
            assertNotEquals(FileOpText.reasonRes(other), elsewhere, "$other")
        }
        for (other in FileOpEngine.Reason.entries - FileOpEngine.Reason.PREVIOUS_NOT_KEPT) {
            assertNotEquals(FileOpText.reasonRes(other), previous, "$other")
        }
        assertEquals(R.string.io_error_changed_elsewhere, elsewhere)
        assertEquals(R.string.io_error_previous_not_kept, previous)
    }
}
