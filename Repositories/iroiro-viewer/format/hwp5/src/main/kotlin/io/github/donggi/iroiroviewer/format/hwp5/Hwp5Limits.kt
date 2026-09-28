package io.github.donggi.iroiroviewer.format.hwp5

/**
 * HWP 5.0 변환의 구조 상한. **한곳에 모은다**(CLAUDE.md '안전' 의 단서 — 그 포맷만 아는 상한은 그 포맷
 * 곁에 둔다). 여러 포맷이 함께 쓰는 예산(엔트리 수·평문 한 벌의 `DecryptLimits`)만 `core:safety` 에 있다.
 *
 * 값은 두 가지에서 정했다. **실물이 쓰는 양**(13단계 표본 — 가장 큰 구역이 풀어서 2.4 MB, 레코드 8만 개,
 * 문단 16,000개, 표 하나의 칸 1,200개)보다 넉넉하게, 그리고 **화면이 받을 수 있는 양**(부분 하나의 HTML 은
 * 8 Mi자에서 멈춘다 — `HtmlWriter`)을 넘지 않게. 명세가 크기를 적은 곳도 믿지 않는다 — 레코드 머리의
 * 크기·개수 칸은 전부 공격자가 적는 값이다.
 */
internal object Hwp5Limits {

    /** `FileHeader` 스트림에서 읽는 양. 명세는 256바이트다. */
    const val MAX_FILE_HEADER_BYTES = 4096L

    /** 풀어 낸 `DocInfo` 하나. 실물은 200 KB 안쪽이다. */
    const val MAX_DOCINFO_BYTES = 32L * 1024 * 1024

    /**
     * 풀어 낸 구역 하나(`BodyText/SectionN`). 실물에서 가장 큰 것이 2.4 MB 였다. 문서를 여는 동안 구역 하나씩,
     * 그리는 동안 최근 구역 하나를 메모리에 든다 — 폰의 힙에서 한 벌을 감당할 크기로 묶는다.
     */
    const val MAX_SECTION_BYTES = 64L * 1024 * 1024

    /** 구역 수. `DOCUMENT_PROPERTIES` 가 적은 값도, 이름을 세어 찾는 것도 여기서 멈춘다. */
    const val MAX_SECTIONS = 256

    /** `DocInfo` 의 레코드 수. 실물은 수천이다(스타일 2,374개가 가장 많았다). */
    const val MAX_DOCINFO_RECORDS = 500_000

    /** `DocInfo` 의 표 하나(글자 모양·문단 모양·글꼴…)의 항목 수. 참조가 16비트라 그 너머는 가리킬 수 없다. */
    const val MAX_TABLE_ENTRIES = 65_536

    /** 문서가 적는 이름·명령 문자열 하나(글꼴 이름·스타일 이름·필드 명령). */
    const val MAX_STRING_CHARS = 4_096

    // 그림의 대체 글의 상한은 HWPX 변환기와 한 벌이다 — `format:html` 의 `HancomAlt.MAX_CHARS`.

    /** 수식 스크립트 하나. */
    const val MAX_EQUATION_CHARS = 8_192

    /** 문단 하나의 컨트롤 수. 글자 8칸마다 하나가 들어가므로 글자 수로도 묶이지만 따로 막는다. */
    const val MAX_CTRLS_PER_PARAGRAPH = 8_192

    /** 문단 하나의 글자 모양 구간 수. */
    const val MAX_CHAR_RUNS = 65_536

    /**
     * 확장 컨트롤의 짝(`CTRL_HEADER`)을 찾을 때 앞으로 보는 수. 실물은 언제나 바로 다음 것이 짝이다(문단 46,961개 전부).
     * 묶지 않으면 모든 ID 가 어긋나게 만든 문단이 컨트롤 수의 제곱만큼 돈다.
     */
    const val MAX_CTRL_SEARCH = 16

    /** 문단 안에서 겹쳐 열린 필드. 넘는 것은 무시하고 짝이 되는 끝도 무시한다. */
    const val MAX_FIELD_DEPTH = 16

