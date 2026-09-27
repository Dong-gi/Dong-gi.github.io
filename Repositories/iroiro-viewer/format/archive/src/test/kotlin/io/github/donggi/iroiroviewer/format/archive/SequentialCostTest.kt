package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * **엔트리 하나씩 여는 것이 얼마나 비싼가.**
 *
 * 8단계의 풀기는 아카이브 전체를 소비한다. [ArchiveReader.open] 을 엔트리마다 부르면
 * 7z 에서는 그때마다 파일을 새로 열고 `nextEntry` 를 index 번 부른다 —
 * [SevenZArchiveReader.open] 의 주석과 구현이 그렇게 되어 있다. 그러면 전체 풀기가
 * 엔트리 수의 **제곱**에 비례한다.
 *
 * 그 짐작이 맞는지 여기서 잰다. 수는 기계마다 다르므로 **비율만** 단언한다 —
 * 절대 시간을 단언하면 느린 CI 에서 이유 없이 빨개진다.
 */
class SequentialCostTest {

    private val dir: File = Files.createTempDirectory("iroiro-seq").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    /**
     * 두 크기를 **번갈아** 재고 각각의 **가장 빠른 값**을 쓴다.
     *
     * ## 최소값을 쓰는 이유
     *
     * 시간 시험이 한 번의 측정에 기대면 기계가 바쁠 때 무너진다. 잡음은 언제나 시간을
     * **늘리는 쪽**으로만 작용하므로, 최소값이 '방해받지 않았다면 얼마였을까' 에 가장
     * 가까운 추정이다. 상한을 느슨하게 푸는 것보다 이쪽이 낫다 — 느슨하게 풀면 시험이
     * 재려던 차이(제곱 대 선형)까지 함께 흐려진다.
     *
     * ## 번갈아 재는 이유
     *
     * 최소값만으로는 모자랐다. `./gradlew test` 가 모듈별 테스트 워커를 **병렬로** 돌리는
     * 동안, 작은 쪽(60개, 0.8초)의 세 번이 전부 붐비는 구간에 떨어지고 큰 쪽(120개, 3초)은
     * 그 구간을 평균으로 흡수해 **배율이 3.8 에서 2.02 로 주저앉았다.** 짧은 측정일수록
     * 같은 길이의 방해에 더 크게 부푼다.
     *
     * 번갈아 재면 붐비는 구간이 **두 측정에 같은 확률로** 걸리므로 비율이 한쪽으로 쏠리지
     * 않는다. 부하가 있으면 두 값이 함께 커질 뿐 배율은 남는다 — 이 시험이 단언하는 것이
     * 절대 시간이 아니라 배율이라는 사실이 여기서 값을 한다.
     *
     * ## 세 번이 다섯 번이 된 이유
     *
     * 10단계가 JVM 시험을 268 → 309건으로 늘리자 `./gradlew test` 의 병렬 워커가 그만큼
     * 오래 겹쳤고, 세 번으로는 **양쪽 시험이 번갈아 깨졌다**(같은 날 7z 배율과 ZIP 배율이
     * 한 번씩). 상한을 느슨하게 푸는 길은 쓰지 않는다 — 그러면 재려던 차이(제곱 대 선형)가
     * 함께 흐려진다. **표본을 늘리는 쪽이 옳다**: 잡음은 시간을 늘리는 쪽으로만 작용하므로
     * 시도가 늘수록 최소값은 '방해받지 않았다면' 에 가까워지기만 한다.
     */
    private fun fastestPair(times: Int = 5, small: () -> Unit, big: () -> Unit): Pair<Long, Long> {
        // JIT 를 데운다. 이 한 쌍은 재지 않는다.
        small()
        big()
        var bestSmall = Long.MAX_VALUE
        var bestBig = Long.MAX_VALUE
        repeat(times) {
            val t0 = System.nanoTime()
            small()
            bestSmall = minOf(bestSmall, System.nanoTime() - t0)
            val t1 = System.nanoTime()
            big()
            bestBig = minOf(bestBig, System.nanoTime() - t1)
        }
        return bestSmall to bestBig
    }

