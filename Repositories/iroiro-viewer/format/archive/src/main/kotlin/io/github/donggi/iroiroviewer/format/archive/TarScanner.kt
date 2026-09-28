package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * tar 를 읽어 오는 곳. **건너뛰기의 값이 둘로 갈린다** — 압축하지 않은 tar 파일은 자리만 옮기면 되고(공짜),
 * 압축한 tar 는 풀어서 버려야 한다(풀어낸 만큼의 시간).
 */
internal interface TarSource {
    fun read(b: ByteArray, off: Int, len: Int): Int

    /** 정확히 [n] 바이트를 지나간다. 모자라면 던진다. 풀어서 지나간 양은 [TarScanner.onDrain] 이 받는다. */
    fun skip(n: Long): Long
}

/** 풀린 스트림(gzip·bzip2·xz) 또는 무작위 접근이 안 되는 원본. 건너뛰기가 곧 읽어서 버리기다. */
internal class StreamTarSource(private val input: InputStream) : TarSource {

    private val scratch = ByteArray(64 * 1024)

    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)

    override fun skip(n: Long): Long {
        var left = n
        while (left > 0) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("건너뛰는 중에 중단되었다")
            val r = input.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (r < 0) throw EOFException("tar 가 중간에 끝났다")
            left -= r
        }
        return n
    }
}

/** 압축하지 않은 tar 파일. 건너뛰기는 자리를 옮기는 것뿐이다. */
internal class ChannelTarSource(private val channel: SeekableByteChannel) : TarSource {

    override fun read(b: ByteArray, off: Int, len: Int): Int = interruptible { channel.read(ByteBuffer.wrap(b, off, len)) }

    override fun skip(n: Long): Long = interruptible {
        val to = channel.position() + n
        // 자료가 파일보다 길다고 적힌 머리. 자리만 옮기면 조용히 끝을 넘어 다음 읽기가 EOF 가 된다 —
        // 스트림 쪽과 같은 말(잘린 파일)을 하게 여기서 끊는다.
        if (to > channel.size()) throw EOFException("tar 가 중간에 끝났다")
        channel.position(to)
        n
    }

