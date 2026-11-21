package io.github.donggi.iroiroviewer.playback

import java.util.Locale

/**
 * 외부 자막 파일의 **싱크를 옮긴다**(±0.1초씩). 양수면 자막이 늦게, 음수면 일찍 뜬다.
 *
 * ## 왜 글을 고쳐 쓰는가
 *
 * media3 에는 자막 시각을 옮기는 자리가 없다(`SubtitleConfiguration` 에 그런 칸이 없다). 받은 자막을 화면이 늦게 그리는 길은
 * 늦추기만 되고 앞당기기는 안 된다 — 아직 오지 않은 자막을 미리 그릴 수 없다. 그래서 자막 바이트가 들어가는 길
 * ([SubtitleDataSourceFactory] — 이미 인코딩을 바꾸려고 통째로 읽는다)에서 **시각 글자를 고쳐 쓴다.** 옮긴 값은 자막 주소의
 * 질의([PARAM])에 실려 항목과 함께 다닌다 — 큐에 그 항목이 있는 동안은 다음 곡으로 갔다 돌아와도 남는다.
 *
 * **글 자막만**(SRT·VTT·ASS·SSA, 그리고 SRT 로 바뀌어 들어가는 SAMI — [SubtitleNames.needsTranscoding])이다. TTML 은 그 길을 지나지 않고, 영상 안 자막은 밖이다.
 *
 * android.* 를 쓰지 않는다 — 시각 고쳐 쓰기는 JVM 시험이 답한다.
 */
object SubtitleShift {

    /** 자막 주소에 옮긴 값(ms)을 싣는 질의 이름. `FileDataSource` 는 경로만 읽으므로 질의가 있어도 같은 파일을 연다. */
    const val PARAM = "iroiro-shift"

    /** 한 번 누를 때 옮기는 양. */
    const val STEP_MS = 100L

    /** 옮길 수 있는 끝(10분). 손으로 고친 주소가 터무니없는 값을 싣지 않게 누른다. */
    const val MAX_MS = 600_000L

    /** 이 자막 파일을 옮길 수 있는가 — 바이트를 가로채는 글 자막만. */
    fun supports(name: String?): Boolean = name != null && SubtitleNames.needsTranscoding(name)

    /** [current] 에서 [delta] 만큼. 걸음([STEP_MS])에 맞추고 끝([MAX_MS])에서 멈춘다. */
    fun step(current: Long, delta: Long): Long {
        val raw = current + delta
        val snapped = Math.round(raw / STEP_MS.toDouble()) * STEP_MS
        return snapped.coerceIn(-MAX_MS, MAX_MS)
    }

    /** 주소의 질의 값 → 옮긴 양. 없거나 읽을 수 없으면 0. */
    fun parse(value: String?): Long = value?.toLongOrNull()?.coerceIn(-MAX_MS, MAX_MS) ?: 0L

    /**
     * [text] 의 시각을 [shiftMs] 만큼 옮긴다. [extension] 은 점 없는 소문자(`srt`·`vtt`·`ass`·`ssa`). 다른 것이면 그대로 둔다.
     * 0 아래로 내려간 시각은 0 에 멈춘다(끝까지 0 이면 그 줄은 뜨지 않는다). 줄 끝(`\r\n`)은 그대로다.
     */
    fun apply(text: String, extension: String, shiftMs: Long): String {
        if (shiftMs == 0L) return text
        return when (extension) {
            "srt" -> shiftCueLines(text, shiftMs, ",")
            "vtt" -> shiftCueLines(text, shiftMs, ".")
            "ass", "ssa" -> shiftDialogues(text, shiftMs)
            else -> text
        }
    }

    // ---- SRT·VTT ----------------------------------------------------------------------

    /**
     * `00:01:02,345 --> 00:01:04,000` 의 한 시각. 시는 없을 수 있고(VTT·SRT 모두), **소수 자리도 없을 수 있다** — media3 의
     * `SubripParser`·`WebvttParserUtil` 이 둘 다 받는다(1.11.1 바이트코드로 확인). 소수는 있으면 **꼭 세 자리**다(둘 다 그 밖의
     * 줄을 버린다 — 고쳐 쓰지 않고 버려진 그대로 둔다). 시는 여섯 자리까지 — 터무니없는 자리 수가 `toLong` 을 넘쳐 열기가 죽지 않게.
     */
    private val CUE_TIME = Regex("""(?:(\d{1,6}):)?(\d{1,2}):(\d{1,2})(?:([,.])(\d{3}))?""")

    /** 시각 줄 — 앞에 시각, `-->`, 뒤에 시각. 글 줄에 우연히 든 `-->` 는 건드리지 않는다. */
    private val CUE_LINE = Regex("""^\s*${CUE_TIME.pattern}\s*-->\s*${CUE_TIME.pattern}""")

