package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.Clock
import io.github.donggi.iroirobrowser.torrent.net.PublicAddresses
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 손으로 돌리는 시계 — 시간 초과를 실제로 기다리지 않는다. */
class ManualClock(start: Long = 1_000_000L) : Clock {
    private val now = AtomicLong(start)
    override fun nowMs(): Long = now.get()
    fun advance(ms: Long) {
        now.addAndGet(ms)
    }
}

/**
 * 시험용 거르개 — 공용 주소 규칙에 더해 **루프백(127/8)의 정한 포트만** 허락한다(가짜 군집의 서버들 — 엔진이 같은 IP 에 하나만 붙으므로
 * 가짜들은 127.0.0.2·127.0.0.3 … 에도 선다). 그 밖의 루프백 포트·사설 주소·169.254.169.254 는 그대로 거른다 — 적대 대본이 그것을 본다.
 * 무엇을 물었는지 적는다.
 */
class LoopbackFilter(vararg ports: Int) : AddressFilter {
    val allowed: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>().also { it.addAll(ports.toList()) }
    val asked: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    val refused: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

    override fun allows(address: InetAddress, port: Int, purpose: Purpose): Boolean {
        asked += "${address.hostAddress}:$port/$purpose"
        val loopback = address is Inet4Address && address.address[0].toInt() == 127
        val ok = if (loopback) port in allowed else PublicAddresses.allows(address, port, purpose)
        if (!ok) refused += "${address.hostAddress}:$port/$purpose"
        return ok
    }
}

/** 파이썬 벡터의 내용 규칙 — block(c) = SHA1("iroiro-" + seed + "-" + c). */
fun vectorPayload(seed: Int, length: Long): ByteArray {
    val out = ByteArray(length.toInt())
    var c = 0
    var off = 0
    while (off < out.size) {
        val block = MessageDigest.getInstance("SHA-1").digest("iroiro-$seed-$c".toByteArray(Charsets.UTF_8))
        val n = minOf(block.size, out.size - off)
        System.arraycopy(block, 0, out, off, n)
        off += n
        c++
    }
    return out
}

/** 루프백의 다른 주소(127.0.0.n) — 같은 IP 에 하나만 붙는 엔진에 가짜 시더 여럿을 보이려고. */
fun lo(n: Int): InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, n.toByte()))

fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

fun sha1(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(b)

fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

/** 조건이 참이 될 때까지(실제 시간) 기다린다. */
fun waitUntil(timeoutMs: Long = 10_000, step: Long = 20, what: String = "condition", cond: () -> Boolean) {
    val end = System.nanoTime() / 1_000_000 + timeoutMs
    while (System.nanoTime() / 1_000_000 < end) {
        if (cond()) return
        Thread.sleep(step)
    }
    if (!cond()) throw AssertionError("timed out waiting for $what")
}
