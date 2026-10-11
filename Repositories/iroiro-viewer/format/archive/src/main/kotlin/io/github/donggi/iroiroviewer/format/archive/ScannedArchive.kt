package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * 머리를 훑는 아카이브(tar·cpio·ar)의 **이름 상한**. 세 형식이 같은 바탕([EntryScannerBase])에서 같은 값을 쓴다 — 14단계가
 * tar 에서 세운 값이라 `TarLimits` 에 있었는데, cpio·ar 가 그것을 빌려 쓰면서 이름이 거짓말을 하게 되어 옮겼다. 여러 포맷이
 * 함께 쓰는 예산(`ParseLimits`)이 아니라 **이 바탕만 아는** 구조 상한이라 여기 둔다(`TarLimits` 와 같은 까닭).
 */
internal object EntryNameLimits {

    /**
     * 이름 하나(tar 의 GNU 긴 이름 `L`·긴 링크 `K`, cpio 의 이름 칸, ar 의 BSD 긴 이름 `#1/`)의 상한.
     *
     * 이름은 **메모리에 통째로 올라간다** — commons-compress 의 tar·cpio 읽기를 쓰지 않은 까닭 하나가 이것이다(그쪽은 머리에
     * 적힌 크기만큼 끝까지 모은다. 1 GiB 라고 적으면 1 GiB 다). 리눅스의 경로 상한(PATH_MAX)이 4,096바이트라 64 KiB 는 넉넉하다.
     */
    const val MAX_LONG_NAME_BYTES = 64 * 1024

    /**
     * 한 번 훑는 동안 **모든 항목 이름의 바이트 합** 상한. 목록은 이름을 전부 들고 있다. ar 의 GNU 긴 이름 표(`//`) 하나도 이
     * 값을 넘으면 읽기 전에 거절한다.
     *
     * 이름 하나의 상한만으로는 모자란다 — 압축한 tar 는 같은 긴 이름을 되풀이하면 몇 MB 파일이 수천 개의 64 KiB
     * 이름(PAX 경로라면 1 MiB)으로 풀리고, 항목 상한(1만 개)까지 곱하면 기가바이트가 힙에 올라 앱이 죽는다(해제량의
     * 상한은 목록 화면에서 1 GiB 라 그 전에 메모리가 먼저 끝난다). 정상 tar 는 1만 개 × 수백
     * 바이트라 수 MB 다. 16 MiB 는 항목 1만 개가 평균 1.6 KiB 경로를 써도 들어간다.
     */
    const val MAX_NAME_BYTES_TOTAL = 16 * 1024 * 1024
}

/**
 * 머리를 하나씩 읽어 오는 곳. **건너뛰기의 값이 둘로 갈린다** — 압축하지 않은 파일은 자리만 옮기면 되고(공짜),
 * 압축한 스트림은 풀어서 버려야 한다(풀어낸 만큼의 시간). tar·cpio·ar 가 함께 쓴다.
 */
internal interface ScanSource {
    fun read(b: ByteArray, off: Int, len: Int): Int

    /** 정확히 [n] 바이트를 지나간다. 모자라면 던진다. 풀어서 지나간 양은 [EntryScannerBase] 의 `onDrain` 이 받는다. */
    fun skip(n: Long): Long

    /** 원본에 남은 바이트. 모르면 -1 — 풀린 스트림은 끝까지 풀어 봐야 안다. 적힌 길이가 파일보다 긴지 보는 데 쓴다. */
    fun remaining(): Long = -1L
}

/** 풀린 스트림(gzip·bzip2·xz…) 또는 무작위 접근이 안 되는 원본. 건너뛰기가 곧 읽어서 버리기다. */
internal class StreamScanSource(private val input: InputStream) : ScanSource {

    private val scratch = ByteArray(64 * 1024)

    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)

    override fun skip(n: Long): Long {
        var left = n
        while (left > 0) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("건너뛰는 중에 중단되었다")
            val r = input.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (r < 0) throw EOFException("아카이브가 중간에 끝났다")
            left -= r
        }
        return n
    }
}

