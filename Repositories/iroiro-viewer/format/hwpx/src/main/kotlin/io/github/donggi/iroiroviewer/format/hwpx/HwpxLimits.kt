package io.github.donggi.iroiroviewer.format.hwpx

/**
 * HWPX 의 **구조 상한**을 한곳에 모은다(CLAUDE.md '안전' 의 단서 — 포맷만 아는 상한은 그 포맷 곁에 둔다).
 *
 * 여러 포맷이 같이 쓰는 예산(엔트리 수·해제량·XML 깊이·암호를 푼 평문 한 벌)은 `core:safety` 의
 * `ParseLimits`·`DecryptLimits` 가 정한다. 여기 있는 것은 **HWPX 의 모양을 알아야 셀 수 있는 것**뿐이다 —
 * 매니페스트 항목 수, 구역 수, 표의 행·칸, 글상자·각주의 겹침.
 *
 * 값은 정상 문서가 닿지 않을 만큼 넉넉히, 적대적 문서가 기억과 시간을 다 쓰지 못할 만큼 좁게 잡았다.
 * 실측 기준: 표본 중 가장 큰 구역(K25, 13 MB)이 표 126개·칸 15,388개, 최상위 문단 801개다.
 */
internal object HwpxLimits {

    /** `content.hpf` 매니페스트·스파인 항목 수. 그림이 수천 장인 문서도 이 안에 든다. */
    const val MAX_MANIFEST_ITEMS = 50_000

    /** 구역 수. 한글은 구역을 수백 개 만들지 않는다. */
    const val MAX_SECTIONS = 2_000

    /** `header.xml` 의 목록 하나(글자 모양·문단 모양·테두리…)의 항목 수. 표본의 최대가 수천이다. */
    const val MAX_HEADER_ITEMS = 200_000

    /** 문단 번호의 수준 수(1..10). 한글 2010 이전은 7, 뒤로 10. */
    const val NUMBER_LEVELS = 10

    /** 표 안의 표의 깊이. docx 와 같다. */
    const val MAX_TABLE_DEPTH = 8

    /** 표 하나의 행·칸. 넘으면 뒤를 자르고 알린다(docx 와 같은 값). */
    const val MAX_ROWS = 5_000
    const val MAX_CELLS = 100_000

    /** 칸 하나의 합치기 폭·높이, 표의 열 수. HTML 의 `colspan` 상한(1000)과 같다. */
    const val MAX_SPAN = 1_000

    /** 훑기가 기억해 두는 표 칸 정보의 총수(문서 전체). 넘으면 뒤의 표는 합치지 않고 그린다. */
    const val MAX_STORED_SPAN_CELLS = 2_000_000

    /**
     * 훑기가 기억해 두는 그림 설명문의 수(문서 전체, 사람이 쓴 것만 — `HancomAlt`). 하나가 300자까지라 꽉 차면 십수 MB 다
     * (300자 × 2만 × UTF-16 에 항목의 머리) — 표 칸 정보의 상한([MAX_STORED_SPAN_CELLS], 8 MB)과 같은 자릿수다. 실물 표본에는
     * 사람이 쓴 설명문이 하나도 없었다. 넘으면 뒤의 그림은 대체 글 없이 그린다.
     */
    const val MAX_STORED_ALTS = 20_000

    /** 글상자·캡션·각주 안의 글상자… 가 겹치는 깊이. */
    const val MAX_SUBLIST_DEPTH = 4

    /** 묶음 개체(`hp:container`) 안의 개체가 겹치는 깊이. */
    const val MAX_GRAPHIC_DEPTH = 32

    /** 부분 하나에 모아 그리는 각주·미주의 수. */
    const val MAX_NOTES = 10_000

    /** 글자 요소(`hp:t`) 하나의 글자 수. 넘으면 자르고 알린다. */
    const val MAX_TEXT = 1_000_000

    /** 잇달아 둘 수 있는 빈 문단. 한글 문서는 빈 문단으로 간격을 띄우므로 셋까지는 살린다. */
    const val MAX_EMPTY_RUN = 3

    /**
     * 제목 글을 모으는 상한(글자). 목차 한 줄·조각 이름은 다듬은 뒤 `HancomTitles.MAX_CHARS`(200)로 자른다 — 공백을 줄이기
     * 전이라 조금 더 모은다(HWP 5.0 의 `Hwp5Limits.MAX_HEADING_CHARS` 와 같은 값).
     */
    const val MAX_HEADING_CHARS = 300

    /** 블록 사이에 걸린 책갈피를 다음 문단까지 들고 가는 수. */
    const val MAX_PENDING_ANCHORS = 64

    /** 필드(누름틀·하이퍼링크) 겹침. */
    const val MAX_FIELD_DEPTH = 16

    /** 필드 매개변수(`hp:stringParam`) 하나의 글자 수. 하이퍼링크 주소가 여기 있다. */
    const val MAX_PARAM_CHARS = 4_096

    /** 수식 스크립트 하나. */
    const val MAX_SCRIPT_CHARS = 64 * 1024

    /** 덧말·글자 겹치기·글맵시 같은 짧은 글자. */
    const val MAX_SHORT_TEXT = 1_024

    /** 문서 제목(메타데이터). */
    const val MAX_TITLE_CHARS = 500

    /** 미리보기 글(`Preview/PrvText.txt`). 한글이 앞부분 몇 KB 만 적는다. */
    const val MAX_PREVIEW_BYTES = 1L * 1024 * 1024

    /** `mimetype` 항목. 19바이트면 되지만 뒤에 줄바꿈이 붙은 파일이 있다. */
    const val MAX_MIMETYPE_BYTES = 256L

    /**
     * 암호 HWPX 의 열쇠 유도(PBKDF2) 반복 수. 한컴은 1024 를 쓴다(한컴의 OWPML 모델 `encodeInit`).
     * 넘으면 '다루지 않는다' — 항목마다 따로 유도하므로 반복 수를 믿으면 파일 하나로 몇 분을 쓸 수 있다.
     */
    const val MAX_KDF_ITERATIONS = 1_000_000

    /** 암호 매니페스트(`META-INF/manifest.xml`)의 항목 수. */
    const val MAX_ENCRYPTED_ENTRIES = 10_000

    /**
     * 문서 하나에서 열쇠 유도에 쓰는 반복 수의 **합**(`HwpxDecryptor` 의 주석). 한컴의 값(1024)이면 항목마다 salt 가
     * 달라도 [MAX_ENCRYPTED_ENTRIES] 개를 다 유도하고 남는다 — 실물 O16 은 일곱 항목에 salt 가 둘이다. 반복 수를
     * 부풀린 문서는 100만 회짜리 열두 번에서 멈춘다(데스크톱 JVM 에서 100만 회가 약 0.5초).
     */
    const val MAX_KDF_TOTAL_ITERATIONS = 12_000_000L
}
