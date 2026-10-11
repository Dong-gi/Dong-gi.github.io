package io.github.donggi.iroirobrowser.torrent.real

import io.github.donggi.iroirobrowser.torrent.dht.Dht
import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.Clock
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.net.Transport
import io.github.donggi.iroirobrowser.torrent.session.EventLoop
import io.github.donggi.iroirobrowser.torrent.util.Hex
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 실제 DHT 에 찾기 하나(사람이 돌린다 — 실제 인터넷 시험의 일부). 엔진과 같은 [Dht] 로, 응답·피어가 언제 오는지 찍는다.
 * `./gradlew :torrent:realSwarm --offline -Pargs="dht <info-hash 16진>"`(RealSwarm 이 이리로 넘긴다).
 */
fun dhtProbe(infoHashHex: String, rounds: Int = 2) {
    val loop = EventLoop(Clock.SYSTEM) { println("  log: $it") }
    loop.start("probe-net")
    val io = Executors.newFixedThreadPool(2)
    val dht = Dht(loop, Transport.DIRECT, Resolver.SYSTEM, AddressFilter.PUBLIC, io, SecureRandom(), Dht.BOOTSTRAP) { println("  dht: $it") }
    val ih = Hex.decode(infoHashHex)!!
    repeat(rounds) { round ->
        val t0 = System.nanoTime()
        val done = CountDownLatch(1)
        var first = -1L
        var total = 0
        loop.post {
            dht.lookup(ih, onPeers = { peers ->
                total += peers.size
                if (first < 0) first = (System.nanoTime() - t0) / 1_000_000
            }, onDone = { n ->
                println("round $round: done in ${(System.nanoTime() - t0) / 1_000_000} ms, peers $n (first at $first ms), responses ${dht.responses}/${dht.queriesSent} sent, timeouts ${dht.timeouts}, known nodes ${dht.knownNodes}")
                done.countDown()
            })
        }
        while (!done.await(5, TimeUnit.SECONDS)) {
            println("  … ${(System.nanoTime() - t0) / 1_000_000} ms: responses ${dht.responses}/${dht.queriesSent}, timeouts ${dht.timeouts}, peers so far $total, known ${dht.knownNodes}")
        }
    }
    loop.post { dht.close() }
    Thread.sleep(300)
    loop.close()
    io.shutdownNow()
}
