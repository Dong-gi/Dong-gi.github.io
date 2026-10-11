package io.github.donggi.iroirobrowser.torrent.fake

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** 웹 시드가 받은 요청 하나. [decodedPath] 는 경로의 퍼센트를 UTF-8 로 푼 것, [range] 는 Range 헤더 그대로. */
class WebSeedRequest(val method: String, val rawTarget: String, val decodedPath: String, val range: String?, val headers: Map<String, String>)

/**
 * @property corruptRanges 토렌트 흐름의 오프셋 기준 — 이 바이트를 뒤집어(xor 0xFF) 보낸다.
 * @property ignoreRange Range 를 무시하고 200 으로 파일 통째로.
 * @property status 모든 요청에 이 상태로 답한다(503 이면 몸 "1" 과 Retry-After: 1 — BEP 17 의 '기다릴 초').
 * @property redirectTo 모든 요청을 302 로 `redirectTo(끝의 '/' 를 뗀 것) + 요청 대상(경로+쿼리)` 으로.
 * @property bytesPerSecond 몸을 이 속도로 흘린다.
 * @property supportBep17 `/seed?info_hash=..&piece=N(&ranges=a-b,c-d)` 를 받는다(BEP 17). 아니면 404.
 */
data class WebSeedOptions(
    val corruptRanges: List<LongRange> = emptyList(),
    val ignoreRange: Boolean = false,
    val status: Int? = null,
    val redirectTo: String? = null,
    val bytesPerSecond: Long? = null,
    val supportBep17: Boolean = false,
)

/**
 * 가짜 웹 시드. BEP 19(GetRight 꼴) HTTP 서버이고 BEP 17(Hoffman 꼴)은 선택이다. 한 요청마다 한 연결, `Connection: close`.
 *
 * - URL: 여러 파일이면 [baseUrl] + name + "/" + 경로(성분마다 퍼센트 인코딩), 한 파일이면 [baseUrl] + name.
 * - Range 는 `bytes=a-b`·`bytes=a-`·`bytes=-n` 하나만 받는다 → 206 + `Content-Range: bytes a-b/size`.
 *   끝이 파일을 넘으면 파일 끝으로 줄인다(RFC 9110). 시작이 파일 밖이면 416(Content-Range 는 별표 꼴 — 'bytes', 별표, '/size').
 *   여러 범위(쉼표)나 문법이 틀린 Range 는 무시하고 200 으로 통째로 보낸다(RFC 가 허락하는 대로).
 * - 패드 파일(BEP 47)은 서비스하지 않는다(404).
 * - BEP 17 의 ranges 는 조각 시작 기준, 끝을 포함한다(`a-b` 는 a..b). 틀린 info_hash 는 404, 틀린 piece·ranges 는 400.
 *
 * @param bindAddress 설 루프백 주소(기본 127.0.0.1). 루프백이 아니면 예외.
 */
