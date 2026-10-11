package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.IOException
import java.io.InterruptedIOException

/**
 * 아카이브를 열고 판별하는 입구.
 *
 * 판별은 **매직 바이트가 먼저**다. 확장자는 사람이 바꾸고 틀리게 적는 값이라 신뢰
 * 근거가 되지 못한다. `.cbz` 로 적힌 RAR 파일은 실제로 흔하다. 확장자의 도움을 받는 것은 매직이 아예 없는
 * LZMA(`.lzma`·`.tlz`) 하나다([LzmaHeader] — 이름만으로는 열지 않는다).
 */
object Archives {

    private val ZIP = intArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_EMPTY = intArrayOf(0x50, 0x4B, 0x05, 0x06)
    private val ZIP_SPANNED = intArrayOf(0x50, 0x4B, 0x07, 0x08)
    private val SEVEN_Z = intArrayOf(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)
    private val RAR4 = intArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val RAR5 = intArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)

    /**
     * 판별에 읽는 앞머리. tar 는 매직이 257번째 바이트에 있고(그마저 옛 V7 tar 에는 없다) 검사합이 머리 한 칸
     * 전체에 걸리므로 512바이트가 있어야 한다. 빈 tar(끝 표시 두 칸)를 알아보려면 1,024바이트다. cpio 의 옛 이진 머리는
     * 이름 끝의 NUL 까지 본다(이 안에 들어오는 이름만).
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
     * 앞머리의 매직으로 본 컨테이너의 모양. [probeContainer] 에 tar·압축 스트림·cpio·ar 를 더한 것이다.
     *
     * - **압축 스트림은 안을 보지 않는다** — 매직의 답은 항목 하나짜리 모양(`GZ`·`BZ2`·`XZ`·`LZ4`·`Z`)이고, 안이 tar 인지
     *   ([ArchiveKind.TAR_GZ] 등)는 풀어 봐야 안다. 그 확인은 [detect] 와 [open] 이 한다.
     * - **LZMA 는 여기서 답하지 않는다** — 매직이 없다. 이름을 아는 [detect]·[open] 이 본다.
     * - 압축하지 않은 tar 는 머리 한 칸의 **검사합**으로 알아본다(매직이 없는 옛 V7 tar 도 검사합은 있다). **압축 매직보다
     *   먼저 본다** — tar 의 첫 머리는 첫 항목의 이름으로 시작하므로, `BZh9-사진/` 같은 폴더를 묶은 tar 가 bzip2 로
     *   보였다(14단계까지의 결함). 검사합 512바이트가 우연히 맞는 압축 파일은 없다시피 하다. [head] 가 512바이트보다 짧으면
     *   tar 로 보지 않는다.
     */
    fun probeKind(head: ByteArray): ArchiveKind? {
        when (probeContainer(head)) {
            FormatId.ZIP -> return ArchiveKind.ZIP
            FormatId.SEVEN_Z -> return ArchiveKind.SEVEN_Z
            FormatId.RAR -> return ArchiveKind.RAR
            else -> Unit
        }
        if (head.startsWith(ZIP_SPANNED)) return null
        if (head.size >= TarFormat.RECORD && TarFormat.looksLikeHeader(head.copyOf(TarFormat.RECORD))) return ArchiveKind.TAR
        CompressedStreams.codecOf(head)?.let { return ArchiveKind.streamOf(it) }
        if (ArFormat.looksLike(head)) return if (ArFormat.isDebian(head)) ArchiveKind.DEB else ArchiveKind.AR
        if (CpioFormat.looksLike(head)) return ArchiveKind.CPIO
        // 빈 tar — 끝 표시(0 으로 찬 칸)뿐이다. GNU tar 가 빈 목록으로 만들면 이 모양이다. **앞머리만으로는
        // 확정되지 않는다**(ISO 이미지도 앞이 0 이다) — 리더가 끝까지 0 인지 보고, 아니면 '다루지 않는 형식' 이다.
        if (head.size >= 2 * TarFormat.RECORD && TarFormat.isZero(head.copyOf(2 * TarFormat.RECORD))) return ArchiveKind.TAR
        return null
    }

    /**
     * 이 원본이 **우리가 여는** 아카이브인가, 무슨 모양인가. 압축 스트림은 풀어서 안의 첫 머리까지 본다 — tar 면
     * `TAR.GZ`, 아니면 항목 하나짜리 `GZ` 다. 목록을 만들지는 않는다(압축 tar 의 목록은 처음부터 끝까지 풀어야 한다).
     *
     * 확장자가 압축이라 열려고 하는 화면(만화 뷰어 등)이 '다루지 않는 형식' 을 정확히 말하는 데 쓴다. 앞 512바이트도
     * 풀리지 않는 압축 스트림(깨졌다)은 null 이다 — 여는 쪽([open])은 같은 파일을 '깨졌다' 로 말한다.
     */
    fun detect(source: DocumentSource): ArchiveKind? {
        val head = source.head(HEAD_BYTES)
        val kind = probeKind(head) ?: lzmaHinted(head, source.displayName) ?: return null
        // 첫 머리만 본다. 리더를 만들면 압축 안 한 tar 는 목록을 곧바로 세우는데, 판별의 좁은 상한(`PROBE` 의 항목
        // 1,000개)에서는 항목이 많은 tar 가 '아카이브가 아니다' 로 떨어진다.
        return try {
            refine(source, kind)
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: IOException) {
            null
        }
    }

    /**
     * 매직으로 정한 모양을 내용으로 다듬는다. 압축 스트림은 안이 tar 인지(앞 512바이트를 풀어 검사합을 본다 — [innerIsTar]),
     * 압축 안 한 tar 는 정말 tar 인지(빈 tar 면 끝까지 0 인지) 본다. 나머지는 매직이 곧 답이다. tar 가 아니면 null.
     *
     * @throws IOException 압축 스트림의 앞부분이 풀리지 않는다.
     */
    private fun refine(source: DocumentSource, kind: ArchiveKind): ArchiveKind? = when (kind.shape) {
        ArchiveKind.Shape.TAR -> if (TarArchiveReader.startsLikeTar(source, null)) kind else null
        ArchiveKind.Shape.STREAM -> {
            val codec = kind.codec!!
            if (innerIsTar(source, codec)) ArchiveKind.tarOf(codec) else kind
        }
        ArchiveKind.Shape.ZIP,
        ArchiveKind.Shape.SEVEN_Z,
        ArchiveKind.Shape.RAR,
        ArchiveKind.Shape.CPIO,
        ArchiveKind.Shape.AR,
        -> kind
    }

    /**
     * 압축 스트림 안이 tar 인가 — 판별([detect])과 여는 쪽([open])이 같은 것을 부른다.
     *
     * **소비할 입력에 상한이 있다**([StreamLimits.PROBE_INPUT_BYTES]). 앞 512바이트가 나오기까지 입력만 먹는 스트림 — xz 의 빈 블록
     * 수만 개, LZ4 의 건너뛰는 프레임·빈 프레임, 끝나지 않는 gzip 이름 — 은 그만큼 파일을 읽고, 목록 화면은 그동안 '읽는 중' 에 서
     * 있다. 넘으면 **tar 가 아니라고 본다**: 항목 하나짜리로 열리고, 그 목록은 머리와 끝의 인덱스만 읽는다
     * ([SingleStreamReader]). 안이 실은 tar 였다면 사용자는 그것을 풀어서 연다 — 아카이브 안의 아카이브와 같은 길이다. 깨진 것으로
     * 말하지 않는 것은 깨졌다는 근거가 없어서다.
     *
     * **답이 파일의 내용만으로 정해진다.** 예전에는 시각(5초)으로 끊어, 목록이 TAR.BZ2 로 세운 파일이 풀기 순간 SD 가 몇 초 멎으면
     * 항목 하나짜리로 열렸다 — '전부 풀기' 가 `backup.tar` 한 파일을 만들고 고른 항목은 '0개 완료', 만화의 패스는 쪽을 잃었다.
     * 목록·풀기·만화의 패스·표지가 저마다 다시 판별하므로, 판별이 같은 답을 내는 것이 그 모두를 맞추는 유일한 길이다.
     */
    private fun innerIsTar(source: DocumentSource, codec: StreamCodec): Boolean = try {
        TarArchiveReader.startsLikeTar(source, codec, probe = true)
    } catch (e: ProbeLimitException) {
        false
    }

    /** 매직이 없는 LZMA — 이름이 그렇다고 하고 머리가 그럴듯할 때만([LzmaHeader]). */
    private fun lzmaHinted(head: ByteArray, name: String): ArchiveKind? =
        if (LzmaHeader.hintedBy(name) && LzmaHeader.plausible(head)) ArchiveKind.LZMA else null

    /** [ProbeContext] 를 받는 판별기. 레지스트리에 등록해 쓴다. */
    val probe = io.github.donggi.iroiroviewer.format.FormatProbe { ctx ->
        probeContainer(ctx.head)
    }

    /**
     * 컨테이너 종류에 맞는 리더를 연다.
     *
     * 압축 스트림은 안을 풀어 보고 tar 면 [TarArchiveReader], 아니면 항목 하나짜리 [SingleStreamReader] 로 연다. 앞부분이
     * 풀리지 않으면 `IOException`(깨졌다)이다 — 다만 **LZMA 는 '아카이브가 아니다'** 다. 매직이 없으니 풀리지 않는 것을
     * 깨진 LZMA 라고 말할 근거가 없다(메모리 상한은 그대로 올린다 — 그것은 '너무 크다' 다). 안을 보는 데 입력 상한이 있다
     * ([innerIsTar] — 넘으면 항목 하나짜리다. [limits] 와 상관없이 같은 값이라 같은 파일은 언제나 같은 모양으로 열린다).
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
        // 예외 메시지에 파일 이름을 넣지 않는다. 이 값은 로그와 화면을 타고 나간다.
        val kind = probeKind(head) ?: lzmaHinted(head, source.displayName) ?: error("아카이브가 아니다")
        // tar·압축 스트림·cpio·ar 에는 암호가 없다. 받은 암호는 쓰지 않는다(지우는 것은 넘긴 쪽의 일이다).
        return when (kind.shape) {
            ArchiveKind.Shape.ZIP -> ZipArchiveReader(source, budget, password = password)
            ArchiveKind.Shape.SEVEN_Z -> SevenZArchiveReader(source, budget, limits, password = password)
            ArchiveKind.Shape.RAR -> RarArchiveReader(source, budget, password = password)
            ArchiveKind.Shape.TAR -> TarArchiveReader(source, budget, limits, null)
            ArchiveKind.Shape.STREAM -> openStream(source, budget, limits, kind.codec!!)
            ArchiveKind.Shape.CPIO -> CpioArchiveReader(source, budget, limits)
            ArchiveKind.Shape.AR -> ArArchiveReader(source, budget, limits, kind)
        }
    }

    private fun openStream(source: DocumentSource, budget: EntryBudget, limits: ParseLimits, codec: StreamCodec): ArchiveReader {
        val tar = try {
            innerIsTar(source, codec)
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: org.tukaani.xz.MemoryLimitException) {
            throw e
        } catch (e: IOException) {
            if (codec == StreamCodec.LZMA) error("아카이브가 아니다")
            throw e
        }
        return if (tar) {
            TarArchiveReader(source, budget, limits, codec, headChecked = true)
        } else {
            SingleStreamReader(source, budget, codec)
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
