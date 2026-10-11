package io.github.donggi.iroirobrowser.tether.core

/**
 * 같은 열쇠의 기록을 [intervalMs] 에 한 줄로 묶는다.
 *
 * 거절은 몰려서 온다 — 그룹의 손님이 tun2proxy 로 연결 수십 개를 한꺼번에 열 때 상한에 걸리거나, 폰이 붙은 망에서 누가 포트를 두드리면 연결
 * 하나마다 한 줄이 logcat 에 쏟아졌다(그 줄을 쓰는 것도 받기 스레드의 일이다). 이제 같은 까닭은 [intervalMs] 에 한 줄이고, 그 사이 묶인 수는
 * 다음 줄에 붙인다. 열쇠는 까닭의 이름이라 몇 개로 정해져 있다(표가 자라지 않는다).
 *
 * 마지막 묶음의 수는 다음 거절이 올 때까지 적히지 않는다 — 수 자체는 앱이 화면에 보인다(TetherState).
 */
internal class LogThrottle(private val nowMs: () -> Long, private val intervalMs: Long = DEFAULT_INTERVAL_MS) {

    private class Slot(val atMs: Long, var skipped: Int)

    private val slots = HashMap<String, Slot>()

    /** 지금 적을 차례면 그 사이 묶인 수와 함께 [write] 를 부른다. 아니면 세기만 한다. */
    fun note(key: String, write: (skipped: Int) -> Unit) {
        val now = nowMs()
        val skipped: Int
        synchronized(slots) {
            val slot = slots[key]
            if (slot != null && now - slot.atMs in 0 until intervalMs) {
                slot.skipped++
                return
            }
            skipped = slot?.skipped ?: 0
            slots[key] = Slot(now, 0)
        }
        write(skipped)
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 1_000L
    }
}
