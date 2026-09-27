package io.github.donggi.iroiroviewer.format.cfb

import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test

/**
 * [TinyCfb] 가 짠 파일을 **독립 구현(olefile)** 으로 확인하기 위한 내보내기. 평소에는 건너뛴다.
 *
 * 환경 변수 `CFB_DUMP_DIR` 을 주고 돌리면 그 폴더에 표본과 목록(`경로 \t 크기 \t SHA-256`)을 쓴다.
 * 목록은 **짜개에 넣은 바이트**에서 셈한다(우리 리더를 거치지 않는다). 생성 스크립트
 * (`verify_tinycfb.py`, 저장소 밖)가 olefile 로 열어 목록과 견준다. 12단계에서 v3·v4·DIFAT 표본 셋을
 * 이렇게 확인했다 — 짜개가 틀리면 리더 시험이 **같은 잘못을 서로 맞다고** 하게 되기 때문이다.
 * (표본은 시험용 합성 바이트다. 사용자 문서도 평문도 아니다.)
 */
class TinyCfbOracleDump {

    private fun data(n: Int, seed: Int) = ByteArray(n) { ((it * 31 + seed) xor (it ushr 7)).toByte() }

    @Test
    fun 표본을_내보낸다() {
        val dir = System.getenv("CFB_DUMP_DIR")
        assumeTrue("CFB_DUMP_DIR 이 없다", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        val tree = linkedMapOf(
            "small" to data(100, 1),
            "Sub/inner" to data(3000, 2),
            "Sub/Deeper/leaf" to data(64, 3),
            "big" to data(10_000, 4),
            "empty" to ByteArray(0),
            "\u0005SummaryInformation" to data(200, 5),
        )
        val difat = linkedMapOf("huge" to data(7_300_000, 11), "tiny" to data(100, 1))
        val samples = listOf(
            Triple("tiny-v3", 3, tree),
            Triple("tiny-v4", 4, tree),
            Triple("tiny-difat", 3, difat),
        )
        for ((name, version, streams) in samples) {
            val t = TinyCfb(version)
            for ((path, bytes) in streams) t.stream(path, bytes)
            if (streams === tree) t.storage("EmptyStorage")
            File(out, "$name.cfb").writeBytes(t.build().bytes)
            val lines = streams.map { (path, bytes) ->
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                "${path.replace("\u0005", "\\x05")}\t${bytes.size}\t$sha"
            }
            File(out, "$name.listing").writeText(lines.joinToString("\n") + "\n")
        }
    }
}
