package io.github.donggi.iroirobrowser.tether.core

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URLDecoder

/**
 * Wi-Fi Direct 의 손님이 브라우저로 폰의 폴더 하나([SharedTree])를 오르내리는 작은 HTTP 서버. [BASE_PORT](8080)부터 [PORT_ATTEMPTS] 개
 * (8080…8089)를 차례로 해 보아 비어 있는 첫 포트에 묶는다 — 모두 차 있을 때만 [StartError.PORT_TAKEN]. 실제 포트([actualPort])를 화면이
 * 보이고, `Host` 검사는 포트를 가리지 않는다(아래 2). **옮기는 것은 사용자 결정이다**(2026-10-10 — [HttpProxyServer] 의 머리말).
 * 위험은 남는다 — 이 폰의 다른 앱이 8080 을 먼저 쥐면 손님이 익숙한 주소(…:8080)로 그 앱의 가짜 화면을 열고 올린 파일을 그
 * 앱에 넘길 수 있다. 사용자가 감수했고, 화면의 '공유 폴더' 설명이 위 상태 줄의 실제 포트를 쓰라고 적는다.
 *
 * ## 왜 브라우저인가
 *
 * 손님에 아무것도 깔지 않아도 되고, 폰이 그룹 오너라 주소가 `192.168.49.1` 로 정해져 찾을 것도 없다. 프록시 둘과 같은 층에 셋째 서버를 얹었다.
 *
 * ## 누가 닿는가
 *
 * 1. [PeerGate] 를 프록시와 **똑같이** 쓴다 — 핫스팟이 꺼져 있으면, 폰이 붙은 보통 Wi-Fi 에서는, 그리고 **폰 안의 다른 앱은** 닿지 못한다.
 *    마지막이 중요하다: 이 서버는 SAF 로 받은 폴더 권한을 HTTP 로 바꿔 주므로, 폰 안에 인증 없이 열려 있으면 다른 앱이 저장소 제한을 비켜 가는
 *    통로가 된다.
 * 2. **`Host` 머리가 [allowedHost](`192.168.49.1`, 포트는 있어도 없어도)가 아니면 421 로 거절한다**(2026-10-10, Web 에 합치며
 *    더했다). 손님이 192.168.49.1 에 프록시 없이 바로 닿는 구성(tun2proxy 의 `--setup`)에서는 손님 PC 의 브라우저가 연 **남의 웹 페이지**가
 *    DNS 재바인딩으로 이 서버를 같은 출처처럼 불러 공유 폴더를 읽고 쓸 수 있다고 보았다(추론 — 브라우저가 사설망 요청을 막으면 막힌다).
 *    재바인딩한 요청에는 공격자의 이름이 `Host` 로 실린다. 머리가 없는 요청(HTTP/1.0)도 거절한다 — 브라우저는 늘 싣는다.
 *
 * 그 안에서는 **인증이 없다** — Wi-Fi Direct 의 WPA2 패스프레이즈가 유일한 통제선이라는 뜻이고 프록시와 같은 전제다(사용자 결정 — 그룹에
 * 붙인 기기만 닿는다). 그룹에 기기를 붙이는 순간 그 기기는 폴더를 읽고 **덮어쓸** 수 있다.
 *
 * ## API
 *
 * | 요청 | 하는 일 |
 * |---|---|
 * | `GET /` | 한 파일짜리 화면(앱의 `assets/tether/fileman.html` — [ui]) |
 * | `GET /api/list?path=…` | 폴더 목록(JSON) |
 * | `GET /api/file?path=…` | 받기. `Range` 를 받는다 |
 * | `PUT /api/file?path=…` | 올리기. 본문이 그대로 파일 내용(같은 이름은 다 받은 뒤에 바꿔 넣는다 — [serveUpload]) |
 *
 * 올리기가 `PUT` 인 것은 뜻한 것이다 — HTML `<form>` 은 GET/POST 만 보내므로 악성 페이지가 폼 전송으로 올리기를 일으킬 길이 막힌다. 다른
 * 출처의 `fetch`·XHR 은 미리 묻기(OPTIONS)가 필요한데 이 서버는 답하지 않는다(404). 연결 하나에 요청 하나(`Connection: close`) — keep-alive 의
 * 본문 경계를 틀릴 일이 없다. `HEAD` 는 받지 않는다.
 *
 * 상한: 동시 연결 [MAX_SESSIONS](usb-tether 와 같다 — 브라우저는 호스트 하나에 6개 남짓만 연다), 연결마다 스레드 하나([ThreadBudget]).
 */
