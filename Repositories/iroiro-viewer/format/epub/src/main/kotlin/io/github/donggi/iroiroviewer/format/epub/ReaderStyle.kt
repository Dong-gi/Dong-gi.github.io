package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.HtmlShell
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import kotlin.math.floor
import kotlin.math.min

/**
 * **읽는 모양을 CSS 로 옮기는 유일한 곳** — 여백과 바탕(흐름으로 읽는 글), 쪽 맞춤(고정 레이아웃).
 *
 * ## 글자 크기는 여기 없다
 *
 * 글자 크기는 WebView 의 `textZoom` 이 한다(화면 쪽). 책의 CSS 가 `font-size` 를 몇 겹으로 적어 두어도 그 위에서 곱해지므로
 * **책의 조판과 다투지 않고**, 바꿔도 쪽을 다시 읽지 않는다. 여기서 `font-size` 를 덮으면 제목·각주·표의 크기 차이가 사라진다.
 *
 * ## 왜 `format:epub` 에 있는가
 *
 * 문서 화면이 볼 수 있는 포맷 모듈이 이것 하나다(의존 표). 값을 CSS 로 옮기는 길은 `format:html` 의 [CssValues]·[StyleBuilder]
 * 뿐이어야 하는데, 화면이 그것을 직접 부르게 하면 '화면은 `format:html` 을 직접 부르지 않는다' 가 깨진다. 흐름 문서(docx·HWP)도
 * 이 모양을 쓰므로([apply]) 본래 자리는 `format:html` 이다 — 옮기는 것은 그 모듈을 고칠 때의 일로 남긴다.
 *
 * ## 바탕을 바꿀 때 지키는 것
 *
 * * **글이 읽혀야 한다.** 어두운 바탕에서는 문서가 적은 글자색·배경색을 **덮는다**(`!important`). 검은 글자를 적어 둔 문서가
 *   흔하고(워드·한글 변환기가 글자마다 색을 적는다), 그것을 두면 검정 위의 검정이 된다. 대가는 색으로 뜻을 준 글(빨간 강조)이
 *   한 색이 되는 것이다 — 뒤집기 필터(`filter:invert`)로 색을 살리는 길은 그림의 채도가 높은 색을 되돌리지 못하고(두 번의
 *   `hue-rotate` 사이에서 값이 잘린다) CSS 배경 그림까지 뒤집어, '그림은 그대로' 를 지킬 수 없어 고르지 않았다.
 * * **그림은 그대로다.** 색을 바꾸는 규칙은 글자·배경·테두리만 건드린다. 투명한 그림(수식·선화)은 검은 선이 어두운 바탕에
 *   묻히므로 **그림 뒤에만** 흰 바탕을 깐다 — 그림 자체는 바뀌지 않는다.
 * * **세피아는 덮지 않는다.** 종이 색만 바꾸고 글자색은 문서가 적지 않았을 때만 준다(`:where` — 특이도 0). 밝은 바탕이라
 *   문서의 색이 그대로 읽힌다.
 */
object ReaderStyle {

    /** 바탕. 화면이 설정(시스템·밝게·어둡게·세피아)을 기기의 밤 모드로 풀어 준다. */
    enum class Tone { LIGHT, DARK, SEPIA }

    /** 좌우 여백. */
    enum class Margin { NARROW, NORMAL, WIDE }

    /**
     * 화면이 장마다 청하는 모양.
     *
     * @param viewWidth·[viewHeight] 화면의 크기(**CSS px** — 화면 화소 ÷ 밀도). 고정 레이아웃 쪽을 맞추는 데만 쓴다.
     */
    data class Look(
        val margin: Margin = Margin.NORMAL,
        val tone: Tone = Tone.LIGHT,
        val viewWidth: Double = 0.0,
        val viewHeight: Double = 0.0,
    )

