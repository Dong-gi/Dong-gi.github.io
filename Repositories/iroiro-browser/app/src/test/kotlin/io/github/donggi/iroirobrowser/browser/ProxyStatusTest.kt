package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.proxy.NetworkPath.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyStatusTest {

    @Test
    fun 꺼졌거나_주소가_없으면_글을_숨긴다() {
        // 그때는 알림 띠가 말한다 — 막대에는 스위치만.
        assertTrue(ProxyStatus.of(Phase.OFF, null).hidden)
        assertTrue(ProxyStatus.of(Phase.NOT_CONFIGURED, null).hidden)
        assertEquals(R.string.proxy_spoken_off, ProxyStatus.of(Phase.OFF, null).spokenRes)
    }

    @Test
    fun 출구를_확인하면_나라_코드를_초록으로() {
        val ok = ProxyStatus.of(Phase.OK, "US")
        assertFalse(ok.hidden)
        assertEquals("US", ok.labelText)
        assertEquals(0, ok.labelRes)
        assertEquals(ProxyStatus.Tone.GOOD, ok.tone)
        assertEquals(R.string.proxy_spoken_ok, ok.spokenRes)
        // 나라를 모르면 빈 글이 아니라 'OK' 를 — 숨기면 켜졌는지 막대에서 보이지 않는다.
        val unknown = ProxyStatus.of(Phase.OK, null)
        assertEquals(R.string.state_ok, unknown.labelRes)
        assertEquals(R.string.proxy_spoken_ok_unknown, unknown.spokenRes)
    }

    @Test
    fun 뜻이_안_잡히던_글은_말로() {
        // 앞 판의 '계정'·'불가'·'?' — 막대 글만으로 뜻이 안 잡혔다.
        assertEquals(R.string.state_auth, ProxyStatus.of(Phase.AUTH_REJECTED, null).labelRes)
        assertEquals(R.string.state_unsupported, ProxyStatus.of(Phase.UNSUPPORTED, null).labelRes)
        assertEquals(R.string.state_check_failed, ProxyStatus.of(Phase.CHECK_FAILED, null).labelRes)
        assertEquals(ProxyStatus.Tone.WARN, ProxyStatus.of(Phase.CHECK_FAILED, null).tone)
        assertEquals(R.string.state_unreachable, ProxyStatus.of(Phase.UNREACHABLE, null).labelRes)
        assertEquals(ProxyStatus.Tone.BAD, ProxyStatus.of(Phase.UNREACHABLE, null).tone)
    }

    @Test
    fun 한국_출구는_느낌표와_빨강() {
        val direct = ProxyStatus.of(Phase.LOOKS_DIRECT, "KR")
        assertEquals("KR!", direct.labelText)
        assertEquals(ProxyStatus.Tone.BAD, direct.tone)
        assertEquals("KR!", ProxyStatus.of(Phase.LOOKS_DIRECT, null).labelText)
    }

    @Test
    fun 모든_상태에_낭독기의_말이_있다() {
        for (phase in Phase.entries) assertTrue(ProxyStatus.of(phase, "US").spokenRes != 0, phase.name)
    }

    @Test
    fun 나라_이름은_한국어로_모르면_코드_그대로() {
        assertEquals("미국", ProxyStatus.countryName("US"))
        assertEquals("일본", ProxyStatus.countryName("jp"))
        // Cloudflare 의 Tor(T1) — 지역 코드의 꼴이 아니다.
        assertEquals("T1", ProxyStatus.countryName("T1"))
        assertEquals("", ProxyStatus.countryName(null))
        assertEquals("", ProxyStatus.countryName(" "))
    }
}
