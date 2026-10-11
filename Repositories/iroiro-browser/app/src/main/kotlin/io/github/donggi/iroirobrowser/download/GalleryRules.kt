package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.SafeFileName
import io.github.donggi.iroirobrowser.core.sites.GalleryResolver
import io.github.donggi.iroirobrowser.core.sites.GalleryUnit
import io.github.donggi.iroirobrowser.core.sites.Site
import io.github.donggi.iroirobrowser.core.sites.SiteException
import io.github.donggi.iroirobrowser.core.sites.SiteHttpException
import io.github.donggi.iroirobrowser.core.sites.SiteTarget
import io.github.donggi.iroirobrowser.download.TorrentTexts.Part
import io.github.donggi.iroirobrowser.proxy.NetErrors
import java.io.IOException

/**
 * 갤러리 받기(pixiv 작품·작가, hitomi.la 갤러리 — [DownloadCenter.Kind.SET])의 **판단** — 화면·MediaStore·망에 묶이지 않은 것만 모았다(기기 없이
 * 시험한다 — `GalleryRulesTest`). 사용자 결정(2026-10-11): 'pixiv, hitomi 기능을 브라우저에 통합' — 데스크톱 다운로더(iroiro-downloader)에서 옮겼다.
 * 사이트를 알아보고 답을 읽는 것은 `:core` 의 `sites`(다른 갈래가 짓는다), 받기는 [DownloadCenter], 묻기는 [GalleryPrompt] 다.
 *
 * 공용 '내려받기' 아래의 자리, 목록·알림의 진행 글, 이어받을 자리, 실패를 건너뛸지 멈출지, 까닭의 글, 같은 것을 다시 받으려 할 때의 판단. 글은
 * 자원 번호와 인자([Part] — 토렌트의 글이 쓰는 그 꼴)로만 고른다 — 글로 짓는 것은 부르는 쪽([TorrentTexts.text])이다.
 */
internal object GalleryRules {

    /** 폴더 성분의 깊이 상한 — `Hitomi/<작가>/<id_제목>` 이 셋이다. 남이 지은 계획이 공용 폴더 안을 끝없이 파 내려가지 않게. */
    const val MAX_DEPTH = 4

    // ── 자리 ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 공용 '내려받기' 아래 [folder] 의 MediaStore `RELATIVE_PATH`(끝에 `/`). 성분은 계획이 이미 정리했지만 **한 번 더 거른다**
     * ([SafeFileName.clean] — 구분자·`..`·제어·방향 제어 문자) — 작가 이름·갤러리 제목은 남이 지은 글이라 공용 폴더 밖이나 엉뚱한 깊이를
     * 가리키지 않게(같은 것을 두 번 걸러도 같다). 빈 성분은 뺀다.
     */
    fun relativePath(folder: List<String>): String {
        val parts = folder.filter { it.isNotBlank() }.take(MAX_DEPTH).map { SafeFileName.clean(it) }
        return (listOf(TorrentRules.DOWNLOAD_DIR) + parts).joinToString("/") + "/"
    }

    // ── 진행의 글 ────────────────────────────────────────────────────────────────────────────────────────

    /** 목록의 둘째 줄·알림의 글 — 파일 수를 알면 'n / m개'(받은 파일), 작가 받기면 '작품 a / b · 파일 n'. */
    fun progress(units: Int, unitsDone: Int, fileCount: Int?, filesSaved: Int): Part =
        if (fileCount != null) {
            Part(R.string.gallery_progress_files, filesSaved, fileCount)
        } else {
            Part(R.string.gallery_progress_works, unitsDone, units, filesSaved)
        }

    /** 알림의 진행 막대 — (끝, 지금). 단위로 센다(파일 수를 아는 계획은 단위 하나가 파일 하나다). */
    fun bar(units: Int, unitsDone: Int): Pair<Int, Int> {
        val max = units.coerceAtLeast(1)
        return max to unitsDone.coerceIn(0, max)
    }

