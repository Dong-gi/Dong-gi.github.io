package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.HancomFonts
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import java.util.Locale

/**
 * 글자 한 벌의 모양(글자 모양 레코드에서 만든 것). CSS 는 여기서만 만든다 — 값은 전부 [CssValues] 를 지난다.
 *
 * @param size 크기(1/100 pt).
 * @param color `0xrrggbb`, 없으면 -1.
 * @param shade 음영 색. 없음·흰색은 -1 로 둔다(흰 음영을 적으면 어두운 화면에서 글자 뒤에 흰 띠가 생긴다).
 * @param underline 0 없음, 1 아래, 3 위.
 */
internal class RunStyle(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Int,
    val strike: Boolean,
    val sup: Boolean,
    val sub: Boolean,
    val color: Int,
    val shade: Int,
    val size: Int,
    val font: String?,
    val effect: Boolean,
) {
    /** 윗첨자·아랫첨자로 감쌀 태그. */
    val wrap: String? get() = if (sup) "sup" else if (sub) "sub" else null

    /**
     * 글자 덩이 하나의 CSS — **물려받는 것**(크기·굵기·기울임·색·글꼴)은 문단 바탕 [base] 와 다른 것만 적는다. 물려받지
     * 않는 장식(밑줄·취소선)과 음영은 언제나 적는다.
     */
    fun css(base: RunStyle, lightBackground: Boolean = true): String? {
        val sb = StyleBuilder()
        if (size != base.size && base.size > 0 && size > 0) {
            sb.add("font-size", CssValues.percent(size * 100.0 / base.size, MIN_FONT_PERCENT, MAX_FONT_PERCENT))
        }
        if (bold != base.bold) sb.add("font-weight", if (bold) "bold" else "normal")
        if (italic != base.italic) sb.add("font-style", if (italic) "italic" else "normal")
        val c = shownColor(lightBackground)
        if (c != base.shownColor(lightBackground)) sb.add("color", hex(c) ?: "initial")
        // 글꼴은 이름이 아니라 명조·고딕으로만(HWPX 와 같다 — `HancomFonts`). 바탕이 명조면 부분 전체가 `div.serif` 다.
        val serif = HancomFonts.isSerif(font ?: base.font)
        if (serif != HancomFonts.isSerif(base.font)) sb.add("font-family", if (serif) "serif" else "sans-serif")
        val deco = when {
            underline == 1 && strike -> "underline line-through"
            underline == 3 && strike -> "overline line-through"
            underline == 1 -> "underline"
            underline == 3 -> "overline"
            strike -> "line-through"
            else -> null
        }
        sb.add("text-decoration", deco)
        if (shade >= 0) sb.add("background-color", hex(shade))
        return sb.build()
    }

    /**
     * 문단 요소에 한 번 적는 바탕 — 문서 기본 [doc] 과 다른 것만. 문단 첫 글자의 모양을 바탕으로 삼으면 대부분의 문단은
     * 덩이마다 `span` 을 두지 않아도 되고, 빈 문단의 줄 높이도 그 글자 크기를 따른다. **제목이면 크기와 굵기를 언제나 적는다**
     * — 적지 않으면 브라우저의 `h1{font-size:2em}` 이 한글의 크기를 덮는다(12단계 docx 와 같은 판단).
     */
    fun paragraphCss(doc: RunStyle, heading: Boolean, sb: StyleBuilder, lightBackground: Boolean = true) {
        if ((heading || size != doc.size) && doc.size > 0 && size > 0) {
            sb.add("font-size", CssValues.percent(size * 100.0 / doc.size, MIN_FONT_PERCENT, MAX_FONT_PERCENT))
        }
        if (heading || bold != doc.bold) sb.add("font-weight", if (bold) "bold" else "normal")
        if (italic != doc.italic) sb.add("font-style", if (italic) "italic" else "normal")
        val c = shownColor(lightBackground)
        if (c != doc.shownColor(lightBackground)) hex(c)?.let { sb.add("color", it) }
        val serif = HancomFonts.isSerif(font ?: doc.font)
        if (serif != HancomFonts.isSerif(doc.font)) sb.add("font-family", if (serif) "serif" else "sans-serif")
    }

    /**
     * 실제로 적을 글자 색. **밝은 바탕 위의 흰 글자는 적지 않는다**(-1, 기본 색). 한글 문서는 흰 글자를 파란 도형·그림 위에
     * 얹는 일이 흔한데(표지·차례 상자), 흐름 렌더는 도형의 채우기와 겹친 그림을 그 뒤에 깔지 못한다 — 흰 글자가 흰 화면에
     * 그대로 가면 **글이 사라진다.** 바탕을 아는 곳(칸의 면 색, 글상자의 채우기)에서는 문서의 색을 그대로 쓴다.
     */
    fun shownColor(lightBackground: Boolean): Int {
        // **글자 자신의 음영이 바탕이다.** 음영이 어두우면 흰 글자는 그 위에서 읽히므로 지우지 않는다 — 칸의 면 색만 보고
        // 지우면 남색 음영 위의 흰 글자가 검은 글자가 되어 사라졌다(검토가 잡았다).
        val onLight = if (shade >= 0) isLight(shade) else lightBackground
        return if (onLight && color >= 0 && isLight(color)) -1 else color
    }

    companion object {
        val DEFAULT = RunStyle(false, false, 0, false, false, false, 0, -1, 1000, null, false)

        private const val MIN_FONT_PERCENT = 30.0
        private const val MAX_FONT_PERCENT = 400.0

        /** 밝은 색인가 — 세 채널이 모두 0xE0 이상(흰색에 가까운 것만). */
        fun isLight(c: Int): Boolean =
            ((c ushr 16) and 0xFF) >= LIGHT && ((c ushr 8) and 0xFF) >= LIGHT && (c and 0xFF) >= LIGHT

        private const val LIGHT = 0xE0

        fun of(cs: CharShape, face: String?): RunStyle = RunStyle(
            bold = cs.bold,
            italic = cs.italic,
            underline = when (cs.underlinePos) {
                1 -> 1
                3 -> 3
                else -> 0
            },
            strike = cs.strike,
            sup = cs.superscript,
            sub = cs.subscript && !cs.superscript,
            color = cs.textColor,
            shade = if (cs.shadeColor == 0xFFFFFF) -1 else cs.shadeColor,
            size = cs.sizeCentiPt.coerceIn(0, 409_600),
            font = face?.takeIf { it.isNotBlank() },
            effect = cs.effect,
        )

        /** `0xrrggbb` → `#rrggbb`. 없으면 null. */
        fun hex(c: Int): String? = if (c < 0) null else CssValues.hexColor(String.format(Locale.ROOT, "%06x", c and 0xFFFFFF))
    }
}

