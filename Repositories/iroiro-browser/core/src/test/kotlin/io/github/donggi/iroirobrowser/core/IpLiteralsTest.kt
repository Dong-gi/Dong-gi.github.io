package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IpLiteralsTest {

    @Test
    fun ipv4() {
        listOf("0.0.0.0", "10.0.2.2", "255.255.255.255", "127.0.0.1").forEach { assertTrue(IpLiterals.isIpv4(it), it) }
        listOf("256.1.1.1", "1.2.3", "1.2.3.4.5", "a.b.c.d", "1..2.3", "", "1.2.3.04444").forEach { assertFalse(IpLiterals.isIpv4(it), it) }
    }

    @Test
    fun ipv6() {
        listOf(
            "::1", "::", "fe80::1", "2001:db8::8a2e:370:7334", "2001:0db8:0000:0000:0000:ff00:0042:8329", "fc00::",
            "::ffff:192.168.0.1", "1:2:3:4:5:6:1.2.3.4", "1:2:3:4:5:6:7:8", "1::8",
        ).forEach { assertTrue(IpLiterals.isIpv6(it), it) }
        listOf(
            "1:2:3:4:5:6:7:8:9", "1::2::3", "12345::", "g::1", ":::", "1:2:3:4:5:6:7", "fe80::1%eth0", "::ffff:999.1.1.1", "",
            "1:2:3:4:5:6:7:1.2.3.4",
        ).forEach { assertFalse(IpLiterals.isIpv6(it), it) }
    }

    @Test
    fun 크로미움이_같게_읽는_IPv4_만_바이트로() {
        assertContentEquals(byteArrayOf(10, 0, 2, 2), IpLiterals.ipv4Bytes("10.0.2.2"))
        assertContentEquals(byteArrayOf(-1, -1, -1, -1), IpLiterals.ipv4Bytes("255.255.255.255"))
        assertContentEquals(byteArrayOf(0, 0, 0, 0), IpLiterals.ipv4Bytes("0.0.0.0"))
        // 앞의 0(크로미움은 8진수), 짧은 꼴, 16진, 넘침, 유니코드 숫자, 끝의 점 — 모두 null.
        listOf("010.0.0.1", "10.0.0.01", "00.0.0.0", "10.1", "0x0a.0.0.1", "256.0.0.1", "1.2.3.4.5", "١.2.3.4", "1.2.3.4.", "", "1..2.3")
            .forEach { assertNull(IpLiterals.ipv4Bytes(it), it) }
    }

    @Test
    fun IPv6_를_바이트로() {
        assertContentEquals(ByteArray(15) + 1, IpLiterals.ipv6Bytes("::1"))
        assertContentEquals(ByteArray(16), IpLiterals.ipv6Bytes("::"))
        assertContentEquals(IpLiterals.ipv6Bytes("fe80::1"), IpLiterals.ipv6Bytes("FE80:0:0:0:0:0:0:1"))
        assertContentEquals(IpLiterals.ipv6Bytes("::ffff:a00:1"), IpLiterals.ipv6Bytes("::ffff:10.0.0.1"))
        assertContentEquals(
            byteArrayOf(0x20, 0x01, 0x0d, -0x48, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01),
            IpLiterals.ipv6Bytes("2001:db8::1"),
        )
        assertContentEquals(IpLiterals.ipv6Bytes("1:2:3:4:5:6:7:0"), IpLiterals.ipv6Bytes("1:2:3:4:5:6:7::"))
        // 끝의 IPv4 에 앞의 0, 영역 표시, 유니코드 숫자, 괄호 — null.
        listOf("::ffff:010.0.0.1", "fe80::1%eth0", "fe80::١", "[::1]", "1::2::3", "12345::").forEach { assertNull(IpLiterals.ipv6Bytes(it), it) }
    }

    @Test
    fun 크로미움이_IPv4_로_읽으려_드는_호스트() {
        listOf("10.0.0.1", "127.1", "2130706433", "0x7f000001", "0X7F.1", "foo.123", "a.0x", "08", "1.2.3.4.").forEach { assertTrue(IpLiterals.endsInNumber(it), it) }
        listOf("example.com", "nas", "1e5", "0xg", "box.123abc", "a.0x1g", "").forEach { assertFalse(IpLiterals.endsInNumber(it), it) }
    }
}
