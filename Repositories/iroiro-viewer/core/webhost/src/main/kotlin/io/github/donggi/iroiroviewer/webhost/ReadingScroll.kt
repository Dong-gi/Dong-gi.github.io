package io.github.donggi.iroiroviewer.webhost

import kotlin.math.floor

/**
 * 문서 안의 **읽던 자리**를 0~1 의 비율로 — WebView 의 스크롤 화소와 그 비율을 오간다.
 *
 * ## 왜 화소가 아니라 비율인가
 *
 * 화소는 글자 크기·여백·화면 폭이 바뀌면 다른 곳을 가리킨다. 비율은 '장의 얼마쯤' 이라 그 변화를 대강 견딘다 — 정확히
 * 같은 낱말로 돌아가지는 않지만(CFI 같은 글 위치가 아니다), 스크립트를 끈 WebView 에서 Kotlin 이 알 수 있는 것은
 * 스크롤 범위뿐이다(JavaScript 를 켜지 않는다 — `LockedWebView` 의 표).
 *
 * ## 세로쓰기는 가로로, 오른쪽에서 잰다
 *
 * `writing-mode: vertical-rl` 인 장은 **세로로 넘치지 않고 가로로만 넘친다** — 줄이 화면 높이에 맞춰 세로로 서고, 줄이
 * 늘어날수록 왼쪽으로 쌓인다. 그래서 세로로 넘치면 세로, 아니면 가로로 잰다(오른쪽에서 넘기는 책은 가로가 먼저다 — [axisOf]).
 * 가로이면서 책이 오른쪽에서 왼쪽으로 넘기면(`page-progression-direction="rtl"`) **장의 처음이 오른쪽 끝**이다 — 비율 0 이
 * 오른쪽 끝이다.
 *
 * 순수 함수라 JVM 시험이 답한다(`ReadingScrollTest`). 화면 캡처로 '반쯤 걸렸다' 를 판정하는 일은 이 저장소가 여러 번
 * 틀렸다.
 */
object ReadingScroll {

    /** 이 문서가 어느 쪽으로 스크롤되는가. */
    enum class Axis { VERTICAL, HORIZONTAL }

    /**
     * 스크롤할 수 있는 최대치(범위 − 보이는 폭) 둘로 축을 정한다. 둘 다 0 이면 null — 한 화면에 든다.
     *
     * **보통은 세로를 먼저 본다.** 가로 문서에 넓은 표 하나가 있어 가로로도 조금 넘치는 흔한 경우를 세로로 읽어야 한다.
     *
     * **오른쪽에서 넘기는 책([rightToLeft])이면 가로를 먼저 본다.** 세로쓰기 장도 세로로 **조금은** 넘칠 수 있다 — 화면보다
     * 긴 그림 하나(`max-width` 는 세로쓰기에서 가로를 누를 뿐이다), 줄 높이의 반올림. 세로를 먼저 보면 그 몇 화소 때문에 장
     * 전체를 세로로 재고, 첫 줄을 오른쪽 끝에 맞추는 일(`startOf`)도 세로 축의 0 으로 떨어져 아무 일도 하지 않는다(검토가
     * 잡았다). 가로쓰기 RTL 책(아랍어)은 가로로 넘치지 않으므로 여전히 세로다.
     */
    fun axisOf(maxX: Int, maxY: Int, rightToLeft: Boolean = false): Axis? = when {
        rightToLeft && maxX > 0 -> Axis.HORIZONTAL
        maxY > 0 -> Axis.VERTICAL
        maxX > 0 -> Axis.HORIZONTAL
        else -> null
    }

