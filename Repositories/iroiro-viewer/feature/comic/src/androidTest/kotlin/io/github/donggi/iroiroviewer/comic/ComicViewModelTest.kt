package io.github.donggi.iroiroviewer.comic

import android.app.Application
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.io.FileKey
import io.github.donggi.iroiroviewer.safety.ComicLimits
import io.github.donggi.iroiroviewer.safety.ImageLimits
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 읽는 방향과 이어보기가 **DB 를 왕복해도 살아남는가.**
 *
 * `ComicViewModel` 은 Room 을 직접 부르므로 JVM 시험이 닿지 못한다. 그래서 여기에 둔다.
 *
 * ## 이 파일이 생긴 이유
 *
 * 9단계 뒤 감사에서, **일반 압축(.zip·.7z·.rar)으로 묶은 만화는 읽는 방향을 기억하지
 * 못한다**는 것이 나왔다. 압축 목록에서 그림 항목을 탭해 들어오는 길
 * (`startEntryIndex >= 0`)이 `key` 만 채우고 `restore` 를 건너뛰었기 때문이다. 방향이
 * 기본값(LTR)인 채로 열리고, 화면을 나갈 때 `close()` 가 그 기본값을 DB 에 덮어썼다.
 *
 * cbz·cbr 은 목록에서 곧바로 뷰어로 가므로 이 길에 닿지 않는다. 그러나 일반 zip 으로
 * 묶은 만화는 **이 길이 유일한 입구**라, 오른쪽에서 왼쪽을 골라도 다음에 열면 매번
 * 왼쪽에서 오른쪽이었다. 화면에는 아무 오류도 나지 않는다 — 방향만 조용히 되돌아간다.
 */
class ComicViewModelTest {

    private val app = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as Application
    private lateinit var work: File

    @Before
    fun setUp() {
        work = File(app.getExternalFilesDir(null), "comic-vm-${System.nanoTime()}")
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun png(color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    /** 이름을 `.zip` 으로 둔다 — 걸리는 경로가 바로 그 파일들이다. */
    private fun zipOf(name: String, pages: Int): File {
        val f = File(work, name)
        ZipArchiveOutputStream(f).use { z ->
            repeat(pages) { i ->
                val b = png(0xFF000000.toInt() or (i * 40 shl 8))
                val e = ZipArchiveEntry("%02d.png".format(i + 1))
                e.size = b.size.toLong()
                z.putArchiveEntry(e)
                z.write(b)
                z.closeArchiveEntry()
            }
            z.finish()
        }
        return f
    }

    private fun newVm(): ComicViewModel = ComicViewModel(app).also { it.onViewport(1080, 2400) }

    /** `open` 은 코루틴을 띄운다. 시험 스레드는 결과를 기다려야 한다. */
    private fun awaitReady(vm: ComicViewModel) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (vm.state.value is ComicViewModel.State.Ready) return
            Thread.sleep(20)
        }
        throw AssertionError("책이 열리지 않았다: ${vm.state.value}")
    }

