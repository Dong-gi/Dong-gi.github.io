package io.github.donggi.iroirobrowser.core.media

/**
 * 탭 하나가 찾은 미디어 — **페이지마다 경계를 둔다**(사용자가 짚었다, 2026-10-07: '찾은 미디어가 지금 페이지의 것인지 가릴 수 없다').
 *
 * 앞 판은 주 문서를 **요청할 때** 목록을 비웠다. 그래서 ① SPA 이동(pushState·해시 — 유튜브 모바일의 다음 영상)은 비우지 않아 지금 영상이 맨
 * 아래에 깔렸고 ② 링크를 누른 뒤 새 페이지가 커밋되기 전(해외 프록시면 수 초) 옛 재생기가 낸 요청이 새 목록에 들어갔고 ③ 페이지가 바뀌지 않는
 * 탐색(첨부 응답·204)을 누르면 영상은 그대로인데 목록이 비었다. 이제는 지우지 않고 **탐색이 커밋될 때 경계만 옮긴다**([commit]).
 *
 * - 항목마다 찾은 시각과 그 페이지(주소·제목)를 든다. 지금 페이지의 것은 최근에 요청된 차례로 위에, 앞 페이지의 것은 페이지마다 아래에 남는다
 *   (앞 페이지 [maxPreviousPages] 개까지, 떠난 지 [keepMs] 안의 것만). 같은 미디어가 다시 요청되면 지금 페이지로 올라온다.
 * - **커밋 알림은 새 문서의 첫 요청보다 늦다** — 렌더러가 커밋하고 요청을 내는 동안 브라우저 쪽의 알림은 메인 스레드에 줄 서 있다. 그래서 다른
 *   문서로의 커밋이면 그 탐색이 **시작된 뒤 처음 찾은 것**을 새 페이지로 옮긴다. 옛 재생기가 그 사이 다시 낸 요청(재생목록 갱신·조각)은 이미
 *   있던 항목이라 옛 페이지에 남는다. 그 사이 옛 재생기가 처음 낸 것(화질 바꾸기의 새 재생목록)은 새 페이지로 가지만 마스터 아래로 숨는다.
 * - 조각(`#…`)만 바뀐 같은 문서 안의 이동은 경계가 아니다.
 * - 번호만 다른 같은 경로의 영상·소리 파일은 한 항목으로 묶는다([MediaSniffer.seriesKey]) — [SERIES_MIN] 개부터 한 줄로 보인다.
 * - HLS 마스터를 읽으면([readDone]) 그 아래 재생목록(화질·소리·자막)을 숨긴다 — 그 줄을 고르면 소리 없는 영상·소리만·자막 조각이 받아졌다.
 *
 * 시각은 부르는 쪽이 준다(단조 시계 — `SystemClock.elapsedRealtime`). 찾기는 WebView 의 스레드에서, 나머지는 메인 스레드에서 부른다 —
 * 모두 이 객체로 잠근다.
 */
