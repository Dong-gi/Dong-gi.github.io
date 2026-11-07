package io.github.donggi.iroiroviewer

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **다른 앱이 넘긴 설치 파일(APK)은 열지 않는다**([refusalOf]). 넘긴 것을 '다른 앱으로 열기' 로 설치 관리자에 넘기면
 * 설치 관리자는 그것을 이 앱이 보낸 것으로 받는다 — 권한 없는 앱이 이 앱의 설치 권한을 빌려 쓴다.
 *
 * 판단(이름·내용)은 순수 함수로 보고, 받는 화면이 그 판단을 **어느 화면으로 보내기 전에** 부르는지(새로 받을 때·되살릴 때)는 소스를 글로
 * 읽어 본다(이 저장소의 관례 — `ReceiveFiltersTest`).
 */
class ReceivedApkTest {

    private val dir: File = Files.createTempDirectory("received-apk").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun zip(name: String, vararg entries: String): File {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for (e in entries) {
                z.putNextEntry(ZipEntry(e))
                z.write(1)
                z.closeEntry()
            }
        }
        return File(dir, name).apply { writeBytes(out.toByteArray()) }
    }

    @Test
    fun 설치_파일이면_이름이든_내용이든_열지_않는다() {
        assertEquals(R.string.open_refused_apk, refusalOf(File(dir, "app.apk").apply { writeText("x") }))
        // 이름을 바꾼 APK — 문서·그림·압축·모르는 형식으로 와도.
        for (name in listOf("명세서.pdf", "photo.jpg", "a.zip", "book.cbz", "statement", "x.bin")) {
            assertEquals(R.string.open_refused_apk, refusalOf(zip(name, "AndroidManifest.xml", "classes.dex")), name)
        }
    }

    @Test
    fun 설치_파일이_아니면_그대로_연다() {
        assertNull(refusalOf(zip("photos.zip", "a.jpg", "b.jpg")))
        assertNull(refusalOf(zip("source.zip", "app/src/main/AndroidManifest.xml")))
        assertNull(refusalOf(File(dir, "a.pdf").apply { writeText("%PDF-1.7") }))
        assertNull(refusalOf(File(dir, "memo.txt").apply { writeText("hello") }))
    }

    /** 받는 화면이 **화면을 고르기 전에** 거른다 — 새로 받을 때도, 프로세스가 죽었다 살아나 되살릴 때도. 거르면 사본과 되살릴 경로를 남기지 않는다. */
    @Test
    fun 받는_화면이_어느_화면으로_보내기_전에_거른다() {
        val source = File("src/main/kotlin/io/github/donggi/iroiroviewer/OpenActivity.kt").readText()
        val receive = source.substringAfter("private suspend fun receive(").substringBefore("private fun handToPlayer(")
        // 되살릴 때.
        val restore = receive.substringBefore("if (request == null)")
        assertTrue(restore.indexOf("refusalOf(File(r.path))") in 0 until restore.indexOf("_state.value = r"), "되살린 경로를 거르지 않는다")
        // 새로 받을 때 — 갈래(IncomingRoute)를 정하고 상태를 세우기 전에.
        val fresh = receive.substringAfter("is IncomingFiles.Outcome.Ok ->")
        val check = fresh.indexOf("refusalOf(File(path))")
        assertTrue(check >= 0, "새로 받은 것을 거르지 않는다")
        assertTrue(check < fresh.indexOf("IncomingRoute.of(File(path))"), "갈래를 정한 뒤에 거른다")
        assertTrue(check < fresh.indexOf("handToPlayer(ready)"), "재생기에 넘긴 뒤에 거른다")
        // 거를 때 — 사본을 지우고 되살릴 경로를 지운다.
        val refuse = source.substringAfter("private fun refuse(").substringBefore("\n    }")
        assertTrue("discardCopy()" in refuse && "saved.remove<String>(KEY_PATH)" in refuse && "State.Failed(message)" in refuse, refuse)
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue("<string name=\"open_refused_apk\">다른 앱이 보낸 설치 파일은 열지 않습니다</string>" in strings)
    }

    /**
     * 받은 화면의 이름은 보낸 쪽이 적은 글이다 — 방향 제어 글자(U+202E 등)를 **보이는 표시로** 적는다(`PathNames.forDisplay` — 양방향 제어 문자로
     * 확장자를 위장할 수 있다). 받은 사본의 이름은 이미 `_` 로 바뀌지만(`PathRules.sanitize`) 제자리로 연
     * 파일은 원래 이름 그대로다.
     */
    @Test
    fun 받은_화면은_보일_이름을_적는다() {
        val source = File("src/main/kotlin/io/github/donggi/iroiroviewer/OpenActivity.kt").readText()
        val screen = source.substringAfter("private fun NoViewerScreen(").substringBefore("\n}\n")
        assertTrue("name = PathNames.forDisplay(File(path).name)" in screen, screen.take(400))
    }
}
