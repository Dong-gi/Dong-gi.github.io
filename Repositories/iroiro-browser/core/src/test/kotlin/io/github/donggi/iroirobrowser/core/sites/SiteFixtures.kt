package io.github.donggi.iroirobrowser.core.sites

import java.io.IOException

/**
 * 시험의 합성 자료 — 번호·제목·이름은 모두 지어낸 것이다(이 저장소는 GitHub Pages 로 통째 공개된다 — 실제 작품·이름을 넣지 않는다).
 * 꼴은 2026-10-11 실측한 답의 짜임을 흉내 낸다: pixiv 의 봉투와 body 의 키(받기에 쓰지 않는 잡다한 키도 함께), hitomi 의
 * `var galleryinfo = …`(끝의 `;` 없음)·`hasavif` 만 있는 파일 항목, gg.js 의 줄마다 `case N:` 와 한 묶음.
 */
internal object SiteFixtures {

    fun q(text: String): String = Json.write(text)

    fun envelope(body: String): String = """{"error":false,"message":"","body":$body}"""

    fun errorEnvelope(message: String): String = """{"error":true,"message":${q(message)},"body":[]}"""

    fun pximg(id: Long, page: Int, ext: String = "png"): String =
        "https://i.pximg.net/img-original/img/2026/01/02/03/04/05/${id}_p$page.$ext"

    /** `ajax/illust/{id}` 의 body — 받기에 쓰는 키에 더해 실측처럼 작가의 작품 맵과 잡다한 키를 싣는다. */
    fun illustBody(
        id: Long,
        title: String = "시험 작품",
        type: Int = 0,
        pageCount: Int = 1,
        userId: Long = 900_001,
        userName: String = "시험 작가",
        original: String? = pximg(id, 0),
        xRestrict: Int = 0,
        loginOnly: Boolean = false,
        idAsString: Boolean = true,
    ): String {
        val idValue = if (idAsString) q(id.toString()) else id.toString()
        val originalValue = original?.let { q(it) } ?: "null"
        return """
            {"illustId":$idValue,"illustTitle":${q(title)},"illustComment":"설명","id":$idValue,"title":${q(title)},
             "illustType":$type,"createDate":"2026-01-02T03:04:05+00:00","restrict":0,"xRestrict":$xRestrict,"sl":2,
             "urls":{"mini":null,"thumb":null,"small":null,"regular":null,"original":$originalValue},
             "tags":{"authorId":"$userId","isLocked":false,"tags":[{"tag":"태그","locked":true}],"writable":true},
             "userId":"$userId","userName":${q(userName)},"userAccount":"tester",
             "userIllusts":{"$id":{"id":"$id","title":${q(title)},"illustType":$type},"${id - 1}":null,"${id - 2}":null},
             "width":1200,"height":800,"pageCount":$pageCount,"bookmarkCount":3,"likeCount":4,
             "extraData":{"meta":{"canonical":"https://www.pixiv.net/artworks/$id"}},
             "isLoginOnly":$loginOnly,"aiType":1,"noLoginData":{"zengoIdWorks":[]}}
        """.trimIndent()
    }

    fun pagesBody(urls: List<String?>): String = urls.joinToString(",", "[", "]") { url ->
        val u = url?.let { q(it) } ?: "null"
        """{"urls":{"thumb_mini":null,"small":null,"regular":null,"original":$u},"width":1200,"height":800}"""
    }

    fun ugoiraBody(originalSrc: String?, src: String? = null): String {
        val parts = ArrayList<String>()
        if (originalSrc != null) parts += "\"originalSrc\":${q(originalSrc)}"
        if (src != null) parts += "\"src\":${q(src)}"
        parts += "\"mime_type\":\"image/jpeg\""
        parts += "\"frames\":[{\"file\":\"000000.jpg\",\"delay\":100}]"
        return parts.joinToString(",", "{", "}")
    }

    fun profileBody(illusts: List<Long>, manga: List<Long>): String {
        fun group(ids: List<Long>) = if (ids.isEmpty()) "[]" else ids.joinToString(",", "{", "}") { "\"$it\":null" }
        return """{"illusts":${group(illusts)},"manga":${group(manga)},"novels":[],"mangaSeries":[],"pickup":[],
            "bookmarkCount":{"public":{"illust":0}},"externalSiteWorksStatus":{"booth":false}}"""
    }

