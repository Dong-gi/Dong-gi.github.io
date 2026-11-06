package io.github.donggi.iroiroviewer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.ui.icon.VisibilityIcon
import io.github.donggi.iroiroviewer.ui.icon.VisibilityOffIcon

/**
 * **새 암호**를 받는 두 칸(암호·암호 확인)의 상태 — 무엇을 잠글 때 쓴다(ZIP 만들기). 이미 있는 암호를 묻는 것은 [PasswordDialog] 다.
 *
 * [PasswordDialog] 와 같은 규칙을 지킨다 — 그래서 곁에 둔다. 상태는 **`remember` 로** 든다([rememberNewPasswordState] — 저장되는 상태면 회전할
 * 때 암호가 저장 번들로 들어간다, 함정 표 `rememberTextFieldState`), 칸은 보안 입력칸이다(가린다·복사를 막는다·키보드가 배우지 않는다), 꺼내는
 * 순간 `CharArray` 로 옮기고 칸을 비운다([take]). 칸의 버퍼 자체는 덮을 길이 없다(같은 한계).
 */
@Stable
class NewPasswordState internal constructor() {
    internal val first = TextFieldState()
    internal val second = TextFieldState()

    /** 첫 칸의 글자 — 사본이 아니다. 판정(영문인가·옮길 수 있는가)에 들여다보기만 한다. */
    val text: CharSequence get() = first.text

    /** 두 칸 모두 비었다 — 암호를 쓰지 않는다. */
    val isEmpty: Boolean get() = first.text.isEmpty() && second.text.isEmpty()

    /** 두 칸이 같다. */
    val matches: Boolean get() = contentEquals(first.text, second.text)

    /** 친 암호를 꺼내고 두 칸을 비운다. 첫 칸이 비었으면 null. **배열의 주인이 넘어간다** — 받은 쪽이 다 쓰고 0 으로 덮는다. */
    fun take(): CharArray? {
        val t = first.text
        val out = if (t.isEmpty()) null else CharArray(t.length) { t[it] }
        clear()
        return out
    }

    /** 두 칸을 비운다. */
    fun clear() {
        first.clearText()
        second.clearText()
    }

    private fun contentEquals(a: CharSequence, b: CharSequence): Boolean {
        if (a.length != b.length) return false
        for (i in 0 until a.length) if (a[i] != b[i]) return false
        return true
    }
}

/** 저장되지 않는 상태로 든다(머리말). 회전하면 입력이 지워지는 것이 대가다. */
@Composable
fun rememberNewPasswordState(): NewPasswordState = remember { NewPasswordState() }

/**
 * 새 암호의 두 칸. 첫 칸에 무엇이든 치면 확인 칸이 나온다 — 비워 두면 암호를 쓰지 않는 것이라 칸 하나로 끝난다. 두 칸이 다르면 확인 칸이
 * 말한다(확인 칸을 치기 시작한 뒤에만 — 치는 동안 빨갛게 하지 않는다). 확인 단추를 막을지는 부르는 쪽이 [NewPasswordState] 로 정한다.
 *
 * 눈 단추 하나가 두 칸을 함께 보이고 가린다(보이기는 저장하지 않는다 — 칸이 새로 뜨면 늘 가린 채다).
 *
 * @param label 첫 칸의 이름. 기본은 '암호 (선택)'.
 */
@Composable
fun NewPasswordFields(
    state: NewPasswordState,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.ui_password_optional),
) {
    var shown by remember { mutableStateOf(false) }
    val mode = if (shown) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped
    val eye: @Composable () -> Unit = {
        IconButton(onClick = { shown = !shown }) {
            Icon(
                imageVector = if (shown) VisibilityOffIcon else VisibilityIcon,
                contentDescription = stringResource(if (shown) R.string.ui_password_hide else R.string.ui_password_show),
            )
        }
    }
    // 칸이 화면에서 빠지면(닫기·화면 전환) 비운다 — 버퍼를 덮지는 못해도 글자를 들고 있는 시간은 줄인다.
    DisposableEffect(state) { onDispose { state.clear() } }
    Column(modifier) {
        OutlinedSecureTextField(
            state = state.first,
            label = { Text(label) },
            textObfuscationMode = mode,
            trailingIcon = eye,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.isEmpty) {
            val mismatch = state.second.text.isNotEmpty() && !state.matches
            OutlinedSecureTextField(
                state = state.second,
                label = { Text(stringResource(R.string.ui_password_confirm)) },
                isError = mismatch,
                supportingText = if (mismatch) {
                    { Text(stringResource(R.string.ui_password_mismatch)) }
                } else {
                    null
                },
                textObfuscationMode = mode,
                trailingIcon = eye,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
