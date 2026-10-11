package io.github.donggi.iroirobrowser.torrent.session

import io.github.donggi.iroirobrowser.torrent.net.Clock
import java.io.Closeable
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 셀렉터에 건 채널이 준비됐을 때. */
internal interface IoHandler {
    fun onReady(key: SelectionKey)

    /** 처리하다 예외가 났다 — 그 채널만 닫는다. */
    fun onFailure(e: Exception)
}

/**
 * 엔진의 **이벤트 루프 — 스레드 하나**. 모든 피어 연결(비차단 TCP), DHT 와 UDP 트래커(비차단 UDP), 시계(시간 초과·주기 일)가 여기서 돈다.
 * 토렌트의 상태(조각 표·고르개·연결)는 이 스레드만 만진다 — 자물쇠가 없다.
 *
 * 왜 스레드 하나인가: 연결 60개를 연결마다 스레드로 하면 읽기·쓰기에 120개까지 늘고(안드로이드의 스레드는 스택만 1 MB 를 잡는다), 조각 표를
 * 함께 만지려면 자물쇠가 든다. 셀렉터 하나면 연결 수와 상관없이 스레드가 하나다. 막히는 일(디스크·이름 풀이·HTTP)은 따로 된 작은 일꾼 풀이
 * 하고 결과를 [post] 로 돌려보낸다.
 *
 * 시계는 [Clock] 이다 — 시험은 손으로 돌리는 시계를 주고 [wakeup] 으로 깨운다(시간 초과를 실제로 기다리지 않는다).
 */
internal class EventLoop(private val clock: Clock, private val log: (String) -> Unit) : Closeable {

    val selector: Selector = Selector.open()
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val timers = PriorityQueue<Timer>(compareBy<Timer>({ it.at }, { it.seq }))
    private var seq = 0L

    @Volatile
    private var thread: Thread? = null

    @Volatile
    var isClosed = false
        private set
    private val stopped = CountDownLatch(1)

    inner class Timer(val at: Long, val seq: Long, val task: () -> Unit) {
        var cancelled = false
            private set

        fun cancel() {
            cancelled = true
        }
    }

    fun now(): Long = clock.nowMs()

    fun start(name: String) {
        val t = Thread(::run, name)
        t.isDaemon = true
        thread = t
        t.start()
    }

    fun inLoop(): Boolean = Thread.currentThread() === thread

    /** 어느 스레드에서든 — 루프에서 돌 일을 넣는다. */
    fun post(task: Runnable) {
        if (isClosed) return
        tasks.add(task)
        selector.wakeup()
    }

    fun wakeup() {
        selector.wakeup()
    }

    /** 루프 스레드에서만. [delayMs] 뒤(엔진의 시계로)에 돈다. */
    fun schedule(delayMs: Long, task: () -> Unit): Timer {
        val t = Timer(now() + maxOf(0, delayMs), seq++, task)
        timers.add(t)
        return t
    }

    fun register(channel: SelectableChannel, ops: Int, handler: IoHandler): SelectionKey = channel.register(selector, ops, handler)

    private fun run() {
        try {
            while (!isClosed) {
                runTasks()
                runTimers()
                if (isClosed) break
                val next = timers.peek()
                val wait = if (next == null) 1000L else (next.at - now()).coerceIn(1L, 1000L)
                val n = if (tasks.isEmpty()) selector.select(wait) else selector.selectNow()
                if (n > 0) {
                    val it = selector.selectedKeys().iterator()
                    while (it.hasNext()) {
                        val key = it.next()
                        it.remove()
                        val h = key.attachment() as? IoHandler ?: continue
                        try {
                            if (key.isValid) h.onReady(key)
                        } catch (e: CancelledKeyException) {
                            // 닫힌 채널 — 무시
                        } catch (e: Exception) {
                            runCatching { h.onFailure(e) }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            log("event loop died: $e")
            isClosed = true
        } finally {
            runCatching { closeAll() }
            stopped.countDown()
        }
    }

    private fun runTasks() {
        var n = 0
        while (n++ < 10_000) {
            val t = tasks.poll() ?: return
            try {
                t.run()
            } catch (e: Exception) {
                log("task failed: $e")
            }
        }
    }

    private fun runTimers() {
        val now = now()
        while (true) {
            val t = timers.peek() ?: return
            if (t.at > now) return
            timers.poll()
            if (t.cancelled) continue
            try {
                t.task()
            } catch (e: Exception) {
                log("timer failed: $e")
            }
        }
    }

    private fun closeAll() {
        for (key in selector.keys()) {
            runCatching { key.channel().close() }
        }
        runCatching { selector.close() }
    }

    /** 루프를 멈춘다. 루프 스레드에서 부르면 기다리지 않는다. */
    override fun close() {
        if (isClosed) return
        isClosed = true
        selector.wakeup()
        if (!inLoop() && thread != null) stopped.await(5, TimeUnit.SECONDS)
    }
}
