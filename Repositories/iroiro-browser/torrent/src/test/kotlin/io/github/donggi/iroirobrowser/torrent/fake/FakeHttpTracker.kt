package io.github.donggi.iroirobrowser.torrent.fake

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * HTTP 트래커가 받은 요청 하나(BEP 3·23·7). 경로가 무엇이든 모든 요청을 기록한다.
 * [params] 는 퍼센트를 푼 바이트('+' 는 공백으로 푼다). 아래 필드는 그 첫 값을 해석한 것이고, 없거나 틀리면 null.
 */
class HttpAnnounce(
    val path: String,
    val rawQuery: String,
    val headers: Map<String, String>,
    val params: Map<String, List<ByteArray>>,
    val infoHash: ByteArray?,
    val peerId: ByteArray?,
    val port: Int?,
    val uploaded: Long?,
    val downloaded: Long?,
    val left: Long?,
    val event: String?,
    val compact: String?,
    val numwant: Int?,
    val key: String?,
) {
    /** 이름 [name] 의 첫 값을 글자로(ISO-8859-1). */
    fun param(name: String): String? = params[name]?.firstOrNull()?.let { String(it, Charsets.ISO_8859_1) }
}

/** 트래커가 요청에 어떻게 답할지. 적대 꼴은 엔진의 방어(크기 한도·시간 초과·되돌리기 한도 등)를 시험하려는 것이다. */
sealed class TrackerBehavior {
    /** 정상 답. */
    object Normal : TrackerBehavior()

    /** "failure reason" 만 담은 사전(상태 200). */
    class Failure(val reason: String) : TrackerBehavior()

    /** 올바른 bencode 사전이지만 몸이 정확히 [bytes] 바이트(모자라면 가능한 가장 작은 크기). Content-Length 를 붙인다. */
    class HugeBody(val bytes: Int) : TrackerBehavior()

    /** [HugeBody] 와 같은 몸을 chunked 로 — 길이를 미리 알리지 않는다. */
    class HugeBodyChunked(val bytes: Int) : TrackerBehavior()

    /** 302, Location 은 요청한 주소 그대로 — 끝없이 되돌린다. */
    object RedirectLoop : TrackerBehavior()

    /** 302, Location 은 [location] 그대로. */
    class RedirectTo(val location: String) : TrackerBehavior()

    /** 상태 200 에 bencode 가 아닌 몸. */
    object InvalidBencode : TrackerBehavior()

    /** 정상 응답(머리 포함)을 한 바이트씩 [delayMsPerByte] 간격으로. */
    class SlowDrip(val delayMsPerByte: Long) : TrackerBehavior()

    /** 요청을 받고 답하지 않는다. 연결은 상대가 끊거나 트래커가 닫힐 때까지 열어 둔다. */
    object NoResponse : TrackerBehavior()

    /** 상태·헤더·몸을 그대로. Content-Length·Transfer-Encoding 이 없으면 Content-Length 를, Connection 이 없으면 close 를 더한다. */
    class Raw(val status: Int, val headers: Map<String, String>, val body: ByteArray) : TrackerBehavior()
}

/**
 * 가짜 HTTP 트래커(BEP 3 announce, BEP 23 compact, BEP 7 peers6). HTTP/1.1, 한 요청마다 한 연결, `Connection: close`.
 *
 * 정상 답의 "peers": compact=1 을 청했고 [forceDictPeers] 가 아니면 6바이트 꼴(IPv4 만), 아니면 사전 목록
 * (ip·port·peer id — no_peer_id=1 이면 peer id 를 뺀다). "peers6" 는 [peers6] 의 IPv6 만 18바이트 꼴로, 비면 넣지 않는다.
 *
 * @param bindAddress 설 루프백 주소(기본 127.0.0.1). 루프백이 아니면 예외.
 */
class FakeHttpTracker(val bindAddress: InetAddress = loopback(1)) : Closeable {
    @Volatile
    var peers: List<InetSocketAddress> = emptyList()

    @Volatile
    var peers6: List<InetSocketAddress> = emptyList()

    @Volatile
    var interval: Long = 1800

    @Volatile
    var minInterval: Long? = null

    @Volatile
    var complete: Long = 1

    @Volatile
    var incomplete: Long = 0

    @Volatile
    var forceDictPeers: Boolean = false

    @Volatile
    var behavior: TrackerBehavior = TrackerBehavior.Normal

    /** 정상 답에 "warning message" 를 넣는다(더한 손잡이). */
    @Volatile
    var warningMessage: String? = null

    private val records = ArrayList<HttpAnnounce>()