    /** 그리기 개체의 변환 행렬 쌍의 수(실물은 1–2). 채우기 색을 찾을 때 이 수만큼 건너뛴다. */
    const val MAX_SHAPE_MATRICES = 64

    /** 표 안의 표. */
    const val MAX_TABLE_DEPTH = 8

    /** 표 하나의 행. 12단계 docx 와 같다. */
    const val MAX_ROWS = 5_000

    /** 표 하나의 열. 한글의 표는 열이 수백을 넘지 않는다. */
    const val MAX_COLS = 1_024

    /** 표 하나의 칸. 12단계 docx 와 같다. */
    const val MAX_CELLS = 100_000

    /** 겹침 검사에 격자를 잡는 칸 수의 상한. 넘으면 검사를 건너뛴다(칸은 그대로 그린다). */
    const val MAX_GRID_SLOTS = 4_000_000

    /**
     * 걷기 하나(구역 하나의 훑기·조각 하나의 그리기)가 잡는 격자 칸의 합. 실물에서 가장 큰 문서(K01, 표 126개)가 수만이다 —
     * 넘으면 뒤의 표는 겹침 검사를 건너뛴다. 격자를 잡는 값이 문서가 적는 개수에 비례하지 않게 한다.
     */
    const val MAX_GRID_SLOTS_PER_WALK = 64_000_000L

    /** 묶음 개체(`$con`) 안의 묶음. */
    const val MAX_SHAPE_DEPTH = 16

    /** 글상자 안의 글상자. 12단계 docx 와 같다. */
    const val MAX_TEXTBOX_DEPTH = 3

    /**
     * 문단 목록의 겹침(표 칸·글상자·캡션·주석이 서로 안에 든다). 표·글상자 상한이 각각 막지만, 섞어서
     * 쌓는 문서를 위해 합계를 한 번 더 막는다 — 걷기가 재귀라 이 깊이가 곧 스택이다.
     */
    const val MAX_NESTING = 24

    /** 목록 하나(칸·글상자)의 문단 수. */
    const val MAX_LIST_PARAGRAPHS = 100_000

    /** 부분 하나가 모아 그리는 각주·미주. */
    const val MAX_NOTES = 10_000

    /** 잇달아 오는 빈 문단 가운데 그리는 것(12단계 docx 와 같다 — 빈 문단 수천 개로 화면을 채운 문서가 있다). */
    const val MAX_EMPTY_RUN = 3

    /** 목차 줄 수. */
    const val MAX_OUTLINE = 5_000

    /**
     * 책갈피 이름·덧말 하나의 글자. 목차 줄(부분의 이름)은 HWPX 와 함께 쓰는 `HancomTitles.MAX_CHARS` 로 자른다 — 13단계 짝
     * 대조 전에는 이 값이 목차 줄의 상한이었다(값은 같다).
     */
    const val MAX_TITLE_CHARS = 200

    /** 목차·라벨을 모으는 문단 글자. */
    const val MAX_HEADING_CHARS = 300

    /** 문서 안 링크가 가리키는 대상(문단·개체의 인스턴스 번호) 수. */
    const val MAX_LINK_TARGETS = 100_000

    /** 인스턴스 번호 → 문단 표의 항목 수. 문단이 이보다 많은 문서는 뒤쪽 링크가 갈 곳을 잃는다(글자는 남는다). */
    const val MAX_INSTANCE_IDS = 500_000

    /** 책갈피 수. */
    const val MAX_BOOKMARKS = 20_000

    /** `PrvText`(미리 보기 글) 스트림. 명세상 앞부분 몇 KB 다. */
    const val MAX_PREVIEW_BYTES = 4L * 1024 * 1024

    /** 번호 문단의 수준(명세의 7 + 5.1 의 확장 3). */
    const val NUMBERING_LEVELS = 10

    /** 그림의 폭 상한(pt). 12단계 docx 와 같다. */
    const val MAX_IMAGE_PT = 2000.0

    /** 들여쓰기·여백 상한(pt). */
    const val MAX_INDENT_PT = 200.0

    /** 문단 간격 상한(pt). */
    const val MAX_SPACING_PT = 72.0
}
