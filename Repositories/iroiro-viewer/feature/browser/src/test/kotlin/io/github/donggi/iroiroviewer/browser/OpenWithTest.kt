package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 파일을 눌렀을 때 가는 곳(`TapRoute`)과 다른 앱으로 열기의 규칙(`OpenWithRules`).
 *
 * 가는 곳의 표는 **모든 종류를 하나씩** 적는다. 종류가 늘면 표에 없는 값이 생겨 첫 시험이 깨진다 — 컴파일러가
 * `when` 의 빠진 가지를 잡는 것과 별개로, **어느 길이 옳은지**는 시험만 말할 수 있다(함정 표 '종류를 옮기면…').
 */
class OpenWithTest {

    private val folder = "/storage/emulated/0/Download"
    private val trash = "/storage/emulated/0/${TrashStore.DIR_NAME}"

    private fun entry(
        name: String,
        kind: FileKind = MimeResolver.kindOf(name, isDirectory = false),
        dir: String = folder,
        isDirectory: Boolean = false,
    ) = FileEntry(
        name = name,
        path = "$dir/$name",
        isDirectory = isDirectory,
        size = if (isDirectory) 0 else 1,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = kind,
    )

    /** 폴더가 아닌 파일의 길. 뷰어가 있는 종류는 제 뷰어로, 없는 셋(APK·글꼴·기타)만 다른 앱으로. */
    private val expected = mapOf(
        FileKind.FOLDER to TapRoute.DETAIL,
        FileKind.IMAGE to TapRoute.IMAGE,
        FileKind.AUDIO to TapRoute.PLAYER,
        FileKind.VIDEO to TapRoute.PLAYER,
        FileKind.TEXT to TapRoute.TEXT,
        FileKind.CODE to TapRoute.TEXT,
        FileKind.PDF to TapRoute.DOCUMENT,
        FileKind.EBOOK to TapRoute.DOCUMENT,
        FileKind.ARCHIVE to TapRoute.ARCHIVE,
        FileKind.COMIC to TapRoute.COMIC,
        FileKind.DOCUMENT to TapRoute.DOCUMENT,
        FileKind.SHEET to TapRoute.DOCUMENT,
        FileKind.SLIDE to TapRoute.DOCUMENT,
        FileKind.HWP to TapRoute.DOCUMENT,
        FileKind.APK to TapRoute.EXTERNAL,
        FileKind.FONT to TapRoute.EXTERNAL,
        FileKind.OTHER to TapRoute.EXTERNAL,
    )

    // ---- 가는 곳 -----------------------------------------------------------------

    @Test
    fun 모든_종류의_파일이_정해진_길로_간다() {
        // 표가 종류 전부를 덮는지부터 — 새 종류가 생기면 여기서 깨져, 그 종류의 길을 정하게 만든다.
        assertEquals(FileKind.entries.toSet(), expected.keys, "표에 없는 종류가 있다")
        for (kind in FileKind.entries) {
            assertEquals(expected[kind], TapRoute.of(isDirectory = false, kind = kind, inTrash = false), "$kind")
        }
    }

    @Test
    fun 다른_앱으로_가는_것은_뷰어가_없는_셋뿐이다() {
        val external = FileKind.entries.filter { TapRoute.of(false, it, inTrash = false) == TapRoute.EXTERNAL }
        assertEquals(setOf(FileKind.APK, FileKind.FONT, FileKind.OTHER), external.toSet())
    }

    @Test
    fun 폴더는_종류와_상관없이_들어간다() {
        for (kind in FileKind.entries) {
            assertEquals(TapRoute.FOLDER, TapRoute.of(isDirectory = true, kind = kind, inTrash = false), "$kind")
            assertEquals(TapRoute.FOLDER, TapRoute.of(isDirectory = true, kind = kind, inTrash = true), "$kind")
        }
    }

    @Test
    fun 휴지통_안의_것은_다른_앱으로_보내지_않는다() {
        for (kind in FileKind.entries) {
            val route = TapRoute.of(isDirectory = false, kind = kind, inTrash = true)
            assertNotEquals(TapRoute.EXTERNAL, route, "$kind")
            // 뷰어로 여는 것은 밖으로 나가지 않으므로 그대로다 — 막는 것은 다른 앱에 넘기는 길 하나다.
            val outside = expected.getValue(kind)
            assertEquals(if (outside == TapRoute.EXTERNAL) TapRoute.DETAIL else outside, route, "$kind")
        }
    }

