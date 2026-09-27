package io.github.donggi.iroiroviewer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 트랙 조각 정리.
 *
 * `Format` 의 문자열은 전부 nullable 이고 수는 없으면 −1 이다. **모든 칸이 비어 있는
 * 트랙**이 이 파일이 지키는 경계다 — 그때 화면이 '−1 채널' 이나 빈 줄을 그리면 안 된다.
 */
class TrackLabelsTest {

    @Test
    fun 모든_칸이_비면_번호만_남는다() {
        val d = TrackLabels.describe(TrackLabels.Info(), ordinal = 2)
        assertNull(d.name)
        assertNull(d.languageCode)
        assertNull(d.channelCount)
        assertNull(d.codec)
        assertEquals(2, d.ordinal)
        assertFalse(d.forced)
    }

    @Test
    fun 공백과_음수는_전부_null_이_된다() {
        val d = TrackLabels.describe(
            TrackLabels.Info(label = "   ", language = "  ", channelCount = -1, sampleMimeType = ""),
            ordinal = 1,
        )
        assertNull(d.name)
        assertNull(d.languageCode)
        assertNull(d.channelCount)
        assertNull(d.codec)
    }

    @Test
    fun 모름을_뜻하는_언어_코드는_보여_주지_않는다() {
        for (code in listOf("und", "UND", "mul", "zxx")) {
            assertNull(
                TrackLabels.describe(TrackLabels.Info(language = code), 1).languageCode,
                "$code 는 언어 이름이 아니라 '모른다' 는 표시다",
            )
        }
        assertEquals("kor", TrackLabels.describe(TrackLabels.Info(language = "KOR"), 1).languageCode)
    }

    @Test
    fun 있는_값은_그대로_옮긴다() {
        val d = TrackLabels.describe(
            TrackLabels.Info(
                label = " 한국어 더빙 ",
                language = "ko",
                channelCount = 6,
                sampleMimeType = "audio/eac3",
                forced = true,
            ),
            ordinal = 1,
        )
        assertEquals("한국어 더빙", d.name)
        assertEquals("ko", d.languageCode)
        assertEquals(6, d.channelCount)
        assertEquals("E-AC-3", d.codec)
        assertTrue(d.forced)
    }

    @Test
    fun 아는_코덱은_짧은_이름으로() {
        assertEquals("AAC", TrackLabels.codecOf("audio/mp4a-latm"))
        assertEquals("FLAC", TrackLabels.codecOf("audio/flac"))
        assertEquals("SRT", TrackLabels.codecOf("application/x-subrip"))
        assertEquals("ASS", TrackLabels.codecOf("text/x-ssa"))
        assertEquals("WebVTT", TrackLabels.codecOf("text/vtt"))
    }

    @Test
    fun 모르는_코덱은_빗금_뒤를_대문자로() {
        assertEquals("SOMETHING", TrackLabels.codecOf("audio/x-something"))
        assertEquals("WEIRD", TrackLabels.codecOf("application/weird"))
        assertNull(TrackLabels.codecOf(null))
        assertNull(TrackLabels.codecOf("  "))
        assertNull(TrackLabels.codecOf("audio/"), "빗금 뒤가 비면 이름이 없다")
    }

    @Test
    fun media3_의_속살은_보여_주지_않는다() {
        // 자막 트랙은 파서를 지나면 이 MIME 이 되어, 그대로 적으면 `MEDIA3-CUES` 가 뜬다.
        assertNull(TrackLabels.codecOf(TrackLabels.MEDIA3_CUES))
        // `codecs` 마저 비면 원래 형식을 알 길이 없다 — 그때도 아무것도 적지 않는다.
        assertNull(TrackLabels.describe(TrackLabels.Info(sampleMimeType = TrackLabels.MEDIA3_CUES), 1).codec)
    }

    @Test
    fun 자막의_원래_형식은_codecs_에_있다() {
        // media3 가 `sampleMimeType` 을 내부 컨테이너로 갈아 끼우면서 원래 MIME 을
        // `codecs` 로 옮긴다(바이트코드로 확인했다).
        assertEquals(
            "application/x-subrip",
            TrackLabels.originalMimeOf(TrackLabels.MEDIA3_CUES, "application/x-subrip"),
        )
        // 변환을 거치지 않은 트랙(소리·영상)은 `sampleMimeType` 이 그대로다.
        assertEquals("audio/mp4a-latm", TrackLabels.originalMimeOf("audio/mp4a-latm", null))
        // 원래 형식을 알면 자막 줄에 `SRT` 를 적을 수 있다.
        val d = TrackLabels.describe(
            TrackLabels.Info(sampleMimeType = TrackLabels.MEDIA3_CUES, codecs = "application/x-subrip"),
            1,
        )
        assertEquals("SRT", d.codec)
    }

    @Test
    fun 그림_자막은_변환된_뒤에도_걸러진다() {
        // **이 시험이 없어서 한 번 놓쳤다.** 거르는 조건이 `sampleMimeType` 을 보고 있었는데
        // 그 값은 언제나 내부 컨테이너라 **절대 참이 되지 않았다** — PGS·VobSub 도 파서를
        // 지나기 때문이다(`DefaultSubtitleParserFactory.supportsFormat` 에 셋이 다 있다).
        for (mime in listOf("application/pgs", "application/vobsub", "application/dvbsubs")) {
            assertTrue(
                TrackLabels.isPictureSubtitle(TrackLabels.originalMimeOf(TrackLabels.MEDIA3_CUES, mime)),
                "$mime 가 변환된 뒤에도 그림 자막으로 잡혀야 한다",
            )
        }
        assertFalse(
            TrackLabels.isPictureSubtitle(
                TrackLabels.originalMimeOf(TrackLabels.MEDIA3_CUES, "application/x-subrip")
            )
        )
    }

    @Test
    fun 그림_자막은_따로_가른다() {
        assertTrue(TrackLabels.isPictureSubtitle("application/pgs"))
        assertTrue(TrackLabels.isPictureSubtitle("application/vobsub"))
        assertTrue(TrackLabels.isPictureSubtitle("application/dvbsubs"))
        assertFalse(TrackLabels.isPictureSubtitle("application/x-subrip"))
        assertFalse(TrackLabels.isPictureSubtitle(null))
    }
}
