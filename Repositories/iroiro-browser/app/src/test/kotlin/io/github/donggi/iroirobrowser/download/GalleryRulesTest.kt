package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.sites.GalleryFile
import io.github.donggi.iroirobrowser.core.sites.GalleryResolver
import io.github.donggi.iroirobrowser.core.sites.GalleryUnit
import io.github.donggi.iroirobrowser.core.sites.Site
import io.github.donggi.iroirobrowser.core.sites.SiteException
import io.github.donggi.iroirobrowser.core.sites.SiteHttpException
import io.github.donggi.iroirobrowser.core.sites.SiteTarget
import io.github.donggi.iroirobrowser.download.DownloadCenter.Status
import io.github.donggi.iroirobrowser.download.GalleryRules.Action
import io.github.donggi.iroirobrowser.download.GalleryRules.Again
import io.github.donggi.iroirobrowser.download.GalleryRules.Existing
import io.github.donggi.iroirobrowser.download.GalleryRules.FileStep
import io.github.donggi.iroirobrowser.download.GalleryRules.Position
import io.github.donggi.iroirobrowser.download.GalleryRules.UnitKind
import io.github.donggi.iroirobrowser.download.GalleryRules.UnitStep
import io.github.donggi.iroirobrowser.proxy.NetErrors
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 갤러리 받기(pixiv·hitomi)의 판단 — 자리·진행 글·이어받을 자리·건너뛰기와 멈춤·까닭·같은 것을 다시 받기·목록의 할 일. 고르는 일만 본다(자원 번호와
 * 인자). 시험 자료는 합성이다 — 실제 작품·작가·주소를 넣지 않는다(공개 저장소).
 */
class GalleryRulesTest {

