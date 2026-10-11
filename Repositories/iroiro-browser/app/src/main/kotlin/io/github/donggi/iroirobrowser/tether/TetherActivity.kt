package io.github.donggi.iroirobrowser.tether

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.setup.SetupBundle
import io.github.donggi.iroirobrowser.setup.SetupFiles
import io.github.donggi.iroirobrowser.tether.core.PassphraseVault
import io.github.donggi.iroirobrowser.tether.core.Passphrases
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 테더링 화면 — 상태(포트·핫스팟·주고받은 양·거절한 연결)와 **하나뿐인 켜기·끄기**('테더링 켜기'·'테더링 끄기' — 서버 셋과 핫스팟을 함께,
 * 사용자 결정 2026-10-10), 핫스팟(Wi-Fi Direct)의 SSID·패스프레이즈, 공유 폴더. 브라우저의 ⋮ 메뉴 '테더링'·알림·퀵 설정 타일을 길게 누르기
 * ([TilePreferencesActivity] 를 거쳐)에서 연다. **`:tether` 프로세스** — 상태([TetherState])가 그 프로세스의 정적 필드다. 브라우저의
 * 것(WebApp.of)을 부르지 않는다(그 프로세스에서는 막혀 있다 — WebApp).
 *
 * 화면은 플랫폼 뷰로 짓는다(브라우저의 설정 화면과 같은 꼴 — usb-tether 의 AppCompat·Material 은 들이지 않았다). 내보내지 않는다 — 다른 앱이
 * 이 화면을 띄울 까닭이 없다(usb-tether 의 런처 화면은 내보내야 했다). 타일을 길게 누를 때 시스템 UI 가 띄우는 것은 그 일만 하는 작은 화면이다.
 *
 * 핫스팟이 서지 못하거나 켜진 뒤 내려가면 서비스가 테더링을 통째로 끄고, 이 화면의 상태 줄에 '테더링 꺼짐' 과 그 까닭이 남는다(단추는 다시
 * '테더링 켜기'). 서버 하나가 포트를 얻지 못한 것은 테더링을 끄지 않는다 — 그 서버의 줄에 까닭이 보인다.
 *
 * 권한은 **'테더링 켜기' 를 누를 때만** 청한다([TetherPermissions]). 패스프레이즈 칸은 화면 상태 저장에서 빠지고(`isSaveEnabled = false` —
 * 프록시 비밀번호 칸과 같다) 자동 완성에도 빠진다(`importantForAutofill = NO` — 자동 완성 서비스가 '저장할까요?' 를 띄우지 않게).
 *
 * 상태는 둘로 나눠 그린다: 서버·핫스팟의 상태 줄은 화면 낭독기의 **라이브 영역**이고 글이 실제로 바뀔 때만 다시 쓴다. 1초마다
 * 바뀌는 주고받은 양·거절·상한의 수는 라이브 영역 밖의 줄에 둔다 — 예전에는 한 글이라 1초마다 다시 쓰여 낭독기가 쉬지 않고 읽었다.
 */
class TetherActivity : Activity() {

    private lateinit var settings: TetherSettings
    private lateinit var folder: SharedFolder
    private lateinit var status: TextView
    private lateinit var traffic: TextView

    /** 하나뿐인 켜기·끄기 — 서버 셋과 핫스팟을 함께. */
    private lateinit var toggleButton: Button
    private lateinit var ssid: EditText
    private lateinit var passphrase: EditText
    private lateinit var passphraseUnreadable: TextView
    private lateinit var effectiveSsid: TextView
    private lateinit var locationButton: Button
    private lateinit var appSettingsButton: Button
    private lateinit var folderName: TextView
    private lateinit var forgetButton: Button
    private lateinit var battery: TextView