/** 압축하지 않은 파일. 건너뛰기는 자리를 옮기는 것뿐이다. */
internal class ChannelScanSource(private val channel: SeekableByteChannel) : ScanSource {

    override fun read(b: ByteArray, off: Int, len: Int): Int = interruptible { channel.read(ByteBuffer.wrap(b, off, len)) }

    override fun skip(n: Long): Long = interruptible {
        val at = channel.position()
        // 자료가 파일보다 길다고 적힌 머리. 자리만 옮기면 조용히 끝을 넘어 다음 읽기가 EOF 가 된다 —
        // 스트림 쪽과 같은 말(잘린 파일)을 하게 여기서 끊는다. **뺄셈으로 견준다** — 적힌 크기는 `Long` 의 끝까지 올 수 있어
        // `at + n` 이 넘쳐 음수가 되면 검사를 지나 `position(음수)` 가 `IllegalArgumentException` 을 냈다.
        if (n > channel.size() - at) throw EOFException("아카이브가 중간에 끝났다")
        channel.position(at + n)
        n
    }

    fun position(): Long = channel.position()

    override fun remaining(): Long = interruptible { channel.size() - channel.position() }
}

/**
 * 파일 채널은 **인터럽트되면 스스로 닫히고 `ClosedByInterruptException` 을 던진다** — `InterruptedIOException`
 * 이 아니다. 그대로 두면 사용자의 취소가 '입출력 실패' 로 보고되고 닫힌 채널을 붙든 다음 읽기가 또 실패한다.
 * 취소의 말로 옮긴다.
 */
internal inline fun <T> interruptible(block: () -> T): T = try {
    block()
} catch (e: java.nio.channels.ClosedByInterruptException) {
    throw InterruptedIOException("읽는 중에 중단되었다")
}

/**
 * 항목 하나의 머리. 메타 머리(tar 의 긴 이름·PAX, ar 의 긴 이름 표)를 **이미 적용한** 모양이다.
 *
 * @property dataSize 머리 뒤에 실제로 놓인 자료 바이트(채움 제외). 건너뛰기가 이것을 쓴다.
 * @property declaredSize 화면에 적을 크기. 대개 [dataSize] 와 같고 파일이 아니면 0 이다.
 * @property presetName 이미 글자로 읽힌 이름(tar 의 PAX `path`). 있으면 [nameBytes] 의 판정보다 먼저다.
 * @property byteSum 자료 바이트의 합(cpio `070702` 형식의 검사값). 없으면 -1.
 */
internal class ScannedEntry(
    val nameBytes: ByteArray,
    val presetName: String? = null,
    val presetLabel: String = "",
    val kind: Kind,
    val dataSize: Long,
    val declaredSize: Long,
    val mtimeMillis: Long,
    /** 원본 안에서 자료가 시작하는 자리. 압축하지 않은 파일의 무작위 접근이 이것을 쓴다. */
    val dataOffset: Long,
    val byteSum: Long = -1L,
) {
    enum class Kind {
        FILE,
        DIRECTORY,

        /** 심볼릭 링크·하드링크. 내용이 파일이 아니라 경로다(cpio 의 하드링크는 자료가 마지막 하나에만 있다). */
        LINK,

        /** 문자·블록 장치, FIFO, 소켓. 내용이 없다. */
        DEVICE,

        /** tar 의 희소 파일·다음 볼륨으로 이어지는 조각, cpio 의 모르는 종류. 자료가 있지만 그대로 풀면 다른 파일이 된다. */
        UNREADABLE,
    }
}

/** 머리를 하나씩 내놓는 것 — tar·cpio·ar 가 이 모양이다. */
internal interface EntryScanner {
    /** 다음 항목. 끝이면 null. */
    fun next(): ScannedEntry?

    /** 지금 항목의 자료. **닫아도 원본은 닫히지 않는다.** 다 읽지 않아도 다음 [next] 가 나머지를 건너뛴다. */
    fun data(): InputStream
}

