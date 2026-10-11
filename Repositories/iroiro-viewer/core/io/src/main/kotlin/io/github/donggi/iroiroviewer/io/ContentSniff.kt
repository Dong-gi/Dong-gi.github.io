package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.charset.BinarySniffer
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * 확장자로는 갈리지 않는 파일을 **앞머리로** 가른다.
 *
 * `.ts` 는 MPEG-TS(방송 녹화·IPTV)와 TypeScript 가 함께 쓰고, `.mts` 는 캠코더의 AVCHD(M2TS)와 TypeScript 의 ES 모듈이
 * 함께 쓴다. 목록은 파일을 열지 않으므로(`MimeResolver` 의 머리말) 이름대로 적고(`.ts` 는 영상, `.mts` 는 기타 — media3 가 M2TS 를
 * 틀지 못한다), 목록이 선 뒤와 **누를 때** 여기서 가른다 — 글이면 텍스트 뷰어로 간다. 판단은 순수 함수([isText])라 JVM 시험이 박는다.
 *
 * ## 글로 보내는 것은 텍스트 뷰어가 받는 것뿐이다
 *
 * TypeScript 는 언제나 글이다. 그래서 **글로 보이는 것만** 글로 보내고 나머지는 이름대로 영상 쪽에 둔다. 글인지는 텍스트 뷰어와
 * **같은 판정기·같은 앞머리 길이**([BinarySniffer])가 정한다 — 다르게 정하면 우리가 글로 보낸 파일을 텍스트 뷰어가 '글이 아닌
 * 파일' 로 돌려보낸다. 영상 쪽은 이진 파일을 정직하게 다룬다 — 재생하지 못하면 그렇다고 말하고 '다른 앱으로 열기' 를 둔다.
 *
 * 처음에는 거꾸로 짰다 — **패킷이 0번 자리부터 셋 이어져야** 영상이고 나머지는 전부 글이었다. 그러면 패킷 한가운데서 시작하는
 * 녹화(잘라 낸 방송 녹화가 그렇다)가 텍스트 뷰어로 가서 '글이 아닌 파일' 로 끝난다. media3 는 그런 파일을 튼다(아래 [isMpegTs]).
 *
 * ## 패킷 무늬를 먼저 보는 까닭
 *
 * 판정기는 널 바이트로 이진을 가른다. 널 패킷(PID 0x1FFF, 채움 `0xFF`)만 이어진 앞머리에는 널이 없어 판정기가 **글로 본다** —
 * 그래서 패킷 무늬가 맞으면 판정기에 묻지 않고 영상이다.
 */
object ContentSniff {

    /** 확장자를 TypeScript 와 나눠 쓰는 영상. 소문자, 점 없음. */
    private val AMBIGUOUS_STREAM = setOf("ts", "mts")

    private const val SYNC: Byte = 0x47
    private const val TS_PACKET = 188

    /** 캠코더(AVCHD)·블루레이의 M2TS — 188바이트 패킷 앞에 4바이트 시각이 붙는다. */
    private const val M2TS_PACKET = 192

    /** 동기 바이트가 맞아야 하는 패킷 수. media3 의 `TsExtractor.sniff` 와 같다(1.11.1 바이트코드 — 다섯). */
    private const val PACKETS = 5

    /** 판별에 읽는 앞머리 — 텍스트 뷰어가 이진을 가르는 길이와 같다. 패킷 무늬(첫 패킷 안의 시작 + 다섯 패킷)도 이 안에 든다. */
    const val HEAD_BYTES = BinarySniffer.SNIFF_BYTES

    /** 이름만으로는 영상인지 글인지 모르는 파일인가(`.ts`·`.mts`). */
    fun isAmbiguousStream(name: String): Boolean = MimeResolver.extensionOf(name) in AMBIGUOUS_STREAM

    /**
     * 앞 [length] 바이트에 MPEG-TS(188) 또는 M2TS(192) 패킷이 [PACKETS] 개 이어지는가. **첫 패킷 안 어느 자리에서 시작해도
     * 된다** — 녹화를 자르면 패킷 한가운데서 시작한다. media3 의 `TsExtractor.sniff` 도 첫 188바이트 안에서 시작 자리를 찾는다.
     * (media3 는 192바이트 패킷을 모른다 — M2TS 는 영상 쪽으로 보내되 재생 화면이 '재생하지 못하는 형식' 을 말한다.)
     */
    fun isMpegTs(head: ByteArray, length: Int = head.size): Boolean {
        val n = length.coerceIn(0, head.size)
        return syncs(head, n, TS_PACKET) || syncs(head, n, M2TS_PACKET)
    }

    private fun syncs(head: ByteArray, length: Int, packet: Int): Boolean {
        start@ for (start in 0 until packet) {
            for (i in 0 until PACKETS) {
                val at = start + i * packet
                // 이 시작에서 모자라면 뒤의 시작은 더 모자라다.
                if (at >= length) return false
                if (head[at] != SYNC) continue@start
            }
            return true
        }
        return false
    }

    /**
     * 글로 열어야 하는가 — 패킷으로 이어지지 않고([isMpegTs]) 텍스트 뷰어의 판정기가 글로 본다. 빈 파일은 글이다(판정기가
     * 그렇게 본다 — 텍스트 뷰어가 빈 화면으로 연다).
     */
    fun isText(head: ByteArray, length: Int = head.size): Boolean {
        val n = length.coerceIn(0, head.size)
        return !isMpegTs(head, n) && BinarySniffer.sniff(head, n) is BinarySniffer.Verdict.Text
    }

    /**
     * 파일을 열어 앞머리를 본다 — **디스크 입출력이다**(부르는 쪽이 입출력 디스패처에서 부른다. 다른 앱으로 열기처럼 주 스레드의
     * 한 번은 읽는 양이 [HEAD_BYTES] 라 받아들였다).
     *
     * @return 영상 쪽이면 true, 글이면 false, **읽지 못했으면 null** — 없거나 열리지 않는 파일은 어느 쪽으로도 단정하지 않는다.
     */
    fun looksLikeStream(file: File): Boolean? = try {
        FileInputStream(file).use { input ->
            val head = ByteArray(HEAD_BYTES)
            var total = 0
            while (total < head.size) {
                val n = input.read(head, total, head.size - total)
                if (n < 0) break
                total += n
            }
            !isText(head, total)
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /**
     * 이 파일을 **글로** 열어야 하는가 — 이름이 갈리고([isAmbiguousStream]) 앞머리가 글이다. 읽지 못했으면 false
     * (이름대로 영상 쪽에 맡긴다 — 그쪽이 '파일을 열 수 없습니다' 를 정확히 말한다).
     */
    fun isTextNamedLikeStream(file: File): Boolean = isAmbiguousStream(file.name) && looksLikeStream(file) == false
}