    /**
     * 흐름으로 읽는 글의 여백·바탕.
     *
     * 여백은 `body` 의 좌우 **안쪽 여백**이다 — 껍데기(`HtmlShell`)가 이미 그 자리에 18px 을 두고 있고, 바깥 여백은 0 으로
     * 누른다(책이 `body.calibre{margin:5%}` 처럼 적어 두면 우리 값과 더해져 고른 것보다 넓어진다).
     */
    fun flow(margin: Margin, tone: Tone): String = buildString {
        val side = when (margin) {
            Margin.NARROW -> NARROW_PT
            Margin.NORMAL -> NORMAL_PT
            Margin.WIDE -> WIDE_PT
        }
        val body = StyleBuilder()
            .add("padding-left", CssValues.pt(side, 0.0, MAX_MARGIN_PT)?.let { it + IMPORTANT })
            .add("padding-right", CssValues.pt(side, 0.0, MAX_MARGIN_PT)?.let { it + IMPORTANT })
            .add("margin-left", "0$IMPORTANT")
            .add("margin-right", "0$IMPORTANT")
            .build()
        if (body != null) append("body{").append(body).append('}')
        append(toneCss(tone))
    }

    /** 바탕색(ARGB). 화면이 WebView 의 바탕과 둘레를 같은 색으로 칠한다 — 쪽이 그려지기 전 한 프레임이 번쩍이지 않게. */
    fun background(tone: Tone): Int = when (tone) {
        Tone.LIGHT -> 0xFFFFFFFF.toInt()
        Tone.DARK -> 0xFF121212.toInt()
        Tone.SEPIA -> 0xFFF4ECD8.toInt()
    }

    /** 고정 레이아웃 쪽 둘레의 색. PDF 의 바닥(어두운 회색)과 같다 — 종이 한 장을 얹는 화면이라는 점이 같다. */
    const val FIXED_BACKDROP: Int = 0xFF303030.toInt()

    /**
     * 고정 레이아웃 쪽 하나를 화면에 **통째로** 맞춘다.
     *
     * 쪽의 화폭([page])을 `html` 의 크기로 못 박고(`position:relative` 로 절대 위치의 기준이 되게 한다 — 명세상 그 기준은 화폭
     * 크기의 초기 담는 상자다), `zoom` 으로 줄여 화면에 넣고 가운데에 놓는다. 흐름 렌더와 달리 **다시 흘리지 않는다** — 줄이
     * 바뀌지 않고 좌표가 그대로다.
     *
     * **`zoom` 을 고른 까닭** — `transform: scale()` 은 배치 크기를 바꾸지 않아 원래 화폭만큼 스크롤이 남는다. `zoom` 은 배치까지
     * 줄인다. 배율은 소수 넷째 자리에서 **내린다** — 올리면 1 화소 넘쳐 스크롤 막대가 생긴다. 그리고 **백분율로 적는다**
     * (`CssValues.percent` 는 소수 넷째 자리까지 그대로 적는다). `CssValues.number` 는 둘째 자리에서 **반올림**해, 내려 둔 배율이
     * 도로 올라가 쪽이 화면을 넘었다.
     *
     * 껍데기의 읽기용 여백(`body` 의 16·18px)과 그림의 `max-width:100%` 는 좌표를 흔들므로 되돌린다. 여백은 `!important` 없이
     * 되돌린다 — 껍데기의 규칙(같은 특이도, 앞)만 이기고, 책이 클래스로 준 `body` 의 여백(`body.page{…}`)은 살린다.
     *
     * @param page 모르면(쪽이 크기를 적지 않았다) null — 그때는 그림을 화면에 맞추기만 한다.
     */
    fun fixedPage(page: FixedLayout.Viewport?, viewWidth: Double, viewHeight: Double): String {
        val reset = "body{margin:0;padding:0}"
        val backdrop = "html{background-color:#303030}"
        if (page == null || !(viewWidth > 0.0) || !(viewHeight > 0.0) || !viewWidth.isFinite() || !viewHeight.isFinite()) {
            // 크기를 모른다. 만화형 책(그림을 곧바로 차례에 둔다)이 대개 이렇다 — 그림 한 장을 화면에 맞춘다.
            return "$backdrop$reset" + "img,svg{max-width:100%;max-height:100vh}"
        }
        val scale = fitScale(page.width, page.height, viewWidth, viewHeight)
        val left = (viewWidth / scale - page.width) / 2.0
        val top = (viewHeight / scale - page.height) / 2.0
        val html = StyleBuilder()
            .add("position", "relative")
            .add("width", CssValues.pt(page.width * PT_PER_PX, 0.0, MAX_SIDE_PT))
            .add("height", CssValues.pt(page.height * PT_PER_PX, 0.0, MAX_SIDE_PT))
            .add("margin-left", CssValues.pt(left.coerceAtLeast(0.0) * PT_PER_PX, 0.0, MAX_SIDE_PT))
            .add("margin-top", CssValues.pt(top.coerceAtLeast(0.0) * PT_PER_PX, 0.0, MAX_SIDE_PT))
            .add("margin-right", "0")
            .add("margin-bottom", "0")
            .add("padding", "0")
            .add("zoom", CssValues.percent(scale * 100.0, MIN_ZOOM * 100.0, MAX_ZOOM * 100.0))
            .build()
        return "$backdrop$reset" + "html{$html}" +
            // 쪽의 종이. 책이 `body` 에 바탕·높이를 적었으면 그것이 이긴다(`:where` 는 특이도 0). 높이를 화폭만큼 채우는
            // 것은 글이 짧은 쪽에서 종이 아래가 바닥의 회색으로 비지 않게 하려는 것이다.
            ":where(body){background-color:#ffffff;min-height:100%}" +
            "img,svg,video,table{max-width:none}"
    }

