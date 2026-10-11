package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.ArchivePath
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.text.Normalizer
import java.util.Locale
import java.util.zip.CRC32

/**
 * 파일 하나를 싼 압축 스트림 — 안이 tar 가 아닌 `.gz`·`.bz2`·`.xz`·`.lz4`·`.lzma`·`.Z` — 을 **항목 하나짜리 아카이브**로
 * 연다. 풀기는 압축 tar 와 같은 길(파일 작업 큐 → 임시 파일 → fsync → 원자적 rename)을 지난다.
 *
 * ## 목록은 앞 512바이트만 푼다
 *
 * 항목은 하나이고 그 이름·크기·시각은 머리와 파일 이름에서 온다. 압축을 끝까지 푸는 것은 사용자가 풀거나 그림을 열 때뿐이다.
 * 목록이 푸는 것은 `Archives` 가 안이 tar 인지 보려고 풀어 보는 **앞 512바이트**다(해제기가 한 번에 푸는 덩이까지 — bzip2 는
 * 블록 하나, 많아야 900 KB). 64 MiB 의 0 을 싼 65 KiB 짜리 폭탄도 그만큼만 푼다. 더 푸는 자리는 하나다 — 그 512바이트가
 * 0 이고 **안의 이름이 `.tar` 로 끝나면** 빈 tar 인지 보려고 1 MiB 까지 더 푼다(`TarArchiveReader.startsLikeTar`). 예전에는
 * 이름을 보지 않아 0 으로 시작하는 본문이면 언제나 1 MiB 를 풀었다. 판별에는 소비할 입력의 상한(4 MiB, `StreamLimits`)이 있고,
 * 넘으면 안을 보지 못한 채 이 리더로 연다 — 시각이 아니라 입력이라 같은 파일은 언제나 같은 답이다.
 *
 * ## 크기 — 정직한 것만 보인다
 *
 * | 형식 | 적힌 크기 | 보이는가 |
 * |---|---|---|
 * | gzip | 트레일러의 ISIZE — **멤버 하나**의 길이를 2^32 로 나눈 나머지 | **아니다**(-1). 이어 붙인 파일과 4 GiB 넘는 파일에서는 정직한 파일도 틀린다 |
 * | xz | 스트림마다 끝의 인덱스(블록의 풀린 크기)([XzIndex]) | 보인다. 해제기가 끝에서 인덱스와 실제를 견주므로 거짓이면 그때 깨진 파일이 된다 |
 * | LZMA | 머리의 '풀린 크기'(모르면 전부 `FF`) | 보인다. 해제기가 정확히 그만큼만 푼다 |
 * | LZ4 | 프레임 머리의 '내용 크기'(넣지 않아도 된다) | **아니다**. 프레임마다의 값이고 프레임은 이어 붙을 수 있다 |
 * | bzip2·`.Z` | 없다 | -1 |
 *
 * 보이는 크기도 **화면에만 쓴다**([ArchiveEntry.declaredSize]) — 상한은 언제나 실제로 푼 바이트를 센다([EntryBudget]):
 * 항목 하나 `maxSingleOutput`, 압축비 `maxCompressionRatio`(분모는 파일에서 실제로 읽은 압축 바이트), 총량 `maxTotalOutput`.
 *
 * ## 시각
 *
 * gzip 머리의 MTIME 이 있으면 그것, 없으면 **압축 파일 자신의 수정시각**이다. 지어낸 값이 아니다 — `bzip2`·`xz`·`gzip` 은
 * 압축할 때 원본의 시각을 압축 파일에 옮겨 두고 풀 때 되돌려 놓는다(`gunzip` 의 기본값도 그렇다). 그 도구들이 하는 것과 같다.
 *
 * ## 무작위 접근
 *
 * 항목이 하나라 '앞의 것을 풀어서 버려야 닿는' 일이 없다 — [randomAccess] 는 참이고 연속 압축(solid)이 아니다. 만화 뷰어는
 * 그림 한 장짜리를 번호로 여는 리더로 읽는다.
 */
