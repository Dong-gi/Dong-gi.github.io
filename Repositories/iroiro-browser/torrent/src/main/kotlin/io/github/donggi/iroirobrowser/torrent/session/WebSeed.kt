package io.github.donggi.iroirobrowser.torrent.session

import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.meta.TorrentFile
import io.github.donggi.iroirobrowser.torrent.net.HttpException
import io.github.donggi.iroirobrowser.torrent.net.HttpGet
import io.github.donggi.iroirobrowser.torrent.net.HttpLimits
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.piece.Requester
import io.github.donggi.iroirobrowser.torrent.wire.Wire
import java.io.IOException
import java.io.InputStream

/**
 * 웹 시드 하나 — BEP 19(GetRight 꼴 `url-list`, 파일 주소에 `Range`) 또는 BEP 17(Hoffman 꼴 `httpseeds`, `?info_hash=…&piece=N`).
 * 웹 시드는 모든 조각을 가진 것으로 본다. 해시가 틀린 조각을 준 웹 시드는 **버린다**(사용자 결정 그대로). 이벤트 루프가 상태를 쓰고,
 * 받기([WebSeedFetch])는 일꾼 스레드가 한다.
 */
internal class WebSeed(val url: String, val bep17: Boolean) : Requester {
    var busy = false
    var disabled = false
    var failures = 0
    var nextAt = 0L
    var lastError: String? = null
    var downloaded = 0L

    @Volatile
    var cancelled = false

    override fun toString(): String = "webseed $url"
}

/** 받아 온 바이트를 조각의 16 KiB 블록으로 잘라 넘긴다. */
internal class BlockAssembler(private val meta: Metainfo, firstPiece: Int, private val deliver: (Int, Int, ByteArray) -> Unit) {
    private var piece = firstPiece
    private var begin = 0
    private var buf = ByteArray(blockLength())
    private var fill = 0

    private fun blockLength(): Int = minOf(Wire.BLOCK, meta.pieceSize(piece) - begin)

    fun write(data: ByteArray, offset: Int, length: Int) {
        var off = offset
        var left = length
        while (left > 0) {
            val n = minOf(left, buf.size - fill)
            System.arraycopy(data, off, buf, fill, n)
            fill += n
            off += n
            left -= n
            if (fill == buf.size) emit()
        }
    }

    fun zeros(count: Long) {
        var left = count
        val z = ByteArray(Wire.BLOCK)
        while (left > 0) {
            val n = minOf(left, z.size.toLong()).toInt()
            write(z, 0, n)
            left -= n
        }
    }

    private fun emit() {
        deliver(piece, begin, buf)
        begin += buf.size
        if (begin >= meta.pieceSize(piece)) {
            piece++
            begin = 0
        }
        fill = 0
        buf = if (piece < meta.pieceCount) ByteArray(blockLength()) else ByteArray(0)
    }
}

/** 웹 시드에서 조각들을 받는다(일꾼 스레드, 막히는 호출). */
internal object WebSeedFetch {

    class Failure(message: String, val permanent: Boolean) : IOException(message)

    /**
     * [run] 의 조각들을 받아 블록마다 [deliver](조각, 시작, 바이트 — 막힐 수 있다: 디스크 역압). 실패하면 [Failure].
     */
    fun fetch(
        http: HttpGet,
        meta: Metainfo,
        ws: WebSeed,
        run: IntRange,
        deliver: (Int, Int, ByteArray) -> Unit,
    ) {
        val cancelled = { ws.cancelled }
        if (ws.bep17) {
            for (p in run) {
                if (ws.cancelled) throw Failure("cancelled", permanent = false)
                val sep = if ('?' in ws.url) '&' else '?'
                val url = "${ws.url}${sep}info_hash=${HttpGet.Percent.encode(meta.infoHash)}&piece=$p"
                val size = meta.pieceSize(p)
                val assembler = BlockAssembler(meta, p, deliver)
                get(http, url, null, size.toLong(), cancelled) { input -> pump(input, size.toLong(), assembler, cancelled) }
            }
            return
        }
        val start = meta.pieceOffset(run.first)
        val end = meta.pieceOffset(run.last) + meta.pieceSize(run.last)
        val assembler = BlockAssembler(meta, run.first, deliver)
        var pos = start
        var fi = meta.fileAt(pos)
        while (pos < end) {
            if (ws.cancelled) throw Failure("cancelled", permanent = false)
            if (fi >= meta.files.size) throw Failure("past end", permanent = true)
            val f = meta.files[fi]
            if (f.length == 0L || pos >= f.end) {
                fi++
                continue
            }
            val segEnd = minOf(end, f.end)
            val count = segEnd - pos
            if (f.isPad) {
                assembler.zeros(count)
            } else {
                val from = pos - f.offset
                val to = segEnd - f.offset - 1
                val wholeFile = from == 0L && to == f.length - 1
                get(http, fileUrl(ws.url, meta, f), "bytes=$from-$to", count, cancelled, wholeFileOk = wholeFile) { input ->
                    pump(input, count, assembler, cancelled)
                }
            }
            pos = segEnd
            fi++
        }
    }

