package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.model.PathNames

/**
 * 위쪽 막대의 제목. 판단을 여기 모아 JVM 에서 시험한다(`DocTitleTest`).
 *
 * * **열린 문서는 문서가 준 이름이다**([DocViewModel.Doc.name]) — EPUB·흐름 문서는 메타데이터의 제목, 없으면 파일
 *   이름이고 PDF 는 파일 이름이다. 14단계까지의 모양 그대로다.
 * * **여는 중·실패 화면은 화면이 받은 경로의 파일 이름이다.** 전에는 비어 있었다 — 무엇을 열다 실패했는지 모른 채
 *   '이 앱이 다루지 않는 문서입니다' 만 보였고, 그 화면에서 '다른 앱으로 열기' 를 누르는 사람은 무엇이 넘어가는지도
 *   몰랐다. 이미지 뷰어는 처음부터 파일 이름을 적는다.
 *
 * **전체 경로를 적지 않는다.** 막대 한 줄에 들지 않고, 캡처·화면 공유로 폴더 구조가 새어 나간다.
 */
internal object DocTitle {

    fun of(state: DocViewModel.State, path: String): String = when (state) {
        is DocViewModel.State.Ready -> state.doc.name
        is DocViewModel.State.Loading, is DocViewModel.State.Failed -> fileNameOf(path)
    }

    /** 경로의 마지막 조각 — 판단은 [PathNames.lastSegment] 한 벌이다(뷰어 넷이 같은 답을 낸다). */
    fun fileNameOf(path: String): String = PathNames.lastSegment(path)
}
