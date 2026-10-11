package io.github.donggi.iroirobrowser.core

import java.util.Locale

/** 출구 확인 쪽의 답(`키=값` 줄 — cloudflare 의 `/cdn-cgi/trace`)에서 IP 와 나라. */
data class ExitInfo(val ip: String, val country: String?) {
    /** 한국으로 나간다 — 프록시를 거치지 않은 것 같다. */
    val looksDirect: Boolean get() = country == "KR"
}

object ExitTrace {
    /** 출구를 따로 확인하는 제3자 주소(`ip=`·`loc=` 줄을 준다). */
    const val DEFAULT_URL = "https://cloudflare.com/cdn-cgi/trace"

    fun parse(text: String): ExitInfo? {
        val fields = text.lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .toMap()
        val ip = fields["ip"]?.takeIf { it.isNotEmpty() } ?: return null
        val country = fields["loc"]?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        return ExitInfo(ip, country)
    }
}
