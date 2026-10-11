package io.github.donggi.iroirobrowser.torrent

/**
 * 엔진이 받지 않거나 하지 못한 까닭. 화면의 글은 앱이 이 값으로 고른다(엔진은 화면 문구를 만들지 않는다 — `:net` 의 중계와 같다).
 */
enum class TorrentError {
    /** .torrent·메타데이터가 깨졌거나 규격에 맞지 않는다([TorrentException.detail] 에 기계 말로 무엇인지). */
    INVALID_TORRENT,

    /** `private=1`(초대제 트래커의 토렌트) — 받지 않는다(사용자 결정 ⑪). 그 트래커는 올린 양으로 회원을 정지시킨다. */
    PRIVATE_TORRENT,

    /** 순수 v2(BEP 52) — 지원 안 함(사용자 결정 ③). 하이브리드는 v1 으로 받는다. */
    V2_ONLY,

    /** 상한을 넘었다(파일 수 10만·조각 수·경로 길이·메타데이터 10 MiB). */
    TOO_LARGE,

    /** 마그넷 주소가 깨졌다. */
    INVALID_MAGNET,

    /** 같은 info-hash 의 토렌트가 이미 있다. */
    DUPLICATE,

    /** 그런 토렌트가 없다. */
    UNKNOWN_TORRENT,

    /** 저장 공간이 모자라다. */
    DISK_FULL,

    /** 파일을 쓰거나 읽지 못했다. */
    DISK_ERROR,

    /** 엔진이 닫혔다. */
    ENGINE_CLOSED,
}

class TorrentException(val error: TorrentError, val detail: String? = null) :
    Exception(if (detail == null) error.name else "${error.name}: $detail")
