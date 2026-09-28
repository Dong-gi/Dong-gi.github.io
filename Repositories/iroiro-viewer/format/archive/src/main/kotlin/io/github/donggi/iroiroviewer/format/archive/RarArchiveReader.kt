package io.github.donggi.iroiroviewer.format.archive

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import com.github.junrar.exception.InitDeciphererFailedException
import com.github.junrar.exception.RarException
import com.github.junrar.exception.WrongPasswordException
import com.github.junrar.rarfile.FileHeader
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

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
    password: CharArray? = null,
) : ArchiveReader {

    /**
     * 우리 사본. junrar 에 `char[]` 로 건네고(`ArchiveOptions`) [close] 에서 지운다 — junrar 가 그
     * 배열을 붙들고 항목마다 열쇠를 유도하므로 리더가 사는 동안 살아 있어야 한다. (junrar 는 안에서
     * 열쇠를 유도할 때마다 `String` 을 만든다. 그것은 우리가 덮을 수 없다.)
     *
     * **암호 RAR 은 검증하지 못했다.** 표본을 만들 도구가 없다 — junrar 에는 쓰기 구현이 없고
     * 이 기계에 WinRAR 이 없다(RAR5 압축 표본이 미검증인 것과 같은 사정). 아래의 예외 옮기기와
     * [verifyPassword] 는 junrar 8.1.1 의 **소스를 읽고** 짰다 — 실제 표본을 탄 적이 없다.
     */
    private val pw: CharArray? = password?.copyOf()

    /**
     * junrar 의 예외를 우리 말로 옮긴다. **그대로 두면 앱이 죽는다** — `RarException` 은 `Exception`
     * 을 바로 잇는 검사 예외라 `IOException`·`RuntimeException` 을 잡는 호출부(만화 뷰어)를 모두
     * 지나 코루틴 밖으로 나간다(검토가 잡았다). 헤더까지 잠긴 RAR5(`rar -hp`)를 암호 없이 열면
     * `WrongPasswordException("Missing password…")` 이다.
     */
    private val archive: Archive = try {
        val options = ArchiveOptions.builder().apply { if (pw != null) password(pw) }.build()
        val file = source.asFile()
        val a = if (file != null) Archive(file, options) else Archive(source.openStream(), options)
        // RAR4 의 잠긴 헤더는 틀린 열쇠로도 '열린다' — 머리를 못 읽어 항목이 하나도 없다.
        if (headerLocked(a) && a.fileHeaders.isEmpty()) {
            a.close()
            throw ArchivePasswordException(wrongPassword = pw != null)
        }
        a
    } catch (e: WrongPasswordException) {
        pw?.fill('\u0000')
        throw ArchivePasswordException(wrongPassword = pw != null)
    } catch (e: InitDeciphererFailedException) {
        // RAR4 의 잠긴 헤더를 암호 없이 열면 열쇠를 못 만든다.
        pw?.fill('\u0000')
        if (pw == null) throw ArchivePasswordException(wrongPassword = false)
        throw IOException("RAR 을 열 수 없다: InitDeciphererFailedException")
    } catch (e: RarException) {
        pw?.fill('\u0000')
        // 메시지를 싣지 않는다 — 파일 이름이 들어 있다.
        throw IOException("RAR 을 열 수 없다: ${e.javaClass.simpleName}")
    } catch (t: Throwable) {
        pw?.fill('\u0000')
        throw t
    }

    private fun headerLocked(a: Archive): Boolean = try {
        a.isEncrypted
    } catch (e: RarException) {
        false
    }

    private val hasPassword = pw != null

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
                    decryptable = h.isEncrypted && hasPassword,
                    crc = if (h.hasFileCrc()) h.fileCRC.toLong() and 0xFFFF_FFFFL else -1L,
                    nameCharset = if (h.isUnicode) "UTF-16LE(헤더 표시)" else "CP437/로캘",
                    lastModified = runCatching { h.mTime?.time ?: 0L }.getOrDefault(0L),
                )
            }
            raw = rawList
            entries = list
        } catch (t: Throwable) {
            archive.close()
            pw?.fill('\u0000')
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
        val stream = archive.getInputStream(header)
        // **junrar 의 읽는 스트림은 실패를 삼킨다.** 해제가 다른 스레드에서 돌다 `RarException`
        // (틀린 암호·CRC)을 만나면 잡아 버리고 파이프만 닫는다 — 우리 쪽에는 멀쩡한 끝(EOF)으로
        // 보인다(소스로 확인). 적힌 크기보다 먼저 끝나면 실패로 돌린다.
        val exact = if (header.fullUnpackSize > 0) ExactLengthStream(stream, header.fullUnpackSize) else stream
        return budget.guard(exact, header.fullPackSize)
    }

    /**
     * 암호가 맞는가. **읽는 스트림으로 확인하지 않는다** — [open] 의 주석처럼 실패가 삼켜져 어떤
     * 암호든 '맞다' 가 된다(검토가 잡았다: 틀린 암호가 세션에 기억되고 자물쇠가 사라졌다).
     * `extractFile` 은 부르는 스레드에서 돌고 `RarException` 을 그대로 던진다. 맨 앞의 암호 항목을
     * [VERIFY_CAP] 까지 풀어 본다.
     */
    override fun verifyPassword(): Boolean {
        val target = entries.filter { it.isEncrypted && it.isReadable }.minByOrNull { it.index } ?: return true
        val header = raw.getOrNull(target.index) ?: return true
        return try {
            archive.extractFile(header, CappedSink(VERIFY_CAP))
            true
        } catch (t: Throwable) {
            val chain = generateSequence(t) { it.cause }.take(6).toList()
            chain.firstOrNull { it is InterruptedIOException }?.let { throw it }
            when {
                chain.any { it is CappedSink.Enough } -> true
                t is ParseLimitExceededException -> throw t
                t is RarException || t is IOException -> false
                else -> throw t
            }
        }
    }

    /** 적힌 크기보다 먼저 끝나면 던진다. */
    private class ExactLengthStream(input: InputStream, private val expected: Long) : java.io.FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val c = super.read()
            if (c < 0) end() else count++
            return c
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n < 0) end() else count += n
            return n
        }

        private fun end() {
            if (count < expected) throw IOException("RAR 항목이 중간에 끝났다 — 암호가 틀렸거나 파일이 손상됐다")
        }
    }

    /** 버리는 싱크. [cap] 바이트를 넘으면 그만 풀라고 던진다. */
    private class CappedSink(private val cap: Long) : java.io.OutputStream() {
        class Enough : IOException("확인에 충분하다")

        private var count = 0L

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("중단")
            count += len
            if (count >= cap) throw Enough()
        }
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
        // junrar 는 항목마다 소비한 입력을 주지 않는다. 끝난 항목의 **선언 압축 크기**를 더한다 — 분모도 같은 값의
        // 합이라([inputBytesFor]) 진행 바는 항목 단위로 뛰지만 끝에서 맞는다.
        var consumedBefore = 0L
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
            consumedBefore += header.fullPackSize.coerceAtLeast(0L)
            sink.consumed(consumedBefore)
        }
    }

    /** 고른 항목의 선언 압축 크기 합. 실측이 아니다 — 위 [extractSequentially] 의 주석. */
    override fun inputBytesFor(selected: Set<Int>?): Long =
        entries.filter { it.isReadable && (selected == null || it.index in selected) }
            .sumOf { raw.getOrNull(it.index)?.fullPackSize?.coerceAtLeast(0L) ?: 0L }

    override fun close() {
        try {
            archive.close()
        } finally {
            pw?.fill('\u0000')
        }
    }

    private companion object {
        /** 확인을 위해 풀 최대량. */
        const val VERIFY_CAP = 16L shl 20
    }

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
