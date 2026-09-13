package io.github.donggi.iroiroviewer.safety

import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 읽는 동안의 누적 예산.
 *
 * 두 가지를 센다. **엔트리 수**는 아카이브 하나에 대한 것이라 리더가 사는 동안 줄지
 * 않는다. **출력 바이트**는 '한 번의 작업' 에 대한 것이라 [resetOutput] 으로 끊는다 —
 * 둘을 구분하지 않으면 300쪽짜리 만화책을 끝까지 넘기는 것만으로 총량 상한에 걸린다.
 * 상한의 뜻은 '이 리더로 평생 읽을 양' 이 아니라 '한 번에 풀어낼 양' 이다.
 *
 * 스레드 안전하지 않다 — 아카이브 하나는 한 코루틴이 읽는다.
 */
class EntryBudget(private val limits: ParseLimits = ParseLimits.DEFAULT) {

    var entryCount: Int = 0
        private set

    var totalOutput: Long = 0L
        private set

    /** 목록을 만들기 전에 개수를 먼저 본다. 라이브러리가 전부 올린 뒤에 세면 늦다. */
    fun checkEntryCount(declared: Int) {
        if (declared > limits.maxEntries) {
            throw ParseLimitExceededException(
                "maxEntries",
                "엔트리 ${declared}개 (상한 ${limits.maxEntries})",
            )
        }
    }

    fun beginEntry() {
        entryCount++
        if (entryCount > limits.maxEntries) {
            throw ParseLimitExceededException(
                "maxEntries",
                "엔트리 ${entryCount}개 (상한 ${limits.maxEntries})",
            )
        }
    }

    /**
     * 실제로 만들어 낸 바이트를 더한다. 선언된 크기가 아니라 읽은 바이트다.
     *
     * 예외: 라이브러리가 우리 스트림 밖에서 푸는 양(solid 아카이브에서 앞 엔트리를
     * 건너뛰며 해제하는 분량)은 셀 방법이 없어 **선언 크기로 미리 계상**한다.
     */
    fun addOutput(bytes: Long) {
        if (bytes <= 0) return
        totalOutput += bytes
        if (totalOutput > limits.maxTotalOutput) {
            throw ParseLimitExceededException(
                "maxTotalOutput",
                "총 ${totalOutput}바이트 (상한 ${limits.maxTotalOutput})",
            )
        }
    }

    /** 새 작업을 시작한다. 엔트리 수는 그대로 두고 출력 총량만 되돌린다. */
    fun resetOutput() {
        totalOutput = 0L
    }

    /**
     * 엔트리 하나를 읽을 스트림. 엔트리별 상한·총량·압축비를 함께 건다.
     *
     * @param compressedBytes 지금까지 소비한 **압축 입력** 바이트를 돌려주는 함수.
     *   라이브러리가 통계를 주면 그것을 넘긴다(헤더의 선언값은 공격자가 적는 값이라
     *   분모로 쓰기에 약하다). 모르면 null 이고, 그러면 압축비 감시를 걸지 않는다.
     */
    fun guard(source: InputStream, compressedBytes: (() -> Long)? = null): InputStream =
        LimitedInputStream(
            source = source,
            maxBytes = limits.maxSingleOutput,
            limitName = "maxSingleOutput",
            ratio = compressedBytes?.let { RatioGuard(limits, it) },
            onProduced = ::addOutput,
        )

    /** 압축 크기를 상수로만 아는 경우. */
    fun guard(source: InputStream, declaredCompressedSize: Long): InputStream =
        guard(source, if (declaredCompressedSize > 0) ({ declaredCompressedSize }) else null)

    /**
     * 엔트리 하나를 **받아 쓸** 스트림. 읽기 쪽 [guard] 와 같은 상한을 건다.
     *
     * 출력 쪽 가드가 따로 필요한 이유는 **라이브러리가 우리에게 스트림을 주지 않고
     * 우리 스트림에 쓰는 경우**가 있기 때문이다 — junrar 의
     * `Archive.extractFile(header, out)` 이 그렇다. 그쪽 경로에서 읽기 가드는 걸 자리가
     * 없고, 걸지 않으면 압축폭탄이 그대로 디스크로 흘러간다.
     */
    fun guard(target: OutputStream, compressedBytes: (() -> Long)? = null): OutputStream =
        LimitedOutputStream(
            target = target,
            maxBytes = limits.maxSingleOutput,
            limitName = "maxSingleOutput",
            ratio = compressedBytes?.let { RatioGuard(limits, it) },
            onProduced = ::addOutput,
        )
}

/**
 * 쓴 바이트가 상한을 넘으면 거기서 끊는 스트림. [LimitedInputStream] 의 거울상이다.
 *
 * **`write(ByteArray, Int, Int)` 를 반드시 재정의한다.** `FilterOutputStream` 의 기본
 * 구현은 그것을 바이트 하나씩 `write(Int)` 로 풀어 쓴다 — 그대로 두면 수십 MB 를 푸는
 * 동안 바이트마다 가상 호출이 일어나 그것만으로 몇 배가 느려진다.
 */
