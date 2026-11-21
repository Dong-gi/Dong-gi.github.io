package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 'Files에 저장'([IncomingSaves])의 판단 — 보낸 쪽이 적은 이름, 저장해도 되는 자리, 처음 폴더, 받은 목록, 결과 문장의 고르기.
 * 제공자에게 묻고 복사하는 일은 기기에서만 돈다.
 */
class IncomingSavesTest {

    private val platform: (String) -> String? = { mime ->
        mapOf(
            "application/pdf" to "pdf",
            "image/jpeg" to "jpg",
            "application/vnd.android.package-archive" to "apk",
            "application/x-weird" to "../x",
            "application/octet-stream" to "bin",
        )[mime]
    }

    private val roots = listOf("/storage/emulated/0", "/storage/1A2B-3C4D")

    @Test
    fun `이름은 마지막 조각만 다듬어 쓴다`() {
        assertEquals("사진.jpg", IncomingSaves.nameFor("사진.jpg", "image/jpeg", platform))
        // 경로를 적어 보내도 고른 폴더 밖을 가리키지 못한다.
        assertEquals("secret.db", IncomingSaves.nameFor("../../data/secret.db", null, platform))
        assertEquals("x.txt", IncomingSaves.nameFor("C:\\Users\\x.txt", null, platform))
        // 제어 문자·FAT 금지 글자·글 방향 글자.
        assertEquals("a_b_.txt", IncomingSaves.nameFor("a:b\u0001.txt", null, platform))
        assertEquals("a_fdp.apk", IncomingSaves.nameFor("a\u202Efdp.apk", null, platform))
        // 비었거나 점뿐이면 대신 이름 — 형식을 알면 확장자가 붙는다.
        assertEquals("file.pdf", IncomingSaves.nameFor(null, "application/pdf", platform))
        assertEquals("file", IncomingSaves.nameFor("..", null, platform))
        assertEquals("file.jpg", IncomingSaves.nameFor(" / ", "image/jpeg", platform))
    }

    /** 숨은 파일을 만들지 않는다 — `.nomedia` 하나가 그 폴더의 사진을 갤러리에서 모두 감춘다. */
    @Test
    fun `점으로 시작하는 이름은 밑줄로 바꾼다`() {
        assertEquals("_nomedia", IncomingSaves.nameFor(".nomedia", null, platform))
        assertEquals("_hidden.jpg", IncomingSaves.nameFor("...hidden.jpg", null, platform))
        assertEquals("_nomedia", IncomingSaves.nameFor("  .nomedia", null, platform))
        assertEquals("_nomedia", IncomingSaves.nameFor("dir/.nomedia", null, platform))
    }

    @Test
    fun `확장자가 없을 때만 형식의 확장자를 붙인다`() {
        assertEquals("download.pdf", IncomingSaves.nameFor("download", "application/pdf; charset=binary", platform))
        // 있으면 이름이 이긴다 — 이 앱이 모르는 확장자여도(보는 길과 다르다 — 사용자의 파일이다).
        assertEquals("report.xyz", IncomingSaves.nameFor("report.xyz", "application/pdf", platform))
        assertEquals("photo.png", IncomingSaves.nameFor("photo.png", "image/jpeg", platform))
        // 설치 파일도 이름 그대로 저장한다(열지 않을 뿐이다).
        assertEquals("app.apk", IncomingSaves.nameFor("app", "application/vnd.android.package-archive", platform))
        // 와일드카드·octet-stream·모양이 이상한 답은 붙이지 않는다.
        assertEquals("blob", IncomingSaves.nameFor("blob", "image/*", platform))
        assertEquals("blob", IncomingSaves.nameFor("blob", "application/octet-stream", platform))
        assertEquals("blob", IncomingSaves.nameFor("blob", "application/x-weird", platform))
        assertNull(IncomingSaves.extensionFor("IMAGE/JPEG ", { null }))
        assertEquals("jpg", IncomingSaves.extensionFor("IMAGE/JPEG ", platform))
    }

