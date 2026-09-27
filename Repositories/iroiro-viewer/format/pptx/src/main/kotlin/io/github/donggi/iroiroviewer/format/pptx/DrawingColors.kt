package io.github.donggi.iroiroviewer.format.pptx

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * sRGB 색 하나. 채널은 0..1, [a] 는 불투명도(1 이 불투명).
 */
internal data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {

    /** 완전히 투명하다 — 그리지 않는다. */
    val isTransparent: Boolean get() = a <= 0.001

    /** `#rrggbb`. 알파는 버린다. */
    fun hex(): String = "#" + byteHex(r) + byteHex(g) + byteHex(b)

    /**
     * CSS 값. 불투명하면 `#rrggbb`, 아니면 `rgba(r,g,b,a)`.
     *
     * **문서의 글자가 아니라 수로 만든다** — 채널을 정수로 바꿔 적으므로 선언을 끊는 글자가
     * 들어갈 자리가 없다(`CssValues` 가 막는 것과 같은 성질이다). `CssValues.hexColor` 의
     * 여덟 자리는 앞의 두 자리를 **버리는** ARGB 규칙이라 알파를 실을 수 없어 여기서 적는다.
     */
    fun css(): String {
        if (a >= 0.999) return hex()
        val alpha = String.format(Locale.ROOT, "%.3f", a.coerceIn(0.0, 1.0)).trimEnd('0').trimEnd('.')
        return "rgba(" + byte(r) + "," + byte(g) + "," + byte(b) + "," + alpha.ifEmpty { "0" } + ")"
    }

    companion object {
        val BLACK = Rgba(0.0, 0.0, 0.0)
        val WHITE = Rgba(1.0, 1.0, 1.0)

        /** `RRGGBB`(앞의 `#` 허용). 여섯 자리 16진이 아니면 null. */
        fun parseHex(raw: String?): Rgba? {
            val v = raw?.trim()?.removePrefix("#") ?: return null
            if (v.length != 6 || !v.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            val n = v.toInt(16)
            return Rgba(((n shr 16) and 0xFF) / 255.0, ((n shr 8) and 0xFF) / 255.0, (n and 0xFF) / 255.0)
        }

        /** 0..1 을 0..255 로. **반올림은 `floor(x + 0.5)`** — `kotlin.math.round` 는 짝수 쪽으로 간다(CLAUDE.md 함정 표). */
        fun byte(c: Double): Int = floor(c.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()

        private fun byteHex(c: Double): String {
            val v = byte(c)
            return HEX[v shr 4].toString() + HEX[v and 0xF]
        }

        private const val HEX = "0123456789abcdef"
    }
}

/**
 * DrawingML 의 색 변환(ECMA-376 §20.1.2.3). **순수 계산**이라 문서도 패키지도 모른다.
 *
 * ## 어느 색 공간에서 계산하는가
 *
 * * `lumMod`·`lumOff`·`lum`·`sat*`·`hue*` — **HSL**. 오피스의 '밝게 40%'(`lumMod 60000` +
 *   `lumOff 40000`)가 이것이고, 파워포인트가 보여 주는 값과 맞는다(시험이 손으로 계산한 값과 견준다).
 * * `tint`·`shade` — **선형 RGB**(감마를 푼 값). 명세의 말('10% tint 는 입력 10% 와 흰색 90%')을
 *   어느 공간에서 섞는지 명세가 적지 않는데, LibreOffice 가 선형 공간에서 섞어 파워포인트와
 *   맞추고 있어 그쪽을 따른다(LibreOffice `oox` 의 `Color::getColor` 를 읽고 옮겼다).
 * * `alpha*` — 불투명도.
 *
 * 모르는 변환(`gamma`·`red` 따위)은 건너뛴다 — 색 하나가 조금 달라지는 것이 색을 통째로
 * 잃는 것보다 낫다.
 */
internal object ColorMath {

    /** 변환 목록을 **적힌 순서대로** 건다(순서가 뜻을 갖는다 — lumMod 뒤의 lumOff). */
    fun apply(base: Rgba, mods: List<ColorMod>): Rgba {
        var c = base
        for (m in mods) c = applyOne(c, m.name, m.value)
        return c
    }

    private fun applyOne(c: Rgba, name: String, value: Int): Rgba {
        val v = value / 100_000.0
        return when (name) {
            "alpha" -> c.copy(a = v.coerceIn(0.0, 1.0))
            "alphaMod" -> c.copy(a = (c.a * v).coerceIn(0.0, 1.0))
            "alphaOff" -> c.copy(a = (c.a + v).coerceIn(0.0, 1.0))
            "lumMod", "lumOff", "lum", "satMod", "satOff", "sat", "hueMod", "hueOff", "hue", "comp" -> {
                val hsl = toHsl(c)
                var h = hsl[0]
                var s = hsl[1]
                var l = hsl[2]
                when (name) {
                    "lumMod" -> l *= v
                    "lumOff" -> l += v
                    "lum" -> l = v
                    "satMod" -> s *= v
                    "satOff" -> s += v
                    "sat" -> s = v
                    // 색상은 1/60000 도로 적힌다.
                    "hueMod" -> h *= v
                    "hueOff" -> h += value / 60_000.0 / 360.0
                    "hue" -> h = value / 60_000.0 / 360.0
                    "comp" -> h += 0.5
                }
                fromHsl(h, s.coerceIn(0.0, 1.0), l.coerceIn(0.0, 1.0), c.a)
            }
            "tint" -> linear(c) { ch -> 1.0 - (1.0 - ch) * v.coerceIn(0.0, 1.0) }
            "shade" -> linear(c) { ch -> ch * v.coerceIn(0.0, 1.0) }
            "inv" -> Rgba(1 - c.r, 1 - c.g, 1 - c.b, c.a)
            "gray" -> {
                val y = 0.3 * c.r + 0.59 * c.g + 0.11 * c.b
                Rgba(y, y, y, c.a)
            }
            else -> c
        }
    }

    private inline fun linear(c: Rgba, f: (Double) -> Double): Rgba = Rgba(
        fromLinear(f(toLinear(c.r))),
        fromLinear(f(toLinear(c.g))),
        fromLinear(f(toLinear(c.b))),
        c.a,
    )

    /** sRGB → 선형. */
    fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** 선형 → sRGB. */
    fun fromLinear(l: Double): Double {
        val v = l.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
    }

    /** [h] 는 0..1(한 바퀴), [s]·[l] 은 0..1. */
    fun toHsl(c: Rgba): DoubleArray {
        val max = maxOf(c.r, c.g, c.b)
        val min = minOf(c.r, c.g, c.b)
        val l = (max + min) / 2
        val d = max - min
        if (d < 1e-12) return doubleArrayOf(0.0, 0.0, l)
        val s = d / (1 - abs(2 * l - 1))
        val h = when (max) {
            c.r -> ((c.g - c.b) / d).mod(6.0)
            c.g -> (c.b - c.r) / d + 2
            else -> (c.r - c.g) / d + 4
        } / 6.0
        return doubleArrayOf(h, s, l)
    }

    fun fromHsl(h: Double, s: Double, l: Double, a: Double = 1.0): Rgba {
        val hh = h.mod(1.0) * 6
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs(hh.mod(2.0) - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            hh < 1 -> Triple(c, x, 0.0)
            hh < 2 -> Triple(x, c, 0.0)
            hh < 3 -> Triple(0.0, c, x)
            hh < 4 -> Triple(0.0, x, c)
            hh < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return Rgba((r + m).coerceIn(0.0, 1.0), (g + m).coerceIn(0.0, 1.0), (b + m).coerceIn(0.0, 1.0), a)
    }
}

/**
 * 색 이름을 색으로 — 테마 색(색 대응표를 거쳐), 시스템 색, 미리 정한 색.
 *
 * @param scheme 테마의 색(`dk1`·`lt1`·`accent1`…). 비어 있으면 오피스 기본 테마를 쓴다 —
 *   테마가 없거나 깨진 파일도 글자는 검게, 배경은 희게 보여야 한다.
 * @param clrMap 색 대응표(`bg1` → `lt1`). 마스터의 `p:clrMap`, 슬라이드가 덮어쓰면 그것.
 */
internal class ColorResolver(
    private val scheme: Map<String, Rgba>,
    private val clrMap: Map<String, String>,
) {

    /** 색을 푼다. 모르는 색이면 null — 부르는 쪽이 기본값을 고른다. */
    fun resolve(spec: ColorSpec?, phClr: Rgba? = null): Rgba? {
        spec ?: return null
        val base = when (spec.kind) {
            ColorKind.SRGB -> Rgba.parseHex(spec.value)
            ColorKind.SCHEME -> schemeColor(spec.value, phClr)
            ColorKind.SYSTEM -> Rgba.parseHex(spec.last) ?: SYSTEM[spec.value]
            ColorKind.PRESET -> PresetColors.of(spec.value)
            ColorKind.HSL -> spec.nums?.takeIf { it.size == 3 }?.let {
                ColorMath.fromHsl(it[0] / 60_000.0 / 360.0, (it[1] / 100_000.0).coerceIn(0.0, 1.0), (it[2] / 100_000.0).coerceIn(0.0, 1.0))
            }
            ColorKind.SCRGB -> spec.nums?.takeIf { it.size == 3 }?.let {
                Rgba(
                    ColorMath.fromLinear(it[0] / 100_000.0),
                    ColorMath.fromLinear(it[1] / 100_000.0),
                    ColorMath.fromLinear(it[2] / 100_000.0),
                )
            }
        } ?: return null
        return ColorMath.apply(base, spec.mods)
    }

    /** 테마 색 이름 하나. `bg1`·`tx1` 은 대응표를 한 번 거친다(되풀이하지 않는다 — 고리가 생길 수 없다). */
    fun schemeColor(name: String, phClr: Rgba? = null): Rgba? {
        if (name == "phClr") return phClr
        val mapped = clrMap[name] ?: DEFAULT_MAP[name] ?: name
        return scheme[mapped] ?: OFFICE_SCHEME[mapped]
    }

    companion object {
        /** 테마도 대응표도 모르는 풀이. 테마 자체의 색(`srgbClr`·`sysClr`)을 풀 때 쓴다. */
        val PLAIN = ColorResolver(emptyMap(), emptyMap())

        /** 대응표가 없을 때의 기본(오피스 기본 마스터와 같다). */
        val DEFAULT_MAP = mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2")

        /** 오피스 2013~ 기본 테마의 색. 테마가 없는 파일의 마지막 기댈 곳. */
        val OFFICE_SCHEME: Map<String, Rgba> = mapOf(
            "dk1" to "000000", "lt1" to "FFFFFF", "dk2" to "44546A", "lt2" to "E7E6E6",
            "accent1" to "4472C4", "accent2" to "ED7D31", "accent3" to "A5A5A5", "accent4" to "FFC000",
            "accent5" to "5B9BD5", "accent6" to "70AD47", "hlink" to "0563C1", "folHlink" to "954F72",
        ).mapValues { Rgba.parseHex(it.value)!! }

        /** 테마 색 슬롯 이름. 이 밖의 이름은 테마에서 읽지 않는다. */
        val SCHEME_SLOTS = OFFICE_SCHEME.keys

        /** `sysClr` 에 `lastClr` 가 없을 때. 윈도 기본값이다. */
        private val SYSTEM: Map<String, Rgba> = mapOf(
            "windowText" to "000000", "window" to "FFFFFF", "btnFace" to "F0F0F0", "btnText" to "000000",
            "menu" to "F0F0F0", "menuText" to "000000", "highlight" to "0078D7", "highlightText" to "FFFFFF",
            "grayText" to "6D6D6D", "captionText" to "000000", "infoBk" to "FFFFE1", "infoText" to "000000",
            "3dDkShadow" to "696969", "3dLight" to "E3E3E3", "btnShadow" to "A0A0A0", "btnHighlight" to "FFFFFF",
        ).mapValues { Rgba.parseHex(it.value)!! }
    }
}

/**
 * 미리 정한 색(`a:prstClr`, ST_PresetColorVal). 명세의 이름은 CSS 색 이름에 `dk`·`lt`·`med`
 * 줄임을 섞은 것이라(`dkBlue` = darkblue), 줄임을 펴서 CSS 이름표로 찾는다. 자주 쓰이는 것만 둔다.
 */
internal object PresetColors {

    fun of(name: String): Rgba? {
        val key = name.lowercase()
            .replace(Regex("^dk"), "dark")
            .replace(Regex("^lt"), "light")
            .replace(Regex("^med"), "medium")
        return TABLE[key]?.let { Rgba.parseHex(it) }
    }

    private val TABLE = mapOf(
        "black" to "000000", "white" to "FFFFFF", "red" to "FF0000", "green" to "008000", "blue" to "0000FF",
        "yellow" to "FFFF00", "cyan" to "00FFFF", "aqua" to "00FFFF", "magenta" to "FF00FF", "fuchsia" to "FF00FF",
        "gray" to "808080", "grey" to "808080", "silver" to "C0C0C0", "maroon" to "800000", "navy" to "000080",
        "olive" to "808000", "purple" to "800080", "teal" to "008080", "orange" to "FFA500", "lime" to "00FF00",
        "pink" to "FFC0CB", "brown" to "A52A2A", "gold" to "FFD700", "indigo" to "4B0082", "violet" to "EE82EE",
        "darkblue" to "00008B", "darkred" to "8B0000", "darkgreen" to "006400", "darkgray" to "A9A9A9",
        "darkgrey" to "A9A9A9", "darkcyan" to "008B8B", "darkmagenta" to "8B008B", "darkorange" to "FF8C00",
        "darkviolet" to "9400D3", "lightblue" to "ADD8E6", "lightgreen" to "90EE90", "lightgray" to "D3D3D3",
        "lightgrey" to "D3D3D3", "lightyellow" to "FFFFE0", "lightpink" to "FFB6C1", "lightcyan" to "E0FFFF",
        "mediumblue" to "0000CD", "mediumpurple" to "9370DB", "mediumseagreen" to "3CB371",
        "skyblue" to "87CEEB", "steelblue" to "4682B4", "royalblue" to "4169E1", "tomato" to "FF6347",
        "crimson" to "DC143C", "coral" to "FF7F50", "salmon" to "FA8072", "khaki" to "F0E68C", "tan" to "D2B48C",
        "beige" to "F5F5DC", "ivory" to "FFFFF0", "lavender" to "E6E6FA", "turquoise" to "40E0D0",
        "chocolate" to "D2691E", "firebrick" to "B22222", "forestgreen" to "228B22", "seagreen" to "2E8B57",
        "slategray" to "708090", "dimgray" to "696969", "gainsboro" to "DCDCDC", "whitesmoke" to "F5F5F5",
    )
}
