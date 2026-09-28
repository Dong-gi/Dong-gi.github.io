package io.github.donggi.iroiroviewer.docview

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.io.ExternalOpen
import kotlinx.coroutines.launch

/**
 * 문서 뷰어가 '다른 앱으로 열기' 를 **어디에** 띄우는가. 화면의 판단을 여기 모아 JVM 에서 시험한다(`DocOpenWithTest`).
 *
 * ## 실패 화면 — 갈래마다 여기서 정한다
 *
 * 띄우는 것은 **'이 앱이 이 파일을 보여 줄 수 없다'** 로 끝난 자리뿐이다. 그 파일을 여는 앱은 따로 있을 수 있다.
 * **파일에 닿지 못한 실패(권한·입출력)에는 띄우지 않는다** — 받는 앱도 우리가 준 URI 로 같은 파일을 읽으므로 같은
 * 벽에 부딪히고, 사용자는 '다른 앱이면 된다' 로 잘못 읽는다. **이 앱이 스스로 묻는 암호에도 띄우지 않는다** — 암호를
 * 넣으면 여기서 열린다.
 *
 * **`else` 로 끝내지 않는다.** [OpenFailure] 는 sealed 라, 갈래가 새로 생기면 [onFailure] 의 `when` 이 컴파일되지 않는다
 * — 그 갈래를 여기서 정하기 전까지. 종류를 옮긴 뒤 `else` 가 조용히 틀린 답을 내던 자리(함정 표의 `FileKind`)를 되풀이하지
 * 않으려는 것이다.
 *
 * ## 위쪽 막대의 ⋮ — 파일에 닿은 뒤로는 언제나
 *
 * 규칙은 하나다: **이번 열기에서 파일에 닿았으면 둔다**([inMenu]) — 닿은 뒤에 읽기가 막힌 실패(`NoPermission`·`Io`)는
 * 닿지 못한 것으로 친다. 압축·텍스트 화면의 ⋮ 와 같은 규칙이다(만화는 여는 중에 ⋮ 의 이 줄을 두지 않는다 —
 * `ComicOpenWith.menuTarget`).
 *
 * | 상태 | ⋮ | 까닭 |
 * |---|---|---|
 * | 여는 중, 닿기 전 | 없다 | 없거나 열리지 않는 파일일 수 있다 — 받는 앱도 같은 벽에 부딪힌다. 파일을 열어 보는 한 번(수 ms)이다 |
 * | 여는 중, 닿은 뒤 | 있다 | docx·HWPX 는 몇 초, 상한은 30초다. 다른 앱으로 넘기려는 사람이 그만큼 기다리지 않게 |
 * | 열렸다 | 있다 | '지금 보는 파일을 다른 앱으로' |
 * | 암호를 묻는다 | 있다 | 이 앱이 묻고 풀지만, 암호를 모르는 사람도 있고 다른 앱에 맡기려는 사람도 있다. 압축·만화의 암호 화면과 같다 — 실패 화면의 단추는 없다(암호 넣기와 다투지 않게) |
 * | 실패 화면에 단추가 있는 갈래 | 있다 | **단추와 겹쳐도 둔다.** ⋮ 는 모든 뷰어에서 '다른 앱으로 열기' 를 찾는 고정된 자리라, 갈래에 따라 ⋮ 가 났다 없어졌다 하면 사용자가 볼 수 없는 구분이 드러난다. 형제 화면(텍스트·압축·만화)도 겹쳐 둔다 |
 * | 파일에 닿지 못했다(`NoPermission`·`Io`·[DocViewModel.State.Failed.unreachable]) | 없다 | 받는 앱도 닿지 못한다 |
 *
 * ⋮ 안에 든 것이 이것 하나라 '없다' 는 ⋮ 자체가 없다는 뜻이다(흐린 항목 하나만 든 메뉴를 열게 하지 않는다). 둘 다 언제나
 * 고르는 창이다([ExternalOpen.Mode.CHOOSE]) — 사용자가 '다른 앱' 을 골라 달라고 말한 자리다.
 *
 * **막대가 없으면 ⋮ 도 없다.** PDF 쪽을 두드리면 막대가 숨는데, 그 두드림이 있는 곳은 쪽이 떠 있는 화면뿐이다 — 여는 중·
 * 실패 화면에서는 숨은 막대를 되살릴 손이 없다. 그래서 막대를 숨길 수 있는 것은 열린 문서뿐이다([barVisible]).
 */
