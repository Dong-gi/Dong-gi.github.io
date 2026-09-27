package io.github.donggi.iroiroviewer.format

/**
 * 이 뷰어가 아는 포맷.
 *
 * **등록소(`app/FormatRegistry.kt`)는 아직 없다.** 1단계 계획이 그것을 예고했고 이 주석도
 * 그렇게 적고 있었지만, 8단계까지 포맷이 하나씩 붙는 동안 각 `feature` 가 자기 포맷 모듈을
 * 직접 부르는 편이 단순해서 만들지 않았다 — 지금 매직으로 컨테이너를 가르는 자리는
 * `format:archive` 의 `Archives.probeContainer` 하나다. **11단계에서 PDF·EPUB opener 가
 * 둘 이상 생기면 그때 만든다.** 그전까지 새 포맷을 더할 때 고치는 곳은 여기와, 그 포맷을
 * 여는 `feature` 하나다.
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
    DOCX("DOCX", FormatCategory.DOCUMENT),
    XLSX("XLSX", FormatCategory.DOCUMENT),
    PPTX("PPTX", FormatCategory.DOCUMENT),
    HWPX("HWPX", FormatCategory.DOCUMENT),
    HWP5("HWP", FormatCategory.DOCUMENT),

    /**
     * 오피스 확장자의 CFB(OLE2) — 이전 형식(`.doc`·`.xls`·`.ppt`)이거나 **암호가 걸린 OOXML** 이다.
     * 속을 봐야 갈리므로 판별기는 이 이름으로 넘기고, 여는이가 CFB 를 읽어 정한다.
     */
    LEGACY_OFFICE("오피스(OLE2)", FormatCategory.DOCUMENT),

    IMAGE("이미지", FormatCategory.IMAGE),
    AUDIO("오디오", FormatCategory.MEDIA),
    VIDEO("비디오", FormatCategory.MEDIA),
    TEXT("텍스트", FormatCategory.TEXT),
    ;

    val isZipContainer: Boolean
        get() = this in ZIP_CONTAINERS

    companion object {
        /** 실체가 ZIP 인 것들. 판별 2단계(엔트리 이름 보기)가 필요한 집합이다. */
        val ZIP_CONTAINERS = setOf(ZIP, CBZ, EPUB, DOCX, XLSX, PPTX, HWPX)
    }
}

enum class FormatCategory { ARCHIVE, COMIC, DOCUMENT, IMAGE, MEDIA, TEXT }
