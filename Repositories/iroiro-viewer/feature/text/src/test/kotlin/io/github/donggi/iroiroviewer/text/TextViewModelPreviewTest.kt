package io.github.donggi.iroiroviewer.text

import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.data.TextViewerKind
import io.github.donggi.iroiroviewer.format.text.MarkdownPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 뷰모델이 미리보기를 **언제 만들고 언제 놓는가** — 실제 파일로, 화면이 부르는 차례 그대로(`open` → `togglePreview` → 떠나기).
 * 파싱과 디스크는 진짜 디스패처에서 돈다. 그래서 기다림은 시간이 아니라 상태로 한다(`first`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextViewModelPreviewTest {

    private lateinit var root: File

    /** 이 시험이 만든 뷰모델. 끝나면 그 일을 모두 멈추고 기다린다(아래 [tearDown]). */
    private val vms = ArrayList<TextViewModel>()

    private class Store : TextViewerStore {
        override suspend fun load(kind: TextViewerKind): TextViewerDefaults = TextViewerDefaults.defaultsFor(kind)
        override suspend fun save(kind: TextViewerKind, value: TextViewerDefaults) = Unit
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        root = File(System.getProperty("user.dir"), "build/tmp/text-vm-preview-test").absoluteFile
        root.deleteRecursively()
        root.mkdirs()
        File(root, "img").mkdirs()
        File(root, "img/cat.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1))
    }

    /**
     * **뷰모델의 일을 멈추고 끝나기를 기다린 뒤에** 주 디스패처를 되돌린다. 시험에서는 `onCleared` 가 불리지 않아, 디스크·파싱에서
     * 돌던 일이 시험이 끝난 뒤에 주 디스패처로 돌아오다 '주 디스패처가 없다' 로 던진다 — 그 예외를 다음 시험의 `runTest` 가 제 것으로
     * 알린다.
     */
    @AfterTest
    fun tearDown() {
        runBlocking { for (vm in vms) vm.viewModelScope.coroutineContext.job.cancelAndJoin() }
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    private fun opened(name: String, text: String, entry: Long = 1L): TextViewModel = runBlocking {
        val file = File(root, name).apply { writeText(text) }
        val vm = TextViewModel(Store()).also { vms += it }
        vm.open(file.path, entry)
        withTimeout(TIMEOUT) { vm.state.first { it is TextViewModel.State.Ready } }
        vm
    }

    /** 미리보기를 켜고 다 만들어지기를 기다린다. */
    private fun TextViewModel.built(): TextViewModel.Preview.Ready = runBlocking {
        togglePreview()
        val p = withTimeout(TIMEOUT) {
            preview.first { it !is TextViewModel.Preview.Off && it !is TextViewModel.Preview.Building }
        }
        assertIs<TextViewModel.Preview.Ready>(p)
    }

    @Test
    fun `화면을 떠나면 다 만든 미리보기를 놓는다`() {
        val vm = opened("a.md", "# 제목\n\n![고양이](img/cat.png)")
        vm.built()
        // 뷰모델은 액티비티에 묶여 화면을 떠나도 산다 — 쪽을 다음 입장까지 쥐지 않는다.
        vm.onScreenLeft()
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
    }

    @Test
    fun `만드는 도중에 떠나면 뒤늦게 띄우지 않는다`() = runBlocking {
        val vm = opened("big.md", "가나다 **라마** `바사`\n\n".repeat(60_000))
        vm.togglePreview()
        vm.onScreenLeft()
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
        // 취소된 일이 끝나며 `Ready` 를 쓰지 않는다(디스크·파싱은 다른 스레드에서 돈다 — 그 사이를 넉넉히 기다린다).
        delay(1_500)
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
    }

    @Test
    fun `마크다운의 그림은 번호로 싣는다`() {
        val p = opened("b.md", "![고양이](img/cat.png)").built()
        assertEquals(listOf("img/cat.png"), p.images)
        assertEquals(MarkdownPreview.pagePath(1), p.pagePath)
        assertEquals(root.absoluteFile, p.baseDir)
    }

    /** HTML 은 그리지 않는다 — 미리보기를 청해도 아무 일이 없다. 그린 모양은 '브라우저로 열기' 가 보인다. */
    @Test
    fun `HTML 에는 미리보기가 없다`() {
        val vm = opened("c.html", "<p>본문</p><script>x()</script>")
        assertFalse(assertIs<TextViewModel.State.Ready>(vm.state.value).doc.previewable)
        vm.togglePreview()
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
    }

    @Test
    fun `미리보기 상한보다 큰 마크다운은 상한을 말한다`() = runBlocking {
        val vm = opened("huge.md", "a".repeat((MarkdownPreview.MAX_SOURCE_BYTES + 1).toInt()))
        vm.togglePreview()
        assertEquals(TextViewModel.Preview.TooLarge(MarkdownPreview.MAX_SOURCE_BYTES), vm.preview.value)
    }

    @Test
    fun `회전은 떠나는 것이 아니다`() {
        assertFalse(leavesScreen(changingConfigurations = true))
        assertTrue(leavesScreen(changingConfigurations = false))
        // 액티비티를 모르면 떠난 것으로 본다 — 쥐고 있는 쪽이 틀리는 것보다 낫다.
        assertTrue(leavesScreen(changingConfigurations = null))
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
