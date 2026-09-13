package io.github.donggi.iroiroviewer.safety

/**
 * 이미지 뷰어가 쓸 수 있는 메모리의 상한. **순수 함수다.**
 *
 * ## 왜 [ParseLimits] 와 따로 두는가
 *
 * [ParseLimits] 는 *신뢰할 수 없는 입력*으로부터 우리를 지키는 상한이다(압축 폭탄, 무한
 * 중첩). 이쪽은 성격이 다르다 — 입력이 악의적이지 않아도, 사용자의 108MP 사진 한 장이
 * 힙 상한보다 크다. 막아야 할 것이 공격이 아니라 **산수**다.
 *
 * ## 왜 순수 함수인가
 *
 * 이 값들이 틀리면 앱이 `OutOfMemoryError` 로 죽는데, 그것을 에뮬레이터에서 재현해 고치는
 * 일은 느리고 불확실하다. 화면 크기와 힙 등급만 주면 답이 나오는 함수로 떼어 두면 JVM
 * 테스트가 초 단위로 돈다.
 *
 * **다만 시험이 단언할 수 있는 것은 공식의 출력뿐이다.** 실제 실행이 이 상한 안에 머무는지는
 * 런타임 계측(`BitmapBudget`)과 진단 화면으로만 확인된다 — 하드웨어 비트맵은 네이티브 힙
 * 통계에 잡히지도 않는다.
 */
object ImageLimits {

    /** 비트맵 한 화소의 바이트. `ARGB_8888` 기준. */
    const val BYTES_PER_PIXEL = 4

    /**
     * 비트맵 한 장을 그릴 수 있는 절대 상한.
     *
     * API 31 의 `Canvas` 가 100 MiB 를 넘는 비트맵을 그리기를 거부한다(API 35 는 150 MiB).
     * **minSdk 가 하한이므로 낮은 쪽을 기준으로 삼는다** — 높은 쪽에 맞추면 Android 12
     * 기기에서만 죽는 코드가 된다.
     */
    const val MAX_BITMAP_BYTES = 100L * 1024 * 1024

    /**
     * 살아 있는 비트맵 전체의 상한을 힙 등급의 몇 분의 몇으로 잡는가.
     *
     * 힙 전부를 쓸 수는 없다 — Compose·Room·ExoPlayer 가 같은 힙을 쓴다. 1/4 은
     * 안드로이드 이미지 라이브러리들이 오래 쓴 값이고, 우리는 디코딩 임시본이 그 밖에서
     * 한 장 더 생기므로 여유가 필요하다.
     */
    private const val HEAP_FRACTION = 4

    /**
     * 뷰어가 한 번에 살려 두는 페이지 수.
     *
     * `beyondViewportPageCount = 1` 이면 앞뒤 한 장씩이고, 넘기는 도중에는 두 장이 동시에
     * 화면에 걸친다. 그래서 최악은 `현재-1 … 현재+2` 의 넷이다. 이 수를 줄이면 되돌아
     * 스와이프할 때마다 다시 디코딩해야 해서 **체감이 그만큼 나빠진다.**
     */
    const val LIVE_PAGES = 4

    data class Budget(
        /** 바닥층(화면 맞춤) 한 장의 바이트. */
        val baseBytes: Long,
        /** 살아 있는 바닥층 전체의 바이트. **예산을 넘지 않는다.** */
        val liveCap: Long,
        /** 선명화층(확대했을 때 다시 뜨는 한 장)의 상한. */
        val detailCap: Long,
        /** 비트맵 한 장의 절대 상한. */
        val maxBitmapBytes: Long,
        /**
         * 동시에 살려 둘 수 있는 바닥층 장수.
         *
         * **상수가 아니라 예산에서 끌어낸다.** [LIVE_PAGES] 를 그대로 쓰면 힙이 작은
         * 기기에서 예산을 넘긴다 — 화면이 1080×2400 이면 한 장이 10.4 MB 이고, 힙 등급이
         * 64 MB 인 기기의 몫(16 MB)으로는 **두 장도 못 든다.**
         */
        val livePages: Int,
    )

    /**
     * @param screenWidth·[screenHeight] 화면 화소.
     * @param memoryClassMb `ActivityManager.getMemoryClass()`. 앱이 쓸 수 있는 힙(MB).
     */
    fun budgetOf(screenWidth: Int, screenHeight: Int, memoryClassMb: Int): Budget {
        val base = (screenWidth.toLong() * screenHeight * BYTES_PER_PIXEL).coerceAtLeast(1L)
        val heap = memoryClassMb.toLong() * 1024 * 1024
        val allowance = heap / HEAP_FRACTION

        // **장수를 예산에서 끌어낸다.** 예전에는 `base * LIVE_PAGES` 를 그대로 `liveCap`
        // 으로 썼는데, 그 값은 '필요한 양' 이지 '써도 되는 양' 이 아니다. 힙이 작은 기기에서
        // 예산을 넘긴 채로 상한이라고 부르게 되고, 그것을 믿은 호출자가 죽는다.
        // (9단계가 이 함수의 첫 호출자다 — 그 전까지는 선언만 되어 있었다.)
        val pages = (allowance / base).toInt().coerceIn(MIN_LIVE_PAGES, LIVE_PAGES)
        val live = (base * pages).coerceAtMost(allowance.coerceAtLeast(base))

        // 남는 자리를 선명화층에 준다. 힙이 작은 기기에서는 0 이 될 수 있고, 그때는
        // 선명화를 아예 켜지 않는다 — 흐린 것이 죽는 것보다 낫다.
        val detail = (allowance - live).coerceAtLeast(0L).coerceAtMost(base * 3)

        return Budget(
            baseBytes = base,
            liveCap = live,
            detailCap = detail,
            maxBitmapBytes = MAX_BITMAP_BYTES,
            livePages = pages,
        )
    }

