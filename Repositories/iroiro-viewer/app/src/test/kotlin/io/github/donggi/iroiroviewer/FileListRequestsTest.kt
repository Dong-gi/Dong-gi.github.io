package io.github.donggi.iroiroviewer

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 재생 화면이 뒤로 가며 연 파일 목록에 건네는 폴더([FileListRequests]). 요청은 값이 아니라 **도착**이다 — 같은 폴더를 두 번 청해도
 * 두 번 와야 하고, 꺼낸 뒤에 새로 온 것은 남아야 한다.
 */
class FileListRequestsTest {

    @AfterTest
    fun cleanUp() {
        FileListRequests.post(null)
    }

    @Test
    fun `넣은 폴더가 기다리고 꺼내면 비워진다`() {
        FileListRequests.post("/storage/emulated/0/Download")
        val request = FileListRequests.pending.value
        assertEquals("/storage/emulated/0/Download", request?.folder)
        FileListRequests.consume(request!!)
        assertNull(FileListRequests.pending.value)
    }

    @Test
    fun `같은 폴더를 다시 청하면 새 요청으로 온다`() {
        FileListRequests.post("/storage/emulated/0/Movies")
        val first = FileListRequests.pending.value
        FileListRequests.post("/storage/emulated/0/Movies")
        // 같은 값을 합치는 흐름(`MutableStateFlow`)이라도 새 요청은 새로 낸다 — 값이 같아 합쳐지면 두 번째가 오지 않는다.
        assertNotSame(first, FileListRequests.pending.value)
    }

    @Test
    fun `꺼내기 전에 새로 온 요청은 옛 요청을 꺼내도 남는다`() {
        FileListRequests.post("/storage/emulated/0/A")
        val old = FileListRequests.pending.value!!
        FileListRequests.post("/storage/emulated/0/B")
        val fresh = FileListRequests.pending.value
        FileListRequests.consume(old)
        assertSame(fresh, FileListRequests.pending.value)
    }

    @Test
    fun `첫 화면을 청하면 남은 폴더를 비운다`() {
        FileListRequests.post("/storage/emulated/0/A")
        FileListRequests.post(null)
        assertNull(FileListRequests.pending.value)
    }
}
