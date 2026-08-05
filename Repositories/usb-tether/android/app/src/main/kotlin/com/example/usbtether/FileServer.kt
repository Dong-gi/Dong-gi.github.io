package com.example.usbtether

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Wi-Fi Direct 클라이언트가 브라우저로 폰의 특정 폴더를 오르내릴 수 있게 하는
 * 작은 HTTP 서버.
 *
 * ## 왜 브라우저인가
 *
 * 클라이언트에 아무것도 설치하지 않아도 되고, 폰이 그룹 오너라 주소가
 * `192.168.49.1` 로 고정되어 디스커버리도 필요 없다. 프록시 두 개와 같은 계층에
 * 세 번째 서버를 얹는 형태다.
 *
 * ## 접근 통제
 *
 * [PeerFilter] 를 프록시와 **똑같이** 쓴다. 즉 연결이 그룹 오너 주소로 도착했고
 * 상대가 `192.168.49.2`–`254` 일 때만 서비스한다. 그래서
 *
 *  - 핫스팟이 꺼져 있으면 어떤 연결도 통과하지 못한다
 *  - 폰이 붙어 있는 일반 Wi-Fi 에서 도달할 수 없다
 *  - **폰 내부의 다른 앱도 도달할 수 없다** (peer 가 `192.168.49.1` 이 되어 거부)
 *
 * 마지막 항목이 중요하다. 이 서버는 SAF 로 받은 폴더 권한을 HTTP API 로 바꿔주므로,
 * 인증 없이 폰 내부에 열려 있으면 다른 앱이 스코프드 스토리지를 우회하는 통로가 된다.
 *
 * 그 대역 안에서는 **인증이 없다.** Wi-Fi Direct 의 WPA2 패스프레이즈가 유일한 통제선
 * 이라는 뜻이며, 프록시와 같은 전제다. 핫스팟에 다른 기기를 붙이는 순간 그 기기는
 * 폴더를 읽고 쓸 수 있다. 인증이 필요해지면 [authorize] 한 곳만 채우면 된다.
 *
 * ## API
 *
 * | 요청 | 동작 |
 * |---|---|
 * | `GET /` | 단일 파일 웹 UI (`assets/fileman.html`) |
 * | `GET /api/list?path=…` | 폴더 목록 (JSON) |
 * | `GET /api/file?path=…` | 다운로드. `Range` 지원 |
 * | `PUT /api/file?path=…` | 업로드. 본문이 그대로 파일 내용 |
 *
 * 업로드를 `PUT` 으로 둔 것은 의도다. HTML `<form>` 은 GET/POST 만 보낼 수 있으므로,
 * 악성 웹페이지가 폼 전송으로 업로드를 유발하는 경로가 원천 차단된다. 브라우저의
 * cross-origin `fetch`/XHR 은 Private Network Access 의 preflight 를 요구하는데 이
 * 서버는 응답하지 않는다.
 */
