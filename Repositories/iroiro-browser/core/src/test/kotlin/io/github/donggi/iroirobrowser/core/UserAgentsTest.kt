package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserAgentsTest {

    @Test
    fun 폰의_WebView_는_크롬의_줄인_꼴로() {
        val wv = "Mozilla/5.0 (Linux; Android 12; SM-A908N Build/SP1A.210812.016; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/153.0.8010.36 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36",
            UserAgents.generalize(wv),
        )
    }

    @Test
    fun 태블릿은_Mobile_없이() {
        val tablet = "Mozilla/5.0 (Linux; Android 13; SM-X700 Build/TP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/140.0.7339.51 Safari/537.36"
        assertEquals("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
            UserAgents.generalize(tablet))
    }

    @Test
    fun 판을_못_읽으면_null() {
        assertNull(UserAgents.generalize("Mozilla/5.0 (Linux; Android 12) Something/1.0"))
        assertNull(UserAgents.generalize(""))
    }

    @Test
    fun 데스크톱_사이트는_리눅스_크롬의_줄인_꼴로() {
        val wv = "Mozilla/5.0 (Linux; Android 12; SM-A908N Build/SP1A.210812.016; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/153.0.8010.36 Mobile Safari/537.36"
        val desktop = UserAgents.desktop(wv)
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36", desktop)
        // 모바일 꼴과 같이 기기를 가리키는 것이 남지 않는다.
        assertFalse(desktop!!.contains("SM-A908N") || desktop.contains("wv") || desktop.contains("Mobile") || desktop.contains("Android"))
        assertNull(UserAgents.desktop("Mozilla/5.0 (Linux; Android 12) Something/1.0"))
    }

    @Test
    fun WebView_상표() {
        assertTrue(UserAgents.isWebViewBrand("Android WebView"))
        assertFalse(UserAgents.isWebViewBrand("Chromium"))
        assertFalse(UserAgents.isWebViewBrand("Not)A;Brand"))
    }
}
