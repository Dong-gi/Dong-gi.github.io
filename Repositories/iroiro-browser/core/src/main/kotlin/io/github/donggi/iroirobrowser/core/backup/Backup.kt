package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.adblock.HideRule
import java.io.IOException

/**
 * 설정 백업 하나 — 설정 › 백업의 '설정 내보내기' 가 쓰는 판 번호 붙은 JSON 파일([BackupJson])의 내용(사용자 요청 2026-10-16 — '북마크·설정
 * 내보내기/가져오기 => 구현해주세요'). 릴리스도 이 PC 의 디버그 키로 서명하고 앱 자료는 백업·기기 이전에서 빠지므로(`data_extraction_rules`)
 * 다시 깔면 이것이 북마크·설정을 옮기는 길이다.
 *
 * 칸이 null 이면 '파일에 없다' 다 — 가져올 때 그 칸은 지금 값을 그대로 둔다([ImportPlan]).
 *
 * **넣지 않는 것** — 프록시 비밀번호(기기 키로 잠겨 다시 깐 앱·다른 기기에서 열리지 않는다)와 프록시 스위치(가져오기가 직접 연결을 켜지
 * 않게 — fail-closed), 테더링(따로 된 프로세스·따로 된 설정), 쿠키·사이트 저장소, 열린 탭, 내려받기·토렌트. 방문 기록과 프록시의 주소·
 * 사용자는 내보낼 때 고른 경우에만 든다.
 */
data class Backup(
    /** 만든 앱의 판(보이기만 한다). */
    val app: String? = null,
    /** 만든 때(밀리초). */
    val created: Long? = null,
    val settings: Settings? = null,
    val proxy: ProxyAccount? = null,
    val adblock: AdBlock? = null,
    /** 사용자가 숨긴 요소(`사이트##선택자`). */
    val hidden: List<HideRule>? = null,
    /** 팝업을 허용한 사이트(등록 도메인). */
    val popupSites: List<String>? = null,
    val bookmarks: List<BookmarkEntry>? = null,
    val history: List<HistoryEntry>? = null,
) {
    /** 낱개 설정. 예외 규칙과 출구 확인 주소는 프록시 설정의 일부라 가져올 때 프록시 설정의 저장 길(`NetworkPath.update`)을 지난다. */
    data class Settings(
        val searchEngine: String? = null,
        val fullscreen: Boolean? = null,
        val forceZoom: Boolean? = null,
        val torrentWifiOnly: Boolean? = null,
        val exitCheckUrl: String? = null,
        val bypass: BypassPart? = null,
        /** 살아 있는 탭의 수(2026-10-16 — 판 1 에 더한 칸, 옛 파일에는 없다). */
        val liveTabs: Int? = null,
        /** 방문 기록 보관 기간(일, 0 = 기간 없음)·캐시 비우기 주기(일, 0 = 하지 않음) — 2026-10-16 에 더한 칸. */
        val historyDays: Int? = null,
        val cacheDays: Int? = null,
    )

    /** 예외 규칙 — 칸마다 없으면 지금 값. 있으면 그 칸을 통째로 바꾼다(설정 화면의 칸 하나처럼). */
    data class BypassPart(
        val hosts: List<String>? = null,
        val cidrs: List<String>? = null,
        val privateNetworks: Boolean? = null,
        val simpleHostnames: Boolean? = null,
    )

    /** 프록시의 주소와 사용자 — **비밀번호 칸은 없다.** */
    data class ProxyAccount(val host: String, val port: Int, val user: String)

    data class AdBlock(
        val enabled: Boolean? = null,
        val wifiOnly: Boolean? = null,
        /** 들어 있는 목록마다 켜졌는가(목록의 id). */
        val lists: List<ListState>? = null,
        /** 사용자가 주소로 더한 목록. */
        val customLists: List<CustomList>? = null,
        /** 광고 차단을 끈 사이트(등록 도메인). */
        val allowedSites: List<String>? = null,
    )

    data class ListState(val id: String, val on: Boolean)

    data class CustomList(val url: String, val title: String, val on: Boolean)
}

/** 북마크 하나 — [created] 는 넣은 때(밀리초). */
data class BookmarkEntry(val url: String, val title: String, val created: Long)

/** 방문 기록 한 줄 — 앱의 `history` 표와 같은 칸(주소마다 한 줄). */
data class HistoryEntry(val url: String, val title: String, val lastVisit: Long, val visits: Int, val typed: Int)

/**
 * 받지 않은 파일. [reason] 으로 화면이 까닭을 고른다 — 글([message])은 기록용이다. `IOException` 인 것은 파일 읽기의 실패와 한 갈래로 잡히게
 * 하려는 것이다(부르는 쪽은 이것을 먼저 잡는다).
 */
class BackupException(val reason: Reason, message: String) : IOException(message) {
    enum class Reason {
        /** 상한(파일 크기·값의 수·항목 수)을 넘었다. */
        TOO_LARGE,

        /** 이 앱의 설정 백업이 아니다(`format` 이 다르다). */
        NOT_BACKUP,

        /** 이 앱이 아는 것보다 새 판의 파일이다. */
        NEWER_VERSION,

        /** JSON 이 깨졌다. */
        MALFORMED,
    }
}
