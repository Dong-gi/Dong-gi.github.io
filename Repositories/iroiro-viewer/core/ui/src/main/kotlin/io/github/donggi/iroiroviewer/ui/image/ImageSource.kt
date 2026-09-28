package io.github.donggi.iroiroviewer.ui.image

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.ImageLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 이미지 파일 하나를 여는 계약.
 *
 * ## 왜 파일 디스크립터를 '인스턴스' 가 아니라 '공장' 으로 넘기는가
 *
 * `ImageDecoder.createSource` 의 오버로드 여덟 개를 실제로 확인했는데
 * **`ParcelFileDescriptor` 를 받는 것이 없다.** 있는 것은
 * `createSource(Callable<AssetFileDescriptor>)` 하나이고, `decodeBitmap` 이
 * try-with-resources 로 감싸 **디코딩 한 번에 그 FD 를 닫는다.**
 *
 * 그래서 한 번 연 FD 를 들고 다니다 두 번째 디코딩에서 "닫힌 디스크립터" 를 만나게 된다.
 * 계약을 **부를 때마다 새로 여는 공장**으로 두면 그 함정이 구조적으로 사라진다.
 */
fun interface FdFactory {
    /** 부를 때마다 **새** 디스크립터를 연다. 받는 쪽이 닫는다. */
    fun open(): AssetFileDescriptor
}

/** 파일 경로에서 만드는 공장. */
fun fdFactoryOf(path: String): FdFactory = FdFactory {
    AssetFileDescriptor(
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY),
        0,
        AssetFileDescriptor.UNKNOWN_LENGTH,
    )
}

/**
 * 파일을 열기 전에 알아야 하는 것.
 *
 * [decoderAppliesOrientation] 이 이 구조체의 핵심이다 — 아래 [probe] 주석 참고.
 */
data class ImageProbe(
    /** 파일에 저장된 그대로의 화소. EXIF 회전을 적용하지 않은 값이다. */
    val storedWidth: Int,
    val storedHeight: Int,
    /** 화면에 보여야 하는 화소. 90·270 회전이면 저장값과 가로세로가 바뀐다. */
    val displayWidth: Int,
    val displayHeight: Int,
    val mimeType: String?,
    /** EXIF 방향 태그 값. 1 이 정방향. */
    val orientation: Int,
    /**
     * **플랫폼 디코더가 방향을 스스로 적용하는가.**
     *
     * `null` 이면 잴 수 없었다는 뜻이다(아래 참고). 이 값이 필요한 이유는 층이 둘이기
     * 때문이다 — `ImageDecoder`(바닥층)는 적용하고 `BitmapRegionDecoder`(상세층)는
     * 적용하지 않는다. 모르는 채 섞으면 확대하는 순간 그림이 90° 돌아간다.
     */
    val decoderAppliesOrientation: Boolean?,
    /**
     * 플랫폼이 **움직이는 그림으로 여는가**(`ImageDecoder.ImageInfo.isAnimated`). 헤더를 읽는 김에
     * 함께 받는다 — 따로 훑을 필요가 없고, '틀 수 있는가' 를 플랫폼 자신에게 묻는 것이라 표를 만들지
     * 않는다. 한 장짜리 GIF 는 거짓이고, APNG 도 거짓이다(플랫폼이 첫 장면만 준다).
     */
    val isAnimated: Boolean = false,
) {
    val isRotated90: Boolean
        get() = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

    /**
     * 영역 디코딩(상세층)을 **곧바로** 써도 되는가 — 바닥층이 방향을 적용했는지 치수로 이미 안다.
     *
     * 여덟 방향 모두 좌표를 옮길 수 있다(`RegionMath`). 모르는 것은 '바닥층이 적용했는가' 하나이고,
     * 그것이 거짓이 되는 파일(180°·거울상·정사각)은 [needsPixelCheck] 로 화소를 재 본 뒤에야 조각을
     * 뜬다(`ImageIo.appliedOrientationByPixels`). 6단계는 그 파일들을 흐린 채로 두었다.
     */
    val canUseRegionDecoder: Boolean
        get() = decoderAppliesOrientation != null

    /** 치수로 답이 나지 않아 **화소로 재 봐야** 방향을 아는가. */
    val needsPixelCheck: Boolean
        get() = decoderAppliesOrientation == null
}

