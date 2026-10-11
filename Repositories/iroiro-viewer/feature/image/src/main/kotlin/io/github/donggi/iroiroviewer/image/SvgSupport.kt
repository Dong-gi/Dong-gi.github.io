package io.github.donggi.iroiroviewer.image

import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.safety.LimitedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * SVG 를 그리는 데 필요한 판단 — 플랫폼 디코더(`ImageDecoder`)에 SVG 가 없어서 그림 뷰어가 **잠긴 WebView** 로 그린다(사용자 요청,
 * 14단계 뒤). 여기는 `android.*` 를 쓰지 않는 판단만 둔다 — 파일 고르기·앞머리 읽기·뿌리 요소의 치수·우리가 쓰는 쪽(HTML).
 *
 * ## 그리는 길
 *
 * SVG 를 **문서로 열지 않고 `<img>` 로 싣는다.** 브라우저는 `<img>` 로 실린 SVG 를 '고정 모드' 로 그린다 — 스크립트가 돌지 않고,
 * 바깥 자원(다른 그림·글꼴·스타일시트)을 하나도 가져오지 않으며 누를 수도 없다. 잠긴 WebView 가 자바스크립트를 끄고 우리 호스트 밖의
 * 요청에 빈 404 를 주는 것과 겹쳐, 사용자 파일 하나가 할 수 있는 일이 '그림이 그려진다' 하나로 줄어든다. 쪽(HTML)은 우리가 쓴 것이고
 * 파일의 글은 한 글자도 들어가지 않는다([pageHtml]).
 *
 * ## 확대
 *
 * WebView 의 손가락 확대를 쓴다 — 벡터라 어느 배율에서도 다시 그려져 선명하다(비트맵을 키우는 사진과 다르다).
 */
internal object SvgSupport {

    /** SVG 하나의 상한. 그림은 렌더러 프로세스가 그리고, 거대한 지도 SVG 는 렌더러를 한참 붙잡는다. 마크다운 그림과 같은 값이다. */
    const val MAX_BYTES = 32L * 1024 * 1024

    /** 뿌리 요소를 찾느라 읽는 앞머리. XML 선언·주석·DOCTYPE 뒤에 온다. */
    const val HEAD_BYTES = 64 * 1024

    /** 너무 길쭉한 비율은 자른다 — 1:100 을 넘는 그림은 화면에 맞추면 어차피 선 하나다. */
    private const val MAX_ASPECT = 100.0

    /** 그림 뷰어가 WebView 로 그릴 이름인가. */
    fun isSvgName(name: String): Boolean = MimeResolver.extensionOf(name) in EXTENSIONS

    private val EXTENSIONS = setOf("svg", "svgz")

    /** 뿌리 `<svg>` 의 치수. 모르는 것은 null 이다. */
    data class Root(val width: Double?, val height: Double?, val viewBoxWidth: Double?, val viewBoxHeight: Double?) {
        /** 폭 ÷ 높이. 치수가 둘 다 있으면 그것, 아니면 `viewBox`, 둘 다 없으면 null(화면 전체에 맞춘다). */
        val aspect: Double?
            get() {
                val a = when {
                    width != null && height != null && width > 0 && height > 0 -> width / height
                    viewBoxWidth != null && viewBoxHeight != null && viewBoxWidth > 0 && viewBoxHeight > 0 ->
                        viewBoxWidth / viewBoxHeight
                    else -> return null
                }
                return a.coerceIn(1 / MAX_ASPECT, MAX_ASPECT)
            }
    }