class LimitedOutputStream(
    private val target: OutputStream,
    private val maxBytes: Long,
    private val limitName: String = "maxSingleOutput",
    private val ratio: RatioGuard? = null,
    private val onProduced: (Long) -> Unit = {},
) : OutputStream() {

    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        advance(1)
        target.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        // **쓰기 전에 센다.** 넘은 뒤에 세면 상한을 넘은 바이트가 이미 디스크에 닿는다.
        advance(len.toLong())
        target.write(b, off, len)
    }

    override fun flush() = target.flush()

    /** 감싼 스트림을 닫는다. 예산은 건드리지 않는다 — 누적은 아카이브 단위다. */
    override fun close() = target.close()

    private fun advance(n: Long) {
        bytesWritten += n
        if (bytesWritten > maxBytes) {
            throw ParseLimitExceededException(limitName, "${bytesWritten}바이트 (상한 $maxBytes)")
        }
        ratio?.observe(bytesWritten)
        onProduced(n)
    }
}

/**
 * 읽은 바이트가 상한을 넘으면 거기서 끊는 스트림.
 *
 * 압축 폭탄의 본질은 "선언은 작은데 풀면 크다" 이므로, 방어는 푸는 도중에 세는 것밖에
 * 없다. 다 풀고 나서 크기를 보면 이미 늦다.
 */
class LimitedInputStream(
    source: InputStream,
    private val maxBytes: Long,
    private val limitName: String = "maxSingleOutput",
    private val ratio: RatioGuard? = null,
    private val onProduced: (Long) -> Unit = {},
) : FilterInputStream(source) {

    var bytesRead: Long = 0L
        private set

    override fun read(): Int {
        val b = `in`.read()
        if (b >= 0) advance(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = `in`.read(b, off, len)
        if (n > 0) advance(n.toLong())
        return n
    }

    /**
     * **위임하기 전에 자른다.**
     *
     * `InflaterInputStream.skip` 은 요청한 만큼을 *실제로 풀어낸 뒤* 총량을 돌려준다.
     * 먼저 위임하고 나중에 세면, 상한이 1MB 라도 `skip(20MB)` 한 번에 20MB 를 다 푼
     * 뒤에야 예외가 난다 — 막겠다던 바로 그 비용을 이미 치른 뒤다.
     */
    override fun skip(n: Long): Long {
        val allowed = (maxBytes - bytesRead + 1).coerceAtLeast(0L)
        val skipped = `in`.skip(minOf(n, allowed))
        if (skipped > 0) advance(skipped)
        return skipped
    }

    private fun advance(n: Long) {
        bytesRead += n
        if (bytesRead > maxBytes) {
            throw ParseLimitExceededException(limitName, "${bytesRead}바이트 (상한 $maxBytes)")
        }
        ratio?.observe(bytesRead)
        onProduced(n)
    }

    /** 감싼 스트림을 닫는다. 예산은 건드리지 않는다 — 누적은 아카이브 단위다. */
    override fun close() = `in`.close()
}

/**
 * 압축비 감시. 바닥값([ParseLimits.ratioFloorBytes])을 넘긴 뒤에만 본다.
 *
 * 작은 파일은 정상적으로도 비율이 크게 나온다 — 0 으로 채운 1KB 파일은 20바이트로
 * 줄어든다. 비율만 보고 막으면 그런 파일을 거부하게 되므로, 절대량이 의미 있는
 * 수준에 이른 뒤에 비율을 본다.
 *
 * 분모를 **함수로 받는 것**이 핵심이다. 헤더에 적힌 압축 크기는 공격자가 쓰는 값이고,
 * 라이브러리가 실제로 소비한 입력 바이트를 알려주면 그쪽이 훨씬 단단하다.
 */
class RatioGuard(
    private val limits: ParseLimits,
    private val compressedBytes: () -> Long,
) {
    fun observe(uncompressed: Long) {
        if (uncompressed < limits.ratioFloorBytes) return
        val compressed = compressedBytes()
        if (compressed <= 0) return
        val ratio = uncompressed / compressed
        if (ratio > limits.maxCompressionRatio) {
            throw ParseLimitExceededException(
                "maxCompressionRatio",
                "${ratio}:1 (상한 ${limits.maxCompressionRatio}:1, 압축 입력 $compressed)",
            )
        }
    }
}

/**
 * 중첩 깊이. 컨테이너 안의 컨테이너, XML 요소의 겹침처럼 재귀로 스택이나 메모리를
 * 터뜨리는 구조를 막는다.
 */
class DepthGuard(private val max: Int, private val limitName: String = "maxDepth") {

    var depth: Int = 0
        private set

    inline fun <T> around(block: () -> T): T {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    /**
     * 상태를 바꾸기 **전에** 검사한다. 올린 뒤에 던지면 짝이 되는 [exit] 가 불리지
     * 않아 깊이가 1씩 샌다 — 그렇게 되면 한 번 거부된 뒤로 정상 문서까지 막힌다.
     */
    fun enter() {
        if (depth + 1 > max) {
            throw ParseLimitExceededException(limitName, "깊이 ${depth + 1} (상한 $max)")
        }
        depth++
    }

    fun exit() {
        if (depth > 0) depth--
    }
}
