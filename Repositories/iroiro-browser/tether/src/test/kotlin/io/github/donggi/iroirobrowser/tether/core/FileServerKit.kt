package io.github.donggi.iroirobrowser.tether.core

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 파일 서버 시험의 공통 — 임시 폴더 위의 [DirectoryTree], 관리 스위치([manage] — 시험이 켜고 끈다), 루프백의 요청 꼴들. 시험이 끝나면
 * [close] 가 서버를 내리고 폴더를 지운다.
 */
internal class FileServerKit(
    val root: File = Files.createTempDirectory("tether-share").toFile(),
    manageOn: Boolean = true,
    val tree: DirectoryTree = DirectoryTree(root),
    sharedTree: SharedTree = tree,
    thumbnails: ThumbnailCache? = null,
    maxTreeEntries: Int = SharedOps.MAX_TREE_ENTRIES,
    env: TetherEnvironment = testEnv(),
    /** 휴지통 보관 기간(일) — 시험이 바꾼다. */
    trashDays: () -> Int = { SharedOps.DEFAULT_TRASH_DAYS },
    /** 서버가 선 뒤 휴지통을 처음 손보기까지 — 기본은 실제 값(시험이 끝나기 전에 돌지 않는다). */
    trashSweepFirstMs: Long = FileServer.TRASH_SWEEP_FIRST_MS,
    /** 휴지통 기록의 벽시계. */
    wallClockMs: () -> Long = { System.currentTimeMillis() },
) : AutoCloseable {
    val manage = AtomicBoolean(manageOn)
    private val closer = Closer()
    val server: FileServer = closer.keep(
        FileServer(
            env, sharedTree, { name -> "ui:$name".toByteArray() }, basePort = 0,
            manageAllowed = { manage.get() }, thumbnails = thumbnails, maxTreeEntries = maxTreeEntries,
            trashRetentionDays = trashDays, trashSweepFirstMs = trashSweepFirstMs, wallClockMs = wallClockMs,
        ),
    )
    val port: Int

    init {
        port = server.start()
        check(port > 0)
    }

    fun file(path: String, text: String = path): File = File(root, path).also {
        it.parentFile.mkdirs()
        it.writeText(text)
    }

    fun dir(path: String): File = File(root, path).also { it.mkdirs() }

    fun get(target: String, extra: String = ""): HttpReply =
        rawHttp(port, "GET $target HTTP/1.1\r\nHost: 192.168.49.1:8080\r\n$extra\r\n".toByteArray(Charsets.UTF_8))

    /** JSON 몸의 POST — [type] 과 [extra] 로 꼴을 바꿔 CSRF·상한을 본다. */
    fun post(path: String, json: String, type: String? = "application/json", extra: String = ""): HttpReply {
        val body = json.toByteArray(Charsets.UTF_8)
        val typeLine = if (type != null) "Content-Type: $type\r\n" else ""
        val head = "POST $path HTTP/1.1\r\nHost: 192.168.49.1:8080\r\n${typeLine}Content-Length: ${body.size}\r\n$extra\r\n"
        return rawHttp(port, head.toByteArray(Charsets.UTF_8) + body)
    }

    fun put(target: String, body: ByteArray = ByteArray(0), extra: String = ""): HttpReply {
        val head = "PUT $target HTTP/1.1\r\nHost: 192.168.49.1:8080\r\nContent-Length: ${body.size}\r\n$extra\r\n"
        return rawHttp(port, head.toByteArray(Charsets.UTF_8) + body)
    }

    /** 맨 위에서 본 이름들(숨은 것 포함 — 디스크 그대로). */
    fun names(path: String = ""): Set<String> = File(root, path).list().orEmpty().toSet()

    override fun close() {
        closer.close()
        root.deleteRecursively()
    }
}

/** JSON 의 글 하나를 감싼다(시험의 몸 짓기). */
internal fun q(text: String): String = Json.quote(text)

/** 응답 본문을 JSON 객체로. */
@Suppress("UNCHECKED_CAST")
internal fun HttpReply.json(): Map<String, Any?> = JsonReader.parse(text) as Map<String, Any?>

internal fun enc(text: String): String = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
