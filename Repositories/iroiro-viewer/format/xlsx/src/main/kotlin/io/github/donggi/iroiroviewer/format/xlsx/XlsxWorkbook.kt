package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/** `workbook.xml` 의 `<sheet>` 하나. [state] 는 `visible`·`hidden`·`veryHidden`(없으면 보인다). */
internal data class SheetEntry(val name: String, val state: String?, val relId: String?) {
    val visible: Boolean get() = state == null || state == "visible"
}

/** `workbook.xml` 에서 필요한 것 — 날짜 체계와 시트 목록(적힌 순서 그대로). */
internal class WorkbookInfo(val date1904: Boolean, val sheets: List<SheetEntry>, val sheetsTruncated: Boolean) {

    companion object {
        /** 읽는다. 깨진 파일이면 던진다 — 통합 문서의 뼈대라 이것 없이는 보여 줄 것이 없다. */
        fun read(p: XmlPullParser, limits: ParseLimits): WorkbookInfo {
            if (!XlsxXml.toRoot(p, limits)) return WorkbookInfo(false, emptyList(), false)
            var date1904 = false
            val sheets = ArrayList<SheetEntry>()
            var truncated = false
            XlsxXml.children(p, limits) { name ->
                when (name) {
                    "workbookPr" -> date1904 = OoxmlXml.attr(p, "date1904")?.let { it == "1" || it.equals("true", true) } == true
                    "sheets" -> XlsxXml.children(p, limits) { s ->
                        if (s == "sheet") {
                            if (sheets.size < XlsxLimits.MAX_SHEETS) {
                                sheets.add(SheetEntry(OoxmlXml.attr(p, "name").orEmpty(), OoxmlXml.attr(p, "state"), OoxmlXml.rel(p, "id")))
                            } else {
                                truncated = true
                            }
                        }
                    }
                    else -> Unit
                }
            }
            return WorkbookInfo(date1904, sheets, truncated)
        }
    }
}

/**
 * 공유 문자열 표(`sharedStrings.xml`). 셀이 `t="s"` 로 번호만 적으면 여기서 글자를 찾는다.
 *
 * **여는 때 한 번 다 읽는다.** 시트마다 다시 읽으면 시트를 넘길 때마다 수십 MB 를 푼다. 대신 개수와
 * 글자 수에 상한을 두고([XlsxLimits]), 넘으면 거기까지만 들고 [truncated] 를 세운다 — 뒤의 번호는
 * 빈 칸으로 그린다.
 *
 * 서식 있는 문자열은 **글자만** 든다(조각을 이어 붙인다). 윗주(`rPh`)는 버린다.
 */
internal class SharedStrings private constructor(private val items: List<String>, val truncated: Boolean) {

    /** 번호의 글자. 없는 번호(상한에 잘렸거나 문서가 틀렸다)는 빈 문자열. */
    operator fun get(index: Int): String = if (index >= 0 && index < items.size) items[index] else ""

    val size: Int get() = items.size

    companion object {
        val EMPTY = SharedStrings(emptyList(), false)

        /**
         * @param checkpoint 몇천 개마다 부른다 — 취소를 보는 자리다(여는 코루틴의 `ensureActive`).
         * @param maxItems 개수 상한. 시험이 작은 값으로 상한의 동작을 본다.
         * @param maxChars 글자 수 합계 상한.
         */
        fun read(
            p: XmlPullParser,
            limits: ParseLimits,
            maxItems: Int = XlsxLimits.MAX_SHARED_STRINGS,
            maxChars: Long = XlsxLimits.MAX_SHARED_STRING_CHARS,
            checkpoint: () -> Unit,
        ): SharedStrings {
            val items = ArrayList<String>()
            var total = 0L
            var truncated = false
            try {
                if (!XlsxXml.toRoot(p, limits)) return EMPTY
                val depth = p.depth
                while (true) {
                    val ev = p.nextGuarded(limits)
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.END_TAG && p.depth <= depth) break
                    if (ev != XmlPullParser.START_TAG || p.depth != depth + 1) continue
                    if (p.name != "si") {
                        OoxmlXml.skip(p, limits)
                        continue
                    }
                    val s = XlsxXml.readRichText(p, limits)
                    if (items.size >= maxItems || total + s.length > maxChars) {
                        truncated = true
                        break
                    }
                    items.add(s)
                    total += s.length
                    if (items.size % 4096 == 0) checkpoint()
                }
            } catch (e: ParseLimitExceededException) {
                // 32 MiB 를 넘는 표. 읽은 데까지 쓴다 — 앞쪽 시트는 대개 앞쪽 문자열을 쓴다.
                truncated = true
            }
            return SharedStrings(items, truncated)
        }
    }
}

/** `docProps/core.xml` 의 제목(`dc:title`). 없으면 빈 문자열 — 화면이 파일 이름으로 채운다. */
internal object CoreTitle {
    private const val MAX_CHARS = 512

    fun read(p: XmlPullParser, limits: ParseLimits): String {
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "title") {
                return OoxmlXml.collectText(p, limits, MAX_CHARS).trim()
            }
            ev = p.nextGuarded(limits)
        }
        return ""
    }
}

/**
 * 시트 이름을 **사람에게 보일 모양으로.** 이름은 문서가 적는 값이고 화면의 아래 막대·목차·알림 문장에
 * 그대로 나간다. 제어 문자는 공백으로, 방향을 바꾸는 문자(U+202A~202E·U+2066~2069)는 버리고 — 알림
 * 문장의 뒷부분을 거꾸로 보이게 할 수 있다 — 공백을 하나로 모아 [MAX_CHARS] 에서 자른다. pptx 의
 * 슬라이드 제목과 같은 다듬기다. **찾기 열쇠(시트 사이 링크)에는 쓰지 않는다** — 적힌 이름으로 찾는다.
 */
internal object SheetNames {
    const val MAX_CHARS = 120

    fun display(raw: String): String {
        val sb = StringBuilder(minOf(raw.length, MAX_CHARS + 8))
        var space = false
        for (c in raw) {
            if (sb.length >= MAX_CHARS) break
            when {
                c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' -> Unit
                c.isWhitespace() || c.isISOControl() -> space = sb.isNotEmpty()
                else -> {
                    if (space) sb.append(' ')
                    space = false
                    sb.append(c)
                }
            }
        }
        // 자른 자리가 대리 쌍 한가운데면 앞 반쪽을 버린다.
        if (sb.isNotEmpty() && sb[sb.length - 1].isHighSurrogate()) sb.setLength(sb.length - 1)
        return sb.toString()
    }
}