    /** `close` 는 저장을 코루틴에 맡긴다. 값이 실제로 DB 에 닿을 때까지 기다린다. */
    private fun awaitSaved(file: File, expect: ComicViewModel.Direction) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (savedDirection(file) == expect.code) return
            Thread.sleep(20)
        }
        throw AssertionError("저장된 방향이 ${expect}가 아니다: ${savedDirection(file)}")
    }

    private fun savedDirection(file: File): Int? = runBlocking {
        val key = FileKey.of(file.name, file.length(), file.lastModified())
        IroiroDatabase.get(app).comicProgress().find(key)?.readDirection
    }

    /** 이 zip 의 `ordinal` 번째 쪽이 몇 번 엔트리인가. 압축 화면이 넘기는 값이 이것이다. */
    private fun entryIndexOf(file: File, ordinal: Int): Int = runBlocking {
        val budget = ImageLimits.budgetOf(1080, 2400, 192)
        val result = ComicOpen.open(file.path, ComicLimits.windowBytes(budget))
        check(result is ComicOpen.Result.Ready) { "열리지 않았다: $result" }
        result.source.use { it.pages[ordinal].entryIndex }
    }

    /**
     * **이 시험이 이 파일의 이유다.**
     *
     * 오른쪽에서 왼쪽으로 읽던 책을, 압축 목록에서 그림을 탭해 다시 연다.
     * 방향이 살아 있어야 하고, 그 자리에서 나가도 DB 의 값이 뒤집히지 않아야 한다.
     */
    @Test
    fun 압축_목록에서_연_만화도_읽는_방향을_되살린다() {
        val f = zipOf("만화.zip", pages = 4)

        // ① 한 번 읽고 오른쪽에서 왼쪽으로 바꾼 뒤 나간다.
        val first = newVm()
        first.open(f.path)
        awaitReady(first)
        first.setDirection(ComicViewModel.Direction.RTL)
        first.close()
        awaitSaved(f, ComicViewModel.Direction.RTL)

        // ② 압축 목록에서 3쪽 그림을 탭해 들어온다.
        val entry = entryIndexOf(f, ordinal = 2)
        val second = newVm()
        second.open(f.path, startEntryIndex = entry)
        awaitReady(second)

        assertEquals(
            "읽는 방향이 되살아나야 한다",
            ComicViewModel.Direction.RTL,
            second.direction.value,
        )
        assertEquals("엔트리로 지정한 쪽으로 가야 한다", 2, second.page.value)

        // ③ 그 자리에서 나가도 저장된 방향이 뒤집히지 않는다. 고치기 전에는 여기서
        //    LTR(0)로 덮어써졌고, 그 뒤로는 무엇을 해도 왼쪽에서 오른쪽이었다.
        second.close()
        Thread.sleep(300)
        assertEquals(
            "나갔다고 방향이 기본값으로 덮어써지면 안 된다",
            ComicViewModel.Direction.RTL.code,
            savedDirection(f),
        )
    }

    /** 이어보기는 **엔트리를 지정하지 않았을 때만** 쪽을 되살린다. */
    @Test
    fun 엔트리를_지정하면_이어보기_쪽을_쓰지_않는다() {
        val f = zipOf("이어보기.zip", pages = 6)

        val first = newVm()
        first.open(f.path)
        awaitReady(first)
        first.onPageChanged(4)
        first.close()
        awaitSaved(f, ComicViewModel.Direction.LTR)

        // 엔트리를 지정하지 않으면 4쪽으로 돌아온다.
        val resumed = newVm()
        resumed.open(f.path)
        awaitReady(resumed)
        assertEquals(4, resumed.page.value)
        resumed.close()

        // 지정하면 그 쪽이 이긴다.
        val jumped = newVm()
        jumped.open(f.path, startEntryIndex = entryIndexOf(f, ordinal = 1))
        awaitReady(jumped)
        assertEquals(1, jumped.page.value)
        assertNotEquals("이어보기 쪽이 이기면 안 된다", 4, jumped.page.value)
        jumped.close()
    }

    /** 책을 바꾸면 앞 책의 방향이 따라오지 않는다(9단계가 기기에서 잡은 결함). */
    @Test
    fun 책을_바꾸면_앞_책의_방향이_따라오지_않는다() {
        val webtoon = zipOf("웹툰.zip", pages = 3)
        val manga = zipOf("만화2.zip", pages = 3)

        val vm = newVm()
        vm.open(webtoon.path)
        awaitReady(vm)
        vm.setDirection(ComicViewModel.Direction.VERTICAL)
        vm.close()
        awaitSaved(webtoon, ComicViewModel.Direction.VERTICAL)

        vm.open(webtoon.path)
        awaitReady(vm)
        assertEquals(ComicViewModel.Direction.VERTICAL, vm.direction.value)

        vm.open(manga.path)
        awaitReady(vm)
        assertEquals(
            "기록이 없는 책은 기본값으로 열린다",
            ComicViewModel.Direction.LTR,
            vm.direction.value,
        )
    }
}
