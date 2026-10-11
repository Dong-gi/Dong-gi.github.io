package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.z.ZCompressorInputStream
import org.tukaani.xz.BasicArrayCache
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * 파일 하나를 통째로 싸는 압축 스트림의 종류. **안에 무엇이 들었는지는 풀어 봐야 안다** — tar 면 `TAR.GZ` 처럼 tar 로,
 * 아니면 `GZ` 처럼 항목 하나짜리로 연다(`Archives.open`). 해제기는 모두 이미 들인 순수 JVM 라이브러리다
 * (commons-compress 1.28.0, org.tukaani:xz 1.12, 플랫폼의 `Inflater`).
 *
 * | 종류 | 앞머리 | 해제기 | 이어 붙인 것 |
 * |---|---|---|---|
 * | [GZIP] | `1F 8B 08` | [GzipInputStream] — 머리는 우리가, 해제는 `Inflater` | 멤버를 끝까지 |
 * | [BZIP2] | `BZh1`~`BZh9` | commons-compress | 스트림을 끝까지 |
 * | [XZ] | `FD 37 7A 58 5A 00` | org.tukaani.xz | 스트림을 끝까지(명세가 그렇다) |
 * | [LZ4] | `04 22 4D 18`(앞에 건너뛰는 프레임 `5x 2A 4D 18` 이 와도 된다) | commons-compress(프레임 형식) — **프레임 하나씩**([Lz4FramesInputStream]) | 프레임을 끝까지(우리 고리) |
 * | [LZMA] | **없다** — 이름(`.lzma`·`.tlz`)과 머리의 그럴듯함([LzmaHeader]) | org.tukaani.xz(LZMA-alone) | 없다 |
 * | [Z] | `1F 9D` 와 코드 폭 9~16([ZHeader]) | commons-compress(LZW) | 없다 |
 *
 * 옛 LZ4(`02 21 4C 18`, `lz4 -l`)·zstd·brotli·lzip 은 여기 없다 — 앞의 셋은 해제기가 없거나(zstd 는 네이티브라 정책 밖)
 * 지금 라이브러리가 읽지 못하고, lzip(`.tlz` 를 함께 쓴다)은 commons-compress 에 없다. 그것들은 '다루지 않는 형식' 이다.
 */
enum class StreamCodec { GZIP, BZIP2, XZ, LZ4, LZMA, Z }

/** 압축 스트림의 해제가 쓰는 메모리 상한. 포맷의 구조가 아니라 해제기의 것이라 여기 둔다. */
internal object StreamLimits {

    /**
     * xz·LZMA 해제에 허용하는 메모리(KiB) — 대부분이 사전이다. `xz -9`(사전 64 MiB)의 해제에 65 MiB 쯤이 든다 — 표준 사전
     * 값을 전부 받으면서, 머리에 1.5 GiB 사전을 적은 파일은 거절하는 자리다. 7z 의 64 MiB 와 같은 까닭의 상한이고 조금
     * 넉넉한 것은 `xz -9` 가 그 경계를 살짝 넘기 때문이다(7z 는 64 MiB 사전 표본이 실제로 거절된다).
     * `lzma -9`(LZMA SDK·`xz --format=lzma -9`)도 사전이 64 MiB 라 같은 값으로 들어온다.
     */
    const val DICTIONARY_MEMORY_KIB = 72 * 1024

    /**
     * `.Z`(LZW) 표의 메모리(KiB). 코드 폭은 16비트까지라([ZHeader]) 표가 65,536칸 × 6바이트 = 384 KiB 다. 머리에 적힌 폭은
     * 그 전에 걸러지므로 이 값은 두 번째 그물이다.
     */
    const val LZW_MEMORY_KIB = 1024

