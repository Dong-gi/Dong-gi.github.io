package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ShareHelper.isUnderRoots] — 다른 앱에 넘기는 모든 URI(공유·다른 앱으로 열기)가 지나는 관문. 정규화(링크 따라가기)는
 * 파일시스템의 일이라 기기에서 보고, 여기서는 정규화한 경로를 루트와 견주는 규칙만 박는다.
 */
class ShareRootsTest {

    private val cache = "/data/user/0/io.github.donggi.iroiroviewer/cache/share/"
    private val roots = listOf("/storage/", cache)

    @Test
    fun `저장 볼륨과 공유 캐시 아래는 넘긴다`() {
        assertTrue(ShareHelper.isUnderRoots("/storage/emulated/0/Download/app.apk", roots))
        // SD 카드 볼륨. 공백·한글이 든 이름은 URI 를 만들 때 FileProvider 가 인코딩한다.
        assertTrue(ShareHelper.isUnderRoots("/storage/1A2B-3C4D/한글 폴더/파일 이름.apk", roots))
        assertTrue(ShareHelper.isUnderRoots(cache + "clean.jpg", roots))
    }

    @Test
    fun `이웃 경로와 루트 자체와 앱 내부는 넘기지 않는다`() {
        assertFalse(ShareHelper.isUnderRoots("/storagex/emulated/0/a.apk", roots))
        assertFalse(ShareHelper.isUnderRoots("/storage", roots))
        assertFalse(ShareHelper.isUnderRoots("/data/user/0/io.github.donggi.iroiroviewer/databases/iro.db", roots))
        assertFalse(ShareHelper.isUnderRoots("/data/user/0/io.github.donggi.iroiroviewer/cache/sharex/a", roots))
    }

    @Test
    fun `빗금으로 끝나지 않는 루트는 아무것도 허락하지 않는다`() {
        // 누가 루트를 `/storage` 로 줄여 적어도 `/storagex` 가 새어 나가지 않는다.
        assertFalse(ShareHelper.isUnderRoots("/storagex/a", listOf("/storage")))
        assertFalse(ShareHelper.isUnderRoots("/storage/emulated/0/a", listOf("/storage")))
    }
}