class SingleStreamReader internal constructor(
    private val source: DocumentSource,
    private val budget: EntryBudget,
    val codec: StreamCodec,
) : ArchiveReader {

    override val formatId: FormatId? get() = null

    override val kind: ArchiveKind = ArchiveKind.streamOf(codec)

    override val randomAccess: Boolean get() = true

    private val entry: ArchiveEntry

    override val entries: List<ArchiveEntry>

    init {
        budget.beginEntry()
        var nameBytes: ByteArray? = null
        var mtimeSeconds = 0L
        var size = -1L
        when (codec) {
            StreamCodec.GZIP -> GzipHeaders.firstOf(source).let {
                nameBytes = it.nameBytes
                mtimeSeconds = it.mtimeSeconds
            }
            StreamCodec.LZMA -> size = LzmaHeader.knownSize(source.head(LzmaHeader.PROBE_BYTES))
            StreamCodec.XZ -> size = XzIndex.uncompressedSize(source)
            // 크기를 적지 않거나(bzip2·.Z), 적어도 파일 전체의 크기라고 할 수 없다(LZ4 — 위 표).
            StreamCodec.BZIP2, StreamCodec.LZ4, StreamCodec.Z -> Unit
        }
        val named = StreamNames.of(nameBytes, source.displayName)
        val mtime = if (mtimeSeconds > 0 && mtimeSeconds <= Long.MAX_VALUE / 1000) {
            mtimeSeconds * 1000
        } else {
            source.asFile()?.lastModified()?.coerceAtLeast(0L) ?: 0L
        }
        entry = ArchiveEntry(
            index = 0,
            name = named.name,
            safeName = ArchiveEntry.sanitize(named.name),
            declaredSize = size,
            // 압축된 자료가 곧 이 파일이다(머리·트레일러 몇십 바이트를 빼면).
            compressedSize = source.length.takeIf { it > 0 } ?: -1L,
            isDirectory = false,
            isLink = false,
            isEncrypted = false,
            crc = -1L,
            nameCharset = named.label,
            lastModified = mtime,
        )
        entries = listOf(entry)
    }

    override fun open(entry: ArchiveEntry): InputStream {
        require(entry.isReadable) { "읽을 수 없는 항목이다" }
        if (entry.index != 0) throw ParseLimitExceededException("index", "없는 엔트리")
        val (stream, counter) = CompressedStreams.open(source, codec)
        try {
            // 압축비의 분모는 파일에서 실제로 읽은 압축 바이트다 — 항목이 하나라 경계의 여유를 얹을 일이 없다.
            return budget.guard(stream) { counter.count }
        } catch (t: Throwable) {
            stream.close()
            throw t
        }
    }

    /**
     * 하나뿐인 항목을 푼다. 진행률의 분자는 **파일에서 읽어 들인 압축 바이트**(분모는 파일 크기)다 — 압축 tar 와 같다.
     *
     * 항목 하나의 실패(깨진 자료·압축비·크기 상한)는 [EntrySink.finish] 로 보고하고, 취소와 아카이브 전체를 끝내는 상한만
     * 위로 던진다(다른 리더와 같은 계약). 항목이 하나뿐이라 결과는 같아 보이지만, 쓰다 만 임시 파일을 소비자가 치우는 길이
     * 그것으로 갈린다.
     */
    override fun extractSequentially(sink: EntrySink) {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("푸는 중에 중단되었다")
        val out = sink.begin(entry) ?: return
        var written = 0L
        var failure: Throwable? = null
        var counter: CountingInputStream? = null
        try {
            val (stream, counted) = CompressedStreams.open(source, codec)
            counter = counted
            budget.guard(stream) { counted.count }.use { input ->
                val buf = ByteArray(COPY_BUFFER)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("푸는 중에 중단되었다")
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    written += n
                    sink.consumed(counted.count)
                }
            }
        } catch (t: Throwable) {
            if (t is InterruptedIOException) throw t
            // 아카이브 전체를 무효로 만드는 상한은 항목 실패로 세지 않는다.
            if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
            failure = t
        }
        sink.finish(entry, written, failure)
        counter?.let { sink.consumed(it.count) }
    }

    /** 파일 크기 — 압축 tar 와 같다(`ArchiveReader.inputBytesFor` 의 표). 고르지 않았으면 0. */
    override fun inputBytesFor(selected: Set<Int>?): Long {
        if (selected != null && 0 !in selected) return 0L
        return source.length.takeIf { it > 0 } ?: -1L
    }

    override fun close() = Unit

    private companion object {
        const val COPY_BUFFER = 64 * 1024
    }
}

