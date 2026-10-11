package io.github.donggi.iroirobrowser.lab

import android.os.Bundle

/**
 * 실험 하나의 조건 — adb 의 인텐트 extras 로 온다(`tools/lab/run_lab.py`). 시험 프록시의 계정은 그 도구가 만든 **시험용 값**이고,
 * 사용자의 실제 프록시 계정은 이 길로 오지 않는다.
 *
 * 데이터 클래스로 두지 않는다 — 자동 `toString` 이 계정을 로그에 실어 낸다.
 */
class LabParams private constructor(
    /** 실험 기록을 묶는 이름(로그의 첫 낱말). */
    val run: String,
    /** `load`(WebView 로 쪽을 연다) · `relayhttp`(중계를 HttpURLConnection 으로 쓴다) · `info`. */
    val exp: String,
    /** `https`(TLS 로 감싼 프록시를 WebView 에 바로) · `http`(평문 프록시 — 대조군) · `relay`(앱 안 중계) · `direct`. */
    val mode: String,
    val proxyHost: String,
    val proxyPort: Int,
    val httpProxyPort: Int,
    val user: String,
    val pass: String,
    /** 예외 규칙. `<simple>` 은 점 없는 이름(`bypassSimpleHostnames`). */
    val bypass: List<String>,
    /** WebRTC 가드: `0` 없음, `1` 1판, `2` 2판(빈 틀까지). */
    val guard: String,
    /** 가드를 넣는 길: `start`(addDocumentStartJavaScript) · `event`(addJavaScriptOnEvent — 틀·세계 지정, 1.17). */
    val inject: String,
    /** 스크립트가 넣는 dns-prefetch 를 무디게 하는 가드. */
    val dnsGuard: Boolean,
    /** `off` 면 WebSettingsCompat.setSpeculativeLoadingStatus(DISABLED). */
    val speculative: String,
    /** `xrw-empty` 면 Profile.addCustomHeader 로 X-Requested-With 를 빈 값으로 덮어 본다. */
    val headers: String,
    val url: String,
    /** 먼저 열 쪽(프록시 인증을 미리 채우는 실험). */
    val pre: String?,
    /** [pre] 를 화면에 붙이지 않은 두 번째 WebView 로 연다(보이지 않게 인증만 채울 수 있는가). 그 WebView 는 기본 프로필이다. */
    val preHidden: Boolean,
    /** 주 WebView 의 프로필 이름(MULTI_PROFILE). 비면 기본 프로필. */
    val profile: String,
    val preWaitMs: Long,
    /** 쪽을 연 뒤 기다리는 시간 — 그 뒤 `done` 을 적는다. */
    val waitMs: Long,
    /** 프록시의 407 에 계정으로 답하는가(거절하면 무슨 일이 나는지 보는 실험). */
    val answerAuth: Boolean,
) {
    companion object {
        fun from(extras: Bundle): LabParams = LabParams(
            run = extras.getString("run") ?: "run",
            exp = extras.getString("exp") ?: "info",
            mode = extras.getString("mode") ?: "direct",
            proxyHost = extras.getString("phost") ?: "",
            proxyPort = extras.getString("pport")?.toIntOrNull() ?: 0,
            httpProxyPort = extras.getString("hport")?.toIntOrNull() ?: 0,
            user = extras.getString("user") ?: "",
            pass = extras.getString("pass") ?: "",
            bypass = extras.getString("bypass")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            guard = extras.getString("guard") ?: "0",
            inject = extras.getString("inject") ?: "start",
            dnsGuard = extras.getString("dnsguard") == "1",
            speculative = extras.getString("speculative") ?: "",
            headers = extras.getString("headers") ?: "",
            url = extras.getString("url") ?: "about:blank",
            pre = extras.getString("pre")?.takeIf { it.isNotEmpty() },
            preHidden = extras.getString("prehidden") == "1",
            profile = extras.getString("profile") ?: "",
            preWaitMs = extras.getString("prewait")?.toLongOrNull() ?: 4_000L,
            waitMs = extras.getString("wait")?.toLongOrNull() ?: 6_000L,
            answerAuth = extras.getString("answer") != "0",
        )
    }
}
