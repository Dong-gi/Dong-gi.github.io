package io.github.donggi.iroiroviewer.text

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import kotlin.math.roundToInt

/**
 * **두 손가락으로 글자 크기를 바꾼다**(예전에는 ⋮ 를 열어 한 단계씩만 바꿀 수 있었다). 바꾼
 * 크기는 ⋮ 의 '글자 키우기' 와 같은 설정에 남는다(그 파일의 갈래 — 글·코드 — 에).
 *
 * 손가락 하나의 몸짓(위아래로 밀기·길게 눌러 고르기)은 그대로 아래로 내려간다 — 둘째 손가락이 닿아 있는 동안만 보고, 그동안의 움직임은
 * **먼저(Initial) 먹어** 목록이 함께 밀리거나 고르기가 시작되지 않게 한다. 크기는 둘째 손가락이 닿은 때의 크기 × 벌린 비율을 sp 로
 * 반올림한 것이고 바뀔 때마다 [onFontSp] 를 부른다.
 *
 * @param fontSp 지금 크기 — 몸짓마다 시작할 때 읽는다(이 수식어는 한 번만 서므로 값이 아니라 읽는 함수를 받는다).
 */
internal fun Modifier.pinchFontSize(fontSp: () -> Int, onFontSp: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var base = -1
        var scale = 1f
        var applied = -1
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } < 2) {
                // 하나만 남았다 — 다시 둘이 되면 그때의 크기에서 새로 센다.
                base = -1
                continue
            }
            if (base < 0) {
                base = fontSp()
                scale = 1f
                applied = base
            }
            scale *= event.calculateZoom()
            val target = PinchFont.sizeFor(base, scale)
            if (target != applied) {
                applied = target
                onFontSp(target)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

internal object PinchFont {
    /** 시작 크기 [base] 를 [scale] 배 한 크기 — sp 로 반올림하고 범위([TextViewerDefaults.MIN_FONT_SP]~[TextViewerDefaults.MAX_FONT_SP])를 누른다. */
    fun sizeFor(base: Int, scale: Float): Int {
        val raw = base * scale
        if (raw.isNaN()) return base
        return raw.roundToInt().coerceIn(TextViewerDefaults.MIN_FONT_SP, TextViewerDefaults.MAX_FONT_SP)
    }
}
