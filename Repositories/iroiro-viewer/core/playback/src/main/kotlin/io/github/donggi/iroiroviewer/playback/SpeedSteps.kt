package io.github.donggi.iroiroviewer.playback

/**
 * 재생 속도의 눈금. **순수 계산이라 JVM 시험으로 박힌다.**
 *
 * ## 왜 연속 슬라이더가 아닌가
 *
 * 배속은 **되돌아올 수 있어야 하는 값**이다. 슬라이더로 두면 1.0 배로 정확히 돌아오는
 * 일이 손가락에 달리고, 1.03 배 같은 자리에 걸린 사람은 무엇이 이상한지 모른 채 소리가
 * 미묘하게 틀어진 것만 듣는다. 눈금으로 두면 '한 칸 위' 와 '원래대로' 가 언제나 정확하다.
 * 재생기들(VLC·MX·YouTube)이 모두 눈금을 쓰는 이유도 같다.
 *
 * ## 왜 이 여섯인가
 *
 * 0.5 · 0.75 · 1.0 · 1.25 · 1.5 · 2.0. 아래로는 받아쓰기·따라 부르기, 위로는 강의·해설을
 * 빨리 넘기는 쓰임이다. 0.25 배는 소리가 뭉개져 알아들을 수 없고, 3배 이상은 말소리가
 * 사라져 영상만 넘어가는 것과 다르지 않아 둘 다 뺐다.
 *
 * ## 0 이하를 절대 내보내지 않는다
 *
 * `PlaybackParameters(float)` 의 생성자가 `speed > 0` 을 `checkArgument` 로 검사해서,
 * 0 이 한 번이라도 새면 **메인 스레드에서 `IllegalArgumentException` 으로 앱이 죽는다**
 * (바이트코드로 확인했다). 그래서 후보를 상수 목록으로 박고 [nearest] 로만 들어오게 한다.
 */
object SpeedSteps {

    /** 고를 수 있는 배속. 오름차순이고 [NORMAL] 을 반드시 포함한다. */
    val STEPS: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    /** 보통 속도. '되돌아올 자리' 다. */
    const val NORMAL = 1.0f

    /**
     * 플레이어가 말하는 속도를 **우리 눈금 가운데 가장 가까운 것**으로 바꾼다.
     *
     * 플레이어 쪽 값은 부동소수라 우리가 넣은 1.25f 가 그대로 돌아온다는 보장이 없고,
     * 다른 컨트롤러(잠금화면·블루투스)가 임의의 값을 넣었을 수도 있다. 화면의 눈금을
     * 고르는 일은 언제나 이 함수를 거친다 — 그래야 '켜진 칸' 이 반드시 하나다.
     */
    fun nearest(speed: Float): Float {
        if (!speed.isFinite() || speed <= 0f) return NORMAL
        return STEPS.minByOrNull { kotlin.math.abs(it - speed) } ?: NORMAL
    }

    /** 지금 배속이 보통인가. 화면이 '배속 표시를 계속 띄울지' 를 이것으로 가른다. */
    fun isNormal(speed: Float): Boolean = nearest(speed) == NORMAL

    /**
     * `1.5×`. **문구가 아니라 수 표기라 여기서 만든다.**
     *
     * 숫자와 곱셈 기호뿐이라 번역할 것이 없다. 사람에게 읽어 줄 말
     * ('재생 속도 1.5배')은 [speedLabel] 이 문자열 자원에서 만든다 — 그쪽이 문구다.
     *
     * 소수점 아래 0 은 떼지 않는다(`1.0×`). 떼면 `1×` 와 `1.25×` 의 폭이 달라져
     * 단추가 눌릴 때마다 옆 단추가 밀린다.
     */
    fun text(speed: Float): String {
        val s = nearest(speed)
        val whole = s.toInt()
        val frac = kotlin.math.round((s - whole) * 100).toInt()
        return if (frac % 10 == 0) "$whole.${frac / 10}×" else "$whole.${frac.toString().padStart(2, '0')}×"
    }
}
