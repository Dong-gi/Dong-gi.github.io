package io.github.donggi.iroiroviewer.playback

/**
 * 트랙 하나를 **사람이 고를 수 있는 모양**으로 정리한다. 순수 계산이라 JVM 시험으로 박힌다.
 *
 * ## 왜 문구를 여기서 만들지 않는가
 *
 * 이 파일이 내놓는 것은 [Descriptor] — **조각들**이다. '한국어 · 스테레오 · AAC' 라는
 * 한 줄을 만드는 일은 [trackLabel] 이 문자열 자원으로 한다. 문구를 여기에 박으면
 * 5단계가 실패 문구를 두 벌 두었다가 한쪽만 고쳐졌던 그 형태가 된다 — 게다가 언어 이름
 * (`kor` → '한국어')은 우리가 표를 만들 것이 아니라 플랫폼의 `Locale` 이 아는 것이다.
 *
 * ## 왜 media3 의 `Format` 을 받지 않는가
 *
 * `Format` 은 `androidx.media3.common` 의 타입이고, 이 모듈의 JVM 시험은 android.jar
 * 스텁 위에서 돈다. 그 타입을 시험에서 만들려 들면 `RuntimeException: Stub!` 을 만난다.
 * 그래서 [Info] 로 한 겹 옮기고, 옮기는 일만 [PlaybackConnection] 이 메인 스레드에서 한다.
 *
 * **`Format` 의 문자열 필드는 전부 `@Nullable` 이고 수 필드는 없으면 −1**(`Format.NO_VALUE`)
 * 이다. 로컬 mkv·mp4 에서 `label` 이 비어 있는 것은 드문 일이 아니라 기본이다.
 */
object TrackLabels {

    /** [PlaybackConnection] 이 `Format` 에서 그대로 옮겨 오는 값들. */
    data class Info(
        /** `Format.label`. 파일이 적어 둔 이름. */
        val label: String? = null,
        /** `Format.language`. ISO 639 또는 BCP-47. */
        val language: String? = null,
        /** `Format.channelCount`. 없으면 −1. */
        val channelCount: Int = -1,
        /** `Format.sampleMimeType`. 자막이면 대개 [MEDIA3_CUES] 다(아래 [originalMimeOf]). */
        val sampleMimeType: String? = null,
        /** `Format.codecs`. **자막의 원래 형식이 여기 들어 있다**([originalMimeOf]). */
        val codecs: String? = null,
        /** `Format.selectionFlags` 에 `SELECTION_FLAG_FORCED` 가 있는가. */
        val forced: Boolean = false,
    )

    /**
     * 화면이 한 줄로 조립할 조각들. **아무 조각도 없으면 [ordinal] 만 남는다** —
     * 그때 화면은 '소리 1'·'자막 2' 로 적는다. 빈 줄을 보여 주지 않으려는 마지막 안전망이다.
     */
    data class Descriptor(
        /** 파일이 적어 둔 이름. 공백뿐이면 null. */
        val name: String?,
        /** 언어 코드. 화면이 `Locale` 로 사람 말로 바꾼다. 모름(`und`)과 공백은 null. */
        val languageCode: String?,
        /** 채널 수. 모르면 null. 1·2·6 을 화면이 모노·스테레오·5.1 로 읽는다. */
        val channelCount: Int?,
        /** `AAC`·`SRT` 같은 짧은 이름. 모르면 null. */
        val codec: String?,
        /** 같은 종류 안에서 몇 번째인가. **1부터.** */
        val ordinal: Int,
        /** 강제 자막인가(대사 중 외국어만 나오는 그것). */
        val forced: Boolean,
    )

    /**
     * **자막 트랙의 원래 형식을 되찾는다.**
     *
     * media3 는 자막을 뽑는 길에서 파서를 한 번 지나게 하고, 그때
     * `SubtitleTranscodingTrackOutput.format()` 이 `sampleMimeType` 을 [MEDIA3_CUES] 로
     * 갈아 끼우면서 **원래 MIME 을 `codecs` 로 옮긴다**(바이트코드로 확인했다).
     *
     * 이것을 모르면 두 가지가 한꺼번에 조용히 망가진다.
     *
     * * **그림 자막이 걸러지지 않는다.** 거르는 조건이 `sampleMimeType` 을 보는데 그 값이
     *   언제나 [MEDIA3_CUES] 라 **절대 참이 되지 않는다.** PGS·VobSub 도 파서를 지나므로
     *   (`DefaultSubtitleParserFactory.supportsFormat` 에 셋이 다 들어 있다) 목록에 올라와,
     *   고르면 표시만 옮겨 가고 화면에는 아무 글자도 뜨지 않는다.
     * * **자막 줄에 형식을 적을 수 없다.** `SRT`·`ASS` 대신 쓸 것이 없어진다.
     *
     * 변환을 거치지 않은 트랙(소리·영상)은 `sampleMimeType` 이 그대로이므로 그것을 쓴다.
     */
    fun originalMimeOf(sampleMimeType: String?, codecs: String?): String? =
        if (sampleMimeType?.lowercase() == MEDIA3_CUES) codecs ?: sampleMimeType else sampleMimeType

