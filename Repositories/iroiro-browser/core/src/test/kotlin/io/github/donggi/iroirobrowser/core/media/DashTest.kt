package io.github.donggi.iroirobrowser.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashTest {

    private val vod = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT0M12.0S" minBufferTime="PT2S">
          <BaseURL>media/</BaseURL>
          <Period>
            <AdaptationSet mimeType="video/mp4" contentType="video">
              <SegmentTemplate timescale="1000" duration="4000" startNumber="1" initialization="${'$'}RepresentationID${'$'}/init.mp4" media="${'$'}RepresentationID${'$'}/seg${'$'}Number%03d${'$'}.m4s"/>
              <Representation id="v1" bandwidth="800000" width="640" height="360" codecs="avc1.4d401e"/>
              <Representation id="v2" bandwidth="2500000" width="1280" height="720" codecs="avc1.4d401f"/>
            </AdaptationSet>
            <AdaptationSet mimeType="audio/mp4" lang="ko">
              <Representation id="a" bandwidth="128000" codecs="mp4a.40.2">
                <SegmentTemplate timescale="48000" initialization="a/init.mp4" media="a/${'$'}Time${'$'}.m4s">
                  <SegmentTimeline><S t="0" d="192000" r="2"/></SegmentTimeline>
                </SegmentTemplate>
              </Representation>
            </AdaptationSet>
            <AdaptationSet mimeType="text/vtt"><Representation id="sub" bandwidth="100"/></AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    @Test
    fun 화질과_조각_주소() {
        val m = DashManifest.parse(vod, "https://cdn.test/dash/vod.mpd")
        assertFalse(m.dynamic)
        assertEquals(12.0, m.duration)
        assertEquals(listOf("v2", "v1"), m.videos.map { it.id })
        val v2 = m.videos[0]
        assertEquals("1280x720 · 2.5 Mbps", v2.label)
        assertEquals("https://cdn.test/dash/media/v2/init.mp4", v2.init!!.url)
        assertEquals(
            listOf("https://cdn.test/dash/media/v2/seg001.m4s", "https://cdn.test/dash/media/v2/seg002.m4s", "https://cdn.test/dash/media/v2/seg003.m4s"),
            v2.segments.map { it.url },
        )
        val a = m.audios.single()
        assertEquals("ko · 128 kbps", a.label)
        assertEquals(listOf("0", "192000", "384000"), a.segments.map { it.url.substringAfterLast('/').substringBefore('.') })
        assertFalse(a.protected)
    }

    @Test
    fun 조각_목록과_파일_하나() {
        val text = """
            <MPD type="static" mediaPresentationDuration="PT8S">
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <Representation id="list" bandwidth="1000">
                    <BaseURL>https://other.test/all.mp4</BaseURL>
                    <SegmentList>
                      <Initialization range="0-799"/>
                      <SegmentURL mediaRange="800-1799"/>
                      <SegmentURL mediaRange="1800-2999"/>
                    </SegmentList>
                  </Representation>
                  <Representation id="whole" bandwidth="500">
                    <BaseURL>whole.mp4</BaseURL>
                    <SegmentBase indexRange="0-100"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val m = DashManifest.parse(text, "https://cdn.test/d/x.mpd")
        val list = m.videos.first { it.id == "list" }
        assertEquals(0L, list.init!!.offset)
        assertEquals(800L, list.init!!.length)
        assertEquals(listOf(800L to 1000L, 1800L to 1200L), list.segments.map { it.offset to it.length })
        assertTrue(list.segments.all { it.url == "https://other.test/all.mp4" })
        val whole = m.videos.first { it.id == "whole" }
        assertNull(whole.init)
        assertEquals("https://cdn.test/d/whole.mp4", whole.segments.single().url)
        assertNull(whole.segments.single().offset)
    }

    @Test
    fun 실시간과_DRM_표시() {
        val live = DashManifest.parse(
            """<MPD type="dynamic"><Period><AdaptationSet contentType="video"><SegmentTemplate media="s${'$'}Number${'$'}.m4s" duration="2" timescale="1"/><Representation id="v" bandwidth="1"/></AdaptationSet></Period></MPD>""".let {
                it.replace("<MPD type=\"dynamic\">", "<MPD type=\"dynamic\" mediaPresentationDuration=\"PT4S\">")
            },
            "https://x.test/l.mpd",
        )
        assertTrue(live.dynamic)
        val drm = DashManifest.parse(
            """<MPD type="static" mediaPresentationDuration="PT2S"><Period><AdaptationSet contentType="video"><ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/><Representation id="v" bandwidth="1"><BaseURL>v.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>""",
            "https://x.test/d.mpd",
        )
        assertTrue(drm.videos.single().protected)
    }

    @Test
    fun DOCTYPE_은_읽지_않는다() {
        assertFailsWith<DashManifest.Invalid> {
            DashManifest.parse("""<?xml version="1.0"?><!DOCTYPE MPD [<!ENTITY x SYSTEM "file:///etc/passwd">]><MPD>&x;</MPD>""", "https://x.test/a.mpd")
        }
        assertFailsWith<DashManifest.Invalid> { DashManifest.parse("<html>not dash</html>", "https://x.test/a.mpd") }
    }

    @Test
    fun 기간과_틀_채우기() {
        assertEquals(3723.5, DashManifest.isoDuration("PT1H2M3.5S"))
        assertEquals(93600.0, DashManifest.isoDuration("P1DT2H"))
        assertNull(DashManifest.isoDuration("1:00"))
        assertEquals("v1/seg007-48000.m4s", DashManifest.fill("${'$'}RepresentationID${'$'}/seg${'$'}Number%03d${'$'}-${'$'}Time${'$'}.m4s", "v1", "1", 7, 48000))
        assertEquals("a${'$'}b-500", DashManifest.fill("a${'$'}${'$'}b-${'$'}Bandwidth${'$'}", "x", "500", null, null))
    }
}