/**
 * [EntryScanner] 의 공통 바탕 — 자리 세기, 남은 자료·채움 건너뛰기(자리 옮기기이거나 풀어서 버리기), 훑는 양의 상한,
 * 이름의 상한. **형식마다 다른 것은 머리를 읽는 [next] 하나다.** 14단계의 tar 가 세운 판단을 cpio·ar 가 그대로 탄다.
 *
 * @param scanCap 원본에서 소비할 수 있는 최대 바이트(머리·자료·채움 전부). 압축한 tar 에서 한 항목에 닿거나 목록을 만드는
 *   비용의 상한이다 — 7z 의 '건너뛸 양' 과 같은 뜻이라 같은 상한 이름(`maxTotalOutput`)으로 끊는다.
 * @param onDrain 건너뛰느라 **읽어서 버린** 바이트. 순차 추출이 예산에 올린다(solid 7z 와 같은 규칙).
 * @param headerLimit 이름 상한을 넘었을 때의 상한 이름. 진단에만 쓴다.
 */
internal abstract class EntryScannerBase(
    protected val src: ScanSource,
    private val scanCap: Long,
    private val onDrain: (Long) -> Unit,
    private val headerLimit: String,
) : EntryScanner {

    private val drains: Boolean = src !is ChannelScanSource

    /** 지금까지 원본에서 지나간 바이트. */
    var position: Long = 0L
        protected set

    /** 지금 항목의 남은 자료와 그 뒤의 채움. */
    protected var remaining = 0L
    protected var padding = 0L

    /** 끝 표시를 만났다. */
    protected var ended = false

    /** 지금까지 내놓은 항목 이름의 바이트 합. */
    private var nameBytesTotal = 0L

    final override fun data(): InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (remaining <= 0) return -1
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("읽는 중에 중단되었다")
            val n = src.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n < 0) throw EOFException("아카이브가 중간에 끝났다")
            remaining -= n
            advance(n.toLong())
            return n
        }

        override fun close() = Unit
    }

    /** 다음 항목의 자료를 연다 — [size] 바이트의 자료와 그 뒤 [pad] 바이트의 채움. */
    protected fun beginData(size: Long, pad: Long) {
        remaining = size
        padding = pad
    }

    /** 지금 항목의 남은 자료와 채움을 지나간다. */
    protected fun skipRest() {
        val n = remaining + padding
        remaining = 0L
        padding = 0L
        if (n > 0) skipBytes(n)
    }

    protected fun skipBytes(n: Long) {
        if (n <= 0) return
        checkCap(n)
        src.skip(n)
        position += n
        if (drains) onDrain(n)
    }

    /**
     * 메타 자료(이름·이름 표·PAX 머리) [size] 바이트를 읽는다. 적힌 크기는 공격자의 값이라 **읽기 전에** 두 가지를 본다:
     *
     * 1. 원본이 파일이고 **남은 것보다 길다고 적혔으면 깨진 머리다**(EOF). 상한을 먼저 보면 700바이트 cpio 의 이름 길이
     *    `FFFFFFFF` 가 '너무 크다' 로 보였다 — 사실은 깨진 파일이고, 화면의 말(크기 상한)과 권하는 일(다른 앱으로 열기)이
     *    달라진다.
     * 2. 그다음 [cap] 을 넘으면 상한이다. 풀린 스트림은 남은 길이를 모르므로(`ScanSource.remaining` 이 -1) 이것만 본다.
     */
    protected fun readBytes(size: Long, cap: Int): ByteArray {
        if (size < 0) throw IOException("메타 자료의 길이가 음수다")
        val left = src.remaining()
        if (left >= 0 && size > left) throw EOFException("아카이브가 중간에 끝났다")
        if (size > cap) throw ParseLimitExceededException(headerLimit, "메타 자료 ${size}바이트 (상한 $cap)")
        val n = size.toInt()
        val buf = ByteArray(n)
        readFully(buf, 0, n)
        return buf
    }

    /**
     * 내놓을 항목의 이름을 센다 — 목록은 이름을 끝까지 들고 있으므로 하나의 길이와 합을 함께 묶는다
     * ([EntryNameLimits.MAX_NAME_BYTES_TOTAL]).
     */
    protected fun countName(name: ByteArray) {
        if (name.size > EntryNameLimits.MAX_LONG_NAME_BYTES) {
            throw ParseLimitExceededException(headerLimit, "이름 ${name.size}바이트 (상한 ${EntryNameLimits.MAX_LONG_NAME_BYTES})")
        }
        nameBytesTotal += name.size
        if (nameBytesTotal > EntryNameLimits.MAX_NAME_BYTES_TOTAL) {
            throw ParseLimitExceededException(
                headerLimit,
                "이름 합 ${nameBytesTotal}바이트 (상한 ${EntryNameLimits.MAX_NAME_BYTES_TOTAL})",
            )
        }
    }

    protected fun readFully(buf: ByteArray, off: Int, n: Int) {
        if (readUpTo(buf, off, n) < n) throw EOFException("아카이브가 중간에 끝났다")
    }

    /** [n] 바이트까지 읽는다. 원본이 그 전에 끝나면 읽은 만큼을 돌려준다. */
    protected fun readUpTo(buf: ByteArray, off: Int, n: Int): Int {
        var got = 0
        while (got < n) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("읽는 중에 중단되었다")
            val r = src.read(buf, off + got, n - got)
            if (r < 0) break
            got += r
        }
        advance(got.toLong())
        return got
    }

    protected fun advance(n: Long) {
        checkCap(n)
        position += n
    }

    /**
     * **뺄셈으로 견준다.** tar 의 크기는 256진·PAX 로 `Long` 의 끝 가까이 올 수 있고(그 위는 `TarFormat` 이 깨진 것으로 거른다),
     * `position + n` 이 넘쳐 음수가 되면 상한이 꺼진다 — 0 폭탄을 붙인 `.tar.bz2` 가 1 GiB 상한을 지나 스트림 끝까지 풀렸다.
     * [position] 은 언제나 [scanCap] 이하라(넘기 전에 던진다) 뺄셈은 넘치지 않는다.
     */
    private fun checkCap(n: Long) {
        if (n > scanCap - position) {
            throw ParseLimitExceededException(
                "maxTotalOutput",
                "아카이브를 ${saturatingAdd(position, n)}바이트까지 읽어야 한다 (상한 $scanCap)",
            )
        }
    }

    protected fun end(): ScannedEntry? {
        ended = true
        return null
    }
}

