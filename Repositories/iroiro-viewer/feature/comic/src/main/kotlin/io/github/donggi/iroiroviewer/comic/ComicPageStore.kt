package io.github.donggi.iroiroviewer.comic

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.AnimationLimits
import io.github.donggi.iroiroviewer.safety.ComicLimits
import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.ui.image.ImageFormats
import io.github.donggi.iroiroviewer.ui.image.ImageIo
import io.github.donggi.iroiroviewer.ui.image.decodeAnimated
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 쪽 하나에 대해 **그리기 전에 알아야 하는 것.**
 *
 * 치수를 먼저 아는 것이 이 구조체의 요점이다. 세로 모드는 쪽을 통짜로 들지 띠로 자를지를
 * 그리기 전에 정해야 하고, 애니메이션은 틀어도 되는지를 프레임 버퍼가 잡히기 전에 정해야
 * 한다. 둘 다 답이 치수에 달려 있는데, 알아내려고 한 번 디코딩하면 막으려던 그 메모리를
 * 이미 쓴 뒤다.
 */
data class PageInfo(
    val width: Int,
    val height: Int,
    /** 플랫폼이 틀 수 있는 애니메이션인가(GIF·애니WebP). */
    val animated: Boolean,
    /**
     * APNG 인가.
     *
     * **[animated] 와 따로 둔다.** APNG 은 움직이는 파일이지만 플랫폼이 첫 장면만 준다.
     * 하나로 합치면 화면이 "움직입니다" 라고 말해 놓고 멈춘 그림을 보여 주게 된다 —
     * 6단계가 판별기(`ImageFormats.isApng`)를 미리 넣어 둔 것이 이 자리를 위해서다.
     */
    val apng: Boolean,
    /**
     * EXIF 방향. [width]·[height] 는 **저장 방향**이다(`BitmapFactory` 는 방향을 적용하지
     * 않는다) — 바닥층은 적용해서 뜨므로 90°·270° 쪽이면 가로세로가 바뀌어 보인다.
     */
    val orientation: Int = 1,
) {
    /** 90°·270° 로 눕혀 보여야 하는가. */
    private val turned: Boolean get() = orientation in 5..8

    /** 화면에 보이는 방향의 폭. 최대 배율(원본의 2배)은 이것으로 잰다. */
    val displayWidth: Int get() = if (turned) height else width
    val displayHeight: Int get() = if (turned) width else height

    /**
     * 확대했을 때 원본에서 조각을 떠 얹어도 되는가. **정방향 쪽만**이다 — 영역 디코딩은
     * 방향을 적용하지 않으므로 방향 태그가 붙은 쪽에 얹으면 조각이 눕거나 뒤집힌다.
     * 만화 쪽에 방향 태그가 붙는 일은 드물어 걸리는 자리가 거의 없다.
     */
    val canUseRegionDecoder: Boolean get() = !animated && (orientation == 0 || orientation == 1)
}

/**
 * 쪽을 디코딩해 주고, 최근 것을 **예산 안에서** 들고 있는다.
 *
 * ## 축출은 하되 `recycle` 은 하지 않는다
 *
 * [LruCache] 에서 밀려난 비트맵을 즉시 `recycle()` 하면, 아직 그리고 있는 프레임이
 * 파괴된 비트맵을 만나 앱이 죽는다. 참조만 놓으면 GC 가 마지막 그리기가 끝난 뒤에
 * 걷어 간다 — **지금 걷어 가는 것보다 조금 늦게 걷어 가는 것이 안전하다.**
 *
 * ## 왜 상한이 [ImageLimits.Budget.liveCap] 인가
 *
 * 그 값이 '동시에 살아 있어도 되는 바닥층 전체' 이고, 만화가 실제로 동시에 드는 것이
 * 정확히 그것(앞뒤 한 장씩 + 넘기는 도중 두 장)이다. 한 장의 바이트는
 * [ImageLimits.pageCap] 으로 따로 눌러 두었으므로 두 값이 함께 성립한다.
 */
