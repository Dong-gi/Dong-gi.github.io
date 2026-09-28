package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.model.PathNames

/**
 * 위쪽 막대의 제목. 판단을 여기 모아 JVM 에서 시험한다(`TextTitleTest`).
 *
 * * **다 읽은 파일은 그 이름이다**([TextViewModel.Doc.name]) — 14단계까지의 모양 그대로다.
 * * **여는 중·색인 중·실패 화면은 화면이 받은 경로의 파일 이름이다.** 전에는 비어 있었다 — '글이 아닌 파일로 보입니다' 만
 *   보이고 무엇을 열다 그렇게 됐는지 적혀 있지 않았다. 200 MB 로그를 색인하는 몇 초 동안에도 무엇을 기다리는지 안다.
 *
 * **전체 경로를 적지 않는다.** 막대 한 줄에 들지 않고, 캡처·화면 공유로 폴더 구조가 새어 나간다.
 */
internal object TextTitle {

    fun of(state: TextViewModel.State, path: String): String = when (state) {
        is TextViewModel.State.Ready -> state.doc.name
        is TextViewModel.State.Loading, is TextViewModel.State.Indexing, is TextViewModel.State.Failed -> fileNameOf(path)
    }

    /** 경로의 마지막 조각 — 판단은 [PathNames.lastSegment] 한 벌이다(뷰어 넷이 같은 답을 낸다). */
    fun fileNameOf(path: String): String = PathNames.lastSegment(path)
}