/**
 * 머리와 자료가 번갈아 놓인 아카이브(tar·cpio·ar)를 읽는 바탕. 형식마다 다른 것은 머리를 읽는 [EntryScanner] 하나이고,
 * 목록·항목 열기·순차 추출·진행률의 규칙은 여기 한 벌이다(14단계가 tar 로 세운 것을 cpio·ar 가 함께 쓴다 — 두 벌이면
 * 한쪽만 고쳐진다).
 *
 * ## 두 모양이 비용을 가른다
 *
 * | | 목록 | 항목 하나 열기 | 순차 추출 |
 * |---|---|---|---|
 * | **압축 안 함**(파일) | 머리만 읽고 자료는 **자리를 옮겨** 건너뛴다 | 자리로 바로 간다(무작위 접근) | 고른 것만 읽는다 |
 * | **압축 스트림**(tar.gz 등) | 스트림을 **처음부터 끝까지 푼다**(머리가 자료 사이사이에 있다) | 앞의 항목을 전부 풀어서 버린다 | 한 번에 끝까지 |
 *
 * 압축한 쪽은 solid 7z 와 같은 처지다 — 한 항목을 보려면 앞엣것을 다 풀어야 한다. 그래서 같은 규칙을 쓴다:
 * **건너뛸 양(풀어서 버릴 양)을 `maxTotalOutput` 으로 묶고**, 목록은 게으르게 만들어 만화 뷰어의 패스
 * (`extractSequentially` 만 쓴다)가 목록 한 번을 더 풀지 않게 한다. 목록 자체도 같은 상한에 묶인다 —
 * 기본값(1 GiB)으로 여는 압축 목록 화면에서 **풀린 크기가 1 GiB 를 넘는 압축 tar 는 열리지 않는다.**
 *
 * ## 막는 것 — ZIP·7z·RAR 과 같은 방어
 *
 * - 머리 수는 `maxEntries`, 항목 하나는 `maxSingleOutput`, 한 번의 작업은 `maxTotalOutput`([EntryBudget]).
 * - 압축비는 **그 항목을 읽는 동안 파일에서 소비한 압축 바이트**가 분모다(압축 안 한 것은 1:1 이라 걸지 않는다).
 * - 경로 탈출은 [ArchiveEntry.sanitize] 가 막는다 — ZIP 과 같은 관문을 지난다.
 * - 링크·장치·희소 파일은 [ArchiveEntry.isLink] 로 걸러 풀지 않는다(RAR 의 링크와 같은 취급).
 * - 취소는 스레드 인터럽트로 닿는다 — 머리 읽기·건너뛰기·자료 읽기가 모두 인터럽트를 본다.
 *
 * ## 이름
 *
 * tar·cpio·ar 는 이름의 인코딩을 적지 않는다(tar 의 PAX `path` 만 UTF-8 이 약속이다). 그래서 머리의 이름 바이트는 ZIP 과
 * **같은 판정**([EntryNameDecoder])을 지난다 — 한국어 윈도우 도구가 CP949 로 적은 이름이 그대로 살아난다.
 *
 * @param codec 감싼 압축 스트림. null 이면 압축하지 않았다.
 * @param scannerOf 원본·훑는 양의 상한·버린 양을 받는 함수를 받아 머리를 읽는 것을 만든다.
 */
