package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.SafeFileName
import java.io.IOException
import java.io.InterruptedIOException

/** 받을 파일 하나 — 주소, 저장 이름(확장자까지, 이미 정리한 것), 그 요청의 Referer(pixiv·hitomi 모두 없으면 거절한다). */
class GalleryFile(val url: String, val fileName: String, val referer: String)

/**
 * 갤러리 받기의 한 단위 — 디스크에 적어 두었다가([GalleryCodec]) 차례가 오면 [GalleryResolver.expand] 가 파일로 푼다. 단위로 나누는 것은
 * **받을 때 다시 물어야 하는 것** 때문이다 — hitomi 의 그림 주소는 `gg.js` 가 수시로 바꾸고, pixiv 작가의 작품은 수천 개라 미리 다 묻지 않는다.
 */
sealed class GalleryUnit {
    /** 주소가 정해진 파일(pixiv 의 쪽·우고이라 zip). */
    class Direct(val file: GalleryFile) : GalleryUnit()

    /** hitomi 그림 하나 — 주소는 받을 때 `gg.js` 로 셈한다([hash] 는 sha256 16진, [ext] 는 고른 확장자). */
    class HitomiImage(val galleryId: Long, val hash: String, val ext: String, val fileName: String) : GalleryUnit()

    /** pixiv 작품 하나 — 작가 받기에서 차례가 오면 묻고 그 쪽들(또는 우고이라 zip)로 푼다. */
    class PixivWork(val id: Long) : GalleryUnit()
}

/**
 * 받기 계획 — 확인 창에 보일 것과 받을 것.
 *
 * @property title 확인 창·목록에 보일 이름(작품 제목·작가 이름·갤러리 제목)
 * @property creator 작가·그룹(모르면 null)
 * @property folder 공용 '내려받기' 아래 폴더 성분(이미 정리한 것) — `["Pixiv", "<작가>"]`, `["Hitomi", "<작가 또는 그룹>", "<id>_<제목>"]`
 * @property units 차례대로 받을 단위
 * @property fileCount 알면 파일 수(작품의 쪽 수·갤러리의 장 수), 작가 받기는 null(작품을 풀 때 안다)
 */
class GalleryPlan(
    val target: SiteTarget,
    val title: String,
    val creator: String?,
    val folder: List<String>,
    val units: List<GalleryUnit>,
    val fileCount: Int?,
)

/**
 * 망에서 읽어 계획을 짓고([plan]), 받을 때 단위를 파일로 푼다([expand]). 블로킹 — 일꾼 스레드에서 부른다. [cancelled] 가 참이 되면 다음
 * 요청 전에 [java.io.InterruptedIOException] 을 던진다. 실패는 [SiteException]·[SiteHttpException]·[IOException].
 *
 * hitomi 의 `gg.js` 는 한 인스턴스 안에서 [GG_TTL_MS] 동안 기억한다 — 그림이 404 면 앱이 [invalidate] 를 부르고 한 번 더 푼다.
 */
class GalleryResolver(private val fetch: SiteFetch, private val now: () -> Long = System::currentTimeMillis) {

    // gg.js 의 기억 — 상태는 [ggState] 아래에서만 읽고 쓴다. 받기는 [ggFetching] 아래에서 하나씩(둘이 동시에 받지 않게) — 받는 동안
    // [invalidate] 가 막히지 않게 두 자물쇠로 가른다. 받는 사이 버려졌으면([ggGeneration] 이 바뀌었다) 받은 것을 기억하지 않는다.
    private val ggState = Any()
    private val ggFetching = Any()
    private var gg: Hitomi.Gg? = null
    private var ggAt = 0L
    private var ggGeneration = 0L

    /** 대상을 읽어 계획을 짓는다 — pixiv 작품은 작품 정보와 쪽들, pixiv 작가는 작품 번호들, hitomi 는 갤러리 정보. */
    @Throws(IOException::class)
    fun plan(target: SiteTarget, cancelled: () -> Boolean = { false }): GalleryPlan = when (target) {
        is SiteTarget.PixivArtwork -> planPixivArtwork(target, cancelled)
        is SiteTarget.PixivUser -> planPixivUser(target, cancelled)
        is SiteTarget.HitomiGallery -> planHitomi(target, cancelled)
    }

