package io.github.donggi.iroiroviewer.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 재생기가 여는 스킴 — **기기 안의 것만.** media3 의 기본 소스가 여는 네트워크 갈래를 이것이 막는다(`INTERNET` 이 없어 OS 도 막는다 —
 * 겹쳐 둔다).
 */
class LocalOnlyDataSourceTest {

    @Test
    fun `파일과 콘텐츠와 스킴 없는 경로는 연다`() {
        assertTrue(LocalOnlyDataSource.allows("file"))
        assertTrue(LocalOnlyDataSource.allows("FILE"))
        assertTrue(LocalOnlyDataSource.allows("content"))
        assertTrue(LocalOnlyDataSource.allows(null))
    }

    @Test
    fun `네트워크와 그 밖의 스킴은 열지 않는다`() {
        for (scheme in listOf("http", "https", "HTTPS", "udp", "rtmp", "rtsp", "data", "asset", "ftp", "sftp", "")) {
            assertFalse(LocalOnlyDataSource.allows(scheme), scheme)
        }
    }
}
