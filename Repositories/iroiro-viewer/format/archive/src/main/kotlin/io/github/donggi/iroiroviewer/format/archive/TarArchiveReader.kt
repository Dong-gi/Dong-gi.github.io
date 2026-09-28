package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.channels.SeekableByteChannel

/**
 * tar 와 그것을 싼 압축 스트림(`.tar.gz`·`.tgz`·`.tar.bz2`·`.tbz2`·`.tar.xz`·`.txz`, 만화 `.cbt`)을 읽는다.
 *
 * ## 두 모양이 비용을 가른다
 *
 * | | 목록 | 항목 하나 열기 | 순차 추출 |
 * |---|---|---|---|
 * | **tar**(압축 안 함, 파일) | 머리만 읽고 자료는 **자리를 옮겨** 건너뛴다 — 항목 1만 개에 5 MB 를 읽는다 | 자리로 바로 간다(무작위 접근) | 고른 것만 읽는다 |
 * | **tar.gz·bz2·xz** | 스트림을 **처음부터 끝까지 푼다**(머리가 자료 사이사이에 있다) | 앞의 항목을 전부 풀어서 버린다 | 한 번에 끝까지 |
 *
 * 압축한 tar 는 solid 7z 와 같은 처지다 — 한 항목을 보려면 앞엣것을 다 풀어야 한다. 그래서 같은 규칙을 쓴다:
 * **건너뛸 양(풀어서 버릴 양)을 `maxTotalOutput` 으로 묶고**, 목록은 게으르게 만들어 만화 뷰어의 패스
 * (`extractSequentially` 만 쓴다)가 목록 한 번을 더 풀지 않게 한다. 목록 자체도 같은 상한에 묶인다 —
 * 기본값(1 GiB)으로 여는 압축 목록 화면에서 **풀린 크기가 1 GiB 를 넘는 압축 tar 는 열리지 않는다.**
 *
 * ## 막는 것 — ZIP·7z·RAR 과 같은 방어
 *
 * - 머리 수는 `maxEntries`, 항목 하나는 `maxSingleOutput`, 한 번의 작업은 `maxTotalOutput`([EntryBudget]).
 * - 압축비는 **그 항목을 읽는 동안 파일에서 소비한 압축 바이트**가 분모다(압축 안 한 tar 는 1:1 이라 걸지 않는다).
 * - 경로 탈출은 [ArchiveEntry.sanitize] 가 막는다 — ZIP 과 같은 관문을 지난다.
 * - 링크·하드링크·장치·FIFO·희소 파일은 [ArchiveEntry.isLink] 로 걸러 풀지 않는다(RAR 의 링크와 같은 취급).
 * - 메타 머리(긴 이름·PAX)의 크기와 **이름 바이트의 합**은 [TarLimits] 로 묶는다 — 압축한 tar 는 작은 파일이 큰
 *   이름 수천 개로 풀려 목록만으로 힙을 채울 수 있다. 이 클래스가 commons-compress 의 tar 읽기를 쓰지 않는 까닭이
 *   그것이다([TarScanner] 주석).
 * - 취소는 스레드 인터럽트로 닿는다 — 머리 읽기·건너뛰기·자료 읽기가 모두 인터럽트를 본다.
 *
 * ## 이름
 *
 * tar 는 이름의 인코딩을 적지 않는다(PAX `path` 만 UTF-8 이 약속이다). 그래서 머리의 이름 바이트는 ZIP 과
 * **같은 판정**([EntryNameDecoder])을 지난다 — 한국어 윈도우 도구가 CP949 로 적은 이름이 그대로 살아난다.
 *
 * @param compression 매직으로 이미 정한 것. [Archives.open] 이 준다.
 */