/**
 * 필드 명령에서 링크의 대상을 읽는다. 명령은 `대상;…` 꼴이고 `\` 가 `;`·`:` 따위를 적는다(HWPX 의 Command 와 같은 규칙 —
 * python-hwpx PR #111). **문서 안 링크는 `?#숫자`** 로 문단·표의 인스턴스 번호를 가리킨다(13단계 실측, 명세 S01 의 차례).
 * `?이름` 은 책갈피로 읽는다 — **실물로 확인하지 못했다.**
 */
internal object HwpLinks {
    sealed interface Target
    class Instance(val id: Long) : Target
    class Bookmark(val name: String) : Target
    object External : Target
    object None : Target

    fun parse(command: String): Target {
        val target = StringBuilder()
        var i = 0
        while (i < command.length) {
            val c = command[i]
            if (c == '\\' && i + 1 < command.length) {
                target.append(command[i + 1])
                i += 2
                continue
            }
            if (c == ';') break
            target.append(c)
            i++
        }
        val t = target.toString().trim()
        return when {
            t.isEmpty() -> None
            t.startsWith("?#") -> t.substring(2).toLongOrNull()?.takeIf { it > 0 }?.let { Instance(it) } ?: None
            t.startsWith("?") -> t.substring(1).takeIf { it.isNotBlank() }?.let { Bookmark(it) } ?: None
            t.startsWith("#") -> t.substring(1).takeIf { it.isNotBlank() }?.let { Bookmark(it) } ?: None
            else -> External
        }
    }
}
