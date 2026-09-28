package io.github.donggi.iroiroviewer.comic

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.selects.select

/**
 * 화면에 한 번만 전하는 것의 줄(14단계). 버퍼를 든 `Channel` 에 **아직 화면이 받지 않은 것을 버리는 길**을 더했다.
 *
 * ## 왜 버리는 길이 필요한가 — 수집기가 사라져도 줄은 남는다
 *
 * ViewModel 은 액티비티에 묶여 있고 알림을 받는 수집기는 화면에 묶여 있다. 수집기가 앞 알림을 띄우는 동안(10초) 다음
 * 것은 줄에서 기다리는데, 그 사이 뷰어를 닫으면 수집기만 사라지고 **줄에 남은 것은 다음에 뷰어를 열 때 배달된다.**
 * 같은 책을 다시 열면 '어느 책의 것인가'(경로)로도 가려지지 않는다 — 1쪽을 보고 있는데 '끝까지 읽었습니다. 다음 권'
 * 이 뜬다(검토가 잡았다). 9단계 뒤 감사가 적은 '버퍼를 가진 `Channel` 이 다음 문서의 화면에 배달한다' 와 같은 자리다.
 * 그래서 책을 열고 닫는 자리에서 [discardPending] 한다.
 */
internal class BookEvents<E : Any> {

    private val channel = Channel<E>(Channel.BUFFERED)

    /** 화면이 모은다. 받는 쪽은 하나다. */
    val flow: Flow<E> = channel.receiveAsFlow()

    fun offer(event: E) {
        channel.trySend(event)
    }

    /** 아직 받지 않은 것을 모두 버린다. 이미 받아 띄우는 중인 알림은 [showWhileCurrent] 가 걷는다. */
    fun discardPending() {
        while (channel.tryReceive().isSuccess) Unit
    }
}

/**
 * 동작 단추가 붙은 알림을 **그 책이 열려 있는 동안만** 띄운다(14단계).
 *
 * ## 왜 필요한가 — 책이 화면을 떠나지 않고 바뀐다
 *
 * 9단계까지는 책을 바꾸려면 뷰어를 닫아야 했고, 닫으면 알림을 띄우던 코루틴이 취소되어 알림도 함께 걷혔다. 다음 권
 * (14단계)은 **뷰어를 닫지 않고** 책을 바꾼다. 그대로 두면 앞 책의 '5쪽부터 이어서 봅니다 · 처음부터' 가 새 책 위에
 * 10초 동안 남고, 그것을 누르면 **새 책**이 처음으로 돌아간다(VM 은 하나이고 '처음부터' 는 지금 책에 걸린다). 새 책의
 * 이어보기 알림은 그 뒤에 줄을 서서 앞 것이 사라질 때까지 뜨지도 않는다.
 *
 * 그래서 책이 바뀌는 순간 알림을 걷는다 — `showSnackbar` 를 부른 코루틴을 취소하면 Material 3 가 그 알림을 치운다.
 * 기간은 여기서 손으로 적는다(동작 단추를 붙이면 기본값이 무기한이다 — 함정 표).
 *
 * @param stillCurrent 알림을 낸 책이 아직 열려 있는가.
 * @param changes 책이 바뀔 때 값을 내는 흐름(VM 의 상태). [stillCurrent] 를 다시 볼 때를 알려 줄 뿐 값은 읽지 않는다.
 * @return 사람이 고른 결과. 책이 바뀌어 걷었거나 처음부터 낡았으면 null — 부르는 쪽은 아무것도 하지 않는다.
 */
internal suspend fun SnackbarHostState.showWhileCurrent(
    stillCurrent: () -> Boolean,
    changes: Flow<*>,
    message: String,
    actionLabel: String,
): SnackbarResult? {
    if (!stillCurrent()) return null
    return coroutineScope {
        val shown = async {
            showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Long)
        }
        val stale = async { changes.first { !stillCurrent() } }
        try {
            select<SnackbarResult?> {
                shown.onAwait { it }
                stale.onAwait { null }
            }
        } finally {
            // 진 쪽을 거둔다. 알림이 떠 있는 채로 취소되면 Material 3 가 그 알림을 치운다.
            shown.cancel()
            stale.cancel()
        }
    }
}
