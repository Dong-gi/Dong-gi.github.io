package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.ArchivePath
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.InputStream
import java.io.OutputStream

/**
 * 아카이브 안의 항목 하나.
 *
 * **신원은 [index] 다. 이름이 아니다.** ZIP 명세는 같은 이름의 엔트리를 금지하지 않고,
 * 이름은 인코딩 판정을 거친 *손실 가능한* 문자열이라 서로 다른 엔트리가 같은 문자열로
 * 접힐 수 있다. 이름으로 찾으면 사용자가 고른 것과 다른 파일의 바이트가 열린다.
 *
 * [declaredSize] 를 "선언된" 이라고 부르는 것은 그것이 **공격자가 적는 값**이기
 * 때문이다. 화면에 보여주는 것 말고 어떤 결정에도 쓰지 마라 — 버퍼 크기를 이 값으로
 * 잡는 순간 42.zip 한 줄에 앱이 죽는다.
 */
data class ArchiveEntry(
    /** 아카이브 안의 순서. 리더가 실제 엔트리를 찾는 데 쓰는 유일한 키다. */
    val index: Int,
    /** 디코드된 이름. 화면에 보여주는 용도다. */
    val name: String,
    /** [ArchivePath] 를 통과한 안전한 상대경로. null 이면 풀어서는 안 되는 이름이다. */
    val safeName: String?,
    val declaredSize: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    /** 심볼릭 링크·하드링크·정션. 내용이 파일이 아니라 경로 문자열이다. */
    val isLink: Boolean,
    /** 암호가 걸려 있다. 이 뷰어는 복호화하지 않는다. */
    val isEncrypted: Boolean,
    /** 없으면 -1. */
    val crc: Long,
    /** 이름을 어떤 인코딩으로 읽었는가. 진단과 회귀 시험에 쓴다. */
    val nameCharset: String,
    /**
     * 수정시각(epoch 밀리초). **0 은 '모름' 이다.**
     *
     * 푼 파일에 되살린다. 되살리지 않으면 아카이브를 풀 때마다 수천 개 파일의 시각이
     * 전부 '지금' 이 되어 날짜 정렬이 무너지고, 이어보기 키(`sha256(크기+이름+수정시각)`)
     * 도 함께 끊긴다 — 4단계가 복사에서 이미 한 번 고친 실패 형태다.
     *
     * 모르면 **지어내지 않는다.** 0 이면 푼 쪽이 시각을 건드리지 않는다.
     */
    val lastModified: Long = 0L,
) {
    /** 실제로 열어도 되는 항목인가. */
    val isReadable: Boolean
        get() = !isDirectory && !isLink && !isEncrypted && safeName != null

    companion object {
        fun sanitize(name: String): String? = ArchivePath.sanitize(name)
    }
}

/**
 * 아카이브를 읽는 공통 계약.
 *
 * ## 자원 규칙
 *
 * 리더를 만드는 데 실패하면 **리더가 스스로 정리한다** — 생성자가 던진 객체는
 * 호출자가 닫을 방법이 없기 때문이다. 성공한 뒤로는 호출자가 [close] 할 책임을 진다.
 * [open] 이 돌려준 스트림은 리더보다 먼저 닫아야 한다.
 *
 * ## 블로킹 규칙
 *
 * [open] 이 돌려주는 스트림의 `read` 는 **블로킹이고 코루틴 취소에 반응하지 않는다.**
 * solid 아카이브에서는 한 번의 `read` 가 앞 엔트리 전체를 푸느라 오래 걸릴 수 있다.
 * 반드시 `runInterruptible { }` 안에서 읽어라 — 그래야 취소가 스레드 인터럽트로
 * 전달되고, 구현이 그것을 보고 멈춘다.
 */
interface ArchiveReader : AutoCloseable {

    val formatId: FormatId

    val entries: List<ArchiveEntry>

    /**
     * 무작위 접근이 싼가.
     *
     * ZIP 은 true — 엔트리마다 독립 압축이라 필요한 것만 푼다.
     * 7z(solid)와 RAR(solid)은 false — 앞엣것을 다 풀어야 뒤엣것이 나온다. 만화 뷰어는
     * 이 값이 false 면 "열 때 한 번에 다 풀어 캐시" 전략으로 바꾼다.
     */
    val randomAccess: Boolean

    /**
     * 엔트리 하나를 읽는 스트림. 상한이 걸린 스트림이 나온다.
     *
     * **한 항목을 보여 주는 용도다.** 여러 항목을 이것으로 훑지 마라 —
     * [extractSequentially] 주석의 실측을 보라.
     *
     * @throws IllegalArgumentException 읽을 수 없는 항목(디렉터리·링크·암호·위험한 이름)일 때.
     */
    fun open(entry: ArchiveEntry): InputStream

