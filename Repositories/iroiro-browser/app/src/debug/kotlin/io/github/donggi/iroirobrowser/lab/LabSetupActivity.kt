package io.github.donggi.iroirobrowser.lab

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.sites.SiteDataActivity

/**
 * **디버그 빌드에만 있다.** PC 의 시험 도구가 adb 로 브라우저의 프록시 설정을 넣는다 — 설정 화면을 손으로 채우지 않고 브라우저를 실험실의
 * 시험 프록시에 붙이려고. 넣는 계정은 실험실의 시험 계정이다(사용자의 실제 프록시 계정이 아니다). 화면 없이 넣고 끝난다.
 *
 * extras(모두 문자열): `enabled`(1/0), `host`, `port`, `user`, `pass`, `exit`(출구 확인 주소), `cidrs`(쉼표), `private`(1/0).
 * `open`=`sitedata` 면 설정을 바꾸지 않고 '사이트별 데이터' 화면을 연다(설정 화면은 비밀번호 칸이 있어 시험 도구가 화면을 읽지 않는다).
 * 광고 차단: `adblock`(1/0), `adblock_list`(실험실의 목록 주소 — 들어 있는 목록을 모두 끄고 이것만 켜서 바로 받는다).
 */
class LabSetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val x = intent.extras
        x?.getString("adblock")?.let { WebApp.of(this).adblock.setEnabled(it == "1") }
        x?.getString("adblock_list")?.let { url ->
            val ads = WebApp.of(this).adblock
            ads.lists.filter { it.url != url && it.enabled }.forEach { ads.setListEnabled(it, false) }
            val existing = ads.lists.firstOrNull { it.url == url }
            if (existing == null) ads.addList(url, "실험 목록") else if (!existing.enabled) ads.setListEnabled(existing, true) else ads.updateNow()
        }
        if (x != null && x.keySet().all { it.startsWith("adblock") }) {
            // 광고 차단만 바꿨다 — 길은 그대로.
        } else if (x?.getString("open") == "sitedata") {
            startActivity(Intent(this, SiteDataActivity::class.java))
        } else if (x != null) {
            val network = WebApp.of(this).network
            val old = network.settings
            val next = old.copy(
                enabled = x.getString("enabled")?.let { it == "1" } ?: old.enabled,
                host = x.getString("host") ?: old.host,
                port = x.getString("port")?.toIntOrNull() ?: old.port,
                user = x.getString("user") ?: old.user,
                password = x.getString("pass") ?: old.password,
                exitCheckUrl = x.getString("exit") ?: old.exitCheckUrl,
                bypass = old.bypass.copy(
                    cidrs = x.getString("cidrs")?.let { Bypass.split(it).map(Bypass::normalizeCidr) } ?: old.bypass.cidrs,
                    privateNetworks = x.getString("private")?.let { it == "1" } ?: old.bypass.privateNetworks,
                ),
            )
            network.update(next) { Log.i("IROLAB", "setup applied $next") }
        }
        finish()
    }

    @Suppress("unused")
    private fun describe(s: ProxySettings) = s.toString()
}
