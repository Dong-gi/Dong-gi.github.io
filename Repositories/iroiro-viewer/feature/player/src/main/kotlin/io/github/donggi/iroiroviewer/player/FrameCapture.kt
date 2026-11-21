package io.github.donggi.iroiroviewer.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.annotation.StringRes
import io.github.donggi.iroiroviewer.io.FrameShot

/**
 * 영상 화면 캡처 — 지금 표면에 놓인 프레임을 비트맵으로 받는다(`PixelCopy`).
 *
 * **뷰가 아니라 표면에서 받는다** — `PixelCopy.request(SurfaceView, …)` 는 디코더가 표면에 넣은 마지막 버퍼를 받는 크기로 늘이거나
 * 줄여 옮긴다. 그래서 화면의 확대·화면 비율(표면에 건 `graphicsLayer`·상자 크기)과 상관없이 **영상 크기**로 받는다(크기는
 * `FrameShot.sizeOf` — 화소 비율을 펴고 상한 안으로). 자막·조작부는 다른 층이라 담기지 않는다.
 *
 * 쓰는 일은 파일 작업 큐가 한다(`FileOpManager.Request.SaveFrame`).
 */
internal object FrameCapture {

    sealed interface Result {
        class Ok(val bitmap: Bitmap) : Result

        data class Failed(@param:StringRes val why: Int) : Result
    }

    /** [view] 의 지금 프레임을 [size] 로 받아 메인 스레드에서 [onDone] 에 넘긴다. 메인 스레드에서 부른다. */
    fun request(view: SurfaceView, size: FrameShot.Size, onDone: (Result) -> Unit) {
        if (!view.holder.surface.isValid) {
            onDone(Result.Failed(R.string.player_capture_no_frame))
            return
        }
        val bitmap = try {
            Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            onDone(Result.Failed(R.string.player_capture_failed))
            return
        }
        try {
            PixelCopy.request(view, bitmap, { code ->
                if (code == PixelCopy.SUCCESS) {
                    onDone(Result.Ok(bitmap))
                } else {
                    bitmap.recycle()
                    onDone(Result.Failed(reasonOf(code)))
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            // 그 사이 표면이 사라졌다(목록을 펼쳤다 따위).
            bitmap.recycle()
            onDone(Result.Failed(R.string.player_capture_no_frame))
        }
    }

    /**
     * `PixelCopy` 의 실패를 말할 까닭으로. 아직 그려진 프레임이 없으면(첫 프레임 전) 그렇게, 표면을 읽을 수 없으면(보호된 표면 —
     * 이 앱은 DRM 영상을 틀지 않으므로 대개 표면이 막 사라진 것이다) 그렇게, 나머지(시간 초과 따위)는 '캡처하지 못했다' 로.
     */
    @StringRes
    private fun reasonOf(code: Int): Int = when (code) {
        PixelCopy.ERROR_SOURCE_NO_DATA -> R.string.player_capture_no_frame
        PixelCopy.ERROR_SOURCE_INVALID -> R.string.player_capture_unreadable
        else -> R.string.player_capture_failed
    }
}
