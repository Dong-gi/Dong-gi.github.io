package io.github.donggi.iroirobrowser.core.sites

import java.io.IOException

/**
 * 갤러리 받기를 하는 사이트 — pixiv(작품·작가)와 hitomi.la(갤러리). 사용자 결정(2026-10-11): 'pixiv, hitomi 기능을 브라우저에 통합' —
 * 데스크톱 다운로더(iroiro-downloader)에서 옮겼다. [folder] 는 공용 '내려받기' 아래 맨 앞 폴더 이름이다.
 */
enum class Site(val folder: String) {
    PIXIV("Pixiv"),
    HITOMI("Hitomi"),
}

/**
 * 주소가 가리키는 것. [key] 는 같은 것을 두 번 받지 않으려는 열쇠(데스크톱의 작업 ID 와 같은 꼴 — `pixiv-artwork-123`), [canonicalUrl] 은 그
 * 페이지의 정규 주소(언어 머리·질의·조각을 뗀 것 — 목록의 '원래 페이지 열기' 가 연다).
 */
sealed class SiteTarget(val site: Site, val id: Long) {
    abstract val key: String
    abstract val canonicalUrl: String

    /** pixiv 작품 하나(일러스트·만화·우고이라). */
    class PixivArtwork(id: Long) : SiteTarget(Site.PIXIV, id) {
        override val key: String get() = "pixiv-artwork-$id"
        override val canonicalUrl: String get() = "https://www.pixiv.net/artworks/$id"
    }

    /** pixiv 작가 — 그 작가의 일러스트와 만화를 모두(데스크톱은 일러스트만 받았다). */
    class PixivUser(id: Long) : SiteTarget(Site.PIXIV, id) {
        override val key: String get() = "pixiv-user-$id"
        override val canonicalUrl: String get() = "https://www.pixiv.net/users/$id"
    }

    /** hitomi.la 갤러리 하나. */
    class HitomiGallery(id: Long) : SiteTarget(Site.HITOMI, id) {
        override val key: String get() = "hitomi-gallery-$id"
        override val canonicalUrl: String get() = "https://hitomi.la/reader/$id.html"
    }

    override fun equals(other: Any?): Boolean = other is SiteTarget && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = key
}

/** 주소 알아보기 — 순수 함수(망에 나가지 않는다). 메뉴·길게 누르기에서 보일지 정할 때 부른다. */
object SiteUrls {

    /** 아는 사이트의 작품·작가·갤러리 주소면 그 대상, 아니면 null(그 밖의 pixiv·hitomi 페이지도 null). */
    fun recognize(url: String): SiteTarget? = UrlRecognizer.recognize(url)
}

/**
 * 사이트에서 작은 덩이(JSON·JS)를 읽는다 — 앱이 준다(길은 앱의 HTTP[중계 또는 예외 규칙], 쿠키·User-Agent 는 WebView 의 것). 블로킹. [limit]
 * 바이트를 넘으면 [java.io.IOException]. 2xx 밖이면 [SiteHttpException]. 파일(그림·zip)은 여기로 받지 않는다 — 앱이 흘려 받으며 쓴다.
 */
fun interface SiteFetch {
    @Throws(IOException::class)
    fun get(url: String, referer: String?, limit: Int): ByteArray
}

/** 사이트가 2xx 밖으로 답했다([code]). 프록시가 만든 답(중계·Squid)이면 [byProxy] — 사이트의 '없음' 이 아니다. */
class SiteHttpException(val code: Int, message: String, val byProxy: Boolean = false) : IOException(message)

/** 사이트가 답했지만 받을 수 없다 — 까닭([reason])을 앱이 글로 바꾼다. [detail] 은 기록용(화면에 그대로 보이지 않는다). */
class SiteException(val reason: Reason, val detail: String? = null) : IOException(detail ?: reason.name) {
    enum class Reason {
        /** 없거나 지워졌거나 비공개. */
        NOT_FOUND,

        /** 로그인해야 볼 수 있다(pixiv 의 연령 제한 작품 등). */
        LOGIN_REQUIRED,

        /** 사이트가 요청을 막았다(봇 확인 페이지·403). */
        BLOCKED,

        /** 받을 파일이 없다. */
        EMPTY,

        /** 답의 꼴이 예상과 다르다(사이트가 바뀌었을 수 있다 — JSON 이 아닌 것, 필드가 없는 것). */
        UNEXPECTED,

        /** 너무 크다(단위가 [GalleryResolver.MAX_UNITS] 를 넘는다 등). */
        TOO_LARGE,
    }
}
