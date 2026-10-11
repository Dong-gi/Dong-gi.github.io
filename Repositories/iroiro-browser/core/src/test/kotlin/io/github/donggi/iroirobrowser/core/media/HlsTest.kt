package io.github.donggi.iroirobrowser.core.media

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlsTest {

    @Test
    fun 마스터() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="한국어, 기본",LANGUAGE="ko",DEFAULT=YES,URI="audio/ko.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="aud"
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            https://cdn.test/hi/index.m3u8
        """.trimIndent()
        val m = assertIs<HlsPlaylist.Master>(HlsPlaylist.parse(text, "https://site.test/v/master.m3u8"))
        assertEquals(listOf(2_500_000L, 800_000L), m.variants.map { it.bandwidth })
        assertEquals("https://cdn.test/hi/index.m3u8", m.variants[0].uri)
        assertEquals("https://site.test/v/low/index.m3u8", m.variants[1].uri)
        assertEquals("640x360", m.variants[1].resolution)
        assertEquals("aud", m.variants[1].audioGroup)
        assertEquals("1280x720 · 2.5 Mbps", m.variants[0].label)
        assertEquals(1, m.audio.size)
        assertEquals("한국어, 기본", m.audio[0].name)
        assertEquals("https://site.test/v/audio/ko.m3u8", m.audio[0].uri)
        assertTrue(m.audio[0].isDefault)
    }

    @Test
    fun 미디어_조각_잠금_끝() {
        val text = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:7
            #EXTINF:6.000,
            seg7.ts
            #EXT-X-KEY:METHOD=AES-128,URI="../keys/k1.bin",IV=0x000102030405060708090A0B0C0D0E0F
            #EXTINF:5.5,
            seg8.ts
            #EXT-X-KEY:METHOD=AES-128,URI="../keys/k2.bin"
            #EXT-X-DISCONTINUITY
            #EXTINF:4,title
            /abs/seg9.ts?token=a,b
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:2,
            seg10.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val m = assertIs<HlsPlaylist.Media>(HlsPlaylist.parse(text, "https://cdn.test/v/hi/index.m3u8"))
        assertEquals(4, m.segments.size)
        assertTrue(m.ended)
        assertEquals(17.5, m.totalDuration)
        assertEquals(listOf(7L, 8L, 9L, 10L), m.segments.map { it.sequence })
        assertEquals("https://cdn.test/v/hi/seg7.ts", m.segments[0].uri)
        assertEquals("https://cdn.test/abs/seg9.ts?token=a,b", m.segments[2].uri)
        assertNull(m.segments[0].key)
        val k1 = m.segments[1].key!!
        assertEquals("https://cdn.test/v/keys/k1.bin", k1.uri)
        assertTrue(k1.isAes128)
        assertContentEquals(ByteArray(16) { it.toByte() }, k1.iv)
        assertNull(m.segments[2].key!!.iv)
        assertTrue(m.segments[2].discontinuity)
        assertNull(m.segments[3].key)
        assertEquals(setOf("AES-128"), m.encryption)
        assertFalse(m.fragmentedMp4)
    }

    @Test
    fun 실시간_방송과_fMP4와_바이트_범위() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:4,
            #EXT-X-BYTERANGE:1000@720
            all.mp4
            #EXTINF:4,
            #EXT-X-BYTERANGE:2000
            all.mp4
        """.trimIndent()
        val m = assertIs<HlsPlaylist.Media>(HlsPlaylist.parse(text, "https://cdn.test/f/index.m3u8"))
        assertFalse(m.ended)
        assertTrue(m.fragmentedMp4)
        assertEquals("https://cdn.test/f/init.mp4", m.segments[0].map!!.uri)
        assertEquals(720L, m.segments[0].map!!.byteRange!!.length)
        assertEquals(720L, m.segments[0].byteRange!!.offset)
        assertEquals(1000L, m.segments[0].byteRange!!.length)
        // 시작이 없으면 앞 조각의 끝에서 잇는다.
        assertEquals(1720L, m.segments[1].byteRange!!.offset)
        assertEquals(2000L, m.segments[1].byteRange!!.length)
    }

    @Test
    fun 재생목록이_아니면() {
        assertFailsWith<HlsPlaylist.Invalid> { HlsPlaylist.parse("<html>login</html>", "https://x.test/a.m3u8") }
    }

    @Test
    fun 속성_목록() {
        val a = HlsPlaylist.attributes("""BANDWIDTH=1,CODECS="a,b",NAME="x=y",DEFAULT=YES""")
        assertEquals("1", a["BANDWIDTH"])
        assertEquals("a,b", a["CODECS"])
        assertEquals("x=y", a["NAME"])
        assertEquals("YES", a["DEFAULT"])
    }

    @Test
    fun AES128_풀기() {
        val key = ByteArray(16) { (it * 7).toByte() }
        val plain = "TS 조각 하나 — 188 바이트 묶음이라고 치자".toByteArray()
        fun encrypt(iv: ByteArray): ByteArray = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(plain)
        }
        val iv = ByteArray(16) { 3 }
        assertContentEquals(plain, HlsCrypto.decrypt(encrypt(iv), key, iv, sequence = 0))
        // IV 가 없으면 조각 번호.
        assertContentEquals(plain, HlsCrypto.decrypt(encrypt(HlsCrypto.ivFor(300)), key, null, sequence = 300))
        assertEquals(1, HlsCrypto.ivFor(300)[14].toInt())
        assertEquals(44, HlsCrypto.ivFor(300)[15].toInt())
    }

    @Test
    fun 미디어_가리기() {
        assertEquals(MediaKind.HLS, MediaSniffer.classify("https://cdn.test/v/master.m3u8?token=1"))
        assertEquals(MediaKind.DASH, MediaSniffer.classify("https://cdn.test/v/manifest.mpd"))
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://cdn.test/clip.MP4#t=10"))
        assertEquals(MediaKind.AUDIO, MediaSniffer.classify("https://cdn.test/a/song.m4a"))
        assertNull(MediaSniffer.classify("https://cdn.test/v/seg7.ts"))
        assertNull(MediaSniffer.classify("https://cdn.test/v/chunk.m4s"))
        assertNull(MediaSniffer.classify("https://cdn.test/app.js"))
        assertNull(MediaSniffer.classify("blob:https://cdn.test/abc"))
        assertEquals("clip.MP4", MediaSniffer.displayName("https://cdn.test/clip.MP4?x=1"))
        assertEquals("cdn.test", MediaSniffer.displayName("https://cdn.test/"))
    }

    @Test
    fun 마스터_아래의_재생목록_모두와_평균_대역폭() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="한국어",DEFAULT=YES,URI="a/ko.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="묶인 소리",DEFAULT=NO
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="s",NAME="자막",LANGUAGE="ko",URI="s/ko.m3u8"
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="v",NAME="다른 각도",URI="angle/index.m3u8"
            #EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID="cc",NAME="CC",INSTREAM-ID="CC1"
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=1800000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2",AUDIO="a"
            720/index.m3u8
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=90000,URI="720/iframe.m3u8"
        """.trimIndent()
        val m = assertIs<HlsPlaylist.Master>(HlsPlaylist.parse(text, "https://cdn.test/v/master.m3u8"))
        assertEquals(1_800_000L, m.variants[0].averageBandwidth)
        assertEquals(2, m.audio.size)
        assertEquals("자막", m.subtitles.single().name)
        assertEquals(
            setOf(
                "https://cdn.test/v/720/index.m3u8", "https://cdn.test/v/a/ko.m3u8", "https://cdn.test/v/s/ko.m3u8",
                "https://cdn.test/v/angle/index.m3u8", "https://cdn.test/v/720/iframe.m3u8",
            ),
            m.childUris,
        )
    }

    @Test
    fun 번호만_다른_파일의_열쇠() {
        assertEquals("https://cdn.test/v/seg-#.mp4", MediaSniffer.seriesKey("https://cdn.test/v/seg-12.mp4?token=a#t=1"))
        assertEquals(MediaSniffer.seriesKey("https://cdn.test/v/seg-1.mp4"), MediaSniffer.seriesKey("https://cdn.test/v/seg-2.mp4?x=1"))
        // 마지막 숫자 묶음만 — 화질 이름의 숫자는 그대로.
        assertEquals("https://cdn.test/v/video_1080p_#.mp4", MediaSniffer.seriesKey("https://cdn.test/v/video_1080p_00012.mp4"))
        assertNull(MediaSniffer.seriesKey("https://cdn.test/v/clip.mp4"))
        // 경로의 숫자는 이름이 아니다.
        assertNull(MediaSniffer.seriesKey("https://cdn.test/2024/clip.mp4"))
    }

    @Test
    fun 같은_미디어의_열쇠() {
        assertEquals("https://cdn.test/v/clip.mp4?x=1", MediaSniffer.key("https://cdn.test/v/clip.mp4?x=1#t=10"))
        // 저지연 HLS 의 전달 지시는 뗀다 — 다시 받을 때마다 바뀐다.
        assertEquals("https://cdn.test/v/index.m3u8", MediaSniffer.key("https://cdn.test/v/index.m3u8?_HLS_msn=123&_HLS_part=2"))
        assertEquals("https://cdn.test/v/index.m3u8?token=a", MediaSniffer.key("https://cdn.test/v/index.m3u8?_HLS_msn=9&token=a&_HLS_skip=YES"))
        assertEquals("https://cdn.test/v/index.m3u8?token=a", MediaSniffer.key("https://cdn.test/v/index.m3u8?token=a"))
    }

    @Test
    fun 페이지_제목으로_이름() {
        assertEquals("고양이 영상 - 사이트.mp4", MediaSniffer.nameFromTitle("고양이 영상 - 사이트", "https://cdn.test/3f9a1c.mp4?x=1"))
        assertEquals("a_b.m4a", MediaSniffer.nameFromTitle("a/b", "https://cdn.test/x.M4A"))
        assertNull(MediaSniffer.nameFromTitle("  ", "https://cdn.test/x.mp4"))
        assertNull(MediaSniffer.nameFromTitle("///", "https://cdn.test/x.mp4"))
    }

    @Test
    fun 코덱_이름과_어림한_크기() {
        assertEquals("H.264/AAC", MediaCodecs.describe("avc1.64001f,mp4a.40.2"))
        assertEquals("VP9/Opus", MediaCodecs.describe("vp09.00.10.08, opus"))
        assertEquals("H.265", MediaCodecs.describe("hvc1.1.6.L93.B0,stpp.ttml.im1t"))
        assertEquals("AAC", MediaCodecs.describe("mp4a.40.5"))
        assertEquals("xyz", MediaCodecs.describe("xyz.1"))
        assertNull(MediaCodecs.describe(null))
        assertNull(MediaCodecs.describe("wvtt"))
        assertEquals(true, MediaCodecs.hasVideo("avc1.4d401f,mp4a.40.2"))
        assertEquals(false, MediaCodecs.hasVideo("mp4a.40.2"))
        assertNull(MediaCodecs.hasVideo(""))
        // 2.5 Mbps × 60초 = 18,750,000 바이트.
        assertEquals(18_750_000L, MediaCodecs.estimateBytes(2_500_000, 60.0))
        assertNull(MediaCodecs.estimateBytes(0, 60.0))
        assertNull(MediaCodecs.estimateBytes(2_500_000, 0.0))
    }
}
