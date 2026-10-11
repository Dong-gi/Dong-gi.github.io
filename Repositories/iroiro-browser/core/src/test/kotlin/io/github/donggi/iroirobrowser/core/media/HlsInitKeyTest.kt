package io.github.donggi.iroirobrowser.core.media

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 잠긴(AES-128) fMP4 의 초기 조각(`#EXT-X-MAP`) — 그 태그가 나온 자리에서 걸려 있던 잠금을 함께 읽는다(RFC 8216 4.3.2.4·4.3.2.5).
 * 앞 판은 초기 조각을 늘 평문으로 보고 풀지 않아, 잠긴 초기 조각이면 `moov` 가 깨져 합치기·재생이 실패했다.
 */
class HlsInitKeyTest {

    @Test
    fun KEY_뒤의_MAP_은_그_잠금이_걸린다() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXT-X-KEY:METHOD=AES-128,URI="k.bin",IV=0x0F0E0D0C0B0A09080706050403020100
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4,
            s1.m4s
            #EXT-X-KEY:METHOD=AES-128,URI="k2.bin",IV=0x00000000000000000000000000000001
            #EXTINF:4,
            s2.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val m = assertIs<HlsPlaylist.Media>(HlsPlaylist.parse(text, "https://cdn.test/v/index.m3u8"))
        val init = assertNotNull(m.segments[0].map)
        val key = assertNotNull(init.key)
        assertEquals("https://cdn.test/v/k.bin", key.uri)
        assertContentEquals(ByteArray(16) { (15 - it).toByte() }, key.iv)
        // 뒤에서 열쇠가 바뀌어도 이미 선언된 초기 조각의 잠금은 그대로다(같은 초기 조각을 둘째 조각도 쓴다).
        assertSame(init, m.segments[1].map)
        assertEquals("https://cdn.test/v/k2.bin", m.segments[1].key!!.uri)
    }

    @Test
    fun KEY_앞의_MAP_은_잠기지_않았다() {
        // ffmpeg 의 fMP4 + 잠금은 MAP 을 먼저 적고 KEY 를 조각마다 적는다 — 초기 조각은 평문이다(풀면 오히려 깨진다).
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="k.bin"
            #EXTINF:4,
            s1.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val m = assertIs<HlsPlaylist.Media>(HlsPlaylist.parse(text, "https://cdn.test/v/index.m3u8"))
        assertNull(m.segments[0].map!!.key)
        assertNotNull(m.segments[0].key)
    }

    @Test
    fun METHOD_NONE_뒤의_MAP_은_잠기지_않았다() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="k.bin",IV=0x01
            #EXT-X-KEY:METHOD=NONE
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4,
            s1.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val m = assertIs<HlsPlaylist.Media>(HlsPlaylist.parse(text, "https://cdn.test/v/index.m3u8"))
        assertNull(m.segments[0].map!!.key)
    }

    @Test
    fun 잠긴_초기_조각은_그_열쇠와_IV_로_풀린다() {
        // 초기 조각에는 조각 번호가 없다 — RFC 는 IV 를 반드시 적으라고 한다. 적힌 IV 로 푼다(없으면 hls.js 처럼 0 — 받기가 그렇게 부른다).
        val secret = ByteArray(16) { (it * 11).toByte() }
        val iv = ByteArray(16) { (15 - it).toByte() }
        val plain = "ftyp....moov — 초기 조각이라고 치자".toByteArray()
        val sealed = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), IvParameterSpec(iv))
            doFinal(plain)
        }
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="k.bin",IV=0x0F0E0D0C0B0A09080706050403020100
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4,
            s1.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val key = (HlsPlaylist.parse(text, "https://cdn.test/v/index.m3u8") as HlsPlaylist.Media).segments[0].map!!.key!!
        assertContentEquals(plain, HlsCrypto.decrypt(sealed, secret, key.iv, sequence = 0))
    }
}
