package io.github.donggi.iroiroviewer.format.epub

/**
 * EPUB 안의 상대 주소를 **ZIP 엔트리 이름**으로 바꾼다.
 *
 * 순수 계산이라 JVM 시험이 답한다. 여기가 틀리면 증상은 '그림이 안 보인다' 하나로 뭉쳐
 * 보이지만 원인은 여럿이다 — 기준 폴더를 잘못 잡았거나, `..` 을 안 풀었거나, 퍼센트
 * 인코딩을 안 풀었거나.
 *
 * ## 위로 올라가는 주소를 막는다
 *
 * `../../../../etc/passwd` 를 그대로 풀면 ZIP 밖을 가리키는 이름이 나온다. 이 앱에서는
 * 그것이 곧바로 파일을 여는 길은 아니지만(우리는 ZIP 엔트리만 찾는다) **밖을 가리키는
 * 이름을 만들어 두면 언젠가 그것을 파일 경로로 쓰는 코드가 생긴다.** 8단계가 아카이브
 * 풀기에서 같은 판단을 했다 — 여기서도 뿌리 위로 올라가면 null 이다.
 */
object EpubHref {

    /**
     * [base] 폴더에서 [href] 를 푼다.
     *
     * @param base ZIP 안의 폴더. 루트면 빈 문자열이다(`OEBPS` 처럼 끝에 `/` 없이 준다).
     * @return ZIP 엔트리 이름. 밖을 가리키거나 비어 있으면 null.
     */
    fun resolve(base: String, href: String): String? {
        val path = strip(href) ?: return null
        val parts = ArrayList<String>()
        // 절대 주소(`/x/y`)는 EPUB 루트 기준이다. 그 경우 기준 폴더를 쓰지 않는다.
        if (!path.startsWith("/")) {
            base.split('/').filterTo(parts) { it.isNotEmpty() && it != "." }
        }
        // **주소가 스스로 이름 하나를 대야 한다.** `./` 나 `..` 만 있는 주소는 폴더를
        // 가리키는 것이라 내줄 자원이 없다 — 그런데도 기준 폴더를 돌려주면 호출자는
        // 그것을 파일 이름으로 알고 찾는다.
        var named = false
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    // **뿌리 위로 올라가면 거기서 끝이다.** 비어 있는데 더 올라가려는
                    // 것은 밖을 가리키겠다는 뜻이고, 그 이름은 만들지 않는다.
                    if (parts.isEmpty()) return null
                    parts.removeAt(parts.size - 1)
                }
                else -> {
                    parts.add(segment)
                    named = true
                }
            }
        }
        if (!named || parts.isEmpty()) return null
        return parts.joinToString("/")
    }

    /** [path] 가 들어 있는 폴더. 루트면 빈 문자열. */
    fun dirOf(path: String): String {
        val slash = path.lastIndexOf('/')
        return if (slash < 0) "" else path.substring(0, slash)
    }

    /**
     * 조각(`#…`)과 질의(`?…`)를 떼고 퍼센트 인코딩을 푼다.
     *
     * **`+` 를 공백으로 바꾸지 않는다.** 그것은 폼 인코딩의 규칙이고 URL 경로에서
     * `+` 는 그냥 `+` 다 — `java.net.URLDecoder` 를 쓰면 `a+b.png` 가 `a b.png` 가 되어
     * 파일을 못 찾는다.
     */
    private fun strip(href: String): String? {
        var s = href.trim()
        if (s.isEmpty()) return null
        s = s.substringBefore('#').substringBefore('?')
        if (s.isEmpty()) return null
        return percentDecode(s)
    }

    /** 퍼센트 인코딩만 푼다. 바이트를 모아 UTF-8 로 읽는다(한글 파일명이 그 모양이다). */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val bytes = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val value = hex.toIntOrNull(16)
                if (value != null) {
                    bytes.write(value)
                    i += 3
                    continue
                }
            }
            // ASCII 밖의 글자는 이미 문자열이므로 UTF-8 바이트로 넣는다.
            bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