    /**
     * 화폭을 화면에 넣는 배율 — 폭과 높이 가운데 **작은 쪽**에 맞춘다(쪽 전체가 보인다). 소수 넷째 자리에서 내린다.
     */
    fun fitScale(pageWidth: Int, pageHeight: Int, viewWidth: Double, viewHeight: Double): Double {
        if (pageWidth <= 0 || pageHeight <= 0 || !(viewWidth > 0.0) || !(viewHeight > 0.0)) return 1.0
        val raw = min(viewWidth / pageWidth, viewHeight / pageHeight)
        return (floor(raw * 10_000.0) / 10_000.0).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /**
     * 흐름 문서(docx·HWP·HWPX)의 HTML 에 [css] 를 얹는다. 흐름 문서의 계약(`FlowDocument.partHtml`)은 CSS 를 받지 않으므로
     * 껍데기를 **한 번 더** 씌운다 — `HtmlShell.wrap` 은 이미 머리가 있는 문서면 그 끝(`</head>` 앞)에 스타일을 넣고, 태그 자리를
     * 주석·날것의 글을 건너뛰며 찾는다. 읽기용 기본 CSS 가 한 번 더 들어가지만 같은 규칙이라 모양이 바뀌지 않는다.
     */
    fun apply(html: String, css: String): String = if (css.isEmpty()) html else HtmlShell.wrap(html, css)

    private fun toneCss(tone: Tone): String = when (tone) {
        Tone.LIGHT -> ""
        Tone.SEPIA -> "html,body{background-color:#f4ecd8$IMPORTANT}:where(body){color:#3b2f22}"
        Tone.DARK ->
            "html,body{background-color:#121212$IMPORTANT}" +
                "body,body *{color:#e0e0e0$IMPORTANT;background-color:transparent$IMPORTANT;" +
                "border-color:#5f5f5f$IMPORTANT;text-shadow:none$IMPORTANT}" +
                "body a,body a *{color:#8ab4f8$IMPORTANT}" +
                // 그림 **뒤에만** 흰 바탕 — 투명한 수식·선화가 어두운 바탕에 묻히지 않게. 그림은 그대로다.
                "img,svg,video,canvas{background-color:#ffffff$IMPORTANT}"
    }

    private const val IMPORTANT = " !important"

    /** 1 CSS px = 0.75pt. `CssValues` 가 px 을 내지 않아 pt 로 옮긴다. */
    private const val PT_PER_PX = 0.75

    /** 좁게 8px · 보통 18px(껍데기의 기본과 같다) · 넓게 40px. */
    private const val NARROW_PT = 6.0
    private const val NORMAL_PT = 13.5
    private const val WIDE_PT = 30.0
    private const val MAX_MARGIN_PT = 200.0

    private const val MAX_SIDE_PT = FixedLayout.MAX_SIDE * PT_PER_PX
    private const val MIN_ZOOM = 0.01
    private const val MAX_ZOOM = 10.0
}
