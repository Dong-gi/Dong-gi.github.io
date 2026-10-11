package io.github.donggi.iroirobrowser.core.sites

import java.util.Locale

/**
 * pixiv 의 웹 AJAX 를 읽는다 — 데스크톱의 OAuth·앱 API 가 아니라 브라우저가 쓰는 것이고, 로그인은 WebView 의 쿠키로 된다(쿠키·UA 는 앱이
 * [SiteFetch] 안에서 싣는다). 답은 봉투 `{"error":bool,"message":str,"body":…}` 다.
 *
 * 2026-10-11 실측(로그인 없이, 공식 계정 user 11 의 전체 연령 작품): `ajax/illust/{id}` 의 body 에 `illustId`(글)·`illustTitle`·`illustType`(정수)·
 * `pageCount`·`userId`(글)·`userName`·`urls.original`·`xRestrict`·`isLoginOnly` 가 있고, 그 작가의 모든 작품 번호(`userIllusts`)까지 실어 작품이
 * 2,316 개인 작가에서 약 119 KB 였다. `pages` 의 body 는 `[{"urls":{"original":…},"width","height"}]`, `profile/all` 의 `illusts`·`manga` 는
 * 번호 → null 맵. 없는 작품과 우고이라가 아닌 작품의 `ugoira_meta` 는 **HTTP 404** 와 `error:true` 봉투였다(그래서 404 를 '없음' 으로 읽는다 —
 * [SiteFetch] 는 2xx 밖의 몸을 주지 않는다). 연령 제한 작품은 열어 보지 않았다 — `urls.original` 이 null 인 것을 로그인 필요로 읽는다(과제의 명세).
 */
internal object Pixiv {

    private const val WEB = "https://www.pixiv.net"

    /** 원본 그림·우고이라 zip(i.pximg.net)에 붙이는 Referer — 없으면 403(데스크톱 문서). */
    const val IMAGE_REFERER = "https://www.pixiv.net/"

    const val UGOIRA = 2L

    /** 제목이 이름 안에서 차지할 수 있는 바이트 — 번호·`_p000`·확장자를 붙여도 [io.github.donggi.iroirobrowser.core.SafeFileName.MAX_BYTES] 를 넘지 않게. */
    private const val TITLE_BYTES = 120

    /** 사이트가 준 그림 주소의 길이 상한(실측 약 90자) — 계획 글의 크기를 묶는다. */
    const val MAX_URL_CHARS = 1024

    fun illustUrl(id: Long) = "$WEB/ajax/illust/$id"
    fun pagesUrl(id: Long) = "$WEB/ajax/illust/$id/pages"
    fun ugoiraUrl(id: Long) = "$WEB/ajax/illust/$id/ugoira_meta"
    fun userUrl(id: Long) = "$WEB/ajax/user/$id"
    fun profileUrl(id: Long) = "$WEB/ajax/user/$id/profile/all"

    /** 작품 정보 가운데 받기에 쓰는 것. [original] 이 null 이면 로그인해야 보이는 작품이다. [restricted] 는 연령 제한·로그인 전용. */
    class Illust(
        val id: Long,
        val title: String,
        val type: Long,
        val pageCount: Long,
        val userId: Long?,
        val userName: String,
        val original: String?,
        val restricted: Boolean,
    )

    /**
     * 봉투를 벗긴다. `error:true` 면 메시지에 로그인·연령 제한이 보이면 LOGIN_REQUIRED, 아니면 NOT_FOUND(지워졌거나 비공개). 2xx 로 온
     * 봉투만 여기 닿는다 — 404 의 봉투는 [SiteResponses.classify] 가 이미 NOT_FOUND 로 바꿨다.
     */
    fun body(bytes: ByteArray, what: String): Any? {
        val root = SiteResponses.json(SiteResponses.text(bytes), what) as? Map<*, *> ?: SiteResponses.unexpected("$what: 봉투가 객체가 아니다")
        val error = root["error"] as? Boolean ?: SiteResponses.unexpected("$what: error 가 없다")
        if (error) {
            val message = root["message"] as? String ?: ""
            val reason = if (mentionsLogin(message)) SiteException.Reason.LOGIN_REQUIRED else SiteException.Reason.NOT_FOUND
            throw SiteException(reason, "$what: ${message.take(200)}")
        }
        if (!root.containsKey("body")) SiteResponses.unexpected("$what: body 가 없다")
        return root["body"]
    }

