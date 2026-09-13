package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * 7z 를 읽는다. LZMA·LZMA2 해제는 org.tukaani:xz 가 한다.
 *
 * ## solid 압축이라는 사실이 이 클래스의 모든 것을 정한다
 *
 * 7z 는 여러 파일을 한 덩어리로 묶어 압축한다. 그래서 뒤쪽 파일 하나를 꺼내려면 앞엣것을
 * 전부 풀어야 하고, 그 해제는 **우리 스트림 밖에서** 일어난다 — commons-compress 가
 * `read()` 안에서 `IOUtils.skip(stream, Long.MAX_VALUE)` 로 앞 엔트리를 통째로 흘려보낸다.
 * 그 바이트는 [EntryBudget] 에 한 번도 잡히지 않는다.
 *
 * 그래서 두 가지를 한다.
 * 1. **열기 전에 비용을 본다** — 건너뛸 엔트리들의 선언 크기 합이 총량 상한을 넘으면
 *    스트림을 돌려주지 않는다. 실제 해제량은 그 선언 크기로 묶이므로 이것이 상계다.
 * 2. **인터럽트를 검사한다** — `read()` 한 번이 오래 걸리는 동안 코루틴 취소가 닿을 길이
 *    그것뿐이다. 호출자는 `runInterruptible { }` 안에서 읽어야 한다.
 */
