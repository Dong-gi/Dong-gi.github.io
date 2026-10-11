package io.github.donggi.iroirobrowser.tether.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * '상태가 바뀌었다' 알림을 합친다 — [windowMs] 안에 몇 번이 오든 듣개는 한 번.
 *
 * 앱의 테더 상태는 아무 스레드에서나 '바뀜' 을 알리고 듣개(화면·타일)는 메인 스레드에서 돈다. 예전에는 알림마다 메인 스레드에 일을 하나씩
 * 올렸다 — 거절이 몰리면(받기 스레드가 연결마다 알린다) 메인 스레드가 그 일로 밀렸다. 이제 첫 알림은 곧바로(앞의 것에서 [windowMs] 가
 * 지났으면) 보내고, 그 뒤 창 안에 온 것들은 창이 끝날 때 **한 번에** 보낸다. 듣개는 보낼 때의 상태를 읽으므로 합쳐도 마지막 상태를 놓치지
 * 않는다 — 보내기 직전에 '기다리는 중' 을 내리므로 듣개가 도는 사이에 온 알림은 다음 차례로 간다.
 *
 * @param nowMs 단조 시계(밀리초).
 * @param schedule [delayMs] 뒤에 일을 돌린다(앱은 메인 스레드의 Handler — 시험은 손으로 돌린다).
 * @param dispatch 듣개를 부른다(schedule 이 돌리는 스레드에서).
 */
class ChangeCoalescer(
    private val windowMs: Long,
    private val nowMs: () -> Long,
    private val schedule: (delayMs: Long, task: () -> Unit) -> Unit,
    private val dispatch: () -> Unit,
) {
    private val pending = AtomicBoolean(false)

    /** 마지막으로 보낸 때. 아직 보낸 적이 없으면 null. */
    @Volatile
    private var lastDispatchMs: Long? = null

    /** 아무 스레드에서나. */
    fun changed() {
        if (!pending.compareAndSet(false, true)) return
        val last = lastDispatchMs
        val wait = if (last == null) 0 else (last + windowMs - nowMs()).coerceIn(0, windowMs)
        schedule(wait) {
            lastDispatchMs = nowMs()
            pending.set(false)
            dispatch()
        }
    }
}
