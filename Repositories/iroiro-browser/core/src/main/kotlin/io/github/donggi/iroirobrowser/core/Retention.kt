package io.github.donggi.iroirobrowser.core

/**
 * 인터넷 사용 기록을 두는 기간(설정 › 인터넷 사용 기록 — 사용자 요청 2026-10-16: '현재 방문기록/캐시 등을 며칠동안 유지하고 있나요? 그 부분도
 * 설정 가능하면 좋겠네요'). 0 은 '기간 없음' 이다 — 방문 기록은 주소마다 한 줄로 최근 1만 곳까지(앱의 `Library`), 캐시는 WebView 가 용량으로만
 * 다룬다(날짜로 지우지 않는다). 쿠키·사이트 저장소는 여기서 다루지 않는다 — 기간으로 지우면 로그인이 풀린다(사이트가 정한 만료까지 둔다).
 */
object Retention {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** 방문 기록 보관(일) — 마지막으로 간 때가 이보다 오래된 주소를 지운다. 고르는 창의 차례. */
    val HISTORY_DAYS = listOf(0, 365, 90, 30, 7, 1)

    /** 캐시 비우기(일) — 마지막으로 비운 지 이만큼 지나면 WebView 의 HTTP 캐시를 통째로 비운다. 고르는 창의 차례. */
    val CACHE_DAYS = listOf(0, 30, 7, 1)

    /** 지울 방문 기록의 기준 — 마지막으로 간 때가 이보다 앞선 주소. 기간 없음(또는 모르는 값)이면 null. */
    fun historyCutoff(days: Int, now: Long): Long? = if (days > 0 && days in HISTORY_DAYS) now - days * DAY_MS else null

    /**
     * 캐시를 비울 때인가 — 주기가 있고, 비운 적이 있으며([clearedAt] > 0 — 주기를 켤 때 그때를 적는다, 켜자마자 비우지 않는다), 그 뒤로 [days] 일이
     * 지났다. 시계가 거꾸로 가서 비운 때가 앞날이면 참이다(비우고 다시 센다 — 그대로 두면 그날까지 비우지 않는다).
     */
    fun cacheDue(days: Int, clearedAt: Long, now: Long): Boolean =
        days > 0 && days in CACHE_DAYS && clearedAt > 0 && (now < clearedAt || now - clearedAt >= days * DAY_MS)
}
