package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.FormatProbe
import io.github.donggi.iroiroviewer.format.ProbeContext

/**
 * 이것이 EPUB 인가. **`format:api` 의 [FormatProbe] 계약을 쓰는 첫 구현이다** —
 * 2단계가 세워 두고 11단계까지 부르는 코드가 하나도 없었다.
 *
 * ## 세 갈래로 묻고, 싼 것부터 본다
 *
 * 1. **고정 자리의 매직.** 명세는 `mimetype` 이 압축되지 않은 **첫 엔트리**여야 한다고
 *    적고, 그러면 파일의 30번째 바이트부터 `mimetypeapplication/epub+zip` 이 그대로
 *    보인다. 앞부분 64바이트만 읽으면 되므로 가장 싸다.
 * 2. **엔트리 이름.** 1이 아니어도(만드는 도구가 그냥 압축해 넣은 경우가 흔하다)
 *    `META-INF/container.xml` 이 있으면 EPUB 이다. ZIP 을 한 번 여는 값이 든다.
 * 3. **확장자.** 위 둘을 못 봤고(엔트리 목록이 없을 수 있다) 확장자가 `epub` 이면 맡는다.
 *    틀렸으면 [EpubOpener] 가 `container.xml 이 없다` 로 정확히 끝낸다.
 *
 * **확장자만으로 단정하지 않는 것이 요점이다.** 목록 화면은 확장자만 보지만
 * (`FileKind`), 실제로 열 때는 안을 본다 — `ProbeContext` 의 주석이 그 경계를 적어 두었다.
 */
object EpubProbe : FormatProbe {

    /** ZIP 의 지역 헤더 뒤, 첫 엔트리 이름이 시작하는 자리. */
    private const val NAME_AT = 30

    private const val STORED_HEAD = "mimetypeapplication/epub+zip"

    override fun probe(context: ProbeContext): FormatId? {
        if (!context.headStartsWith(0x50, 0x4B, 0x03, 0x04)) return null

        val head = context.head
        if (head.size >= NAME_AT + STORED_HEAD.length) {
            val text = String(head, NAME_AT, STORED_HEAD.length, Charsets.US_ASCII)
            if (text == STORED_HEAD) return FormatId.EPUB
        }

        val names = context.zipEntryNames.value
        if (names != null) {
            return if (names.any { it == "META-INF/container.xml" }) FormatId.EPUB else null
        }

        return if (context.extension == "epub") FormatId.EPUB else null
    }
}
