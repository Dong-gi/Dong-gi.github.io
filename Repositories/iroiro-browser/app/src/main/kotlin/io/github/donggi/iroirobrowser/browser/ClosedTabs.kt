package io.github.donggi.iroirobrowser.browser

import android.os.Bundle

/**
 * 닫은 탭 — '탭을 닫았습니다 · 되돌리기' 와 메뉴의 '최근 닫은 탭'(앞 판은 잘못 닫으면 그것으로 끝이었고 뒤로 가기 기록도
 * 사라졌다). 탭마다 주소·제목과 **뒤로·앞으로 기록**(`WebView.saveState` 의 묶음 — 접은 탭과 같은 길이라 되살리면 기록이 돌아온다)을 둔다.
 *
 * 앱이 사는 동안 **메모리에만** 둔다 — 기록 묶음은 `Parcel` 이라 디스크에 두는 꼴이 아니다(세션 되살리기와 같은 까닭, [SessionStore]).
 * 한 번에 닫은 것(모두 닫기)은 한 묶음([Entry.batch])이라 되돌리기가 함께 되살린다. 최근 [limit] 개까지 — 넘치면 오래된 것부터 버린다.
 */
class ClosedTabs(private val limit: Int = DEFAULT_LIMIT) {

    /**
     * 닫은 탭 하나. [index] 는 닫을 때 탭 목록의 자리(되살리면 그 자리로), [state] 는 뒤로·앞으로 기록(WebView 가 없던 탭 — 세션에서
     * 되살린 뒤 아직 고르지 않은 탭 — 이면 null, 주소만으로 연다), [selected] 는 닫을 때 보던 탭(묶음을 되살리면 그 탭을 고른다).
     */
    class Entry(
        val url: String,
        val title: String,
        val state: Bundle?,
        val index: Int,
        val openerId: Long?,
        val desktop: Boolean,
        val batch: Long,
        val selected: Boolean = false,
    ) {
        val label: String get() = title.ifBlank { url }
    }

    /** 새것이 앞. */
    private val entries = ArrayList<Entry>()
    private var nextBatch = 1L

    val all: List<Entry> get() = entries
    val isEmpty: Boolean get() = entries.isEmpty()

    /** 한 번에 닫는 탭들의 묶음 번호. */
    fun newBatch(): Long = nextBatch++

    /** 닫은 탭을 넣는다(주소가 없는 빈 탭은 넣지 않는다 — 되살릴 것이 없다). */
    fun push(entry: Entry) {
        if (entry.url.isEmpty()) return
        entries.add(0, entry)
        while (entries.size > limit) entries.removeAt(entries.size - 1)
    }

    /** 그 묶음을 꺼낸다 — 탭 목록의 자리 차례로(앞 자리부터 넣어야 뒤의 자리가 맞는다). */
    fun takeBatch(batch: Long): List<Entry> {
        val taken = entries.filter { it.batch == batch }
        entries.removeAll { it.batch == batch }
        return taken.sortedBy { it.index }
    }

    /** 하나를 꺼낸다(목록에서 골랐다). 없으면 false. */
    fun take(entry: Entry): Boolean = entries.remove(entry)

    fun countOf(batch: Long): Int = entries.count { it.batch == batch }

    companion object {
        /** 기록 묶음은 탭마다 수 KB~수십 KB(어림 — 재지 않았다)라 스물다섯이면 메모리에 부담이 없다. 모두 닫기 한 번이 다 들어갈 만큼. */
        const val DEFAULT_LIMIT = 25
    }
}
