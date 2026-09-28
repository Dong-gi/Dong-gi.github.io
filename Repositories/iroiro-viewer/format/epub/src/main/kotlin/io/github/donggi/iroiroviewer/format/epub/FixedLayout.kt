package io.github.donggi.iroiroviewer.format.epub

/**
 * **고정 레이아웃 쪽의 크기**를 찾는다(EPUB 3.3 고정 레이아웃 — `rendition:layout="pre-paginated"`).
 *
 * ## 왜 크기가 필요한가
 *
 * 고정 레이아웃 쪽은 만든 사람이 정한 **한 장의 화폭**(예: 1200×1600 CSS px) 위에 글과 그림을 좌표로 얹는다. 흘려서
 * 그리면(흐름 렌더) 좌표가 화면 폭으로 다시 계산되어 그림과 글이 엇갈린다 — 11단계가 '뜻이 달라진다' 로 미룬 이유다.
 * 그 화폭을 알아야 화면에 **통째로 줄여** 맞출 수 있다([ReaderStyle.fixedPage]).
 *
 * ## 어디서 읽는가(명세의 차례)
 *
 * 1. XHTML 쪽의 `<meta name="viewport" content="width=1200, height=1600">` — 명세가 요구하는 자리다.
 * 2. SVG 쪽이면 뿌리 `<svg>` 의 `width`·`height`, 없으면 `viewBox`.
 * 3. 책 전체의 `rendition:viewport`(옛 방식) — 쪽이 스스로 적지 않았을 때만.
 *
 * **위생기는 `<meta>` 를 지운다**(`HtmlSanitizer` 의 `DROP_VOID`). 그래서 크기는 위생 **전의** 글에서 읽는다 — 읽는 것은
 * 숫자 둘뿐이고, 그 숫자는 우리가 범위를 눌러 CSS 로 옮긴다(글자를 그대로 옮기지 않는다).
 *
 * ## DOM 을 만들지 않는다
 *
 * 머리(`<body` 앞)의 앞부분만 **글자로 훑는다**. 저장소 규칙('DOM 파싱 금지')이기도 하고, 쪽 하나를 열 때마다 XML 을
 * 한 번 더 파싱할 이유가 없다.
 */
object FixedLayout {

    /** 쪽 하나의 화폭. CSS px 이다. */
    data class Viewport(val width: Int, val height: Int)

    /**
     * 뷰포트 글(`width=1200, height=1600` · `width=1200;height=1600`)을 읽는다. 둘 중 하나라도 없거나 숫자가 아니면 null.
     *
     * `device-width` 같은 낱말은 **고정 화폭이 아니다** — 흐름 쪽이 흔히 적는 값이라 null 로 돌려 맞추지 않는다.
     */
    fun parseViewport(content: String?): Viewport? {
        if (content.isNullOrBlank() || content.length > MAX_CONTENT) return null
        var width: Int? = null
        var height: Int? = null
        for (part in content.split(',', ';')) {
            val eq = part.indexOf('=')
            if (eq < 0) continue
            val key = part.substring(0, eq).trim().lowercase()
            val value = number(part.substring(eq + 1)) ?: continue
            when (key) {
                "width" -> if (width == null) width = value
                "height" -> if (height == null) height = value
            }
        }
        return if (width != null && height != null) Viewport(width, height) else null
    }

    /**
     * XHTML 쪽의 머리에서 뷰포트를 찾는다. `<body` 앞, 앞의 [SCAN_CHARS] 글자까지만 본다.
     *
     * 주석 안의 `<meta>` 는 건너뛴다 — 실물 책(E03)이 머리에 주석으로 막아 둔 `<meta>` 를 여럿 둔다.
     */
    fun fromHtml(text: String): Viewport? {
        val end = minOf(text.length, SCAN_CHARS)
        var i = 0
        while (i < end) {
            val lt = text.indexOf('<', i)
            if (lt < 0 || lt >= end) return null
            if (text.startsWith("<!--", lt)) {
                val close = text.indexOf("-->", lt + 4)
                if (close < 0) return null
                i = close + 3
                continue
            }
            if (tagAt(text, lt, "body")) return null
            if (tagAt(text, lt, "meta")) {
                val gt = text.indexOf('>', lt)
                if (gt < 0) return null
                val tag = text.substring(lt, gt)
                if (attribute(tag, "name")?.trim().equals("viewport", ignoreCase = true)) {
                    parseViewport(attribute(tag, "content"))?.let { return it }
                }
                i = gt + 1
                continue
            }
            i = lt + 1
        }
        return null
    }

