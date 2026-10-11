package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.hash
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GalleryCodecTest {

    /** 계획을 견줄 수 있는 꼴로(GalleryPlan 에는 equals 가 없다). */
    private fun describe(plan: GalleryPlan): List<Any?> = listOf(
        plan.target.key, plan.target.javaClass.simpleName, plan.title, plan.creator, plan.folder, plan.fileCount,
        plan.units.map { unit ->
            when (unit) {
                is GalleryUnit.Direct -> listOf("file", unit.file.url, unit.file.fileName, unit.file.referer)
                is GalleryUnit.HitomiImage -> listOf("hitomi", unit.galleryId, unit.hash, unit.ext, unit.fileName)
                is GalleryUnit.PixivWork -> listOf("pixiv", unit.id)
            }
        },
    )

    private fun roundTrip(plan: GalleryPlan) {
        val text = GalleryCodec.encode(plan)
        assertEquals(describe(plan), describe(GalleryCodec.decode(text)), text)
    }

    private val pixivArtwork = GalleryPlan(
        target = SiteTarget.PixivArtwork(123),
        title = "제목 \"따옴표\" \\ 역빗금\n줄",
        creator = "작가",
        folder = listOf("Pixiv", "작가"),
        units = listOf(
            GalleryUnit.Direct(GalleryFile("https://i.pximg.net/img-original/img/2026/01/01/00/00/00/123_p0.png", "123_제목_p000.png", "https://www.pixiv.net/")),
            GalleryUnit.Direct(GalleryFile("https://i.pximg.net/img-original/img/2026/01/01/00/00/00/123_p1.jpg", "123_제목_p001.jpg", "https://www.pixiv.net/")),
        ),
        fileCount = 2,
    )

    private val pixivUser = GalleryPlan(
        target = SiteTarget.PixivUser(11),
        title = "작가",
        creator = null,
        folder = listOf("Pixiv", "작가"),
        units = listOf(GalleryUnit.PixivWork(300), GalleryUnit.PixivWork(200), GalleryUnit.PixivWork(100)),
        fileCount = null,
    )

    private val hitomi = GalleryPlan(
        target = SiteTarget.HitomiGallery(4242),
        title = "갤러리 😀",
        creator = "그룹",
        folder = listOf("Hitomi", "그룹", "4242_갤러리"),
        units = listOf(
            GalleryUnit.HitomiImage(4242, hash(1), "avif", "001.avif"),
            GalleryUnit.HitomiImage(4242, hash(2), "webp", "002 (2).webp"),
        ),
        fileCount = 2,
    )

    @Test
    fun 왕복() {
        roundTrip(pixivArtwork)
        roundTrip(pixivUser)
        roundTrip(hitomi)
        roundTrip(GalleryPlan(SiteTarget.PixivUser(1), "", null, listOf("Pixiv"), emptyList(), 0))
        // 홀로 선 서로게이트가 든 제목도 그대로 돌아온다(글로 쓰면 이스케이프).
        roundTrip(GalleryPlan(SiteTarget.HitomiGallery(1), "a\uD800b", "\uDC00", listOf("Hitomi"), emptyList(), null))
    }

    @Test
    fun 판_번호와_꼴이_적힌다() {
        val text = GalleryCodec.encode(pixivUser)
        assertTrue(text.startsWith("{\"format\":\"iroiro-gallery-plan\",\"v\":1,"), text)
        val decoded = GalleryCodec.decode(text)
        assertIs<SiteTarget.PixivUser>(decoded.target)
        assertNull(decoded.fileCount)
    }

    @Test
    fun 큰_계획도_왕복한다() {
        val units = List(GalleryResolver.MAX_UNITS) { i ->
            GalleryUnit.HitomiImage(99, hash(i), if (i % 2 == 0) "avif" else "webp", "${"이름".repeat(30)} ($i).avif")
        }
        val plan = GalleryPlan(SiteTarget.HitomiGallery(99), "t", null, listOf("Hitomi", "unknown", "99_t"), units, units.size)
        roundTrip(plan)
    }

    private fun reason(text: String): SiteException.Reason = assertFailsWith<SiteException>(text) { GalleryCodec.decode(text) }.reason

    private fun mutated(edit: (MutableMap<String, Any?>) -> Unit): String {
        @Suppress("UNCHECKED_CAST")
        val root = Json.parse(GalleryCodec.encode(hitomi), Json.SITE) as MutableMap<String, Any?>
        edit(root)
        return Json.write(root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun firstUnit(root: MutableMap<String, Any?>): MutableMap<String, Any?> = (root["units"] as List<Any?>)[0] as MutableMap<String, Any?>

    @Test
    fun 깨졌거나_모르는_판은_UNEXPECTED() {
        val good = GalleryCodec.encode(hitomi)
        val bad = listOf(
            "", "{", "[]", "null", good.dropLast(1), good + "x", "\u0000",
            mutated { it["v"] = 2L },
            mutated { it["v"] = "1" },
            mutated { it.remove("v") },
            mutated { it["format"] = "other" },
            mutated { it.remove("format") },
            mutated { it["target"] = mapOf("kind" to "pixiv-novel", "id" to 1L) },
            mutated { it["target"] = mapOf("kind" to "hitomi-gallery", "id" to 0L) },
            mutated { it["target"] = mapOf("kind" to "hitomi-gallery", "id" to -5L) },
            mutated { it["target"] = mapOf("kind" to "hitomi-gallery", "id" to 1234567890123L) },
            mutated { it["target"] = mapOf("kind" to "hitomi-gallery", "id" to "4242") },
            mutated { it.remove("title") },
            mutated { it["creator"] = 5L },
            mutated { it["folder"] = emptyList<String>() },
            mutated { it["folder"] = listOf("Hitomi", "..") },
            mutated { it["folder"] = listOf("Hitomi", "a/b") },
            mutated { it["folder"] = listOf("Hitomi", "a\\b") },
            mutated { it["folder"] = listOf("Hitomi", " 앞빈칸") },
            mutated { it["folder"] = listOf("Hitomi", "") },
            mutated { it["folder"] = listOf("Hitomi", "a\u0000b") },
            mutated { it["folder"] = listOf("Hitomi", "a\uD800") },
            mutated { it["folder"] = List(9) { "x" } },
            mutated { it["fileCount"] = -1L },
            mutated { it["fileCount"] = 1.5 },
            mutated { it["units"] = "x" },
            mutated { it["units"] = listOf(1L) },
            mutated { firstUnit(it)["t"] = "video" },
            mutated { firstUnit(it)["hash"] = "abc" },
            mutated { firstUnit(it)["hash"] = hash(1).uppercase() },
            mutated { firstUnit(it)["ext"] = "jxl" },
            mutated { firstUnit(it)["ext"] = "jpg" },
            mutated { firstUnit(it)["gallery"] = 0L },
            mutated { firstUnit(it)["name"] = "../001.avif" },
            mutated { firstUnit(it)["name"] = "" },
            mutated { firstUnit(it)["name"] = "x".repeat(300) + ".avif" },
            mutated { firstUnit(it).remove("name") },
            mutated { it["units"] = listOf(mapOf("t" to "file", "url" to "file:///etc/passwd", "name" to "a.png", "referer" to "https://x/")) },
            mutated { it["units"] = listOf(mapOf("t" to "file", "url" to "javascript:alert(1)", "name" to "a.png", "referer" to "https://x/")) },
            mutated { it["units"] = listOf(mapOf("t" to "file", "url" to "https://x/a b.png", "name" to "a.png", "referer" to "https://x/")) },
            mutated { it["units"] = listOf(mapOf("t" to "file", "url" to "https://x/a.png", "name" to "a.png")) },
            mutated { it["units"] = listOf(mapOf("t" to "pixiv", "id" to 0L)) },
            mutated { it["units"] = listOf(mapOf("t" to "pixiv")) },
            mutated { it["units"] = List(GalleryResolver.MAX_UNITS + 1) { mapOf("t" to "pixiv", "id" to 1L) } },
        )
        for (text in bad) {
            assertEquals(SiteException.Reason.UNEXPECTED, reason(text), text.take(200))
        }
    }

    @Test
    fun 모르는_키는_무시한다() {
        val text = mutated {
            it["later"] = mapOf("x" to 1L)
            firstUnit(it)["extra"] = true
        }
        assertEquals(describe(hitomi), describe(GalleryCodec.decode(text)))
    }

    /** 왕복한 글을 뒤틀면 — 계획이 나오거나 UNEXPECTED. 그 밖의 예외는 없어야 한다(고정 씨앗). */
    @Test
    fun 뒤튼_글() {
        val random = Random(4242)
        val texts = listOf(GalleryCodec.encode(pixivArtwork), GalleryCodec.encode(pixivUser), GalleryCodec.encode(hitomi))
        val pool = "{}[]\",:0-.\\/ az\u0000"
        var accepted = 0
        repeat(5_000) { round ->
            val s = StringBuilder(texts[round % texts.size])
            repeat(1 + random.nextInt(3)) {
                val at = random.nextInt(s.length)
                when (random.nextInt(3)) {
                    0 -> s.deleteCharAt(at)
                    1 -> s.insert(at, pool[random.nextInt(pool.length)])
                    else -> s.setCharAt(at, pool[random.nextInt(pool.length)])
                }
            }
            try {
                GalleryCodec.decode(s.toString())
                accepted++
            } catch (e: SiteException) {
                assertEquals(SiteException.Reason.UNEXPECTED, e.reason)
            } catch (e: Throwable) {
                fail("판 $round: ${e.javaClass.name} ${e.message} — $s")
            }
        }
        // 글자 하나를 바꿔도 여전히 올바른 계획인 판(제목 안의 글자 등)이 있어야 '받는 길' 도 지난 것이다.
        assertTrue(accepted > 0)
    }
}
