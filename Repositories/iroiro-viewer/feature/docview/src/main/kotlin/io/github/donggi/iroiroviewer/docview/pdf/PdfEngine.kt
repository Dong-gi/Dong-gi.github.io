package io.github.donggi.iroiroviewer.docview.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * `android.graphics.pdf` 를 만지는 **유일한 파일.**
 *
 * [PdfRenderer] 와 `PdfRenderer.Page` 가 이 파일 밖으로 나가지 않는다. 밖으로 나가는 것은
 * 값(크기·비트맵)뿐이라, 나중에 렌더를 별도 프로세스로 밀어낼 때 경계를 이 함수들 자리에
 * 그대로 끼워 넣을 수 있다 — CLAUDE.md 안전 절이 '렌더 인터페이스는 처음부터 프로세스를
 * 넘길 수 있는 모양으로 짜라' 고 적어 둔 그것이고, AOSP 의 [PdfRenderer] 생성자 javadoc
 * 자신이 신뢰할 수 없는 파일은 격리 프로세스를 권한다. **격리 프로세스는 11단계에 만들지
 * 않는다** — 6MB 비트맵을 Binder 로 돌려받는 길(트랜잭션 1MB 한계, SharedMemory)을 한 번도
 * 시험하지 못했고, 확인하지 못한 구조를 넣지 않는 것이 이 저장소의 규칙이다.
 *
 * ## 왜 3분기가 없는가
 *
 * CLAUDE.md 실측표가 오래 '11단계의 3분기(플랫폼 35+ / `PdfRendererPreV`(확장 13+) /
 * 레거시)' 를 예고했지만, 조사 끝에 **분기를 만들지 않기로 했다.**
 *
 * * `android-36.1/data/api-versions.xml` 에서 우리가 쓰는 API 가 전부 `since="21"` 이고
 *   `sdks` 속성이 **없다** — 확장과 무관하게 모든 기기에 있다. 생성자(PFD)·`getPageCount`·
 *   `openPage`·`Page.getWidth/getHeight`·`render(Bitmap, Rect, Matrix, Int)` 가 그렇다.
 * * 확장 13+ 가 내려 주는 `PdfRendererPreV`·`LoadParams`·`RenderParams` 가 더하는 것은
 *   **암호·폼·텍스트 선택·검색·링크**다. 폼 이하는 범위 밖이고, 남는 일이 '쪽을 비트맵에
 *   그리기' 하나라 레거시 한 벌이 minSdk 31 부터 compileSdk 37 까지 전부를 덮는다.
 * * 그리고 **확장 13 이상 기기가 이 저장소에 없다**(에뮬레이터 R=1·S=1). 분기를 만들면
 *   두 갈래를 한 줄도 실행해 보지 못한 채 두게 된다 — '에뮬레이터로 검증되지 않는 것은
 *   문서에 남긴다' 가 여기서는 **'코드를 쓰지 않는다'** 로 읽히는 것이 맞다.
 *
 * ## 암호에만 분기가 하나 있다
 *
 * 암호 PDF 를 열기로 하면서(11단계 뒤) 분기가 **하나** 생겼다. 암호를 받는 생성자
 * `PdfRenderer(pfd, LoadParams)` 가 **API 35 의 프레임워크**에만 있기 때문이다
 * (`api-versions.xml`: `since="35"`, 그 생성자에는 `sdks` 가 없다). 확장 13 의
 * `PdfRendererPreV` 는 여전히 쓰지 않는다 — 위 이유 그대로다.
 *
 * * **API 35 이상** — [openWithPassword]. pdfium 이 스스로 푼다. 평문 사본이 없다.
 * * **그 아래** — 우리가 풀어(`crypt/PdfDecryptor`) 메모리 파일에 평문 한 벌을 만들고
 *   [open] 의 서술자 판으로 연다. 두 갈래 **모두 에뮬레이터에서 돈다**(Android_12_Phone ·
 *   Android_15_Tablet) — 위에서 분기를 거절한 이유가 여기서는 성립하지 않는다.
 *
 * 나중에 텍스트 선택·검색을 넣는 사람에게: `PdfRendererPreV` 의 인자 순서를 **javadoc 이
 * 아니라 javap 로** 확인하라. android-36.1 의 javadoc 예제가 `page.render(bitmap, params,
 * null, null)` 인데 실제 시그니처는 `render(Bitmap, Rect, Matrix, RenderParams)` 다.
 *
 * ## 찾기도 API 35 의 프레임워크에만 있다(14단계)
 *
 * `PdfRenderer.Page.searchText(String)` 은 `api-versions.xml` 에 `since="35"` 이고 그 메서드에는 `sdks` 가 없다 — 확장 13
 * 이 아니라 **프레임워크 35** 다(암호 생성자와 같은 사정). 그래서 [searchPage] 에 `@RequiresApi(35)` 를 달고, 부르는 쪽은
 * `SDK_INT` 로 가른다. 34 이하 기기에서는 찾기 단추가 없다(`PdfSearch` 의 주석).
 */
