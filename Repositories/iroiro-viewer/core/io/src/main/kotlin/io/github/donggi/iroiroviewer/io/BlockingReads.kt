package io.github.donggi.iroiroviewer.io

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * **남의 제공자에 막히지 않고** 부르고 읽는다 — 'Files에 저장'([IncomingSaves])이 파일 작업 큐 안에서 쓴다.
 *
 * 제공자의 묻기·열기(바인더)와 스트림의 `read` 는 응답하지 않으면 영영 돌아오지 않고, 코루틴의 취소는 그 스레드에 닿지 않는다. 큐는 한 번에
 * 하나라 그대로 두면 **앱 전체의 파일 작업이 멎는다.** 그래서 막힐 수 있는 호출은 따로 된 스레드에서 돌고, 큐 쪽은 시간 상한과 취소를 들고
 * 기다리기만 한다. 기다림을 버리면 그 스레드는 돌아올 때까지 남지만(끊을 길이 없다) 큐는 다음 일로 간다. 늦게 돌아온 스트림은 닫는다.
 */
internal object BlockingReads {

    /**
     * 버린(기다림을 그만둔) 채 아직 돌아오지 않은 스레드의 수 — 프로세스 전체. [MAX_ABANDONED] 에 닿으면 새 호출을 시작하지 않는다([saturated]).
     * 막힌 제공자 하나가 항목마다 스레드를 하나씩 묶어 수천 개를 쌓지 않게.
     */
    private val abandoned = java.util.concurrent.atomic.AtomicInteger(0)

    /** 버린 스레드가 상한에 닿았는가. 닿았으면 부르는 쪽은 그 항목을 '못 읽음' 으로 넘긴다. */
    fun saturated(): Boolean = abandoned.get() >= MAX_ABANDONED

    internal fun abandonedCount(): Int = abandoned.get()

    /** 던진 것을 값 자리에 싣는 꼴. */
    private class Thrown(val error: Throwable)

    /**
     * [block] 을 따로 된 스레드에서 부르고 [timeoutMs] 까지 기다린다. 넘으면 [onTimeout] 을 부르고 null. 버린 스레드가 상한이면
     * 시작하지 않고 똑같이 한다. 부른 쪽이 취소되면 기다림을 버리고 취소가 그대로 올라간다. [block] 이 던진 것은 그대로 던진다.
     *
     * **값을 잃지 않는다** — 스레드와 부른 쪽이 상태 하나(`RUNNING` → `DONE`·`ABANDONED`)를 먼저 바꾸는 쪽이 이긴다. 스레드가 이기면 값은
     * 부른 쪽의 것이다(상한과 같은 순간에 끝났어도 돌려주고, 취소로 나가면 [Closeable] 을 닫는다). 부른 쪽이 이기면 늦게 온 값은 스레드가 닫는다.
     */
    suspend fun <T> call(timeoutMs: Long, onTimeout: () -> Unit = {}, block: () -> T): T? {
        if (saturated()) {
            onTimeout()
            return null
        }
        val state = java.util.concurrent.atomic.AtomicInteger(RUNNING)
        val value = java.util.concurrent.atomic.AtomicReference<Any?>(null)
        val done = CompletableDeferred<Unit>()
        Thread({
            val outcome: Any? = try {
                block()
            } catch (t: Throwable) {
                Thrown(t)
            }
            value.set(outcome)
            if (state.compareAndSet(RUNNING, DONE)) {
                done.complete(Unit)
            } else {
                abandoned.decrementAndGet()
                (outcome as? Closeable)?.let { runCatching { it.close() } }
            }
        }, THREAD_NAME).apply { isDaemon = true }.start()
        try {
            val finished = withTimeoutOrNull(timeoutMs) { done.await() } != null
            if (!finished && state.compareAndSet(RUNNING, ABANDONED)) {
                abandoned.incrementAndGet()
                onTimeout()
                return null
            }
            // 끝났다 — 시간 안이든, 상한과 같은 순간이든(그때 `withTimeoutOrNull` 은 null 을 줄 수 있다).
            val outcome = value.get()
            if (outcome is Thrown) throw outcome.error
            @Suppress("UNCHECKED_CAST")
            return outcome as T
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (state.compareAndSet(RUNNING, ABANDONED)) {
                abandoned.incrementAndGet()
            } else {
                (value.get() as? Closeable)?.let { runCatching { it.close() } }
            }
            throw e
        }
    }

