package io.github.donggi.iroiroviewer.docview.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import io.github.donggi.iroiroviewer.docview.pdf.crypt.FileChannelSource
import io.github.donggi.iroiroviewer.docview.pdf.text.PageText
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextEngine
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 열린 PDF 하나.
 *
 * ## `warnings` 와 `unsupported` 가 언제나 비어 있는 것이 이 포맷의 정직한 한계다
 *
 * pdfium 은 **무엇을 못 그렸는지 알려 주지 않는다.** 내용 스트림이 깨진 쪽도 예외 없이
 * 열리고, 렌더도 성공하며, 그냥 빈 쪽이 나온다(표본으로 확인했다). 그러니 '간이 렌더'
 * 배지를 달면 그 배지가 0 을 말하면서 거짓말을 하게 된다 — **배지를 달지 않고 이 한계를
 * 적는 편이 정직하다.** 직접 구현한 EPUB 여는이는 반대다(무엇을 버렸는지 자기가 안다).
 *
 * ## 닫기
 *
 * [close] 는 `AutoCloseable` 이라 `suspend` 가 아니다. 그런데 렌더는 코루틴 안에서 돌고
 * **렌더 도중에 `renderer.close()` 를 부르면 `Current page not closed` 가 나면서 문서가
 * 하나도 닫히지 않는다**. 그래서 닫기를 [IroDispatchers.pdfRender]
 * 에 **얹는다** — 그 디스패처는 병렬도가 1 이라 진행 중인 렌더 뒤에 줄을 서고, 새 렌더는
 * [close] 가 세우는 깃발이 막는다. 결과적으로 `close()` 는 곧바로 돌아오고 실제 닫기는
 * 안전한 순간에 일어난다.
 */
