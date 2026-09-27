package io.github.donggi.iroiroviewer.playback

/**
 * 화면 속 화면(PiP)의 **계산만** 맡는다. 순수 함수라 JVM 시험으로 박힌다.
 *
 * ## 왜 `android.util.Rational` 을 돌려주지 않는가
 *
 * 이 모듈의 JVM 시험은 android.jar 스텁 위에서 돈다 — `Rational` 을 만드는 순간
 * `RuntimeException: Stub!` 이다. 그래서 [Aspect] 로 돌려주고 플랫폼 타입으로 바꾸는 일은
 * 액티비티가 한 줄로 한다. [TrackLabels] 가 `Format` 을 받지 않는 것과 같은 이유다.
 *
 * ## 비율을 여기서 자르는 것이 요점이다
 *
 * 시스템이 받는 비율은 `[0.41841, 2.39]` 양끝 포함이고(플랫폼 리소스
 * `config_pictureInPictureMin/MaxAspectRatio`, 에뮬레이터의 framework-res 에서 직접 읽었다),
 * 벗어나면 `IllegalArgumentException: Aspect ratio is too extreme` 이 난다. 그 예외는
 * **PiP 로 들어갈 때뿐 아니라 파라미터를 갱신하기만 해도** 나온다(`services.jar` 의
 * `ensureValidPictureInPictureActivityParams` 가 두 경로 모두에서 불린다). 그러니까
 * 시네마스코프(2.76:1) 영상을 **열어 두기만 해도** 재생 화면이 통째로 죽는다는 뜻이다.
 *
 * 그래서 자르는 자리는 '들어가기 직전' 이 아니라 **비율을 만드는 이 한 곳**이어야 한다.
 * 한계값 자체는 디스플레이별 리소스라 기기마다 다를 수 있으므로 2.39 를 그대로 박지 않고
 * 안쪽으로 여유를 둔다.
 */
object PipMath {

    /** 분자·분모로 표현한 화면 비율. 액티비티가 `android.util.Rational` 로 바꾼다. */
    data class Aspect(val numerator: Int, val denominator: Int)

    /**
     * PiP 창에 붙일 비율. **모르면 null 이고, 그때는 `setAspectRatio` 를 부르지 않는다.**
     *
     * 부르지 않으면 검사도 없고 시스템이 기본값(16:9, `config_pictureInPictureDefault
     * AspectRatio=1.77778`)으로 창을 만든다. 0 이나 NaN 으로 `Rational` 을 만들면 무한·NaN 이
     * 되고 NaN 은 `Float.compare` 규칙상 한계 밖으로 판정되어 위의 그 예외가 난다.
     *
     * @param videoAspect `PlaybackConnection.State.videoAspect`. 이미
     *   `pixelWidthHeightRatio` 가 곱해진 '보여야 할' 비율이다.
     */
    fun aspectOf(videoAspect: Float): Aspect? {
        if (!videoAspect.isFinite() || videoAspect <= 0f) return null
        val clamped = videoAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        // **1/1000 로 양자화한다.** 파라미터 갱신은 바인더 호출이라, 비율이 소수점 여섯째
        // 자리에서 흔들릴 때마다 부르면 레이아웃마다 IPC 가 난다. 1/1000 이면 눈으로는
        // 구분되지 않으면서 흔들림을 흡수한다.
        //
        // **`kotlin.math.round` 를 쓰지 않는다.** 그것은 `Math.rint` 라 **반올림이 짝수
        // 쪽으로** 간다 — 9:16(0.5625)이 562.5 에서 563 이 아니라 562 가 된다. 눈에 띄는
        // 차이는 아니지만 시험이 읽는 사람의 상식과 어긋나고, 그 어긋남은 다음 사람이
        // 시험을 고치게 만든다. 여기서는 언제나 위로 올린다.
        val n = kotlin.math.floor(clamped * 1000f + 0.5f).toInt().coerceIn(
            kotlin.math.ceil(MIN_ASPECT * 1000f).toInt(),
            kotlin.math.floor(MAX_ASPECT * 1000f).toInt(),
        )
        return Aspect(n, 1000)
    }