    fun userBody(name: String, id: Long = 900_001): String =
        """{"userId":"$id","name":${q(name)},"image":"https://i.pximg.net/user-profile/img/x.jpg","premium":false,"partial":0}"""

    /** 합성 해시 — sha256 꼴(64자 16진). [tail] 로 끝 세 자리를 정해 g 를 고른다. */
    fun hash(seed: Int, tail: String = ""): String {
        val base = StringBuilder()
        var x = seed.toLong() * 2_654_435_761L + 12_345L
        while (base.length < 64) {
            x = (x * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L)
            base.append("0123456789abcdef"[((x ushr 33) and 15L).toInt()])
        }
        return base.substring(0, 64 - tail.length) + tail
    }

    class HFile(val name: String?, val hash: String, val avif: Any? = 1L, val extra: String = "")

    fun hitomiGallery(
        id: Long,
        title: String = "시험 갤러리",
        artists: List<String>? = listOf("작가 하나"),
        groups: List<String>? = listOf("그룹 하나"),
        files: List<HFile>,
        idAsString: Boolean = true,
        semicolon: Boolean = false,
    ): String {
        fun people(list: List<String>?, key: String) = list?.joinToString(",", "[", "]") { """{"url":"/$key/x-all.html","$key":${q(it)}}""" } ?: "null"
        val fileText = files.joinToString(",", "[", "]") { f ->
            val fields = ArrayList<String>()
            fields += "\"hash\":${q(f.hash)}"
            fields += "\"width\":1000"
            if (f.avif != null) fields += "\"hasavif\":${if (f.avif is String) q(f.avif) else f.avif}"
            fields += "\"height\":1500"
            if (f.name != null) fields += "\"name\":${q(f.name)}"
            if (f.extra.isNotEmpty()) fields += f.extra
            fields.joinToString(",", "{", "}")
        }
        val idValue = if (idAsString) q(id.toString()) else id.toString()
        val body = """{"language_url":"/index-korean.html","parodys":null,"blocked":0,"video":null,"groups":${people(groups, "group")},
            "japanese_title":null,"galleryurl":"/doujinshi/x-$id.html","type":"doujinshi","date":"2026-01-02 03:04:00-05",
            "title":${q(title)},"artists":${people(artists, "artist")},"languages":[],"related":[1,2],"id":$idValue,
            "language":"korean","tags":[{"tag":"시험","url":"/tag/x-all.html"}],"characters":null,"files":$fileText}"""
        return "var galleryinfo = $body" + if (semicolon) ";" else ""
    }

    /** gg.js — 실측의 짜임(줄마다 `case N:`, 묶음 끝 `o = X; break;`, 그 뒤 s 와 b). [groups] 는 (값, case 들). */
    fun ggJs(fallback: Int = 1, groups: List<Pair<Int, List<Int>>> = listOf(0 to listOf(2977, 364, 7)), b: String = "'1700000000/'"): String {
        val body = StringBuilder()
        for ((value, cases) in groups) {
            for (c in cases) body.append("case ").append(c).append(":\n")
            body.append("o = ").append(value).append("; break;\n")
        }
        return "'use strict';\ngg = { m: function(g) {\nvar o = $fallback;\nswitch (g) {\n$body}\nreturn o;\n},\n" +
            "s: function(h) { var m = /(..)(.)$/.exec(h); return parseInt(m[2]+m[1], 16).toString(10); },\nb: $b\n};\n"
    }

    /** 가짜 받기 — 주소마다 답(바이트) 또는 예외. 모르는 주소는 사이트의 404. 부른 차례를 적는다. */
    class FakeFetch : SiteFetch {
        class Call(val url: String, val referer: String?, val limit: Int)

        val calls = ArrayList<Call>()
        private val routes = HashMap<String, () -> ByteArray>()

        fun text(url: String, body: String) {
            routes[url] = { body.toByteArray(Charsets.UTF_8) }
        }

        fun bytes(url: String, body: ByteArray) {
            routes[url] = { body }
        }

        fun fail(url: String, error: IOException) {
            routes[url] = { throw error }
        }

        fun urls(): List<String> = calls.map { it.url }

        override fun get(url: String, referer: String?, limit: Int): ByteArray {
            calls += Call(url, referer, limit)
            val route = routes[url] ?: throw SiteHttpException(404, "HTTP 404")
            return route()
        }
    }
}