    /** [defaultSep] 는 소수 자리가 없던 시각에 붙일 구분자(SRT `,`·VTT `.`) — 옮기면 0.1초 자리가 생긴다. */
    private fun shiftCueLines(text: String, shiftMs: Long, defaultSep: String): String = mapLines(text) { line ->
        val m = CUE_LINE.find(line) ?: return@mapLines line
        // 시각 줄의 앞부분만 고친다 — 뒤의 VTT 설정(`position:10%` 따위)은 그대로 붙인다.
        val head = CUE_TIME.replace(line.substring(0, m.range.last + 1)) { t ->
            val (h, mi, s, sep, frac) = t.destructured
            val ms = ((h.ifEmpty { "0" }.toLong() * 60 + mi.toLong()) * 60 + s.toLong()) * 1000 + frac.ifEmpty { "0" }.toLong()
            formatCue((ms + shiftMs).coerceAtLeast(0), sep.ifEmpty { defaultSep })
        }
        head + line.substring(m.range.last + 1)
    }

    private fun formatCue(ms: Long, sep: String): String =
        // 기기의 로캘로 적지 않는다 — 아랍어·페르시아어 로캘이면 숫자가 아랍-인도 숫자가 되어 파서가 그 줄을 버린다.
        String.format(Locale.ROOT, "%02d:%02d:%02d%s%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, sep, ms % 1000)

    // ---- ASS·SSA ----------------------------------------------------------------------

    /**
     * `0:01:02.34` — 소수는 1/100초. 자리 수를 셈하지 않는다 — media3 의 `SsaParser` 가 자리 수와 상관없이 값 × 1/100초로 읽는다
     * (`.5` 는 0.05초 — 1.11.1 바이트코드로 확인). 화면이 본 시각에서 정확히 옮긴 만큼만 움직이려면 같게 읽어야 한다. 자리 수는
     * 위아래를 둬 `toLong` 을 넘치지 않게 한다.
     */
    private val ASS_TIME = Regex("""^\s*(\d{1,6}):(\d{1,2}):(\d{1,2})[.:](\d{1,9})\s*$""")

    /**
     * `Dialogue:` 줄의 시작·끝 칸을 옮긴다. 칸의 차례는 `[Events]` 의 `Format:` 줄이 정한다(media3 의 `SsaParser` 도 그렇게 읽는다) —
     * 없으면 표준 차례(Layer, Start, End, …)다. 마지막 칸(Text)은 쉼표를 품을 수 있어 칸 수만큼만 가른다.
     */
    private fun shiftDialogues(text: String, shiftMs: Long): String {
        var startAt = 1
        var endAt = 2
        var fields = 10
        return mapLines(text) { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("Format:", ignoreCase = true) -> {
                    val names = trimmed.substring("Format:".length).split(',').map { it.trim().lowercase() }
                    if ("start" in names && "end" in names) {
                        startAt = names.indexOf("start")
                        endAt = names.indexOf("end")
                        fields = names.size
                    }
                    line
                }
                trimmed.startsWith("Dialogue:", ignoreCase = true) -> {
                    val lead = line.length - trimmed.length + "Dialogue:".length
                    val parts = line.substring(lead).split(',', limit = fields).toMutableList()
                    if (parts.size <= maxOf(startAt, endAt)) return@mapLines line
                    val start = shiftAss(parts[startAt], shiftMs) ?: return@mapLines line
                    val end = shiftAss(parts[endAt], shiftMs) ?: return@mapLines line
                    parts[startAt] = start
                    parts[endAt] = end
                    line.substring(0, lead) + parts.joinToString(",")
                }
                else -> line
            }
        }
    }

    private fun shiftAss(field: String, shiftMs: Long): String? {
        val (h, m, s, frac) = ASS_TIME.find(field)?.destructured ?: return null
        // 소수는 자리 수와 상관없이 1/100초 단위다 — media3 가 그렇게 읽는다(`ASS_TIME`).
        val ms = ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + frac.toLong() * 10 + shiftMs
        val t = ms.coerceAtLeast(0)
        val lead = field.takeWhile { it == ' ' }
        return lead + String.format(Locale.ROOT, "%d:%02d:%02d.%02d", t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, t % 1000 / 10)
    }

    /**
     * 줄마다 바꾼다. 줄 끝은 `\n`·`\r\n`·홀로 선 `\r` 셋 다이고(media3 의 줄 읽기가 셋을 다 안다) 글자 그대로 둔다.
     */
    private inline fun mapLines(text: String, transform: (String) -> String): String {
        val out = StringBuilder(text.length + 64)
        var from = 0
        while (true) {
            var end = from
            while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
            out.append(transform(text.substring(from, end)))
            if (end >= text.length) break
            val eolEnd = if (text[end] == '\r' && end + 1 < text.length && text[end + 1] == '\n') end + 2 else end + 1
            out.append(text, end, eolEnd)
            from = eolEnd
        }
        return out.toString()
    }
}
