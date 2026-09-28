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
    password: CharArray? = null,
) : ArchiveReader {

    /**
     * 우리 사본. [open] 이 파일을 새로 열 때마다 쓰므로 리더가 사는 동안 들고 있어야 한다.
     * [close] 에서 지운다. 7z 는 명세가 암호를 UTF-16LE 로 못 박아 두어 후보를 대 볼 일이 없다.
     */
    private val password: CharArray? = password?.copyOf()

    private fun openFile(withPassword: Boolean = true): SevenZFile {
        val builder = SevenZFile.builder()
            // 기본값이 Integer.MAX_VALUE 라 사실상 무제한이다. 헤더에 적힌 사전 크기
            // 하나로 힙을 넘길 수 있으므로 반드시 묶는다.
            //
            // 반드시 KiB 쪽을 쓴다. 이름이 비슷한 setMaxMemoryLimitKb 는 **이름과 달리
            // 바이트를 받아** 1024로 나눈다 — 65536을 넘기면 64MiB 가 아니라 64KiB 가
            // 되어 정상 7z 도 MemoryLimitException 으로 죽는다(실측으로 밟았다).
            .setMaxMemoryLimitKiB(MAX_HEADER_MEMORY_KIB)
        if (withPassword && password != null) builder.setPassword(password)
        val file = source.asFile()
        if (file != null) builder.setFile(file)
        else builder.setSeekableByteChannel(
            source.openChannel() ?: error("7z 는 무작위 접근이 필요하다")
        )
        return builder.get()
    }

    /**
     * 헤더까지 잠긴 7z 는 **여는 순간** 암호를 요구한다(`PasswordRequiredException`, 실측).
     * 틀린 암호로 열면 그냥 '헤더가 없다' 는 `IOException` 이 온다 — 그래서 그때는 암호 없이
     * 한 번 더 열어 보고, 암호를 요구하면 '틀렸다' 로 옮긴다. 깨진 파일과 가르는 길이 그것뿐이다.
     *
     * **어떤 실패든 사본을 지운다.** 예전 판은 두 예외만 잡아, 라이브러리가 던지는
     * `RuntimeException`(깨진 헤더)에서는 사본이 지워지지 않은 채 버려졌다(검토가 잡았다).
     */
    private val handle: SevenZFile = try {
        openFile()
    } catch (e: org.apache.commons.compress.PasswordRequiredException) {
        this.password?.fill('\u0000')
        throw ArchivePasswordException(wrongPassword = false)
    } catch (e: java.io.IOException) {
        val headerLocked = this.password != null && try {
            openFile(withPassword = false).close()
            false
        } catch (x: org.apache.commons.compress.PasswordRequiredException) {
            true
        } catch (x: Throwable) {
            false
        }
        this.password?.fill('\u0000')
        if (headerLocked) throw ArchivePasswordException(wrongPassword = true)
        throw e
    } catch (t: Throwable) {
        this.password?.fill('\u0000')
        throw t
    }

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
                    isEncrypted = false, // 아래에서 `nextEntry` 로 가려 고친다
                    crc = if (e.hasCrc) e.crcValue else -1L,
                    nameCharset = "UTF-16LE(명세)",
                    lastModified = if (e.hasLastModifiedDate) {
                        runCatching { e.lastModifiedDate.time }.getOrDefault(0L)
                    } else {
                        0L
                    },
                )
            }
            val locked = lockedPositions(handle.entries.count())
            entries = if (locked.isEmpty()) list else list.map {
                if (it.index in locked) it.copy(isEncrypted = true, decryptable = password != null) else it
            }
        } catch (t: Throwable) {
            handle.close()
            password?.fill('\u0000')
            throw t
        }
    }

    /**
     * 잠긴 항목의 자리.
     *
     * ## 암호 없이, 따로 연 파일로 가린다
     *
     * commons-compress 는 항목의 암호 여부를 목록에 싣지 않는다 — `contentMethods` 는 `nextEntry`
     * 가 그 폴더의 **해제기 사슬을 실제로 만들 때** 채워진다(함정 표). 그 사슬 만들기가 가볍지 않다:
     *
     * - LZMA(LZMA2 가 아니다)·BZip2 해제기는 **만들면서 바로 읽는다.** 암호를 준 파일이면 그 읽기가
     *   AES 열쇠 유도(SHA-256 2^19 회)를 부른다 — 폴더마다. 틀린 암호면 `CorruptedInputException`.
     * - BCJ2·PPMd·64 MiB 사전 같은 폴더는 **만드는 것 자체가 실패한다.**
     *
     * 예전 판은 암호를 준 핸들에서 그대로 돌고 예외를 잡지 않아, **암호와 상관없는 Ultra·BCJ2 7z 의
     * 목록까지 '깨졌다' 로 나갔다**(검토가 잡은 회귀). 이제 암호 없이 연 파일로 돌고 항목마다 예외를
     * 가른다 — 암호가 없으면 AES 는 첫 읽기에서 곧바로 `PasswordRequiredException` 을 던지므로 열쇠를
     * 유도하지 않는다.
     *
     * | `nextEntry` 가 | 뜻 |
     * |---|---|
     * | 돌아왔고 `contentMethods` 가 있다 | 새 폴더. AES 가 있으면 잠겼다 |
     * | 돌아왔고 `contentMethods` 가 없다 | 앞 항목과 같은 폴더 — 앞의 판정을 잇는다 |
     * | `PasswordRequiredException` | 잠긴 새 폴더 |
     * | 그 밖의 `IOException` | 우리가 못 푸는 새 폴더 — 잠겼는지 모른다(잠기지 않은 것으로 둔다) |
     * | `RuntimeException` | 앞에서 실패한 폴더의 다음 항목(라이브러리가 빈 스트림을 감싸다 넘어진다) — 앞의 판정을 잇는다 |
     *
     * 헤더까지 잠긴 파일은 암호 없이 열리지 않는다 — 그때는 내용을 가진 항목이 **전부** 잠긴 것이다.
     */
    private fun lockedPositions(count: Int): Set<Int> {
        val probe = try {
            openFile(withPassword = false)
        } catch (e: org.apache.commons.compress.PasswordRequiredException) {
            // 헤더까지 잠겼다. 여기까지 왔으면 암호로 열린 것이다.
            return handle.entries.withIndex().filter { it.value.hasStream() }.map { it.index }.toSet()
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: java.io.IOException) {
            // 가리지 못한다. 목록까지 막지 않는다 — 잠긴 항목은 읽을 때 암호를 요구하며 실패한다.
            return emptySet()
        }
        val locked = HashSet<Int>()
        probe.use { f ->
            val all = f.entries.toList()
            var lastLocked = false
            for (at in 0 until minOf(count, all.size)) {
                val hasStream = all[at].hasStream()
                val isLocked = try {
                    val e = f.nextEntry ?: break
                    val methods = e.contentMethods
                    when {
                        !hasStream -> false
                        methods == null -> lastLocked
                        else -> methods.any { it.method == org.apache.commons.compress.archivers.sevenz.SevenZMethod.AES256SHA256 }
                    }
                } catch (e: org.apache.commons.compress.PasswordRequiredException) {
                    true
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: java.io.IOException) {
                    false
                } catch (e: RuntimeException) {
                    lastLocked
                }
                if (hasStream) lastLocked = isLocked
                if (isLocked && hasStream) locked.add(at)
            }
        }
        return locked
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
            // 끝난 항목들이 소비한 압축 입력. 라이브러리의 통계는 **항목마다 0 에서 다시** 센다(`getNextEntry`
            // 가 되돌린다) — 그래서 끝날 때마다 더한다. 건너뛰며 푸는 항목도 우리가 읽어 버리므로 여기 잡힌다.
            var consumedBefore = 0L
            fun spentNow(): Long = runCatching { file.statisticsForCurrentEntry.compressedCount }.getOrDefault(0L)
            while (true) {
                val raw = file.nextEntry ?: break
                position++
                val entry = byIndex[position] ?: continue
                if (raw.isDirectory) {
                    // **폴더 항목도 소비자에게 보인다** — ZIP·RAR·tar 와 같은 계약이다. 예전에는 여기서 건너뛰어,
                    // 7z 를 풀면 빈 폴더가 생기지 않았고 폴더의 수정시각도 되살릴 길이 없었다. 폴더에는 자료가 없으므로
                    // 받을 곳을 주더라도 tar 가 읽을 수 없는 항목에 하는 것처럼 그 항목만 실패로 돌린다.
                    if (sink.begin(entry) != null) {
                        sink.finish(entry, 0L, IllegalArgumentException("읽을 수 없는 항목이다"))
                    }
                    continue
                }

                val guarded = budget.guard(SevenZEntryStream(file, owned = false)) { spentNow() }
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
                            sink.consumed(consumedBefore + spentNow())
                        }
                    } catch (t: Throwable) {
                        if (t is java.io.InterruptedIOException) throw t
                        if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
                        // 건너뛰는 중의 실패는 보고할 곳이 없다 — sink 는 [EntrySink.begin]
                        // 이 스트림을 준 엔트리만 [EntrySink.finish] 로 받는다는 계약이다.
                    }
                    consumedBefore += spentNow()
                    sink.consumed(consumedBefore)
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
                        sink.consumed(consumedBefore + spentNow())
                    }
                } catch (t: Throwable) {
                    if (t is java.io.InterruptedIOException) throw t
                    // 아카이브 전체를 무효로 만드는 상한은 항목 실패로 세지 않는다.
                    if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
                    failure = t
                }
                sink.finish(entry, written, failure)
                consumedBefore += spentNow()
                sink.consumed(consumedBefore)
            }
        }
    }

    /**
     * 파일 크기. 순차 추출은 고른 것과 상관없이 **끝까지 푼다**(solid 라 건너뛰는 것도 풀어서 버린다) — 소비한
     * 압축 입력의 합은 팩 스트림의 합이고, 그것은 파일 크기에서 머리만큼 모자란다. 진행 바는 99% 언저리에서 끝난다.
     */
    override fun inputBytesFor(selected: Set<Int>?): Long = source.length.takeIf { it > 0 } ?: -1L

    override fun close() {
        // 계약상 [open] 이 준 스트림은 리더보다 먼저 닫힌다 — 그 뒤라 지워도 안전하다.
        password?.fill('\u0000')
        handle.close()
    }

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