    /**
     * 판별(압축 스트림 안이 tar 인가 — 앞 512바이트를 풀어 본다)이 **파일에서 소비할 수 있는 압축 입력**. 넘으면 안을 보지 못한 것으로
     * 치고 항목 하나짜리로 연다(`Archives`).
     *
     * **시계가 아니라 입력으로 잰다.** 예전에는 5초였는데 같은 파일이 목록과 풀기에서 다른 모양이 될 수 있었다 — 목록이 TAR.BZ2 로
     * 세운 것을, 풀기 순간 SD 가 몇 초 멎으면 항목 하나짜리로 열어 '전부 풀기' 가 `backup.tar` 한 파일을 만들었다.
     * 입력으로 재면 답이 파일의 내용만으로 정해진다 — 목록·풀기·만화의 패스·표지가 언제 어디서 열어도 같은 모양을 본다.
     *
     * 4 MiB 는 정상 파일이 앞 512바이트를 내기까지 읽는 양보다 넉넉하다 — bzip2 는 블록 하나(입력 900 KB, 압축하면 그 언저리)를 다
     * 풀어야 첫 바이트가 나오고, 나머지 형식은 처음 몇 KB 안에서 나온다. 넘는 것은 앞에 입력만 먹는 것(건너뛰는 프레임·빈 블록·끝없는
     * gzip 이름)을 몇 MB 붙인 파일이다.
     */
    const val PROBE_INPUT_BYTES = 4L * 1024 * 1024

    /**
     * 판별의 둘째 걸음(앞 512바이트가 0 이고 안의 이름이 tar 라 빈 tar 인지 끝까지 0 을 확인한다 — `TarArchiveReader.startsLikeTar`)에
     * 더 소비할 수 있는 압축 입력. 빈 tar 는 많아야 1 MiB 의 0 이라 어느 형식으로 싸도 몇 KB 다(LZ4 가 가장 커서 4 KB 남짓).
     * 첫 걸음의 상한(4 MiB)을 그대로 두면, 작은 bzip2 스트림을 이은 파일이 스트림마다 블록 표(최대 1 MB 남짓)를 새로 잡아 몇 초가
     * 걸린다(해제기가 이은 스트림마다 표를 버리고 새로 잡는다 — 1.28.0 소스).
     */
    const val PROBE_ZERO_TAIL_INPUT_BYTES = 16L * 1024
}

/** 압축 스트림을 연다 — 해제기를 고르는 곳은 여기 하나다(tar 를 싼 것과 파일 하나를 싼 것이 같은 해제기를 쓴다). */
internal object CompressedStreams {

    private const val BUFFER = 64 * 1024

    /**
     * 풀린 스트림과, 그 밑에서 **파일에서 읽은 압축 바이트**를 세는 것. 닫으면 파일도 닫힌다. [codec] 이 null 이면 압축하지
     * 않은 원본 그대로다.
     *
     * 세는 것은 버퍼 **위**다 — 해제기가 실제로 가져간 양이 나온다. 아래에 두면 버퍼가 미리 읽은 64 KiB 가 항목 경계에서
     * 앞 항목의 몫으로 잡혀, 뒤 항목의 압축비가 부풀어 오른다.
     *
     * ## 해제기의 마지막 그물 — 스택이 넘치면 깨진 파일이다
     *
     * 라이브러리 해제기 안에는 입력이 깊이를 정하는 재귀가 있다 — LZ4 의 이어 붙인 빈 프레임(그래서 [Lz4FramesInputStream] 이 프레임
     * 사이를 넘는다), bzip2 의 한 스트림 안의 **빈 블록**(1.28.0 소스: 블록이 끝나면 `setupNoRandPartA` 가 `initBlock` 뒤에
     * `setupBlock` 을 부르고 그것이 다시 `setupNoRandPartA` 로 들어간다 — 첫 블록 뒤의 블록은 내용 없이도 검사를 지난다). 찾은 것을
     * 고쳐도 라이브러리 판이 바뀌면 또 생긴다. 그래서 라이브러리의 해제기는 [StackGuardInputStream] 으로 싸서 `StackOverflowError` 를
     * **그 파일이 깨졌다**(`IOException`)로 옮긴다 — 잡지 않으면 썸네일·판별처럼 아무도 시키지 않은 일이 프로세스를 죽인다. 넘친
     * 스택은 잡는 자리에 왔을 때 이미 풀려 있다(잡는 쪽은 얕다). 해제기를 만드는 동안(생성자가 첫 블록을 푼다)의 넘침도 같다.
     * gzip 은 싸지 않는다 — 머리와 멤버 사이를 우리가 고리로 넘고 해제는 플랫폼의 `Inflater` 라 재귀가 없다(판별이 그 형을 본다 —
     * `TarArchiveReader.innerNameIsTar`).
     *
     * @param probeInputBytes 판별의 입력 상한([StreamLimits.PROBE_INPUT_BYTES] — [CountingInputStream.limitProbe]). 판별이 아니면
     *   주지 않는다.
     * @throws IOException 그 형식이 아니다(매직·머리가 맞지 않는다), 사전이 메모리 상한을 넘는다, 또는 해제기가 스택을 넘겼다.
     */
    fun open(source: DocumentSource, codec: StreamCodec?, probeInputBytes: Long? = null): Pair<InputStream, CountingInputStream> {
        val raw = source.openStream()
        try {
            val counted = CountingInputStream(BufferedInputStream(raw, BUFFER))
            if (probeInputBytes != null) counted.limitProbe(probeInputBytes)
            val stream: InputStream = try {
                decoder(codec, counted)
            } catch (e: StackOverflowError) {
                throw IOException(StackGuardInputStream.MESSAGE)
            }
            return stream to counted
        } catch (t: Throwable) {
            raw.close()
            throw t
        }
    }

