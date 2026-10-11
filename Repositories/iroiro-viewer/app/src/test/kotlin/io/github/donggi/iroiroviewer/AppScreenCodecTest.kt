package io.github.donggi.iroiroviewer

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 화면 ↔ 저장 상태. 회전·프로세스 재생성 뒤에 **같은 화면, 같은 돌아갈 곳**으로 살아나야 한다 — 표에서 빠진 화면은 조용히
 * 첫 화면으로 떨어진다(되살리기 쪽 `when` 이 `else` 로 끝나 컴파일러가 말하지 않는다).
 */
class AppScreenCodecTest {

    private val odd = "/storage/emulated/0/만화: 1권 >상< (2).cbz"

    private val all: List<AppScreen> = listOf(
        AppScreen.Browser,
        AppScreen.Settings,
        AppScreen.Diag(),
        AppScreen.Diag(back = AppScreen.Settings),
        AppScreen.Notice(),
        AppScreen.Notice(back = AppScreen.Settings),
        AppScreen.Notice(back = AppScreen.Archive(odd)),
        AppScreen.Viewer(odd),
        AppScreen.Text(odd),
        AppScreen.Edit(odd),
        AppScreen.Edit(odd, encoding = "CP949"),
        AppScreen.Edit(odd, encoding = null, fromViewer = false),
        AppScreen.Edit("/storage/emulated/0/a:b:c.txt", encoding = "SHIFT_JIS", fromViewer = false),
        AppScreen.Editor(odd, "docx"),
        AppScreen.Editor("/storage/emulated/0/표: 3월 >합계<.xlsx", "xlsx"),
        AppScreen.Editor("/storage/emulated/0/a:b.hwpx", "hwpx"),
        AppScreen.Archive(odd),
        AppScreen.Comic(odd, 12),
        AppScreen.Comic(odd, -1),
        AppScreen.Doc(odd),
    )

    @Test
    fun `모든 화면이 왕복한다 — 경로의 콜론·꺾쇠까지`() {
        for (s in all) assertEquals(s, AppScreenCodec.decode(AppScreenCodec.encode(s)), AppScreenCodec.encode(s))
    }

    @Test
    fun `Saver 도 같은 표를 쓴다`() {
        val scope = SaverScope { true }
        for (s in all) {
            val saved = with(AppScreen.Saver) { scope.save(s) }
            assertEquals(s, AppScreen.Saver.restore(saved!!))
        }
    }

    @Test
    fun `설정에서 연 고지·진단은 설정으로 돌아간다`() {
        assertEquals(AppScreen.Settings, (AppScreenCodec.decode("n>s") as AppScreen.Notice).back)
        assertEquals(AppScreen.Settings, (AppScreenCodec.decode("d>s") as AppScreen.Diag).back)
        assertEquals(AppScreen.Archive("/a:b.zip"), (AppScreenCodec.decode("n>a:/a:b.zip") as AppScreen.Notice).back)
    }

    @Test
    fun `옛 판이 남긴 값은 첫 화면으로 돌아가는 것으로 읽는다`() {
        assertEquals(AppScreen.Notice(AppScreen.Browser), AppScreenCodec.decode("n"))
        assertEquals(AppScreen.Diag(AppScreen.Browser), AppScreenCodec.decode("d"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("b"))
    }

    /**
     * 편집기는 경로·뷰어에서 고른 인코딩·돌아갈 곳을 든다 — 회전·프로세스 재생성 뒤에도 같은 인코딩으로 열고, 닫으면 같은
     * 곳(뷰어 또는 목록)으로 간다. 고치는 글은 싣지 않는다(뷰모델과 초안이 든다).
     */
    @Test
    fun `편집기는 인코딩과 돌아갈 곳을 함께 든다`() {
        assertEquals("e:t::/a.txt", AppScreenCodec.encode(AppScreen.Edit("/a.txt")))
        assertEquals("e:b:UTF_16LE:/a.txt", AppScreenCodec.encode(AppScreen.Edit("/a.txt", "UTF_16LE", fromViewer = false)))
        assertEquals(AppScreen.Edit("/x:y.md", "CP949", fromViewer = true), AppScreenCodec.decode("e:t:CP949:/x:y.md"))
        // 망가진 값 — 첫 화면.
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("e:"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("e:t"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("e:tt:/a"))
    }

    /**
     * 편집기의 보기 모드로 연 자리는 경로와 편집기의 형식 글자를 든다 — 글 편집기(`e:`)와 머리가 겹치지 않게 `r:` 다.
     * 옛 판의 `o:`(앱의 오피스 편집 화면 — 걷었다)는 첫 화면으로 읽는다.
     */
    @Test
    fun `편집기로 연 자리는 형식과 경로를 든다`() {
        assertEquals("r:docx:/a.docx", AppScreenCodec.encode(AppScreen.Editor("/a.docx", "docx")))
        assertEquals(AppScreen.Editor("/x:y.hwpx", "hwpx"), AppScreenCodec.decode("r:hwpx:/x:y.hwpx"))
        // 망가진 값·형식에 쓸 수 없는 글자 — 첫 화면.
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("r:"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("r:docx"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("r:docx:"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("r::/a.docx"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("r:DOC X:/a.docx"))
        // 옛 판의 오피스 편집 화면.
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("o:t:docx:/a.docx"))
    }

    @Test
    fun `망가진 값은 죽지 않고 첫 화면이 된다`() {
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("c:숫자없음"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("?"))
        assertEquals(AppScreen.Browser, AppScreenCodec.decode(""))
        assertEquals(AppScreen.Comic("x", -1), AppScreenCodec.decode("c:abc:x"))
        // 고지에서 고지로 가는 길은 없다 — 뒤로가기가 제자리를 돌지 않게 첫 화면으로 접는다.
        assertEquals(AppScreen.Notice(AppScreen.Browser), AppScreenCodec.decode("n>n>s"))
        assertEquals(AppScreen.Diag(AppScreen.Browser), AppScreenCodec.decode("d>n"))
        assertEquals(AppScreen.Browser, AppScreen.Saver.restore(42))
        // 걷어 낸 네트워크 저장소 화면(2026-10-05 ~ 10-09)이 남긴 값 — 첫 화면으로 읽는다.
        assertEquals(AppScreen.Browser, AppScreenCodec.decode("r"))
    }
}