    /** 봉투의 메시지는 사이트의 언어(Accept-Language)를 따른다 — 일·영·한·중의 '로그인'·연령 제한 낱말을 본다. */
    fun mentionsLogin(message: String): Boolean {
        val m = message.lowercase(Locale.ROOT)
        return LOGIN_WORDS.any { it in m }
    }

    private val LOGIN_WORDS = listOf("ログイン", "login", "log in", "sign in", "로그인", "登录", "登入", "r-18", "r18", "年齢", "연령")

    fun illust(body: Any?, id: Long): Illust {
        val what = "illust $id"
        val map = body as? Map<*, *> ?: SiteResponses.unexpected("$what: body 가 객체가 아니다")
        val shownId = SiteResponses.integer(map["illustId"]) ?: SiteResponses.integer(map["id"])
            ?: SiteResponses.unexpected("$what: illustId 가 없다")
        if (shownId != id) SiteResponses.unexpected("$what: 다른 작품의 답($shownId)")
        val title = map["illustTitle"] as? String ?: map["title"] as? String ?: SiteResponses.unexpected("$what: illustTitle 이 없다")
        val type = SiteResponses.integer(map["illustType"]) ?: SiteResponses.unexpected("$what: illustType 이 없다")
        val pageCount = SiteResponses.integer(map["pageCount"]) ?: SiteResponses.unexpected("$what: pageCount 가 없다")
        val userName = map["userName"] as? String ?: SiteResponses.unexpected("$what: userName 이 없다")
        val urls = map["urls"] as? Map<*, *> ?: SiteResponses.unexpected("$what: urls 가 없다")
        if (!urls.containsKey("original")) SiteResponses.unexpected("$what: urls.original 이 없다")
        val original = when (val o = urls["original"]) {
            null -> null
            is String -> imageUrl(o, what)
            else -> SiteResponses.unexpected("$what: urls.original 의 꼴")
        }
        val restricted = (SiteResponses.integer(map["xRestrict"]) ?: 0L) > 0L || map["isLoginOnly"] == true
        return Illust(id, title, type, pageCount, SiteResponses.integer(map["userId"]), userName, original, restricted)
    }

    /** `pages` — 쪽마다의 원본 주소. null 인 쪽이 하나라도 있으면 로그인해야 보이는 작품이다. */
    fun pages(body: Any?, id: Long): List<String> {
        val what = "pages $id"
        val list = body as? List<*> ?: SiteResponses.unexpected("$what: body 가 목록이 아니다")
        return list.map { page ->
            val urls = (page as? Map<*, *>)?.get("urls") as? Map<*, *> ?: SiteResponses.unexpected("$what: 쪽에 urls 가 없다")
            when (val o = urls["original"]) {
                is String -> imageUrl(o, what)
                null -> throw SiteException(SiteException.Reason.LOGIN_REQUIRED, "$what: 쪽의 original 이 null")
                else -> SiteResponses.unexpected("$what: original 의 꼴")
            }
        }
    }

    /** `ugoira_meta` — 원본 크기 zip(`originalSrc`), 없으면 작은 것(`src`). 둘 다 없으면 null. */
    fun ugoiraZip(body: Any?, id: Long): String? {
        val what = "ugoira $id"
        val map = body as? Map<*, *> ?: SiteResponses.unexpected("$what: body 가 객체가 아니다")
        val src = map["originalSrc"] as? String ?: map["src"] as? String ?: return null
        return imageUrl(src, what)
    }