/**
 * 항목 하나짜리의 이름 — **gzip 의 FNAME** 이 있으면 그것, 없으면 **압축 파일 이름**에서 짓는다.
 *
 * ## FNAME
 *
 * 머리에 적힌 원본 이름이다(GNU gzip 이 기본으로 적는다 — `gzip -n` 이면 없다). 공격자가 적는 값이므로:
 *
 * 1. **경로의 마지막 조각만** 쓴다(`../../x`·`/etc/passwd`·`C:\x` → `x`·`passwd`·`x`). 조각을 자르는 것은 이름을 글자로
 *    읽은 **뒤**다 — CP949·Shift_JIS 의 둘째 바이트에 `0x5C`(역슬래시)가 온다.
 * 2. **글자로 읽는 것은 tar 이름과 같은 판정**([EntryNameDecoder])이다. RFC 1952 는 ISO-8859-1 이라고 적지만 실제 도구는
 *    파일 시스템의 바이트를 그대로 적는다 — GNU gzip(리눅스·맥)은 UTF-8, 한국어 윈도우 도구는 CP949. 라틴-1 이름도 판정의
 *    라틴-1 갈래가 읽는다(`café`). **대가**: 짧은 라틴-1 이름은 한글 찌꺼기로 기울 수 있다(판정의 '못 하는 것').
 * 3. 제어 문자(C0·DEL·**C1**)가 하나라도 있으면 쓰지 않는다 — 라틴-1 로 읽힌 UTF-8 은 C1(U+0080~009F)을 만들고, 그것은
 *    `ArchivePath.sanitize` 가 거르지 않는다(C0·DEL 만 거른다).
 * 4. 그다음 풀기의 관문(`ArchivePath.sanitize`)을 지나야 한다 — `..`·`.`·끝의 점·공백.
 * 5. **압축 파일 자신과 같은 이름이면** 쓰지 않는다 — '여기에 풀기' 가 자기 자신을 덮어쓰는 것은 정책과 상관없이 막히므로
 *    (`ArchiveExtractEngine`) 그 이름으로는 풀리지 않는다. 견주는 것은 **풀기가 실제로 만들 이름**([asWritten])이고 대소문자와
 *    정규화(NFC·NFD)를 가리지 않는다 — FNAME `a?b.gz` 는 풀기의 관문에서 `a_b.gz` 가 되고, FAT·exFAT SD 카드에서 그것은
 *    `A_B.GZ` 와 한 파일이다. 그런 FNAME 은 버리고 압축 파일 이름에서 짓는다(`A_B`).
 * 6. [GzipHeaders.MAX_NAME_BYTES](4,096) 바이트를 넘으면 쓰지 않는다.
 *
 * ## 파일 이름에서 — 7-Zip 의 규칙과 같다(`DefaultName.cpp`)
 *
 * tar 를 줄여 적은 확장자는 `.tar` 로 바꾸고(`x.tgz` → `x.tar`), 그 밖에는 마지막 확장자를 뗀다(`log.txt.gz` → `log.txt`,
 * `x.Z` → `x`). 확장자가 없으면 `~` 를 붙인다(`README` → `README~`) — 같은 폴더에 풀었을 때 압축 파일 자신과 이름이 같지
 * 않게 하려는 것이다. 그래도 쓸 수 없는 이름이 나오면(끝이 점·공백) [FALLBACK] 이다.
 */
internal object StreamNames {

    data class Named(val name: String, val label: String)

    /** 다른 길이 모두 막혔을 때의 이름. 파일 이름이지 화면의 문장이 아니다. */
    const val FALLBACK = "data"

    /** tar 를 줄여 적은 확장자 → 푼 이름에 붙일 것. 소문자. */
    private val TAR_SHORTHANDS = listOf(
        ".tgz" to ".tar", ".taz" to ".tar", ".tbz2" to ".tar", ".tbz" to ".tar", ".tb2" to ".tar",
        ".txz" to ".tar", ".tlz" to ".tar", ".tlz4" to ".tar",
    )

