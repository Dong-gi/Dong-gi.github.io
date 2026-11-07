package io.github.donggi.iroirobrowser.proxy

import android.annotation.SuppressLint
import android.content.Context
import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.LiveTabs
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.core.Retention

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

    /**
     * 전체 화면 — 상태 표시줄과 막대를 숨기고 페이지가 화면을 채운다(화면 맨 위에서 아래로 밀면 함께 나타난다). 기본 켬(사용자 결정).
     * 프록시 설정처럼 곧바로 쓴다(commit) — 바꾸자마자 앱이 끝나도 남게.
     */
    var fullscreen: Boolean
        get() = prefs.getBoolean(K_FULLSCREEN, true)
        @SuppressLint("ApplySharedPref") // 일부러 commit — 위 주석
        set(value) {
            prefs.edit().putBoolean(K_FULLSCREEN, value).commit()
        }

    /**
     * 항상 확대 허용 — 사이트가 뷰포트로 막아 둔 두 손가락 확대를 푼다(`PageScripts.FORCE_ZOOM`). 기본 켬(사용자 결정).
     * 전체 화면처럼 곧바로 쓴다.
     */
    var forceZoom: Boolean
        get() = prefs.getBoolean(K_FORCE_ZOOM, true)
        @SuppressLint("ApplySharedPref") // 일부러 commit — 위 주석
        set(value) {
            prefs.edit().putBoolean(K_FORCE_ZOOM, value).commit()
        }

    /**
     * 살아 있는 탭의 수 — 넘으면 가장 오래 안 본 탭을 접는다(WebView 를 없애고 뒤로·앞으로 기록만 메모리에 — 고르면 되살린다). 기본 5.
     * 저장값은 [LiveTabs.clamp] 로 범위 안에 맞춘다.
     */
    var liveTabs: Int
        get() = LiveTabs.clamp(prefs.getInt(K_LIVE_TABS, LiveTabs.DEFAULT))
        set(value) = prefs.edit().putInt(K_LIVE_TABS, LiveTabs.clamp(value)).apply()

    /** 방문 기록 보관 기간(일, 0 = 기간 없음 — 최근 1만 곳까지). 고를 수 있는 값([Retention.HISTORY_DAYS]) 밖이면 기간 없음. */
    var historyDays: Int
        get() = prefs.getInt(K_HISTORY_DAYS, 0).takeIf { it in Retention.HISTORY_DAYS } ?: 0
        set(value) = prefs.edit().putInt(K_HISTORY_DAYS, value.takeIf { it in Retention.HISTORY_DAYS } ?: 0).apply()

    /** 캐시 비우기 주기(일, 0 = 하지 않음). 처음 켜면 비운 때를 지금으로 적는다 — 켜는 순간부터 센다([Retention.cacheDue]). */
    var cacheDays: Int
        get() = prefs.getInt(K_CACHE_DAYS, 0).takeIf { it in Retention.CACHE_DAYS } ?: 0
        set(value) {
            val days = value.takeIf { it in Retention.CACHE_DAYS } ?: 0
            val edit = prefs.edit().putInt(K_CACHE_DAYS, days)
            if (days > 0 && prefs.getLong(K_CACHE_CLEARED_AT, 0L) <= 0L) edit.putLong(K_CACHE_CLEARED_AT, System.currentTimeMillis())
            edit.apply()
        }

    /** 캐시를 마지막으로 비운 때(밀리초, 0 = 적은 적 없음) — 정한 날마다 비울 때와 '쿠키·사이트 데이터·캐시 지우기' 가 적는다. */
    var cacheClearedAt: Long
        get() = prefs.getLong(K_CACHE_CLEARED_AT, 0L)
        set(value) = prefs.edit().putLong(K_CACHE_CLEARED_AT, value).apply()

    private fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val K_ENABLED = "proxy_enabled"
        const val K_LIVE_TABS = "live_tabs"
        const val K_HISTORY_DAYS = "history_days"
        const val K_CACHE_DAYS = "cache_days"
        const val K_CACHE_CLEARED_AT = "cache_cleared_at"
        const val K_HOST = "proxy_host"
        const val K_PORT = "proxy_port"
        const val K_USER = "proxy_user"
        const val K_SECRET = "proxy_secret"
        const val K_BYPASS_HOSTS = "bypass_hosts"
        const val K_BYPASS_CIDRS = "bypass_cidrs"
        const val K_BYPASS_PRIVATE = "bypass_private"
        const val K_BYPASS_SIMPLE = "bypass_simple"
        const val K_EXIT_URL = "exit_url"
        const val K_FULLSCREEN = "fullscreen"
        const val K_FORCE_ZOOM = "force_zoom"
    }
}
