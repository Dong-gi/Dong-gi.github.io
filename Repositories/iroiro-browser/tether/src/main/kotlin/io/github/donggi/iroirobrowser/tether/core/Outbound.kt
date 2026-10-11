package io.github.donggi.iroirobrowser.tether.core

import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * 프록시가 손님 대신 바깥으로 여는 소켓의 **유일한 문**.
 *
 * 손님의 트래픽은 Web 의 프록시를 거치지 않고 이 폰의 회선(시스템의 기본 망)으로 바로 나간다 — 그것이 테더링이다(CLAUDE.md '테더링' 의
 * '프록시 밖으로 바로 나감'). 그래서 JVM 의 어떤 프록시 설정도 타지 않게 짓는다: 맨 `Socket()` 은 연결할 때 `ProxySelector` 를 묻는다
 * (`socksProxyHost` 등). 앱 프로세스의 그런 설정은 손님이 고른 것이 아니고, 브라우저의 `Authenticator.setDefault` 같은 프로세스 전체 설정이
 * 같은 프로세스에 있으면 닿을 수도 있다(테더는 따로 된 프로세스지만 문을 이것 하나로 묶어 둔다). 토렌트 엔진의 `Transport` 와 같은 까닭이다.
 * UDP(`DatagramSocket`)에는 프록시 개념이 없다.
 *
 * 시험은 이것을 바꿔 어디에 붙으려 했는지 센다.
 */
interface Outbound {
    /** 차단 TCP 연결 — 이미 거른 주소 객체로만(이름을 다시 풀지 않는다). */
    fun connect(address: InetAddress, port: Int, timeoutMs: Int): Socket

    /** 원격 쪽 UDP 소켓(와일드카드·임의 포트). */
    fun openUdp(): DatagramSocket

    companion object {
        val DIRECT: Outbound = object : Outbound {
            override fun connect(address: InetAddress, port: Int, timeoutMs: Int): Socket {
                val socket = Socket(Proxy.NO_PROXY)
                try {
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                } catch (e: Exception) {
                    try {
                        socket.close()
                    } catch (_: Exception) {
                    }
                    throw e
                }
                return socket
            }

            override fun openUdp(): DatagramSocket = DatagramSocket()
        }
    }
}