    /** 그림으로 된 자막인가. **우리는 그리지 않는다** — 고를 수 있게 두면 빈 화면이 된다. */
    fun isPictureSubtitle(mimeType: String?): Boolean = when (mimeType?.lowercase()) {
        "application/pgs", "application/vobsub", "application/dvbsubs" -> true
        else -> false
    }

    /**
     * MIME 을 짧은 이름으로. 아는 것만 이름을 주고, 모르면 **빗금 뒤를 대문자로** 쓴다.
     *
     * 모르는 것에 `null` 을 주지 않는 이유는, 이름이 하나도 없는 트랙에서 코덱이라도
     * 보이는 편이 '소리 2' 보다 많은 것을 말해 주기 때문이다.
     */
    fun codecOf(mimeType: String?): String? {
        val mime = mimeType?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        // **media3 의 속살은 보여 주지 않는다.** [originalMimeOf] 를 거치고도 이 값이
        // 남아 있다면 `codecs` 마저 비어 원래 형식을 알 수 없다는 뜻이다. 그대로 적으면
        // 화면에 `MEDIA3-CUES` 가 뜬다(고치기 전에 기기에서 실제로 봤다).
        if (mime in HIDDEN) return null
        KNOWN[mime]?.let { return it }
        val tail = mime.substringAfter('/', "").removePrefix("x-").takeIf { it.isNotBlank() } ?: return null
        return tail.uppercase()
    }

    /**
     * 조각을 정리한다. **빈 문자열과 −1 을 여기서 전부 null 로 바꾼다** — 그러지 않으면
     * 화면이 '−1 채널' 이나 이름 없는 빈 칸을 그린다.
     *
     * @param ordinal 같은 종류 안에서 몇 번째인가. 1부터.
     */
    fun describe(info: Info, ordinal: Int): Descriptor = Descriptor(
        name = info.label?.trim()?.takeIf { it.isNotEmpty() },
        // `und`(undetermined)는 '모른다' 를 적어 둔 것이지 언어 이름이 아니다.
        // `mul`(multiple)·`zxx`(언어 없음)도 사람에게 보여 줄 말이 아니다.
        languageCode = info.language?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() && it != "und" && it != "mul" && it != "zxx" },
        channelCount = info.channelCount.takeIf { it > 0 },
        codec = codecOf(originalMimeOf(info.sampleMimeType, info.codecs)),
        ordinal = ordinal,
        forced = info.forced,
    )

    /** media3 가 자막을 뽑으며 씌우는 내부 컨테이너. 위 [originalMimeOf] 참고. */
    const val MEDIA3_CUES = "application/x-media3-cues"

    /** 사람에게 보여 줄 것이 없는 MIME. 위 [codecOf] 의 주석 참고. */
    private val HIDDEN = setOf(MEDIA3_CUES)

    private val KNOWN = mapOf(
        "audio/mp4a-latm" to "AAC",
        "audio/mpeg" to "MP3",
        "audio/mpeg-l2" to "MP2",
        "audio/opus" to "Opus",
        "audio/vorbis" to "Vorbis",
        "audio/flac" to "FLAC",
        "audio/raw" to "PCM",
        "audio/ac3" to "AC-3",
        "audio/eac3" to "E-AC-3",
        "audio/eac3-joc" to "E-AC-3",
        "audio/true-hd" to "TrueHD",
        "audio/vnd.dts" to "DTS",
        "audio/vnd.dts.hd" to "DTS-HD",
        "audio/alac" to "ALAC",
        "application/x-subrip" to "SRT",
        "text/x-ssa" to "ASS",
        "text/vtt" to "WebVTT",
        "application/ttml+xml" to "TTML",
        "application/x-quicktime-tx3g" to "tx3g",
        "application/cea-608" to "CEA-608",
        "application/cea-708" to "CEA-708",
    )
}