class FoundMediaLog(
    private val maxPreviousPages: Int = MAX_PREVIOUS_PAGES,
    private val keepMs: Long = KEEP_MS,
) {

    /** 페이지 하나 — 커밋된 주소와 그동안의 제목(제목은 커밋 뒤에 따로 온다). [left] 는 떠난 때(지금 페이지면 0). */
    class Page internal constructor(val serial: Int, val url: String, title: String, val since: Long) {
        @Volatile
        var title: String = title
            internal set

        @Volatile
        var left: Long = 0L
            internal set
    }

    /** HLS 재생목록을 뒤에서 읽은 결과. */
    enum class Read { NONE, PENDING, MASTER, MEDIA, FAILED }

    /** 찾은 미디어 하나 — 번호만 다른 같은 경로의 파일들은 한 항목이다(목록의 줄은 [Row]). */
    class Entry internal constructor(val kind: MediaKind, val url: String, val foundAt: Long, page: Page) {
        @Volatile
        var page: Page = page
            internal set

        /** 마지막으로 요청된 때. */
        @Volatile
        var seenAt: Long = foundAt
            internal set

        @Volatile
        var read: Read = Read.NONE
            internal set

        /** 읽은 마스터면 그 아래 재생목록의 수. */
        @Volatile
        var master: Master? = null
            internal set

        /** 번호만 다른 파일들(처음 찾은 차례, [MAX_MEMBERS] 개까지) — 로그의 잠금 안에서만 만진다. */
        internal val members = LinkedHashSet<String>().apply { add(url) }

        /** 찾은 페이지의 주소 — 받을 때 Referer 를 정한다. 첫 커밋 전에 찾았으면 빈 글이다. */
        val pageUrl: String get() = page.url

        /**
         * 찾은 때의 페이지 제목 — 받는 파일의 기본 이름. 찾을 때 적어 두고(그 뒤 SPA 가 제목을 바꿔도 이 항목의 것은 그대로), 그때 제목이 아직
         * 없었으면 그 페이지의 제목이 오는 대로 채운다([retitle]). 지금 페이지로 옮겨지면 그 페이지의 것으로.
         */
        @Volatile
        var pageTitle: String = page.title
            internal set
    }

    /** 마스터 재생목록의 아래 — 화질·따로 된 소리·자막의 수와 그 주소들. */
    class Master internal constructor(val variants: Int, val audio: Int, val subtitles: Int, internal val children: Set<String>)

    /** 목록 창의 한 줄 — [urls] 가 둘 이상이면 번호만 다른 파일의 묶음이다(처음 찾은 차례). */
    class Row internal constructor(val entry: Entry, val urls: List<String>) {
        val url: String get() = urls.first()
        val series: Boolean get() = urls.size > 1
    }

    class Section internal constructor(val page: Page, val rows: List<Row>)

    /** 목록 창에 보일 것 — 지금 페이지([page])의 줄과, 앞 페이지마다의 줄(최근에 떠난 페이지부터). */
    class Listing internal constructor(val page: Page, val current: List<Row>, val previous: List<Section>) {
        val isEmpty: Boolean get() = current.isEmpty() && previous.isEmpty()
    }

    /** [found] 의 답 — 목록이 어떻게 바뀌었나. */
    enum class Change {
        /** 그대로다(지금 페이지에 있는 것을 다시 요청했다). */
        NONE,

        /** 처음 찾았다. */
        NEW,

        /** 앞 페이지에 있던 것이 지금 페이지로 올라왔다. */
        MOVED,

        /** 묶음에 번호만 다른 파일이 하나 늘었다. */
        GREW,
    }

    private var serial = 0
    private var page = Page(0, "", "", 0L)
    private val entries = LinkedHashMap<String, Entry>()
    private val readsByPage = HashMap<Int, Int>()

    /** 지금 페이지. */
    val currentPage: Page
        @Synchronized get() = page

    /** 쪽이 낸 요청 하나가 미디어다([MediaSniffer.classify]). WebView 의 스레드에서 부른다. */
    @Synchronized
    fun found(url: String, kind: MediaKind, now: Long): Change {
        val member = MediaSniffer.key(url)
        val key = when (kind) {
            MediaKind.VIDEO, MediaKind.AUDIO -> MediaSniffer.seriesKey(member)?.let { SERIES_PREFIX + it } ?: member
            else -> member
        }
        val e = entries[key]
        if (e == null) {
            entries[key] = Entry(kind, member, now, page)
            raiseMasterOf(member, now)
            trim(now)
            return Change.NEW
        }
        e.seenAt = now
        var change = Change.NONE
        if (e.members.size < MAX_MEMBERS && e.members.add(member)) change = Change.GREW
        if (e.page !== page) {
            moveToCurrent(e)
            change = Change.MOVED
        }
        if (raiseMasterOf(member, now)) change = Change.MOVED
        return change
    }

    /**
     * 탐색이 커밋됐다 — 경계를 옮긴다(목록은 지우지 않는다). [sameDocument] 는 같은 문서 안의 이동(pushState·해시)인가 — 모르면 null(탐색
     * 알림이 없는 WebView). 같은 문서이거나 모르는데 주소가 조각(`#…`)까지 빼고 같으면 경계가 아니다. [navigationStart] 는 다른 문서로 가는 그
     * 탐색이 시작된 때 — 그 뒤에 처음 찾은 것은 새 페이지의 것이다(머리말). 경계를 옮겼으면 true.
     */
    @Synchronized
    fun commit(url: String, title: String, now: Long, sameDocument: Boolean?, navigationStart: Long?): Boolean {
        val old = page
        if (sameDocument != false && old.url.isNotEmpty() && old.url.substringBefore('#') == url.substringBefore('#')) return false
        val next = Page(++serial, url, title, now)
        old.left = now
        page = next
        // 빈 탭(첫 커밋 전·about:blank)에서 찾은 것은 이 페이지의 것이다 — 첫 탐색의 요청도 커밋 알림보다 먼저 온다.
        val blank = old.url.isEmpty() || old.url == "about:blank"
        for (e in entries.values) {
            if (e.page !== old) continue
            if (blank || (sameDocument == false && navigationStart != null && e.foundAt >= navigationStart)) moveToCurrent(e)
        }
        trim(now)
        return true
    }

    /** 지금 페이지의 제목이 왔다 — 제목 없이 찾은 항목에도 채운다. */
    @Synchronized
    fun retitle(title: String) {
        page.title = title
        for (e in entries.values) if (e.page === page && e.pageTitle.isBlank()) e.pageTitle = title
    }

    /** 목록 창에 보일 것. 오래된 앞 페이지는 여기서도 버린다. */
    @Synchronized
    fun listing(now: Long): Listing {
        trim(now)
        val hidden = hiddenUrls()
        val visible = entries.values.filter { it.kind != MediaKind.HLS || it.url !in hidden }
        val current = rows(visible.filter { it.page === page })
        val previous = visible.filter { it.page !== page }.groupBy { it.page }.entries
            .sortedByDescending { it.key.left }
            .map { (p, list) -> Section(p, rows(list)) }
        return Listing(page, current, previous)
    }

    /** 지금 페이지에서 찾은 줄의 수 — 막대 단추에 보인다. */
    @Synchronized
    fun currentCount(): Int {
        val hidden = hiddenUrls()
        return entries.values.filter { it.page === page && (it.kind != MediaKind.HLS || it.url !in hidden) }
            .sumOf { if (it.members.size >= SERIES_MIN) 1 else it.members.size }
    }

    /** 앞 페이지까지 하나도 없는가(오래된 앞 페이지는 먼저 버린다 — 막대의 단추를 감출지). */
    @Synchronized
    fun isEmpty(now: Long): Boolean {
        trim(now)
        return entries.isEmpty()
    }

    /**
     * 뒤에서 읽을 HLS 재생목록을 하나 맡는다 — 아직 읽지 않았고, 이미 읽은 마스터 아래의 것이 아니고, 그 페이지에서 [MAX_READS_PER_PAGE] 번을
     * 넘지 않았으면 true(그동안 [Read.PENDING]). 재생기가 이미 받은 것을 한 번 더 받는 것이라(프록시로) 페이지마다 수를 묶는다.
     */
    @Synchronized
    fun claimRead(url: String): Boolean {
        val member = MediaSniffer.key(url)
        val e = entries[member] ?: return false
        if (e.kind != MediaKind.HLS || e.read != Read.NONE) return false
        if (masterEntryOf(member) != null) {
            e.read = Read.MEDIA
            return false
        }
        val n = readsByPage[e.page.serial] ?: 0
        if (n >= MAX_READS_PER_PAGE) return false
        readsByPage[e.page.serial] = n + 1
        e.read = Read.PENDING
        return true
    }

    /** 뒤에서 읽은 결과 — 마스터면 그 아래 재생목록을 숨긴다. [playlist] 가 null 이면 읽지 못했다. */
    @Synchronized
    fun readDone(url: String, playlist: HlsPlaylist?) {
        val e = entries[MediaSniffer.key(url)] ?: return
        when (playlist) {
            is HlsPlaylist.Master -> {
                e.read = Read.MASTER
                e.master = Master(
                    playlist.variants.size, playlist.audio.count { it.uri != null }, playlist.subtitles.count { it.uri != null },
                    playlist.childUris.mapTo(HashSet()) { MediaSniffer.key(it) },
                )
            }
            is HlsPlaylist.Media -> e.read = Read.MEDIA
            null -> e.read = Read.FAILED
        }
    }

    /** [url] 을 아래에 둔 마스터(읽은 것). 하위 재생목록을 고르면 그 마스터의 화질 창으로 돌린다. */
    @Synchronized
    fun masterOf(url: String): Entry? = masterEntryOf(MediaSniffer.key(url))

    /** 숨긴 재생목록이 요청되면 그 마스터를 지금 페이지로 올린다 — 그 스트림이 아직 돈다. 올렸으면 true. */
    private fun raiseMasterOf(member: String, now: Long): Boolean {
        val m = masterEntryOf(member) ?: return false
        m.seenAt = maxOf(m.seenAt, now)
        if (m.page === page) return false
        moveToCurrent(m)
        return true
    }

    private fun moveToCurrent(e: Entry) {
        e.page = page
        e.pageTitle = page.title
    }

    private fun masterEntryOf(member: String): Entry? = entries.values.firstOrNull { it.master?.children?.contains(member) == true }

    private fun hiddenUrls(): Set<String> {
        val out = HashSet<String>()
        for (e in entries.values) e.master?.let { out.addAll(it.children) }
        return out
    }

    private fun rows(list: List<Entry>): List<Row> = list.sortedByDescending { it.seenAt }.flatMap { e ->
        if (e.members.size >= SERIES_MIN) listOf(Row(e, e.members.toList())) else e.members.map { Row(e, listOf(it)) }
    }

    /** 오래된 앞 페이지([keepMs] 넘게 지났거나 [maxPreviousPages] 개 넘는 것)를 버리고, 너무 많으면 오래 안 본 것부터(앞 페이지 먼저). */
    private fun trim(now: Long) {
        val previous = entries.values.map { it.page }.filter { it !== page }.distinct().sortedByDescending { it.left }
        val keep = previous.filterIndexed { i, p -> i < maxPreviousPages && now - p.left <= keepMs }.toHashSet()
        entries.values.removeAll { it.page !== page && it.page !in keep }
        if (entries.size > MAX_ENTRIES) {
            val drop = entries.entries.sortedWith(compareBy<Map.Entry<String, Entry>>({ it.value.page === page }, { it.value.seenAt }))
                .take(entries.size - MAX_ENTRIES).map { it.key }
            drop.forEach { entries.remove(it) }
        }
        val live = entries.values.mapTo(HashSet()) { it.page.serial }.apply { add(page.serial) }
        readsByPage.keys.retainAll(live)
    }

    companion object {
        /** 앞 페이지를 몇 개까지 남기나. */
        const val MAX_PREVIOUS_PAGES = 3

        /**
         * 떠난 지 이만큼 지난 앞 페이지의 것은 버린다 — 그동안은 막대의 단추가 흐리게 남는다(앞 페이지의 것만 있을 때). 뒤로 가서 다시 요청되면
         * 지금 페이지의 것으로 되살아난다.
         */
        const val KEEP_MS = 10 * 60 * 1000L

        /** 번호만 다른 파일이 이만큼 되면 한 줄로 묶어 보인다 — 둘은 같은 쪽의 다른 영상(`clip1`·`clip2`)일 때가 많다. */
        const val SERIES_MIN = 3

        /** 묶음이 기억하는 파일 수. */
        const val MAX_MEMBERS = 500

        /** 탭 하나가 기억하는 항목 수. */
        const val MAX_ENTRIES = 200

        /** 페이지마다 뒤에서 읽는 HLS 재생목록의 수. */
        const val MAX_READS_PER_PAGE = 6

        private const val SERIES_PREFIX = "series:"
    }
}
