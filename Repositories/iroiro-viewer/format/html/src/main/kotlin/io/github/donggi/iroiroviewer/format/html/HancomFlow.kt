package io.github.donggi.iroiroviewer.format.html

/**
 * 두 한글 변환기(HWP 5.0·HWPX)의 **바탕 스타일**. 둘이 이 상수 하나를 쓴다.
 *
 * 처음에는 변환기마다 한 벌씩 두었고, 13단계 끝의 짝 대조(같은 보도자료의 HWP·HWPX)가 일곱 군데의 어긋남을 찾았다 —
 * 캡션 크기(HWP 만 .92em 으로 줄였다), 표 여백, 칸 안쪽 여백, 제목의 줄 간격(HWP 140%·HWPX 1.6), 칸의 기본 세로 정렬
 * (HWP 위·HWPX 가운데), 칸 끝 문단의 아래 여백. 한쪽만 고치면 같은 문서가 포맷에 따라 달리 보인다.
 *
 * * 문단은 `pre-wrap`(한글은 공백 여럿과 탭을 글자로 다룬다), 여백은 0 — 한글 문서는 빈 문단과 문단 간격으로 띄우므로
 *   바탕 여백을 더하면 두 번 띄운다. 줄 간격은 한글의 기본값 160% 를 **단위 없는 수**로 준다: 백분율은 문단에서 길이로
 *   풀린 뒤 자식에게 물려져, 문단보다 큰 글자에 좁은 줄을 준다(HWP 판이 그랬다).
 * * 제목(`h1`..`h6`)의 크기와 굵기는 변환기가 언제나 적는다 — 아래 규칙은 적지 못했을 때의 안전망이다(브라우저의
 *   `h1{font-size:2em}` 이 한글의 크기를 덮지 않게).
 * * 칸의 기본 세로 정렬은 가운데다(한글의 기본값). 위·아래인 칸만 변환기가 적는다.
 * * 캡션(`div.cap`)은 줄이지 않는다 — 캡션 문단이 제 크기를 들고 있다(오라클인 바로보기도 그 크기로 적는다).
 * * 칸 안쪽 여백의 좌우 5pt 는 한글의 기본 칸 여백(1.8 mm ≈ 5.1 pt)이다.
 */
object HancomCss {
    const val FLOW = """
p,h1,h2,h3,h4,h5,h6{white-space:pre-wrap;tab-size:4;margin:0;line-height:1.6;}
h1,h2,h3,h4,h5,h6{font-size:1em;font-weight:normal;}
.mk{display:inline-block;text-indent:0;padding-right:.4em;white-space:nowrap;}
table{border-collapse:collapse;margin:.4em 0;}
td{border:1px solid #9e9e9e;padding:2pt 5pt;vertical-align:middle;}
td.nb{border-color:transparent;}
td.gap{border:none;}
.tb{border:1px solid #bdbdbd;border-radius:3px;padding:4pt 8pt;margin:.4em 0;}
.cap{margin:.3em 0;}
.ext{text-decoration:underline dotted;}
a{color:#1a56c4;}
.math{font-family:monospace;}
.serif{font-family:serif;}
sup.fnref a,sup.fnnum a{text-decoration:none;}
.notes{font-size:.88em;margin-top:2em;}
.notes hr{border:none;border-top:1px solid #bdbdbd;width:30%;margin:0 0 .6em;}
.note{margin:.2em 0;}
.preview p{margin:0 0 .3em;}
img{vertical-align:text-bottom;}
"""
}

/**
 * 긴 구역을 부분(part)으로 나누는 기준 — 12단계 docx 의 `ChunkPolicy` 와 같은 값이다. 무게가 기준을 넘으면 **다음 제목
 * 바로 앞**에서, 제목이 오지 않으면 두 배쯤에서 아무 최상위 문단 앞에서 끊는다. 구역이 바뀌면 언제나 끊는다(부르는 쪽).
 */
data class HancomChunkPolicy(
    val softChars: Int = 150_000,
    val softBlocks: Int = 1_500,
    val hardChars: Int = 300_000,
    val hardBlocks: Int = 3_000,
    /** 부분마다 찍어 두는 시작 상태의 합의 상한(12단계 docx 의 '목록 폭탄' 교훈). 넘으면 구역 안에서는 더 끊지 않는다. */
    val snapshotBudget: Long = 16L * 1024 * 1024,
)