    private fun decoder(codec: StreamCodec?, counted: CountingInputStream): InputStream = when (codec) {
        null -> counted
        StreamCodec.GZIP -> GzipInputStream(counted)
        StreamCodec.BZIP2 -> StackGuardInputStream(BZip2CompressorInputStream(counted, true))
        // **블록마다 사전을 새로 잡지 않게 한다.** org.tukaani.xz 는 블록을 열 때마다 LZMA2 사전(블록 머리에 적힌 크기,
        // 상한 72 MiB)을 새 배열로 잡는다 — 기본 보관소(`ArrayCache.getDummyCache`)는 아무것도 들고 있지 않다. 16바이트짜리
        // 빈 블록을 이어 붙인 파일은 블록마다 64 MiB 를 잡고 버린다: 2,000개(36 KB)에 5.6초가 들었다(데스크톱 JVM 에서
        // 쟀다 — 1 MB 면 블록이 6만 개 남짓이라 몇 분이다). 풀어 내는 것이 없어 압축비 상한에도 걸리지 않는다. 이 스트림
        // 하나만의 보관소를 주면 끝난 블록의 사전을 다음 블록이 받는다 — 같은 파일이 12 ms 다(`StreamDetectTest`).
        // 전역 보관소(`getInstance`)는 쓰지 않는다 — 스트림과 함께 버려져야 그 사전이 다른 화면의 메모리를 붙들지 않는다.
        StreamCodec.XZ -> StackGuardInputStream(
            XZInputStream(counted, StreamLimits.DICTIONARY_MEMORY_KIB, true, BasicArrayCache()),
        )
        // 프레임 사이를 해제기에 맡기지 않는다 — 이어 붙인 빈 프레임마다 재귀가 한 겹씩 깊어진다([Lz4FramesInputStream]).
        StreamCodec.LZ4 -> StackGuardInputStream(Lz4FramesInputStream(counted))
        StreamCodec.LZMA -> {
            // 매직이 없다. 판별이 이미 보았지만 이 함수는 직접 불릴 수도 있다 — 그럴듯하지 않은 머리를 해제기에 주지
            // 않는다(사전 크기 칸이 쓰레기면 그만큼을 잡으려 든다).
            requireHead(counted, LzmaHeader.PROBE_BYTES, "LZMA") { LzmaHeader.plausible(it) }
            StackGuardInputStream(LZMAInputStream(counted, StreamLimits.DICTIONARY_MEMORY_KIB))
        }
        StreamCodec.Z -> {
            // commons-compress 는 코드 폭 칸(5비트, 31까지)을 그대로 믿고 표를 2^폭 칸으로 잡는다 — 31이면 넘쳐서
            // 음수가 된다. 명세(compress(1))의 폭은 9~16 이다.
            requireHead(counted, 3, ".Z") { ZHeader.plausible(it) }
            StackGuardInputStream(ZCompressorInputStream(counted, StreamLimits.LZW_MEMORY_KIB))
        }
    }