    /** 단위를 받을 파일들로 — [GalleryUnit.Direct] 는 그대로, hitomi 그림은 주소를 셈하고, pixiv 작품은 묻는다(지워졌으면 [SiteException]). */
    @Throws(IOException::class)
    fun expand(unit: GalleryUnit, cancelled: () -> Boolean = { false }): List<GalleryFile> = when (unit) {
        is GalleryUnit.Direct -> listOf(unit.file)
        is GalleryUnit.HitomiImage -> listOf(hitomiFile(unit, cancelled))
        is GalleryUnit.PixivWork -> {
            if (SiteResponses.positiveId(unit.id.toString()) == null) SiteResponses.unexpected("pixiv 작품 번호 ${unit.id}")
            pixivWork(unit.id, cancelled).files
        }
    }

    /** 기억한 `gg.js` 를 버린다(그림이 404 였다 — 그 사이 바뀌었을 수 있다). */
    fun invalidate(): Unit {
        synchronized(ggState) {
            gg = null
            ggGeneration++
        }
    }

    // ── 받기 ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 덩이 하나를 받는다 — 받기 전에 취소를 보고, 사이트의 2xx 밖 답을 우리 까닭으로 옮긴다([SiteResponses.classify]). [missing] 은 404 의 뜻
     * (대상이면 NOT_FOUND, gg.js 처럼 사이트의 부품이면 UNEXPECTED).
     */
    private fun load(
        url: String,
        referer: String,
        cancelled: () -> Boolean,
        what: String,
        missing: SiteException.Reason = SiteException.Reason.NOT_FOUND,
    ): ByteArray {
        if (cancelled()) throw InterruptedIOException("취소했다")
        val bytes = try {
            fetch.get(url, referer, FETCH_LIMIT)
        } catch (e: SiteHttpException) {
            throw SiteResponses.classify(e, missing, what)
        }
        // 앱의 받기가 상한을 지키지만(계약) 여기서도 한 번 — 넘은 덩이를 읽개에 넘기지 않는다.
        if (bytes.size > FETCH_LIMIT) throw SiteException(SiteException.Reason.TOO_LARGE, "$what: ${bytes.size} 바이트")
        return bytes
    }

    // ── pixiv ───────────────────────────────────────────────────────────────────────────────────────────────

    private class PixivFiles(val info: Pixiv.Illust, val files: List<GalleryFile>)

    private fun planPixivArtwork(target: SiteTarget.PixivArtwork, cancelled: () -> Boolean): GalleryPlan {
        val work = pixivWork(target.id, cancelled)
        val info = work.info
        return GalleryPlan(
            target = target,
            title = SiteNames.display(info.title) ?: target.id.toString(),
            creator = SiteNames.display(info.userName),
            folder = listOf(Site.PIXIV.folder, pixivFolder(info.userName, info.userId ?: 0L)),
            units = work.files.map { GalleryUnit.Direct(it) },
            fileCount = work.files.size,
        )
    }

    /**
     * 작가 — 이름(`ajax/user/{id}`)과 작품 번호(`profile/all` 의 일러스트와 만화). 단위는 [GalleryUnit.PixivWork] 이고 계획 때는 작품을 묻지
     * 않는다(수천 개다 — 받을 차례가 오면 [expand] 가 묻는다). 그래서 [GalleryPlan.fileCount] 는 null.
     */
    private fun planPixivUser(target: SiteTarget.PixivUser, cancelled: () -> Boolean): GalleryPlan {
        val referer = target.canonicalUrl
        val userName = Pixiv.userName(
            Pixiv.body(load(Pixiv.userUrl(target.id), referer, cancelled, "user ${target.id}"), "user ${target.id}"),
            target.id,
        )
        val ids = Pixiv.workIds(
            Pixiv.body(load(Pixiv.profileUrl(target.id), referer, cancelled, "profile ${target.id}"), "profile ${target.id}"),
            target.id,
        )
        if (ids.isEmpty()) throw SiteException(SiteException.Reason.EMPTY, "user ${target.id}: 작품이 없다")
        if (ids.size > MAX_UNITS) throw SiteException(SiteException.Reason.TOO_LARGE, "user ${target.id}: 작품 ${ids.size}")
        return GalleryPlan(
            target = target,
            title = SiteNames.display(userName) ?: target.id.toString(),
            creator = SiteNames.display(userName),
            folder = listOf(Site.PIXIV.folder, pixivFolder(userName, target.id)),
            units = ids.map { GalleryUnit.PixivWork(it) },
            fileCount = null,
        )
    }

