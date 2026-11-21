package io.github.donggi.iroirobrowser.tether.core

import java.io.File
import java.net.InetAddress

/**
 * **개발용 실행기**(시험 소스에만 있다 — 앱에 들어가지 않는다): PC 의 임시 폴더를 공유 폴더로 파일 서버를 루프백에 띄워 화면을 브라우저로 본다.
 *
 *     java -cp <tether 의 main·test 클래스>;<kotlin-stdlib> io.github.donggi.iroirobrowser.tether.core.DevFileServerKt <공유 폴더> <화면 자산 폴더> <포트> [관리 스위치 파일]
 *
 * 손님 거르기는 **여기서만** 루프백을 받는 것([ANY_PEER])으로 주입한다 — 제품의 [PeerFilter] 는 그대로다. `Host`·`Origin` 은 `127.0.0.1` 로 받는다.
 * 관리 허용은 [관리 스위치 파일]이 있는 동안 켜진다(요청마다 본다 — 서버를 다시 띄우지 않고 켜고 끈다). 섬네일은 작은 그림 파일을 그대로 준다
 * ([DirectoryTree.thumbnail]).
 */
fun main(args: Array<String>) {
    require(args.size >= 3) { "인자: <공유 폴더> <화면 자산 폴더> <포트> [관리 스위치 파일]" }
    val root = File(args[0])
    val assets = File(args[1])
    val port = args[2].toInt()
    val manageFlag = args.getOrNull(3)?.let { File(it) }
    val env = TetherEnvironment(
        peers = ANY_PEER,
        bindAddress = InetAddress.getByName("127.0.0.1"),
        log = TetherLog { level, tag, message, error -> println("$level $tag $message ${error?.javaClass?.simpleName ?: ""}") },
    )
    val server = FileServer(
        env, DirectoryTree(root),
        ui = { name -> File(assets, name).takeIf { it.isFile }?.readBytes() },
        basePort = port, attempts = 1, allowedHost = "127.0.0.1",
        manageAllowed = { manageFlag?.exists() == true },
        thumbnails = ThumbnailCache(File(root.parentFile, "thumb-cache")),
    )
    check(server.start() == port) { "포트 $port 를 묶지 못했다" }
    println("http://127.0.0.1:$port/ 에서 듣는다 — 공유 폴더 ${root.absolutePath}")
    Thread.currentThread().join()
}
