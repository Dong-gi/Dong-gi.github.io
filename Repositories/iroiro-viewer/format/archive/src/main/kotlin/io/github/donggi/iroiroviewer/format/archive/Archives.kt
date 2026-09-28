package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits

/**
 * 아카이브를 열고 판별하는 입구.
 *
 * 판별은 **매직 바이트가 먼저**다. 확장자는 사람이 바꾸고 틀리게 적는 값이라 신뢰
 * 근거가 되지 못한다. `.cbz` 로 적힌 RAR 파일은 실제로 흔하다.
 */
object Archives {

    private val ZIP = intArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_EMPTY = intArrayOf(0x50, 0x4B, 0x05, 0x06)
    private val ZIP_SPANNED = intArrayOf(0x50, 0x4B, 0x07, 0x08)
    private val SEVEN_Z = intArrayOf(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)
    private val RAR4 = intArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val RAR5 = intArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)

    /** gzip — `1F 8B` 에 압축 방식 8(deflate). 명세(RFC 1952)가 정한 방식은 그것 하나다. */
    private val GZIP = intArrayOf(0x1F, 0x8B, 0x08)

    /** bzip2 — `BZh` 뒤에 블록 크기 `1`~`9`. */
    private val BZIP2 = intArrayOf(0x42, 0x5A, 0x68)

    /** xz — `FD 37 7A 58 5A 00`. */
    private val XZ = intArrayOf(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)

    /**
     * 판별에 읽는 앞머리. tar 는 매직이 257번째 바이트에 있고(그마저 옛 V7 tar 에는 없다) 검사합이 머리 한 칸
     * 전체에 걸리므로 512바이트가 있어야 한다. 빈 tar(끝 표시 두 칸)를 알아보려면 1,024바이트다.
     */
    private const val HEAD_BYTES = 2 * 512

    /**
     * 아카이브 계열인가. 아니면 null. 여기서는 ZIP 안을 들여다보지 않는다.
     *
     * 분할 아카이브 표식(`50 4B 07 08`)은 **ZIP 으로 보지 않는다.** 여러 조각 중 하나일
     * 뿐이라 통짜로 열면 라이브러리 예외로 끝나고, 사용자에게는 '깨진 파일' 로 보인다.
     * 지원하지 않는다는 사실을 정확히 말하는 편이 낫다.
     */
    fun probeContainer(head: ByteArray): FormatId? = when {
        head.startsWith(ZIP_SPANNED) -> null
        head.startsWith(ZIP) || head.startsWith(ZIP_EMPTY) -> FormatId.ZIP
        head.startsWith(SEVEN_Z) -> FormatId.SEVEN_Z
        head.startsWith(RAR5) || head.startsWith(RAR4) -> FormatId.RAR
        else -> null
    }

    /**
     * 컨테이너의 모양. [probeContainer] 에 tar 계열을 더한 것이다.
     *
     * **압축 스트림(gzip·bzip2·xz)은 안이 tar 인지까지 보지 않는다** — 그러려면 풀어 봐야 한다. 그 확인은
     * [detect] 와 [open] 이 한다. 압축하지 않은 tar 는 머리 한 칸의 **검사합**으로 알아본다(매직이 없는 옛 V7
     * tar 도 검사합은 있다). [head] 가 512바이트보다 짧으면 tar 로 보지 않는다.
     */
    fun probeKind(head: ByteArray): ArchiveKind? {
        when (probeContainer(head)) {
            FormatId.ZIP -> return ArchiveKind.ZIP
            FormatId.SEVEN_Z -> return ArchiveKind.SEVEN_Z
            FormatId.RAR -> return ArchiveKind.RAR
            else -> Unit
        }
        if (head.startsWith(ZIP_SPANNED)) return null
        return when {
            head.startsWith(GZIP) -> ArchiveKind.TAR_GZ
            head.startsWith(BZIP2) && head.size > 3 && head[3] in '1'.code.toByte()..'9'.code.toByte() -> ArchiveKind.TAR_BZIP2
            head.startsWith(XZ) -> ArchiveKind.TAR_XZ
            head.size >= TarFormat.RECORD && TarFormat.looksLikeHeader(head.copyOf(TarFormat.RECORD)) -> ArchiveKind.TAR
            // 빈 tar — 끝 표시(0 으로 찬 칸)뿐이다. GNU tar 가 빈 목록으로 만들면 이 모양이다. **앞머리만으로는
            // 확정되지 않는다**(ISO 이미지도 앞이 0 이다) — 리더가 끝까지 0 인지 보고, 아니면 '다루지 않는 형식' 이다.
            head.size >= 2 * TarFormat.RECORD && TarFormat.isZero(head.copyOf(2 * TarFormat.RECORD)) -> ArchiveKind.TAR
            else -> null
        }
    }

    /**
     * 이 원본이 **우리가 여는** 아카이브인가. 압축 스트림은 풀어서 안의 첫 머리까지 본다 — `.gz` 로 싼 로그 하나는
     * null 이다. 목록을 만들지는 않는다(압축 tar 의 목록은 처음부터 끝까지 풀어야 한다).
     *
     * 확장자가 압축이라 열려고 하는 화면(만화 뷰어 등)이 '다루지 않는 형식' 을 정확히 말하는 데 쓴다.
     */
    fun detect(source: DocumentSource): ArchiveKind? {
        val kind = probeKind(source.head(HEAD_BYTES)) ?: return null
        if (!kind.isTar) return kind
        // 첫 머리만 본다. 리더를 만들면 압축 안 한 tar 는 목록을 곧바로 세우는데, 판별의 좁은 상한(`PROBE` 의 항목
        // 1,000개)에서는 항목이 많은 tar 가 '아카이브가 아니다' 로 떨어진다.
        return try {
            if (TarArchiveReader.startsLikeTar(source, compressionOf(kind))) kind else null
        } catch (e: java.io.InterruptedIOException) {
            throw e
        } catch (e: java.io.IOException) {
            null
        }
    }

    private fun compressionOf(kind: ArchiveKind): TarArchiveReader.Compression = when (kind) {
        ArchiveKind.TAR_GZ -> TarArchiveReader.Compression.GZIP
        ArchiveKind.TAR_BZIP2 -> TarArchiveReader.Compression.BZIP2
        ArchiveKind.TAR_XZ -> TarArchiveReader.Compression.XZ
        else -> TarArchiveReader.Compression.NONE
    }

    /** [ProbeContext] 를 받는 판별기. 레지스트리에 등록해 쓴다. */
    val probe = io.github.donggi.iroiroviewer.format.FormatProbe { ctx ->
        probeContainer(ctx.head)
    }

    /**
     * 컨테이너 종류에 맞는 리더를 연다.
     *
     * @param limits 이 아카이브에 걸 상한. 목록만 볼 때는 [ParseLimits.PROBE] 를 준다.
     */
    fun open(
        source: DocumentSource,
        limits: ParseLimits = ParseLimits.DEFAULT,
        // 예산을 밖에서 주면 그 예산이 든 상한이 이긴다. limits 와 budget 이 서로 다른
        // 상한을 들고 있으면 어느 쪽이 도는지 알 수 없으므로, 예산을 만들 때 쓴 상한을
        // 그대로 쓰게 강제한다.
        budget: EntryBudget = EntryBudget(limits),
        /**
         * 사용자가 넣은 암호. 리더가 **자기 사본**을 들고 닫을 때 지운다 — 넘긴 배열은 부르는
         * 쪽이 지운다. 암호가 없으면 암호 항목은 [ArchiveEntry.isReadable] 이 거짓이다.
         */
        password: CharArray? = null,
    ): ArchiveReader {
        val head = source.head(HEAD_BYTES)
        return when (val kind = probeKind(head)) {
            ArchiveKind.ZIP -> ZipArchiveReader(source, budget, password = password)
            ArchiveKind.SEVEN_Z -> SevenZArchiveReader(source, budget, limits, password = password)
            ArchiveKind.RAR -> RarArchiveReader(source, budget, password = password)
            // tar 에는 암호가 없다. 받은 암호는 쓰지 않는다(지우는 것은 넘긴 쪽의 일이다).
            ArchiveKind.TAR, ArchiveKind.TAR_GZ, ArchiveKind.TAR_BZIP2, ArchiveKind.TAR_XZ ->
                TarArchiveReader(source, budget, limits, compressionOf(kind))
            // 예외 메시지에 파일 이름을 넣지 않는다. 이 값은 로그와 화면을 타고 나간다.
            null -> error("아카이브가 아니다")
        }
    }

    /**
     * 리더가 받은 암호가 맞는가. 방식마다 다르게 확인한다([ArchiveReader.verifyPassword]).
     *
     * **한계를 적어 둔다.** 맞는 암호인데 그 항목이 깨져 있으면 '틀렸다' 로 나온다 — 7z 가
     * 틀린 암호를 '데이터가 깨졌다' 로만 알리기 때문에 둘을 가를 길이 없다.
     *
     * @throws ParseLimitExceededException 확인하려면 상한을 넘겨야 한다('틀렸다' 가 아니다).
     */
    fun verifyPassword(reader: ArchiveReader): Boolean = reader.verifyPassword()

    private fun ByteArray.startsWith(magic: IntArray): Boolean {
        if (size < magic.size) return false
        return magic.indices.all { this[it] == magic[it].toByte() }
    }
}
