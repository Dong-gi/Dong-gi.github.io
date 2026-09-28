package io.github.donggi.iroiroviewer.text

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.donggi.iroiroviewer.io.ExternalOpen
import kotlinx.coroutines.launch

/**
 * 텍스트 뷰어가 '다른 앱으로 열기' 를 **어디에** 띄우는가. 화면의 판단을 여기 모아 JVM 에서 시험한다(`TextOpenWithTest`).
 *
 * * **실패 화면**([onFailure]) — '글이 아니다(바이너리)' 와 '읽지 못하는 인코딩(UTF-32)' 에. 이 앱이 보여 줄 수 없을 뿐
 *   파일은 멀쩡하다 — 그림·압축이 확장자만 `.txt` 인 것일 수도 있고, UTF-32 를 읽는 편집기도 있다. **파일에 닿지 못한
 *   것(없다·못 읽는다)에는 두지 않는다** — 받는 앱도 같은 파일에 닿지 못한다.
 * * **⋮ 메뉴**([inMenu]) — **이번 열기에서 파일에 닿은 뒤로는** 언제나. 마크다운 미리보기 중에도 원문 파일을 넘긴다
 *   (미리보기는 이 앱이 만든 HTML 이지 파일이 아니다). 닿기 전(앞머리를 읽기 전, 수 ms)과 닿지 못한 실패에는 흐리다 —
 *   없는 파일은 `ExternalOpen` 이 '넘길 수 없다' 로 받지만, **있는데 열리지 않는 파일**은 거기서 걸러지지 않고 받는
 *   앱에 넘어가 그 앱이 같은 벽에 부딪힌다. 단추가 있는 실패 갈래에도 메뉴를 둔다(겹쳐 둔다 — 형제 화면과 같은 규칙).
 *
 * **`else` 로 끝내지 않는다.** 상태·종류가 늘면 여기 `when` 이 컴파일되지 않는다 — 새 갈래를 여기서 정하기 전까지.
 */
internal object TextOpenWith {

    /** 이 실패 화면에 '다른 앱으로 열기' 단추를 둘 것인가. */
    fun onFailure(kind: TextViewModel.State.Failed.Kind): Boolean = when (kind) {
        TextViewModel.State.Failed.Kind.BINARY -> true
        TextViewModel.State.Failed.Kind.UNSUPPORTED_ENCODING -> true
        TextViewModel.State.Failed.Kind.UNREADABLE -> false
    }

    /** ⋮ 의 '다른 앱으로 열기' 를 누를 수 있는가. */
    fun inMenu(state: TextViewModel.State): Boolean = when (state) {
        // 앞머리를 읽기 전에는 파일에 닿았는지 모른다.
        is TextViewModel.State.Loading -> state.reached
        // 색인은 200 MB 로그에서 몇 초가 걸린다. 그동안에도 다른 앱으로 넘길 수 있어야 기다리지 않는다.
        is TextViewModel.State.Indexing, is TextViewModel.State.Ready -> true
        is TextViewModel.State.Failed -> onFailure(state.kind)
    }
}

/**
 * [path] 를 다른 앱으로 연다 — 언제나 고르는 창이다. 띄우지 못했으면 우리 문장([ExternalOpen.messageOf])을 이 화면의
 * 스낵바로 알린다(예외 문구는 내보내지 않는다). 띄웠으면 알릴 것이 없다.
 *
 * **액티비티의 컨텍스트로 부른다**(`LocalContext`). 받는 앱의 화면이 우리 태스크 위에 쌓여 뒤로 가면 이 파일로
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