    /** 앞 [n] 바이트를 읽어 보고 되돌린다. 버퍼가 표시를 받는다(`BufferedInputStream`). */
    private inline fun requireHead(input: InputStream, n: Int, what: String, ok: (ByteArray) -> Boolean) {
        input.mark(n)
        val head = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(head, got, n - got)
            if (r < 0) break
            got += r
        }
        input.reset()
        if (!ok(head.copyOf(got))) throw IOException("$what 머리가 아니다")
    }

    /**
     * 앞머리의 매직으로 압축 스트림을 가린다. **[StreamCodec.LZMA] 는 여기서 답하지 않는다** — 매직이 없어서 이름의 도움이
     * 필요하다([LzmaHeader.hintedBy]).
     */
    fun codecOf(head: ByteArray): StreamCodec? = when {
        head.startsWith(0x1F, 0x8B, 0x08) -> StreamCodec.GZIP
        head.startsWith(0x42, 0x5A, 0x68) && head.size > 3 && head[3] in '1'.code.toByte()..'9'.code.toByte() -> StreamCodec.BZIP2
        head.startsWith(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> StreamCodec.XZ
        isLz4(head) -> StreamCodec.LZ4
        ZHeader.plausible(head) -> StreamCodec.Z
        else -> null
    }

    /** LZ4 프레임(`04 22 4D 18`) 또는 그 앞의 건너뛰는 프레임(`50`~`5F 2A 4D 18`). 판단은 푸는 고리와 한 벌이다. */
    private fun isLz4(head: ByteArray): Boolean =
        Lz4FramesInputStream.isFrame(head) || Lz4FramesInputStream.isSkippable(head)

    /** -1 은 '아무것이나'. */
    private fun ByteArray.startsWith(vararg magic: Int): Boolean {
        if (size < magic.size) return false
        for (i in magic.indices) if (magic[i] >= 0 && this[i] != magic[i].toByte()) return false
        return true
    }
}

/**
 * LZMA-alone(`.lzma`, LZMA SDK·`xz --format=lzma`)의 13바이트 머리 — 속성 1, 사전 크기 4(작은 끝), 풀린 크기 8(작은 끝,
 * 모르면 전부 `FF`).
 *
 * ## 매직이 없다 — 그래서 셋을 함께 요구한다
 *
 * 1. **이름이 그렇다고 한다**([hintedBy]: `.lzma`·`.tlz`·`.tar.lzma`). 이름 없이 내용만으로는 LZMA 라고 하지 않는다 —
 *    아무 이진 파일이나 그럴듯한 머리를 가질 수 있다.
 * 2. **머리가 그럴듯하다**([plausible]) — xz-utils 가 `.lzma` 를 알아볼 때 쓰는 판단(liblzma `alone_decoder.c` 의 'picky'
 *    검사)에 해제기가 정한 한 바이트를 더했다:
 *    - 속성 바이트가 225 미만이고 `lc + lp ≤ 4`(liblzma 는 그 너머를 풀지 않는다 — 실제 파일은 거의 다 `0x5D`).
 *    - 사전 크기가 `2^n` 이거나 `2^n + 2^(n-1)`(n 은 12~30). 인코더가 쓰는 값이 이것뿐이다(xz 는 이 모양으로 올려 적는다).
 *      `FFFFFFFF` 는 xz 가 받지만 우리 해제기(DICT_SIZE_MAX = 2 GiB - 16)가 풀지 못해 넣지 않는다.
 *    - 풀린 크기가 '모름'(전부 `FF`)이거나 256 GiB(2^38) 미만.
 *    - 압축 자료의 첫 바이트(범위 부호기의 첫 바이트)가 `00` — LZMA 의 부호기는 언제나 0 으로 시작한다.
 * 3. **앞 512바이트가 실제로 풀린다**(`Archives` 가 안이 tar 인지 보려고 풀어 본다). 풀리지 않으면 깨진 LZMA 가 아니라
 *    **LZMA 가 아닌 것**으로 본다 — 매직이 없으니 '깨졌다' 라고 말할 근거도 없다.
 *
 * 무작위 바이트가 2를 지날 확률은 대략 0.29 × 1.5×10⁻⁸ × 6×10⁻⁸ × 1/256 ≈ 10⁻¹⁸ 이다(속성·사전·크기·첫 바이트).
 */
