package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.model.PathNames

/**
 * 위쪽 막대의 제목. 판단을 여기 모아 JVM 에서 시험한다(`ArchiveTitleTest`).
 *
 * * **목록이 열려 있으면 14단계까지의 모양 그대로다** — 아카이브 안의 폴더에 들어가 있으면 그 폴더 이름, 맨 위면
 *   압축 파일 이름([ArchiveViewModel.Doc.name]).
 * * **읽는 중·암호를 묻는 중·실패 화면은 화면이 받은 경로의 파일 이름이다.** 전에는 비어 있었다 — '암호를 넣어야 열 수
 *   있는 압축 파일입니다' 만 보이고 어느 압축 파일의 암호인지 적혀 있지 않았다. 폴더 이름은 목록이 없으면 뜻이 없다
 *   (읽기를 시작할 때 맨 위로 되돌린다).
 *
 * **전체 경로를 적지 않는다.** 막대 한 줄에 들지 않고, 캡처·화면 공유로 폴더 구조가 새어 나간다.
 */
internal object ArchiveTitle {

    /** [folder] 는 아카이브 안에서 지금 보는 폴더(빈 글이 맨 위), [path] 는 화면이 받은 압축 파일의 경로다. */
    fun of(state: ArchiveViewModel.State, folder: String, path: String): String = when (state) {
        is ArchiveViewModel.State.Ready -> folder.substringAfterLast('/').ifEmpty { state.doc.name }
        is ArchiveViewModel.State.Loading,
        ArchiveViewModel.State.NeedsPassword,
        is ArchiveViewModel.State.Failed,
        -> fileNameOf(path)
    }

    /** 경로의 마지막 조각 — 판단은 [PathNames.lastSegment] 한 벌이다(뷰어 넷이 같은 답을 낸다). */
    fun fileNameOf(path: String): String = PathNames.lastSegment(path)
}
