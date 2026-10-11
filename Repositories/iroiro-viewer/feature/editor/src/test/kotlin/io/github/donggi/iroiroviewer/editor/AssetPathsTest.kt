package io.github.donggi.iroiroviewer.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssetPathsTest {

    @Test
    fun `빈 경로와 뿌리는 첫 쪽이다`() {
        assertEquals("index.html", AssetPaths.resolve(null, "index.html"))
        assertEquals("index.html", AssetPaths.resolve("", "index.html"))
        assertEquals("index.html", AssetPaths.resolve("/", "index.html"))
    }

    @Test
    fun `엔진 폴더 안의 자리는 그대로 풀린다`() {
        assertEquals("assets/index-zNX2.js", AssetPaths.resolve("/assets/index-zNX2.js", "index.html"))
        assertEquals("fonts/Noto Sans KR.woff2", AssetPaths.resolve("/fonts/Noto%20Sans%20KR.woff2", "index.html"))
        assertEquals("a+b.css", AssetPaths.resolve("/a+b.css", "index.html"))
        assertEquals("글꼴/나눔.ttf", AssetPaths.resolve("/%EA%B8%80%EA%BC%B4/%EB%82%98%EB%88%94.ttf", "index.html"))
    }

    @Test
    fun `묶음 밖으로 나가는 자리는 거절한다`() {
        for (p in listOf("/../x.js", "/a/../../x.js", "/a/./b.js", "/%2e%2e/x.js", "/a/%2E%2E/b", "/a//../b.js", "/dir/", "/a//", "/a\\b.js", "/a%5Cb.js", "/a%00.js")) {
            assertNull(AssetPaths.resolve(p, "index.html"), p)
        }
    }

    @Test
    fun `가운데의 빈 조각은 접는다`() {
        // ONLYOFFICE 의 슬라이드 테마 — `themes/` + `/themes.js`.
        assertEquals("sdkjs/slide/themes/themes.js", AssetPaths.resolve("/sdkjs/slide/themes//themes.js", "host.html"))
        assertEquals("a/b.js", AssetPaths.resolve("//a///b.js", "host.html"))
    }

    @Test
    fun `깨진 퍼센트 인코딩은 거절한다`() {
        for (p in listOf("/a%", "/a%2", "/a%zz.js", "/%C3%28.js", "/%FF.js")) {
            assertNull(AssetPaths.resolve(p, "index.html"), p)
        }
    }

    @Test
    fun `슬래시로 시작하지 않는 경로는 거절한다`() {
        assertNull(AssetPaths.resolve("index.html", "index.html"))
    }
}
