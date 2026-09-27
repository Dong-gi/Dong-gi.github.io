package io.github.donggi.iroiroviewer.format.opc

/**
 * OPC 부분(part) 이름 다루기 — ECMA-376 Part 2 §6.2.2·§9.3.
 *
 * ## 이름을 한 가지 모양으로만 다룬다
 *
 * 이 모듈 밖으로 나가는 부분 이름은 언제나 **앞의 `/` 가 없고 퍼센트 인코딩을 푼** 모양이다
 * (`word/media/image1.png`). ZIP 항목 이름이 그 모양이고, 화면이 자원을 청하는 경로
 * (`WebHost.pathOf`)도 그 모양이다. 모양이 둘이면 한쪽에서 찾고 다른 쪽에서 못 찾는 날이 온다.
 *
 * **찾을 때는 대소문자를 가리지 않는다** — 명세가 부분 이름을 ASCII 대소문자 무관으로 정한다.
 * 실제로 `[Content_Types].xml` 의 `PartName` 과 ZIP 항목 이름의 대소문자가 다른 파일이 있다.
 */
object OpcNames {

    /** 찾기용 열쇠. 앞의 `/` 를 떼고, 퍼센트를 풀고, 소문자로. */
    fun key(name: String): String = normalize(name).lowercase()

    /** 바깥에 내보내는 모양. 앞의 `/` 를 떼고 퍼센트를 푼다. 대소문자는 그대로. */
    fun normalize(name: String): String = percentDecode(name.removePrefix("/"))

    /** 부분이 들어 있는 폴더(`word/document.xml` → `word/`). 뿌리면 빈 문자열. */
    fun dirOf(part: String): String {
        val n = normalize(part)
        val slash = n.lastIndexOf('/')
        return if (slash < 0) "" else n.substring(0, slash + 1)
    }

    /**
     * 관계의 대상(`Target`)을 부분 이름으로 푼다 — §9.3. 기준은 **관계를 가진 부분의 폴더**다
     * (패키지 관계면 뿌리).
     *
     * @param source 관계를 가진 부분. 패키지 관계면 null.
     * @return 부분 이름(앞 `/` 없음, 퍼센트 푼 모양). 뿌리를 벗어나거나(`../../`) 비었으면 null.
     */
    fun resolve(source: String?, target: String): String? {
        // 조각(#)과 질의(?)는 부분 이름이 아니다. 조각은 부르는 쪽이 따로 쓴다.
        var t = target.substringBefore('#').substringBefore('?').trim()
        if (t.isEmpty()) return null
        // 역슬래시로 적은 파일이 있다(명세 위반이지만 오피스가 읽는다).
        t = t.replace('\\', '/')
        val base = if (t.startsWith("/")) "" else source?.let { dirOf(it) }.orEmpty()
        val segments = ArrayList<String>()
        for (seg in (base + t.removePrefix("/")).split('/')) {
            when (seg) {
                "", "." -> Unit
                // **뿌리를 벗어나면 버린다.** 패키지 밖을 가리키는 관계는 우리가 내줄 것이 없고,
                // 조용히 뿌리로 되돌리면 다른 부분을 가리키게 된다.
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.size - 1)
                else -> segments.add(seg)
            }
        }
        if (segments.isEmpty()) return null
        return percentDecode(segments.joinToString("/"))
    }

    /**
     * 부분 이름을 **본문에 적을 상대 URL** 로 — 변환기가 `img src` 에 쓰는 모양이다.
     *
     * 그대로 적으면 안 되는 이유가 셋이다. 위생기(`Urls.rewrite`)가 **공백을 지우므로**
     * `my image.png` 가 `myimage.png` 가 되어 사라지고, `#`·`?` 는 조각·질의로 읽혀 이름이
     * 잘리며, `%` 는 WebView 가 이미 인코딩된 것으로 읽는다. 영문·숫자·`-._~/` 밖은 전부
     * UTF-8 퍼센트로 적는다. 화면이 청하는 경로(`WebHost.pathOf`)는 디코딩된 모양이라
     * 되돌리는 일은 따로 없다.
     */
    fun toUrl(name: String): String = io.github.donggi.iroiroviewer.format.html.FlowUrls.encode(normalize(name))

    /**
     * 퍼센트 인코딩을 UTF-8 로 푼다. **`+` 를 공백으로 바꾸지 않는다**(`URLDecoder` 는 폼
     * 인코딩이라 바꾼다 — 11단계의 함정 표). 잘못된 `%` 는 글자 그대로 둔다.
     */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = StringBuilder(s.length)
        // 이어진 `%XX` 들을 바이트로 모았다가 한꺼번에 UTF-8 로 푼다 — 한글 한 글자가 `%XX` 셋이다.
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

    /** `s[at]`·`s[at+1]` 이 16진 두 자리면 그 값, 아니면 -1. */
    private fun hex(s: String, at: Int): Int {
        if (at + 1 >= s.length) return -1
        val hi = Character.digit(s[at], 16)
        val lo = Character.digit(s[at + 1], 16)
        return if (hi < 0 || lo < 0) -1 else hi * 16 + lo
    }
}
