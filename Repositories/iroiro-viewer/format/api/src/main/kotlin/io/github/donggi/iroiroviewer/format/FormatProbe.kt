package io.github.donggi.iroiroviewer.format

/**
 * 판별에 필요한 재료. 파서마다 파일을 다시 열지 않도록 한 번 읽어 돌려 쓴다.
 *
 * [zipEntryNames] 가 게으른 것은 ZIP 을 여는 비용 때문이다. 매직 바이트가 `PK` 가
 * 아니면 아무도 이것을 건드리지 않는다.
 */
class ProbeContext(
    val source: DocumentSource,
    /** 소문자, 앞의 점 없음. 확장자가 없으면 빈 문자열. */
    val extension: String,
    /** 앞부분 바이트(보통 64). */
    val head: ByteArray,
    /** ZIP 이면 엔트리 이름 목록, 아니면 null. 공급은 상위(app)가 한다. */
    val zipEntryNames: Lazy<List<String>?> = lazy { null },
) {
    fun headStartsWith(vararg magic: Int): Boolean {
        if (head.size < magic.size) return false
        return magic.withIndex().all { (i, b) -> head[i] == b.toByte() }
    }
}

/**
 * 확장자와 매직으로 포맷을 가린다. 모르면 null 을 돌려주고 다음 판별기로 넘어간다.
 *
 * **목록 화면에서는 이것을 부르지 않는다** — 파일마다 앞부분을 읽으면 폴더 하나를
 * 그리는 데 수천 번의 읽기가 생긴다. 목록은 확장자만 보고, 매직은 실제로 열 때 본다.
 */
fun interface FormatProbe {
    fun probe(context: ProbeContext): FormatId?
}
