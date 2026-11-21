package io.github.donggi.iroirobrowser.tether.core

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** 시험은 모두 루프백에서 돈다 — 바깥 망에 아무것도 나가지 않는다. */
internal val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")

/** 루프백 손님을 받는 거르개(실제 [PeerFilter] 는 루프백을 거절한다 — 그것은 따로 시험한다). */
internal val ANY_PEER = PeerGate { null }

/** 실제 [PeerFilter] — 그룹이 떠 있고 인터페이스도 맞고 로컬 망(99)으로 들어왔다고 답한다. 루프백은 주소 검사에서 걸린다. */
internal fun realPeerFilter(): PeerFilter =
    PeerFilter(groupInterface = { "p2p-wlan0-0" }, socketMark = { PeerFilter.LOCAL_NET_ID }, interfaceName = { "p2p-wlan0-0" })

/**
 * 시험의 목적지 거르개 — 이름은 [names] 로만 풀고(DNS 를 쓰지 않는다), IP 리터럴은 그대로, [blocked] 만 막는다(루프백을 허락해야 가짜
 * 원격에 닿는다). 무엇을 물었는지 [asked] 에 적는다.
 */
internal class TestDestinations(
    private val names: Map<String, List<InetAddress>> = emptyMap(),
    private val blocked: Set<InetAddress> = emptySet(),
) : DestinationPolicy {
    val asked = CopyOnWriteArrayList<String>()

    override fun resolve(host: String): Destination {
        asked += host
        val candidates = names[host] ?: if (looksLiteral(host)) listOf(InetAddress.getByName(host)) else emptyList()
        if (candidates.isEmpty()) return Destination.Unresolved("모르는 이름")
        val allowed = candidates.firstOrNull { it !in blocked } ?: return Destination.Rejected("막힘")
        return Destination.Allowed(allowed)
    }

    override fun isBlocked(address: InetAddress): Boolean = address in blocked

    private fun looksLiteral(host: String): Boolean = host.contains(':') || host.all { it.isDigit() || it == '.' }
}

/** 기록 줄을 모은다 — '목적지를 남기지 않는다' 를 시험이 본다. */
internal class LogLines : TetherLog {
    val lines = CopyOnWriteArrayList<String>()

    override fun write(level: TetherLog.Level, tag: String, message: String, error: Throwable?) {
        lines += "$level $tag $message ${error?.javaClass?.simpleName ?: ""}"
    }
}

internal class Counters {
    val bytesIn = AtomicLong()
    val bytesOut = AtomicLong()
    val rejections = CopyOnWriteArrayList<PeerRejection>()
    val refusals = CopyOnWriteArrayList<Refusal>()
    val died = CopyOnWriteArrayList<TcpServer>()
}

internal fun testEnv(
    destinations: DestinationPolicy = TestDestinations(),
    budget: ThreadBudget = ThreadBudget(),
    peers: PeerGate = ANY_PEER,
    log: TetherLog = TetherLog.NONE,
    outbound: Outbound = Outbound.DIRECT,
    counters: Counters = Counters(),
    timings: TetherTimings = TetherTimings(),
    nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
): TetherEnvironment = TetherEnvironment(
    peers = peers,
    destinations = destinations,
    outbound = outbound,
    budget = budget,
    log = log,
    bindAddress = LOOPBACK,
    timings = timings,
    nowMs = nowMs,
    onBytesIn = { counters.bytesIn.addAndGet(it) },
    onBytesOut = { counters.bytesOut.addAndGet(it) },
    onRejected = { counters.rejections += it },
    onRefused = { counters.refusals += it },
    onServerDied = { counters.died += it },
)

/**
 * 서버의 스레드에서 잡히지 않고 나간 예외. **안드로이드에서는 그런 예외 하나가 프로세스를 죽인다**(테더가 죽으면 PC 의 인터넷이 끊긴다) — JVM
 * 에서는 스레드만 끝나 조용히 지나가므로 시험마다 여기에 모아 없는지 본다([Closer.close]).
 */
