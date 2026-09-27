package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.SafeXml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * EPUB 의 뼈대를 읽는다 — `META-INF/container.xml` 과 OPF(패키지 문서).
 *
 * ## 접두어가 아니라 이름과 URI 로 본다
 *
 * `SafeXml` 이 네임스페이스를 켜 두었으므로 `parser.name` 은 접두어가 떨어진 지역 이름이다.
 * 문서가 `opf:manifest` 로 쓰든 `manifest` 로 쓰든 같은 값이 나온다 — **접두어로 비교하면
 * 파일마다 다른 접두어에 걸려 넘어진다.**
 *
 * ## 무엇을 읽지 않는가
 *
 * `<metadata>` 에서 제목·언어와 **식별자**만 읽는다. 저자·출판사는 화면에 쓸 자리가 없다.
 * **읽지 않는 것은 실수로 새지도 않는다** — 목록 화면에 저자를 띄우려면 그때 이 함수를
 * 고치면서 '어디에 쓰는가' 를 같이 정하면 된다.
 *
 * 식별자는 **글꼴 난독화의 열쇠를 만드는 데만** 쓴다([FontObfuscation]). `EpubBook` 의 공개
 * API 로 내보내지 않는다 — ISBN 이나 판매처 고유번호가 들어 있어 화면·로그에 나갈 이유가 없다.
 */
internal object EpubPackage {

    /** 매니페스트 항목 하나. */
    data class Item(
        val id: String,
        /** ZIP 엔트리 이름(OPF 기준으로 이미 풀었다). */
        val path: String,
        val mediaType: String,
        /** EPUB3 의 `properties`. 표지와 목차를 여기서 알아본다. */
        val properties: String,
    )

    data class Package(
        val title: String,
        val language: String?,
        val items: List<Item>,
        /** 읽는 차례. [Item.id] 의 목록이고 `linear="no"` 는 빠져 있다. */
        val spine: List<String>,
        /** EPUB2 의 목차(`toc.ncx`) 항목 id. 없으면 null. */
        val ncxId: String?,
        /**
         * `package@unique-identifier` 가 가리키는 `dc:identifier` 의 값. 없으면 null.
         * IDPF 글꼴 난독화의 열쇠가 여기서 나온다.
         */
        val uniqueIdentifier: String? = null,
        /** 모든 `dc:identifier` 의 값(문서 순서). Adobe 난독화가 UUID 를 여기서 찾는다. */
        val identifiers: List<String> = emptyList(),
    ) {
        // 식별자가 로그·예외에 실려 나가지 않게 한다(위 머리말).
        override fun toString(): String = "Package(title=$title, items=${items.size}, spine=${spine.size})"
    }

    /**
     * `META-INF/container.xml` 에서 OPF 의 자리를 읽는다.
     *
     * **첫 `rootfile` 을 쓴다.** 명세는 여럿을 허용하지만 둘 이상인 파일은 실물로 거의
     * 없고, 고르는 규칙(렌디션 선택)은 우리가 지원하지 않는 기능에 달려 있다.
     */
    fun rootFile(input: InputStream, limits: ParseLimits = ParseLimits.DEFAULT): String? {
        val parser = SafeXml.newParser(input, null, limits)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val path = parser.getAttributeValue(null, "full-path")
                if (!path.isNullOrBlank()) return EpubHref.resolve("", path)
            }
            event = parser.next()
        }
        return null
    }

    /**
     * OPF 를 읽는다.
     *
     * @param opfPath OPF 자신의 ZIP 이름. 매니페스트의 상대 주소가 이것의 폴더 기준이다.
     */
    fun parse(
        input: InputStream,
        opfPath: String,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ): Package {
        val base = EpubHref.dirOf(opfPath)
        val parser = SafeXml.newParser(input, null, limits)

        var title = ""
        var language: String? = null
        val items = ArrayList<Item>()
        val spine = ArrayList<String>()
        var ncxId: String? = null
        var pending: String? = null
        var uniqueId: String? = null
        var pendingId: String? = null
        val identifiers = ArrayList<String>()
        val identifierById = HashMap<String, String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "package" -> uniqueId = parser.getAttributeValue(null, "unique-identifier")?.trim()
                    "title" -> pending = "title"
                    "language" -> pending = "language"
                    "identifier" -> {
                        pending = "identifier"
                        pendingId = parser.getAttributeValue(null, "id")
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                            val path = EpubHref.resolve(base, href)
                            if (path != null) {
                                items.add(
                                    Item(
                                        id = id,
                                        path = path,
                                        mediaType = parser.getAttributeValue(null, "media-type")
                                            .orEmpty().trim().lowercase(),
                                        properties = parser.getAttributeValue(null, "properties")
                                            .orEmpty(),
                                    )
                                )
                            }
                        }
                    }

                    "spine" -> ncxId = parser.getAttributeValue(null, "toc")

                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        // `linear="no"` 는 본문 차례 밖(광고·판권)이다. 차례에 넣으면
                        // 사용자가 읽는 순서가 책이 말한 순서와 달라진다.
                        val linear = parser.getAttributeValue(null, "linear")
                        if (!idref.isNullOrBlank() && !linear.equals("no", ignoreCase = true)) {
                            spine.add(idref)
                        }
                    }
                }

                XmlPullParser.TEXT -> if (pending != null) {
                    val raw = SafeXml.text(parser, limits)
                    // **식별자는 XML 공백 넷만 벗긴다.** 코틀린의 `trim()` 은 NBSP·U+3000 같은 유니코드
                    // 공백까지 벗기는데, 글꼴 난독화의 열쇠(EPUB 3.3 4.4)는 U+0020·0009·000D·000A 만
                    // 뺀 식별자의 SHA-1 이다. 끝에 NBSP 가 붙은 식별자면 열쇠가 틀려 글꼴이 조용히 깨진다
                    // (검토가 잡았다). 제목·언어는 화면에 보일 값이라 그대로 `trim()` 한다.
                    val text = if (pending == "identifier") {
                        raw.trim { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
                    } else {
                        raw.trim()
                    }
                    if (text.isNotEmpty()) {
                        // **첫 값만 쓴다.** `dc:title` 이 여럿인 책이 있고(부제·정렬용),
                        // 뒤엣것으로 덮으면 화면에 부제만 뜬다.
                        if (pending == "title" && title.isEmpty()) title = text
                        if (pending == "language" && language == null) language = text
                        if (pending == "identifier" && identifiers.size < MAX_IDENTIFIERS) {
                            identifiers.add(text)
                            pendingId?.let { identifierById.putIfAbsent(it, text) }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    pending = null
                    pendingId = null
                }
            }
            event = parser.next()
        }
        return Package(
            title, language, items, spine, ncxId,
            uniqueIdentifier = uniqueId?.let { identifierById[it] },
            identifiers = identifiers,
        )
    }

    /** 식별자를 이만큼만 모은다. 수만 개를 적은 악성 OPF 가 메모리를 채우지 못하게. */
    private const val MAX_IDENTIFIERS = 64
}
