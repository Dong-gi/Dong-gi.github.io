package io.github.donggi.iroiroviewer.format.hwpx

/**
 * 패키지 안의 이름 다루기.
 *
 * ## 이름은 한 모양
 *
 * 바깥으로 나가는 이름은 **앞의 `/` 가 없고 퍼센트 인코딩을 푼** 모양이다(`BinData/image1.png`) —
 * `FlowPackage` 의 약속이다. 찾을 때는 대소문자를 가리지 않는다: 한글이 적는 매니페스트의 `href` 와
 * ZIP 항목 이름은 같게 나오지만(`BinData/image3.BMP`), 손으로 고친 패키지는 어긋나기 쉽다.
 *
 * `format:opc` 의 `OpcNames` 와 같은 일을 하지만 그 모듈을 볼 수 없어(HWPX 는 OPC 가 아니다) 따로 둔다.
 */
internal object HwpxNames {

    /** 찾기용 열쇠. */
    fun key(name: String): String = normalize(name).lowercase()

    /** 바깥에 내보내는 모양. 앞의 `/` 를 떼고 역슬래시를 슬래시로, 퍼센트를 푼다. */
    fun normalize(name: String): String = percentDecode(name.replace('\\', '/').removePrefix("/"))

    /** 이름이 든 폴더(`Contents/content.hpf` → `Contents/`). 뿌리면 빈 문자열. */
    fun dirOf(name: String): String {
        val n = normalize(name)
        val slash = n.lastIndexOf('/')
        return if (slash < 0) "" else n.substring(0, slash + 1)
    }

    /**
     * [base] 폴더 기준으로 [href] 를 푼다. `..` 로 뿌리를 벗어나면 null — 패키지 밖을 가리키는 것은
     * 우리가 내줄 것이 없고, 조용히 뿌리로 되돌리면 **다른 항목**을 가리키게 된다.
     */
    fun resolve(base: String, href: String): String? {
        val t = href.trim().replace('\\', '/')
        if (t.isEmpty()) return null
        // 스킴이 붙은 주소(바깥 파일)는 이름이 아니다.
        if (SCHEME.containsMatchIn(t)) return null
        val start = if (t.startsWith("/")) "" else base
        val segments = ArrayList<String>()
        for (seg in (start + t.removePrefix("/")).split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.size - 1)
                else -> segments.add(seg)
            }
        }
        if (segments.isEmpty()) return null
        return percentDecode(segments.joinToString("/"))
    }

    /**
     * 퍼센트 인코딩을 UTF-8 로 푼다. **`+` 를 공백으로 바꾸지 않는다**(`URLDecoder` 는 폼 인코딩이라
     * 바꾼다 — CLAUDE.md 함정 표). 잘못된 `%` 는 글자 그대로 둔다.
     */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = StringBuilder(s.length)
        val pending = java.io.ByteArrayOutputStream()
        fun flush() {
            if (pending.size() > 0) {
                out.append(String(pending.toByteArray(), Charsets.UTF_8))
                pending.reset()
            }
        }
        var i = 0
        while (i < s.length) {
            val v = if (s[i] == '%') hex(s, i + 1) else -1
            if (v >= 0) {
                pending.write(v)
                i += 3
            } else {
                flush()
                out.append(s[i])
                i++
            }
        }
        flush()
        return out.toString()
    }

    private fun hex(s: String, at: Int): Int {
        if (at + 1 >= s.length) return -1
        val hi = Character.digit(s[at], 16)
        val lo = Character.digit(s[at + 1], 16)
        return if (hi < 0 || lo < 0) -1 else hi * 16 + lo
    }

    /** `http:`·`file:`·`C:` 처럼 콜론 앞이 스킴 모양인 것. */
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}

/**
 * 본문에 적는 `id` 들. **`[a-z0-9-]` 만 쓴다** — docx 의 `DocxIds` 와 같은 규칙(되돌릴 수 있게 옮긴다).
 * 책갈피 이름은 문서가 적는 값이라 그대로 쓰면 URL 조각으로 건너가는 길에서 인코딩 문제가 생긴다.
 */
internal object HwpxIds {

    /** 제목 문단. 구역과 최상위 블록 번호라 문서 안에서 하나뿐이다. */
    fun heading(section: Int, block: Int): String = "h-$section-$block"

    /** 책갈피. 영소문자·숫자는 그대로, 나머지 바이트는 `-` 와 16진 두 자리. */
    fun bookmark(name: String): String {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size + 8).append("bm-")
        val limit = minOf(bytes.size, MAX_NAME_BYTES)
        for (i in 0 until limit) {
            val c = bytes[i].toInt() and 0xFF
            if (c in 'a'.code..'z'.code || c in '0'.code..'9'.code) {
                sb.append(c.toChar())
            } else {
                sb.append('-').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
        if (bytes.size > MAX_NAME_BYTES) sb.append("-h").append(Integer.toHexString(name.hashCode()))
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"
    private const val MAX_NAME_BYTES = 120
}