internal object Uncaught {
    val errors = CopyOnWriteArrayList<String>()

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> errors += "${thread.name}: $error" }
    }

    fun install() = Unit
}

/** 닫을 것을 모아 시험 끝에 닫는다. 닫은 뒤 서버 스레드에서 잡히지 않은 예외가 없었는지 본다. */
internal class Closer : Closeable {
    private val items = ArrayList<() -> Unit>()

    init {
        Uncaught.install()
    }

    fun <T : Closeable> keep(c: T): T = c.also { items += { it.close() } }

    fun <T : TcpServer> keep(server: T): T = server.also { items += { it.stop() } }

    override fun close() {
        items.reversed().forEach { runCatching { it() } }
        // 닫으며 끝나는 중계 스레드가 던질 틈을 조금 준다.
        Thread.sleep(50)
        val found = Uncaught.errors.toList()
        Uncaught.errors.clear()
        check(found.isEmpty()) { "서버 스레드에서 잡히지 않은 예외(안드로이드에서는 프로세스가 죽는다): $found" }
    }
}

/** 받은 것을 그대로 돌려주는 TCP 원격. 손님이 쓰기를 닫으면(EOF) 이쪽도 쓰기를 닫는다. */
internal class EchoServer : Closeable {
    private val server = ServerSocket(0, 50, LOOPBACK)
    val port: Int get() = server.localPort
    val accepted = AtomicInteger()

