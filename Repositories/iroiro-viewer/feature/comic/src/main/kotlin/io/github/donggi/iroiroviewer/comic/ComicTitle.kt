package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.PathNames

/**
 * 위쪽 막대의 제목. 판단을 여기 모아 JVM 에서 시험한다(`ComicTitleTest`).
 *
 * * **열린 책은 책의 이름이다**([ComicViewModel.Book.name] — 책 파일은 확장자를 뗀 이름, 폴더는 폴더 이름).
 * * **여는 중·실패 화면은 여는 책의 경로에서 뗀 이름이다.** 전에는 비어 있었다 — 깨졌거나 잠긴 `.cbz` 가 '다른 앱으로 열기'
 *   단추와 함께 떠도 무엇을 넘기는지 보이지 않았다. 문서·텍스트·압축 뷰어가 같은 규칙이다(`DocTitle`·`TextTitle`·`ArchiveTitle`).
 *   **열린 뒤의 이름과 같은 모양으로 적는다**([bookNameOf]) — 다르면 책이 열리는 순간 제목의 확장자가 사라지며 글자가 바뀐다.
 *
 * 화면이 받은 경로가 아니라 **지금 여는 책의 경로**를 받는다 — 다음 권으로 넘어가는 동안에는 새 책의 이름이어야 한다.
 */
internal object ComicTitle {

    fun of(state: ComicViewModel.State, bookPath: String): String = when (state) {
        is ComicViewModel.State.Ready -> state.book.name
        ComicViewModel.State.Loading, is ComicViewModel.State.Failed -> bookNameOf(PathNames.lastSegment(bookPath))
    }

    /**
     * [ComicViewModel.Book.name] 과 같은 모양 — 책 파일은 확장자를 뗀다. 폴더인지는 여기서 묻지 않는다(디스크를 읽는 일이고
     * 이 함수는 그리는 중에 불린다). 그래서 **확장자가 만화·압축일 때만** 뗀다 — `Vol.1` 같은 폴더 이름은 그대로 남는다.
     */
    fun bookNameOf(name: String): String = when (MimeResolver.kindOf(name, isDirectory = false)) {
        FileKind.COMIC, FileKind.ARCHIVE -> name.substringBeforeLast('.')
        else -> name
    }
}
