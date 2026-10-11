package io.github.donggi.iroiroviewer.image

import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.donggi.iroiroviewer.io.CropSupport
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.ui.image.ImageIo
import io.github.donggi.iroiroviewer.ui.image.RegionMath
import io.github.donggi.iroiroviewer.ui.image.RegionOrientation
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 잘라 저장의 **뜨는 쪽**(`CropSupport.Renderer`) — 화면이 본 그대로의 한 자리를 원본 해상도로 뜬다. `app` 이 꽂는다.
 *
 * 확대할 때 선명한 조각을 얹는 길(`RasterDetail`)과 **같은 셈**을 쓴다 — 화면 방향의 사각형을 저장 방향으로 옮기고
 * (`RegionMath.toStored`), 영역 디코더(`BitmapRegionDecoder`)로 원본 화소를 떠서 화면 방향으로 돌린다. 화면의 바닥층이 EXIF 방향을
 * 적용했는지는 파일마다 재고(`ImageIo.probe`), 치수로 갈리지 않는 사진(180°·거울상·정사각)은 화소로 잰다(`RegionOrientation`).
 *
 * **그 길을 못 쓰면 바닥층과 같은 디코더로 통째로 떠서 자른다** — 영역 디코더가 없는 형식(GIF·BMP)이거나, 방향을 가를 수 없는
 * 사진이다. 화면이 본 것과 같다는 것이 언제나 먼저다. 대신 큰 그림은 그 길에서 더 줄어들 수 있고, 줄였으면 결과가 그렇게 말한다.
 *
 * **그리기·메모** — 요청에 그릴 것([CropSupport.Ask.overlay])이 실려 오면 뜬 그림(그림 전체) 위에 그것을 그려서 준다.
 * 그리는 코드는 화면과 같은 [MarkupPainter] 이고 배율만 다르다 — 뜬 그림의 폭을 주면 거기 맞춰 그린다. 원본이 커서 줄여 떴으면 획도
 * 같은 비율로 줄어 든다(자리·굵기가 그림에 대해 같은 몫이다).
 */
object ImageCropRenderer : CropSupport.Renderer {

    override suspend fun render(ask: CropSupport.Ask): CropSupport.Rendered {
        val rendered = renderPiece(ask)
        val overlay = ask.overlay ?: return rendered
        val ok = rendered as? CropSupport.Rendered.Ok ?: return rendered
        return withOverlay(ok, overlay)
    }

    private suspend fun renderPiece(ask: CropSupport.Ask): CropSupport.Rendered {
        val probe = ImageIo.probe(ask.path) ?: return CropSupport.Rendered.Unreadable
        // 움직이는 그림은 자르지 않는다 — 첫 장면만 남는 것을 '잘라 낸 그림' 이라 부를 수 없다(화면도 메뉴를 끈다).
        if (probe.isAnimated) return CropSupport.Rendered.Unsupported
        if (probe.displayWidth != ask.expectedWidth || probe.displayHeight != ask.expectedHeight) {
            return CropSupport.Rendered.Changed
        }
        val dw = probe.displayWidth
        val dh = probe.displayHeight
        val l = ask.left.coerceIn(0, dw - 1)
        val t = ask.top.coerceIn(0, dh - 1)
        val r = ask.right.coerceIn(l + 1, dw)
        val b = ask.bottom.coerceIn(t + 1, dh)
        val sample = sampleFor((r - l).toLong() * (b - t), ask.maxPixels)

        val orientation = RegionOrientation(ask.path, probe).tileOrientation()
        if (orientation != null) {
            val stored = RegionMath.toStored(orientation, probe.storedWidth, probe.storedHeight, intArrayOf(l, t, r, b))
            ImageIo.decodeRegionOriented(ask.path, stored, sample, orientation)?.let {
                Iro.d { "잘라 저장: 영역 디코딩 ${it.width}x${it.height} · 표본 $sample · 방향 $orientation" }
                return CropSupport.Rendered.Ok(it, probe.mimeType, downscaled = sample > 1)
            }
        }

        // 영역 디코딩을 못 쓴다 — 화면과 같은 디코더로 통째로 떠서 자른다.
        val wholeSample = sampleFor(dw.toLong() * dh, ask.maxPixels)
        val whole = ImageIo.decodeSampled(ask.path, wholeSample) ?: return CropSupport.Rendered.Unreadable
        return try {
            // 뜬 그림의 크기로 사각형을 옮긴다. 표본으로 나누지 않고 비율로 옮긴다 — 디코더가 나머지를 올리는지 내리는지 모른다.
            // 왼·위는 내리고 오른·아래는 올린다 — 둘 다 내리면 고른 자리의 마지막 줄·칸이 잘려 나갔다.
            val sx = whole.width.toDouble() / dw
            val sy = whole.height.toDouble() / dh
            val cl = floor(l * sx).toInt().coerceIn(0, whole.width - 1)
            val ct = floor(t * sy).toInt().coerceIn(0, whole.height - 1)
            val cr = ceil(r * sx).toInt().coerceIn(cl + 1, whole.width)
            val cb = ceil(b * sy).toInt().coerceIn(ct + 1, whole.height)
            val piece = Bitmap.createBitmap(whole, cl, ct, cr - cl, cb - ct)
            // 같은 크기면 플랫폼이 원본을 그대로 돌려준다 — 그때는 놓지 않는다.
            if (piece !== whole) whole.recycle()
            Iro.d { "잘라 저장: 통째로 떠 자름 ${piece.width}x${piece.height} · 표본 $wholeSample" }
            CropSupport.Rendered.Ok(piece, probe.mimeType, downscaled = wholeSample > 1)
        } catch (e: RuntimeException) {
            whole.recycle()
            CropSupport.Rendered.Unreadable
        } catch (e: OutOfMemoryError) {
            whole.recycle()
            CropSupport.Rendered.Unreadable
        }
    }

    /**
     * 뜬 그림 위에 [overlay] 를 그린다. 캔버스는 고칠 수 있는 비트맵에만 그릴 수 있다 — 디코더가 고칠 수 없게 준 것(바닥층과 같은
     * 디코더로 통째로 뜬 길)은 사본을 떠서 그린다(잠깐 두 장이 든다 — 그 길은 영역 디코더가 없는 형식이나 방향을 가를 수 없는 사진뿐이다).
     */
    private fun withOverlay(ok: CropSupport.Rendered.Ok, overlay: CropSupport.Overlay): CropSupport.Rendered {
        var bitmap = ok.bitmap
        return try {
            if (!bitmap.isMutable) {
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                bitmap.recycle()
                bitmap = copy ?: return CropSupport.Rendered.Unreadable
            }
            overlay.draw(Canvas(bitmap), bitmap.width, bitmap.height)
            Iro.d { "그리기 저장: ${bitmap.width}x${bitmap.height} 위에 그렸다" }
            CropSupport.Rendered.Ok(bitmap, ok.sourceMime, ok.downscaled)
        } catch (e: RuntimeException) {
            bitmap.recycle()
            CropSupport.Rendered.Unreadable
        } catch (e: OutOfMemoryError) {
            bitmap.recycle()
            CropSupport.Rendered.Unreadable
        }
    }

    /** [pixels] 를 [max] 화소 안에 넣는 가장 작은 2의 거듭제곱 표본. */
    internal fun sampleFor(pixels: Long, max: Long): Int {
        var s = 1
        while (s < MAX_SAMPLE && pixels / (s.toLong() * s) > max) s *= 2
        return s
    }

    private const val MAX_SAMPLE = 64
}