    fun of(fname: ByteArray?, archiveName: String): Named {
        val leaf = lastSegment(archiveName)
        fname?.let { fromHeader(it, leaf) }?.let { return it }
        return Named(derived(leaf), "압축 파일 이름")
    }

    /** gzip FNAME 에서. 쓸 수 없으면 null(위 1~6). */
    fun fromHeader(raw: ByteArray, archiveName: String): Named? {
        if (raw.isEmpty() || raw.size > GzipHeaders.MAX_NAME_BYTES) return null
        val decoded = EntryNameDecoder.decode(raw, utf8Flag = false, fallback = String(raw, Charsets.ISO_8859_1))
        val leaf = lastSegment(decoded.name)
        if (leaf.isEmpty() || leaf.any { it.isISOControl() }) return null
        val safe = ArchivePath.sanitize(leaf) ?: return null
        if (safe.contains('/') || sameOnDisk(asWritten(safe), archiveName)) return null
        return Named(safe, "gzip 머리 · ${decoded.charsetLabel}")
    }

    /**
     * 같은 볼륨에서 한 파일을 가리킬 수 있는 두 이름인가 — 대소문자와 **유니코드 정규화**(NFC·NFD)를 가리지 않고 견준다. 한글은
     * 맥에서 온 이름이 자모로 풀린 NFD 인 일이 흔하고, 대소문자를 가리지 않는 리눅스 볼륨(ext4·F2FS 의 casefold)은 정규화한 뒤에
     * 견준다고 알려져 있다(기기에서 확인하지 못했다). 넓게 견주는 쪽의 대가는 쓸 수 있었던 FNAME 을 버리고 압축 파일 이름에서
     * 짓는 것뿐이다.
     */
    private fun sameOnDisk(a: String, b: String): Boolean =
        Normalizer.normalize(a, Normalizer.Form.NFC).equals(Normalizer.normalize(b, Normalizer.Form.NFC), ignoreCase = true)

    /** FAT·exFAT 가 받지 않는 글자. `/` 는 이미 조각을 나눴다. */
    private const val FAT_FORBIDDEN = "\\:*?\"<>|"

    /**
     * 풀기가 이 이름으로 **실제로 만들 파일 이름**(위 5). 풀기는 이름 조각마다 `PathRules.sanitize`(core:io — 이 모듈은 거기
     * 닿지 않는다)를 지나므로 그 글자 규칙을 여기 옮겼다: 앞뒤 공백을 떼고, 제어 문자와 FAT 금지문자를 `_` 로 바꾸고, 끝의 점·공백을
     * 뗀다. **255바이트 자르기는 옮기지 않았다** — 4,096바이트까지 받는 FNAME 이 잘려서 압축 파일 이름과 같아지는 자리는 풀기의
     * 마지막 관문(`SelfOverwriteGuard` — 파일의 정체로 견준다)이 막는다. 두 규칙을 한 벌로 묶지 못한 대가로, `PathRules` 의 글자
     * 규칙이 바뀌면 여기도 바꿔야 한다.
     */
    private fun asWritten(name: String): String = name.trim()
        .map { c -> if (c.code < 0x20 || c.code == 0x7F || c in FAT_FORBIDDEN) '_' else c }
        .joinToString("")
        .trimEnd('.', ' ')

    /** 압축 파일 이름에서(위 '파일 이름에서'). 언제나 쓸 수 있는 이름을 준다. */
    fun derived(archiveName: String): String {
        val leaf = lastSegment(archiveName)
        val lower = leaf.lowercase(Locale.ROOT)
        val base = TAR_SHORTHANDS.firstOrNull { (suffix, _) -> lower.endsWith(suffix) && leaf.length > suffix.length }
            ?.let { (suffix, tar) -> leaf.substring(0, leaf.length - suffix.length) + tar }
            ?: leaf.lastIndexOf('.').let { dot -> if (dot > 0) leaf.substring(0, dot) else "$leaf~" }
        return ArchivePath.sanitize(base)?.takeIf { !it.contains('/') && !it.any { c -> c.isISOControl() } } ?: FALLBACK
    }

