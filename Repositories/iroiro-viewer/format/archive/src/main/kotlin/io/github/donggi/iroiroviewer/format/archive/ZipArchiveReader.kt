package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.InputStreamStatistics
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * ZIP 계열(zip·cbz·docx·xlsx·pptx·epub·hwpx)을 읽는다.
 *
 * `java.util.zip.ZipFile` 을 쓰지 않는 이유가 셋이다. 원본 파일명 바이트를 꺼낼 수 없고
 * (그러면 CP949 판정을 할 수 없다), CP949 아카이브에서 예외를 던지며, ZIP64 를 다루지
 * 못한다. commons-compress 는 셋 다 해결한다.
 *
 * **ISO-8859-1 로 여는 것이 핵심이다.** 이 문자셋은 바이트를 1:1 로 문자에 대응시키므로
 * 어떤 바이트열에도 예외를 내지 않는다. 이름은 그 뒤에 [EntryNameDecoder] 가 정한다.
 */
class ZipArchiveReader(
    source: DocumentSource,
    private val budget: EntryBudget,
    override val formatId: FormatId = FormatId.ZIP,
) : ArchiveReader {

    private val zip: ZipFile = run {
        // **여는 것보다 먼저 개수를 본다.** commons-compress 는 생성자에서 중앙 디렉터리를
        // 전부 객체로 만들기 때문에, 열고 나서 세면 이미 힙에 올라온 뒤다.
        checkDeclaredEntryCount(source)
        val builder = ZipFile.builder().setCharset(StandardCharsets.ISO_8859_1)
        val file = source.asFile()
        if (file != null) builder.setFile(file)
        else builder.setSeekableByteChannel(
            source.openChannel() ?: error("ZIP 은 무작위 접근이 필요하다")
        )
        builder.get()
    }

    /** 인덱스로 찾는다. 이름은 신원이 아니다 — [ArchiveEntry] 주석 참고. */
    private val raw: List<ZipArchiveEntry>

    override val entries: List<ArchiveEntry>

    init {
        // 초기화가 실패하면 이미 연 핸들을 여기서 닫는다. 생성자가 던진 객체는
        // 호출자가 close() 할 방법이 없어 그대로 두면 파일 서술자가 샌다.
        try {
            val rawList = ArrayList<ZipArchiveEntry>(64)
            val list = ArrayList<ArchiveEntry>(64)
            val it = zip.entries
            while (it.hasMoreElements()) {
                val e = it.nextElement()
                budget.beginEntry()
                // 유니코드 경로 추가필드(0x7075)가 있으면 라이브러리가 이미 정답을
                // 복원해 두었다. 그것을 버리고 원본 바이트로 다시 추측하면, 제대로
                // 만들어진 일본어·중국어 아카이브의 이름을 우리가 망가뜨리게 된다.
                val decoded = if (e.nameSource == ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD) {
                    EntryNameDecoder.Decoded(e.name, "유니코드 경로 추가필드")
                } else {
                    EntryNameDecoder.decode(
                        raw = e.rawName,
                        utf8Flag = e.generalPurposeBit.usesUTF8ForNames(),
                        fallback = e.name,
                    )
                }
                rawList += e
                list += ArchiveEntry(
                    index = rawList.size - 1,
                    name = decoded.name,
                    safeName = ArchiveEntry.sanitize(decoded.name),
                    declaredSize = e.size,
                    compressedSize = e.compressedSize,
                    isDirectory = e.isDirectory,
                    isLink = runCatching { e.isUnixSymlink }.getOrDefault(false),
                    isEncrypted = e.generalPurposeBit.usesEncryption(),
                    crc = e.crc,
                    nameCharset = decoded.charsetLabel,
                    // `getTime` 은 값이 없으면 -1 을 준다. 0 이 우리의 '모름' 이다.
                    lastModified = e.time.coerceAtLeast(0L),
                )
            }
            raw = rawList
            entries = list
        } catch (t: Throwable) {
            zip.close()
            throw t
        }
    }

    /**
     * ZIP 이 **스스로 적어 둔** 엔트리 수를 보고 거른다.
     *
     * ## 여기 있던 상계 검사를 버린 이유
     *
     * 처음에는 `파일크기 / 46`(중앙 디렉터리 레코드의 최소 길이)을 상계로 구해 개수
     * 상한과 견줬다. 그런데 상계는 "이만큼까지 있을 수 있다" 는 뜻이라, 파일이
     * 460,000바이트만 넘으면 **엔트리가 3개인 zip 도** 거절됐다. 실측이다 —
     * 419,911바이트는 열리고 461,869바이트는 `엔트리 10040개` 로 거절됐다.
     * 2단계부터 이 결함을 안고 있었고, 표본이 전부 작아서 자가시험도 통과했다.
     *
     * **상계로는 '너무 많다' 를 판정할 수 없다.** 그래서 추측을 버리고 EOCD 에 적힌
     * 수를 읽는다([ZipEntryCount]). 못 읽으면 검사하지 않는다 — EOCD 가 없는 파일은
     * commons-compress 도 열지 못하므로 그쪽에서 실패한다.
     */
    private fun checkDeclaredEntryCount(source: DocumentSource) {
        val declared = ZipEntryCount.read(source) ?: return
        val clamped = if (declared > Int.MAX_VALUE) Int.MAX_VALUE else declared.toInt()
        budget.checkEntryCount(clamped)
    }

    override val randomAccess: Boolean get() = true

    override fun open(entry: ArchiveEntry): InputStream {
        require(entry.isReadable) { "읽을 수 없는 항목이다" }
        val e = raw.getOrNull(entry.index) ?: throw ParseLimitExceededException("index", "없는 엔트리")
        val stream = zip.getInputStream(e)
        // 압축비의 분모로 '실제로 소비한 입력 바이트' 를 쓴다. 헤더의 선언값은
        // 공격자가 적는 값이라 분모로 약하다.
        val stats = stream as? InputStreamStatistics
        return if (stats != null) budget.guard(stream) { stats.compressedCount }
        else budget.guard(stream, e.compressedSize)
    }

    /**
     * ZIP 은 엔트리마다 독립 압축이라 **순차와 무작위의 비용이 같다.** 그래서 계약을
     * 지키는 가장 단순한 구현이 곧 가장 빠른 구현이다(실측 배율 1.25~1.42 — 선형 이하).
     */
    override fun extractSequentially(sink: EntrySink) {
        for (entry in entries) {
            val out = sink.begin(entry) ?: continue
            var written = 0L
            var failure: Throwable? = null
            try {
                open(entry).use { input ->
                    val buf = ByteArray(COPY_BUFFER)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw java.io.InterruptedIOException("푸는 중에 중단되었다")
                        }
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                    }
                }
            } catch (t: Throwable) {
                // **취소는 위로 올린다.** 엔트리 실패로 세면 사용자가 취소를 눌렀는데도
                // 나머지 9,999개를 계속 푼다.
                if (t is java.io.InterruptedIOException) throw t
                // 아카이브 전체를 무효로 만드는 상한은 항목 실패로 세지 않는다.
                if (t is ParseLimitExceededException && t.isFatalForArchive()) throw t
                failure = t
            }
            sink.finish(entry, written, failure)
        }
    }

    override fun close() = zip.close()

    private companion object {
        const val COPY_BUFFER = 64 * 1024
    }
}
