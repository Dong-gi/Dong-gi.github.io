package io.github.donggi.iroiroviewer.format.html

/**
 * 변환기(docx·xlsx·pptx·HWPX)가 HTML 을 **쓰는** 유일한 길.
 *
 * ## 왜 문자열을 이어 붙이지 않는가
 *
 * 문서에서 온 글자(본문·시트 값·파일 이름·색 코드)는 공격자가 적는 값이다. 한 곳에서라도
 * `"<td>" + value + "</td>"` 로 붙이면 그 값의 `<script>` 가 그대로 태그가 된다. 그래서
 * **글자는 [text] 로만, 속성 값은 [start]·[void] 의 인자로만** 들어가게 하고, 둘 다 여기서
 * 이스케이프한다. 태그와 속성 **이름**은 변환기가 적는 상수라 형식만 검사한다.
 *
 * 그래도 결과는 `HtmlSanitizer` 를 한 번 더 지난다(`FlowDocument` 의 주석) — 이 클래스의
 * 실수가 곧바로 구멍이 되지 않게 하는, 겹쳐 둔 방어다.
 *
 * ## 길이 상한
 *
 * 쓰는 양을 [maxChars] 로 묶는다. 넘으면 **던지지 않고 멈춘다**([full]) — 긴 문서의 앞부분은
 * 보여 주는 편이 통째로 실패하는 것보다 낫다. 변환기는 [full] 을 보고 `FlowWarnings.TRUNCATED`
 * 를 남긴다. 멈춘 뒤에도 [end] 는 받는다 — 열린 태그를 닫아 두어야 WebView 가 문서를 온전히 읽는다.
 */
class HtmlWriter(private val maxChars: Int = DEFAULT_MAX_CHARS) {

    private val out = StringBuilder(4096)

    /** 상한에 닿아 더 쓰지 않는다. */
    var full: Boolean = false
        private set

    /** 열린 태그들. [closeAll] 이 쓴다. */
    private val open = ArrayList<String>()

    /**
     * [open] 과 짝을 이루는 '실제로 썼는가'. 멈춘 뒤에 연 태그는 짝을 세려고 열린 것으로 치지만
     * 쓰지 않았으므로 **닫는 태그도 쓰지 않는다** — 쓰면 짝 없는 닫는 태그가 쌓이고, 그 양이
     * 상한과 상관없이 늘어난다(12단계 검토가 잡았다).
     */
    private val written = ArrayList<Boolean>()

    val length: Int get() = out.length

    /**
     * 여는 태그. 값이 null 인 속성은 쓰지 않는다.
     *
     * @param attrs 이름과 값. 이름은 `[a-z][a-z0-9-]*` 이어야 한다.
     */
    fun start(tag: String, vararg attrs: Pair<String, String?>): HtmlWriter {
        checkName(tag)
        if (full) {
            // 멈췄어도 짝을 맞추려고 열린 것으로 센다 — [end] 가 쓰지 않고 걷어 낸다.
            open.add(tag)
            written.add(false)
            return this
        }
        out.append('<').append(tag)
        writeAttrs(attrs)
        out.append('>')
        open.add(tag)
        written.add(true)
        checkFull()
        return this
    }

    /** 닫는 태그. 가장 안쪽의 같은 이름까지 닫는다(중간에 남은 것도 함께). */
    fun end(tag: String): HtmlWriter {
        checkName(tag)
        val at = open.lastIndexOf(tag)
        if (at < 0) return this
        while (open.size > at) popOne()
        return this
    }

    /** 가장 안쪽 것을 닫는다. 쓴 태그의 닫는 태그는 상한을 넘어도 쓴다 — 짝이 맞아야 문서가 온전하다. */
    private fun popOne() {
        val t = open.removeAt(open.size - 1)
        if (written.removeAt(written.size - 1)) out.append("</").append(t).append('>')
    }

    /** 짝이 없는 태그(`br`·`img`·`hr`·`col`·`wbr`). */
    fun void(tag: String, vararg attrs: Pair<String, String?>): HtmlWriter {
        checkName(tag)
        if (full) return this
        out.append('<').append(tag)
        writeAttrs(attrs)
        out.append('>')
        checkFull()
        return this
    }