class PdfDocument internal constructor(
    private val renderer: PdfRenderer,
    /** 쪽 수. 문서가 열린 뒤에는 바뀌지 않는다. */
    val pageCount: Int,
    /** 글을 꺼낼 원본(글 고르기). 없으면 이 문서에서는 글을 고를 수 없다. */
    private val textOrigin: PdfTextOrigin? = null,
) : OpenedDocument {

    override val formatId: FormatId = FormatId.PDF

    override val warnings: List<ParseWarning> = emptyList()

    override val unsupported: UnsupportedFeatures = UnsupportedFeatures()

    private val closed = AtomicBoolean(false)

    /**
     * **우리 문서의 불변식**(쪽이 한 번에 하나만 열린다)을 표현한다.
     *
     * 디스패처(병렬도 1)와 하는 일이 다르다 — 그쪽은 pdfium 의 전역 잠금에 맞춘
     * 프로세스 차원의 직렬화이고, 이쪽은 '이 문서의 쪽' 에 대한 우리 약속이다. 둘을
     * 겹쳐 두는 것은 둘이 서로 다른 것을 지키기 때문이다.
     */
    private val lock = Mutex()

    /** 닫기를 얹을 자리. 실제 닫기는 진행 중인 렌더 뒤에 선다. */
    private val closeScope = CoroutineScope(SupervisorJob() + IroDispatchers.pdfRender)

    /** 쪽 크기는 바뀌지 않으므로 한 번 재면 기억한다. 잰 값은 `[폭pt, 높이pt]` 다. */
    private val sizes = HashMap<Int, IntArray>()

    /**
     * 쪽 크기를 포인트로. 닫혔거나 범위 밖이면 null.
     *
     * **`internal` 인 것은 [PdfEngine] 이 `internal` 이기 때문만이 아니다** — 이 문서를
     * 만지는 것은 [PdfPageStore] 하나여야 한다. 예산과 캐시가 거기 있으므로, 다른 곳이
     * 직접 그리면 그 두 가지를 지나지 않는 비트맵이 생긴다.
     */
    internal suspend fun sizeOf(ordinal: Int): IntArray? {
        if (closed.get() || ordinal !in 0 until pageCount) return null
        synchronized(sizes) { sizes[ordinal] }?.let { return it }
        val measured = lock.withLock {
            if (closed.get()) return null
            PdfEngine.pageSize(renderer, ordinal)
        } ?: return null
        synchronized(sizes) { sizes[ordinal] = measured }
        return measured
    }

    /** 쪽 하나를 그린다. 닫혔거나 범위 밖이면 null — **예외로 끝내지 않는다.** */
    internal suspend fun render(ordinal: Int, spec: PdfEngine.Spec): Bitmap? {
        val size = sizeOf(ordinal) ?: return null
        return render(PdfSpreads.Layout(size[0], size[1], listOf(PdfSpreads.Placement(ordinal, 0, 0, size[0], size[1]))), spec)
    }

    /**
     * 가상 쪽 하나(쪽 하나, 또는 두 쪽 보기의 펼침)를 그린다. 닫혔거나 범위 밖의 쪽이 있으면 null — **예외로 끝내지 않는다.**
     */
    internal suspend fun render(layout: PdfSpreads.Layout, spec: PdfEngine.Spec): Bitmap? {
        if (closed.get() || layout.parts.any { it.page !in 0 until pageCount }) return null
        return lock.withLock {
            if (closed.get()) null else PdfEngine.render(renderer, layout, spec)
        }
    }

    /**
     * 쪽 하나에서 글을 찾는다(API 35 이상). 닫혔거나 범위 밖이면 null.
     *
     * 그리기와 **같은 잠금**을 지난다 — 찾기도 쪽을 연다('한 번에 한 쪽'). 문서 전체를 훑는 동안 그리기가 쪽마다 끼어들 수
     * 있게, 잠금은 쪽 하나를 찾는 동안만 쥔다.
     */
    @RequiresApi(35)
    internal suspend fun search(ordinal: Int, query: String): List<PdfSearch.Match>? {
        if (closed.get() || ordinal !in 0 until pageCount) return null
        return lock.withLock {
            if (closed.get()) null else PdfEngine.searchPage(renderer, ordinal, query)
        }
    }

    // ---- 글 고르기 ------------------------------------------------------------------

    /**
     * 글 꺼내기의 상태. **잠금 하나**가 지킨다 — 해석기가 글꼴·객체를 기억해 두고 나눠 쓰므로 한 번에 한 쪽만 꺼낸다.
     * 그리기와는 잠금이 다르다 — pdfium 을 만지지 않는다.
     */
    private val textLock = Mutex()
    private var textEngine: PdfTextEngine? = null
    private var textChannel: FileChannel? = null
    private var textFailed = false

    /** 꺼낸 쪽 몇 장. 같은 쪽에서 다시 길게 누르면 다시 꺼내지 않는다. */
    private val textCache = object : LinkedHashMap<Int, PageText>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PageText>?): Boolean = size > TEXT_CACHE_PAGES
    }

    /** 이 문서에서 글을 고를 수 있는가. 원본이 없으면(API 35 에서 플랫폼이 직접 푼 암호 문서) 없다. */
    internal val canSelectText: Boolean get() = textOrigin != null

    /**
     * 쪽 하나의 글자와 자리. 꺼낼 수 없으면(원본이 없다·구조를 못 읽는다·쪽 수가 플랫폼과 다르다) null.
     *
     * **쪽 수가 플랫폼과 다르면 고르기를 끈다.** 쪽 나무가 망가진 문서는 pdfium 과 우리가 쪽을 다르게 셀 수 있고, 그러면 3쪽을
     * 눌렀는데 4쪽의 글이 골라진다 — 아무것도 고르지 못하는 편이 낫다.
     */
    internal suspend fun pageText(ordinal: Int): PageText? {
        if (closed.get() || textOrigin == null || ordinal !in 0 until pageCount) return null
        return withContext(IroDispatchers.parsing) {
            textLock.withLock {
                if (closed.get() || textFailed) return@withLock null
                textCache[ordinal]?.let { return@withLock it }
                // **쪽 하나에 시간 상한을 건다**(파일당 30초 — `ParseLimits`). 넘으면 이 쪽만 못 고른 것으로 하고 잠금을 놓는다 —
                // 잠금을 쥔 채 몇 시간을 돌면 닫기도 기다린다.
                withTimeoutOrNull(ParseLimits.DEFAULT.openTimeoutMs) {
                    val ctx = currentCoroutineContext()
                    val check = { ctx.ensureActive() }
                    try {
                        val engine = textEngine ?: openText(check) ?: return@withTimeoutOrNull null
                        engine.page(ordinal, check).also { textCache[ordinal] = it }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        Iro.d { "글을 꺼내지 못했다: ${e::class.java.simpleName}" }
                        null
                    } catch (e: RuntimeException) {
                        Iro.d { "글을 꺼내지 못했다: ${e::class.java.simpleName}" }
                        null
                    } catch (e: StackOverflowError) {
                        // 해석기의 깊이 문턱을 비켜 간 문서다. 그 엔진은 다시 쓰지 않는다.
                        dropText()
                        null
                    } catch (e: OutOfMemoryError) {
                        // 엔진과 그 캐시를 함께 놓는다 — 남겨 두면 다음 쪽에서 또 넘친다.
                        dropText()
                        null
                    }
                }
            }
        }
    }

    /** 글 꺼내기를 끈다(이 문서에서는 다시 시도하지 않는다). 잠금 안에서 부른다. */
    private fun dropText() {
        textFailed = true
        textEngine = null
        textCache.clear()
        runCatching { textChannel?.close() }
        textChannel = null
    }

    /**
     * 글 꺼내기를 연다. **실패하면 다시 열지 않는다**(취소는 빼고) — 예전에는 길게 누를 때마다 파일을 새로 열어 xref 를 다시
     * 세웠고, 그때마다 앞 채널이 닫히지 않고 새었다.
     */
    private fun openText(check: () -> Unit): PdfTextEngine? {
        runCatching { textChannel?.close() }
        textChannel = null
        val channel = when (val o = textOrigin) {
            is PdfTextOrigin.OfFile -> FileInputStream(o.file).channel
            // 메모리 파일. 이 스트림은 서술자의 주인이 아니다 — 닫는 것은 [close] 가 서술자를 닫을 때다.
            is PdfTextOrigin.OfDescriptor -> FileInputStream(o.descriptor.fileDescriptor).channel
            null -> return null
        }
        textChannel = channel
        val engine = try {
            PdfTextEngine.open(FileChannelSource(channel), check)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
        if (engine == null || engine.pageCount != pageCount) {
            Iro.d { "글 고르기를 끈다: ${if (engine == null) "열지 못했다(잠긴 원본·망가진 구조)" else "쪽 수 ${engine.pageCount} ≠ $pageCount"}" }
            dropText()
            return null
        }
        textEngine = engine
        return engine
    }

    /**
     * 닫는다. 여러 번 불러도 한 번만 닫는다.
     *
     * **`ParcelFileDescriptor` 를 따로 닫지 않는다** — 생성에 성공했을 때 소유권이
     * [PdfRenderer] 로 넘어갔고, `renderer.close()` 가 그것을 닫는다. (생성에 **실패**
     * 했을 때는 소유권이 넘어가지 않으므로 [PdfEngine.open] 이 그 자리에서 닫는다.)
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeScope.launch {
            runCatching { renderer.close() }
                .onFailure { Iro.d { "PDF 를 닫다 실패: ${it::class.java.simpleName}" } }
            // 글 꺼내기가 도는 중이면 끝나기를 기다린다(닫힘 깃발이 다음 쪽을 막는다).
            textLock.withLock {
                runCatching { textChannel?.close() }
                (textOrigin as? PdfTextOrigin.OfDescriptor)?.let { runCatching { it.descriptor.close() } }
                textEngine = null
                textChannel = null
                textCache.clear()
            }
        }
    }

    private companion object {
        const val TEXT_CACHE_PAGES = 4
    }
}

/** 글을 꺼낼 원본 — pdfium 이 그리는 것과 **같은 바이트**여야 한다. */
internal sealed interface PdfTextOrigin {
    data class OfFile(val file: File) : PdfTextOrigin

    /** 우리가 풀어 둔 평문(메모리 파일)의 복제 서술자. 문서가 주인이고 닫을 때 닫는다. */
    class OfDescriptor(val descriptor: ParcelFileDescriptor) : PdfTextOrigin
}