    /**
     * 화면의 **끝 가장자리**까지 본 몫(0~1) — 읽은 양이다. [fractionOf] 가 화면의 앞 가장자리(되살릴 자리)라면 이것은 문서
     * 전체의 진행(`doc_progress.progress`)이 쓴다.
     *
     * 둘을 가르는 까닭: 한 화면에 드는 장은 스크롤할 것이 없어 자리가 언제나 0 이다. 자리로 진행을 셈하면 마지막 장이 짧은
     * 책(판권·지은이 소개)은 끝까지 읽어도 `(장 수 − 1) / 장 수` 에 머물러 '다 읽음' 이 되지 않는다. 본 몫으로는 1 이다.
     *
     * @param extent 보이는 폭(화소). 범위는 `max + extent` 다.
     */
    fun seenOf(position: Int, max: Int, extent: Int, fromEnd: Boolean): Float {
        if (max <= 0) return 1f
        if (extent <= 0) return fractionOf(position, max, fromEnd)
        val p = position.coerceIn(0, max)
        val from = if (fromEnd) max - p else p
        return ((from + extent).toDouble() / (max.toLong() + extent)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 스크롤 자리 → 비율. [fromEnd] 면 끝(오른쪽)이 0 이다. 범위를 벗어난 값(넘쳐 끌기의 순간)은 잘라 준다.
     */
    fun fractionOf(position: Int, max: Int, fromEnd: Boolean): Float {
        if (max <= 0) return 0f
        val p = position.coerceIn(0, max).toFloat() / max
        return if (fromEnd) 1f - p else p
    }

    /** 비율 → 스크롤 자리. 반올림 방향이 뜻을 가지므로 `floor(x + 0.5)` 로 적는다(`round` 는 짝수 쪽이다). */
    fun positionOf(fraction: Float, max: Int, fromEnd: Boolean): Int {
        if (max <= 0) return 0
        val f = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        val p = floor(f.toDouble() * max + 0.5).toInt().coerceIn(0, max)
        return if (fromEnd) max - p else p
    }

    /**
     * 새 쪽을 열 때 옮겨 갈 자리.
     *
     * * 링크의 조각(`#note3`)이 있으면 null — WebView 가 스스로 그 자리로 간다. 우리가 옮기면 다툰다.
     * * 저장된 자리([saved])가 처음보다 뒤면 그것. 0 은 '처음' 이라 아래 규칙을 따른다.
     * * **오른쪽에서 왼쪽으로 넘기는 책이면 0(= 오른쪽 끝)을 청한다.** 11단계부터 세로쓰기 책의 첫 줄이 화면 오른쪽 끝에
     *   반쯤 걸렸던(E03·E04) 까닭으로 본 것이 이것이다 — WebView 의 가로 스크롤은 0(왼쪽 끝)에서 시작하는데 세로쓰기 장은
     *   오른쪽 끝에서 시작한다. 장이 화면보다 조금 넓으면 첫 줄이 오른쪽 밖으로 밀려 반쯤 잘리고, 아주 넓으면 장의 **끝**이
     *   먼저 보인다. 배치가 끝난 뒤 오른쪽 끝으로 옮긴다(`LockedWebView`).
     * * 그 밖에는 null — 처음(위 끝)이 WebView 의 기본값이다.
     */
    fun startOf(saved: Float?, hasFragment: Boolean, rightToLeft: Boolean): Float? = when {
        hasFragment -> null
        saved != null && !saved.isNaN() && saved > 0f -> saved.coerceAtMost(1f)
        rightToLeft -> 0f
        else -> null
    }

    /**
     * 옮기기 한 벌 — **배치가 끝날 때까지 되풀이한다.**
     *
     * 스크립트가 꺼져 있어 '배치가 끝났다' 를 알려 주는 것이 `onPageFinished` 하나뿐이고, 그것도 그림·글꼴이 늦게 붙으면
     * 뒤에서 범위가 또 바뀐다. 그래서 짧은 간격으로 다시 옮기되, 쪽이 다 읽힌 뒤 **범위가 연달아 [STABLE_TRIES] 번 그대로면**
     * 끝낸다. 끝없이 돌지 않게 [MAX_TRIES] 에서 멈춘다(큰 시트는 배치에 수십 초가 걸린다 — 12단계 실측. 그때는 대강의
     * 자리에서 멈춘다). 사용자가 화면을 만지면 화면 쪽이 이것을 버린다 — 손으로 옮긴 자리를 우리가 되돌리지 않게.
     *
     * **[awaitChange] — 다시 흐르기를 기다린다.** 글자 크기(`textZoom`)나 화면 크기(회전)가 바뀌면 쪽은 이미 다 읽혔으므로
     * `onPageFinished` 가 다시 오지 않고, 배치는 **그 뒤에** 따로 돈다. 그때 '범위가 두 번 그대로' 를 끝으로 보면, 큰 문서에서
     * 다시 흐르기가 240ms 안에 끝나지 않을 때 **옛 범위에서 멈추고** 뒤늦은 배치가 자리를 밀어낸다(검토가 잡았다 — 2,920문단
     * docx 가 그 크기다). 그래서 범위가 **한 번이라도 바뀐 뒤에만** 그대로인 것을 센다. 끝내 바뀌지 않으면(한 화면에 드는 쪽,
     * 크기가 고정된 시트) [MAX_TRIES] 에서 멈춘다 — 같은 자리로 되풀이해 옮길 뿐이라 해가 없다.
     */
    class Restore(val fraction: Float, private val awaitChange: Boolean = false) {
        private var tries = 0
        private var stable = 0
        private var lastMax = -1
        private var changed = false

        /**
         * 한 번 옮긴 뒤 부른다.
         *
         * @param max 지금의 스크롤 최대치(축의 것).
         * @param pageFinished 쪽이 다 읽혔는가(`onPageFinished` 가 왔다).
         * @return 한 번 더 옮겨야 하는가.
         */
        fun again(max: Int, pageFinished: Boolean): Boolean {
            tries++
            val same = max == lastMax
            if (!same && lastMax >= 0) changed = true
            lastMax = max
            if (pageFinished && (changed || !awaitChange)) {
                stable = if (same) stable + 1 else 0
            }
            if (tries >= MAX_TRIES) return false
            return !(pageFinished && stable >= STABLE_TRIES)
        }
    }

    /** 다시 옮기는 간격. */
    const val RETRY_MS = 120L

    /** 이만큼 되풀이하면 멈춘다(약 4초). */
    const val MAX_TRIES = 32

    /** 쪽이 다 읽힌 뒤 범위가 이만큼 연달아 그대로면 멈춘다. */
    const val STABLE_TRIES = 2
}