class ComicPageStore(
    private val source: ComicSource,
    private val budget: ImageLimits.Budget,
) {

    /** 한 쪽 보기의 쪽([StillKey])과 두 쪽 보기의 반쪽([HalfKey])이 **한 상한**을 나눠 쓴다 — 둘 다 바닥층이다. */
    private val stills = object : LruCache<Any, ImageBitmap>(
        budget.liveCap.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: Any, value: ImageBitmap): Int =
            (value.width.toLong() * value.height * ImageLimits.BYTES_PER_PIXEL)
                .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private val bands = object : LruCache<BandKey, ImageBitmap>(
        ComicLimits.bandCacheBytes(budget).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: BandKey, value: ImageBitmap): Int =
            (value.width.toLong() * value.height * ImageLimits.BYTES_PER_PIXEL)
                .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private val infos = HashMap<Int, PageInfo>()
    private val infoLock = Mutex()

    val pageCount: Int get() = source.pages.size

    /** 한 장에 허락하는 바이트. 화면이 표본을 정할 때 그대로 넘긴다. */
    val pageCap: Long = ImageLimits.pageCap(budget)

    /** 선명화 조각 한 장의 상한. */
    val detailCap: Long = budget.detailCap

    /**
     * 두 쪽 보기에서 **쪽 하나**에 허락하는 바이트 — [pageCap] 의 절반(14단계).
     *
     * 두 쪽이 **화면 한 장의 몫을 나눠 쓴다.** 페이저는 펼침 넷을 들므로(앞뒤 한 장씩 + 넘기는 도중 둘) 반쪽 여덟 장이
     * 한 장짜리 넷과 같은 바이트다 — [ImageLimits.Budget.liveCap] 이 그대로 성립하고 [stills] 의 상한도 그대로다.
     * 한 쪽이 차지하는 화면이 절반이라 **맞춘 크기 그대로 뜨면**([half]) 흐려지지 않는다 — 2의 거듭제곱 표본으로 뜨면
     * 흔한 쪽 크기에서 흐려진다([SpreadMath.halfSize] 의 주석).
     */
    val spreadPageCap: Long = (pageCap / 2).coerceAtLeast(1L)

    /**
     * 쪽 목록(격자)의 썸네일 전체에 허락하는 바이트(14단계).
     *
     * **선명화 몫([ImageLimits.Budget.detailCap])을 빌린다.** `budgetOf` 가 '살아 있는 쪽 말고 더 써도 되는 양' 으로
     * 계산해 둔 수이고, 격자가 떠 있는 동안에는 새 선명화 조각도 세로 모드의 띠도 뜨지 않는다 — 격자를 열 때 띠 캐시를
     * 비운다([clearBands]). **정확히 0 은 아니다**: 격자를 열기 전에 확대해 둔 쪽의 조각 한 장과 화면에 걸린 띠 한두
     * 장은 그 쪽이 컴포지션에 남아 있는 동안 산다. 힙이 작은 기기에서 이 몫이 0 이 되면 썸네일을 뜨지 않고 칸에 쪽 번호만
     * 보인다 — `budgetOf` 의 '흐린 것이 죽는 것보다 낫다' 그대로다.
     */
    val thumbCap: Long = budget.detailCap

    /** 이미 잰 것이 있으면 그것. 없으면 null — 디코딩하지 않는다. */
    fun knownInfo(ordinal: Int): PageInfo? = synchronized(infos) { infos[ordinal] }

    /**
     * 쪽을 재 본다. 바이트를 읽어야 하므로 값이 공짜는 아니다.
     *
     * solid 아카이브에서 창 밖의 쪽을 재려 들면 패스가 한 번 더 돈다. 그래서 화면은
     * **보이는 쪽과 그 이웃만** 잰다.
     */
    suspend fun info(ordinal: Int): PageInfo? {
        knownInfo(ordinal)?.let { return it }
        val bytes = source.bytes(ordinal) ?: return null
        return infoLock.withLock {
            knownInfo(ordinal)?.let { return it }
            val size = ImageIo.bounds(bytes) ?: return null
            val info = PageInfo(
                width = size[0],
                height = size[1],
                animated = ImageFormats.playsAnimated(bytes) &&
                    AnimationLimits.canAnimate(size[0], size[1], budget),
                apng = ImageFormats.isApng(bytes),
                orientation = ImageIo.exifOrientation(bytes),
            )
            synchronized(infos) { infos[ordinal] = info }
            info
        }
    }

    /**
     * 정지 쪽 한 장.
     *
     * @param targetLongest 긴 변이 이 화소 안에 들어오게 뜬다.
     */
    suspend fun still(ordinal: Int, targetLongest: Int): ImageBitmap? {
        if (targetLongest <= 0) return null
        val key = StillKey(ordinal, targetLongest)
        stills.get(key)?.let { return it }
        val began = System.nanoTime()
        val bytes = source.bytes(ordinal) ?: return null
        val read = System.nanoTime()
        val bitmap = ImageIo.decodeFitted(
            bytes = bytes,
            targetLongest = targetLongest,
            capBytes = pageCap,
        ) ?: return null
        val image = bitmap.asImageBitmap()
        stills.put(key, image)
        // 쪽 하나의 값을 **읽기와 디코딩으로 갈라** 찍는다. 합계만 보면 solid 아카이브가
        // 느린 것인지 큰 그림이 느린 것인지 구별할 수 없다. 릴리스에서는 사라진다.
        Iro.d(TAG) {
            val readMs = (read - began) / 1_000_000
            val decodeMs = (System.nanoTime() - read) / 1_000_000
            "쪽 $ordinal ${bytes.size}B 읽기 ${readMs}ms 디코딩 ${decodeMs}ms " +
                "-> ${image.width}x${image.height}"
        }
        return image
    }

    /**
     * 두 쪽 보기의 정지 쪽 하나 — 반쪽 자리([slotWidth]×[slotHeight])에 **맞춘 크기 그대로** 뜬다(14단계).
     *
     * [still] 과 길이 다른 까닭은 [SpreadMath.halfSize] 에 있다: 2의 거듭제곱 표본은 반쪽 몫([spreadPageCap])에 들려면
     * 흔한 쪽 크기에서 자리보다 작게 떠서 늘어나 보인다. 그래서 `setTargetSize` 로 자리 크기에 맞춰 뜨고, 그 크기가
     * 몫을 넘으면(힙이 작은 기기) 비를 지켜 줄인다 — 바이트가 먼저인 것은 [still] 과 같다.
     */
    suspend fun half(ordinal: Int, slotWidth: Int, slotHeight: Int): ImageBitmap? {
        if (slotWidth <= 0 || slotHeight <= 0) return null
        val key = HalfKey(ordinal, slotWidth, slotHeight)
        stills.get(key)?.let { return it }
        val began = System.nanoTime()
        val bytes = source.bytes(ordinal) ?: return null
        val read = System.nanoTime()
        val bitmap = decodeToSlot(bytes, slotWidth, slotHeight, spreadPageCap) ?: return null
        val image = bitmap.asImageBitmap()
        stills.put(key, image)
        Iro.d(TAG) {
            val readMs = (read - began) / 1_000_000
            val decodeMs = (System.nanoTime() - read) / 1_000_000
            "반쪽 $ordinal ${bytes.size}B 읽기 ${readMs}ms 디코딩 ${decodeMs}ms -> ${image.width}x${image.height}"
        }
        return image
    }

    /**
     * 화면이 두 쪽을 펼치는가를 소스에 알린다 — solid 의 창이 뛰어든 자리에서 앞 펼침까지 담게 한다
     * ([Spreads.lookBehind]). 무작위 접근 소스에는 아무 일도 없다.
     */
    fun setTwoUp(twoUp: Boolean) = source.setLookBehind(Spreads.lookBehind(twoUp))

    /**
     * 움직이는 쪽 하나.
     *
     * **캐시하지 않는다.** `AnimatedImageDrawable` 은 상태(지금 몇 번째 프레임인가)를
     * 들고 있어서 두 곳에서 동시에 그리면 서로의 프레임을 밀어낸다. 만화책 안의 GIF 는
     * 드물어 다시 여는 값도 작다.
     */
    suspend fun moving(ordinal: Int, targetLongest: Int, capBytes: Long = pageCap): Drawable? {
        val bytes = source.bytes(ordinal) ?: return null
        return decodeAnimated(bytes, targetLongest = targetLongest, capBytes = capBytes)
    }

    /**
     * 긴 쪽(웹툰)의 띠 하나.
     *
     * 세로 모드에서 쪽 높이가 뷰포트의 [ComicLimits.MAX_TALL_RATIO] 배를 넘으면
     * 이 길로 온다. 통짜로 들면 한 장이 예산 전체를 넘는데, 흐리게 만들어 해결할 문제가
     * 아니다 — 웹툰은 글자를 읽는 그림이다.
     */
    suspend fun band(ordinal: Int, band: Int, viewportWidth: Int, viewportHeight: Int): ImageBitmap? {
        val info = info(ordinal) ?: return null
        val key = BandKey(ordinal, band, viewportWidth)
        bands.get(key)?.let { return it }
        val bytes = source.bytes(ordinal) ?: return null

        val bandSrcHeight = ComicLimits.bandHeight(info.width, viewportWidth, viewportHeight)
        val top = band.toLong() * bandSrcHeight
        if (top >= info.height) return null
        val sample = ImageLimits.sampleForBudget(
            info.width, bandSrcHeight, targetLongestFor(info.width, bandSrcHeight, viewportWidth), pageCap,
        )
        val began = System.nanoTime()
        val bitmap: Bitmap = ImageIo.decodeBand(
            bytes = bytes,
            top = top.toInt(),
            height = bandSrcHeight,
            sample = sample,
        ) ?: return null
        val image = bitmap.asImageBitmap()
        bands.put(key, image)
        Iro.d(TAG) {
            val ms = (System.nanoTime() - began) / 1_000_000
            "띠 $ordinal-$band 원본 ${info.width}x${info.height} 표본 $sample " +
                "-> ${image.width}x${image.height} ${ms}ms"
        }
        return image
    }

    /**
     * 확대한 자리의 **선명한 조각**. [rect] 는 원본 화소 `[왼, 위, 오른, 아래]` 다
     * (정방향 쪽만 오므로 저장 방향과 화면 방향이 같다 — [PageInfo.canUseRegionDecoder]).
     *
     * **캐시하지 않는다.** 확대는 지금 보는 쪽 하나에서만 일어나고, 화면이 새 자리를 청할
     * 때마다 앞 조각은 쓸모가 없어진다. 바이트는 창 안이면 이미 힙에 있다(solid 아카이브).
     */
    suspend fun region(ordinal: Int, rect: IntArray, sample: Int): Bitmap? {
        val bytes = source.bytes(ordinal) ?: return null
        return ImageIo.decodeRegion(bytes, rect = rect, sample = sample, rotation = 0)
    }

    /** 이 쪽을 세로 모드에서 몇 개의 띠로 자를 것인가. 통짜로 들 쪽이면 1. */
    fun bandCount(info: PageInfo, viewportWidth: Int, viewportHeight: Int): Int {
        if (!ComicLimits.isTall(info.width, info.height, viewportWidth, viewportHeight)) return 1
        val h = ComicLimits.bandHeight(info.width, viewportWidth, viewportHeight)
        return ((info.height + h - 1) / h).coerceAtLeast(1)
    }

    /** 뷰포트가 바뀌면(회전) 옛 크기로 뜬 것은 쓸모가 없다. */
    fun clearBitmaps() {
        stills.evictAll()
        bands.evictAll()
    }

    /** 띠 캐시를 비운다. 격자가 그 몫([thumbCap])을 빌리는 동안이다. */
    fun clearBands() {
        bands.evictAll()
    }

    /**
     * 못 연 쪽의 '다른 앱으로 열기' 가 넘길 경로. 폴더 만화의 쪽이고 그 파일이 열릴 때만이다([ComicOpenWith.pageFailureTarget]).
     * 여는 탐침이 디스크를 만지므로 입출력 디스패처에서 돈다.
     */
    suspend fun failedPageTarget(ordinal: Int): String? {
        val file = source.pageFile(ordinal) ?: return null
        return withContext(IroDispatchers.io) { ComicOpenWith.pageFailureTarget(file) }
    }

    /** 쪽 목록의 훑기. 소스의 것을 그대로 부른다([ComicSource.scan]). */
    suspend fun scan(demand: ScanDemand, onPage: (ordinal: Int, bytes: ByteArray?) -> Unit) =
        source.scan(demand, onPage)

    private companion object {
        const val TAG = "Comic"
    }

    private data class StillKey(val ordinal: Int, val targetLongest: Int)

    private data class HalfKey(val ordinal: Int, val slotWidth: Int, val slotHeight: Int)

    private data class BandKey(val ordinal: Int, val band: Int, val viewportWidth: Int)
}

