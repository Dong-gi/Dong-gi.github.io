package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.ParseWarning

/**
 * 흐름 문서가 그림을 꺼내 오는 **꾸러미**. OOXML(ZIP + OPC), HWPX(ZIP + OWPML 매니페스트), HWP 5.0
 * (CFB 의 `BinData` 저장소)이 각자 구현한다. [FlowDocumentBase] 는 이것만 안다.
 *
 * ## 이름은 한 모양
 *
 * 이름은 **앞의 `/` 가 없고 퍼센트 인코딩을 푼** 모양이다(`word/media/image1.png`, `BinData/image1.png`).
 * 화면이 자원을 청하는 경로(`WebHost.pathOf`)가 그 모양이고, 본문에 적을 때는 [FlowUrls.encode] 가
 * URL 로 바꾼다. 모양이 둘이면 한쪽에서 찾고 다른 쪽에서 못 찾는 날이 온다(12단계의 `OpcNames` 주석).
 *
 * ## 스레드
 *
 * 구현은 스레드 안전하지 않아도 된다 — [FlowDocumentBase] 가 모든 접근을 한 잠금으로 세운다.
 */
interface FlowPackage : AutoCloseable {

    /** 이 이름의 항목이 있는가. 찾는 규칙(대소문자 무관 등)은 포맷이 정한다. */
    fun has(name: String): Boolean

    /** 바깥에 내보내는 이름으로 고친다(실제 항목의 대소문자 등). 없으면 null. */
    fun canonical(name: String): String?

    /** 항목의 MIME. 모르면 null — 그때는 확장자로 짐작한다. */
    fun contentType(name: String): String?

    /**
     * 항목을 통째로. 없거나 [max] 를 넘으면 null — 그림 하나가 크다고 부분이 실패하지는 않는다
     * (`FlowDocumentBase.openResource` 가 404 로 끝낸다). 꾸러미 전체의 상한(해제 총량 등)은 상한 예외로
     * 던질 수 있다.
     */
    fun readBytes(name: String, max: Long): ByteArray?

    /** 여는 동안·읽는 동안 나온 경고. 읽을 때마다 지금의 것을 준다. */
    val warnings: List<ParseWarning>

    /** 매크로(스크립트)가 들어 있는가. **돌리지 않는다** — 들어 있다는 사실만 알린다. */
    val hasMacros: Boolean
}

/** 꾸러미 이름을 **본문에 적을 상대 URL** 로. */
object FlowUrls {

    /**
     * 영문·숫자·`-._~/` 밖은 전부 UTF-8 퍼센트로 적는다. 그대로 적으면 안 되는 이유가 셋이다 —
     * 위생기(`Urls.rewrite`)가 **공백을 지우므로** `my image.png` 가 `myimage.png` 가 되어 사라지고,
     * `#`·`?` 는 조각·질의로 읽혀 이름이 잘리며, `%` 는 WebView 가 이미 인코딩된 것으로 읽는다
     * (CLAUDE.md 함정 표).
     */
    fun encode(name: String): String {
        val out = StringBuilder(name.length + 8)
        for (b in name.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val safe = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code || c == '/'.code
            if (safe) out.append(c.toChar()) else out.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
