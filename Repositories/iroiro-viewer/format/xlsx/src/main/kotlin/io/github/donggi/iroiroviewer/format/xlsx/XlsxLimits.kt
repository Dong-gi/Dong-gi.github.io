package io.github.donggi.iroiroviewer.format.xlsx

/**
 * 통합 문서 변환의 상한.
 *
 * **한곳에 모은다.** 저장소 규칙은 방어 상한을 `core:safety` 에 두라고 하는데(`PdfLimits`·
 * `ComicLimits`), 12단계는 넷이 나눠 쓰느라 공용 모듈을 고칠 수 없었다. 옮기기 쉽게 이 파일
 * 하나에만 적는다 — 옮길 때는 이름만 바꾸면 된다.
 *
 * 값은 **화면이 받을 수 있는 양**에서 정했다. 시트 하나가 WebView 한 쪽이고 HTML 은 8 Mi자에서
 * 멈춘다(`HtmlWriter`). 셀 20만 개가 대략 그 근처다.
 */
internal object XlsxLimits {
    /** 공유 문자열 개수. */
    const val MAX_SHARED_STRINGS = 2_000_000

    /** 공유 문자열 글자 수 합계. 넘으면 뒤의 것은 빈 칸으로 그린다. */
    const val MAX_SHARED_STRING_CHARS = 16L * 1024 * 1024

    /** 셀 하나의 글자 수 — Excel 자체의 한도가 32,767 자다. */
    const val MAX_CELL_CHARS = 32_767

    /**
     * 시트 하나에서 그리는 행(빈 줄 포함).
     *
     * **값을 정한 것은 우리 변환이 아니라 WebView 의 표 배치다.** 20열 시트를 태블릿 에뮬레이터에서
     * 재면(12단계) 1,000행이 4.6초, 2,000행이 10초, 5,000행(칸 10만)이 45초 걸려야 배치가 끝났다 —
     * 변환 자체는 5,000행에서도 JVM 으로 1초 안쪽이다. 폰에서 읽는 표로는 2,000행이면 넉넉하고,
     * 넘으면 시트 이름과 함께 '줄였다' 를 알린다.
     */
    const val MAX_RENDERED_ROWS = 2_000

    /** 시트 하나에서 그리는 열(보이는 열). */
    const val MAX_RENDERED_COLUMNS = 256

    /** 시트 하나에서 그리는 칸(빈 칸 포함). 넓은 시트가 행 상한 안에서 같은 배치 시간에 닿지 않게 — 위 문단. */
    const val MAX_RENDERED_CELLS = 50_000

    /** 빈 행이 이보다 많이 이어지면 한 줄(`tr.gap`)로 접는다. */
    const val GAP_ROWS = 50

    /** 병합 범위 개수. 넘는 것은 병합하지 않고 칸마다 그린다(내용은 잃지 않는다). */
    const val MAX_MERGES = 20_000

    /** 병합 겹침 검사에 쓰는 행 단위 작업량. 악의적인 병합 목록이 시간을 태우지 못하게 한다. */
    const val MAX_MERGE_WORK = 5_000_000

    /** 셀 사이 연결(`hyperlinks`) 개수. */
    const val MAX_LINKS = 10_000

    /** 시트(부분) 개수. */
    const val MAX_SHEETS = 1_024

    /** `cols` 의 범위 항목 개수. */
    const val MAX_COL_SPECS = 2_048

    /** 서식 표 하나(글꼴·채우기·테두리·셀 서식)의 항목 개수. Excel 의 셀 서식 한도가 64,000 이다. */
    const val MAX_STYLE_ENTRIES = 65_536

    /** 사용자 표시 형식 개수. */
    const val MAX_NUM_FMTS = 4_096

    /** 시트 하나가 관계로 거는 그림판·메모 부분 수(같은 부분은 한 번으로 센 뒤). 명세상 하나씩이다. */
    const val MAX_SHEET_ANNEXES = 4

    /** 그림판(drawing) 하나의 개체 수. */
    const val MAX_DRAWING_ITEMS = 1_000

    /** 그림판의 묶음(`grpSp`) 중첩. */
    const val MAX_GROUP_DEPTH = 16

    /**
     * 그림의 대체 글자(`descr`) 글자 수. 속성 값은 길이 상한이 없고, 이스케이프하면 `"` 한 자가 여섯 자가
     * 된다 — 자르지 않으면 본문이 위생기의 입력 상한을 넘어 부분이 통째로 던진다.
     */
    const val MAX_ALT_CHARS = 1_000

    /** 도형 하나의 글자 수. */
    const val MAX_SHAPE_CHARS = 64 * 1024

    /** 메모를 셀 때 보는 개수. 그 위는 더 세지 않는다. */
    const val MAX_COMMENTS_COUNTED = 100_000

    /** 행 높이·열 너비의 상한(pt). 터무니없는 값이 화면을 수 km 로 늘리지 못하게 한다. */
    const val MAX_ROW_HEIGHT_PT = 600.0
    const val MAX_COL_WIDTH_PT = 1_500.0
}