class SevenZArchiveReader(
    private val source: DocumentSource,
    private val budget: EntryBudget,
    private val limits: ParseLimits = ParseLimits.DEFAULT,
    override val formatId: FormatId = FormatId.SEVEN_Z,
) : ArchiveReader {

    private fun openFile(): SevenZFile {
        val builder = SevenZFile.builder()
            // 기본값이 Integer.MAX_VALUE 라 사실상 무제한이다. 헤더에 적힌 사전 크기
            // 하나로 힙을 넘길 수 있으므로 반드시 묶는다.
            //
            // 반드시 KiB 쪽을 쓴다. 이름이 비슷한 setMaxMemoryLimitKb 는 **이름과 달리
            // 바이트를 받아** 1024로 나눈다 — 65536을 넘기면 64MiB 가 아니라 64KiB 가
            // 되어 정상 7z 도 MemoryLimitException 으로 죽는다(실측으로 밟았다).
            .setMaxMemoryLimitKiB(MAX_HEADER_MEMORY_KIB)
        val file = source.asFile()
        if (file != null) builder.setFile(file)
        else builder.setSeekableByteChannel(
            source.openChannel() ?: error("7z 는 무작위 접근이 필요하다")
        )
        return builder.get()
    }

    private val handle: SevenZFile = openFile()

    override val entries: List<ArchiveEntry>

    init {
        try {
            val list = ArrayList<ArchiveEntry>(64)
            // **물리적 위치를 따로 센다.** [open] 과 [extractSequentially] 는 `nextEntry` 를 이 횟수만큼
            // 불러 엔트리를 찾으므로, 번호가 목록 안의 자리(list.size)이면 이름 없는 엔트리
            // 하나에 둘이 어긋나 **다른 파일의 바이트**가 열린다. 2단계가 ZIP 에서 같은
            // 형태의 결함(이름을 신원으로 씀)을 이미 한 번 밟았다.
            var position = -1
            for (e in handle.entries) {
                position++
                budget.beginEntry()
                // 7z 의 이름은 명세가 UTF-16LE 로 못 박아 두어 인코딩 판정이 필요 없다.
                val name = e.name ?: continue
                list += ArchiveEntry(
                    index = position,
                    name = name,
                    safeName = ArchiveEntry.sanitize(name),
                    declaredSize = if (e.hasStream()) e.size else 0L,
                    compressedSize = -1L,
                    isDirectory = e.isDirectory,
                    // isAntiItem 은 링크가 아니라 '삭제 표시' 다(증분 백업용). 내용이
                    // 없으므로 링크와 같은 취급으로 걸러 낸다.
                    isLink = e.isAntiItem ||
                        (e.hasWindowsAttributes && UnixMode.isSevenZSymlink(e.windowsAttributes)),
                    isEncrypted = false, // 7z 는 헤더 자체가 암호화되면 여는 단계에서 실패한다
                    crc = if (e.hasCrc) e.crcValue else -1L,
                    nameCharset = "UTF-16LE(명세)",
                    lastModified = if (e.hasLastModifiedDate) {
                        runCatching { e.lastModifiedDate.time }.getOrDefault(0L)
                    } else {
                        0L
                    },
                )
            }
            entries = list
        } catch (t: Throwable) {
            handle.close()
            throw t
        }
    }

    override val randomAccess: Boolean get() = false

    override fun open(entry: ArchiveEntry): InputStream {
        require(entry.isReadable) { "읽을 수 없는 항목이다" }

        // 건너뛰게 될 해제 비용을 먼저 본다. 실제 해제량은 선언 크기로 묶이므로
        // 이 합이 상계다. 넘으면 스트림을 아예 돌려주지 않는다.
        val skipCost = entries.take(entry.index).sumOf { it.declaredSize.coerceAtLeast(0L) }
        if (skipCost > limits.maxTotalOutput) {
            throw ParseLimitExceededException(
                "maxTotalOutput",
                "이 항목에 닿으려면 ${skipCost}바이트를 풀어야 한다 (상한 ${limits.maxTotalOutput})",
            )
        }

        // 새로 열어 순차로 찾아간다. 기존 핸들의 위치를 건드리지 않아야 여러 엔트리를
        // 동시에 읽어도 서로 망가뜨리지 않는다.
        val file = openFile()
        try {
            var index = -1
            while (true) {
                val e = file.nextEntry ?: break
                index++
                if (index == entry.index) {
                    require(!e.isDirectory) { "디렉터리다" }
                    // 압축비의 분모를 라이브러리 통계에서 가져온다. 7z 엔트리에는
                    // 압축 크기 필드가 없어(폴더 단위로 묶이므로) 이것 말고 방법이 없다.
                    return budget.guard(SevenZEntryStream(file)) {
                        runCatching { file.statisticsForCurrentEntry.compressedCount }.getOrDefault(0L)
                    }
                }
            }
            throw ParseLimitExceededException("index", "없는 엔트리")
        } catch (t: Throwable) {
            file.close()
            throw t
        }
    }

    /**
     * **파일을 한 번만 연다.** 이것이 이 클래스에서 [open] 과 가장 크게 다른 점이고,
     * 엔트리 수의 제곱이던 비용을 선형으로 만드는 유일한 장치다.
     *
     * 건너뛰는 엔트리도 **우리가 읽어서 버린다.** 읽지 않고 `nextEntry` 로 넘어가면
     * commons-compress 가 나중 엔트리의 첫 `read` 안에서 한꺼번에 흘려보내는데,
     * 그 바이트는 [EntryBudget] 에 한 번도 잡히지 않는다 — 압축폭탄이 계측 밖으로 샌다.
     * 드레인은 [budget] 이 감싼 스트림으로 하므로 상한이 그대로 걸린다.
     */
    override fun extractSequentially(sink: EntrySink) {
        val file = openFile()
        file.use {
            var position = -1
            val byIndex = entries.associateBy { it.index }
            val drain = ByteArray(COPY_BUFFER)
            while (true) {
                val raw = file.nextEntry ?: break
                position++
                if (raw.isDirectory) continue
                val entry = byIndex[position] ?: continue

                val guarded = budget.guard(SevenZEntryStream(file, owned = false)) {
                    runCatching { file.statisticsForCurrentEntry.compressedCount }.getOrDefault(0L)
                }
                val out = sink.begin(entry)
                if (out == null) {
                    // 고르지 않은 엔트리. solid 라 **어차피 풀린다** — 우리가 읽어 버려야
                    // 그 양이 예산에 잡힌다.
                    //
                    // **여기에도 try 가 필요하다.** 건너뛰는 엔트리가 폭탄이면
                    // `maxSingleOutput` 이 여기서 터지는데, 그것은 그 항목 하나의 상한이지
                    // 아카이브를 무효로 만드는 상한이 아니다. 막지 않으면 **고르지도 않은
                    // 엔트리 하나가 패스 전체를 죽인다** — 만화 뷰어는 창 밖의 쪽을 전부
                    // 이 길로 지나므로 그 확률이 낮지 않다.
                    try {
                        while (true) {
                            if (Thread.currentThread().isInterrupted) {
                                throw java.io.InterruptedIOException("푸는 중에 중단되었다")
                            }
                            if (guarded.read(drain) < 0) break
                        }
                    } catch (t: Throwable) {
                        if (t is java.io.InterruptedIOException) throw t
                        if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
                        // 건너뛰는 중의 실패는 보고할 곳이 없다 — sink 는 [EntrySink.begin]
                        // 이 스트림을 준 엔트리만 [EntrySink.finish] 로 받는다는 계약이다.
                    }
                    continue
                }

                var written = 0L
                var failure: Throwable? = null
                try {
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw java.io.InterruptedIOException("푸는 중에 중단되었다")
                        }
                        val n = guarded.read(drain)
                        if (n < 0) break
                        out.write(drain, 0, n)
                        written += n
                    }
                } catch (t: Throwable) {
                    if (t is java.io.InterruptedIOException) throw t
                    // 아카이브 전체를 무효로 만드는 상한은 항목 실패로 세지 않는다.
                    if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
                    failure = t
                }
                sink.finish(entry, written, failure)
            }
        }
    }

    override fun close() = handle.close()

    /**
     * 현재 엔트리를 읽는 스트림. 닫으면 뒤에 있는 [SevenZFile] 도 닫는다.
     *
     * 읽기 직전에 인터럽트를 본다. solid 아카이브에서 첫 `read` 한 번이 앞 엔트리
     * 전체를 푸느라 몇 초씩 걸릴 수 있고, 그동안 코루틴 취소가 닿을 길은 이것뿐이다.
     *
     * **`Thread.interrupted()` 를 쓰지 않는다.** 그것은 정적 함수이면서 **플래그를
     * 지운다** — 한 번 보고 나면 다음 검사가 아무것도 못 본다. 취소는 한 번만 오므로,
     * 지우는 순간 그 뒤의 모든 검사가 무력해지고 뒤늦게 도는 루프가 멈추지 않는다.
     *
     * [owned] 가 거짓이면 [close] 가 뒤의 파일을 닫지 않는다. 순차 추출은 파일 하나를
     * 엔트리 수천 개가 나눠 쓰기 때문에, 엔트리 하나를 닫았다고 파일이 닫히면 안 된다.
     */
    private class SevenZEntryStream(
        private val file: SevenZFile,
        private val owned: Boolean = true,
    ) : InputStream() {

        private fun checkInterrupted() {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("읽는 중에 중단되었다")
            }
        }

        override fun read(): Int {
            checkInterrupted()
            return file.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            checkInterrupted()
            return file.read(b, off, len)
        }

        override fun close() {
            if (owned) file.close()
        }
    }

    private companion object {
        /** 헤더 해제에 허용하는 메모리. 64MiB. */
        const val MAX_HEADER_MEMORY_KIB = 64 * 1024

        const val COPY_BUFFER = 64 * 1024
    }
}
