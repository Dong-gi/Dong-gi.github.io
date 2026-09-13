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
    ): ArchiveReader {
        val head = source.head(16)
        return when (probeContainer(head)) {
            FormatId.ZIP -> ZipArchiveReader(source, budget)
            FormatId.SEVEN_Z -> SevenZArchiveReader(source, budget, limits)
            FormatId.RAR -> RarArchiveReader(source, budget)
            // 예외 메시지에 파일 이름을 넣지 않는다. 이 값은 로그와 화면을 타고 나간다.
            else -> error("아카이브가 아니다")
        }
    }

    private fun ByteArray.startsWith(magic: IntArray): Boolean {
        if (size < magic.size) return false
        return magic.indices.all { this[it] == magic[it].toByte() }
    }
}
