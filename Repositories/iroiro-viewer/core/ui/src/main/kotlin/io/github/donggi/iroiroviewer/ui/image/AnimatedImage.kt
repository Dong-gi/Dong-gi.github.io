package io.github.donggi.iroiroviewer.ui.image

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.AnimationLimits
import io.github.donggi.iroiroviewer.safety.ImageLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/**
 * 움직이는 그림 하나. 6단계가 9단계로 미뤄 둔 것이다.
 *
 * ## 왜 콜백을 **필드로** 들어야 하는가
 *
 * `Drawable.setCallback` 은 콜백을 **약한 참조**로 든다. 익명 객체를 그 자리에서 만들어
 * 넘기면 다른 참조가 없어 곧 회수되고, 그 뒤로는 `invalidateDrawable` 이 오지 않아
 * **그림이 첫 장면에서 조용히 멈춘다.** 오류도 로그도 없다 — 6단계가 이것 때문에
 * 애니메이션을 미뤘다.
 *
 * 그래서 콜백은 이 페인터의 필드이고, 페인터의 수명은 [RememberObserver] 로
 * 컴포지션에 묶는다. 컴포지션을 벗어나면 애니메이션을 멈추고 콜백을 뗀다 — 화면 밖
 * 페이지가 계속 프레임을 그리면 만화를 빠르게 넘길 때 디코더 여럿이 동시에 돈다.
 *
 * **컴포지션 안에 있어도 멈춰 둘 수 있다**([playing]). 페이저는 앞뒤 한 장씩을 화면 밖에서도
 * 구성해 두는데, GIF 가 모인 폴더에서 그 셋이 전부 돌면 보이지도 않는 그림이 프레임을 푼다.
 * 이미지 뷰어는 자리 잡은 장만 튼다(14단계). 만화 뷰어는 기본값(언제나 튼다) 그대로다.
 *
 * @param initialPlaying 처음 붙을 때 틀 것인가. 첫 프레임에 한 번 돌았다 멈추는 깜빡임을 막으려고
 *   생성자에서 받는다.
 */
class AnimatedImagePainter(
    private val drawable: Drawable,
    initialPlaying: Boolean = true,
) : Painter(), RememberObserver {

    /** 컴포지션에 붙어 있는가. 붙기 전에 [playing] 을 바꿔도 그리기를 시작하지 않는다. */
    private var attached = false

    /** 지금 틀 것인가. 거짓이면 보이던 장면에서 멈춘다(`AnimatedImageDrawable.stop`). */
    var playing: Boolean = initialPlaying
        set(value) {
            if (field == value) return
            field = value
            if (attached) applyPlaying()
        }

    /** 이 값이 바뀌면 Compose 가 다시 그린다. `invalidateDrawable` 이 여기로 온다. */
    private var tick by mutableIntStateOf(0)

    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            tick++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, who, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler.removeCallbacks(what, who)
        }
    }

    override val intrinsicSize: Size
        get() = Size(
            drawable.intrinsicWidth.coerceAtLeast(1).toFloat(),
            drawable.intrinsicHeight.coerceAtLeast(1).toFloat(),
        )

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            // **읽어야 구독된다.** 이 한 줄이 없으면 tick 이 올라도 이 그리기가 다시
            // 불리지 않는다 — Compose 의 스냅샷 구독은 '읽은 것' 을 기준으로 한다.
            @Suppress("UNUSED_EXPRESSION")
            tick
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    override fun onRemembered() {
        attached = true
        drawable.callback = callback
        if (drawable is AnimatedImageDrawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
        applyPlaying()
    }

    override fun onForgotten() = release()

    override fun onAbandoned() = release()

    private fun applyPlaying() {
        if (drawable !is AnimatedImageDrawable) return
        if (playing) drawable.start() else drawable.stop()
    }

    private fun release() {
        attached = false
        if (drawable is AnimatedImageDrawable) drawable.stop()
        drawable.callback = null
    }
}

/**
 * 컴포지션에 묶인 페인터. [drawable] 이 바뀌면 새로 만든다.
 *
 * @param playing 지금 틀 것인가. 바뀌면 그 자리에서 멈추거나 다시 튼다 — 페인터를 새로 만들지 않는다.
 */
