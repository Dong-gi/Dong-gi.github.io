package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 다른 앱이 넘긴 파일의 이름과 자리([IncomingFiles]). 제공자에게 묻는 일은 기기에서만 돌지만, 그 답을 이름·경로로 바꾸는 규칙은
 * 여기서 박는다 — 보낸 쪽이 적는 글이라 모양이 제각각이다.
 */
class IncomingFilesTest {

    private val platform: (String) -> String? = { mime ->
        mapOf("application/pdf" to "pdf", "image/jpeg" to "jpg", "text/plain" to "txt", "application/x-foo" to "foo")[mime]
    }

    @Test
    fun `이름은 마지막 조각만 다듬어 쓴다`() {
        assertEquals("a.pdf", IncomingFiles.nameFor("a.pdf", null, platform))
        // 경로를 이름에 적어 보내도 사본 폴더 밖을 가리키지 못한다.
        assertEquals("secret.db", IncomingFiles.nameFor("../../databases/secret.db", null, platform))
        assertEquals("x.txt", IncomingFiles.nameFor("C:\\Users\\x.txt", null, platform))
        // 파일시스템이 받지 않는 글자·제어 문자는 바꾼다(PathRules.sanitize).
        assertEquals("a_b_.txt", IncomingFiles.nameFor("a:b\u0001.txt", null, platform))
        // 이름이 없거나 점뿐이면 대신 이름 — 형식을 알면 확장자가 붙는다.
        assertEquals("file.pdf", IncomingFiles.nameFor(null, "application/pdf", platform))
        assertEquals("file", IncomingFiles.nameFor("..", null, platform))
        assertEquals("file.jpg", IncomingFiles.nameFor(" ", "image/jpeg", platform))
    }

    @Test
    fun `이 앱이 모르는 이름에만 형식의 확장자를 붙인다`() {
        // 확장자가 없다 — 뷰어는 확장자로 종류를 정한다.
        assertEquals("download.pdf", IncomingFiles.nameFor("download", "application/pdf", platform))
        // 모르는 확장자다.
        assertEquals("report.bin.pdf", IncomingFiles.nameFor("report.bin", "application/pdf; charset=binary", platform))
        // 아는 확장자면 이름이 이긴다 — 형식 문자열은 보낸 앱이 대충 적는 일이 많다.
        assertEquals("photo.png", IncomingFiles.nameFor("photo.png", "image/jpeg", platform))
        assertEquals("notes.md", IncomingFiles.nameFor("notes.md", "text/plain", platform))
        // 붙여도 이 앱이 모르는 종류면 붙이지 않는다 — 이름만 길어진다.
        assertEquals("blob", IncomingFiles.nameFor("blob", "application/x-foo", platform))
        // 와일드카드·빈 형식은 형식이 아니다.
        assertEquals("blob", IncomingFiles.nameFor("blob", "*/*", platform))
        assertEquals("blob", IncomingFiles.nameFor("blob", "", platform))
    }

    @Test
    fun `플랫폼 표가 모를 수 있는 한글·만화 형식은 우리가 안다`() {
        val none: (String) -> String? = { null }
        assertEquals("hwp", IncomingFiles.extensionFor("application/haansofthwp", none))
        assertEquals("hwpx", IncomingFiles.extensionFor("application/hwp+zip", none))
        assertEquals("cbz", IncomingFiles.extensionFor("application/vnd.comicbook+zip", none))
        assertEquals("md", IncomingFiles.extensionFor("Text/Markdown", none))
        assertNull(IncomingFiles.extensionFor("application/octet-stream", none))
    }

    @Test
    fun `저장소 문서 id 를 볼륨의 경로로 옮긴다`() {
        val volumes = mapOf("primary" to "/storage/emulated/0", "1A2B-3C4D" to "/storage/1A2B-3C4D")
        val dir: (String) -> String? = { volumes[it] }
        assertEquals("/storage/emulated/0/Download/a.pdf", IncomingFiles.storagePathOf("primary:Download/a.pdf", dir))
        assertEquals("/storage/1A2B-3C4D/Music/b.mp3", IncomingFiles.storagePathOf("1A2B-3C4D:Music/b.mp3", dir))
        assertEquals("/storage/emulated/0", IncomingFiles.storagePathOf("primary:", dir))
        // 모르는 볼륨·모양이 아닌 id 는 경로가 없다(그때는 사본이다).
        assertNull(IncomingFiles.storagePathOf("9999-0000:x", dir))
        assertNull(IncomingFiles.storagePathOf("primary", dir))
        assertNull(IncomingFiles.storagePathOf(":x", dir))
    }

    @Test
    fun `남의 제공자 URI 안에 통째로 든 저장소 경로를 찾는다`() {
        // Files by Google 의 꼴 — 풀린 경로 안에 file:///storage/… 가 들어 있다.
        assertEquals(
            "/storage/emulated/0/Movies/a.mp4",
            IncomingFiles.embeddedPath("/1/file:///storage/emulated/0/Movies/a.mp4"),
        )
        assertEquals("/storage/emulated/0/x.txt", IncomingFiles.embeddedPath("/root/storage/emulated/0/x.txt"))
        // 빗금까지 맞아야 한다 — `device_storage/` 는 저장소 경로가 아니다.
        assertNull(IncomingFiles.embeddedPath("/device_storage/Download/a.pdf"))
        assertNull(IncomingFiles.embeddedPath(null))
    }

    @Test
    fun `권한자 앞의 사용자 번호는 떼고 견준다`() {
        // ContentResolver 는 `0@` 를 떼고 같은 사용자의 제공자를 연다 — 붙은 채로 견주면 우리 제공자가 남의 것으로 보인다.
        assertEquals("io.github.donggi.iroiroviewer.files", IncomingFiles.bareAuthority("0@io.github.donggi.iroiroviewer.files"))
        assertEquals("media", IncomingFiles.bareAuthority("10@media"))
        assertEquals("com.android.externalstorage.documents", IncomingFiles.bareAuthority("com.android.externalstorage.documents"))
    }

    @Test
    fun `제자리는 저장 볼륨 아래만이다`() {
        assertTrue(IncomingFiles.isUnderStorage("/storage/emulated/0/Download/a.pdf"))
        assertFalse(IncomingFiles.isUnderStorage("/data/user/0/io.github.donggi.iroiroviewer/databases/iroiro.db"))
        assertFalse(IncomingFiles.isUnderStorage("/storagex/a"))
        assertFalse(IncomingFiles.isUnderStorage("/storage"))
    }
}