    /** 다 받은 뒤의 덧붙임 — 건너뛴 것이 있을 때만(로그인이 필요해 건너뛴 작품이 있으면 그 수도 — 로그인하면 받을 수 있다). */
    fun note(skipped: Int, skippedLogin: Int): Part? = when {
        skipped <= 0 -> null
        skippedLogin > 0 -> Part(R.string.gallery_skipped_login, skipped, skippedLogin)
        else -> Part(R.string.gallery_skipped, skipped)
    }

    /** ⋮ 메뉴·고리를 길게 누를 때의 한 줄. */
    fun menuRes(target: SiteTarget): Int = when (target) {
        is SiteTarget.PixivArtwork -> R.string.gallery_menu_artwork
        is SiteTarget.PixivUser -> R.string.gallery_menu_user
        is SiteTarget.HitomiGallery -> R.string.gallery_menu_hitomi
    }

    // ── 이어받을 자리 ────────────────────────────────────────────────────────────────────────────────────

    /** 계획 안의 자리 — 단위의 차례와 그 단위를 푼 파일들의 차례. [file] 이 -1 이면 그 단위를 통째로(건너뛴 작품). */
    data class Position(val unit: Int, val file: Int) {
        /** 이 자리가 [other] 보다 뒤인가 — 단위를 통째로(-1) 끝낸 자리는 그 단위의 어느 파일보다 뒤다. */
        fun after(other: Position): Boolean {
            if (unit != other.unit) return unit > other.unit
            val mine = if (file < 0) Int.MAX_VALUE else file
            val theirs = if (other.file < 0) Int.MAX_VALUE else other.file
            return mine > theirs
        }
    }

    /**
     * 이어받을 자리 — 목록에 적힌 자리([stored] — `downloads.json` 은 1.5초 모아 적는다)와 덧붙이기 기록의 마지막 일([last] — 그 파일을 드러냈거나
     * 건너뛰었다, [DownloadSets]). 앱이 그 사이에 죽었으면 기록이 앞서 있다 — 그 다음부터 받아야 같은 파일을 두 번 받지 않는다(' (1)' 사본). 받던
     * 파일은 처음부터다(감춘 항목은 버린다).
     */
    fun resumeAt(stored: Position, last: Position?): Position {
        if (last == null) return stored
        val next = if (last.file < 0) Position(last.unit + 1, 0) else Position(last.unit, last.file + 1)
        return if (next.after(stored)) next else stored
    }

    // ── 실패 — 건너뛸까, 멈출까 ──────────────────────────────────────────────────────────────────────────

    /** 단위의 종류 — 실패를 가르는 데 쓴다. */
    enum class UnitKind { FILE, HITOMI_IMAGE, PIXIV_WORK }

    fun kindOf(unit: GalleryUnit): UnitKind = when (unit) {
        is GalleryUnit.Direct -> UnitKind.FILE
        is GalleryUnit.HitomiImage -> UnitKind.HITOMI_IMAGE
        is GalleryUnit.PixivWork -> UnitKind.PIXIV_WORK
    }

    /** 단위를 파일로 풀다([io.github.donggi.iroirobrowser.core.sites.GalleryResolver.expand]) 실패했을 때. */
    sealed class UnitStep {
        /** 사용자가 멈췄거나 그만뒀다 — 어느 쪽인지는 받기의 깃발이 정한다. */
        data object Stop : UnitStep()

        /** 그 단위(작품)를 건너뛰고 센다 — [login] 이면 로그인이 필요해서(로그인하면 받을 수 있다). */
        data class Skip(val login: Boolean) : UnitStep()

        /** 받기를 멈춘다(이어받기는 그 단위부터) — [reason] 은 목록·알림에 보일 까닭. */
        class Pause(val reason: Part) : UnitStep()
    }

    /** 파일 하나를 받다 실패했을 때(다시 해 보기를 다 쓴 뒤). */
    sealed class FileStep {
        data object Stop : FileStep()

        /** hitomi 그림이 없다 — 기억한 `gg.js` 를 버리고 그 단위를 한 번 더 푼 뒤 같은 파일을 다시(그 사이 주소 셈이 바뀌었을 수 있다). */
        data object ReResolve : FileStep()

        /** 그 파일을 건너뛰고 센다(사이트에 없다). */
        data object Skip : FileStep()