    /** BEP 19 — 한 파일 토렌트면 주소가 그 파일이다(`/` 로 끝나면 이름을 붙인다). 여러 파일이면 주소 + 이름 + `/` + 경로. */
    fun fileUrl(base: String, meta: Metainfo, f: TorrentFile): String {
        if (!meta.isMultiFile) return if (base.endsWith("/")) base + HttpGet.Percent.encode(meta.urlName) else base
        val sb = StringBuilder(base)
        if (!base.endsWith("/")) sb.append('/')
        sb.append(HttpGet.Percent.encode(meta.urlName))
        for (c in f.urlPath) sb.append('/').append(HttpGet.Percent.encode(c))
        return sb.toString()
    }

    private fun get(
        http: HttpGet,
        url: String,
        range: String?,
        length: Long,
        cancelled: () -> Boolean,
        wholeFileOk: Boolean = true,
        body: (InputStream) -> Unit,
    ) {
        val limits = HttpLimits(
            maxBodyBytes = length,
            connectTimeoutMs = 10_000,
            idleTimeoutMs = 20_000,
            totalTimeoutMs = 30_000 + length / 16, // 16 KB/s 보다 느리면 끊는다
            maxRedirects = 3,
        )
        val headers = if (range != null) mapOf("Range" to range) else emptyMap()
        val response = try {
            http.open(url, Purpose.WEB_SEED, limits, headers, cancelled)
        } catch (e: HttpException) {
            // TOO_LARGE 는 범위를 무시하고 파일 통째의 길이를 알린 서버다(Range 를 모른다).
            val permanent = e.kind == HttpException.Kind.BLOCKED_ADDRESS || e.kind == HttpException.Kind.BAD_URL ||
                e.kind == HttpException.Kind.TOO_MANY_REDIRECTS || e.kind == HttpException.Kind.TOO_LARGE
            throw Failure(e.message ?: "http", permanent)
        }
        response.use { r ->
            when {
                r.status == 206 && range != null -> {
                    val cr = r.header("content-range")
                    if (cr != null && !cr.trim().lowercase().startsWith(range.lowercase().replace("bytes=", "bytes ") + "/")) {
                        throw Failure("content-range mismatch: $cr", permanent = true)
                    }
                }
                r.status == 200 && (range == null || wholeFileOk) -> Unit
                r.status == 200 -> throw Failure("range not supported", permanent = true)
                r.status == 404 || r.status == 410 -> throw Failure("http ${r.status}", permanent = true)
                else -> throw Failure("http ${r.status}", permanent = false)
            }
            try {
                body(r.body)
            } catch (e: HttpException) {
                throw Failure(e.message ?: "http", permanent = e.kind == HttpException.Kind.TOO_LARGE)
            }
        }
    }

    private fun pump(input: InputStream, length: Long, assembler: BlockAssembler, cancelled: () -> Boolean) {
        val buf = ByteArray(64 * 1024)
        var left = length
        while (left > 0) {
            if (cancelled()) throw Failure("cancelled", permanent = false)
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw Failure("short body", permanent = false)
            assembler.write(buf, 0, n)
            left -= n
        }
    }
}
