package io.github.donggi.iroiroviewer.ui

import android.graphics.ImageDecoder
import java.io.IOException

/**
 * 썸네일을 **못 만든 기록.** 영구 실패와 일시 실패를 가른다 — [ThumbnailStore] 의 부정 캐시다.
 *
 * ## 왜 가르는가
 *
 * 예전 부정 캐시는 '파일이 있는데 못 만들었다' 를 전부 영구 실패로 적었다. 그런데 못 만든 까닭에는
 * **파일이 아니라 그 순간의 사정**인 것이 섞여 있다 — 메모리가 모자랐다(`OutOfMemoryError`),
 * 저장소가 잠깐 읽기를 거절했다(`IOException`), 아카이브 관문이 붐볐다. 그것을 영구로 적으면 사진
 * 여러 장을 빠르게 훑다 한 번 메모리가 빠듯했던 칸이 **앱을 껐다 켤 때까지** 종류 배지로 남는다.
 *
 * 영구 실패는 '파일이 있는데 **그 바이트로는** 못 읽는다' 뿐이다(SVG·TIFF·깨진 파일 — CLAUDE.md 함정 표).
 * 그것이 영구여도 되는 까닭은 키가 `sha256(경로 + 크기 + 수정시각)` 이기 때문이다 — 파일이 바뀌면
 * 키가 바뀌어 기록이 저절로 닿지 않는다. 그러니 '같은 키면 같은 바이트' 이고, 바이트가 정한 실패는
 * 다시 해도 같다.
 *
 * ## 세 갈래
 *
 * | [Cause] | 무엇 | 어떻게 |
 * |---|---|---|
 * | [Cause.PERMANENT] | 디코더가 '이 형식·이 데이터로는 안 된다' 고 말했다 | 세션 내내 다시 하지 않는다 |
 * | [Cause.TRANSIENT] | 메모리·입출력 — 파일이 아니라 그 순간의 사정 | 물러났다가 다시 한다. **영구로 바뀌지 않는다** |
 * | [Cause.UNCERTAIN] | 플랫폼이 둘을 가르지 않고 한 예외로 준다(동영상 프레임, 표지) | 물러났다가 다시 하되 [MAX_UNCERTAIN] 번 거듭 실패하면 영구로 본다 |
 *
 * 물러나는 시간은 [BASE_BACKOFF_MS] 에서 두 배씩 늘어 [MAX_BACKOFF_MS] 에서 멈춘다. 그래서 끝내 안
 * 되는 파일이 일시 실패로 잘못 분류되어도 비용은 **칸이 보이는 동안 10분에 한 번**으로 묶인다.
 *
 * 순수 코틀린이다(시각은 주입한다) — JVM 시험이 물러나기와 승격을 초 단위로 확인한다.
 */
class ThumbnailFailures(private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {

    enum class Cause { PERMANENT, TRANSIENT, UNCERTAIN }

    private class Entry {
        var permanent = false
        /** 일시·불확실 실패를 합친 횟수. 물러나는 시간을 정한다. */
        var attempts = 0
        /** 불확실 실패만 센 것. 영구로 승격을 정한다. */
        var uncertain = 0
        var retryAt = 0L
    }

    private val entries = HashMap<String, Entry>()

    @Synchronized
    fun isPermanent(key: String): Boolean = entries[key]?.permanent == true

    /**
     * 지금 만들어 봐도 되는가. 0 이면 된다. 양수면 그만큼(ms) 기다려야 한다. [PERMANENT_WAIT] 이면
     * 이 세션에서는 다시 하지 않는다.
     */
    @Synchronized
    fun waitMs(key: String): Long {
        val e = entries[key] ?: return 0L
        if (e.permanent) return PERMANENT_WAIT
        return (e.retryAt - clock()).coerceAtLeast(0L)
    }

    /** 실패 하나를 적는다. 영구가 됐으면 참. */
    @Synchronized
    fun record(key: String, cause: Cause): Boolean {
        val e = entries.getOrPut(key) { Entry() }
        when (cause) {
            Cause.PERMANENT -> e.permanent = true
            Cause.TRANSIENT, Cause.UNCERTAIN -> {
                e.attempts++
                if (cause == Cause.UNCERTAIN && ++e.uncertain >= MAX_UNCERTAIN) {
                    e.permanent = true
                } else {
                    e.retryAt = clock() + backoffMs(e.attempts)
                }
            }
        }
        return e.permanent
    }

    /** 만들었거나, 우리가 그 파일을 건드렸다(지웠다·돌렸다) — 기록을 버린다. */
    @Synchronized
    fun clear(key: String) {
        entries.remove(key)
    }

    companion object {
        /** [waitMs] 가 '다시 하지 않는다' 로 돌려주는 값. */
        const val PERMANENT_WAIT = Long.MAX_VALUE

        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 10 * 60_000L

        /** 불확실한 실패를 이만큼 거듭하면 파일 탓으로 본다. */
        const val MAX_UNCERTAIN = 3

        /** [attempts] 번째 실패 뒤에 기다리는 시간. 2초, 4초, 8초 … 10분에서 멈춘다. */
        fun backoffMs(attempts: Int): Long {
            val shift = (attempts - 1).coerceIn(0, 30)
            return (BASE_BACKOFF_MS shl shift).coerceIn(BASE_BACKOFF_MS, MAX_BACKOFF_MS)
        }

        /**
         * `ImageDecoder.DecodeException.error` 의 뜻.
         *
         * - `SOURCE_MALFORMED_DATA` — 디코더를 만들지 못했거나 데이터가 깨졌다. 지원하지 않는 형식(SVG·TIFF)도
         *   여기로 온다(플랫폼이 '디코더를 만들지 못했다' 를 이 값으로 준다). **바이트가 정한 실패다.**
         * - `SOURCE_INCOMPLETE` — 바이트가 도중에 끝났다. 받는 중인 파일이면 다 받은 뒤 크기가 바뀌어 **키가
         *   바뀐다** — 이 키의 바이트는 앞으로도 모자란다. 영구다.
         * - `SOURCE_EXCEPTION` — 디코더가 아니라 **읽기**가 던졌다(저장소 입출력). 일시다.
         * - 그 밖(앞으로 생길 값) — 모른다.
         */
        fun causeOfDecodeError(error: Int): Cause = when (error) {
            ImageDecoder.DecodeException.SOURCE_MALFORMED_DATA,
            ImageDecoder.DecodeException.SOURCE_INCOMPLETE,
            -> Cause.PERMANENT
            ImageDecoder.DecodeException.SOURCE_EXCEPTION -> Cause.TRANSIENT
            else -> Cause.UNCERTAIN
        }

        /**
         * 디코더가 아닌 곳에서 난 실패의 뜻. `ImageDecoder.DecodeException` 은 `IOException` 이므로
         * 부르는 쪽이 **그것을 먼저** 걸러 [causeOfDecodeError] 로 보낸다.
         *
         * 메모리 부족과 입출력은 파일이 아니라 그 순간의 사정이다. 그 밖의 런타임 예외는 플랫폼이
         * 무엇을 말하려는지 모른다 — 메시지로 가르지 않는다(판마다 바뀌고, 분기의 근거가 되는 순간
         * 그 문자열이 화면 쪽으로 새는 길이 열린다).
         */
        fun causeOf(t: Throwable): Cause = when (t) {
            is OutOfMemoryError -> Cause.TRANSIENT
            is IOException -> Cause.TRANSIENT
            else -> Cause.UNCERTAIN
        }
    }
}
