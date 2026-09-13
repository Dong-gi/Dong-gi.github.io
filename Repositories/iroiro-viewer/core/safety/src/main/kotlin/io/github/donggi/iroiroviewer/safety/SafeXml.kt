package io.github.donggi.iroiroviewer.safety

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * **XML 파서를 만드는 유일한 지점.**
 *
 * `android.util.Xml.newPullParser()` 를 직접 부르는 것을 전역으로 금지한다. 그쪽은
 * DOCDECL 처리가 켜진 채로 오고, 파서 구현에 엔티티 확장 한도가 없어서 문서 하나로
 * 메모리를 고갈시킬 수 있다(이른바 billion laughs). 설정 하나를 빠뜨린 파서가 하나라도
 * 생기면 방어가 무너지므로, 생성 자체를 한 함수에 가둔다.
 *
 * 네임스페이스는 켠다. **접두어(`hp:`, `w:`)로 비교하지 말고 URI 로 비교하라** —
 * 문서가 어떤 접두어를 쓰는지는 문서 마음이고, 한컴은 버전에 따라 네임스페이스 URI 가
 * 바뀐다고 직접 경고했다.
 *
 * ## 텍스트 상한의 한계를 정직하게 적는다
 *
 * `parser.text` 를 읽는 시점에는 문자열이 **이미 힙에 올라와 있다.** 풀 파서는 인접한
 * 텍스트를 하나로 합쳐서 주기 때문이다. 그래서 [text] 의 길이 검사는 '너무 큰 것을
 * 화면까지 보내지 않는' 2차 방어일 뿐 OOM 자체를 막지 못한다.
 * **진짜 방어는 입력 바이트를 먼저 자르는 것**이고, 그래서 [newParser] 가 입력을
 * [ParseLimits.maxXmlBytes] 로 감싼다. 파서가 스트림을 직접 물리지 못하게 하라.
 */
object SafeXml {

    /**
     * 안전 설정을 건 파서. 입력을 물리지 않은 상태로 돌려준다.
     *
     * @throws XmlPullParserException 파서 구현이 없을 때(JVM 테스트에 kxml2 를 넣지 않은 경우).
     */
    fun newParser(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // 외부 DTD·엔티티 확장을 막는다. 이 기능을 모르는 구현도 있어서 실패는 삼키되,
        // 그런 구현에서도 기본값이 꺼짐인 것을 전제로 한다(kxml2·ExpatPullParser 가 그렇다).
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        runCatching { parser.setFeature(XmlPullParser.FEATURE_VALIDATION, false) }
        return parser
    }

    /**
     * 스트림에 물린 파서. **입력 바이트가 [ParseLimits.maxXmlBytes] 로 잘린다.**
     *
     * 인코딩을 null 로 주면 XML 선언을 따른다.
     */
    fun newParser(
        input: InputStream,
        encoding: String? = null,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ): XmlPullParser = newParser().apply {
        setInput(
            LimitedInputStream(input, limits.maxXmlBytes, "maxXmlBytes"),
            encoding,
        )
    }

    /**
     * 텍스트를 읽되 길이 상한을 건다. 위 주석의 한계를 알고 쓰라 — 이것은 2차 방어다.
     */
    fun text(parser: XmlPullParser, limits: ParseLimits = ParseLimits.DEFAULT): String {
        val t = parser.text ?: return ""
        if (t.length > limits.maxXmlTextChars) {
            throw ParseLimitExceededException(
                "maxXmlTextChars",
                "${t.length}자 (상한 ${limits.maxXmlTextChars})",
            )
        }
        return t
    }
}

/**
 * 깊이를 검사하는 [XmlPullParser.next].
 *
 * 파서가 `next()` 를 직접 부르면 중첩 상한이 걸리지 않는다. **파서 코드에서는
 * 이것만 쓴다** — 수천 겹으로 겹친 요소가 스택을 터뜨리는 것을 여기서 끊는다.
 */
fun XmlPullParser.nextGuarded(limits: ParseLimits = ParseLimits.DEFAULT): Int {
    val event = next()
    if (depth > limits.maxXmlDepth) {
        throw ParseLimitExceededException("maxXmlDepth", "깊이 $depth (상한 ${limits.maxXmlDepth})")
    }
    return event
}

/** 깊이를 검사하는 [XmlPullParser.nextTag]. */
fun XmlPullParser.nextTagGuarded(limits: ParseLimits = ParseLimits.DEFAULT): Int {
    val event = nextTag()
    if (depth > limits.maxXmlDepth) {
        throw ParseLimitExceededException("maxXmlDepth", "깊이 $depth (상한 ${limits.maxXmlDepth})")
    }
    return event
}