        class Pause(val reason: Part) : FileStep()
    }

    /** 작가 받기에서 건너뛸 작품의 까닭 — 지워졌다·비공개·로그인이 필요하다·받을 파일이 없다. 그 밖(막힘·꼴이 다름)은 다음 작품도 같을 것이라 멈춘다. */
    private val SKIPPABLE_WORK = setOf(SiteException.Reason.NOT_FOUND, SiteException.Reason.LOGIN_REQUIRED, SiteException.Reason.EMPTY)

    /** 사이트가 '없다' 고 답한 HTTP 코드. */
    private val GONE = setOf(404, 410)

    /** 목적지(사이트)가 '없다' 고 답했는가 — 프록시(중계·Squid)가 만든 답은 빼고(그것은 사이트의 '없음' 이 아니다). */
    private fun gone(e: Throwable): Boolean = e is SiteHttpException && !e.byProxy && e.code in GONE

    /**
     * 단위를 풀다 실패했다 — pixiv 작가 받기의 작품이 지워졌거나·로그인이 필요하거나·받을 파일이 없으면 **그 작품만 건너뛰고 센다**(수천 작품 가운데
     * 하나 때문에 멈추지 않게). 그 밖 — 막힘(봇 확인)·꼴이 다름·`gg.js` 를 못 읽음·망이 이어 실패함 — 은 다음 단위도 같을 것이라 **멈춘다**.
     * [stopped] 는 사용자가 멈췄거나 그만뒀는가(그때의 예외는 그 탓이다 — 시간 초과의 `InterruptedIOException` 과 갈린다).
     */
    fun onExpandFailure(e: IOException, kind: UnitKind, stopped: Boolean, site: Site): UnitStep = when {
        stopped -> UnitStep.Stop
        kind == UnitKind.PIXIV_WORK && e is SiteException && e.reason in SKIPPABLE_WORK ->
            UnitStep.Skip(login = e.reason == SiteException.Reason.LOGIN_REQUIRED)
        kind == UnitKind.PIXIV_WORK && gone(e) -> UnitStep.Skip(login = false)
        else -> UnitStep.Pause(reason(e, site))
    }

    /**
     * 파일 하나를 받다 실패했다(다시 해 보기를 다 쓴 뒤 — [DownloadCenter] 가 망·5xx·408·429 는 세 번까지 해 본다). 사이트가 '없다'(404·410)고
     * 하면 hitomi 그림은 주소를 한 번 다시 셈해 보고([reResolved] 가 거짓일 때), 그래도 없거나 다른 파일이면 **건너뛰고 센다**. 그 밖은 멈춘다 —
     * 거절(403 등)은 다시 해도 같고, 망은 이미 세 번 실패했다.
     */
    fun onFileFailure(e: IOException, kind: UnitKind, reResolved: Boolean, stopped: Boolean, site: Site): FileStep = when {
        stopped -> FileStep.Stop
        gone(e) && kind == UnitKind.HITOMI_IMAGE && !reResolved -> FileStep.ReResolve
        gone(e) -> FileStep.Skip
        else -> FileStep.Pause(reason(e, site))
    }

