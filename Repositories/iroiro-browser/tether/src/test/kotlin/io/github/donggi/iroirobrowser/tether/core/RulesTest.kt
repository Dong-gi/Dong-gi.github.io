package io.github.donggi.iroirobrowser.tether.core

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 작은 규칙들 — Range·JSON·경로 검사·이름 부호·패스프레이즈·SSID·스레드 예산. */
class RulesTest {

    @Test
    fun Range_의_세_꼴과_맞출_수_없는_것() {
        assertEquals(ByteRange(0, 499), ByteRange.parse("bytes=0-499", 1000))
        assertEquals(ByteRange(500, 999), ByteRange.parse("bytes=500-", 1000))
        assertEquals(ByteRange(900, 999), ByteRange.parse("bytes=-100", 1000))
        // 꼬리가 전체보다 길면 처음부터.
        assertEquals(ByteRange(0, 9), ByteRange.parse("bytes=-50", 10))
        // 끝이 넘치면 마지막 바이트까지.
        assertEquals(ByteRange(5, 9), ByteRange.parse("bytes=5-500", 10))
        assertEquals(ByteRange(3, 3), ByteRange.parse(" bytes=3-3 ", 10))
        assertNull(ByteRange.parse(null, 10))
        assertNull(ByteRange.parse("bytes=10-", 10)) // 시작이 끝을 넘었다
        assertNull(ByteRange.parse("bytes=5-4", 10))
        assertNull(ByteRange.parse("bytes=0-1,3-4", 10)) // 여러 구간
        assertNull(ByteRange.parse("bytes=-0", 10))
        assertNull(ByteRange.parse("bytes=x-1", 10))
        assertNull(ByteRange.parse("bytes=", 10))
        assertNull(ByteRange.parse("bytes=0-1", 0)) // 빈 파일
    }

    @Test
    fun JSON_글은_따옴표와_역슬래시와_제어_문자를_이스케이프한다() {
        assertEquals("\"a\\\"b\\\\c\"", Json.quote("a\"b\\c"))
        assertEquals("\"\\n\\r\\t\\u0001\\u001f\"", Json.quote("\n\r\t\u0001\u001f"))
        assertEquals("\"한글 이름.txt\"", Json.quote("한글 이름.txt"))
        assertEquals("""{"error":"x"}""", Json.error("x"))
        assertEquals("""{"ok":true,"name":"a.bin","bytes":12}""", Json.uploaded("a.bin", 12))
        assertTrue(Json.folderMissing().contains("\"needsFolder\":true"))
    }

    @Test
    fun 목록은_폴더_먼저_그다음_파일이고_이름은_대소문자를_가리지_않는다() {
        val json = Json.listing(
            "sub",
            "공유",
            listOf(
                SharedEntry("1", "b.txt", false, 3, 10),
                SharedEntry("2", "Zed", true, 0, 0),
                SharedEntry("3", "A.txt", false, 1, 20),
                SharedEntry("4", "alpha", true, 0, 0),
            ),
        )
        val names = Regex("\"name\":\"([^\"]*)\"").findAll(json).map { it.groupValues[1] }.toList()
        assertEquals(listOf("alpha", "Zed", "A.txt", "b.txt"), names)
        assertTrue(json.startsWith("""{"path":"sub","root":"공유","entries":["""))
        assertTrue(json.contains(""""name":"b.txt","dir":false,"size":3,"modified":10"""))
    }

    @Test
    fun 경로_검사는_절대_경로와_점_성분과_빈_성분과_역슬래시와_NUL_을_막는다() {
        assertEquals(emptyList(), SharedPaths.split(""))
        assertEquals(listOf("a", "b c", "한글.txt"), SharedPaths.split("a/b c/한글.txt"))
        for (bad in listOf("/a", "a/../b", "..", ".", "a/./b", "a//b", "a/", "a\\b", "a\u0000b")) {
            assertNull(SharedPaths.split(bad), bad)
        }
        assertEquals(32, SharedPaths.split(List(32) { "d" }.joinToString("/"))?.size)
        assertNull(SharedPaths.split(List(33) { "d" }.joinToString("/")))
    }