    private val main = Handler(Looper.getMainLooper())
    private val listener: () -> Unit = { render() }
    private val tick = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = TetherSettings(this)
        folder = SharedFolder(this, settings)
        val scroll = ScrollView(this)
        Ui.edgeToEdge(this, scroll)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 32))
        }
        scroll.addView(column)
        column.addView(Ui.header(this, getString(R.string.tether_title)) { finish() })

        section(column, R.string.tether_section_status)
        status = note(column, R.string.tether_status_off).apply {
            alpha = 1f
            textSize = 14f
            // 상태가 실제로 바뀔 때만 다시 쓰는 줄 — render() 가 같은 글이면 손대지 않는다.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        // 1초마다 바뀌는 수 — 라이브 영역 밖.
        traffic = note(column, 0, "").apply { textSize = 13f }
        toggleButton = button(column, R.string.tether_toggle_start) { toggle() }
        // 꺼진 까닭이 위치 서비스·권한이면 그 설정으로 가는 단추(그 문제일 때만 보인다).
        locationButton = button(column, R.string.tether_open_location_settings) { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        appSettingsButton = button(column, R.string.tether_open_app_settings) { openAppSettings() }
        note(column, R.string.tether_toggle_note)
        note(column, R.string.tether_server_note)
        note(column, R.string.tether_permission_note)

        section(column, R.string.tether_section_hotspot)
        note(column, R.string.tether_hotspot_note)
        ssid = field(column, R.string.tether_ssid, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, settings.ssid())
        effectiveSsid = note(column, 0, "")
        // 화면과 서비스가 같은 값을 쓴다(같은 프로세스의 보관소). 저장값을 열지 못했으면 칸을 비우고 까닭을 보인다 — 새 난수로 덮어쓰지 않는다.
        val stored = settings.passphrase()
        passphrase = field(
            column, R.string.tether_passphrase,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, (stored as? PassphraseVault.Current.Ready)?.passphrase.orEmpty(),
        ).apply {
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        passphraseUnreadable = note(column, R.string.tether_passphrase_unreadable).apply {
            alpha = 1f
            visibility = if (stored == PassphraseVault.Current.Unreadable) View.VISIBLE else View.GONE
        }
        column.addView(padded(CheckBox(this).apply {
            text = getString(R.string.tether_show_passphrase)
            setOnCheckedChangeListener { _, on ->
                passphrase.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (on) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                passphrase.setSelection(passphrase.text.length)
            }
        }))

        section(column, R.string.tether_section_folder)
        note(column, R.string.tether_folder_note)
        folderName = note(column, 0, "").apply { alpha = 1f }
        button(column, R.string.tether_pick_folder) { pickFolder() }
        forgetButton = button(column, R.string.tether_forget_folder) { confirmForget() }

        section(column, R.string.tether_section_pc)
        note(column, R.string.tether_pc_note)
        // 시작 스크립트와 README 를 zip 하나로 '내려받기' 에(setup/SetupFiles — 브라우저의 것을 부르지 않는다).
        button(column, R.string.setup_tether_pc_files) { SetupFiles.save(this, SetupBundle.TETHER_PC) }

        section(column, R.string.tether_section_notes)
        note(column, R.string.tether_direct_note)
        battery = note(column, 0, "")
        button(column, R.string.tether_open_battery_settings) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        }
        render()
        renderSlow()
    }

    /** Android 12 이하의 뒤로 — 13 이상은 [onCreate] 에서 건 `OnBackInvokedCallback`(브라우저의 화면들과 같은 까닭). */
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Android 12 이하의 뒤로 — 13 이상은 OnBackInvokedCallback")
    override fun onBackPressed() {
        finish()
    }

    override fun onResume() {
        super.onResume()
        TetherState.addListener(listener)
        main.post(tick)
        renderSlow()
    }

    override fun onPause() {
        TetherState.removeListener(listener)
        main.removeCallbacks(tick)
        super.onPause()
    }

    // ── 할 일 ────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 하나뿐인 켜기·끄기(사용자 결정 2026-10-10). 켜져 있으면(켜는 중 포함) 서비스를 멈춰 핫스팟과 서버를 함께 내린다. 꺼져 있으면 넣은 값을
     * 보고 권한을 청한 뒤 서비스에 맡긴다 — 서버 셋과 핫스팟이 함께 선다(핫스팟이 서지 못하면 서비스가 통째로 끄고 까닭을 남긴다).
     */
    private fun toggle() {
        if (TetherState.tetheringOn) {
            TetherService.stop(this)
            main.postDelayed({ render() }, 200)
            return
        }
        // 넣은 값을 먼저 본다 — 뻔한 패스프레이즈·길이·SSID 의 바이트.
        val typedSsid = ssid.text.toString()
        val typedPassphrase = passphrase.text.toString()
        Passphrases.check(typedSsid, typedPassphrase)?.let { problem ->
            val text = TetherTexts.hotspot(TetherTexts.credential(problem), null)
            AlertDialog.Builder(this).setMessage(getString(text.res, *text.args.toTypedArray())).setPositiveButton(R.string.close, null).show()
            return
        }
        // 잠가 두지 못해도 이 프로세스의 서비스는 이 값을 쓴다 — 알리기만 한다.
        if (!settings.save(typedSsid, typedPassphrase)) Toast.makeText(this, R.string.tether_passphrase_not_saved, Toast.LENGTH_LONG).show()
        passphraseUnreadable.visibility = View.GONE
        // 꼭 필요한 권한이 없을 때만 묻는다(13 이상은 알림도 함께 — 알림은 거절해도 켠다).
        if (!TetherPermissions.hasHotspotPermission(this)) {
            requestPermissions(TetherPermissions.toRequest(this), REQ_HOTSPOT)
            return
        }
        startTethering()
    }

    private fun startTethering() {
        if (!TetherService.start(this)) {
            Toast.makeText(this, R.string.tether_service_refused, Toast.LENGTH_LONG).show()
        }
        main.postDelayed({ render() }, 200)
    }

    @Deprecated("플랫폼 Activity 의 결과 받기 — AndroidX 없이")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQ_HOTSPOT) return
        when {
            TetherPermissions.hasHotspotPermission(this) -> startTethering()
            // 12 이하에서 '대략적인 위치' 만 받았다 — Wi-Fi Direct 는 정확한 위치가 있어야 한다.
            TetherPermissions.onlyCoarse(this) -> explainPermission(R.string.tether_permission_precise)
            else -> explainPermission(R.string.tether_permission_denied)
        }
    }

    private fun explainPermission(message: Int) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.tether_open_app_settings) { _, _ -> openAppSettings() }
            .setNegativeButton(R.string.close, null)
            .show()
        render()
    }

    private fun pickFolder() {
        try {
            // 플랫폼 Activity 의 결과 — AndroidX 의 결과 API 는 새 의존이다(브라우저의 북마크 화면과 같다).
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.tether_folder_picker_missing, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("플랫폼 Activity 의 결과 받기 — AndroidX 없이")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (!folder.remember(uri)) Toast.makeText(this, R.string.tether_folder_failed, Toast.LENGTH_LONG).show()
        renderSlow()
    }

    private fun confirmForget() {
        AlertDialog.Builder(this)
            .setMessage(R.string.tether_forget_question)
            .setPositiveButton(R.string.tether_forget_folder) { _, _ ->
                folder.forget()
                renderSlow()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openAppSettings() {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    // ── 그리기 ───────────────────────────────────────────────────────────────────────────────────────────

    private fun render() {
        val statusText = TetherTexts.status(
            TetherTexts.Status(
                serviceRunning = TetherState.serviceRunning,
                foregroundDenied = TetherState.foregroundDenied,
                socksPort = TetherState.socksPort,
                httpPort = TetherState.httpPort,
                filePort = TetherState.filePort,
                socksError = TetherState.socksError,
                httpError = TetherState.httpError,
                fileError = TetherState.fileError,
                hotspotActive = TetherState.hotspotActive,
                hotspotStarting = TetherState.hotspotStarting,
                hotspotSsid = TetherState.hotspotSsid,
                groupInterface = TetherState.groupInterface,
                hotspotProblem = TetherState.hotspotProblem,
                hotspotDetail = TetherState.hotspotDetail,
            ),
        ).joinToString("\n") { text(it) }
        // 라이브 영역은 글이 실제로 바뀔 때만 손댄다 — 같은 글을 다시 넣어도 낭독기가 읽을 수 있다.
        if (status.text.toString() != statusText) status.text = statusText
        // 아래 글들도 1초마다 그리지만 같으면 손대지 않는다 — 같은 글을 넣어도 접근성 이벤트가 나가 화면이 늘 바쁘다(uiautomator dump 가
        // 'could not get idle state' 로 실패했다).
        val trafficText = TetherTexts.traffic(
            TetherTexts.Traffic(
                bytesIn = Formatter.formatShortFileSize(this, TetherState.bytesIn.get()),
                bytesOut = Formatter.formatShortFileSize(this, TetherState.bytesOut.get()),
                rejected = TetherState.rejected.get(),
                lastRejection = TetherState.lastRejection,
                refusedBudget = TetherState.refusedBudget.get(),
                refusedSessions = TetherState.refusedSessions.get(),
                refusedUdp = TetherState.refusedUdp.get(),
            ),
        ).joinToString("\n") { text(it) }
        if (traffic.text.toString() != trafficText) traffic.text = trafficText
        // 켜져 있는 동안(켜는 중 포함)은 끄기 단추이고 SSID·패스프레이즈를 잠근다(그 값으로 그룹을 세우고 있다).
        val on = TetherState.tetheringOn
        val toggleText = getString(if (on) R.string.tether_toggle_stop else R.string.tether_toggle_start)
        if (toggleButton.text.toString() != toggleText) toggleButton.text = toggleText
        ssid.isEnabled = !on
        passphrase.isEnabled = !on
        val effective = getString(R.string.tether_ssid_effective, Passphrases.normalizeSsid(ssid.text.toString()))
        if (effectiveSsid.text.toString() != effective) effectiveSsid.text = effective
        locationButton.visibility = if (TetherState.hotspotProblem == HotspotProblem.LOCATION_OFF) View.VISIBLE else View.GONE
        appSettingsButton.visibility =
            if (TetherState.hotspotProblem == HotspotProblem.PERMISSION || TetherPermissions.onlyCoarse(this)) View.VISIBLE else View.GONE
    }

    /** 1초마다 고치지 않는 것 — 공유 폴더(제공자에 묻는 IPC)와 배터리 최적화. 돌아올 때와 바꾼 뒤에만. */
    private fun renderSlow() {
        val name = folder.rootName()
        folderName.text = if (name != null) getString(R.string.tether_folder_current, name) else getString(R.string.tether_folder_none)
        forgetButton.visibility = if (settings.treeUri != null) View.VISIBLE else View.GONE
        val ignoring = getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true
        battery.text = getString(if (ignoring) R.string.tether_battery_exempt else R.string.tether_battery_optimized)
    }

    /** 글 — 인자가 글이면 그것부터 바꾼다(겹친 글). */
    private fun text(t: TetherTexts.Text): String = getString(t.res, *t.args.map { if (it is TetherTexts.Text) text(it) else it }.toTypedArray())

    // ── 짓기(설정 화면과 같은 꼴) ────────────────────────────────────────────────────────────────────────────

    private fun section(column: LinearLayout, title: Int) {
        column.addView(TextView(this).apply {
            text = getString(title)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Ui.themeColor(context, android.R.attr.colorAccent))
            setPadding(Ui.dp(context, 20), Ui.dp(context, 24), Ui.dp(context, 20), Ui.dp(context, 4))
        })
    }

    private fun note(column: LinearLayout, text: Int, raw: String? = null): TextView {
        val view = TextView(this).apply {
            this.text = raw ?: getString(text)
            textSize = 13f
            alpha = 0.75f
            setPadding(Ui.dp(context, 20), Ui.dp(context, 2), Ui.dp(context, 20), Ui.dp(context, 6))
        }
        column.addView(view)
        return view
    }

    /** 이름이 위에 붙은 칸 — 이름을 칸에 잇는다(`labelFor` — 화면 낭독기가 칸을 이름과 함께 읽는다). */
    private fun field(column: LinearLayout, label: Int, type: Int, value: String): EditText {
        val edit = EditText(this).apply {
            id = View.generateViewId()
            // 한 줄로 **먼저** 묶고 입력 종류를 나중에 건다. 거꾸로 하면 setSingleLine 이 글자 바꾸기를 한 줄 꼴로 갈아 끼워 비밀번호 가리기가
            // 풀린다 — 패스프레이즈가 '보이기' 를 끈 채 그대로 보였다(2026-10-10 에뮬레이터). 입력 종류(멀티라인 깃발 없음)도 한 줄을 정한다.
            isSingleLine = true
            inputType = type
            setText(value)
        }
        column.addView(TextView(this).apply {
            text = getString(label)
            textSize = 13f
            setPadding(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), 0)
            labelFor = edit.id
        })
        column.addView(padded(edit))
        return edit
    }

    private fun button(column: LinearLayout, label: Int, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = getString(label)
            setOnClickListener { onClick() }
        }
        column.addView(padded(b))
        return b
    }

    private fun padded(view: View): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
        }
        box.addView(view, Ui.matchWidth())
        return box
    }

    private companion object {
        const val REQ_HOTSPOT = 1
        const val REQ_FOLDER = 2

        /** 주고받은 양을 고쳐 그리는 간격. */
        const val TICK_MS = 1000L
    }
}
