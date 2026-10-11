package io.github.donggi.iroiroviewer.docview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.donggi.iroiroviewer.io.EditorApp

/*
 * 문서 뷰어 ⋮ 의 **'편집'** — 편집기(`feature:editor` — `EditorApp`)로 넘긴다.
 *
 * 이 뷰어가 여는 것은 PDF·EPUB 이다(오피스·한글 문서는 편집기의 보기 모드가 연다 — `app` 이 가른다). 그 가운데 편집기가
 * 다루는 것은 PDF 다 — ONLYOFFICE 가 고칠 수 있는 문서(docx)로 바꿔 열고, 저장하면 원본 곁에 새 파일이 생긴다(`EditorSlots`).
 *
 * **읽기 전용 사본(다른 앱이 준 것)에는 없다** — `app` 이 그 화면에 [DocViewScreen] 의 `onEditor` 를 주지 않는다(곁에 쓰면 사용자가 찾아갈 수
 * 없는 앱 캐시다).
 */

/**
 * [path] 를 편집기로 고칠 수 있으면 그 형식 글자, 아니면 null — 꾸러미 관리자에 묻는다(경로가 바뀔 때만). [onEditor] 가 없으면(읽기 전용 사본)
 * 묻지 않는다. 실패 화면의 '편집기로 열기'(이 뷰어가 보여 주지 못한 문서)와 '편집' 이 같은 답을 쓴다.
 */
@Composable
internal fun rememberEditorKind(path: String, onEditor: ((String) -> Unit)?): String? {
    val context = LocalContext.current
    return remember(path, onEditor != null) { if (onEditor != null) EditorApp.kindFor(context, path) else null }
}

/**
 * ⋮ 의 '편집' 을 누르면 할 일 — 편집기가 그 형식을 다루지 않으면 null(줄이 없다). 누르면 뷰어의 문서를 닫고(두 벌을 들지 않게) [onEditor] 에
 * 편집기의 형식 글자를 넘긴다. 닫은 문서는 `app` 이 다시 연다(`reopen`) — 편집기에서 돌아올 때(형식을 바꿔 곁에 새 파일을 만들었어도 원래
 * 문서를), 그리고 편집기를 띄우지 못했을 때(닫힌 채 '여는 중' 동그라미가 남았다).
 */
@Composable
internal fun rememberEditAction(path: String, closeViewer: () -> Unit, onEditor: ((String) -> Unit)?): (() -> Unit)? {
    // 부르는 쪽은 람다를 재구성마다 새로 준다 — 최신 것을 읽는다(단추의 람다는 그대로 둔다).
    val latestEditor by rememberUpdatedState(onEditor)
    val latestClose by rememberUpdatedState(closeViewer)
    val editorKind = rememberEditorKind(path, onEditor) ?: return null
    return remember(editorKind) {
        {
            latestClose()
            latestEditor?.invoke(editorKind)
        }
    }
}