    /** 글자. `&`·`<`·`>`·`"`·`'` 를 바꾸고, 제어문자(탭·줄바꿈 제외)는 버린다. */
    fun text(value: CharSequence): HtmlWriter {
        if (full || value.isEmpty()) return this
        escape(value, out)
        checkFull()
        return this
    }

    /**
     * 다른 쓰개가 쓴 것을 여기에 잇는다 — 뒤에 와야 하는 것을 먼저 만나는 자리(한글의 아래쪽 캡션은 스키마의 차례상 표보다
     * 앞에 적혀 있다)에서 따로 써 두었다가 붙인다. [other] 도 이 클래스로 쓴 것이라 이스케이프를 이미 거쳤다. **열린 것을 모두
     * 닫은 뒤에만** 받는다(짝 없는 태그가 이 쓰개의 짝 세기를 흐트러뜨리지 않게). 이쪽이 상한에 닿았으면 잇지 않고, 이으면서
     * 넘으면 [full] 이 된다.
     */
    fun append(other: HtmlWriter): HtmlWriter {
        require(other.open.isEmpty()) { "열린 태그가 남은 쓰개는 이을 수 없다" }
        if (full || other.out.isEmpty()) return this
        out.append(other.out)
        checkFull()
        return this
    }

    /** 열린 것을 전부 닫는다. 변환이 중간에 끝났을 때(상한·취소) 부른다. */
    fun closeAll(): HtmlWriter {
        while (open.isNotEmpty()) popOne()
        return this
    }

    override fun toString(): String = out.toString()

    private fun writeAttrs(attrs: Array<out Pair<String, String?>>) {
        for ((name, value) in attrs) {
            if (value == null) continue
            checkName(name)
            out.append(' ').append(name).append("=\"")
            escape(value, out)
            out.append('"')
        }
    }

    private fun checkFull() {
        if (out.length >= maxChars) full = true
    }

    private fun checkName(name: String) {
        require(name.isNotEmpty() && name[0] in 'a'..'z' && name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
            "태그·속성 이름은 상수여야 한다"
        }
    }

    companion object {
        /** 부분 하나의 상한(글자). `HtmlSanitizer` 의 입력 상한(32 MiB)보다 넉넉히 작게. */
        const val DEFAULT_MAX_CHARS = 8 * 1024 * 1024

        /** [text] 와 같은 규칙으로 이스케이프한 문자열. 시험과 `title` 같은 짧은 값에 쓴다. */
        fun escape(value: CharSequence): String = StringBuilder(value.length + 16).also { escape(value, it) }.toString()

        private fun escape(value: CharSequence, out: StringBuilder) {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when {
                    c == '&' -> out.append("&amp;")
                    c == '<' -> out.append("&lt;")
                    c == '>' -> out.append("&gt;")
                    c == '"' -> out.append("&quot;")
                    c == '\'' -> out.append("&#39;")
                    // 탭·줄바꿈은 둔다(`pre-wrap` 문단이 쓴다). 나머지 제어문자는 버린다 — XML 이
                    // 허락하지 않는 글자라 WebView 가 문서를 거기서 끊을 수 있다.
                    c == '\t' || c == '\n' -> out.append(c)
                    c.code < 0x20 || c.code == 0x7F -> Unit
                    // 대리 문자는 **짝이 맞을 때만** 쓴다. 짝 잃은 반쪽은 UTF-8 로 옮길 수 없다.
                    c.isHighSurrogate() -> {
                        if (i + 1 < value.length && value[i + 1].isLowSurrogate()) {
                            out.append(c).append(value[i + 1])
                            i++
                        }
                    }
                    c.isLowSurrogate() -> Unit
                    else -> out.append(c)
                }
                i++
            }
        }
    }
}

/**
 * 문서에서 온 값을 **CSS 값으로 옮기는 유일한 길.**
 *
 * `style` 속성은 이스케이프만으로 막히지 않는다 — `color: red; background: url(https://…)` 처럼
 * **값 안에서 선언을 끊고 새 선언을 여는** 것이 따옴표 없이 된다. 그래서 값마다 **모양을
 * 검사해 통과한 것만** 쓴다. 색은 16진 여섯 자리, 길이는 숫자와 단위, 글꼴 이름은 글자·숫자·
 * 공백·하이픈뿐이다. 통과하지 못한 값은 null — 그 선언을 쓰지 않는다.
 */
