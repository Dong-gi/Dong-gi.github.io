package io.github.donggi.iroiroviewer.format.archive

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.InputStream

/**
 * RAR 4·5 를 읽는다.
 *
 * **junrar 의 추출 헬퍼를 쓰지 않는다.** `Junrar.extract()` 와 `LocalFolderExtractor`
 * 는 스스로 경로를 만들어 파일을 쓰는데, 2026년에 나온 경로 탈출 CVE 두 건이 정확히
 * 거기 있었다. 우리가 스트림을 직접 쥐면 그 클래스의 경로 처리를 아예 지나지 않으므로
 * 이 계열 취약점에 구조적으로 노출되지 않는다. import 자체를 하지 마라.
 *
 * 라이선스: junrar 는 UnRAR 라이선스를 승계한다(RAR 압축 알고리즘 재구현 금지, 고지
 * 의무). 앱의 오픈소스 고지 화면에 전문을 싣는다.
 */
class RarArchiveReader(
    source: DocumentSource,
    private val budget: EntryBudget,
    override val formatId: FormatId = FormatId.RAR,
) : ArchiveReader {

    private val archive: Archive = run {
        val file = source.asFile()
        if (file != null) Archive(file) else Archive(source.openStream())
    }

    private val raw: List<FileHeader>

    override val entries: List<ArchiveEntry>

    init {
        try {
            val headers = archive.fileHeaders
            budget.checkEntryCount(headers.size)
            val list = ArrayList<ArchiveEntry>(headers.size)
            val rawList = ArrayList<FileHeader>(headers.size)
            for (h in headers) {
                budget.beginEntry()
                val name = h.fileName ?: continue
                rawList += h
                list += ArchiveEntry(
                    index = rawList.size - 1,
                    name = name,
                    safeName = ArchiveEntry.sanitize(name),
                    declaredSize = h.fullUnpackSize,
                    compressedSize = h.fullPackSize,
                    isDirectory = h.isDirectory,
                    isLink = isLink(h),
                    isEncrypted = h.isEncrypted,
                    crc = if (h.hasFileCrc()) h.fileCRC.toLong() and 0xFFFF_FFFFL else -1L,
                    nameCharset = if (h.isUnicode) "UTF-16LE(헤더 표시)" else "CP437/로캘",
                    lastModified = runCatching { h.mTime?.time ?: 0L }.getOrDefault(0L),
                )
            }
            raw = rawList
            entries = list
        } catch (t: Throwable) {
            archive.close()
            throw t
        }
    }

    /**
     * 링크 판정.
     *
     * RAR5 는 헤더에 리디렉션 레코드를 싣는다 — 심볼릭 링크·하드링크·정션·파일 복사.
     * junrar 8.1.1 이 그것을 `FileHeader.getRedirection()` 으로 준다. 종류를 가리지 않고
     * **리디렉션이 있으면 실체가 아니다**(내용이 파일이 아니라 대상 경로 문자열이다).
     *
     * RAR4 에는 그 레코드가 없어 유닉스 속성 비트로 본다.
     */
    private fun isLink(h: FileHeader): Boolean {
        if (runCatching { h.redirection != null }.getOrDefault(false)) return true
        val host = h.hostOS
        val unixLike = host == com.github.junrar.rarfile.HostSystem.unix ||
            host == com.github.junrar.rarfile.HostSystem.macos ||
            host == com.github.junrar.rarfile.HostSystem.beos
        return unixLike && UnixMode.isSymlinkFromUnixAttributes(h.fileAttr)
    }

    /** RAR 도 solid 압축이 가능하고, 그 경우 무작위 접근이 매우 느리다. */
    override val randomAccess: Boolean get() = false

    override fun open(entry: ArchiveEntry): InputStream {
        require(entry.isReadable) { "읽을 수 없는 항목이다" }
        val header = raw.getOrNull(entry.index)
            ?: throw ParseLimitExceededException("index", "없는 엔트리")
        return budget.guard(archive.getInputStream(header), header.fullPackSize)
    }

    /**
     * **`extractFile` 로 우리 스레드에서 푼다.**
     *
     * [open] 이 쓰는 `Archive.getInputStream` 은 `PipedInputStream` 과 **새 스레드**를
     * 만든다(바이트코드로 확인했다). 그러면 우리가 인터럽트를 받아도 해제는 그 스레드에서
     * 계속 돌고, 취소가 사실상 닿지 않는다. `extractFile(header, out)` 은 부르는
     * 스레드에서 돌기 때문에, 우리가 쥔 [java.io.OutputStream] 의 `write` 에서
     * 인터럽트를 보면 그 자리에서 멈춘다.
     *
     * ## solid RAR 에서 건너뛴 엔트리는 우리가 드레인하지 않는다
     *
     * junrar 의 `extractFile` 이 스스로 한다 — `lastProcessedFileIndex` 를 들고 있다가
     * 목표보다 앞에 있는 엔트리들을 `skipFile`(내용을 널 스트림으로 해제)로 지나간다.
     * 그래서 **인덱스 오름차순으로만 부르면** 사전 연속성이 유지되고 비용도 전체 한 번이다.
     * 되감으면(`index < lastProcessedFileIndex`) 사전을 초기화하고 처음부터 다시 간다 —
     * 그래서 이 구현은 순서를 절대 뒤집지 않는다.
     */
    override fun extractSequentially(sink: EntrySink) {
        for (entry in entries) {
            val header = raw.getOrNull(entry.index) ?: continue
            val out = sink.begin(entry) ?: continue
            val counting = CountingInterruptibleStream(out)
            var failure: Throwable? = null
            try {
                // 압축 입력 크기를 상수로 준다. junrar 는 엔트리별 소비 바이트를 주지 않는다.
                budget.guard(counting, header.fullPackSize.takeIf { it > 0 }?.let { { it } }).use { guarded ->
                    archive.extractFile(header, guarded)
                }
            } catch (t: Throwable) {
                if (t is java.io.InterruptedIOException) throw t
                // 아카이브 전체를 무효로 만드는 상한은 항목 실패로 세지 않는다.
                if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
                // junrar 는 우리 스트림의 예외를 자기 예외로 감싸 던진다. 인터럽트가
                // 그렇게 묻히면 취소가 '실패' 로 보고되므로 원인을 벗겨 본다.
                var cause: Throwable? = t.cause
                var hop = 0
                while (cause != null && hop < 4) {
                    if (cause is java.io.InterruptedIOException) throw cause
                    cause = cause.cause
                    hop++
                }
                failure = t
            }
            sink.finish(entry, counting.written, failure)
        }
    }

    override fun close() = archive.close()

    /** 쓴 바이트를 세고, 쓸 때마다 인터럽트를 본다. 취소가 해제 루프 안으로 닿는 지점이다. */
    private class CountingInterruptibleStream(private val target: java.io.OutputStream) : java.io.OutputStream() {
        var written: Long = 0L
            private set

        override fun write(b: Int) {
            check()
            target.write(b)
            written++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            check()
            target.write(b, off, len)
            written += len
        }

        override fun flush() = target.flush()

        /** 감싼 스트림은 닫지 않는다 — 소비자가 준 것이고 소비자가 닫는다. */
        override fun close() = Unit

        private fun check() {
            if (Thread.currentThread().isInterrupted) {
                throw java.io.InterruptedIOException("푸는 중에 중단되었다")
            }
        }
    }
}
