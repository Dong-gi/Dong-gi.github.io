package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 문서 뷰어의 '다른 앱으로 열기' 를 어디에 띄우는가([DocOpenWith]).
 *
 * **갈래마다 답을 여기 적는다.** 화면의 `when` 은 `else` 가 없어 새 갈래가 생기면 컴파일이 멈추지만, 그 갈래의 답을
 * 누가 어떻게 정했는지는 이 표가 남긴다 — 그리고 [모든_갈래의_답이_표에_있다] 가 표에 빠진 갈래를 잡는다.
 */
class DocOpenWithTest {

    /** 갈래 하나에 표본 하나. 값은 '이 실패 화면에 단추를 둔다' 다. */
    private val expected: List<Pair<OpenFailure, Boolean>> = listOf(
        // 이 앱이 다루지 않는다 — 다른 앱은 연다.
        OpenFailure.Unsupported("여는이가 없다") to true,
        OpenFailure.LegacyFormat("OLE2") to true,
        OpenFailure.Encrypted("공개 키 처리기") to true,
        OpenFailure.Corrupt("형식이 깨졌다") to true,
        OpenFailure.TooLarge("maxEntries") to true,
        OpenFailure.Timeout("30초 안에 열지 못했다") to true,
        // 이 앱이 묻는다.
        OpenFailure.PasswordRequired("암호") to false,
        // 파일에 닿지 못했다 — 다른 앱도 같은 벽에 부딪힌다.
        OpenFailure.NoPermission("읽을 권한이 없다") to false,
        OpenFailure.Io("입출력이 실패했다") to false,
    )

    @Test
    fun 모든_갈래의_답이_표에_있다() {
        // Kotlin 의 sealed 는 JVM 17 대상에서 `PermittedSubclasses` 를 남긴다 — 선언된 갈래 전부다(javap 로 확인했다).
        // sealed 가 아니면 null 이다 — 그때는 아래 개수 검사가 '읽지 못했다' 로 멈춘다(조용히 통과하지 않게).
        val declared = OpenFailure::class.java.permittedSubclasses.orEmpty().map { it.name }.toSortedSet()
        val covered = expected.map { it.first.javaClass.name }.toSortedSet()
        assertTrue(declared.size >= 9, "갈래를 읽지 못했다: $declared")
        assertEquals(declared, covered, "새 갈래는 DocOpenWith.onFailure 와 이 표에서 함께 정한다")
    }

    @Test
    fun 갈래마다_정한_대로다() {
        for ((failure, want) in expected) {
            assertEquals(want, DocOpenWith.onFailure(failure), failure::class.java.simpleName)
        }
    }

    @Test
    fun 이_앱이_묻는_암호에는_틀렸어도_권하지_않는다() {
        // 틀린 암호를 넣은 뒤에도 같은 창이 다시 묻는다. 그때 '다른 앱' 을 권하면 암호가 틀린 것이 앱 탓처럼 읽힌다.
        assertFalse(DocOpenWith.onFailure(OpenFailure.PasswordRequired("암호", wrongPassword = true)))
    }

    @Test
    fun 없는_파일은_모르는_형식이_아니다() {
        // 판별이 앞부분을 읽지 못하면 '여는이가 없다(Unsupported)' 로 끝나 단추가 뜬다. 그 앞에서 가른다.
        val missing = reachFailureOf(isFile = false, openError = FileNotFoundException("ENOENT"))
        assertIs<OpenFailure.Io>(missing)
        assertFalse(DocOpenWith.onFailure(missing))

        // 있는데 열리지 않는다 — 공용 매핑과 같은 답이다(권한은 권한, 나머지는 입출력).
        val denied = reachFailureOf(isFile = true, openError = SecurityException("denied"))
        assertIs<OpenFailure.NoPermission>(denied)
        assertFalse(DocOpenWith.onFailure(denied))
        val broken = reachFailureOf(isFile = true, openError = IOException("EIO"))
        assertIs<OpenFailure.Io>(broken)
        assertFalse(DocOpenWith.onFailure(broken))

        assertNull(reachFailureOf(isFile = true, openError = null))
    }

