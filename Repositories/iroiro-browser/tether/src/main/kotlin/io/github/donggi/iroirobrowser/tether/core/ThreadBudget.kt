package io.github.donggi.iroirobrowser.tether.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * 테더 서버 셋(SOCKS5·HTTP 프록시·파일 서버)이 **함께 쓰는 스레드 예산**.
 *
 * 서버들은 연결마다 스레드를 쓴다 — 프록시의 TCP 중계는 2(일꾼 + 반대 방향), SOCKS5 의 UDP 묶음은 3(일꾼 + 두 방향 — UDP 버퍼 64 KiB
 * 둘도 함께), 파일 서버는 1. 서버마다의 동시 연결 상한만 있던 usb-tether 에서는 그룹의 손님이 셋을 함께 채워 **1,300 개 남짓**까지 늘릴 수
 * 있었다(SOCKS5 256 × UDP 3 + HTTP 256 × 2 + 파일 32). 이제 연결을 받기 전에 그 연결이 쥘 스레드 수만큼
 * [tryAcquire] 하고, 끝나면 [release] 한다 — 모자라면 그 연결은 받지 않는다(닫거나 SOCKS5 의 실패 답). 서버마다의 상한은 usb-tether 그대로
 * 두었다(이 PC 처럼 HTTP 프록시로 PC 의 앱들이 통째로 지나는 쓰임을 줄이지 않으려고) — 셋을 함께 묶는 것은 이 예산 하나다.
 * 예산 [DEFAULT_LIMIT] 는 SOCKS5 혼자 상한까지 차도(TCP 128 × 2 + UDP 128 × 3 → 640) 들어가고 그때도 HTTP 터널 64 개가 남는 크기다.
 * 어림: 스레드 하나의 스택 예약이 1 MiB 안팎이라 768 개면 가상 주소 768 MiB 남짓이다(64비트 폰에서 문제가 아니고, 실제로 쓰는 메모리는 그보다
 * 훨씬 적다 — 기기에서 재지 않았다).
 *
 * 테더는 브라우저와 따로 된 프로세스(`:tether`)라, 이것을 넘어 메모리가 모자라도 탭·내려받기는 함께 죽지 않는다.
 */
class ThreadBudget(val limit: Int = DEFAULT_LIMIT) {

    private val used = AtomicInteger()

    /** 지금 쥔 스레드 수. */
    val inUse: Int get() = used.get()

    /** [threads] 만큼 쥔다. 예산을 넘으면 쥐지 않고 false. */
    fun tryAcquire(threads: Int): Boolean {
        while (true) {
            val current = used.get()
            if (current + threads > limit) return false
            if (used.compareAndSet(current, current + threads)) return true
        }
    }

    fun release(threads: Int) {
        used.addAndGet(-threads)
    }

    companion object {
        const val DEFAULT_LIMIT = 768
    }
}