    // ── 자리 ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 폴더는_내려받기_아래로_성분마다_한_번_더_거른다() {
        assertEquals("Download/Pixiv/작가/", GalleryRules.relativePath(listOf("Pixiv", "작가")))
        // 남이 지은 이름 — 구분자·상위 폴더·방향 제어 문자가 공용 폴더 밖이나 엉뚱한 깊이를 가리키지 않게.
        assertEquals("Download/Hitomi/a_b/download/x_y/", GalleryRules.relativePath(listOf("Hitomi", "a/b", "..", "x\u202Ey")))
        assertEquals("Download/Hitomi/그룹_ 이름_/12_제목 _/", GalleryRules.relativePath(listOf("Hitomi", "그룹: 이름?", "12_제목 *")))
        // 빈 성분은 뺀다. 성분이 없으면 '내려받기' 바로 아래.
        assertEquals("Download/Pixiv/", GalleryRules.relativePath(listOf("Pixiv", " ", "")))
        assertEquals("Download/", GalleryRules.relativePath(emptyList()))
        // 같은 것을 두 번 걸러도 같다(계획이 이미 거른 성분).
        val once = GalleryRules.relativePath(listOf("Hitomi", "a/b"))
        assertEquals(once, GalleryRules.relativePath(listOf("Hitomi", "a_b")))
    }

    @Test
    fun 폴더_깊이에는_상한이_있다() {
        val path = GalleryRules.relativePath((1..10).map { "d$it" })
        assertEquals("Download/d1/d2/d3/d4/", path)
        assertEquals(GalleryRules.MAX_DEPTH, path.trimEnd('/').split('/').size - 1)
    }

    // ── 진행의 글 ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 진행은_파일_수를_알면_파일로_작가_받기면_작품으로() {
        val files = GalleryRules.progress(units = 40, unitsDone = 12, fileCount = 40, filesSaved = 11)
        assertEquals(R.string.gallery_progress_files, files.res)
        assertEquals(listOf<Any>(11, 40), files.args)
        val works = GalleryRules.progress(units = 120, unitsDone = 3, fileCount = null, filesSaved = 15)
        assertEquals(R.string.gallery_progress_works, works.res)
        assertEquals(listOf<Any>(3, 120, 15), works.args)
        // 알림의 막대는 단위로 — 빈 계획에서도 0 으로 나누지 않고, 넘친 값은 끝에서 멈춘다.
        assertEquals(40 to 12, GalleryRules.bar(40, 12))
        assertEquals(1 to 0, GalleryRules.bar(0, 0))
        assertEquals(5 to 5, GalleryRules.bar(5, 9))
    }

    @Test
    fun 다_받은_뒤의_덧붙임은_건너뛴_것이_있을_때만() {
        assertNull(GalleryRules.note(0, 0))
        assertEquals(R.string.gallery_skipped, GalleryRules.note(2, 0)?.res)
        val login = GalleryRules.note(3, 2)
        assertEquals(R.string.gallery_skipped_login, login?.res)
        assertEquals(listOf<Any>(3, 2), login?.args)
    }

    @Test
    fun 메뉴의_한_줄은_대상마다_다르다() {
        assertEquals(R.string.gallery_menu_artwork, GalleryRules.menuRes(SiteTarget.PixivArtwork(1L)))
        assertEquals(R.string.gallery_menu_user, GalleryRules.menuRes(SiteTarget.PixivUser(2L)))
        assertEquals(R.string.gallery_menu_hitomi, GalleryRules.menuRes(SiteTarget.HitomiGallery(3L)))
    }

    // ── 이어받을 자리 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 이어받을_자리는_기록이_앞서_있으면_그_다음부터() {
        val stored = Position(3, 2)
        // 기록이 없거나 뒤처졌으면 목록의 자리.
        assertEquals(stored, GalleryRules.resumeAt(stored, null))
        assertEquals(stored, GalleryRules.resumeAt(stored, Position(3, 1)))
        assertEquals(stored, GalleryRules.resumeAt(stored, Position(2, 9)))
        // 목록이 늦게 적혀(1.5초 모아 적는다 — 그 사이 앱이 죽었다) 기록이 앞서 있다 — 마지막으로 드러낸 파일의 다음(같은 파일을 두 번 받지 않게).
        assertEquals(Position(3, 5), GalleryRules.resumeAt(stored, Position(3, 4)))
        assertEquals(Position(4, 1), GalleryRules.resumeAt(stored, Position(4, 0)))
        // 통째로 건너뛴 작품(-1)이 마지막이면 다음 단위의 처음.
        assertEquals(Position(6, 0), GalleryRules.resumeAt(stored, Position(5, -1)))
        assertEquals(Position(4, 0), GalleryRules.resumeAt(Position(4, 0), Position(3, -1)))
        // 통째로 끝낸 자리는 그 단위의 어느 파일보다 뒤다.
        assertTrue(Position(3, -1).after(Position(3, 99)))
        assertFalse(Position(3, 99).after(Position(3, -1)))
        assertFalse(Position(3, 2).after(Position(3, 2)))
    }

    // ── 실패 — 건너뛸까, 멈출까 ──────────────────────────────────────────────────────────────────────────

    @Test
    fun 단위의_종류() {
        val file = GalleryFile("https://img.example/1.png", "1.png", "https://site.example/")
        assertEquals(UnitKind.FILE, GalleryRules.kindOf(GalleryUnit.Direct(file)))
        assertEquals(UnitKind.HITOMI_IMAGE, GalleryRules.kindOf(GalleryUnit.HitomiImage(1L, "ab".repeat(32), "webp", "001.webp")))
        assertEquals(UnitKind.PIXIV_WORK, GalleryRules.kindOf(GalleryUnit.PixivWork(7L)))
    }

    @Test
    fun 작가_받기의_지워진_작품과_로그인_작품은_건너뛰고_센다() {
        fun expand(e: IOException, stopped: Boolean = false) = GalleryRules.onExpandFailure(e, UnitKind.PIXIV_WORK, stopped, Site.PIXIV)
        assertEquals(UnitStep.Skip(login = false), expand(SiteException(SiteException.Reason.NOT_FOUND)))
        assertEquals(UnitStep.Skip(login = true), expand(SiteException(SiteException.Reason.LOGIN_REQUIRED)))
        assertEquals(UnitStep.Skip(login = false), expand(SiteException(SiteException.Reason.EMPTY)))
        assertEquals(UnitStep.Skip(login = false), expand(SiteHttpException(404, "HTTP 404")))
        assertEquals(UnitStep.Skip(login = false), expand(SiteHttpException(410, "HTTP 410")))
        // 다음 작품도 같을 것 — 멈춘다(막힘·꼴이 다름·거절).
        assertIs<UnitStep.Pause>(expand(SiteException(SiteException.Reason.BLOCKED)))
        assertIs<UnitStep.Pause>(expand(SiteException(SiteException.Reason.UNEXPECTED)))
        assertIs<UnitStep.Pause>(expand(SiteHttpException(403, "HTTP 403")))
        // 프록시가 만든 404 는 사이트의 '없음' 이 아니다.
        assertIs<UnitStep.Pause>(expand(SiteHttpException(404, "HTTP 404", byProxy = true)))
        // 망이 세 번 실패했다.
        assertIs<UnitStep.Pause>(expand(ConnectException("refused")))
        // 사용자가 멈췄다 — 그때의 예외(시간 초과처럼 보이는 것도)는 그 탓이다.
        assertEquals(UnitStep.Stop, expand(InterruptedIOException("cancelled"), stopped = true))
        assertEquals(UnitStep.Stop, expand(SiteException(SiteException.Reason.NOT_FOUND), stopped = true))
    }

    @Test
    fun 작품이_아닌_단위는_없어도_건너뛰지_않고_멈춘다() {
        // hitomi 그림의 주소 셈(gg.js)이 없거나 꼴이 다르면 다음 그림도 같다.
        for (kind in listOf(UnitKind.HITOMI_IMAGE, UnitKind.FILE)) {
            assertIs<UnitStep.Pause>(GalleryRules.onExpandFailure(SiteException(SiteException.Reason.NOT_FOUND), kind, false, Site.HITOMI))
            assertIs<UnitStep.Pause>(GalleryRules.onExpandFailure(SiteHttpException(404, "HTTP 404"), kind, false, Site.HITOMI))
        }
    }

    @Test
    fun hitomi_그림이_없으면_한_번_다시_셈하고_그래도_없으면_건너뛴다() {
        val gone = SiteHttpException(404, "HTTP 404")
        assertEquals(FileStep.ReResolve, GalleryRules.onFileFailure(gone, UnitKind.HITOMI_IMAGE, reResolved = false, stopped = false, site = Site.HITOMI))
        assertEquals(FileStep.Skip, GalleryRules.onFileFailure(gone, UnitKind.HITOMI_IMAGE, reResolved = true, stopped = false, site = Site.HITOMI))
        assertEquals(FileStep.Skip, GalleryRules.onFileFailure(SiteHttpException(410, "HTTP 410"), UnitKind.HITOMI_IMAGE, true, false, Site.HITOMI))
        // pixiv 의 쪽이 없으면 곧바로 건너뛴다(다시 셈할 것이 없다).
        assertEquals(FileStep.Skip, GalleryRules.onFileFailure(gone, UnitKind.FILE, false, false, Site.PIXIV))
        assertEquals(FileStep.Skip, GalleryRules.onFileFailure(gone, UnitKind.PIXIV_WORK, false, false, Site.PIXIV))
    }

    @Test
    fun 파일의_거절_망_프록시는_멈추고_까닭을_말한다() {
        fun file(e: IOException) = GalleryRules.onFileFailure(e, UnitKind.HITOMI_IMAGE, reResolved = false, stopped = false, site = Site.HITOMI)
        val forbidden = file(SiteHttpException(403, "HTTP 403"))
        assertIs<FileStep.Pause>(forbidden)
        assertEquals(R.string.gallery_err_http, forbidden.reason.res)
        assertEquals(listOf<Any>(403), forbidden.reason.args)
        // 프록시가 만든 404 — 사이트의 '없음' 이 아니다(다시 셈하지도 건너뛰지도 않는다).
        val proxy = file(SiteHttpException(404, "HTTP 404", byProxy = true))
        assertIs<FileStep.Pause>(proxy)
        assertEquals(R.string.gallery_err_proxy, proxy.reason.res)
        val net = file(ConnectException("refused"))
        assertIs<FileStep.Pause>(net)
        assertEquals(R.string.gallery_err_network, net.reason.res)
        assertEquals(listOf<Any>(NetErrors.describe(ConnectException("refused"))), net.reason.args)
        val stopped = GalleryRules.onFileFailure(SiteHttpException(404, "HTTP 404"), UnitKind.HITOMI_IMAGE, false, stopped = true, site = Site.HITOMI)
        assertEquals(FileStep.Stop, stopped)
    }

    // ── 까닭의 글 ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 까닭은_사이트의_까닭마다_다르고_로그인은_할_일까지() {
        val reasons = SiteException.Reason.entries.map { GalleryRules.reason(SiteException(it), Site.PIXIV).res }
        assertEquals(reasons.size, reasons.toSet().size)
        assertEquals(R.string.gallery_err_login_pixiv, GalleryRules.reason(SiteException(SiteException.Reason.LOGIN_REQUIRED), Site.PIXIV).res)
        assertEquals(R.string.gallery_err_login, GalleryRules.reason(SiteException(SiteException.Reason.LOGIN_REQUIRED), Site.HITOMI).res)
        assertEquals(R.string.gallery_err_login, GalleryRules.reason(SiteException(SiteException.Reason.LOGIN_REQUIRED), null).res)
        assertEquals(listOf<Any>(GalleryResolver.MAX_UNITS), GalleryRules.reason(SiteException(SiteException.Reason.TOO_LARGE), Site.PIXIV).args)
        // 사이트가 준 기계 말(detail)은 글에 싣지 않는다 — 우리 문장만.
        assertTrue(GalleryRules.reason(SiteException(SiteException.Reason.UNEXPECTED, "token=abc"), Site.PIXIV).args.isEmpty())
    }

    @Test
    fun HTTP_답과_망과_모르는_것의_까닭() {
        fun http(code: Int, proxy: Boolean = false) = GalleryRules.reason(SiteHttpException(code, "HTTP $code", proxy), Site.PIXIV)
        assertEquals(R.string.gallery_err_not_found, http(404).res)
        assertEquals(R.string.gallery_err_not_found, http(410).res)
        assertEquals(R.string.gallery_err_busy, http(429).res)
        assertEquals(R.string.gallery_err_server, http(503).res)
        assertEquals(listOf<Any>(503), http(503).args)
        assertEquals(R.string.gallery_err_http, http(403).res)
        assertEquals(R.string.gallery_err_proxy, http(403, proxy = true).res)
        assertEquals(R.string.gallery_err_proxy, http(502, proxy = true).res)
        // 망은 NetErrors 의 우리 말로(플랫폼의 영어 글을 보이지 않는다), 앱 안의 결함은 '알 수 없는 까닭'.
        val net = GalleryRules.reason(IOException("Connection reset"), Site.PIXIV)
        assertEquals(R.string.gallery_err_network, net.res)
        assertEquals(listOf<Any>(NetErrors.describe(IOException("Connection reset"))), net.args)
        assertEquals(R.string.gallery_err_other, GalleryRules.reason(IllegalStateException("x"), Site.PIXIV).res)
    }

    // ── 같은 것을 다시 ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 같은_것이_받는_중이거나_멈췄으면_목록을_받은_적이_있으면_묻는다() {
        assertEquals(Again.Fresh, GalleryRules.again(emptyList()))
        assertEquals(Again.Listed(2), GalleryRules.again(listOf(Existing(1, Status.DONE, 5), Existing(2, Status.RUNNING, 1))))
        assertEquals(Again.Listed(3), GalleryRules.again(listOf(Existing(3, Status.PAUSED, 0))))
        assertEquals(Again.Listed(4), GalleryRules.again(listOf(Existing(4, Status.QUEUED, 0))))
        // 받는 중인 것이 멈춘 것보다 먼저(그 줄을 보여 준다).
        assertEquals(Again.Listed(6), GalleryRules.again(listOf(Existing(5, Status.PAUSED, 0), Existing(6, Status.RUNNING, 0))))
        assertEquals(Again.Ask, GalleryRules.again(listOf(Existing(7, Status.DONE, 0))))
        // 그만뒀어도 받은 파일이 있으면 묻는다(다시 받으면 ' (1)' 사본이 생긴다). 받은 것 없이 끝났으면 묻지 않는다.
        assertEquals(Again.Ask, GalleryRules.again(listOf(Existing(8, Status.CANCELLED, 3))))
        assertEquals(Again.Fresh, GalleryRules.again(listOf(Existing(9, Status.CANCELLED, 0), Existing(10, Status.FAILED, 0))))
    }

    // ── 목록의 할 일 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 목록의_할_일은_상태마다() {
        fun actions(status: Status, saved: Int = 3, first: Boolean = true, page: Boolean = true) = GalleryRules.actions(status, saved, first, page)
        assertEquals(listOf(Action.PAUSE, Action.STOP, Action.OPEN_PAGE), actions(Status.RUNNING))
        assertEquals(listOf(Action.PAUSE, Action.STOP, Action.OPEN_PAGE), actions(Status.QUEUED))
        assertEquals(listOf(Action.RESUME, Action.OPEN_PAGE, Action.REMOVE, Action.DELETE_FILES), actions(Status.PAUSED))
        assertEquals(listOf(Action.OPEN_FIRST, Action.OPEN_PAGE, Action.REMOVE, Action.DELETE_FILES), actions(Status.DONE))
        assertEquals(listOf(Action.OPEN_PAGE, Action.REMOVE, Action.DELETE_FILES), actions(Status.CANCELLED))
        assertEquals(listOf(Action.OPEN_PAGE, Action.REMOVE), actions(Status.FAILED, saved = 0))
        // 받은 것이 없으면 첫 파일 열기·받은 파일까지 지우기가 없다. 페이지를 모르면 열기도 없다.
        assertEquals(listOf(Action.REMOVE), actions(Status.DONE, saved = 0, first = false, page = false))
        // 받는 중에는 목록에서 빼거나 파일을 지우지 않는다(먼저 멈추거나 그만둔다).
        assertFalse(Action.REMOVE in actions(Status.RUNNING) || Action.DELETE_FILES in actions(Status.RUNNING))
    }
}