internal object PdfEngine {

    /**
     * 렌더 한 번에 필요한 전부. **모두 값이다** — 나중에 프로세스 경계를 넘길 수 있다.
     *
     * [srcLeft]·[srcTop] 은 쪽 전체를 [scale] 로 그렸다고 쳤을 때의 화소 좌표다.
     * 타일이 아니면 둘 다 0 이다.
     */
    data class Spec(
        val bitmapWidth: Int,
        val bitmapHeight: Int,
        val scale: Double,
        val srcLeft: Int = 0,
        val srcTop: Int = 0,
    )

    /** 연 결과. 예외를 밖으로 내보내지 않고 [error] 로 옮겨 [PdfFailures] 가 가리게 한다. */
    sealed interface Opened {
        data class Ok(val renderer: PdfRenderer, val pageCount: Int) : Opened
        data class Error(val cause: Throwable) : Opened
    }

    /**
     * 파일을 연다. **실패하면 우리가 연 [ParcelFileDescriptor] 를 우리가 닫는다.**
     *
     * 소유권은 **성공했을 때만** 넘어간다 — [PdfRenderer] 의 생성자가 던지면 그 PFD 는
     * 닫히지 않은 채 남는다(설계 검토가 치명으로 잡았다). 그리고 이 앱에서 실패는 드문
     * 길이 아니다: 암호 PDF·깨진 PDF·PDF 아닌 파일이 전부 생성자에서 던진다.
     */
    suspend fun open(file: File): Opened = openWith(
        { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) },
    ) { PdfRenderer(it) }

    /**
     * 이미 연 서술자로 연다(암호를 우리가 푼 메모리 파일). **소유권을 받는다** — 성공하면
     * `renderer.close()` 가, 실패하면 이 함수가 닫는다.
     */
    suspend fun open(descriptor: ParcelFileDescriptor): Opened {
        // **소유권은 블록이 실제로 돌 때 넘어간다.** 렌더 디스패처에 줄을 서 있는 동안 취소되면
        // 블록이 아예 돌지 않아(`withContext` 가 들어가기 전에 취소를 본다) `acquire` 가 불리지
        // 않고, 그러면 아무도 서술자를 닫지 않아 평문이 든 메모리 파일이 GC 를 기다린다(적대적
        // 검토가 잡았다). 받지 않았으면 여기서 닫는다.
        var taken = false
        try {
            return openWith({ taken = true; descriptor }) { PdfRenderer(it) }
        } finally {
            if (!taken) runCatching { descriptor.close() }
        }
    }

    /**
     * 암호를 넣어 연다. **API 35 의 프레임워크에만 있는 생성자다.**
     *
     * 암호가 틀려도 암호가 없을 때와 같은 `SecurityException` 이 온다 — 부르는 쪽은 암호를
     * 넣었다는 사실로 '틀렸다' 를 안다.
     *
     * 암호가 `String` 으로 건너간다(`LoadParams.Builder.setPassword` 가 그것만 받는다).
     * `String` 은 지울 수 없으므로 이 함수 안에서만 만들고 곧바로 놓는다.
     */
    @RequiresApi(35)
    suspend fun openWithPassword(file: File, password: CharArray): Opened = openWith(
        { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) },
    ) { pfd ->
        PdfRenderer(pfd, LoadParams.Builder().setPassword(String(password)).build())
    }

    /**
     * **문서를 `withContext` 의 반환값에만 싣지 않는다.** 돌아오는 사이에 취소되면 코루틴이
     * 값을 버리고, 받은 적 없는 pdfium 문서는 아무도 닫지 못한다(함정 표). 만든 자리에서
     * [made] 에 적어 두고, 값이 호출자에게 닿은 뒤에야 지운다 — 닿지 못했으면 `finally`
     * 가 닫는다. 막 만든 문서라 그리는 중인 쪽이 없으므로 어느 스레드에서 닫아도 안전하다.
     */
    private suspend fun openWith(
        acquire: () -> ParcelFileDescriptor,
        create: (ParcelFileDescriptor) -> PdfRenderer,
    ): Opened {
        var made: PdfRenderer? = null
        try {
            val opened = withContext(IroDispatchers.pdfRender) {
                var pfd: ParcelFileDescriptor? = null
                try {
                    pfd = acquire()
                    val renderer = create(pfd)
                    // 여기까지 왔으면 소유권이 넘어갔다. 닫는 것은 `renderer.close()` 다.
                    pfd = null
                    made = renderer
                    Opened.Ok(renderer, renderer.pageCount)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Opened.Error(t)
                } finally {
                    // 성공 경로에서는 위에서 null 로 바꿔 두었으므로 닫지 않는다.
                    runCatching { pfd?.close() }
                }
            }
            made = null // 호출자에게 닿았다
            return opened
        } finally {
            made?.let { runCatching { it.close() } }
        }
    }

    /**
     * 쪽 크기를 포인트로 잰다. `[폭, 높이]` 또는 null.
     *
     * **쪽을 여는 것도 렌더와 같은 줄에 세운다** — pdfium 의 잠금이 프로세스 전역이라
     * 어차피 직렬화되고, 우리 쪽에서도 '한 번에 한 쪽' 을 지켜야 한다.
     */
    suspend fun pageSize(renderer: PdfRenderer, ordinal: Int): IntArray? =
        withContext(IroDispatchers.pdfRender) {
            try {
                renderer.openPage(ordinal).use { intArrayOf(it.width, it.height) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Iro.d { "쪽 크기를 재지 못했다 #$ordinal: ${t::class.java.simpleName}" }
                null
            }
        }

    /**
     * 가상 쪽([layout] — 쪽 하나, 또는 나란히 놓은 두 쪽)을 비트맵 **한 장**에 그린다.
     *
     * 지키는 것 넷.
     *
     * 1. **쪽은 이 함수 안에서 열고 닫는다.** `use { }` 를 벗어나지 않으므로 '한 번에 한
     *    쪽' 이 구조로 성립한다. 레거시가 던지는 `IllegalStateException: Current page not
     *    closed` 에 기대지 않는다 — 35+ 재구현에는 그 검사가 아예 없고, 레거시에서도
     *    가드 필드가 volatile 이 아니라 스레드가 둘이면 나지 않는다. **그 예외는 방어가
     *    아니라 증상이다.**
     * 2. **비트맵은 매번 새로 만들고 곧바로 흰색으로 지운다.** `render` 는 비트맵을 지우지
     *    않는다(실측) — 새 비트맵에 그냥 그리면 쪽이 칠하지 않은 자리가 **투명**으로 남고,
     *    캐시의 것을 재사용하면 **앞 쪽의 그림이 비쳐 보인다.** 종이의 흰색은 PDF 가 아니라
     *    우리가 칠하는 것이다.
     * 3. **`Matrix` 로 그린다. `matrix = null` 을 절대 쓰지 않는다.** null 이면 쪽을
     *    비트맵에 **늘려 맞춘다**(실측: 595×842pt 쪽을 800×800 에 그렸더니 레터박스 없이
     *    네 귀퉁이가 쪽의 귀퉁이였다). 한 문서 안에서도 쪽 크기가 다르므로 첫 쪽 크기로
     *    비트맵을 만들어 돌려 쓰면 가로 쪽에서 곧바로 찌그러진다.
     * 4. **취소는 여기까지다.** `render` 를 부르기 직전에 한 번 확인하고, **그 뒤로는
     *    취소가 닿지 않는다** — 렌더 도중 `Thread.interrupt()` 를 걸어도 끝까지 돌고
     *    예외도 나지 않는다(실측). `Documents.open` 의 시간 상한도 기다리기를 그만두는
     *    것일 뿐이고 그동안 전역 잠금은 물려 있다.
     *
     * **두 쪽이어도 '한 번에 한 쪽' 이다** — 쪽마다 열고 그리고 닫은 뒤 다음 쪽을 연다. 두 쪽 사이·둘레는 바닥 색
     * ([SPREAD_BACKDROP])으로 칠하고 쪽의 자리만 희게 칠한다(`render` 가 지우지 않으므로 종이의 흰색은 우리 몫이다).
     * 쪽 하나가 가상 쪽 전체를 덮으면(두 쪽 보기가 아닐 때) 옛 길 그대로 통째로 희게 지운다.
     */
    suspend fun render(renderer: PdfRenderer, layout: PdfSpreads.Layout, spec: Spec): Bitmap? =
        withContext(IroDispatchers.pdfRender) {
            try {
                val bitmap = Bitmap.createBitmap(
                    spec.bitmapWidth.coerceAtLeast(1),
                    spec.bitmapHeight.coerceAtLeast(1),
                    // `RGB_565` 는 `Unsupported pixel format` 이다. 고를 수 있는 것이 아니다.
                    Bitmap.Config.ARGB_8888,
                )
                val s = spec.scale.toFloat()
                if (layout.coversWhole) {
                    bitmap.eraseColor(Color.WHITE)
                } else {
                    bitmap.eraseColor(SPREAD_BACKDROP)
                    val canvas = Canvas(bitmap)
                    val paper = Paint().apply { color = Color.WHITE }
                    for (part in layout.parts) {
                        canvas.drawRect(
                            part.left * s - spec.srcLeft,
                            part.top * s - spec.srcTop,
                            (part.left + part.width) * s - spec.srcLeft,
                            (part.top + part.height) * s - spec.srcTop,
                            paper,
                        )
                    }
                }
                for (part in layout.parts) {
                    // 펼침이면 쪽마다 **그 쪽의 자리로 자른다**(`PdfSpreads.clipOf` — 자르기 상자 밖의 재단 여백이 쪽 사이와
                    // 옆 쪽에 번지지 않게). 타일이 그 쪽에 닿지 않으면 쪽을 열지도 않는다.
                    val clip = if (layout.coversWhole) {
                        null
                    } else {
                        val c = PdfSpreads.clipOf(
                            part, spec.scale, spec.srcLeft, spec.srcTop, bitmap.width, bitmap.height,
                        ) ?: continue
                        Rect(c[0], c[1], c[2], c[3])
                    }
                    renderer.openPage(part.page).use { page ->
                        val matrix = Matrix().apply {
                            setScale(s, s)
                            postTranslate(part.left * s - spec.srcLeft, part.top * s - spec.srcTop)
                        }
                        currentCoroutineContext().ensureActive()
                        // 쪽 하나면 `destClip` 은 null 이다. **행렬 없이** 그것만 주면 타일이 아니라 클립 안에 쪽
                        // 전체를 축소한다(실측). 행렬을 함께 주면 클립은 자르기만 한다 — 잘라 보여 주는 일은 `Matrix` 가 한다.
                        page.render(bitmap, clip, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 닫힌 문서에 그리면 문서가 약속한 `IllegalStateException` 이 아니라 **NPE**
                // 가 온다(실측). 그 예외에 기대지 않고 부르는 쪽이 닫힘 표시를 들고 있다.
                Iro.d { "쪽을 그리지 못했다 #${layout.parts.map { it.page }}: ${t::class.java.simpleName}" }
                null
            }
        }

    /**
     * 쪽 하나에서 [query] 를 찾는다. 결과의 사각형은 **쪽 좌표(포인트)** 다.
     *
     * 결과가 [PdfSearch.MAX_PER_PAGE] 를 넘으면 앞의 것만 싣는다. 실패(닫힌 문서·깨진 쪽)는 빈 목록이 아니라 null —
     * 부르는 쪽이 '없다' 와 '못 읽었다' 를 가를 수 있게.
     */
    @RequiresApi(35)
    suspend fun searchPage(renderer: PdfRenderer, ordinal: Int, query: String): List<PdfSearch.Match>? =
        withContext(IroDispatchers.pdfRender) {
            try {
                currentCoroutineContext().ensureActive()
                renderer.openPage(ordinal).use { page ->
                    page.searchText(query).take(PdfSearch.MAX_PER_PAGE).map { m ->
                        PdfSearch.Match(
                            page = ordinal,
                            boxes = m.bounds.map { r -> PdfSearch.Box(r.left, r.top, r.right, r.bottom) },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Iro.d { "쪽에서 찾지 못했다 #$ordinal: ${t::class.java.simpleName}" }
                null
            }
        }

    /** 두 쪽 보기의 쪽 사이·둘레. 문서 화면의 바닥(`DocViewScreen` 의 `PAPER_BACKDROP`)과 같은 회색이다. */
    private const val SPREAD_BACKDROP = 0xFF303030.toInt()
}
