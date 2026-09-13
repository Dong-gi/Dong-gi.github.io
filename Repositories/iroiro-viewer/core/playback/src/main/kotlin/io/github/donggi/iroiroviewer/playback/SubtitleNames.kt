package io.github.donggi.iroiroviewer.playback

import io.github.donggi.iroiroviewer.model.FileEntry

/**
 * 영상 옆의 **자막 파일을 이름으로 찾는다.**
 *
 * ## 추측이라는 것을 숨기지 않는다
 *
 * `영화.mkv` 옆의 `영화.srt` 가 그 영화의 자막이라는 보장은 어디에도 없다. 이름 규칙은
 * 관행일 뿐이고, `영화.ko.srt`·`영화.kor.srt`·`영화 (한글).srt` 처럼 갈래가 많다.
 * 9단계의 `ComicPages` 가 '어느 그림이 몇 쪽인가' 를 이름으로 추측한 것과 같은 종류다.
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
     * **`.smi`(SAMI)가 없다.** media3 의 파서 열 갈래(`extractor/text/` 아래 cea·dvb·pgs·
     * ssa·subrip·ttml·tx3g·vobsub·webvtt)에 SAMI 가 없기 때문이다. 한국어 자막에 흔한
     * 형식이지만 직접 파서를 쓰는 것은 이 단계의 범위가 아니다 — 목록에 올려 놓고 붙이면
     * 빈 자막이 되는 것보다, **다루지 않는다고 정확히 말하는 편이 낫다.**
     */
    val EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")

    fun isSubtitle(name: String): Boolean = extensionOf(name) in EXTENSIONS

    /**
     * media3 에 넘길 MIME. 확장자로 정한다 — 내용으로 보려면 파일을 열어야 하고,
     * 자막을 고르는 시트는 목록을 그리는 자리다.
     */
    fun mimeOf(name: String): String = when (extensionOf(name)) {
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
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
        return matched.sortedBy { it.name } + rest.sortedBy { it.name }
    }

    /** 기본으로 붙일 자막. 짝이 맞는 것이 없으면 null — 아무것이나 고르지 않는다. */
    fun defaultFor(mediaName: String, siblings: List<FileEntry>): FileEntry? =
        siblings.firstOrNull { !it.isDirectory && !it.isLocked && matches(mediaName, it.name) }
}
