package io.github.donggi.iroirobrowser.torrent.net

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel

/**
 * 엔진이 바깥으로 나가는 **유일한 문**. 토렌트는 프록시를 거치지 않고 기기에서 바로 나간다(사용자 결정 — 웹 쪽은 그대로 프록시로만 간다).
 * 그래서 이 문은 시스템·JVM 의 어떤 프록시 설정도 타지 않게 짓는다:
 *
 * - 차단 소켓(HTTP 트래커·웹 시드)은 `Socket(Proxy.NO_PROXY)` — 맨 `Socket()` 은 JVM 의 `socksProxyHost`·`ProxySelector` 를 볼 수 있다.
 *   HTTP 도 `HttpURLConnection` 을 쓰지 않고 이 소켓 위에서 엔진이 짓는다([HttpGet]) — 플랫폼 HTTP 는 시스템의 Wi-Fi 프록시를 따른다.
 * - 피어는 `SocketChannel`, UDP(DHT·UDP 트래커)는 `DatagramChannel` — 둘 다 프록시 개념이 없다.
 *
 * 시험은 이것을 가짜로 바꿔 어디에 붙으려 했는지를 센다.
 */
interface Transport {
    /** 비차단 TCP 를 열고 연결을 시작한다(끝은 셀렉터의 OP_CONNECT 로). */
    fun connectTcp(target: InetSocketAddress): SocketChannel

    /** 차단 TCP — HTTP 용. */
    fun connectBlocking(target: InetSocketAddress, timeoutMs: Int): Socket

    /** UDP 채널(비차단으로 셀렉터에 건다). 보내기는 [sendUdp] 로만. */
    fun openUdp(): DatagramChannel

    fun sendUdp(channel: DatagramChannel, data: ByteBuffer, target: InetSocketAddress): Int

    companion object {
        val DIRECT: Transport = object : Transport {
            override fun connectTcp(target: InetSocketAddress): SocketChannel {
                val ch = SocketChannel.open()
                try {
                    ch.configureBlocking(false)
                    ch.setOption(StandardSocketOptions.TCP_NODELAY, true)
                    ch.connect(target)
                } catch (e: Exception) {
                    runCatching { ch.close() }
                    throw e
                }
                return ch
            }

            override fun connectBlocking(target: InetSocketAddress, timeoutMs: Int): Socket {
                val s = Socket(Proxy.NO_PROXY)
                try {
                    s.tcpNoDelay = true
                    s.connect(target, timeoutMs)
                } catch (e: Exception) {
                    runCatching { s.close() }
                    throw e
                }
                return s
            }

            override fun openUdp(): DatagramChannel {
                val ch = DatagramChannel.open()
                try {
                    ch.configureBlocking(false)
                    ch.bind(InetSocketAddress(0))
                } catch (e: Exception) {
                    runCatching { ch.close() }
                    throw e
                }
                return ch
            }

            override fun sendUdp(channel: DatagramChannel, data: ByteBuffer, target: InetSocketAddress): Int = channel.send(data, target)
        }
    }
}

/** 이름 풀이(차단). 엔진은 일꾼 스레드에서만 부른다. */
fun interface Resolver {
    fun resolve(host: String): List<InetAddress>

    companion object {
        val SYSTEM = Resolver { host -> InetAddress.getAllByName(host).toList() }
    }
}

/** 엔진의 시계 — 시험은 손으로 돌리는 시계를 준다. 밀리초. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        /** 단조 시계(벽시계가 바뀌어도 시간 초과가 흔들리지 않게). */
        val SYSTEM = Clock { System.nanoTime() / 1_000_000 }
    }
}
