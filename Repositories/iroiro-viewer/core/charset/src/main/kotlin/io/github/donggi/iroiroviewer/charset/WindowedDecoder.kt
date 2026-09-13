package io.github.donggi.iroiroviewer.charset

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction

/**
 * 바이트를 **창 단위로** 읽으면서 문자로 푼다.
 *
 * ## 왜 이것이 따로 필요한가
 *
 * 수백 MB 파일을 통째로 메모리에 올릴 수 없으니 조각내어 읽어야 하는데, 조각 경계에서
 * 멀티바이트 문자가 잘린다. 여기서 **아주 흔한 잘못**이 창마다
 * `String(bytes, off, len, charset)` 을 새로 부르는 것이다. 그러면 경계의 글자가 조용히
 * `U+FFFD` 로 바뀐다 — 예외도 나지 않으므로 **깨진 줄 알 방법이 없다.**
 *
 * 실측으로 확인했다(전 코드포인트·창 크기 1~9 전수): 같은 본문을 5바이트 창으로 잘라
 * 창마다 `String()` 을 부르면 UTF-8 에서 `U+FFFD` 9개, CP949 에서 4개, UTF-16 에서 6개가
 * 생긴다.
 *
 * ## 올바른 모양
 *
 * 1. **디코더 인스턴스 하나를 파일 전체에 걸쳐 유지한다.** 창마다 새로 만들지 않는다.
 * 2. 창 사이에 [CharsetDecoder.reset] 을 부르지 않는다 — 다른 파일을 열 때만 부른다.
 * 3. `decode(in, out, endOfInput = false)` 는 잘린 시퀀스를 **소비하지 않고 남긴다.**
 *    그 바이트를 [ByteBuffer.compact] 로 다음 창 앞에 이월한다.
 * 4. `OVERFLOW` 는 '끝났다' 가 아니다. 출력을 비우고 **같은 입력으로 다시** 부른다.
 * 5. 스트림 끝에서 `endOfInput = true` 한 번, 그리고 [CharsetDecoder.flush] 한 번.
 *    **둘 다** 빠뜨리면 마지막 글자가 사라진다.
 *
 * 이월되는 최대 바이트는 실측으로 **3**이다(UTF-8·UTF-16·GB18030. CP949·Shift_JIS 는 1).
 * 그래서 입력 버퍼는 창 크기보다 최소 4바이트 커야 한다.
 */
class WindowedDecoder(
    private val decoder: CharsetDecoder,
    private val windowBytes: Int = DEFAULT_WINDOW,
) {

    /** 이월 여유. 실측 최대 3 에 하나 더 둔다. */
    private val input: ByteBuffer = ByteBuffer.allocate(windowBytes + CARRY_ROOM)
    private val output: CharBuffer = CharBuffer.allocate(windowBytes + CARRY_ROOM)

    init {
        // **REPLACE 다.** 뷰어는 깨진 바이트를 만나도 파일 전체를 포기하지 않는다 —
        // 그 자리에 U+FFFD 를 두고 나머지를 보여 주는 것이 사용자에게 더 쓸모 있다.
        // (판정 단계는 반대로 REPORT 를 쓴다. 거기서는 '읽히는가' 가 답이기 때문이다.)
        decoder.onMalformedInput(CodingErrorAction.REPLACE)
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
    }

    /**
     * 스트림을 끝까지 읽으며 푼 문자를 [onChars] 로 흘린다.
     *
     * [onChars] 는 **넘겨받은 배열을 보관하면 안 된다** — 다음 회전에서 덮어쓴다.
     * 필요하면 그 자리에서 복사하라.
     *
     * @param limitChars 이만큼 푼 뒤 멈춘다. 0 이하면 끝까지.
     * @return 실제로 푼 문자 수.
     */
    fun decodeAll(
        stream: InputStream,
        limitChars: Long = 0,
        onChars: (CharArray, Int, Int) -> Unit,
    ): Long {
        val chunk = ByteArray(windowBytes)
        var produced = 0L
        var endOfStream = false
        // 아무것도 읽지도 풀지도 못한 회전이 몇 번 이어졌는가.
        // **한 회전에 진전이 없는 것은 정상이다** — 창이 1바이트면 3바이트 문자 하나를
        // 만드는 데 세 회전이 걸린다. 그것을 교착으로 읽으면 첫 글자도 못 내고 끝난다
        // (실제로 그렇게 짰다가 시험에 잡혔다). 여러 번 이어질 때만 교착으로 본다.
        var barren = 0

        while (true) {
            var readBytes = 0
            if (!endOfStream) {
                val room = input.remaining()
                if (room > 0) {
                    val n = stream.read(chunk, 0, minOf(room, chunk.size))
                    if (n < 0) endOfStream = true else readBytes = n.also { input.put(chunk, 0, n) }
                }
            }

            input.flip()
            var madeChars = 0
            while (true) {
                val result = decoder.decode(input, output, endOfStream)
                madeChars += drain(onChars)
                // OVERFLOW 는 '끝났다' 가 아니다. 출력만 비우고 같은 입력으로 다시 부른다.
                if (!result.isOverflow) break
            }
            produced += madeChars
            input.compact()

            if (limitChars > 0 && produced >= limitChars) break

            if (endOfStream) {
                // 마지막 한 번. **둘 다 필요하다** — endOfInput=true 로 잘린 시퀀스를
                // 마무리하고, flush 로 디코더 내부에 남은 것을 꺼낸다.
                input.flip()
                decoder.decode(input, output, true)
                produced += drain(onChars)
                decoder.flush(output)
                produced += drain(onChars)
                break
            }

            barren = if (readBytes == 0 && madeChars == 0) barren + 1 else 0
            // 버퍼에 넣을 자리도 없고 풀리지도 않으면 설계가 잘못된 것이다.
            // 무한 루프로 앱을 멈추게 두지 않는다.
            if (barren >= MAX_BARREN_ROUNDS) break
        }
        return produced
    }

    private fun drain(onChars: (CharArray, Int, Int) -> Unit): Int {
        output.flip()
        val n = output.remaining()
        if (n > 0) onChars(output.array(), output.position(), n)
        output.clear()
        return n
    }

    companion object {
        /**
         * 한 번에 읽는 바이트. 64KiB 는 판정 창과 같은 크기다 — 파일을 열 때 이미 그만큼
         * 읽으므로 같은 단위로 맞추면 읽기가 한 번 준다.
         */
        const val DEFAULT_WINDOW = 64 * 1024

        /** 창 경계에서 이월되는 최대 바이트(실측 3)에 하나 더. */
        const val CARRY_ROOM = 4

        /** 이만큼 연속으로 아무 진전이 없으면 교착으로 본다. */
        private const val MAX_BARREN_ROUNDS = 8
    }
}
