package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * tar 와 그것을 싼 압축 스트림을 읽는다 — `.tar`·`.tar.gz`/`.tgz`·`.tar.bz2`/`.tbz2`·`.tar.xz`/`.txz`(14단계)와
 * `.tar.lz4`·`.tar.lzma`/`.tlz`·`.tar.Z`/`.taz`, 만화 `.cbt`.
 *
 * 목록·항목 열기·순차 추출·진행률의 규칙은 cpio·ar 와 함께 쓰는 [ScannedArchive] 에 있고(두 모양의 비용, 막는 것,
 * 이름의 판정 — 그 클래스의 주석), 여기 남은 것은 tar 만의 사정 둘이다:
 *
 * 1. **머리를 읽는 것**([TarScanner]) — 메타 머리(긴 이름·PAX)·링크의 크기 칸·희소 파일.
 * 2. **첫 머리를 만들 때 본다** — 압축 스트림의 매직은 '압축됐다' 까지만 말한다. 안이 tar 가 아니면 [NotTarException]
 *    이다(`Archives.open` 은 그 경우를 항목 하나짜리 [SingleStreamReader] 로 연다).
 *
 * 압축의 종류는 [StreamCodec] 이 말한다(null 이면 압축하지 않았다). 해제기는 [CompressedStreams] 한 곳이 고른다 —
 * 단독 압축 스트림(`.gz` 로 싼 파일 하나)도 같은 해제기를 쓴다.
 */
class TarArchiveReader private constructor(core: ScannedArchive) : ArchiveReader by core {

    /**
     * @param codec 매직으로 이미 정한 것. null 이면 압축하지 않은 tar 다.
     */
    constructor(
        source: DocumentSource,
        budget: EntryBudget,
        limits: ParseLimits = ParseLimits.DEFAULT,
        codec: StreamCodec? = null,
    ) : this(build(source, budget, limits, codec, headChecked = false))

    /**
     * [Archives.open] 이 쓴다 — 판별이 이미 첫 머리를 풀어 보았으면([headChecked]) 다시 풀지 않는다. xz·LZMA 는 해제기를
     * 만들 때마다 사전(최대 64 MiB)을 새로 잡으므로 한 번이라도 아낀다.
     */
    internal constructor(
        source: DocumentSource,
        budget: EntryBudget,
        limits: ParseLimits,
        codec: StreamCodec?,
        headChecked: Boolean,
    ) : this(build(source, budget, limits, codec, headChecked))

    /** tar 가 아니다 — 압축 스트림은 맞는데 안이 tar 가 아니다(`.gz` 로 싼 로그 파일 하나 같은 것). */
    class NotTarException : IllegalStateException("tar 가 아니다")

