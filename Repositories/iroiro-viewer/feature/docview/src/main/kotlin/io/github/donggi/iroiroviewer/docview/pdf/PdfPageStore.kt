package io.github.donggi.iroiroviewer.docview.pdf

import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.safety.PdfLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 쪽을 그려 주고, 최근 것을 **예산 안에서** 들고 있는다.
 *
 * `ComicPageStore` 와 **같은 것**: 상한이 [ImageLimits.Budget.liveCap], 한 장의 상한이
 * [ImageLimits.pageCap], 키에 뷰포트를 넣어 옛 크기로 뜬 것이 살아남지 않게 하는 것,
 * 그리고 **축출은 하되 `recycle()` 은 하지 않는 것.**
 *
 * `recycle()` 을 안 하는 이유는 만화보다 무겁다. 만화에서는 '아직 그리고 있는 프레임이
 * 파괴된 비트맵을 만나 앱이 죽는다' 였는데, PDF 는 recycle 된 비트맵에 `render` 하면
 * 예외가 아니라 **`SIGABRT` 로 프로세스가 즉사한다**(조사 중 실제로 죽었다). 로그 한 줄
 * 남기고 앱이 사라지는 종류의 실패다.
 *
 * **다른 것**: 창(window)과 미리 읽기가 없다. 9단계의 창은 solid 아카이브의 정의(앞
 * 14쪽을 풀어야 15쪽이 나온다) 때문에 생긴 장치인데, `PdfRenderer` 는 쪽 무작위 접근이
 * 싸다(2000쪽 문서에서 마지막 쪽 열기 9.7ms 실측). **없어도 되는 상태를 만들면 그 상태가
 * 틀리는 날이 온다.**
 *
 * 그리고 선명화가 띠(band)가 아니라 **타일 재렌더**다. 만화는 원본 비트맵을
 * `BitmapRegionDecoder` 로 다시 읽지만 PDF 에는 디코딩할 원본이 없다 — 대신 더 큰 배율로
 * 다시 그리면 되고, 그것이 PDF 가 벡터라서 얻는 이득이다.
 *
 * ## 화면 한 칸 = 가상 쪽 하나(14단계)
 *
 * 두 쪽 보기를 더하면서 그리는 단위가 쪽이 아니라 **화면 한 칸([PdfSpreads.View])** 이 됐다. 쪽 하나면 그 쪽, 펼침이면
 * 두 쪽을 나란히 놓은 가상 쪽이다([PdfSpreads.layout]). 맞춤·타일·예산 계산은 가상 쪽의 크기로 `PdfLimits` 를 그대로
 * 부른다 — 계산을 두 벌로 두지 않는다.
 *
 * ## 쪽 목록의 작은 그림
 *
 * 쪽 목록(격자)이 열려 있는 동안에만 작은 그림을 든다. 그 예산은 **선명화층의 몫**([ImageLimits.Budget.detailCap])이다 —
 * 격자가 쪽을 덮고 있는 동안에는 확대한 조각이 보일 일이 없으므로, 열 때 조각을 놓고([openGrid] — 캐시는 여기서, 화면의 쪽
 * 층이 쥔 조각은 [gridOpen] 을 보고 그쪽이 놓는다) 닫을 때 작은 그림을 놓는다([closeGrid]). 그래서 살아 있는 양이 예산이 이미
 * 허락한 `liveCap + detailCap` 을 넘지 않는다. 선명화 예산이 0 인 작은 힙에서는 작은 그림도 없다(번호만 보인다).
 */
