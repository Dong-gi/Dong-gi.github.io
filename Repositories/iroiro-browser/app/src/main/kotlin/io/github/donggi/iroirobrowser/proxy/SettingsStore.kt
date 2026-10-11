package io.github.donggi.iroirobrowser.proxy

import android.annotation.SuppressLint
import android.content.Context
import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.core.SearchEngine

/**
 * 설정 파일 하나(SharedPreferences `web`). 비밀번호만 [SecretBox] 로 잠가 둔다 — 나머지(호스트·포트·사용자·예외)는 평문이다(앱의 사적
 * 저장소, 백업·이전에서 빠진다).
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("web", Context.MODE_PRIVATE)

    fun load(): ProxySettings {
        val d = ProxySettings.DEFAULT
        val bypass = Bypass(
            hosts = prefs.getString(K_BYPASS_HOSTS, null)?.let(::lines) ?: d.bypass.hosts,
            cidrs = prefs.getString(K_BYPASS_CIDRS, null)?.let(::lines) ?: d.bypass.cidrs,
            privateNetworks = prefs.getBoolean(K_BYPASS_PRIVATE, d.bypass.privateNetworks),
            simpleHostnames = prefs.getBoolean(K_BYPASS_SIMPLE, d.bypass.simpleHostnames),
        )
        return ProxySettings(
            enabled = prefs.getBoolean(K_ENABLED, d.enabled),
            host = prefs.getString(K_HOST, d.host) ?: d.host,
            port = prefs.getInt(K_PORT, d.port),
            user = prefs.getString(K_USER, d.user) ?: d.user,
            password = prefs.getString(K_SECRET, null)?.let(SecretBox::open) ?: "",
            bypass = bypass,
            exitCheckUrl = prefs.getString(K_EXIT_URL, d.exitCheckUrl) ?: d.exitCheckUrl,
        )
    }

    @SuppressLint("ApplySharedPref") // 일부러 commit — 아래 주석
    fun save(s: ProxySettings) {
        prefs.edit()
            .putBoolean(K_ENABLED, s.enabled)
            .putString(K_HOST, s.host)
            .putInt(K_PORT, s.port)
            .putString(K_USER, s.user)
            .putString(K_SECRET, if (s.password.isEmpty()) null else SecretBox.seal(s.password))
            .putString(K_BYPASS_HOSTS, s.bypass.hosts.joinToString("\n"))
            .putString(K_BYPASS_CIDRS, s.bypass.cidrs.joinToString("\n"))
            .putBoolean(K_BYPASS_PRIVATE, s.bypass.privateNetworks)
            .putBoolean(K_BYPASS_SIMPLE, s.bypass.simpleHostnames)
            .putString(K_EXIT_URL, s.exitCheckUrl)
            // 바로 쓴다(apply 는 나중에 쓴다) — 저장하자마자 앱이 죽어도(사용자가 지움·시스템이 끝냄) 바꾼 길이 남게.
            .commit()
    }

    var searchEngine: SearchEngine
        get() = SearchEngine.byId(prefs.getString(K_SEARCH, null))
        set(value) = prefs.edit().putString(K_SEARCH, value.id).apply()

    /**
     * 전체 화면 — 상태 표시줄과 막대를 숨기고 페이지가 화면을 채운다(화면 맨 위에서 아래로 밀면 함께 나타난다). 기본 켬(사용자 요청,
     * 2026-10-08). 프록시 설정처럼 곧바로 쓴다(commit) — 바꾸자마자 앱이 끝나도 남게.
     */
    var fullscreen: Boolean
        get() = prefs.getBoolean(K_FULLSCREEN, true)
        @SuppressLint("ApplySharedPref") // 일부러 commit — 위 주석
        set(value) {
            prefs.edit().putBoolean(K_FULLSCREEN, value).commit()
        }

    /**
     * 항상 확대 허용 — 사이트가 뷰포트로 막아 둔 두 손가락 확대를 푼다(`PageScripts.FORCE_ZOOM`). 기본 켬(사용자 요청, 2026-10-09 — '사이트
     * 설정과 무관하게 확대하여 볼 수 있게'). 전체 화면처럼 곧바로 쓴다.
     */
    var forceZoom: Boolean
        get() = prefs.getBoolean(K_FORCE_ZOOM, true)
        @SuppressLint("ApplySharedPref") // 일부러 commit — 위 주석
        set(value) {
            prefs.edit().putBoolean(K_FORCE_ZOOM, value).commit()
        }

    /**
     * 토렌트를 Wi-Fi(요금제가 아닌 망)에서만 받는다 — 사용자 결정(2026-10-09): 설정은 두되 **기본 끔**. 켜면 엔진의 망 조건 훅이 거짓이 되어 토렌트가
     * '망 조건 기다림' 으로 멈춘다. 곧바로 쓴다(commit) — 바꾸자마자 앱이 끝나도 남게.
     */
    var torrentWifiOnly: Boolean
        get() = prefs.getBoolean(K_TORRENT_WIFI_ONLY, false)
        @SuppressLint("ApplySharedPref") // 일부러 commit — 위 주석
        set(value) {
            prefs.edit().putBoolean(K_TORRENT_WIFI_ONLY, value).commit()
        }

    /**
     * 토렌트를 처음 쓸 때의 안내('프록시를 거치지 않고 이 기기의 IP 로 받습니다 · 저작권 책임' — 사용자 결정 ㉓)를 보였는가. 한 번만 보인다.
     * 곧바로 쓴다 — 안내를 보고 받기를 누른 직후 앱이 죽어도 다시 묻지 않게.
     */
    var torrentNoticeSeen: Boolean
        get() = prefs.getBoolean(K_TORRENT_NOTICE, false)
        @SuppressLint("ApplySharedPref") // 일부러 commit — 위 주석
        set(value) {
            prefs.edit().putBoolean(K_TORRENT_NOTICE, value).commit()
        }

    private fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val K_ENABLED = "proxy_enabled"
        const val K_HOST = "proxy_host"
        const val K_PORT = "proxy_port"
        const val K_USER = "proxy_user"
        const val K_SECRET = "proxy_secret"
        const val K_BYPASS_HOSTS = "bypass_hosts"
        const val K_BYPASS_CIDRS = "bypass_cidrs"
        const val K_BYPASS_PRIVATE = "bypass_private"
        const val K_BYPASS_SIMPLE = "bypass_simple"
        const val K_EXIT_URL = "exit_url"
        const val K_SEARCH = "search_engine"
        const val K_FULLSCREEN = "fullscreen"
        const val K_FORCE_ZOOM = "force_zoom"
        const val K_TORRENT_WIFI_ONLY = "torrent_wifi_only"
        const val K_TORRENT_NOTICE = "torrent_notice_seen"
    }
}
