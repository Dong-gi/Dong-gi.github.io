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
 *
 * ## HWPX 를 전자책으로 잡지 않는다
 *
 * HWPX 는 EPUB 처럼 생겼다 — `mimetype`(내용 `application/hwp+zip`)과 `META-INF/container.xml` 을 둘 다 갖는다(CLAUDE.md 함정 표).
 * 그것을 먼저 가리던 HWPX 판별기는 없다(한글 문서는 편집기가 연다).
 * 그래서 여기서 가린다 — **첫 엔트리가 저장된 `mimetype` 이고 그 내용이 OCF 가 아닌 알려진 형식([NOT_OCF] — HWPX·ODF)이면 맡지
 * 않는다**, 이름으로 볼 때는 HWPX 의 꾸러미 파일(`Contents/content.hpf`)이 있으면 맡지 않는다.
 *
 * **저장된 `mimetype` 의 내용 하나로 '아니다' 를 정하지 않는다**. 내용이 EPUB 의 것도 알려진 다른 형식도 아니면(앞에 BOM·공백이
 * 붙었다, 끝에 줄바꿈이 붙었다, 모르는 형식이다) 이름으로 본다 — 예전 판(11단계)도 고정 자리가 맞지 않으면 `container.xml` 로 넘어가 열었다.
 * 10-09 의 판은 내용이 EPUB 이 아니면 곧바로 '아니다' 로 끝내, 그런 EPUB 이 '이 앱이 다루지 않는 문서' 가 됐다.
 */
object EpubProbe : FormatProbe {

    /** ZIP 의 지역 헤더 뒤, 첫 엔트리 이름이 시작하는 자리. */
    private const val NAME_AT = 30

    /** 지역 헤더의 '이름 길이' 자리(2바이트, 리틀 엔디언). `mimetype` 이면 8 이다. */
    private const val NAME_LENGTH_AT = 26

    /** 지역 헤더의 '추가 필드 길이' 자리(2바이트, 리틀 엔디언). 0 이 아니면 이름 바로 뒤에 내용이 오지 않는다. */
    private const val EXTRA_LENGTH_AT = 28

    /** 지역 헤더의 '압축한 크기' 자리(4바이트). STORED 면 내용의 길이다(데이터 기술자를 쓰면 0 — 그때는 앞부분 끝까지 본다). */
    private const val COMPRESSED_SIZE_AT = 18

    private const val MIMETYPE = "mimetype"
    private const val EPUB_MIME = "application/epub+zip"

    /**
     * `mimetype` 을 쓰는 꾸러미 가운데 **OCF 가 아닌 알려진 형식**의 앞머리 — 이것으로 시작하면 EPUB 이 아니라고 정한다. HWPX(와 그 서식·
     * OWPML)는 `container.xml` 까지 가져 이름으로는 가릴 수 없을 수 있고(꾸러미 파일 이름으로 한 번 더 가린다), ODF 는 `opendocument`
     * 앞에서 자른다 — 판별기가 받는 앞부분(보통 64바이트)에 `mimetype` 의 내용은 26바이트까지만 든다.
     */
    private val NOT_OCF = listOf("application/hwp+zip", "application/vnd.oasis.")

    /** HWPX 의 꾸러미 파일(OWPML) — 이것이 있으면 `container.xml` 이 있어도 EPUB 이 아니다. */
    private const val HWPX_PACKAGE = "Contents/content.hpf"

    override fun probe(context: ProbeContext): FormatId? {
        if (!context.headStartsWith(0x50, 0x4B, 0x03, 0x04)) return null

        storedMimetype(context.head)?.let { mime ->
            if (mime.startsWith(EPUB_MIME)) return FormatId.EPUB
            if (NOT_OCF.any { mime.startsWith(it) }) return null
            // 그 밖은 내용만으로 정하지 않는다 — 아래 이름으로 본다.
        }

        val names = context.zipEntryNames.value
        if (names != null) {
            if (names.any { it == HWPX_PACKAGE }) return null
            return if (names.any { it == "META-INF/container.xml" }) FormatId.EPUB else null
        }

        return if (context.extension == "epub") FormatId.EPUB else null
    }

    /**
     * 첫 엔트리가 압축하지 않고(STORED) 추가 필드 없이 저장된 `mimetype` 이면 그 내용(앞부분에 든 만큼, 적힌 길이까지), 아니면 null(그때는
     * 이름으로 본다 — 압축한 `mimetype` 은 내용을 읽을 수 없고, 그런 EPUB 이 흔하다). 적힌 길이가 0 이면(데이터 기술자) 앞부분 끝까지라
     * 다음 머리의 바이트가 섞일 수 있다 — 앞머리로만 견주므로 그것으로 충분하다. ASCII 가 아닌 바이트는 대체 글자가 된다(BOM 따위).
     */
    private fun storedMimetype(head: ByteArray): String? {
        val start = NAME_AT + MIMETYPE.length
        if (head.size <= start) return null
        if (u16(head, NAME_LENGTH_AT) != MIMETYPE.length) return null
        if (String(head, NAME_AT, MIMETYPE.length, Charsets.US_ASCII) != MIMETYPE) return null
        if (u16(head, METHOD_AT) != STORED || u16(head, EXTRA_LENGTH_AT) != 0) return null
        val declared = u32(head, COMPRESSED_SIZE_AT)
        val available = (head.size - start).toLong()
        val length = if (declared in 1..available) declared.toInt() else available.toInt()
        return String(head, start, length, Charsets.US_ASCII)
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

    /** 지역 헤더의 '압축 방식' 자리(2바이트). 0 이 저장(STORED)이다. */
    private const val METHOD_AT = 8
    private const val STORED = 0
}
