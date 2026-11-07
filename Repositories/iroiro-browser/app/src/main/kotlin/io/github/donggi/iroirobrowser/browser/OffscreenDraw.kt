package io.github.donggi.iroirobrowser.browser

import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import io.github.donggi.iroirobrowser.BuildConfig

/**
 * 화면에 그려지지 않는 뷰를 화면 밖 표면에 그린다 — 뒤에서 재생을 잇는 탭의 WebView([PageWebView.drawOffscreen], [BackgroundPlay]).
 *
 * 그 WebView 는 크로미움에 '보임' 이라(숨기면 크로미움이 영상을 멈춘다 — 소리가 있어도) 영상 프레임을 계속 내는데, 창이 숨었거나(앱이 뒤·화면
 * 꺼짐) 쪽의 자리에 투명하게 남은 동안은 아무도 그것을 그리지 않는다. 그리지 않은 프레임은 돌아오지 않아 디코더의 출력이 막히고, 영상 파이프라인이
 * 서면 소리도 함께 선다(`DECODER_UNDERFLOW`). 끊긴 뒤 유튜브는 가장 낮은 화질(144p)로 재생기를 다시 세운다. 하드웨어 캔버스([android.view.Surface.lockHardwareCanvas])에 [View.draw] 로 그리면 WebView 의 그리기
 * (draw functor)가 렌더 스레드에서 돌아 프레임이 소비된다 — 화면에 보이는 것과 같다.
 *
 * **뷰가 다시 그리기를 청할 때만 그린다**([invalidated]). 화면에 보일 때와 같은 횟수다: 크로미움은 새 그림(영상 프레임 포함)이
 * 생길 때마다 WebView 에 다시 그리기를 청하고(30 fps 영상이면 초당 30번), 시스템은 그 청이 있을 때만 그린다. vsync 마다(초당 60번 —
 * 화면이 꺼져도) 그리면 뒤에서의 CPU 가 앞에서 볼 때보다 크다. 청을 [FALLBACK_MS] 넘게 받지 못하면 vsync 마다 그린다 — 청을 받는 길을
 * 놓쳤을 때의 물러서기다(영상이 멈추는 것보다 낫다). 그리지 못한 영상 프레임은 버린 프레임으로
 * 세어지고 유튜브가 그것을 보고 화질을 낮추므로(30일 남는다) 고정된 낮은 빈도로 그리지 않는다.
 *
 * **영상을 숨긴 동안은 물러서지 않는다**([requestsOnly] — [PageBridge.hideVideos]). 숨은 영상은 크로미움이 화면에 내지 않고 스스로
 * 흘려보내므로 그리지 않아도 재생이 서지 않고, 쪽이 그대로면 WebView 는 다시 그리기를 청하지 않는다 — 그때 물러서면 청이 없다는 까닭으로 오히려
 * vsync 마다 그린다. 숨기기가 듣지 않았으면(영상이 보이면) 청이 그대로 오므로 청할 때 그린다.
 *
 * 그림은 버린다([ImageReader] 를 비우기만 한다). 메인 스레드에서만 쓴다([invalidated] 는 다른 스레드에서 불려도 된다).
 */
internal class OffscreenDraw(private val view: View) : Choreographer.FrameCallback {

    private val main = Handler(Looper.getMainLooper())
    private var reader: ImageReader? = null
    private var running = false

    /** 잇달아 실패한 그리기 — [MAX_FAILURES] 에 이르면 멈춘다(실패를 프레임마다 되풀이하지 않게). */
    private var failures = 0

    /** 그린 뒤 뷰가 다시 그리기를 청했는가. */
    @Volatile
    private var requested = false

    /** 마지막으로 청을 받은 때([SystemClock.uptimeMillis]). */
    @Volatile
    private var requestedAt = 0L

    /** 디버그 빌드의 기록 — 그린 수와 그 가운데 청 없이 그린 수를 [STAT_MS] 마다 적는다. */
    private var drawn = 0
    private var fallbackDrawn = 0
    private var statSince = 0L

    val isRunning: Boolean
        get() = running

    /** 청이 있을 때만 그리고 물러서지 않는다 — 쪽의 영상을 숨긴 동안([PageBridge.hideVideos]). [start] 가 내린다. */
    var requestsOnly = false

    /** 뷰가 다시 그리기를 청했다([PageWebView] 의 `invalidate`·`postInvalidateOnAnimation`). */
    fun invalidated() {
        requested = true
        requestedAt = SystemClock.uptimeMillis()
    }

    fun start() {
        if (running) return
        running = true
        failures = 0
        requestsOnly = false
        // 처음 한 번은 청이 없어도 그린다.
        invalidated()
        drawn = 0
        fallbackDrawn = 0
        statSince = SystemClock.uptimeMillis()
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** 멈추고 표면을 놓는다. 뷰가 다시 화면에 그려지도록 새로 그리기를 청한다(화면 밖에서만 그려진 동안의 그림이 남지 않게). */
    fun stop() {
        if (!running && reader == null) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        reader?.close()
        reader = null
        view.invalidate()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val now = SystemClock.uptimeMillis()
        val wasRequested = requested
        if (due(wasRequested, now - requestedAt, requestsOnly)) {
            // 그리는 동안 들어온 청은 남겨 다음 프레임에 그린다 — 그래서 그리기 전에 내린다.
            requested = false
            if (drawOnce()) {
                failures = 0
                drawn++
                if (!wasRequested) fallbackDrawn++
            } else if (++failures >= MAX_FAILURES) {
                stop()
                return
            }
        }
        if (BuildConfig.DEBUG && now - statSince >= STAT_MS) {
            Log.i("WebUI", "offscreen drawn=$drawn fallback=$fallbackDrawn requestsOnly=$requestsOnly in ${now - statSince} ms")
            drawn = 0
            fallbackDrawn = 0
            statSince = now
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun drawOnce(): Boolean {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return false
        val target = reader?.takeIf { it.width == w && it.height == h } ?: newReader(w, h) ?: return false
        val surface = target.surface
        val canvas = try {
            surface.lockHardwareCanvas()
        } catch (e: RuntimeException) {
            return false
        }
        try {
            view.draw(canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        return true
    }

    private fun newReader(w: Int, h: Int): ImageReader? {
        reader?.close()
        reader = null
        return try {
            ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, IMAGES, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE).apply {
                // 그린 것은 쓰지 않는다 — 곧바로 돌려줘야 다음 그리기가 막히지 않는다.
                setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close() }, main)
            }.also { reader = it }
        } catch (e: RuntimeException) {
            null
        }
    }

    companion object {
        private const val IMAGES = 2
        private const val MAX_FAILURES = 120

        /**
         * 청이 이만큼 끊기면 vsync 마다 그린다. 30 fps 영상의 청은 33 ms 마다 오므로 재생 중에는 닿지 않는다 — 버퍼링으로 프레임이
         * 끊긴 동안·영상 트랙이 없는 재생에서만 닿는다.
         */
        const val FALLBACK_MS = 500L

        private const val STAT_MS = 10_000L

        /** 이 프레임에 그리는가 — 청이 있었거나, 청이 [FALLBACK_MS] 넘게 끊겼다(영상을 숨긴 동안([requestsOnly])은 청이 있을 때만). */
        fun due(requested: Boolean, sinceRequestMs: Long, requestsOnly: Boolean = false): Boolean =
            requested || (!requestsOnly && sinceRequestMs >= FALLBACK_MS)
    }
}