object CssValues {

    /**
     * OOXML 의 색(`FF0000`·`auto`)을 `#ff0000` 으로. 여섯 자리 16진이 아니면 null.
     * 여덟 자리(ARGB, xlsx 가 쓴다)면 앞의 알파를 버린다.
     */
    fun hexColor(raw: String?): String? {
        val v = raw?.trim() ?: return null
        val hex = when (v.length) {
            6 -> v
            8 -> v.substring(2)
            else -> return null
        }
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return "#" + hex.lowercase()
    }

    /** 포인트 길이. 음수·NaN·터무니없이 큰 값은 [min]..[max] 로 누른다. */
    fun pt(value: Double, min: Double = -2000.0, max: Double = 2000.0): String? {
        if (value.isNaN() || value.isInfinite()) return null
        return trim(value.coerceIn(min, max)) + "pt"
    }

    /** 백분율. */
    fun percent(value: Double, min: Double = 0.0, max: Double = 1000.0): String? {
        if (value.isNaN() || value.isInfinite()) return null
        return trim(value.coerceIn(min, max)) + "%"
    }

    /**
     * 단위 없는 수(`1.6`) — 줄 간격에 쓴다. 백분율(`160%`)은 요소에서 길이로 풀린 뒤 자식에게 **그 길이로** 물려져, 문단보다
     * 큰 글자에 좁은 줄을 준다. 단위 없는 수는 자식마다 자기 글자 크기에 곱한다. 소수 둘째 자리까지.
     */
    fun number(value: Double, min: Double, max: Double): String? {
        if (value.isNaN() || value.isInfinite()) return null
        val s = String.format(java.util.Locale.ROOT, "%.2f", value.coerceIn(min, max)).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    /** 뷰포트 폭 대비 길이(`vw`). pptx 가 슬라이드를 화면 폭에 맞출 때 쓴다. */
    fun vw(value: Double, min: Double = -1000.0, max: Double = 1000.0): String? {
        if (value.isNaN() || value.isInfinite()) return null
        return trim(value.coerceIn(min, max)) + "vw"
    }

    /**
     * 글꼴 이름. 글자·숫자·공백·하이픈·밑줄만 남기고, 남는 것이 없으면 null.
     * 따옴표로 감싸 돌려준다 — 공백이 든 이름(`Malgun Gothic`)도 한 이름으로 읽힌다.
     */
    fun fontFamily(raw: String?): String? {
        val v = raw?.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }?.trim()
        if (v.isNullOrEmpty() || v.length > 64) return null
        return "\"$v\""
    }

    /** 정해 둔 낱말 가운데 하나(`left`·`center`…)면 그대로, 아니면 null. */
    fun keyword(raw: String?, allowed: Set<String>): String? = raw?.takeIf { it in allowed }

    /** 소수 넷째 자리까지, 끝의 0 을 뗀다. */
    private fun trim(v: Double): String {
        val s = String.format(java.util.Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }
}

/**
 * `style` 속성 하나를 모은다. 값은 [CssValues] 를 지난 것이어야 한다 — 이 클래스는 그것을
 * 믿고 이어 붙인다. 속성 **이름**은 변환기가 적는 상수라 형식만 본다.
 */
class StyleBuilder {
    private val out = StringBuilder()

    /** 값이 null 이면 아무것도 하지 않는다. */
    fun add(property: String, value: String?): StyleBuilder {
        if (value == null) return this
        require(property.isNotEmpty() && property.all { it in 'a'..'z' || it == '-' }) { "CSS 속성 이름은 상수여야 한다" }
        // 값에 선언을 끊는 글자가 있으면 쓰지 않는다 — [CssValues] 를 지났다면 있을 수 없다.
        if (value.any { it == ';' || it == '{' || it == '}' || it == '<' || it == '\\' }) return this
        if (out.isNotEmpty()) out.append(';')
        out.append(property).append(':').append(value)
        return this
    }

    /** 쓸 것이 없으면 null — 빈 `style=""` 을 만들지 않는다. */
    fun build(): String? = if (out.isEmpty()) null else out.toString()
}
