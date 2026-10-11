package io.github.donggi.iroirobrowser.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.adblock.AdBlockActivity
import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.core.SearchEngine
import io.github.donggi.iroirobrowser.setup.SetupBundle
import io.github.donggi.iroirobrowser.setup.SetupFiles
import io.github.donggi.iroirobrowser.sites.SiteDataActivity
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 설정 — 프록시(호스트·포트·계정·예외·출구 확인 주소), 토렌트('Wi-Fi 에서만'), 화면(전체 화면), 검색 엔진, 사이트별 데이터·사용 기록 지우기, 정보. **프록시를 켜고 끄는 것은
 * 브라우저 화면 왼쪽 위의 스위치뿐이다**(사용자 지정 — 같은 단추를 다른 곳에 두지 않는다). 여기서는 어느 프록시로 나갈지만 정한다.
 *
 * 비밀번호 칸은 비워 두면 저장된 비밀번호를 그대로 둔다(다시 칠 필요가 없게). 화면 상태 저장에서 빠진다(`isSaveEnabled = false`).
 * 저장하면 길이 바뀌었을 때만 중계를 새로 세우고(새 세션 계정) WebView 의 길을 다시 걸고 출구를 다시 본다
 * ([io.github.donggi.iroirobrowser.proxy.NetworkPath.update] — 길이 그대로면 저장만 한다). 바꾸고 저장하지 않은 채 나가면 묻는다.
 */
@Suppress("DEPRECATION") // Switch: 플랫폼 위젯을 쓴다(새 의존 없이)
class SettingsActivity : Activity() {