internal class FileServer(
    private val context: Context,
    private val onBytesIn: (Long) -> Unit = {},
    private val onBytesOut: (Long) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val connections = ConnectionRegistry()
    private val folder = SharedFolder(context)

    /** 바인딩된 포트. 동작 중이 아니면 -1. */
    @Volatile var actualPort: Int = -1
        private set

    /** 마지막 기동 실패 원인. 정상이면 null. */
    @Volatile var lastError: String? = null
        private set

    /**
     * 동기적으로 바인딩한 뒤 accept 를 시작한다.
     *
     * 프록시와 달리 폴더가 지정되지 않아도 기동한다. 폴더 없이도 UI 를 띄워
     * "폴더를 고르세요" 를 보여주는 편이, 서버가 뜨지 않아 브라우저에서 아무 반응이
     * 없는 것보다 알기 쉽다.
     */
    fun start(): Int {
        if (!running.compareAndSet(false, true)) return actualPort
        val sock = bindWithFallback() ?: run {
            running.set(false)
            return -1
        }
        serverSocket = sock
        actualPort = sock.localPort
        lastError = null
        Log.i(TAG, "file server listening on port $actualPort")
        thread(name = "file-server-acceptor", isDaemon = true) { acceptLoop() }
        return actualPort
    }

    /** 리스닝 소켓을 닫고 수립된 연결까지 끊는다. 근거는 [ConnectionRegistry]. */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        connections.closeAll()
        executor.shutdownNow()
        actualPort = -1
    }

    /**
     * `BASE_PORT` 부터 순서대로 비어 있는 첫 포트를 잡는다.
     *
     * 폴백을 두는 이유는 HTTP 프록시와 같다 — 사용자가 앱 화면에 표시된 포트를 읽고
     * 브라우저 주소창에 직접 입력하므로, 자동 탐색으로 가로챌 여지가 없다.
     * (SOCKS5 는 런처가 자동으로 찾아 접속했기 때문에 폴백을 없앴다.)
     */
    private fun bindWithFallback(): ServerSocket? {
        for (offset in 0 until PORT_FALLBACK_ATTEMPTS) {
            val port = BASE_PORT + offset
            try {
                return ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
            } catch (e: Exception) {
                Log.w(TAG, "bind on $port failed: ${e.message}")
            }
        }
        val lastPort = BASE_PORT + PORT_FALLBACK_ATTEMPTS - 1
        lastError = "파일 서버 포트 $BASE_PORT–$lastPort 를 모두 다른 앱이 쓰고 있습니다"
        Log.e(TAG, "could not bind on $BASE_PORT..$lastPort")
        return null
    }

    private fun acceptLoop() {
        try {
            while (running.get()) {
                val client = try {
                    serverSocket!!.accept()
                } catch (e: Exception) {
                    if (running.get()) Log.e(TAG, "accept() failed", e)
                    break
                }
                if (!authorize(client)) {
                    try { client.close() } catch (_: Exception) {}
                    continue
                }
                if (connections.size >= MAX_CONCURRENT_SESSIONS) {
                    Log.w(TAG, "동시 세션 상한 초과로 연결을 거부했다")
                    try { client.close() } catch (_: Exception) {}
                    continue
                }
                if (!connections.register(client)) {
                    Log.w(TAG, "종료 중이라 연결을 거부했다")
                    try { client.close() } catch (_: Exception) {}
                    continue
                }
                try {
                    executor.submit { handleClient(client) }
                } catch (t: Throwable) {
                    connections.unregister(client)
                    try { client.close() } catch (_: Exception) {}
                    Log.e(TAG, "작업 제출 실패: ${t.javaClass.simpleName}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "acceptLoop crashed", t)
            running.set(false)
        }
    }

    /**
     * 이 연결을 서비스해도 되는지. 인증을 붙이려면 여기 한 곳만 채우면 된다.
     *
     * 지금은 [PeerFilter] 만 본다 — 근거는 클래스 KDoc 의 "접근 통제" 절.
     */
    private fun authorize(client: Socket): Boolean {
        if (PeerFilter.isAllowed(client.inetAddress, client.localAddress)) return true
        Log.w(TAG, "허용되지 않은 네트워크의 클라이언트를 거부했다")
        return false
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.soTimeout = REQUEST_TIMEOUT_MS
            // 헤더를 바이트 단위로 읽으므로 반드시 버퍼로 감싼다. 그리고 업로드 본문이
            // 이미 버퍼에 들어와 있을 수 있으므로 **이 스트림을 그대로** 본문 읽기에
            // 넘긴다(HttpProxyServer 와 같은 이유).
            val input = BufferedInputStream(client.getInputStream(), HEADER_BUFFER_BYTES)
            val output = client.getOutputStream()

            val request = readRequest(input) ?: return
            route(request, input, output)
        } catch (_: MalformedRequest) {
            Log.w(TAG, "잘못된 요청을 거부했다")
            trySendStatus(client, 400, "Bad Request")
        } catch (e: Exception) {
            Log.w(TAG, "client error: ${e.javaClass.simpleName}")
        } finally {
            connections.unregister(client)
            try { client.close() } catch (_: Exception) {}
        }
    }

    //=========================================================================
    // 요청 파싱
    //=========================================================================

    /** 파싱된 요청 하나. 이 서버는 연결당 하나만 처리하고 닫는다. */
    private class Request(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
    ) {
        /** `?` 앞부분. */
        val path: String get() = target.substringBefore('?')

        /** 쿼리스트링의 `path` 파라미터를 퍼센트 디코딩해 돌려준다. 없으면 빈 문자열(루트). */
        val requestedPath: String get() = queryParam("path") ?: ""

        fun header(name: String): String? = headers[name.lowercase()]

        private fun queryParam(name: String): String? {
            val query = target.substringAfter('?', "")
            if (query.isEmpty()) return null
            for (pair in query.split('&')) {
                val key = pair.substringBefore('=')
                if (key != name) continue
                val raw = pair.substringAfter('=', "")
                return try {
                    // UTF-8 퍼센트 디코딩. 한글 파일명이 여기로 온다.
                    URLDecoder.decode(raw, "UTF-8")
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }
    }

    /** 요청 문법 오류. 400 으로 응답한다. */
    private class MalformedRequest : Exception()

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 3) throw MalformedRequest()

        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            if (headers.size >= MAX_HEADER_COUNT) throw MalformedRequest()
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim().lowercase()] =
                line.substring(separator + 1).trim()
        }
        return Request(parts[0].uppercase(), parts[1], headers)
    }

    /**
     * CRLF 로 끝나는 한 줄을 읽는다.
     *
     * LF 를 동반하지 않는 CR 은 거부한다 — 그대로 보존하면 헤더 주입이 된다
     * (HttpProxyServer 의 같은 함수와 동일한 판단).
     */
    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            if (builder.length >= MAX_HEADER_LINE_BYTES) throw MalformedRequest()
            val b = input.read()
            if (b == -1) return if (builder.isEmpty()) null else builder.toString()
            when (b) {
                '\r'.code -> {
                    val next = input.read()
                    if (next == '\n'.code || next == -1) return builder.toString()
                    throw MalformedRequest()
                }
                '\n'.code -> return builder.toString()
                else -> builder.append(b.toChar())
            }
        }
    }

    //=========================================================================
    // 라우팅
    //=========================================================================

    private fun route(request: Request, input: InputStream, output: OutputStream) {
        when {
            request.method == "GET" && request.path == "/" -> serveUi(output)
            request.method == "GET" && request.path == PATH_LIST -> serveList(request, output)
            request.method == "GET" && request.path == PATH_FILE -> serveDownload(request, output)
            request.method == "PUT" && request.path == PATH_FILE -> serveUpload(request, input, output)
            // HEAD 는 지원하지 않는다. 브라우저 UI 가 쓰지 않고, 지원하려면 본문 없이
            // 헤더만 보내는 분기가 다운로드 경로 전체에 퍼진다.
            else -> sendStatus(output, 404, "Not Found")
        }
    }

    /** 웹 UI. assets 에서 그대로 읽어 보낸다. */
    private fun serveUi(output: OutputStream) {
        val body = try {
            context.assets.open(UI_ASSET).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "UI 애셋을 읽을 수 없다: ${e.javaClass.simpleName}")
            sendStatus(output, 500, "Internal Server Error")
            return
        }
        sendHeaders(
            output, 200, "OK",
            listOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Content-Length" to body.size.toString(),
                // UI 는 앱 버전과 함께 바뀌므로 캐시하지 않는다.
                "Cache-Control" to "no-store",
            ),
        )
        output.write(body)
        output.flush()
    }

    /** 폴더 목록을 JSON 으로 보낸다. */
    private fun serveList(request: Request, output: OutputStream) {
        if (!folder.isAvailable()) {
            sendJson(output, 503, "Service Unavailable", Json.folderMissing())
            return
        }
        val path = request.requestedPath
        val target = folder.resolve(path)
        if (target == null || !target.isDirectory) {
            sendJson(output, 404, "Not Found", Json.error("폴더를 찾을 수 없습니다"))
            return
        }
        val children = try {
            target.listFiles()
        } catch (e: Exception) {
            Log.w(TAG, "목록 조회 실패: ${e.javaClass.simpleName}")
            sendJson(output, 500, "Internal Server Error", Json.error("목록을 읽을 수 없습니다"))
            return
        }
        sendJson(output, 200, "OK", Json.listing(path, folder.displayName, children))
    }

    /**
     * 파일을 보낸다. `Range: bytes=start-end` 를 지원한다.
     *
     * Range 가 없으면 전송하지 않는 이유가 없다 — 동영상 탐색과 중단 재개가 여기에
     * 달려 있다.
     */
    private fun serveDownload(request: Request, output: OutputStream) {
        if (!folder.isAvailable()) {
            sendStatus(output, 503, "Service Unavailable")
            return
        }
        val target = folder.resolve(request.requestedPath)
        if (target == null || !target.isFile) {
            sendStatus(output, 404, "Not Found")
            return
        }
        val length = target.length()
        val name = target.name ?: "download"

        val range = ByteRange.parse(request.header("Range"), length)
        if (range == null && request.header("Range") != null) {
            sendHeaders(
                output, 416, "Range Not Satisfiable",
                listOf("Content-Range" to "bytes */$length", "Content-Length" to "0"),
            )
            output.flush()
            return
        }

        val stream = folder.openInput(target)
        if (stream == null) {
            sendStatus(output, 500, "Internal Server Error")
            return
        }
        stream.use { source ->
            val effective = range ?: ByteRange(0, if (length > 0) length - 1 else 0)
            val count = if (length == 0L) 0L else effective.end - effective.start + 1

            val headers = mutableListOf(
                "Content-Type" to SharedFolder.guessMimeType(name),
                "Content-Length" to count.toString(),
                "Accept-Ranges" to "bytes",
                // filename* 는 RFC 6266. 한글 파일명이 깨지지 않으려면 이 형식이어야 한다.
                "Content-Disposition" to "attachment; filename*=UTF-8''${percentEncode(name)}",
            )
            if (range != null) {
                headers += "Content-Range" to "bytes ${range.start}-${range.end}/$length"
                sendHeaders(output, 206, "Partial Content", headers)
            } else {
                sendHeaders(output, 200, "OK", headers)
            }

            if (effective.start > 0) skipFully(source, effective.start)
            copy(source, output, count) { onBytesOut(it) }
            output.flush()
        }
    }

    /**
     * 업로드. 본문 전체가 파일 내용이 된다.
     *
     * `Content-Length` 를 요구한다. chunked 전송 인코딩은 지원하지 않는다 — 브라우저의
     * `XMLHttpRequest.send(File)` 은 항상 길이를 붙이고, chunked 를 받으려면 디코더가
     * 필요해 범위를 넘는다.
     *
     * 스트리밍으로 옮긴다. 본문을 메모리에 모으면 큰 파일에서 바로 OOM 이다.
     */
    private fun serveUpload(request: Request, input: InputStream, output: OutputStream) {
        if (!folder.isAvailable()) {
            sendJson(output, 503, "Service Unavailable", Json.folderMissing())
            return
        }
        val declared = request.header("Content-Length")?.toLongOrNull()
        if (declared == null || declared < 0) {
            sendJson(output, 411, "Length Required", Json.error("Content-Length 가 필요합니다"))
            return
        }
        if (request.header("Transfer-Encoding") != null) {
            sendJson(output, 501, "Not Implemented", Json.error("chunked 업로드는 지원하지 않습니다"))
            return
        }

        val target = folder.createForWrite(request.requestedPath)
        if (target == null) {
            sendJson(output, 400, "Bad Request", Json.error("업로드 경로가 잘못됐습니다"))
            return
        }
        val sink = folder.openOutput(target)
        if (sink == null) {
            sendJson(output, 500, "Internal Server Error", Json.error("파일을 열 수 없습니다"))
            return
        }

        val written = try {
            sink.use { copy(input, it, declared) { bytes -> onBytesIn(bytes) } }
        } catch (e: Exception) {
            Log.w(TAG, "업로드 실패: ${e.javaClass.simpleName}")
            // 중간에 끊긴 파일을 남기지 않는다. 클라이언트는 다시 올릴 수 있다.
            try { target.delete() } catch (_: Exception) {}
            sendJson(output, 500, "Internal Server Error", Json.error("업로드가 중단됐습니다"))
            return
        }
        if (written != declared) {
            try { target.delete() } catch (_: Exception) {}
            sendJson(output, 400, "Bad Request", Json.error("본문 길이가 선언과 다릅니다"))
            return
        }
        sendJson(output, 200, "OK", Json.uploaded(target.name ?: "", written))
    }

    //=========================================================================
    // 바이트 이동
    //=========================================================================

    /**
     * [count] 바이트를 옮긴다. 실제로 옮긴 양을 돌려준다.
     *
     * 원본이 먼저 끝나면 그만큼만 옮기고 반환한다 — 호출부가 선언 길이와 비교해
     * 판단한다.
     */
    private fun copy(
        source: InputStream,
        sink: OutputStream,
        count: Long,
        onBytes: (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
        var remaining = count
        while (remaining > 0) {
            val want = minOf(remaining, buffer.size.toLong()).toInt()
            val read = source.read(buffer, 0, want)
            if (read == -1) break
            sink.write(buffer, 0, read)
            onBytes(read.toLong())
            remaining -= read
        }
        sink.flush()
        return count - remaining
    }

    /**
     * [count] 바이트를 버린다.
     *
     * `InputStream.skip` 은 요청보다 적게 건너뛸 수 있으므로 반복해야 한다. 0 을
     * 돌려주는 구현도 있어(스트림 종류에 따라) 그 경우 읽어서 버린다.
     */
    private fun skipFully(source: InputStream, count: Long) {
        var remaining = count
        val scratch = ByteArray(TRANSFER_BUFFER_BYTES)
        while (remaining > 0) {
            val skipped = source.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            val want = minOf(remaining, scratch.size.toLong()).toInt()
            val read = source.read(scratch, 0, want)
            if (read == -1) return
            remaining -= read
        }
    }

    //=========================================================================
    // 응답 쓰기
    //=========================================================================

    private fun sendHeaders(
        output: OutputStream,
        code: Int,
        reason: String,
        headers: List<Pair<String, String>>,
    ) {
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        for ((name, value) in headers) builder.append(name).append(": ").append(value).append("\r\n")
        // 연결당 요청 하나만 처리한다. keep-alive 를 지원하려면 본문 경계를 모든
        // 응답에서 정확히 맞춰야 하고, 얻는 것보다 실수할 여지가 크다.
        builder.append("Connection: close\r\n")
        builder.append("\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun sendStatus(output: OutputStream, code: Int, reason: String) {
        sendHeaders(output, code, reason, listOf("Content-Length" to "0"))
        output.flush()
    }

    private fun trySendStatus(client: Socket, code: Int, reason: String) {
        try {
            sendStatus(client.getOutputStream(), code, reason)
        } catch (_: Exception) {
            // 이미 끊긴 연결. 알릴 방법이 없다.
        }
    }

    private fun sendJson(output: OutputStream, code: Int, reason: String, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        sendHeaders(
            output, code, reason,
            listOf(
                "Content-Type" to "application/json; charset=utf-8",
                "Content-Length" to body.size.toString(),
                "Cache-Control" to "no-store",
            ),
        )
        output.write(body)
        output.flush()
    }

    companion object {
        private const val TAG = "FileServer"

        /** 기본 포트. 폴백은 +9 까지. */
        const val BASE_PORT = 8080
        private const val PORT_FALLBACK_ATTEMPTS = 10

        private const val PATH_LIST = "/api/list"
        private const val PATH_FILE = "/api/file"
        private const val UI_ASSET = "fileman.html"

        /**
         * 요청 헤더를 다 받기까지 기다리는 시간.
         *
         * 본문 전송 중에는 이 타임아웃이 매 읽기마다 갱신되므로 큰 파일 업로드가
         * 끊기지 않는다. 아무것도 보내지 않는 연결은 여기서 정리된다.
         */
        private const val REQUEST_TIMEOUT_MS = 30_000

        private const val MAX_HEADER_LINE_BYTES = 8 * 1024
        private const val HEADER_BUFFER_BYTES = MAX_HEADER_LINE_BYTES
        private const val MAX_HEADER_COUNT = 64

        /** 동시 세션 상한. 근거는 Socks5Server 의 같은 상수 주석 참고. */
        private const val MAX_CONCURRENT_SESSIONS = 32

        /** 전송 버퍼. 프록시 릴레이(8 KiB)보다 크게 잡는다 — 파일은 대개 큰 덩어리다. */
        private const val TRANSFER_BUFFER_BYTES = 64 * 1024

        /**
         * RFC 3986 unreserved 를 제외한 모든 바이트를 퍼센트 인코딩한다.
         *
         * `URLEncoder` 를 쓰지 않는다 — 공백을 `+` 로 바꾸는 form 인코딩이라
         * `Content-Disposition` 의 `filename*` 값으로는 틀리다.
         */
        fun percentEncode(text: String): String {
            val builder = StringBuilder()
            for (byte in text.toByteArray(Charsets.UTF_8)) {
                val value = byte.toInt() and 0xFF
                val char = value.toChar()
                if (char.isLetterOrDigit() && value < 0x80 || char in "-_.~") {
                    builder.append(char)
                } else {
                    builder.append('%').append("%02X".format(value))
                }
            }
            return builder.toString()
        }
    }
}

/** `Range` 헤더가 지정한 바이트 구간. 양끝 포함이다. */
internal class ByteRange(val start: Long, val end: Long) {
    companion object {
        /**
         * `Range: bytes=start-end` 를 파싱한다.
         *
         * 지원 형태: `bytes=0-499`, `bytes=500-`(끝까지), `bytes=-500`(마지막 500바이트).
         * 다중 구간(`bytes=0-1,5-6`)은 지원하지 않는다 — multipart/byteranges 응답을
         * 만들어야 하고 브라우저가 파일 다운로드에 쓰지 않는다.
         *
         * @return 정규화된 구간. 헤더가 없거나 만족시킬 수 없으면 null
         */
        fun parse(header: String?, totalLength: Long): ByteRange? {
            if (header == null) return null
            if (totalLength <= 0) return null

            val spec = header.trim().removePrefix("bytes=").trim()
            if (spec.isEmpty() || spec.contains(',')) return null

            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val startText = spec.substring(0, dash)
            val endText = spec.substring(dash + 1)

            val start: Long
            val end: Long
            if (startText.isEmpty()) {
                // suffix 형태: 마지막 N 바이트
                val suffix = endText.toLongOrNull() ?: return null
                if (suffix <= 0) return null
                start = maxOf(0, totalLength - suffix)
                end = totalLength - 1
            } else {
                start = startText.toLongOrNull() ?: return null
                if (start < 0 || start >= totalLength) return null
                end = if (endText.isEmpty()) {
                    totalLength - 1
                } else {
                    val parsed = endText.toLongOrNull() ?: return null
                    minOf(parsed, totalLength - 1)
                }
                if (end < start) return null
            }
            return ByteRange(start, end)
        }
    }
}

/**
 * 손으로 쓴 최소 JSON 직렬화.
 *
 * 의존성을 늘리지 않기 위한 선택이다. 출력하는 구조가 세 가지로 고정되어 있어
 * 범용 직렬화기가 필요하지 않다. 다만 **문자열 이스케이프는 정확해야 한다** —
 * 파일명에 `"` 나 역슬래시, 제어문자가 들어올 수 있다.
 */
internal object Json {

    fun error(message: String): String = """{"error":${quote(message)}}"""

    fun folderMissing(): String =
        """{"error":${quote("공유 폴더가 지정되지 않았습니다. 앱에서 폴더를 선택하세요.")},"needsFolder":true}"""

    fun uploaded(name: String, bytes: Long): String =
        """{"ok":true,"name":${quote(name)},"bytes":$bytes}"""

    /** 폴더 목록. 폴더가 먼저, 그다음 파일. 각 그룹은 이름순. */
    fun listing(path: String, rootName: String?, children: Array<DocumentFile>): String {
        val sorted = children.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }
                .thenBy { (it.name ?: "").lowercase() }
        )
        val entries = sorted.joinToString(",") { child ->
            val name = child.name ?: ""
            """{"name":${quote(name)},"dir":${child.isDirectory},""" +
                """"size":${child.length()},"modified":${child.lastModified()}}"""
        }
        return """{"path":${quote(path)},"root":${quote(rootName ?: "")},"entries":[$entries]}"""
    }

    /** JSON 문자열 리터럴로 감싼다. 제어문자는 `\uXXXX` 로 이스케이프한다. */
    fun quote(text: String): String {
        val builder = StringBuilder(text.length + 2)
        builder.append('"')
        for (char in text) {
            when {
                char == '"' -> builder.append("\\\"")
                char == '\\' -> builder.append("\\\\")
                char == '\n' -> builder.append("\\n")
                char == '\r' -> builder.append("\\r")
                char == '\t' -> builder.append("\\t")
                char.code < 0x20 -> builder.append("\\u%04x".format(char.code))
                else -> builder.append(char)
            }
        }
        builder.append('"')
        return builder.toString()
    }
}
