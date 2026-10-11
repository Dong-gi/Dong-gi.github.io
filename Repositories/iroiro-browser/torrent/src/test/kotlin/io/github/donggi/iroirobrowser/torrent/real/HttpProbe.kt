package io.github.donggi.iroirobrowser.torrent.real

import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.HttpGet
import io.github.donggi.iroirobrowser.torrent.net.HttpLimits
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.net.Transport
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import javax.net.ssl.SSLSocketFactory

/** 실제 HTTPS 받기를 엔진의 HttpGet 과 JDK 의 HttpURLConnection(둘 다 프록시 없이)으로 번갈아 재 본다(사람이 돌린다). */
fun httpProbe(url: String, rounds: Int) {
    val http = HttpGet(Transport.DIRECT, Resolver.SYSTEM, AddressFilter.PUBLIC, SSLSocketFactory.getDefault() as SSLSocketFactory, null, "Web/0.1")
    repeat(rounds) { i ->
        val t0 = System.nanoTime()
        val ours = runCatching { http.fetch(url, Purpose.WEB_SEED, HttpLimits(maxBodyBytes = 10_000_000, totalTimeoutMs = 60_000)) }
        val t1 = System.nanoTime()
        println("#$i engine: ${ours.map { "${it.first} ${it.second.size} bytes" }.getOrElse { it.toString() }} in ${(t1 - t0) / 1_000_000} ms")
        val jdk = runCatching {
            val c = URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.inputStream.use { it.readBytes().size }.also { c.disconnect() }
        }
        println("#$i jdk:    ${jdk.map { "$it bytes" }.getOrElse { it.toString() }} in ${(System.nanoTime() - t1) / 1_000_000} ms")
    }
}