    fun position(): Long = channel.position()
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
 * tar 한 항목의 머리. 메타 머리(긴 이름·PAX)를 **이미 적용한** 모양이다.
 *
 * @property dataSize 머리 뒤에 실제로 놓인 자료 바이트. 링크·장치·폴더는 0 이다(아래 [TarScanner.next]).
 * @property declaredSize 화면에 적을 크기. 대개 [dataSize] 와 같다.
 */
internal class TarEntryHeader(
    val nameBytes: ByteArray,
    /** PAX `path` 가 UTF-8 로 읽혔으면 그 이름. 이것이 [nameBytes] 보다 먼저다. */
    val paxName: String?,
    val kind: Kind,
    val dataSize: Long,
    val declaredSize: Long,
    val mtimeMillis: Long,
    /** tar 스트림 안에서 자료가 시작하는 자리. 압축하지 않은 tar 의 무작위 접근이 이것을 쓴다. */
    val dataOffset: Long,
) {
    enum class Kind {
        FILE,
        DIRECTORY,

        /** 심볼릭 링크·하드링크. 내용이 파일이 아니라 경로다. */
        LINK,

        /** 문자·블록 장치, FIFO. 내용이 없다. */
        DEVICE,

        /** GNU 희소 파일·다음 볼륨으로 이어지는 조각. 자료가 있지만 그대로 풀면 다른 파일이 된다. */
        UNREADABLE,
    }
}

/**
 * tar 머리를 **하나씩** 읽는다.
 *
 * ## commons-compress 의 `TarArchiveInputStream` 을 쓰지 않는 이유
 *
 * 1. **메타 머리를 끝까지 메모리에 모은다.** GNU 긴 이름은 머리에 적힌 크기만큼 `ByteArrayOutputStream` 에 쌓고,
 *    PAX 값도 적힌 길이만큼 배열을 잡는다. 크기는 공격자가 적는 값이다 — 1 GiB 라고 적은 긴 이름 하나에 앱이 죽는다.
 *    여기서는 [TarLimits] 로 묶는다.
 * 2. **이름의 원본 바이트를 주지 않는다.** 이름을 기기 기본 인코딩으로 푼 문자열만 준다. 한국어 윈도우 도구가
 *    CP949 로 적은 이름을 가려낼 길이 없다. 여기서는 바이트를 넘겨 ZIP 과 같은 판정([EntryNameDecoder])을 탄다.
 * 3. **링크의 자료 크기를 믿는다.** POSIX ustar 는 링크·장치·폴더에는 자료 레코드가 없다고 적는데, 크기 칸을
 *    채워 두는 도구가 있다. 그 크기만큼 건너뛰면 다음 머리를 놓친다.
 *
 * 머리 하나는 512바이트라 읽는 쪽은 짧다. 대신 판단이 전부 여기 있다.
 *
 * ## 쓰는 법
 *
 * [next] 로 머리를 얻고, 필요하면 [data] 로 자료를 읽는다. 다 읽지 않아도 된다 — 다음 [next] 가 남은 자료와
 * 채움을 건너뛴다. 그 건너뛰기는 [TarSource] 에 따라 공짜(자리 옮기기)이거나 풀어서 버리기다.
 *
 * @param scanCap tar 스트림에서 소비할 수 있는 최대 바이트(머리·자료·채움 전부). 압축한 tar 에서 한 항목에 닿거나
 *   목록을 만드는 비용의 상한이다 — 7z 의 '건너뛸 양' 과 같은 뜻이라 같은 상한 이름(`maxTotalOutput`)으로 끊는다.
 * @param onDrain 건너뛰느라 **읽어서 버린** 바이트. 순차 추출이 예산에 올린다(solid 7z 와 같은 규칙).
 */
internal class TarScanner(
    private val src: TarSource,
    private val scanCap: Long = Long.MAX_VALUE,
    private val drains: Boolean = src !is ChannelTarSource,
    private val onDrain: (Long) -> Unit = {},
) {

    /** 지금까지 tar 스트림에서 지나간 바이트. */
    var position: Long = 0L
        private set

    private var remaining = 0L
    private var padding = 0L
    private var ended = false

    /** 하드링크의 크기 칸을 판단하려고 미리 읽은 머리 한 칸. */
    private var pending: ByteArray? = null

    /** 지금까지 내놓은 항목 이름의 바이트 합. */
    private var nameBytesTotal = 0L

    /**
     * 다음 항목. 끝이면 null.
     *
     * ## 종류마다 자료가 있는가
     *
     * | 종류 | 자료 |
     * |---|---|
     * | `0`·NUL·`7`·모르는 글자 | 크기만큼. POSIX 는 모르는 종류를 **보통 파일로** 읽으라고 적는다 |
     * | `5`(폴더)·`2`(심볼릭 링크)·`3`·`4`·`6`(장치·FIFO) | **없다.** 크기 칸은 무시한다 |
     * | `1`(하드링크) | PAX 머리가 있으면 크기만큼(pax 는 하드링크에 본문을 허락한다), 없으면 아래 판단 |
     * | `D`(GNU 폴더 목록) | 크기만큼 있고, 항목은 폴더다 |
     * | `S`(옛 GNU 희소)·`M`(다음 볼륨에서 이어짐) | 크기만큼 있고, 풀지 않는다 |
     * | `L`·`K`·`x`·`X`·`g`·`V`·`N` | 메타 머리. 항목이 아니다 |
     *
     * **하드링크의 크기 칸** — 옛 도구는 크기를 채우고 자료는 싣지 않았고, POSIX 1988 은 그것을 무시하라고 했다.
     * pax 머리 없이 크기가 적힌 하드링크를 만나면 **다음 칸을 미리 읽어** 그것이 머리(검사합이 맞는다)거나 끝
     * 표시면 자료가 없는 것으로, 아니면 자료가 있는 것으로 본다. 하드링크의 자료는 어차피 풀지 않으므로(링크다)
     * 건너뛰기만 맞으면 된다.
     */
    fun next(): TarEntryHeader? {
        if (ended) return null
        skipRest()
        var longName: ByteArray? = null
        var pax = PaxHeaders.EMPTY
        var sawPax = false
        while (true) {
            val rec = readRecord() ?: return end()
            if (TarFormat.isZero(rec)) return end()
            if (!TarFormat.checksumOk(rec)) throw IOException("tar 머리의 검사합이 맞지 않는다")
            val type = TarFormat.type(rec)
            val size = TarFormat.size(rec)
            when (type) {
                'L' -> {
                    longName = readMeta(size, TarLimits.MAX_LONG_NAME_BYTES).trimEndNul()
                    continue
                }
                'K' -> {
                    // 긴 링크 대상. 링크는 풀지 않으므로 대상은 쓰지 않지만 크기는 묶는다.
                    readMeta(size, TarLimits.MAX_LONG_NAME_BYTES)
                    continue
                }
                'x', 'X' -> {
                    pax = pax.merge(PaxHeaders.parse(readMeta(size, TarLimits.MAX_PAX_BYTES)))
                    sawPax = true
                    continue
                }
                'g' -> {
                    // 전역 PAX 머리. `git archive` 가 커밋 번호를 여기 싣는다. 뒤 항목 전부에 걸리는 값인데
                    // 우리가 쓰는 열쇠(이름·크기·시각)를 전역으로 적는 도구는 없다시피 해 읽고 버린다.
                    readMeta(size, TarLimits.MAX_PAX_BYTES)
                    continue
                }
                'V', 'N' -> {
                    // 볼륨 이름·옛 GNU 긴 이름 목록. 항목이 아니다.
                    skipBytes(TarFormat.padded(size))
                    continue
                }
            }
            return entry(rec, type, size, longName, pax, sawPax)
        }
    }

    private fun entry(
        rec: ByteArray,
        type: Char,
        headerSize: Long,
        longName: ByteArray?,
        pax: PaxHeaders,
        sawPax: Boolean,
    ): TarEntryHeader {
        val size = pax.size ?: headerSize
        // 이름의 차례: PAX `path` → GNU 긴 이름 → 머리의 이름(ustar 면 앞머리를 이은 것). PAX 경로는 UTF-8 이
        // 약속이다 — 그렇게 읽히지 않거나 `hdrcharset=BINARY` 면 바이트를 넘겨 판정에 맡긴다.
        val paxPath = pax.path
        val paxName = if (paxPath != null && !pax.binaryNames) strictUtf8(paxPath) else null
        val nameBytes = paxPath ?: longName ?: TarFormat.nameBytes(rec)
        // 이름은 목록이 끝까지 들고 있다 — 하나의 길이와 합을 함께 묶는다([TarLimits.MAX_NAME_BYTES_TOTAL]).
        // PAX 경로는 머리 상한(1 MiB)까지 올 수 있어 여기서 긴 이름과 같은 64 KiB 로 다시 자른다.
        if (nameBytes.size > TarLimits.MAX_LONG_NAME_BYTES) {
            throw ParseLimitExceededException("tarHeader", "이름 ${nameBytes.size}바이트 (상한 ${TarLimits.MAX_LONG_NAME_BYTES})")
        }
        nameBytesTotal += nameBytes.size
        if (nameBytesTotal > TarLimits.MAX_NAME_BYTES_TOTAL) {
            throw ParseLimitExceededException("tarHeader", "이름 합 ${nameBytesTotal}바이트 (상한 ${TarLimits.MAX_NAME_BYTES_TOTAL})")
        }
        val endsWithSlash = nameBytes.isNotEmpty() && nameBytes.last() == '/'.code.toByte()
        var kind: TarEntryHeader.Kind
        var dataSize: Long
        when (type) {
            '5' -> {
                kind = TarEntryHeader.Kind.DIRECTORY
                dataSize = 0
            }
            'D' -> {
                kind = TarEntryHeader.Kind.DIRECTORY
                dataSize = size
            }
            '2', '3', '4', '6' -> {
                kind = if (type == '2') TarEntryHeader.Kind.LINK else TarEntryHeader.Kind.DEVICE
                dataSize = 0
            }
            '1' -> {
                kind = TarEntryHeader.Kind.LINK
                dataSize = if (size == 0L || sawPax) size else hardlinkData(size)
            }
            'S', 'M' -> {
                if (type == 'S') skipGnuSparseExtensions(rec)
                kind = TarEntryHeader.Kind.UNREADABLE
                dataSize = size
            }
            else -> {
                // '0'·NUL·'7' 과 모르는 글자. 옛 V7 tar 는 폴더를 '이름 끝의 /' 로만 표시했다.
                kind = if ((type == '0' || type.code == 0) && endsWithSlash) TarEntryHeader.Kind.DIRECTORY else TarEntryHeader.Kind.FILE
                dataSize = if (kind == TarEntryHeader.Kind.DIRECTORY) 0 else size
            }
        }
        if (kind == TarEntryHeader.Kind.FILE && pax.sparse) kind = TarEntryHeader.Kind.UNREADABLE
        val mtime = pax.mtimeMillis ?: TarFormat.mtimeMillis(rec)
        remaining = dataSize
        padding = TarFormat.padded(dataSize) - dataSize
        return TarEntryHeader(
            nameBytes = nameBytes,
            paxName = paxName,
            kind = kind,
            dataSize = dataSize,
            declaredSize = if (kind == TarEntryHeader.Kind.FILE) dataSize else 0L,
            mtimeMillis = mtime,
            dataOffset = position,
        )
    }

    /** 위 표의 하드링크 판단. 미리 읽은 칸은 머리였으면 [pending] 에 두고, 자료였으면 자료의 첫 칸으로 센다. */
    private fun hardlinkData(size: Long): Long {
        val peek = readRecord() ?: return 0L
        if (TarFormat.isZero(peek) || TarFormat.checksumOk(peek)) {
            pending = peek
            position -= TarFormat.RECORD
            return 0L
        }
        // 자료였다. 첫 칸은 이미 지나갔으므로 나머지만 건너뛰게 둔다 — 자리는 [position] 이 이미 센다.
        remaining = 0L
        skipBytes((TarFormat.padded(size) - TarFormat.RECORD).coerceAtLeast(0L))
        return 0L
    }

    /** 옛 GNU 희소 머리 뒤의 확장 머리들. 자료보다 먼저 놓인다 — 건너뛰지 않으면 자료를 머리로 읽는다. */
    private fun skipGnuSparseExtensions(header: ByteArray) {
        var more = header[TarFormat.GNU_SPARSE_EXTENDED].toInt() != 0
        while (more) {
            val ext = readRecord() ?: throw EOFException("tar 가 중간에 끝났다")
            more = ext[TarFormat.GNU_SPARSE_EXTENSION_EXTENDED].toInt() != 0
        }
    }

    /**
     * 지금 항목의 자료. **닫아도 원본은 닫히지 않는다.** 다 읽지 않아도 다음 [next] 가 나머지를 건너뛴다.
     */
    fun data(): InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (remaining <= 0) return -1
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("읽는 중에 중단되었다")
            val n = src.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n < 0) throw EOFException("tar 가 중간에 끝났다")
            remaining -= n
            advance(n.toLong())
            return n
        }

        override fun close() = Unit
    }

    /** 지금 항목의 남은 자료와 채움을 지나간다. */
    private fun skipRest() {
        val n = remaining + padding
        remaining = 0L
        padding = 0L
        if (n > 0) skipBytes(n)
    }

    private fun skipBytes(n: Long) {
        if (n <= 0) return
        checkCap(n)
        src.skip(n)
        position += n
        if (drains) onDrain(n)
    }

    private fun readMeta(size: Long, cap: Int): ByteArray {
        if (size > cap) throw ParseLimitExceededException("tarHeader", "메타 머리 ${size}바이트 (상한 $cap)")
        val n = size.toInt()
        val buf = ByteArray(n)
        readFully(buf, n)
        skipBytes(TarFormat.padded(size) - size)
        return buf
    }

    /** 512바이트 한 칸. 칸 경계에서 깨끗하게 끝나면 null, 칸 한가운데에서 끝나면 잘린 파일이다. */
    private fun readRecord(): ByteArray? {
        pending?.let {
            pending = null
            position += TarFormat.RECORD
            return it
        }
        val rec = ByteArray(TarFormat.RECORD)
        val got = readUpTo(rec, TarFormat.RECORD)
        if (got == 0) return null
        if (got < TarFormat.RECORD) throw EOFException("tar 가 중간에 끝났다")
        return rec
    }

    private fun readFully(buf: ByteArray, n: Int) {
        if (readUpTo(buf, n) < n) throw EOFException("tar 가 중간에 끝났다")
    }

    private fun readUpTo(buf: ByteArray, n: Int): Int {
        var got = 0
        while (got < n) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("읽는 중에 중단되었다")
            val r = src.read(buf, got, n - got)
            if (r < 0) break
            got += r
        }
        advance(got.toLong())
        return got
    }

    private fun advance(n: Long) {
        checkCap(n)
        position += n
    }

    private fun checkCap(n: Long) {
        if (position + n > scanCap) {
            throw ParseLimitExceededException("maxTotalOutput", "tar 를 ${position + n}바이트까지 읽어야 한다 (상한 $scanCap)")
        }
    }

    private fun end(): TarEntryHeader? {
        ended = true
        return null
    }

    private fun ByteArray.trimEndNul(): ByteArray {
        var end = size
        while (end > 0 && this[end - 1].toInt() == 0) end--
        return if (end == size) this else copyOf(end)
    }

    private fun strictUtf8(raw: ByteArray): String? = try {
        java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(raw))
            .toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }
}