    private fun lastSegment(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')
}

/**
 * gzip 첫 멤버의 머리만 읽는다 — 목록이 쓴다. 해제기를 만들지 않는다.
 *
 * **이름·주석 칸은 [GzipHeaders.MAX_NAME_BYTES] 까지만 읽는다** — 넘으면 이름 없이 멈춘다(그 길이의 이름은 어차피 쓰지 않는다).
 * 판별이 입력 상한에 걸려 이 리더로 온 파일(끝나지 않는 이름·주석)을 여기서 마감 없이 끝까지 다시 읽으면, 판별에 상한을 둔 뜻이
 * 사라진다 — 목록이 몇 GB 를 읽은 뒤 '깨졌다' 로 끝났다.
 */
internal fun GzipHeaders.firstOf(source: DocumentSource): GzipHeader =
    BufferedInputStream(source.openStream(), 8 * 1024).use { input ->
        if (input.read() != ID1 || input.read() != ID2) throw IOException("gzip 이 아니다")
        readAfterId({ input.read() }, fieldLimit = MAX_NAME_BYTES.toLong())
    }

/**
 * xz 의 풀린 크기 — 스트림마다 **끝의 인덱스**에 블록의 풀린 크기가 적혀 있다(파일을 풀지 않고 끝에서 거꾸로 읽는다).
 * 적힌 값이라 화면에만 쓴다. 못 읽으면(깨졌거나, 채널이 없거나, 아래 상한) -1 이고 목록은 그대로 선다 — 크기를 못 보일 뿐이다.
 *
 * org.tukaani.xz 의 `SeekableXZInputStream` 이 같은 일을 하지만 쓰지 않는다 — 스트림 사이의 채움을 **4바이트씩** 거꾸로
 * 읽어(1.12 소스) 0 으로 채운 수백 MB 를 붙인 파일 하나가 목록을 몇 분 멈추고, 그동안 인터럽트를 보지 않는다. 여기서는
 * 채움·인덱스·스트림 수에 상한을 둔다.
 *
 * 모양(xz 파일 형식 1.2.1): 스트림 = 머리 12(`FD 37 7A 58 5A 00`·플래그 2·CRC32) + 블록들 + 인덱스 + 꼬리 12(CRC32·뒤로 크기 4·
 * 플래그 2·`YZ`). 인덱스 = `00` · 레코드 수(VLI) · 레코드마다 (채우지 않은 크기, 풀린 크기)(VLI 둘) · 0 채움(4의 배수까지) ·
 * CRC32. 스트림 사이에는 0 채움(4의 배수)이 올 수 있다.
 */
internal object XzIndex {

    private const val MAX_STREAMS = 64
    private const val MAX_INDEX_BYTES = 1 shl 20
    private const val MAX_PADDING_BYTES = 1L shl 20
    private val HEADER_MAGIC = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)

    fun uncompressedSize(source: DocumentSource): Long {
        val channel = source.openChannel() ?: return -1L
        return channel.use { ch ->
            try {
                interruptible { read(ch) }
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                -1L
            }
        }
    }

    private fun read(ch: SeekableByteChannel): Long {
        var pos = ch.size()
        if (pos < 24 || pos % 4 != 0L) return -1L
        var total = 0L
        var streams = 0
        while (pos > 0) {
            pos = skipPadding(ch, pos)
            if (pos < 24) return -1L
            if (++streams > MAX_STREAMS) return -1L
            val footer = readAt(ch, pos - 12, 12)
            if (footer[10] != 'Y'.code.toByte() || footer[11] != 'Z'.code.toByte()) return -1L
            if (crc32(footer, 4, 6) != u32(footer, 0)) return -1L
            val indexSize = (u32(footer, 4) + 1) * 4
            if (indexSize > MAX_INDEX_BYTES || indexSize > pos - 24) return -1L
            val index = readAt(ch, pos - 12 - indexSize, indexSize.toInt())
            val (uncompressed, blocks) = parseIndex(index) ?: return -1L
            // **뺄셈으로 견준다.** 블록 자리의 합은 `Long` 의 끝 가까이 올 수 있어 `12 + blocks + indexSize + 12` 가 넘치면 음수가 되어
            // 검사를 지나고, 그 자리로 옮기는 `position(음수)` 가 `IllegalArgumentException` 을 냈다(입출력 예외가
            // 아니라 아래 `catch` 가 받지 못한다). `indexSize ≤ pos - 24` 라 오른쪽은 음수가 아니다.
            if (blocks > pos - 24 - indexSize) return -1L
            val streamSize = 12 + blocks + indexSize + 12
            val start = pos - streamSize
            val header = readAt(ch, start, 12)
            if (!header.copyOf(6).contentEquals(HEADER_MAGIC)) return -1L
            // 머리와 꼬리의 플래그가 같고 머리의 CRC32 가 맞아야 그 자리가 정말 스트림의 시작이다.
            if (header[6] != footer[8] || header[7] != footer[9]) return -1L
            if (crc32(header, 6, 2) != u32(header, 8)) return -1L
            total += uncompressed
            if (total < 0) return -1L
            pos = start
        }
        return total
    }

