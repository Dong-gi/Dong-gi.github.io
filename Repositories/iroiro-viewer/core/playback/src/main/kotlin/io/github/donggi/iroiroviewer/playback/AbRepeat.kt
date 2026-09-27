package io.github.donggi.iroiroviewer.playback

/**
 * A-B 구간 반복의 규칙. **순수 계산이라 JVM 시험으로 박힌다.**
 *
 * 6·9단계가 제스처를 화면 캡처로 판정하려다 여러 번 틀렸고, 10단계는 그래서 계산을
 * [GestureMath]·[FolderQueue] 처럼 밖으로 뺀다. 기기에서 확인하는 것은 **연결이 되었는가**
 * 뿐이다.
 *
 * ## 왜 클리핑이 아니라 되감기인가
 *
 * media3 에는 구간 재생용 `MediaItem.ClippingConfiguration` 이 있지만 이 앱에서는 쓸 수
 * 없다. 화면이 쥔 것은 `MediaController` 이고, 거기서 항목의 구성을 바꾸는 길은
 * `replaceMediaItem` 뿐인데 **로컬 파일의 `ProgressiveMediaSource.canUpdateMediaItem` 이
 * `uri`·`imageDurationMs`·`customCacheKey` 셋만 비교해 true 를 준다** — 소스를 다시 만들지
 * 않으므로 새로 넣은 클리핑이 **조용히 무시된다**(바이트코드로 확인했다). 큐를 통째로
 * 다시 세우면 걸리기는 하지만, 그 순간부터 `getCurrentPosition`·`getDuration` 이 **클립
 * 기준**으로 바뀌어 진행 바·시간 표시·[PositionSaver] 가 전부 다른 시간축을 보게 된다.
 *
 * 그래서 **B 를 지나면 A 로 되감는다.** 시간축이 하나로 남고, 껐다 켜는 데 드는 것도 없다.
 *
 * ## 되감는 사람은 티커다 — 그래서 눈금을 조인다
 *
 * `MediaController` 에는 `ExoPlayer.createMessage(...).setPosition(b)` 같은 예약이 없다
 * (그 함수는 `ExoPlayer` 인터페이스에만 있고 서비스 안에서만 닿는다). 남은 길은
 * [PlaybackConnection] 의 티커뿐인데, 기본 간격 500ms 로 두면 **구간 끝이 반 박자 넘쳐
 * 들린다.** 그렇다고 티커 전체를 50ms 로 올리면 `push()` 가 `State` 를 통째로 복사하며
 * 재구성을 열 배로 늘린다. [tickDelayMs] 가 **B 근처에서만** 간격을 좁히는 이유다.
 */
object AbRepeat {

    /**
     * 찍어 둔 구간. [bMs] 가 null 이면 **A 만 찍은 상태**(B 를 기다린다).
     *
     * 값은 언제나 밀리초이고 [aMs] < [bMs] 가 보장된다([mark] 만이 이것을 만든다).
     */
    data class Span(val aMs: Long, val bMs: Long?) {
        /** 구간이 완성되어 실제로 되감고 있는가. */
        val isLooping: Boolean get() = bMs != null
    }

    /** [mark] 가 한 일. */
    sealed interface Result {
        /** 구간이 바뀌었다. */
        data class Marked(val span: Span) : Result

        /** 구간을 지웠다(세 번째 누름). */
        data object Cleared : Result

        /**
         * **아무것도 바꾸지 않았다.** 구간이 [MIN_SPAN_MS] 보다 짧거나, 길이를 모르거나,
         * 파일이 너무 짧아 구간을 세울 수 없다.
         *
         * 여기서 'A 를 슬쩍 옮겨 준다' 를 하지 않는 것이 중요하다. 옮김의 크기가 정의상
         * 1초 미만이라 **진행 바에서도 시간 문구에서도 보이지 않고**, 사용자는 자기가
         * 찍은 자리가 왜 달라졌는지 알 길이 없다. 거절하고 말하는 편이 정직하다.
         */
        data object TooShort : Result
    }

