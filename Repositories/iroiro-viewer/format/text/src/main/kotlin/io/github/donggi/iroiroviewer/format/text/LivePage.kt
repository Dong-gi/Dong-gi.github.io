package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding

/**
 * HTML 미리보기의 **스크립트 실행**(사용자가 그 쪽에서 고른다). 위생을 거친 쪽([HtmlPreview])과 달리 **파일의 바이트 그대로**를
 * 스크립트를 켠 WebView 에 내준다 — 메일에 붙어 오는 보안 메일(암호를 넣으면 스크립트가 풀어 보여 준다)처럼 스크립트가 곧 내용인
 * 쪽이 있다. 앞 패스도 위생기도 거치지 않으므로 여기서 정하는 것은 둘뿐이다 — 쪽을 **어떤 인코딩으로 읽힐지**([transportLabel])와
 * 쪽이 가리키는 파일을 **어떤 형식으로 내줄지**([PreviewTypes.ofLive]). 막는 일은 화면(잠긴 WebView 의 가로채기, 쪽마다 따로인 출처)과
 * 파일 고르기(`PreviewFiles` — 그 파일의 폴더와 그 아래, 실제 파일로 푼 경로)가 한다.
 *
 * ## 쪽의 주소
 *
 * 쪽은 [PAGE_PATH] — 이름이 빈 경로(`f/`)에 둔다. 상대 주소가 그 폴더를 기준으로 풀리고(`img/a.png` → `f/img/a.png`), 이름이 빈 파일은
 * 있을 수 없으니 **같은 폴더의 진짜 파일과 겹치지 않는다**(쪽 이름을 `index.html` 로 두면 옆의 `index.html` 을 가리키는 링크가 이 쪽을
 * 받는다). 위로 올라가는 주소(`../x`)는 WebView 가 풀어 `f/` 밖이 되고, 화면이 404 를 준다.
 */
object LivePage {

    /** 쪽 자신의 경로(`WebHost.pathOf` 의 모양 — 앞의 `/` 가 없다). */
    const val PAGE_PATH = "f/"

    /**
     * 스크립트를 실행하는 쪽의 크기 상한. 위생을 거친 쪽(8 MiB — 우리가 글로 들고 바꾸는 양)보다 크다 — 이 쪽은 디스크에서 그대로 흘려
     * 내주므로 우리 메모리를 쓰지 않는다. 첨부를 품은 보안 메일이 8 MiB 를 넘을 수 있다. 같은 폴더의 파일 하나의 상한과 같다.
     */
    const val MAX_PAGE_BYTES: Long = 32L * 1024 * 1024

    /** 화면이 받은 경로가 쪽의 폴더 안이면 그 폴더 기준의 상대 경로, 쪽 자신이거나 밖이면 null. */
    fun relativeOf(path: String): String? =
        if (path.length > PAGE_PATH.length && path.startsWith(PAGE_PATH)) path.substring(PAGE_PATH.length) else null

