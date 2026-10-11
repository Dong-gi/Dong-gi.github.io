package io.github.donggi.iroirobrowser.setup

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 설정 파일 zip 짓기([SetupZip]) — 순수 함수라 안드로이드 없이 돈다. 담은 바이트·차례·맨 위 폴더·경로 거르기. */
class SetupZipTest {

    private val time = 1_760_000_000_000L // 고정 — 시각이 시험을 흔들지 않게

    /** zip 을 앞에서부터(로컬 머리) 읽어 (이름, 내용)의 차례를 돌려준다. */
    private fun entries(zip: ByteArray): List<Pair<String, ByteArray>> {
        val out = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val e = input.nextEntry ?: break
                assertFalse(e.isDirectory, e.name)
                out += e.name to input.readBytes()
            }
        }
        return out
    }

    @Test
    fun 맨_위_폴더_아래에_받은_차례대로_바이트_그대로_담는다() {
        val files = listOf(
            "README.txt" to "한글 설명\n둘째 줄\n".toByteArray(Charsets.UTF_8),
            "windows-wifi.bat" to "@echo off\r\nREM x\r\n".toByteArray(Charsets.US_ASCII),
            "server/lib/cert.sh" to byteArrayOf(0, 1, 2, 0x0D, 0x0A, 0x0A, -1),
            "empty.txt" to ByteArray(0),
        )
        val got = entries(SetupZip.build("web-tether-pc", files, time))
        assertEquals(files.map { "web-tether-pc/${it.first}" }, got.map { it.first })
        for ((want, have) in files.zip(got)) assertContentEquals(want.second, have.second, want.first)
    }

    @Test
    fun 중앙_디렉터리도_같은_이름을_적고_경로는_폴더_밖으로_나가지_않는다() {
        // 윈도 탐색기·unzip 은 중앙 디렉터리를 읽는다 — 앞의 로컬 머리만 맞는 것으로는 모자라다.
        val zip = SetupZip.build("web-proxy-server", listOf("README.md" to byteArrayOf(1), "server/verify.sh" to byteArrayOf(2)), time)
        val file = File.createTempFile("setup", ".zip").apply { deleteOnExit() }
        try {
            file.writeBytes(zip)
            ZipFile(file).use { z ->
                val names = z.entries().toList().map { it.name }
                assertEquals(listOf("web-proxy-server/README.md", "web-proxy-server/server/verify.sh"), names)
                for (name in names) {
                    assertFalse(name.startsWith("/") || name.contains('\\') || name.contains(':'), name)
                    assertTrue(name.split('/').none { it.isEmpty() || it == "." || it == ".." }, name)
                    assertTrue(name.startsWith("web-proxy-server/"), name)
                }
                assertContentEquals(byteArrayOf(2), z.getInputStream(z.getEntry("web-proxy-server/server/verify.sh")).readBytes())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun 이상한_경로와_겹친_이름은_짓지_않는다() {
        val bad = listOf(
            "", "/etc/passwd", "../x", "a/../b", "a/..", "./a", "a/.", "a//b", "a/", "...", "a\\b", "C:x", "한글.txt", "a b",
            "x".repeat(201),
        )
        for (path in bad) {
            assertFailsWith<IllegalArgumentException>(path) { SetupZip.checkPath(path) }
            assertFailsWith<IllegalArgumentException>(path) { SetupZip.build("top", listOf(path to ByteArray(0)), time) }
        }
        for (top in listOf("", "..", ".", "a/b", "/a", "a b")) {
            assertFailsWith<IllegalArgumentException>(top) { SetupZip.build(top, listOf("a" to ByteArray(0)), time) }
        }
        assertFailsWith<IllegalArgumentException> { SetupZip.build("top", listOf("a" to ByteArray(0), "a" to ByteArray(1)), time) }
        // 보통 이름은 된다.
        for (ok in listOf("README.md", "server/conf/squid-plain.conf.tmpl", "a.b-c_d", ".hidden", "v1..2")) SetupZip.checkPath(ok)
    }
}
