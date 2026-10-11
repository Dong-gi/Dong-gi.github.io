package io.github.donggi.iroiroviewer.text

import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.data.TextViewerKind
import io.github.donggi.iroiroviewer.format.text.HtmlPreview
import io.github.donggi.iroiroviewer.format.text.LivePage
import io.github.donggi.iroiroviewer.format.text.PreviewAsset
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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
        File(root, "s.css").writeText("p{color:red;background:url(img/cat.png)}")
    }

    /**
     * **뷰모델의 일을 멈추고 끝나기를 기다린 뒤에** 주 디스패처를 되돌린다. 시험에서는 `onCleared` 가 불리지 않아, 디스크·파싱에서
     * 돌던 일이 시험이 끝난 뒤에 주 디스패처로 돌아오다 '주 디스패처가 없다' 로 던진다 — 그 예외를 다음 시험의 `runTest` 가 제 것으로
     * 알린다(되돌림 확인에서 `ViewerSettingsTest` 하나가 그렇게 깨졌다).
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
        // 뷰모델은 액티비티에 묶여 화면을 떠나도 산다 — 쪽(큰 HTML 이면 수십 MB)을 다음 입장까지 쥐지 않는다.
        vm.onScreenLeft()
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
    }

    @Test
    fun `만드는 도중에 떠나면 뒤늦게 띄우지 않는다`() = runBlocking {
        val vm = opened("big.html", "<p>가나다</p>".repeat(200_000))
        vm.togglePreview()
        vm.onScreenLeft()
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
        // 취소된 일이 끝나며 `Ready` 를 쓰지 않는다(디스크·파싱은 다른 스레드에서 돈다 — 그 사이를 넉넉히 기다린다).
        delay(1_500)
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
    }

    @Test
    fun `마크다운의 그림은 마크다운의 표로 싣는다`() {
        val p = opened("b.md", "![고양이](img/cat.png)").built()
        assertEquals(PreviewKind.MARKDOWN, p.kind)
        val disk = p.assets.filterIsInstance<PreviewAsset.Disk>().single()
        assertEquals("img/cat.png", disk.path)
        // HTML 의 표(`IMAGE`)가 아니다 — 마크다운의 동작은 HTML 미리보기를 세우며 바뀌지 않아야 했다.
        assertEquals(PreviewAsset.Use.MARKDOWN, disk.use)
    }

    @Test
    fun `HTML 은 스타일시트까지 읽어 한 쪽으로`() {
        val p = opened("c.html", "<link rel=\"stylesheet\" href=\"s.css\"><p>본문</p><script>x()</script>").built()
        assertEquals(PreviewKind.HTML, p.kind)
        val html = String(p.html, Charsets.UTF_8)
        assertTrue("<p>본문</p>" in html && "<script" !in html, html)
        assertTrue(p.withheld)
        val sheet = assertNotNull(p.assets.filterIsInstance<PreviewAsset.Sheet>().single().css)
        val css = String(sheet, Charsets.UTF_8)
        assertTrue("color:red" in css && "/r/" in css && "cat.png" !in css, css)
        assertEquals(listOf("img/cat.png"), p.assets.filterIsInstance<PreviewAsset.Disk>().map { it.path })
        assertEquals(HtmlPreview.pagePath(1), p.pagePath)
    }

    private fun TextViewModel.awaitPreview(match: (TextViewModel.Preview.Ready) -> Boolean): TextViewModel.Preview.Ready =
        runBlocking {
            withTimeout(TIMEOUT) {
                preview.first { it is TextViewModel.Preview.Ready && match(it) }
            } as TextViewModel.Preview.Ready
        }

    @Test
    fun `스크립트 실행은 파일 그대로, 열 때마다 새 호스트에서`() {
        val vm = opened("m.html", "<meta charset=\"utf-8\"><p>본문</p><script>x()</script>")
        val safe = vm.built()
        assertTrue(safe.scripted)
        assertNull(safe.live)
        vm.runScripts()
        val first = vm.awaitPreview { it.live != null }
        val live = assertNotNull(first.live)
        assertEquals(LivePage.PAGE_PATH, first.pagePath)
        assertEquals(0, first.html.size)
        assertTrue(live.host.endsWith(".live.iroiro.invalid"), live.host)
        // 문서가 인코딩을 말했다 — 브라우저에 맡긴다.
        assertNull(live.encoding)
        assertEquals(File(root, "m.html").length(), live.length)
        // 멈추면 위생을 거친 쪽으로, 다시 실행하면 새 호스트다 — 앞 실행이 남긴 것을 읽을 수 없다.
        vm.stopScripts()
        vm.awaitPreview { it.live == null }
        vm.runScripts()
        val second = assertNotNull(vm.awaitPreview { it.live != null }.live)
        assertNotEquals(live.host, second.host)
    }

    @Test
    fun `선언 없는 옛 한국어 쪽에는 우리 판정을 준다`() = runBlocking {
        val file = File(root, "old.html")
        val text = "<html><body><p>" + "한국어로 쓴 옛 문서입니다. ".repeat(40) + "</p><script>x()</script></body></html>"
        file.writeBytes(text.toByteArray(charset("x-windows-949")))
        val vm = TextViewModel(Store()).also { vms += it }
        vm.open(file.path, 1L)
        val ready = withTimeout(TIMEOUT) { vm.state.first { it is TextViewModel.State.Ready } } as TextViewModel.State.Ready
        assertEquals(TextEncoding.CP949, ready.doc.encoding)
        vm.built()
        vm.runScripts()
        // 브라우저의 기본값(UTF-8)으로 두면 글이 깨진다 — 우리 판정을 브라우저가 아는 이름으로 준다.
        assertEquals("euc-kr", vm.awaitPreview { it.live != null }.live?.encoding)
    }

    @Test
    fun `손으로 고른 인코딩은 문서의 선언보다 앞선다`() = runBlocking {
        val vm = opened("d.html", "<meta charset=\"euc-kr\"><p>abc</p><script>x()</script>")
        vm.built()
        vm.chooseEncoding(TextEncoding.SHIFT_JIS)
        withTimeout(TIMEOUT) {
            vm.state.first { (it as? TextViewModel.State.Ready)?.doc?.encoding == TextEncoding.SHIFT_JIS }
        }
        vm.awaitPreview { it.live == null }
        vm.runScripts()
        assertEquals("shift_jis", vm.awaitPreview { it.live != null }.live?.encoding)
    }

    @Test
    fun `미리보기를 닫았다 열면 스크립트 실행은 내려가 있다`() {
        val vm = opened("e.html", "<p>본문</p><script>x()</script>")
        vm.built()
        vm.runScripts()
        vm.awaitPreview { it.live != null }
        vm.togglePreview()
        assertEquals(TextViewModel.Preview.Off, vm.preview.value)
        // 고른 것은 저장하지 않는다 — 다시 열면 위생을 거친 쪽부터다.
        assertNull(vm.built().live)
    }

    @Test
    fun `위생 쪽에 너무 큰 HTML 도 스크립트로는 연다`() {
        // 첨부를 품은 보안 메일은 8 MiB 를 넘을 수 있다 — 위생을 거친 쪽이 '너무 크다' 로 끝나도 그 자리에서 스크립트로 열 수 있어야 한다.
        val big = "<p>본문</p><script>x()</script>" + "<!--" + "a".repeat(9 * 1024 * 1024) + "-->"
        val vm = opened("big-mail.html", big)
        vm.togglePreview()
        val tooLarge = runBlocking {
            withTimeout(TIMEOUT) { vm.preview.first { it is TextViewModel.Preview.TooLarge } }
        } as TextViewModel.Preview.TooLarge
        assertFalse(tooLarge.live)
        vm.runScripts()
        val live = assertNotNull(vm.awaitPreview { it.live != null }.live)
        assertEquals(File(root, "big-mail.html").length(), live.length)
    }

    @Test
    fun `마크다운에는 스크립트 실행이 없다`() {
        val vm = opened("g.md", "# 제목")
        val p = vm.built()
        vm.runScripts()
        assertSame(p, vm.preview.value)
    }

    /** 이 앱은 인터넷에 닿지 않는다 — HTML 미리보기는 묻지 않고 곧바로 위생을 거친 쪽이다. 바깥 그림은 버리고 말한다. */
    @Test
    fun `HTML 미리보기는 묻지 않고 위생을 거친 쪽을 띄운다`() {
        val vm = opened("n1.html", "<p>본문</p><img src=\"https://example.com/a.png\">")
        vm.togglePreview()
        val p = vm.awaitPreview { true }
        assertNull(p.live)
        assertTrue(p.withheld)
        assertFalse("example.com" in String(p.html, Charsets.UTF_8))
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
