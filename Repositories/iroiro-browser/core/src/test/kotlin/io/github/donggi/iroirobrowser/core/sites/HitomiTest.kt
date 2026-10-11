package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.SafeFileName
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.HFile
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.ggJs
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.hash
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.hitomiGallery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HitomiTest {

    private fun gallery(text: String, id: Long): Hitomi.Gallery = Hitomi.parseGallery(text.toByteArray(Charsets.UTF_8), id)

    private fun gg(text: String): Hitomi.Gg = Hitomi.parseGg(text.toByteArray(Charsets.UTF_8))

    private fun reason(block: () -> Unit): SiteException.Reason = assertFailsWith<SiteException> { block() }.reason

    @Test
    fun 갤러리_정보() {
        val files = listOf(
            HFile("001.jpg", hash(1)),
            HFile("002.png", hash(2), avif = 0L),
            HFile("003.gif", hash(3)),
            HFile(null, hash(4)),
            HFile("p/005.jpg", hash(5), avif = null),
            HFile("006.JPG", hash(6).uppercase(), avif = "1"),
        )
        val g = gallery(hitomiGallery(5001, files = files), 5001)
        assertEquals("시험 갤러리", g.title)
        assertEquals("작가 하나", g.creator)
        assertEquals(listOf("Hitomi", "작가 하나", "5001_시험 갤러리"), g.folder)
        assertEquals(listOf("001.avif", "002.webp", "003.webp", "004.avif", "005.webp", "006.avif"), g.files.map { it.name })
        assertEquals(listOf("avif", "webp", "webp", "avif", "webp", "avif"), g.files.map { it.ext })
        // 해시는 소문자로 맞춘다(CDN 의 경로가 소문자다).
        assertEquals(listOf(hash(1), hash(2), hash(3), hash(4), hash(5), hash(6)), g.files.map { it.hash })
    }

    @Test
    fun 머리와_끝의_변형() {
        val files = listOf(HFile("1.jpg", hash(1)))
        val plain = hitomiGallery(7, files = files)
        val body = plain.removePrefix("var galleryinfo = ")
        for (text in listOf(
            plain,
            hitomiGallery(7, files = files, semicolon = true),
            "\n  var galleryinfo\n=\n$body\n;\n",
            "let galleryinfo=$body;",
            body,
            "\uFEFF$plain",
        )) {
            assertEquals(1, gallery(text, 7).files.size, text.take(40))
        }
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("$plain;x", 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("$plain;;", 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("var other = $body", 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("var galleryinfo $body", 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("var galleryinfo = [1]", 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery(plain.dropLast(3), 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("", 7) })
        assertEquals(SiteException.Reason.BLOCKED, reason { gallery("<html><body>403 Forbidden</body></html>", 7) })
    }

    @Test
    fun 다른_갤러리의_답이나_꼴이_다른_파일은_UNEXPECTED() {
        val files = listOf(HFile("1.jpg", hash(1)))
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery(hitomiGallery(7, files = files), 8) })
        assertEquals(7, gallery(hitomiGallery(7, files = files, idAsString = false), 7).folder[2].substringBefore('_').toInt())
        for (bad in listOf("abc", hash(1).take(63), hash(1) + "0", hash(1).replaceRange(0, 1, "g"))) {
            assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery(hitomiGallery(7, files = listOf(HFile("1.jpg", bad))), 7) }, bad)
        }
        val noHash = "var galleryinfo = {\"id\":\"7\",\"title\":\"t\",\"files\":[{\"name\":\"1.jpg\",\"hasavif\":1}]}"
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery(noHash, 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("var galleryinfo = {\"id\":\"7\",\"files\":{}}", 7) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { gallery("var galleryinfo = {\"id\":\"7\",\"files\":[1]}", 7) })
        // files 가 없거나 null 이면 빈 목록 — 받을 것이 없다는 판단은 계획이 한다(EMPTY).
        assertEquals(0, gallery("var galleryinfo = {\"id\":\"7\",\"title\":\"t\",\"files\":null}", 7).files.size)
    }

    @Test
    fun 작가가_없으면_그룹_그것도_없으면_unknown() {
        val files = listOf(HFile("1.jpg", hash(1)))
        val byGroup = gallery(hitomiGallery(9, artists = null, groups = listOf("그룹 갑", "그룹 을"), files = files), 9)
        assertEquals("그룹 갑, 그룹 을", byGroup.creator)
        assertEquals("그룹 갑, 그룹 을", byGroup.folder[1])
        val blankArtist = gallery(hitomiGallery(9, artists = listOf(" "), groups = listOf("그룹"), files = files), 9)
        assertEquals("그룹", blankArtist.creator)
        val nobody = gallery(hitomiGallery(9, artists = null, groups = null, files = files), 9)
        assertNull(nobody.creator)
        assertEquals("unknown", nobody.folder[1])
        val many = gallery(hitomiGallery(9, artists = listOf("갑", "을", "병"), files = files), 9)
        assertEquals("갑, 을, 병", many.folder[1])
        // 작가 이름의 경로 구분자는 폴더를 늘리지 못한다.
        val slash = gallery(hitomiGallery(9, artists = listOf("../../x"), files = files), 9)
        assertEquals(".._.._x".trimStart('.'), slash.folder[1])
        assertTrue(slash.folder.all { SiteNames.isClean(it) })
    }

    @Test
    fun 폴더_제목은_80자에서_자른_뒤_다듬는다() {
        val files = listOf(HFile("1.jpg", hash(1)))
        val title = "a".repeat(72) + "    나머지"
        val g = gallery(hitomiGallery(123456, title = title, files = files), 123456)
        // `123456_` 7자 + a 72자 + 빈칸 하나 = 80자 — 나머지는 잘리고, 자른 끝의 빈칸도 남지 않는다(자른 뒤 다듬는다).
        assertEquals("123456_" + "a".repeat(72), g.folder[2])
        // 80자 안이어도 앱 공용 정리가 200 바이트로 묶는다 — 3바이트 글자는 `123456_` 뒤에 64자.
        val korean = gallery(hitomiGallery(123456, title = "가".repeat(100), files = files), 123456)
        assertEquals("123456_" + "가".repeat(64), korean.folder[2])
        // 80 \uCF54\uB4DC \uD3EC\uC778\uD2B8(`1_` + 78\uC790)\uB85C \uC790\uB978 \uB4A4 \uC571 \uACF5\uC6A9 \uC815\uB9AC\uAC00 200 \uBC14\uC774\uD2B8\uB85C \uB2E4\uC2DC \uBB36\uB294\uB2E4 \u2014 4\uBC14\uC774\uD2B8 \uAE00\uC790\uB294 49\uC790\uAE4C\uC9C0.
        val emoji = gallery(hitomiGallery(1, title = "\uD83D\uDE00".repeat(100), files = files), 1)
        assertEquals("1_" + "\uD83D\uDE00".repeat(49), emoji.folder[2])
        assertFalse(SiteNames.hasLoneSurrogate(emoji.folder[2]))
        // 제목이 비면 번호.
        assertEquals("55_55", gallery(hitomiGallery(55, title = "  ", files = files), 55).folder[2])
        val control = gallery(hitomiGallery(56, title = "줄\n바꿈\t제목", files = files), 56)
        assertEquals("줄 바꿈 제목", control.title)
    }

    @Test
    fun 파일_이름_정리() {
        val files = listOf(
            HFile("../../etc/passwd", hash(1)),
            HFile("a\u202Eb.jpg", hash(2)),
            HFile(".jpg", hash(3)),
            HFile("___.jpg", hash(4)),
            HFile("download.jpg", hash(5)),
            HFile("x".repeat(300) + ".jpg", hash(6)),
            HFile("", hash(7)),
            HFile("a:b?.png", hash(8)),
        )
        val names = gallery(hitomiGallery(3, files = files), 3).files.map { it.name }
        assertEquals("passwd.avif", names[0])
        assertEquals("a_b.avif", names[1])
        assertEquals("jpg.avif", names[2])
        assertEquals("004.avif", names[3])
        assertEquals("download.avif", names[4])
        assertTrue(names[5].endsWith(".avif") && names[5].length <= SafeFileName.MAX_BYTES, names[5])
        assertEquals("007.avif", names[6])
        assertEquals("a_b_.avif", names[7])
        assertTrue(names.all { SiteNames.isClean(it) })
    }

    @Test
    fun 같은_갤러리에서_겹치는_이름() {
        val files = listOf(HFile("a.jpg", hash(1)), HFile("a.png", hash(2)), HFile("A.jpg", hash(3)), HFile("a (2).jpg", hash(4)))
        val names = gallery(hitomiGallery(4, files = files), 4).files.map { it.name }
        assertEquals(listOf("a.avif", "a (2).avif", "A (3).avif", "a (2) (2).avif"), names)
        // NFC 와 NFD 로 같은 이름도 겹침이다.
        val nfd = gallery(hitomiGallery(4, files = listOf(HFile("e\u0301.jpg", hash(1)), HFile("\u00e9.jpg", hash(2)))), 4).files.map { it.name }
        assertEquals(listOf("\u00e9.avif", "\u00e9 (2).avif"), nfd)
    }

    @Test
    fun 같은_이름_이만_개도_곧바로() {
        // 다듬은 이름의 상한(200 바이트)에 꽉 찬 이름 — 꼬리를 붙이려면 앞을 줄여야 한다.
        val names = List(GalleryResolver.MAX_UNITS) { "x".repeat(195) + ".avif" }
        val started = System.nanoTime()
        val unique = SiteNames.unique(names)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(names.size, unique.toSet().size)
        assertTrue(unique.all { SiteNames.isClean(it) && it.endsWith(".avif") })
        assertTrue(unique.last().endsWith(" (20000).avif"), unique.last())
        assertTrue(millis < 5_000, "${millis}ms")
    }

    @Test
    fun 확장자_고르기() {
        fun ext(name: String?, vararg flags: Pair<String, Any?>) = Hitomi.chooseExt(mapOf(*flags), name)
        assertEquals("avif", ext("a.jpg", "hasavif" to 1L))
        assertEquals("avif", ext("a.jpg", "hasavif" to true))
        assertEquals("avif", ext("a.jpg", "hasavif" to "1"))
        assertEquals("webp", ext("a.jpg", "hasavif" to 0L))
        assertEquals("webp", ext("a.jpg"))
        assertEquals("webp", ext("a.jpg", "haswebp" to 1L, "hasjxl" to 1L))
        assertEquals("webp", ext("a.jpg", "hasjxl" to 1L))
        assertEquals("webp", ext(null))
        // 움직일 수 있는 gif 는 webp — 안드로이드 12 가 움직이는 WebP 를 튼다.
        assertEquals("webp", ext("a.gif", "hasavif" to 1L))
        assertEquals("webp", ext("A.GIF", "hasavif" to 1L))
    }

    @Test
    fun gg_실측의_꼴() {
        val parsed = gg(ggJs())
        assertEquals("1700000000/", parsed.b)
        assertEquals(0, parsed.m(2977))
        assertEquals(0, parsed.m(364))
        assertEquals(0, parsed.m(7))
        assertEquals(1, parsed.m(8))
        assertEquals(1, parsed.m(0))
        assertEquals(3, parsed.caseCount)
    }

    @Test
    fun gg_기본값과_묶음이_바뀌어도() {
        val parsed = gg(ggJs(fallback = 0, groups = listOf(1 to listOf(5, 6), 2 to listOf(7), 0 to listOf(8))))
        assertEquals(1, parsed.m(5))
        assertEquals(1, parsed.m(6))
        assertEquals(2, parsed.m(7))
        assertEquals(0, parsed.m(8))
        assertEquals(0, parsed.m(9))
        // 묶음이 하나도 없어도 된다(모두 기본값).
        assertEquals(1, gg(ggJs(fallback = 1, groups = emptyList())).m(5))
    }

    @Test
    fun gg_default_절과_같은_case() {
        val js = """
            gg = { m: function(g) {
            var o = 0;
            switch (g) {
            case 1:
            o = 1; break;
            case 1:
            case 2:
            o = 2; break;
            default:
            o = 3; break;
            }
            return o;
            },
            s: function(h) { var m = /(..)(.)$/.exec(h); return parseInt(m[2]+m[1], 16).toString(10); },
            b: '42/'
            };
        """.trimIndent()
        val parsed = gg(js)
        assertEquals("42/", parsed.b)
        // switch 는 처음 맞는 case — 1 은 앞의 묶음(1).
        assertEquals(1, parsed.m(1))
        assertEquals(2, parsed.m(2))
        assertEquals(3, parsed.m(3))
    }

    @Test
    fun gg_주석_공백_큰따옴표_다른_이름() {
        val js = "/* 머리 */ 'use strict'; // 줄 주석\n" +
            "window.gg={b:\"9/\",s:function(hash){let r=/(..)(.)$/.exec(hash);return parseInt(r[2]+r[1],16).toString(10);}," +
            "m:function(x){let v=1;switch(x){case 10:case 20:v=0;break;case 30:v=0}return v;}};"
        val parsed = gg(js)
        assertEquals("9/", parsed.b)
        assertEquals(0, parsed.m(10))
        assertEquals(0, parsed.m(20))
        assertEquals(0, parsed.m(30))
        assertEquals(1, parsed.m(40))
    }

    @Test
    fun gg_꼴이_다르면_UNEXPECTED() {
        val good = ggJs()
        val broken = mapOf(
            "b 없음" to good.replace("b: '1700000000/'", "c: '1700000000/'"),
            "b 의 꼴" to ggJs(b = "'abc/'"),
            "b 끝의 / 없음" to ggJs(b = "'1700000000'"),
            "b 의 경로 탈출" to ggJs(b = "'../1/'"),
            "s 가 바뀜" to good.replace("m[2]+m[1]", "m[1]+m[2]"),
            "s 의 진법" to good.replace(", 16)", ", 10)"),
            "s 없음" to good.replace("s: function(h)", "t: function(h)"),
            "m 없음" to good.replace("m: function(g)", "n: function(g)"),
            "m 이 둘" to good.replace("b: '1700000000/'", "b: '1700000000/', m: function(g) { var o = 1; switch (g) { } return o; }"),
            "if 문" to good.replace("switch (g) {", "if (g) { o = 0; }\nswitch (g) {"),
            "break 없는 묶음" to good.replace("o = 0; break;", "o = 0;\ncase 99:\no = 1; break;"),
            "다른 변수에 대입" to good.replace("o = 0; break;", "p = 0; break;"),
            "case 범위 밖" to ggJs(groups = listOf(0 to listOf(4096))),
            "큰 값" to ggJs(groups = listOf(100 to listOf(5))),
            "글 case" to good.replace("case 7:", "case '7':"),
            "닫히지 않은 주석" to "$good/*",
            "닫히지 않은 글" to "$good'",
            "b 의 이스케이프" to ggJs(b = "'17\\00/'"),
            "너무 큼" to good + "//" + "x".repeat(1 shl 20),
            "빈 글" to "",
        )
        for ((name, js) in broken) {
            assertEquals(SiteException.Reason.UNEXPECTED, reason { gg(js) }, name)
        }
        assertEquals(SiteException.Reason.BLOCKED, reason { gg("<!DOCTYPE html><html>") })
    }

    private class Vector(val hash: String, val ext: String, val g: Int, val url: String)

    /**
     * 대조값 — 우리 코드와 따로, 사이트의 공식(common.js 의 `subdomain_from_url`·`url_from_hash`, gg.js 의 `s`)을 파이썬으로 옮겨 합성 해시로
     * 셈한 것(2026-10-11, 노트의 hitomi_vectors.py — 고정 씨앗). gg 는 기본 1, {2977, 364, 7} → 0, {1000, 4095} → 2.
     */
    @Test
    fun 주소_대조값() {
        val parsed = gg(ggJs(fallback = 1, groups = listOf(0 to listOf(2977, 364, 7), 2 to listOf(1000, 4095))))
        val vectors = listOf(
            Vector("06cbdac0966dff3953d57a641a0c7939ec5d0035f9401604971939bd06dd8a1b", "avif", 2977, "https://a1.gold-usergeneratedcontent.net/1700000000/2977/06cbdac0966dff3953d57a641a0c7939ec5d0035f9401604971939bd06dd8a1b.avif"),
            Vector("06cbdac0966dff3953d57a641a0c7939ec5d0035f9401604971939bd06dd8a1b", "webp", 2977, "https://w1.gold-usergeneratedcontent.net/1700000000/2977/06cbdac0966dff3953d57a641a0c7939ec5d0035f9401604971939bd06dd8a1b.webp"),
            Vector("0ad890a7df1454b6ace37444665725d88d4c4b1478534082c537bfd0b589a6c1", "avif", 364, "https://a1.gold-usergeneratedcontent.net/1700000000/364/0ad890a7df1454b6ace37444665725d88d4c4b1478534082c537bfd0b589a6c1.avif"),
            Vector("0ad890a7df1454b6ace37444665725d88d4c4b1478534082c537bfd0b589a6c1", "webp", 364, "https://w1.gold-usergeneratedcontent.net/1700000000/364/0ad890a7df1454b6ace37444665725d88d4c4b1478534082c537bfd0b589a6c1.webp"),
            Vector("66398af9edd45bbe54233b92239ff1575420a2bb94e20a4e42c73fc74794d070", "avif", 7, "https://a1.gold-usergeneratedcontent.net/1700000000/7/66398af9edd45bbe54233b92239ff1575420a2bb94e20a4e42c73fc74794d070.avif"),
            Vector("66398af9edd45bbe54233b92239ff1575420a2bb94e20a4e42c73fc74794d070", "webp", 7, "https://w1.gold-usergeneratedcontent.net/1700000000/7/66398af9edd45bbe54233b92239ff1575420a2bb94e20a4e42c73fc74794d070.webp"),
            Vector("95061a7f0923abc8ffd825579579b7a438907b8750a0e2283301a417bf6cc8e3", "avif", 910, "https://a2.gold-usergeneratedcontent.net/1700000000/910/95061a7f0923abc8ffd825579579b7a438907b8750a0e2283301a417bf6cc8e3.avif"),
            Vector("95061a7f0923abc8ffd825579579b7a438907b8750a0e2283301a417bf6cc8e3", "webp", 910, "https://w2.gold-usergeneratedcontent.net/1700000000/910/95061a7f0923abc8ffd825579579b7a438907b8750a0e2283301a417bf6cc8e3.webp"),
            Vector("a63e4705e7a4db640ced3fcd36925f195a79e64376b60764ec2d04dc4ef1dfff", "avif", 4095, "https://a3.gold-usergeneratedcontent.net/1700000000/4095/a63e4705e7a4db640ced3fcd36925f195a79e64376b60764ec2d04dc4ef1dfff.avif"),
            Vector("a63e4705e7a4db640ced3fcd36925f195a79e64376b60764ec2d04dc4ef1dfff", "webp", 4095, "https://w3.gold-usergeneratedcontent.net/1700000000/4095/a63e4705e7a4db640ced3fcd36925f195a79e64376b60764ec2d04dc4ef1dfff.webp"),
            Vector("b812eb5b4d43f52bbd5f85c8b20a2f80f44db0add6c4b35b22be21a57b3a6000", "avif", 0, "https://a2.gold-usergeneratedcontent.net/1700000000/0/b812eb5b4d43f52bbd5f85c8b20a2f80f44db0add6c4b35b22be21a57b3a6000.avif"),
            Vector("b812eb5b4d43f52bbd5f85c8b20a2f80f44db0add6c4b35b22be21a57b3a6000", "webp", 0, "https://w2.gold-usergeneratedcontent.net/1700000000/0/b812eb5b4d43f52bbd5f85c8b20a2f80f44db0add6c4b35b22be21a57b3a6000.webp"),
            Vector("681f7a4a7c9c72eb7e1dff37a0f511d760c9a66fb6112a82cc0f07d71fc0b22d", "avif", 3362, "https://a2.gold-usergeneratedcontent.net/1700000000/3362/681f7a4a7c9c72eb7e1dff37a0f511d760c9a66fb6112a82cc0f07d71fc0b22d.avif"),
            Vector("681f7a4a7c9c72eb7e1dff37a0f511d760c9a66fb6112a82cc0f07d71fc0b22d", "webp", 3362, "https://w2.gold-usergeneratedcontent.net/1700000000/3362/681f7a4a7c9c72eb7e1dff37a0f511d760c9a66fb6112a82cc0f07d71fc0b22d.webp"),
            Vector("7431c9a5b3258c8bca45d0d07b13d922c7b766b0787c39561af9353bff854171", "avif", 279, "https://a2.gold-usergeneratedcontent.net/1700000000/279/7431c9a5b3258c8bca45d0d07b13d922c7b766b0787c39561af9353bff854171.avif"),
            Vector("7431c9a5b3258c8bca45d0d07b13d922c7b766b0787c39561af9353bff854171", "webp", 279, "https://w2.gold-usergeneratedcontent.net/1700000000/279/7431c9a5b3258c8bca45d0d07b13d922c7b766b0787c39561af9353bff854171.webp"),
            Vector("b67ec9d38a75d89c60e8b4527c74fa50030948954b18905a8b04899bd87db2cf", "avif", 3884, "https://a2.gold-usergeneratedcontent.net/1700000000/3884/b67ec9d38a75d89c60e8b4527c74fa50030948954b18905a8b04899bd87db2cf.avif"),
            Vector("b67ec9d38a75d89c60e8b4527c74fa50030948954b18905a8b04899bd87db2cf", "webp", 3884, "https://w2.gold-usergeneratedcontent.net/1700000000/3884/b67ec9d38a75d89c60e8b4527c74fa50030948954b18905a8b04899bd87db2cf.webp"),
            Vector("963fa28b17871cd4e35f227697630f9d8cb59aa4b4042b2599e1b9e5084ff4b2", "avif", 587, "https://a2.gold-usergeneratedcontent.net/1700000000/587/963fa28b17871cd4e35f227697630f9d8cb59aa4b4042b2599e1b9e5084ff4b2.avif"),
            Vector("963fa28b17871cd4e35f227697630f9d8cb59aa4b4042b2599e1b9e5084ff4b2", "webp", 587, "https://w2.gold-usergeneratedcontent.net/1700000000/587/963fa28b17871cd4e35f227697630f9d8cb59aa4b4042b2599e1b9e5084ff4b2.webp"),
        )
        for (v in vectors) {
            assertEquals(v.g, Hitomi.g(v.hash), v.hash)
            assertEquals(v.url, Hitomi.imageUrl(parsed, v.hash, v.ext), v.hash)
        }
    }

    @Test
    fun g_는_끝_1자_더하기_그_앞_2자() {
        // 실측 갤러리의 첫 그림은 g=364 — 같은 끝(…6c1)의 합성 해시로.
        assertEquals(364, Hitomi.g(hash(1, "6c1")))
        assertEquals(0xba1, Hitomi.g(hash(1, "a1b")))
        assertEquals(0, Hitomi.g(hash(1, "000")))
        assertEquals(4095, Hitomi.g(hash(1, "fff")))
        assertFailsWith<IllegalArgumentException> { Hitomi.imageUrl(gg(ggJs()), hash(1), "jxl") }
        assertFailsWith<IllegalArgumentException> { Hitomi.imageUrl(gg(ggJs()), "abc", "avif") }
    }
}
