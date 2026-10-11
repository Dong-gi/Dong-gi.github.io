package io.github.donggi.iroiroviewer

import java.io.IOException
import java.security.KeyStoreException
import java.security.ProviderException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 걷은 기능이 남긴 것의 정리(`LegacyCleanup`)는 **실패해도 앱을 죽이지 않는다**. 정리는 날것의 스레드에서 돌아, 새는 예외가
 * 프로세스를 끝내고 앱을 켤 때마다 되풀이된다. AndroidKeyStore 는 선언하지 않은 예외(`ProviderException`·NPE 따위)도 던진다.
 */
class LegacyCleanupTest {

    @Test
    fun `정리가 던지는 예외는 삼키고 다음에 다시 한다`() {
        // 고치기 전에는 GeneralSecurityException·IOException 만 잡았다 — 아래 둘이 정리 스레드를 넘어 앱을 끝냈다.
        assertFalse(LegacyCleanup.quietly("시험") { throw ProviderException("keystore2") })
        assertFalse(LegacyCleanup.quietly("시험") { throw NullPointerException() })
        assertFalse(LegacyCleanup.quietly("시험") { throw IllegalStateException() })
        assertFalse(LegacyCleanup.quietly("시험") { throw KeyStoreException() })
        assertFalse(LegacyCleanup.quietly("시험") { throw IOException() })
        assertTrue(LegacyCleanup.quietly("시험") {})
    }

    @Test
    fun `오류는 삼키지 않는다`() {
        // 메모리 부족 따위는 이 정리의 실패가 아니다.
        assertFailsWith<OutOfMemoryError> { LegacyCleanup.quietly("시험") { throw OutOfMemoryError() } }
    }
}