class FileServer(
    env: TetherEnvironment,
    private val tree: SharedTree,
    /** `GET /` 의 화면 바이트. 읽지 못하면 null(500). */
    private val ui: () -> ByteArray?,
    basePort: Int = BASE_PORT,
    attempts: Int = PORT_ATTEMPTS,
    /** 받는 `Host` — 이 이름에 `:포트` 가 붙거나 붙지 않은 것만(포트는 가리지 않는다 — 옮겨 선 포트로 와도 받는다). */
    private val allowedHost: String = PeerFilter.GROUP_OWNER,
) : TcpServer(env, "file-server", basePort, attempts, maxSessions = MAX_SESSIONS, threadsPerSession = 1) {

    override fun handle(client: Socket) {
        client.tcpNoDelay = true
        // 머리를 다 받기까지의 전체 마감(DeadlineInputStream 의 머리말).
        val deadline = DeadlineInputStream(client, client.getInputStream(), env.timings.fileHeaderMs, env.nowMs)
        // 머리를 바이트 단위로 읽으므로 버퍼로 감싸고, 올리기의 본문이 이미 버퍼에 들어와 있을 수 있어 **이 스트림을 그대로** 본문 읽기에 넘긴다.
        val input = BufferedInputStream(deadline, HEADER_BUFFER_BYTES)
        val output = client.getOutputStream()
        try {
            val request = readRequest(input) ?: return
            // 머리를 다 받았다 — 본문(올리기)은 큰 파일이 오래 걸리므로 전체 마감 대신 읽기 하나의 시한만 둔다.
            deadline.finish()
            client.soTimeout = env.timings.fileBodyReadMs
            if (!isAllowedHost(request.header("host"), allowedHost)) {
                env.log.w(TAG, "Host 머리가 그룹 오너 주소가 아니라 거절했다")
                sendText(output, 421, "Misdirected Request", WRONG_HOST_HELP)
                return
            }
            route(request, input, output)
        } catch (_: MalformedRequest) {
            env.log.w(TAG, "틀린 요청을 거절했다")
            sendStatus(output, 400, "Bad Request")
        }
    }

    // ── 요청 읽기 ─────────────────────────────────────────────────────────────────────────────────────────

    /** 읽어 낸 요청 하나. 이 서버는 연결마다 하나만 처리하고 닫는다. */
    private class Request(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
    ) {
        /** `?` 앞. */
        val path: String get() = target.substringBefore('?')

        /** 질의의 `path` 를 퍼센트 부호를 풀어 돌려준다. 없으면 빈 글(맨 위). */
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
                    // UTF-8 퍼센트 풀기(한글 이름). 문자 집합은 이름으로 준다 — (String, Charset) 판은 API 33 부터다.
                    URLDecoder.decode(raw, "UTF-8")
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }
    }

    /** 요청 문법 오류 — 400. */
    private class MalformedRequest : Exception()

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 3) throw MalformedRequest()

        val headers = HashMap<String, String>()
        var lines = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            // 줄을 센다 — 담은 머리 수(이름이 겹치면 덮는다)로 세면 `:` 없는 줄이나 같은 이름이 끝없이 이어져도 걸리지 않았다.
            if (++lines > MAX_HEADER_COUNT) throw MalformedRequest()
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            // Host 가 둘이면 어느 것을 볼지 갈린다 — 거절한다(HttpProxyServer 의 스머글링 막기와 같은 판단).
            if (name == "host" && headers.containsKey("host")) throw MalformedRequest()
            headers[name] = line.substring(separator + 1).trim()
        }
        return Request(parts[0].uppercase(), parts[1], headers)
    }

    /** CRLF 로 끝나는 한 줄. 홀로 선 CR 은 거절한다(HttpProxyServer.readHeaderLine 과 같은 판단 — 머리 주입). */
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

    // ── 길 고르기 ─────────────────────────────────────────────────────────────────────────────────────────

    private fun route(request: Request, input: InputStream, output: OutputStream) {
        when {
            request.method == "GET" && request.path == "/" -> serveUi(output)
            request.method == "GET" && request.path == PATH_LIST -> serveList(request, output)
            request.method == "GET" && request.path == PATH_FILE -> serveDownload(request, output)
            request.method == "PUT" && request.path == PATH_FILE -> serveUpload(request, input, output)
            // HEAD 는 받지 않는다 — 화면이 쓰지 않고, 받으려면 본문 없이 머리만 보내는 갈래가 받기 전체에 퍼진다. OPTIONS 도(위 머리말).
            else -> sendStatus(output, 404, "Not Found")
        }
    }

    private fun serveUi(output: OutputStream) {
        val body = try {
            ui()
        } catch (e: Exception) {
            null
        }
        if (body == null) {
            env.log.e(TAG, "화면 파일을 읽지 못했다")
            sendStatus(output, 500, "Internal Server Error")
            return
        }
        sendHeaders(
            output, 200, "OK",
            listOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Content-Length" to body.size.toString(),
                // 화면은 앱의 판과 함께 바뀐다 — 캐시하지 않는다.
                "Cache-Control" to "no-store",
            ),
        )
        output.write(body)
        output.flush()
    }

    private fun serveList(request: Request, output: OutputStream) {
        if (!tree.isAvailable()) {
            sendJson(output, 503, "Service Unavailable", Json.folderMissing())
            return
        }
        val path = request.requestedPath
        val segments = SharedPaths.split(path)
        val target = segments?.let { tree.find(it) }
        if (target == null || !target.isDirectory) {
            sendJson(output, 404, "Not Found", Json.error("폴더를 찾을 수 없습니다"))
            return
        }
        val children = try {
            tree.list(target)
        } catch (e: Exception) {
            env.log.w(TAG, "목록을 읽지 못했다: ${e.javaClass.simpleName}")
            sendJson(output, 500, "Internal Server Error", Json.error("목록을 읽을 수 없습니다"))
            return
        }
        sendJson(output, 200, "OK", Json.listing(path, tree.rootName(), children))
    }

    /** 파일을 보낸다. `Range: bytes=start-end` 를 받는다 — 동영상 찾기와 끊긴 받기 잇기가 여기에 달렸다. */
    private fun serveDownload(request: Request, output: OutputStream) {
        if (!tree.isAvailable()) {
            sendStatus(output, 503, "Service Unavailable")
            return
        }
        val target = SharedPaths.split(request.requestedPath)?.let { tree.find(it) }
        if (target == null || target.isDirectory) {
            sendStatus(output, 404, "Not Found")
            return
        }
        val length = target.size
        val name = target.name.ifEmpty { "download" }

        val rangeHeader = request.header("range")
        val range = ByteRange.parse(rangeHeader, length)
        if (range == null && rangeHeader != null) {
            sendHeaders(output, 416, "Range Not Satisfiable", listOf("Content-Range" to "bytes */$length", "Content-Length" to "0"))
            output.flush()
            return
        }

        val stream = tree.openRead(target)
        if (stream == null) {
            sendStatus(output, 500, "Internal Server Error")
            return
        }
        stream.use { source ->
            val effective = range ?: ByteRange(0, if (length > 0) length - 1 else 0)
            val count = if (length == 0L) 0L else effective.end - effective.start + 1

            val headers = mutableListOf(
                "Content-Type" to SharedPaths.guessMimeType(name),
                "Content-Length" to count.toString(),
                "Accept-Ranges" to "bytes",
                // filename* 는 RFC 6266 — 한글 이름이 깨지지 않으려면 이 꼴이어야 한다.
                "Content-Disposition" to "attachment; filename*=UTF-8''${SharedPaths.percentEncode(name)}",
            )
            if (range != null) {
                headers += "Content-Range" to "bytes ${range.start}-${range.end}/$length"
                sendHeaders(output, 206, "Partial Content", headers)
            } else {
                sendHeaders(output, 200, "OK", headers)
            }

            if (effective.start > 0) skipFully(source, effective.start)
            copy(source, output, count) { env.onBytesOut(it) }
            output.flush()
        }
    }

    /**
     * 올리기 — 본문 전체가 파일 내용이 된다. `Content-Length` 를 요구한다(chunked 는 받지 않는다 — 브라우저의 `XMLHttpRequest.send(File)` 은
     * 늘 길이를 붙이고, chunked 를 받으려면 풀개가 필요해 범위를 넘는다). `Transfer-Encoding` 이 있으면 `Content-Length` 와 함께든 아니든 501
     * 이다 — 예전에는 길이부터 보아 TE 만 오면 411 이었다(문서와 어긋났다). 흘려서 옮긴다 — 본문을 메모리에 모으면 큰 파일에서
     * 곧바로 OOM 이다.
     *
     * **임시 이름으로 받고 다 받은 뒤에 바꾼다**([SharedTree] 의 머리말). 끊기거나(손님이 끊음·읽기 시한) 길이가 선언과 다르거나
     * 쓰기를 열지 못하면 임시만 지운다 — 원래 파일은 그대로다. 예전에는 받기 전에 원래 파일을 지워, 덮어쓰기가 끊기면 둘 다 사라졌다.
     */
    private fun serveUpload(request: Request, input: InputStream, output: OutputStream) {
        if (!tree.isAvailable()) {
            sendJson(output, 503, "Service Unavailable", Json.folderMissing())
            return
        }
        if (request.header("transfer-encoding") != null) {
            sendJson(output, 501, "Not Implemented", Json.error("chunked 올리기는 받지 않습니다 — Content-Length 로 보내세요"))
            return
        }
        val declared = request.header("content-length")?.toLongOrNull()
        if (declared == null || declared < 0) {
            sendJson(output, 411, "Length Required", Json.error("Content-Length 가 필요합니다"))
            return
        }

        val segments = SharedPaths.split(request.requestedPath)?.takeIf { it.isNotEmpty() }
        val temp = try {
            segments?.let { tree.createUploadTemp(it) }
        } catch (e: Exception) {
            env.log.w(TAG, "올릴 자리를 만들지 못했다: ${e.javaClass.simpleName}")
            sendJson(output, 500, "Internal Server Error", Json.error("올릴 자리를 만들지 못했습니다"))
            return
        }
        if (segments == null || temp == null) {
            sendJson(output, 400, "Bad Request", Json.error("올릴 경로가 틀렸습니다"))
            return
        }
        val sink = tree.openWrite(temp)
        if (sink == null) {
            // 빈 임시 파일을 남기지 않는다.
            deleteQuietly(temp)
            sendJson(output, 500, "Internal Server Error", Json.error("파일을 열 수 없습니다"))
            return
        }

        val written = try {
            sink.use { copy(input, it, declared) { bytes -> env.onBytesIn(bytes) } }
        } catch (e: Exception) {
            env.log.w(TAG, "올리기 실패: ${e.javaClass.simpleName}")
            // 끊긴 것은 임시뿐이다 — 원래 파일은 그대로다. 손님이 다시 올리면 된다.
            deleteQuietly(temp)
            sendJson(output, 500, "Internal Server Error", Json.error("올리기가 끊겼습니다 — 원래 파일은 그대로입니다"))
            return
        }
        if (written != declared) {
            deleteQuietly(temp)
            sendJson(output, 400, "Bad Request", Json.error("본문 길이가 선언과 다릅니다 — 원래 파일은 그대로입니다"))
            return
        }
        val placed = try {
            tree.commitUpload(temp, segments)
        } catch (e: Exception) {
            // 어디서 실패했는지 모른다 — 임시가 받은 내용을 쥐고 있을 수 있어 지우지 않는다.
            env.log.w(TAG, "올린 파일을 제자리에 두지 못했다: ${e.javaClass.simpleName}")
            null
        }
        if (placed == null) {
            sendJson(output, 500, "Internal Server Error", Json.error("받은 파일을 제자리에 두지 못했습니다"))
            return
        }
        sendJson(output, 200, "OK", Json.uploaded(placed.name, written))
    }

    private fun deleteQuietly(target: SharedEntry) {
        try {
            tree.delete(target)
        } catch (_: Exception) {
        }
    }

    // ── 바이트 옮기기 ─────────────────────────────────────────────────────────────────────────────────────

    /** [count] 바이트를 옮기고 실제로 옮긴 양을 돌려준다. 원본이 먼저 끝나면 그만큼만 — 부르는 쪽이 선언과 견준다. */
    private fun copy(source: InputStream, sink: OutputStream, count: Long, onBytes: (Long) -> Unit): Long {
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

    /** [count] 바이트를 버린다. `skip` 은 덜 건너뛸 수 있고 0 을 주는 구현도 있어 그때는 읽어서 버린다. */
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

    // ── 답 쓰기 ──────────────────────────────────────────────────────────────────────────────────────────

    private fun sendHeaders(output: OutputStream, code: Int, reason: String, headers: List<Pair<String, String>>) {
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        for ((name, value) in headers) builder.append(name).append(": ").append(value).append("\r\n")
        // 연결마다 요청 하나 — keep-alive 를 하려면 모든 답의 본문 경계를 정확히 맞춰야 하고 얻는 것보다 틀릴 틈이 크다.
        builder.append("Connection: close\r\n")
        builder.append("\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun sendStatus(output: OutputStream, code: Int, reason: String) {
        try {
            sendHeaders(output, code, reason, listOf("Content-Length" to "0"))
            output.flush()
        } catch (_: Exception) {
            // 이미 끊긴 연결.
        }
    }

    private fun sendText(output: OutputStream, code: Int, reason: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        sendHeaders(
            output, code, reason,
            listOf("Content-Type" to "text/plain; charset=utf-8", "Content-Length" to body.size.toString(), "Cache-Control" to "no-store"),
        )
        output.write(body)
        output.flush()
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

        /** 기본 포트. 차 있으면 다음 포트로(클래스 머리말). */
        const val BASE_PORT = 8080

        /** 해 볼 포트 수 — [BASE_PORT] 부터 하나씩(8080…8089). usb-tether·처음 합친 판과 같은 값. */
        const val PORT_ATTEMPTS = 10

        /** 동시 연결 상한(usb-tether 와 같다). 근거는 클래스 머리말. */
        const val MAX_SESSIONS = 32

        private const val PATH_LIST = "/api/list"
        private const val PATH_FILE = "/api/file"

        private const val MAX_HEADER_LINE_BYTES = 8 * 1024
        private const val HEADER_BUFFER_BYTES = MAX_HEADER_LINE_BYTES

        /** 머리 줄 수 상한(`:` 없는 줄·겹친 이름도 센다). */
        internal const val MAX_HEADER_COUNT = 64

        /** 옮기기 버퍼 — 프록시의 중계(8 KiB)보다 크게. 파일은 대개 큰 덩이다. */
        private const val TRANSFER_BUFFER_BYTES = 64 * 1024

        /** 421 의 본문 — 손님의 브라우저에 보인다. */
        internal const val WRONG_HOST_HELP =
            "이 파일 서버는 http://192.168.49.1 로 연 페이지의 요청만 받습니다(Host 머리가 다르면 거절합니다 — DNS 재바인딩 막기).\n" +
                "주소창에 http://192.168.49.1:8080 처럼 IP 주소로 여세요."

        /**
         * `Host` 가 [allowed] 이거나 `[allowed]:포트`(숫자 1~5자)인가. 대소문자·끝의 점·다른 이름은 받지 않는다 — 주소 리터럴만 받으므로 그럴
         * 일이 없다. 머리가 없으면(null) 거절.
         */
        fun isAllowedHost(host: String?, allowed: String): Boolean {
            val value = host?.trim() ?: return false
            if (value == allowed) return true
            if (!value.startsWith("$allowed:")) return false
            val port = value.substring(allowed.length + 1)
            return port.length in 1..5 && port.all { it in '0'..'9' }
        }
    }
}