    /**
     * 앞머리 글에서 뿌리 `<svg …>` 를 찾아 치수를 읽는다. 없으면 null — SVG 가 아니다(확장자만 SVG 인 파일).
     *
     * **XML 을 해석하지 않는다.** 시작 태그 하나의 속성 셋만 필요하고, 해석기를 세우면 외부 실체·DOCTYPE 을 따로 막아야 한다. 주석과
     * 처리 지시(`<?…?>`)·DOCTYPE 은 건너뛴다 — 그 안의 `<svg` 를 뿌리로 읽지 않으려는 것이다.
     *
     * **앞머리를 한 번만 훑는다**(정규식도 쓰지 않는다). 처음 판은 속성을 정규식으로 찾아, 이름 글자만 6만 개 이어진 시작 태그 하나에
     * 53초가 걸렸다(실패할 때마다 짧은 이름으로 되짚었다). 함정 표의 '되풀이될 수 있는 것의 탐색 범위를 묶어라' 다.
     */
    fun rootOf(head: String): Root? {
        var i = 0
        val n = head.length
        while (i < n) {
            val lt = head.indexOf('<', i)
            if (lt < 0) return null
            when {
                head.startsWith("<!--", lt) -> {
                    val end = head.indexOf("-->", lt + 4)
                    if (end < 0) return null
                    i = end + 3
                }
                head.startsWith("<?", lt) -> {
                    val end = head.indexOf("?>", lt + 2)
                    if (end < 0) return null
                    i = end + 2
                }
                head.startsWith("<!", lt) -> i = declarationEnd(head, lt + 2) ?: return null
                else -> {
                    // 첫 요소다. 그것이 svg(접두어가 붙을 수 있다 — `svg:svg`)가 아니면 SVG 가 아니다.
                    var nameEnd = lt + 1
                    while (nameEnd < n && !head[nameEnd].isWhitespace() && head[nameEnd] != '>' && head[nameEnd] != '/') nameEnd++
                    if (head.substring(lt + 1, nameEnd).substringAfter(':') != "svg") return null
                    val tagEnd = tagEnd(head, nameEnd) ?: return null
                    return attributesOf(head, nameEnd, tagEnd)
                }
            }
        }
        return null
    }

    /**
     * `<!DOCTYPE …>` 같은 선언의 끝 다음 자리. 내부 부분집합(`[…]`) 안의 `>` 와 따옴표 안의 글자(`"<svg …>"`)는 끝으로 보지 않는다.
     * `]` 와 `>` 사이의 공백도 받는다 — XML 이 허락한다(`] >` 에서 처음 판이 멀쩡한 SVG 를 'SVG 가 아니다' 로 보냈다).
     */
    private fun declarationEnd(s: String, from: Int): Int? {
        var quote = 0.toChar()
        var subset = false
        for (i in from until s.length) {
            val c = s[i]
            when {
                quote.code != 0 -> if (c == quote) quote = 0.toChar()
                c == '"' || c == '\'' -> quote = c
                c == '[' && !subset -> subset = true
                c == ']' && subset -> subset = false
                c == '>' && !subset -> return i + 1
            }
        }
        return null
    }

