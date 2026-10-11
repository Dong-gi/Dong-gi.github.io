package io.github.donggi.iroirobrowser.net

import java.util.Base64

/**
 * 원격 프록시 하나. [tls] 면 프록시까지의 연결을 TLS 로 감싼다 — 사용자의 프록시(Squid `https_port`, Basic 인증)가 그렇다.
 * 평문(`http`)이면 CONNECT 의 목적지와 계정이 망에 그대로 보이므로 실험의 대조군으로만 쓴다.
 *
 * 계정은 만들 때 Basic 머리로 바꿔 두고 원문을 들지 않는다. [toString] 은 계정을 적지 않는다 — 로그·예외에 실려 나가지 않게.
 */
class UpstreamProxy(
    val tls: Boolean,
    val host: String,
    val port: Int,
    user: String,
    password: String,
) {
    internal val authorization: String = basic(user, password)

    override fun toString(): String = "UpstreamProxy(${if (tls) "https" else "http"}://$host:$port)"

    companion object {
        internal fun basic(user: String, password: String): String =
            "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
    }
}
