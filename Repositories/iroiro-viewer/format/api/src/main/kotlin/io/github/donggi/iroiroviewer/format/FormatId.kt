package io.github.donggi.iroiroviewer.format

/**
 * 이 뷰어가 아는 포맷.
 *
 * 무엇을 무엇으로 여는지는 등록소(`app` 의 `FormatRegistry`)가 이 이름으로 가른다 — 판별기(`FormatProbe`)가 답하는 것이 이 값이다.
 * 압축의 형식 이름은 `format:archive` 의 `ArchiveKind` 가 따로 든다(이 목록에 tar·cpio 의 자리가 없다).
 *
 * **오피스·한글 문서(docx·xlsx·pptx·HWPX·HWP)의 자리는 없다** — 그 문서는 편집기(`feature:editor`)의 보기 모드가
 * 쪽을 PC 처럼 그린다.
 *
 * [category] 는 목록 화면이 아이콘과 기본 동작을 고르는 데 쓴다.
 */
enum class FormatId(val label: String, val category: FormatCategory) {
    ZIP("ZIP", FormatCategory.ARCHIVE),
    SEVEN_Z("7z", FormatCategory.ARCHIVE),
    RAR("RAR", FormatCategory.ARCHIVE),

    CBZ("CBZ", FormatCategory.COMIC),
    CB7("CB7", FormatCategory.COMIC),
    CBR("CBR", FormatCategory.COMIC),

    PDF("PDF", FormatCategory.DOCUMENT),
    EPUB("EPUB", FormatCategory.DOCUMENT),

    IMAGE("이미지", FormatCategory.IMAGE),
    AUDIO("오디오", FormatCategory.MEDIA),
    VIDEO("비디오", FormatCategory.MEDIA),
    TEXT("텍스트", FormatCategory.TEXT),
    ;

    val isZipContainer: Boolean
        get() = this in ZIP_CONTAINERS

    companion object {
        /** 실체가 ZIP 인 것들. 판별 2단계(엔트리 이름 보기)가 필요한 집합이다. */
        val ZIP_CONTAINERS = setOf(ZIP, CBZ, EPUB)
    }
}

enum class FormatCategory { ARCHIVE, COMIC, DOCUMENT, IMAGE, MEDIA, TEXT }
