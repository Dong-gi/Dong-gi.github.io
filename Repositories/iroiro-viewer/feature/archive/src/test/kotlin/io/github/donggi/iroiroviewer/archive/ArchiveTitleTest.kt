package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.archive.ArchiveKind
import io.github.donggi.iroiroviewer.format.archive.ArchiveTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 압축 화면 위쪽 막대의 제목([ArchiveTitle]). 목록이 없는 화면에도 어느 압축 파일인지 적는다. */
class ArchiveTitleTest {

    private val path = "/storage/emulated/0/Download/사진 모음 (2026).7z"

    @Test
    fun 목록이_없는_화면은_파일_이름이다() {
        val states = listOf(
            ArchiveViewModel.State.Loading(),
            ArchiveViewModel.State.Loading(reached = true),
            ArchiveViewModel.State.NeedsPassword,
        ) + ArchiveViewModel.State.Failed.Kind.entries.map { ArchiveViewModel.State.Failed(it) }
        for (state in states) assertEquals("사진 모음 (2026).7z", ArchiveTitle.of(state, folder = "", path = path), state.toString())
    }

    @Test
    fun 목록이_열려_있으면_전과_같다() {
        val ready = ArchiveViewModel.State.Ready(doc())
        // 맨 위 — 압축 파일 이름.
        assertEquals("a.zip", ArchiveTitle.of(ready, folder = "", path = path))
        // 안의 폴더 — 그 폴더의 이름(경로가 아니다).
        assertEquals("2026", ArchiveTitle.of(ready, folder = "사진/2026", path = path))
        assertEquals("사진", ArchiveTitle.of(ready, folder = "사진", path = path))
    }

    @Test
    fun 전체_경로를_적지_않는다() {
        assertFalse('/' in ArchiveTitle.of(ArchiveViewModel.State.NeedsPassword, folder = "", path = path))
        assertEquals("a.zip", ArchiveTitle.fileNameOf("/storage/emulated/0/a.zip"))
        // 끝의 빗금은 떼고 자른다(`File` 의 정규화와 같다). 빗금이 없으면 그대로다.
        assertEquals("a.zip", ArchiveTitle.fileNameOf("/storage/emulated/0/a.zip/"))
        assertEquals("a.zip", ArchiveTitle.fileNameOf("a.zip"))
        // 안드로이드에서 역슬래시는 이름의 글자다 — 윈도의 구분자로 읽지 않는다.
        assertEquals("a\\b.zip", ArchiveTitle.fileNameOf("/storage/emulated/0/a\\b.zip"))
        assertEquals("", ArchiveTitle.fileNameOf(""))
    }

    private fun doc() = ArchiveViewModel.Doc(
        path = "/storage/emulated/0/a.zip",
        name = "a.zip",
        fileBytes = 22,
        kind = ArchiveKind.ZIP,
        tree = ArchiveTree.build(emptyList()),
        entries = emptyList(),
        solid = false,
    )
}
