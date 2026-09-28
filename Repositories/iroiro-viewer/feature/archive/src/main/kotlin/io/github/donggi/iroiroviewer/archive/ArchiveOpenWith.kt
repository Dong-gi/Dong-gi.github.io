package io.github.donggi.iroiroviewer.archive

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.donggi.iroiroviewer.io.ExternalOpen
import kotlinx.coroutines.launch

/**
 * 압축 화면이 '다른 앱으로 열기' 를 **어디에** 띄우는가. 화면의 판단을 여기 모아 JVM 에서 시험한다(`ArchiveOpenWithTest`).
 *
 * **넘기는 것은 언제나 압축 파일 자신이다. 안의 항목에는 두지 않는다** — 항목에는 경로가 없고, 경로를 만들려면 항목을
 * 디스크로 뽑아야 한다. 그것이 이 화면이 하지 않기로 한 일이다(`EntryNotice` 의 주석, CLAUDE.md '지원하지 않는 것').
 * 그래서 이 파일의 판단은 **화면의 상태** 하나만 본다 — 항목(`ArchiveTree.Node`)을 받는 함수가 없는 것이 그 약속이다.
 *
 * * **실패 화면**([onFailure]) — 이 앱이 목록을 보여 줄 수 없는 갈래에: 다루지 않는 형식(iso, tar 가 아닌 것을 싼
 *   `.gz`·`.bz2`·`.xz`, 분할 아카이브), 깨짐, 상한(항목 수·풀어야 할 양), 이 앱이 풀지 않는 잠금(PKWARE 의 강한 암호화).
 *   **파일에 닿지 못한 것에는 두지 않는다** — 받는 앱도 같은 파일에 닿지 못한다. **헤더까지 잠긴 것(암호 넣기)은 실패가
 *   아니다** — 이 앱이 묻고 푼다.
 * * **⋮ 메뉴**([inMenu]) — **이번 열기에서 파일에 닿은 뒤로는** 언제나. 목록이 열려 있어도(다른 압축 앱으로 풀고 싶을
 *   수 있다), 암호를 묻는 중이어도(사용자가 고른 것이다), 단추가 있는 실패 화면이어도(겹쳐 둔다 — 형제 화면과 같은 규칙)
 *   누를 수 있다. 닿기 전(파일을 열어 보기 전, 수 ms)과 닿지 못한 실패에는 흐리다 — 없는 파일은 `ExternalOpen` 이 '넘길 수
 *   없다' 로 받지만, **있는데 열리지 않는 파일**은 거기서 걸러지지 않고 받는 앱에 넘어가 그 앱이 같은 벽에 부딪힌다.
 *
 * **`else` 로 끝내지 않는다.** 상태·종류가 늘면 여기 `when` 이 컴파일되지 않는다 — 새 갈래를 여기서 정하기 전까지.
 */
internal object ArchiveOpenWith {

    /** 이 실패 화면에 '다른 앱으로 열기' 단추를 둘 것인가. */
    fun onFailure(kind: ArchiveViewModel.State.Failed.Kind): Boolean = when (kind) {
        ArchiveViewModel.State.Failed.Kind.UNSUPPORTED -> true
        ArchiveViewModel.State.Failed.Kind.CORRUPT -> true
        // 두 상한은 이 앱의 방어이지 파일의 결함이 아니다 — 다른 압축 앱은 연다.
        ArchiveViewModel.State.Failed.Kind.TOO_LARGE -> true
        ArchiveViewModel.State.Failed.Kind.TOO_BIG -> true
        // 공개되지 않은 방식이라 이 앱이 풀지 않는 잠금. 그 방식을 아는 앱(PKWARE)은 연다.
        ArchiveViewModel.State.Failed.Kind.ENCRYPTED -> true
        // 없거나 열리지 않는다(`isReachable` — 권한 거부도 여기로 온다). 받는 앱도 같은 파일에 닿지 못한다.
        ArchiveViewModel.State.Failed.Kind.UNREADABLE -> false
    }

    /** ⋮ 의 '다른 앱으로 열기' 를 누를 수 있는가. */
    fun inMenu(state: ArchiveViewModel.State): Boolean = when (state) {
        // 압축한 tar 의 목록은 끝까지 풀어야 서서 몇 초가 걸린다. 그동안에도 넘길 수 있어야 기다리지 않는다 — 다만 파일에
        // 닿은 뒤부터다.
        is ArchiveViewModel.State.Loading -> state.reached
        ArchiveViewModel.State.NeedsPassword,
        is ArchiveViewModel.State.Ready,
        -> true
        is ArchiveViewModel.State.Failed -> onFailure(state.kind)
    }
}

/**
 * [path] 를 다른 앱으로 연다 — 언제나 고르는 창이다. 띄우지 못했으면 우리 문장([ExternalOpen.messageOf])을 이 화면의
 * 스낵바로 알린다(예외 문구는 내보내지 않는다). 띄웠으면 알릴 것이 없다.
 *
 * **액티비티의 컨텍스트로 부른다**(`LocalContext`). 받는 앱의 화면이 우리 태스크 위에 쌓여 뒤로 가면 이 목록으로
 * 돌아온다([ExternalOpen] 의 '태스크'). 호출은 동기이고 가볍다 — 주 스레드에서 그대로 부른다.
 */
@Composable
internal fun rememberOpenWith(path: String, snackbar: SnackbarHostState): () -> Unit {
    val context = LocalContext.current
    // 문장은 `LocalResources` 에서 읽는다 — 컨텍스트에서 읽으면 구성이 바뀌어도 다시 읽히지 않는다(lint 의
    // LocalContextGetResourceValueCall). 결과마다 문장이 다르므로 미리 `stringResource` 로 셋을 들지 않고 누른 자리에서 고른다.
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(path, context, resources, snackbar, scope) {
        {
            val result = ExternalOpen.open(context, path, ExternalOpen.Mode.CHOOSE)
            ExternalOpen.messageOf(result)?.let { id ->
                val message = resources.getString(id)
                scope.launch { snackbar.showSnackbar(message) }
            }
        }
    }
}