    // ── 까닭의 글 ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 받을 수 없거나 멈춘 까닭 — 사이트의 까닭([SiteException.Reason]), 사이트·프록시의 HTTP 답([SiteHttpException]), 망([NetErrors] — 플랫폼의 영어
     * 글을 보이지 않는다). 사이트가 준 자세한 기계 말(`detail`)은 보이지 않는다 — 우리 문장만. 로그인이 필요하면 할 일까지(이 브라우저에서
     * 로그인 — 우리는 사용자 대신 로그인하지 않는다).
     */
    fun reason(e: Throwable, site: Site?): Part = when (e) {
        is SiteException -> when (e.reason) {
            SiteException.Reason.NOT_FOUND -> Part(R.string.gallery_err_not_found)
            SiteException.Reason.LOGIN_REQUIRED -> Part(if (site == Site.PIXIV) R.string.gallery_err_login_pixiv else R.string.gallery_err_login)
            SiteException.Reason.BLOCKED -> Part(R.string.gallery_err_blocked)
            SiteException.Reason.EMPTY -> Part(R.string.gallery_err_empty)
            SiteException.Reason.UNEXPECTED -> Part(R.string.gallery_err_unexpected)
            SiteException.Reason.TOO_LARGE -> Part(R.string.gallery_err_too_large, GalleryResolver.MAX_UNITS)
        }
        is SiteHttpException -> when {
            e.byProxy -> Part(R.string.gallery_err_proxy, e.code)
            e.code in GONE -> Part(R.string.gallery_err_not_found)
            e.code == 429 -> Part(R.string.gallery_err_busy)
            e.code >= 500 -> Part(R.string.gallery_err_server, e.code)
            else -> Part(R.string.gallery_err_http, e.code)
        }
        is IOException -> Part(R.string.gallery_err_network, NetErrors.describe(e))
        else -> Part(R.string.gallery_err_other)
    }

    // ── 같은 것을 다시 ───────────────────────────────────────────────────────────────────────────────────

    /** 같은 대상([SiteTarget.key])의 목록 항목 하나 — 번호·상태·받은 파일 수. */
    class Existing(val id: Long, val status: DownloadCenter.Status, val filesSaved: Int)

    /** 같은 것을 받으려 할 때. */
    sealed class Again {
        /** 받는 중이거나 멈춘 것이 있다 — 다시 넣지 않고 목록을 보인다(이어받기는 거기서). */
        data class Listed(val id: Long) : Again()

        /** 받은 적이 있다 — 다시 받을지 한 번 묻는다(받으면 같은 폴더에 ' (1)' 사본이 생긴다). */
        data object Ask : Again()

        /** 처음이거나 받은 것 없이 끝났다 — 묻지 않는다. */
        data object Fresh : Again()
    }

    fun again(existing: List<Existing>): Again {
        existing.firstOrNull { it.status == DownloadCenter.Status.QUEUED || it.status == DownloadCenter.Status.RUNNING }
            ?.let { return Again.Listed(it.id) }
        existing.firstOrNull { it.status == DownloadCenter.Status.PAUSED }?.let { return Again.Listed(it.id) }
        return if (existing.any { it.status == DownloadCenter.Status.DONE || it.filesSaved > 0 }) Again.Ask else Again.Fresh
    }

    // ── 목록의 할 일 ─────────────────────────────────────────────────────────────────────────────────────

    /** 목록에서 갤러리 받기 하나에 할 수 있는 일. */
    enum class Action { PAUSE, RESUME, STOP, OPEN_FIRST, OPEN_PAGE, REMOVE, DELETE_FILES }

    /**
     * 지금 할 수 있는 일 — 받는 중이면 일시 정지·그만 받기, 멈췄으면 이어받기, 다 받았으면 첫 파일 열기. 원래 페이지 열기는 페이지를 알면 늘.
     * 끝났거나 멈춘 것은 목록에서 빼기(받은 파일은 그대로)와, 받은 파일이 있으면 받은 파일까지 지우기. **'그만 받기' 는 받은 파일을 남긴다** — 그래서
     * 보통 파일의 '받기 취소(지움)' 와 이름이 다르다.
     */
    fun actions(status: DownloadCenter.Status, filesSaved: Int, hasFirst: Boolean, hasPage: Boolean): List<Action> = buildList {
        val active = status == DownloadCenter.Status.QUEUED || status == DownloadCenter.Status.RUNNING
        when (status) {
            DownloadCenter.Status.QUEUED, DownloadCenter.Status.RUNNING -> {
                add(Action.PAUSE)
                add(Action.STOP)
            }
            DownloadCenter.Status.PAUSED -> add(Action.RESUME)
            DownloadCenter.Status.DONE -> if (hasFirst) add(Action.OPEN_FIRST)
            DownloadCenter.Status.FAILED, DownloadCenter.Status.CANCELLED -> Unit
        }
        if (hasPage) add(Action.OPEN_PAGE)
        if (!active) {
            add(Action.REMOVE)
            if (filesSaved > 0) add(Action.DELETE_FILES)
        }
    }
}