    /**
     * WebView 에 **전송 쪽의 인코딩**으로 줄 이름(`WebResourceResponse` 의 encoding). null 이면 주지 않는다 — 그러면 브라우저가 BOM 과
     * 문서의 `<meta charset>` 으로 정하고, 아무것도 없으면 제 기본값(UTF-8)을 쓴다.
     *
     * 전송 쪽의 인코딩은 문서의 선언을 **이기므로** 함부로 주면 쓴 사람이 적은 것을 덮는다. 그래서 줄 때를 좁힌다.
     *
     * 1. BOM 이 있으면 주지 않는다 — 브라우저는 BOM 을 무엇보다 먼저 따른다. **사용자가 고른 인코딩도 이기지 못한다**(뷰어의 원문은
     *    고른 대로 읽히지만 이 쪽은 BOM 대로다).
     * 2. 사용자가 뷰어에서 인코딩을 **골랐으면** 그것 — 브라우저의 '인코딩' 메뉴와 같다. 문서의 선언보다 사람의 선택이 이긴다.
     * 3. 문서가 앞 1024 바이트 안에서 브라우저가 아는 이름으로 인코딩을 선언했으면 주지 않는다([declaredCharset]).
     * 4. 우리 판정이 UTF-8 이면 주지 않는다 — 브라우저의 기본값과 같고, 브라우저는 우리보다 넓게 훑는다(크롬은 `<head>` 안이면 1024
     *    바이트 뒤의 `<meta>` 도 읽는다). 앞 1 MiB 가 ASCII 뿐인 EUC-KR 쪽은 우리 판정이 UTF-8 이라, 주면 늦게 적힌 선언을 덮었다.
     * 5. 나머지는 우리 판정 — 선언 없는 옛 한국어 쪽(CP949)에는 브라우저의 기본값(UTF-8)보다 맞는다.
     *
     * ## XML 로 내주는 쪽([xml] — `.xhtml`)은 3 이 다르다
     *
     * 크롬은 XML 로 읽는 쪽의 `<meta charset>` 을 보지 않는다 — 문서의 선언은 **XML 선언**(`<?xml … encoding="…"?>`)이다
     * ([xmlDeclaredEncoding]). 그래서 BOM > 사용자가 고른 것 > XML 선언 > 우리 판정이다. 예전에는 HTML 의 규칙(`<meta>`)으로 정해,
     * XML 선언 없이 `<meta charset="euc-kr">` 만 적은 EUC-KR `.xhtml` 에 우리가 맞게 판정하고도 인코딩을 주지 않았다 — 크롬은 그것을
     * XML 의 기본값(UTF-8)으로 읽어 한글이 깨졌다.
     */
    fun transportLabel(
        chosen: TextEncoding?,
        detected: TextEncoding,
        hasBom: Boolean,
        head: ByteArray,
        headLength: Int,
        known: (String) -> Boolean = ::isKnownLabel,
        xml: Boolean = false,
    ): String? = when {
        hasBom -> null
        chosen != null -> labelOf(chosen)
        xml && xmlDeclaredEncoding(head, headLength, known) != null -> null
        !xml && declaredCharset(head, headLength, known) != null -> null
        detected == TextEncoding.UTF_8 -> null
        else -> labelOf(detected)
    }

    /** 이 이름의 쪽을 XML 로 내주는가 — 화면이 쪽의 형식을 정하는 표(`PreviewTypes.ofLive`)와 한 벌이다(`.xhtml`). */
    fun servedAsXml(name: String): Boolean = PreviewTypes.ofLive(name)?.let { it.endsWith("+xml") || it.endsWith("/xml") } == true

    /**
     * 문서 맨 앞의 XML 선언이 적은 인코딩 이름(소문자, 앞뒤 공백을 뗀 것). 선언이 없거나, `encoding` 이 없거나, 브라우저가 모르는 이름이면
     * null — 그러면 XML 의 기본값(UTF-8)이 아니라 우리 판정을 준다(위 차례).
     *
     * XML 1.0 의 선언은 **문서의 첫 바이트**에서 시작한다(`<?xml` 뒤에 공백). 순서는 `version` → `encoding` → `standalone` 이지만 이름으로
     * 찾는다. 선언이 [PRESCAN_BYTES] 안에서 끝나지 않으면 없는 것으로 본다. UTF-16 처럼 ASCII 가 아닌 모양(BOM 없는 UTF-16)은 보지 않는다.
     */
    fun xmlDeclaredEncoding(bytes: ByteArray, length: Int, known: (String) -> Boolean = ::isKnownLabel): String? {
        val n = minOf(length, bytes.size, PRESCAN_BYTES)
        val decl = "<?xml"
        if (n < decl.length + 1) return null
        for (i in decl.indices) if (bytes[i] != decl[i].code.toByte()) return null
        if (!isSpace(bytes[decl.length])) return null
        var end = -1
        for (i in decl.length until n - 1) {
            if (bytes[i] == QUESTION && bytes[i + 1] == GT) {
                end = i
                break
            }
        }
        if (end < 0) return null
        val text = String(bytes, 0, end, Charsets.ISO_8859_1)
        val m = XML_ENCODING.find(text) ?: return null
        val label = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim(*SPACES).lowercase()
        return label.takeIf { it.isNotEmpty() && known(it) }
    }