    /** 작가 폴더 — 이름을 다듬어서. 다듬으면 비는 이름(점·빈칸뿐)이면 작가 번호. */
    private fun pixivFolder(userName: String, userId: Long): String {
        val cleaned = SiteNames.clean(userName)
        val usable = !(cleaned == SafeFileName.FALLBACK && userName.trim() != cleaned)
        return if (usable || userId <= 0L) cleaned else userId.toString()
    }

    /**
     * 작품 하나를 받을 파일로 — 우고이라면 `ugoira_meta` 의 zip, 한 쪽이면 `urls.original`, 여러 쪽이면 `pages`. 작품 정보의 `urls.original` 이
     * null 이면 로그인해야 보이는 작품이다. 연령 제한·로그인 전용 작품의 `pages`·`ugoira_meta` 가 '없음' 이면 그것도 로그인 필요로 읽는다.
     */
    private fun pixivWork(id: Long, cancelled: () -> Boolean): PixivFiles {
        val referer = SiteTarget.PixivArtwork(id).canonicalUrl
        val what = "illust $id"
        val info = Pixiv.illust(Pixiv.body(load(Pixiv.illustUrl(id), referer, cancelled, what), what), id)
        val original = info.original ?: throw SiteException(SiteException.Reason.LOGIN_REQUIRED, "$what: urls.original 이 null")
        val files = when {
            info.type == Pixiv.UGOIRA -> {
                val zip = restrictedAware(info) {
                    Pixiv.ugoiraZip(Pixiv.body(load(Pixiv.ugoiraUrl(id), referer, cancelled, "ugoira $id"), "ugoira $id"), id)
                } ?: throw SiteException(
                    if (info.restricted) SiteException.Reason.LOGIN_REQUIRED else SiteException.Reason.UNEXPECTED,
                    "ugoira $id: zip 주소가 없다",
                )
                listOf(GalleryFile(zip, Pixiv.ugoiraName(id, info.title), Pixiv.IMAGE_REFERER))
            }
            info.pageCount <= 0L -> throw SiteException(SiteException.Reason.EMPTY, "$what: pageCount ${info.pageCount}")
            info.pageCount == 1L -> {
                listOf(GalleryFile(original, Pixiv.pageName(id, info.title, null, Pixiv.extension(original, what)), Pixiv.IMAGE_REFERER))
            }
            else -> {
                if (info.pageCount > MAX_UNITS) throw SiteException(SiteException.Reason.TOO_LARGE, "$what: 쪽 ${info.pageCount}")
                val urls = restrictedAware(info) {
                    Pixiv.pages(Pixiv.body(load(Pixiv.pagesUrl(id), referer, cancelled, "pages $id"), "pages $id"), id)
                }
                if (urls.isEmpty()) throw SiteException(SiteException.Reason.EMPTY, "pages $id: 쪽이 없다")
                if (urls.size > MAX_UNITS) throw SiteException(SiteException.Reason.TOO_LARGE, "pages $id: 쪽 ${urls.size}")
                val names = SiteNames.unique(urls.mapIndexed { page, url -> Pixiv.pageName(id, info.title, page, Pixiv.extension(url, what)) })
                urls.mapIndexed { page, url -> GalleryFile(url, names[page], Pixiv.IMAGE_REFERER) }
            }
        }
        return PixivFiles(info, files)
    }

    private inline fun <T> restrictedAware(info: Pixiv.Illust, block: () -> T): T = try {
        block()
    } catch (e: SiteException) {
        if (info.restricted && e.reason == SiteException.Reason.NOT_FOUND) {
            throw SiteException(SiteException.Reason.LOGIN_REQUIRED, "illust ${info.id}: 제한 작품의 ${e.detail}")
        }
        throw e
    }

