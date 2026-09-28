package io.github.donggi.iroiroviewer.docview

import java.util.Locale
import kotlin.math.floor

/**
 * `doc_progress.locator`·`progress` 에 적는 **장·부분 안의 자리**.
 *
 * ## 모양
 *
 * `locator` 는 `"<장 번호>:<장 안의 비율>"` 이다 — `"5:0.4213"` 은 여섯째 장(0부터)의 42% 자리. 비율은 스크롤 범위에 대한
 * 것이라 글자 크기가 바뀌어도 대강 같은 곳을 가리킨다(`ReadingScroll` 의 주석). **경로도 글도 담지 않는다** — 그 칸의
 * 약속(`DocProgressEntity`)이고, 무엇을 읽었는지가 새지 않게 하는 규칙이다.
 *
 * 장 번호를 함께 적는 것은 `page` 칸과 **어긋났는지 알아보려는** 것이다. 둘이 다르면(옛 판이 `page` 만 고쳐 썼다, 손으로
 * 고친 값) 비율을 버리고 장의 처음에서 연다 — 다른 장의 비율로 옮기면 엉뚱한 곳에 떨어진다.
 *
 * `progress` 는 흐름 문서가 **문서 전체**에서 얼마나 왔는가(0~1)다 — `(장 + 장 안에서 본 몫) / 장 수`. 본 몫은 화면의 **끝**
 * 가장자리까지다(`ReadingScroll.seenOf`) — 자리(앞 가장자리)로 셈하면 한 화면에 드는 마지막 장(판권·지은이 소개)을 읽어도
 * 진행이 `(장 수 − 1) / 장 수` 에 머물러 파일 목록의 '다 읽음'(몫 0.99 이상)에 닿지 않는다.
 *
 * **PDF 는 두 칸을 비운다.** 쪽 문서의 자리는 `page`·`page_count` 가 이미 말하고, 파일 목록의 배지는 `progress` 가 있으면
 * 그것을 먼저 쓴다 — PDF 에 몫을 적으면 배지가 '4/8쪽' 에서 '50%' 로 바뀌고, **한 쪽짜리 PDF 는 열기만 해도 '다 읽음'**
 * 이 된다(배지가 '자리가 하나뿐이면 달지 않는다' 로 막아 둔 바로 그 거짓이다 — 검토가 잡았다).
 */
internal object DocLocator {

    /** 기록 한 벌의 두 칸. */
    data class Fields(val locator: String?, val progress: Double?)

    /**
     * 기록에 적을 두 칸. [paged] 면(PDF) 둘 다 비운다(머리말).
     *
     * @param fraction 장 안의 자리(화면의 앞 가장자리). `locator` 가 된다.
     * @param seen 장 안에서 본 몫(화면의 끝 가장자리). `progress` 가 된다.
     */
    fun fields(paged: Boolean, index: Int, fraction: Float, seen: Float, count: Int): Fields =
        if (paged) Fields(null, null) else Fields(encode(index, fraction), progress(index, seen, count))

    /**
     * 적는다. 비율은 넷째 자리까지(`Locale.ROOT` — 기기의 언어가 소수점을 쉼표로 쓰면 읽지 못하는 값이 된다).
     * 처음(0)이면 null — 칸을 비워 둔다. `page` 만으로 충분하다.
     */
    fun encode(index: Int, fraction: Float): String? {
        if (index < 0) return null
        val f = clean(fraction)
        if (f <= 0f) return null
        return index.toString() + ":" + String.format(Locale.ROOT, "%.4f", floor(f * 10_000.0) / 10_000.0)
    }

    /**
     * 읽는다. [index] 가 적힌 장 번호와 같을 때만 그 비율을, 아니면 null.
     *
     * **엄격하게 읽는다.** 모양이 조금이라도 다르면 null — 이 칸은 우리만 쓰는 칸이라 다른 모양은 고장이고, 고장 난 값으로
     * 옮기느니 장의 처음이 낫다.
     */
    fun decode(locator: String?, index: Int): Float? {
        if (locator == null || locator.length > MAX_LENGTH) return null
        val m = PATTERN.matchEntire(locator) ?: return null
        val at = m.groupValues[1].toIntOrNull() ?: return null
        if (at != index) return null
        val f = m.groupValues[2].toFloatOrNull() ?: return null
        return if (f.isNaN() || f < 0f || f > 1f) null else f
    }

    /** 문서 전체에서 얼마나 왔는가 — `(장 + 본 몫) / 장 수`. [count] 가 0 이하면 null. */
    fun progress(index: Int, seen: Float, count: Int): Double? {
        if (count <= 0 || index < 0) return null
        val v = (index + clean(seen).toDouble()) / count
        return v.coerceIn(0.0, 1.0)
    }

    private fun clean(f: Float): Float = if (f.isNaN()) 0f else f.coerceIn(0f, 1f)

    private val PATTERN = Regex("(\\d{1,7}):(0(?:\\.\\d{1,6})?|1(?:\\.0{1,6})?)")

    private const val MAX_LENGTH = 32
}
