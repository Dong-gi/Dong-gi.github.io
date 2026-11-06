package io.github.donggi.iroiroviewer.io

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 설치 파일(APK)인가([ApkFiles]) — 이름과 **내용**(ZIP 의 가운데 목록에 `AndroidManifest.xml`). 그리고 그 판단이 '다른 앱으로 열기'·공유의
 * 관문(`ExternalOpen.open`·`ShareHelper.share`)에 걸려 있는가(배선 — 이 저장소의 관례대로 소스를 글로 읽는다).
 *
 * 표본은 이 시험이 그 자리에서 짓는 ZIP 이다(진짜 APK 를 쓰지 않는다 — 설치 관리자가 찾는 이름만 있으면 같은 길이다).
 */
class ApkFilesTest {

    private val dir: File = Files.createTempDirectory("apkfiles").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun zip(name: String, entries: List<String>, comment: String? = null): File {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for (e in entries) {
                z.putNextEntry(ZipEntry(e))
                z.write(e.toByteArray())
                z.closeEntry()
            }
            if (comment != null) z.setComment(comment)
        }
        return File(dir, name).apply { writeBytes(out.toByteArray()) }
    }

    @Test
    fun 이름이_apk_면_설치_파일이다() {
        assertTrue(ApkFiles.nameSaysApk("game.apk"))
        assertTrue(ApkFiles.nameSaysApk("GAME.APK"))
        assertFalse(ApkFiles.nameSaysApk("game.zip"))
        assertFalse(ApkFiles.nameSaysApk("apk"))
        // 내용이 무엇이든 이름이 말하면 설치 파일로 본다(설치 관리자로 가는 형식이 이름에서 나온다).
        assertTrue(ApkFiles.isApk(File(dir, "x.apk").apply { writeText("not a zip") }))
    }

    @Test
    fun 이름을_바꾼_APK_는_내용으로_알아본다() {
        val apk = listOf("AndroidManifest.xml", "classes.dex", "resources.arsc")
        for (name in listOf("invoice.pdf", "photo.jpg", "a.zip", "statement", "movie.rmvb", "x.cbz")) {
            assertTrue(ApkFiles.isApk(zip(name, apk)), name)
        }
        // 가운데 목록의 뒤쪽에 있어도.
        assertTrue(ApkFiles.isApk(zip("late.bin", (1..300).map { "res/raw/f$it.txt" } + "AndroidManifest.xml")))
        // 끝 레코드 뒤에 주석이 길어도(끝에서부터 찾는다).
        assertTrue(ApkFiles.isApk(zip("comment.bin", apk, comment = "c".repeat(60_000))))
    }

    @Test
    fun 설치_관리자가_찾는_이름만_센다() {
        // 소스 묶음의 매니페스트 — 뿌리가 아니다.
        assertFalse(ApkFiles.isApk(zip("project.zip", listOf("app/src/main/AndroidManifest.xml", "build.gradle"))))
        // 대소문자를 가린다(libziparchive 는 바이트로 견준다).
        assertFalse(ApkFiles.isApk(zip("lower.zip", listOf("androidmanifest.xml"))))
        assertFalse(ApkFiles.isApk(zip("plain.zip", listOf("a.txt", "b/c.png"))))
        // ZIP 이 아닌 것·빈 것·잘린 것.
        assertFalse(ApkFiles.isApk(File(dir, "doc.pdf").apply { writeText("%PDF-1.7\n...") }))
        assertFalse(ApkFiles.isApk(File(dir, "empty.bin").apply { writeBytes(ByteArray(0)) }))
        val whole = zip("whole.bin", listOf("AndroidManifest.xml")).readBytes()
        assertFalse(ApkFiles.isApk(File(dir, "cut.bin").apply { writeBytes(whole.copyOf(whole.size - 30)) }))
        // 없는 파일은 이름만 본다.
        assertFalse(ApkFiles.isApk(File(dir, "missing.zip")))
    }

    /** ZIP64(항목이 65,535개를 넘는다) — 끝 레코드의 위치표를 따라 가운데 목록을 찾는다. */
    @Test
    fun ZIP64_도_따라간다() {
        val names = (0 until 66_000).map { "f$it" } + "AndroidManifest.xml"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.setLevel(0)
            for (n in names) {
                z.putNextEntry(ZipEntry(n))
                z.closeEntry()
            }
        }
        assertTrue(ApkFiles.isApk(File(dir, "big.bin").apply { writeBytes(out.toByteArray()) }))
    }

    /** 가운데 목록이 상한보다 크다고 적혀 있으면 **설치 파일로 친다** — 증명하지 못한 것을 설치 관리자에 넘기지 않는다. */
    @Test
    fun 상한을_넘는_가운데_목록은_설치_파일로_친다() {
        // ZIP64 끝 레코드(항목 200만 개, 가운데 목록 1바이트 자리 0) + 위치표 + 끝 레코드(넘침 표시).
        val zip64 = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06064b50).putLong(44).putShort(45).putShort(45).putInt(0).putInt(0)
            .putLong(2_000_000).putLong(2_000_000).putLong(1).putLong(0).array()
        val locator = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x07064b50).putInt(0).putLong(0).putInt(1).array()
        val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06054b50).putShort(0).putShort(0).putShort(-1).putShort(-1).putInt(-1).putInt(-1).putShort(0).array()
        assertTrue(ApkFiles.isApk(File(dir, "huge.bin").apply { writeBytes(zip64 + locator + eocd) }))
    }

    /** 받은 것일 때만 막는다 — 파일 목록에서 고른 APK 의 설치 흐름은 그대로다. 받은 것이 아니면 디스크를 읽지도 않는다. */
    @Test
    fun 받은_것인지에_따라_가른다() {
        assertFalse(ApkFiles.refusesHandOff(received = false) { throw AssertionError("받은 것이 아니면 읽지 않는다") })
        assertTrue(ApkFiles.refusesHandOff(received = true) { true })
        assertFalse(ApkFiles.refusesHandOff(received = true) { false })
    }

    /** 배선 — '다른 앱으로 열기' 와 공유의 관문이 받은 사본을 이 판단으로 거른다. 파일 목록의 경로는 받은 사본이 아니다. */
    @Test
    fun 다른_앱으로_열기와_공유의_관문이_받은_설치_파일을_거른다() {
        val open = File("src/main/kotlin/io/github/donggi/iroiroviewer/io/ExternalOpen.kt").readText()
        val body = open.substringAfter("fun open(context: Context, path: String, mode: Mode = Mode.VIEW): Result {")
        val guard = body.indexOf("ApkFiles.refusesHandOff(IncomingFiles.isCopy(context, path)) { ApkFiles.isApk(File(path)) }")
        assertTrue(guard >= 0, "ExternalOpen.open 이 받은 설치 파일을 거르지 않는다")
        assertTrue(guard < body.indexOf("ShareHelper.uriOf(context, path)"), "URI 를 만든 뒤에 거른다")
        val share = File("src/main/kotlin/io/github/donggi/iroiroviewer/io/ShareHelper.kt").readText()
            .substringAfter("fun share(context: Context, paths: List<String>, chooserTitle: String): Boolean {")
        val filter = share.indexOf("ApkFiles.refusesHandOff(IncomingFiles.isCopy(context, p)) { ApkFiles.isApk(File(p)) }")
        assertTrue(filter >= 0 && filter < share.indexOf("uriOf(context, it)"), "공유가 받은 설치 파일을 거르지 않는다")
    }
}