    /** XML 선언의 `encoding` 의사 속성. 값은 따옴표 안이다(XML 1.0 의 `EncodingDecl`). */
    private val XML_ENCODING = Regex("""\sencoding\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    /**
     * 브라우저(WHATWG Encoding 표)가 알아듣는 이름. `TextEncoding.label` 을 그대로 쓰지 않는다 — `CP949` 는 그 표에 없는 이름이다
     * (CP949 는 그 표의 `euc-kr` 이다 — 표의 EUC-KR 이 곧 windows-949 다).
     */
    fun labelOf(encoding: TextEncoding): String = when (encoding) {
        TextEncoding.UTF_8 -> "utf-8"
        TextEncoding.UTF_16LE -> "utf-16le"
        TextEncoding.UTF_16BE -> "utf-16be"
        TextEncoding.CP949 -> "euc-kr"
        TextEncoding.SHIFT_JIS -> "shift_jis"
        TextEncoding.GB18030 -> "gb18030"
        TextEncoding.BIG5 -> "big5"
        TextEncoding.LATIN1 -> "iso-8859-1"
    }

    /** 문서의 선언을 훑는 범위. 명세의 '미리 훑기' 가 보는 길이다. */
    const val PRESCAN_BYTES = 1024

    /**
     * 문서가 앞 [PRESCAN_BYTES] 바이트 안에서 선언한 인코딩의 이름(소문자, 앞뒤 공백을 뗀 것), 없거나 알아볼 수 없으면 null.
     *
     * HTML 명세의 '바이트 흐름을 미리 훑어 인코딩을 정하기' 를 옮겼다 — 주석(`<!-- -->`)을 건너뛰고, 다른 태그의 속성 안에 든
     * `<meta` 에 속지 않고, `http-equiv="content-type"` 없이 `content` 만 적은 것은 받지 않는다. [known] 이 모르는 이름은 명세의
     * '실패' 와 같다(다음 `<meta>` 를 본다). 한 번 훑고 끝난다 — 되돌아가 다시 읽는 자리가 없다.
     */
    fun declaredCharset(bytes: ByteArray, length: Int, known: (String) -> Boolean = ::isKnownLabel): String? =
        Prescan(bytes, minOf(length, bytes.size, PRESCAN_BYTES), known).run()

    /**
     * 브라우저가 알아들을 이름인가 — **브라우저의 표(WHATWG Encoding 표준의 이름표)로** 본다. 처음에는 자바가 아는 이름으로 가늠했는데,
     * 자바는 `cp949`·`ms949`·`x-windows-949` 를 알고 크롬은 모른다 — 그런 선언을 '문서가 말했다' 로 보고 입을 다물면 크롬은 선언을 버리고
     * 기본값(UTF-8)으로 읽어 한글이 깨졌다. 모르는 이름은 선언이 없는 것으로 보고 우리 판정을 준다(위의 차례).
     *
     * 표는 전부가 아니다 — 브라우저가 '대체 문자' 로만 읽는 이름(`iso-2022-kr` 등)은 일부러 뺐다(선언대로 두면 글이 전부 �이 된다).
     * 빠진 이름이 있어도 틀리는 쪽은 우리 판정을 주는 쪽이라 해가 작다.
     */
    fun isKnownLabel(label: String): Boolean = label in WHATWG_LABELS

    private val WHATWG_LABELS: Set<String> = setOf(
        // UTF-8
        "unicode-1-1-utf-8", "unicode11utf8", "unicode20utf8", "utf-8", "utf8", "x-unicode20utf8",
        // 한국어 — 표의 EUC-KR 은 곧 windows-949 다
        "cseuckr", "csksc56011987", "euc-kr", "iso-ir-149", "korean", "ks_c_5601-1987", "ks_c_5601-1989", "ksc5601", "ksc_5601",
        "windows-949",
        // 일본어
        "csshiftjis", "ms932", "ms_kanji", "shift-jis", "shift_jis", "sjis", "windows-31j", "x-sjis",
        "cseucpkdfmtjapanese", "euc-jp", "x-euc-jp", "csiso2022jp", "iso-2022-jp",
        // 중국어
        "chinese", "csgb2312", "csiso58gb231280", "gb2312", "gb_2312", "gb_2312-80", "gbk", "iso-ir-58", "x-gbk", "gb18030",
        "big5", "big5-hkscs", "cn-big5", "csbig5", "x-x-big5",
        // UTF-16
        "unicodefffe", "utf-16be", "csunicode", "iso-10646-ucs-2", "ucs-2", "unicode", "unicodefeff", "utf-16", "utf-16le",
        // 서유럽(windows-1252)
        "ansi_x3.4-1968", "ascii", "cp1252", "cp819", "csisolatin1", "ibm819", "iso-8859-1", "iso-ir-100", "iso8859-1", "iso88591",
        "iso_8859-1", "iso_8859-1:1987", "l1", "latin1", "us-ascii", "windows-1252", "x-cp1252",
        // 그 밖의 한 바이트 표
        "866", "cp866", "csibm866", "ibm866",
        "csisolatin2", "iso-8859-2", "iso-ir-101", "iso8859-2", "iso88592", "iso_8859-2", "iso_8859-2:1987", "l2", "latin2",
        "csisolatin3", "iso-8859-3", "iso-ir-109", "iso8859-3", "iso88593", "iso_8859-3", "iso_8859-3:1988", "l3", "latin3",
        "csisolatin4", "iso-8859-4", "iso-ir-110", "iso8859-4", "iso88594", "iso_8859-4", "iso_8859-4:1988", "l4", "latin4",
        "csisolatincyrillic", "cyrillic", "iso-8859-5", "iso-ir-144", "iso8859-5", "iso88595", "iso_8859-5", "iso_8859-5:1988",
        "arabic", "asmo-708", "csiso88596e", "csiso88596i", "csisolatinarabic", "ecma-114", "iso-8859-6", "iso-8859-6-e",
        "iso-8859-6-i", "iso-ir-127", "iso8859-6", "iso88596", "iso_8859-6", "iso_8859-6:1987",
        "csisolatingreek", "ecma-118", "elot_928", "greek", "greek8", "iso-8859-7", "iso-ir-126", "iso8859-7", "iso88597",
        "iso_8859-7", "iso_8859-7:1987", "sun_eu_greek",
        "csiso88598e", "csisolatinhebrew", "hebrew", "iso-8859-8", "iso-8859-8-e", "iso-ir-138", "iso8859-8", "iso88598",
        "iso_8859-8", "iso_8859-8:1988", "visual", "csiso88598i", "iso-8859-8-i", "logical",
        "csisolatin6", "iso-8859-10", "iso-ir-157", "iso8859-10", "iso885910", "l6", "latin6",
        "iso-8859-13", "iso8859-13", "iso885913", "iso-8859-14", "iso8859-14", "iso885914",
        "csisolatin9", "iso-8859-15", "iso8859-15", "iso885915", "iso_8859-15", "l9", "iso-8859-16",
        "cskoi8r", "koi", "koi8", "koi8-r", "koi8_r", "koi8-ru", "koi8-u",
        "csmacintosh", "mac", "macintosh", "x-mac-roman", "x-mac-cyrillic", "x-mac-ukrainian",
        "dos-874", "iso-8859-11", "iso8859-11", "iso885911", "tis-620", "windows-874",
        "cp1250", "windows-1250", "x-cp1250", "cp1251", "windows-1251", "x-cp1251", "cp1253", "windows-1253", "x-cp1253",
        "cp1254", "csisolatin5", "iso-8859-9", "iso-ir-148", "iso8859-9", "iso88599", "iso_8859-9", "iso_8859-9:1989", "l5",
        "latin5", "windows-1254", "x-cp1254", "cp1255", "windows-1255", "x-cp1255", "cp1256", "windows-1256", "x-cp1256",
        "cp1257", "windows-1257", "x-cp1257", "cp1258", "windows-1258", "x-cp1258",
        "x-user-defined",
    )

    /** 명세의 알고리즘 그대로. 상태는 [pos] 하나다. */
    private class Prescan(private val b: ByteArray, private val n: Int, private val known: (String) -> Boolean) {
        private var pos = 0

        fun run(): String? {
            while (pos < n) {
                when {
                    startsWith("<!--") -> {
                        // 앞의 `<!` 다음부터 `-->` 를 찾는다 — 명세는 `<!-->` 의 두 `-` 도 끝으로 센다.
                        val end = indexOf("-->", pos + 2)
                        if (end < 0) return null
                        pos = end + 3
                        continue
                    }
                    startsWithIgnoreCase("<meta") && pos + 5 < n && isSpaceOrSlash(b[pos + 5]) -> {
                        pos += 5
                        meta()?.let { return it }
                    }
                    pos + 1 < n && b[pos] == LT && (isLetter(b[pos + 1]) || (b[pos + 1] == SLASH && pos + 2 < n && isLetter(b[pos + 2]))) -> {
                        // 다른 태그. 이름을 건너뛰고 속성을 모두 읽어 버린다 — 속성 값 안의 `<meta` 에 속지 않는다.
                        while (pos < n && !isSpace(b[pos]) && b[pos] != GT) pos++
                        while (attribute() != null) Unit
                    }
                    pos + 1 < n && b[pos] == LT && (b[pos + 1] == BANG || b[pos + 1] == SLASH || b[pos + 1] == QUESTION) -> {
                        val gt = indexOf(">", pos + 1)
                        if (gt < 0) return null
                        pos = gt
                    }
                }
                pos++
            }
            return null
        }

        /** `<meta` 의 속성들. 인코딩을 정했으면 그 이름. */
        private fun meta(): String? {
            val seen = HashSet<String>()
            var gotPragma = false
            var needPragma: Boolean? = null
            var charset: String? = null
            var failed = false
            while (true) {
                val (name, value) = attribute() ?: break
                if (!seen.add(name)) continue
                when (name) {
                    "http-equiv" -> if (value == "content-type") gotPragma = true
                    "content" -> if (charset == null && !failed) {
                        val label = fromContent(value)
                        if (label != null) {
                            if (known(label)) charset = label else failed = true
                            needPragma = true
                        }
                    }
                    "charset" -> {
                        val label = value.trim(*SPACES)
                        charset = label.takeIf { known(it) }
                        failed = charset == null
                        needPragma = false
                    }
                }
            }
            // 태그가 훑는 범위 안에서 끝나지 않았다 — 명세는 거기서 훑기를 그만둔다(반쯤 읽은 선언을 믿지 않는다).
            if (pos >= n) return null
            return when {
                needPragma == null -> null
                needPragma == true && !gotPragma -> null
                failed || charset == null -> null
                else -> charset
            }
        }

        /** 명세의 '속성 하나 읽기'. 속성이 없으면(`>` 이거나 끝) null. 이름과 값은 ASCII 대문자만 소문자로 바뀐다. */
        private fun attribute(): Pair<String, String>? {
            while (pos < n && (isSpace(b[pos]) || b[pos] == SLASH)) pos++
            if (pos >= n || b[pos] == GT) return null
            val name = StringBuilder()
            val value = StringBuilder()
            // 이름
            while (true) {
                if (pos >= n) return null
                val c = b[pos]
                when {
                    c == EQ && name.isNotEmpty() -> {
                        pos++
                        return valueOf(name, value)
                    }
                    isSpace(c) -> break
                    c == SLASH || c == GT -> return name.toString() to ""
                    else -> name.append(lower(c))
                }
                pos++
            }
            // 이름 뒤의 빈칸
            while (pos < n && isSpace(b[pos])) pos++
            if (pos >= n || b[pos] != EQ) return name.toString() to ""
            pos++
            return valueOf(name, value)
        }

        private fun valueOf(name: StringBuilder, value: StringBuilder): Pair<String, String>? {
            while (pos < n && isSpace(b[pos])) pos++
            if (pos >= n) return null
            val c = b[pos]
            if (c == QUOTE || c == APOSTROPHE) {
                pos++
                while (true) {
                    if (pos >= n) return null
                    if (b[pos] == c) {
                        pos++
                        return name.toString() to value.toString()
                    }
                    value.append(lower(b[pos]))
                    pos++
                }
            }
            if (c == GT) return name.toString() to ""
            value.append(lower(c))
            pos++
            while (true) {
                if (pos >= n) return null
                val d = b[pos]
                if (isSpace(d) || d == GT) return name.toString() to value.toString()
                value.append(lower(d))
                pos++
            }
        }

        private fun startsWith(s: String): Boolean {
            if (pos + s.length > n) return false
            for (i in s.indices) if (b[pos + i] != s[i].code.toByte()) return false
            return true
        }

        private fun startsWithIgnoreCase(s: String): Boolean {
            if (pos + s.length > n) return false
            for (i in s.indices) if (lower(b[pos + i]) != s[i]) return false
            return true
        }

        private fun indexOf(s: String, from: Int): Int {
            var i = from
            while (i + s.length <= n) {
                var hit = true
                for (k in s.indices) if (b[i + k] != s[k].code.toByte()) { hit = false; break }
                if (hit) return i
                i++
            }
            return -1
        }
    }

    /**
     * 명세의 'meta 요소에서 인코딩 꺼내기'(`content="text/html; charset=euc-kr"`). 찾으면 이름(소문자), 없으면 null. 한 번 훑는다 —
     * `charset` 뒤에 `=` 가 없으면 그 뒤에서 다시 찾는다.
     */
    internal fun fromContent(s: String): String? {
        var from = 0
        while (true) {
            val at = s.indexOf("charset", from, ignoreCase = true)
            if (at < 0) return null
            var i = at + "charset".length
            while (i < s.length && s[i] in SPACES) i++
            if (i >= s.length || s[i] != '=') {
                from = i
                continue
            }
            i++
            while (i < s.length && s[i] in SPACES) i++
            if (i >= s.length) return null
            val q = s[i]
            if (q == '"' || q == '\'') {
                val end = s.indexOf(q, i + 1)
                return if (end < 0) null else s.substring(i + 1, end).trim(*SPACES).lowercase()
            }
            var end = i
            while (end < s.length && s[end] !in SPACES && s[end] != ';') end++
            return s.substring(i, end).lowercase()
        }
    }

    private val SPACES = charArrayOf('\t', '\n', '\u000C', '\r', ' ')
    private const val LT: Byte = 0x3C
    private const val GT: Byte = 0x3E
    private const val SLASH: Byte = 0x2F
    private const val BANG: Byte = 0x21
    private const val QUESTION: Byte = 0x3F
    private const val EQ: Byte = 0x3D
    private const val QUOTE: Byte = 0x22
    private const val APOSTROPHE: Byte = 0x27

    private fun isSpace(c: Byte): Boolean = c == 0x09.toByte() || c == 0x0A.toByte() || c == 0x0C.toByte() ||
        c == 0x0D.toByte() || c == 0x20.toByte()

    private fun isSpaceOrSlash(c: Byte): Boolean = isSpace(c) || c == SLASH

    private fun isLetter(c: Byte): Boolean = c in 0x41..0x5A || c in 0x61..0x7A

    /** ASCII 대문자만 소문자로. 그 밖의 바이트는 그 값의 글자 — 인코딩 이름에는 ASCII 만 들어오므로 뜻이 없다(명세와 같다). */
    private fun lower(c: Byte): Char {
        val v = c.toInt() and 0xFF
        return if (v in 0x41..0x5A) (v + 0x20).toChar() else v.toChar()
    }
}