    /**
     * 단추를 한 번 눌렀다. **세 번이면 한 바퀴다** — A 찍기 → B 찍기 → 해제.
     *
     * @param current 지금 찍혀 있는 것. 없으면 null.
     * @param positionMs 누른 순간의 재생 위치.
     * @param durationMs 이 항목의 길이. 0 이하면 아직 모른다.
     */
    fun mark(current: Span?, positionMs: Long, durationMs: Long): Result {
        // 길이를 모르면 구간을 세울 수 없다 — B 의 상한도 꼬리 보호도 계산할 수 없다.
        if (durationMs <= 0) return Result.TooShort
        // 파일 자체가 최소 구간 + 꼬리 여유보다 짧으면 어떻게 찍어도 구간이 서지 않는다.
        if (durationMs < MIN_SPAN_MS + TAIL_GUARD_MS) return Result.TooShort

        if (current == null) {
            // **A 에도 꼬리 여유를 건다.** A 를 끝에 찍어 두면 그 뒤에 B 를 찍을 자리가
            // 없어 다음 누름이 반드시 거절당한다. 그리고 A 를 자르지 않으면
            // `maxOf(a, ...)` 같은 보정에서 A 가 그대로 B 로 올라와 꼬리 보호가 통째로
            // 무력해진다 — 설계 검토가 잡은 결함이 정확히 그것이다.
            val a = positionMs.coerceIn(0, durationMs - TAIL_GUARD_MS - MIN_SPAN_MS)
            return Result.Marked(Span(a, null))
        }

        if (current.bMs == null) {
            val b = positionMs.coerceIn(0, durationMs - TAIL_GUARD_MS)
            if (b - current.aMs < MIN_SPAN_MS) return Result.TooShort
            return Result.Marked(current.copy(bMs = b))
        }

        return Result.Cleared
    }

    /** B 를 지났는가. 지났으면 A 로 되감을 차례다. */
    fun shouldLoop(span: Span, positionMs: Long): Boolean {
        val b = span.bMs ?: return false
        return positionMs >= b
    }

    /**
     * 사용자가 구간 **밖으로 건너뛰었는가.** 그랬다면 A-B 를 푼다.
     *
     * 판정을 '탐색 함수를 부른 자리' 가 아니라 **위치 자체**로 하는 것이 요점이다.
     * 알림·잠금화면·블루투스 헤드셋의 탐색은 우리 함수를 지나지 않고 세션으로 바로
     * 들어오므로, 호출부에 표시를 달아 두는 방식은 그 길을 통째로 놓친다. 그러면 구간
     * 밖으로 나간 사용자를 티커가 곧바로 A 로 끌어와 **앱이 말을 안 듣는 것처럼 보인다.**
     *
     * [TOLERANCE_MS] 는 되감기 자신의 넘침보다 넉넉해야 한다 — 그러지 않으면 우리가
     * 되감으려고 B 를 지난 그 순간을 '사용자가 건너뛰었다' 로 오해해 스스로 풀어 버린다.
     */
    fun escaped(span: Span, positionMs: Long): Boolean {
        if (positionMs < span.aMs - TOLERANCE_MS) return true
        val b = span.bMs ?: return false
        return positionMs > b + TOLERANCE_MS
    }

    /**
     * 다음 검사까지 기다릴 시간.
     *
     * 평소에는 [normalMs] 그대로이고, **B 까지 남은 시간이 [TIGHT_WINDOW_MS] 안쪽일 때만**
     * [TIGHT_TICK_MS] 로 좁힌다. 남은 시간은 **벽시계 기준**이라 배속으로 나눈다 —
     * 2배속이면 같은 거리를 절반의 시간에 지나가므로, 나누지 않으면 빨리 볼수록 더 많이
     * 넘친다.
     *
     * @param speed 재생 배속. 0 이하·비정상 값은 1.0 으로 본다.
     */
    fun tickDelayMs(span: Span?, positionMs: Long, speed: Float, normalMs: Long): Long {
        val b = span?.bMs ?: return normalMs
        val rate = if (speed.isFinite() && speed > 0f) speed else 1f
        val remainingWallMs = ((b - positionMs) / rate).toLong()
        if (remainingWallMs > TIGHT_WINDOW_MS) return normalMs
        return minOf(normalMs, TIGHT_TICK_MS)
    }

    /**
     * 구간의 끝을 **바닥에서 이만큼 띄운다.**
     *
     * B 가 길이에 딱 붙으면 되감기 전에 항목이 끝나 다음 곡으로 넘어간다. 300ms 면
     * 티커가 조인 간격(50ms)보다 넉넉히 크고, 귀로는 구간이 짧아진 것을 느끼지 못한다.
     */
    const val TAIL_GUARD_MS = 300L

    /** 이보다 짧은 구간은 만들지 않는다. 1초 미만은 되감기가 계속 걸려 소리가 끊긴다. */
    const val MIN_SPAN_MS = 1_000L

    /** 구간 밖으로 이만큼 벗어나면 사용자가 건너뛴 것으로 본다. */
    const val TOLERANCE_MS = 1_500L

    /** B 까지 남은 벽시계 시간이 이 안쪽이면 티커를 조인다. */
    const val TIGHT_WINDOW_MS = 1_500L

    /** 조였을 때의 간격. */
    const val TIGHT_TICK_MS = 50L
}
