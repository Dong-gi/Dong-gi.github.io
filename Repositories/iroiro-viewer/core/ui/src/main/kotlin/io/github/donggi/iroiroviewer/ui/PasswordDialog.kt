package io.github.donggi.iroiroviewer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 암호를 묻는다. 문서·압축 파일·만화가 같은 것을 쓴다.
 *
 * **한 곳에 두는 이유**는 암호가 머무는 자리를 줄이는 규칙이 여기 있기 때문이다. 화면마다 따로
 * 만들면 그중 하나가 `rememberTextFieldState()` 를 써서 암호를 저장 번들에 싣는 날이 온다.
 *
 * ## 암호가 머무는 곳을 줄인다
 *
 * * 입력 상태를 **`remember` 로** 든다. `rememberTextFieldState()` 는 저장되는 상태라 회전할 때
 *   암호가 **저장 상태 번들**로 들어가고, 그 번들은 최근 앱 화면을 위해 시스템이 디스크에 쓸 수
 *   있다. 회전하면 입력이 지워지는 것이 대가다.
 * * `SecureTextField` 를 쓴다 — 글자를 가리고, 복사·잘라내기를 막고, 키보드가 입력을 배우지
 *   않게(`KeyboardType.Password`) 한다.
 * * 넣는 순간 `CharArray` 로 옮기고 입력칸을 비운다. 배열은 받은 쪽이 쓰고 지운다.
 *
 * **완전히 지우지는 못한다.** Compose 입력칸은 글자를 자기 버퍼(`CharSequence`)에 들고 있어
 * 우리가 그 메모리를 0 으로 덮을 길이 없다. 줄일 수 있는 데까지 줄인 것이다.
 *
 * @param title·[message] 무엇을 여는지는 부르는 쪽이 안다(문서인가 압축 파일인가).
 * @param wrong 방금 넣은 암호가 틀렸다 — 입력칸 아래에 알린다.
 * @param onSubmit **배열의 주인이 넘어간다.** 받은 쪽이 다 쓰고 0 으로 덮는다.
 */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    wrong: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    val field = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    val submit = {
        val text = field.text
        if (text.isNotEmpty()) {
            val chars = CharArray(text.length) { text[it] }
            field.clearText()
            onSubmit(chars)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                OutlinedSecureTextField(
                    state = field,
                    label = { Text(stringResource(R.string.ui_password_label)) },
                    isError = wrong,
                    supportingText = if (wrong) {
                        { Text(stringResource(R.string.ui_password_wrong)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { submit() },
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = field.text.isNotEmpty(), onClick = { submit() }) {
                Text(stringResource(R.string.ui_password_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_password_cancel)) }
        },
    )
    // 대화상자가 뜨면 곧바로 칠 수 있게 한다 — 키보드를 여는 것까지 한 번에.
    LaunchedEffect(Unit) { focus.requestFocus() }
}