    /**
     * `profile/all` — 일러스트**와 만화**의 번호(데스크톱은 `type=illust` 만 받아 만화가 빠졌다), 큰 것(새 것)부터. 비면 `[]` 로 온다.
     * 번호가 아닌 키가 있으면 꼴이 바뀐 것이다 — 조용히 빼지 않고 UNEXPECTED.
     */
    fun workIds(body: Any?, userId: Long): List<Long> {
        val what = "profile $userId"
        val map = body as? Map<*, *> ?: SiteResponses.unexpected("$what: body 가 객체가 아니다")
        val ids = HashSet<Long>()
        for (key in listOf("illusts", "manga")) {
            when (val group = map[key]) {
                null -> Unit
                is Map<*, *> -> for (k in group.keys) ids += workId(k, what)
                is List<*> -> for (k in group) ids += workId(k, what)
                else -> SiteResponses.unexpected("$what: $key 의 꼴")
            }
        }
        return ids.sortedDescending()
    }

    private fun workId(value: Any?, what: String): Long {
        val text = when (value) {
            is String -> value
            is Long -> value.toString()
            else -> SiteResponses.unexpected("$what: 작품 번호의 꼴")
        }
        return SiteResponses.positiveId(text) ?: SiteResponses.unexpected("$what: 작품 번호가 아니다")
    }

    fun userName(body: Any?, userId: Long): String {
        val map = body as? Map<*, *> ?: SiteResponses.unexpected("user $userId: body 가 객체가 아니다")
        return map["name"] as? String ?: SiteResponses.unexpected("user $userId: name 이 없다")
    }

    /**
     * 사이트가 준 그림 주소를 받기 전에 본다 — https, pximg.net 의 호스트, 길이. 앱이 그 주소로 받기를 하므로 남의 호스트·다른 스킴을 들이지
     * 않는다(쿠키를 그 호스트에 싣게 될 수 있다).
     */
    fun imageUrl(url: String, what: String): String {
        if (url.length > MAX_URL_CHARS || !url.startsWith("https://")) SiteResponses.unexpected("$what: 그림 주소의 꼴")
        val hostEnd = url.indexOf('/', 8)
        val host = (if (hostEnd < 0) url.substring(8) else url.substring(8, hostEnd)).lowercase(Locale.ROOT)
        if (hostEnd < 0 || !(host == "pximg.net" || host.endsWith(".pximg.net"))) SiteResponses.unexpected("$what: 그림의 호스트")
        if (url.any { it <= ' ' || it == '\\' || it.code >= 0x7F }) SiteResponses.unexpected("$what: 그림 주소의 글자")
        return url
    }

    /** 주소 경로의 확장자(`.png`) — 데스크톱의 `Path(url).suffix` 와 같다. 영숫자 1~8자가 아니면 꼴이 바뀐 것이다. */
    fun extension(url: String, what: String): String {
        val path = url.substringBefore('#').substringBefore('?')
        val last = path.substringAfterLast('/')
        val dot = last.lastIndexOf('.')
        val ext = if (dot > 0) last.substring(dot + 1) else ""
        if (ext.isEmpty() || ext.length > 8 || !ext.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) {
            SiteResponses.unexpected("$what: 그림 주소에 확장자가 없다")
        }
        return "." + ext.lowercase(Locale.ROOT)
    }

    /** 한 쪽 `{id}_{제목}{ext}`, 여러 쪽 `{id}_{제목}_p{000}{ext}`(0 부터 세 자리) — 데스크톱과 같다. */
    fun pageName(id: Long, title: String, page: Int?, ext: String): String {
        val base = "${id}_${titlePart(title)}"
        val name = if (page == null) "$base$ext" else "${base}_p${page.toString().padStart(3, '0')}$ext"
        return SiteNames.clean(name)
    }

    /** 우고이라 `{id}_{제목}_ugoira.zip` — 데스크톱과 같다(프레임의 시간 정보는 데스크톱처럼 싣지 않는다 — zip 에는 그림만 든다). */
    fun ugoiraName(id: Long, title: String): String = SiteNames.clean("${id}_${titlePart(title)}_ugoira.zip")

    /** 제목을 먼저 줄여 둔다 — 다 붙인 뒤 [SiteNames.clean] 이 줄이면 `_p000` 이 잘려 쪽끼리 이름이 겹친다. */
    private fun titlePart(title: String): String =
        SiteNames.limitUtf8(SiteNames.prepare(title).trim(), TITLE_BYTES).trimEnd('.', ' ')
}