    private lateinit var app: WebApp
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var bypassHosts: EditText
    private lateinit var bypassCidrs: EditText
    private lateinit var privateNetworks: Switch
    private lateinit var simpleHostnames: Switch
    private lateinit var exitUrl: EditText
    private lateinit var engines: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = WebApp.of(this)
        val s = app.network.settings
        val scroll = ScrollView(this)
        Ui.edgeToEdge(this, scroll)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 32))
        }
        scroll.addView(column)
        column.addView(Ui.header(this, getString(R.string.settings)) { leave() })

        section(column, R.string.settings_proxy)
        note(column, R.string.settings_proxy_switch_note)
        host = field(column, R.string.settings_host, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, s.host)
        port = field(column, R.string.settings_port, InputType.TYPE_CLASS_NUMBER, s.port.toString())
        user = field(column, R.string.settings_user, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, s.user)
        password = field(column, if (s.password.isEmpty()) R.string.settings_password else R.string.settings_password_keep,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "").apply { isSaveEnabled = false }
        val show = CheckBox(this).apply {
            text = getString(R.string.settings_show_password)
            setOnCheckedChangeListener { _, on ->
                password.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (on) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                password.setSelection(password.text.length)
            }
        }
        column.addView(padded(show))
        // 프록시 서버를 세우는 스크립트와 설명(README)을 zip 하나로 '내려받기' 에(CLAUDE.md '설정 파일 zip').
        column.addView(padded(Button(this).apply {
            text = getString(R.string.setup_server_files)
            setOnClickListener { SetupFiles.save(this@SettingsActivity, SetupBundle.PROXY_SERVER) }
        }))

        section(column, R.string.settings_bypass)
        note(column, R.string.settings_bypass_note)
        bypassHosts = field(column, R.string.settings_bypass_hosts, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            s.bypass.hosts.joinToString("\n"), multiLine = true)
        bypassCidrs = field(column, R.string.settings_bypass_cidrs, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            s.bypass.cidrs.joinToString("\n"), multiLine = true)
        privateNetworks = Switch(this).apply {
            text = getString(R.string.settings_bypass_private)
            isChecked = s.bypass.privateNetworks
        }
        column.addView(padded(privateNetworks))
        simpleHostnames = Switch(this).apply {
            text = getString(R.string.settings_bypass_simple)
            isChecked = s.bypass.simpleHostnames
        }
        column.addView(padded(simpleHostnames))

        section(column, R.string.settings_exit)
        note(column, R.string.settings_exit_note)
        exitUrl = field(column, R.string.settings_exit_url, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, s.exitCheckUrl)

        column.addView(padded(Button(this).apply {
            text = getString(R.string.settings_save)
            setOnClickListener { save() }
        }))

        section(column, R.string.adblock)
        val ads = app.adblock
        note(column, 0, if (ads.enabled) getString(R.string.settings_adblock_on, ads.lists.count { it.enabled }) else getString(R.string.settings_adblock_off))
        column.addView(padded(Button(this).apply {
            text = getString(R.string.settings_adblock_open)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, AdBlockActivity::class.java)) }
        }))

        // 토렌트 — 프록시를 거치지 않는다는 것(브라우저가 직접 연결로 넘어가지 않는다는 약속의 예외 — 다른 하나는 테더링)을 늘 적어 두고, 'Wi-Fi 에서만'(사용자 결정
        // — 기본 끔)은 고르는 즉시 저장하고 엔진이 망 조건을 곧바로 다시 묻게 한다.
        section(column, R.string.settings_torrent)
        note(column, R.string.settings_torrent_note)
        column.addView(padded(Switch(this).apply {
            text = getString(R.string.settings_torrent_wifi_only)
            isChecked = app.settings.torrentWifiOnly
            setOnCheckedChangeListener { _, on ->
                app.settings.torrentWifiOnly = on
                app.torrents.settingsChanged()
            }
        }))
        note(column, R.string.settings_torrent_wifi_note)

        // 전체 화면 — 고르는 즉시 저장한다(검색 엔진처럼 '저장' 단추를 기다리지 않는다). 브라우저 화면은 돌아갈 때(onResume) 읽는다.
        // 설명은 스위치 아래에 둔다(토렌트 절과 같은 차례) — 위에 두면 '전체 화면' 바로 밑의 '항상 확대 허용' 설명이 전체 화면의 것으로 읽혔다.
        section(column, R.string.settings_display)
        column.addView(padded(Switch(this).apply {
            text = getString(R.string.settings_fullscreen)
            isChecked = app.settings.fullscreen
            setOnCheckedChangeListener { _, on -> app.settings.fullscreen = on }
        }))
        note(column, R.string.settings_fullscreen_note)
        // 항상 확대 허용 — 같은 까닭으로 곧바로 저장한다. 브라우저 화면은 돌아갈 때(onResume) 열린 탭에 걸거나 걷는다.
        column.addView(padded(Switch(this).apply {
            text = getString(R.string.settings_force_zoom)
            isChecked = app.settings.forceZoom
            setOnCheckedChangeListener { _, on -> app.settings.forceZoom = on }
        }))
        note(column, R.string.settings_force_zoom_note)

        section(column, R.string.settings_search)
        engines = RadioGroup(this)
        val currentEngine = app.settings.searchEngine
        SearchEngine.entries.forEach { engine ->
            engines.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = engine.label
                tag = engine
                isChecked = engine == currentEngine
            })
        }
        engines.setOnCheckedChangeListener { group, checkedId ->
            (group.findViewById<View>(checkedId)?.tag as? SearchEngine)?.let { app.settings.searchEngine = it }
        }
        column.addView(padded(engines))

        section(column, R.string.settings_data)
        column.addView(padded(Button(this).apply {
            text = getString(R.string.site_data)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, SiteDataActivity::class.java)) }
        }))
        column.addView(padded(Button(this).apply {
            text = getString(R.string.settings_clear_history)
            setOnClickListener { confirmClearHistory() }
        }))
        column.addView(padded(Button(this).apply {
            text = getString(R.string.settings_clear_data)
            setOnClickListener { confirmClear() }
        }))

        section(column, R.string.settings_popups)
        note(column, R.string.settings_popups_note)
        val popupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(popupList)
        renderPopupSites(popupList)

        section(column, R.string.settings_about)
        val webView = WebViewCompat.getCurrentWebViewPackage(this)?.versionName ?: "?"
        note(column, 0, getString(R.string.settings_about_text, BuildConfig.VERSION_NAME, webView))
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }
    }

    /** Android 12 이하의 뒤로 — 13 이상은 [onCreate] 에서 건 `OnBackInvokedCallback`(BrowserActivity 와 같은 까닭). */
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Android 12 이하의 뒤로 — 13 이상은 OnBackInvokedCallback")
    override fun onBackPressed() {
        leave()
    }

    /** 나간다 — 바꾼 것이 저장되지 않았으면 먼저 묻는다. */
    private fun leave() {
        if (!isDirty()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_unsaved_title)
            .setMessage(R.string.settings_unsaved_question)
            .setPositiveButton(R.string.settings_save) { _, _ -> save() }
            .setNegativeButton(R.string.settings_discard) { _, _ -> finish() }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    /** 프록시 칸들이 저장된 것과 다른가(검색 엔진은 고르는 즉시 저장된다). */
    private fun isDirty(): Boolean {
        val s = app.network.settings
        return host.text.toString().trim() != s.host ||
            port.text.toString().trim() != s.port.toString() ||
            user.text.toString() != s.user ||
            password.text.isNotEmpty() ||
            Bypass.split(bypassHosts.text.toString()) != s.bypass.hosts ||
            Bypass.split(bypassCidrs.text.toString()) != s.bypass.cidrs ||
            privateNetworks.isChecked != s.bypass.privateNetworks ||
            simpleHostnames.isChecked != s.bypass.simpleHostnames ||
            exitUrl.text.toString().trim() != s.exitCheckUrl
    }

    private fun save() {
        val errors = ArrayList<String>()
        // 켜고 끄기는 브라우저 화면의 스위치 — 여기서는 그대로 둔다. 켜져 있으면 호스트가 있어야 한다(없으면 아무 데도 나가지 않는다).
        val on = app.network.settings.enabled
        val hostText = host.text.toString().trim()
        val portText = port.text.toString().trim()
        if (on || hostText.isNotEmpty()) {
            ProxySettings.checkHost(hostText)?.let { errors += it }
            ProxySettings.checkPort(portText)?.let { errors += it }
        }
        val hosts = Bypass.split(bypassHosts.text.toString()).mapNotNull { raw ->
            try {
                Bypass.normalizeHost(raw)
            } catch (e: Bypass.Invalid) {
                errors += e.message ?: raw
                null
            }
        }
        val cidrs = Bypass.split(bypassCidrs.text.toString()).mapNotNull { raw ->
            try {
                Bypass.normalizeCidr(raw)
            } catch (e: Bypass.Invalid) {
                errors += e.message ?: raw
                null
            }
        }
        val exit = exitUrl.text.toString().trim()
        if (!exit.startsWith("https://") && !exit.startsWith("http://")) errors += getString(R.string.settings_exit_invalid)
        if (errors.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle(R.string.settings_invalid).setMessage(errors.joinToString("\n")).setPositiveButton(R.string.close, null).show()
            return
        }
        val old = app.network.settings
        val typed = password.text.toString()
        val next = ProxySettings(
            enabled = on,
            host = hostText,
            port = portText.toIntOrNull() ?: ProxySettings.DEFAULT_PORT,
            user = user.text.toString(),
            password = typed.ifEmpty { old.password },
            bypass = Bypass(hosts, cidrs, privateNetworks.isChecked, simpleHostnames.isChecked),
            exitCheckUrl = exit,
        )
        val publicRanges = cidrs.filter(Bypass::isPublicCidr)
        if (publicRanges.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_public_title)
                .setMessage(getString(R.string.settings_public_warning, publicRanges.joinToString(", ")))
                .setPositiveButton(R.string.settings_save) { _, _ -> apply(next) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            apply(next)
        }
    }

    /**
     * 저장한다. 길이 그대로면(아무것도 바꾸지 않았거나 꺼져 있을 때 주소만 바꿨다) [io.github.donggi.iroirobrowser.proxy.NetworkPath.update] 가
     * 중계를 다시 세우지 않는다 — 앞 판은 '저장' 만 눌러도 길을 다시 세워 열린 쪽을 모두 다시 부르고 받던 HLS·DASH 를 끊었다.
     */
    private fun apply(next: ProxySettings) {
        val old = app.network.settings
        val message = when {
            !next.enabled -> R.string.settings_saved_off
            next.sameRoute(old) && next.exitCheckUrl == old.exitCheckUrl -> R.string.settings_unchanged
            else -> R.string.settings_saved_checking
        }
        app.network.update(next) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_clear_question)
            .setPositiveButton(R.string.delete) { _, _ ->
                app.library.clearHistory { Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear_data)
            .setMessage(R.string.settings_clear_question)
            .setPositiveButton(R.string.settings_clear) { _, _ -> clearData() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearData() {
        app.sites.clear()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
        WebView(this).apply {
            clearCache(true)
            destroy()
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            WebStorageCompat.deleteBrowsingData(WebStorage.getInstance()) { done() }
        } else {
            WebStorage.getInstance().deleteAllData()
            done()
        }
    }

    private fun done() {
        Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
    }

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

    /**
     * 이름이 위에 붙은 칸. 이름을 칸에 잇는다(`labelFor`) — 화면 낭독기가 칸을 읽을 때 '호스트' 처럼 이름을 함께 읽는다(앞 판은 이름 없이
     * 'edit box' 로만 읽혔다).
     */
    private fun field(column: LinearLayout, label: Int, type: Int, value: String, multiLine: Boolean = false): EditText {
        val edit = EditText(this).apply {
            id = View.generateViewId()
            inputType = type
            setText(value)
            isSingleLine = !multiLine
            if (multiLine) {
                minLines = 2
                gravity = Gravity.TOP or Gravity.START
            }
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

    /** 팝업을 허용한 사이트 — 누르면 다시 막을지 묻는다(허용은 브라우저 화면의 '팝업을 막았습니다' 알림에서 한다). */
    private fun renderPopupSites(list: LinearLayout) {
        list.removeAllViews()
        val sites = app.popups.sites()
        if (sites.isEmpty()) {
            list.addView(TextView(this).apply {
                text = getString(R.string.settings_popups_none)
                textSize = 13f
                alpha = 0.75f
                setPadding(Ui.dp(context, 20), Ui.dp(context, 2), Ui.dp(context, 20), Ui.dp(context, 6))
            })
            return
        }
        for (site in sites) {
            list.addView(TextView(this).apply {
                text = site
                textSize = 15f
                minHeight = Ui.dp(context, 48)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0)
                setOnClickListener {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setMessage(getString(R.string.settings_popups_remove, site))
                        .setPositiveButton(R.string.delete) { _, _ ->
                            app.popups.setAllowed(site, false)
                            renderPopupSites(list)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            })
        }
    }

    private fun padded(view: View): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
        }
        box.addView(view, Ui.matchWidth())
        return box
    }
}