    /**
     * 엔트리들을 **인덱스 순서로 한 번만 훑어** 소비한다. 푸는 쪽이 쓰는 길이다.
     *
     * ## 왜 [open] 을 반복하면 안 되는가 — 실측
     *
     * 7z 에서 엔트리마다 [open] 을 부르면 그때마다 파일을 새로 열고 `nextEntry` 를
     * index 번 돌린다. 개발 PC 에서 잰 값이다.
     *
     * | 엔트리 | 하나씩 열기 |
     * |---|---|
     * | 60개 | 702 ms |
     * | 120개 | 2,705 ms (**3.85배**) |
     *
     * 두 배로 늘렸는데 네 배가 되었다 — **제곱**이다. ZIP 은 같은 모양의 시험에서
     * 1.25~1.42배(400개 14 ms → 800개 20 ms)로 선형 이하다.
     * 1,000개짜리 7z 를 하나씩 풀면 몇 분이 걸린다.
     *
     * ## 왜 밀어 주는(push) 모양인가
     *
     * 소비자가 [EntrySink.begin] 으로 받을 곳을 주고 리더가 거기에 쓴다. 당겨 가는
     * 모양(`InputStream` 을 넘겨주기)이 더 자연스러워 보이지만, **RAR 에서 그 길이 막힌다** —
     * junrar 의 `Archive.getInputStream` 은 `PipedInputStream` 과 **새 스레드**를 만든다
     * (바이트코드로 확인했다). 그러면 우리 스레드를 인터럽트해도 해제가 멈추지 않는다.
     * 반면 `Archive.extractFile(header, out)` 은 **부르는 스레드에서** 돌기 때문에,
     * 우리가 쥔 [java.io.OutputStream] 의 `write` 에서 인터럽트를 보면 취소가 닿는다.
     *
     * (junrar 의 추출 *헬퍼*(`Junrar.extract`·`LocalFolderExtractor`)를 쓰지 않는다는
     * 규칙은 그대로다 — 그것들은 **스스로 경로를 만들어** 파일을 쓰고 경로 탈출 CVE 가
     * 거기 있었다. `extractFile(header, out)` 은 우리가 준 스트림에 쓸 뿐 경로를 모른다.)
     *
     * ## 구현이 지켜야 하는 것
     *
     * - 인덱스 오름차순으로 정확히 한 번씩 방문한다.
     * - [EntrySink.begin] 이 null 을 주면 건너뛴다. **solid 아카이브에서는 건너뛴 것도
     *   실제로는 풀린다** — 그것이 solid 의 뜻이다. 상한 계산이 그 사실을 알아야 한다.
     * - 엔트리 하나의 실패는 [EntrySink.finish] 에 실어 보고하고 **다음으로 간다.**
     *   위로 던지는 것은 취소와 아카이브 전체를 무효로 만드는 오류뿐이다.
     */
    fun extractSequentially(sink: EntrySink)

    /**
     * solid 압축인가 — 엔트리 하나를 꺼내려면 앞엣것을 풀어야 하는가.
     *
     * [randomAccess] 의 반대말처럼 보이지만 뜻이 다르다. [randomAccess] 는 "아무 엔트리나
     * 싸게 열 수 있는가" 이고 이것은 "앞엣것에 의존하는가" 다. 화면은 이 값이 참일 때
     * 썸네일 같은 무작위 접근을 아예 시도하지 않는다.
     */
    val solid: Boolean get() = !randomAccess
}

/**
 * 순차 추출이 엔트리마다 부르는 것.
 *
 * **[begin] 이 null 을 주는 것은 '건너뛰라' 는 뜻이지 '실패' 가 아니다.** 고르지 않은
 * 엔트리, 풀 수 없는 이름, 링크, 암호 항목이 전부 여기서 걸러진다.
 */
interface EntrySink {

    /** 이 엔트리를 받을 곳. null 이면 건너뛴다. 돌려준 스트림은 리더가 닫지 않는다. */
    fun begin(entry: ArchiveEntry): OutputStream?

    /**
     * [begin] 이 스트림을 준 엔트리가 끝났다. **성공이든 실패든 정확히 한 번 불린다.**
     *
     * @param written 실제로 쓴 바이트.
     * @param failure 이 엔트리만의 실패. null 이면 성공이다.
     */
    fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?)
}

/**
 * 이 상한 위반이 **아카이브 전체를 무효로 만드는가.**
 *
 * 상한에는 두 종류가 있고 대응이 달라야 한다.
 *
 * - **엔트리 상한**(`maxSingleOutput`·`maxCompressionRatio`) — 그 항목 하나가 이상한
 *   것이다. 그 항목만 실패로 세고 나머지는 계속 푼다. 폭탄 하나 때문에 멀쩡한 999개를
 *   버리지 않는다(4단계가 '항목 실패가 배치를 끝내지 않는다' 로 이미 고친 형태다).
 * - **아카이브 상한**(`maxTotalOutput`·`maxEntries`) — 한 번 넘으면 **그 뒤 전부가
 *   반드시 넘는다.** 항목 실패로 세면 9,999번을 더 시도하고 9,999개의 실패를 보고한다.
 *   그래서 위로 던져 그 자리에서 끝낸다.
 */
internal fun ParseLimitExceededException.isFatalForArchive(): Boolean =
    limitName == "maxTotalOutput" || limitName == "maxEntries"
