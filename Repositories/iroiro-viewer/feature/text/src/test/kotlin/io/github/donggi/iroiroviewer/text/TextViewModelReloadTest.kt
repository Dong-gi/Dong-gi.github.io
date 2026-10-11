package io.github.donggi.iroiroviewer.text

import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.data.TextViewerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * 편집기가 저장한 뒤 텍스트 뷰어가 **새 글을 읽는가**. 뷰어의 뷰모델은 같은 경로를 다시 열면 옛 색인을 그대로 쓴다 — 회전과
 * 되돌아오기에는 그것이 맞지만, 편집기가 저장한 뒤에는 옛 글의 자리로 새 글을 읽게 된다. 편집기가 남긴 표시([TextSaves])가 있을 때만
 * 다시 읽는다. 그리고 그 파일의 '편집' 단추 — 저장 볼륨 밖(이 시험의 임시 폴더)이라 숨는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextViewModelReloadTest {

    private lateinit var root: File
    private val vms = ArrayList<TextViewModel>()

    private class Store : TextViewerStore {
        override suspend fun load(kind: TextViewerKind): TextViewerDefaults = TextViewerDefaults.defaultsFor(kind)
        override suspend fun save(kind: TextViewerKind, value: TextViewerDefaults) = Unit
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        root = File(System.getProperty("user.dir"), "build/tmp/text-vm-reload-test").absoluteFile
        root.deleteRecursively()
        root.mkdirs()
    }

    /** 뷰모델의 일을 멈추고 끝나기를 기다린 뒤에 주 디스패처를 되돌린다(`TextViewModelPreviewTest` 의 같은 까닭). */
    @AfterTest
    fun tearDown() {
        runBlocking { for (vm in vms) vm.viewModelScope.coroutineContext.job.cancelAndJoin() }
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    @Test
    fun `저장 표시가 있을 때만 같은 경로를 다시 읽는다`() = runBlocking {
        val file = File(root, "memo.txt").apply { writeText("첫 줄\n") }
        val vm = TextViewModel(Store()).also { vms += it }
        vm.open(file.path, 1L)
        val first = withTimeout(TIMEOUT) { vm.state.first { it is TextViewModel.State.Ready } }

        // 밖에서 바뀌었지만 표시가 없다 — 되돌아온 것(새 입장)이라도 옛 색인 그대로다(지금까지의 동작).
        file.writeText("첫 줄\n둘째 줄\n셋째 줄\n")
        vm.open(file.path, 2L)
        assertSame(first, vm.state.value)

        // 편집기가 저장했다 — 새로 읽는다.
        TextSaves.record(file.path, TextEncoding.UTF_8)
        vm.open(file.path, 3L)
        val second = withTimeout(TIMEOUT) {
            vm.state.first { it is TextViewModel.State.Ready && it !== first }
        } as TextViewModel.State.Ready
        assertEquals(file.length(), second.doc.sizeBytes)
        // 표시는 한 번 쓰고 사라진다 — 다음 입장은 다시 옛 색인 그대로다.
        vm.open(file.path, 4L)
        assertSame(second, vm.state.value)
        // 저장 볼륨 밖이라 편집 단추가 없다.
        assertEquals(TextEditGate.Hidden, vm.editGate.value)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