    /**
     * [input] 을 따로 된 스레드가 조각으로 읽어 줄에 넣는다. 받는 쪽은 [next] 로 꺼내며 시간 상한과 취소를 쥔다. 끝나면 반드시 [close].
     * 줄은 [CAPACITY] 조각으로 묶여 있어 읽는 쪽이 쓰는 쪽보다 크게 앞서지 않는다.
     */
    class Pump(private val input: InputStream, private val chunkBytes: Int = CHUNK_BYTES) : Closeable {

        sealed interface Item {
            class Chunk(val bytes: ByteArray, val length: Int) : Item
            data object End : Item
            class Failed(val error: Throwable) : Item
        }

        private val queue = ArrayBlockingQueue<Item>(CAPACITY)

        @Volatile
        private var closed = false

        /** 읽는 스레드의 상태 — 닫을 때 아직 돌고 있으면 버린 스레드로 센다([call] 과 같은 셈). */
        private val state = java.util.concurrent.atomic.AtomicInteger(RUNNING)

        private val thread = Thread({ run() }, THREAD_NAME).apply { isDaemon = true }

        fun start(): Pump = apply { thread.start() }

        private fun run() {
            try {
                readAll()
            } finally {
                if (!state.compareAndSet(RUNNING, DONE)) abandoned.decrementAndGet()
            }
        }

        private fun readAll() {
            try {
                while (!closed) {
                    val buffer = ByteArray(chunkBytes)
                    val n = input.read(buffer)
                    if (n < 0) {
                        put(Item.End)
                        return
                    }
                    if (n > 0) put(Item.Chunk(buffer, n))
                }
            } catch (t: Throwable) {
                // 닫아서 깨운 것이면 받을 사람이 없다.
                if (!closed) put(Item.Failed(t))
            }
        }

        private fun put(item: Item) {
            try {
                while (!closed) if (queue.offer(item, PUT_WAIT_MS, TimeUnit.MILLISECONDS)) return
            } catch (e: InterruptedException) {
                // 닫혔다.
            }
        }

        /** 다음 조각. [timeoutMs] 동안 아무것도 오지 않으면 null(멎은 스트림). 취소되면 기다림을 끊는다. */
        suspend fun next(timeoutMs: Long): Item? =
            runInterruptible(Dispatchers.IO) { queue.poll(timeoutMs, TimeUnit.MILLISECONDS) }

        /**
         * 그만 읽는다. 스트림 닫기는 **다른 스레드에서** 한다 — 닫기 자체가 제공자에 막힐 수 있다. 닫으면 대개 멎은 `read` 가 풀린다.
         */
        override fun close() {
            if (closed) return
            closed = true
            if (thread.isAlive && state.compareAndSet(RUNNING, ABANDONED)) abandoned.incrementAndGet()
            thread.interrupt()
            Thread({ runCatching { input.close() } }, THREAD_NAME).apply { isDaemon = true }.start()
        }
    }

    /** 읽기 조각의 크기. */
    const val CHUNK_BYTES = 64 * 1024

    /** 줄에 들 조각 수. */
    private const val CAPACITY = 4

    private const val PUT_WAIT_MS = 200L

    private const val THREAD_NAME = "iroiro-receive"

    /** 버린 채 남아 있어도 되는 스레드 수(프로세스 전체). */
    const val MAX_ABANDONED = 16

    private const val RUNNING = 0
    private const val DONE = 1
    private const val ABANDONED = 2

    /** 읽다 던진 것이 입출력 실패인가(아니면 남의 코드의 다른 예외 — 둘 다 '못 읽음' 이다). */
    fun isIo(t: Throwable): Boolean = t is IOException
}
