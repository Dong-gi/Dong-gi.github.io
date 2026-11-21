package io.github.donggi.iroiroviewer.playback

import kotlin.math.floor

/**
 * 프레임 넘기기의 셈 — 프레임 길이와 다음에 탐색할 자리. `android.*` 를 쓰지 않아 JVM 시험이 답한다(`FrameStepTest`).
 *
 * media3 의 `MediaController` 에는 프레임 단위로 움직이는 명령이 없다. 그래서 **정확 탐색**(플레이어의 `SeekParameters` 가 기본값
 * `EXACT` 그대로다 — 앱 어디서도 바꾸지 않는다)으로 프레임 사이의 자리를 고른다. 정확 탐색은 **표시 시각이 탐색 자리 이상인 첫
 * 프레임**을 보인다. 그래서 프레임 j 를 보이려면 j−1 과 j 사이의 자리로 가면 되는데, 그 끝에 붙이면 컨테이너의 시각 반올림(밀리초·
 * 90 kHz)이 한 프레임을 건너뛰게 한다. **j 앞 4분의 1 프레임**으로 간다 — 아래로 ¼, 위로 ¾ 의 여유가 남는다.
 *
 * 프레임 시각을 `k × 길이` 로 본다(일정한 프레임율, 첫 프레임 0초). 가변 프레임율 영상은 한 프레임을 건너뛰거나 되풀이할 수 있다.
 * 멈춘 자리가 탐색으로 온 곳이 아니면(재생하다 멈췄다) 지금 보이는 프레임을 반올림으로 짐작한다 — 첫 걸음이 한 프레임 어긋날 수
 * 있고, 그다음부터는 맞는다(위치가 우리가 고른 자리이기 때문이다).
 */
object FrameStep {

    /** 프레임율을 모를 때의 길이 — 1/30초. */
    const val DEFAULT_FRAME_US = 1_000_000.0 / 30

    /**
     * 프레임율이 이보다 크면 이 값으로 본다. 밀리초로 탐색하므로 ¼ 프레임이 1 ms 를 넘어야 반올림을 견딘다(240 fps 에서 1.04 ms).
     * 슬로 모션 녹화(240 fps 로 찍어 30 fps 로 담은 것)는 담긴 프레임율로 온다.
     */
    const val MAX_FPS = 240f

    /** 프레임 한 장의 길이(마이크로초). [fps] 가 없거나(media3 의 `NO_VALUE` = −1) 1 fps 아래면 1/30초. */
    fun frameUs(fps: Float): Double =
        if (fps.isFinite() && fps >= 1f) 1_000_000.0 / fps.coerceAtMost(MAX_FPS) else DEFAULT_FRAME_US

    /**
     * 한 프레임 앞([forward]) 또는 뒤로 갈 때 탐색할 자리(ms). 갈 곳이 없으면 null — 첫 프레임에서 뒤로, 끝을 넘어 앞으로.
     *
     * @param durationMs 길이. 모르면(0 이하) 앞으로의 끝을 보지 않는다.
     */
    fun target(positionMs: Long, frameUs: Double, durationMs: Long, forward: Boolean): Long? {
        val shown = floor(positionMs.coerceAtLeast(0) * 1000.0 / frameUs + 0.5).toLong()
        val next = if (forward) shown + 1 else shown - 1
        if (next < 0) return null
        // 다음 프레임의 표시 시각이 길이에 닿으면 그 프레임은 없다 — 탐색 자리(¼ 앞)로 재면 마지막 프레임에서 헛걸음한다.
        if (forward && durationMs > 0 && next * frameUs >= durationMs * 1000.0) return null
        return floor((next - 0.25) * frameUs / 1000.0).toLong().coerceAtLeast(0)
    }
}
