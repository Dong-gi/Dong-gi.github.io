package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.docview.pdf.PdfFailures
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.toOpenFailure
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * pdfium 이 던진 것을 사용자가 읽을 갈래로 옮긴다.
 *
 * **이 시험이 지키는 것은 문구가 아니라 뜻이다.** 공용 `toOpenFailure()` 는 같은 예외를
 * 정확히 뒤바뀐 뜻으로 옮긴다 — 암호 PDF 의 `SecurityException` 을
 * `NoPermission("읽을 권한이 없다")` 로 만들어 **사용자가 권한 설정을 뒤지게** 한다.
 * 그 되돌림을 막는 것이 `PdfFailures` 의 존재 이유이므로, 고침을 되돌리면 여기가 깨져야 한다.
 */
class PdfFailuresTest {

    @Test
    fun 암호는_권한이_아니라_암호다() {
        val f = PdfFailures.of(SecurityException("password required"))
        // 암호를 **물어야** 한다. 처음 여는 것이므로 '틀렸다' 가 아니다.
        assertIs<OpenFailure.PasswordRequired>(f)
        assertEquals(false, f.wrongPassword)
        // 공용 매핑이었다면 NoPermission 이었을 것이다. 그것이 이 파일이 있는 이유다.
        assertIs<OpenFailure.NoPermission>(SecurityException("x").toOpenFailure())
    }

    @Test
    fun 깨진_파일은_입출력_실패가_아니다() {
        val f = PdfFailures.of(IOException("file not in PDF format or corrupted"))
        assertIs<OpenFailure.Corrupt>(f)
    }

    @Test
    fun 우리가_먼저_막았어야_하는_자리는_지원하지_않음이다() {
        assertIs<OpenFailure.Unsupported>(PdfFailures.of(IllegalArgumentException("not seekable")))
    }

    @Test
    fun 취소는_실패가_아니다() {
        // 경계 catch 의 첫 줄 규칙. 삼키면 취소된 코루틴이 끝까지 달린다.
        assertFailsWith<java.util.concurrent.CancellationException> {
            PdfFailures.of(java.util.concurrent.CancellationException("취소"))
        }
    }

    @Test
    fun 모르는_예외는_공용_매핑에_맡긴다() {
        assertIs<OpenFailure.TooLarge>(PdfFailures.of(OutOfMemoryError("heap")))
    }

    @Test
    fun 예외_메시지를_화면_문구로_쓰지_않는다() {
        // `detail` 은 화면에 나가지 않지만, 거기에 예외 메시지를 넣는 구현으로 바뀌면
        // 절대경로와 공격자가 심은 문자열이 한 걸음 만에 화면에 닿는다.
        val nasty = "C:/Users/someone/비밀/문서.pdf <script>"
        for (f in listOf(
            PdfFailures.of(SecurityException(nasty)),
            PdfFailures.of(IOException(nasty)),
            PdfFailures.of(IllegalArgumentException(nasty)),
        )) {
            assertTrue(nasty !in f.detail, "예외 메시지가 detail 로 샜다: ${f.detail}")
        }
    }

    @Test
    fun 같은_예외는_같은_갈래로_간다() {
        assertEquals(
            PdfFailures.of(IOException("a"))::class,
            PdfFailures.of(IOException("b"))::class,
        )
    }
}