object ImageIo {

    /**
     * 파일을 재 본다. **표를 만들지 않고 파일마다 직접 잰다.**
     *
     * 어느 포맷에서 디코더가 EXIF 회전을 적용하는지를 정적인 표로 적으면 언젠가 틀린다 —
     * AVIF 는 시스템 속성 하나로 디코더 구현이 통째로 바뀌고, 기기·안드로이드 판마다
     * 다르다. 대신 **세 값을 읽어 스스로 답하게** 한다.
     *
     * - 저장 치수: `BitmapFactory(inJustDecodeBounds)` — 방향을 적용하지 않는다.
     * - 표시 치수: `ImageDecoder` 의 헤더 — 적용했다면 90·270 에서 가로세로가 바뀌어 온다.
     * - EXIF 방향 태그.
     *
     * 둘을 견주면 "이 디코더가 이 파일의 방향을 적용했는가" 가 나온다.
     * **잴 수 없는 경우도 정직하게 적는다** — 180°·거울상·정사각은 치수가 안 바뀌어
     * 판별되지 않고, 그때 [ImageProbe.decoderAppliesOrientation] 은 `null` 이다. 그 파일은
     * 확대해서 조각이 필요해지는 순간 [appliedOrientationByPixels] 가 화소로 잰다 — 여기서 미리
     * 재지 않는 것은 확대하지 않을 사진에까지 디코딩을 한 번 더 치르지 않으려는 것이다.
     */
    suspend fun probe(path: String): ImageProbe? = withContext(IroDispatchers.image) {
        val file = File(path)
        if (!file.isFile) return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val storedW = bounds.outWidth
        val storedH = bounds.outHeight

        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        var displayW = storedW
        var displayH = storedH
        var mime = bounds.outMimeType
        var animated = false
        runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { _, info, _ ->
                displayW = info.size.width
                displayH = info.size.height
                mime = info.mimeType
                animated = info.isAnimated
                // 헤더만 보려는 것이므로 가장 작게 뜨고 버린다.
                throw HeaderOnly()
            }
        }

        if (storedW <= 0 || storedH <= 0) return@withContext null

        val wantsSwap = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val swapped = displayW == storedH && displayH == storedW && storedW != storedH
        val applied: Boolean? = when {
            // **정방향은 잴 것이 없다** — 어느 디코더도 돌리지 않는다. 정사각 검사보다 먼저 둔다:
            // 뒤에 두면 정사각 정방향 사진이 '잴 수 없다' 가 되어 선명화 조각이 한 번도 오지 않는다
            // (적대적 검토가 잡았다).
            orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED -> true
            // 치수가 안 바뀌는 방향(3·거울상)이나 정사각이면 잴 수 없다.
            !wantsSwap -> null
            storedW == storedH -> null
            wantsSwap -> swapped
            else -> true
        }