    /**
     * SVG 쪽의 뿌리 `<svg>` 에서 크기를 읽는다. `width`·`height` 가 **단위 없는 수나 px** 면 그것, 아니면 `viewBox` 의
     * 폭·높이. 백분율(`100%`)은 화폭이 아니다.
     */
    fun fromSvg(text: String): Viewport? {
        val end = minOf(text.length, SCAN_CHARS)
        var from = 0
        while (from < end) {
            val lt = text.indexOf('<', from)
            if (lt < 0 || lt >= end) return null
            if (text.startsWith("<!--", lt)) {
                val close = text.indexOf("-->", lt + 4)
                if (close < 0) return null
                from = close + 3
                continue
            }
            // 접두어가 붙은 뿌리(`<svg:svg`)도 받는다. `<svgx` 는 다른 이름이다.
            val local = when {
                text.startsWith("svg:svg", lt + 1, ignoreCase = true) -> lt + 8
                text.startsWith("svg", lt + 1, ignoreCase = true) -> lt + 4
                else -> {
                    from = lt + 1
                    continue
                }
            }
            val c = text.getOrNull(local)
            if (c == null || !(c.isWhitespace() || c == '>' || c == '/')) {
                from = lt + 1
                continue
            }
            val gt = text.indexOf('>', lt)
            if (gt < 0) return null
            val tag = text.substring(lt, gt)
            val w = length(attribute(tag, "width"))
            val h = length(attribute(tag, "height"))
            if (w != null && h != null) return Viewport(w, h)
            val box = attribute(tag, "viewBox")?.trim()?.split(Regex("[\\s,]+"))
            if (box != null && box.size == 4) {
                val bw = number(box[2])
                val bh = number(box[3])
                if (bw != null && bh != null) return Viewport(bw, bh)
            }
            return null
        }
        return null
    }

    /** 한 화폭 변의 상한. 명세가 정하지 않았다 — 실물(1200·1577·2048)보다 넉넉히, 퇴화한 값만 거른다. */
    const val MAX_SIDE = 20_000

    private const val SCAN_CHARS = 64 * 1024
    private const val MAX_CONTENT = 512

    /** `1200`·`1200.5`·`1200px` → 반올림한 정수. 범위 밖·음수·0 이면 null. */
    private fun number(raw: String): Int? {
        val t = raw.trim().removeSuffix("px").trim()
        if (t.isEmpty() || t.length > 12) return null
        val v = t.toDoubleOrNull() ?: return null
        if (!v.isFinite() || v < 1.0 || v > MAX_SIDE) return null
        return kotlin.math.floor(v + 0.5).toInt()
    }

    private fun length(raw: String?): Int? {
        val t = raw?.trim() ?: return null
        if (t.endsWith("%")) return null
        return number(t)
    }

    private fun tagAt(text: String, at: Int, name: String): Boolean {
        if (!text.startsWith("<$name", at, ignoreCase = true)) return false
        val c = text.getOrNull(at + name.length + 1) ?: return false
        return c.isWhitespace() || c == '>' || c == '/'
    }

    /** 태그 글자에서 속성 하나. 따옴표(`"`·`'`)로 싼 값만 읽는다(XHTML 은 늘 싼다). */
    private fun attribute(tag: String, name: String): String? {
        var from = 0
        while (true) {
            val at = tag.indexOf(name, from, ignoreCase = true)
            if (at < 0) return null
            from = at + name.length
            // 이름의 앞이 공백이어야 한다 — `data-name=` 을 `name=` 으로 읽지 않게.
            if (at == 0 || !tag[at - 1].isWhitespace()) continue
            var i = at + name.length
            while (i < tag.length && tag[i].isWhitespace()) i++
            if (i >= tag.length || tag[i] != '=') continue
            i++
            while (i < tag.length && tag[i].isWhitespace()) i++
            if (i >= tag.length) return null
            val quote = tag[i]
            if (quote != '"' && quote != '\'') continue
            val close = tag.indexOf(quote, i + 1)
            if (close < 0) return null
            return tag.substring(i + 1, close)
        }
    }
}