@Composable
fun rememberAnimatedPainter(drawable: Drawable, playing: Boolean = true): AnimatedImagePainter {
    val painter = remember(drawable) { AnimatedImagePainter(drawable, initialPlaying = playing) }
    // 컴포지션이 확정된 뒤에 바꾼다. 구성 도중에 바꾸면 버려질 구성이 드로어블을 건드린다.
    SideEffect { painter.playing = playing }
    return painter
}

/**
 * 움직이는 그림을 **메모리 위의 바이트**에서 연다.
 *
 * 돌려주는 것이 [AnimatedImageDrawable] 이 아닐 수 있다 — 한 장짜리 GIF, 정지 WebP,
 * 그리고 APNG 이 그렇다. 그때는 그대로 정지 그림으로 그리면 되고, 화면이
 * "움직이지만 첫 장면만" 이라고 말해야 하는지는 [ImageFormats.isApng] 가 따로 답한다.
 *
 * @return 디코딩한 그림. 열 수 없으면 null.
 */
suspend fun decodeAnimated(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
    targetLongest: Int,
    capBytes: Long = 0L,
    minSample: Int = 1,
): Drawable? = withContext(IroDispatchers.image) {
    if (length <= 0 || offset < 0 || offset + length > bytes.size) return@withContext null
    animatedFrom(ImageDecoder.createSource(bytes, offset, length), targetLongest, capBytes, minSample)
}

/**
 * 움직이는 그림을 **파일에서** 연다. 이미지 뷰어가 쓴다(14단계).
 *
 * 바이트 판을 쓰려고 파일을 통째로 읽지 않는다(`File.readBytes()` 금지) — `ImageDecoder` 가 파일을
 * 스스로 연다.
 *
 * @param minSample 표본의 하한. [AnimationPlan.sampleFor] 가 예산에 맞춰 정한 값을 준다 — 목표
 *   해상도만 보고 뜨면 프레임 버퍼 셋이 예산을 넘는다(`AnimationLimits.canAnimate`).
 */
suspend fun decodeAnimated(
    path: String,
    targetLongest: Int,
    capBytes: Long = 0L,
    minSample: Int = 1,
): Drawable? = withContext(IroDispatchers.image) {
    val file = java.io.File(path)
    if (!file.isFile) return@withContext null
    animatedFrom(ImageDecoder.createSource(file), targetLongest, capBytes, minSample)
}

/** 두 입구가 **같은 헤더 콜백**을 쓰게 모은 곳. 표본·할당 규칙이 갈리면 두 뷰어가 다른 메모리를 쓴다. */
private fun animatedFrom(
    source: ImageDecoder.Source,
    targetLongest: Int,
    capBytes: Long,
    minSample: Int,
): Drawable? {
    return try {
        ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            decoder.setTargetSampleSize(
                maxOf(
                    ImageLimits.sampleForBudget(
                        info.size.width, info.size.height, targetLongest, capBytes,
                    ),
                    minSample.coerceAtLeast(1),
                ),
            )
            // **할당자를 지정하지 않는다.** 움직이는 그림은 디코더가 프레임 버퍼를 스스로
            // 관리하고, 거기에 `ALLOCATOR_SOFTWARE` 를 강제하면 우리가 얻는 것 없이
            // 구현의 선택만 좁힌다. 정지 그림의 경로(`ImageIo.decode`)와 다른 이유다 —
            // 그쪽은 확대했을 때 화소를 다시 읽어야 해서 소프트웨어가 필요하다.
            decoder.isMutableRequired = false
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

/**
 * 이 쪽을 **움직이게 틀 것인가.**
 *
 * 포맷이 맞고([ImageFormats.playsAnimated]) 예산이 허락할 때만([AnimationLimits.canAnimate])
 * 참이다. 둘을 한 자리에서 묻게 해 두면 화면이 한쪽만 보고 결정하는 일이 없다.
 */
fun shouldAnimate(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    width: Int,
    height: Int,
    budget: ImageLimits.Budget,
): Boolean = ImageFormats.playsAnimated(bytes, offset, length) &&
    AnimationLimits.canAnimate(width, height, budget)