    internal companion object {

        private fun build(
            source: DocumentSource,
            budget: EntryBudget,
            limits: ParseLimits,
            codec: StreamCodec?,
            headChecked: Boolean,
        ): ScannedArchive {
            // **첫 머리를 여기서 본다.** 매직은 압축 스트림까지만 말해 준다 — `.gz` 로 싼 로그 하나를 tar 로 여는
            // 척하면 목록이 '깨졌다' 로 끝나는데, 사실은 tar 가 아니다.
            if (!headChecked && !startsLikeTar(source, codec)) throw NotTarException()
            val kind = if (codec == null) ArchiveKind.TAR else ArchiveKind.tarOf(codec)
            return ScannedArchive(source, budget, limits, codec, kind) { src, cap, onDrain ->
                TarScanner(src, cap, onDrain)
            }.also { it.prepare() }
        }

        /**
         * 첫 머리가 tar 인가 — 검사합이 맞는 머리 한 칸이거나 **빈 tar** 다. 목록은 만들지 않는다(판별이 쓴다 —
         * 리더를 만들면 압축 안 한 tar 는 목록을 곧바로 세우고, 판별의 좁은 상한에서 큰 tar 가 '아니다' 로 떨어진다).
         *
         * ## 빈 tar — 0 으로 시작하는 것만으로는 모른다
         *
         * 끝 표시(0 으로 찬 칸)뿐인 tar 다. 둘을 함께 요구한다:
         *
         * 1. **끝까지 0** 이다([TarLimits.MAX_EMPTY_TAR_BYTES] 안에서). ISO 이미지는 앞 32 KiB 가 0 이고(`.iso` 도 목록에서
         *    '압축' 종류다), 그것을 빈 tar 로 보면 '다루지 않는 형식' 대신 '항목 0개' 가 뜬다.
         * 2. 압축 스트림이면 **안의 이름이 tar 다** — 항목 하나로 열었을 때의 이름([StreamNames.of]: gzip 의 FNAME, 없으면 압축
         *    파일 이름에서 지은 것)이 `.tar` 로 끝난다. 0 으로 된 파일을 싼 것(`gzip zero.img`)도 끝까지 0 이라 첫째 조건만으로는
         *    빈 tar 가 되어, 사용자가 그 파일을 풀 길이 없었다(본문이 1,000,000바이트까지 그랬다). tar 를 싼 쪽은
         *    이름이 이 모양이다 — `tar -czf x.tar.gz` 는 FNAME 을 적지 않고(파일 이름에서 `x.tar` 를 짓는다), `gzip x.tar` 는
         *    `x.tar` 를 적는다. 이름을 **먼저** 본다 — tar 이름이 아니면 1 MiB 를 풀어 보지 않는다(목록이 푸는 양,
         *    `SingleStreamReader` 의 주석).
         *
         * 압축 스트림이 깨져 앞 512바이트도 풀리지 않으면 `IOException` 이 그대로 나간다(판별은 그것을 '아니다' 로,
         * 여는 쪽은 '깨졌다' 로 읽는다).
         *
         * ## 판별의 상한 — 시각이 아니라 소비한 입력
         *
         * [probe] 면 파일에서 소비할 수 있는 압축 입력에 상한을 둔다 — 첫 512바이트까지 [StreamLimits.PROBE_INPUT_BYTES], 빈 tar 의
         * 나머지를 확인하는 데 [StreamLimits.PROBE_ZERO_TAIL_INPUT_BYTES] 더. 넘으면 [ProbeLimitException] 이다(부르는 쪽
         * `Archives` 가 항목 하나짜리로 연다). 같은 파일이면 언제 열어도 같은 답이다 — 예전의 5초는 기기가 느린 순간에만 다른 답을 냈다
         * (목록은 TAR.BZ2, 풀기는 항목 하나짜리). 압축하지 않은 tar 에는 걸지 않는다 — 파일에서 많아야 1 MiB 를 읽는다.
         */
        internal fun startsLikeTar(
            source: DocumentSource,
            codec: StreamCodec?,
            probe: Boolean = false,
            /** 첫 걸음의 입력 상한. 시험이 줄인다. */
            probeInputBytes: Long = StreamLimits.PROBE_INPUT_BYTES,
        ): Boolean {
            val limited = probe && codec != null
            val (stream, counted) = CompressedStreams.open(source, codec, probeInputBytes.takeIf { limited })
            return stream.use { input ->
                val head = ByteArray(TarFormat.RECORD)
                var got = 0
                while (got < head.size) {
                    val r = input.read(head, got, head.size - got)
                    if (r < 0) break
                    got += r
                }
                when {
                    got < head.size -> false
                    !TarFormat.isZero(head) -> TarFormat.checksumOk(head)
                    codec != null && !innerNameIsTar(source, input) -> false
                    else -> {
                        // 둘째 걸음 — 빈 tar 는 몇 KB 로 싸인다. 여기서 첫 걸음의 상한을 그대로 쓰면 작은 압축 스트림을 이은 파일이
                        // 스트림마다 해제기의 표를 새로 잡는 동안 판별을 붙든다(`StreamLimits.PROBE_ZERO_TAIL_INPUT_BYTES`).
                        if (limited) counted.limitProbe(StreamLimits.PROBE_ZERO_TAIL_INPUT_BYTES)
                        restIsZero(input)
                    }
                }
            }
        }

        /**
         * 항목 하나로 열었을 때의 이름이 `.tar` 로 끝나는가(위 '빈 tar' 의 2). gzip 은 해제기가 만들 때 읽은 첫 머리의 FNAME
         * ([GzipInputStream.firstHeader])을 쓴다 — 파일을 다시 열지 않는다.
         */
        private fun innerNameIsTar(source: DocumentSource, input: InputStream): Boolean {
            val fname = (input as? GzipInputStream)?.firstHeader?.nameBytes
            return StreamNames.of(fname, source.displayName).name.endsWith(".tar", ignoreCase = true)
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