/**
 * 부분 하나의 무게를 센다 — 글자 수와 **블록 수**. 블록은 최상위 문단 하나, 그리고 **표의 칸 하나**다.
 *
 * 칸을 블록으로 치는 까닭: 공문서는 본문 대부분이 표라(K01 은 문단 16,000개 가운데 15,400개가 칸 안이다) 최상위 문단만
 * 세면 표 수백 개가 한 부분이 되고, WebView 는 칸 수에 비례해 배치가 느리다(12단계 실측: 칸 10만 개의 표가 45초). 처음에는
 * HWP 가 칸을 블록 하나로, HWPX 가 글자 60자로 쳐서 **같은 보도자료가 HWP 로 6부분, HWPX 로 4부분**이었다(13단계 짝 대조).
 *
 * 쓰는 차례: 최상위 문단을 시작하기 직전(번호를 세기 전)에 [wantsCut] 을 묻고, 참이면 상태를 찍을 값으로 [cut] 을 부른
 * 뒤 [onBlock]. 글자는 [addChars], 칸은 [addBlocks].
 */
class HancomChunkMeter(private val policy: HancomChunkPolicy) {
    private var chars = 0L
    private var blocks = 0
    private var snapshotBytes = 0L

    /** 찍어 둔 상태가 예산을 다 썼다 — 구역 안에서는 더 끊지 않는다. */
    var saturated = false
        private set

    fun addChars(n: Int) {
        chars += n
    }

    fun addBlocks(n: Int) {
        blocks += n
    }

    /** 이 최상위 문단 앞에서 끊고 싶은가. 제목이면 부드러운 기준으로도 끊는다. */
    fun wantsCut(heading: Boolean): Boolean = !saturated && blocks > 0 && (
        (heading && (chars >= policy.softChars || blocks >= policy.softBlocks)) ||
            chars >= policy.hardChars || blocks >= policy.hardBlocks
        )

    /**
     * 끊는다. [cost] 는 찍어 둘 상태의 크기다 — 예산을 넘으면 끊지 않고 거짓(그 뒤로 [saturated]). [force] 면 예산과 관계없이
     * 끊는다(구역의 경계 — 부분 하나가 구역 둘에 걸치지 않는다는 약속이 먼저다. 구역은 많아야 수백이다).
     */
    fun cut(cost: Long, force: Boolean = false): Boolean {
        if (!force && snapshotBytes + cost > policy.snapshotBudget) {
            saturated = true
            return false
        }
        snapshotBytes += cost
        chars = 0
        blocks = 0
        return true
    }

    /** 최상위 문단 하나를 센다([cut] 뒤에). */
    fun onBlock() {
        blocks++
    }
}

/**
 * 제목의 글을 목차 줄과 **부분의 이름**으로 다듬는다. 부분의 이름은 알림 문장에도 들어가므로(`partName` → 경고의 `detail`)
 * 12단계 시트 이름과 같이 다듬는다 — 제어 문자는 공백으로, **방향을 바꾸는 문자는 버린다**(그대로 두면 제목 하나가 알림
 * 문장을 거꾸로 보이게 한다), 공백 여럿은 하나로, [MAX_CHARS] 자까지. 처음에는 HWPX 쪽이 방향 문자를 거르지 않았고 상한도
 * 달랐다(13단계 짝 대조).
 *
 * 줄바꿈은 부르는 쪽이 공백으로 적는다(`A<br>B` 의 제목은 `A B` 다).
 */
object HancomTitles {
    const val MAX_CHARS = 200

    fun clean(text: String): String {
        val sb = StringBuilder(minOf(text.length, MAX_CHARS * 2))
        for (c in text) {
            when {
                isDirectionControl(c) -> Unit
                c.isISOControl() -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.trim().replace(SPACES, " ").take(MAX_CHARS)
    }

    /** 방향을 바꾸는 글자 — 끼워 넣기·덮어쓰기(U+202A~202E), 격리(U+2066~2069), 표시(U+200E·200F·061C). */
    private fun isDirectionControl(c: Char): Boolean =
        c in '‪'..'‮' || c in '⁦'..'⁩' || c == '‎' || c == '‏' || c == '؜'

    private val SPACES = Regex("\\s{2,}")
}