    /** 받은 요청들(사본). */
    val announces: List<HttpAnnounce> get() = synchronized(records) { records.toList() }

    private val listener = FakeTcpListener("fake-http-tracker", bindAddress) { handle(it) }

    val port: Int = listener.port

    /** "http://<bindAddress>:<port>/announce". */
    val announceUrl: String = "http://${fakeUrlHost(bindAddress)}:$port/announce"

    /** BEP 48 scrape 주소(더한 것). 경로가 "/scrape" 로 끝나면 Normal 일 때 files 사전으로 답한다. */
    val scrapeUrl: String = "http://${fakeUrlHost(bindAddress)}:$port/scrape"

    override fun close() = listener.close()

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        val request = try {
            fakeReadHttpRequest(input)
        } catch (_: FakeBadRequest) {
            writeSimple(out, 400, "text/plain", "bad request".toByteArray())
            return
        } ?: return
        val record = toRecord(request)
        synchronized(records) { records += record }

        when (val b = behavior) {
            TrackerBehavior.Normal -> {
                val body = if (record.path.endsWith("/scrape")) scrapeBody(record) else normalBody(record)
                writeSimple(out, 200, "text/plain", FakeBencode.encode(body))
            }
            is TrackerBehavior.Failure ->
                writeSimple(out, 200, "text/plain", FakeBencode.encode(mapOf("failure reason" to b.reason.toByteArray(Charsets.UTF_8))))
            is TrackerBehavior.HugeBody -> writeHuge(out, record, b.bytes, chunked = false)
            is TrackerBehavior.HugeBodyChunked -> writeHuge(out, record, b.bytes, chunked = true)
            TrackerBehavior.RedirectLoop -> writeRedirect(out, "http://${fakeUrlHost(bindAddress)}:${socket.localPort}${request.originForm}")
            is TrackerBehavior.RedirectTo -> writeRedirect(out, b.location)
            TrackerBehavior.InvalidBencode -> writeSimple(out, 200, "text/plain", INVALID_BENCODE)
            is TrackerBehavior.SlowDrip -> {
                val body = FakeBencode.encode(normalBody(record))
                val all = fakeHttpHead(200, simpleHeaders("text/plain", body.size.toLong())) + body
                for (x in all) {
                    out.write(x.toInt())
                    out.flush()
                    Thread.sleep(b.delayMsPerByte)
                }
            }
            TrackerBehavior.NoResponse -> {
                // 상대가 끊거나 close() 가 소켓을 닫을 때까지 붙잡고 있는다.
                val sink = ByteArray(4096)
                while (input.read(sink) >= 0) Unit
                return
            }
            is TrackerBehavior.Raw -> {
                val headers = ArrayList<Pair<String, String>>()
                b.headers.forEach { (k, v) -> headers += k to v }
                val names = b.headers.keys.map { it.lowercase() }.toSet()
                if ("content-length" !in names && "transfer-encoding" !in names) headers += "Content-Length" to b.body.size.toString()
                if ("connection" !in names) headers += "Connection" to "close"
                out.write(fakeHttpHead(b.status, headers))
                out.write(b.body)
                out.flush()
            }
        }
        lingerClose(socket, input)
    }

    private fun toRecord(r: FakeHttpRequest): HttpAnnounce {
        val params = fakeParseQuery(r.rawQuery).mapValues { it.value.toList() }
        fun first(name: String): ByteArray? = params[name]?.firstOrNull()
        fun text(name: String): String? = first(name)?.let { String(it, Charsets.ISO_8859_1) }
        return HttpAnnounce(
            path = r.rawPath,
            rawQuery = r.rawQuery,
            headers = r.headers.toMap(),
            params = params,
            infoHash = first("info_hash"),
            peerId = first("peer_id"),
            port = text("port")?.toIntOrNull(),
            uploaded = text("uploaded")?.toLongOrNull(),
            downloaded = text("downloaded")?.toLongOrNull(),
            left = text("left")?.toLongOrNull(),
            event = text("event"),
            compact = text("compact"),
            numwant = text("numwant")?.toIntOrNull(),
            key = text("key"),
        )
    }

    private fun normalBody(a: HttpAnnounce): LinkedHashMap<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["complete"] = complete
        m["incomplete"] = incomplete
        m["interval"] = interval
        minInterval?.let { m["min interval"] = it }
        warningMessage?.let { m["warning message"] = it.toByteArray(Charsets.UTF_8) }
        val list = peers
        m["peers"] = if (a.compact == "1" && !forceDictPeers) {
            ByteArrayOutputStream().also { o -> list.mapNotNull(::fakeCompact4).forEach { o.write(it) } }.toByteArray()
        } else {
            val noPeerId = a.param("no_peer_id") == "1"
            list.mapIndexed { i, p ->
                val d = LinkedHashMap<String, Any>()
                d["ip"] = (p.address?.hostAddress ?: p.hostString).toByteArray(Charsets.US_ASCII)
                d["port"] = p.port.toLong()
                if (!noPeerId) d["peer id"] = dictPeerId(i)
                d
            }
        }
        val six = peers6.mapNotNull(::fakeCompact6)
        if (six.isNotEmpty()) m["peers6"] = ByteArrayOutputStream().also { o -> six.forEach { o.write(it) } }.toByteArray()
        return m
    }

    /** BEP 48 scrape — 청한 info_hash 마다 {complete, downloaded, incomplete}. */
    private fun scrapeBody(a: HttpAnnounce): Map<String, Any> {
        val files = LinkedHashMap<String, Any>()
        for (h in a.params["info_hash"].orEmpty()) {
            files[String(h, Charsets.ISO_8859_1)] = mapOf("complete" to complete, "downloaded" to 0L, "incomplete" to incomplete)
        }
        return mapOf("files" to files)
    }

    /**
     * 정상 사전에 "x-padding" 키(0 바이트들)를 더해 몸 전체를 [target] 바이트로 맞춘다.
     * 키는 바이트 차례여야 하므로 'x' 로 시작하는 이 키가 맨 끝에 온다.
     */
    private fun writeHuge(out: OutputStream, a: HttpAnnounce, target: Int, chunked: Boolean) {
        val base = FakeBencode.encode(normalBody(a))
        val prefix = base.copyOfRange(0, base.size - 1) + "9:x-padding".toByteArray(Charsets.US_ASCII)
        var padding = 0
        for (digits in 1..10) {
            val n = target.toLong() - prefix.size - digits - 1 - 1
            if (n >= 0 && n.toString().length == digits) {
                padding = n.toInt()
                break
            }
        }
        val lengthField = "$padding:".toByteArray(Charsets.US_ASCII)
        val total = prefix.size.toLong() + lengthField.size + padding + 1
        val headers = ArrayList<Pair<String, String>>()
        headers += "Content-Type" to "text/plain"
        if (chunked) headers += "Transfer-Encoding" to "chunked" else headers += "Content-Length" to total.toString()
        headers += "Connection" to "close"
        out.write(fakeHttpHead(200, headers))

        fun emit(bytes: ByteArray, len: Int = bytes.size) {
            if (len == 0) return
            if (chunked) out.write("${Integer.toHexString(len)}\r\n".toByteArray(Charsets.US_ASCII))
            out.write(bytes, 0, len)
            if (chunked) out.write("\r\n".toByteArray(Charsets.US_ASCII))
        }
        emit(prefix)
        emit(lengthField)
        val zeros = ByteArray(16 * 1024)
        var left = padding
        while (left > 0) {
            val n = minOf(left, zeros.size)
            emit(zeros, n)
            left -= n
        }
        emit(byteArrayOf('e'.code.toByte()))
        if (chunked) out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun writeRedirect(out: OutputStream, location: String) {
        val body = ByteArray(0)
        out.write(fakeHttpHead(302, listOf("Location" to location, "Content-Length" to "0", "Connection" to "close")))
        out.write(body)
        out.flush()
    }

    private fun writeSimple(out: OutputStream, status: Int, type: String, body: ByteArray) {
        out.write(fakeHttpHead(status, simpleHeaders(type, body.size.toLong())))
        out.write(body)
        out.flush()
    }

    private fun simpleHeaders(type: String, length: Long) =
        listOf("Content-Type" to type, "Content-Length" to length.toString(), "Connection" to "close")

    /** FIN 을 먼저 보내고 상대가 닫기를 잠깐 기다린다 — 읽지 않은 입력 때문에 RST 가 나가 답이 잘리지 않게. */
    private fun lingerClose(socket: Socket, input: BufferedInputStream) {
        try {
            socket.shutdownOutput()
            socket.soTimeout = 1_000
            val sink = ByteArray(4096)
            while (input.read(sink) >= 0) Unit
        } catch (_: SocketTimeoutException) {
        } catch (_: java.io.IOException) {
        }
    }

    private companion object {
        val INVALID_BENCODE: ByteArray = "d8:intervali1800e5:peers99:short".toByteArray(Charsets.US_ASCII)

        fun dictPeerId(i: Int): ByteArray = "-FK0000-%012d".format(i).toByteArray(Charsets.US_ASCII)
    }
}