    @Test
    fun 항목으로_부르면_경로에서_휴지통을_알아본다() {
        assertEquals(TapRoute.EXTERNAL, TapRoute.of(entry("앱.apk")))
        assertEquals(TapRoute.DETAIL, TapRoute.of(entry("0f3c.apk", dir = trash)))
        // 휴지통 폴더 이름을 이름 앞머리에 가진 평범한 폴더는 휴지통이 아니다.
        assertEquals(TapRoute.EXTERNAL, TapRoute.of(entry("앱.apk", dir = "$folder/${TrashStore.DIR_NAME}-old")))
    }

    @Test
    fun 확장자로_정한_종류가_그대로_길이_된다() {
        // 목록이 매기는 종류(`MimeResolver.kindOf`)에서 끝까지 — 사용자가 누르는 것은 이름이다.
        val cases = mapOf(
            "설치.apk" to TapRoute.EXTERNAL,
            "글꼴.ttf" to TapRoute.EXTERNAL,
            "글꼴.otf" to TapRoute.EXTERNAL,
            "자료.bin" to TapRoute.EXTERNAL,
            "이름없음" to TapRoute.EXTERNAL,
            "사진.jpg" to TapRoute.IMAGE,
            "노래.mp3" to TapRoute.PLAYER,
            "영상.mkv" to TapRoute.PLAYER,
            "메모.txt" to TapRoute.TEXT,
            "코드.kt" to TapRoute.TEXT,
            "책.pdf" to TapRoute.DOCUMENT,
            "책.epub" to TapRoute.DOCUMENT,
            "보고.docx" to TapRoute.DOCUMENT,
            "표.xlsx" to TapRoute.DOCUMENT,
            "발표.pptx" to TapRoute.DOCUMENT,
            "공문.hwp" to TapRoute.DOCUMENT,
            "만화.cbz" to TapRoute.COMIC,
            "묶음.zip" to TapRoute.ARCHIVE,
        )
        for ((name, route) in cases) assertEquals(route, TapRoute.of(entry(name)), name)
    }

    // ---- 넘겨도 되는가 ---------------------------------------------------------------

    @Test
    fun 넘길_수_있는_것은_휴지통_밖의_파일뿐이다() {
        assertTrue(OpenWithRules.canHandOff(entry("앱.apk")))
        assertTrue(OpenWithRules.canHandOff(entry("사진.jpg")))
        assertFalse(OpenWithRules.canHandOff(entry("폴더", kind = FileKind.FOLDER, isDirectory = true)))
        assertFalse(OpenWithRules.canHandOff(entry("이상한것", kind = FileKind.FOLDER)))
        assertFalse(OpenWithRules.canHandOff(entry("0f3c.apk", dir = trash)))
        assertFalse(OpenWithRules.canHandOff(entry("0f3c", dir = "$trash/안쪽")))
    }

    @Test
    fun 선택_메뉴는_파일_하나를_골랐을_때만_겨눈다() {
        val apk = entry("앱.apk")
        val pdf = entry("책.pdf")
        val dir = entry("폴더", kind = FileKind.FOLDER, isDirectory = true)
        val shown = listOf(dir, apk, pdf)

        assertNull(OpenWithRules.singleFile(emptySet(), shown))
        assertSame(apk, OpenWithRules.singleFile(setOf(apk.path), shown))
        assertSame(pdf, OpenWithRules.singleFile(setOf(pdf.path), shown))
        // 둘을 고르면 어느 것을 넘길지 모른다 — '이름 바꾸기' 처럼 꺼진다.
        assertNull(OpenWithRules.singleFile(setOf(apk.path, pdf.path), shown))
        // 폴더는 다른 앱에 넘기지 않는다.
        assertNull(OpenWithRules.singleFile(setOf(dir.path), shown))
        // 이름 필터에 가려 지금 보이지 않는 것은 넘기지 않는다.
        assertNull(OpenWithRules.singleFile(setOf("$folder/가려진.apk"), shown))
    }

    @Test
    fun 선택_메뉴도_휴지통_안의_것은_겨누지_않는다() {
        val trashed = entry("0f3c.apk", dir = trash)
        assertNull(OpenWithRules.singleFile(setOf(trashed.path), listOf(trashed)))
    }

    // ---- 넘긴 뒤 -----------------------------------------------------------------

    @Test
    fun 눌러서_띄웠으면_정보_시트가_뜨지_않는다() {
        assertNull(OpenWithRules.afterTap(entry("앱.apk"), ExternalOpen.Result.STARTED))
    }

