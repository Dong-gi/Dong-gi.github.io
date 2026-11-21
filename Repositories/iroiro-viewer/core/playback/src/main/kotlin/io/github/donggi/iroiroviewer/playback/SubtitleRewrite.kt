package io.github.donggi.iroiroviewer.playback

/**
 * 인코딩을 UTF-8 로 맞춘 자막 바이트를 **파서에 넘기기 전에 한 번 고쳐 쓰는 한 곳**([SubtitleDataSourceFactory] 가 부른다).
 *
 * 차례가 요점이다 — SAMI 면 먼저 SRT 로 바꾸고([SamiToSrt]), 싱크([SubtitleShift])는 **그 SRT 에** 건다. 그래서 싱크를 옮기는
 * 코드가 SAMI 를 따로 알 필요가 없고, `.smi` 에도 같은 ±0.1초가 듣는다. 둘 다 할 일이 없으면 바이트를 건드리지 않는다(다시
 * 인코딩하면 잘못된 바이트가 `U+FFFD` 로 바뀐다).
 *
 * android.* 를 쓰지 않는다 — JVM 시험이 답한다.
 */
object SubtitleRewrite {

    /**
     * @param extension 점 없는 소문자 확장자([SubtitleNames.extensionOf]).
     * @param isCancelled 참이 되면 SAMI 바꾸기가 `CancellationException` 을 던진다.
     */
    fun apply(utf8: ByteArray, extension: String, shiftMs: Long, isCancelled: () -> Boolean = { false }): ByteArray {
        val sami = extension in SubtitleNames.SAMI_EXTENSIONS
        if (!sami && shiftMs == 0L) return utf8
        var text = String(utf8, Charsets.UTF_8)
        var format = extension
        if (sami) {
            if (SamiToSrt.looksLikeSami(text)) {
                text = SamiToSrt.convert(text, isCancelled)
            } else if (shiftMs == 0L) {
                // SAMI 가 아니다(`<SYNC` 없음 — 이름만 `.smi` 인 SRT). 바꿀 것도 옮길 것도 없으면 바이트 그대로 — 디코딩을 거치면
                // 잘못된 바이트가 U+FFFD 로 바뀐다.
                return utf8
            }
            // 그 SRT 도 SRT 로 보고 옮긴다.
            format = "srt"
        }
        if (shiftMs != 0L) text = SubtitleShift.apply(text, format, shiftMs)
        return text.toByteArray(Charsets.UTF_8)
    }
}