internal object LzmaHeader {

    /** 머리 13바이트와 범위 부호기의 첫 바이트. */
    const val PROBE_BYTES = 14

    private const val UNKNOWN_SIZE = -1L

    /** 이름이 LZMA 라고 하는가. 대소문자를 가리지 않는다. */
    fun hintedBy(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".lzma") || lower.endsWith(".tlz")
    }

    fun plausible(head: ByteArray): Boolean {
        if (head.size < PROBE_BYTES) return false
        val props = head[0].toInt() and 0xFF
        if (props >= 9 * 5 * 5) return false
        val lc = props % 9
        val lp = (props / 9) % 5
        if (lc + lp > 4) return false
        if (!dictionaryPlausible(u32(head, 1))) return false
        val size = u64(head, 5)
        if (size != UNKNOWN_SIZE && (size < 0 || size >= (1L shl 38))) return false
        return head[13].toInt() == 0
    }

    /** 머리의 풀린 크기. 모르면 -1. [plausible] 을 지난 머리에만 부른다. */
    fun knownSize(head: ByteArray): Long {
        if (head.size < 13) return UNKNOWN_SIZE
        val size = u64(head, 5)
        return if (size < 0) UNKNOWN_SIZE else size
    }

    /** `2^n` 또는 `2^n + 2^(n-1)`, n 은 12~30. */
    fun dictionaryPlausible(d: Long): Boolean {
        for (n in 12..30) {
            val p = 1L shl n
            if (d == p || d == p + (p shr 1)) return true
        }
        return false
    }

    private fun u32(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private fun u64(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}

/**
 * `.Z`(유닉스 compress, LZW)의 3바이트 머리 — `1F 9D` 와 플래그 바이트(높은 비트 = 블록 모드, 낮은 5비트 = 최대 코드 폭).
 *
 * 매직이 두 바이트뿐이라 플래그 바이트까지 본다: 코드 폭은 9~16(compress(1)·ncompress 의 `-b` 범위, GNU gzip 도 16 을 넘으면
 * 풀지 않는다)이고 쓰지 않는 두 비트(`0x60`)는 0 이다.
 */
internal object ZHeader {
    fun plausible(head: ByteArray): Boolean {
        if (head.size < 3) return false
        if (head[0] != 0x1F.toByte() || head[1] != 0x9D.toByte()) return false
        val flags = head[2].toInt() and 0xFF
        return flags and 0x60 == 0 && (flags and 0x1F) in 9..16
    }
}

/**
 * 라이브러리 해제기를 감싸 **스택이 넘치면 깨진 파일**(`IOException`)로 끝낸다 — [CompressedStreams.open] 의 '마지막 그물'.
 *
 * 한 번 넘친 해제기는 상태가 반쯤 바뀐 채 남으므로 그 뒤의 읽기도 모두 같은 예외로 끝낸다. 원인(`StackOverflowError`)은 싣지
 * 않는다 — 그 스택 자취는 수천 겹이고, 예외는 로그로 나간다(메시지는 화면에 나가지 않는다 — 부르는 쪽이 종류만 본다).
 */
internal class StackGuardInputStream(private val inner: InputStream) : InputStream() {

    private var broken = false

    override fun read(): Int = guard { inner.read() }

    override fun read(b: ByteArray, off: Int, len: Int): Int = guard { inner.read(b, off, len) }

    override fun skip(n: Long): Long = guard { inner.skip(n) }

    override fun available(): Int = guard { inner.available() }

    override fun close() = inner.close()

    private inline fun <T> guard(block: () -> T): T {
        if (broken) throw IOException(MESSAGE)
        try {
            return block()
        } catch (e: StackOverflowError) {
            broken = true
            throw IOException(MESSAGE)
        }
    }

    companion object {
        const val MESSAGE = "압축 자료가 깨졌다(해제기의 재귀가 스택을 넘었다)"
    }
}