    @Test
    fun 눌러서_못_띄웠으면_까닭과_함께_정보_시트가_뜬다() {
        val apk = entry("앱.apk")
        for (result in ExternalOpen.Result.entries - ExternalOpen.Result.STARTED) {
            val detail = assertNotNull(OpenWithRules.afterTap(apk, result), "$result")
            assertSame(apk, detail.entry)
            // 문장은 `core:io` 한 곳의 것이다 — 결과마다 그 문장이 시트에 적힌다.
            assertEquals(ExternalOpen.messageOf(result), detail.notice, "$result")
            assertNotNull(detail.notice, "$result")
        }
    }

    @Test
    fun 눌러서_다른_앱으로_가는_파일은_시트에서_모든_앱으로_다시_고를_수_있다() {
        // '이 형식을 아는 앱이 없습니다'(NO_APP) 로 뜬 시트의 '다른 앱으로 열기' 가 모든 앱에서 고르는 창으로 가는 길이다.
        // 다른 앱으로 가는 종류인데 시트에 그 단추가 없으면 선언한 앱이 없는 파일(글꼴·.xyz)을 열어 볼 길이 사라진다.
        val names = listOf("글꼴.ttf", "글꼴.otf", "설치.apk", "자료.xyz", "자료.bin", "이름없음")
        for (name in names) {
            val e = entry(name)
            assertEquals(TapRoute.EXTERNAL, TapRoute.of(e), name)
            val detail = assertNotNull(OpenWithRules.afterTap(e, ExternalOpen.Result.NO_APP), name)
            assertTrue(OpenWithRules.canHandOff(detail.entry), name)
        }
        for (kind in FileKind.entries.filter { TapRoute.of(false, it, inTrash = false) == TapRoute.EXTERNAL }) {
            assertTrue(OpenWithRules.canHandOff(entry("x", kind = kind)), "$kind")
        }
    }

    @Test
    fun 시트에서_띄웠으면_시트를_닫고_못_띄웠으면_까닭을_바꿔_적는다() {
        val shown = DetailRequest(entry("글꼴.ttf"), notice = ExternalOpen.messageOf(ExternalOpen.Result.NO_APP))
        assertNull(OpenWithRules.afterChoose(shown, ExternalOpen.Result.STARTED))

        val failed = assertNotNull(OpenWithRules.afterChoose(shown, ExternalOpen.Result.FAILED))
        assertSame(shown.entry, failed.entry)
        assertEquals(ExternalOpen.messageOf(ExternalOpen.Result.FAILED), failed.notice)
        assertNotEquals(shown.notice, failed.notice)

        // 선택 메뉴의 '정보' 로 연 시트(까닭 없음)에서 실패하면 그때 까닭이 생긴다.
        val plain = DetailRequest(entry("글꼴.ttf"))
        assertEquals(
            ExternalOpen.messageOf(ExternalOpen.Result.NO_APP),
            OpenWithRules.afterChoose(plain, ExternalOpen.Result.NO_APP)?.notice,
        )
    }

    // ---- 띄운 직후의 누름 -------------------------------------------------------------

    @Test
    fun 띄운_적이_없으면_누름을_흘려보내지_않는다() {
        assertFalse(OpenWithRules.settling(startedAt = null, now = 0))
        assertFalse(OpenWithRules.settling(startedAt = null, now = 123_456))
    }

    @Test
    fun 띄운_직후의_두_번째_누름은_흘려보낸다() {
        val at = 10_000L
        // 같은 순간과, 사람이 두 번 누르는 간격(수십~수백 ms)은 흘려보낸다 — 두 겹 설치 관리자·아래 줄 열기를 막는 자리다.
        assertTrue(OpenWithRules.settling(at, at))
        assertTrue(OpenWithRules.settling(at, at + 40))
        assertTrue(OpenWithRules.settling(at, at + 300))
        assertTrue(OpenWithRules.settling(at, at + OpenWithRules.SETTLE_MS - 1))
    }

    @Test
    fun 시간이_지나면_다시_누를_수_있다() {
        val at = 10_000L
        // 받는 앱에서 돌아와 누르는 것은 막지 않는다 — 막는 창은 짧아야 한다.
        assertFalse(OpenWithRules.settling(at, at + OpenWithRules.SETTLE_MS))
        assertFalse(OpenWithRules.settling(at, at + 5_000))
        assertTrue(OpenWithRules.SETTLE_MS in 300..1_500, "흘려보내는 창이 두 번 누름보다 짧거나 사람이 느낄 만큼 길다")
        // 시계가 거꾸로 간 값으로는 누름을 삼키지 않는다 — 삼키는 쪽으로 틀리면 앱이 멈춘 것처럼 보인다.
        assertFalse(OpenWithRules.settling(at, at - 1))
    }
}
