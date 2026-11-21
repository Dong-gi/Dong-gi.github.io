package io.github.donggi.iroiroviewer.playback

import io.github.donggi.iroiroviewer.model.FileEntry

/**
 * 영상 옆의 **자막 파일을 이름으로 찾는다.**
 *
 * ## 추측이라는 것을 숨기지 않는다
 *
 * `영화.mkv` 옆의 `영화.srt` 가 그 영화의 자막이라는 보장은 어디에도 없다. 이름 규칙은
 * 관행일 뿐이고, `영화.ko.srt`·`영화.kor.srt`·`영화 (한글).srt` 처럼 갈래가 많다.
 *
 * 그래서 이 추측은 **'무엇을 기본으로 붙일까' 만** 정한다. 짝이 틀렸거나 못 찾았을 때
 * 막다른 길이 되지 않도록, 화면은 **같은 폴더의 자막 파일을 전부** 목록으로 보여 준다.
 * 추측이 맞으면 한 번에 붙고, 틀리면 한 번 더 누르면 된다.
 *
 * ## android.* 를 쓰지 않는다
 *
 * 이름 계산은 순수 함수라 JVM 시험으로 전부 박힌다.
 */
object SubtitleNames {

    /**
     * 우리가 자막으로 **읽을 수 있는** 확장자. 소문자, 점 없음.
     *
     * **`.smi`·`.sami`(SAMI)는 media3 가 읽지 못한다**(파서 열 갈래 cea·dvb·pgs·ssa·subrip·ttml·tx3g·vobsub·webvtt 에 없다).
     * 바이트가 들어가는 길에서 SRT 로 바꿔 넘긴다([SamiToSrt] — [SubtitleRewrite]).
     */
    val EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "ttml", "dfxp", "smi", "sami")

    /** SAMI 의 확장자. 이 자막은 SRT 로 바꿔 넘긴다([SubtitleRewrite]). */
    val SAMI_EXTENSIONS = setOf("smi", "sami")

    fun isSubtitle(name: String): Boolean = extensionOf(name) in EXTENSIONS

    /**
     * 인코딩을 판정해 UTF-8 로 바꿔 넘길 자막인가(`SubtitleDataSourceFactory`). **글 자막만**이다 — media3 의 파서가 BOM 이
     * 없으면 UTF-8 로 단정하는 것들. TTML(`ttml`·`dfxp`)은 XML 이라 인코딩을 **선언**에 적는다(`<?xml … encoding=…?>`) —
     * 바이트를 UTF-8 로 바꾸고 선언을 그대로 두면 파서가 선언대로 읽어 깨진다. 그래서 그대로 넘긴다.
     */
    fun needsTranscoding(name: String): Boolean = extensionOf(name) in TRANSCODED

    private val TRANSCODED = setOf("srt", "ass", "ssa", "vtt", "smi", "sami")

    /**
     * media3 에 넘길 MIME. 확장자로 정한다 — 내용으로 보려면 파일을 열어야 하고,
     * 자막을 고르는 시트는 목록을 그리는 자리다. SAMI 는 SRT 로 바뀌어 들어가므로 SRT 의 것이다.
     */
    fun mimeOf(name: String): String = when (extensionOf(name)) {
        "srt", "smi", "sami" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        // media3 의 TTML 파서(`extractor/text/ttml`)가 받는 이름이다. DFXP 는 TTML 의 옛 이름이고 모양이 같다.
        "ttml", "dfxp" -> "application/ttml+xml"
        else -> "application/x-subrip"
    }

    /** 점 없는 소문자 확장자. 없으면 빈 문자열. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** 확장자를 뗀 이름. `영화.ko.srt` → `영화.ko`. */
    fun stemOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    /**
     * 이 자막이 이 영상의 것인가.
     *
     * `영화.mkv` 에 붙는 것은 `영화.srt` 와, **`영화.` 로 시작하는 것**(`영화.ko.srt`·
     * `영화.eng.ass`)이다. `영화2.srt` 는 붙지 않는다 — 점까지 맞아야 한다.
     * 비교는 대소문자를 가리지 않는다(FAT 볼륨에서 대소문자가 흔들린다).
     */
    fun matches(mediaName: String, subtitleName: String): Boolean {
        if (!isSubtitle(subtitleName)) return false
        val media = stemOf(mediaName).lowercase()
        if (media.isEmpty()) return false
        val sub = stemOf(subtitleName).lowercase()
        return sub == media || sub.startsWith("$media.")
    }

    /**
     * 이 영상에 붙일 수 있는 자막들. **짝이 맞는 것이 앞**이고 같은 폴더의 나머지가 뒤다.
     *
     * 나머지까지 주는 것이 요점이다 — 이름 규칙이 우리 추측과 다른 파일
     * (`영화-한글자막.srt`)도 사용자가 한 번 눌러 붙일 수 있어야 한다.
     */
    fun candidatesFor(mediaName: String, siblings: List<FileEntry>): List<FileEntry> {
        val subs = siblings.filter { !it.isDirectory && !it.isLocked && isSubtitle(it.name) }
        val (matched, rest) = subs.partition { matches(mediaName, it.name) }
        return matched.sortedWith(SAMI_LAST.thenBy { it.name }) + rest.sortedBy { it.name }
    }

    /**
     * 기본으로 붙일 자막. 짝이 맞는 것이 없으면 null — 아무것이나 고르지 않는다.
     *
     * 짝이 맞는 것 가운데 **SAMI 는 뒤로 민다** — `영화.srt` 와 `영화.smi` 가 함께 있으면 이름 차례로는 `.smi` 가 먼저인데, SAMI 는
     * 바꿔 넣는 길이라 언어 하나만·서식 없이 보인다([SamiToSrt]). 원래 형식이 있으면 그것이 낫다. 나머지는 폴더가 준 차례 그대로다.
     */
    fun defaultFor(mediaName: String, siblings: List<FileEntry>): FileEntry? =
        siblings.filter { !it.isDirectory && !it.isLocked && matches(mediaName, it.name) }.sortedWith(SAMI_LAST).firstOrNull()

    /** SAMI 가 아닌 것이 먼저(안정 정렬이라 그 밖의 차례는 남는다). */
    private val SAMI_LAST: Comparator<FileEntry> = compareBy { extensionOf(it.name) in SAMI_EXTENSIONS }
}
