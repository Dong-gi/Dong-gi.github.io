package io.github.donggi.iroiroviewer.ui.image

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.ParcelFileDescriptor
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
) {
    val isRotated90: Boolean
        get() = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

    /**
     * 영역 디코딩(상세층)을 써도 되는가.
     *
     * **방향을 확실히 아는 파일에만 쓴다.** 180°·거울상은 가로세로가 바뀌지 않아
     * '디코더가 적용했는지' 를 잴 방법이 없다. 추측해서 돌리느니 그 파일은 통짜 재디코딩
     * 경로로 보낸다 — 그쪽은 바닥층과 같은 디코더를 타므로 방향이 구조적으로 일치한다.
     */
    val canUseRegionDecoder: Boolean
        get() = decoderAppliesOrientation != null &&
            (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED ||
                orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270)
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
     * 판별되지 않고, 그때 [ImageProbe.decoderAppliesOrientation] 은 `null` 이다.
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
        runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { _, info, _ ->
                displayW = info.size.width
                displayH = info.size.height
                mime = info.mimeType
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
            // 치수가 안 바뀌는 방향(1·3·거울상)이나 정사각이면 잴 수 없다.
            !wantsSwap && orientation != ExifInterface.ORIENTATION_NORMAL &&
                orientation != ExifInterface.ORIENTATION_UNDEFINED -> null
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