internal class ScannedArchive(
    private val source: DocumentSource,
    private val budget: EntryBudget,
    private val limits: ParseLimits,
    private val codec: StreamCodec?,
    override val kind: ArchiveKind,
    private val scannerOf: (src: ScanSource, cap: Long, onDrain: (Long) -> Unit) -> EntryScanner,
) : ArchiveReader {

    override val formatId: FormatId? get() = null

    /**
     * 자리로 바로 갈 수 있는가. 압축하지 않았고 원본이 파일일 때만이다 — 스트림만 주는 원본
     * (아카이브 안의 아카이브 같은 것)이면 압축 안 한 것도 앞에서부터 읽는다.
     */
    private val seekable: Boolean = codec == null && source.asFile() != null

    override val randomAccess: Boolean get() = seekable

    /** 목록의 항목마다 자료의 자리와 크기. [open] 이 무작위 접근에 쓴다. */
    private class Span(val dataOffset: Long, val dataSize: Long, val byteSum: Long)

    /**
     * 목록. 순차 추출이 끝까지 가면 거기서 세운 것을 여기 둔다 — 다른 스레드가 [entries] 로 읽을 수 있어 `@Volatile`
     * 이고, [spans] 를 **먼저** 쓴 뒤 이것을 쓴다(이것을 본 스레드는 [spans] 도 본다).
     */
    @Volatile
    private var listed: List<ArchiveEntry>? = null
    private var spans: List<Span>? = null

    /** 예산에 이미 센 머리 수. 목록과 순차 추출이 같은 예산으로 두 번 세지 않게 한다. */
    private var counted = 0

    /**
     * 무작위 접근이 되는 원본은 목록을 곧바로 세운다 — 머리만 읽으므로 싸다. 만든 쪽이 형식을 확인한 **뒤에** 부른다
     * (tar 가 아닌 것을 목록으로 읽으면 '깨졌다' 가 되는데, 사실은 '다루지 않는 형식' 이다).
     */
    fun prepare() {
        if (seekable) list()
    }

    override val entries: List<ArchiveEntry>
        get() = listed ?: list()

    /**
     * 머리를 전부 읽어 목록을 세운다. 압축 안 한 파일은 자료를 자리 옮기기로 건너뛰고, 압축한 것은 풀어서 버린다
     * — 그 양이 `maxTotalOutput` 에 묶인다.
     */
    @Synchronized
    private fun list(): List<ArchiveEntry> {
        listed?.let { return it }
        val out = ArrayList<ArchiveEntry>(64)
        val where = ArrayList<Span>(64)
        withScanner(limits.maxTotalOutput) { scanner, _ ->
            while (true) {
                val h = scanner.next() ?: break
                count(out.size)
                out += entryOf(out.size, h)
                where += Span(h.dataOffset, h.dataSize, h.byteSum)
            }
        }
        spans = where
        listed = out
        return out
    }

    private fun count(index: Int) {
        if (index >= counted) {
            budget.beginEntry()
            counted = index + 1
        }
    }

    private fun entryOf(index: Int, h: ScannedEntry): ArchiveEntry {
        val decoded = h.presetName?.let { EntryNameDecoder.Decoded(it, h.presetLabel) }
            ?: EntryNameDecoder.decode(
                raw = h.nameBytes,
                utf8Flag = false,
                fallback = String(h.nameBytes, Charsets.ISO_8859_1),
            )
        val isDirectory = h.kind == ScannedEntry.Kind.DIRECTORY
        return ArchiveEntry(
            index = index,
            name = decoded.name,
            safeName = ArchiveEntry.sanitize(decoded.name),
            declaredSize = h.declaredSize,
            // 압축 안 한 것은 저장된 그대로다. 압축한 tar 는 항목마다의 압축 크기가 없다(스트림 하나다).
            compressedSize = if (codec == null) h.dataSize else -1L,
            isDirectory = isDirectory,
            isLink = h.kind == ScannedEntry.Kind.LINK ||
                h.kind == ScannedEntry.Kind.DEVICE ||
                h.kind == ScannedEntry.Kind.UNREADABLE,
            isEncrypted = false,
            crc = -1L,
            nameCharset = decoded.charsetLabel,
            lastModified = h.mtimeMillis,
        )
    }

    // ---- 여는 길 ----------------------------------------------------------------------

    /** 풀린 스트림과 그 밑의 압축 입력 계수기([CompressedStreams.open]). */
    private fun openStream(): Pair<InputStream, CountingInputStream> = CompressedStreams.open(source, codec)

    /**
     * 처음부터 머리를 훑는 것을 연다. 압축 안 한 파일이면 채널로 — 자료를 자리 옮기기로 건너뛴다.
     *
     * @param cap 훑으며 소비할 수 있는 바이트.
     */
    private inline fun <T> withScanner(
        cap: Long,
        noinline onDrain: (Long) -> Unit = {},
        block: (EntryScanner, () -> Long) -> T,
    ): T {
        if (seekable) {
            val channel = openChannel()
            return channel.use { ch ->
                val src = ChannelScanSource(ch)
                block(scannerOf(src, Long.MAX_VALUE, {}), { src.position() })
            }
        }
        val (input, counter) = openStream()
        return input.use {
            block(scannerOf(StreamScanSource(input), cap, onDrain), { counter.count })
        }
    }

    private fun openChannel(): SeekableByteChannel =
        source.openChannel() ?: throw IOException("아카이브 파일의 채널을 열 수 없다")

    override fun open(entry: ArchiveEntry): InputStream {
        require(entry.isReadable) { "읽을 수 없는 항목이다" }
        val all = entries
        if (entry.index !in all.indices) throw ParseLimitExceededException("index", "없는 엔트리")
        val span = spans!![entry.index]
        if (seekable) {
            val channel = openChannel()
            try {
                channel.position(span.dataOffset)
                // 저장된 그대로라 압축비가 1:1 이다 — 비율 감시를 걸 이유가 없다.
                return budget.guard(withSum(ChannelEntryStream(channel, span.dataSize), span.byteSum))
            } catch (t: Throwable) {
                channel.close()
                throw t
            }
        }

        // **앞엣것을 풀어서 버려야 닿는다.** 7z 의 `open` 과 같은 규칙 — 풀어야 할 양이 총량 상한을 넘으면 스트림을
        // 주지 않는다. 적힌 크기는 공격자의 값이지만 여기서는 그 크기가 곧 다음 머리의 자리라 실제로 그만큼 푼다. 합은 넘치지
        // 않게 더한다(크기 하나가 2^62 까지 온다 — 넷이면 `Long` 을 넘는다).
        var skipCost = 0L
        for (i in 0 until entry.index) skipCost = saturatingAdd(skipCost, spans!![i].dataSize)
        if (skipCost > limits.maxTotalOutput) {
            throw ParseLimitExceededException(
                "maxTotalOutput",
                "이 항목에 닿으려면 ${skipCost}바이트를 풀어야 한다 (상한 ${limits.maxTotalOutput})",
            )
        }
        val (input, counter) = openStream()
        try {
            val cap = saturatingAdd(saturatingAdd(limits.maxTotalOutput, span.dataSize), TarFormat.RECORD.toLong())
            val scanner = scannerOf(StreamScanSource(input), cap, {})
            var index = -1
            while (true) {
                scanner.next() ?: throw IOException("아카이브 항목이 목록과 다르다")
                index++
                if (index == entry.index) break
            }
            val start = counter.count
            val data = withSum(scanner.data(), span.byteSum)
            return budget.guard(OwningStream(data, input)) { counter.count - start + RATIO_SLACK }
        } catch (t: Throwable) {
            input.close()
            throw t
        }
    }

    /**
     * 순차 추출. **목록 없이도 돈다** — 머리를 만나는 대로 항목을 만든다(번호는 머리의 차례라 목록과 같다).
     * 만화 뷰어의 solid 패스가 압축 tar 를 두 번(목록 한 번, 패스 한 번) 풀지 않게 하려는 것이다.
     *
     * 건너뛰는 항목의 자료는 압축한 스트림에서 **풀어서 버린다** — 그 양을 예산에 올린다(solid 7z 와 같은 규칙:
     * 아무것도 고르지 않고 지나가는 것만으로 상한에 닿을 수 있어야 한다). 압축 안 한 파일은 자리만 옮긴다.
     */
    override fun extractSequentially(sink: EntrySink) {
        val known = listed
        val built = if (known == null) ArrayList<ArchiveEntry>(64) else null
        val builtSpans = if (known == null) ArrayList<Span>(64) else null
        var reachedEnd = false
        withScanner(limits.maxTotalOutput, onDrain = { budget.addOutput(it) }) { scanner, consumedInput ->
            val buf = ByteArray(COPY_BUFFER)
            // 압축 안 한 파일의 분자는 **고른 항목의 자료를 읽은 양**이다(자리 옮기기는 소비가 아니다).
            var readSelected = 0L
            fun progress() = sink.consumed(if (seekable) readSelected else consumedInput())
            var index = -1
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("푸는 중에 중단되었다")
                val h = scanner.next() ?: break
                index++
                count(index)
                val entry = known?.getOrNull(index) ?: entryOf(index, h)
                built?.add(entry)
                builtSpans?.add(Span(h.dataOffset, h.dataSize, h.byteSum))
                val out = sink.begin(entry)
                if (out == null) {
                    progress()
                    continue
                }
                if (!entry.isReadable) {
                    // 소비자가 링크·희소 파일 같은 것에 받을 곳을 주었다. ZIP 이 `open` 의 `require` 로 거절하는 것과
                    // 같게 그 항목만 실패로 돌린다 — 희소 파일의 조각을 파일 내용으로 쓰면 다른 파일이 된다.
                    sink.finish(entry, 0L, IllegalArgumentException("읽을 수 없는 항목이다"))
                    continue
                }
                val start = consumedInput()
                // 압축비의 분모는 이 항목을 읽는 동안 파일에서 가져간 압축 바이트다. 해제기가 미리 가져간 입력
                // ([RATIO_SLACK] 이내)이 앞 항목의 몫으로 잡히므로 그만큼 얹는다 — 비율을 낮게 보는 쪽으로만 틀린다.
                val guarded = budget.guard(
                    withSum(scanner.data(), h.byteSum),
                    if (seekable) null else ({ consumedInput() - start + RATIO_SLACK }),
                )
                var written = 0L
                var failure: Throwable? = null
                try {
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("푸는 중에 중단되었다")
                        val n = guarded.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        if (seekable) readSelected += n
                        progress()
                    }
                } catch (t: Throwable) {
                    if (t is InterruptedIOException) throw t
                    // 아카이브 전체를 무효로 만드는 상한은 항목 실패로 세지 않는다.
                    if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
                    failure = t
                }
                sink.finish(entry, written, failure)
                progress()
            }
            reachedEnd = true
        }
        // 끝까지 갔으면 그 머리들이 곧 목록이다 — 뒤에 `entries` 를 불러도 다시 풀지 않는다. 자료의 자리도 함께
        // 둔다([open] 이 쓴다 — 목록만 세우고 자리를 비워 두면 그 뒤의 `open` 이 널에 넘어진다).
        if (reachedEnd && built != null && builtSpans != null && listed == null) {
            synchronized(this) {
                if (listed == null) {
                    spans = builtSpans
                    listed = built
                }
            }
        }
    }

    override fun inputBytesFor(selected: Set<Int>?): Long {
        if (!seekable) return source.length.takeIf { it > 0 } ?: -1L
        var total = 0L
        for (e in entries) {
            if (e.isReadable && (selected == null || e.index in selected)) total = saturatingAdd(total, spans!![e.index].dataSize)
        }
        return total
    }

    override fun close() = Unit

    /** 채널에서 [size] 바이트까지. 닫으면 채널도 닫는다. */
    private class ChannelEntryStream(private val channel: SeekableByteChannel, size: Long) : InputStream() {
        private var left = size

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (left <= 0) return -1
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("읽는 중에 중단되었다")
            val n = interruptible { channel.read(ByteBuffer.wrap(b, off, minOf(len.toLong(), left).toInt())) }
            if (n < 0) throw EOFException("아카이브가 중간에 끝났다")
            left -= n
            return n
        }

        override fun close() = channel.close()
    }

    /** 항목의 자료를 읽고, 닫을 때 밑의 스트림(파일)까지 닫는다. */
    private class OwningStream(private val data: InputStream, private val owner: InputStream) : InputStream() {
        override fun read(): Int = data.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = data.read(b, off, len)
        override fun close() = owner.close()
    }

    internal companion object {
        private const val COPY_BUFFER = 64 * 1024

        /** 해제기가 미리 가져가는 입력의 여유. gzip 은 8 KiB 씩, xz 는 LZMA2 조각(최대 64 KiB)씩 가져간다. */
        const val RATIO_SLACK = 64L * 1024

        /** 검사값이 있으면 다 읽었을 때 견준다. */
        private fun withSum(input: InputStream, byteSum: Long): InputStream =
            if (byteSum >= 0) ByteSumStream(input, byteSum) else input
    }
}

