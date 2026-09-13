package io.github.donggi.iroiroviewer.format

import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * **문서를 여는 유일한 진입점.**
 *
 * 파서를 직접 부르지 마라. 시간 상한과 예외→[OpenFailure] 변환이 여기 한 곳에만
 * 있어야, 파서를 새로 붙일 때 그것을 빠뜨릴 수가 없다.
 *
 * 상한을 파서가 아니라 여기서 거는 이유는, 파서가 스스로 시계를 보게 하면 파서마다
 * 다르게 구현하고 결국 어딘가는 빠뜨리기 때문이다. 파서는 협조적 취소
 * (`currentCoroutineContext().ensureActive()`)만 지키면 된다.
 */
object Documents {

    suspend fun open(
        opener: DocumentOpener,
        source: DocumentSource,
        progress: ProgressSink = ProgressSink.NONE,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ): OpenOutcome = try {
        withTimeout(limits.openTimeoutMs) {
            opener.open(source, progress)
        }
    } catch (e: TimeoutCancellationException) {
        // 시간 상한은 '취소' 로 오지만 사용자에게는 실패다. 다시 던지면 조용히 사라진다.
        OpenOutcome.Failed(OpenFailure.Timeout("${limits.openTimeoutMs / 1000}초 안에 열지 못했다"))
    } catch (t: Throwable) {
        OpenOutcome.Failed(t.toOpenFailure())
    }
}
