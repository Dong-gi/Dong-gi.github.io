package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.format.text.TextLanguage
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 목록의 종류 표(`core:io` 의 [MimeResolver])와 텍스트 뷰어의 언어 표(`format:text` 의 [TextLanguage])가 어긋나지 않는가.
 *
 * 뷰어가 칠할 줄 아는 파일을 목록이 '기타' 로 보면 누르는 순간 **다른 앱으로 넘어간다** — 14단계 뒤까지 `.cjs`·`.less`·
 * `.properties`·`.gitignore`·`Dockerfile` 이 그랬다. 두 모듈은 서로를 볼 수 없어 둘을 다 보는 여기서 견준다.
 */
class TextNamesConsistencyTest {

    /** 뷰어는 칠할 줄 알지만 목록이 **일부러** 다른 종류로 보는 확장자. */
    private val elsewhere = mapOf(
        // MPEG-TS 와 확장자를 나눠 쓴다. 목록은 영상으로 적고, 누를 때 내용으로 가른다(`ContentSniff`).
        "ts" to FileKind.VIDEO,
        // 캠코더(AVCHD)의 M2TS 와 나눠 쓴다. media3 가 틀지 못해 기타로 적고(누르면 다른 앱), TypeScript 면 누를 때 글로 간다.
        "mts" to FileKind.OTHER,
        // 그림이다 — 그림 뷰어가 잠긴 WebView 로 그린다.
        "svg" to FileKind.IMAGE,
    )

    @Test
    fun `칠하는 확장자는 목록에서도 글이나 코드다`() {
        for (ext in TextLanguage.knownExtensions) {
            val kind = MimeResolver.kindOf("x.$ext", isDirectory = false)
            val expected = elsewhere[ext]
            if (expected != null) {
                assertEquals(expected, kind, ext)
            } else {
                assertTrue(kind == FileKind.TEXT || kind == FileKind.CODE, "$ext → $kind")
            }
        }
    }

    @Test
    fun `이름으로 칠하는 파일도 목록에서 글이나 코드다`() {
        for (name in TextLanguage.knownNames) {
            // 표는 소문자다. 목록은 대소문자를 가리지 않아야 한다(`Makefile`·`Dockerfile`).
            for (shown in listOf(name, name.replaceFirstChar { it.uppercase() })) {
                val kind = MimeResolver.kindOf(shown, isDirectory = false)
                assertTrue(kind == FileKind.TEXT || kind == FileKind.CODE, "$shown → $kind")
            }
        }
    }

    @Test
    fun `확장자 없는 아무 파일은 여전히 기타다`() {
        // 이름 표에 없는 것까지 글로 보지 않는다 — 내용을 열어 보지 않고는 모른다.
        assertEquals(FileKind.OTHER, MimeResolver.kindOf("README", isDirectory = false))
        assertEquals(FileKind.OTHER, MimeResolver.kindOf("LICENSE", isDirectory = false))
        assertEquals(FileKind.OTHER, MimeResolver.kindOf(".hidden", isDirectory = false))
    }
}