/**
 * **폭에 맞추려면** 긴 변 목표를 얼마로 줘야 하는가.
 *
 * [ImageLimits.sampleFor] 는 '긴 변' 기준이라 폭 맞춤을 바로 표현하지 못한다. 폭이
 * [viewportWidth] 가 되는 배율을 긴 변에 그대로 적용하면 같은 뜻이 되고, 상한 계산을
 * 한 함수에 모아 둘 수 있다.
 */
internal fun targetLongestFor(width: Int, height: Int, viewportWidth: Int): Int {
    if (width <= 0 || viewportWidth <= 0) return viewportWidth.coerceAtLeast(1)
    val longest = maxOf(width, height)
    return (viewportWidth.toLong() * longest / width).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * 쪽 바이트 → 반쪽 자리에 맞춘 비트맵([SpreadMath.halfSize] 의 크기 그대로).
 *
 * 규칙은 `ImageIo.decodeFitted` 의 헤더 콜백과 맞춘다 — 소프트웨어 할당(확대하면 원본을 다시 읽고, 하드웨어 비트맵은
 * 소프트웨어 합성을 막는다), 헤더에서 취소 확인(그 뒤는 네이티브 디코딩이라 끼어들 수 없다), 같은 디스패처(한 번에 하나
 * — 디코딩 임시본이 둘 이상 겹치지 않게). 크기는 헤더의 치수(디코더가 방향을 적용한 뒤의 값)로 잰다.
 */
private suspend fun decodeToSlot(bytes: ByteArray, slotWidth: Int, slotHeight: Int, capBytes: Long): Bitmap? =
    withContext(IroDispatchers.image) {
        val job = currentCoroutineContext()[Job]
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(bytes, 0, bytes.size)) { decoder, info, _ ->
                val size = SpreadMath.halfSize(info.size.width, info.size.height, slotWidth, slotHeight, capBytes)
                    ?: throw IOException("크기를 모른다")
                decoder.setTargetSize(size[0], size[1])
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                if (job?.isActive == false) throw CancellationException("이미지 요청이 취소됐다")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
