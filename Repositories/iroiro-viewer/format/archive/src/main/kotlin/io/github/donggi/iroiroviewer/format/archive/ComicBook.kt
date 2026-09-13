package io.github.donggi.iroiroviewer.format.archive

import java.text.Collator

/**
 * 만화 한 권의 쪽 하나.
 *
 * **[entryIndex] 가 신원이다.** [name] 은 인코딩 판정을 거친 손실 가능한 문자열이고,
 * 같은 이름의 엔트리가 실재한다 — 이름으로 되짚으면 사용자가 보는 쪽과 다른 바이트가
 * 열린다(`ArchiveEntry` 의 같은 주석 참고).
 */
data class ComicPage(
    /** 읽는 차례. 0 부터. 이어보기가 저장하는 값이다. */
    val ordinal: Int,
    /** 아카이브 안의 자리. 폴더 만화에서는 나열 순서의 자리다. */
    val entryIndex: Int,
    val name: String,
    /**
     * 헤더에 **적힌** 크기.
     *
     * 진행 표시의 분모로만 쓴다. 창 크기·버퍼 크기 같은 결정에 쓰면 42.zip 한 줄에
     * 앱이 죽는다 — 창은 **실제로 읽은 바이트**로 닫는다.
     */
    val declaredSize: Long,
)

/**
 * 아카이브의 엔트리 목록에서 **만화의 쪽들**을 뽑는다.
 *
 * [ComicPages] 위에 얹는 얇은 함수다. 무엇이 쪽이고 어떤 차례인가 하는 규칙은 전부
 * 그쪽에 있고(폴더 만화도 같은 규칙을 쓴다), 여기서는 그 답을 아카이브 엔트리에
 * 이어 붙이기만 한다. 규칙을 두 곳에 적으면 폴더로 연 만화와 압축으로 연 만화의
 * 쪽 번호가 달라지고, 그러면 이어보기가 다른 쪽을 가리킨다.
 */
object ComicBook {

    /**
     * 읽을 수 있는 그림 엔트리를 차례대로.
     *
     * **읽을 수 없는 항목은 애초에 쪽이 아니다.** 암호·링크·위험한 이름은
     * [ArchiveEntry.isReadable] 이 걸러 준다. 그것을 쪽으로 세면 만화 한가운데에
     * 영영 열리지 않는 쪽이 생기고, 300쪽이라고 말해 놓고 298쪽만 보여 주게 된다.
     */
    fun pagesOf(
        entries: List<ArchiveEntry>,
        collator: Collator = io.github.donggi.iroiroviewer.model.NameSortKey.koreanCollator(),
    ): List<ComicPage> {
        val readable = entries.filter { it.isReadable }
        if (readable.isEmpty()) return emptyList()
        val order = ComicPages.order(readable.map { it.name }, collator)
        return order.mapIndexed { ordinal, position ->
            val e = readable[position]
            ComicPage(
                ordinal = ordinal,
                entryIndex = e.index,
                name = e.name,
                declaredSize = e.declaredSize.coerceAtLeast(0L),
            )
        }
    }
}