internal object DocOpenWith {

    /**
     * 이 실패 화면에 '다른 앱으로 열기' 단추를 둘 것인가 — 화면이 부르는 쪽. 파일에 닿지 못한 표시가 서 있으면 종류와
     * 상관없이 두지 않는다. 지금은 닿지 못한 실패가 `Io`·`NoPermission` 뿐이지만(공용 매핑), 매핑이 다른 갈래를 내게 되면
     * 없는 파일에 단추가 뜨는 길이 생긴다.
     */
    fun onFailure(state: DocViewModel.State.Failed): Boolean = !state.unreachable && onFailure(state.failure)

    /** 이 실패 종류에 '다른 앱으로 열기' 단추를 둘 것인가. */
    fun onFailure(failure: OpenFailure): Boolean = when (failure) {
        // 이 앱이 다루지 않는 갈래(.mobi·.odt·.xlsb …)와 이전 형식(.doc·.xls·.ppt·한글 97 이전) — 그것을 여는 앱이 따로 있다.
        is OpenFailure.Unsupported -> true
        is OpenFailure.LegacyFormat -> true
        // 우리가 풀 수 없는 잠금 — 상업 DRM·인증서로 잠근 PDF·HWP 5.0 의 암호. 열쇠를 가진 앱(발행처의 뷰어)은 연다.
        is OpenFailure.Encrypted -> true
        // 우리 파서가 명세에 어긋난다고 본 것. 더 너그러운 앱은 고쳐 가며 연다(오피스가 그렇다).
        is OpenFailure.Corrupt -> true
        // 우리 방어 상한(크기·시간)에 걸렸다. 상한은 이 앱의 약속이지 파일의 결함이 아니다.
        is OpenFailure.TooLarge -> true
        is OpenFailure.Timeout -> true
        // 암호는 이 앱이 묻는다(표준 보안 처리기·암호 OOXML·HWPX). 여기서 다른 앱을 권하면 '이 앱은 못 연다' 로 읽힌다.
        is OpenFailure.PasswordRequired -> false
        // 파일에 닿지 못했다(권한·입출력·없는 파일 — 없는 파일은 `Io` 로 온다). 다른 앱도 같은 벽에 부딪힌다.
        is OpenFailure.NoPermission -> false
        is OpenFailure.Io -> false
    }

    /** 위쪽 막대의 ⋮ 에 '다른 앱으로 열기' 를 둘 것인가. 파일에 닿은 뒤로는 언제나다(이 객체의 표). */
    fun inMenu(state: DocViewModel.State): Boolean = when (state) {
        is DocViewModel.State.Loading -> state.reached
        is DocViewModel.State.Ready -> true
        // 실패 단추의 갈래에 이 앱이 묻는 암호를 더한 것이다 — 빠지는 것은 파일에 닿지 못한 갈래뿐이다. 새 갈래는
        // [onFailure] 의 `when` 에서 정해지고 여기는 그 답을 따른다(한쪽만 고쳐지지 않게).
        is DocViewModel.State.Failed ->
            !state.unreachable && (onFailure(state.failure) || state.failure is OpenFailure.PasswordRequired)
    }

    /**
     * ⋮ 를 든 위쪽 막대를 그릴 것인가. [tapVisible] 은 PDF 쪽을 두드려 여닫는 값이다. 그 두드림은 **쪽이 떠 있을 때**
     * (열린 문서)에만 있으므로, 여는 중·실패 화면에서는 두드림의 값과 상관없이 그린다 — 숨은 채로 남으면 닫기·⋮ 에
     * 닿을 길이 뒤로가기 말고는 없다. (EPUB·흐름 문서는 이 막대를 쓰지 않고 칸으로 나눈 막대를 늘 그린다.)
     */
    fun barVisible(tapVisible: Boolean, state: DocViewModel.State): Boolean =
        tapVisible || state !is DocViewModel.State.Ready
}

/**
 * [path] 를 다른 앱으로 연다 — 언제나 고르는 창이다. 띄우지 못했으면 우리 문장([ExternalOpen.messageOf])을 이 화면의
 * 스낵바로 알린다(예외 문구는 내보내지 않는다). 띄웠으면 알릴 것이 없다.
 *
 * **액티비티의 컨텍스트로 부른다**(`LocalContext`). 받는 앱의 화면이 우리 태스크 위에 쌓여 뒤로 가면 이 문서로
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
