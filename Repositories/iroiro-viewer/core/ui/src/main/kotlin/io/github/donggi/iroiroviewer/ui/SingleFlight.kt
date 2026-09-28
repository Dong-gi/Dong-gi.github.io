package io.github.donggi.iroiroviewer.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 같은 키의 일을 **하나만** 돌린다. 겹쳐 온 나머지는 그 결과를 기다린다 — [ThumbnailStore] 의 요청 병합이다.
 *
 * ## 일하던 쪽이 취소되면 기다리던 쪽이 이어받는다
 *
 * 예전 병합은 일하던 쪽이 취소되면 기다리던 쪽에 **null** 을 건넸다. 격자에서 그것은 이렇게 보인다 —
 * 칸 하나가 화면을 벗어나 요청이 취소되는데, 네이티브 디코딩은 끊기지 않아 수백 ms 더 돌고(그 사이
 * 디스크 캐시에는 이미 써진다), 그 틈에 같은 칸이 되돌아와 그 요청을 기다리면 **방금 만든 썸네일 대신
 * 빈 칸(종류 배지)** 을 받는다. 그 칸은 다시 화면을 벗어났다 돌아올 때까지 배지로 남는다. 취소는
 * 기다리던 쪽의 사정이 아니다 — 그래서 결과가 '버려졌다' 를 따로 싣고, 받은 쪽은 스스로 다시 한다.
 *
 * ## 표를 먼저 지우고 결과를 건넨다
 *
 * 차례가 거꾸로면 깨어난 쪽이 표에 남은 **이미 끝난** 약속을 다시 집어 '버려졌다' 를 되받으며 돈다.
 * 지우는 일은 [NonCancellable] 안에서 한다 — 취소된 코루틴이 잠금을 기다리다 그대로 빠져나가면 끝난
 * 약속이 표에 영영 남고, 그 키는 이 세션 내내 곧바로 null 을 받는다.
 *
 * 순수 코틀린이다(코루틴만 쓴다) — JVM 시험이 취소와 이어받기를 확인한다.
 */
internal class SingleFlight<V : Any> {

    /** 한 번의 결과. [abandoned] 면 값이 아니라 '일하던 쪽이 끝까지 가지 못했다' 다. */
    private class Done<V>(val value: V?, val abandoned: Boolean)

    private val lock = Mutex()
    private val running = HashMap<String, CompletableDeferred<Done<V>>>()

    /** 지금 이 키로 일하는 쪽이 있는가. 시험이 표가 비워지는지 본다. */
    suspend fun isRunning(key: String): Boolean = lock.withLock { key in running }

    /**
     * [key] 로 [work] 를 돌린다. 이미 누가 돌리고 있으면 그 결과를 받는다.
     *
     * [work] 가 던진 것(취소 포함)은 **부른 쪽에만** 간다. 기다리던 쪽은 그것을 받지 않고 다시 시도한다.
     */
    suspend fun run(key: String, work: suspend () -> V?): V? {
        while (true) {
            val (deferred, mine) = lock.withLock {
                val existing = running[key]
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<Done<V>>().also { running[key] = it } to true
                }
            }
            if (!mine) {
                // 기다리는 쪽의 취소는 여기서 그대로 던져진다(`await` 는 취소를 받는다).
                val done = deferred.await()
                if (done.abandoned) continue
                return done.value
            }
            val value = try {
                work()
            } catch (t: Throwable) {
                finish(key, deferred, Done(null, abandoned = true))
                throw t
            }
            finish(key, deferred, Done(value, abandoned = false))
            return value
        }
    }

    private suspend fun finish(key: String, deferred: CompletableDeferred<Done<V>>, done: Done<V>) {
        withContext(NonCancellable) {
            lock.withLock { if (running[key] === deferred) running.remove(key) }
        }
        deferred.complete(done)
    }
}
