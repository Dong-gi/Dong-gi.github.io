package io.github.donggi.iroirobrowser.torrent.real

import io.github.donggi.iroirobrowser.torrent.TorrentConfig
import io.github.donggi.iroirobrowser.torrent.TorrentEngine
import io.github.donggi.iroirobrowser.torrent.TorrentEnvironment
import io.github.donggi.iroirobrowser.torrent.TorrentListener
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.HttpGet
import io.github.donggi.iroirobrowser.torrent.net.HttpLimits
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.net.Transport
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl.SSLSocketFactory

/**
 * **실제 인터넷 시험**(사용자 승인 — 합법 공개 토렌트로). 보통 `test` 에는 들지 않고 사람이 돌린다:
 *
 * ```
 * ./gradlew :torrent:realSwarm --offline -Pargs="inspect https://…/x.torrent <작업 폴더>"
 * ./gradlew :torrent:realSwarm --offline -Pargs="torrent https://…/x.torrent <작업 폴더> <받을 MB> <초> [파일 번호,…]"
 * ./gradlew :torrent:realSwarm --offline -Pargs="magnet <마그넷 — & 는 %26 으로> <작업 폴더> <받을 MB> <초>"
 * ```
 * 엔진은 앱과 같은 설정(프록시 없음 — `Proxy.NO_PROXY`, 공용 주소만, MSE 먼저, DHT 읽기 전용)으로 돈다. .torrent 도 엔진의 HTTP 로 바로 받는다.
 * 정한 바이트를 **해시까지 확인하면** 멈추고(트래커에 stopped) 엔진을 닫는다 — 이 PC 의 IP 가 그 군집에 드러난다(사용자 승인).
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "dht") return dhtProbe(args[1], args.getOrNull(2)?.toInt() ?: 2)
    if (args.firstOrNull() == "http") return httpProbe(args[1], args.getOrNull(2)?.toInt() ?: 3)
    require(args.size >= 3) { "mode url work [mb] [seconds] [files]" }
    val mode = args[0]
    val source = args[1].replace("%26", "&")
    val work = File(args[2])
    val limitBytes = (args.getOrNull(3)?.toLong() ?: 20L) * 1024 * 1024
    val seconds = args.getOrNull(4)?.toLong() ?: 300L
    val files = args.getOrNull(5)?.takeIf { it != "-" }?.split(',')?.filter { it.isNotBlank() }?.map { it.toInt() }?.toSet()
    val noDht = args.getOrNull(6) == "nodht"

    if (mode == "inspect") {
        val m = Metainfo.parse(fetch(source))
        println("name=${m.name} infohash=${m.infoHashHex} pieces=${m.pieceCount}x${m.pieceLength} total=${m.totalLength}")
        println("trackers=${m.trackers}")
        println("webseeds=${m.webSeeds} httpseeds=${m.httpSeeds} hybrid=${m.isHybrid}")
        for (f in m.files) println("  #${f.index} ${f.length} ${f.displayPath}${if (f.isPad) " [pad]" else ""}")
        return
    }

    work.mkdirs()
    val events = ConcurrentLinkedQueue<String>()
    val listener = object : TorrentListener {
        override fun onFileCompleted(id: String, fileIndex: Int, file: File) {
            events += "file-completed #$fileIndex ${file.length()} bytes"
        }

        override fun onFinished(id: String) {
            events += "finished"
        }

        override fun onError(id: String, error: io.github.donggi.iroirobrowser.torrent.TorrentError, detail: String?) {
            events += "error $error $detail"
        }

        override fun onMetadata(id: String, info: io.github.donggi.iroirobrowser.torrent.TorrentInfo) {
            events += "metadata ${info.name} files=${info.files.size} total=${info.totalLength}"
        }
    }
    val logLines = ConcurrentLinkedQueue<String>()
    val logT0 = System.nanoTime()
    val env = TorrentEnvironment(log = { line ->
        logLines += line
        if ("closed:" !in line) println("  [${(System.nanoTime() - logT0) / 1_000_000} ms] log: $line")
    })
    val engine = TorrentEngine(work, TorrentConfig(dht = !noDht), env, listener)
    val restored = engine.start()
    val t0 = System.nanoTime()
    fun ms() = (System.nanoTime() - t0) / 1_000_000
    val id = when (mode) {
        "torrent" -> {
            val bytes = fetch(source)
            println("[${ms()} ms] .torrent ${bytes.size} bytes")
            val meta = Metainfo.parse(bytes)
            if (restored.any { it.id == meta.infoHashHex }) {
                engine.resume(meta.infoHashHex) // 앞서 받던 것 — 이어 받는다(되살린 것은 멈춤이다)
                meta.infoHashHex
            } else {
                engine.add(bytes, files)
            }
        }
        "resume" -> {
            // 저장된 상태에서 되살린 첫 토렌트를 이어 받는다(앱이 죽었다 다시 뜬 것과 같다 — 되살린 것은 멈춤이다).
            val r = restored.firstOrNull() ?: error("nothing to resume in $work")
            println("restored ${r.id} state ${r.state} done ${r.doneBytes}")
            engine.resume(r.id)
            r.id
        }
        "magnet" -> {
            val ih = engine.inspectMagnet(source).infoHashHex
            if (restored.any { it.id == ih }) {
                engine.resume(ih)
                ih
            } else {
                engine.addMagnet(source)
            }
        }
        else -> error("mode $mode")
    }
    println("[${ms()} ms] added $id")
    // 0.5초마다 본다(상한을 넘겨 받지 않게 — 앞 판은 2초마다라 빠른 군집에서 몇십 MB 를 더 받았다). 출력은 2초마다.
    var firstPeerAt = -1L
    var firstTrackerAt = -1L
    var last: TorrentStatus? = null
    var stopReason = "timeout"
    var peakRate = 0L
    var tick = 0
    while ((System.nanoTime() - t0) / 1_000_000_000 < seconds) {
        Thread.sleep(500)
        val s = engine.status(id) ?: break
        last = s
        if (firstTrackerAt < 0 && s.trackers.any { it.state == "ok" || it.state == "error" }) firstTrackerAt = ms()
        if (firstPeerAt < 0 && s.connectedPeers > 0) firstPeerAt = ms()
        peakRate = maxOf(peakRate, s.downloadRate)
        if (++tick % 4 == 0) {
            while (true) println("  event: ${events.poll() ?: break}")
            println(line(s, (System.nanoTime() - t0) / 1_000_000))
        }
        if (s.state == TorrentState.COMPLETED) {
            stopReason = "completed"
            break
        }
        if (s.state == TorrentState.ERROR) {
            stopReason = "error ${s.error} ${s.errorDetail}"
            break
        }
        if (s.doneBytes >= limitBytes) {
            stopReason = "limit reached (${s.doneBytes} verified bytes)"
            break
        }
    }
    println("first tracker answer $firstTrackerAt ms, first peer $firstPeerAt ms")
    val elapsed = (System.nanoTime() - t0) / 1_000_000
    engine.pause(id) // 트래커에 stopped, 모든 연결을 끊는다
    Thread.sleep(3_000)
    val s = engine.status(id) ?: last
    engine.close()
    println("=== result ===")
    println("stop: $stopReason after ${elapsed / 1000}s")
    if (s != null) {
        println("verified ${s.doneBytes} / wanted ${s.wantedBytes}, downloaded ${s.downloadedBytes}, hash failures ${s.hashFailures}")
        println("peers known ${s.knownPeers}, sources ${s.peerSources}, peak rate ${peakRate / 1024} KiB/s, avg ${if (elapsed > 0) s.downloadedBytes * 1000 / elapsed / 1024 else 0} KiB/s")
        println("mse attempts ${s.mseAttempts}, mse handshakes ${s.mseSucceeded}, plain fallbacks ${s.plainFallbacks}")
        for (t in (last ?: s).trackers) println("tracker ${t.url} state=${t.state} peers=${t.lastPeers} seeders=${t.seeders} leechers=${t.leechers} error=${t.lastError}")
        println("connected now ${s.connectedPeers}, state ${s.state}")
    }
    val closes = logLines.filter { "closed:" in it }
    println("connections closed: ${closes.size}; reasons: " + closes.map { it.substringAfter("closed: ").substringBefore(':').take(40) }.groupingBy { it }.eachCount())
    for (l in logLines.filter { "closed:" !in it }.take(40)) println("  log: $l")
}

private fun line(s: TorrentStatus, ms: Long): String {
    val active = s.files.firstOrNull { it.index == s.activeFile }
    return "[${ms / 1000}s] ${s.state} peers ${s.connectedPeers}/${s.knownPeers} seeds ${s.seeds} ws ${s.webSeeds} " +
        "rate ${s.downloadRate / 1024} KiB/s done ${s.doneBytes / 1024} KiB dl ${s.downloadedBytes / 1024} KiB " +
        "src ${s.peerSources} mse ${s.mseSucceeded}/${s.mseAttempts} fb ${s.plainFallbacks} " +
        "active ${active?.path?.takeLast(40)} trackers ${s.trackers.joinToString { "${it.state}:${it.lastPeers}" }}"
}

/** .torrent 를 엔진과 같은 길(프록시 없이, 공용 주소만)로 받는다. */
private fun fetch(url: String): ByteArray {
    val http = HttpGet(Transport.DIRECT, Resolver.SYSTEM, AddressFilter.PUBLIC, SSLSocketFactory.getDefault() as SSLSocketFactory, null, "Web/0.1")
    val (status, body) = http.fetch(url, Purpose.WEB_SEED, HttpLimits(maxBodyBytes = 10_000_000, totalTimeoutMs = 60_000, maxRedirects = 3))
    check(status == 200) { "http $status" }
    return body
}