    @Test
    fun filename_별표_꼴은_unreserved_만_남기고_UTF_8_바이트를_부호로_바꾼다() {
        assertEquals("%ED%95%9C%20%EA%B8%80.txt", SharedPaths.percentEncode("한 글.txt"))
        assertEquals("a~b-c_d.e09AZ", SharedPaths.percentEncode("a~b-c_d.e09AZ"))
        assertEquals("%2B%25%2F%3F%22", SharedPaths.percentEncode("+%/?\""))
        assertEquals("text/plain", SharedPaths.guessMimeType("README.TXT"))
        assertEquals("video/mp4", SharedPaths.guessMimeType("clip.mp4"))
        assertEquals("application/octet-stream", SharedPaths.guessMimeType("noext"))
        assertEquals("application/octet-stream", SharedPaths.guessMimeType("x.unknown"))
    }

    @Test
    fun 뻔한_패스프레이즈를_막는다() {
        for (weak in listOf("12345678", "PASSWORD", "UsbTether", "abababab", "aaaaaaaaaa", "abcdefgh", "87654321", "23456789", "hgfedcba")) {
            assertTrue(Passphrases.isWeak(weak), weak)
            assertEquals(Passphrases.Problem.WEAK, Passphrases.check("x", weak), weak)
        }
        assertFalse(Passphrases.isWeak("k7Qm2xPa9Ld4Rt"))
        assertNull(Passphrases.check("MyPhone", "k7Qm2xPa9Ld4Rt"))
    }

    @Test
    fun 패스프레이즈는_출력_가능한_ASCII_만_받는다() {
        // WPA2 의 패스프레이즈는 출력 가능한 ASCII 8–63자다 — 예전에는 길이(UTF-16 글자 수)만 보았다.
        for (bad in listOf("한글패스워드입니다", "k7Qm2xPa9Ld4Rté", "k7Qm2xPa\t9Ld4", "k7Qm2xPa9Ld4\u007F", "k7Qm2xPa😀Ld4Rt", "k7Qm2xPa 9Ld4")) {
            assertEquals(Passphrases.Problem.NOT_ASCII, Passphrases.check("x", bad), bad)
            assertEquals(Passphrases.Problem.NOT_ASCII, Passphrases.passphraseProblem(bad), bad)
        }
        // 짧은 한글도 길이보다 ASCII 를 먼저 알린다 — 한 자를 더 친 뒤에야 진짜 까닭을 듣지 않게.
        assertEquals(Passphrases.Problem.NOT_ASCII, Passphrases.check("x", "한글"))
        // 공백과 기호는 출력 가능한 ASCII 다.
        assertNull(Passphrases.check("x", "k7 Qm!2x~Pa{9}Ld"))
        assertNull(Passphrases.check("x", " !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~Zq"))
    }

    @Test
    fun 우리_그룹은_오너이고_이름이_같고_인터페이스가_있을_때만() {
        val ssid = "DIRECT-UT-USBTether"
        assertEquals("p2p-wlan0-0", GroupWatch.ourInterface(formed = true, owner = true, networkName = ssid, interfaceName = "p2p-wlan0-0", ourSsid = ssid))
        // 그룹이 서지 않았다·이 폰이 손님이다·남의 그룹(같은 대역·같은 로컬 망 — 옛 테더 앱 등)·인터페이스 이름이 비었다·우리가 켜지 않았다.
        assertNull(GroupWatch.ourInterface(false, true, ssid, "p2p-wlan0-0", ssid))
        assertNull(GroupWatch.ourInterface(true, false, ssid, "p2p-wlan0-0", ssid))
        assertNull(GroupWatch.ourInterface(true, true, "DIRECT-xy-Other", "p2p-wlan0-0", ssid))
        assertNull(GroupWatch.ourInterface(true, true, ssid, "", ssid))
        assertNull(GroupWatch.ourInterface(true, true, ssid, null, ssid))
        assertNull(GroupWatch.ourInterface(true, true, ssid, "p2p-wlan0-0", null))
        assertTrue(GroupWatch.isOurs(owner = true, networkName = ssid, ourSsid = ssid))
        assertFalse(GroupWatch.isOurs(owner = true, networkName = null, ourSsid = null))
    }

