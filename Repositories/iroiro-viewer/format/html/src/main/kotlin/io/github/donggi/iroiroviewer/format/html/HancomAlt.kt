package io.github.donggi.iroiroviewer.format.html

/**
 * 한글 문서(HWP 5.0·HWPX)의 **개체 설명문**을 그림의 대체 글(`img@alt`)로 옮기는 규칙. **두 변환기가 이 규칙 하나를 쓴다.**
 *
 * HWP 5.0 은 개체 공통 속성의 '개체 설명문' 문자열, HWPX 는 `hp:shapeComment` 에 같은 글을 적는다. 처음에는 HWP 5.0
 * 변환기만 설명문을 읽었고(규칙도 그 안에 있었다) HWPX 는 대체 글을 늘 비웠다 — 사람이 쓴 그림 설명이 HWP 로만 화면 낭독기에
 * 닿았다(13단계에서 미룬 것). 규칙을 여기 두면 한쪽만 고쳐지는 일이 없다(`HancomChars` 와 같은 까닭).
 *
 * ## 한글이 저절로 넣는 설명문은 버린다
 *
 * 한글은 개체를 넣을 때마다 설명문을 저절로 채운다 — 첫 줄이 개체 종류의 이름 + `입니다.`([AUTO_COMMENT_HEADS]·클립아트)이고
 * 뒤 줄이 전부 `열쇠: 값`(`원본 그림의 이름`·`원본 그림의 크기`, 사진이면 `사진 찍은 날짜`·`프로그램 이름` 같은 EXIF 줄)이다.
 * 그대로 두면 화면 낭독기가 'CLP000043080017.bmp' 를 읽고, 그림이 깨지면 그 글이 화면에 뜬다. 13단계 표본의 설명문이
 * 전부 이 틀이었다 — HWP 205개(13단계), HWPX 111개(그림 107·사각형 2·다각형 1·수식 1, 2026-09-28 에 이 규칙으로 셌다).
 * **사람이 쓴 설명은 남긴다.**
 * 모양으로 짐작한 규칙이다 — 한컴이 설명문의 틀을 적은 공개 자료는 찾지 못했다.
 */
object HancomAlt {

    /** 대체 글의 상한(글자). 12단계가 상한 없는 `descr` 에 한 번 데었다 — 본문이 위생기의 입력 상한을 넘었다. */
    const val MAX_CHARS = 300

    /**
     * 설명문을 **읽을 때**의 상한. [MAX_CHARS] 보다 넉넉하다 — 저절로 넣은 설명문인지는 **뒤 줄까지 보고** 가리므로, 읽을 때
     * 자르면 잘린 끝 줄에 `:` 가 없어 저절로 넣은 설명이 사람이 쓴 것으로 남는다(사진의 EXIF 줄이 붙으면 300자를 넘는다).
     */
    const val MAX_READ_CHARS = 4_096

    /** 대체 글로 쓸 글. 비었거나 한글이 저절로 넣은 설명문이면 빈 문자열이다. 가리고 나서 [MAX_CHARS] 로 자른다. */
    fun of(raw: String?): String {
        if (raw.isNullOrBlank() || isAutoComment(raw)) return ""
        return raw.trim().take(MAX_CHARS)
    }

    /** 한글이 저절로 넣은 설명문인가(위 '버린다'). 빈 첫 줄도 쓸 것이 없으므로 참이다. */
    fun isAutoComment(s: String): Boolean {
        val lines = s.trim().lines()
        val first = lines.first().trim()
        if (first.isEmpty()) return true
        if (first !in AUTO_COMMENT_HEADS && !CLIP_ART_HEAD.matches(first)) return false
        // 뒤 줄은 전부 `열쇠: 값` 이다. 사람이 이어 쓴 설명이 있으면 남긴다.
        return lines.drop(1).all { it.isBlank() || ':' in it }
    }

    /**
     * 저절로 넣는 설명문의 첫 줄 — 개체 종류의 이름 + `입니다.`. 표본에서 본 것은 그림·사각형·묶음 개체·개체 연결선·다각형·
     * 클립아트(`장식11-4입니다.`)이고, 나머지는 같은 틀의 개체 종류다. **아무 `…입니다.` 나 받지 않는다** — '조직도입니다.' 는
     * 사람이 쓴 설명일 수 있다(13단계 검토가 잡았다).
     */
    private val AUTO_COMMENT_HEADS = setOf(
        "그림", "사각형", "타원", "선", "호", "다각형", "곡선", "묶음 개체", "개체 연결선", "글맵시", "글상자",
        "OLE 개체", "차트", "동영상", "수식", "표",
    ).map { "${it}입니다." }.toSet()

    /** 클립아트의 설명문 첫 줄(`장식11-4입니다.`). */
    private val CLIP_ART_HEAD = Regex("장식[0-9]+(-[0-9]+)*입니다\\.")
}