    private fun readAllOneByOne(reader: ArchiveReader): Int {
        var bytes = 0
        for (e in reader.entries) {
            if (!e.isReadable) continue
            reader.open(e).use { s -> bytes += s.readBytes().size }
        }
        return bytes
    }

    /**
     * 엔트리 수를 두 배로 늘리면 시간이 몇 배가 되는가.
     *
     * 선형이면 약 2배, 제곱이면 약 4배다. 잡음을 감안해 **2.6배를 넘으면 제곱**으로 본다.
     */
    @Test
    fun `7z 를 하나씩 열면 엔트리 수의 제곱으로 비싸진다`() {
        val small = ArchiveSamples.sevenZ(File(dir, "s.7z"), count = 60, bytesEach = 4096)
        val big = ArchiveSamples.sevenZ(File(dir, "b.7z"), count = 120, bytesEach = 4096)

        val (tSmall, tBig) = fastestPair(
            small = { Archives.open(FileDocumentSource(small), ParseLimits.DEFAULT).use { readAllOneByOne(it) } },
            big = { Archives.open(FileDocumentSource(big), ParseLimits.DEFAULT).use { readAllOneByOne(it) } },
        )
        val ratio = tBig.toDouble() / tSmall.toDouble()
        println("7z 하나씩: 60개 ${tSmall / 1_000_000}ms, 120개 ${tBig / 1_000_000}ms, 배율 ${"%.2f".format(ratio)}")
        assertTrue(
            ratio > 2.6,
            "엔트리를 두 배로 늘렸는데 ${"%.2f".format(ratio)}배밖에 안 늘었다 — " +
                "open() 이 이미 선형이라면 이 시험과 순차 API 의 전제가 틀린 것이다",
        )
    }

    /**
     * ZIP 은 무작위 접근이 싸다. 같은 시험에서 선형이어야 한다.
     *
     * **7z 보다 훨씬 많은 엔트리로 잰다.** ZIP 60개는 이 기계에서 **5 ms** 라 배율이
     * 재는 것이 비용이 아니라 타이머 잡음이 된다(실제로 0.74배와 3.41배가 번갈아 나왔다).
     * 400·800개로 키웠더니 13·20 ms 가 됐는데 그래도 상한에 닿을락 말락 했다(2.16배).
     * 1000·2000개면 30·60 ms 대라 한 자리 ms 의 흔들림이 배율을 움직이지 못한다.
     * 7z 을 같은 크기로 키우지 못하는 것은 그쪽이 제곱이라 2000개면 분 단위가 되기 때문이다.
     */
    @Test
    fun `ZIP 은 하나씩 열어도 선형이다`() {
        val small = ArchiveSamples.zip(File(dir, "s.zip"), count = 1000, bytesEach = 4096)
        val big = ArchiveSamples.zip(File(dir, "b.zip"), count = 2000, bytesEach = 4096)

        val (tSmall, tBig) = fastestPair(
            small = { Archives.open(FileDocumentSource(small), ParseLimits.DEFAULT).use { readAllOneByOne(it) } },
            big = { Archives.open(FileDocumentSource(big), ParseLimits.DEFAULT).use { readAllOneByOne(it) } },
        )
        val ratio = tBig.toDouble() / tSmall.toDouble()
        println("ZIP 하나씩: 1000개 ${tSmall / 1_000_000}ms, 2000개 ${tBig / 1_000_000}ms, 배율 ${"%.2f".format(ratio)}")
        assertTrue(ratio < 2.6, "ZIP 이 선형이 아니다: ${"%.2f".format(ratio)}배")
    }

    /** 무엇을 하든 **바이트는 같아야 한다.** 빠른 길이 다른 내용을 내놓으면 의미가 없다. */
    @Test
    fun `엔트리 내용이 번호와 맞는다`() {
        val f = ArchiveSamples.sevenZ(File(dir, "c.7z"), count = 8, bytesEach = 512)
        Archives.open(FileDocumentSource(f), ParseLimits.DEFAULT).use { r ->
            for ((i, e) in r.entries.withIndex()) {
                val got = r.open(e).use { it.readBytes() }
                assertContentEquals(ArchiveSamples.payload(i, 512), got, "$i 번 엔트리")
            }
        }
    }
}
