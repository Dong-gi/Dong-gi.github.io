package io.github.donggi.iroiroviewer.playback

/**
 * 제스처가 값을 얼마나 움직이는가. **순수 계산이라 JVM 시험으로 박힌다.**
 *
 * 6·9단계가 제스처를 화면 캡처로 판정하려다 여러 번 틀렸다(CLAUDE.md 함정 표). 그래서
 * 계산은 여기로 빼서 시험이 답하게 하고, 화면에서 확인하는 것은 **연결이 되었는가** 뿐이다.
 */
object GestureMath {

    /** 화면의 어느 쪽에서 시작했는가. 세로로 끌 때 무엇을 바꿀지 정한다. */
    enum class Side { LEFT, RIGHT }

    /**
     * 좌우 어느 쪽인가.
     *
     * **가운데를 비워 두지 않는다.** '가운데는 아무것도 아님' 으로 두면 세로로 끌었는데
     * 아무 일도 안 일어나는 띠가 생기고, 그 띠의 존재를 아무도 모른다.
     */
    fun sideOf(x: Float, width: Float): Side =
        if (width > 0f && x < width / 2f) Side.LEFT else Side.RIGHT

    /**
     * 가로로 끌었는가 세로로 끌었는가. **한 번 정하면 그 제스처가 끝날 때까지 바꾸지 않는다.**
     *
     * 매 프레임 다시 정하면 비스듬히 끌 때 탐색과 소리가 번갈아 바뀐다.
     *
     * @return true 면 가로(탐색).
     */
    fun isHorizontal(dx: Float, dy: Float): Boolean = kotlin.math.abs(dx) >= kotlin.math.abs(dy)

    /**
     * 가로로 끈 거리를 탐색 시간으로.
     *
     * **화면 폭을 전부 끌면 [FULL_SWIPE_SEEK_MS]** 다. 영상 길이에 비례시키면 짧은
     * 영상에서는 손가락이 조금만 움직여도 끝으로 가고 긴 영상에서는 아무리 끌어도
     * 제자리라, 같은 동작이 파일마다 다른 뜻이 된다.
     */
    fun seekDeltaMs(dx: Float, width: Float): Long {
        if (width <= 0f) return 0
        return (dx / width * FULL_SWIPE_SEEK_MS).toLong()
    }

    /**
     * 세로로 끈 거리를 0~1 값의 변화량으로. 위로 끌면(음수 dy) 커진다.
     *
     * 화면 높이의 [FULL_SWIPE_FRACTION] 만큼 끌면 0 에서 1 까지 간다 — 화면 전체를
     * 써야 끝에서 끝까지 가면 한 손으로 조절할 수 없다.
     */
    fun levelDelta(dy: Float, height: Float): Float {
        if (height <= 0f) return 0f
        return -dy / (height * FULL_SWIPE_FRACTION)
    }

    /** 탐색 위치를 길이 안으로 자른다. 길이를 모르면(0 이하) 0. */
    fun clampPosition(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0) return 0
        return positionMs.coerceIn(0, durationMs)
    }

    /**
     * 몇 번째 두드림인가.
     *
     * 첫 두드림은 조작부를 여닫고, **두 번째부터** 30초씩 옮긴다. 창을 벗어나면 다시 1부터다.
     *
     * @param lastAtMs 직전 두드림 시각. 없으면 0.
     * @param nowMs 지금.
     * @param previousCount 직전까지 센 수.
     */
    fun tapCount(lastAtMs: Long, nowMs: Long, previousCount: Int): Int =
        if (lastAtMs > 0 && nowMs - lastAtMs <= MULTI_TAP_WINDOW_MS) previousCount + 1 else 1

    /**
     * 두드림 하나가 옮기는 시간. 1번째는 0(조작부 토글), 2번째부터 ±[TAP_SEEK_MS].
     *
     * @param count [tapCount] 가 센 수.
     * @param side 두드린 쪽. 오른쪽이 앞으로.
     */
    fun tapSeekMs(count: Int, side: Side): Long {
        if (count < 2) return 0
        return if (side == Side.RIGHT) TAP_SEEK_MS else -TAP_SEEK_MS
    }

    /** 화면 폭을 전부 끌었을 때 옮기는 시간. 90초. */
    const val FULL_SWIPE_SEEK_MS = 90_000L

    /** 밝기·소리를 끝에서 끝까지 옮기는 데 쓰는 화면 높이의 비율. */
    const val FULL_SWIPE_FRACTION = 0.7f

    /** 두드림 하나가 옮기는 시간. */
    const val TAP_SEEK_MS = 30_000L

    /**
     * 연속 두드림으로 세는 간격.
     *
     * **이 값이 조작부 토글을 미루는 시간이기도 하다.** 첫 두드림에 바로 토글해 버리면
     * 두 번 두드려 30초를 건너뛸 때마다 조작부가 한 번 깜빡인다 — 사용자가 지적한 것이
     * 그것이다. 그래서 첫 두드림의 토글을 이만큼 미뤄 두고, 그 안에 두 번째가 오면
     * **미뤄 둔 토글을 취소한다.**
     *
     * 그래서 너무 길면 안 된다. 토글이 그만큼 늦게 보이기 때문이다. 안드로이드의 더블탭
     * 기본값이 300ms 이고, 세 번·네 번을 이어 두드리는 것도 그 간격 안에 들어온다.
     */
    const val MULTI_TAP_WINDOW_MS = 320L

    /**
     * 두드려 건너뛴 뒤 안내를 띄워 두는 시간.
     *
     * 끌기는 손을 떼면 사라지지만 두드림에는 끝이 없어서, 지우는 사람이 없으면 안내가
     * 화면에 남는다. 이어 두드리는 동안에는 매번 다시 시작하므로 끊기지 않는다.
     */
    const val TAP_FEEDBACK_HOLD_MS = 900L
}