/** 음이 아닌 두 크기의 합. `Long` 을 넘으면 `Long.MAX_VALUE` — 상한과 견주는 자리가 넘친 음수로 검사를 지나지 않게. */
internal fun saturatingAdd(a: Long, b: Long): Long = if (b > Long.MAX_VALUE - a) Long.MAX_VALUE else a + b

/**
 * 다 읽었을 때 바이트의 합이 [expected] 와 같은가 본다 — cpio `070702` 형식의 검사값이다. 이름은 'crc' 지만 CRC 가 아니라
 * **자료 바이트를 더한 것**(32비트에서 넘친 것은 버린다)이다. 다르면 깨진 항목이다.
 */
private class ByteSumStream(input: InputStream, private val expected: Long) : FilterInputStream(input) {
    private var sum = 0L

    override fun read(): Int {
        val b = `in`.read()
        if (b < 0) check() else sum = (sum + b) and 0xFFFF_FFFFL
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = `in`.read(b, off, len)
        if (n < 0) {
            check()
        } else {
            var s = sum
            for (i in off until off + n) s += b[i].toInt() and 0xFF
            sum = s and 0xFFFF_FFFFL
        }
        return n
    }

    private fun check() {
        if (sum != (expected and 0xFFFF_FFFFL)) throw IOException("항목의 검사값이 맞지 않는다")
    }
}
