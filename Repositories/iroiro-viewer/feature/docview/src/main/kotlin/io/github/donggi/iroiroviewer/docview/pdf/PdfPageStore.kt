package io.github.donggi.iroiroviewer.docview.pdf

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.safety.PdfLimits

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
 */
class PdfPageStore(
    private val doc: PdfDocument,
    private val budget: ImageLimits.Budget,
) {

    /** 맞춤 층 하나. 뷰포트가 바뀌면 키가 달라져 옛것이 저절로 밀려난다. */
    private data class FitKey(val ordinal: Int, val viewportWidth: Int, val viewportHeight: Int)

    /** 선명화 타일 하나. 자리와 배율이 키에 든다. */
    private data class TileKey(
        val ordinal: Int,
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

    /** 확대했을 때 그 자리에 얹는 조각. */
    data class DetailTile(val image: ImageBitmap, val tile: PdfLimits.Tile)

    val pageCount: Int get() = doc.pageCount

    /** 한 장에 허락하는 바이트. */
    val pageCap: Long = ImageLimits.pageCap(budget)

    /** 이미 잰 크기가 있으면 그것. 없으면 null — 문서를 만지지 않는다. */
    fun knownSize(ordinal: Int): IntArray? = synchronized(sizes) { sizes[ordinal] }

    private val sizes = HashMap<Int, IntArray>()

    /** 쪽 크기를 포인트로. */
    suspend fun size(ordinal: Int): IntArray? {
        knownSize(ordinal)?.let { return it }
        val measured = doc.sizeOf(ordinal) ?: return null
        synchronized(sizes) { sizes[ordinal] = measured }
        return measured
    }

    /**
     * 뷰포트에 맞춘 한 장.
     *
     * 비트맵 크기를 **쪽마다 다시 계산한다** — 한 문서 안에서도 쪽 크기가 다르고
     * (`595×842` 와 `792×612` 가 한 파일에 있다), 첫 쪽 크기로 만들어 돌려 쓰면 가로
     * 쪽에서 곧바로 찌그러진다.
     */
    suspend fun fitted(ordinal: Int, viewportWidth: Int, viewportHeight: Int): ImageBitmap? {
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        val key = FitKey(ordinal, viewportWidth, viewportHeight)
        fitted.get(key)?.let { return it }

        val size = size(ordinal) ?: return null
        val scale = PdfLimits.fitScale(size[0], size[1], viewportWidth, viewportHeight)
        val plan = PdfLimits.pageBitmap(size[0], size[1], scale, pageCap) ?: return null

        val began = System.nanoTime()
        val bitmap = doc.render(
            ordinal,
            PdfEngine.Spec(plan.width, plan.height, plan.scale),
        ) ?: return null
        val image = bitmap.asImageBitmap()
        fitted.put(key, image)
        Iro.d(TAG) {
            val ms = (System.nanoTime() - began) / 1_000_000
            "쪽 $ordinal ${size[0]}x${size[1]}pt -> ${image.width}x${image.height} ${ms}ms"
        }
        return image
    }

    /**
     * 확대한 자리를 더 선명하게 그린 조각.
     *
     * 확대하지 않았으면 곧바로 null 이다 — 맞춤 층이 이미 그 해상도라 같은 그림을 두 번
     * 그리는 일이 된다([PdfLimits.needsDetail]).
     *
     * @param focusX·[focusY] 쪽 안의 비율 좌표(0~1). 범위 밖이어도 잘라 준다.
     * @param maxScale 화면이 허락하는 최대 확대(포인트 → 화소). [PdfLimits.tileFor] 참고.
     */
    suspend fun detail(
        ordinal: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        scale: Double,
        focusX: Double,
        focusY: Double,
        maxScale: Double,
    ): DetailTile? {
        val size = size(ordinal) ?: return null
        val tile = PdfLimits.tileFor(
            widthPt = size[0],
            heightPt = size[1],
            scale = scale,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            focusX = focusX,
            focusY = focusY,
            capBytes = budget.detailCap,
            maxScale = maxScale,
        ) ?: return null

        val key = TileKey(
            ordinal = ordinal,
            scaleMilli = (tile.scale * 1000).toInt(),
            srcLeft = tile.srcLeft,
            srcTop = tile.srcTop,
            width = tile.width,
            height = tile.height,
        )
        tiles.get(key)?.let { return it }

        val bitmap = doc.render(
            ordinal,
            PdfEngine.Spec(
                bitmapWidth = tile.width,
                bitmapHeight = tile.height,
                scale = tile.scale,
                srcLeft = tile.srcLeft,
                srcTop = tile.srcTop,
            ),
        ) ?: return null
        val made = DetailTile(bitmap.asImageBitmap(), tile)
        tiles.put(key, made)
        // **확대는 화면 캡처로 판정하지 않는다**(6단계가 그것으로 세 번 틀렸다).
        // 배율과 자리를 여기 찍어 두면 기기에서 logcat 으로 읽을 수 있다.
        // 릴리스에서는 호출 지점째 사라진다.
        Iro.d(TAG) {
            "타일 쪽 $ordinal x${"%.2f".format(tile.scale)} " +
                "(${tile.srcLeft},${tile.srcTop}) ${tile.width}x${tile.height} " +
                "/ 쪽 ${tile.pageWidth}x${tile.pageHeight}"
        }
        return made
    }

    /**
     * 비트맵을 전부 놓는다. **회전했을 때 부른다** — 예산이 화면 크기에서 나오므로
     * 가로·세로가 바뀌면 옛 예산으로 뜬 것을 들고 있을 이유가 없다.
     *
     * 잰 쪽 크기는 지우지 않는다. 그것은 화면과 무관한 문서의 사실이다.
     */
    fun clearBitmaps() {
        fitted.evictAll()
        tiles.evictAll()
    }

    private fun bytesOf(image: ImageBitmap): Int =
        (image.width.toLong() * image.height * ImageLimits.BYTES_PER_PIXEL)
            .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private companion object {
        const val TAG = "pdf"
    }
}