    // ── hitomi ──────────────────────────────────────────────────────────────────────────────────────────────

    /** 갤러리 정보 하나로 계획을 — 그림 주소는 받을 때 셈한다(`gg.js` 가 수시로 바뀐다). */
    private fun planHitomi(target: SiteTarget.HitomiGallery, cancelled: () -> Boolean): GalleryPlan {
        val what = "gallery ${target.id}"
        val gallery = Hitomi.parseGallery(load(Hitomi.galleryUrl(target.id), Hitomi.readerUrl(target.id), cancelled, what), target.id)
        if (gallery.files.isEmpty()) throw SiteException(SiteException.Reason.EMPTY, "$what: 파일이 없다")
        if (gallery.files.size > MAX_UNITS) throw SiteException(SiteException.Reason.TOO_LARGE, "$what: 파일 ${gallery.files.size}")
        return GalleryPlan(
            target = target,
            title = gallery.title,
            creator = gallery.creator,
            folder = gallery.folder,
            units = gallery.files.map { GalleryUnit.HitomiImage(target.id, it.hash, it.ext, it.name) },
            fileCount = gallery.files.size,
        )
    }

    private fun hitomiFile(unit: GalleryUnit.HitomiImage, cancelled: () -> Boolean): GalleryFile {
        if (!Hitomi.isHash(unit.hash) || unit.ext !in Hitomi.EXTENSIONS || SiteResponses.positiveId(unit.galleryId.toString()) == null) {
            SiteResponses.unexpected("hitomi 단위의 꼴(${unit.galleryId})")
        }
        val gg = currentGg(cancelled)
        return GalleryFile(Hitomi.imageUrl(gg, unit.hash, unit.ext), unit.fileName, Hitomi.readerUrl(unit.galleryId))
    }

    /** 기억한 gg.js 가 [GG_TTL_MS] 안이면 그것을, 아니면 받는다. 시계가 거꾸로 갔으면 낡은 것으로 본다. */
    private fun currentGg(cancelled: () -> Boolean): Hitomi.Gg {
        freshGg()?.let { return it }
        synchronized(ggFetching) {
            val generation = synchronized(ggState) {
                freshGgLocked()?.let { return it }
                ggGeneration
            }
            val fetched = Hitomi.parseGg(load(Hitomi.GG_URL, "https://hitomi.la/", cancelled, "gg.js", SiteException.Reason.UNEXPECTED))
            synchronized(ggState) {
                if (ggGeneration == generation) {
                    gg = fetched
                    ggAt = now()
                }
            }
            return fetched
        }
    }

    private fun freshGg(): Hitomi.Gg? = synchronized(ggState) { freshGgLocked() }

    private fun freshGgLocked(): Hitomi.Gg? {
        val cached = gg ?: return null
        val age = now() - ggAt
        return if (age in 0 until GG_TTL_MS) cached else null
    }

    companion object {
        /** 계획 하나의 단위 상한 — 넘으면 [SiteException.Reason.TOO_LARGE]. */
        const val MAX_UNITS = 20_000

        /** pixiv 작가 받기에서 작품과 작품 사이에 쉬는 시간(데스크톱은 0.3초) — 앱의 받기가 [GalleryUnit.PixivWork] 사이에 쉰다. */
        const val PIXIV_GAP_MS = 600L

        /** `gg.js` 를 기억하는 시간(gallery-dl 과 같다 — 운영 쪽이 수시로 바꾼다). */
        const val GG_TTL_MS = 30 * 60 * 1000L

        /** [SiteFetch] 에 주는 상한 — JSON·JS 한 덩이. */
        const val FETCH_LIMIT = 8 * 1024 * 1024
    }
}

/** 계획을 글(JSON)로 — 앱이 받기 항목 곁의 파일에 적어 두고 다시 뜰 때 읽는다. 깨졌으면 [SiteException] (`UNEXPECTED`). */
object GalleryCodec {
    fun encode(plan: GalleryPlan): String = PlanJson.encode(plan)

    @Throws(SiteException::class)
    fun decode(text: String): GalleryPlan = PlanJson.decode(text)
}