    @Test
    fun `긴 이름은 255바이트 안으로 자르고 확장자를 남긴다`() {
        val name = IncomingSaves.nameFor("가".repeat(200) + ".jpg", null, platform)
        assertTrue(name.toByteArray().size <= PathRules.MAX_NAME_BYTES)
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun `저장 볼륨의 폴더만 받는다`() {
        assertTrue(IncomingSaves.isAllowedDestination("/storage/emulated/0/Download", roots))
        assertTrue(IncomingSaves.isAllowedDestination("/storage/emulated/0", roots))
        assertTrue(IncomingSaves.isAllowedDestination("/storage/1A2B-3C4D/DCIM", roots))
        // 다른 앱의 자리 — 대소문자를 가리지 않는다.
        assertFalse(IncomingSaves.isAllowedDestination("/storage/emulated/0/Android/data/x.y", roots))
        assertFalse(IncomingSaves.isAllowedDestination("/storage/emulated/0/android/OBB", roots))
        assertTrue(IncomingSaves.isAllowedDestination("/storage/emulated/0/Android/media/x.y", roots))
        // 휴지통.
        assertFalse(IncomingSaves.isAllowedDestination("/storage/emulated/0/${TrashStore.DIR_NAME}", roots))
        assertFalse(IncomingSaves.isAllowedDestination("/storage/emulated/0/${TrashStore.DIR_NAME}/a", roots))
        // 볼륨 밖·앱 내부·이웃 경로.
        assertFalse(IncomingSaves.isAllowedDestination("/data/user/0/io.github.donggi.iroiroviewer/files", roots))
        assertFalse(IncomingSaves.isAllowedDestination("/storage/emulated/01/Download", roots))
        assertFalse(IncomingSaves.isAllowedDestination("/storage/self/primary", roots))
        assertFalse(IncomingSaves.isAllowedDestination("/storage/emulated/0/../0/Download", roots))
        // 볼륨 목록이 이상하게 와도(`/storage/` 밖의 뿌리) 받지 않는다.
        assertFalse(IncomingSaves.isAllowedDestination("/storage/x/a", listOf("/", "/storage")))
    }

    @Test
    fun `처음 폴더는 아직 쓸 수 있는 마지막 폴더 아니면 내려받기`() {
        val download = "/storage/emulated/0/Download"
        assertEquals(download, IncomingSaves.startDir(null, download) { true })
        assertEquals(download, IncomingSaves.startDir("", download) { true })
        assertEquals("/storage/emulated/0/Pictures", IncomingSaves.startDir("/storage/emulated/0/Pictures", download) { true })
        assertEquals(download, IncomingSaves.startDir("/storage/1A2B-3C4D/x", download) { false })
    }

    @Test
    fun `받은 목록은 겹친 것을 빼고 차례를 지키며 상한을 넘으면 받지 않는다`() {
        assertEquals(listOf("a", "b", "c"), IncomingSaves.distinctCapped(listOf("a", "b", "a", "c", "b"), { it }))
        assertEquals(listOf("a", "b"), IncomingSaves.distinctCapped(listOf("a", "b", "a"), { it }, max = 2))
        assertNull(IncomingSaves.distinctCapped(listOf("a", "b", "c"), { it }, max = 2))
        assertEquals(emptyList(), IncomingSaves.distinctCapped(emptyList<String>(), { it }))
    }

    /** 적힌 크기보다 짧게 끝나면 잘린 스트림, 크게 넘치면 끊는다. 모르면 막지 않는다(공간 지킴이 끊는다). */
    @Test
    fun `적힌 크기와 받은 바이트를 견준다`() {
        val ok = IncomingSaves.SizeVerdict.OK
        val mib = IncomingSaves.OVERFLOW_SLACK_BYTES
        assertEquals(ok, IncomingSaves.sizeVerdict(100, 100, ended = true))
        assertEquals(IncomingSaves.SizeVerdict.TRUNCATED, IncomingSaves.sizeVerdict(99, 100, ended = true))
        // 아직 읽는 중이면 짧은 것은 판정하지 않는다.
        assertEquals(ok, IncomingSaves.sizeVerdict(99, 100, ended = false))
        // 조금 넘는 것은 받는다(작은 파일은 1 MiB 까지, 큰 파일은 적힌 크기만큼까지).
        assertEquals(ok, IncomingSaves.sizeVerdict(100 + mib, 100, ended = true))
        assertEquals(IncomingSaves.SizeVerdict.OVERFLOW, IncomingSaves.sizeVerdict(101 + mib, 100, ended = false))
        assertEquals(ok, IncomingSaves.sizeVerdict(20 * mib, 10 * mib, ended = true))
        assertEquals(IncomingSaves.SizeVerdict.OVERFLOW, IncomingSaves.sizeVerdict(20 * mib + 1, 10 * mib, ended = false))
        // 크기를 모른다.
        assertEquals(ok, IncomingSaves.sizeVerdict(Long.MAX_VALUE / 2, -1, ended = true))
        assertEquals(ok, IncomingSaves.sizeVerdict(0, 0, ended = true))
    }

    @Test
    fun `content 만 받는다`() {
        assertTrue(IncomingSaves.acceptsScheme("content"))
        assertTrue(IncomingSaves.acceptsScheme("CONTENT"))
        assertFalse(IncomingSaves.acceptsScheme("file"))
        assertFalse(IncomingSaves.acceptsScheme("http"))
        assertFalse(IncomingSaves.acceptsScheme(null))
    }

    @Test
    fun `결과 문장의 고르기`() {
        fun finished(outcome: FileOpEngine.Outcome, paths: List<String>?) = FileOpManager.Finished(
            "r",
            FileOpManager.Kind.RECEIVE,
            outcome,
            paths?.let { FileOpManager.Extra.Received("/storage/emulated/0/Download", "Download", it) },
        )
        val one = "/storage/emulated/0/Download/사진 (2).jpg"
        assertEquals(
            FileOpText.ReceivedSummary.Saved("Download", 1, "사진 (2).jpg", 0),
            FileOpText.receivedSummary(finished(FileOpEngine.Outcome.Done(1, 0, 0), listOf(one))),
        )
        assertEquals(
            FileOpText.ReceivedSummary.Saved("Download", 2, null, 1),
            FileOpText.receivedSummary(finished(FileOpEngine.Outcome.Done(2, 0, 1), listOf(one, "/x/b.mp4"))),
        )
        // 하나도 못 놓았다.
        assertEquals(
            FileOpText.ReceivedSummary.Saved("", 0, null, 3),
            FileOpText.receivedSummary(finished(FileOpEngine.Outcome.Done(0, 0, 3), null)),
        )
        // 중간에 취소 — 이미 놓은 것은 남았다고 말한다.
        assertEquals(
            FileOpText.ReceivedSummary.Cancelled(1),
            FileOpText.receivedSummary(finished(FileOpEngine.Outcome.Cancelled, listOf(one))),
        )
        assertEquals(
            FileOpText.ReceivedSummary.Failed(FileOpEngine.Reason.NO_SPACE, 1, 2),
            FileOpText.receivedSummary(
                finished(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, "c", 1, 0, 2), listOf(one)),
            ),
        )
        // 공간으로 멈췄다 — 멈춘 항목부터는 '남음'(skipped)이지 '못 읽음' 이 아니다.
        assertEquals(
            FileOpText.ReceivedSummary.Failed(FileOpEngine.Reason.NO_SPACE, 1, 0, 3),
            FileOpText.receivedSummary(
                finished(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, "c", 1, 3, 0), listOf(one)),
            ),
        )
        // 다른 작업은 말하지 않는다.
        assertNull(
            FileOpText.receivedSummary(
                FileOpManager.Finished("c", FileOpManager.Kind.COPY, FileOpEngine.Outcome.Done(1, 0, 0)),
            ),
        )
    }
}
