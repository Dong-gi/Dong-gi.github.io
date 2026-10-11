package io.github.donggi.iroirobrowser.setup

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * zip 에 담는 자산(`assets/setup/…`)의 규칙 — 파일 목록이 자산 폴더와 같은가, 줄 끝·인코딩이 받는 쪽에 맞는가, 지은 zip 이 바이트를
 * 그대로 담는가. 줄 끝이 틀리면 받는 쪽에서 소리 없이 깨진다: cmd.exe 는 LF 만 있는 배치 파일을 잘못 읽고, bash 는 CRLF 스크립트의 `\r` 을
 * 명령의 일부로 읽는다(`set: pipefail: invalid option name`). 저장소의 `.gitattributes`(`* eol=lf`, `*.bat eol=crlf`)가 체크아웃을 지키고
 * 이 시험이 그 결과를 본다.
 */
class SetupAssetsTest {

    private val assets = File("src/main/assets")

    private fun filesUnder(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.sorted().toList()

    private fun bytes(bundle: SetupBundle, path: String): ByteArray = File(assets, "${bundle.assetDir}/$path").readBytes()

    @Test
    fun 묶음의_파일_목록이_자산_폴더와_같다() {
        for (bundle in SetupBundle.entries) {
            val dir = File(assets, bundle.assetDir)
            assertTrue(dir.isDirectory, dir.absolutePath)
            assertEquals(filesUnder(dir), bundle.files.sorted(), bundle.name)
            assertEquals(bundle.files.size, bundle.files.toSet().size, bundle.name)
            assertEquals("${bundle.topFolder}.zip", bundle.zipName)
            for (path in bundle.files) SetupZip.checkPath(path) // ASCII 이름 — 어느 압축 프로그램에서도 깨지지 않게
        }
        // 자산의 setup/ 에는 두 묶음 말고 아무것도 없다(빠진 묶음·떠도는 파일이 없게).
        val all = filesUnder(File(assets, "setup")).toSet()
        val listed = SetupBundle.entries.flatMap { b -> b.files.map { "${b.assetDir.removePrefix("setup/")}/$it" } }.toSet()
        assertEquals(listed, all)
    }

    @Test
    fun 배치_파일은_ASCII_와_CRLF_다() {
        val bat = bytes(SetupBundle.TETHER_PC, "windows-wifi.bat")
        assertTrue(bat.all { it >= 0 }, "ASCII 가 아닌 바이트 — cmd.exe 는 배치 파일을 콘솔의 코드 페이지로 읽는다")
        for (i in bat.indices) {
            if (bat[i] == '\n'.code.toByte()) assertTrue(i > 0 && bat[i - 1] == '\r'.code.toByte(), "LF 만 있는 줄 끝(바이트 $i)")
            if (bat[i] == '\r'.code.toByte()) assertTrue(i + 1 < bat.size && bat[i + 1] == '\n'.code.toByte(), "CR 만 있는 줄 끝(바이트 $i)")
        }
        assertTrue(bat.size >= 2 && bat[bat.size - 2] == '\r'.code.toByte() && bat.last() == '\n'.code.toByte(), "마지막 줄도 CRLF")
    }

    @Test
    fun 셸_템플릿_글은_LF_와_UTF8_이고_BOM_이_없다() {
        var seen = 0
        for (bundle in SetupBundle.entries) {
            for (path in bundle.files.filter { it.endsWith(".sh") || it.endsWith(".tmpl") || it.endsWith(".md") || it.endsWith(".txt") }) {
                val data = bytes(bundle, path)
                assertFalse(data.contains('\r'.code.toByte()), "$path 에 CR")
                assertTrue(data.isNotEmpty() && data.last() == '\n'.code.toByte(), "$path 의 마지막 줄바꿈")
                assertFalse(data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte(), "$path 에 BOM")
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)) // 깨진 UTF-8 이면 던진다
                seen++
            }
        }
        assertEquals(11, seen) // 서버 묶음 아홉 전부와 README.txt·macOS 스크립트 — 목록이 바뀌면 여기도 본다
    }

    @Test
    fun 실제_자산으로_지은_zip_은_파일을_바이트_그대로_담는다() {
        for (bundle in SetupBundle.entries) {
            val files = bundle.files.map { it to bytes(bundle, it) }
            val zip = SetupZip.build(bundle.topFolder, files)
            val got = ArrayList<Pair<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(zip)).use { input ->
                while (true) {
                    val e = input.nextEntry ?: break
                    got += e.name to input.readBytes()
                }
            }
            assertEquals(files.map { "${bundle.topFolder}/${it.first}" }, got.map { it.first }, bundle.name)
            for ((want, have) in files.zip(got)) assertContentEquals(want.second, have.second, "${bundle.name} ${want.first}")
        }
    }

    @Test
    fun 서버_스크립트는_예시_도메인과_bash_로_도는_안내만_적는다() {
        val setup = String(bytes(SetupBundle.PROXY_SERVER, "server/setup-squid.sh"), Charsets.UTF_8)
        assertTrue(setup.contains("--domain proxy.example.com"), "사용법의 도메인은 예시여야 한다")
        // zip 은 실행 비트를 싣지 못한다 — 스크립트를 경로로 곧바로 돌리라는 안내가 남으면 'command not found' 다.
        for (stale in listOf("sudo ./", "sudo \$0", "sudo \${SCRIPT_DIR}/")) assertFalse(setup.contains(stale), stale)
        for (hint in listOf("sudo bash setup-squid.sh", "sudo bash \$0", "sudo bash \${SCRIPT_DIR}/verify.sh", "sudo bash \${SCRIPT_DIR}/uninstall.sh")) {
            assertTrue(setup.contains(hint), hint)
        }
        val readme = String(bytes(SetupBundle.PROXY_SERVER, "README.md"), Charsets.UTF_8)
        for (hint in listOf("sudo bash setup-squid.sh", "sudo bash verify.sh", "sudo bash uninstall.sh", "Web › 설정 › 프록시")) assertTrue(readme.contains(hint), hint)
        // 확장(Edge·PAC)은 이제 쓰지 않는다 — 클라이언트는 Web 이다. 서버 파일의 안내·주석도 그렇다.
        for (gone in listOf("Edge", "PAC", "MV3")) assertFalse(readme.contains(gone), gone)
        for (name in SetupBundle.PROXY_SERVER.files.filter { it.startsWith("server/") }) {
            val text = String(bytes(SetupBundle.PROXY_SERVER, name), Charsets.UTF_8)
            for (gone in listOf("Edge", "Chrome", "PAC", "sudo ./")) assertFalse(text.contains(gone), "$name: $gone")
        }
    }

    @Test
    fun PC_스크립트의_머리말은_README_를_가리키고_저장소_이야기를_하지_않는다() {
        for (name in listOf("windows-wifi.bat", "macos-wifi.sh")) {
            val text = String(bytes(SetupBundle.TETHER_PC, name), Charsets.US_ASCII)
            assertTrue(text.contains("README.txt"), name)
            for (gone in listOf("CLAUDE.md", "Repositories/", "git-ignored", "(pc/)")) assertFalse(text.contains(gone), "$name: $gone")
            // SOCKS5 는 1080 고정 — 앱이 옮기지 않는다(CLAUDE.md '테더링').
            assertTrue(text.contains("socks5://192.168.49.1:"), name)
        }
        val readme = String(bytes(SetupBundle.TETHER_PC, "README.txt"), Charsets.UTF_8)
        for (asset in listOf("tun2proxy-x86_64-pc-windows-msvc.zip", "tun2proxy-aarch64-apple-darwin.zip", "tun2proxy-x86_64-apple-darwin.zip")) {
            assertTrue(readme.contains(asset), asset)
        }
        assertTrue(readme.contains("sudo bash macos-wifi.sh"))
        val bat = String(bytes(SetupBundle.TETHER_PC, "windows-wifi.bat"), Charsets.US_ASCII)
        // 관리자 판정은 fltmc — net session 은 Server 서비스가 꺼진 PC 에서 관리자여도 실패해 권한 요청이 되풀이됐다.
        assertTrue(bat.contains("fltmc >nul 2>&1") && !bat.contains("net session >nul"))
        // 관리자 창은 스크립트가 끝나면 닫힌다 — tun2proxy 가 없거나 오류로 끝나면 멈춰서 글이 보이게.
        assertTrue(bat.contains("if not exist \"%~dp0tun2proxy.exe\"") && bat.contains("if errorlevel 1 pause"))
        assertFalse(bat.contains("enabledelayedexpansion"), "쓰지 않는데 폴더 경로의 ! 를 지운다")
        val mac = String(bytes(SetupBundle.TETHER_PC, "macos-wifi.sh"), Charsets.US_ASCII)
        assertTrue(mac.contains("trap cleanup EXIT\n") && !mac.contains("trap cleanup EXIT INT"), "정리는 끝날 때 한 번")
    }
}