    /** PiP 창에 놓을 조작. 시스템이 그리고 우리 리시버가 받는다. */
    enum class Command { PREVIOUS, PLAY, PAUSE, NEXT }

    /**
     * PiP 창에 무엇을 몇 개 놓을 것인가.
     *
     * 자리는 대개 셋이다(`config_pictureInPictureMaxNumberOfActions = 3`, 플랫폼 주석이
     * '3 미만이면 안 된다' 고 적는다). **넘겨도 예외가 아니라 조용히 잘리는데**, 자르는
     * 차례를 우리가 고를 수 없으므로 모자라면 **우리 쪽에서** 가장 중요한 하나만 남긴다.
     *
     * 재생/일시정지를 '토글' 하나로 두지 않는 것이 중요하다. 시스템이 단추를 그린 뒤
     * 사용자가 누를 때까지 시간이 흐르고, 그 사이 큐가 끝나 멎으면 토글은 **정반대로**
     * 움직인다. media3 의 알림도 같은 이유로 명시 명령을 쓴다.
     */
    fun commands(hasQueue: Boolean, isPlaying: Boolean, maxActions: Int): List<Command> {
        val middle = if (isPlaying) Command.PAUSE else Command.PLAY
        if (!hasQueue || maxActions < 3) return listOf(middle)
        return listOf(Command.PREVIOUS, middle, Command.NEXT)
    }

    /**
     * 단추를 눌러 들어갈 수 있는가.
     *
     * **재생목록이 펼쳐져 있으면 들어가지 않는다.** 지금 컴포지션은
     * `hasVideo && !playlistOpen` 일 때만 `SurfaceView` 를 만들고 사라질 때 표면을 뗀다 —
     * 목록을 펼친 채 들어가면 **표면이 없는 창**이 PiP 로 뜬다. 목록을 우리가 대신 닫으면
     * '한 번 손대면 그 뜻이 이긴다'(`userChoice`)를 덮어쓰게 되므로, 아예 들어가지 않는
     * 편이 두 규칙을 모두 지킨다. 단추도 그때는 보이지 않는다.
     */
    fun canEnter(hasVideo: Boolean, playlistOpen: Boolean, supported: Boolean): Boolean =
        supported && hasVideo && !playlistOpen

    /**
     * 홈으로 나갈 때 **저절로** 들어갈 것인가(`setAutoEnterEnabled`).
     *
     * [canEnter] 에 `isPlaying` 이 더 붙는다. 자동 진입은 '이 사람은 계속 보려고 나간다'
     * 는 **추측**이고, 멈춰 둔 영상은 계속 볼 뜻이 아니기 때문이다. 단추는 추측이 아니라
     * 명시라 멈춰 있어도 들어간다.
     *
     * 이 값을 한 번 켜 두고 잊으면 안 된다 — 진입 판정이 `Task.startPausingLocked` 안에
     * 있어서 **태스크가 바뀌며 우리가 멈추는 모든 길**(홈 버튼·제스처 홈·최근앱·다른 앱
     * 실행)이 그 한 곳을 지난다. 조건이 바뀔 때마다 `setPictureInPictureParams` 로
     * 갱신해야 소리 파일을 듣다 홈으로 나갔을 때 검은 창이 뜨지 않는다.
     */
    fun autoEnter(
        hasVideo: Boolean,
        isPlaying: Boolean,
        playlistOpen: Boolean,
        supported: Boolean,
    ): Boolean = canEnter(hasVideo, playlistOpen, supported) && isPlaying

    /**
     * 시스템 한계(`0.41841`)보다 **안쪽**이다. 경계를 정확히 노리면
     * `Rational.floatValue()` 의 `(float)num/(float)den` 반올림에 걸린다.
     */
    const val MIN_ASPECT = 0.42f

    /** 시스템 한계(`2.39`)보다 안쪽. 이유는 [MIN_ASPECT] 와 같다. */
    const val MAX_ASPECT = 2.38f
}