    /**
     * 힙이 아무리 작아도 이만큼은 든다.
     *
     * 두 장 미만이면 넘기는 순간 앞 장을 버리고 다음 장을 뜨느라 **넘길 때마다 빈 화면**이
     * 보인다. 그것은 '아껴서 안 죽는' 것이 아니라 '못 쓰는' 것이다. 두 장에서도 예산을
     * 넘긴다면 그 기기에서는 한 장이 화면 크기와 맞지 않는다는 뜻이고, 그때는
     * [sampleFor] 가 더 줄여 준다.
     */
    const val MIN_LIVE_PAGES = 2

    /**
     * 이 크기의 원본을 [targetLongest] 화소 안쪽으로 줄이려면 얼마나 건너뛰어야 하는가.
     *
     * **2의 거듭제곱만 돌려준다.** `ImageDecoder.setTargetSampleSize` 는 다른 값도 받지만
     * 내부에서 어차피 거듭제곱으로 내림하고, 그 사이 값을 믿으면 우리가 센 바이트와
     * 실제가 어긋난다.
     *
     * 결과가 [MAX_BITMAP_BYTES] 를 넘지 않을 때까지 한 번 더 줄인다 — 108MP 사진은
     * 화면에 맞춰도 그릴 수 없는 크기가 될 수 있다.
     */
    fun sampleFor(width: Int, height: Int, targetLongest: Int): Int {
        if (width <= 0 || height <= 0 || targetLongest <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= targetLongest) sample *= 2
        while (bytesAt(width, height, sample) > MAX_BITMAP_BYTES) sample *= 2
        return sample
    }

    /**
     * **예산 안에 드는** 표본. 목표 해상도보다 흐려지더라도 바이트를 먼저 지킨다.
     *
     * ## 왜 [sampleFor] 만으로는 모자란가
     *
     * 표본이 2의 거듭제곱이라 목표 해상도 근처에서 **최대 2배(넓이로 4배)까지 크게 뜬다.**
     * 4000×3000 쪽을 긴 변 2400 으로 뜨면 4000/2 = 2000 < 2400 이라 표본이 1에 머물고,
     * 한 장이 **45.8 MiB** 가 된다. 1080×2400 화면의 한 장 예산(9.9 MiB)의 4.6배다.
     * 사진 한 장을 보는 화면에서는 그래도 살지만, **만화는 그런 장을 네 장 동시에 든다.**
     *
     * 그래서 만화처럼 여러 장을 동시에 드는 화면은 목표 해상도가 아니라 **바이트**를
     * 기준으로 표본을 정한다. 흐려지는 것은 눈에 보이지만 죽는 것은 되돌릴 수 없다 —
     * [budgetOf] 의 `detailCap` 주석이 이미 같은 선택을 해 두었다.
     *
     * @param capBytes 결과 비트맵 한 장이 넘어서는 안 되는 바이트.
     */
    fun sampleForBudget(width: Int, height: Int, targetLongest: Int, capBytes: Long): Int {
        var sample = sampleFor(width, height, targetLongest)
        if (capBytes <= 0) return sample
        // 넓이가 표본의 제곱으로 줄므로 몇 번이면 반드시 든다. 상한을 두어 무한 루프를 막는다.
        var guard = 0
        while (bytesAt(width, height, sample) > capBytes && guard < MAX_SAMPLE_STEPS) {
            sample *= 2
            guard++
        }
        return sample
    }

    /** 표본을 이보다 더 키우지 않는다. 2^16 이면 어떤 그림도 한 화소로 줄어든다. */
    private const val MAX_SAMPLE_STEPS = 16

    /**
     * 만화 쪽 한 장에 허락하는 바이트.
     *
     * 살아 있는 장수로 예산을 나눈 값이다 — 그래야 **네 장을 동시에 들어도** 예산 안이다.
     */
    fun comicPageCap(budget: Budget): Long =
        (budget.liveCap / budget.livePages.coerceAtLeast(1)).coerceAtLeast(1L)

    /** 이 표본으로 디코딩하면 몇 바이트인가. */
    fun bytesAt(width: Int, height: Int, sample: Int): Long {
        val w = (width + sample - 1) / sample
        val h = (height + sample - 1) / sample
        return w.toLong() * h * BYTES_PER_PIXEL
    }

    /** 이 크기를 한 장으로 그릴 수 있는가. */
    fun canDraw(width: Int, height: Int): Boolean =
        width.toLong() * height * BYTES_PER_PIXEL <= MAX_BITMAP_BYTES
}