    @Test
    fun 패스프레이즈_길이는_8에서_63() {
        assertEquals(Passphrases.Problem.LENGTH, Passphrases.check("x", "k7Qm2xP"))
        assertNull(Passphrases.check("x", "k7Qm2xPa"))
        assertNull(Passphrases.check("x", "k7Qm2xPa".repeat(7) + "k7Qm2xP"))
        assertEquals(Passphrases.Problem.LENGTH, Passphrases.check("x", "k7Qm2xPa".repeat(8)))
    }

    @Test
    fun SSID_는_DIRECT_와_영숫자_둘로_시작하지_않으면_DIRECT_UT_를_붙인다() {
        assertEquals("DIRECT-UT-MyPhone", Passphrases.normalizeSsid("MyPhone"))
        assertEquals("DIRECT-UT-MyPhone", Passphrases.normalizeSsid("  MyPhone "))
        assertEquals("DIRECT-ab-Phone", Passphrases.normalizeSsid("DIRECT-ab-Phone"))
        assertEquals("DIRECT-UT-DIRECT-a", Passphrases.normalizeSsid("DIRECT-a"))
        assertEquals("DIRECT-UT-DIRECT-!!x", Passphrases.normalizeSsid("DIRECT-!!x"))
        assertEquals("DIRECT-UT-USBTether", Passphrases.normalizeSsid(""))
        assertEquals("DIRECT-UT-USBTether", Passphrases.normalizeSsid("   "))
    }

    @Test
    fun SSID_는_앞머리까지_32바이트_안_한글은_UTF_8_로_센다() {
        val strong = "k7Qm2xPa9Ld4Rt"
        assertNull(Passphrases.check("a".repeat(22), strong)) // 10 + 22 = 32
        assertEquals(Passphrases.Problem.SSID_TOO_LONG, Passphrases.check("a".repeat(23), strong))
        assertNull(Passphrases.check("한".repeat(7), strong)) // 10 + 21
        assertEquals(Passphrases.Problem.SSID_TOO_LONG, Passphrases.check("한".repeat(8), strong)) // 10 + 24
    }

    @Test
    fun 만든_패스프레이즈는_20자이고_헷갈리는_글자가_없고_약하지_않다() {
        val random = SecureRandom()
        val made = (1..200).map { Passphrases.generate(random) }
        for (p in made) {
            assertEquals(20, p.length)
            assertTrue(p.all { it in Passphrases.ALPHABET }, p)
            assertTrue(p.none { it in "0O1lI" }, p)
            assertNull(Passphrases.check("x", p), p)
        }
        assertEquals(200, made.toSet().size)
        assertEquals(57, Passphrases.ALPHABET.length)
        assertNotEquals(Passphrases.generate(), Passphrases.generate())
    }

    @Test
    fun 스레드_예산은_상한을_넘겨_쥐지_않고_돌려주면_다시_쥔다() {
        val budget = ThreadBudget(5)
        assertTrue(budget.tryAcquire(2))
        assertTrue(budget.tryAcquire(3))
        assertFalse(budget.tryAcquire(1))
        assertEquals(5, budget.inUse)
        budget.release(2)
        assertFalse(budget.tryAcquire(3))
        assertTrue(budget.tryAcquire(2))
        budget.release(5)
        assertEquals(0, budget.inUse)
        // SOCKS5 가 혼자 상한까지 차도 예산 안이고(TCP 128 × 2 + UDP 128 × 3 = 640) 그때도 HTTP 터널 64 개가 남는다.
        val socksAlone = (Socks5Server.MAX_SESSIONS - Socks5Server.MAX_UDP_ASSOCIATIONS) * 2 + Socks5Server.MAX_UDP_ASSOCIATIONS * 3
        assertTrue(socksAlone + 64 * 2 <= ThreadBudget.DEFAULT_LIMIT)
        // 셋이 함께 차면 usb-tether 의 어림(256 × 3 + 256 × 2 + 32 = 1,312)이었던 것이 예산에서 멈춘다.
        val oldWorst = Socks5Server.MAX_SESSIONS * 3 + HttpProxyServer.MAX_SESSIONS * 2 + FileServer.MAX_SESSIONS
        assertEquals(1312, oldWorst)
        assertTrue(ThreadBudget.DEFAULT_LIMIT < 1000)
    }
}