        ImageProbe(
            storedWidth = storedW,
            storedHeight = storedH,
            displayWidth = if (wantsSwap && applied != false) storedH else storedW,
            displayHeight = if (wantsSwap && applied != false) storedW else storedH,
            mimeType = mime,
            orientation = orientation,
            decoderAppliesOrientation = applied,
            isAnimated = animated,
        )
    }

    /** 헤더만 읽고 빠져나오는 신호. 디코딩 비용을 치르지 않으려는 것이다. */
    private class HeaderOnly : RuntimeException(null, null, false, false)

    /**
     * 한 장을 **화면에 맞는 크기로** 연다.
     *
     * 표본을 헤더 단계에서 정하므로 12MP 사진을 통째로 메모리에 올리지 않는다.
     * 취소는 헤더 콜백에서만 받는다 — 그 뒤는 네이티브라 우리가 끼어들 수 없다.
     */
    suspend fun decodeFitted(path: String, targetLongest: Int, capBytes: Long = 0L): Bitmap? =
        withContext(IroDispatchers.image) {
            val file = File(path)
            if (!file.isFile) return@withContext null
            decode(
                ImageDecoder.createSource(file),
                targetLongest,
                capBytes,
                currentCoroutineContext()[Job],
            )
        }

    /**
     * **메모리 위의 바이트**를 화면에 맞는 크기로 연다. 만화 뷰어가 쓴다.
     *
     * ## 왜 파일로 뽑지 않는가
     *
     * 아카이브 안의 쪽에는 경로가 없다. 캐시 폴더에 뽑아 경로를 만드는 길도 있지만,
     * 썸네일 캐시를 `filesDir` 에 둔 그 논리가 여기서는 **반대 방향으로** 작용한다 —
     * 썸네일은 320px 짜리 파생물이지만 만화 쪽은 **원본 그대로**다. 200쪽짜리 열 권을
     * 보면 사용자의 만화가 통째로 앱 저장소에 평문으로 복제된다. 그래서 쪽은 힙에만 둔다.
     *
     * `ImageDecoder.createSource(byte[], int, int)` 는 **API 31 부터**이고 이 앱의
     * minSdk 가 31 이라 그대로 쓸 수 있다(`api-versions.xml` 에서 `since="31"` 확인).
     * `InputStream` 을 받는 오버로드는 **없다** — 있는 줄 알고 설계하면 막힌다.
     */
    suspend fun decodeFitted(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
        targetLongest: Int,
        capBytes: Long = 0L,
    ): Bitmap? = withContext(IroDispatchers.image) {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return@withContext null
        decode(
            ImageDecoder.createSource(bytes, offset, length),
            targetLongest,
            capBytes,
            currentCoroutineContext()[Job],
        )
    }

    /**
     * 두 오버로드가 **같은 헤더 콜백**을 쓰게 모은 곳.
     *
     * 표본 크기·할당자·취소 규칙이 갈리면 아카이브에서 연 쪽과 파일에서 연 쪽이 다른
     * 메모리를 쓰게 되고, 그 차이는 기기에서만 드러난다.
     *
     * @param capBytes 결과 한 장의 바이트 상한. 0 이면 걸지 않는다(사진 뷰어). 만화는
     *   이 값을 준다 — 표본이 2의 거듭제곱이라 목표 해상도만 보면 한 장이 예산의 네 배가
     *   되고, 만화는 그런 장을 네 장 동시에 든다([ImageLimits.sampleForBudget] 주석).
     */
    private fun decode(
        source: ImageDecoder.Source,
        targetLongest: Int,
        capBytes: Long,
        job: Job?,
    ): Bitmap? = try {
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sample = ImageLimits.sampleForBudget(
                info.size.width, info.size.height, targetLongest, capBytes,
            )
            decoder.setTargetSampleSize(sample)
            // 소프트웨어로 뜬다. 확대했을 때 픽셀을 다시 읽어야 하고, 하드웨어
            // 비트맵은 읽을 수 없다. 그리고 소프트웨어 캔버스 합성도 막힌다.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            // **취소를 받을 수 있는 유일한 자리다.** 이 콜백을 지나면 네이티브 디코딩이라
            // 우리가 끼어들 수 없다. 만화에서 쪽을 빨리 넘기면 여기서 끊긴다.
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

    /**
     * 디코딩하지 않고 **치수만** 잰다. 만화가 쪽마다 부른다.
     *
     * 세로(웹툰) 모드는 쪽을 통짜로 들지 띠로 자를지를 그리기 전에 정해야 하고,
     * 애니메이션은 틀어도 되는지를 프레임 버퍼가 잡히기 전에 정해야 한다. 둘 다
     * 답이 치수에 달려 있는데, 알아내려고 한 번 디코딩하면 막으려던 그 메모리를
     * 이미 쓴 뒤다.
     *
     * **[ImageDecoder] 가 아니라 [BitmapFactory] 를 쓴다.** `inJustDecodeBounds` 는
     * 픽셀을 한 바이트도 올리지 않는 것이 명시된 계약이고, `ImageDecoder` 에서 같은
     * 것을 하려면 헤더 콜백에서 예외를 던져 빠져나와야 한다(위 [probe] 가 그 방식이다).
     * 쪽마다 부르는 자리에서 예외로 흐름을 끊는 것은 값이 싸지 않다.
     */
    fun bounds(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): IntArray? {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, offset, length, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return intArrayOf(opts.outWidth, opts.outHeight)
    }

    /**
     * 원본의 **한 자리**를 뜬다. 확대했을 때 바닥층 위에 얹는 선명한 조각이다.
     *
     * [rect] 는 **저장 방향**의 원본 화소 `[왼, 위, 오른, 아래]` 이고(`RegionMath.toStored`
     * 로 옮긴 것), 잘라 온 조각을 [orientation] 대로 돌리고 뒤집어 화면 방향으로 준다.
     * `BitmapRegionDecoder` 가 EXIF 방향을 적용하지 않기 때문이다. 바닥층이 방향을 적용하지 않은
     * 파일이면 부르는 쪽이 [RegionMath.NORMAL] 을 준다 — 조각도 바닥층처럼 저장 방향 그대로여야 한다.
     *
     * 여덟 방향을 전부 받는다. 예전 판(`decodeRegion(path, …, rotation)`)은 돌리기만 했고 90°·270°
     * 에서만 불렸다.
     *
     * 형식이 영역 디코딩을 지원하지 않으면(GIF 등) null 이다 — 그때는 흐린 바닥층이 남는다.
     */
    suspend fun decodeRegionOriented(path: String, rect: IntArray, sample: Int, orientation: Int): Bitmap? =
        withContext(IroDispatchers.image) {
            if (!File(path).isFile) return@withContext null
            region(
                { android.graphics.BitmapRegionDecoder.newInstance(path) },
                rect, sample, RegionMath.rotationDegrees(orientation), RegionMath.mirrorOf(orientation),
            )
        }

    /**
     * 원본의 한 자리를 뜬다 — **바이트 판.** 만화 쪽이 쓴다(아카이브 안의 쪽에는 경로가 없다).
     * [rotation] 도(시계 방향) 돌리기만 한다.
     */
    suspend fun decodeRegion(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
        rect: IntArray,
        sample: Int,
        rotation: Int,
    ): Bitmap? = withContext(IroDispatchers.image) {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return@withContext null
        region(
            { android.graphics.BitmapRegionDecoder.newInstance(bytes, offset, length) },
            rect, sample, rotation, RegionMath.Mirror.NONE,
        )
    }

    /**
     * 바닥층의 디코더가 이 파일의 방향을 **적용하는가를 화소로** 잰다. 치수로 답이 나지 않는 파일
     * ([ImageProbe.needsPixelCheck] — 180°·거울상·정사각)만 실제로 잰다. 판정 자체는 [OrientationMatch].
     *
     * ## 무엇과 무엇을 견주는가 — 같은 표본의 작은 그림 둘
     *
     * 하나는 **바닥층과 같은 디코더**(`ImageDecoder`)로, 하나는 **조각과 같은 디코더**
     * (`BitmapRegionDecoder`)로 원본 전체를 같은 표본으로 뜬다. 같은 형식의 코덱이 같은 표본으로 줄이므로
     * 두 그림의 차이는 **방향**이 거의 전부다(남는 잡음은 [OrientationMatch.MAX_MATCH] 가 받아 준다 —
     * 두 디코더의 줄이는 방식이 화소 단위로 같다는 것은 확인하지 않았다). 방향을 적용하는가는 파일과
     * 디코더가 정하는 것이지 표본이 정하는 것이 아니라, 작게 뜬 그림의 답이 화면의 바닥층에도 그대로 맞는다.
     *
     * 화면에 떠 있는 바닥층을 줄여 쓰지 않는 까닭: 바닥층은 수천 화소라 수십 화소로 한 번에 줄이면
     * 이중선형 보간이 화소를 건너뛰어(에일리어싱) 잎사귀 같은 자리에서 차이가 수십으로 뛴다 — 맞는
     * 가설도 '닮지 않았다' 가 되어 판정을 포기한다. 작은 그림 둘은 합쳐 수십 KB 이고 **확대해서 조각이
     * 처음 필요해질 때 한 번만** 돈다([RegionOrientation]).
     *
     * @return 적용한다 true, 안 한다 false, 가를 수 없거나 뜨지 못했다 null — null 이면 조각을 뜨지
     *   않는다(흐린 채로 확대된다). 틀린 조각을 얹는 것보다 흐린 편이 낫다.
     */
    suspend fun appliedOrientationByPixels(path: String, probe: ImageProbe): Boolean? =
        withContext(IroDispatchers.image) {
            if (!probe.needsPixelCheck) return@withContext probe.decoderAppliesOrientation
            val file = File(path)
            if (!file.isFile) return@withContext null
            val w = probe.storedWidth
            val h = probe.storedHeight
            val (tw, th) = OrientationMatch.thumbSize(w, h)
            val bw = tw * OrientationMatch.BOX
            val bh = th * OrientationMatch.BOX
            val sample = ImageLimits.sampleFor(w, h, maxOf(bw, bh))
            val raw = region(
                { android.graphics.BitmapRegionDecoder.newInstance(path) },
                intArrayOf(0, 0, w, h), sample, 0, RegionMath.Mirror.NONE,
            ) ?: return@withContext null
            try {
                val shown = decodeAtSample(file, sample, currentCoroutineContext()[Job])
                    ?: return@withContext null
                try {
                    val rawPx = pixelsAt(raw, bw, bh) ?: return@withContext null
                    val shownPx = pixelsAt(shown, bw, bh) ?: return@withContext null
                    OrientationMatch.decide(
                        probe.orientation,
                        OrientationMatch.boxDownsample(shownPx, bw, bh, OrientationMatch.BOX),
                        OrientationMatch.boxDownsample(rawPx, bw, bh, OrientationMatch.BOX),
                        tw, th,
                    )
                } finally {
                    shown.recycle()
                }
            } finally {
                // 둘 다 우리가 방금 뜬, 아무도 그리지 않은 비트맵이다(축출된 비트맵과 다르다).
                raw.recycle()
            }
        }

    /**
     * 바닥층과 **같은 디코더**로, 표본만 [sample] 로 못 박아 뜬다. [decode] 는 목표 크기에서 표본을
     * 스스로 고르므로 조각 쪽과 표본이 어긋날 수 있다 — 여기서는 둘이 같아야 한다.
     */
    private fun decodeAtSample(file: File, sample: Int, job: Job?): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
            decoder.setTargetSampleSize(sample.coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            if (job?.isActive == false) throw CancellationException("방향 판정이 취소됐다")
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

    /**
     * [src] 를 [w]×[h] 로 줄여 화소를 읽는다. 줄인 사본은 읽고 곧바로 버린다 — [src] 는 건드리지
     * 않는다.
     */
    private fun pixelsAt(src: Bitmap, w: Int, h: Int): IntArray? = try {
        val scaled = src.scale(w, h, filter = true)
        try {
            IntArray(w * h).also { scaled.getPixels(it, 0, w, 0, 0, w, h) }
        } finally {
            if (scaled !== src) scaled.recycle()
        }
    } catch (e: RuntimeException) {
        // 하드웨어 비트맵이면 화소를 읽을 수 없다(`IllegalStateException`). 바닥층은 소프트웨어로
        // 뜨므로 올 일이 없지만, 오면 판정을 포기할 뿐이다.
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private suspend fun region(
        open: () -> android.graphics.BitmapRegionDecoder?,
        rect: IntArray,
        sample: Int,
        rotation: Int,
        mirror: RegionMath.Mirror,
    ): Bitmap? {
        // 네이티브 디코딩이 시작되면 끼어들 수 없다. 여는 직전이 취소를 볼 마지막 자리다.
        if (currentCoroutineContext()[Job]?.isActive == false) return null
        var decoder: android.graphics.BitmapRegionDecoder? = null
        return try {
            val d = open() ?: return null
            decoder = d
            val l = rect[0].coerceIn(0, d.width - 1)
            val t = rect[1].coerceIn(0, d.height - 1)
            val r = rect[2].coerceIn(l + 1, d.width)
            val b = rect[3].coerceIn(t + 1, d.height)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val piece = d.decodeRegion(android.graphics.Rect(l, t, r, b), opts) ?: return null
            if (rotation % 360 == 0 && mirror == RegionMath.Mirror.NONE) {
                piece
            } else {
                // **돌린 다음에 뒤집는다**(`RegionMath.rotationDegrees` 의 약속). 음수 배율로
                // 뒤집으면 결과가 원점 밖으로 나가지만 `createBitmap` 이 사상된 경계를 원점으로
                // 당겨 준다 — 그 사상이 `RegionMath.displayOf` 와 같다는 것은 JVM 시험이 확인한다.
                val m = android.graphics.Matrix().apply {
                    postRotate(rotation.toFloat())
                    when (mirror) {
                        RegionMath.Mirror.HORIZONTAL -> postScale(-1f, 1f)
                        RegionMath.Mirror.VERTICAL -> postScale(1f, -1f)
                        RegionMath.Mirror.NONE -> Unit
                    }
                }
                val turned = Bitmap.createBitmap(piece, 0, 0, piece.width, piece.height, m, true)
                // 아직 아무도 그리지 않은 조각이라 곧바로 돌려줘도 안전하다(축출된 비트맵과 다르다).
                if (turned !== piece) piece.recycle()
                turned
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            // **반드시 반납한다**(함정 표). 네이티브 쪽에 디코더 상태가 통째로 살아 있다.
            decoder?.recycle()
        }
    }

    /**
     * 메모리 위 그림의 EXIF 방향. 없거나 읽지 못하면 [ExifInterface.ORIENTATION_NORMAL].
     *
     * 영역 디코딩(`BitmapRegionDecoder`)은 방향을 적용하지 않고 바닥층(`ImageDecoder`)은
     * 적용한다. 방향 태그가 붙은 쪽에 조각을 얹으면 누운 조각이 얹히므로, 호출자가 먼저
     * 이 값을 보고 정방향일 때만 영역 디코딩을 쓴다.
     */
    fun exifOrientation(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return ExifInterface.ORIENTATION_NORMAL
        return try {
            ExifInterface(java.io.ByteArrayInputStream(bytes, offset, length)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (e: RuntimeException) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * 긴 쪽(웹툰)의 **띠 하나**를 뜬다.
     *
     * ## 왜 여기서만 영역 디코딩을 쓰는가
     *
     * 6단계가 [android.graphics.BitmapRegionDecoder] 를 미룬 이유는 그것이 EXIF 회전을
     * 적용하지 않아 [ImageDecoder] 로 뜬 바닥층과 방향이 어긋날 수 있어서였다. 만화
     * 쪽에서는 그 이유가 약하다 — 스캔·작화 이미지에 EXIF 방향 태그가 붙는 일이 드물고,
     * 무엇보다 **여기에는 겹쳐 그릴 바닥층이 없다.** 띠가 그 자리의 유일한 그림이다.
     *
     * `newInstance(byte[], int, int)` 는 **API 31 부터**다(`api-versions.xml` 확인).
     * 불리언을 받는 옛 오버로드는 31 에서 deprecated 이므로 쓰지 않는다.
     *
     * @param top·[height] 원본 화소 좌표의 띠. 쪽 밖으로 나가면 잘라서 뜬다.
     * @param sample 2의 거듭제곱. 호출자가 [ImageLimits.sampleForBudget] 으로 정한다.
     */
    suspend fun decodeBand(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
        top: Int,
        height: Int,
        sample: Int,
    ): Bitmap? = withContext(IroDispatchers.image) {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return@withContext null
        if (height <= 0) return@withContext null
        var decoder: android.graphics.BitmapRegionDecoder? = null
        try {
            val d = android.graphics.BitmapRegionDecoder.newInstance(bytes, offset, length)
                ?: return@withContext null
            decoder = d
            val y0 = top.coerceIn(0, (d.height - 1).coerceAtLeast(0))
            val y1 = (top + height).coerceIn(y0 + 1, d.height)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            d.decodeRegion(android.graphics.Rect(0, y0, d.width, y1), opts)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            // **반드시 반납한다.** 네이티브 쪽에 디코더 상태가 통째로 살아 있어서,
            // 띠마다 하나씩 새면 웹툰 한 회를 내리는 동안 수십 개가 쌓인다.
            decoder?.recycle()
        }
    }
}