    init {
        thread(isDaemon = true, name = "test-echo") {
            while (!server.isClosed) {
                val s = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                accepted.incrementAndGet()
                thread(isDaemon = true) {
                    s.use {
                        try {
                            val buf = ByteArray(8192)
                            val inp = it.getInputStream()
                            val out = it.getOutputStream()
                            while (true) {
                                val n = inp.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
                                out.flush()
                            }
                            it.shutdownOutput()
                            // 상대가 닫을 때까지 잠깐 둔다(반쯤 닫힘을 시험이 본다).
                            it.soTimeout = 2000
                            runCatching { inp.read() }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        server.close()
    }
}

/** 받은 데이터그램을 보낸 곳으로 그대로 돌려주는 UDP 원격. */
internal class UdpEcho : Closeable {
    val socket = DatagramSocket(0, LOOPBACK)
    val port: Int get() = socket.localPort
    val received = AtomicInteger()

    init {
        thread(isDaemon = true, name = "test-udp-echo") {
            val buf = ByteArray(65535)
            while (!socket.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(p)
                } catch (_: Exception) {
                    break
                }
                received.incrementAndGet()
                runCatching { socket.send(DatagramPacket(p.data, p.offset, p.length, p.address, p.port)) }
            }
        }
    }

    override fun close() {
        socket.close()
    }
}

/**
 * 손님이 쓰기를 닫을 때까지(반쯤 닫힘) 받고, 그 뒤에 [pieces] 를 [gapMs] 씩 띄워 천천히 보낸 뒤 닫는 원격 — '원격이 오래 걸려
 * 답한다' 를 짧게 꾸민 것. 받은 것을 [received] 에 둔다.
 */
internal class SlowReplyServer(private val pieces: List<String>, private val gapMs: Long) : Closeable {
    private val server = ServerSocket(0, 50, LOOPBACK)
    val port: Int get() = server.localPort
    val received = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true, name = "test-slow") {
            while (!server.isClosed) {
                val s = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) {
                    s.use {
                        try {
                            received += String(readAll(it.getInputStream()), Charsets.ISO_8859_1)
                            val out = it.getOutputStream()
                            for (piece in pieces) {
                                Thread.sleep(gapMs)
                                out.write(piece.toByteArray(Charsets.ISO_8859_1))
                                out.flush()
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        server.close()
    }
}

/** 요청 머리를 적고 정한 본문으로 답하는 HTTP 원격(연결마다 요청 하나). */
internal class OriginServer(private val body: String = "origin-body") : Closeable {
    private val server = ServerSocket(0, 50, LOOPBACK)
    val port: Int get() = server.localPort
    val heads = CopyOnWriteArrayList<String>()
    val bodies = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true, name = "test-origin") {
            while (!server.isClosed) {
                val s = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) {
                    s.use {
                        try {
                            it.soTimeout = 5000
                            val inp = it.getInputStream()
                            val head = readHead(inp)
                            heads += head
                            val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                            bodies += String(readExactly(inp, length), Charsets.UTF_8)
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            it.getOutputStream().write(
                                ("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1) + bytes,
                            )
                            it.getOutputStream().flush()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        server.close()
    }
}

/**
 * '닿지 못하는 원격' 으로 쓰는 루프백 포트 — **듣지 않는 소켓으로 묶어 쥔다**(쥔 소켓을 돌려준다 — 시험이 [Closer] 로 닫는다). 쥐고 있는 동안
 * 그 포트는 아무도 묶지 못하고 OS 의 임시 포트로도 나가지 않으며, 거기로 잇기는 거절된다(루프백 — 바깥 망으로 나가지 않는다).
 *
 * 예전에는 묶었다 곧 놓은 번호를 썼다 — 놓은 뒤에는 그 포트가 다시 아무의 것이 될 수 있어, 그 틈에 누가 그 포트에서 듣거나 서버의 바깥
 * 연결이 그 포트를 제 임시 포트로 받아 자기 자신에 이어지면(TCP 동시 열기) '닿지 못함' 이 이어져 버린다.
 */
internal fun refusingPort(): Socket = Socket(java.net.Proxy.NO_PROXY).apply { bind(java.net.InetSocketAddress(LOOPBACK, 0)) }

/** 그 포트가 비어 있는가(묶어 보고 곧 닫는다) — SOCKS5 의 '옮기지 않는다' 시험이 다음 포트를 건드리지 않았는지 본다. */
internal fun isFree(port: Int): Boolean = runCatching { ServerSocket(port, 1, LOOPBACK).close() }.isSuccess

/**
 * 포트 옮기기 시험의 자리 — [squatter] 가 [base] 를 쥐고(남의 앱 흉내), 그 다음 포트도 **시험이 쥐고 있다가** 서버를 세우기 바로 앞에서
 * 놓는다([handOver]). SOCKS5 는 옮기지 않아야 하므로 다음 포트가 비어 있어야 '옮겼다' 는 되돌림이 드러나고, HTTP·파일은 옮겨야 하므로 다음
 * 포트가 비어 있어야 그리로 간다.
 *
 * 자리는 **OS 의 임시 포트 범위 밖**(20000–29999 — 윈도 49152–65535·리눅스 32768–60999 의 아래)에서 고른다. 예전 꼴(`ServerSocket(0)`
 * 의 자리 + 다음 포트를 열어 보고 곧 닫기)은 윈도에서 다음 포트가 **곧 이 PC 가 다음에 나눠 줄 임시 포트**였다(임시 포트를 차례로 준다 —
 * 그 자리 다음의 연결이 100번에 100번 base+1 을 받았다, 2026-11-21). 놓은 뒤 서버가 묶기까지의 틈에 다른 곳의 `ServerSocket(0)` 이 끼면
 * (Gradle 이 다른 모듈의 시험을 함께 돌린다 — `org.gradle.parallel`) 그 듣는 소켓이 base+1 을 가져가 서버가 서지 못했다 — 그 틈에
 * `ServerSocket(0)` 하나를 끼운 시험에서 옛 꼴은 20번에 20번 졌고 이 꼴은 0번이었다(HttpProxyServerTest 가 가끔 실패한 까닭으로 본다. 바깥
 * 연결의 임시 포트는 윈도에서 듣는 소켓의 묶기를 막지 않았다). 범위 밖의 포트는 누가 그 번호를 일부러 묶지 않는 한 남에게 가지 않는다.
 * 묶지 못하는 번호(쓰는 중·윈도의 예약 범위)는 건너뛴다.
 */
internal class PortPair private constructor(val squatter: ServerSocket, private var next: ServerSocket?) : java.io.Closeable {
    val base: Int get() = squatter.localPort

    /** 쥔 다음 포트를 놓고 곧바로 [start] 를 부른다(서버를 세우기). */
    fun <T> handOver(start: () -> T): T {
        next?.close()
        next = null
        return start()
    }

    override fun close() {
        next?.close()
        squatter.close()
    }

    companion object {
        fun squat(): PortPair {
            val random = java.util.Random()
            repeat(500) {
                val port = 20_000 + random.nextInt(10_000)
                val first = runCatching { ServerSocket(port, 1, LOOPBACK) }.getOrNull() ?: return@repeat
                val second = runCatching { ServerSocket(port + 1, 1, LOOPBACK) }.getOrNull()
                if (second != null) return PortPair(first, second)
                first.close()
            }
            error("이어진 빈 포트 둘을 찾지 못했다")
        }
    }
}

/** [condition] 이 설 때까지 기다린다. */
internal fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        if (condition()) return
        Thread.sleep(20)
    }
    kotlin.test.fail("조건이 제때 서지 않았다")
}

/** 빈 줄까지의 HTTP 머리(CRLF 포함 글). */
internal fun readHead(inp: InputStream): String {
    val out = ByteArrayOutputStream()
    var last4 = 0
    while (true) {
        val b = inp.read()
        if (b == -1) break
        out.write(b)
        last4 = (last4 shl 8) or b
        if (last4 == 0x0D0A0D0A) break
    }
    return out.toString("ISO-8859-1")
}

internal fun readExactly(inp: InputStream, n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = inp.read(buf, off, n - off)
        if (r == -1) break
        off += r
    }
    return if (off == n) buf else buf.copyOf(off)
}

internal fun readAll(inp: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (true) {
        val n = try {
            inp.read(buf)
        } catch (_: Exception) {
            -1
        }
        if (n == -1) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** 머리와 본문으로 가른 HTTP 답. */
internal class HttpReply(val status: Int, val head: String, val body: ByteArray) {
    fun header(name: String): String? =
        head.lines().firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()

    val text: String get() = String(body, Charsets.UTF_8)
}

/** [port] 에 [request] 를 그대로 보내고 연결이 닫힐 때까지 받은 답. */
internal fun rawHttp(port: Int, request: ByteArray, timeoutMs: Int = 5000): HttpReply {
    Socket(LOOPBACK, port).use { s ->
        s.soTimeout = timeoutMs
        s.getOutputStream().write(request)
        s.getOutputStream().flush()
        val all = readAll(s.getInputStream())
        return parseReply(all)
    }
}

internal fun parseReply(all: ByteArray): HttpReply {
    val text = String(all, Charsets.ISO_8859_1)
    val split = text.indexOf("\r\n\r\n")
    if (split < 0) return HttpReply(-1, text, ByteArray(0))
    val head = text.substring(0, split)
    val status = head.substringAfter(' ').substringBefore(' ').toIntOrNull() ?: -1
    return HttpReply(status, head, all.copyOfRange(split + 4, all.size))
}

/** 시험의 공유 폴더 — 임시 폴더 위의 [SharedTree]. 성분마다 자식만 찾아 내려간다(앱의 SAF 구현과 같은 꼴). */
internal class DirectoryTree(private val root: File, var available: Boolean = true, var nativeMove: Boolean = true) : SharedTree {
    override fun isAvailable(): Boolean = available && root.isDirectory

    override fun rootName(): String = root.name

    override fun find(path: List<String>): SharedEntry? {
        var current = root
        for (segment in path) {
            current = current.listFiles()?.firstOrNull { it.name == segment } ?: return null
        }
        return entry(current)
    }

    override fun list(directory: SharedEntry): List<SharedEntry> =
        (File(directory.ref).listFiles() ?: error("목록을 읽지 못했다")).map { entry(it) }

    override fun openRead(file: SharedEntry): InputStream = FileInputStream(File(file.ref))

    /** 원래 파일은 건드리지 않고 같은 폴더에 숨은 임시 파일을 만든다(이름이 겹치면 번호를 붙인다 — 앱의 SAF 가 그러듯). */
    override fun createUploadTemp(path: List<String>): SharedEntry? {
        if (path.isEmpty()) return null
        val parent = find(path.dropLast(1)) ?: return null
        if (!parent.isDirectory) return null
        if (File(parent.ref, path.last()).isDirectory) return null
        val base = SharedPaths.uploadTempName(path.last())
        for (n in 0 until 100) {
            val temp = File(parent.ref, if (n == 0) base else "$base.$n")
            if (temp.createNewFile()) return entry(temp)
        }
        error("임시 파일을 만들지 못했다")
    }

    override fun openWrite(file: SharedEntry): OutputStream = FileOutputStream(File(file.ref), false)

    /** 같은 이름(대소문자 무시)이 있으면 지우지 않고 임시를 지운 뒤 null(계약 — 덮어쓰기는 파일 서버가 휴지통으로 옮긴 뒤에 부른다). */
    override fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry? {
        val parent = find(path.dropLast(1))
        val taken = parent == null || File(parent.ref).listFiles()?.any { it.name.equals(path.last(), ignoreCase = true) } != false
        if (taken) {
            File(temp.ref).delete()
            return null
        }
        val target = File(parent!!.ref, path.last())
        return try {
            // 바꿔 끼우지 않는 옮기기 — 그새 생긴 같은 이름을 덮지 않는다(File.renameTo 는 리눅스에서 덮는다).
            Files.move(File(temp.ref).toPath(), target.toPath())
            entry(target)
        } catch (e: java.io.IOException) {
            null
        }
    }

    override fun delete(entry: SharedEntry): Boolean = File(entry.ref).deleteRecursively()

    override fun createDirectory(parent: SharedEntry, name: String): SharedEntry {
        val dir = File(parent.ref, name)
        check(!dir.exists() && dir.mkdir()) { "폴더를 만들지 못했다" }
        return entry(dir)
    }

    override fun createFile(parent: SharedEntry, name: String): SharedEntry {
        val file = File(parent.ref, name)
        check(file.createNewFile()) { "파일을 만들지 못했다" }
        return entry(file)
    }

    override fun rename(entry: SharedEntry, newName: String): SharedEntry {
        val from = File(entry.ref)
        val to = File(from.parentFile, newName)
        check(!to.exists() || to.canonicalPath == from.canonicalPath) { "같은 이름이 있다" }
        check(from.renameTo(to)) { "이름을 바꾸지 못했다" }
        return entry(to)
    }

    /** 제공자의 옮기기 흉내 — [nativeMove] 가 꺼져 있으면 null(복사 뒤 지우기 길을 시험한다). 같은 이름이 있으면 던진다(SAF 처럼). */
    override fun move(entry: SharedEntry, fromParent: SharedEntry, toParent: SharedEntry): SharedEntry? {
        if (!nativeMove) return null
        val to = File(toParent.ref, entry.name)
        check(!to.exists()) { "같은 이름이 있다" }
        check(File(entry.ref).renameTo(to)) { "옮기지 못했다" }
        return entry(to)
    }

    /** 제공자의 복사는 하지 않는 꼴 — 늘 손으로 짓는 길을 탄다. */
    override fun copy(entry: SharedEntry, toParent: SharedEntry): SharedEntry? = null

    override fun storage(): Storage? = Storage(free = 5_000_000_000, total = 64_000_000_000)

    /** 작은 그림 파일은 그대로 섬네일로(시험·개발용 — 앱은 제공자의 섬네일). */
    override fun thumbnail(entry: SharedEntry, sizePx: Int): ByteArray? {
        val file = File(entry.ref)
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("jpg", "jpeg", "png", "gif", "webp") || file.length() > Thumbnails.MAX_BYTES) return null
        thumbnailCalls.incrementAndGet()
        return file.readBytes()
    }

    val thumbnailCalls = AtomicInteger()

    private fun entry(f: File) = SharedEntry(f.path, f.name, f.isDirectory, if (f.isDirectory) 0 else f.length(), f.lastModified())
}
