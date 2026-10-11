package io.github.donggi.iroirobrowser.core.sites

/**
 * [GalleryCodec] 의 몸 — 계획을 판 번호가 붙은 JSON 으로.
 *
 * ```
 * {"format":"iroiro-gallery-plan","v":1,
 *  "target":{"kind":"pixiv-artwork","id":123},
 *  "title":"…","creator":"…"|null,"folder":["Pixiv","…"],"fileCount":3|null,
 *  "units":[{"t":"file","url":"…","name":"…","referer":"…"},
 *           {"t":"hitomi","gallery":123,"hash":"…64…","ext":"avif","name":"…"},
 *           {"t":"pixiv","id":123}]}
 * ```
 *
 * 다시 읽을 때 **모두 다시 본다** — 앱 안의 파일이지만 깨질 수 있고(전원·저장 공간), 폴더 성분과 파일 이름은 앱이 그대로 경로로 쓰므로
 * `..`·구분자·제어 문자가 든 것은 받지 않는다([SiteNames.isClean]). 모르는 판·모르는 단위 종류는 UNEXPECTED(뒤의 판이 쓴 글을 앞의 판이
 * 읽은 것일 수 있다). 모르는 키는 무시한다(같은 판 안에서 덧붙인 것).
 */
internal object PlanJson {

    const val FORMAT = "iroiro-gallery-plan"
    const val VERSION = 1L

    /**
     * 우리 계획이 낼 수 있는 가장 큰 글보다 넉넉하게 — 단위 2만 × (주소 1,024자 + 이름 + 꼬리) 가 약 2,700만 자, hitomi 2만 장은 약 800만 자.
     * 실제로는 수 MB 에 그친다.
     */
    private val LIMITS = Json.Limits(
        maxChars = 32 * 1024 * 1024,
        maxDepth = 8,
        maxStringChars = MAX_TEXT_CHARS,
        maxValues = GalleryResolver.MAX_UNITS * 12 + 1_000,
    )

    /** 제목·작가·주소 글 하나의 상한(제목은 [SiteNames.MAX_DISPLAY] 코드 포인트로 짓지만 앱이 지은 계획도 받게 넉넉히). */
    private const val MAX_TEXT_CHARS = 8 * 1024

    private const val MAX_FOLDER_DEPTH = 8

    fun encode(plan: GalleryPlan): String {
        val root = LinkedHashMap<String, Any?>()
        root["format"] = FORMAT
        root["v"] = VERSION
        root["target"] = linkedMapOf("kind" to kindOf(plan.target), "id" to plan.target.id)
        root["title"] = plan.title
        root["creator"] = plan.creator
        root["folder"] = plan.folder
        root["fileCount"] = plan.fileCount?.toLong()
        root["units"] = plan.units.map { unit ->
            when (unit) {
                is GalleryUnit.Direct -> linkedMapOf(
                    "t" to "file",
                    "url" to unit.file.url,
                    "name" to unit.file.fileName,
                    "referer" to unit.file.referer,
                )
                is GalleryUnit.HitomiImage -> linkedMapOf(
                    "t" to "hitomi",
                    "gallery" to unit.galleryId,
                    "hash" to unit.hash,
                    "ext" to unit.ext,
                    "name" to unit.fileName,
                )
                is GalleryUnit.PixivWork -> linkedMapOf("t" to "pixiv", "id" to unit.id)
            }
        }
        return Json.write(root)
    }

    private fun kindOf(target: SiteTarget): String = when (target) {
        is SiteTarget.PixivArtwork -> "pixiv-artwork"
        is SiteTarget.PixivUser -> "pixiv-user"
        is SiteTarget.HitomiGallery -> "hitomi-gallery"
    }

    fun decode(text: String): GalleryPlan {
        val parsed = try {
            Json.parse(text, LIMITS)
        } catch (e: JsonException) {
            bad("JSON — ${e.message}")
        }
        val root = parsed as? Map<*, *> ?: bad("객체가 아니다")
        if (root["format"] != FORMAT) bad("모르는 꼴")
        val version = root["v"] as? Long ?: bad("판 번호가 없다")
        if (version != VERSION) bad("모르는 판 $version")

        val targetMap = root["target"] as? Map<*, *> ?: bad("target 이 없다")
        val targetId = id(targetMap["id"]) ?: bad("target 의 번호")
        val target = when (targetMap["kind"]) {
            "pixiv-artwork" -> SiteTarget.PixivArtwork(targetId)
            "pixiv-user" -> SiteTarget.PixivUser(targetId)
            "hitomi-gallery" -> SiteTarget.HitomiGallery(targetId)
            else -> bad("모르는 대상 종류")
        }
        val title = root["title"] as? String ?: bad("title 이 없다")
        val creator = when (val c = root["creator"]) {
            null -> null
            is String -> c
            else -> bad("creator 의 꼴")
        }
        val folderList = root["folder"] as? List<*> ?: bad("folder 가 없다")
        if (folderList.isEmpty() || folderList.size > MAX_FOLDER_DEPTH) bad("folder 의 깊이")
        val folder = folderList.map { part -> (part as? String)?.takeIf { SiteNames.isClean(it) } ?: bad("folder 성분") }
        val fileCount = when (val n = root["fileCount"]) {
            null -> null
            is Long -> if (n in 0..Int.MAX_VALUE.toLong()) n.toInt() else bad("fileCount 의 범위")
            else -> bad("fileCount 의 꼴")
        }
        val unitList = root["units"] as? List<*> ?: bad("units 가 없다")
        if (unitList.size > GalleryResolver.MAX_UNITS) bad("단위가 너무 많다")
        val units = unitList.map { unit(it) }
        return GalleryPlan(target, title, creator, folder, units, fileCount)
    }

    private fun unit(value: Any?): GalleryUnit {
        val map = value as? Map<*, *> ?: bad("단위가 객체가 아니다")
        return when (map["t"]) {
            "file" -> GalleryUnit.Direct(
                GalleryFile(
                    url = webUrl(map["url"]) ?: bad("파일 주소"),
                    fileName = name(map["name"]),
                    referer = webUrl(map["referer"]) ?: bad("파일의 Referer"),
                ),
            )
            "hitomi" -> {
                val hash = (map["hash"] as? String)?.takeIf { Hitomi.isHash(it) } ?: bad("hitomi 해시")
                val ext = (map["ext"] as? String)?.takeIf { it in Hitomi.EXTENSIONS } ?: bad("hitomi 확장자")
                GalleryUnit.HitomiImage(id(map["gallery"]) ?: bad("hitomi 갤러리 번호"), hash, ext, name(map["name"]))
            }
            "pixiv" -> GalleryUnit.PixivWork(id(map["id"]) ?: bad("pixiv 작품 번호"))
            else -> bad("모르는 단위 종류")
        }
    }

    private fun name(value: Any?): String = (value as? String)?.takeIf { SiteNames.isClean(it) } ?: bad("파일 이름")

    private fun id(value: Any?): Long? = (value as? Long)?.let { SiteResponses.positiveId(it.toString()) }

    /** 주소는 http(s) 이고 공백·제어 문자가 없다(앱이 그대로 요청에 쓴다). */
    private fun webUrl(value: Any?): String? {
        val url = value as? String ?: return null
        if (url.length > Pixiv.MAX_URL_CHARS) return null
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        if (url.any { it <= ' ' || it.code == 0x7F }) return null
        return url
    }

    private fun bad(what: String): Nothing = throw SiteException(SiteException.Reason.UNEXPECTED, "계획 글: $what")
}
