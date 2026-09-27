package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import org.xmlpull.v1.XmlPullParser

/**
 * `styles.xml` 의 `<color>` 하나를 읽은 그대로. 풀기([XlsxColors.resolve])는 **나중에** 한다 —
 * 덮어쓴 팔레트(`colors/indexedColors`)가 `styles.xml` 의 맨 끝에 오기 때문이다.
 */
internal data class ColorSpec(
    val rgb: String? = null,
    val theme: Int? = null,
    val indexed: Int? = null,
    val tint: Double = 0.0,
    val auto: Boolean = false,
) {
    companion object {
        /** 지금 선 `<color>`·`<fgColor>` 같은 요소의 속성을 읽는다. */
        fun read(p: XmlPullParser): ColorSpec = ColorSpec(
            rgb = OoxmlXml.attr(p, "rgb"),
            theme = OoxmlXml.int(p, "theme"),
            indexed = OoxmlXml.int(p, "indexed"),
            tint = OoxmlXml.attr(p, "tint")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0,
            auto = OoxmlXml.attr(p, "auto")?.let { it == "1" || it.equals("true", true) } == true,
        )
    }
}

/**
 * SpreadsheetML 의 색 — 직접 적은 ARGB, 테마 색, 옛 팔레트 번호, 그리고 명도 조정(tint).
 *
 * ## 테마 번호는 테마 파일의 순서가 아니다
 *
 * `theme1.xml` 의 `clrScheme` 은 `dk1, lt1, dk2, lt2, accent1…6, hlink, folHlink` 순서로 적히는데,
 * 셀 서식의 `theme="n"` 은 **앞의 두 쌍을 바꿔** 읽는다 — 0=lt1, 1=dk1, 2=lt2, 3=dk2, 4…9=accent1…6,
 * 10=hlink, 11=folHlink. 근거: openpyxl 3.1.5 가 쓰는 기본 글꼴은 `<color theme="1"/>` 이고 그 테마의
 * 첫 원소는 dk1(검정)·둘째는 lt1(흰색)이다. 순서대로 읽으면 **모든 통합 문서의 기본 글자가 흰색**이
 * 된다. Apache POI(`ThemesTable`)·LibreOffice 도 같은 교환을 한다. `XlsxColorsTest` 가 박는다.
 */
internal object XlsxColors {

    /**
     * 옛 64색 팔레트(`indexed="n"`). openpyxl 3.1.5 의 `COLOR_INDEX` 와 한 자씩 대조했다.
     * 64·65 는 시스템 전경·배경이라 여기 없고, 부르는 쪽이 '자동' 으로 본다.
     */
    val DEFAULT_PALETTE: IntArray = intArrayOf(
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080,
        0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF,
        0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF,
        0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99,
        0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
        0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333,
    )

    /**
     * 테마 파일이 없을 때의 테마 — Office 2007 기본값(openpyxl 이 쓰는 테마와 같다).
     * **Excel 의 번호 순서**로 적는다(위 주석).
     */
    val DEFAULT_THEME: IntArray = intArrayOf(
        0xFFFFFF, 0x000000, 0xEEECE1, 0x1F497D,
        0x4F81BD, 0xC0504D, 0x9BBB59, 0x8064A2, 0x4BACC6, 0xF79646,
        0x0000FF, 0x800080,
    )

    /** `theme1.xml` 의 요소 이름 → Excel 번호. */
    val THEME_SLOTS: Map<String, Int> = mapOf(
        "lt1" to 0, "dk1" to 1, "lt2" to 2, "dk2" to 3,
        "accent1" to 4, "accent2" to 5, "accent3" to 6, "accent4" to 7, "accent5" to 8, "accent6" to 9,
        "hlink" to 10, "folHlink" to 11,
    )

    fun hex(rgb: Int): String {
        val v = rgb and 0xFFFFFF
        val s = Integer.toHexString(v)
        return "#" + "000000".substring(s.length) + s
    }

    /**
     * 색 하나를 `#rrggbb` 로. 자동(`auto`)·시스템 색(64·65)·모르는 번호는 null — 기본색을 쓰라는 뜻이다.
     *
     * @param theme Excel 번호 순서의 테마 색 12개.
     * @param palette 옛 팔레트 64색(문서가 덮어썼으면 그것).
     */
    fun resolve(spec: ColorSpec?, theme: IntArray, palette: IntArray): String? {
        if (spec == null || spec.auto) return null
        val base: Int = when {
            spec.rgb != null -> {
                val css = CssValues.hexColor(spec.rgb) ?: return null
                css.substring(1).toInt(16)
            }
            spec.theme != null -> theme.getOrNull(spec.theme) ?: return null
            spec.indexed != null -> palette.getOrNull(spec.indexed) ?: return null
            else -> return null
        }
        return hex(if (spec.tint != 0.0) applyTint(base, spec.tint) else base)
    }

    /**
     * 명도 조정 — ECMA-376 Part 1 `CT_Color` 의 `tint` 속성 설명에 적힌 식(절 번호는 판마다 달라
     * 적지 않는다). RGB 를 HLS 로 옮기고
     * (HLSMAX = 255) 음수면 `L·(1+tint)`, 양수면 `L·(1−tint) + (HLSMAX − HLSMAX·(1−tint))`.
     *
     * **실수로 계산하고 채널마다 반올림한다.** Excel 의 색 선택기가 보여 주는 값
     * (흰색 −15% = D9D9D9, 검정 +50% = 7F7F7F, Office 2007 강조1 +40% = 95B3D7)과 맞는다.
     * Excel 은 정수 HLS 를 쓰는 것으로 보여 몇몇 값에서 채널당 ±1 이 어긋날 수 있다(확인하지 못했다).
     */
    fun applyTint(rgb: Int, tint: Double): Int {
        if (tint == 0.0 || tint.isNaN()) return rgb
        val t = tint.coerceIn(-1.0, 1.0)
        val r = (rgb shr 16 and 0xFF) / 255.0
        val g = (rgb shr 8 and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        var h = 0.0
        var s = 0.0
        if (max != min) {
            val d = max - min
            s = if (l <= 0.5) d / (max + min) else d / (2 - max - min)
            h = when (max) {
                r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6
                g -> ((b - r) / d + 2) / 6
                else -> ((r - g) / d + 4) / 6
            }
        }
        val lum = l * HLSMAX
        val adjusted = if (t < 0) lum * (1 + t) else lum * (1 - t) + (HLSMAX - HLSMAX * (1 - t))
        val l2 = (adjusted / HLSMAX).coerceIn(0.0, 1.0)
        if (s == 0.0) {
            val v = channel(l2)
            return (v shl 16) or (v shl 8) or v
        }
        val q = if (l2 < 0.5) l2 * (1 + s) else l2 + s - l2 * s
        val p = 2 * l2 - q
        return (channel(hue(p, q, h + 1.0 / 3)) shl 16) or (channel(hue(p, q, h)) shl 8) or channel(hue(p, q, h - 1.0 / 3))
    }

    private const val HLSMAX = 255.0

    private fun channel(v: Double): Int = Math.floor(v.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()

    private fun hue(p: Double, q: Double, h0: Double): Double {
        var h = h0
        if (h < 0) h += 1
        if (h > 1) h -= 1
        return when {
            h < 1.0 / 6 -> p + (q - p) * 6 * h
            h < 1.0 / 2 -> q
            h < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - h) * 6
            else -> p
        }
    }
}
