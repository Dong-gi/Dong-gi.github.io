package io.github.donggi.iroirobrowser.setup

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 앱이 공용 '내려받기' 에 zip 으로 내어 주는 설정 파일 묶음 — 자산의 폴더(`assets/setup/…`), zip 의 이름과 맨 위 폴더, 담을 파일과 그 차례.
 * 파일을 손으로 적는 것은 zip 의 차례를 정하고 빠진 파일이 소리 없이 넘어가지 않게 하려는 것이다 — 시험(`SetupAssetsTest`)이 자산 폴더의 실제
 * 파일과 견준다.
 */
enum class SetupBundle(val assetDir: String, val zipName: String, val topFolder: String, val files: List<String>) {

    /** 설정 › 프록시의 '서버 구성 파일 받기' — 프록시 서버를 세우는 `server/`(옛 chrome-proxy-extension 에서 옮겼다)와 README. */
    PROXY_SERVER(
        "setup/proxy-server", "web-proxy-server.zip", "web-proxy-server",
        listOf(
            "README.md",
            "server/setup-squid.sh", "server/verify.sh", "server/uninstall.sh", "server/lib/cert.sh",
            "server/conf/certbot-deploy-hook.sh.tmpl", "server/conf/squid-native-tls.conf.tmpl",
            "server/conf/squid-plain.conf.tmpl", "server/conf/stunnel-squid.conf.tmpl",
        ),
    ),

    /** 테더링 › 다른 기기에서의 'PC 연결 파일 받기' — 시작 스크립트 둘과 README. tun2proxy·wintun 은 담지 않는다(남의 실행 파일 — README 가 받는 곳을 적는다). */
    TETHER_PC("setup/tether-pc", "web-tether-pc.zip", "web-tether-pc", listOf("README.txt", "windows-wifi.bat", "macos-wifi.sh")),
}

/**
 * zip 짓기 — 순수(안드로이드 없이 시험한다). 담는 바이트는 손대지 않는다(줄 끝·인코딩 그대로 — `.bat` 은 CRLF, 나머지는 LF).
 * `java.util.zip` 은 유닉스 실행 비트를 싣지 못한다 — 그래서 두 README 가 스크립트를 `bash x.sh` 로 돌리라고 적는다.
 */
object SetupZip {

    /**
     * [files](맨 위 폴더 아래의 상대 경로와 내용)를 [topFolder] 아래에 담은 zip 의 바이트. 차례는 받은 그대로이고 폴더 항목은 따로 두지 않는다
     * (푸는 쪽이 경로에서 만든다). 폴더 이름·경로가 이상하거나([checkPath]) 겹치면 던진다.
     */
    fun build(topFolder: String, files: List<Pair<String, ByteArray>>, time: Long = System.currentTimeMillis()): ByteArray {
        checkSegment(topFolder)
        val seen = HashSet<String>()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((path, content) in files) {
                checkPath(path)
                require(seen.add(path)) { "duplicate entry: $path" }
                zip.putNextEntry(ZipEntry("$topFolder/$path").apply { this.time = time })
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * zip 안의 상대 경로 — ASCII 글자·숫자·`.`·`_`·`-` 로 된 조각을 `/` 로 잇는다. 절대 경로·빈 조각·점만으로 된 조각(`.`·`..`)·역슬래시·
     * 드라이브 글자(`:`)·공백·한글은 안 된다 — 어느 압축 프로그램으로 풀어도 맨 위 폴더 밖으로 나가지 않고 이름이 깨지지 않게.
     */
    fun checkPath(path: String) {
        require(path.isNotEmpty() && path.length <= MAX_PATH) { "bad entry path" }
        path.split('/').forEach(::checkSegment)
    }

    private fun checkSegment(segment: String) {
        require(segment.isNotEmpty() && segment.any { it != '.' } && segment.all { it in SAFE }) { "bad entry path segment: $segment" }
    }

    private const val MAX_PATH = 200
    private val SAFE: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('.', '_', '-')).toSet()
}