    /** 시작 태그의 끝(`>`). 따옴표 안의 `>` 는 건너뛴다. */
    private fun tagEnd(s: String, from: Int): Int? {
        var quote = 0.toChar()
        for (i in from until s.length) {
            val c = s[i]
            if (quote.code != 0) {
                if (c == quote) quote = 0.toChar()
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
        }
        return null
    }

    /**
     * 시작 태그 `s[from, to)` 의 속성 가운데 치수 셋(`width`·`height`·`viewBox`)을 읽는다. 앞에서 뒤로 한 번 지나간다 — 이름을 읽고,
     * `=` 와 따옴표 값이 이어지면 값을 가진다. 따옴표 안은 건너뛴다(값 속의 `width="…"` 를 속성으로 읽지 않는다).
     */
    private fun attributesOf(s: String, from: Int, to: Int): Root {
        val map = HashMap<String, String>()
        var i = from
        while (i < to) {
            val c = s[i]
            when {
                c == '"' || c == '\'' -> {
                    val end = s.indexOf(c, i + 1)
                    if (end < 0 || end >= to) break
                    i = end + 1
                }
                isNameStart(c) -> {
                    val start = i
                    while (i < to && isNameChar(s[i])) i++
                    // 찾는 이름은 일곱 자 이하다 — 긴 이름은 글로 만들지도 않는다.
                    val name = if (i - start <= 7) s.substring(start, i) else null
                    var j = i
                    while (j < to && s[j].isWhitespace()) j++
                    if (j < to && s[j] == '=') {
                        j++
                        while (j < to && s[j].isWhitespace()) j++
                        if (j < to && (s[j] == '"' || s[j] == '\'')) {
                            val end = s.indexOf(s[j], j + 1)
                            if (end < 0 || end >= to) break
                            when (name) {
                                "width", "height" -> map.putIfAbsent(name, s.substring(j + 1, end))
                                "viewBox", "viewbox" -> map.putIfAbsent("viewbox", s.substring(j + 1, end))
                            }
                            i = end + 1
                        } else {
                            i = j
                        }
                    }
                }
                else -> i++
            }
        }
        val box = map["viewbox"]?.let(::viewBoxOf)
        return Root(lengthOf(map["width"]), lengthOf(map["height"]), box?.first, box?.second)
    }

    private fun isNameStart(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c == '_' || c == ':'

    private fun isNameChar(c: Char): Boolean = isNameStart(c) || c in '0'..'9' || c == '-' || c == '.'

    /**
     * 값 하나가 이보다 길 리 없다 — 묶어 두면 아래 정규식이 입력 길이에 끌려가지 않는다(예: 폭 칸에 숫자 3만 2천 자와 틀린
     * 글자 하나를 넣으면 26초였다. 숫자를 되짚으며 실패한다).
     */
    private const val MAX_LENGTH_CHARS = 32
    private const val MAX_VIEWBOX_CHARS = 256

    private val LENGTH = Regex("""^([+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)\s*([A-Za-z%]*)$""")
    private val VIEWBOX_SEPARATOR = Regex("""[\s,]+""")

    /** `viewBox="minX minY width height"`(쉼표·공백 섞임)의 폭과 높이. */
    private fun viewBoxOf(v: String): Pair<Double, Double>? {
        if (v.length > MAX_VIEWBOX_CHARS) return null
        val parts = v.trim().split(VIEWBOX_SEPARATOR)
        if (parts.size != 4) return null
        val w = parts[2].toDoubleOrNull() ?: return null
        val h = parts[3].toDoubleOrNull() ?: return null
        return if (w.isFinite() && h.isFinite() && w > 0 && h > 0) w to h else null
    }

    /**
     * 길이를 CSS 화소로. 단위가 없거나 `px` 면 그대로, 절대 단위는 CSS 의 비율로 바꾼다. `%`·`em`·`ex` 처럼 기준을 모르는 것은
     * null — 그때는 `viewBox` 의 비율을 쓴다.
     */
    internal fun lengthOf(v: String?): Double? {
        val s = v?.trim() ?: return null
        if (s.length > MAX_LENGTH_CHARS) return null
        val m = LENGTH.find(s) ?: return null
        val num = m.groupValues[1].toDoubleOrNull() ?: return null
        val px = when (m.groupValues[2].lowercase()) {
            "", "px" -> num
            "pt" -> num * 4 / 3
            "pc" -> num * 16
            "mm" -> num * 96 / 25.4
            "cm" -> num * 96 / 2.54
            "in" -> num * 96
            else -> return null
        }
        return px.takeIf { it.isFinite() && it > 0 }
    }

    /**
     * 우리가 쓰는 쪽. 파일의 글은 들어가지 않는다 — 그림 주소([imagePath])와 비율([aspect])뿐이다.
     *
     * 비율을 알면 그림 상자를 화면에 맞춘 크기로 정하고 **그 상자만** 희게 칠한다. SVG 는 대개 바탕이 투명하고 검은 선으로 그려져
     * 뷰어의 검은 바탕 위에서는 보이지 않는다 — 크롬이 SVG 파일을 흰 쪽에 그리는 것과 같은 판단이다. 비율을 모르면 화면 전체를
     * 상자로 두고(`object-fit:contain`) 전체를 희게 칠한다.
     */
    fun pageHtml(imagePath: String, aspect: Double?): String {
        val box = if (aspect == null) {
            "width:100vw;height:100vh;object-fit:contain;"
        } else {
            // 비율은 [Root.aspect] 가 [MAX_ASPECT] 로 자른 값이라 지수 표기가 나오지 않는다(1e-2 .. 1e2).
            "width:min(100vw,calc(100vh*$aspect));height:min(100vh,calc(100vw/$aspect));"
        }
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=10,user-scalable=yes\">" +
            "<style>html,body{margin:0;height:100%;background:#000;}" +
            "body{display:flex;align-items:center;justify-content:center;}" +
            "img{display:block;background:#fff;$box}</style></head>" +
            "<body><img src=\"$imagePath\" alt=\"\"></body></html>"
    }

    /** 그릴 준비가 된 SVG — 파일과 그것이 gzip 인지(`svgz`), 그리고 뿌리의 치수. **풀린 바이트를 들고 있지 않는다**([open]). */
    class Prepared(val file: File, val gzip: Boolean, val root: Root) {
        /**
         * 내줄 바이트. WebView 의 다른 스레드에서 불린다. **그때 다시 열어 그때 푼다** — 처음 판은 풀린 바이트를 장마다 쥐어, 페이저가
         * 미리 세우는 서너 장의 svgz 가 저마다 32 MiB 까지 들 수 있었다. 다시 열 때도 [MAX_BYTES] 로 묶는다 — 그새 파일이
         * 바뀌어도 상한을 건너뛰지 못하고, 넘으면 그림이 거기서 끊긴다. 파일이 사라졌으면 [IOException] 이다(부르는 쪽이 404 로 바꾼다).
         */
        fun open(): InputStream = LimitedInputStream(if (gzip) gunzip(file) else FileInputStream(file), MAX_BYTES, "svgBytes")
    }

    /**
     * 파일을 읽어 그릴 준비를 한다 — 디스크 입출력과 압축 풀기다(부르는 쪽이 파싱 디스패처에서 부른다). SVG 가 아니거나(뿌리 `<svg>`
     * 가 없다) 상한을 넘거나 읽지 못하면 null — 그림 뷰어가 비트맵 디코더에 맡긴다(내용이 PNG 인 `.svg` 도 있다). 그것도 못 읽으면
     * '열 수 없다' 다.
     *
     * `svgz`(gzip 으로 싼 SVG)는 **앞머리로** 알아본다 — 확장자가 `.svg` 인 gzip 도 있다. 끝까지 풀어 크기를 [MAX_BYTES] 로 묶되(폭탄)
     * **앞머리만 남긴다**.
     */
    fun prepare(file: File): Prepared? = try {
        if (!file.isFile || file.length() !in 1..MAX_BYTES) {
            null
        } else {
            val gzip = FileInputStream(file).use { it.read() == 0x1F && it.read() == 0x8B }
            val head = if (gzip) inflatedHead(file) ?: return null else readHead(FileInputStream(file))
            rootOf(decodeHead(head))?.let { Prepared(file, gzip, it) }
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /** [input] 의 앞 [HEAD_BYTES] 를 읽고 닫는다. */
    private fun readHead(input: InputStream): ByteArray = input.use {
        val buf = ByteArray(HEAD_BYTES)
        var total = 0
        while (total < buf.size) {
            val n = it.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        buf.copyOf(total)
    }

    /** gzip 을 끝까지 풀어 크기를 잰다 — [MAX_BYTES] 를 넘으면 null(폭탄이거나 너무 크다). 앞 [HEAD_BYTES] 만 돌려준다. */
    private fun inflatedHead(file: File): ByteArray? = gunzip(file).use { input ->
        val head = ByteArray(HEAD_BYTES)
        var kept = 0
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return null
            if (kept < head.size) {
                val take = minOf(n, head.size - kept)
                System.arraycopy(buf, 0, head, kept, take)
                kept += take
            }
        }
        head.copyOf(kept)
    }

    /**
     * gzip 으로 읽는 스트림. **머리를 읽다 던지면 파일을 닫는다** — `GZIPInputStream` 의 생성자가 머리를 읽으므로 두 바이트짜리 파일
     * 하나가 서술자를 GC 까지 쥐게 할 수 있었다('생성자에서 자원을 열었으면 초기화 실패 시 스스로 닫아라').
     */
    private fun gunzip(file: File): InputStream {
        val raw = FileInputStream(file)
        try {
            return GZIPInputStream(raw)
        } catch (e: Throwable) {
            raw.close()
            throw e
        }
    }

    /**
     * 앞머리를 글로. 속성 셋만 읽으므로 ASCII 가 살면 된다 — UTF-8·ISO-8859 계열은 `ISO_8859_1` 로 바이트를 그대로 글자로 옮긴다.
     * UTF-16(BOM)은 바이트가 둘씩이라 그 인코딩으로 푼다.
     */
    internal fun decodeHead(head: ByteArray): String = when {
        head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte() -> String(head, Charsets.UTF_16LE)
        head.size >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte() -> String(head, Charsets.UTF_16BE)
        else -> String(head, Charsets.ISO_8859_1)
    }
}