class FakeWebSeed(
    val torrent: FakeTorrent,
    val options: WebSeedOptions = WebSeedOptions(),
    val bindAddress: InetAddress = loopback(1),
) : Closeable {
    private val records = ArrayList<WebSeedRequest>()

    val requests: List<WebSeedRequest> get() = synchronized(records) { records.toList() }

    private val listener = FakeTcpListener("fake-web-seed", bindAddress) { handle(it) }

    val port: Int = listener.port

    /** "http://<bindAddress>:<port>/ws/". */
    val baseUrl: String = "http://${fakeUrlHost(bindAddress)}:$port/ws/"

    /** "http://<bindAddress>:<port>/seed". */
    val bep17Url: String = "http://${fakeUrlHost(bindAddress)}:$port/seed"

    /** [entry] 를 받을 URL(더한 것). */
    fun fileUrl(entry: FakeLayoutEntry): String =
        if (torrent.singleFile) {
            baseUrl + fakePercentEncode(torrent.name)
        } else {
            baseUrl + fakePercentEncode(torrent.name) + "/" + entry.path.joinToString("/") { fakePercentEncode(it) }
        }

    override fun close() = listener.close()

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        val request = try {
            fakeReadHttpRequest(input)
        } catch (_: FakeBadRequest) {
            writeText(out, 400, "bad request", head = false)
            return
        } ?: return
        val decodedPath = String(fakePercentDecode(request.rawPath, plusAsSpace = false), Charsets.UTF_8)
        synchronized(records) {
            records += WebSeedRequest(request.method, request.target, decodedPath, request.headers["range"], request.headers)
        }
        val head = request.method == "HEAD"
        val status = options.status
        val redirect = options.redirectTo
        when {
            request.method != "GET" && !head -> writeText(out, 405, "method not allowed", head)
            status != null -> writeStatus(out, status, head)
            redirect != null -> {
                val location = redirect.trimEnd('/') + request.originForm
                out.write(fakeHttpHead(302, listOf("Location" to location, "Content-Length" to "0", "Connection" to "close")))
                out.flush()
            }
            request.rawPath == "/seed" -> if (options.supportBep17) serveBep17(out, request, head) else writeText(out, 404, "not found", head)
            else -> serveFile(out, request, head)
        }
        lingerClose(socket, input)
    }

    private fun findEntry(rawPath: String): FakeLayoutEntry? {
        if (!rawPath.startsWith("/ws/")) return null
        val segments = rawPath.removePrefix("/ws/").split('/').map { String(fakePercentDecode(it, plusAsSpace = false), Charsets.UTF_8) }
        if (torrent.singleFile) return torrent.layout.first().takeIf { segments == listOf(torrent.name) }
        if (segments.firstOrNull() != torrent.name) return null
        val rest = segments.drop(1)
        return torrent.layout.firstOrNull { it.path == rest }
    }

    private fun serveFile(out: OutputStream, request: FakeHttpRequest, head: Boolean) {
        val entry = findEntry(request.rawPath)
        if (entry == null || entry.isPad) {
            writeText(out, 404, "not found", head)
            return
        }
        val size = entry.length
        var from = 0L
        var to = size - 1
        var status = 200
        val rangeHeader = request.headers["range"]
        if (rangeHeader != null && !options.ignoreRange) {
            val r = parseRange(rangeHeader, size)
            if (r === UNSATISFIABLE) {
                out.write(fakeHttpHead(416, listOf("Content-Range" to "bytes */$size", "Content-Length" to "0", "Connection" to "close")))
                out.flush()
                return
            }
            if (r != null) {
                from = r.first
                to = r.last
                status = 206
            }
        }
        val length = to - from + 1
        val headers = ArrayList<Pair<String, String>>()
        headers += "Content-Type" to "application/octet-stream"
        headers += "Accept-Ranges" to "bytes"
        headers += "Content-Length" to length.toString()
        if (status == 206) headers += "Content-Range" to "bytes $from-$to/$size"
        headers += "Connection" to "close"
        out.write(fakeHttpHead(status, headers))
        if (!head) Throttle().stream(out, entry.offset + from, length)
        out.flush()
    }

    private fun serveBep17(out: OutputStream, request: FakeHttpRequest, head: Boolean) {
        val params = fakeParseQuery(request.rawQuery)
        val infoHash = params["info_hash"]?.firstOrNull()
        if (infoHash == null || !infoHash.contentEquals(torrent.infoHashInternal)) {
            writeText(out, 404, "unknown info_hash", head)
            return
        }
        val piece = params["piece"]?.firstOrNull()?.let { String(it, Charsets.ISO_8859_1).toIntOrNull() }
        if (piece == null || piece !in 0 until torrent.pieceCount) {
            writeText(out, 400, "bad piece", head)
            return
        }
        val pieceSize = torrent.pieceSize(piece).toLong()
        val rangesText = params["ranges"]?.firstOrNull()?.let { String(it, Charsets.ISO_8859_1) }
        val spans = if (rangesText == null) {
            listOf(0L until pieceSize)
        } else {
            rangesText.split(',').map { part ->
                val dash = part.indexOf('-')
                val a = if (dash > 0) part.substring(0, dash).toLongOrNull() else null
                val b = if (dash > 0) part.substring(dash + 1).toLongOrNull() else null
                if (a == null || b == null || a < 0 || b < a || b >= pieceSize) {
                    writeText(out, 400, "bad ranges", head)
                    return
                }
                a..b
            }
        }
        val total = spans.sumOf { it.last - it.first + 1 }
        out.write(
            fakeHttpHead(
                200,
                listOf("Content-Type" to "application/octet-stream", "Content-Length" to total.toString(), "Connection" to "close"),
            ),
        )
        if (!head) {
            val throttle = Throttle()
            val base = piece.toLong() * torrent.pieceLength
            for (s in spans) throttle.stream(out, base + s.first, s.last - s.first + 1)
        }
        out.flush()
    }

    /** null = Range 를 무시한다, [UNSATISFIABLE] = 416, 그 밖 = 보낼 범위(끝 포함). */
    private fun parseRange(header: String, size: Long): LongRange? {
        val h = header.trim()
        if (!h.startsWith("bytes=", ignoreCase = true)) return null
        val spec = h.substring(6).trim()
        if (',' in spec) return null
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val left = spec.substring(0, dash).trim()
        val right = spec.substring(dash + 1).trim()
        if (left.isEmpty()) {
            val n = right.toLongOrNull() ?: return null
            if (n <= 0 || size == 0L) return UNSATISFIABLE
            return maxOf(0L, size - n)..(size - 1)
        }
        val a = left.toLongOrNull() ?: return null
        val b = if (right.isEmpty()) Long.MAX_VALUE else right.toLongOrNull() ?: return null
        if (a < 0 || b < a) return null
        if (a >= size) return UNSATISFIABLE
        return a..minOf(b, size - 1)
    }

    private fun writeStatus(out: OutputStream, status: Int, head: Boolean) {
        if (status == 503) {
            val body = "1".toByteArray(Charsets.US_ASCII)
            out.write(fakeHttpHead(503, listOf("Retry-After" to "1", "Content-Type" to "text/plain", "Content-Length" to body.size.toString(), "Connection" to "close")))
            if (!head) out.write(body)
            out.flush()
            return
        }
        writeText(out, status, status.toString(), head)
    }

    private fun writeText(out: OutputStream, status: Int, text: String, head: Boolean) {
        val body = text.toByteArray(Charsets.UTF_8)
        out.write(fakeHttpHead(status, listOf("Content-Type" to "text/plain", "Content-Length" to body.size.toString(), "Connection" to "close")))
        if (!head) out.write(body)
        out.flush()
    }

    /** FIN 을 먼저 보내고 상대가 닫기를 잠깐 기다린다. */
    private fun lingerClose(socket: Socket, input: BufferedInputStream) {
        try {
            socket.shutdownOutput()
            socket.soTimeout = 1_000
            val sink = ByteArray(4096)
            while (input.read(sink) >= 0) Unit
        } catch (_: SocketTimeoutException) {
        } catch (_: IOException) {
        }
    }

    /** 응답 하나의 몸을 흘린다. 손상 범위를 적용하고, 속도 한도가 있으면 지킨다. */
    private inner class Throttle {
        private val bps = options.bytesPerSecond?.takeIf { it > 0 }
        private val startedAt = System.nanoTime()
        private var sent = 0L

        fun stream(out: OutputStream, torrentOffset: Long, length: Long) {
            val chunk = if (bps == null) 64 * 1024L else maxOf(1L, minOf(16 * 1024L, bps / 10))
            var done = 0L
            while (done < length) {
                val n = minOf(chunk, length - done).toInt()
                val at = torrentOffset + done
                val data = torrent.slice(at, n)
                for (r in options.corruptRanges) {
                    val lo = maxOf(r.first, at)
                    val hi = minOf(r.last, at + n - 1)
                    var i = lo
                    while (i <= hi) {
                        val k = (i - at).toInt()
                        data[k] = (data[k].toInt() xor 0xFF).toByte()
                        i++
                    }
                }
                out.write(data)
                done += n
                sent += n
                if (bps != null) {
                    out.flush()
                    val wait = startedAt + sent * 1_000_000_000L / bps - System.nanoTime()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                }
            }
        }
    }

    private companion object {
        /** 416 표지(같은 객체인지로 견준다). */
        val UNSATISFIABLE = LongRange(-1, -2)
    }
}
