package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.xmlpull.v1.XmlPullParser

/**
 * 메모 하나(`w:comments` 의 `w:comment`).
 *
 * @param ordinal 메모 부분에 적힌 차례(1부터). 워드는 메모를 **문서 차례로** 적으므로(`w:id` 도 그 차례로 매긴다) 이것이 워드의
 *   메모 번호다. 조각의 경계와 관계없이 한 값이라 훑기와 그리기가 따로 셈을 들 필요가 없다 — 각주 번호와 다른 점이다.
 * @param initials 지은이의 머리글자(`w:initials`). 워드의 옛 표지(`[MK1]`)가 머리글자 + 번호다.
 */
internal class CommentInfo(val ordinal: Int, val initials: String, val author: String) {
    /**
     * 본문의 작은 표지이자 메모 쪽의 머리. **화면 문구가 아니라 문서의 값**(머리글자와 번호)으로만 짓는다 — 순수 JVM 모듈은
     * 화면 문구를 만들지 않는다(CLAUDE.md '코드'). 각주 번호와 같은 자리다.
     */
    val label: String get() = "[$initials$ordinal]"

    /** 메모 쪽 항목의 `id`. 부분 안에서만 쓰인다(메모는 가리킨 조각의 끝에 그린다). */
    val noteId: String get() = "cm-$ordinal"

    /** 본문 표지의 `id` — 메모 쪽 머리를 누르면 돌아온다. */
    val backId: String get() = "cmref-$ordinal"
}

/**
 * 메모 부분(`word/comments.xml`)에서 읽은 것 — `w:id` → 메모. 여는 동안 한 번 읽는다. 본문은 들지 않는다 — 그리는 조각이
 * 가리킨 것만 그때 흘려 읽는다(각주와 같다).
 *
 * [part] 가 null 이면 메모 부분이 없거나 읽지 못했다 — 본문의 메모 표지(`w:commentReference`)는 보일 글이 없으므로
 * 버린 것으로 센다(`UnsupportedFeatures.COMMENT`). **보인 메모는 세지 않는다** — 12단계는 메모를 그리지 않고 셌다.
 */
internal class DocxComments(val part: String?, private val byId: Map<Int, CommentInfo>) {

    fun find(id: Int): CommentInfo? = byId[id]

    val size: Int get() = byId.size

    companion object {
        val NONE = DocxComments(null, emptyMap())

        /** 문서 하나의 메모 상한. 넘는 메모는 찾지 못한 것으로 친다(버린 것으로 센다). */
        const val MAX_COMMENTS = 50_000

        private const val MAX_INITIALS = 8
        private const val MAX_AUTHOR = 100
        private val SPACES = Regex("\\s+")

        /** [p] 는 뿌리(`w:comments`)에 선 파서. 같은 `w:id` 가 둘이면 먼저 나온 것을 쓴다. */
        fun read(p: XmlPullParser, limits: ParseLimits, part: String): DocxComments {
            val byId = HashMap<Int, CommentInfo>()
            var ordinal = 0
            DocxProps.eachChild(p, limits) { name ->
                val id = OoxmlXml.int(p, "id")
                if (name == "comment" && id != null && byId.size < MAX_COMMENTS && id !in byId) {
                    ordinal++
                    byId[id] = CommentInfo(ordinal, clean(OoxmlXml.attr(p, "initials"), MAX_INITIALS, keepSpaces = false), clean(OoxmlXml.attr(p, "author"), MAX_AUTHOR, keepSpaces = true))
                }
                OoxmlXml.skip(p, limits)
            }
            return DocxComments(part, byId)
        }

        /** 이름 다듬기 — 제어 문자를 버리고 공백을 모은다. 머리글자는 공백 없이(표지가 `[M K1]` 로 갈라지지 않게). */
        private fun clean(raw: String?, max: Int, keepSpaces: Boolean): String {
            if (raw == null) return ""
            val s = raw.filter { it >= ' ' }.trim()
            val joined = if (keepSpaces) s.replace(SPACES, " ") else s.replace(SPACES, "")
            return joined.take(max)
        }
    }
}

/**
 * 조각 하나를 그리는 동안 만난 메모 — 본문의 걷기와 각주의 걷기가 **함께** 쓴다(각주 안에도 메모가 달린다). 조각의 끝에서
 * 이것이 가리킨 메모만 그린다.
 */
internal class CommentSink {
    private val seen = HashSet<Int>()

    /** 만난 메모의 차례 번호들. */
    val ordinals: Set<Int> get() = seen

    /**
     * 메모 하나를 만났다. (메모 쪽에 드는가, 본문 표지의 `id`) 를 준다 — 처음이면 (참, [CommentInfo.backId]), 두 번째부터는
     * (참, null)(한 부분 안에 같은 `id` 가 둘이면 안 된다). 상한을 넘으면 적지 않고 (거짓, null) — 그 표지는 링크 없이 선다.
     */
    fun add(info: CommentInfo): Pair<Boolean, String?> {
        if (info.ordinal in seen) return true to null
        if (seen.size >= MAX_PER_PART) return false to null
        seen.add(info.ordinal)
        return true to info.backId
    }

    companion object {
        /** 부분 하나에 모아 그리는 메모의 수(각주의 `MAX_NOTES` 와 같다). */
        const val MAX_PER_PART = 10_000
    }
}
