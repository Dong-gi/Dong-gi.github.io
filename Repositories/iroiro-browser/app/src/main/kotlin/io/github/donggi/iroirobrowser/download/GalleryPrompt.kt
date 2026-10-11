package io.github.donggi.iroirobrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.core.sites.GalleryPlan
import io.github.donggi.iroirobrowser.core.sites.SiteTarget
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 브라우저 화면에서 갤러리 받기(pixiv 작품·작가, hitomi.la 갤러리 — 사용자 결정 2026-10-11)로 들어가는 길. 브라우저의 ⋮ 메뉴('이 작품 받기'·'이
 * 작가의 작품 모두 받기'·'이 갤러리 받기' — 지금 페이지의 커밋된 주소를 알아볼 때만)와 고리를 길게 눌렀을 때의 같은 줄이 [offer] 를 부른다. 묻는 차례:
 *
 * 1. 같은 대상([SiteTarget.key])이 목록에서 받는 중·멈춤이면 '이미 내려받기 목록에 있습니다'(목록 보기 — 이어받기는 거기서), 받은 적이 있으면 다시
 *    받을지 한 번 묻는다(같은 폴더에 ' (1)' 사본이 생긴다 — [GalleryRules.again]).
 * 2. 취소할 수 있는 '목록을 읽는 중…' 창 — [DownloadCenter.planGallery](내려받기와 같은 길, WebView 의 쿠키 — pixiv 는 이 브라우저의 로그인으로
 *    묻는다. 우리는 사용자 대신 로그인하지 않는다).
 * 3. 받을 수 없으면 까닭 한 줄(로그인이 필요하면 할 일까지), 아니면 확인 창(제목·작가·파일 또는 작품 수·저장 자리) → 받기 → '받기 시작했습니다 ·
 *    목록 보기'.
 *
 * '주소로 받기'(붙여 넣기)는 없고, 다른 앱의 VIEW 인텐트도 받지 않는다 — 이 브라우저 안에서 연 페이지뿐이다. 글은 짧게(사용자: '설명이 너무
 * 장황합니다'). 메인 스레드.
 */
internal class GalleryPrompt(private val activity: Activity, private val host: Host) {

    interface Host {
        fun trace(message: String)

        fun askNotificationPermission()

        /** 아래에 잠깐 뜨는 한 줄과 단추 하나('받기 시작했습니다 · 목록 보기'). */
        fun showMessage(text: String, action: String, onAction: () -> Unit)
    }

    private val app = WebApp.of(activity)

    /** 목록을 읽는 중의 진행 창 — 하나만. */
    private var reading: AlertDialog? = null
    private var inspection: DownloadCenter.Inspection? = null
    private var readingSerial = 0

    fun close() {
        stopReading()
    }

    /** [target] 을 받으려 한다 — [userAgent] 는 그 탭의 WebView 의 것(쿠키와 함께 페이지와 같게 보이도록). */
    fun offer(target: SiteTarget, userAgent: String) {
        host.trace("gallery offered ${target.key}")
        when (GalleryRules.again(existing(target))) {
            is GalleryRules.Again.Listed -> showListed()
            GalleryRules.Again.Ask -> AlertDialog.Builder(activity)
                .setMessage(R.string.gallery_ask_again)
                .setPositiveButton(R.string.gallery_again) { _, _ -> read(target, userAgent) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            GalleryRules.Again.Fresh -> read(target, userAgent)
        }
    }

    private fun existing(target: SiteTarget): List<GalleryRules.Existing> =
        app.downloads.galleryItems(target.key).map { GalleryRules.Existing(it.id, it.status, it.gallery?.filesSaved ?: 0) }

    private fun showListed() {
        AlertDialog.Builder(activity)
            .setMessage(R.string.gallery_already_listed)
            .setPositiveButton(R.string.gallery_open_list) { _, _ -> openList() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** 목록을 읽는다 — 읽는 동안 취소할 수 있는 창. 거듭 누름은 무시한다. */
    private fun read(target: SiteTarget, userAgent: String) {
        val serial = startReading() ?: return
        inspection = app.downloads.planGallery(target, userAgent) { result ->
            if (!stillReading(serial)) return@planGallery
            stopReading()
            when (result) {
                is DownloadCenter.GalleryResult.Failed -> {
                    host.trace("gallery refused ${target.key}")
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.gallery_cannot)
                        .setMessage(result.message)
                        .setNegativeButton(R.string.close, null)
                        .show()
                }
                is DownloadCenter.GalleryResult.Ready -> confirm(result.plan, userAgent)
            }
        }
    }

    /** 확인 창 — 제목, 작가, 파일 수(작가 받기는 작품 수), 저장 자리. */
    private fun confirm(plan: GalleryPlan, userAgent: String) {
        val lines = ArrayList<String>()
        plan.creator?.takeIf { it.isNotBlank() }?.let { lines += activity.getString(R.string.gallery_creator, it) }
        // 다른 모듈의 속성은 스마트 캐스트가 되지 않는다 — 지역 변수로 받는다.
        val count = plan.fileCount
        lines += if (count != null) activity.getString(R.string.gallery_files, count) else activity.getString(R.string.gallery_works, plan.units.size)
        lines += activity.getString(R.string.gallery_place, GalleryRules.relativePath(plan.folder))
        AlertDialog.Builder(activity)
            .setTitle(plan.title.ifBlank { plan.target.key })
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(R.string.download) { _, _ -> start(plan, userAgent) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun start(plan: GalleryPlan, userAgent: String) {
        // 읽는 사이 같은 것을 다른 탭에서 넣었을 수 있다 — 받는 중·멈춘 것이 있으면 다시 넣지 않는다.
        if (GalleryRules.again(existing(plan.target)) is GalleryRules.Again.Listed) {
            showListed()
            return
        }
        host.askNotificationPermission()
        app.downloads.enqueueGallery(plan, userAgent)
        host.trace("gallery started ${plan.target.key} units=${plan.units.size}")
        host.showMessage(activity.getString(R.string.gallery_started), activity.getString(R.string.gallery_open_list)) { openList() }
    }

    private fun openList() {
        activity.startActivity(Intent(activity, DownloadsActivity::class.java))
    }

    // ── 진행 창 ─────────────────────────────────────────────────────────────────────────────────────────

    /** 목록을 읽는 중 — 취소할 수 있다. 이미 떠 있으면 null(거듭 누름은 무시). */
    private fun startReading(): Int? {
        if (reading != null) return null
        val serial = ++readingSerial
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 24), Ui.dp(activity, 20), Ui.dp(activity, 24), Ui.dp(activity, 8))
            addView(ProgressBar(activity).apply { isIndeterminate = true })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.gallery_reading)
                textSize = 16f
                setPadding(Ui.dp(activity, 16), 0, 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(row)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            // 사용자가 닫았다 — 늦게 오는 답을 버린다(답이 와서 닫을 때는 먼저 비우므로 여기에 오지 않는다).
            if (reading === dialog) {
                reading = null
                readingSerial++
                inspection?.cancel()
                inspection = null
                host.trace("gallery reading cancelled")
            }
        }
        reading = dialog
        dialog.show()
        return serial
    }

    private fun stillReading(serial: Int): Boolean = serial == readingSerial && reading != null && !activity.isDestroyed

    private fun stopReading() {
        val d = reading ?: return
        reading = null
        inspection = null
        d.dismiss()
    }
}
