package io.github.donggi.iroiroviewer.safety

/**
 * 만화 한 권을 읽는 동안의 상한. **순수 함수다.**
 *
 * ## 왜 [ImageLimits] 와 따로 두는가
 *
 * [ImageLimits] 가 답하는 것은 "비트맵을 얼마나 들어도 되는가" 다. 만화에는 그 밖에
 * 두 가지가 더 있다.
 *
 * - **디코딩 전의 원본 바이트.** solid 아카이브(7z·RAR)는 한 쪽만 꺼낼 수가 없어서
 *   앞에서부터 훑으며 여러 쪽을 한꺼번에 받아 둬야 한다. 그 바이트는 비트맵이 아니지만
 *   같은 힙에 산다.
 * - **웹툰 한 장의 높이.** 폭에 맞춰 통짜로 뜨면 800×12000 짜리 한 장이 예산 전체를
 *   넘는다. 그것은 표본을 키워 흐리게 만들 문제가 아니라 **띠로 잘라 그릴** 문제다.
 *
 * 두 값을 [ImageLimits.Budget] 에서 끌어내는 것이 이 파일의 요점이다 — 예산이 둘로
 * 갈려 서로를 모르면 각자 '상한 안' 이면서 합쳐서 힙을 넘긴다.
 */
object ComicLimits {

    /**
     * solid 아카이브에서 **힙에 들고 있어도 되는 원본 바이트.**
     *
     * 비트맵 예산의 절반을 준다. 쪽 파일은 압축된 JPEG·PNG 이라 같은 쪽을 디코딩한
     * 비트맵보다 대개 한 자릿수 작고(1 MB 대 10 MB), 그래서 이 절반으로 수십 쪽이
     * 들어온다. 비트맵 예산에서 끌어내므로 힙이 작은 기기에서는 함께 줄어든다.
     *
     * **디스크에 쓰지 않는다.** 쪽은 사용자 만화의 *원본 그대로*라, 캐시 폴더에 뽑으면
     * 200쪽짜리 열 권을 본 것만으로 만화가 통째로 앱 저장소에 평문 복제된다
     * (`ImageSource.decodeFitted(bytes, …)` 의 주석이 같은 판단을 이미 적어 두었다).
     */
    fun windowBytes(budget: ImageLimits.Budget): Long =
        (budget.liveCap / 2).coerceIn(MIN_WINDOW_BYTES, MAX_WINDOW_BYTES)

    /**
     * 창이 이보다 작아지지는 않는다.
     *
     * **쪽 수가 아니라 바이트로 바닥을 둔다.** '최소 32쪽' 같은 쪽 수 바닥은 쪽 하나의
     * 크기를 모르는 채 정하는 값이라, 한 쪽이 4 MB 인 고해상도 만화에서 128 MB 를
     * 뜻하게 된다. 바이트로 두면 그런 일이 구조적으로 없다.
     */
    const val MIN_WINDOW_BYTES = 4L * 1024 * 1024

    /**
     * 창이 이보다 커지지도 않는다.
     *
     * 창을 키우면 되돌아갈 때 다시 푸는 일이 줄지만, 그 이득은 금세 평평해지고
     * 힙 압박은 계속 는다. 48 MiB 면 흔한 쪽 크기(1~2 MB)로 20~40쪽이다.
     */
    const val MAX_WINDOW_BYTES = 48L * 1024 * 1024

    /**
     * 세로 모드에서 통짜 비트맵으로 그려도 되는 쪽인가.
     *
     * 웹툰 한 회는 폭 800에 높이 10,000을 넘는 것이 흔하다. 뷰포트 폭에 맞춰 뜨면
     * 1080×13500 = **55 MiB** 한 장이고, 그런 것이 화면에 둘씩 걸린다. 그래서 높이가
     * 뷰포트의 [MAX_TALL_RATIO] 배를 넘으면 통짜로 들지 않고 띠로 자른다.
     */
    fun isTall(pageWidth: Int, pageHeight: Int, viewportWidth: Int, viewportHeight: Int): Boolean {
        if (pageWidth <= 0 || pageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return false
        val drawnHeight = pageHeight.toDouble() * viewportWidth / pageWidth
        return drawnHeight > viewportHeight.toDouble() * MAX_TALL_RATIO
    }

    /** 이 배수를 넘으면 띠로 자른다. */
    const val MAX_TALL_RATIO = 3

    /**
     * 띠 하나의 **원본 화소 높이.**
     *
     * 화면에 그려질 높이가 뷰포트 한 개 분량이 되도록 원본 좌표로 되돌린 값이다.
     * 띠가 화면보다 작으면 한 화면을 그리는 데 여러 장이 필요해 경계가 눈에 띄고,
     * 크면 한 장이 예산을 먹는다.
     */
    fun bandHeight(pageWidth: Int, viewportWidth: Int, viewportHeight: Int): Int {
        if (pageWidth <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1
        val h = viewportHeight.toDouble() * pageWidth / viewportWidth
        return h.toInt().coerceIn(MIN_BAND_HEIGHT, MAX_BAND_HEIGHT)
    }

    private const val MIN_BAND_HEIGHT = 64
    private const val MAX_BAND_HEIGHT = 8192

    /**
     * 띠 캐시의 상한.
     *
     * **[ImageLimits.Budget.detailCap] 을 쓴다.** 그것이 `budgetOf` 가 이미 '살아 있는
     * 쪽 말고 더 써도 되는 양' 으로 계산해 둔 수이고, 힙이 작은 기기에서 0 이 되어
     * 캐시가 저절로 꺼진다 — `budgetOf` 의 주석이 '흐린 것이 죽는 것보다 낫다' 고
     * 적은 그 설계 의도 그대로다. 0 이면 화면에 걸린 띠만 들고 나머지는 그때그때 뜬다.
     */
    fun bandCacheBytes(budget: ImageLimits.Budget): Long = budget.detailCap

    /**
     * 세로 모드에서 미리 뜨는 양을 여기서 정하지 않는다.
     *
     * `LazyColumn` 이 이미 보이는 항목의 앞뒤를 한 겹 더 구성하고, 띠 하나가 화면 한 장
     * 분량이라 그것이 곧 '한 화면 앞서기' 다. 우리가 따로 숫자를 두면 **같은 것을 두 곳에서
     * 정하게** 되고, 둘이 어긋나는 날이 온다.
     */
}