class PdfPageStore(
    private val doc: PdfDocument,
    private val budget: ImageLimits.Budget,
) {

    /** 맞춤 층 하나. 뷰포트가 바뀌면 키가 달라져 옛것이 저절로 밀려난다. */
    private data class FitKey(val view: PdfSpreads.View, val viewportWidth: Int, val viewportHeight: Int)

    /** 선명화 타일 하나. 자리와 배율이 키에 든다. */
    private data class TileKey(
        val view: PdfSpreads.View,
        val scaleMilli: Int,
        val srcLeft: Int,
        val srcTop: Int,
        val width: Int,
        val height: Int,
    )

    private val fitted = object : LruCache<FitKey, ImageBitmap>(
        budget.liveCap.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: FitKey, value: ImageBitmap): Int = bytesOf(value)
    }

    /**
     * 선명화 캐시. 예산이 0 이면 [LruCache] 가 아무것도 담지 못하므로 [detail] 이
     * 언제나 null 을 준다 — **힙이 작은 기기에서 선명화가 저절로 꺼지는 것이
     * [ImageLimits.budgetOf] 의 설계 의도다**('흐린 것이 죽는 것보다 낫다').
     */
    private val tiles = object : LruCache<TileKey, DetailTile>(
        budget.detailCap.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: TileKey, value: DetailTile): Int = bytesOf(value.image)
    }

    /** 쪽 목록의 작은 그림. 선명화층의 예산을 빌려 쓴다(머리말). */
    private val thumbs = object : LruCache<Int, ImageBitmap>(
        budget.detailCap.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = bytesOf(value)
    }

    /** 확대했을 때 그 자리에 얹는 조각. */
    data class DetailTile(val image: ImageBitmap, val tile: PdfLimits.Tile)

    val pageCount: Int get() = doc.pageCount

    /** 한 장에 허락하는 바이트. */
    val pageCap: Long = ImageLimits.pageCap(budget)

    /** 이미 잰 크기가 있으면 그것. 없으면 null — 문서를 만지지 않는다. */
    fun knownSize(ordinal: Int): IntArray? = synchronized(sizes) { sizes[ordinal] }

    /** 이미 잰 크기로 가상 쪽을 놓는다. 하나라도 모르면 null — 문서를 만지지 않는다. */
    internal fun knownLayout(view: PdfSpreads.View): PdfSpreads.Layout? {
        val measured = view.pages.map { knownSize(it) ?: return null }
        return PdfSpreads.layout(view.pages, measured)
    }

    private val sizes = HashMap<Int, IntArray>()

    /** 쪽 크기를 포인트로. */
    suspend fun size(ordinal: Int): IntArray? {
        knownSize(ordinal)?.let { return it }
        val measured = doc.sizeOf(ordinal) ?: return null
        synchronized(sizes) { sizes[ordinal] = measured }
        return measured
    }

    /** 가상 쪽을 놓는다(쪽 크기를 재 가며). */
    internal suspend fun layout(view: PdfSpreads.View): PdfSpreads.Layout? {
        val measured = view.pages.map { size(it) ?: return null }
        return PdfSpreads.layout(view.pages, measured)
    }

    /** 쪽 하나를 뷰포트에 맞춘 한 장. [fitted] 의 쪽 하나짜리다. */
    suspend fun fitted(ordinal: Int, viewportWidth: Int, viewportHeight: Int): ImageBitmap? =
        fitted(PdfSpreads.View.single(ordinal), viewportWidth, viewportHeight)

    /**
     * 뷰포트에 맞춘 한 장.
     *
     * 비트맵 크기를 **쪽마다 다시 계산한다** — 한 문서 안에서도 쪽 크기가 다르고
     * (`595×842` 와 `792×612` 가 한 파일에 있다), 첫 쪽 크기로 만들어 돌려 쓰면 가로
     * 쪽에서 곧바로 찌그러진다.
     */
    internal suspend fun fitted(view: PdfSpreads.View, viewportWidth: Int, viewportHeight: Int): ImageBitmap? {
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        val key = FitKey(view, viewportWidth, viewportHeight)
        fitted.get(key)?.let { return it }

        val layout = layout(view) ?: return null
        val scale = PdfLimits.fitScale(layout.width, layout.height, viewportWidth, viewportHeight)
        val plan = PdfLimits.pageBitmap(layout.width, layout.height, scale, pageCap) ?: return null

        val began = System.nanoTime()
        val bitmap = doc.render(
            layout,
            PdfEngine.Spec(plan.width, plan.height, plan.scale),
        ) ?: return null
        val image = bitmap.asImageBitmap()
        fitted.put(key, image)
        Iro.d(TAG) {
            val ms = (System.nanoTime() - began) / 1_000_000
            "쪽 ${view.pages} ${layout.width}x${layout.height}pt -> ${image.width}x${image.height} ${ms}ms"
        }
        return image
    }

    /** 쪽 하나의 선명한 조각. [detail] 의 쪽 하나짜리다. */
    suspend fun detail(
        ordinal: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        scale: Double,
        focusX: Double,
        focusY: Double,
        maxScale: Double,
    ): DetailTile? = detail(PdfSpreads.View.single(ordinal), viewportWidth, viewportHeight, scale, focusX, focusY, maxScale)

    /**
     * 확대한 자리를 더 선명하게 그린 조각.
     *
     * 확대하지 않았으면 곧바로 null 이다 — 맞춤 층이 이미 그 해상도라 같은 그림을 두 번
     * 그리는 일이 된다([PdfLimits.needsDetail]).
     *
     * @param focusX·[focusY] 가상 쪽 안의 비율 좌표(0~1). 범위 밖이어도 잘라 준다.
     * @param maxScale 화면이 허락하는 최대 확대(포인트 → 화소). [PdfLimits.tileFor] 참고.
     */
    internal suspend fun detail(
        view: PdfSpreads.View,
        viewportWidth: Int,
        viewportHeight: Int,
        scale: Double,
        focusX: Double,
        focusY: Double,
        maxScale: Double,
    ): DetailTile? {
        if (_gridOpen.value) return null
        val layout = layout(view) ?: return null
        val tile = PdfLimits.tileFor(
            widthPt = layout.width,
            heightPt = layout.height,
            scale = scale,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            focusX = focusX,
            focusY = focusY,
            capBytes = budget.detailCap,
            maxScale = maxScale,
        ) ?: return null

        val key = TileKey(
            view = view,
            scaleMilli = (tile.scale * 1000).toInt(),
            srcLeft = tile.srcLeft,
            srcTop = tile.srcTop,
            width = tile.width,
            height = tile.height,
        )
        tiles.get(key)?.let { return it }

        val bitmap = doc.render(
            layout,
            PdfEngine.Spec(
                bitmapWidth = tile.width,
                bitmapHeight = tile.height,
                scale = tile.scale,
                srcLeft = tile.srcLeft,
                srcTop = tile.srcTop,
            ),
        ) ?: return null
        // 그리는 사이에 쪽 목록이 열렸으면 버린다 — 담으면 격자가 쓰는 예산(선명화층의 몫)을 조각이 도로 먹는다.
        if (_gridOpen.value) return null
        val made = DetailTile(bitmap.asImageBitmap(), tile)
        tiles.put(key, made)
        // **확대는 화면 캡처로 판정하지 않는다**(6단계가 그것으로 세 번 틀렸다).
        // 배율과 자리를 여기 찍어 두면 기기에서 logcat 으로 읽을 수 있다.
        // 릴리스에서는 호출 지점째 사라진다.
        Iro.d(TAG) {
            "타일 쪽 ${view.pages} x${"%.2f".format(tile.scale)} " +
                "(${tile.srcLeft},${tile.srcTop}) ${tile.width}x${tile.height} " +
                "/ 쪽 ${tile.pageWidth}x${tile.pageHeight}"
        }
        return made
    }

    private val _gridOpen = MutableStateFlow(false)

    /**
     * 쪽 목록이 열려 있는가. **화면의 쪽 층이 이것을 본다** — 캐시를 비워도 지금 보이는 쪽이 든 조각(`DetailLayer`)은 그
     * 컴포저블이 쥐고 있어 놓이지 않는다. 열리면 쪽 층이 조각을 놓고, 닫히면 다시 청한다(`DocViewScreen` 의 `DocPageView`).
     * 그래야 격자가 열린 동안 살아 있는 양이 정말 `liveCap + detailCap` 안이다(검토가 잡았다 — 캐시만 비웠었다).
     */
    val gridOpen: StateFlow<Boolean> = _gridOpen.asStateFlow()

    /** 쪽 목록을 연다 — 선명화 조각을 놓아 작은 그림의 예산을 비운다(머리말). */
    fun openGrid() {
        _gridOpen.value = true
        tiles.evictAll()
    }

    /** 쪽 목록을 닫는다 — 작은 그림을 놓는다. 선명화는 쪽 층이 다시 청한다. */
    fun closeGrid() {
        _gridOpen.value = false
        thumbs.evictAll()
    }

    /**
     * 쪽 목록의 작은 그림 하나. [boxWidth]×[boxHeight] 화소 상자에 맞춘다. 선명화 예산이 없으면(작은 힙) null.
     *
     * 작은 그림 하나가 선명화 예산을 다 먹지 않게 한 장의 상한을 예산의 1/[MIN_THUMBS] 로 누른다.
     */
    suspend fun thumbnail(ordinal: Int, boxWidth: Int, boxHeight: Int): ImageBitmap? {
        if (budget.detailCap <= 0 || boxWidth <= 0 || boxHeight <= 0) return null
        thumbs.get(ordinal)?.let { return it }
        val size = size(ordinal) ?: return null
        val scale = PdfLimits.fitScale(size[0], size[1], boxWidth, boxHeight)
        val plan = PdfLimits.pageBitmap(size[0], size[1], scale, budget.detailCap / MIN_THUMBS) ?: return null
        val one = PdfSpreads.Layout(size[0], size[1], listOf(PdfSpreads.Placement(ordinal, 0, 0, size[0], size[1])))
        val bitmap = doc.render(one, PdfEngine.Spec(plan.width, plan.height, plan.scale)) ?: return null
        val image = bitmap.asImageBitmap()
        // 닫힌 뒤에 도착한 것은 담지 않는다 — 격자 밖에서 예산을 먹는다.
        if (_gridOpen.value) thumbs.put(ordinal, image)
        return image
    }

    /** 쪽 하나에서 찾는다(API 35 이상). */
    @RequiresApi(35)
    internal suspend fun search(ordinal: Int, query: String): List<PdfSearch.Match>? = doc.search(ordinal, query)

    /**
     * 비트맵을 전부 놓는다. **회전했을 때 부른다** — 예산이 화면 크기에서 나오므로
     * 가로·세로가 바뀌면 옛 예산으로 뜬 것을 들고 있을 이유가 없다.
     *
     * 잰 쪽 크기는 지우지 않는다. 그것은 화면과 무관한 문서의 사실이다.
     */
    fun clearBitmaps() {
        fitted.evictAll()
        tiles.evictAll()
        thumbs.evictAll()
    }

    private fun bytesOf(image: ImageBitmap): Int =
        (image.width.toLong() * image.height * ImageLimits.BYTES_PER_PIXEL)
            .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private companion object {
        const val TAG = "pdf"

        /** 선명화 예산을 작은 그림 몇 장이 나눠 쓰는가(적어도). */
        const val MIN_THUMBS = 24L
    }
}
