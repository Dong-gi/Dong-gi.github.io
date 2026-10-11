package io.github.donggi.iroiroviewer.format.archive

import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * LZ4 프레임 형식을 **프레임 하나씩** 푼다 — 이어 붙인 프레임·건너뛰는 프레임·빈 프레임까지. 프레임 사이를 이 고리가 넘는다
 * ([GzipInputStream] 이 멤버 사이를 넘는 것과 같은 모양).
 *
 * ## 해제기에게 프레임 사이를 맡기지 않는다
 *
 * commons-compress 의 `FramedLZ4CompressorInputStream(in, decompressConcatenated = true)` 는 프레임 경계를 **재귀로** 넘는다 —
 * 1.28.0 소스에서 블록 길이 0(프레임의 끝)을 읽은 `nextBlock()` 이 `init(false)` 를 부르고, 다음 프레임도 비었으면 그것이 다시
 * `nextBlock()` 을 부른다. 빈 프레임(11바이트)을 이은 만큼 스택이 깊어지고, 생성자에서 바로 돈다. 빈 프레임 20만 개짜리 `x.cbt` 를
 * 둔 폴더를 파일 목록에서 **열기만 했는데** 프로세스가 죽었다(R8 릴리스 빌드, 기기에서 재현 — `StackOverflowError`, 스택 1035 KB 에서
 * `init`↔`nextBlock` 이 2,600번 남짓). 표지 썸네일이 판별(`Archives.detect`)로 그 생성자에 들어갔다.
 *
 * 그래서 해제기는 언제나 `decompressConcatenated = false` 로 **프레임 하나만** 푼다 — 프레임 끝에서 멈추고 입력을 그 바로 뒤에 둔다
 * (블록·검사값을 정확히 그 길이만큼 읽고 더 읽지 않는다 — 1.28.0 소스로 확인했다). 다음 프레임은 새 해제기다.
 *
 * ## 무엇을 받는가 — lz4 도구와 같다
 *
 * - **빈 프레임**(블록이 없다)은 아무것도 내지 않고 지나간다. 빈 프레임만 수만 개 이은 것도 **빈 내용**이다 — 깨졌다고 하지 않는다.
 *   명세가 받는 모양이고(빈 파일을 압축한 것을 `cat` 으로 이으면 이렇다) `lz4 -d` 도 빈 결과를 낸다. 지은 파일이라는 의심만으로
 *   '깨졌다' 를 말할 근거는 없다 — 대신 그런 파일이 드는 값(아래 '대가')을 판별이 센다.
 * - **건너뛰는 프레임**(`5x 2A 4D 18` + 길이 4 + 자료)은 어디에 와도 읽어서 버린다 — 맨 앞, 프레임 사이, 마지막 프레임 뒤. 해제기에
 *   맡기면 마지막 프레임 뒤의 것에서 'LZ4 가 아니다' 로 던진다(새 해제기에게는 그것이 첫 프레임이다).
 * - 프레임이 하나도 없으면(빈 입력·건너뛰는 프레임뿐) LZ4 가 아니다. 프레임 뒤에 다른 것이 붙었으면 깨진 파일이다 — 해제기가
 *   이어 풀던 때와 같은 말이다.
 *
 * ## 대가
 *
 * 프레임마다 해제기를 새로 만든다. 블록이 앞 블록에 기대는 프레임(머리의 '블록 독립' 비트가 0)이면 해제기가 64 KiB 창을 새로
 * 잡는다 — 빈 프레임 하나(11바이트)가 64 KiB 를 잡았다 버린다. 풀어 내는 것이 없어 압축비 상한에도 걸리지 않으므로, 판별은 프레임마다
 * 값을 매기고([FRAME_PROBE_COST] — 판별의 입력 상한에 얹는다), 풀기·목록은 읽을 때마다 인터럽트를 본다([CountingInputStream]).
 */
internal class Lz4FramesInputStream(private val input: CountingInputStream) : InputStream() {

    /** 지금 푸는 프레임. null 이면 다음 프레임을 찾을 차례다. */
    private var frame: FramedLZ4CompressorInputStream? = null
    private var started = false
    private var done = false
    private val one = ByteArray(1)
    private val signature = ByteArray(SIGNATURE_BYTES)
    private var scratch: ByteArray? = null