class TarArchiveReader(
    private val source: DocumentSource,
    private val budget: EntryBudget,
    private val limits: ParseLimits = ParseLimits.DEFAULT,
    private val compression: Compression = Compression.NONE,
) : ArchiveReader {

    enum class Compression(val kind: ArchiveKind) {
        NONE(ArchiveKind.TAR),
        GZIP(ArchiveKind.TAR_GZ),
        BZIP2(ArchiveKind.TAR_BZIP2),
        XZ(ArchiveKind.TAR_XZ),
    }

    /** tar 가 아니다 — 압축 스트림은 맞는데 안이 tar 가 아니다(`.gz` 로 싼 로그 파일 하나 같은 것). */
    class NotTarException : IllegalStateException("tar 가 아니다")

    override val formatId: FormatId? get() = null

    override val kind: ArchiveKind get() = compression.kind

    /**
     * 자리로 바로 갈 수 있는가. 압축하지 않았고 원본이 채널을 줄 때만이다 — 스트림만 주는 원본
     * (아카이브 안의 아카이브 같은 것)이면 압축 안 한 tar 도 앞에서부터 읽는다.
     */
    private val seekable: Boolean = compression == Compression.NONE && source.asFile() != null

    override val randomAccess: Boolean get() = seekable

    /** 목록의 항목마다 자료의 자리와 크기. [open] 이 무작위 접근에 쓴다. */
    private class Span(val dataOffset: Long, val dataSize: Long)

    /**
     * 목록. 순차 추출이 끝까지 가면 거기서 세운 것을 여기 둔다 — 다른 스레드가 [entries] 로 읽을 수 있어 `@Volatile`
     * 이고, [spans] 를 **먼저** 쓴 뒤 이것을 쓴다(이것을 본 스레드는 [spans] 도 본다).
     */
    @Volatile
    private var listed: List<ArchiveEntry>? = null
    private var spans: List<Span>? = null

    /** 예산에 이미 센 머리 수. 목록과 순차 추출이 같은 예산으로 두 번 세지 않게 한다. */
    private var counted = 0

    init {
        // **첫 머리를 여기서 본다.** 매직은 압축 스트림까지만 말해 준다 — `.gz` 로 싼 로그 하나를 tar 로 여는
        // 척하면 목록이 '깨졌다' 로 끝나는데, 사실은 '다루지 않는 형식' 이다.
        if (!startsLikeTar(source, compression)) throw NotTarException()
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
                where += Span(h.dataOffset, h.dataSize)
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

    private fun entryOf(index: Int, h: TarEntryHeader): ArchiveEntry {
        val decoded = h.paxName?.let { EntryNameDecoder.Decoded(it, "UTF-8(PAX)") }
            ?: EntryNameDecoder.decode(
                raw = h.nameBytes,
                utf8Flag = false,
                fallback = String(h.nameBytes, Charsets.ISO_8859_1),
            )
        val isDirectory = h.kind == TarEntryHeader.Kind.DIRECTORY
        return ArchiveEntry(
            index = index,
            name = decoded.name,
            safeName = ArchiveEntry.sanitize(decoded.name),
            declaredSize = h.declaredSize,
            // 압축 안 한 tar 는 저장된 그대로다. 압축한 tar 는 항목마다의 압축 크기가 없다(스트림 하나다).
            compressedSize = if (compression == Compression.NONE) h.dataSize else -1L,
            isDirectory = isDirectory,
            isLink = h.kind == TarEntryHeader.Kind.LINK ||
                h.kind == TarEntryHeader.Kind.DEVICE ||
                h.kind == TarEntryHeader.Kind.UNREADABLE,
            isEncrypted = false,
            crc = -1L,
            nameCharset = decoded.charsetLabel,
            lastModified = h.mtimeMillis,
        )
    }

    // ---- 여는 길 ----------------------------------------------------------------------

    /** 풀린 tar 스트림과 그 밑의 압축 입력 계수기([openTarStream]). */
    private fun openStream(): Pair<InputStream, CountingInputStream> = openTarStream(source, compression)

    /**
     * 처음부터 머리를 훑는 [TarScanner] 를 연다. 압축 안 한 파일이면 채널로 — 자료를 자리 옮기기로 건너뛴다.
     *
     * @param cap 훑으며 소비할 수 있는 tar 스트림 바이트.
     */
    private inline fun <T> withScanner(
        cap: Long,
        noinline onDrain: (Long) -> Unit = {},
        block: (TarScanner, () -> Long) -> T,
    ): T {
        if (seekable) {
            val channel = openChannel()
            return channel.use { ch ->
                val src = ChannelTarSource(ch)
                block(TarScanner(src, Long.MAX_VALUE), { src.position() })
            }
        }
        val (input, counter) = openStream()
        return input.use {
            block(TarScanner(StreamTarSource(input), cap, onDrain = onDrain), { counter.count })
        }
    }

    private fun openChannel(): SeekableByteChannel =
        source.openChannel() ?: throw IOException("tar 파일의 채널을 열 수 없다")

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
                return budget.guard(ChannelEntryStream(channel, span.dataSize))
            } catch (t: Throwable) {
                channel.close()
                throw t
            }
        }

        // **앞엣것을 풀어서 버려야 닿는다.** 7z 의 `open` 과 같은 규칙 — 풀어야 할 양이 총량 상한을 넘으면 스트림을
        // 주지 않는다. 적힌 크기는 공격자의 값이지만 tar 에서는 그 크기가 곧 다음 머리의 자리라 실제로 그만큼 푼다.
        val skipCost = all.take(entry.index).sumOf { spans!![it.index].dataSize }
        if (skipCost > limits.maxTotalOutput) {
            throw ParseLimitExceededException(
                "maxTotalOutput",
                "이 항목에 닿으려면 ${skipCost}바이트를 풀어야 한다 (상한 ${limits.maxTotalOutput})",
            )
        }
        val (input, counter) = openStream()
        try {
            val scanner = TarScanner(StreamTarSource(input), limits.maxTotalOutput + span.dataSize + TarFormat.RECORD)
            var index = -1
            while (true) {
                scanner.next() ?: throw IOException("tar 항목이 목록과 다르다")
                index++
                if (index == entry.index) break
            }
            val start = counter.count
            val data = scanner.data()
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
     * 건너뛰는 항목의 자료는 압축한 tar 에서 **풀어서 버린다** — 그 양을 예산에 올린다(solid 7z 와 같은 규칙:
     * 아무것도 고르지 않고 지나가는 것만으로 상한에 닿을 수 있어야 한다). 압축 안 한 tar 는 자리만 옮긴다.
     */
    override fun extractSequentially(sink: EntrySink) {
        val known = listed
        val built = if (known == null) ArrayList<ArchiveEntry>(64) else null
        val builtSpans = if (known == null) ArrayList<Span>(64) else null
        var reachedEnd = false
        withScanner(limits.maxTotalOutput, onDrain = { budget.addOutput(it) }) { scanner, consumedInput ->
            val buf = ByteArray(COPY_BUFFER)
            // 압축 안 한 tar 의 분자는 **고른 항목의 자료를 읽은 양**이다(자리 옮기기는 소비가 아니다).
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
                builtSpans?.add(Span(h.dataOffset, h.dataSize))
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
                    scanner.data(),
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
        return entries.filter { it.isReadable && (selected == null || it.index in selected) }
            .sumOf { spans!![it.index].dataSize }
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
            val n = interruptible { channel.read(java.nio.ByteBuffer.wrap(b, off, minOf(len.toLong(), left).toInt())) }
            if (n < 0) throw java.io.EOFException("tar 가 중간에 끝났다")
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
        private const val RATIO_SLACK = 64L * 1024

        /**
         * 풀린 tar 스트림과, 그 밑에서 **파일에서 읽은 압축 바이트**를 세는 것. 닫으면 파일도 닫힌다.
         *
         * gzip·bzip2 는 **이어 붙인 스트림**(pigz·pbzip2 가 만든다)까지 푼다 — 첫 조각에서 멈추면 뒤의 항목이
         * 통째로 사라진다. xz 는 `XZInputStream` 이 원래 이어 붙인 것을 읽는다.
         */
        private fun openTarStream(source: DocumentSource, compression: Compression): Pair<InputStream, CountingInputStream> {
            val raw = source.openStream()
            try {
                // 세는 것은 버퍼 **위**다 — 해제기가 실제로 가져간 양이 나온다. 아래에 두면 버퍼가 미리 읽은 64 KiB 가
                // 항목 경계에서 앞 항목의 몫으로 잡혀, 뒤 항목의 압축비가 부풀어 오른다.
                val counted = CountingInputStream(BufferedInputStream(raw, 64 * 1024))
                val stream: InputStream = when (compression) {
                    Compression.NONE -> counted
                    Compression.GZIP -> GzipCompressorInputStream.builder()
                        .setInputStream(counted)
                        .setDecompressConcatenated(true)
                        .get()
                    Compression.BZIP2 -> BZip2CompressorInputStream(counted, true)
                    Compression.XZ -> XZInputStream(counted, TarLimits.XZ_MEMORY_LIMIT_KIB)
                }
                return stream to counted
            } catch (t: Throwable) {
                raw.close()
                throw t
            }
        }

        /**
         * 첫 머리가 tar 인가 — 검사합이 맞는 머리 한 칸이거나 **빈 tar** 다. 목록은 만들지 않는다(판별이 쓴다 —
         * 리더를 만들면 압축 안 한 tar 는 목록을 곧바로 세우고, 판별의 좁은 상한에서 큰 tar 가 '아니다' 로 떨어진다).
         *
         * 빈 tar 는 **끝까지 0** 이어야 한다. 앞머리가 0 인 것만으로는 모른다 — ISO 이미지는 앞 32 KiB 가 0 이고
         * (`.iso` 도 목록에서 '압축' 종류다), 그것을 빈 tar 로 보면 '다루지 않는 형식' 대신 '항목 0개' 가 뜬다.
         */
        internal fun startsLikeTar(source: DocumentSource, compression: Compression): Boolean =
            openTarStream(source, compression).first.use { input ->
                val head = ByteArray(TarFormat.RECORD)
                var got = 0
                while (got < head.size) {
                    val r = input.read(head, got, head.size - got)
                    if (r < 0) break
                    got += r
                }
                if (got < head.size) return@use false
                if (TarFormat.isZero(head)) restIsZero(input) else TarFormat.checksumOk(head)
            }

        /** 남은 바이트가 [TarLimits.MAX_EMPTY_TAR_BYTES] 안에서 끝나고 전부 0 인가. 넘으면 빈 tar 가 아니다. */
        private fun restIsZero(input: InputStream): Boolean {
            val buf = ByteArray(8 * 1024)
            var seen = 0L
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("읽는 중에 중단되었다")
                val n = input.read(buf)
                if (n < 0) return true
                for (i in 0 until n) if (buf[i].toInt() != 0) return false
                seen += n
                if (seen > TarLimits.MAX_EMPTY_TAR_BYTES) return false
            }
        }
    }
}