    /**
     * [end] 앞의 0 채움(4바이트 단위)을 거꾸로 지나간 자리. 채움이 [MAX_PADDING_BYTES] 를 넘거나 앞이 전부 0 이면 -1.
     * 4 KiB 씩 읽는다 — 4바이트씩 읽으면 1 MiB 채움에 읽기가 26만 번이다.
     */
    private fun skipPadding(ch: SeekableByteChannel, end: Long): Long {
        var pos = end
        var padding = 0L
        while (pos > 0) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("xz 인덱스를 읽는 중에 중단되었다")
            val n = minOf(PADDING_CHUNK.toLong(), pos).toInt()
            val buf = readAt(ch, pos - n, n)
            var i = n
            while (i >= 4 && buf[i - 1].toInt() == 0 && buf[i - 2].toInt() == 0 &&
                buf[i - 3].toInt() == 0 && buf[i - 4].toInt() == 0
            ) {
                i -= 4
            }
            padding += n - i
            pos -= n - i
            if (padding > MAX_PADDING_BYTES) return -1L
            if (i > 0) return pos
        }
        return -1L
    }

    private const val PADDING_CHUNK = 4096

    /** (풀린 크기의 합, 블록 자리의 합). 깨졌으면 null. */
    private fun parseIndex(b: ByteArray): Pair<Long, Long>? {
        if (b.size < 8 || b[0].toInt() != 0) return null
        if (crc32(b, 0, b.size - 4) != u32(b, b.size - 4)) return null
        var at = 1
        fun vli(): Long? {
            var v = 0L
            for (i in 0 until 9) {
                if (at >= b.size - 4) return null
                val x = b[at++].toInt() and 0xFF
                v = v or ((x and 0x7F).toLong() shl (7 * i))
                if (x and 0x80 == 0) return if (i > 0 && x == 0) null else v
            }
            return null
        }
        val count = vli() ?: return null
        if (count < 0 || count > b.size) return null
        var uncompressed = 0L
        var blocks = 0L
        for (i in 0 until count) {
            val unpadded = vli() ?: return null
            val size = vli() ?: return null
            // 2^62 를 넘는 크기는 깨진 인덱스로 본다(tar 의 크기와 같은 경계 — `TarLimits.MAX_ENTRY_BYTES`). 그 아래면 아래의 채움 셈
            // (`unpadded + 3`)이 넘치지 않는다.
            if (unpadded <= 0 || unpadded > TarLimits.MAX_ENTRY_BYTES || size > TarLimits.MAX_ENTRY_BYTES) return null
            blocks += (unpadded + 3) and 3L.inv()
            uncompressed += size
            if (blocks < 0 || uncompressed < 0) return null
        }
        while (at < b.size - 4) {
            if (b[at++].toInt() != 0) return null
        }
        return if (at % 4 == 0) uncompressed to blocks else null
    }

    private fun readAt(ch: SeekableByteChannel, at: Long, n: Int): ByteArray {
        val buf = ByteBuffer.allocate(n)
        ch.position(at)
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) throw IOException("xz 가 중간에 끝났다")
        }
        return buf.array()
    }

    private fun crc32(b: ByteArray, off: Int, len: Int): Long = CRC32().apply { update(b, off, len) }.value

    private fun u32(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((b[at + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