    @Test
    fun 실제로_열어_본다() {
        val file = File.createTempFile("docopenwith", ".pdf")
        try {
            assertNull(reachFailureOf(file.isFile, openErrorOf(file)))
            assertTrue(file.delete())
            assertIs<OpenFailure.Io>(reachFailureOf(file.isFile, openErrorOf(file)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun 닿지_못한_파일은_입출력_실패로_알리지_않는다() {
        // 종류는 공용 매핑의 것(`Io` — 단추가 없다)이고, 화면이 문장을 '읽을 수 없다' 로 가르는 표시만 선다.
        // 표시가 없으면 없어진 파일이 '입출력이 실패했습니다' 로 나가 디스크가 고장 난 것처럼 읽힌다.
        val missing = unreachableState(isFile = false, openError = FileNotFoundException("ENOENT"))
        assertNotNull(missing)
        assertTrue(missing.unreachable)
        assertIs<OpenFailure.Io>(missing.failure)
        assertFalse(DocOpenWith.onFailure(missing.failure))
        assertFalse(DocOpenWith.inMenu(missing))
        // 있는데 열리지 않는다 — 안드로이드는 권한 거부도 `FileNotFoundException` 으로 알린다.
        val denied = unreachableState(isFile = true, openError = FileNotFoundException("EACCES (Permission denied)"))
        assertNotNull(denied)
        assertTrue(denied.unreachable)
        assertFalse(DocOpenWith.onFailure(denied.failure))
        assertNull(unreachableState(isFile = true, openError = null))
        // 여는이가 낸 실패(파일에는 닿았다)는 그 종류의 문장 그대로다.
        assertFalse(DocViewModel.State.Failed(OpenFailure.Io("입출력이 실패했다")).unreachable)
    }

    /**
     * 막대의 ⋮ 는 실패 단추의 갈래에 **이 앱이 묻는 암호**를 더한 것이다 — 빠지는 것은 파일에 닿지 못한 갈래뿐.
     * 단추가 있는 갈래에도 ⋮ 를 둔다(겹쳐 둔다 — `DocOpenWith` 의 표).
     */
    @Test
    fun 막대의_메뉴는_닿지_못한_실패에만_없다() {
        for ((failure, button) in expected) {
            val want = button || failure is OpenFailure.PasswordRequired
            assertEquals(want, DocOpenWith.inMenu(DocViewModel.State.Failed(failure)), failure::class.java.simpleName)
        }
        // 암호를 묻는 화면에서도 넘길 수 있다 — 틀린 암호를 넣은 뒤에도. 실패 화면의 단추는 없다(암호 넣기와 다투지 않게).
        val wrong = DocViewModel.State.Failed(OpenFailure.PasswordRequired("암호", wrongPassword = true))
        assertTrue(DocOpenWith.inMenu(wrong))
        assertFalse(DocOpenWith.onFailure(wrong))
        // 닿지 못한 두 갈래.
        assertFalse(DocOpenWith.inMenu(DocViewModel.State.Failed(OpenFailure.NoPermission("읽을 권한이 없다"))))
        assertFalse(DocOpenWith.inMenu(DocViewModel.State.Failed(OpenFailure.Io("입출력이 실패했다"))))

        val ready = DocViewModel.State.Ready(DocViewModel.Doc.Flow("/storage/emulated/0/a.docx", "a", FakeFlow))
        assertTrue(DocOpenWith.inMenu(ready))
    }

    @Test
    fun 여는_중에는_파일에_닿은_뒤부터_있다() {
        // 닿기 전 — 없거나 열리지 않는 파일일 수 있다.
        assertFalse(DocOpenWith.inMenu(DocViewModel.State.Loading()))
        // 닿은 뒤 — docx·HWPX 를 여는 몇 초를 기다리지 않고 넘긴다.
        assertTrue(DocOpenWith.inMenu(DocViewModel.State.Loading(reached = true)))
    }

    @Test
    fun 닿아_본_뒤의_상태가_막대를_정한다() {
        // 닿았다 — 닿은 채로 여는 중이다. 이 값이 아니면 여는 동안 ⋮ 가 끝내 뜨지 않는다.
        val reached = reachState(isFile = true, openError = null)
        assertEquals(DocViewModel.State.Loading(reached = true), reached)
        assertTrue(DocOpenWith.inMenu(reached))
        // 없다·열리지 않는다 — 닿지 못한 실패 화면이고 ⋮ 도 단추도 없다.
        for ((isFile, error) in listOf(false to FileNotFoundException("ENOENT"), true to FileNotFoundException("EACCES"))) {
            val state = reachState(isFile, error)
            assertIs<DocViewModel.State.Failed>(state)
            assertTrue(state.unreachable)
            assertFalse(DocOpenWith.inMenu(state), "isFile=$isFile")
            assertFalse(DocOpenWith.onFailure(state), "isFile=$isFile")
        }
    }

    /**
     * **여는 동안 없어진 파일도 '깨졌다'·'다루지 않는다' 가 아니다.** 열기 전에는 닿았는데 여는 몇 초(상한 30초) 사이에 지워졌거나
     * SD 를 뺐으면, 판별은 그 실패를 삼켜 Unsupported 로, OOXML·EPUB 은 리더를 여는 자리의 입출력 예외를 Corrupt 로 낸다 — 둘 다
     * 단추가 뜨는 갈래다. 실패한 뒤에 한 번 더 닿아 본 답으로 가른다.
     */
    @Test
    fun 여는_사이에_없어진_파일은_권하지_않는다() {
        for ((failure, _) in expected) {
            val gone = failedAfter(failure, isFile = false, openError = FileNotFoundException("ENOENT"))
            assertTrue(gone.unreachable, failure::class.java.simpleName)
            assertFalse(DocOpenWith.onFailure(gone), failure::class.java.simpleName)
            assertFalse(DocOpenWith.inMenu(gone), failure::class.java.simpleName)
            // 있는데 열리지 않게 됐다(SD 를 뺐다 — EIO).
            val broken = failedAfter(failure, isFile = true, openError = IOException("EIO"))
            assertTrue(broken.unreachable, failure::class.java.simpleName)
            assertFalse(DocOpenWith.onFailure(broken), failure::class.java.simpleName)
        }
        // 닿으면 여는이의 답 그대로다 — 암호 화면도 그대로 선다.
        for ((failure, want) in expected) {
            val state = failedAfter(failure, isFile = true, openError = null)
            assertEquals(DocViewModel.State.Failed(failure), state)
            assertEquals(want, DocOpenWith.onFailure(state), failure::class.java.simpleName)
        }
    }

    @Test
    fun 닿지_못한_표시가_서면_종류와_상관없이_권하지_않는다() {
        // 공용 매핑이 닿지 못한 실패를 `Io`·`NoPermission` 이 아닌 갈래로 내게 되어도, 없는 파일에 단추·⋮ 가 뜨지 않는다.
        for ((failure, _) in expected) {
            val state = DocViewModel.State.Failed(failure, unreachable = true)
            assertFalse(DocOpenWith.onFailure(state), failure::class.java.simpleName)
            assertFalse(DocOpenWith.inMenu(state), failure::class.java.simpleName)
        }
        // 표시가 없으면 종류의 답 그대로다.
        for ((failure, want) in expected) {
            assertEquals(want, DocOpenWith.onFailure(DocViewModel.State.Failed(failure)), failure::class.java.simpleName)
        }
    }

    @Test
    fun 막대를_숨길_수_있는_것은_열린_문서뿐이다() {
        val ready = DocViewModel.State.Ready(DocViewModel.Doc.Flow("/storage/emulated/0/a.docx", "a", FakeFlow))
        // 쪽을 두드려 숨겼다 — 다시 두드리면 돌아온다.
        assertFalse(DocOpenWith.barVisible(tapVisible = false, state = ready))
        assertTrue(DocOpenWith.barVisible(tapVisible = true, state = ready))
        // 여는 중·실패 화면에는 두드릴 쪽이 없다. 숨긴 값이 남아 있어도 닫기와 ⋮ 가 보여야 한다.
        val others = listOf(
            DocViewModel.State.Loading(),
            DocViewModel.State.Loading(reached = true),
            DocViewModel.State.Failed(OpenFailure.PasswordRequired("암호")),
            DocViewModel.State.Failed(OpenFailure.Unsupported("여는이가 없다")),
            DocViewModel.State.Failed(OpenFailure.Io("파일이 없다"), unreachable = true),
        )
        for (state in others) assertTrue(DocOpenWith.barVisible(tapVisible = false, state = state), state.toString())
    }

    /** 부분 하나짜리 흐름 문서. 이 시험은 문서를 읽지 않는다 — 상태의 모양만 본다. */
    private object FakeFlow : FlowDocument {
        override val formatId = FormatId.DOCX
        override val warnings = emptyList<ParseWarning>()
        override val unsupported = UnsupportedFeatures()
        override val title = ""
        override val kind = FlowKind.DOCUMENT
        override val parts = listOf(FlowPart("word/document.xml", "본문"))
        override val outline = emptyList<FlowOutline>()
        override fun partHtml(index: Int): String? = null
        override fun openResource(path: String): InputStream? = null
        override fun mediaTypeOf(path: String): String? = null
        override fun close() = Unit
    }
}
