package io.github.donggi.iroiroviewer.model

/** 무엇을 기준으로 줄을 세우는가. */
enum class SortKey { NAME, DATE, SIZE, KIND }

/**
 * 정렬 규칙.
 *
 * [foldersFirst] 는 거의 모든 파일 관리자의 기본값이고 사람들이 그렇게 기대한다.
 * 끄는 선택지를 두되 기본은 켠 채로 둔다.
 */
data class SortSpec(
    val key: SortKey = SortKey.NAME,
    val ascending: Boolean = true,
    val foldersFirst: Boolean = true,
) {
    fun toggled(newKey: SortKey): SortSpec =
        if (newKey == key) copy(ascending = !ascending) else copy(key = newKey, ascending = true)

    companion object {
        val DEFAULT = SortSpec()
    }
}

enum class ViewMode { LIST, GRID }