    override fun read(): Int = if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("LZ4 를 푸는 중에 중단되었다")
            if (done) return -1
            val current = frame ?: nextFrame()
            if (current == null) {
                done = true
                return -1
            }
            frame = current
            val before = input.count
            val n = current.read(b, off, len)
            if (n >= 0) return n
            // -1 이 프레임의 끝인지 본다. 해제기는 내용 없는 블록 하나를 지나도 -1 을 준다 — 그때는 다음 부름이 다음 블록을 읽는다
            // (입력이 줄어든다). 프레임이 정말 끝났으면 입력을 더 읽지 않고 -1 을 되풀이한다.
            if (input.count == before) frame = null
        }
    }

    /**
     * 다음 프레임의 해제기. 끝이면 null. 건너뛰는 프레임은 여기서 지나간다. 앞머리 넷을 보고 되돌린다(버퍼가 표시를 받는다 —
     * [CountingInputStream] 은 되돌린 만큼을 다시 세지 않는다).
     */
    private fun nextFrame(): FramedLZ4CompressorInputStream? {
        while (true) {
            input.mark(SIGNATURE_BYTES)
            val got = readUpTo(signature, SIGNATURE_BYTES)
            if (got == 0) {
                if (!started) throw IOException("LZ4 가 아니다")
                return null
            }
            when {
                got == SIGNATURE_BYTES && isSkippable(signature) -> skipFrame()
                got == SIGNATURE_BYTES && isFrame(signature) -> {
                    input.reset()
                    // 판별이면 이 프레임의 값을 상한에 얹는다(위 '대가'). 판별이 아니면 아무 일도 하지 않는다.
                    input.charge(FRAME_PROBE_COST)
                    started = true
                    return FramedLZ4CompressorInputStream(input, false)
                }
                else -> throw IOException(if (started) "LZ4 뒤에 알 수 없는 자료가 있다" else "LZ4 가 아니다")
            }
        }
    }

    /** 건너뛰는 프레임의 길이(작은 끝 4바이트, 4 GiB 까지)를 읽고 그만큼을 읽어서 버린다. 모자라면 잘린 파일이다. */
    private fun skipFrame() {
        val size = ByteArray(4)
        if (readUpTo(size, 4) < 4) throw EOFException("LZ4 가 중간에 끝났다")
        var left = 0L
        for (i in 0 until 4) left = left or ((size[i].toLong() and 0xFF) shl (8 * i))
        val buf = scratch ?: ByteArray(SKIP_BUFFER).also { scratch = it }
        // `skip` 을 쓰지 않는다 — 파일의 `skip` 은 끝을 넘어도 넘었다고 답할 수 있다. 읽어서 버리면 끝을 본다(인터럽트·판별의 상한도
        // 읽을 때마다 [CountingInputStream] 이 본다).
        while (left > 0) {
            val r = input.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (r < 0) throw EOFException("LZ4 가 중간에 끝났다")
            left -= r
        }
    }

    private fun readUpTo(into: ByteArray, n: Int): Int {
        var got = 0
        while (got < n) {
            val r = input.read(into, got, n - got)
            if (r < 0) break
            got += r
        }
        return got
    }

    override fun close() {
        done = true
        // 해제기는 닫지 않는다 — 닫으면 밑의 입력을 닫는데, 그것은 여기서 한 번 닫는다. 해제기는 순수 자바라 놓을 자원이 없다.
        frame = null
        input.close()
    }

    internal companion object {
        private const val SIGNATURE_BYTES = 4
        private const val SKIP_BUFFER = 8 * 1024

        /**
         * 판별이 프레임 하나에 매기는 값(입력 바이트로 친다). 판별의 입력 상한(4 MiB, [StreamLimits.PROBE_INPUT_BYTES])에서 프레임
         * 1,024개 — 정상 파일은 앞 512바이트가 첫 프레임에서 나온다. 빈 프레임 수십만 개가 판별을 몇 초씩 붙들지 못하게 한다.
         */
        const val FRAME_PROBE_COST = 4L * 1024

        /** LZ4 프레임의 매직(`04 22 4D 18`). */
        fun isFrame(b: ByteArray): Boolean =
            b.size >= 4 && b[0] == 0x04.toByte() && b[1] == 0x22.toByte() && b[2] == 0x4D.toByte() && b[3] == 0x18.toByte()

        /** 건너뛰는 프레임의 매직(`50`~`5F 2A 4D 18`). */
        fun isSkippable(b: ByteArray): Boolean =
            b.size >= 4 && (b[0].toInt() and 0xF0) == 0x50 && b[1] == 0x2A.toByte() && b[2] == 0x4D.toByte() && b[3] == 0x18.toByte()
    }
}
